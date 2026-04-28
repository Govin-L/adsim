import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, type PlacementResult, type Simulation } from '@/api/client'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

interface Props {
  simulation: Simulation
  onUpdated: (simulation: Simulation) => void
}

type FormState = Record<number, { ctr: string; cvr: string; cpa: string }>

export default function CalibrationFormCard({ simulation, onUpdated }: Props) {
  const { t } = useTranslation()
  const placementResults = simulation.results?.placementResults ?? []
  const [saving, setSaving] = useState(false)
  const [formState, setFormState] = useState<FormState>(() => buildFormState(simulation))

  useEffect(() => {
    setFormState(buildFormState(simulation))
  }, [simulation])

  const hasPlacements = placementResults.length > 0
  const canSubmit = useMemo(() => {
    return placementResults.some((placement) => {
      const row = formState[placement.placementIndex]
      return Boolean(row?.ctr || row?.cvr || row?.cpa)
    })
  }, [formState, placementResults])

  if (!hasPlacements) return null

  const handleChange = (placementIndex: number, field: 'ctr' | 'cvr' | 'cpa', value: string) => {
    setFormState((current) => ({
      ...current,
      [placementIndex]: {
        ...current[placementIndex],
        [field]: value,
      },
    }))
  }

  const handleSubmit = async () => {
    if (!canSubmit || saving || !simulation.id) return
    setSaving(true)
    try {
      const updated = await api.updateCalibration(simulation.id, {
        placements: placementResults
          .map((placement) => {
            const row = formState[placement.placementIndex]
            return {
              placementIndex: placement.placementIndex,
              ctr: row?.ctr ? Number(row.ctr) / 100 : undefined,
              cvr: row?.cvr ? Number(row.cvr) / 100 : undefined,
              cpa: row?.cpa ? Number(row.cpa) : undefined,
            }
          })
          .filter((placement) => placement.ctr != null || placement.cvr != null || placement.cpa != null),
      })
      onUpdated(updated)
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="mb-8 animate-in stagger-5">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-4">
        {t('result.calibration.form.title')}
      </h2>
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-xs font-medium text-muted-foreground">
            {t('result.calibration.form.subtitle')}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {placementResults.map((placement) => (
            <PlacementCalibrationRow
              key={placement.placementIndex}
              placement={placement}
              row={formState[placement.placementIndex]}
              onChange={handleChange}
            />
          ))}
          <Button onClick={handleSubmit} disabled={!canSubmit || saving} className="bg-teal hover:bg-teal/90">
            {saving ? t('result.calibration.form.saving') : t('result.calibration.form.submit')}
          </Button>
        </CardContent>
      </Card>
    </section>
  )
}

function buildFormState(simulation: Simulation): FormState {
  const placementResults = simulation.results?.placementResults ?? []
  const calibrationLookup = new Map(
    (simulation.results?.calibration?.placements ?? []).map((placement) => [placement.placementIndex, placement]),
  )

  return Object.fromEntries(
    placementResults.map((placement) => {
      const calibration = calibrationLookup.get(placement.placementIndex)
      return [
        placement.placementIndex,
        {
          ctr: calibration?.actualMetrics.ctr != null ? String(calibration.actualMetrics.ctr * 100) : '',
          cvr: calibration?.actualMetrics.cvr != null ? String(calibration.actualMetrics.cvr * 100) : '',
          cpa: calibration?.actualMetrics.cpa != null ? String(calibration.actualMetrics.cpa) : '',
        },
      ]
    }),
  )
}

function PlacementCalibrationRow({
  placement,
  row,
  onChange,
}: {
  placement: PlacementResult
  row: { ctr: string; cvr: string; cpa: string }
  onChange: (placementIndex: number, field: 'ctr' | 'cvr' | 'cpa', value: string) => void
}) {
  const { t } = useTranslation()

  return (
    <div className="rounded-lg border bg-muted/20 px-3 py-3">
      <p className="text-sm font-medium">
        {t(`create.platform.${placement.placement.platform}`)} / {t(`create.placementType.${placement.placement.placementType}`)}
      </p>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mt-3">
        <Input
          type="number"
          placeholder={t('result.calibration.form.ctrPlaceholder')}
          value={row?.ctr ?? ''}
          onChange={(event) => onChange(placement.placementIndex, 'ctr', event.target.value)}
        />
        <Input
          type="number"
          placeholder={t('result.calibration.form.cvrPlaceholder')}
          value={row?.cvr ?? ''}
          onChange={(event) => onChange(placement.placementIndex, 'cvr', event.target.value)}
        />
        <Input
          type="number"
          placeholder={t('result.calibration.form.cpaPlaceholder')}
          value={row?.cpa ?? ''}
          onChange={(event) => onChange(placement.placementIndex, 'cpa', event.target.value)}
        />
      </div>
    </div>
  )
}
