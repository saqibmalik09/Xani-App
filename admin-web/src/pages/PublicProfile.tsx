import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { get, appName } from '../lib/api'
import { loadAppOpenConfig, tryOpenApp } from '../lib/openApp'
import { mediaUrl, useAuth } from '../lib/auth'
import { PageLoader } from '../ui/Loader'

type PublicProfile = {
  id: string
  username: string
  full_name: string
  avatar_url: string
}

/**
 * Public profile share landing: https://{website}/u/{username}
 * Tries to open the installed app, then shows a simple card as fallback.
 */
export function PublicProfilePage() {
  const { username = '' } = useParams()
  const { mediaBase, appName: brand } = useAuth()
  const name = brand || appName()
  const [info, setInfo] = useState<PublicProfile | null>(null)
  const [err, setErr] = useState('')
  const [triedApp, setTriedApp] = useState(false)

  const user = username.trim().replace(/^@/, '')

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!user) {
        setErr('This profile link is incomplete.')
        return
      }
      const cfg = await loadAppOpenConfig()
      if (!cancelled) {
        tryOpenApp(`u/${encodeURIComponent(user)}`, cfg)
        setTriedApp(true)
      }
      const r = await get<PublicProfile>(`/v1/public/profile/${encodeURIComponent(user)}`, { auth: false })
      if (cancelled) return
      if (!r.ok) {
        setErr(r.message)
        return
      }
      setInfo(r.data)
    })()
    return () => {
      cancelled = true
    }
  }, [user])

  if (!info && !err) return <PageLoader label="Opening profile…" />

  const av = info ? mediaUrl(mediaBase, info.avatar_url) : ''

  return (
    <div
      style={{
        minHeight: '100%',
        display: 'grid',
        placeItems: 'start center',
        padding: '32px 20px 48px',
        background:
          'radial-gradient(1200px 600px at 10% -10%, #ccfbf1 0%, transparent 55%), radial-gradient(900px 500px at 100% 0%, #e2e8f0 0%, transparent 50%), var(--wash)',
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 420,
          background: 'var(--surface)',
          border: '1px solid var(--line)',
          borderRadius: 16,
          boxShadow: 'var(--shadow)',
          padding: '28px 24px',
          textAlign: 'center',
        }}
      >
        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--muted)', letterSpacing: 0.4 }}>{name}</div>
        {err ? (
          <div className="err-banner" style={{ marginTop: 16 }}>
            {err}
          </div>
        ) : null}
        {info ? (
          <>
            <div
              style={{
                width: 88,
                height: 88,
                borderRadius: '50%',
                margin: '18px auto 12px',
                overflow: 'hidden',
                background: 'var(--wash)',
                display: 'grid',
                placeItems: 'center',
                fontSize: 32,
                fontWeight: 700,
                color: 'var(--muted)',
              }}
            >
              {av ? <img src={av} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : (info.full_name || info.username).slice(0, 1).toUpperCase()}
            </div>
            <h1 style={{ margin: '0 0 4px', fontSize: 22 }}>{info.full_name || info.username}</h1>
            <p className="muted" style={{ margin: 0, fontSize: 14 }}>
              @{info.username}
            </p>
            <p className="muted" style={{ margin: '16px 0 0', fontSize: 13, lineHeight: 1.45 }}>
              {triedApp
                ? 'If the app is installed it should open. Otherwise install it and open this link again.'
                : 'Opening in the app…'}
            </p>
            <button
              type="button"
              style={{
                marginTop: 16,
                width: '100%',
                height: 44,
                borderRadius: 10,
                border: 'none',
                background: 'var(--ink)',
                color: '#fff',
                fontWeight: 700,
                cursor: 'pointer',
              }}
              onClick={() => void loadAppOpenConfig().then((cfg) => tryOpenApp(`u/${encodeURIComponent(info.username)}`, cfg))}
            >
              Open in app
            </button>
          </>
        ) : null}
        <p style={{ marginTop: 18, fontSize: 13 }}>
          <Link to="/login">Sign in to console</Link>
        </p>
      </div>
    </div>
  )
}
