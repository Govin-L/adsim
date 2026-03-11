package com.adsim.api

import com.adsim.api.dto.CreateSimulationRequest
import com.adsim.api.dto.InterviewRequest
import com.adsim.api.dto.InterviewResponse
import com.adsim.model.Agent
import com.adsim.model.Simulation
import com.adsim.simulation.SimulationService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/simulations")
class SimulationController(
    private val simulationService: SimulationService
) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateSimulationRequest): Simulation {
        return simulationService.create(request)
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
        @Valid @RequestBody request: InterviewRequest
    ): ResponseEntity<InterviewResponse> {
        return simulationService.interview(id, agentId, request)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/{id}/progress", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun progress(@PathVariable id: String): SseEmitter {
        return simulationService.subscribeProgress(id)
    }
}
