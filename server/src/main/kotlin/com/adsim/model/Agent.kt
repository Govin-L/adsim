package com.adsim.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document("agents")
data class Agent(
    @Id
    val id: String? = null,
    @Indexed
    val simulationId: String,
    val persona: Persona,
    val decisions: Decisions? = null,
    val placementDecisions: List<PlacementDecisions> = emptyList(),
    val placementOutcomes: List<PlacementOutcome> = emptyList(),
    val campaignState: AgentCampaignState = AgentCampaignState()
)

data class PlacementDecisions(
    val placementIndex: Int,
    val platform: String,
    val placementType: PlacementType,
    val decisions: Decisions,
    val exposureEvent: ExposureEvent? = null
)

data class PlacementOutcome(
    val placementIndex: Int,
    val platform: String,
    val placementType: PlacementType,
    val exposureEvent: ExposureEvent,
    val attention: StageDecision,
    val click: StageDecision,
    val conversion: StageDecision
) {
    val decisions: Decisions
        get() = Decisions(
            attention = attention,
            click = click,
            conversion = conversion
        )
}

data class AgentCampaignState(
    val placementsSeen: Int = 0,
    val noticedCount: Int = 0,
    val clickedCount: Int = 0,
    val convertedCount: Int = 0,
    val fatigueScore: Int = 0,
    val brandFamiliarity: String = "never_heard"
)

data class ConsumerContext(
    val currentBrand: String? = null,
    val currentProductPrice: Double? = null,
    val satisfaction: String? = null,  // "satisfied" / "neutral" / "looking_for_alternatives"
    val brandAwareness: String = "never_heard",  // "never_heard" / "heard_not_tried" / "tried_once" / "regular_user"
    val recentAdExposure: Int = 0
)

data class Persona(
    val name: String,
    val age: Int,
    val gender: String,
    val income: IncomeLevel,
    val cityTier: Int,
    val interests: List<String>,
    val platformBehavior: PlatformBehavior,
    val consumptionHabits: ConsumptionHabits,
    val consumerContext: ConsumerContext = ConsumerContext()
)

enum class IncomeLevel { LOW, MEDIUM, HIGH }

data class PlatformBehavior(
    val dailyUsageMinutes: Int,
    val contentPreferences: List<String>,
    val purchaseFrequency: PurchaseFrequency
)

enum class PurchaseFrequency { NEVER, RARELY, SOMETIMES, OFTEN }

data class ConsumptionHabits(
    val priceSensitivity: SensitivityLevel,
    val decisionSpeed: DecisionSpeed,
    val brandLoyalty: SensitivityLevel
)

enum class SensitivityLevel { LOW, MEDIUM, HIGH }
enum class DecisionSpeed { IMPULSIVE, MODERATE, DELIBERATE }

data class Decisions(
    val attention: StageDecision,
    val click: StageDecision,
    val conversion: StageDecision
)

data class StageDecision(
    val passed: Boolean,
    val reasoning: String,
    val factors: List<String> = emptyList(),
    val score: Int = 0,  // keep for backward compat
    val likelihoodBand: LikelihoodBand? = null,
    val probability: Double? = null,
    val positiveFactors: List<String> = emptyList(),
    val negativeFactors: List<String> = emptyList()
)

enum class LikelihoodBand {
    VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
}
