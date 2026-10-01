import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { get } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { rolesApi } from '../lib/roles'
import { Loader } from '../ui/Loader'

type Hit = {
  id: string
  username: string
  full_name: string
  avatar_url: string
  email?: string
}

export function DashboardPage() {
  const { session, appName, mediaBase, can, active } = useAuth()
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
