import { useCallback, useEffect, useMemo, useState, type CSSProperties, type FormEvent } from 'react'
import { del, get, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { PageLoader } from '../ui/Loader'

type FrameLogRow = {
  id: string
  user_id: string
  item_id: string
  kind: string
  source: string
  price_paid: number
  days: number
  active: boolean
  expired: boolean
  created_at: string
  expires_at?: string
  item_title: string
  preview_key: string
  username?: string
  full_name?: string
  category_id?: string
  category_title?: string
}

type StoreCat = {
  id: string
  parent_id?: string | null
  title: string
  sort_order: number
}

type ListResp = { items: FrameLogRow[]; next_cursor?: string }

const KINDS: Array<[string, string]> = [
  ['', 'All types'],
  ['frame', 'Frame'],
  ['badge', 'Badge'],
  ['entrance', 'Entrance'],
  ['vip', 'VIP'],
  ['bubble', 'Bubble'],
]

const selectStyle: CSSProperties = {
  height: 36,
  minWidth: 160,
  borderRadius: 8,
  border: '1px solid var(--line)',
  padding: '0 10px',
  background: 'var(--surface)',
  color: 'var(--ink)',
  fontSize: 13,
}

const dayLabel = (d: number) =>
  d === 0 ? 'Permanent' : d === 1 ? '1 day' : d === 7 ? '1 week' : d === 30 ? '1 month' : d === 90 ? '3 months' : `${d} days`

const kindLabel = (k: string) => KINDS.find(([v]) => v === k)?.[1] || k || 'Frame'

const sourceLabel = (s: string) => {
  if (s === 'admin') return 'Admin'
  if (s === 'signup') return 'Signup'
  if (s === 'role') return 'Role'
  return 'Purchase'
}

function flattenCats(flat: StoreCat[]): StoreCat[] {
  const map = new Map<string, StoreCat & { children: StoreCat[] }>()
  for (const c of flat) map.set(c.id, { ...c, children: [] })
  const roots: Array<StoreCat & { children: StoreCat[] }> = []
  for (const c of flat) {
    const node = map.get(c.id)!
    if (c.parent_id && map.has(c.parent_id)) map.get(c.parent_id)!.children.push(node)
    else roots.push(node)
  }
  const sortRec = (nodes: Array<StoreCat & { children: StoreCat[] }>) => {
    nodes.sort((a, b) => a.sort_order - b.sort_order || a.title.localeCompare(b.title))
    nodes.forEach((n) => sortRec(n.children as Array<StoreCat & { children: StoreCat[] }>))
  }
  sortRec(roots)
  const out: StoreCat[] = []
  const walk = (nodes: Array<StoreCat & { children: StoreCat[] }>, depth: number) => {
    for (const n of nodes) {
      out.push({ ...n, title: `${'— '.repeat(depth)}${n.title}` })
      walk(n.children as Array<StoreCat & { children: StoreCat[] }>, depth + 1)
    }
  }
  walk(roots, 0)
  return out
}

export function FramePurchasesPage() {
  const { mediaBase } = useAuth()
  const [rows, setRows] = useState<FrameLogRow[]>([])
  const [cats, setCats] = useState<StoreCat[]>([])
  const [next, setNext] = useState('')
  const [q, setQ] = useState('')
  const [term, setTerm] = useState('')
  const [kind, setKind] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const [expireRow, setExpireRow] = useState<FrameLogRow | null>(null)
  const [revokeRow, setRevokeRow] = useState<FrameLogRow | null>(null)

  const catOptions = useMemo(() => flattenCats(cats), [cats])

  const load = useCallback(
    async (before: string, append: boolean) => {
      append ? setMore(true) : setLoading(true)
      const params = new URLSearchParams({ limit: '50' })
      if (term) params.set('q', term)
      if (kind) params.set('kind', kind)
      if (categoryId) params.set('category_id', categoryId)
      if (before) params.set('before_id', before)
      const r = await get<ListResp>(`/v1/admin/frame-purchases?${params}`)
      append ? setMore(false) : setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setRows((old) => (append ? [...old, ...(r.data.items || [])] : r.data.items || []))
      setNext(r.data.next_cursor || '')
    },
    [term, kind, categoryId],
  )

  useEffect(() => {
    void get<{ items: StoreCat[] }>('/v1/admin/store-categories').then((r) => {
      if (r.ok) setCats(r.data.items || [])
    })
  }, [])

  useEffect(() => {
    void load('', false)
  }, [load])

  const search = (e: FormEvent) => {
    e.preventDefault()
    setTerm(q.trim())
  }

  const expireNow = async (row: FrameLogRow) => {
    setBusy(true)
    setError('')
    const r = await post<FrameLogRow>(`/v1/admin/frame-purchases/${row.id}/expire`, {})
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setExpireRow(null)
    setOk('Item expired.')
    await load('', false)
  }

  const revoke = async (row: FrameLogRow) => {
    setBusy(true)
    setError('')
    const r = await del(`/v1/admin/frame-purchases/${row.id}`)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setRevokeRow(null)
    setOk('Purchase revoked.')
    await load('', false)
  }

  const stateOf = (r: FrameLogRow) => (r.expired ? 'Expired' : r.active ? 'Active' : 'Owned')

  if (loading && rows.length === 0) return <PageLoader label="Loading store purchases…" />

  return (
    <div className="rates-page">
      <div className="rates-toolbar">
        <div className="page-head" style={{ marginBottom: 0 }}>
          <h1>Store purchases</h1>
          <p>Every bag item bought or granted — filter by category, expire or revoke a row.</p>
        </div>
        <form className="rates-toolbar-right" onSubmit={search}>
          <select
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
            style={selectStyle}
            aria-label="Category"
          >
            <option value="">All categories</option>
            {catOptions.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
          <select value={kind} onChange={(e) => setKind(e.target.value)} style={selectStyle} aria-label="Type">
            {KINDS.map(([v, label]) => (
              <option key={v || 'all'} value={v}>
                {label}
              </option>
            ))}
          </select>
          <input
            className="rates-search"
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search user…"
            aria-label="Search user"
          />
          <Button type="submit" size="sm">
            Search
          </Button>
        </form>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      {rows.length === 0 ? (
        <div className="table-wrap">
          <p className="muted" style={{ margin: 16 }}>
            No store purchases in this filter.
          </p>
        </div>
      ) : (
        <div className="table-wrap rates-table-wrap">
          <table className="data-table rates-table">
            <thead>
              <tr>
                <th>User</th>
                <th>Item</th>
                <th>Category</th>
                <th>Type</th>
                <th>Duration</th>
                <th className="num">Paid</th>
                <th>Source</th>
                <th>State</th>
                <th>Bought</th>
                <th>Expires</th>
                <th className="num"> </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>
                    <strong>@{r.username || r.user_id}</strong>
                    {r.full_name ? <span className="muted"> {r.full_name}</span> : null}
                  </td>
                  <td>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                      {r.preview_key ? (
                        <img
                          src={mediaUrl(mediaBase, r.preview_key)}
                          alt=""
                          style={{ width: 28, height: 28, borderRadius: '50%', objectFit: 'cover' }}
                          loading="lazy"
                        />
                      ) : null}
                      {r.item_title}
                    </span>
                  </td>
                  <td>{r.category_title || '—'}</td>
                  <td>{kindLabel(r.kind)}</td>
                  <td className="nowrap">{dayLabel(r.days)}</td>
                  <td className="num">{r.price_paid > 0 ? r.price_paid.toLocaleString() : 'Free'}</td>
                  <td>{sourceLabel(r.source)}</td>
                  <td>
                    <span className={`status-pill ${r.expired ? 'off' : 'on'}`}>{stateOf(r)}</span>
                  </td>
                  <td className="muted nowrap">{new Date(r.created_at).toLocaleString()}</td>
                  <td className="muted nowrap">{r.expires_at ? new Date(r.expires_at).toLocaleString() : 'Keep'}</td>
                  <td className="nowrap num">
                    <Button size="sm" variant="secondary" disabled={r.expired} onClick={() => setExpireRow(r)}>
                      Expire
                    </Button>
                    <Button size="sm" variant="danger" onClick={() => setRevokeRow(r)}>
                      Revoke
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {next ? (
        <div style={{ display: 'flex', justifyContent: 'center' }}>
          <Button size="sm" variant="ghost" loading={more} onClick={() => void load(next, true)}>
            Load more
          </Button>
        </div>
      ) : null}

      {expireRow ? (
        <div className="admin-modal-backdrop" role="presentation" onClick={() => setExpireRow(null)}>
          <div className="admin-modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h2>Expire now</h2>
            <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
              End @{expireRow.username || expireRow.user_id}’s {expireRow.item_title} immediately? It stays on this list as expired.
            </p>
            <div className="admin-modal-actions">
              <Button size="sm" variant="ghost" onClick={() => setExpireRow(null)}>
                Cancel
              </Button>
              <Button size="sm" loading={busy} onClick={() => void expireNow(expireRow)}>
                Expire
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {revokeRow ? (
        <div className="admin-modal-backdrop" role="presentation" onClick={() => setRevokeRow(null)}>
          <div className="admin-modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h2>Revoke</h2>
            <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
              Remove {revokeRow.item_title} from @{revokeRow.username || revokeRow.user_id}? They lose the item. Coins already paid stay paid.
            </p>
            <div className="admin-modal-actions">
              <Button size="sm" variant="ghost" onClick={() => setRevokeRow(null)}>
                Cancel
              </Button>
              <Button size="sm" variant="danger" loading={busy} onClick={() => void revoke(revokeRow)}>
                Revoke
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
