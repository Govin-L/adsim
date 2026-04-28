import { useTranslation } from 'react-i18next'
import type { ActionRecommendation } from '@/api/client'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

interface Props {
  recommendations: ActionRecommendation[]
}

export default function RecommendationsCard({ recommendations }: Props) {
  const { t } = useTranslation()

  if (!recommendations.length) return null

  return (
    <section className="mb-8 animate-in stagger-4">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-4">
        {t('result.recommendations.title')}
      </h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {recommendations.map((recommendation, index) => (
          <Card key={`${recommendation.title}-${index}`}>
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between gap-3">
                <CardTitle className="text-sm">{recommendation.title}</CardTitle>
                <Badge variant="outline" className="text-[10px] uppercase">
                  {t(`result.recommendations.priority.${recommendation.priority}`)}
                </Badge>
              </div>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground leading-relaxed">{recommendation.detail}</p>
              <p className="mt-3 text-[11px] text-muted-foreground/70">
                {t('result.recommendations.appliesTo')} {t(`result.blockers.stage.${recommendation.appliesTo}`)}
              </p>
            </CardContent>
          </Card>
        ))}
      </div>
    </section>
  )
}
