import { useCallback, useEffect, useState } from 'react'
import { del, get, post } from '../lib/api'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type Version = {
  id: string
  version: string
  enabled: boolean
  is_default: boolean
  message: string
  whatsapp: string
  created_at: string
  updated_at: string
}

type List = { items: Version[] }

type Draft = {
  id: string | null
  version: string
  message: string
  whatsapp: string
  enabled: boolean
  is_default: boolean
}

const DEFAULT_MESSAGE = 'A new version is available. Please contact us to update.'

const emptyDraft = (): Draft => ({
  id: null,
  version: '',
  message: DEFAULT_MESSAGE,
  whatsapp: '',
  enabled: true,
  is_default: false,
})

/**
 * Released app versions and their gate. Each row is one shipped build: turn it
 * off and every device on that build is walled off instantly with the row's own
 * message and WhatsApp contact. Many versions can be on or off at once; one can
 * be marked default. Devices on a version with no row here are never gated.
 */
export function AppVersionsPage() {
  const [rows, setRows] = useState<Version[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [draft, setDraft] = useState<Draft>(emptyDraft)
  const [saving, setSaving] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback(async () => {
    const r = await get<List>('/v1/admin/app-versions')
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setRows(r.data.items ?? [])
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const flash = (msg: string) => {
    setNotice(msg)
    window.setTimeout(() => setNotice(''), 2200)
  }

  const edit = (v: Version) => {
    setDraft({
      id: v.id,
      version: v.version,
      message: v.message,
      whatsapp: v.whatsapp,
      enabled: v.enabled,
      is_default: v.is_default,
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const save = async () => {
    const version = draft.version.trim()
    if (!version) {
      setError('Enter a version number.')
      return
    }
    setSaving(true)
    setError('')
    const body = {
      version,
      message: draft.message.trim(),
      whatsapp: draft.whatsapp.trim(),
      enabled: draft.enabled,
      is_default: draft.is_default,
    }
    const r = draft.id
      ? await post<Version>(`/v1/admin/app-versions/${draft.id}`, body)
      : await post<Version>('/v1/admin/app-versions', body)
    setSaving(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    flash(draft.id ? 'Version updated.' : 'Version created.')
    setDraft(emptyDraft())
    void load()
  }

  // Toggle / make-default reuse the update endpoint with the row's own fields,
  // so one row changing never disturbs another's message or contact.
  const patch = async (v: Version, change: Partial<Version>) => {
    setBusyId(v.id)
    const r = await post<Version>(`/v1/admin/app-versions/${v.id}`, {
      version: v.version,
      message: v.message,
      whatsapp: v.whatsapp,
      enabled: v.enabled,
      is_default: v.is_default,
      ...change,
    })
    setBusyId(null)
    if (!r.ok) {
      setError(r.message)
      return
    }
    void load()
  }

  const remove = async (v: Version) => {
    if (!window.confirm(`Delete version ${v.version}? Devices on it will no longer be gated.`)) return
    setBusyId(v.id)
    const r = await del(`/v1/admin/app-versions/${v.id}`)
    setBusyId(null)
    if (!r.ok) {
      setError(r.message)
      return
    }
    flash('Version deleted.')
    if (draft.id === v.id) setDraft(emptyDraft())
    void load()
  }

  if (loading) {
    return error ? <div className="err-banner">{error}</div> : <PageLoader label="Loading app versions…" />
  }

  return (
    <div className="rates-page">
      <div className="page-head">
        <h1>App versions</h1>
        <p>
          Each row is a released build. Turn one off to wall off every device on that version with its own
          message and WhatsApp contact — instantly, no restart. Builds with no row here are never gated.
        </p>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {notice ? <div className="ok-banner">{notice}</div> : null}

      <Card title={draft.id ? `Edit version ${draft.version || ''}` : 'Add a version'}>
        <div style={{ display: 'grid', gap: 14 }}>
          <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
            <div style={{ flex: '1 1 160px', minWidth: 160 }}>
              <Field
                label="Version"
                placeholder="e.g. 1.11"
                value={draft.version}
                onChange={(e) => setDraft({ ...draft, version: e.target.value })}
                hint="Must match the build's version name exactly."
              />
            </div>
            <div style={{ flex: '1 1 220px', minWidth: 220 }}>
              <Field
                label="WhatsApp number"
                placeholder="e.g. +14155551234"
                value={draft.whatsapp}
                onChange={(e) => setDraft({ ...draft, whatsapp: e.target.value })}
                hint="Shown as a Contact button. Leave blank for no button."
              />
            </div>
          </div>
          <TextArea
            label="Message"
            placeholder={DEFAULT_MESSAGE}
            value={draft.message}
            onChange={(e) => setDraft({ ...draft, message: e.target.value })}
            hint="What the user sees when this version is turned off."
          />
          <div style={{ display: 'flex', gap: 20, alignItems: 'center', flexWrap: 'wrap' }}>
            <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 14 }}>
              <input
                type="checkbox"
                checked={draft.enabled}
                onChange={(e) => setDraft({ ...draft, enabled: e.target.checked })}
              />
              On (allowed)
            </label>
            <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 14 }}>
              <input
                type="checkbox"
                checked={draft.is_default}
                onChange={(e) => setDraft({ ...draft, is_default: e.target.checked })}
              />
              Default
            </label>
            <div style={{ marginLeft: 'auto', display: 'flex', gap: 10 }}>
              {draft.id ? (
                <Button variant="secondary" onClick={() => setDraft(emptyDraft())}>
                  Cancel
                </Button>
              ) : null}
              <Button loading={saving} onClick={() => void save()}>
                {draft.id ? 'Save changes' : 'Create version'}
              </Button>
            </div>
          </div>
        </div>
      </Card>

      <div style={{ height: 18 }} />

      <Card title="Released versions">
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Version</th>
                <th>Status</th>
                <th>Default</th>
                <th>Message</th>
                <th>WhatsApp</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((v) => (
                <tr key={v.id}>
                  <td>
                    <strong>{v.version}</strong>
                  </td>
                  <td>
                    <button
                      type="button"
                      className={`status-pill ${v.enabled ? 'on' : 'off'}`}
                      style={{ cursor: 'pointer', border: 'none' }}
                      disabled={busyId === v.id}
                      onClick={() => void patch(v, { enabled: !v.enabled })}
                      title={v.enabled ? 'Turn off (block this version)' : 'Turn on (allow this version)'}
                    >
                      {v.enabled ? 'On' : 'Off'}
                    </button>
                  </td>
                  <td>
                    {v.is_default ? (
                      <span className="status-pill on">Default</span>
                    ) : (
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={busyId === v.id}
                        onClick={() => void patch(v, { is_default: true })}
                      >
                        Make default
                      </Button>
                    )}
                  </td>
                  <td style={{ maxWidth: 320 }}>
                    <span className="muted">{v.message || DEFAULT_MESSAGE}</span>
                  </td>
                  <td className="muted nowrap">{v.whatsapp || '—'}</td>
                  <td style={{ textAlign: 'right' }}>
                    <div style={{ display: 'inline-flex', gap: 8 }}>
                      <Button size="sm" variant="secondary" onClick={() => edit(v)}>
                        Edit
                      </Button>
                      <Button size="sm" variant="danger" disabled={busyId === v.id} onClick={() => void remove(v)}>
                        Delete
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={6} className="muted" style={{ textAlign: 'center', padding: 24 }}>
                    No versions yet. Add your first released build above.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}
