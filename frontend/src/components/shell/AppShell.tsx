import { Workflow } from 'lucide-react'
import { ReactNode, useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { HowItWorksPanel } from '../../howitworks/HowItWorksPanel'
import { screenFor } from '../../howitworks/content'
import { WorkerBadge } from '../WorkerBadge'
import { Sidebar } from './Sidebar'

function stored(key: string, fallback: boolean): boolean {
  try {
    const v = localStorage.getItem(key)
    return v === null ? fallback : v === 'true'
  } catch {
    return fallback
  }
}

function store(key: string, value: boolean) {
  try {
    localStorage.setItem(key, String(value))
  } catch {
    // storage unavailable, keep it in memory only
  }
}

/** FusionRM application shell: dark sidebar, white 64px top bar, page on #f9fafb, How it works aside. */
export function AppShell({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  const screen = screenFor(pathname)
  const [collapsed, setCollapsed] = useState(() => stored('fusionlos.collapsed', false))
  const [panelOpen, setPanelOpen] = useState(() => stored('fusionlos.howitworks', false))

  const toggleCollapsed = () => setCollapsed((c) => !c)
  const togglePanel = () => setPanelOpen((p) => !p)
  useEffect(() => store('fusionlos.collapsed', collapsed), [collapsed])
  useEffect(() => store('fusionlos.howitworks', panelOpen), [panelOpen])

  // "[" collapses the sidebar, "?" opens How it works, outside text fields.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement | null
      if (e.metaKey || e.ctrlKey || e.altKey || !['[', '?'].includes(e.key)) return
      if (el && (el.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName))) return
      e.preventDefault()
      if (e.key === '[') toggleCollapsed()
      else togglePanel()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <div className="flex min-h-screen">
      <Sidebar collapsed={collapsed} onToggle={toggleCollapsed} />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-4 border-b border-line bg-white px-8 shadow-[0_1px_2px_rgba(0,0,0,0.05)]">
          <span className="font-semibold">{screen.title}</span>
          <span className="ml-auto">
            <WorkerBadge />
          </span>
          <div className="flex items-center gap-1 rounded-lg bg-[#f3f4f6] p-1">
            <button
              type="button"
              role="switch"
              aria-checked={panelOpen}
              title="How it works (press ?)"
              onClick={togglePanel}
              className={`inline-flex min-h-[36px] items-center gap-1.5 rounded-md px-3 font-semibold ${
                panelOpen ? 'bg-white text-ink shadow-[0_1px_2px_rgba(0,0,0,0.08)]' : 'text-muted hover:text-ink'
              }`}
            >
              <Workflow className="size-4" aria-hidden />
              How it works
            </button>
          </div>
        </header>
        <div className="flex min-h-0 flex-1">
          <main className="min-w-0 flex-1 p-8">
            <div className="flex max-w-[1120px] flex-col gap-6">{children}</div>
          </main>
          {panelOpen && <HowItWorksPanel screen={screen} onClose={togglePanel} />}
        </div>
      </div>
    </div>
  )
}
