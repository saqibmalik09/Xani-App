import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  api,
  appName,
  clearTokens,
  deviceKey,
  getAccess,
  getRefresh,
  post,
  setTokens,
} from './api'
import type { MembershipContext } from './roles'

export type User = {
  id: string
  username: string
  full_name: string
  email: string
  avatar_url: string
  bio: string
  location: string
  password_login_active: boolean
}

export type Session = {
  user: User
  roles: string[]
  /** Platform-wide permissions (reach 4 or legacy global grants). */
  permissions: string[]
  /** Every membership the account holds, each with its own effective permissions. */
  memberships: MembershipContext[]
}

type AuthState = {
  ready: boolean
  session: Session | null
  appName: string
  mediaBase: string
  /** Public site for shared join/profile links (branding Website URL or APP_WEB_URL). */
  webBaseUrl: string
  login: (identifier: string, password: string) => Promise<{ ok: true } | { ok: false; message: string }>
  logout: () => Promise<void>
  refreshMe: () => Promise<void>
  refreshConfig: () => Promise<void>
  setUser: (user: User) => void
  /** Platform-wide authority, or held (at any reach) through the active membership. */
  can: (slug: string) => boolean
  /** Held anywhere: platform or through any membership. Drives navigation. */
  canAny: (slug: string) => boolean
  /** Platform-wide only. */
  canPlatform: (slug: string) => boolean
  /** The membership context the console is currently acting from (null = platform/none). */
  active: MembershipContext | null
  setActiveMembership: (id: string | null) => void
}

const AuthCtx = createContext<AuthState | null>(null)

const DASHBOARD_PERM = 'admin.view_dashboard'
const ACTIVE_KEY = 'xani.admin.activeMembership'

type RawSession = {
  user: User
  roles?: string[]
  permissions?: string[]
  memberships?: MembershipContext[]
  tokens?: { access_token: string; refresh_token: string }
}

function toSession(raw: RawSession): Session {
  return {
    user: raw.user,
    roles: raw.roles || [],
    permissions: raw.permissions || [],
    memberships: raw.memberships || [],
  }
}

function storedActive(userId: string): string | null {
  try {
    const raw = localStorage.getItem(ACTIVE_KEY)
    if (!raw) return null
    const map = JSON.parse(raw) as Record<string, string>
    return map[userId] || null
  } catch {
    return null
  }
}

function storeActive(userId: string, id: string | null) {
  try {
    const raw = localStorage.getItem(ACTIVE_KEY)
    const map = raw ? (JSON.parse(raw) as Record<string, string>) : {}
    if (id) map[userId] = id
    else delete map[userId]
    localStorage.setItem(ACTIVE_KEY, JSON.stringify(map))
  } catch {
    // storage unavailable: context just does not persist across reloads
  }
}

/** Pick the context to land in: remembered one if still held, else the one with the most authority. */
function pickActive(s: Session, remembered: string | null): string | null {
  if (s.memberships.length === 0) return null
  if (remembered && s.memberships.some((m) => m.membership_id === remembered)) return remembered
  const ranked = [...s.memberships].sort((a, b) => {
    const score = (m: MembershipContext) => m.permissions.reduce((acc, p) => acc + p.reach, 0)
    return score(b) - score(a)
  })
  return ranked[0].membership_id
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false)
  const [session, setSession] = useState<Session | null>(null)
  const [activeId, setActiveId] = useState<string | null>(null)
  const [name, setName] = useState(appName())
  const [mediaBase, setMediaBase] = useState('')
  const [webBaseUrl, setWebBaseUrl] = useState('')

  const install = useCallback((s: Session) => {
    setSession(s)
    const chosen = pickActive(s, storedActive(s.user.id))
    setActiveId(chosen)
    storeActive(s.user.id, chosen)
  }, [])

  const applySession = useCallback(
    (raw: RawSession) => {
      if (raw.tokens) setTokens(raw.tokens.access_token, raw.tokens.refresh_token)
      install(toSession(raw))
    },
    [install],
  )

  const refreshMe = useCallback(async () => {
    const r = await api<RawSession>('GET', '/v1/auth/me', { auth: true })
    if (!r.ok) {
      clearTokens()
      setSession(null)
      setActiveId(null)
      return
    }
    install(toSession(r.data))
  }, [install])

  const refreshConfig = useCallback(async () => {
    const cfg = await api<{
      app_name?: string
      media_base_url?: string
      web_base_url?: string
      favicon_key?: string
    }>('GET', '/v1/config', { auth: false })
    if (!cfg.ok) return
    if (cfg.data.app_name) setName(cfg.data.app_name)
    if (cfg.data.media_base_url) setMediaBase(cfg.data.media_base_url.replace(/\/$/, ''))
    if (typeof cfg.data.web_base_url === 'string') setWebBaseUrl(cfg.data.web_base_url.replace(/\/$/, ''))
    const fav = cfg.data.favicon_key
    if (fav && cfg.data.media_base_url) {
      const href = mediaUrl(cfg.data.media_base_url.replace(/\/$/, ''), fav)
      let link = document.querySelector<HTMLLinkElement>("link[rel='icon']")
      if (!link) {
        link = document.createElement('link')
        link.rel = 'icon'
        document.head.appendChild(link)
      }
      link.href = href
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      await refreshConfig()
      if (getAccess() || getRefresh()) {
        await refreshMe()
      }
      if (!cancelled) setReady(true)
    })()
    return () => {
      cancelled = true
    }
  }, [refreshMe, refreshConfig])

  const login = useCallback(
    async (identifier: string, password: string) => {
      const r = await post<RawSession>(
        '/v1/auth/login',
        {
          identifier: identifier.trim(),
          password,
          device: { key: deviceKey(), platform: 'web', model: navigator.userAgent.slice(0, 80) },
        },
        { auth: false },
      )
      if (!r.ok) return { ok: false as const, message: r.message }
      applySession(r.data)
      return { ok: true as const }
    },
    [applySession],
  )

  const logout = useCallback(async () => {
    await post('/v1/auth/logout', {}, { auth: true }).catch(() => undefined)
    clearTokens()
    setSession(null)
    setActiveId(null)
  }, [])

  const active = useMemo(
    () => session?.memberships.find((m) => m.membership_id === activeId) || null,
    [session, activeId],
  )

  const canPlatform = useCallback((slug: string) => !!session?.permissions?.includes(slug), [session])

  const can = useCallback(
    (slug: string) => canPlatform(slug) || !!active?.permissions.some((p) => p.slug === slug),
    [canPlatform, active],
  )

  const canAny = useCallback(
    (slug: string) =>
      canPlatform(slug) || !!session?.memberships.some((m) => m.permissions.some((p) => p.slug === slug)),
    [canPlatform, session],
  )

  const setActiveMembership = useCallback(
    (id: string | null) => {
      setActiveId(id)
      if (session) storeActive(session.user.id, id)
    },
    [session],
  )

  const value = useMemo<AuthState>(
    () => ({
      ready,
      session,
      appName: name,
      mediaBase,
      webBaseUrl,
      login,
      logout,
      refreshMe,
      refreshConfig,
      setUser: (user) => setSession((s) => (s ? { ...s, user } : s)),
      can,
      canAny,
      canPlatform,
      active,
      setActiveMembership,
    }),
    [ready, session, name, mediaBase, webBaseUrl, login, logout, refreshMe, refreshConfig, can, canAny, canPlatform, active, setActiveMembership],
  )

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>
}

export function useAuth(): AuthState {
  const v = useContext(AuthCtx)
  if (!v) throw new Error('useAuth outside AuthProvider')
  return v
}

export function mediaUrl(base: string, key: string): string {
  if (!key) return ''
  if (key.startsWith('http')) return key
  if (!base) return key
  return `${base}/${key.replace(/^\//, '')}`
}

/** May this session use the console at all? Platform dashboard permission, or any role membership. */
export function canEnterConsole(s: Session | null, can: (slug: string) => boolean): boolean {
  if (!s) return false
  return can(DASHBOARD_PERM) || s.memberships.length > 0
}

export { DASHBOARD_PERM }
