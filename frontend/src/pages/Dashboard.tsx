import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { campaignApi, customerApi, segmentApi } from '../api'
import { Card, CardContent, CardHeader, CardTitle } from '../../@/components/ui/card'
import { Badge } from '../../@/components/ui/badge'
import { Users, Filter, Megaphone, Sparkles } from 'lucide-react'

export default function Dashboard() {
  const [campaigns, setCampaigns]     = useState<any[]>([])
  const [customerCount, setCustomerCount] = useState(0)
  const [segmentCount, setSegmentCount]   = useState(0)
  const navigate = useNavigate()

  useEffect(() => {
    customerApi.getAll(0, 1).then(r => setCustomerCount(r.data.totalElements))
    segmentApi.getAll().then(r => setSegmentCount(r.data.length))
    campaignApi.getAll().then(r => setCampaigns(r.data.slice(0, 5)))
  }, [])

  const stats = [
    { label: 'Total Customers', value: customerCount, icon: Users },
    { label: 'Segments',        value: segmentCount,  icon: Filter },
    { label: 'Campaigns',       value: campaigns.length, icon: Megaphone },
    { label: 'AI Features',     value: 3,             icon: Sparkles },
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
        {stats.map(({ label, value, icon: Icon }) => (
          <Card key={label}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                {label}
              </CardTitle>
              <Icon size={14} className="text-muted-foreground" />
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
          {campaigns.length === 0 ? (
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