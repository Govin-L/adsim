import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Sparkles, Plus, X, Send, Loader2, ArrowRight, Package, Target, Megaphone, Clock, CheckCircle2, XCircle, Activity, Building2 } from 'lucide-react'
import { api, type SimulationInput, type AdPlacement, type CompetitorInfo, type Simulation } from '@/api/client'
import { templates } from '@/data/templates'
import LanguageSwitch from '@/components/LanguageSwitch'
import LlmSettings from '@/components/LlmSettings'
import BuildInfo from '@/components/BuildInfo'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'

interface ChatMessage { role: 'ai' | 'user'; content: string }

const TEMPLATE_ICONS = { beauty: Package, ecommerce: Target, app: Megaphone }

export default function CreateSimulation() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const bottomRef = useRef<HTMLDivElement>(null)

  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [plan, setPlan] = useState<SimulationInput | null>(null)
  const [rawInput, setRawInput] = useState('')
  const [parsing, setParsing] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [agentCount, setAgentCount] = useState(20)
  const [duplicateWarning, setDuplicateWarning] = useState<number | null>(null)
  const [history, setHistory] = useState<Simulation[]>([])

  useEffect(() => {
    api.listSimulations().then(setHistory).catch(() => {})
  }, [])

  useEffect(() => {
    if (messages.length > 0) bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  const handleTemplate = (key: string) => {
    const tpl = templates.find(t => t.key === key)
    if (tpl) setPlan(tpl.getInput())
  }

  const handleSend = async () => {
    if (!input.trim() || parsing) return
    const userMsg = input.trim()
    setInput('')
    setMessages(prev => [...prev, { role: 'user', content: userMsg }])
    setRawInput(prev => prev ? `${prev}\n${userMsg}` : userMsg)
    setParsing(true)
    try {
      const context = plan
        ? `Current plan:\n${JSON.stringify(plan, null, 2)}\n\nUser wants to change:\n${userMsg}`
        : userMsg
      const res = await api.parsePlan(context)
      setPlan(res.input)
      if (res.missingFields.length > 0) {
        setMessages(prev => [...prev, { role: 'ai', content: `${t('create.missingFields')} ${res.missingFields.join(', ')}` }])
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Parse failed')
    } finally {
      setParsing(false)
    }
  }

  const handleSubmit = async () => {
    if (!plan) return
    setSubmitting(true)
    try {
      const sim = await api.createSimulation(plan, rawInput, agentCount)
      navigate(`/simulation/${sim.id}`)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to start')
      setSubmitting(false)
    }
  }

  const updateProduct = (field: string, value: unknown) => {
    if (plan) setPlan({ ...plan, product: { ...plan.product, [field]: value } })
  }
  const updateAudience = (field: string, value: unknown) => {
    if (plan) setPlan({ ...plan, targetAudience: { ...plan.targetAudience, [field]: value } })
  }
  const isDuplicate = (platform: string, type: string, excludeIdx?: number) =>
    plan?.adPlacements.some((p, i) => i !== excludeIdx && p.platform === platform && p.placementType === type) ?? false

  const updatePlacement = (i: number, field: string, value: unknown) => {
    if (!plan) return
    const updated = { ...plan.adPlacements[i], [field]: value }
    if ((field === 'platform' || field === 'placementType') && isDuplicate(updated.platform, updated.placementType, i)) {
      setDuplicateWarning(i)
      setTimeout(() => setDuplicateWarning(null), 3000)
      return
    }
    setDuplicateWarning(null)
    const placements = [...plan.adPlacements]
    placements[i] = updated
    const totalBudget = field === 'budget' ? placements.reduce((s, p) => s + p.budget, 0) : plan.totalBudget
    setPlan({ ...plan, adPlacements: placements, totalBudget })
  }
  const removePlacement = (i: number) => {
    if (!plan) return
    const placements = plan.adPlacements.filter((_, idx) => idx !== i)
    setPlan({ ...plan, adPlacements: placements, totalBudget: placements.reduce((s, p) => s + p.budget, 0) })
  }
  const addPlacement = () => {
    if (!plan) return
    const platforms = ['xiaohongshu', 'douyin', 'kuaishou', 'bilibili', 'weibo', 'wechat_moments', 'wechat_channels', 'meta', 'google', 'tiktok']
    const types = ['INFO_FEED', 'SEARCH', 'KOL_SEEDING', 'SHORT_VIDEO', 'SPLASH_SCREEN', 'LIVESTREAM', 'HASHTAG_CHALLENGE', 'SHOPPING']
    let platform = 'xiaohongshu', placementType = 'INFO_FEED'
    for (const pl of platforms) { for (const tp of types) { if (!isDuplicate(pl, tp)) { platform = pl; placementType = tp; break } } if (!isDuplicate(platform, placementType)) break }
    const np: AdPlacement = { platform, placementType, objectives: ['CONVERSION'], format: 'VIDEO', budget: 100000, creativeDescription: '' }
    setPlan({ ...plan, adPlacements: [...plan.adPlacements, np], totalBudget: plan.totalBudget + 100000 })
  }

  const updateCompetitor = (i: number, field: keyof CompetitorInfo, value: string | number) => {
    if (!plan) return
    const competitors = [...(plan.competitors || [])]
    competitors[i] = { ...competitors[i], [field]: value }
    setPlan({ ...plan, competitors })
  }
  const removeCompetitor = (i: number) => {
    if (!plan) return
    setPlan({ ...plan, competitors: (plan.competitors || []).filter((_, idx) => idx !== i) })
  }
  const addCompetitor = () => {
    if (!plan) return
    const competitors = plan.competitors || []
    if (competitors.length >= 5) return
    setPlan({ ...plan, competitors: [...competitors, { brandName: '', price: 0, positioning: '' }] })
  }

  return (
    <div className="min-h-screen flex flex-col bg-background">
      {/* Header */}
      <header className="border-b bg-card/80 backdrop-blur-sm sticky top-0 z-10 animate-in">
        <div className="max-w-6xl mx-auto px-6 h-14 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="font-serif text-xl tracking-tight">{t('app.name')}</span>
            <Separator orientation="vertical" className="h-5" />
            <span className="text-xs text-muted-foreground tracking-widest uppercase">{t('app.tagline')}</span>
          </div>
          <div className="flex items-center gap-4">
            <LlmSettings />
            <LanguageSwitch />
          </div>
        </div>
      </header>

      {/* Main */}
      <main className="flex-1 overflow-y-auto">
        <div className="max-w-6xl mx-auto px-6 py-8">
          {/* STATE 1: Landing */}
          {!plan && messages.length === 0 && (
            <div className="animate-in">
              <div className="mb-10">
                <h2 className="font-serif text-3xl tracking-tight mb-2">{t('create.title')}</h2>
                <p className="text-muted-foreground">{t('create.greeting')}</p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
                {templates.map((tpl, i) => {
                  const Icon = TEMPLATE_ICONS[tpl.key as keyof typeof TEMPLATE_ICONS] || Package
                  const preview = tpl.getInput()
                  return (
                    <Card key={tpl.key}
                      className={cn('cursor-pointer transition-all duration-300 hover:shadow-lg hover:border-teal/40 hover:-translate-y-0.5 group animate-in', `stagger-${i + 1}`)}
                      onClick={() => handleTemplate(tpl.key)}>
                      <CardHeader className="pb-3">
                        <div className="flex items-start justify-between">
                          <div className="w-9 h-9 rounded-lg bg-teal/10 flex items-center justify-center text-teal group-hover:bg-teal group-hover:text-white transition-colors">
                            <Icon size={18} />
                          </div>
                          <ArrowRight size={14} className="text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
                        </div>
                        <CardTitle className="text-base mt-3">{t(`create.templates.${tpl.key}.title`)}</CardTitle>
                      </CardHeader>
                      <CardContent className="pt-0">
                        <p className="text-xs text-muted-foreground mb-3 line-clamp-2">
                          {preview.product.name || preview.product.category} &middot; ¥{preview.product.price}
                        </p>
                        <div className="flex flex-wrap gap-1.5">
                          {[...new Set(preview.adPlacements.map(ap => ap.platform))].map((platform, j) => (
                            <Badge key={j} variant="secondary" className="text-[10px] font-normal">
                              {t(`create.platform.${platform}`)}
                            </Badge>
                          ))}
                          <Badge variant="outline" className="text-[10px] font-mono">
                            ¥{(preview.totalBudget / 10000).toFixed(0)}万
                          </Badge>
                        </div>
                      </CardContent>
                    </Card>
                  )
                })}
              </div>

              {/* History */}
              {history.length > 0 && (
                <div>
                  <h3 className="text-xs font-medium tracking-wide uppercase text-muted-foreground mb-3 flex items-center gap-1.5">
                    <Clock size={12} />
                    {t('create.history.title')}
                  </h3>
                  <div className="space-y-2">
                    {history.slice(0, 5).map(sim => {
                      const StatusIcon = sim.status === 'COMPLETED' ? CheckCircle2 : sim.status === 'FAILED' ? XCircle : Activity
                      const statusColor = sim.status === 'COMPLETED' ? 'text-emerald-500' : sim.status === 'FAILED' ? 'text-red-500' : 'text-blue-500'
                      return (
                        <div key={sim.id}
                          className="flex items-center justify-between px-4 py-3 rounded-lg border cursor-pointer hover:bg-muted/50 transition-colors"
                          onClick={() => navigate(`/simulation/${sim.id}`)}>
                          <div className="flex items-center gap-3 min-w-0">
                            <StatusIcon size={14} className={cn('shrink-0', statusColor)} />
                            <div className="min-w-0">
                              <p className="text-sm font-medium truncate">{sim.input.product.name}</p>
                              <p className="text-[11px] text-muted-foreground">
                                {[...new Set(sim.input.adPlacements.map(p => t(`create.platform.${p.platform}`)))].join(' · ')}
                                {' · '}
                                {new Date(sim.createdAt).toLocaleString()}
                              </p>
                            </div>
                          </div>
                          <Badge variant="outline" className="text-[10px] font-mono shrink-0 ml-2">
                            {t(`result.status.${sim.status}`)}
                          </Badge>
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Chat messages */}
          {messages.map((msg, i) => (
            <div key={i} className={cn('flex mb-3 animate-fade', msg.role === 'user' ? 'justify-end' : 'justify-start')}>
              <div className={cn('max-w-[70%] px-4 py-2.5 text-sm leading-relaxed whitespace-pre-wrap rounded-2xl',
                msg.role === 'user'
                  ? 'bg-foreground text-background rounded-br-md'
                  : 'bg-card border rounded-bl-md text-foreground'
              )}>{msg.content}</div>
            </div>
          ))}

          {parsing && (
            <div className="flex mb-3 animate-fade">
              <div className="bg-card border rounded-2xl rounded-bl-md px-4 py-2.5 text-sm text-muted-foreground flex items-center gap-2">
                <Loader2 size={14} className="animate-spin" />
                {t('create.parsing')}
              </div>
            </div>
          )}

          {/* STATE 2: Plan Editor */}
          {plan && (
            <div className="space-y-4 animate-in">
              {/* Product */}
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-sm font-medium tracking-wide uppercase text-muted-foreground flex items-center gap-2">
                    <Package size={14} />
                    {t('create.product.title')}
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="grid grid-cols-2 lg:grid-cols-4 gap-x-4 gap-y-3">
                    <FieldGroup label={t('create.product.brandName')}>
                      <Input value={plan.product.brandName} onChange={e => updateProduct('brandName', e.target.value)} placeholder={t('create.product.brandNamePlaceholder')} />
                    </FieldGroup>
                    <FieldGroup label={t('create.product.name')}>
                      <Input value={plan.product.name} onChange={e => updateProduct('name', e.target.value)} placeholder={t('create.product.namePlaceholder')} />
                    </FieldGroup>
                    <FieldGroup label={t('create.product.price')}>
                      <Input type="number" value={plan.product.price || ''} onChange={e => updateProduct('price', Number(e.target.value))} placeholder={t('create.product.pricePlaceholder')} className="font-mono" />
                    </FieldGroup>
                    <FieldGroup label={t('create.product.category')}>
                      <Input value={plan.product.category} onChange={e => updateProduct('category', e.target.value)} placeholder={t('create.product.categoryPlaceholder')} />
                    </FieldGroup>
                    <div className="col-span-2">
                      <FieldGroup label={t('create.product.sellingPoints')}>
                        <Input value={plan.product.sellingPoints} onChange={e => updateProduct('sellingPoints', e.target.value)} placeholder={t('create.product.sellingPointsPlaceholder')} />
                      </FieldGroup>
                    </div>
                    <div className="col-span-2">
                      <Label className="text-xs text-muted-foreground mb-1.5 block">{t('create.product.productStage')}</Label>
                      <div className="flex gap-2">
                        {(['NEW_LAUNCH', 'ESTABLISHED', 'BESTSELLER'] as const).map(stage => (
                          <Button key={stage} type="button" size="sm"
                            variant={plan.product.productStage === stage ? 'default' : 'outline'}
                            className={cn('text-xs h-8', plan.product.productStage === stage && 'bg-teal hover:bg-teal/90')}
                            onClick={() => updateProduct('productStage', stage)}>
                            {t(`create.product.stage${stage === 'NEW_LAUNCH' ? 'NewLaunch' : stage === 'ESTABLISHED' ? 'Established' : 'Bestseller'}`)}
                          </Button>
                        ))}
                      </div>
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* Audience */}
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-sm font-medium tracking-wide uppercase text-muted-foreground flex items-center gap-2">
                    <Target size={14} />
                    {t('create.audience.title')}
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="flex flex-wrap items-end gap-6">
                    <div>
                      <Label className="text-xs text-muted-foreground mb-1.5 block">{t('create.audience.gender')}</Label>
                      <div className="flex gap-1.5">
                        {['all', 'female', 'male'].map(g => (
                          <Button key={g} type="button" size="sm"
                            variant={plan.targetAudience.gender === g ? 'default' : 'outline'}
                            className={cn('text-xs h-8', plan.targetAudience.gender === g && 'bg-teal hover:bg-teal/90')}
                            onClick={() => updateAudience('gender', g)}>
                            {t(`create.audience.gender${g.charAt(0).toUpperCase() + g.slice(1)}`)}
                          </Button>
                        ))}
                      </div>
                    </div>
                    <div className="flex items-end gap-2">
                      <FieldGroup label={t('create.audience.ageMin')} className="w-20">
                        <Input type="number" value={plan.targetAudience.ageRange?.[0] || 18} className="font-mono"
                          onChange={e => updateAudience('ageRange', [Number(e.target.value), plan.targetAudience.ageRange?.[1] || 65])} />
                      </FieldGroup>
                      <span className="pb-2 text-muted-foreground">–</span>
                      <FieldGroup label={t('create.audience.ageMax')} className="w-20">
                        <Input type="number" value={plan.targetAudience.ageRange?.[1] || 65} className="font-mono"
                          onChange={e => updateAudience('ageRange', [plan.targetAudience.ageRange?.[0] || 18, Number(e.target.value)])} />
                      </FieldGroup>
                    </div>
                    <FieldGroup label={t('create.audience.region')}>
                      <Input value={plan.targetAudience.region || ''} placeholder={t('create.audience.regionPlaceholder')}
                        onChange={e => updateAudience('region', e.target.value)} />
                    </FieldGroup>
                  </div>
                </CardContent>
              </Card>

              {/* Market Context */}
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-sm font-medium tracking-wide uppercase text-muted-foreground flex items-center gap-2">
                    <Building2 size={14} />
                    {t('create.market.title')}
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  {/* Brand Awareness */}
                  <div>
                    <Label className="text-xs text-muted-foreground mb-1.5 block">{t('create.market.brandAwareness')}</Label>
                    <div className="flex gap-2">
                      {(['NEW', 'EMERGING', 'WELL_KNOWN', 'TOP'] as const).map(level => (
                        <Button key={level} type="button" size="sm"
                          variant={plan.brandAwareness === level ? 'default' : 'outline'}
                          className={cn('text-xs h-8', plan.brandAwareness === level && 'bg-teal hover:bg-teal/90')}
                          onClick={() => setPlan({ ...plan, brandAwareness: level })}>
                          {t(`create.market.awareness${level === 'NEW' ? 'New' : level === 'EMERGING' ? 'Emerging' : level === 'WELL_KNOWN' ? 'WellKnown' : 'Top'}`)}
                        </Button>
                      ))}
                    </div>
                  </div>

                  {/* Campaign Goal */}
                  <div>
                    <Label className="text-xs text-muted-foreground mb-1.5 block">{t('create.market.campaignGoal')}</Label>
                    <div className="flex gap-2">
                      {(['ACQUISITION', 'RETENTION', 'MIXED'] as const).map(goal => (
                        <Button key={goal} type="button" size="sm"
                          variant={plan.campaignGoal === goal ? 'default' : 'outline'}
                          className={cn('text-xs h-8', plan.campaignGoal === goal && 'bg-teal hover:bg-teal/90')}
                          onClick={() => setPlan({ ...plan, campaignGoal: goal })}>
                          {t(`create.market.goal${goal === 'ACQUISITION' ? 'Acquisition' : goal === 'RETENTION' ? 'Retention' : 'Mixed'}`)}
                        </Button>
                      ))}
                    </div>
                  </div>

                  <Separator />

                  {/* Competitors */}
                  <div>
                    <Label className="text-xs text-muted-foreground mb-1.5 block">{t('create.market.competitors')}</Label>
                    <p className="text-xs text-muted-foreground/70 mb-3">{t('create.market.competitorHint')}</p>
                    <div className="space-y-2">
                      {(plan.competitors || []).map((c, i) => (
                        <div key={i} className="relative flex items-center gap-3 p-3 rounded-xl border bg-muted/30">
                          <div className="flex-1 grid grid-cols-3 gap-3">
                            <FieldGroup label={t('create.market.competitorBrand')}>
                              <Input value={c.brandName} placeholder={t('create.market.competitorBrandPlaceholder')}
                                onChange={e => updateCompetitor(i, 'brandName', e.target.value)} />
                            </FieldGroup>
                            <FieldGroup label={`${t('create.market.competitorPrice')} (¥)`}>
                              <Input type="number" value={c.price || ''} className="font-mono"
                                onChange={e => updateCompetitor(i, 'price', Number(e.target.value))} />
                            </FieldGroup>
                            <FieldGroup label={t('create.market.competitorPositioning')}>
                              <Input value={c.positioning} placeholder={t('create.market.competitorPositioningPlaceholder')}
                                onChange={e => updateCompetitor(i, 'positioning', e.target.value)} />
                            </FieldGroup>
                          </div>
                          <Button variant="ghost" size="icon" className="h-6 w-6 shrink-0 text-muted-foreground hover:text-destructive"
                            onClick={() => removeCompetitor(i)}>
                            <X size={14} />
                          </Button>
                        </div>
                      ))}
                      {(plan.competitors || []).length < 5 && (
                        <Button variant="outline" className="w-full border-dashed text-muted-foreground hover:text-foreground hover:border-teal/50" onClick={addCompetitor}>
                          <Plus size={14} className="mr-1.5" />
                          {t('create.market.addCompetitor')}
                        </Button>
                      )}
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* Placements */}
              <Card>
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-sm font-medium tracking-wide uppercase text-muted-foreground flex items-center gap-2">
                      <Megaphone size={14} />
                      {t('create.placement.title')}
                    </CardTitle>
                    <span className="text-lg font-semibold font-mono text-amber">¥{plan.totalBudget.toLocaleString()}</span>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  {plan.adPlacements.map((p, i) => (
                    <div key={i} className="relative p-4 rounded-xl border bg-muted/30 transition-colors hover:bg-muted/50">
                      <Button variant="ghost" size="icon" className="absolute top-2 right-2 h-6 w-6 text-muted-foreground hover:text-destructive"
                        onClick={() => removePlacement(i)}>
                        <X size={14} />
                      </Button>

                      {/* Row 1 */}
                      <div className="grid grid-cols-4 gap-3 mb-3 pr-8">
                        <FieldGroup label={t('create.placement.platform')}>
                          <select value={p.platform} onChange={e => updatePlacement(i, 'platform', e.target.value)}
                            className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring">
                            {['xiaohongshu','douyin','kuaishou','bilibili','weibo','wechat_moments','wechat_channels','meta','google','tiktok'].map(v =>
                              <option key={v} value={v}>{t(`create.platform.${v}`)}</option>
                            )}
                          </select>
                        </FieldGroup>
                        <FieldGroup label={t('create.placement.type')}>
                          <select value={p.placementType} onChange={e => updatePlacement(i, 'placementType', e.target.value)}
                            className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring">
                            {['INFO_FEED','SEARCH','KOL_SEEDING','SHORT_VIDEO','SPLASH_SCREEN','LIVESTREAM','HASHTAG_CHALLENGE','SHOPPING'].map(v =>
                              <option key={v} value={v}>{t(`create.placementType.${v}`)}</option>
                            )}
                          </select>
                        </FieldGroup>
                        <FieldGroup label={t('create.placement.format')}>
                          <select value={p.format} onChange={e => updatePlacement(i, 'format', e.target.value)}
                            className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring">
                            <option value="VIDEO">{t('create.creative.formatVideo')}</option>
                            <option value="IMAGE">{t('create.creative.formatImage')}</option>
                            <option value="IMAGE_TEXT">{t('create.creative.formatImageText')}</option>
                            <option value="CAROUSEL">{t('create.creative.formatCarousel')}</option>
                          </select>
                        </FieldGroup>
                        <FieldGroup label={`${t('create.placement.budget')} (¥)`}>
                          <Input type="number" value={p.budget} className="font-mono"
                            onChange={e => updatePlacement(i, 'budget', Number(e.target.value))} />
                        </FieldGroup>
                      </div>

                      {/* Row 2: Objectives */}
                      <div className="mb-3">
                        <Label className="text-xs text-muted-foreground mb-1.5 block">{t('create.placement.objective')}</Label>
                        <div className="flex flex-wrap gap-1.5">
                          {['BRAND_AWARENESS','SEEDING','TRAFFIC','CONVERSION','LEAD_GENERATION'].map((obj: string) => {
                            const selected = p.objectives.includes(obj)
                            return (
                              <Badge key={obj} variant={selected ? 'default' : 'outline'}
                                className={cn('cursor-pointer text-xs transition-all', selected ? 'bg-teal hover:bg-teal/90 text-white border-teal' : 'hover:border-teal/50')}
                                onClick={() => {
                                  const newObjs = selected ? p.objectives.filter(o => o !== obj) : [...p.objectives, obj]
                                  if (newObjs.length > 0) updatePlacement(i, 'objectives', newObjs)
                                }}>
                                {t(`create.objectiveType.${obj}`)}
                              </Badge>
                            )
                          })}
                        </div>
                      </div>

                      {/* Row 3: Creative */}
                      <FieldGroup label={t('create.placement.creative')}>
                        <Textarea value={p.creativeDescription} rows={2} placeholder={t('create.creative.descriptionPlaceholder')}
                          onChange={e => updatePlacement(i, 'creativeDescription', e.target.value)} className="resize-none" />
                      </FieldGroup>

                      {duplicateWarning === i && (
                        <p className="text-xs mt-2 text-amber animate-fade">{t('create.placement.duplicateWarning')}</p>
                      )}
                    </div>
                  ))}

                  <Button variant="outline" className="w-full border-dashed text-muted-foreground hover:text-foreground hover:border-teal/50" onClick={addPlacement}>
                    <Plus size={14} className="mr-1.5" />
                    {t('create.placement.addPlacement')}
                  </Button>
                </CardContent>
              </Card>

              {/* Agent Count + Submit */}
              <div className="flex items-center gap-3 animate-in stagger-5">
                <div className="shrink-0">
                  <Label className="text-xs text-muted-foreground mb-1.5 block">{t('create.settings.agentCount')}</Label>
                  <Input type="number" min={1} value={agentCount || ''}
                    onChange={e => setAgentCount(e.target.value === '' ? 0 : Number(e.target.value))}
                    onBlur={() => setAgentCount(prev => Math.max(1, prev || 20))}
                    className="w-24 font-mono text-sm" />
                </div>
                <div className="flex-1 pt-5">
              <Button size="lg" className="w-full h-12 text-sm font-semibold tracking-wide bg-teal hover:bg-teal/90"
                disabled={submitting} onClick={handleSubmit}>
                {submitting ? (
                  <><Loader2 size={16} className="mr-2 animate-spin" />{t('create.submitting')}</>
                ) : (
                  <><Sparkles size={16} className="mr-2" />{t('create.startSimulation')}</>
                )}
              </Button>
                </div>
              </div>
            </div>
          )}

          <div ref={bottomRef} />
        </div>
      </main>

      {/* Bottom bar */}
      <div className="shrink-0 border-t bg-card/80 backdrop-blur-sm">
        <div className="max-w-6xl mx-auto px-6 py-3 flex gap-3 items-center">
          <Input value={input} onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSend()}
            placeholder={t('create.chatPlaceholder')}
            disabled={parsing}
            className="flex-1" />
          <Button onClick={handleSend} disabled={parsing || !input.trim()} size="icon" className="shrink-0 bg-teal hover:bg-teal/90">
            {parsing ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
          </Button>
          <BuildInfo />
        </div>
      </div>
    </div>
  )
}

function FieldGroup({ label, className, children }: { label: string; className?: string; children: React.ReactNode }) {
  return (
    <div className={className}>
      <Label className="text-xs text-muted-foreground mb-1.5 block">{label}</Label>
      {children}
    </div>
  )
}
