import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts'

interface Props {
  funnel: {
    exposure: { count: number; rate: number }
    attention: { count: number; rate: number }
    click: { count: number; rate: number }
    conversion: { count: number; rate: number }
  }
}

const COLORS = ['#374151', '#6B7280', '#9CA3AF', '#D1D5DB']

export default function FunnelChart({ funnel }: Props) {
  const data = [
    { name: 'Exposure', count: funnel.exposure.count },
    { name: 'Attention', count: funnel.attention.count },
    { name: 'Click', count: funnel.click.count },
    { name: 'Conversion', count: funnel.conversion.count },
  ]

  return (
    <section className="mb-8">
      <h2 className="text-lg font-semibold mb-4">Conversion Funnel</h2>
      <ResponsiveContainer width="100%" height={250}>
        <BarChart data={data} layout="vertical" margin={{ left: 20 }}>
          <XAxis type="number" />
          <YAxis type="category" dataKey="name" width={80} />
          <Tooltip formatter={(value: number) => [value, 'Agents']} />
          <Bar dataKey="count" radius={[0, 4, 4, 0]}>
            {data.map((_, index) => (
              <Cell key={index} fill={COLORS[index]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </section>
  )
}
