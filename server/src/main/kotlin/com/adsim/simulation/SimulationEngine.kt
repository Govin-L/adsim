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
import java.util.concurrent.atomic.AtomicInteger

@Component
class SimulationEngine(
    private val agentRepository: AgentRepository,
    private val config: SimulationConfig
) {
    private val logger = LoggerFactory.getLogger(SimulationEngine::class.java)
    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    suspend fun run(
        input: SimulationInput,
        agents: List<Agent>,
        chatModel: ChatModel,
        concurrency: Int? = null,
        onProgress: (completed: Int) -> Unit
    ) {
        val actualConcurrency = concurrency ?: config.maxConcurrency
        logger.info("Running simulation for {} agents, concurrency: {}", agents.size, actualConcurrency)

        val semaphore = Semaphore(actualConcurrency)
        val completed = AtomicInteger(0)

        // Call LLM for each agent using staged information disclosure (3 calls per agent)
        val decidedAgents = coroutineScope {
            agents.map { agent ->
                async {
                    semaphore.withPermit {
                        val decided = simulateAgentStaged(input, agent, chatModel)
                        onProgress(completed.incrementAndGet())
                        decided
                    }
                }
            }.awaitAll()
        }

        // Save all agents
        agentRepository.saveAll(decidedAgents)

        val withDecisions = decidedAgents.filter { it.decisions != null }
        val noticed = withDecisions.count { it.decisions!!.attention.passed }
        val clicked = withDecisions.count { it.decisions!!.click.passed }
        val converted = withDecisions.count { it.decisions!!.conversion.passed }
        logger.info("Simulation complete: {} agents, noticed={}, clicked={}, converted={}",
            agents.size, noticed, clicked, converted)
    }

    /**
     * 3-stage pipeline: each stage is a separate LLM call with only the information
     * visible at that funnel stage. Agents who fail a stage skip subsequent stages.
     */
    private suspend fun simulateAgentStaged(input: SimulationInput, agent: Agent, chatModel: ChatModel): Agent {
        val agentDesc = "${agent.persona.name}(${agent.persona.age}/${agent.persona.gender})"
        val startTime = System.currentTimeMillis()

        // Stage 1: Attention (minimal info — thumbnail only, no brand/price/product)
        val stage1 = callStageWithRetry("attention", agent, chatModel, buildStage1Prompt(input, agent))
            ?: return agent // all retries failed

        if (!stage1.passed) {
            val elapsed = System.currentTimeMillis() - startTime
            logger.info("Agent {} [{}] decided: attn=false ({}ms)", agent.id, agentDesc, elapsed)
            return agent.copy(decisions = Decisions(
                attention = StageDecision(false, stage1.reasoning, stage1.factors),
                click = StageDecision(false, "没有注意到这条广告", emptyList()),
                conversion = StageDecision(false, "没有注意到这条广告", emptyList())
            ))
        }

        // Stage 2: Click (brand + title visible, no price/specs)
        val stage2 = callStageWithRetry("click", agent, chatModel, buildStage2Prompt(input, agent))
            ?: return agent

        if (!stage2.passed) {
            val elapsed = System.currentTimeMillis() - startTime
            logger.info("Agent {} [{}] decided: attn=true, click=false ({}ms)", agent.id, agentDesc, elapsed)
            return agent.copy(decisions = Decisions(
                attention = StageDecision(true, stage1.reasoning, stage1.factors),
                click = StageDecision(false, stage2.reasoning, stage2.factors),
                conversion = StageDecision(false, "没有点击广告", emptyList())
            ))
        }

        // Stage 3: Conversion (full product details)
        val stage3 = callStageWithRetry("conversion", agent, chatModel, buildStage3Prompt(input, agent))
            ?: return agent

        val elapsed = System.currentTimeMillis() - startTime
        logger.info("Agent {} [{}] decided: attn=true, click=true, conv={} ({}ms)",
            agent.id, agentDesc, stage3.passed, elapsed)

        return agent.copy(decisions = Decisions(
            attention = StageDecision(true, stage1.reasoning, stage1.factors),
            click = StageDecision(true, stage2.reasoning, stage2.factors),
            conversion = StageDecision(stage3.passed, stage3.reasoning, stage3.factors)
        ))
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

    private fun buildStage1Prompt(input: SimulationInput, agent: Agent): List<ChatMessage> {
        val persona = agent.persona
        val placement = input.adPlacements.firstOrNull()
        val platform = placement?.platform ?: "xiaohongshu"
        val placementType = placement?.placementType ?: PlacementType.INFO_FEED
        val format = placement?.format ?: CreativeFormat.VIDEO

        val feedItemDesc = buildFeedItemDescription(placementType, format, input.product, placement?.creativeDescription ?: "")

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

Available factor tags:
Positive: interest_match, creative_appeal, entertainment_value, brand_trust, social_proof
Negative: no_interest, creative_boring, ad_fatigue, wrong_format, no_brand_trust

Output valid JSON only. Reasoning in Chinese (中文).
        """.trimIndent()

        val user = """
In your feed you see:
$feedItemDesc

Would you stop scrolling and look at this content more closely?

Output JSON:
{"passed": true/false, "reasoning": "中文理由(1-2句)", "factors": ["tag1"]}
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
        creativeDescription: String
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
            PlacementType.SEARCH ->
                "【搜索结果】你搜索了相关关键词，结果中有一条带'推广'标签的${formatHint}：「${brand} ${productName}」"
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

    private fun buildStage2Prompt(input: SimulationInput, agent: Agent): List<ChatMessage> {
        val persona = agent.persona
        val ctx = persona.consumerContext
        val placement = input.adPlacements.firstOrNull()
        val platform = placement?.platform ?: "xiaohongshu"
        val placementType = placement?.placementType ?: PlacementType.INFO_FEED

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

You stopped scrolling and are now looking at this content more closely on $platform.

Available factor tags:
Positive: interest_match, creative_appeal, brand_trust, social_proof, need_match, entertainment_value
Negative: no_interest, creative_boring, no_brand_trust, ad_fatigue, wrong_format, no_need

Output valid JSON only. Reasoning in Chinese (中文).
        """.trimIndent()

        val user = """
You can see:
- Title/headline: "${placement?.creativeDescription ?: input.product.name}"
- Brand: ${input.product.brandName}
- Content type: $placementDesc$awarenessLine

You still don't know the exact price or detailed product specs.
Would you tap/click to see the full details?

Output JSON:
{"passed": true/false, "reasoning": "中文理由(1-2句)", "factors": ["tag1", "tag2"]}
        """.trimIndent()

        return listOf(SystemMessage.from(system), UserMessage.from(user))
    }

    // ── Stage 3: Conversion ─────────────────────────────────────────────

    private fun buildStage3Prompt(input: SimulationInput, agent: Agent): List<ChatMessage> {
        val persona = agent.persona
        val ctx = persona.consumerContext
        val placement = input.adPlacements.firstOrNull()
        val platform = placement?.platform ?: "xiaohongshu"

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

You've clicked through and are now viewing the full product page on $platform.

Available factor tags:
Positive: interest_match, creative_appeal, price_acceptable, brand_trust, social_proof, urgency, need_match, entertainment_value
Negative: no_interest, creative_boring, price_too_high, no_brand_trust, no_reviews, no_need, ad_fatigue, wrong_format, competitor_preference, impulse_resist

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
{"passed": true/false, "reasoning": "中文理由(1-2句)", "factors": ["tag1", "tag2"]}
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
        val passed: Boolean,
        val reasoning: String,
        val factors: List<String> = emptyList()
    )
}
