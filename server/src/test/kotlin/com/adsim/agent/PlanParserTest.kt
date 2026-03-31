package com.adsim.agent

import com.adsim.model.*
import com.adsim.support.FakeChatModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanParserTest {

    private val parser = PlanParser()

    @Test
    fun `compile full plan extracts market context fields`() {
        val model = FakeChatModel(listOf(
            """
            {
              "product": {
                "brandName": "珀莱雅",
                "name": "双抗精华",
                "price": 299,
                "category": "护肤",
                "sellingPoints": "抗老、修护",
                "productStage": "BESTSELLER",
                "description": "爆款精华"
              },
              "adPlacements": [
                {
                  "platform": "xiaohongshu",
                  "placementType": "INFO_FEED",
                  "objectives": ["SEEDING", "CONVERSION"],
                  "format": "VIDEO",
                  "budget": 200000,
                  "creativeDescription": "达人测评视频"
                }
              ],
              "totalBudget": 200000,
              "competitors": [
                { "brandName": "OLAY", "price": 259, "positioning": "抗老精华" },
                { "brandName": "雅诗兰黛", "price": 680, "positioning": "高端抗老" }
              ],
              "brandAwareness": "WELL_KNOWN",
              "campaignGoal": "ACQUISITION",
              "targetAudience": {
                "ageRange": [25, 35],
                "gender": "female",
                "region": "一二线城市",
                "interests": ["护肤", "抗老"]
              }
            }
            """.trimIndent()
        ))

        val result = parser.compile("任意自然语言输入", null, model)

        assertEquals("珀莱雅", result.mergedPlan.product.brandName)
        assertEquals(299.0, result.mergedPlan.product.price)
        assertEquals(2, result.mergedPlan.competitors.size)
        assertEquals(BrandAwareness.WELL_KNOWN, result.mergedPlan.brandAwareness)
        assertEquals(CampaignGoal.ACQUISITION, result.mergedPlan.campaignGoal)
        assertTrue(result.changedFields.isEmpty())
    }

    @Test
    fun `compile patch preserves unspecified fields and returns changed fields`() {
        val currentPlan = basePlan()
        val model = FakeChatModel(listOf(
            """
            {
              "changedFields": ["product.price", "targetAudience.ageRange"],
              "warnings": [],
              "patch": {
                "product": {
                  "price": 199
                },
                "targetAudience": {
                  "ageRange": [23, 32]
                }
              }
            }
            """.trimIndent()
        ))

        val result = parser.compile("把价格改成 199，年龄改成 23-32", currentPlan, model)

        assertEquals(199.0, result.mergedPlan.product.price)
        assertEquals(listOf(23, 32), result.mergedPlan.targetAudience.ageRange)
        assertEquals(currentPlan.competitors, result.mergedPlan.competitors)
        assertEquals(currentPlan.brandAwareness, result.mergedPlan.brandAwareness)
        assertEquals(currentPlan.campaignGoal, result.mergedPlan.campaignGoal)
        assertTrue(result.changedFields.contains("product.price"))
        assertTrue(result.changedFields.contains("targetAudience.ageRange"))
    }

    @Test
    fun `compile patch rebalances placement budgets when only total budget changes`() {
        val currentPlan = basePlan()
        val model = FakeChatModel(listOf(
            """
            {
              "changedFields": ["totalBudget"],
              "warnings": [],
              "patch": {
                "totalBudget": 600000
              }
            }
            """.trimIndent()
        ))

        val result = parser.compile("总预算改成 60 万", currentPlan, model)

        assertEquals(600000L, result.mergedPlan.totalBudget)
        assertEquals(listOf(400000L, 200000L), result.mergedPlan.adPlacements.map { it.budget })
        assertTrue(result.warnings.any { it.contains("重新分配预算") })
    }

    private fun basePlan(): SimulationInput {
        return SimulationInput(
            product = Product(
                brandName = "珀莱雅",
                name = "双抗精华",
                price = 299.0,
                category = "护肤",
                sellingPoints = "抗老、修护",
                productStage = ProductStage.ESTABLISHED,
                description = "适合轻熟龄人群"
            ),
            adPlacements = listOf(
                AdPlacement(
                    platform = "xiaohongshu",
                    placementType = PlacementType.INFO_FEED,
                    objectives = listOf(CampaignObjective.SEEDING),
                    format = CreativeFormat.VIDEO,
                    budget = 200000L,
                    creativeDescription = "信息流视频"
                ),
                AdPlacement(
                    platform = "xiaohongshu",
                    placementType = PlacementType.SEARCH,
                    objectives = listOf(CampaignObjective.CONVERSION),
                    format = CreativeFormat.IMAGE_TEXT,
                    budget = 100000L,
                    creativeDescription = "搜索关键词卡"
                )
            ),
            totalBudget = 300000L,
            targetAudience = TargetAudience(
                ageRange = listOf(25, 35),
                gender = "female",
                region = "一二线城市",
                interests = listOf("护肤", "抗老")
            ),
            competitors = listOf(
                CompetitorInfo("OLAY", 259.0, "抗老精华")
            ),
            brandAwareness = BrandAwareness.EMERGING,
            campaignGoal = CampaignGoal.ACQUISITION
        )
    }
}
