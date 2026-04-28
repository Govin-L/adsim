import { useTranslation } from 'react-i18next'
import type { PlacementResult } from '@/api/client'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

interface Props {
  placementResults: PlacementResult[]
}

export default function PlacementResults({ placementResults }: Props) {
  const { t } = useTranslation()

  if (placementResults.length === 0) {
    return null
  }

  const rankedResults = [...placementResults].sort(
    (left, right) => (right.simulatedMetrics?.overallConversionRate ?? right.metrics.overallConversionRate) - (left.simulatedMetrics?.overallConversionRate ?? left.metrics.overallConversionRate),
  )

  return (
    <section className="mb-8 animate-in stagger-3">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-4">
        {t('result.placements.title')}
      </h2>
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        {rankedResults.map((result) => (
          <Card key={`${result.placementIndex}-${result.placement.platform}-${result.placement.placementType}`}>
            <CardHeader className="pb-3">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <CardTitle className="text-sm">
                    {t(`create.platform.${result.placement.platform}`)}
                  </CardTitle>
                  <p className="text-xs text-muted-foreground mt-1">
                    {t(`create.placementType.${result.placement.placementType}`)}
                  </p>
                </div>
                <Badge variant="outline" className="font-mono text-[11px]">
                  ¥{result.placement.budget.toLocaleString()}
                </Badge>
              </div>
            </CardHeader>
            <CardContent>
              <p className="text-[11px] text-muted-foreground">
                {t('result.placements.samples', {
                  successful: result.successfulAgents,
                  total: result.totalAgents,
                })}
              </p>
              <div className="grid grid-cols-2 gap-3 mt-4">
                <PlacementMetric label={t('result.metrics.attentionRate')} value={pct(result.simulatedMetrics?.attentionRate ?? result.metrics.attentionRate)} />
                <PlacementMetric label={t('result.metrics.ctr')} value={pct(result.simulatedMetrics?.ctr ?? result.metrics.ctr)} />
                <PlacementMetric label={t('result.metrics.cvr')} value={pct(result.simulatedMetrics?.cvr ?? result.metrics.cvr)} />
                <PlacementMetric
                  label={t('result.metrics.estimatedCPALabel')}
                  value={
                    (result.estimatedMetrics?.estimatedCPA ?? result.metrics.estimatedCPA) != null
                      ? `¥${(result.estimatedMetrics?.estimatedCPA ?? result.metrics.estimatedCPA)! < 1 ? (result.estimatedMetrics?.estimatedCPA ?? result.metrics.estimatedCPA)!.toFixed(2) : (result.estimatedMetrics?.estimatedCPA ?? result.metrics.estimatedCPA)!.toFixed(0)}`
                      : '—'
                  }
                />
              </div>
              {result.topInsights && result.topInsights.length > 0 && (
                <p className="mt-4 text-xs leading-relaxed text-muted-foreground">
                  {result.topInsights[0].text}
                </p>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </section>
  )
}

function PlacementMetric({ label, value }: { label: string; value: string }) {
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
