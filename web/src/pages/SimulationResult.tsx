import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { api, type Simulation, type Agent } from '../api/client'
import FunnelChart from '../components/FunnelChart'
import MetricsCards from '../components/MetricsCards'
import DropOffReasons from '../components/DropOffReasons'

export default function SimulationResult() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [simulation, setSimulation] = useState<Simulation | null>(null)
  const [agents, setAgents] = useState<Agent[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return

    // Initial fetch
    api.getSimulation(id).then(setSimulation).catch(() => setError('Simulation not found'))

    // SSE for progress
    const es = api.subscribeProgress(id)
    es.onmessage = (event) => {
      const data = JSON.parse(event.data)
      setSimulation(prev => prev ? { ...prev, progress: data } : prev)

      if (data.status === 'completed' || data.status === 'failed') {
        es.close()
        // Re-fetch full simulation with results
        api.getSimulation(id).then(setSimulation)
        api.getAgents(id).then(setAgents)
      }
    }
    es.onerror = () => es.close()

    return () => es.close()
  }, [id])

  // Fetch agents when simulation completes
  useEffect(() => {
    if (simulation?.status === 'COMPLETED' && id && agents.length === 0) {
      api.getAgents(id).then(setAgents)
    }
  }, [simulation?.status, id, agents.length])

  if (error) {
    return (
      <div className="max-w-4xl mx-auto p-6 text-center">
        <p className="text-red-500 text-lg">{error}</p>
      </div>
    )
  }

  if (!simulation) {
    return (
      <div className="max-w-4xl mx-auto p-6 text-center">
        <p className="text-gray-400">Loading...</p>
      </div>
    )
  }

  const isRunning = ['PENDING', 'GENERATING', 'SIMULATING', 'AGGREGATING'].includes(simulation.status)

  return (
    <div className="max-w-4xl mx-auto p-6">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold">{simulation.input.product.name}</h1>
          <p className="text-gray-500">
            {simulation.input.platform} &middot; ¥{simulation.input.budget.toLocaleString()} budget
          </p>
        </div>
        <StatusBadge status={simulation.status} />
      </div>

      {/* Progress */}
      {isRunning && (
        <div className="mb-8">
          <div className="flex justify-between text-sm text-gray-500 mb-2">
            <span>{statusLabel(simulation.status)}</span>
            <span>{simulation.progress.completed} / {simulation.progress.total}</span>
          </div>
          <div className="w-full bg-gray-100 rounded-full h-3">
            <div
              className="bg-black rounded-full h-3 transition-all duration-300"
              style={{ width: `${simulation.progress.total > 0 ? (simulation.progress.completed / simulation.progress.total) * 100 : 0}%` }}
            />
          </div>
        </div>
      )}

      {/* Results */}
      {simulation.status === 'COMPLETED' && simulation.results && (
        <>
          <MetricsCards metrics={simulation.results.metrics} />
          <FunnelChart funnel={simulation.results.funnel} />
          <DropOffReasons dropOffReasons={simulation.results.dropOffReasons} />

          {/* Agent List */}
          <section className="mt-8">
            <h2 className="text-lg font-semibold mb-4">Agents ({agents.length})</h2>
            <div className="grid gap-3">
              {agents.slice(0, 20).map(agent => (
                <div
                  key={agent.id}
                  onClick={() => navigate(`/simulation/${id}/agent/${agent.id}`)}
                  className="p-4 border rounded-lg hover:bg-gray-50 cursor-pointer flex justify-between items-center"
                >
                  <div>
                    <span className="font-medium">{agent.persona.name}</span>
                    <span className="text-gray-400 text-sm ml-2">
                      {agent.persona.age}y, {agent.persona.gender}, {agent.persona.income} income
                    </span>
                  </div>
                  <div className="flex gap-2">
                    <FunnelBadge label="Attention" passed={agent.decisions?.attention.passed} />
                    <FunnelBadge label="Click" passed={agent.decisions?.click.passed} />
                    <FunnelBadge label="Convert" passed={agent.decisions?.conversion.passed} />
                  </div>
                </div>
              ))}
              {agents.length > 20 && (
                <p className="text-sm text-gray-400 text-center">Showing 20 of {agents.length} agents</p>
              )}
            </div>
          </section>
        </>
      )}

      {simulation.status === 'FAILED' && (
        <div className="text-center py-12">
          <p className="text-red-500 text-lg">Simulation failed</p>
          <button onClick={() => navigate('/')} className="mt-4 px-4 py-2 bg-black text-white rounded-lg">
            Try Again
          </button>
        </div>
      )}
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    PENDING: 'bg-gray-100 text-gray-600',
    GENERATING: 'bg-blue-50 text-blue-600',
    SIMULATING: 'bg-yellow-50 text-yellow-700',
    AGGREGATING: 'bg-purple-50 text-purple-600',
    COMPLETED: 'bg-green-50 text-green-700',
    FAILED: 'bg-red-50 text-red-600',
  }
  return (
    <span className={`px-3 py-1 rounded-full text-sm font-medium ${colors[status] || 'bg-gray-100'}`}>
      {status}
    </span>
  )
}

function FunnelBadge({ label, passed }: { label: string; passed?: boolean }) {
  if (passed === undefined) return <span className="text-xs text-gray-300 px-2 py-1 bg-gray-50 rounded">{label}</span>
  return (
    <span className={`text-xs px-2 py-1 rounded ${passed ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-500'}`}>
      {label}
    </span>
  )
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    PENDING: 'Initializing...',
    GENERATING: 'Generating agents...',
    SIMULATING: 'Running simulation...',
    AGGREGATING: 'Aggregating results...',
  }
  return labels[status] || status
}
