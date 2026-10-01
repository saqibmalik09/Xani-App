import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { del, get, patch, post } from '../lib/api'
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

type ListResp<T> = { items: T[] }

const selectStyle: React.CSSProperties = {
  height: 36,
  borderRadius: 8,
  border: '1px solid var(--line)',
  padding: '0 10px',
  background: 'var(--surface)',
  fontSize: 13,
}

export function TermsPoliciesPage() {
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
    const r = await get<ListResp<Article>>('/v1/admin/legal/articles')
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
      ? await post<Article>('/v1/admin/legal/articles', payload)
      : await patch<Article>(`/v1/admin/legal/articles/${editing?.id}`, payload)
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
    const r = await del(`/v1/admin/legal/articles/${editing.id}`)
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

  if (loading && items.length === 0) return <PageLoader label="Loading terms…" />

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head">
        <h1>Terms & Policies</h1>
        <p>Legal topics users open from Settings and the profile menu — same article pattern as Help Centre.</p>
      </div>

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
            <p className="muted">No articles yet. Add Terms of Service, Privacy Policy, and related topics.</p>
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
    </div>
  )
}
