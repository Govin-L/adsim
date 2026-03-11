package com.adsim.agent

import com.adsim.config.SimulationConfig
import com.adsim.model.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AgentGenerator(
    private val chatModel: ChatLanguageModel,
    private val config: SimulationConfig
) {
    private val logger = LoggerFactory.getLogger(AgentGenerator::class.java)
    private val objectMapper = jacksonObjectMapper()

    suspend fun generate(input: SimulationInput, count: Int): List<Agent> {
        logger.info("Generating {} agents for platform: {}", count, input.platform)

        val agents = mutableListOf<Agent>()
        val batchSize = config.agentBatchSize
        val batches = (count + batchSize - 1) / batchSize

        for (i in 0 until batches) {
            val remaining = count - agents.size
            val currentBatchSize = minOf(batchSize, remaining)
            val batchIndex = i + 1

            logger.info("Generating agent batch {}/{}, size: {}", batchIndex, batches, currentBatchSize)

            val batchAgents = generateBatch(input, currentBatchSize, batchIndex, batches)
            agents.addAll(batchAgents)
        }

        logger.info("Agent generation complete: {} agents", agents.size)
        return agents
    }

    private fun generateBatch(input: SimulationInput, batchSize: Int, batchIndex: Int, totalBatches: Int): List<Agent> {
        val systemPrompt = buildSystemPrompt(input)
        val userPrompt = buildUserPrompt(input, batchSize, batchIndex, totalBatches)

        val response = chatModel.chat(
            ChatRequest.builder()
                .messages(listOf(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt)))
                .build()
        )

        val content = response.aiMessage().text()
        return parseAgents(content)
    }

    private fun buildSystemPrompt(input: SimulationInput): String {
        return """
You are a user persona generator for marketing simulation.
You generate realistic, diverse user personas for the ${input.platform} platform.

Each persona must be unique with distinct demographics, interests, and behaviors.
Include users who would AND would NOT be interested in the advertised product.
Be realistic: most platform users are NOT the target audience for any specific ad.

Output valid JSON only.
        """.trimIndent()
    }

    private fun buildUserPrompt(input: SimulationInput, batchSize: Int, batchIndex: Int, totalBatches: Int): String {
        val ageRange = input.targetAudience.ageRange
        return """
Generate $batchSize unique ${input.platform} user personas.

This is batch $batchIndex of $totalBatches. Ensure diversity across batches:
- Batch focus: vary age groups, income levels, city tiers, and interests
- Include both target audience members AND non-target users
- About 40-60% should roughly match the target audience, the rest should not

Platform: ${input.platform}
Product being advertised: ${input.product.name} (${input.product.category}, ¥${input.product.price})
Target audience: ${input.targetAudience.gender}, age ${ageRange[0]}-${ageRange[1]}, interests: ${input.targetAudience.interests.joinToString(", ")}

Output JSON format:
{
  "agents": [
    {
      "name": "小明",
      "age": 28,
      "gender": "female",
      "income": "MEDIUM",
      "cityTier": 2,
      "interests": ["skincare", "fitness"],
      "platformBehavior": {
        "dailyUsageMinutes": 45,
        "contentPreferences": ["video", "review"],
        "purchaseFrequency": "SOMETIMES"
      },
      "consumptionHabits": {
        "priceSensitivity": "MEDIUM",
        "decisionSpeed": "MODERATE",
        "brandLoyalty": "LOW"
      }
    }
  ]
}

Valid enum values:
- income: LOW, MEDIUM, HIGH
- purchaseFrequency: NEVER, RARELY, SOMETIMES, OFTEN
- priceSensitivity/brandLoyalty: LOW, MEDIUM, HIGH
- decisionSpeed: IMPULSIVE, MODERATE, DELIBERATE
        """.trimIndent()
    }

    private fun parseAgents(content: String): List<Agent> {
        return try {
            val wrapper = objectMapper.readValue<AgentBatchWrapper>(content)
            wrapper.agents.map { dto ->
                Agent(
                    simulationId = "",
                    persona = Persona(
                        name = dto.name,
                        age = dto.age,
                        gender = dto.gender,
                        income = IncomeLevel.valueOf(dto.income),
                        cityTier = dto.cityTier,
                        interests = dto.interests,
                        platformBehavior = PlatformBehavior(
                            dailyUsageMinutes = dto.platformBehavior.dailyUsageMinutes,
                            contentPreferences = dto.platformBehavior.contentPreferences,
                            purchaseFrequency = PurchaseFrequency.valueOf(dto.platformBehavior.purchaseFrequency)
                        ),
                        consumptionHabits = ConsumptionHabits(
                            priceSensitivity = SensitivityLevel.valueOf(dto.consumptionHabits.priceSensitivity),
                            decisionSpeed = DecisionSpeed.valueOf(dto.consumptionHabits.decisionSpeed),
                            brandLoyalty = SensitivityLevel.valueOf(dto.consumptionHabits.brandLoyalty)
                        )
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to parse agent batch: {}", e.message)
            emptyList()
        }
    }

    // DTO for JSON parsing
    private data class AgentBatchWrapper(val agents: List<AgentDto>)
    private data class AgentDto(
        val name: String,
        val age: Int,
        val gender: String,
        val income: String,
        val cityTier: Int,
        val interests: List<String>,
        val platformBehavior: PlatformBehaviorDto,
        val consumptionHabits: ConsumptionHabitsDto
    )
    private data class PlatformBehaviorDto(
        val dailyUsageMinutes: Int,
        val contentPreferences: List<String>,
        val purchaseFrequency: String
    )
    private data class ConsumptionHabitsDto(
        val priceSensitivity: String,
        val decisionSpeed: String,
        val brandLoyalty: String
    )
}
