import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Lightbulb } from 'lucide-react'
import type { InsightSummary } from '@/api/client'

interface Props {
  insights: InsightSummary[]
}

export default function InsightsBanner({ insights }: Props) {
  const { t } = useTranslation()

  if (!insights || insights.length === 0) return null

  return (
    <Card className="mb-8 animate-in border-amber-200 bg-amber-50/50 dark:border-amber-900 dark:bg-amber-950/20">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-sm font-semibold text-amber-800 dark:text-amber-300">
          <Lightbulb size={16} />
          {t('result.insights.title')}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="space-y-1.5">
          {insights.map((insight, i) => (
            <li key={i} className="flex items-start gap-2 text-sm text-amber-900 dark:text-amber-200">
              <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-amber-500" />
              {insight.text}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
