import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { get, post } from '../lib/api'
import { Button } from '../ui/Button'
import { Card } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type Override = 'inherit' | 'allow' | 'deny'

type Row = {
  id: number
  slug: string
  name: string
  category: string
  is_system: boolean
  effective: boolean
  from_role: boolean
  roles: string[]
  override: Override
  granted_by?: string
  granted_at_ms?: number
  expires_at_ms?: number
  /** False when the caller does not hold this permission, or is the subject. */
  editable: boolean
}

type Access = {
  id: string
  username: string
  full_name: string
  avatar_url: string
  banned: boolean
  roles: string[]
  items: Row[]
  self: boolean
}

type Filter = 'all' | 'overrides' | 'allowed' | 'blocked'

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'overrides', label: 'Overrides only' },
  { key: 'allowed', label: 'Allowed' },
  { key: 'blocked', label: 'Blocked' },
]

function fmtWhen(ms?: number): string {
  if (!ms) return ''
  return new Date(ms).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

/** Why this account can or cannot do the thing, in one line. */
function source(row: Row): string {
  if (row.override === 'allow') {
    const who = row.granted_by ? ` by ${row.granted_by}` : ''
    const when = row.granted_at_ms ? ` on ${fmtWhen(row.granted_at_ms)}` : ''
    const until = row.expires_at_ms ? ` · until ${fmtWhen(row.expires_at_ms)}` : ''
    return `allowed here${who}${when}${until}`
  }
  if (row.override === 'deny') {
    const who = row.granted_by ? ` by ${row.granted_by}` : ''
    const held = row.from_role ? ` (their ${row.roles.join(', ')} role grants it)` : ''
    return `revoked here${who}${held}`
  }
  if (row.from_role) return `from ${row.roles.join(', ')}`
  return 'no role grants it'
}

/**
 * One account's permission overrides, for the whole catalog.
 *
 * A role is still the normal answer. This page is the exception: give one
 * person something their role does not carry, or take away something it
 * does. A revoke beats every role they hold, so it is the way to keep a
 * single clerk out of one screen without inventing a role for them.
 */
export function UserPermissionsPage() {
  const { id = '' } = useParams()
  const [data, setData] = useState<Access | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState('')
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<Filter>('all')

  const load = useCallback(async () => {
    setLoading(true)
    const r = await get<Access>(`/v1/rbac/users/${id}/permissions`)
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setData(r.data)
  }, [id])

  useEffect(() => {
    void load()
  }, [load])

  const setMode = async (row: Row, mode: 'allow' | 'revoke' | 'inherit') => {
    if (mode === 'revoke' && row.from_role) {
      if (!window.confirm(`Revoke “${row.name}” for this account? Their ${row.roles.join(', ')} role grants it, and this override will win.`)) return
    }
    setBusy(row.slug)
    setError('')
    setOk('')
    const r = await post(`/v1/rbac/users/${id}/permissions`, { permission: row.slug, mode })
    setBusy('')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(`${row.name}: ${r.message}`)
    await load()
  }

  const groups = useMemo(() => {
    if (!data) return []
    const q = query.trim().toLowerCase()
    const keep = data.items.filter((row) => {
      if (q && !row.name.toLowerCase().includes(q) && !row.slug.toLowerCase().includes(q) && !row.category.toLowerCase().includes(q)) return false
      if (filter === 'overrides') return row.override !== 'inherit'
      if (filter === 'allowed') return row.effective
      if (filter === 'blocked') return !row.effective
      return true
    })
    const byCat = new Map<string, Row[]>()
    for (const row of keep) {
      const list = byCat.get(row.category)
      if (list) list.push(row)
      else byCat.set(row.category, [row])
    }
    return [...byCat.entries()]
  }, [data, query, filter])

  const overrideCount = data?.items.filter((r) => r.override !== 'inherit').length ?? 0

  if (loading && !data) {
    return error ? <div className="err-banner">{error}</div> : <PageLoader label="Reading permissions…" />
  }
  if (!data) return error ? <div className="err-banner">{error}</div> : null

  const name = data.full_name || `@${data.username}`

  return (
    <div className="rates-page feature-access-page">
      <div className="page-head">
        <h1>Permissions · {name}</h1>
        <p>
          <Link to={`/users/${id}`}>← Profile</Link> · Roles are the normal way to give access. Use this page for the
          exception: allow one permission their role does not carry, or revoke one it does. A revoke outranks every
          role they hold and takes effect immediately, without a sign-out.
        </p>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      {data.self ? (
        <div className="err-banner">
          This is your own account. Change your access through roles, or ask another administrator — the console will
          not let you grant or revoke your own permissions.
        </div>
      ) : null}

      <div className="withdraw-stats">
        <span>
          <em>Roles</em> <strong>{data.roles.length ? data.roles.join(', ') : 'None'}</strong>
        </span>
        <span>
          <em>Permissions held</em> <strong>{data.items.filter((r) => r.effective).length}</strong>
        </span>
        <span className={overrideCount > 0 ? 'is-warn' : ''}>
          <em>Overrides on this account</em> <strong>{overrideCount}</strong>
        </span>
      </div>

      <Card title="Catalog">
        <div className="withdraw-toolbar">
          <div className="tabs" style={{ marginBottom: 0 }}>
            {FILTERS.map((f) => (
              <button key={f.key} type="button" className={`tab${filter === f.key ? ' is-active' : ''}`} onClick={() => setFilter(f.key)}>
                {f.label}
                {f.key === 'overrides' && overrideCount ? (
                  <span className="muted" style={{ fontSize: 12 }}> {overrideCount}</span>
                ) : null}
              </button>
            ))}
          </div>
          <input
            className="feature-access-search-input"
            placeholder="Filter by name, slug or category"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>

        {groups.length === 0 ? (
          <p className="muted" style={{ margin: '14px 0 0' }}>
            Nothing matches that.
          </p>
        ) : (
          groups.map(([category, rows]) => (
            <div key={category} style={{ marginTop: 18 }}>
              <h3 style={{ margin: '0 0 8px', fontSize: 13, textTransform: 'uppercase', letterSpacing: 0.4, color: 'var(--muted)' }}>
                {category}
              </h3>
              <div className="table-wrap">
                <table className="data-table feature-access-user-table">
                  <thead>
                    <tr>
                      <th>Permission</th>
                      <th className="feature-access-status-col">Now</th>
                      <th className="feature-access-actions-col">This account</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((row) => (
                      <tr key={row.slug}>
                        <td>
                          <div className="feature-access-feature">
                            <strong>{row.name}</strong>
                            <span className="muted">{row.slug}</span>
                          </div>
                        </td>
                        <td className="feature-access-status-col">
                          <div className="feature-access-status">
                            <span className={`status-pill ${row.effective ? 'on' : 'off'}`}>{row.effective ? 'Allowed' : 'Blocked'}</span>
                            <span className="muted">{source(row)}</span>
                          </div>
                        </td>
                        <td className="feature-access-actions-col">
                          {row.editable ? (
                            <div className="feature-access-actions">
                              <Button
                                size="sm"
                                variant={row.override === 'allow' ? 'primary' : 'secondary'}
                                loading={busy === row.slug}
                                onClick={() => void setMode(row, 'allow')}
                              >
                                Allow
                              </Button>
                              <Button
                                size="sm"
                                variant={row.override === 'deny' ? 'danger' : 'secondary'}
                                loading={busy === row.slug}
                                onClick={() => void setMode(row, 'revoke')}
                              >
                                Revoke
                              </Button>
                              <Button
                                size="sm"
                                variant={row.override === 'inherit' ? 'primary' : 'ghost'}
                                loading={busy === row.slug}
                                disabled={row.override === 'inherit'}
                                onClick={() => void setMode(row, 'inherit')}
                              >
                                Role decides
                              </Button>
                            </div>
                          ) : (
                            <span
                              className="muted"
                              title={
                                data.self
                                  ? 'You cannot change your own permissions.'
                                  : `You do not hold “${row.name}” yourself, so you cannot grant or revoke it.`
                              }
                            >
                              {data.self ? 'Your own account' : 'Not yours to give'}
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))
        )}
      </Card>
    </div>
  )
}
