package com.adsim.simulation

import com.adsim.config.SimulationConfig
import com.adsim.model.*
import com.adsim.support.FakeChatModel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationEngineTest {

    @Test
    fun `run simulates every placement and stores placement decisions`() = runBlocking {
        val agentRepository = Mockito.mock(AgentRepository::class.java)
        val priorRepository = Mockito.mock(PlacementPriorRepository::class.java)
        Mockito.`when`(priorRepository.findByPlatformAndPlacementTypeAndCategory("xiaohongshu", PlacementType.INFO_FEED, "护肤"))
            .thenReturn(
                PlacementPrior(
                    platform = "xiaohongshu",
                    placementType = PlacementType.INFO_FEED,
                    category = "护肤",
                    baseAttention = 1.0,
                    baseClick = 1.0,
                    baseConversion = 0.1,
                    calibrationCount = 2
                )
            )
        val engine = SimulationEngine(
            agentRepository = agentRepository,
            config = SimulationConfig(maxConcurrency = 1, maxRetries = 1, agentBatchSize = 1),
            priorCalibrationService = PriorCalibrationService(priorRepository)
        )
        val progress = mutableListOf<Int>()
        val input = SimulationInput(
            product = Product(
                brandName = "珀莱雅",
                name = "双抗精华",
                price = 299.0,
                category = "护肤",
                sellingPoints = "抗老修护",
                productStage = ProductStage.ESTABLISHED
            ),
            adPlacements = listOf(
                AdPlacement(
                    platform = "xiaohongshu",
                    placementType = PlacementType.INFO_FEED,
                    objectives = listOf(CampaignObjective.CONVERSION),
                    format = CreativeFormat.VIDEO,
                    budget = 200000L,
                    creativeDescription = "信息流视频"
                ),
                AdPlacement(
                    platform = "google",
                    placementType = PlacementType.SEARCH,
                    objectives = listOf(CampaignObjective.CONVERSION),
                    format = CreativeFormat.IMAGE_TEXT,
                    budget = 100000L,
                    creativeDescription = "搜索推广"
                )
            ),
            totalBudget = 300000L,
            targetAudience = TargetAudience()
        )
        val agent = Agent(
            id = "agent-1",
            simulationId = "sim-1",
            persona = Persona(
                name = "Ava",
                age = 28,
                gender = "female",
                income = IncomeLevel.MEDIUM,
                cityTier = 1,
                interests = listOf("护肤"),
                platformBehavior = PlatformBehavior(
                    dailyUsageMinutes = 60,
                    contentPreferences = listOf("video", "search"),
                    purchaseFrequency = PurchaseFrequency.SOMETIMES
                ),
                consumptionHabits = ConsumptionHabits(
                    priceSensitivity = SensitivityLevel.MEDIUM,
                    decisionSpeed = DecisionSpeed.MODERATE,
                    brandLoyalty = SensitivityLevel.MEDIUM
                )
            )
        )
        val chatModel = FakeChatModel(
            listOf(
                """{"likelihoodBand": "HIGH", "probability": 1.0, "reasoning": "注意到了", "positiveFactors": ["interest_match"]}""",
                """{"likelihoodBand": "HIGH", "probability": 1.0, "reasoning": "想继续看", "positiveFactors": ["creative_appeal"]}""",
                """{"likelihoodBand": "LOW", "probability": 0.0, "reasoning": "价格还是偏高", "negativeFactors": ["price_too_high"]}"""
            )
        )

        val exposureEvents = listOf(
            ExposureEvent(
                agentId = "agent-1",
                placementIndex = 0,
                sequence = 0,
                deliveryContext = DeliveryContext(source = "test", frequency = 1)
            )
        )

        engine.run(input, listOf(agent), chatModel, concurrency = 1, exposureEvents = exposureEvents) { completed ->
            progress += completed
        }

        @Suppress("UNCHECKED_CAST")
        val savedAgent = Mockito.mockingDetails(agentRepository).invocations
            .first { invocation -> invocation.method.name == "saveAll" }
            .arguments[0] as List<Agent>

        val decidedAgent = savedAgent.single()
        assertEquals(listOf(1), progress)
        assertEquals(1, decidedAgent.placementOutcomes.size)
        assertEquals(1, decidedAgent.placementDecisions.size)
        assertEquals(PlacementType.INFO_FEED, decidedAgent.placementOutcomes[0].placementType)
        assertEquals(exposureEvents.first(), decidedAgent.placementOutcomes.first().exposureEvent)
        assertNotNull(decidedAgent.decisions)
        assertEquals(decidedAgent.placementOutcomes.first().decisions, decidedAgent.decisions)
        assertEquals(decidedAgent.placementOutcomes.first().decisions, decidedAgent.placementDecisions.first().decisions)
        assertTrue(decidedAgent.placementOutcomes.first().decisions.click.passed)
        assertEquals(LikelihoodBand.HIGH, decidedAgent.placementOutcomes.first().decisions.attention.likelihoodBand)
        assertEquals(1.0, decidedAgent.placementOutcomes.first().decisions.click.probability)
        assertEquals(0.02, decidedAgent.placementOutcomes.first().decisions.conversion.probability ?: 0.0, 0.0001)
        assertEquals(listOf("price_too_high"), decidedAgent.placementOutcomes.first().decisions.conversion.negativeFactors)
        assertEquals(1, decidedAgent.campaignState.placementsSeen)
        assertEquals(1, decidedAgent.campaignState.noticedCount)
        assertEquals(1, decidedAgent.campaignState.clickedCount)
        assertTrue(decidedAgent.campaignState.fatigueScore > 0)
    }
}
