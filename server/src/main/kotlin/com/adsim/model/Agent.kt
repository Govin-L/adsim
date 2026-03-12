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
    val decisions: Decisions? = null
)

data class Persona(
    val name: String,
    val age: Int,
    val gender: String,
    val income: IncomeLevel,
    val cityTier: Int,
    val interests: List<String>,
    val platformBehavior: PlatformBehavior,
    val consumptionHabits: ConsumptionHabits
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
    val score: Int = 0 // 0-100, LLM rates appeal/intent, system decides passed via threshold
)
