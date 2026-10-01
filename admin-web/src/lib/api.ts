/** Typed client for the identity envelope: { statusCode, error, message, data }. */

export type ApiOk<T> = { ok: true; status: number; message: string; data: T }
export type ApiErr = {
  ok: false
  status: number
  code: string
  message: string
  isTransport: boolean
  /** Per-field validation messages (server `data.fields`), keyed by field. */
  fields?: Record<string, string>
}
export type ApiResult<T> = ApiOk<T> | ApiErr

const ACCESS_KEY = 'xani.admin.access'
const REFRESH_KEY = 'xani.admin.refresh'
const DEVICE_KEY = 'xani.admin.device'

export function appName(): string {
  return (import.meta.env.VITE_APP_NAME as string)?.trim() || 'App'
}

/** Relative /v1 in dev (Vite proxy); absolute base when set for production builds. */
function apiRoot(): string {
  const base = (import.meta.env.VITE_API_BASE_URL as string)?.replace(/\/$/, '') || ''
  if (import.meta.env.DEV) return ''
  return base
}

export function deviceKey(): string {
  let k = localStorage.getItem(DEVICE_KEY)
  if (!k) {
    k = crypto.randomUUID()
    localStorage.setItem(DEVICE_KEY, k)
  }
  return k
}

export function getAccess(): string {
  return sessionStorage.getItem(ACCESS_KEY) || localStorage.getItem(ACCESS_KEY) || ''
}

export function getRefresh(): string {
  return localStorage.getItem(REFRESH_KEY) || ''
}

export function setTokens(access: string, refresh: string) {
  sessionStorage.setItem(ACCESS_KEY, access)
  localStorage.setItem(ACCESS_KEY, access)
  localStorage.setItem(REFRESH_KEY, refresh)
}

export function clearTokens() {
  sessionStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

type Envelope = {
  statusCode?: number
  error?: boolean
  message?: string
  code?: string
  data?: unknown
}

let refreshInflight: Promise<boolean> | null = null

async function tryRefresh(): Promise<boolean> {
  const refresh = getRefresh()
  if (!refresh) return false
  if (refreshInflight) return refreshInflight
  refreshInflight = (async () => {
    try {
      const res = await fetch(`${apiRoot()}/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refresh_token: refresh }),
      })
      const body = (await res.json()) as Envelope
      if (!res.ok || body.error || !body.data) return false
      const data = body.data as { tokens?: { access_token?: string; refresh_token?: string } }
      const a = data.tokens?.access_token
      const r = data.tokens?.refresh_token
      if (!a || !r) return false
      setTokens(a, r)
      return true
    } catch {
      return false
    } finally {
      refreshInflight = null
    }
  })()
  return refreshInflight
}

export async function api<T>(
  method: string,
  path: string,
  opts: {
    body?: unknown
    auth?: boolean
    signal?: AbortSignal
    /** Raw body (e.g. avatar bytes); skips JSON stringify. */
    raw?: Blob | ArrayBuffer
    contentType?: string
  } = {},
): Promise<ApiResult<T>> {
  const headers: Record<string, string> = {}
  if (opts.raw) {
    headers['Content-Type'] = opts.contentType || 'application/octet-stream'
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (opts.auth !== false) {
    const token = getAccess()
    if (token) headers.Authorization = `Bearer ${token}`
  }

  const url = `${apiRoot()}${path}`
  let res: Response
  try {
    res = await fetch(url, {
      method,
      headers,
      signal: opts.signal,
      body: opts.raw
        ? opts.raw
        : opts.body !== undefined
          ? JSON.stringify(opts.body)
          : undefined,
    })
  } catch {
    return {
      ok: false,
      status: 0,
      code: 'transport',
      message: 'Could not reach the server. Check your connection and try again.',
      isTransport: true,
    }
  }

  if (res.status === 401 && opts.auth !== false && getRefresh()) {
    const ok = await tryRefresh()
    if (ok) return api(method, path, { ...opts, auth: true })
    clearTokens()
  }

  let body: Envelope = {}
  const text = await res.text()
  if (text) {
    try {
      body = JSON.parse(text) as Envelope
    } catch {
      return {
        ok: false,
        status: res.status,
        code: 'bad_response',
        message: 'The server sent something unexpected.',
        isTransport: false,
      }
    }
  }

  if (!res.ok || body.error) {
    const raw = (body.data as { fields?: Record<string, unknown> } | undefined)?.fields
    const fields =
      raw && typeof raw === 'object'
        ? Object.fromEntries(Object.entries(raw).map(([k, v]) => [k, typeof v === 'string' ? v : String(v)]))
        : undefined
    return {
      ok: false,
      status: res.status,
      code: body.code || 'error',
      message: body.message || 'Something went wrong.',
      isTransport: false,
      fields,
    }
  }

  return {
    ok: true,
    status: res.status,
    message: body.message || 'OK',
    data: body.data as T,
  }
}

export const get = <T,>(path: string, opts?: { signal?: AbortSignal; auth?: boolean }) =>
  api<T>('GET', path, opts)

export const post = <T,>(path: string, body?: unknown, opts?: { signal?: AbortSignal; auth?: boolean }) =>
  api<T>('POST', path, { body: body ?? {}, ...opts })

export const put = <T,>(path: string, body?: unknown, opts?: { signal?: AbortSignal; auth?: boolean }) =>
  api<T>('PUT', path, { body: body ?? {}, ...opts })

export const patch = <T,>(path: string, body?: unknown, opts?: { signal?: AbortSignal; auth?: boolean }) =>
  api<T>('PATCH', path, { body: body ?? {}, ...opts })

export const del = <T,>(path: string, opts?: { signal?: AbortSignal; auth?: boolean }) =>
  api<T>('DELETE', path, opts)

export const putBytes = <T,>(path: string, bytes: Blob, contentType: string) =>
  api<T>('PUT', path, { raw: bytes, contentType, auth: true })
