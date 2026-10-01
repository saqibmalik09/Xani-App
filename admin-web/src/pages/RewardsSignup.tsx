import { useCallback, useEffect, useMemo, useState, type CSSProperties, type FormEvent } from 'react'
import { del, get, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { PageLoader } from '../ui/Loader'
import type { Catalog, Role } from './Roles'

type BagItem = {
  id: string
  kind: string
  title: string
  preview_key: string
  status: number
}

type ItemRow = { item: BagItem }

type Rule = {
  id: string
  trigger: 'signup' | 'role'
  role_id?: number
  item_id: string
  days: number
  enabled: boolean
  item_kind: string
  item_title: string
  preview_key: string
  role_name?: string
}

type Person = {
  user_id: string
  username: string
  full_name: string
  assigned: boolean
  expired: boolean
  expires_at?: string
  granted_at?: string
}

type Grant = {
  id: string
  user_id: string
  item_id: string
  kind: string
  source: string
  days: number
  expired: boolean
  created_at: string
  expires_at?: string
  item_title: string
  username?: string
  full_name?: string
}

type ListResp<T> = { items: T[]; next_cursor?: string }

const selectStyle: CSSProperties = {
  height: 36,
  minWidth: 160,
  borderRadius: 8,
  border: '1px solid var(--line)',
  padding: '0 10px',
  background: 'var(--surface)',
  color: 'var(--ink)',
  fontSize: 13,
}

const KIND: Record<string, string> = {
  frame: 'Frame',
  vip: 'VIP',
  entrance: 'Entrance',
  bubble: 'Bubble',
  badge: 'Badge',
}

const dayLabel = (d: number) => (d === 0 ? 'Permanent' : d === 1 ? '1 day' : `${d} days`)

function Thumb({ k }: { k: string }) {
  const { mediaBase } = useAuth()
  const src = mediaUrl(mediaBase, k)
  if (!src) return null
  return <img src={src} alt="" className="rewards-thumb" loading="lazy" />
}

export function RewardsSignupPage() {
  const [rules, setRules] = useState<Rule[]>([])
  const [items, setItems] = useState<BagItem[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)

  const [itemId, setItemId] = useState('')
  const [days, setDays] = useState(7)
  const [roleId, setRoleId] = useState(0)
  const [roleItemId, setRoleItemId] = useState('')
  const [roleDays, setRoleDays] = useState(7)

  const [peopleRule, setPeopleRule] = useState<Rule | null>(null)
  const [people, setPeople] = useState<Person[]>([])
  const [peopleNext, setPeopleNext] = useState('')
  const [peopleMore, setPeopleMore] = useState(false)

  const [grants, setGrants] = useState<Grant[]>([])
  const [grantNext, setGrantNext] = useState('')
  const [grantSrc, setGrantSrc] = useState<'signup' | 'role' | ''>('')
  const [grantMore, setGrantMore] = useState(false)

  const signupRules = useMemo(() => rules.filter((r) => r.trigger === 'signup'), [rules])
  const roleRules = useMemo(() => rules.filter((r) => r.trigger === 'role'), [rules])

  const loadRules = useCallback(async () => {
    const r = await get<ListResp<Rule>>('/v1/admin/reward-rules')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setRules(r.data.items || [])
  }, [])

  useEffect(() => {
    void (async () => {
      setLoading(true)
      const [bag, cat, ru, g] = await Promise.all([
        get<ListResp<ItemRow>>('/v1/admin/store-items?status=1'),
        get<Catalog>('/v1/rbac/catalog'),
        get<ListResp<Rule>>('/v1/admin/reward-rules'),
        get<ListResp<Grant>>('/v1/admin/reward-grants?limit=50&trigger=signup'),
      ])
      setLoading(false)
      if (!bag.ok) {
        setError(bag.message)
        return
      }
      if (!ru.ok) {
        setError(ru.message)
        return
      }
      setItems((bag.data.items || []).map((row) => row.item).filter((it) => it && it.status === 1))
      if (cat.ok) setRoles(cat.data.roles || [])
      setRules(ru.data.items || [])
      if (g.ok) {
        setGrants(g.data.items || [])
        setGrantNext(g.data.next_cursor || '')
        setGrantSrc('signup')
      }
    })()
  }, [])

  const addRule = async (e: FormEvent, trigger: 'signup' | 'role') => {
    e.preventDefault()
    setError('')
    setOk('')
    const chosen = trigger === 'signup' ? itemId : roleItemId
    const d = trigger === 'signup' ? days : roleDays
    if (!chosen) {
      setError('Pick an item from My Bag.')
      return
    }
    if (trigger === 'role' && roleId < 1) {
      setError('Pick a role.')
      return
    }
    setBusy(true)
    const r = await post<Rule>('/v1/admin/reward-rules', {
      trigger,
      item_id: chosen,
      days: d,
      role_id: trigger === 'role' ? roleId : 0,
    })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk('Pack saved. New signups or role grants receive it until the days run out.')
    await loadRules()
  }

  const remove = async (id: string) => {
    setError('')
    const r = await del(`/v1/admin/reward-rules/${id}`)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setRules((old) => old.filter((x) => x.id !== id))
    if (peopleRule?.id === id) {
      setPeopleRule(null)
      setPeople([])
    }
  }

  const loadPeople = async (rule: Rule, before: string, append: boolean) => {
    append ? setPeopleMore(true) : setPeople([])
    setPeopleRule(rule)
    const params = new URLSearchParams({ limit: '50' })
    if (before) params.set('before_id', before)
    const r = await get<ListResp<Person>>(`/v1/admin/reward-rules/${rule.id}/people?${params}`)
    setPeopleMore(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setPeople((old) => (append ? [...old, ...(r.data.items || [])] : r.data.items || []))
    setPeopleNext(r.data.next_cursor || '')
  }

  const grantRole = async (rule: Rule) => {
    setBusy(true)
    setError('')
    setOk('')
    const r = await post<{ granted: number }>(`/v1/admin/reward-rules/${rule.id}/grant`, {})
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(`Granted to ${r.data.granted} role member${r.data.granted === 1 ? '' : 's'} this batch.`)
    await loadPeople(rule, '', false)
  }

  const loadGrants = async (src: 'signup' | 'role' | '', before: string, append: boolean) => {
    if (append) setGrantMore(true)
    setGrantSrc(src)
    const params = new URLSearchParams({ limit: '50' })
    if (src) params.set('trigger', src)
    if (before) params.set('before_id', before)
    const r = await get<ListResp<Grant>>(`/v1/admin/reward-grants?${params}`)
    setGrantMore(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setGrants((old) => (append ? [...old, ...(r.data.items || [])] : r.data.items || []))
    setGrantNext(r.data.next_cursor || '')
  }

  if (loading) return <PageLoader label="Loading rewards…" />

  return (
    <div className="rewards-page">
      <div className="page-head">
        <h1>New signup</h1>
        <p>Optional My Bag items for new accounts, or for a role, lasting X days then expiring.</p>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <section className="rewards-block">
        <h2>New users</h2>
        <form className="rewards-row" onSubmit={(e) => void addRule(e, 'signup')}>
          <select value={itemId} onChange={(e) => setItemId(e.target.value)} style={selectStyle} aria-label="Bag item">
            <option value="">Item (optional)…</option>
            {items.map((it) => (
              <option key={it.id} value={it.id}>
                {KIND[it.kind] || it.kind} — {it.title}
              </option>
            ))}
          </select>
          <input
            type="number"
            min={0}
            max={3650}
            value={days}
            onChange={(e) => setDays(Number(e.target.value) || 0)}
            style={{ ...selectStyle, width: 88, minWidth: 88 }}
            aria-label="Days"
            title="0 = permanent"
          />
          <span className="muted rewards-hint">days (0 = keep)</span>
          <Button type="submit" size="sm" loading={busy}>
            Add
          </Button>
        </form>
        {signupRules.length === 0 ? (
          <p className="muted">No signup pack. New users get nothing extra until you add a frame, entrance, badge, or VIP.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Item</th>
                <th>Type</th>
                <th>Days</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {signupRules.map((r) => (
                <tr key={r.id} className={peopleRule?.id === r.id ? 'is-click' : undefined}>
                  <td>
                    <button type="button" className="rewards-item" onClick={() => void loadPeople(r, '', false)}>
                      <Thumb k={r.preview_key} />
                      {r.item_title}
                    </button>
                  </td>
                  <td>{KIND[r.item_kind] || r.item_kind}</td>
                  <td>{dayLabel(r.days)}</td>
                  <td className="nowrap">
                    <Button size="sm" variant="ghost" onClick={() => void loadPeople(r, '', false)}>
                      Who got it
                    </Button>
                    <Button size="sm" variant="danger" onClick={() => void remove(r.id)}>
                      Remove
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="rewards-block">
        <h2>By role</h2>
        <form className="rewards-row" onSubmit={(e) => void addRule(e, 'role')}>
          <select value={roleId} onChange={(e) => setRoleId(Number(e.target.value) || 0)} style={selectStyle} aria-label="Role">
            <option value={0}>Role…</option>
            {roles.map((role) => (
              <option key={role.id} value={role.id}>
                {role.name}
              </option>
            ))}
          </select>
          <select
            value={roleItemId}
            onChange={(e) => setRoleItemId(e.target.value)}
            style={selectStyle}
            aria-label="Bag item for role"
          >
            <option value="">Item from My Bag…</option>
            {items.map((it) => (
              <option key={it.id} value={it.id}>
                {KIND[it.kind] || it.kind} — {it.title}
              </option>
            ))}
          </select>
          <input
            type="number"
            min={0}
            max={3650}
            value={roleDays}
            onChange={(e) => setRoleDays(Number(e.target.value) || 0)}
            style={{ ...selectStyle, width: 88, minWidth: 88 }}
            aria-label="Days"
          />
          <span className="muted rewards-hint">days</span>
          <Button type="submit" size="sm" loading={busy}>
            Add
          </Button>
        </form>
        {roleRules.length === 0 ? (
          <p className="muted">No role pack. Pick a role and a bag item so those members get it for X days.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Role</th>
                <th>Item</th>
                <th>Days</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {roleRules.map((r) => (
                <tr key={r.id}>
                  <td>{r.role_name || r.role_id}</td>
                  <td>
                    <span className="rewards-item">
                      <Thumb k={r.preview_key} />
                      {r.item_title} <span className="muted">({KIND[r.item_kind] || r.item_kind})</span>
                    </span>
                  </td>
                  <td>{dayLabel(r.days)}</td>
                  <td className="nowrap">
                    <Button size="sm" variant="ghost" onClick={() => void loadPeople(r, '', false)}>
                      Eligible
                    </Button>
                    <Button size="sm" variant="secondary" loading={busy} onClick={() => void grantRole(r)}>
                      Grant now
                    </Button>
                    <Button size="sm" variant="danger" onClick={() => void remove(r.id)}>
                      Remove
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {peopleRule ? (
        <section className="rewards-block">
          <h2>
            {peopleRule.trigger === 'signup' ? 'New users who received' : 'Role members'} — {peopleRule.item_title}
          </h2>
          {people.length === 0 ? (
            <p className="muted">Nobody on this list yet.</p>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Status</th>
                  <th>Expires</th>
                </tr>
              </thead>
              <tbody>
                {people.map((p) => (
                  <tr key={p.user_id}>
                    <td>
                      <strong>@{p.username}</strong>
                      {p.full_name ? <span className="muted"> {p.full_name}</span> : null}
                    </td>
                    <td>
                      <span className={`status-pill ${p.assigned && !p.expired ? 'on' : 'off'}`}>
                        {!p.assigned ? 'Eligible' : p.expired ? 'Expired' : 'Assigned'}
                      </span>
                    </td>
                    <td className="muted">{p.expires_at ? new Date(p.expires_at).toLocaleString() : p.assigned ? 'Keep' : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {peopleNext ? (
            <Button size="sm" variant="ghost" loading={peopleMore} onClick={() => void loadPeople(peopleRule, peopleNext, true)}>
              Load more
            </Button>
          ) : null}
        </section>
      ) : null}

      <section className="rewards-block">
        <h2>Assigned</h2>
        <div className="notify-tabs">
          <button type="button" className={grantSrc === 'signup' ? 'is-on' : ''} onClick={() => void loadGrants('signup', '', false)}>
            New signup
          </button>
          <button type="button" className={grantSrc === 'role' ? 'is-on' : ''} onClick={() => void loadGrants('role', '', false)}>
            Role
          </button>
          <button type="button" className={grantSrc === '' ? 'is-on' : ''} onClick={() => void loadGrants('', '', false)}>
            All
          </button>
        </div>
        {grants.length === 0 ? (
          <p className="muted">No grants yet.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>User</th>
                <th>Item</th>
                <th>From</th>
                <th>Days</th>
                <th>State</th>
                <th>Expires</th>
              </tr>
            </thead>
            <tbody>
              {grants.map((g) => (
                <tr key={g.id}>
                  <td>
                    <strong>@{g.username || g.user_id}</strong>
                    {g.full_name ? <span className="muted"> {g.full_name}</span> : null}
                  </td>
                  <td>
                    {g.item_title} <span className="muted">({KIND[g.kind] || g.kind})</span>
                  </td>
                  <td>{g.source === 'role' ? 'Role' : 'Signup'}</td>
                  <td>{dayLabel(g.days)}</td>
                  <td>
                    <span className={`status-pill ${g.expired ? 'off' : 'on'}`}>{g.expired ? 'Expired' : 'Active'}</span>
                  </td>
                  <td className="muted">{g.expires_at ? new Date(g.expires_at).toLocaleString() : 'Keep'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {grantNext ? (
          <Button size="sm" variant="ghost" loading={grantMore} onClick={() => void loadGrants(grantSrc, grantNext, true)}>
            Load more
          </Button>
        ) : null}
      </section>
    </div>
  )
}
