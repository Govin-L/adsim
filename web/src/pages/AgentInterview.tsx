import { useEffect, useState, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { api, type Agent, type StageDecision } from '../api/client'
import LanguageSwitch from '../components/LanguageSwitch'

interface Message {
  role: 'user' | 'assistant'
  content: string
}

export default function AgentInterview() {
  const { t } = useTranslation()
  const { id, agentId } = useParams<{ id: string; agentId: string }>()
  const navigate = useNavigate()
  const [agent, setAgent] = useState<Agent | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [conversationId, setConversationId] = useState<string | undefined>()
  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (id && agentId) api.getAgent(id, agentId).then(setAgent)
  }, [id, agentId])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const send = async () => {
    if (!input.trim() || !id || !agentId || loading) return
    const userMessage = input.trim()
    setInput('')
    setMessages(prev => [...prev, { role: 'user', content: userMessage }])
    setLoading(true)
    try {
      const res = await api.interview(id, agentId, userMessage, conversationId)
      setConversationId(res.conversationId)
      setMessages(prev => [...prev, { role: 'assistant', content: res.reply }])
    } catch {
      setMessages(prev => [...prev, { role: 'assistant', content: t('interview.failed') }])
    } finally {
      setLoading(false)
    }
  }

  if (!agent) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: 'var(--color-bg)' }}>
        <p style={{ color: 'var(--color-text-tertiary)' }}>{t('result.loading')}</p>
      </div>
    )
  }

  const p = agent.persona
  const d = agent.decisions

  return (
    <div className="min-h-screen flex flex-col" style={{ background: 'var(--color-bg)' }}>
      {/* Header */}
      <header className="border-b shrink-0 animate-in" style={{ borderColor: 'var(--color-border)', background: 'var(--color-surface)' }}>
        <div className="max-w-3xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button onClick={() => navigate(`/simulation/${id}`)} className="text-xs cursor-pointer transition-colors"
              style={{ color: 'var(--color-text-tertiary)' }}
              onMouseEnter={e => e.currentTarget.style.color = 'var(--color-text)'}
              onMouseLeave={e => e.currentTarget.style.color = 'var(--color-text-tertiary)'}
            >
              &larr; {t('interview.backToResults')}
            </button>
          </div>
          <LanguageSwitch />
        </div>
      </header>

      {/* Agent Info */}
      <div className="shrink-0 border-b animate-in stagger-1" style={{ borderColor: 'var(--color-border)' }}>
        <div className="max-w-3xl mx-auto px-6 py-5">
          <div className="flex items-start justify-between">
            <div>
              <h1 className="text-xl font-semibold" style={{ letterSpacing: '-0.01em' }}>{p.name}</h1>
              <p className="text-xs mt-1.5 flex flex-wrap gap-x-3 gap-y-1" style={{ color: 'var(--color-text-tertiary)', fontFamily: 'var(--font-mono)' }}>
                <span>{p.age}y</span>
                <span>{p.gender}</span>
                <span>{p.income} {t('interview.income')}</span>
                <span>{t('interview.tier')} {p.cityTier} {t('interview.city')}</span>
              </p>
              <p className="text-xs mt-1" style={{ color: 'var(--color-text-tertiary)' }}>
                {t('interview.interests')}: {p.interests.join(', ')}
              </p>
            </div>
          </div>

          {/* Decision Summary */}
          {d && (
            <div className="mt-4 grid grid-cols-3 gap-2">
              <DecisionCard label={t('interview.decision.attention')} decision={d.attention} />
              <DecisionCard label={t('interview.decision.click')} decision={d.click} />
              <DecisionCard label={t('interview.decision.conversion')} decision={d.conversion} />
            </div>
          )}

          {agent.campaignState && (
            <div className="mt-4 grid grid-cols-2 md:grid-cols-3 gap-2">
              <MiniStat label={t('interview.campaign.placementsSeen')} value={String(agent.campaignState.placementsSeen)} />
              <MiniStat label={t('interview.campaign.clickedCount')} value={String(agent.campaignState.clickedCount)} />
              <MiniStat label={t('interview.campaign.fatigueScore')} value={String(agent.campaignState.fatigueScore)} />
            </div>
          )}

          {((agent.placementOutcomes && agent.placementOutcomes.length > 0) || (agent.placementDecisions && agent.placementDecisions.length > 0)) && (
            <div className="mt-4 space-y-2">
              <p className="text-xs uppercase tracking-wider" style={{ color: 'var(--color-text-tertiary)' }}>
                {t('interview.campaign.placementBreakdown')}
              </p>
              {(agent.placementOutcomes && agent.placementOutcomes.length > 0 ? agent.placementOutcomes : agent.placementDecisions ?? []).map((placement) => {
                const attention = 'attention' in placement ? placement.attention : placement.decisions.attention
                const click = 'click' in placement ? placement.click : placement.decisions.click
                const conversion = 'conversion' in placement ? placement.conversion : placement.decisions.conversion
                const traceMeta = 'exposureEvent' in placement ? placement.exposureEvent : undefined

                return (
                  <div
                    key={`${placement.placementIndex}-${placement.platform}-${placement.placementType}`}
                    className="rounded-lg px-3 py-2"
                    style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}
                  >
                    <p className="text-xs font-medium">
                      {t(`create.platform.${placement.platform}`)} / {t(`create.placementType.${placement.placementType}`)}
                    </p>
                    <p className="text-[11px] mt-1" style={{ color: 'var(--color-text-tertiary)' }}>
                      A {attention.passed ? 'Y' : 'N'} · C {click.passed ? 'Y' : 'N'} · V {conversion.passed ? 'Y' : 'N'}
                    </p>
                    {traceMeta && (
                      <p className="text-[10px] mt-1 break-words" style={{ color: 'var(--color-text-tertiary)', fontFamily: 'var(--font-mono)' }}>
                        seq {traceMeta.sequence} · {traceMeta.deliveryContext.source} · f{traceMeta.deliveryContext.frequency}
                        {traceMeta.deliveryContext.intentLevel != null ? ` · intent ${Math.round(traceMeta.deliveryContext.intentLevel * 100)}%` : ''}
                      </p>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>

      {/* Chat */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-3xl mx-auto px-6 py-6 space-y-4">
          {messages.length === 0 && (
            <div className="text-center py-16 animate-fade">
              <p className="text-sm" style={{ color: 'var(--color-text-tertiary)' }}>{t('interview.emptyHint')}</p>
              <p className="text-xs mt-2" style={{ color: 'var(--color-border)' }}>{t('interview.emptyExample')}</p>
            </div>
          )}
          {messages.map((msg, i) => (
            <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'} animate-fade`}>
              <div className={`max-w-[80%] px-4 py-3 text-sm leading-relaxed ${
                msg.role === 'user' ? 'rounded-2xl rounded-br-md' : 'rounded-2xl rounded-bl-md'
              }`} style={{
                background: msg.role === 'user' ? 'var(--color-text)' : 'var(--color-surface)',
                color: msg.role === 'user' ? 'var(--color-bg)' : 'var(--color-text)',
                border: msg.role === 'assistant' ? '1px solid var(--color-border)' : 'none',
              }}>
                {msg.content}
              </div>
            </div>
          ))}
          {loading && (
            <div className="flex justify-start animate-fade">
              <div className="px-4 py-3 rounded-2xl rounded-bl-md text-sm"
                style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', color: 'var(--color-text-tertiary)' }}>
                {t('interview.thinking')}
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Input */}
      <div className="shrink-0 border-t" style={{ borderColor: 'var(--color-border)', background: 'var(--color-surface)' }}>
        <div className="max-w-3xl mx-auto px-6 py-4 flex gap-3">
          <input
            type="text"
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && send()}
            placeholder={t('interview.placeholder')}
            disabled={loading}
            className="flex-1 rounded-lg px-4 py-2.5 text-sm outline-none transition-all disabled:opacity-50"
            style={{ border: '1px solid var(--color-border)', fontFamily: 'var(--font-body)' }}
            onFocus={e => e.target.style.borderColor = 'var(--color-text)'}
            onBlur={e => e.target.style.borderColor = 'var(--color-border)'}
          />
          <button onClick={send} disabled={loading || !input.trim()}
            className="px-5 py-2.5 rounded-lg text-sm font-medium cursor-pointer transition-opacity disabled:opacity-30 disabled:cursor-not-allowed"
            style={{ background: 'var(--color-text)', color: 'var(--color-bg)' }}
            onMouseEnter={e => e.currentTarget.style.opacity = '0.85'}
            onMouseLeave={e => e.currentTarget.style.opacity = '1'}
          >
            {t('interview.send')}
          </button>
        </div>
      </div>
    </div>
  )
}

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg px-3 py-2" style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}>
      <p className="text-[10px] uppercase tracking-wider" style={{ color: 'var(--color-text-tertiary)' }}>{label}</p>
      <p className="mt-1 text-sm font-semibold" style={{ fontFamily: 'var(--font-mono)' }}>{value}</p>
    </div>
  )
}

function DecisionCard({ label, decision }: { label: string; decision: StageDecision }) {
  const probabilityPct = decision.probability != null ? `${Math.round(decision.probability * 100)}%` : null
  const driverTags = (decision.passed ? decision.positiveFactors : decision.negativeFactors) ?? []

  return (
    <div className="p-3 rounded-lg" style={{
      background: decision.passed ? 'var(--color-success-bg)' : 'var(--color-error-bg)',
      border: `1px solid ${decision.passed ? '#bbf7d0' : '#fecaca'}`,
    }}>
      <div className="flex items-center justify-between gap-2 mb-1.5">
        <div className="flex items-center gap-2 min-w-0">
          <span className="w-1.5 h-1.5 rounded-full" style={{ background: decision.passed ? 'var(--color-success)' : 'var(--color-error)' }} />
          <span className="text-xs font-semibold uppercase tracking-wide" style={{ color: decision.passed ? 'var(--color-success)' : 'var(--color-error)' }}>
            {label}
          </span>
        </div>
        <div className="text-[10px] text-right" style={{ color: 'var(--color-text-tertiary)', fontFamily: 'var(--font-mono)' }}>
          {decision.likelihoodBand && <div>{decision.likelihoodBand}</div>}
          {probabilityPct && <div>{probabilityPct}</div>}
        </div>
      </div>
      <p className="text-xs leading-relaxed" style={{ color: 'var(--color-text-secondary)' }}>{decision.reasoning}</p>
      {driverTags.length > 0 && (
        <p className="text-[10px] mt-2 break-words" style={{ color: 'var(--color-text-tertiary)', fontFamily: 'var(--font-mono)' }}>
          {driverTags.join(' · ')}
        </p>
      )}
    </div>
  )
}
