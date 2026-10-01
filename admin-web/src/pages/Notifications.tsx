import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { get, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import type { Catalog, Role } from './Roles'

type Audience = 'all' | 'users' | 'roles'

type Person = {
  id: string
  username: string
  full_name: string
  avatar_url: string
  email?: string
  status?: number
  fail_reason?: string
}

type Job = {
  id: string
  title: string
  body: string
  audience: Audience
  status: number
  total: number
  sent: number
  failed: number
  skipped: number
  fail_reason?: string
  created_at: string
}

function jobStatus(s: number) {
  if (s === 1) return 'Queued'
  if (s === 2) return 'Sending'
  if (s === 3) return 'Done'
  return 'Failed'
}

function audienceLabel(a: string) {
  if (a === 'users') return 'Selected users'
  if (a === 'roles') return 'Selected roles'
  return 'All users'
}

function Face({ url, name }: { url: string; name: string }) {
  const { mediaBase } = useAuth()
  const src = mediaUrl(mediaBase, url)
  if (src) return <img src={src} alt="" className="accounts-suggest-av" loading="lazy" />
  return (
    <span className="accounts-suggest-av accounts-suggest-av--letter">{(name || '?').slice(0, 1).toUpperCase()}</span>
  )
}

type PushType = { category: string; label: string; toggle: boolean; channel: string; high_priority: boolean }
type PushStat = { category: string; state: number; reason: string; count: number }

const reasonText: Record<string, string> = {
  no_token: 'No registered device',
  muted: 'Turned off by user',
  dead_token: 'Device token expired',
  expired: 'Too old to deliver',
  provider_not_configured: 'Firebase not configured',
  provider_config: 'Firebase credentials rejected',
  provider_unavailable: 'FCM unavailable',
}

/** Last 24 h of automatic + campaign deliveries, by notification type. */
function DeliveryStats() {
  const [types, setTypes] = useState<PushType[]>([])
  const [stats, setStats] = useState<PushStat[]>([])
  const [failed, setFailed] = useState('')

  useEffect(() => {
    let live = true
    const load = async () => {
      const [t, s] = await Promise.all([
        get<{ items: PushType[] }>('/v1/admin/push/types'),
        get<{ items: PushStat[] }>('/v1/admin/push/stats'),
      ])
      if (!live) return
      if (!t.ok || !s.ok) {
        setFailed((t.ok ? s : t).message)
        return
      }
      setFailed('')
      setTypes(t.data.items || [])
      setStats(s.data.items || [])
    }
    void load()
    const id = setInterval(() => void load(), 60_000)
    return () => {
      live = false
      clearInterval(id)
    }
  }, [])

  const sum = (cat: string, state: number) =>
    stats.filter((r) => r.category === cat && r.state === state).reduce((n, r) => n + r.count, 0)
  const topReason = (cat: string) => {
    const r = stats.filter((x) => x.category === cat && (x.state === 2 || x.state === 3) && x.reason)
    if (r.length === 0) return ''
    const best = r.reduce((a, b) => (b.count > a.count ? b : a))
    return reasonText[best.reason] || best.reason
  }

  if (failed) return <div className="err-banner">Delivery stats: {failed}</div>
  if (types.length === 0) return null

  return (
    <section className="settings-card settings-card-wide" style={{ marginBottom: 16 }}>
      <header>
        <h2>Delivery · last 24 h</h2>
        <p>Automatic notifications (campaigns are tracked per job below). Users can turn off the types marked “user setting”.</p>
      </header>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, marginTop: 8 }}>
        <thead>
          <tr style={{ textAlign: 'left', color: 'var(--muted, #888)' }}>
            <th style={{ padding: '6px 4px' }}>Type</th>
            <th>Sent</th>
            <th>Skipped</th>
            <th>Failed</th>
            <th>Queued</th>
            <th>Main reason</th>
          </tr>
        </thead>
        <tbody>
          {types.map((t) => (
            <tr key={t.category} style={{ borderTop: '1px solid var(--line, #eee)' }}>
              <td style={{ padding: '6px 4px' }}>
                {t.label}
                <span className="muted" style={{ fontSize: 11, marginLeft: 6 }}>
                  {t.toggle ? 'user setting' : 'always on'}
                </span>
              </td>
              <td>{sum(t.category, 1)}</td>
              <td>{sum(t.category, 2)}</td>
              <td>{sum(t.category, 3)}</td>
              <td>{sum(t.category, 0)}</td>
              <td className="muted">{topReason(t.category)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}

export function NotificationsPage() {
  const { id } = useParams()
  if (id) return <NotificationJobPage id={id} />
  return <NotificationsHome />
}

function NotificationsHome() {
  const navigate = useNavigate()
  const [jobs, setJobs] = useState<Job[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [audience, setAudience] = useState<Audience>('all')
  const [picked, setPicked] = useState<Person[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [roleIds, setRoleIds] = useState<Set<number>>(new Set())
  const [q, setQ] = useState('')
  const [hits, setHits] = useState<Person[]>([])
  const [open, setOpen] = useState(false)
  const wrapRef = useRef<HTMLDivElement>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const loadJobs = useCallback(async () => {
    const r = await get<{ items: Job[] }>('/v1/admin/push/jobs?limit=30')
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setJobs(r.data.items || [])
  }, [])

  useEffect(() => {
    void loadJobs()
    void get<Catalog>('/v1/rbac/catalog').then((r) => {
      if (r.ok) setRoles(r.data.roles || [])
    })
  }, [loadJobs])

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      const t = q.trim()
      if (!t) {
        setHits([])
        return
      }
      void get<{ items: Person[] }>(`/v1/admin/users/search?q=${encodeURIComponent(t)}&limit=8`).then((r) => {
        if (r.ok) {
          setHits(r.data.items || [])
          setOpen(true)
        }
      })
    }, 220)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [q])

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  const payload = () => ({
    title: title.trim(),
    body: body.trim(),
    audience,
    user_ids: audience === 'users' ? picked.map((p) => p.id) : [],
    role_ids: audience === 'roles' ? [...roleIds] : [],
  })

  const send = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const r = await post<Job>('/v1/admin/push/jobs', payload())
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    navigate(`/notifications/${r.data.id}`)
  }

  const addPerson = (p: Person) => {
    setPicked((prev) => (prev.some((x) => x.id === p.id) ? prev : [...prev, p]))
    setQ('')
    setHits([])
    setOpen(false)
  }

  if (loading) return <PageLoader label="Loading notifications…" />

  return (
    <div className="notify-page">
      <div className="page-head">
        <h1>Notifications</h1>
        <p>Push a title and message. Sending stays in the background.</p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <DeliveryStats />

      <form className="notify-compose" onSubmit={(e) => void send(e)}>
          <Field label="Title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={80} required />
          <TextArea
            label="Message"
            value={body}
            onChange={(e) => setBody(e.target.value)}
            maxLength={240}
            required
            style={{ minHeight: 72 }}
          />
          <fieldset className="notify-audience">
            <legend>Send to</legend>
            <label>
              <input type="radio" name="aud" checked={audience === 'all'} onChange={() => setAudience('all')} /> All users
            </label>
            <label>
              <input type="radio" name="aud" checked={audience === 'users'} onChange={() => setAudience('users')} /> Selected
              users
            </label>
            <label>
              <input type="radio" name="aud" checked={audience === 'roles'} onChange={() => setAudience('roles')} /> Selected
              roles
            </label>
          </fieldset>

          {audience === 'users' ? (
            <div>
              <div className="accounts-search" ref={wrapRef}>
                <label className="field-label" style={{ width: '100%' }}>
                  Add people
                  <div className="accounts-search-row">
                    <input
                      className="accounts-search-input"
                      value={q}
                      onChange={(e) => {
                        setQ(e.target.value)
                        setOpen(true)
                      }}
                      placeholder="Username, email, phone, or name…"
                      autoComplete="off"
                    />
                  </div>
                </label>
                {open && hits.length > 0 ? (
                  <ul className="accounts-suggest" role="listbox">
                    {hits.map((s) => (
                      <li key={s.id}>
                        <button type="button" className="accounts-suggest-item" onClick={() => addPerson(s)}>
                          <Face url={s.avatar_url} name={s.full_name || s.username} />
                          <span className="accounts-suggest-text">
                            <strong>@{s.username}</strong>
                            <span className="muted">{s.full_name || '—'}</span>
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                ) : null}
              </div>
              <ul className="notify-chips">
                {picked.map((p) => (
                  <li key={p.id}>
                    @{p.username}
                    <button type="button" onClick={() => setPicked((prev) => prev.filter((x) => x.id !== p.id))}>
                      ×
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}

          {audience === 'roles' ? (
            <div className="notify-roles">
              {roles.map((role) => (
                <label key={role.id}>
                  <input
                    type="checkbox"
                    checked={roleIds.has(role.id)}
                    onChange={() => {
                      setRoleIds((prev) => {
                        const next = new Set(prev)
                        if (next.has(role.id)) next.delete(role.id)
                        else next.add(role.id)
                        return next
                      })
                    }}
                  />
                  {role.name}
                </label>
              ))}
            </div>
          ) : null}

          <div className="form-actions">
            <Button type="submit" size="sm" loading={busy}>
              Send
            </Button>
          </div>
        </form>

      <h2 className="notify-h2">Recent</h2>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Title</th>
              <th>To</th>
              <th>Status</th>
              <th className="num">Sent</th>
            </tr>
          </thead>
          <tbody>
            {jobs.length === 0 ? (
              <tr>
                <td colSpan={4} className="muted">
                  None yet.
                </td>
              </tr>
            ) : (
              jobs.map((j) => (
                <tr key={j.id} className="is-click" onClick={() => navigate(`/notifications/${j.id}`)}>
                  <td>{j.title}</td>
                  <td>{audienceLabel(j.audience)}</td>
                  <td>
                    {jobStatus(j.status)}
                    {j.fail_reason ? <div className="notify-fail-why">{j.fail_reason}</div> : null}
                  </td>
                  <td className="num">
                    {j.sent.toLocaleString()} / {j.total.toLocaleString()}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function NotificationJobPage({ id }: { id: string }) {
  const navigate = useNavigate()
  const [job, setJob] = useState<Job | null>(null)
  const [pending, setPending] = useState<Person[]>([])
  const [sent, setSent] = useState<Person[]>([])
  const [failed, setFailed] = useState<Person[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [moreP, setMoreP] = useState('')
  const [moreS, setMoreS] = useState('')
  const [moreF, setMoreF] = useState('')
  const [tab, setTab] = useState<'pending' | 'sent' | 'failed'>('pending')

  const loadJob = useCallback(async () => {
    const r = await get<Job>(`/v1/admin/push/jobs/${id}`)
    if (!r.ok) {
      setError(r.message)
      setLoading(false)
      return
    }
    setJob(r.data)
    setLoading(false)
  }, [id])

  const loadList = useCallback(
    async (status: 'pending' | 'sent' | 'failed', after: string, append: boolean) => {
      const params = new URLSearchParams({ status, limit: '40' })
      if (after) params.set('after_id', after)
      const r = await get<{ items: Person[]; next_cursor?: string }>(
        `/v1/admin/push/jobs/${id}/recipients?${params}`,
      )
      if (!r.ok) return
      const items = r.data.items || []
      const next = items.length >= 40 ? r.data.next_cursor || '' : ''
      if (status === 'pending') {
        setPending((prev) => (append ? [...prev, ...items] : items))
        setMoreP(next)
      } else if (status === 'sent') {
        setSent((prev) => (append ? [...prev, ...items] : items))
        setMoreS(next)
      } else {
        setFailed((prev) => (append ? [...prev, ...items] : items))
        setMoreF(next)
      }
    },
    [id],
  )

  useEffect(() => {
    void loadJob()
    void loadList('pending', '', false)
    void loadList('sent', '', false)
    void loadList('failed', '', false)
  }, [loadJob, loadList])

  useEffect(() => {
    if (job && job.failed > 0 && job.sent === 0 && job.status !== 1 && job.status !== 2) {
      setTab('failed')
    }
  }, [job])

  useEffect(() => {
    if (!job || (job.status !== 1 && job.status !== 2)) return
    const t = setInterval(() => {
      void loadJob()
      void loadList('pending', '', false)
      void loadList('sent', '', false)
      void loadList('failed', '', false)
    }, 2000)
    return () => clearInterval(t)
  }, [job, loadJob, loadList])

  if (loading) return <PageLoader label="Loading…" />
  if (!job) {
    return (
      <div className="notify-page">
        <div className="err-banner">{error || 'Not found.'}</div>
        <Button size="sm" variant="ghost" onClick={() => navigate('/notifications')}>
          Back
        </Button>
      </div>
    )
  }

  const rows = tab === 'pending' ? pending : tab === 'sent' ? sent : failed
  const more = tab === 'pending' ? moreP : tab === 'sent' ? moreS : moreF

  return (
    <div className="notify-page">
      <div className="page-head">
        <button type="button" className="muted" style={{ fontSize: 13, fontWeight: 600 }} onClick={() => navigate('/notifications')}>
          ← Notifications
        </button>
        <h1>{job.title}</h1>
        <p>
          {job.body} · {audienceLabel(job.audience)} · {jobStatus(job.status)}
        </p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {job.fail_reason ? (
        <div className="err-banner">
          {job.failed === job.total && job.sent === 0
            ? job.fail_reason
            : `${job.failed.toLocaleString()} failed — ${job.fail_reason}`}
        </div>
      ) : job.failed > 0 ? (
        <div className="err-banner">
          {job.failed.toLocaleString()} {job.failed === 1 ? 'person' : 'people'} failed. Open Failed for each reason.
        </div>
      ) : null}

      <div className="notify-progress">
        <span>
          Sent {job.sent.toLocaleString()} · pending {(job.total - job.sent - job.failed - job.skipped).toLocaleString()} ·
          failed {job.failed.toLocaleString()} · total {job.total.toLocaleString()}
        </span>
      </div>

      <div className="notify-tabs">
        <button type="button" className={tab === 'pending' ? 'is-on' : ''} onClick={() => setTab('pending')}>
          Pending
        </button>
        <button type="button" className={tab === 'sent' ? 'is-on' : ''} onClick={() => setTab('sent')}>
          Sent
        </button>
        <button type="button" className={tab === 'failed' ? 'is-on' : ''} onClick={() => setTab('failed')}>
          Failed
        </button>
      </div>

      <ul className="notify-people">
        {rows.length === 0 ? (
          <li className="muted">
            {tab === 'pending' ? 'Nobody waiting.' : tab === 'sent' ? 'Nobody sent yet.' : 'Nobody failed.'}
          </li>
        ) : (
          rows.map((p) => (
            <li key={p.id}>
              <Face url={p.avatar_url} name={p.full_name || p.username} />
              <span>
                <strong>@{p.username}</strong>
                {p.full_name ? ` · ${p.full_name}` : ''}
                {tab === 'failed' && p.fail_reason ? <em className="notify-fail-why">{p.fail_reason}</em> : null}
              </span>
            </li>
          ))
        )}
      </ul>
      {more ? (
        <Button size="sm" variant="ghost" onClick={() => void loadList(tab, more, true)}>
          More
        </Button>
      ) : null}
    </div>
  )
}
