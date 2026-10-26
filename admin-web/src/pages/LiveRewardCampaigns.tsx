import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import { del, get, patch, post, put } from '../lib/api'
import { ROLE_STATUS, rolesApi, type RoleView } from '../lib/roles'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { fmtClock, fmtMs } from './LiveTime'
import { EngineBanner, StatusPill } from './LiveRewards'

/**
 * Campaign editor. The vocabulary (roles, modes, periods, conditions,
 * limits) comes from the server; the operator composes, the server
 * validates, and a version is immutable once active — changing anything
 * means a new version with an explicit change behaviour.
 */

type Vocab = {
  roles: { id: number; name: string }[]
  assets: string[]
  mode_rules: string[]
  time_requirements: string[]
  period_kinds: string[]
  reward_modes: string[]
  deliveries: string[]
  claim_rules: string[]
  change_behaviors: string[]
  conditions: { type: string; ops: string[]; windows: string[]; numeric: boolean; boolean?: boolean }[]
  condition_limits: { depth: number; groups: number; leaves: number }
  weekdays?: { id: number; name: string }[]
}

/**
 * One fixed level. The rule fields are optional and all additive: a level
 * without them behaves exactly as before. They are judged for this level
 * only, after the version-wide conditions.
 */
type Level = {
  threshold_ms: number
  asset: string
  amount: number
  amount_cohost?: number | null
  label?: string
  conditions?: CondNode
  max_total?: number
  max_per_day?: number
  max_per_week?: number
  max_per_month?: number
  days_of_week?: number[]
  days_of_month?: number[]
  active_from?: string | null
  active_until?: string | null
  requires_previous?: boolean
}

type Version = {
  id: string
  campaign_id: string
  version_no: number
  status: string
  effective_from?: string
  effective_to?: string
  activated_at?: string
  change_behavior: string
  reset_progress: boolean
  eligible_roles: number[]
  mode_rule: string
  time_requirement: string
  min_session_ms: number
  period_kind: string
  rolling_days: number
  include_historical: boolean
  reward_mode: string
  repeat_interval_ms: number
  repeat_asset: string
  repeat_amount: number
  repeat_amount_cohost?: number | null
  max_total: number
  max_per_day: number
  max_per_week: number
  max_per_month: number
  min_interval_ms: number
  qualification_extension_ms: number
  delivery: string
  claim_rule: string
  claim_after_ms: number
  claim_expires_ms: number
  claim_days_of_month: number[]
  claim_from?: string | null
  claim_until?: string | null
  claim_max_per_day: number
  notify_push: boolean
  notify_notice: boolean
  notify_live_toast: boolean
  notify_title: string
  notify_body: string
  conditions: unknown
  condition_types: string[]
  note: string
  created_at: string
  levels: (Level & { id?: string; level_no?: number })[]
  // Restart schedule (period_kind = cycle) and the level a returning host
  // resumes at when a new period opens (1 = start over, today's behaviour).
  cycle_weekdays: number[]
  cycle_days_of_month: number[]
  cycle_every_days: number
  cycle_anchor?: string | null
  // Each host's own week: cycles of cycle_every_days (blank = 7) anchored
  // at the host's signup day plus the ladder's new-signup days — not a
  // global restart weekday.
  cycle_per_host: boolean
  reset_to_level_no: number
  // Ladder group switches — flippable at ANY time, even on the active
  // version. Weekly off: hours outside the new-signup window wait, unpaid
  // (LADDER_OFF), until switched back on. New-signup off: young hosts go by
  // the weekly levels from day one.
  tiers_weekly_on: boolean
  tiers_new_host_on: boolean
  // Optional gift-level ladder for a repeating reward: the level reached by
  // diamonds received in the period prices each paid hour. [] = plain repeating.
  rate_tiers: RateTier[]
}

/** One ladder step (server: rewards.RateTier). */
type RateTier = {
  kind: 'weekly' | 'new_host'
  key?: string
  name: string
  min_received: number
  new_host_days?: number
  amount: number
  amount_cohost?: number | null
  asset: string
  max_hours_per_day: number
}

/** The starter ladder (same as the seeded "Weekly host reward" draft). */
const STARTER_LADDER = (asset: string): RateTier[] => [
  { kind: 'weekly', name: 'Level 1', min_received: 0, amount: 1000, asset, max_hours_per_day: 2 },
  { kind: 'weekly', name: 'Level 2', min_received: 150_000, amount: 3000, asset, max_hours_per_day: 2 },
  { kind: 'weekly', name: 'Level 3', min_received: 300_000, amount: 5000, asset, max_hours_per_day: 2 },
  { kind: 'weekly', name: 'Level 4', min_received: 900_000, amount: 12_000, asset, max_hours_per_day: 2 },
  { kind: 'weekly', name: 'Level 5', min_received: 1_200_000, amount: 18_000, asset, max_hours_per_day: 2 },
  { kind: 'new_host', name: 'New Signup', min_received: 0, new_host_days: 6, amount: 4000, asset, max_hours_per_day: 2 },
]

const weeklyTiers = (v: Version) => (v.rate_tiers || []).filter((t) => t.kind === 'weekly').sort((a, b) => a.min_received - b.min_received)
const newHostTier = (v: Version) => (v.rate_tiers || []).find((t) => t.kind === 'new_host')
const hasLadder = (v: Version) => (v.rate_tiers || []).length > 0
// Milestone: a fixed level whose one payout per period is priced by the gift ladder (no per-hour pay).
const isMilestone = (v: Version) => v.reward_mode === 'fixed_levels' && hasLadder(v)

type Campaign = { id: string; name: string; description: string; status: string; created_at: string; active_version?: Version }

const MIN = 60_000

const blank = (): Version => ({
  id: '',
  campaign_id: '',
  version_no: 0,
  status: 'draft',
  change_behavior: 'new_periods',
  reset_progress: false,
  eligible_roles: [1],
  mode_rule: 'combined',
  time_requirement: 'accumulated',
  min_session_ms: 0,
  period_kind: 'day',
  rolling_days: 7,
  include_historical: false,
  reward_mode: 'fixed_levels',
  repeat_interval_ms: 10 * MIN,
  repeat_asset: 'coins',
  repeat_amount: 10,
  repeat_amount_cohost: null,
  max_total: 0,
  max_per_day: 0,
  max_per_week: 0,
  max_per_month: 0,
  min_interval_ms: 0,
  qualification_extension_ms: 0,
  delivery: 'direct',
  claim_rule: 'immediate',
  claim_after_ms: 0,
  claim_expires_ms: 0,
  claim_days_of_month: [],
  claim_from: null,
  claim_until: null,
  claim_max_per_day: 0,
  notify_push: true,
  notify_notice: true,
  notify_live_toast: true,
  notify_title: '',
  notify_body: '',
  conditions: {},
  condition_types: [],
  note: '',
  created_at: '',
  levels: [{ threshold_ms: 30 * MIN, asset: 'coins', amount: 100, amount_cohost: null, label: '' }],
  cycle_weekdays: [],
  cycle_days_of_month: [],
  cycle_every_days: 0,
  cycle_anchor: null,
  cycle_per_host: false,
  reset_to_level_no: 1,
  tiers_weekly_on: true,
  tiers_new_host_on: true,
  rate_tiers: [],
})

// ─── level rules / cycle helpers ─────────────────────────────────────────────

/** Monday-first for display; ids are JavaScript / server weekday numbers (0 = Sunday). */
const WEEKDAYS: { id: number; short: string; long: string }[] = [
  { id: 1, short: 'Mon', long: 'Monday' },
  { id: 2, short: 'Tue', long: 'Tuesday' },
  { id: 3, short: 'Wed', long: 'Wednesday' },
  { id: 4, short: 'Thu', long: 'Thursday' },
  { id: 5, short: 'Fri', long: 'Friday' },
  { id: 6, short: 'Sat', long: 'Saturday' },
  { id: 0, short: 'Sun', long: 'Sunday' },
]
const weekdayNames = (ids: number[] | undefined, long = false) =>
  WEEKDAYS.filter((w) => (ids || []).includes(w.id)).map((w) => (long ? w.long : w.short))

/** True when a condition tree has at least one leaf (an empty group is "no conditions"). */
function condNonEmpty(c: unknown): c is CondNode {
  if (!c || typeof c !== 'object') return false
  const n = c as CondNode
  if (isGroup(n)) return ((n.all || n.any || []) as CondNode[]).some(condNonEmpty)
  return typeof (n as CondLeaf).type === 'string' && (n as CondLeaf).type !== ''
}

/** What the server stores: an empty / all-empty tree becomes {} so a half-built group never blocks saving. */
const cleanCond = (c: unknown): CondNode => (condNonEmpty(c) ? c : {})

function levelHasRules(l: Level): boolean {
  return (
    condNonEmpty(l.conditions) ||
    !!(l.max_total || l.max_per_day || l.max_per_week || l.max_per_month) ||
    !!(l.days_of_week?.length || l.days_of_month?.length) ||
    !!(l.active_from || l.active_until) ||
    !!l.requires_previous
  )
}

/** Indices of v.levels in ascending threshold order; the server numbers levels the same way. */
const levelOrder = (levels: Level[]) => levels.map((_, i) => i).sort((a, b) => levels[a].threshold_ms - levels[b].threshold_ms)
const levelNoOf = (levels: Level[], i: number) => levelOrder(levels).indexOf(i) + 1
const levelTitle = (levels: Level[], i: number) => {
  const l = levels[i]
  return `Level ${levelNoOf(levels, i)} · ${fmtMs(l.threshold_ms, { seconds: false })} · ${l.amount} ${l.asset}${l.label ? ` — ${l.label}` : ''}`
}

/** Parses "1, 15, 31" into a clean 1..31 list (or 0..6 for weekdays). */
const parseDayList = (s: string, lo: number, hi: number) =>
  Array.from(new Set(s.split(',').map((x) => Math.round(Number(x.trim()))).filter((n) => Number.isFinite(n) && n >= lo && n <= hi))).sort((a, b) => a - b)

/** The restart schedule in words: "every Monday and Thursday". */
function cycleWords(v: Version): string {
  if (v.cycle_per_host)
    return `every ${v.cycle_every_days > 0 ? `${v.cycle_every_days} days` : '7 days'} per host, counted from each host's own signup day${hasLadder(v) && newHostTier(v) ? ' plus the new-signup days (one host restarts Tuesday→Tuesday, another Sunday→Sunday)' : ''}`
  if (v.cycle_weekdays?.length) return `every ${list(weekdayNames(v.cycle_weekdays, true))}`
  if (v.cycle_days_of_month?.length) return `on day${v.cycle_days_of_month.length === 1 ? '' : 's'} ${list(v.cycle_days_of_month.map(String))} of each month`
  if (v.cycle_every_days > 0) return `every ${v.cycle_every_days} days${v.cycle_anchor ? ` counted from ${v.cycle_anchor}` : ''}`
  return '{restart schedule not set}'
}

/** A level's own rules in words, appended after its reward. */
function levelRuleWords(l: Level, levelNo: number, userRoles: UserRole[]): string[] {
  const out: string[] = []
  if (condNonEmpty(l.conditions)) out.push(`only if ${condWords(l.conditions, userRoles)}`)
  if (l.requires_previous && levelNo > 1) out.push(`only after level ${levelNo - 1} has paid in the same period`)
  if (l.days_of_week?.length) out.push(`on ${list(weekdayNames(l.days_of_week, true), 'or')} only`)
  if (l.days_of_month?.length) out.push(`on day${l.days_of_month.length === 1 ? '' : 's'} ${list(l.days_of_month.map(String), 'or')} of the month only`)
  if (l.active_from && l.active_until) out.push(`from ${l.active_from} to ${l.active_until}`)
  else if (l.active_from) out.push(`from ${l.active_from}`)
  else if (l.active_until) out.push(`until ${l.active_until}`)
  const caps: string[] = []
  if (l.max_total) caps.push(`${l.max_total} time${l.max_total === 1 ? '' : 's'} in total`)
  if (l.max_per_day) caps.push(`${l.max_per_day} per day`)
  if (l.max_per_week) caps.push(`${l.max_per_week} per week`)
  if (l.max_per_month) caps.push(`${l.max_per_month} per month`)
  if (caps.length) out.push(`at most ${list(caps)}`)
  return out
}

export function LiveRewardCampaigns() {
  return (
    <Routes>
      <Route index element={<CampaignList />} />
      <Route path=":id" element={<CampaignDetail />} />
      <Route path=":id/new" element={<VersionEditor />} />
      <Route path=":id/new/:fromVersion" element={<VersionEditor />} />
      <Route path=":id/edit/:editVersion" element={<VersionEditor />} />
    </Routes>
  )
}

function useVocab() {
  const [v, setV] = useState<Vocab | null>(null)
  useEffect(() => {
    void (async () => {
      const r = await get<{ campaigns: Campaign[]; vocab: Vocab }>('/v1/admin/rewards/campaigns')
      if (r.ok) setV(r.data.vocab)
    })()
  }, [])
  return v
}

/** Platform roles (agent, VIP, …) for USER_ROLE conditions and for reading a policy aloud. */
type UserRole = { slug: string; title: string }
function useUserRoles(): UserRole[] {
  const [roles, setRoles] = useState<UserRole[]>([])
  useEffect(() => {
    void (async () => {
      const r = await rolesApi.list(false)
      if (!r.ok) return
      setRoles(
        (r.data.items || [])
          .filter((x: RoleView) => x.status === ROLE_STATUS.active || x.status === ROLE_STATUS.draft)
          .map((x: RoleView) => ({ slug: x.slug, title: x.title || x.name || x.slug })),
      )
    })()
  }, [])
  return roles
}

// ─── plain-words policy ──────────────────────────────────────────────────────

const list = (xs: string[], conj = 'and') => (xs.length <= 1 ? xs.join('') : `${xs.slice(0, -1).join(', ')} ${conj} ${xs[xs.length - 1]}`)
const mins = (ms: number) => fmtMs(ms, { seconds: false })

const MODE_WORDS: Record<string, string> = {
  audio: 'audio live only',
  video: 'video live only',
  combined: 'audio or video live (both kinds of time added together)',
  either: 'audio or video live (whichever kind of time reaches the target first)',
  each: 'audio and video live (both kinds of time must each reach the target)',
}
const PERIOD_WORDS: Record<string, (v: Version) => string> = {
  session: () => 'within one single live session',
  day: () => 'accumulated across the business day (resets at the Live Time day start)',
  week: () => 'accumulated across the business week',
  month: () => 'accumulated across the business month',
  lifetime: () => 'accumulated over the whole account lifetime',
  rolling_days: (v) => `accumulated over any rolling ${v.rolling_days}-day window`,
  cycle: (v) => `accumulated across the campaign's own cycle, which restarts ${cycleWords(v)} (at the Live Time day start)`,
}
const WINDOW_WORDS: Record<string, (n?: number) => string> = {
  period: () => 'in the reward period',
  current_day: () => 'in the current business day',
  calendar_week: () => 'in the calendar week',
  calendar_month: () => 'in the calendar month',
  lifetime: () => 'over the account lifetime',
  rolling_hours: (n) => `in the last ${n || 0} hours`,
  rolling_days: (n) => `in the last ${n || 0} days`,
}
const COND_WORDS: Record<string, { what: string; unit?: (n: number) => string }> = {
  SESSION_TIME: { what: 'a single live session', unit: (n) => `of at least ${mins(n)}` },
  LIVE_ROLE: { what: 'live role' },
  LIVE_MODE: { what: 'live mode' },
  USER_ROLE: { what: 'platform role' },
  ACCOUNT_AGE: { what: 'account age', unit: (n) => `${n} days` },
  FACE_VERIFIED: { what: 'face verification' },
  RECEIVED_GIFT_VALUE: { what: 'gift coins received', unit: (n) => `${n} coins` },
  RECEIVED_GIFT_COUNT: { what: 'gifts received', unit: (n) => `${n} gifts` },
  UNIQUE_GIFTERS: { what: 'different gifters', unit: (n) => `${n} people` },
  SENT_GIFT_VALUE: { what: 'gift coins sent', unit: (n) => `${n} coins` },
  SENT_GIFT_COUNT: { what: 'gifts sent', unit: (n) => `${n} gifts` },
  UNIQUE_RECIPIENTS: { what: 'different people gifted', unit: (n) => `${n} people` },
  QUALIFIED_DAYS: { what: 'days that reached the time target', unit: (n) => `${n} days` },
}
const OP_WORDS: Record<string, string> = { gte: 'at least', lte: 'at most', eq: 'exactly', in: 'one of' }

function condWords(node: CondNode, userRoles: UserRole[], depth = 0): string {
  if (isGroup(node)) {
    const key: 'all' | 'any' = 'all' in node ? 'all' : 'any'
    const kids = (node[key] || []).map((k) => condWords(k, userRoles, depth + 1)).filter(Boolean)
    if (kids.length === 0) return ''
    if (kids.length === 1) return kids[0]
    const joined = list(kids, key === 'all' ? 'and' : 'or')
    return depth > 0 ? `(${joined})` : joined
  }
  const leaf = node as CondLeaf
  // Anything not set yet reads as a {placeholder}, never as "undefined".
  if (!leaf.type) return '{condition}'
  const w = COND_WORDS[leaf.type] || { what: leaf.type }
  const op = leaf.op ? OP_WORDS[leaf.op] || leaf.op : '{operator}'
  const win = leaf.window && leaf.window !== 'period' ? ' ' + (WINDOW_WORDS[leaf.window] || (() => leaf.window))(leaf.window_n) : ''
  const setVals = (): string[] => {
    const raw = Array.isArray(leaf.value) ? (leaf.value as unknown[]) : leaf.value == null || leaf.value === '' ? [] : [leaf.value]
    const vals = raw.map(String).filter(Boolean)
    return vals.length ? vals : ['{value}']
  }
  if (leaf.type === 'LIVE_ROLE') return `the user is live as ${list(setVals().map((x) => (x === 'cohost' ? 'co-host' : x)), 'or')}`
  if (leaf.type === 'LIVE_MODE') return `the live is ${list(setVals(), 'or')}`
  if (leaf.type === 'USER_ROLE') return `the user holds the platform role ${list(setVals().map((s) => userRoles.find((r) => r.slug === s)?.title || s), 'or')}`
  if (leaf.type === 'FACE_VERIFIED') return 'the account’s face verification is approved (a reached reward is not added until it is)'
  const hasNum = leaf.value != null && leaf.value !== '' && !Number.isNaN(Number(leaf.value))
  const n = hasNum ? Number(leaf.value) : 0
  const num = (f?: (n: number) => string) => (hasNum ? (f ? f(n) : String(n)) : '{value}')
  if (leaf.type === 'SESSION_TIME') return `one live session lasts at least ${num(mins)}`
  if (leaf.type === 'ACCOUNT_AGE') return `the account is ${op} ${num()} days old`
  return `${w.what} ${op} ${num(w.unit)}${win}`
}

/** The rule as a manager reads it: who, doing what, for how long, gets what, how, with which limits. */
function describePolicy(v: Version, vocab: Vocab | null, userRoles: UserRole[]): { verdict: string; lines: string[] } {
  const roleWord = (id: number) => (id === 2 ? 'co-host' : id === 1 ? 'host' : vocab?.roles.find((r) => r.id === id)?.name || String(id))
  const who = v.eligible_roles.length ? list(v.eligible_roles.map(roleWord), 'or') : '{role}'
  const mode = MODE_WORDS[v.mode_rule] || v.mode_rule
  const period = (PERIOD_WORDS[v.period_kind] || (() => v.period_kind))(v)
  const timeReq =
    v.time_requirement === 'single_session'
      ? 'in one unbroken session'
      : v.time_requirement === 'accumulated_with_min_session'
        ? `counting only sessions of at least ${mins(v.min_session_ms)}`
        : ''
  const hist = v.include_historical ? ' Time backfilled from before the rule went live counts too.' : ''

  const cohostNote = (host: number, co: number | null | undefined) => (co != null && co !== host ? ` (co-host ${co})` : '')
  const amt = (n: number | null | undefined, asset: string) => (n ? `${n} ${asset || '{asset}'}` : `{amount} ${asset || '{asset}'}`)
  const sortedLevels = [...v.levels].sort((a, b) => a.threshold_ms - b.threshold_ms)
  const ladderWords = () => {
    const w = weeklyTiers(v)
    const nh = newHostTier(v)
    const step = (t: RateTier) => `${amt(t.amount, t.asset)}${cohostNote(t.amount, t.amount_cohost)} per ${v.repeat_interval_ms ? mins(v.repeat_interval_ms) : '{minutes}'}${t.max_hours_per_day ? ` (max ${t.max_hours_per_day} a day)` : ''}`
    const lines = w.map((t) => `${t.name || 'level'} from ${t.min_received.toLocaleString()} diamonds received this period: ${step(t)}`)
    if (nh) lines.push(`${nh.name || 'new host'} (the ONLY rate during the first ${nh.new_host_days} days after signup — weekly levels start automatically the day the window ends): ${step(nh)}`)
    const off =
      v.tiers_weekly_on === false && v.tiers_new_host_on === false
        ? ' (BOTH GROUPS SWITCHED OFF right now: nothing is paid until a switch is turned back on)'
        : v.tiers_weekly_on === false
          ? ' (WEEKLY LEVELS SWITCHED OFF right now: only the new-signup rate pays; other hours wait until weekly is back on)'
          : v.tiers_new_host_on === false
            ? ' (NEW-SIGNUP RATE SWITCHED OFF right now: young hosts go by the weekly levels from day one)'
            : ''
    return `the rate of the level reached by diamonds received in the period — ${list(lines, 'and')}${off}`
  }
  const milestoneWords = () => {
    const w = weeklyTiers(v)
    const nh = newHostTier(v)
    const l = sortedLevels[0]
    const lines = w.map((t) => `${t.name || 'level'} from ${t.min_received.toLocaleString()} diamonds received this period: ${amt(t.amount, t.asset)}${cohostNote(t.amount, t.amount_cohost)}`)
    if (nh) lines.push(`${nh.name || 'new host'} (the ONLY level during the first ${nh.new_host_days} days after signup): ${amt(nh.amount, nh.asset)}${cohostNote(nh.amount, nh.amount_cohost)}`)
    const rules = l ? levelRuleWords(l, 1, userRoles) : []
    return `ONE reward per period when ${l?.threshold_ms ? mins(l.threshold_ms) : '{minutes}'} is reached${rules.length ? ` (${rules.join('; ')})` : ''} — the amount is the gift level the host holds at that moment, not every level passed: ${list(lines, 'and')}. Nothing is paid per hour`
  }
  const reward =
    v.reward_mode === 'repeating'
      ? hasLadder(v)
        ? ladderWords()
        : `${amt(v.repeat_amount, v.repeat_asset)}${cohostNote(v.repeat_amount, v.repeat_amount_cohost)} every ${v.repeat_interval_ms ? mins(v.repeat_interval_ms) : '{minutes}'} of live time`
      : v.levels.length === 0
        ? '{levels not set}'
        : isMilestone(v)
          ? milestoneWords()
          : list(
            sortedLevels.map((l, i) => {
              const rules = levelRuleWords(l, i + 1, userRoles)
              return `${amt(l.amount, l.asset)}${cohostNote(l.amount, l.amount_cohost)} at ${l.threshold_ms ? mins(l.threshold_ms) : '{minutes}'}${l.label ? ` (${l.label})` : ''}${rules.length ? ` — ${rules.join('; ')}` : ''}`
            }),
            'and',
          )
  const levelRuleLines = v.reward_mode === 'fixed_levels' ? sortedLevels.map((l, i) => ({ no: i + 1, rules: levelRuleWords(l, i + 1, userRoles) })).filter((x) => x.rules.length) : []
  const resume =
    v.reward_mode === 'fixed_levels' && v.reset_to_level_no > 1
      ? `When a new period opens, a host the campaign has paid before resumes at level ${v.reset_to_level_no}: levels below it are skipped, so their first reward of the period is level ${v.reset_to_level_no}. A host's very first period always starts at level 1. Paid rewards and gift totals are never touched.`
      : ''

  const limits: string[] = []
  if (v.max_total) limits.push(`at most ${v.max_total} reward${v.max_total === 1 ? '' : 's'} per ${v.period_kind === 'rolling_days' ? 'window' : v.period_kind}`)
  if (v.max_per_day) limits.push(`at most ${v.max_per_day} per day`)
  if (v.max_per_week) limits.push(`at most ${v.max_per_week} per week`)
  if (v.max_per_month) limits.push(`at most ${v.max_per_month} per month`)
  if (v.min_interval_ms) limits.push(`at least ${mins(v.min_interval_ms)} between two rewards`)

  const cond = condWords((v.conditions as CondNode) || {}, userRoles)
  const grace = v.qualification_extension_ms ? ` A reward blocked only by a condition gets ${mins(v.qualification_extension_ms)} after the period ends to satisfy it.` : ''

  let delivery: string
  if (v.delivery === 'direct') delivery = 'added to the balance the moment the time is reached'
  else {
    const open =
      v.claim_rule === 'relative'
        ? `${mins(v.claim_after_ms)} after it is earned`
        : v.claim_rule === 'calendar_days'
          ? `on day${v.claim_days_of_month.length === 1 ? '' : 's'} ${list(v.claim_days_of_month.map(String), 'and')} of each month`
          : v.claim_rule === 'fixed_dates'
            ? `from ${v.claim_from ? fmtClock(v.claim_from) : '—'}${v.claim_until ? ` until ${fmtClock(v.claim_until)}` : ''}`
            : 'immediately'
    delivery = `held until the host taps Claim; the claim window opens ${open}${v.claim_expires_ms ? ` and closes ${mins(v.claim_expires_ms)} later` : ''}${v.claim_max_per_day ? `; at most ${v.claim_max_per_day} claim${v.claim_max_per_day === 1 ? '' : 's'} a day` : ''}`
  }
  const notify = [v.notify_push ? 'push' : '', v.notify_notice ? 'inbox notice' : '', v.notify_live_toast ? 'in-live toast' : ''].filter(Boolean)

  const verdict =
    `Policy: a ${who} who goes live in ${mode}${timeReq ? `, ${timeReq}` : ''}, ${period}, ` +
    `earns ${reward}${cond ? `, provided ${cond}` : ''}. The reward is ${delivery}.` +
    (limits.length ? ` Limits: ${list(limits)}.` : '') +
    hist +
    grace +
    (resume ? ` ${resume}` : '')

  const lines = [
    `Who: ${who}.`,
    `Live kind: ${mode}.`,
    `Time: ${period}${timeReq ? `, ${timeReq}` : ''}.${hist}`,
    `Reward: ${reward}.`,
    cond ? `Conditions: only if ${cond}.${grace}` : 'Conditions: none — time alone earns it.',
    levelRuleLines.length ? `Level rules: ${levelRuleLines.map((x) => `level ${x.no} ${x.rules.join(', ')}`).join('; ')}.` : 'Level rules: none — every level follows the conditions above.',
    resume ? `Restart: ${resume}` : '',
    limits.length ? `Limits: ${list(limits)}.` : 'Limits: none.',
    `Delivery: ${delivery}.`,
    `Tells the host by: ${notify.length ? list(notify) : 'nothing (silent)'}.`,
    `When this version is activated over a running one: ${
      v.change_behavior === 'immediate' ? 'applies at once to periods already in progress' : v.change_behavior === 'new_sessions' ? 'applies from the next live session' : 'applies from the next day/week/month period; periods already started keep the old rule'
    }.`,
  ].filter(Boolean)
  return { verdict, lines }
}

function PolicyText({ v, vocab, userRoles, compact }: { v: Version; vocab: Vocab | null; userRoles: UserRole[]; compact?: boolean }) {
  const { verdict, lines } = useMemo(() => describePolicy(v, vocab, userRoles), [v, vocab, userRoles])
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText([verdict, '', ...lines].join('\n'))
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1500)
    } catch {
      /* clipboard unavailable: text is still on screen */
    }
  }
  return (
    <div style={{ fontSize: compact ? 12 : 13, lineHeight: 1.5 }}>
      <p style={{ margin: '0 0 8px', fontWeight: 600 }}>{verdict}</p>
      {!compact ? (
        <ul style={{ margin: 0, paddingLeft: 18 }}>
          {lines.map((l, i) => (
            <li key={i}>{l}</li>
          ))}
        </ul>
      ) : null}
      <div style={{ marginTop: 8 }}>
        <Button size="sm" variant="ghost" onClick={() => void copy()}>
          {copied ? 'Copied' : 'Copy text'}
        </Button>
      </div>
    </div>
  )
}

type SimAmount = { threshold_ms: number; asset: string; amount: number; blocked?: string }
type SimLadderOut = {
  tier_key?: string
  tier_name?: string
  new_host?: boolean
  rate: number
  asset?: string
  next_tier_name?: string
  next_tier_min?: number
  next_tier_rate?: number
  remaining_diamonds?: number
  cap_hours_today?: number
  capped_hours?: number
  reason?: string
}
type SimOut = {
  verified_ms: number
  thresholds_reached: number[] | null
  amounts: SimAmount[] | null
  next_threshold_ms: number
  reason?: string
  ladder?: SimLadderOut | null
  // Version-level "Max rewards per day": how many reached intervals it refused.
  day_limit_blocked?: number
  max_per_day?: number
}

type SimIn = { role: number; audio_ms: number; video_ms: number; session_ms: number; already_paid_ms: number; received_diamonds: number; account_age_days: number; hours_paid_today: number }

/** What the simulation means, under the raw JSON. */
function simWords(v: Version, sim: SimIn, out: SimOut): string {
  const role = sim.role === 2 ? 'co-host' : 'host'
  const input = v.time_requirement === 'single_session' ? `one session of ${mins(sim.session_ms)}` : `${mins(sim.audio_ms)} audio + ${mins(sim.video_ms)} video`
  if (out.reason === 'ROLE_NOT_ALLOWED') return `A ${role} is not eligible under this rule: nothing is paid.`
  const lad = out.ladder
  if (out.reason === 'MAX_PER_DAY') return `A ${role} with ${input} counts as ${mins(out.verified_ms)} verified, but today's limit of ${out.max_per_day} reward${out.max_per_day === 1 ? '' : 's'} is already used up (${sim.hours_paid_today} paid today): nothing more is paid until tomorrow, however long they stay live.`
  if (out.reason && out.reason !== 'RATE_NOT_UNLOCKED' && out.reason !== 'LADDER_OFF') return `Not paid: ${out.reason}.`
  const paid = (out.amounts || []).filter((a) => !a.blocked).map((a) => `${a.amount} ${a.asset} at ${mins(a.threshold_ms)}`)
  const dayCap = out.day_limit_blocked
    ? ` The daily limit of ${out.max_per_day} reward${out.max_per_day === 1 ? '' : 's'} then stops the remaining ${out.day_limit_blocked} interval${out.day_limit_blocked === 1 ? '' : 's'} — nothing more today, however long they stay live.`
    : ''
  const already = sim.already_paid_ms ? ` (levels up to ${mins(sim.already_paid_ms)} already paid earlier in the period)` : ''
  const next = out.next_threshold_ms > 0 ? ` Next reward at ${mins(out.next_threshold_ms)} — ${mins(Math.max(0, out.next_threshold_ms - out.verified_ms))} more live time.` : ' No further level in this period.'
  let ladWords = ''
  if (lad) {
    if (lad.reason === 'LADDER_OFF') {
      ladWords = ` The level group that would pay these hours is switched OFF — they wait, unpaid, and resume when the switch is turned back on.`
    } else if (lad.reason === 'RATE_NOT_UNLOCKED') {
      ladWords = ` With ${sim.received_diamonds.toLocaleString()} diamonds received and a ${sim.account_age_days}-day-old account, no gift level is unlocked — reached hours wait, unpaid, until the first level's diamonds arrive.`
    } else {
      ladWords = ` With ${sim.received_diamonds.toLocaleString()} diamonds received${lad.new_host ? ` and a ${sim.account_age_days}-day-old account, the new-signup rate applies:` : ','} each paid hour is at ${lad.tier_name} (${lad.rate.toLocaleString()} ${lad.asset || ''}/h)${lad.next_tier_name ? `; ${(lad.remaining_diamonds || 0).toLocaleString()} more diamonds → ${lad.next_tier_name} (${(lad.next_tier_rate || 0).toLocaleString()}/h)` : ''}.`
    }
    if (lad.capped_hours) ladWords += ` ${lad.capped_hours} hour${lad.capped_hours === 1 ? '' : 's'} blocked by the day cap (${sim.hours_paid_today} already paid today, max ${lad.cap_hours_today}) — they pay again tomorrow.`
  }
  if (paid.length === 0) return `A ${role} with ${input} counts as ${mins(out.verified_ms)} verified${already}: nothing is paid.${ladWords}${dayCap}${next}`
  return `A ${role} with ${input} counts as ${mins(out.verified_ms)} verified${already} and receives ${list(paid)}.${ladWords}${dayCap}${next}`
}

// ─── set up by live type ─────────────────────────────────────────────────────

/**
 * One rule = one campaign in the engine (one way of counting time, one ladder
 * of levels). An org that wants "video: 1000 at 1h, 3000 at 2h; audio: only
 * co-hosts, 200 at 30m" therefore needs separate campaigns. This screen lets
 * them write all of it once and creates the campaigns together, each
 * independently readable, pausable and auditable.
 */
type TrackLevel = { minutes: number; host: number; cohost: number | null }
type Track = { key: 'video' | 'audio'; label: string; on: boolean; host: boolean; cohost: boolean; levels: TrackLevel[] }

const freshTracks = (): Track[] => [
  { key: 'video', label: 'Video live', on: true, host: true, cohost: true, levels: [{ minutes: 60, host: 1000, cohost: null }] },
  { key: 'audio', label: 'Audio live', on: false, host: true, cohost: true, levels: [{ minutes: 60, host: 500, cohost: null }] },
]

/** The version this track becomes — the same payload the full editor would send. */
function trackVersion(t: Track, base: { period: string; asset: string; delivery: string }): Version {
  const roles = [t.host ? 1 : 0, t.cohost ? 2 : 0].filter(Boolean)
  const levels: Level[] = t.levels
    .filter((l) => l.minutes > 0)
    .map((l) => {
      // Co-host-only tracks take their amount from the co-host column.
      const amount = t.host ? l.host : (l.cohost ?? l.host)
      const amount_cohost = t.host && t.cohost && l.cohost != null && l.cohost !== l.host ? l.cohost : null
      return { threshold_ms: l.minutes * MIN, asset: base.asset, amount, amount_cohost, label: '' }
    })
  return { ...blank(), eligible_roles: roles, mode_rule: t.key, period_kind: base.period, delivery: base.delivery, claim_rule: 'immediate', levels }
}

function trackProblem(t: Track): string {
  if (!t.host && !t.cohost) return 'choose host, co-host or both'
  if (t.levels.length === 0) return 'add at least one level'
  for (const l of t.levels) {
    if (l.minutes < 1) return 'every level needs at least 1 minute'
    const amount = t.host ? l.host : (l.cohost ?? l.host)
    if (!(amount > 0)) return 'every level needs an amount above 0'
    const seen = t.levels.filter((x) => x.minutes === l.minutes).length
    if (seen > 1) return `two levels at ${l.minutes} minutes`
  }
  return ''
}

function QuickSetup({ vocab, userRoles, onDone }: { vocab: Vocab | null; userRoles: UserRole[]; onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [period, setPeriod] = useState('day')
  const [asset, setAsset] = useState('coins')
  const [delivery, setDelivery] = useState('direct')
  const [activate, setActivate] = useState(false)
  const [tracks, setTracks] = useState<Track[]>(freshTracks)
  const [busy, setBusy] = useState(false)
  const [results, setResults] = useState<{ label: string; ok: boolean; message: string; id?: string }[]>([])

  const setTrack = (k: Track['key'], f: (t: Track) => Track) => setTracks((ts) => ts.map((t) => (t.key === k ? f(t) : t)))
  const enabled = tracks.filter((t) => t.on)
  const problems = enabled.map((t) => ({ t, p: trackProblem(t) })).filter((x) => x.p)
  const canCreate = name.trim().length > 0 && enabled.length > 0 && problems.length === 0 && !busy

  const create = async () => {
    setBusy(true)
    const out: typeof results = []
    for (const t of enabled) {
      const label = `${name.trim()} · ${t.label}${t.host && t.cohost ? '' : t.host ? ' · host' : ' · co-host'}`
      const c = await post<Campaign>('/v1/admin/rewards/campaigns', { name: label.slice(0, 80), description: `Created from “set up by live type”: ${t.label.toLowerCase()} time only.` })
      if (!c.ok) {
        out.push({ label, ok: false, message: c.message })
        continue
      }
      const ver = trackVersion(t, { period, asset, delivery })
      const payload = { ...ver, levels: ver.levels.map((l) => ({ ...l, amount_cohost: l.amount_cohost ?? null })) }
      const v = await post<Version>(`/v1/admin/rewards/campaigns/${c.data.id}/versions`, payload)
      if (!v.ok) {
        out.push({ label, ok: false, message: `Campaign created but the rule was refused: ${v.message}`, id: c.data.id })
        continue
      }
      if (activate) {
        const a = await post(`/v1/admin/rewards/versions/${v.data.id}/activate`, { change_behavior: 'new_periods', reset_progress: false })
        out.push({ label, ok: a.ok, message: a.ok ? 'Created and active.' : `Created as draft; activation failed: ${a.message}`, id: c.data.id })
      } else {
        out.push({ label, ok: true, message: 'Created as a validated draft. Activate it from the campaign page.', id: c.data.id })
      }
    }
    setResults(out)
    setBusy(false)
    onDone()
  }

  if (!open) {
    return (
      <Card title="Set up rewards by live type">
        <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
          Different reward for video than for audio? A reward only for co-hosts in audio rooms? Write every track on one screen; each live type becomes its own campaign so it can be read, paused and audited on its own.
        </p>
        <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>
          Set up by live type
        </Button>
      </Card>
    )
  }

  return (
    <Card title="Set up rewards by live type">
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
        <Field label="Name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. October host rewards" />
        <Select label="Period" value={period} options={['day', 'week', 'month']} onChange={setPeriod} hint="Time adds up across this period, then resets." />
        <Select label="Paid in (wallet currency)" value={asset} options={vocab?.assets || ['coins', 'diamonds', 'beans']} onChange={setAsset} />
        <Select label="Delivery" value={delivery} options={['direct', 'claim']} onChange={setDelivery} hint="direct = added the second it is earned" />
      </div>

      {tracks.map((t) => {
        const problem = t.on ? trackProblem(t) : ''
        return (
          <div key={t.key} style={{ border: '1px solid var(--line)', borderRadius: 'var(--radius-sm)', padding: 12, marginTop: 12, opacity: t.on ? 1 : 0.6 }}>
            <div style={{ display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
              <label style={{ display: 'flex', gap: 6, alignItems: 'center', fontWeight: 600 }}>
                <input type="checkbox" checked={t.on} onChange={(e) => setTrack(t.key, (x) => ({ ...x, on: e.target.checked }))} /> {t.label}
              </label>
              {t.on ? (
                <>
                  <span className="muted" style={{ fontSize: 11 }}>
                    who earns:
                  </span>
                  <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                    <input type="checkbox" checked={t.host} onChange={(e) => setTrack(t.key, (x) => ({ ...x, host: e.target.checked }))} /> Host
                  </label>
                  <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                    <input type="checkbox" checked={t.cohost} onChange={(e) => setTrack(t.key, (x) => ({ ...x, cohost: e.target.checked }))} /> Co-host
                  </label>
                </>
              ) : (
                <span className="muted" style={{ fontSize: 11 }}>
                  no reward for {t.label.toLowerCase()}
                </span>
              )}
            </div>
            {t.on ? (
              <>
                <table className="table" style={{ marginTop: 8 }}>
                  <thead>
                    <tr>
                      <th>After (minutes of {t.key} live)</th>
                      {t.host ? <th>Host gets</th> : null}
                      {t.cohost ? <th>Co-host gets{t.host ? ' (blank = same as host)' : ''}</th> : null}
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {t.levels.map((l, i) => (
                      <tr key={i}>
                        <td>
                          <input className="admin-context-select" type="number" min={1} value={l.minutes} onChange={(e) => setTrack(t.key, (x) => ({ ...x, levels: x.levels.map((y, j) => (j === i ? { ...y, minutes: Math.max(0, Math.round(Number(e.target.value) || 0)) } : y)) }))} />
                        </td>
                        {t.host ? (
                          <td>
                            <input className="admin-context-select" type="number" min={1} value={l.host} onChange={(e) => setTrack(t.key, (x) => ({ ...x, levels: x.levels.map((y, j) => (j === i ? { ...y, host: Number(e.target.value) || 0 } : y)) }))} />
                          </td>
                        ) : null}
                        {t.cohost ? (
                          <td>
                            <input
                              className="admin-context-select"
                              type="number"
                              min={1}
                              placeholder={t.host ? 'same' : ''}
                              value={l.cohost ?? ''}
                              onChange={(e) => setTrack(t.key, (x) => ({ ...x, levels: x.levels.map((y, j) => (j === i ? { ...y, cohost: e.target.value === '' ? null : Number(e.target.value) || 0 } : y)) }))}
                            />
                          </td>
                        ) : null}
                        <td>
                          <Button size="sm" variant="ghost" onClick={() => setTrack(t.key, (x) => ({ ...x, levels: x.levels.filter((_, j) => j !== i) }))}>
                            Remove
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <Button size="sm" variant="ghost" style={{ marginTop: 6 }} onClick={() => setTrack(t.key, (x) => ({ ...x, levels: [...x.levels, { minutes: (x.levels.at(-1)?.minutes || 0) + 60, host: (x.levels.at(-1)?.host || 0) * 2 || 1000, cohost: null }] }))}>
                  + next level
                </Button>
                {problem ? (
                  <div className="form-error" style={{ marginTop: 8 }}>
                    {problem}
                  </div>
                ) : (
                  <div style={{ marginTop: 8, fontSize: 12 }}>
                    <span className="muted">Reads as: </span>
                    {describePolicy(trackVersion(t, { period, asset, delivery }), vocab, userRoles).verdict}
                  </div>
                )}
              </>
            ) : null}
          </div>
        )
      })}

      <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginTop: 14, flexWrap: 'wrap' }}>
        <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <input type="checkbox" checked={activate} onChange={(e) => setActivate(e.target.checked)} /> Activate now (starts paying from the next {period})
        </label>
        <span style={{ flex: 1 }} />
        <Button variant="ghost" size="sm" disabled={busy} onClick={() => setOpen(false)}>
          Close
        </Button>
        <Button disabled={!canCreate} loading={busy} onClick={() => void create()}>
          Create {enabled.length || ''} campaign{enabled.length === 1 ? '' : 's'}
        </Button>
      </div>
      {!name.trim() && enabled.length ? (
        <div className="muted" style={{ fontSize: 11, marginTop: 6 }}>
          Give the set a name; each campaign is named “{'{name}'} · Video live”, “{'{name}'} · Audio live · co-host”…
        </div>
      ) : null}
      {results.length ? (
        <ul style={{ marginTop: 12, paddingLeft: 18, fontSize: 12 }}>
          {results.map((r, i) => (
            <li key={i} style={{ color: r.ok ? undefined : 'var(--danger, #b91c1c)' }}>
              <strong>{r.label}</strong> — {r.message}{' '}
              {r.id ? (
                <Link to={`/live-rewards/campaigns/${r.id}`} style={{ fontSize: 12 }}>
                  Open
                </Link>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}
    </Card>
  )
}

// ─── list ────────────────────────────────────────────────────────────────────

function CampaignList() {
  const navigate = useNavigate()
  const vocab = useVocab()
  const userRoles = useUserRoles()
  const [list, setList] = useState<Campaign[] | null>(null)
  const [error, setError] = useState('')
  const [name, setName] = useState('')
  const [desc, setDesc] = useState('')
  const [busy, setBusy] = useState(false)
  const load = useCallback(async () => {
    const r = await get<{ campaigns: Campaign[] }>('/v1/admin/rewards/campaigns?archived=1')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setList(r.data.campaigns || [])
  }, [])
  useEffect(() => {
    void load()
  }, [load])
  const create = async () => {
    setBusy(true)
    const r = await post<Campaign>('/v1/admin/rewards/campaigns', { name, description: desc })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    navigate(`/live-rewards/campaigns/${r.data.id}/new`)
  }
  return (
    <div>
      <div className="page-head">
        <h1>Reward campaigns</h1>
        <p>A campaign is a name; a version is the rule. Activate a version to start paying; publish a new one to change anything.</p>
      </div>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', margin: '8px 0 16px' }}>
        <Link to="/live-rewards" className="status-pill" style={{ textDecoration: 'none', fontSize: 12 }}>
          ← Live rewards
        </Link>
      </div>
      <EngineBanner />
      {error ? <div className="form-error">{error}</div> : null}
      <QuickSetup vocab={vocab} userRoles={userRoles} onDone={() => void load()} />
      <Card title="New campaign (full editor)">
        <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
          One rule with every option: conditions, limits, claim windows, repeating rewards, rolling windows.
        </p>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr auto', gap: 8, alignItems: 'end' }}>
          <Field label="Name" value={name} onChange={(e) => setName(e.target.value)} />
          <Field label="Description" value={desc} onChange={(e) => setDesc(e.target.value)} />
          <Button size="sm" disabled={busy || name.trim().length === 0} onClick={() => void create()}>
            Create
          </Button>
        </div>
      </Card>
      <Card>
        {!list ? (
          <PageLoader />
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Campaign</th>
                <th>Status</th>
                <th>Active rule</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {list.length === 0 ? (
                <tr>
                  <td colSpan={4} className="muted">
                    No campaigns yet.
                  </td>
                </tr>
              ) : (
                list.map((c) => (
                  <tr key={c.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/live-rewards/campaigns/${c.id}`)}>
                    <td>
                      <strong>{c.name}</strong>
                      <div className="muted" style={{ fontSize: 11 }}>
                        {c.description}
                      </div>
                    </td>
                    <td>
                      <StatusPill status={c.status === 'active' ? 'added' : c.status === 'archived' ? 'expired' : 'pending'} label={c.status} />
                    </td>
                    <td className="muted" style={{ fontSize: 12 }}>
                      {c.active_version ? <RuleSummary v={c.active_version} /> : 'No active version'}
                    </td>
                    <td className="muted">Open →</td>
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

function RuleSummary({ v }: { v: Version }) {
  const roles = v.eligible_roles.map((r) => (r === 1 ? 'host' : 'cohost')).join('/')
  const reward =
    v.reward_mode === 'repeating'
      ? hasLadder(v)
        ? `${weeklyTiers(v).length} gift level${weeklyTiers(v).length === 1 ? '' : 's'}${newHostTier(v) ? ' + new host' : ''} · ${weeklyTiers(v).map((t) => t.amount).join('/')} ${v.repeat_asset} per ${fmtMs(v.repeat_interval_ms, { seconds: false })}`
        : `${v.repeat_amount} ${v.repeat_asset} every ${fmtMs(v.repeat_interval_ms, { seconds: false })}`
      : isMilestone(v)
        ? `${v.levels.map((l) => fmtMs(l.threshold_ms, { seconds: false })).join(', ')} → gift level once per period (${weeklyTiers(v).length} level${weeklyTiers(v).length === 1 ? '' : 's'}${newHostTier(v) ? ' + new host' : ''} · ${weeklyTiers(v).map((t) => t.amount).join('/')} ${weeklyTiers(v)[0]?.asset || ''})`
        : v.levels.map((l) => `${fmtMs(l.threshold_ms, { seconds: false })}→${l.amount} ${l.asset}`).join(', ')
  const ruled = v.reward_mode === 'fixed_levels' ? [...v.levels].sort((a, b) => a.threshold_ms - b.threshold_ms).map((l, i) => (levelHasRules(l) ? i + 1 : 0)).filter(Boolean) : []
  return (
    <span>
      v{v.version_no} · {roles} · {v.mode_rule} · {v.time_requirement} · {v.period_kind}
      {v.period_kind === 'rolling_days' ? ` ${v.rolling_days}` : ''}
      {v.period_kind === 'cycle' ? ` (${cycleWords(v)})` : ''} · {reward} · {v.delivery}
      {v.condition_types?.length ? ` · conditions: ${v.condition_types.join(', ')}` : ''}
      {ruled.length ? ` · level rules on ${list(ruled.map((n) => `L${n}`))}` : ''}
      {v.reset_to_level_no > 1 ? ` · resumes at L${v.reset_to_level_no}` : ''}
    </span>
  )
}

// ─── detail ──────────────────────────────────────────────────────────────────

function CampaignDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const vocab = useVocab()
  const userRoles = useUserRoles()
  const [c, setC] = useState<Campaign | null>(null)
  const [versions, setVersions] = useState<Version[]>([])
  const [error, setError] = useState('')
  const [msg, setMsg] = useState('')
  const [reading, setReading] = useState<string>('')
  const load = useCallback(async () => {
    const r = await get<{ campaign: Campaign; versions: Version[] }>(`/v1/admin/rewards/campaigns/${id}`)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setC(r.data.campaign)
    setVersions(r.data.versions || [])
  }, [id])
  useEffect(() => {
    void load()
  }, [load])

  const setStatus = async (status: string) => {
    if (status === 'archived' && !window.confirm('Archive this campaign? It becomes read-only and stops paying.')) return
    const r = await patch(`/v1/admin/rewards/campaigns/${id}`, { status })
    setMsg(r.message)
    void load()
  }
  const activate = async (v: Version) => {
    const behavior = window.prompt('Change behaviour for periods already in progress: new_periods (default), new_sessions, immediate', v.change_behavior || 'new_periods')
    if (behavior == null) return
    const r = await post(`/v1/admin/rewards/versions/${v.id}/activate`, { change_behavior: behavior.trim() || 'new_periods', reset_progress: false })
    setMsg(r.message)
    void load()
  }
  const remove = async (v: Version) => {
    if (!window.confirm(`Delete draft v${v.version_no}?`)) return
    const r = await del(`/v1/admin/rewards/versions/${v.id}`)
    setMsg(r.message)
    void load()
  }
  // The one runtime switch a version has: weekly levels and the new-signup
  // rate on/off, flippable while ACTIVE (audited, takes effect immediately).
  const flipLadder = async (v: Version, key: 'weekly_on' | 'new_host_on', on: boolean) => {
    const what = key === 'weekly_on' ? 'the weekly levels' : 'the new-signup rate'
    if (!on && !window.confirm(`Switch ${what} OFF now? ${key === 'weekly_on' ? 'Hours outside the new-signup window stop paying until it is switched back on.' : 'Young hosts go by the weekly levels from day one.'}`)) return
    const r = await post(`/v1/admin/rewards/versions/${v.id}/ladder-toggles`, { [key]: on })
    setMsg(r.message)
    void load()
  }

  if (error) return <div className="form-error">{error}</div>
  if (!c) return <PageLoader />
  return (
    <div>
      <div className="page-head" style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <div style={{ flex: 1 }}>
          <h1 style={{ margin: 0 }}>{c.name}</h1>
          <p style={{ margin: 0 }}>{c.description}</p>
        </div>
        <StatusPill status={c.status === 'active' ? 'added' : c.status === 'archived' ? 'expired' : 'pending'} label={c.status} />
        {c.status === 'active' ? (
          <Button size="sm" variant="secondary" onClick={() => void setStatus('disabled')}>
            Disable
          </Button>
        ) : null}
        {c.status !== 'archived' ? (
          <Button size="sm" variant="ghost" onClick={() => void setStatus('archived')}>
            Archive
          </Button>
        ) : null}
        <Button size="sm" onClick={() => navigate(`/live-rewards/campaigns/${id}/new`)} disabled={c.status === 'archived'}>
          New version
        </Button>
      </div>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', margin: '8px 0 16px' }}>
        <Link to="/live-rewards/campaigns" className="status-pill" style={{ textDecoration: 'none', fontSize: 12 }}>
          ← Campaigns
        </Link>
        <Link to={`/live-rewards/payouts?campaign_id=${id}`} className="status-pill" style={{ textDecoration: 'none', fontSize: 12 }}>
          Payouts
        </Link>
        <Link to={`/live-rewards/stats?campaign_id=${id}`} className="status-pill" style={{ textDecoration: 'none', fontSize: 12 }}>
          Daily · Weekly · Monthly
        </Link>
      </div>
      <EngineBanner />
      {msg ? <div className="muted" style={{ marginBottom: 8 }}>{msg}</div> : null}
      {(() => {
        const active = versions.find((x) => x.status === 'active')
        return active ? (
          <Card title={`Policy now in force (v${active.version_no})`}>
            <PolicyText v={active} vocab={vocab} userRoles={userRoles} />
            {hasLadder(active) ? (
              <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid var(--line)', display: 'flex', gap: 18, alignItems: 'center', flexWrap: 'wrap' }}>
                <strong style={{ fontSize: 13 }}>Level switches</strong>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                  <input
                    type="checkbox"
                    checked={active.tiers_weekly_on !== false}
                    disabled={c.status === 'archived'}
                    onChange={(e) => void flipLadder(active, 'weekly_on', e.target.checked)}
                  />
                  Weekly levels
                  {active.tiers_weekly_on === false ? <span className="status-pill" style={{ fontSize: 10, background: '#fee2e2', color: '#991b1b' }}>off</span> : null}
                </label>
                {newHostTier(active) ? (
                  <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                    <input
                      type="checkbox"
                      checked={active.tiers_new_host_on !== false}
                      disabled={c.status === 'archived'}
                      onChange={(e) => void flipLadder(active, 'new_host_on', e.target.checked)}
                    />
                    New-signup rate
                    {active.tiers_new_host_on === false ? <span className="status-pill" style={{ fontSize: 10, background: '#fee2e2', color: '#991b1b' }}>off</span> : null}
                  </label>
                ) : null}
                <span className="muted" style={{ fontSize: 11 }}>
                  Flippable anytime, even while active — no new version. Weekly off: hours outside the new-signup window wait, unpaid, and resume when switched back on. New-signup off: young hosts go by the weekly levels from day one. Every flip is audited.
                </span>
              </div>
            ) : null}
          </Card>
        ) : null
      })()}
      <Card title="Versions">
        {versions.length === 0 ? (
          <p className="muted">No versions yet. Create one to define the rule.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Version</th>
                <th>Rule</th>
                <th>Window</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {versions.map((v) => (
                <tr key={v.id}>
                  <td>
                    <strong>v{v.version_no}</strong>
                    <div>
                      <StatusPill status={v.status === 'active' ? 'added' : v.status === 'retired' ? 'expired' : 'pending'} label={v.status} />
                    </div>
                    {v.note ? <div className="muted" style={{ fontSize: 11 }}>{v.note}</div> : null}
                    <div style={{ marginTop: 4 }}>
                      <Button size="sm" variant="ghost" onClick={() => setReading(reading === v.id ? '' : v.id)}>
                        {reading === v.id ? 'Hide' : 'Read policy'}
                      </Button>
                    </div>
                  </td>
                  <td style={{ fontSize: 12 }}>
                    <RuleSummary v={v} />
                    {reading === v.id ? (
                      <div style={{ marginTop: 8, padding: 10, border: '1px solid var(--line)', borderRadius: 'var(--radius-sm)' }}>
                        <PolicyText v={v} vocab={vocab} userRoles={userRoles} />
                      </div>
                    ) : null}
                    {v.max_total || v.max_per_day || v.max_per_week || v.max_per_month || v.min_interval_ms ? (
                      <div className="muted" style={{ fontSize: 11 }}>
                        limits: {v.max_total ? `total ${v.max_total} ` : ''}
                        {v.max_per_day ? `day ${v.max_per_day} ` : ''}
                        {v.max_per_week ? `week ${v.max_per_week} ` : ''}
                        {v.max_per_month ? `month ${v.max_per_month} ` : ''}
                        {v.min_interval_ms ? `gap ${fmtMs(v.min_interval_ms, { seconds: false })}` : ''}
                      </div>
                    ) : null}
                  </td>
                  <td className="muted" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                    {v.effective_from ? fmtClock(v.effective_from) : '—'}
                    <br />
                    {v.effective_to ? `→ ${fmtClock(v.effective_to)}` : v.status === 'active' ? '→ now' : ''}
                    <div style={{ fontSize: 11 }}>{v.change_behavior}</div>
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {v.status === 'validated' || v.status === 'draft' ? (
                      <>
                        <Button size="sm" variant="secondary" onClick={() => navigate(`/live-rewards/campaigns/${id}/edit/${v.id}`)} disabled={c.status === 'archived'}>
                          Edit
                        </Button>{' '}
                        <Button size="sm" onClick={() => void activate(v)} disabled={c.status === 'archived'}>
                          Activate
                        </Button>{' '}
                        <Button size="sm" variant="ghost" onClick={() => void remove(v)}>
                          Delete
                        </Button>
                      </>
                    ) : (
                      <Button size="sm" variant="ghost" onClick={() => navigate(`/live-rewards/campaigns/${id}/new/${v.id}`)}>
                        Copy to new
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}

// ─── editor ──────────────────────────────────────────────────────────────────

type CondLeaf = { type: string; op: string; window?: string; window_n?: number; value: unknown }
type CondGroup = { all?: CondNode[]; any?: CondNode[] }
type CondNode = CondLeaf | CondGroup

function isGroup(n: CondNode): n is CondGroup {
  return 'all' in n || 'any' in n
}

/**
 * Levels by gifts received (optional). Each paid interval is priced by the
 * highest level whose diamonds-received threshold the host has reached in
 * the current period (diamonds = what the host is credited; the sender pays
 * coins). The new-signup rate is EXCLUSIVE: during the first new_host_days
 * after signup it is the only rate — weekly levels never apply inside the
 * window — and the host moves to the weekly ladder automatically the day
 * the window ends. Each group has its own on/off switch, flippable even on
 * the active version (the campaign page). Stored as `rate_tiers` on the
 * version — no extra rows, the server resolves the level in memory.
 */
function LadderEditor({ v, assets, onChange, set }: { v: Version; assets: string[]; onChange: (tiers: RateTier[]) => void; set: <K extends keyof Version>(k: K, val: Version[K]) => void }) {
  const tiers = v.rate_tiers || []
  const weekly = weeklyTiers(v)
  const nh = newHostTier(v)
  // Milestone (fixed level priced by the ladder): one payout, so per-hour caps do not exist.
  const milestone = v.reward_mode === 'fixed_levels'
  const defaultAsset = v.repeat_asset || v.levels[0]?.asset || assets[0]
  const setTier = (t: RateTier, patch: Partial<RateTier>) => onChange(tiers.map((x) => (x === t ? { ...x, ...patch } : x)))
  const remove = (t: RateTier) => onChange(tiers.filter((x) => x !== t))
  const addWeekly = () => {
    const last = weekly[weekly.length - 1]
    onChange([
      ...tiers,
      { kind: 'weekly', name: `Level ${weekly.length + 1}`, min_received: last ? last.min_received * 2 || 100_000 : 0, amount: last ? last.amount * 2 : 1000, asset: defaultAsset, max_hours_per_day: milestone ? 0 : (last?.max_hours_per_day ?? 2) },
    ])
  }
  const addNewHost = () => onChange([...tiers, { kind: 'new_host', name: 'New host', min_received: 0, new_host_days: 6, amount: weekly[0]?.amount ?? 1000, asset: defaultAsset, max_hours_per_day: milestone ? 0 : (weekly[0]?.max_hours_per_day ?? 2) }])
  const num = (t: RateTier, k: 'min_received' | 'amount' | 'max_hours_per_day' | 'new_host_days', val: string) => setTier(t, { [k]: Math.max(0, Math.round(Number(val) || 0)) } as Partial<RateTier>)
  const cell = { padding: '4px 6px' }
  // Narrow inputs: the whole ladder must be readable without sideways scroll.
  const narrow = { width: 88 }
  const nameW = { width: 110 }
  const periodWord = v.period_kind === 'cycle' ? 'cycle' : v.period_kind === 'rolling_days' ? 'window' : v.period_kind
  const intervalWord = v.repeat_interval_ms ? fmtMs(v.repeat_interval_ms, { seconds: false }) : 'interval'
  const levelWord = v.levels[0]?.threshold_ms ? fmtMs(v.levels[0].threshold_ms, { seconds: false }) : 'the level time'
  return (
    <div style={{ marginTop: 14, borderTop: '1px solid var(--border, #e5e7eb)', paddingTop: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
        <div>
          <strong>{milestone ? 'Reward amount by gift level' : 'Levels by gifts received'}</strong> {milestone ? null : <span className="muted">(optional)</span>}
          <div className="muted" style={{ fontSize: 12, marginTop: 2 }}>
            {milestone ? (
              <>
                When {levelWord} is reached (and every condition holds) the host is paid ONCE this {periodWord}: the amount of the highest level whose diamonds-received threshold they hold at that moment — a Level 7 host gets the Level 7 amount only, not Levels 1–7. Nothing is paid per hour.
                Diamonds received reset with the {periodWord}; the level is worked out in memory from the gift totals the engine already keeps.
              </>
            ) : (
              <>
                Each paid {v.repeat_interval_ms ? fmtMs(v.repeat_interval_ms, { seconds: false }) : 'interval'} pays the rate of the highest level whose diamonds-received threshold the host has reached this {periodWord}
                (diamonds = what the host is credited for gifts; the sender pays coins). Diamonds received reset with the {periodWord}; the level is worked out in memory from the gift totals the engine already keeps, so adding levels adds no load.
              </>
            )}
            {nh ? ` During the first ${nh.new_host_days} days after signup the new-signup ${milestone ? 'level' : 'rate'} is the only one — the weekly levels start automatically the day the window ends.` : ''}
          </div>
          {tiers.length ? (
            <div style={{ display: 'flex', gap: 16, marginTop: 6, fontSize: 13 }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <input type="checkbox" checked={v.tiers_weekly_on !== false} onChange={(e) => set('tiers_weekly_on', e.target.checked)} />
                Weekly levels on
              </label>
              {nh ? (
                <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <input type="checkbox" checked={v.tiers_new_host_on !== false} onChange={(e) => set('tiers_new_host_on', e.target.checked)} />
                  New-signup rate on
                </label>
              ) : null}
              <span className="muted" style={{ fontSize: 11, alignSelf: 'center' }}>
                These two switches can also be flipped later on the campaign page while the version is active — no new version needed.
              </span>
            </div>
          ) : null}
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          {tiers.length === 0 ? (
            <Button size="sm" variant="ghost" onClick={() => onChange(STARTER_LADDER(defaultAsset).map((t) => (milestone ? { ...t, max_hours_per_day: 0 } : t)))}>
              Prefill starter ladder
            </Button>
          ) : null}
          <Button size="sm" variant="ghost" onClick={addWeekly}>
            + Level
          </Button>
          {!nh ? (
            <Button size="sm" variant="ghost" onClick={addNewHost}>
              + New host rate
            </Button>
          ) : null}
        </div>
      </div>
      {tiers.length ? (
        // Compact inputs so all eight columns fit a normal window with no
        // sideways scroll; overflow is only the fallback for tiny screens.
        <div style={{ overflowX: 'auto', marginTop: 8 }}>
        <table className="table" style={{ minWidth: 600 }}>
          <thead>
            <tr style={{ fontSize: 12 }}>
              <th>Level</th>
              <th>Name</th>
              <th>From diamonds received</th>
              <th>{milestone ? 'Reward (host)' : `Pays per ${intervalWord} (host)`}</th>
              <th>Co-host (blank = same)</th>
              <th title="The wallet currency the host is paid in">Paid in</th>
              {milestone ? null : <th title={`Counts paid rewards, not hours: 2 = two ${intervalWord} rewards, then nothing more that day`}>Max rewards / day (0 = no cap)</th>}
              <th />
            </tr>
          </thead>
          <tbody>
            {weekly.map((t, i) => (
              <tr key={`w${i}`}>
                <td style={cell}>L{i + 1}</td>
                <td style={cell}>
                  <input className="admin-context-select" style={nameW} value={t.name} onChange={(e) => setTier(t, { name: e.target.value })} />
                </td>
                <td style={cell}>
                  <input className="admin-context-select" style={narrow} type="number" min={0} value={t.min_received} onChange={(e) => num(t, 'min_received', e.target.value)} />
                </td>
                <td style={cell}>
                  <input className="admin-context-select" style={narrow} type="number" min={1} value={t.amount} onChange={(e) => num(t, 'amount', e.target.value)} />
                </td>
                <td style={cell}>
                  <input className="admin-context-select" style={narrow} type="number" min={0} value={t.amount_cohost ?? ''} onChange={(e) => setTier(t, { amount_cohost: e.target.value === '' ? null : Number(e.target.value) })} />
                </td>
                <td style={cell}>
                  <select className="admin-context-select" value={t.asset} onChange={(e) => setTier(t, { asset: e.target.value })}>
                    {assets.map((a) => (
                      <option key={a}>{a}</option>
                    ))}
                  </select>
                </td>
                {milestone ? null : (
                  <td style={cell}>
                    <input className="admin-context-select" style={{ width: 64 }} type="number" min={0} max={24} value={t.max_hours_per_day} onChange={(e) => num(t, 'max_hours_per_day', e.target.value)} />
                  </td>
                )}
                <td style={cell}>
                  <Button size="sm" variant="ghost" onClick={() => remove(t)}>
                    Remove
                  </Button>
                </td>
              </tr>
            ))}
            {nh ? (
              <tr key="nh" style={{ background: 'var(--surface-2, #f8fafc)' }}>
                <td style={cell}>New</td>
                <td style={cell}>
                  <input className="admin-context-select" style={nameW} value={nh.name} onChange={(e) => setTier(nh, { name: e.target.value })} />
                </td>
                <td style={{ ...cell, whiteSpace: 'nowrap' }}>
                  <span className="muted" style={{ fontSize: 12 }}>first</span>{' '}
                  <input className="admin-context-select" type="number" min={1} max={365} style={{ width: 56 }} value={nh.new_host_days ?? 6} onChange={(e) => num(nh, 'new_host_days', e.target.value)} />{' '}
                  <span className="muted" style={{ fontSize: 12 }}>days after signup</span>
                </td>
                <td style={cell}>
                  <input className="admin-context-select" style={narrow} type="number" min={1} value={nh.amount} onChange={(e) => num(nh, 'amount', e.target.value)} />
                </td>
                <td style={cell}>
                  <input className="admin-context-select" style={narrow} type="number" min={0} value={nh.amount_cohost ?? ''} onChange={(e) => setTier(nh, { amount_cohost: e.target.value === '' ? null : Number(e.target.value) })} />
                </td>
                <td style={cell}>
                  <select className="admin-context-select" value={nh.asset} onChange={(e) => setTier(nh, { asset: e.target.value })}>
                    {assets.map((a) => (
                      <option key={a}>{a}</option>
                    ))}
                  </select>
                </td>
                {milestone ? null : (
                  <td style={cell}>
                    <input className="admin-context-select" style={{ width: 64 }} type="number" min={0} max={24} value={nh.max_hours_per_day} onChange={(e) => num(nh, 'max_hours_per_day', e.target.value)} />
                  </td>
                )}
                <td style={cell}>
                  <Button size="sm" variant="ghost" onClick={() => remove(nh)}>
                    Remove
                  </Button>
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
        </div>
      ) : null}
      {tiers.length && milestone ? (
        <div className="muted" style={{ fontSize: 12, marginTop: 6 }}>
          Reads as: when {levelWord} is reached this {periodWord}, a host holding {weekly[0]?.name || 'the first level'} is paid {weekly[0]?.amount ?? 0} {weekly[0]?.asset || defaultAsset} once
          {weekly[1] ? `; a host who has received ${weekly[1].min_received.toLocaleString()} diamonds this ${periodWord} is paid the ${weekly[1].name} amount (${weekly[1].amount}) instead` : ''}
          {nh ? `. A host in the first ${nh.new_host_days} days after signup is paid ${nh.amount} — the weekly levels take over automatically the day the window ends` : ''}.
          {' '}Add conditions (e.g. Days that reached the target ≥ 5, role, face verified) under Conditions to decide WHEN the level counts as reached.
          {(v.period_kind === 'session' || v.period_kind === 'lifetime') ? ' Levels need a day, week, month, rolling or cycle period.' : ''}
        </div>
      ) : null}
      {tiers.length && !milestone ? (
        <div className="muted" style={{ fontSize: 12, marginTop: 6 }}>
          Reads as: a host at {weekly[0]?.name || 'the first level'} earns {weekly[0]?.amount ?? 0} {weekly[0]?.asset || v.repeat_asset} per paid {v.repeat_interval_ms ? fmtMs(v.repeat_interval_ms, { seconds: false }) : 'interval'}
          {weekly[1] ? `; once ${weekly[1].min_received.toLocaleString()} diamonds are received this ${periodWord}, the next paid hour is at ${weekly[1].name} (${weekly[1].amount})` : ''}
          {nh ? `. A host in the first ${nh.new_host_days} days after signup earns ${nh.amount} and nothing else — the weekly levels take over automatically the day the window ends` : ''}.
          {weekly.some((t) => t.max_hours_per_day) ? ' Hours past a level’s daily cap are shown as waiting for the next day and are not paid.' : ''}
          {(v.period_kind === 'session' || v.period_kind === 'lifetime') ? ' Levels need a day, week, month, rolling or cycle period.' : ''}
        </div>
      ) : null}
    </div>
  )
}

function VersionEditor() {
  const { id = '', fromVersion, editVersion } = useParams()
  const navigate = useNavigate()
  const vocab = useVocab()
  const userRoles = useUserRoles()
  const [v, setV] = useState<Version>(blank())
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  // Edit mode: the draft is rewritten in place under the same version number.
  // Loading is tracked so the form never shows a blank rule for a split second.
  const [loading, setLoading] = useState(Boolean(fromVersion || editVersion))
  // account_age_days defaults to 400 so the simulation shows the regular
  // (non-new-signup) answer unless the operator asks about a young account.
  const [sim, setSim] = useState<SimIn>({ role: 1, audio_ms: 0, video_ms: 45 * MIN, session_ms: 45 * MIN, already_paid_ms: 0, received_diamonds: 0, account_age_days: 400, hours_paid_today: 0 })
  const [simOut, setSimOut] = useState<SimOut | null>(null)
  const [simFor, setSimFor] = useState<Version | null>(null)

  useEffect(() => {
    const srcID = editVersion || fromVersion
    if (!srcID) return
    void (async () => {
      const r = await get<{ versions: Version[] }>(`/v1/admin/rewards/campaigns/${id}`)
      if (!r.ok) {
        setError(r.message)
        setLoading(false)
        return
      }
      const src = r.data.versions.find((x) => x.id === srcID)
      if (!src) {
        setError('Version not found.')
      } else if (editVersion) {
        if (src.status !== 'draft' && src.status !== 'validated') {
          setError(`v${src.version_no} is ${src.status} and cannot be edited — use "Copy to new" instead.`)
        }
        setV({ ...src, rate_tiers: src.rate_tiers || [] })
      } else {
        setV({ ...src, rate_tiers: src.rate_tiers || [], id: '', status: 'draft', effective_from: undefined, effective_to: undefined, note: `Copied from v${src.version_no}` })
      }
      setLoading(false)
    })()
  }, [id, fromVersion, editVersion])
  const editable = !editVersion || v.status === 'draft' || v.status === 'validated'

  const set = <K extends keyof Version>(k: K, val: Version[K]) => setV((p) => ({ ...p, [k]: val }))
  const setLevel = (i: number, patch: Partial<Level>) => setV((p) => ({ ...p, levels: p.levels.map((x, j) => (j === i ? { ...x, ...patch } : x)) }))
  // "Amount from gift level" on fixed levels: shown while the toggle is on or a ladder is already stored.
  const [ladderToggle, setLadderToggle] = useState(false)
  const fixedLadder = v.reward_mode === 'fixed_levels' && (ladderToggle || hasLadder(v))
  const payload = useMemo(
    () => ({
      ...v,
      // Ladder prices each hour of a repeating reward, or the one milestone
      // payout of a fixed level (no hour caps there); keys are server-assigned.
      rate_tiers: (v.rate_tiers || []).map((t) => ({ ...t, amount_cohost: t.amount_cohost ?? null, max_hours_per_day: v.reward_mode === 'fixed_levels' ? 0 : t.max_hours_per_day })),
      levels: v.levels.map((l) => ({
        ...l,
        amount_cohost: l.amount_cohost ?? null,
        conditions: cleanCond(l.conditions),
        days_of_week: l.days_of_week || [],
        days_of_month: l.days_of_month || [],
        active_from: l.active_from || null,
        active_until: l.active_until || null,
      })),
    }),
    [v],
  )

  const validate = async () => {
    setBusy(true)
    const r = await post<{ ok: boolean; normalized: Version; simulation?: SimOut }>(`/v1/admin/rewards/campaigns/${id}/versions/validate`, { ...payload, simulate: sim })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      setOk('')
      return
    }
    setError('')
    setOk('Valid. ' + (r.data.simulation ? 'Simulation below.' : ''))
    setSimOut(r.data.simulation || null)
    // Read the verdict from the server's normalized rule, not the half-typed form.
    setSimFor(r.data.normalized ? { ...v, ...r.data.normalized, levels: r.data.normalized.levels?.length ? r.data.normalized.levels : v.levels } : v)
  }
  const save = async () => {
    setBusy(true)
    const r = editVersion
      ? await put<Version>(`/v1/admin/rewards/versions/${editVersion}`, payload)
      : await post<Version>(`/v1/admin/rewards/campaigns/${id}/versions`, payload)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    navigate(`/live-rewards/campaigns/${id}`)
  }

  if (!vocab || loading) return <PageLoader />
  const roleName = (r: number) => vocab.roles.find((x) => x.id === r)?.name || String(r)
  const minutes = (ms: number) => Math.round(ms / MIN)
  const fromMinutes = (s: string) => Math.max(0, Math.round(Number(s) || 0)) * MIN

  return (
    <div>
      <div className="page-head">
        <h1>{editVersion ? `Edit v${v.version_no || ''}` : 'New version'}</h1>
        <p>
          {editVersion
            ? 'Change levels, checks and the ladder, then Update — the draft keeps its version number. Nothing pays until you activate.'
            : 'Compose the rule; the server validates against its vocabulary. Nothing pays until you activate.'}
        </p>
      </div>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', margin: '8px 0 16px' }}>
        <Link to={`/live-rewards/campaigns/${id}`} className="status-pill" style={{ textDecoration: 'none', fontSize: 12 }}>
          ← Campaign
        </Link>
      </div>
      <EngineBanner />
      {error ? <div className="form-error">{error}</div> : null}
      {ok ? <div className="muted" style={{ marginBottom: 8 }}>{ok}</div> : null}

      <Card title="Who and what counts">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
          <div>
            <div className="muted" style={{ fontSize: 11, marginBottom: 4 }}>
              Eligible roles
            </div>
            {vocab.roles.map((r) => (
              <label key={r.id} style={{ display: 'inline-flex', gap: 6, marginRight: 12, alignItems: 'center' }}>
                <input
                  type="checkbox"
                  checked={v.eligible_roles.includes(r.id)}
                  onChange={(e) => set('eligible_roles', e.target.checked ? [...v.eligible_roles, r.id] : v.eligible_roles.filter((x) => x !== r.id))}
                />
                {r.name}
              </label>
            ))}
          </div>
          <Select
            label="Which live time counts"
            value={v.mode_rule}
            options={vocab.mode_rules}
            onChange={(x) => set('mode_rule', x)}
            hint="audio / video = only that kind; combined = both added together; either = whichever kind reaches first; each = both kinds must reach. One campaign counts time one way — for a different reward per live type, create one campaign per type (Campaigns → Set up by live type)."
          />
          <Select
            label="Time requirement"
            value={v.time_requirement}
            options={vocab.time_requirements}
            onChange={(x) => {
              set('time_requirement', x)
              if (x === 'single_session') set('period_kind', 'session')
              else if (v.period_kind === 'session') set('period_kind', 'day')
            }}
          />
          {v.time_requirement === 'accumulated_with_min_session' ? (
            <Field label="Minimum session (minutes)" type="number" value={minutes(v.min_session_ms)} onChange={(e) => set('min_session_ms', fromMinutes(e.target.value))} />
          ) : null}
          <Select
            label="Period"
            value={v.period_kind}
            options={vocab.period_kinds}
            onChange={(x) => {
              set('period_kind', x)
              if (x === 'session') set('time_requirement', 'single_session')
              else if (v.time_requirement === 'single_session') set('time_requirement', 'accumulated')
            }}
            hint="Reward day/week/month follow the Live Time calendar (timezone + day start). cycle = the campaign's own restart days (weekdays, dates, or every N days)."
          />
          {v.period_kind === 'rolling_days' ? <Field label="Rolling days" type="number" value={v.rolling_days} onChange={(e) => set('rolling_days', Number(e.target.value) || 0)} /> : null}
          {v.period_kind === 'cycle' ? <CycleEditor v={v} set={set} /> : null}
          {v.reward_mode === 'fixed_levels' && v.period_kind !== 'session' && v.period_kind !== 'lifetime' ? (
            <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span className="muted" style={{ fontSize: 11 }}>
                When a period restarts, hosts resume at
              </span>
              <select className="admin-context-select" value={Math.min(v.reset_to_level_no || 1, Math.max(1, v.levels.length))} onChange={(e) => set('reset_to_level_no', Number(e.target.value) || 1)}>
                {levelOrder(v.levels).map((i, n) => (
                  <option key={i} value={n + 1}>
                    {n === 0 ? 'Level 1 (start over — default)' : levelTitle(v.levels, i)}
                  </option>
                ))}
              </select>
              <span className="muted" style={{ fontSize: 11 }}>
                Levels below the chosen one are skipped for hosts the campaign has paid before; a host's first period always starts at level 1. Nothing already paid is touched.
              </span>
            </label>
          ) : null}
          <label style={{ display: 'flex', gap: 6, alignItems: 'center', alignSelf: 'end' }}>
            <input type="checkbox" checked={v.include_historical} onChange={(e) => set('include_historical', e.target.checked)} /> Count historical (backfilled) time
          </label>
        </div>
      </Card>

      <Card title="Reward">
        <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
          {vocab.reward_modes.map((m) => (
            <Button key={m} size="sm" variant={v.reward_mode === m ? 'primary' : 'ghost'} onClick={() => set('reward_mode', m)}>
              {m === 'fixed_levels' ? 'Fixed levels' : 'Repeating'}
            </Button>
          ))}
        </div>
        {v.reward_mode === 'repeating' ? (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
              <Field label="Pays every (minutes of live time)" type="number" value={minutes(v.repeat_interval_ms)} onChange={(e) => set('repeat_interval_ms', fromMinutes(e.target.value))} hint="One reward per interval. Cap how many per day under Limits → Max rewards per day." />
              <Select label="Paid in (wallet currency)" value={v.repeat_asset} options={vocab.assets} onChange={(x) => set('repeat_asset', x)} />
              <Field
                label={hasLadder(v) ? 'Base amount (used only without levels)' : 'Amount (host)'}
                type="number"
                value={v.repeat_amount}
                onChange={(e) => set('repeat_amount', Number(e.target.value) || 0)}
              />
              <Field
                label="Amount (co-host, blank = same)"
                type="number"
                value={v.repeat_amount_cohost ?? ''}
                onChange={(e) => set('repeat_amount_cohost', e.target.value === '' ? null : Number(e.target.value))}
              />
            </div>
            <LadderEditor v={v} assets={vocab.assets} onChange={(tiers) => set('rate_tiers', tiers)} set={set} />
          </>
        ) : (
          <>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, fontSize: 13 }}>
            <input
              type="checkbox"
              checked={fixedLadder}
              onChange={(e) => {
                setLadderToggle(e.target.checked)
                if (!e.target.checked) set('rate_tiers', [])
                else if (v.levels.length > 1) set('levels', v.levels.slice(0, 1))
              }}
            />
            <span>
              Amount comes from the host&apos;s gift level (one payout per period)
              <span className="muted" style={{ marginLeft: 6, fontSize: 12 }}>
                — one level only: its minutes define when the reward is due; the amount is the gift level held at that moment. Hourly campaigns are not affected.
              </span>
            </span>
          </label>
          <table className="table">
            <thead>
              <tr>
                <th>Minutes</th>
                <th title="The wallet currency the host is paid in">Paid in</th>
                <th>Amount (host)</th>
                <th>Amount (co-host)</th>
                <th>Label</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {v.levels.map((l, i) => (
                <tr key={i}>
                  <td>
                    <input className="admin-context-select" type="number" value={minutes(l.threshold_ms)} onChange={(e) => set('levels', v.levels.map((x, j) => (j === i ? { ...x, threshold_ms: fromMinutes(e.target.value) } : x)))} />
                  </td>
                  {fixedLadder ? (
                    <td colSpan={3} className="muted" style={{ fontSize: 12 }}>
                      Paid in / amount come from the gift level below
                    </td>
                  ) : (
                    <>
                      <td>
                        <select className="admin-context-select" value={l.asset} onChange={(e) => set('levels', v.levels.map((x, j) => (j === i ? { ...x, asset: e.target.value } : x)))}>
                          {vocab.assets.map((a) => (
                            <option key={a}>{a}</option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <input className="admin-context-select" type="number" value={l.amount} onChange={(e) => set('levels', v.levels.map((x, j) => (j === i ? { ...x, amount: Number(e.target.value) || 0 } : x)))} />
                      </td>
                      <td>
                        <input
                          className="admin-context-select"
                          type="number"
                          placeholder="same"
                          value={l.amount_cohost ?? ''}
                          onChange={(e) => set('levels', v.levels.map((x, j) => (j === i ? { ...x, amount_cohost: e.target.value === '' ? null : Number(e.target.value) } : x)))}
                        />
                      </td>
                    </>
                  )}
                  <td>
                    <input className="admin-context-select" value={l.label || ''} onChange={(e) => set('levels', v.levels.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))} />
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {levelHasRules(l) ? (
                      <span className="status-pill on" style={{ fontSize: 11, marginRight: 6 }} title="This level has its own rules (see Level rules below)">
                        rules
                      </span>
                    ) : null}
                    <Button size="sm" variant="ghost" onClick={() => set('levels', v.levels.filter((_, j) => j !== i))}>
                      Remove
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {fixedLadder ? <LadderEditor v={v} assets={vocab.assets} onChange={(tiers) => set('rate_tiers', tiers)} set={set} /> : null}
          </>
        )}
        {v.reward_mode === 'fixed_levels' && (!fixedLadder || v.levels.length === 0) ? (
          <Button size="sm" variant="secondary" style={{ marginTop: 8 }} onClick={() => set('levels', [...v.levels, { threshold_ms: (v.levels.at(-1)?.threshold_ms || 0) + 30 * MIN, asset: 'coins', amount: 100, amount_cohost: null, label: '' }])}>
            Add level
          </Button>
        ) : null}
      </Card>

      <Card title="Limits">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
          <Field label="Max total per period (0 = none)" type="number" value={v.max_total} onChange={(e) => set('max_total', Number(e.target.value) || 0)} />
          <Field
            label="Max rewards per day (0 = no cap)"
            type="number"
            value={v.max_per_day}
            onChange={(e) => set('max_per_day', Number(e.target.value) || 0)}
            hint={
              v.reward_mode === 'repeating' && v.repeat_interval_ms
                ? `Counts paid rewards, not hours: 2 = two ${fmtMs(v.repeat_interval_ms, { seconds: false })} rewards, then nothing more that day however long the host stays live. Applies with or without levels.`
                : 'Counts paid rewards in the business day.'
            }
          />
          <Field label="Max per week" type="number" value={v.max_per_week} onChange={(e) => set('max_per_week', Number(e.target.value) || 0)} />
          <Field label="Max per month" type="number" value={v.max_per_month} onChange={(e) => set('max_per_month', Number(e.target.value) || 0)} />
          <Field label="Min gap between rewards (minutes)" type="number" value={minutes(v.min_interval_ms)} onChange={(e) => set('min_interval_ms', fromMinutes(e.target.value))} />
          <Field label="Condition grace after period end (minutes)" type="number" value={minutes(v.qualification_extension_ms)} onChange={(e) => set('qualification_extension_ms', fromMinutes(e.target.value))} hint="Blocked thresholds get this long to satisfy conditions before expiring." />
        </div>
      </Card>

      <Card title="Conditions (optional)">
        <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
          Apply to every level. For a rule that belongs to one level only (what it must receive, which days, how many times) use Level rules below.
        </p>
        <ConditionEditor vocab={vocab} userRoles={userRoles} value={(v.conditions as CondNode) || {}} onChange={(c) => set('conditions', c)} />
      </Card>

      {v.reward_mode === 'fixed_levels' ? <LevelRulesCard v={v} vocab={vocab} userRoles={userRoles} setLevel={setLevel} /> : null}

      <Card title="Delivery and notifications">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
          <Select label="Delivery" value={v.delivery} options={vocab.deliveries} onChange={(x) => set('delivery', x)} hint="direct = added to balance the second it is earned; claim = host taps Claim" />
          {v.delivery === 'claim' ? (
            <>
              <Select label="Claim window" value={v.claim_rule} options={vocab.claim_rules} onChange={(x) => set('claim_rule', x)} />
              {v.claim_rule === 'relative' ? <Field label="Claimable after (minutes)" type="number" value={minutes(v.claim_after_ms)} onChange={(e) => set('claim_after_ms', fromMinutes(e.target.value))} /> : null}
              {v.claim_rule === 'calendar_days' ? (
                <Field
                  label="Days of month (comma-separated)"
                  value={v.claim_days_of_month.join(',')}
                  onChange={(e) =>
                    set(
                      'claim_days_of_month',
                      e.target.value
                        .split(',')
                        .map((s) => Number(s.trim()))
                        .filter((n) => n >= 1 && n <= 31),
                    )
                  }
                />
              ) : null}
              {v.claim_rule === 'fixed_dates' ? (
                <>
                  <Field label="Claim from" type="datetime-local" value={v.claim_from ? v.claim_from.slice(0, 16) : ''} onChange={(e) => set('claim_from', e.target.value ? new Date(e.target.value).toISOString() : null)} />
                  <Field label="Claim until" type="datetime-local" value={v.claim_until ? v.claim_until.slice(0, 16) : ''} onChange={(e) => set('claim_until', e.target.value ? new Date(e.target.value).toISOString() : null)} />
                </>
              ) : null}
              <Field label="Expires after (minutes, 0 = never)" type="number" value={minutes(v.claim_expires_ms)} onChange={(e) => set('claim_expires_ms', fromMinutes(e.target.value))} />
              <Field label="Max claims per day (0 = none)" type="number" value={v.claim_max_per_day} onChange={(e) => set('claim_max_per_day', Number(e.target.value) || 0)} />
            </>
          ) : null}
        </div>
        <div style={{ display: 'flex', gap: 16, marginTop: 10, flexWrap: 'wrap' }}>
          <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            <input type="checkbox" checked={v.notify_push} onChange={(e) => set('notify_push', e.target.checked)} /> Push
          </label>
          <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            <input type="checkbox" checked={v.notify_notice} onChange={(e) => set('notify_notice', e.target.checked)} /> Inbox notice
          </label>
          <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            <input type="checkbox" checked={v.notify_live_toast} onChange={(e) => set('notify_live_toast', e.target.checked)} /> In-live toast
          </label>
        </div>
        <TextArea label="Note (shown in the version list)" value={v.note} onChange={(e) => set('note', e.target.value)} style={{ marginTop: 10 }} />
      </Card>

      <Card title="Simulate">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12 }}>
          <Select label="Role" value={roleName(sim.role)} options={vocab.roles.map((r) => r.name)} onChange={(x) => setSim({ ...sim, role: vocab.roles.find((r) => r.name === x)?.id || 1 })} />
          <Field label="Audio minutes" type="number" value={minutes(sim.audio_ms)} onChange={(e) => setSim({ ...sim, audio_ms: fromMinutes(e.target.value) })} />
          <Field label="Video minutes" type="number" value={minutes(sim.video_ms)} onChange={(e) => setSim({ ...sim, video_ms: fromMinutes(e.target.value) })} />
          <Field label="Best session minutes" type="number" value={minutes(sim.session_ms)} onChange={(e) => setSim({ ...sim, session_ms: fromMinutes(e.target.value) })} />
          <Field label="Already paid up to (minutes)" type="number" value={minutes(sim.already_paid_ms)} onChange={(e) => setSim({ ...sim, already_paid_ms: fromMinutes(e.target.value) })} />
          {hasLadder(v) ? (
            <>
              <Field label="Diamonds received this period" type="number" value={sim.received_diamonds} onChange={(e) => setSim({ ...sim, received_diamonds: Math.max(0, Math.round(Number(e.target.value) || 0)) })} />
              <Field label="Account age (days since signup)" type="number" value={sim.account_age_days} onChange={(e) => setSim({ ...sim, account_age_days: Math.max(0, Math.round(Number(e.target.value) || 0)) })} />
            </>
          ) : null}
          <Field label="Rewards already paid today" type="number" value={sim.hours_paid_today} onChange={(e) => setSim({ ...sim, hours_paid_today: Math.max(0, Math.round(Number(e.target.value) || 0)) })} />
        </div>
        {hasLadder(v) ? (
          <p className="muted" style={{ fontSize: 12, margin: '8px 0 0' }}>
            Diamonds are what the host is credited for gifts (the sender pays coins). Account age decides the new-signup rate; hours already paid today count against each level's daily cap.
          </p>
        ) : null}
        {simOut ? (
          <>
            <pre style={{ fontSize: 12, marginTop: 10 }}>{JSON.stringify(simOut, null, 2)}</pre>
            <div style={{ borderTop: '1px solid var(--line)', paddingTop: 10, marginTop: 4 }}>
              <div className="muted" style={{ fontSize: 11, marginBottom: 4 }}>
                What this means
              </div>
              <p style={{ margin: '0 0 10px', fontSize: 13 }}>{simWords(simFor || v, sim, simOut)}</p>
              <div className="muted" style={{ fontSize: 11, marginBottom: 4 }}>
                The rule that was applied
              </div>
              <PolicyText v={simFor || v} vocab={vocab} userRoles={userRoles} compact />
            </div>
          </>
        ) : null}
      </Card>

      <Card title="Policy in plain words">
        <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
          Updates as you edit. This is what managers and support read to know exactly who gets what, when, and how.
        </p>
        <PolicyText v={v} vocab={vocab} userRoles={userRoles} />
      </Card>

      <div style={{ display: 'flex', gap: 8, margin: '12px 0 32px' }}>
        <Button variant="secondary" disabled={busy} onClick={() => void validate()}>
          Validate & simulate
        </Button>
        <Button disabled={busy || !editable} onClick={() => void save()}>
          {editVersion ? `Update v${v.version_no || ''}` : 'Save draft'}
        </Button>
        {editVersion ? (
          <Button variant="ghost" disabled={busy} onClick={() => navigate(`/live-rewards/campaigns/${id}/new/${editVersion}`)}>
            Save as new version instead
          </Button>
        ) : null}
      </div>
    </div>
  )
}

// ─── restart schedule + level rules editors ──────────────────────────────────

/** Weekday toggle chips, Monday first. */
function DayPick({ value, onChange }: { value: number[]; onChange: (v: number[]) => void }) {
  return (
    <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
      {WEEKDAYS.map((w) => {
        const on = value.includes(w.id)
        return (
          <button
            key={w.id}
            type="button"
            className="status-pill"
            onClick={() => onChange(on ? value.filter((x) => x !== w.id) : [...value, w.id].sort((a, b) => a - b))}
            style={{
              cursor: 'pointer',
              fontSize: 11,
              border: on ? '1px solid var(--accent, #2563eb)' : '1px solid var(--line)',
              background: on ? 'var(--accent-soft, rgba(37,99,235,.1))' : 'transparent',
              fontWeight: on ? 600 : 400,
            }}
            title={w.long}
          >
            {on ? '✓ ' : ''}
            {w.short}
          </button>
        )
      })}
    </div>
  )
}

/** Comma list of days of the month, kept as a clean 1..31 array. */
function DaysOfMonthField({ label, value, onChange, hint }: { label: string; value: number[]; onChange: (v: number[]) => void; hint?: string }) {
  const [text, setText] = useState(value.join(', '))
  useEffect(() => {
    // Keep the box in sync when the list changes elsewhere (copy from a version).
    const clean = parseDayList(text, 1, 31)
    if (clean.join(',') !== value.join(',')) setText(value.join(', '))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])
  return (
    <Field
      label={label}
      value={text}
      placeholder="e.g. 1, 15"
      onChange={(e) => {
        setText(e.target.value)
        onChange(parseDayList(e.target.value, 1, 31))
      }}
      hint={hint}
    />
  )
}

/** The cycle restart schedule: each host's own week, or one of weekdays / days of month / every N days. */
function CycleEditor({ v, set }: { v: Version; set: <K extends keyof Version>(k: K, val: Version[K]) => void }) {
  const kind: 'per_host' | 'weekdays' | 'month_days' | 'every' = v.cycle_per_host ? 'per_host' : v.cycle_every_days > 0 ? 'every' : v.cycle_days_of_month.length > 0 ? 'month_days' : 'weekdays'
  const pick = (k: typeof kind) => {
    if (k === kind) return
    set('cycle_per_host', k === 'per_host')
    set('cycle_weekdays', [])
    set('cycle_days_of_month', [])
    set('cycle_every_days', k === 'every' ? 7 : 0)
    if (k !== 'every') set('cycle_anchor', null)
  }
  return (
    <div style={{ gridColumn: '1 / -1', border: '1px solid var(--line)', borderRadius: 'var(--radius-sm)', padding: 10, display: 'flex', flexDirection: 'column', gap: 8 }}>
      <div className="muted" style={{ fontSize: 11 }}>
        Restart days (Live Time timezone, at the day start). Live time counts from the restart; nothing already paid is touched.
      </div>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {(
          [
            ['per_host', "Each host's own week"],
            ['weekdays', 'Weekdays'],
            ['month_days', 'Days of the month'],
            ['every', 'Every N days'],
          ] as const
        ).map(([k, label]) => (
          <Button key={k} size="sm" variant={kind === k ? 'primary' : 'ghost'} onClick={() => pick(k)}>
            {label}
          </Button>
        ))}
      </div>
      {kind === 'per_host' ? (
        <div className="muted" style={{ fontSize: 12 }}>
          Every host gets their own week: it starts the day their account was created
          {hasLadder(v) && newHostTier(v) ? ` plus the ${newHostTier(v)?.new_host_days ?? 6} new-signup days (the week count begins the day the new-signup rate ends)` : ''}, and restarts every{' '}
          <input
            className="admin-context-select"
            type="number"
            min={2}
            max={366}
            placeholder="7"
            style={{ width: 64 }}
            value={v.cycle_every_days || ''}
            onChange={(e) => set('cycle_every_days', Math.max(0, Math.round(Number(e.target.value) || 0)))}
          />{' '}
          days (blank = 7). A host whose new-signup window ends on a Tuesday restarts Tuesday→Tuesday; another host runs Sunday→Sunday.
        </div>
      ) : null}
      {kind === 'weekdays' ? <DayPick value={v.cycle_weekdays} onChange={(x) => set('cycle_weekdays', x)} /> : null}
      {kind === 'month_days' ? (
        <DaysOfMonthField label="Restart on days" value={v.cycle_days_of_month} onChange={(x) => set('cycle_days_of_month', x)} hint="A day past the month's end (e.g. 31) means the month's last day." />
      ) : null}
      {kind === 'every' ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
          <Field label="Every (days)" type="number" min={2} max={366} value={v.cycle_every_days} onChange={(e) => set('cycle_every_days', Math.max(0, Math.round(Number(e.target.value) || 0)))} />
          <Field label="First restart on" type="date" value={v.cycle_anchor || ''} onChange={(e) => set('cycle_anchor', e.target.value || null)} hint="Cycles repeat every N days before and after this date." />
        </div>
      ) : null}
      <div className="muted" style={{ fontSize: 11 }}>Reads as: restarts {cycleWords(v)}.</div>
    </div>
  )
}

/** One level's own rules: conditions, chain, day schedule, repeat caps. */
function LevelRuleEditor({ v, i, vocab, userRoles, setLevel }: { v: Version; i: number; vocab: Vocab; userRoles: UserRole[]; setLevel: (i: number, patch: Partial<Level>) => void }) {
  const l = v.levels[i]
  const no = levelNoOf(v.levels, i)
  const clear = () =>
    setLevel(i, { conditions: {}, max_total: 0, max_per_day: 0, max_per_week: 0, max_per_month: 0, days_of_week: [], days_of_month: [], active_from: null, active_until: null, requires_previous: false })
  const words = levelRuleWords(l, no, userRoles)
  return (
    <div style={{ border: '1px solid var(--line)', borderRadius: 'var(--radius-sm)', padding: 12, marginTop: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
        <strong style={{ fontSize: 13 }}>{levelTitle(v.levels, i)}</strong>
        <span style={{ flex: 1 }} />
        <Button size="sm" variant="ghost" onClick={clear}>
          Remove rules
        </Button>
      </div>

      <div className="muted" style={{ fontSize: 11, margin: '10px 0 4px' }}>
        Must also satisfy (for this level only)
      </div>
      <ConditionEditor vocab={vocab} userRoles={userRoles} value={(l.conditions as CondNode) || {}} onChange={(c) => setLevel(i, { conditions: c })} />

      {no > 1 ? (
        <label style={{ display: 'flex', gap: 6, alignItems: 'center', marginTop: 10, fontSize: 13 }}>
          <input type="checkbox" checked={!!l.requires_previous} onChange={(e) => setLevel(i, { requires_previous: e.target.checked })} />
          Only after level {no - 1} has paid in the same period
        </label>
      ) : null}

      <div className="muted" style={{ fontSize: 11, margin: '12px 0 4px' }}>
        Days this level can pay (Live Time timezone; leave empty for any day)
      </div>
      <DayPick value={l.days_of_week || []} onChange={(x) => setLevel(i, { days_of_week: x })} />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12, marginTop: 8 }}>
        <DaysOfMonthField label="Days of the month" value={l.days_of_month || []} onChange={(x) => setLevel(i, { days_of_month: x })} />
        <Field label="From date" type="date" value={l.active_from || ''} onChange={(e) => setLevel(i, { active_from: e.target.value || null })} />
        <Field label="Until date" type="date" value={l.active_until || ''} onChange={(e) => setLevel(i, { active_until: e.target.value || null })} />
      </div>

      <div className="muted" style={{ fontSize: 11, margin: '12px 0 4px' }}>
        Repeat at most (0 = no limit). Counted per host from what was actually paid.
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 12 }}>
        <Field label="In total" type="number" min={0} value={l.max_total || 0} onChange={(e) => setLevel(i, { max_total: Math.max(0, Math.round(Number(e.target.value) || 0)) })} />
        <Field label="Per day" type="number" min={0} value={l.max_per_day || 0} onChange={(e) => setLevel(i, { max_per_day: Math.max(0, Math.round(Number(e.target.value) || 0)) })} />
        <Field label="Per week" type="number" min={0} value={l.max_per_week || 0} onChange={(e) => setLevel(i, { max_per_week: Math.max(0, Math.round(Number(e.target.value) || 0)) })} />
        <Field label="Per month" type="number" min={0} value={l.max_per_month || 0} onChange={(e) => setLevel(i, { max_per_month: Math.max(0, Math.round(Number(e.target.value) || 0)) })} />
      </div>

      <p className="muted" style={{ fontSize: 11, marginTop: 10, marginBottom: 0 }}>
        Reads as: level {no} pays {words.length ? words.join('; ') : 'whenever its time is reached (no extra rule yet)'}.
      </p>
    </div>
  )
}

/**
 * Level rules: pick a level from the ladder above and give it rules of its
 * own. Levels without rules behave exactly as before.
 */
function LevelRulesCard({ v, vocab, userRoles, setLevel }: { v: Version; vocab: Vocab; userRoles: UserRole[]; setLevel: (i: number, patch: Partial<Level>) => void }) {
  const [pick, setPick] = useState('')
  const [open, setOpen] = useState<number[]>([])
  const order = levelOrder(v.levels)
  const shown = order.filter((i) => levelHasRules(v.levels[i]) || open.includes(i))
  const free = order.filter((i) => !shown.includes(i))
  const add = () => {
    const i = Number(pick)
    if (!Number.isInteger(i) || i < 0 || i >= v.levels.length) return
    setOpen((o) => (o.includes(i) ? o : [...o, i]))
    setPick('')
  }
  return (
    <Card title="Level rules (optional)">
      <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
        Rules for one level only, on top of the conditions above: what it must receive (e.g. gift coins in the last 7 days), which days it can pay, whether the level below must pay first, and how many times it may repeat. A level with no rules pays on time alone, as before.
      </p>
      {v.levels.length === 0 ? <p className="muted">Add levels in the Reward card first.</p> : null}
      {shown.map((i) => (
        <LevelRuleEditor key={i} v={v} i={i} vocab={vocab} userRoles={userRoles} setLevel={setLevel} />
      ))}
      {free.length ? (
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginTop: shown.length ? 12 : 0 }}>
          <select className="admin-context-select" value={pick} onChange={(e) => setPick(e.target.value)} aria-label="Level">
            <option value="">Choose a level…</option>
            {free.map((i) => (
              <option key={i} value={i}>
                {levelTitle(v.levels, i)}
              </option>
            ))}
          </select>
          <Button size="sm" variant="secondary" disabled={pick === ''} onClick={add}>
            Add rules to this level
          </Button>
        </div>
      ) : null}
    </Card>
  )
}

function Select({ label, value, options, onChange, hint }: { label: string; value: string; options: string[]; onChange: (v: string) => void; hint?: string }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span className="muted" style={{ fontSize: 11 }}>
        {label}
      </span>
      <select className="admin-context-select" value={value} onChange={(e) => onChange(e.target.value)}>
        {options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </select>
      {hint ? (
        <span className="muted" style={{ fontSize: 11 }}>
          {hint}
        </span>
      ) : null}
    </label>
  )
}

// ─── condition tree editor ───────────────────────────────────────────────────

const COND_LABEL: Record<string, string> = {
  SESSION_TIME: 'Single session length',
  LIVE_ROLE: 'Live role (host / co-host)',
  LIVE_MODE: 'Live mode (audio / video)',
  USER_ROLE: 'User role (platform role)',
  ACCOUNT_AGE: 'Account age (days)',
  FACE_VERIFIED: 'Face verified (approved face identity)',
  RECEIVED_GIFT_VALUE: 'Gift coins received',
  RECEIVED_GIFT_COUNT: 'Gifts received',
  UNIQUE_GIFTERS: 'Different gifters',
  SENT_GIFT_VALUE: 'Gift coins sent',
  SENT_GIFT_COUNT: 'Gifts sent',
  UNIQUE_RECIPIENTS: 'Different people gifted',
  QUALIFIED_DAYS: 'Days that reached the target',
}

/** The value a freshly picked condition starts with: true for yes/no rules, 1 for numbers, nothing for sets. */
const condDefaultValue = (s: { numeric: boolean; boolean?: boolean }): CondLeaf['value'] => (s.boolean ? true : s.numeric ? 1 : [])

/** Values a set-valued condition can take: the real roles/modes, never free text. */
function suggestionsFor(type: string, vocab: Vocab, userRoles: UserRole[]): { value: string; label: string }[] | null {
  switch (type) {
    case 'LIVE_ROLE':
      return vocab.roles.map((r) => ({ value: r.name, label: r.name === 'cohost' ? 'Co-host' : r.name === 'host' ? 'Host' : r.name }))
    case 'LIVE_MODE':
      return [
        { value: 'audio', label: 'Audio' },
        { value: 'video', label: 'Video' },
      ]
    case 'USER_ROLE':
      return userRoles.map((r) => ({ value: r.slug, label: r.title }))
    default:
      return null
  }
}

/** Multi-select as toggle chips (op `in`) or a single pick (op `eq`); extra slugs typed by hand stay selectable. */
function MultiPick({ options, value, single, onChange, placeholder }: { options: { value: string; label: string }[]; value: string[]; single: boolean; onChange: (v: string[]) => void; placeholder: string }) {
  const [custom, setCustom] = useState('')
  const known = new Set(options.map((o) => o.value))
  const extras = value.filter((x) => !known.has(x)).map((x) => ({ value: x, label: x }))
  const all = [...options, ...extras]
  if (single) {
    return (
      <select className="admin-context-select" value={value[0] || ''} onChange={(e) => onChange(e.target.value ? [e.target.value] : [])}>
        <option value="">{placeholder}</option>
        {all.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    )
  }
  return (
    <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}>
      {all.length === 0 ? (
        <span className="muted" style={{ fontSize: 11 }}>
          {placeholder}
        </span>
      ) : null}
      {all.map((o) => {
        const on = value.includes(o.value)
        return (
          <button
            key={o.value}
            type="button"
            className="status-pill"
            onClick={() => onChange(on ? value.filter((x) => x !== o.value) : [...value, o.value])}
            style={{
              cursor: 'pointer',
              fontSize: 11,
              border: on ? '1px solid var(--accent, #2563eb)' : '1px solid var(--line)',
              background: on ? 'var(--accent-soft, rgba(37,99,235,.1))' : 'transparent',
              fontWeight: on ? 600 : 400,
            }}
            title={o.value}
          >
            {on ? '✓ ' : ''}
            {o.label}
          </button>
        )
      })}
      <input
        className="admin-context-select"
        style={{ width: 130 }}
        placeholder="other slug…"
        value={custom}
        onChange={(e) => setCustom(e.target.value)}
        onKeyDown={(e) => {
          if (e.key !== 'Enter') return
          e.preventDefault()
          const s = custom.trim()
          if (s && !value.includes(s)) onChange([...value, s])
          setCustom('')
        }}
      />
    </div>
  )
}

function ConditionEditor({ vocab, userRoles, value, onChange }: { vocab: Vocab; userRoles: UserRole[]; value: CondNode; onChange: (c: CondNode) => void }) {
  const empty = !isGroup(value) && !('type' in value)
  if (empty) {
    return (
      <div>
        <p className="muted" style={{ fontSize: 12 }}>
          No conditions: time alone earns the reward.
        </p>
        <Button size="sm" variant="secondary" onClick={() => onChange({ all: [] })}>
          Add conditions
        </Button>
      </div>
    )
  }
  return (
    <div>
      <NodeEditor vocab={vocab} userRoles={userRoles} node={value} onChange={onChange} depth={1} onRemove={() => onChange({})} />
      <p className="muted" style={{ fontSize: 11, marginTop: 8 }}>
        Reads as: {condWords(value, userRoles) || '—'}
      </p>
    </div>
  )
}

function NodeEditor({ vocab, userRoles, node, onChange, depth, onRemove }: { vocab: Vocab; userRoles: UserRole[]; node: CondNode; onChange: (n: CondNode) => void; depth: number; onRemove: () => void }) {
  if (isGroup(node)) {
    const key: 'all' | 'any' = 'all' in node ? 'all' : 'any'
    const children = node[key] || []
    const setChildren = (c: CondNode[]) => onChange({ [key]: c } as CondGroup)
    return (
      <div style={{ border: '1px solid var(--line)', borderRadius: 'var(--radius-sm)', padding: 10, marginTop: 6 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <select className="admin-context-select" value={key} onChange={(e) => onChange({ [e.target.value]: children } as CondGroup)}>
            <option value="all">ALL of</option>
            <option value="any">ANY of</option>
          </select>
          <span style={{ flex: 1 }} />
          <Button size="sm" variant="ghost" onClick={() => setChildren([...children, { type: vocab.conditions[0].type, op: vocab.conditions[0].ops[0], window: 'period', value: condDefaultValue(vocab.conditions[0]) }])}>
            + condition
          </Button>
          {depth < vocab.condition_limits.depth ? (
            <Button size="sm" variant="ghost" onClick={() => setChildren([...children, { all: [] }])}>
              + group
            </Button>
          ) : null}
          <Button size="sm" variant="ghost" onClick={onRemove}>
            Remove
          </Button>
        </div>
        {children.map((c, i) => (
          <NodeEditor key={i} vocab={vocab} userRoles={userRoles} node={c} depth={depth + 1} onChange={(n) => setChildren(children.map((x, j) => (j === i ? n : x)))} onRemove={() => setChildren(children.filter((_, j) => j !== i))} />
        ))}
      </div>
    )
  }
  const spec = vocab.conditions.find((c) => c.type === node.type) || vocab.conditions[0]
  const leaf = node as CondLeaf
  const suggestions = suggestionsFor(leaf.type, vocab, userRoles)
  const values = Array.isArray(leaf.value) ? (leaf.value as string[]) : leaf.value != null && leaf.value !== '' ? [String(leaf.value)] : []
  return (
    <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap', marginTop: 6 }}>
      <select
        className="admin-context-select"
        value={leaf.type}
        onChange={(e) => {
          const s = vocab.conditions.find((c) => c.type === e.target.value)!
          onChange({ type: s.type, op: s.ops[0], window: s.windows[0], value: condDefaultValue(s) })
        }}
      >
        {vocab.conditions.map((c) => (
          <option key={c.type} value={c.type}>
            {COND_LABEL[c.type] || c.type}
          </option>
        ))}
      </select>
      {spec.boolean ? (
        // Yes/no rule: nothing to pick. The value is always true ("must be verified").
        <span className="muted" style={{ fontSize: 12 }}>
          must be approved — a reached reward stays blocked (not added) until the account’s face is verified
        </span>
      ) : (
        <select className="admin-context-select" value={leaf.op} onChange={(e) => onChange({ ...leaf, op: e.target.value, value: e.target.value === 'eq' && Array.isArray(leaf.value) ? (leaf.value as string[]).slice(0, 1) : leaf.value })}>
          {spec.ops.map((o) => (
            <option key={o} value={o}>
              {OP_WORDS[o] || o}
            </option>
          ))}
        </select>
      )}
      {spec.boolean ? null : spec.numeric ? (
        <input className="admin-context-select" type="number" value={Number(leaf.value) || 0} onChange={(e) => onChange({ ...leaf, value: Number(e.target.value) || 0 })} />
      ) : suggestions ? (
        <MultiPick
          options={suggestions}
          value={values}
          single={leaf.op === 'eq'}
          onChange={(vals) => onChange({ ...leaf, value: vals })}
          placeholder={leaf.type === 'USER_ROLE' ? (userRoles.length ? 'pick roles' : 'loading roles…') : 'pick'}
        />
      ) : (
        <input className="admin-context-select" placeholder="comma-separated values" value={values.join(',')} onChange={(e) => onChange({ ...leaf, value: e.target.value.split(',').map((s) => s.trim()).filter(Boolean) })} />
      )}
      {spec.windows.length > 1 ? (
        <select className="admin-context-select" value={leaf.window || spec.windows[0]} onChange={(e) => onChange({ ...leaf, window: e.target.value })}>
          {spec.windows.map((w) => (
            <option key={w}>{w}</option>
          ))}
        </select>
      ) : null}
      {leaf.window === 'rolling_hours' || leaf.window === 'rolling_days' ? (
        <input className="admin-context-select" type="number" placeholder="n" style={{ width: 70 }} value={leaf.window_n || 0} onChange={(e) => onChange({ ...leaf, window_n: Number(e.target.value) || 0 })} />
      ) : null}
      <Button size="sm" variant="ghost" onClick={onRemove}>
        ×
      </Button>
    </div>
  )
}
