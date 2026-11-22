import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ExternalLink } from 'lucide-react'
import { api, ApiError, Decision, DocumentType, temporalUiLink } from '../api/client'
import { CheckChips } from '../components/CheckChips'
import { LedgerCard } from '../components/LedgerCard'
import { StatusChip } from '../components/StatusChip'
import { WorkflowTimeline } from '../components/WorkflowTimeline'
import { countdown, money, time, useNow } from '../format'
import { useAuth } from '../auth'

export function CaseDetailPage() {
  const { id = '' } = useParams()
  const { me } = useAuth()
  const user = me.id
  const now = useNow()
  const queryClient = useQueryClient()
  const [item, setItem] = useState<DocumentType>('PAYSLIP')
  const [note, setNote] = useState('')

  const application = useQuery({
    queryKey: ['application', id],
    queryFn: () => api.getApplication(id),
    refetchInterval: 2000,
    retry: false,
  })
  const history = useQuery({ queryKey: ['history', id], queryFn: () => api.getHistory(id), refetchInterval: 2000 })
  const decide = useMutation({
    mutationFn: (decision: Decision) => api.decide(id, decision, note || undefined, item),
    onSuccess: () => {
      setNote('')
      queryClient.invalidateQueries({ queryKey: ['application', id] })
    },
  })

  const app = application.data
  if (application.error instanceof ApiError && application.error.status === 404) {
    return (
      <div className="card card-body flex flex-col gap-2">
        <p>No application {id}. It may have been removed by a reset.</p>
        <Link to="/underwriting">Back to the queue</Link>
      </div>
    )
  }
  if (!app) return <p className="text-muted">Loading case {id}</p>

  const uw = app.underwriting
  const inUnderwriting = app.status?.startsWith('UNDERWRITING_') ?? false
  const isUnderwriter = me.role === 'UNDERWRITER' || me.role === 'SENIOR_UNDERWRITER'
  // Escalated cases belong to a senior underwriter; the API enforces the same rule.
  const needsSenior = app.status === 'UNDERWRITING_ESCALATED' && me.role !== 'SENIOR_UNDERWRITER'
  const canDecide = isUnderwriter && !needsSenior && inUnderwriting && app.status !== 'UNDERWRITING_INFO_REQUESTED'

  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <Link to={isUnderwriter ? '/underwriting' : '/ops'} className="inline-flex items-center gap-1 font-semibold">
          <ArrowLeft className="size-4" /> {isUnderwriter ? 'Queue' : 'Operations'}
        </Link>
        <h1 className="page-title">Case {id}</h1>
        <StatusChip status={app.status} />
        {uw?.fourEyesRequired && <span className="badge-violet">4 eyes</span>}
        {uw?.reviewReasons?.includes('PEP_REVIEW') && <span className="badge-due">PEP review</span>}
        <span className="badge-neutral" title="Workflow version this application runs on">
          workflow {app.workflow?.version ?? 'v1'}
        </span>
        <a href={temporalUiLink(id)} target="_blank" rel="noreferrer" className="ml-auto inline-flex items-center gap-1 font-semibold">
          Open in Temporal UI <ExternalLink className="size-3.5" />
        </a>
      </div>

      <div className="grid grid-cols-[repeat(auto-fit,minmax(320px,1fr))] items-start gap-6">
        <section className="card">
          <div className="card-header">Application</div>
          <div className="card-body flex flex-col gap-5">
            <dl className="dl m-0">
              <dt>Applicant</dt><dd>{app.applicant.name}</dd>
              <dt>Monthly income</dt><dd>{money(app.applicant.monthlyIncome)}</dd>
              <dt>Amount</dt><dd>{money(app.amount, app.currency)}</dd>
              <dt>Tenor</dt><dd>{app.tenorMonths} months</dd>
              <dt>Assigned to</dt><dd>{uw?.assignedTo ?? ''}{uw?.escalated ? ' (escalated)' : ''}</dd>
              {inUnderwriting && uw?.reminderAt && <><dt>Reminder</dt><dd>{countdown(uw.reminderAt, now)}</dd></>}
              {inUnderwriting && uw?.escalateAt && !uw.escalated && <><dt>Escalation</dt><dd>{countdown(uw.escalateAt, now)}</dd></>}
            </dl>
            <div>
              <h3 className="section-label mb-1">Checks</h3>
              <CheckChips checks={app.checks} />
            </div>
            {(app.documents?.length ?? 0) > 0 && (
              <div>
                <h3 className="section-label mb-1">Documents</h3>
                <ul className="m-0 list-none p-0">
                  {app.documents!.map((d) => (
                    <li key={d.fileRef}>{d.type.toLowerCase().replace('_', ' ')} at {time(d.uploadedAt)}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </section>

        <section className="card">
          <div className="card-header">Decision</div>
          <div className="card-body flex flex-col gap-5">
            {uw?.fourEyesRequired && (
              <p className="rounded-md bg-[#f5f3ff] px-3 py-2 text-[13px] text-[#6d28d9]">
                4 eyes: two different underwriters must approve.
              </p>
            )}
            {uw?.reviewReasons?.includes('PEP_REVIEW') && (
              <p className="rounded-md bg-[#fffbeb] px-3 py-2 text-[13px] text-[#b45309]">
                PEP review: the applicant matched the politically exposed persons list. Check source of funds.
              </p>
            )}
            {canDecide ? (
              <div className="flex flex-col gap-3">
                <p className="text-muted">Deciding as <span className="font-medium text-ink">{user}</span></p>
                <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Note (optional)" className="input" />
                <div className="flex gap-2">
                  <button onClick={() => decide.mutate('APPROVE')} disabled={decide.isPending} className="btn-primary flex-1">
                    Approve
                  </button>
                  <button onClick={() => decide.mutate('REJECT')} disabled={decide.isPending} className="btn-danger flex-1">
                    Reject
                  </button>
                </div>
                <div className="flex gap-2">
                  <select value={item} onChange={(e) => setItem(e.target.value as DocumentType)}
                          className="min-h-[44px] flex-1 rounded-md border border-[#d1d5db]">
                    <option value="PAYSLIP">Payslip (re-runs bureau)</option>
                    <option value="ID_DOCUMENT">ID document (re-runs KYC)</option>
                  </select>
                  <button onClick={() => decide.mutate('REQUEST_INFO')} disabled={decide.isPending} className="btn-secondary whitespace-nowrap">
                    Request info
                  </button>
                </div>
                {decide.isError && <p className="text-[#b91c1c]">{(decide.error as Error).message}</p>}
              </div>
            ) : (
              <p className="text-muted">
                {!isUnderwriter
                  ? 'Read only: decisions are made by underwriters.'
                  : needsSenior
                    ? 'Escalated: this case now waits for a senior underwriter.'
                    : app.status === 'UNDERWRITING_INFO_REQUESTED'
                      ? 'Waiting for the applicant to upload the requested document.'
                      : 'No decision needed at this stage.'}
              </p>
            )}
            <div>
              <h3 className="section-label mb-1">Approvals</h3>
              {(uw?.approvals?.length ?? 0) === 0 ? (
                <p className="text-muted">None yet.</p>
              ) : (
                <ul className="m-0 list-none p-0">
                  {uw!.approvals!.map((a) => (
                    <li key={a.at}>{a.by} at {time(a.at)}</li>
                  ))}
                </ul>
              )}
              {uw?.decision && (
                <p className="mt-2">
                  Final: <span className="font-medium">{uw.decision.decision.toLowerCase()}</span> by {uw.decision.by}
                </p>
              )}
            </div>
          </div>
        </section>

        <div className="flex flex-col gap-6">
          <LedgerCard applicationId={id} />
          <section className="card">
            <div className="card-header">Workflow timeline</div>
            <div className="card-body">
              <WorkflowTimeline rows={history.data ?? []} now={now} />
            </div>
          </section>
        </div>
      </div>
    </>
  )
}
