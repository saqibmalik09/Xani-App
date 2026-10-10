/**
 * Role application forms: the builder a role owner edits on the dashboard,
 * and the renderer that turns the same definition into inputs (used for the
 * live preview here; the mobile apps render the identical schema).
 */
import { useMemo, useState } from 'react'
import { FIELD_TYPES, normalizePhoneValue, phoneLikeField, type FieldType, type FormField } from '../lib/roles'
import { useCountries } from '../pages/PaymentMethods'
import { Button } from './Button'
import { Field, TextArea } from './Field'
import { Select } from './Org'

const MAX_FIELDS = 40

function keyFromLabel(label: string): string {
  const k = label
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 40)
  return !k || !/^[a-z]/.test(k) ? `f_${k}` : k
}

export function emptyField(type: FieldType = 'text'): FormField {
  return { key: '', label: '', type, required: true, hint: '', min: null, max: null, options: type === 'dropdown' ? [''] : undefined }
}

const hasLength = (t: FieldType) => t === 'text' || t === 'textarea'
const hasRange = (t: FieldType) => t === 'number'

/** Builder: ordered list of fields with inline editing, reorder and remove. */
export function FormBuilder({ value, onChange, disabled }: { value: FormField[]; onChange: (v: FormField[]) => void; disabled?: boolean }) {
  const update = (i: number, patch: Partial<FormField>) => {
    const next = value.map((f, idx) => (idx === i ? { ...f, ...patch } : f))
    onChange(next)
  }
  const move = (i: number, dir: -1 | 1) => {
    const j = i + dir
    if (j < 0 || j >= value.length) return
    const next = [...value]
    ;[next[i], next[j]] = [next[j], next[i]]
    onChange(next)
  }
  const remove = (i: number) => onChange(value.filter((_, idx) => idx !== i))
  const add = () => {
    if (value.length >= MAX_FIELDS) return
    onChange([...value, emptyField()])
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {value.length === 0 ? (
        <p className="muted" style={{ margin: 0 }}>
          No questions yet. Applicants will only see the referral code and an optional note until you add fields here.
        </p>
      ) : null}
      {value.map((f, i) => (
        <div key={i} className="role-row" style={{ alignItems: 'stretch', flexDirection: 'column', gap: 10, cursor: 'default' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span className="org-code">{i + 1}</span>
            <strong style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {f.label || 'Untitled question'}
            </strong>
            <span className="muted" style={{ fontSize: 12 }}>
              {FIELD_TYPES.find((t) => t.id === f.type)?.label} · {f.required ? 'required' : 'optional'}
            </span>
            {!disabled ? (
              <span style={{ display: 'inline-flex', gap: 4 }}>
                <button type="button" className="chip" onClick={() => move(i, -1)} disabled={i === 0} aria-label="Move up">
                  ↑
                </button>
                <button type="button" className="chip" onClick={() => move(i, 1)} disabled={i === value.length - 1} aria-label="Move down">
                  ↓
                </button>
                <button type="button" className="chip" onClick={() => remove(i)} aria-label="Remove field">
                  Remove
                </button>
              </span>
            ) : null}
          </div>
          <div className="perm-grid">
            <Field
              label="Label"
              value={f.label}
              disabled={disabled}
              maxLength={80}
              placeholder="e.g. WhatsApp number"
              onChange={(e) => {
                const label = e.target.value
                const auto = !f.key || f.key === keyFromLabel(f.label)
                update(i, { label, key: auto ? keyFromLabel(label) : f.key })
              }}
            />
            <Field
              label="Key"
              value={f.key}
              disabled={disabled}
              maxLength={40}
              hint="Stable id for the answer. Lowercase letters, numbers, underscores."
              onChange={(e) => update(i, { key: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, '') })}
            />
            <Select
              label="Type"
              value={f.type}
              disabled={disabled}
              hint={FIELD_TYPES.find((t) => t.id === f.type)?.hint}
              onChange={(e) => {
                const type = e.target.value as FieldType
                update(i, {
                  type,
                  options: type === 'dropdown' ? (f.options?.length ? f.options : ['']) : undefined,
                  min: hasLength(type) || hasRange(type) ? f.min ?? null : null,
                  max: hasLength(type) || hasRange(type) ? f.max ?? null : null,
                })
              }}
            >
              {FIELD_TYPES.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.label}
                </option>
              ))}
            </Select>
            <label className="perm-check" style={{ alignSelf: 'end' }}>
              <input type="checkbox" checked={f.required} disabled={disabled} onChange={(e) => update(i, { required: e.target.checked })} />
              <span>
                <strong>Required</strong>
                <div className="muted">Applicant cannot submit without it.</div>
              </span>
            </label>
            {hasLength(f.type) || hasRange(f.type) ? (
              <>
                <Field
                  label={hasRange(f.type) ? 'Minimum value' : 'Minimum length'}
                  type="number"
                  disabled={disabled}
                  value={f.min ?? ''}
                  onChange={(e) => update(i, { min: e.target.value === '' ? null : Number(e.target.value) })}
                  hint="Leave empty for no minimum."
                />
                <Field
                  label={hasRange(f.type) ? 'Maximum value' : 'Maximum length'}
                  type="number"
                  disabled={disabled}
                  value={f.max ?? ''}
                  onChange={(e) => update(i, { max: e.target.value === '' ? null : Number(e.target.value) })}
                  hint="Leave empty for no maximum."
                />
              </>
            ) : null}
            <Field
              label="Help text"
              value={f.hint || ''}
              disabled={disabled}
              maxLength={160}
              placeholder="Shown under the input"
              onChange={(e) => update(i, { hint: e.target.value })}
            />
            {f.type === 'dropdown' ? (
              <TextArea
                label="Options (one per line)"
                disabled={disabled}
                value={(f.options || []).join('\n')}
                onChange={(e) => update(i, { options: e.target.value.split('\n') })}
                hint="At least two. Applicants pick exactly one."
                style={{ minHeight: 72 }}
              />
            ) : null}
          </div>
        </div>
      ))}
      {!disabled ? (
        <div className="form-actions">
          <Button variant="secondary" type="button" onClick={add} disabled={value.length >= MAX_FIELDS}>
            Add question
          </Button>
          <span className="muted" style={{ alignSelf: 'center', fontSize: 12 }}>
            {value.length} / {MAX_FIELDS}
          </span>
        </div>
      ) : null}
    </div>
  )
}

/** Client-side check mirroring the server rules, so the preview is honest. */
export function validateAnswers(fields: FormField[], answers: Record<string, unknown>): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const f of fields) {
    const raw = answers[f.key]
    const str = raw === undefined || raw === null ? '' : String(raw).trim()
    if (!str) {
      if (f.required) errors[f.key] = `${f.label} is required.`
      continue
    }
    if (phoneLikeField(f)) {
      if (!normalizePhoneValue(str)) {
        errors[f.key] = 'Enter a valid phone number with country code (e.g. +923001234567).'
      }
      continue
    }
    switch (f.type) {
      case 'text':
      case 'textarea':
        if (f.min != null && str.length < f.min) errors[f.key] = `At least ${f.min} characters.`
        else if (f.max != null && str.length > f.max) errors[f.key] = `At most ${f.max} characters.`
        break
      case 'number': {
        const n = Number(str)
        if (!Number.isFinite(n)) errors[f.key] = 'Enter a number.'
        else if (f.min != null && n < f.min) errors[f.key] = `Minimum ${f.min}.`
        else if (f.max != null && n > f.max) errors[f.key] = `Maximum ${f.max}.`
        break
      }
      case 'dropdown':
        if (!(f.options || []).includes(str)) errors[f.key] = 'Choose one of the options.'
        break
      case 'country':
        if (!/^[A-Za-z]{2}$/.test(str)) errors[f.key] = 'Pick a country.'
        break
    }
  }
  return errors
}

/** Renderer: the same definition as inputs. Values are kept as strings. */
export function DynamicForm({
  fields,
  value,
  onChange,
  errors,
  disabled,
}: {
  fields: FormField[]
  value: Record<string, string>
  onChange: (v: Record<string, string>) => void
  errors?: Record<string, string>
  disabled?: boolean
}) {
  const countries = useCountries()
  const set = (k: string, v: string) => onChange({ ...value, [k]: v })
  if (fields.length === 0) return <p className="muted" style={{ margin: 0 }}>This role has no application questions.</p>
  return (
    <div className="form-grid">
      {fields.map((f) => {
        const label = f.required ? f.label : `${f.label} (optional)`
        const err = errors?.[f.key]
        switch (f.type) {
          case 'textarea':
            return <TextArea key={f.key} label={label} hint={f.hint} error={err} value={value[f.key] || ''} disabled={disabled} maxLength={f.max ?? 4000} onChange={(e) => set(f.key, e.target.value)} />
          case 'number':
            return <Field key={f.key} label={label} hint={f.hint} error={err} type="number" value={value[f.key] || ''} disabled={disabled} min={f.min ?? undefined} max={f.max ?? undefined} onChange={(e) => set(f.key, e.target.value)} />
          case 'phone':
          case 'whatsapp':
            return <Field key={f.key} label={label} hint={f.hint || 'Include the country code, e.g. +92 300 1234567'} error={err} type="tel" value={value[f.key] || ''} disabled={disabled} onChange={(e) => set(f.key, e.target.value)} />
          case 'dropdown':
            return (
              <Select key={f.key} label={label} hint={err || f.hint} value={value[f.key] || ''} disabled={disabled} onChange={(e) => set(f.key, e.target.value)}>
                <option value="">Choose…</option>
                {(f.options || []).filter(Boolean).map((o) => (
                  <option key={o} value={o}>
                    {o}
                  </option>
                ))}
              </Select>
            )
          case 'country':
            return (
              <Select key={f.key} label={label} hint={err || f.hint} value={value[f.key] || ''} disabled={disabled} onChange={(e) => set(f.key, e.target.value)}>
                <option value="">Choose a country…</option>
                {countries.map((c) => (
                  <option key={c.iso2} value={c.iso2}>
                    {c.name}
                  </option>
                ))}
              </Select>
            )
          default:
            return <Field key={f.key} label={label} hint={f.hint} error={err} value={value[f.key] || ''} disabled={disabled} maxLength={f.max ?? 4000} onChange={(e) => set(f.key, e.target.value)} />
        }
      })}
    </div>
  )
}

/** Read-only answers with labels — the approver's view of a submission. */
export function FormAnswers({ fields, data }: { fields?: FormField[]; data?: Record<string, unknown> }) {
  const countries = useCountries()
  const rows = useMemo(() => {
    if (!data) return []
    const byKey = new Map((fields || []).map((f) => [f.key, f]))
    const out: { key: string; label: string; value: string }[] = []
    for (const [k, v] of Object.entries(data)) {
      const f = byKey.get(k)
      let text = v === null || v === undefined ? '' : String(v)
      if (f?.type === 'country') text = countries.find((c) => c.iso2 === text)?.name || text
      out.push({ key: k, label: f?.label || k, value: text })
    }
    // Keep the form's order first, then anything the form no longer has.
    const order = new Map((fields || []).map((f, i) => [f.key, i]))
    out.sort((a, b) => (order.get(a.key) ?? 1e9) - (order.get(b.key) ?? 1e9))
    return out
  }, [fields, data, countries])
  if (rows.length === 0) return null
  return (
    <dl className="org-kv">
      {rows.map((r) => (
        <span key={r.key} style={{ display: 'contents' }}>
          <dt>{r.label}</dt>
          <dd style={{ whiteSpace: 'pre-wrap' }}>{r.value || <span className="muted">—</span>}</dd>
        </span>
      ))}
    </dl>
  )
}

/** Live preview used on the role's Form tab: fill it in, see validation. */
export function FormPreview({ fields }: { fields: FormField[] }) {
  const [value, setValue] = useState<Record<string, string>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <DynamicForm fields={fields} value={value} onChange={setValue} errors={errors} />
      {fields.length ? (
        <div className="form-actions">
          <Button variant="secondary" type="button" onClick={() => setErrors(validateAnswers(fields, value))}>
            Test validation
          </Button>
          <Button
            variant="ghost"
            type="button"
            onClick={() => {
              setValue({})
              setErrors({})
            }}
          >
            Clear
          </Button>
        </div>
      ) : null}
    </div>
  )
}
