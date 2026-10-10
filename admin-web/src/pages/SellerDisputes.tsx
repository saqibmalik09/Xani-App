import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get, post } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { fmtWhen, SellerAvatar, TransferStatusPill, type TransferRow } from './Sellers'

type DisputeStatus = 'open' | 'resolved' | 'dismissed'

type DisputeRow = {
  id: string
  transfer_id?: string
  seller_id: string
  user_id: string
  reason: string
  status: DisputeStatus
  resolution: string
  created_at_ms: number
  resolved_at_ms?: number
  username: string
  full_name: string
  avatar_url?: string
  seller_username: string
  seller_name: string
}

type DisputeMessage = {
  id: string
  sender_id: string
  sender: string
  system: boolean
  body: string
  created_at_ms: number
}

type DisputeDetail = {
  dispute: DisputeRow
  messages: DisputeMessage[]
  transfer?: TransferRow
}

const TABS: { key: DisputeStatus | ''; label: string }[] = [
  { key: 'open', label: 'Open' },
  { key: 'resolved', label: 'Resolved' },
  { key: 'dismissed', label: 'Dismissed' },
  { key: '', label: 'All' },
]

function DisputePill({ status }: { status: DisputeStatus }) {
  const cls = status === 'resolved' ? 'on' : status === 'dismissed' ? 'off' : 'warn'
  return <span className={`status-pill ${cls}`}>{status.charAt(0).toUpperCase() + status.slice(1)}</span>
}

/**
 * Disputes users raise against sellers: the list, each dispute's chat thread
 * (the console can reply as itself or as the system), and resolution — which
 * can also set the disputed transaction's final status.
 */
export function SellerDisputesPage() {
  const [status, setStatus] = useState<DisputeStatus | ''>('open')
  const [rows, setRows] = useState<DisputeRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [openID, setOpenID] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    const q = new URLSearchParams({ limit: '50' })
    if (status) q.set('status', status)
    const r = await get<{ items: DisputeRow[] }>(`/v1/admin/sellers/disputes?${q}`)
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setRows(r.data.items || [])
  }, [status])

  useEffect(() => {
    void load()
  }, [load])

  if (openID) {
    return (
      <DisputeDetailView
        id={openID}
        onBack={() => {
          setOpenID('')
          void load()
        }}
      />
    )
  }

  return (
    <div className="withdraw-page">
      <div className="page-head">
        <div>
          <h1>Seller disputes</h1>
          <p>Complaints users filed against sellers. Open one to read the thread, reply, and resolve.</p>
        </div>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

      <Card>
        <div className="withdraw-toolbar">
          <div className="tabs" style={{ marginBottom: 0 }}>
            {TABS.map((t) => (
              <button
                key={t.key || 'all'}
                type="button"
                className={`tab${status === t.key ? ' is-active' : ''}`}
                onClick={() => setStatus(t.key)}
              >
                {t.label}
              </button>
            ))}
          </div>
          <Button size="sm" variant="secondary" onClick={() => void load()}>
            Refresh
          </Button>
        </div>

        {loading && rows.length === 0 ? (
          <PageLoader label="Loading disputes…" />
        ) : rows.length === 0 ? (
          <p className="muted" style={{ margin: '16px 0 0' }}>
            No {status || ''} disputes.
          </p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Filed by</th>
                  <th>Against seller</th>
                  <th>Reason</th>
                  <th>Opened</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((d) => (
                  <tr key={d.id}>
                    <td>
                      <span className="withdraw-user">
                        <SellerAvatar url={d.avatar_url} name={d.full_name || d.username} />
                        <span>
                          <Link to={`/users/${d.user_id}`} className="withdraw-user-name">
                            {d.full_name || `@${d.username}`}
                          </Link>
                          <div className="muted">@{d.username}</div>
                        </span>
                      </span>
                    </td>
                    <td>
                      <Link to={`/users/${d.seller_id}`}>{d.seller_name || `@${d.seller_username}`}</Link>
                      <div className="muted">@{d.seller_username}</div>
                    </td>
                    <td style={{ maxWidth: 320 }}>
                      <span className="muted">{d.reason.length > 120 ? `${d.reason.slice(0, 120)}…` : d.reason}</span>
                    </td>
                    <td className="nowrap muted">{fmtWhen(d.created_at_ms)}</td>
                    <td>
                      <DisputePill status={d.status} />
                    </td>
                    <td className="nowrap">
                      <Button size="sm" variant="secondary" onClick={() => setOpenID(d.id)}>
                        Open
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}

function DisputeDetailView({ id, onBack }: { id: string; onBack: () => void }) {
  const [detail, setDetail] = useState<DisputeDetail | null>(null)
  const [error, setError] = useState('')
  const { can } = useAuth()
  const manage = can('sellers.manage')

  const load = useCallback(async () => {
    const r = await get<DisputeDetail>(`/v1/seller-disputes/${id}`)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setDetail(r.data)
  }, [id])

  useEffect(() => {
    void load()
  }, [load])

  if (!detail) {
    return (
      <div className="withdraw-page">
        {error ? <div className="err-banner">{error}</div> : <PageLoader label="Loading dispute…" />}
        <Button size="sm" variant="secondary" onClick={onBack}>
          ← All disputes
        </Button>
      </div>
    )
  }

  const d = detail.dispute
  return (
    <div className="withdraw-page">
      <div className="page-head">
        <div>
          <h1>
            Dispute #{d.id} <DisputePill status={d.status} />
          </h1>
          <p>
            <Link to={`/users/${d.user_id}`}>{d.full_name || `@${d.username}`}</Link> against{' '}
            <Link to={`/users/${d.seller_id}`}>{d.seller_name || `@${d.seller_username}`}</Link> · opened{' '}
            {fmtWhen(d.created_at_ms)}
          </p>
        </div>
        <Button size="sm" variant="secondary" onClick={onBack}>
          ← All disputes
        </Button>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

      {detail.transfer ? (
        <Card title="Disputed transaction">
          <p style={{ margin: 0 }}>
            <strong>{detail.transfer.amount.toLocaleString()}</strong> {detail.transfer.debit_currency} →{' '}
            <strong>{detail.transfer.received.toLocaleString()}</strong> {detail.transfer.credit_currency} to{' '}
            @{detail.transfer.receiver_username} · {fmtWhen(detail.transfer.created_at_ms)} ·{' '}
            <TransferStatusPill status={detail.transfer.status} />
          </p>
        </Card>
      ) : null}

      <div className="catalog-split">
        <Card title="Conversation">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, maxHeight: 480, overflowY: 'auto' }}>
            {detail.messages.map((m) => (
              <div
                key={m.id}
                style={{
                  padding: '8px 12px',
                  borderRadius: 10,
                  background: m.system ? 'var(--surface-2, #f4f4f6)' : 'var(--surface)',
                  border: '1px solid var(--line)',
                }}
              >
                <div className="muted" style={{ fontSize: 12, marginBottom: 2 }}>
                  {m.system ? 'System' : m.sender_id === d.user_id ? `@${m.sender} (filer)` : m.sender_id === d.seller_id ? `@${m.sender} (seller)` : `@${m.sender}`} ·{' '}
                  {fmtWhen(m.created_at_ms)}
                </div>
                <div style={{ fontSize: 14, whiteSpace: 'pre-wrap' }}>{m.body}</div>
              </div>
            ))}
            {detail.messages.length === 0 ? <p className="muted">No messages yet.</p> : null}
          </div>
          {d.status === 'open' ? <ReplyBox id={d.id} manage={manage} onSent={() => void load()} /> : null}
        </Card>

        {manage && d.status === 'open' ? <ResolveBox id={d.id} hasTransfer={Boolean(detail.transfer)} onDone={() => void load()} /> : null}
        {d.status !== 'open' && d.resolution ? (
          <Card title="Resolution">
            <p style={{ margin: 0 }}>{d.resolution}</p>
            <p className="muted" style={{ margin: '8px 0 0', fontSize: 13 }}>
              Closed {fmtWhen(d.resolved_at_ms)}
            </p>
          </Card>
        ) : null}
      </div>
    </div>
  )
}

function ReplyBox({ id, manage, onSent }: { id: string; manage: boolean; onSent: () => void }) {
  const [body, setBody] = useState('')
  const [asSystem, setAsSystem] = useState(false)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')

  const send = async () => {
    if (!body.trim()) return
    setBusy(true)
    setErr('')
    const r = await post(`/v1/seller-disputes/${id}/messages`, { body: body.trim(), as_system: asSystem })
    setBusy(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    setBody('')
    onSent()
  }

  return (
    <div style={{ marginTop: 12 }}>
      {err ? <div className="err-banner" style={{ marginBottom: 8 }}>{err}</div> : null}
      <TextArea label="Reply" rows={3} value={body} onChange={(e) => setBody(e.target.value)} maxLength={1000} />
      <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginTop: 8 }}>
        {manage ? (
          <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer', fontSize: 13 }}>
            <input type="checkbox" checked={asSystem} onChange={(e) => setAsSystem(e.target.checked)} />
            Send as system
          </label>
        ) : null}
        <Button size="sm" loading={busy} onClick={() => void send()}>
          Send
        </Button>
      </div>
    </div>
  )
}

function ResolveBox({ id, hasTransfer, onDone }: { id: string; hasTransfer: boolean; onDone: () => void }) {
  const [status, setStatus] = useState<'resolved' | 'dismissed'>('resolved')
  const [transferStatus, setTransferStatus] = useState('')
  const [resolution, setResolution] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')

  const resolve = async () => {
    if (!resolution.trim()) {
      setErr('Write a short resolution note — both parties see it.')
      return
    }
    setBusy(true)
    setErr('')
    const r = await post(`/v1/admin/sellers/disputes/${id}/resolve`, {
      status,
      resolution: resolution.trim(),
      transfer_status: transferStatus || undefined,
    })
    setBusy(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    onDone()
  }

  const selectStyle = {
    height: 36,
    borderRadius: 8,
    border: '1px solid var(--line)',
    padding: '0 10px',
    background: 'var(--surface)',
    fontSize: 13,
  } as const

  return (
    <Card title="Resolve">
      {err ? <div className="err-banner">{err}</div> : null}
      <div className="form-grid">
        <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>Outcome</span>
          <select style={selectStyle} value={status} onChange={(e) => setStatus(e.target.value as 'resolved' | 'dismissed')}>
            <option value="resolved">Resolved (in the user's favour or fixed)</option>
            <option value="dismissed">Dismissed (no fault found)</option>
          </select>
        </label>
        {hasTransfer ? (
          <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>Transaction becomes</span>
            <select style={selectStyle} value={transferStatus} onChange={(e) => setTransferStatus(e.target.value)}>
              <option value="">Leave as is</option>
              <option value="completed">Completed</option>
              <option value="failed">Failed</option>
            </select>
          </label>
        ) : null}
        <Field
          label="Resolution note (sent to both parties)"
          value={resolution}
          onChange={(e) => setResolution(e.target.value)}
          maxLength={1000}
        />
      </div>
      <div className="form-actions">
        <Button loading={busy} onClick={() => void resolve()}>
          Close dispute
        </Button>
      </div>
    </Card>
  )
}
