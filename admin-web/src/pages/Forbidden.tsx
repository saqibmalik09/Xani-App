import { Link } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { Button } from '../ui/Button'

export function ForbiddenPage() {
  const { logout, appName } = useAuth()
  return (
    <div style={{ minHeight: '100%', display: 'grid', placeItems: 'center', padding: 24 }}>
      <div
        style={{
          maxWidth: 420,
          textAlign: 'center',
          background: 'var(--surface)',
          border: '1px solid var(--line)',
          borderRadius: 16,
          padding: 32,
          boxShadow: 'var(--shadow)',
        }}
      >
        <h1 style={{ margin: '0 0 8px', fontSize: 20 }}>No console access</h1>
        <p style={{ margin: '0 0 20px', color: 'var(--muted)', fontSize: 14, lineHeight: 1.5 }}>
          This {appName} account does not have permission to open the dashboard. Ask an Administrator
          to grant access, or sign in with a different account.
        </p>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
          <Button variant="secondary" onClick={() => logout()}>
            Sign out
          </Button>
          <Link to="/login">
            <Button>Back to sign in</Button>
          </Link>
        </div>
      </div>
    </div>
  )
}
