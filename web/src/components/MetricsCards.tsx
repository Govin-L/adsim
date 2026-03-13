import { useTranslation } from 'react-i18next'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { HelpCircle } from 'lucide-react'

interface Props {
  metrics: {
    attentionRate: number
    ctr: number
    cvr: number
    overallConversionRate: number
    estimatedCPA: number | null
  }
}

const RATE_KEYS = new Set(['attentionRate', 'ctr', 'cvr', 'overall'])

export default function MetricsCards({ metrics }: Props) {
  const { t } = useTranslation()

  const cards = [
    { key: 'attentionRate', label: t('result.metrics.attentionRate'), value: pct(metrics.attentionRate), tip: t('result.metrics.attentionRateTip') },
    { key: 'ctr', label: t('result.metrics.ctr'), value: pct(metrics.ctr), tip: t('result.metrics.ctrTip') },
    { key: 'cvr', label: t('result.metrics.cvr'), value: pct(metrics.cvr), tip: t('result.metrics.cvrTip') },
    { key: 'overall', label: t('result.metrics.overallConversionRate'), value: pct(metrics.overallConversionRate), tip: t('result.metrics.overallConversionRateTip') },
    { key: 'cpa', label: t('result.metrics.estimatedCPALabel'), value: metrics.estimatedCPA != null ? `\u00a5${metrics.estimatedCPA < 1 ? metrics.estimatedCPA.toFixed(2) : metrics.estimatedCPA.toFixed(0)}` : '\u2014', tip: t('result.metrics.estimatedCPATip') },
  ]

  return (
    <TooltipProvider>
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-2">
        {cards.map((card, i) => (
          <Card key={card.key} className={`animate-in stagger-${i + 1} relative`}>
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
                {RATE_KEYS.has(card.key) && (
                  <Badge variant="outline" className="text-[9px] px-1 py-0 font-normal text-muted-foreground/70 border-muted-foreground/20">
                    {t('result.metrics.simulatedBadge')}
                  </Badge>
                )}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
      <p className="text-xs text-muted-foreground/60 mb-8 text-center">
        {t('result.metrics.simulatedDisclaimer')}
      </p>
    </TooltipProvider>
  )
}

function pct(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}
