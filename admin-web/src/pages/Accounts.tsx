import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { get, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card } from '../ui/Field'
import { Loader } from '../ui/Loader'
import { AccountModerationPanel } from './AccountModerationPanel'

type Hit = {
  id: string
  username: string
  full_name: string
  avatar_url: string
  email: string
  phone: string
  gender: number
  state: number
  banned: boolean
  ban_reason?: string
  follower_count: number
  following_count: number
  coins: number
  diamonds: number
  beans: number
  allow_screenshots: boolean
  video_count: number
  post_count: number
  role_slugs: string[]
  role_names: string[]
  referral_codes: string[]
  referred_by_id?: string
  referred_by_name?: string
  referred_by_username?: string
  code_used?: string
  device_count: number
  max_device_accounts: number
  password_login_active?: boolean
  ghost?: boolean
  created_at: string
}

type ListResp<T> = { items: T[] }

const genderLabel = (g: number) => (['—', 'Male', 'Female', 'Prefer not'][g] ?? '—')
const stateLabel = (h: Hit) => {
  if (h.banned || h.state === 2) return 'Banned'
  if (h.state === 3) return 'Deleted'
  return 'Active'
}

function ToolIcon({ d }: { d: string }) {
  return (
    <svg className="accounts-tool-svg" viewBox="0 0 24 24" aria-hidden>
      <path fill="currentColor" d={d} />
    </svg>
  )
}

export function AccountsPage() {
  const { mediaBase, can } = useAuth()
  const [params, setParams] = useSearchParams()
  const [q, setQ] = useState('')
  const [suggestions, setSuggestions] = useState<Hit[]>([])
  const [suggestOpen, setSuggestOpen] = useState(false)
  const [suggestLoading, setSuggestLoading] = useState(false)
  const [highlight, setHighlight] = useState(0)

  const [user, setUser] = useState<Hit | null>(null)
  const [error, setError] = useState('')
  const [loadingUser, setLoadingUser] = useState(false)
  const [ghostBusy, setGhostBusy] = useState(false)

  const toggleGhost = async (u: Hit) => {
    if (ghostBusy) return
    setGhostBusy(true)
    setError('')
    const r = await post<{ ghost: boolean }>(`/v1/admin/users/${u.id}/ghost`, { on: !u.ghost })
    setGhostBusy(false)
    if (r.ok) setUser((cur) => (cur && cur.id === u.id ? { ...cur, ghost: !u.ghost } : cur))
    else setError(r.message || 'Could not update ghost mode.')
  }

  const wrapRef = useRef<HTMLDivElement>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  const fetchSuggestions = useCallback(async (term: string) => {
    abortRef.current?.abort()
    const t = term.trim()
    if (t.length < 1) {
      setSuggestions([])
      setSuggestOpen(false)
      setSuggestLoading(false)
      return
    }
    const ac = new AbortController()
    abortRef.current = ac
    setSuggestLoading(true)
    const r = await get<ListResp<Hit>>(`/v1/admin/users/search?q=${encodeURIComponent(t)}&limit=8`, {
      signal: ac.signal,
    })
    if (ac.signal.aborted) return
    setSuggestLoading(false)
    if (!r.ok) {
      setSuggestions([])
      return
    }
    setSuggestions(r.data.items || [])
    setSuggestOpen(true)
    setHighlight(0)
  }, [])

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => void fetchSuggestions(q), 220)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [q, fetchSuggestions])

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) setSuggestOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  const loadUser = async (h: Hit) => {
    setSuggestOpen(false)
    setQ(`@${h.username}`)
    setSuggestions([])
    setUser(h)
    setError('')
    setLoadingUser(true)
    const r = await get<ListResp<Hit>>(`/v1/admin/users/search?q=${encodeURIComponent(h.id)}&limit=3`)
    const fresh = r.ok ? r.data.items?.find((x) => x.id === h.id) || h : h
    setUser(fresh)
    setLoadingUser(false)
  }

  useEffect(() => {
    const id = params.get('id')
    if (!id) return
    let cancelled = false
    void (async () => {
      const r = await get<ListResp<Hit>>(`/v1/admin/users/search?q=${encodeURIComponent(id)}&limit=3`)
      if (cancelled) return
      const hit = r.ok ? r.data.items?.find((x) => x.id === id) : undefined
      if (hit) void loadUser(hit)
      const next = new URLSearchParams(params)
      next.delete('id')
      setParams(next, { replace: true })
    })()
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (!suggestOpen || suggestions.length === 0) {
      if (e.key === 'Enter' && suggestions.length === 1) {
        e.preventDefault()
        void loadUser(suggestions[0])
      }
      return
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setHighlight((i) => Math.min(i + 1, suggestions.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHighlight((i) => Math.max(i - 1, 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const pick = suggestions[highlight]
      if (pick) void loadUser(pick)
    } else if (e.key === 'Escape') {
      setSuggestOpen(false)
    }
  }

  const face = user ? mediaUrl(mediaBase, user.avatar_url) : ''

  return (
    <div className="accounts-page">
      <div className="page-head">
        <h1>Search</h1>
        <p>Find a person by username, email, phone, or name, then pick them from the list.</p>
      </div>

      <Card>
        <div className="accounts-search" ref={wrapRef}>
          <label className="field-label" style={{ width: '100%' }}>
            Search
            <div className="accounts-search-row">
              <input
                className="accounts-search-input"
                value={q}
                onChange={(e) => {
                  setQ(e.target.value)
                  setSuggestOpen(true)
                }}
                onFocus={() => suggestions.length > 0 && setSuggestOpen(true)}
                onKeyDown={onKeyDown}
                placeholder="Start typing username, email, phone, or name…"
                autoComplete="off"
                autoFocus
              />
              {suggestLoading ? (
                <span className="accounts-search-spin">
                  <Loader size={16} />
                </span>
              ) : null}
            </div>
          </label>

          {suggestOpen && (suggestions.length > 0 || (q.trim() && !suggestLoading)) ? (
            <ul className="accounts-suggest" role="listbox">
              {suggestions.length === 0 ? (
                <li className="accounts-suggest-empty">No matches</li>
              ) : (
                suggestions.map((s, i) => {
                  const av = mediaUrl(mediaBase, s.avatar_url)
                  return (
                    <li key={s.id}>
                      <button
                        type="button"
                        className={`accounts-suggest-item${i === highlight ? ' is-active' : ''}`}
                        onMouseEnter={() => setHighlight(i)}
                        onClick={() => void loadUser(s)}
                      >
                        {av ? (
                          <img src={av} alt="" className="accounts-suggest-av" loading="lazy" />
                        ) : (
                          <span className="accounts-suggest-av accounts-suggest-av--letter">
                            {(s.full_name || s.username).slice(0, 1).toUpperCase()}
                          </span>
                        )}
                        <span className="accounts-suggest-text">
                          <strong>@{s.username}</strong>
                          <span className="muted">
                            {s.full_name || '—'}
                            {s.email ? ` · ${s.email}` : ''}
                          </span>
                        </span>
                        <span className={`status-pill ${s.banned || s.state === 2 ? 'off' : 'on'}`}>
                          {stateLabel(s)}
                        </span>
                      </button>
                    </li>
                  )
                })
              )}
            </ul>
          ) : null}
        </div>
        {error ? <div className="err-banner" style={{ marginTop: 12 }}>{error}</div> : null}
      </Card>

      {!user ? (
        <Card>
          <p className="muted" style={{ margin: 0, textAlign: 'center', padding: '28px 12px' }}>
            Type above and pick a result to open the account.
          </p>
        </Card>
      ) : loadingUser && !user.id ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
          <Loader />
        </div>
      ) : (
        <div className="accounts-detail">
          <Card>
            <div className="accounts-hero">
              {face ? (
                <img src={face} alt="" className="accounts-hero-av" />
              ) : (
                <div className="accounts-hero-av accounts-hero-av--letter">
                  {(user.full_name || user.username).slice(0, 1).toUpperCase()}
                </div>
              )}
              <div className="accounts-hero-main">
                <div className="accounts-hero-title">
                  <h2>{user.full_name || user.username}</h2>
                  <span className={`status-pill ${user.banned || user.state === 2 ? 'off' : 'on'}`}>
                    {stateLabel(user)}
                  </span>
                  {user.ghost ? <span className="status-pill warn">👻 Ghost</span> : null}
                </div>
                <div className="muted">
                  @{user.username} · ID <code>{user.id}</code>
                </div>
                <div className="chip-row" style={{ marginTop: 8 }}>
                  {(user.role_names || []).map((n) => (
                    <span key={n} className="chip">
                      {n}
                    </span>
                  ))}
                </div>
              </div>
              <Button size="sm" variant="ghost" onClick={() => setUser(null)}>
                Clear
              </Button>
            </div>

            <div className="accounts-tool-row" role="navigation" aria-label="Account tools">
              {can('finance.adjust_wallet') ? (
                <Link className="accounts-tool" to={`/accounts/${user.id}/balance`} title="Edit balance">
                  <ToolIcon d="M3 6.75A2.75 2.75 0 0 1 5.75 4h12.5A2.75 2.75 0 0 1 21 6.75v10.5A2.75 2.75 0 0 1 18.25 20H5.75A2.75 2.75 0 0 1 3 17.25V6.75Zm2.75-.25a.25.25 0 0 0-.25.25v1.5h13v-1.5a.25.25 0 0 0-.25-.25H5.75ZM4.5 10v7.25c0 .138.112.25.25.25h14.5a.25.25 0 0 0 .25-.25V10H4.5Zm8.25 4.25a.75.75 0 0 1 .75-.75h3.5a.75.75 0 0 1 0 1.5h-3.5a.75.75 0 0 1-.75-.75Z" />
                  <span>Balance</span>
                </Link>
              ) : null}
              {can('finance.manage_store') ? (
                <Link className="accounts-tool" to={`/accounts/${user.id}/store`} title="Assign store">
                  <ToolIcon d="M4.5 6.75A2.25 2.25 0 0 1 6.75 4.5h10.5A2.25 2.25 0 0 1 19.5 6.75v1.5h-15v-1.5ZM4.5 9.75h15v8.75A2.25 2.25 0 0 1 17.25 20.75H6.75A2.25 2.25 0 0 1 4.5 18.5V9.75Zm4.5 2.5a.75.75 0 0 0 0 1.5h6a.75.75 0 0 0 0-1.5h-6Z" />
                  <span>Store</span>
                </Link>
              ) : null}
              <Link className="accounts-tool" to={`/accounts/${user.id}/moderation`} title="Moderation">
                <ToolIcon d="M12 2.25 4.5 5.25v5.4c0 4.72 3.16 9.08 7.5 10.35 4.34-1.27 7.5-5.63 7.5-10.35V5.25L12 2.25Zm0 3.1 5.25 2.1v3.2c0 3.62-2.33 6.95-5.25 8.05-2.92-1.1-5.25-4.43-5.25-8.05v-3.2L12 5.35Z" />
                <span>Moderation</span>
              </Link>
              <Link className="accounts-tool" to={`/accounts/${user.id}/password`} title="Set or change password">
                <ToolIcon d="M12 2.25a4.5 4.5 0 0 0-4.5 4.5v2.25H6.75A1.5 1.5 0 0 0 5.25 10.5v9A1.5 1.5 0 0 0 6.75 21h10.5a1.5 1.5 0 0 0 1.5-1.5v-9a1.5 1.5 0 0 0-1.5-1.5H16.5V6.75A4.5 4.5 0 0 0 12 2.25Zm-3 4.5a3 3 0 1 1 6 0v2.25h-6V6.75Zm-1.5 3.75h9v9h-9v-9Zm4.5 2.25a1.125 1.125 0 0 0-.75 1.95v1.8a.75.75 0 0 0 1.5 0v-1.8a1.125 1.125 0 0 0-.75-1.95Z" />
                <span>Password</span>
              </Link>
              <Link className="accounts-tool" to={`/accounts/${user.id}/devices`} title="Devices">
                <ToolIcon d="M7.5 3.75A1.75 1.75 0 0 0 5.75 5.5v13c0 .97.78 1.75 1.75 1.75h9A1.75 1.75 0 0 0 18.25 18.5v-13A1.75 1.75 0 0 0 16.5 3.75h-9ZM7.25 5.5c0-.138.112-.25.25-.25h9c.138 0 .25.112.25.25v13a.25.25 0 0 1-.25.25h-9a.25.25 0 0 1-.25-.25v-13Zm4 12.25a.75.75 0 0 0 0 1.5h1.5a.75.75 0 0 0 0-1.5h-1.5Z" />
                <span>Devices</span>
              </Link>
            </div>

            <div className="accounts-wallet-row accounts-wallet-row--compact">
              <div className="accounts-wallet-stat">
                <span className="muted">Coins</span>
                <strong>{user.coins.toLocaleString()}</strong>
              </div>
              <div className="accounts-wallet-stat">
                <span className="muted">Beans</span>
                <strong>{user.beans.toLocaleString()}</strong>
              </div>
              <div className="accounts-wallet-stat">
                <span className="muted">Diamonds</span>
                <strong>{user.diamonds.toLocaleString()}</strong>
              </div>
            </div>

            <div className="accounts-stats">
              <Link className="accounts-stat-link" to={`/accounts/${user.id}/followers`}>
                <span className="muted">Followers</span>
                <strong>{user.follower_count}</strong>
              </Link>
              <Link className="accounts-stat-link" to={`/accounts/${user.id}/following`}>
                <span className="muted">Following</span>
                <strong>{user.following_count}</strong>
              </Link>
              <Link className="accounts-stat-link" to={`/accounts/${user.id}/videos`}>
                <span className="muted">Videos</span>
                <strong>{user.video_count}</strong>
              </Link>
              <Link className="accounts-stat-link" to={`/accounts/${user.id}/posts`}>
                <span className="muted">Posts</span>
                <strong>{user.post_count}</strong>
              </Link>
              <Link className="accounts-stat-link" to={`/accounts/${user.id}/devices`}>
                <span className="muted">Devices</span>
                <strong>{user.device_count}</strong>
              </Link>
            </div>
          </Card>

          <AccountModerationPanel
            userId={user.id}
            username={user.username}
            banned={user.banned}
            state={user.state}
            onChanged={() => void loadUser(user)}
          />

          <Card title="Profile">
            <dl className="accounts-dl">
              <dt>Email</dt>
              <dd>{user.email || '—'}</dd>
              <dt>Phone</dt>
              <dd>{user.phone || '—'}</dd>
              <dt>Gender</dt>
              <dd>{genderLabel(user.gender)}</dd>
              <dt>Joined</dt>
              <dd>{new Date(user.created_at).toLocaleString()}</dd>
              <dt>Screenshots</dt>
              <dd>{user.allow_screenshots ? 'Allowed' : 'Blocked'}</dd>
              <dt>Ghost mode</dt>
              <dd>
                <span className={`status-pill ${user.ghost ? 'warn' : 'off'}`}>
                  {user.ghost ? 'On' : 'Off'}
                </span>{' '}
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={ghostBusy}
                  onClick={() => void toggleGhost(user)}
                >
                  {ghostBusy ? '…' : user.ghost ? 'Revoke' : 'Grant'}
                </Button>
                <span className="muted" style={{ display: 'block', marginTop: 4 }}>
                  Watches any live unseen — no count, no roster, no join line — and enters
                  friends-only or locked rooms without a password. Their comments and gifts
                  show like anyone else&apos;s.
                </span>
              </dd>
              <dt>Login password</dt>
              <dd>
                {user.password_login_active ? (
                  <>
                    <span className="status-pill on">Set</span>
                    {' · '}
                    <Link to={`/accounts/${user.id}/password`}>Change</Link>
                  </>
                ) : (
                  <>
                    <span className="status-pill off">Not set</span>
                    {' · '}
                    <Link to={`/accounts/${user.id}/password`}>Set password</Link>
                  </>
                )}
              </dd>
              <dt>Referral codes</dt>
              <dd>{user.referral_codes?.length ? user.referral_codes.join(', ') : '—'}</dd>
              <dt>Referred by</dt>
              <dd>
                {user.referred_by_username ? (
                  <>
                    @{user.referred_by_username}
                    {user.referred_by_name ? ` (${user.referred_by_name})` : ''}
                    {user.code_used ? ` · code ${user.code_used}` : ''}
                  </>
                ) : (
                  '—'
                )}
              </dd>
            </dl>
            <Link to={`/users/${user.id}`} className="accounts-link">
              Open classic profile page →
            </Link>
          </Card>
        </div>
      )}
    </div>
  )
}
