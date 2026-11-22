import { useQuery } from '@tanstack/react-query'
import { api, LedgerEntry } from '../api/client'
import { money, time } from '../format'

const LABEL: Record<LedgerEntry['type'], { text: string; badge: string }> = {
  RESERVE: { text: 'Funds reserved', badge: 'badge-info' },
  OPEN_ACCOUNT: { text: 'Loan account opened', badge: 'badge-info' },
  PAYOUT: { text: 'Paid out', badge: 'badge-good' },
  DUPLICATE_PAYOUT_REQUEST: { text: 'Repeat request, not paid again', badge: 'badge-due' },
  RELEASE: { text: 'Reservation released', badge: 'badge-risk' },
  CLOSE_ACCOUNT: { text: 'Loan account closed', badge: 'badge-risk' },
}

/** What the core banking mock recorded. Shows that a retried payout paid once. */
export function LedgerCard({ applicationId }: { applicationId: string }) {
  const ledger = useQuery({
    queryKey: ['ledger', applicationId],
    queryFn: () => api.ledger(applicationId),
    refetchInterval: 2000,
  })
  const entries = ledger.data ?? []
  if (entries.length === 0) return null
  const payouts = entries.filter((e) => e.type === 'PAYOUT').length
  return (
    <section className="card">
      <div className="card-header">
        Core banking ledger
        <span className={`ml-auto ${payouts > 1 ? 'badge-risk' : 'badge-neutral'}`}>
          {payouts} {payouts === 1 ? 'payout' : 'payouts'}
        </span>
      </div>
      <ul className="m-0 list-none divide-y divide-[#f3f4f6] p-0">
        {entries.map((e) => (
          <li key={e.id} className="flex flex-col gap-1 px-6 py-2.5">
            <span className="flex items-center gap-3">
              <span className={LABEL[e.type].badge}>{LABEL[e.type].text}</span>
              <span className="ml-auto text-[12px] text-muted">{time(e.at)}</span>
            </span>
            <span className="text-[13px] text-muted">
              {e.ref}
              {e.amount ? `, ${money(e.amount)}` : ''}
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}
