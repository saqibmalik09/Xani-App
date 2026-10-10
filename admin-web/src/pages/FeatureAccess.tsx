import { useCallback, useEffect, useState } from 'react'
import { get, post } from '../lib/api'
import { Button } from '../ui/Button'
import { Card } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type Feature = { slug: string; title: string; note: string }

type RoleRow = {
  id: number
  slug: string
  title: string
  scope: number
  status: number
  is_system: boolean
  features: Record<string, boolean>
}

type Matrix = { features: Feature[]; roles: RoleRow[] }

type Hit = {
  id: string
  username: string
  full_name: string
  avatar_url: string
}

type UserAccess = Hit & {
  effective: Record<string, boolean>
  overrides: Record<string, 'allow' | 'deny' | 'inherit'>
  from_role: Record<string, boolean>
}

const scopeLabel = (s: number) =>
  (['', 'Platform', 'Live room', 'Agency', 'Country'][s] ?? 'Platform')

/**
 * Who may see Live, go live, use chat and capture the screen — by role, and by
 * single account for exceptions. Roles come from the server catalog, so a role
 * created today is listed here without a deploy.
 */
export function FeatureAccessPage() {
  const [matrix, setMatrix] = useState<Matrix | null>(null)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  const load = useCallback(async () => {
    const r = await get<Matrix>('/v1/admin/feature-access')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setMatrix(r.data)
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const toggleRole = async (role: RoleRow, feature: Feature, allow: boolean) => {
    const key = `${role.slug}:${feature.slug}`
    setBusy(key)
    setError('')
    setOk('')
    const r = await post(`/v1/admin/feature-access/roles`, {
      role: role.slug,
      feature: feature.slug,
      allow,
    })
    setBusy('')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(`${feature.title}: ${allow ? 'allowed' : 'removed'} for ${role.title}.`)
    await load()
  }

  if (!matrix) {
    return error ? <div className="err-banner">{error}</div> : <PageLoader label="Loading feature access…" />
  }

  return (
    <div className="rates-page feature-access-page">
      <div className="page-head">
        <h1>Feature access</h1>
        <p>
          Which roles can see Live, go live, use chat and capture the screen. A plain user holds none of
          these until a role — or a single account below — says otherwise. Changes reach phones
          immediately; nobody has to sign out or reopen the app.
        </p>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <Card title="By role">
        <p className="feature-access-lead">
          Every active role on the platform. Toggling a feature adds or removes that one permission and
          leaves the rest of the role alone.
        </p>
        <div className="table-wrap feature-access-matrix-wrap">
          <table className="data-table feature-access-matrix">
            <thead>
              <tr>
                <th className="feature-access-role-col">Role</th>
                {matrix.features.map((f) => (
                  <th key={f.slug} className="feature-access-toggle-col" title={f.note}>
                    {f.title}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {matrix.roles.map((role) => (
                <tr key={role.id}>
                  <td className="feature-access-role-col">
                    <div className="feature-access-role">
                      <strong>{role.title}</strong>
                      <span className="muted">
                        {role.slug} · {scopeLabel(role.scope)}
                        {role.is_system ? ' · system' : ''}
                      </span>
                    </div>
                  </td>
                  {matrix.features.map((f) => {
                    const on = role.features[f.slug]
                    const key = `${role.slug}:${f.slug}`
                    return (
                      <td key={f.slug} className="feature-access-toggle-col">
                        <Button
                          size="sm"
                          variant={on ? 'primary' : 'secondary'}
                          loading={busy === key}
                          onClick={() => void toggleRole(role, f, !on)}
                        >
                          {on ? 'Allowed' : 'Off'}
                        </Button>
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <UserOverrides features={matrix.features} onError={setError} onOk={setOk} />
    </div>
  )
}

/**
 * The exception path: search one account and allow or revoke a feature just for
 * them. Revoke outranks any role they hold; "Role decides" drops the override.
 */
function UserOverrides({
  features,
  onError,
  onOk,
}: {
  features: Feature[]
  onError: (m: string) => void
  onOk: (m: string) => void
}) {
  const [query, setQuery] = useState('')
  const [hits, setHits] = useState<Hit[]>([])
  const [searching, setSearching] = useState(false)
  const [searched, setSearched] = useState(false)
  const [picked, setPicked] = useState<UserAccess | null>(null)
  const [busy, setBusy] = useState('')

  const search = async () => {
    const q = query.trim()
    if (!q) return
    setSearching(true)
    setSearched(false)
    const r = await get<{ items: Hit[] }>(`/v1/mod/users?q=${encodeURIComponent(q)}&limit=8`)
    setSearching(false)
    setSearched(true)
    if (!r.ok) {
      onError(r.message)
      setHits([])
      return
    }
    setHits(r.data.items || [])
  }

  const open = useCallback(
    async (id: string) => {
      const r = await get<UserAccess>(`/v1/admin/feature-access/users/${id}`)
      if (!r.ok) {
        onError(r.message)
        return
      }
      setPicked(r.data)
    },
    [onError],
  )

  const setMode = async (feature: Feature, mode: 'allow' | 'revoke' | 'inherit') => {
    if (!picked) return
    setBusy(feature.slug)
    onError('')
    onOk('')
    const r = await post(`/v1/admin/feature-access/users/${picked.id}`, {
      feature: feature.slug,
      mode,
    })
    setBusy('')
    if (!r.ok) {
      onError(r.message)
      return
    }
    onOk(`${feature.title}: ${r.message}`)
    await open(picked.id)
  }

  return (
    <Card title="One account">
      <p className="feature-access-lead">
        Search a user id, username or name to allow a feature their role does not carry — or to revoke
        one their role does. Revoke always wins.
      </p>

      <div className="feature-access-search">
        <input
          className="feature-access-search-input"
          placeholder="User id, @username or name"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') void search()
          }}
        />
        <Button size="sm" loading={searching} onClick={() => void search()}>
          Search
        </Button>
      </div>

      {searched && hits.length === 0 ? (
        <p className="muted feature-access-empty">No accounts match that.</p>
      ) : null}

      {hits.length > 0 ? (
        <ul className="feature-access-hits">
          {hits.map((h) => (
            <li key={h.id} className="feature-access-hit">
              <div className="feature-access-hit-meta">
                <strong>{h.full_name || `@${h.username}`}</strong>
                <span className="muted">
                  @{h.username} · {h.id}
                </span>
              </div>
              <Button
                size="sm"
                variant={picked?.id === h.id ? 'primary' : 'secondary'}
                onClick={() => void open(h.id)}
              >
                {picked?.id === h.id ? 'Selected' : 'Manage'}
              </Button>
            </li>
          ))}
        </ul>
      ) : null}

      {picked ? (
        <div className="feature-access-picked">
          <div className="feature-access-picked-head">
            <strong>{picked.full_name || `@${picked.username}`}</strong>
            <span className="muted">
              @{picked.username} · {picked.id}
            </span>
          </div>
          <div className="table-wrap">
            <table className="data-table feature-access-user-table">
              <thead>
                <tr>
                  <th>Feature</th>
                  <th className="feature-access-status-col">Now</th>
                  <th className="feature-access-actions-col">This account</th>
                </tr>
              </thead>
              <tbody>
                {features.map((f) => {
                  const mode = picked.overrides[f.slug] || 'inherit'
                  const on = picked.effective[f.slug]
                  const source =
                    mode === 'allow'
                      ? 'allowed here'
                      : mode === 'deny'
                        ? 'revoked here'
                        : picked.from_role[f.slug]
                          ? 'from their role'
                          : 'no role grants it'
                  return (
                    <tr key={f.slug}>
                      <td>
                        <div className="feature-access-feature">
                          <strong>{f.title}</strong>
                          <span className="muted">{f.note}</span>
                        </div>
                      </td>
                      <td className="feature-access-status-col">
                        <div className="feature-access-status">
                          <span className={`status-pill ${on ? 'on' : 'off'}`}>
                            {on ? 'Allowed' : 'Blocked'}
                          </span>
                          <span className="muted">{source}</span>
                        </div>
                      </td>
                      <td className="feature-access-actions-col">
                        <div className="feature-access-actions">
                          <Button
                            size="sm"
                            variant={mode === 'allow' ? 'primary' : 'secondary'}
                            loading={busy === f.slug}
                            onClick={() => void setMode(f, 'allow')}
                          >
                            Allow
                          </Button>
                          <Button
                            size="sm"
                            variant={mode === 'deny' ? 'danger' : 'secondary'}
                            loading={busy === f.slug}
                            onClick={() => void setMode(f, 'revoke')}
                          >
                            Revoke
                          </Button>
                          <Button
                            size="sm"
                            variant={mode === 'inherit' ? 'primary' : 'ghost'}
                            loading={busy === f.slug}
                            onClick={() => void setMode(f, 'inherit')}
                          >
                            Role decides
                          </Button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </Card>
  )
}
