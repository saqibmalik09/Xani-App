import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { get, post, put } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type Status = 'pending' | 'approved' | 'rejected' | 'all'

type Policy = { min_diamonds: number; max_diamonds: number; day_from: number; day_to: number; closed_message: string }
type Window = { open: boolean; day_from: number; day_to: number; today: number; message: string; next_open?: string }
type Stats = { pending: number; pending_diamonds: number; approved: number; rejected: number }
type SettingsResp = { policy: Policy; window: Window; stats: Stats }

type CountryGroup = { country_id: number; country: string; iso2: string; count: number; diamonds: number }

type WithdrawRow = {
  id: string
  user_id: string
  method_id: string
  method_name: string
  country_id: number
  country?: string
  iso2?: string
  account_title: string
  account_no: string
  diamonds: number
  status: 'pending' | 'approved' | 'rejected'
  note: string
  created_at_ms: number
  decided_at_ms?: number
  username: string
  full_name: string
  avatar_url?: string
}

const PAGE = 50

const STATUS_TABS: { key: Status; label: string }[] = [
  { key: 'pending', label: 'Pending' },
  { key: 'approved', label: 'Approved' },
  { key: 'rejected', label: 'Rejected' },
  { key: 'all', label: 'All' },
]

function fmtWhen(ms?: number): string {
  if (!ms) return '—'
  return new Date(ms).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function ordinal(n: number): string {
  const r = n % 100
  if (r >= 11 && r <= 13) return `${n}th`
  switch (n % 10) {
    case 1:
      return `${n}st`
    case 2:
      return `${n}nd`
    case 3:
      return `${n}rd`
    default:
      return `${n}th`
  }
}

function flag(iso2?: string): string {
  if (!iso2 || iso2.length !== 2) return ''
  const a = iso2.toUpperCase()
  return String.fromCodePoint(0x1f1e6 + a.charCodeAt(0) - 65, 0x1f1e6 + a.charCodeAt(1) - 65)
}

/** Payout desk: one sorted request table, status tabs, country filter. */
export function WithdrawalsPage() {
  const [settings, setSettings] = useState<SettingsResp | null>(null)
  const [status, setStatus] = useState<Status>('pending')
  const [groups, setGroups] = useState<CountryGroup[]>([])
  const [country, setCountry] = useState(-1)
  const [rows, setRows] = useState<WithdrawRow[]>([])
  const [cursor, setCursor] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const [showPolicy, setShowPolicy] = useState(false)
  const inflight = useRef<AbortController | null>(null)

  const loadSettings = useCallback(async () => {
    const r = await get<SettingsResp>('/v1/admin/withdrawals/settings')
    if (r.ok) setSettings(r.data)
    else setError(r.message)
  }, [])

  const loadGroups = useCallback(async () => {
    const r = await get<{ items: CountryGroup[] }>(`/v1/admin/withdrawals/countries?status=${status}`)
    if (!r.ok) return
    const items = r.data.items || []
    setGroups(items)
    setCountry((cur) => (cur >= 0 && !items.some((g) => g.country_id === cur) ? -1 : cur))
  }, [status])

  const loadRows = useCallback(
    async (before: string, append: boolean) => {
      inflight.current?.abort()
      const ctl = new AbortController()
      inflight.current = ctl
      if (append) setMore(true)
      else setLoading(true)
      const params = new URLSearchParams({ status, limit: String(PAGE) })
      if (country >= 0) params.set('country_id', String(country))
      if (before) params.set('before_id', before)
      const r = await get<{ items: WithdrawRow[]; next_cursor: string }>(`/v1/admin/withdrawals?${params}`, {
        signal: ctl.signal,
      })
      if (ctl.signal.aborted) return
      if (append) setMore(false)
      else setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      const items = r.data.items || []
      setRows((old) => (append ? [...old, ...items] : items))
      setCursor(r.data.next_cursor || '')
    },
    [status, country],
  )

  useEffect(() => {
    void loadSettings()
  }, [loadSettings])

  useEffect(() => {
    void loadGroups()
    void loadRows('', false)
    return () => inflight.current?.abort()
  }, [loadGroups, loadRows])

  const replace = (row: WithdrawRow) => {
    setRows((old) => {
      if (status === 'pending' || (status !== 'all' && status !== row.status)) return old.filter((r) => r.id !== row.id)
      return old.map((r) => (r.id === row.id ? row : r))
    })
    void loadGroups()
    void loadSettings()
  }

  const stats = settings?.stats
  const win = settings?.window
  const pol = settings?.policy
  const windowLabel = !win
    ? '—'
    : win.open
      ? win.day_from === win.day_to
        ? `Open · ${ordinal(win.day_from)}`
        : `Open · ${ordinal(win.day_from)}–${ordinal(win.day_to)}`
      : `Closed · ${win.next_open || '—'}`

  return (
    <div className="withdraw-page">
      <div className="page-head withdraw-head">
        <div>
          <h1>Withdrawals</h1>
          <p>Diamond payouts, newest first. Approve after paying; reject refunds diamonds.</p>
        </div>
        <div className="withdraw-head-actions">
          <Link to="/payment-methods" className="muted" style={{ fontSize: 13 }}>
            Payment methods →
          </Link>
          <Button size="sm" variant={showPolicy ? 'primary' : 'secondary'} onClick={() => setShowPolicy((v) => !v)}>
            Policy
          </Button>
          <Button
            size="sm"
            variant="secondary"
            onClick={() => {
              void loadRows('', false)
              void loadGroups()
              void loadSettings()
            }}
          >
            Refresh
          </Button>
        </div>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

      <div className="withdraw-stats">
        <span>
          <em>Pending</em> <strong>{(stats?.pending ?? 0).toLocaleString()}</strong>
        </span>
        <span>
          <em>Pending 💎</em> <strong>{(stats?.pending_diamonds ?? 0).toLocaleString()}</strong>
        </span>
        <span>
          <em>Approved</em> <strong>{(stats?.approved ?? 0).toLocaleString()}</strong>
        </span>
        <span>
          <em>Rejected</em> <strong>{(stats?.rejected ?? 0).toLocaleString()}</strong>
        </span>
        <span className={win?.open ? 'is-ok' : 'is-warn'}>
          <em>Window</em> <strong>{windowLabel}</strong>
        </span>
        <span>
          <em>Bounds</em>{' '}
          <strong>
            {pol ? `${pol.min_diamonds.toLocaleString()}–${pol.max_diamonds.toLocaleString()} 💎` : '—'}
          </strong>
        </span>
      </div>

      {showPolicy && pol ? (
        <PolicyCard
          policy={pol}
          onSaved={(next) => {
            setSettings((old) => (old ? { ...old, policy: next.policy, window: next.window } : old))
            setShowPolicy(false)
          }}
        />
      ) : null}

      <Card>
        <div className="withdraw-toolbar">
          <div className="tabs" style={{ marginBottom: 0 }}>
            {STATUS_TABS.map((t) => (
              <button
                key={t.key}
                type="button"
                className={`tab${status === t.key ? ' is-active' : ''}`}
                onClick={() => setStatus(t.key)}
              >
                {t.label}
                {t.key === 'pending' && stats ? (
                  <span className="muted" style={{ fontSize: 12 }}>
                    {' '}
                    {stats.pending}
                  </span>
                ) : null}
              </button>
            ))}
          </div>
          <label className="withdraw-filter">
            Country
            <select
              className="accounts-select"
              value={String(country)}
              onChange={(e) => setCountry(Number(e.target.value))}
            >
              <option value="-1">All countries</option>
              {groups.map((g) => (
                <option key={g.country_id} value={g.country_id}>
                  {flag(g.iso2)} {g.country} ({g.count})
                </option>
              ))}
            </select>
          </label>
        </div>

        {loading && rows.length === 0 ? (
          <PageLoader label="Loading…" />
        ) : rows.length === 0 ? (
          <p className="muted" style={{ margin: '16px 0 0' }}>
            No {status === 'all' ? '' : status} requests{country >= 0 ? ' in this country' : ''}.
          </p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Country</th>
                  <th>Method</th>
                  <th>Account</th>
                  <th className="num">Diamonds</th>
                  <th>Requested</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <RequestRow key={row.id} row={row} onChanged={replace} />
                ))}
              </tbody>
            </table>
          </div>
        )}

        {cursor ? (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 12 }}>
            <Button size="sm" variant="ghost" loading={more} onClick={() => void loadRows(cursor, true)}>
              Load more
            </Button>
          </div>
        ) : null}
      </Card>
    </div>
  )
}

function PolicyCard({ policy, onSaved }: { policy: Policy; onSaved: (r: { policy: Policy; window: Window }) => void }) {
  const [min, setMin] = useState(String(policy.min_diamonds))
  const [max, setMax] = useState(String(policy.max_diamonds))
  const [from, setFrom] = useState(String(policy.day_from))
  const [to, setTo] = useState(String(policy.day_to))
  const [msg, setMsg] = useState(policy.closed_message)
  const [saving, setSaving] = useState(false)
  const [err, setErr] = useState('')

  const save = async () => {
    const body = {
      min_diamonds: Number(min) || 0,
      max_diamonds: Number(max) || 0,
      day_from: Number(from) || 0,
      day_to: Number(to) || 0,
      closed_message: msg.trim(),
    }
    if (body.min_diamonds < 1) return setErr('Minimum must be at least 1 diamond.')
    if (body.max_diamonds < body.min_diamonds) return setErr('Maximum must be at least the minimum.')
    if (body.day_from < 1 || body.day_from > 31 || body.day_to < 1 || body.day_to > 31)
      return setErr('Days must be between 1 and 31.')
    setSaving(true)
    setErr('')
    const r = await put<{ policy: Policy; window: Window }>('/v1/admin/withdrawals/settings', body)
    setSaving(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    onSaved(r.data)
  }

  const f = Number(from) || 0
  const t = Number(to) || 0
  const preview =
    f >= 1 && t >= 1
      ? f === t
        ? `Users can request only on the ${ordinal(f)} of each month.`
        : f <= t
          ? `Users can request from the ${ordinal(f)} to the ${ordinal(t)} of each month.`
          : `Users can request from the ${ordinal(f)} through month end and until the ${ordinal(t)} of the next month.`
      : ''

  return (
    <Card title="Withdrawal policy">
      {err ? <div className="err-banner">{err}</div> : null}
      <div className="form-grid">
        <Field label="Minimum diamonds" type="number" min={1} value={min} onChange={(e) => setMin(e.target.value)} />
        <Field label="Maximum diamonds" type="number" min={1} value={max} onChange={(e) => setMax(e.target.value)} />
        <Field label="Window opens (day)" type="number" min={1} max={31} value={from} onChange={(e) => setFrom(e.target.value)} />
        <Field label="Window closes (day)" type="number" min={1} max={31} value={to} onChange={(e) => setTo(e.target.value)} />
      </div>
      {preview ? (
        <p className="muted" style={{ margin: '10px 0 0', fontSize: 12 }}>
          {preview}
        </p>
      ) : null}
      <div style={{ marginTop: 12 }}>
        <TextArea
          label="Message when closed"
          rows={2}
          maxLength={300}
          value={msg}
          onChange={(e) => setMsg(e.target.value)}
        />
      </div>
      <div className="form-actions">
        <Button size="sm" loading={saving} onClick={() => void save()}>
          Save policy
        </Button>
      </div>
    </Card>
  )
}

function StatusPill({ status }: { status: WithdrawRow['status'] }) {
  const cls = status === 'approved' ? 'on' : status === 'rejected' ? 'off' : 'warn'
  return (
    <span className={`status-pill ${cls}`}>
      {status === 'approved' ? 'Approved' : status === 'rejected' ? 'Rejected' : 'Pending'}
    </span>
  )
}

function Avatar({ url, name }: { url?: string; name: string }) {
  const { mediaBase } = useAuth()
  const src = url ? mediaUrl(mediaBase, url) : ''
  return src ? (
    <img src={src} alt="" className="withdraw-av" loading="lazy" />
  ) : (
    <span className="withdraw-av withdraw-av--letter">{name.slice(0, 1).toUpperCase()}</span>
  )
}

function RequestRow({ row, onChanged }: { row: WithdrawRow; onChanged: (row: WithdrawRow) => void }) {
  const [busy, setBusy] = useState<'approve' | 'reject' | ''>('')
  const [note, setNote] = useState('')
  const [asking, setAsking] = useState(false)
  const [err, setErr] = useState('')
  const name = row.full_name || `@${row.username}`

  const decide = async (approve: boolean) => {
    if (
      approve &&
      !window.confirm(`Approve 💎 ${row.diamonds.toLocaleString()} to ${name} via ${row.method_name}? Mark only after money is sent.`)
    )
      return
    setBusy(approve ? 'approve' : 'reject')
    setErr('')
    const r = await post<WithdrawRow>(`/v1/admin/withdrawals/${row.id}/${approve ? 'approve' : 'reject'}`, {
      note: note.trim(),
    })
    setBusy('')
    if (!r.ok) {
      setErr(r.message)
      return
    }
    setAsking(false)
    onChanged({ ...row, ...r.data })
  }

  return (
    <>
      <tr>
        <td>
          <span className="withdraw-user">
            <Avatar url={row.avatar_url} name={name} />
            <span>
              <Link to={`/users/${row.user_id}`} className="withdraw-user-name">
                {name}
              </Link>
              <div className="muted">@{row.username}</div>
            </span>
          </span>
        </td>
        <td className="nowrap">
          {flag(row.iso2)} {row.country || row.iso2 || '—'}
        </td>
        <td>{row.method_name}</td>
        <td>
          <div className="withdraw-account">{row.account_title}</div>
          <button
            type="button"
            className="withdraw-copy"
            title="Copy account number"
            onClick={() => void navigator.clipboard?.writeText(row.account_no)}
          >
            {row.account_no}
          </button>
        </td>
        <td className="num">
          <strong>{row.diamonds.toLocaleString()}</strong>
        </td>
        <td className="nowrap muted">{fmtWhen(row.created_at_ms)}</td>
        <td>
          <StatusPill status={row.status} />
          {row.note ? <div className="muted withdraw-note">{row.note}</div> : null}
        </td>
        <td className="nowrap">
          {row.status === 'pending' ? (
            <span className="withdraw-actions">
              <Button size="sm" loading={busy === 'approve'} disabled={busy === 'reject'} onClick={() => void decide(true)}>
                Approve
              </Button>
              <Button
                size="sm"
                variant={asking ? 'secondary' : 'danger'}
                disabled={busy !== ''}
                onClick={() => setAsking((v) => !v)}
              >
                {asking ? 'Cancel' : 'Reject'}
              </Button>
            </span>
          ) : (
            <span className="muted">{row.decided_at_ms ? fmtWhen(row.decided_at_ms) : '—'}</span>
          )}
        </td>
      </tr>
      {asking || err ? (
        <tr className="withdraw-expand">
          <td colSpan={8}>
            {err ? <div className="err-banner" style={{ marginBottom: asking ? 8 : 0 }}>{err}</div> : null}
            {asking ? (
              <div className="withdraw-reject">
                <Field
                  label="Reject reason (shown to user)"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  maxLength={500}
                  placeholder="Account number does not match…"
                />
                <Button size="sm" variant="danger" loading={busy === 'reject'} onClick={() => void decide(false)}>
                  Reject & refund {row.diamonds.toLocaleString()} 💎
                </Button>
              </div>
            ) : null}
          </td>
        </tr>
      ) : null}
    </>
  )
}
