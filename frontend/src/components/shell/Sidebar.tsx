import { Activity, ClipboardCheck, ExternalLink, FilePlus2, LayoutDashboard, LogOut, LucideIcon, PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { Role, temporalUiLink } from '../../api/client'
import { ACCESS, ROLES, useAuth } from '../../auth'
import { LogoMark } from './Logo'

type Item = { label: string; to: string; icon: LucideIcon; roles?: Role[]; end?: boolean }

// Each role sees only its own screens. The chaos panel stays off the nav on purpose: open /chaos on a second screen.
const ITEMS: Item[] = [
  { label: 'Overview', to: '/', icon: LayoutDashboard, end: true },
  { label: 'Apply', to: '/apply', icon: FilePlus2, roles: ACCESS.apply },
  { label: 'Underwriting', to: '/underwriting', icon: ClipboardCheck, roles: ACCESS.underwriting },
  { label: 'Operations', to: '/ops', icon: Activity, roles: ACCESS.ops },
]


/** Gray 900 sidebar from the FusionRM shell. 224px expanded, 72px icon rail collapsed. */
export function Sidebar({ collapsed, onToggle }: { collapsed: boolean; onToggle: () => void }) {
  const { me, logout } = useAuth()
  const items = ITEMS.filter((it) => !it.roles || it.roles.includes(me.role))
  const iconBtn =
    'flex size-8 shrink-0 items-center justify-center rounded-md text-sidebar-text hover:bg-sidebar-active hover:text-white'
  const itemClass = (active: boolean) =>
    `group relative flex min-h-[44px] items-center gap-3 rounded-md px-2.5 no-underline ${
      active ? 'bg-sidebar-active text-white hover:text-white' : 'text-sidebar-text hover:bg-sidebar-active/60 hover:text-white'
    } ${collapsed ? 'justify-center px-0' : ''}`

  return (
    <aside
      className={`sticky top-0 flex h-screen shrink-0 flex-col gap-5 bg-sidebar pb-4 text-white transition-[width] ${
        collapsed ? 'w-[72px] px-3' : 'w-[224px] px-4'
      }`}
    >
      {collapsed ? (
        <div className="flex h-16 items-center justify-center">
          <button
            type="button"
            aria-label="Expand sidebar"
            title="Expand sidebar (press [)"
            onClick={onToggle}
            className="group flex size-9 items-center justify-center rounded-md hover:bg-sidebar-active"
          >
            <LogoMark className="size-7 shrink-0 group-hover:hidden" />
            <PanelLeftOpen className="hidden size-5 text-[#d1d5db] group-hover:block" />
          </button>
        </div>
      ) : (
        <div className="flex h-16 items-center gap-2.5 px-1">
          <LogoMark />
          <div className="min-w-0 leading-tight">
            <div className="truncate font-semibold">
              Fusion<span className="text-sidebar-tag">LOS</span>
            </div>
            <div className="text-[11px] font-semibold uppercase tracking-wider text-sidebar-tag">Loan origination</div>
          </div>
          <button type="button" aria-label="Collapse sidebar" title="Collapse sidebar (press [)" onClick={onToggle} className={`${iconBtn} ml-auto`}>
            <PanelLeftClose className="size-[18px]" />
          </button>
        </div>
      )}

      <nav className="flex flex-col gap-1 font-semibold" aria-label="Main">
        {items.map((it) => (
          <NavLink key={it.to} to={it.to} end={it.end} title={collapsed ? it.label : undefined} className={({ isActive }) => itemClass(isActive)}>
            {({ isActive }) => (
              <>
                <it.icon className={`size-5 shrink-0 ${isActive ? 'text-sidebar-tag' : 'text-muted group-hover:text-[#d1d5db]'}`} aria-hidden />
                {!collapsed && <span className="truncate">{it.label}</span>}
              </>
            )}
          </NavLink>
        ))}
        <a href={temporalUiLink()} target="_blank" rel="noreferrer" title={collapsed ? 'Temporal UI' : undefined} className={itemClass(false)}>
          <ExternalLink className="size-5 shrink-0 text-muted group-hover:text-[#d1d5db]" aria-hidden />
          {!collapsed && <span className="truncate">Temporal UI</span>}
        </a>
      </nav>

      <div className={`mt-auto flex items-center gap-2 border-t border-sidebar-active px-1 pt-4 ${collapsed ? 'flex-col px-0' : ''}`}>
        <div
          className="flex size-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-[#6366f1] to-[#7c3aed] text-[12px] font-semibold"
          title={collapsed ? `${me.name} (${ROLES[me.role].label})` : undefined}
        >
          {me.name.slice(0, 1)}
        </div>
        {!collapsed && (
          <div className="min-w-0 flex-1 px-1 leading-snug">
            <span className="block truncate text-[13px] font-semibold">{me.name}</span>
            <span className="block truncate text-[12px] text-sidebar-text">{ROLES[me.role].label}</span>
          </div>
        )}
        <button type="button" onClick={logout} aria-label="Sign out" title="Sign out" className={iconBtn}>
          <LogOut className="size-[18px]" />
        </button>
      </div>
    </aside>
  )
}
