import { useEffect, useState } from 'react'
import { customerApi } from '../api'
import { Card, CardContent, CardHeader, CardTitle } from '../../@/components/ui/card'
import { Input } from '../../@/components/ui/input'
import { Button } from '../../@/components/ui/button'
import { Badge } from '../../@/components/ui/badge'
import { Search, ChevronLeft, ChevronRight } from 'lucide-react'

export default function Customers() {
  const [data, setData]         = useState<any>(null)
  const [page, setPage]         = useState(0)
  const [search, setSearch]     = useState('')
  const [query, setQuery]       = useState('')
  const [selected, setSelected] = useState<any>(null)
  const [orders, setOrders]     = useState<any[]>([])

  useEffect(() => {
    customerApi.getAll(page, 20, query).then(r => setData(r.data))
  }, [page, query])

  function handleSearch() {
    setPage(0)
    setQuery(search)
  }

  function selectCustomer(c: any) {
    setSelected(c)
    customerApi.getOrders(c.id).then(r => setOrders(r.data))
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold">Customers</h1>
        <p className="text-muted-foreground text-sm mt-1">
          {data?.totalElements ?? 0} total customers
        </p>
      </div>

      <div className="flex gap-2">
        <Input
          placeholder="Search by name or email..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleSearch()}
          className="max-w-sm"
        />
        <Button variant="outline" onClick={handleSearch}>
          <Search size={14} className="mr-2" /> Search
        </Button>
      </div>

      <div className="flex gap-4">
        {/* Customer list */}
        <Card className="flex-1">
          <CardContent className="p-0">
            <div className="divide-y">
              {data?.content?.map((c: any) => (
                <div
                  key={c.id}
                  onClick={() => selectCustomer(c)}
                  className={`flex items-center justify-between px-4 py-3 cursor-pointer hover:bg-accent transition-colors ${
                    selected?.id === c.id ? 'bg-accent' : ''
                  }`}
                >
                  <div>
                    <p className="text-sm font-medium">{c.name}</p>
                    <p className="text-xs text-muted-foreground">{c.email} · {c.city}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-semibold">₹{c.totalSpent?.toLocaleString()}</p>
                    <p className="text-xs text-muted-foreground">{c.orderCount} orders</p>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Customer detail */}
        {selected && (
          <Card className="w-72 h-fit">
            <CardHeader>
              <CardTitle className="text-sm">{selected.name}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="space-y-1 text-muted-foreground">
                <p>{selected.email}</p>
                <p>{selected.phone}</p>
                <p>{selected.city} · <Badge variant="outline">{selected.gender}</Badge></p>
              </div>
              <div className="pt-2 border-t space-y-1">
                <p className="font-medium">Orders ({orders.length})</p>
                {orders.slice(0, 4).map((o: any) => (
                  <div key={o.id} className="flex justify-between text-xs text-muted-foreground">
                    <span>{new Date(o.orderedAt).toLocaleDateString()}</span>
                    <span className="font-medium text-foreground">₹{o.amount}</span>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        )}
      </div>

      {/* Pagination */}
      {data && (
        <div className="flex items-center gap-3 text-sm text-muted-foreground">
          <Button
            variant="outline" size="sm"
            disabled={page === 0}
            onClick={() => setPage(p => p - 1)}
          >
            <ChevronLeft size={14} />
          </Button>
          <span>Page {page + 1} of {data.totalPages}</span>
          <Button
            variant="outline" size="sm"
            disabled={page >= data.totalPages - 1}
            onClick={() => setPage(p => p + 1)}
          >
            <ChevronRight size={14} />
          </Button>
        </div>
      )}
    </div>
  )
}