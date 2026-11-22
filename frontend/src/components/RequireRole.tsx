import { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Role } from '../api/client'
import { ROLES, useAuth } from '../auth'

/** Shows the screen only to the listed roles. Everyone else gets a pointer to who can see it. */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { me, logout } = useAuth()
  if (roles.includes(me.role)) return <>{children}</>
  return (
    <div className="card card-body flex flex-col gap-3">
      <p>
        This screen is for {roles.map((r) => ROLES[r].label.toLowerCase()).join(' or ')}. You are signed in as {me.name} (
        {ROLES[me.role].label.toLowerCase()}).
      </p>
      <div className="flex gap-3">
        <Link to={ROLES[me.role].home} className="btn-primary no-underline hover:text-white">
          Go to my screen
        </Link>
        <button type="button" onClick={logout} className="btn-secondary">
          Sign in as someone else
        </button>
      </div>
    </div>
  )
}
