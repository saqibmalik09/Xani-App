import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get, patch, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

export type SellerRow = {
  user_id: string
  username: string
  full_name: string
  avatar_url?: string
  role_id: number
  role_title: string
  whatsapp: string
  account_no: string
  active: boolean
  min_send: number
  max_send: number
  sent: number
  sent_count: number
  topped_up: number
}

type SellerTotals = {
  sent_today: number
  sent_week: number
  sent_month: number
  sent_total: number
  send_count: number
  topped_up: number
  received: number
}

type SellerDetail = {
  user: { user_id: string; username: string; full_name: string; avatar_url?: string }
  profile: {
    user_id: string
    whatsapp: string
    account_no: string
    active: boolean
    min_send: number
    max_send: number
    note: string
  }
  totals: SellerTotals
  balance: { coins: number; diamonds: number; beans: number }
}

export type TransferRow = {
  id: string
  seller_id: string
  receiver_id: string
  amount: number
  received: number
  debit_currency: string
  credit_currency: string
  fee_amount: number
  bonus_amount: number
  note: string
  status: 'pending' | 'completed' | 'disputed' | 'failed'
  status_note: string
  created_at_ms: number
  seller_username: string
  seller_name: string
  receiver_username: string
  receiver_name: string
}

type Topup = {
  id: string
  coins: number
  diamonds: number
  beans: number
  note: string
  actor: string
  created_at: string
}

type TeamRow = {
  user_id: string
  username: string
  full_name: string
  role_title: string
  depth: number
  sent: number
  sent_count: number
  received: number
}

export function fmtWhen(msOrIso?: number | string): string {
  if (!msOrIso) return '—'
  const d = typeof msOrIso === 'number' ? new Date(msOrIso) : new Date(msOrIso)
  return d.toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function TransferStatusPill({ status }: { status: TransferRow['status'] }) {
  const cls = status === 'completed' ? 'on' : status === 'failed' ? 'off' : 'warn'
  const label = status.charAt(0).toUpperCase() + status.slice(1)
  return <span className={`status-pill ${cls}`}>{label}</span>
}

export function SellerAvatar({ url, name }: { url?: string; name: string }) {
  const { mediaBase } = useAuth()
  const src = url ? mediaUrl(mediaBase, url) : ''
  return src ? (
    <img src={src} alt="" className="withdraw-av" loading="lazy" />
  ) : (
    <span className="withdraw-av withdraw-av--letter">{(name || '?').slice(0, 1).toUpperCase()}</span>
  )
}

/**
 * Coin sellers console: every member of the configured seller roles, their
 * float, their numbers, and per-seller controls (active switch, contact
 * fields, limit overrides, top-ups).
 */
export function SellersPage() {
  const [rows, setRows] = useState<SellerRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [q, setQ] = useState('')
  const [roleIDs, setRoleIDs] = useState<number[]>([])
  const [enabled, setEnabled] = useState(true)
  const [selected, setSelected] = useState<SellerRow | null>(null)
  const { can } = useAuth()
  const manage = can('sellers.manage')

  const load = useCallback(async () => {
    setLoading(true)
    const query = q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ''
    const r = await get<{ sellers: SellerRow[]; role_ids: number[]; enabled: boolean }>(`/v1/admin/sellers${query}`)
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setRows(r.data.sellers || [])
    setRoleIDs(r.data.role_ids || [])
    setEnabled(r.data.enabled)
  }, [q])

  useEffect(() => {
    void load()
  }, [load])

  if (selected) {
    return (
      <SellerDetailView
        row={selected}
        manage={manage}
        onBack={() => {
          setSelected(null)
          void load()
        }}
      />
    )
  }

  return (
    <div className="withdraw-page">
      <div className="page-head">
        <div>
          <h1>Coin sellers</h1>
          <p>
            Members of the seller roles who hold a float and recharge users. Top up, switch on or off, and set
            per-seller limits from here.
          </p>
        </div>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {!enabled ? <div className="err-banner">The seller feature is switched off in Settings → Sellers.</div> : null}
      {roleIDs.length === 0 ? (
        <div className="err-banner">
          No seller roles are configured yet. Set them in <Link to="/settings/sellers">Settings → Sellers</Link>.
        </div>
      ) : null}

      <Card>
        <div className="withdraw-toolbar">
          <Field label="" placeholder="Search by username…" value={q} onChange={(e) => setQ(e.target.value)} />
          <Button size="sm" variant="secondary" onClick={() => void load()}>
            Refresh
          </Button>
        </div>
        {loading && rows.length === 0 ? (
          <PageLoader label="Loading sellers…" />
        ) : rows.length === 0 ? (
          <p className="muted" style={{ margin: '16px 0 0' }}>
            No sellers found. Assign the seller role to accounts from the Roles screen; they appear here.
          </p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Seller</th>
                  <th>Role</th>
                  <th>WhatsApp</th>
                  <th className="num">Topped up</th>
                  <th className="num">Sent</th>
                  <th className="num">Sends</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((s) => {
                  const name = s.full_name || `@${s.username}`
                  return (
                    <tr key={s.user_id}>
                      <td>
                        <span className="withdraw-user">
                          <SellerAvatar url={s.avatar_url} name={name} />
                          <span>
                            <Link to={`/users/${s.user_id}`} className="withdraw-user-name">
                              {name}
                            </Link>
                            <div className="muted">
                              @{s.username} · #{s.user_id}
                            </div>
                          </span>
                        </span>
                      </td>
                      <td>{s.role_title}</td>
                      <td>{s.whatsapp || <span className="muted">—</span>}</td>
                      <td className="num">{s.topped_up.toLocaleString()}</td>
                      <td className="num">{s.sent.toLocaleString()}</td>
                      <td className="num">{s.sent_count.toLocaleString()}</td>
                      <td>
                        <span className={`status-pill ${s.active ? 'on' : 'off'}`}>{s.active ? 'Active' : 'Inactive'}</span>
                      </td>
                      <td className="nowrap">
                        <Button size="sm" variant="secondary" onClick={() => setSelected(s)}>
                          Open
                        </Button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}

// ─── detail ──────────────────────────────────────────────────────────────────

type DetailTab = 'topups' | 'transfers' | 'team'

function SellerDetailView({ row, manage, onBack }: { row: SellerRow; manage: boolean; onBack: () => void }) {
  const [detail, setDetail] = useState<SellerDetail | null>(null)
  const [tab, setTab] = useState<DetailTab>('topups')
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const r = await get<SellerDetail>(`/v1/admin/sellers/${row.user_id}`)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setDetail(r.data)
  }, [row.user_id])

  useEffect(() => {
    void load()
  }, [load])

  const name = row.full_name || `@${row.username}`
  const t = detail?.totals

  return (
    <div className="withdraw-page">
      <div className="page-head">
        <div>
          <h1 style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <SellerAvatar url={row.avatar_url} name={name} /> {name}
          </h1>
          <p>
            @{row.username} · #{row.user_id} · {row.role_title} ·{' '}
            <Link to={`/users/${row.user_id}`}>account</Link> · <Link to={`/users/${row.user_id}/ledger`}>ledger</Link>
          </p>
        </div>
        <Button size="sm" variant="secondary" onClick={onBack}>
          ← All sellers
        </Button>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

      <div className="withdraw-stats">
        <span>
          <em>Balance</em>{' '}
          <strong>
            🪙 {(detail?.balance.coins ?? 0).toLocaleString()} · 💎 {(detail?.balance.diamonds ?? 0).toLocaleString()} · 🫘{' '}
            {(detail?.balance.beans ?? 0).toLocaleString()}
          </strong>
        </span>
        <span>
          <em>Today</em> <strong>{(t?.sent_today ?? 0).toLocaleString()}</strong>
        </span>
        <span>
          <em>This week</em> <strong>{(t?.sent_week ?? 0).toLocaleString()}</strong>
        </span>
        <span>
          <em>This month</em> <strong>{(t?.sent_month ?? 0).toLocaleString()}</strong>
        </span>
        <span>
          <em>All time</em> <strong>{(t?.sent_total ?? 0).toLocaleString()}</strong> ({(t?.send_count ?? 0).toLocaleString()} sends)
        </span>
        <span>
          <em>Topped up</em> <strong>{(t?.topped_up ?? 0).toLocaleString()}</strong>
        </span>
      </div>

      <div className="catalog-split">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {manage && detail ? <ProfileEditor detail={detail} onSaved={() => void load()} /> : null}
          {manage ? <TopupForm sellerID={row.user_id} onDone={() => void load()} /> : null}
        </div>
        <Card>
          <div className="tabs">
            {(['topups', 'transfers', 'team'] as DetailTab[]).map((k) => (
              <button key={k} type="button" className={`tab${tab === k ? ' is-active' : ''}`} onClick={() => setTab(k)}>
                {k === 'topups' ? 'Top-ups' : k === 'transfers' ? 'Sends' : 'Team'}
              </button>
            ))}
          </div>
          {tab === 'topups' ? <TopupsTab sellerID={row.user_id} /> : null}
          {tab === 'transfers' ? <TransfersTab sellerID={row.user_id} /> : null}
          {tab === 'team' ? <TeamTab sellerID={row.user_id} /> : null}
        </Card>
      </div>
    </div>
  )
}

function ProfileEditor({ detail, onSaved }: { detail: SellerDetail; onSaved: () => void }) {
  const p = detail.profile
  const [whatsapp, setWhatsapp] = useState(p.whatsapp)
  const [accountNo, setAccountNo] = useState(p.account_no)
  const [active, setActive] = useState(p.active)
  const [minSend, setMinSend] = useState(String(p.min_send || 0))
  const [maxSend, setMaxSend] = useState(String(p.max_send || 0))
  const [note, setNote] = useState(p.note)
  const [saving, setSaving] = useState(false)
  const [err, setErr] = useState('')
  const [ok, setOk] = useState('')

  const save = async () => {
    setSaving(true)
    setErr('')
    setOk('')
    const r = await patch(`/v1/admin/sellers/${p.user_id || detail.user.user_id}`, {
      whatsapp: whatsapp.trim(),
      account_no: accountNo.trim(),
      active,
      min_send: Math.max(0, Math.floor(Number(minSend) || 0)),
      max_send: Math.max(0, Math.floor(Number(maxSend) || 0)),
      note: note.trim(),
    })
    setSaving(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    setOk('Saved.')
    onSaved()
  }

  return (
    <Card title="Seller profile">
      {err ? <div className="err-banner">{err}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <div className="form-grid">
        <Field label="WhatsApp (optional)" value={whatsapp} onChange={(e) => setWhatsapp(e.target.value)} maxLength={40} placeholder="+92 300 1234567" />
        <Field label="Account no. (optional)" value={accountNo} onChange={(e) => setAccountNo(e.target.value)} maxLength={120} />
        <Field
          label="Min per send (0 = global)"
          type="number"
          min={0}
          value={minSend}
          onChange={(e) => setMinSend(e.target.value)}
        />
        <Field
          label="Max per send (0 = global)"
          type="number"
          min={0}
          value={maxSend}
          onChange={(e) => setMaxSend(e.target.value)}
        />
        <Field label="Internal note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={300} />
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 26, cursor: 'pointer' }}>
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          <span style={{ fontSize: 13, fontWeight: 600 }}>Active (can sell and appears in the app list)</span>
        </label>
      </div>
      <div className="form-actions">
        <Button loading={saving} onClick={() => void save()}>
          Save profile
        </Button>
      </div>
    </Card>
  )
}

function TopupForm({ sellerID, onDone }: { sellerID: string; onDone: () => void }) {
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const [ok, setOk] = useState('')

  const send = async () => {
    const n = Math.floor(Number(amount) || 0)
    if (n === 0) {
      setErr('Enter a non-zero amount. Use a negative amount to take balance back.')
      return
    }
    setBusy(true)
    setErr('')
    setOk('')
    const r = await post<{ applied: boolean; balance: { coins: number; diamonds: number; beans: number } }>(
      `/v1/admin/sellers/${sellerID}/topup`,
      {
        amount: n,
        currency: currency || undefined,
        note: note.trim(),
        client_id: `t${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      },
    )
    setBusy(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    setOk(
      `Done. Balance now 🪙 ${r.data.balance.coins.toLocaleString()} · 💎 ${r.data.balance.diamonds.toLocaleString()} · 🫘 ${r.data.balance.beans.toLocaleString()}`,
    )
    setAmount('')
    setNote('')
    onDone()
  }

  return (
    <Card title="Top up balance">
      {err ? <div className="err-banner">{err}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <div className="form-grid">
        <Field
          label="Amount"
          hint="Negative takes balance back."
          type="number"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>Currency</span>
          <select
            style={{ height: 36, borderRadius: 8, border: '1px solid var(--line)', padding: '0 10px', background: 'var(--surface)', fontSize: 13 }}
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
          >
            <option value="">Default (settings)</option>
            <option value="coins">Coins</option>
            <option value="diamonds">Diamonds</option>
            <option value="beans">Beans</option>
          </select>
        </label>
        <Field label="Note (shown in the ledger)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={200} />
      </div>
      <div className="form-actions">
        <Button loading={busy} onClick={() => void send()}>
          Add to balance
        </Button>
      </div>
    </Card>
  )
}

function TopupsTab({ sellerID }: { sellerID: string }) {
  const [rows, setRows] = useState<Topup[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let live = true
    setLoading(true)
    void get<{ items: Topup[] }>(`/v1/admin/sellers/${sellerID}/topups`).then((r) => {
      if (!live) return
      setLoading(false)
      if (r.ok) setRows(r.data.items || [])
    })
    return () => {
      live = false
    }
  }, [sellerID])

  if (loading) return <PageLoader label="Loading top-ups…" />
  if (rows.length === 0) return <p className="muted">No top-ups yet.</p>
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>When</th>
            <th className="num">Amount</th>
            <th>By</th>
            <th>Note</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((t) => {
            const amount = t.coins || t.diamonds || t.beans
            const cur = t.coins ? '🪙' : t.diamonds ? '💎' : '🫘'
            return (
              <tr key={t.id}>
                <td className="nowrap muted">{fmtWhen(t.created_at)}</td>
                <td className="num">
                  <strong style={{ color: amount < 0 ? 'var(--danger, #c00)' : undefined }}>
                    {cur} {amount.toLocaleString()}
                  </strong>
                </td>
                <td>{t.actor ? `@${t.actor}` : '—'}</td>
                <td className="muted">{t.note || '—'}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export function TransfersTable({ rows, showSeller }: { rows: TransferRow[]; showSeller: boolean }) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            {showSeller ? <th>Seller</th> : null}
            <th>Receiver</th>
            <th className="num">Amount</th>
            <th className="num">Fee</th>
            <th className="num">Received</th>
            <th>When</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((t) => (
            <tr key={t.id}>
              {showSeller ? (
                <td>
                  <Link to={`/users/${t.seller_id}`}>{t.seller_name || `@${t.seller_username}`}</Link>
                  <div className="muted">@{t.seller_username}</div>
                </td>
              ) : null}
              <td>
                <Link to={`/users/${t.receiver_id}`}>{t.receiver_name || `@${t.receiver_username}`}</Link>
                <div className="muted">
                  @{t.receiver_username} · #{t.receiver_id}
                </div>
              </td>
              <td className="num">
                <strong>{t.amount.toLocaleString()}</strong> {t.debit_currency}
              </td>
              <td className="num">{t.fee_amount ? t.fee_amount.toLocaleString() : '—'}</td>
              <td className="num">
                {t.received.toLocaleString()} {t.credit_currency}
              </td>
              <td className="nowrap muted">{fmtWhen(t.created_at_ms)}</td>
              <td>
                <TransferStatusPill status={t.status} />
                {t.status_note ? <div className="muted withdraw-note">{t.status_note}</div> : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function TransfersTab({ sellerID }: { sellerID: string }) {
  const [rows, setRows] = useState<TransferRow[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let live = true
    setLoading(true)
    void get<{ items: TransferRow[] }>(`/v1/admin/sellers/${sellerID}/transfers`).then((r) => {
      if (!live) return
      setLoading(false)
      if (r.ok) setRows(r.data.items || [])
    })
    return () => {
      live = false
    }
  }, [sellerID])

  if (loading) return <PageLoader label="Loading sends…" />
  if (rows.length === 0) return <p className="muted">No sends yet.</p>
  return <TransfersTable rows={rows} showSeller={false} />
}

function TeamTab({ sellerID }: { sellerID: string }) {
  const [rows, setRows] = useState<TeamRow[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let live = true
    setLoading(true)
    void get<{ team: TeamRow[] }>(`/v1/admin/sellers/${sellerID}/team`).then((r) => {
      if (!live) return
      setLoading(false)
      if (r.ok) setRows(r.data.team || [])
    })
    return () => {
      live = false
    }
  }, [sellerID])

  if (loading) return <PageLoader label="Loading team…" />
  if (rows.length === 0) return <p className="muted">No downline members.</p>
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>Member</th>
            <th>Role</th>
            <th className="num">Level</th>
            <th className="num">Sent</th>
            <th className="num">Sends</th>
            <th className="num">Received</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((m) => (
            <tr key={m.user_id}>
              <td>
                <Link to={`/users/${m.user_id}`}>{m.full_name || `@${m.username}`}</Link>
                <div className="muted">@{m.username}</div>
              </td>
              <td>{m.role_title}</td>
              <td className="num">{m.depth}</td>
              <td className="num">{m.sent.toLocaleString()}</td>
              <td className="num">{m.sent_count.toLocaleString()}</td>
              <td className="num">{m.received.toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
