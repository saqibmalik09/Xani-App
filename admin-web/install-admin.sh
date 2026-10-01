#!/usr/bin/env bash
# Install / refresh the admin console at admin.smilee.online
#
# What it does:
#   1) npm run build with empty VITE_API_BASE_URL (SPA uses same-origin /v1)
#   2) Writes nginx site → serves dist/ + proxies /v1 → identity :8081
#   3) Reloads nginx
#   4) Issues / renews TLS with the Certbot already on the box
#
# PM2 is optional. Nginx serves the static build (fast, no Node hang).
# If you still want a PM2 process for a health check, set USE_PM2=1.
#
# Usage (on the server):
#   Full stack (identity + admin-web):  preferred
#     cd ~/Xani && git pull --ff-only && sudo bash backend-services/dev/update-server.sh
#
#   Admin site only (nginx + certbot first time):
#     cd ~/Xani && git pull --ff-only
#     sudo bash client-apps/admin-web/install-admin.sh
#
# Overrides:
#   DOMAIN=admin.smilee.online
#   REPO_DIR=~/Xani
#   USE_PM2=1          # also run: pm2 serve dist --spa -p 4173 (nginx still serves files)
#   SKIP_CERTBOT=1     # nginx only; print the certbot command
#   SKIP_BUILD=1       # reuse existing dist/

set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "run: sudo bash $0" >&2
  exit 1
fi

DOMAIN="${DOMAIN:-admin.smilee.online}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# Prefer the checkout this script lives in (~/Xani/... or /opt/xani/...).
REPO_DIR="${REPO_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
APP_DIR="${APP_DIR:-$SCRIPT_DIR}"
DIST_ROOT="${DIST_ROOT:-$APP_DIR/dist}"
CONF="/etc/nginx/sites-available/xani-admin"
LINK="/etc/nginx/sites-enabled/xani-admin"
TEMPLATE="$APP_DIR/nginx-admin.conf.template"
USE_PM2="${USE_PM2:-0}"
SKIP_CERTBOT="${SKIP_CERTBOT:-0}"
SKIP_BUILD="${SKIP_BUILD:-0}"

if ! command -v nginx >/dev/null 2>&1; then
  echo "ERROR: nginx is not on PATH. This script will not install it." >&2
  exit 1
fi

if [ ! -f "$TEMPLATE" ]; then
  echo "ERROR: missing template $TEMPLATE" >&2
  exit 1
fi

# ─── build (same-origin /v1 via nginx) ───────────────────────────────────────
if [ "$SKIP_BUILD" != "1" ]; then
  if ! command -v npm >/dev/null 2>&1; then
    echo "ERROR: npm not found. Install Node or set SKIP_BUILD=1 with a ready dist/." >&2
    exit 1
  fi
  echo "==> build admin-web (VITE_API_BASE_URL empty → same-origin /v1)"
  cd "$APP_DIR"
  # Empty base so production fetch('/v1/...') hits this host; nginx proxies to identity.
  VITE_API_BASE_URL= VITE_APP_NAME="${VITE_APP_NAME:-Xani}" npm run build
fi

if [ ! -f "$DIST_ROOT/index.html" ]; then
  echo "ERROR: $DIST_ROOT/index.html missing. Run npm run build first." >&2
  exit 1
fi

# ─── optional PM2 (not required for static; nginx serves dist/) ──────────────
if [ "$USE_PM2" = "1" ]; then
  if ! command -v pm2 >/dev/null 2>&1; then
    echo "WARN: USE_PM2=1 but pm2 not found; continuing with nginx-only static." >&2
  else
    echo "==> pm2 serve (optional health process on :4173; nginx still serves dist/)"
    # npx serve is only started if you later switch nginx to proxy; kept as a named process.
    pm2 delete xani-admin 2>/dev/null || true
    pm2 serve "$DIST_ROOT" 4173 --spa --name xani-admin
    pm2 save
  fi
fi

# ─── nginx site (idempotent replace of our file only) ────────────────────────
echo "==> nginx site ${DOMAIN}"
if grep -R --include='*.conf' -n "server_name.*${DOMAIN}" /etc/nginx/sites-enabled /etc/nginx/conf.d 2>/dev/null \
   | grep -v "xani-admin" | grep -q .; then
  echo "ERROR: ${DOMAIN} is already a server_name in another nginx site." >&2
  grep -R --include='*.conf' -n "server_name.*${DOMAIN}" /etc/nginx/sites-enabled /etc/nginx/conf.d || true
  exit 1
fi

# | delimiters so / in DIST_ROOT never breaks sed (no escape dance).
sed -e "s|DOMAIN_ADMIN|${DOMAIN}|g" \
    -e "s|DIST_ROOT|${DIST_ROOT}|g" \
    "$TEMPLATE" > "$CONF"

ln -sfn "$CONF" "$LINK"

if ! nginx -t; then
  echo "ERROR: nginx -t failed. Leaving $CONF in place for inspection; not reloading." >&2
  nginx -t >&2 || true
  exit 1
fi
systemctl reload nginx
echo "    nginx reloaded → http://${DOMAIN}/"

# ─── certbot ─────────────────────────────────────────────────────────────────
CERTBOT=""
if command -v certbot >/dev/null 2>&1; then
  CERTBOT="certbot"
elif python3 -m certbot --help >/dev/null 2>&1; then
  CERTBOT="python3 -m certbot"
fi

if [ "$SKIP_CERTBOT" = "1" ]; then
  echo
  echo "SKIP_CERTBOT=1 — issue TLS yourself:"
  echo "  sudo ${CERTBOT:-certbot} --nginx -d ${DOMAIN}"
  exit 0
fi

if [ -z "$CERTBOT" ]; then
  echo "WARN: certbot not found. Site is on HTTP only." >&2
  echo "  Install certbot, then: sudo certbot --nginx -d ${DOMAIN}" >&2
  exit 0
fi

echo "==> certbot --nginx -d ${DOMAIN}"
CERTBOT_ARGS=(--nginx -d "$DOMAIN" --non-interactive --agree-tos --redirect)
if [ -n "${CERTBOT_EMAIL:-}" ]; then
  CERTBOT_ARGS+=(--email "$CERTBOT_EMAIL")
else
  CERTBOT_ARGS+=(--register-unsafely-without-email)
fi
# Non-interactive renew/issue; does not touch other certificates.
$CERTBOT "${CERTBOT_ARGS[@]}" || {
  echo "WARN: certbot failed. DNS A record for ${DOMAIN} must point at this server." >&2
  echo "  Retry: sudo ${CERTBOT} --nginx -d ${DOMAIN}" >&2
  exit 1
}

echo
echo "OK  https://${DOMAIN}/"
echo "    SPA:  $DIST_ROOT"
echo "    API:  https://${DOMAIN}/v1/config  (proxied to identity :8081)"
echo "    Login: superadministrator@gmail.com / simple123password"
echo "    (restart identity once if that seed has not run yet)"
