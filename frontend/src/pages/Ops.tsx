import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api, Durations, Stage, Status } from '../api/client'
import { StatusChip } from '../components/StatusChip'
import { WorkerBadge } from '../components/WorkerBadge'
import { duration, since, useNow } from '../format'

const STAGES: { id: Stage; label: string }[] = [
  { id: 'INTAKE', label: 'Intake' },
  { id: 'VERIFICATION', label: 'Verification' },
  { id: 'UNDERWRITING', label: 'Underwriting' },
  { id: 'OFFER', label: 'Offer' },
  { id: 'DISBURSEMENT', label: 'Disbursement' },
]

const DURATION_ROWS: { key: keyof Durations; label: string }[] = [
  { key: 'reminderAfter', label: 'Underwriter reminder' },
  { key: 'escalateAfter', label: 'Escalation to senior' },
  { key: 'offerExpiresAfter', label: 'Offer expiry' },
  { key: 'retryMaxInterval', label: 'Longest wait between retries' },
  { key: 'retryScheduleToClose', label: 'Give up on a check after' },
  { key: 'stuckAfter', label: 'Flag as stuck after' },
]

export function OpsPage() {
  const now = useNow()
  const queryClient = useQueryClient()
  const summary = useQuery({ queryKey: ['ops'], queryFn: api.opsSummary, refetchInterval: 5000 })
  const durations = useQuery({ queryKey: ['durations'], queryFn: api.durations })
  const refresh = () => queryClient.invalidateQueries()
  const seed = useMutation({ mutationFn: () => api.seed(30), onSuccess: refresh })
  const reset = useMutation({ mutationFn: api.reset, onSuccess: refresh })

  const data = summary.data
  const profiles = durations.data ? Object.keys(durations.data.profiles).sort().reverse() : []

  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="page-title">Operations</h1>
        <WorkerBadge />
        <div className="ml-auto flex gap-2">
          <button className="btn-secondary" disabled={seed.isPending} onClick={() => seed.mutate()}>
            {seed.isPending ? 'Seeding' : 'Seed 30 applications'}
          </button>
          <button
            className="btn-danger"
            disabled={reset.isPending}
            onClick={() => {
              if (window.confirm('Terminate every running workflow and delete all applications?')) reset.mutate()
            }}
          >
            {reset.isPending ? 'Resetting' : 'Reset all data'}
          </button>
        </div>
      </div>
      {seed.data && <p className="text-muted">Started {seed.data.started} applications. They reach their stages within a minute.</p>}
      {reset.data && <p className="text-muted">Terminated {reset.data.terminated} workflows and cleared the data.</p>}

      <section className="grid gap-4 sm:grid-cols-3 lg:grid-cols-5">
        {STAGES.map((s) => (
          <div key={s.id} className="card px-6 py-4">
            <div className="text-[13px] text-muted">{s.label}</div>
            <div className="text-[22px] font-semibold">{data?.openByStage[s.id] ?? ''}</div>
            <div className="text-[13px] text-muted">open</div>
          </div>
        ))}
      </section>

      <div className="flex flex-col gap-6">
        <section className="card overflow-x-auto">
          <div className="card-header">
            SLA breaches
            <span className="font-normal text-muted">underwriting past its reminder deadline</span>
            <span className={`ml-auto ${data?.slaBreaches.length ? 'badge-risk' : 'badge-good'}`}>{data?.slaBreaches.length ?? 0}</span>
          </div>
          <table className="w-full whitespace-nowrap [&_td]:px-4 first:[&_td]:pl-6">
            <tbody className="divide-y divide-[#f3f4f6]">
              {data?.slaBreaches.map((b) => (
                <tr key={b.id}>
                  <td className="td"><Link to={`/cases/${b.id}`} className="font-medium">{b.id}</Link></td>
                  <td className="td">{b.applicant}</td>
                  <td className="td">{b.assignedTo}{b.escalated && <span className="badge-risk ml-2">escalated</span>}</td>
                  <td className="td text-right text-muted">late {since(b.dueAt, now)}</td>
                </tr>
              ))}
              {data?.slaBreaches.length === 0 && (
                <tr><td className="td text-muted">No breaches.</td></tr>
              )}
            </tbody>
          </table>
        </section>

        <section className="card overflow-x-auto">
          <div className="card-header">
            Stuck applications
            <span className="font-normal text-muted">system working for over {data ? duration(data.stuckAfter) : ''}</span>
            <span className={`ml-auto ${data?.stuck.length ? 'badge-due' : 'badge-good'}`}>{data?.stuck.length ?? 0}</span>
          </div>
          <table className="w-full whitespace-nowrap [&_td]:px-4 first:[&_td]:pl-6">
            <tbody className="divide-y divide-[#f3f4f6]">
              {data?.stuck.map((s) => (
                <tr key={s.id}>
                  <td className="td"><Link to={`/cases/${s.id}`} className="font-medium">{s.id}</Link></td>
                  <td className="td">{s.applicant}</td>
                  <td className="td"><StatusChip status={s.status} /></td>
                  <td className="td text-right text-muted">{since(s.since, now)}</td>
                </tr>
              ))}
              {data?.stuck.length === 0 && (
                <tr><td className="td text-muted">Nothing stuck.</td></tr>
              )}
            </tbody>
          </table>
        </section>
      </div>

      <section className="card">
        <div className="card-header">
          Durations
          <span className="font-normal text-muted">same workflow code, two configurations</span>
        </div>
        <table className="w-full">
          <thead>
            <tr className="border-b border-line">
              <th className="th">Setting</th>
              {profiles.map((p) => (
                <th key={p} className="th">
                  {p === 'prod-durations' ? 'Production' : 'Short'}
                  {durations.data?.active === p && <span className="badge-info ml-2">active</span>}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-[#f3f4f6]">
            {DURATION_ROWS.map((row) => (
              <tr key={row.key}>
                <td className="td">{row.label}</td>
                {profiles.map((p) => (
                  <td key={p} className="td font-medium">{duration(durations.data!.profiles[p][row.key])}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {data && (
        <section className="card">
          <div className="card-header flex items-center gap-2">
            Workflow versions
            <span className="badge-neutral ml-auto">worker build {data.worker.build ?? 'unknown'}</span>
          </div>
          <div className="card-body flex flex-col gap-3">
            <div className="flex flex-wrap gap-6">
              {Object.keys(data.openByVersion).length === 0 && <span className="text-muted">No open applications.</span>}
              {Object.entries(data.openByVersion).map(([version, count]) => (
                <div key={version}>
                  <div className="text-[13px] text-muted">Open on {version}</div>
                  <div className="text-[22px] font-semibold">{count}</div>
                </div>
              ))}
            </div>
            <p className="text-[13px] text-muted">
              v2 adds a PEP check to verification. Run <code className="font-mono">make deploy-v2</code> with applications
              in flight: they finish on v1, new ones start on v2.
            </p>
          </div>
        </section>
      )}

      {data && Object.keys(data.byStatus).length > 0 && (
        <section className="card">
          <div className="card-header">All applications by status</div>
          <div className="card-body flex flex-wrap gap-2">
            {Object.entries(data.byStatus).map(([status, count]) => (
              <span key={status} className="inline-flex items-center gap-1.5">
                <StatusChip status={status as Status} />
                <span className="font-semibold">{count}</span>
              </span>
            ))}
          </div>
        </section>
      )}
    </>
  )
}
