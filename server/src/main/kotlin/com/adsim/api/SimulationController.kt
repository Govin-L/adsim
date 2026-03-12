package com.adsim.api

import com.adsim.agent.PlanParser
import com.adsim.api.dto.*
import com.adsim.config.LlmRequestConfig
import com.adsim.model.Agent
import com.adsim.model.Simulation
import com.adsim.simulation.SimulationService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/simulations")
class SimulationController(
    private val simulationService: SimulationService,
    private val planParser: PlanParser,
    private val llmRequestConfig: LlmRequestConfig
) {

    @PostMapping("/verify-llm")
    fun verifyLlm(httpRequest: HttpServletRequest): Map<String, Any> {
        return try {
            val model = llmRequestConfig.resolve(httpRequest)
            val reply = model.chat("Reply with exactly: ok")
            mapOf("success" to true, "reply" to reply)
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
        }
    }

    @PostMapping("/parse")
    fun parsePlan(@Valid @RequestBody request: ParsePlanRequest, httpRequest: HttpServletRequest): ParsePlanResponse {
        val model = llmRequestConfig.resolve(httpRequest)
        val input = planParser.parse(request.content, model)
        val missing = planParser.findMissingFields(input)
        return ParsePlanResponse(input, missing)
    }

    @PostMapping
    fun create(@RequestBody request: CreateSimulationRequest, httpRequest: HttpServletRequest): Simulation {
        val model = llmRequestConfig.resolve(httpRequest)
        val concurrency = httpRequest.getHeader("X-LLM-Concurrency")?.toIntOrNull()
        return simulationService.create(request, model, concurrency)
    }

    @GetMapping
    fun list(): List<Simulation> {
        return simulationService.list()
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Simulation> {
        return simulationService.get(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/{id}/agents")
    fun listAgents(@PathVariable id: String): List<Agent> {
        return simulationService.getAgents(id)
    }

    @GetMapping("/{id}/agents/{agentId}")
    fun getAgent(@PathVariable id: String, @PathVariable agentId: String): ResponseEntity<Agent> {
        return simulationService.getAgent(id, agentId)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @PostMapping("/{id}/agents/{agentId}/interview")
    fun interview(
        @PathVariable id: String,
        @PathVariable agentId: String,
        @Valid @RequestBody request: InterviewRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<InterviewResponse> {
        val model = llmRequestConfig.resolve(httpRequest)
        return simulationService.interview(id, agentId, request, model)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/{id}/progress", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun progress(@PathVariable id: String): SseEmitter {
        return simulationService.subscribeProgress(id)
    }
}
