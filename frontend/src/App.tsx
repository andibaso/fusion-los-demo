import { Navigate, Route, Routes } from 'react-router-dom'
import { ACCESS, ROLES, useAuthState } from './auth'
import { RequireRole } from './components/RequireRole'
import { AppShell } from './components/shell/AppShell'
import { ApplyPage, MyApplicationPage } from './pages/Applicant'
import { CaseDetailPage } from './pages/CaseDetail'
import { ChaosPanelPage } from './pages/ChaosPanel'
import { LoginPage } from './pages/Login'
import { OpsPage } from './pages/Ops'
import { OverviewPage } from './pages/Overview'
import { UnderwriterQueuePage } from './pages/UnderwriterQueue'

export function App() {
  const { me, checking } = useAuthState()
  if (checking) return null
  // Signed out: the login page answers every URL, so a deep link opens after sign in.
  if (!me) return <LoginPage />

  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<OverviewPage />} />
        <Route path="/login" element={<Navigate to={ROLES[me.role].home} replace />} />
        <Route path="/apply" element={<RequireRole roles={ACCESS.apply}><ApplyPage /></RequireRole>} />
        <Route path="/my/:id" element={<RequireRole roles={ACCESS.apply}><MyApplicationPage /></RequireRole>} />
        <Route path="/underwriting" element={<RequireRole roles={ACCESS.underwriting}><UnderwriterQueuePage /></RequireRole>} />
        <Route path="/cases/:id" element={<RequireRole roles={ACCESS.caseDetail}><CaseDetailPage /></RequireRole>} />
        <Route path="/ops" element={<RequireRole roles={ACCESS.ops}><OpsPage /></RequireRole>} />
        <Route path="/chaos" element={<RequireRole roles={ACCESS.ops}><ChaosPanelPage /></RequireRole>} />
      </Routes>
    </AppShell>
  )
}
