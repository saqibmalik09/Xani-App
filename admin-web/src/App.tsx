import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, DASHBOARD_PERM, canEnterConsole, useAuth } from './lib/auth'
import { Shell } from './layout/Shell'
import { PageLoader } from './ui/Loader'

const LoginPage = lazy(() => import('./pages/Login').then((m) => ({ default: m.LoginPage })))
const ForbiddenPage = lazy(() => import('./pages/Forbidden').then((m) => ({ default: m.ForbiddenPage })))
const DashboardPage = lazy(() => import('./pages/Dashboard').then((m) => ({ default: m.DashboardPage })))
const ProfilePage = lazy(() => import('./pages/Profile').then((m) => ({ default: m.ProfilePage })))
const RolesPage = lazy(() => import('./pages/Roles').then((m) => ({ default: m.RolesPage })))
const ApprovalsPage = lazy(() => import('./pages/Approvals').then((m) => ({ default: m.ApprovalsPage })))
const MembersPage = lazy(() => import('./pages/Members').then((m) => ({ default: m.MembersPage })))
const MemberDetailPage = lazy(() => import('./pages/Members').then((m) => ({ default: m.MemberDetailPage })))
const AuditPage = lazy(() => import('./pages/Audit').then((m) => ({ default: m.AuditPage })))
const PermissionsPage = lazy(() => import('./pages/Permissions').then((m) => ({ default: m.PermissionsPage })))
const SettingsPage = lazy(() => import('./pages/Settings').then((m) => ({ default: m.SettingsPage })))
const NotificationsPage = lazy(() =>
  import('./pages/Notifications').then((m) => ({ default: m.NotificationsPage })),
)
const RewardsSignupPage = lazy(() =>
  import('./pages/RewardsSignup').then((m) => ({ default: m.RewardsSignupPage })),
)
const CountryRatesPage = lazy(() =>
  import('./pages/CountryRates').then((m) => ({ default: m.CountryRatesPage })),
)
const NetworkPage = lazy(() => import('./pages/Network').then((m) => ({ default: m.NetworkPage })))
const UsersPage = lazy(() => import('./pages/Users').then((m) => ({ default: m.UsersPage })))
const UserDetailPage = lazy(() => import('./pages/UserDetail').then((m) => ({ default: m.UserDetailPage })))
const AccountsPage = lazy(() => import('./pages/Accounts').then((m) => ({ default: m.AccountsPage })))
const RestrictionsPage = lazy(() => import('./pages/Restrictions').then((m) => ({ default: m.RestrictionsPage })))
const AccountVideosPage = lazy(() => import('./pages/AccountBrowse').then((m) => ({ default: m.AccountVideosPage })))
const AccountPostsPageAll = lazy(() =>
  import('./pages/AccountBrowse').then((m) => ({ default: m.AccountPostsPageAll })),
)
const AccountFollowersPage = lazy(() =>
  import('./pages/AccountBrowse').then((m) => ({ default: m.AccountFollowersPage })),
)
const AccountFollowingPage = lazy(() =>
  import('./pages/AccountBrowse').then((m) => ({ default: m.AccountFollowingPage })),
)
const AccountBalancePage = lazy(() =>
  import('./pages/AccountTools').then((m) => ({ default: m.AccountBalancePage })),
)
const AccountPasswordPage = lazy(() =>
  import('./pages/AccountTools').then((m) => ({ default: m.AccountPasswordPage })),
)
const AccountStorePage = lazy(() => import('./pages/AccountTools').then((m) => ({ default: m.AccountStorePage })))
const AccountModerationPage = lazy(() =>
  import('./pages/AccountTools').then((m) => ({ default: m.AccountModerationPage })),
)
const AccountDevicesPage = lazy(() =>
  import('./pages/AccountTools').then((m) => ({ default: m.AccountDevicesPage })),
)
const GiftsPage = lazy(() => import('./pages/Gifts').then((m) => ({ default: m.GiftsPage })))
const StickersPage = lazy(() => import('./pages/Stickers').then((m) => ({ default: m.StickersPage })))
const BannersPage = lazy(() => import('./pages/Banners').then((m) => ({ default: m.BannersPage })))
const HelpCenterPage = lazy(() => import('./pages/HelpCenter').then((m) => ({ default: m.HelpCenterPage })))
const TermsPoliciesPage = lazy(() => import('./pages/TermsPolicies').then((m) => ({ default: m.TermsPoliciesPage })))
const StorePage = lazy(() => import('./pages/Store').then((m) => ({ default: m.StorePage })))
const FramePurchasesPage = lazy(() =>
  import('./pages/FramePurchases').then((m) => ({ default: m.FramePurchasesPage })),
)
const LevelsPage = lazy(() => import('./pages/Levels').then((m) => ({ default: m.LevelsPage })))
const LevelUsersPage = lazy(() => import('./pages/LevelUsers').then((m) => ({ default: m.LevelUsersPage })))
const LevelUserDetailPage = lazy(() =>
  import('./pages/LevelUserDetail').then((m) => ({ default: m.LevelUserDetailPage })),
)
const LivePage = lazy(() => import('./pages/Live').then((m) => ({ default: m.LivePage })))
const WithdrawalsPage = lazy(() => import('./pages/Withdrawals').then((m) => ({ default: m.WithdrawalsPage })))
const PaymentMethodsPage = lazy(() =>
  import('./pages/PaymentMethods').then((m) => ({ default: m.PaymentMethodsPage })),
)

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { ready, session, can } = useAuth()
  if (!ready) return <PageLoader label="Loading session…" />
  if (!session) return <Navigate to="/login" replace />
  if (!canEnterConsole(session, can)) return <Navigate to="/forbidden" replace />
  return children
}

/** Guard: one permission, or any of a list. The server enforces the same rule; this only shapes navigation. */
function RequirePerm({ perm, anyOf, children }: { perm?: string; anyOf?: string[]; children: React.ReactNode }) {
  const { can } = useAuth()
  const ok = perm ? can(perm) : (anyOf || []).some((p) => can(p))
  if (!ok) return <Navigate to="/" replace />
  return children
}

/** Pages that any membership holder can open (their own network / requests). */
function RequireMembership({ children }: { children: React.ReactNode }) {
  const { session, can } = useAuth()
  if (!session) return <Navigate to="/login" replace />
  if (session.memberships.length === 0 && !can(DASHBOARD_PERM)) return <Navigate to="/" replace />
  return children
}

function AppRoutes() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forbidden" element={<ForbiddenPage />} />
        <Route
          path="/"
          element={
            <RequireAuth>
              <Shell />
            </RequireAuth>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route
            path="users"
            element={
              <RequirePerm perm="admin.manage_users">
                <UsersPage />
              </RequirePerm>
            }
          />
          <Route
            path="users/:id"
            element={
              <RequirePerm perm="admin.manage_users">
                <UserDetailPage />
              </RequirePerm>
            }
          />
          <Route
            path="accounts"
            element={
              <RequirePerm perm="admin.manage_users">
                <AccountsPage />
              </RequirePerm>
            }
          />
          <Route
            path="restrictions"
            element={
              <RequirePerm perm="admin.manage_users">
                <RestrictionsPage />
              </RequirePerm>
            }
          />
          <Route
            path="accounts/:id/videos"
            element={
              <RequirePerm perm="admin.manage_users">
                <AccountVideosPage />
              </RequirePerm>
            }
          />
          <Route
            path="accounts/:id/posts"
            element={
              <RequirePerm perm="admin.manage_users">
                <AccountPostsPageAll />
              </RequirePerm>
            }
          />
          <Route
            path="accounts/:id/followers"
            element={
              <RequirePerm perm="admin.manage_users">
                <AccountFollowersPage />
              </RequirePerm>
            }
          />
          <Route
            path="accounts/:id/following"
            element={
              <RequirePerm perm="admin.manage_users">
                <AccountFollowingPage />
              </RequirePerm>
            }
          />
          <Route
            path="accounts/:id/balance"
            element={
              <RequirePerm perm="admin.manage_users">
                <AccountBalancePage />
              </RequirePerm>
            }
          />
          <Route
            path="accounts/:id/password"
            element={
              <RequirePerm perm="admin.manage_users">
                <AccountPasswordPage />
              </RequirePerm>
            }
          />
          <Route
            path="accounts/:id/store"
            element={
              <RequirePerm perm="admin.manage_users">
                <AccountStorePage />
              </RequirePerm>
            }
          />
          <Route
            path="accounts/:id/moderation"
            element={
              <RequirePerm perm="admin.manage_users">
                <AccountModerationPage />
              </RequirePerm>
            }
          />
          <Route
            path="accounts/:id/devices"
            element={
              <RequirePerm perm="admin.manage_users">
                <AccountDevicesPage />
              </RequirePerm>
            }
          />
          <Route
            path="gifts"
            element={
              <RequirePerm perm="finance.manage_gifts">
                <GiftsPage />
              </RequirePerm>
            }
          />
          <Route
            path="stickers"
            element={
              <RequirePerm perm="finance.manage_stickers">
                <StickersPage />
              </RequirePerm>
            }
          />
          <Route
            path="banners"
            element={
              <RequirePerm perm="finance.manage_banners">
                <BannersPage />
              </RequirePerm>
            }
          />
          <Route
            path="help"
            element={
              <RequirePerm perm="admin.manage_help">
                <HelpCenterPage />
              </RequirePerm>
            }
          />
          <Route
            path="legal"
            element={
              <RequirePerm perm="admin.manage_help">
                <TermsPoliciesPage />
              </RequirePerm>
            }
          />
          <Route
            path="store"
            element={
              <RequirePerm perm="finance.manage_store">
                <StorePage />
              </RequirePerm>
            }
          />
          <Route
            path="frame-purchases"
            element={
              <RequirePerm perm="finance.manage_store">
                <FramePurchasesPage />
              </RequirePerm>
            }
          />
          <Route
            path="live"
            element={
              <RequirePerm perm="admin.manage_live">
                <LivePage />
              </RequirePerm>
            }
          />
          <Route
            path="withdrawals"
            element={
              <RequirePerm perm="finance.manage_withdrawals">
                <WithdrawalsPage />
              </RequirePerm>
            }
          />
          <Route
            path="payment-methods"
            element={
              <RequirePerm perm="finance.manage_withdrawals">
                <PaymentMethodsPage />
              </RequirePerm>
            }
          />
          <Route
            path="levels"
            element={
              <RequirePerm perm="finance.manage_levels">
                <LevelsPage />
              </RequirePerm>
            }
          />
          <Route
            path="level-users"
            element={
              <RequirePerm perm="finance.manage_levels">
                <LevelUsersPage />
              </RequirePerm>
            }
          />
          <Route
            path="level-users/:id"
            element={
              <RequirePerm perm="finance.manage_levels">
                <LevelUserDetailPage />
              </RequirePerm>
            }
          />
          <Route
            path="roles/:ref?"
            element={
              <RequirePerm perm="roles.view">
                <RolesPage />
              </RequirePerm>
            }
          />
          <Route path="role-applications" element={<Navigate to="/approvals" replace />} />
          <Route
            path="approvals/:id?"
            element={
              <RequirePerm anyOf={['requests.approve', 'requests.view', 'memberships.create']}>
                <ApprovalsPage />
              </RequirePerm>
            }
          />
          <Route
            path="members"
            element={
              <RequirePerm perm="memberships.view">
                <MembersPage />
              </RequirePerm>
            }
          />
          <Route
            path="members/:id"
            element={
              <RequirePerm perm="memberships.view">
                <MemberDetailPage />
              </RequirePerm>
            }
          />
          <Route
            path="audit"
            element={
              <RequirePerm perm="audit.view">
                <AuditPage />
              </RequirePerm>
            }
          />
          <Route
            path="permissions"
            element={
              <RequirePerm perm="admin.manage_permissions">
                <PermissionsPage />
              </RequirePerm>
            }
          />
          <Route
            path="settings/*"
            element={
              <RequirePerm perm="admin.manage_settings">
                <SettingsPage />
              </RequirePerm>
            }
          />
          <Route
            path="notifications/:id?"
            element={
              <RequirePerm perm="admin.send_push">
                <NotificationsPage />
              </RequirePerm>
            }
          />
          <Route
            path="rewards/signup"
            element={
              <RequirePerm perm="finance.manage_store">
                <RewardsSignupPage />
              </RequirePerm>
            }
          />
          <Route
            path="dollar-rates"
            element={
              <RequirePerm perm="finance.manage_coin_rates">
                <CountryRatesPage />
              </RequirePerm>
            }
          />
          <Route path="rewards/rates" element={<Navigate to="/dollar-rates" replace />} />
          <Route
            path="network"
            element={
              <RequireMembership>
                <NetworkPage />
              </RequireMembership>
            }
          />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  )
}
