import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { campaignApi, customerApi, segmentApi } from '../api'
import { Card, CardContent, CardHeader, CardTitle } from '../../@/components/ui/card'
import { Badge } from '../../@/components/ui/badge'
import { Skeleton } from '../../@/components/ui/skeleton'
import { Users, Filter, Megaphone, Sparkles } from 'lucide-react'

export default function Dashboard() {
  const [campaigns, setCampaigns]     = useState<any[]>([])
  const [customerCount, setCustomerCount] = useState(0)
  const [segmentCount, setSegmentCount]   = useState(0)
  const [loading, setLoading]         = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    Promise.all([
      customerApi.getAll(0, 1).then(r => setCustomerCount(r.data.totalElements)),
      segmentApi.getAll().then(r => setSegmentCount(r.data.length)),
      campaignApi.getAll().then(r => setCampaigns(r.data.slice(0, 5))),
    ]).finally(() => setLoading(false))
  }, [])

  const stats = [
    { label: 'Total Customers', value: customerCount,    icon: Users,    tint: 'bg-indigo-50 text-indigo-600' },
    { label: 'Segments',        value: segmentCount,     icon: Filter,   tint: 'bg-blue-50 text-blue-600' },
    { label: 'Campaigns',       value: campaigns.length, icon: Megaphone, tint: 'bg-violet-50 text-violet-600' },
    { label: 'AI Magic',       value: '✨',            icon: Sparkles, tint: 'bg-yellow-50 text-yellow-600' },
  ]

  const statusColor: Record<string, string> = {
    LAUNCHED:  'default',
    COMPLETED: 'secondary',
    DRAFT:     'outline',
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="text-muted-foreground text-sm mt-1">
          SoleStreet — AI-native campaign intelligence
        </p>
      </div>

      <div className="grid grid-cols-4 gap-4">
        {loading
          ? Array.from({ length: 4 }).map((_, i) => (
              <Card key={i}>
                <CardHeader className="flex flex-row items-center justify-between pb-2">
                  <Skeleton className="h-3 w-24" />
                  <Skeleton className="h-7 w-7 rounded-md" />
                </CardHeader>
                <CardContent>
                  <Skeleton className="h-8 w-12" />
                </CardContent>
              </Card>
            ))
          : stats.map(({ label, value, icon: Icon, tint }) => (
              <Card key={label}>
                <CardHeader className="flex flex-row items-center justify-between pb-2">
                  <CardTitle className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                    {label}
                  </CardTitle>
                  <div className={`rounded-md p-1.5 ${tint}`}>
                    <Icon size={14} />
                  </div>
                </CardHeader>
                <CardContent>
                  <p className="text-2xl font-bold">{value}</p>
                </CardContent>
              </Card>
            ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-sm font-semibold">Recent Campaigns</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {loading ? (
            Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="flex items-center justify-between p-3 rounded-lg border">
                <div className="space-y-2">
                  <Skeleton className="h-4 w-40" />
                  <Skeleton className="h-3 w-28" />
                </div>
                <Skeleton className="h-5 w-16" />
              </div>
            ))
          ) : campaigns.length === 0 ? (
            <p className="text-muted-foreground text-sm">No campaigns yet.</p>
          ) : (
            campaigns.map(c => (
              <div
                key={c.id}
                onClick={() => navigate(`/campaigns/${c.id}`)}
                className="flex items-center justify-between p-3 rounded-lg border cursor-pointer hover:bg-accent transition-colors"
              >
                <div>
                  <p className="text-sm font-medium">{c.name}</p>
                  <p className="text-xs text-muted-foreground">{c.segmentName} · {c.totalLogs} messages</p>
                </div>
                <div className="flex items-center gap-3 text-xs text-muted-foreground">
                  <span>{c.delivered} delivered</span>
                  <span>{c.opened} opened</span>
                  <Badge variant={statusColor[c.status] as any ?? 'outline'}>
                    {c.status}
                  </Badge>
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  )
}