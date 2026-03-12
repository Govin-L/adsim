export interface LlmConfig {
  apiKey: string
  baseUrl: string
  model: string
  concurrency?: number | undefined
}

const STORAGE_KEY = 'adsim-llm-config'

export function getLlmConfig(): LlmConfig | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const config = JSON.parse(raw) as LlmConfig
    if (!config.apiKey) return null
    return config
  } catch {
    return null
  }
}

export function saveLlmConfig(config: LlmConfig): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(config))
}

export function isLlmConfigured(): boolean {
  return getLlmConfig() !== null
}
