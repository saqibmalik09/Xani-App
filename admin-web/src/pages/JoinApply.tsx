import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { get, post, appName, deviceKey } from '../lib/api'
import { canEnterConsole, useAuth } from '../lib/auth'
import { loadAppOpenConfig, tryOpenApp } from '../lib/openApp'
import { phoneLikeField, normalizePhoneValue, type FormField } from '../lib/roles'
import { validateAnswers } from '../ui/RoleForm'
import { Button } from '../ui/Button'
import { Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type PublicJoin = {
  code: string
  role: { slug: string; title: string }
  owner: { full_name: string; username: string; role: string }
  fields: FormField[]
}

/**
 * Public application landing for shared role links:
 *   /join/{code}/{roleSlug}
 * No sign-in — the candidate fills contact + role fields and submits.
 */
export function JoinApplyPage() {
  const { code = '', role = '' } = useParams()
  const { ready, session, can, appName: brand } = useAuth()
  const name = brand || appName()

  const [info, setInfo] = useState<PublicJoin | null>(null)
  const [loadErr, setLoadErr] = useState('')
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [answers, setAnswers] = useState<Record<string, string>>({})
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const codeNorm = code.trim().toUpperCase()
  const roleNorm = role.trim().toLowerCase()

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!codeNorm || !roleNorm) {
        setLoadErr('This invite link is incomplete.')
        return
      }
      const cfg = await loadAppOpenConfig()
      if (!cancelled) {
        tryOpenApp(`join/${encodeURIComponent(codeNorm)}/${encodeURIComponent(roleNorm)}`, cfg)
      }
      const r = await get<PublicJoin>(`/v1/public/join/${encodeURIComponent(codeNorm)}/${encodeURIComponent(roleNorm)}`, {
        auth: false,
      })
      if (cancelled) return
      if (!r.ok) {
        setLoadErr(r.message)
        return
      }
      setInfo(r.data)
    })()
    return () => {
      cancelled = true
    }
  }, [codeNorm, roleNorm])

  const fields = info?.fields || []

  const missingRequired = useMemo(() => {
    if (!email.trim() || !fullName.trim()) return true
    for (const f of fields) {
      if (!f.required) continue
      if (!(answers[f.key] || '').trim()) return true
    }
    return false
  }, [fields, answers, email, fullName])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!info || busy) return
    setBusy(true)
    setError('')
    setOk('')
    setFieldErrors({})
    const local = validateAnswers(fields, answers)
    if (Object.keys(local).length) {
      setFieldErrors(local)
      setBusy(false)
      setError('Fix the highlighted fields and try again.')
      return
    }
    const form: Record<string, string> = { ...answers }
    for (const f of fields) {
      if (!phoneLikeField(f)) continue
      const n = normalizePhoneValue(form[f.key] || '')
      if (n) form[f.key] = n
    }
    const r = await post(
      `/v1/public/join/${encodeURIComponent(info.code)}/${encodeURIComponent(info.role.slug)}`,
      {
        email: email.trim(),
        full_name: fullName.trim(),
        phone: phone.trim() || undefined,
        note: note.trim(),
        form,
        device_key: deviceKey(),
      },
      { auth: false },
    )
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      if (r.fields) setFieldErrors(r.fields)
      return
    }
    setOk('Application sent. You will be notified when it is reviewed.')
    setAnswers({})
    setNote('')
  }

  if (!ready || (!info && !loadErr)) return <PageLoader label="Opening application…" />

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
          maxWidth: 480,
          background: 'var(--surface)',
          border: '1px solid var(--line)',
          borderRadius: 16,
          boxShadow: 'var(--shadow)',
          padding: '28px 24px',
        }}
      >
        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--muted)', letterSpacing: 0.4 }}>{name}</div>
        <h1 style={{ margin: '6px 0 4px', fontSize: 22 }}>{info ? `Apply as ${info.role.title}` : 'Invite'}</h1>
        {info ? (
          <p className="muted" style={{ margin: '0 0 18px', fontSize: 14, lineHeight: 1.45 }}>
            Invited by {info.owner.full_name || `@${info.owner.username}`}
            {info.owner.role ? ` (${info.owner.role})` : ''}. Code <code>{info.code}</code>.
          </p>
        ) : null}

        {loadErr ? (
          <div className="err-banner" style={{ marginBottom: 12 }}>
            {loadErr}
          </div>
        ) : null}
        {ok ? <div className="ok-banner">{ok}</div> : null}
        {error ? <div className="err-banner">{error}</div> : null}

        {info && !ok ? (
          <form onSubmit={(e) => void onSubmit(e)} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Field
              label="Full name *"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              autoComplete="name"
              required
              maxLength={80}
            />
            <Field
              label="Email *"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
            <Field
              label="Phone (optional)"
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              autoComplete="tel"
            />
            {fields.map((f) => (
              <Field
                key={f.key}
                label={f.label + (f.required ? ' *' : '')}
                hint={
                  phoneLikeField(f)
                    ? f.hint || 'Include country code, e.g. +923001234567'
                    : f.hint || undefined
                }
                error={fieldErrors[f.key]}
                value={answers[f.key] || ''}
                onChange={(e) => setAnswers((prev) => ({ ...prev, [f.key]: e.target.value }))}
                type={f.type === 'number' ? 'number' : phoneLikeField(f) ? 'tel' : 'text'}
                required={f.required}
              />
            ))}
            <Field
              label="Note to your upline (optional)"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={300}
            />
            <Button type="submit" style={{ width: '100%', marginTop: 6 }} loading={busy} disabled={missingRequired || busy}>
              Submit application
            </Button>
            <p className="muted" style={{ margin: '4px 0 0', fontSize: 13, lineHeight: 1.4 }}>
              Prefer the app?{' '}
              <button
                type="button"
                onClick={() =>
                  void loadAppOpenConfig().then((cfg) =>
                    tryOpenApp(`join/${encodeURIComponent(info.code)}/${encodeURIComponent(info.role.slug)}`, cfg),
                  )
                }
                style={{ background: 'none', border: 'none', padding: 0, color: 'var(--accent)', fontWeight: 600, cursor: 'pointer' }}
              >
                Open in app
              </button>
              . No account sign-in needed to submit here.
            </p>
          </form>
        ) : null}

        {session && canEnterConsole(session, can) ? (
          <p style={{ marginTop: 16, fontSize: 13 }}>
            <Link to="/">Back to console</Link>
          </p>
        ) : null}
      </div>
    </div>
  )
}
