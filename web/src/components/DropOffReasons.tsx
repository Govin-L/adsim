import { useTranslation } from 'react-i18next'

interface Props {
  dropOffReasons: {
    attentionToClick: { reason: string; count: number; percentage: number }[]
    clickToConversion: { reason: string; count: number; percentage: number }[]
  }
}

export default function DropOffReasons({ dropOffReasons }: Props) {
  const { t } = useTranslation()

  return (
    <section className="mb-8 animate-in stagger-4">
      <h2 className="text-xs font-semibold uppercase tracking-wider mb-4" style={{ color: 'var(--color-text-secondary)', letterSpacing: '0.08em' }}>
        {t('result.dropOff.title')}
      </h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <ReasonCard title={t('result.dropOff.noticedNotClicked')} reasons={dropOffReasons.attentionToClick} />
        <ReasonCard title={t('result.dropOff.clickedNotConverted')} reasons={dropOffReasons.clickToConversion} />
      </div>
    </section>
  )
}

function ReasonCard({ title, reasons }: { title: string; reasons: { reason: string; percentage: number }[] }) {
  if (reasons.length === 0) return null

  return (
    <div className="p-5 rounded-xl" style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}>
      <h3 className="text-xs font-medium mb-4" style={{ color: 'var(--color-text-tertiary)' }}>{title}</h3>
      <div className="space-y-4">
        {reasons.map((r, i) => (
          <div key={i}>
            <div className="flex justify-between items-baseline gap-3 mb-1.5">
              <span className="text-sm" style={{ color: 'var(--color-text)' }}>{r.reason}</span>
              <span className="text-xs font-medium shrink-0" style={{ fontFamily: 'var(--font-mono)', color: 'var(--color-text-tertiary)' }}>
                {(r.percentage * 100).toFixed(0)}%
              </span>
            </div>
            <div className="w-full h-1.5 rounded-full" style={{ background: 'var(--color-border-subtle)' }}>
              <div className="h-1.5 rounded-full transition-all duration-500" style={{ width: `${r.percentage * 100}%`, background: 'var(--color-text-tertiary)' }} />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
