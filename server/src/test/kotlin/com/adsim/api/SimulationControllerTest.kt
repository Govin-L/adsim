package com.adsim.api

import com.adsim.agent.PlanParser
import com.adsim.api.dto.CalibrationPlacementRequest
import com.adsim.api.dto.ParsePlanRequest
import com.adsim.api.dto.UpdateCalibrationRequest
import com.adsim.config.LlmRequestConfig
import com.adsim.model.*
import com.adsim.simulation.SimulationService
import com.adsim.support.FakeChatModel
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant
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

    @Test
    fun `verify llm returns error message when model call fails`() {
        val simulationService = Mockito.mock(SimulationService::class.java)
        val planParser = Mockito.mock(PlanParser::class.java)
        val failingModel = object : ChatModel {
            override fun doChat(chatRequest: ChatRequest): ChatResponse {
                throw IllegalStateException("404 page not found")
            }
        }
        val llmRequestConfig = LlmRequestConfig(failingModel)

        val controller = SimulationController(simulationService, planParser, llmRequestConfig)
        val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

        mockMvc.perform(
            post("/api/simulations/verify-llm")
                .header("X-LLM-Base-Url", "http://127.0.0.1:8317")
                .header("X-LLM-Model", "gpt-5.4")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("404 page not found"))
    }

    @Test
    fun `calibration endpoint returns updated simulation`() {
        val simulationService = Mockito.mock(SimulationService::class.java)
        val planParser = Mockito.mock(PlanParser::class.java)
        val chatModel = FakeChatModel(emptyList())
        val llmRequestConfig = LlmRequestConfig(chatModel)
        val simulation = completedSimulation().copy(
            results = completedSimulation().results?.copy(
                calibration = CalibrationResult(
                    placements = listOf(
                        CalibrationPlacementResult(
                            placementIndex = 0,
                            actualMetrics = ActualPerformanceMetrics(ctr = 0.08, cvr = 0.03, cpa = 120.0),
                            simulatedMetrics = SimulatedFunnelMetrics(0.6, 0.05, 0.02, 0.01),
                            prior = PlacementPriorSnapshot(
                                baseAttention = 0.6,
                                baseClick = 0.08,
                                baseConversion = 0.03,
                                calibrationCount = 1,
                                converged = false
                            ),
                            deltas = CalibrationDelta(ctrDelta = 0.03, cvrDelta = 0.01, cpaDelta = -30.0)
                        )
                    ),
                    summary = CalibrationSummary(
                        coverage = 1,
                        averageCtrDelta = 0.03,
                        averageCvrDelta = 0.01,
                        averageCpaDelta = -30.0
                    )
                )
            )
        )

        val calibrationRequest = UpdateCalibrationRequest(
            placements = listOf(
                CalibrationPlacementRequest(
                    placementIndex = 0,
                    ctr = 0.08,
                    cvr = 0.03,
                    cpa = 120.0
                )
            )
        )

        Mockito.`when`(simulationService.updateCalibration("sim-1", calibrationRequest))
            .thenReturn(simulation)

        val controller = SimulationController(simulationService, planParser, llmRequestConfig)
        val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

        mockMvc.perform(
            post("/api/simulations/sim-1/calibration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(calibrationRequest)
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.results.calibration.summary.coverage").value(1))
            .andExpect(jsonPath("$.results.calibration.placements[0].deltas.ctrDelta").value(0.03))
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

    private fun completedSimulation(): Simulation {
        val placement = basePlan().adPlacements.first()
        return Simulation(
            id = "sim-1",
            status = SimulationStatus.COMPLETED,
            input = basePlan(),
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
