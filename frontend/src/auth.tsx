import { useQueryClient } from '@tanstack/react-query'
import { createContext, ReactNode, useContext, useEffect, useState } from 'react'
import { api, ApiError, DemoUser, onSessionExpired, Role } from './api/client'

/** What each role is for. Used on the login cards, in the sidebar and by the route guards. */
export const ROLES: Record<Role, { label: string; does: string; home: string }> = {
  APPLICANT: { label: 'Applicant', does: 'Applies for a loan, uploads documents, accepts the offer.', home: '/apply' },
  UNDERWRITER: { label: 'Underwriter', does: 'Works own cases: approve, reject or ask for a document.', home: '/underwriting' },
  SENIOR_UNDERWRITER: {
    label: 'Senior underwriter',
    does: 'Sees the whole team queue, takes escalated cases, gives second approvals.',
    home: '/underwriting',
  },
  OPERATIONS: { label: 'Operations', does: 'Watches SLAs and the worker, breaks services, seeds and resets sample data.', home: '/ops' },
}

/** Who may open each screen. The API enforces the same rules; this only keeps the UI honest. */
export const ACCESS = {
  apply: ['APPLICANT'],
  underwriting: ['UNDERWRITER', 'SENIOR_UNDERWRITER'],
  caseDetail: ['UNDERWRITER', 'SENIOR_UNDERWRITER', 'OPERATIONS'],
  ops: ['OPERATIONS'],
} satisfies Record<string, Role[]>

type Auth = {
  me: DemoUser | null
  checking: boolean
  login: (userId: string, password?: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<Auth | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [me, setMe] = useState<DemoUser | null>(null)
  const [checking, setChecking] = useState(true)

  useEffect(() => {
    onSessionExpired(() => setMe(null))
    api
      .me()
      .then(setMe)
      .catch((e) => {
        if (!(e instanceof ApiError && e.status === 401)) console.error(e)
      })
      .finally(() => setChecking(false))
  }, [])

  // Cached queries belong to the previous user, so they go on every sign in and sign out.
  const login = async (userId: string, password?: string) => {
    const user = await api.login(userId, password)
    queryClient.clear()
    setMe(user)
  }
  const logout = async () => {
    await api.logout().catch(() => undefined)
    queryClient.clear()
    setMe(null)
  }

  return <AuthContext.Provider value={{ me, checking, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): Auth & { me: DemoUser } {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth outside AuthProvider')
  return auth as Auth & { me: DemoUser } // screens behind the login gate always have a user
}

export function useAuthState(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuthState outside AuthProvider')
  return auth
}

const SCREEN_FOR_PATH: [string, keyof typeof ACCESS][] = [
  ['/apply', 'apply'],
  ['/my/', 'apply'],
  ['/underwriting', 'underwriting'],
  ['/cases/', 'caseDetail'],
  ['/ops', 'ops'],
  ['/chaos', 'ops'],
]

/** Whether a role may open a URL. Paths not listed (the overview) are open to everyone. */
export function canOpen(role: Role, pathname: string) {
  const match = SCREEN_FOR_PATH.find(([prefix]) => pathname.startsWith(prefix))
  return !match || (ACCESS[match[1]] as Role[]).includes(role)
}
