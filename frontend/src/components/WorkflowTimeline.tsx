import { HistoryRow } from '../api/client'
import { countdown, time } from '../format'

const STAGES = ['INTAKE', 'VERIFICATION', 'UNDERWRITING', 'OFFER', 'DISBURSEMENT']

const OUTCOME_BADGE: Record<string, string> = {
  COMPLETED: 'badge-good',
  FIRED: 'badge-good',
  RECEIVED: 'badge-neutral',
  RUNNING: 'badge-info',
  SCHEDULED: 'badge-info',
  WAITING: 'badge-due',
  RETRYING: 'badge-risk',
  FAILED: 'badge-risk',
  TIMED_OUT: 'badge-risk',
  CANCELED: 'badge-neutral',
}

const KIND_ICON: Record<string, string> = { ACTIVITY: 'A', TIMER: 'T', SIGNAL: 'S' }

/** On-screen companion to the Temporal UI: one group per stage, retries nested under their activity. */
export function WorkflowTimeline({ rows, now }: { rows: HistoryRow[]; now: number }) {
  const stages = STAGES.filter((stage) => rows.some((r) => r.stage === stage))
  if (stages.length === 0) return <p className="text-muted">No history yet.</p>
  return (
    <div className="flex flex-col gap-4">
      {stages.map((stage) => (
        <section key={stage}>
          <h3 className="section-label mb-1.5">{stage.toLowerCase()}</h3>
          <ol className="m-0 flex list-none flex-col gap-1.5 p-0">
            {rows
              .filter((r) => r.stage === stage)
              .map((r, i) => (
                <li key={`${r.at}-${i}`} className="text-[13px]">
                  <div className="flex items-center gap-2">
                    <span className="flex size-5 shrink-0 items-center justify-center rounded bg-primary-soft text-[10px] font-semibold text-primary-hover">
                      {KIND_ICON[r.kind]}
                    </span>
                    <span className="w-[72px] shrink-0 whitespace-nowrap text-[12px] text-muted">{time(r.at)}</span>
                    <span className="truncate">{r.name}</span>
                    <span className={`ml-auto shrink-0 ${OUTCOME_BADGE[r.outcome] ?? 'badge-neutral'}`}>
                      {r.outcome.toLowerCase().replace('_', ' ')}
                      {r.kind === 'TIMER' && r.outcome === 'WAITING' && ` ${countdown(r.nextAt, now)}`}
                    </span>
                  </div>
                  {r.attempt !== undefined && r.attempt > 1 && (
                    <div className="ml-7 mt-1 border-l-2 border-[#fecaca] pl-3 text-[12px] text-muted">
                      attempt {r.attempt}, {r.attempt - 1} {r.attempt === 2 ? 'retry' : 'retries'} so far
                      {r.lastFailure && <span className="text-[#b91c1c]">. Last error: {r.lastFailure}</span>}
                      {r.outcome === 'RETRYING' && r.nextAt && <span>. Next try {countdown(r.nextAt, now)}</span>}
                    </div>
                  )}
                </li>
              ))}
          </ol>
        </section>
      ))}
    </div>
  )
}
