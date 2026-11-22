import { CheckResult } from '../api/client'

const BADGE: Record<string, string> = {
  PASSED: 'badge-good',
  HIT: 'badge-risk',
  FLAGGED: 'badge-due',
  FAILED: 'badge-risk',
  SKIPPED: 'badge-neutral',
}

const LABELS: Record<string, string> = { kyc: 'KYC', bureau: 'Bureau', sanctions: 'Sanctions', pep: 'PEP', valuation: 'Valuation' }

/** PEP only exists on workflow v2, so it is shown only when the application has that check. */
export function CheckChips({ checks }: { checks?: Record<string, CheckResult> }) {
  const names = ['kyc', 'bureau', 'sanctions', ...(checks?.pep ? ['pep'] : []), 'valuation']
  return (
    <ul className="m-0 flex list-none flex-col divide-y divide-[#f3f4f6] p-0">
      {names.map((name) => {
        const check = checks?.[name]
        return (
          <li key={name} className="flex items-center justify-between py-2">
            <span>{LABELS[name]}</span>
            <span className={check ? BADGE[check.status] : 'badge-info'}>
              {check ? check.status.toLowerCase() : 'pending'}
              {check?.score ? ` ${check.score}` : ''}
            </span>
          </li>
        )
      })}
    </ul>
  )
}
