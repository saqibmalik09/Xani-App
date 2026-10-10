import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { get, put } from '../lib/api'
import { Button } from '../ui/Button'
import { Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { fmtMs } from './LiveTime'

/**
 * The accounting day for live time: which timezone a "day" is measured in
 * and what wall-clock minute it starts at. Default UTC 00:00. Changing it
 * re-projects every day and month total from the ledger (the ledger itself
 * never changes); until that finishes the aggregator holds and this page
 * says so.
 */

type LiveTimeSettings = {
  day_tz: string
  day_start: string
  week_start: string
  allowance_beats: number
  checkpoint_min_sec: number
  host_beat_sec: number
  cohost_beat_sec: number
  allowance_host_sec: number
  allowance_cohost_sec: number
  calendar: { tz: string; day_start: string; week_start: string; today: string; describe: string; today_start: string; today_end: string }
  aggregator?: {
    halted: boolean
    meta_tz: string
    meta_day_start: string
    meta_week_start: string
    rebuild?: Record<string, unknown>
    backlog: number
  }
}

const WEEKDAYS = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']

function zoneList(): string[] {
  const intl = Intl as unknown as { supportedValuesOf?: (k: string) => string[] }
  const zones = intl.supportedValuesOf ? intl.supportedValuesOf('timeZone') : []
  return zones.includes('UTC') ? zones : ['UTC', ...zones]
}

export function SettingsLiveTime() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [cur, setCur] = useState<LiveTimeSettings | null>(null)
  const [tz, setTz] = useState('UTC')
  const [start, setStart] = useState('00:00')
  const [week, setWeek] = useState('monday')
  const [beats, setBeats] = useState(2)
  const [ckpt, setCkpt] = useState(3)
  const zones = useMemo(zoneList, [])

  const load = async () => {
    const r = await get<LiveTimeSettings>('/v1/admin/settings/live-time')
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setCur(r.data)
    setTz(r.data.day_tz)
    setStart(r.data.day_start)
    setWeek(r.data.week_start)
    setBeats(r.data.allowance_beats)
    setCkpt(r.data.checkpoint_min_sec)
  }

  useEffect(() => {
    void load()
  }, [])

  // While a rebuild is in flight, poll so the banner clears on its own.
  useEffect(() => {
    const phase = cur?.aggregator?.rebuild?.phase
    if (phase !== 'running') return
    const t = window.setInterval(() => void load(), 5000)
    return () => window.clearInterval(t)
  }, [cur?.aggregator?.rebuild?.phase])

  const calendarChanged = cur && (tz !== cur.day_tz || start !== cur.day_start || week !== cur.week_start)

  const save = async (e: FormEvent) => {
    e.preventDefault()
    if (calendarChanged && !window.confirm('Changing the day definition re-computes every host\u2019s daily and monthly totals from the ledger. Lifetime totals and sessions do not change. Continue?')) {
      return
    }
    setBusy(true)
    setError('')
    setOk('')
    const r = await put<LiveTimeSettings>('/v1/admin/settings/live-time', {
      day_tz: tz,
      day_start: start,
      week_start: week,
      allowance_beats: beats,
      checkpoint_min_sec: ckpt,
    })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setCur(r.data)
    setOk(r.message || 'Saved.')
  }

  if (loading) return <PageLoader label="Loading…" />

  const localExample = (() => {
    try {
      const now = new Date()
      return now.toLocaleTimeString(undefined, { timeZone: tz, hour: '2-digit', minute: '2-digit', hour12: false })
    } catch {
      return ''
    }
  })()

  const agg = cur?.aggregator
  const rebuild = agg?.rebuild
  const rebuilding = rebuild?.phase === 'running'

  return (
    <div className="settings-page">
      <div className="page-head">
        <Link to="/settings" className="muted" style={{ fontSize: 13, fontWeight: 600 }}>
          ← Settings
        </Link>
        <h1>Live time day</h1>
        <p>
          Which timezone a host&apos;s &ldquo;day&rdquo; is measured in and the time it starts. Daily, weekly and monthly
          live-time totals — in the app and the dashboard — follow this. The default is UTC at 00:00.
        </p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      {rebuilding ? (
        <div className="ok-banner">
          Rebuilding day totals for the new definition… {String(rebuild?.segments ?? 0)} segments so far. Figures shown in the
          app may lag until this finishes.
        </div>
      ) : agg?.halted ? (
        <div className="err-banner">
          The aggregator is holding: projections were built with {agg.meta_tz} · {agg.meta_day_start} and settings now say{' '}
          {cur?.day_tz} · {cur?.day_start}. Save again to start the rebuild, or open{' '}
          <Link to="/live-time/audit">Time audit</Link>.
        </div>
      ) : null}

      <section className="settings-card">
        <form className="settings-form" onSubmit={(e) => void save(e)}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>Timezone</span>
            <input
              list="livetime-zones"
              value={tz}
              onChange={(e) => setTz(e.target.value)}
              placeholder="UTC"
              required
              style={{
                height: 'var(--field-h)',
                padding: '0 14px',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--line)',
                background: 'var(--surface)',
                color: 'var(--ink)',
                fontSize: 14,
              }}
            />
            <datalist id="livetime-zones">
              {zones.map((z) => (
                <option key={z} value={z} />
              ))}
            </datalist>
            <span className="muted" style={{ fontSize: 12 }}>
              IANA name, e.g. UTC, Asia/Karachi, Europe/Berlin.{localExample ? ` It is ${localExample} there now.` : ''}
            </span>
          </label>

          <Field
            label="Day starts at"
            type="time"
            step={60}
            value={start}
            onChange={(e) => setStart(e.target.value)}
            hint="Local wall-clock time in that zone. 00:00 is midnight; 09:00 means a broadcast at 08:59 counts toward the previous day."
            required
          />

          <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>Week starts on</span>
            <select
              value={week}
              onChange={(e) => setWeek(e.target.value)}
              style={{
                height: 'var(--field-h)',
                padding: '0 14px',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--line)',
                background: 'var(--surface)',
                color: 'var(--ink)',
                fontSize: 14,
              }}
            >
              {WEEKDAYS.map((d) => (
                <option key={d} value={d}>
                  {d[0].toUpperCase() + d.slice(1)}
                </option>
              ))}
            </select>
          </label>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field
              label="Allowance (heartbeats)"
              type="number"
              min={1}
              max={10}
              value={beats}
              onChange={(e) => setBeats(Number(e.target.value) || 2)}
              hint={
                cur
                  ? `How long silence counts before time stops: ${beats * cur.host_beat_sec}s for hosts, ${beats * cur.cohost_beat_sec}s for co-hosts.`
                  : ''
              }
              required
            />
            <Field
              label="Checkpoint every (seconds)"
              type="number"
              min={1}
              max={120}
              value={ckpt}
              onChange={(e) => setCkpt(Number(e.target.value) || 3)}
              hint="How often open proofs are written to the database. Stretches automatically when many hosts are live."
              required
            />
          </div>

          {cur ? (
            <div style={{ padding: 12, background: 'var(--wash)', borderRadius: 'var(--radius-sm)', fontSize: 12 }}>
              <strong>In use now:</strong> {cur.calendar.tz} · day starts {cur.calendar.day_start} · week from{' '}
              {cur.calendar.week_start} · today is {cur.calendar.today}
              <div className="muted" style={{ marginTop: 4 }}>
                Today runs {new Date(cur.calendar.today_start).toLocaleString(undefined, { hour12: false })} →{' '}
                {new Date(cur.calendar.today_end).toLocaleString(undefined, { hour12: false })} (your clock).
                {agg ? ` Aggregator backlog ${agg.backlog}.` : ''}
              </div>
            </div>
          ) : null}

          <div className="form-actions">
            <Button type="button" size="sm" variant="ghost" onClick={() => navigate('/settings')}>
              Back
            </Button>
            <Button type="submit" size="sm" loading={busy}>
              {calendarChanged ? 'Save and rebuild totals' : 'Save'}
            </Button>
          </div>
        </form>
      </section>

      {cur ? (
        <p className="muted" style={{ fontSize: 12 }}>
          Allowance today: hosts {fmtMs(cur.allowance_host_sec * 1000)}, co-hosts {fmtMs(cur.allowance_cohost_sec * 1000)}. A
          participant silent longer than that stops counting at their last heartbeat + allowance; when they return a new
          segment starts and the gap is recorded, never credited.
        </p>
      ) : null}
    </div>
  )
}
