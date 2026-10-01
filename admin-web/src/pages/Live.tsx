import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { get, post } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { LevelBadge, type LevelView } from './LevelUsers'

type Kind = '' | 'audio' | 'video' | 'pk'

type HostBrief = {
  id: string
  username: string
  full_name: string
  avatar_url?: string
  country?: string
  receiver_level?: LevelView
}

type PkSide = { room_id: string; host: HostBrief; score: number }

type PkView = {
  id: string
  phase: 'fighting' | 'result' | 'ended'
  round: number
  duration_sec: number
  ends_at_ms: number
  result_until_ms?: number
  left: PkSide
  right: PkSide
  winner_id?: string
}

type LiveRoom = {
  id: string
  host_id: string
  host: HostBrief
  kind: 'audio' | 'video'
  title: string
  cover_url?: string
  started_at_ms: number
  duration_seconds: number
  viewers: number
  peak_viewers: number
  guests: number
  audience: string
  locked: boolean
  host_away: boolean
  gift_coins: number
  seats: number
  requests: boolean
  pk?: PkView
  unique_viewers: number
  total_joins: number
  gift_count: number
  sfu_seats: number
  host_seen_ms: number
  hidden: boolean
  category: 'audio' | 'video' | 'pk'
}

type RoomsResp = {
  items: LiveRoom[]
  counts: { audio: number; video: number; pk: number; all: number }
  totals: { viewers: number; gift_coins: number }
}

type Person = {
  id: string
  username: string
  full_name: string
  avatar_url?: string
  role: 'host' | 'guest' | 'viewer'
  joined_at_ms?: number
  sender_level?: LevelView
  receiver_level?: LevelView
}

type PeopleResp = { items: Person[]; viewers: number }

const TABS: { kind: Kind; label: string }[] = [
  { kind: '', label: 'All' },
  { kind: 'audio', label: 'Audio' },
  { kind: 'video', label: 'Video' },
  { kind: 'pk', label: 'PK' },
]

const REFRESH_MS = 8000
const PEOPLE_PAGE = 50

function fmtDuration(total: number): string {
  const s = Math.max(0, Math.floor(total))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  return h > 0 ? `${h}h ${String(m).padStart(2, '0')}m` : `${m}:${String(sec).padStart(2, '0')}`
}

function fmtAgo(ms: number, now: number): string {
  if (!ms) return '—'
  const d = Math.max(0, now - ms)
  if (d < 1000 * 60) return `${Math.floor(d / 1000)}s ago`
  if (d < 1000 * 3600) return `${Math.floor(d / 60000)}m ago`
  return `${Math.floor(d / 3600000)}h ago`
}

function displayName(h: { full_name: string; username: string }): string {
  return h.full_name || `@${h.username}`
}

/**
 * Every open broadcast, as the app lists it, in one place: audio rooms,
 * video rooms, and the video rooms currently in a PK battle. Rows open the
 * roster; a moderator can end any room from here.
 */
export function LivePage() {
  const { can } = useAuth()
  const canEnd = can('live.end_any')
  const [kind, setKind] = useState<Kind>('')
  const [data, setData] = useState<RoomsResp | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<string>('')
  const [auto, setAuto] = useState(true)
  const [now, setNow] = useState(() => Date.now())
  const inflight = useRef<AbortController | null>(null)

  const load = useCallback(
    async (quiet: boolean) => {
      inflight.current?.abort()
      const ctl = new AbortController()
      inflight.current = ctl
      if (!quiet) setLoading(true)
      const params = new URLSearchParams({ limit: '300' })
      if (kind) params.set('kind', kind)
      const r = await get<RoomsResp>(`/v1/admin/live/rooms?${params}`, { signal: ctl.signal })
      if (ctl.signal.aborted) return
      setLoading(false)
      if (!r.ok) {
        if (!r.isTransport || !quiet) setError(r.message)
        return
      }
      setError('')
      setData(r.data)
      setNow(Date.now())
    },
    [kind],
  )

  useEffect(() => {
    void load(false)
    return () => inflight.current?.abort()
  }, [load])

  // Console refresh: paused while the tab is hidden or auto is off.
  useEffect(() => {
    if (!auto) return
    const tick = () => {
      if (document.visibilityState === 'visible') void load(true)
    }
    const id = window.setInterval(tick, REFRESH_MS)
    document.addEventListener('visibilitychange', tick)
    return () => {
      window.clearInterval(id)
      document.removeEventListener('visibilitychange', tick)
    }
  }, [auto, load])

  const rooms = data?.items || []
  const selectedRoom = useMemo(() => rooms.find((r) => r.id === selected) || null, [rooms, selected])

  useEffect(() => {
    if (selected && !selectedRoom && !loading) setSelected('')
  }, [selected, selectedRoom, loading])

  const onEnded = (id: string) => {
    setData((old) => (old ? { ...old, items: old.items.filter((r) => r.id !== id) } : old))
    if (selected === id) setSelected('')
    void load(true)
  }

  const counts = data?.counts || { audio: 0, video: 0, pk: 0, all: 0 }
  const totals = data?.totals || { viewers: 0, gift_coins: 0 }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head" style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: 240 }}>
          <h1>Live</h1>
          <p>Every broadcast open right now, as viewers see it. Open a row for who is inside; end a room when it must stop.</p>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <label className="muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
            <input type="checkbox" checked={auto} onChange={(e) => setAuto(e.target.checked)} />
            Auto refresh
          </label>
          <Button size="sm" variant="secondary" onClick={() => void load(true)}>
            Refresh
          </Button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12 }}>
        <Stat label="Live rooms" value={counts.all.toLocaleString()} />
        <Stat label="Audio" value={counts.audio.toLocaleString()} />
        <Stat label="Video" value={counts.video.toLocaleString()} />
        <Stat label="PK battles" value={counts.pk.toLocaleString()} />
        <Stat label={kind ? 'Viewers (tab)' : 'Viewers'} value={totals.viewers.toLocaleString()} />
        <Stat label={kind ? 'Gift coins (tab)' : 'Gift coins'} value={totals.gift_coins.toLocaleString()} />
      </div>

      <div className="tabs" style={{ marginBottom: 0 }}>
        {TABS.map((t) => {
          const n = t.kind === '' ? counts.all : counts[t.kind]
          return (
            <button
              key={t.kind || 'all'}
              type="button"
              className={`tab${kind === t.kind ? ' is-active' : ''}`}
              onClick={() => {
                setKind(t.kind)
                setSelected('')
              }}
            >
              {t.label} <span className="muted" style={{ fontSize: 12 }}>{n}</span>
            </button>
          )
        })}
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: selectedRoom ? 'minmax(0, 1fr) minmax(320px, 400px)' : 'minmax(0, 1fr)',
          gap: 14,
          alignItems: 'start',
        }}
      >
        <Card>
          {error ? <div className="err-banner">{error}</div> : null}
          {loading && rooms.length === 0 ? (
            <PageLoader label="Loading live rooms…" />
          ) : rooms.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>
              Nobody is live {kind ? `on ${TABS.find((t) => t.kind === kind)?.label.toLowerCase()}` : ''} right now.
            </p>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Host</th>
                    <th>Broadcast</th>
                    <th>Type</th>
                    <th>Viewers</th>
                    <th>Peak</th>
                    <th>Unique</th>
                    <th>Gifts</th>
                    <th>On air</th>
                    <th>Host</th>
                    {canEnd ? <th /> : null}
                  </tr>
                </thead>
                <tbody>
                  {rooms.map((room) => (
                    <RoomRow
                      key={room.id}
                      room={room}
                      now={now}
                      selected={room.id === selected}
                      canEnd={canEnd}
                      onSelect={() => setSelected((cur) => (cur === room.id ? '' : room.id))}
                      onEnded={() => onEnded(room.id)}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        {selectedRoom ? (
          <RoomPanel key={selectedRoom.id} room={selectedRoom} now={now} canEnd={canEnd} onClose={() => setSelected('')} onEnded={() => onEnded(selectedRoom.id)} />
        ) : null}
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        background: 'var(--surface)',
        border: '1px solid var(--line)',
        borderRadius: 'var(--radius)',
        padding: '12px 14px',
        boxShadow: 'var(--shadow)',
      }}
    >
      <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: 0.04 }}>{label}</div>
      <div style={{ marginTop: 6, fontSize: 20, fontWeight: 800, color: 'var(--ink)' }}>{value}</div>
    </div>
  )
}

function Avatar({ url, name, size = 28 }: { url?: string; name: string; size?: number }) {
  const { mediaBase } = useAuth()
  const src = url ? mediaUrl(mediaBase, url) : ''
  return src ? (
    <img src={src} alt="" style={{ width: size, height: size, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} loading="lazy" />
  ) : (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        background: 'var(--line)',
        display: 'inline-grid',
        placeItems: 'center',
        fontSize: size * 0.4,
        fontWeight: 700,
        color: 'var(--ink-soft)',
        flexShrink: 0,
      }}
    >
      {name.slice(0, 1).toUpperCase()}
    </span>
  )
}

function CategoryPill({ category }: { category: LiveRoom['category'] }) {
  const style: React.CSSProperties =
    category === 'pk'
      ? { background: 'linear-gradient(90deg,#8b5cf6,#ec4899)', color: '#fff' }
      : category === 'audio'
        ? { background: 'var(--wash-deep)', color: 'var(--ink-soft)' }
        : { background: 'var(--accent-soft)', color: 'var(--accent-hover)' }
  return (
    <span className="status-pill" style={style}>
      {category === 'pk' ? 'PK' : category === 'audio' ? 'Audio' : 'Video'}
    </span>
  )
}

function useEndRoom(room: LiveRoom, onEnded: () => void) {
  const [ending, setEnding] = useState(false)
  const [err, setErr] = useState('')
  const end = async () => {
    if (!window.confirm(`End “${room.title || 'this broadcast'}” by ${displayName(room.host)}? Everyone inside is disconnected.`)) return
    setEnding(true)
    setErr('')
    const r = await post(`/v1/admin/live/rooms/${room.id}/end`, { reason: 'admin_ended' })
    setEnding(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    onEnded()
  }
  return { end, ending, err }
}

function RoomRow({
  room,
  now,
  selected,
  canEnd,
  onSelect,
  onEnded,
}: {
  room: LiveRoom
  now: number
  selected: boolean
  canEnd: boolean
  onSelect: () => void
  onEnded: () => void
}) {
  const { end, ending } = useEndRoom(room, onEnded)
  const pk = room.pk
  const opp = pk ? (pk.left.room_id === room.id ? pk.right : pk.left) : null
  const mine = pk ? (pk.left.room_id === room.id ? pk.left : pk.right) : null
  return (
    <tr
      className="row-link"
      onClick={onSelect}
      style={selected ? { background: 'var(--accent-soft)' } : undefined}
    >
      <td>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <Avatar url={room.host.avatar_url} name={displayName(room.host)} />
          <span style={{ minWidth: 0 }}>
            <strong>{displayName(room.host)}</strong>
            <div className="muted" style={{ fontSize: 11 }}>
              @{room.host.username} · {room.host_id}
            </div>
          </span>
        </span>
      </td>
      <td style={{ maxWidth: 260 }}>
        <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{room.title || <span className="muted">Untitled</span>}</div>
        <div className="muted" style={{ fontSize: 11 }}>
          {room.audience}
          {room.locked ? ' · locked' : ''}
          {room.kind === 'audio' ? ` · ${room.guests + 1}/${room.seats} seats` : room.guests > 0 ? ` · ${room.guests} co-host${room.guests > 1 ? 's' : ''}` : ''}
          {opp && mine ? (
            <>
              {' · '}
              <span style={{ color: 'var(--ink)' }}>
                {mine.score.toLocaleString()} vs {opp.score.toLocaleString()} @{opp.host.username}
              </span>
            </>
          ) : null}
        </div>
      </td>
      <td>
        <CategoryPill category={room.category} />
      </td>
      <td>
        <strong>{room.viewers.toLocaleString()}</strong>
      </td>
      <td>{room.peak_viewers.toLocaleString()}</td>
      <td>{room.unique_viewers.toLocaleString()}</td>
      <td>
        {room.gift_coins.toLocaleString()}
        <span className="muted" style={{ fontSize: 11 }}> · {room.gift_count}</span>
      </td>
      <td>{fmtDuration((now - room.started_at_ms) / 1000)}</td>
      <td>
        {room.host_away || room.hidden ? (
          <span className="status-pill off">Away · {fmtAgo(room.host_seen_ms, now)}</span>
        ) : (
          <span className="status-pill on">Live</span>
        )}
      </td>
      {canEnd ? (
        <td onClick={(e) => e.stopPropagation()}>
          <Button size="sm" variant="danger" loading={ending} onClick={() => void end()}>
            End
          </Button>
        </td>
      ) : null}
    </tr>
  )
}

function RoomPanel({
  room,
  now,
  canEnd,
  onClose,
  onEnded,
}: {
  room: LiveRoom
  now: number
  canEnd: boolean
  onClose: () => void
  onEnded: () => void
}) {
  const [people, setPeople] = useState<Person[]>([])
  const [viewers, setViewers] = useState(0)
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')
  const { end, ending, err: endErr } = useEndRoom(room, onEnded)

  const load = useCallback(
    async (cursor: number, append: boolean) => {
      append ? setMore(true) : setLoading(true)
      const params = new URLSearchParams({ limit: String(PEOPLE_PAGE) })
      if (cursor > 0) params.set('cursor', String(cursor))
      const r = await get<PeopleResp>(`/v1/admin/live/rooms/${room.id}/people?${params}`)
      append ? setMore(false) : setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      const items = r.data.items || []
      setViewers(r.data.viewers)
      setPeople((old) => (append ? [...old, ...items] : items))
      setDone(items.filter((p) => p.role === 'viewer').length < PEOPLE_PAGE)
    },
    [room.id],
  )

  useEffect(() => {
    void load(0, false)
  }, [load])

  // Cursor = the oldest viewer join on the page so far (host / guests always lead).
  const nextCursor = useMemo(() => {
    let oldest = 0
    for (const p of people) {
      if (p.role === 'viewer' && p.joined_at_ms) oldest = oldest === 0 ? p.joined_at_ms : Math.min(oldest, p.joined_at_ms)
    }
    return oldest
  }, [people])

  const pk = room.pk
  return (
    <Card>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
        <Avatar url={room.host.avatar_url} name={displayName(room.host)} size={44} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 800, fontSize: 15 }}>{room.title || 'Untitled'}</div>
          <div className="muted" style={{ fontSize: 12 }}>
            {displayName(room.host)} · @{room.host.username}
          </div>
          <div className="chip-row">
            <CategoryPill category={room.category} />
            <span className="chip">{room.audience}</span>
            {room.locked ? <span className="chip">locked</span> : null}
            <span className="chip">room {room.id}</span>
          </div>
        </div>
        <button type="button" className="admin-icon-btn" aria-label="Close" onClick={onClose}>
          ✕
        </button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginTop: 14 }}>
        <Mini label="Watching" value={room.viewers.toLocaleString()} />
        <Mini label="Peak" value={room.peak_viewers.toLocaleString()} />
        <Mini label="Unique" value={room.unique_viewers.toLocaleString()} />
        <Mini label="Joins" value={room.total_joins.toLocaleString()} />
        <Mini label="Gift coins" value={room.gift_coins.toLocaleString()} />
        <Mini label="Gifts" value={room.gift_count.toLocaleString()} />
        <Mini label="On air" value={fmtDuration(room.duration_seconds)} />
        <Mini label="SFU seats" value={room.sfu_seats.toLocaleString()} />
        <Mini label="Host seen" value={fmtAgo(room.host_seen_ms, now)} />
      </div>

      {pk ? (
        <div
          style={{
            marginTop: 14,
            padding: 12,
            borderRadius: 'var(--radius-sm)',
            background: 'linear-gradient(90deg, rgba(139,92,246,.12), rgba(236,72,153,.12))',
            border: '1px solid var(--line)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12 }}>
            <strong>PK · round {pk.round}</strong>
            <span className="muted">
              {pk.phase === 'fighting'
                ? `${fmtDuration((pk.ends_at_ms - now) / 1000)} left`
                : pk.phase === 'result'
                  ? `result · ${fmtDuration(((pk.result_until_ms || now) - now) / 1000)}`
                  : 'ended'}
            </span>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr auto 1fr', gap: 8, alignItems: 'center', marginTop: 8 }}>
            <PkSideView side={pk.left} winner={pk.winner_id} align="left" />
            <span className="muted" style={{ fontSize: 11, fontWeight: 700 }}>
              VS
            </span>
            <PkSideView side={pk.right} winner={pk.winner_id} align="right" />
          </div>
          <PkBar left={pk.left.score} right={pk.right.score} />
        </div>
      ) : null}

      {canEnd ? (
        <div style={{ marginTop: 14 }}>
          <Button variant="danger" size="sm" loading={ending} onClick={() => void end()} style={{ width: '100%' }}>
            End broadcast
          </Button>
          {endErr ? <div className="err-banner" style={{ marginTop: 8 }}>{endErr}</div> : null}
        </div>
      ) : null}

      <div style={{ marginTop: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <strong style={{ fontSize: 13 }}>Inside now</strong>
        <span className="muted" style={{ fontSize: 12 }}>
          {viewers.toLocaleString()} watching
        </span>
      </div>
      {error ? <div className="err-banner" style={{ marginTop: 8 }}>{error}</div> : null}
      {loading && people.length === 0 ? (
        <PageLoader label="Loading people…" />
      ) : people.length === 0 ? (
        <p className="muted" style={{ margin: '8px 0 0' }}>
          Nobody inside.
        </p>
      ) : (
        <div className="role-list" style={{ marginTop: 8, maxHeight: 520, overflowY: 'auto' }}>
          {people.map((p) => (
            <div key={p.id} className="role-row" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <Avatar url={p.avatar_url} name={displayName(p)} size={32} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="role-row-title" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{displayName(p)}</span>
                  <span className={`status-pill ${p.role === 'viewer' ? 'off' : 'on'}`} style={{ fontSize: 10 }}>
                    {p.role}
                  </span>
                </div>
                <div className="role-row-meta">
                  @{p.username} · {p.id}
                  {p.joined_at_ms ? ` · joined ${fmtAgo(p.joined_at_ms, now)}` : ''}
                </div>
              </div>
              <LevelBadge level={p.receiver_level || p.sender_level} size={18} />
            </div>
          ))}
        </div>
      )}
      {!done && people.length > 0 && nextCursor > 0 ? (
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 10 }}>
          <Button size="sm" variant="ghost" loading={more} onClick={() => void load(nextCursor, true)}>
            Load more
          </Button>
        </div>
      ) : null}
    </Card>
  )
}

function Mini({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ background: 'var(--wash)', borderRadius: 'var(--radius-sm)', padding: '8px 10px' }}>
      <div className="muted" style={{ fontSize: 10, textTransform: 'uppercase', letterSpacing: 0.04 }}>{label}</div>
      <div style={{ fontWeight: 700, fontSize: 14 }}>{value}</div>
    </div>
  )
}

function PkSideView({ side, winner, align }: { side: PkSide; winner?: string; align: 'left' | 'right' }) {
  const won = winner && winner === side.host.id
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexDirection: align === 'right' ? 'row-reverse' : 'row', minWidth: 0 }}>
      <Avatar url={side.host.avatar_url} name={displayName(side.host)} size={30} />
      <div style={{ minWidth: 0, textAlign: align }}>
        <div style={{ fontSize: 12, fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {displayName(side.host)} {won ? '🏆' : ''}
        </div>
        <div className="muted" style={{ fontSize: 11 }}>
          {side.score.toLocaleString()} coins
        </div>
      </div>
    </div>
  )
}

function PkBar({ left, right }: { left: number; right: number }) {
  const total = Math.max(1, left + right)
  const frac = left + right === 0 ? 0.5 : Math.min(0.95, Math.max(0.05, left / total))
  return (
    <div style={{ marginTop: 8, height: 6, borderRadius: 3, background: 'rgba(236,72,153,.35)', overflow: 'hidden' }}>
      <div style={{ width: `${frac * 100}%`, height: '100%', background: '#8b5cf6', transition: 'width 300ms ease' }} />
    </div>
  )
}
