import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { mediaUrl, useAuth } from '../lib/auth'
import { rolesApi } from '../lib/roles'

type NavLeaf = {
  to: string
  label: string
  end?: boolean
  perm?: string
  /** Visible when any of these is held (instead of `perm`). */
  anyOf?: string[]
  /** Visible to anyone holding at least one role membership. */
  membersOnly?: boolean
  /** Show the pending-approvals badge. */
  badge?: 'approvals'
}

type NavGroup = {
  id: string
  label: string
  children: NavLeaf[]
}

type NavEntry = ({ kind: 'link' } & NavLeaf) | ({ kind: 'group' } & NavGroup)

/** Manager-oriented groups: related tools under one open/close parent. */
const NAV: NavEntry[] = [
  { kind: 'link', to: '/', label: 'Overview', end: true },
  {
    kind: 'group',
    id: 'rewards',
    label: 'Rewards',
    children: [{ to: '/rewards/signup', label: 'New signup', perm: 'finance.manage_store' }],
  },
  {
    kind: 'group',
    id: 'people',
    label: 'People',
    children: [
      { to: '/users', label: 'Users', perm: 'admin.manage_users' },
      { to: '/accounts', label: 'Search', perm: 'admin.manage_users' },
      { to: '/restrictions', label: 'Restrictions', perm: 'admin.manage_users' },
    ],
  },
  { kind: 'link', to: '/live', label: 'Live', perm: 'admin.manage_live' },
  {
    kind: 'group',
    id: 'catalog',
    label: 'My Bag',
    children: [
      { to: '/gifts', label: 'Gifts', perm: 'finance.manage_gifts' },
      { to: '/stickers', label: 'Stickers', perm: 'finance.manage_stickers' },
      { to: '/store', label: 'Store', perm: 'finance.manage_store' },
      { to: '/frame-purchases', label: 'Purchases', perm: 'finance.manage_store' },
    ],
  },
  {
    kind: 'group',
    id: 'levels',
    label: 'Levels',
    children: [
      { to: '/levels', label: 'Ladders', perm: 'finance.manage_levels' },
      { to: '/level-users', label: 'Level users', perm: 'finance.manage_levels' },
    ],
  },
  {
    kind: 'group',
    id: 'payouts',
    label: 'Payouts',
    children: [
      { to: '/withdrawals', label: 'Withdrawals', perm: 'finance.manage_withdrawals' },
      { to: '/payment-methods', label: 'Payment methods', perm: 'finance.manage_withdrawals' },
      { to: '/dollar-rates', label: 'Dollar rates', perm: 'finance.manage_coin_rates' },
    ],
  },
  {
    kind: 'group',
    id: 'organization',
    label: 'Organization',
    children: [
      { to: '/approvals', label: 'Approvals', anyOf: ['requests.approve', 'requests.view'], badge: 'approvals' },
      { to: '/members', label: 'Members', perm: 'memberships.view' },
      { to: '/roles', label: 'Roles', perm: 'roles.view' },
      { to: '/permissions', label: 'Permissions', perm: 'admin.manage_permissions' },
      { to: '/audit', label: 'Audit trail', perm: 'audit.view' },
    ],
  },
  { kind: 'link', to: '/network', label: 'My network', membersOnly: true },
  {
    kind: 'group',
    id: 'support',
    label: 'Support',
    children: [
      { to: '/help', label: 'Help Center', perm: 'admin.manage_help' },
      { to: '/legal', label: 'Terms & Policies', perm: 'admin.manage_help' },
    ],
  },
  { kind: 'link', to: '/banners', label: 'Banners', perm: 'finance.manage_banners' },
  { kind: 'link', to: '/notifications', label: 'Notifications', perm: 'admin.send_push' },
  { kind: 'link', to: '/settings', label: 'Settings', perm: 'admin.manage_settings' },
]

const COLLAPSE_KEY = 'xani.admin.sidebarCollapsed'
const GROUPS_KEY = 'xani.admin.navGroups.v2'

function loadOpenGroups(): Record<string, boolean> {
  try {
    const raw = localStorage.getItem(GROUPS_KEY)
    if (!raw) return {}
    return { ...JSON.parse(raw) }
  } catch {
    return {}
  }
}

function leafVisible(leaf: NavLeaf, can: (p: string) => boolean, hasMembership: boolean) {
  if (leaf.membersOnly) return hasMembership
  if (leaf.anyOf) return leaf.anyOf.some(can)
  return !leaf.perm || can(leaf.perm)
}

export function Shell() {
  const { session, appName, logout, mediaBase, can, canPlatform, active, setActiveMembership } = useAuth()
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(COLLAPSE_KEY) === '1')
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>(loadOpenGroups)
  const [accountOpen, setAccountOpen] = useState(false)
  const [pending, setPending] = useState(0)
  const accountRef = useRef<HTMLDivElement>(null)
  const user = session?.user
  const face = mediaUrl(mediaBase, user?.avatar_url || '')
  const memberships = session?.memberships || []
  const hasMembership = memberships.length > 0
  const canApprove = can('requests.approve')

  // Pending-approvals badge: one light count on entry and every minute; the
  // Approvals page itself refreshes on action.
  useEffect(() => {
    if (!canApprove) {
      setPending(0)
      return
    }
    let cancelled = false
    const tick = async () => {
      const r = await rolesApi.inboxCount()
      if (!cancelled && r.ok) setPending(r.data.pending || 0)
    }
    void tick()
    const id = window.setInterval(() => void tick(), 60_000)
    return () => {
      cancelled = true
      window.clearInterval(id)
    }
  }, [canApprove, active?.membership_id, location.pathname])

  useEffect(() => {
    localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0')
  }, [collapsed])

  useEffect(() => {
    localStorage.setItem(GROUPS_KEY, JSON.stringify(openGroups))
  }, [openGroups])

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [open])

  useEffect(() => {
    if (!accountOpen) return
    const onDoc = (e: MouseEvent) => {
      if (!accountRef.current?.contains(e.target as Node)) setAccountOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setAccountOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDoc)
      document.removeEventListener('keydown', onKey)
    }
  }, [accountOpen])

  const entries = useMemo(() => {
    return NAV.map((entry) => {
      if (entry.kind === 'link') {
        return leafVisible(entry, can, hasMembership) ? entry : null
      }
      const children = entry.children.filter((c) => leafVisible(c, can, hasMembership))
      if (children.length === 0) return null
      return { ...entry, children }
    }).filter(Boolean) as NavEntry[]
  }, [can, hasMembership])

  const renderLabel = (leaf: NavLeaf) => (
    <span className="admin-nav-label">
      {leaf.label}
      {leaf.badge === 'approvals' && pending > 0 ? (
        <span className="admin-nav-badge" aria-label={`${pending} pending`}>
          {pending > 99 ? '99+' : pending}
        </span>
      ) : null}
    </span>
  )

  const toggleGroup = (id: string) => {
    setOpenGroups((prev) => ({ ...prev, [id]: !prev[id] }))
  }

  const closeMobile = () => setOpen(false)

  return (
    <div className="admin-shell" data-collapsed={collapsed ? '1' : '0'}>
      {open ? <div className="admin-backdrop" onClick={closeMobile} aria-hidden /> : null}

      <aside className="admin-sidebar" data-open={open ? '1' : '0'} aria-label="Main navigation">
        <div className="admin-sidebar-top">
          <div className="admin-brand-block">
            <div className="admin-brand">{appName}</div>
            <div className="admin-brand-sub">Console</div>
          </div>
          <button
            type="button"
            className="admin-icon-btn admin-sidebar-toggle"
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            title={collapsed ? 'Expand' : 'Collapse'}
            onClick={() => setCollapsed((v) => !v)}
          >
            <span className="admin-burger" data-open={collapsed ? '0' : '1'} />
          </button>
          <button type="button" className="admin-icon-btn admin-close" aria-label="Close menu" onClick={closeMobile}>
            ✕
          </button>
        </div>

        <nav className="admin-nav">
          {entries.map((entry) => {
            if (entry.kind === 'link') {
              return (
                <NavLink
                  key={entry.to}
                  to={entry.to}
                  end={entry.end}
                  title={entry.label}
                  onClick={closeMobile}
                  className={({ isActive }) => (isActive ? 'admin-nav-link is-active' : 'admin-nav-link')}
                >
                  {renderLabel(entry)}
                </NavLink>
              )
            }

            const childActive = entry.children.some(
              (c) => location.pathname === c.to || location.pathname.startsWith(c.to + '/'),
            )
            const expanded = childActive || !!openGroups[entry.id]

            // Rail mode: flatten children so icons/titles still work.
            if (collapsed) {
              return entry.children.map((child) => (
                <NavLink
                  key={child.to}
                  to={child.to}
                  title={child.label}
                  onClick={closeMobile}
                  className={({ isActive }) => (isActive ? 'admin-nav-link is-active' : 'admin-nav-link')}
                >
                  {renderLabel(child)}
                </NavLink>
              ))
            }

            return (
              <div key={entry.id} className={`admin-nav-group${childActive ? ' has-active' : ''}`}>
                <button
                  type="button"
                  className={`admin-nav-group-btn${expanded ? ' is-open' : ''}`}
                  aria-expanded={expanded}
                  onClick={() => toggleGroup(entry.id)}
                >
                  <span>{entry.label}</span>
                  <span className="admin-nav-chevron" aria-hidden />
                </button>
                {expanded ? (
                  <div className="admin-nav-children">
                    {entry.children.map((child) => (
                      <NavLink
                        key={child.to}
                        to={child.to}
                        title={child.label}
                        onClick={closeMobile}
                        className={({ isActive }) =>
                          isActive ? 'admin-nav-link admin-nav-child is-active' : 'admin-nav-link admin-nav-child'
                        }
                      >
                        {renderLabel(child)}
                      </NavLink>
                    ))}
                  </div>
                ) : null}
              </div>
            )
          })}
        </nav>
      </aside>

      <div className="admin-main">
        <header className="admin-topbar">
          <button
            type="button"
            className="admin-icon-btn admin-menu"
            aria-label={open ? 'Close menu' : 'Open menu'}
            aria-expanded={open}
            onClick={() => setOpen((v) => !v)}
          >
            <span className="admin-burger" data-open={open ? '1' : '0'} />
          </button>
          <div style={{ flex: 1 }} />
          {hasMembership ? (
            <label className="admin-context" title="Which role you are acting as">
              <span className="admin-context-label">Acting as</span>
              <select
                className="admin-context-select"
                value={active?.membership_id || ''}
                onChange={(e) => setActiveMembership(e.target.value || null)}
              >
                {canPlatform('admin.view_dashboard') ? <option value="">Platform</option> : null}
                {memberships.map((m) => (
                  <option key={m.membership_id} value={m.membership_id}>
                    {m.role_title} · {m.role_slug}
                  </option>
                ))}
              </select>
            </label>
          ) : null}
          <div className="admin-account" ref={accountRef}>
            <button
              type="button"
              className="admin-userchip"
              aria-haspopup="menu"
              aria-expanded={accountOpen}
              onClick={() => setAccountOpen((v) => !v)}
            >
              <div className="admin-userchip-text">
                <div className="admin-userchip-name">{user?.full_name || user?.username}</div>
                <div className="admin-userchip-handle">@{user?.username}</div>
              </div>
              <div className="admin-avatar">
                {face ? (
                  <img src={face} alt="" width={36} height={36} style={{ objectFit: 'cover' }} loading="lazy" />
                ) : (
                  (user?.full_name || user?.username || '?').slice(0, 1).toUpperCase()
                )}
              </div>
            </button>
            {accountOpen ? (
              <div className="admin-account-menu" role="menu">
                <Link
                  role="menuitem"
                  to="/profile"
                  className="admin-account-item"
                  onClick={() => setAccountOpen(false)}
                >
                  Profile
                </Link>
                <button
                  type="button"
                  role="menuitem"
                  className="admin-account-item admin-account-item--danger"
                  onClick={() => {
                    setAccountOpen(false)
                    void logout()
                  }}
                >
                  Sign out
                </button>
              </div>
            ) : null}
          </div>
        </header>
        <main className="admin-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
