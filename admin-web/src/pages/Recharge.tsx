import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { del, get, patch, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { useCountries, type Country } from './PaymentMethods'

type Status = 'pending' | 'approved' | 'rejected' | 'all'
type Tab = 'requests' | 'packages' | 'methods'

type Stats = { pending: number; pending_coins: number; approved: number; approved_coins: number; rejected: number }

export type RechargeRow = {
  id: string
  user_id: string
  package_id: string
  package_title: string
  currency: string
  price_minor: number
  price: string
  coins: number
  credited_coins: number
  method_id: string
  method_name: string
  account_title: string
  account_no: string
  country_id: number
  country?: string
  iso2?: string
  screenshot_key: string
  screenshot_url: string
  user_note: string
  status: 'pending' | 'approved' | 'rejected'
  note: string
  created_at_ms: number
  decided_at_ms?: number
  username: string
  full_name: string
  avatar_url?: string
}

type Package = {
  id: string
  title: string
  country_id: number
  currency: string
  price_minor: number
  price: string
  coins: number
  bonus_coins: number
  total_coins: number
  status: number
  sort_order: number
}

type Method = {
  id: string
  name: string
  title: string
  account_title: string
  account_no: string
  instructions: string
  country_id: number
  status: number
  sort_order: number
}

const PAGE = 50

const STATUS_TABS: { key: Status; label: string }[] = [
  { key: 'pending', label: 'Pending' },
  { key: 'approved', label: 'Approved' },
  { key: 'rejected', label: 'Rejected' },
  { key: 'all', label: 'All' },
]

const selectStyle: CSSProperties = {
  height: 36,
  borderRadius: 8,
  border: '1px solid var(--line)',
  padding: '0 10px',
  background: 'var(--surface)',
  fontSize: 13,
  width: '100%',
}

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

function flag(iso2?: string): string {
  if (!iso2 || iso2.length !== 2) return ''
  const a = iso2.toUpperCase()
  return String.fromCodePoint(0x1f1e6 + a.charCodeAt(0) - 65, 0x1f1e6 + a.charCodeAt(1) - 65)
}

function useCountryName(countries: Country[]) {
  return useMemo(() => {
    const m = new Map<number, string>()
    for (const c of countries) m.set(c.id, c.name)
    return (id: number) => (id ? m.get(id) || `#${id}` : 'All countries')
  }, [countries])
}

/**
 * Recharge desk. Requests: every "I paid, here is the screenshot" claim with
 * the proof inline; approve credits coins (optionally a different amount),
 * reject sends the reason to the user. Packages: price → coins per country.
 * Methods: the receiving accounts the user is told to pay into.
 */
export function RechargePage() {
  const [params, setParams] = useSearchParams()
  const tab = (params.get('tab') as Tab) || 'requests'
  const setTab = (t: Tab) => {
    const next = new URLSearchParams(params)
    if (t === 'requests') next.delete('tab')
    else next.set('tab', t)
    setParams(next, { replace: true })
  }
  const userFilter = params.get('user_id') || ''
  const countries = useCountries()

  return (
    <div className="withdraw-page">
      <div className="page-head withdraw-head">
        <div>
          <h1>Recharge</h1>
          <p>Coins bought by bank or wallet transfer. Check the screenshot against the account, then approve to credit.</p>
        </div>
        <div className="tabs" style={{ marginBottom: 0 }}>
          <button type="button" className={`tab${tab === 'requests' ? ' is-active' : ''}`} onClick={() => setTab('requests')}>
            Requests
          </button>
          <button type="button" className={`tab${tab === 'packages' ? ' is-active' : ''}`} onClick={() => setTab('packages')}>
            Packages
          </button>
          <button type="button" className={`tab${tab === 'methods' ? ' is-active' : ''}`} onClick={() => setTab('methods')}>
            Receiving accounts
          </button>
        </div>
      </div>

      {tab === 'packages' ? (
        <PackagesTab countries={countries} />
      ) : tab === 'methods' ? (
        <MethodsTab countries={countries} />
      ) : (
        <RequestsTab
          userFilter={userFilter}
          onClearUser={() => {
            const next = new URLSearchParams(params)
            next.delete('user_id')
            setParams(next, { replace: true })
          }}
        />
      )}
    </div>
  )
}

// ─── requests ────────────────────────────────────────────────────────────────

function RequestsTab({ userFilter, onClearUser }: { userFilter: string; onClearUser: () => void }) {
  const [stats, setStats] = useState<Stats | null>(null)
  const [status, setStatus] = useState<Status>('pending')
  const [rows, setRows] = useState<RechargeRow[]>([])
  const [cursor, setCursor] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const inflight = useRef<AbortController | null>(null)

  const loadStats = useCallback(async () => {
    const r = await get<Stats>('/v1/admin/recharge/stats')
    if (r.ok) setStats(r.data)
  }, [])

  const loadRows = useCallback(
    async (before: string, append: boolean) => {
      inflight.current?.abort()
      const ctl = new AbortController()
      inflight.current = ctl
      if (append) setMore(true)
      else setLoading(true)
      const q = new URLSearchParams({ status, limit: String(PAGE) })
      if (userFilter) q.set('user_id', userFilter)
      if (before) q.set('before_id', before)
      const r = await get<{ items: RechargeRow[]; next_cursor: string }>(`/v1/admin/recharge/requests?${q}`, {
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
    [status, userFilter],
  )

  useEffect(() => {
    void loadStats()
  }, [loadStats])

  useEffect(() => {
    void loadRows('', false)
    return () => inflight.current?.abort()
  }, [loadRows])

  const replace = (row: RechargeRow) => {
    setRows((old) => {
      if (status === 'pending' || (status !== 'all' && status !== row.status)) return old.filter((r) => r.id !== row.id)
      return old.map((r) => (r.id === row.id ? row : r))
    })
    void loadStats()
  }

  return (
    <>
      <div className="withdraw-stats">
        <span className={stats && stats.pending > 0 ? 'is-warn' : ''}>
          <em>Pending</em> <strong>{(stats?.pending ?? 0).toLocaleString()}</strong>
        </span>
        <span>
          <em>Pending 🪙</em> <strong>{(stats?.pending_coins ?? 0).toLocaleString()}</strong>
        </span>
        <span>
          <em>Approved</em> <strong>{(stats?.approved ?? 0).toLocaleString()}</strong>
        </span>
        <span>
          <em>Credited 🪙</em> <strong>{(stats?.approved_coins ?? 0).toLocaleString()}</strong>
        </span>
        <span>
          <em>Rejected</em> <strong>{(stats?.rejected ?? 0).toLocaleString()}</strong>
        </span>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

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
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            {userFilter ? (
              <span className="chip">
                User <Link to={`/users/${userFilter}`}>#{userFilter}</Link>
                <button type="button" className="withdraw-copy" onClick={onClearUser} title="Show all users" style={{ marginLeft: 6 }}>
                  ×
                </button>
              </span>
            ) : null}
            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                void loadRows('', false)
                void loadStats()
              }}
            >
              Refresh
            </Button>
          </div>
        </div>

        {loading && rows.length === 0 ? (
          <PageLoader label="Loading…" />
        ) : rows.length === 0 ? (
          <p className="muted" style={{ margin: '16px 0 0' }}>
            No {status === 'all' ? '' : status} requests.
          </p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Package</th>
                  <th>Paid to</th>
                  <th>Proof</th>
                  <th className="num">Coins</th>
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
    </>
  )
}

function StatusPill({ status }: { status: RechargeRow['status'] }) {
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

function Proof({ row }: { row: RechargeRow }) {
  const { mediaBase } = useAuth()
  const src = row.screenshot_url || (row.screenshot_key ? mediaUrl(mediaBase, row.screenshot_key) : '')
  if (!src) return <span className="muted">—</span>
  return (
    <a href={src} target="_blank" rel="noreferrer" title="Open screenshot in a new tab">
      <img
        src={src}
        alt="Payment screenshot"
        loading="lazy"
        style={{ width: 56, height: 72, objectFit: 'cover', borderRadius: 8, border: '1px solid var(--line)', display: 'block' }}
      />
    </a>
  )
}

function RequestRow({ row, onChanged }: { row: RechargeRow; onChanged: (row: RechargeRow) => void }) {
  const [busy, setBusy] = useState<'approve' | 'reject' | ''>('')
  const [mode, setMode] = useState<'' | 'approve' | 'reject'>('')
  const [note, setNote] = useState('')
  const [coins, setCoins] = useState(String(row.coins))
  const [err, setErr] = useState('')
  const name = row.full_name || `@${row.username}`

  const decide = async (approve: boolean) => {
    const override = Math.max(0, Math.floor(Number(coins) || 0))
    if (approve && override !== row.coins) {
      if (!window.confirm(`Credit ${override.toLocaleString()} coins instead of the package's ${row.coins.toLocaleString()}?`)) return
    }
    if (!approve && !note.trim()) {
      setErr('Give the user a reason for the rejection.')
      return
    }
    setBusy(approve ? 'approve' : 'reject')
    setErr('')
    const r = await post<RechargeRow>(`/v1/admin/recharge/requests/${row.id}/${approve ? 'approve' : 'reject'}`, {
      note: note.trim(),
      coins: approve && override !== row.coins ? override : 0,
    })
    setBusy('')
    if (!r.ok) {
      setErr(r.message)
      return
    }
    setMode('')
    onChanged({ ...row, ...r.data })
  }

  const open = mode !== '' || err !== '' || row.user_note !== ''

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
              <div className="muted">
                @{row.username}
                {row.iso2 ? ` · ${flag(row.iso2)} ${row.country || row.iso2}` : ''}
              </div>
            </span>
          </span>
        </td>
        <td>
          <div className="withdraw-account">
            {row.currency} {row.price}
          </div>
          <div className="muted">{row.package_title || '—'}</div>
        </td>
        <td>
          <div className="withdraw-account">{row.method_name}</div>
          <div className="muted" style={{ fontSize: 12 }}>
            {row.account_title}
          </div>
          <button
            type="button"
            className="withdraw-copy"
            title="Copy account number"
            onClick={() => void navigator.clipboard?.writeText(row.account_no)}
          >
            {row.account_no}
          </button>
        </td>
        <td>
          <Proof row={row} />
        </td>
        <td className="num">
          <strong>{(row.status === 'approved' ? row.credited_coins : row.coins).toLocaleString()}</strong>
          {row.status === 'approved' && row.credited_coins !== row.coins ? (
            <div className="muted" style={{ fontSize: 11 }}>
              pkg {row.coins.toLocaleString()}
            </div>
          ) : null}
        </td>
        <td className="nowrap muted">{fmtWhen(row.created_at_ms)}</td>
        <td>
          <StatusPill status={row.status} />
          {row.note ? <div className="muted withdraw-note">{row.note}</div> : null}
        </td>
        <td className="nowrap">
          {row.status === 'pending' ? (
            <span className="withdraw-actions">
              <Button
                size="sm"
                variant={mode === 'approve' ? 'secondary' : 'primary'}
                disabled={busy !== ''}
                onClick={() => setMode((m) => (m === 'approve' ? '' : 'approve'))}
              >
                {mode === 'approve' ? 'Cancel' : 'Approve'}
              </Button>
              <Button
                size="sm"
                variant={mode === 'reject' ? 'secondary' : 'danger'}
                disabled={busy !== ''}
                onClick={() => setMode((m) => (m === 'reject' ? '' : 'reject'))}
              >
                {mode === 'reject' ? 'Cancel' : 'Reject'}
              </Button>
            </span>
          ) : (
            <span className="muted">{row.decided_at_ms ? fmtWhen(row.decided_at_ms) : '—'}</span>
          )}
        </td>
      </tr>
      {open ? (
        <tr className="withdraw-expand">
          <td colSpan={8}>
            {row.user_note ? (
              <div className="muted" style={{ marginBottom: mode || err ? 8 : 0, fontSize: 13 }}>
                <strong>User says:</strong> {row.user_note}
              </div>
            ) : null}
            {err ? <div className="err-banner" style={{ marginBottom: mode ? 8 : 0 }}>{err}</div> : null}
            {mode === 'approve' ? (
              <div className="withdraw-reject">
                <Field
                  label="Coins to credit"
                  hint={`Package gives ${row.coins.toLocaleString()}. Change only if the user paid a different amount.`}
                  type="number"
                  min={1}
                  value={coins}
                  onChange={(e) => setCoins(e.target.value)}
                />
                <Field
                  label="Note to user (optional)"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  maxLength={500}
                  placeholder="Thanks, credited."
                />
                <Button size="sm" loading={busy === 'approve'} onClick={() => void decide(true)}>
                  Approve & credit {Math.max(0, Math.floor(Number(coins) || 0)).toLocaleString()} 🪙
                </Button>
              </div>
            ) : null}
            {mode === 'reject' ? (
              <div className="withdraw-reject">
                <Field
                  label="Reject reason (shown to user)"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  maxLength={500}
                  placeholder="No payment received on this account…"
                />
                <Button size="sm" variant="danger" loading={busy === 'reject'} onClick={() => void decide(false)}>
                  Reject
                </Button>
              </div>
            ) : null}
          </td>
        </tr>
      ) : null}
    </>
  )
}

// ─── packages ────────────────────────────────────────────────────────────────

type PkgDraft = {
  title: string
  country_id: string
  currency: string
  price: string
  coins: string
  bonus_coins: string
  status: boolean
  sort_order: string
}

const EMPTY_PKG: PkgDraft = {
  title: '',
  country_id: '0',
  currency: '',
  price: '',
  coins: '',
  bonus_coins: '0',
  status: true,
  sort_order: '0',
}

function pkgDraft(p: Package): PkgDraft {
  return {
    title: p.title,
    country_id: String(p.country_id || 0),
    currency: p.currency,
    price: p.price,
    coins: String(p.coins),
    bonus_coins: String(p.bonus_coins || 0),
    status: p.status === 1,
    sort_order: String(p.sort_order || 0),
  }
}

function sortPkgs(list: Package[]): Package[] {
  return list.slice().sort((a, b) => a.country_id - b.country_id || a.sort_order - b.sort_order || a.price_minor - b.price_minor)
}

function PackagesTab({ countries }: { countries: Country[] }) {
  const [items, setItems] = useState<Package[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState('new')
  const countryName = useCountryName(countries)

  const load = useCallback(async () => {
    setLoading(true)
    const r = await get<{ items: Package[] }>('/v1/admin/recharge/packages')
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setItems(sortPkgs(r.data.items || []))
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const current = selected === 'new' ? null : items.find((p) => p.id === selected) || null

  return (
    <>
      {error ? <div className="err-banner">{error}</div> : null}
      <div className="catalog-split">
        <Card
          title="Packages"
          actions={
            <Button size="sm" variant={selected === 'new' ? 'primary' : 'secondary'} onClick={() => setSelected('new')}>
              + New
            </Button>
          }
        >
          {loading && items.length === 0 ? (
            <PageLoader label="Loading packages…" />
          ) : items.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>
              No packages yet. Users see nothing to buy until one is on.
            </p>
          ) : (
            <div className="role-list">
              {items.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  className={`role-row${p.id === selected ? ' is-active' : ''}`}
                  onClick={() => setSelected(p.id)}
                  style={{ display: 'flex', alignItems: 'center', gap: 10, width: '100%', textAlign: 'left' }}
                >
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="role-row-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span>
                        {p.currency} {p.price} → 🪙 {p.total_coins.toLocaleString()}
                      </span>
                      <span className={`status-pill ${p.status === 1 ? 'on' : 'off'}`} style={{ fontSize: 10 }}>
                        {p.status === 1 ? 'On' : 'Off'}
                      </span>
                    </div>
                    <div className="role-row-meta">
                      {countryName(p.country_id)}
                      {p.title ? ` · ${p.title}` : ''}
                      {p.bonus_coins ? ` · +${p.bonus_coins.toLocaleString()} bonus` : ''}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </Card>

        <PackageEditor
          key={current?.id || 'new'}
          pkg={current}
          countries={countries}
          onSaved={(p) => {
            setItems((old) => {
              const i = old.findIndex((x) => x.id === p.id)
              if (i < 0) return sortPkgs([...old, p])
              const next = old.slice()
              next[i] = p
              return sortPkgs(next)
            })
            setSelected(p.id)
          }}
          onDeleted={(id, hard) => {
            if (hard) {
              setItems((old) => old.filter((x) => x.id !== id))
              setSelected('new')
            } else {
              void load()
            }
          }}
        />
      </div>
    </>
  )
}

function PackageEditor({
  pkg,
  countries,
  onSaved,
  onDeleted,
}: {
  pkg: Package | null
  countries: Country[]
  onSaved: (p: Package) => void
  onDeleted: (id: string, hard: boolean) => void
}) {
  const [draft, setDraft] = useState<PkgDraft>(pkg ? pkgDraft(pkg) : EMPTY_PKG)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [err, setErr] = useState('')
  const [ok, setOk] = useState('')
  const set = <K extends keyof PkgDraft>(k: K, v: PkgDraft[K]) => setDraft((d) => ({ ...d, [k]: v }))

  const save = async () => {
    const b = {
      title: draft.title.trim(),
      country_id: Number(draft.country_id) || 0,
      currency: draft.currency.trim().toUpperCase(),
      price: draft.price.trim(),
      coins: Math.floor(Number(draft.coins) || 0),
      bonus_coins: Math.max(0, Math.floor(Number(draft.bonus_coins) || 0)),
      status: draft.status ? 1 : 0,
      sort_order: Number(draft.sort_order) || 0,
    }
    if (b.currency.length < 3) return setErr('Enter a currency code such as PKR or USD.')
    if (!/^\d+(\.\d{1,2})?$/.test(b.price) || Number(b.price) <= 0) return setErr('Price must be a positive amount like 1000 or 9.99.')
    if (b.coins < 1) return setErr('Coins must be at least 1.')
    setSaving(true)
    setErr('')
    setOk('')
    const r = pkg
      ? await patch<Package>(`/v1/admin/recharge/packages/${pkg.id}`, b)
      : await post<Package>('/v1/admin/recharge/packages', b)
    setSaving(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    setOk(r.message)
    onSaved(r.data)
  }

  const remove = async () => {
    if (!pkg) return
    if (!window.confirm(`Delete this package? If users already bought it, it will be switched off instead.`)) return
    setDeleting(true)
    setErr('')
    const r = await del<{ deleted: boolean }>(`/v1/admin/recharge/packages/${pkg.id}`)
    setDeleting(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    onDeleted(pkg.id, r.data.deleted)
  }

  const total = Math.floor(Number(draft.coins) || 0) + Math.max(0, Math.floor(Number(draft.bonus_coins) || 0))

  return (
    <Card
      title={pkg ? `Edit · ${pkg.currency} ${pkg.price}` : 'New package'}
      actions={
        pkg ? (
          <Button size="sm" variant="danger" loading={deleting} onClick={() => void remove()}>
            Delete
          </Button>
        ) : undefined
      }
    >
      {err ? <div className="err-banner">{err}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <div className="form-grid">
        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>Country</span>
          <select style={selectStyle} value={draft.country_id} onChange={(e) => set('country_id', e.target.value)}>
            <option value="0">All countries</option>
            {countries.map((c) => (
              <option key={c.id} value={String(c.id)}>
                {c.name} ({c.iso2})
              </option>
            ))}
          </select>
          <span className="muted" style={{ fontSize: 12 }}>
            Users see the packages for their country, else the global ones.
          </span>
        </label>
        <Field label="Currency" value={draft.currency} onChange={(e) => set('currency', e.target.value)} placeholder="PKR" maxLength={8} />
        <Field label="Price" hint="What the user sends, e.g. 1000 or 9.99." value={draft.price} onChange={(e) => set('price', e.target.value)} placeholder="1000" />
        <Field label="Coins" type="number" min={1} value={draft.coins} onChange={(e) => set('coins', e.target.value)} />
        <Field label="Bonus coins" hint="Extra on top, shown as a badge." type="number" min={0} value={draft.bonus_coins} onChange={(e) => set('bonus_coins', e.target.value)} />
        <Field label="Title" hint="Optional label, e.g. “Starter”." value={draft.title} onChange={(e) => set('title', e.target.value)} maxLength={80} />
        <Field label="Sort order" type="number" value={draft.sort_order} onChange={(e) => set('sort_order', e.target.value)} />
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 26, cursor: 'pointer' }}>
          <input type="checkbox" checked={draft.status} onChange={(e) => set('status', e.target.checked)} />
          <span style={{ fontSize: 13, fontWeight: 600 }}>Switched on (visible in the app)</span>
        </label>
      </div>
      <p className="muted" style={{ margin: '12px 0 0', fontSize: 13 }}>
        User pays <strong>{draft.currency.toUpperCase() || '—'} {draft.price || '—'}</strong> and receives{' '}
        <strong>🪙 {total.toLocaleString()}</strong>.
      </p>
      <div className="form-actions">
        <Button loading={saving} onClick={() => void save()}>
          {pkg ? 'Save changes' : 'Create package'}
        </Button>
      </div>
    </Card>
  )
}

// ─── methods (receiving accounts) ────────────────────────────────────────────

type MethodDraft = {
  name: string
  title: string
  account_title: string
  account_no: string
  instructions: string
  country_id: string
  status: boolean
  sort_order: string
}

const EMPTY_METHOD: MethodDraft = {
  name: '',
  title: '',
  account_title: '',
  account_no: '',
  instructions: '',
  country_id: '0',
  status: true,
  sort_order: '0',
}

function methodDraft(m: Method): MethodDraft {
  return {
    name: m.name,
    title: m.title,
    account_title: m.account_title,
    account_no: m.account_no,
    instructions: m.instructions,
    country_id: String(m.country_id || 0),
    status: m.status === 1,
    sort_order: String(m.sort_order || 0),
  }
}

function sortMethods(list: Method[]): Method[] {
  return list.slice().sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name))
}

function MethodsTab({ countries }: { countries: Country[] }) {
  const [items, setItems] = useState<Method[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState('new')
  const countryName = useCountryName(countries)

  const load = useCallback(async () => {
    setLoading(true)
    const r = await get<{ items: Method[] }>('/v1/admin/recharge/methods')
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setItems(sortMethods(r.data.items || []))
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const current = selected === 'new' ? null : items.find((m) => m.id === selected) || null

  return (
    <>
      {error ? <div className="err-banner">{error}</div> : null}
      <div className="catalog-split">
        <Card
          title="Receiving accounts"
          actions={
            <Button size="sm" variant={selected === 'new' ? 'primary' : 'secondary'} onClick={() => setSelected('new')}>
              + New
            </Button>
          }
        >
          {loading && items.length === 0 ? (
            <PageLoader label="Loading accounts…" />
          ) : items.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>
              No receiving accounts yet. Users cannot recharge until one is on.
            </p>
          ) : (
            <div className="role-list">
              {items.map((m) => (
                <button
                  key={m.id}
                  type="button"
                  className={`role-row${m.id === selected ? ' is-active' : ''}`}
                  onClick={() => setSelected(m.id)}
                  style={{ display: 'flex', alignItems: 'center', gap: 10, width: '100%', textAlign: 'left' }}
                >
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="role-row-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{m.name}</span>
                      <span className={`status-pill ${m.status === 1 ? 'on' : 'off'}`} style={{ fontSize: 10 }}>
                        {m.status === 1 ? 'On' : 'Off'}
                      </span>
                    </div>
                    <div className="role-row-meta">
                      {countryName(m.country_id)} · {m.account_title} · {m.account_no}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </Card>

        <MethodEditor
          key={current?.id || 'new'}
          method={current}
          countries={countries}
          onSaved={(m) => {
            setItems((old) => {
              const i = old.findIndex((x) => x.id === m.id)
              if (i < 0) return sortMethods([...old, m])
              const next = old.slice()
              next[i] = m
              return sortMethods(next)
            })
            setSelected(m.id)
          }}
          onDeleted={(id, hard) => {
            if (hard) {
              setItems((old) => old.filter((x) => x.id !== id))
              setSelected('new')
            } else {
              void load()
            }
          }}
        />
      </div>
    </>
  )
}

function MethodEditor({
  method,
  countries,
  onSaved,
  onDeleted,
}: {
  method: Method | null
  countries: Country[]
  onSaved: (m: Method) => void
  onDeleted: (id: string, hard: boolean) => void
}) {
  const [draft, setDraft] = useState<MethodDraft>(method ? methodDraft(method) : EMPTY_METHOD)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [err, setErr] = useState('')
  const [ok, setOk] = useState('')
  const set = <K extends keyof MethodDraft>(k: K, v: MethodDraft[K]) => setDraft((d) => ({ ...d, [k]: v }))

  const save = async () => {
    const b = {
      name: draft.name.trim(),
      title: draft.title.trim(),
      account_title: draft.account_title.trim(),
      account_no: draft.account_no.trim(),
      instructions: draft.instructions.trim(),
      country_id: Number(draft.country_id) || 0,
      status: draft.status ? 1 : 0,
      sort_order: Number(draft.sort_order) || 0,
    }
    if (!b.name) return setErr('Give the account a name (e.g. JazzCash, Bank transfer).')
    if (!b.account_title) return setErr('Enter the account title the user will see.')
    if (!b.account_no) return setErr('Enter the account number / IBAN / wallet id.')
    setSaving(true)
    setErr('')
    setOk('')
    const r = method
      ? await patch<Method>(`/v1/admin/recharge/methods/${method.id}`, b)
      : await post<Method>('/v1/admin/recharge/methods', b)
    setSaving(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    setOk(r.message)
    onSaved(r.data)
  }

  const remove = async () => {
    if (!method) return
    if (!window.confirm(`Delete “${method.name}”? If it has recharge history it will be switched off instead.`)) return
    setDeleting(true)
    setErr('')
    const r = await del<{ deleted: boolean }>(`/v1/admin/recharge/methods/${method.id}`)
    setDeleting(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    onDeleted(method.id, r.data.deleted)
  }

  return (
    <Card
      title={method ? `Edit · ${method.name}` : 'New receiving account'}
      actions={
        method ? (
          <Button size="sm" variant="danger" loading={deleting} onClick={() => void remove()}>
            Delete
          </Button>
        ) : undefined
      }
    >
      {err ? <div className="err-banner">{err}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <div className="form-grid">
        <Field label="Name" value={draft.name} onChange={(e) => set('name', e.target.value)} placeholder="JazzCash, Easypaisa, Bank transfer…" maxLength={80} />
        <Field label="Title" hint="One line under the name, e.g. the bank." value={draft.title} onChange={(e) => set('title', e.target.value)} maxLength={120} />
        <Field label="Account title" hint="Name on the account, exactly as the user will see it." value={draft.account_title} onChange={(e) => set('account_title', e.target.value)} maxLength={120} />
        <Field label="Account number" hint="IBAN, wallet number, email…" value={draft.account_no} onChange={(e) => set('account_no', e.target.value)} maxLength={120} />
        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>Country</span>
          <select style={selectStyle} value={draft.country_id} onChange={(e) => set('country_id', e.target.value)}>
            <option value="0">All countries</option>
            {countries.map((c) => (
              <option key={c.id} value={String(c.id)}>
                {c.name} ({c.iso2})
              </option>
            ))}
          </select>
        </label>
        <Field label="Sort order" type="number" value={draft.sort_order} onChange={(e) => set('sort_order', e.target.value)} />
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 26, cursor: 'pointer' }}>
          <input type="checkbox" checked={draft.status} onChange={(e) => set('status', e.target.checked)} />
          <span style={{ fontSize: 13, fontWeight: 600 }}>Switched on (visible in the app)</span>
        </label>
      </div>
      <div style={{ marginTop: 12 }}>
        <TextArea
          label="Instructions"
          hint="Shown on the pay screen: reference to include, timing, what to screenshot."
          rows={4}
          value={draft.instructions}
          onChange={(e) => set('instructions', e.target.value)}
          maxLength={2000}
        />
      </div>
      <div className="form-actions">
        <Button loading={saving} onClick={() => void save()}>
          {method ? 'Save changes' : 'Create account'}
        </Button>
      </div>
    </Card>
  )
}
