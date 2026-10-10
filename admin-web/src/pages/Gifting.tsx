import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { Select } from '../ui/Org'
import { Avatar, Stat, displayName, fmtClock } from './LiveTime'
import {
  giftingApi,
  n,
  pct,
  t,
  type Brief,
  type Detail,
  type GiftRow,
  type Health,
  type PeerRow,
  type Policy,
  type PolicyVersion,
  type ShareRow,
  type Statement,
  type Stats,
  type Summary,
  type Totals,
  type Tree,
  type TreeCategory,
  type Vocabulary,
} from '../lib/gifting'
import { PolicyBuilder, builderIsValid, calcText, emptyBuilder, flattenTree, type BuilderValue } from './GiftPolicyBuilder'

type PersonData = { user?: Brief; summary: Summary; received: Statement; sent: Statement; shares: ShareRow[] }

/**
 * Gifting dashboard. Rules are policies (versioned, immutable history); the
 * catalog tree shows where rules live; records are the facts with their money
 * trail. Everything is rendered as English; codes never reach the operator.
 */

export function GiftingPage() {
  return (
    <Routes>
      <Route index element={<Overview />} />
      <Route path="policies" element={<Policies />} />
      <Route path="policies/new" element={<PolicyNew />} />
      <Route path="policies/:id" element={<PolicyDetail />} />
      <Route path="catalog" element={<CatalogTree />} />
      <Route path="records" element={<Records />} />
      <Route path="records/:id" element={<RecordDetail />} />
      <Route path="users/:id" element={<Person />} />
      <Route path="*" element={<Overview />} />
    </Routes>
  )
}

function useVocab() {
  const [v, setV] = useState<Vocabulary | null>(null)
  useEffect(() => {
    void (async () => {
      const r = await giftingApi.vocab()
      if (r.ok) setV(r.data)
    })()
  }, [])
  return v
}

function useTree() {
  const [tree, setTree] = useState<Tree | null>(null)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    const r = await giftingApi.tree()
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setTree(r.data)
  }, [])
  useEffect(() => {
    void load()
  }, [load])
  return { tree, error, reload: load }
}

function SectionNav() {
  const items = [
    ['/gifting', 'Overview'],
    ['/gifting/policies', 'Policies'],
    ['/gifting/catalog', 'Catalog tree'],
    ['/gifting/records', 'Records'],
    ['/settings/gifting', 'Settings'],
  ]
  return (
    <>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', margin: '8px 0 16px' }}>
        {items.map(([to, label]) => (
          <Link key={to} to={to} className="status-pill" style={{ textDecoration: 'none', fontSize: 12 }}>
            {label}
          </Link>
        ))}
      </div>
      <KillBanner />
    </>
  )
}

/** Shown on every gifting page while the global switch is off. */
function KillBanner() {
  const { can } = useAuth()
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    const r = await giftingApi.health()
    if (r.ok) setEnabled(!!r.data.enabled)
  }, [])
  useEffect(() => {
    void load()
  }, [load])
  const turnOn = async () => {
    setBusy(true)
    const r = await giftingApi.saveSettings({ enabled: true })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    void load()
  }
  if (enabled !== false) return null
  return (
    <div className="form-error" style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', marginBottom: 16 }}>
      <div style={{ flex: 1 }}>
        <strong>{t('kill.off')}</strong>
        {error ? <div style={{ marginTop: 4 }}>{error}</div> : null}
      </div>
      {can('admin.manage_settings') ? (
        <Button size="sm" loading={busy} onClick={() => void turnOn()}>
          Turn on now
        </Button>
      ) : null}
      <Link to="/settings/gifting" className="status-pill" style={{ textDecoration: 'none', fontSize: 12 }}>
        Settings
      </Link>
    </div>
  )
}

function Who({ b, size = 28 }: { b?: Brief; size?: number }) {
  if (!b) return <span className="muted">—</span>
  return (
    <Link to={`/gifting/users/${b.id}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 8, textDecoration: 'none', color: 'inherit' }}>
      <Avatar url={b.avatar_url} name={displayName(b) || b.id} size={size} />
      <span>
        <strong>{displayName(b) || `#${b.id}`}</strong>
        <span className="muted" style={{ display: 'block', fontSize: 11 }}>
          {b.username ? `@${b.username} · ` : ''}#{b.id}
        </span>
      </span>
    </Link>
  )
}

function Pill({ tone, children }: { tone: 'ok' | 'warn' | 'bad' | 'muted'; children: React.ReactNode }) {
  const style: React.CSSProperties =
    tone === 'ok'
      ? { background: 'var(--accent-soft)', color: 'var(--accent-hover)' }
      : tone === 'warn'
        ? { background: '#fef3c7', color: '#92400e' }
        : tone === 'bad'
          ? { background: 'var(--danger)', color: '#fff' }
          : { background: 'var(--surface-2, var(--surface))', color: 'var(--ink-soft)', border: '1px solid var(--line)' }
  return (
    <span className="status-pill" style={style}>
      {children}
    </span>
  )
}

const statusTone = (s: string): 'ok' | 'warn' | 'bad' | 'muted' => (s === 'active' ? 'ok' : s === 'suspended' ? 'warn' : s === 'retired' ? 'bad' : 'muted')

// ─── Overview ────────────────────────────────────────────────────────────────

function Overview() {
  const [h, setH] = useState<Health | null>(null)
  const [stats, setStats] = useState<Stats | null>(null)
  const [period, setPeriod] = useState('month')
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    const [a, b] = await Promise.all([giftingApi.health(), giftingApi.stats(period)])
    if (!a.ok) {
      setError(a.message)
      return
    }
    setError('')
    setH(a.data)
    if (b.ok) setStats(b.data)
  }, [period])
  useEffect(() => {
    void load()
    const id = window.setInterval(() => void load(), 10_000)
    return () => window.clearInterval(id)
  }, [load])

  const hs = h?.health
  const max = Math.max(1, ...(stats?.series.map((s) => s.coins) ?? [1]))
  return (
    <div>
      <div className="page-head">
        <h1>{t('page.overview')}</h1>
        <p>{t('page.overview.sub')}</p>
      </div>
      <SectionNav />
      {error ? <div className="form-error">{error}</div> : null}
      {!h || !hs ? (
        <PageLoader />
      ) : (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10 }}>
            <Stat label="Engine" value={h.enabled ? 'On' : 'Off'} tone={h.enabled ? 'live' : 'warn'} sub={h.enabled ? 'accepting gifts' : 'refusing every send'} />
            <Stat label="Today" value={n(stats?.today.coins)} sub={`coins · ${n(stats?.today.count)} gifts`} />
            <Stat label="Receivers got today" value={n(stats?.today.receiver_diamonds)} sub="diamonds" />
            <Stat label="Platform today" value={n(stats?.today.platform_diamonds)} sub="diamonds" />
            <Stat label="Upline shares today" value={n(stats?.today.share_diamonds)} sub={`diamonds · ${n(stats?.today.cashback_coins)} coins cashback`} />
            <Stat label="Shares queued" value={n(hs.pending_allocations)} sub={hs.oldest_pending_at && hs.pending_allocations ? `oldest ${fmtClock(hs.oldest_pending_at)}` : 'paid by the settler'} tone={hs.pending_allocations > 1000 ? 'warn' : undefined} />
            <Stat label="Shares failed" value={n(hs.failed_allocations)} sub="need repair" tone={hs.failed_allocations ? 'warn' : undefined} />
            <Stat label="Notices queued" value={n(hs.outbox_queued)} sub={`pool ${hs.pool_acquired}/${hs.pool_total} busy`} />
          </div>
          <div style={{ marginTop: 16 }}>
            <Card
              title={`Volume · ${stats ? `${stats.from} → ${stats.to}` : ''}`}
              actions={
                <Select label="" value={period} onChange={(e) => setPeriod(e.target.value)}>
                  <option value="today">Today</option>
                  <option value="week">This week</option>
                  <option value="month">This month</option>
                </Select>
              }
            >
              {stats ? (
                <>
                  <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', fontSize: 12, marginBottom: 10 }}>
                    <span className="muted">
                      <strong>{n(stats.totals.coins)}</strong> coins sent
                    </span>
                    <span className="muted">
                      <strong>{n(stats.totals.count)}</strong> gifts
                    </span>
                    <span className="muted">
                      receivers <strong>{n(stats.totals.receiver_diamonds)}</strong> 💎
                    </span>
                    <span className="muted">
                      platform <strong>{n(stats.totals.platform_diamonds)}</strong> 💎
                    </span>
                    <span className="muted">
                      shares <strong>{n(stats.totals.share_diamonds)}</strong> 💎
                    </span>
                    <span className="muted">
                      cashback <strong>{n(stats.totals.cashback_coins)}</strong> coins
                    </span>
                  </div>
                  {stats.series.length === 0 ? (
                    <p className="muted">No gifts in this period.</p>
                  ) : (
                    <div style={{ display: 'flex', gap: 3, alignItems: 'flex-end', height: 90 }}>
                      {stats.series.map((s) => (
                        <div key={s.day} title={`${s.day}: ${n(s.coins)} coins · ${n(s.count)} gifts`} style={{ flex: 1, background: 'var(--accent)', height: `${Math.max(2, (s.coins / max) * 100)}%`, borderRadius: 2 }} />
                      ))}
                    </div>
                  )}
                </>
              ) : (
                <PageLoader />
              )}
            </Card>
          </div>
          <div style={{ marginTop: 16 }}>
            <PersonSearch />
          </div>
        </>
      )}
    </div>
  )
}

function PersonSearch() {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  return (
    <Card title="Open a person">
      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          const id = q.trim().replace(/^#/, '')
          if (/^\d+$/.test(id)) navigate(`/gifting/users/${id}`)
        }}
        style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}
      >
        <Field label="User id" value={q} onChange={(e) => setQ(e.target.value)} style={{ flex: 1 }} hint="Received, sent, upline shares — any period. Search by name under People → Search." />
        <Button type="submit" size="sm">
          Open
        </Button>
      </form>
    </Card>
  )
}

// ─── Policies ────────────────────────────────────────────────────────────────

function ScopeLabel({ p, tree }: { p: Policy; tree: Tree | null }) {
  const cats = useMemo(() => flattenTree(tree), [tree])
  if (p.scope_kind === 'global') return <>{t('global')}</>
  if (p.scope_kind === 'category') {
    const c = cats.find((x) => x.id === p.scope_id)
    return <>Category · {c ? c.label.replace(/— /g, '') : `#${p.scope_id}`}</>
  }
  const g = cats.flatMap((c) => c.gifts).find((x) => x.id === p.scope_id)
  return <>Gift · {g ? g.title : `#${p.scope_id}`}</>
}

function Policies() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const { tree } = useTree()
  const [rows, setRows] = useState<Policy[] | null>(null)
  const [error, setError] = useState('')
  const kind = params.get('kind') || ''
  const status = params.get('status') || ''
  const scopeKind = params.get('scope_kind') || ''
  const scopeId = params.get('scope_id') || ''
  const q = params.get('q') || ''
  const load = useCallback(async () => {
    const r = await giftingApi.policies({ kind, status, scope_kind: scopeKind, scope_id: scopeId, q })
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setRows(r.data.items)
  }, [kind, status, scopeKind, scopeId, q])
  useEffect(() => {
    void load()
  }, [load])
  const set = (k: string, v: string) => {
    const p = new URLSearchParams(params)
    if (v) p.set(k, v)
    else p.delete(k)
    setParams(p, { replace: true })
  }
  return (
    <div>
      <div className="page-head">
        <h1>{t('page.policies')}</h1>
        <p>{t('page.policies.sub')}</p>
      </div>
      <SectionNav />
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 12 }}>
        <div style={{ width: 200 }}>
          <Select label="Kind" value={kind} onChange={(e) => set('kind', e.target.value)}>
            <option value="">All</option>
            <option value="eligibility">{t('eligibility')}</option>
            <option value="distribution">{t('distribution')}</option>
          </Select>
        </div>
        <div style={{ width: 160 }}>
          <Select label="Status" value={status} onChange={(e) => set('status', e.target.value)}>
            <option value="">All</option>
            <option value="active">{t('active')}</option>
            <option value="draft">{t('draft')}</option>
            <option value="suspended">{t('suspended')}</option>
            <option value="retired">{t('retired')}</option>
          </Select>
        </div>
        <div style={{ width: 160 }}>
          <Select label="Scope" value={scopeKind} onChange={(e) => set('scope_kind', e.target.value)}>
            <option value="">All</option>
            <option value="global">{t('global')}</option>
            <option value="category">Category</option>
            <option value="gift">Gift</option>
          </Select>
        </div>
        <div style={{ flex: 1, minWidth: 180 }}>
          <Field label="Search name" value={q} onChange={(e) => set('q', e.target.value)} />
        </div>
        <Link to="/gifting/policies/new?kind=eligibility" style={{ textDecoration: 'none' }}>
          <Button size="sm" variant="ghost">
            + Who can send
          </Button>
        </Link>
        <Link to="/gifting/policies/new?kind=distribution" style={{ textDecoration: 'none' }}>
          <Button size="sm">+ Where the value goes</Button>
        </Link>
      </div>
      {error ? <div className="form-error">{error}</div> : null}
      {!rows ? (
        <PageLoader />
      ) : rows.length === 0 ? (
        <Card>
          <p className="muted" style={{ margin: 0 }}>
            No policies yet. Without one, every gift pays the receiver the built-in share and the rest goes to the platform. Start with “Where the value goes” at <strong>Everywhere</strong>.
          </p>
        </Card>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Policy</th>
              <th>Kind</th>
              <th>Scope</th>
              <th>Status</th>
              <th>Rule</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((p) => (
              <tr key={p.id} className="row-link" style={{ cursor: 'pointer' }} onClick={() => navigate(`/gifting/policies/${p.id}`)}>
                <td>
                  <strong>{p.name}</strong>
                  <span className="muted" style={{ display: 'block', fontSize: 11 }}>
                    priority {p.priority} · {p.version_count ?? 0} version{(p.version_count ?? 0) === 1 ? '' : 's'}
                  </span>
                </td>
                <td style={{ whiteSpace: 'nowrap' }}>{t(p.kind)}</td>
                <td>
                  <ScopeLabel p={p} tree={tree} />
                </td>
                <td>
                  <Pill tone={statusTone(p.status)}>{t(p.status)}</Pill>
                </td>
                <td style={{ fontSize: 12, maxWidth: 420 }}>{p.current?.summary || <span className="muted">no active version</span>}</td>
                <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>{fmtClock(p.updated_at)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

function PolicyNew() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const vocab = useVocab()
  const { tree } = useTree()
  const kind = (params.get('kind') === 'distribution' ? 'distribution' : 'eligibility') as BuilderValue['kind']
  const [v, setV] = useState<BuilderValue>(() => {
    const b = emptyBuilder(kind)
    const sk = params.get('scope_kind')
    if (sk === 'category' || sk === 'gift') {
      b.scope_kind = sk
      b.scope_id = params.get('scope_id') || ''
    }
    return b
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const save = async (activate: boolean) => {
    const problem = builderIsValid(v)
    if (problem) {
      setError(problem)
      return
    }
    setBusy(true)
    setError('')
    const r = await giftingApi.create({ kind: v.kind, scope_kind: v.scope_kind, scope_id: v.scope_id || undefined, name: v.name, description: v.description, priority: v.priority, body: v.body, activation: v.activation })
    if (!r.ok) {
      setBusy(false)
      setError(r.message)
      return
    }
    if (activate && r.data.current_version_id) {
      const a = await giftingApi.activate(r.data.current_version_id)
      if (!a.ok) {
        setBusy(false)
        setError(`Saved as draft, but could not activate: ${a.message}`)
        navigate(`/gifting/policies/${r.data.id}`)
        return
      }
    }
    setBusy(false)
    navigate(`/gifting/policies/${r.data.id}`)
  }
  return (
    <div>
      <Link to="/gifting/policies" className="muted" style={{ fontSize: 13, fontWeight: 600 }}>
        ← Policies
      </Link>
      <div className="page-head">
        <h1>New policy · {t(kind)}</h1>
        <p>{kind === 'eligibility' ? 'Decide who may send which gift to whom, and when. Anything you do not set is inherited.' : 'Decide how the value of a gift is shared between the receiver, the platform, cashback and uplines.'}</p>
      </div>
      <PolicyBuilder value={v} onChange={setV} vocab={vocab} tree={tree} />
      {error ? <div className="form-error" style={{ marginTop: 10 }}>{error}</div> : null}
      <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
        <Button variant="ghost" loading={busy} onClick={() => void save(false)}>
          Save as draft
        </Button>
        <Button loading={busy} onClick={() => void save(true)}>
          Save and activate
        </Button>
      </div>
    </div>
  )
}

function PolicyDetail() {
  const { id = '' } = useParams()
  const { can } = useAuth()
  const vocab = useVocab()
  const { tree } = useTree()
  const [pol, setPol] = useState<Policy | null>(null)
  const [versions, setVersions] = useState<PolicyVersion[]>([])
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState<{ versionId?: string; v: BuilderValue } | null>(null)
  const [meta, setMeta] = useState<{ name: string; description: string; priority: string } | null>(null)
  const manage = can('gift.manage')

  const load = useCallback(async () => {
    const r = await giftingApi.policy(id)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setPol(r.data.policy)
    setVersions(r.data.versions)
  }, [id])
  useEffect(() => {
    void load()
  }, [load])

  const act = async (fn: () => Promise<{ ok: boolean; message: string }>) => {
    setBusy(true)
    setOk('')
    const r = await fn()
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setOk(r.message)
    void load()
  }

  const startEdit = (from: PolicyVersion, asDraft: boolean) => {
    if (!pol) return
    setEditing({
      versionId: asDraft ? from.id : undefined,
      v: { kind: pol.kind as BuilderValue['kind'], scope_kind: pol.scope_kind, scope_id: pol.scope_id || '', name: pol.name, description: pol.description, priority: pol.priority, activation: from.activation, body: from.body },
    })
    window.scrollTo({ top: 0 })
  }
  const saveEdit = async (activate: boolean) => {
    if (!editing || !pol) return
    setBusy(true)
    setError('')
    const r = editing.versionId ? await giftingApi.updateDraft(editing.versionId, editing.v.body, editing.v.activation) : await giftingApi.newVersion(pol.id, editing.v.body, editing.v.activation)
    if (!r.ok) {
      setBusy(false)
      setError(r.message)
      return
    }
    if (activate) {
      const a = await giftingApi.activate(r.data.id)
      if (!a.ok) {
        setBusy(false)
        setError(`Draft saved, but could not activate: ${a.message}`)
        setEditing(null)
        void load()
        return
      }
    }
    setBusy(false)
    setOk(activate ? 'Activated.' : 'Draft saved.')
    setEditing(null)
    void load()
  }

  if (error && !pol) return <div className="form-error">{error}</div>
  if (!pol) return <PageLoader />
  const current = versions.find((v) => v.id === pol.current_version_id)
  return (
    <div>
      <Link to="/gifting/policies" className="muted" style={{ fontSize: 13, fontWeight: 600 }}>
        ← Policies
      </Link>
      <div className="page-head" style={{ display: 'flex', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap' }}>
        <div style={{ flex: 1 }}>
          <h1 style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
            {pol.name} <Pill tone={statusTone(pol.status)}>{t(pol.status)}</Pill>
          </h1>
          <p>
            {t(pol.kind)} · <ScopeLabel p={pol} tree={tree} /> · priority {pol.priority}
            {pol.description ? ` · ${pol.description}` : ''}
          </p>
        </div>
        {manage ? (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {pol.status === 'active' ? (
              <Button size="sm" variant="ghost" loading={busy} onClick={() => void act(() => giftingApi.setStatus(pol.id, 'suspended'))}>
                Pause
              </Button>
            ) : pol.status !== 'retired' ? (
              <Button size="sm" loading={busy} disabled={!pol.current_version_id} onClick={() => void act(() => giftingApi.setStatus(pol.id, 'active'))}>
                Resume
              </Button>
            ) : null}
            {pol.status !== 'retired' ? (
              <Button
                size="sm"
                variant="danger"
                loading={busy}
                onClick={() => {
                  if (window.confirm('Retire this policy? It stops applying to new gifts. History is kept; this cannot be undone.')) void act(() => giftingApi.setStatus(pol.id, 'retired'))
                }}
              >
                Retire
              </Button>
            ) : null}
            <Button size="sm" variant="ghost" onClick={() => setMeta({ name: pol.name, description: pol.description, priority: String(pol.priority) })}>
              Rename
            </Button>
          </div>
        ) : null}
      </div>
      {error ? <div className="form-error" style={{ marginBottom: 10 }}>{error}</div> : null}
      {ok ? <div className="muted" style={{ marginBottom: 10 }}>{ok}</div> : null}

      {meta ? (
        <Card title="Name, description, priority">
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 3fr 1fr', gap: 10 }}>
            <Field label="Name" value={meta.name} onChange={(e) => setMeta({ ...meta, name: e.target.value })} />
            <Field label="Description" value={meta.description} onChange={(e) => setMeta({ ...meta, description: e.target.value })} />
            <Field label="Priority" type="number" value={meta.priority} onChange={(e) => setMeta({ ...meta, priority: e.target.value })} />
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
            <Button
              size="sm"
              loading={busy}
              onClick={() =>
                void act(async () => {
                  const r = await giftingApi.updateMeta(pol.id, { name: meta.name, description: meta.description, priority: Number(meta.priority) || 0 })
                  if (r.ok) setMeta(null)
                  return r
                })
              }
            >
              Save
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setMeta(null)}>
              Cancel
            </Button>
          </div>
        </Card>
      ) : null}

      {editing ? (
        <div style={{ marginBottom: 16 }}>
          <Card title={editing.versionId ? 'Edit draft' : 'New version'}>
            <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
              {editing.versionId ? 'A draft can be edited until it is activated.' : 'Saving creates a new version. The current version keeps applying until the new one is activated; past gifts keep pointing at the version that priced them.'}
            </p>
          </Card>
          <div style={{ height: 12 }} />
          <PolicyBuilder value={editing.v} onChange={(v) => setEditing({ ...editing, v })} lockMeta vocab={vocab} tree={tree} />
          <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
            <Button variant="ghost" loading={busy} onClick={() => void saveEdit(false)}>
              Save draft
            </Button>
            <Button loading={busy} onClick={() => void saveEdit(true)}>
              Save and activate
            </Button>
            <Button variant="ghost" onClick={() => setEditing(null)}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <Card title="In force now">
          {current ? (
            <>
              <p style={{ fontSize: 15, lineHeight: 1.5, margin: 0 }}>{current.summary}</p>
              <p className="muted" style={{ fontSize: 12 }}>
                Version {current.version_no} · {t(current.activation, vocab)} · activated {current.activated_at ? fmtClock(current.activated_at) : '—'}
                {current.activated_by ? ` by #${current.activated_by}` : ''}
              </p>
            </>
          ) : (
            <p className="muted" style={{ margin: 0 }}>
              No active version. Activate a draft below.
            </p>
          )}
          {manage && current ? (
            <Button size="sm" onClick={() => startEdit(current, false)}>
              Change (new version)
            </Button>
          ) : null}
        </Card>
      )}

      <div style={{ height: 12 }} />
      <Card title="Versions">
        <table className="table">
          <thead>
            <tr>
              <th>#</th>
              <th>Status</th>
              <th>Rule</th>
              <th>Activation</th>
              <th>Created</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {versions.map((v) => (
              <tr key={v.id}>
                <td>{v.version_no}</td>
                <td>
                  <Pill tone={v.status === 'active' ? 'ok' : v.status === 'draft' ? 'muted' : 'bad'}>{t(v.status)}</Pill>
                </td>
                <td style={{ fontSize: 12, maxWidth: 480 }}>{v.summary}</td>
                <td style={{ fontSize: 12 }}>{t(v.activation, vocab)}</td>
                <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                  {fmtClock(v.created_at)}
                  {v.activated_at ? (
                    <span className="muted" style={{ display: 'block' }}>
                      active {fmtClock(v.activated_at)}
                      {v.retired_at ? ` → ${fmtClock(v.retired_at)}` : ''}
                    </span>
                  ) : null}
                </td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  {manage && v.status === 'draft' ? (
                    <span style={{ display: 'inline-flex', gap: 6 }}>
                      <Button size="sm" variant="ghost" onClick={() => startEdit(v, true)}>
                        Edit
                      </Button>
                      <Button size="sm" loading={busy} onClick={() => void act(() => giftingApi.activate(v.id))}>
                        Activate
                      </Button>
                    </span>
                  ) : manage && v.status === 'retired' ? (
                    <Button size="sm" variant="ghost" onClick={() => startEdit(v, false)}>
                      Copy as new
                    </Button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
      <div style={{ height: 12 }} />
      <AuditList entity="policy" entityId={pol.id} />
    </div>
  )
}

function AuditList({ entity, entityId }: { entity: string; entityId: string }) {
  const [rows, setRows] = useState<{ id: string; actor_id: string; action: string; created_at: string }[]>([])
  useEffect(() => {
    void (async () => {
      const r = await giftingApi.audit({ entity, entity_id: entityId })
      if (r.ok) setRows(r.data.items)
    })()
  }, [entity, entityId])
  if (rows.length === 0) return null
  return (
    <Card title="Changes">
      <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12 }}>
        {rows.map((r) => (
          <li key={r.id}>
            <strong>{r.action.replace('.', ' · ')}</strong> by #{r.actor_id} · {fmtClock(r.created_at)}
          </li>
        ))}
      </ul>
    </Card>
  )
}

// ─── Catalog tree ────────────────────────────────────────────────────────────

function CatalogTree() {
  const { can } = useAuth()
  const { tree, error, reload } = useTree()
  const [msg, setMsg] = useState('')
  const [busy, setBusy] = useState(false)
  const manage = can('gift.manage')
  const flat = useMemo(() => flattenTree(tree), [tree])
  const run = async (fn: () => Promise<{ ok: boolean; message: string }>) => {
    setBusy(true)
    const r = await fn()
    setBusy(false)
    setMsg(r.message)
    if (r.ok) void reload()
  }
  return (
    <div>
      <div className="page-head">
        <h1>{t('page.catalog')}</h1>
        <p>{t('page.catalog.sub')}</p>
      </div>
      <SectionNav />
      {error ? <div className="form-error">{error}</div> : null}
      {msg ? <div className="muted" style={{ marginBottom: 10 }}>{msg}</div> : null}
      {!tree ? (
        <PageLoader />
      ) : (
        <>
          <Card
            title="Everywhere"
            actions={
              <span style={{ display: 'inline-flex', gap: 6 }}>
                <PolicyCount c={tree.global} scope="global" />
                {manage ? <NewPolicyLinks scope="global" /> : null}
              </span>
            }
          >
            <p className="muted" style={{ margin: 0, fontSize: 12 }}>
              Rules at this level apply to every gift unless a category or gift overrides a field. Catalog v{tree.version} · rules rev {tree.rev}.
            </p>
          </Card>
          <div style={{ height: 12 }} />
          {tree.categories.length === 0 ? (
            <Card>
              <p className="muted" style={{ margin: 0 }}>
                No categories yet. Create them under My Bag → Gifts.
              </p>
            </Card>
          ) : (
            tree.categories.map((c) => <CategoryNode key={c.id} c={c} depth={0} flat={flat} manage={manage} busy={busy} run={run} />)
          )}
        </>
      )}
    </div>
  )
}

function PolicyCount({ c, scope, id }: { c: { Eligibility: number; Distribution: number }; scope: string; id?: string }) {
  const to = `/gifting/policies?scope_kind=${scope}${id ? `&scope_id=${id}` : ''}`
  if (!c.Eligibility && !c.Distribution)
    return (
      <Link to={to} className="muted" style={{ fontSize: 11, textDecoration: 'none' }}>
        inherits
      </Link>
    )
  return (
    <Link to={to} style={{ textDecoration: 'none', display: 'inline-flex', gap: 4 }}>
      {c.Eligibility ? <Pill tone="ok">{c.Eligibility} who</Pill> : null}
      {c.Distribution ? <Pill tone="ok">{c.Distribution} split</Pill> : null}
    </Link>
  )
}

function NewPolicyLinks({ scope, id }: { scope: string; id?: string }) {
  const sid = id ? `&scope_kind=${scope}&scope_id=${id}` : ''
  return (
    <span style={{ display: 'inline-flex', gap: 4 }}>
      <Link to={`/gifting/policies/new?kind=eligibility${sid}`} className="status-pill" style={{ textDecoration: 'none', fontSize: 11 }}>
        + who
      </Link>
      <Link to={`/gifting/policies/new?kind=distribution${sid}`} className="status-pill" style={{ textDecoration: 'none', fontSize: 11 }}>
        + split
      </Link>
    </span>
  )
}

function CategoryNode({ c, depth, flat, manage, busy, run }: { c: TreeCategory; depth: number; flat: ReturnType<typeof flattenTree>; manage: boolean; busy: boolean; run: (fn: () => Promise<{ ok: boolean; message: string }>) => Promise<void> }) {
  const [open, setOpen] = useState(depth < 1)
  const [moving, setMoving] = useState(false)
  const [linking, setLinking] = useState<string | null>(null)
  const descendants = useMemo(() => {
    const ids = new Set<string>()
    const walk = (x: TreeCategory) => {
      ids.add(x.id)
      for (const k of x.children || []) walk(k)
    }
    walk(c)
    return ids
  }, [c])
  return (
    <div style={{ marginLeft: depth * 18, marginBottom: 8 }}>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', padding: '8px 12px', background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 'var(--radius)' }}>
        <button type="button" onClick={() => setOpen(!open)} style={{ background: 'none', border: 0, cursor: 'pointer', fontSize: 14, width: 20 }}>
          {open ? '▾' : '▸'}
        </button>
        <strong>{c.title}</strong>
        {c.lucky ? <Pill tone="warn">lucky</Pill> : null}
        {c.status !== 1 ? <Pill tone="muted">hidden</Pill> : null}
        <span className="muted" style={{ fontSize: 12 }}>
          {c.gifts.length} gift{c.gifts.length === 1 ? '' : 's'} · {(c.children || []).length} sub
        </span>
        <span style={{ marginLeft: 'auto', display: 'inline-flex', gap: 6, alignItems: 'center' }}>
          <PolicyCount c={c.policies} scope="category" id={c.id} />
          {manage ? (
            <>
              <NewPolicyLinks scope="category" id={c.id} />
              <Button size="sm" variant="ghost" onClick={() => setMoving(!moving)}>
                Move
              </Button>
            </>
          ) : null}
        </span>
      </div>
      {moving ? (
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', padding: '8px 12px' }}>
          <div style={{ flex: 1 }}>
            <Select label="Put inside" defaultValue={c.parent_id || ''} id={`mv-${c.id}`}>
              <option value="">Top level</option>
              {flat
                .filter((x) => !descendants.has(x.id))
                .map((x) => (
                  <option key={x.id} value={x.id}>
                    {x.label}
                  </option>
                ))}
            </Select>
          </div>
          <Button
            size="sm"
            loading={busy}
            onClick={() => {
              const sel = document.getElementById(`mv-${c.id}`) as HTMLSelectElement | null
              void run(() => giftingApi.setParent(c.id, sel?.value || '0')).then(() => setMoving(false))
            }}
          >
            Save
          </Button>
        </div>
      ) : null}
      {open ? (
        <>
          {c.gifts.length ? (
            <table className="table" style={{ marginLeft: 30, width: 'calc(100% - 30px)', marginTop: 6 }}>
              <tbody>
                {c.gifts.map((g) => (
                  <tr key={g.id}>
                    <td style={{ width: 36 }}>
                      <GiftIcon listingKey={g.listing_key} />
                    </td>
                    <td>
                      <strong>{g.title}</strong>
                      <span className="muted" style={{ display: 'block', fontSize: 11 }}>
                        {n(g.coin_price)} coins · #{g.id}
                        {g.category_id !== c.id ? ' · linked here' : ''}
                        {g.category_ids.length > 1 ? ` · in ${g.category_ids.length} categories` : ''}
                        {g.status !== 1 ? ' · hidden' : ''}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
                        <PolicyCount c={g.policies} scope="gift" id={g.id} />
                        {manage ? (
                          <>
                            <NewPolicyLinks scope="gift" id={g.id} />
                            <Button size="sm" variant="ghost" onClick={() => setLinking(linking === g.id ? null : g.id)}>
                              Also in…
                            </Button>
                            {g.category_id !== c.id ? (
                              <Button size="sm" variant="ghost" loading={busy} onClick={() => void run(() => giftingApi.unlink(g.id, c.id))}>
                                Remove from here
                              </Button>
                            ) : null}
                          </>
                        ) : null}
                      </span>
                      {linking === g.id ? (
                        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', marginTop: 6, justifyContent: 'flex-end' }}>
                          <div style={{ width: 260 }}>
                            <Select label="Add to category" id={`ln-${g.id}`} defaultValue="">
                              <option value="">Choose…</option>
                              {flat
                                .filter((x) => !g.category_ids.includes(x.id))
                                .map((x) => (
                                  <option key={x.id} value={x.id}>
                                    {x.label}
                                  </option>
                                ))}
                            </Select>
                          </div>
                          <Button
                            size="sm"
                            loading={busy}
                            onClick={() => {
                              const sel = document.getElementById(`ln-${g.id}`) as HTMLSelectElement | null
                              if (!sel?.value) return
                              void run(() => giftingApi.link(g.id, sel.value)).then(() => setLinking(null))
                            }}
                          >
                            Link
                          </Button>
                        </div>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
          {(c.children || []).map((k) => (
            <CategoryNode key={k.id} c={k} depth={depth + 1} flat={flat} manage={manage} busy={busy} run={run} />
          ))}
        </>
      ) : null}
    </div>
  )
}

function GiftIcon({ listingKey, url, size = 28 }: { listingKey?: string; url?: string; size?: number }) {
  const { mediaBase } = useAuth()
  const src = url || (listingKey ? `${mediaBase.replace(/\/$/, '')}/${listingKey.replace(/^\//, '')}` : '')
  return src ? <img src={src} alt="" style={{ width: size, height: size, objectFit: 'contain', borderRadius: 6 }} loading="lazy" /> : <span style={{ display: 'inline-block', width: size, height: size, borderRadius: 6, background: 'var(--line)' }} />
}

// ─── Records ─────────────────────────────────────────────────────────────────

function Records() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const [rows, setRows] = useState<GiftRow[] | null>(null)
  const [next, setNext] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const f = {
    receiver_id: params.get('receiver_id') || '',
    sender_id: params.get('sender_id') || '',
    room_id: params.get('room_id') || '',
    gift_id: params.get('gift_id') || '',
    status: params.get('status') || '',
    from: params.get('from') || '',
    to: params.get('to') || '',
  }
  const key = JSON.stringify(f)
  const load = useCallback(
    async (cursor?: string) => {
      setBusy(true)
      const r = await giftingApi.records({ ...JSON.parse(key), cursor, limit: 50 })
      setBusy(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setRows((prev) => (cursor && prev ? [...prev, ...r.data.items] : r.data.items))
      setNext(r.data.next_cursor || '')
    },
    [key],
  )
  useEffect(() => {
    void load()
  }, [load])
  const set = (k: string, v: string) => {
    const p = new URLSearchParams(params)
    if (v) p.set(k, v)
    else p.delete(k)
    setParams(p, { replace: true })
  }
  return (
    <div>
      <div className="page-head">
        <h1>{t('page.records')}</h1>
        <p>{t('page.records.sub')}</p>
      </div>
      <SectionNav />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 8, marginBottom: 12 }}>
        <Field label="Receiver id" value={f.receiver_id} onChange={(e) => set('receiver_id', e.target.value)} />
        <Field label="Sender id" value={f.sender_id} onChange={(e) => set('sender_id', e.target.value)} />
        <Field label="Room id" value={f.room_id} onChange={(e) => set('room_id', e.target.value)} />
        <Field label="Gift id" value={f.gift_id} onChange={(e) => set('gift_id', e.target.value)} />
        <Select label="Status" value={f.status} onChange={(e) => set('status', e.target.value)}>
          <option value="">Any</option>
          <option value="1">{t('fact:1')}</option>
          <option value="2">{t('fact:2')}</option>
          <option value="3">{t('fact:3')}</option>
        </Select>
        <Field label="From" type="date" value={f.from} onChange={(e) => set('from', e.target.value)} />
        <Field label="To" type="date" value={f.to} onChange={(e) => set('to', e.target.value)} />
      </div>
      <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
        Default window: last 7 days. Narrow by receiver or sender for older history.
      </p>
      {error ? <div className="form-error">{error}</div> : null}
      {!rows ? (
        <PageLoader />
      ) : (
        <>
          <GiftTable rows={rows} onOpen={(id) => navigate(`/gifting/records/${id}`)} />
          {next ? (
            <div style={{ marginTop: 10 }}>
              <Button size="sm" variant="ghost" loading={busy} onClick={() => void load(next)}>
                Load more
              </Button>
            </div>
          ) : null}
        </>
      )}
    </div>
  )
}

function GiftTable({ rows, onOpen, hide }: { rows: GiftRow[]; onOpen: (id: string) => void; hide?: 'sender' | 'receiver' }) {
  if (rows.length === 0)
    return (
      <p className="muted" style={{ margin: 0 }}>
        No gifts.
      </p>
    )
  return (
    <table className="table">
      <thead>
        <tr>
          <th>When</th>
          <th>Gift</th>
          {hide !== 'sender' ? <th>From</th> : null}
          {hide !== 'receiver' ? <th>To</th> : null}
          <th>Coins</th>
          <th>Receiver got</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((g) => (
          <tr key={g.id} className="row-link" style={{ cursor: 'pointer' }} onClick={() => onOpen(g.id)}>
            <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>{fmtClock(g.at)}</td>
            <td>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <GiftIcon url={g.listing_url} />
                <span>
                  <strong>{g.gift_title}</strong>
                  <span className="muted" style={{ display: 'block', fontSize: 11 }}>
                    {g.count > 1 ? `×${g.count} · ` : ''}
                    {n(g.unit_coins)} each{g.combo > 1 ? ` · combo ${g.combo}` : ''}
                    {g.room_id ? ` · room ${g.room_id}` : ' · profile'}
                  </span>
                </span>
              </span>
            </td>
            {hide !== 'sender' ? (
              <td>
                <Who b={g.sender} />
              </td>
            ) : null}
            {hide !== 'receiver' ? (
              <td>
                <Who b={g.receiver} />
              </td>
            ) : null}
            <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>{n(g.coins)}</td>
            <td style={{ fontVariantNumeric: 'tabular-nums' }}>
              {n(g.diamonds)} 💎{g.cashback_coins ? <span className="muted"> · {n(g.cashback_coins)} back</span> : null}
            </td>
            <td>
              <Pill tone={g.status === 1 ? 'ok' : 'bad'}>{t(`fact:${g.status}`)}</Pill>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function RecordDetail() {
  const { id = '' } = useParams()
  const { can } = useAuth()
  const vocab = useVocab()
  const [d, setD] = useState<Detail | null>(null)
  const [error, setError] = useState('')
  const [msg, setMsg] = useState('')
  const [busy, setBusy] = useState(false)
  const load = useCallback(async () => {
    const r = await giftingApi.record(id)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setD(r.data)
  }, [id])
  useEffect(() => {
    void load()
  }, [load])
  if (error) return <div className="form-error">{error}</div>
  if (!d) return <PageLoader />
  const f = d.fact
  const failed = d.allocations.some((a) => a.settle_state === 4)
  const who = (uid: string) => d.people[uid]
  return (
    <div>
      <Link to="/gifting/records" className="muted" style={{ fontSize: 13, fontWeight: 600 }}>
        ← Records
      </Link>
      <div className="page-head">
        <h1 style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <GiftIcon url={d.gift.listing_url} size={40} />
          {f.gift_title}
          {f.count > 1 ? ` ×${f.count}` : ''} <Pill tone={f.status === 1 ? 'ok' : 'bad'}>{t(`fact:${f.status}`)}</Pill>
        </h1>
        <p>
          {fmtClock(f.created_at)} · gift #{f.id} · {f.room_id ? `room ${f.room_id}` : 'from profile'} · {t(`lrole:${f.live_role}`)} · {t(`mode:${f.live_mode}`)}
          {f.pk_match_id ? ` · PK ${f.pk_match_id}` : ''}
        </p>
      </div>
      {msg ? <div className="muted" style={{ marginBottom: 10 }}>{msg}</div> : null}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10 }}>
        <Stat label="Sender paid" value={n(f.total_coins)} sub={`coins · ${n(f.unit_coins)} × ${f.count}`} />
        <Stat label="Value" value={n(f.distributable_value)} sub={`conversion v${f.conversion_version}`} />
        <Stat label="Receiver got" value={n(f.receiver_diamonds)} sub="diamonds" />
        <Stat label="Platform" value={n(f.platform_diamonds)} sub="diamonds" />
        <Stat label="Upline shares" value={n(f.share_diamonds)} sub="diamonds" tone={failed ? 'warn' : undefined} />
        <Stat label="Cashback" value={n(f.cashback_coins)} sub="coins to sender" />
      </div>
      <div style={{ height: 12 }} />
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        <Card title="From">
          <Who b={who(f.sender_id) || d.gift.sender} size={36} />
          <p className="muted" style={{ fontSize: 12, marginBottom: 0 }}>
            role #{f.sender_role_id || '—'}
          </p>
        </Card>
        <Card title="To">
          <Who b={who(f.receiver_id) || d.gift.receiver} size={36} />
          <p className="muted" style={{ fontSize: 12, marginBottom: 0 }}>
            role #{f.receiver_role_id || '—'}
          </p>
        </Card>
      </div>
      <div style={{ height: 12 }} />
      <Card
        title="Money trail"
        actions={
          failed && can('gift.repair') ? (
            <Button
              size="sm"
              loading={busy}
              onClick={() => {
                setBusy(true)
                void giftingApi.repair(f.id).then((r) => {
                  setBusy(false)
                  setMsg(r.ok ? `${r.message} ${r.data.requeued} row(s).` : r.message)
                  void load()
                })
              }}
            >
              Re-queue failed shares
            </Button>
          ) : null
        }
      >
        <table className="table">
          <thead>
            <tr>
              <th>Goes to</th>
              <th>Who</th>
              <th>Share</th>
              <th>Value</th>
              <th>Paid</th>
              <th>Settlement</th>
              <th>Why</th>
            </tr>
          </thead>
          <tbody>
            {d.allocations.map((a) => (
              <tr key={a.id}>
                <td>
                  {t(a.target, vocab)}
                  {a.depth ? <span className="muted"> · level {a.depth}</span> : null}
                </td>
                <td>{a.beneficiary_id && a.beneficiary_id !== '0' ? <Who b={who(a.beneficiary_id) || { id: a.beneficiary_id }} /> : <span className="muted">{a.relation === 'platform' ? 'Platform' : '—'}</span>}</td>
                <td>{a.fixed ? `${n(a.fixed)} fixed` : pct(a.bps)}</td>
                <td style={{ fontVariantNumeric: 'tabular-nums' }}>{n(a.value)}</td>
                <td style={{ fontVariantNumeric: 'tabular-nums' }}>{a.coins ? `${n(a.coins)} coins` : `${n(a.diamonds)} 💎`}</td>
                <td>
                  <Pill tone={a.settle_state === 4 ? 'bad' : a.settle_state === 2 ? 'warn' : a.settle_state === 5 || a.skipped ? 'muted' : 'ok'}>{t(`settle:${a.skipped ? 5 : a.settle_state}`)}</Pill>
                  {a.wallet_entry_id ? (
                    <span className="muted" style={{ display: 'block', fontSize: 11 }}>
                      ledger #{a.wallet_entry_id}
                    </span>
                  ) : null}
                </td>
                <td className="muted" style={{ fontSize: 12 }}>
                  {calcText(a.calc, vocab)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
      <div style={{ height: 12 }} />
      <Card title="Priced by">
        <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
          <li>
            Who-can-send version: {f.eligibility_version_id ? `#${f.eligibility_version_id}` : <span className="muted">built-in defaults</span>}
          </li>
          <li>
            Split version: {f.distribution_version_id ? `#${f.distribution_version_id}` : <span className="muted">legacy receiver share</span>}
          </li>
          <li>
            Catalog v{f.catalog_version} · conversion v{f.conversion_version} · idempotency <code style={{ fontSize: 11 }}>{f.idem_key}</code>
          </li>
        </ul>
        {d.participants?.length ? (
          <p className="muted" style={{ fontSize: 12, marginBottom: 0 }}>
            Upline at the time:{' '}
            {d.participants
              .filter((p) => p.side === 2 && p.depth > 0)
              .map((p) => `${displayName(who(p.user_id) || {}) || `#${p.user_id}`} (level ${p.depth}, role #${p.role_id})`)
              .join(' → ') || 'none'}
          </p>
        ) : null}
      </Card>
    </div>
  )
}

// ─── Person ──────────────────────────────────────────────────────────────────

function TotalsLine({ x }: { x: Totals }) {
  return (
    <>
      <strong>{n(x.coins)}</strong> coins · {n(x.diamonds)} 💎 · {n(x.count)} gifts
    </>
  )
}

function PeerTable({ peers, label }: { peers: PeerRow[]; label: string }) {
  if (!peers.length)
    return (
      <p className="muted" style={{ margin: 0 }}>
        Nobody yet.
      </p>
    )
  return (
    <table className="table">
      <thead>
        <tr>
          <th>#</th>
          <th>{label}</th>
          <th>Coins</th>
          <th>Diamonds</th>
          <th>Gifts</th>
          <th>Last</th>
        </tr>
      </thead>
      <tbody>
        {peers.map((p) => (
          <tr key={p.peer.id}>
            <td>{p.rank}</td>
            <td>
              <Who b={p.peer} />
            </td>
            <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>{n(p.coins)}</td>
            <td style={{ fontVariantNumeric: 'tabular-nums' }}>{n(p.diamonds)}</td>
            <td>{n(p.count)}</td>
            <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>{fmtClock(new Date(p.last_at_ms).toISOString())}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function Person() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const vocab = useVocab()
  const [period, setPeriod] = useState('today')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [sort, setSort] = useState('top')
  const [data, setData] = useState<PersonData | null>(null)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    const r = await giftingApi.user(id, period, period === 'range' ? from : undefined, period === 'range' ? to : undefined, sort)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setData(r.data)
  }, [id, period, from, to, sort])
  useEffect(() => {
    if (period === 'range' && (!from || !to)) return
    void load()
    const t = window.setInterval(() => void load(), 15_000)
    return () => window.clearInterval(t)
  }, [load, period, from, to])
  const s = data?.summary
  const rec: Statement | undefined = data?.received
  const sent: Statement | undefined = data?.sent
  return (
    <div>
      <Link to="/gifting" className="muted" style={{ fontSize: 13, fontWeight: 600 }}>
        ← Gifting
      </Link>
      <div className="page-head" style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <div style={{ flex: 1 }}>
          <h1>{data?.user ? displayName(data.user) || `#${id}` : `#${id}`}</h1>
          <p>
            {data?.user?.username ? `@${data.user.username} · ` : ''}#{id} ·{' '}
            <Link to={`/users/${id}`}>account</Link> · <Link to={`/gifting/records?receiver_id=${id}`}>all received</Link> · <Link to={`/gifting/records?sender_id=${id}`}>all sent</Link>
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <Select label="Period" value={period} onChange={(e) => setPeriod(e.target.value)}>
            <option value="today">Today</option>
            <option value="week">This week</option>
            <option value="month">This month</option>
            <option value="range">Date range</option>
          </Select>
          {period === 'range' ? (
            <>
              <Field label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
              <Field label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </>
          ) : null}
          <Select label="Order" value={sort} onChange={(e) => setSort(e.target.value)}>
            <option value="top">Top</option>
            <option value="latest">Latest</option>
          </Select>
        </div>
      </div>
      {error ? <div className="form-error">{error}</div> : null}
      {!data || !s || !rec || !sent ? (
        <PageLoader />
      ) : (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10 }}>
            <Stat label="Received today" value={n(s.received_today.diamonds)} sub={`💎 · ${n(s.received_today.coins)} coins · ${n(s.received_today.count)} gifts`} />
            <Stat label="Received this week" value={n(s.received_week.diamonds)} sub={`💎 · ${n(s.received_week.count)} gifts`} />
            <Stat label="Received this month" value={n(s.received_month.diamonds)} sub={`💎 · ${n(s.received_month.count)} gifts`} />
            <Stat label="Sent today" value={n(s.sent_today.coins)} sub={`coins · ${n(s.sent_today.count)} gifts`} />
            <Stat label="Sent this week" value={n(s.sent_week.coins)} sub={`coins · ${n(s.sent_week.count)} gifts`} />
            <Stat label="Sent this month" value={n(s.sent_month.coins)} sub={`coins · ${n(s.sent_month.count)} gifts`} />
          </div>
          <div style={{ height: 12 }} />
          <Card title={`Received · ${rec.from === rec.to ? rec.from : `${rec.from} → ${rec.to}`}`}>
            <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
              <TotalsLine x={rec.totals} />
            </p>
            <h4 style={{ margin: '8px 0' }}>{sort === 'top' ? 'Top senders' : 'Latest senders'}</h4>
            <PeerTable peers={rec.peers} label="Sender" />
            <h4 style={{ margin: '14px 0 8px' }}>Gifts</h4>
            <GiftTable rows={rec.gifts} hide="receiver" onOpen={(gid) => navigate(`/gifting/records/${gid}`)} />
          </Card>
          <div style={{ height: 12 }} />
          <Card title={`Sent · ${sent.from === sent.to ? sent.from : `${sent.from} → ${sent.to}`}`}>
            <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
              <TotalsLine x={sent.totals} />
            </p>
            <h4 style={{ margin: '8px 0' }}>{sort === 'top' ? 'Top receivers' : 'Latest receivers'}</h4>
            <PeerTable peers={sent.peers} label="Receiver" />
            <h4 style={{ margin: '14px 0 8px' }}>Gifts</h4>
            <GiftTable rows={sent.gifts} hide="sender" onOpen={(gid) => navigate(`/gifting/records/${gid}`)} />
          </Card>
          <div style={{ height: 12 }} />
          <Card title="Upline shares earned">
            {data.shares.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                None in this period.
              </p>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>When</th>
                    <th>From gift</th>
                    <th>As</th>
                    <th>Share</th>
                    <th>Diamonds</th>
                    <th>Settlement</th>
                  </tr>
                </thead>
                <tbody>
                  {data.shares.map((a) => (
                    <tr key={a.id} className="row-link" style={{ cursor: 'pointer' }} onClick={() => navigate(`/gifting/records/${a.gift_id}`)}>
                      <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>{fmtClock(a.created_at)}</td>
                      <td>#{a.gift_id}</td>
                      <td>
                        {t(a.target, vocab)}
                        {a.depth ? ` · level ${a.depth}` : ''}
                      </td>
                      <td>{a.fixed ? `${n(a.fixed)} fixed` : pct(a.bps)}</td>
                      <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>{n(a.diamonds)}</td>
                      <td>
                        <Pill tone={a.settle_state === 4 ? 'bad' : a.settle_state === 2 ? 'warn' : 'ok'}>{t(`settle:${a.settle_state}`)}</Pill>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>
        </>
      )}
    </div>
  )
}
