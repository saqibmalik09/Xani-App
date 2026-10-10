import { del, get, patch, post, put } from './api'

/**
 * Gifting engine client + copy. Every code the engine emits has English text
 * in GIFTING_TEXT (mirrors the server vocabulary); the UI renders sentences,
 * never raw codes. Vocab from the server wins when loaded.
 */

export type Item = { code: string; text: string }

export type Vocabulary = {
  targets: Item[]
  conditions: Item[]
  operators: Item[]
  fallbacks: Item[]
  activations: Item[]
  reasons: Item[]
  assets: Item[]
  scopes: Item[]
  live_modes: Item[]
  live_roles: Item[]
  room_types: Item[]
}

export type Condition = {
  type: string
  op?: string
  value?: number
  values?: number[]
  texts?: string[]
  from?: string
  to?: string
}

export type RolePair = { sender_roles: number[]; receiver_roles: number[] }

export type Notify = { chat?: boolean; system?: boolean; push_sender?: boolean; push_receiver?: boolean }

export type EligibilityBody = {
  pairs?: RolePair[]
  allow_self?: boolean
  live_modes?: string[]
  live_roles?: string[]
  room_types?: string[]
  conditions?: Condition[]
  max_count?: number
  daily_cap_coins?: number
  hide_when_locked?: boolean
  notify?: Notify
}

export type Allocation = {
  target: string
  label?: string
  bps?: number
  fixed?: number
  asset?: string
  role_id?: number
  depth?: number
  bps_by_depth?: number[]
  fallback?: string
  qualify?: Condition[]
  max_diamonds?: number
}

export type DistributionBody = {
  allocations?: Allocation[]
  remainder?: string
}

export type PolicyVersion = {
  id: string
  policy_id: string
  version_no: number
  status: 'draft' | 'active' | 'retired'
  body: EligibilityBody | DistributionBody
  summary: string
  activation: string
  activated_by?: string
  activated_at?: string
  retired_at?: string
  created_by?: string
  created_at: string
}

export type Policy = {
  id: string
  kind: 'eligibility' | 'distribution' | 'program'
  scope_kind: 'global' | 'category' | 'gift'
  scope_id?: string
  name: string
  description: string
  status: 'draft' | 'active' | 'suspended' | 'retired'
  priority: number
  current_version_id?: string
  created_at: string
  updated_at: string
  current?: PolicyVersion
  version_count?: number
}

export type Brief = { id: string; username?: string; full_name?: string; avatar_url?: string }

export type Totals = { coins: number; value: number; diamonds: number; cashback_coins: number; count: number }

export type GiftRow = {
  id: string
  sender: Brief
  receiver: Brief
  gift_id: string
  gift_title: string
  listing_url?: string
  unit_coins: number
  count: number
  coins: number
  diamonds: number
  cashback_coins?: number
  combo: number
  room_id?: string
  status: number
  at_ms: number
  at: string
}

export type PeerRow = {
  peer: Brief
  coins: number
  diamonds: number
  count: number
  last_at_ms: number
  last_gift_id?: string
  listing_url?: string
  rank: number
}

export type Statement = {
  period: string
  from: string
  to: string
  totals: Totals
  peers: PeerRow[]
  gifts: GiftRow[]
  next_cursor?: string
  sort: string
}

export type Summary = {
  received_today: Totals
  received_week: Totals
  received_month: Totals
  sent_today: Totals
  sent_week: Totals
  sent_month: Totals
  day: string
}

export type LedgerAllocation = {
  id: string
  target: string
  relation: string
  depth: number
  beneficiary_id: string
  beneficiary_role_id: number
  bps: number
  fixed: number
  value: number
  diamonds: number
  coins: number
  idem: string
  inline: boolean
  skipped: boolean
  calc?: Record<string, unknown>
  wallet_entry_id?: string
  settle_state: number
}

export type Participant = { side: number; depth: number; user_id: string; role_id: number; membership_id?: string }

export type Fact = {
  id: string
  created_at: string
  day: string
  room_id?: string
  sender_id: string
  receiver_id: string
  gift_id: string
  gift_title: string
  unit_coins: number
  count: number
  total_coins: number
  combo: number
  idem_key: string
  receiver_diamonds: number
  platform_diamonds: number
  share_diamonds: number
  cashback_coins: number
  status: number
  sender_role_id: number
  receiver_role_id: number
  live_role: number
  live_mode: number
  pk_match_id?: string
  pk_side?: number
  catalog_version: number
  conversion_version: number
  distributable_value: number
  eligibility_version_id?: string
  distribution_version_id?: string
}

export type Detail = {
  gift: GiftRow
  fact: Fact
  allocations: LedgerAllocation[]
  participants: Participant[]
  people: Record<string, Brief>
}

export type ShareRow = LedgerAllocation & { gift_id: string; created_at: string }

export type TreeGift = {
  id: string
  category_id: string
  category_ids: string[]
  title: string
  coin_price: number
  listing_key: string
  animation_key?: string
  status: number
  sort_order: number
  policies: { Eligibility: number; Distribution: number }
}

export type TreeCategory = {
  id: string
  parent_id?: string
  title: string
  status: number
  sort_order: number
  lucky: boolean
  policies: { Eligibility: number; Distribution: number }
  gifts: TreeGift[]
  children: TreeCategory[]
}

export type Tree = { categories: TreeCategory[]; global: { Eligibility: number; Distribution: number }; version: number; rev: number }

export type Conversion = {
  version: number
  coin_value_num: number
  coin_value_den: number
  value_diamond_num: number
  value_diamond_den: number
  note: string
  activated_at?: string
}

export type Health = {
  health: {
    pending_allocations: number
    failed_allocations: number
    outbox_queued: number
    oldest_pending_at?: string
    pool_total: number
    pool_idle: number
    pool_acquired: number
  }
  enabled: boolean
}

export type PlatformTotals = {
  coins: number
  value: number
  receiver_diamonds: number
  platform_diamonds: number
  share_diamonds: number
  cashback_coins: number
  count: number
}

export type Stats = {
  period: string
  from: string
  to: string
  totals: PlatformTotals
  today: PlatformTotals
  series: ({ day: string } & PlatformTotals)[]
}

export type LeafState = { allowed: boolean; hidden?: boolean; reason?: string; text?: string }

export type Simulation = {
  state: LeafState
  value: number
  receiver_diamonds: number
  platform_diamonds: number
  share_diamonds: number
  cashback_coins: number
  rows: LedgerAllocation[]
  eligibility: Record<string, unknown>
  distribution: Record<string, unknown>
  chain: { user_id: string; role_id: number; membership_id: string; depth: number }[]
}

export type Explain = {
  gift_id: string
  scopes: string[]
  eligibility: Record<string, unknown> & { sources: Record<string, string> }
  distribution: Record<string, unknown> & { sources: Record<string, string> }
  conversion: Conversion
}

export type GiftSettings = {
  enabled: boolean
  receiving_visibility: 'everyone' | 'followers' | 'self'
  platform_fallback: string
  platform_user_id: string
  default_notify: Notify
  max_count: number
  chain_depth: number
  rules_refresh_sec: number
  settle_batch: number
  settle_interval_ms: number
  outbox_interval_ms: number
  idem_retention_days: number
  partition_days_ahead: number
  receiver_bps: number
}

export type AuditRow = {
  id: string
  actor_id: string
  action: string
  entity: string
  entity_id: string
  detail?: unknown
  created_at: string
}

type List<T> = { items: T[]; next_cursor?: string }

const qs = (o: Record<string, string | number | undefined>) => {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== '' && v !== 0) p.set(k, String(v))
  const s = p.toString()
  return s ? `?${s}` : ''
}

export const giftingApi = {
  vocab: () => get<Vocabulary>('/v1/admin/gifting/vocab'),
  health: () => get<Health>('/v1/admin/gifting/health'),
  stats: (period: string, from?: string, to?: string) => get<Stats>(`/v1/admin/gifting/stats${qs({ period, from, to })}`),
  tree: () => get<Tree>('/v1/admin/gifting/catalog'),
  setParent: (id: string, parent_id: string) => put<{ ok: boolean }>(`/v1/admin/gifting/categories/${id}/parent`, { parent_id }),
  link: (giftId: string, category_id: string) => post<{ ok: boolean }>(`/v1/admin/gifting/gifts/${giftId}/links`, { category_id }),
  unlink: (giftId: string, categoryId: string) => del<{ ok: boolean }>(`/v1/admin/gifting/gifts/${giftId}/links/${categoryId}`),
  policies: (f: { kind?: string; scope_kind?: string; scope_id?: string; status?: string; q?: string }) =>
    get<List<Policy>>(`/v1/admin/gifting/policies${qs(f)}`),
  policy: (id: string) => get<{ policy: Policy; versions: PolicyVersion[] }>(`/v1/admin/gifting/policies/${id}`),
  validate: (kind: string, body: unknown) => post<{ summary: string; valid: boolean }>('/v1/admin/gifting/policies/validate', { kind, body }),
  create: (b: { kind: string; scope_kind: string; scope_id?: string; name: string; description?: string; priority?: number; body: unknown; activation?: string }) =>
    post<Policy>('/v1/admin/gifting/policies', b),
  updateMeta: (id: string, b: { name: string; description: string; priority: number }) => patch<Policy>(`/v1/admin/gifting/policies/${id}`, b),
  setStatus: (id: string, status: string) => post<Policy>(`/v1/admin/gifting/policies/${id}/status`, { status }),
  newVersion: (id: string, body: unknown, activation: string) => post<PolicyVersion>(`/v1/admin/gifting/policies/${id}/versions`, { body, activation }),
  updateDraft: (versionId: string, body: unknown, activation: string) => put<PolicyVersion>(`/v1/admin/gifting/versions/${versionId}`, { body, activation }),
  activate: (versionId: string) => post<Policy>(`/v1/admin/gifting/versions/${versionId}/activate`),
  simulate: (b: Record<string, unknown>) => post<Simulation>('/v1/admin/gifting/simulate', b),
  explain: (giftId: string) => get<Explain>(`/v1/admin/gifting/explain/${giftId}`),
  conversion: () => get<Conversion>('/v1/admin/gifting/conversion'),
  setConversion: (c: Partial<Conversion>) => post<Conversion>('/v1/admin/gifting/conversion', c),
  records: (f: Record<string, string | number | undefined>) => get<List<GiftRow>>(`/v1/admin/gifting/records${qs(f)}`),
  record: (id: string) => get<Detail>(`/v1/admin/gifting/records/${id}`),
  repair: (id: string) => post<{ requeued: number }>(`/v1/admin/gifting/records/${id}/repair`),
  user: (id: string, period: string, from?: string, to?: string, sort?: string) =>
    get<{ user?: Brief; summary: Summary; received: Statement; sent: Statement; shares: ShareRow[] }>(
      `/v1/admin/gifting/users/${id}${qs({ period, from, to, sort })}`,
    ),
  audit: (f: { entity?: string; entity_id?: string; before_id?: string }) => get<List<AuditRow>>(`/v1/admin/gifting/audit${qs(f)}`),
  settings: () => get<GiftSettings>('/v1/admin/settings/gifting'),
  saveSettings: (b: Partial<GiftSettings>) => put<GiftSettings>('/v1/admin/settings/gifting', b),
}

/** English copy for the dashboard (codes → text). Server vocab overrides. */
export const GIFTING_TEXT: Record<string, string> = {
  // kinds
  eligibility: 'Who can send',
  distribution: 'Where the value goes',
  program: 'Programs',
  // scopes
  global: 'Everywhere',
  category: 'One category (and its sub-categories)',
  gift: 'One gift',
  // statuses
  draft: 'Draft',
  active: 'Active',
  suspended: 'Paused',
  retired: 'Retired',
  // targets
  receiver: 'Receiver',
  sender: 'Sender (cashback)',
  platform: 'Platform',
  direct_upline: "Receiver's direct upline",
  upline_by_role: "Receiver's upline with a role",
  upline_by_depth: "Receiver's upline at a depth",
  any_upline: 'Every upline of the receiver',
  pk_winner: 'PK winner',
  pk_loser: 'PK loser',
  // relations
  self: 'Self',
  direct: 'Direct upline',
  upline: 'Upline',
  pk: 'PK',
  // fallbacks
  'fallback:platform': 'goes to the platform',
  'fallback:receiver': 'goes to the receiver',
  'fallback:skip': 'is not paid',
  // activation
  immediate: 'applies to every new gift immediately',
  new_periods: 'applies from the next period',
  new_sessions: 'applies from the next live session',
  // settle states
  'settle:1': 'Paid instantly',
  'settle:2': 'Queued',
  'settle:3': 'Paid',
  'settle:4': 'Failed',
  'settle:5': 'Not paid (by rule)',
  // fact status
  'fact:1': 'Committed',
  'fact:2': 'Reversed',
  'fact:3': 'Voided',
  // live role / mode
  'lrole:0': '—',
  'lrole:1': 'Host',
  'lrole:2': 'Guest',
  'lrole:3': 'Viewer',
  'mode:0': '—',
  'mode:1': 'Audio',
  'mode:2': 'Video',
  // conditions
  SENDER_ROLE: "Sender's role",
  RECEIVER_ROLE: "Receiver's role",
  UPLINE_ROLE: "Receiver's upline role",
  UPLINE_DEPTH: "Receiver's upline depth",
  SENDER_LEVEL: "Sender's gifting level",
  RECEIVER_LEVEL: "Receiver's gifting level",
  ACCOUNT_AGE: "Sender's account age (days)",
  MIN_GIFT_VALUE: 'Gift value (coins)',
  MIN_COUNT: 'Gift count',
  GIFT_CATEGORY: 'Gift category',
  GIFT_ID: 'Specific gift',
  LIVE_ROLE: "Receiver's seat (host, guest, viewer)",
  LIVE_MODE: 'Live mode (audio, video)',
  ROOM_TYPE: 'Room type (live, pk)',
  PK_SIDE: 'PK side',
  TIME_WINDOW: 'Time of day',
  SENT_TODAY: "Sender's coins sent today",
  RECEIVED_TODAY: "Receiver's diamonds received today",
  gte: 'at least',
  lte: 'at most',
  eq: 'exactly',
  in: 'one of',
  // page copy
  'page.overview': 'Gifting',
  'page.overview.sub': 'Who may gift whom, where the value goes, and every gift ever sent — with the rule that priced it.',
  'page.policies': 'Policies',
  'page.policies.sub': 'A policy is a named rule-set at one scope. Each save is a new version; history never changes.',
  'page.catalog': 'Catalog tree',
  'page.catalog.sub': 'Categories can nest; a gift can live in several categories. Rules on a parent reach every child unless a child overrides a field.',
  'page.records': 'Records',
  'page.records.sub': 'Every gift sent, with its money trail. Nothing here is edited or deleted.',
  'page.users': 'Person',
  'page.settings': 'Gifting engine',
  'inherit.hint': 'Leave a field untouched to inherit it from the wider scope (gift → category → parent category → everywhere → built-in default).',
  'kill.off': 'Gifting is OFF. Sends are refused everywhere until it is turned on.',
}

export function t(code: string | number | undefined, vocab?: Vocabulary | null): string {
  if (code === undefined || code === null) return ''
  const c = String(code)
  if (vocab) {
    for (const list of Object.values(vocab) as Item[][]) {
      const hit = list.find((i) => i.code === c)
      if (hit) return hit.text
    }
  }
  return GIFTING_TEXT[c] ?? c
}

export const pct = (bps: number) => (bps % 100 === 0 ? `${bps / 100}%` : `${(bps / 100).toFixed(2).replace(/\.?0+$/, '')}%`)

export const n = (v: number | undefined) => (v ?? 0).toLocaleString()
