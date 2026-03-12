import { useState } from 'react'
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
    { key: 'attentionRate', label: t('result.metrics.attentionRate'), value: pct(metrics.attentionRate), tip: t('result.metrics.attentionRateTip') },
    { key: 'ctr', label: t('result.metrics.ctr'), value: pct(metrics.ctr), tip: t('result.metrics.ctrTip') },
    { key: 'cvr', label: t('result.metrics.cvr'), value: pct(metrics.cvr), tip: t('result.metrics.cvrTip') },
    { key: 'overall', label: t('result.metrics.overallConversionRate'), value: pct(metrics.overallConversionRate), tip: t('result.metrics.overallConversionRateTip') },
    { key: 'cpa', label: t('result.metrics.estimatedCPA'), value: metrics.estimatedCPA != null ? `¥${metrics.estimatedCPA < 1 ? metrics.estimatedCPA.toFixed(2) : metrics.estimatedCPA.toFixed(0)}` : '—', tip: t('result.metrics.estimatedCPATip') },
  ]

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-8">
      {cards.map((card, i) => (
        <MetricCard key={card.key} card={card} index={i} />
      ))}
    </div>
  )
}

function MetricCard({ card, index }: { card: { key: string; label: string; value: string; tip: string }; index: number }) {
  const [showTip, setShowTip] = useState(false)
  return (
    <div
      className={`p-4 rounded-xl animate-in stagger-${index + 1} relative`}
      style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}>
      <button
        className="absolute top-2.5 right-2.5 w-4 h-4 rounded-full flex items-center justify-center text-[10px] cursor-pointer transition-colors"
        style={{ color: 'var(--color-text-tertiary)', border: '1px solid var(--color-border)' }}
        onMouseEnter={() => setShowTip(true)}
        onMouseLeave={() => setShowTip(false)}
        onClick={() => setShowTip(s => !s)}
      >?</button>
      {showTip && (
        <div className="absolute top-8 right-0 z-10 px-3 py-2 rounded-lg text-xs max-w-[200px] shadow-lg"
          style={{ background: 'var(--color-text)', color: 'var(--color-bg)' }}>
          {card.tip}
        </div>
      )}
      <div className="text-2xl font-semibold tracking-tight" style={{ fontFamily: 'var(--font-mono)' }}>
        {card.value}
      </div>
      <div className="mt-1.5 text-xs font-medium uppercase tracking-wider" style={{ color: 'var(--color-text-tertiary)' }}>
        {card.label}
      </div>
    </div>
  )
}

function pct(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}
