import { useCallback, useEffect, useRef, useState, type CSSProperties, type FormEvent } from 'react'
import { del, get, patch, post, putBytes } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

export type LevelKind = 'sender' | 'receiver'

export type LevelTier = {
  id: string
  kind: LevelKind
  slug: string
  title: string
  threshold_coins: number
  asset_key: string
  asset_mime: string
  status: number
  created_at: string
  updated_at: string
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

const ASSET_ACCEPT = 'image/png,image/webp,image/gif,image/apng,image/svg+xml,.svga,.json,application/x-svga,application/json'

/** True when the asset is a static picture a browser <img> can show. */
export function isImageAsset(mime: string, key: string): boolean {
  if (mime.startsWith('image/')) return true
  const k = key.toLowerCase()
  return k.endsWith('.png') || k.endsWith('.webp') || k.endsWith('.gif') || k.endsWith('.svg') || k.endsWith('.jpg')
}

/** Level picture as the dashboard can show it: the image, or a chip for an animated file. */
export function LevelAsset({ url, mime, size = 28, label }: { url: string; mime: string; size?: number; label?: string }) {
  if (!url) return null
  if (isImageAsset(mime, url)) {
    return <img src={url} alt="" style={{ width: size, height: size, objectFit: 'contain' }} loading="lazy" />
  }
  return (
    <span
      className="status-pill on"
      title={url.split('/').pop()}
      style={{ height: size, display: 'inline-flex', alignItems: 'center', padding: '0 8px', fontSize: 11 }}
    >
      {label || (mime.includes('json') ? 'Lottie' : 'SVGA')}
    </span>
  )
}

/**
 * Gifting levels. Two ladders — Sender (coins given) and Receiver (coins
 * received) — each a list of rungs with a slug, one picture (static or
 * animated) and the lifetime coin amount that unlocks it. A person reaches
 * the next rung automatically the moment their total crosses the bar, and
 * the picture next to their name changes everywhere at once.
 */
export function LevelsPage() {
  const [kind, setKind] = useState<LevelKind>('sender')
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head">
        <h1>Levels</h1>
        <p>
          Sender levels rank people by coins they have gifted; receiver levels by coins received. Each level has one
          picture (PNG, WebP, GIF, SVGA or Lottie) and the coin amount that unlocks it. Add as many as you need.
        </p>
      </div>
      <div className="tabs">
        <button type="button" className={`tab${kind === 'sender' ? ' is-active' : ''}`} onClick={() => setKind('sender')}>
          Sender levels
        </button>
        <button
          type="button"
          className={`tab${kind === 'receiver' ? ' is-active' : ''}`}
          onClick={() => setKind('receiver')}
        >
          Receiver levels
        </button>
      </div>
      <LadderPanel key={kind} kind={kind} />
    </div>
  )
}

function LadderPanel({ kind }: { kind: LevelKind }) {
  const { mediaBase } = useAuth()
  const [items, setItems] = useState<LevelTier[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)

  const [editing, setEditing] = useState<LevelTier | null>(null)
  const [creating, setCreating] = useState(false)
  const [slug, setSlug] = useState('')
  const [title, setTitle] = useState('')
  const [threshold, setThreshold] = useState('')
  const [status, setStatus] = useState(1)
  const [assetKey, setAssetKey] = useState('')
  const [assetMime, setAssetMime] = useState('')
  const assetRef = useRef<HTMLInputElement>(null)

  const load = useCallback(async () => {
    setLoading(true)
    const r = await get<ListResp<LevelTier>>(`/v1/admin/levels?kind=${kind}`)
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setItems(r.data.items || [])
  }, [kind])

  useEffect(() => {
    void load()
  }, [load])

  const fill = (t: LevelTier | null) => {
    setSlug(t?.slug ?? '')
    setTitle(t?.title ?? '')
    setThreshold(t ? String(t.threshold_coins) : '')
    setStatus(t?.status ?? 1)
    setAssetKey(t?.asset_key ?? '')
    setAssetMime(t?.asset_mime ?? '')
  }

  const startCreate = () => {
    setCreating(true)
    setEditing(null)
    fill(null)
    // Suggest the next rung number so a thousand-level ladder is quick to type.
    setSlug(String(items.length + 1))
    setError('')
    setOk('')
  }

  const startEdit = (t: LevelTier) => {
    setCreating(false)
    setEditing(t)
    fill(t)
    setError('')
    setOk('')
  }

  const onAssetPick = async (file: File | null) => {
    if (!file) return
    setBusy(true)
    setError('')
    const ext = file.name.includes('.') ? file.name.split('.').pop() : ''
    // Browsers report no type for .svga; the server resolves the extension.
    const r = await putBytes<{ key: string; mime: string }>(
      `/v1/admin/levels/upload?ext=${encodeURIComponent(ext || '')}`,
      file,
      file.type || 'application/octet-stream',
    )
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setAssetKey(r.data.key)
    setAssetMime(r.data.mime || file.type || '')
    setOk('Picture uploaded.')
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const coins = Number(threshold)
    if (!Number.isFinite(coins) || coins < 0) {
      setError('Coins must be a whole number, zero or more.')
      return
    }
    if (creating && !assetKey) {
      setError('Upload a picture for this level.')
      return
    }
    setBusy(true)
    setError('')
    setOk('')
    const body = {
      kind,
      slug: slug.trim(),
      title: title.trim(),
      threshold_coins: Math.floor(coins),
      asset_key: assetKey,
      asset_mime: assetMime,
      status,
    }
    const r = creating
      ? await post<LevelTier>('/v1/admin/levels', body)
      : await patch<LevelTier>(`/v1/admin/levels/${editing?.id}`, body)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(creating ? 'Level created.' : 'Saved.')
    setCreating(false)
    setEditing(r.data)
    fill(r.data)
    await load()
  }

  const onDelete = async () => {
    if (!editing) return
    if (!window.confirm(`Delete level “${editing.slug}”? People on it drop to the level below.`)) return
    setBusy(true)
    const r = await del(`/v1/admin/levels/${editing.id}`)
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

  if (loading && items.length === 0) return <PageLoader label="Loading levels…" />

  const assetUrl = mediaUrl(mediaBase, assetKey)
  const noun = kind === 'sender' ? 'sent' : 'received'

  return (
    <div className="catalog-split">
      <Card
        title={`${kind === 'sender' ? 'Sender' : 'Receiver'} ladder`}
        actions={
          <Button size="sm" onClick={startCreate}>
            + New level
          </Button>
        }
      >
        {error && !creating && !editing ? <div className="err-banner">{error}</div> : null}
        <div className="role-list">
          {items.map((t, i) => (
            <button
              key={t.id}
              type="button"
              className={`role-row${editing?.id === t.id ? ' is-active' : ''}`}
              onClick={() => startEdit(t)}
            >
              <div className="sticker-thumb" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <LevelAsset url={mediaUrl(mediaBase, t.asset_key)} mime={t.asset_mime} size={32} />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14 }}>
                  Level {i + 1} · {t.slug}
                  {t.title ? <span className="muted"> — {t.title}</span> : null}
                </div>
                <div className="muted" style={{ fontSize: 12 }}>
                  {t.threshold_coins.toLocaleString()} coins {noun} · {t.status === 1 ? 'Active' : 'Off'}
                </div>
              </div>
            </button>
          ))}
          {items.length === 0 ? <p className="muted">No levels yet. Create the first rung.</p> : null}
        </div>
      </Card>

      <Card title={creating ? 'New level' : editing ? 'Edit level' : 'Details'}>
        {error && (creating || editing) ? <div className="err-banner">{error}</div> : null}
        {ok ? <div className="ok-banner">{ok}</div> : null}
        {!creating && !editing ? (
          <p className="muted" style={{ margin: 0 }}>
            Select a level or create one. Levels are numbered by their coin amount, lowest first.
          </p>
        ) : (
          <form className="form-grid" onSubmit={(e) => void onSubmit(e)}>
            <Field label="Slug (shown as the level name)" value={slug} onChange={(e) => setSlug(e.target.value)} required />
            <Field label="Title (optional)" value={title} onChange={(e) => setTitle(e.target.value)} />
            <Field
              label={`Coins ${noun} to reach`}
              type="number"
              min={0}
              step={1}
              value={threshold}
              onChange={(e) => setThreshold(e.target.value)}
              required
            />
            <label className="field-label">
              Status
              <select style={selectStyle} value={status} onChange={(e) => setStatus(Number(e.target.value))}>
                <option value={1}>Active</option>
                <option value={0}>Inactive</option>
              </select>
            </label>
            <div className="brand-pick">
              <span className="brand-label">Picture (image or animation)</span>
              <button
                type="button"
                className="brand-thumb brand-thumb-lg"
                onClick={() => assetRef.current?.click()}
                disabled={busy}
              >
                {assetUrl ? (
                  isImageAsset(assetMime, assetKey) ? (
                    <img src={assetUrl} alt="" />
                  ) : (
                    <span>{assetKey.split('/').pop()}</span>
                  )
                ) : (
                  <span>Click to set</span>
                )}
              </button>
              <input
                ref={assetRef}
                type="file"
                accept={ASSET_ACCEPT}
                hidden
                onChange={(e) => void onAssetPick(e.target.files?.[0] || null)}
              />
              <span className="muted" style={{ fontSize: 11 }}>
                PNG / WebP / GIF for a still picture, SVGA or Lottie JSON for an animated one.
              </span>
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
