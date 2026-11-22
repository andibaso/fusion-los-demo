import { X } from 'lucide-react'
import { ReactNode, useState } from 'react'
import { Screen } from './content'

const TABS = [
  { id: 'business', label: 'Business' },
  { id: 'temporal', label: 'Why Temporal' },
  { id: 'flow', label: 'Flow' },
  { id: 'code', label: 'Code' },
] as const

type TabId = (typeof TABS)[number]['id']

function Tile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-sidebar-active px-3 py-2.5">
      <div className="text-[13px] text-sidebar-text">{label}</div>
      <div className="font-semibold text-white">{value}</div>
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="text-[11px] font-semibold uppercase tracking-wider text-sidebar-text">{title}</div>
      <div className="text-[13px] leading-5 text-[#d1d5db]">{children}</div>
    </div>
  )
}

/** Dark read only aside, as in FusionRM. Content comes from howitworks/content.ts. */
export function HowItWorksPanel({ screen, onClose }: { screen: Screen; onClose: () => void }) {
  const [tab, setTab] = useState<TabId>('business')
  return (
    <aside className="sticky top-16 flex h-[calc(100vh-64px)] w-[400px] shrink-0 flex-col overflow-y-auto bg-sidebar text-white">
      <div className="flex items-center gap-2 border-b border-sidebar-active px-5 py-4">
        <span className="font-semibold">How it works</span>
        <span className="text-[13px] text-sidebar-text">{screen.title}</span>
        <button
          type="button"
          aria-label="Close how it works"
          onClick={onClose}
          className="ml-auto flex size-9 items-center justify-center rounded-md text-sidebar-text hover:bg-sidebar-active hover:text-white"
        >
          <X className="size-4" />
        </button>
      </div>
      <div className="flex flex-wrap gap-1.5 border-b border-sidebar-active px-5 py-3" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={`min-h-[36px] rounded-md px-2.5 text-[13px] font-medium ${
              tab === t.id ? 'bg-primary text-white' : 'bg-sidebar-active text-[#d1d5db] hover:text-white'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="flex flex-col gap-4 px-5 py-4">
        {tab === 'business' && (
          <>
            <Section title="Business objective">{screen.objective}</Section>
            <div className="grid grid-cols-2 gap-2">
              {screen.outcomes.map((o) => (
                <Tile key={o.label} label={o.label} value={o.value} />
              ))}
            </div>
          </>
        )}

        {tab === 'temporal' && (
          <ol className="m-0 flex list-none flex-col gap-3 p-0">
            {screen.whyTemporal.map((w) => (
              <li key={w.after} className="rounded-md bg-sidebar-active p-3 text-[13px] leading-5">
                <div className="text-sidebar-text">
                  <span className="font-semibold text-[#fca5a5]">Before: </span>
                  {w.before}
                </div>
                <div className="mt-1.5 text-white">
                  <span className="font-semibold text-[#86efac]">With Temporal: </span>
                  {w.after}
                </div>
              </li>
            ))}
          </ol>
        )}

        {tab === 'flow' && (
          <ol className="m-0 flex list-none flex-col gap-4 p-0">
            {screen.flow.map((step, i) => (
              <li key={step.title} className="flex gap-3">
                <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-primary text-[12px] font-semibold">
                  {i + 1}
                </span>
                <span>
                  <span className="block font-semibold text-white">{step.title}</span>
                  <span className="text-[13px] text-[#d1d5db]">{step.detail}</span>
                </span>
              </li>
            ))}
          </ol>
        )}

        {tab === 'code' && screen.code && (
          <Section title={screen.code.file}>
            <pre className="overflow-x-auto rounded-md bg-code p-3 font-mono text-[12px] leading-5 text-[#c7d2fe]">
              {screen.code.text}
            </pre>
          </Section>
        )}

        {screen.live && <Section title="Live on this screen">{screen.live}</Section>}
      </div>
    </aside>
  )
}
