import { FormEvent, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ExternalLink } from 'lucide-react'
import { api, ApiError, ApplicationDetail, DocumentType, temporalUiLink } from '../api/client'
import { StageStepper } from '../components/StageStepper'
import { StatusChip } from '../components/StatusChip'
import { Timeline } from '../components/Timeline'
import { countdown, money, useNow } from '../format'

const STATUS_TEXT: Record<string, string> = {
  INTAKE_RECEIVED: 'We have received your application.',
  VERIFICATION_RUNNING: 'We are checking your identity and credit history.',
  VERIFICATION_FAILED: 'We could not complete our checks.',
  UNDERWRITING_PENDING: 'An underwriter is reviewing your application.',
  UNDERWRITING_ESCALATED: 'A senior underwriter is reviewing your application.',
  UNDERWRITING_AWAITING_SECOND_APPROVAL: 'Your application needs one more approval.',
  UNDERWRITING_INFO_REQUESTED: 'We need a document from you. Please upload it below.',
  OFFER_SENT: 'Your offer is ready. Please accept or decline it below.',
  OFFER_EXPIRED: 'Your offer has expired.',
  DISBURSEMENT_RUNNING: 'Your loan is being set up and paid out.',
  COMPLETED: 'Your loan has been paid out.',
  REJECTED: 'We are unable to offer you a loan this time.',
  OFFER_DECLINED: 'You declined the offer.',
  DISBURSEMENT_COMPENSATED: 'We could not pay out your loan. The reserved funds were released and the account closed.',
  CANCELLED: 'This application was cancelled.',
}

export function ApplyPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({
    name: 'Ben Tan',
    email: 'ben.tan@example.com',
    monthlyIncome: 8500,
    amount: 45000,
    tenorMonths: 36,
  })
  const submit = useMutation({
    mutationFn: () =>
      api.createApplication({
        applicant: { name: form.name, email: form.email, monthlyIncome: form.monthlyIncome },
        product: 'PERSONAL_LOAN',
        amount: form.amount,
        tenorMonths: form.tenorMonths,
      }),
    onSuccess: (result) => navigate(`/my/${result.applicationId}`),
  })
  const recent = useQuery({ queryKey: ['applications'], queryFn: api.listApplications, refetchInterval: 5000 })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    submit.mutate()
  }
  const field = (key: keyof typeof form, label: string, type = 'text') => (
    <label className="block">
      <span className="label">{label}</span>
      <input
        type={type}
        value={form[key]}
        onChange={(e) => setForm({ ...form, [key]: type === 'number' ? Number(e.target.value) : e.target.value })}
        className="input"
      />
    </label>
  )

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <form onSubmit={onSubmit} className="card">
        <div className="card-header">Apply for a personal loan</div>
        <div className="card-body flex flex-col gap-4">
          {field('name', 'Full name')}
          {field('email', 'Email', 'email')}
          {field('monthlyIncome', 'Monthly income (SGD)', 'number')}
          {field('amount', 'Loan amount (SGD)', 'number')}
          {field('tenorMonths', 'Tenor (months)', 'number')}
          <p className="text-[13px] text-muted">
            Above 50,000 needs two approvals. Above 100,000 adds a valuation. A name with "Blocked" fails sanctions.
          </p>
          {submit.isError && <p className="text-[#b91c1c]">{(submit.error as Error).message}</p>}
          <button disabled={submit.isPending} className="btn-primary w-full">
            {submit.isPending ? 'Submitting' : 'Submit application'}
          </button>
        </div>
      </form>

      <section className="card self-start">
        <div className="card-header">Recent applications</div>
        <ul className="m-0 list-none divide-y divide-[#f3f4f6] p-0">
          {(recent.data ?? []).slice(0, 12).map((a) => (
            <li key={a.id} className="flex items-center gap-3 px-6 py-3">
              <Link to={`/my/${a.id}`} className="whitespace-nowrap font-medium">{a.id}</Link>
              <span className="truncate text-muted">{a.applicant.name}</span>
              <span className="ml-auto"><StatusChip status={a.status} /></span>
            </li>
          ))}
          {recent.data?.length === 0 && <li className="px-6 py-3 text-muted">No applications yet.</li>}
        </ul>
      </section>
    </div>
  )
}

const FINAL = ['VERIFICATION_FAILED', 'OFFER_EXPIRED', 'OFFER_DECLINED', 'DISBURSEMENT_COMPENSATED', 'COMPLETED', 'REJECTED', 'CANCELLED']

/** The workflow ignores a cancel once disbursement has started, so the button goes away too. */
function canWithdraw(app: ApplicationDetail) {
  return !!app.status && !FINAL.includes(app.status) && app.stage !== 'DISBURSEMENT'
}

export function MyApplicationPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const application = useQuery({
    queryKey: ['application', id],
    queryFn: () => api.getApplication(id),
    refetchInterval: 2000,
    retry: false,
  })
  const audit = useQuery({ queryKey: ['audit', id], queryFn: () => api.getAudit(id), refetchInterval: 2000 })
  const now = useNow()
  const upload = useMutation({
    mutationFn: (type: DocumentType) => api.uploadDocument(id, type),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['application', id] }),
  })
  const respond = useMutation({
    mutationFn: (response: 'ACCEPT' | 'DECLINE') => api.respondToOffer(id, response),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['application', id] }),
  })

  const withdraw = useMutation({
    mutationFn: () => api.cancel(id, 'Withdrawn by applicant'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['application', id] }),
  })

  const notFound = application.error instanceof ApiError && application.error.status === 404
  const app = application.data

  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="page-title">Application {id}</h1>
        {app && <StatusChip status={app.status} />}
        <a href={temporalUiLink(id)} target="_blank" rel="noreferrer" className="ml-auto inline-flex items-center gap-1 font-semibold">
          Open in Temporal UI <ExternalLink className="size-3.5" />
        </a>
      </div>

      {notFound && <p className="card card-body text-muted">Submitted. Waiting for the intake stage to record it.</p>}

      {app && (
        <>
          <div className="card card-body flex flex-col gap-4">
            <StageStepper stage={app.stage} status={app.status} />
            <p className="text-[16px] font-medium">{(app.status && STATUS_TEXT[app.status]) ?? ''}</p>
            <p className="text-[13px] text-muted">
              {app.live.available
                ? app.live.waitingOn
                  ? `Workflow is waiting on: ${app.live.waitingOn}`
                  : 'Workflow is working.'
                : 'Worker is not answering right now. Your application is safe and will continue.'}
            </p>
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-muted">
                {app.applicant.name}, {money(app.amount, app.currency)} over {app.tenorMonths} months
              </p>
              {canWithdraw(app) && (
                <button
                  onClick={() => window.confirm('Withdraw this application?') && withdraw.mutate()}
                  disabled={withdraw.isPending}
                  className="btn-secondary btn-sm ml-auto"
                >
                  Withdraw application
                </button>
              )}
            </div>
            {withdraw.isError && <p className="text-[#b91c1c]">{(withdraw.error as Error).message}</p>}
          </div>

          {app.status === 'UNDERWRITING_INFO_REQUESTED' && (
            <div className="card flex flex-col gap-3 bg-primary-soft px-6 py-5 ring-2 ring-primary-ring">
              <h2 className="font-semibold">Document needed</h2>
              {app.underwriting?.infoRequests
                ?.filter((r) => !r.fulfilledAt)
                .map((r) => (
                  <div key={r.requestedAt} className="flex items-center gap-3">
                    <span>{r.item === 'PAYSLIP' ? 'Latest payslip' : 'Identity document'}</span>
                    <button
                      onClick={() => upload.mutate(r.item)}
                      disabled={upload.isPending}
                      className="btn-primary btn-sm"
                    >
                      Upload
                    </button>
                  </div>
                ))}
              {upload.isError && <p className="text-[#b91c1c]">{(upload.error as Error).message}</p>}
            </div>
          )}

          {app.status === 'OFFER_SENT' && app.offer && (
            <div className="card flex flex-col gap-3 bg-primary-soft px-6 py-5 ring-2 ring-primary-ring">
              <h2 className="flex items-center gap-2 font-semibold">
                Your offer
                {app.offer.expiresAt && <span className="badge-due">expires {countdown(app.offer.expiresAt, now)}</span>}
              </h2>
              <p className="text-[16px]">
                {app.offer.rate}% a year, {money(app.offer.monthlyInstalment, app.currency)} a month
              </p>
              <div className="flex gap-3">
                <button
                  onClick={() => respond.mutate('ACCEPT')}
                  disabled={respond.isPending}
                  className="btn-primary"
                >
                  Accept offer
                </button>
                <button
                  onClick={() => respond.mutate('DECLINE')}
                  disabled={respond.isPending}
                  className="btn-secondary"
                >
                  Decline
                </button>
              </div>
              {respond.isError && <p className="text-[#b91c1c]">{(respond.error as Error).message}</p>}
            </div>
          )}
        </>
      )}

      <section className="card">
        <div className="card-header">What has happened</div>
        <div className="card-body">
          <Timeline events={audit.data ?? []} />
        </div>
      </section>
    </>
  )
}
