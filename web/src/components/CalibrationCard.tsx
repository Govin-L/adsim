import { useTranslation } from 'react-i18next'
import type { CalibrationResult, PlacementPriorSnapshot } from '@/api/client'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

interface Props {
  calibration: CalibrationResult
}

export default function CalibrationCard({ calibration }: Props) {
  const { t } = useTranslation()

  if (!calibration.placements.length) return null

  return (
    <section className="mb-8 animate-in stagger-5">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-4">
        {t('result.calibration.title')}
      </h2>
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-xs font-medium text-muted-foreground">
            {t('result.calibration.subtitle', { coverage: calibration.summary.coverage })}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {calibration.placements.map((placement) => (
            <div key={placement.placementIndex} className="rounded-lg border bg-muted/20 px-3 py-3">
              <p className="text-sm font-medium">
                {t('result.calibration.placement', { index: placement.placementIndex + 1 })}
              </p>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mt-3 text-xs">
                <DeltaItem
                  label="CTR"
                  actual={placement.actualMetrics.ctr}
                  simulated={placement.simulatedMetrics.ctr}
                  delta={placement.deltas.ctrDelta}
                />
                <DeltaItem
                  label="CVR"
                  actual={placement.actualMetrics.cvr}
                  simulated={placement.simulatedMetrics.cvr}
                  delta={placement.deltas.cvrDelta}
                />
                <DeltaItem
                  label="CPA"
                  actual={placement.actualMetrics.cpa}
                  simulated={placement.estimatedMetrics?.estimatedCPA}
                  delta={placement.deltas.cpaDelta}
                  currency
                />
              </div>
              {placement.prior && (
                <PriorGuardrail prior={placement.prior} />
              )}
            </div>
          ))}
        </CardContent>
      </Card>
    </section>
  )
}

function DeltaItem({
  label,
  actual,
  simulated,
  delta,
  currency = false,
}: {
  label: string
  actual?: number | null
  simulated?: number | null
  delta?: number | null
  currency?: boolean
}) {
  const { t } = useTranslation()

  return (
    <div>
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className="mt-1 text-muted-foreground">
        {t('result.calibration.actual')} {formatValue(actual, currency)} / {t('result.calibration.simulated')} {formatValue(simulated, currency)}
      </p>
      <p className="mt-1 font-mono">{formatDelta(t, delta, currency)}</p>
    </div>
  )
}

function PriorGuardrail({ prior }: { prior: PlacementPriorSnapshot }) {
  const { t } = useTranslation()

  return (
    <div className="mt-3 rounded-md border border-dashed bg-background/60 px-3 py-2">
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
        {t('result.calibration.prior.title')}
      </p>
      <p className="mt-1 text-xs text-muted-foreground">
        {t('result.calibration.prior.subtitle', { count: prior.calibrationCount })}
      </p>
      <div className="mt-2 grid grid-cols-1 md:grid-cols-3 gap-2 text-xs">
        <PriorMetric label={t('result.calibration.prior.attention')} value={prior.baseAttention} />
        <PriorMetric label={t('result.calibration.prior.ctr')} value={prior.baseClick} />
        <PriorMetric label={t('result.calibration.prior.cvr')} value={prior.baseConversion} />
      </div>
    </div>
  )
}

function PriorMetric({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className="mt-1 font-mono">{(value * 100).toFixed(1)}%</p>
    </div>
  )
}

function formatValue(value?: number | null, currency = false): string {
  if (value == null) return '—'
  return currency ? `¥${value.toFixed(2)}` : `${(value * 100).toFixed(1)}%`
}

function formatDelta(t: (key: string) => string, value?: number | null, currency = false): string {
  if (value == null) return 'Δ —'
  const prefix = value > 0 ? '+' : ''
  return currency
    ? `${t('result.calibration.delta')} ${prefix}¥${value.toFixed(2)}`
    : `${t('result.calibration.delta')} ${prefix}${(value * 100).toFixed(1)}%`
}
