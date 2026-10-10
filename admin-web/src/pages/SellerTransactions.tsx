import { useCallback, useEffect, useRef, useState } from 'react'
import { get, post } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { fmtWhen, TransferStatusPill, type TransferRow } from './Sellers'
import { Link } from 'react-router-dom'

const PAGE = 50

type StatusFilter = '' | 'pending' | 'completed' | 'disputed' | 'failed'

const STATUS_TABS: { key: StatusFilter; label: string }[] = [
  { key: '', label: 'All' },
  { key: 'pending', label: 'Pending' },
  { key: 'completed', label: 'Completed' },
  { key: 'disputed', label: 'Disputed' },
  { key: 'failed', label: 'Failed' },
]

/**
 * Every seller → user send, newest first, with date and status filters.
 * Managers can move a transaction between pending / completed / disputed /
 * failed; the seller is notified of the change.
 */
export function SellerTransactionsPage() {
  const [rows, setRows] = useState<TransferRow[]>([])
  const [cursor, setCursor] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState<StatusFilter>('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [seller, setSeller] = useState('')
  const inflight = useRef<AbortController | null>(null)
  const { can } = useAuth()
  const manage = can('sellers.manage')

  const load = useCallback(
    async (before: string, append: boolean) => {
      inflight.current?.abort()
      const ctl = new AbortController()
      inflight.current = ctl
      if (append) setMore(true)
      else setLoading(true)
      const q = new URLSearchParams({ limit: String(PAGE) })
      if (status) q.set('status', status)
      if (from) q.set('from', from)
      if (to) q.set('to', to)
      if (seller.trim()) q.set('seller', seller.trim())
      if (before) q.set('before', before)
      const r = await get<{ items: TransferRow[]; next_cursor: string }>(`/v1/admin/sellers/transfers?${q}`, {
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
    [status, from, to, seller],
  )

  useEffect(() => {
    void load('', false)
    return () => inflight.current?.abort()
  }, [load])

  const replace = (row: TransferRow) => setRows((old) => old.map((r) => (r.id === row.id ? row : r)))

  return (
    <div className="withdraw-page">
      <div className="page-head">
        <div>
          <h1>Seller transactions</h1>
          <p>Every send from a seller to a user. Change a transaction's status when something needs correcting.</p>
        </div>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

      <Card>
        <div className="withdraw-toolbar" style={{ flexWrap: 'wrap', gap: 8 }}>
          <div className="tabs" style={{ marginBottom: 0 }}>
            {STATUS_TABS.map((t) => (
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
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <Field label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            <Field label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            <Field label="Seller id" placeholder="#id" value={seller} onChange={(e) => setSeller(e.target.value)} />
            <Button size="sm" variant="secondary" onClick={() => void load('', false)}>
              Refresh
            </Button>
          </div>
        </div>

        {loading && rows.length === 0 ? (
          <PageLoader label="Loading transactions…" />
        ) : rows.length === 0 ? (
          <p className="muted" style={{ margin: '16px 0 0' }}>
            No transactions match.
          </p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Seller</th>
                  <th>Receiver</th>
                  <th className="num">Amount</th>
                  <th className="num">Fee</th>
                  <th className="num">Received</th>
                  <th>When</th>
                  <th>Status</th>
                  {manage ? <th></th> : null}
                </tr>
              </thead>
              <tbody>
                {rows.map((t) => (
                  <TransactionRow key={t.id} row={t} manage={manage} onChanged={replace} />
                ))}
              </tbody>
            </table>
          </div>
        )}

        {cursor ? (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 12 }}>
            <Button size="sm" variant="ghost" loading={more} onClick={() => void load(cursor, true)}>
              Load more
            </Button>
          </div>
        ) : null}
      </Card>
    </div>
  )
}

function TransactionRow({
  row,
  manage,
  onChanged,
}: {
  row: TransferRow
  manage: boolean
  onChanged: (row: TransferRow) => void
}) {
  const [open, setOpen] = useState(false)
  const [next, setNext] = useState(row.status)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')

  const save = async () => {
    setBusy(true)
    setErr('')
    const r = await post<TransferRow>(`/v1/admin/sellers/transfers/${row.id}/status`, {
      status: next,
      note: note.trim(),
    })
    setBusy(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    setOpen(false)
    onChanged(r.data)
  }

  const cols = manage ? 8 : 7

  return (
    <>
      <tr>
        <td>
          <Link to={`/users/${row.seller_id}`}>{row.seller_name || `@${row.seller_username}`}</Link>
          <div className="muted">@{row.seller_username}</div>
        </td>
        <td>
          <Link to={`/users/${row.receiver_id}`}>{row.receiver_name || `@${row.receiver_username}`}</Link>
          <div className="muted">
            @{row.receiver_username} · #{row.receiver_id}
          </div>
        </td>
        <td className="num">
          <strong>{row.amount.toLocaleString()}</strong> {row.debit_currency}
        </td>
        <td className="num">{row.fee_amount ? row.fee_amount.toLocaleString() : '—'}</td>
        <td className="num">
          {row.received.toLocaleString()} {row.credit_currency}
        </td>
        <td className="nowrap muted">{fmtWhen(row.created_at_ms)}</td>
        <td>
          <TransferStatusPill status={row.status} />
          {row.status_note ? <div className="muted withdraw-note">{row.status_note}</div> : null}
        </td>
        {manage ? (
          <td className="nowrap">
            <Button size="sm" variant="secondary" onClick={() => setOpen((o) => !o)}>
              {open ? 'Cancel' : 'Set status'}
            </Button>
          </td>
        ) : null}
      </tr>
      {open ? (
        <tr className="withdraw-expand">
          <td colSpan={cols}>
            {err ? <div className="err-banner" style={{ marginBottom: 8 }}>{err}</div> : null}
            <div className="withdraw-reject">
              <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>New status</span>
                <select
                  style={{ height: 36, borderRadius: 8, border: '1px solid var(--line)', padding: '0 10px', background: 'var(--surface)', fontSize: 13 }}
                  value={next}
                  onChange={(e) => setNext(e.target.value as TransferRow['status'])}
                >
                  <option value="pending">Pending</option>
                  <option value="completed">Completed</option>
                  <option value="disputed">Disputed</option>
                  <option value="failed">Failed</option>
                </select>
              </label>
              <Field
                label="Note (shown with the status)"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                maxLength={300}
              />
              <Button size="sm" loading={busy} onClick={() => void save()}>
                Save status
              </Button>
            </div>
          </td>
        </tr>
      ) : null}
    </>
  )
}
