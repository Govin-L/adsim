package com.adsim.simulation

import com.adsim.model.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ResultAggregator {

    private val logger = LoggerFactory.getLogger(ResultAggregator::class.java)
    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    /**
     * Aggregate agent decisions into campaign-level and placement-level metrics.
     * Placement-level results are derived from replayable placement traces when available
     * (placementOutcomes first, placementDecisions as fallback), while top-level results
     * summarize all simulated placement exposures.
     */
    suspend fun aggregate(
        agents: List<Agent>,
        budget: Long,
        placements: List<AdPlacement> = emptyList(),
        chatModel: ChatModel? = null,
        deliveryPlan: DeliveryPlan? = null
    ): SimulationResults {
        val hasPlacementTraces = agents.any { placementTraceSamples(it).isNotEmpty() }
        val overallSamples = buildOverallSamples(agents, hasPlacementTraces)
        val totalPopulation = if (hasPlacementTraces) {
            overallSamples.size
        } else {
            agents.size
        }

        val overall = aggregateSamples(
            totalPopulation = totalPopulation,
            samples = overallSamples,
            budget = budget,
            placements = placements,
            chatModel = chatModel
        )

        val placementResults = placements.mapIndexed { placementIndex, placement ->
            val placementSamples = buildPlacementSamples(agents, placementIndex, hasPlacementTraces)
            val plannedSamples = placementSamples.size
            val aggregate = aggregateSamples(
                totalPopulation = plannedSamples,
                samples = placementSamples,
                budget = placement.budget,
                placements = listOf(placement),
                chatModel = null
            )
            PlacementResult(
                placementIndex = placementIndex,
                placement = placement,
                totalAgents = plannedSamples,
                successfulAgents = aggregate.successfulSamples,
                metrics = aggregate.metrics,
                simulatedMetrics = aggregate.simulatedMetrics,
                estimatedMetrics = aggregate.estimatedMetrics,
                funnel = aggregate.funnel,
                dropOffReasons = aggregate.dropOffReasons,
                clusteredReasons = aggregate.clusteredReasons,
                segmentInsights = aggregate.segmentInsights,
                topInsights = aggregate.topInsights,
                sampleQuality = SampleQuality(
                    plannedSamples = plannedSamples,
                    simulatedSamples = aggregate.successfulSamples,
                    successRate = if (plannedSamples > 0) aggregate.successfulSamples.toDouble() / plannedSamples else 0.0
                ),
                stageBlockers = aggregate.stageBlockers,
                recommendations = aggregate.recommendations
            )
        }

        logger.info(
            "Aggregation complete: plannedSamples={}, simulatedSamples={}, placements={}",
            totalPopulation,
            overall.successfulSamples,
            placements.size
        )

        return SimulationResults(
            totalAgents = totalPopulation,
            successfulAgents = overall.successfulSamples,
            metrics = overall.metrics,
            simulatedMetrics = overall.simulatedMetrics,
            estimatedMetrics = overall.estimatedMetrics,
            funnel = overall.funnel,
            dropOffReasons = overall.dropOffReasons,
            clusteredReasons = overall.clusteredReasons,
            segmentInsights = overall.segmentInsights,
            topInsights = overall.topInsights,
            placementResults = placementResults,
            utilization = buildAgentUtilization(agents, deliveryPlan, placements),
            stageBlockers = overall.stageBlockers,
            recommendations = overall.recommendations
        )
    }

    private fun buildOverallSamples(agents: List<Agent>, hasPlacementTraces: Boolean): List<DecisionSample> {
        return if (hasPlacementTraces) {
            agents.flatMap { agent ->
                placementTraceSamples(agent).map { sample ->
                    DecisionSample(agent = agent, decisions = sample.decisions)
                }
            }
        } else {
            agents.mapNotNull { agent ->
                agent.decisions?.let { DecisionSample(agent = agent, decisions = it) }
            }
        }
    }

    private fun buildPlacementSamples(
        agents: List<Agent>,
        placementIndex: Int,
        hasPlacementTraces: Boolean
    ): List<DecisionSample> {
        return if (hasPlacementTraces) {
            agents.flatMap { agent ->
                placementTraceSamples(agent)
                    .filter { it.placementIndex == placementIndex }
                    .map { sample -> DecisionSample(agent = agent, decisions = sample.decisions) }
            }
        } else if (placementIndex == 0) {
            agents.mapNotNull { agent ->
                agent.decisions?.let { DecisionSample(agent = agent, decisions = it) }
            }
        } else {
            emptyList()
        }
    }

    private fun placementTraceSamples(agent: Agent): List<PlacementSample> {
        return when {
            agent.placementOutcomes.isNotEmpty() -> agent.placementOutcomes.map { outcome ->
                PlacementSample(
                    placementIndex = outcome.placementIndex,
                    decisions = outcome.decisions
                )
            }
            agent.placementDecisions.isNotEmpty() -> agent.placementDecisions.map { sample ->
                PlacementSample(
                    placementIndex = sample.placementIndex,
                    decisions = sample.decisions
                )
            }
            else -> emptyList()
        }
    }

    private suspend fun aggregateSamples(
        totalPopulation: Int,
        samples: List<DecisionSample>,
        budget: Long,
        placements: List<AdPlacement>,
        chatModel: ChatModel?
    ): AggregateSnapshot {
        val successful = samples.size
        val noticed = samples.count { it.decisions.attention.passed }
        val clicked = samples.filter { it.decisions.attention.passed }
            .count { it.decisions.click.passed }
        val converted = samples.filter { it.decisions.attention.passed && it.decisions.click.passed }
            .count { it.decisions.conversion.passed }

        val attentionRate = if (successful > 0) noticed.toDouble() / successful else 0.0
        val ctr = if (noticed > 0) clicked.toDouble() / noticed else 0.0
        val cvr = if (clicked > 0) converted.toDouble() / clicked else 0.0
        val overallConversionRate = if (successful > 0) converted.toDouble() / successful else 0.0

        val estimatedImpressions = estimateImpressions(placements, budget)
        val viewabilityRate = 0.50
        val estimatedViewers = estimatedImpressions * viewabilityRate
        val estimatedConversions = estimatedViewers * overallConversionRate
        val estimatedCPA = if (estimatedConversions > 0) budget.toDouble() / estimatedConversions else null

        val dropOffReasons = buildDropOffReasons(samples)
        val clusteredReasons = if (chatModel != null) {
            try {
                clusterDropOffReasons(samples, chatModel)
            } catch (e: Exception) {
                logger.warn("Failed to cluster reasons via LLM, skipping: {}", e.message?.take(150))
                null
            }
        } else {
            null
        }
        val segmentInsights = analyzeSegments(samples)
        val stageBlockers = buildStageBlockers(samples)
        val funnel = Funnel(
            exposure = FunnelStage(successful, if (totalPopulation > 0) successful.toDouble() / totalPopulation else 0.0),
            attention = FunnelStage(noticed, attentionRate),
            click = FunnelStage(clicked, ctr),
            conversion = FunnelStage(converted, cvr)
        )
        val recommendations = buildRecommendations(stageBlockers)
        val topInsights = generateTopInsights(segmentInsights, funnel, clusteredReasons, stageBlockers)

        val simulatedMetrics = SimulatedFunnelMetrics(
            attentionRate = attentionRate,
            ctr = ctr,
            cvr = cvr,
            overallConversionRate = overallConversionRate
        )
        val estimatedMetrics = EstimatedBusinessMetrics(
            estimatedImpressions = estimatedImpressions,
            estimatedViewers = estimatedViewers,
            estimatedConversions = estimatedConversions,
            estimatedCPA = estimatedCPA
        )

        return AggregateSnapshot(
            successfulSamples = successful,
            metrics = Metrics(
                attentionRate = attentionRate,
                ctr = ctr,
                cvr = cvr,
                overallConversionRate = overallConversionRate,
                estimatedCPA = estimatedCPA
            ),
            simulatedMetrics = simulatedMetrics,
            estimatedMetrics = estimatedMetrics,
            funnel = funnel,
            dropOffReasons = dropOffReasons,
            clusteredReasons = clusteredReasons,
            segmentInsights = segmentInsights,
            topInsights = topInsights,
            stageBlockers = stageBlockers,
            recommendations = recommendations
        )
    }

    /**
     * Estimate total impressions from budget, using CPM benchmarks per placement type.
     * Each placement's budget is divided by its CPM to get impressions, then summed.
     */
    private fun estimateImpressions(placements: List<AdPlacement>, totalBudget: Long): Double {
        if (placements.isEmpty()) {
            return totalBudget / 80.0 * 1000
        }
        return placements.sumOf { placement ->
            val cpm = cpmBenchmark(placement.placementType)
            placement.budget.toDouble() / cpm * 1000
        }
    }

    /**
     * CPM benchmarks by placement type (¥ per 1000 impressions).
     * Based on Chinese social media advertising industry averages.
     */
    private fun cpmBenchmark(type: PlacementType): Double = when (type) {
        PlacementType.INFO_FEED -> 40.0
        PlacementType.SEARCH -> 80.0
        PlacementType.KOL_SEEDING -> 300.0
        PlacementType.SHORT_VIDEO -> 50.0
        PlacementType.SPLASH_SCREEN -> 25.0
        PlacementType.LIVESTREAM -> 150.0
        PlacementType.HASHTAG_CHALLENGE -> 200.0
        PlacementType.SHOPPING -> 60.0
    }

    private fun buildDropOffReasons(samples: List<DecisionSample>): DropOffReasons {
        val noticedNotClicked = samples.filter {
            it.decisions.attention.passed && !it.decisions.click.passed
        }
        val clickedNotConverted = samples.filter {
            it.decisions.attention.passed && it.decisions.click.passed && !it.decisions.conversion.passed
        }

        return DropOffReasons(
            attentionToClick = groupReasons(noticedNotClicked.map { it.decisions.click.reasoning }),
            clickToConversion = groupReasons(clickedNotConverted.map { it.decisions.conversion.reasoning })
        )
    }

    private fun buildStageBlockers(samples: List<DecisionSample>): List<StageBlocker> {
        val failures = buildList {
            samples.forEach { sample ->
                val decisions = sample.decisions
                when {
                    !decisions.attention.passed -> addAll(blockerFactors(decisions.attention).map { Triple("attention", it, decisions.attention.reasoning) })
                    !decisions.click.passed -> addAll(blockerFactors(decisions.click).map { Triple("click", it, decisions.click.reasoning) })
                    !decisions.conversion.passed -> addAll(blockerFactors(decisions.conversion).map { Triple("conversion", it, decisions.conversion.reasoning) })
                }
            }
        }

        if (failures.isEmpty()) return emptyList()
        val totalFailures = failures.size

        return failures.groupingBy { it.first to it.second }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { (key, count) ->
                val (stage, factor) = key
                StageBlocker(
                    stage = stage,
                    factor = factor,
                    count = count,
                    share = count.toDouble() / totalFailures,
                    summary = blockerSummary(stage, factor)
                )
            }
    }

    private fun blockerFactors(decision: StageDecision): List<String> {
        return decision.negativeFactors.ifEmpty { decision.factors }
    }

    private fun blockerSummary(stage: String, factor: String): String {
        val factorText = when (factor) {
            "ad_fatigue" -> "重复曝光导致用户疲劳"
            "frequency_overload" -> "同品牌多次触达造成厌倦"
            "creative_boring" -> "创意不够吸引注意"
            "price_too_high" -> "价格门槛压制转化"
            "competitor_preference" -> "用户已有更强竞品偏好"
            "no_brand_trust" -> "品牌信任不足"
            "no_need" -> "需求匹配度不足"
            "wrong_format" -> "投放形式和内容不匹配"
            "impulse_resist" -> "缺少立即转化动机"
            "no_interest" -> "兴趣匹配不足"
            else -> factor
        }
        return when (stage) {
            "attention" -> "注意阶段主要受「$factorText」拖累。"
            "click" -> "点击阶段主要受「$factorText」拖累。"
            else -> "转化阶段主要受「$factorText」拖累。"
        }
    }

    private fun buildRecommendations(stageBlockers: List<StageBlocker>): List<ActionRecommendation> {
        return stageBlockers.take(4).map { blocker ->
            when (blocker.factor) {
                "ad_fatigue", "frequency_overload" -> ActionRecommendation(
                    title = "降低同品牌重复触达",
                    detail = "为高频 placement 降频，或者更换 creative 钩子，避免相同信息被连续看到。",
                    appliesTo = blocker.stage,
                    priority = "high"
                )
                "creative_boring", "wrong_format" -> ActionRecommendation(
                    title = "重做首屏钩子和内容形式",
                    detail = "优先调整封面、标题和前 3 秒表达，让内容形式更贴合当前 placement 场景。",
                    appliesTo = blocker.stage,
                    priority = "high"
                )
                "price_too_high", "impulse_resist" -> ActionRecommendation(
                    title = "补充价格解释和转化激励",
                    detail = "增加价格锚点、限时权益或试用理由，降低用户在转化阶段的犹豫。",
                    appliesTo = blocker.stage,
                    priority = "high"
                )
                "competitor_preference", "no_brand_trust" -> ActionRecommendation(
                    title = "强化品牌可信度和差异化",
                    detail = "加入真实评价、对比证据和品牌背书，明确为什么值得从竞品切换。",
                    appliesTo = blocker.stage,
                    priority = "medium"
                )
                else -> ActionRecommendation(
                    title = "提升人群与创意匹配度",
                    detail = "重新校准目标受众和 messaging，减少无兴趣或无需求样本占比。",
                    appliesTo = blocker.stage,
                    priority = "medium"
                )
            }
        }.distinctBy { it.title to it.appliesTo }
    }

    private fun groupReasons(reasons: List<String>): List<ReasonStat> {
        if (reasons.isEmpty()) return emptyList()
        val total = reasons.size
        return reasons.groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { ReasonStat(it.key, it.value, it.value.toDouble() / total) }
    }

    private suspend fun clusterDropOffReasons(samples: List<DecisionSample>, chatModel: ChatModel): ClusteredDropOffReasons {
        val noticedNotClicked = samples.filter {
            it.decisions.attention.passed && !it.decisions.click.passed
        }
        val clickedNotConverted = samples.filter {
            it.decisions.attention.passed && it.decisions.click.passed && !it.decisions.conversion.passed
        }

        val attentionToClick = clusterReasons(
            noticedNotClicked.map { it.decisions.click.reasoning },
            chatModel
        )
        val clickToConversion = clusterReasons(
            clickedNotConverted.map { it.decisions.conversion.reasoning },
            chatModel
        )

        return ClusteredDropOffReasons(
            attentionToClick = attentionToClick,
            clickToConversion = clickToConversion
        )
    }

    private suspend fun clusterReasons(reasons: List<String>, chatModel: ChatModel): List<ClusteredReason> {
        if (reasons.isEmpty()) return emptyList()

        if (reasons.size < 5) {
            val total = reasons.size
            return reasons.groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { (reason, count) ->
                    ClusteredReason(
                        category = reason.take(20),
                        percentage = count.toDouble() / total * 100,
                        count = count,
                        quotes = listOf(reason)
                    )
                }
        }

        val prompt = buildClusterPrompt(reasons)
        return try {
            val response = chatModel.chat(
                ChatRequest.builder()
                    .messages(
                        listOf(
                            SystemMessage.from("You are a data analyst. Cluster the given drop-off reasons into categories. Output valid JSON only."),
                            UserMessage.from(prompt)
                        )
                    )
                    .build()
            )
            val text = response.aiMessage().text()
            parseClusteredReasons(text)
        } catch (e: Exception) {
            logger.warn("LLM clustering failed: {}", e.message?.take(150))
            val total = reasons.size
            reasons.groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { (reason, count) ->
                    ClusteredReason(
                        category = reason.take(20),
                        percentage = count.toDouble() / total * 100,
                        count = count,
                        quotes = listOf(reason)
                    )
                }
        }
    }

    private fun buildClusterPrompt(reasons: List<String>): String {
        val reasonList = reasons.mapIndexed { index, reason -> "${index + 1}. $reason" }.joinToString("\n")
        return """
Below are ${reasons.size} user drop-off reasons from an ad simulation.
Group them into 5-8 categories. For each category provide:
- category: short label in Chinese (≤10 chars)
- percentage: % of total reasons in this category
- count: number of reasons in this category
- quotes: 3 representative original quotes (or fewer if category has <3)

All counts must sum to ${reasons.size}. All percentages must sum to ~100.

Reasons:
$reasonList

Output JSON array:
[
  {"category": "价格太高", "percentage": 35.0, "count": 7, "quotes": ["原文1", "原文2", "原文3"]},
  ...
]
        """.trimIndent()
    }

    private fun parseClusteredReasons(text: String): List<ClusteredReason> {
        return try {
            objectMapper.readValue<List<ClusteredReason>>(text)
        } catch (e: Exception) {
            logger.warn("Failed to parse clustered reasons JSON: {}", e.message?.take(100))
            emptyList()
        }
    }

    fun analyzeSegments(samples: List<DecisionSample>): List<SegmentInsight> {
        if (samples.isEmpty()) return emptyList()

        val insights = mutableListOf<SegmentInsight>()

        val ageBuckets = mapOf(
            "18-24" to { sample: DecisionSample -> sample.agent.persona.age in 18..24 },
            "25-30" to { sample: DecisionSample -> sample.agent.persona.age in 25..30 },
            "31-40" to { sample: DecisionSample -> sample.agent.persona.age in 31..40 },
            "40+" to { sample: DecisionSample -> sample.agent.persona.age > 40 }
        )
        insights.add(buildSegmentInsight("age", samples, ageBuckets))

        val genderBuckets = samples.map { it.agent.persona.gender }.distinct().associateWith { gender ->
            { sample: DecisionSample -> sample.agent.persona.gender == gender }
        }
        insights.add(buildSegmentInsight("gender", samples, genderBuckets))

        val incomeBuckets = IncomeLevel.entries.associate { level ->
            level.name to { sample: DecisionSample -> sample.agent.persona.income == level }
        }
        insights.add(buildSegmentInsight("income", samples, incomeBuckets))

        val priceSensitivityBuckets = SensitivityLevel.entries.associate { level ->
            level.name to { sample: DecisionSample -> sample.agent.persona.consumptionHabits.priceSensitivity == level }
        }
        insights.add(buildSegmentInsight("priceSensitivity", samples, priceSensitivityBuckets))

        return insights
    }

    private fun buildSegmentInsight(
        dimension: String,
        samples: List<DecisionSample>,
        buckets: Map<String, (DecisionSample) -> Boolean>
    ): SegmentInsight {
        val totalConverted = samples.count { isConverted(it.decisions) }
        val avgRate = if (samples.isNotEmpty()) totalConverted.toDouble() / samples.size else 0.0

        val segments = buckets.mapNotNull { (label, predicate) ->
            val matched = samples.filter(predicate)
            if (matched.isEmpty()) return@mapNotNull null
            val converted = matched.count { isConverted(it.decisions) }
            val rate = converted.toDouble() / matched.size
            SegmentStat(
                label = label,
                agentCount = matched.size,
                convertedCount = converted,
                conversionRate = rate
            )
        }

        if (segments.size <= 1) {
            return SegmentInsight(dimension = dimension, segments = segments)
        }

        val maxRate = segments.maxOf { it.conversionRate }
        val minRate = segments.minOf { it.conversionRate }
        val highlighted = segments.map { segment ->
            when {
                segment.conversionRate == maxRate && avgRate > 0 && maxRate > avgRate * 1.5 ->
                    segment.copy(highlight = "highest")
                segment.conversionRate == minRate && avgRate > 0 && minRate < avgRate * 0.5 ->
                    segment.copy(highlight = "lowest")
                else -> segment
            }
        }

        return SegmentInsight(dimension = dimension, segments = highlighted)
    }

    private fun isConverted(decisions: Decisions): Boolean {
        return decisions.attention.passed && decisions.click.passed && decisions.conversion.passed
    }

    private fun buildAgentUtilization(
        agents: List<Agent>,
        deliveryPlan: DeliveryPlan?,
        placements: List<AdPlacement>
    ): AgentUtilization? {
        if (agents.isEmpty()) return null

        val reachedAgents = agents.filter { placementTraceSamples(it).isNotEmpty() }
        val reachedCount = reachedAgents.size
        val exposuresPerReachedAgent = reachedAgents.map { placementTraceSamples(it).size }
        val placementCoverage = placements.mapIndexed { placementIndex, _ ->
            val summary = deliveryPlan?.placementSummaries?.firstOrNull { it.placementIndex == placementIndex }
            val reached = summary?.uniqueAgentsReached
                ?: agents.count { agent -> placementTraceSamples(agent).any { it.placementIndex == placementIndex } }
            val eligible = summary?.eligibleAgents ?: reached
            PlacementCoverage(
                placementIndex = placementIndex,
                uniqueAgentsReached = reached,
                uniqueReachRate = if (agents.isNotEmpty()) reached.toDouble() / agents.size else 0.0,
                plannedSamples = summary?.plannedSamples ?: reached,
                eligibleAgents = eligible
            )
        }
        val searchSummary = deliveryPlan?.placementSummaries
            ?.filter { summary -> placements.getOrNull(summary.placementIndex)?.placementType == PlacementType.SEARCH }
            .orEmpty()
        val searchEligibleRate = if (searchSummary.isNotEmpty()) {
            searchSummary.map { summary -> summary.eligibleAgents.toDouble() / agents.size }.average()
        } else {
            0.0
        }

        return AgentUtilization(
            uniqueAgentsReached = reachedCount,
            uniqueReachRate = reachedCount.toDouble() / agents.size,
            averageExposuresPerReachedAgent = if (exposuresPerReachedAgent.isNotEmpty()) exposuresPerReachedAgent.average() else 0.0,
            averagePlacementsPerReachedAgent = if (exposuresPerReachedAgent.isNotEmpty()) exposuresPerReachedAgent.average() else 0.0,
            searchEligibleRate = searchEligibleRate,
            placementCoverage = placementCoverage
        )
    }

    fun generateTopInsights(
        segments: List<SegmentInsight>,
        funnel: Funnel,
        clusteredReasons: ClusteredDropOffReasons?,
        stageBlockers: List<StageBlocker>
    ): List<InsightSummary> {
        val insights = mutableListOf<InsightSummary>()

        val allSegments = segments.flatMap { insight ->
            insight.segments.map { segment -> Triple(insight.dimension, segment.label, segment.conversionRate) }
        }.filter { it.third > 0 }

        val best = allSegments.maxByOrNull { it.third }
        if (best != null) {
            insights.add(InsightSummary("转化率最高人群：${best.first}=${best.second}，转化率 ${"%.1f".format(best.third * 100)}%"))
        }

        val worst = allSegments.minByOrNull { it.third }
        if (worst != null && worst != best) {
            insights.add(InsightSummary("转化率最低人群：${worst.first}=${worst.second}，转化率 ${"%.1f".format(worst.third * 100)}%"))
        }

        val funnelSteps = listOf(
            "曝光→注意" to (1.0 - funnel.attention.rate),
            "注意→点击" to (1.0 - funnel.click.rate),
            "点击→转化" to (1.0 - funnel.conversion.rate)
        )
        val biggestDrop = funnelSteps.maxByOrNull { it.second }
        if (biggestDrop != null && biggestDrop.second > 0) {
            insights.add(InsightSummary("最大流失环节：${biggestDrop.first}，流失率 ${"%.1f".format(biggestDrop.second * 100)}%"))
        }

        val topReason = clusteredReasons?.let {
            val allReasons = it.attentionToClick + it.clickToConversion
            allReasons.maxByOrNull { reason -> reason.count }
        }
        if (topReason != null) {
            insights.add(InsightSummary("首要流失原因：${topReason.category}（占比 ${"%.1f".format(topReason.percentage)}%）"))
        }

        val blocker = stageBlockers.firstOrNull()
        if (blocker != null) {
            insights.add(InsightSummary("当前最强阻塞因素：${blocker.summary}"))
        }

        return insights
    }

    private data class PlacementSample(
        val placementIndex: Int,
        val decisions: Decisions
    )

    data class DecisionSample(
        val agent: Agent,
        val decisions: Decisions
    )

    private data class AggregateSnapshot(
        val successfulSamples: Int,
        val metrics: Metrics,
        val simulatedMetrics: SimulatedFunnelMetrics,
        val estimatedMetrics: EstimatedBusinessMetrics,
        val funnel: Funnel,
        val dropOffReasons: DropOffReasons,
        val clusteredReasons: ClusteredDropOffReasons?,
        val segmentInsights: List<SegmentInsight>,
        val topInsights: List<InsightSummary>,
        val stageBlockers: List<StageBlocker>,
        val recommendations: List<ActionRecommendation>
    )
}
