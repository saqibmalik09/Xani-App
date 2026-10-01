import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import {
  MEMBER_STATUS,
  REQ_STATUS,
  kindLabel,
  memberStatusLabel,
  personLabel,
  rolesApi,
  when,
  type CodeInfo,
  type Membership,
  type ReferralCodeView,
  type RequestView,
  type SummaryRow,
} from '../lib/roles'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { Chain, Select, StatusPill } from '../ui/Org'

/** The signed-in person's own place in the organisation: roles, codes, upline, downline, requests. */
export function NetworkPage() {
  const { session, can, active, setActiveMembership } = useAuth()
  const [memberships, setMemberships] = useState<Membership[] | null>(null)
  const [codes, setCodes] = useState<ReferralCodeView[]>([])
  const [requests, setRequests] = useState<RequestView[]>([])
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [selectedId, setSelectedId] = useState('')

  const load = useCallback(async () => {
    const [m, c, r] = await Promise.all([rolesApi.myMemberships(), rolesApi.myCodes(), rolesApi.myRequests()])
    if (!m.ok) {
      setError(m.message)
      setMemberships([])
      return
    }
    setMemberships(m.data.items || [])
    if (c.ok) setCodes(c.data.items || [])
    if (r.ok) setRequests(r.data.items || [])
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!memberships?.length) return
    const preferred = active?.membership_id && memberships.some((m) => m.id === active.membership_id) ? active.membership_id : memberships[0].id
    setSelectedId((cur) => (cur && memberships.some((m) => m.id === cur) ? cur : preferred))
  }, [memberships, active])

  const selected = useMemo(() => memberships?.find((m) => m.id === selectedId) || null, [memberships, selectedId])

  const flash = (msg: string) => {
    setOk(msg)
    setError('')
    window.setTimeout(() => setOk(''), 5000)
  }

  if (!memberships) return <PageLoader label="Loading your network…" />

  const activeOnes = memberships.filter((m) => m.status === MEMBER_STATUS.active)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div className="page-head">
        <h1>My network</h1>
        <p>
          Your roles, the people above and below you, your referral codes and the requests you have open.
          {session ? ` Signed in as @${session.user.username}.` : ''}
        </p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <div className="org-two">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Card title="My roles">
            {memberships.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                You do not hold a role yet. Redeem a referral code below, or apply for a role that allows it.
              </p>
            ) : (
              <div className="role-list">
                {memberships.map((m) => (
                  <button key={m.id} type="button" className={`role-row${m.id === selectedId ? ' is-selected' : ''}`} onClick={() => setSelectedId(m.id)}>
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <div className="role-row-title" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                        {m.role.title} <StatusPill kind="s" status={m.status} />
                      </div>
                      <div className="role-row-meta">
                        {m.referral_code ? (
                          <>
                            code <span className="org-code">{m.referral_code}</span> ·{' '}
                          </>
                        ) : null}
                        {m.direct_children} direct · since {when(m.approved_at || m.created_at)}
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            )}
            {activeOnes.length > 1 && selected && selected.status === MEMBER_STATUS.active && active?.membership_id !== selected.id ? (
              <div className="form-actions" style={{ marginTop: 12 }}>
                <Button size="sm" variant="secondary" onClick={() => setActiveMembership(selected.id)}>
                  Act as {selected.role.title} in this console
                </Button>
              </div>
            ) : null}
          </Card>

          <ReferralCodes codes={codes} />

          <JoinWithCode
            onDone={(v) => {
              flash(v.status === REQ_STATUS.approved ? 'You are in.' : `Application ${v.code} sent to your upline for approval.`)
              void load()
            }}
          />
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {selected ? <MembershipPanel key={selected.id} m={selected} canSeeUpline={can('hierarchy.view_upline') || can('memberships.view')} /> : null}

          <Card title="My requests">
            {requests.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                No requests yet.
              </p>
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Request</th>
                      <th>Kind</th>
                      <th>Role</th>
                      <th>Status</th>
                      <th>When</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {requests.map((r) => (
                      <MyRequestRow
                        key={r.id}
                        r={r}
                        onChanged={(v) => {
                          setRequests((prev) => prev.map((x) => (x.id === v.id ? v : x)))
                          flash(`Request ${v.code} cancelled.`)
                        }}
                        onError={setError}
                      />
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}

function MyRequestRow({ r, onChanged, onError }: { r: RequestView; onChanged: (v: RequestView) => void; onError: (m: string) => void }) {
  const [busy, setBusy] = useState(false)
  const cancel = async () => {
    const reason = window.prompt('Why are you cancelling?', '')
    if (reason === null) return
    setBusy(true)
    const x = await rolesApi.cancel(r.id, reason)
    setBusy(false)
    if (!x.ok) {
      onError(x.message)
      return
    }
    onChanged(x.data)
  }
  return (
    <tr>
      <td>
        <span className="org-code">{r.code}</span>
      </td>
      <td>{kindLabel[r.kind]}</td>
      <td>
        {r.role.title}
        {r.parent ? <div className="muted">under {personLabel(r.parent)}</div> : null}
      </td>
      <td>
        <StatusPill kind="q" status={r.status} />
        {r.reason && r.status !== REQ_STATUS.pending ? <div className="muted">{r.reason}</div> : null}
      </td>
      <td>{when(r.created_at)}</td>
      <td>
        {r.status === REQ_STATUS.pending && r.can_cancel ? (
          <Button size="sm" variant="ghost" loading={busy} onClick={() => void cancel()}>
            Cancel
          </Button>
        ) : null}
      </td>
    </tr>
  )
}

function ReferralCodes({ codes }: { codes: ReferralCodeView[] }) {
  const [copied, setCopied] = useState('')
  const copy = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(code)
      window.setTimeout(() => setCopied(''), 1500)
    } catch {
      window.prompt('Copy your code:', code)
    }
  }
  return (
    <Card title="My referral codes">
      {codes.length === 0 ? (
        <p className="muted" style={{ margin: 0 }}>
          None of your roles recruits people. When one does, a code appears here to share.
        </p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {codes.map((c) => (
            <div key={c.code} className="role-row" style={{ cursor: 'default', alignItems: 'flex-start' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="role-row-title" style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', letterSpacing: 1 }}>{c.code}</div>
                <div className="role-row-meta">
                  As {c.role.title} · people who redeem it apply for{' '}
                  {c.target_roles.length ? c.target_roles.map((t) => t.title).join(', ') : 'nothing yet (no child roles set)'} and land under you after
                  your approval.
                </div>
              </div>
              <Button size="sm" variant="secondary" onClick={() => void copy(c.code)}>
                {copied === c.code ? 'Copied' : 'Copy'}
              </Button>
            </div>
          ))}
        </div>
      )}
    </Card>
  )
}

function JoinWithCode({ onDone }: { onDone: (v: RequestView) => void }) {
  const [code, setCode] = useState('')
  const [info, setInfo] = useState<CodeInfo | null>(null)
  const [role, setRole] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState<'lookup' | 'apply' | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    setInfo(null)
    setRole('')
    setError('')
    const t = code.trim()
    if (t.length < 4) return
    const timer = setTimeout(async () => {
      setBusy('lookup')
      const r = await rolesApi.lookupCode(t)
      setBusy(null)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setInfo(r.data)
      if (r.data.target_roles.length === 1) setRole(r.data.target_roles[0].slug)
    }, 350)
    return () => clearTimeout(timer)
  }, [code])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!info) return
    setBusy('apply')
    setError('')
    const r = info.target_roles.length > 1 ? await rolesApi.apply({ referral_code: code.trim(), role, note: note.trim() }) : await rolesApi.redeem(code.trim(), note.trim())
    setBusy(null)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setCode('')
    setNote('')
    onDone(r.data)
  }

  return (
    <Card title="Join with a referral code">
      <form className="form-grid" onSubmit={(e) => void submit(e)}>
        <Field label="Referral code" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="e.g. 7K3M9P" autoComplete="off" hint="The person who gave you the code becomes your upline and approves your application." />
        {error ? <div className="err-banner">{error}</div> : null}
        {info ? (
          <div className="ok-banner">
            Code belongs to <strong>{personLabel(info.owner)}</strong> ({info.membership.role.title}). You would apply for{' '}
            {info.target_roles.length === 1 ? <strong>{info.target_roles[0].title}</strong> : 'one of the roles below'}.
          </div>
        ) : null}
        {info && info.target_roles.length > 1 ? (
          <Select label="Role to apply for" value={role} onChange={(e) => setRole(e.target.value)} required>
            <option value="">Choose…</option>
            {info.target_roles.map((t) => (
              <option key={t.id} value={t.slug}>
                {t.title}
              </option>
            ))}
          </Select>
        ) : null}
        <TextArea label="Note to your upline" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
        <div className="form-actions">
          <Button type="submit" loading={busy === 'apply'} disabled={!info || (info.target_roles.length > 1 && !role)}>
            Apply
          </Button>
        </div>
      </form>
    </Card>
  )
}

function MembershipPanel({ m, canSeeUpline }: { m: Membership; canSeeUpline: boolean }) {
  const [upline, setUpline] = useState<Membership[] | null>(null)
  const [summary, setSummary] = useState<SummaryRow[]>([])
  const [items, setItems] = useState<Membership[]>([])
  const [next, setNext] = useState('')
  const [depth, setDepth] = useState(1)
  const [status, setStatus] = useState(0)
  const [q, setQ] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!canSeeUpline) {
      setUpline([])
      return
    }
    void rolesApi.upline(m.id).then((r) => setUpline(r.ok ? r.data.items || [] : []))
    void rolesApi.summary(m.id).then((r) => {
      if (r.ok) setSummary(r.data.items || [])
    })
  }, [m.id, canSeeUpline])

  const load = useCallback(
    async (cursor: string, append: boolean) => {
      setLoading(true)
      const r = await rolesApi.downline(m.id, { depth, status, q: q.trim(), cursor })
      setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setItems((prev) => (append ? [...prev, ...(r.data.items || [])] : r.data.items || []))
      setNext(r.data.next_cursor || '')
    },
    [m.id, depth, status, q],
  )

  useEffect(() => {
    const t = setTimeout(() => void load('', false), 200)
    return () => clearTimeout(t)
  }, [load])

  const total = summary.reduce((n, r) => n + r.count, 0)

  return (
    <>
      <Card title={m.referral_code ? `${m.role.title} · code ${m.referral_code}` : m.role.title}>
        <div className="org-stats" style={{ marginBottom: 16 }}>
          <div className="org-stat">
            <span>Direct downline</span>
            <strong>{m.direct_children}</strong>
          </div>
          <div className="org-stat">
            <span>Whole downline</span>
            <strong>{total || m.direct_children}</strong>
          </div>
          <div className="org-stat">
            <span>Status</span>
            <strong style={{ fontSize: 14 }}>{memberStatusLabel[m.status]}</strong>
          </div>
          <div className="org-stat">
            <span>Levels above</span>
            <strong>{upline ? upline.length : '…'}</strong>
          </div>
        </div>
        <div className="muted" style={{ fontSize: 12, marginBottom: 6 }}>
          Upline
        </div>
        {upline === null ? <PageLoader /> : canSeeUpline ? <Chain upline={upline} self={m} /> : <span className="muted">Your role does not show the upline.</span>}
        {summary.length ? (
          <div className="chip-row">
            {summary.map((row) => (
              <span key={`${row.role.id}-${row.status}`} className="chip">
                {row.role.title} · {memberStatusLabel[row.status]?.toLowerCase()} · {row.count}
              </span>
            ))}
          </div>
        ) : null}
      </Card>

      <Card title="My downline">
        <div className="org-toolbar">
          <Select label="Levels" value={depth} onChange={(e) => setDepth(Number(e.target.value))}>
            <option value={1}>Direct only</option>
            <option value={2}>2 levels</option>
            <option value={0}>Everyone below</option>
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
            Nobody {depth === 1 ? 'directly ' : ''}under you{q || status ? ' matches' : ' yet — share your referral code'}.
          </p>
        ) : (
          <div className="table-wrap" style={{ marginTop: 16 }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Member</th>
                  <th>Role</th>
                  {depth !== 1 ? <th>Level</th> : null}
                  <th>Status</th>
                  <th>Direct downline</th>
                  <th>Since</th>
                </tr>
              </thead>
              <tbody>
                {items.map((d) => (
                  <tr key={d.id}>
                    <td>
                      <Link to={`/members/${d.id}`}>{personLabel(d)}</Link>
                      <div className="muted">@{d.username}</div>
                    </td>
                    <td>{d.role.title}</td>
                    {depth !== 1 ? <td>{d.tree_depth ?? d.depth}</td> : null}
                    <td>
                      <StatusPill kind="s" status={d.status} />
                    </td>
                    <td>{d.direct_children}</td>
                    <td>{when(d.approved_at || d.created_at)}</td>
                  </tr>
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
    </>
  )
}
