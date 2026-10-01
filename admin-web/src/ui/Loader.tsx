/** Compact professional spinner — SVG, no image decode, no layout shift. */
export function Loader({
  size = 20,
  light = false,
  label = 'Loading',
}: {
  size?: number
  light?: boolean
  label?: string
}) {
  const color = light ? 'rgba(255,255,255,0.92)' : 'var(--accent)'
  return (
    <span
      role="status"
      aria-label={label}
      style={{
        width: size,
        height: size,
        display: 'inline-flex',
        flexShrink: 0,
      }}
    >
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="9" stroke={color} strokeOpacity="0.25" strokeWidth="3" />
        <path
          d="M21 12a9 9 0 0 0-9-9"
          stroke={color}
          strokeWidth="3"
          strokeLinecap="round"
        >
          <animateTransform
            attributeName="transform"
            type="rotate"
            from="0 12 12"
            to="360 12 12"
            dur="0.7s"
            repeatCount="indefinite"
          />
        </path>
      </svg>
    </span>
  )
}

/** Full-area centered loader for route / page waits. */
export function PageLoader({ label = 'Loading…' }: { label?: string }) {
  return (
    <div
      style={{
        minHeight: 240,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        color: 'var(--muted)',
        fontSize: 14,
      }}
    >
      <Loader size={28} />
      <span>{label}</span>
    </div>
  )
}
