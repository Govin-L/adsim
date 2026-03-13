import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { ChevronDown, ChevronUp } from 'lucide-react'
import type { ClusteredDropOffReasons, ClusteredReason } from '@/api/client'

interface Props {
  dropOffReasons: {
    attentionToClick: { reason: string; count: number; percentage: number }[]
    clickToConversion: { reason: string; count: number; percentage: number }[]
  }
  clusteredReasons?: ClusteredDropOffReasons
}

export default function DropOffReasons({ dropOffReasons, clusteredReasons }: Props) {
  const { t } = useTranslation()

  return (
    <section className="mb-8 animate-in stagger-4">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-4">
        {t('result.dropOff.title')}
      </h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {clusteredReasons ? (
          <>
            <ClusteredReasonCard
              title={t('result.dropOff.noticedNotClicked')}
              reasons={clusteredReasons.attentionToClick}
            />
            <ClusteredReasonCard
              title={t('result.dropOff.clickedNotConverted')}
              reasons={clusteredReasons.clickToConversion}
            />
          </>
        ) : (
          <>
            <ReasonCard title={t('result.dropOff.noticedNotClicked')} reasons={dropOffReasons.attentionToClick} />
            <ReasonCard title={t('result.dropOff.clickedNotConverted')} reasons={dropOffReasons.clickToConversion} />
          </>
        )}
      </div>
    </section>
  )
}

// Legacy format
function ReasonCard({ title, reasons }: { title: string; reasons: { reason: string; percentage: number }[] }) {
  if (reasons.length === 0) return null

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-xs font-medium text-muted-foreground">{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {reasons.map((r, i) => (
          <div key={i}>
            <div className="flex justify-between items-baseline gap-3 mb-1.5">
              <span className="text-sm text-foreground">{r.reason}</span>
              <span className="text-xs font-medium shrink-0 font-mono text-muted-foreground">
                {(r.percentage * 100).toFixed(0)}%
              </span>
            </div>
            <Progress value={r.percentage * 100} className="h-1.5" />
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

// Clustered format with expandable quotes
function ClusteredReasonCard({ title, reasons }: { title: string; reasons: ClusteredReason[] }) {
  if (reasons.length === 0) return null

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-xs font-medium text-muted-foreground">{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {reasons.map((r, i) => (
          <ClusteredReasonItem key={i} reason={r} />
        ))}
      </CardContent>
    </Card>
  )
}

function ClusteredReasonItem({ reason }: { reason: ClusteredReason }) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(false)
  const hasQuotes = reason.quotes && reason.quotes.length > 0

  return (
    <div>
      <div className="flex justify-between items-baseline gap-3 mb-1.5">
        <span className="text-sm text-foreground">{reason.category}</span>
        <div className="flex items-center gap-2 shrink-0">
          <span className="text-xs font-mono text-muted-foreground">
            {(reason.percentage * 100).toFixed(0)}%
          </span>
          <span className="text-[10px] text-muted-foreground/60">({reason.count})</span>
        </div>
      </div>
      <Progress value={reason.percentage * 100} className="h-1.5" />
      {hasQuotes && (
        <div className="mt-1.5">
          <button
            onClick={() => setExpanded(!expanded)}
            className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
          >
            {expanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
            {expanded ? t('result.dropOff.collapseQuotes') : t('result.dropOff.expandQuotes')}
          </button>
          {expanded && (
            <div className="mt-2 space-y-1.5 pl-3 border-l-2 border-muted">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground mb-1">
                {t('result.dropOff.quotes')}
              </p>
              {reason.quotes.map((q, i) => (
                <p key={i} className="text-xs text-muted-foreground italic">
                  &ldquo;{q}&rdquo;
                </p>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
