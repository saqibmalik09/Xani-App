/** Open the installed Android app via custom scheme / intent URL from branding config. */

export type AppOpenConfig = {
  android_package_name?: string
  app_link_scheme?: string
  web_base_url?: string
}

let cached: AppOpenConfig | null = null

export async function loadAppOpenConfig(): Promise<AppOpenConfig> {
  if (cached) return cached
  try {
    const res = await fetch('/v1/config')
    const body = (await res.json()) as { data?: AppOpenConfig }
    cached = body.data || {}
    return cached
  } catch {
    return {}
  }
}

/** Try to hand off to the installed APK. Safe no-op on desktop / when unset. */
export function tryOpenApp(path: string, cfg: AppOpenConfig): boolean {
  const scheme = (cfg.app_link_scheme || '').trim().toLowerCase()
  const pkg = (cfg.android_package_name || '').trim()
  if (!scheme && !pkg) return false
  const clean = path.replace(/^\//, '')
  const ua = typeof navigator !== 'undefined' ? navigator.userAgent || '' : ''
  const android = /Android/i.test(ua)
  if (android && scheme) {
    // Chrome intent: opens whichever installed app registers the scheme —
    // release and debug/sideload builds both match (a package pin would skip
    // the debug build, whose applicationId carries a suffix). Play fallback
    // sends a phone without the app to the store page when we know the package;
    // otherwise Chrome stays on this landing.
    const fallback = pkg
      ? `;S.browser_fallback_url=${encodeURIComponent(`https://play.google.com/store/apps/details?id=${pkg}`)}`
      : ''
    const intent = `intent://${clean}#Intent;scheme=${encodeURIComponent(scheme)};action=android.intent.action.VIEW${fallback};end`
    window.location.href = intent
    return true
  }
  if (scheme) {
    window.location.href = `${scheme}://${clean}`
    return true
  }
  return false
}
