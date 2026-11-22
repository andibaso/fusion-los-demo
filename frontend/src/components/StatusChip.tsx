import { Status } from '../api/client'

// gray pending or closed by a person, indigo running, amber waiting on a human, red failed, green done
const BADGE: Record<string, string> = {
  pending: 'badge-neutral',
  closed: 'badge-neutral',
  running: 'badge-info',
  waiting: 'badge-due',
  failed: 'badge-risk',
  done: 'badge-good',
}

export function statusTone(status?: Status): keyof typeof BADGE {
  if (!status || status === 'INTAKE_RECEIVED') return 'pending'
  if (status === 'COMPLETED') return 'done'
  if (status.endsWith('_RUNNING')) return 'running'
  if (status.startsWith('UNDERWRITING_') || status === 'OFFER_SENT') return 'waiting'
  if (status === 'CANCELLED' || status === 'OFFER_DECLINED' || status === 'OFFER_EXPIRED') return 'closed'
  return 'failed'
}

export function StatusChip({ status, label }: { status?: Status; label?: string }) {
  return (
    <span className={BADGE[statusTone(status)]}>
      {label ?? (status ? status.replaceAll('_', ' ').toLowerCase() : 'submitting')}
    </span>
  )
}
