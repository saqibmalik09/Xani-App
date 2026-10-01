import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { get, post, del } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { PageLoader } from '../ui/Loader'

type Rendition = { key: string; mime: string; w?: number; h?: number }

type PostItem = {
  id: string
  kind: string
  caption: string
  visibility?: string
  state?: string
  created_at: string
  media?: {
    kind: string
    state: string
    duration_ms?: number
    renditions?: Record<string, Rendition>
  }
}

type Person = {
  id: string
  username: string
  full_name: string
  avatar_url?: string
  followers?: number
}

type ListResp<T> = { items: T[]; next_cursor?: string }

function posterOf(p: PostItem, mediaBase: string): string {
  const r = p.media?.renditions || {}
  const still = r.poster || r.thumb || r.preview
  if (still?.key) return mediaUrl(mediaBase, still.key)
  const img = Object.values(r).find((x) => (x.mime || '').startsWith('image/'))
  return img?.key ? mediaUrl(mediaBase, img.key) : ''
}

function videoOf(p: PostItem, mediaBase: string): string {
  const r = p.media?.renditions || {}
  const ranked = Object.values(r)
    .filter((x) => (x.mime || '').startsWith('video/'))
    .sort((a, b) => (b.h || 0) - (a.h || 0))
  return ranked[0]?.key ? mediaUrl(mediaBase, ranked[0].key) : ''
}

export function AccountVideosPage() {
  return <AccountPostsPage title="Videos" kind="video" />
}

export function AccountPostsPageAll() {
  return <AccountPostsPage title="Posts" kind="" />
}

function AccountPostsPage({ title, kind }: { title: string; kind: string }) {
  const { id = '' } = useParams()
  const { mediaBase, can } = useAuth()
  const moderate = can('mod.remove_content')
  const [rows, setRows] = useState<PostItem[]>([])
  const [next, setNext] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [play, setPlay] = useState<PostItem | null>(null)
  const [busyId, setBusyId] = useState('')

  const load = useCallback(
    async (before: string, append: boolean) => {
      append ? setMore(true) : setLoading(true)
      const params = new URLSearchParams({ limit: '24' })
      if (kind) params.set('kind', kind)
      if (before) params.set('before', before)
      const r = await get<ListResp<PostItem>>(`/v1/admin/users/${id}/posts?${params}`)
      append ? setMore(false) : setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setRows((old) => (append ? [...old, ...(r.data.items || [])] : r.data.items || []))
      setNext(r.data.next_cursor || '')
    },
    [id, kind],
  )

  useEffect(() => {
    void load('', false)
  }, [load])

  const patchRow = (postId: string, restricted: boolean) => {
    setRows((old) =>
      old.map((r) =>
        r.id === postId
          ? { ...r, state: restricted ? 'restricted' : 'live', visibility: restricted ? r.visibility : 'public' }
          : r,
      ),
    )
    setPlay((p) =>
      p?.id === postId
        ? { ...p, state: restricted ? 'restricted' : 'live', visibility: restricted ? p.visibility : 'public' }
        : p,
    )
  }

  const restrict = async (p: PostItem, restricted: boolean) => {
    setBusyId(p.id)
    setError('')
    setOk('')
    const r = await post<{ post: PostItem }>(`/v1/admin/users/${id}/posts/${p.id}/restrict`, { restricted })
    setBusyId('')
    if (!r.ok) {
      setError(r.message)
      return
    }
    patchRow(p.id, restricted)
    setOk(restricted ? 'Restricted — only the author can see it.' : 'Unrestricted — it can show in feed again.')
  }

  const remove = async (p: PostItem) => {
    if (!window.confirm('Delete this post? It will disappear for everyone, including the author.')) return
    setBusyId(p.id)
    setError('')
    setOk('')
    const r = await del(`/v1/admin/users/${id}/posts/${p.id}`)
    setBusyId('')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setRows((old) => old.filter((x) => x.id !== p.id))
    if (play?.id === p.id) setPlay(null)
    setOk('Post deleted.')
  }

  if (loading && rows.length === 0) return <PageLoader label={`Loading ${title.toLowerCase()}…`} />

  const playSrc = play ? videoOf(play, mediaBase) : ''
  const playPoster = play ? posterOf(play, mediaBase) : ''

  return (
    <div className="rates-page">
      <div className="rates-toolbar">
        <div className="page-head" style={{ marginBottom: 0 }}>
          <h1>{title}</h1>
          <p>
            {kind === 'video'
              ? 'Videos this account posted. Restrict keeps it on the author only — not in feed or other listings.'
              : 'Everything this account published. Restrict keeps it on the author only — not in feed or other listings.'}
          </p>
        </div>
        <Link to={`/accounts?id=${id}`}>
          <Button size="sm" variant="secondary">
            Back
          </Button>
        </Link>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      {rows.length === 0 ? (
        <p className="muted">No {title.toLowerCase()} yet.</p>
      ) : (
        <div className="account-media-grid">
          {rows.map((p) => {
            const poster = posterOf(p, mediaBase)
            const vid = videoOf(p, mediaBase)
            const restricted = p.state === 'restricted' || p.visibility === 'private'
            return (
              <article key={p.id} className="account-media-cell">
                <button type="button" className="account-media-open" onClick={() => setPlay(p)}>
                  {poster ? <img src={poster} alt="" /> : <span className="account-media-empty">{p.kind === 'text' ? 'Text' : p.kind}</span>}
                  {vid ? <span className="account-media-play">Play</span> : null}
                  {p.caption ? <span className="account-media-cap">{p.caption}</span> : null}
                </button>
                {restricted ? <span className="account-media-badge">Restricted</span> : null}
                {moderate ? (
                  <div className="account-media-acts">
                    <Button
                      size="sm"
                      variant="secondary"
                      loading={busyId === p.id}
                      onClick={() => void restrict(p, !restricted)}
                    >
                      {restricted ? 'Unrestrict' : 'Restrict'}
                    </Button>
                    <Button size="sm" variant="danger" loading={busyId === p.id} onClick={() => void remove(p)}>
                      Delete
                    </Button>
                  </div>
                ) : null}
              </article>
            )
          })}
        </div>
      )}
      {next ? (
        <div style={{ display: 'flex', justifyContent: 'center' }}>
          <Button size="sm" variant="ghost" loading={more} onClick={() => void load(next, true)}>
            Load more
          </Button>
        </div>
      ) : null}

      {play ? (
        <div className="admin-modal-backdrop" role="presentation" onClick={() => setPlay(null)}>
          <div className="admin-modal account-play-modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h2>Video</h2>
            {playSrc ? (
              <video className="account-play-video" src={playSrc} poster={playPoster || undefined} controls autoPlay playsInline />
            ) : playPoster ? (
              <img className="account-play-video" src={playPoster} alt="" />
            ) : (
              <p className="muted">{play.caption || 'This post has no playable media yet.'}</p>
            )}
            {play.caption ? <p className="muted" style={{ marginBottom: 0 }}>{play.caption}</p> : null}
            <div className="admin-modal-actions">
              {moderate ? (
                <>
                  <Button
                    size="sm"
                    variant="secondary"
                    loading={busyId === play.id}
                    onClick={() =>
                      void restrict(
                        play,
                        !(play.state === 'restricted' || play.visibility === 'private'),
                      )
                    }
                  >
                    {play.state === 'restricted' || play.visibility === 'private' ? 'Unrestrict' : 'Restrict'}
                  </Button>
                  <Button size="sm" variant="danger" loading={busyId === play.id} onClick={() => void remove(play)}>
                    Delete
                  </Button>
                </>
              ) : null}
              <Button size="sm" variant="secondary" onClick={() => setPlay(null)}>
                Close
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}

export function AccountFollowersPage() {
  return <AccountPeoplePage title="Followers" dir="followers" />
}

export function AccountFollowingPage() {
  return <AccountPeoplePage title="Following" dir="following" />
}

function AccountPeoplePage({ title, dir }: { title: string; dir: 'followers' | 'following' }) {
  const { id = '' } = useParams()
  const { mediaBase } = useAuth()
  const navigate = useNavigate()
  const [rows, setRows] = useState<Person[]>([])
  const [next, setNext] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(
    async (cursor: string, append: boolean) => {
      append ? setMore(true) : setLoading(true)
      const params = new URLSearchParams({ limit: '40' })
      if (cursor) params.set('cursor', cursor)
      const r = await get<ListResp<Person>>(`/v1/admin/users/${id}/${dir}?${params}`)
      append ? setMore(false) : setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setRows((old) => (append ? [...old, ...(r.data.items || [])] : r.data.items || []))
      setNext(r.data.next_cursor || '')
    },
    [id, dir],
  )

  useEffect(() => {
    void load('', false)
  }, [load])

  if (loading && rows.length === 0) return <PageLoader label={`Loading ${title.toLowerCase()}…`} />

  return (
    <div className="rates-page">
      <div className="rates-toolbar">
        <div className="page-head" style={{ marginBottom: 0 }}>
          <h1>{title}</h1>
          <p>People on this list. Open anyone to search them in Accounts.</p>
        </div>
        <Link to={`/accounts?id=${id}`}>
          <Button size="sm" variant="secondary">
            Back
          </Button>
        </Link>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {rows.length === 0 ? (
        <p className="muted">Nobody on this list yet.</p>
      ) : (
        <div className="table-wrap rates-table-wrap">
          <table className="data-table rates-table">
            <thead>
              <tr>
                <th>Person</th>
                <th className="num">Followers</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((p) => {
                const face = mediaUrl(mediaBase, p.avatar_url || '')
                return (
                  <tr key={p.id} className="is-click" onClick={() => navigate(`/accounts?id=${p.id}`)}>
                    <td>
                      <span className="account-person">
                        {face ? (
                          <img src={face} alt="" className="accounts-suggest-av" />
                        ) : (
                          <span className="accounts-suggest-av accounts-suggest-av--letter">
                            {(p.full_name || p.username || '?').slice(0, 1).toUpperCase()}
                          </span>
                        )}
                        <span>
                          <strong>@{p.username}</strong>
                          {p.full_name ? <span className="muted"> {p.full_name}</span> : null}
                        </span>
                      </span>
                    </td>
                    <td className="num">{(p.followers || 0).toLocaleString()}</td>
                  </tr>
                )
              })}
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
    </div>
  )
}
