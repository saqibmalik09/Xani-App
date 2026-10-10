import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { Select } from '../ui/Org'
import { ROLE_STATUS, rolesApi, type RoleView } from '../lib/roles'
import {
  giftingApi,
  pct,
  t,
  type Allocation,
  type Condition,
  type DistributionBody,
  type EligibilityBody,
  type Simulation,
  type Tree,
  type TreeCategory,
  type Vocabulary,
} from '../lib/gifting'

/**
 * Visual policy builder. Operators pick scope, who, behaviour and conditions
 * from pickers; the server renders the plain-English sentence as they edit
 * (validate), a dry run shows the money (simulate), and only then can the
 * version be saved or activated. JSON is behind "Advanced" for developers.
 */

export type BuilderValue = {
  kind: 'eligibility' | 'distribution'
  scope_kind: 'global' | 'category' | 'gift'
  scope_id: string
  name: string
  description: string
  priority: number
  activation: string
  body: EligibilityBody | DistributionBody
}

export const emptyBuilder = (kind: BuilderValue['kind']): BuilderValue => ({
  kind,
  scope_kind: 'global',
  scope_id: '',
  name: '',
  description: '',
  priority: 0,
  activation: 'immediate',
  body: kind === 'distribution' ? { allocations: [{ target: 'receiver', bps: 9000, asset: 'diamonds' }], remainder: 'platform' } : {},
})

type Props = {
  value: BuilderValue
  onChange: (v: BuilderValue) => void
  /** Scope + name are fixed when editing an existing policy. */
  lockMeta?: boolean
  vocab: Vocabulary | null
  tree: Tree | null
}

export function flattenTree(tree: Tree | null): { id: string; label: string; depth: number; gifts: { id: string; title: string }[] }[] {
  const out: { id: string; label: string; depth: number; gifts: { id: string; title: string }[] }[] = []
  const walk = (cats: TreeCategory[], depth: number) => {
    for (const c of cats) {
      out.push({ id: c.id, label: `${'— '.repeat(depth)}${c.title}`, depth, gifts: c.gifts.map((g) => ({ id: g.id, title: g.title })) })
      walk(c.children || [], depth + 1)
    }
  }
  walk(tree?.categories || [], 0)
  return out
}

/** Active roles only — the ones a policy may name as a party or an upline. */
export function useRoles() {
  const [roles, setRoles] = useState<RoleView[]>([])
  useEffect(() => {
    void (async () => {
      const r = await rolesApi.list()
      if (r.ok) setRoles((r.data.items ?? []).filter((x) => x.status === ROLE_STATUS.active))
    })()
  }, [])
  return roles
}

function ChipPicker<T extends string | number>({
  options,
  value,
  onChange,
  empty,
}: {
  options: { value: T; label: string }[]
  value: T[]
  onChange: (v: T[]) => void
  empty: string
}) {
  const toggle = (v: T) => (value.includes(v) ? onChange(value.filter((x) => x !== v)) : onChange([...value, v]))
  return (
    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
      {options.map((o) => {
        const on = value.includes(o.value)
        return (
          <button
            key={String(o.value)}
            type="button"
            className="status-pill"
            onClick={() => toggle(o.value)}
            style={{
              cursor: 'pointer',
              background: on ? 'var(--accent-soft)' : 'var(--surface-2, var(--surface))',
              color: on ? 'var(--accent-hover)' : 'var(--ink-soft)',
              border: `1px solid ${on ? 'var(--accent)' : 'var(--line)'}`,
            }}
          >
            {o.label}
          </button>
        )
      })}
      {value.length === 0 ? <span className="muted" style={{ fontSize: 12, alignSelf: 'center' }}>{empty}</span> : null}
    </div>
  )
}

const NUMERIC_CONDS = ['SENDER_LEVEL', 'RECEIVER_LEVEL', 'ACCOUNT_AGE', 'MIN_GIFT_VALUE', 'MIN_COUNT', 'UPLINE_DEPTH', 'SENT_TODAY', 'RECEIVED_TODAY']
const ROLE_CONDS = ['SENDER_ROLE', 'RECEIVER_ROLE', 'UPLINE_ROLE']
const TEXT_CONDS: Record<string, string[]> = { LIVE_ROLE: ['host', 'guest', 'viewer'], LIVE_MODE: ['audio', 'video'], ROOM_TYPE: ['live', 'pk'], PK_SIDE: ['left', 'right'] }

export function ConditionEditor({
  list,
  onChange,
  roles,
  vocab,
  tree,
  allow,
}: {
  list: Condition[]
  onChange: (c: Condition[]) => void
  roles: RoleView[]
  vocab: Vocabulary | null
  tree: Tree | null
  /** Restrict the picker (e.g. qualify lists). */
  allow?: string[]
}) {
  const types = (vocab?.conditions.map((c) => c.code) ?? [...NUMERIC_CONDS, ...ROLE_CONDS, ...Object.keys(TEXT_CONDS), 'GIFT_CATEGORY', 'GIFT_ID', 'TIME_WINDOW']).filter(
    (c) => !allow || allow.includes(c),
  )
  const cats = useMemo(() => flattenTree(tree), [tree])
  const set = (i: number, c: Condition) => onChange(list.map((x, j) => (j === i ? c : x)))
  const add = () => onChange([...list, { type: types[0], op: 'gte', value: 1 }])
  const remove = (i: number) => onChange(list.filter((_, j) => j !== i))
  const defaultsFor = (type: string): Condition => {
    if (NUMERIC_CONDS.includes(type)) return { type, op: 'gte', value: 1 }
    if (ROLE_CONDS.includes(type)) return { type, op: 'in', values: [] }
    if (TEXT_CONDS[type]) return { type, op: 'in', texts: [] }
    if (type === 'TIME_WINDOW') return { type, from: '00:00', to: '23:59' }
    return { type, op: 'in', values: [] }
  }
  return (
    <div style={{ display: 'grid', gap: 8 }}>
      {list.map((c, i) => (
        <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 8, padding: 10, border: '1px solid var(--line)', borderRadius: 8 }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <div style={{ flex: 2, minWidth: 200 }}>
              <Select label="Check" value={c.type} onChange={(e) => set(i, defaultsFor(e.target.value))}>
                {types.map((ty) => (
                  <option key={ty} value={ty}>
                    {t(ty, vocab)}
                  </option>
                ))}
              </Select>
            </div>
            {NUMERIC_CONDS.includes(c.type) ? (
              <>
                <div style={{ width: 140 }}>
                  <Select label="Is" value={c.op || 'gte'} onChange={(e) => set(i, { ...c, op: e.target.value })}>
                    <option value="gte">{t('gte', vocab)}</option>
                    <option value="lte">{t('lte', vocab)}</option>
                    <option value="eq">{t('eq', vocab)}</option>
                  </Select>
                </div>
                <div style={{ width: 140 }}>
                  <Field label="Number" type="number" min={0} value={String(c.value ?? 0)} onChange={(e) => set(i, { ...c, value: Number(e.target.value) })} />
                </div>
              </>
            ) : null}
            {c.type === 'TIME_WINDOW' ? (
              <>
                <div style={{ width: 130 }}>
                  <Field label="From (UTC)" type="time" value={c.from || '00:00'} onChange={(e) => set(i, { ...c, from: e.target.value })} />
                </div>
                <div style={{ width: 130 }}>
                  <Field label="To (UTC)" type="time" value={c.to || '23:59'} onChange={(e) => set(i, { ...c, to: e.target.value })} />
                </div>
              </>
            ) : null}
            <Button type="button" size="sm" variant="ghost" onClick={() => remove(i)}>
              Remove
            </Button>
          </div>
          {ROLE_CONDS.includes(c.type) ? (
            <ChipPicker
              options={roles.map((r) => ({ value: r.id, label: r.title }))}
              value={c.values || []}
              onChange={(values) => set(i, { ...c, op: 'in', values })}
              empty="Pick at least one role."
            />
          ) : null}
          {TEXT_CONDS[c.type] ? (
            <ChipPicker options={TEXT_CONDS[c.type].map((x) => ({ value: x, label: x }))} value={c.texts || []} onChange={(texts) => set(i, { ...c, op: 'in', texts })} empty="Pick at least one." />
          ) : null}
          {c.type === 'GIFT_CATEGORY' ? (
            <ChipPicker options={cats.map((x) => ({ value: Number(x.id), label: x.label.replace(/— /g, '') }))} value={c.values || []} onChange={(values) => set(i, { ...c, op: 'in', values })} empty="Pick at least one category." />
          ) : null}
          {c.type === 'GIFT_ID' ? (
            <ChipPicker
              options={cats.flatMap((x) => x.gifts.map((g) => ({ value: Number(g.id), label: g.title })))}
              value={c.values || []}
              onChange={(values) => set(i, { ...c, op: 'in', values })}
              empty="Pick at least one gift."
            />
          ) : null}
        </div>
      ))}
      <div>
        <Button type="button" size="sm" variant="ghost" onClick={add}>
          + Add a check
        </Button>
      </div>
    </div>
  )
}

export function PolicyBuilder({ value, onChange, lockMeta, vocab, tree }: Props) {
  const roles = useRoles()
  const cats = useMemo(() => flattenTree(tree), [tree])
  const [summary, setSummary] = useState('')
  const [valid, setValid] = useState<boolean | null>(null)
  const [problem, setProblem] = useState('')
  const [advanced, setAdvanced] = useState(false)
  const [json, setJson] = useState('')
  const timer = useRef<number | null>(null)

  const body = value.body
  const setBody = (b: EligibilityBody | DistributionBody) => onChange({ ...value, body: b })

  // Live validation + summary, debounced to the server (cheap: no DB write).
  useEffect(() => {
    if (timer.current) window.clearTimeout(timer.current)
    timer.current = window.setTimeout(async () => {
      const r = await giftingApi.validate(value.kind, value.body)
      if (r.ok) {
        setSummary(r.data.summary)
        setValid(true)
        setProblem('')
      } else {
        setValid(false)
        setProblem(r.message)
      }
    }, 350)
    return () => {
      if (timer.current) window.clearTimeout(timer.current)
    }
  }, [value.kind, value.body])

  useEffect(() => {
    if (advanced) setJson(JSON.stringify(value.body, null, 2))
  }, [advanced, value.body])

  const applyJson = () => {
    try {
      setBody(JSON.parse(json))
      setProblem('')
    } catch (e) {
      setProblem(`JSON: ${(e as Error).message}`)
    }
  }

  const scopeOptions = value.scope_kind === 'category' ? cats.map((c) => ({ id: c.id, label: c.label })) : value.scope_kind === 'gift' ? cats.flatMap((c) => c.gifts.map((g) => ({ id: g.id, label: `${g.title} · ${c.label.replace(/— /g, '')}` }))) : []

  return (
    <div style={{ display: 'grid', gap: 12 }}>
      <Card title="1 · Where does this apply?">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
          <Select label="Scope" value={value.scope_kind} disabled={lockMeta} onChange={(e) => onChange({ ...value, scope_kind: e.target.value as BuilderValue['scope_kind'], scope_id: '' })}>
            {(vocab?.scopes ?? [{ code: 'global', text: t('global') }, { code: 'category', text: t('category') }, { code: 'gift', text: t('gift') }]).map((s) => (
              <option key={s.code} value={s.code}>
                {s.text}
              </option>
            ))}
          </Select>
          {value.scope_kind !== 'global' ? (
            <Select label={value.scope_kind === 'gift' ? 'Gift' : 'Category'} value={value.scope_id} disabled={lockMeta} onChange={(e) => onChange({ ...value, scope_id: e.target.value })}>
              <option value="">Choose…</option>
              {scopeOptions.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.label}
                </option>
              ))}
            </Select>
          ) : null}
          <Field label="Name" value={value.name} disabled={lockMeta} onChange={(e) => onChange({ ...value, name: e.target.value })} placeholder="e.g. Luxury gifts · agency 10%" />
          <Field label="Priority" type="number" value={String(value.priority)} disabled={lockMeta} onChange={(e) => onChange({ ...value, priority: Number(e.target.value) })} hint="Higher wins when two policies share a scope." />
        </div>
        <div style={{ marginTop: 10 }}>
          <Field label="Description (internal)" value={value.description} disabled={lockMeta} onChange={(e) => onChange({ ...value, description: e.target.value })} />
        </div>
        <p className="muted" style={{ fontSize: 12, marginBottom: 0 }}>
          {t('inherit.hint')}
        </p>
      </Card>

      {value.kind === 'eligibility' ? (
        <EligibilityCards body={body as EligibilityBody} onChange={setBody} roles={roles} vocab={vocab} tree={tree} />
      ) : (
        <DistributionCards body={body as DistributionBody} onChange={setBody} roles={roles} vocab={vocab} tree={tree} />
      )}

      <Card title="Activation">
        <Select label="When a version is activated it" value={value.activation} onChange={(e) => onChange({ ...value, activation: e.target.value })}>
          {(vocab?.activations ?? [{ code: 'immediate', text: t('immediate') }]).map((a) => (
            <option key={a.code} value={a.code}>
              {a.text}
            </option>
          ))}
        </Select>
      </Card>

      <Card title="In plain English">
        {valid === false ? <div className="form-error">{problem}</div> : null}
        <p style={{ margin: 0, fontSize: 15, lineHeight: 1.5 }}>{summary || <span className="muted">Checking…</span>}</p>
        <p className="muted" style={{ fontSize: 12, marginBottom: 0 }}>
          This sentence is saved with the version and shown in history and on the app, so a question about any gift has one answer.
        </p>
      </Card>

      <Simulator value={value} vocab={vocab} tree={tree} />

      <div>
        <Button type="button" size="sm" variant="ghost" onClick={() => setAdvanced(!advanced)}>
          {advanced ? 'Hide' : 'Show'} advanced (JSON)
        </Button>
        {advanced ? (
          <div style={{ marginTop: 8 }}>
            <textarea className="org-select" rows={14} style={{ width: '100%', fontFamily: 'monospace', fontSize: 12 }} value={json} onChange={(e) => setJson(e.target.value)} />
            <div style={{ marginTop: 6 }}>
              <Button type="button" size="sm" onClick={applyJson}>
                Apply JSON
              </Button>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}

export function builderIsValid(v: BuilderValue): string {
  if (!v.name.trim()) return 'Give the policy a name.'
  if (v.scope_kind !== 'global' && !v.scope_id) return `Choose the ${v.scope_kind}.`
  return ''
}

// ─── Eligibility cards ───────────────────────────────────────────────────────

function Tri({ label, value, onChange, hint }: { label: string; value: boolean | undefined; onChange: (v: boolean | undefined) => void; hint?: string }) {
  return (
    <Select label={label} value={value === undefined ? 'inherit' : value ? 'yes' : 'no'} onChange={(e) => onChange(e.target.value === 'inherit' ? undefined : e.target.value === 'yes')} hint={hint}>
      <option value="inherit">Inherit</option>
      <option value="yes">Yes</option>
      <option value="no">No</option>
    </Select>
  )
}

function EligibilityCards({ body, onChange, roles, vocab, tree }: { body: EligibilityBody; onChange: (b: EligibilityBody) => void; roles: RoleView[]; vocab: Vocabulary | null; tree: Tree | null }) {
  const roleOpts = roles.map((r) => ({ value: r.id, label: r.title }))
  const pairs = body.pairs
  const setField = <K extends keyof EligibilityBody>(k: K, v: EligibilityBody[K]) => {
    const next = { ...body }
    if (v === undefined) delete next[k]
    else next[k] = v
    onChange(next)
  }
  return (
    <>
      <Card title="2 · Who may send it to whom?">
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 10 }}>
          <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13 }}>
            <input type="checkbox" checked={pairs !== undefined} onChange={(e) => setField('pairs', e.target.checked ? [] : undefined)} />
            Set role pairs here (otherwise inherit)
          </label>
        </div>
        {pairs !== undefined ? (
          <div style={{ display: 'grid', gap: 10 }}>
            {pairs.length === 0 ? <p className="muted" style={{ margin: 0, fontSize: 12 }}>No pairs = anyone can send to anyone.</p> : null}
            {pairs.map((p, i) => (
              <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr auto 1fr auto', gap: 10, alignItems: 'start', padding: 10, border: '1px solid var(--line)', borderRadius: 8 }}>
                <div>
                  <div className="muted" style={{ fontSize: 12, marginBottom: 6 }}>
                    Sender holds any of
                  </div>
                  <ChipPicker options={roleOpts} value={p.sender_roles} onChange={(sender_roles) => setField('pairs', pairs.map((x, j) => (j === i ? { ...x, sender_roles } : x)))} empty="anyone" />
                </div>
                <div style={{ alignSelf: 'center', fontSize: 20 }}>→</div>
                <div>
                  <div className="muted" style={{ fontSize: 12, marginBottom: 6 }}>
                    Receiver holds any of
                  </div>
                  <ChipPicker options={roleOpts} value={p.receiver_roles} onChange={(receiver_roles) => setField('pairs', pairs.map((x, j) => (j === i ? { ...x, receiver_roles } : x)))} empty="anyone" />
                </div>
                <Button type="button" size="sm" variant="ghost" onClick={() => setField('pairs', pairs.filter((_, j) => j !== i))}>
                  Remove
                </Button>
              </div>
            ))}
            <div>
              <Button type="button" size="sm" variant="ghost" onClick={() => setField('pairs', [...pairs, { sender_roles: [], receiver_roles: [] }])}>
                + Add a pair
              </Button>
            </div>
          </div>
        ) : null}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12, marginTop: 12 }}>
          <Tri label="Can send to yourself" value={body.allow_self} onChange={(v) => setField('allow_self', v)} />
          <Tri label="Hide from people who cannot send it" value={body.hide_when_locked} onChange={(v) => setField('hide_when_locked', v)} hint="No: show locked with the reason." />
        </div>
      </Card>

      <Card title="3 · Where and when?">
        <div style={{ display: 'grid', gap: 12 }}>
          <ListField label="Live modes" options={(vocab?.live_modes ?? []).map((x) => ({ value: x.code, label: x.text }))} value={body.live_modes} onChange={(v) => setField('live_modes', v)} />
          <ListField label="Receiver's seat" options={(vocab?.live_roles ?? []).map((x) => ({ value: x.code, label: x.text }))} value={body.live_roles} onChange={(v) => setField('live_roles', v)} />
          <ListField label="Room type" options={(vocab?.room_types ?? []).map((x) => ({ value: x.code, label: x.text }))} value={body.room_types} onChange={(v) => setField('room_types', v)} />
        </div>
      </Card>

      <Card title="4 · Limits">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
          <OptNum label="Max at once" value={body.max_count} onChange={(v) => setField('max_count', v)} min={1} max={9999} />
          <OptNum label="Daily cap per sender (coins)" value={body.daily_cap_coins} onChange={(v) => setField('daily_cap_coins', v)} min={0} hint="0 = no cap." />
        </div>
      </Card>

      <Card title="5 · Extra checks (all must pass)">
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 10 }}>
          <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13 }}>
            <input type="checkbox" checked={body.conditions !== undefined} onChange={(e) => setField('conditions', e.target.checked ? [] : undefined)} />
            Set checks here (otherwise inherit)
          </label>
        </div>
        {body.conditions !== undefined ? <ConditionEditor list={body.conditions} onChange={(c) => setField('conditions', c)} roles={roles} vocab={vocab} tree={tree} /> : null}
      </Card>

      <Card title="6 · Announce">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
          {(['chat', 'system', 'push_sender', 'push_receiver'] as const).map((k) => (
            <Tri
              key={k}
              label={{ chat: 'In the live chat', system: 'System notice', push_sender: 'Push to sender', push_receiver: 'Push to receiver' }[k]}
              value={body.notify?.[k]}
              onChange={(v) => {
                const next = { ...(body.notify || {}) }
                if (v === undefined) delete next[k]
                else next[k] = v
                setField('notify', Object.keys(next).length ? next : undefined)
              }}
            />
          ))}
        </div>
      </Card>
    </>
  )
}

function ListField({ label, options, value, onChange }: { label: string; options: { value: string; label: string }[]; value: string[] | undefined; onChange: (v: string[] | undefined) => void }) {
  return (
    <div>
      <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)', marginBottom: 6 }}>
        <input type="checkbox" checked={value !== undefined} onChange={(e) => onChange(e.target.checked ? [] : undefined)} />
        {label} {value === undefined ? <span className="muted" style={{ fontWeight: 400 }}>· inherit</span> : null}
      </label>
      {value !== undefined ? <ChipPicker options={options} value={value} onChange={onChange} empty="Any." /> : null}
    </div>
  )
}

function OptNum({ label, value, onChange, min, max, hint }: { label: string; value: number | undefined; onChange: (v: number | undefined) => void; min?: number; max?: number; hint?: string }) {
  return (
    <div>
      <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)', marginBottom: 6 }}>
        <input type="checkbox" checked={value !== undefined} onChange={(e) => onChange(e.target.checked ? (min ?? 0) : undefined)} />
        {label} {value === undefined ? <span className="muted" style={{ fontWeight: 400 }}>· inherit</span> : null}
      </label>
      {value !== undefined ? <Field label="" type="number" min={min} max={max} value={String(value)} onChange={(e) => onChange(Number(e.target.value))} hint={hint} /> : null}
    </div>
  )
}

// ─── Distribution cards ──────────────────────────────────────────────────────

function DistributionCards({ body, onChange, roles, vocab, tree }: { body: DistributionBody; onChange: (b: DistributionBody) => void; roles: RoleView[]; vocab: Vocabulary | null; tree: Tree | null }) {
  const allocs = body.allocations
  const setAllocs = (a: Allocation[] | undefined) => {
    const next = { ...body }
    if (a === undefined) delete next.allocations
    else next.allocations = a
    onChange(next)
  }
  const total = (allocs || []).reduce((s, a) => s + (a.target === 'any_upline' ? (a.bps_by_depth || []).reduce((x, y) => x + y, 0) : a.bps || 0), 0)
  const targets = vocab?.targets ?? []
  return (
    <>
      <Card title="2 · Where does the value go?">
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 10 }}>
          <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13 }}>
            <input type="checkbox" checked={allocs !== undefined} onChange={(e) => setAllocs(e.target.checked ? [{ target: 'receiver', bps: 9000, asset: 'diamonds' }] : undefined)} />
            Set the split here (otherwise inherit)
          </label>
          {allocs !== undefined ? (
            <span className="status-pill" style={{ marginLeft: 'auto', background: total > 10000 ? 'var(--danger)' : 'var(--accent-soft)', color: total > 10000 ? '#fff' : 'var(--accent-hover)' }}>
              {pct(total)} allocated · {pct(Math.max(0, 10000 - total))} remainder
            </span>
          ) : null}
        </div>
        {allocs !== undefined ? (
          <div style={{ display: 'grid', gap: 10 }}>
            {allocs.map((a, i) => (
              <AllocationCard key={i} a={a} roles={roles} vocab={vocab} tree={tree} onChange={(x) => setAllocs(allocs.map((y, j) => (j === i ? x : y)))} onRemove={() => setAllocs(allocs.filter((_, j) => j !== i))} />
            ))}
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {targets.map((tg) => (
                <Button key={tg.code} type="button" size="sm" variant="ghost" onClick={() => setAllocs([...allocs, defaultAlloc(tg.code)])}>
                  + {tg.text}
                </Button>
              ))}
            </div>
          </div>
        ) : null}
      </Card>
      <Card title="3 · Whatever is left">
        <Select label="Remainder (rounding and unallocated value)" value={body.remainder ?? 'inherit'} onChange={(e) => onChange(e.target.value === 'inherit' ? { ...body, remainder: undefined } : { ...body, remainder: e.target.value })}>
          <option value="inherit">Inherit</option>
          <option value="platform">{t('fallback:platform', vocab)}</option>
          <option value="receiver">{t('fallback:receiver', vocab)}</option>
        </Select>
      </Card>
    </>
  )
}

function defaultAlloc(target: string): Allocation {
  switch (target) {
    case 'sender':
      return { target, bps: 100, asset: 'coins' }
    case 'any_upline':
      return { target, bps_by_depth: [300, 200, 100], fallback: 'platform' }
    case 'upline_by_role':
      return { target, bps: 500, role_id: 0, fallback: 'platform' }
    case 'upline_by_depth':
      return { target, bps: 500, depth: 1, fallback: 'platform' }
    case 'receiver':
      return { target, bps: 9000, asset: 'diamonds' }
    default:
      return { target, bps: 500, fallback: 'platform' }
  }
}

function AllocationCard({ a, onChange, onRemove, roles, vocab, tree }: { a: Allocation; onChange: (a: Allocation) => void; onRemove: () => void; roles: RoleView[]; vocab: Vocabulary | null; tree: Tree | null }) {
  const isUpline = ['direct_upline', 'upline_by_role', 'upline_by_depth', 'any_upline', 'pk_winner', 'pk_loser'].includes(a.target)
  const mode = a.fixed && a.fixed > 0 ? 'fixed' : 'bps'
  return (
    <div style={{ padding: 12, border: '1px solid var(--line)', borderRadius: 8, display: 'grid', gap: 10 }}>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
        <strong>{t(a.target, vocab)}</strong>
        <span className="muted" style={{ fontSize: 12 }}>
          {a.target === 'sender' ? 'paid in coins' : 'paid in diamonds'}
        </span>
        <Button type="button" size="sm" variant="ghost" style={{ marginLeft: 'auto' }} onClick={onRemove}>
          Remove
        </Button>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 10 }}>
        {a.target === 'any_upline' ? (
          <div style={{ gridColumn: '1 / -1' }}>
            <div className="muted" style={{ fontSize: 12, marginBottom: 6 }}>
              Share per upline level (level 1 = direct upline). Percent of the gift value.
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}>
              {(a.bps_by_depth || []).map((v, i) => (
                <div key={i} style={{ width: 110 }}>
                  <Field label={`Level ${i + 1} (%)`} type="number" min={0} max={100} step={0.01} value={String(v / 100)} onChange={(e) => onChange({ ...a, bps_by_depth: (a.bps_by_depth || []).map((x, j) => (j === i ? Math.round(Number(e.target.value) * 100) : x)) })} />
                </div>
              ))}
              <Button type="button" size="sm" variant="ghost" onClick={() => onChange({ ...a, bps_by_depth: [...(a.bps_by_depth || []), 0] })}>
                + level
              </Button>
              {(a.bps_by_depth || []).length > 1 ? (
                <Button type="button" size="sm" variant="ghost" onClick={() => onChange({ ...a, bps_by_depth: (a.bps_by_depth || []).slice(0, -1) })}>
                  − level
                </Button>
              ) : null}
            </div>
          </div>
        ) : (
          <>
            <Select label="Amount as" value={mode} onChange={(e) => onChange(e.target.value === 'fixed' ? { ...a, bps: 0, fixed: 1 } : { ...a, fixed: 0, bps: 500 })}>
              <option value="bps">Percent of gift value</option>
              <option value="fixed">Fixed value per gift</option>
            </Select>
            {mode === 'bps' ? (
              <Field label="Percent" type="number" min={0} max={100} step={0.01} value={String((a.bps || 0) / 100)} onChange={(e) => onChange({ ...a, bps: Math.round(Number(e.target.value) * 100) })} />
            ) : (
              <Field label="Fixed value" type="number" min={1} value={String(a.fixed || 1)} onChange={(e) => onChange({ ...a, fixed: Number(e.target.value) })} />
            )}
          </>
        )}
        {a.target === 'upline_by_role' ? (
          <Select label="Upline who is" value={String(a.role_id || 0)} onChange={(e) => onChange({ ...a, role_id: Number(e.target.value) })}>
            <option value="0">Choose a role…</option>
            {roles.map((r) => (
              <option key={r.id} value={r.id}>
                {r.title}
              </option>
            ))}
          </Select>
        ) : null}
        {a.target === 'upline_by_depth' ? <Field label="Levels up" type="number" min={1} max={32} value={String(a.depth || 1)} onChange={(e) => onChange({ ...a, depth: Number(e.target.value) })} /> : null}
        {a.target !== 'sender' ? <Field label="Cap per gift (diamonds, 0 = none)" type="number" min={0} value={String(a.max_diamonds || 0)} onChange={(e) => onChange({ ...a, max_diamonds: Number(e.target.value) })} /> : null}
        {isUpline ? (
          <Select label="If nobody qualifies, the share" value={a.fallback || 'platform'} onChange={(e) => onChange({ ...a, fallback: e.target.value })}>
            {(vocab?.fallbacks ?? []).map((f) => (
              <option key={f.code} value={f.code}>
                {f.text}
              </option>
            ))}
          </Select>
        ) : null}
      </div>
      {isUpline && a.target !== 'pk_winner' && a.target !== 'pk_loser' ? (
        <div>
          <div className="muted" style={{ fontSize: 12, marginBottom: 6 }}>
            The upline must meet (optional):
          </div>
          <ConditionEditor list={a.qualify || []} onChange={(qualify) => onChange({ ...a, qualify: qualify.length ? qualify : undefined })} roles={roles} vocab={vocab} tree={tree} allow={['RECEIVER_ROLE', 'RECEIVER_LEVEL', 'ACCOUNT_AGE', 'MIN_GIFT_VALUE']} />
        </div>
      ) : null}
    </div>
  )
}

// ─── Simulator ───────────────────────────────────────────────────────────────

function Simulator({ value, vocab, tree }: { value: BuilderValue; vocab: Vocabulary | null; tree: Tree | null }) {
  const cats = useMemo(() => flattenTree(tree), [tree])
  const gifts = cats.flatMap((c) => c.gifts)
  const [sender, setSender] = useState('')
  const [receiver, setReceiver] = useState('')
  const [gift, setGift] = useState(value.scope_kind === 'gift' ? value.scope_id : '')
  const [count, setCount] = useState('1')
  const [mode, setMode] = useState('video')
  const [role, setRole] = useState('host')
  const [pk, setPk] = useState(false)
  const [res, setRes] = useState<Simulation | null>(null)
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const run = useCallback(async () => {
    setBusy(true)
    setErr('')
    const r = await giftingApi.simulate({
      sender_id: sender.trim(),
      receiver_id: receiver.trim(),
      gift_id: gift,
      count: Number(count) || 1,
      live_mode: mode,
      live_role: role,
      in_pk: pk,
      [value.kind]: value.body,
    })
    setBusy(false)
    if (!r.ok) {
      setErr(r.message)
      setRes(null)
      return
    }
    setRes(r.data)
  }, [sender, receiver, gift, count, mode, role, pk, value.kind, value.body])
  return (
    <Card title="Try it (nothing is sent)">
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10 }}>
        <Field label="Sender user id" value={sender} onChange={(e) => setSender(e.target.value)} />
        <Field label="Receiver user id" value={receiver} onChange={(e) => setReceiver(e.target.value)} />
        <Select label="Gift" value={gift} onChange={(e) => setGift(e.target.value)}>
          <option value="">Choose…</option>
          {gifts.map((g) => (
            <option key={g.id} value={g.id}>
              {g.title}
            </option>
          ))}
        </Select>
        <Field label="Count" type="number" min={1} value={count} onChange={(e) => setCount(e.target.value)} />
        <Select label="Live mode" value={mode} onChange={(e) => setMode(e.target.value)}>
          <option value="video">Video</option>
          <option value="audio">Audio</option>
          <option value="">None (profile)</option>
        </Select>
        <Select label="Receiver's seat" value={role} onChange={(e) => setRole(e.target.value)}>
          <option value="host">Host</option>
          <option value="guest">Guest</option>
          <option value="viewer">Viewer</option>
          <option value="">None</option>
        </Select>
        <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, alignSelf: 'end' }}>
          <input type="checkbox" checked={pk} onChange={(e) => setPk(e.target.checked)} /> During a PK
        </label>
        <div style={{ alignSelf: 'end' }}>
          <Button type="button" size="sm" loading={busy} disabled={!sender || !receiver || !gift} onClick={() => void run()}>
            Run
          </Button>
        </div>
      </div>
      {err ? <div className="form-error" style={{ marginTop: 10 }}>{err}</div> : null}
      {res ? (
        <div style={{ marginTop: 12 }}>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 8 }}>
            <span className="status-pill" style={{ background: res.state.allowed ? 'var(--accent-soft)' : 'var(--danger)', color: res.state.allowed ? 'var(--accent-hover)' : '#fff' }}>
              {res.state.allowed ? 'Allowed' : `Refused · ${res.state.text || res.state.reason}`}
            </span>
            <span className="muted" style={{ fontSize: 12 }}>
              value {res.value.toLocaleString()} · receiver {res.receiver_diamonds.toLocaleString()} 💎 · platform {res.platform_diamonds.toLocaleString()} 💎 · shares {res.share_diamonds.toLocaleString()} 💎 · cashback {res.cashback_coins.toLocaleString()} coins
            </span>
          </div>
          <table className="table">
            <thead>
              <tr>
                <th>Goes to</th>
                <th>Who</th>
                <th>Share</th>
                <th>Value</th>
                <th>Pays</th>
                <th>Settles</th>
                <th>Why</th>
              </tr>
            </thead>
            <tbody>
              {res.rows.map((r) => (
                <tr key={r.idem}>
                  <td>{t(r.target, vocab)}</td>
                  <td>{r.beneficiary_id && r.beneficiary_id !== '0' ? `#${r.beneficiary_id}${r.depth ? ` · level ${r.depth}` : ''}` : <span className="muted">—</span>}</td>
                  <td>{r.fixed ? `${r.fixed} fixed` : pct(r.bps)}</td>
                  <td>{r.value.toLocaleString()}</td>
                  <td>{r.coins ? `${r.coins.toLocaleString()} coins` : `${r.diamonds.toLocaleString()} 💎`}</td>
                  <td>{r.skipped ? t('settle:5') : r.inline ? t('settle:1') : t('settle:2')}</td>
                  <td className="muted" style={{ fontSize: 12 }}>
                    {calcText(r.calc, vocab)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {res.chain.length ? (
            <p className="muted" style={{ fontSize: 12 }}>
              Receiver's upline: {res.chain.map((c) => `#${c.user_id} (level ${c.depth})`).join(' → ')}
            </p>
          ) : (
            <p className="muted" style={{ fontSize: 12 }}>
              Receiver has no upline.
            </p>
          )}
        </div>
      ) : null}
    </Card>
  )
}

export function calcText(calc: Record<string, unknown> | undefined, vocab: Vocabulary | null): string {
  if (!calc) return ''
  const parts: string[] = []
  if (calc.rule) parts.push(String(calc.rule) === 'remainder' ? 'remainder' : t(String(calc.rule), vocab))
  if (calc.reason) parts.push(t(String(calc.reason), vocab))
  if (calc.fallback) parts.push(t(`fallback:${calc.fallback}`, vocab))
  if (calc.capped) parts.push(`capped from ${calc.original}`)
  return parts.join(' · ')
}
