import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { get } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import {
  childRoleViews,
  rolesApi,
  visibleRolesForContext,
  type ReferralCodeView,
  type RoleView,
} from '../lib/roles'
import { Button } from '../ui/Button'
import { Card } from '../ui/Field'
import { Loader } from '../ui/Loader'

type Hit = {
  id: string
  username: string
  full_name: string
  avatar_url: string
  email?: string
}

export function DashboardPage() {
  const { session, appName, mediaBase, can, active, webBaseUrl } = useAuth()
  const navigate = useNavigate()
  const [users, setUsers] = useState<number | null>(null)
  const [reports, setReports] = useState<number | null>(null)
  const [reportsNew, setReportsNew] = useState<number | null>(null)
  const [q, setQ] = useState('')
  const [hits, setHits] = useState<Hit[]>([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [highlight, setHighlight] = useState(0)
  const wrapRef = useRef<HTMLDivElement>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const canSearch = can('admin.manage_users')
  const canHelp = can('admin.manage_help')
  const canApprove = can('requests.approve')
  const hasMembership = (session?.memberships.length || 0) > 0
  const [pendingApprovals, setPendingApprovals] = useState<number | null>(null)
  const [codes, setCodes] = useState<ReferralCodeView[]>([])
  const [copied, setCopied] = useState('')

  useEffect(() => {
    if (!canSearch) return
    let cancelled = false
    void (async () => {
      const r = await get<{ items: unknown[]; total?: number }>('/v1/admin/users?limit=1')
      if (cancelled) return
      if (r.ok && typeof r.data.total === 'number') setUsers(r.data.total)
    })()
    return () => {
      cancelled = true
    }
  }, [canSearch])

  useEffect(() => {
    if (!canApprove) return
    let cancelled = false
    void (async () => {
      const r = await rolesApi.inboxCount()
      if (cancelled) return
      if (r.ok) setPendingApprovals(r.data.pending)
    })()
    return () => {
      cancelled = true
    }
  }, [canApprove])

  useEffect(() => {
    if (!hasMembership) {
      setCodes([])
      return
    }
    let cancelled = false
    void (async () => {
      const r = await rolesApi.myCodes()
      if (cancelled) return
      if (r.ok) setCodes(r.data.items || [])
    })()
    return () => {
      cancelled = true
    }
  }, [hasMembership])

  useEffect(() => {
    if (!canHelp) return
    let cancelled = false
    void (async () => {
      const r = await get<{ items: unknown[]; total?: number; pending?: number }>('/v1/admin/support/tickets')
      if (cancelled) return
      if (!r.ok) return
      if (typeof r.data.total === 'number') setReports(r.data.total)
      else setReports((r.data.items || []).length)
      if (typeof r.data.pending === 'number') setReportsNew(r.data.pending)
    })()
    return () => {
      cancelled = true
    }
  }, [canHelp])

  const search = useCallback(async (term: string) => {
    abortRef.current?.abort()
    const t = term.trim()
    if (t.length < 1) {
      setHits([])
      setOpen(false)
      setLoading(false)
      return
    }
    const ac = new AbortController()
    abortRef.current = ac
    setLoading(true)
    const r = await get<{ items: Hit[] }>(`/v1/admin/users/search?q=${encodeURIComponent(t)}&limit=8`, {
      signal: ac.signal,
    })
    if (ac.signal.aborted) return
    setLoading(false)
    if (!r.ok) {
      setHits([])
      return
    }
    setHits(r.data.items || [])
    setOpen(true)
    setHighlight(0)
  }, [])

  useEffect(() => {
    if (!canSearch) return
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => void search(q), 220)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [q, search, canSearch])

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  const pick = (h: Hit) => {
    setOpen(false)
    navigate(`/accounts?id=${encodeURIComponent(h.id)}`)
  }

  const onKey = (e: React.KeyboardEvent) => {
    if (!open || hits.length === 0) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setHighlight((i) => Math.min(i + 1, hits.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHighlight((i) => Math.max(i - 1, 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const h = hits[highlight]
      if (h) pick(h)
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  return (
    <div className="overview-page">
      <div className="page-head">
        <h1>Overview</h1>
        <p>Welcome back, {session?.user.full_name || session?.user.username}.</p>
      </div>

      <div className="overview-stats">
        <div className="overview-stat">
          <span>Console</span>
          <strong>{appName}</strong>
        </div>
        {active ? (
          <button type="button" className="overview-stat overview-stat--link" onClick={() => navigate('/network')}>
            <span>Acting as</span>
            <strong>
              {active.role_title}
              <em className="overview-stat-new"> · {active.role_slug}</em>
            </strong>
          </button>
        ) : hasMembership ? (
          <button type="button" className="overview-stat overview-stat--link" onClick={() => navigate('/network')}>
            <span>Acting as</span>
            <strong>Platform</strong>
          </button>
        ) : null}
        {canSearch ? (
          <div className="overview-stat">
            <span>Users</span>
            <strong>{users === null ? '…' : users.toLocaleString()}</strong>
          </div>
        ) : null}
        {canApprove ? (
          <button type="button" className="overview-stat overview-stat--link" onClick={() => navigate('/approvals')}>
            <span>Pending approvals</span>
            <strong>{pendingApprovals === null ? '…' : pendingApprovals.toLocaleString()}</strong>
          </button>
        ) : null}
        {canHelp ? (
          <button
            type="button"
            className="overview-stat overview-stat--link"
            onClick={() => navigate('/help')}
          >
            <span>Reported problems</span>
            <strong>
              {reports === null ? '…' : reports.toLocaleString()}
              {reportsNew != null && reportsNew > 0 ? (
                <em className="overview-stat-new"> · {reportsNew.toLocaleString()} new</em>
              ) : null}
            </strong>
          </button>
        ) : null}
      </div>

      <RolesDirectory />

      {codes.length > 0 ? (
        <Card title="Share your invite">
          <p className="muted" style={{ margin: '0 0 12px', fontSize: 13 }}>
            Copy your code or a role-specific join link. Links use the Website URL from Settings → Branding.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {codes.map((c) => {
              const links =
                c.join_links && c.join_links.length > 0
                  ? c.join_links
                  : (c.target_roles || []).map((t) => ({
                      role: t,
                      url: webBaseUrl
                        ? `${webBaseUrl.replace(/\/$/, '')}/join/${encodeURIComponent(c.code)}/${encodeURIComponent(t.slug)}`
                        : '',
                    }))
              return (
                <div key={c.code} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
                    <span className="org-code" style={{ fontSize: 15 }}>
                      {c.code}
                    </span>
                    <span className="muted" style={{ fontSize: 13 }}>
                      as {c.role.title}
                    </span>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        void navigator.clipboard.writeText(c.code).then(() => {
                          setCopied(`code:${c.code}`)
                          window.setTimeout(() => setCopied(''), 1500)
                        })
                      }}
                    >
                      {copied === `code:${c.code}` ? 'Copied' : 'Copy code'}
                    </Button>
                  </div>
                  {links.map((l) => {
                    const url = l.url
                    const key = `link:${c.code}:${l.role.slug}`
                    return (
                      <div
                        key={l.role.slug}
                        style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}
                      >
                        <span style={{ fontSize: 13, fontWeight: 600, minWidth: 88 }}>{l.role.title}</span>
                        {url ? (
                          <code style={{ fontSize: 12, wordBreak: 'break-all', flex: 1 }}>{url}</code>
                        ) : (
                          <span className="muted" style={{ fontSize: 12, flex: 1 }}>
                            Set Website URL in Settings → Branding
                          </span>
                        )}
                        <Button
                          size="sm"
                          variant="secondary"
                          disabled={!url}
                          onClick={() => {
                            if (!url) return
                            void navigator.clipboard.writeText(url).then(() => {
                              setCopied(key)
                              window.setTimeout(() => setCopied(''), 1500)
                            })
                          }}
                        >
                          {copied === key ? 'Copied' : 'Copy link'}
                        </Button>
                      </div>
                    )
                  })}
                </div>
              )
            })}
          </div>
          <p style={{ margin: '12px 0 0', fontSize: 13 }}>
            <button type="button" className="overview-stat--link" style={{ padding: 0, border: 'none', background: 'none', color: 'var(--accent)', fontWeight: 600, cursor: 'pointer' }} onClick={() => navigate('/network')}>
              Open my network
            </button>
          </p>
        </Card>
      ) : null}

      {canSearch ? (
        <div className="accounts-search overview-search" ref={wrapRef}>
          <label className="field-label" style={{ width: '100%' }}>
            Search users
            <div className="accounts-search-row">
              <input
                className="accounts-search-input"
                value={q}
                onChange={(e) => {
                  setQ(e.target.value)
                  setOpen(true)
                }}
                onFocus={() => hits.length > 0 && setOpen(true)}
                onKeyDown={onKey}
                placeholder="Start typing username, email, phone, or name…"
                autoComplete="off"
              />
              {loading ? (
                <span className="accounts-search-spin">
                  <Loader size={16} />
                </span>
              ) : null}
            </div>
          </label>
          {open && (hits.length > 0 || (q.trim() && !loading)) ? (
            <ul className="accounts-suggest" role="listbox">
              {hits.length === 0 ? (
                <li className="accounts-suggest-empty">No matches</li>
              ) : (
                hits.map((s, i) => {
                  const av = mediaUrl(mediaBase, s.avatar_url)
                  return (
                    <li key={s.id}>
                      <button
                        type="button"
                        className={`accounts-suggest-item${i === highlight ? ' is-active' : ''}`}
                        onMouseEnter={() => setHighlight(i)}
                        onClick={() => pick(s)}
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
                      </button>
                    </li>
                  )
                })
              )}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

/**
 * Roles directory: one card per role within the current context (all roles for a
 * platform admin, else the active role's allowed sublines), showing its live
 * member count and the child roles it may manage. Every card links into the
 * filtered Members list, which already carries search, view and suspend/revoke.
 * A user holding several roles is counted under each, so totals stay per-role.
 */
function RolesDirectory() {
  const { can, canPlatform, active } = useAuth()
  const navigate = useNavigate()
  const [roles, setRoles] = useState<RoleView[]>([])
  const [loading, setLoading] = useState(true)
  // Needs roles.view to fetch the catalog and memberships.view so each card's
  // link into the Members list actually resolves.
  const canBrowse = can('roles.view') && can('memberships.view')

  useEffect(() => {
    if (!canBrowse) {
      setRoles([])
      setLoading(false)
      return
    }
    let cancelled = false
    setLoading(true)
    void (async () => {
      const r = await rolesApi.list()
      if (cancelled) return
      setLoading(false)
      if (r.ok) setRoles(r.data.items || [])
    })()
    return () => {
      cancelled = true
    }
  }, [canBrowse, active?.membership_id])

  if (!canBrowse) return null

  const visible = visibleRolesForContext(roles, active, canPlatform)
  const openRole = (slug: string) => navigate(`/members?role=${encodeURIComponent(slug)}`)

  return (
    <Card title="Roles">
      {loading ? (
        <Loader size={18} />
      ) : visible.length === 0 ? (
        <p className="muted" style={{ margin: 0, fontSize: 13 }}>
          No roles to show for your context yet.
        </p>
      ) : (
        <div className="roles-dir">
          {visible.map((r) => {
            const subs = childRoleViews(r, roles)
            return (
              <div className="roles-dir-card" key={r.id}>
                <button
                  type="button"
                  className="roles-dir-head"
                  onClick={() => openRole(r.slug)}
                  title={`View ${r.title} members`}
                >
                  <span className="roles-dir-name">{r.title}</span>
                  <span className="roles-dir-count">{(r.member_count || 0).toLocaleString()}</span>
                </button>
                {subs.length > 0 ? (
                  <div>
                    <div className="roles-dir-subs-label">Can manage</div>
                    <div className="roles-dir-subs">
                      {subs.map((c) => (
                        <button
                          type="button"
                          key={c.id}
                          className="roles-dir-sub"
                          onClick={() => openRole(c.slug)}
                          title={`View ${c.title} members`}
                        >
                          {c.title} <b>{(c.member_count || 0).toLocaleString()}</b>
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}
              </div>
            )
          })}
        </div>
      )}
    </Card>
  )
}
