package com.adsim.simulation

import com.adsim.config.SimulationConfig
import com.adsim.model.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

@Component
class SimulationEngine(
    private val agentRepository: AgentRepository,
    private val config: SimulationConfig,
    private val priorCalibrationService: PriorCalibrationService
) {
    private val logger = LoggerFactory.getLogger(SimulationEngine::class.java)
    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    suspend fun run(
        input: SimulationInput,
        agents: List<Agent>,
        chatModel: ChatModel,
        concurrency: Int? = null,
        exposureEvents: List<ExposureEvent>? = null,
        onProgress: (completed: Int) -> Unit
    ) {
        val actualConcurrency = concurrency ?: config.maxConcurrency
        val placements = input.adPlacements
        val plannedExposureEvents = exposureEvents ?: buildLegacyExposureEvents(input, agents)
        logger.info(
            "Running simulation for {} agents across {} planned exposures ({} placements), concurrency: {}",
            agents.size,
            plannedExposureEvents.size,
            placements.size,
            actualConcurrency
        )

        if (placements.isEmpty()) {
            agentRepository.saveAll(agents)
            logger.warn("Simulation input has no placements, skipping decision generation")
            return
        }

        if (plannedExposureEvents.isEmpty()) {
            agentRepository.saveAll(agents)
            logger.warn("No exposure events planned, skipping decision generation")
            return
        }

        val exposureEventsByAgent = plannedExposureEvents.groupBy { it.agentId }
        val semaphore = Semaphore(actualConcurrency)
        val completed = AtomicInteger(0)

        val decidedAgents = coroutineScope {
            agents.mapIndexed { index, agent ->
                async {
                    semaphore.withPermit {
                        val agentEvents = exposureEventsByAgent[agentKey(agent, index)].orEmpty()
                        simulateAgentAcrossPlacements(input, agent, agentEvents, chatModel) {
                            onProgress(completed.incrementAndGet())
                        }
                    }
                }
            }.awaitAll()
        }

        agentRepository.saveAll(decidedAgents)

        val placementSamples = decidedAgents.flatMap { it.placementDecisions }
        val noticed = placementSamples.count { it.decisions.attention.passed }
        val clicked = placementSamples.count { it.decisions.attention.passed && it.decisions.click.passed }
        val converted = placementSamples.count {
            it.decisions.attention.passed && it.decisions.click.passed && it.decisions.conversion.passed
        }
        logger.info(
            "Simulation complete: {} agents, exposures={}, noticed={}, clicked={}, converted={}",
            agents.size,
            placementSamples.size,
            noticed,
            clicked,
            converted
        )
    }

    /**
     * Run the 3-stage pipeline for each placement and keep the first placement as the
     * legacy primary decisions field for backward compatibility.
     */
    private suspend fun simulateAgentAcrossPlacements(
        input: SimulationInput,
        agent: Agent,
        exposureEvents: List<ExposureEvent>,
        chatModel: ChatModel,
        onPlacementCompleted: () -> Unit
    ): Agent {
        var campaignState = initialCampaignState(agent)
        val sortedEvents = exposureEvents.sortedBy { it.sequence }
        val placementOutcomes = sortedEvents.mapNotNull { event ->
            val placement = input.adPlacements.getOrNull(event.placementIndex)
            if (placement == null) {
                onPlacementCompleted()
                null
            } else {
                try {
                    val outcome = simulatePlacementStaged(input, placement, agent, campaignState, chatModel, event)
                    if (outcome != null) {
                        campaignState = outcome.updatedCampaignState
                        PlacementOutcome(
                            placementIndex = event.placementIndex,
                            platform = placement.platform,
                            placementType = placement.placementType,
                            exposureEvent = event,
                            attention = outcome.decisions.attention,
                            click = outcome.decisions.click,
                            conversion = outcome.decisions.conversion
                        )
                    } else {
                        null
                    }
                } finally {
                    onPlacementCompleted()
                }
            }
        }
        val placementDecisions = placementOutcomes.map { placementOutcome ->
            PlacementDecisions(
                placementIndex = placementOutcome.placementIndex,
                platform = placementOutcome.platform,
                placementType = placementOutcome.placementType,
                decisions = placementOutcome.decisions,
                exposureEvent = placementOutcome.exposureEvent
            )
        }

        return agent.copy(
            decisions = placementOutcomes.firstOrNull()?.decisions,
            placementDecisions = placementDecisions,
            placementOutcomes = placementOutcomes,
            campaignState = campaignState
        )
    }

    /**
     * 3-stage pipeline: each stage is a separate LLM call with only the information
     * visible at that funnel stage. Agents who fail a stage skip subsequent stages.
     */
    private suspend fun simulatePlacementStaged(
        input: SimulationInput,
        placement: AdPlacement,
        agent: Agent,
        campaignState: AgentCampaignState,
        chatModel: ChatModel,
        exposureEvent: ExposureEvent? = null
    ): PlacementSimulationOutcome? {
        val agentDesc = "${agent.persona.name}(${agent.persona.age}/${agent.persona.gender})"
        val startTime = System.currentTimeMillis()
        val prior = priorCalibrationService.loadPrior(placement.platform, placement.placementType, input.product.category)

        val stage1 = callStageWithRetry(
            "attention",
            agent,
            chatModel,
            buildStage1Prompt(input, placement, agent, campaignState, exposureEvent)
        ) ?: return null
        val attentionDecision = toStageDecision(stage1, prior?.baseAttention)

        if (!attentionDecision.passed) {
            val elapsed = System.currentTimeMillis() - startTime
            logger.info(
                "Agent {} [{}] placement {} decided: attn=false ({}ms)",
                agent.id,
                agentDesc,
                placement.placementType,
                elapsed
            )
            return PlacementSimulationOutcome(
                decisions = Decisions(
                    attention = attentionDecision,
                    click = StageDecision(
                        passed = false,
                        reasoning = "没有注意到这条广告",
                        factors = emptyList(),
                        likelihoodBand = LikelihoodBand.VERY_LOW,
                        probability = 0.0,
                        negativeFactors = listOf("no_attention")
                    ),
                    conversion = StageDecision(
                        passed = false,
                        reasoning = "没有注意到这条广告",
                        factors = emptyList(),
                        likelihoodBand = LikelihoodBand.VERY_LOW,
                        probability = 0.0,
                        negativeFactors = listOf("no_attention")
                    )
                ),
                updatedCampaignState = nextCampaignState(campaignState, noticed = false, clicked = false, converted = false)
            )
        }

        val stage2 = callStageWithRetry(
            "click",
            agent,
            chatModel,
            buildStage2Prompt(input, placement, agent, campaignState, exposureEvent)
        ) ?: return null
        val clickDecision = toStageDecision(stage2, prior?.baseClick)

        if (!clickDecision.passed) {
            val elapsed = System.currentTimeMillis() - startTime
            logger.info(
                "Agent {} [{}] placement {} decided: attn=true, click=false ({}ms)",
                agent.id,
                agentDesc,
                placement.placementType,
                elapsed
            )
            return PlacementSimulationOutcome(
                decisions = Decisions(
                    attention = attentionDecision,
                    click = clickDecision,
                    conversion = StageDecision(
                        passed = false,
                        reasoning = "没有点击广告",
                        factors = emptyList(),
                        likelihoodBand = LikelihoodBand.VERY_LOW,
                        probability = 0.0,
                        negativeFactors = listOf("no_click")
                    )
                ),
                updatedCampaignState = nextCampaignState(campaignState, noticed = true, clicked = false, converted = false)
            )
        }

        val stage3 = callStageWithRetry(
            "conversion",
            agent,
            chatModel,
            buildStage3Prompt(input, placement, agent, campaignState, exposureEvent)
        ) ?: return null
        val conversionDecision = toStageDecision(stage3, prior?.baseConversion)

        val elapsed = System.currentTimeMillis() - startTime
        logger.info(
            "Agent {} [{}] placement {} decided: attn=true, click=true, conv={} prob={} ({}ms)",
            agent.id,
            agentDesc,
            placement.placementType,
            conversionDecision.passed,
            conversionDecision.probability,
            elapsed
        )

        return PlacementSimulationOutcome(
            decisions = Decisions(
                attention = attentionDecision,
                click = clickDecision,
                conversion = conversionDecision
            ),
            updatedCampaignState = nextCampaignState(
                campaignState,
                noticed = attentionDecision.passed,
                clicked = clickDecision.passed,
                converted = conversionDecision.passed
            )
        )
    }

    /**
     * Call a single stage with retry logic.
     * Returns null if all retries are exhausted.
     */
    private suspend fun callStageWithRetry(
        stageName: String,
        agent: Agent,
        chatModel: ChatModel,
        messages: List<ChatMessage>
    ): DecisionDto? {
        val agentDesc = "${agent.persona.name}(${agent.persona.age}/${agent.persona.gender})"
        var lastException: Exception? = null

        repeat(config.maxRetries) { attempt ->
            try {
                return callStage(messages, chatModel)
            } catch (e: Exception) {
                lastException = e
                logger.warn("Agent {} [{}] stage '{}' attempt {}/{} failed: {}",
                    agent.id, agentDesc, stageName, attempt + 1, config.maxRetries, e.message?.take(150))
                delay(2000L * (attempt + 1))
            }
        }

        logger.error("Agent {} [{}] stage '{}' GAVE UP after {} retries, last error: {}",
            agent.id, agentDesc, stageName, config.maxRetries, lastException?.message?.take(200))
        return null
    }

    private fun callStage(messages: List<ChatMessage>, chatModel: ChatModel): DecisionDto {
        val response = chatModel.chat(
            ChatRequest.builder().messages(messages).build()
        )
        val text = response.aiMessage().text()
        return parseDecision(text)
    }

    // ── Stage 1: Attention ──────────────────────────────────────────────

    private fun buildStage1Prompt(
        input: SimulationInput,
        placement: AdPlacement,
        agent: Agent,
        campaignState: AgentCampaignState,
        exposureEvent: ExposureEvent? = null
    ): List<ChatMessage> {
        val persona = agent.persona
        val platform = placement.platform
        val placementType = placement.placementType
        val format = placement.format

        val feedItemDesc = buildFeedItemDescription(
            placementType,
            format,
            input.product,
            placement.creativeDescription,
            exposureEvent
        )

        val system = """
You are ${persona.name}, ${persona.age} years old, ${persona.gender}.
Income: ${persona.income}, City tier: ${persona.cityTier}
Interests: ${persona.interests.joinToString(", ")}
Platform usage: ${persona.platformBehavior.dailyUsageMinutes} min/day, prefers: ${persona.platformBehavior.contentPreferences.joinToString(", ")}
Purchase frequency on platform: ${persona.platformBehavior.purchaseFrequency}

You are browsing $platform, scrolling through your feed. You've already seen 30+ pieces of content.
Your attention is scarce — most content you scroll past in 1-2 seconds.
You can see the brand and product name on the thumbnail, but you don't know the price or detailed specs.
This is a quick glance — you decide in 1-2 seconds whether to stop or keep scrolling.
Campaign context: you've already seen this brand ${campaignState.placementsSeen} times in this simulation, fatigue score ${campaignState.fatigueScore}, brand familiarity ${campaignState.brandFamiliarity}.

Available factor tags:
Positive: interest_match, creative_appeal, entertainment_value, brand_trust, social_proof
Negative: no_interest, creative_boring, ad_fatigue, wrong_format, no_brand_trust, frequency_overload

Output valid JSON only. Reasoning in Chinese (中文).
        """.trimIndent()

        val user = """
In your feed you see:
$feedItemDesc

Would you stop scrolling and look at this content more closely?

Output JSON:
{"likelihoodBand": "VERY_LOW|LOW|MEDIUM|HIGH|VERY_HIGH", "reasoning": "中文理由(1-2句)", "positiveFactors": ["tag1"], "negativeFactors": ["tag2"]}
        """.trimIndent()

        return listOf(SystemMessage.from(system), UserMessage.from(user))
    }

    /**
     * Generate a feed-item description including brand name and brief creative info.
     * Simulates what a user actually sees in 1-2 seconds while scrolling.
     * Does NOT include price or detailed product specs.
     */
    private fun buildFeedItemDescription(
        placementType: PlacementType,
        format: CreativeFormat,
        product: Product,
        creativeDescription: String,
        exposureEvent: ExposureEvent? = null
    ): String {
        val brand = product.brandName
        val productName = product.name
        val formatHint = when (format) {
            CreativeFormat.VIDEO -> "视频"
            CreativeFormat.IMAGE -> "图片"
            CreativeFormat.IMAGE_TEXT -> "图文笔记"
            CreativeFormat.CAROUSEL -> "多图轮播"
        }
        val creativeBrief = if (creativeDescription.isNotBlank()) creativeDescription.take(60) else ""

        return when (placementType) {
            PlacementType.KOL_SEEDING ->
                "【达人推荐】一位博主发布的${formatHint}，封面标题含「${brand} ${productName}」。${if (creativeBrief.isNotBlank()) "内容预览：$creativeBrief" else ""}"
            PlacementType.INFO_FEED ->
                "【信息流广告】一条${formatHint}广告，品牌「${brand}」，产品「${productName}」，底部有'广告'标签。${if (creativeBrief.isNotBlank()) "广告文案：$creativeBrief" else ""}"
            PlacementType.SEARCH -> {
                val intentHint = exposureEvent?.deliveryContext?.intentLevel?.let {
                    "搜索意图强度约 ${(it * 100).toInt()}%"
                } ?: "你刚产生了相关搜索需求"
                "【搜索结果】基于当前需求场景，$intentHint，结果中有一条带'推广'标签的${formatHint}：「${brand} ${productName}」"
            }
            PlacementType.SHORT_VIDEO ->
                "【短视频】信息流中自动播放的${formatHint}，左下角显示「${brand}」品牌名。${if (creativeBrief.isNotBlank()) "视频内容：$creativeBrief" else ""}"
            PlacementType.HASHTAG_CHALLENGE ->
                "【话题挑战】发现页出现与「${brand} ${productName}」相关的热门话题挑战"
            PlacementType.SPLASH_SCREEN ->
                "【开屏广告】打开 App 时全屏展示「${brand} ${productName}」的${formatHint}广告"
            PlacementType.LIVESTREAM ->
                "【直播推荐】一场主播正在展示「${brand}」产品的直播间推荐"
            PlacementType.SHOPPING ->
                "【商城推荐】购物频道中出现「${brand} ${productName}」的${formatHint}商品卡片"
        }
    }

    // ── Stage 2: Click ──────────────────────────────────────────────────

    private fun buildStage2Prompt(
        input: SimulationInput,
        placement: AdPlacement,
        agent: Agent,
        campaignState: AgentCampaignState,
        exposureEvent: ExposureEvent? = null
    ): List<ChatMessage> {
        val persona = agent.persona
        val ctx = persona.consumerContext
        val platform = placement.platform
        val placementType = placement.placementType

        val awarenessLine = if (ctx.brandAwareness != "never_heard") {
            val desc = when (ctx.brandAwareness) {
                "heard_not_tried" -> "听说过但从未购买或使用"
                "tried_once" -> "曾经买过/试过，但不常用"
                "regular_user" -> "经常购买，是这个品牌的老用户"
                else -> ""
            }
            "\nYour awareness of this brand: $desc"
        } else ""

        val placementDesc = when (placementType) {
            PlacementType.KOL_SEEDING -> "KOL/blogger recommendation"
            PlacementType.INFO_FEED -> "in-feed sponsored post"
            PlacementType.SEARCH -> "search promoted result"
            PlacementType.SHORT_VIDEO -> "short video ad"
            PlacementType.HASHTAG_CHALLENGE -> "hashtag challenge promotion"
            PlacementType.SPLASH_SCREEN -> "app splash screen ad"
            PlacementType.LIVESTREAM -> "livestream product showcase"
            PlacementType.SHOPPING -> "shopping section product card"
        }

        val intentLine = exposureEvent?.deliveryContext?.intentLevel?.let {
            "Current intent level for this exposure: ${(it * 100).toInt()}%."
        } ?: ""

        val system = """
You are ${persona.name}, ${persona.age} years old, ${persona.gender}.
Income: ${persona.income}, City tier: ${persona.cityTier}
Interests: ${persona.interests.joinToString(", ")}
Platform usage: ${persona.platformBehavior.dailyUsageMinutes} min/day, prefers: ${persona.platformBehavior.contentPreferences.joinToString(", ")}
Purchase frequency on platform: ${persona.platformBehavior.purchaseFrequency}
Price sensitivity: ${persona.consumptionHabits.priceSensitivity}
Decision speed: ${persona.consumptionHabits.decisionSpeed}
Brand loyalty: ${persona.consumptionHabits.brandLoyalty}
You've seen ${ctx.recentAdExposure} similar product ads/promotions this week.
In this campaign simulation, you've already seen this brand ${campaignState.placementsSeen} times, clicked ${campaignState.clickedCount} times, fatigue score ${campaignState.fatigueScore}.
$intentLine

You stopped scrolling and are now looking at this content more closely on $platform.

Available factor tags:
Positive: interest_match, creative_appeal, brand_trust, social_proof, need_match, entertainment_value
Negative: no_interest, creative_boring, no_brand_trust, ad_fatigue, wrong_format, no_need, frequency_overload

Output valid JSON only. Reasoning in Chinese (中文).
        """.trimIndent()

        val user = """
You can see:
- Title/headline: "${placement.creativeDescription.ifBlank { input.product.name }}"
- Brand: ${input.product.brandName}
- Content type: $placementDesc$awarenessLine

You still don't know the exact price or detailed product specs.
Would you tap/click to see the full details?

Output JSON:
{"likelihoodBand": "VERY_LOW|LOW|MEDIUM|HIGH|VERY_HIGH", "reasoning": "中文理由(1-2句)", "positiveFactors": ["tag1"], "negativeFactors": ["tag2"]}
        """.trimIndent()

        return listOf(SystemMessage.from(system), UserMessage.from(user))
    }

    // ── Stage 3: Conversion ─────────────────────────────────────────────

    private fun buildStage3Prompt(
        input: SimulationInput,
        placement: AdPlacement,
        agent: Agent,
        campaignState: AgentCampaignState,
        exposureEvent: ExposureEvent? = null
    ): List<ChatMessage> {
        val persona = agent.persona
        val ctx = persona.consumerContext
        val platform = placement.platform

        val currentUsage = if (ctx.currentBrand != null) {
            "You currently use: ${ctx.currentBrand}" +
                (if (ctx.currentProductPrice != null) " at ¥${ctx.currentProductPrice}" else "") +
                (when (ctx.satisfaction) {
                    "satisfied" -> " (satisfied)"
                    "neutral" -> " (neutral)"
                    "looking_for_alternatives" -> " (looking for alternatives)"
                    else -> ""
                })
        } else {
            "You are not currently using a similar product"
        }

        val awarenessDesc = when (ctx.brandAwareness) {
            "never_heard" -> "从未听说过这个品牌"
            "heard_not_tried" -> "听说过但从未购买或使用"
            "tried_once" -> "曾经买过/试过，但不常用"
            "regular_user" -> "经常购买，是这个品牌的老用户"
            else -> "从未听说过这个品牌"
        }

        val competitorsText = if (input.competitors.isNotEmpty()) {
            "\nCompetitors: " + input.competitors.joinToString(", ") {
                "${it.brandName} (¥${it.price}${if (it.positioning.isNotBlank()) ", ${it.positioning}" else ""})"
            }
        } else ""

        val productStageDesc = when (input.product.productStage.name) {
            "NEW_LAUNCH" -> "new product launch (no reviews yet)"
            "BESTSELLER" -> "bestseller (many positive reviews)"
            else -> "established product (some reviews available)"
        }

        val intentLine = exposureEvent?.deliveryContext?.intentLevel?.let {
            "Current intent level for this exposure: ${(it * 100).toInt()}%."
        } ?: ""

        val system = """
You are ${persona.name}, ${persona.age} years old, ${persona.gender}.
Income: ${persona.income}, City tier: ${persona.cityTier}
Interests: ${persona.interests.joinToString(", ")}
Platform usage: ${persona.platformBehavior.dailyUsageMinutes} min/day, prefers: ${persona.platformBehavior.contentPreferences.joinToString(", ")}
Purchase frequency on platform: ${persona.platformBehavior.purchaseFrequency}
Price sensitivity: ${persona.consumptionHabits.priceSensitivity}
Decision speed: ${persona.consumptionHabits.decisionSpeed}
Brand loyalty: ${persona.consumptionHabits.brandLoyalty}
Your awareness of "${input.product.brandName}": $awarenessDesc
$currentUsage
You've seen ${ctx.recentAdExposure} similar product ads/promotions this week.
In this campaign simulation, you've already seen this brand ${campaignState.placementsSeen} times and noticed it ${campaignState.noticedCount} times. Fatigue score: ${campaignState.fatigueScore}. Campaign familiarity: ${campaignState.brandFamiliarity}.
$intentLine

You've clicked through and are now viewing the full product page on $platform.

Available factor tags:
Positive: interest_match, creative_appeal, price_acceptable, brand_trust, social_proof, urgency, need_match, entertainment_value
Negative: no_interest, creative_boring, price_too_high, no_brand_trust, no_reviews, no_need, ad_fatigue, wrong_format, competitor_preference, impulse_resist, frequency_overload

Output valid JSON only. Reasoning in Chinese (中文).
        """.trimIndent()

        val descLine = if (input.product.description.isNotBlank()) {
            "\n- Description: ${input.product.description}"
        } else ""

        val user = """
Product details:
- Brand: ${input.product.brandName}
- Product: ${input.product.name} (${input.product.category})
- Price: ¥${input.product.price}
- Key selling points: ${input.product.sellingPoints}
- Product stage: $productStageDesc$descLine$competitorsText

Would you actually purchase this right now?

Output JSON:
{"likelihoodBand": "VERY_LOW|LOW|MEDIUM|HIGH|VERY_HIGH", "reasoning": "中文理由(1-2句)", "positiveFactors": ["tag1"], "negativeFactors": ["tag2"]}
        """.trimIndent()

        return listOf(SystemMessage.from(system), UserMessage.from(user))
    }

    // ── Parsing ─────────────────────────────────────────────────────────

    private fun parseDecision(content: String): DecisionDto {
        try {
            return objectMapper.readValue<DecisionDto>(content)
        } catch (e: Exception) {
            logger.error("Failed to parse stage response: {}, content: {}", e.message, content.take(200))
            throw RuntimeException("Failed to parse LLM stage response: ${e.message}", e)
        }
    }

    private data class DecisionDto(
        val passed: Boolean? = null,
        val reasoning: String,
        val factors: List<String> = emptyList(),
        val likelihoodBand: LikelihoodBand? = null,
        val probability: Double? = null,
        val positiveFactors: List<String> = emptyList(),
        val negativeFactors: List<String> = emptyList()
    )

    private data class PlacementSimulationOutcome(
        val decisions: Decisions,
        val updatedCampaignState: AgentCampaignState
    )

    private fun toStageDecision(
        dto: DecisionDto,
        priorBaseProbability: Double? = null
    ): StageDecision {
        val band = dto.likelihoodBand ?: if (dto.passed == true) LikelihoodBand.HIGH else LikelihoodBand.LOW
        val rawProbability = (dto.probability ?: sampleProbability(band)).coerceIn(0.0, 1.0)
        val probability = priorCalibrationService.applyPrior(rawProbability, priorBaseProbability)
        val passed = dto.passed ?: samplePassed(probability)
        val positiveFactors = dto.positiveFactors.ifEmpty {
            if (passed && dto.factors.isNotEmpty()) dto.factors else emptyList()
        }
        val negativeFactors = dto.negativeFactors.ifEmpty {
            if (!passed && dto.factors.isNotEmpty()) dto.factors else emptyList()
        }
        val fallbackFactors = if (passed) positiveFactors else negativeFactors

        return StageDecision(
            passed = passed,
            reasoning = dto.reasoning,
            factors = dto.factors.ifEmpty { fallbackFactors },
            likelihoodBand = band,
            probability = probability,
            positiveFactors = positiveFactors,
            negativeFactors = negativeFactors
        )
    }

    private fun sampleProbability(band: LikelihoodBand): Double {
        val range = when (band) {
            LikelihoodBand.VERY_LOW -> 0.02..0.12
            LikelihoodBand.LOW -> 0.12..0.30
            LikelihoodBand.MEDIUM -> 0.30..0.55
            LikelihoodBand.HIGH -> 0.55..0.80
            LikelihoodBand.VERY_HIGH -> 0.80..0.95
        }
        return ThreadLocalRandom.current().nextDouble(range.start, range.endInclusive)
    }

    private fun samplePassed(probability: Double): Boolean {
        return ThreadLocalRandom.current().nextDouble() < probability
    }

    private fun buildLegacyExposureEvents(input: SimulationInput, agents: List<Agent>): List<ExposureEvent> {
        return agents.flatMapIndexed { agentIndex, agent ->
            input.adPlacements.mapIndexed { placementIndex, placement ->
                ExposureEvent(
                    agentId = agentKey(agent, agentIndex),
                    placementIndex = placementIndex,
                    sequence = agentIndex * input.adPlacements.size + placementIndex,
                    deliveryContext = DeliveryContext(
                        source = "legacy_full_matrix",
                        frequency = 1,
                        intentLevel = if (placement.placementType == PlacementType.SEARCH) 1.0 else null,
                        estimatedCostWeight = 1.0
                    )
                )
            }
        }
    }

    private fun agentKey(agent: Agent, agentIndex: Int): String {
        return agent.id ?: "agent-$agentIndex"
    }

    private fun initialCampaignState(agent: Agent): AgentCampaignState {
        return AgentCampaignState(
            brandFamiliarity = agent.persona.consumerContext.brandAwareness
        )
    }

    private fun nextCampaignState(
        current: AgentCampaignState,
        noticed: Boolean,
        clicked: Boolean,
        converted: Boolean
    ): AgentCampaignState {
        val nextPlacementsSeen = current.placementsSeen + 1
        val nextNoticed = current.noticedCount + if (noticed) 1 else 0
        val nextClicked = current.clickedCount + if (clicked) 1 else 0
        val nextConverted = current.convertedCount + if (converted) 1 else 0
        val fatigueIncrease = when {
            converted -> 0
            clicked -> 1
            noticed -> 2
            else -> 1
        }
        val nextFatigue = (current.fatigueScore + fatigueIncrease).coerceAtMost(10)
        val nextBrandFamiliarity = when {
            converted || nextClicked >= 2 -> "regular_user"
            nextNoticed >= 2 -> "tried_once"
            nextPlacementsSeen >= 1 && current.brandFamiliarity == "never_heard" -> "heard_not_tried"
            else -> current.brandFamiliarity
        }

        return current.copy(
            placementsSeen = nextPlacementsSeen,
            noticedCount = nextNoticed,
            clickedCount = nextClicked,
            convertedCount = nextConverted,
            fatigueScore = nextFatigue,
            brandFamiliarity = nextBrandFamiliarity
        )
    }
}
