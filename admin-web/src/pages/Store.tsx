import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type FormEvent } from 'react'
import { del, get, patch, post, putBytes } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type StoreCat = {
  id: string
  parent_id?: string | null
  title: string
  description: string
  status: number
  sort_order: number
  child_count?: number
}

type StoreItem = {
  id: string
  category_id: string
  kind: string
  title: string
  description: string
  coin_price: number
  duration_days: number
  preview_key: string
  preview_mime: string
  asset_key: string
  asset_mime: string
  status: number
  sort_order: number
  created_at: string
  category_title?: string
}

type ItemPrice = {
  id?: string
  days: number
  coin_price: number
}

type ItemRow = { item: StoreItem; prices: ItemPrice[] }

/** The usual packages, one tap each; anything else via “+ Custom days”. */
const PRESET_DAYS: Array<[number, string]> = [
  [1, '1 day'],
  [7, '1 week'],
  [30, '1 month'],
  [90, '3 months'],
]

const dayLabel = (d: number) =>
  d === 0 ? 'Permanent' : PRESET_DAYS.find(([v]) => v === d)?.[1] || `${d} days`

const ITEM_KINDS: Array<[string, string]> = [
  ['frame', 'Avatar frame'],
  ['badge', 'Badge'],
  ['entrance', 'Entrance effect'],
  ['vip', 'VIP'],
  ['bubble', 'Chat bubble'],
]

/** Accepted kinds only — unknown / blank / title-cased values become a real option. */
function normalizeKind(raw: string | undefined | null): string {
  const k = (raw || '').trim().toLowerCase()
  if (ITEM_KINDS.some(([v]) => v === k)) return k
  return 'frame'
}

/**
 * When the admin named the category “Badges” / “Entrance effects”, new items
 * under it should open on that type — not stuck on Avatar frame.
 */
function kindFromCategoryTitle(title: string): string {
  const t = title.trim().toLowerCase()
  if (/\bbadge/.test(t)) return 'badge'
  if (/\bentrance|\bentry|\bjoin/.test(t)) return 'entrance'
  if (/\bvip/.test(t)) return 'vip'
  if (/\bbubble|\bchat\s*bubble/.test(t)) return 'bubble'
  if (/\bframe/.test(t)) return 'frame'
  return 'frame'
}

type ListResp<T> = { items: T[] }

const selectStyle: CSSProperties = {
  height: 36,
  width: '100%',
  borderRadius: 8,
  border: '1px solid var(--line)',
  padding: '0 10px',
  background: 'var(--surface)',
  color: 'var(--ink)',
  fontSize: 13,
  cursor: 'pointer',
}

type TreeNode = StoreCat & { children: TreeNode[]; depth: number }

function buildTree(flat: StoreCat[]): TreeNode[] {
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

export function StorePage() {
  const [items, setItems] = useState<StoreCat[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const [statusFilter, setStatusFilter] = useState('')

  const [editing, setEditing] = useState<StoreCat | null>(null)
  const [creating, setCreating] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState(1)
  const [parentId, setParentId] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    const params = new URLSearchParams()
    if (statusFilter !== '') params.set('status', statusFilter)
    const r = await get<ListResp<StoreCat>>(`/v1/admin/store-categories?${params}`)
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setItems(r.data.items || [])
  }, [statusFilter])

  useEffect(() => {
    void load()
  }, [load])

  const tree = useMemo(() => buildTree(items), [items])
  const flat = useMemo(() => flattenTree(tree), [tree])

  const startCreateRoot = () => {
    setCreating(true)
    setEditing(null)
    setTitle('')
    setDescription('')
    setStatus(1)
    setParentId('')
    setError('')
    setOk('')
  }

  const startCreateChild = (parent: StoreCat) => {
    setCreating(true)
    setEditing(null)
    setTitle('')
    setDescription('')
    setStatus(1)
    setParentId(parent.id)
    setError('')
    setOk('')
  }

  const startEdit = (c: StoreCat) => {
    setCreating(false)
    setEditing(c)
    setTitle(c.title)
    setDescription(c.description)
    setStatus(c.status)
    setParentId(c.parent_id || '')
    setError('')
    setOk('')
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const body = {
      title: title.trim(),
      description,
      status,
      sort_order: editing?.sort_order ?? 0,
      parent_id: parentId || undefined,
      clear_parent: !parentId && !!editing,
    }
    const r = creating
      ? await post<StoreCat>('/v1/admin/store-categories', {
          title: body.title,
          description: body.description,
          status: body.status,
          sort_order: body.sort_order,
          parent_id: body.parent_id,
        })
      : await patch<StoreCat>(`/v1/admin/store-categories/${editing?.id}`, body)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(creating ? 'Category created.' : 'Saved.')
    setCreating(false)
    setEditing(r.data)
    setParentId(r.data.parent_id || '')
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
    const r = await del(`/v1/admin/store-categories/${editing.id}`)
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

  if (loading && items.length === 0) return <PageLoader label="Loading store…" />

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head">
        <h1>Store</h1>
        <p>
          Build the shop tree: create a top category, then add sub-categories under it. Toggle Active / Inactive on any
          level.
        </p>
      </div>

      <div className="catalog-split">
        <Card
          title="Category tree"
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
          </div>

          {error && !creating && !editing ? <div className="err-banner">{error}</div> : null}

          {flat.length === 0 ? (
            <p className="muted">No categories yet. Add a top category to start.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {flat.map((n) => (
                <div
                  key={n.id}
                  className={`tree-row${editing?.id === n.id && !creating ? ' is-selected' : ''}`}
                  style={{ paddingLeft: 10 + n.depth * 18 }}
                  onClick={() => startEdit(n)}
                >
                  <span style={{ flex: 1, minWidth: 0 }}>
                    <strong style={{ fontSize: 14 }}>{n.title}</strong>
                    {n.child_count ? (
                      <span className="muted" style={{ marginLeft: 8, fontSize: 12 }}>
                        {n.child_count} sub
                      </span>
                    ) : null}
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
                ? 'New sub-category'
                : 'New top category'
              : editing
                ? 'Edit category'
                : 'Details'
          }
        >
          {!creating && !editing ? (
            <p className="muted">Select a category in the tree, or create a top / sub category.</p>
          ) : (
            <form className="form-grid" onSubmit={(e) => void onSubmit(e)}>
              {error ? <div className="err-banner">{error}</div> : null}
              {ok ? <div className="ok-banner">{ok}</div> : null}

              <Field label="Title *" value={title} onChange={(e) => setTitle(e.target.value)} required />
              <TextArea label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />

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
                <span className="muted" style={{ fontWeight: 400, fontSize: 12 }}>
                  Pick a parent to nest this under another category.
                </span>
              </label>

              <label className="field-label">
                Status
                <select value={status} onChange={(e) => setStatus(Number(e.target.value))} style={selectStyle}>
                  <option value={1}>Active</option>
                  <option value={0}>Inactive</option>
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

      {editing && !creating ? <ItemsPanel key={editing.id} category={editing} /> : null}
    </div>
  )
}

/**
 * The items sold under the selected category: avatar frames, VIP passes,
 * entrance effects, chat bubbles. Each item carries a preview image (the shop
 * card) and the asset the app wears or plays — PNG/WebP for a static frame,
 * SVGA / Lottie JSON / MP4 for an animated one.
 */
function ItemsPanel({ category }: { category: StoreCat }) {
  const { mediaBase } = useAuth()
  const [items, setItems] = useState<ItemRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)

  const [editing, setEditing] = useState<StoreItem | null>(null)
  const [creating, setCreating] = useState(false)
  const [kind, setKind] = useState('frame')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [price, setPrice] = useState(0)
  const [days, setDays] = useState(0)
  const [status, setStatus] = useState(1)
  const [previewKey, setPreviewKey] = useState('')
  const [previewMime, setPreviewMime] = useState('')
  const [assetKey, setAssetKey] = useState('')
  const [assetMime, setAssetMime] = useState('')
  const [assetName, setAssetName] = useState('')
  const [packages, setPackages] = useState<ItemPrice[]>([])
  const previewRef = useRef<HTMLInputElement>(null)
  const assetRef = useRef<HTMLInputElement>(null)

  const load = useCallback(async () => {
    setLoading(true)
    const r = await get<ListResp<ItemRow>>(`/v1/admin/store-items?category_id=${category.id}`)
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setItems(r.data.items || [])
  }, [category.id])

  useEffect(() => {
    void load()
  }, [load])

  const fillForm = (it: StoreItem | null, prices: ItemPrice[] = []) => {
    setPackages(prices.map((p) => ({ days: p.days, coin_price: p.coin_price })))
    setKind(it ? normalizeKind(it.kind) : kindFromCategoryTitle(category.title))
    setTitle(it?.title || '')
    setDescription(it?.description || '')
    setPrice(it?.coin_price ?? 0)
    setDays(it?.duration_days ?? 0)
    setStatus(it?.status ?? 1)
    setPreviewKey(it?.preview_key || '')
    setPreviewMime(it?.preview_mime || '')
    setAssetKey(it?.asset_key || '')
    setAssetMime(it?.asset_mime || '')
    setAssetName(it?.asset_key ? it.asset_key.split('/').pop() || it.asset_key : '')
  }

  const startCreate = () => {
    setCreating(true)
    setEditing(null)
    fillForm(null)
    setError('')
    setOk('')
  }

  const startEdit = (row: ItemRow) => {
    setCreating(false)
    setEditing(row.item)
    fillForm(row.item, row.prices)
    setError('')
    setOk('')
  }

  const upload = async (k: 'preview' | 'asset', file: File) => {
    const ext = file.name.includes('.') ? file.name.split('.').pop() : ''
    // Browsers report no type for .svga; the server resolves the extension.
    const r = await putBytes<{ key: string; mime: string }>(
      `/v1/admin/store-items/upload?kind=${k}&ext=${encodeURIComponent((ext || '').toLowerCase())}`,
      file,
      file.type || 'application/octet-stream',
    )
    if (!r.ok) throw new Error(r.message)
    return r.data
  }

  const onPreviewPick = async (file: File | null) => {
    if (!file) return
    setBusy(true)
    setError('')
    try {
      const up = await upload('preview', file)
      setPreviewKey(up.key)
      setPreviewMime(up.mime)
      setOk('Preview uploaded.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed')
    }
    setBusy(false)
  }

  const onAssetPick = async (file: File | null) => {
    if (!file) return
    setBusy(true)
    setError('')
    try {
      const up = await upload('asset', file)
      setAssetKey(up.key)
      setAssetMime(up.mime)
      setAssetName(file.name || up.key)
      setOk('Asset uploaded.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed')
    }
    setBusy(false)
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const body = {
      category_id: category.id,
      kind: normalizeKind(kind),
      title: title.trim(),
      description,
      coin_price: price,
      duration_days: days,
      status,
      preview_key: previewKey,
      preview_mime: previewMime,
      asset_key: assetKey,
      asset_mime: assetMime,
      sort_order: editing?.sort_order ?? 0,
      prices: packages,
    }
    const r = creating
      ? await post<StoreItem>('/v1/admin/store-items', body)
      : await patch<StoreItem>(`/v1/admin/store-items/${editing?.id}`, body)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(creating ? 'Item created.' : 'Saved.')
    setCreating(false)
    // Keep the kind we just saved if the ack omits it (older servers).
    const saved = { ...r.data, kind: normalizeKind(r.data.kind || kind) }
    setEditing(saved)
    fillForm(saved, packages)
    await load()
  }

  const onDelete = async () => {
    if (!editing) return
    if (!window.confirm(`Delete “${editing.title}”?`)) return
    setBusy(true)
    const r = await del(`/v1/admin/store-items/${editing.id}`)
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

  const kindLabel = (k: string) => ITEM_KINDS.find(([v]) => v === normalizeKind(k))?.[1] || k || 'Avatar frame'
  const previewUrl = mediaUrl(mediaBase, previewKey)

  return (
    <div className="catalog-split">
      <Card
        title={`Items in “${category.title}”`}
        actions={
          <Button size="sm" onClick={startCreate}>
            + New item
          </Button>
        }
      >
        {loading ? (
          <PageLoader label="Loading items…" />
        ) : (
          <div className="role-list">
            {items.map((row) => (
              <button
                key={row.item.id}
                type="button"
                className={`role-row${editing?.id === row.item.id ? ' is-active' : ''}`}
                onClick={() => startEdit(row)}
              >
                <div className="sticker-thumb">
                  {row.item.preview_key ? <img src={mediaUrl(mediaBase, row.item.preview_key)} alt="" /> : null}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 700, fontSize: 14 }}>{row.item.title}</div>
                  <div className="muted" style={{ fontSize: 12 }}>
                    {kindLabel(row.item.kind)} ·{' '}
                    {row.prices.length > 0
                      ? row.prices.map((p) => `${dayLabel(p.days)} ${p.coin_price}c`).join(' · ')
                      : `${row.item.coin_price} coins`}{' '}
                    · {row.item.status === 1 ? 'Active' : 'Off'}
                  </div>
                </div>
              </button>
            ))}
            {items.length === 0 ? <p className="muted">No items in this category yet.</p> : null}
          </div>
        )}
      </Card>

      <Card title={creating ? 'New item' : editing ? 'Edit item' : 'Item details'}>
        {error ? <div className="err-banner">{error}</div> : null}
        {ok ? <div className="ok-banner">{ok}</div> : null}
        {!creating && !editing ? (
          <p className="muted" style={{ margin: 0 }}>
            Select an item or create one — an avatar frame, a VIP pass, an entrance effect.
          </p>
        ) : (
          <form className="form-grid" onSubmit={(e) => void onSubmit(e)}>
            <Field label="Title *" value={title} onChange={(e) => setTitle(e.target.value)} required />
            <label className="field-label">
              Type
              <select
                name="kind"
                style={selectStyle}
                value={normalizeKind(kind)}
                onChange={(e) => setKind(e.target.value)}
              >
                {ITEM_KINDS.map(([v, label]) => (
                  <option key={v} value={v}>
                    {label}
                  </option>
                ))}
              </select>
              <span className="muted" style={{ fontWeight: 400, fontSize: 12 }}>
                What the app does with this item: frame around the avatar, badge next to the name,
                entrance effect when they join a LIVE, and so on. Change it before saving.
              </span>
            </label>
            <TextArea label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
            <Field
              label="Price (coins)"
              type="number"
              min={0}
              value={String(price)}
              onChange={(e) => setPrice(Math.max(0, Number(e.target.value) || 0))}
            />
            <Field
              label="Validity (days, 0 = permanent)"
              type="number"
              min={0}
              value={String(days)}
              onChange={(e) => setDays(Math.max(0, Number(e.target.value) || 0))}
            />
            <label className="field-label">
              Status
              <select style={selectStyle} value={status} onChange={(e) => setStatus(Number(e.target.value))}>
                <option value={1}>Active</option>
                <option value={0}>Inactive</option>
              </select>
            </label>
            <div className="field-label" style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <span>Duration packages (what users buy)</span>
              {packages.map((p, i) => (
                <div key={i} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <input
                    type="number"
                    min={0}
                    value={String(p.days)}
                    onChange={(e) => {
                      const v = Math.max(0, Number(e.target.value) || 0)
                      setPackages((list) => list.map((x, j) => (j === i ? { ...x, days: v } : x)))
                    }}
                    style={{ ...selectStyle, width: 90 }}
                  />
                  <span className="muted" style={{ fontSize: 12, width: 76 }}>
                    {dayLabel(p.days)}
                  </span>
                  <input
                    type="number"
                    min={0}
                    value={String(p.coin_price)}
                    onChange={(e) => {
                      const v = Math.max(0, Number(e.target.value) || 0)
                      setPackages((list) => list.map((x, j) => (j === i ? { ...x, coin_price: v } : x)))
                    }}
                    style={{ ...selectStyle, width: 110 }}
                  />
                  <span className="muted" style={{ fontSize: 12 }}>
                    coins
                  </span>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    style={{ minWidth: 0, padding: '0 8px' }}
                    onClick={() => setPackages((list) => list.filter((_, j) => j !== i))}
                  >
                    ✕
                  </Button>
                </div>
              ))}
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                {PRESET_DAYS.map(([d, label]) => (
                  <Button
                    key={d}
                    type="button"
                    size="sm"
                    variant="ghost"
                    disabled={packages.some((p) => p.days === d)}
                    onClick={() => setPackages((list) => [...list, { days: d, coin_price: price || 0 }])}
                  >
                    + {label}
                  </Button>
                ))}
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() => setPackages((list) => [...list, { days: 0, coin_price: price || 0 }])}
                >
                  + Custom days
                </Button>
              </div>
              <span className="muted" style={{ fontWeight: 400, fontSize: 12 }}>
                Each package is one buyable option: its days (0 = permanent) and its own price.
              </span>
            </div>
            <div className="brand-pick">
              <span className="brand-label">Preview image (shop card)</span>
              <button
                type="button"
                className="brand-thumb brand-thumb-lg"
                onClick={() => previewRef.current?.click()}
                disabled={busy}
              >
                {previewUrl ? <img src={previewUrl} alt="" /> : <span>Click to set</span>}
              </button>
              <input
                ref={previewRef}
                type="file"
                accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml"
                hidden
                onChange={(e) => void onPreviewPick(e.target.files?.[0] || null)}
              />
            </div>
            <div className="brand-pick">
              <span className="brand-label">Asset (worn / played in-app)</span>
              <Button type="button" size="sm" variant="ghost" onClick={() => assetRef.current?.click()} disabled={busy}>
                {assetKey ? 'Replace file' : 'Upload file'}
              </Button>
              {assetKey ? (
                <span className="muted" style={{ fontSize: 12 }}>
                  {assetName} · {assetMime || 'file'}
                </span>
              ) : (
                <span className="muted" style={{ fontSize: 12 }}>
                  PNG / WebP for static, SVGA / Lottie JSON / MP4 for animated.
                </span>
              )}
              <input
                ref={assetRef}
                type="file"
                accept="image/*,.svga,.json,video/mp4,video/webm"
                hidden
                onChange={(e) => void onAssetPick(e.target.files?.[0] || null)}
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
