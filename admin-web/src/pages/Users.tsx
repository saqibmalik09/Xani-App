import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { get } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card } from '../ui/Field'
import { Loader, PageLoader } from '../ui/Loader'

type UserRow = {
  id: string
  username: string
  full_name: string
  avatar_url: string
  coins: number
  diamonds: number
  created_at: string
}

type ListResp = { items: UserRow[]; next_cursor?: string }

function fmtJoined(iso: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

export function UsersPage() {
  const { mediaBase } = useAuth()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  const [rows, setRows] = useState<UserRow[]>([])
  const [cursor, setCursor] = useState<string | undefined>()
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const abortRef = useRef<AbortController | null>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const load = useCallback(
    async (reset: boolean, after?: string) => {
      abortRef.current?.abort()
      const ac = new AbortController()
      abortRef.current = ac
      if (reset) setLoading(true)
      else setMore(true)
      setError('')
      const params = new URLSearchParams({ limit: '40' })
      if (query) params.set('q', query)
      if (after) {
        const [at, id] = after.split('|')
        if (at) params.set('after_at', at)
        if (id) params.set('after_id', id)
      }
      const r = await get<ListResp>(`/v1/admin/users?${params}`, { signal: ac.signal })
      if (ac.signal.aborted) return
      setLoading(false)
      setMore(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      const items = r.data.items || []
      setRows((prev) => (reset ? items : [...prev, ...items]))
      setCursor(r.data.next_cursor || undefined)
    },
    [query],
  )

  useEffect(() => {
    void load(true)
    return () => abortRef.current?.abort()
  }, [load])

  // Live search: debounce typing into the list query.
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      const next = q.trim()
      setQuery((prev) => (prev === next ? prev : next))
    }, 280)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [q])

  return (
    <div className="users-page">
      <div className="page-head users-head">
        <div>
          <h1>Users</h1>
          <p>Newest first. Search by username prefix, then open a row for profile and wallet.</p>
        </div>
        <Button
          size="sm"
          variant="secondary"
          loading={loading && rows.length > 0}
          onClick={() => void load(true)}
        >
          Refresh
        </Button>
      </div>

      <Card>
        <div className="users-toolbar">
          <label className="users-search">
            Search
            <div className="users-search-row">
              <input
                className="accounts-search-input"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="Username prefix, e.g. saq"
                autoComplete="off"
                autoFocus
              />
              {loading ? (
                <span className="accounts-search-spin">
                  <Loader size={14} />
                </span>
              ) : null}
            </div>
          </label>
          {query ? (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                setQ('')
                setQuery('')
              }}
            >
              Clear
            </Button>
          ) : null}
        </div>

        {error ? <div className="err-banner">{error}</div> : null}

        {loading && rows.length === 0 ? (
          <PageLoader label="Loading users…" />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>User</th>
                  <th className="num">Coins</th>
                  <th className="num">Diamonds</th>
                  <th>Joined</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((u) => {
                  const face = mediaUrl(mediaBase, u.avatar_url)
                  const name = u.full_name || u.username || '?'
                  return (
                    <tr key={u.id} className="row-link" onClick={() => navigate(`/users/${u.id}`)}>
                      <td>
                        <span className="users-cell">
                          {face ? (
                            <img className="users-av" src={face} alt="" loading="lazy" />
                          ) : (
                            <span className="users-av users-av--letter">{name.slice(0, 1).toUpperCase()}</span>
                          )}
                          <span className="users-cell-text">
                            <strong>{u.full_name || '—'}</strong>
                            <span className="muted">@{u.username}</span>
                          </span>
                        </span>
                      </td>
                      <td className="num">{u.coins.toLocaleString()}</td>
                      <td className="num">{u.diamonds.toLocaleString()}</td>
                      <td className="nowrap muted">{fmtJoined(u.created_at)}</td>
                      <td className="nowrap" onClick={(e) => e.stopPropagation()}>
                        <Button size="sm" variant="secondary" onClick={() => navigate(`/users/${u.id}`)}>
                          Open
                        </Button>
                      </td>
                    </tr>
                  )
                })}
                {rows.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="muted" style={{ padding: 20, textAlign: 'center' }}>
                      No users match{query ? ` “${query}”` : ''}.
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
              Load more
            </Button>
          </div>
        ) : null}
      </Card>
    </div>
  )
}
