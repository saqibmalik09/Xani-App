import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { get, patch, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { AccountModerationPanel, BAN_DURATIONS } from './AccountModerationPanel'

type Hit = {
  id: string
  username: string
  full_name: string
  avatar_url: string
  state: number
  banned: boolean
  ban_reason?: string
  coins: number
  diamonds: number
  beans: number
  allow_screenshots: boolean
  password_login_active?: boolean
}

type Device = {
  device_id: string
  platform: number
  model: string
  key_hash_prefix: string
  account_count: number
  banned: boolean
}

type DeviceUser = {
  id: string
  username: string
  full_name: string
  avatar_url: string
  state: number
}

type FrameCat = { id: string; title: string }

type FrameItemRow = {
  item: { id: string; category_id: string; kind: string; title: string; preview_key: string }
  prices: Array<{ days: number }>
}

type ListResp<T> = { items: T[] }

const frameDayLabel = (d: number) =>
  d === 0 ? 'Permanent' : d === 1 ? '1 day' : d === 7 ? '1 week' : d === 30 ? '1 month' : d === 90 ? '3 months' : `${d} days`

const itemKindLabel = (k: string) =>
  k === 'frame' ? 'Frame' : k === 'vip' ? 'VIP' : k === 'entrance' ? 'Entrance' : k === 'bubble' ? 'Bubble' : k === 'badge' ? 'Badge' : k || 'Frame'

const platformLabel = (p: number) => (['?', 'Android', 'iOS', 'Web'][p] ?? '?')

function parseWhole(raw: string): number | null {
  const t = raw.trim()
  if (!/^\d+$/.test(t)) return null
  const n = Number(t)
  if (!Number.isSafeInteger(n) || n < 0) return null
  return n
}

function changeHint(next: number, current: number, unit: string) {
  const d = next - current
  if (d === 0) return `No change - stays at ${current.toLocaleString()} ${unit}`
  if (d > 0) return `Will add ${d.toLocaleString()} ${unit} -> ${next.toLocaleString()}`
  return `Will remove ${Math.abs(d).toLocaleString()} ${unit} -> ${next.toLocaleString()}`
}

function useAccountHit() {
  const { id = '' } = useParams()
  const [user, setUser] = useState<Hit | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const reload = useCallback(async () => {
    const r = await get<ListResp<Hit>>(`/v1/admin/users/search?q=${encodeURIComponent(id)}&limit=3`)
    if (!r.ok) {
      setError(r.message)
      setLoading(false)
      return
    }
    const hit = r.data.items?.find((x) => x.id === id)
    if (!hit) {
      setError('That account was not found.')
      setLoading(false)
      return
    }
    setError('')
    setUser(hit)
    setLoading(false)
  }, [id])

  useEffect(() => {
    setLoading(true)
    void reload()
  }, [reload])

  return { id, user, error, setError, loading, reload }
}

function ToolShell({ title, children }: { title: string; children: ReactNode }) {
  const { id = '' } = useParams()
  return (
    <div className="rates-page">
      <div className="rates-toolbar">
        <div className="page-head" style={{ marginBottom: 0 }}>
          <h1>{title}</h1>
        </div>
        <Link to={`/accounts?id=${id}`}>
          <Button size="sm" variant="secondary">
            Back
          </Button>
        </Link>
      </div>
      {children}
    </div>
  )
}

export function AccountPasswordPage() {
  const { can } = useAuth()
  const { user, error, setError, loading, reload } = useAccountHit()
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [ok, setOk] = useState('')

  if (loading) return <PageLoader label="Loading password…" />
  if (!user) {
    return (
      <ToolShell title="Password">
        <div className="err-banner">{error || 'Not found.'}</div>
      </ToolShell>
    )
  }

  const hasPassword = Boolean(user.password_login_active)
  const save = async (e: FormEvent) => {
    e.preventDefault()
    if (!can('admin.manage_users')) return
    setBusy(true)
    setError('')
    setOk('')
    const r = await post<{ password_login_active?: boolean }>(`/v1/admin/users/${user.id}/password`, {
      new_password: next,
      confirm_password: confirm,
    })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setNext('')
    setConfirm('')
    setOk(hasPassword ? 'Password changed. Their other sessions were signed out.' : 'Password set. They can sign in with it now.')
    await reload()
  }

  return (
    <ToolShell title="Password">
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <Card>
        <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
          @{user.username} — {hasPassword ? 'login password is set' : 'no login password yet (social-only or unset)'}.
          Passwords are stored hashed, so the current value can never be shown.
        </p>
        <dl className="accounts-dl" style={{ marginBottom: 16 }}>
          <dt>Current password</dt>
          <dd>
            {hasPassword ? (
              <span className="status-pill on">Set (hidden)</span>
            ) : (
              <span className="status-pill off">Not set</span>
            )}
          </dd>
        </dl>
        <form onSubmit={(e) => void save(e)} style={{ display: 'flex', flexDirection: 'column', gap: 12, maxWidth: 420 }}>
          <Field
            label={hasPassword ? 'New password' : 'Set password'}
            type="password"
            autoComplete="new-password"
            value={next}
            onChange={(e) => setNext(e.target.value)}
            hint="8–128 characters"
            required
            minLength={8}
            maxLength={128}
          />
          <Field
            label="Confirm password"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
            minLength={8}
            maxLength={128}
          />
          <Button
            type="submit"
            size="sm"
            loading={busy}
            disabled={!can('admin.manage_users') || next.length < 8 || next !== confirm}
          >
            {hasPassword ? 'Change password' : 'Set password'}
          </Button>
        </form>
      </Card>
    </ToolShell>
  )
}

export function AccountBalancePage() {
  const { can } = useAuth()
  const { user, error, setError, loading, reload } = useAccountHit()
  const [coins, setCoins] = useState('')
  const [beans, setBeans] = useState('')
  const [diamonds, setDiamonds] = useState('')
  const [busy, setBusy] = useState(false)
  const [ok, setOk] = useState('')

  useEffect(() => {
    if (!user) return
    setCoins(String(user.coins))
    setBeans(String(user.beans))
    setDiamonds(String(user.diamonds))
  }, [user])

  if (loading) return <PageLoader label="Loading balance..." />
  if (!user) {
    return (
      <ToolShell title="Edit balance">
        <div className="err-banner">{error || 'Not found.'}</div>
      </ToolShell>
    )
  }

  const nextCoins = parseWhole(coins)
  const nextBeans = parseWhole(beans)
  const nextDiamonds = parseWhole(diamonds)
  const dCoins = nextCoins !== null ? nextCoins - user.coins : 0
  const dBeans = nextBeans !== null ? nextBeans - user.beans : 0
  const dDiamonds = nextDiamonds !== null ? nextDiamonds - user.diamonds : 0

  const save = async () => {
    if (!can('finance.adjust_wallet')) return
    setBusy(true)
    setError('')
    setOk('')
    const r = await post(`/v1/admin/users/${user.id}/wallet`, {
      coins: dCoins,
      diamonds: dDiamonds,
      beans: dBeans,
    })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk('Balance updated.')
    await reload()
  }

  return (
    <ToolShell title="Edit balance">
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <Card>
        <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
          @{user.username} - raise a number to add, lower it to remove.
        </p>
        <div className="accounts-wallet-edit">
          <Field
            label={`Coins - now ${user.coins.toLocaleString()}`}
            type="number"
            min={0}
            value={coins}
            onChange={(e) => setCoins(e.target.value)}
            hint={nextCoins === null ? 'Whole number 0 or more.' : changeHint(nextCoins, user.coins, 'coins')}
          />
          <Field
            label={`Beans - now ${user.beans.toLocaleString()}`}
            type="number"
            min={0}
            value={beans}
            onChange={(e) => setBeans(e.target.value)}
            hint={nextBeans === null ? 'Whole number 0 or more.' : changeHint(nextBeans, user.beans, 'beans')}
          />
          <Field
            label={`Diamonds - now ${user.diamonds.toLocaleString()}`}
            type="number"
            min={0}
            value={diamonds}
            onChange={(e) => setDiamonds(e.target.value)}
            hint={nextDiamonds === null ? 'Whole number 0 or more.' : changeHint(nextDiamonds, user.diamonds, 'diamonds')}
          />
        </div>
        <Button
          size="sm"
          loading={busy}
          style={{ marginTop: 12 }}
          disabled={!can('finance.adjust_wallet') || nextCoins === null || nextBeans === null || nextDiamonds === null || (dCoins === 0 && dBeans === 0 && dDiamonds === 0)}
          onClick={() => void save()}
        >
          Save
        </Button>
      </Card>
    </ToolShell>
  )
}

export function AccountStorePage() {
  const { id = '' } = useParams()
  const { mediaBase } = useAuth()
  const { user, error, loading } = useAccountHit()
  const [cats, setCats] = useState<FrameCat[]>([])
  const [rows, setRows] = useState<FrameItemRow[]>([])
  const [catId, setCatId] = useState('')
  const [itemId, setItemId] = useState('')
  const [days, setDays] = useState('')
  const [customDays, setCustomDays] = useState('7')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const [err, setErr] = useState('')

  useEffect(() => {
    void (async () => {
      const [c, i] = await Promise.all([
        get<ListResp<FrameCat>>('/v1/admin/store-categories?status=1'),
        get<ListResp<FrameItemRow>>('/v1/admin/store-items?status=1'),
      ])
      if (c.ok) setCats(c.data.items || [])
      if (i.ok) setRows(i.data.items || [])
    })()
  }, [])

  if (loading) return <PageLoader label="Loading store..." />

  const inCat = rows.filter((r) => !catId || r.item.category_id === catId)
  const picked = rows.find((r) => r.item.id === itemId)
  const previewUrl = picked?.item.preview_key ? mediaUrl(mediaBase, picked.item.preview_key) : ''
  const effectiveDays = days === 'custom' ? Math.max(0, Number(customDays) || 0) : Number(days)

  const assign = async () => {
    if (!picked || days === '') return
    setBusy(true)
    setErr('')
    setMsg('')
    const r = await post(`/v1/admin/users/${id}/frame`, { item_id: picked.item.id, days: effectiveDays })
    setBusy(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    setMsg(`Assigned "${picked.item.title}" for ${frameDayLabel(effectiveDays)}.`)
  }

  return (
    <ToolShell title="Assign store">
      {error ? <div className="err-banner">{error}</div> : null}
      {err ? <div className="err-banner">{err}</div> : null}
      {msg ? <div className="ok-banner">{msg}</div> : null}
      <Card>
        {user ? <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>Grant a bag item to @{user.username}. Free - no coins move.</p> : null}
        <div className="accounts-stack-fields">
          <label className="field-label">
            Category
            <select className="accounts-select" value={catId} onChange={(e) => { setCatId(e.target.value); setItemId('') }}>
              <option value="">All categories</option>
              {cats.map((c) => (
                <option key={c.id} value={c.id}>{c.title}</option>
              ))}
            </select>
          </label>
          <label className="field-label">
            Item
            <select className="accounts-select" value={itemId} onChange={(e) => { setItemId(e.target.value); setDays('') }}>
              <option value="">Choose an item...</option>
              {inCat.map((r) => (
                <option key={r.item.id} value={r.item.id}>
                  {itemKindLabel(r.item.kind)} - {r.item.title}
                </option>
              ))}
            </select>
          </label>
          <label className="field-label">
            Duration
            <select className="accounts-select" value={days} onChange={(e) => setDays(e.target.value)} disabled={!picked}>
              <option value="">Choose duration...</option>
              {(picked?.prices || []).map((p) => (
                <option key={p.days} value={String(p.days)}>{frameDayLabel(p.days)}</option>
              ))}
              <option value="custom">Custom days...</option>
            </select>
          </label>
          {days === 'custom' ? (
            <Field label="Days (0 = permanent)" type="number" min={0} value={customDays} onChange={(e) => setCustomDays(e.target.value)} />
          ) : null}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 12 }}>
          {previewUrl ? <img src={previewUrl} alt="" style={{ width: 44, height: 44, borderRadius: '50%', objectFit: 'cover' }} /> : null}
          <Button size="sm" loading={busy} disabled={!picked || days === ''} onClick={() => void assign()}>
            Assign
          </Button>
        </div>
      </Card>
    </ToolShell>
  )
}

type HostBlock = {
  host_id: string
  host_username: string
  user_id: string
  username: string
  by_username: string
  until: string
}

type RestrictionsExtra = {
  blocked_from_lives: HostBlock[]
  host_blocks_by_user: HostBlock[]
}

function fmtWhen(iso?: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

function remaining(until?: string, permanent?: boolean) {
  if (permanent || !until) return 'Permanent'
  const ms = new Date(until).getTime() - Date.now()
  if (ms <= 0) return 'Expired'
  const m = Math.ceil(ms / 60_000)
  if (m < 60) return `${m}m left`
  const h = Math.floor(m / 60)
  if (h < 48) return `${h}h left`
  return `${Math.floor(h / 24)}d left`
}

/** Effective capture/mic state from the moderation endpoint (permission engine). */
type ModInfo = {
  can_capture: boolean
  capture_override: 'allow' | 'deny' | 'inherit'
  mic_disabled: boolean
}

/** Dedicated Moderation tool: account ban + streaming. Devices stay on Devices. */
export function AccountModerationPage() {
  const { can } = useAuth()
  const { id, user, error, setError, loading, reload } = useAccountHit()
  const [busy, setBusy] = useState(false)
  const [ok, setOk] = useState('')
  const [blocks, setBlocks] = useState<RestrictionsExtra | null>(null)
  const [mod, setMod] = useState<ModInfo | null>(null)
  const [micReason, setMicReason] = useState('')
  const [warnReason, setWarnReason] = useState('')

  const canCaptureManage = can('screen.capture.manage')
  const canModApp = can('mod.app_users')

  const loadBlocks = useCallback(async () => {
    const r = await get<RestrictionsExtra>(`/v1/admin/users/${id}/restrictions`)
    if (r.ok) setBlocks(r.data)
  }, [id])

  const loadMod = useCallback(async () => {
    if (!canCaptureManage && !canModApp) return
    const r = await get<ModInfo>(`/v1/mod/users/${id}`)
    if (r.ok) setMod(r.data)
  }, [id, canCaptureManage, canModApp])

  useEffect(() => {
    void loadBlocks()
    void loadMod()
  }, [loadBlocks, loadMod])

  if (loading) return <PageLoader label="Loading moderation…" />
  if (!user) {
    return (
      <ToolShell title="Moderation">
        <div className="err-banner">{error || 'Not found.'}</div>
      </ToolShell>
    )
  }

  const act = async (fn: () => Promise<{ ok: boolean; message: string }>, success: string) => {
    setBusy(true)
    setError('')
    setOk('')
    const r = await fn()
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(success)
    await reload()
    await loadBlocks()
    await loadMod()
  }

  return (
    <ToolShell title="Moderation">
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <AccountModerationPanel
        userId={user.id}
        username={user.username}
        banned={user.banned}
        state={user.state}
        onChanged={() => { void reload(); void loadBlocks() }}
      />

      <Card>
        <header style={{ marginBottom: 10 }}>
          <h2 style={{ margin: 0, fontSize: 15 }}>
            Screenshots &amp; screen recording
            {mod ? (
              <span className={`status-pill ${mod.can_capture ? 'on' : 'off'}`} style={{ marginLeft: 8 }}>
                {mod.can_capture ? 'Allowed' : 'Blocked'}
              </span>
            ) : null}
          </h2>
          <p className="muted" style={{ margin: '4px 0 0', fontSize: 12 }}>
            The app blocks capture unless this account holds screen.capture — from its role, or from a
            direct override here. Revoke wins over any role; &ldquo;Role decides&rdquo; removes the override.
          </p>
        </header>
        {canCaptureManage && mod ? (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Button
              size="sm"
              variant={mod.capture_override === 'allow' ? 'primary' : 'secondary'}
              loading={busy}
              onClick={() =>
                void act(() => post(`/v1/mod/users/${user.id}/capture`, { mode: 'allow' }), 'Screen capture allowed.')
              }
            >
              Allow
            </Button>
            <Button
              size="sm"
              variant={mod.capture_override === 'deny' ? 'primary' : 'secondary'}
              loading={busy}
              onClick={() =>
                void act(() => post(`/v1/mod/users/${user.id}/capture`, { mode: 'revoke' }), 'Screen capture revoked.')
              }
            >
              Revoke
            </Button>
            <Button
              size="sm"
              variant={mod.capture_override === 'inherit' ? 'primary' : 'secondary'}
              loading={busy}
              onClick={() =>
                void act(() => post(`/v1/mod/users/${user.id}/capture`, { mode: 'inherit' }), 'Override removed; the role decides.')
              }
            >
              Role decides
            </Button>
          </div>
        ) : (
          <Button
            size="sm"
            variant="secondary"
            loading={busy}
            onClick={() =>
              void act(
                () => patch(`/v1/admin/users/${user.id}/screenshots`, { allow: !user.allow_screenshots }),
                user.allow_screenshots ? 'Screenshots disallowed.' : 'Screenshots allowed.',
              )
            }
          >
            {user.allow_screenshots ? 'Disallow screenshots' : 'Allow screenshots'}
          </Button>
        )}
      </Card>

      {canModApp ? (
        <Card>
          <header style={{ marginBottom: 10 }}>
            <h2 style={{ margin: 0, fontSize: 15 }}>
              Microphone
              {mod ? (
                <span className={`status-pill ${mod.mic_disabled ? 'off' : 'on'}`} style={{ marginLeft: 8 }}>
                  {mod.mic_disabled ? 'Disabled' : 'Enabled'}
                </span>
              ) : null}
            </h2>
            <p className="muted" style={{ margin: '4px 0 0', fontSize: 12 }}>
              Disabling blocks starting or accepting calls, going live, taking an audio seat and unmuting
              on stage, until re-enabled. The person is notified.
            </p>
          </header>
          {mod && !mod.mic_disabled ? (
            <div style={{ maxWidth: 360, marginBottom: 10 }}>
              <Field label="Reason (shown to the person)" value={micReason} onChange={(e) => setMicReason(e.target.value)} />
            </div>
          ) : null}
          <Button
            size="sm"
            variant={mod?.mic_disabled ? 'secondary' : 'danger'}
            loading={busy}
            disabled={!mod}
            onClick={() =>
              void act(
                () => post(`/v1/mod/users/${user.id}/mic`, { disabled: !mod?.mic_disabled, reason: micReason.trim() }),
                mod?.mic_disabled ? 'Microphone enabled.' : 'Microphone disabled.',
              )
            }
          >
            {mod?.mic_disabled ? 'Enable microphone' : 'Disable microphone'}
          </Button>
        </Card>
      ) : null}

      {canModApp ? (
        <Card>
          <header style={{ marginBottom: 10 }}>
            <h2 style={{ margin: 0, fontSize: 15 }}>Send warning</h2>
            <p className="muted" style={{ margin: '4px 0 0', fontSize: 12 }}>
              Lands in the person&apos;s notifications with your reason, plus a push.
            </p>
          </header>
          <div style={{ maxWidth: 420, marginBottom: 10 }}>
            <Field label="Reason" value={warnReason} onChange={(e) => setWarnReason(e.target.value)} />
          </div>
          <Button
            size="sm"
            variant="secondary"
            loading={busy}
            disabled={!warnReason.trim()}
            onClick={() =>
              void act(
                () => post(`/v1/mod/users/${user.id}/warn`, { reason: warnReason.trim() }).then((r) => {
                  if (r.ok) setWarnReason('')
                  return r
                }),
                'Warning sent.',
              )
            }
          >
            Send warning
          </Button>
        </Card>
      ) : null}

      <Card>
        <header style={{ marginBottom: 10 }}>
          <h2 style={{ margin: 0, fontSize: 15 }}>Blocked from other hosts&apos; lives</h2>
          <p className="muted" style={{ margin: '4px 0 0', fontSize: 12 }}>
            Host or moderator blocks keeping this user out of someone else&apos;s broadcast.
          </p>
        </header>
        {(blocks?.blocked_from_lives || []).length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>None active.</p>
        ) : (
          <div className="accounts-devices">
            {(blocks?.blocked_from_lives || []).map((b) => (
              <div key={`${b.host_id}:${b.user_id}`} className="accounts-device">
                <div>
                  <strong>@{b.host_username}&apos;s lives · {remaining(b.until, false)}</strong>
                  <div className="muted">
                    {b.by_username ? `by @${b.by_username} · ` : ''}ends {fmtWhen(b.until)}
                  </div>
                </div>
                {can('mod.ban_user') ? (
                  <Button
                    size="sm"
                    variant="secondary"
                    loading={busy}
                    onClick={() =>
                      void act(
                        () => post('/v1/admin/restrictions/host-blocks/clear', { host_id: b.host_id, user_id: b.user_id }),
                        'Host block cleared.',
                      )
                    }
                  >
                    Clear
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        )}
        <div style={{ marginTop: 10 }}>
          <Link to="/restrictions?tab=hosts">
            <Button size="sm" variant="ghost">All host blocks</Button>
          </Link>
        </div>
      </Card>

      <Card>
        <header style={{ marginBottom: 10 }}>
          <h2 style={{ margin: 0, fontSize: 15 }}>People this host blocked from their lives</h2>
        </header>
        {(blocks?.host_blocks_by_user || []).length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>None active.</p>
        ) : (
          <div className="accounts-devices">
            {(blocks?.host_blocks_by_user || []).map((b) => (
              <div key={`${b.host_id}:${b.user_id}`} className="accounts-device">
                <div>
                  <strong>
                    @{b.username}{' '}
                    <span className="status-pill warn">{remaining(b.until, false)}</span>
                  </strong>
                  <div className="muted">
                    {b.by_username ? `by @${b.by_username} · ` : ''}ends {fmtWhen(b.until)}
                  </div>
                </div>
                <div className="accounts-device-btns">
                  <Link to={`/accounts?id=${b.user_id}`}>
                    <Button size="sm" variant="ghost">Open</Button>
                  </Link>
                  {can('mod.ban_user') ? (
                    <Button
                      size="sm"
                      variant="secondary"
                      loading={busy}
                      onClick={() =>
                        void act(
                          () => post('/v1/admin/restrictions/host-blocks/clear', { host_id: b.host_id, user_id: b.user_id }),
                          'Host block cleared.',
                        )
                      }
                    >
                      Clear
                    </Button>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </ToolShell>
  )
}

/** Devices tool only — ban this hardware so linked accounts are kicked until unblocked. */
export function AccountDevicesPage() {
  const { can } = useAuth()
  const { id, user, error, setError, loading, reload } = useAccountHit()
  const navigate = useNavigate()
  const [devices, setDevices] = useState<Device[]>([])
  const [deviceUsers, setDeviceUsers] = useState<DeviceUser[] | null>(null)
  const [deviceTitle, setDeviceTitle] = useState('')
  const [busy, setBusy] = useState(false)
  const [ok, setOk] = useState('')
  const [banDeviceId, setBanDeviceId] = useState('')
  const [deviceDuration, setDeviceDuration] = useState('1440')
  const [deviceReason, setDeviceReason] = useState('Device banned by admin')

  const loadDevices = useCallback(async () => {
    const d = await get<ListResp<Device>>(`/v1/admin/users/${id}/devices`)
    if (d.ok) setDevices(d.data.items || [])
  }, [id])

  useEffect(() => {
    void loadDevices()
  }, [loadDevices])

  if (loading) return <PageLoader label="Loading devices…" />

  const act = async (fn: () => Promise<{ ok: boolean; message: string }>, success: string) => {
    setBusy(true)
    setError('')
    setOk('')
    const r = await fn()
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(success)
    setBanDeviceId('')
    await reload()
    await loadDevices()
  }

  return (
    <ToolShell title="Devices">
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <Card>
        <header style={{ marginBottom: 12 }}>
          <h2 style={{ margin: 0, fontSize: 16 }}>Device ban</h2>
          <p className="muted" style={{ margin: '4px 0 0', fontSize: 13 }}>
            Separate from account ban. Banning a device signs out every account on it immediately and blocks new logins on that hardware until you unban.
          </p>
          {user ? <p className="muted" style={{ margin: '6px 0 0', fontSize: 13 }}>@{user.username}</p> : null}
        </header>
        <div style={{ marginBottom: 12 }}>
          <Link to="/restrictions?tab=devices">
            <Button size="sm" variant="ghost">All device bans</Button>
          </Link>
          {' '}
          <Link to={`/accounts/${id}/moderation`}>
            <Button size="sm" variant="ghost">Account / streaming bans →</Button>
          </Link>
        </div>
        {devices.length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>No devices linked.</p>
        ) : (
          <div className="accounts-devices">
            {devices.map((d) => (
              <div key={d.device_id} className="accounts-device">
                <div>
                  <strong>
                    {platformLabel(d.platform)}
                    {d.model ? ` · ${d.model}` : ''}
                    {d.banned ? <span className="status-pill off" style={{ marginLeft: 8 }}>Banned</span> : null}
                  </strong>
                  <div className="muted">
                    {d.key_hash_prefix}… · {d.account_count} accounts
                  </div>
                  {banDeviceId === d.device_id && !d.banned ? (
                    <div className="form-grid" style={{ marginTop: 8, maxWidth: 360 }}>
                      <Field
                        label="Reason"
                        value={deviceReason}
                        onChange={(e) => setDeviceReason(e.target.value)}
                      />
                      <label className="field-label">
                        Ban duration
                        <select
                          className="accounts-search-input"
                          value={deviceDuration}
                          onChange={(e) => setDeviceDuration(e.target.value)}
                          style={{ marginTop: 6 }}
                        >
                          {BAN_DURATIONS.map((x) => (
                            <option key={x.label} value={String(x.minutes)}>{x.label}</option>
                          ))}
                        </select>
                      </label>
                    </div>
                  ) : null}
                </div>
                <div className="accounts-device-btns">
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => {
                      void (async () => {
                        setDeviceTitle(`${platformLabel(d.platform)} ${d.model || ''}`)
                        const r = await get<ListResp<DeviceUser>>(`/v1/admin/devices/${d.device_id}/users`)
                        if (!r.ok) {
                          setError(r.message)
                          return
                        }
                        setDeviceUsers(r.data.items || [])
                      })()
                    }}
                  >
                    View accounts
                  </Button>
                  {can('mod.ban_device') ? (
                    d.banned ? (
                      <Button size="sm" variant="secondary" loading={busy} onClick={() => void act(() => post(`/v1/admin/devices/${d.device_id}/unban`, {}), 'Device unbanned.')}>
                        Unban device
                      </Button>
                    ) : banDeviceId === d.device_id ? (
                      <>
                        <Button
                          size="sm"
                          variant="danger"
                          loading={busy}
                          onClick={() => {
                            const minutes = Number(deviceDuration)
                            void act(
                              () =>
                                post(`/v1/admin/devices/${d.device_id}/ban`, {
                                  reason: deviceReason.trim() || 'Device banned by admin',
                                  duration_minutes: minutes,
                                  permanent: minutes === 0,
                                }),
                              minutes === 0 ? 'Device permanently banned — sessions ended.' : `Device banned for ${minutes} minutes — sessions ended.`,
                            )
                          }}
                        >
                          Confirm ban
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setBanDeviceId('')}>Cancel</Button>
                      </>
                    ) : (
                      <Button size="sm" variant="danger" onClick={() => { setBanDeviceId(d.device_id); setDeviceDuration('1440') }}>
                        Ban device
                      </Button>
                    )
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        )}
        {deviceUsers ? (
          <div className="accounts-device-users">
            <div className="accounts-device-users-head">
              <strong>{deviceTitle}</strong>
              <Button size="sm" variant="ghost" onClick={() => setDeviceUsers(null)}>Close</Button>
            </div>
            <ul className="accounts-device-user-list">
              {deviceUsers.map((u) => (
                <li key={u.id}>
                  <button type="button" className="accounts-device-user" onClick={() => navigate(`/accounts?id=${u.id}`)}>
                    <span>
                      <strong>@{u.username}</strong>
                      <span className="muted"> {u.full_name}</span>
                    </span>
                    <span className={`status-pill ${u.state === 2 ? 'off' : 'on'}`}>
                      {u.state === 2 ? 'Banned' : u.state === 3 ? 'Deleted' : 'Active'}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ) : null}
      </Card>
    </ToolShell>
  )
}
