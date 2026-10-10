import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { canEnterConsole, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

function safeNext(raw: string | null): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//')) return '/'
  return raw
}

export function LoginPage() {
  const { ready, session, can, login, appName } = useAuth()
  const nav = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (!ready) return <PageLoader label="Starting…" />
  // Same gate as RequireAuth: platform dashboard perm OR any live role membership.
  // Join links (/join/…) are allowed through even without console seats.
  if (session) {
    if (next.startsWith('/join/')) return <Navigate to={next} replace />
    return <Navigate to={canEnterConsole(session, can) ? next || '/' : '/forbidden'} replace />
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    if (!identifier.trim() || !password) {
      setError('Enter your email or username and password.')
      return
    }
    setBusy(true)
    const r = await login(identifier, password)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    nav(next.startsWith('/join/') ? next : '/', { replace: true })
  }

  return (
    <div
      style={{
        minHeight: '100%',
        display: 'grid',
        placeItems: 'center',
        padding: 24,
        background:
          'radial-gradient(1200px 600px at 10% -10%, #ccfbf1 0%, transparent 55%), radial-gradient(900px 500px at 100% 0%, #e2e8f0 0%, transparent 50%), var(--wash)',
      }}
    >
      <form
        onSubmit={onSubmit}
        style={{
          width: '100%',
          maxWidth: 400,
          background: 'var(--surface)',
          border: '1px solid var(--line)',
          borderRadius: 16,
          boxShadow: 'var(--shadow)',
          padding: '32px 28px 28px',
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
      >
        <div style={{ marginBottom: 4 }}>
          <div
            style={{
              fontSize: 22,
              fontWeight: 800,
              letterSpacing: -0.4,
              color: 'var(--ink)',
            }}
          >
            {appName}
          </div>
          <div style={{ marginTop: 4, fontSize: 14, color: 'var(--muted)' }}>Sign in to the console</div>
        </div>

        <Field
          label="Email or username"
          name="identifier"
          autoComplete="username"
          autoFocus
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          placeholder="you@company.com"
          disabled={busy}
        />
        <Field
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="••••••••"
          disabled={busy}
        />

        {error ? (
          <div
            role="alert"
            style={{
              background: 'var(--danger-soft)',
              color: 'var(--danger)',
              borderRadius: 'var(--radius-sm)',
              padding: '10px 12px',
              fontSize: 13,
              fontWeight: 500,
            }}
          >
            {error}
          </div>
        ) : null}

        <Button type="submit" loading={busy} style={{ width: '100%', marginTop: 4 }}>
          Sign in
        </Button>
      </form>
    </div>
  )
}
