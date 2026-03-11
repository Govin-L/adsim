import { useEffect, useState, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { api, type Agent } from '../api/client'

interface Message {
  role: 'user' | 'assistant'
  content: string
}

export default function AgentInterview() {
  const { id, agentId } = useParams<{ id: string; agentId: string }>()
  const navigate = useNavigate()
  const [agent, setAgent] = useState<Agent | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [conversationId, setConversationId] = useState<string | undefined>()
  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (id && agentId) {
      api.getAgent(id, agentId).then(setAgent)
    }
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
      setMessages(prev => [...prev, { role: 'assistant', content: 'Interview failed. Please try again.' }])
    } finally {
      setLoading(false)
    }
  }

  if (!agent) {
    return <div className="max-w-2xl mx-auto p-6 text-center text-gray-400">Loading...</div>
  }

  const decisions = agent.decisions

  return (
    <div className="max-w-2xl mx-auto p-6 flex flex-col" style={{ height: 'calc(100vh - 3rem)' }}>
      {/* Header */}
      <div className="mb-6">
        <button onClick={() => navigate(`/simulation/${id}`)} className="text-sm text-gray-400 hover:text-gray-600 mb-2">
          &larr; Back to results
        </button>
        <h1 className="text-xl font-bold">{agent.persona.name}</h1>
        <p className="text-sm text-gray-500">
          {agent.persona.age}y, {agent.persona.gender}, {agent.persona.income} income, Tier {agent.persona.cityTier} city
        </p>
        <p className="text-sm text-gray-500">
          Interests: {agent.persona.interests.join(', ')}
        </p>
      </div>

      {/* Decision Summary */}
      {decisions && (
        <div className="mb-6 p-4 bg-gray-50 rounded-lg text-sm space-y-2">
          <DecisionRow label="Attention" passed={decisions.attention.passed} reasoning={decisions.attention.reasoning} />
          <DecisionRow label="Click" passed={decisions.click.passed} reasoning={decisions.click.reasoning} />
          <DecisionRow label="Conversion" passed={decisions.conversion.passed} reasoning={decisions.conversion.reasoning} />
        </div>
      )}

      {/* Chat */}
      <div className="flex-1 overflow-y-auto space-y-4 mb-4">
        {messages.length === 0 && (
          <p className="text-gray-400 text-center py-8">
            Ask this agent anything about their decision.
            <br />
            <span className="text-sm">e.g. "Why didn't you click?" or "What would make you buy?"</span>
          </p>
        )}
        {messages.map((msg, i) => (
          <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-[80%] px-4 py-2 rounded-2xl ${
              msg.role === 'user' ? 'bg-black text-white' : 'bg-gray-100 text-gray-800'
            }`}>
              {msg.content}
            </div>
          </div>
        ))}
        {loading && (
          <div className="flex justify-start">
            <div className="px-4 py-2 bg-gray-100 rounded-2xl text-gray-400">Thinking...</div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="flex gap-2">
        <input
          type="text"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && send()}
          placeholder="Ask this agent..."
          className="flex-1 border rounded-lg px-4 py-2"
          disabled={loading}
        />
        <button
          onClick={send}
          disabled={loading || !input.trim()}
          className="px-6 py-2 bg-black text-white rounded-lg hover:bg-gray-800 disabled:bg-gray-300"
        >
          Send
        </button>
      </div>
    </div>
  )
}

function DecisionRow({ label, passed, reasoning }: { label: string; passed: boolean; reasoning: string }) {
  return (
    <div className="flex items-start gap-2">
      <span className={`mt-0.5 w-2 h-2 rounded-full shrink-0 ${passed ? 'bg-green-500' : 'bg-red-400'}`} />
      <div>
        <span className="font-medium">{label}:</span>{' '}
        <span className="text-gray-600">{reasoning}</span>
      </div>
    </div>
  )
}
