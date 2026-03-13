import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Settings, Check, Loader2, CircleCheck, CircleX } from 'lucide-react'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogDescription } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { api } from '@/api/client'
import { getLlmConfig, saveLlmConfig, isLlmConfigured, type LlmConfig } from '@/lib/llm-config'

export default function LlmSettings() {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [config, setConfig] = useState<LlmConfig>({ apiKey: '', baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o-mini', concurrency: 5 })
  const [saved, setSaved] = useState(false)
  const [configured, setConfigured] = useState(isLlmConfigured())
  const [verifying, setVerifying] = useState(false)
  const [verifyResult, setVerifyResult] = useState<'success' | 'failed' | null>(null)

  useEffect(() => {
    const existing = getLlmConfig()
    if (existing) setConfig(existing)
    setVerifyResult(null)
  }, [open])

  const handleSave = () => {
    saveLlmConfig(config)
    setConfigured(true)
    setSaved(true)
    setTimeout(() => { setSaved(false); setOpen(false) }, 800)
  }

  const handleVerify = async () => {
    // Save first so the API client picks up the config
    saveLlmConfig(config)
    setVerifying(true)
    setVerifyResult(null)
    try {
      const res = await api.verifyLlm()
      setVerifyResult(res.success ? 'success' : 'failed')
      if (res.success) setConfigured(true)
    } catch {
      setVerifyResult('failed')
    } finally {
      setVerifying(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <button className="flex items-center gap-1.5 text-xs cursor-pointer text-muted-foreground hover:text-foreground transition-colors">
          <Settings size={14} />
          <Badge variant={configured ? 'secondary' : 'outline'} className="text-[10px] font-normal">
            {configured ? t('settings.configured') : t('settings.notConfigured')}
          </Badge>
        </button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('settings.llm')}</DialogTitle>
          <DialogDescription className="text-xs text-muted-foreground">
            {t('settings.llmDescription')}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 pt-2" data-1p-ignore>
          <div>
            <Label className="text-xs">{t('settings.apiKey')}</Label>
            <Input type="password" value={config.apiKey} placeholder={t('settings.apiKeyPlaceholder')}
              onChange={e => { setConfig(c => ({ ...c, apiKey: e.target.value })); setVerifyResult(null) }}
              autoComplete="off" data-1p-ignore data-lpignore="true"
              className="mt-1.5 font-mono text-sm" />
          </div>
          <div>
            <Label className="text-xs">{t('settings.baseUrl')}</Label>
            <Input value={config.baseUrl} placeholder={t('settings.baseUrlPlaceholder')}
              onChange={e => { setConfig(c => ({ ...c, baseUrl: e.target.value })); setVerifyResult(null) }}
              className="mt-1.5 font-mono text-sm" />
          </div>
          <div>
            <Label className="text-xs">{t('settings.model')}</Label>
            <Input value={config.model} placeholder={t('settings.modelPlaceholder')}
              onChange={e => { setConfig(c => ({ ...c, model: e.target.value })); setVerifyResult(null) }}
              className="mt-1.5 font-mono text-sm" />
          </div>
          <div>
            <Label className="text-xs">{t('settings.concurrency')}</Label>
            <Input type="number" min={1} value={config.concurrency ?? ''}
              onChange={e => setConfig(c => ({ ...c, concurrency: e.target.value === '' ? undefined : Number(e.target.value) }))}
              onBlur={() => setConfig(c => ({ ...c, concurrency: Math.max(1, c.concurrency || 5) }))}
              className="mt-1.5 font-mono text-sm" />
            <p className="text-[10px] text-muted-foreground mt-1">{t('settings.concurrencyHint')}</p>
          </div>

          {/* Verify result */}
          {verifyResult && (
            <div className={`flex items-center gap-2 text-xs px-3 py-2 rounded-lg ${
              verifyResult === 'success' ? 'bg-success-light text-success' : 'bg-error-light text-error'
            }`}>
              {verifyResult === 'success' ? <CircleCheck size={14} /> : <CircleX size={14} />}
              {verifyResult === 'success' ? t('settings.verifySuccess') : t('settings.verifyFailed')}
            </div>
          )}

          <div className="flex gap-2">
            <Button variant="outline" onClick={handleVerify} disabled={!config.apiKey.trim() || verifying} className="flex-1">
              {verifying ? <><Loader2 size={14} className="mr-1.5 animate-spin" />{t('settings.verifying')}</> : t('settings.verify')}
            </Button>
            <Button onClick={handleSave} disabled={!config.apiKey.trim()} className="flex-1 bg-teal hover:bg-teal/90">
              {saved ? <><Check size={14} className="mr-1.5" />{t('settings.saved')}</> : t('settings.save')}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
