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
}

export interface ParsePlanResponse {
  input: SimulationInput
  missingFields: string[]
}

export interface Simulation {
  id: string
  status: 'PENDING' | 'GENERATING' | 'SIMULATING' | 'AGGREGATING' | 'COMPLETED' | 'FAILED'
  progress: { total: number; completed: number }
  input: SimulationInput
  rawInput: string
  results: SimulationResults | null
  createdAt: string
  completedAt: string | null
}

export interface SimulationResults {
  totalAgents: number
  successfulAgents: number
  metrics: {
    attentionRate: number
    ctr: number
    cvr: number
    overallConversionRate: number
    estimatedCPA: number | null
  }
  funnel: {
    exposure: { count: number; rate: number }
    attention: { count: number; rate: number }
    click: { count: number; rate: number }
    conversion: { count: number; rate: number }
  }
  dropOffReasons: {
    attentionToClick: { reason: string; count: number; percentage: number }[]
    clickToConversion: { reason: string; count: number; percentage: number }[]
  }
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
  decisions: {
    attention: { passed: boolean; reasoning: string }
    click: { passed: boolean; reasoning: string }
    conversion: { passed: boolean; reasoning: string }
  } | null
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

  parsePlan: (content: string) =>
    request<ParsePlanResponse>('/simulations/parse', {
      method: 'POST',
      body: JSON.stringify({ content }),
    }),

  createSimulation: (input: SimulationInput, rawInput: string, agentCount?: number) =>
    request<Simulation>('/simulations', {
      method: 'POST',
      body: JSON.stringify({ input, rawInput, agentCount: agentCount || 200 }),
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

  subscribeProgress: (simulationId: string) =>
    new EventSource(`${BASE_URL}/simulations/${simulationId}/progress`),
}
