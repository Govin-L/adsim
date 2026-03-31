package com.adsim.api

import com.adsim.agent.PlanParser
import com.adsim.api.dto.ParsePlanRequest
import com.adsim.config.LlmRequestConfig
import com.adsim.model.*
import com.adsim.simulation.SimulationService
import com.adsim.support.FakeChatModel
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SimulationControllerTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `parse endpoint returns merged plan with changed fields and warnings`() {
        val simulationService = Mockito.mock(SimulationService::class.java)
        val planParser = Mockito.mock(PlanParser::class.java)
        val chatModel = FakeChatModel(emptyList())
        val llmRequestConfig = LlmRequestConfig(chatModel)

        val currentPlan = basePlan()
        val mergedPlan = currentPlan.copy(
            product = currentPlan.product.copy(price = 199.0)
        )

        Mockito.`when`(planParser.compile("把价格改成 199", currentPlan, chatModel))
            .thenReturn(
                PlanParser.PlanCompileResult(
                    mergedPlan = mergedPlan,
                    changedFields = listOf("product.price"),
                    warnings = listOf("已按现有 placement 占比重新分配预算。")
                )
            )
        Mockito.`when`(planParser.findMissingFields(mergedPlan)).thenReturn(emptyList())

        val controller = SimulationController(simulationService, planParser, llmRequestConfig)
        val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

        mockMvc.perform(
            post("/api/simulations/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ParsePlanRequest("把价格改成 199", currentPlan)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mergedPlan.product.price").value(199.0))
            .andExpect(jsonPath("$.changedFields[0]").value("product.price"))
            .andExpect(jsonPath("$.warnings[0]").value("已按现有 placement 占比重新分配预算。"))
            .andExpect(jsonPath("$.missingFields").isArray)
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
                    objectives = listOf(CampaignObjective.CONVERSION),
                    format = CreativeFormat.VIDEO,
                    budget = 300000L,
                    creativeDescription = "信息流视频"
                )
            ),
            totalBudget = 300000L,
            targetAudience = TargetAudience(
                ageRange = listOf(25, 35),
                gender = "female",
                region = "一二线城市",
                interests = listOf("护肤", "抗老")
            ),
            competitors = listOf(CompetitorInfo("OLAY", 259.0, "抗老精华")),
            brandAwareness = BrandAwareness.EMERGING,
            campaignGoal = CampaignGoal.ACQUISITION
        )
    }
}
