package com.adsim.agent

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
class PlanParser {
    private val logger = LoggerFactory.getLogger(PlanParser::class.java)
    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    data class PlanCompileResult(
        val mergedPlan: SimulationInput,
        val changedFields: List<String>,
        val warnings: List<String>
    )

    fun compile(rawInput: String, currentPlan: SimulationInput?, chatModel: ChatModel): PlanCompileResult {
        logger.info("Compiling marketing plan, input length: {}, hasCurrentPlan: {}", rawInput.length, currentPlan != null)

        return if (currentPlan == null) {
            PlanCompileResult(
                mergedPlan = parseFullPlan(rawInput, chatModel),
                changedFields = emptyList(),
                warnings = emptyList()
            )
        } else {
            val patchResult = parsePatch(rawInput, currentPlan, chatModel)
            val normalizationWarnings = mutableListOf<String>()
            val mergedPlan = mergePatch(currentPlan, patchResult.patch, normalizationWarnings)
            val changedFields = patchResult.changedFields.ifEmpty { deriveChangedFields(currentPlan, mergedPlan) }
            PlanCompileResult(
                mergedPlan = mergedPlan,
                changedFields = changedFields,
                warnings = (patchResult.warnings + normalizationWarnings).distinct()
            )
        }
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

    private fun parseFullPlan(rawInput: String, chatModel: ChatModel): SimulationInput {
        val response = chatModel.chat(
            ChatRequest.builder()
                .messages(listOf(SystemMessage.from(FULL_PLAN_SYSTEM_PROMPT), UserMessage.from(buildFullPlanPrompt(rawInput))))
                .build()
        )

        val content = response.aiMessage().text()
        return parseFullPlanResponse(content)
    }

    private fun parsePatch(rawInput: String, currentPlan: SimulationInput, chatModel: ChatModel): PlanPatchDto {
        val response = chatModel.chat(
            ChatRequest.builder()
                .messages(listOf(SystemMessage.from(PATCH_SYSTEM_PROMPT), UserMessage.from(buildPatchPrompt(rawInput, currentPlan))))
                .build()
        )

        val content = response.aiMessage().text()
        return parsePatchResponse(content)
    }

    private fun buildFullPlanPrompt(rawInput: String): String {
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
      "placementType": "INFO_FEED | SEARCH | KOL_SEEDING | SHORT_VIDEO | SPLASH_SCREEN | LIVESTREAM | HASHTAG_CHALLENGE | SHOPPING",
      "objectives": ["BRAND_AWARENESS", "SEEDING", "TRAFFIC", "CONVERSION", "LEAD_GENERATION"],
      "format": "VIDEO | IMAGE | IMAGE_TEXT | CAROUSEL",
      "budget": 100000,
      "creativeDescription": "description of the creative for this placement"
    }
  ],
  "totalBudget": 500000,
  "competitors": [
    {
      "brandName": "competitor brand",
      "price": 199,
      "positioning": "optional positioning"
    }
  ],
  "brandAwareness": "NEW | EMERGING | WELL_KNOWN | TOP",
  "campaignGoal": "ACQUISITION | RETENTION | MIXED",
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
- If competitors are mentioned, extract them into competitors
- If brand awareness or campaign goal are not specified, default to "EMERGING" and "ACQUISITION"
- If platform is not specified, default to "xiaohongshu"
- If productStage is not clear, default to "NEW_LAUNCH"
        """.trimIndent()
    }

    private fun buildPatchPrompt(rawInput: String, currentPlan: SimulationInput): String {
        val currentPlanJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(currentPlan)
        return """
You are editing an existing structured marketing plan.
Update ONLY the fields the user explicitly wants to change.
Do not delete or overwrite fields the user did not mention.
If the user changes total budget, also update adPlacements budgets so that the placement budget sum matches totalBudget.
If the user changes placements, return the full updated adPlacements array.

Current plan:
---
$currentPlanJson
---

User instruction:
---
$rawInput
---

Output valid JSON only in this format:
{
  "changedFields": ["product.price", "adPlacements", "totalBudget"],
  "warnings": ["optional warning"],
  "patch": {
    "product": {
      "brandName": "optional",
      "name": "optional",
      "price": 299,
      "category": "optional",
      "sellingPoints": "optional",
      "productStage": "NEW_LAUNCH | ESTABLISHED | BESTSELLER",
      "description": "optional"
    },
    "adPlacements": [
      {
        "platform": "xiaohongshu | douyin | wechat | meta | google | tiktok",
        "placementType": "INFO_FEED | SEARCH | KOL_SEEDING | SHORT_VIDEO | SPLASH_SCREEN | LIVESTREAM",
        "objectives": ["BRAND_AWARENESS", "SEEDING", "TRAFFIC", "CONVERSION", "LEAD_GENERATION"],
        "format": "VIDEO | IMAGE | IMAGE_TEXT | CAROUSEL",
        "budget": 100000,
        "creativeDescription": "description"
      }
    ],
    "totalBudget": 500000,
    "competitors": [
      {
        "brandName": "competitor brand",
        "price": 199,
        "positioning": "optional positioning"
      }
    ],
    "brandAwareness": "NEW | EMERGING | WELL_KNOWN | TOP",
    "campaignGoal": "ACQUISITION | RETENTION | MIXED",
    "targetAudience": {
      "ageRange": [25, 35],
      "gender": "female | male | all",
      "region": "optional",
      "interests": ["skincare", "beauty"]
    }
  }
}

Rules:
- Omit unchanged fields from patch
- Use warnings for ambiguous edits or places where you had to infer
- Return changedFields using dot paths when possible
- Never drop competitors, brandAwareness, campaignGoal, or targetAudience fields unless user explicitly changes them
        """.trimIndent()
    }

    private fun parseFullPlanResponse(content: String): SimulationInput {
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
            ),
            competitors = dto.competitors?.map(::toCompetitorInfo) ?: emptyList(),
            brandAwareness = dto.brandAwareness?.let(::toBrandAwareness) ?: BrandAwareness.EMERGING,
            campaignGoal = dto.campaignGoal?.let(::toCampaignGoal) ?: CampaignGoal.ACQUISITION
        )
    }

    private fun parsePatchResponse(content: String): PlanPatchDto {
        return objectMapper.readValue(content)
    }

    private fun mergePatch(currentPlan: SimulationInput, patch: PartialPlanDto, warnings: MutableList<String>): SimulationInput {
        val mergedProduct = patch.product?.let { mergeProduct(currentPlan.product, it) } ?: currentPlan.product
        var mergedPlacements = patch.adPlacements?.map(::toPlacement) ?: currentPlan.adPlacements
        var mergedTotalBudget = patch.totalBudget ?: currentPlan.totalBudget
        val mergedAudience = patch.targetAudience?.let { mergeAudience(currentPlan.targetAudience, it) } ?: currentPlan.targetAudience
        val mergedCompetitors = patch.competitors?.map(::toCompetitorInfo) ?: currentPlan.competitors
        val mergedBrandAwareness = patch.brandAwareness?.let(::toBrandAwareness) ?: currentPlan.brandAwareness
        val mergedCampaignGoal = patch.campaignGoal?.let(::toCampaignGoal) ?: currentPlan.campaignGoal

        if (patch.totalBudget != null && patch.adPlacements == null && currentPlan.adPlacements.isNotEmpty()) {
            mergedPlacements = rebalanceBudgets(currentPlan.adPlacements, patch.totalBudget)
            warnings += "已按现有 placement 占比重新分配预算。"
        }

        if (mergedPlacements.isNotEmpty()) {
            val placementsBudget = mergedPlacements.sumOf { it.budget }
            if (mergedTotalBudget != placementsBudget) {
                mergedTotalBudget = placementsBudget
                warnings += "已按 placement 预算合计重算总预算。"
            }
        }

        return SimulationInput(
            product = mergedProduct,
            adPlacements = mergedPlacements,
            totalBudget = mergedTotalBudget,
            targetAudience = mergedAudience,
            competitors = mergedCompetitors,
            brandAwareness = mergedBrandAwareness,
            campaignGoal = mergedCampaignGoal
        )
    }

    private fun mergeProduct(current: Product, patch: PartialProductDto): Product {
        return current.copy(
            brandName = patch.brandName ?: current.brandName,
            name = patch.name ?: current.name,
            price = patch.price ?: current.price,
            category = patch.category ?: current.category,
            sellingPoints = patch.sellingPoints ?: current.sellingPoints,
            productStage = patch.productStage?.let(::toProductStage) ?: current.productStage,
            description = patch.description ?: current.description
        )
    }

    private fun mergeAudience(current: TargetAudience, patch: PartialAudienceDto): TargetAudience {
        return current.copy(
            ageRange = patch.ageRange ?: current.ageRange,
            gender = patch.gender ?: current.gender,
            region = patch.region ?: current.region,
            interests = patch.interests ?: current.interests
        )
    }

    private fun rebalanceBudgets(placements: List<AdPlacement>, totalBudget: Long): List<AdPlacement> {
        if (placements.isEmpty()) return placements
        val currentTotal = placements.sumOf { it.budget }
        if (currentTotal <= 0L) {
            val evenBudget = totalBudget / placements.size
            var remaining = totalBudget
            return placements.mapIndexed { index, placement ->
                val budget = if (index == placements.lastIndex) remaining else evenBudget.also { remaining -= it }
                placement.copy(budget = budget)
            }
        }

        val scaled = placements.map { placement ->
            val proportion = placement.budget.toDouble() / currentTotal.toDouble()
            (totalBudget * proportion).toLong()
        }.toMutableList()

        val diff = totalBudget - scaled.sum()
        if (scaled.isNotEmpty()) {
            scaled[scaled.lastIndex] += diff
        }

        return placements.mapIndexed { index, placement ->
            placement.copy(budget = scaled[index].coerceAtLeast(0L))
        }
    }

    private fun deriveChangedFields(before: SimulationInput, after: SimulationInput): List<String> {
        val changed = mutableListOf<String>()
        if (before.product != after.product) changed += "product"
        if (before.adPlacements != after.adPlacements) changed += "adPlacements"
        if (before.totalBudget != after.totalBudget) changed += "totalBudget"
        if (before.targetAudience != after.targetAudience) changed += "targetAudience"
        if (before.competitors != after.competitors) changed += "competitors"
        if (before.brandAwareness != after.brandAwareness) changed += "brandAwareness"
        if (before.campaignGoal != after.campaignGoal) changed += "campaignGoal"
        return changed
    }

    private fun toPlacement(p: ParsedPlacementDto): AdPlacement {
        return AdPlacement(
            platform = p.platform,
            placementType = runCatching { PlacementType.valueOf(p.placementType) }.getOrDefault(PlacementType.INFO_FEED),
            objectives = p.objectives.mapNotNull { runCatching { CampaignObjective.valueOf(it) }.getOrNull() }
                .ifEmpty { listOf(CampaignObjective.CONVERSION) },
            format = runCatching { CreativeFormat.valueOf(p.format) }.getOrDefault(CreativeFormat.VIDEO),
            budget = p.budget,
            creativeDescription = p.creativeDescription
        )
    }

    private fun toCompetitorInfo(dto: ParsedCompetitorDto): CompetitorInfo {
        return CompetitorInfo(
            brandName = dto.brandName,
            price = dto.price,
            positioning = dto.positioning ?: ""
        )
    }

    private fun toBrandAwareness(value: String): BrandAwareness {
        return runCatching { BrandAwareness.valueOf(value) }.getOrDefault(BrandAwareness.EMERGING)
    }

    private fun toCampaignGoal(value: String): CampaignGoal {
        return runCatching { CampaignGoal.valueOf(value) }.getOrDefault(CampaignGoal.ACQUISITION)
    }

    private fun toProductStage(value: String): ProductStage {
        return runCatching { ProductStage.valueOf(value) }.getOrDefault(ProductStage.NEW_LAUNCH)
    }

    // DTOs for JSON parsing
    private data class ParsedPlanDto(
        val product: ParsedProductDto,
        val adPlacements: List<ParsedPlacementDto>,
        val totalBudget: Long,
        val targetAudience: ParsedAudienceDto,
        val competitors: List<ParsedCompetitorDto>? = null,
        val brandAwareness: String? = null,
        val campaignGoal: String? = null
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
    private data class ParsedCompetitorDto(
        val brandName: String,
        val price: Double,
        val positioning: String? = null
    )
    private data class PlanPatchDto(
        val changedFields: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val patch: PartialPlanDto = PartialPlanDto()
    )
    private data class PartialPlanDto(
        val product: PartialProductDto? = null,
        val adPlacements: List<ParsedPlacementDto>? = null,
        val totalBudget: Long? = null,
        val targetAudience: PartialAudienceDto? = null,
        val competitors: List<ParsedCompetitorDto>? = null,
        val brandAwareness: String? = null,
        val campaignGoal: String? = null
    )
    private data class PartialProductDto(
        val brandName: String? = null,
        val name: String? = null,
        val price: Double? = null,
        val category: String? = null,
        val sellingPoints: String? = null,
        val productStage: String? = null,
        val description: String? = null
    )
    private data class PartialAudienceDto(
        val ageRange: List<Int>? = null,
        val gender: String? = null,
        val region: String? = null,
        val interests: List<String>? = null
    )

    companion object {
        private const val FULL_PLAN_SYSTEM_PROMPT = """You are a marketing plan parser.
Extract structured information from natural language marketing plans.
You understand both Chinese and English marketing terminology.
Output valid JSON only. Be precise with numbers and categorizations."""
        private const val PATCH_SYSTEM_PROMPT = """You are a marketing plan patch compiler.
Update only the fields the user asked to change.
Preserve all unspecified fields.
Output valid JSON only."""
    }
}
