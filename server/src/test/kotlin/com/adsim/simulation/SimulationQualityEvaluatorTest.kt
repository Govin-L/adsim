package com.adsim.simulation

import com.adsim.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationQualityEvaluatorTest {

    private val evaluator = SimulationQualityEvaluator()

    @Test
    fun `evaluate returns completed with warnings when sample success rate is below threshold`() {
        val agents = listOf(
            agentWithPlacements(simulatedPlacements = 2),
            agentWithPlacements(simulatedPlacements = 1),
            agentWithPlacements(simulatedPlacements = 0)
        )

        val assessment = evaluator.evaluate(
            requestedAgents = 4,
            generatedAgents = 3,
            agents = agents,
            plannedSamples = 6,
            minSuccessRate = 0.8
        )

        assertEquals(SimulationStatus.COMPLETED_WITH_WARNINGS, assessment.status)
        assertEquals(0.75, assessment.quality.generationSuccessRate, 0.0001)
        assertEquals(0.5, assessment.quality.sampleSuccessRate, 0.0001)
        assertTrue(assessment.quality.warningCodes.contains("low_generation_success_rate"))
        assertTrue(assessment.quality.warningCodes.contains("low_simulation_success_rate"))
        assertTrue(assessment.quality.failedStages.contains("generation"))
        assertTrue(assessment.quality.failedStages.contains("simulation"))
    }

    @Test
    fun `evaluate returns failed when no simulation samples completed`() {
        val assessment = evaluator.evaluate(
            requestedAgents = 3,
            generatedAgents = 3,
            agents = listOf(
                Agent(simulationId = "sim-1", persona = basePersona()),
                Agent(simulationId = "sim-1", persona = basePersona())
            ),
            plannedSamples = 6,
            minSuccessRate = 0.8
        )

        assertEquals(SimulationStatus.FAILED, assessment.status)
        assertEquals("No placement simulations completed successfully.", assessment.errorMessage)
    }

    @Test
    fun `evaluate returns reasonability warnings for extreme metrics and broad search coverage`() {
        val agents = listOf(
            agentWithDecisionProfile(attentionPassed = true, clickPassed = true, conversionPassed = true),
            agentWithDecisionProfile(attentionPassed = true, clickPassed = true, conversionPassed = true),
            agentWithDecisionProfile(attentionPassed = true, clickPassed = true, conversionPassed = false)
        )
        val utilization = AgentUtilization(
            uniqueAgentsReached = 3,
            uniqueReachRate = 1.0,
            averageExposuresPerReachedAgent = 1.0,
            averagePlacementsPerReachedAgent = 1.0,
            searchEligibleRate = 0.9
        )

        val assessment = evaluator.evaluate(
            requestedAgents = 3,
            generatedAgents = 3,
            agents = agents,
            plannedSamples = 3,
            minSuccessRate = 0.8,
            utilization = utilization
        )

        assertEquals(SimulationStatus.COMPLETED_WITH_WARNINGS, assessment.status)
        assertTrue(assessment.quality.reasonabilityWarnings.any { it.code == "low_sample_confidence" })
        assertTrue(assessment.quality.reasonabilityWarnings.any { it.code == "attention_too_high" })
        assertTrue(assessment.quality.reasonabilityWarnings.any { it.code == "ctr_too_high" })
        assertTrue(assessment.quality.reasonabilityWarnings.any { it.code == "cvr_too_high" })
        assertTrue(assessment.quality.reasonabilityWarnings.any { it.code == "search_coverage_too_high" })
    }

    private fun agentWithPlacements(simulatedPlacements: Int): Agent {
        val outcomes = (0 until simulatedPlacements).map { index ->
            val decisions = Decisions(
                attention = StageDecision(true, "注意到了"),
                click = StageDecision(index == 0, "点击表现"),
                conversion = StageDecision(false, "还没转化")
            )
            PlacementOutcome(
                placementIndex = index,
                platform = if (index == 0) "xiaohongshu" else "google",
                placementType = if (index == 0) PlacementType.INFO_FEED else PlacementType.SEARCH,
                exposureEvent = ExposureEvent(
                    agentId = "agent-$index",
                    placementIndex = index,
                    sequence = index,
                    deliveryContext = DeliveryContext(source = "test", frequency = 1)
                ),
                attention = decisions.attention,
                click = decisions.click,
                conversion = decisions.conversion
            )
        }
        return Agent(
            simulationId = "sim-1",
            persona = basePersona(),
            decisions = outcomes.firstOrNull()?.decisions,
            placementOutcomes = outcomes
        )
    }

    private fun agentWithDecisionProfile(
        attentionPassed: Boolean,
        clickPassed: Boolean,
        conversionPassed: Boolean
    ): Agent {
        val decisions = Decisions(
            attention = StageDecision(attentionPassed, "注意到了"),
            click = StageDecision(clickPassed, "点击了"),
            conversion = StageDecision(conversionPassed, "转化了")
        )
        return Agent(
            simulationId = "sim-1",
            persona = basePersona(),
            decisions = decisions,
            placementOutcomes = listOf(
                PlacementOutcome(
                    placementIndex = 0,
                    platform = "google",
                    placementType = PlacementType.SEARCH,
                    exposureEvent = ExposureEvent(
                        agentId = "agent-profile",
                        placementIndex = 0,
                        sequence = 0,
                        deliveryContext = DeliveryContext(source = "test", frequency = 1, intentLevel = 0.9)
                    ),
                    attention = decisions.attention,
                    click = decisions.click,
                    conversion = decisions.conversion
                )
            )
        )
    }

    private fun basePersona(): Persona {
        return Persona(
            name = "Ava",
            age = 28,
            gender = "female",
            income = IncomeLevel.MEDIUM,
            cityTier = 1,
            interests = listOf("护肤"),
            platformBehavior = PlatformBehavior(
                dailyUsageMinutes = 60,
                contentPreferences = listOf("video"),
                purchaseFrequency = PurchaseFrequency.SOMETIMES
            ),
            consumptionHabits = ConsumptionHabits(
                priceSensitivity = SensitivityLevel.MEDIUM,
                decisionSpeed = DecisionSpeed.MODERATE,
                brandLoyalty = SensitivityLevel.MEDIUM
            )
        )
    }
}
