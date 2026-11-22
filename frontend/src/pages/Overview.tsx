import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Clock, Database, RefreshCw, ShieldCheck, Undo2, Workflow } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api, LoanApplication, Role } from '../api/client'
import { ROLES, useAuth } from '../auth'
import { LogoMark } from '../components/shell/Logo'
import { statusTone } from '../components/StatusChip'
import { OVERVIEW } from '../howitworks/content'

/** What the system shows, one card per capability. */
const PROOFS = [
  {
    icon: ShieldCheck,
    title: 'Nothing gets lost',
    business: 'Applications do not end up in an unknown state after a crash.',
    temporal: 'Stop the worker mid stage. On restart the workflow continues from the last completed step.',
    try: { label: 'Submit an application', to: '/apply' },
  },
  {
    icon: RefreshCw,
    title: 'Failures handle themselves',
    business: 'A vendor outage delays applications. They continue when the vendor recovers.',
    temporal: 'A retry policy on each call. Attempts and errors are visible, no retry code in the app.',
    try: { label: 'Break the bureau', to: '/chaos' },
  },
  {
    icon: Clock,
    title: 'Waiting is free and managed',
    business: 'Reminders, escalation to a senior underwriter and offer expiry run on schedule.',
    temporal: 'Durable timers and signals. A wait of days holds no worker thread and survives restarts.',
    try: { label: 'Open the queue', to: '/underwriting' },
  },
  {
    icon: Undo2,
    title: 'Compensation is built in',
    business: 'If payout fails, the fund reservation and the new loan account are reversed.',
    temporal: 'Saga steps with compensations run in reverse, each one on record.',
    try: { label: 'Break core banking', to: '/chaos' },
  },
  {
    icon: Workflow,
    title: 'Change without breaking in-flight loans',
    business: 'Process changes can be deployed while applications are in progress.',
    temporal: 'Versioned workflow code. Old applications finish on old logic, new ones take the new path.',
    try: { label: 'See versions in Operations', to: '/ops' },
  },
]

const OWNERSHIP = [
  { icon: Workflow, title: 'Temporal owns the process', detail: 'Stages, waits, retries, timers, and the status of each application.' },
  { icon: Database, title: 'MongoDB owns the data', detail: 'Application document, check results, decisions and the audit projection.' },
  { icon: RefreshCw, title: 'Existing services do the work', detail: 'KYC, bureau, sanctions, valuation, core banking, called as activities.' },
]

function count(apps: LoanApplication[], test: (a: LoanApplication) => boolean) {
  return apps.filter(test).length
}

const HOME_LABEL: Record<Role, string> = {
  APPLICANT: 'Start an application',
  UNDERWRITER: 'Open my queue',
  SENIOR_UNDERWRITER: 'Open the team queue',
  OPERATIONS: 'Open operations',
}

export function OverviewPage() {
  const { me } = useAuth()
  const apps = useQuery({ queryKey: ['applications'], queryFn: api.listApplications, refetchInterval: 5000 })
  const list = apps.data ?? []
  const stats = [
    { label: 'Applications', value: list.length },
    { label: 'In progress', value: count(list, (a) => statusTone(a.status) === 'running') },
    { label: 'Waiting on a person', value: count(list, (a) => statusTone(a.status) === 'waiting') },
    { label: 'Paid out', value: count(list, (a) => a.status === 'COMPLETED') },
  ]

  return (
    <>
      <section className="card overflow-hidden">
        <div className="flex flex-col gap-4 bg-gradient-to-br from-[#eef2ff] to-white px-8 py-7">
          <div className="flex items-center gap-3">
            <LogoMark className="size-10 shrink-0" />
            <div>
              <div className="text-[20px] font-semibold">
                Fusion<span className="text-primary">LOS</span>
              </div>
              <div className="section-label text-primary">Loan origination on Temporal and MongoDB</div>
            </div>
          </div>
          <p className="max-w-[760px] text-[16px] leading-7 text-[#374151]">{OVERVIEW.objective}</p>
          <div className="flex flex-wrap gap-3">
            <Link to={ROLES[me.role].home} className="btn-primary no-underline hover:text-white">
              {HOME_LABEL[me.role]} <ArrowRight className="size-4" />
            </Link>
          </div>
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((s) => (
          <div key={s.label} className="card px-6 py-4">
            <div className="text-[13px] text-muted">{s.label}</div>
            <div className="text-[22px] font-semibold">{apps.isLoading ? '' : s.value}</div>
          </div>
        ))}
      </section>

      {apps.isSuccess && list.length === 0 && (
        <p className="card card-body text-muted">
          {me.role === 'APPLICANT'
            ? 'You have no applications yet.'
            : 'No applications yet. Operations can seed 30 sample applications.'}
        </p>
      )}

      <section className="card">
        <div className="card-header">One application, five stages, one workflow</div>
        <ol className="card-body m-0 grid list-none gap-4 p-0 sm:grid-cols-5">
          {OVERVIEW.flow.map((step, i) => (
            <li key={step.title} className="flex flex-col gap-1.5 px-6 py-4 sm:px-0 sm:first:pl-6 sm:last:pr-6">
              <div className="flex items-center gap-2">
                <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-primary text-[12px] font-semibold text-white">
                  {i + 1}
                </span>
                <span className="font-semibold">{step.title}</span>
              </div>
              <span className="text-[13px] leading-5 text-muted">{step.detail}</span>
            </li>
          ))}
        </ol>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-[16px] font-semibold">What it shows</h2>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {PROOFS.map((p) => (
            <div key={p.title} className="card flex flex-col gap-3 px-6 py-5">
              <div className="flex items-center gap-2.5">
                <span className="flex size-9 items-center justify-center rounded-md bg-primary-soft text-primary">
                  <p.icon className="size-5" aria-hidden />
                </span>
                <span className="font-semibold">{p.title}</span>
              </div>
              <p className="text-[13px] leading-5">
                <span className="font-semibold">Business: </span>
                {p.business}
              </p>
              <p className="text-[13px] leading-5 text-muted">
                <span className="font-semibold text-ink">Temporal: </span>
                {p.temporal}
              </p>
              <div className="mt-auto">
                <Link to={p.try.to} className="inline-flex items-center gap-1 text-[13px] font-semibold">
                  {p.try.label} <ArrowRight className="size-3.5" />
                </Link>
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="card">
        <div className="card-header">Why Temporal instead of status columns and cron jobs</div>
        <table className="w-full">
          <thead>
            <tr className="border-b border-line">
              <th className="th w-1/2">Before</th>
              <th className="th">With Temporal</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#f3f4f6]">
            {OVERVIEW.whyTemporal.map((w) => (
              <tr key={w.after}>
                <td className="td text-muted">{w.before}</td>
                <td className="td">{w.after}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        {OWNERSHIP.map((o) => (
          <div key={o.title} className="card flex gap-3 px-6 py-5">
            <o.icon className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden />
            <div>
              <div className="font-semibold">{o.title}</div>
              <div className="text-[13px] leading-5 text-muted">{o.detail}</div>
            </div>
          </div>
        ))}
      </section>

      <p className="text-[13px] text-muted">
        Not a good fit: short synchronous flows, or a process that business analysts must draw rather than developers write
        as code.
      </p>
    </>
  )
}
