import { useTranslation } from 'react-i18next'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { HelpCircle } from 'lucide-react'

import type { EstimatedBusinessMetrics, SimulatedFunnelMetrics } from '@/api/client'

interface Props {
  simulatedMetrics?: SimulatedFunnelMetrics
  estimatedMetrics?: EstimatedBusinessMetrics
  metrics?: {
    attentionRate: number
    ctr: number
    cvr: number
    overallConversionRate: number
    estimatedCPA: number | null
  }
}

export default function MetricsCards({ simulatedMetrics, estimatedMetrics, metrics }: Props) {
  const { t } = useTranslation()

  const resolvedSimulated = simulatedMetrics ?? (metrics ? {
    attentionRate: metrics.attentionRate,
    ctr: metrics.ctr,
    cvr: metrics.cvr,
    overallConversionRate: metrics.overallConversionRate,
  } : undefined)
  const resolvedEstimated = estimatedMetrics ?? (metrics ? {
    estimatedImpressions: 0,
    estimatedViewers: 0,
    estimatedConversions: 0,
    estimatedCPA: metrics.estimatedCPA,
  } : undefined)

  if (!resolvedSimulated || !resolvedEstimated) return null

  const sections = [
    {
      title: t('result.metrics.simulatedSection'),
      disclaimer: t('result.metrics.simulatedDisclaimer'),
      badge: t('result.metrics.simulatedBadge'),
      cards: [
        { key: 'attentionRate', label: t('result.metrics.attentionRate'), value: pct(resolvedSimulated.attentionRate), tip: t('result.metrics.attentionRateTip') },
        { key: 'ctr', label: t('result.metrics.ctr'), value: pct(resolvedSimulated.ctr), tip: t('result.metrics.ctrTip') },
        { key: 'cvr', label: t('result.metrics.cvr'), value: pct(resolvedSimulated.cvr), tip: t('result.metrics.cvrTip') },
        { key: 'overall', label: t('result.metrics.overallConversionRate'), value: pct(resolvedSimulated.overallConversionRate), tip: t('result.metrics.overallConversionRateTip') },
      ],
    },
    {
      title: t('result.metrics.estimatedSection'),
      disclaimer: t('result.metrics.estimatedDisclaimer'),
      badge: t('result.metrics.estimatedBadge'),
      cards: [
        {
          key: 'cpa',
          label: t('result.metrics.estimatedCPALabel'),
          value: resolvedEstimated.estimatedCPA != null ? `\u00a5${resolvedEstimated.estimatedCPA < 1 ? resolvedEstimated.estimatedCPA.toFixed(2) : resolvedEstimated.estimatedCPA.toFixed(0)}` : '\u2014',
          tip: t('result.metrics.estimatedCPATip'),
        },
      ],
    },
  ]

  return (
    <TooltipProvider>
      <div className="space-y-4 mb-8">
        {sections.map((section, sectionIndex) => (
          <section key={section.title}>
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
                {section.title}
              </span>
              <span className="text-[11px] text-muted-foreground/70">
                {section.disclaimer}
              </span>
            </div>
            <div className={`grid gap-3 ${section.cards.length === 1 ? 'grid-cols-1 sm:grid-cols-1 lg:grid-cols-1' : 'grid-cols-2 sm:grid-cols-2 lg:grid-cols-4'}`}>
              {section.cards.map((card, i) => (
                <Card key={card.key} className={`animate-in stagger-${sectionIndex * 3 + i + 1} relative`}>
                  <CardContent className="pt-4 pb-4">
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <button className="absolute top-3 right-3 text-muted-foreground hover:text-foreground transition-colors">
                          <HelpCircle size={14} />
                        </button>
                      </TooltipTrigger>
                      <TooltipContent side="top" className="max-w-[200px]">
                        {card.tip}
                      </TooltipContent>
                    </Tooltip>
                    <div className="text-2xl font-semibold tracking-tight font-mono">
                      {card.value}
                    </div>
                    <div className="mt-1.5 flex items-center gap-1.5">
                      <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                        {card.label}
                      </span>
                      <Badge variant="outline" className="text-[9px] px-1 py-0 font-normal text-muted-foreground/70 border-muted-foreground/20">
                        {section.badge}
                      </Badge>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </section>
        ))}
      </div>
    </TooltipProvider>
  )
}

function pct(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}
