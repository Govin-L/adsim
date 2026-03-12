import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { api, type Simulation, type Agent } from '../api/client'
import FunnelChart from '../components/FunnelChart'
import MetricsCards from '../components/MetricsCards'
import DropOffReasons from '../components/DropOffReasons'
import LanguageSwitch from '../components/LanguageSwitch'

export default function SimulationResult() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [simulation, setSimulation] = useState<Simulation | null>(null)
  const [agents, setAgents] = useState<Agent[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    api.getSimulation(id).then(setSimulation).catch(() => setError(t('result.notFound')))

    const es = api.subscribeProgress(id)
    es.onmessage = (event) => {
      const data = JSON.parse(event.data)
      setSimulation(prev => prev ? { ...prev, progress: data } : prev)
      if (data.status === 'completed' || data.status === 'failed') {
        es.close()
        api.getSimulation(id).then(setSimulation)
        api.getAgents(id).then(setAgents)
      }
    }
    es.onerror = () => es.close()
    return () => es.close()
  }, [id, t])

  useEffect(() => {
    if (simulation?.status === 'COMPLETED' && id && agents.length === 0) {
      api.getAgents(id).then(setAgents)
    }
  }, [simulation?.status, id, agents.length])

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: 'var(--color-bg)' }}>
        <p style={{ color: 'var(--color-error)' }}>{error}</p>
      </div>
    )
  }

  if (!simulation) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: 'var(--color-bg)' }}>
        <p style={{ color: 'var(--color-text-tertiary)' }}>{t('result.loading')}</p>
      </div>
    )
  }

  const isRunning = ['PENDING', 'GENERATING', 'SIMULATING', 'AGGREGATING'].includes(simulation.status)
  const pct = simulation.progress.total > 0 ? (simulation.progress.completed / simulation.progress.total) * 100 : 0

  return (
    <div className="min-h-screen" style={{ background: 'var(--color-bg)' }}>
      {/* Header */}
      <header className="border-b animate-in" style={{ borderColor: 'var(--color-border)', background: 'var(--color-surface)' }}>
        <div className="max-w-7xl mx-auto px-8 py-5 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <button onClick={() => navigate('/')} className="cursor-pointer" style={{ color: 'var(--color-text-tertiary)', fontFamily: 'var(--font-display)', fontSize: '1.25rem' }}>
              {t('app.name')}
            </button>
            <span style={{ color: 'var(--color-border)' }}>/</span>
            <div>
              <h1 className="text-lg font-semibold" style={{ letterSpacing: '-0.01em' }}>{simulation.input.product.name}</h1>
              <p className="text-xs mt-0.5" style={{ color: 'var(--color-text-tertiary)' }}>
                {simulation.input.platform} &middot; ¥{simulation.input.budget.toLocaleString()} {t('result.budget')}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <StatusBadge status={simulation.status} t={t} />
            <LanguageSwitch />
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-8 py-8">
        {/* Progress */}
        {isRunning && (
          <div className="mb-10 animate-in stagger-1">
            <div className="flex justify-between text-xs mb-2.5" style={{ color: 'var(--color-text-tertiary)', fontFamily: 'var(--font-mono)' }}>
              <span>{t(`result.status.${simulation.status}`)}</span>
              <span>{simulation.progress.completed}/{simulation.progress.total}</span>
            </div>
            <div className="w-full h-2 rounded-full overflow-hidden" style={{ background: 'var(--color-border-subtle)' }}>
              <div className="h-2 rounded-full transition-all duration-500" style={{ width: `${pct}%`, background: 'var(--color-text)', animation: pct < 100 ? 'pulse-bar 2s ease-in-out infinite' : 'none' }} />
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
            <section className="mt-8 animate-in stagger-5">
              <h2 className="text-xs font-semibold uppercase tracking-wider mb-4" style={{ color: 'var(--color-text-secondary)', letterSpacing: '0.08em' }}>
                {t('result.agents.title')} <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-text-tertiary)' }}>({agents.length})</span>
              </h2>
              <div className="space-y-2">
                {agents.slice(0, 20).map(agent => (
                  <div key={agent.id}
                    onClick={() => navigate(`/simulation/${id}/agent/${agent.id}`)}
                    className="p-4 rounded-xl flex justify-between items-center cursor-pointer transition-all"
                    style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}
                    onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--color-text-tertiary)'}
                    onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--color-border)'}
                  >
                    <div className="flex items-center gap-3">
                      <span className="font-medium text-sm">{agent.persona.name}</span>
                      <span className="text-xs" style={{ color: 'var(--color-text-tertiary)', fontFamily: 'var(--font-mono)' }}>
                        {agent.persona.age} &middot; {agent.persona.gender} &middot; {agent.persona.income}
                      </span>
                    </div>
                    <div className="flex gap-1.5">
                      <FunnelDot label={t('result.agents.attention')} passed={agent.decisions?.attention.passed} />
                      <FunnelDot label={t('result.agents.click')} passed={agent.decisions?.click.passed} />
                      <FunnelDot label={t('result.agents.convert')} passed={agent.decisions?.conversion.passed} />
                    </div>
                  </div>
                ))}
              </div>
              {agents.length > 20 && (
                <p className="text-xs text-center mt-4" style={{ color: 'var(--color-text-tertiary)' }}>
                  {t('result.agents.showing', { shown: 20, total: agents.length })}
                </p>
              )}
            </section>
          </>
        )}

        {/* Failed */}
        {simulation.status === 'FAILED' && (
          <div className="text-center py-20 animate-fade">
            <p className="text-lg font-medium" style={{ color: 'var(--color-error)' }}>{t('result.failed.title')}</p>
            <button onClick={() => navigate('/')}
              className="mt-6 px-6 py-2.5 rounded-lg text-sm font-medium cursor-pointer transition-opacity"
              style={{ background: 'var(--color-text)', color: 'var(--color-bg)' }}
              onMouseEnter={e => e.currentTarget.style.opacity = '0.85'}
              onMouseLeave={e => e.currentTarget.style.opacity = '1'}
            >
              {t('result.failed.retry')}
            </button>
          </div>
        )}
      </main>
    </div>
  )
}

function StatusBadge({ status, t }: { status: string; t: (key: string) => string }) {
  const styles: Record<string, { bg: string; color: string }> = {
    PENDING: { bg: 'var(--color-border-subtle)', color: 'var(--color-text-tertiary)' },
    GENERATING: { bg: '#eff6ff', color: '#2563eb' },
    SIMULATING: { bg: 'var(--color-accent-bg)', color: 'var(--color-accent)' },
    AGGREGATING: { bg: '#faf5ff', color: '#7c3aed' },
    COMPLETED: { bg: 'var(--color-success-bg)', color: 'var(--color-success)' },
    FAILED: { bg: 'var(--color-error-bg)', color: 'var(--color-error)' },
  }
  const s = styles[status] || styles.PENDING
  return (
    <span className="px-3 py-1 rounded-full text-xs font-semibold" style={{ background: s.bg, color: s.color, fontFamily: 'var(--font-mono)' }}>
      {t(`result.status.${status}`)}
    </span>
  )
}

function FunnelDot({ label, passed }: { label: string; passed?: boolean }) {
  const bg = passed === undefined ? 'var(--color-border)' : passed ? 'var(--color-success)' : 'var(--color-error)'
  return (
    <div className="flex items-center gap-1.5 px-2 py-1 rounded text-xs" title={label}
      style={{ background: passed === undefined ? 'var(--color-border-subtle)' : passed ? 'var(--color-success-bg)' : 'var(--color-error-bg)' }}>
      <span className="w-1.5 h-1.5 rounded-full" style={{ background: bg }} />
      <span style={{ color: bg, fontFamily: 'var(--font-mono)', fontSize: '0.65rem' }}>{label}</span>
    </div>
  )
}
