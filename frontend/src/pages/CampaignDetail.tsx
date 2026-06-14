import { useEffect, useState, useCallback } from 'react'
import { useParams } from 'react-router-dom'
import { campaignApi } from '../api'
import { Card, CardContent, CardHeader, CardTitle } from '../../@/components/ui/card'
import { Button } from '../../@/components/ui/button'
import { Badge } from '../../@/components/ui/badge'
import { Skeleton } from '../../@/components/ui/skeleton'
import { Sparkles, Rocket, RefreshCw } from 'lucide-react'
import CampaignVisuals from '../components/CampaignVisuals'

export default function CampaignDetail() {
  const { id } = useParams<{ id: string }>()
  const [campaign, setCampaign]   = useState<any>(null)
  const [summary, setSummary]     = useState('')
  const [loadingSummary, setLoadingSummary] = useState(false)
  const [launching, setLaunching] = useState(false)

  const load = useCallback(() => {
    if (id) campaignApi.getById(id).then(r => setCampaign(r.data))
  }, [id])

  useEffect(() => {
    load()
    const interval = setInterval(load, 5000) // poll every 5s
    return () => clearInterval(interval)
  }, [load])

  async function handleLaunch() {
    if (!id) return
    setLaunching(true)
    try {
      await campaignApi.launch(id)
      load()
    } finally {
      setLaunching(false)
    }
  }

  async function handleSummary() {
    if (!id) return
    setLoadingSummary(true)
    try {
      const r = await campaignApi.aiSummary(id)
      setSummary(r.data.summary)
    } finally {
      setLoadingSummary(false)
    }
  }

  if (!campaign) return (
    <div className="space-y-4">
      <Skeleton className="h-8 w-64" />
      <Skeleton className="h-40 w-full" />
    </div>
  )

  const total     = campaign.totalLogs || 1
  const delivRate = total > 0 ? ((campaign.delivered / total) * 100).toFixed(1) : '0.0'
  const openRate  = total > 0 ? ((campaign.opened / total) * 100).toFixed(1) : '0.0'
  const clickRate = total > 0 ? ((campaign.clicked / total) * 100).toFixed(1) : '0.0'

  const funnel = [
    { label: 'Sent',      value: campaign.totalLogs, color: 'bg-blue-500' },
    { label: 'Delivered', value: campaign.delivered, color: 'bg-green-500' },
    { label: 'Opened',    value: campaign.opened,    color: 'bg-yellow-500' },
    { label: 'Clicked',   value: campaign.clicked,   color: 'bg-indigo-500' },
    { label: 'Failed',    value: campaign.failed,    color: 'bg-red-500' },
  ]

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold">{campaign.name}</h1>
          <p className="text-muted-foreground text-sm mt-1">
            {campaign.segmentName} · {campaign.channel}
            {campaign.launchedAt && ` · Launched ${new Date(campaign.launchedAt).toLocaleString()}`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant={
            campaign.status === 'LAUNCHED'  ? 'default' :
            campaign.status === 'COMPLETED' ? 'secondary' : 'outline'
          }>
            {campaign.status}
          </Badge>
          {campaign.status === 'DRAFT' && (
            <Button size="sm" onClick={handleLaunch} disabled={launching}>
              <Rocket size={13} className="mr-1" />
              {launching ? 'Launching...' : 'Launch'}
            </Button>
          )}
          {campaign.status === 'LAUNCHED' && (
            <Button size="sm" variant="outline" onClick={load}>
              <RefreshCw size={13} className="mr-1" /> Refresh
            </Button>
          )}
        </div>
      </div>

      {/* Funnel Stats */}
      <div className="grid grid-cols-5 gap-3">
        {funnel.map(({ label, value, color }) => (
          <Card key={label}>
            <CardHeader className="pb-1">
              <CardTitle className="text-xs text-muted-foreground">{label}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-xl font-bold">{value}</p>
              <div className={`h-1 rounded-full mt-2 ${color}`}
                style={{ width: `${Math.min((value / total) * 100, 100)}%` }} />
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Rates */}
      <Card>
        <CardHeader>
          <CardTitle className="text-sm">Conversion Rates</CardTitle>
        </CardHeader>
        <CardContent className="flex gap-8 text-sm">
          <div>
            <p className="text-2xl font-bold">{delivRate}%</p>
            <p className="text-muted-foreground text-xs">Delivery Rate</p>
          </div>
          <div>
            <p className="text-2xl font-bold">{openRate}%</p>
            <p className="text-muted-foreground text-xs">Open Rate</p>
          </div>
          <div>
            <p className="text-2xl font-bold">{clickRate}%</p>
            <p className="text-muted-foreground text-xs">Click Rate</p>
          </div>
        </CardContent>
      </Card>

      {/* AI Summary */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="text-sm flex items-center gap-2">
            <Sparkles size={14} className="text-indigo-400" />
            AI Campaign Analysis
          </CardTitle>
          <Button size="sm" variant="outline" onClick={handleSummary} disabled={loadingSummary}>
            {loadingSummary ? 'Analyzing...' : 'Generate Analysis'}
          </Button>
        </CardHeader>
        <CardContent>
          {loadingSummary && (
            <div className="space-y-2">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-4/5" />
              <Skeleton className="h-4 w-3/5" />
            </div>
          )}
          {(summary || campaign.aiSummary) && !loadingSummary && (
            <p className="text-sm text-muted-foreground leading-relaxed">
              {summary || campaign.aiSummary}
            </p>
          )}
          {(summary || campaign.aiSummary) && !loadingSummary && (
          <CampaignVisuals
            totalLogs={campaign.totalLogs}
            sent={campaign.sent}
            delivered={campaign.delivered}
            opened={campaign.opened}
            clicked={campaign.clicked}
            converted={campaign.converted}
            failed={campaign.failed}
          />
        )}
          {!summary && !campaign.aiSummary && !loadingSummary && (
            <p className="text-sm text-muted-foreground">
              Click "Generate Analysis" to get AI insights on this campaign.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}