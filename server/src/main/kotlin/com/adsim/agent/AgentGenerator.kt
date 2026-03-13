package com.adsim.agent

import com.adsim.config.SimulationConfig
import com.adsim.model.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class AgentGenerator(
    private val config: SimulationConfig
) {
    private val logger = LoggerFactory.getLogger(AgentGenerator::class.java)
    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    suspend fun generate(
        input: SimulationInput,
        count: Int,
        chatModel: ChatModel,
        concurrency: Int = 2,
        onProgress: (generated: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Agent> {
        val platform = input.adPlacements.firstOrNull()?.platform ?: "xiaohongshu"
        logger.info("Generating {} agents for platform: {}, concurrency: {}", count, platform, concurrency)

        val batchSize = config.agentBatchSize
        val batches = (count + batchSize - 1) / batchSize
        val generated = AtomicInteger(0)
        val semaphore = Semaphore(concurrency)

        val results = coroutineScope {
            (0 until batches).map { i ->
                val currentBatchSize = if (i < batches - 1) batchSize else count - batchSize * i
                val batchIndex = i + 1
                async {
                    semaphore.withPermit {
                        logger.info("Generating agent batch {}/{}, size: {}", batchIndex, batches, currentBatchSize)
                        val batchAgents = generateBatch(input, currentBatchSize, batchIndex, batches, chatModel)
                        val total = generated.addAndGet(batchAgents.size)
                        onProgress(total, count)
                        batchAgents
                    }
                }
            }.awaitAll()
        }

        val agents = results.flatten()
        logger.info("Agent generation complete: {} agents", agents.size)
        return agents
    }

    private fun generateBatch(input: SimulationInput, batchSize: Int, batchIndex: Int, totalBatches: Int, chatModel: ChatModel): List<Agent> {
        val platform = input.adPlacements.firstOrNull()?.platform ?: "xiaohongshu"
        val systemPrompt = buildSystemPrompt(platform)
        val userPrompt = buildUserPrompt(input, platform, batchSize, batchIndex, totalBatches)

        val response = chatModel.chat(
            ChatRequest.builder()
                .messages(listOf(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt)))
                .build()
        )

        val content = response.aiMessage().text()
        return parseAgents(content)
    }

    private fun buildSystemPrompt(platform: String): String {
        return """
You are a user persona generator for marketing simulation.
You generate realistic, diverse user personas for the $platform platform.

Each persona must be unique with distinct demographics, interests, and behaviors.
Include users who would AND would NOT be interested in the advertised product.
Be realistic: most platform users are NOT the target audience for any specific ad.

Each persona must include a consumerContext describing their current relationship with the product category and brand.

Output valid JSON only.
        """.trimIndent()
    }

    private fun buildBrandAwarenessDistribution(awareness: BrandAwareness): String {
        return when (awareness) {
            BrandAwareness.NEW -> "70% never_heard, 20% heard_not_tried, 8% tried_once, 2% regular_user"
            BrandAwareness.EMERGING -> "40% never_heard, 35% heard_not_tried, 18% tried_once, 7% regular_user"
            BrandAwareness.WELL_KNOWN -> "15% never_heard, 35% heard_not_tried, 30% tried_once, 20% regular_user"
            BrandAwareness.TOP -> "5% never_heard, 20% heard_not_tried, 30% tried_once, 45% regular_user"
        }
    }

    private fun buildUserPrompt(input: SimulationInput, platform: String, batchSize: Int, batchIndex: Int, totalBatches: Int): String {
        val ageRange = input.targetAudience.ageRange
        val competitorSection = if (input.competitors.isNotEmpty()) {
            val competitorList = input.competitors.joinToString("\n") { "  - ${it.brandName} (¥${it.price}${if (it.positioning.isNotBlank()) ", ${it.positioning}" else ""})" }
            """
Competitors in market:
$competitorList
Assign each agent a currentBrand from the competitor list or null (not using any similar product).
If competitors are provided, distribute: each competitor ~15-25% of agents, remainder have no current brand.
            """.trimIndent()
        } else {
            """
No specific competitors provided.
About 50% of agents should have currentBrand=null (not using similar products), 50% should use "some similar product" (generate a realistic brand name).
            """.trimIndent()
        }

        val awarenessDistribution = buildBrandAwarenessDistribution(input.brandAwareness)

        val genderDiversity = if (input.targetAudience.gender == "female") {
            "Even though the target audience is female, include at least 20% male agents for realistic simulation."
        } else if (input.targetAudience.gender == "male") {
            "Even though the target audience is male, include at least 20% female agents for realistic simulation."
        } else {
            ""
        }

        return """
Generate $batchSize unique $platform user personas.

This is batch $batchIndex of $totalBatches. Ensure diversity across batches:
- Batch focus: vary age groups, income levels, city tiers, and interests
- Include both target audience members AND non-target users
- About 40-60% should roughly match the target audience, the rest should not
- Each age group (18-24, 25-30, 31-40, 40+) must have at least 15% representation
${if (genderDiversity.isNotBlank()) "- $genderDiversity" else ""}

Platform: $platform
Brand: ${input.product.brandName}
Product: ${input.product.name} (${input.product.category}, ¥${input.product.price})
Product stage: ${input.product.productStage}
Key selling points: ${input.product.sellingPoints}
Brand awareness level: ${input.brandAwareness} — distribute brandAwareness in consumerContext as: $awarenessDistribution
Campaign goal: ${input.campaignGoal}
Target audience: ${input.targetAudience.gender}, age ${ageRange[0]}-${ageRange[1]}${if (input.targetAudience.region.isNotBlank()) ", region: ${input.targetAudience.region}" else ""}${if (input.targetAudience.interests.isNotEmpty()) ", interests: ${input.targetAudience.interests.joinToString(", ")}" else ""}

$competitorSection

For each agent, also generate a consumerContext:
- currentBrand: the brand they currently use in this product category (or null)
- currentProductPrice: price of their current product (or null)
- satisfaction: "satisfied", "neutral", or "looking_for_alternatives"
- brandAwareness: their awareness of "${input.product.brandName}" — use distribution above
- recentAdExposure: 0-5, how many similar product ads they saw this week (random)

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
      },
      "consumerContext": {
        "currentBrand": "MAC",
        "currentProductPrice": 170,
        "satisfaction": "satisfied",
        "brandAwareness": "heard_not_tried",
        "recentAdExposure": 2
      }
    }
  ]
}

Valid enum values:
- income: LOW, MEDIUM, HIGH
- purchaseFrequency: NEVER, RARELY, SOMETIMES, OFTEN
- priceSensitivity/brandLoyalty: LOW, MEDIUM, HIGH
- decisionSpeed: IMPULSIVE, MODERATE, DELIBERATE
- consumerContext.satisfaction: satisfied, neutral, looking_for_alternatives
- consumerContext.brandAwareness: never_heard, heard_not_tried, tried_once, regular_user
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
                        ),
                        consumerContext = dto.consumerContext?.let {
                            ConsumerContext(
                                currentBrand = it.currentBrand,
                                currentProductPrice = it.currentProductPrice,
                                satisfaction = it.satisfaction,
                                brandAwareness = it.brandAwareness ?: "never_heard",
                                recentAdExposure = it.recentAdExposure ?: 0
                            )
                        } ?: ConsumerContext()
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
        val consumptionHabits: ConsumptionHabitsDto,
        val consumerContext: ConsumerContextDto? = null
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
    private data class ConsumerContextDto(
        val currentBrand: String? = null,
        val currentProductPrice: Double? = null,
        val satisfaction: String? = null,
        val brandAwareness: String? = null,
        val recentAdExposure: Int? = null
    )
}
