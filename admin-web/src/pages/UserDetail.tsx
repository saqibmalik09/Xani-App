import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { get, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type Detail = {
  id: string
  username: string
  full_name: string
  avatar_url: string
  email: string
  email_verified: boolean
  bio: string
  status: string
  location: string
  birthday?: string
  gender: number
  coins: number
  diamonds: number
  beans: number
  follower_count: number
  following_count: number
  profile_views: number
  password_login_active?: boolean
  created_at: string
  roles: { role_slug: string; role_name: string; scope_type: number }[]
  referral_codes: { code: string; role_id: number }[]
  referred_by?: string
}

export function UserDetailPage() {
  const { id } = useParams()
  const { mediaBase, can } = useAuth()
  const [data, setData] = useState<Detail | null>(null)
  const [error, setError] = useState('')
  const [passNext, setPassNext] = useState('')
  const [passConfirm, setPassConfirm] = useState('')
  const [passBusy, setPassBusy] = useState(false)
  const [passOk, setPassOk] = useState('')
  const [passErr, setPassErr] = useState('')

  const load = async () => {
    if (!id) return
    const r = await get<Detail>(`/v1/admin/users/${id}`)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setData(r.data)
  }

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!id) return
      const r = await get<Detail>(`/v1/admin/users/${id}`)
      if (cancelled) return
      if (!r.ok) {
        setError(r.message)
        return
      }
      setData(r.data)
    })()
    return () => {
      cancelled = true
    }
  }, [id])

  async function savePassword(e: FormEvent) {
    e.preventDefault()
    if (!data || !can('admin.manage_users')) return
    setPassBusy(true)
    setPassErr('')
    setPassOk('')
    const r = await post(`/v1/admin/users/${data.id}/password`, {
      new_password: passNext,
      confirm_password: passConfirm,
    })
    setPassBusy(false)
    if (!r.ok) {
      setPassErr(r.message)
      return
    }
    setPassNext('')
    setPassConfirm('')
    setPassOk(data.password_login_active ? 'Password changed. Sessions signed out.' : 'Password set.')
    await load()
  }

  if (!data && !error) return <PageLoader label="Loading profile…" />
  if (error) {
    return (
      <div>
        <div className="err-banner">{error}</div>
        <Button variant="secondary" style={{ marginTop: 12 }} onClick={() => history.back()}>
          Back
        </Button>
      </div>
    )
  }
  if (!data) return null

  const face = mediaUrl(mediaBase, data.avatar_url)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <Link to="/users" className="muted" style={{ fontWeight: 600 }}>
          ← Users
        </Link>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap', marginLeft: 'auto' }}>
          {can('admin.manage_permissions') ? (
            <Link to={`/users/${id}/permissions`} style={{ fontWeight: 600 }}>
              Permissions →
            </Link>
          ) : null}
          {can('finance.view_ledger') ? (
            <Link to={`/users/${id}/ledger`} style={{ fontWeight: 600 }}>
              Ledger · where coins came from and went →
            </Link>
          ) : null}
          {can('finance.manage_recharge') ? (
            <Link to={`/recharge?user_id=${id}`} style={{ fontWeight: 600 }}>
              Recharge requests →
            </Link>
          ) : null}
        </div>
      </div>

      <Card>
        <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap', alignItems: 'center' }}>
          {face ? (
            <img className="thumb-lg" src={face} alt="" />
          ) : (
            <div className="thumb-lg" style={{ display: 'grid', placeItems: 'center', fontSize: 32, fontWeight: 800 }}>
              {(data.full_name || data.username).slice(0, 1).toUpperCase()}
            </div>
          )}
          <div style={{ minWidth: 0 }}>
            <h1 style={{ margin: 0, fontSize: 22 }}>{data.full_name || data.username}</h1>
            <div className="muted">@{data.username}</div>
            <div className="chip-row">
              {(data.roles || []).map((r) => (
                <span key={r.role_slug} className="chip">
                  {r.role_name}
                </span>
              ))}
            </div>
          </div>
        </div>
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 12 }}>
        <Stat label="Coins" value={data.coins.toLocaleString()} />
        <Stat label="Diamonds" value={data.diamonds.toLocaleString()} />
        <Stat label="Beans" value={data.beans.toLocaleString()} />
        <Stat label="Followers" value={String(data.follower_count)} />
        <Stat label="Following" value={String(data.following_count)} />
        <Stat label="Profile views" value={String(data.profile_views)} />
      </div>

      <Card title="Account">
        <dl style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: '10px 16px', margin: 0, fontSize: 14 }}>
          <dt className="muted">Email</dt>
          <dd style={{ margin: 0 }}>
            {data.email || '—'} {data.email_verified ? <span className="status-pill on">verified</span> : null}
          </dd>
          <dt className="muted">Status</dt>
          <dd style={{ margin: 0 }}>{data.status || '—'}</dd>
          <dt className="muted">Location</dt>
          <dd style={{ margin: 0 }}>{data.location || '—'}</dd>
          <dt className="muted">Birthday</dt>
          <dd style={{ margin: 0 }}>{data.birthday || '—'}</dd>
          <dt className="muted">Bio</dt>
          <dd style={{ margin: 0 }}>{data.bio || '—'}</dd>
          <dt className="muted">Joined</dt>
          <dd style={{ margin: 0 }}>{new Date(data.created_at).toLocaleString()}</dd>
          <dt className="muted">Login password</dt>
          <dd style={{ margin: 0 }}>
            {data.password_login_active ? (
              <span className="status-pill on">Set (hidden)</span>
            ) : (
              <span className="status-pill off">Not set</span>
            )}
          </dd>
          <dt className="muted">Referred by</dt>
          <dd style={{ margin: 0 }}>
            {data.referred_by ? (
              <Link to={`/users/${data.referred_by}`}>#{data.referred_by}</Link>
            ) : (
              '—'
            )}
          </dd>
        </dl>
      </Card>

      {can('admin.manage_users') ? (
        <Card title={data.password_login_active ? 'Change password' : 'Set password'}>
          {passOk ? <div className="ok-banner">{passOk}</div> : null}
          {passErr ? <div className="err-banner">{passErr}</div> : null}
          <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
            Passwords are hashed — the current value can never be shown. Saving signs this user out of every device.
          </p>
          <form onSubmit={(e) => void savePassword(e)} style={{ display: 'flex', flexDirection: 'column', gap: 12, maxWidth: 420 }}>
            <Field
              label={data.password_login_active ? 'New password' : 'Password'}
              type="password"
              autoComplete="new-password"
              value={passNext}
              onChange={(e) => setPassNext(e.target.value)}
              hint="8–128 characters"
              required
              minLength={8}
              maxLength={128}
            />
            <Field
              label="Confirm password"
              type="password"
              autoComplete="new-password"
              value={passConfirm}
              onChange={(e) => setPassConfirm(e.target.value)}
              required
              minLength={8}
              maxLength={128}
            />
            <Button type="submit" size="sm" loading={passBusy} disabled={passNext.length < 8 || passNext !== passConfirm}>
              {data.password_login_active ? 'Change password' : 'Set password'}
            </Button>
          </form>
        </Card>
      ) : null}

      <Card title="Referral codes">
        {(data.referral_codes || []).length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>
            No referral codes for this account yet.
          </p>
        ) : (
          <div className="chip-row">
            {data.referral_codes.map((c) => (
              <span key={c.code} className="chip" style={{ fontFamily: 'ui-monospace, monospace', letterSpacing: 1 }}>
                {c.code}
              </span>
            ))}
          </div>
        )}
      </Card>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        background: 'var(--surface)',
        border: '1px solid var(--line)',
        borderRadius: 'var(--radius)',
        padding: '14px 16px',
        boxShadow: 'var(--shadow)',
      }}
    >
      <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--muted)', textTransform: 'uppercase' }}>{label}</div>
      <div style={{ marginTop: 6, fontSize: 18, fontWeight: 800 }}>{value}</div>
    </div>
  )
}
