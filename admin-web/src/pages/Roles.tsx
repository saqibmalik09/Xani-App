import { useCallback, useEffect, useMemo, useState, type FormEvent, Fragment } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import {
  MEMBER_STATUS,
  REQ_STATUS,
  ROLE_STATUS,
  personLabel,
  reachLabel,
  rolesApi,
  when,
  type Catalog as EngineCatalog,
  type FormField,
  type Membership,
  type Perm,
  type Policy,
  type RoleVersion,
  type RoleView,
} from '../lib/roles'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { RevokeDrawer, TransferDrawer } from '../ui/MemberActions'
import { Select, StatusPill } from '../ui/Org'
import { FormBuilder, FormPreview } from '../ui/RoleForm'

export type { Perm }

/** Legacy shape of /v1/rbac/catalog, still used by pickers on other pages. */
export type Role = {
  id: number
  uid?: string
  code?: string
  slug: string
  name: string
  title?: string
  status?: number
  scope: number
  is_system: boolean
  parent_role_id?: number | null
  permissions: string[]
  referral_target_role_ids?: number[]
  referral_target_slugs?: string[]
  child_role_ids?: number[]
}

export type Catalog = { roles: Role[]; permissions: Perm[]; referral?: { code_length: number } }

type Tab = 'details' | 'permissions' | 'hierarchy' | 'form' | 'members' | 'history'

const defaultPolicy = (): Policy => ({
  approval: { mode: 'parent', ttl_hours: 336 },
  recruitment: { self_apply: false, referral_apply: true },
  membership: { leave_upline: false, remove_downline: false, shift_upline: false },
})

export function RolesPage() {
  const { ref } = useParams()
  const navigate = useNavigate()
  const { canAny } = useAuth()
  const [catalog, setCatalog] = useState<EngineCatalog | null>(null)
  const [roles, setRoles] = useState<RoleView[]>([])
  const [includeRetired, setIncludeRetired] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  const canCreate = canAny('roles.create') || canAny('admin.manage_roles')

  const load = useCallback(async () => {
    const [c, l] = await Promise.all([rolesApi.catalog(), rolesApi.list(includeRetired)])
    if (!c.ok) {
      setError(c.message)
      setLoading(false)
      return
    }
    setCatalog(c.data)
    if (!l.ok) setError(l.message)
    else setRoles(l.data.items || [])
    setLoading(false)
  }, [includeRetired])

  useEffect(() => {
    void load()
  }, [load])

  const selected = useMemo(() => {
    if (!ref || ref === 'new') return null
    return roles.find((r) => String(r.id) === ref || r.slug === ref || r.code === ref || r.uid === ref) || null
  }, [roles, ref])

  const byId = useMemo(() => new Map(roles.map((r) => [r.id, r])), [roles])

  const flash = (msg: string) => {
    setOk(msg)
    setError('')
    window.setTimeout(() => setOk(''), 4000)
  }

  const afterWrite = async (r: RoleView, msg: string) => {
    flash(msg)
    await load()
    navigate(`/roles/${r.slug}`, { replace: true })
  }

  if (loading || !catalog) return <PageLoader label="Loading roles…" />

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div className="page-head" style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <h1>Roles</h1>
          <p>
            A role is a job title with abilities. Each role has a slug (its stable name in APIs and links — renames keep
            the old slug as an alias), a title people see, and an application form candidates fill in.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <label className="perm-check" style={{ padding: '6px 10px' }}>
            <input type="checkbox" checked={includeRetired} onChange={(e) => setIncludeRetired(e.target.checked)} />
            <span>Show retired</span>
          </label>
          {canCreate ? <Button onClick={() => navigate('/roles/new')}>New role</Button> : null}
        </div>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <div className="org-two">
        <Card title={`All roles (${roles.length})`}>
          <div className="role-list">
            {roles.map((role) => (
              <Link
                key={role.id}
                to={`/roles/${role.slug}`}
                className={`role-row${selected?.id === role.id ? ' is-selected' : ''}`}
                style={{ textDecoration: 'none', color: 'inherit' }}
              >
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div className="role-row-title" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    {role.title}
                    <StatusPill kind="r" status={role.status} />
                  </div>
                  <div className="role-row-meta">
                    <span className="org-code">{role.slug}</span>
                    {role.is_system ? ' · built-in' : ''}
                    {role.upline_roles?.length
                      ? ` · under ${role.upline_roles.map((u) => byId.get(u.id)?.title || u.title).join(' / ')}`
                      : ''}
                    {' · '}
                    {role.member_count} member{role.member_count === 1 ? '' : 's'}
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </Card>

        {ref === 'new' ? (
          <CreateRole
            catalog={catalog}
            roles={roles}
            onCreated={(r) => void afterWrite(r, 'Role created. Activate it when the permissions are right.')}
            onError={setError}
          />
        ) : selected ? (
          <RoleDetail
            key={selected.id}
            role={selected}
            catalog={catalog}
            roles={roles}
            onChanged={(r, msg) => void afterWrite(r, msg)}
            onDeleted={() => {
              flash('Draft deleted.')
              void load()
              navigate('/roles', { replace: true })
            }}
            onError={setError}
          />
        ) : (
          <Card title="Select a role">
            <p className="muted">
              Pick a role on the left to see its identifiers, permissions with reach, who it reports to, its members and
              its change history.
            </p>
          </Card>
        )}
      </div>
    </div>
  )
}

// ─── create ──────────────────────────────────────────────────────────────────

function CreateRole({
  catalog,
  roles,
  onCreated,
  onError,
}: {
  catalog: EngineCatalog
  roles: RoleView[]
  onCreated: (r: RoleView) => void
  onError: (m: string) => void
}) {
  const [title, setTitle] = useState('')
  const [slug, setSlug] = useState('')
  const [description, setDescription] = useState('')
  const [scope, setScope] = useState(1)
  const [uplines, setUplines] = useState<Set<string>>(new Set())
  const [allowSame, setAllowSame] = useState(false)
  const [policy, setPolicy] = useState<Policy>(defaultPolicy())
  const [reach, setReach] = useState<Record<string, number>>({})
  const [children, setChildren] = useState<Set<string>>(new Set())
  const [activate, setActivate] = useState(false)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    const r = await rolesApi.create({
      title: title.trim(),
      slug: slug.trim().toLowerCase(),
      description: description.trim(),
      scope,
      upline_roles: [...uplines],
      allow_same_role_child: allowSame,
      policy,
      permissions: Object.entries(reach)
        .filter(([, v]) => v > 0)
        .map(([s, v]) => ({ slug: s, reach: v })),
      child_roles: [...children],
      activate,
    })
    setBusy(false)
    if (!r.ok) {
      onError(r.message)
      return
    }
    onCreated(r.data)
  }

  return (
    <Card title="Create role">
      <form className="form-grid" onSubmit={(e) => void submit(e)}>
        <Field
          label="Title"
          value={title}
          onChange={(e) => {
            setTitle(e.target.value)
            if (!slug || slug === slugify(title)) setSlug(slugify(e.target.value))
          }}
          required
          maxLength={80}
          hint="Shown to people — e.g. Agency Manager"
        />
        <Field
          label="Slug"
          value={slug}
          onChange={(e) => setSlug(e.target.value)}
          required
          hint="Stable id in APIs and links — lowercase, e.g. agency_manager."
        />
        <TextArea label="Description" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} />
        <Select label="Scope" value={scope} onChange={(e) => setScope(Number(e.target.value))}>
          {catalog.scopes.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </Select>
        <UplinePicker roles={roles} value={uplines} onChange={setUplines} exclude={0} />
        <label className="perm-check">
          <input type="checkbox" checked={allowSame} onChange={(e) => setAllowSame(e.target.checked)} />
          <span>
            <strong>Members may recruit the same role under themselves</strong>
            <div className="muted">Turn on for multi-level structures (an agent under an agent).</div>
          </span>
        </label>
        <PolicyEditor value={policy} onChange={setPolicy} />
        <ChildrenPicker roles={roles} value={children} onChange={setChildren} exclude={0} />
        <PermissionReachEditor perms={catalog.permissions} reaches={catalog.reaches} value={reach} onChange={setReach} />
        <label className="perm-check">
          <input type="checkbox" checked={activate} onChange={(e) => setActivate(e.target.checked)} />
          <span>
            <strong>Activate immediately</strong>
            <div className="muted">Otherwise the role is saved as a Draft nobody can be assigned to yet.</div>
          </span>
        </label>
        <div className="form-actions">
          <Button type="submit" loading={busy}>
            Create role
          </Button>
        </div>
      </form>
    </Card>
  )
}

// ─── detail ──────────────────────────────────────────────────────────────────

function RoleDetail({
  role,
  catalog,
  roles,
  onChanged,
  onDeleted,
  onError,
}: {
  role: RoleView
  catalog: EngineCatalog
  roles: RoleView[]
  onChanged: (r: RoleView, msg: string) => void
  onDeleted: () => void
  onError: (m: string) => void
}) {
  const { can, canAny } = useAuth()
  const [tab, setTab] = useState<Tab>('details')
  const [busy, setBusy] = useState(false)
  const manage = canAny('admin.manage_roles')
  const canUpdate = manage || can('roles.update') || can('roles.manage_hierarchy') || can('roles.manage_recruitment')
  const canPerms = manage || can('roles.manage_permissions')
  const canHierarchy = manage || can('roles.manage_hierarchy')
  const canForm = manage || can('roles.manage_recruitment')
  const canSuspend = manage || canAny('roles.suspend')
  const canRetire = manage || canAny('roles.retire')
  const retired = role.status === ROLE_STATUS.retired
  // User (default for every signup) and Administrator cannot be deleted/suspended.
  const protectedRole = role.id === 1 || role.slug === 'user' || role.id === 10 || role.slug === 'administrator'
  const [retiring, setRetiring] = useState(false)

  const setStatus = async (status: number, label: string) => {
    setBusy(true)
    const r = await rolesApi.setStatus(role.id, status)
    setBusy(false)
    if (!r.ok) {
      onError(r.message)
      return
    }
    onChanged(r.data, `Role ${label}.`)
  }

  const remove = async () => {
    if (!window.confirm(`Delete draft “${role.title}”? This cannot be undone.`)) return
    setBusy(true)
    const r = await rolesApi.remove(role.id)
    setBusy(false)
    if (!r.ok) {
      onError(r.message)
      return
    }
    onDeleted()
  }

  const tabs: { id: Tab; label: string }[] = [
    { id: 'details', label: 'Details' },
    { id: 'permissions', label: `Permissions (${role.permissions.length})` },
    { id: 'hierarchy', label: 'Hierarchy' },
    { id: 'form', label: `Application form (${role.form?.length || 0})` },
    { id: 'members', label: `Members (${role.member_count})` },
    { id: 'history', label: `History (v${role.version})` },
  ]

  return (
    <Card
      title={role.title}
      actions={
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <StatusPill kind="r" status={role.status} />
          {!retired && canSuspend && role.status !== ROLE_STATUS.active ? (
            <Button size="sm" loading={busy} onClick={() => void setStatus(ROLE_STATUS.active, 'activated')}>
              Activate
            </Button>
          ) : null}
          {!retired && canSuspend && role.status === ROLE_STATUS.active && !protectedRole ? (
            <Button size="sm" variant="secondary" loading={busy} onClick={() => void setStatus(ROLE_STATUS.suspended, 'suspended')}>
              Suspend
            </Button>
          ) : null}
          {!retired && canRetire && !protectedRole && role.status !== ROLE_STATUS.draft ? (
            <Button size="sm" variant="danger" loading={busy} onClick={() => setRetiring((v) => !v)}>
              Delete role…
            </Button>
          ) : null}
          {canRetire && !protectedRole && role.status === ROLE_STATUS.draft ? (
            <Button size="sm" variant="danger" loading={busy} onClick={() => void remove()}>
              Delete draft
            </Button>
          ) : null}
        </div>
      }
    >
      {retiring ? (
        <RetirePanel
          role={role}
          roles={roles}
          onClose={() => setRetiring(false)}
          onDone={(r, msg) => {
            setRetiring(false)
            onChanged(r, msg)
          }}
          onError={onError}
          onShowMembers={() => setTab('members')}
        />
      ) : null}
      <dl className="org-kv" style={{ marginBottom: 16 }}>
        <dt>Slug</dt>
        <dd>
          <span className="org-code">{role.slug}</span>
          {role.aliases.length ? (
            <span className="muted">
              {' '}
              · also answers to {role.aliases.map((a) => (
                <span key={a} className="org-code" style={{ marginRight: 4 }}>
                  {a}
                </span>
              ))}
            </span>
          ) : null}
        </dd>
        <dt>Scope</dt>
        <dd>{catalog.scopes.find((s) => s.id === role.scope)?.name || role.scope}</dd>
        <dt>Reports to</dt>
        <dd>
          {role.upline_roles?.length ? (
            <span className="chip-row" style={{ display: 'inline-flex', flexWrap: 'wrap', gap: 6 }}>
              {role.upline_roles.map((u) => (
                <Link key={u.id} to={`/roles/${u.slug}`} className="chip">
                  {u.title}
                </Link>
              ))}
            </span>
          ) : (
            <span className="muted">Top of the tree</span>
          )}
        </dd>
        <dt>Updated</dt>
        <dd>{when(role.updated_at)}</dd>
      </dl>

      <div className="tabs" role="tablist">
        {tabs.map((t) => (
          <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} className={`tab${tab === t.id ? ' is-active' : ''}`} onClick={() => setTab(t.id)}>
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'details' ? (
        <DetailsForm role={role} readOnly={retired || !canUpdate} onChanged={onChanged} onError={onError} />
      ) : null}
      {tab === 'permissions' ? (
        <PermissionsForm role={role} catalog={catalog} readOnly={retired || !canPerms} onChanged={onChanged} onError={onError} />
      ) : null}
      {tab === 'hierarchy' ? (
        <HierarchyForm role={role} roles={roles} readOnly={retired || !canHierarchy} onChanged={onChanged} onError={onError} />
      ) : null}
      {tab === 'form' ? <ApplicationFormTab role={role} readOnly={retired || !canForm} onChanged={onChanged} onError={onError} /> : null}
      {tab === 'members' ? <MembersTab role={role} /> : null}
      {tab === 'history' ? <HistoryTab role={role} /> : null}
    </Card>
  )
}

/**
 * Deleting (retiring) a role with live members forces a decision about them:
 * move everyone to another role in place, or revoke the role from all of
 * them. The server does it as one audited operation.
 */
function RetirePanel({
  role,
  roles,
  onClose,
  onDone,
  onError,
  onShowMembers,
}: {
  role: RoleView
  roles: RoleView[]
  onClose: () => void
  onDone: (r: RoleView, msg: string) => void
  onError: (m: string) => void
  onShowMembers: () => void
}) {
  const hasMembers = role.member_count > 0
  const [mode, setMode] = useState<'transfer' | 'revoke'>('transfer')
  const [target, setTarget] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const targets = roles.filter(
    (r) =>
      r.id !== role.id &&
      r.status === ROLE_STATUS.active &&
      r.id !== 1 &&
      r.slug !== 'user' &&
      r.id !== 10 &&
      r.slug !== 'administrator',
  )

  const submit = async () => {
    if (hasMembers && mode === 'transfer' && !target) {
      onError('Pick the role members move to.')
      return
    }
    setBusy(true)
    const r = await rolesApi.retire(role.id, hasMembers ? { members: mode, target_role: target, reason } : { members: '' })
    setBusy(false)
    if (!r.ok) {
      onError(r.message)
      return
    }
    onDone(
      r.data,
      hasMembers
        ? mode === 'transfer'
          ? `Role retired; ${role.member_count} member${role.member_count === 1 ? '' : 's'} moved.`
          : `Role retired; ${role.member_count} member${role.member_count === 1 ? '' : 's'} revoked.`
        : 'Role retired.',
    )
  }

  return (
    <div style={{ border: '1px solid var(--danger, #dc2626)', borderRadius: 10, padding: 16, marginBottom: 16, background: 'var(--surface)' }}>
      <div style={{ fontWeight: 700, marginBottom: 6 }}>Delete “{role.title}”</div>
      {hasMembers ? (
        <>
          <p className="muted" style={{ margin: '0 0 10px' }}>
            {role.member_count} {role.member_count === 1 ? 'person holds' : 'people hold'} this role right now —{' '}
            <button
              type="button"
              onClick={onShowMembers}
              style={{ background: 'none', border: 'none', padding: 0, color: 'var(--accent, #7c3aed)', cursor: 'pointer', textDecoration: 'underline', font: 'inherit' }}
            >
              see who
            </button>
            . Decide what happens to them; the role is then retired (it disappears from pickers and cannot be edited again).
          </p>
          <label style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
            <input type="radio" name="retire-mode" checked={mode === 'transfer'} onChange={() => setMode('transfer')} />
            <span>Move all members to</span>
            <select
              className="org-select"
              style={{ width: 'auto', height: 32 }}
              value={target}
              disabled={mode !== 'transfer'}
              onChange={(e) => setTarget(e.target.value)}
              aria-label="Role members move to"
            >
              <option value="">Choose a role…</option>
              {targets.map((r) => (
                <option key={r.id} value={r.slug}>
                  {r.title}
                </option>
              ))}
            </select>
            <span className="muted">(upline and downline stay as they are)</span>
          </label>
          <label style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 10, flexWrap: 'wrap' }}>
            <input type="radio" name="retire-mode" checked={mode === 'revoke'} onChange={() => setMode('revoke')} />
            <span>Revoke the role from all members</span>
            <input
              style={{
                width: 260,
                height: 32,
                padding: '0 10px',
                borderRadius: 'var(--radius-sm, 8px)',
                border: '1px solid var(--line)',
                background: 'var(--surface)',
                color: 'var(--ink)',
                fontSize: 13,
              }}
              placeholder="Reason shown to members (optional)"
              value={reason}
              disabled={mode !== 'revoke'}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
        </>
      ) : (
        <p className="muted" style={{ margin: '0 0 10px' }}>
          Nobody holds this role. It is retired immediately: it disappears from pickers and cannot be edited again.
        </p>
      )}
      <div style={{ display: 'flex', gap: 8 }}>
        <Button size="sm" variant="danger" loading={busy} onClick={() => void submit()}>
          {hasMembers ? (mode === 'transfer' ? 'Move members & delete role' : 'Revoke all & delete role') : 'Delete role'}
        </Button>
        <Button size="sm" variant="secondary" onClick={onClose}>
          Cancel
        </Button>
      </div>
    </div>
  )
}

function ApplicationFormTab({
  role,
  readOnly,
  onChanged,
  onError,
}: {
  role: RoleView
  readOnly: boolean
  onChanged: (r: RoleView, msg: string) => void
  onError: (m: string) => void
}) {
  const initial = useMemo(() => role.form || [], [role])
  const [fields, setFields] = useState<FormField[]>(initial)
  const [busy, setBusy] = useState(false)
  const [preview, setPreview] = useState(false)
  const dirty = JSON.stringify(fields) !== JSON.stringify(initial)

  const save = async () => {
    setBusy(true)
    const r = await rolesApi.setForm(role.id, fields)
    setBusy(false)
    if (!r.ok) {
      onError(r.message)
      return
    }
    onChanged(r.data, 'Application form saved. The app shows the new questions the next time someone applies.')
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <p className="muted" style={{ margin: 0 }}>
        Questions a person answers when applying to become a <strong>{role.title}</strong> — documents, bank details,
        WhatsApp, anything the approver needs. The app builds the screen from this list, so adding a role or a question
        never needs an app update. Every answer is checked again on the server.
      </p>
      <div className="tabs" role="tablist" style={{ marginBottom: 0 }}>
        <button type="button" role="tab" aria-selected={!preview} className={`tab${!preview ? ' is-active' : ''}`} onClick={() => setPreview(false)}>
          Build
        </button>
        <button type="button" role="tab" aria-selected={preview} className={`tab${preview ? ' is-active' : ''}`} onClick={() => setPreview(true)}>
          Preview as applicant
        </button>
      </div>
      {preview ? <FormPreview fields={fields} /> : <FormBuilder value={fields} onChange={setFields} disabled={readOnly} />}
      {!readOnly ? (
        <div className="form-actions">
          <Button loading={busy} disabled={!dirty} onClick={() => void save()}>
            Save form
          </Button>
          {dirty ? (
            <Button variant="ghost" onClick={() => setFields(initial)}>
              Reset
            </Button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

function DetailsForm({
  role,
  readOnly,
  onChanged,
  onError,
}: {
  role: RoleView
  readOnly: boolean
  onChanged: (r: RoleView, msg: string) => void
  onError: (m: string) => void
}) {
  const [title, setTitle] = useState(role.title)
  const [slug, setSlug] = useState(role.slug)
  const [description, setDescription] = useState(role.description || '')
  const [allowSame, setAllowSame] = useState(role.allow_same_role_child)
  const [policy, setPolicy] = useState<Policy>(role.policy || defaultPolicy())
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    const body: Parameters<typeof rolesApi.update>[1] = {}
    if (title.trim() !== role.title) body.title = title.trim()
    if (slug.trim().toLowerCase() !== role.slug) body.slug = slug.trim().toLowerCase()
    if (description.trim() !== (role.description || '')) body.description = description.trim()
    if (allowSame !== role.allow_same_role_child) body.allow_same_role_child = allowSame
    if (JSON.stringify(policy) !== JSON.stringify(role.policy)) body.policy = policy
    if (Object.keys(body).length === 0) {
      setBusy(false)
      onChanged(role, 'Nothing changed.')
      return
    }
    const r = await rolesApi.update(role.id, body)
    setBusy(false)
    if (!r.ok) {
      onError(r.message)
      return
    }
    onChanged(r.data, body.slug ? `Role saved. The old slug “${role.slug}” still works as an alias.` : 'Role saved.')
  }

  return (
    <form className="form-grid" onSubmit={(e) => void submit(e)}>
      <Field label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={80} disabled={readOnly} />
      <Field
        label="Slug"
        value={slug}
        onChange={(e) => setSlug(e.target.value)}
        required
        disabled={readOnly || role.is_system}
        hint={role.is_system ? 'Built-in slugs are fixed.' : 'Renaming keeps the old slug as an alias so nothing breaks.'}
      />
      <TextArea label="Description" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} disabled={readOnly} />
      <p className="muted" style={{ margin: 0 }}>
        Who this role reports to is set on the <strong>Hierarchy</strong> tab.
      </p>
      <label className="perm-check">
        <input type="checkbox" checked={allowSame} onChange={(e) => setAllowSame(e.target.checked)} disabled={readOnly} />
        <span>
          <strong>Members may recruit the same role under themselves</strong>
          <div className="muted">Turn on for multi-level structures (an agent under an agent).</div>
        </span>
      </label>
      <PolicyEditor value={policy} onChange={setPolicy} disabled={readOnly} />
      {!readOnly ? (
        <div className="form-actions">
          <Button type="submit" loading={busy}>
            Save changes
          </Button>
        </div>
      ) : null}
    </form>
  )
}

function PermissionsForm({
  role,
  catalog,
  readOnly,
  onChanged,
  onError,
}: {
  role: RoleView
  catalog: EngineCatalog
  readOnly: boolean
  onChanged: (r: RoleView, msg: string) => void
  onError: (m: string) => void
}) {
  const initial = useMemo(() => Object.fromEntries(role.permissions.map((p) => [p.slug, p.reach])), [role])
  const [reach, setReach] = useState<Record<string, number>>(initial)
  const [busy, setBusy] = useState(false)
  const dirty = JSON.stringify(reach) !== JSON.stringify(initial)

  const save = async () => {
    setBusy(true)
    const r = await rolesApi.setPermissions(
      role.id,
      Object.entries(reach)
        .filter(([, v]) => v > 0)
        .map(([slug, v]) => ({ slug, reach: v })),
    )
    setBusy(false)
    if (!r.ok) {
      onError(r.message)
      return
    }
    onChanged(r.data, 'Permissions saved. Everyone holding this role gets the new set on their next request.')
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <p className="muted" style={{ margin: 0 }}>
        Each permission carries a <strong>reach</strong>: how far down the tree it applies. “Own membership” = only
        themselves, “Direct downline” = people directly under them, “Whole downline” = everyone below, “Whole platform” =
        everyone. You can only grant what you hold platform-wide yourself.
      </p>
      <PermissionReachEditor perms={catalog.permissions} reaches={catalog.reaches} value={reach} onChange={setReach} disabled={readOnly} />
      {!readOnly ? (
        <div className="form-actions">
          <Button loading={busy} disabled={!dirty} onClick={() => void save()}>
            Save permissions
          </Button>
          {dirty ? (
            <Button variant="ghost" onClick={() => setReach(initial)}>
              Reset
            </Button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

function HierarchyForm({
  role,
  roles,
  readOnly,
  onChanged,
  onError,
}: {
  role: RoleView
  roles: RoleView[]
  readOnly: boolean
  onChanged: (r: RoleView, msg: string) => void
  onError: (m: string) => void
}) {
  const initialChildren = useMemo(() => new Set(role.child_roles.map((c) => c.slug)), [role])
  const initialUplines = useMemo(() => new Set((role.upline_roles || []).map((c) => c.slug)), [role])
  const [children, setChildren] = useState<Set<string>>(initialChildren)
  const [uplines, setUplines] = useState<Set<string>>(initialUplines)
  const [busy, setBusy] = useState(false)
  const same = (a: Set<string>, b: Set<string>) => [...a].sort().join() === [...b].sort().join()
  const childrenDirty = !same(children, initialChildren)
  const uplinesDirty = !same(uplines, initialUplines)
  const reportsHere = roles.filter((r) => r.id !== role.id && (r.upline_role_ids || []).includes(role.id))

  const save = async () => {
    setBusy(true)
    let latest = role
    if (uplinesDirty) {
      const r = await rolesApi.setUplines(role.id, [...uplines])
      if (!r.ok) {
        setBusy(false)
        onError(r.message)
        return
      }
      latest = r.data
    }
    if (childrenDirty) {
      const r = await rolesApi.setChildren(role.id, [...children])
      if (!r.ok) {
        setBusy(false)
        onError(r.message)
        return
      }
      latest = r.data
    }
    setBusy(false)
    onChanged(latest, 'Hierarchy saved. Members can now be placed under any of the upline roles.')
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <UplinePicker roles={roles} value={uplines} onChange={setUplines} exclude={role.id} disabled={readOnly} />
      <div>
        <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6 }}>Roles that report to {role.title}</div>
        {reportsHere.length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>
            None yet. Tick {role.title} under “Reports to” on another role, or tick that role below.
          </p>
        ) : (
          <div className="chip-row">
            {reportsHere.map((r) => (
              <Link key={r.id} to={`/roles/${r.slug}`} className="chip">
                {r.title} · {r.slug}
              </Link>
            ))}
          </div>
        )}
      </div>
      <ChildrenPicker roles={roles} value={children} onChange={setChildren} exclude={role.id} disabled={readOnly} />
      {!readOnly ? (
        <div className="form-actions">
          <Button loading={busy} disabled={!childrenDirty && !uplinesDirty} onClick={() => void save()}>
            Save hierarchy
          </Button>
          {childrenDirty || uplinesDirty ? (
            <Button
              variant="ghost"
              onClick={() => {
                setChildren(initialChildren)
                setUplines(initialUplines)
              }}
            >
              Reset
            </Button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

function MembersTab({ role }: { role: RoleView }) {
  const { can } = useAuth()
  const [q, setQ] = useState('')
  const [items, setItems] = useState<Membership[]>([])
  const [next, setNext] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [expanded, setExpanded] = useState<string | null>(null)
  const [action, setAction] = useState<{ kind: 'transfer' | 'revoke'; m: Membership } | null>(null)
  const canTransfer = can('memberships.transfer')
  const canRevoke = can('memberships.revoke')

  const load = useCallback(
    async (cursor: string, append: boolean) => {
      setLoading(true)
      const r = await rolesApi.members(role.id, q.trim(), cursor)
      setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setItems((prev) => (append ? [...prev, ...(r.data.items || [])] : r.data.items || []))
      setNext(r.data.next_cursor || '')
    },
    [role.id, q],
  )

  useEffect(() => {
    const t = setTimeout(() => void load('', false), 200)
    return () => clearTimeout(t)
  }, [load])

  const flash = (msg: string) => {
    setOk(msg)
    window.setTimeout(() => setOk(''), 5000)
  }

  const live = (m: Membership) => m.status === MEMBER_STATUS.active || m.status === MEMBER_STATUS.suspended

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        People holding this role. Expand a row to see their downline chain; Shift moves them (or their downline) under
        another upline; Revoke removes the role and notifies that person when approved.
      </p>
      <Field label="Search members" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Username, name or referral code" />
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      {items.length === 0 && !loading ? (
        <p className="muted">No one holds this role{q ? ' matching that search' : ' yet'}.</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th style={{ width: 36 }} />
                <th>Member</th>
                <th>Their code</th>
                <th>Status</th>
                <th>Direct downline</th>
                <th>Since</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {items.map((m) => {
                const open = expanded === m.id
                return (
                  <Fragment key={m.id}>
                    <tr>
                      <td>
                        {m.direct_children > 0 ? (
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setExpanded(open ? null : m.id)}
                            aria-label={open ? 'Hide downline' : 'Show downline'}
                          >
                            {open ? '▾' : '▸'}
                          </Button>
                        ) : (
                          <span className="muted">·</span>
                        )}
                      </td>
                      <td>
                        <Link to={`/members/${m.id}`}>{personLabel(m)}</Link>
                        <div className="muted">@{m.username}</div>
                      </td>
                      <td>{m.referral_code ? <span className="org-code">{m.referral_code}</span> : <span className="muted">—</span>}</td>
                      <td>
                        <StatusPill kind="s" status={m.status} />
                      </td>
                      <td>{m.direct_children}</td>
                      <td>{when(m.approved_at || m.created_at)}</td>
                      <td>
                        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                          <Link to={`/members/${m.id}`}>
                            <Button size="sm" variant="ghost">
                              View
                            </Button>
                          </Link>
                          {canTransfer && live(m) ? (
                            <Button size="sm" variant="secondary" onClick={() => setAction({ kind: 'transfer', m })}>
                              Shift
                            </Button>
                          ) : null}
                          {canRevoke && live(m) ? (
                            <Button size="sm" variant="danger" onClick={() => setAction({ kind: 'revoke', m })}>
                              Revoke
                            </Button>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                    {open ? (
                      <tr>
                        <td colSpan={7} style={{ background: 'var(--surface-2, var(--surface))', padding: '12px 16px' }}>
                          <MemberDownlinePanel
                            rootId={m.id}
                            rootLabel={personLabel(m)}
                            onTransfer={(child) => setAction({ kind: 'transfer', m: child })}
                            onRevoke={(child) => setAction({ kind: 'revoke', m: child })}
                          />
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {next ? (
        <div className="form-actions">
          <Button variant="secondary" loading={loading} onClick={() => void load(next, true)}>
            Load more
          </Button>
        </div>
      ) : null}
      {action?.kind === 'transfer' ? (
        <TransferDrawer
          m={action.m}
          onClose={() => setAction(null)}
          onDone={(v) => {
            setAction(null)
            flash(v.status === REQ_STATUS.approved ? 'Shift completed.' : `Shift request ${v.code} sent for approval.`)
            void load('', false)
            setExpanded(null)
          }}
        />
      ) : null}
      {action?.kind === 'revoke' ? (
        <RevokeDrawer
          m={action.m}
          onClose={() => setAction(null)}
          onDone={(v) => {
            setAction(null)
            flash(
              v.status === REQ_STATUS.approved
                ? 'Role revoked — the person was notified.'
                : `Revoke request ${v.code} sent for approval. They will be notified when it is approved.`,
            )
            void load('', false)
            setExpanded(null)
          }}
        />
      ) : null}
    </div>
  )
}

/** Expandable downline under a role member — full chain with Shift / Revoke. */
function MemberDownlinePanel({
  rootId,
  rootLabel,
  onTransfer,
  onRevoke,
}: {
  rootId: string
  rootLabel: string
  onTransfer: (m: Membership) => void
  onRevoke: (m: Membership) => void
}) {
  const { can } = useAuth()
  const [depth, setDepth] = useState(0)
  const [items, setItems] = useState<Membership[]>([])
  const [next, setNext] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const canTransfer = can('memberships.transfer')
  const canRevoke = can('memberships.revoke')

  const load = useCallback(
    async (cursor: string, append: boolean) => {
      setLoading(true)
      const r = await rolesApi.downline(rootId, { depth, cursor })
      setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setItems((prev) => (append ? [...prev, ...(r.data.items || [])] : r.data.items || []))
      setNext(r.data.next_cursor || '')
    },
    [rootId, depth],
  )

  useEffect(() => {
    void load('', false)
  }, [load])

  const live = (m: Membership) => m.status === MEMBER_STATUS.active || m.status === MEMBER_STATUS.suspended

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
        <strong style={{ fontSize: 13 }}>
          Downline under {rootLabel}
        </strong>
        <Select label="Levels" value={depth} onChange={(e) => setDepth(Number(e.target.value))}>
          <option value={1}>Direct only</option>
          <option value={2}>2 levels</option>
          <option value={3}>3 levels</option>
          <option value={0}>Everyone below</option>
        </Select>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {loading && items.length === 0 ? (
        <PageLoader label="Loading downline…" />
      ) : items.length === 0 ? (
        <p className="muted" style={{ margin: 0 }}>
          Nobody below this member.
        </p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Level</th>
                <th>Member</th>
                <th>Role</th>
                <th>Status</th>
                <th>Direct downline</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {items.map((m) => (
                <tr key={m.id}>
                  <td>{m.tree_depth ?? m.depth}</td>
                  <td>
                    <Link to={`/members/${m.id}`}>{personLabel(m)}</Link>
                    <div className="muted">@{m.username}</div>
                  </td>
                  <td>
                    <Link to={`/roles/${m.role.slug}`}>{m.role.title}</Link>
                  </td>
                  <td>
                    <StatusPill kind="s" status={m.status} />
                  </td>
                  <td>{m.direct_children}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                      <Link to={`/members/${m.id}`} className="muted" style={{ fontSize: 12 }}>
                        View
                      </Link>
                      {canTransfer && live(m) ? (
                        <Button size="sm" variant="secondary" onClick={() => onTransfer(m)}>
                          Shift
                        </Button>
                      ) : null}
                      {canRevoke && live(m) ? (
                        <Button size="sm" variant="danger" onClick={() => onRevoke(m)}>
                          Revoke
                        </Button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {next ? (
        <div className="form-actions">
          <Button variant="secondary" loading={loading} onClick={() => void load(next, true)}>
            Load more
          </Button>
        </div>
      ) : null}
    </div>
  )
}

function HistoryTab({ role }: { role: RoleView }) {
  const [items, setItems] = useState<RoleVersion[] | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    void rolesApi.versions(role.id).then((r) => {
      if (r.ok) setItems(r.data.items || [])
      else setError(r.message)
    })
  }, [role.id])
  if (error) return <div className="err-banner">{error}</div>
  if (!items) return <PageLoader label="Loading history…" />
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Version</th>
            <th>When</th>
            <th>Changed by</th>
            <th>Snapshot</th>
          </tr>
        </thead>
        <tbody>
          {items.map((v) => (
            <tr key={v.version}>
              <td>v{v.version}</td>
              <td>{when(v.created_at)}</td>
              <td>{v.changed_by ? <Link to={`/users/${v.changed_by}`}>{v.changed_by}</Link> : <span className="muted">system</span>}</td>
              <td>
                <details>
                  <summary className="muted" style={{ cursor: 'pointer' }}>
                    view
                  </summary>
                  <pre style={{ fontSize: 11, maxHeight: 240, overflow: 'auto', margin: '8px 0 0' }}>{JSON.stringify(v.snapshot, null, 2)}</pre>
                </details>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ─── shared editors ──────────────────────────────────────────────────────────

function UplinePicker({
  roles,
  value,
  onChange,
  exclude,
  disabled,
}: {
  roles: RoleView[]
  value: Set<string>
  onChange: (v: Set<string>) => void
  exclude: number
  disabled?: boolean
}) {
  const toggle = (slug: string) => {
    const next = new Set(value)
    if (next.has(slug)) next.delete(slug)
    else next.add(slug)
    onChange(next)
  }
  return (
    <div>
      <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6 }}>Reports to (upline roles)</div>
      <p className="muted" style={{ marginTop: 0 }}>
        Tick every role a member of this one may sit under. Tick several to run the same role in different lines —
        e.g. a Moderator under a Country Head <em>or</em> under an Administrator. Holders of each ticked role can
        recruit this role with their referral code. Leave all unticked for a top-level role.
      </p>
      <div className="perm-grid">
        {roles
          .filter((r) => r.id !== exclude && r.status !== ROLE_STATUS.retired)
          .map((r) => (
            <label key={r.id} className="perm-check">
              <input type="checkbox" checked={value.has(r.slug)} onChange={() => toggle(r.slug)} disabled={disabled} />
              <span>
                <strong>{r.title}</strong>
                <div className="muted">{r.slug}</div>
              </span>
            </label>
          ))}
      </div>
      {value.size === 0 ? <p className="muted" style={{ margin: '6px 0 0' }}>No upline — members sit at the top of the tree.</p> : null}
    </div>
  )
}

function ChildrenPicker({
  roles,
  value,
  onChange,
  exclude,
  disabled,
}: {
  roles: RoleView[]
  value: Set<string>
  onChange: (v: Set<string>) => void
  exclude: number
  disabled?: boolean
}) {
  const toggle = (slug: string) => {
    const next = new Set(value)
    if (next.has(slug)) next.delete(slug)
    else next.add(slug)
    onChange(next)
  }
  return (
    <div>
      <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6 }}>Roles this one may recruit (referral codes)</div>
      <p className="muted" style={{ marginTop: 0 }}>
        Someone holding this role gets a referral code; people who redeem it apply for one of the roles ticked here and
        land directly under them — after approval.
      </p>
      <div className="perm-grid">
        {roles
          .filter((r) => r.id !== exclude && r.status !== ROLE_STATUS.retired)
          .map((r) => (
            <label key={r.id} className="perm-check">
              <input type="checkbox" checked={value.has(r.slug)} onChange={() => toggle(r.slug)} disabled={disabled} />
              <span>
                <strong>{r.title}</strong>
                <div className="muted">{r.slug}</div>
              </span>
            </label>
          ))}
      </div>
    </div>
  )
}

function PolicyEditor({ value, onChange, disabled }: { value: Policy; onChange: (p: Policy) => void; disabled?: boolean }) {
  return (
    <div>
      <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6 }}>Approval & recruitment policy</div>
      <div className="perm-grid">
        <Select
          label="Who approves requests"
          value={value.approval.mode}
          disabled={disabled}
          onChange={(e) => onChange({ ...value, approval: { ...value.approval, mode: e.target.value as Policy['approval']['mode'] } })}
        >
          <option value="parent">Upline chain or platform admins</option>
          <option value="platform">Platform admins only</option>
        </Select>
        <Field
          label="Requests expire after (hours)"
          type="number"
          min={1}
          max={8760}
          disabled={disabled}
          value={value.approval.ttl_hours}
          onChange={(e) => onChange({ ...value, approval: { ...value.approval, ttl_hours: Math.max(1, Number(e.target.value) || 1) } })}
        />
        <label className="perm-check">
          <input
            type="checkbox"
            disabled={disabled}
            checked={value.recruitment.self_apply}
            onChange={(e) => onChange({ ...value, recruitment: { ...value.recruitment, self_apply: e.target.checked } })}
          />
          <span>
            <strong>People may apply without a code</strong>
            <div className="muted">Application still needs approval.</div>
          </span>
        </label>
        <label className="perm-check">
          <input
            type="checkbox"
            disabled={disabled}
            checked={value.recruitment.referral_apply}
            onChange={(e) => onChange({ ...value, recruitment: { ...value.recruitment, referral_apply: e.target.checked } })}
          />
          <span>
            <strong>People may apply with a referral code</strong>
            <div className="muted">The code owner becomes their upline.</div>
          </span>
        </label>
      </div>
      <div style={{ fontSize: 13, fontWeight: 700, margin: '14px 0 6px' }}>Member self-service</div>
      <div className="muted" style={{ fontSize: 12, marginBottom: 8 }}>
        What members of this role may do about their own place in the tree, from the app. Everything is off by
        default and re-checked server-side.
      </div>
      <div className="perm-grid">
        <label className="perm-check">
          <input
            type="checkbox"
            disabled={disabled}
            checked={mem(value).leave_upline}
            onChange={(e) => onChange({ ...value, membership: { ...mem(value), leave_upline: e.target.checked } })}
          />
          <span>
            <strong>Can leave upline</strong>
            <div className="muted">Detaches at once with a required reason; the upline is informed. Role and downline are kept.</div>
          </span>
        </label>
        <label className="perm-check">
          <input
            type="checkbox"
            disabled={disabled}
            checked={mem(value).remove_downline}
            onChange={(e) => onChange({ ...value, membership: { ...mem(value), remove_downline: e.target.checked } })}
          />
          <span>
            <strong>Can remove downline</strong>
            <div className="muted">A direct downline can be removed from the role with a required reason; the member is informed.</div>
          </span>
        </label>
        <label className="perm-check">
          <input
            type="checkbox"
            disabled={disabled}
            checked={mem(value).shift_upline}
            onChange={(e) => onChange({ ...value, membership: { ...mem(value), shift_upline: e.target.checked } })}
          />
          <span>
            <strong>Can shift to another upline</strong>
            <div className="muted">The member asks with the new upline's referral code; the new upline approves or rejects.</div>
          </span>
        </label>
      </div>
    </div>
  )
}

/** Roles saved before the self-service switches have no membership section. */
const mem = (p: Policy) => p.membership ?? { leave_upline: false, remove_downline: false, shift_upline: false }

function PermissionReachEditor({
  perms,
  reaches,
  value,
  onChange,
  disabled,
}: {
  perms: Perm[]
  reaches: EngineCatalog['reaches']
  value: Record<string, number>
  onChange: (v: Record<string, number>) => void
  disabled?: boolean
}) {
  const byCategory = useMemo(() => {
    const map = new Map<string, Perm[]>()
    for (const p of perms) {
      const list = map.get(p.category) || []
      list.push(p)
      map.set(p.category, list)
    }
    return [...map.entries()].sort(([a], [b]) => a.localeCompare(b))
  }, [perms])

  const set = (slug: string, reach: number) => {
    const next = { ...value }
    if (reach > 0) next[slug] = reach
    else delete next[slug]
    onChange(next)
  }

  return (
    <div>
      {byCategory.map(([cat, list]) => (
        <div key={cat} style={{ marginTop: 14 }}>
          <div style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 8 }}>{cat}</div>
          <div className="perm-grid">
            {list.map((p) => {
              const r = value[p.slug] || 0
              return (
                <label key={p.slug} className={`perm-check${r ? ' is-on' : ''}`} style={{ alignItems: 'center' }}>
                  <input type="checkbox" checked={r > 0} disabled={disabled} onChange={(e) => set(p.slug, e.target.checked ? 4 : 0)} />
                  <span style={{ flex: 1, minWidth: 0 }}>
                    <strong>{p.name}</strong>
                    <div className="muted">{p.slug}</div>
                  </span>
                  {r > 0 ? (
                    <select className="org-select" style={{ width: 'auto', height: 30, fontSize: 12 }} value={r} disabled={disabled} onChange={(e) => set(p.slug, Number(e.target.value))} aria-label={`Reach for ${p.name}`}>
                      {reaches.map((x) => (
                        <option key={x.id} value={x.id}>
                          {reachLabel[x.id] || x.name}
                        </option>
                      ))}
                    </select>
                  ) : null}
                </label>
              )
            })}
          </div>
        </div>
      ))}
    </div>
  )
}

function slugify(s: string): string {
  return s
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .replace(/^[^a-z]+/, '')
}
