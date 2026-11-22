// Typed client for the FusionLOS REST API. All URLs are relative: Vite proxies /api to Spring Boot.

export type Stage = 'INTAKE' | 'VERIFICATION' | 'UNDERWRITING' | 'OFFER' | 'DISBURSEMENT'

export type Status =
  | 'INTAKE_RECEIVED'
  | 'VERIFICATION_RUNNING' | 'VERIFICATION_FAILED'
  | 'UNDERWRITING_PENDING' | 'UNDERWRITING_INFO_REQUESTED' | 'UNDERWRITING_ESCALATED'
  | 'UNDERWRITING_AWAITING_SECOND_APPROVAL'
  | 'OFFER_SENT' | 'OFFER_EXPIRED' | 'OFFER_DECLINED'
  | 'DISBURSEMENT_RUNNING' | 'DISBURSEMENT_COMPENSATED'
  | 'COMPLETED' | 'REJECTED' | 'CANCELLED'

export interface CheckResult {
  status: 'PASSED' | 'FAILED' | 'HIT' | 'FLAGGED' | 'SKIPPED'
  ref?: string
  score?: number
  at?: string
}

export interface LoanApplication {
  id: string
  applicant: { customerId: string; name: string; email: string; monthlyIncome: number }
  product: string
  amount: number
  tenorMonths: number
  currency: string
  status?: Status
  stage?: Stage
  checks?: Record<string, CheckResult>
  underwriting?: {
    assignedTo: string
    assignedAt: string
    reminderAt?: string
    escalateAt?: string
    escalated: boolean
    fourEyesRequired: boolean
    decision?: DecisionRecord
    approvals?: DecisionRecord[]
    infoRequests?: { item: DocumentType; requestedBy: string; requestedAt: string; fulfilledAt?: string }[]
    reviewReasons?: string[]
  }
  documents?: { type: DocumentType; fileRef: string; uploadedAt: string }[]
  offer?: { rate: number; monthlyInstalment: number; expiresAt?: string; response?: string }
  disbursement?: { reservationRef?: string; accountNumber?: string; disbursementRef?: string }
  workflow?: { workflowId: string; version: string }
  createdAt: string
  updatedAt: string
}

export type DocumentType = 'PAYSLIP' | 'ID_DOCUMENT'
export type Decision = 'APPROVE' | 'REJECT' | 'REQUEST_INFO'

export interface DecisionRecord {
  decision: Decision
  by: string
  note?: string
  at: string
}

export interface LiveState {
  available: boolean
  stage?: Stage
  waitingOn?: string
}

export type ApplicationDetail = LoanApplication & { live: LiveState }

export interface AuditEvent {
  id: string
  applicationId: string
  at: string
  stage?: Stage
  type: string
  actor: string
  detail?: Record<string, unknown>
}

export interface QueueRow {
  id: string
  applicant: string
  amount: number
  score?: number
  status: Status
  assignedTo: string
  waitingSince: string
  reminderAt?: string
  escalateAt?: string
  escalated: boolean
  fourEyes: boolean
  approvals: number
  reviewReasons: string[]
}

export interface HistoryRow {
  stage: string
  kind: 'ACTIVITY' | 'TIMER' | 'SIGNAL'
  name: string
  at: string
  closedAt?: string
  outcome: string
  attempt?: number
  lastFailure?: string
  nextAt?: string
}

export type ChaosMode = 'OK' | 'SLOW' | 'ERROR_500' | 'TIMEOUT' | 'DOWN'

export interface ChaosState {
  service: string
  mode: ChaosMode
  latencyMs: number
  failNextN: number
}

export interface NewApplication {
  applicant: { name: string; email: string; monthlyIncome: number }
  product: string
  amount: number
  tenorMonths: number
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
  }
}

let sessionExpired = () => {}
/** Called when any API call comes back 401, so the app can show the login page again. */
export function onSessionExpired(handler: () => void) {
  sessionExpired = handler
}

// The session lives in an HttpOnly cookie set by /api/auth/login. Same origin, so fetch sends it.
async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (response.status === 401 && !path.startsWith('/auth/')) sessionExpired()
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new ApiError(response.status, error.message ?? response.statusText)
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export interface LedgerEntry {
  id: string
  applicationId: string
  type: 'RESERVE' | 'OPEN_ACCOUNT' | 'PAYOUT' | 'DUPLICATE_PAYOUT_REQUEST' | 'RELEASE' | 'CLOSE_ACCOUNT'
  ref: string
  amount?: number
  at: string
}

export interface WorkerStatus {
  up: boolean
  lastSeen?: string
  build?: string
}

export interface OpsSummary {
  worker: WorkerStatus
  openByStage: Record<Stage, number>
  byStatus: Partial<Record<Status, number>>
  slaBreaches: { id: string; applicant: string; status: Status; assignedTo: string; dueAt: string; escalated: boolean }[]
  stuck: { id: string; applicant: string; status: Status; since: string }[]
  stuckAfter: string
  openByVersion: Record<string, number>
}

/** Durations arrive as ISO 8601, for example PT20S or PT48H. */
export type Durations = Record<
  'reminderAfter' | 'escalateAfter' | 'offerExpiresAfter' | 'retryMaxInterval' | 'retryScheduleToClose' | 'stuckAfter',
  string
>

export type Role = 'APPLICANT' | 'UNDERWRITER' | 'SENIOR_UNDERWRITER' | 'OPERATIONS'

export interface DemoUser {
  id: string
  name: string
  role: Role
}

export const api = {
  loginOptions: () => request<{ passwordRequired: boolean; users: DemoUser[] }>('GET', '/auth/options'),
  login: (userId: string, password?: string) => request<DemoUser>('POST', '/auth/login', { userId, password }),
  logout: () => request<void>('POST', '/auth/logout'),
  me: () => request<DemoUser>('GET', '/auth/me'),
  createApplication: (body: NewApplication) =>
    request<{ applicationId: string; created: boolean }>('POST', '/applications', body),
  listApplications: () => request<LoanApplication[]>('GET', '/applications'),
  getApplication: (id: string) => request<ApplicationDetail>('GET', `/applications/${id}`),
  getAudit: (id: string) => request<AuditEvent[]>('GET', `/applications/${id}/audit`),
  getHistory: (id: string) => request<HistoryRow[]>('GET', `/applications/${id}/history`),
  decide: (id: string, decision: Decision, note?: string, item?: DocumentType) =>
    request<void>('POST', `/applications/${id}/decision`, { decision, note, item }),
  uploadDocument: (id: string, type: DocumentType) =>
    request<void>('POST', `/applications/${id}/documents`, { type, fileRef: `${type.toLowerCase()}-${Date.now()}.pdf` }),
  chaos: () => request<ChaosState[]>('GET', '/chaos'),
  setChaos: (service: string, state: Omit<ChaosState, 'service'>) =>
    request<ChaosState>('POST', `/chaos/${service}`, state),
  resetChaos: () => request<ChaosState[]>('POST', '/chaos/reset'),
  verificationSetting: () => request<{ parallel: boolean }>('GET', '/config/verification'),
  setVerificationSetting: (parallel: boolean) =>
    request<{ parallel: boolean }>('POST', '/config/verification', { parallel }),
  cancel: (id: string, reason?: string) => request<void>('POST', `/applications/${id}/cancel`, { reason }),
  respondToOffer: (id: string, response: 'ACCEPT' | 'DECLINE') =>
    request<void>('POST', `/applications/${id}/offer`, { response }),
  ledger: (id: string) => request<LedgerEntry[]>('GET', `/applications/${id}/ledger`),
  opsSummary: () => request<OpsSummary>('GET', '/ops/summary'),
  worker: () => request<WorkerStatus>('GET', '/ops/worker'),
  durations: () => request<{ active: string; profiles: Record<string, Durations> }>('GET', '/config/durations'),
  seed: (count = 30) => request<{ started: number }>('POST', `/admin/seed?count=${count}`),
  reset: () => request<{ terminated: number }>('POST', '/admin/reset'),
  underwritingQueue: () => request<QueueRow[]>('GET', '/underwriting/queue'),
}

export const temporalUiLink = (workflowId?: string) =>
  workflowId ? `/temporal/namespaces/default/workflows/${workflowId}` : '/temporal'
