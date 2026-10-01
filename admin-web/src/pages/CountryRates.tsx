import { useCallback, useEffect, useMemo, useState, type CSSProperties, type FormEvent } from 'react'
import { del, get, put } from '../lib/api'
import { Button } from '../ui/Button'
import { Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { useCountries, type Country } from './PaymentMethods'

type Rate = {
  country_id: number
  iso2: string
  name: string
  dollars: string
  amount?: number
  coins?: number
  updated_at: string
}

function rateAmount(r: Rate): number {
  return r.amount || r.coins || 0
}

type ListResp = { items: Rate[] }

const selectStyle: CSSProperties = {
  height: 'var(--field-h)',
  width: '100%',
  borderRadius: 'var(--radius-sm)',
  border: '1px solid var(--line)',
  padding: '0 12px',
  background: 'var(--surface)',
  color: 'var(--ink)',
  fontSize: 14,
}

function amountPerDollar(r: Rate): string {
  const [w, f = ''] = r.dollars.split('.')
  const whole = Number(w || '0')
  const frac = Number((f + '00').slice(0, 2))
  if (!Number.isInteger(whole) || !Number.isInteger(frac) || whole < 0 || frac < 0) return '—'
  const cents = whole * 100 + frac
  if (cents < 1 || rateAmount(r) < 1) return '—'
  return Math.floor((rateAmount(r) * 100) / cents).toLocaleString()
}

function matches(r: Rate, raw: string): boolean {
  const n = raw.trim().toLowerCase()
  if (!n) return true
  const name = r.name.toLowerCase()
  const iso = r.iso2.toLowerCase()
  return name.startsWith(n) || iso.startsWith(n) || name.includes(n) || String(r.country_id) === n
}

export function CountryRatesPage() {
  const countries = useCountries()
  const [rows, setRows] = useState<Rate[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [q, setQ] = useState('')
  const [modal, setModal] = useState<'closed' | 'create' | Rate>('closed')
  const [busy, setBusy] = useState(false)
  const [countryId, setCountryId] = useState(0)
  const [dollars, setDollars] = useState('1')
  const [amount, setAmount] = useState('280')
  const [remove, setRemove] = useState<Rate | null>(null)

  const load = useCallback(async () => {
    const r = await get<ListResp>('/v1/admin/country-rates')
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setRows(r.data.items || [])
  }, [])

  useEffect(() => {
    void (async () => {
      setLoading(true)
      await load()
      setLoading(false)
    })()
  }, [load])

  const taken = useMemo(() => new Set(rows.map((r) => r.country_id)), [rows])
  const available = useMemo(
    () => countries.filter((c) => !taken.has(c.id)),
    [countries, taken],
  )

  const filtered = useMemo(() => rows.filter((r) => matches(r, q)), [rows, q])

  const openCreate = () => {
    setError('')
    setOk('')
    setCountryId(available[0]?.id || 0)
    setDollars('1')
    setAmount('280')
    setModal('create')
  }

  const openEdit = (r: Rate) => {
    setError('')
    setOk('')
    setCountryId(r.country_id)
    setDollars(r.dollars)
    setAmount(String(rateAmount(r)))
    setModal(r)
  }

  const save = async (e: FormEvent) => {
    e.preventDefault()
    const n = Number(amount.replace(/,/g, ''))
    if (!countryId) {
      setError('Pick a country.')
      return
    }
    if (!Number.isInteger(n) || n < 1) {
      setError('Amount must be a whole number of 1 or more.')
      return
    }
    setBusy(true)
    setError('')
    const r = await put<Rate>('/v1/admin/country-rates', {
      country_id: countryId,
      dollars: dollars.trim(),
      amount: n,
    })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setModal('closed')
    setOk('Rate saved.')
    await load()
  }

  const drop = async (r: Rate) => {
    setBusy(true)
    setError('')
    const res = await del(`/v1/admin/country-rates/${r.country_id}`)
    setBusy(false)
    if (!res.ok) {
      setError(res.message)
      return
    }
    setRemove(null)
    setOk('Rate removed.')
    await load()
  }

  if (loading) return <PageLoader label="Loading dollar rates…" />

  const editing = modal !== 'closed' && modal !== 'create'
  const countryLocked = editing

  return (
    <div className="rates-page">
      <div className="rates-toolbar">
        <div className="page-head" style={{ marginBottom: 0 }}>
          <h1>Dollar rates</h1>
          <p>Set $1 = 280 amount (or any pair) per country. The app uses this price.</p>
        </div>
        <div className="rates-toolbar-right">
          <input
            className="rates-search"
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search country…"
            aria-label="Search country"
          />
          <Button size="sm" onClick={openCreate} disabled={available.length === 0}>
            Add rate
          </Button>
        </div>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      {filtered.length === 0 ? (
        <div className="table-wrap">
          <p className="muted" style={{ margin: 16 }}>
            {rows.length === 0 ? 'No country rates yet. Add one — for example $1 = 280 amount.' : 'No country matches that search.'}
          </p>
        </div>
      ) : (
        <div className="table-wrap rates-table-wrap">
          <table className="data-table rates-table">
            <thead>
              <tr>
                <th>Country</th>
                <th className="num">Dollars</th>
                <th className="num">Amount</th>
                <th>Rate</th>
                <th>Updated</th>
                <th className="num"> </th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((r) => (
                <tr key={r.country_id}>
                  <td>
                    <strong>{r.name}</strong>
                    <span className="muted"> {r.iso2}</span>
                  </td>
                  <td className="num">${r.dollars}</td>
                  <td className="num">{rateAmount(r).toLocaleString()}</td>
                  <td className="nowrap">$1 = {amountPerDollar(r)}</td>
                  <td className="muted nowrap">{new Date(r.updated_at).toLocaleString()}</td>
                  <td className="nowrap num">
                    <Button size="sm" variant="ghost" onClick={() => openEdit(r)}>
                      Edit
                    </Button>
                    <Button size="sm" variant="danger" onClick={() => setRemove(r)}>
                      Delete
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modal !== 'closed' ? (
        <div className="admin-modal-backdrop" role="presentation" onClick={() => setModal('closed')}>
          <form
            className="admin-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="rate-modal-title"
            onClick={(e) => e.stopPropagation()}
            onSubmit={(e) => void save(e)}
          >
            <h2 id="rate-modal-title">{editing ? 'Edit rate' : 'Add country rate'}</h2>
            <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
              Example: $1 = 280 amount.
            </p>
            <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>Country</span>
              <select
                value={countryId || ''}
                onChange={(e) => setCountryId(Number(e.target.value) || 0)}
                style={selectStyle}
                disabled={countryLocked}
                required
              >
                <option value="">Select country…</option>
                {(countryLocked ? countries : available).map((c: Country) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.iso2})
                  </option>
                ))}
              </select>
            </label>
            <div className="rate-fields">
              <Field
                label="Dollars ($)"
                inputMode="decimal"
                value={dollars}
                onChange={(e) => setDollars(e.target.value)}
                placeholder="1"
              />
              <Field
                label="Amount"
                inputMode="numeric"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="280"
                hint="What $1 (or the dollars you entered) is worth"
              />
            </div>
            <div className="admin-modal-actions">
              <Button type="button" size="sm" variant="ghost" onClick={() => setModal('closed')}>
                Cancel
              </Button>
              <Button type="submit" size="sm" loading={busy}>
                Save
              </Button>
            </div>
          </form>
        </div>
      ) : null}

      {remove ? (
        <div className="admin-modal-backdrop" role="presentation" onClick={() => setRemove(null)}>
          <div className="admin-modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h2>Delete rate</h2>
            <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
              Remove {remove.name} (${remove.dollars} = {rateAmount(remove).toLocaleString()} amount)?
            </p>
            <div className="admin-modal-actions">
              <Button size="sm" variant="ghost" onClick={() => setRemove(null)}>
                Cancel
              </Button>
              <Button size="sm" variant="danger" loading={busy} onClick={() => void drop(remove)}>
                Delete
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
