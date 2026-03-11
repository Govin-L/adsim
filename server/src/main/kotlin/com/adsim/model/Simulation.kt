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
    val results: SimulationResults? = null,
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

data class SimulationInput(
    val product: Product,
    val creative: Creative,
    val platform: String = "xiaohongshu",
    val budget: Long,
    val targetAudience: TargetAudience
)

data class Product(
    val name: String,
    val price: Double,
    val category: String,
    val description: String = ""
)

data class Creative(
    val description: String,
    val format: CreativeFormat = CreativeFormat.VIDEO
)

enum class CreativeFormat {
    VIDEO, IMAGE, TEXT
}

data class TargetAudience(
    val ageRange: List<Int> = listOf(18, 65),
    val gender: String = "all",
    val interests: List<String> = emptyList()
)
