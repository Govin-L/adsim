package com.adsim.simulation

import com.adsim.model.Agent
import com.adsim.model.AgentUtilization
import com.adsim.model.ReasonabilityWarning
import com.adsim.model.SimulationQuality
import com.adsim.model.SimulationStatus
import org.springframework.stereotype.Component

@Component
class SimulationQualityEvaluator {

    fun evaluate(
        requestedAgents: Int,
        generatedAgents: Int,
        agents: List<Agent>,
        plannedSamples: Int,
        minSuccessRate: Double,
        utilization: AgentUtilization? = null
    ): QualityAssessment {
        val simulatedSamples = agents.sumOf { agent ->
            when {
                agent.placementOutcomes.isNotEmpty() -> agent.placementOutcomes.size
                agent.placementDecisions.isNotEmpty() -> agent.placementDecisions.size
                agent.decisions != null -> 1
                else -> 0
            }
        }
        val generationSuccessRate = if (requestedAgents > 0) generatedAgents.toDouble() / requestedAgents else 0.0
        val sampleSuccessRate = if (plannedSamples > 0) simulatedSamples.toDouble() / plannedSamples else 0.0

        val failedStages = buildList {
            if (generatedAgents < requestedAgents) add("generation")
            if (simulatedSamples < plannedSamples) add("simulation")
        }
        val warningCodes = buildList {
            if (generationSuccessRate < minSuccessRate) add("low_generation_success_rate")
            if (sampleSuccessRate < minSuccessRate) add("low_simulation_success_rate")
        }
        val reasonabilityWarnings = buildReasonabilityWarnings(agents, plannedSamples, utilization)

        val quality = SimulationQuality(
            requestedAgents = requestedAgents,
            generatedAgents = generatedAgents,
            generationSuccessRate = generationSuccessRate,
            plannedSamples = plannedSamples,
            simulatedSamples = simulatedSamples,
            sampleSuccessRate = sampleSuccessRate,
            failedStages = failedStages,
            warningCodes = warningCodes,
            reasonabilityWarnings = reasonabilityWarnings
        )

        return when {
            generatedAgents == 0 -> QualityAssessment(
                status = SimulationStatus.FAILED,
                quality = quality,
                errorMessage = "No agents were generated successfully."
            )
            simulatedSamples == 0 -> QualityAssessment(
                status = SimulationStatus.FAILED,
                quality = quality,
                errorMessage = "No placement simulations completed successfully."
            )
            warningCodes.isNotEmpty() || reasonabilityWarnings.isNotEmpty() -> QualityAssessment(
                status = SimulationStatus.COMPLETED_WITH_WARNINGS,
                quality = quality
            )
            else -> QualityAssessment(
                status = SimulationStatus.COMPLETED,
                quality = quality
            )
        }
    }

    private fun buildReasonabilityWarnings(
        agents: List<Agent>,
        plannedSamples: Int,
        utilization: AgentUtilization?
    ): List<ReasonabilityWarning> {
        val samples = agents.flatMap { agent ->
            when {
                agent.placementOutcomes.isNotEmpty() -> agent.placementOutcomes.map { it.decisions }
                agent.placementDecisions.isNotEmpty() -> agent.placementDecisions.map { it.decisions }
                else -> emptyList()
            }
        }
        if (samples.isEmpty()) return emptyList()

        val attentionPassed = samples.count { it.attention.passed }
        val clickPassed = samples.count { it.attention.passed && it.click.passed }
        val conversionPassed = samples.count {
            it.attention.passed && it.click.passed && it.conversion.passed
        }
        val attentionRate = attentionPassed.toDouble() / samples.size
        val ctr = if (attentionPassed > 0) clickPassed.toDouble() / attentionPassed else 0.0
        val cvr = if (clickPassed > 0) conversionPassed.toDouble() / clickPassed else 0.0
        val warnings = mutableListOf<ReasonabilityWarning>()

        if (plannedSamples < 30 || samples.size < 30) {
            warnings += ReasonabilityWarning(
                code = "low_sample_confidence",
                severity = "medium",
                message = "当前曝光样本较少，结果波动可能偏大。"
            )
        }
        if (attentionRate > 0.8) {
            warnings += ReasonabilityWarning(
                code = "attention_too_high",
                severity = "medium",
                message = "注意率明显偏高，可能高估了素材首屏吸引力。"
            )
        }
        if (ctr > 0.6) {
            warnings += ReasonabilityWarning(
                code = "ctr_too_high",
                severity = "high",
                message = "CTR 明显偏高，当前点击意愿可能不够可信。"
            )
        }
        if (cvr > 0.4) {
            warnings += ReasonabilityWarning(
                code = "cvr_too_high",
                severity = "high",
                message = "CVR 明显偏高，当前转化结果可能偏乐观。"
            )
        }
        if (utilization != null && utilization.searchEligibleRate > 0.7) {
            warnings += ReasonabilityWarning(
                code = "search_coverage_too_high",
                severity = "medium",
                message = "SEARCH 覆盖过广，搜索意图 gate 可能仍然过松。"
            )
        }

        return warnings
    }

    data class QualityAssessment(
        val status: SimulationStatus,
        val quality: SimulationQuality,
        val errorMessage: String? = null
    )
}
