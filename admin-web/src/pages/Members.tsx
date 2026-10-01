import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import {
  DISPOSITION,
  MEMBER_STATUS,
  REQ_STATUS,
  kindLabel,
  memberStatusLabel,
  personLabel,
  rolesApi,
  when,
  type Membership,
  type MembershipBrief,
  type RequestView,
  type RoleView,
  type SummaryRow,
  type TransferPreview,
} from '../lib/roles'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { Chain, Drawer, MembershipPicker, Select, StatusPill } from '../ui/Org'

// ─── list ────────────────────────────────────────────────────────────────────

export function MembersPage() {
  const [params, setParams] = useSearchParams()
  const [roles, setRoles] = useState<RoleView[]>([])
  const [q, setQ] = useState(params.get('q') || '')
  const role = params.get('role') || ''
  const status = Number(params.get('status') || 0)
  const [items, setItems] = useState<Membership[]>([])
  const [next, setNext] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    void rolesApi.list().then((r) => {
      if (r.ok) setRoles(r.data.items || [])
    })
  }, [])

  const setParam = (k: string, v: string) => {
    const p = new URLSearchParams(params)
    if (v) p.set(k, v)
    else p.delete(k)
    setParams(p, { replace: true })
  }

  const load = useCallback(
    async (cursor: string, append: boolean) => {
      setLoading(true)
      const r = await rolesApi.memberships({ role, status, q: q.trim(), cursor })
      setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setItems((prev) => (append ? [...prev, ...(r.data.items || [])] : r.data.items || []))
      setNext(r.data.next_cursor || '')
    },
    [role, status, q],
  )

  useEffect(() => {
    const t = setTimeout(() => void load('', false), 200)
    return () => clearTimeout(t)
  }, [load])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div className="page-head">
        <h1>Members</h1>
        <p>Everyone holding a role, within your reach. Open a member to see their upline, downline and history.</p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      <Card>
        <div className="org-toolbar">
          <div className="org-toolbar-grow">
            <Field
              label="Search"
              value={q}
              onChange={(e) => {
                setQ(e.target.value)
                setParam('q', e.target.value)
              }}
              placeholder="Username, name or referral code"
            />
          </div>
          <Select label="Role" value={role} onChange={(e) => setParam('role', e.target.value)}>
            <option value="">All roles</option>
            {roles.map((r) => (
              <option key={r.id} value={r.slug}>
                {r.title}
              </option>
            ))}
          </Select>
          <Select label="Status" value={status} onChange={(e) => setParam('status', e.target.value === '0' ? '' : e.target.value)}>
            <option value={0}>Any status</option>
            {Object.entries(memberStatusLabel).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </Select>
        </div>
        {loading && items.length === 0 ? (
          <PageLoader label="Loading members…" />
        ) : items.length === 0 ? (
          <p className="muted" style={{ marginTop: 16 }}>
            No members match.
          </p>
        ) : (
          <div className="table-wrap" style={{ marginTop: 16 }}>
            <MemberTable items={items} />
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
    </div>
  )
}

function MemberTable({ items, showDepth }: { items: Membership[]; showDepth?: boolean }) {
  return (
    <table className="table">
      <thead>
        <tr>
          <th>Member</th>
          <th>Role</th>
          <th>Their code</th>
          <th>Joined with</th>
          {showDepth ? <th>Level</th> : null}
          <th>Status</th>
          <th>Direct downline</th>
          <th>Since</th>
        </tr>
      </thead>
      <tbody>
        {items.map((m) => (
          <tr key={m.id}>
            <td>
              <Link to={`/members/${m.id}`}>{personLabel(m)}</Link>
              <div className="muted">@{m.username}</div>
            </td>
            <td>
              <Link to={`/roles/${m.role.slug}`}>{m.role.title}</Link>
            </td>
            <td>{m.referral_code ? <span className="org-code">{m.referral_code}</span> : <span className="muted">—</span>}</td>
            <td>{m.joined_code ? <span className="org-code">{m.joined_code}</span> : <span className="muted">—</span>}</td>
            {showDepth ? <td>{m.tree_depth ?? m.depth}</td> : null}
            <td>
              <StatusPill kind="s" status={m.status} />
            </td>
            <td>{m.direct_children}</td>
            <td>{when(m.approved_at || m.created_at)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

// ─── detail ──────────────────────────────────────────────────────────────────

type Action = 'suspend' | 'reactivate' | 'block' | 'unblock' | 'transfer' | 'revoke' | 'reactivation-request' | null

export function MemberDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { can } = useAuth()
  const [m, setM] = useState<Membership | null>(null)
  const [upline, setUpline] = useState<Membership[]>([])
  const [summary, setSummary] = useState<SummaryRow[]>([])
  const [requests, setRequests] = useState<RequestView[]>([])
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [action, setAction] = useState<Action>(null)

  const load = useCallback(async () => {
    const r = await rolesApi.membership(id)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setM(r.data)
    const [u, s, q] = await Promise.all([rolesApi.upline(id), rolesApi.summary(id), rolesApi.userRequests(r.data.user_id)])
    if (u.ok) setUpline(u.data.items || [])
    if (s.ok) setSummary(s.data.items || [])
    if (q.ok) setRequests(q.data.items || [])
  }, [id])

  useEffect(() => {
    setM(null)
    void load()
  }, [load])

  const flash = (msg: string) => {
    setOk(msg)
    setError('')
    window.setTimeout(() => setOk(''), 5000)
  }

  if (error && !m) {
    return (
      <div>
        <div className="err-banner">{error}</div>
        <Button variant="ghost" onClick={() => navigate('/members')}>
          Back to members
        </Button>
      </div>
    )
  }
  if (!m) return <PageLoader label="Loading member…" />

  const active = m.status === MEMBER_STATUS.active
  const suspended = m.status === MEMBER_STATUS.suspended
  const blocked = m.status === MEMBER_STATUS.blocked
  const revoked = m.status === MEMBER_STATUS.revoked
  const totals = summary.reduce(
    (acc, row) => {
      acc.all += row.count
      if (row.status === MEMBER_STATUS.active) acc.active += row.count
      return acc
    },
    { all: 0, active: 0 },
  )
  const pending = requests.filter((r) => r.status === REQ_STATUS.pending)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div className="page-head" style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <p style={{ margin: '0 0 4px' }}>
            <Link to="/members">Members</Link> / @{m.username}
          </p>
          <h1 style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            {personLabel(m)} <StatusPill kind="s" status={m.status} />
          </h1>
          <p>
            @{m.username} · <Link to={`/roles/${m.role.slug}`}>{m.role.title}</Link> <span className="muted">· {m.role.slug}</span>
            {m.ended_at ? ` · ended ${when(m.ended_at)}${m.ended_reason ? ` — ${m.ended_reason}` : ''}` : ''}
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {can('admin.manage_users') ? (
            <Button variant="ghost" onClick={() => navigate(`/users/${m.user_id}`)}>
              Account
            </Button>
          ) : null}
          {!revoked && can('memberships.transfer') ? (
            <Button variant="secondary" onClick={() => setAction('transfer')}>
              Transfer
            </Button>
          ) : null}
          {active && can('memberships.suspend') ? (
            <Button variant="secondary" onClick={() => setAction('suspend')}>
              Suspend
            </Button>
          ) : null}
          {suspended && can('memberships.suspend') ? (
            <>
              <Button onClick={() => setAction('reactivate')}>Reactivate</Button>
              <Button variant="secondary" onClick={() => setAction('reactivation-request')}>
                Request reactivation
              </Button>
            </>
          ) : null}
          {!blocked && can('memberships.block') ? (
            <Button variant="danger" onClick={() => setAction('block')}>
              Block
            </Button>
          ) : null}
          {blocked && can('memberships.block') ? <Button onClick={() => setAction('unblock')}>Unblock</Button> : null}
          {!revoked && can('memberships.revoke') ? (
            <Button variant="danger" onClick={() => setAction('revoke')}>
              Remove
            </Button>
          ) : null}
          {revoked && can('memberships.create') ? (
            <Button onClick={() => navigate('/approvals')}>Assign again</Button>
          ) : null}
        </div>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      {pending.length ? (
        <div className="ok-banner" style={{ background: '#fff4d6', color: '#8a5a00', borderColor: '#f5d78e' }}>
          {pending.length} pending request{pending.length === 1 ? '' : 's'} for this person:{' '}
          {pending.map((r) => (
            <Link key={r.id} to={`/approvals/${r.id}`} style={{ marginRight: 8 }}>
              {kindLabel[r.kind]} {r.code}
            </Link>
          ))}
        </div>
      ) : null}

      <div className="org-stats">
        <div className="org-stat">
          <span>Direct downline</span>
          <strong>{m.direct_children}</strong>
        </div>
        <div className="org-stat">
          <span>Whole downline</span>
          <strong>{totals.all}</strong>
        </div>
        <div className="org-stat">
          <span>Active below</span>
          <strong>{totals.active}</strong>
        </div>
        <div className="org-stat">
          <span>Levels above</span>
          <strong>{upline.length}</strong>
        </div>
        <div className="org-stat">
          <span>Referral code</span>
          <strong style={{ fontSize: 14 }}>{m.referral_code || '—'}</strong>
        </div>
        <div className="org-stat">
          <span>Member since</span>
          <strong style={{ fontSize: 14 }}>{when(m.approved_at || m.created_at)}</strong>
        </div>
      </div>

      <Card title="Upline">
        <Chain upline={upline} self={m} />
        {m.approved_by ? (
          <p className="muted" style={{ marginBottom: 0 }}>
            Approved by <Link to={`/users/${m.approved_by}`}>{m.approved_by}</Link> · {when(m.approved_at)}
          </p>
        ) : null}
      </Card>

      {summary.length ? (
        <Card title="Downline by role">
          <div className="chip-row" style={{ marginTop: 0 }}>
            {summary.map((row) => (
              <span key={`${row.role.id}-${row.status}`} className="chip">
                {row.role.title} · {memberStatusLabel[row.status]?.toLowerCase()} · {row.count}
              </span>
            ))}
          </div>
        </Card>
      ) : null}

      <Downline id={m.id} />

      {requests.length ? (
        <Card title="Requests about this person">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Request</th>
                  <th>Kind</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>When</th>
                </tr>
              </thead>
              <tbody>
                {requests.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <Link to={`/approvals/${r.id}`}>{r.code}</Link>
                    </td>
                    <td>{kindLabel[r.kind]}</td>
                    <td>{r.role.title}</td>
                    <td>
                      <StatusPill kind="q" status={r.status} />
                    </td>
                    <td>{when(r.created_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      ) : null}

      {action === 'suspend' || action === 'reactivate' || action === 'block' || action === 'unblock' ? (
        <TransitionDrawer
          m={m}
          kind={action}
          onClose={() => setAction(null)}
          onDone={(next, msg) => {
            setM(next)
            setAction(null)
            flash(msg)
          }}
        />
      ) : null}
      {action === 'transfer' ? (
        <TransferDrawer
          m={m}
          upline={upline}
          onClose={() => setAction(null)}
          onDone={(v) => {
            setAction(null)
            flash(v.status === REQ_STATUS.approved ? 'Transfer completed.' : `Transfer request ${v.code} sent for approval.`)
            void load()
          }}
        />
      ) : null}
      {action === 'revoke' ? (
        <RevokeDrawer
          m={m}
          onClose={() => setAction(null)}
          onDone={(v) => {
            setAction(null)
            flash(v.status === REQ_STATUS.approved ? 'Membership removed.' : `Removal request ${v.code} sent for approval.`)
            void load()
          }}
        />
      ) : null}
      {action === 'reactivation-request' ? (
        <ReactivationDrawer
          m={m}
          onClose={() => setAction(null)}
          onDone={(v) => {
            setAction(null)
            flash(v.status === REQ_STATUS.approved ? 'Membership reactivated.' : `Reactivation request ${v.code} sent for approval.`)
            void load()
          }}
        />
      ) : null}
    </div>
  )
}

function Downline({ id }: { id: string }) {
  const [roles, setRoles] = useState<RoleView[]>([])
  const [depth, setDepth] = useState(1)
  const [role, setRole] = useState('')
  const [status, setStatus] = useState(0)
  const [q, setQ] = useState('')
  const [items, setItems] = useState<Membership[]>([])
  const [next, setNext] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    void rolesApi.list().then((r) => {
      if (r.ok) setRoles(r.data.items || [])
    })
  }, [])

  const load = useCallback(
    async (cursor: string, append: boolean) => {
      setLoading(true)
      const r = await rolesApi.downline(id, { depth, role, status, q: q.trim(), cursor })
      setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setItems((prev) => (append ? [...prev, ...(r.data.items || [])] : r.data.items || []))
      setNext(r.data.next_cursor || '')
    },
    [id, depth, role, status, q],
  )

  useEffect(() => {
    const t = setTimeout(() => void load('', false), 200)
    return () => clearTimeout(t)
  }, [load])

  return (
    <Card title="Downline">
      <div className="org-toolbar">
        <Select label="Levels" value={depth} onChange={(e) => setDepth(Number(e.target.value))}>
          <option value={1}>Direct only</option>
          <option value={2}>2 levels</option>
          <option value={3}>3 levels</option>
          <option value={0}>Everyone below</option>
        </Select>
        <Select label="Role" value={role} onChange={(e) => setRole(e.target.value)}>
          <option value="">All roles</option>
          {roles.map((r) => (
            <option key={r.id} value={r.slug}>
              {r.title}
            </option>
          ))}
        </Select>
        <Select label="Status" value={status} onChange={(e) => setStatus(Number(e.target.value))}>
          <option value={0}>Any status</option>
          {Object.entries(memberStatusLabel).map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </Select>
        <div className="org-toolbar-grow">
          <Field label="Search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Username, name or code" />
        </div>
      </div>
      {error ? <div className="err-banner" style={{ marginTop: 12 }}>{error}</div> : null}
      {loading && items.length === 0 ? (
        <PageLoader label="Loading downline…" />
      ) : items.length === 0 ? (
        <p className="muted" style={{ marginTop: 16 }}>
          Nobody {depth === 1 ? 'directly ' : ''}below this member{q || role || status ? ' matches' : ' yet'}.
        </p>
      ) : (
        <div className="table-wrap" style={{ marginTop: 16 }}>
          <MemberTable items={items} showDepth={depth !== 1} />
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
  )
}

// ─── drawers ─────────────────────────────────────────────────────────────────

const transitionCopy = {
  suspend: {
    title: 'Suspend membership',
    body: 'They keep their place in the tree but lose the role’s abilities until reactivated. Their downline is untouched.',
    button: 'Suspend',
    done: 'Membership suspended.',
  },
  reactivate: {
    title: 'Reactivate membership',
    body: 'Restores the role’s abilities immediately.',
    button: 'Reactivate',
    done: 'Membership reactivated.',
  },
  block: {
    title: 'Block member',
    body: 'Blocked members cannot use the role, cannot apply again and cannot be transferred. Only an unblock lifts this.',
    button: 'Block',
    done: 'Member blocked.',
  },
  unblock: {
    title: 'Unblock member',
    body: 'Lifts the bar. The membership stays ended; they may apply again or you can request reactivation.',
    button: 'Unblock',
    done: 'Member unblocked.',
  },
} as const

function TransitionDrawer({
  m,
  kind,
  onClose,
  onDone,
}: {
  m: Membership
  kind: keyof typeof transitionCopy
  onClose: () => void
  onDone: (m: Membership, msg: string) => void
}) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const c = transitionCopy[kind]
  const submit = async () => {
    if (!reason.trim()) {
      setError('Add a short reason — it goes in the audit trail.')
      return
    }
    setBusy(true)
    const r = await rolesApi.transition(m.id, kind, reason.trim())
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    onDone(r.data, c.done)
  }
  return (
    <Drawer title={c.title} onClose={onClose}>
      {error ? <div className="err-banner">{error}</div> : null}
      <p style={{ margin: 0 }}>
        <strong>{personLabel(m)}</strong> · {m.role.title} · {m.code}
      </p>
      <p className="muted" style={{ margin: 0 }}>
        {c.body}
      </p>
      <TextArea label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required />
      <div className="form-actions">
        <Button variant={kind === 'block' || kind === 'suspend' ? 'danger' : 'primary'} loading={busy} onClick={() => void submit()}>
          {c.button}
        </Button>
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
    </Drawer>
  )
}

function TransferDrawer({ m, upline, onClose, onDone }: { m: Membership; upline: Membership[]; onClose: () => void; onDone: (v: RequestView) => void }) {
  const [to, setTo] = useState<MembershipBrief | null>(null)
  const [childrenOnly, setChildrenOnly] = useState(false)
  const [note, setNote] = useState('')
  const [preview, setPreview] = useState<TransferPreview | null>(null)
  const [busy, setBusy] = useState<'preview' | 'submit' | null>(null)
  const [error, setError] = useState('')
  const exclude = useMemo(() => [m.id, ...(m.parent_id && !childrenOnly ? [m.parent_id] : [])], [m, childrenOnly])

  useEffect(() => setPreview(null), [to, childrenOnly])

  const runPreview = async () => {
    if (!to) return
    setBusy('preview')
    setError('')
    const r = await rolesApi.transferPreview(m.id, { new_parent_id: to.id, children_only: childrenOnly })
    setBusy(null)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setPreview(r.data)
  }

  const submit = async () => {
    if (!to) return
    setBusy('submit')
    setError('')
    const r = await rolesApi.transfer(m.id, { new_parent_id: to.id, children_only: childrenOnly, note: note.trim() })
    setBusy(null)
    if (!r.ok) {
      setError(r.message)
      return
    }
    onDone(r.data)
  }

  const blocked = preview ? preview.blockers.length > 0 : true

  return (
    <Drawer title="Transfer (shift upline)" onClose={onClose}>
      {error ? <div className="err-banner">{error}</div> : null}
      <p style={{ margin: 0 }}>
        <strong>{personLabel(m)}</strong> · {m.role.title} · {m.code}
      </p>
      <div>
        <div className="muted" style={{ fontSize: 12, marginBottom: 6 }}>
          Current upline
        </div>
        <Chain upline={upline} />
      </div>
      <label className="perm-check">
        <input type="checkbox" checked={childrenOnly} onChange={(e) => setChildrenOnly(e.target.checked)} />
        <span>
          <strong>Move only their downline</strong>
          <div className="muted">The member stays where they are; everyone directly under them moves to the new upline.</div>
        </span>
      </label>
      <MembershipPicker
        label="New upline"
        value={to}
        onChange={setTo}
        exclude={exclude}
        hint="Must hold a role allowed to have this role under it, and must not be inside the subtree being moved."
      />
      <TextArea label="Note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} placeholder="Why — shown to approvers and kept in the audit trail." />
      {preview ? (
        <div className={preview.blockers.length ? 'err-banner' : 'ok-banner'}>
          {preview.blockers.length ? (
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {preview.blockers.map((b) => (
                <li key={b}>{b}</li>
              ))}
            </ul>
          ) : (
            <>
              Moving <strong>{preview.moving}</strong> membership{preview.moving === 1 ? '' : 's'} ({preview.direct_moving} direct) to{' '}
              <strong>{preview.to ? personLabel(preview.to) : 'the top'}</strong>. Limit per transfer: {preview.max_allowed.toLocaleString()}.
            </>
          )}
        </div>
      ) : null}
      <div className="form-actions">
        {!preview || preview.blockers.length ? (
          <Button variant="secondary" loading={busy === 'preview'} disabled={!to} onClick={() => void runPreview()}>
            Check
          </Button>
        ) : null}
        <Button loading={busy === 'submit'} disabled={!to || blocked} onClick={() => void submit()}>
          Send transfer
        </Button>
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
    </Drawer>
  )
}

function RevokeDrawer({ m, onClose, onDone }: { m: Membership; onClose: () => void; onDone: (v: RequestView) => void }) {
  const [disposition, setDisposition] = useState<number>(DISPOSITION.reparent)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async () => {
    if (!reason.trim()) {
      setError('Add a reason — the person and approvers will see it.')
      return
    }
    setBusy(true)
    const r = await rolesApi.revoke(m.id, { disposition, reason: reason.trim() })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    onDone(r.data)
  }
  return (
    <Drawer title="Remove from role" onClose={onClose}>
      {error ? <div className="err-banner">{error}</div> : null}
      <p style={{ margin: 0 }}>
        <strong>{personLabel(m)}</strong> · {m.role.title} · {m.code}
        {m.direct_children ? ` · ${m.direct_children} direct downline` : ''}
      </p>
      <Select label="What happens to their downline" value={disposition} onChange={(e) => setDisposition(Number(e.target.value))}>
        <option value={DISPOSITION.reparent}>Move them up to this member’s upline</option>
        <option value={DISPOSITION.keep}>Keep them where they are (under a revoked node)</option>
        <option value={DISPOSITION.suspend}>Suspend everyone below as well</option>
      </Select>
      <TextArea label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required />
      <div className="form-actions">
        <Button variant="danger" loading={busy} onClick={() => void submit()}>
          Remove
        </Button>
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
    </Drawer>
  )
}

function ReactivationDrawer({ m, onClose, onDone }: { m: Membership; onClose: () => void; onDone: (v: RequestView) => void }) {
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async () => {
    setBusy(true)
    const r = await rolesApi.reactivationRequest(m.id, note.trim())
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    onDone(r.data)
  }
  return (
    <Drawer title="Request reactivation" onClose={onClose}>
      {error ? <div className="err-banner">{error}</div> : null}
      <p className="muted" style={{ margin: 0 }}>
        Puts {personLabel(m)} back as {m.role.title} in their previous place, once approved.
      </p>
      <TextArea label="Note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
      <div className="form-actions">
        <Button loading={busy} onClick={() => void submit()}>
          Send
        </Button>
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
    </Drawer>
  )
}
