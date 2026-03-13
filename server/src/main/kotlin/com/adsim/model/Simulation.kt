package com.adsim.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("simulations")
data class Simulation(
    @Id
    val id: String? = null,
    val status: SimulationStatus = SimulationStatus.PENDING,
    val progress: Progress = Progress(),
    val input: SimulationInput,
    val rawInput: String = "",
    val results: SimulationResults? = null,
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null
)

enum class SimulationStatus {
    PENDING, GENERATING, SIMULATING, AGGREGATING, COMPLETED, FAILED
}

data class Progress(
    val total: Int = 0,
    val completed: Int = 0
)

data class CompetitorInfo(
    val brandName: String,
    val price: Double,
    val positioning: String = ""
)

enum class BrandAwareness { NEW, EMERGING, WELL_KNOWN, TOP }
enum class CampaignGoal { ACQUISITION, RETENTION, MIXED }

data class SimulationInput(
    val product: Product,
    val adPlacements: List<AdPlacement>,
    val totalBudget: Long,
    val targetAudience: TargetAudience,
    val competitors: List<CompetitorInfo> = emptyList(),
    val brandAwareness: BrandAwareness = BrandAwareness.EMERGING,
    val campaignGoal: CampaignGoal = CampaignGoal.ACQUISITION
)

data class Product(
    val brandName: String,
    val name: String,
    val price: Double,
    val category: String,
    val sellingPoints: String,
    val productStage: ProductStage,
    val description: String = ""
)

enum class ProductStage {
    NEW_LAUNCH, ESTABLISHED, BESTSELLER
}

data class AdPlacement(
    val platform: String,
    val placementType: PlacementType,
    val objectives: List<CampaignObjective>,
    val format: CreativeFormat,
    val budget: Long,
    val creativeDescription: String
)

enum class PlacementType {
    INFO_FEED, SEARCH, KOL_SEEDING, SHORT_VIDEO, SPLASH_SCREEN, LIVESTREAM, HASHTAG_CHALLENGE, SHOPPING
}

enum class CampaignObjective {
    BRAND_AWARENESS, SEEDING, TRAFFIC, CONVERSION, LEAD_GENERATION
}

enum class CreativeFormat {
    VIDEO, IMAGE, IMAGE_TEXT, CAROUSEL
}

data class TargetAudience(
    val ageRange: List<Int> = listOf(18, 65),
    val gender: String = "all",
    val region: String = "",
    val interests: List<String> = emptyList()
)
