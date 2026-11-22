import { AuditEvent } from '../api/client'
import { time } from '../format'

const TEMPLATES: Record<string, string> = {
  CASE_ASSIGNED: 'new case assigned',
  OFFER_SENT: 'your loan offer',
  LOAN_DISBURSED: 'loan disbursed',
  APPLICATION_REJECTED: 'application rejected',
  REMINDER: 'reminder, case still waiting',
  CASE_ESCALATED: 'case escalated',
  SECOND_APPROVAL_NEEDED: 'second approval needed',
  INFO_REQUESTED: 'we need a document from you',
  OFFER_EXPIRED: 'your offer has expired',
  DISBURSEMENT_FAILED: 'we could not pay out your loan',
  APPLICATION_CANCELLED: 'application cancelled',
}

/** Turns audit events into sentences an applicant can read. */
function describe(e: AuditEvent): string {
  const d = (e.detail ?? {}) as Record<string, string | number | boolean>
  switch (e.type) {
    case 'APPLICATION_SUBMITTED':
      return 'Application submitted'
    case 'STATUS_CHANGED':
      return `Status is now ${String(d.status).replaceAll('_', ' ').toLowerCase()}`
    case 'CHECKS_COMPLETED':
      return `Checks finished: ${Object.entries(d)
        .map(([k, v]) => `${k} ${String(v).toLowerCase()}`)
        .join(', ')}`
    case 'ASSIGNED':
      return `Assigned to ${d.assignedTo}${d.fourEyesRequired ? ' (two approvals needed)' : ''}`
    case 'DECISION':
      return `${e.actor} decided to ${String(d.decision).toLowerCase()}`
    case 'OFFER_PREPARED':
      return `Offer prepared: ${d.rate}% a year, ${d.monthlyInstalment} a month`
    case 'OFFER_RESPONSE':
      return `Offer ${d.response === 'ACCEPT' ? 'accepted' : 'declined'} by ${e.actor}`
    case 'NOTIFICATION_SENT':
      return `Notification sent to ${d.to}: ${TEMPLATES[String(d.template)] ?? d.template}`
    case 'ESCALATED':
      return `Escalated to ${d.assignedTo}`
    case 'REMINDER':
      return `Reminder: case still waiting for ${d.to}`
    case 'APPROVAL':
      return `First approval by ${e.actor}`
    case 'SAME_USER_APPROVAL_IGNORED':
      return `Second approval by the same person (${e.actor}) ignored`
    case 'INFO_REQUESTED':
      return `${e.actor} asked for a document: ${String(d.item).replace('_', ' ').toLowerCase()}`
    case 'INFO_RECEIVED':
      return `Document received: ${String(d.item).replace('_', ' ').toLowerCase()}`
    case 'CHECK_RERUN':
      return `Check run again: ${d.check} ${String(d.status).toLowerCase()}`
    case 'FUNDS_RESERVED':
      return `Funds reserved (${d.reservationRef})`
    case 'LOAN_ACCOUNT_CREATED':
      return `Loan account ${d.accountNumber} opened`
    case 'FUNDS_DISBURSED':
      return `Money paid out (${d.disbursementRef})`
    case 'LOAN_ACCOUNT_CLOSED':
      return `Undone: loan account ${d.accountNumber} closed`
    case 'WORKFLOW_VERSION':
      return `Running on workflow ${d.version}: PEP check added`
    case 'CANCELLED':
      return `Cancelled by the applicant: ${d.reason}`
    case 'RESERVATION_RELEASED':
      return `Undone: reserved funds released (${d.reservationRef})`
    default:
      return e.type
  }
}

export function Timeline({ events }: { events: AuditEvent[] }) {
  if (events.length === 0) return <p className="text-muted">Nothing yet.</p>
  return (
    <ol className="m-0 flex list-none flex-col p-0">
      {events.map((e, i) => (
        <li key={e.id} className="relative flex gap-3 pb-3 last:pb-0">
          {i < events.length - 1 && <span className="absolute left-[5px] top-4 h-full w-px bg-line" aria-hidden />}
          <span className="relative mt-1.5 size-[11px] shrink-0 rounded-full border-2 border-primary-ring bg-white" aria-hidden />
          <span className="w-20 shrink-0 text-[13px] text-muted">{time(e.at)}</span>
          <span>{describe(e)}</span>
        </li>
      ))}
    </ol>
  )
}
