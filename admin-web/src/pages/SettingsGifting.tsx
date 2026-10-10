import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { Select } from '../ui/Org'
import { rolesApi, type RoleView } from '../lib/roles'
import { giftingApi, type Conversion, type GiftSettings, type Notify } from '../lib/gifting'

/**
 * Gifting engine settings: the master switch, built-in defaults every policy
 * inherits from, the platform account, the coin → value → diamond rate, and
 * worker timings. Rules themselves are policies (Gifting → Policies).
 */

type NumKey = 'max_count' | 'chain_depth' | 'receiver_bps' | 'rules_refresh_sec' | 'settle_batch' | 'settle_interval_ms' | 'outbox_interval_ms' | 'idem_retention_days' | 'partition_days_ahead'

const DEFAULTS: { key: NumKey; label: string; hint: string; min: number; max: number }[] = [
  { key: 'receiver_bps', label: 'Receiver share when no split policy applies (basis points)', hint: '9000 = 90%. The rest goes to the platform. Used only for gifts no distribution policy covers.', min: 1, max: 10000 },
  { key: 'max_count', label: 'Max gifts at once (default)', hint: 'Per send, unless a policy sets its own.', min: 1, max: 9999 },
  { key: 'chain_depth', label: 'Upline levels read', hint: 'How far up the receiver\u2019s chain the engine looks for "upline" shares. 0 turns upline shares off.', min: 0, max: 32 },
]

const WORKERS: { key: NumKey; label: string; hint: string; min: number; max: number }[] = [
  { key: 'rules_refresh_sec', label: 'Rule refresh (s)', hint: 'How often each process re-reads policies. Admin saves also refresh at once.', min: 5, max: 3600 },
  { key: 'settle_batch', label: 'Settle batch', hint: 'Upline shares paid per round.', min: 10, max: 1000 },
  { key: 'settle_interval_ms', label: 'Settle interval (ms)', hint: 'Pause between rounds when the queue is empty.', min: 100, max: 60000 },
  { key: 'outbox_interval_ms', label: 'Notice interval (ms)', hint: 'Delivery loop for system notices and pushes.', min: 200, max: 60000 },
  { key: 'idem_retention_days', label: 'Idempotency keys kept (days)', hint: 'Duplicate sends are refused within this window.', min: 1, max: 90 },
  { key: 'partition_days_ahead', label: 'Partitions created ahead (days)', hint: 'Daily tables are pre-created this far out.', min: 1, max: 90 },
]

export function SettingsGifting() {
  const [cur, setCur] = useState<GiftSettings | null>(null)
  const [draft, setDraft] = useState<Record<NumKey, string>>({} as Record<NumKey, string>)
  const [enabled, setEnabled] = useState(false)
  const [visibility, setVisibility] = useState<GiftSettings['receiving_visibility']>('everyone')
  const [fallback, setFallback] = useState('')
  const [platformUser, setPlatformUser] = useState('')
  const [notify, setNotify] = useState<Notify>({})
  const [roles, setRoles] = useState<RoleView[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  const apply = (s: GiftSettings) => {
    setCur(s)
    setEnabled(!!s.enabled)
    setVisibility(s.receiving_visibility)
    setFallback(s.platform_fallback)
    setPlatformUser(s.platform_user_id || '')
    setNotify(s.default_notify || {})
    const d = {} as Record<NumKey, string>
    for (const f of [...DEFAULTS, ...WORKERS]) d[f.key] = String(s[f.key] ?? '')
    setDraft(d)
  }

  useEffect(() => {
    void (async () => {
      const [r, rl] = await Promise.all([giftingApi.settings(), rolesApi.list()])
      if (!r.ok) {
        setError(r.message)
        return
      }
      apply(r.data)
      if (rl.ok) setRoles(rl.data.items ?? [])
    })()
  }, [])

  const save = async (e: FormEvent) => {
    e.preventDefault()
    if (!cur) return
    const body: Partial<GiftSettings> = {}
    if (enabled !== cur.enabled) body.enabled = enabled
    if (visibility !== cur.receiving_visibility) body.receiving_visibility = visibility
    if (fallback !== cur.platform_fallback) body.platform_fallback = fallback
    if (platformUser !== (cur.platform_user_id || '')) body.platform_user_id = platformUser
    if (JSON.stringify(notify) !== JSON.stringify(cur.default_notify || {})) body.default_notify = notify
    for (const f of [...DEFAULTS, ...WORKERS]) {
      const v = Number(draft[f.key])
      if (!Number.isFinite(v)) {
        setError(`${f.label} must be a number.`)
        return
      }
      if (v !== cur[f.key]) body[f.key] = Math.round(v)
    }
    if (Object.keys(body).length === 0) {
      setOk('Nothing changed.')
      return
    }
    setBusy(true)
    setError('')
    const r = await giftingApi.saveSettings(body)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    apply(r.data)
    setOk(r.message)
  }

  if (error && !cur) return <div className="form-error">{error}</div>
  if (!cur) return <PageLoader />
  const notifyBox = (k: keyof Notify, label: string) => (
    <label key={k} style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13 }}>
      <input type="checkbox" checked={!!notify[k]} onChange={(e) => setNotify({ ...notify, [k]: e.target.checked })} />
      {label}
    </label>
  )
  return (
    <div className="settings-page">
      <Link to="/settings" className="muted" style={{ fontSize: 13, fontWeight: 600 }}>
        ← Settings
      </Link>
      <div className="page-head">
        <h1>Gifting engine</h1>
        <p>
          Master switch, built-in defaults and worker timings. Who may gift whom and where the value goes are policies — edit them under <Link to="/gifting/policies">Gifting → Policies</Link>.
        </p>
      </div>
      <form onSubmit={(e) => void save(e)}>
        <Card title="Master switch">
          <label style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
            <span>
              <strong>Gifting {enabled ? 'enabled' : 'disabled'}</strong>
              <div className="muted" style={{ fontSize: 12 }}>
                When off every send is refused with a clear message and no coins move. Shares already queued keep settling.
              </div>
            </span>
          </label>
        </Card>
        <div style={{ height: 12 }} />
        <Card title="Built-in defaults (what policies inherit)">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
            {DEFAULTS.map((f) => (
              <Field key={f.key} label={f.label} type="number" min={f.min} max={f.max} value={draft[f.key] ?? ''} onChange={(e) => setDraft({ ...draft, [f.key]: e.target.value })} hint={`${f.hint} ${f.min}–${f.max}`} />
            ))}
          </div>
          <div style={{ marginTop: 12 }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)', marginBottom: 6 }}>Announce a gift by default</div>
            <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
              {notifyBox('chat', 'In the live chat')}
              {notifyBox('system', 'System notice')}
              {notifyBox('push_sender', 'Push to sender')}
              {notifyBox('push_receiver', 'Push to receiver')}
            </div>
            <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>
              A policy can override any of these for its scope.
            </div>
          </div>
        </Card>
        <div style={{ height: 12 }} />
        <Card title="Privacy and platform account">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
            <Select label="Who can see what a person received" value={visibility} onChange={(e) => setVisibility(e.target.value as GiftSettings['receiving_visibility'])} hint="Applies to the Gifting tab on profiles and host sheets. Admins with gift.view always see it.">
              <option value="everyone">Everyone</option>
              <option value="followers">Followers only</option>
              <option value="self">Only the person</option>
            </Select>
            <Field label="Platform account user id" value={platformUser} onChange={(e) => setPlatformUser(e.target.value)} hint="Where the platform share is recorded. Leave empty to use the first active holder of the role below." />
            <Select label="Fallback role for the platform account" value={fallback} onChange={(e) => setFallback(e.target.value)}>
              {roles.some((r) => r.slug === fallback) ? null : <option value={fallback}>{fallback}</option>}
              {roles.map((r) => (
                <option key={r.id} value={r.slug}>
                  {r.title}
                </option>
              ))}
            </Select>
          </div>
        </Card>
        <div style={{ height: 12 }} />
        <Card title="Worker timings">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
            {WORKERS.map((f) => (
              <Field key={f.key} label={f.label} type="number" min={f.min} max={f.max} value={draft[f.key] ?? ''} onChange={(e) => setDraft({ ...draft, [f.key]: e.target.value })} hint={`${f.hint} ${f.min}–${f.max}`} />
            ))}
          </div>
        </Card>
        {error ? <div className="form-error" style={{ marginTop: 10 }}>{error}</div> : null}
        {ok ? <div className="muted" style={{ marginTop: 10 }}>{ok}</div> : null}
        <div style={{ marginTop: 14 }}>
          <Button type="submit" loading={busy}>
            Save
          </Button>
        </div>
      </form>
      <div style={{ height: 12 }} />
      <ConversionCard />
    </div>
  )
}

/** Coins → value → diamonds. Append-only: a new rate applies to new gifts only. */
function ConversionCard() {
  const [cur, setCur] = useState<Conversion | null>(null)
  const [d, setD] = useState({ coin_value_num: '1', coin_value_den: '1', value_diamond_num: '1', value_diamond_den: '1', note: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const load = async () => {
    const r = await giftingApi.conversion()
    if (r.ok) {
      setCur(r.data)
      setD({ coin_value_num: String(r.data.coin_value_num), coin_value_den: String(r.data.coin_value_den), value_diamond_num: String(r.data.value_diamond_num), value_diamond_den: String(r.data.value_diamond_den), note: '' })
    }
  }
  useEffect(() => {
    void load()
  }, [])
  const save = async () => {
    setBusy(true)
    setError('')
    const r = await giftingApi.setConversion({
      coin_value_num: Number(d.coin_value_num),
      coin_value_den: Number(d.coin_value_den),
      value_diamond_num: Number(d.value_diamond_num),
      value_diamond_den: Number(d.value_diamond_den),
      note: d.note,
    })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(r.message)
    void load()
  }
  const coinsPerDiamond = cur ? (cur.coin_value_num * cur.value_diamond_num) / (cur.coin_value_den * cur.value_diamond_den) : 1
  return (
    <Card title="Coin → diamond rate">
      <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
        {cur ? (
          <>
            Now (v{cur.version}): 1 coin = {cur.coin_value_num}/{cur.coin_value_den} value; 1 value = {cur.value_diamond_num}/{cur.value_diamond_den} diamonds → <strong>1 coin ≈ {coinsPerDiamond} diamonds</strong> before the split.
            {cur.note ? ` · ${cur.note}` : ''}
          </>
        ) : (
          'Loading…'
        )}{' '}
        Every gift records the rate version that priced it; changing the rate never changes history.
      </p>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
        <Field label="Coin → value (num)" type="number" min={1} value={d.coin_value_num} onChange={(e) => setD({ ...d, coin_value_num: e.target.value })} />
        <Field label="Coin → value (den)" type="number" min={1} value={d.coin_value_den} onChange={(e) => setD({ ...d, coin_value_den: e.target.value })} />
        <Field label="Value → diamond (num)" type="number" min={1} value={d.value_diamond_num} onChange={(e) => setD({ ...d, value_diamond_num: e.target.value })} />
        <Field label="Value → diamond (den)" type="number" min={1} value={d.value_diamond_den} onChange={(e) => setD({ ...d, value_diamond_den: e.target.value })} />
        <Field label="Note" value={d.note} onChange={(e) => setD({ ...d, note: e.target.value })} />
      </div>
      {error ? <div className="form-error" style={{ marginTop: 10 }}>{error}</div> : null}
      {ok ? <div className="muted" style={{ marginTop: 10 }}>{ok}</div> : null}
      <div style={{ marginTop: 10 }}>
        <Button
          size="sm"
          loading={busy}
          onClick={() => {
            if (window.confirm('Activate this rate for all new gifts? Past gifts keep their rate.')) void save()
          }}
        >
          Activate new rate
        </Button>
      </div>
    </Card>
  )
}
