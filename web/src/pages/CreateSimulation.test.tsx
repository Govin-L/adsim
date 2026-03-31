import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ParsePlanResponse, SimulationInput } from '@/api/client'
import { api } from '@/api/client'
import { templates } from '@/data/templates'
import CreateSimulation from './CreateSimulation'

const mockNavigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

vi.mock('sonner', () => ({
  toast: {
    error: vi.fn(),
  },
}))

vi.mock('@/components/LanguageSwitch', () => ({
  default: () => <div data-testid="language-switch" />,
}))

vi.mock('@/components/LlmSettings', () => ({
  default: () => <div data-testid="llm-settings" />,
}))

vi.mock('@/components/BuildInfo', () => ({
  default: () => <div data-testid="build-info" />,
}))

vi.mock('@/api/client', () => ({
  api: {
    listSimulations: vi.fn(),
    parsePlan: vi.fn(),
    createSimulation: vi.fn(),
  },
}))

function cloneBeautyPlan(): SimulationInput {
  return JSON.parse(JSON.stringify(templates.find((template) => template.key === 'beauty')!.getInput()))
}

describe('CreateSimulation', () => {
  const listSimulationsMock = vi.mocked(api.listSimulations)
  const parsePlanMock = vi.mocked(api.parsePlan)

  beforeEach(() => {
    vi.clearAllMocks()
    mockNavigate.mockReset()
    listSimulationsMock.mockResolvedValue([])
  })

  it('在应用 patch 之前阻止继续提交，并在确认后更新当前方案', async () => {
    const mergedPlan = cloneBeautyPlan()
    mergedPlan.product.name = '小黑钻唇釉 Pro'

    const patch: ParsePlanResponse = {
      mergedPlan,
      changedFields: ['product.name'],
      warnings: ['预算未调整'],
      missingFields: [],
    }
    parsePlanMock.mockResolvedValue(patch)

    render(<CreateSimulation />)

    fireEvent.click(screen.getByText('完美日记 · 小黑钻唇釉'))
    expect(screen.getByDisplayValue('小黑钻唇釉')).toBeInTheDocument()

    const chatInput = screen.getByPlaceholderText('描述你的方案，或输入修改指令...')
    fireEvent.change(chatInput, { target: { value: '把产品名称改成小黑钻唇釉 Pro' } })
    fireEvent.keyDown(chatInput, { key: 'Enter', code: 'Enter' })

    await waitFor(() =>
      expect(parsePlanMock).toHaveBeenCalledWith(
        '把产品名称改成小黑钻唇釉 Pro',
        expect.objectContaining({
          brandAwareness: 'WELL_KNOWN',
          campaignGoal: 'ACQUISITION',
        }),
      ),
    )

    expect(await screen.findByText('待应用的方案修改')).toBeInTheDocument()
    expect(screen.getByText('product.name')).toBeInTheDocument()
    expect(screen.getAllByText(/预算未调整/)).toHaveLength(2)
    expect(screen.getByRole('button', { name: '开始模拟' })).toBeDisabled()
    expect(chatInput).toBeDisabled()
    expect(screen.queryByDisplayValue('小黑钻唇釉 Pro')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '应用修改' }))

    await waitFor(() => expect(screen.queryByText('待应用的方案修改')).not.toBeInTheDocument())
    expect(screen.getByDisplayValue('小黑钻唇釉 Pro')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '开始模拟' })).not.toBeDisabled()
  })

  it('忽略 patch 时保留原始方案，并恢复交互', async () => {
    const mergedPlan = cloneBeautyPlan()
    mergedPlan.product.brandName = '新品牌'

    const patch: ParsePlanResponse = {
      mergedPlan,
      changedFields: ['product.brandName'],
      warnings: [],
      missingFields: [],
    }
    parsePlanMock.mockResolvedValue(patch)

    render(<CreateSimulation />)

    fireEvent.click(screen.getByText('完美日记 · 小黑钻唇釉'))

    const chatInput = screen.getByPlaceholderText('描述你的方案，或输入修改指令...')
    fireEvent.change(chatInput, { target: { value: '把品牌名改成新品牌' } })
    fireEvent.keyDown(chatInput, { key: 'Enter', code: 'Enter' })

    expect(await screen.findByText('待应用的方案修改')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '忽略修改' }))

    await waitFor(() => expect(screen.queryByText('待应用的方案修改')).not.toBeInTheDocument())
    expect(screen.getByDisplayValue('完美日记')).toBeInTheDocument()
    expect(screen.queryByDisplayValue('新品牌')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '开始模拟' })).not.toBeDisabled()
    expect(chatInput).not.toBeDisabled()
  })
})
