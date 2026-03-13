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
     * Aggregate agent decisions into emerged metrics.
     * Uses LLM to cluster drop-off reasons, computes segment insights.
     */
    suspend fun aggregate(agents: List<Agent>, budget: Long, placements: List<AdPlacement> = emptyList(), chatModel: ChatModel? = null): SimulationResults {
        val withDecisions = agents.filter { it.decisions != null }
        val total = agents.size
        val successful = withDecisions.size

        val noticed = withDecisions.count { it.decisions!!.attention.passed }
        val clicked = withDecisions.filter { it.decisions!!.attention.passed }
            .count { it.decisions!!.click.passed }
        val converted = withDecisions.filter { it.decisions!!.attention.passed && it.decisions!!.click.passed }
            .count { it.decisions!!.conversion.passed }

        val attentionRate = if (successful > 0) noticed.toDouble() / successful else 0.0
        val ctr = if (noticed > 0) clicked.toDouble() / noticed else 0.0
        val cvr = if (clicked > 0) converted.toDouble() / clicked else 0.0
        val overallConversionRate = if (successful > 0) converted.toDouble() / successful else 0.0

        // CPA calculation:
        // Each simulated agent = a user who actually SAW the ad.
        // Total impressions (from CPM benchmark) include users who never saw the ad.
        // viewability rate converts impressions → actual viewers.
        // Agent's overallConversionRate = conversion rate among viewers.
        // CPA = budget / (impressions × viewability × agentConversionRate)
        val estimatedImpressions = estimateImpressions(placements, budget)
        val viewabilityRate = 0.50 // industry average: ~50% of impressions are actually viewed
        val estimatedViewers = estimatedImpressions * viewabilityRate
        val estimatedConversions = estimatedViewers * overallConversionRate
        val estimatedCPA = if (estimatedConversions > 0) budget.toDouble() / estimatedConversions else null

        logger.info("CPA calc: impressions={}, viewability={}, viewers={}, agentCVR={}, conversions={}, CPA={}",
            estimatedImpressions.toLong(), viewabilityRate, estimatedViewers.toLong(),
            "%.4f".format(overallConversionRate), estimatedConversions.toLong(), estimatedCPA?.let { "%.2f".format(it) })

        val dropOffReasons = buildDropOffReasons(withDecisions)

        // LLM-based reason clustering
        val clusteredReasons = if (chatModel != null) {
            try {
                clusterDropOffReasons(withDecisions, chatModel)
            } catch (e: Exception) {
                logger.warn("Failed to cluster reasons via LLM, skipping: {}", e.message?.take(150))
                null
            }
        } else null

        // Segment analysis (pure computation)
        val segmentInsights = analyzeSegments(withDecisions)

        val funnel = Funnel(
            exposure = FunnelStage(successful, 1.0),
            attention = FunnelStage(noticed, attentionRate),
            click = FunnelStage(clicked, ctr),
            conversion = FunnelStage(converted, cvr)
        )

        // Generate top insights
        val topInsights = generateTopInsights(segmentInsights, funnel, clusteredReasons)

        logger.info("Aggregation complete: {}/{} agents, noticed={}, clicked={}, converted={}",
            successful, total, noticed, clicked, converted)

        return SimulationResults(
            totalAgents = total,
            successfulAgents = successful,
            metrics = Metrics(
                attentionRate = attentionRate,
                ctr = ctr,
                cvr = cvr,
                overallConversionRate = overallConversionRate,
                estimatedCPA = estimatedCPA
            ),
            funnel = funnel,
            dropOffReasons = dropOffReasons,
            clusteredReasons = clusteredReasons,
            segmentInsights = segmentInsights,
            topInsights = topInsights
        )
    }

    /**
     * Estimate total impressions from budget, using CPM benchmarks per placement type.
     * Each placement's budget is divided by its CPM to get impressions, then summed.
     */
    private fun estimateImpressions(placements: List<AdPlacement>, totalBudget: Long): Double {
        if (placements.isEmpty()) {
            // Fallback: blended CPM
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
        PlacementType.INFO_FEED -> 40.0       // algorithm-recommended feed ads
        PlacementType.SEARCH -> 80.0           // user-initiated search, more targeted
        PlacementType.KOL_SEEDING -> 300.0     // influencer content, includes talent fee
        PlacementType.SHORT_VIDEO -> 50.0      // short video ads
        PlacementType.SPLASH_SCREEN -> 25.0    // splash screen, massive reach but low engagement
        PlacementType.LIVESTREAM -> 150.0       // livestream promotion
        PlacementType.HASHTAG_CHALLENGE -> 200.0 // branded hashtag challenges
        PlacementType.SHOPPING -> 60.0          // in-app shopping promotions
    }

    private fun buildDropOffReasons(agents: List<Agent>): DropOffReasons {
        val noticedNotClicked = agents.filter {
            it.decisions!!.attention.passed && !it.decisions!!.click.passed
        }
        val clickedNotConverted = agents.filter {
            it.decisions!!.attention.passed && it.decisions!!.click.passed && !it.decisions!!.conversion.passed
        }

        return DropOffReasons(
            attentionToClick = groupReasons(noticedNotClicked.map { it.decisions!!.click.reasoning }),
            clickToConversion = groupReasons(clickedNotConverted.map { it.decisions!!.conversion.reasoning })
        )
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

    // --- LLM-based reason clustering ---

    private suspend fun clusterDropOffReasons(agents: List<Agent>, chatModel: ChatModel): ClusteredDropOffReasons {
        val noticedNotClicked = agents.filter {
            it.decisions!!.attention.passed && !it.decisions!!.click.passed
        }
        val clickedNotConverted = agents.filter {
            it.decisions!!.attention.passed && it.decisions!!.click.passed && !it.decisions!!.conversion.passed
        }

        val attnToClick = clusterReasons(
            noticedNotClicked.map { it.decisions!!.click.reasoning },
            chatModel
        )
        val clickToConv = clusterReasons(
            clickedNotConverted.map { it.decisions!!.conversion.reasoning },
            chatModel
        )

        return ClusteredDropOffReasons(
            attentionToClick = attnToClick,
            clickToConversion = clickToConv
        )
    }

    private suspend fun clusterReasons(reasons: List<String>, chatModel: ChatModel): List<ClusteredReason> {
        if (reasons.isEmpty()) return emptyList()

        // If < 5 reasons, skip LLM clustering — return one ClusteredReason per unique reason
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
        try {
            val response = chatModel.chat(
                ChatRequest.builder()
                    .messages(listOf(
                        SystemMessage.from("You are a data analyst. Cluster the given drop-off reasons into categories. Output valid JSON only."),
                        UserMessage.from(prompt)
                    ))
                    .build()
            )
            val text = response.aiMessage().text()
            return parseClusteredReasons(text, reasons.size)
        } catch (e: Exception) {
            logger.warn("LLM clustering failed: {}", e.message?.take(150))
            // Fallback: simple grouping
            val total = reasons.size
            return reasons.groupingBy { it }
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
        val reasonList = reasons.mapIndexed { i, r -> "${i + 1}. $r" }.joinToString("\n")
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

    private fun parseClusteredReasons(text: String, totalCount: Int): List<ClusteredReason> {
        return try {
            objectMapper.readValue<List<ClusteredReason>>(text)
        } catch (e: Exception) {
            logger.warn("Failed to parse clustered reasons JSON: {}", e.message?.take(100))
            emptyList()
        }
    }

    // --- Segment analysis (pure computation) ---

    fun analyzeSegments(agents: List<Agent>): List<SegmentInsight> {
        if (agents.isEmpty()) return emptyList()

        val insights = mutableListOf<SegmentInsight>()

        // Age segments
        val ageBuckets = mapOf(
            "18-24" to { a: Agent -> a.persona.age in 18..24 },
            "25-30" to { a: Agent -> a.persona.age in 25..30 },
            "31-40" to { a: Agent -> a.persona.age in 31..40 },
            "40+" to { a: Agent -> a.persona.age > 40 }
        )
        insights.add(buildSegmentInsight("age", agents, ageBuckets))

        // Gender segments
        val genderBuckets = agents.map { it.persona.gender }.distinct().associateWith { g ->
            { a: Agent -> a.persona.gender == g }
        }
        insights.add(buildSegmentInsight("gender", agents, genderBuckets))

        // Income segments
        val incomeBuckets = IncomeLevel.entries.associate { level ->
            level.name to { a: Agent -> a.persona.income == level }
        }
        insights.add(buildSegmentInsight("income", agents, incomeBuckets))

        // Price sensitivity segments
        val psBuckets = SensitivityLevel.entries.associate { level ->
            level.name to { a: Agent -> a.persona.consumptionHabits.priceSensitivity == level }
        }
        insights.add(buildSegmentInsight("priceSensitivity", agents, psBuckets))

        return insights
    }

    private fun buildSegmentInsight(
        dimension: String,
        agents: List<Agent>,
        buckets: Map<String, (Agent) -> Boolean>
    ): SegmentInsight {
        val totalConverted = agents.count { isConverted(it) }
        val avgRate = if (agents.isNotEmpty()) totalConverted.toDouble() / agents.size else 0.0

        val segments = buckets.mapNotNull { (label, predicate) ->
            val matched = agents.filter(predicate)
            if (matched.isEmpty()) return@mapNotNull null
            val converted = matched.count { isConverted(it) }
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

        // Mark highlights
        val maxRate = segments.maxOf { it.conversionRate }
        val minRate = segments.minOf { it.conversionRate }
        val highlighted = segments.map { seg ->
            when {
                seg.conversionRate == maxRate && avgRate > 0 && maxRate > avgRate * 1.5 ->
                    seg.copy(highlight = "highest")
                seg.conversionRate == minRate && avgRate > 0 && minRate < avgRate * 0.5 ->
                    seg.copy(highlight = "lowest")
                else -> seg
            }
        }

        return SegmentInsight(dimension = dimension, segments = highlighted)
    }

    private fun isConverted(agent: Agent): Boolean {
        val d = agent.decisions ?: return false
        return d.attention.passed && d.click.passed && d.conversion.passed
    }

    // --- Top insights generation (pure string) ---

    fun generateTopInsights(
        segments: List<SegmentInsight>,
        funnel: Funnel,
        clusteredReasons: ClusteredDropOffReasons?
    ): List<InsightSummary> {
        val insights = mutableListOf<InsightSummary>()

        // Best segment
        val allSegments = segments.flatMap { insight ->
            insight.segments.map { seg -> Triple(insight.dimension, seg.label, seg.conversionRate) }
        }.filter { it.third > 0 }
        val best = allSegments.maxByOrNull { it.third }
        if (best != null) {
            insights.add(InsightSummary("转化率最高人群：${best.first}=${best.second}，转化率 ${"%.1f".format(best.third * 100)}%"))
        }

        // Worst segment
        val worst = allSegments.minByOrNull { it.third }
        if (worst != null && worst != best) {
            insights.add(InsightSummary("转化率最低人群：${worst.first}=${worst.second}，转化率 ${"%.1f".format(worst.third * 100)}%"))
        }

        // Biggest funnel drop
        val funnelSteps = listOf(
            "曝光→注意" to (1.0 - funnel.attention.rate),
            "注意→点击" to (1.0 - funnel.click.rate),
            "点击→转化" to (1.0 - funnel.conversion.rate)
        )
        val biggestDrop = funnelSteps.maxByOrNull { it.second }
        if (biggestDrop != null && biggestDrop.second > 0) {
            insights.add(InsightSummary("最大流失环节：${biggestDrop.first}，流失率 ${"%.1f".format(biggestDrop.second * 100)}%"))
        }

        // Top drop-off reason
        val topReason = clusteredReasons?.let {
            val allReasons = it.attentionToClick + it.clickToConversion
            allReasons.maxByOrNull { r -> r.count }
        }
        if (topReason != null) {
            insights.add(InsightSummary("首要流失原因：${topReason.category}（占比 ${"%.1f".format(topReason.percentage)}%）"))
        }

        return insights
    }
}
