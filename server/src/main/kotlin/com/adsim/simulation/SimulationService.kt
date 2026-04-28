package com.adsim.simulation

import com.adsim.agent.AgentGenerator
import com.adsim.api.dto.CalibrationPlacementRequest
import com.adsim.api.dto.CreateSimulationRequest
import com.adsim.api.dto.InterviewRequest
import com.adsim.api.dto.InterviewResponse
import com.adsim.api.dto.UpdateCalibrationRequest
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
    private val deliveryPlanner: DeliveryPlanner,
    private val resultAggregator: ResultAggregator,
    private val simulationQualityEvaluator: SimulationQualityEvaluator,
    private val interviewService: InterviewService,
    private val priorCalibrationService: PriorCalibrationService,
    private val config: com.adsim.config.SimulationConfig
) {
    private val logger = LoggerFactory.getLogger(SimulationService::class.java)
    private val sseEmitters = ConcurrentHashMap<String, MutableList<SseEmitter>>()

    @jakarta.annotation.PostConstruct
    fun cleanupOrphanedSimulations() {
        val running = listOf(SimulationStatus.PENDING, SimulationStatus.GENERATING, SimulationStatus.SIMULATING, SimulationStatus.AGGREGATING)
        val orphaned = simulationRepository.findByStatusIn(running)
        if (orphaned.isNotEmpty()) {
            logger.info("Cleaning up {} orphaned simulations", orphaned.size)
            orphaned.forEach { sim ->
                simulationRepository.save(sim.copy(status = SimulationStatus.FAILED, errorMessage = "Server restarted during simulation"))
            }
        }
    }

    fun create(request: CreateSimulationRequest, chatModel: dev.langchain4j.model.chat.ChatModel, concurrency: Int? = null): Simulation {
        val simulation = simulationRepository.save(
            Simulation(input = request.input, rawInput = request.rawInput)
        )
        val simulationId = simulation.id!!
        logger.info("Simulation created, id: {}", simulationId)

        CoroutineScope(Dispatchers.IO).launch {
            runSimulation(simulationId, request.agentCount, chatModel, concurrency)
        }

        return simulation
    }

    fun list(): List<Simulation> {
        return simulationRepository.findAllByOrderByCreatedAtDesc()
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

    fun interview(simulationId: String, agentId: String, request: InterviewRequest, chatModel: dev.langchain4j.model.chat.ChatModel): InterviewResponse? {
        val simulation = get(simulationId) ?: return null
        val agent = getAgent(simulationId, agentId) ?: return null
        return interviewService.chat(simulation, agent, request, chatModel)
    }

    fun subscribeProgress(simulationId: String): SseEmitter {
        val emitter = SseEmitter(600_000L)
        sseEmitters.getOrPut(simulationId) { mutableListOf() }.add(emitter)
        emitter.onCompletion { sseEmitters[simulationId]?.remove(emitter) }
        emitter.onTimeout { sseEmitters[simulationId]?.remove(emitter) }
        return emitter
    }

    fun updateCalibration(simulationId: String, request: UpdateCalibrationRequest): Simulation? {
        val simulation = get(simulationId) ?: return null
        val results = simulation.results ?: return simulation
        if (simulation.status != SimulationStatus.COMPLETED && simulation.status != SimulationStatus.COMPLETED_WITH_WARNINGS) {
            return simulation
        }

        val calibration = buildCalibration(simulation, results, request.placements)
        syncPlacementPriors(simulation, results, calibration)
        val updated = simulation.copy(results = results.copy(calibration = calibration))
        return simulationRepository.save(updated)
    }

    private suspend fun runSimulation(simulationId: String, agentCount: Int, chatModel: dev.langchain4j.model.chat.ChatModel, concurrency: Int? = null) {
        try {
            val simulation = get(simulationId) ?: return

            // Phase 1: Generate agents
            val startTime = System.currentTimeMillis()
            updateStatus(simulationId, SimulationStatus.GENERATING)
            val effectiveConcurrency = (concurrency ?: config.maxConcurrency).coerceAtLeast(1)
            logger.info("[{}] Phase 1: Generating {} agents, concurrency: {}", simulationId, agentCount, effectiveConcurrency)
            val agents = agentGenerator.generate(simulation.input, agentCount, chatModel, effectiveConcurrency) { generated, total ->
                emitProgress(simulationId, total, generated, "generating")
            }
            val savedAgents = agentRepository.saveAll(
                agents.map { it.copy(simulationId = simulationId) }
            )
            logger.info("[{}] Phase 1 done: {} agents generated in {}s", simulationId, savedAgents.size, (System.currentTimeMillis() - startTime) / 1000)
            if (savedAgents.isEmpty()) {
                markFailed(simulationId, "No agents were generated successfully.")
                return
            }
            val deliveryPlan = deliveryPlanner.plan(
                savedAgents,
                simulation.input.adPlacements,
                simulation.input.product.category
            )
            val plannedSimulationSamples = deliveryPlan.plannedSamples
            updateProgress(simulationId, plannedSimulationSamples, 0)

            // Phase 2: Run simulation
            val phase2Start = System.currentTimeMillis()
            updateStatus(simulationId, SimulationStatus.SIMULATING)
            logger.info(
                "[{}] Phase 2: Simulating {} agents across {} planned exposures ({} placements), concurrency: {}",
                simulationId,
                savedAgents.size,
                plannedSimulationSamples,
                simulation.input.adPlacements.size,
                effectiveConcurrency
            )
            simulationEngine.run(
                input = simulation.input,
                agents = savedAgents,
                chatModel = chatModel,
                concurrency = effectiveConcurrency,
                exposureEvents = deliveryPlan.exposureEvents
            ) { completed ->
                updateProgress(simulationId, plannedSimulationSamples, completed)
            }
            logger.info("[{}] Phase 2 done in {}s", simulationId, (System.currentTimeMillis() - phase2Start) / 1000)

            // Phase 3: Aggregate results
            updateStatus(simulationId, SimulationStatus.AGGREGATING)
            val completedAgents = agentRepository.findBySimulationId(simulationId)
            val withDecisions = completedAgents.count { it.decisions != null }
            logger.info("[{}] Phase 3: Aggregating, {}/{} agents have decisions", simulationId, withDecisions, completedAgents.size)
            val aggregatedResults = resultAggregator.aggregate(
                agents = completedAgents,
                budget = simulation.input.totalBudget,
                placements = simulation.input.adPlacements,
                chatModel = chatModel,
                deliveryPlan = deliveryPlan
            )
            val qualityAssessment = simulationQualityEvaluator.evaluate(
                requestedAgents = agentCount,
                generatedAgents = savedAgents.size,
                agents = completedAgents,
                plannedSamples = plannedSimulationSamples,
                minSuccessRate = config.minSuccessRate,
                utilization = aggregatedResults.utilization
            )
            if (qualityAssessment.status == SimulationStatus.FAILED) {
                markFailed(simulationId, qualityAssessment.errorMessage ?: "Simulation quality check failed.")
                return
            }
            val results = aggregatedResults.copy(quality = qualityAssessment.quality)

            // Complete
            simulationRepository.save(
                simulation.copy(
                    status = qualityAssessment.status,
                    results = results,
                    completedAt = Instant.now()
                )
            )
            emitProgress(simulationId, plannedSimulationSamples, plannedSimulationSamples, "completed")
            logger.info("Simulation completed, id: {}", simulationId)
        } catch (e: Exception) {
            logger.error("Simulation failed, id: {}", simulationId, e)
            val errorMsg = formatErrorMessage(e)
            markFailed(simulationId, errorMsg)
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

    private fun markFailed(simulationId: String, errorMessage: String) {
        simulationRepository.findById(simulationId).ifPresent {
            simulationRepository.save(it.copy(status = SimulationStatus.FAILED, errorMessage = errorMessage))
        }
        emitProgress(simulationId, 0, 0, "failed", errorMessage)
    }

    private fun buildCalibration(
        simulation: Simulation,
        results: SimulationResults,
        requests: List<CalibrationPlacementRequest>
    ): CalibrationResult {
        val placementLookup = results.placementResults.associateBy { it.placementIndex }
        val calibratedPlacements = requests.mapNotNull { request ->
            val simulated = placementLookup[request.placementIndex] ?: return@mapNotNull null
            val actualMetrics = ActualPerformanceMetrics(
                ctr = request.ctr,
                cvr = request.cvr,
                cpa = request.cpa,
                impressions = request.impressions,
                conversions = request.conversions
            )
            val simulatedFunnelMetrics = simulated.simulatedMetrics ?: SimulatedFunnelMetrics(
                attentionRate = simulated.metrics.attentionRate,
                ctr = simulated.metrics.ctr,
                cvr = simulated.metrics.cvr,
                overallConversionRate = simulated.metrics.overallConversionRate
            )
            val estimatedBusinessMetrics = simulated.estimatedMetrics ?: EstimatedBusinessMetrics(
                estimatedImpressions = 0.0,
                estimatedViewers = 0.0,
                estimatedConversions = 0.0,
                estimatedCPA = simulated.metrics.estimatedCPA
            )
            CalibrationPlacementResult(
                placementIndex = request.placementIndex,
                actualMetrics = actualMetrics,
                simulatedMetrics = simulatedFunnelMetrics,
                estimatedMetrics = estimatedBusinessMetrics,
                prior = priorCalibrationService.loadPrior(
                    simulated.placement.platform,
                    simulated.placement.placementType,
                    simulation.input.product.category
                )?.let(priorCalibrationService::toSnapshot),
                deltas = CalibrationDelta(
                    ctrDelta = delta(actualMetrics.ctr, simulatedFunnelMetrics.ctr),
                    cvrDelta = delta(actualMetrics.cvr, simulatedFunnelMetrics.cvr),
                    cpaDelta = delta(actualMetrics.cpa, estimatedBusinessMetrics.estimatedCPA)
                )
            )
        }

        return CalibrationResult(
            placements = calibratedPlacements,
            summary = CalibrationSummary(
                coverage = calibratedPlacements.size,
                averageCtrDelta = averageOf(calibratedPlacements.mapNotNull { it.deltas.ctrDelta }),
                averageCvrDelta = averageOf(calibratedPlacements.mapNotNull { it.deltas.cvrDelta }),
                averageCpaDelta = averageOf(calibratedPlacements.mapNotNull { it.deltas.cpaDelta })
            )
        )
    }

    private fun syncPlacementPriors(
        simulation: Simulation,
        results: SimulationResults,
        calibration: CalibrationResult
    ) {
        val placementsByIndex = simulation.input.adPlacements.mapIndexed { index, placement ->
            index to placement
        }.toMap()
        val resultByIndex = results.placementResults.associateBy { it.placementIndex }

        calibration.placements.forEach { placementCalibration ->
            val placement = placementsByIndex[placementCalibration.placementIndex] ?: return@forEach
            val simulated = resultByIndex[placementCalibration.placementIndex]
            val simulatedMetrics = simulated?.simulatedMetrics ?: SimulatedFunnelMetrics(
                attentionRate = simulated?.metrics?.attentionRate ?: 0.0,
                ctr = simulated?.metrics?.ctr ?: 0.0,
                cvr = simulated?.metrics?.cvr ?: 0.0,
                overallConversionRate = simulated?.metrics?.overallConversionRate ?: 0.0
            )
            priorCalibrationService.updatePrior(
                placement = placement,
                product = simulation.input.product,
                simulatedAttention = simulatedMetrics.attentionRate,
                simulatedCtr = simulatedMetrics.ctr,
                simulatedCvr = simulatedMetrics.cvr,
                actualCtr = placementCalibration.actualMetrics.ctr,
                actualCvr = placementCalibration.actualMetrics.cvr
            )
        }
    }

    private fun delta(actual: Double?, simulated: Double?): Double? {
        if (actual == null || simulated == null) return null
        return actual - simulated
    }

    private fun averageOf(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        return values.average()
    }

    private fun emitProgress(simulationId: String, total: Int, completed: Int, status: String, error: String? = null) {
        val payload = mutableMapOf<String, Any>("total" to total, "completed" to completed, "status" to status)
        if (error != null) payload["error"] = error
        val data = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(payload)
        sseEmitters[simulationId]?.forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().data(data))
                if (status == "completed" || status == "failed") emitter.complete()
            } catch (_: Exception) {
                // client disconnected
            }
        }
    }

    private fun formatErrorMessage(e: Exception): String {
        val className = e.javaClass.simpleName
        val raw = e.message ?: return "Unknown error"

        return when {
            className.contains("Authentication") || raw.contains("API key", ignoreCase = true) ->
                "LLM API Key is missing or invalid. Please configure it in the LLM Settings."
            className.contains("Timeout") || raw.contains("timed out", ignoreCase = true) ->
                "LLM request timed out. Please check your network or try again."
            raw.contains("too many concurrent", ignoreCase = true) ->
                "Too many concurrent requests. Please reduce the concurrency in LLM Settings."
            raw.contains("rate limit", ignoreCase = true) || raw.contains("429") ->
                "LLM API rate limit exceeded. Please wait and try again."
            raw.contains("insufficient_quota", ignoreCase = true) || raw.contains("billing", ignoreCase = true) ->
                "LLM API quota exceeded. Please check your account balance."
            raw.contains("model_not_found", ignoreCase = true) || raw.contains("does not exist", ignoreCase = true) ->
                "The configured LLM model was not found. Please check the model name in LLM Settings."
            raw.contains("connection refused", ignoreCase = true) || raw.contains("connect", ignoreCase = true) ->
                "Cannot connect to LLM API. Please check the Base URL in LLM Settings."
            else -> raw.take(200)
        }
    }
}
