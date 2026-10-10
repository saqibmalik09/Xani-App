import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react'
import { get, put } from '../lib/api'
import { rolesApi, type RoleView } from '../lib/roles'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type Control = {
  name: string
  kind: 'bool' | 'int' | 'string' | 'enum'
  label: string
  help: string
  min?: number
  max?: number
  options?: string[]
}

type SettingsPayload = {
  values: Record<string, boolean | number | string>
  controls: Control[]
}

const selectStyle: CSSProperties = {
  height: 36,
  borderRadius: 8,
  border: '1px solid var(--line)',
  padding: '0 10px',
  background: 'var(--surface)',
  fontSize: 13,
  width: '100%',
}

/**
 * The coin-seller control panel. The form renders from the schema the server
 * returns, so a control added on the backend appears here with no dashboard
 * change. Role pickers get a friendlier multi-select on top of the raw value.
 */
export function SettingsSellersPage() {
  const [controls, setControls] = useState<Control[]>([])
  const [values, setValues] = useState<Record<string, boolean | number | string>>({})
  const [roles, setRoles] = useState<RoleView[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    const [r, rr] = await Promise.all([get<SettingsPayload>('/v1/admin/settings/sellers'), rolesApi.list()])
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setControls(r.data.controls || [])
    setValues(r.data.values || {})
    if (rr.ok) setRoles((rr.data.items || []).filter((x) => x.status === 2 && x.id !== 1))
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const set = (name: string, v: boolean | number | string) => {
    setValues((old) => ({ ...old, [name]: v }))
    setOk('')
  }

  const save = async () => {
    setSaving(true)
    setError('')
    setOk('')
    const body: Record<string, boolean | number | string> = {}
    for (const c of controls) {
      const v = values[c.name]
      if (v === undefined) continue
      body[c.name] = c.kind === 'int' ? Math.floor(Number(v) || 0) : v
    }
    const r = await put<SettingsPayload>('/v1/admin/settings/sellers', body)
    setSaving(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setValues(r.data.values || {})
    setOk('Saved. Changes apply immediately.')
  }

  if (loading) {
    return (
      <div className="withdraw-page">
        <PageLoader label="Loading seller settings…" />
      </div>
    )
  }

  return (
    <div className="withdraw-page">
      <div className="page-head">
        <div>
          <h1>Seller settings</h1>
          <p>
            The coin-seller control panel: roles, limits, fee, currencies, how the in-app list looks and how requests
            work. Every control applies platform-wide the moment it is saved.
          </p>
        </div>
        <Button loading={saving} onClick={() => void save()}>
          Save all
        </Button>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <Card>
        <div className="form-grid">
          {controls.map((c) => (
            <ControlField key={c.name} control={c} value={values[c.name]} roles={roles} onChange={(v) => set(c.name, v)} />
          ))}
        </div>
        <div className="form-actions">
          <Button loading={saving} onClick={() => void save()}>
            Save all
          </Button>
        </div>
      </Card>
    </div>
  )
}

function ControlField({
  control: c,
  value,
  roles,
  onChange,
}: {
  control: Control
  value: boolean | number | string | undefined
  roles: RoleView[]
  onChange: (v: boolean | number | string) => void
}) {
  // Role-id lists get a picker; the stored value stays a CSV string.
  if (c.kind === 'string' && (c.name === 'role_ids' || c.name === 'target_role_ids')) {
    return <RolePicker label={c.label} help={c.help} value={String(value ?? '')} roles={roles} onChange={onChange} />
  }
  if (c.kind === 'bool') {
    return (
      <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>{c.label}</span>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', minHeight: 36 }}>
          <input type="checkbox" checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />
          <span style={{ fontSize: 13 }}>{Boolean(value) ? 'On' : 'Off'}</span>
        </label>
        <span className="muted" style={{ fontSize: 12 }}>
          {c.help}
        </span>
      </label>
    )
  }
  if (c.kind === 'enum') {
    return (
      <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>{c.label}</span>
        <select style={selectStyle} value={String(value ?? '')} onChange={(e) => onChange(e.target.value)}>
          {(c.options || []).map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
        <span className="muted" style={{ fontSize: 12 }}>
          {c.help}
        </span>
      </label>
    )
  }
  if (c.kind === 'int') {
    return (
      <Field
        label={c.label}
        hint={c.help}
        type="number"
        min={c.min}
        max={c.max}
        value={String(value ?? 0)}
        onChange={(e) => onChange(Math.floor(Number(e.target.value) || 0))}
      />
    )
  }
  return <Field label={c.label} hint={c.help} value={String(value ?? '')} onChange={(e) => onChange(e.target.value)} maxLength={200} />
}

function RolePicker({
  label,
  help,
  value,
  roles,
  onChange,
}: {
  label: string
  help: string
  value: string
  roles: RoleView[]
  onChange: (v: string) => void
}) {
  const selected = useMemo(
    () =>
      new Set(
        value
          .split(',')
          .map((s) => Number(s.trim()))
          .filter((n) => n > 0),
      ),
    [value],
  )
  const toggle = (id: number) => {
    const next = new Set(selected)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    onChange([...next].sort((a, b) => a - b).join(','))
  }
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 6, gridColumn: '1 / -1' }}>
      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>{label}</span>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
        {roles.map((r) => (
          <button
            key={r.id}
            type="button"
            className={`chip${selected.has(r.id) ? ' is-active' : ''}`}
            style={{
              cursor: 'pointer',
              border: selected.has(r.id) ? '1px solid var(--brand, #4f46e5)' : '1px solid var(--line)',
              background: selected.has(r.id) ? 'var(--brand-soft, #eef2ff)' : 'var(--surface)',
              borderRadius: 999,
              padding: '4px 12px',
              fontSize: 13,
            }}
            onClick={() => toggle(r.id)}
          >
            {r.title || r.name} <span className="muted">#{r.id}</span>
          </button>
        ))}
        {roles.length === 0 ? <span className="muted">No roles loaded.</span> : null}
      </div>
      <span className="muted" style={{ fontSize: 12 }}>
        {help} Stored as: {value || '(none)'}
      </span>
    </label>
  )
}
