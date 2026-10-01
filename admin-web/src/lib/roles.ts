/** Role engine wire types + helpers. Ids travel as strings (snowflakes). */
import { del, get, patch, post, put } from './api'

export const REACH = { self: 1, direct: 2, descendants: 3, platform: 4 } as const
export const ROLE_STATUS = { draft: 1, active: 2, suspended: 3, retired: 4 } as const
export const MEMBER_STATUS = { active: 2, suspended: 3, revoked: 4, blocked: 5 } as const
export const REQ_KIND = { assign: 1, apply: 2, transfer: 3, revoke: 4, reactivate: 5 } as const
export const REQ_STATUS = { draft: 1, pending: 2, approved: 3, rejected: 4, cancelled: 5, expired: 6 } as const
export const DISPOSITION = { keep: 1, reparent: 2, suspend: 3 } as const

export const reachLabel: Record<number, string> = {
  1: 'Own membership',
  2: 'Direct downline',
  3: 'Whole downline',
  4: 'Whole platform',
}
export const roleStatusLabel: Record<number, string> = { 1: 'Draft', 2: 'Active', 3: 'Suspended', 4: 'Retired' }
export const memberStatusLabel: Record<number, string> = { 2: 'Active', 3: 'Suspended', 4: 'Revoked', 5: 'Blocked' }
export const kindLabel: Record<number, string> = {
  1: 'Assign',
  2: 'Application',
  3: 'Transfer',
  4: 'Removal',
  5: 'Reactivation',
}
export const reqStatusLabel: Record<number, string> = {
  1: 'Draft',
  2: 'Pending',
  3: 'Approved',
  4: 'Rejected',
  5: 'Cancelled',
  6: 'Expired',
}

export type EffectivePermission = { slug: string; reach: number }

export type MembershipContext = {
  membership_id: string
  parent_id?: string
  root_id: string
  role_id: number
  role_slug: string
  role_title: string
  role_code: string
  permissions: EffectivePermission[]
}

export type Perm = { id: number; slug: string; name: string; category: string; is_system?: boolean }

export type RoleBrief = { id: number; code: string; slug: string; title: string; status: number }

export type Policy = {
  approval: { mode: 'parent' | 'platform'; ttl_hours: number }
  recruitment: { self_apply: boolean; referral_apply: boolean }
}

/** Field types a role's application form may use (server vocabulary). */
export type FieldType = 'text' | 'textarea' | 'number' | 'dropdown' | 'country' | 'phone'

export const FIELD_TYPES: { id: FieldType; label: string; hint: string }[] = [
  { id: 'text', label: 'Short text', hint: 'One line — name, ID number, bank account…' },
  { id: 'textarea', label: 'Paragraph', hint: 'Multi-line — address, about you…' },
  { id: 'number', label: 'Number', hint: 'Digits only, with an optional min / max value.' },
  { id: 'dropdown', label: 'Dropdown', hint: 'Pick one of the options you list.' },
  { id: 'country', label: 'Country', hint: 'Country picker (stored as ISO code).' },
  { id: 'phone', label: 'Phone / WhatsApp', hint: 'Phone number with country code.' },
]

/**
 * One input on a role's application form. min/max are character counts for
 * text, value range for numbers, ignored otherwise.
 */
export type FormField = {
  key: string
  label: string
  type: FieldType
  required: boolean
  hint?: string
  min?: number | null
  max?: number | null
  options?: string[]
}

export type ApplicationForm = { role: RoleBrief; fields: FormField[] }

export type RoleView = {
  id: number
  uid: string
  code: string
  slug: string
  name: string
  title: string
  description: string
  scope: number
  status: number
  version: number
  is_system: boolean
  parent_role_id?: number | null
  allow_same_role_child: boolean
  policy: Policy
  form: FormField[]
  updated_at: string
  permissions: { id: number; slug: string; name: string; category: string; reach: number }[]
  child_role_ids: number[]
  child_roles: RoleBrief[]
  parent_role?: RoleBrief
  /** Every role a member may report to (declared parent first). */
  upline_role_ids: number[]
  upline_roles: RoleBrief[]
  aliases: string[]
  member_count: number
  permission_slugs: string[]
}

export type RoleVersion = { role_id: number; version: number; snapshot: unknown; changed_by?: string; created_at: string }

export type Catalog = {
  permissions: Perm[]
  scopes: { id: number; slug: string; name: string }[]
  reaches: { id: number; slug: string; name: string }[]
  dispositions: { id: number; slug: string; name: string }[]
  referral?: { code_length: number }
}

export type Membership = {
  id: string
  code: string
  user_id: string
  role_id: number
  parent_id?: string
  root_id: string
  depth: number
  status: number
  request_id?: string
  approved_by?: string
  approved_at?: string
  ended_at?: string
  ended_reason?: string
  version: number
  created_at: string
  updated_at: string
  username: string
  full_name: string
  avatar_url?: string
  user_state: number
  /** This member's own code — what others use to join under them. */
  referral_code?: string
  /** The upline's code this member joined with. */
  joined_code?: string
  referral_code_id?: string
  tree_depth?: number
  direct_children: number
  role: RoleBrief
}

export type MembershipBrief = {
  id: string
  code: string
  user_id: string
  username: string
  full_name: string
  role: RoleBrief
  status: number
  referral_code?: string
}

export type UserBrief = { id: string; username: string; full_name: string; email?: string; avatar_url?: string; state?: number }

export type RequestView = {
  id: string
  code: string
  kind: number
  status: number
  subject_user_id: string
  role_id: number
  membership_id?: string
  parent_id?: string
  children_only: boolean
  disposition: number
  note: string
  reason: string
  requested_by: string
  decided_by?: string
  decided_at?: string
  expires_at?: string
  version: number
  created_at: string
  updated_at: string
  role: RoleBrief
  subject: UserBrief
  requested_by_user: UserBrief
  decided_by_user?: UserBrief
  parent?: MembershipBrief
  membership?: MembershipBrief
  can_decide: boolean
  can_cancel: boolean
  kind_label: string
  status_label: string
  downline_count?: number
  /** The upline's code this placement was requested with. */
  referral_code?: string
  /** Candidate's answers (keyed by field key) and the form that labels them. */
  form_data?: Record<string, unknown>
  form?: FormField[]
}

export type TransferPreview = {
  membership: MembershipBrief
  from?: MembershipBrief
  to?: MembershipBrief
  children_only: boolean
  moving: number
  direct_moving: number
  max_allowed: number
  blockers: string[]
}

export type SummaryRow = { role: RoleBrief; status: number; count: number }

export type Organisation = {
  user: UserBrief
  memberships: Membership[]
  uplines: Record<string, Membership[]>
}

export type ReferralCodeView = { code: string; membership_id: string; role: RoleBrief; target_roles: RoleBrief[] }

export type AuditEvent = {
  id: string
  actor_user_id?: string
  subject_user_id?: string
  action: string
  entity_type: number
  entity_id: string
  request_id?: string
  before?: unknown
  after?: unknown
  created_at: string
}

export type Page<T> = { items: T[]; next_cursor?: string }

export function qs(params: Record<string, string | number | boolean | undefined | null>): string {
  const parts: string[] = []
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '' || v === 0 || v === false) continue
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
  }
  return parts.length ? `?${parts.join('&')}` : ''
}

export type CodeInfo = { code: string; owner: UserBrief; membership: MembershipBrief; target_roles: RoleBrief[] }

export type PermReachInput = { slug: string; reach: number }

export type CreateRoleBody = {
  title: string
  slug: string
  description: string
  scope: number
  parent_role?: string
  upline_roles?: string[]
  allow_same_role_child: boolean
  policy: Policy
  permissions: PermReachInput[]
  child_roles: string[]
  activate: boolean
}

export type UpdateRoleBody = Partial<{
  title: string
  slug: string
  description: string
  parent_role: string
  allow_same_role_child: boolean
  policy: Policy
}>

export type Delegation = {
  id: string
  from_membership_id: string
  to_membership_id: string
  permission_ids?: number[]
  permissions?: string[]
  reach: number
  expires_at?: string
  created_at: string
}

const enc = (v: string | number) => encodeURIComponent(String(v))

export const rolesApi = {
  catalog: () => get<Catalog>('/v1/roles/catalog'),
  list: (includeRetired = false) => get<Page<RoleView>>(`/v1/roles${qs({ include_retired: includeRetired ? 1 : 0 })}`),
  get: (ref: string | number) => get<RoleView>(`/v1/roles/${enc(ref)}`),
  create: (body: CreateRoleBody) => post<RoleView>('/v1/roles', body),
  update: (ref: string | number, body: UpdateRoleBody) => patch<RoleView>(`/v1/roles/${enc(ref)}`, body),
  setPermissions: (ref: string | number, permissions: PermReachInput[]) =>
    put<RoleView>(`/v1/roles/${enc(ref)}/permissions`, { permissions }),
  setChildren: (ref: string | number, child_roles: string[]) => put<RoleView>(`/v1/roles/${enc(ref)}/children`, { child_roles }),
  setUplines: (ref: string | number, upline_roles: string[]) => put<RoleView>(`/v1/roles/${enc(ref)}/uplines`, { upline_roles }),
  setForm: (ref: string | number, fields: FormField[]) => put<RoleView>(`/v1/roles/${enc(ref)}/form`, { fields }),
  applicationForm: (ref: string | number) => get<ApplicationForm>(`/v1/roles/${enc(ref)}/application-form`),
  setStatus: (ref: string | number, status: number) => post<RoleView>(`/v1/roles/${enc(ref)}/status`, { status }),
  retire: (ref: string | number, body: { members?: 'transfer' | 'revoke' | ''; target_role?: string; reason?: string }) =>
    post<RoleView>(`/v1/roles/${enc(ref)}/retire`, body),
  remove: (ref: string | number) => del<{ deleted: boolean }>(`/v1/roles/${enc(ref)}`),
  versions: (ref: string | number) => get<Page<RoleVersion>>(`/v1/roles/${enc(ref)}/versions`),
  apply: (body: { role?: string; parent_id?: string; referral_code?: string; note?: string; form?: Record<string, unknown> }) =>
    post<RequestView>('/v1/requests/apply', body),
  redeem: (code: string, note: string) => post<RequestView>('/v1/referral/redeem', { code, note }),
  lookupCode: (code: string) => get<CodeInfo>(`/v1/referral/codes/${enc(code.trim())}`),
  delegations: (fromId: string, cursor?: string) =>
    get<Page<Delegation>>(`/v1/memberships/${fromId}/delegations${qs({ cursor, limit: 50 })}`),
  delegate: (fromId: string, body: { to_membership_id: string; permissions: string[]; reach: number; expires_at?: string }) =>
    post<Delegation>(`/v1/memberships/${fromId}/delegations`, body),
  revokeDelegation: (id: string) => del<{ revoked: boolean }>(`/v1/delegations/${id}`),
  members: (ref: string | number, q: string, cursor: string) =>
    get<Page<Membership>>(`/v1/roles/${encodeURIComponent(String(ref))}/members${qs({ q, cursor, limit: 50 })}`),
  inboxCount: () => get<{ pending: number }>('/v1/requests/inbox/count'),
  inbox: (params: { status?: number; kind?: number; role?: string; cursor?: string }) =>
    get<Page<RequestView>>(`/v1/requests/inbox${qs({ ...params, limit: 50 })}`),
  request: (id: string) => get<RequestView>(`/v1/requests/${id}`),
  approve: (id: string, note: string) => post<RequestView>(`/v1/requests/${id}/approve`, { note }),
  reject: (id: string, reason: string) => post<RequestView>(`/v1/requests/${id}/reject`, { reason }),
  cancel: (id: string, reason: string) => post<RequestView>(`/v1/requests/${id}/cancel`, { reason }),
  assign: (body: {
    user_id: string
    role: string
    parent_id?: string
    referral_code?: string
    approve_now?: boolean
    note?: string
    form?: Record<string, string>
  }) => post<RequestView>('/v1/requests/assign', body),
  memberships: (params: { role?: string; status?: number; q?: string; cursor?: string }) =>
    get<Page<Membership>>(`/v1/memberships${qs({ ...params, limit: 50 })}`),
  membership: (id: string) => get<Membership>(`/v1/memberships/${id}`),
  downline: (id: string, params: { depth?: number; role?: string; status?: number; q?: string; cursor?: string }) =>
    get<Page<Membership>>(`/v1/memberships/${id}/downline${qs({ ...params, limit: 50 })}`),
  upline: (id: string) => get<Page<Membership>>(`/v1/memberships/${id}/upline`),
  summary: (id: string) => get<Page<SummaryRow>>(`/v1/memberships/${id}/summary`),
  transition: (id: string, kind: 'suspend' | 'reactivate' | 'block' | 'unblock', reason: string) =>
    post<Membership>(`/v1/memberships/${id}/${kind}`, { reason }),
  transferPreview: (id: string, body: { new_parent_id: string; children_only: boolean }) =>
    post<TransferPreview>(`/v1/memberships/${id}/transfer/preview`, body),
  transfer: (id: string, body: { new_parent_id: string; children_only: boolean; note?: string }) =>
    post<RequestView>(`/v1/memberships/${id}/transfer`, body),
  revoke: (id: string, body: { disposition: number; reason: string }) =>
    post<RequestView>(`/v1/memberships/${id}/revoke`, body),
  reactivationRequest: (id: string, note: string) =>
    post<RequestView>(`/v1/memberships/${id}/reactivation-request`, { note }),
  myMemberships: () => get<Page<Membership>>('/v1/me/memberships'),
  myRequests: (cursor?: string) => get<Page<RequestView>>(`/v1/me/requests${qs({ cursor, limit: 50 })}`),
  myCodes: () => get<Page<ReferralCodeView>>('/v1/me/referral-codes'),
  organisation: (userId: string) => get<Organisation>(`/v1/users/${userId}/organization`),
  userRequests: (userId: string, cursor?: string) =>
    get<Page<RequestView>>(`/v1/users/${userId}/requests${qs({ cursor, limit: 50 })}`),
  audit: (params: { entity_type?: number; entity_id?: string; subject_id?: string; actor_id?: string; cursor?: string }) =>
    get<Page<AuditEvent>>(`/v1/audit/roles${qs({ ...params, limit: 50 })}`),
}

export function personLabel(p: { full_name?: string; username: string }): string {
  return p.full_name?.trim() || `@${p.username}`
}

export function when(iso?: string): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString()
}
