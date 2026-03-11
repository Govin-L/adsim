package com.adsim.api.dto

import com.adsim.model.*
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CreateSimulationRequest(
    @field:Valid @field:NotNull
    val product: ProductDto,
    @field:Valid @field:NotNull
    val creative: CreativeDto,
    val platform: String = "xiaohongshu",
    @field:Min(1)
    val budget: Long,
    @field:Valid @field:NotNull
    val targetAudience: TargetAudienceDto,
    @field:Min(10)
    val agentCount: Int = 200
) {
    fun toInput() = SimulationInput(
        product = Product(
            name = product.name,
            price = product.price,
            category = product.category,
            description = product.description ?: ""
        ),
        creative = Creative(
            description = creative.description,
            format = creative.format ?: CreativeFormat.VIDEO
        ),
        platform = platform,
        budget = budget,
        targetAudience = TargetAudience(
            ageRange = targetAudience.ageRange ?: listOf(18, 65),
            gender = targetAudience.gender ?: "all",
            interests = targetAudience.interests ?: emptyList()
        )
    )
}

data class ProductDto(
    @field:NotBlank val name: String,
    @field:Min(0) val price: Double,
    @field:NotBlank val category: String,
    val description: String? = null
)

data class CreativeDto(
    @field:NotBlank val description: String,
    val format: CreativeFormat? = null
)

data class TargetAudienceDto(
    val ageRange: List<Int>? = null,
    val gender: String? = null,
    val interests: List<String>? = null
)

data class InterviewRequest(
    @field:NotBlank val message: String,
    val conversationId: String? = null
)

data class InterviewResponse(
    val reply: String,
    val conversationId: String
)
