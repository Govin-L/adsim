const BASE_URL = '/api'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const { headers, ...rest } = options || {}
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...headers },
    ...rest,
  })
  if (!res.ok) {
    throw new Error(`API error: ${res.status} ${res.statusText}`)
  }
  return res.json()
}

export interface CreateSimulationRequest {
  product: {
    name: string
    price: number
    category: string
    description?: string
  }
  creative: {
    description: string
    format?: 'VIDEO' | 'IMAGE' | 'TEXT'
  }
  platform: string
  budget: number
  targetAudience: {
    ageRange?: number[]
    gender?: string
    interests?: string[]
  }
  agentCount?: number
}

export interface Simulation {
  id: string
  status: 'PENDING' | 'GENERATING' | 'SIMULATING' | 'AGGREGATING' | 'COMPLETED' | 'FAILED'
  progress: { total: number; completed: number }
  input: CreateSimulationRequest
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

export const api = {
  createSimulation: (data: CreateSimulationRequest) =>
    request<Simulation>('/simulations', {
      method: 'POST',
      body: JSON.stringify(data),
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
