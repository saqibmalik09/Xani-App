import type { InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from 'react'

type FieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string
  hint?: string
  error?: string
}

export function Field({ label, hint, error, id, style, ...rest }: FieldProps) {
  const fid = id || rest.name || label.replace(/\s+/g, '-').toLowerCase()
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>{label}</span>
      <input
        id={fid}
        style={{
          height: 'var(--field-h)',
          padding: '0 14px',
          borderRadius: 'var(--radius-sm)',
          border: `1px solid ${error ? 'var(--danger)' : 'var(--line)'}`,
          background: 'var(--surface)',
          color: 'var(--ink)',
          outline: 'none',
          fontSize: 14,
          ...style,
        }}
        {...rest}
      />
      {error ? (
        <span style={{ fontSize: 12, color: 'var(--danger)' }}>{error}</span>
      ) : hint ? (
        <span style={{ fontSize: 12, color: 'var(--muted)' }}>{hint}</span>
      ) : null}
    </label>
  )
}

type AreaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string
  hint?: string
  error?: string
}

export function TextArea({ label, hint, error, id, style, ...rest }: AreaProps) {
  const fid = id || rest.name || label.replace(/\s+/g, '-').toLowerCase()
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-soft)' }}>{label}</span>
      <textarea
        id={fid}
        style={{
          minHeight: 96,
          padding: '12px 14px',
          borderRadius: 'var(--radius-sm)',
          border: `1px solid ${error ? 'var(--danger)' : 'var(--line)'}`,
          background: 'var(--surface)',
          color: 'var(--ink)',
          outline: 'none',
          fontSize: 14,
          resize: 'vertical',
          ...style,
        }}
        {...rest}
      />
      {error ? (
        <span style={{ fontSize: 12, color: 'var(--danger)' }}>{error}</span>
      ) : hint ? (
        <span style={{ fontSize: 12, color: 'var(--muted)' }}>{hint}</span>
      ) : null}
    </label>
  )
}

export function Card({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section
      style={{
        background: 'var(--surface)',
        border: '1px solid var(--line)',
        borderRadius: 'var(--radius)',
        boxShadow: 'var(--shadow)',
        padding: 22,
      }}
    >
      {(title || actions) && (
        <header
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            marginBottom: 18,
          }}
        >
          {title ? (
            <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: 'var(--ink)' }}>{title}</h2>
          ) : (
            <span />
          )}
          {actions}
        </header>
      )}
      {children}
    </section>
  )
}
