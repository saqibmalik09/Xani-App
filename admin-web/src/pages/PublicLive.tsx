import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { get, appName } from '../lib/api'
import { loadAppOpenConfig, tryOpenApp } from '../lib/openApp'
import { mediaUrl, useAuth } from '../lib/auth'
import { PageLoader } from '../ui/Loader'

type PublicLive = {
  id: string
  live: boolean
  kind: string
  title: string
  host: {
    username: string
    full_name: string
    avatar_url: string
  }
}

/**
 * Live share landing: https://{website}/live/{roomId}
 * Tries to open the installed app into that room; if the broadcast is over,
 * says so instead of dumping the user into a cold home screen.
 */
export function PublicLivePage() {
  const { roomId = '' } = useParams()
  const { mediaBase, appName: brand } = useAuth()
  const name = brand || appName()
  const [info, setInfo] = useState<PublicLive | null>(null)
  const [err, setErr] = useState('')
  const [triedApp, setTriedApp] = useState(false)

  const id = roomId.trim()

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!/^\d+$/.test(id) || id === '0') {
        setErr('This live link is incomplete.')
        return
      }
      const cfg = await loadAppOpenConfig()
      const r = await get<PublicLive>(`/v1/public/live/${encodeURIComponent(id)}`, { auth: false })
      if (cancelled) return
      if (!r.ok) {
        setErr(r.message)
        return
      }
      setInfo(r.data)
      // Only hand off to the app when the room is still live. An ended link
      // staying on this page is the correct UX — opening the app would just
      // flash "ended" after a cold start.
      if (r.data.live) {
        tryOpenApp(`live/${encodeURIComponent(id)}`, cfg)
        setTriedApp(true)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [id])

  if (!info && !err) return <PageLoader label="Opening live…" />

  const host = info?.host
  const display = (host?.full_name || host?.username || '').trim()
  const av = host ? mediaUrl(mediaBase, host.avatar_url) : ''
  const ended = info != null && !info.live

  return (
    <div
      style={{
        minHeight: '100%',
        display: 'grid',
        placeItems: 'start center',
        padding: '32px 20px 48px',
        background:
          'radial-gradient(1200px 600px at 10% -10%, #fce7f3 0%, transparent 55%), radial-gradient(900px 500px at 100% 0%, #ede9fe 0%, transparent 50%), var(--wash)',
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
              {av ? (
                <img src={av} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                (display || 'L').slice(0, 1).toUpperCase()
              )}
            </div>
            <h1 style={{ margin: '0 0 4px', fontSize: 22 }}>{display || 'Live'}</h1>
            {host?.username ? (
              <p className="muted" style={{ margin: 0, fontSize: 14 }}>
                @{host.username}
              </p>
            ) : null}
            {info.title ? (
              <p style={{ margin: '12px 0 0', fontSize: 15, fontWeight: 600 }}>{info.title}</p>
            ) : null}
            <p
              className="muted"
              style={{
                margin: '16px 0 0',
                fontSize: 13,
                lineHeight: 1.45,
                color: ended ? 'var(--danger, #b91c1c)' : undefined,
              }}
            >
              {ended
                ? 'This live has ended.'
                : triedApp
                  ? 'If the app is installed it should open. Otherwise install it and open this link again.'
                  : 'Opening in the app…'}
            </p>
            {!ended ? (
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
                onClick={() =>
                  void loadAppOpenConfig().then((cfg) => tryOpenApp(`live/${encodeURIComponent(id)}`, cfg))
                }
              >
                Open in app
              </button>
            ) : null}
          </>
        ) : null}
        <p style={{ marginTop: 18, fontSize: 13 }}>
          <Link to="/login">Sign in to console</Link>
        </p>
      </div>
    </div>
  )
}
