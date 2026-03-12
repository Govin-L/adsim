package com.adsim.agent

import com.adsim.model.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PlanParser {
    private val logger = LoggerFactory.getLogger(PlanParser::class.java)
    private val objectMapper = jacksonObjectMapper()

    fun parse(rawInput: String, chatModel: ChatModel): SimulationInput {
        logger.info("Parsing marketing plan, input length: {}", rawInput.length)

        val response = chatModel.chat(
            ChatRequest.builder()
                .messages(listOf(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(buildUserPrompt(rawInput))))
                .build()
        )

        val content = response.aiMessage().text()
        return parseResponse(content)
    }

    fun findMissingFields(input: SimulationInput): List<String> {
        val missing = mutableListOf<String>()
        if (input.product.brandName.isBlank()) missing.add("brandName")
        if (input.product.name.isBlank()) missing.add("productName")
        if (input.product.price <= 0) missing.add("price")
        if (input.product.category.isBlank()) missing.add("category")
        if (input.product.sellingPoints.isBlank()) missing.add("sellingPoints")
        if (input.adPlacements.isEmpty()) missing.add("adPlacements")
        if (input.totalBudget <= 0) missing.add("totalBudget")
        if (input.targetAudience.gender == "all" &&
            input.targetAudience.ageRange == listOf(18, 65) &&
            input.targetAudience.interests.isEmpty()) missing.add("targetAudience")
        return missing
    }

    private fun buildUserPrompt(rawInput: String): String {
        return """
Parse the following marketing plan into structured JSON.
Extract as much information as possible. For fields you cannot determine, use reasonable defaults.

Marketing plan:
---
$rawInput
---

Output JSON:
{
  "product": {
    "brandName": "brand name",
    "name": "product name",
    "price": 299,
    "category": "product category",
    "sellingPoints": "key selling points",
    "productStage": "NEW_LAUNCH | ESTABLISHED | BESTSELLER",
    "description": "optional additional description"
  },
  "adPlacements": [
    {
      "platform": "xiaohongshu | douyin | wechat | meta | google | tiktok",
      "placementType": "INFO_FEED | SEARCH | KOL_SEEDING | SHORT_VIDEO | SPLASH_SCREEN | LIVESTREAM",
      "objectives": ["BRAND_AWARENESS", "SEEDING", "TRAFFIC", "CONVERSION", "LEAD_GENERATION"],
      "format": "VIDEO | IMAGE | IMAGE_TEXT",
      "budget": 100000,
      "creativeDescription": "description of the creative for this placement"
    }
  ],
  "totalBudget": 500000,
  "targetAudience": {
    "ageRange": [25, 35],
    "gender": "female | male | all",
    "region": "first and second tier cities in China",
    "interests": ["skincare", "beauty"]
  }
}

Important:
- If multiple placements are described, create separate entries for each
- Do NOT create duplicate entries with the same platform + placementType combination. Merge them instead.
- Each placement can have multiple objectives (e.g., ["SEEDING", "BRAND_AWARENESS"]).
- Budget for all placements should sum to totalBudget
- If budget per placement is not specified, distribute evenly
- If platform is not specified, default to "xiaohongshu"
- If productStage is not clear, default to "NEW_LAUNCH"
        """.trimIndent()
    }

    private fun parseResponse(content: String): SimulationInput {
        val dto = objectMapper.readValue<ParsedPlanDto>(content)
        return SimulationInput(
            product = Product(
                brandName = dto.product.brandName,
                name = dto.product.name,
                price = dto.product.price,
                category = dto.product.category,
                sellingPoints = dto.product.sellingPoints,
                productStage = runCatching { ProductStage.valueOf(dto.product.productStage) }.getOrDefault(ProductStage.NEW_LAUNCH),
                description = dto.product.description ?: ""
            ),
            adPlacements = dto.adPlacements.map { p ->
                AdPlacement(
                    platform = p.platform,
                    placementType = runCatching { PlacementType.valueOf(p.placementType) }.getOrDefault(PlacementType.INFO_FEED),
                    objectives = p.objectives.mapNotNull { runCatching { CampaignObjective.valueOf(it) }.getOrNull() }.ifEmpty { listOf(CampaignObjective.CONVERSION) },
                    format = runCatching { CreativeFormat.valueOf(p.format) }.getOrDefault(CreativeFormat.VIDEO),
                    budget = p.budget,
                    creativeDescription = p.creativeDescription
                )
            },
            totalBudget = dto.totalBudget,
            targetAudience = TargetAudience(
                ageRange = dto.targetAudience.ageRange ?: listOf(18, 65),
                gender = dto.targetAudience.gender ?: "all",
                region = dto.targetAudience.region ?: "",
                interests = dto.targetAudience.interests ?: emptyList()
            )
        )
    }

    // DTOs for JSON parsing
    private data class ParsedPlanDto(
        val product: ParsedProductDto,
        val adPlacements: List<ParsedPlacementDto>,
        val totalBudget: Long,
        val targetAudience: ParsedAudienceDto
    )
    private data class ParsedProductDto(
        val brandName: String, val name: String, val price: Double,
        val category: String, val sellingPoints: String,
        val productStage: String, val description: String? = null
    )
    private data class ParsedPlacementDto(
        val platform: String, val placementType: String, val objectives: List<String>,
        val format: String, val budget: Long, val creativeDescription: String
    )
    private data class ParsedAudienceDto(
        val ageRange: List<Int>?, val gender: String?,
        val region: String?, val interests: List<String>?
    )

    companion object {
        private const val SYSTEM_PROMPT = """You are a marketing plan parser.
Extract structured information from natural language marketing plans.
You understand both Chinese and English marketing terminology.
Output valid JSON only. Be precise with numbers and categorizations."""
    }
}
