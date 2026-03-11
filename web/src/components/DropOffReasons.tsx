interface Props {
  dropOffReasons: {
    attentionToClick: { reason: string; count: number; percentage: number }[]
    clickToConversion: { reason: string; count: number; percentage: number }[]
  }
}

export default function DropOffReasons({ dropOffReasons }: Props) {
  return (
    <section className="mb-8">
      <h2 className="text-lg font-semibold mb-4">Drop-off Reasons</h2>
      <div className="grid grid-cols-2 gap-6">
        <ReasonList title="Noticed but didn't click" reasons={dropOffReasons.attentionToClick} />
        <ReasonList title="Clicked but didn't convert" reasons={dropOffReasons.clickToConversion} />
      </div>
    </section>
  )
}

function ReasonList({ title, reasons }: { title: string; reasons: { reason: string; percentage: number }[] }) {
  if (reasons.length === 0) return null

  return (
    <div>
      <h3 className="text-sm font-medium text-gray-500 mb-3">{title}</h3>
      <div className="space-y-3">
        {reasons.map((r, i) => (
          <div key={i}>
            <div className="flex justify-between text-sm mb-1">
              <span className="text-gray-700">{r.reason}</span>
              <span className="text-gray-400">{(r.percentage * 100).toFixed(0)}%</span>
            </div>
            <div className="w-full bg-gray-100 rounded-full h-2">
              <div className="bg-gray-400 rounded-full h-2" style={{ width: `${r.percentage * 100}%` }} />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
