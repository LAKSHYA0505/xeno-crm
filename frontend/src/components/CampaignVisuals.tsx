import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell,
  PieChart, Pie, Legend,
} from 'recharts'

interface CampaignVisualsProps {
  totalLogs: number
  sent: number
  delivered: number
  opened: number
  clicked: number
  converted: number
  failed: number
}

const FUNNEL_COLORS = ['#3b82f6', '#22c55e', '#eab308', '#6366f1', '#10b981']
const PIE_COLORS    = ['#10b981', '#6366f1', '#eab308', '#22c55e', '#ef4444']

// Hoisted style objects (avoids the double-brace JSX gotcha)
const barMargin   = { left: 8, right: 16 }
const tickStyle   = { fontSize: 12 }
const cursorStyle = { fill: 'rgba(99,102,241,0.08)' }
const legendStyle = { fontSize: 11 }

export default function CampaignVisuals(s: CampaignVisualsProps) {
  const funnelData = [
    { stage: 'Sent',      count: s.sent },
    { stage: 'Delivered', count: s.delivered },
    { stage: 'Opened',    count: s.opened },
    { stage: 'Clicked',   count: s.clicked },
    { stage: 'Orders',    count: s.converted },
  ]

  // Non-overlapping outcome buckets (monotonic funnel → clean breakdown)
  const pieData = [
    { name: 'Converted',          value: s.converted },
    { name: 'Clicked, no order',  value: Math.max(s.clicked   - s.converted, 0) },
    { name: 'Opened, no click',   value: Math.max(s.opened    - s.clicked,   0) },
    { name: 'Delivered, no open', value: Math.max(s.delivered - s.opened,    0) },
    { name: 'Failed',             value: s.failed },
  ].filter(d => d.value > 0)

  return (
  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
    <div>
      <p className="text-xs text-muted-foreground mb-2">Conversion funnel</p>
      <ResponsiveContainer width="100%" height={240}>
        <BarChart data={funnelData} layout="vertical" margin={barMargin}>
          <XAxis type="number" hide />
          <YAxis type="category" dataKey="stage" width={70}
            tick={tickStyle} axisLine={false} tickLine={false} />
          <Tooltip cursor={cursorStyle} />
          <Bar dataKey="count" radius={[0, 4, 4, 0]}>
            {funnelData.map((_, i) => <Cell key={i} fill={FUNNEL_COLORS[i]} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>

    <div>
      <p className="text-xs text-muted-foreground mb-2">Outcome breakdown</p>
      <ResponsiveContainer width="100%" height={240}>
        <PieChart>
          <Pie data={pieData} dataKey="value" nameKey="name"
            innerRadius={45} outerRadius={70} paddingAngle={2}>
            {pieData.map((_, i) => <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />)}
          </Pie>
          <Tooltip />
          <Legend wrapperStyle={legendStyle} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  </div>
)
}