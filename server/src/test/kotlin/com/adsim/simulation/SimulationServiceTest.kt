package com.adsim.simulation

import com.adsim.agent.AgentGenerator
import com.adsim.api.dto.CalibrationPlacementRequest
import com.adsim.api.dto.UpdateCalibrationRequest
import com.adsim.model.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationServiceTest {

    @Test
    fun `update calibration persists deltas onto simulation results`() {
        val simulationRepository = Mockito.mock(SimulationRepository::class.java)
        val priorRepository = Mockito.mock(PlacementPriorRepository::class.java)
        val simulation = completedSimulation()

        Mockito.`when`(simulationRepository.findById("sim-1")).thenReturn(Optional.of(simulation))
        Mockito.`when`(simulationRepository.save(Mockito.any(Simulation::class.java))).thenAnswer { it.arguments[0] as Simulation }
        Mockito.`when`(priorRepository.save(Mockito.any(PlacementPrior::class.java))).thenAnswer { it.arguments[0] as PlacementPrior }

        val service = SimulationService(
            simulationRepository = simulationRepository,
            agentRepository = Mockito.mock(AgentRepository::class.java),
            agentGenerator = Mockito.mock(AgentGenerator::class.java),
            simulationEngine = Mockito.mock(SimulationEngine::class.java),
            deliveryPlanner = Mockito.mock(DeliveryPlanner::class.java),
            resultAggregator = Mockito.mock(ResultAggregator::class.java),
            simulationQualityEvaluator = SimulationQualityEvaluator(),
            interviewService = Mockito.mock(InterviewService::class.java),
            priorCalibrationService = PriorCalibrationService(priorRepository),
            config = com.adsim.config.SimulationConfig()
        )

        val updated = service.updateCalibration(
            "sim-1",
            UpdateCalibrationRequest(
                placements = listOf(
                    CalibrationPlacementRequest(
                        placementIndex = 0,
                        ctr = 0.08,
                        cvr = 0.03,
                        cpa = 120.0
                    )
                )
            )
        )

        assertNotNull(updated)
        assertNotNull(updated.results?.calibration)
        assertEquals(1, updated.results?.calibration?.summary?.coverage)
        val ctrDelta = updated.results?.calibration?.placements?.firstOrNull()?.deltas?.ctrDelta
        assertNotNull(ctrDelta)
        assertEquals(0.03, ctrDelta, 0.0001)

        @Suppress("UNCHECKED_CAST")
        val savedPrior = Mockito.mockingDetails(priorRepository).invocations
            .first { invocation -> invocation.method.name == "save" }
            .arguments[0] as PlacementPrior
        assertEquals("xiaohongshu", savedPrior.platform)
        assertEquals(PlacementType.INFO_FEED, savedPrior.placementType)
        assertEquals("护肤", savedPrior.category)
        assertTrue(savedPrior.baseClick > 0.05)
        assertTrue(savedPrior.baseConversion > 0.02)
    }

    private fun completedSimulation(): Simulation {
        val placement = AdPlacement(
            platform = "xiaohongshu",
            placementType = PlacementType.INFO_FEED,
            objectives = listOf(CampaignObjective.CONVERSION),
            format = CreativeFormat.VIDEO,
            budget = 100000L,
            creativeDescription = "信息流视频"
        )
        return Simulation(
            id = "sim-1",
            status = SimulationStatus.COMPLETED,
            input = SimulationInput(
                product = Product(
                    brandName = "珀莱雅",
                    name = "双抗精华",
                    price = 299.0,
                    category = "护肤",
                    sellingPoints = "抗老修护",
                    productStage = ProductStage.ESTABLISHED
                ),
                adPlacements = listOf(placement),
                totalBudget = 100000L,
                targetAudience = TargetAudience()
            ),
            results = SimulationResults(
                totalAgents = 20,
                successfulAgents = 20,
                metrics = Metrics(0.6, 0.05, 0.02, 0.01, 150.0),
                simulatedMetrics = SimulatedFunnelMetrics(0.6, 0.05, 0.02, 0.01),
                estimatedMetrics = EstimatedBusinessMetrics(2500000.0, 1250000.0, 12500.0, 150.0),
                funnel = Funnel(
                    exposure = FunnelStage(20, 1.0),
                    attention = FunnelStage(12, 0.6),
                    click = FunnelStage(1, 0.05),
                    conversion = FunnelStage(0, 0.02)
                ),
                dropOffReasons = DropOffReasons(emptyList(), emptyList()),
                placementResults = listOf(
                    PlacementResult(
                        placementIndex = 0,
                        placement = placement,
                        totalAgents = 20,
                        successfulAgents = 20,
                        metrics = Metrics(0.6, 0.05, 0.02, 0.01, 150.0),
                        simulatedMetrics = SimulatedFunnelMetrics(0.6, 0.05, 0.02, 0.01),
                        estimatedMetrics = EstimatedBusinessMetrics(2500000.0, 1250000.0, 12500.0, 150.0),
                        funnel = Funnel(
                            exposure = FunnelStage(20, 1.0),
                            attention = FunnelStage(12, 0.6),
                            click = FunnelStage(1, 0.05),
                            conversion = FunnelStage(0, 0.02)
                        ),
                        dropOffReasons = DropOffReasons(emptyList(), emptyList())
                    )
                )
            ),
            createdAt = Instant.now()
        )
    }
}
