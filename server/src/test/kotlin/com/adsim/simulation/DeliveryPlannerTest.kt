package com.adsim.simulation

import com.adsim.model.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeliveryPlannerTest {

    private val priorRepository = Mockito.mock(PlacementPriorRepository::class.java)
    private val planner = DeliveryPlanner(PriorCalibrationService(priorRepository))

    @BeforeEach
    fun resetMocks() {
        Mockito.reset(priorRepository)
    }

    @Test
    fun `plan reduces search coverage and responds to placement budgets`() {
        val agents = listOf(
            agent("Ava", listOf("video", "search"), listOf("护肤", "测评"), DecisionSpeed.DELIBERATE),
            agent("Bella", listOf("video"), listOf("护肤"), DecisionSpeed.MODERATE),
            agent("Chris", listOf("search", "review"), listOf("比价", "攻略"), DecisionSpeed.DELIBERATE),
            agent("Dylan", listOf("short_video"), listOf("娱乐"), DecisionSpeed.IMPULSIVE)
        )
        val placements = listOf(
            AdPlacement(
                platform = "xiaohongshu",
                placementType = PlacementType.INFO_FEED,
                objectives = listOf(CampaignObjective.CONVERSION),
                format = CreativeFormat.VIDEO,
                budget = 200_000L,
                creativeDescription = "信息流视频"
            ),
            AdPlacement(
                platform = "google",
                placementType = PlacementType.SEARCH,
                objectives = listOf(CampaignObjective.CONVERSION),
                format = CreativeFormat.IMAGE_TEXT,
                budget = 50_000L,
                creativeDescription = "搜索广告"
            )
        )

        val plan = planner.plan(agents, placements, "护肤")
        val feedSummary = plan.placementSummaries.first { it.placementIndex == 0 }
        val searchSummary = plan.placementSummaries.first { it.placementIndex == 1 }

        assertTrue(feedSummary.plannedSamples > searchSummary.plannedSamples)
        assertTrue(searchSummary.uniqueAgentsReached < agents.size)
        assertEquals(searchSummary.plannedSamples, plan.exposureEvents.count { it.placementIndex == 1 })
        assertTrue(plan.exposureEvents.filter { it.placementIndex == 1 }.all { it.deliveryContext.intentLevel != null })
    }

    @Test
    fun `plan keeps baseline exposure mix when no prior exists`() {
        val agents = (1..10).map { index ->
            agent("Agent$index", listOf("video"), listOf("护肤"), DecisionSpeed.MODERATE)
        }
        val placement = AdPlacement(
            platform = "xiaohongshu",
            placementType = PlacementType.KOL_SEEDING,
            objectives = listOf(CampaignObjective.CONVERSION),
            format = CreativeFormat.VIDEO,
            budget = 50_000L,
            creativeDescription = "达人种草视频"
        )

        val plan = planner.plan(agents, listOf(placement), "护肤")

        assertEquals(7, plan.placementSummaries.first().plannedSamples)
    }

    @Test
    fun `plan increases exposure mix when strong prior exists`() {
        val agents = (1..10).map { index ->
            agent("Agent$index", listOf("video"), listOf("护肤"), DecisionSpeed.MODERATE)
        }
        val placement = AdPlacement(
            platform = "xiaohongshu",
            placementType = PlacementType.KOL_SEEDING,
            objectives = listOf(CampaignObjective.CONVERSION),
            format = CreativeFormat.VIDEO,
            budget = 50_000L,
            creativeDescription = "达人种草视频"
        )
        Mockito.`when`(priorRepository.findByPlatformAndPlacementTypeAndCategory("xiaohongshu", PlacementType.KOL_SEEDING, "护肤"))
            .thenReturn(
                PlacementPrior(
                    platform = "xiaohongshu",
                    placementType = PlacementType.KOL_SEEDING,
                    category = "护肤",
                    baseAttention = 0.95,
                    baseClick = 0.30,
                    baseConversion = 0.15,
                    calibrationCount = 4
                )
            )

        val plan = planner.plan(agents, listOf(placement), "护肤")

        assertEquals(8, plan.placementSummaries.first().plannedSamples)
    }

    private fun agent(
        name: String,
        contentPreferences: List<String>,
        interests: List<String>,
        decisionSpeed: DecisionSpeed
    ): Agent {
        return Agent(
            id = name.lowercase(),
            simulationId = "sim-1",
            persona = Persona(
                name = name,
                age = 28,
                gender = "female",
                income = IncomeLevel.MEDIUM,
                cityTier = 1,
                interests = interests,
                platformBehavior = PlatformBehavior(
                    dailyUsageMinutes = 60,
                    contentPreferences = contentPreferences,
                    purchaseFrequency = PurchaseFrequency.SOMETIMES
                ),
                consumptionHabits = ConsumptionHabits(
                    priceSensitivity = SensitivityLevel.MEDIUM,
                    decisionSpeed = decisionSpeed,
                    brandLoyalty = SensitivityLevel.MEDIUM
                )
            )
        )
    }
}
