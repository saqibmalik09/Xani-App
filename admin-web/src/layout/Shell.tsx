import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { mediaUrl, useAuth } from '../lib/auth'
import { rolesApi, visibleRolesForContext, type RoleView } from '../lib/roles'

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
  {
    kind: 'group',
    id: 'live',
    label: 'Live',
    children: [
      { to: '/live', label: 'Rooms', perm: 'admin.manage_live' },
      { to: '/live-time', label: 'Live time', perm: 'live.view_time', end: true },
      { to: '/live-time/audit', label: 'Time audit', perm: 'live.view_time' },
    ],
  },
  {
    kind: 'group',
    id: 'live-rewards',
    label: 'Live rewards',
    children: [
      { to: '/live-rewards', label: 'Health', perm: 'reward.view', end: true },
      { to: '/live-rewards/payouts', label: 'Payouts', perm: 'reward.view' },
      { to: '/live-rewards/stats', label: 'Daily · Weekly · Monthly', perm: 'reward.view' },
      { to: '/live-rewards/campaigns', label: 'Campaigns', perm: 'reward.view' },
      { to: '/live-rewards/findings', label: 'Findings', perm: 'reward.view' },
    ],
  },
  {
    kind: 'group',
    id: 'gifting',
    label: 'Gifting',
    children: [
      { to: '/gifting', label: 'Overview', perm: 'gift.view', end: true },
      { to: '/gifting/policies', label: 'Policies', perm: 'gift.view' },
      { to: '/gifting/catalog', label: 'Catalog tree', perm: 'gift.view' },
      { to: '/gifting/records', label: 'Records', perm: 'gift.view' },
      { to: '/settings/gifting', label: 'Settings', perm: 'admin.manage_settings' },
    ],
  },
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
    label: 'Money',
    children: [
      { to: '/recharge', label: 'Recharge', perm: 'finance.manage_recharge' },
      { to: '/withdrawals', label: 'Withdrawals', perm: 'finance.manage_withdrawals' },
      { to: '/payment-methods', label: 'Payment methods', perm: 'finance.manage_withdrawals' },
      { to: '/dollar-rates', label: 'Dollar rates', perm: 'finance.manage_coin_rates' },
    ],
  },
  {
    kind: 'group',
    id: 'sellers',
    label: 'Coin sellers',
    children: [
      { to: '/sellers', label: 'Sellers', anyOf: ['sellers.manage', 'sellers.view'], end: true },
      { to: '/sellers/transactions', label: 'Transactions', anyOf: ['sellers.manage', 'sellers.view'] },
      { to: '/sellers/disputes', label: 'Disputes', anyOf: ['sellers.manage', 'sellers.view'] },
      { to: '/settings/sellers', label: 'Settings', anyOf: ['sellers.manage', 'admin.manage_settings'] },
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
      { to: '/feature-access', label: 'Feature access', perm: 'access.manage_features' },
      { to: '/contact-sync', label: 'Contact sync', perm: 'contacts.view_sync' },
      { to: '/app-versions', label: 'App versions', perm: 'app.manage_versions' },
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
  const [roles, setRoles] = useState<RoleView[]>([])
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

  // Dynamic "Roles" nav: one entry per role in the current context, refreshed
  // when the acting membership changes. Only loaded for people who can browse
  // members, since each entry opens the (perm-gated) Members list.
  const canBrowseRoles = can('roles.view') && can('memberships.view')
  useEffect(() => {
    if (!canBrowseRoles) {
      setRoles([])
      return
    }
    let cancelled = false
    void rolesApi.list().then((r) => {
      if (!cancelled && r.ok) setRoles(r.data.items || [])
    })
    return () => {
      cancelled = true
    }
  }, [canBrowseRoles, active?.membership_id])

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
    const base = NAV.map((entry) => {
      if (entry.kind === 'link') {
        return leafVisible(entry, can, hasMembership) ? entry : null
      }
      const children = entry.children.filter((c) => leafVisible(c, can, hasMembership))
      if (children.length === 0) return null
      return { ...entry, children }
    }).filter(Boolean) as NavEntry[]

    if (canBrowseRoles) {
      const visible = visibleRolesForContext(roles, active, canPlatform)
      if (visible.length > 0) {
        const group: NavEntry = {
          kind: 'group',
          id: 'roles-directory',
          label: 'Roles',
          children: visible.map((r) => ({
            to: `/members?role=${encodeURIComponent(r.slug)}`,
            label: `${r.title} · ${(r.member_count || 0).toLocaleString()}`,
          })),
        }
        const idx = base.findIndex((e) => e.kind === 'group' && e.id === 'organization')
        if (idx < 0) base.push(group)
        else base.splice(idx + 1, 0, group)
      }
    }
    return base
  }, [can, hasMembership, canBrowseRoles, roles, active, canPlatform])

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

            const childActive = entry.children.some((c) =>
              c.to.includes('?')
                ? location.pathname + location.search === c.to
                : location.pathname === c.to || location.pathname.startsWith(c.to + '/'),
            )
            const expanded = childActive || !!openGroups[entry.id]

            // Rail mode: flatten children so icons/titles still work.
            if (collapsed) {
              return entry.children.map((child) => (
                <NavLink
                  key={child.to}
                  to={child.to}
                  end={child.end}
                  title={child.label}
                  onClick={closeMobile}
                  className={({ isActive }) =>
                    (child.to.includes('?') ? location.pathname + location.search === child.to : isActive)
                      ? 'admin-nav-link is-active'
                      : 'admin-nav-link'
                  }
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
                        end={child.end}
                        title={child.label}
                        onClick={closeMobile}
                        className={({ isActive }) =>
                          (child.to.includes('?') ? location.pathname + location.search === child.to : isActive)
                            ? 'admin-nav-link admin-nav-child is-active'
                            : 'admin-nav-link admin-nav-child'
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
