package com.adsim.model

data class SimulationResults(
    val totalAgents: Int,
    val successfulAgents: Int,
    val metrics: Metrics,
    val funnel: Funnel,
    val dropOffReasons: DropOffReasons,
    val clusteredReasons: ClusteredDropOffReasons? = null,
    val segmentInsights: List<SegmentInsight>? = null,
    val topInsights: List<InsightSummary>? = null
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

data class ClusteredReason(
    val category: String,
    val percentage: Double,
    val count: Int,
    val quotes: List<String>
)

data class ClusteredDropOffReasons(
    val attentionToClick: List<ClusteredReason>,
    val clickToConversion: List<ClusteredReason>
)

data class SegmentStat(
    val label: String,
    val agentCount: Int,
    val convertedCount: Int,
    val conversionRate: Double,
    val highlight: String? = null  // "highest" | "lowest" | null
)

data class SegmentInsight(
    val dimension: String,
    val segments: List<SegmentStat>
)

data class InsightSummary(
    val text: String
)
