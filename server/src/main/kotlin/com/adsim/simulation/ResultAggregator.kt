package com.adsim.simulation

import com.adsim.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ResultAggregator {

    private val logger = LoggerFactory.getLogger(ResultAggregator::class.java)

    /**
     * Aggregate agent decisions into emerged metrics.
     * Groups similar drop-off reasons using LLM summarization.
     */
    fun aggregate(agents: List<Agent>, budget: Long): SimulationResults {
        val withDecisions = agents.filter { it.decisions != null }
        val total = agents.size
        val successful = withDecisions.size

        val noticed = withDecisions.count { it.decisions!!.attention.passed }
        val clicked = withDecisions.filter { it.decisions!!.attention.passed }
            .count { it.decisions!!.click.passed }
        val converted = withDecisions.filter { it.decisions!!.attention.passed && it.decisions!!.click.passed }
            .count { it.decisions!!.conversion.passed }

        val attentionRate = if (successful > 0) noticed.toDouble() / successful else 0.0
        val ctr = if (noticed > 0) clicked.toDouble() / noticed else 0.0
        val cvr = if (clicked > 0) converted.toDouble() / clicked else 0.0
        val overallConversionRate = if (successful > 0) converted.toDouble() / successful else 0.0

        // CPA: use overall conversion rate to estimate from budget
        // This is a simplified calculation, will be refined with CPM benchmarks
        val estimatedCPA = if (converted > 0) budget.toDouble() / converted * (successful.toDouble() / estimateReach(budget)) else null

        val dropOffReasons = buildDropOffReasons(withDecisions)

        logger.info("Aggregation complete: {}/{} agents, noticed={}, clicked={}, converted={}",
            successful, total, noticed, clicked, converted)

        return SimulationResults(
            totalAgents = total,
            successfulAgents = successful,
            metrics = Metrics(
                attentionRate = attentionRate,
                ctr = ctr,
                cvr = cvr,
                overallConversionRate = overallConversionRate,
                estimatedCPA = estimatedCPA
            ),
            funnel = Funnel(
                exposure = FunnelStage(successful, 1.0),
                attention = FunnelStage(noticed, attentionRate),
                click = FunnelStage(clicked, ctr),
                conversion = FunnelStage(converted, cvr)
            ),
            dropOffReasons = dropOffReasons
        )
    }

    private fun estimateReach(budget: Long): Double {
        // TODO: Use platform-specific CPM benchmarks
        // Placeholder: assume CPM = ¥35 (Xiaohongshu beauty category)
        val cpmBenchmark = 35.0
        return budget / cpmBenchmark * 1000
    }

    private fun buildDropOffReasons(agents: List<Agent>): DropOffReasons {
        val noticedNotClicked = agents.filter {
            it.decisions!!.attention.passed && !it.decisions!!.click.passed
        }
        val clickedNotConverted = agents.filter {
            it.decisions!!.attention.passed && it.decisions!!.click.passed && !it.decisions!!.conversion.passed
        }

        return DropOffReasons(
            attentionToClick = groupReasons(noticedNotClicked.map { it.decisions!!.click.reasoning }),
            clickToConversion = groupReasons(clickedNotConverted.map { it.decisions!!.conversion.reasoning })
        )
    }

    private fun groupReasons(reasons: List<String>): List<ReasonStat> {
        if (reasons.isEmpty()) return emptyList()
        // TODO: Use LLM to group similar reasons
        // For now, simple frequency count
        val total = reasons.size
        return reasons.groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { ReasonStat(it.key, it.value, it.value.toDouble() / total) }
    }
}
