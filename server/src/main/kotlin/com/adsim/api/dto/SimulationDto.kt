package com.adsim.api.dto

import jakarta.validation.constraints.NotBlank

// Natural language input — the primary way to create simulations
data class ParsePlanRequest(
    @field:NotBlank val content: String,
    val currentPlan: com.adsim.model.SimulationInput? = null
)

// Direct structured input — advanced mode / confirmation after parse
data class CreateSimulationRequest(
    val input: com.adsim.model.SimulationInput,
    val rawInput: String = "",
    val agentCount: Int = 200
)

data class ParsePlanResponse(
    val mergedPlan: com.adsim.model.SimulationInput,
    val changedFields: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val missingFields: List<String>
)

data class InterviewRequest(
    @field:NotBlank val message: String,
    val conversationId: String? = null
)

data class InterviewResponse(
    val reply: String,
    val conversationId: String
)

data class UpdateCalibrationRequest(
    val placements: List<CalibrationPlacementRequest> = emptyList()
)

data class CalibrationPlacementRequest(
    val placementIndex: Int,
    val ctr: Double? = null,
    val cvr: Double? = null,
    val cpa: Double? = null,
    val impressions: Long? = null,
    val conversions: Long? = null
)
