package com.adsim.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

@Document("placement_priors")
data class PlacementPrior(
    @Id
    val id: String? = null,
    val platform: String,
    val placementType: PlacementType,
    val category: String,
    val baseAttention: Double,
    val baseClick: Double,
    val baseConversion: Double,
    val calibrationCount: Int = 0,
    val updatedAt: Instant = Instant.now()
)

interface PlacementPriorRepository : MongoRepository<PlacementPrior, String> {
    fun findByPlatformAndPlacementTypeAndCategory(
        platform: String,
        placementType: PlacementType,
        category: String
    ): PlacementPrior?
}
