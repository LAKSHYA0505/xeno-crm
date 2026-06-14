import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { campaignApi, segmentApi } from "../api";
import { Card, CardContent } from "../../@/components/ui/card";
import { Button } from "../../@/components/ui/button";
import { Input } from "../../@/components/ui/input";
import { Badge } from "../../@/components/ui/badge";
import { Skeleton } from "../../@/components/ui/skeleton";
import { Plus, Rocket, X, Megaphone } from "lucide-react";

export default function Campaigns() {
  const [campaigns, setCampaigns] = useState<any[]>([]);
  const [segments, setSegments]   = useState<any[]>([]);
  const [open, setOpen]           = useState(false);
  const [loading, setLoading]     = useState(true);
  const [form, setForm]           = useState({
    name: "", segmentId: "", messageTemplate: "", channel: "whatsapp",
  });
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    campaignApi.getAll().then((r) => setCampaigns(r.data)).finally(() => setLoading(false));
    segmentApi.getAll().then((r) => setSegments(r.data));
  }, []);

  async function handleCreate() {
    if (!form.name || !form.segmentId) return;
    setCreating(true);
    try {
      await campaignApi.create(form);
      const r = await campaignApi.getAll();
      setCampaigns(r.data);
      setOpen(false);
      setForm({ name: "", segmentId: "", messageTemplate: "", channel: "whatsapp" });
    } finally {
      setCreating(false);
    }
  }

  async function handleLaunch(id: string, e: React.MouseEvent) {
    e.stopPropagation();
    await campaignApi.launch(id);
    const r = await campaignApi.getAll();
    setCampaigns(r.data);
  }

  const statusColor: Record<string, any> = {
    LAUNCHED: "default", COMPLETED: "secondary", DRAFT: "outline",
  };

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Campaigns</h1>
          <p className="text-muted-foreground text-sm mt-1">{campaigns.length} total</p>
        </div>
        <Button onClick={() => setOpen(true)}>
          <Plus size={14} className="mr-2" />New Campaign
        </Button>
      </div>

      {/* Inline Modal */}
      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          {/* Backdrop */}
          <div
            className="absolute inset-0 bg-black/50 backdrop-blur-sm"
            onClick={() => setOpen(false)}
          />
          {/* Modal */}
          <div className="relative bg-background border rounded-xl shadow-xl w-full max-w-md p-6 space-y-4 z-10">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold">Create Campaign</h2>
              <button
                onClick={() => setOpen(false)}
                className="text-muted-foreground hover:text-foreground transition-colors"
              >
                <X size={18} />
              </button>
            </div>

            <Input
              placeholder="Campaign name"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />

            <select
              className="w-full border rounded-md px-3 py-2 text-sm bg-background"
              value={form.segmentId}
              onChange={(e) => setForm((f) => ({ ...f, segmentId: e.target.value }))}
            >
              <option value="">Select segment...</option>
              {segments.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.customerCount} customers)
                </option>
              ))}
            </select>

            <select
              className="w-full border rounded-md px-3 py-2 text-sm bg-background"
              value={form.channel}
              onChange={(e) => setForm((f) => ({ ...f, channel: e.target.value }))}
            >
              <option value="whatsapp">WhatsApp</option>
              <option value="sms">SMS</option>
              <option value="email">Email</option>
            </select>

            <textarea
              className="w-full border rounded-md px-3 py-2 text-sm bg-background resize-none"
              rows={3}
              placeholder="Message template (optional — AI will generate if empty)"
              value={form.messageTemplate}
              onChange={(e) => setForm((f) => ({ ...f, messageTemplate: e.target.value }))}
            />

            <p className="text-xs text-muted-foreground">
              Leave message empty to let AI generate personalized messages per customer.
            </p>

            <Button className="w-full" onClick={handleCreate} disabled={creating}>
              {creating ? "Creating..." : "Create Campaign"}
            </Button>
          </div>
        </div>
      )}

      {/* Campaign List */}
      <div className="space-y-3">
        {loading ? (
          Array.from({ length: 3 }).map((_, i) => (
            <Card key={i}>
              <CardContent className="flex items-center justify-between py-4">
                <div className="space-y-2">
                  <Skeleton className="h-4 w-48" />
                  <Skeleton className="h-3 w-64" />
                </div>
                <Skeleton className="h-8 w-24" />
              </CardContent>
            </Card>
          ))
        ) : campaigns.length === 0 ? (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-12 text-center">
              <Megaphone className="text-muted-foreground mb-2" size={24} />
              <p className="text-sm text-muted-foreground">
                No campaigns yet — create one or launch from the Launch page.
              </p>
            </CardContent>
          </Card>
        ) : (
          campaigns.map((c: any) => (
            <Card
              key={c.id}
              className="cursor-pointer hover:bg-accent/50 transition-colors"
              onClick={() => navigate(`/campaigns/${c.id}`)}
            >
              <CardContent className="flex items-center justify-between py-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <p className="font-medium text-sm">{c.name}</p>
                    <Badge variant={statusColor[c.status] ?? "outline"}>
                      {c.status}
                    </Badge>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {c.segmentName} · {c.channel} · {c.totalLogs} messages
                  </p>
                </div>

                <div className="flex items-center gap-6 text-xs text-muted-foreground">
                  <div className="text-center">
                    <p className="font-semibold text-foreground">{c.delivered}</p>
                    <p>delivered</p>
                  </div>
                  <div className="text-center">
                    <p className="font-semibold text-foreground">{c.opened}</p>
                    <p>opened</p>
                  </div>
                  <div className="text-center">
                    <p className="font-semibold text-foreground">{c.clicked}</p>
                    <p>clicked</p>
                  </div>
                  {c.status === "DRAFT" && (
                    <Button size="sm" onClick={(e) => handleLaunch(c.id, e)}>
                      <Rocket size={13} className="mr-1" />Launch
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}