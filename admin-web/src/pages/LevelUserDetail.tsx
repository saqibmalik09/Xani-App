import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { get } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { LevelAsset } from './Levels'
import type { LevelPerson, LevelView } from './LevelUsers'

type Person = { id: string; username: string; full_name: string; avatar_url?: string }

type GiftRecord = {
  id: string
  room_id: string
  sender: Person
  receiver: Person
  gift_id: string
  gift_title: string
  listing_url?: string
  unit_coins: number
  count: number
  total_coins: number
  receiver_diamonds: number
  platform_diamonds: number
  combo: number
  at: string
}

type ListResp<T> = { items: T[]; next_cursor?: string }

function personLabel(p?: Person): string {
  if (!p) return '—'
  return p.full_name || (p.username ? `@${p.username}` : p.id)
}

/** One ladder's standing: picture, level, and the bar to the next rung. */
function LadderCard({ title, coins, count, level, nextAt }: { title: string; coins: number; count: number; level?: LevelView; nextAt: number }) {
  const base = level?.threshold_coins ?? 0
  const span = nextAt > base ? nextAt - base : 0
  const pct = span > 0 ? Math.min(100, Math.round(((coins - base) / span) * 100)) : level ? 100 : 0
  return (
    <Card title={title}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <div
          style={{
            width: 56,
            height: 56,
            borderRadius: 12,
            background: 'var(--surface-2, var(--line))',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {level ? <LevelAsset url={level.asset_url || ''} mime={level.asset_mime || ''} size={44} /> : <span className="muted">—</span>}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 16 }}>
            {level ? `Level ${level.level} · ${level.slug}` : 'No level yet'}
            {level?.title ? <span className="muted"> — {level.title}</span> : null}
          </div>
          <div className="muted" style={{ fontSize: 12 }}>
            {coins.toLocaleString()} coins · {count.toLocaleString()} gifts
          </div>
          <div style={{ marginTop: 8, height: 6, borderRadius: 3, background: 'var(--line)', overflow: 'hidden' }}>
            <div style={{ width: `${pct}%`, height: '100%', background: 'var(--accent, #6d5dfc)' }} />
          </div>
          <div className="muted" style={{ fontSize: 11, marginTop: 4 }}>
            {nextAt > 0 ? `${(nextAt - coins).toLocaleString()} more coins to the next level (${nextAt.toLocaleString()})` : level ? 'Top of the ladder' : 'No ladder configured'}
          </div>
        </div>
      </div>
    </Card>
  )
}

/** Gift history one way: what this person sent, or what they received. */
function HistoryPanel({ userId, dir }: { userId: string; dir: 'sent' | 'received' }) {
  const [items, setItems] = useState<GiftRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [cursor, setCursor] = useState('')
  const [error, setError] = useState('')

  const load = useCallback(
    async (before: string, append: boolean) => {
      append ? setMore(true) : setLoading(true)
      const params = new URLSearchParams({ user_id: userId, dir, limit: '50' })
      if (before) params.set('before_id', before)
      const r = await get<ListResp<GiftRecord>>(`/v1/admin/live-gifts?${params}`)
      append ? setMore(false) : setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setItems((old) => (append ? [...old, ...(r.data.items || [])] : r.data.items || []))
      setCursor(r.data.next_cursor || '')
    },
    [userId, dir],
  )

  useEffect(() => {
    void load('', false)
  }, [load])

  const total = items.reduce((n, g) => n + g.total_coins, 0)
  const sent = dir === 'sent'

  return (
    <Card title={sent ? 'Gifts sent' : 'Gifts received'}>
      {error ? <div className="err-banner">{error}</div> : null}
      <p className="muted" style={{ margin: '0 0 10px', fontSize: 12 }}>
        {items.length} record{items.length === 1 ? '' : 's'} shown · {total.toLocaleString()} coins
      </p>
      {loading && items.length === 0 ? (
        <PageLoader label="Loading…" />
      ) : items.length === 0 ? (
        <p className="muted" style={{ margin: 0 }}>
          Nothing {sent ? 'sent' : 'received'} yet.
        </p>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Date &amp; time</th>
                <th>{sent ? 'To' : 'From'}</th>
                <th>Gift</th>
                <th>Type</th>
                <th>Price</th>
                <th>Qty</th>
                <th>Total coins</th>
                <th>Room</th>
              </tr>
            </thead>
            <tbody>
              {items.map((g) => {
                const other = sent ? g.receiver : g.sender
                return (
                  <tr key={g.id}>
                    <td className="muted" style={{ whiteSpace: 'nowrap', fontSize: 12 }}>
                      {new Date(g.at).toLocaleString()}
                    </td>
                    <td>
                      <Link to={`/level-users/${other?.id}`}>
                        <strong>{personLabel(other)}</strong>
                      </Link>
                      <div className="muted" style={{ fontSize: 11 }}>
                        {other?.username ? `@${other.username}` : other?.id}
                      </div>
                    </td>
                    <td>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        {g.listing_url ? <img src={g.listing_url} alt="" loading="lazy" style={{ width: 28, height: 28, objectFit: 'contain' }} /> : null}
                        {g.gift_title}
                      </span>
                    </td>
                    <td className="muted">{g.combo > 1 ? `Combo ×${g.combo}` : 'Live gift'}</td>
                    <td>{g.unit_coins.toLocaleString()}</td>
                    <td>{g.count}</td>
                    <td>
                      <strong>{g.total_coins.toLocaleString()}</strong>
                    </td>
                    <td className="muted" style={{ fontSize: 11 }}>
                      {g.room_id}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {cursor ? (
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 12 }}>
          <Button size="sm" variant="ghost" loading={more} onClick={() => void load(cursor, true)}>
            Load more
          </Button>
        </div>
      ) : null}
    </Card>
  )
}

/**
 * One person's gifting: standing on both ladders, then who they gifted and
 * who gifted them, with gift, price, type and date/time.
 */
export function LevelUserDetailPage() {
  const { id = '' } = useParams()
  const { mediaBase } = useAuth()
  const [person, setPerson] = useState<LevelPerson | null>(null)
  const [error, setError] = useState('')
  const [tab, setTab] = useState<'sent' | 'received'>('sent')

  useEffect(() => {
    let alive = true
    setPerson(null)
    void get<LevelPerson>(`/v1/admin/levels/users/${id}`).then((r) => {
      if (!alive) return
      if (!r.ok) {
        setError(r.message)
        return
      }
      setPerson(r.data)
    })
    return () => {
      alive = false
    }
  }, [id])

  if (error) return <div className="err-banner">{error}</div>
  if (!person) return <PageLoader label="Loading…" />

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head" style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        {person.avatar_url ? (
          <img src={mediaUrl(mediaBase, person.avatar_url)} alt="" style={{ width: 56, height: 56, borderRadius: '50%', objectFit: 'cover' }} />
        ) : (
          <span style={{ width: 56, height: 56, borderRadius: '50%', background: 'var(--line)' }} />
        )}
        <div style={{ flex: 1 }}>
          <h1 style={{ margin: 0 }}>{person.full_name || `@${person.username}`}</h1>
          <p style={{ margin: 0 }}>
            @{person.username} · id {person.id} · <Link to={`/users/${person.id}`}>Open account</Link> ·{' '}
            <Link to="/level-users">Back to level users</Link>
          </p>
        </div>
      </div>

      <div className="catalog-split">
        <LadderCard title="Sender level" coins={person.sent_coins} count={person.sent_count} level={person.sender_level} nextAt={person.sender_next} />
        <LadderCard
          title="Receiver level"
          coins={person.received_coins}
          count={person.received_count}
          level={person.receiver_level}
          nextAt={person.receiver_next}
        />
      </div>

      <div className="tabs">
        <button type="button" className={`tab${tab === 'sent' ? ' is-active' : ''}`} onClick={() => setTab('sent')}>
          Sent ({person.sent_count.toLocaleString()})
        </button>
        <button type="button" className={`tab${tab === 'received' ? ' is-active' : ''}`} onClick={() => setTab('received')}>
          Received ({person.received_count.toLocaleString()})
        </button>
      </div>
      <HistoryPanel key={`${id}:${tab}`} userId={id} dir={tab} />
    </div>
  )
}
