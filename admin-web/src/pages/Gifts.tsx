import { useCallback, useEffect, useRef, useState, type CSSProperties, type FormEvent } from 'react'
import { del, get, patch, post, putBytes } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { Loader, PageLoader } from '../ui/Loader'

type Category = {
  id: string
  title: string
  description: string
  status: number
  sort_order: number
  lucky: boolean
  gift_count?: number
}

type Gift = {
  id: string
  category_id: string
  category_title?: string
  title: string
  description: string
  coin_price: number
  listing_key: string
  animation_key: string
  animation_mime: string
  sound_key: string
  sound_mime: string
  duration_ms: number
  status: number
  sort_order: number
  created_at: string
}

type Person = { id: string; username: string; full_name: string; avatar_url?: string }

type GiftRecord = {
  id: string
  room_id: string
  sender: Person
  receiver: Person
  gift_id: string
  gift_title: string
  listing_url?: string
  unit_coins: number
  count: number
  total_coins: number
  receiver_diamonds: number
  platform_diamonds: number
  combo: number
  at: string
}

type PlatformTotals = {
  diamonds: number
  coins: number
  gifts: number
  receiver_bps: number
  platform_user_id?: string
}

type ListResp<T> = { items: T[]; next_cursor?: string }

function fileLabel(key: string, mime?: string): string {
  if (!key) return ''
  const base = key.split('/').pop() || key
  if (mime?.includes('svga') || base.endsWith('.svga')) return `${base} (SVGA)`
  if (mime?.includes('json')) return `${base} (Lottie)`
  if (mime?.includes('svg')) return `${base} (SVG)`
  if (mime?.startsWith('video/')) return `${base} (video)`
  if (mime?.startsWith('audio/')) return `${base} (audio)`
  return base
}

const ANIM_ACCEPT =
  '.svga,.json,image/jpeg,image/png,image/apng,image/webp,image/gif,image/svg+xml,video/mp4,video/webm,application/x-svga,application/json'
const SOUND_ACCEPT = 'audio/*,.mp3,.aac,.m4a,.ogg,.oga,.wav,.flac,.weba'

function personLabel(p?: Person): string {
  if (!p) return '—'
  return p.full_name || (p.username ? `@${p.username}` : p.id)
}

const selectStyle: CSSProperties = {
  height: 36,
  borderRadius: 8,
  border: '1px solid var(--line)',
  padding: '0 10px',
  background: 'var(--surface)',
  fontSize: 13,
}

export function GiftsPage() {
  const [tab, setTab] = useState<'gifts' | 'categories' | 'records'>('gifts')
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head">
        <h1>Gifts</h1>
        <p>Create categories, then add gifts with a list image, coin price, an animation (SVGA, Lottie, GIF, MP4…) and a sound.</p>
      </div>
      <div className="tabs">
        <button type="button" className={`tab${tab === 'gifts' ? ' is-active' : ''}`} onClick={() => setTab('gifts')}>
          Gifts
        </button>
        <button
          type="button"
          className={`tab${tab === 'categories' ? ' is-active' : ''}`}
          onClick={() => setTab('categories')}
        >
          Categories
        </button>
        <button type="button" className={`tab${tab === 'records' ? ' is-active' : ''}`} onClick={() => setTab('records')}>
          Sent gifts
        </button>
      </div>
      {tab === 'gifts' ? <GiftsPanel /> : tab === 'categories' ? <CategoriesPanel /> : <RecordsPanel />}
    </div>
  )
}

/** Every gift sent in a live: who, to whom, what, how much, when. */
function RecordsPanel() {
  const [items, setItems] = useState<GiftRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [cursor, setCursor] = useState<string | undefined>()
  const [error, setError] = useState('')
  const [userId, setUserId] = useState('')
  const [roomId, setRoomId] = useState('')
  const [dir, setDir] = useState<'received' | 'sent'>('received')
  const [applied, setApplied] = useState({ userId: '', roomId: '', dir: 'received' as 'received' | 'sent' })
  const [totals, setTotals] = useState<PlatformTotals | null>(null)

  useEffect(() => {
    let alive = true
    void get<PlatformTotals>('/v1/admin/live-gifts/totals').then((r) => {
      if (alive && r.ok) setTotals(r.data)
    })
    return () => {
      alive = false
    }
  }, [])

  const load = useCallback(
    async (reset: boolean, before?: string) => {
      if (reset) setLoading(true)
      else setMore(true)
      setError('')
      const params = new URLSearchParams({ limit: '50' })
      if (applied.userId.trim()) {
        params.set('user_id', applied.userId.trim())
        params.set('dir', applied.dir)
      }
      if (applied.roomId.trim()) params.set('room_id', applied.roomId.trim())
      if (before) params.set('before_id', before)
      const r = await get<ListResp<GiftRecord>>(`/v1/admin/live-gifts?${params}`)
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
    [applied],
  )

  useEffect(() => {
    void load(true)
  }, [load])

  const total = items.reduce((n, g) => n + g.total_coins, 0)
  const platformShown = items.reduce((n, g) => n + (g.platform_diamonds || 0), 0)

  return (
    <Card title="Sent gifts">
      {totals ? (
        <div className="stats-row" style={{ display: 'flex', gap: 18, flexWrap: 'wrap', marginBottom: 12, fontSize: 13 }}>
          <div>
            <div className="muted" style={{ fontSize: 11 }}>Platform revenue</div>
            <strong>{totals.diamonds.toLocaleString()} 💎</strong>
          </div>
          <div>
            <div className="muted" style={{ fontSize: 11 }}>Gross coins gifted</div>
            <strong>{totals.coins.toLocaleString()}</strong>
          </div>
          <div>
            <div className="muted" style={{ fontSize: 11 }}>Gifts</div>
            <strong>{totals.gifts.toLocaleString()}</strong>
          </div>
          <div>
            <div className="muted" style={{ fontSize: 11 }}>Split</div>
            <strong>
              {(totals.receiver_bps / 100).toFixed(0)}% receiver · {(100 - totals.receiver_bps / 100).toFixed(0)}% platform
            </strong>
            {totals.platform_user_id ? (
              <div className="muted" style={{ fontSize: 11 }}>booked to user {totals.platform_user_id}</div>
            ) : (
              <div className="muted" style={{ fontSize: 11 }}>no platform account — set gifts.platform_user_id</div>
            )}
          </div>
        </div>
      ) : null}
      <form
        className="filters filters-compact"
        onSubmit={(e) => {
          e.preventDefault()
          setApplied({ userId, roomId, dir })
        }}
      >
        <Field label="User id" value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="Sender or receiver id" />
        <label className="field-label">
          Side
          <select value={dir} onChange={(e) => setDir(e.target.value as 'received' | 'sent')} style={selectStyle}>
            <option value="received">Received by user</option>
            <option value="sent">Sent by user</option>
          </select>
        </label>
        <Field label="Room id" value={roomId} onChange={(e) => setRoomId(e.target.value)} placeholder="Live room id" />
        <Button type="submit" size="sm" style={{ alignSelf: 'flex-end', minWidth: 72 }}>
          Filter
        </Button>
      </form>
      {error ? <div className="err-banner">{error}</div> : null}
      <p className="muted" style={{ margin: '4px 0 10px', fontSize: 12 }}>
        {items.length} record{items.length === 1 ? '' : 's'} shown · {total.toLocaleString()} coins · {platformShown.toLocaleString()} platform 💎
      </p>
      {loading && items.length === 0 ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 20 }}>
          <Loader />
        </div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table className="table">
            <thead>
              <tr>
                <th>When</th>
                <th>From</th>
                <th>To</th>
                <th>Gift</th>
                <th>Qty</th>
                <th>Coins</th>
                <th>To receiver</th>
                <th>Platform</th>
                <th>Room</th>
              </tr>
            </thead>
            <tbody>
              {items.map((g) => (
                <tr key={g.id}>
                  <td className="muted" style={{ whiteSpace: 'nowrap', fontSize: 12 }}>
                    {new Date(g.at).toLocaleString()}
                  </td>
                  <td>
                    <strong>{personLabel(g.sender)}</strong>
                    <div className="muted" style={{ fontSize: 11 }}>{g.sender?.id}</div>
                  </td>
                  <td>
                    <strong>{personLabel(g.receiver)}</strong>
                    <div className="muted" style={{ fontSize: 11 }}>{g.receiver?.id}</div>
                  </td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      {g.listing_url ? <img className="thumb" src={g.listing_url} alt="" loading="lazy" style={{ width: 28, height: 28 }} /> : null}
                      <span>{g.gift_title}</span>
                      {g.combo > 1 ? <span className="muted" style={{ fontSize: 11 }}>×{g.combo} combo</span> : null}
                    </div>
                  </td>
                  <td>{g.count}</td>
                  <td>
                    <strong>{g.total_coins.toLocaleString()}</strong>
                    {g.count > 1 ? <div className="muted" style={{ fontSize: 11 }}>{g.unit_coins.toLocaleString()} each</div> : null}
                  </td>
                  <td>{(g.receiver_diamonds ?? 0).toLocaleString()} 💎</td>
                  <td>{(g.platform_diamonds ?? 0).toLocaleString()} 💎</td>
                  <td className="muted" style={{ fontSize: 11 }}>{g.room_id}</td>
                </tr>
              ))}
              {items.length === 0 && !loading ? (
                <tr>
                  <td colSpan={9} className="muted" style={{ padding: 16 }}>
                    No gifts match.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      )}
      {cursor ? (
        <div style={{ marginTop: 12, display: 'flex', justifyContent: 'center' }}>
          <Button size="sm" variant="secondary" loading={more} onClick={() => void load(false, cursor)}>
            More
          </Button>
        </div>
      ) : null}
    </Card>
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
  const [lucky, setLucky] = useState(false)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    const r = await get<ListResp<Category>>('/v1/admin/gift-categories')
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
    setLucky(false)
    setError('')
    setOk('')
  }

  const startEdit = (c: Category) => {
    setCreating(false)
    setEditing(c)
    setTitle(c.title)
    setDescription(c.description)
    setStatus(c.status)
    setLucky(!!c.lucky)
    setError('')
    setOk('')
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const body = { title: title.trim(), description, status, lucky, sort_order: editing?.sort_order ?? 0 }
    const r = creating
      ? await post<Category>('/v1/admin/gift-categories', body)
      : await patch<Category>(`/v1/admin/gift-categories/${editing?.id}`, body)
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

  if (loading) return <PageLoader label="Loading…" />

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
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Status</th>
              <th>Gifts</th>
            </tr>
          </thead>
          <tbody>
            {items.map((c) => (
              <tr key={c.id} className={`row-link${editing?.id === c.id && !creating ? ' is-selected' : ''}`} onClick={() => startEdit(c)}>
                <td>
                  <strong>{c.title}</strong>
                  {c.lucky ? <div className="muted" style={{ fontSize: 11 }}>Lucky · combo sends</div> : null}
                </td>
                <td>
                  <span className={`status-pill ${c.status === 1 ? 'on' : 'off'}`}>{c.status === 1 ? 'Active' : 'Off'}</span>
                </td>
                <td>{c.gift_count ?? 0}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card title={creating ? 'New category' : editing ? 'Edit category' : 'Select a category'}>
        {!creating && !editing ? (
          <p className="muted">Click a row to edit, or New to add one.</p>
        ) : (
          <form className="form-grid" onSubmit={(e) => void onSubmit(e)}>
            {error ? <div className="err-banner">{error}</div> : null}
            {ok ? <div className="ok-banner">{ok}</div> : null}
            <Field label="Title *" value={title} onChange={(e) => setTitle(e.target.value)} required />
            <TextArea label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
            <label className="field-label">
              Status
              <select value={status} onChange={(e) => setStatus(Number(e.target.value))} style={selectStyle}>
                <option value={1}>Active</option>
                <option value={0}>Inactive</option>
              </select>
            </label>
            <label className="field-label" style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <input type="checkbox" checked={lucky} onChange={(e) => setLucky(e.target.checked)} />
              Lucky category — the gift stays selected and every tap sends again with a combo counter
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

function GiftsPanel() {
  const { mediaBase } = useAuth()
  const [categories, setCategories] = useState<Category[]>([])
  const [items, setItems] = useState<Gift[]>([])
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
  const [sort, setSort] = useState('newest')

  const [editing, setEditing] = useState<Gift | null>(null)
  const [creating, setCreating] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [coinPrice, setCoinPrice] = useState(100)
  const [cat, setCat] = useState('')
  const [status, setStatus] = useState(1)
  const [listingKey, setListingKey] = useState('')
  const [animKey, setAnimKey] = useState('')
  const [animMime, setAnimMime] = useState('')
  const [animName, setAnimName] = useState('')
  const [soundKey, setSoundKey] = useState('')
  const [soundMime, setSoundMime] = useState('')
  const [soundName, setSoundName] = useState('')
  const [durationMs, setDurationMs] = useState(0)

  const listingRef = useRef<HTMLInputElement>(null)
  const animRef = useRef<HTMLInputElement>(null)
  const soundRef = useRef<HTMLInputElement>(null)

  const loadCats = useCallback(async () => {
    const r = await get<ListResp<Category>>('/v1/admin/gift-categories')
    if (r.ok) setCategories(r.data.items || [])
  }, [])

  const loadGifts = useCallback(
    async (reset: boolean, nextCursor?: string) => {
      if (reset) setLoading(true)
      else setMore(true)
      setError('')
      const params = new URLSearchParams({ limit: '30', sort })
      if (query) params.set('q', query)
      if (categoryId) params.set('category_id', categoryId)
      if (statusFilter !== '') params.set('status', statusFilter)
      if (nextCursor) {
        const parts = nextCursor.split('|')
        if (parts[0] === 'newest' && parts[1] && parts[2]) {
          params.set('after_at', parts[1])
          params.set('after_id', parts[2])
        } else if (parts[0] === 'price' && parts[1] && parts[2]) {
          params.set('after_price', parts[1])
          params.set('after_id', parts[2])
        } else if (parts[0] === 'title' && parts[1] && parts[2]) {
          params.set('after_title', parts[1])
          params.set('after_id', parts[2])
        }
      }
      const r = await get<ListResp<Gift>>(`/v1/admin/gifts?${params}`)
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
    [query, categoryId, statusFilter, sort],
  )

  useEffect(() => {
    void loadCats()
  }, [loadCats])
  useEffect(() => {
    void loadGifts(true)
  }, [loadGifts])

  const fillForm = (g: Gift | null) => {
    if (!g) {
      setTitle('')
      setDescription('')
      setCoinPrice(100)
      setCat(categories[0]?.id || '')
      setStatus(1)
      setListingKey('')
      setAnimKey('')
      setAnimMime('')
      setAnimName('')
      setSoundKey('')
      setSoundMime('')
      setSoundName('')
      setDurationMs(0)
      return
    }
    setTitle(g.title)
    setDescription(g.description)
    setCoinPrice(g.coin_price)
    setCat(g.category_id)
    setStatus(g.status)
    setListingKey(g.listing_key)
    setAnimKey(g.animation_key)
    setAnimMime(g.animation_mime)
    setAnimName(fileLabel(g.animation_key, g.animation_mime))
    setSoundKey(g.sound_key || '')
    setSoundMime(g.sound_mime || '')
    setSoundName(fileLabel(g.sound_key || '', g.sound_mime))
    setDurationMs(g.duration_ms || 0)
  }

  const startCreate = () => {
    setCreating(true)
    setEditing(null)
    fillForm(null)
    setError('')
    setOk('')
  }

  const startEdit = (g: Gift) => {
    setCreating(false)
    setEditing(g)
    fillForm(g)
    setError('')
    setOk('')
  }

  const uploadFile = async (kind: 'listing' | 'anim' | 'sound', file: File) => {
    const ext = file.name.includes('.') ? file.name.split('.').pop() : ''
    // Browsers report no type for .svga and some audio; the server resolves
    // the extension, so always send it.
    const r = await putBytes<{ key: string; mime: string }>(
      `/v1/admin/gifts/upload?kind=${kind}&ext=${encodeURIComponent((ext || '').toLowerCase())}`,
      file,
      file.type || 'application/octet-stream',
    )
    if (!r.ok) throw new Error(r.message)
    return r.data
  }

  const onSoundPick = async (file: File | null) => {
    if (!file) return
    setBusy(true)
    setError('')
    try {
      const up = await uploadFile('sound', file)
      setSoundKey(up.key)
      setSoundMime(up.mime)
      setSoundName(file.name || fileLabel(up.key, up.mime))
      setOk('Sound saved.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed')
    }
    setBusy(false)
  }

  const onListingPick = async (file: File | null) => {
    if (!file) return
    setBusy(true)
    setError('')
    try {
      const up = await uploadFile('listing', file)
      setListingKey(up.key)
      setOk('List image uploaded.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed')
    }
    setBusy(false)
  }

  const onAnimPick = async (file: File | null) => {
    if (!file) return
    setBusy(true)
    setError('')
    try {
      const up = await uploadFile('anim', file)
      setAnimKey(up.key)
      setAnimMime(up.mime)
      setAnimName(file.name || fileLabel(up.key, up.mime))
      setOk('Animation file saved.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed')
    }
    setBusy(false)
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
      coin_price: coinPrice,
      status,
      listing_key: listingKey,
      animation_key: animKey,
      animation_mime: animMime,
      sound_key: soundKey,
      sound_mime: soundMime,
      duration_ms: durationMs,
      sort_order: editing?.sort_order ?? 0,
    }
    const r = creating ? await post<Gift>('/v1/admin/gifts', body) : await patch<Gift>(`/v1/admin/gifts/${editing?.id}`, body)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(creating ? 'Gift created.' : 'Saved.')
    setCreating(false)
    setEditing(r.data)
    fillForm(r.data)
    await loadGifts(true)
  }

  const onDelete = async () => {
    if (!editing) return
    if (!window.confirm(`Delete “${editing.title}”?`)) return
    setBusy(true)
    const r = await del(`/v1/admin/gifts/${editing.id}`)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk('Deleted.')
    setEditing(null)
    setCreating(false)
    await loadGifts(true)
  }

  const listingUrl = mediaUrl(mediaBase, listingKey)

  return (
    <div className="catalog-split">
      <Card
        title="All gifts"
        actions={
          <Button size="sm" onClick={startCreate} disabled={categories.length === 0}>
            + New gift
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
            <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} style={selectStyle}>
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
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={selectStyle}>
              <option value="">All</option>
              <option value="1">Active</option>
              <option value="0">Inactive</option>
            </select>
          </label>
          <label className="field-label">
            Sort
            <select value={sort} onChange={(e) => setSort(e.target.value)} style={selectStyle}>
              <option value="newest">Newest</option>
              <option value="price_asc">Price ↑</option>
              <option value="price_desc">Price ↓</option>
              <option value="title">A–Z</option>
            </select>
          </label>
          <Button type="submit" size="sm" style={{ alignSelf: 'flex-end', minWidth: 72 }}>
            Filter
          </Button>
        </form>

        {categories.length === 0 ? <p className="muted">Add a category first (Categories tab).</p> : null}
        {error && !creating && !editing ? <div className="err-banner">{error}</div> : null}

        {loading && items.length === 0 ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 20 }}>
            <Loader />
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Image</th>
                  <th>Title</th>
                  <th>Animation</th>
                  <th>Coins</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {items.map((g) => {
                  const thumb = mediaUrl(mediaBase, g.listing_key)
                  return (
                    <tr
                      key={g.id}
                      className={`row-link${editing?.id === g.id && !creating ? ' is-selected' : ''}`}
                      onClick={() => startEdit(g)}
                    >
                      <td>
                        {thumb ? <img className="thumb" src={thumb} alt="" loading="lazy" /> : <div className="thumb" />}
                      </td>
                      <td>
                        <strong>{g.title}</strong>
                        <div className="muted">{g.category_title}</div>
                      </td>
                      <td className="muted" style={{ fontSize: 12, maxWidth: 160 }}>
                        {g.animation_key ? fileLabel(g.animation_key, g.animation_mime) : '—'}
                        {g.sound_key ? <div>♪ {fileLabel(g.sound_key, g.sound_mime)}</div> : null}
                      </td>
                      <td>{g.coin_price.toLocaleString()}</td>
                      <td>
                        <span className={`status-pill ${g.status === 1 ? 'on' : 'off'}`}>
                          {g.status === 1 ? 'Active' : 'Off'}
                        </span>
                      </td>
                    </tr>
                  )
                })}
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="muted" style={{ padding: 16 }}>
                      No gifts yet.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        )}

        {cursor ? (
          <div style={{ marginTop: 12, display: 'flex', justifyContent: 'center' }}>
            <Button size="sm" variant="secondary" loading={more} onClick={() => void loadGifts(false, cursor)}>
              More
            </Button>
          </div>
        ) : null}
      </Card>

      <Card title={creating ? 'New gift' : editing ? `Edit · ${editing.title}` : 'Select a gift'}>
        {!creating && !editing ? (
          <p className="muted">Click a gift in the table, or New gift.</p>
        ) : (
          <form className="form-grid" onSubmit={(e) => void onSubmit(e)}>
            {error ? <div className="err-banner">{error}</div> : null}
            {ok ? <div className="ok-banner">{ok}</div> : null}

            <Field label="Title *" value={title} onChange={(e) => setTitle(e.target.value)} required />
            <Field
              label="Coins *"
              type="number"
              min={0}
              value={coinPrice}
              onChange={(e) => setCoinPrice(Number(e.target.value) || 0)}
              required
            />
            <label className="field-label">
              Category *
              <select value={cat} onChange={(e) => setCat(e.target.value)} required style={selectStyle}>
                <option value="">Choose…</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.title}
                  </option>
                ))}
              </select>
            </label>
            <label className="field-label">
              Status
              <select value={status} onChange={(e) => setStatus(Number(e.target.value))} style={selectStyle}>
                <option value={1}>Active</option>
                <option value={0}>Inactive</option>
              </select>
            </label>
            <TextArea label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />

            <div className="upload-row">
              <div>
                <div className="field-label-text">List image</div>
                <input
                  ref={listingRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp,image/gif"
                  hidden
                  onChange={(e) => void onListingPick(e.target.files?.[0] || null)}
                />
                <Button type="button" size="sm" variant="secondary" loading={busy} onClick={() => listingRef.current?.click()}>
                  Upload image
                </Button>
              </div>
              {listingUrl ? <img className="thumb" src={listingUrl} alt="" /> : null}
            </div>

            <div className="upload-row">
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="field-label-text">Animation file</div>
                <p className="muted" style={{ margin: '0 0 6px', fontSize: 12 }}>
                  SVGA (recommended), Lottie JSON, GIF, APNG/PNG, WebP, SVG, MP4 or WebM — up to 40 MB.
                </p>
                <input
                  ref={animRef}
                  type="file"
                  accept={ANIM_ACCEPT}
                  hidden
                  onChange={(e) => {
                    void onAnimPick(e.target.files?.[0] || null)
                    e.target.value = ''
                  }}
                />
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                  <Button type="button" size="sm" variant="secondary" loading={busy} onClick={() => animRef.current?.click()}>
                    Upload animation
                  </Button>
                  {animKey ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setAnimKey('-')
                        setAnimMime('')
                        setAnimName('')
                      }}
                    >
                      Remove
                    </Button>
                  ) : null}
                </div>
                {animKey && animKey !== '-' ? (
                  <div className="file-chip" title={animKey}>
                    {animName || fileLabel(animKey, animMime)}
                  </div>
                ) : (
                  <div className="muted" style={{ marginTop: 6, fontSize: 12 }}>
                    {animKey === '-' ? 'Animation will be removed on save' : 'No animation yet — the list image is shown instead'}
                  </div>
                )}
              </div>
            </div>

            <div className="upload-row">
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="field-label-text">Sound</div>
                <p className="muted" style={{ margin: '0 0 6px', fontSize: 12 }}>
                  MP3, AAC, M4A, OGG or WAV — plays together with the animation on every viewer&apos;s device.
                </p>
                <input
                  ref={soundRef}
                  type="file"
                  accept={SOUND_ACCEPT}
                  hidden
                  onChange={(e) => {
                    void onSoundPick(e.target.files?.[0] || null)
                    e.target.value = ''
                  }}
                />
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                  <Button type="button" size="sm" variant="secondary" loading={busy} onClick={() => soundRef.current?.click()}>
                    Upload sound
                  </Button>
                  {soundKey ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setSoundKey('-')
                        setSoundMime('')
                        setSoundName('')
                      }}
                    >
                      Remove
                    </Button>
                  ) : null}
                </div>
                {soundKey && soundKey !== '-' ? (
                  <div className="file-chip" title={soundKey}>
                    ♪ {soundName || fileLabel(soundKey, soundMime)}
                  </div>
                ) : (
                  <div className="muted" style={{ marginTop: 6, fontSize: 12 }}>
                    {soundKey === '-' ? 'Sound will be removed on save' : 'No sound'}
                  </div>
                )}
              </div>
            </div>

            <Field
              label="Show for (seconds)"
              type="number"
              min={0}
              max={60}
              step={0.5}
              value={durationMs / 1000}
              onChange={(e) => setDurationMs(Math.round(Math.max(0, Math.min(60, Number(e.target.value) || 0)) * 1000))}
            />
            <p className="muted" style={{ margin: '-6px 0 0', fontSize: 12 }}>
              0 = the animation&apos;s own length (3 s for still images). Every viewer holds the gift on screen this long.
            </p>

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
