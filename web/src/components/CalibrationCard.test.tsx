import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import CalibrationCard from './CalibrationCard'

describe('CalibrationCard', () => {
  it('展示 placement prior 护栏快照', () => {
    render(
      <CalibrationCard
        calibration={{
          summary: {
            coverage: 1,
            averageCtrDelta: 0.03,
            averageCvrDelta: 0.01,
            averageCpaDelta: -30,
          },
          placements: [
            {
              placementIndex: 0,
              actualMetrics: { ctr: 0.08, cvr: 0.03, cpa: 120 },
              simulatedMetrics: {
                attentionRate: 0.6,
                ctr: 0.05,
                cvr: 0.02,
                overallConversionRate: 0.01,
              },
              estimatedMetrics: {
                estimatedImpressions: 2500000,
                estimatedViewers: 1250000,
                estimatedConversions: 833.33,
                estimatedCPA: 150,
              },
              prior: {
                baseAttention: 0.6,
                baseClick: 0.08,
                baseConversion: 0.03,
                calibrationCount: 3,
              },
              deltas: {
                ctrDelta: 0.03,
                cvrDelta: 0.01,
                cpaDelta: -30,
              },
            },
          ],
        }}
      />,
    )

    expect(screen.getByText('当前 prior 护栏')).toBeInTheDocument()
    expect(screen.getByText('基于 3 次校准沉淀的当前收敛基线')).toBeInTheDocument()
    expect(screen.getByText('60.0%')).toBeInTheDocument()
    expect(screen.getByText('8.0%')).toBeInTheDocument()
    expect(screen.getByText('3.0%')).toBeInTheDocument()
  })
})
