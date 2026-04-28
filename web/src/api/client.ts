import { getLlmConfig } from '@/lib/llm-config'

const BASE_URL = '/api'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const { headers, ...rest } = options || {}
  const llm = getLlmConfig()
  const llmHeaders: Record<string, string> = {}
  if (llm) {
    llmHeaders['X-LLM-Api-Key'] = llm.apiKey
    llmHeaders['X-LLM-Base-Url'] = llm.baseUrl
    llmHeaders['X-LLM-Model'] = llm.model
    if (llm.concurrency) llmHeaders['X-LLM-Concurrency'] = String(llm.concurrency)
  }
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...llmHeaders, ...headers },
    ...rest,
  })
  if (!res.ok) {
    throw new Error(`API error: ${res.status} ${res.statusText}`)
  }
  return res.json()
}

/* ---- Types ---- */

export interface CompetitorInfo {
  brandName: string
  price: number
  positioning: string
}

export interface Product {
  brandName: string
  name: string
  price: number
  category: string
  sellingPoints: string
  productStage: 'NEW_LAUNCH' | 'ESTABLISHED' | 'BESTSELLER'
  description?: string
}

export interface AdPlacement {
  platform: string
  placementType: string
  objectives: string[]
  format: string
  budget: number
  creativeDescription: string
}

export interface TargetAudience {
  ageRange?: number[]
  gender?: string
  region?: string
  interests?: string[]
}

export interface SimulationInput {
  product: Product
  adPlacements: AdPlacement[]
  totalBudget: number
  targetAudience: TargetAudience
  competitors?: CompetitorInfo[]
  brandAwareness?: 'NEW' | 'EMERGING' | 'WELL_KNOWN' | 'TOP'
  campaignGoal?: 'ACQUISITION' | 'RETENTION' | 'MIXED'
}

export interface ParsePlanResponse {
  mergedPlan: SimulationInput
  changedFields: string[]
  warnings: string[]
  missingFields: string[]
}

export interface Simulation {
  id: string
  status: 'PENDING' | 'GENERATING' | 'SIMULATING' | 'AGGREGATING' | 'COMPLETED' | 'COMPLETED_WITH_WARNINGS' | 'FAILED'
  progress: { total: number; completed: number }
  errorMessage: string | null
  input: SimulationInput
  rawInput: string
  results: SimulationResults | null
  createdAt: string
  completedAt: string | null
}

export interface ClusteredReason {
  category: string
  percentage: number
  count: number
  quotes: string[]
}

export interface ClusteredDropOffReasons {
  attentionToClick: ClusteredReason[]
  clickToConversion: ClusteredReason[]
}

export interface SegmentStat {
  label: string
  agentCount: number
  convertedCount: number
  conversionRate: number
  highlight: string | null  // "highest" | "lowest" | null
}

export interface SegmentInsight {
  dimension: string
  segments: SegmentStat[]
}

export interface InsightSummary {
  text: string
}

export interface Metrics {
  attentionRate: number
  ctr: number
  cvr: number
  overallConversionRate: number
  estimatedCPA: number | null
}

export interface SimulatedFunnelMetrics {
  attentionRate: number
  ctr: number
  cvr: number
  overallConversionRate: number
}

export interface EstimatedBusinessMetrics {
  estimatedImpressions: number
  estimatedViewers: number
  estimatedConversions: number
  estimatedCPA: number | null
}

export interface FunnelStage {
  count: number
  rate: number
}

export interface Funnel {
  exposure: FunnelStage
  attention: FunnelStage
  click: FunnelStage
  conversion: FunnelStage
}

export interface DropOffReason {
  reason: string
  count: number
  percentage: number
}

export interface DropOffReasons {
  attentionToClick: DropOffReason[]
  clickToConversion: DropOffReason[]
}

export interface PlacementResult {
  placementIndex: number
  placement: AdPlacement
  totalAgents: number
  successfulAgents: number
  metrics: Metrics
  simulatedMetrics?: SimulatedFunnelMetrics
  estimatedMetrics?: EstimatedBusinessMetrics
  funnel: Funnel
  dropOffReasons: DropOffReasons
  clusteredReasons?: ClusteredDropOffReasons
  segmentInsights?: SegmentInsight[]
  topInsights?: InsightSummary[]
  sampleQuality?: SampleQuality
  stageBlockers?: StageBlocker[]
  recommendations?: ActionRecommendation[]
}

export interface SampleQuality {
  plannedSamples: number
  simulatedSamples: number
  successRate: number
  belowThreshold: boolean
}

export interface ReasonabilityWarning {
  code: string
  severity: string
  message: string
}

export interface PlacementCoverage {
  placementIndex: number
  uniqueAgentsReached: number
  uniqueReachRate: number
  plannedSamples: number
  eligibleAgents: number
}

export interface AgentUtilization {
  uniqueAgentsReached: number
  uniqueReachRate: number
  averageExposuresPerReachedAgent: number
  averagePlacementsPerReachedAgent: number
  searchEligibleRate: number
  placementCoverage: PlacementCoverage[]
}

export interface SimulationQuality {
  requestedAgents: number
  generatedAgents: number
  generationSuccessRate: number
  plannedSamples: number
  simulatedSamples: number
  sampleSuccessRate: number
  failedStages: string[]
  warningCodes: string[]
  reasonabilityWarnings: ReasonabilityWarning[]
}

export interface StageBlocker {
  stage: string
  factor: string
  count: number
  share: number
  summary: string
}

export interface ActionRecommendation {
  title: string
  detail: string
  appliesTo: string
  priority: string
}

export interface CalibrationDelta {
  ctrDelta?: number | null
  cvrDelta?: number | null
  cpaDelta?: number | null
}

export interface ActualPerformanceMetrics {
  ctr?: number | null
  cvr?: number | null
  cpa?: number | null
  impressions?: number | null
  conversions?: number | null
}

export interface PlacementPriorSnapshot {
  baseAttention: number
  baseClick: number
  baseConversion: number
  calibrationCount: number
}

export interface CalibrationPlacementResult {
  placementIndex: number
  actualMetrics: ActualPerformanceMetrics
  simulatedMetrics: SimulatedFunnelMetrics
  estimatedMetrics?: EstimatedBusinessMetrics
  prior?: PlacementPriorSnapshot | null
  deltas: CalibrationDelta
}

export interface CalibrationSummary {
  coverage: number
  averageCtrDelta?: number | null
  averageCvrDelta?: number | null
  averageCpaDelta?: number | null
}

export interface CalibrationResult {
  placements: CalibrationPlacementResult[]
  summary: CalibrationSummary
}

export interface SimulationResults {
  totalAgents: number
  successfulAgents: number
  metrics: Metrics
  simulatedMetrics?: SimulatedFunnelMetrics
  estimatedMetrics?: EstimatedBusinessMetrics
  funnel: Funnel
  dropOffReasons: DropOffReasons
  clusteredReasons?: ClusteredDropOffReasons
  segmentInsights?: SegmentInsight[]
  topInsights?: InsightSummary[]
  placementResults?: PlacementResult[]
  utilization?: AgentUtilization
  quality?: SimulationQuality
  stageBlockers?: StageBlocker[]
  recommendations?: ActionRecommendation[]
  calibration?: CalibrationResult
}

export interface UpdateCalibrationRequest {
  placements: Array<{
    placementIndex: number
    ctr?: number
    cvr?: number
    cpa?: number
    impressions?: number
    conversions?: number
  }>
}

export interface StageDecision {
  passed: boolean
  reasoning: string
  factors?: string[]
  score?: number
  likelihoodBand?: 'VERY_LOW' | 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH' | null
  probability?: number | null
  positiveFactors?: string[]
  negativeFactors?: string[]
}

export interface PlacementOutcome {
  placementIndex: number
  platform: string
  placementType: string
  exposureEvent: {
    agentId: string
    placementIndex: number
    sequence: number
    deliveryContext: {
      source: string
      frequency: number
      intentLevel?: number | null
      estimatedCostWeight: number
    }
  }
  attention: StageDecision
  click: StageDecision
  conversion: StageDecision
}

export interface Agent {
  id: string
  simulationId: string
  persona: {
    name: string
    age: number
    gender: string
    income: string
    cityTier: number
    interests: string[]
    platformBehavior: {
      dailyUsageMinutes: number
      contentPreferences: string[]
      purchaseFrequency: string
    }
    consumptionHabits: {
      priceSensitivity: string
      decisionSpeed: string
      brandLoyalty: string
    }
  }
  campaignState?: {
    placementsSeen: number
    noticedCount: number
    clickedCount: number
    convertedCount: number
    fatigueScore: number
    brandFamiliarity: string
  }
  decisions: {
    attention: StageDecision
    click: StageDecision
    conversion: StageDecision
  } | null
  placementDecisions?: Array<{
    placementIndex: number
    platform: string
    placementType: string
    decisions: {
      attention: StageDecision
      click: StageDecision
      conversion: StageDecision
    }
  }>
  placementOutcomes?: PlacementOutcome[]
}

export interface InterviewResponse {
  reply: string
  conversationId: string
}

/* ---- API ---- */

export const api = {
  verifyLlm: () =>
    request<{ success: boolean; reply?: string; error?: string }>('/simulations/verify-llm', {
      method: 'POST',
    }),

  parsePlan: (content: string, currentPlan?: SimulationInput | null) =>
    request<ParsePlanResponse>('/simulations/parse', {
      method: 'POST',
      body: JSON.stringify({ content, currentPlan: currentPlan ?? null }),
    }),

  listSimulations: () =>
    request<Simulation[]>('/simulations'),

  createSimulation: (input: SimulationInput, rawInput: string, agentCount?: number) =>
    request<Simulation>('/simulations', {
      method: 'POST',
      body: JSON.stringify({ input, rawInput, agentCount: agentCount || 20 }),
    }),

  getSimulation: (id: string) =>
    request<Simulation>(`/simulations/${id}`),

  getAgents: (simulationId: string) =>
    request<Agent[]>(`/simulations/${simulationId}/agents`),

  getAgent: (simulationId: string, agentId: string) =>
    request<Agent>(`/simulations/${simulationId}/agents/${agentId}`),

  interview: (simulationId: string, agentId: string, message: string, conversationId?: string) =>
    request<InterviewResponse>(`/simulations/${simulationId}/agents/${agentId}/interview`, {
      method: 'POST',
      body: JSON.stringify({ message, conversationId }),
    }),

  updateCalibration: (simulationId: string, payload: UpdateCalibrationRequest) =>
    request<Simulation>(`/simulations/${simulationId}/calibration`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  subscribeProgress: (simulationId: string) =>
    new EventSource(`${BASE_URL}/simulations/${simulationId}/progress`),
}
