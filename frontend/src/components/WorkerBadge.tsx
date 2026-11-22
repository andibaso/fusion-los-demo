import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

/** Red the moment the worker stops writing heartbeats. Applications are safe either way. */
export function WorkerBadge() {
  const worker = useQuery({ queryKey: ['worker'], queryFn: api.worker, refetchInterval: 5000 })
  if (!worker.data) return null
  return worker.data.up ? (
    <span className="badge-good" title="Heartbeat in the last 15 seconds">
      <span className="size-1.5 rounded-full bg-[#15803d]" aria-hidden /> Worker {worker.data.build ?? ''} up
    </span>
  ) : (
    <span className="badge-risk" title="No heartbeat for 15 seconds. Workflows wait on the Temporal server.">
      <span className="size-1.5 rounded-full bg-[#b91c1c]" aria-hidden /> Worker down
    </span>
  )
}
