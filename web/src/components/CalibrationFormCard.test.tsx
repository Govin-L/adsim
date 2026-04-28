import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Simulation } from '@/api/client'
import { api } from '@/api/client'
import CalibrationFormCard from './CalibrationFormCard'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: {
      ...actual.api,
      updateCalibration: vi.fn(),
    },
  }
})

describe('CalibrationFormCard', () => {
  const updateCalibrationMock = vi.mocked(api.updateCalibration)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('把表单里的百分比换算为小数后提交校准数据', async () => {
    const simulation = buildSimulation()
    const onUpdated = vi.fn()
    const updatedSimulation = {
      ...simulation,
      results: {
        ...simulation.results!,
        calibration: {
          placements: [],
          summary: {
            coverage: 0,
            averageCtrDelta: null,
            averageCvrDelta: null,
            averageCpaDelta: null,
          },
        },
      },
    }
    updateCalibrationMock.mockResolvedValue(updatedSimulation)

    render(<CalibrationFormCard simulation={simulation} onUpdated={onUpdated} />)

    fireEvent.change(screen.getByPlaceholderText('CTR %'), { target: { value: '8' } })
    fireEvent.change(screen.getByPlaceholderText('CVR %'), { target: { value: '3' } })
    fireEvent.change(screen.getByPlaceholderText('CPA'), { target: { value: '120' } })
    fireEvent.click(screen.getByRole('button', { name: '保存校准数据' }))

    await waitFor(() =>
      expect(updateCalibrationMock).toHaveBeenCalledWith('sim-1', {
        placements: [
          {
            placementIndex: 0,
            ctr: 0.08,
            cvr: 0.03,
            cpa: 120,
          },
        ],
      }),
    )

    expect(onUpdated).toHaveBeenCalledWith(updatedSimulation)
  })
})

function buildSimulation(): Simulation {
  return {
    id: 'sim-1',
    status: 'COMPLETED',
    progress: { total: 1, completed: 1 },
    errorMessage: null,
    input: {
      product: {
        brandName: '珀莱雅',
        name: '双抗精华',
        price: 299,
        category: '护肤',
        sellingPoints: '抗老修护',
        productStage: 'ESTABLISHED',
      },
      adPlacements: [
        {
          platform: 'xiaohongshu',
          placementType: 'INFO_FEED',
          objectives: ['CONVERSION'],
          format: 'VIDEO',
          budget: 100000,
          creativeDescription: '信息流视频',
        },
      ],
      totalBudget: 100000,
      targetAudience: {
        gender: 'female',
      },
    },
    rawInput: '',
    results: {
      totalAgents: 20,
      successfulAgents: 20,
      metrics: {
        attentionRate: 0.6,
        ctr: 0.05,
        cvr: 0.02,
        overallConversionRate: 0.01,
        estimatedCPA: 150,
      },
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
      funnel: {
        exposure: { count: 20, rate: 1 },
        attention: { count: 12, rate: 0.6 },
        click: { count: 1, rate: 0.05 },
        conversion: { count: 0, rate: 0.02 },
      },
      dropOffReasons: {
        attentionToClick: [],
        clickToConversion: [],
      },
      placementResults: [
        {
          placementIndex: 0,
          placement: {
            platform: 'xiaohongshu',
            placementType: 'INFO_FEED',
            objectives: ['CONVERSION'],
            format: 'VIDEO',
            budget: 100000,
            creativeDescription: '信息流视频',
          },
          totalAgents: 20,
          successfulAgents: 20,
          metrics: {
            attentionRate: 0.6,
            ctr: 0.05,
            cvr: 0.02,
            overallConversionRate: 0.01,
            estimatedCPA: 150,
          },
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
          funnel: {
            exposure: { count: 20, rate: 1 },
            attention: { count: 12, rate: 0.6 },
            click: { count: 1, rate: 0.05 },
            conversion: { count: 0, rate: 0.02 },
          },
          dropOffReasons: {
            attentionToClick: [],
            clickToConversion: [],
          },
        },
      ],
    },
    createdAt: '2026-04-01T00:00:00Z',
    completedAt: '2026-04-01T00:10:00Z',
  }
}
