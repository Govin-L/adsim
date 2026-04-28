import { useTranslation } from 'react-i18next'
import type { AgentUtilization } from '@/api/client'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

interface Props {
  utilization: AgentUtilization
}

export default function UtilizationCard({ utilization }: Props) {
  const { t } = useTranslation()

  return (
    <Card className="mb-8 animate-in stagger-2">
      <CardHeader className="pb-3">
        <CardTitle className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
          {t('result.utilization.title')}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          <UtilizationMetric label={t('result.utilization.uniqueAgentsReached')} value={String(utilization.uniqueAgentsReached)} />
          <UtilizationMetric label={t('result.utilization.uniqueReachRate')} value={pct(utilization.uniqueReachRate)} />
          <UtilizationMetric label={t('result.utilization.avgExposures')} value={utilization.averageExposuresPerReachedAgent.toFixed(1)} />
          <UtilizationMetric label={t('result.utilization.searchEligibleRate')} value={pct(utilization.searchEligibleRate)} />
        </div>

        {utilization.placementCoverage.length > 0 && (
          <div className="space-y-2">
            {utilization.placementCoverage.map((coverage) => (
              <div key={coverage.placementIndex} className="rounded-lg border bg-muted/20 px-3 py-2">
                <div className="flex items-center justify-between gap-3 text-xs">
                  <span className="font-medium">{t('result.utilization.placement', { index: coverage.placementIndex + 1 })}</span>
                  <span className="font-mono text-muted-foreground">{pct(coverage.uniqueReachRate)}</span>
                </div>
                <p className="mt-1 text-[11px] text-muted-foreground">
                  {t('result.utilization.coverageDetail', {
                    reached: coverage.uniqueAgentsReached,
                    eligible: coverage.eligibleAgents,
                    planned: coverage.plannedSamples,
                  })}
                </p>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function UtilizationMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border bg-muted/20 px-3 py-2">
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className="mt-1 text-sm font-semibold font-mono">{value}</p>
    </div>
  )
}

function pct(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}
