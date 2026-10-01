import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, Route, Routes, useNavigate } from 'react-router-dom'
import { del, get, post, put, putBytes } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type FcmStatus = {
  configured: boolean
  project_id?: string
}

type FirebaseWeb = {
  configured: boolean
  project_id?: string
  vapid?: boolean
  android?: boolean
  config?: Record<string, unknown>
}

type FirebaseProbe = {
  ok: boolean
  project_id?: string
  detail?: string
  api_key?: boolean
  vapid?: boolean
}

function vapidFromConfig(cfg?: Record<string, unknown>): string {
  if (!cfg) return ''
  const v = cfg.vapidKey ?? cfg.vapid_key
  return typeof v === 'string' ? v : ''
}

function formatConfigValue(v: unknown): string {
  if (v == null) return '—'
  if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') return String(v)
  try {
    return JSON.stringify(v)
  } catch {
    return String(v)
  }
}

type Branding = {
  app_name: string
  web_url: string
  favicon_key: string
  default_avatar_key: string
  referral_code_length: number
  fcm: FcmStatus
  firebase_web?: FirebaseWeb
}

function BackLink() {
  return (
    <Link to="/settings" className="muted" style={{ fontSize: 13, fontWeight: 600 }}>
      ← Settings
    </Link>
  )
}

/** Settings hub: click a card to open the editor. */
export function SettingsPage() {
  return (
    <Routes>
      <Route index element={<SettingsHub />} />
      <Route path="branding" element={<SettingsBranding />} />
      <Route path="firebase" element={<SettingsFirebase />} />
      <Route path="referrals" element={<SettingsReferrals />} />
      <Route path="*" element={<SettingsHub />} />
    </Routes>
  )
}

function SettingsHub() {
  const [fcmOn, setFcmOn] = useState(false)
  const [webOn, setWebOn] = useState(false)
  const [appName, setAppName] = useState('')

  useEffect(() => {
    void (async () => {
      const r = await get<Branding>('/v1/admin/settings')
      if (!r.ok) return
      setAppName(r.data.app_name || '')
      setFcmOn(!!r.data.fcm?.configured)
      setWebOn(!!r.data.firebase_web?.configured)
    })()
  }, [])

  return (
    <div className="settings-page">
      <div className="page-head">
        <h1>Settings</h1>
        <p>Open a section to edit. Changes apply without a client release.</p>
      </div>
      <div className="settings-hub">
        <Link to="/settings/branding" className="settings-hub-card">
          <strong>App branding</strong>
          <span>App name, website URL, favicon, default profile image.</span>
          <em>{appName || 'Open →'}</em>
        </Link>
        <Link to="/settings/firebase" className="settings-hub-card">
          <strong>Firebase</strong>
          <span>Service account, web SDK config, and Web Push certificate.</span>
          <em>
            {fcmOn ? 'Service account on' : 'Service account off'}
            {' · '}
            {webOn ? 'Web config on' : 'Web config off'}
          </em>
        </Link>
        <Link to="/settings/referrals" className="settings-hub-card">
          <strong>Referral codes</strong>
          <span>How many digits new member codes have.</span>
          <em>Open →</em>
        </Link>
      </div>
    </div>
  )
}

function SettingsBranding() {
  const { mediaBase, refreshConfig } = useAuth()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [appName, setAppName] = useState('')
  const [webUrl, setWebUrl] = useState('')
  const [faviconKey, setFaviconKey] = useState('')
  const [avatarKey, setAvatarKey] = useState('')
  const faviconRef = useRef<HTMLInputElement>(null)
  const avatarRef = useRef<HTMLInputElement>(null)

  const load = useCallback(async () => {
    const r = await get<Branding>('/v1/admin/settings')
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setAppName(r.data.app_name || '')
    setWebUrl(r.data.web_url || '')
    setFaviconKey(r.data.favicon_key || '')
    setAvatarKey(r.data.default_avatar_key || '')
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const save = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const r = await put<Branding>('/v1/admin/settings', {
      app_name: appName.trim(),
      web_url: webUrl.trim(),
    })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setAppName(r.data.app_name || '')
    setWebUrl(r.data.web_url || '')
    await refreshConfig()
    setOk('Branding saved.')
  }

  const uploadBrand = async (kind: 'favicon' | 'avatar', file: File | null) => {
    if (!file) return
    setBusy(true)
    setError('')
    setOk('')
    const ext = file.name.includes('.') ? file.name.split('.').pop() : ''
    const r = await putBytes<Branding & { key: string }>(
      `/v1/admin/settings/branding/upload?kind=${kind}&ext=${encodeURIComponent(ext || '')}`,
      file,
      file.type || 'application/octet-stream',
    )
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    if (kind === 'favicon') setFaviconKey(r.data.key || r.data.favicon_key)
    else setAvatarKey(r.data.key || r.data.default_avatar_key)
    await refreshConfig()
    setOk(kind === 'favicon' ? 'Favicon updated.' : 'Default profile updated.')
  }

  const clearImage = async (kind: 'favicon' | 'avatar') => {
    setBusy(true)
    setError('')
    setOk('')
    const body = kind === 'favicon' ? { favicon_key: '' } : { default_avatar_key: '' }
    const r = await put<Branding>('/v1/admin/settings', body)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    if (kind === 'favicon') setFaviconKey('')
    else setAvatarKey('')
    await refreshConfig()
    setOk(kind === 'favicon' ? 'Favicon cleared.' : 'Default profile cleared.')
  }

  if (loading) return <PageLoader label="Loading branding…" />

  const faviconUrl = mediaUrl(mediaBase, faviconKey)
  const avatarUrl = mediaUrl(mediaBase, avatarKey)

  return (
    <div className="settings-page">
      <div className="page-head">
        <BackLink />
        <h1>App branding</h1>
        <p>Name, website, favicon, and default profile used across the product.</p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <section className="settings-card settings-card-wide">
        <form className="settings-form" onSubmit={(e) => void save(e)}>
          <Field label="App name" value={appName} onChange={(e) => setAppName(e.target.value)} maxLength={64} required />
          <Field
            label="Website URL"
            hint="Shared profile and post links. Blank uses the server APP_WEB_URL."
            placeholder="https://example.com"
            value={webUrl}
            onChange={(e) => setWebUrl(e.target.value)}
            maxLength={200}
          />
          <div className="brand-row">
            <div className="brand-pick">
              <span className="brand-label">Favicon</span>
              <button type="button" className="brand-thumb" onClick={() => faviconRef.current?.click()} disabled={busy}>
                {faviconUrl ? <img src={faviconUrl} alt="" /> : <span>Click to set</span>}
              </button>
              <input
                ref={faviconRef}
                type="file"
                accept="image/png,image/jpeg,image/webp,image/svg+xml,image/x-icon,.ico"
                hidden
                onChange={(e) => void uploadBrand('favicon', e.target.files?.[0] || null)}
              />
              {faviconKey ? (
                <Button type="button" size="sm" variant="ghost" onClick={() => void clearImage('favicon')} disabled={busy}>
                  Clear
                </Button>
              ) : null}
            </div>
            <div className="brand-pick">
              <span className="brand-label">Default profile</span>
              <button
                type="button"
                className="brand-thumb brand-thumb-lg"
                onClick={() => avatarRef.current?.click()}
                disabled={busy}
              >
                {avatarUrl ? <img src={avatarUrl} alt="" /> : <span>Click to set</span>}
              </button>
              <input
                ref={avatarRef}
                type="file"
                accept="image/png,image/jpeg,image/webp,image/gif"
                hidden
                onChange={(e) => void uploadBrand('avatar', e.target.files?.[0] || null)}
              />
              {avatarKey ? (
                <Button type="button" size="sm" variant="ghost" onClick={() => void clearImage('avatar')} disabled={busy}>
                  Clear
                </Button>
              ) : null}
            </div>
          </div>
          <div className="form-actions">
            <Button type="button" size="sm" variant="ghost" onClick={() => navigate('/settings')}>
              Back
            </Button>
            <Button type="submit" size="sm" loading={busy}>
              Save branding
            </Button>
          </div>
        </form>
      </section>
    </div>
  )
}

function SettingsFirebase() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [testBusy, setTestBusy] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [fcm, setFcm] = useState<FcmStatus>({ configured: false })
  const [web, setWeb] = useState<FirebaseWeb>({ configured: false })
  const [saJson, setSaJson] = useState('')
  const [webJson, setWebJson] = useState('')
  const [vapid, setVapid] = useState('')
  const [androidId, setAndroidId] = useState('')
  const [probe, setProbe] = useState<{ ok: boolean; fcm: FirebaseProbe; web: FirebaseProbe } | null>(null)

  const load = useCallback(async () => {
    const r = await get<Branding>('/v1/admin/settings')
    setLoading(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setFcm(r.data.fcm || { configured: false })
    const fw = r.data.firebase_web || { configured: false }
    setWeb(fw)
    if (fw.config) {
      setWebJson(JSON.stringify(fw.config, null, 2))
      setVapid(vapidFromConfig(fw.config))
      setAndroidId(String(fw.config.androidAppId ?? ''))
    }
  }, [])

  const saveAndroidId = async (e: FormEvent) => {
    e.preventDefault()
    const id = androidId.trim()
    if (!web.configured) {
      setError('Save the web app config first; the Android app shares its project and API key.')
      return
    }
    setBusy(true)
    setError('')
    setOk('')
    const r = await put<{ firebase_web: FirebaseWeb }>('/v1/admin/settings/firebase/web', { androidAppId: id })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setWeb(r.data.firebase_web)
    if (r.data.firebase_web?.config) setWebJson(JSON.stringify(r.data.firebase_web.config, null, 2))
    setOk('Android app ID saved. Phones pick it up on their next launch.')
  }

  useEffect(() => {
    void load()
  }, [load])

  const saveServiceAccount = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    let parsed: unknown
    try {
      parsed = JSON.parse(saJson || '{}')
    } catch {
      setBusy(false)
      setError('Service account JSON is not valid.')
      return
    }
    const r = await put<{ fcm: FcmStatus }>('/v1/admin/settings/push/fcm', parsed)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setFcm(r.data.fcm)
    setSaJson('')
    setOk('Service account saved. Key is never shown again.')
  }

  const clearServiceAccount = async () => {
    if (!window.confirm('Clear Firebase service account? Push stops until you paste a new JSON.')) return
    setBusy(true)
    setError('')
    setOk('')
    const r = await del<{ fcm: FcmStatus }>('/v1/admin/settings/push/fcm')
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setFcm(r.data.fcm)
    setOk('Service account cleared.')
  }

  const saveWebConfig = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    setProbe(null)
    const trimmedJson = webJson.trim()
    const trimmedVapid = vapid.trim()
    let payload: Record<string, unknown>
    if (!trimmedJson) {
      if (!web.configured) {
        setBusy(false)
        setError('Paste the web app config JSON first.')
        return
      }
      payload = { vapidKey: trimmedVapid }
    } else {
      try {
        payload = JSON.parse(trimmedJson) as Record<string, unknown>
      } catch {
        setBusy(false)
        setError('Web app config JSON is not valid.')
        return
      }
      payload.vapidKey = trimmedVapid
    }
    const r = await put<{ firebase_web: FirebaseWeb }>('/v1/admin/settings/firebase/web', payload)
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setWeb(r.data.firebase_web)
    if (r.data.firebase_web?.config) {
      setWebJson(JSON.stringify(r.data.firebase_web.config, null, 2))
      setVapid(vapidFromConfig(r.data.firebase_web.config))
    }
    setOk('Web app config saved. Apps read it from /v1/config as firebase_web.')
  }

  const clearWebConfig = async () => {
    if (!window.confirm('Clear Firebase web config? Clients will get null until you paste again.')) return
    setBusy(true)
    setError('')
    setOk('')
    setProbe(null)
    const r = await del<{ firebase_web: FirebaseWeb }>('/v1/admin/settings/firebase/web')
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setWeb(r.data.firebase_web)
    setWebJson('')
    setVapid('')
    setOk('Web app config cleared.')
  }

  const testConnection = async () => {
    setTestBusy(true)
    setError('')
    setOk('')
    const r = await post<{ ok: boolean; fcm: FirebaseProbe; web: FirebaseProbe }>('/v1/admin/settings/firebase/test')
    setTestBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setProbe(r.data)
    setOk(r.message || (r.data.ok ? 'Firebase connection looks good.' : 'Connection check finished.'))
  }

  if (loading) return <PageLoader label="Loading Firebase…" />

  const currentEntries = web.config ? Object.entries(web.config) : []

  return (
    <div className="settings-page">
      <div className="page-head">
        <BackLink />
        <h1>Firebase</h1>
        <p>Service account for server push, public web SDK config, and the Web Push certificate.</p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <div className="settings-grid">
        <section className="settings-card settings-card-wide">
          <header>
            <h2>Service account JSON</h2>
            <p>
              From Firebase Console → Project settings → Service accounts. Used for FCM push.
              {fcm.configured ? ` On project ${fcm.project_id || '—'}.` : ' Not configured.'}
            </p>
          </header>
          <form className="settings-form" onSubmit={(e) => void saveServiceAccount(e)}>
            <TextArea
              label="Paste service-account JSON"
              value={saJson}
              onChange={(e) => setSaJson(e.target.value)}
              spellCheck={false}
              placeholder='{ "type": "service_account", "project_id": "…", … }'
              style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12, minHeight: 140 }}
              hint={fcm.configured ? 'Leave blank to keep the current key. Paste a new object to replace it.' : 'Paste the full JSON, then Save.'}
            />
            <div className="form-actions">
              <Button type="submit" size="sm" loading={busy} disabled={!saJson.trim()}>
                Save service account
              </Button>
              {fcm.configured ? (
                <Button type="button" size="sm" variant="ghost" onClick={() => void clearServiceAccount()} disabled={busy}>
                  Clear
                </Button>
              ) : null}
            </div>
          </form>
        </section>

        <section className="settings-card settings-card-wide">
          <header>
            <h2>Current web config</h2>
            <p>
              Stored value served on <code>/v1/config</code> as <code>firebase_web</code>
              {web.configured ? ` · project ${web.project_id || '—'}` : ' · not configured'}.
            </p>
          </header>
          {currentEntries.length === 0 ? (
            <p className="muted" style={{ margin: '12px 0 0', fontSize: 13 }}>
              Nothing saved yet. Paste the web app config below.
            </p>
          ) : (
            <dl className="fb-kv">
              {currentEntries.map(([k, v]) => (
                <div key={k} className="fb-kv-row">
                  <dt>{k}</dt>
                  <dd>{formatConfigValue(v)}</dd>
                </div>
              ))}
            </dl>
          )}
        </section>

        <section className="settings-card settings-card-wide">
          <header>
            <h2>Web app config</h2>
            <p>
              Firebase Console → Project settings → Your apps → Web. Then Cloud Messaging → Web Push certificates for the
              key pair.
            </p>
          </header>
          <form className="settings-form" onSubmit={(e) => void saveWebConfig(e)}>
            <TextArea
              label="Web app config JSON"
              value={webJson}
              onChange={(e) => setWebJson(e.target.value)}
              spellCheck={false}
              placeholder='{ "apiKey": "…", "authDomain": "…", "projectId": "…", "appId": "…" }'
              style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12, minHeight: 140 }}
              hint="Keep or edit the JSON as stored. Saving writes the same fields plus the Web Push certificate."
            />
            <Field
              label="Web Push certificate"
              value={vapid}
              onChange={(e) => setVapid(e.target.value)}
              spellCheck={false}
              autoComplete="off"
              placeholder="B… (VAPID public key)"
              hint="Project settings → Cloud Messaging → Web Push certificates → Key pair."
              style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12 }}
            />
            <div className="form-actions">
              <Button type="button" size="sm" variant="ghost" onClick={() => navigate('/settings')}>
                Back
              </Button>
              <Button type="submit" size="sm" loading={busy} disabled={!webJson.trim() && !vapid.trim()}>
                Save web config
              </Button>
              {web.configured ? (
                <Button type="button" size="sm" variant="ghost" onClick={() => void clearWebConfig()} disabled={busy}>
                  Clear
                </Button>
              ) : null}
            </div>
          </form>
        </section>

        <section className="settings-card settings-card-wide">
          <header>
            <h2>Android app</h2>
            <p>
              Required for Android push. Firebase Console → Project settings → Your apps → Android (package{' '}
              <code>app.xani</code>; add <code>app.xani.debug</code> for debug builds) → App ID.
              {web.android ? ' Configured.' : ' Not configured — Android devices cannot get a push token.'}
            </p>
          </header>
          <form className="settings-form" onSubmit={(e) => void saveAndroidId(e)}>
            <Field
              label="Android app ID"
              value={androidId}
              onChange={(e) => setAndroidId(e.target.value)}
              spellCheck={false}
              autoComplete="off"
              placeholder="1:1234567890:android:abc123def456"
              style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12 }}
            />
            <div className="form-actions">
              <Button type="submit" size="sm" loading={busy} disabled={!androidId.trim()}>
                Save Android app ID
              </Button>
            </div>
          </form>
        </section>

        <section className="settings-card settings-card-wide">
          <header>
            <h2>Test connection</h2>
            <p>Checks the saved service account against FCM, and the saved web API key plus Web Push certificate against Google.</p>
          </header>
          <div className="form-actions" style={{ marginTop: 12 }}>
            <Button type="button" size="sm" loading={testBusy} onClick={() => void testConnection()} disabled={busy}>
              Test connection
            </Button>
          </div>
          {probe ? (
            <ul className="fb-probe">
              <li className={probe.fcm.ok ? 'is-ok' : 'is-bad'}>
                <strong>FCM</strong>
                <span>{probe.fcm.detail || (probe.fcm.ok ? 'OK' : 'Failed')}</span>
                {probe.fcm.project_id ? <em>{probe.fcm.project_id}</em> : null}
              </li>
              <li className={probe.web.ok ? 'is-ok' : 'is-bad'}>
                <strong>Web</strong>
                <span>{probe.web.detail || (probe.web.ok ? 'OK' : 'Failed')}</span>
                {probe.web.project_id ? <em>{probe.web.project_id}</em> : null}
              </li>
            </ul>
          ) : null}
        </section>
      </div>
    </div>
  )
}

function SettingsReferrals() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [length, setLength] = useState(5)

  useEffect(() => {
    void (async () => {
      const r = await get<Branding>('/v1/admin/settings')
      setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setLength(r.data.referral_code_length || 6)
    })()
  }, [])

  const save = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const r = await put<Branding>('/v1/admin/settings', { referral_code_length: length })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setLength(r.data.referral_code_length || 6)
    setOk(`Saved. New members get ${r.data.referral_code_length || 6}-digit referral codes; existing codes are unchanged.`)
  }

  if (loading) return <PageLoader label="Loading…" />

  return (
    <div className="settings-page">
      <div className="page-head">
        <BackLink />
        <h1>Referral codes</h1>
        <p>
          Every member gets a unique numeric referral code when their role is approved. Others enter it to join under
          that member, and the code owner approves them.
        </p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <section className="settings-card">
        <form className="settings-form" onSubmit={(e) => void save(e)}>
          <Field
            label="Digits in new codes"
            type="number"
            min={4}
            max={12}
            value={length}
            onChange={(e) => setLength(Number(e.target.value) || 6)}
            hint="Between 4 and 12 digits. Codes never start with 0 and are always unique. Default 6 (about 900,000 combinations)."
            required
          />
          <div className="form-actions">
            <Button type="button" size="sm" variant="ghost" onClick={() => navigate('/settings')}>
              Back
            </Button>
            <Button type="submit" size="sm" loading={busy}>
              Save
            </Button>
          </div>
        </form>
      </section>
    </div>
  )
}
