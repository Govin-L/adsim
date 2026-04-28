package com.adsim.simulation

import com.adsim.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResultAggregatorTest {

    private val aggregator = ResultAggregator()

    @Test
    fun `aggregate calculates metrics and reasons from simulated decisions`() = runBlocking {
        val agents = listOf(
            agent("Ava", 26, "female", IncomeLevel.MEDIUM, true, true, true, "符合我的需求", "我想继续了解", "价格合适"),
            agent("Bella", 29, "female", IncomeLevel.MEDIUM, true, true, false, "对我有吸引力", "品牌看起来可靠", "价格太高"),
            agent("Chris", 35, "male", IncomeLevel.HIGH, true, false, false, "偶然看了一眼", "没有兴趣点进去", "没有点击广告"),
            agent("Dylan", 45, "male", IncomeLevel.LOW, false, false, false, "我直接划过去了", "没有注意到这条广告", "没有注意到这条广告")
        )

        val results = aggregator.aggregate(
            agents = agents,
            budget = 4000L,
            placements = listOf(
                AdPlacement(
                    platform = "xiaohongshu",
                    placementType = PlacementType.INFO_FEED,
                    objectives = listOf(CampaignObjective.CONVERSION),
                    format = CreativeFormat.VIDEO,
                    budget = 4000L,
                    creativeDescription = "信息流测评视频"
                )
            )
        )

        assertEquals(4, results.totalAgents)
        assertEquals(4, results.successfulAgents)
        assertNotNull(results.simulatedMetrics)
        assertNotNull(results.estimatedMetrics)
        assertEquals(0.75, results.simulatedMetrics.attentionRate, 0.0001)
        assertEquals(2.0 / 3.0, results.simulatedMetrics.ctr, 0.0001)
        assertEquals(0.5, results.simulatedMetrics.cvr, 0.0001)
        assertEquals(0.25, results.simulatedMetrics.overallConversionRate, 0.0001)
        assertEquals(0.32, results.estimatedMetrics.estimatedCPA!!, 0.0001)
        assertEquals("没有兴趣点进去", results.dropOffReasons.attentionToClick.first().reason)
        assertEquals("价格太高", results.dropOffReasons.clickToConversion.first().reason)
        assertTrue(results.topInsights?.isNotEmpty() == true)
        assertEquals(1, results.placementResults.size)
        assertEquals(4, results.placementResults.first().totalAgents)
        assertNotNull(results.placementResults.first().simulatedMetrics)
        assertEquals(0.25, results.placementResults.first().simulatedMetrics!!.overallConversionRate, 0.0001)
    }

    @Test
    fun `aggregate highlights highest and lowest segments`() = runBlocking {
        val agents = listOf(
            agent("Ava", 26, "female", IncomeLevel.MEDIUM, true, true, true, "注意到了", "点击看看", "愿意购买"),
            agent("Bella", 29, "female", IncomeLevel.MEDIUM, true, true, false, "注意到了", "点击看看", "还是太贵"),
            agent("Chris", 35, "male", IncomeLevel.HIGH, true, false, false, "看到了", "懒得点", "没有点击广告"),
            agent("Dylan", 45, "male", IncomeLevel.LOW, false, false, false, "没有兴趣", "没有注意到这条广告", "没有注意到这条广告")
        )

        val results = aggregator.aggregate(agents, 4000L)

        val ageInsight = results.segmentInsights?.firstOrNull { it.dimension == "age" }
        assertNotNull(ageInsight)
        assertTrue(ageInsight.segments.any { it.highlight == "highest" })
        assertTrue(ageInsight.segments.any { it.highlight == "lowest" })
    }

    @Test
    fun `aggregate produces placement level breakdown for multi placement decisions`() = runBlocking {
        val placements = listOf(
            AdPlacement(
                platform = "xiaohongshu",
                placementType = PlacementType.INFO_FEED,
                objectives = listOf(CampaignObjective.CONVERSION),
                format = CreativeFormat.VIDEO,
                budget = 3000L,
                creativeDescription = "信息流视频"
            ),
            AdPlacement(
                platform = "google",
                placementType = PlacementType.SEARCH,
                objectives = listOf(CampaignObjective.CONVERSION),
                format = CreativeFormat.IMAGE_TEXT,
                budget = 3000L,
                creativeDescription = "搜索广告"
            )
        )

        val agents = listOf(
            agentWithPlacementDecisions(
                "Ava",
                placement0 = Decisions(
                    attention = StageDecision(true, "看到了", listOf("interest_match"), likelihoodBand = LikelihoodBand.HIGH, probability = 0.82, positiveFactors = listOf("interest_match")),
                    click = StageDecision(true, "点进去看看", listOf("creative_appeal"), likelihoodBand = LikelihoodBand.HIGH, probability = 0.73, positiveFactors = listOf("creative_appeal")),
                    conversion = StageDecision(true, "准备购买", listOf("price_acceptable"), likelihoodBand = LikelihoodBand.MEDIUM, probability = 0.54, positiveFactors = listOf("price_acceptable"))
                ),
                placement1 = Decisions(
                    attention = StageDecision(true, "看到了", listOf("need_match"), likelihoodBand = LikelihoodBand.HIGH, probability = 0.75, positiveFactors = listOf("need_match")),
                    click = StageDecision(false, "搜索结果不够吸引", listOf("creative_boring"), likelihoodBand = LikelihoodBand.LOW, probability = 0.18, negativeFactors = listOf("creative_boring")),
                    conversion = StageDecision(false, "没有点击广告", negativeFactors = listOf("no_click"), likelihoodBand = LikelihoodBand.VERY_LOW, probability = 0.0)
                )
            ),
            agentWithPlacementDecisions(
                "Bella",
                placement0 = Decisions(
                    attention = StageDecision(true, "看到了", listOf("interest_match"), likelihoodBand = LikelihoodBand.HIGH, probability = 0.78, positiveFactors = listOf("interest_match")),
                    click = StageDecision(false, "创意一般", listOf("creative_boring"), likelihoodBand = LikelihoodBand.LOW, probability = 0.21, negativeFactors = listOf("creative_boring")),
                    conversion = StageDecision(false, "没有点击广告", negativeFactors = listOf("no_click"), likelihoodBand = LikelihoodBand.VERY_LOW, probability = 0.0)
                ),
                placement1 = Decisions(
                    attention = trueDecision("注意到了", "interest_match"),
                    click = trueDecision("点击了", "need_match"),
                    conversion = trueDecision("搜索结果很符合需求", "price_acceptable")
                )
            )
        )

        val deliveryPlan = DeliveryPlan(
            placementSummaries = listOf(
                PlacementDeliverySummary(
                    placementIndex = 0,
                    plannedSamples = 2,
                    eligibleAgents = 2,
                    uniqueAgentsReached = 2,
                    eligibleAgentIds = listOf("ava", "bella"),
                    reachedAgentIds = listOf("ava", "bella")
                ),
                PlacementDeliverySummary(
                    placementIndex = 1,
                    plannedSamples = 2,
                    eligibleAgents = 1,
                    uniqueAgentsReached = 1,
                    eligibleAgentIds = listOf("bella"),
                    reachedAgentIds = listOf("bella")
                )
            )
        )

        val results = aggregator.aggregate(agents = agents, budget = 6000L, placements = placements, deliveryPlan = deliveryPlan)

        assertEquals(4, results.totalAgents)
        assertEquals(4, results.successfulAgents)
        assertEquals(2, results.placementResults.size)
        assertEquals(2, results.placementResults[0].totalAgents)
        assertEquals("xiaohongshu", results.placementResults[0].placement.platform)
        assertNotNull(results.placementResults[0].simulatedMetrics)
        assertEquals(0.5, results.placementResults[0].simulatedMetrics!!.ctr, 0.0001)
        assertEquals(2, results.placementResults[0].sampleQuality.plannedSamples)
        assertEquals(2, results.placementResults[0].sampleQuality.simulatedSamples)
        assertEquals("google", results.placementResults[1].placement.platform)
        assertNotNull(results.placementResults[1].simulatedMetrics)
        assertEquals(1.0, results.placementResults[1].simulatedMetrics!!.cvr, 0.0001)
        assertNotNull(results.utilization)
        assertEquals(2, results.utilization.uniqueAgentsReached)
        assertEquals(1.0, results.utilization.uniqueReachRate, 0.0001)
        assertEquals(2.0, results.utilization.averageExposuresPerReachedAgent, 0.0001)
        assertEquals(2, results.utilization.placementCoverage.size)
        assertEquals(0.5, results.utilization.searchEligibleRate, 0.0001)
        assertTrue(results.stageBlockers.isNotEmpty())
        assertEquals("creative_boring", results.stageBlockers.first().factor)
        assertTrue(results.recommendations.isNotEmpty())
    }

    private fun agent(
        name: String,
        age: Int,
        gender: String,
        income: IncomeLevel,
        attentionPassed: Boolean,
        clickPassed: Boolean,
        conversionPassed: Boolean,
        attentionReason: String,
        clickReason: String,
        conversionReason: String
    ): Agent {
        return Agent(
            simulationId = "sim-1",
            persona = Persona(
                name = name,
                age = age,
                gender = gender,
                income = income,
                cityTier = 1,
                interests = listOf("护肤"),
                platformBehavior = PlatformBehavior(
                    dailyUsageMinutes = 60,
                    contentPreferences = listOf("video", "review"),
                    purchaseFrequency = PurchaseFrequency.SOMETIMES
                ),
                consumptionHabits = ConsumptionHabits(
                    priceSensitivity = SensitivityLevel.MEDIUM,
                    decisionSpeed = DecisionSpeed.MODERATE,
                    brandLoyalty = SensitivityLevel.MEDIUM
                )
            ),
            decisions = Decisions(
                attention = StageDecision(attentionPassed, attentionReason, if (attentionPassed) listOf("interest_match") else listOf("no_interest")),
                click = StageDecision(clickPassed, clickReason, if (clickPassed) listOf("creative_appeal") else listOf("no_interest")),
                conversion = StageDecision(conversionPassed, conversionReason, if (conversionPassed) listOf("price_acceptable") else listOf("price_too_high"))
            )
        )
    }

    private fun agentWithPlacementDecisions(
        name: String,
        placement0: Decisions,
        placement1: Decisions
    ): Agent {
        return Agent(
            id = name.lowercase(),
            simulationId = "sim-2",
            persona = Persona(
                name = name,
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
            ),
            decisions = placement0,
            placementOutcomes = listOf(
                PlacementOutcome(
                    placementIndex = 0,
                    platform = "xiaohongshu",
                    placementType = PlacementType.INFO_FEED,
                    exposureEvent = ExposureEvent(
                        agentId = name.lowercase(),
                        placementIndex = 0,
                        sequence = 0,
                        deliveryContext = DeliveryContext(source = "test_feed", frequency = 1)
                    ),
                    attention = placement0.attention,
                    click = placement0.click,
                    conversion = placement0.conversion
                ),
                PlacementOutcome(
                    placementIndex = 1,
                    platform = "google",
                    placementType = PlacementType.SEARCH,
                    exposureEvent = ExposureEvent(
                        agentId = name.lowercase(),
                        placementIndex = 1,
                        sequence = 1,
                        deliveryContext = DeliveryContext(source = "test_search", frequency = 1, intentLevel = 0.8)
                    ),
                    attention = placement1.attention,
                    click = placement1.click,
                    conversion = placement1.conversion
                )
            ),
            placementDecisions = listOf(
                PlacementDecisions(0, "xiaohongshu", PlacementType.INFO_FEED, placement0),
                PlacementDecisions(1, "google", PlacementType.SEARCH, placement1)
            )
        )
    }

    private fun trueDecision(reason: String, factor: String = "interest_match"): StageDecision =
        StageDecision(
            passed = true,
            reasoning = reason,
            factors = listOf(factor),
            likelihoodBand = LikelihoodBand.HIGH,
            probability = 1.0,
            positiveFactors = listOf(factor)
        )
}
