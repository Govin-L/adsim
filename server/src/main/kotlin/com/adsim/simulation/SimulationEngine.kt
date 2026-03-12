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

    // Industry benchmark thresholds: top N% of scores pass each stage
    // These represent: attention 15%, click 12% of attenders, conversion 8% of clickers
    private val attentionPassRate = 0.15
    private val clickPassRate = 0.12
    private val conversionPassRate = 0.08

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

        // Phase 1: Collect scores from LLM for all agents
        val scoredAgents = coroutineScope {
            agents.map { agent ->
                async {
                    semaphore.withPermit {
                        val scored = scoreAgent(input, agent, chatModel)
                        onProgress(completed.incrementAndGet())
                        scored
                    }
                }
            }.awaitAll()
        }

        // Phase 2: System decides pass/fail based on score distribution + industry benchmarks
        val decided = applyThresholds(scoredAgents)

        // Phase 3: Save all agents
        agentRepository.saveAll(decided)

        val withDecisions = decided.filter { it.decisions != null }
        val noticed = withDecisions.count { it.decisions!!.attention.passed }
        val clicked = withDecisions.count { it.decisions!!.click.passed }
        val converted = withDecisions.count { it.decisions!!.conversion.passed }
        logger.info("Simulation complete: {} agents, noticed={}, clicked={}, converted={} (thresholds: attn={}%, click={}%, cvr={}%)",
            agents.size, noticed, clicked, converted,
            (attentionPassRate * 100).toInt(), (clickPassRate * 100).toInt(), (conversionPassRate * 100).toInt())
    }

    /**
     * LLM scores the agent's reaction. No pass/fail — just scores + reasoning.
     */
    private suspend fun scoreAgent(input: SimulationInput, agent: Agent, chatModel: ChatModel): Agent {
        val agentDesc = "${agent.persona.name}(${agent.persona.age}/${agent.persona.gender})"
        var lastException: Exception? = null

        repeat(config.maxRetries) { attempt ->
            try {
                val startTime = System.currentTimeMillis()
                val scores = callLlm(input, agent, chatModel)
                val elapsed = System.currentTimeMillis() - startTime
                logger.info("Agent {} [{}] scored: attn={}, click={}, conv={} ({}ms)",
                    agent.id, agentDesc, scores.attention, scores.click, scores.conversion, elapsed)
                // Store scores temporarily; pass/fail decided later by applyThresholds
                return agent.copy(decisions = Decisions(
                    attention = StageDecision(passed = false, reasoning = scores.attentionReasoning, score = scores.attention),
                    click = StageDecision(passed = false, reasoning = scores.clickReasoning, score = scores.click),
                    conversion = StageDecision(passed = false, reasoning = scores.conversionReasoning, score = scores.conversion)
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

    /**
     * Probability-based pass/fail using scores + industry benchmarks.
     *
     * Instead of hard cutoffs, each agent's score is converted to a pass probability:
     *   probability = (score / avgScore) × targetRate
     *
     * Higher scores → higher probability, but even low scores have a chance.
     * Overall pass rate converges to targetRate across all agents.
     * Each agent can walk through the full funnel, producing richer data.
     */
    private fun applyThresholds(agents: List<Agent>): List<Agent> {
        val scored = agents.filter { it.decisions != null }
        if (scored.isEmpty()) return agents

        val random = java.util.concurrent.ThreadLocalRandom.current()

        // Calculate calibration factors from average scores
        val avgAttn = scored.map { it.decisions!!.attention.score }.average().coerceAtLeast(1.0)
        val avgClick = scored.map { it.decisions!!.click.score }.average().coerceAtLeast(1.0)
        val avgConv = scored.map { it.decisions!!.conversion.score }.average().coerceAtLeast(1.0)

        logger.info("Score averages: attention={}, click={}, conversion={}", "%.1f".format(avgAttn), "%.1f".format(avgClick), "%.1f".format(avgConv))

        val decided = scored.map { agent ->
            val d = agent.decisions!!

            // Attention: probability based on score relative to average
            val attnProb = ((d.attention.score / avgAttn) * attentionPassRate).coerceIn(0.0, 1.0)
            val attnPassed = random.nextDouble() < attnProb

            // Click: only if noticed, probability based on click score
            val clickProb = ((d.click.score / avgClick) * clickPassRate).coerceIn(0.0, 1.0)
            val clickPassed = attnPassed && random.nextDouble() < clickProb

            // Conversion: only if clicked, probability based on conversion score
            val convProb = ((d.conversion.score / avgConv) * conversionPassRate).coerceIn(0.0, 1.0)
            val convPassed = clickPassed && random.nextDouble() < convProb

            agent.copy(decisions = Decisions(
                attention = StageDecision(
                    passed = attnPassed,
                    reasoning = if (attnPassed) d.attention.reasoning else "没有注意到这条广告",
                    score = d.attention.score
                ),
                click = StageDecision(
                    passed = clickPassed,
                    reasoning = if (!attnPassed) "没有注意到这条广告" else if (!clickPassed) d.click.reasoning else d.click.reasoning,
                    score = d.click.score
                ),
                conversion = StageDecision(
                    passed = convPassed,
                    reasoning = if (!clickPassed) "没有点击广告" else if (!convPassed) d.conversion.reasoning else d.conversion.reasoning,
                    score = d.conversion.score
                )
            ))
        }

        val noticed = decided.count { it.decisions!!.attention.passed }
        val clicked = decided.count { it.decisions!!.click.passed }
        val converted = decided.count { it.decisions!!.conversion.passed }
        logger.info("Probability-based decisions: {}/{} noticed (target {}%), {}/{} clicked (target {}%), {}/{} converted (target {}%)",
            noticed, scored.size, (attentionPassRate * 100).toInt(),
            clicked, noticed, (clickPassRate * 100).toInt(),
            converted, clicked, (conversionPassRate * 100).toInt())

        // Merge back unscored agents
        val scoredIds = scored.map { it.id }.toSet()
        return decided + agents.filter { it.id !in scoredIds }
    }

    private fun callLlm(input: SimulationInput, agent: Agent, chatModel: ChatModel): AgentScores {
        val systemPrompt = buildSystemPrompt(input)
        val userPrompt = buildUserPrompt(input, agent)

        val response = chatModel.chat(
            ChatRequest.builder()
                .messages(listOf(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt)))
                .build()
        )

        val text = response.aiMessage().text()
        logger.debug("LLM response for agent {}: {}", agent.id, text.take(100))
        return parseScores(text)
    }

    private fun buildSystemPrompt(input: SimulationInput): String {
        val platform = input.adPlacements.firstOrNull()?.platform ?: "xiaohongshu"
        return """
You are simulating a real $platform user seeing an advertisement in their feed.
Stay completely in character based on the persona. Your scores must reflect THIS specific person's genuine reaction.

For each stage, rate your reaction on a scale of 0-100:
- 0 = absolutely no interest / would never do this
- 25 = very unlikely, barely relevant to me
- 50 = neutral, could go either way
- 75 = somewhat interested / likely
- 100 = extremely interested / would definitely do this

Be nuanced and realistic. Consider the persona's specific interests, income, habits, and current browsing context.
A score of 50+ does NOT mean "yes" — the system will decide pass/fail based on industry benchmarks.
Your job is to accurately rate HOW appealing/likely each stage is for this specific person.

Output valid JSON only. Reasoning in Chinese (中文).
        """.trimIndent()
    }

    private fun buildUserPrompt(input: SimulationInput, agent: Agent): String {
        val persona = agent.persona
        val placement = input.adPlacements.firstOrNull()
        val platform = placement?.platform ?: "xiaohongshu"
        return """
You are: ${persona.name}, ${persona.age} years old, ${persona.gender}
Income: ${persona.income}, City tier: ${persona.cityTier}
Interests: ${persona.interests.joinToString(", ")}
Platform usage: ${persona.platformBehavior.dailyUsageMinutes} min/day, prefers: ${persona.platformBehavior.contentPreferences.joinToString(", ")}
Purchase frequency on platform: ${persona.platformBehavior.purchaseFrequency}
Price sensitivity: ${persona.consumptionHabits.priceSensitivity}
Decision speed: ${persona.consumptionHabits.decisionSpeed}
Brand loyalty: ${persona.consumptionHabits.brandLoyalty}

You are browsing $platform. An ad appears via ${placement?.placementType ?: "INFO_FEED"}:
- Brand: ${input.product.brandName}
- Product: ${input.product.name} (${input.product.category}, ¥${input.product.price})
- Key selling points: ${input.product.sellingPoints}
- Product stage: ${input.product.productStage} ${if (input.product.productStage.name == "NEW_LAUNCH") "(new, no reviews yet)" else if (input.product.productStage.name == "BESTSELLER") "(popular, many positive reviews)" else "(some reviews available)"}
${if (input.product.description.isNotBlank()) "- Additional info: ${input.product.description}" else ""}
- Ad creative: ${placement?.creativeDescription ?: ""}
- Ad format: ${placement?.format ?: "VIDEO"}

Rate each stage 0-100 from YOUR perspective:

Output JSON:
{
  "attention_score": 0-100,
  "attention_reasoning": "中文：为什么这个分数（1-2句）",
  "click_score": 0-100,
  "click_reasoning": "中文：为什么这个分数（1-2句）",
  "conversion_score": 0-100,
  "conversion_reasoning": "中文：为什么这个分数（1-2句）"
}
        """.trimIndent()
    }

    private fun parseScores(content: String): AgentScores {
        try {
            val dto = objectMapper.readValue<ScoresDto>(content)
            return AgentScores(
                attention = dto.attention_score.coerceIn(0, 100),
                attentionReasoning = dto.attention_reasoning,
                click = dto.click_score.coerceIn(0, 100),
                clickReasoning = dto.click_reasoning,
                conversion = dto.conversion_score.coerceIn(0, 100),
                conversionReasoning = dto.conversion_reasoning
            )
        } catch (e: Exception) {
            logger.error("Failed to parse scores: {}, content: {}", e.message, content.take(200))
            throw RuntimeException("Failed to parse LLM response: ${e.message}", e)
        }
    }

    private data class AgentScores(
        val attention: Int,
        val attentionReasoning: String,
        val click: Int,
        val clickReasoning: String,
        val conversion: Int,
        val conversionReasoning: String
    )

    private data class ScoresDto(
        val attention_score: Int,
        val attention_reasoning: String,
        val click_score: Int,
        val click_reasoning: String,
        val conversion_score: Int,
        val conversion_reasoning: String
    )
}
