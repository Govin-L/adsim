import { useTranslation } from 'react-i18next'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts'

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
    <section className="mb-8 p-5 rounded-xl animate-in stagger-3" style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}>
      <h2 className="text-xs font-semibold uppercase tracking-wider mb-5" style={{ color: 'var(--color-text-secondary)', letterSpacing: '0.08em' }}>
        {t('result.funnel.title')}
      </h2>
      <ResponsiveContainer width="100%" height={200}>
        <BarChart data={data} layout="vertical" margin={{ left: 10, right: 20 }}>
          <XAxis type="number" tick={{ fontSize: 11, fontFamily: 'var(--font-mono)', fill: '#a8a29e' }} axisLine={false} tickLine={false} />
          <YAxis type="category" dataKey="name" width={70} tick={{ fontSize: 12, fontFamily: 'var(--font-body)', fill: '#78716c' }} axisLine={false} tickLine={false} />
          <Tooltip
            formatter={(value: number) => [value, t('result.funnel.agents')]}
            contentStyle={{ fontFamily: 'var(--font-mono)', fontSize: '12px', borderRadius: '8px', border: '1px solid var(--color-border)' }}
          />
          <Bar dataKey="count" radius={[0, 6, 6, 0]} barSize={28}>
            {data.map((_, index) => (
              <Cell key={index} fill={COLORS[index]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </section>
  )
}
