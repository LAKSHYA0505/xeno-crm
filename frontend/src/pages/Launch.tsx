import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api'
import { Card, CardContent, CardHeader, CardTitle } from '../../@/components/ui/card'
import { Button } from '../../@/components/ui/button'
import { Badge } from '../../@/components/ui/badge'
import { Skeleton } from '../../@/components/ui/skeleton'
import {
  Sparkles, Rocket, ChevronLeft, Users,
  MessageSquare, CheckCircle2, Loader2
} from 'lucide-react'

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────
type Channel = 'whatsapp' | 'sms' | 'email'

interface Preview {
  count:   number
  sample:  any[]
  rules:   string
  message: string   // AI drafted message template
}

interface Stats {
  id:        string
  name:      string
  status:    string
  totalLogs: number
  sent:      number
  delivered: number
  opened:    number
  clicked:   number
  failed:    number
  aiSummary: string | null
}

type Stage = 'input' | 'preview' | 'launching' | 'stats'

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
const CHANNEL_LABELS: Record<Channel, string> = {
  whatsapp: 'WhatsApp',
  sms:      'SMS',
  email:    'Email',
}

function pct(num: number, denom: number) {
  if (!denom) return '0'
  return ((num / denom) * 100).toFixed(1)
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

/** Animated "AI thinking" skeleton */
function AiThinking({ label }: { label: string }) {
  return (
    <div className="space-y-3 py-2">
      <div className="flex items-center gap-2 text-sm text-indigo-500">
        <Loader2 size={14} className="animate-spin" />
        <span>{label}</span>
      </div>
      <Skeleton className="h-3 w-full" />
      <Skeleton className="h-3 w-4/5" />
      <Skeleton className="h-3 w-3/5" />
    </div>
  )
}

/** Single stat tile */
function StatTile({
  label, value, sub, color,
}: {
  label: string; value: number; sub?: string; color: string
}) {
  return (
    <div className="text-center">
      <p className={`text-3xl font-bold ${color}`}>{value}</p>
      <p className="text-xs text-muted-foreground mt-0.5">{label}</p>
      {sub && <p className="text-xs text-muted-foreground">{sub}</p>}
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Main component
// ─────────────────────────────────────────────────────────────────────────────
export default function Launch() {
  const navigate = useNavigate()

  // ── Stage state ────────────────────────────────────────────────────────────
  const [stage, setStage] = useState<Stage>('input')

  // ── Stage 1 — input ────────────────────────────────────────────────────────
  const [nlQuery,  setNlQuery]  = useState('')
  const [channel,  setChannel]  = useState<Channel>('whatsapp')
  const [parsing,  setParsing]  = useState(false)
  const [parseErr, setParseErr] = useState('')

  // ── Stage 2 — preview ─────────────────────────────────────────────────────
  const [preview,        setPreview]        = useState<Preview | null>(null)
  const [campaignName,   setCampaignName]   = useState('')
  const [editingMessage, setEditingMessage] = useState(false)
  const [draftMessage,   setDraftMessage]   = useState('')

  // ── Stage 3 — launching ───────────────────────────────────────────────────
  const [launchProgress, setLaunchProgress] = useState(0)

  // ── Stage 4 — stats ───────────────────────────────────────────────────────
  const [campaignId,     setCampaignId]     = useState<string | null>(null)
  const [stats,          setStats]          = useState<Stats | null>(null)
  const [loadingSummary, setLoadingSummary] = useState(false)

  // ─────────────────────────────────────────────────────────────────────────
  // Stage 1 → 2: Parse NL query with AI
  // ─────────────────────────────────────────────────────────────────────────
  async function handleBuild() {
    if (!nlQuery.trim()) return
    setParsing(true)
    setParseErr('')
    try {
      const parseRes = await api.post('/api/segments/parse', { query: nlQuery })
      const { count, sample, rules, message } = parseRes.data

      setPreview({
        count,
        sample,
        rules,
        message: message ?? '',
      })
      setDraftMessage(message ?? '')
      setCampaignName(`${nlQuery.slice(0, 35)}… campaign`)
      setStage('preview')
    } catch (e: any) {
      const detail = e?.response?.data?.message ?? e?.message
      setParseErr(
        detail
          ? `AI parsing failed: ${detail}`
          : 'AI parsing failed — please try rephrasing your query.'
      )
    } finally {
      setParsing(false)
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Stage 2 → 3 → 4: Save segment, create campaign, launch
  // ─────────────────────────────────────────────────────────────────────────
  async function handleLaunch() {
    if (!preview) return
    setStage('launching')
    setLaunchProgress(10)

    try {
      // 1. Save segment
      const segRes = await api.post('/api/segments', {
        name:        campaignName,
        description: nlQuery,
        nlQuery,
        rules:       preview.rules,
      })
      setLaunchProgress(35)

      // 2. Create campaign (with AI-drafted message)
      const campRes = await api.post('/api/campaigns', {
        name:            campaignName,
        segmentId:       segRes.data.id,
        channel,
        messageTemplate: draftMessage,
      })
      setLaunchProgress(60)

      // 3. Launch campaign
      const launched = await api.post(`/api/campaigns/${campRes.data.id}/launch`)
      setLaunchProgress(90)

      setCampaignId(launched.data.id)
      setStats(launched.data)

      // small pause so user sees 90% → 100%
      await new Promise(r => setTimeout(r, 500))
      setLaunchProgress(100)
      setStage('stats')
    } catch (e: any) {
      alert('Launch failed: ' + (e?.response?.data?.message ?? e.message))
      setStage('preview')
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Stage 4: Poll stats every 4 seconds
  // ─────────────────────────────────────────────────────────────────────────
  const pollStats = useCallback(async () => {
    if (!campaignId) return
    try {
      const r = await api.get(`/api/campaigns/${campaignId}`)
      setStats(r.data)
    } catch (_) {}
  }, [campaignId])

  useEffect(() => {
    if (stage !== 'stats' || !campaignId) return
    const id = setInterval(pollStats, 4000)
    return () => clearInterval(id)
  }, [stage, campaignId, pollStats])

  // ─────────────────────────────────────────────────────────────────────────
  // AI Summary
  // ─────────────────────────────────────────────────────────────────────────
  async function handleSummary() {
    if (!campaignId) return
    setLoadingSummary(true)
    try {
      const r = await api.post(`/api/campaigns/${campaignId}/ai-summary`)
      setStats(s => s ? { ...s, aiSummary: r.data.summary } : s)
    } catch (e: any) {
      alert('Analysis failed: ' + (e?.response?.data?.message ?? e.message))
    } finally {
      setLoadingSummary(false)
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Reset
  // ─────────────────────────────────────────────────────────────────────────
  function handleReset() {
    setStage('input')
    setNlQuery('')
    setPreview(null)
    setCampaignId(null)
    setStats(null)
    setLaunchProgress(0)
    setParseErr('')
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Render
  // ─────────────────────────────────────────────────────────────────────────
  return (
    <div className="max-w-2xl mx-auto space-y-6">

      {/* ── Page header ───────────────────────────────────────────────── */}
      <div>
        <h1 className="text-2xl font-bold">Launch Campaign</h1>
        <p className="text-muted-foreground text-sm mt-1">
          Describe your audience — AI segments, drafts, and sends.
        </p>
      </div>

      {/* ── Stage indicator ───────────────────────────────────────────── */}
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        {(['input', 'preview', 'launching', 'stats'] as Stage[]).map((s, i) => {
          const labels: Record<Stage, string> = {
            input:     '1. Describe',
            preview:   '2. Review',
            launching: '3. Launching',
            stats:     '4. Live Stats',
          }
          const active  = s === stage
          const done    = ['input','preview','launching','stats'].indexOf(s)
                        < ['input','preview','launching','stats'].indexOf(stage)
          return (
            <span key={s} className="flex items-center gap-1.5">
              {i > 0 && <span className="text-border">›</span>}
              <span className={
                active ? 'text-indigo-500 font-medium' :
                done   ? 'text-foreground' : ''
              }>
                {done && <CheckCircle2 size={11} className="inline mr-0.5 text-green-500" />}
                {labels[s]}
              </span>
            </span>
          )
        })}
      </div>

      {/* ══════════════════════════════════════════════════════════════════
          STAGE 1 — INPUT
      ══════════════════════════════════════════════════════════════════ */}
      {stage === 'input' && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm flex items-center gap-2">
              <Sparkles size={14} className="text-indigo-500" />
              Who do you want to reach?
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <textarea
              rows={3}
              className="w-full border rounded-lg px-3 py-2.5 text-sm bg-background
                         resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500
                         placeholder:text-muted-foreground"
              placeholder={
                'e.g. "Customers who spent over ₹5000 but haven\'t ordered in 60 days"\n' +
                'or "Female customers in Mumbai who bought running shoes"'
              }
              value={nlQuery}
              onChange={e => setNlQuery(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) handleBuild()
              }}
            />

            {/* Channel selector */}
            <div className="flex items-center gap-3">
              <span className="text-xs text-muted-foreground shrink-0">Send via</span>
              <div className="flex gap-2">
                {(['whatsapp', 'sms', 'email'] as Channel[]).map(c => (
                  <button
                    key={c}
                    onClick={() => setChannel(c)}
                    className={`px-3 py-1 rounded-full text-xs border transition-colors ${
                      channel === c
                        ? 'bg-indigo-600 border-indigo-600 text-white'
                        : 'border-border text-muted-foreground hover:border-indigo-400'
                    }`}
                  >
                    {CHANNEL_LABELS[c]}
                  </button>
                ))}
              </div>
            </div>

            {parseErr && (
              <p className="text-xs text-red-500">{parseErr}</p>
            )}

            {parsing
              ? <AiThinking label="AI is analyzing your audience…" />
              : (
                <Button
                  className="w-full bg-indigo-600 hover:bg-indigo-700 text-white"
                  disabled={!nlQuery.trim()}
                  onClick={handleBuild}
                >
                  <Sparkles size={13} className="mr-2" />
                  Build Segment with AI
                  <span className="ml-2 text-indigo-300 text-xs">⌘↵</span>
                </Button>
              )
            }
          </CardContent>
        </Card>
      )}

      {/* ══════════════════════════════════════════════════════════════════
          STAGE 2 — PREVIEW
      ══════════════════════════════════════════════════════════════════ */}
      {stage === 'preview' && preview && (
        <div className="space-y-4">

          {/* Audience card */}
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm flex items-center gap-2">
                  <Users size={14} className="text-indigo-500" />
                  Audience Preview
                </CardTitle>
                <Badge className="bg-indigo-100 text-indigo-700 border-0 text-xs">
                  AI Generated
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="space-y-3">
              {/* Match count */}
              <div className="flex items-baseline gap-1.5">
                <span className="text-4xl font-bold text-indigo-600">
                  {preview.count}
                </span>
                <span className="text-sm text-muted-foreground">
                  customers matched
                </span>
              </div>

              {/* Parsed rules */}
              <div className="bg-muted/40 rounded-md px-3 py-2 text-xs font-mono
                              text-muted-foreground space-y-0.5">
                {(() => {
                  try {
                    const r = JSON.parse(preview.rules)
                    return r.conditions?.map((c: any, i: number) => (
                      <p key={i}>
                        <span className="text-indigo-400">{c.field}</span>
                        {' '}{c.op}{' '}
                        <span className="text-green-400">{c.value}</span>
                      </p>
                    ))
                  } catch {
                    return <p>{preview.rules}</p>
                  }
                })()}
              </div>

              {/* Sample customers */}
              <div className="divide-y">
                {preview.sample?.slice(0, 4).map((c: any) => (
                  <div key={c.id}
                    className="flex items-center justify-between py-1.5 text-xs">
                    <span className="font-medium">{c.name}</span>
                    <span className="text-muted-foreground">
                      {c.city} · ₹{c.totalSpent?.toLocaleString()}
                    </span>
                  </div>
                ))}
                {preview.count > 4 && (
                  <p className="text-xs text-muted-foreground pt-1.5">
                    +{preview.count - 4} more customers
                  </p>
                )}
              </div>
            </CardContent>
          </Card>

          {/* Message card */}
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-sm flex items-center gap-2">
                <MessageSquare size={14} className="text-indigo-500" />
                AI Drafted Message
                <Badge variant="outline" className="text-xs ml-auto">
                  {CHANNEL_LABELS[channel]}
                </Badge>
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {editingMessage ? (
                <textarea
                  rows={4}
                  className="w-full border rounded-lg px-3 py-2 text-sm bg-background
                             resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  value={draftMessage}
                  onChange={e => setDraftMessage(e.target.value)}
                />
              ) : (
                <p className="text-sm bg-muted/40 rounded-lg px-3 py-2.5 leading-relaxed">
                  {draftMessage}
                </p>
              )}

              <div className="flex items-center gap-2">
                <p className="text-xs text-muted-foreground">
                  Uses <code className="bg-muted px-1 rounded">{'{{name}}'}</code> and{' '}
                  <code className="bg-muted px-1 rounded">{'{{last_product}}'}</code> per customer
                </p>
                <button
                  className="ml-auto text-xs text-indigo-500 hover:underline"
                  onClick={() => setEditingMessage(v => !v)}
                >
                  {editingMessage ? 'Done' : 'Edit'}
                </button>
              </div>
            </CardContent>
          </Card>

          {/* Campaign name */}
          <div className="flex items-center gap-2">
            <input
              className="flex-1 border rounded-lg px-3 py-2 text-sm bg-background
                         focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="Campaign name…"
              value={campaignName}
              onChange={e => setCampaignName(e.target.value)}
            />
          </div>

          {/* Actions */}
          <div className="flex gap-3">
            <Button variant="outline" onClick={() => setStage('input')}>
              <ChevronLeft size={13} className="mr-1" /> Refine
            </Button>
            <Button
              className="flex-1 bg-indigo-600 hover:bg-indigo-700 text-white"
              disabled={!campaignName.trim() || preview.count === 0}
              onClick={handleLaunch}
            >
              <Rocket size={13} className="mr-2" />
              Launch to {preview.count} customers
            </Button>
          </div>
        </div>
      )}

      {/* ══════════════════════════════════════════════════════════════════
          STAGE 3 — LAUNCHING
      ══════════════════════════════════════════════════════════════════ */}
      {stage === 'launching' && (
        <Card>
          <CardContent className="py-10 space-y-5 text-center">
            <div className="flex justify-center">
              <Loader2 size={32} className="animate-spin text-indigo-500" />
            </div>
            <div className="space-y-1">
              <p className="font-semibold">
                {launchProgress < 35 ? 'Saving segment…' :
                 launchProgress < 60 ? 'Creating campaign…' :
                 launchProgress < 90 ? 'Sending messages…' :
                                       'Almost done…'}
              </p>
              <p className="text-xs text-muted-foreground">
                {preview?.count} customers · {CHANNEL_LABELS[channel]}
              </p>
            </div>

            {/* Progress bar */}
            <div className="mx-auto w-64 bg-muted rounded-full h-2 overflow-hidden">
              <div
                className="h-full bg-indigo-500 rounded-full transition-all duration-500"
                style={{ width: `${launchProgress}%` }}
              />
            </div>
            <p className="text-xs text-muted-foreground">{launchProgress}%</p>
          </CardContent>
        </Card>
      )}

      {/* ══════════════════════════════════════════════════════════════════
          STAGE 4 — LIVE STATS
      ══════════════════════════════════════════════════════════════════ */}
      {stage === 'stats' && stats && (
        <div className="space-y-4">

          {/* Success banner */}
          <div className="flex items-center gap-2 text-sm text-green-600
                          bg-green-50 dark:bg-green-950/30 border border-green-200
                          dark:border-green-800 rounded-lg px-3 py-2">
            <CheckCircle2 size={14} />
            <span>Campaign launched · updating live every 4s</span>
            <Badge variant="outline" className="ml-auto text-xs">
              {stats.status}
            </Badge>
          </div>

          {/* Live stat tiles */}
          <Card>
            <CardContent className="py-6">
              <div className="grid grid-cols-5 gap-4">
                <StatTile
                  label="Total"
                  value={stats.totalLogs}
                  color="text-foreground"
                />
                <StatTile
                  label="Sent"
                  value={stats.sent}
                  sub={`${pct(stats.sent, stats.totalLogs)}%`}
                  color="text-blue-500"
                />
                <StatTile
                  label="Delivered"
                  value={stats.delivered}
                  sub={`${pct(stats.delivered, stats.totalLogs)}%`}
                  color="text-green-500"
                />
                <StatTile
                  label="Opened"
                  value={stats.opened}
                  sub={`${pct(stats.opened, stats.delivered)}%`}
                  color="text-yellow-500"
                />
                <StatTile
                  label="Clicked"
                  value={stats.clicked}
                  sub={`${pct(stats.clicked, stats.opened)}%`}
                  color="text-indigo-500"
                />
              </div>

              {/* Live funnel bar */}
              <div className="mt-5 space-y-1.5">
                {[
                  { label: 'Delivered', value: stats.delivered, color: 'bg-green-500' },
                  { label: 'Opened',    value: stats.opened,    color: 'bg-yellow-500' },
                  { label: 'Clicked',   value: stats.clicked,   color: 'bg-indigo-500' },
                  { label: 'Failed',    value: stats.failed,    color: 'bg-red-400' },
                ].map(({ label, value, color }) => (
                  <div key={label} className="flex items-center gap-2 text-xs">
                    <span className="w-16 text-right text-muted-foreground shrink-0">
                      {label}
                    </span>
                    <div className="flex-1 bg-muted rounded-full h-1.5 overflow-hidden">
                      <div
                        className={`h-full rounded-full ${color} transition-all duration-700`}
                        style={{
                          width: `${Math.min(
                            pct(value, stats.totalLogs) as unknown as number, 100
                          )}%`
                        }}
                      />
                    </div>
                    <span className="w-8 text-muted-foreground">{value}</span>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          {/* AI Summary */}
          <Card>
            <CardHeader className="pb-2 flex flex-row items-center justify-between">
              <CardTitle className="text-sm flex items-center gap-2">
                <Sparkles size={13} className="text-indigo-400" />
                AI Campaign Analysis
              </CardTitle>
              <Button
                size="sm" variant="outline"
                onClick={handleSummary}
                disabled={
                  loadingSummary ||
                  !['LAUNCHED', 'COMPLETED'].includes(stats.status)
                }
              >
                {loadingSummary ? (
                  <><Loader2 size={12} className="animate-spin mr-1.5" /> Analyzing…</>
                ) : 'Generate Analysis'}
              </Button>
            </CardHeader>
            <CardContent>
              {loadingSummary && <AiThinking label="Analyzing campaign performance…" />}
              {stats.aiSummary && !loadingSummary && (
                <p className="text-sm text-muted-foreground leading-relaxed">
                  {stats.aiSummary}
                </p>
              )}
              {!stats.aiSummary && !loadingSummary && (
                <p className="text-xs text-muted-foreground">
                  {['LAUNCHED', 'COMPLETED'].includes(stats.status)
                    ? 'Click Generate Analysis for an AI summary of campaign performance.'
                    : 'Analysis available once the campaign is launched.'}
                </p>
              )}
            </CardContent>
          </Card>

          {/* Actions */}
          <div className="flex gap-3">
            <Button variant="outline" onClick={handleReset} className="flex-1">
              Launch Another Campaign
            </Button>
            <Button
              variant="ghost"
              onClick={() => navigate(`/campaigns/${campaignId}`)}
            >
              Full Detail →
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}