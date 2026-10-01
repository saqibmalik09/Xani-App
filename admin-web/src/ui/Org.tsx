import { useEffect, useRef, useState, type ReactNode, type SelectHTMLAttributes } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../lib/api'
import {
  memberStatusLabel,
  personLabel,
  reqStatusLabel,
  roleStatusLabel,
  rolesApi,
  type Membership,
  type MembershipBrief,
} from '../lib/roles'
import { Loader } from './Loader'

/** Coloured pill for membership (s), role (r) or request (q) status. */
export function StatusPill({ kind, status }: { kind: 's' | 'r' | 'q'; status: number }) {
  const label =
    kind === 's' ? memberStatusLabel[status] : kind === 'r' ? roleStatusLabel[status] : reqStatusLabel[status]
  return <span className={`status-pill ${kind}${status}`}>{label || status}</span>
}

export function Select({ label, hint, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement> & { label: string; hint?: string; children: ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>{label}</span>
      <select className="org-select" {...rest}>
        {children}
      </select>
      {hint ? <span style={{ fontSize: 12, color: 'var(--muted)' }}>{hint}</span> : null}
    </label>
  )
}

export function Drawer({ title, onClose, children, actions }: { title: string; onClose: () => void; children: ReactNode; actions?: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [onClose])
  return (
    <>
      <div className="org-drawer-backdrop" onClick={onClose} aria-hidden />
      <aside className="org-drawer" role="dialog" aria-label={title}>
        <div className="org-drawer-head">
          <h2>{title}</h2>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            {actions}
            <button type="button" className="admin-icon-btn" aria-label="Close" onClick={onClose}>
              ✕
            </button>
          </div>
        </div>
        <div className="org-drawer-body">{children}</div>
      </aside>
    </>
  )
}

export function MemberLink({ m, self }: { m: Pick<Membership, 'id' | 'username' | 'full_name' | 'role'> & { referral_code?: string }; self?: boolean }) {
  return (
    <Link to={`/members/${m.id}`} className={`org-chain-node${self ? ' is-self' : ''}`}>
      <strong>{personLabel(m)}</strong>
      <small>
        {m.role.title}
        {m.referral_code ? ` · ${m.referral_code}` : ''}
      </small>
    </Link>
  )
}

/** Upline chain rendered root → nearest → (self). Input is nearest-first. */
export function Chain({ upline, self }: { upline: Pick<Membership, 'id' | 'username' | 'full_name' | 'role' | 'code'>[]; self?: Membership }) {
  const rootFirst = [...upline].reverse()
  if (rootFirst.length === 0 && !self) return <span className="muted">Top of the tree</span>
  return (
    <div className="org-chain">
      {rootFirst.length === 0 ? <span className="muted">Top of the tree</span> : null}
      {rootFirst.map((m) => (
        <span key={m.id} style={{ display: 'contents' }}>
          <MemberLink m={m} />
          <span className="org-chain-arrow">→</span>
        </span>
      ))}
      {self ? <MemberLink m={self} self /> : null}
    </div>
  )
}

type Hit = { id: string; username: string; full_name: string; avatar_url?: string }

/** Account picker on the public search route, so uplines without the admin user list can still pick people. */
export function UserPicker({ label, value, onChange, hint }: { label: string; value: Hit | null; onChange: (u: Hit | null) => void; hint?: string }) {
  const [q, setQ] = useState('')
  const [hits, setHits] = useState<Hit[]>([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const wrap = useRef<HTMLDivElement>(null)
  const abort = useRef<AbortController | null>(null)

  useEffect(() => {
    const t = q.trim()
    if (t.length < 2) {
      setHits([])
      return
    }
    const timer = setTimeout(async () => {
      abort.current?.abort()
      const ac = new AbortController()
      abort.current = ac
      setLoading(true)
      const r = await get<{ items: Hit[] }>(`/v1/users/search?q=${encodeURIComponent(t)}&limit=10`, { signal: ac.signal })
      if (ac.signal.aborted) return
      setLoading(false)
      if (r.ok) {
        setHits(r.data.items || [])
        setOpen(true)
      }
    }, 200)
    return () => clearTimeout(timer)
  }, [q])

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  return (
    <div className="accounts-search" ref={wrap} style={{ position: 'relative' }}>
      <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
        <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>{label}</span>
        {value ? (
          <div className="role-row" style={{ cursor: 'default', justifyContent: 'space-between' }}>
            <div>
              <div className="role-row-title">{personLabel(value)}</div>
              <div className="role-row-meta">@{value.username}</div>
            </div>
            <button type="button" className="chip" onClick={() => onChange(null)}>
              Change
            </button>
          </div>
        ) : (
          <div className="accounts-search-row">
            <input
              className="accounts-search-input"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onFocus={() => hits.length > 0 && setOpen(true)}
              placeholder="Type a username or name…"
              autoComplete="off"
            />
            {loading ? (
              <span className="accounts-search-spin">
                <Loader size={16} />
              </span>
            ) : null}
          </div>
        )}
        {hint ? <span style={{ fontSize: 12, color: 'var(--muted)' }}>{hint}</span> : null}
      </label>
      {open && !value && hits.length > 0 ? (
        <ul className="accounts-suggest" role="listbox">
          {hits.map((h) => (
            <li key={h.id}>
              <button
                type="button"
                className="accounts-suggest-item"
                onClick={() => {
                  onChange(h)
                  setOpen(false)
                  setQ('')
                }}
              >
                <span className="accounts-suggest-av accounts-suggest-av--letter">
                  {(h.full_name || h.username).slice(0, 1).toUpperCase()}
                </span>
                <span className="accounts-suggest-text">
                  <strong>@{h.username}</strong>
                  <span className="muted">{h.full_name || '—'}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

/** Membership picker: searches members the caller may see, optionally limited to one role. */
export function MembershipPicker({
  label,
  value,
  onChange,
  role,
  hint,
  exclude,
}: {
  label: string
  value: MembershipBrief | null
  onChange: (m: MembershipBrief | null) => void
  role?: string
  hint?: string
  exclude?: string[]
}) {
  const [q, setQ] = useState('')
  const [hits, setHits] = useState<Membership[]>([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const wrap = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const timer = setTimeout(async () => {
      setLoading(true)
      const r = await rolesApi.memberships({ role, q: q.trim(), status: 2 })
      setLoading(false)
      if (r.ok) {
        setHits((r.data.items || []).filter((m) => !exclude?.includes(m.id)))
        setOpen(true)
      }
    }, 200)
    return () => clearTimeout(timer)
  }, [q, role, exclude])

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  return (
    <div className="accounts-search" ref={wrap} style={{ position: 'relative' }}>
      <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
        <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>{label}</span>
        {value ? (
          <div className="role-row" style={{ cursor: 'default', justifyContent: 'space-between' }}>
            <div>
              <div className="role-row-title">{personLabel(value)}</div>
              <div className="role-row-meta">
                {value.role.title}
                {value.referral_code ? (
                  <>
                    {' '}
                    · code <span className="org-code">{value.referral_code}</span>
                  </>
                ) : null}
              </div>
            </div>
            <button type="button" className="chip" onClick={() => onChange(null)}>
              Change
            </button>
          </div>
        ) : (
          <div className="accounts-search-row">
            <input
              className="accounts-search-input"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onFocus={() => setOpen(true)}
              placeholder="Search by username, name or referral code…"
              autoComplete="off"
            />
            {loading ? (
              <span className="accounts-search-spin">
                <Loader size={16} />
              </span>
            ) : null}
          </div>
        )}
        {hint ? <span style={{ fontSize: 12, color: 'var(--muted)' }}>{hint}</span> : null}
      </label>
      {open && !value ? (
        <ul className="accounts-suggest" role="listbox">
          {hits.length === 0 ? (
            <li className="accounts-suggest-empty">{loading ? 'Searching…' : 'No matching members'}</li>
          ) : (
            hits.map((m) => (
              <li key={m.id}>
                <button
                  type="button"
                  className="accounts-suggest-item"
                  onClick={() => {
                    onChange({
                      id: m.id,
                      code: m.code,
                      user_id: m.user_id,
                      username: m.username,
                      full_name: m.full_name,
                      role: m.role,
                      status: m.status,
                      referral_code: m.referral_code,
                    })
                    setOpen(false)
                    setQ('')
                  }}
                >
                  <span className="accounts-suggest-av accounts-suggest-av--letter">
                    {(m.full_name || m.username).slice(0, 1).toUpperCase()}
                  </span>
                  <span className="accounts-suggest-text">
                    <strong>{personLabel(m)}</strong>
                    <span className="muted">
                      {m.role.title}
                      {m.referral_code ? ` · code ${m.referral_code}` : ''} · {m.direct_children} direct
                    </span>
                  </span>
                </button>
              </li>
            ))
          )}
        </ul>
      ) : null}
    </div>
  )
}
