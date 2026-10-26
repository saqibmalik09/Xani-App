import { Fragment, useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { del, get, post, put } from '../lib/api'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { Avatar, RangeBar, Stat, displayName, fmtClock, fmtMs } from './LiveTime'
import { LiveRewardCampaigns } from './LiveRewardCampaigns'

/**
 * Live rewards: what hosts were paid for verified live time, when, and why.
 * Every day label here is the Live Time business day (same tz + day start
 * as the Live time pages), and every reward carries the instant its
 * threshold was crossed, the instant the wallet moved, and the balance
 * before → after, so a question about any payout has one answer.
 */

export type CalendarInfo = { tz: string; day_start: string; week_start: string; today: string; now: string }

export type Message = {
  title: string
  body: string
  detail: string
  day: string
  reached_at: string
  added_at: string
  before?: number | null
  after?: number | null
  tz: string
  status: string
  status_label: string
}

export type LedgerView = {
  id: string
  kind: 'earn' | 'reversal' | 'manual'
  campaign_id: string
  campaign: string
  version_no: number
  user_id: string
  day: string
  period_kind: string
  period_key: string
  role: string
  mode_rule: string
  required_ms: number
  verified_ms: number
  asset: string
  amount: number
  status: string
  status_label: string
  delivery: string
  source: string
  reached_at: string
  evaluated_at: string
  earned_at: string
  issued_at?: string
  claimable_from?: string
  claimable_until?: string
  claimed_at?: string
  balance_before?: number
  balance_after?: number
  wallet_ref?: string
  delay_ms: number
  message: Message
  note?: string
}

type Hit = { id: string; username: string; full_name: string; avatar_url?: string; state: number; banned: boolean; email?: string; phone?: string }

type Totals = { Asset: string; Earned: number; Issued: number; Claimed: number; Reversed: number; Count: number }

type PayoutsResp = { items: LedgerView[]; people: Record<string, Hit>; totals: Totals[]; cursor: string; calendar: CalendarInfo }

type Bucket = {
  key: string
  label: string
  earned: Record<string, number>
  issued: Record<string, number>
  claimed: Record<string, number>
  reversed: Record<string, number>
  count: number
  users: number
  thresholds_reached: number
  blocked: number
}

type Health = {
  enabled: boolean
  score: number
  status: string
  counts_24h: Record<string, number>
  open_findings: Record<string, number>
  dirty_backlog: number
  repair_backlog: number
  stuck_outbox: number
  event_lag_ms: number
  scheduler_size: number
  process: Record<string, number>
  active_campaigns: number
  calendar: CalendarInfo
  uptime: string
}

type LeafState = { type: string; op: string; window: string; window_n?: number; required: unknown; actual: unknown; pass: boolean; reason?: string }

type CampaignStatus = {
  campaign_id: string
  campaign: string
  period_kind: string
  period_key: string
  eligible: boolean
  reason?: string
  verified_ms: number
  next_threshold_ms: number
  remaining_ms: number
  next_amount: number
  next_asset: string
  next_due_at?: string
  rewards_in_period: number
  max_total: number
  levels: { threshold_ms: number; asset: string; amount: number; amount_cohost?: number; label?: string; reached: boolean; paid: boolean }[]
  roles: string[]
  mode_rule: string
  delivery: string
  conditions?: LeafState[]
  pending?: { threshold_ms: number; status: string; reason?: string; reached_at: string; next_eval_at?: string; deadline?: string }[]
  // Gift-level ladder (repeating rewards with levels by diamonds received —
  // what the host is credited; the sender pays coins).
  ladder?: LadderStatus | null
  computed_at?: string
  revision?: number
  stale?: boolean
}

/** The host's position on a gift-level ladder, as the server computed it. */
type LadderStatus = {
  tier_key?: string
  tier_name?: string
  tier_kind?: string
  rate: number
  asset?: string
  received_diamonds: number
  next_tier_name?: string
  next_tier_min?: number
  next_tier_rate?: number
  remaining_diamonds: number
  hours_paid_today: number
  cap_hours_today: number
  today_total: number
  new_host: boolean
  new_host_days_left?: number
  unlocked: boolean
  // Milestone: the ladder prices ONE fixed-level payout per period, not each hour.
  milestone?: boolean
  // Group switches, flippable on the active version from the campaign page.
  weekly_on?: boolean
  new_host_on?: boolean
  tiers: { key: string; name: string; kind: string; min_received: number; new_host_days?: number; amount: number; amount_cohost?: number | null; asset: string; max_hours_per_day: number; current: boolean; reached: boolean }[]
}

/** Roster / search column: the stored snapshot headline for one campaign. */
type LevelBrief = {
  campaign_id: string
  campaign: string
  period_key: string
  eligible: boolean
  reason?: string
  next_amount: number
  next_asset?: string
  remaining_ms: number
  rewards_in_period: number
  ladder?: LadderStatus | null
  computed_at: string
  revision: number
  stale?: boolean
}

type Explain = {
  live: boolean
  counting: boolean
  campaigns: CampaignStatus[]
  calendar: CalendarInfo
  claimable: { asset: string; amount: number; count: number; waiting_amount: number; next_opens_at?: string }[]
  at: string
  snapshot: {
    pieces: number
    overlap_clips: number
    reset_ms?: number
    reset_windows?: number
    open_room: string
    open_since?: string
    open_counted_until?: string
    sessions: Record<string, string>
  }
  progress: Record<string, unknown>[]
  thresholds: Record<string, unknown>[]
  recent: LedgerView[]
  sessions?: SessionRecord[]
}

type SessionCampaign = {
  campaign_id: string
  campaign: string
  period_kind: string
  counts: boolean
  why?: string
  counted_ms: number
  per_session: boolean
  reached_ms: number[]
  next_threshold_ms: number
}
type SessionRecord = {
  key: string
  room_id: string
  role: string
  mode: string
  day: string
  start: string
  end: string
  open: boolean
  historical: boolean
  live_ms: number
  reset_ms: number
  campaigns: SessionCampaign[]
}

/** Reason codes → what an operator reads. */
const REASON_TEXT: Record<string, string> = {
  TIME_NOT_REACHED: 'Required live time not reached yet',
  ROLE_NOT_ALLOWED: 'Live role is not one this reward pays (host / co-host rule)',
  MODE_NOT_ALLOWED: 'Live type (audio / video) is not one this reward counts',
  MIN_SESSION_NOT_MET: 'A single session must be at least the minimum length',
  GIFT_VALUE_NOT_MET: 'Gift value received is below the required amount',
  GIFT_COUNT_NOT_MET: 'Gift count received is below the required number',
  UNIQUE_GIFTERS_NOT_MET: 'Not enough different people have sent gifts',
  SENT_NOT_MET: 'Gifts sent are below the requirement',
  QUALIFIED_DAYS_NOT_MET: 'Not enough qualifying days yet',
  ACCOUNT_AGE: 'Account is too new',
  USER_ROLE: 'User does not hold a required platform role',
  FACE_NOT_VERIFIED: 'Face not verified — the reward is not added until the account’s face verification is approved',
  CONDITION_FAILED: 'A condition is not met',
  MAX_TOTAL: 'Reached the maximum rewards for this period',
  MAX_PER_DAY: 'Reached the daily maximum',
  MAX_PER_WEEK: 'Reached the weekly maximum',
  MAX_PER_MONTH: 'Reached the monthly maximum',
  MIN_INTERVAL: 'Too soon after the previous reward',
  POLICY_DISABLED: 'No active policy version decides right now',
  VERSION_SUPERSEDED: 'The version that reached this was replaced',
  HISTORICAL_EXCLUDED: 'Time before the policy started is not counted',
  HISTORICAL_NO_POLICY: 'No policy existed at that time',
  CONDITION_EXPIRED: 'The deadline to meet the conditions passed',
  CLAIM_WINDOW_NOT_OPEN: 'Claim window has not opened',
  CLAIM_WINDOW_EXPIRED: 'Claim window closed',
  WALLET_FAILED: 'Wallet write failed (will retry)',
  DUPLICATE_PREVENTED: 'Already paid for this level',
  TIME_RESET: 'Cleared by an operator time reset',
  RATE_NOT_UNLOCKED: 'No gift level reached yet — this hour waits until the first level’s diamonds are received',
  LADDER_OFF: 'The level group that would pay this hour is switched off — it waits and resumes when the switch is turned back on',
  TIER_MAX_HOURS: 'Today’s paid hours for this level are used up — more hours pay again tomorrow',
}

/** Finding kinds → what an operator reads (the Checks card and the Findings page). */
const FINDING_TEXT: Record<string, string> = {
  TIME_PASS_NO_THRESHOLD: 'Live time was reached but no reward row exists (missing hour)',
  ELIGIBLE_NOT_ISSUED: 'Eligible but never added',
  LATE_ISSUE: 'Added late',
  LEDGER_NO_WALLET: 'Reward recorded but the wallet entry is missing',
  WALLET_NO_LEDGER: 'Wallet entry without a reward record',
  AMOUNT_MISMATCH: 'Paid amount differs from the level’s rate (wrongly paid)',
  WRONG_TIER: 'Paid at a different gift level than the rules give for the recorded diamonds (wrongly paid)',
  OVER_LIMIT: 'More paid hours on one day than the level allows (wrongly paid)',
  PERIOD_MISMATCH: 'Reward recorded under the wrong week / period',
  PROGRESS_MISMATCH: 'Progress shown to the host disagreed with a fresh check',
  STUCK_PENDING: 'Pending past its deadline',
  STUCK_OUTBOX: 'Notification not delivered',
  DUPLICATE_PREVENTED: 'A duplicate payout was prevented',
}
const findingText = (kind: string) => FINDING_TEXT[kind] || kind.replaceAll('_', ' ').toLowerCase()
const reasonText = (code?: string) => (code ? REASON_TEXT[code] || code.replaceAll('_', ' ').toLowerCase() : '')

const COND_NAME: Record<string, string> = {
  SESSION_TIME: 'Live time',
  LIVE_ROLE: 'Live role',
  LIVE_MODE: 'Live type',
  USER_ROLE: 'Platform role',
  ACCOUNT_AGE: 'Account age (days)',
  FACE_VERIFIED: 'Face verification',
  RECEIVED_GIFT_VALUE: 'Gift value received',
  RECEIVED_GIFT_COUNT: 'Gifts received',
  UNIQUE_GIFTERS: 'Different gifters',
  SENT_GIFT_VALUE: 'Gift value sent',
  SENT_GIFT_COUNT: 'Gifts sent',
  UNIQUE_RECIPIENTS: 'Different recipients',
  QUALIFIED_DAYS: 'Qualifying days',
}
const OP_TEXT: Record<string, string> = { gte: 'at least', lte: 'at most', eq: 'exactly', in: 'one of' }
const condValue = (type: string, v: unknown): string => {
  if (Array.isArray(v)) return v.map((x) => condValue(type, x)).join(', ')
  if (v === null || v === undefined || v === '') return '—'
  if (type === 'SESSION_TIME' && typeof v === 'number') return fmtMs(v, { seconds: false })
  if (type === 'FACE_VERIFIED') return v === true || v === 'true' ? 'verified' : 'not verified'
  return String(v)
}

/** One condition as a sentence: "Live role must be one of host — now: moderator · Not met yet". */
function ConditionLine({ s }: { s: LeafState }) {
  const win = s.window && s.window !== 'period' ? ` (${s.window}${s.window_n ? ' ' + s.window_n : ''})` : ''
  return (
    <li style={{ color: s.pass ? 'var(--ink)' : '#991b1b', marginBottom: 2 }}>
      <strong>{s.pass ? '✓' : '✗'}</strong> {COND_NAME[s.type] || s.type}
      {win} must be {OP_TEXT[s.op] || s.op} <strong>{condValue(s.type, s.required)}</strong> — now: <strong>{condValue(s.type, s.actual)}</strong>
      {s.pass ? <span className="muted"> · met</span> : <> · <strong>not met yet</strong>{s.reason ? ` (${reasonText(s.reason)})` : ''}</>}
    </li>
  )
}

type DaySummary = { day: string; label: string; live_ms: number; added: Record<string, number>; count: number }
type Summary = {
  range: string
  from: string
  to: string
  live_ms: number
  earned: Record<string, number>
  added: Record<string, number>
  claimable: Record<string, number>
  pending: Record<string, number>
  count: number
  days: DaySummary[]
}

type TimeReset = {
  id: string
  day: string
  from_at: string
  to_at: string
  cleared_ms: number
  reason: string
  removed_pending: number
  created_by: string
  created_at: string
  revoked_by: string | null
  revoked_at: string | null
}
type UserResp = { user: Hit | null; explain: Explain; summary: Summary; time: Record<string, unknown> | null; resets?: TimeReset[]; checks?: Finding[]; history_days?: number }

type DayGroup = { day: string; label: string; live_ms: number; audio_ms: number; video_ms: number; totals: Record<string, number>; rewards: LedgerView[] }

type Finding = {
  id: string
  kind: string
  severity: number
  user_id: string
  campaign_id: string
  period_key: string
  threshold_ms: number
  ledger_id?: string | null
  detail: Record<string, unknown>
  found_at: string
  repaired_at?: string | null
  repair_attempts: number
  resolved_at?: string | null
}

type Detail = LedgerView & {
  timeline: { at: string; local: string; what: string }[]
  threshold?: Record<string, unknown>
  evidence: Record<string, unknown>
  conditions: unknown
  calendar: CalendarInfo
}

// ─── formatting ──────────────────────────────────────────────────────────────

export function fmtAmounts(m?: Record<string, number>): string {
  if (!m) return '—'
  const parts = Object.entries(m)
    .filter(([, v]) => v !== 0)
    .map(([k, v]) => `${v.toLocaleString()} ${k}`)
  return parts.length ? parts.join(' · ') : '0'
}

export function StatusPill({ status, label }: { status: string; label?: string }) {
  const style: React.CSSProperties =
    status === 'added' || status === 'claimed'
      ? { background: 'var(--accent-soft)', color: 'var(--accent-hover)' }
      : status === 'claimable'
        ? { background: '#dbeafe', color: '#1e3a8a' }
        : status === 'pending'
          ? { background: '#fef3c7', color: '#92400e' }
          : { background: '#fee2e2', color: '#991b1b' }
  return (
    <span className="status-pill" style={{ ...style, fontSize: 10 }}>
      {label || status}
    </span>
  )
}

function CalendarLine({ cal }: { cal?: CalendarInfo }) {
  if (!cal) return null
  return (
    <span className="muted" style={{ fontSize: 12 }}>
      Reward day: {cal.tz} · starts {cal.day_start} · week from {cal.week_start} · today {cal.today}
    </span>
  )
}

/** One payout row: the same words the host sees, plus the audit numbers. */
export function RewardRow({ r, person, showUser, to }: { r: LedgerView; person?: Hit; showUser?: boolean; to: string }) {
  return (
    <tr>
      <td style={{ whiteSpace: 'nowrap' }}>
        <div style={{ fontWeight: 600 }}>{r.day}</div>
        <div className="muted" style={{ fontSize: 11 }}>
          {r.message.reached_at || fmtClock(r.earned_at)}
        </div>
      </td>
      {showUser ? (
        <td>
          {person ? (
            <Link to={`/live-rewards/users/${r.user_id}`} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar url={person.avatar_url} name={displayName(person)} size={24} />
              <span>
                <span style={{ fontWeight: 600 }}>{displayName(person)}</span>
                <span className="muted" style={{ fontSize: 11, display: 'block' }}>
                  @{person.username}
                </span>
              </span>
            </Link>
          ) : (
            <Link to={`/live-rewards/users/${r.user_id}`}>#{r.user_id}</Link>
          )}
        </td>
      ) : null}
      <td>
        <div style={{ fontWeight: 600 }}>{r.message.title}</div>
        <div className="muted" style={{ fontSize: 11 }}>
          {r.campaign} · v{r.version_no} · {r.role} · {r.mode_rule}
        </div>
      </td>
      <td style={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
        {fmtMs(r.required_ms, { seconds: false })}
        <div className="muted" style={{ fontSize: 11 }}>
          verified {fmtMs(r.verified_ms, { seconds: false })}
        </div>
      </td>
      <td style={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
        {r.message.added_at || '—'}
        {r.balance_before != null && r.balance_after != null ? (
          <div className="muted" style={{ fontSize: 11 }}>
            {r.balance_before.toLocaleString()} → {r.balance_after.toLocaleString()} {r.asset}
          </div>
        ) : null}
      </td>
      <td>
        <StatusPill status={r.status} label={r.status_label} />
        {r.delay_ms > 0 ? (
          <div className="muted" style={{ fontSize: 11 }} title="Threshold reached → added">
            {r.delay_ms < 1000 ? `${r.delay_ms}ms` : fmtMs(r.delay_ms)} late
          </div>
        ) : null}
      </td>
      <td>
        <Link to={to} className="muted" style={{ fontSize: 12 }}>
          Details
        </Link>
      </td>
    </tr>
  )
}

// ─── routes ──────────────────────────────────────────────────────────────────

export function LiveRewardsPage() {
  return (
    <Routes>
      <Route index element={<RewardsHome />} />
      <Route path="payouts" element={<RewardsPayouts />} />
      <Route path="payouts/:id" element={<RewardDetailPage />} />
      <Route path="time" element={<TimeAndPayouts />} />
      <Route path="stats" element={<RewardsStats />} />
      <Route path="users/:id" element={<RewardsUser />} />
      <Route path="findings" element={<RewardsFindings />} />
      <Route path="campaigns/*" element={<LiveRewardCampaigns />} />
      <Route path="*" element={<RewardsHome />} />
    </Routes>
  )
}

function SectionNav() {
  const items = [
    ['/live-rewards', 'Health'],
    ['/live-rewards/payouts', 'Payouts'],
    ['/live-rewards/time', 'Time & payouts'],
    ['/live-rewards/stats', 'Daily · Weekly · Monthly'],
    ['/live-rewards/campaigns', 'Campaigns'],
    ['/live-rewards/findings', 'Findings'],
    ['/settings/rewards', 'Settings'],
  ]
  return (
    <>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', margin: '8px 0 16px' }}>
        {items.map(([to, label]) => (
          <Link key={to} to={to} className="status-pill" style={{ textDecoration: 'none', fontSize: 12 }}>
            {label}
          </Link>
        ))}
      </div>
      <EngineBanner />
    </>
  )
}

/**
 * The one state that makes every other page look broken: campaigns active,
 * hosts counting, nothing paid — because the global switch is off (the
 * migration seeds it off). Shown on every Live Rewards page until it is on.
 */
export function EngineBanner() {
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [active, setActive] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    const r = await get<{ enabled: boolean; active_campaigns?: number }>('/v1/admin/rewards/health')
    if (r.ok) {
      setEnabled(!!r.data.enabled)
      setActive(r.data.active_campaigns || 0)
    }
  }, [])
  useEffect(() => {
    void load()
  }, [load])
  const turnOn = async () => {
    setBusy(true)
    const r = await put<{ enabled: boolean }>('/v1/admin/settings/rewards', { enabled: true })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    void load()
  }
  if (enabled !== false) return null
  return (
    <div className="form-error" style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', marginBottom: 16 }}>
      <div style={{ flex: 1 }}>
        <strong>Live rewards engine is OFF.</strong> {active ? `${active} campaign${active === 1 ? ' is' : 's are'} active and hosts are counting time, but nothing is being paid and no toast is sent.` : 'No campaign pays until it is on.'}{' '}
        When it is turned on, live time already counted in the current period is re-read, so levels crossed today are paid then — with their real completed-at time.
        {error ? <div style={{ marginTop: 4 }}>{error}</div> : null}
      </div>
      <Button size="sm" loading={busy} onClick={() => void turnOn()}>
        Turn on now
      </Button>
      <Link to="/settings/rewards" className="status-pill" style={{ textDecoration: 'none', fontSize: 12 }}>
        Settings
      </Link>
    </div>
  )
}

/**
 * Roster / search column: current level and rate, diamonds to the next level,
 * paid hours today — from the stored snapshot, one line per active campaign.
 */
function LevelCell({ levels }: { levels?: LevelBrief[] }) {
  if (!levels || levels.length === 0) return <span className="muted">—</span>
  return (
    <>
      {levels.map((b) => {
        const l = b.ladder
        return (
          <div key={b.campaign_id} style={{ marginBottom: 2 }}>
            {levels.length > 1 ? <span className="muted">{b.campaign} · </span> : null}
            {l ? (
              <>
                <strong>{l.unlocked ? l.tier_name : 'no level'}</strong>
                {l.unlocked ? ` ${l.rate.toLocaleString()}${l.milestone ? ' once' : '/h'}` : ''}
                {l.milestone ? null : l.cap_hours_today ? <span className="muted"> · {l.hours_paid_today}/{l.cap_hours_today} h today</span> : l.hours_paid_today ? <span className="muted"> · {l.hours_paid_today} h today</span> : null}
                {l.next_tier_name ? <div className="muted">{l.remaining_diamonds.toLocaleString()} diamonds → {l.next_tier_name}</div> : <div className="muted">{l.new_host ? 'new-signup window' : 'top level'}</div>}
              </>
            ) : (
              <>
                {b.eligible ? <span>next {b.next_amount.toLocaleString()} {b.next_asset || ''} in {fmtMs(b.remaining_ms || 0, { seconds: false })}</span> : <span style={{ color: '#991b1b' }}>{reasonText(b.reason) || 'not eligible'}</span>}
                {b.rewards_in_period ? <span className="muted"> · {b.rewards_in_period} paid</span> : null}
              </>
            )}
            {b.stale ? <span className="muted"> · refreshing</span> : null}
          </div>
        )
      })}
    </>
  )
}

function UserSearch() {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [hits, setHits] = useState<(Hit & { month?: { added: Record<string, number>; live_ms: number; count: number }; levels?: LevelBrief[] })[] | null>(null)
  const [busy, setBusy] = useState(false)
  const inflight = useRef<AbortController | null>(null)
  const search = async (e?: FormEvent) => {
    e?.preventDefault()
    const term = q.trim()
    if (!term) return
    inflight.current?.abort()
    const ac = new AbortController()
    inflight.current = ac
    setBusy(true)
    const r = await get<{ items: typeof hits }>(`/v1/admin/rewards/search?q=${encodeURIComponent(term)}`, { signal: ac.signal })
    setBusy(false)
    if (r.ok) setHits((r.data as unknown as { items?: typeof hits }).items ?? (r.data as unknown as typeof hits))
  }
  return (
    <Card title="Find a host">
      <form onSubmit={search} style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
        <Field label="Username, name, email, phone or id" value={q} onChange={(e) => setQ(e.target.value)} style={{ flex: 1 }} />
        <Button type="submit" size="sm" disabled={busy}>
          Search
        </Button>
      </form>
      {hits ? (
        <table className="table" style={{ marginTop: 12 }}>
          <thead>
            <tr>
              <th>Host</th>
              <th>This month</th>
              <th>Live time</th>
              <th>Level · next</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {hits.length === 0 ? (
              <tr>
                <td colSpan={5} className="muted">
                  No accounts match.
                </td>
              </tr>
            ) : (
              hits.map((h) => (
                <tr key={h.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/live-rewards/users/${h.id}`)}>
                  <td>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Avatar url={h.avatar_url} name={displayName(h)} />
                      <span>
                        <span style={{ fontWeight: 600 }}>{displayName(h)}</span>
                        <span className="muted" style={{ display: 'block', fontSize: 11 }}>
                          @{h.username} · #{h.id}
                        </span>
                      </span>
                    </span>
                  </td>
                  <td>{fmtAmounts(h.month?.added)}</td>
                  <td>{h.month ? fmtMs(h.month.live_ms, { seconds: false }) : '—'}</td>
                  <td style={{ fontSize: 12 }}>
                    <LevelCell levels={h.levels} />
                  </td>
                  <td className="muted">Open →</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      ) : null}
    </Card>
  )
}

// ─── Online today roster ──────────────────────────────────────────────────────

type RosterEntry = {
  user_id: string
  user?: Hit
  live_ms: number
  start?: string
  end?: string
  live: boolean
  role?: string
  mode?: string
  room_id: string
  rewards: { asset: string; amount: number; count: number; versions?: string[] }[]
  reward_last_at?: string
  gift_coins: number
  gift_count: number
  levels?: LevelBrief[]
}
type RosterResp = { day: string; calendar: CalendarInfo; entries: RosterEntry[]; hosts: number; live_now: number; total_ms: number }

/**
 * Who was live on a business day (default today) — closed and still live —
 * with their verified time, what they were paid and when, and gifts received.
 * Reads the source tables directly, so it is populated even when the daily
 * projection is behind.
 *
 * With `detail` (the Time & payouts page) a row click opens the host's day
 * inline — every session and every payment with its policy version — instead
 * of leaving the page. `day`/`onDayChange` make the picker URL-driven there.
 */
function OnlineRoster({ day: dayProp, onDayChange, detail }: { day?: string; onDayChange?: (d: string) => void; detail?: boolean } = {}) {
  const navigate = useNavigate()
  const [dayState, setDayState] = useState('')
  const day = onDayChange ? dayProp || '' : dayState
  const setDay = onDayChange || setDayState
  const [open, setOpen] = useState('')
  const [resp, setResp] = useState<RosterResp | null>(null)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    const q = day ? `?day=${day}` : ''
    const r = await get<RosterResp>(`/v1/admin/rewards/roster${q}`)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setResp(r.data)
  }, [day])
  useEffect(() => {
    void load()
    const t = window.setInterval(() => void load(), 15_000)
    return () => window.clearInterval(t)
  }, [load])

  const today = resp?.calendar?.today
  const isToday = !day || day === today
  return (
    <Card
      title={isToday ? 'Online today' : `Hosts on ${day}`}
      actions={
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <input type="date" value={day || today || ''} max={today} onChange={(e) => setDay(e.target.value)} />
          {!isToday ? (
            <Button size="sm" variant="ghost" onClick={() => setDay('')}>
              Today
            </Button>
          ) : null}
        </div>
      }
    >
      <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
        Every host who went live {isToday ? 'today' : `on ${day}`} — still live or ended — with their verified time, rewards paid (and when), and gifts received. Live time read from the ledger, so it shows
        even if the daily rollup is behind.
      </p>
      {error ? <div className="form-error">{error}</div> : null}
      {!resp ? (
        <PageLoader />
      ) : (
        <>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', fontSize: 12, marginBottom: 8 }}>
            <span className="muted">
              <strong>{resp.hosts}</strong> hosts
            </span>
            <span className="muted">
              <strong>{resp.live_now}</strong> live now
            </span>
            <span className="muted">
              total <strong>{fmtMs(resp.total_ms, { seconds: false })}</strong>
            </span>
          </div>
          {resp.entries.length === 0 ? (
            <p className="muted">Nobody went live {isToday ? 'today' : `on ${day}`}.</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Host</th>
                  <th>Live time</th>
                  <th>When</th>
                  <th>Reward got</th>
                  <th>Added at</th>
                  <th>Level · next</th>
                  <th>Gifts received</th>
                </tr>
              </thead>
              <tbody>
                {resp.entries.map((e) => (
                  <Fragment key={e.user_id}>
                  <tr
                    className="row-link"
                    style={{ cursor: 'pointer' }}
                    onClick={() => (detail ? setOpen(open === e.user_id ? '' : e.user_id) : navigate(`/live-rewards/users/${e.user_id}`))}
                  >
                    <td>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Avatar url={e.user?.avatar_url} name={e.user ? displayName(e.user) : e.user_id} />
                        <span>
                          <strong>{e.user ? displayName(e.user) : `#${e.user_id}`}</strong>
                          <span className="muted" style={{ display: 'block', fontSize: 11 }}>
                            {e.user ? `@${e.user.username} · ` : ''}#{e.user_id}
                          </span>
                        </span>
                      </span>
                    </td>
                    <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>
                      {fmtMs(e.live_ms)}
                      {e.live ? (
                        <>
                          {' '}
                          <StatusPill status="added" label={`live${e.role ? ' · ' + e.role : ''}${e.mode ? ' · ' + e.mode : ''}`} />
                        </>
                      ) : null}
                    </td>
                    <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                      {e.start ? fmtClock(e.start) : '—'}
                      {!e.live && e.end ? ` → ${fmtClock(e.end)}` : e.live ? ' → now' : ''}
                    </td>
                    <td style={{ fontSize: 12 }}>
                      {e.rewards.length === 0 ? (
                        <span className="muted">—</span>
                      ) : (
                        e.rewards.map((r) => (
                          <div key={r.asset}>
                            <strong>{r.amount.toLocaleString()}</strong> {r.asset}
                            <span className="muted">
                              {' '}
                              · {r.count} reward{r.count > 1 ? 's' : ''}
                              {r.versions?.length ? ` · ${r.versions.join(' ')}` : ''}
                            </span>
                          </div>
                        ))
                      )}
                    </td>
                    <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>{e.reward_last_at ? fmtClock(e.reward_last_at) : '—'}</td>
                    <td style={{ fontSize: 12 }}>
                      <LevelCell levels={e.levels} />
                    </td>
                    <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                      {e.gift_count ? (
                        <>
                          <strong>{e.gift_coins.toLocaleString()}</strong> coins <span className="muted">· {e.gift_count} gifts</span>
                        </>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                  </tr>
                  {detail && open === e.user_id && resp ? (
                    <tr>
                      <td colSpan={7} style={{ background: 'var(--surface-2, rgba(0,0,0,0.02))' }}>
                        <RosterDayDetail uid={e.user_id} day={resp.day} />
                      </td>
                    </tr>
                  ) : null}
                  </Fragment>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </Card>
  )
}

// ─── Day detail: one host's sessions + payments for a business day ───────────

type DaySession = {
  room_id: string
  user_id: string
  role: string
  kind?: string
  started_at?: string
  ended_at?: string
  verified_ms: number
  paused_ms: number
  segments: number
  end_reason: string
  live: boolean
  counting?: boolean
}

/**
 * The proof behind one roster row: every session the host ran that day
 * (start → end, verified time) and every reward added (exact time, amount,
 * the live time it required, and the policy version that paid it).
 */
function RosterDayDetail({ uid, day }: { uid: string; day: string }) {
  const [sessions, setSessions] = useState<DaySession[] | null>(null)
  const [sessErr, setSessErr] = useState('')
  const [payouts, setPayouts] = useState<LedgerView[] | null>(null)
  const [payErr, setPayErr] = useState('')

  useEffect(() => {
    let on = true
    void (async () => {
      const [s, p] = await Promise.all([
        get<{ sessions: DaySession[] }>(`/v1/admin/live-time/users/${uid}/sessions?from=${day}&to=${day}`),
        get<PayoutsResp>(`/v1/admin/rewards/payouts?user_id=${uid}&from=${day}&to=${day}&limit=50`),
      ])
      if (!on) return
      if (s.ok) setSessions(s.data.sessions || [])
      else setSessErr(s.message)
      if (p.ok) setPayouts(p.data.items || [])
      else setPayErr(p.message)
    })()
    return () => {
      on = false
    }
  }, [uid, day])

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16, padding: '8px 4px' }}>
      <div>
        <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 4 }}>Sessions · {day}</div>
        {sessErr ? (
          <p className="muted" style={{ fontSize: 12 }}>
            {sessErr}
          </p>
        ) : !sessions ? (
          <PageLoader />
        ) : sessions.length === 0 ? (
          <p className="muted" style={{ fontSize: 12 }}>
            No closed sessions recorded for this day.
          </p>
        ) : (
          <table className="table" style={{ fontSize: 12 }}>
            <thead>
              <tr>
                <th>Started</th>
                <th>Ended</th>
                <th>Role</th>
                <th>Verified</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((s) => (
                <tr key={`${s.room_id}:${s.started_at || ''}`}>
                  <td style={{ whiteSpace: 'nowrap' }}>{fmtClock(s.started_at)}</td>
                  <td style={{ whiteSpace: 'nowrap' }}>{s.live ? <StatusPill status="added" label="live now" /> : fmtClock(s.ended_at)}</td>
                  <td>
                    {s.role}
                    {s.kind ? <span className="muted"> · {s.kind}</span> : null}
                  </td>
                  <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>{fmtMs(s.verified_ms)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <div>
        <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 4 }}>Rewards · {day}</div>
        {payErr ? (
          <p className="muted" style={{ fontSize: 12 }}>
            {payErr}
          </p>
        ) : !payouts ? (
          <PageLoader />
        ) : payouts.length === 0 ? (
          <p className="muted" style={{ fontSize: 12 }}>
            Nothing paid this day.
          </p>
        ) : (
          <table className="table" style={{ fontSize: 12 }}>
            <thead>
              <tr>
                <th>Added at</th>
                <th>Reward</th>
                <th>For time</th>
                <th>Version</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {payouts.map((r) => (
                <tr key={r.id}>
                  <td style={{ whiteSpace: 'nowrap' }}>{fmtClock(r.issued_at || r.earned_at)}</td>
                  <td style={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                    {r.kind === 'reversal' ? '' : '+'}
                    {r.amount.toLocaleString()} {r.asset}
                  </td>
                  <td style={{ fontVariantNumeric: 'tabular-nums' }}>{fmtMs(r.required_ms)}</td>
                  <td>
                    <Link to={`/live-rewards/payouts/${r.id}`}>v{r.version_no}</Link>
                  </td>
                  <td className="muted">{r.status_label}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <div style={{ marginTop: 6, display: 'flex', gap: 12, fontSize: 12 }}>
          <Link to={`/live-rewards/users/${uid}`}>Full reward profile →</Link>
          <Link to={`/live-time/users/${uid}`}>Live time profile →</Link>
        </div>
      </div>
    </div>
  )
}

// ─── Host lookup: one host's time + payouts by day / week / month ────────────

const RANGE_LABEL: Record<string, string> = { day: 'Today', week: 'This week', month: 'This month' }

/**
 * Find one host by username / email / phone / id and read their verified live
 * time and rewards added, broken down by business day inside the day / week /
 * month window — plus every payout in that window. Uses the same figures as
 * the host's full profile, focused on time + payments.
 */
function HostTimeAndPayouts() {
  const [q, setQ] = useState('')
  const [hits, setHits] = useState<Hit[] | null>(null)
  const [searching, setSearching] = useState(false)
  const [sel, setSel] = useState<Hit | null>(null)
  const [range, setRange] = useState<'day' | 'week' | 'month'>('month')
  const [summary, setSummary] = useState<Summary | null>(null)
  const [payouts, setPayouts] = useState<LedgerView[] | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const searchRef = useRef<AbortController | null>(null)

  // Debounced account search — only while nobody is selected.
  useEffect(() => {
    if (sel) return
    const term = q.trim()
    if (!term) {
      setHits(null)
      return
    }
    const id = window.setTimeout(() => {
      void (async () => {
        searchRef.current?.abort()
        const ac = new AbortController()
        searchRef.current = ac
        setSearching(true)
        const r = await get<{ items: Hit[] }>(`/v1/admin/rewards/search?q=${encodeURIComponent(term)}`, { signal: ac.signal })
        if (ac.signal.aborted) return
        setSearching(false)
        if (r.ok) setHits((r.data as unknown as { items?: Hit[] }).items ?? (r.data as unknown as Hit[]) ?? [])
        else setError(r.message)
      })()
    }, 250)
    return () => window.clearTimeout(id)
  }, [q, sel])

  // Selected host: per-day summary for the window + its payouts.
  const loadHost = useCallback(async (uid: string, rng: string) => {
    setLoading(true)
    setError('')
    const u = await get<UserResp>(`/v1/admin/rewards/users/${uid}?range=${rng}`)
    if (!u.ok) {
      setLoading(false)
      setError(u.message)
      return
    }
    const sum = u.data.summary
    setSummary(sum)
    const p = new URLSearchParams({ user_id: uid, limit: '200' })
    if (sum?.from) p.set('from', sum.from)
    if (sum?.to) p.set('to', sum.to)
    const pr = await get<PayoutsResp>(`/v1/admin/rewards/payouts?${p}`)
    setLoading(false)
    setPayouts(pr.ok ? pr.data.items || [] : [])
  }, [])

  useEffect(() => {
    if (sel) void loadHost(sel.id, range)
  }, [sel, range, loadHost])

  const pick = (h: Hit) => {
    setSel(h)
    setHits(null)
  }
  const clear = () => {
    setSel(null)
    setSummary(null)
    setPayouts(null)
    setHits(null)
    setQ('')
  }

  return (
    <Card title="Find a host · time & payouts">
      <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
        Search a host by username, email, phone or id to see their verified live time and rewards added — by day inside the day,
        week or month window — and every payout in it.
      </p>
      {!sel ? (
        <>
          <Field
            label="Username, name, email, phone or id"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="e.g. @host, host@mail.com, +15551234567"
          />
          {error ? <div className="form-error" style={{ marginTop: 8 }}>{error}</div> : null}
          {searching ? <PageLoader label="Searching…" /> : null}
          {hits && !searching ? (
            hits.length === 0 ? (
              <p className="muted" style={{ margin: '10px 0 0' }}>
                No account matches “{q.trim()}”.
              </p>
            ) : (
              <table className="table" style={{ marginTop: 10 }}>
                <tbody>
                  {hits.map((h) => (
                    <tr key={h.id} className="row-link" style={{ cursor: 'pointer' }} onClick={() => pick(h)}>
                      <td>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                          <Avatar url={h.avatar_url} name={displayName(h)} />
                          <span>
                            <strong>{displayName(h)}</strong>
                            <span className="muted" style={{ display: 'block', fontSize: 11 }}>
                              @{h.username} · #{h.id}
                              {h.email ? ` · ${h.email}` : h.phone ? ` · ${h.phone}` : ''}
                            </span>
                          </span>
                        </span>
                      </td>
                      <td className="muted">Open →</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )
          ) : null}
        </>
      ) : (
        <>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            <Avatar url={sel.avatar_url} name={displayName(sel)} size={36} />
            <div style={{ flex: 1, minWidth: 180 }}>
              <strong>{displayName(sel)}</strong>
              <div className="muted" style={{ fontSize: 11 }}>
                @{sel.username} · #{sel.id}
                {sel.email ? ` · ${sel.email}` : sel.phone ? ` · ${sel.phone}` : ''}
              </div>
            </div>
            <div style={{ display: 'flex', gap: 6 }}>
              {(['day', 'week', 'month'] as const).map((b) => (
                <Button key={b} size="sm" variant={range === b ? 'primary' : 'ghost'} onClick={() => setRange(b)}>
                  {b === 'day' ? 'Daily' : b === 'week' ? 'Weekly' : 'Monthly'}
                </Button>
              ))}
            </div>
            <Button size="sm" variant="secondary" onClick={clear}>
              Change host
            </Button>
            <Link to={`/live-rewards/users/${sel.id}`} className="muted" style={{ fontSize: 12 }}>
              Full profile →
            </Link>
          </div>

          {error ? <div className="form-error" style={{ marginTop: 10 }}>{error}</div> : null}
          {loading && !summary ? (
            <PageLoader />
          ) : summary ? (
            <>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10, margin: '12px 0' }}>
                <Stat label={`Live time · ${RANGE_LABEL[range]}`} value={fmtMs(summary.live_ms || 0, { seconds: false })} sub={`${summary.from || '—'} → ${summary.to || '—'}`} />
                <Stat label={`Rewards added · ${RANGE_LABEL[range]}`} value={fmtAmounts(summary.added)} sub={`${summary.count || 0} reward${summary.count === 1 ? '' : 's'}`} />
                <Stat label="Pending" value={fmtAmounts(summary.pending)} sub="earned, not yet in wallet" />
                <Stat label="Claimable" value={fmtAmounts(summary.claimable)} />
              </div>

              <div style={{ fontSize: 13, fontWeight: 700, margin: '4px 0 6px' }}>By business day</div>
              <table className="table">
                <thead>
                  <tr>
                    <th>Day</th>
                    <th>Live time</th>
                    <th>Rewards added</th>
                    <th>Count</th>
                  </tr>
                </thead>
                <tbody>
                  {(summary.days || []).length === 0 ? (
                    <tr>
                      <td colSpan={4} className="muted">
                        No live time or rewards in this {range}.
                      </td>
                    </tr>
                  ) : (
                    summary.days.map((d) => (
                      <tr key={d.day}>
                        <td>
                          <div style={{ fontWeight: 600 }}>{d.label || d.day}</div>
                          <div className="muted" style={{ fontSize: 11 }}>{d.day}</div>
                        </td>
                        <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>{fmtMs(d.live_ms || 0, { seconds: false })}</td>
                        <td>{fmtAmounts(d.added)}</td>
                        <td>{d.count || 0}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>

              <div style={{ fontSize: 13, fontWeight: 700, margin: '14px 0 6px' }}>Payouts · {RANGE_LABEL[range]}</div>
              <table className="table">
                <thead>
                  <tr>
                    <th>Day · completed</th>
                    <th>Reward</th>
                    <th>Required</th>
                    <th>Added · balance</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {!payouts ? (
                    <tr>
                      <td colSpan={6} className="muted">
                        Loading…
                      </td>
                    </tr>
                  ) : payouts.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="muted">
                        Nothing paid in this {range}.
                      </td>
                    </tr>
                  ) : (
                    payouts.map((r) => <RewardRow key={r.id} r={r} to={`/live-rewards/payouts/${r.id}`} />)
                  )}
                </tbody>
              </table>
            </>
          ) : null}
        </>
      )}
    </Card>
  )
}

// ─── Time & payouts page ─────────────────────────────────────────────────────

/**
 * Every host of a business day in one place — live now on top, then the most
 * recent — paid or not, with time, rewards (count + policy version), gifts,
 * and a click-open of each host's sessions and payments. The Daily stats
 * rows link here, so a day's total can always be broken down to people.
 */
function TimeAndPayouts() {
  const [params, setParams] = useSearchParams()
  const day = params.get('day') || ''
  return (
    <div>
      <div className="page-head">
        <h1>Time &amp; payouts</h1>
        <p>
          Who was live on a day — still live or ended, paid or not — with their verified time, every reward (and the policy version that
          paid it), and gifts received. Click a host to see their sessions and each payment.
        </p>
      </div>
      <SectionNav />
      <HostTimeAndPayouts />
      <div style={{ marginTop: 16 }} />
      <OnlineRoster
        day={day}
        onDayChange={(d) => {
          const next = new URLSearchParams(params)
          if (d) next.set('day', d)
          else next.delete('day')
          setParams(next)
        }}
        detail
      />
    </div>
  )
}

// ─── Health (home) ───────────────────────────────────────────────────────────

function RewardsHome() {
  const [h, setH] = useState<Health | null>(null)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    const r = await get<Health>('/v1/admin/rewards/health')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setH(r.data)
  }, [])
  useEffect(() => {
    void load()
    const id = window.setInterval(() => void load(), 10_000)
    return () => window.clearInterval(id)
  }, [load])

  return (
    <div>
      <div className="page-head">
        <h1>Live rewards</h1>
        <p>Payouts for verified live time. Every reward is paid at the minute it is earned, with the proof beside it.</p>
      </div>
      <SectionNav />
      {error ? <div className="form-error">{error}</div> : null}
      {!h ? (
        <PageLoader />
      ) : (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10 }}>
            <Stat
              label="Status"
              value={h.enabled ? `${h.status} · ${h.score}` : 'Disabled'}
              sub={h.enabled ? 'health score /100' : 'Turn on in Settings → Live rewards'}
              tone={h.enabled && h.score < 60 ? 'warn' : h.enabled ? 'live' : undefined}
            />
            <Stat label="Issued 24h" value={String(h.counts_24h.Issued ?? 0)} sub={`${h.counts_24h.Claimable ?? 0} claimable · ${h.counts_24h.Claimed ?? 0} claimed`} />
            <Stat label="Pending now" value={String((h.counts_24h.Reached ?? 0) + (h.counts_24h.Blocked ?? 0) + (h.counts_24h.Eligible ?? 0))} sub={`${h.counts_24h.Blocked ?? 0} blocked by limits/conditions`} />
            <Stat
              label="Worst delay"
              value={fmtMs(h.process.worst_issue_delay_ms ?? 0)}
              sub={`${h.process.late_issues ?? 0} over 5s · target ≤ 60s`}
              tone={(h.process.worst_issue_delay_ms ?? 0) > 60_000 ? 'warn' : undefined}
            />
            <Stat label="Open findings" value={String(Object.values(h.open_findings || {}).reduce((a, b) => a + b, 0))} tone={Object.keys(h.open_findings || {}).length ? 'warn' : undefined} />
            <Stat label="Queues" value={`${h.dirty_backlog} · ${h.repair_backlog} · ${h.stuck_outbox}`} sub="dirty · repair · stuck outbox" />
            <Stat label="Event lag" value={fmtMs(h.event_lag_ms)} sub={`${h.scheduler_size} scheduled wakes`} />
            <Stat label="Campaigns" value={String(h.active_campaigns)} sub={`up ${h.uptime}`} />
          </div>
          <div style={{ marginTop: 8 }}>
            <CalendarLine cal={h.calendar} />
          </div>
          <div style={{ marginTop: 16 }}>
            <OnlineRoster />
          </div>
          <div style={{ marginTop: 16 }}>
            <UserSearch />
          </div>
          <Card title="Process counters">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 6, fontSize: 12 }}>
              {Object.entries(h.process).map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                  <span className="muted">{k}</span>
                  <span style={{ fontVariantNumeric: 'tabular-nums' }}>{v.toLocaleString()}</span>
                </div>
              ))}
            </div>
          </Card>
        </>
      )}
    </div>
  )
}

// ─── Payouts ─────────────────────────────────────────────────────────────────

function RewardsPayouts() {
  const [params, setParams] = useSearchParams()
  const [resp, setResp] = useState<PayoutsResp | null>(null)
  const [more, setMore] = useState<LedgerView[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const from = params.get('from') || ''
  const to = params.get('to') || ''
  const status = params.get('status') || ''
  const asset = params.get('asset') || ''
  const kind = params.get('kind') || ''
  const campaign = params.get('campaign_id') || ''
  const userID = params.get('user_id') || ''

  const query = useCallback(
    (cursor?: string) => {
      const p = new URLSearchParams()
      if (from) p.set('from', from)
      if (to) p.set('to', to)
      if (status) p.set('status', status)
      if (asset) p.set('asset', asset)
      if (kind) p.set('kind', kind)
      if (campaign) p.set('campaign_id', campaign)
      if (userID) p.set('user_id', userID)
      if (cursor) p.set('cursor', cursor)
      p.set('limit', '100')
      return p
    },
    [from, to, status, asset, kind, campaign, userID],
  )

  useEffect(() => {
    let alive = true
    setBusy(true)
    void (async () => {
      const r = await get<PayoutsResp>(`/v1/admin/rewards/payouts?${query()}`)
      if (!alive) return
      setBusy(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setResp(r.data)
      setMore([])
    })()
    return () => {
      alive = false
    }
  }, [query])

  const loadMore = async () => {
    if (!resp?.cursor) return
    const last = more.length ? more[more.length - 1] : resp.items[resp.items.length - 1]
    const cursor = `${new Date(last.earned_at).toISOString()}|${last.id}`
    const r = await get<PayoutsResp>(`/v1/admin/rewards/payouts?${query(cursor)}`)
    if (r.ok) {
      setMore((m) => [...m, ...r.data.items])
      setResp((p) => (p ? { ...p, cursor: r.data.cursor, people: { ...p.people, ...r.data.people } } : p))
    }
  }

  const setFilter = (k: string, v: string) => {
    const next = new URLSearchParams(params)
    if (v) next.set(k, v)
    else next.delete(k)
    setParams(next)
  }
  const items = useMemo(() => [...(resp?.items || []), ...more], [resp, more])

  return (
    <div>
      <div className="page-head">
        <h1>Payouts</h1>
        <p>Every reward in the ledger: when the time was completed, when it was added, and the balance before and after.</p>
      </div>
      <SectionNav />
      <Card>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <RangeBar from={from || resp?.calendar.today || ''} to={to || resp?.calendar.today || ''} today={resp?.calendar.today} />
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span className="muted" style={{ fontSize: 11 }}>
              Status
            </span>
            <select className="admin-context-select" value={status} onChange={(e) => setFilter('status', e.target.value)}>
              <option value="">All</option>
              {['issued', 'claimable', 'claimed', 'earned', 'expired', 'cancelled'].map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span className="muted" style={{ fontSize: 11 }}>
              Asset
            </span>
            <select className="admin-context-select" value={asset} onChange={(e) => setFilter('asset', e.target.value)}>
              <option value="">All</option>
              {['coins', 'diamonds', 'beans'].map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span className="muted" style={{ fontSize: 11 }}>
              Kind
            </span>
            <select className="admin-context-select" value={kind} onChange={(e) => setFilter('kind', e.target.value)}>
              <option value="">All</option>
              <option value="earn">Earned</option>
              <option value="manual">Manual</option>
              <option value="reversal">Reversal</option>
            </select>
          </label>
          <Field label="User id" value={userID} onChange={(e) => setFilter('user_id', e.target.value.trim())} style={{ width: 160 }} />
        </div>
        <div style={{ marginTop: 8 }}>
          <CalendarLine cal={resp?.calendar} />
        </div>
      </Card>
      {error ? <div className="form-error">{error}</div> : null}
      {resp?.totals?.length ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 10, margin: '12px 0' }}>
          {resp.totals.map((t) => (
            <Stat key={t.Asset} label={`${t.Asset} in range`} value={`${t.Issued.toLocaleString()} added`} sub={`${t.Earned.toLocaleString()} earned · ${t.Claimed.toLocaleString()} claimed · ${t.Reversed.toLocaleString()} reversed · ${t.Count} rewards`} />
          ))}
        </div>
      ) : null}
      <Card>
        {busy && !resp ? (
          <PageLoader />
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Day · completed</th>
                <th>Host</th>
                <th>Reward</th>
                <th>Required</th>
                <th>Added · balance</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.length === 0 ? (
                <tr>
                  <td colSpan={7} className="muted">
                    No rewards in this range.
                  </td>
                </tr>
              ) : (
                items.map((r) => <RewardRow key={r.id} r={r} person={resp?.people[r.user_id]} showUser to={`/live-rewards/payouts/${r.id}`} />)
              )}
            </tbody>
          </table>
        )}
        {resp?.cursor ? (
          <div style={{ marginTop: 12 }}>
            <Button size="sm" variant="secondary" onClick={() => void loadMore()}>
              Load more
            </Button>
          </div>
        ) : null}
      </Card>
    </div>
  )
}

// ─── Daily / Weekly / Monthly ────────────────────────────────────────────────

function RewardsStats() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const bucket = params.get('bucket') || 'day'
  const from = params.get('from') || ''
  const to = params.get('to') || ''
  const campaign = params.get('campaign_id') || ''
  const [resp, setResp] = useState<{ buckets: Bucket[]; calendar: CalendarInfo } | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    void (async () => {
      const p = new URLSearchParams({ bucket })
      if (from) p.set('from', from)
      if (to) p.set('to', to)
      if (campaign) p.set('campaign_id', campaign)
      const r = await get<{ buckets: Bucket[]; calendar: CalendarInfo }>(`/v1/admin/rewards/stats?${p}`)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setResp(r.data)
    })()
  }, [bucket, from, to, campaign])
  const setBucket = (b: string) => {
    const next = new URLSearchParams(params)
    next.set('bucket', b)
    setParams(next)
  }
  return (
    <div>
      <div className="page-head">
        <h1>Rewards by day, week and month</h1>
        <p>Totals per business day in the Live Time calendar, so each figure lines up with the Live time pages.</p>
      </div>
      <SectionNav />
      <Card>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <div style={{ display: 'flex', gap: 6 }}>
            {(['day', 'week', 'month'] as const).map((b) => (
              <Button key={b} size="sm" variant={bucket === b ? 'primary' : 'ghost'} onClick={() => setBucket(b)}>
                {b === 'day' ? 'Daily' : b === 'week' ? 'Weekly' : 'Monthly'}
              </Button>
            ))}
          </div>
          <RangeBar from={from || resp?.calendar.today || ''} to={to || resp?.calendar.today || ''} today={resp?.calendar.today} />
        </div>
        <div style={{ marginTop: 8 }}>
          <CalendarLine cal={resp?.calendar} />
        </div>
      </Card>
      {error ? <div className="form-error">{error}</div> : null}
      <Card>
        {!resp ? (
          <PageLoader />
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>{bucket === 'day' ? 'Day' : bucket === 'week' ? 'Week' : 'Month'}</th>
                <th>Added</th>
                <th>Earned</th>
                <th>Claimed</th>
                <th>Reversed</th>
                <th>Rewards</th>
                <th>Reached · blocked</th>
              </tr>
            </thead>
            <tbody>
              {resp.buckets.length === 0 ? (
                <tr>
                  <td colSpan={7} className="muted">
                    Nothing paid in this range.
                  </td>
                </tr>
              ) : (
                resp.buckets.map((b) => (
                  <tr
                    key={b.key}
                    className={bucket === 'day' ? 'row-link' : undefined}
                    style={bucket === 'day' ? { cursor: 'pointer' } : undefined}
                    title={bucket === 'day' ? 'See every host of this day: time, rewards, gifts' : undefined}
                    onClick={bucket === 'day' ? () => navigate(`/live-rewards/time?day=${b.key}`) : undefined}
                  >
                    <td>
                      <div style={{ fontWeight: 600 }}>{b.label}</div>
                      <div className="muted" style={{ fontSize: 11 }}>
                        {b.key}
                      </div>
                    </td>
                    <td style={{ fontWeight: 600 }}>{fmtAmounts(b.issued)}</td>
                    <td>{fmtAmounts(b.earned)}</td>
                    <td>{fmtAmounts(b.claimed)}</td>
                    <td>{fmtAmounts(b.reversed)}</td>
                    <td>{b.count}</td>
                    <td className="muted">
                      {b.thresholds_reached} · {b.blocked}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}

/**
 * One line per day: "2 added (1 × 1,000 + 1 × 3,000 coins) · 1 pending".
 * Groups the day's rows by status and rate so a level change mid-day reads
 * as two rates, not a mystery total.
 */
function dayRollup(rows: LedgerView[]): string {
  const earn = rows.filter((r) => r.kind === 'earn')
  if (!earn.length) return ''
  const by = (status: string) => earn.filter((r) => r.status === status)
  const rates = (xs: LedgerView[]) => {
    const m = new Map<string, number>()
    for (const r of xs) m.set(`${r.amount}|${r.asset}`, (m.get(`${r.amount}|${r.asset}`) || 0) + 1)
    return Array.from(m.entries())
      .map(([k, n]) => {
        const [amount, asset] = k.split('|')
        return `${n} × ${Number(amount).toLocaleString()} ${asset}`
      })
      .join(' + ')
  }
  const parts: string[] = []
  const added = [...by('added'), ...by('claimed')]
  if (added.length) parts.push(`${added.length} added (${rates(added)})`)
  const claimable = by('claimable')
  if (claimable.length) parts.push(`${claimable.length} waiting to claim (${rates(claimable)})`)
  const pending = by('pending')
  if (pending.length) parts.push(`${pending.length} pending`)
  const expired = [...by('expired'), ...by('cancelled')]
  if (expired.length) parts.push(`${expired.length} expired / cancelled`)
  return parts.join(' · ')
}

// ─── Gift-level ladder ───────────────────────────────────────────────────────

/** A thin progress bar; value/max in any unit. */
function Bar({ value, max, color }: { value: number; max: number; color?: string }) {
  const pct = max > 0 ? Math.max(0, Math.min(100, (value / max) * 100)) : 0
  return (
    <div style={{ height: 6, borderRadius: 3, background: 'var(--wash-deep, #e5e7eb)', overflow: 'hidden', marginTop: 4 }}>
      <div style={{ width: `${pct}%`, height: '100%', background: color || 'var(--accent, #2563eb)', transition: 'width .3s' }} />
    </div>
  )
}

/**
 * The host's gift level, in the words a manager asks for: current level
 * and rate, diamonds received this period and how many more unlock the next
 * level, paid hours today against the level's cap, and today's total.
 */
function LadderPanel({ ladder }: { ladder: LadderStatus }) {
  const l = ladder
  const nextMin = l.next_tier_min || 0
  const prevMin = l.tiers.filter((t) => t.kind === 'weekly' && t.min_received <= l.received_diamonds).reduce((m, t) => Math.max(m, t.min_received), 0)
  const span = nextMin > prevMin ? nextMin - prevMin : 0
  return (
    <div style={{ marginTop: 8, padding: 10, borderRadius: 8, background: 'var(--wash, #f8fafc)', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12, fontSize: 12 }}>
      <div>
        <div className="muted">Current level</div>
        <div style={{ fontSize: 15, fontWeight: 600 }}>
          {l.unlocked ? l.tier_name : 'None yet'}
          {l.new_host ? <span className="status-pill" style={{ marginLeft: 6, fontSize: 10, background: '#dbeafe', color: '#1e3a8a' }}>new host{l.new_host_days_left != null ? ` · ${l.new_host_days_left}d left` : ''}</span> : null}
          {l.weekly_on === false ? <span className="status-pill" style={{ marginLeft: 6, fontSize: 10, background: '#fee2e2', color: '#991b1b' }}>weekly off</span> : null}
          {l.new_host_on === false ? <span className="status-pill" style={{ marginLeft: 6, fontSize: 10, background: '#fee2e2', color: '#991b1b' }}>new-signup off</span> : null}
        </div>
        <div>
          {l.unlocked ? (
            <>pays <strong>{l.rate.toLocaleString()} {l.asset}</strong> {l.milestone ? 'once this period, when the level time and its conditions are met' : 'per paid hour'}</>
          ) : (
            <span style={{ color: '#991b1b' }}>{l.milestone ? 'nothing is paid until the first level’s diamonds are received' : 'no paid hours until the first level’s diamonds are received'}</span>
          )}
        </div>
      </div>
      <div>
        <div className="muted">Diamonds received this period</div>
        <div style={{ fontSize: 15, fontWeight: 600 }}>{l.received_diamonds.toLocaleString()}</div>
        {l.next_tier_name ? (
          <>
            <div>
              <strong>{l.remaining_diamonds.toLocaleString()}</strong> more → {l.next_tier_name} ({(l.next_tier_rate || 0).toLocaleString()}{l.milestone ? '' : '/h'})
            </div>
            <Bar value={l.received_diamonds - prevMin} max={span} />
          </>
        ) : l.new_host ? (
          <div className="muted">new-signup window — the weekly levels start the day it ends</div>
        ) : (
          <div className="muted">top level reached</div>
        )}
      </div>
      {l.milestone ? null : (
        <div>
          <div className="muted">Paid hours today</div>
          <div style={{ fontSize: 15, fontWeight: 600 }}>
            {l.hours_paid_today}
            {l.cap_hours_today ? ` / ${l.cap_hours_today}` : ''}
          </div>
          {l.cap_hours_today ? <Bar value={l.hours_paid_today} max={l.cap_hours_today} color={l.hours_paid_today >= l.cap_hours_today ? '#92400e' : undefined} /> : <div className="muted">no daily cap</div>}
          {l.cap_hours_today && l.hours_paid_today >= l.cap_hours_today ? <div style={{ color: '#92400e' }}>cap reached — more hours pay again tomorrow</div> : null}
        </div>
      )}
      <div>
        <div className="muted">Today’s total from this program</div>
        <div style={{ fontSize: 15, fontWeight: 600 }}>
          {l.today_total.toLocaleString()} {l.asset || ''}
        </div>
        <div className="muted">
          {l.milestone ? `${l.hours_paid_today} reward${l.hours_paid_today === 1 ? '' : 's'} today` : `${l.hours_paid_today} paid hour${l.hours_paid_today === 1 ? '' : 's'}`} · added to wallet
        </div>
      </div>
      <div style={{ gridColumn: '1 / -1', display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {l.tiers.map((t) => (
          <span
            key={t.key}
            className="status-pill"
            title={t.kind === 'new_host' ? `accounts up to ${t.new_host_days} days old` : `from ${t.min_received.toLocaleString()} diamonds received`}
            style={{
              fontSize: 11,
              background: t.current ? 'var(--accent-soft)' : t.reached ? '#fef3c7' : 'var(--wash-deep)',
              color: t.current ? 'var(--accent-hover)' : t.reached ? '#92400e' : 'var(--ink-soft)',
              outline: t.current ? '1px solid var(--accent-hover)' : undefined,
            }}
          >
            {t.name} · {t.amount.toLocaleString()}{l.milestone ? '' : '/h'}{t.max_hours_per_day ? ` · max ${t.max_hours_per_day}/day` : ''}
            {t.kind === 'new_host' ? ` · ≤${t.new_host_days}d` : ` · ≥${t.min_received.toLocaleString()}`}
            {t.current ? ' ← now' : ''}
          </span>
        ))}
      </div>
    </div>
  )
}

/** Open checks for one host: anything the auditor flagged as wrong, missing or late. */
function ChecksCard({ checks }: { checks: Finding[] }) {
  const open = checks.filter((f) => !f.resolved_at)
  return (
    <Card title={`Checks · ${open.length ? `${open.length} open` : 'all clear'}`}>
      <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
        The auditor replays every paid hour against the rules that produced it (level for the recorded diamonds, amount, week, daily cap, wallet entry) and re-derives missing hours. Anything that does not match shows here, on the
        host’s own page, until it is repaired or resolved.
      </p>
      {!open.length ? (
        <p className="muted">No open checks for this host.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>What</th>
              <th>Where</th>
              <th>Detail</th>
              <th>Found</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {open.map((f) => (
              <tr key={f.id}>
                <td style={{ fontSize: 12 }}>
                  <StatusPill status={f.severity >= 4 ? 'expired' : 'pending'} label={f.kind} />
                  <div>{findingText(f.kind)}</div>
                </td>
                <td style={{ fontSize: 12 }}>
                  {f.period_key}
                  {f.threshold_ms ? ` · ${fmtMs(f.threshold_ms, { seconds: false })}` : ''}
                </td>
                <td style={{ fontSize: 11 }}>
                  <code>{JSON.stringify(f.detail)}</code>
                </td>
                <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                  {fmtClock(f.found_at)}
                  {f.repaired_at ? <div className="muted">repaired {fmtClock(f.repaired_at)}</div> : f.repair_attempts ? <div className="muted">{f.repair_attempts} repair attempt{f.repair_attempts === 1 ? '' : 's'}</div> : null}
                </td>
                <td style={{ fontSize: 12 }}>
                  {f.ledger_id ? <Link to={`/live-rewards/payouts/${f.ledger_id}`}>payout →</Link> : null}
                  <div>
                    <Link to={`/live-rewards/findings?user_id=${f.user_id}`}>all checks →</Link>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  )
}

// ─── User debugger ───────────────────────────────────────────────────────────

function RewardsUser() {
  const { id = '' } = useParams()
  const [params] = useSearchParams()
  const range = params.get('range') || 'month'
  const [resp, setResp] = useState<UserResp | null>(null)
  const [history, setHistory] = useState<{ days: DayGroup[]; cursor: string } | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')

  const load = useCallback(async () => {
    const p = new URLSearchParams({ range })
    const [u, h] = await Promise.all([
      get<UserResp>(`/v1/admin/rewards/users/${id}?${p}`),
      get<{ days: DayGroup[]; cursor: string }>(`/v1/admin/rewards/users/${id}/history?limit=100`),
    ])
    if (!u.ok) {
      setError(u.message)
      return
    }
    setError('')
    setResp(u.data)
    if (h.ok) setHistory(h.data)
  }, [id, range])
  useEffect(() => {
    void load()
    const t = window.setInterval(() => void load(), 15_000)
    return () => window.clearInterval(t)
  }, [load])

  const reevaluate = async () => {
    setBusy(true)
    const r = await post<{ issued: LedgerView[]; blocked: number; reached: number }>(`/v1/admin/rewards/users/${id}/reevaluate`, {})
    setBusy(false)
    setMsg(r.ok ? `${r.message} ${r.data.issued.length} issued · ${r.data.reached} reached · ${r.data.blocked} blocked.` : r.message)
    void load()
  }

  if (error) return <div className="form-error">{error}</div>
  if (!resp) return <PageLoader />
  const ex = resp.explain || ({} as Explain)
  const u = resp.user
  const summary = resp.summary || ({ range: '', from: '', to: '', live_ms: 0, earned: {}, added: {}, claimable: {}, pending: {}, count: 0, days: [] } as Summary)
  const campaigns = ex.campaigns || []
  const sessions = ex.sessions || []
  const snap = ex.snapshot || { pieces: 0, overlap_clips: 0, open_room: '0', sessions: {} }
  const historyDays = history?.days || []
  return (
    <div>
      <div className="page-head" style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        {u ? <Avatar url={u.avatar_url} name={displayName(u)} size={44} /> : null}
        <div style={{ flex: 1 }}>
          <h1 style={{ margin: 0 }}>{u ? displayName(u) : `#${id}`}</h1>
          <p style={{ margin: 0 }}>
            {u ? `@${u.username} · #${u.id}` : ''} {ex.live ? (ex.counting ? '· Live, counting' : '· Live, not counting') : '· Not live'}
          </p>
        </div>
        <Link to={`/live-time/users/${id}`} className="muted" style={{ fontSize: 13 }}>
          Live time →
        </Link>
        <a href="#time-reset" className="muted" style={{ fontSize: 13 }}>
          Testing: clear time ↓
        </a>
        <Button size="sm" variant="secondary" disabled={busy} onClick={() => void reevaluate()}>
          Re-evaluate now
        </Button>
      </div>
      <SectionNav />
      {msg ? <div className="muted" style={{ marginBottom: 8 }}>{msg}</div> : null}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 10 }}>
        <Stat label={`Added · ${summary.range || '—'}`} value={fmtAmounts(summary.added)} sub={`${summary.from || '—'} → ${summary.to || '—'}`} />
        <Stat label="Live time in range" value={fmtMs(summary.live_ms || 0, { seconds: false })} />
        <Stat label="Pending" value={fmtAmounts(summary.pending)} sub="earned, not yet in wallet" />
        <Stat label="Claimable" value={fmtAmounts(summary.claimable)} />
        <Stat label="Rewards" value={String(summary.count || 0)} />
      </div>
      <div style={{ marginTop: 8 }}>
        <CalendarLine cal={ex.calendar} />
      </div>

      <Card title="Eligibility right now">
        {campaigns.length === 0 ? (
          <p className="muted">No active campaign applies.</p>
        ) : (
          campaigns.map((c) => {
            const roles = c.roles || []
            const levels = c.levels || []
            const conditions = c.conditions || []
            const pending = c.pending || []
            const verified = c.verified_ms || 0
            return (
            <div key={c.campaign_id} style={{ borderTop: '1px solid var(--line)', padding: '10px 0' }}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                <strong>{c.campaign}</strong>
                <span className="muted" style={{ fontSize: 12 }}>
                  {c.period_kind} · {c.period_key} · {roles.join('/') || '—'} · {c.mode_rule} · {c.delivery}
                </span>
                <StatusPill status={c.eligible ? 'added' : 'expired'} label={c.eligible ? 'Eligible' : 'Not eligible'} />
                {c.stale ? <StatusPill status="pending" label="refreshing" /> : null}
                {c.computed_at ? <span className="muted" style={{ fontSize: 11 }}>as of {fmtClock(c.computed_at)}</span> : null}
              </div>
              {!c.eligible ? (
                <div style={{ fontSize: 12, marginTop: 4, color: '#991b1b' }}>
                  Not met yet: <strong>{reasonText(c.reason) || 'see conditions below'}</strong>
                  {c.reason === 'TIME_NOT_REACHED' && c.next_threshold_ms ? ` — ${fmtMs(c.remaining_ms || 0, { seconds: false })} more live time needed for the next level.` : ''}
                </div>
              ) : null}
              {c.ladder ? <LadderPanel ladder={c.ladder} /> : null}
              <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>
                Verified {fmtMs(verified, { seconds: false })}
                {c.next_threshold_ms ? (
                  <>
                    {' '}
                    · next {fmtMs(c.next_threshold_ms, { seconds: false })} → {c.next_amount} {c.next_asset} in {fmtMs(c.remaining_ms || 0, { seconds: false })}
                    {c.next_due_at ? ` (due ${fmtClock(c.next_due_at)})` : ''}
                  </>
                ) : (
                  ' · all levels reached'
                )}
                {c.max_total ? ` · ${c.rewards_in_period || 0}/${c.max_total} this period` : ''}
              </div>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 6 }}>
                {levels.map((l) => (
                  <span
                    key={l.threshold_ms}
                    className="status-pill"
                    style={{
                      fontSize: 11,
                      background: l.paid ? 'var(--accent-soft)' : l.reached ? '#fef3c7' : 'var(--wash-deep)',
                      color: l.paid ? 'var(--accent-hover)' : l.reached ? '#92400e' : 'var(--ink-soft)',
                    }}
                  >
                    {fmtMs(l.threshold_ms, { seconds: false })} → {l.amount} {l.asset}
                    {l.paid ? ' ✓ paid' : l.reached ? ' · reached, pending' : ` · needs ${fmtMs(Math.max(0, (l.threshold_ms || 0) - verified), { seconds: false })} more`}
                  </span>
                ))}
              </div>
              <div style={{ fontSize: 12, marginTop: 6 }}>
                <span className="muted">Conditions:</span>
                {conditions.length ? (
                  <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
                    {conditions.map((s, i) => (
                      <ConditionLine key={i} s={s} />
                    ))}
                  </ul>
                ) : (
                  <span className="muted"> {c.reason === 'ROLE_NOT_ALLOWED' || c.reason === 'MODE_NOT_ALLOWED' ? 'not checked — the role / live-type rule already fails' : 'none besides time, role and live type — all met'}</span>
                )}
              </div>
              {pending.length ? (
                <ul style={{ margin: '6px 0 0', paddingLeft: 18, fontSize: 12 }}>
                  {pending.map((p) => (
                    <li key={p.threshold_ms}>
                      {fmtMs(p.threshold_ms, { seconds: false })} · {p.status} {p.reason ? `(${reasonText(p.reason)})` : ''} · reached {fmtClock(p.reached_at)}
                      {p.next_eval_at ? ` · retry ${fmtClock(p.next_eval_at)}` : ''}
                      {p.deadline ? ` · deadline ${fmtClock(p.deadline)}` : ''}
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
            )
          })
        )}
      </Card>

      <ChecksCard checks={resp.checks || []} />

      <Card title="Live sessions · last 7 days">
        <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
          Every live this host did, as the time ledger recorded it, and what each campaign made of it. This is the record to check when a host ends a live and asks why there was no reward.
        </p>
        {!sessions.length ? (
          <p className="muted">No live sessions in the last 7 days.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>When</th>
                <th>Role · type</th>
                <th>Live time</th>
                <th>Counted for rewards</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((s) => {
                const camp = s.campaigns || []
                const reached = (c: SessionCampaign) => c.reached_ms || []
                return (
                <tr key={s.key}>
                  <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                    {s.day}
                    <br />
                    {fmtClock(s.start)} → {s.open ? <StatusPill status="added" label="live now" /> : fmtClock(s.end)}
                    <br />
                    <span className="muted">room #{s.room_id}</span>
                  </td>
                  <td style={{ fontSize: 12 }}>
                    {s.role || '—'} · {s.mode || '—'}
                    {s.historical ? (
                      <>
                        <br />
                        <span className="muted">before policy start</span>
                      </>
                    ) : null}
                  </td>
                  <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                    {fmtMs((s.live_ms || 0) + (s.reset_ms || 0))}
                    {s.reset_ms ? (
                      <>
                        <br />
                        <span style={{ color: '#991b1b' }}>−{fmtMs(s.reset_ms)} cleared by reset</span>
                      </>
                    ) : null}
                  </td>
                  <td style={{ fontSize: 12 }}>
                    {camp.length === 0 ? (
                      <span className="muted">no active campaign</span>
                    ) : (
                      camp.map((c) => (
                        <div key={c.campaign_id} style={{ marginBottom: 3 }}>
                          <strong>{c.campaign}</strong>{' '}
                          {c.counts ? (
                            <>
                              <span style={{ color: 'var(--accent-hover)' }}>✓ {fmtMs(c.counted_ms || 0)} counted</span>
                              {c.per_session ? (
                                <span className="muted">
                                  {' '}
                                  · this session {reached(c).length ? `completed ${reached(c).map((m) => fmtMs(m, { seconds: false })).join(', ')}` : 'completed no level'}
                                  {c.next_threshold_ms ? ` · next ${fmtMs(c.next_threshold_ms, { seconds: false })}` : ''}
                                </span>
                              ) : (
                                <span className="muted"> · added to the {c.period_kind} total</span>
                              )}
                            </>
                          ) : (
                            <span style={{ color: '#991b1b' }}>✗ not counted — {reasonText(c.why)}</span>
                          )}
                        </div>
                      ))
                    )}
                  </td>
                </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </Card>

      <Card title="History by business day">
        {!history ? (
          <PageLoader />
        ) : historyDays.length === 0 ? (
          <p className="muted">No rewards yet.</p>
        ) : (
          historyDays.map((d) => (
            <div key={d.day} style={{ marginBottom: 14 }}>
              <div style={{ display: 'flex', gap: 10, alignItems: 'baseline', flexWrap: 'wrap' }}>
                <strong>{d.label}</strong>
                <span className="muted" style={{ fontSize: 12 }}>
                  {d.day} · live {fmtMs(d.live_ms || 0, { seconds: false })} · {fmtAmounts(d.totals)}
                </span>
                <span className="muted" style={{ fontSize: 12 }}>{dayRollup(d.rewards || [])}</span>
              </div>
              <table className="table" style={{ marginTop: 6 }}>
                <tbody>
                  {(d.rewards || []).map((r) => (
                    <RewardRow key={r.id} r={r} to={`/live-rewards/payouts/${r.id}`} />
                  ))}
                </tbody>
              </table>
            </div>
          ))
        )}
      </Card>

      <TimeResetCard userId={id} today={ex.calendar?.today} resets={resp.resets || []} onChanged={() => void load()} />

      <Card title="Snapshot (debug)">
        <div className="muted" style={{ fontSize: 12 }}>
          As of {fmtClock(ex.at)} · {snap.pieces || 0} day-pieces · {snap.overlap_clips || 0} overlap clips
          {snap.reset_windows ? ` · ${fmtMs(snap.reset_ms || 0, { seconds: false })} cleared by ${snap.reset_windows} reset(s)` : ''}
          {snap.open_room && snap.open_room !== '0' ? ` · open room #${snap.open_room} since ${fmtClock(snap.open_since)} counted until ${fmtClock(snap.open_counted_until)}` : ''}
        </div>
        {Object.keys(snap.sessions || {}).length ? (
          <ul style={{ fontSize: 12, paddingLeft: 18 }}>
            {Object.entries(snap.sessions || {}).map(([k, v]) => (
              <li key={k}>
                <code>{k}</code> {v}
              </li>
            ))}
          </ul>
        ) : null}
        <details style={{ marginTop: 8 }}>
          <summary className="muted" style={{ fontSize: 12, cursor: 'pointer' }}>
            Progress hints ({ex.progress?.length ?? 0}) · threshold rows ({ex.thresholds?.length ?? 0})
          </summary>
          <pre style={{ fontSize: 11, overflow: 'auto', maxHeight: 320 }}>{JSON.stringify({ progress: ex.progress || [], thresholds: ex.thresholds || [] }, null, 2)}</pre>
        </details>
      </Card>
    </div>
  )
}

// ─── Clear counted time ──────────────────────────────────────────────────────

/**
 * Operator reset: live time on one business day stops counting for rewards.
 * Today → from the day start until this instant (the host keeps streaming and
 * earns again from now). A past day → the whole day. Only the reward count is
 * affected: the live-time ledger stays as recorded and paid rewards stay paid.
 */
function TimeResetCard({ userId, today, resets, onChanged }: { userId: string; today?: string; resets: TimeReset[]; onChanged: () => void }) {
  const [day, setDay] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const [err, setErr] = useState('')
  const target = day || today || ''
  const isToday = !today || target === today

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const what = isToday ? `today (${target}) from the day start until now` : `the whole business day ${target}`
    if (!window.confirm(`Clear this user's counted live time for ${what}?\n\nRewards count restarts from that point. Unpaid pending levels in that window are removed; rewards already paid stay paid. Live-time records are not changed.`)) return
    setBusy(true)
    setErr('')
    setMsg('')
    const r = await post<TimeReset>(`/v1/admin/rewards/users/${userId}/time-reset`, { day: day || undefined, reason })
    setBusy(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    setMsg(r.message)
    setReason('')
    onChanged()
  }

  const revoke = async (id: string) => {
    if (!window.confirm('Put this time back? It will count for rewards again at the next evaluation.')) return
    setBusy(true)
    const r = await del<{ id: string }>(`/v1/admin/rewards/time-resets/${id}`)
    setBusy(false)
    setMsg(r.ok ? r.message : '')
    setErr(r.ok ? '' : r.message)
    onChanged()
  }

  return (
    <div id="time-reset">
    <Card title="Testing tool · Clear counted time (rewards only)">
      <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
        Administrator-only, for testing a policy: the host's live time on the chosen day stops counting towards rewards, so the count restarts from that point and levels can be earned again. Hosts never see
        or use this. Today clears from the day start until now; a past day clears the whole day. The live-time ledger is untouched and rewards already paid stay paid (reverse those from the payout page).
      </p>
      <form onSubmit={(e) => void submit(e)} style={{ display: 'grid', gridTemplateColumns: 'minmax(160px, 200px) 1fr auto', gap: 10, alignItems: 'end' }}>
        <Field label="Business day" type="date" value={day} max={today} onChange={(e) => setDay(e.target.value)} />
        <Field
          label="Reason (shown with the reset)"
          value={reason}
          required
          minLength={3}
          maxLength={300}
          placeholder="e.g. idle stream, agency request #123"
          onChange={(e) => setReason(e.target.value)}
        />
        <Button type="submit" variant="danger" loading={busy} disabled={reason.trim().length < 3}>
          {isToday ? 'Clear today until now' : `Clear ${target}`}
        </Button>
      </form>
      {err ? <div className="form-error">{err}</div> : null}
      {msg ? <div className="muted" style={{ fontSize: 12, marginTop: 6 }}>{msg}</div> : null}
      {resets.length ? (
        <table className="table" style={{ marginTop: 10 }}>
          <thead>
            <tr>
              <th>Day</th>
              <th>Cleared window</th>
              <th>Reason</th>
              <th>Pending removed</th>
              <th>By</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {resets.map((r) => (
              <tr key={r.id} style={r.revoked_at ? { opacity: 0.55 } : undefined}>
                <td>{r.day}</td>
                <td style={{ fontSize: 12 }}>
                  {fmtClock(r.from_at)} → {fmtClock(r.to_at)} <span className="muted">({fmtMs(r.cleared_ms, { seconds: false })})</span>
                </td>
                <td style={{ fontSize: 12 }}>{r.reason}</td>
                <td>{r.removed_pending}</td>
                <td style={{ fontSize: 12 }}>
                  #{r.created_by} · {fmtClock(r.created_at)}
                </td>
                <td>
                  {r.revoked_at ? <StatusPill status="expired" label={`Revoked ${fmtClock(r.revoked_at)}`} /> : <StatusPill status="added" label="Active" />}
                </td>
                <td>
                  {!r.revoked_at ? (
                    <Button size="sm" variant="secondary" disabled={busy} onClick={() => void revoke(r.id)}>
                      Put time back
                    </Button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </Card>
    </div>
  )
}

// ─── Detail ──────────────────────────────────────────────────────────────────

function RewardDetailPage() {
  const { id = '' } = useParams()
  const [d, setD] = useState<Detail | null>(null)
  const [error, setError] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const load = useCallback(async () => {
    const r = await get<Detail>(`/v1/admin/rewards/payouts/${id}`)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setD(r.data)
  }, [id])
  useEffect(() => {
    void load()
  }, [load])
  const reverse = async () => {
    if (!window.confirm('Reverse this reward? The amount is taken back from the balance and a reversal line is written.')) return
    setBusy(true)
    const r = await post<LedgerView>(`/v1/admin/rewards/payouts/${id}/reverse`, { note })
    setBusy(false)
    setMsg(r.message)
    if (r.ok) void load()
  }
  if (error) return <div className="form-error">{error}</div>
  if (!d) return <PageLoader />
  return (
    <div>
      <div className="page-head">
        <h1>{d.message.title}</h1>
        <p>{d.message.body}</p>
      </div>
      <SectionNav />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 10 }}>
        <Stat label="Reward day" value={d.day} sub={`${d.calendar.tz} · day starts ${d.calendar.day_start}`} />
        <Stat label="Completed at" value={d.message.reached_at || '—'} sub={`required ${fmtMs(d.required_ms, { seconds: false })} · verified ${fmtMs(d.verified_ms, { seconds: false })}`} />
        <Stat label="Added at" value={d.message.added_at || '—'} sub={d.delay_ms ? `${fmtMs(d.delay_ms)} after completion` : undefined} tone={d.delay_ms > 60_000 ? 'warn' : undefined} />
        <Stat
          label="Balance"
          value={d.balance_before != null && d.balance_after != null ? `${d.balance_before.toLocaleString()} → ${d.balance_after.toLocaleString()}` : '—'}
          sub={d.asset}
        />
        <Stat label="Status" value={d.status_label} sub={`${d.kind} · ${d.source} · ${d.delivery}`} />
        {typeof d.evidence?.tier_name === 'string' ? (
          d.evidence.milestone ? (
            <Stat
              label="Gift level at completion"
              value={String(d.evidence.tier_name)}
              sub={`${Number(d.evidence.rate || d.amount).toLocaleString()} ${d.asset} once this period · ${Number(d.evidence.received_diamonds || 0).toLocaleString()} diamonds received`}
            />
          ) : (
            <Stat
              label="Gift level for this hour"
              value={String(d.evidence.tier_name)}
              sub={`${Number(d.evidence.rate || d.amount).toLocaleString()} ${d.asset}/h · ${Number(d.evidence.received_diamonds || 0).toLocaleString()} diamonds received${d.evidence.hour_no_today ? ` · hour ${String(d.evidence.hour_no_today)} of the day` : ''}${d.evidence.max_hours_per_day ? ` (max ${String(d.evidence.max_hours_per_day)})` : ''}`}
            />
          )
        ) : null}
      </div>
      <Card title="Timeline">
        <ol style={{ paddingLeft: 18, fontSize: 13 }}>
          {d.timeline.map((s, i) => (
            <li key={i}>
              <strong>{s.local}</strong> — {s.what}
            </li>
          ))}
        </ol>
        <pre style={{ fontSize: 12, whiteSpace: 'pre-wrap' }}>{d.message.detail}</pre>
      </Card>
      <Card title="Host">
        <Link to={`/live-rewards/users/${d.user_id}`}>Open host #{d.user_id} →</Link>
        <span className="muted" style={{ marginLeft: 12, fontSize: 12 }}>
          {d.campaign} · version {d.version_no} · {d.period_kind} {d.period_key} · {d.role} · {d.mode_rule}
          {d.wallet_ref ? ` · wallet ref ${d.wallet_ref}` : ''}
        </span>
      </Card>
      <Card title="Evidence">
        <pre style={{ fontSize: 11, overflow: 'auto', maxHeight: 360 }}>{JSON.stringify({ evidence: d.evidence, conditions: d.conditions, threshold: d.threshold }, null, 2)}</pre>
      </Card>
      {(d.status === 'added' || d.status === 'claimed') && d.kind !== 'reversal' ? (
        <Card title="Reverse">
          <Field label="Reason (recorded in the audit trail)" value={note} onChange={(e) => setNote(e.target.value)} />
          <div style={{ marginTop: 8, display: 'flex', gap: 8, alignItems: 'center' }}>
            <Button size="sm" variant="danger" disabled={busy || note.trim().length < 3} onClick={() => void reverse()}>
              Reverse this reward
            </Button>
            {msg ? <span className="muted">{msg}</span> : null}
          </div>
        </Card>
      ) : null}
    </div>
  )
}

// ─── Findings ────────────────────────────────────────────────────────────────

function RewardsFindings() {
  const [params, setParams] = useSearchParams()
  const resolved = params.get('resolved') === '1'
  const kind = params.get('kind') || ''
  const userID = params.get('user_id') || ''
  const campaignID = params.get('campaign_id') || ''
  const severity = params.get('severity') || ''
  const [hostInput, setHostInput] = useState(userID)
  const [resp, setResp] = useState<{ items: Finding[]; open: Record<string, number>; cursor: string } | null>(null)
  const [error, setError] = useState('')
  /** Keep every other filter when one changes. */
  const setFilter = (patch: Record<string, string>) => {
    const next: Record<string, string> = { ...(resolved ? { resolved: '1' } : {}), kind, user_id: userID, campaign_id: campaignID, severity, ...patch }
    setParams(Object.fromEntries(Object.entries(next).filter(([, v]) => v !== '')))
  }
  const load = useCallback(async () => {
    const p = new URLSearchParams()
    if (resolved) p.set('resolved', '1')
    if (kind) p.set('kind', kind)
    if (userID) p.set('user_id', userID)
    if (campaignID) p.set('campaign_id', campaignID)
    if (severity) p.set('severity', severity)
    const r = await get<{ items: Finding[]; open: Record<string, number>; cursor: string }>(`/v1/admin/rewards/findings?${p}`)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setResp(r.data)
  }, [resolved, kind, userID, campaignID, severity])
  useEffect(() => {
    void load()
  }, [load])
  const resolve = async (id: string) => {
    const r = await post(`/v1/admin/rewards/findings/${id}/resolve`, {})
    if (r.ok) void load()
  }
  const rebuild = async () => {
    if (!window.confirm('Rebuild the daily projections from the ledger?')) return
    await post('/v1/admin/rewards/rebuild', {})
    void load()
  }
  return (
    <div>
      <div className="page-head">
        <h1>Findings</h1>
        <p>
          Facts the auditor found: a missing hour, a wrongly paid level or amount, a reward in the wrong week, more hours than the level allows, a wallet entry missing. Detection is separate from repair; repairs run through the
          normal payout path and are visible as repaired here and on the host’s page.
        </p>
      </div>
      <SectionNav />
      <Card>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <Button size="sm" variant={resolved ? 'ghost' : 'primary'} onClick={() => setFilter({ resolved: '' })}>
            Open
          </Button>
          <Button size="sm" variant={resolved ? 'primary' : 'ghost'} onClick={() => setFilter({ resolved: '1' })}>
            Resolved
          </Button>
          <select className="admin-context-select" value={kind} onChange={(e) => setFilter({ kind: e.target.value })}>
            <option value="">All kinds</option>
            {Object.keys(resp?.open || {}).map((k) => (
              <option key={k} value={k}>
                {k} ({resp?.open[k]})
              </option>
            ))}
          </select>
          <select className="admin-context-select" value={severity} onChange={(e) => setFilter({ severity: e.target.value })}>
            <option value="">Any severity</option>
            <option value="4">Money wrong (4+)</option>
            <option value="3">Missing / late (3+)</option>
          </select>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              setFilter({ user_id: hostInput.trim().replace(/^#/, '') })
            }}
            style={{ display: 'flex', gap: 4 }}
          >
            <input className="admin-context-select" placeholder="Host id" value={hostInput} onChange={(e) => setHostInput(e.target.value)} style={{ width: 140 }} />
            <Button size="sm" variant="ghost" type="submit">
              Filter
            </Button>
            {userID ? (
              <Button size="sm" variant="ghost" onClick={() => { setHostInput(''); setFilter({ user_id: '' }) }}>
                Clear
              </Button>
            ) : null}
          </form>
          {campaignID ? (
            <Button size="sm" variant="ghost" onClick={() => setFilter({ campaign_id: '' })}>
              campaign {campaignID} ✕
            </Button>
          ) : null}
          <span style={{ flex: 1 }} />
          <Button size="sm" variant="secondary" onClick={() => void rebuild()}>
            Rebuild projections
          </Button>
        </div>
      </Card>
      {error ? <div className="form-error">{error}</div> : null}
      <Card>
        {!resp ? (
          <PageLoader />
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Found</th>
                <th>Kind</th>
                <th>Host</th>
                <th>Where</th>
                <th>Detail</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {(resp.items || []).length === 0 ? (
                <tr>
                  <td colSpan={6} className="muted">
                    Nothing here. The reconciler found no gap between live time, thresholds, the ledger and wallets.
                  </td>
                </tr>
              ) : (
                (resp.items || []).map((f) => (
                  <tr key={f.id}>
                    <td style={{ whiteSpace: 'nowrap' }}>{fmtClock(f.found_at)}</td>
                    <td>
                      <StatusPill status={f.severity >= 3 ? 'expired' : 'pending'} label={f.kind} />
                      <div style={{ fontSize: 11 }}>{findingText(f.kind)}</div>
                      {f.repaired_at ? <div className="muted" style={{ fontSize: 11 }}>repaired {fmtClock(f.repaired_at)}</div> : f.repair_attempts ? <div className="muted" style={{ fontSize: 11 }}>{f.repair_attempts} repair attempts</div> : null}
                    </td>
                    <td>{f.user_id && f.user_id !== '0' ? <Link to={`/live-rewards/users/${f.user_id}`}>#{f.user_id}</Link> : '—'}</td>
                    <td className="muted" style={{ fontSize: 12 }}>
                      {f.campaign_id && f.campaign_id !== '0' ? `campaign ${f.campaign_id} · ` : ''}
                      {f.period_key} {f.threshold_ms ? `· ${fmtMs(f.threshold_ms, { seconds: false })}` : ''}
                      {f.ledger_id ? (
                        <>
                          {' · '}
                          <Link to={`/live-rewards/payouts/${f.ledger_id}`}>ledger</Link>
                        </>
                      ) : null}
                    </td>
                    <td>
                      <code style={{ fontSize: 11 }}>{JSON.stringify(f.detail ?? {})}</code>
                    </td>
                    <td>
                      {!f.resolved_at ? (
                        <Button size="sm" variant="ghost" onClick={() => void resolve(f.id)}>
                          Resolve
                        </Button>
                      ) : (
                        <span className="muted" style={{ fontSize: 11 }}>
                          {fmtClock(f.resolved_at)}
                        </span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
