import { useEffect, useState } from 'react'
import { segmentApi } from '../api'
import { Card, CardContent, CardHeader, CardTitle } from '../../@/components/ui/card'
import { Button } from '../../@/components/ui/button'
import { Input } from '../../@/components/ui/input'
import { Badge } from '../../@/components/ui/badge'
import { Skeleton } from '../../@/components/ui/skeleton'
import { Sparkles, Users, Plus } from 'lucide-react'

export default function Segments() {
  const [segments, setSegments]   = useState<any[]>([])
  const [query, setQuery]         = useState('')
  const [parsing, setParsing]     = useState(false)
  const [preview, setPreview]     = useState<any>(null)
  const [saving, setSaving]       = useState(false)
  const [segName, setSegName]     = useState('')

  useEffect(() => {
    segmentApi.getAll().then(r => setSegments(r.data))
  }, [])

  async function handleParse() {
    if (!query.trim()) return
    setParsing(true)
    setPreview(null)
    try {
      const r = await segmentApi.parse(query)
      setPreview(r.data)
      setSegName(`Segment — ${query.slice(0, 40)}`)
    } catch (e) {
      alert('AI parsing failed. Try again.')
    } finally {
      setParsing(false)
    }
  }

  async function handleSave() {
    if (!preview || !segName.trim()) return
    setSaving(true)
    try {
      await segmentApi.create({
        name: segName,
        description: query,
        nlQuery: query,
        rules: preview.rules
      })
      const r = await segmentApi.getAll()
      setSegments(r.data)
      setPreview(null)
      setQuery('')
      setSegName('')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold">Segments</h1>
        <p className="text-muted-foreground text-sm mt-1">
          Describe your audience in plain English — AI does the rest
        </p>
      </div>

      {/* AI Input */}
      <Card>
        <CardHeader>
          <CardTitle className="text-sm flex items-center gap-2">
            <Sparkles size={14} className="text-indigo-400" />
            AI Segment Builder
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex gap-2">
            <Input
              placeholder='e.g. "customers inactive for 60 days who spent over ₹5000"'
              value={query}
              onChange={e => setQuery(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleParse()}
            />
            <Button onClick={handleParse} disabled={parsing}>
              {parsing ? 'Parsing...' : 'Parse with AI'}
            </Button>
          </div>

          {parsing && (
            <div className="space-y-2">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-2/3" />
            </div>
          )}

          {preview && (
            <div className="border rounded-lg p-4 space-y-3 bg-accent/30">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Users size={14} />
                  <span className="text-sm font-semibold">{preview.count} customers matched</span>
                </div>
                <Badge variant="secondary">AI Generated</Badge>
              </div>

              <div className="text-xs text-muted-foreground font-mono bg-background rounded p-2">
                {JSON.stringify(JSON.parse(preview.rules), null, 2)}
              </div>

              <div>
                <p className="text-xs text-muted-foreground mb-1">Sample customers:</p>
                <div className="space-y-1">
                  {preview.sample?.slice(0, 3).map((c: any) => (
                    <div key={c.id} className="text-xs flex justify-between">
                      <span>{c.name} · {c.city}</span>
                      <span className="text-muted-foreground">₹{c.totalSpent?.toLocaleString()}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="flex gap-2 pt-1">
                <Input
                  placeholder="Segment name..."
                  value={segName}
                  onChange={e => setSegName(e.target.value)}
                  className="text-sm"
                />
                <Button onClick={handleSave} disabled={saving} size="sm">
                  <Plus size={13} className="mr-1" />
                  {saving ? 'Saving...' : 'Save Segment'}
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Segments list */}
      <div className="grid grid-cols-2 gap-3">
        {segments.map(s => (
          <Card key={s.id}>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">{s.name}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {s.nlQuery && (
                <p className="text-xs text-muted-foreground italic">"{s.nlQuery}"</p>
              )}
              <div className="flex items-center justify-between">
                <Badge variant="outline" className="text-xs">
                  <Users size={10} className="mr-1" />
                  {s.customerCount} customers
                </Badge>
                <span className="text-xs text-muted-foreground">
                  {new Date(s.createdAt).toLocaleDateString()}
                </span>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}