import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react'
import { del, get, patch, post } from '../lib/api'
import { Button } from '../ui/Button'
import { Card, Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

export type Country = { id: number; iso2: string; iso3: string; name: string; phone_code: string }

export type PaymentMethod = {
  id: string
  name: string
  title: string
  description: string
  message: string
  account_label: string
  /** 0 = offered everywhere. */
  country_id: number
  /** 0 = use the global policy. */
  min_diamonds: number
  max_diamonds: number
  status: number
  sort_order: number
  created_at: string
  updated_at: string
}

type Draft = {
  name: string
  title: string
  description: string
  message: string
  account_label: string
  country_id: string
  min_diamonds: string
  max_diamonds: string
  status: boolean
  sort_order: string
}

const EMPTY: Draft = {
  name: '',
  title: '',
  description: '',
  message: '',
  account_label: 'Account number',
  country_id: '0',
  min_diamonds: '0',
  max_diamonds: '0',
  status: true,
  sort_order: '0',
}

const selectStyle: CSSProperties = {
  height: 36,
  borderRadius: 8,
  border: '1px solid var(--line)',
  padding: '0 10px',
  background: 'var(--surface)',
  fontSize: 13,
  width: '100%',
}

function toDraft(m: PaymentMethod): Draft {
  return {
    name: m.name,
    title: m.title,
    description: m.description,
    message: m.message,
    account_label: m.account_label,
    country_id: String(m.country_id || 0),
    min_diamonds: String(m.min_diamonds || 0),
    max_diamonds: String(m.max_diamonds || 0),
    status: m.status === 1,
    sort_order: String(m.sort_order || 0),
  }
}

export function useCountries(): Country[] {
  const [list, setList] = useState<Country[]>([])
  useEffect(() => {
    let alive = true
    void get<{ items: Country[] }>('/v1/countries', { auth: false }).then((r) => {
      if (alive && r.ok) setList(r.data.items || [])
    })
    return () => {
      alive = false
    }
  }, [])
  return list
}

/**
 * The ways the console pays creators out. Each method carries its own copy
 * (what the user reads before typing an account), the label of the account
 * field, the country it is offered in, and optional bounds that override
 * the global withdrawal policy.
 */
export function PaymentMethodsPage() {
  const [items, setItems] = useState<PaymentMethod[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<string>('new')
  const countries = useCountries()
  const countryName = useMemo(() => {
    const m = new Map<number, string>()
    for (const c of countries) m.set(c.id, c.name)
    return (id: number) => (id ? m.get(id) || `#${id}` : 'All countries')
  }, [countries])

  const load = useCallback(async () => {
    setLoading(true)
    const r = await get<{ items: PaymentMethod[] }>('/v1/admin/payment-methods')
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setItems(r.data.items || [])
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const current = selected === 'new' ? null : items.find((m) => m.id === selected) || null

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="page-head">
        <h1>Payment methods</h1>
        <p>How creators are paid for diamonds. A method shows in the app only for its country (or everywhere) and only while switched on.</p>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

      <div className="catalog-split">
        <Card
          title="Methods"
          actions={
            <Button size="sm" variant={selected === 'new' ? 'primary' : 'secondary'} onClick={() => setSelected('new')}>
              + New
            </Button>
          }
        >
          {loading && items.length === 0 ? (
            <PageLoader label="Loading methods…" />
          ) : items.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>
              No payment methods yet. Create the first one on the right.
            </p>
          ) : (
            <div className="role-list">
              {items.map((m) => (
                <button
                  key={m.id}
                  type="button"
                  className={`role-row${m.id === selected ? ' is-active' : ''}`}
                  onClick={() => setSelected(m.id)}
                  style={{ display: 'flex', alignItems: 'center', gap: 10, width: '100%', textAlign: 'left' }}
                >
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="role-row-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{m.name}</span>
                      <span className={`status-pill ${m.status === 1 ? 'on' : 'off'}`} style={{ fontSize: 10 }}>
                        {m.status === 1 ? 'On' : 'Off'}
                      </span>
                    </div>
                    <div className="role-row-meta">
                      {countryName(m.country_id)}
                      {m.title ? ` · ${m.title}` : ''}
                      {m.min_diamonds || m.max_diamonds ? ` · ${m.min_diamonds || '—'}–${m.max_diamonds || '—'} 💎` : ''}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </Card>

        <MethodEditor
          key={current?.id || 'new'}
          method={current}
          countries={countries}
          onSaved={(m) => {
            setItems((old) => {
              const i = old.findIndex((x) => x.id === m.id)
              if (i < 0) return [...old, m].sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name))
              const next = old.slice()
              next[i] = m
              return next.sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name))
            })
            setSelected(m.id)
          }}
          onDeleted={(id, hard) => {
            if (hard) {
              setItems((old) => old.filter((x) => x.id !== id))
              setSelected('new')
            } else {
              void load()
            }
          }}
        />
      </div>
    </div>
  )
}

function MethodEditor({
  method,
  countries,
  onSaved,
  onDeleted,
}: {
  method: PaymentMethod | null
  countries: Country[]
  onSaved: (m: PaymentMethod) => void
  onDeleted: (id: string, hard: boolean) => void
}) {
  const [draft, setDraft] = useState<Draft>(method ? toDraft(method) : EMPTY)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [err, setErr] = useState('')
  const [ok, setOk] = useState('')

  const set = <K extends keyof Draft>(k: K, v: Draft[K]) => setDraft((d) => ({ ...d, [k]: v }))

  const body = () => ({
    name: draft.name.trim(),
    title: draft.title.trim(),
    description: draft.description.trim(),
    message: draft.message.trim(),
    account_label: draft.account_label.trim() || 'Account number',
    country_id: Number(draft.country_id) || 0,
    min_diamonds: Math.max(0, Number(draft.min_diamonds) || 0),
    max_diamonds: Math.max(0, Number(draft.max_diamonds) || 0),
    status: draft.status ? 1 : 0,
    sort_order: Number(draft.sort_order) || 0,
  })

  const save = async () => {
    const b = body()
    if (!b.name) {
      setErr('Give the method a name.')
      return
    }
    if (b.max_diamonds > 0 && b.min_diamonds > b.max_diamonds) {
      setErr('Minimum must not exceed maximum.')
      return
    }
    setSaving(true)
    setErr('')
    setOk('')
    const r = method
      ? await patch<PaymentMethod>(`/v1/admin/payment-methods/${method.id}`, b)
      : await post<PaymentMethod>('/v1/admin/payment-methods', b)
    setSaving(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    setOk(r.message)
    onSaved(r.data)
  }

  const remove = async () => {
    if (!method) return
    if (!window.confirm(`Delete “${method.name}”? If it has withdrawal history it will be switched off instead.`)) return
    setDeleting(true)
    setErr('')
    const r = await del<{ deleted: boolean }>(`/v1/admin/payment-methods/${method.id}`)
    setDeleting(false)
    if (!r.ok) {
      setErr(r.message)
      return
    }
    onDeleted(method.id, r.data.deleted)
  }

  return (
    <Card
      title={method ? `Edit · ${method.name}` : 'New payment method'}
      actions={
        method ? (
          <Button size="sm" variant="danger" loading={deleting} onClick={() => void remove()}>
            Delete
          </Button>
        ) : undefined
      }
    >
      {err ? <div className="err-banner">{err}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <div className="form-grid">
        <Field label="Name" value={draft.name} onChange={(e) => set('name', e.target.value)} placeholder="Bank transfer, JazzCash, PayPal…" maxLength={80} />
        <Field label="Title" hint="One line under the name in the app." value={draft.title} onChange={(e) => set('title', e.target.value)} maxLength={120} />
        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>Country</span>
          <select style={selectStyle} value={draft.country_id} onChange={(e) => set('country_id', e.target.value)}>
            <option value="0">All countries</option>
            {countries.map((c) => (
              <option key={c.id} value={String(c.id)}>
                {c.name} ({c.iso2})
              </option>
            ))}
          </select>
          <span className="muted" style={{ fontSize: 12 }}>
            Only users whose account is in this country see the method.
          </span>
        </label>
        <Field
          label="Account field label"
          hint="What the user is asked to type: IBAN, wallet number, email…"
          value={draft.account_label}
          onChange={(e) => set('account_label', e.target.value)}
          maxLength={60}
        />
        <Field
          label="Minimum diamonds"
          hint="0 = use the global policy."
          type="number"
          min={0}
          value={draft.min_diamonds}
          onChange={(e) => set('min_diamonds', e.target.value)}
        />
        <Field
          label="Maximum diamonds"
          hint="0 = use the global policy."
          type="number"
          min={0}
          value={draft.max_diamonds}
          onChange={(e) => set('max_diamonds', e.target.value)}
        />
        <Field label="Sort order" type="number" value={draft.sort_order} onChange={(e) => set('sort_order', e.target.value)} />
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 26, cursor: 'pointer' }}>
          <input type="checkbox" checked={draft.status} onChange={(e) => set('status', e.target.checked)} />
          <span style={{ fontSize: 13, fontWeight: 600 }}>Switched on (visible in the app)</span>
        </label>
      </div>
      <div style={{ marginTop: 12, display: 'grid', gap: 12 }}>
        <TextArea
          label="Description"
          hint="Shown on the withdraw form before the fields: fees, timing, what the user should prepare."
          rows={4}
          value={draft.description}
          onChange={(e) => set('description', e.target.value)}
          maxLength={2000}
        />
        <TextArea
          label="Message"
          hint="A highlighted note on the form, e.g. “Payouts are sent within 3 working days.”"
          rows={2}
          value={draft.message}
          onChange={(e) => set('message', e.target.value)}
          maxLength={2000}
        />
      </div>
      <div className="form-actions">
        <Button loading={saving} onClick={() => void save()}>
          {method ? 'Save changes' : 'Create method'}
        </Button>
      </div>
    </Card>
  )
}
