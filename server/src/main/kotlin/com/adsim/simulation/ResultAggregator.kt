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
    fun aggregate(agents: List<Agent>, budget: Long, placements: List<AdPlacement> = emptyList()): SimulationResults {
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

        // CPA calculation:
        // Each simulated agent = a user who actually SAW the ad.
        // Total impressions (from CPM benchmark) include users who never saw the ad.
        // viewability rate converts impressions → actual viewers.
        // Agent's overallConversionRate = conversion rate among viewers.
        // CPA = budget / (impressions × viewability × agentConversionRate)
        val estimatedImpressions = estimateImpressions(placements, budget)
        val viewabilityRate = 0.50 // industry average: ~50% of impressions are actually viewed
        val estimatedViewers = estimatedImpressions * viewabilityRate
        val estimatedConversions = estimatedViewers * overallConversionRate
        val estimatedCPA = if (estimatedConversions > 0) budget.toDouble() / estimatedConversions else null

        logger.info("CPA calc: impressions={}, viewability={}, viewers={}, agentCVR={}, conversions={}, CPA={}",
            estimatedImpressions.toLong(), viewabilityRate, estimatedViewers.toLong(),
            "%.4f".format(overallConversionRate), estimatedConversions.toLong(), estimatedCPA?.let { "%.2f".format(it) })

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

    /**
     * Estimate total impressions from budget, using CPM benchmarks per placement type.
     * Each placement's budget is divided by its CPM to get impressions, then summed.
     */
    private fun estimateImpressions(placements: List<AdPlacement>, totalBudget: Long): Double {
        if (placements.isEmpty()) {
            // Fallback: blended CPM
            return totalBudget / 80.0 * 1000
        }
        return placements.sumOf { placement ->
            val cpm = cpmBenchmark(placement.placementType)
            placement.budget.toDouble() / cpm * 1000
        }
    }

    /**
     * CPM benchmarks by placement type (¥ per 1000 impressions).
     * Based on Chinese social media advertising industry averages.
     */
    private fun cpmBenchmark(type: PlacementType): Double = when (type) {
        PlacementType.INFO_FEED -> 40.0       // algorithm-recommended feed ads
        PlacementType.SEARCH -> 80.0           // user-initiated search, more targeted
        PlacementType.KOL_SEEDING -> 300.0     // influencer content, includes talent fee
        PlacementType.SHORT_VIDEO -> 50.0      // short video ads
        PlacementType.SPLASH_SCREEN -> 25.0    // splash screen, massive reach but low engagement
        PlacementType.LIVESTREAM -> 150.0       // livestream promotion
        PlacementType.HASHTAG_CHALLENGE -> 200.0 // branded hashtag challenges
        PlacementType.SHOPPING -> 60.0          // in-app shopping promotions
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
