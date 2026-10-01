import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { get } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { LevelAsset, type LevelKind } from './Levels'

export type LevelView = {
  level: number
  slug: string
  title?: string
  asset_url?: string
  asset_mime?: string
  threshold_coins: number
  next_coins?: number
}

export type LevelPerson = {
  id: string
  username: string
  full_name: string
  avatar_url?: string
  sent_coins: number
  sent_count: number
  received_coins: number
  received_count: number
  sender_level?: LevelView
  receiver_level?: LevelView
  sender_next: number
  receiver_next: number
}

type ListResp<T> = { items: T[]; next_cursor?: string }

/** Name + level picture as the app shows it next to a person. */
export function LevelBadge({ level, size = 22 }: { level?: LevelView; size?: number }) {
  if (!level) return <span className="muted">—</span>
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <LevelAsset url={level.asset_url || ''} mime={level.asset_mime || ''} size={size} label={`Lv ${level.slug}`} />
      <span style={{ fontSize: 12 }}>
        Lv {level.slug}
        {level.title ? <span className="muted"> · {level.title}</span> : null}
      </span>
    </span>
  )
}

/**
 * Who stands where on each ladder: everyone who has gifted (or been gifted),
 * highest lifetime total first, with their current level. A row opens the
 * person's gifting history.
 */
export function LevelUsersPage() {
  const { mediaBase } = useAuth()
  const navigate = useNavigate()
  const [kind, setKind] = useState<LevelKind>('sender')
  const [rows, setRows] = useState<LevelPerson[]>([])
  const [next, setNext] = useState('')
  const [q, setQ] = useState('')
  const [term, setTerm] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(
    async (cursor: string, append: boolean) => {
      append ? setMore(true) : setLoading(true)
      const params = new URLSearchParams({ kind, limit: '50' })
      if (term) params.set('q', term)
      if (cursor) {
        const [coins, user] = cursor.split('|')
        if (coins && user) {
          params.set('cursor_coins', coins)
          params.set('cursor_user', user)
        }
      }
      const r = await get<ListResp<LevelPerson>>(`/v1/admin/levels/users?${params}`)
      append ? setMore(false) : setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setRows((old) => (append ? [...old, ...(r.data.items || [])] : r.data.items || []))
      setNext(r.data.next_cursor || '')
    },
    [kind, term],
  )

  useEffect(() => {
    void load('', false)
  }, [load])

  const sender = kind === 'sender'

  return (
    <div className="users-page">
      <div className="page-head users-head">
        <div>
          <h1>Level users</h1>
          <p>Highest totals first. Open a row for that person’s gifting history.</p>
        </div>
      </div>

      <Card>
        <div className="withdraw-toolbar">
          <div className="tabs" style={{ marginBottom: 0 }}>
            <button type="button" className={`tab${sender ? ' is-active' : ''}`} onClick={() => setKind('sender')}>
              Senders
            </button>
            <button type="button" className={`tab${!sender ? ' is-active' : ''}`} onClick={() => setKind('receiver')}>
              Receivers
            </button>
          </div>
          <form
            className="users-toolbar"
            style={{ flex: 1, minWidth: 200 }}
            onSubmit={(e) => {
              e.preventDefault()
              setTerm(q.trim())
            }}
          >
            <label className="users-search">
              Search
              <input
                className="accounts-search-input"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="Username or name prefix…"
              />
            </label>
            <Button type="submit" size="sm">
              Search
            </Button>
            {term ? (
              <Button
                type="button"
                size="sm"
                variant="ghost"
                onClick={() => {
                  setQ('')
                  setTerm('')
                }}
              >
                Clear
              </Button>
            ) : null}
          </form>
        </div>

        {error ? <div className="err-banner">{error}</div> : null}

        {loading && rows.length === 0 ? (
          <PageLoader label="Loading…" />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th className="num">#</th>
                  <th>User</th>
                  <th className="num">{sender ? 'Sent' : 'Received'}</th>
                  <th className="num">Gifts</th>
                  <th>Level</th>
                  <th className="num">Next at</th>
                  <th className="num">{sender ? 'Received' : 'Sent'}</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="muted" style={{ padding: 20, textAlign: 'center' }}>
                      Nobody has {sender ? 'sent' : 'received'} a gift yet{term ? ` matching “${term}”` : ''}.
                    </td>
                  </tr>
                ) : (
                  rows.map((p, i) => {
                    const level = sender ? p.sender_level : p.receiver_level
                    const coins = sender ? p.sent_coins : p.received_coins
                    const count = sender ? p.sent_count : p.received_count
                    const nextAt = sender ? p.sender_next : p.receiver_next
                    const name = p.full_name || p.username
                    return (
                      <tr key={p.id} className="row-link" onClick={() => navigate(`/level-users/${p.id}`)}>
                        <td className="num muted">{i + 1}</td>
                        <td>
                          <span className="users-cell">
                            {p.avatar_url ? (
                              <img className="users-av" src={mediaUrl(mediaBase, p.avatar_url)} alt="" loading="lazy" />
                            ) : (
                              <span className="users-av users-av--letter">{name.slice(0, 1).toUpperCase()}</span>
                            )}
                            <span className="users-cell-text">
                              <strong>{p.full_name || '—'}</strong>
                              <span className="muted">@{p.username}</span>
                            </span>
                          </span>
                        </td>
                        <td className="num">
                          <strong>{coins.toLocaleString()}</strong>
                        </td>
                        <td className="num">{count.toLocaleString()}</td>
                        <td>
                          <LevelBadge level={level} />
                        </td>
                        <td className="num nowrap muted">
                          {nextAt > 0 ? nextAt.toLocaleString() : level ? 'Top' : '—'}
                        </td>
                        <td className="num muted">{(sender ? p.received_coins : p.sent_coins).toLocaleString()}</td>
                        <td className="nowrap" onClick={(e) => e.stopPropagation()}>
                          <Button size="sm" variant="secondary" onClick={() => navigate(`/level-users/${p.id}`)}>
                            Open
                          </Button>
                        </td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>
        )}

        {next ? (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 12 }}>
            <Button size="sm" variant="secondary" loading={more} onClick={() => void load(next, true)}>
              Load more
            </Button>
          </div>
        ) : null}
      </Card>
    </div>
  )
}
