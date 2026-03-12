package com.adsim.api.dto

import jakarta.validation.constraints.NotBlank

// Natural language input — the primary way to create simulations
data class ParsePlanRequest(
    @field:NotBlank val content: String
)

// Direct structured input — advanced mode / confirmation after parse
data class CreateSimulationRequest(
    val input: com.adsim.model.SimulationInput,
    val rawInput: String = "",
    val agentCount: Int = 200
)

data class ParsePlanResponse(
    val input: com.adsim.model.SimulationInput,
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
