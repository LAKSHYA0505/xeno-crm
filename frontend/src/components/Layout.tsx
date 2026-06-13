import { NavLink } from 'react-router-dom'
import { LayoutDashboard, Users, Filter, Megaphone, Rocket } from 'lucide-react'
import { Separator } from '../../@/components/ui/separator'

const nav = [
  { to: '/',          label: 'Dashboard', icon: LayoutDashboard },
  { to: '/launch',    label: 'Launch Campaign',   icon: Rocket },  
  { to: '/customers', label: 'Customers', icon: Users },
  { to: '/segments',  label: 'Segments',  icon: Filter },
  { to: '/campaigns', label: 'Campaigns', icon: Megaphone },
]

export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen bg-background text-foreground overflow-hidden">
      <aside className="w-56 border-r flex flex-col shrink-0">
        <div className="px-5 py-4">
          <p className="font-bold text-base tracking-tight">SoleStreet</p>
          <p className="text-xs text-muted-foreground mt-0.5">CRM Intelligence</p>
        </div>
        <Separator />
        <nav className="flex-1 p-3 space-y-1">
          {nav.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-2.5 px-3 py-2 rounded-md text-sm transition-colors ${
                  isActive
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                }`
              }
            >
              <Icon size={15} />
              {label}
            </NavLink>
          ))}
        </nav>
        <Separator />
        <div className="px-5 py-3">
          <p className="text-xs text-muted-foreground">Powered by Groq AI</p>
        </div>
      </aside>

      <main className="flex-1 overflow-auto p-6">
        {children}
      </main>
    </div>
  )
}