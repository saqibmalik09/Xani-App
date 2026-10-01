import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import {
  REQ_KIND,
  REQ_STATUS,
  kindLabel,
  personLabel,
  reqStatusLabel,
  rolesApi,
  when,
  type CodeInfo,
  type MembershipBrief,
  type Organisation,
  type RequestView,
  type RoleView,
} from '../lib/roles'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { Drawer, MembershipPicker, Select, StatusPill, UserPicker } from '../ui/Org'
import { DynamicForm, FormAnswers, validateAnswers } from '../ui/RoleForm'

export function ApprovalsPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { can } = useAuth()
  const [status, setStatus] = useState<number>(REQ_STATUS.pending)
  const [kind, setKind] = useState<number>(0)
  const [role, setRole] = useState('')
  const [roles, setRoles] = useState<RoleView[]>([])
  const [items, setItems] = useState<RequestView[]>([])
  const [next, setNext] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [assigning, setAssigning] = useState(false)

  const canAssign = can('memberships.create')

  useEffect(() => {
    void rolesApi.list().then((r) => {
      if (r.ok) setRoles(r.data.items || [])
    })
  }, [])

  const load = useCallback(
    async (cursor: string, append: boolean) => {
      setLoading(true)
      const r = await rolesApi.inbox({ status, kind, role, cursor })
      setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setItems((prev) => (append ? [...prev, ...(r.data.items || [])] : r.data.items || []))
      setNext(r.data.next_cursor || '')
    },
    [status, kind, role],
  )

  useEffect(() => {
    void load('', false)
  }, [load])

  const flash = (msg: string) => {
    setOk(msg)
    setError('')
    window.setTimeout(() => setOk(''), 4000)
  }

  /** One decision updates the row in place; no refetch of the whole page. */
  const replace = (v: RequestView) => {
    setItems((prev) => {
      const idx = prev.findIndex((x) => x.id === v.id)
      if (idx < 0) return prev
      if (status && v.status !== status) return prev.filter((x) => x.id !== v.id)
      const copy = [...prev]
      copy[idx] = v
      return copy
    })
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div className="page-head" style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <h1>Approvals</h1>
          <p>
            Every role change is a request: assign, application, transfer, removal, reactivation. Nothing takes effect
            until an eligible approver says yes. You only see requests inside your reach.
          </p>
        </div>
        {canAssign ? <Button onClick={() => setAssigning(true)}>Assign a role</Button> : null}
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <Card>
        <div className="org-toolbar">
          <Select label="Status" value={status} onChange={(e) => setStatus(Number(e.target.value))}>
            {Object.entries(reqStatusLabel)
              .filter(([k]) => Number(k) !== REQ_STATUS.draft)
              .map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
          </Select>
          <Select label="Kind" value={kind} onChange={(e) => setKind(Number(e.target.value))}>
            <option value={0}>All kinds</option>
            {Object.entries(kindLabel).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </Select>
          <Select label="Role" value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="">All roles</option>
            {roles.map((r) => (
              <option key={r.id} value={r.slug}>
                {r.title}
              </option>
            ))}
          </Select>
        </div>

        {loading && items.length === 0 ? (
          <PageLoader label="Loading requests…" />
        ) : items.length === 0 ? (
          <p className="muted" style={{ marginTop: 16 }}>
            No {reqStatusLabel[status].toLowerCase()} requests{kind ? ` of kind “${kindLabel[kind]}”` : ''}.
          </p>
        ) : (
          <div className="table-wrap" style={{ marginTop: 16 }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Request</th>
                  <th>Who</th>
                  <th>Role</th>
                  <th>Upline</th>
                  <th>Requested by</th>
                  <th>When</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((v) => (
                  <RequestRow key={v.id} v={v} onOpen={() => navigate(`/approvals/${v.id}`)} onChanged={replace} onFlash={flash} onError={setError} />
                ))}
              </tbody>
            </table>
          </div>
        )}
        {next ? (
          <div className="form-actions" style={{ marginTop: 12 }}>
            <Button variant="secondary" loading={loading} onClick={() => void load(next, true)}>
              Load more
            </Button>
          </div>
        ) : null}
      </Card>

      {id ? (
        <RequestDrawer
          id={id}
          onClose={() => navigate('/approvals')}
          onChanged={(v) => {
            replace(v)
          }}
          onFlash={flash}
        />
      ) : null}

      {assigning ? (
        <AssignDrawer
          roles={roles}
          onClose={() => setAssigning(false)}
          onDone={(v, msg) => {
            setAssigning(false)
            flash(msg)
            if (v.status === status) setItems((prev) => [v, ...prev.filter((x) => x.id !== v.id)])
          }}
        />
      ) : null}
    </div>
  )
}

function describe(v: RequestView): string {
  switch (v.kind) {
    case REQ_KIND.assign:
      return `Add as ${v.role.title}${v.parent ? ` under ${personLabel(v.parent)}` : ' at the top'}`
    case REQ_KIND.apply:
      return `Applying for ${v.role.title}${v.parent ? ` under ${personLabel(v.parent)}` : ''}`
    case REQ_KIND.transfer:
      return `Move ${v.children_only ? 'downline only ' : ''}to ${v.parent ? personLabel(v.parent) : 'the top'}${v.downline_count ? ` (${v.downline_count} people)` : ''}`
    case REQ_KIND.revoke:
      return `Remove from ${v.role.title}`
    case REQ_KIND.reactivate:
      return `Reactivate as ${v.role.title}`
    default:
      return v.kind_label
  }
}

function RequestRow({
  v,
  onOpen,
  onChanged,
  onFlash,
  onError,
}: {
  v: RequestView
  onOpen: () => void
  onChanged: (v: RequestView) => void
  onFlash: (m: string) => void
  onError: (m: string) => void
}) {
  const [busy, setBusy] = useState<'approve' | 'reject' | null>(null)
  const quick = async (what: 'approve' | 'reject') => {
    let text = ''
    if (what === 'reject') {
      const t = window.prompt('Reason for rejecting (shown to the requester):', '')
      if (t === null) return
      text = t
    }
    setBusy(what)
    const r = what === 'approve' ? await rolesApi.approve(v.id, '') : await rolesApi.reject(v.id, text)
    setBusy(null)
    if (!r.ok) {
      onError(r.message)
      return
    }
    onChanged(r.data)
    onFlash(what === 'approve' ? `Approved ${v.code}.` : `Rejected ${v.code}.`)
  }
  return (
    <tr>
      <td>
        <button type="button" className="chip" onClick={onOpen} style={{ marginBottom: 4 }}>
          {v.code}
        </button>
        <div className="muted" style={{ fontSize: 12 }}>
          {kindLabel[v.kind]} · {describe(v)}
        </div>
      </td>
      <td>
        <Link to={`/members?q=${encodeURIComponent(v.subject.username)}`}>{personLabel(v.subject)}</Link>
        <div className="muted">@{v.subject.username}</div>
      </td>
      <td>
        <Link to={`/roles/${v.role.slug}`}>{v.role.title}</Link>
        <div className="muted">{v.role.slug}</div>
      </td>
      <td>
        {v.parent ? (
          <Link to={`/members/${v.parent.id}`}>{personLabel(v.parent)}</Link>
        ) : (
          <span className="muted">—</span>
        )}
      </td>
      <td>
        {personLabel(v.requested_by_user)}
        {v.requested_by === v.subject_user_id ? <div className="muted">self</div> : null}
      </td>
      <td>
        {when(v.created_at)}
        {v.status === REQ_STATUS.pending && v.expires_at ? <div className="muted">expires {when(v.expires_at)}</div> : null}
      </td>
      <td>
        <StatusPill kind="q" status={v.status} />
      </td>
      <td style={{ whiteSpace: 'nowrap' }}>
        {v.status === REQ_STATUS.pending && v.can_decide ? (
          <span style={{ display: 'inline-flex', gap: 6 }}>
            <Button size="sm" loading={busy === 'approve'} disabled={busy !== null} onClick={() => void quick('approve')}>
              Approve
            </Button>
            <Button size="sm" variant="danger" loading={busy === 'reject'} disabled={busy !== null} onClick={() => void quick('reject')}>
              Reject
            </Button>
          </span>
        ) : (
          <Button size="sm" variant="ghost" onClick={onOpen}>
            View
          </Button>
        )}
      </td>
    </tr>
  )
}

function RequestDrawer({ id, onClose, onChanged, onFlash }: { id: string; onClose: () => void; onChanged: (v: RequestView) => void; onFlash: (m: string) => void }) {
  const [v, setV] = useState<RequestView | null>(null)
  const [error, setError] = useState('')
  const [text, setText] = useState('')
  const [busy, setBusy] = useState<'approve' | 'reject' | 'cancel' | null>(null)

  useEffect(() => {
    setV(null)
    void rolesApi.request(id).then((r) => {
      if (r.ok) setV(r.data)
      else setError(r.message)
    })
  }, [id])

  const act = async (what: 'approve' | 'reject' | 'cancel') => {
    if (!v) return
    if (what !== 'approve' && !text.trim()) {
      setError(what === 'reject' ? 'Give a reason so the requester knows why.' : 'Say why you are cancelling.')
      return
    }
    setBusy(what)
    const r = what === 'approve' ? await rolesApi.approve(v.id, text) : what === 'reject' ? await rolesApi.reject(v.id, text) : await rolesApi.cancel(v.id, text)
    setBusy(null)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setV(r.data)
    onChanged(r.data)
    onFlash(`${v.code} ${reqStatusLabel[r.data.status].toLowerCase()}.`)
  }

  return (
    <Drawer title={v ? `${kindLabel[v.kind]} · ${v.code}` : 'Request'} onClose={onClose}>
      {error ? <div className="err-banner">{error}</div> : null}
      {!v ? (
        <PageLoader />
      ) : (
        <>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <StatusPill kind="q" status={v.status} />
            <span className="muted">{describe(v)}</span>
          </div>
          <dl className="org-kv">
            <dt>Person</dt>
            <dd>
              {personLabel(v.subject)} <span className="muted">@{v.subject.username}</span>
            </dd>
            <dt>Role</dt>
            <dd>
              <Link to={`/roles/${v.role.slug}`}>{v.role.title}</Link> <span className="muted">{v.role.slug}</span>
            </dd>
            {v.membership ? (
              <>
                <dt>Membership</dt>
                <dd>
                  <Link to={`/members/${v.membership.id}`}>
                    {v.membership.role.title}
                    {v.membership.referral_code ? ` · code ${v.membership.referral_code}` : ''}
                  </Link>
                </dd>
              </>
            ) : null}
            <dt>{v.kind === REQ_KIND.transfer ? 'New upline' : 'Upline'}</dt>
            <dd>
              {v.parent ? (
                <Link to={`/members/${v.parent.id}`}>
                  {personLabel(v.parent)} · {v.parent.role.title}
                </Link>
              ) : (
                <span className="muted">Top of the tree</span>
              )}
            </dd>
            {v.referral_code ? (
              <>
                <dt>Joined with code</dt>
                <dd>
                  <span className="org-code">{v.referral_code}</span>
                  {v.parent ? <span className="muted"> · {personLabel(v.parent)}'s code</span> : null}
                </dd>
              </>
            ) : null}
            {v.kind === REQ_KIND.transfer ? (
              <>
                <dt>Moving</dt>
                <dd>
                  {v.children_only ? 'Downline only (member stays)' : 'Member and whole downline'}
                  {v.downline_count ? ` · ${v.downline_count} people` : ''}
                </dd>
              </>
            ) : null}
            {v.kind === REQ_KIND.revoke ? (
              <>
                <dt>Their downline</dt>
                <dd>{v.disposition === 2 ? 'Move up to their upline' : v.disposition === 3 ? 'Suspend everyone below' : 'Keep in place'}</dd>
              </>
            ) : null}
            <dt>Requested by</dt>
            <dd>
              {personLabel(v.requested_by_user)} · {when(v.created_at)}
            </dd>
            {v.note ? (
              <>
                <dt>Note</dt>
                <dd>{v.note}</dd>
              </>
            ) : null}
            {v.decided_by_user ? (
              <>
                <dt>Decided by</dt>
                <dd>
                  {personLabel(v.decided_by_user)} · {when(v.decided_at)}
                </dd>
              </>
            ) : null}
            {v.reason ? (
              <>
                <dt>Decision note</dt>
                <dd>{v.reason}</dd>
              </>
            ) : null}
            {v.status === REQ_STATUS.pending && v.expires_at ? (
              <>
                <dt>Expires</dt>
                <dd>{when(v.expires_at)}</dd>
              </>
            ) : null}
          </dl>

          {v.form_data && Object.keys(v.form_data).length > 0 ? (
            <section>
              <h3 style={{ fontSize: 14, margin: '8px 0' }}>Application answers</h3>
              <FormAnswers fields={v.form} data={v.form_data} />
            </section>
          ) : null}

          {v.status === REQ_STATUS.pending && (v.can_decide || v.can_cancel) ? (
            <>
              <TextArea label={v.can_decide ? 'Note or reason' : 'Reason for cancelling'} value={text} onChange={(e) => setText(e.target.value)} maxLength={500} placeholder="Optional for approvals; required for reject / cancel." />
              <div className="form-actions">
                {v.can_decide ? (
                  <>
                    <Button loading={busy === 'approve'} disabled={busy !== null} onClick={() => void act('approve')}>
                      Approve
                    </Button>
                    <Button variant="danger" loading={busy === 'reject'} disabled={busy !== null} onClick={() => void act('reject')}>
                      Reject
                    </Button>
                  </>
                ) : null}
                {v.can_cancel ? (
                  <Button variant="secondary" loading={busy === 'cancel'} disabled={busy !== null} onClick={() => void act('cancel')}>
                    Cancel request
                  </Button>
                ) : null}
              </div>
            </>
          ) : v.status === REQ_STATUS.pending ? (
            <p className="muted">Waiting for an approver above this person in the tree.</p>
          ) : null}
        </>
      )}
    </Drawer>
  )
}

/** What the chosen person already holds, so the assigner sees their tree before adding to it. */
function CurrentRoles({ userId }: { userId: string }) {
  const [org, setOrg] = useState<Organisation | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading')
  useEffect(() => {
    let alive = true
    setState('loading')
    setOrg(null)
    void rolesApi.organisation(userId).then((r) => {
      if (!alive) return
      if (r.ok) {
        setOrg(r.data)
        setState('ready')
      } else setState('error')
    })
    return () => {
      alive = false
    }
  }, [userId])

  if (state === 'loading') return <p className="muted" style={{ margin: 0, fontSize: 12 }}>Checking their current roles…</p>
  if (state === 'error') return <p className="muted" style={{ margin: 0, fontSize: 12 }}>Could not load their current roles.</p>
  const list = org?.memberships || []
  if (list.length === 0) return <p className="muted" style={{ margin: 0, fontSize: 12 }}>No roles yet — this will be their first.</p>
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>Current roles</span>
      {list.map((m) => {
        const upline = org?.uplines?.[m.id]?.[0]
        return (
          <div key={m.id} className="role-row" style={{ cursor: 'default', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ minWidth: 0 }}>
              <div className="role-row-title">
                <Link to={`/roles/${m.role.slug}`}>{m.role.title}</Link> <StatusPill kind="s" status={m.status} />
              </div>
              <div className="role-row-meta">
                {upline ? (
                  <>
                    under <Link to={`/members/${upline.id}`}>{personLabel(upline)}</Link> ({upline.role.title})
                  </>
                ) : (
                  'top of the tree'
                )}
                {' · '}
                {m.direct_children} direct downline
                {m.referral_code ? (
                  <>
                    {' · code '}
                    <span className="org-code">{m.referral_code}</span>
                  </>
                ) : null}
              </div>
            </div>
            <Link to={`/members/${m.id}`} className="chip">
              Open
            </Link>
          </div>
        )
      })}
    </div>
  )
}

type UplineMode = 'member' | 'code'

function AssignDrawer({ roles, onClose, onDone }: { roles: RoleView[]; onClose: () => void; onDone: (v: RequestView, msg: string) => void }) {
  const [user, setUser] = useState<{ id: string; username: string; full_name: string } | null>(null)
  const [roleSlug, setRoleSlug] = useState('')
  const [uplineRoleSlug, setUplineRoleSlug] = useState('')
  const [mode, setMode] = useState<UplineMode>('member')
  const [parent, setParent] = useState<MembershipBrief | null>(null)
  const [codeInput, setCodeInput] = useState('')
  const [code, setCode] = useState<CodeInfo | null>(null)
  const [codeState, setCodeState] = useState<'idle' | 'checking' | 'ok' | 'bad'>('idle')
  const [codeMsg, setCodeMsg] = useState('')
  const [approveNow, setApproveNow] = useState(false)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState<RequestView | null>(null)

  const role = useMemo(() => roles.find((r) => r.slug === roleSlug) || null, [roles, roleSlug])
  const parentRole = useMemo(() => (role?.parent_role_id ? roles.find((r) => r.id === role.parent_role_id) || null : null), [roles, role])
  /** Roles whose members may be the upline: every "Reports to" role, anyone recruiting this role, and itself when same-role chains are allowed. */
  const uplineRoles = useMemo(() => {
    if (!role) return [] as RoleView[]
    const ids = new Set<number>(role.upline_role_ids || [])
    if (parentRole) ids.add(parentRole.id)
    for (const r of roles) {
      if (r.id === role.id) continue
      if (r.child_role_ids?.includes(role.id)) ids.add(r.id)
      if (r.child_roles?.some((c) => c.id === role.id)) ids.add(r.id)
    }
    if (role.allow_same_role_child) ids.add(role.id)
    return roles.filter((r) => r.status === 2 && ids.has(r.id))
  }, [roles, role, parentRole])
  const uplineRequired = Boolean(parentRole)
  const formFields = role?.form || []
  const [answers, setAnswers] = useState<Record<string, string>>({})
  const [answerErrors, setAnswerErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    setParent(null)
    setCode(null)
    setCodeInput('')
    setCodeState('idle')
    setCodeMsg('')
    setAnswers({})
    setAnswerErrors({})
    setUplineRoleSlug(parentRole?.slug || uplineRoles[0]?.slug || '')
  }, [roleSlug, parentRole, uplineRoles])

  useEffect(() => setParent(null), [uplineRoleSlug])

  // Verify a typed referral code: it must exist, belong to an active member, and be able to onboard this role.
  useEffect(() => {
    const raw = codeInput.trim()
    setCode(null)
    if (!raw) {
      setCodeState('idle')
      setCodeMsg('')
      return
    }
    setCodeState('checking')
    setCodeMsg('')
    const t = setTimeout(async () => {
      const r = await rolesApi.lookupCode(raw)
      if (!r.ok) {
        setCodeState('bad')
        setCodeMsg(r.message || 'No member has this code.')
        return
      }
      if (role && !r.data.target_roles.some((t) => t.id === role.id)) {
        setCodeState('bad')
        setCodeMsg(`${personLabel(r.data.owner)}'s code belongs to a ${r.data.membership.role.title} and cannot onboard a ${role.title}.`)
        return
      }
      setCode(r.data)
      setCodeState('ok')
      setCodeMsg('')
    }, 350)
    return () => clearTimeout(t)
  }, [codeInput, role])

  const uplineChosen = mode === 'code' ? Boolean(code) : Boolean(parent)
  const uplineLabel = mode === 'code' && code ? `${personLabel(code.owner)} (${code.membership.role.title})` : parent ? `${personLabel(parent)} (${parent.role.title})` : ''

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!user || !role) return
    if (uplineRequired && !uplineChosen) {
      setError(`${role.title} must sit under a ${parentRole?.title}. Pick the upline or enter their referral code.`)
      return
    }
    if (mode === 'code' && codeInput.trim() && !code) {
      setError(codeMsg || 'That referral code could not be verified.')
      return
    }
    const filled = Object.fromEntries(Object.entries(answers).filter(([, v]) => v.trim() !== ''))
    if (Object.keys(filled).length > 0) {
      const errs = validateAnswers(formFields, filled)
      if (Object.keys(errs).length > 0) {
        setAnswerErrors(errs)
        setError('Check the application answers.')
        return
      }
    }
    setAnswerErrors({})
    setBusy(true)
    setError('')
    const r = await rolesApi.assign({
      user_id: user.id,
      role: role.slug,
      parent_id: mode === 'member' ? parent?.id : undefined,
      referral_code: mode === 'code' && code ? code.code : undefined,
      approve_now: approveNow,
      note: note.trim(),
      form: Object.keys(filled).length > 0 ? filled : undefined,
    })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      if (r.fields) setAnswerErrors(r.fields)
      return
    }
    setResult(r.data)
    onDone(
      r.data,
      r.data.status === REQ_STATUS.approved
        ? `${personLabel(user)} is now ${role.title}${uplineLabel ? ` under ${uplineLabel}` : ''}.`
        : `Request ${r.data.code} sent to ${uplineLabel || 'the approvers'} for approval.`,
    )
  }

  return (
    <Drawer title="Assign a role" onClose={onClose}>
      {error ? <div className="err-banner">{error}</div> : null}
      {result ? (
        <div className="ok-banner">
          {result.status === REQ_STATUS.approved
            ? `Done — ${personLabel(result.subject)} is now ${result.role.title} and the membership is active.`
            : `Request ${result.code} is waiting for approval${result.parent ? ` from ${personLabel(result.parent)}` : ''}.`}
        </div>
      ) : (
        <form className="form-grid" onSubmit={(e) => void submit(e)}>
          <UserPicker label="Person" value={user} onChange={setUser} hint="Anyone with an account. They are told once it is approved." />
          {user ? <CurrentRoles userId={user.id} /> : null}
          <Select label="Role" value={roleSlug} onChange={(e) => setRoleSlug(e.target.value)} required>
            <option value="">Choose a role…</option>
            {roles
              .filter((r) => r.status === 2)
              .map((r) => (
                <option key={r.id} value={r.slug}>
                  {r.title} · {r.slug}
                </option>
              ))}
          </Select>
          {role ? (
            <>
              {uplineRoles.length > 0 ? (
                <Select
                  label={uplineRequired ? 'Whose referral code' : 'Whose referral code (optional)'}
                  value={uplineRoleSlug}
                  onChange={(e) => setUplineRoleSlug(e.target.value)}
                  hint={
                    uplineRequired
                      ? `Every ${role.title} joins under someone. That person's code is recorded and they approve, unless you approve now.`
                      : 'Leave the upline empty to place them at the top of the tree.'
                  }
                >
                  {!uplineRequired ? <option value="">Nobody — top of the tree</option> : null}
                  {uplineRoles.map((r) => (
                    <option key={r.id} value={r.slug}>
                      {r.title} · {r.slug}
                    </option>
                  ))}
                </Select>
              ) : null}
              {uplineRoleSlug || !uplineRequired ? (
                <div className="tabs" role="tablist" style={{ marginBottom: 0 }}>
                  <button type="button" role="tab" aria-selected={mode === 'member'} className={`tab${mode === 'member' ? ' is-active' : ''}`} onClick={() => setMode('member')}>
                    Pick the member
                  </button>
                  <button type="button" role="tab" aria-selected={mode === 'code'} className={`tab${mode === 'code' ? ' is-active' : ''}`} onClick={() => setMode('code')}>
                    Enter their referral code
                  </button>
                </div>
              ) : null}
              {mode === 'member' ? (
                <MembershipPicker
                  label={uplineRequired ? 'Upline' : 'Upline (optional)'}
                  value={parent}
                  onChange={setParent}
                  role={uplineRoleSlug || undefined}
                  hint={uplineRequired ? 'Search by name, username or their referral code.' : 'Leave empty to place them at the top of the tree.'}
                />
              ) : (
                <Field
                  label="Referral code"
                  value={codeInput}
                  inputMode="numeric"
                  autoComplete="off"
                  onChange={(e) => setCodeInput(e.target.value)}
                  placeholder="e.g. 482913"
                  error={codeState === 'bad' ? codeMsg : undefined}
                  hint={
                    codeState === 'checking'
                      ? 'Checking…'
                      : code
                        ? `Belongs to ${personLabel(code.owner)} · ${code.membership.role.title}. ${personLabel(user || code.owner)} will be placed under them.`
                        : 'The code of the person they should report to. We check it exists and can onboard this role.'
                  }
                />
              )}
            </>
          ) : null}
          {role && formFields.length > 0 ? (
            <section>
              <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 4 }}>Application form · {role.title}</div>
              <p className="muted" style={{ marginTop: 0 }}>
                The questions a candidate answers in the app. Fill them in on their behalf, or leave blank if you are
                placing them directly as a platform admin.
              </p>
              <DynamicForm fields={formFields} value={answers} onChange={setAnswers} errors={answerErrors} />
            </section>
          ) : null}
          <TextArea label="Note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} placeholder="Why this person, for the approver and the audit trail." />
          <label className="perm-check">
            <input type="checkbox" checked={approveNow} onChange={(e) => setApproveNow(e.target.checked)} />
            <span>
              <strong>Approve now</strong>
              <div className="muted">
                Activate the membership immediately instead of sending it to the upline for approval. Only works if you are allowed to approve this placement yourself.
              </div>
            </span>
          </label>
          <div className="form-actions">
            <Button type="submit" loading={busy} disabled={!user || !role || codeState === 'checking'}>
              {approveNow ? 'Assign now' : 'Send for approval'}
            </Button>
            <Button variant="ghost" type="button" onClick={onClose}>
              Close
            </Button>
          </div>
        </form>
      )}
    </Drawer>
  )
}
