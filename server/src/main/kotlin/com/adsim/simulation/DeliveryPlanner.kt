package com.adsim.simulation

import com.adsim.model.AdPlacement
import com.adsim.model.Agent
import com.adsim.model.CampaignObjective
import com.adsim.model.DeliveryContext
import com.adsim.model.DeliveryPlan
import com.adsim.model.ExposureEvent
import com.adsim.model.PlacementDeliverySummary
import com.adsim.model.PlacementType
import org.springframework.stereotype.Component
import kotlin.math.min

@Component
class DeliveryPlanner(
    private val priorCalibrationService: PriorCalibrationService
) {

    fun plan(agents: List<Agent>, placements: List<AdPlacement>, category: String? = null): DeliveryPlan {
        if (agents.isEmpty() || placements.isEmpty()) {
            return DeliveryPlan()
        }

        val exposureEvents = mutableListOf<ExposureEvent>()
        val placementSummaries = mutableListOf<PlacementDeliverySummary>()

        placements.forEachIndexed { placementIndex, placement ->
            val eligibleAgents = agents.filter { isEligible(it, placement) }
            val targetSamples = targetSamples(agents.size, placement, eligibleAgents.size, category)
            val selectedAgents = eligibleAgents.take(targetSamples)
            val placementEvents = selectedAgents.mapIndexed { sequence, agent ->
                ExposureEvent(
                    agentId = agent.id ?: error("DeliveryPlanner requires persisted agents with id"),
                    placementIndex = placementIndex,
                    sequence = sequence,
                    deliveryContext = DeliveryContext(
                        source = deliverySource(placement),
                        frequency = 1,
                        intentLevel = searchIntentLevel(agent, placement),
                        estimatedCostWeight = costWeight(placement)
                    )
                )
            }

            exposureEvents += placementEvents
            placementSummaries += PlacementDeliverySummary(
                placementIndex = placementIndex,
                plannedSamples = placementEvents.size,
                eligibleAgents = eligibleAgents.size,
                uniqueAgentsReached = placementEvents.map { it.agentId }.distinct().size,
                eligibleAgentIds = eligibleAgents.mapNotNull { it.id },
                reachedAgentIds = placementEvents.map { it.agentId }.distinct()
            )
        }

        return DeliveryPlan(
            exposureEvents = exposureEvents,
            placementSummaries = placementSummaries
        )
    }

    private fun isEligible(agent: Agent, placement: AdPlacement): Boolean {
        return when (placement.placementType) {
            PlacementType.SEARCH -> qualifiesForSearch(agent, placement)
            else -> true
        }
    }

    private fun targetSamples(agentCount: Int, placement: AdPlacement, eligibleCount: Int, category: String?): Int {
        if (eligibleCount <= 0) return 0
        val budgetRatio = placement.budget.toDouble() / MIN_PLACEMENT_BUDGET.toDouble()
        val baseReach = when (placement.placementType) {
            PlacementType.INFO_FEED -> 0.75
            PlacementType.SEARCH -> 0.35
            PlacementType.KOL_SEEDING -> 0.45
            PlacementType.SHORT_VIDEO -> 0.7
            PlacementType.SPLASH_SCREEN -> 0.85
            PlacementType.LIVESTREAM -> 0.4
            PlacementType.HASHTAG_CHALLENGE -> 0.5
            PlacementType.SHOPPING -> 0.6
        }
        val budgetBoost = (budgetRatio / 3.0).coerceIn(0.0, 0.3)
        val priorWeight = category?.let {
            priorCalibrationService.deliveryMixWeight(placement.platform, placement.placementType, it)
        } ?: 1.0
        val planned = (agentCount * (baseReach + budgetBoost) * priorWeight).toInt().coerceAtLeast(1)
        return min(planned, eligibleCount)
    }

    private fun qualifiesForSearch(agent: Agent, placement: AdPlacement): Boolean {
        val intent = searchIntentLevel(agent, placement) ?: return false
        return intent >= SEARCH_INTENT_THRESHOLD
    }

    private fun searchIntentLevel(agent: Agent, placement: AdPlacement): Double? {
        if (placement.placementType != PlacementType.SEARCH) return null

        val persona = agent.persona
        var score = 0.0

        if (persona.platformBehavior.contentPreferences.any { it.contains("search", ignoreCase = true) }) {
            score += 0.35
        }
        if (persona.interests.any { interest ->
                interest.contains(placement.platform, ignoreCase = true) ||
                    interest.contains("测评") ||
                    interest.contains("比价") ||
                    interest.contains("攻略")
            }) {
            score += 0.2
        }
        if (persona.consumptionHabits.decisionSpeed.name == "DELIBERATE") {
            score += 0.2
        }
        if (persona.consumptionHabits.priceSensitivity.name != "LOW") {
            score += 0.1
        }
        if (placement.objectives.contains(CampaignObjective.CONVERSION)) {
            score += 0.1
        }
        if (persona.consumerContext.satisfaction == "looking_for_alternatives") {
            score += 0.15
        }

        return score.coerceIn(0.0, 1.0)
    }

    private fun deliverySource(placement: AdPlacement): String {
        return when (placement.placementType) {
            PlacementType.SEARCH -> "search_intent_gate"
            else -> "placement_delivery_mix"
        }
    }

    private fun costWeight(placement: AdPlacement): Double {
        return when (placement.placementType) {
            PlacementType.SEARCH -> 1.2
            PlacementType.KOL_SEEDING -> 1.4
            PlacementType.LIVESTREAM -> 1.3
            PlacementType.HASHTAG_CHALLENGE -> 1.35
            else -> 1.0
        }
    }

    companion object {
        private const val SEARCH_INTENT_THRESHOLD = 0.45
        private const val MIN_PLACEMENT_BUDGET = 50_000L
    }
}
