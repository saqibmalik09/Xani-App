# Admin console

Web console for the same identity API and Postgres that serve the mobile apps.
No direct database access from the browser.

## Setup

```bash
cp .env.example .env
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/v1` to `VITE_API_BASE_URL`.

## Super Admin (seeded on identity boot)

- Email: `superadministrator@gmail.com`
- Username: `superadmin`
- Password: `simple123password`

Restart identity after deploy so the seed runs once (idempotent).

## Deploy (admin.smilee.online)

On the server (DNS A record → this box first):

```bash
cd ~/Xani && git pull --ff-only
sudo bash client-apps/admin-web/install-admin.sh
```

That script builds with empty `VITE_API_BASE_URL` (SPA calls same-origin `/v1`),
writes `/etc/nginx/sites-available/xani-admin`, reloads nginx, and runs Certbot.

Optional:

```bash
sudo CERTBOT_EMAIL=you@example.com bash client-apps/admin-web/install-admin.sh
sudo USE_PM2=1 bash client-apps/admin-web/install-admin.sh   # optional pm2 serve
sudo SKIP_CERTBOT=1 bash client-apps/admin-web/install-admin.sh
```

Nginx serves `dist/` directly (no Node required for traffic). `/v1` is proxied
to identity on `127.0.0.1:8081`.

## Scope (phase 1)

- Sign in with email or username + password
- Gate on `admin.view_dashboard`
- Overview shell, profile (name, bio, username, avatar, password)
- Multi-role / user management: next
