import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { del, get, patch, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type Article = {
  id: string
  title: string
  body: string
  status: number
  sort_order: number
  like_count: number
  dislike_count: number
  updated_at: string
}

type Ticket = {
  id: string
  user_id: string
  subject: string
  body: string
  status: string
  attachment_keys: string[]
  created_at: string
  updated_at: string
  username?: string
  full_name?: string
  reply_count?: number
}

type Reply = {
  id: string
  body: string
  is_staff: boolean
  created_at: string
  username?: string
  full_name?: string
}

type ListResp<T> = { items: T[] }
type TicketDetail = { ticket: Ticket; replies: Reply[] }

const selectStyle: React.CSSProperties = {
  height: 36,
  borderRadius: 8,
  border: '1px solid var(--line)',
  padding: '0 10px',
  background: 'var(--surface)',
  fontSize: 13,
}

function fmt(iso: string) {
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}

export function HelpCenterPage() {
  const [tab, setTab] = useState<'tickets' | 'articles'>('tickets')
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head">
        <h1>Help Center</h1>
        <p>Support tickets from Report a problem, and Help articles users can browse and like.</p>
      </div>
      <div className="filters filters-compact" style={{ gap: 8 }}>
        <Button size="sm" variant={tab === 'tickets' ? 'primary' : 'ghost'} onClick={() => setTab('tickets')}>
          Reported problems
        </Button>
        <Button size="sm" variant={tab === 'articles' ? 'primary' : 'ghost'} onClick={() => setTab('articles')}>
          Articles
        </Button>
      </div>
      {tab === 'tickets' ? <TicketsPane /> : <ArticlesPane />}
    </div>
  )
}

function TicketsPane() {
  const { mediaBase } = useAuth()
  const [items, setItems] = useState<Ticket[]>([])
  const [status, setStatus] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<string | null>(null)
  const [detail, setDetail] = useState<TicketDetail | null>(null)
  const [reply, setReply] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    const q = status ? `?status=${encodeURIComponent(status)}` : ''
    const r = await get<ListResp<Ticket>>(`/v1/admin/support/tickets${q}`)
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setItems(r.data.items || [])
  }, [status])

  useEffect(() => {
    void load()
  }, [load])

  const openTicket = async (id: string) => {
    setSelected(id)
    setBusy(true)
    const r = await get<TicketDetail>(`/v1/admin/support/tickets/${id}`)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setDetail(r.data)
    setReply('')
  }

  const setTicketStatus = async (next: string) => {
    if (!selected) return
    setBusy(true)
    const r = await patch<Ticket>(`/v1/admin/support/tickets/${selected}/status`, { status: next })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setDetail((d) => (d ? { ...d, ticket: r.data } : d))
    await load()
  }

  const sendReply = async (e: FormEvent) => {
    e.preventDefault()
    if (!selected || !reply.trim()) return
    setBusy(true)
    const r = await post<Reply>(`/v1/admin/support/tickets/${selected}/replies`, { body: reply.trim() })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setReply('')
    await openTicket(selected)
    await load()
  }

  if (loading && items.length === 0) return <PageLoader label="Loading reports…" />

  return (
    <div className="catalog-split">
      <Card
        title="Reports"
        actions={
          <select value={status} onChange={(e) => setStatus(e.target.value)} style={selectStyle}>
            <option value="">All</option>
            <option value="pending">Pending</option>
            <option value="ongoing">Ongoing</option>
            <option value="completed">Completed</option>
          </select>
        }
      >
        {error ? <div className="err-banner">{error}</div> : null}
        {items.length === 0 ? (
          <p className="muted">No reports yet.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {items.map((t) => (
              <button
                key={t.id}
                type="button"
                className={`tree-row${selected === t.id ? ' is-selected' : ''}`}
                style={{ textAlign: 'left', cursor: 'pointer', width: '100%', border: 0, background: 'transparent' }}
                onClick={() => void openTicket(t.id)}
              >
                <span style={{ flex: 1, minWidth: 0 }}>
                  <strong style={{ fontSize: 14 }}>{t.subject}</strong>
                  <span className="muted" style={{ display: 'block', fontSize: 12 }}>
                    @{t.username || t.user_id} · {fmt(t.created_at)}
                    {t.reply_count ? ` · ${t.reply_count} replies` : ''}
                  </span>
                </span>
                <span className={`status-pill ${t.status === 'completed' ? 'on' : 'off'}`}>{t.status}</span>
              </button>
            ))}
          </div>
        )}
      </Card>

      <Card title={detail ? detail.ticket.subject : 'Details'}>
        {!detail ? (
          <p className="muted">Select a report to view and reply.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div className="muted" style={{ fontSize: 12 }}>
              @{detail.ticket.username || detail.ticket.user_id}
              {detail.ticket.full_name ? ` · ${detail.ticket.full_name}` : ''} · {fmt(detail.ticket.created_at)}
            </div>
            <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{detail.ticket.body}</p>
            {detail.ticket.attachment_keys?.length ? (
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {detail.ticket.attachment_keys.map((k) => (
                  <a key={k} href={mediaUrl(mediaBase, k)} target="_blank" rel="noreferrer" className="brand-thumb brand-thumb-banner">
                    <img src={mediaUrl(mediaBase, k)} alt="" />
                  </a>
                ))}
              </div>
            ) : null}

            <label className="field-label">
              Status
              <select
                value={detail.ticket.status}
                onChange={(e) => void setTicketStatus(e.target.value)}
                style={selectStyle}
                disabled={busy}
              >
                <option value="pending">Pending</option>
                <option value="ongoing">Ongoing</option>
                <option value="completed">Completed</option>
              </select>
            </label>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 280, overflow: 'auto' }}>
              {detail.replies.length === 0 ? (
                <p className="muted">No replies yet.</p>
              ) : (
                detail.replies.map((r) => (
                  <div
                    key={r.id}
                    style={{
                      padding: '8px 10px',
                      borderRadius: 8,
                      background: r.is_staff ? 'var(--accent-soft, #eef2ff)' : 'var(--surface-2, #f4f4f5)',
                      border: '1px solid var(--line)',
                    }}
                  >
                    <div className="muted" style={{ fontSize: 11, marginBottom: 4 }}>
                      {r.is_staff ? 'Support' : `@${r.username || 'user'}`} · {fmt(r.created_at)}
                    </div>
                    <div style={{ whiteSpace: 'pre-wrap', fontSize: 13 }}>{r.body}</div>
                  </div>
                ))
              )}
            </div>

            <form className="form-grid" onSubmit={(e) => void sendReply(e)}>
              <TextArea label="Reply" value={reply} onChange={(e) => setReply(e.target.value)} required />
              <div className="form-actions">
                <Button type="submit" size="sm" loading={busy} disabled={!reply.trim()}>
                  Send reply
                </Button>
              </div>
            </form>
          </div>
        )}
      </Card>
    </div>
  )
}

function ArticlesPane() {
  const [items, setItems] = useState<Article[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState<Article | null>(null)
  const [creating, setCreating] = useState(false)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [status, setStatus] = useState(1)
  const [sortOrder, setSortOrder] = useState(1)

  const load = useCallback(async () => {
    setLoading(true)
    const r = await get<ListResp<Article>>('/v1/admin/help/articles')
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setItems(r.data.items || [])
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const startCreate = () => {
    setCreating(true)
    setEditing(null)
    setTitle('')
    setBody('')
    setStatus(1)
    setSortOrder((items.length ? Math.max(...items.map((i) => i.sort_order || 0)) : 0) + 1)
    setError('')
    setOk('')
  }

  const startEdit = (a: Article) => {
    setCreating(false)
    setEditing(a)
    setTitle(a.title)
    setBody(a.body)
    setStatus(a.status)
    setSortOrder(a.sort_order || 1)
    setError('')
    setOk('')
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const payload = { title: title.trim(), body, status, sort_order: sortOrder }
    const r = creating
      ? await post<Article>('/v1/admin/help/articles', payload)
      : await patch<Article>(`/v1/admin/help/articles/${editing?.id}`, payload)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(creating ? 'Article created.' : 'Saved.')
    setCreating(false)
    setEditing(r.data)
    await load()
  }

  const onDelete = async () => {
    if (!editing || !window.confirm(`Delete “${editing.title}”?`)) return
    setBusy(true)
    const r = await del(`/v1/admin/help/articles/${editing.id}`)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk('Deleted.')
    setEditing(null)
    setCreating(false)
    await load()
  }

  if (loading && items.length === 0) return <PageLoader label="Loading articles…" />

  return (
    <div className="catalog-split">
      <Card
        title="Topics"
        actions={
          <Button size="sm" onClick={startCreate}>
            + Article
          </Button>
        }
      >
        {error && !creating && !editing ? <div className="err-banner">{error}</div> : null}
        {items.length === 0 ? (
          <p className="muted">No articles yet. Add topics users can open in Help Centre.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {items.map((a) => (
              <button
                key={a.id}
                type="button"
                className={`tree-row${editing?.id === a.id && !creating ? ' is-selected' : ''}`}
                style={{ textAlign: 'left', cursor: 'pointer', width: '100%', border: 0, background: 'transparent' }}
                onClick={() => startEdit(a)}
              >
                <span style={{ flex: 1, minWidth: 0 }}>
                  <strong style={{ fontSize: 14 }}>{a.title}</strong>
                  <span className="muted" style={{ marginLeft: 8, fontSize: 12 }}>
                    #{a.sort_order} · ♥ {a.like_count} · ✕ {a.dislike_count}
                  </span>
                </span>
                <span className={`status-pill ${a.status === 1 ? 'on' : 'off'}`}>{a.status === 1 ? 'Live' : 'Off'}</span>
              </button>
            ))}
          </div>
        )}
      </Card>

      <Card title={creating ? 'New article' : editing ? 'Edit article' : 'Details'}>
        {!creating && !editing ? (
          <p className="muted">Select an article or create one.</p>
        ) : (
          <form className="form-grid" onSubmit={(e) => void onSubmit(e)}>
            {error ? <div className="err-banner">{error}</div> : null}
            {ok ? <div className="ok-banner">{ok}</div> : null}
            <Field label="Title *" value={title} onChange={(e) => setTitle(e.target.value)} required />
            <TextArea label="Details *" value={body} onChange={(e) => setBody(e.target.value)} required />
            <Field
              label="Order"
              type="number"
              min={1}
              value={String(sortOrder)}
              onChange={(e) => setSortOrder(Number(e.target.value) || 1)}
            />
            <label className="field-label">
              Status
              <select value={status} onChange={(e) => setStatus(Number(e.target.value))} style={selectStyle}>
                <option value={1}>Published</option>
                <option value={0}>Hidden</option>
              </select>
            </label>
            <div className="form-actions">
              <Button type="submit" size="sm" loading={busy}>
                {creating ? 'Create' : 'Save'}
              </Button>
              {!creating && editing ? (
                <Button type="button" size="sm" variant="danger" loading={busy} onClick={() => void onDelete()}>
                  Delete
                </Button>
              ) : null}
              <Button
                type="button"
                size="sm"
                variant="ghost"
                onClick={() => {
                  setCreating(false)
                  setEditing(null)
                }}
              >
                Cancel
              </Button>
            </div>
          </form>
        )}
      </Card>
    </div>
  )
}
