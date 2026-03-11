package com.adsim.model

data class SimulationResults(
    val totalAgents: Int,
    val successfulAgents: Int,
    val metrics: Metrics,
    val funnel: Funnel,
    val dropOffReasons: DropOffReasons
)

data class Metrics(
    val attentionRate: Double,
    val ctr: Double,
    val cvr: Double,
    val overallConversionRate: Double,
    val estimatedCPA: Double?
)

data class Funnel(
    val exposure: FunnelStage,
    val attention: FunnelStage,
    val click: FunnelStage,
    val conversion: FunnelStage
)

data class FunnelStage(
    val count: Int,
    val rate: Double
)

data class DropOffReasons(
    val attentionToClick: List<ReasonStat>,
    val clickToConversion: List<ReasonStat>
)

data class ReasonStat(
    val reason: String,
    val count: Int,
    val percentage: Double
)
