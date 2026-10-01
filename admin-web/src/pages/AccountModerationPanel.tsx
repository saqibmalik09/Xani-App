import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { get, post } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'

export const BAN_DURATIONS: Array<{ label: string; minutes: number }> = [
  { label: '5 minutes', minutes: 5 },
  { label: '15 minutes', minutes: 15 },
  { label: '30 minutes', minutes: 30 },
  { label: '1 hour', minutes: 60 },
  { label: '6 hours', minutes: 360 },
  { label: '12 hours', minutes: 720 },
  { label: '24 hours', minutes: 1440 },
  { label: '7 days', minutes: 10080 },
  { label: '30 days', minutes: 43200 },
  { label: 'Permanent', minutes: 0 },
]

const REASONS = [
  'Spam',
  'Harassment',
  'Threats or abusive behavior',
  'Impersonation',
  'Fraud or suspicious activity',
  'Live-stream disruption',
  'Repeated policy violations',
  'Other',
]

type BanInfo = {
  reason: string
  permanent: boolean
  until?: string
  creator_username?: string
}

type RestrictionsResp = {
  account_ban: BanInfo | null
  go_live_ban: BanInfo | null
  join_live_ban: BanInfo | null
  post_ban: BanInfo | null
}

type Props = {
  userId: string
  username: string
  banned: boolean
  state: number
  onChanged: () => void
}

type Kind = 'account' | 'go_live' | 'join_live' | 'post'

function minutesOf(value: string, custom: string): number | null {
  if (value === 'custom') {
    const n = Number(custom.trim())
    if (!Number.isFinite(n) || n < 1 || !Number.isInteger(n)) return null
    return n
  }
  return Number(value)
}

function reasonOf(pick: string, other: string) {
  return pick === 'Other' ? other.trim() || 'Other' : pick
}

function fmtUntil(iso?: string, permanent?: boolean) {
  if (permanent || !iso) return 'Permanent'
  return new Date(iso).toLocaleString(undefined, {
    day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit',
  })
}

function statusText(ban: BanInfo | null | undefined, fallback?: boolean) {
  if (ban) {
    const left = ban.permanent || !ban.until ? 'Permanent' : fmtUntil(ban.until)
    return ban.reason ? `${left} · ${ban.reason}` : left
  }
  if (fallback) return 'Banned'
  return 'Allowed'
}

/** One clear card per restriction type — pick what to block, then apply. */
export function AccountModerationPanel({ userId, username, banned, state, onChanged }: Props) {
  const { can } = useAuth()
  const [info, setInfo] = useState<RestrictionsResp | null>(null)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [open, setOpen] = useState<Kind | null>(null)

  const [reason, setReason] = useState(REASONS[0])
  const [other, setOther] = useState('')
  const [duration, setDuration] = useState('1440')
  const [custom, setCustom] = useState('')

  const reload = useCallback(async () => {
    const r = await get<RestrictionsResp>(`/v1/admin/users/${userId}/restrictions`)
    if (r.ok) setInfo(r.data)
  }, [userId])

  useEffect(() => {
    void reload()
  }, [reload])

  const run = async (key: string, fn: () => Promise<{ ok: boolean; message: string }>, success: string) => {
    setBusy(key)
    setError('')
    setOk('')
    const r = await fn()
    setBusy('')
    if (!r.ok) {
      setError(r.message || 'Action failed.')
      return
    }
    setOk(success)
    setOpen(null)
    await reload()
    onChanged()
  }

  const apply = (kind: Kind, label: string) => {
    const minutes = minutesOf(duration, custom)
    if (minutes == null) {
      setError('Enter a valid duration.')
      return
    }
    void run(
      `ban-${kind}`,
      () => post(`/v1/admin/users/${userId}/ban`, {
        kind,
        reason: reasonOf(reason, other),
        duration_minutes: minutes,
        permanent: minutes === 0,
      }),
      minutes === 0 ? `${label} applied permanently.` : `${label} applied for ${minutes} minutes.`,
    )
  }

  const canBan = can('mod.ban_user')
  const accountOn = !!(info?.account_ban || banned || state === 2)

  return (
    <div style={{ display: 'grid', gap: 12 }}>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <RestrictCard
        title="Account ban"
        blurb={`Signs @${username} out and blocks login.`}
        status={statusText(info?.account_ban, accountOn)}
        blocked={accountOn}
        canBan={canBan}
        busy={busy}
        open={open === 'account'}
        onOpen={() => setOpen(open === 'account' ? null : 'account')}
        onClear={() => void run('unban-account', () => post(`/v1/admin/users/${userId}/unban`, { kind: 'account' }), 'Account unbanned.')}
        clearKey="unban-account"
        form={(
          <BanFields
            reason={reason} setReason={setReason}
            other={other} setOther={setOther}
            duration={duration} setDuration={setDuration}
            custom={custom} setCustom={setCustom}
            busy={busy === 'ban-account'}
            label="Ban account"
            onSubmit={() => apply('account', 'Account ban')}
          />
        )}
      />

      <RestrictCard
        title="Go live"
        blurb="Cannot start a live. Stays signed in."
        status={statusText(info?.go_live_ban)}
        blocked={!!info?.go_live_ban}
        canBan={canBan}
        busy={busy}
        open={open === 'go_live'}
        onOpen={() => setOpen(open === 'go_live' ? null : 'go_live')}
        onClear={() => void run('unban-golive', () => post(`/v1/admin/users/${userId}/unban`, { kind: 'go_live' }), 'Go-live allowed again.')}
        clearKey="unban-golive"
        form={(
          <BanFields
            reason={reason} setReason={setReason}
            other={other} setOther={setOther}
            duration={duration} setDuration={setDuration}
            custom={custom} setCustom={setCustom}
            busy={busy === 'ban-go_live'}
            label="Block go live"
            onSubmit={() => apply('go_live', 'Go-live block')}
          />
        )}
      />

      <RestrictCard
        title="Join lives"
        blurb="Cannot watch or join other people's lives."
        status={statusText(info?.join_live_ban)}
        blocked={!!info?.join_live_ban}
        canBan={canBan}
        busy={busy}
        open={open === 'join_live'}
        onOpen={() => setOpen(open === 'join_live' ? null : 'join_live')}
        onClear={() => void run('unban-join', () => post(`/v1/admin/users/${userId}/unban`, { kind: 'join_live' }), 'Joining lives allowed again.')}
        clearKey="unban-join"
        form={(
          <BanFields
            reason={reason} setReason={setReason}
            other={other} setOther={setOther}
            duration={duration} setDuration={setDuration}
            custom={custom} setCustom={setCustom}
            busy={busy === 'ban-join_live'}
            label="Block join lives"
            onSubmit={() => apply('join_live', 'Join-live block')}
          />
        )}
      />

      <RestrictCard
        title="Posting"
        blurb="Cannot publish new posts or uploads."
        status={statusText(info?.post_ban)}
        blocked={!!info?.post_ban}
        canBan={canBan}
        busy={busy}
        open={open === 'post'}
        onOpen={() => setOpen(open === 'post' ? null : 'post')}
        onClear={() => void run('unban-post', () => post(`/v1/admin/users/${userId}/unban`, { kind: 'post' }), 'Posting allowed again.')}
        clearKey="unban-post"
        form={(
          <BanFields
            reason={reason} setReason={setReason}
            other={other} setOther={setOther}
            duration={duration} setDuration={setDuration}
            custom={custom} setCustom={setCustom}
            busy={busy === 'ban-post'}
            label="Block posting"
            onSubmit={() => apply('post', 'Posting block')}
          />
        )}
      />

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
          <div>
            <h2 style={{ margin: 0, fontSize: 15 }}>Device ban</h2>
            <p className="muted" style={{ margin: '4px 0 0', fontSize: 12 }}>
              Ban the phone/hardware under Devices.
            </p>
          </div>
          <Link to={`/accounts/${userId}/devices`}>
            <Button size="sm" variant="secondary">Open Devices</Button>
          </Link>
        </div>
      </Card>
    </div>
  )
}

function RestrictCard(props: {
  title: string
  blurb: string
  status: string
  blocked: boolean
  canBan: boolean
  busy: string
  open: boolean
  onOpen: () => void
  onClear: () => void
  clearKey: string
  form: ReactNode
}) {
  return (
    <Card>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
        <div style={{ minWidth: 0 }}>
          <h2 style={{ margin: 0, fontSize: 15 }}>{props.title}</h2>
          <p className="muted" style={{ margin: '4px 0 0', fontSize: 12 }}>{props.blurb}</p>
          <p style={{ margin: '8px 0 0', fontSize: 13 }}>
            <span className={`status-pill ${props.blocked ? 'off' : 'on'}`}>{props.blocked ? 'Blocked' : 'OK'}</span>
            <span className="muted" style={{ marginLeft: 8 }}>{props.status}</span>
          </p>
        </div>
        {props.canBan ? (
          props.blocked ? (
            <Button size="sm" variant="secondary" loading={props.busy === props.clearKey} onClick={props.onClear}>
              Clear
            </Button>
          ) : (
            <Button size="sm" variant={props.open ? 'ghost' : 'danger'} onClick={props.onOpen}>
              {props.open ? 'Cancel' : 'Restrict'}
            </Button>
          )
        ) : null}
      </div>
      {props.open && !props.blocked ? <div style={{ marginTop: 12 }}>{props.form}</div> : null}
    </Card>
  )
}

function BanFields(props: {
  reason: string
  setReason: (v: string) => void
  other: string
  setOther: (v: string) => void
  duration: string
  setDuration: (v: string) => void
  custom: string
  setCustom: (v: string) => void
  busy: boolean
  label: string
  onSubmit: () => void
}) {
  return (
    <div className="form-grid" style={{ maxWidth: 420 }}>
      <label className="field-label">
        Reason
        <select className="accounts-search-input" value={props.reason} onChange={(e) => props.setReason(e.target.value)} style={{ marginTop: 6 }}>
          {REASONS.map((r) => <option key={r} value={r}>{r}</option>)}
        </select>
      </label>
      {props.reason === 'Other' ? (
        <Field label="Explain" value={props.other} onChange={(e) => props.setOther(e.target.value)} />
      ) : null}
      <label className="field-label">
        Duration
        <select className="accounts-search-input" value={props.duration} onChange={(e) => props.setDuration(e.target.value)} style={{ marginTop: 6 }}>
          {BAN_DURATIONS.map((d) => <option key={d.label} value={String(d.minutes)}>{d.label}</option>)}
          <option value="custom">Custom minutes…</option>
        </select>
      </label>
      {props.duration === 'custom' ? (
        <Field label="Minutes" type="number" min={1} value={props.custom} onChange={(e) => props.setCustom(e.target.value)} />
      ) : null}
      <Button size="sm" variant="danger" loading={props.busy} onClick={props.onSubmit}>
        {props.label}
      </Button>
    </div>
  )
}
