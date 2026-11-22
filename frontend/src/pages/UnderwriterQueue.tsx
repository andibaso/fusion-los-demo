import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, Status } from '../api/client'
import { StatusChip } from '../components/StatusChip'
import { countdown, money, since, useNow } from '../format'
import { useAuth } from '../auth'

/** Every row is in underwriting, so the queue drops the prefix. */
const QUEUE_LABEL: Partial<Record<Status, string>> = {
  UNDERWRITING_PENDING: 'pending',
  UNDERWRITING_ESCALATED: 'escalated',
  UNDERWRITING_INFO_REQUESTED: 'info requested',
  UNDERWRITING_AWAITING_SECOND_APPROVAL: 'second approval',
}

export function UnderwriterQueuePage() {
  const { me } = useAuth()
  const isSenior = me.role === 'SENIOR_UNDERWRITER'
  const navigate = useNavigate()
  const now = useNow()
  const queryClient = useQueryClient()
  const queue = useQuery({
    queryKey: ['queue', me.id],
    queryFn: api.underwritingQueue,
    refetchInterval: 5000,
  })
  const approve = useMutation({
    mutationFn: (id: string) => api.decide(id, 'APPROVE'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['queue'] }),
  })

  const rows = queue.data ?? []
  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="page-title">Underwriting queue</h1>
        <span className="badge-info">{isSenior ? 'whole team' : `assigned to ${me.name}`}</span>
        <span className="ml-auto text-[13px] text-muted">
          {isSenior ? 'You see every case and decide escalated ones.' : 'Escalated cases move to a senior underwriter.'}
        </span>
      </div>
      {approve.isError && <p className="text-[#b91c1c]">{(approve.error as Error).message}</p>}
      <div className="card overflow-x-auto">
        <table className="w-full whitespace-nowrap [&_td]:px-4 [&_th]:px-4">
          <thead>
            <tr className="border-b border-line">
              <th className="th">ID</th>
              <th className="th">Applicant</th>
              <th className="th text-right">Amount</th>
              <th className="th text-right">Score</th>
              <th className="th">Status</th>
              <th className="th">Waiting</th>
              <th className="th">Reminder</th>
              <th className="th">Escalation</th>
              <th className="th">Review</th>
              <th className="th"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#f3f4f6]">
            {rows.map((r) => (
              <tr
                key={r.id}
                onClick={() => navigate(`/cases/${r.id}`)}
                className={`cursor-pointer hover:bg-[#f9fafb] ${r.escalated ? 'bg-[#fef2f2]' : ''}`}
              >
                <td className="td whitespace-nowrap">
                  <Link to={`/cases/${r.id}`} className="font-medium">{r.id}</Link>
                </td>
                <td className="td">{r.applicant}</td>
                <td className="td text-right">{money(r.amount)}</td>
                <td className="td text-right">{r.score ?? ''}</td>
                <td className="td"><StatusChip status={r.status} label={QUEUE_LABEL[r.status]} /></td>
                <td className="td">{since(r.waitingSince, now)}</td>
                <td className="td whitespace-nowrap">
                  {r.reminderAt && new Date(r.reminderAt).getTime() < now ? (
                    <span className="badge-due">sent {countdown(r.reminderAt, now)}</span>
                  ) : (
                    countdown(r.reminderAt, now)
                  )}
                </td>
                <td className="td">
                  {r.escalated ? <span className="badge-risk">to {r.assignedTo}</span> : countdown(r.escalateAt, now)}
                </td>
                <td className="td">
                  <span className="flex gap-1.5 whitespace-nowrap">
                    {r.fourEyes && <span className="badge-violet" title="Two different approvers needed">4 eyes {r.approvals}/2</span>}
                    {r.reviewReasons.includes('PEP_REVIEW') && <span className="badge-due">PEP</span>}
                  </span>
                </td>
                <td className="td text-right">
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      approve.mutate(r.id)
                    }}
                    disabled={
                      approve.isPending ||
                      r.status === 'UNDERWRITING_INFO_REQUESTED' ||
                      (r.escalated && !isSenior)
                    }
                    className="btn-primary btn-sm"
                  >
                    Approve
                  </button>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={10} className="td py-8 text-center text-muted">
                  No cases waiting{isSenior ? '' : ` for ${me.name}`}. Operations can seed sample data, or an applicant can
                  apply.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}
