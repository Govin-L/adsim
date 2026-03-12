import { useTranslation } from 'react-i18next'

interface Props {
  metrics: {
    attentionRate: number
    ctr: number
    cvr: number
    overallConversionRate: number
    estimatedCPA: number | null
  }
}

export default function MetricsCards({ metrics }: Props) {
  const { t } = useTranslation()

  const cards = [
    { key: 'attentionRate', label: t('result.metrics.attentionRate'), value: pct(metrics.attentionRate) },
    { key: 'ctr', label: t('result.metrics.ctr'), value: pct(metrics.ctr) },
    { key: 'cvr', label: t('result.metrics.cvr'), value: pct(metrics.cvr) },
    { key: 'overall', label: t('result.metrics.overallConversionRate'), value: pct(metrics.overallConversionRate) },
    { key: 'cpa', label: t('result.metrics.estimatedCPA'), value: metrics.estimatedCPA != null ? `¥${metrics.estimatedCPA.toFixed(0)}` : '—' },
  ]

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-8">
      {cards.map((card, i) => (
        <div key={card.key}
          className={`p-4 rounded-xl animate-in stagger-${i + 1}`}
          style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}>
          <div className="text-2xl font-semibold tracking-tight" style={{ fontFamily: 'var(--font-mono)' }}>
            {card.value}
          </div>
          <div className="mt-1.5 text-xs font-medium uppercase tracking-wider" style={{ color: 'var(--color-text-tertiary)' }}>
            {card.label}
          </div>
        </div>
      ))}
    </div>
  )
}

function pct(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}
