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
  const cards = [
    { label: 'Attention Rate', value: pct(metrics.attentionRate) },
    { label: 'CTR', value: pct(metrics.ctr) },
    { label: 'CVR', value: pct(metrics.cvr) },
    { label: 'Overall Conv. Rate', value: pct(metrics.overallConversionRate) },
    { label: 'Est. CPA', value: metrics.estimatedCPA != null ? `¥${metrics.estimatedCPA.toFixed(0)}` : 'N/A' },
  ]

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4 mb-8">
      {cards.map(card => (
        <div key={card.label} className="p-4 border rounded-lg text-center">
          <div className="text-2xl font-bold">{card.value}</div>
          <div className="text-xs text-gray-500 mt-1">{card.label}</div>
        </div>
      ))}
    </div>
  )
}

function pct(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}
