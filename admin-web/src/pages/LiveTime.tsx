import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { get, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

/**
 * Verified live time. Every number on these pages is the ledger's: closed
 * segments plus the one open segment bounded by the last server-received
 * heartbeat. Wall time (room start → end) is shown only as "wall" for
 * comparison and is never what a host is credited.
 *
 * Live rows tick locally between refreshes from `server_now` + `verified_ms`
 * while `counting` is true; the browser clock is only used for the delta
 * since the last response, never as a source of the figure.
 */

type Calendar = {
  tz: string
  day_start: string
  week_start: string
  today: string
  week_from: string
  month: string
  describe: string
  today_start: string
  today_end: string
  server_now: string
}

type UserNow = {
  today_ms: number
  week_ms: number
  month_ms: number
  lifetime_ms: number
  historical_ms: number
  live: boolean
  room_id?: string
  running_ms: number
  counting: boolean
  paused_ms: number
  pending_ms: number
}

type Hit = {
  id: string
  username: string
  full_name: string
  avatar_url?: string
  email?: string
  phone?: string
  state: number
  banned: boolean
  time?: UserNow
}

type DayRow = {
  day: string
  total_ms: number
  live_ms?: number
  host_video_ms: number
  host_audio_ms: number
  cohost_video_ms: number
  cohost_audio_ms: number
  historical_ms: number
}

type Session = {
  room_id: string
  user_id: string
  role: 'host' | 'cohost'
  kind?: string
  title?: string
  started_at?: string
  ended_at?: string
  verified_ms: number
  audio_ms?: number
  video_ms?: number
  paused_ms: number
  segments: number
  beats?: number
  max_gap_ms?: number
  end_reason: string
  historical: boolean
  live: boolean
  counting?: boolean
  room_end?: string
  room_ended_at?: string
}

type UserResp = {
  user: Hit
  now: UserNow
  calendar: Calendar
  from: string
  to: string
  days: DayRow[]
  sessions: Session[]
}

type Segment = {
  id: string
  user_id: string
  role: 'host' | 'cohost'
  mode: 'audio' | 'video'
  segment_no: number
  started_at: string
  ended_at: string
  duration_ms: number
  end_reason: string
  first_beat_at?: string
  last_seen_at: string
  beats: number
  max_gap_ms: number
  gap_before_ms: number
  gap_cause: '' | 'client' | 'platform'
  allowance_ms: number
  calc_version: number
  instance_id: string
  historical: boolean
  aggregated: boolean
  proof_quality: 'strong' | 'thin' | 'none' | 'historical'
}

type OpenRow = {
  user_id: string
  room_id: string
  segment_id: string
  role: 'host' | 'cohost'
  mode: 'audio' | 'video'
  segment_no: number
  started_at: string
  last_seen_at: string
  silent_ms: number
  allowance_ms: number
  verified_ms: number
  paused_ms: number
  counting: boolean
  instance_id: string
  user?: Hit
}

type AuditRow = {
  id: string
  at: string
  event: string
  user_id?: string
  segment_id?: string
  request_id?: string
  instance_id: string
  detail: Record<string, unknown>
}

type RoomResp = {
  room: {
    id: string
    host_id: string
    kind: string
    title: string
    started_at: string
    ended_at?: string
    end_reason: string
    client_id: string
    live: boolean
    wall_ms: number
  }
  people: Record<string, Hit & { verified_ms: number }>
  segments: Segment[]
  open: OpenRow[]
  audit: AuditRow[]
  host_verified_ms: number
  server_now: string
}

type PlatformDay = {
  day: string
  hosts: number
  total_ms: number
  host_video_ms: number
  host_audio_ms: number
  cohost_video_ms: number
  cohost_audio_ms: number
  historical_ms: number
}

type TopHost = DayRow & { user_id: string; user?: Hit }

type DaysResp = { from: string; to: string; days: PlatformDay[]; top: TopHost[]; calendar: Calendar }

type LiveResp = { items: OpenRow[]; count: number; server_now: string }

type Finding = {
  id: string
  kind: string
  severity: number
  user_id?: string
  room_id?: string
  day?: string
  detail: Record<string, unknown>
  found_at: string
  resolved: boolean
  resolved_at?: string
}

type Health = {
  aggregator: {
    halted: boolean
    meta_tz: string
    meta_day_start: string
    meta_week_start: string
    settings_tz: string
    settings_day_start: string
    settings_week_start: string
    activated_at: string
    rebuild?: Record<string, unknown>
    backlog: number
    oldest_open?: string
  }
  open_segments: number
  checkpoint_interval_ms: number
  open_findings: number
  instance_id: string
  counters: Record<string, number>
  server_now: string
}

const REFRESH_MS = 5000

// ─── formatting ──────────────────────────────────────────────────────────────

export function fmtMs(ms: number, opts: { seconds?: boolean } = {}): string {
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  if (opts.seconds !== false) {
    if (h > 0) return `${h}h ${String(m).padStart(2, '0')}m ${String(s).padStart(2, '0')}s`
    if (m > 0) return `${m}m ${String(s).padStart(2, '0')}s`
    return `${s}s`
  }
  if (h > 0) return `${h}h ${String(m).padStart(2, '0')}m`
  return `${m}m`
}

export function fmtClock(iso?: string): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleString(undefined, { hour12: false })
}

function fmtTime(iso?: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString(undefined, { hour12: false })
}

function fmtAgo(iso: string, now: number): string {
  const d = Math.max(0, now - new Date(iso).getTime())
  if (d < 60_000) return `${Math.floor(d / 1000)}s ago`
  if (d < 3_600_000) return `${Math.floor(d / 60_000)}m ago`
  return `${Math.floor(d / 3_600_000)}h ago`
}

export function displayName(h: { full_name?: string; username?: string }): string {
  return h.full_name || (h.username ? `@${h.username}` : '')
}

/** Server-anchored clock: offset between the server's `server_now` and ours. */
function useServerClock(serverNow?: string) {
  const [tick, setTick] = useState(() => Date.now())
  const offset = useMemo(() => (serverNow ? new Date(serverNow).getTime() - Date.now() : 0), [serverNow])
  useEffect(() => {
    const id = window.setInterval(() => setTick(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [])
  return tick + offset
}

/** A live figure: verified_ms as of `asOf` (server time) plus the seconds since, while counting. */
function liveMs(verified: number, counting: boolean, asOf: string, serverNow: number): number {
  if (!counting) return verified
  return verified + Math.max(0, serverNow - new Date(asOf).getTime())
}

// ─── shared bits ─────────────────────────────────────────────────────────────

export function Avatar({ url, name, size = 28 }: { url?: string; name: string; size?: number }) {
  const { mediaBase } = useAuth()
  const src = url ? mediaUrl(mediaBase, url) : ''
  return src ? (
    <img src={src} alt="" style={{ width: size, height: size, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} loading="lazy" />
  ) : (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        background: 'var(--line)',
        display: 'inline-grid',
        placeItems: 'center',
        fontSize: size * 0.4,
        fontWeight: 700,
        color: 'var(--ink-soft)',
        flexShrink: 0,
      }}
    >
      {(name || '?').slice(0, 1).toUpperCase()}
    </span>
  )
}

export function Stat({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: 'live' | 'warn' }) {
  return (
    <div
      style={{
        background: 'var(--surface)',
        border: `1px solid ${tone === 'warn' ? 'var(--danger)' : 'var(--line)'}`,
        borderRadius: 'var(--radius)',
        padding: '12px 14px',
        boxShadow: 'var(--shadow)',
        minWidth: 0,
        overflow: 'hidden',
      }}
    >
      <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: 0.04 }}>{label}</div>
      <div
        style={{
          marginTop: 6,
          fontSize: value.length > 14 ? 14 : 20,
          fontWeight: 800,
          color: tone === 'live' ? 'var(--accent-hover)' : 'var(--ink)',
          fontVariantNumeric: 'tabular-nums',
          overflowWrap: 'anywhere',
          wordBreak: 'break-word',
          lineHeight: 1.25,
        }}
        title={value}
      >
        {value}
      </div>
      {sub ? (
        <div className="muted" style={{ fontSize: 11, marginTop: 2, overflowWrap: 'anywhere' }}>
          {sub}
        </div>
      ) : null}
    </div>
  )
}

function CountingPill({ live, counting }: { live: boolean; counting?: boolean }) {
  if (!live) return null
  return counting ? (
    <span className="status-pill on" style={{ fontSize: 10 }}>
      Counting
    </span>
  ) : (
    <span className="status-pill off" style={{ fontSize: 10 }}>
      Not counting
    </span>
  )
}

function ProofPill({ q }: { q: Segment['proof_quality'] }) {
  const style: React.CSSProperties =
    q === 'strong'
      ? { background: 'var(--accent-soft)', color: 'var(--accent-hover)' }
      : q === 'thin'
        ? { background: '#fef3c7', color: '#92400e' }
        : q === 'none'
          ? { background: '#fee2e2', color: '#991b1b' }
          : { background: 'var(--wash-deep)', color: 'var(--ink-soft)' }
  return (
    <span className="status-pill" style={{ ...style, fontSize: 10 }}>
      {q === 'historical' ? 'Historical' : q === 'strong' ? 'Strong proof' : q === 'thin' ? 'Thin proof' : 'No heartbeat'}
    </span>
  )
}

function CalendarLine({ cal }: { cal?: Calendar }) {
  if (!cal) return null
  return (
    <span className="muted" style={{ fontSize: 12 }}>
      Day: {cal.tz} · starts {cal.day_start} · week from {cal.week_start} · today {cal.today}
    </span>
  )
}

/** Date range picker driven by ?from=&to= in the URL. */
export function RangeBar({ from, to, today }: { from: string; to: string; today?: string }) {
  const [params, setParams] = useSearchParams()
  const [f, setF] = useState(from)
  const [t, setT] = useState(to)
  useEffect(() => {
    setF(from)
    setT(to)
  }, [from, to])
  const apply = (e: FormEvent) => {
    e.preventDefault()
    const next = new URLSearchParams(params)
    next.set('from', f)
    next.set('to', t)
    setParams(next)
  }
  const preset = (days: number) => {
    const end = today || to
    const d = new Date(end + 'T00:00:00Z')
    d.setUTCDate(d.getUTCDate() - (days - 1))
    const next = new URLSearchParams(params)
    next.set('from', d.toISOString().slice(0, 10))
    next.set('to', end)
    setParams(next)
  }
  return (
    <form onSubmit={apply} style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap' }}>
      <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span className="muted" style={{ fontSize: 11 }}>
          From
        </span>
        <input type="date" value={f} onChange={(e) => setF(e.target.value)} className="admin-context-select" />
      </label>
      <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span className="muted" style={{ fontSize: 11 }}>
          To
        </span>
        <input type="date" value={t} onChange={(e) => setT(e.target.value)} className="admin-context-select" />
      </label>
      <Button type="submit" size="sm" variant="secondary">
        Apply
      </Button>
      <Button type="button" size="sm" variant="ghost" onClick={() => preset(1)}>
        Today
      </Button>
      <Button type="button" size="sm" variant="ghost" onClick={() => preset(7)}>
        7 days
      </Button>
      <Button type="button" size="sm" variant="ghost" onClick={() => preset(30)}>
        30 days
      </Button>
    </form>
  )
}

// ─── pages ───────────────────────────────────────────────────────────────────

export function LiveTimePage() {
  return (
    <Routes>
      <Route index element={<LiveTimeHome />} />
      <Route path="users/:id" element={<LiveTimeUser />} />
      <Route path="rooms/:id" element={<LiveTimeRoom />} />
      <Route path="audit" element={<LiveTimeAudit />} />
      <Route path="*" element={<LiveTimeHome />} />
    </Routes>
  )
}

/** Home: search by username / email / phone / id, today across the platform, who is counting now. */
function LiveTimeHome() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [q, setQ] = useState(params.get('q') || '')
  const [hits, setHits] = useState<Hit[] | null>(null)
  const [searching, setSearching] = useState(false)
  const [days, setDays] = useState<DaysResp | null>(null)
  const [live, setLive] = useState<LiveResp | null>(null)
  const [error, setError] = useState('')
  const inflight = useRef<AbortController | null>(null)
  const serverNow = useServerClock(live?.server_now)

  const from = params.get('from') || ''
  const to = params.get('to') || ''

  const loadDays = useCallback(async () => {
    const p = new URLSearchParams()
    if (from) p.set('from', from)
    if (to) p.set('to', to)
    const r = await get<DaysResp>(`/v1/admin/live-time/days?${p}`)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setDays(r.data)
  }, [from, to])

  const loadLive = useCallback(async (quiet: boolean) => {
    const r = await get<LiveResp>('/v1/admin/live-time/live')
    if (!r.ok) {
      if (!quiet) setError(r.message)
      return
    }
    setLive(r.data)
  }, [])

  useEffect(() => {
    void loadDays()
  }, [loadDays])

  useEffect(() => {
    void loadLive(false)
    const tick = () => {
      if (document.visibilityState === 'visible') void loadLive(true)
    }
    const id = window.setInterval(tick, REFRESH_MS)
    return () => window.clearInterval(id)
  }, [loadLive])

  const search = useCallback(async (term: string) => {
    inflight.current?.abort()
    const t = term.trim()
    if (!t) {
      setHits(null)
      return
    }
    const ctl = new AbortController()
    inflight.current = ctl
    setSearching(true)
    const r = await get<{ items: Hit[] }>(`/v1/admin/live-time/search?q=${encodeURIComponent(t)}`, { signal: ctl.signal })
    if (ctl.signal.aborted) return
    setSearching(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setHits(r.data.items || [])
  }, [])

  useEffect(() => {
    const id = window.setTimeout(() => void search(q), 250)
    return () => window.clearTimeout(id)
  }, [q, search])

  const todayRow = days?.days.find((d) => d.day === days.calendar.today)
  const rangeTotal = (days?.days || []).reduce((a, d) => a + d.total_ms, 0)
  const liveTotal = (live?.items || []).reduce((a, o) => a + liveMs(o.verified_ms, o.counting, live!.server_now, serverNow), 0)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head" style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: 240 }}>
          <h1>Live time</h1>
          <p>
            Verified broadcast time per host: what the server observed, to the second. Search a host to see their days and
            sessions; open a session for its proof.
          </p>
          <CalendarLine cal={days?.calendar} />
        </div>
        <Link to="/live-time/audit" className="admin-nav-link" style={{ padding: '6px 10px' }}>
          Time audit →
        </Link>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

      <Card>
        <Field
          label="Find a host"
          placeholder="Username, email, phone, name or id"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          autoFocus
          hint="Matches start of username, exact email or phone, and name words."
        />
        {searching ? <PageLoader label="Searching…" /> : null}
        {hits && !searching ? (
          hits.length === 0 ? (
            <p className="muted" style={{ margin: '10px 0 0' }}>
              No account matches “{q.trim()}”.
            </p>
          ) : (
            <div className="table-wrap" style={{ marginTop: 10 }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Account</th>
                    <th>Email</th>
                    <th>Today</th>
                    <th>This week</th>
                    <th>This month</th>
                    <th>Lifetime</th>
                    <th>Now</th>
                  </tr>
                </thead>
                <tbody>
                  {hits.map((h) => (
                    <tr key={h.id} className="row-link" onClick={() => navigate(`/live-time/users/${h.id}`)}>
                      <td>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                          <Avatar url={h.avatar_url} name={displayName(h)} />
                          <span>
                            <strong>{displayName(h)}</strong>
                            <div className="muted" style={{ fontSize: 11 }}>
                              @{h.username} · {h.id}
                            </div>
                          </span>
                        </span>
                      </td>
                      <td className="muted" style={{ fontSize: 12 }}>
                        {h.email || h.phone || '—'}
                      </td>
                      <td style={{ fontVariantNumeric: 'tabular-nums' }}>{h.time ? fmtMs(h.time.today_ms) : '—'}</td>
                      <td style={{ fontVariantNumeric: 'tabular-nums' }}>{h.time ? fmtMs(h.time.week_ms, { seconds: false }) : '—'}</td>
                      <td style={{ fontVariantNumeric: 'tabular-nums' }}>{h.time ? fmtMs(h.time.month_ms, { seconds: false }) : '—'}</td>
                      <td style={{ fontVariantNumeric: 'tabular-nums' }}>{h.time ? fmtMs(h.time.lifetime_ms, { seconds: false }) : '—'}</td>
                      <td>{h.time?.live ? <CountingPill live counting={h.time.counting} /> : <span className="muted">—</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        ) : null}
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
        <Stat label="Counting now" value={String(live?.count ?? '—')} sub="open segments on this server" tone="live" />
        <Stat label="Live total" value={live ? fmtMs(liveTotal) : '—'} sub="verified so far in open rooms" tone="live" />
        <Stat label="Today" value={todayRow ? fmtMs(todayRow.total_ms, { seconds: false }) : '0m'} sub={todayRow ? `${todayRow.hosts} hosts · closed sessions` : 'closed sessions'} />
        <Stat label="Range" value={days ? fmtMs(rangeTotal, { seconds: false }) : '—'} sub={days ? `${days.from} → ${days.to}` : ''} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.2fr) minmax(0, 1fr)', gap: 14, alignItems: 'start' }}>
        <Card title="Counting right now">
          {!live ? (
            <PageLoader label="Loading…" />
          ) : live.items.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>
              Nobody is on a stage right now.
            </p>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Who</th>
                    <th>Role</th>
                    <th>Verified</th>
                    <th>Paused</th>
                    <th>Last beat</th>
                    <th>State</th>
                    <th>Room</th>
                  </tr>
                </thead>
                <tbody>
                  {live.items.map((o) => (
                    <tr key={o.segment_id} className="row-link" onClick={() => navigate(`/live-time/users/${o.user_id}`)}>
                      <td>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                          <Avatar url={o.user?.avatar_url} name={displayName(o.user || {}) || o.user_id} />
                          <span>
                            <strong>{displayName(o.user || {}) || o.user_id}</strong>
                            <div className="muted" style={{ fontSize: 11 }}>
                              {o.user ? `@${o.user.username} · ` : ''}
                              {o.user_id}
                            </div>
                          </span>
                        </span>
                      </td>
                      <td>
                        {o.role} · {o.mode}
                        {o.segment_no > 1 ? <span className="muted"> · seg {o.segment_no}</span> : null}
                      </td>
                      <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>{fmtMs(liveMs(o.verified_ms, o.counting, live.server_now, serverNow))}</td>
                      <td style={{ fontVariantNumeric: 'tabular-nums' }}>{o.paused_ms > 0 ? fmtMs(o.paused_ms) : '—'}</td>
                      <td className="muted" style={{ fontSize: 12 }}>
                        {fmtAgo(o.last_seen_at, serverNow)}
                      </td>
                      <td>
                        <CountingPill live counting={o.counting} />
                      </td>
                      <td onClick={(e) => e.stopPropagation()}>
                        <Link to={`/live-time/rooms/${o.room_id}`}>{o.room_id}</Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <Card title="Days" actions={days ? <RangeBar from={days.from} to={days.to} today={days.calendar.today} /> : undefined}>
          {!days ? (
            <PageLoader label="Loading…" />
          ) : (
            <>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Day</th>
                      <th>Hosts</th>
                      <th>Total</th>
                      <th>Host</th>
                      <th>Co-host</th>
                    </tr>
                  </thead>
                  <tbody>
                    {days.days.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="muted">
                          No closed sessions in this range yet.
                        </td>
                      </tr>
                    ) : (
                      days.days.map((d) => (
                        <tr key={d.day}>
                          <td>
                            {d.day}
                            {d.day === days.calendar.today ? <span className="muted"> · today</span> : null}
                          </td>
                          <td>{d.hosts}</td>
                          <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>{fmtMs(d.total_ms, { seconds: false })}</td>
                          <td style={{ fontVariantNumeric: 'tabular-nums' }}>{fmtMs(d.host_video_ms + d.host_audio_ms, { seconds: false })}</td>
                          <td style={{ fontVariantNumeric: 'tabular-nums' }}>{fmtMs(d.cohost_video_ms + d.cohost_audio_ms, { seconds: false })}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
              {days.top.length > 0 ? (
                <>
                  <div style={{ marginTop: 14, fontWeight: 700, fontSize: 13 }}>Top hosts in range</div>
                  <div className="role-list" style={{ marginTop: 6 }}>
                    {days.top.map((t) => (
                      <Link key={t.user_id} to={`/live-time/users/${t.user_id}`} className="role-row" style={{ display: 'flex', alignItems: 'center', gap: 10, textDecoration: 'none' }}>
                        <Avatar url={t.user?.avatar_url} name={displayName(t.user || {}) || t.user_id} size={28} />
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div className="role-row-title">{displayName(t.user || {}) || t.user_id}</div>
                          <div className="role-row-meta">{t.user ? `@${t.user.username}` : t.user_id}</div>
                        </div>
                        <strong style={{ fontVariantNumeric: 'tabular-nums' }}>{fmtMs(t.total_ms, { seconds: false })}</strong>
                      </Link>
                    ))}
                  </div>
                </>
              ) : null}
            </>
          )}
        </Card>
      </div>
    </div>
  )
}

/** One host: today / week / month / lifetime, day records, sessions, live row. */
function LiveTimeUser() {
  const { id = '' } = useParams()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { can } = useAuth()
  const [data, setData] = useState<UserResp | null>(null)
  const [error, setError] = useState('')
  const [rebuilding, setRebuilding] = useState(false)
  const [note, setNote] = useState('')
  const [asOf, setAsOf] = useState('')
  const serverNow = useServerClock(asOf)
  const from = params.get('from') || ''
  const to = params.get('to') || ''

  const load = useCallback(
    async (quiet: boolean) => {
      const p = new URLSearchParams()
      if (from) p.set('from', from)
      if (to) p.set('to', to)
      const r = await get<UserResp>(`/v1/admin/live-time/users/${id}?${p}`)
      if (!r.ok) {
        if (!quiet) setError(r.message)
        return
      }
      setError('')
      setData(r.data)
      setAsOf(r.data.calendar.server_now)
    },
    [id, from, to],
  )

  useEffect(() => {
    void load(false)
    const tick = () => {
      if (document.visibilityState === 'visible') void load(true)
    }
    const t = window.setInterval(tick, REFRESH_MS)
    return () => window.clearInterval(t)
  }, [load])

  const rebuild = async () => {
    if (!window.confirm('Recompute every day of this host from the ledger? Totals shown elsewhere may move for a moment.')) return
    setRebuilding(true)
    const r = await post<{ days: number }>(`/v1/admin/live-time/users/${id}/rebuild`)
    setRebuilding(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setNote(`Rebuilt ${r.data.days} day${r.data.days === 1 ? '' : 's'} from the ledger.`)
    void load(true)
  }

  if (!data) return error ? <div className="err-banner">{error}</div> : <PageLoader label="Loading host…" />

  const u = data.user
  const now = data.now
  const liveToday = now.live ? liveMs(now.today_ms, now.counting, asOf, serverNow) : now.today_ms
  const liveRun = now.live ? liveMs(now.running_ms, now.counting, asOf, serverNow) : 0

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head" style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: 240 }}>
          <button type="button" className="admin-nav-link" style={{ padding: '2px 0', background: 'none', border: 0, cursor: 'pointer' }} onClick={() => navigate(-1)}>
            ← Back
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 6 }}>
            <Avatar url={u.avatar_url} name={displayName(u)} size={48} />
            <div>
              <h1 style={{ margin: 0 }}>{displayName(u)}</h1>
              <div className="muted" style={{ fontSize: 12 }}>
                @{u.username} · {u.id}
                {u.email ? ` · ${u.email}` : ''}
                {u.phone ? ` · ${u.phone}` : ''}
              </div>
            </div>
          </div>
          <div style={{ marginTop: 6 }}>
            <CalendarLine cal={data.calendar} />
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Link to={`/accounts/${u.id}/moderation`} className="admin-nav-link" style={{ padding: '6px 10px' }}>
            Account
          </Link>
          {can('live.manage_time') ? (
            <Button size="sm" variant="secondary" loading={rebuilding} onClick={() => void rebuild()}>
              Rebuild from ledger
            </Button>
          ) : null}
        </div>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {note ? <div className="ok-banner">{note}</div> : null}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12 }}>
        <Stat label="Today" value={fmtMs(liveToday)} sub={now.live ? (now.counting ? 'counting now' : 'not counting — silent') : 'closed sessions'} tone={now.live ? 'live' : undefined} />
        <Stat label="This week" value={fmtMs(now.week_ms + (now.live ? liveRun - now.running_ms : 0), { seconds: false })} sub={`from ${data.calendar.week_from}`} />
        <Stat label="This month" value={fmtMs(now.month_ms + (now.live ? liveRun - now.running_ms : 0), { seconds: false })} sub={data.calendar.month} />
        <Stat label="Lifetime" value={fmtMs(now.lifetime_ms + (now.live ? liveRun - now.running_ms : 0), { seconds: false })} sub={now.historical_ms > 0 ? `${fmtMs(now.historical_ms, { seconds: false })} historical` : 'verified'} />
        {now.live ? <Stat label="This live" value={fmtMs(liveRun)} sub={now.paused_ms > 0 ? `${fmtMs(now.paused_ms)} paused` : `room ${now.room_id}`} tone="live" /> : null}
      </div>

      <Card title="Sessions" actions={<RangeBar from={data.from} to={data.to} today={data.calendar.today} />}>
        {data.sessions.length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>
            No sessions between {data.from} and {data.to}.
          </p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Started</th>
                  <th>Ended</th>
                  <th>Role</th>
                  <th>Verified</th>
                  <th>Paused</th>
                  <th>Segments</th>
                  <th>End</th>
                  <th>Proof</th>
                </tr>
              </thead>
              <tbody>
                {data.sessions.map((s) => (
                  <tr key={`${s.room_id}:${s.user_id}`} className="row-link" onClick={() => navigate(`/live-time/rooms/${s.room_id}`)}>
                    <td>{fmtClock(s.started_at)}</td>
                    <td>{s.live ? <span className="status-pill on">Live</span> : fmtTime(s.ended_at)}</td>
                    <td>
                      {s.role}
                      {s.kind ? <span className="muted"> · {s.kind}</span> : null}
                    </td>
                    <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>
                      {s.live ? fmtMs(liveMs(s.verified_ms, !!s.counting, asOf, serverNow)) : fmtMs(s.verified_ms)}
                    </td>
                    <td style={{ fontVariantNumeric: 'tabular-nums' }}>{s.paused_ms > 0 ? fmtMs(s.paused_ms) : '—'}</td>
                    <td>{s.segments}</td>
                    <td className="muted" style={{ fontSize: 12 }}>
                      {s.live ? <CountingPill live counting={s.counting} /> : s.end_reason || s.room_end || '—'}
                    </td>
                    <td>
                      {s.historical ? (
                        <ProofPill q="historical" />
                      ) : s.live ? (
                        <span className="muted">—</span>
                      ) : (
                        <ProofPill q={(s.beats || 0) === 0 ? 'none' : (s.max_gap_ms || 0) > 22_000 ? 'thin' : 'strong'} />
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title="Days">
        {data.days.length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>
            No live time between {data.from} and {data.to}.
          </p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Day</th>
                  <th>Total</th>
                  <th>Host video</th>
                  <th>Host audio</th>
                  <th>Co-host</th>
                  <th>Historical</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.days.map((d) => (
                  <tr key={d.day}>
                    <td>
                      {d.day}
                      {d.day === data.calendar.today ? <span className="muted"> · today</span> : null}
                    </td>
                    <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>
                      {fmtMs(d.day === data.calendar.today && now.live ? liveMs(d.total_ms, now.counting, asOf, serverNow) : d.total_ms)}
                      {d.live_ms ? <span className="muted" style={{ fontSize: 11 }}> · incl. live</span> : null}
                    </td>
                    <td style={{ fontVariantNumeric: 'tabular-nums' }}>{fmtMs(d.host_video_ms)}</td>
                    <td style={{ fontVariantNumeric: 'tabular-nums' }}>{fmtMs(d.host_audio_ms)}</td>
                    <td style={{ fontVariantNumeric: 'tabular-nums' }}>{fmtMs(d.cohost_video_ms + d.cohost_audio_ms)}</td>
                    <td style={{ fontVariantNumeric: 'tabular-nums' }}>{d.historical_ms > 0 ? fmtMs(d.historical_ms) : '—'}</td>
                    <td>
                      <Link to={`/live-time/users/${u.id}?from=${d.day}&to=${d.day}`} onClick={(e) => e.stopPropagation()}>
                        Sessions
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}

/** One room: header, every participant's verified time, the segments, the transitions. */
function LiveTimeRoom() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [data, setData] = useState<RoomResp | null>(null)
  const [error, setError] = useState('')
  const serverNow = useServerClock(data?.server_now)

  const load = useCallback(
    async (quiet: boolean) => {
      const r = await get<RoomResp>(`/v1/admin/live-time/rooms/${id}`)
      if (!r.ok) {
        if (!quiet) setError(r.message)
        return
      }
      setError('')
      setData(r.data)
    },
    [id],
  )

  useEffect(() => {
    void load(false)
  }, [load])

  useEffect(() => {
    if (!data?.room.live) return
    const t = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load(true)
    }, REFRESH_MS)
    return () => window.clearInterval(t)
  }, [data?.room.live, load])

  if (!data) return error ? <div className="err-banner">{error}</div> : <PageLoader label="Loading session…" />

  const room = data.room
  const host = data.people[room.host_id]
  const openHost = data.open.find((o) => o.user_id === room.host_id)
  const hostLive = openHost ? liveMs(openHost.verified_ms, openHost.counting, data.server_now, serverNow) : data.host_verified_ms
  const wall = room.live ? room.wall_ms + Math.max(0, serverNow - new Date(data.server_now).getTime()) : room.wall_ms
  const unverified = Math.max(0, wall - hostLive)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head" style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: 240 }}>
          <button type="button" className="admin-nav-link" style={{ padding: '2px 0', background: 'none', border: 0, cursor: 'pointer' }} onClick={() => navigate(-1)}>
            ← Back
          </button>
          <h1 style={{ marginTop: 6 }}>{room.title || 'Untitled live'}</h1>
          <div className="muted" style={{ fontSize: 12 }}>
            Room {room.id} · {room.kind} · started {fmtClock(room.started_at)}
            {room.ended_at ? ` · ended ${fmtClock(room.ended_at)} (${room.end_reason || 'ended'})` : ' · live'}
            {room.client_id ? ` · client ${room.client_id}` : ''}
          </div>
          {host ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
              <Avatar url={host.avatar_url} name={displayName(host)} size={28} />
              <Link to={`/live-time/users/${host.id}`}>
                <strong>{displayName(host)}</strong> <span className="muted">@{host.username}</span>
              </Link>
            </div>
          ) : null}
        </div>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12 }}>
        <Stat label="Host verified" value={fmtMs(hostLive)} sub="credited to the host" tone={room.live ? 'live' : undefined} />
        <Stat label="Wall time" value={fmtMs(wall)} sub="start → end on the clock" />
        <Stat label="Not counted" value={fmtMs(unverified)} sub={unverified > 0 ? 'silence, gaps, cap' : 'none'} tone={unverified > 60_000 ? 'warn' : undefined} />
        <Stat label="Segments" value={String(data.segments.length + data.open.length)} sub={`${data.open.length} open`} />
      </div>

      <Card title="People on this stage">
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Who</th>
                <th>Verified</th>
                <th>State</th>
              </tr>
            </thead>
            <tbody>
              {Object.values(data.people).map((p) => {
                const o = data.open.find((x) => x.user_id === p.id)
                const v = o ? liveMs(o.verified_ms, o.counting, data.server_now, serverNow) : p.verified_ms
                return (
                  <tr key={p.id} className="row-link" onClick={() => navigate(`/live-time/users/${p.id}`)}>
                    <td>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Avatar url={p.avatar_url} name={displayName(p)} />
                        <span>
                          <strong>{displayName(p)}</strong>
                          <div className="muted" style={{ fontSize: 11 }}>
                            @{p.username} · {p.id}
                            {p.id === room.host_id ? ' · host' : ' · co-host'}
                          </div>
                        </span>
                      </span>
                    </td>
                    <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>{fmtMs(v)}</td>
                    <td>{o ? <CountingPill live counting={o.counting} /> : <span className="muted">closed</span>}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="Segments">
        <p className="muted" style={{ marginTop: 0, fontSize: 12 }}>
          A segment is one unbroken stretch the server observed. A gap longer than the allowance ends one segment at the last
          heartbeat + allowance and starts the next at the return; the gap itself is never counted.
        </p>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Who</th>
                <th>From</th>
                <th>To</th>
                <th>Duration</th>
                <th>Gap before</th>
                <th>Beats</th>
                <th>Max gap</th>
                <th>Last beat</th>
                <th>End</th>
                <th>Proof</th>
              </tr>
            </thead>
            <tbody>
              {data.segments.map((g) => {
                const p = data.people[g.user_id]
                return (
                  <tr key={g.id}>
                    <td>{g.segment_no}</td>
                    <td>
                      {p ? `@${p.username}` : g.user_id} <span className="muted">· {g.role}</span>
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }}>{fmtTime(g.started_at)}</td>
                    <td style={{ whiteSpace: 'nowrap' }}>{fmtTime(g.ended_at)}</td>
                    <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>{fmtMs(g.duration_ms)}</td>
                    <td style={{ fontVariantNumeric: 'tabular-nums' }}>
                      {g.gap_before_ms > 0 ? (
                        <>
                          {fmtMs(g.gap_before_ms)} <span className="muted">· {g.gap_cause || 'client'}</span>
                        </>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td>{g.beats}</td>
                    <td style={{ fontVariantNumeric: 'tabular-nums' }}>{g.max_gap_ms > 0 ? `${(g.max_gap_ms / 1000).toFixed(1)}s` : '—'}</td>
                    <td style={{ whiteSpace: 'nowrap' }}>{fmtTime(g.last_seen_at)}</td>
                    <td className="muted" style={{ fontSize: 12 }}>
                      {g.end_reason}
                    </td>
                    <td>
                      <ProofPill q={g.proof_quality} />
                    </td>
                  </tr>
                )
              })}
              {data.open.map((o) => {
                const p = data.people[o.user_id]
                return (
                  <tr key={o.segment_id} style={{ background: 'var(--accent-soft)' }}>
                    <td>{o.segment_no}</td>
                    <td>
                      {p ? `@${p.username}` : o.user_id} <span className="muted">· {o.role}</span>
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }}>{fmtTime(o.started_at)}</td>
                    <td>
                      <span className="status-pill on">open</span>
                    </td>
                    <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>{fmtMs(liveMs(o.verified_ms, o.counting, data.server_now, serverNow))}</td>
                    <td style={{ fontVariantNumeric: 'tabular-nums' }}>{o.paused_ms > 0 ? fmtMs(o.paused_ms) : '—'}</td>
                    <td>—</td>
                    <td>—</td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      {fmtTime(o.last_seen_at)} <span className="muted">· {fmtAgo(o.last_seen_at, serverNow)}</span>
                    </td>
                    <td>
                      <CountingPill live counting={o.counting} />
                    </td>
                    <td>
                      <span className="muted">allowance {Math.round(o.allowance_ms / 1000)}s</span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="Transitions">
        <p className="muted" style={{ marginTop: 0, fontSize: 12 }}>
          Every change of state the ledger made for this room, with the rule it applied. Heartbeats are not listed; their
          effect is in the segments above.
        </p>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>At</th>
                <th>Event</th>
                <th>Who</th>
                <th>Detail</th>
                <th>Server</th>
              </tr>
            </thead>
            <tbody>
              {data.audit.map((a) => {
                const p = a.user_id ? data.people[a.user_id] : undefined
                const d = a.detail || {}
                const bits: string[] = []
                if (typeof d.rule === 'string') bits.push(`rule ${d.rule}`)
                if (typeof d.reason === 'string') bits.push(d.reason)
                if (typeof d.duration_ms === 'number') bits.push(fmtMs(d.duration_ms))
                if (typeof d.gap_ms === 'number') bits.push(`gap ${fmtMs(d.gap_ms)} (${String(d.gap_cause ?? 'client')})`)
                if (typeof d.last_seen === 'string') bits.push(`last beat ${fmtTime(d.last_seen)}`)
                if (typeof d.ended_at === 'string') bits.push(`ended ${fmtTime(d.ended_at)}`)
                if (typeof d.clock_step_ms === 'number') bits.push(`clock step ${d.clock_step_ms}ms`)
                if (typeof d.from_room_id === 'number' || typeof d.from_room_id === 'string') bits.push(`from room ${String(d.from_room_id)}`)
                if (typeof d.to_room_id === 'number' || typeof d.to_room_id === 'string') bits.push(`to room ${String(d.to_room_id)}`)
                if (typeof d.open_segments === 'number') bits.push(`${d.open_segments} open adopted`)
                if (d.stray === true) bits.push('stray row')
                if (d.reconciler === true) bits.push('reconciler')
                return (
                  <tr key={a.id}>
                    <td style={{ whiteSpace: 'nowrap' }}>{fmtClock(a.at)}</td>
                    <td>
                      <code style={{ fontSize: 12 }}>{a.event}</code>
                    </td>
                    <td>{p ? `@${p.username}` : a.user_id || '—'}</td>
                    <td className="muted" style={{ fontSize: 12 }}>
                      {bits.join(' · ') || JSON.stringify(d)}
                    </td>
                    <td className="muted" style={{ fontSize: 11 }}>
                      {a.instance_id}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}

/** Time audit: health of the accounting pipeline and reconciliation findings. */
function LiveTimeAudit() {
  const { can } = useAuth()
  const [health, setHealth] = useState<Health | null>(null)
  const [findings, setFindings] = useState<Finding[]>([])
  const [all, setAll] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<string>('')

  const load = useCallback(async () => {
    const [h, f] = await Promise.all([
      get<Health>('/v1/admin/live-time/health'),
      get<{ items: Finding[] }>(`/v1/admin/live-time/findings?all=${all ? 1 : 0}&limit=200`),
    ])
    if (!h.ok) {
      setError(h.message)
      return
    }
    if (!f.ok) {
      setError(f.message)
      return
    }
    setError('')
    setHealth(h.data)
    setFindings(f.data.items || [])
  }, [all])

  useEffect(() => {
    void load()
    const t = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load()
    }, 10_000)
    return () => window.clearInterval(t)
  }, [load])

  const resolve = async (id: string) => {
    setBusy(id)
    const r = await post(`/v1/admin/live-time/findings/${id}/resolve`)
    setBusy('')
    if (!r.ok) {
      setError(r.message)
      return
    }
    void load()
  }

  const rebuildAll = async () => {
    if (!window.confirm('Re-project the whole ledger under the current day definition? Day and month totals swap atomically when done.')) return
    setBusy('rebuild')
    const r = await post('/v1/admin/live-time/rebuild')
    setBusy('')
    if (!r.ok) {
      setError(r.message)
      return
    }
    void load()
  }

  const agg = health?.aggregator
  const mismatch = agg && (agg.meta_tz !== agg.settings_tz || agg.meta_day_start !== agg.settings_day_start || agg.meta_week_start !== agg.settings_week_start)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, minWidth: 0, maxWidth: '100%' }}>
      <div className="page-head" style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap', minWidth: 0 }}>
        <div style={{ flex: '1 1 240px', minWidth: 0 }}>
          <h1>Time audit</h1>
          <p>
            Is the accounting pipeline healthy, and does the ledger agree with itself? Findings are written by the
            reconciler every five minutes; a rebuild recomputes projections from the ledger, which never changes.
          </p>
        </div>
        <Link to="/live-time" className="admin-nav-link" style={{ padding: '6px 10px', flexShrink: 0 }}>
          ← Live time
        </Link>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

      {health ? (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 160px), 1fr))',
            gap: 12,
            minWidth: 0,
          }}
        >
          <Stat label="Aggregator" value={agg?.halted ? 'Halted' : 'Running'} sub={agg?.halted ? 'calendar mismatch or rebuild in flight' : `backlog ${agg?.backlog ?? 0}`} tone={agg?.halted ? 'warn' : undefined} />
          <Stat label="Open segments" value={String(health.open_segments)} sub={`checkpoint every ${Math.round(health.checkpoint_interval_ms / 1000)}s`} />
          <Stat label="Open findings" value={String(health.open_findings)} tone={health.open_findings > 0 ? 'warn' : undefined} />
          <Stat label="Closed" value={String(health.counters.closed ?? 0)} sub={`${health.counters.splits ?? 0} splits · ${health.counters.clock_steps ?? 0} clock steps`} />
          <Stat label="Aggregated" value={String(health.counters.agg_segments ?? 0)} sub={`${health.counters.agg_failures ?? 0} failures`} />
          <Stat
            label="Server"
            value={health.instance_id.length > 18 ? `${health.instance_id.slice(0, 16)}…` : health.instance_id}
            sub={agg?.oldest_open ? `oldest open ${fmtClock(agg.oldest_open)}` : 'no open segments'}
          />
        </div>
      ) : (
        <PageLoader label="Loading health…" />
      )}

      {agg ? (
        <Card title="Day definition in use">
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 220px), 1fr))',
              gap: 12,
              minWidth: 0,
            }}
          >
            <div style={{ minWidth: 0 }}>
              <div className="muted" style={{ fontSize: 11, textTransform: 'uppercase' }}>
                Projections built with
              </div>
              <div style={{ fontWeight: 700, overflowWrap: 'anywhere' }}>
                {agg.meta_tz} · {agg.meta_day_start} · week from {agg.meta_week_start}
              </div>
              <div className="muted" style={{ fontSize: 11 }}>
                active since {fmtClock(agg.activated_at)}
              </div>
            </div>
            <div style={{ minWidth: 0 }}>
              <div className="muted" style={{ fontSize: 11, textTransform: 'uppercase' }}>
                Settings say
              </div>
              <div style={{ fontWeight: 700, color: mismatch ? 'var(--danger)' : undefined, overflowWrap: 'anywhere' }}>
                {agg.settings_tz} · {agg.settings_day_start} · week from {agg.settings_week_start}
              </div>
              {mismatch ? (
                <div className="muted" style={{ fontSize: 11 }}>
                  mismatch — the aggregator holds until a rebuild activates this calendar
                </div>
              ) : null}
            </div>
          </div>
          {agg.rebuild && Object.keys(agg.rebuild).length > 0 ? (
            <div
              style={{
                marginTop: 10,
                padding: 10,
                background: 'var(--wash)',
                borderRadius: 'var(--radius-sm)',
                fontSize: 12,
                overflowWrap: 'anywhere',
              }}
            >
              <strong>Rebuild:</strong> {String(agg.rebuild.phase)} · {String(agg.rebuild.segments ?? 0)} segments
              {typeof agg.rebuild.error === 'string' ? ` · ${agg.rebuild.error}` : ''}
              {typeof agg.rebuild.updated_at === 'string' ? ` · ${fmtClock(agg.rebuild.updated_at)}` : ''}
            </div>
          ) : null}
          {can('live.manage_time') ? (
            <div style={{ marginTop: 12 }}>
              <Button size="sm" variant="secondary" loading={busy === 'rebuild'} onClick={() => void rebuildAll()}>
                Rebuild all projections
              </Button>
            </div>
          ) : null}
        </Card>
      ) : null}

      <Card
        title="Findings"
        actions={
          <label className="muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, cursor: 'pointer', fontSize: 12, whiteSpace: 'nowrap' }}>
            <input type="checkbox" checked={all} onChange={(e) => setAll(e.target.checked)} /> Show resolved
          </label>
        }
      >
        {findings.length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>
            Nothing to report. The ledger agrees with itself.
          </p>
        ) : (
          <div className="table-wrap" style={{ maxWidth: '100%' }}>
            <table className="data-table" style={{ tableLayout: 'fixed', width: '100%', minWidth: 640 }}>
              <colgroup>
                <col style={{ width: '18%' }} />
                <col style={{ width: '16%' }} />
                <col style={{ width: '10%' }} />
                <col style={{ width: '18%' }} />
                <col style={{ width: '26%' }} />
                <col style={{ width: '12%' }} />
              </colgroup>
              <thead>
                <tr>
                  <th>Found</th>
                  <th>Kind</th>
                  <th>Severity</th>
                  <th>Who / where</th>
                  <th>Detail</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {findings.map((f) => (
                  <tr key={f.id} style={f.resolved ? { opacity: 0.6 } : undefined}>
                    <td style={{ whiteSpace: 'nowrap', fontSize: 12 }}>{fmtClock(f.found_at)}</td>
                    <td>
                      <code style={{ fontSize: 11, overflowWrap: 'anywhere' }}>{f.kind}</code>
                    </td>
                    <td>
                      <span className={`status-pill ${f.severity >= 3 ? 'off' : ''}`} style={{ fontSize: 10 }}>
                        {f.severity >= 3 ? 'error' : f.severity === 2 ? 'warn' : 'info'}
                      </span>
                    </td>
                    <td style={{ fontSize: 12, overflowWrap: 'anywhere' }}>
                      {f.user_id ? <Link to={`/live-time/users/${f.user_id}`}>user {f.user_id}</Link> : null}
                      {f.room_id ? (
                        <>
                          {f.user_id ? ' · ' : ''}
                          <Link to={`/live-time/rooms/${f.room_id}`}>room {f.room_id}</Link>
                        </>
                      ) : null}
                      {f.day ? ` · ${f.day}` : ''}
                    </td>
                    <td
                      className="muted"
                      style={{ fontSize: 11, overflowWrap: 'anywhere', wordBreak: 'break-word', whiteSpace: 'normal' }}
                      title={JSON.stringify(f.detail)}
                    >
                      {JSON.stringify(f.detail)}
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      {!f.resolved && can('live.manage_time') ? (
                        <Button size="sm" variant="ghost" loading={busy === f.id} onClick={() => void resolve(f.id)}>
                          Resolve
                        </Button>
                      ) : f.resolved ? (
                        <span className="muted" style={{ fontSize: 11 }}>
                          resolved
                        </span>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
