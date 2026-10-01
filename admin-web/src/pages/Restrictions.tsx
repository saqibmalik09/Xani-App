import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { get, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { Loader, PageLoader } from '../ui/Loader'

type Tab = 'accounts' | 'go_live' | 'join_live' | 'posts' | 'devices' | 'hosts'

type BanRow = {
  id: string
  reason: string
  permanent: boolean
  until?: string
  created_at: string
  creator_username?: string
  user_id?: string
  username?: string
  full_name?: string
  avatar_url?: string
  device_id?: string
  platform?: number
  model?: string
  key_hash_prefix?: string
  account_count?: number
}

type HostBlock = {
  host_id: string
  host_username: string
  host_name: string
  user_id: string
  username: string
  full_name: string
  avatar_url: string
  by_username: string
  until: string
  created_at: string
}

type ListResp<T> = { items: T[]; next_cursor?: string }

const platformLabel = (p?: number) => (['?', 'Android', 'iOS', 'Web'][p ?? 0] ?? '?')

function fmtWhen(iso?: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
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

function Face({ url, name }: { url?: string; name: string }) {
  const { mediaBase } = useAuth()
  const src = mediaUrl(mediaBase, url || '')
  if (src) return <img src={src} alt="" className="accounts-suggest-av" loading="lazy" />
  return (
    <span className="accounts-suggest-av accounts-suggest-av--letter">
      {(name || '?').slice(0, 1).toUpperCase()}
    </span>
  )
}

const TABS: Array<{ id: Tab; label: string; need: 'accounts' | 'devices' | 'hosts' }> = [
  { id: 'accounts', label: 'Account', need: 'accounts' },
  { id: 'go_live', label: 'Go live', need: 'accounts' },
  { id: 'join_live', label: 'Join lives', need: 'accounts' },
  { id: 'posts', label: 'Posting', need: 'accounts' },
  { id: 'devices', label: 'Devices', need: 'devices' },
  { id: 'hosts', label: 'Host blocks', need: 'hosts' },
]

export function RestrictionsPage() {
  const { can } = useAuth()
  const [params, setParams] = useSearchParams()
  const raw = params.get('tab') || 'accounts'
  const tab = (TABS.some((t) => t.id === raw) ? raw : 'accounts') as Tab

  const setTab = (t: Tab) => {
    const next = new URLSearchParams(params)
    next.set('tab', t)
    setParams(next, { replace: true })
  }

  const canAccounts = can('mod.ban_user')
  const canDevices = can('mod.ban_device')
  const canHosts = can('admin.manage_users')
  const allowed = (need: 'accounts' | 'devices' | 'hosts') =>
    need === 'accounts' ? canAccounts : need === 'devices' ? canDevices : canHosts

  useEffect(() => {
    const cur = TABS.find((t) => t.id === tab)
    if (cur && !allowed(cur.need)) {
      const first = TABS.find((t) => allowed(t.need))
      if (first) setTab(first.id)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, canAccounts, canDevices, canHosts])

  return (
    <div className="accounts-page">
      <div className="page-head">
        <h1>Restrictions</h1>
        <p>Active account, streaming, posting, device, and host blocks.</p>
      </div>

      <div className="chip-row" style={{ marginBottom: 16 }}>
        {TABS.filter((t) => allowed(t.need)).map((t) => (
          <button
            key={t.id}
            type="button"
            className={`chip${tab === t.id ? ' is-on' : ''}`}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'accounts' && canAccounts ? (
        <UserBanList
          path="/v1/admin/restrictions/account-bans"
          empty="No active account bans."
          unbanKind="account"
          confirm="Unban this account now?"
          okMsg="Account unbanned."
        />
      ) : null}
      {tab === 'go_live' && canAccounts ? (
        <UserBanList
          path="/v1/admin/restrictions/go-live-bans"
          empty="No active go-live blocks."
          unbanKind="go_live"
          confirm="Allow this user to go live again?"
          okMsg="Go-live block cleared."
        />
      ) : null}
      {tab === 'join_live' && canAccounts ? (
        <UserBanList
          path="/v1/admin/restrictions/join-live-bans"
          empty="No active join-live blocks."
          unbanKind="join_live"
          confirm="Allow this user to join lives again?"
          okMsg="Join-live block cleared."
        />
      ) : null}
      {tab === 'posts' && canAccounts ? (
        <UserBanList
          path="/v1/admin/restrictions/post-bans"
          empty="No active posting blocks."
          unbanKind="post"
          confirm="Allow this user to post again?"
          okMsg="Posting block cleared."
        />
      ) : null}
      {tab === 'devices' && canDevices ? <DeviceBansList /> : null}
      {tab === 'hosts' && canHosts ? <HostBlocksList /> : null}
      {!canAccounts && !canDevices && !canHosts ? (
        <Card>
          <p className="muted" style={{ margin: 0 }}>You do not have permission to view restrictions.</p>
        </Card>
      ) : null}
    </div>
  )
}

function UserBanList(props: {
  path: string
  empty: string
  unbanKind: string
  confirm: string
  okMsg: string
}) {
  const [rows, setRows] = useState<BanRow[]>([])
  const [cursor, setCursor] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [ok, setOk] = useState('')

  const load = useCallback(async (reset: boolean, after = '') => {
    reset ? setLoading(true) : setMore(true)
    setError('')
    const q = after ? `?cursor=${encodeURIComponent(after)}` : ''
    const r = await get<ListResp<BanRow>>(`${props.path}${q}`)
    reset ? setLoading(false) : setMore(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    const items = r.data.items || []
    setRows((prev) => (reset ? items : [...prev, ...items]))
    setCursor(r.data.next_cursor || '')
  }, [props.path])

  useEffect(() => {
    void load(true)
  }, [load])

  const unban = async (userId: string) => {
    if (!window.confirm(props.confirm)) return
    setBusy(userId)
    setOk('')
    setError('')
    const r = await post(`/v1/admin/users/${userId}/unban`, { kind: props.unbanKind })
    setBusy('')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk(props.okMsg)
    setRows((prev) => prev.filter((x) => x.user_id !== userId))
  }

  if (loading) return <PageLoader label="Loading…" />

  return (
    <>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <Card>
        {rows.length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>{props.empty}</p>
        ) : (
          <div className="accounts-devices">
            {rows.map((b) => (
              <div key={b.id} className="accounts-device">
                <div style={{ display: 'flex', gap: 12, alignItems: 'center', minWidth: 0 }}>
                  <Face url={b.avatar_url} name={b.full_name || b.username || '?'} />
                  <div style={{ minWidth: 0 }}>
                    <strong>
                      @{b.username}{' '}
                      <span className={`status-pill ${b.permanent ? 'off' : 'warn'}`}>
                        {remaining(b.until, b.permanent)}
                      </span>
                    </strong>
                    <div className="muted">
                      {b.reason || 'No reason'}
                      {b.creator_username ? ` · by @${b.creator_username}` : ''}
                      {!b.permanent && b.until ? ` · until ${fmtWhen(b.until)}` : ''}
                    </div>
                  </div>
                </div>
                <div className="accounts-device-btns">
                  {b.user_id ? (
                    <>
                      <Link to={`/accounts?id=${b.user_id}`}>
                        <Button size="sm" variant="ghost">Open</Button>
                      </Link>
                      <Button size="sm" variant="secondary" loading={busy === b.user_id} onClick={() => void unban(b.user_id!)}>
                        Clear
                      </Button>
                    </>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        )}
        {cursor ? (
          <div style={{ marginTop: 12 }}>
            <Button size="sm" variant="secondary" loading={more} onClick={() => void load(false, cursor)}>
              Load more
            </Button>
          </div>
        ) : null}
      </Card>
    </>
  )
}

function DeviceBansList() {
  const [rows, setRows] = useState<BanRow[]>([])
  const [cursor, setCursor] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [ok, setOk] = useState('')

  const load = useCallback(async (reset: boolean, after = '') => {
    reset ? setLoading(true) : setMore(true)
    setError('')
    const q = after ? `?cursor=${encodeURIComponent(after)}` : ''
    const r = await get<ListResp<BanRow>>(`/v1/admin/restrictions/device-bans${q}`)
    reset ? setLoading(false) : setMore(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    const items = r.data.items || []
    setRows((prev) => (reset ? items : [...prev, ...items]))
    setCursor(r.data.next_cursor || '')
  }, [])

  useEffect(() => {
    void load(true)
  }, [load])

  const unban = async (deviceId: string) => {
    if (!deviceId || deviceId === '0') {
      setError('This ban has no linked device row.')
      return
    }
    if (!window.confirm('Unban this device now?')) return
    setBusy(deviceId)
    setOk('')
    setError('')
    const r = await post(`/v1/admin/devices/${deviceId}/unban`, {})
    setBusy('')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk('Device unbanned.')
    setRows((prev) => prev.filter((x) => x.device_id !== deviceId))
  }

  if (loading) return <PageLoader label="Loading device bans…" />

  return (
    <>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <Card>
        {rows.length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>No active device bans.</p>
        ) : (
          <div className="accounts-devices">
            {rows.map((b) => (
              <div key={b.id} className="accounts-device">
                <div>
                  <strong>
                    {platformLabel(b.platform)}
                    {b.model ? ` · ${b.model}` : ''}{' '}
                    <span className={`status-pill ${b.permanent ? 'off' : 'warn'}`}>
                      {remaining(b.until, b.permanent)}
                    </span>
                  </strong>
                  <div className="muted">
                    {b.key_hash_prefix || '—'}… · {b.account_count ?? 0} accounts · {b.reason || 'No reason'}
                    {b.creator_username ? ` · by @${b.creator_username}` : ''}
                    {!b.permanent && b.until ? ` · until ${fmtWhen(b.until)}` : ''}
                  </div>
                </div>
                <div className="accounts-device-btns">
                  <Button
                    size="sm"
                    variant="secondary"
                    loading={busy === b.device_id}
                    disabled={!b.device_id || b.device_id === '0'}
                    onClick={() => void unban(b.device_id || '')}
                  >
                    Unban
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
        {cursor ? (
          <div style={{ marginTop: 12 }}>
            <Button size="sm" variant="secondary" loading={more} onClick={() => void load(false, cursor)}>
              Load more
            </Button>
          </div>
        ) : null}
      </Card>
    </>
  )
}

function HostBlocksList() {
  const { can } = useAuth()
  const [hostQ, setHostQ] = useState('')
  const [userQ, setUserQ] = useState('')
  const [filterHost, setFilterHost] = useState('')
  const [filterUser, setFilterUser] = useState('')
  const [rows, setRows] = useState<HostBlock[]>([])
  const [cursor, setCursor] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [ok, setOk] = useState('')

  const load = useCallback(async (reset: boolean, after = '') => {
    reset ? setLoading(true) : setMore(true)
    setError('')
    const params = new URLSearchParams()
    if (filterHost.trim()) params.set('host', filterHost.trim().replace(/^@/, ''))
    if (filterUser.trim()) params.set('user', filterUser.trim().replace(/^@/, ''))
    if (after) params.set('cursor', after)
    const q = params.toString() ? `?${params}` : ''
    const r = await get<ListResp<HostBlock>>(`/v1/admin/restrictions/host-blocks${q}`)
    reset ? setLoading(false) : setMore(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    const items = r.data.items || []
    setRows((prev) => (reset ? items : [...prev, ...items]))
    setCursor(r.data.next_cursor || '')
  }, [filterHost, filterUser])

  useEffect(() => {
    void load(true)
  }, [load])

  const clear = async (hostId: string, userId: string) => {
    if (!window.confirm('Remove this host live block?')) return
    const key = `${hostId}:${userId}`
    setBusy(key)
    setOk('')
    setError('')
    const r = await post(`/v1/admin/restrictions/host-blocks/clear`, { host_id: hostId, user_id: userId })
    setBusy('')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setOk('Host block removed.')
    setRows((prev) => prev.filter((x) => !(x.host_id === hostId && x.user_id === userId)))
  }

  return (
    <>
      <Card>
        <div className="settings-form" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr auto', gap: 12, alignItems: 'end' }}>
          <Field label="Host username" value={hostQ} onChange={(e) => setHostQ(e.target.value)} placeholder="@username" />
          <Field label="Blocked username" value={userQ} onChange={(e) => setUserQ(e.target.value)} placeholder="@username" />
          <Button
            size="sm"
            onClick={() => {
              setFilterHost(hostQ)
              setFilterUser(userQ)
            }}
          >
            Filter
          </Button>
        </div>
      </Card>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 32 }}>
          <Loader />
        </div>
      ) : (
        <Card>
          {rows.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>No active host live blocks.</p>
          ) : (
            <div className="accounts-devices">
              {rows.map((b) => {
                const key = `${b.host_id}:${b.user_id}`
                return (
                  <div key={key} className="accounts-device">
                    <div style={{ display: 'flex', gap: 12, alignItems: 'center', minWidth: 0 }}>
                      <Face url={b.avatar_url} name={b.full_name || b.username} />
                      <div style={{ minWidth: 0 }}>
                        <strong>
                          @{b.username}{' '}
                          <span className="status-pill warn">{remaining(b.until, false)}</span>
                        </strong>
                        <div className="muted">
                          Blocked from @{b.host_username}&apos;s lives
                          {b.by_username ? ` · by @${b.by_username}` : ''}
                          {' · until '}
                          {fmtWhen(b.until)}
                        </div>
                      </div>
                    </div>
                    <div className="accounts-device-btns">
                      <Link to={`/accounts?id=${b.user_id}`}>
                        <Button size="sm" variant="ghost">User</Button>
                      </Link>
                      <Link to={`/accounts?id=${b.host_id}`}>
                        <Button size="sm" variant="ghost">Host</Button>
                      </Link>
                      {can('mod.ban_user') ? (
                        <Button size="sm" variant="secondary" loading={busy === key} onClick={() => void clear(b.host_id, b.user_id)}>
                          Clear
                        </Button>
                      ) : null}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
          {cursor ? (
            <div style={{ marginTop: 12 }}>
              <Button size="sm" variant="secondary" loading={more} onClick={() => void load(false, cursor)}>
                Load more
              </Button>
            </div>
          ) : null}
        </Card>
      )}
    </>
  )
}
