import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import type { SegmentInsight } from '@/api/client'

interface Props {
  segments: SegmentInsight[]
}

export default function SegmentAnalysis({ segments }: Props) {
  const { t } = useTranslation()

  if (!segments || segments.length === 0) return null

  return (
    <section className="mb-8 animate-in stagger-4">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-4">
        {t('result.segments.title')}
      </h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {segments.map((insight) => (
          <Card key={insight.dimension}>
            <CardHeader className="pb-3">
              <CardTitle className="text-xs font-medium text-muted-foreground">
                {t(`result.segments.${insight.dimension}`)}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {insight.segments.map((seg) => {
                const pctValue = seg.conversionRate * 100
                return (
                  <div key={seg.label}>
                    <div className="flex justify-between items-center gap-2 mb-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm">{seg.label}</span>
                        {seg.highlight === 'highest' && (
                          <Badge variant="outline" className="bg-emerald-50 text-emerald-700 border-emerald-200 text-[10px] px-1.5 py-0">
                            {t('result.segments.highest')}
                          </Badge>
                        )}
                        {seg.highlight === 'lowest' && (
                          <Badge variant="destructive" className="text-[10px] px-1.5 py-0">
                            {t('result.segments.lowest')}
                          </Badge>
                        )}
                      </div>
                      <div className="flex items-center gap-2 text-xs text-muted-foreground font-mono shrink-0">
                        <span>{pctValue.toFixed(1)}%</span>
                        <span className="text-muted-foreground/50">
                          ({seg.convertedCount}/{seg.agentCount} {t('result.segments.agents')})
                        </span>
                      </div>
                    </div>
                    <Progress value={pctValue} className="h-1.5" />
                  </div>
                )
              })}
            </CardContent>
          </Card>
        ))}
      </div>
    </section>
  )
}
