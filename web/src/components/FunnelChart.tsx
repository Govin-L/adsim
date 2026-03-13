import { useTranslation } from 'react-i18next'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

interface Props {
  funnel: {
    exposure: { count: number; rate: number }
    attention: { count: number; rate: number }
    click: { count: number; rate: number }
    conversion: { count: number; rate: number }
  }
}

const COLORS = ['#0c0a09', '#44403c', '#78716c', '#d97706']

export default function FunnelChart({ funnel }: Props) {
  const { t } = useTranslation()

  const data = [
    { name: t('result.funnel.exposure'), count: funnel.exposure.count },
    { name: t('result.funnel.attention'), count: funnel.attention.count },
    { name: t('result.funnel.click'), count: funnel.click.count },
    { name: t('result.funnel.conversion'), count: funnel.conversion.count },
  ]

  return (
    <Card className="mb-8 animate-in stagger-3">
      <CardHeader className="pb-3">
        <CardTitle className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
          {t('result.funnel.title')}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={200}>
          <BarChart data={data} layout="vertical" margin={{ left: 10, right: 20 }}>
            <XAxis type="number" tick={{ fontSize: 11, fill: '#a8a29e' }} axisLine={false} tickLine={false} />
            <YAxis type="category" dataKey="name" width={70} tick={{ fontSize: 12, fill: '#78716c' }} axisLine={false} tickLine={false} />
            <Tooltip
              formatter={(value) => [String(value), t('result.funnel.agents')]}
              contentStyle={{ fontSize: '12px', borderRadius: '8px', borderColor: 'var(--color-border)' }}
            />
            <Bar dataKey="count" radius={[0, 6, 6, 0]} barSize={28}>
              {data.map((_, index) => (
                <Cell key={index} fill={COLORS[index]} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  )
}
