import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ChaosMode, ChaosState } from '../api/client'

const MODES: ChaosMode[] = ['OK', 'SLOW', 'ERROR_500', 'TIMEOUT', 'DOWN']

/** Not linked from the nav. Open /chaos in a second window. */
export function ChaosPanelPage() {
  const queryClient = useQueryClient()
  const chaos = useQuery({ queryKey: ['chaos'], queryFn: api.chaos, refetchInterval: 2000 })
  const verification = useQuery({ queryKey: ['verification'], queryFn: api.verificationSetting })
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['chaos'] })
    queryClient.invalidateQueries({ queryKey: ['verification'] })
  }
  const set = useMutation({
    mutationFn: (s: ChaosState) => api.setChaos(s.service, { mode: s.mode, latencyMs: s.latencyMs, failNextN: s.failNextN }),
    onSuccess: refresh,
  })
  const reset = useMutation({ mutationFn: api.resetChaos, onSuccess: refresh })
  const toggle = useMutation({ mutationFn: api.setVerificationSetting, onSuccess: refresh })

  return (
    <>
      <div className="flex flex-wrap items-center gap-4">
        <h1 className="page-title">Chaos panel</h1>
        <label className="flex min-h-[44px] items-center gap-2 font-medium">
          <input
            type="checkbox"
            className="size-4 accent-primary"
            checked={verification.data?.parallel ?? true}
            onChange={(e) => toggle.mutate(e.target.checked)}
          />
          Parallel verification
        </label>
        <button onClick={() => reset.mutate()} className="btn-primary ml-auto">
          Reset all
        </button>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {(chaos.data ?? []).map((s) => {
          const broken = s.mode !== 'OK' || s.failNextN > 0
          return (
            <div key={s.service} className={`card flex flex-col ${broken ? 'ring-2 ring-[#b91c1c]' : ''}`}>
              <div className="card-header">
                {s.service}
                <span className={`ml-auto ${broken ? 'badge-risk' : 'badge-good'}`}>{broken ? 'broken' : 'healthy'}</span>
              </div>
              <div className="card-body flex flex-col gap-3">
                <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label={`${s.service} mode`}>
                  {MODES.map((mode) => (
                    <button
                      key={mode}
                      type="button"
                      role="radio"
                      aria-checked={s.mode === mode}
                      onClick={() => set.mutate({ ...s, mode })}
                      className={`min-h-[36px] rounded-md px-2.5 text-[12px] font-semibold ${
                        s.mode === mode
                          ? mode === 'OK'
                            ? 'bg-primary text-white'
                            : 'bg-[#b91c1c] text-white'
                          : 'bg-[#f3f4f6] text-[#4b5563] hover:text-ink'
                      }`}
                    >
                      {mode}
                    </button>
                  ))}
                </div>
                <label className="block">
                  <span className="label">Latency when SLOW: {(s.latencyMs / 1000).toFixed(1)}s</span>
                  <input type="range" min={0} max={20000} step={500} value={s.latencyMs} className="w-full accent-primary"
                         onChange={(e) => set.mutate({ ...s, latencyMs: Number(e.target.value) })} />
                </label>
                <label className="flex items-center gap-2">
                  Fail next
                  <input type="number" min={0} value={s.failNextN} className="input w-20"
                         onChange={(e) => set.mutate({ ...s, failNextN: Number(e.target.value) })} />
                  calls
                </label>
              </div>
            </div>
          )
        })}
      </div>
    </>
  )
}
