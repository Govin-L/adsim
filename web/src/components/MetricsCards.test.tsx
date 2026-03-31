import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import MetricsCards from './MetricsCards'

describe('MetricsCards', () => {
  it('按模拟信号和业务估算拆分展示指标口径', () => {
    render(
      <MetricsCards
        metrics={{
          attentionRate: 0.51,
          ctr: 0.27,
          cvr: 0.19,
          overallConversionRate: 0.1,
          estimatedCPA: 128,
        }}
      />,
    )

    expect(screen.getByText('模拟信号')).toBeInTheDocument()
    expect(screen.getByText('业务估算')).toBeInTheDocument()
    expect(screen.getAllByText('模拟参考')).toHaveLength(4)
    expect(screen.getAllByText('估算值')).toHaveLength(1)
    expect(screen.getByText('¥128')).toBeInTheDocument()
  })
})
