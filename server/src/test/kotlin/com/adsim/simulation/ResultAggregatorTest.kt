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
        assertEquals(0.75, results.metrics.attentionRate, 0.0001)
        assertEquals(2.0 / 3.0, results.metrics.ctr, 0.0001)
        assertEquals(0.5, results.metrics.cvr, 0.0001)
        assertEquals(0.25, results.metrics.overallConversionRate, 0.0001)
        assertEquals(0.32, results.metrics.estimatedCPA!!, 0.0001)
        assertEquals("没有兴趣点进去", results.dropOffReasons.attentionToClick.first().reason)
        assertEquals("价格太高", results.dropOffReasons.clickToConversion.first().reason)
        assertTrue(results.topInsights?.isNotEmpty() == true)
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
}
