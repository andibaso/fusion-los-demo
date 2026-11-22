import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Clock, RefreshCw, ShieldCheck, Undo2 } from 'lucide-react'
import { FormEvent, useState } from 'react'
import { api, DemoUser } from '../api/client'
import { LogoMark } from '../components/shell/Logo'
import { useLocation, useNavigate } from 'react-router-dom'
import { canOpen, ROLES, useAuthState } from '../auth'

const POINTS = [
  { icon: ShieldCheck, text: 'No application is lost when a server stops' },
  { icon: RefreshCw, text: 'Vendor outages are retried until they recover' },
  { icon: Clock, text: 'Reminders and escalations run on durable timers' },
  { icon: Undo2, text: 'A failed payout undoes its earlier steps' },
]

/** Sign in: one card per role. A shared password is asked for only if LOS_LOGIN_PASSWORD is set. */
export function LoginPage() {
  const { login } = useAuthState()
  const location = useLocation()
  const navigate = useNavigate()
  const options = useQuery({ queryKey: ['login-options'], queryFn: api.loginOptions })
  const [selected, setSelected] = useState<string | null>(null)
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const passwordRequired = options.data?.passwordRequired ?? false

  const signIn = async (user: DemoUser) => {
    setSelected(user.id)
    if (passwordRequired && !password) {
      setError('Enter the password first.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await login(user.id, password || undefined)
      // Keep a deep link this user may open; otherwise start on the user's own screen.
      if (!canOpen(user.role, location.pathname)) navigate(ROLES[user.role].home, { replace: true })
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    const user = options.data?.users.find((u) => u.id === selected)
    if (user) signIn(user)
  }

  return (
    <div className="grid min-h-screen bg-page lg:grid-cols-[minmax(380px,5fr)_7fr]">
      <aside className="flex flex-col justify-between gap-10 bg-sidebar px-10 py-10 text-white">
        <div className="flex items-center gap-3">
          <LogoMark className="size-10" />
          <div className="leading-tight">
            <div className="text-[18px] font-semibold">
              Fusion<span className="text-sidebar-tag">LOS</span>
            </div>
            <div className="text-[11px] font-semibold uppercase tracking-wider text-sidebar-tag">Loan origination</div>
          </div>
        </div>
        <div className="flex flex-col gap-6">
          <h1 className="text-[28px] font-semibold leading-tight">
            Every loan application, from submission to payout, as one durable workflow.
          </h1>
          <ul className="m-0 flex list-none flex-col gap-3 p-0">
            {POINTS.map((p) => (
              <li key={p.text} className="flex items-center gap-3 text-[#d1d5db]">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-sidebar-active text-sidebar-tag">
                  <p.icon className="size-4" aria-hidden />
                </span>
                {p.text}
              </li>
            ))}
          </ul>
        </div>
        <p className="text-[13px] text-sidebar-text">Loan origination on Temporal and MongoDB. All data is generated.</p>
      </aside>

      <main className="flex items-center justify-center px-6 py-10">
        <form onSubmit={onSubmit} className="flex w-full max-w-[520px] flex-col gap-5">
          <div>
            <h2 className="text-[22px] font-semibold">Sign in</h2>
            <p className="text-muted">Choose who you are. Sign out to switch.</p>
          </div>

          {passwordRequired && (
            <label className="flex flex-col gap-1.5">
              <span className="label">Password</span>
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="input"
                autoFocus
              />
            </label>
          )}

          <ul className="m-0 flex list-none flex-col gap-3 p-0">
            {options.data?.users.map((user) => (
              <li key={user.id}>
                <button
                  type="button"
                  onClick={() => signIn(user)}
                  disabled={busy}
                  className={`card flex w-full items-center gap-4 px-5 py-4 text-left transition hover:ring-2 hover:ring-primary-ring ${
                    selected === user.id ? 'ring-2 ring-primary' : ''
                  }`}
                >
                  <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-[#6366f1] to-[#7c3aed] text-[15px] font-semibold text-white">
                    {user.name.slice(0, 1)}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-2">
                      <span className="font-semibold">{user.name}</span>
                      <span className="badge-info">{ROLES[user.role].label}</span>
                    </span>
                    <span className="block text-[13px] text-muted">{ROLES[user.role].does}</span>
                  </span>
                  <ArrowRight className="size-4 shrink-0 text-muted" aria-hidden />
                </button>
              </li>
            ))}
          </ul>

          {options.isError && <p className="text-[#b91c1c]">Cannot reach the API. Is it running?</p>}
          {error && <p className="text-[#b91c1c]">{error}</p>}
        </form>
      </main>
    </div>
  )
}
