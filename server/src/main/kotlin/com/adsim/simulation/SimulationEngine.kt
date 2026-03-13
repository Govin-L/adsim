package com.adsim.simulation

import com.adsim.config.SimulationConfig
import com.adsim.model.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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

        // Call LLM for each agent to get binary decisions
        val decidedAgents = coroutineScope {
            agents.map { agent ->
                async {
                    semaphore.withPermit {
                        val decided = decideAgent(input, agent, chatModel)
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
     * LLM makes binary pass/fail decisions with structured reasoning and factor tags.
     */
    private suspend fun decideAgent(input: SimulationInput, agent: Agent, chatModel: ChatModel): Agent {
        val agentDesc = "${agent.persona.name}(${agent.persona.age}/${agent.persona.gender})"
        var lastException: Exception? = null

        repeat(config.maxRetries) { attempt ->
            try {
                val startTime = System.currentTimeMillis()
                val response = callLlm(input, agent, chatModel)
                val elapsed = System.currentTimeMillis() - startTime

                // Enforce sequential constraint: if attention=false, force click/conversion to false
                val attention = response.attention
                val click = if (!attention.passed) {
                    DecisionDto(passed = false, reasoning = "没有注意到这条广告", factors = emptyList())
                } else {
                    response.click
                }
                val conversion = if (!click.passed) {
                    DecisionDto(
                        passed = false,
                        reasoning = if (!attention.passed) "没有注意到这条广告" else "没有点击广告",
                        factors = emptyList()
                    )
                } else {
                    response.conversion
                }

                logger.info("Agent {} [{}] decided: attn={}, click={}, conv={} ({}ms)",
                    agent.id, agentDesc, attention.passed, click.passed, conversion.passed, elapsed)

                return agent.copy(decisions = Decisions(
                    attention = StageDecision(
                        passed = attention.passed,
                        reasoning = attention.reasoning,
                        factors = attention.factors
                    ),
                    click = StageDecision(
                        passed = click.passed,
                        reasoning = click.reasoning,
                        factors = click.factors
                    ),
                    conversion = StageDecision(
                        passed = conversion.passed,
                        reasoning = conversion.reasoning,
                        factors = conversion.factors
                    )
                ))
            } catch (e: Exception) {
                lastException = e
                logger.warn("Agent {} [{}] attempt {}/{} failed: {}", agent.id, agentDesc, attempt + 1, config.maxRetries, e.message?.take(150))
                delay(2000L * (attempt + 1))
            }
        }

        logger.error("Agent {} [{}] GAVE UP after {} retries, last error: {}", agent.id, agentDesc, config.maxRetries, lastException?.message?.take(200))
        return agent // no decisions
    }

    private fun callLlm(input: SimulationInput, agent: Agent, chatModel: ChatModel): ResponseDto {
        val systemPrompt = buildSystemPrompt(input)
        val userPrompt = buildUserPrompt(input, agent)

        val response = chatModel.chat(
            ChatRequest.builder()
                .messages(listOf(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt)))
                .build()
        )

        val text = response.aiMessage().text()
        logger.debug("LLM response for agent {}: {}", agent.id, text.take(100))
        return parseResponse(text)
    }

    private fun buildSystemPrompt(input: SimulationInput): String {
        val platform = input.adPlacements.firstOrNull()?.platform ?: "xiaohongshu"
        return """
You are simulating a real $platform user seeing an advertisement in their feed.
Stay completely in character based on the persona.

You are browsing $platform, having already scrolled through 30+ pieces of content (food posts, fashion tips, travel photos, etc).
You are aware that some content is sponsored/promoted — you have a natural tendency to scroll past ads quickly.
Your attention is scarce — only highly relevant or eye-catching content makes you stop.

For each stage of the advertising funnel, make a YES or NO decision:
1. attention: Would you notice this ad while scrolling?
2. click: If you noticed it, would you tap to learn more?
3. conversion: If you clicked, would you actually buy/download/sign up?

Each decision must include:
- passed: true or false
- reasoning: 1-2 sentences in Chinese explaining WHY
- factors: 1-3 tags describing key decision drivers

Decisions are sequential: if attention=false, click and conversion must also be false.

Available factor tags:
Positive: interest_match, creative_appeal, price_acceptable, brand_trust, social_proof, urgency, need_match, entertainment_value
Negative: no_interest, creative_boring, price_too_high, no_brand_trust, no_reviews, no_need, ad_fatigue, wrong_format, competitor_preference, impulse_resist

Output valid JSON only. Reasoning in Chinese (中文).
        """.trimIndent()
    }

    private fun buildConsumerContextSection(persona: Persona, input: SimulationInput): String {
        val ctx = persona.consumerContext
        val category = input.product.category
        val brand = input.product.brandName

        val currentUsage = if (ctx.currentBrand != null) {
            "你目前在用 ${ctx.currentBrand}" +
                (if (ctx.currentProductPrice != null) "（¥${ctx.currentProductPrice}）" else "") +
                (when (ctx.satisfaction) {
                    "satisfied" -> "，使用体验满意"
                    "neutral" -> "，使用体验一般"
                    "looking_for_alternatives" -> "，正在寻找替代品"
                    else -> ""
                })
        } else {
            "你目前没有使用同类产品"
        }

        val awarenessDesc = when (ctx.brandAwareness) {
            "never_heard" -> "从未听说过这个品牌"
            "heard_not_tried" -> "听说过但从未购买或使用"
            "tried_once" -> "曾经买过/试过，但不常用"
            "regular_user" -> "经常购买，是这个品牌的老用户"
            else -> "从未听说过这个品牌"
        }

        val competitorsText = if (input.competitors.isNotEmpty()) {
            "市场上的主要竞品：" + input.competitors.joinToString("、") {
                "${it.brandName}（¥${it.price}${if (it.positioning.isNotBlank()) "，${it.positioning}" else ""}）"
            }
        } else {
            ""
        }

        return buildString {
            appendLine("Your current $category usage: $currentUsage")
            appendLine("Your awareness of \"$brand\": $awarenessDesc")
            appendLine("You've seen ${ctx.recentAdExposure} similar product ads/promotions this week.")
            if (competitorsText.isNotBlank()) appendLine(competitorsText)
        }.trimEnd()
    }

    private fun buildUserPrompt(input: SimulationInput, agent: Agent): String {
        val persona = agent.persona
        val placement = input.adPlacements.firstOrNull()
        val platform = placement?.platform ?: "xiaohongshu"
        val consumerSection = buildConsumerContextSection(persona, input)
        return """
You are: ${persona.name}, ${persona.age} years old, ${persona.gender}
Income: ${persona.income}, City tier: ${persona.cityTier}
Interests: ${persona.interests.joinToString(", ")}
Platform usage: ${persona.platformBehavior.dailyUsageMinutes} min/day, prefers: ${persona.platformBehavior.contentPreferences.joinToString(", ")}
Purchase frequency on platform: ${persona.platformBehavior.purchaseFrequency}
Price sensitivity: ${persona.consumptionHabits.priceSensitivity}
Decision speed: ${persona.consumptionHabits.decisionSpeed}
Brand loyalty: ${persona.consumptionHabits.brandLoyalty}

$consumerSection

You are browsing $platform. An ad appears via ${placement?.placementType ?: "INFO_FEED"}:
- Brand: ${input.product.brandName}
- Product: ${input.product.name} (${input.product.category}, ¥${input.product.price})
- Key selling points: ${input.product.sellingPoints}
- Product stage: ${input.product.productStage} ${if (input.product.productStage.name == "NEW_LAUNCH") "(new, no reviews yet)" else if (input.product.productStage.name == "BESTSELLER") "(popular, many positive reviews)" else "(some reviews available)"}
${if (input.product.description.isNotBlank()) "- Additional info: ${input.product.description}" else ""}
- Ad creative: ${placement?.creativeDescription ?: ""}
- Ad format: ${placement?.format ?: "VIDEO"}

Make your YES/NO decision for each stage from YOUR perspective:

Output JSON:
{
  "attention": {"passed": true/false, "reasoning": "中文理由", "factors": ["tag1", "tag2"]},
  "click": {"passed": true/false, "reasoning": "中文理由", "factors": ["tag1"]},
  "conversion": {"passed": true/false, "reasoning": "中文理由", "factors": ["tag1"]}
}
        """.trimIndent()
    }

    private fun parseResponse(content: String): ResponseDto {
        try {
            return objectMapper.readValue<ResponseDto>(content)
        } catch (e: Exception) {
            logger.error("Failed to parse response: {}, content: {}", e.message, content.take(200))
            throw RuntimeException("Failed to parse LLM response: ${e.message}", e)
        }
    }

    private data class DecisionDto(
        val passed: Boolean,
        val reasoning: String,
        val factors: List<String> = emptyList()
    )

    private data class ResponseDto(
        val attention: DecisionDto,
        val click: DecisionDto,
        val conversion: DecisionDto
    )
}
