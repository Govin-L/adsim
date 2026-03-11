package com.adsim.simulation

import com.adsim.agent.AgentGenerator
import com.adsim.api.dto.CreateSimulationRequest
import com.adsim.api.dto.InterviewRequest
import com.adsim.api.dto.InterviewResponse
import com.adsim.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class SimulationService(
    private val simulationRepository: SimulationRepository,
    private val agentRepository: AgentRepository,
    private val agentGenerator: AgentGenerator,
    private val simulationEngine: SimulationEngine,
    private val resultAggregator: ResultAggregator,
    private val interviewService: InterviewService
) {
    private val logger = LoggerFactory.getLogger(SimulationService::class.java)
    private val sseEmitters = ConcurrentHashMap<String, MutableList<SseEmitter>>()

    fun create(request: CreateSimulationRequest): Simulation {
        val simulation = simulationRepository.save(
            Simulation(input = request.toInput())
        )
        val simulationId = simulation.id!!
        logger.info("Simulation created, id: {}", simulationId)

        CoroutineScope(Dispatchers.IO).launch {
            runSimulation(simulationId, request.agentCount)
        }

        return simulation
    }

    fun get(id: String): Simulation? {
        return simulationRepository.findById(id).orElse(null)
    }

    fun getAgents(simulationId: String): List<Agent> {
        return agentRepository.findBySimulationId(simulationId)
    }

    fun getAgent(simulationId: String, agentId: String): Agent? {
        val agent = agentRepository.findById(agentId).orElse(null)
        return agent?.takeIf { it.simulationId == simulationId }
    }

    fun interview(simulationId: String, agentId: String, request: InterviewRequest): InterviewResponse? {
        val simulation = get(simulationId) ?: return null
        val agent = getAgent(simulationId, agentId) ?: return null
        return interviewService.chat(simulation, agent, request)
    }

    fun subscribeProgress(simulationId: String): SseEmitter {
        val emitter = SseEmitter(300_000L)
        sseEmitters.getOrPut(simulationId) { mutableListOf() }.add(emitter)
        emitter.onCompletion { sseEmitters[simulationId]?.remove(emitter) }
        emitter.onTimeout { sseEmitters[simulationId]?.remove(emitter) }
        return emitter
    }

    private suspend fun runSimulation(simulationId: String, agentCount: Int) {
        try {
            val simulation = get(simulationId) ?: return

            // Phase 1: Generate agents
            updateStatus(simulationId, SimulationStatus.GENERATING)
            val agents = agentGenerator.generate(simulation.input, agentCount)
            val savedAgents = agentRepository.saveAll(
                agents.map { it.copy(simulationId = simulationId) }
            )
            updateProgress(simulationId, agentCount, 0)

            // Phase 2: Run simulation
            updateStatus(simulationId, SimulationStatus.SIMULATING)
            simulationEngine.run(simulation.input, savedAgents) { completed ->
                updateProgress(simulationId, agentCount, completed)
            }

            // Phase 3: Aggregate results
            updateStatus(simulationId, SimulationStatus.AGGREGATING)
            val completedAgents = agentRepository.findBySimulationId(simulationId)
            val results = resultAggregator.aggregate(completedAgents, simulation.input.budget)

            // Complete
            simulationRepository.save(
                simulation.copy(
                    status = SimulationStatus.COMPLETED,
                    results = results,
                    completedAt = Instant.now()
                )
            )
            emitProgress(simulationId, agentCount, agentCount, "completed")
            logger.info("Simulation completed, id: {}", simulationId)
        } catch (e: Exception) {
            logger.error("Simulation failed, id: {}", simulationId, e)
            updateStatus(simulationId, SimulationStatus.FAILED)
            emitProgress(simulationId, 0, 0, "failed")
        }
    }

    private fun updateStatus(simulationId: String, status: SimulationStatus) {
        simulationRepository.findById(simulationId).ifPresent {
            simulationRepository.save(it.copy(status = status))
        }
    }

    private fun updateProgress(simulationId: String, total: Int, completed: Int) {
        simulationRepository.findById(simulationId).ifPresent {
            simulationRepository.save(it.copy(progress = Progress(total, completed)))
        }
        emitProgress(simulationId, total, completed, "running")
    }

    private fun emitProgress(simulationId: String, total: Int, completed: Int, status: String) {
        val data = """{"total":$total,"completed":$completed,"status":"$status"}"""
        sseEmitters[simulationId]?.forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().data(data))
                if (status == "completed" || status == "failed") emitter.complete()
            } catch (_: Exception) {
                // client disconnected
            }
        }
    }
}
