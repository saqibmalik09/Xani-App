import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { get, put } from '../lib/api'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

/**
 * Engine settings for live-time rewards. These are the developer defaults
 * (seeded by migration) an operator may tune; the *rules* (who gets what
 * for how long) are campaigns, edited under Live rewards → Campaigns.
 */

type RewardSettings = {
  enabled: boolean
  metrics_mode: string
  event_lag_sec: number
  sweeper_interval_sec: number
  dirty_debounce_ms: number
  scheduler_backoff_sec: number
  reconcile_interval_sec: number
  reconcile_recent_days: number
  reconcile_deep_days: number
  repair_per_sec: number
  repair_max_batch: number
  max_active_campaigns: number
  max_levels: number
  rolling_window_max_days: number
  retention_dirty_days: number
  hint_channel_size: number
}

type NumKey = Exclude<keyof RewardSettings, 'enabled' | 'metrics_mode'>

const FIELDS: { key: NumKey; label: string; hint: string; min: number; max: number }[] = [
  { key: 'event_lag_sec', label: 'Event lag (s)', hint: 'How far behind the live-time event stream the reward worker reads; covers in-flight commits.', min: 1, max: 60 },
  { key: 'sweeper_interval_sec', label: 'Due sweeper interval (s)', hint: 'Worst-case latency for a reward if every faster path missed. Keep ≤ 60.', min: 5, max: 120 },
  { key: 'dirty_debounce_ms', label: 'Gift dirty debounce (ms)', hint: 'Coalesces gift bursts before re-evaluating gift conditions.', min: 200, max: 60000 },
  { key: 'scheduler_backoff_sec', label: 'Scheduler backoff (s)', hint: 'Retry delay after a failed evaluation.', min: 5, max: 300 },
  { key: 'reconcile_interval_sec', label: 'Reconcile interval (s)', hint: 'How often the reconciler re-checks recent days for missed rewards.', min: 60, max: 3600 },
  { key: 'reconcile_recent_days', label: 'Reconcile recent days', hint: 'Days re-checked on every pass.', min: 1, max: 14 },
  { key: 'reconcile_deep_days', label: 'Reconcile deep days', hint: 'Days covered by the slow nightly pass.', min: 1, max: 90 },
  { key: 'repair_per_sec', label: 'Repairs per second', hint: 'Rate limit for automatic repairs.', min: 1, max: 1000 },
  { key: 'repair_max_batch', label: 'Repair batch', hint: 'Maximum repairs taken per round.', min: 10, max: 5000 },
  { key: 'max_active_campaigns', label: 'Max active campaigns', hint: 'Guardrail on how many rules can pay at once.', min: 1, max: 200 },
  { key: 'max_levels', label: 'Max levels per version', hint: '', min: 1, max: 50 },
  { key: 'rolling_window_max_days', label: 'Rolling window max days', hint: '', min: 2, max: 90 },
  { key: 'retention_dirty_days', label: 'Dirty-queue retention (days)', hint: '', min: 1, max: 90 },
  { key: 'hint_channel_size', label: 'Hint channel size', hint: 'In-memory queue for go-live / seat hints. Drops are counted on the Health page.', min: 256, max: 1_000_000 },
]

export function SettingsRewards() {
  const [cur, setCur] = useState<RewardSettings | null>(null)
  const [draft, setDraft] = useState<Record<NumKey, string>>({} as Record<NumKey, string>)
  const [enabled, setEnabled] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  const apply = (s: RewardSettings) => {
    setCur(s)
    setEnabled(!!s.enabled)
    const d = {} as Record<NumKey, string>
    for (const f of FIELDS) d[f.key] = String(s[f.key] ?? '')
    setDraft(d)
  }

  useEffect(() => {
    void (async () => {
      const r = await get<RewardSettings>('/v1/admin/settings/rewards')
      if (!r.ok) {
        setError(r.message)
        return
      }
      apply(r.data)
    })()
  }, [])

  const save = async (e: FormEvent) => {
    e.preventDefault()
    if (!cur) return
    const body: Record<string, unknown> = {}
    if (enabled !== cur.enabled) body.enabled = enabled
    for (const f of FIELDS) {
      const n = Number(draft[f.key])
      if (!Number.isFinite(n)) {
        setError(`${f.label} must be a number.`)
        return
      }
      if (n !== cur[f.key]) body[f.key] = Math.round(n)
    }
    if (Object.keys(body).length === 0) {
      setOk('Nothing changed.')
      return
    }
    setBusy(true)
    setError('')
    const r = await put<RewardSettings>('/v1/admin/settings/rewards', body)
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
  return (
    <div className="settings-page">
      <Link to="/settings" className="muted" style={{ fontSize: 13, fontWeight: 600 }}>
        ← Settings
      </Link>
      <div className="page-head">
        <h1>Live rewards engine</h1>
        <p>
          Master switch and worker timings. Reward rules are campaigns — edit them under{' '}
          <Link to="/live-rewards/campaigns">Live rewards → Campaigns</Link>.
        </p>
      </div>
      <form onSubmit={(e) => void save(e)}>
        <Card title="Master switch">
          <label style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
            <span>
              <strong>Rewards {enabled ? 'enabled' : 'disabled'}</strong>
              <div className="muted" style={{ fontSize: 12 }}>
                When off, no evaluation runs and no reward is credited; live time keeps counting. Turning it back on catches up through the sweeper and
                reconciler.
              </div>
            </span>
          </label>
        </Card>
        <div style={{ height: 12 }} />
        <Card title="Worker timings">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
            {FIELDS.map((f) => (
              <Field
                key={f.key}
                label={f.label}
                type="number"
                min={f.min}
                max={f.max}
                value={draft[f.key] ?? ''}
                onChange={(e) => setDraft({ ...draft, [f.key]: e.target.value })}
                hint={`${f.hint ? f.hint + ' ' : ''}${f.min}–${f.max}`}
              />
            ))}
          </div>
          <div className="muted" style={{ fontSize: 12, marginTop: 10 }}>
            Metrics mode: {cur.metrics_mode}
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
    </div>
  )
}
