import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Loader2, AlertCircle, ChevronDown } from 'lucide-react'
import { api, type Simulation, type Agent } from '@/api/client'
import FunnelChart from '@/components/FunnelChart'
import MetricsCards from '@/components/MetricsCards'
import DropOffReasons from '@/components/DropOffReasons'
import InsightsBanner from '@/components/InsightsBanner'
import SegmentAnalysis from '@/components/SegmentAnalysis'
import LanguageSwitch from '@/components/LanguageSwitch'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'

export default function SimulationResult() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [simulation, setSimulation] = useState<Simulation | null>(null)
  const [agentPage, setAgentPage] = useState(1)
  const agentPageSize = 20
  const [agents, setAgents] = useState<Agent[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    api.getSimulation(id).then(setSimulation).catch(() => setError(t('result.notFound')))

    let es: EventSource | null = null
    let done = false

    const connect = () => {
      if (done) return
      es = api.subscribeProgress(id)
      es.onmessage = (event) => {
        const data = JSON.parse(event.data)
        const statusMap: Record<string, string> = { generating: 'GENERATING', running: 'SIMULATING', completed: 'COMPLETED', failed: 'FAILED' }
        setSimulation(prev => prev ? {
          ...prev,
          progress: { total: data.total, completed: data.completed },
          ...(statusMap[data.status] ? { status: statusMap[data.status] as Simulation['status'] } : {}),
          ...(data.error ? { errorMessage: data.error, status: 'FAILED' as const } : {})
        } : prev)
        if (data.status === 'completed' || data.status === 'failed') {
          done = true
          es?.close()
          api.getSimulation(id).then(setSimulation)
          api.getAgents(id).then(setAgents)
        }
      }
      es.onerror = () => {
        es?.close()
        if (!done) {
          api.getSimulation(id).then(sim => {
            if (sim) {
              setSimulation(sim)
              if (sim.status === 'COMPLETED' || sim.status === 'FAILED') {
                done = true
                if (sim.status === 'COMPLETED') api.getAgents(id).then(setAgents)
                return
              }
            }
            setTimeout(connect, 2000)
          }).catch(() => setTimeout(connect, 2000))
        }
      }
    }
    connect()

    return () => { done = true; es?.close() }
  }, [id, t])

  useEffect(() => {
    if (simulation?.status === 'COMPLETED' && id && agents.length === 0) {
      api.getAgents(id).then(setAgents)
    }
  }, [simulation?.status, id, agents.length])

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <p className="text-destructive">{error}</p>
      </div>
    )
  }

  if (!simulation) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="flex items-center gap-2 text-muted-foreground">
          <Loader2 size={16} className="animate-spin" />
          <span>{t('result.loading')}</span>
        </div>
      </div>
    )
  }

  const isRunning = ['PENDING', 'GENERATING', 'SIMULATING', 'AGGREGATING'].includes(simulation.status)
  const pct = simulation.progress.total > 0 ? (simulation.progress.completed / simulation.progress.total) * 100 : 0

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b bg-card/80 backdrop-blur-sm sticky top-0 z-10 animate-in">
        <div className="max-w-7xl mx-auto px-6 h-14 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button onClick={() => navigate('/')} className="cursor-pointer font-serif text-xl tracking-tight text-muted-foreground hover:text-foreground transition-colors">
              {t('app.name')}
            </button>
            <Separator orientation="vertical" className="h-5" />
            <div>
              <h1 className="text-sm font-semibold tracking-tight">{simulation.input.product.name}</h1>
              <p className="text-[11px] text-muted-foreground">
                {[...new Set(simulation.input.adPlacements.map(p => p.platform))].join(' · ')} &middot; ¥{simulation.input.totalBudget.toLocaleString()} {t('result.budget')}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <StatusBadge status={simulation.status} t={t} />
            <LanguageSwitch />
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-6 py-8">
        {/* Progress */}
        {isRunning && (
          <div className="mb-10 animate-in stagger-1">
            <div className="flex justify-between text-xs mb-2.5 text-muted-foreground font-mono">
              <span>{t(`result.status.${simulation.status}`)}</span>
              <span>{simulation.progress.completed}/{simulation.progress.total}</span>
            </div>
            <Progress value={pct} className={cn('h-2', pct < 100 && '[&>[data-slot=progress-indicator]]:animate-pulse')} />

            {/* Plan Summary */}
            <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-4">
              {/* Product */}
              <Card>
                <CardContent className="pt-4 pb-4">
                  <p className="text-[10px] font-semibold uppercase tracking-wider mb-2 text-muted-foreground">{t('create.product.title')}</p>
                  <p className="text-sm font-medium">{simulation.input.product.brandName} · {simulation.input.product.name}</p>
                  <p className="text-xs mt-1 text-muted-foreground">
                    ¥{simulation.input.product.price} · {simulation.input.product.category}
                  </p>
                  <p className="text-xs mt-1.5 line-clamp-2 text-muted-foreground/70">
                    {simulation.input.product.sellingPoints}
                  </p>
                </CardContent>
              </Card>

              {/* Audience */}
              <Card>
                <CardContent className="pt-4 pb-4">
                  <p className="text-[10px] font-semibold uppercase tracking-wider mb-2 text-muted-foreground">{t('create.audience.title')}</p>
                  <p className="text-sm font-medium">
                    {simulation.input.targetAudience.gender === 'all' ? t('create.audience.genderAll') : simulation.input.targetAudience.gender === 'female' ? t('create.audience.genderFemale') : t('create.audience.genderMale')}
                    {simulation.input.targetAudience.ageRange && ` · ${simulation.input.targetAudience.ageRange[0]}-${simulation.input.targetAudience.ageRange[1]}${t('create.audience.ageMax').charAt(t('create.audience.ageMax').length - 1) === '\u9F84' ? '\u5C81' : ''}`}
                  </p>
                  {simulation.input.targetAudience.region && (
                    <p className="text-xs mt-1 text-muted-foreground">{simulation.input.targetAudience.region}</p>
                  )}
                </CardContent>
              </Card>

              {/* Placements */}
              <Card>
                <CardContent className="pt-4 pb-4">
                  <p className="text-[10px] font-semibold uppercase tracking-wider mb-2 text-muted-foreground">{t('create.placement.title')}</p>
                  <div className="space-y-1.5">
                    {simulation.input.adPlacements.map((p, i) => (
                      <div key={i} className="flex justify-between text-xs">
                        <span>{t(`create.platform.${p.platform}`)}</span>
                        <span className="font-mono text-muted-foreground">¥{p.budget.toLocaleString()}</span>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </div>

            {/* Simulation stages */}
            <div className="mt-6 flex items-center justify-center gap-3">
              {(['GENERATING', 'SIMULATING', 'AGGREGATING'] as const).map((stage, i) => {
                const isDone = ['GENERATING', 'SIMULATING', 'AGGREGATING'].indexOf(simulation.status) > i
                const isCurrent = simulation.status === stage
                return (
                  <div key={stage} className="flex items-center gap-3">
                    {i > 0 && <div className={cn('w-8 h-px', isDone ? 'bg-foreground' : 'bg-border')} />}
                    <div className="flex items-center gap-1.5">
                      <div className={cn(
                        'w-2 h-2 rounded-full',
                        isDone && 'bg-foreground',
                        isCurrent && 'bg-teal animate-pulse',
                        !isDone && !isCurrent && 'bg-border'
                      )} />
                      <span className={cn(
                        'text-[11px]',
                        isDone && 'text-foreground',
                        isCurrent && 'text-teal font-semibold',
                        !isDone && !isCurrent && 'text-muted-foreground'
                      )}>
                        {t(`result.status.${stage}`)}
                      </span>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {/* Results */}
        {simulation.status === 'COMPLETED' && simulation.results && (
          <>
            {simulation.results.topInsights && simulation.results.topInsights.length > 0 && (
              <InsightsBanner insights={simulation.results.topInsights} />
            )}
            <MetricsCards metrics={simulation.results.metrics} />
            <FunnelChart funnel={simulation.results.funnel} />
            {simulation.results.segmentInsights && simulation.results.segmentInsights.length > 0 && (
              <SegmentAnalysis segments={simulation.results.segmentInsights} />
            )}
            <DropOffReasons
              dropOffReasons={simulation.results.dropOffReasons}
              clusteredReasons={simulation.results.clusteredReasons}
            />

            {/* Agent List */}
            <section className="mt-8 animate-in stagger-5">
              <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-4">
                {t('result.agents.title')} <span className="font-mono text-muted-foreground/60">({agents.length})</span>
              </h2>
              <div className="space-y-2">
                {agents.slice(0, agentPage * agentPageSize).map(agent => (
                  <Card key={agent.id}
                    className="cursor-pointer transition-all hover:border-muted-foreground/40 hover:shadow-sm"
                    onClick={() => navigate(`/simulation/${id}/agent/${agent.id}`)}>
                    <CardContent className="py-3 flex justify-between items-center">
                      <div className="flex items-center gap-3">
                        <span className="font-medium text-sm">{agent.persona.name}</span>
                        <span className="text-xs text-muted-foreground font-mono">
                          {agent.persona.age} &middot; {agent.persona.gender} &middot; {agent.persona.income}
                        </span>
                      </div>
                      <div className="flex gap-1.5">
                        <FunnelDot label={t('result.agents.attention')} passed={agent.decisions?.attention.passed} />
                        <FunnelDot label={t('result.agents.click')} passed={agent.decisions?.click.passed} />
                        <FunnelDot label={t('result.agents.convert')} passed={agent.decisions?.conversion.passed} />
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
              {agents.length > agentPage * agentPageSize && (
                <Button
                  variant="outline"
                  className="w-full mt-3 border-dashed text-muted-foreground hover:text-foreground"
                  onClick={() => setAgentPage(p => p + 1)}>
                  <ChevronDown size={14} className="mr-1.5" />
                  {t('result.agents.showing', { shown: Math.min(agentPage * agentPageSize, agents.length), total: agents.length })}
                  {' · '}{t('result.agents.loadMore')}
                </Button>
              )}
            </section>
          </>
        )}

        {/* Failed */}
        {simulation.status === 'FAILED' && (
          <div className="text-center py-20 animate-fade">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-destructive/10 mb-4">
              <AlertCircle size={24} className="text-destructive" />
            </div>
            <p className="text-lg font-medium text-destructive">{t('result.failed.title')}</p>
            {simulation.errorMessage && (
              <p className="mt-3 text-sm max-w-lg mx-auto px-4 py-3 rounded-lg text-muted-foreground bg-destructive/5 border border-destructive/10">
                {simulation.errorMessage}
              </p>
            )}
            <Button onClick={() => navigate('/')} className="mt-6 bg-teal hover:bg-teal/90">
              <ArrowLeft size={14} className="mr-1.5" />
              {t('result.failed.retry')}
            </Button>
          </div>
        )}
      </main>
    </div>
  )
}

function StatusBadge({ status, t }: { status: string; t: (key: string) => string }) {
  const variants: Record<string, string> = {
    PENDING: 'bg-muted text-muted-foreground',
    GENERATING: 'bg-blue-50 text-blue-600',
    SIMULATING: 'bg-teal-light text-teal',
    AGGREGATING: 'bg-purple-50 text-purple-600',
    COMPLETED: 'bg-success-light text-success',
    FAILED: 'bg-error-light text-error',
  }
  return (
    <Badge variant="outline" className={cn('font-mono text-xs', variants[status] || variants.PENDING)}>
      {t(`result.status.${status}`)}
    </Badge>
  )
}

function FunnelDot({ label, passed }: { label: string; passed?: boolean }) {
  return (
    <div className={cn(
      'flex items-center gap-1.5 px-2 py-1 rounded text-xs',
      passed === undefined && 'bg-muted',
      passed === true && 'bg-success-light',
      passed === false && 'bg-error-light'
    )} title={label}>
      <span className={cn(
        'w-1.5 h-1.5 rounded-full',
        passed === undefined && 'bg-border',
        passed === true && 'bg-success',
        passed === false && 'bg-error'
      )} />
      <span className={cn(
        'font-mono text-[0.65rem]',
        passed === undefined && 'text-muted-foreground',
        passed === true && 'text-success',
        passed === false && 'text-error'
      )}>{label}</span>
    </div>
  )
}
