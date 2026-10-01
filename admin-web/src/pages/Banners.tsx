import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type FormEvent } from 'react'
import { del, get, patch, post, putBytes } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type BannerCat = {
  id: string
  parent_id?: string | null
  title: string
  description: string
  image_key: string
  placement: string
  status: number
  sort_order: number
  child_count?: number
}

type ListResp<T> = { items: T[] }

const selectStyle: CSSProperties = {
  height: 36,
  borderRadius: 8,
  border: '1px solid var(--line)',
  padding: '0 10px',
  background: 'var(--surface)',
  fontSize: 13,
}

/** Where the banner appears in the app. `live_browse` = Live tab, under All/Video/Audio + Country. */
const PLACEMENTS = [
  {
    value: 'live_browse',
    label: 'Live browse',
    hint: 'Under All / Video / Audio + Country on the Live tab — image carousel only (no event copy).',
  },
  { value: 'splash', label: 'Splash screen', hint: 'Launch / splash surface.' },
  { value: 'home', label: 'Home', hint: 'For You / home surfaces.' },
  { value: 'promo', label: 'Promo', hint: 'General promo slots.' },
  { value: 'onboarding', label: 'Onboarding', hint: 'First-run / onboarding.' },
  { value: 'other', label: 'Other', hint: 'Unspecified surface.' },
]

const DEFAULT_PLACEMENT = 'live_browse'

type TreeNode = BannerCat & { children: TreeNode[]; depth: number }

function buildTree(flat: BannerCat[]): TreeNode[] {
  const map = new Map<string, TreeNode>()
  for (const c of flat) {
    map.set(c.id, { ...c, children: [], depth: 0 })
  }
  const roots: TreeNode[] = []
  for (const c of flat) {
    const node = map.get(c.id)!
    if (c.parent_id && map.has(c.parent_id)) {
      const parent = map.get(c.parent_id)!
      node.depth = parent.depth + 1
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  }
  const sortRec = (nodes: TreeNode[]) => {
    nodes.sort((a, b) => a.sort_order - b.sort_order || a.title.localeCompare(b.title))
    nodes.forEach((n) => sortRec(n.children))
  }
  sortRec(roots)
  return roots
}

function flattenTree(nodes: TreeNode[]): TreeNode[] {
  const out: TreeNode[] = []
  const walk = (list: TreeNode[]) => {
    for (const n of list) {
      out.push(n)
      walk(n.children)
    }
  }
  walk(nodes)
  return out
}

export function BannersPage() {
  const { mediaBase } = useAuth()
  const [items, setItems] = useState<BannerCat[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const [statusFilter, setStatusFilter] = useState('')
  const [placementFilter, setPlacementFilter] = useState('')

  const [editing, setEditing] = useState<BannerCat | null>(null)
  const [creating, setCreating] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState(1)
  const [parentId, setParentId] = useState('')
  const [placement, setPlacement] = useState(DEFAULT_PLACEMENT)
  const [sortOrder, setSortOrder] = useState(1)
  const [imageKey, setImageKey] = useState('')
  const imageRef = useRef<HTMLInputElement>(null)

  const placementMeta = PLACEMENTS.find((p) => p.value === placement) ?? PLACEMENTS[0]

  const load = useCallback(async () => {
    setLoading(true)
    const params = new URLSearchParams()
    if (statusFilter !== '') params.set('status', statusFilter)
    if (placementFilter) params.set('placement', placementFilter)
    const r = await get<ListResp<BannerCat>>(`/v1/admin/banner-categories?${params}`)
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setItems(r.data.items || [])
  }, [statusFilter, placementFilter])

  useEffect(() => {
    void load()
  }, [load])

  const tree = useMemo(() => buildTree(items), [items])
  const flat = useMemo(() => flattenTree(tree), [tree])

  const nextOrderFor = (place: string) => {
    const same = items.filter((i) => i.placement === place)
    if (same.length === 0) return 1
    return Math.max(...same.map((i) => i.sort_order || 0)) + 1
  }

  const startCreateRoot = () => {
    setCreating(true)
    setEditing(null)
    setTitle('')
    setDescription('')
    setStatus(1)
    setParentId('')
    setPlacement(DEFAULT_PLACEMENT)
    setSortOrder(nextOrderFor(DEFAULT_PLACEMENT))
    setImageKey('')
    setError('')
    setOk('')
  }

  const startCreateChild = (parent: BannerCat) => {
    const place = parent.placement || DEFAULT_PLACEMENT
    setCreating(true)
    setEditing(null)
    setTitle('')
    setDescription('')
    setStatus(1)
    setParentId(parent.id)
    setPlacement(place)
    setSortOrder(nextOrderFor(place))
    setImageKey('')
    setError('')
    setOk('')
  }

  const startEdit = (c: BannerCat) => {
    setCreating(false)
    setEditing(c)
    setTitle(c.title)
    setDescription(c.description)
    setStatus(c.status)
    setParentId(c.parent_id || '')
    setPlacement(c.placement || DEFAULT_PLACEMENT)
    setSortOrder(c.sort_order > 0 ? c.sort_order : 1)
    setImageKey(c.image_key || '')
    setError('')
    setOk('')
  }

  const onImagePick = async (file: File | null) => {
    if (!file) return
    setBusy(true)
    setError('')
    const ext = file.name.includes('.') ? file.name.split('.').pop() : ''
    const r = await putBytes<{ key: string }>(
      `/v1/admin/banners/upload?ext=${encodeURIComponent(ext || '')}`,
      file,
      file.type || 'application/octet-stream',
    )
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setImageKey(r.data.key)
    setOk('Image uploaded. Save to apply.')
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const order = Math.max(1, Math.min(999, Number(sortOrder) || 1))
    const body = {
      title: title.trim(),
      description,
      status,
      sort_order: order,
      parent_id: parentId || undefined,
      clear_parent: !parentId && !!editing,
      placement,
      image_key: imageKey,
      clear_image: !imageKey && !!editing,
    }
    const r = creating
      ? await post<BannerCat>('/v1/admin/banner-categories', {
          title: body.title,
          description: body.description,
          status: body.status,
          sort_order: body.sort_order,
          parent_id: body.parent_id,
          placement: body.placement,
          image_key: body.image_key,
        })
      : await patch<BannerCat>(`/v1/admin/banner-categories/${editing?.id}`, body)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(creating ? 'Banner created.' : 'Saved.')
    setCreating(false)
    setEditing(r.data)
    setParentId(r.data.parent_id || '')
    setPlacement(r.data.placement || DEFAULT_PLACEMENT)
    setSortOrder(r.data.sort_order > 0 ? r.data.sort_order : 1)
    setImageKey(r.data.image_key || '')
    await load()
  }

  const onDelete = async () => {
    if (!editing) return
    const kids = editing.child_count ?? 0
    const msg =
      kids > 0
        ? `Delete “${editing.title}” and its ${kids} sub-categor${kids === 1 ? 'y' : 'ies'}?`
        : `Delete “${editing.title}”?`
    if (!window.confirm(msg)) return
    setBusy(true)
    const r = await del(`/v1/admin/banner-categories/${editing.id}`)
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

  const parentOptions = flat.filter((n) => !editing || n.id !== editing.id)
  const imageUrl = mediaUrl(mediaBase, imageKey)

  if (loading && items.length === 0) return <PageLoader label="Loading banners…" />

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head">
        <h1>Banners</h1>
        <p>
          Promo images by placement. Use <strong>Live browse</strong> for the carousel under All / Video / Audio +
          Country on the Live tab. Set order 1, 2, 3… — the app loops them. Update or delete anytime; apps refresh
          within about a minute and keep images cached until the file changes.
        </p>
      </div>

      <div className="catalog-split">
        <Card
          title="Banner tree"
          actions={
            <Button size="sm" onClick={startCreateRoot}>
              + Top category
            </Button>
          }
        >
          <div className="filters filters-compact" style={{ marginBottom: 10 }}>
            <label className="field-label" style={{ maxWidth: 160 }}>
              Show
              <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={selectStyle}>
                <option value="">All</option>
                <option value="1">Active only</option>
                <option value="0">Inactive only</option>
              </select>
            </label>
            <label className="field-label" style={{ maxWidth: 180 }}>
              Placement
              <select value={placementFilter} onChange={(e) => setPlacementFilter(e.target.value)} style={selectStyle}>
                <option value="">All</option>
                {PLACEMENTS.map((p) => (
                  <option key={p.value} value={p.value}>
                    {p.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {error && !creating && !editing ? <div className="err-banner">{error}</div> : null}

          {flat.length === 0 ? (
            <p className="muted">No banners yet. Add a top category (e.g. Splash), then add sub-banners under it.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {flat.map((n) => (
                <div
                  key={n.id}
                  className={`tree-row${editing?.id === n.id && !creating ? ' is-selected' : ''}`}
                  style={{ paddingLeft: 10 + n.depth * 18 }}
                  onClick={() => startEdit(n)}
                >
                  <div className="banner-tree-thumb">
                    {n.image_key ? <img src={mediaUrl(mediaBase, n.image_key)} alt="" /> : null}
                  </div>
                  <span style={{ flex: 1, minWidth: 0 }}>
                    <strong style={{ fontSize: 14 }}>{n.title}</strong>
                    <span className="muted" style={{ marginLeft: 8, fontSize: 12 }}>
                      #{n.sort_order || 0} · {n.placement || '—'}
                      {n.child_count ? ` · ${n.child_count} sub` : ''}
                    </span>
                  </span>
                  <span className={`status-pill ${n.status === 1 ? 'on' : 'off'}`}>
                    {n.status === 1 ? 'Active' : 'Off'}
                  </span>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    style={{ minWidth: 0, padding: '0 8px' }}
                    onClick={(e) => {
                      e.stopPropagation()
                      startCreateChild(n)
                    }}
                  >
                    + Sub
                  </Button>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card
          title={
            creating
              ? parentId
                ? 'New sub-banner'
                : 'New top category'
              : editing
                ? 'Edit banner'
                : 'Details'
          }
        >
          {!creating && !editing ? (
            <p className="muted">Select a banner in the tree, or create a top / sub category.</p>
          ) : (
            <form className="form-grid" onSubmit={(e) => void onSubmit(e)}>
              {error ? <div className="err-banner">{error}</div> : null}
              {ok ? <div className="ok-banner">{ok}</div> : null}

              <Field label="Title *" value={title} onChange={(e) => setTitle(e.target.value)} required />
              <TextArea label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />

              <label className="field-label">
                Placement (where in app)
                <select
                  value={placement}
                  onChange={(e) => {
                    const next = e.target.value
                    setPlacement(next)
                    if (creating) setSortOrder(nextOrderFor(next))
                  }}
                  style={selectStyle}
                >
                  {PLACEMENTS.map((p) => (
                    <option key={p.value} value={p.value}>
                      {p.label}
                    </option>
                  ))}
                </select>
                <span className="muted" style={{ fontWeight: 400, fontSize: 12 }}>
                  {placementMeta.hint} Apps load via /v1/banners?placement={placement}.
                </span>
              </label>

              <Field
                label="Order *"
                type="number"
                min={1}
                max={999}
                value={String(sortOrder)}
                onChange={(e) => setSortOrder(Math.max(1, Math.min(999, Number(e.target.value) || 1)))}
                hint="Carousel order: 1 plays first, then 2, 3… then repeats from 1."
                required
              />

              <label className="field-label">
                Parent category
                <select value={parentId} onChange={(e) => setParentId(e.target.value)} style={selectStyle}>
                  <option value="">None — top level</option>
                  {parentOptions.map((n) => (
                    <option key={n.id} value={n.id}>
                      {'—'.repeat(n.depth)} {n.title}
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

              <div className="brand-pick">
                <span className="brand-label">Banner image</span>
                <button
                  type="button"
                  className="brand-thumb brand-thumb-banner"
                  onClick={() => imageRef.current?.click()}
                  disabled={busy}
                >
                  {imageUrl ? <img src={imageUrl} alt="" /> : <span>Click to set</span>}
                </button>
                <input
                  ref={imageRef}
                  type="file"
                  accept="image/png,image/jpeg,image/webp,image/gif"
                  hidden
                  onChange={(e) => void onImagePick(e.target.files?.[0] || null)}
                />
                {imageKey ? (
                  <Button type="button" size="sm" variant="ghost" onClick={() => setImageKey('')} disabled={busy}>
                    Clear image
                  </Button>
                ) : null}
              </div>

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
