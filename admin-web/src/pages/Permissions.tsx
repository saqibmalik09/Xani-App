import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { del, get, patch, post } from '../lib/api'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import type { Catalog, Perm } from './Roles'

export function PermissionsPage() {
  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [selected, setSelected] = useState<Perm | null>(null)
  const [creating, setCreating] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  const [slug, setSlug] = useState('')
  const [name, setName] = useState('')
  const [category, setCategory] = useState('admin')

  const load = useCallback(async () => {
    const r = await get<Catalog>('/v1/rbac/catalog')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setCatalog(r.data)
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const byCategory = useMemo(() => {
    const map = new Map<string, Perm[]>()
    for (const p of catalog?.permissions || []) {
      const list = map.get(p.category) || []
      list.push(p)
      map.set(p.category, list)
    }
    return [...map.entries()].sort(([a], [b]) => a.localeCompare(b))
  }, [catalog])

  const startCreate = () => {
    setCreating(true)
    setSelected(null)
    setSlug('')
    setName('')
    setCategory('admin')
    setError('')
    setOk('')
  }

  const pick = (p: Perm) => {
    setCreating(false)
    setSelected(p)
    setSlug(p.slug)
    setName(p.name)
    setCategory(p.category)
    setError('')
    setOk('')
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const r = creating
      ? await post<Perm>('/v1/rbac/permissions', {
          slug: slug.trim().toLowerCase(),
          name: name.trim(),
          category: category.trim().toLowerCase() || 'custom',
        })
      : await patch<Perm>(`/v1/rbac/permissions/${selected?.id}`, {
          name: name.trim(),
          category: category.trim().toLowerCase(),
        })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(creating ? 'Permission created. Assign it to a role on the Roles page.' : 'Permission updated.')
    setCreating(false)
    setSelected(r.data)
    await load()
  }

  const onDelete = async () => {
    if (!selected || selected.is_system) return
    if (!window.confirm(`Delete permission “${selected.name}”?`)) return
    setBusy(true)
    const r = await del(`/v1/rbac/permissions/${selected.id}`)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk('Permission deleted.')
    setSelected(null)
    await load()
  }

  if (!catalog) return <PageLoader label="Loading permissions…" />

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div className="page-head" style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <h1>Permissions</h1>
          <p>
            Permissions are the individual switches (open dashboard, manage roles, ban users…). Create new ones here,
            then tick them on a role so every person with that role gets them.
          </p>
        </div>
        <Button onClick={startCreate}>New permission</Button>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <div
        style={{ display: 'grid', gridTemplateColumns: '1fr minmax(260px, 340px)', gap: 16 }}
        className="roles-layout"
      >
        <Card title="Catalog">
          {byCategory.map(([cat, list]) => (
            <div key={cat} style={{ marginBottom: 18 }}>
              <div style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 8 }}>
                {cat}
              </div>
              <div className="role-list">
                {list.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    className={`role-row${selected?.id === p.id && !creating ? ' is-selected' : ''}`}
                    onClick={() => pick(p)}
                  >
                    <div>
                      <div className="role-row-title">{p.name}</div>
                      <div className="role-row-meta">
                        {p.slug}
                        {p.is_system ? ' · built-in' : ' · custom'}
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </Card>

        <Card title={creating ? 'Create permission' : selected ? 'Edit permission' : 'Details'}>
          {!creating && !selected ? (
            <p className="muted">Select a permission to rename it, or create a custom one for your console.</p>
          ) : (
            <form className="form-grid" onSubmit={(e) => void onSubmit(e)}>
              <Field
                label="Slug"
                value={slug}
                onChange={(e) => setSlug(e.target.value)}
                required
                disabled={!creating}
                hint="Stable API key — e.g. admin.export_reports"
              />
              <Field
                label="Label"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                hint="Human-readable name shown in the Roles checklist"
              />
              <Field
                label="Category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                hint="Groups the checklist — admin, agency, live…"
              />
              <div className="form-actions">
                <Button type="submit" loading={busy}>
                  {creating ? 'Create' : 'Save'}
                </Button>
                {!creating && selected && !selected.is_system ? (
                  <Button type="button" variant="danger" loading={busy} onClick={() => void onDelete()}>
                    Delete
                  </Button>
                ) : null}
              </div>
              {selected?.is_system ? (
                <p className="muted">Built-in permissions can be renamed but not deleted.</p>
              ) : null}
            </form>
          )}
        </Card>
      </div>

      <style>{`
        @media (max-width: 800px) {
          .roles-layout { grid-template-columns: 1fr !important; }
        }
      `}</style>
    </div>
  )
}
