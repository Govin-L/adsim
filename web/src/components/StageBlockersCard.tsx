import { useTranslation } from 'react-i18next'
import type { StageBlocker } from '@/api/client'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'

interface Props {
  blockers: StageBlocker[]
}

export default function StageBlockersCard({ blockers }: Props) {
  const { t } = useTranslation()

  if (!blockers.length) return null

  return (
    <section className="mb-8 animate-in stagger-3">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-4">
        {t('result.blockers.title')}
      </h2>
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-xs font-medium text-muted-foreground">
            {t('result.blockers.subtitle')}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {blockers.map((blocker) => (
            <div key={`${blocker.stage}-${blocker.factor}`}>
              <div className="flex justify-between items-start gap-3 mb-1.5">
                <div>
                  <p className="text-sm font-medium">{blocker.summary}</p>
                  <p className="text-[11px] text-muted-foreground">
                    {t(`result.blockers.stage.${blocker.stage}`)} · {blocker.factor}
                  </p>
                </div>
                <span className="text-xs font-mono text-muted-foreground">
                  {(blocker.share * 100).toFixed(0)}%
                </span>
              </div>
              <Progress value={blocker.share * 100} className="h-1.5" />
            </div>
          ))}
        </CardContent>
      </Card>
    </section>
  )
}
