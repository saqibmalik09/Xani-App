import { useCallback, useEffect, useRef, useState, type CSSProperties, type FormEvent } from 'react'
import { del, get, patch, post, putBytes } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type Category = {
  id: string
  title: string
  description: string
  status: number
  sort_order: number
  sticker_count?: number
}

type Sticker = {
  id: string
  category_id: string
  category_title?: string
  title: string
  description: string
  image_key: string
  status: number
  sort_order: number
  created_at: string
}

type ListResp<T> = { items: T[]; next_cursor?: string }

const selectStyle: CSSProperties = {
  height: 36,
  borderRadius: 8,
  border: '1px solid var(--line)',
  padding: '0 10px',
  background: 'var(--surface)',
  fontSize: 13,
}

export function StickersPage() {
  const [tab, setTab] = useState<'options' | 'categories'>('options')
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head">
        <h1>Stickers</h1>
        <p>Create categories, then add sticker options with an image under each category.</p>
      </div>
      <div className="tabs">
        <button
          type="button"
          className={`tab${tab === 'options' ? ' is-active' : ''}`}
          onClick={() => setTab('options')}
        >
          Options
        </button>
        <button
          type="button"
          className={`tab${tab === 'categories' ? ' is-active' : ''}`}
          onClick={() => setTab('categories')}
        >
          Categories
        </button>
      </div>
      {tab === 'options' ? <OptionsPanel /> : <CategoriesPanel />}
    </div>
  )
}

function CategoriesPanel() {
  const [items, setItems] = useState<Category[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [editing, setEditing] = useState<Category | null>(null)
  const [creating, setCreating] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState(1)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    const r = await get<ListResp<Category>>('/v1/admin/sticker-categories')
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
    setDescription('')
    setStatus(1)
    setError('')
    setOk('')
  }

  const startEdit = (c: Category) => {
    setCreating(false)
    setEditing(c)
    setTitle(c.title)
    setDescription(c.description)
    setStatus(c.status)
    setError('')
    setOk('')
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const body = { title: title.trim(), description, status, sort_order: editing?.sort_order ?? 0 }
    const r = creating
      ? await post<Category>('/v1/admin/sticker-categories', body)
      : await patch<Category>(`/v1/admin/sticker-categories/${editing?.id}`, body)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(creating ? 'Category created.' : 'Saved.')
    setCreating(false)
    setEditing(r.data)
    await load()
  }

  if (loading) return <PageLoader label="Loading categories…" />

  return (
    <div className="catalog-split">
      <Card
        title="Categories"
        actions={
          <Button size="sm" onClick={startCreate}>
            + New
          </Button>
        }
      >
        {error && !creating && !editing ? <div className="err-banner">{error}</div> : null}
        <div className="role-list">
          {items.map((c) => (
            <button
              key={c.id}
              type="button"
              className={`role-row${editing?.id === c.id ? ' is-active' : ''}`}
              onClick={() => startEdit(c)}
            >
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14 }}>{c.title}</div>
                <div className="muted" style={{ fontSize: 12 }}>
                  {c.sticker_count ?? 0} options · {c.status === 1 ? 'Active' : 'Off'}
                </div>
              </div>
            </button>
          ))}
          {items.length === 0 ? <p className="muted">No categories yet.</p> : null}
        </div>
      </Card>

      <Card title={creating ? 'New category' : editing ? 'Edit category' : 'Details'}>
        {error && (creating || editing) ? <div className="err-banner">{error}</div> : null}
        {ok ? <div className="ok-banner">{ok}</div> : null}
        {!creating && !editing ? (
          <p className="muted" style={{ margin: 0 }}>
            Select a category or create one.
          </p>
        ) : (
          <form className="form-grid" onSubmit={(e) => void onSubmit(e)}>
            <Field label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
            <TextArea label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
            <label className="field-label">
              Status
              <select style={selectStyle} value={status} onChange={(e) => setStatus(Number(e.target.value))}>
                <option value={1}>Active</option>
                <option value={0}>Inactive</option>
              </select>
            </label>
            <div className="form-actions">
              <Button type="submit" size="sm" loading={busy}>
                {creating ? 'Create' : 'Save'}
              </Button>
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

function OptionsPanel() {
  const { mediaBase } = useAuth()
  const [categories, setCategories] = useState<Category[]>([])
  const [items, setItems] = useState<Sticker[]>([])
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [cursor, setCursor] = useState<string | undefined>()
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)

  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const [editing, setEditing] = useState<Sticker | null>(null)
  const [creating, setCreating] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [cat, setCat] = useState('')
  const [status, setStatus] = useState(1)
  const [imageKey, setImageKey] = useState('')
  const imageRef = useRef<HTMLInputElement>(null)

  const loadCats = useCallback(async () => {
    const r = await get<ListResp<Category>>('/v1/admin/sticker-categories')
    if (r.ok) setCategories(r.data.items || [])
  }, [])

  const loadStickers = useCallback(
    async (reset: boolean, nextCursor?: string) => {
      if (reset) setLoading(true)
      else setMore(true)
      setError('')
      const params = new URLSearchParams({ limit: '30' })
      if (query) params.set('q', query)
      if (categoryId) params.set('category_id', categoryId)
      if (statusFilter !== '') params.set('status', statusFilter)
      if (nextCursor) {
        const parts = nextCursor.split('|')
        if (parts[0] && parts[1]) {
          params.set('after_at', parts[0])
          params.set('after_id', parts[1])
        }
      }
      const r = await get<ListResp<Sticker>>(`/v1/admin/stickers?${params}`)
      setLoading(false)
      setMore(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      const list = r.data.items || []
      setItems((prev) => (reset ? list : [...prev, ...list]))
      setCursor(r.data.next_cursor || undefined)
    },
    [query, categoryId, statusFilter],
  )

  useEffect(() => {
    void loadCats()
  }, [loadCats])
  useEffect(() => {
    void loadStickers(true)
  }, [loadStickers])

  const fillForm = (g: Sticker | null) => {
    if (!g) {
      setTitle('')
      setDescription('')
      setCat(categories[0]?.id || '')
      setStatus(1)
      setImageKey('')
      return
    }
    setTitle(g.title)
    setDescription(g.description)
    setCat(g.category_id)
    setStatus(g.status)
    setImageKey(g.image_key)
  }

  const startCreate = () => {
    setCreating(true)
    setEditing(null)
    fillForm(null)
    setError('')
    setOk('')
  }

  const startEdit = (g: Sticker) => {
    setCreating(false)
    setEditing(g)
    fillForm(g)
    setError('')
    setOk('')
  }

  const onImagePick = async (file: File | null) => {
    if (!file) return
    setBusy(true)
    setError('')
    const ext = file.name.includes('.') ? file.name.split('.').pop() : ''
    const r = await putBytes<{ key: string }>(
      `/v1/admin/stickers/upload?ext=${encodeURIComponent(ext || '')}`,
      file,
      file.type || 'application/octet-stream',
    )
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setImageKey(r.data.key)
    setOk('Image uploaded.')
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!cat) {
      setError('Choose a category.')
      return
    }
    setBusy(true)
    setError('')
    setOk('')
    const body = {
      category_id: cat,
      title: title.trim(),
      description,
      status,
      image_key: imageKey,
      sort_order: editing?.sort_order ?? 0,
    }
    const r = creating
      ? await post<Sticker>('/v1/admin/stickers', body)
      : await patch<Sticker>(`/v1/admin/stickers/${editing?.id}`, body)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(creating ? 'Option created.' : 'Saved.')
    setCreating(false)
    setEditing(r.data)
    fillForm(r.data)
    await loadStickers(true)
  }

  const onDelete = async () => {
    if (!editing) return
    if (!window.confirm(`Delete “${editing.title}”?`)) return
    setBusy(true)
    const r = await del(`/v1/admin/stickers/${editing.id}`)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk('Deleted.')
    setEditing(null)
    setCreating(false)
    await loadStickers(true)
  }

  const imageUrl = mediaUrl(mediaBase, imageKey)

  return (
    <div className="catalog-split">
      <Card
        title="Sticker options"
        actions={
          <Button size="sm" onClick={startCreate} disabled={categories.length === 0}>
            + New option
          </Button>
        }
      >
        <form
          className="filters filters-compact"
          onSubmit={(e) => {
            e.preventDefault()
            setQuery(q.trim())
          }}
        >
          <Field label="Search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Title prefix" />
          <label className="field-label">
            Category
            <select style={selectStyle} value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
              <option value="">All</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.title}
                </option>
              ))}
            </select>
          </label>
          <label className="field-label">
            Status
            <select style={selectStyle} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="">All</option>
              <option value="1">Active</option>
              <option value="0">Inactive</option>
            </select>
          </label>
          <Button type="submit" size="sm">
            Filter
          </Button>
        </form>

        {loading ? (
          <PageLoader label="Loading…" />
        ) : (
          <div className="role-list" style={{ marginTop: 12 }}>
            {items.map((g) => (
              <button
                key={g.id}
                type="button"
                className={`role-row${editing?.id === g.id ? ' is-active' : ''}`}
                onClick={() => startEdit(g)}
              >
                <div className="sticker-thumb">
                  {g.image_key ? <img src={mediaUrl(mediaBase, g.image_key)} alt="" /> : null}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 700, fontSize: 14 }}>{g.title}</div>
                  <div className="muted" style={{ fontSize: 12 }}>
                    {g.category_title || '—'} · {g.status === 1 ? 'Active' : 'Off'}
                  </div>
                </div>
              </button>
            ))}
            {items.length === 0 ? <p className="muted">No stickers yet.</p> : null}
            {cursor ? (
              <Button size="sm" variant="ghost" loading={more} onClick={() => void loadStickers(false, cursor)}>
                Load more
              </Button>
            ) : null}
          </div>
        )}
      </Card>

      <Card title={creating ? 'New option' : editing ? 'Edit option' : 'Details'}>
        {error ? <div className="err-banner">{error}</div> : null}
        {ok ? <div className="ok-banner">{ok}</div> : null}
        {!creating && !editing ? (
          <p className="muted" style={{ margin: 0 }}>
            {categories.length === 0 ? 'Create a category first.' : 'Select an option or create one.'}
          </p>
        ) : (
          <form className="form-grid" onSubmit={(e) => void onSubmit(e)}>
            <Field label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
            <label className="field-label">
              Category
              <select style={selectStyle} value={cat} onChange={(e) => setCat(e.target.value)} required>
                <option value="">Choose…</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.title}
                  </option>
                ))}
              </select>
            </label>
            <TextArea label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
            <label className="field-label">
              Status
              <select style={selectStyle} value={status} onChange={(e) => setStatus(Number(e.target.value))}>
                <option value={1}>Active</option>
                <option value={0}>Inactive</option>
              </select>
            </label>
            <div className="brand-pick">
              <span className="brand-label">Image</span>
              <button
                type="button"
                className="brand-thumb brand-thumb-lg"
                onClick={() => imageRef.current?.click()}
                disabled={busy}
              >
                {imageUrl ? <img src={imageUrl} alt="" /> : <span>Click to set</span>}
              </button>
              <input
                ref={imageRef}
                type="file"
                accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml"
                hidden
                onChange={(e) => void onImagePick(e.target.files?.[0] || null)}
              />
            </div>
            <div className="form-actions">
              <Button type="submit" size="sm" loading={busy}>
                {creating ? 'Create' : 'Save'}
              </Button>
              {editing ? (
                <Button type="button" size="sm" variant="ghost" onClick={() => void onDelete()} disabled={busy}>
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
