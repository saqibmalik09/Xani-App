import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { get } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type Person = { id: string; username: string; full_name: string; avatar_url?: string }
type CatTotal = { count: number; coins: number; diamonds: number; beans: number }
type Sums = {
  spent_coins: number
  earned_coins: number
  earned_diamonds: number
  withdrawn_diamonds: number
  earned_beans: number
  spent_beans: number
}
type Statement = {
  from: string
  to: string
  balance: { coins: number; diamonds: number; beans: number }
  totals: Sums
  categories: Record<string, CatTotal>
  days: { day: string; count: number; spent_coins: number; earned_coins: number; earned_diamonds: number; withdraw_diamonds: number }[]
  lifetime: { totals: Sums; categories: Record<string, CatTotal> }
  recharge: { count: number; coins: number; pending_count: number }
}
type Entry = {
  id: string
  kind: number
  category: string
  coins: number
  diamonds: number
  beans: number
  note: string
  ref: string
  peer?: Person
  actor?: Person
  at_ms: number
}
type GiftGroup = { user: Person; count: number; coins: number; diamonds: number; last_at_ms: number }
type GiftLine = { id: string; gift_title: string; unit_coins: number; count: number; total_coins: number; diamonds: number; at_ms: number }
type UserBrief = { id: string; username: string; full_name: string; avatar_url?: string }

/** Every bucket the ledger can be sliced into, in the order money flows. */
const CATEGORIES: { key: string; label: string; dir: 'in' | 'out' | 'both'; hint: string }[] = [
  { key: 'topups', label: 'Recharges', dir: 'in', hint: 'Coins bought and credited after approval.' },
  { key: 'adjustments', label: 'Adjustments', dir: 'both', hint: 'Staff grants or deductions; each line names who did it.' },
  { key: 'gifts_received', label: 'Gifts received', dir: 'in', hint: 'Diamonds / beans earned from gifts, by sender.' },
  { key: 'game_wins', label: 'Game wins', dir: 'in', hint: 'Coins paid out by games.' },
  { key: 'gifts_sent', label: 'Gifts sent', dir: 'out', hint: 'Coins spent on gifts, by receiver.' },
  { key: 'store', label: 'Store', dir: 'out', hint: 'Frames, entry effects and other store items.' },
  { key: 'game_bets', label: 'Game bets', dir: 'out', hint: 'Coins staked in games.' },
  { key: 'withdrawals', label: 'Withdrawals', dir: 'out', hint: 'Diamonds held for payout (refunds come back as adjustments).' },
  { key: 'platform', label: 'Platform cut', dir: 'both', hint: 'The house share on gifts and store sales (platform account only).' },
]

type Range = 'all' | '7' | '30' | '90'

function fmtWhen(ms?: number): string {
  if (!ms) return '—'
  return new Date(ms).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function signed(n: number): string {
  if (!n) return ''
  return (n > 0 ? '+' : '−') + Math.abs(n).toLocaleString()
}

function rangeParams(range: Range): URLSearchParams {
  const q = new URLSearchParams()
  if (range === 'all') {
    q.set('all', '1')
    return q
  }
  const days = Number(range)
  const to = new Date()
  const from = new Date(to.getTime() - (days - 1) * 86_400_000)
  const day = (d: Date) => d.toISOString().slice(0, 10)
  q.set('from', day(from))
  q.set('to', day(to))
  q.set('tz', String(-new Date().getTimezoneOffset()))
  return q
}

function personName(p?: Person): string {
  if (!p) return ''
  return p.full_name || (p.username ? `@${p.username}` : `#${p.id}`)
}

/**
 * One user's money, end to end. Where every coin, diamond and bean came
 * from and where it went: totals per bucket, then the lines behind any
 * bucket with the other person, the staff member and the moment. This is
 * the page to open when a balance looks wrong.
 */
export function UserLedgerPage() {
  const { id = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const range = (params.get('range') as Range) || 'all'
  const category = params.get('cat') || ''
  const { mediaBase } = useAuth()

  const [user, setUser] = useState<UserBrief | null>(null)
  const [stmt, setStmt] = useState<Statement | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const setParam = (k: string, v: string) => {
    const next = new URLSearchParams(params)
    if (v) next.set(k, v)
    else next.delete(k)
    setParams(next, { replace: true })
  }

  useEffect(() => {
    let alive = true
    void get<UserBrief>(`/v1/admin/users/${id}`).then((r) => {
      if (alive && r.ok) setUser(r.data)
    })
    return () => {
      alive = false
    }
  }, [id])

  const load = useCallback(async () => {
    setLoading(true)
    const q = rangeParams(range)
    const s = await get<Statement>(`/v1/admin/users/${id}/transactions?${q}`)
    setLoading(false)
    if (!s.ok) {
      setError(s.message)
      return
    }
    setError('')
    setStmt(s.data)
  }, [id, range])

  useEffect(() => {
    void load()
  }, [load])

  const cats = stmt?.categories || {}
  const life = stmt?.lifetime.categories || {}
  const windowed = range !== 'all'
  const face = user?.avatar_url ? mediaUrl(mediaBase, user.avatar_url) : ''
  const name = user ? user.full_name || `@${user.username}` : `#${id}`

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head withdraw-head">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {face ? <img src={face} alt="" className="withdraw-av" style={{ width: 40, height: 40 }} /> : null}
          <div>
            <h1 style={{ margin: 0 }}>Ledger · {name}</h1>
            <p style={{ margin: 0 }}>
              <Link to={`/users/${id}`}>← Profile</Link> · <Link to={`/recharge?user_id=${id}`}>Recharge requests</Link>
            </p>
          </div>
        </div>
        <div className="tabs" style={{ marginBottom: 0 }}>
          {(['all', '7', '30', '90'] as Range[]).map((r) => (
            <button key={r} type="button" className={`tab${range === r ? ' is-active' : ''}`} onClick={() => setParam('range', r === 'all' ? '' : r)}>
              {r === 'all' ? 'Lifetime' : `${r} days`}
            </button>
          ))}
        </div>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

      {loading && !stmt ? (
        <PageLoader label="Reading the ledger…" />
      ) : stmt ? (
        <>
          <div className="withdraw-stats">
            <span>
              <em>Coins now</em> <strong>{stmt.balance.coins.toLocaleString()}</strong>
            </span>
            <span>
              <em>Diamonds now</em> <strong>{stmt.balance.diamonds.toLocaleString()}</strong>
            </span>
            <span>
              <em>Beans now</em> <strong>{stmt.balance.beans.toLocaleString()}</strong>
            </span>
            <span className="is-ok">
              <em>Coins in{windowed ? '' : ' (life)'}</em> <strong>+{stmt.totals.earned_coins.toLocaleString()}</strong>
            </span>
            <span className="is-warn">
              <em>Coins out{windowed ? '' : ' (life)'}</em> <strong>−{stmt.totals.spent_coins.toLocaleString()}</strong>
            </span>
            <span>
              <em>Diamonds in</em> <strong>+{stmt.totals.earned_diamonds.toLocaleString()}</strong>
            </span>
            <span>
              <em>Diamonds withdrawn</em> <strong>−{stmt.totals.withdrawn_diamonds.toLocaleString()}</strong>
            </span>
            <span>
              <em>Beans in / out</em>{' '}
              <strong>
                +{stmt.totals.earned_beans.toLocaleString()} / −{stmt.totals.spent_beans.toLocaleString()}
              </strong>
            </span>
            <span className={stmt.recharge.pending_count > 0 ? 'is-warn' : ''}>
              <em>Recharges</em>{' '}
              <strong>
                {stmt.recharge.count} · 🪙 {stmt.recharge.coins.toLocaleString()}
                {stmt.recharge.pending_count ? ` · ${stmt.recharge.pending_count} pending` : ''}
              </strong>
            </span>
          </div>

          <Card title={windowed ? `Where it came from and went · ${stmt.from} → ${stmt.to}` : 'Where it came from and went · lifetime'}>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Bucket</th>
                    <th className="num">Lines</th>
                    <th className="num">Coins</th>
                    <th className="num">Diamonds</th>
                    <th className="num">Beans</th>
                    {windowed ? <th className="num">Lifetime coins</th> : null}
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {CATEGORIES.map((c) => {
                    const t = cats[c.key]
                    const l = life[c.key]
                    if (!t && !l) return null
                    const sign = c.dir === 'in' ? '+' : c.dir === 'out' ? '−' : '±'
                    const active = category === c.key
                    return (
                      <tr key={c.key} className={active ? 'is-active' : ''} style={active ? { background: 'var(--surface-2, rgba(0,0,0,0.03))' } : undefined}>
                        <td>
                          <div style={{ fontWeight: 600 }}>{c.label}</div>
                          <div className="muted" style={{ fontSize: 12 }}>
                            {c.hint}
                          </div>
                        </td>
                        <td className="num">{(t?.count ?? 0).toLocaleString()}</td>
                        <td className="num">{t?.coins ? `${sign}${t.coins.toLocaleString()}` : '—'}</td>
                        <td className="num">{t?.diamonds ? `${sign}${t.diamonds.toLocaleString()}` : '—'}</td>
                        <td className="num">{t?.beans ? `${sign}${t.beans.toLocaleString()}` : '—'}</td>
                        {windowed ? <td className="num muted">{l?.coins ? l.coins.toLocaleString() : '—'}</td> : null}
                        <td className="nowrap">
                          <Button size="sm" variant={active ? 'primary' : 'ghost'} onClick={() => setParam('cat', active ? '' : c.key)}>
                            {active ? 'Hide' : 'Lines'}
                          </Button>
                          {c.key === 'gifts_sent' || c.key === 'gifts_received' ? (
                            <Button size="sm" variant="ghost" onClick={() => setParam('cat', `${c.key}:people`)}>
                              By person
                            </Button>
                          ) : null}
                        </td>
                      </tr>
                    )
                  })}
                  {Object.keys(cats).length === 0 && Object.keys(life).length === 0 ? (
                    <tr>
                      <td colSpan={windowed ? 7 : 6} className="muted">
                        No ledger lines yet.
                      </td>
                    </tr>
                  ) : null}
                </tbody>
              </table>
            </div>
          </Card>

          {category.endsWith(':people') ? (
            <GiftPeople userID={id} range={range} dir={category.startsWith('gifts_sent') ? 'sent' : 'received'} />
          ) : category ? (
            <Entries key={`${category}-${range}`} userID={id} range={range} category={category} />
          ) : (
            <Entries key={`all-${range}`} userID={id} range={range} category="" />
          )}
        </>
      ) : null}
    </div>
  )
}

const PAGE = 50

function Entries({ userID, range, category }: { userID: string; range: Range; category: string }) {
  const [rows, setRows] = useState<Entry[]>([])
  const [cursor, setCursor] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const inflight = useRef<AbortController | null>(null)
  const label = useMemo(() => CATEGORIES.find((c) => c.key === category)?.label || 'All lines', [category])

  const load = useCallback(
    async (before: string, append: boolean) => {
      inflight.current?.abort()
      const ctl = new AbortController()
      inflight.current = ctl
      if (append) setMore(true)
      else setLoading(true)
      const q = rangeParams(range)
      q.set('limit', String(PAGE))
      if (category) q.set('category', category)
      if (before) q.set('before_id', before)
      const r = await get<{ items: Entry[]; next_cursor: string }>(`/v1/admin/users/${userID}/transactions/entries?${q}`, { signal: ctl.signal })
      if (ctl.signal.aborted) return
      if (append) setMore(false)
      else setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      const items = r.data.items || []
      setRows((old) => (append ? [...old, ...items] : items))
      setCursor(r.data.next_cursor || '')
    },
    [userID, range, category],
  )

  useEffect(() => {
    void load('', false)
    return () => inflight.current?.abort()
  }, [load])

  return (
    <Card title={`${label} · newest first`}>
      {error ? <div className="err-banner">{error}</div> : null}
      {loading && rows.length === 0 ? (
        <PageLoader label="Loading lines…" />
      ) : rows.length === 0 ? (
        <p className="muted" style={{ margin: 0 }}>
          Nothing in this bucket for the chosen range.
        </p>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Bucket</th>
                <th>What</th>
                <th>With</th>
                <th>By</th>
                <th className="num">Coins</th>
                <th className="num">Diamonds</th>
                <th className="num">Beans</th>
                <th>Ref</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((e) => (
                <tr key={e.id}>
                  <td className="nowrap muted">{fmtWhen(e.at_ms)}</td>
                  <td className="nowrap">{CATEGORIES.find((c) => c.key === e.category)?.label || e.category || `kind ${e.kind}`}</td>
                  <td>{e.note || <span className="muted">—</span>}</td>
                  <td className="nowrap">
                    {e.peer ? <Link to={`/users/${e.peer.id}/ledger`}>{personName(e.peer)}</Link> : <span className="muted">—</span>}
                  </td>
                  <td className="nowrap">
                    {e.actor ? <Link to={`/users/${e.actor.id}`}>{personName(e.actor)}</Link> : <span className="muted">—</span>}
                  </td>
                  <td className="num" style={{ color: e.coins > 0 ? 'var(--ok, #16a34a)' : e.coins < 0 ? 'var(--danger, #ef4444)' : undefined }}>
                    {signed(e.coins) || '—'}
                  </td>
                  <td className="num" style={{ color: e.diamonds > 0 ? 'var(--ok, #16a34a)' : e.diamonds < 0 ? 'var(--danger, #ef4444)' : undefined }}>
                    {signed(e.diamonds) || '—'}
                  </td>
                  <td className="num" style={{ color: e.beans > 0 ? 'var(--ok, #16a34a)' : e.beans < 0 ? 'var(--danger, #ef4444)' : undefined }}>
                    {signed(e.beans) || '—'}
                  </td>
                  <td className="muted" style={{ fontSize: 11, maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={e.ref}>
                    {e.ref}
                  </td>
                </tr>
              ))}
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

function GiftPeople({ userID, range, dir }: { userID: string; range: Range; dir: 'sent' | 'received' }) {
  const [groups, setGroups] = useState<GiftGroup[]>([])
  const [totals, setTotals] = useState<{ count: number; coins: number; diamonds: number } | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [open, setOpen] = useState<Person | null>(null)

  useEffect(() => {
    let alive = true
    setLoading(true)
    const q = rangeParams(range)
    q.set('dir', dir)
    q.set('limit', '100')
    void get<{ items: GiftGroup[]; totals: { count: number; coins: number; diamonds: number } }>(`/v1/admin/users/${userID}/transactions/gifts?${q}`).then((r) => {
      if (!alive) return
      setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setGroups(r.data.items || [])
      setTotals(r.data.totals)
    })
    return () => {
      alive = false
    }
  }, [userID, range, dir])

  return (
    <>
      <Card title={dir === 'sent' ? 'Gifts sent · by receiver' : 'Gifts received · by sender'}>
        {error ? <div className="err-banner">{error}</div> : null}
        {totals ? (
          <p className="muted" style={{ margin: '0 0 10px', fontSize: 13 }}>
            {totals.count.toLocaleString()} gifts · 🪙 {totals.coins.toLocaleString()} · 💎 {totals.diamonds.toLocaleString()}
          </p>
        ) : null}
        {loading ? (
          <PageLoader label="Folding gifts…" />
        ) : groups.length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>
            No gifts in this range.
          </p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>{dir === 'sent' ? 'Receiver' : 'Sender'}</th>
                  <th className="num">Gifts</th>
                  <th className="num">Coins</th>
                  <th className="num">Diamonds</th>
                  <th>Last</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {groups.map((g) => (
                  <tr key={g.user.id}>
                    <td>
                      <Link to={`/users/${g.user.id}/ledger`}>{personName(g.user)}</Link>
                      <div className="muted" style={{ fontSize: 12 }}>
                        @{g.user.username}
                      </div>
                    </td>
                    <td className="num">{g.count.toLocaleString()}</td>
                    <td className="num">{g.coins.toLocaleString()}</td>
                    <td className="num">{g.diamonds.toLocaleString()}</td>
                    <td className="nowrap muted">{fmtWhen(g.last_at_ms)}</td>
                    <td className="nowrap">
                      <Button size="sm" variant={open?.id === g.user.id ? 'primary' : 'ghost'} onClick={() => setOpen(open?.id === g.user.id ? null : g.user)}>
                        {open?.id === g.user.id ? 'Hide' : 'Gifts'}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {open ? <GiftsWith key={open.id} userID={userID} other={open} range={range} dir={dir} /> : null}
    </>
  )
}

function GiftsWith({ userID, other, range, dir }: { userID: string; other: Person; range: Range; dir: 'sent' | 'received' }) {
  const [rows, setRows] = useState<GiftLine[]>([])
  const [cursor, setCursor] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(
    async (before: string, append: boolean) => {
      if (append) setMore(true)
      else setLoading(true)
      const q = rangeParams(range)
      q.set('dir', dir)
      q.set('limit', String(PAGE))
      if (before) q.set('before_id', before)
      const r = await get<{ items: GiftLine[]; next_cursor: string }>(`/v1/admin/users/${userID}/transactions/gifts/${other.id}?${q}`)
      if (append) setMore(false)
      else setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      const items = r.data.items || []
      setRows((old) => (append ? [...old, ...items] : items))
      setCursor(r.data.next_cursor || '')
    },
    [userID, other.id, range, dir],
  )

  useEffect(() => {
    void load('', false)
  }, [load])

  return (
    <Card title={`${dir === 'sent' ? 'Sent to' : 'Received from'} ${personName(other)}`}>
      {error ? <div className="err-banner">{error}</div> : null}
      {loading && rows.length === 0 ? (
        <PageLoader label="Loading gifts…" />
      ) : rows.length === 0 ? (
        <p className="muted" style={{ margin: 0 }}>
          No gifts.
        </p>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Gift</th>
                <th className="num">Count</th>
                <th className="num">Coins</th>
                <th className="num">Diamonds</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((g) => (
                <tr key={g.id}>
                  <td className="nowrap muted">{fmtWhen(g.at_ms)}</td>
                  <td>
                    {g.gift_title} <span className="muted">· {g.unit_coins.toLocaleString()} each</span>
                  </td>
                  <td className="num">{g.count}</td>
                  <td className="num">{g.total_coins.toLocaleString()}</td>
                  <td className="num">{g.diamonds.toLocaleString()}</td>
                </tr>
              ))}
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
