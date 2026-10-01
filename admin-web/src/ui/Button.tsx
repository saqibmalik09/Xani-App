import type { ButtonHTMLAttributes, CSSProperties, ReactNode } from 'react'
import { Loader } from './Loader'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'

const styles: Record<Variant, CSSProperties> = {
  primary: {
    background: 'var(--accent)',
    color: '#fff',
    border: '1px solid transparent',
  },
  secondary: {
    background: 'var(--surface)',
    color: 'var(--ink)',
    border: '1px solid var(--line)',
  },
  ghost: {
    background: 'transparent',
    color: 'var(--ink-soft)',
    border: '1px solid transparent',
  },
  danger: {
    background: 'var(--danger)',
    color: '#fff',
    border: '1px solid transparent',
  },
}

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant
  loading?: boolean
  size?: 'md' | 'sm'
  children: ReactNode
}

/** Compact default (36px). Use size="sm" for dense table actions (30px). */
export function Button({
  variant = 'primary',
  loading,
  size = 'md',
  children,
  disabled,
  style,
  ...rest
}: Props) {
  const h = size === 'sm' ? 'var(--btn-h-sm)' : 'var(--btn-h)'
  return (
    <button
      type="button"
      disabled={disabled || loading}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 6,
        height: h,
        minWidth: size === 'sm' ? 56 : 88,
        padding: size === 'sm' ? '0 10px' : '0 14px',
        borderRadius: 'var(--radius-sm)',
        fontWeight: 600,
        fontSize: size === 'sm' ? 12 : 13,
        letterSpacing: 0.01,
        cursor: disabled || loading ? 'not-allowed' : 'pointer',
        opacity: disabled && !loading ? 0.55 : 1,
        transition: 'background 120ms ease, opacity 120ms ease',
        ...styles[variant],
        ...style,
      }}
      {...rest}
    >
      {loading ? <Loader size={14} light={variant === 'primary' || variant === 'danger'} /> : null}
      <span>{children}</span>
    </button>
  )
}
