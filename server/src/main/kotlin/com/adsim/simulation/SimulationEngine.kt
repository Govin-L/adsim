package com.adsim.simulation

import com.adsim.config.SimulationConfig
import com.adsim.model.*
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

    suspend fun run(
        input: SimulationInput,
        agents: List<Agent>,
        chatModel: ChatModel,
        onProgress: (completed: Int) -> Unit
    ) {
        logger.info("Running simulation for {} agents", agents.size)

        val semaphore = Semaphore(config.maxConcurrency)
        val completed = AtomicInteger(0)

        coroutineScope {
            agents.map { agent ->
                async {
                    semaphore.withPermit {
                        simulateAgent(input, agent, chatModel)
                        val count = completed.incrementAndGet()
                        if (count % 10 == 0 || count == agents.size) {
                            onProgress(count)
                        }
                    }
                }
            }.awaitAll()
        }

        logger.info("Simulation complete: {} agents processed", completed.get())
    }

    private suspend fun simulateAgent(input: SimulationInput, agent: Agent, chatModel: ChatModel) {
        var lastException: Exception? = null

        repeat(config.maxRetries) { attempt ->
            try {
                val decisions = callLlm(input, agent, chatModel)
                if (decisions != null) {
                    agentRepository.save(agent.copy(decisions = decisions))
                    return
                }
            } catch (e: Exception) {
                lastException = e
                logger.warn("Simulation failed for agent {}, attempt {}: {}", agent.id, attempt + 1, e.message)
                delay(config.batchDelayMs * (attempt + 1))
            }
        }

        logger.error("Simulation failed for agent {} after {} retries", agent.id, config.maxRetries, lastException)
    }

    private fun callLlm(input: SimulationInput, agent: Agent, chatModel: ChatModel): Decisions? {
        val systemPrompt = buildSystemPrompt(input)
        val userPrompt = buildUserPrompt(input, agent)

        val response = chatModel.chat(
            ChatRequest.builder()
                .messages(listOf(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt)))
                .build()
        )

        return parseDecisions(response.aiMessage().text())
    }

    private fun buildSystemPrompt(input: SimulationInput): String {
        val platform = input.adPlacements.firstOrNull()?.platform ?: "xiaohongshu"
        return """
You are simulating a real $platform user seeing an advertisement in their feed.
You must stay completely in character based on the persona provided.
Your decisions must be grounded in the persona's specific attributes — not generic responses.

IMPORTANT REALITY CHECK:
- In reality, the vast majority of users scroll past ads without noticing them.
- The average user sees 100+ content items per session. This ad is just one of them.
- Most users do NOT click ads. Most clickers do NOT purchase.
- Be realistic and honest based on the persona. Do NOT be overly positive.

Output valid JSON only.
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

You are on $platform. An ad appears via ${placement?.placementType ?: "INFO_FEED"}:
- Brand: ${input.product.brandName}
- Product: ${input.product.name} (${input.product.category}, ¥${input.product.price})
- Key selling points: ${input.product.sellingPoints}
- Product stage: ${input.product.productStage} ${if (input.product.productStage.name == "NEW_LAUNCH") "(new, no reviews yet)" else if (input.product.productStage.name == "BESTSELLER") "(popular, many positive reviews)" else "(some reviews available)"}
${if (input.product.description.isNotBlank()) "- Additional info: ${input.product.description}" else ""}
- Ad creative: ${placement?.creativeDescription ?: ""}
- Ad format: ${placement?.format ?: "VIDEO"}
- Campaign objectives: ${placement?.objectives?.joinToString(", ") ?: "CONVERSION"}

Make your decision at each stage. Each stage ONLY happens if the previous stage passed.
Think from YOUR perspective as this specific person.

Output JSON:
{
  "attention": {
    "passed": true/false,
    "reasoning": "Why you did/didn't notice this ad (1-2 sentences from your perspective)"
  },
  "click": {
    "passed": true/false,
    "reasoning": "Why you did/didn't click (1-2 sentences, only if you noticed it)"
  },
  "conversion": {
    "passed": true/false,
    "reasoning": "Why you did/didn't purchase (1-2 sentences, only if you clicked)"
  }
}

If you didn't notice the ad, set click and conversion both to false with reasoning "Did not notice the ad".
If you noticed but didn't click, set conversion to false with reasoning "Did not click the ad".
        """.trimIndent()
    }

    private fun parseDecisions(content: String): Decisions? {
        return try {
            val dto = objectMapper.readValue<DecisionsDto>(content)
            Decisions(
                attention = StageDecision(dto.attention.passed, dto.attention.reasoning),
                click = StageDecision(dto.click.passed, dto.click.reasoning),
                conversion = StageDecision(dto.conversion.passed, dto.conversion.reasoning)
            )
        } catch (e: Exception) {
            logger.error("Failed to parse decisions: {}", e.message)
            null
        }
    }

    private data class DecisionsDto(
        val attention: StageDecisionDto,
        val click: StageDecisionDto,
        val conversion: StageDecisionDto
    )

    private data class StageDecisionDto(
        val passed: Boolean,
        val reasoning: String
    )
}
