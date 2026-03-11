import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type CreateSimulationRequest } from '../api/client'

export default function CreateSimulation() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState<CreateSimulationRequest>({
    product: { name: '', price: 0, category: '', description: '' },
    creative: { description: '', format: 'VIDEO' },
    platform: 'xiaohongshu',
    budget: 200000,
    targetAudience: { ageRange: [18, 35], gender: 'female', interests: [] },
    agentCount: 200,
  })

  const [interestInput, setInterestInput] = useState('')

  const updateProduct = (field: string, value: string | number) => {
    setForm(f => ({ ...f, product: { ...f.product, [field]: value } }))
  }

  const updateCreative = (field: string, value: string) => {
    setForm(f => ({ ...f, creative: { ...f.creative, [field]: value } }))
  }

  const updateAudience = (field: string, value: unknown) => {
    setForm(f => ({ ...f, targetAudience: { ...f.targetAudience, [field]: value } }))
  }

  const addInterest = () => {
    if (!interestInput.trim()) return
    updateAudience('interests', [...(form.targetAudience.interests || []), interestInput.trim()])
    setInterestInput('')
  }

  const removeInterest = (index: number) => {
    const interests = [...(form.targetAudience.interests || [])]
    interests.splice(index, 1)
    updateAudience('interests', interests)
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      const simulation = await api.createSimulation(form)
      navigate(`/simulation/${simulation.id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create simulation')
      setLoading(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto p-6">
      <h1 className="text-3xl font-bold mb-2">AdSim</h1>
      <p className="text-gray-500 mb-8">Simulate before you spend.</p>

      {error && (
        <div className="mb-6 p-4 bg-red-50 text-red-600 rounded-lg text-sm">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-8">
        {/* Product */}
        <section>
          <h2 className="text-lg font-semibold mb-4">Product</h2>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm text-gray-600 mb-1">Name</label>
              <input
                type="text"
                required
                value={form.product.name}
                onChange={e => updateProduct('name', e.target.value)}
                placeholder="e.g. Anti-aging Serum"
                className="w-full border rounded-lg px-3 py-2"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">Price (¥)</label>
              <input
                type="number"
                required
                min={0}
                value={form.product.price || ''}
                onChange={e => updateProduct('price', Number(e.target.value))}
                placeholder="299"
                className="w-full border rounded-lg px-3 py-2"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">Category</label>
              <input
                type="text"
                required
                value={form.product.category}
                onChange={e => updateProduct('category', e.target.value)}
                placeholder="e.g. Skincare"
                className="w-full border rounded-lg px-3 py-2"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">Description</label>
              <input
                type="text"
                value={form.product.description || ''}
                onChange={e => updateProduct('description', e.target.value)}
                placeholder="Brief product description"
                className="w-full border rounded-lg px-3 py-2"
              />
            </div>
          </div>
        </section>

        {/* Creative */}
        <section>
          <h2 className="text-lg font-semibold mb-4">Ad Creative</h2>
          <div>
            <label className="block text-sm text-gray-600 mb-1">Creative Description</label>
            <textarea
              required
              rows={3}
              value={form.creative.description}
              onChange={e => updateCreative('description', e.target.value)}
              placeholder="Describe your ad creative: format, key message, visual style..."
              className="w-full border rounded-lg px-3 py-2"
            />
          </div>
          <div className="mt-3">
            <label className="block text-sm text-gray-600 mb-1">Format</label>
            <select
              value={form.creative.format}
              onChange={e => updateCreative('format', e.target.value)}
              className="border rounded-lg px-3 py-2"
            >
              <option value="VIDEO">Video</option>
              <option value="IMAGE">Image</option>
              <option value="TEXT">Text</option>
            </select>
          </div>
        </section>

        {/* Campaign */}
        <section>
          <h2 className="text-lg font-semibold mb-4">Campaign</h2>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm text-gray-600 mb-1">Platform</label>
              <select
                value={form.platform}
                onChange={e => setForm(f => ({ ...f, platform: e.target.value }))}
                className="w-full border rounded-lg px-3 py-2"
              >
                <option value="xiaohongshu">Xiaohongshu</option>
                <option value="douyin" disabled>Douyin (coming soon)</option>
                <option value="wechat" disabled>WeChat Moments (coming soon)</option>
              </select>
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">Monthly Budget (¥)</label>
              <input
                type="number"
                required
                min={1}
                value={form.budget || ''}
                onChange={e => setForm(f => ({ ...f, budget: Number(e.target.value) }))}
                placeholder="200000"
                className="w-full border rounded-lg px-3 py-2"
              />
            </div>
          </div>
        </section>

        {/* Target Audience */}
        <section>
          <h2 className="text-lg font-semibold mb-4">Target Audience</h2>
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-sm text-gray-600 mb-1">Gender</label>
              <select
                value={form.targetAudience.gender}
                onChange={e => updateAudience('gender', e.target.value)}
                className="w-full border rounded-lg px-3 py-2"
              >
                <option value="all">All</option>
                <option value="female">Female</option>
                <option value="male">Male</option>
              </select>
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">Age Min</label>
              <input
                type="number"
                min={12}
                max={65}
                value={form.targetAudience.ageRange?.[0] || 18}
                onChange={e => updateAudience('ageRange', [Number(e.target.value), form.targetAudience.ageRange?.[1] || 65])}
                className="w-full border rounded-lg px-3 py-2"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">Age Max</label>
              <input
                type="number"
                min={12}
                max={65}
                value={form.targetAudience.ageRange?.[1] || 65}
                onChange={e => updateAudience('ageRange', [form.targetAudience.ageRange?.[0] || 18, Number(e.target.value)])}
                className="w-full border rounded-lg px-3 py-2"
              />
            </div>
          </div>
          <div className="mt-3">
            <label className="block text-sm text-gray-600 mb-1">Interests</label>
            <div className="flex gap-2">
              <input
                type="text"
                value={interestInput}
                onChange={e => setInterestInput(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), addInterest())}
                placeholder="Add interest tag"
                className="flex-1 border rounded-lg px-3 py-2"
              />
              <button type="button" onClick={addInterest} className="px-4 py-2 bg-gray-100 rounded-lg hover:bg-gray-200">
                Add
              </button>
            </div>
            <div className="flex flex-wrap gap-2 mt-2">
              {form.targetAudience.interests?.map((interest, i) => (
                <span key={i} className="px-3 py-1 bg-blue-50 text-blue-700 rounded-full text-sm flex items-center gap-1">
                  {interest}
                  <button type="button" onClick={() => removeInterest(i)} className="hover:text-blue-900">&times;</button>
                </span>
              ))}
            </div>
          </div>
        </section>

        {/* Agent Count */}
        <section>
          <h2 className="text-lg font-semibold mb-4">Simulation Settings</h2>
          <div>
            <label className="block text-sm text-gray-600 mb-1">Agent Count</label>
            <input
              type="number"
              min={10}
              max={1000}
              value={form.agentCount || 200}
              onChange={e => setForm(f => ({ ...f, agentCount: Number(e.target.value) }))}
              className="w-32 border rounded-lg px-3 py-2"
            />
            <span className="text-sm text-gray-400 ml-2">More agents = more accurate but slower & costlier</span>
          </div>
        </section>

        <button
          type="submit"
          disabled={loading}
          className="w-full py-3 bg-black text-white rounded-lg font-medium hover:bg-gray-800 disabled:bg-gray-400"
        >
          {loading ? 'Starting Simulation...' : 'Start Simulation'}
        </button>
      </form>
    </div>
  )
}
