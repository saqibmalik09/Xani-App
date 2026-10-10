import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, Route, Routes, useNavigate } from 'react-router-dom'
import { del, get, post, put, putBytes } from '../lib/api'
import { mediaUrl, useAuth } from '../lib/auth'
import { Button } from '../ui/Button'
import { Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { SettingsLiveTime } from './SettingsLiveTime'
import { SettingsRewards } from './SettingsRewards'
import { SettingsGifting } from './SettingsGifting'

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

type BrandImage = 'favicon' | 'logo' | 'avatar'

type Branding = {
  app_name: string
  web_url: string
  favicon_key: string
  logo_key?: string
  tagline?: string
  default_avatar_key: string
  referral_code_length: number
  max_accounts_per_device: number
  username_change_days: number
  android_package_name?: string
  android_sha256_certs?: string[]
  android_sha1_certs?: string[]
  android_debug_sha1_certs?: string[]
  android_debug_package_name?: string
  android_debug_suffix?: string
  android_assetlinks_path?: string
  app_link_scheme?: string
  google_enabled?: boolean
  google_web_client_id?: string
  google_android_client_id?: string
  google_android_debug_client_id?: string
  google_client_ids?: string[]
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
      <Route path="google" element={<SettingsGoogle />} />
      <Route path="referrals" element={<SettingsReferrals />} />
      <Route path="accounts" element={<SettingsAccounts />} />
      <Route path="live-time" element={<SettingsLiveTime />} />
      <Route path="rewards" element={<SettingsRewards />} />
      <Route path="gifting" element={<SettingsGifting />} />
      <Route path="*" element={<SettingsHub />} />
    </Routes>
  )
}

function SettingsHub() {
  const [fcmOn, setFcmOn] = useState(false)
  const [webOn, setWebOn] = useState(false)
  const [appName, setAppName] = useState('')
  const [liveDay, setLiveDay] = useState('')
  const [rewardsOn, setRewardsOn] = useState<boolean | null>(null)
  const [giftingOn, setGiftingOn] = useState<boolean | null>(null)
  const [googleOn, setGoogleOn] = useState<boolean | null>(null)

  useEffect(() => {
    void (async () => {
      const r = await get<{ enabled: boolean }>('/v1/admin/settings/gifting')
      if (r.ok) setGiftingOn(!!r.data.enabled)
    })()
    void (async () => {
      const r = await get<Branding>('/v1/admin/settings')
      if (!r.ok) return
      setAppName(r.data.app_name || '')
      setFcmOn(!!r.data.fcm?.configured)
      setWebOn(!!r.data.firebase_web?.configured)
      setGoogleOn(!!r.data.google_enabled && !!(r.data.google_client_ids || []).length)
    })()
    void (async () => {
      const r = await get<{ day_tz: string; day_start: string }>('/v1/admin/settings/live-time')
      if (r.ok) setLiveDay(`${r.data.day_tz} · ${r.data.day_start}`)
    })()
    void (async () => {
      const r = await get<{ enabled: boolean }>('/v1/admin/settings/rewards')
      if (r.ok) setRewardsOn(!!r.data.enabled)
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
          <span>App name, logo, tagline, website URL, favicon, default profile image.</span>
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
        <Link to="/settings/google" className="settings-hub-card">
          <strong>Google sign-in</strong>
          <span>OAuth client IDs, plus the package name and SHA-1 to register in Google Cloud.</span>
          <em>{googleOn === null ? 'Open →' : googleOn ? 'Connected' : 'Not connected'}</em>
        </Link>
        <Link to="/settings/referrals" className="settings-hub-card">
          <strong>Referral codes</strong>
          <span>How many digits new member codes have.</span>
          <em>Open →</em>
        </Link>
        <Link to="/settings/accounts" className="settings-hub-card">
          <strong>Sign-in &amp; accounts</strong>
          <span>Accounts per device and the username change window.</span>
          <em>Open →</em>
        </Link>
        <Link to="/settings/live-time" className="settings-hub-card">
          <strong>Live time day</strong>
          <span>Timezone and start time of the accounting day for hosts&apos; live time.</span>
          <em>{liveDay || 'UTC · 00:00'}</em>
        </Link>
        <Link to="/settings/rewards" className="settings-hub-card">
          <strong>Live rewards engine</strong>
          <span>Master switch and worker timings for live-time rewards. Rules live in Live rewards → Campaigns.</span>
          <em>{rewardsOn === null ? 'Open →' : rewardsOn ? 'Enabled' : 'Disabled'}</em>
        </Link>
        <Link to="/settings/gifting" className="settings-hub-card">
          <strong>Gifting engine</strong>
          <span>Master switch, built-in defaults, platform account, coin → diamond rate and worker timings. Rules live in Gifting → Policies.</span>
          <em>{giftingOn === null ? 'Open →' : giftingOn ? 'Enabled' : 'Disabled'}</em>
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
  const [logoKey, setLogoKey] = useState('')
  const [tagline, setTagline] = useState('')
  const [avatarKey, setAvatarKey] = useState('')
  const [androidPackage, setAndroidPackage] = useState('')
  const [androidCerts, setAndroidCerts] = useState('')
  const [appLinkScheme, setAppLinkScheme] = useState('')
  const faviconRef = useRef<HTMLInputElement>(null)
  const logoRef = useRef<HTMLInputElement>(null)
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
    setLogoKey(r.data.logo_key || '')
    setTagline(r.data.tagline || '')
    setAvatarKey(r.data.default_avatar_key || '')
    setAndroidPackage(r.data.android_package_name || '')
    setAndroidCerts((r.data.android_sha256_certs || []).join('\n'))
    setAppLinkScheme(r.data.app_link_scheme || '')
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
      tagline: tagline.trim(),
      web_url: webUrl.trim(),
      android_package_name: androidPackage.trim(),
      android_sha256_certs: androidCerts,
      app_link_scheme: appLinkScheme.trim(),
    })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setAppName(r.data.app_name || '')
    setTagline(r.data.tagline || '')
    setWebUrl(r.data.web_url || '')
    setAndroidPackage(r.data.android_package_name || '')
    setAndroidCerts((r.data.android_sha256_certs || []).join('\n'))
    setAppLinkScheme(r.data.app_link_scheme || '')
    await refreshConfig()
    setOk('Branding saved.')
  }

  const imageLabel: Record<BrandImage, string> = { favicon: 'Favicon', logo: 'App logo', avatar: 'Default profile' }
  const setImageKey = (kind: BrandImage, key: string) => {
    if (kind === 'favicon') setFaviconKey(key)
    else if (kind === 'logo') setLogoKey(key)
    else setAvatarKey(key)
  }

  const uploadBrand = async (kind: BrandImage, file: File | null) => {
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
    setImageKey(kind, r.data.key || '')
    await refreshConfig()
    setOk(`${imageLabel[kind]} updated.`)
  }

  const clearImage = async (kind: BrandImage) => {
    setBusy(true)
    setError('')
    setOk('')
    const field = kind === 'favicon' ? 'favicon_key' : kind === 'logo' ? 'logo_key' : 'default_avatar_key'
    const r = await put<Branding>('/v1/admin/settings', { [field]: '' })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setImageKey(kind, '')
    await refreshConfig()
    setOk(`${imageLabel[kind]} cleared.`)
  }

  if (loading) return <PageLoader label="Loading branding…" />

  const logoUrl = mediaUrl(mediaBase, logoKey)
  const faviconUrl = mediaUrl(mediaBase, faviconKey)
  const avatarUrl = mediaUrl(mediaBase, avatarKey)

  return (
    <div className="settings-page">
      <div className="page-head">
        <BackLink />
        <h1>App branding</h1>
        <p>
          Name, logo, website, favicon, and default profile used across the product. The app reads these at
          launch: splash, sign-in screens and notifications follow without a new release. The phone&apos;s home-screen
          name and icon are part of the installed APK and change on the next build.
        </p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <section className="settings-card settings-card-wide">
        <form className="settings-form" onSubmit={(e) => void save(e)}>
          <Field label="App name" value={appName} onChange={(e) => setAppName(e.target.value)} maxLength={64} required />
          <Field
            label="Tagline"
            hint="Optional line under the name on the app's splash screen."
            value={tagline}
            onChange={(e) => setTagline(e.target.value)}
            maxLength={80}
          />
          <Field
            label="Website URL"
            hint="Public site for shared join and profile links (from this dashboard — not baked into the APK). Change domain here anytime."
            placeholder="https://example.com"
            value={webUrl}
            onChange={(e) => setWebUrl(e.target.value)}
            maxLength={200}
          />
          <Field
            label="App link scheme"
            hint="Custom URL scheme the APK registers (must match APP_WORDMARK in the white-label build, e.g. xani). Web pages use this to open the installed app."
            placeholder="xani"
            value={appLinkScheme}
            onChange={(e) => setAppLinkScheme(e.target.value.toLowerCase().replace(/[^a-z0-9+.-]/g, ''))}
            maxLength={32}
          />
          <Field
            label="Android package name"
            hint="applicationId of the installed APK (release: app.xani — debug: app.xani.debug). Used with the scheme to open the app from share links."
            placeholder="app.xani"
            value={androidPackage}
            onChange={(e) => setAndroidPackage(e.target.value)}
            maxLength={120}
          />
          <TextArea
            label="Android SHA-256 signing fingerprints"
            hint={`One per line. App Links need SHA-256 (not SHA-1). Debug/sideload keystore fingerprint:\nAD:E4:6B:58:DE:E3:5A:53:40:65:40:9C:92:D7:40:5F:87:EC:3B:F3:EA:C3:C9:83:C3:A5:3A:88:6D:4E:57:59\nAfter save, open ${(webUrl || (typeof window !== 'undefined' ? window.location.origin : '')).replace(/\/$/, '')}/.well-known/assetlinks.json — it must list your package + fingerprint.`}
            value={androidCerts}
            onChange={(e) => setAndroidCerts(e.target.value)}
            placeholder="AA:BB:CC:… (SHA-256)"
            disabled={busy}
            rows={4}
            style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12, whiteSpace: 'pre-wrap' }}
          />
          <div className="brand-row">
            <div className="brand-pick">
              <span className="brand-label">App logo</span>
              <button
                type="button"
                className="brand-thumb brand-thumb-lg"
                onClick={() => logoRef.current?.click()}
                disabled={busy}
                title="Square PNG, 512×512 or larger, transparent background. Used on the splash, sign-in screens, notifications, and as the phone's home-screen icon on the next app build."
              >
                {logoUrl ? <img src={logoUrl} alt="" /> : <span>Click to set</span>}
              </button>
              <input
                ref={logoRef}
                type="file"
                accept="image/png,image/jpeg"
                hidden
                onChange={(e) => void uploadBrand('logo', e.target.files?.[0] || null)}
              />
              {logoKey ? (
                <Button type="button" size="sm" variant="ghost" onClick={() => void clearImage('logo')} disabled={busy}>
                  Clear
                </Button>
              ) : null}
            </div>
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

/**
 * Google sign-in. Shows the two values Google Cloud asks for when creating the
 * OAuth clients (package name and SHA-1), then takes the client IDs it hands
 * back. Apps read the web client ID from /v1/config, so switching to another
 * Google project never needs an app release.
 */
function SettingsGoogle() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [enabled, setEnabled] = useState(true)
  const [webClient, setWebClient] = useState('')
  const [androidClient, setAndroidClient] = useState('')
  const [debugClient, setDebugClient] = useState('')
  const [sha1, setSha1] = useState('')
  const [debugSha1, setDebugSha1] = useState('')
  const [suffix, setSuffix] = useState('.debug')
  const [pkg, setPkg] = useState('')
  const [debugPkg, setDebugPkg] = useState('')
  const [audiences, setAudiences] = useState<string[]>([])

  const apply = useCallback((d: Branding) => {
    setEnabled(d.google_enabled !== false)
    setWebClient(d.google_web_client_id || '')
    setAndroidClient(d.google_android_client_id || '')
    setDebugClient(d.google_android_debug_client_id || '')
    setSha1((d.android_sha1_certs || []).join('\n'))
    setDebugSha1((d.android_debug_sha1_certs || []).join('\n'))
    setSuffix(d.android_debug_suffix ?? '.debug')
    setPkg(d.android_package_name || '')
    setDebugPkg(d.android_debug_package_name || '')
    setAudiences(d.google_client_ids || [])
  }, [])

  useEffect(() => {
    void (async () => {
      const r = await get<Branding>('/v1/admin/settings')
      setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      apply(r.data)
    })()
  }, [apply])

  const save = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const r = await put<Branding>('/v1/admin/settings', {
      google_enabled: enabled,
      google_web_client_id: webClient.trim(),
      google_android_client_id: androidClient.trim(),
      google_android_debug_client_id: debugClient.trim(),
      android_sha1_certs: sha1,
      android_debug_sha1_certs: debugSha1,
      android_debug_suffix: suffix.trim(),
    })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    apply(r.data)
    setOk('Saved. Phones pick the new client up on their next launch.')
  }

  if (loading) return <PageLoader label="Loading Google sign-in…" />

  const mono = { fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12 }

  return (
    <div className="settings-page">
      <div className="page-head">
        <BackLink />
        <h1>Google sign-in</h1>
        <p>
          Create the OAuth clients in Google Cloud → APIs &amp; Services → Credentials, then paste the IDs here. The
          apps read the web client ID at launch and re-read it when the button is tapped, so a rotated key applies at
          once — no new build.
        </p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}

      <section className="settings-card settings-card-wide">
        <header>
          <h2>Step 1 — register in Google Cloud</h2>
          <p>
            One <strong>Web application</strong> client, which both builds share, plus one <strong>Android</strong>{' '}
            client per package and signing key. A debug build installs under its own package and is signed with a
            different key, so to Google it is a separate app and needs its own Android client — without it, sign-in
            works on release and fails on debug.
          </p>
        </header>
        <dl className="fb-kv">
          <div className="fb-kv-row">
            <dt>Release package</dt>
            <dd style={mono}>{pkg || 'Set it in Settings → App branding first.'}</dd>
          </div>
          <div className="fb-kv-row">
            <dt>Release SHA-1</dt>
            <dd style={mono}>{sha1 ? sha1.split('\n').join(', ') : 'Save it below to keep it here.'}</dd>
          </div>
          <div className="fb-kv-row">
            <dt>Debug package</dt>
            <dd style={mono}>{debugPkg || 'Set the release package first.'}</dd>
          </div>
          <div className="fb-kv-row">
            <dt>Debug SHA-1</dt>
            <dd style={mono}>{debugSha1 ? debugSha1.split('\n').join(', ') : 'Save it below to keep it here.'}</dd>
          </div>
        </dl>
        <p className="muted" style={{ fontSize: 12 }}>
          Read a fingerprint off a keystore with{' '}
          <code>keytool -list -v -keystore your.keystore -alias your-alias</code>. For the shared debug keystore{' '}
          (<code>~/.android/debug.keystore</code>, alias <code>androiddebugkey</code>, password <code>android</code>)
          that is <code>B5:60:4F:38:8D:FC:DD:1F:A6:A9:BE:D9:10:CC:6D:9D:80:ED:C1:BE</code>. If a build still cannot
          sign in, <code>adb logcat -s GoogleSignIn</code> prints the package and SHA-1 it actually ran with.
        </p>
      </section>

      <section className="settings-card settings-card-wide">
        <header>
          <h2>Step 2 — paste what Google gave you</h2>
        </header>
        <form className="settings-form" onSubmit={(e) => void save(e)}>
          <label className="muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} disabled={busy} />
            Offer Google sign-in in the apps
          </label>
          <Field
            label="Web client ID"
            hint="The client both builds request a token for, and the audience this server accepts. Required — without it the Google button stays hidden."
            placeholder="1234567890-abcdef.apps.googleusercontent.com"
            value={webClient}
            onChange={(e) => setWebClient(e.target.value)}
            disabled={busy}
            spellCheck={false}
            autoComplete="off"
            maxLength={200}
            style={mono}
          />
          <Field
            label="Android client ID — release"
            hint="The app never sends this; Google matches it by package and SHA-1. It only has to exist in the same project. Kept here so a half-finished setup is visible."
            placeholder="1234567890-ghijkl.apps.googleusercontent.com"
            value={androidClient}
            onChange={(e) => setAndroidClient(e.target.value)}
            disabled={busy}
            spellCheck={false}
            autoComplete="off"
            maxLength={200}
            style={mono}
          />
          <TextArea
            label="Release SHA-1 fingerprints"
            hint="One per line — the signing key of the APK you ship. App Links use the SHA-256 list on the branding page instead."
            placeholder="AA:BB:CC:… (SHA-1)"
            value={sha1}
            onChange={(e) => setSha1(e.target.value)}
            disabled={busy}
            rows={2}
            style={{ ...mono, whiteSpace: 'pre-wrap' }}
          />
          <Field
            label="Android client ID — debug"
            hint="The second Android client, for the debug package and the debug keystore. Leave empty if you never sign in from a debug build."
            placeholder="1234567890-mnopqr.apps.googleusercontent.com"
            value={debugClient}
            onChange={(e) => setDebugClient(e.target.value)}
            disabled={busy}
            spellCheck={false}
            autoComplete="off"
            maxLength={200}
            style={mono}
          />
          <TextArea
            label="Debug SHA-1 fingerprints"
            hint="One per line — the debug/sideload keystore the test build is signed with."
            placeholder="B5:60:4F:… (SHA-1)"
            value={debugSha1}
            onChange={(e) => setDebugSha1(e.target.value)}
            disabled={busy}
            rows={2}
            style={{ ...mono, whiteSpace: 'pre-wrap' }}
          />
          <Field
            label="Debug package suffix"
            hint="What the debug build appends to the package name (applicationIdSuffix in the Android build). Only change this if the build changes."
            placeholder=".debug"
            value={suffix}
            onChange={(e) => setSuffix(e.target.value.toLowerCase().replace(/[^a-z0-9._]/g, ''))}
            disabled={busy}
            maxLength={33}
            style={mono}
          />
          <div className="form-actions">
            <Button type="button" size="sm" variant="ghost" onClick={() => navigate('/settings')}>
              Back
            </Button>
            <Button type="submit" size="sm" loading={busy}>
              Save Google sign-in
            </Button>
          </div>
        </form>
      </section>

      <section className="settings-card settings-card-wide">
        <header>
          <h2>Accepted token audiences</h2>
          <p>
            What this server will accept a Google ID token for, in order. The web client above is always first; any
            extra entries come from the <code>GOOGLE_CLIENT_IDS</code> environment bootstrap.
          </p>
        </header>
        {audiences.length ? (
          <ul className="fb-probe">
            {audiences.map((a) => (
              <li key={a} className="is-ok">
                <span style={mono}>{a}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">None yet — Google sign-in is off until a web client ID is saved.</p>
        )}
      </section>
    </div>
  )
}

/** Sign-in & account rules: device limits and the username change window. */
function SettingsAccounts() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [limit, setLimit] = useState(3)
  const [renameDays, setRenameDays] = useState(30)

  useEffect(() => {
    void (async () => {
      const r = await get<Branding>('/v1/admin/settings')
      setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setLimit(r.data.max_accounts_per_device || 3)
      setRenameDays(r.data.username_change_days || 30)
    })()
  }, [])

  const save = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    const r = await put<Branding>('/v1/admin/settings', {
      max_accounts_per_device: limit,
      username_change_days: renameDays,
    })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setLimit(r.data.max_accounts_per_device || 3)
    setRenameDays(r.data.username_change_days || 30)
    setOk(
      `Saved. Up to ${r.data.max_accounts_per_device || 3} accounts per device; ` +
        `usernames can change every ${r.data.username_change_days || 30} days.`,
    )
  }

  if (loading) return <PageLoader label="Loading…" />

  return (
    <div className="settings-page">
      <div className="page-head">
        <BackLink />
        <h1>Sign-in &amp; accounts</h1>
        <p>
          Limits how many different accounts can log in or sign up from one physical device, and how often a user may
          change their username. Both are enforced server-side; a device or user at the limit gets a clear message.
        </p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      {ok ? <div className="ok-banner">{ok}</div> : null}
      <section className="settings-card">
        <form className="settings-form" onSubmit={(e) => void save(e)}>
          <Field
            label="Maximum accounts per device"
            type="number"
            min={1}
            max={50}
            value={limit}
            onChange={(e) => setLimit(Number(e.target.value) || 3)}
            hint="Between 1 and 50. Accounts already linked to a device keep working; the limit applies to new sign-ins. Default 3."
            required
          />
          <Field
            label="Days between username changes"
            type="number"
            min={1}
            max={365}
            value={renameDays}
            onChange={(e) => setRenameDays(Number(e.target.value) || 30)}
            hint="Between 1 and 365. After changing their username, a user waits this many days before changing it again. Default 30."
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
