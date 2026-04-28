package com.adsim.model

data class ExposureEvent(
    val agentId: String,
    val placementIndex: Int,
    val sequence: Int,
    val deliveryContext: DeliveryContext = DeliveryContext()
)

data class DeliveryContext(
    val source: String = "delivery_planner",
    val frequency: Int = 1,
    val intentLevel: Double? = null,
    val estimatedCostWeight: Double = 1.0
)

data class DeliveryPlan(
    val exposureEvents: List<ExposureEvent> = emptyList(),
    val placementSummaries: List<PlacementDeliverySummary> = emptyList()
) {
    val plannedSamples: Int
        get() = exposureEvents.size
}

data class PlacementDeliverySummary(
    val placementIndex: Int,
    val plannedSamples: Int,
    val eligibleAgents: Int,
    val uniqueAgentsReached: Int,
    val eligibleAgentIds: List<String> = emptyList(),
    val reachedAgentIds: List<String> = emptyList()
)
