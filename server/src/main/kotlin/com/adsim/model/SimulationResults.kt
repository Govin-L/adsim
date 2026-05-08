package com.adsim.model

data class SimulationResults(
    val totalAgents: Int,
    val successfulAgents: Int,
    val metrics: Metrics,
    val simulatedMetrics: SimulatedFunnelMetrics? = null,
    val estimatedMetrics: EstimatedBusinessMetrics? = null,
    val funnel: Funnel,
    val dropOffReasons: DropOffReasons,
    val clusteredReasons: ClusteredDropOffReasons? = null,
    val segmentInsights: List<SegmentInsight>? = null,
    val topInsights: List<InsightSummary>? = null,
    val placementResults: List<PlacementResult> = emptyList(),
    val utilization: AgentUtilization? = null,
    val quality: SimulationQuality? = null,
    val stageBlockers: List<StageBlocker> = emptyList(),
    val recommendations: List<ActionRecommendation> = emptyList(),
    val calibration: CalibrationResult? = null
)

data class PlacementResult(
    val placementIndex: Int,
    val placement: AdPlacement,
    val totalAgents: Int,
    val successfulAgents: Int,
    val metrics: Metrics,
    val simulatedMetrics: SimulatedFunnelMetrics? = null,
    val estimatedMetrics: EstimatedBusinessMetrics? = null,
    val funnel: Funnel,
    val dropOffReasons: DropOffReasons,
    val clusteredReasons: ClusteredDropOffReasons? = null,
    val segmentInsights: List<SegmentInsight>? = null,
    val topInsights: List<InsightSummary>? = null,
    val sampleQuality: SampleQuality = SampleQuality(),
    val stageBlockers: List<StageBlocker> = emptyList(),
    val recommendations: List<ActionRecommendation> = emptyList()
)

data class SampleQuality(
    val plannedSamples: Int = 0,
    val simulatedSamples: Int = 0,
    val successRate: Double = 0.0,
    val belowThreshold: Boolean = false
)

data class SimulationQuality(
    val requestedAgents: Int,
    val generatedAgents: Int,
    val generationSuccessRate: Double,
    val plannedSamples: Int,
    val simulatedSamples: Int,
    val sampleSuccessRate: Double,
    val failedStages: List<String> = emptyList(),
    val warningCodes: List<String> = emptyList(),
    val reasonabilityWarnings: List<ReasonabilityWarning> = emptyList()
)

data class AgentUtilization(
    val uniqueAgentsReached: Int,
    val uniqueReachRate: Double,
    val averageExposuresPerReachedAgent: Double,
    val averagePlacementsPerReachedAgent: Double,
    val searchEligibleRate: Double,
    val placementCoverage: List<PlacementCoverage> = emptyList()
)

data class PlacementCoverage(
    val placementIndex: Int,
    val uniqueAgentsReached: Int,
    val uniqueReachRate: Double,
    val plannedSamples: Int,
    val eligibleAgents: Int
)

data class ReasonabilityWarning(
    val code: String,
    val severity: String,
    val message: String
)

data class StageBlocker(
    val stage: String,
    val factor: String,
    val count: Int,
    val share: Double,
    val summary: String
)

data class ActionRecommendation(
    val title: String,
    val detail: String,
    val appliesTo: String,
    val priority: String
)

data class CalibrationResult(
    val placements: List<CalibrationPlacementResult>,
    val summary: CalibrationSummary
)

data class CalibrationPlacementResult(
    val placementIndex: Int,
    val actualMetrics: ActualPerformanceMetrics,
    val simulatedMetrics: SimulatedFunnelMetrics,
    val estimatedMetrics: EstimatedBusinessMetrics? = null,
    val prior: PlacementPriorSnapshot? = null,
    val deltas: CalibrationDelta
)

data class PlacementPriorSnapshot(
    val baseAttention: Double,
    val baseClick: Double,
    val baseConversion: Double,
    val calibrationCount: Int,
    val converged: Boolean = false
)

data class CalibrationSummary(
    val coverage: Int,
    val averageCtrDelta: Double?,
    val averageCvrDelta: Double?,
    val averageCpaDelta: Double?
)

data class ActualPerformanceMetrics(
    val ctr: Double? = null,
    val cvr: Double? = null,
    val cpa: Double? = null,
    val impressions: Long? = null,
    val conversions: Long? = null
)

data class CalibrationDelta(
    val ctrDelta: Double? = null,
    val cvrDelta: Double? = null,
    val cpaDelta: Double? = null
)

/**
 * 当前版本的 metrics 混合了两类口径：
 * 1. attention / ctr / cvr / overallConversionRate：来自 Agent 行为仿真的 simulated signals
 * 2. estimatedCPA：基于预算、placement 配置和行业基准推导出的 estimated business metric
 */
data class Metrics(
    val attentionRate: Double,
    val ctr: Double,
    val cvr: Double,
    val overallConversionRate: Double,
    val estimatedCPA: Double?
)

data class SimulatedFunnelMetrics(
    val attentionRate: Double,
    val ctr: Double,
    val cvr: Double,
    val overallConversionRate: Double
)

data class EstimatedBusinessMetrics(
    val estimatedImpressions: Double,
    val estimatedViewers: Double,
    val estimatedConversions: Double,
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
