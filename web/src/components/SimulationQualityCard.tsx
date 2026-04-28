import { useTranslation } from 'react-i18next'
import type { SimulationQuality } from '@/api/client'
import { AlertTriangle } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

interface Props {
  quality: SimulationQuality
}

export default function SimulationQualityCard({ quality }: Props) {
  const { t } = useTranslation()
  const hasWarnings = quality.warningCodes.length > 0

  return (
    <Card className="mb-8 animate-in stagger-2">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between gap-3">
          <CardTitle className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
            {t('result.quality.title')}
          </CardTitle>
          {hasWarnings && (
            <Badge variant="outline" className="border-amber/40 text-amber">
              <AlertTriangle size={12} className="mr-1" />
              {t('result.quality.warningBadge')}
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          <QualityMetric
            label={t('result.quality.requestedAgents')}
            value={String(quality.requestedAgents)}
          />
          <QualityMetric
            label={t('result.quality.generatedAgents')}
            value={String(quality.generatedAgents)}
          />
          <QualityMetric
            label={t('result.quality.generationSuccessRate')}
            value={pct(quality.generationSuccessRate)}
          />
          <QualityMetric
            label={t('result.quality.sampleSuccessRate')}
            value={pct(quality.sampleSuccessRate)}
          />
        </div>

        <p className="text-xs text-muted-foreground">
          {t('result.quality.samples', {
            simulated: quality.simulatedSamples,
            planned: quality.plannedSamples,
          })}
        </p>

        {(quality.warningCodes.length > 0 || quality.reasonabilityWarnings.length > 0) && (
          <div className="space-y-1.5 rounded-lg border border-amber/30 bg-amber/10 px-3 py-3">
            {quality.warningCodes.map((warningCode) => (
              <p key={warningCode} className="text-xs text-amber">
                {t(`result.quality.warning.${warningCode}`)}
              </p>
            ))}
            {quality.reasonabilityWarnings.map((warning) => (
              <p key={warning.code} className="text-xs text-amber">
                {warning.message}
              </p>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function QualityMetric({ label, value }: { label: string; value: string }) {
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
