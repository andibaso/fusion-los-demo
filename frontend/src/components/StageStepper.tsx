import { Check } from 'lucide-react'
import { Stage, Status } from '../api/client'

const STAGES: { id: Stage; label: string }[] = [
  { id: 'INTAKE', label: 'Intake' },
  { id: 'VERIFICATION', label: 'Verification' },
  { id: 'UNDERWRITING', label: 'Underwriting' },
  { id: 'OFFER', label: 'Offer' },
  { id: 'DISBURSEMENT', label: 'Disbursement' },
]

export function StageStepper({ stage, status }: { stage?: Stage; status?: Status }) {
  const current = STAGES.findIndex((s) => s.id === stage)
  const completed = status === 'COMPLETED'
  return (
    <ol className="m-0 flex list-none flex-wrap items-center gap-2 p-0">
      {STAGES.map((s, i) => {
        const done = completed || i < current
        const active = !completed && i === current
        const tone = done
          ? 'bg-primary text-white'
          : active
            ? 'bg-white text-primary ring-2 ring-primary-ring'
            : 'bg-[#f3f4f6] text-muted'
        return (
          <li key={s.id} className="flex items-center gap-2">
            <span className={`flex size-7 items-center justify-center rounded-full text-[12px] font-semibold ${tone}`}>
              {done ? <Check className="size-4" aria-hidden /> : i + 1}
            </span>
            <span className={active ? 'font-semibold text-primary' : done ? 'font-medium' : 'text-muted'}>{s.label}</span>
            {i < STAGES.length - 1 && <span className={`mx-1 h-0.5 w-8 rounded ${done ? 'bg-primary' : 'bg-line'}`} />}
          </li>
        )
      })}
    </ol>
  )
}
