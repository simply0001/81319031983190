# PocketPass self-hosted Supabase

This directory is the production-oriented backend package for PocketPass. It
contains no credentials and does not modify Android, Oracle Cloud, or DNS.

The deployment is designed for one Oracle VM running the official Supabase
Docker stack behind Caddy:

- `pocketpass.xyz`: public website (`website/`: landing, privacy and account-deletion pages) plus a read-only mirror of the app update feed
- `api.pocketpass.xyz`: allow-listed Supabase API routes only
- `links.pocketpass.xyz`: Android App Link association, auth callback fallback, OAuth consent and connected-apps pages
- `developer.pocketpass.xyz`: developer portal and public API docs
- `admin.pocketpass.xyz`: static admin console (see "Admin console")
- `studio.pocketpass.xyz`: Supabase Studio, opened from the admin console by owners
- PostgreSQL, Supavisor, Kong, and Studio: Docker-internal only; Studio is reachable solely through Caddy

A single VM is not highly available. Define an acceptable RPO/RTO and test the
restore procedure before treating it as production.

## Pinned upstream

The expected Supabase source revision is:

```text
241bb11c0627f2981746d37033f57dbfa81d29b0
```

That commit is self-hosted release `v0.8.0`, in which Envoy became the
default API gateway. PocketPass layers the official `docker-compose.pg17.yml`
and `docker-compose.kong.yml` overrides, so the `api-gw` service stays Kong
(container `supabase-kong`, network aliases `kong` and `envoy`) and our
`kong/kong.yml` keeps applying; migrating the gateway policy to Envoy is
separate work. It does **not** layer the upstream Caddy override:
`compose.production.yml` provides its own pinned Caddy image and routes
Studio on its own hostname behind an owner-session gate (see "Studio
access").

Prepare the upstream checkout:

```bash
sudo install -d -o pocketpass -g pocketpass /opt/pocketpass
sudo -u pocketpass git clone https://github.com/supabase/supabase.git \
  /opt/pocketpass/supabase-upstream
sudo -u pocketpass git -C /opt/pocketpass/supabase-upstream \
  checkout --detach 241bb11c0627f2981746d37033f57dbfa81d29b0
```

The application files live in `/opt/pocketpass/app`; change
`POCKETPASS_INFRA_DIR` if another absolute path is used. On the production VM
this is a copied file tree, not a git checkout: copy changed files to the VM and
`sudo install` them (`-m 644`, scripts `-m 755`, LF line endings) instead of
pulling. `.env.production` stays root-only and is never replaced from a local
copy.

## Configuration

For message push notifications, see [Firebase message notification setup](../../docs/firebase-message-notifications.md) and [iOS APNs and App Store setup](../../ios-app/APP_STORE.md). Android and iOS share the optional `message-push` worker, which uses the `push` Compose profile and a server-only Firebase credential; Firebase is not required for the rest of the stack.

Copy the sanitized template, fill every applicable `REPLACE_ME` value, and
restrict it to the service account:

```bash
cd /opt/pocketpass/app/infra/supabase
cp .env.production.example .env.production
chmod 600 .env.production
chmod 750 scripts/*.sh
```

Generate a coherent key set using the key-generation workflow from the pinned
official Supabase self-hosting release. Never reuse example JWTs or passwords.
The Android app receives only `SUPABASE_PUBLIC_URL` and
`SUPABASE_PUBLISHABLE_KEY`; legacy anon keys may be required internally by the
upstream stack, but service-role and `sb_secret_` keys never belong in an APK.

Required operator-provided values are:

- PostgreSQL password and the full generated Supabase JWT/JWK/API-key set
- dashboard, Realtime, Vault, postgres-meta, Storage, and Logflare secrets
- TLS contact email
- Discord client ID and client secret
- verified Resend sender and Resend API key in `SMTP_PASS`
- an `age` backup recipient; its private identity stays off the VM
- optionally the Ko-fi webhook verification token and Discord webhook URLs
  (see "Ko-fi memberships" and "Limits and limit requests")

`SMTP_HOST=smtp.resend.com`, implicit-TLS port `465`, and user `resend` are
preserved in the template. The production Compose overlay pins GoTrue to port
`465`, so mail cannot fall back to a cleartext SMTP connection. Run
`scripts/validate-auth-production.sh` after any Auth recreation: it checks the
running GoTrue environment and verifies the Resend certificate and TLS
handshake without sending an email. Configure SPF, DKIM, and DMARC in Resend
before sending real mail. Resend's onward delivery to recipient mail servers
uses its ordinary delivery policy; it is not guaranteed end-to-end TLS.

Account email remains in Supabase's private `auth.users` table because Auth
needs it for sign-in and email delivery. It is not part of `public.profiles` or
the curated developer API. The account owner can see their own address; staff
with the relevant `users`, `supporters`, or `audit` permission can see the
addresses required in the admin console. OAuth apps receive an address only
when the user approves the explicit OIDC `email` scope; it may then be present
in the ID token and UserInfo response. OAuth access-token email claims are
blanked by `pocketpass_access_token_hook` even with that scope. Keep the Auth
schema private and do not attempt to encrypt `auth.users.email` in place.
First-party Auth sessions still contain the owner's email claim: Android saves
them with Keystore encryption and iOS uses Keychain; signed-in web pages use
browser storage. Avoid adding account addresses to URLs, server logs, cached
profiles or export files. The standard Caddy access log records request URLs,
not OTP and Ko-fi POST bodies.

Public email signup uses the same passwordless flow as existing-user sign-in.
Username accounts sign up with a password instead (see "Username accounts"
under "Auth setup"). Since 26 September 2026 Kong accepts public
`POST /auth/v1/signup` only for `@users.pocketpass.xyz` addresses and blocks
path variants, so email accounts are created only through the email-code flow.
Development fixture accounts are created by `seed.sql`.

## Network and DNS prerequisites

The operator must create DNS records for `pocketpass.xyz`,
`api.pocketpass.xyz`, `links.pocketpass.xyz`, `admin.pocketpass.xyz`,
`developer.pocketpass.xyz`, and `studio.pocketpass.xyz`
pointing to the VM (plain A records to the VM address; Caddy issues each
certificate on first request once the record resolves).

The network policy is enforced in three layers:

1. **OCI Security List / NSG** (managed in the Oracle console, not from this
   repository):

   | Direction | Protocol | Port | Source |
   |---|---|---|---|
   | ingress | TCP | 80 | 0.0.0.0/0 |
   | ingress | TCP | 443 | 0.0.0.0/0 |
   | ingress | UDP | 443 | 0.0.0.0/0 |
   | ingress | TCP | 22 | administrator CIDR only |

   Nothing else is allowed in. Keep the SSH restriction at this layer — it is
   recoverable from the console if the administrator IP changes, unlike a
   host-level rule.

2. **Host INPUT chain**: left as shipped by the OCI Ubuntu image
   (`netfilter-persistent` restores it at boot: SSH, established traffic,
   reject). Never flush iptables wholesale on OCI — the `InstanceServices`
   rules for 169.254.0.0/16 carry iSCSI and instance metadata, and removing
   them can make the VM unbootable.

3. **DOCKER-USER chain**: Docker-published ports bypass INPUT entirely, so
   `scripts/configure-firewall.sh` installs a default-deny DOCKER-USER chain
   that admits only 80/tcp, 443/tcp, and 443/udp from the external interface.
   Even a container port accidentally published in a future compose revision
   stays unreachable. `systemd/pocketpass-firewall.service` applies it at boot
   before Docker starts. `health.sh` independently asserts that no service
   except Caddy publishes a host port.

Kong and Supavisor ports (8000, 8443, 5432, 6543) and Studio are never
published; do not add `ports:` entries for them.

## Host hardening

Apply the SSH, fail2ban, and unattended-upgrades policy (idempotent; keep the
current SSH session open and verify a fresh login from a second terminal
before closing it):

```bash
sudo /opt/pocketpass/app/infra/supabase/scripts/configure-host-hardening.sh
```

- SSH (`/etc/ssh/sshd_config.d/10-pocketpass.conf`): key-only authentication,
  no root login, `AllowUsers ubuntu`. The `pocketpass` service account has no
  SSH access; reach it with `sudo -u pocketpass`. The script refuses to run if
  `ubuntu` has no `authorized_keys` and self-reverts if `sshd -t` rejects the
  drop-in.
- fail2ban: sshd jail, systemd backend, 5 retries / 10 minutes, 1 hour ban.
- unattended-upgrades: security pocket (and ESM) only, no automatic reboot.
  Docker engine packages are blacklisted — an unattended dockerd restart is a
  full-stack outage. Apply kernel and Docker updates manually in maintenance
  windows; containers are pinned separately via `SUPABASE_UPSTREAM_REF` and
  the Caddy digest.

Install the firewall unit and the backup/health timers:

```bash
sudo /opt/pocketpass/app/infra/supabase/scripts/install-systemd-units.sh
```

Timers: nightly backup at 03:30 UTC, health check every 30 minutes. A failed
run appends to `/var/log/pocketpass-alerts.log` and logs with tag
`pocketpass-alert`. Check status with:

```bash
systemctl list-timers 'pocketpass-*'
journalctl -t pocketpass-alert
```

## Studio access

Studio is never published on the host and has no sign-in of its own. Caddy
proxies `studio.pocketpass.xyz` to `studio:3000` and gates every request with
`forward_auth`: PostgREST runs `public.studio_gate()` (callable only as
`anon`), which reads the `pocketpass_studio` cookie and succeeds only when it
matches a live row in `private.admin_studio_sessions` that belongs to an
admin with `is_owner = true`. Anything else is redirected to
`/_pocketpass/login`, a static page from `studio/` that explains to open Studio
from the console. Only `/_next/static/*` (public, content-hashed Studio code)
bypasses the gate. Sessions are created by the admin console:

1. An owner clicks **Open Studio**. The console calls
   `admin_studio_session_create()`, which stores the SHA-256 of a random
   256-bit token for twelve hours, audits `studio_session_create`, and replaces
   any earlier session of that owner.
2. The console opens `https://studio.pocketpass.xyz/_pocketpass/login#<token>`
   in a new tab. The fragment never reaches a server or a log; the page stores
   the token as a `Secure; SameSite=Strict` cookie for that host only and
   navigates to `/project/default`.
3. Signing out of the console calls `admin_studio_sessions_revoke()`, so the
   Studio tab stops working immediately. Demoting or removing the owner has
   the same effect.

Non-owner admins never see the button and cannot mint sessions (`PT403`).
Studio grants `postgres`-level power, so keep the owner list short. No
dashboard password is involved: `DASHBOARD_USERNAME`/`DASHBOARD_PASSWORD`
remain in `.env.production` only because the upstream compose file requires
them.

Rollout of a Caddyfile, `studio/` or `website/` change:
`compose up -d --no-deps --force-recreate --wait caddy`. Then
`curl -sI https://studio.pocketpass.xyz/api/platform/profile` must answer
`302` with `location: /_pocketpass/login`; `health.sh` also mints a 60-second
session straight in the database and proves a cookie-bearing request reaches
Studio.

Break-glass when the console or API is unavailable: publish Studio to the VM
loopback temporarily and tunnel to it, for example
`sudo docker run --rm --network pocketpass_default -p 127.0.0.1:3000:3000 alpine/socat TCP-LISTEN:3000,fork,reuseaddr TCP:studio:3000`
together with `ssh -L 3000:127.0.0.1:3000 …`, and stop the container when
done. To withdraw browser access entirely, delete the `studio.pocketpass.xyz`
block and the `POCKETPASS_ADMIN_STUDIO_URL` entry from `/config.js`, then
recreate Caddy.

## Admin console

`https://admin.pocketpass.xyz` is a static page (`admin/`) served by Caddy from
a read-only mount. It talks to the normal public API with the publishable key,
which Caddy injects at `/config.js` from `SUPABASE_PUBLISHABLE_KEY`; nothing in
`admin/` is generated or secret.
Owners also get an **Open Studio** button there, which opens Studio in a new
tab on a twelve-hour session that signing out revokes (see "Studio access").

Anyone with a PocketPass account can request a sign-in code there, but every
`public.admin_*` RPC (`20260816000100_admin_console.sql`,
`20260817000100_admin_permissions.sql`) checks `private.admin_users` and raises
`42501` otherwise. Every admin can read the overview stats; everything else is
a per-admin permission from `private.admin_permission_keys()`:

| Key | Grants |
|---|---|
| `users` | the user list with emails and per-account detail |
| `audit` | the audit log and the recent-actions panel |
| `legacy` | flipping `profiles.legacy_account` |
| `tokens` | adding or removing tokens |
| `achievements` | force-unlocking or revoking achievements |
| `admins` | the Admins tab: add, edit and remove other admins |
| `apps` | the Developer apps tab: suspending and reactivating third-party apps |
| `supporters` | the Supporters tab: Ko-fi payments, linking payer emails, granting or revoking supporter status |
| `board_*` | ten Boards keys (`board_requests`, `boards`, `board_content`, `board_members`, `board_suspensions`, `board_private_review`, `board_delete`, `board_filters`, `board_stationery`, `board_settings`), described in [boards-operations.md](../../docs/boards-operations.md) |

Owners (`is_owner`) hold every permission implicitly, cannot be edited or
removed from the console, and are promoted or demoted only with psql. Day-to-day
admin management happens in the console's Admins tab (needs `admins`); the
guards are: an owner is untouchable from the UI, nobody can remove themselves or
drop their own `admins` permission. psql is only needed for owners and
emergencies:

```bash
# promote (or demote with false) an owner
docker compose ... exec -T db psql -U postgres -d postgres -c \
  "update private.admin_users set is_owner = true, updated_at = now()
   where user_id = (select id from auth.users where lower(email) = lower('someone@example.com'));"
# emergency grant without the console
docker compose ... exec -T db psql -U postgres -d postgres -c \
  "insert into private.admin_users (user_id, note, permissions)
   select id, 'ops', array['users','audit'] from auth.users where lower(email) = lower('someone@example.com')
   on conflict (user_id) do nothing;"
# revoke
docker compose ... exec -T db psql -U postgres -d postgres -c \
  "delete from private.admin_users
   where user_id = (select id from auth.users where lower(email) = lower('someone@example.com'));"
# who did what
docker compose ... exec -T db psql -U postgres -d postgres -c \
  "select created_at, admin_id, action, target_user_id, payload
   from private.admin_audit order by id desc limit 50;"
```

The console can flip `profiles.legacy_account` (which unlocks `day_one`
immediately), add or remove tokens (the user gets a `system` notification; the
audit reason is never shown to them), force-unlock or revoke achievements
(revoking an earned one lasts only until the user's next `get_achievements()`),
and shows aggregate stats plus the audit log. The achievement key list lives in
three places that must stay in sync: `private.achievement_keys()`,
`public.get_achievements()`, and `AchievementCatalog` in the app.

Changing the Caddyfile or the Caddy service requires
`docker compose ... up -d --no-deps --wait caddy`; Caddy runs with `admin off`
so there is no live reload, and the API is unreachable for a few seconds while
the container is recreated. Residual risk of the sign-in flow: a 6-digit code
valid for ten minutes, one code email per address per minute, GoTrue's verify
rate limits, and the Kong limit on `POST /auth/v1/otp`. Username accounts add
a password surface: at least eight characters, bcrypt-hashed by GoTrue, with
Kong limiting `POST /auth/v1/signup` to 5/minute and 20/hour and password
sign-in (`POST /auth/v1/token?grant_type=password`) to 10/minute and 60/hour
per client IP, and `POST /auth/v1/recover` terminated with 404 so no reset
path exists.

## Public API and developer portal

Third-party apps connect a PocketPass account with OAuth 2.1 (authorization
code + PKCE) and then act for that user through a curated JSON API. No new
service runs on the VM: GoTrue is the authorization server, PostgREST executes
the API functions, and Kong and Caddy fence connected-app tokens in.

- **Authorization server.** GoTrue's built-in OAuth 2.1 server. Discovery is
  `https://api.pocketpass.xyz/auth/v1/.well-known/openid-configuration`; the
  endpoints are `GET /auth/v1/oauth/authorize` (PKCE `S256` only: `plain` is refused by a terminating Kong route
  and any other value is rejected by GoTrue),
  `POST /auth/v1/oauth/token` and `GET /auth/v1/oauth/userinfo`, all open
  cors-only Kong routes. Authorize and token are limited to 30 requests per
  minute and 300 per hour per client IP. Dynamic client registration is off;
  clients exist only through the portal. Authorization codes are single-use
  and expire after ten minutes.
- **Consent.** `SITE_URL` is the bare origin `https://links.pocketpass.xyz`
  and `GOTRUE_OAUTH_SERVER_AUTHORIZATION_PATH` is `/oauth/consent`, so GoTrue
  sends users to `https://links.pocketpass.xyz/oauth/consent?authorization_id=…`,
  a static page from `caddy/site/oauth/` that signs the user in (email code,
  Discord, or username and password), shows the app and the requested scopes, and approves or denies
  through `/auth/v1/oauth/authorizations/{id}`. A user with a live consent is
  approved silently. `https://links.pocketpass.xyz/oauth/apps` lists and
  revokes connections. The Android app always passes an explicit
  `redirect_to`, so the shorter `SITE_URL` changes nothing for it; the
  developer portal's Discord sign-in is the only new entry in
  `ADDITIONAL_REDIRECT_URLS`.
- **Token shape.** `public.pocketpass_access_token_hook` (GoTrue's custom
  access-token hook) rewrites every token that carries `client_id`: `role`
  becomes `api_client`, and email, phone and metadata are blanked. The
  Postgres role `api_client` (`NOLOGIN`, granted to `authenticator`) can
  execute only `public.api_v1_*`, read Storage objects through two policies
  that re-check scope and consent, upload message media through one insert
  policy (`messages:write`, own folder, own conversation), receive
  Broadcast on the `conversation:`, `notifications:`, `friends:`, `tokens:`
  and `encounters:` topics and Presence on the `conversation:` and
  `friend-presence:` topics through one select policy on
  `realtime.messages` (`private.api_can_access_realtime_topic` and
  `private.api_can_read_presence_topic`, scope-aware), and track Presence on
  those same topics through one insert policy
  (`pocketpass_api_presence_track`, `presence:write`). It holds no other
  table privilege, no other RPC, no GraphQL, cannot send Broadcast and
  cannot join `app_updates`.
  `usage` on schema `realtime` can only be granted by `supabase_admin`
  (`postgres` has no grant option there), so that one statement lives outside
  the migrations: `docker exec supabase-db psql -U supabase_admin -d postgres
  -c 'grant usage on schema realtime to api_client'`.
- **Gateway fence.** Kong `post-function` blocks decode the Bearer JWT
  payload: on the `/auth/v1/` catch-all and on `/graphql/v1` any token that
  contains `"client_id"` is refused with `403 oauth_client_forbidden`; on
  `/rest/v1/` and `/storage/v1/` such a token is refused unless it also
  carries `"role":"api_client"`, which catches a token minted while the hook
  was off. Connected apps therefore reach only `/auth/v1/oauth/token`,
  `/auth/v1/oauth/userinfo`, `/v1/*`, Storage (reads plus RLS-gated
  message-media uploads) and the Realtime websocket (RLS-gated Broadcast
  and Presence),
  and every `POST /storage/v1/object/sign/…` request is clamped to a
  15-minute TTL.
- **Curated API.** `POST https://api.pocketpass.xyz/v1/<resource>.<action>`
  with `Authorization: Bearer <access token>` and a JSON object body (`{}`
  when there are no arguments). Caddy rewrites the path to
  `/pp-api/rpc/api_v1_<resource>_<action>` and injects the publishable
  `apikey` and `Prefer: params=single-object`; Kong forwards `/pp-api/*` to
  PostgREST and answers `404` on `/rest/v1/rpc/api_v1_*`, so `/v1/` is the
  only entry. Every response is a JSON object; errors are
  `{code, message, hint}` (`PT400`–`PT500`) with the matching HTTP status.
  The database allows 120 requests per minute per app and user (failures
  count); Kong adds a 6000/minute per-IP backstop. Endpoint, error and
  rate-limit tables live in the portal's `/docs`.
- **Developer portal.** `https://developer.pocketpass.xyz` is a static site
  (`developer/`, served like `admin/`) with the same email-code, Discord and
  username sign-in. Any
  PocketPass account may register up to five apps through the
  `public.developer_*` RPCs; the registry is `private.developer_apps`, the
  audit trail `private.developer_audit`, usage `private.api_usage`. Creating
  an app inserts the GoTrue client row (the secret is shown once and stored as
  a hash); adding a scope to an existing app revokes its consents so users
  re-approve. Admins with the `apps` permission see every app in the console
  and can suspend or reactivate it.

Scopes are declared per app, shown on the consent page next to the OIDC
scopes, and enforced per request by `private.api_guard`:

After deployment, verify email disclosure with a disposable, consenting test
account and an app registered with `profile:read`. Complete two authorizations
for that same account, requesting `scope=openid` and `scope=openid email`, and
save the two OAuth token responses in separate mode-600 files. Run
`python3 scripts/verify-oauth-email-privacy.py --openid-only /path/to/openid.json
--email-scope /path/to/email.json`. It checks ID tokens, UserInfo, access-token
claims and both ordinary profile endpoints without printing the address or
tokens. Remove the temporary token files when done; do not use an existing
user's account or send messages for this test.

| Scope | Grants |
|---|---|
| `profile:read` | `me.get`, `profiles.get`, `profiles.get_many` and avatar reads |
| `friends:read` | `friends.list`, `friends.requests_list` and the `friends:<user>` Realtime topic |
| `friends:write` | `friends.request_send`, `friends.request_respond`, `friends.request_cancel`, `friends.remove`, `friends.code_get`, `friends.code_resolve` |
| `messages:read` | `conversations.list`, `conversations.get`, `messages.list`, message-media reads and the `conversation:<id>` Realtime topics |
| `messages:write` | `conversations.open`, `conversations.mark_read`, `messages.send` (with image attachments), `messages.edit`, `messages.delete` and message-media uploads |
| `groups:write` | `conversations.create`, `conversations.add_members`, `conversations.remove_member`, `conversations.leave`, `conversations.rename` |
| `notifications:read` | `notifications.list`, `notifications.mark_read`, `notifications.delete` and the `notifications:<user>` Realtime topic |
| `presence:read` | Presence on the `friend-presence:<low>:<high>` topics (with `friends:read`) and the `conversation:<id>` topics (with `messages:read`) |
| `presence:write` | Tracking Presence on those same topics; implies `presence:read` |
| `tokens:read` | `tokens.get` and the `tokens:<user>` Realtime topic |
| `encounters:read` | `encounters.list` and the `encounters:<user>` Realtime topic |
| `puzzles:read` | `puzzles.get` |
| `boards:read` | Board reads, the stationery catalogue and the `boards:<user>` Realtime topic |
| `boards:write` | posting, editing and removing notes and replies, reactions, reports and appeals |
| `boards:membership` | joining and leaving, invitations and ownership offers, board requests, notification preferences and read markers |
| `boards:invite` | direct invitations and invitation codes |
| `boards:manage` | owner tools and artwork uploads (`/v1/boards.artwork_upload`) |
| `boards:moderate` | local moderator tools and explicit review of blocked content |
| `boards:drafts` | cloud drafts and the `board-drafts:<user>` Realtime topic |
| `boards:purchase` | `boards.buy_stationery` |
| `blocks:read` | `blocks.list` and the `blocks:<user>` Realtime topic |
| `blocks:write` | `blocks.set` |
| `privacy:read` | `privacy.get` (Block Messages) and the `privacy:<user>` Realtime topic |
| `privacy:write` | `privacy.set` (Block Messages) |

`session.get` and `session.revoke` need no scope.

The Boards endpoints and fields are listed in `public-api/boards.json`; see
[public-api-boards.md](../../docs/public-api-boards.md). Block Invites has no
API: `set_invite_privacy` refuses OAuth tokens.

### Limits and limit requests

Every app starts with 120 requests per minute per user, 600 per minute per
app and a burst cap of 100 per second per app (`private.developer_apps`
columns `user_rate_limit_per_minute`, `rate_limit_per_minute`,
`rate_limit_per_second`; `realtime_connection_limit` is recorded only, the
Realtime cap is tenant-wide: `_realtime.tenants` holds 5000 concurrent
connections, 500 joins/s, 1000 events/s and 1 MB/s, set by hand as
`supabase_admin`. Realtime's self-host seed would delete and re-insert that
row with the hard-coded defaults (200/100/100/100 KB) on every boot, so the
overlay sets `SEED_SELF_HOST=false`; the row now persists across restarts,
`health.sh` checks the values, and a fresh install must boot once with seeding
on before they are applied. If `JWT_JWKS` ever changes, update
`_realtime.tenants.jwt_jwks` by hand as well. The overlay also raises the
file-descriptor limits that used to cap proxied websockets at about 2000:
Kong runs with `worker_rlimit_nofile`/`ulimit` 65536 and 16384 connections per
worker, Realtime with `RLIMIT_NOFILE=65536`, and PostgREST keeps a pool of 25
connections). Developers ask for more from the app
page in the portal (`public.developer_request_limits`, one pending request per
app, stored in `private.developer_limit_requests`). Each new request and each
decision is posted to Discord through `pg_net` with the webhook stored in
Vault as `discord_limit_requests_webhook`; admins with the `apps` permission
approve (optionally with adjusted values, applied to the app at once) or deny
from the console's Apps page (`public.admin_resolve_limit_request`), and the
developer sees the decision and note in the portal.

The webhook URL lives in `.env.production` as
`DISCORD_LIMIT_REQUESTS_WEBHOOK_URL`; run `scripts/sync-vault-secrets.sh`
after changing it (and after a restore) to copy it into Vault. An empty value
removes the secret and silently disables the notifications; `health.sh` warns
either way. The same script syncs `DISCORD_SUPPORTERS_WEBHOOK_URL` and
`KOFI_VERIFICATION_TOKEN` (see "Ko-fi memberships").

### Feature flag and rollback

`PUBLIC_API_ENABLED` in `.env.production` drives both
`GOTRUE_OAUTH_SERVER_ENABLED` and `GOTRUE_HOOK_CUSTOM_ACCESS_TOKEN_ENABLED`,
so the server never runs without the hook. `configure-production-env.sh`
writes `true` unless `PUBLIC_API_ENABLED=false` is in its environment. To
switch the feature off:

```bash
sudo env PUBLIC_API_ENABLED=false RESEND_SECRET_FILE=... BACKUP_AGE_RECIPIENT=... \
  /opt/pocketpass/app/infra/supabase/scripts/configure-production-env.sh
docker compose ... up -d --no-deps --force-recreate --wait auth
sudo docker restart supabase-kong
```

Editing the `PUBLIC_API_ENABLED=` line in `.env.production` by hand is
equivalent. Kong caches the `auth` container's address, so `/auth/v1/*`
answers 502 after the recreate until Kong is restarted; the third command is
not optional. With the flag off GoTrue stops serving `/auth/v1/oauth/*`, so
connected apps can neither obtain nor refresh tokens; access tokens already
issued keep working on `/v1/*` for at most one hour. The migration, the
`api_client` role, the Kong fence and the static pages stay in place and are
inert. Turning the flag back on is the same recipe with `true`.

### Break-glass with psql

The console (permission `apps`) suspends and reactivates apps; when it is
unavailable, suspend one by hand with the same three statements:

```bash
docker compose ... exec -T db psql -U postgres -d postgres -c \
  "update private.developer_apps set status = 'suspended', updated_at = now() where client_id = '<client id>';
   update auth.oauth_clients set deleted_at = now() where id = '<client id>';
   delete from auth.sessions where oauth_client_id = '<client id>';"
```

Reactivate with `status = 'active'` and `deleted_at = null`. To force every
user of an app to reconnect without suspending it:

```bash
docker compose ... exec -T db psql -U postgres -d postgres -c \
  "update auth.oauth_consents set revoked_at = now() where client_id = '<client id>' and revoked_at is null;
   delete from auth.sessions where oauth_client_id = '<client id>';"
```

### After a restore

`restore.sh` runs `pg_restore --no-owner --no-privileges` and does not apply
`roles.sql`, so a restored cluster may lack the `api_client` role and loses
every grant made to it. Re-create the role (`roles.sql` from the archive, or
`create role api_client nologin; grant api_client to authenticator;`), then
re-run the grant statements of `migrations/20260829000100_public_api.sql`,
`migrations/20260829000300_public_api_followups.sql` and
`migrations/20260903000100_public_api_presence_tokens_encounters.sql`
(schema usage, `select`/`insert` on `storage.objects`, `select`/`insert` on
`realtime.messages`, and `execute` on the `public.api_v1_*` functions and
the `private.api_can_*` predicates), and as `supabase_admin` run
`grant usage on schema realtime to api_client`. `health.sh` fails while the
role or the realtime grant is missing; `test-database.sh` proves the grants.

### Rollout

1. Create the DNS A record for `developer.pocketpass.xyz` before touching
   Caddy; the certificate is issued on first request.
2. Copy the changed files to the VM (see "Pinned upstream"), then
   `migrate.sh` (inert until the flag is on) and `test-database.sh`.
3. `configure-production-env.sh` (new `SITE_URL`, redirect list and
   `PUBLIC_API_ENABLED=true`), `compose up -d --no-deps --wait auth`,
   `sudo docker restart supabase-kong`, then
   `validate-public-api-production.sh --auth-only` and
   `validate-auth-production.sh`.
4. `compose up -d --no-deps --force-recreate --wait api-gw` for
   `kong/kong.yml`, then `... --force-recreate --wait caddy` for the
   Caddyfile and the new `developer/` mount (a few seconds of API downtime),
   then `health.sh` and the full `validate-public-api-production.sh`.
5. Register a test app in the portal and run the connect flow against a
   test account.

`validate-public-api-production.sh` needs `SMOKE_EMAIL` (an existing test
account; the script signs it in through `admin/generate_link` without sending
mail and signs it out again) for the first-party smoke and accepts
`CLIENT_TOKEN` (an access token of a connected app) for the positive checks;
both are skipped with a warning otherwise.

## Ko-fi memberships

A Ko-fi membership (any tier) unlocks every Mii hat for 36 days per payment.
Ko-fi only reports payments, never cancellations, so each `Subscription`
payment extends `public.supporter_status.active_until` to the payment time
plus `private.kofi_grant_interval()` (31 days plus 5 days of grace) and the
status simply lapses when the next payment does not arrive. Hats bought with
tokens are unaffected: `private.owns_mii_hat` stays the single ownership rule
and also passes while the supporter window is open. Status rows are expired,
never deleted, because the app only reacts to inserts and updates.

Webhook URL to paste into Ko-fi's Webhooks page:
`https://api.pocketpass.xyz/webhooks/kofi`. Caddy rewrites it to
`/pp-hooks/kofi` (body capped at 64 KB, query string dropped), Kong routes that
(30 per minute per IP) to PostgREST's `public.kofi_webhook(data text)`, which
only `anon` may execute; the direct `/rest/v1/rpc/kofi_webhook` path is
blocked. Every delivery is verified against the Vault secret
`kofi_verification_token`, filled from `KOFI_VERIFICATION_TOKEN` in
`.env.production` by `scripts/sync-vault-secrets.sh` (run it after changing
the value and after a restore; an empty value makes the endpoint answer
`PT500`, so nothing is accepted). Accepted deliveries are stored in
`private.kofi_events` with the token and duplicate payer-email JSON key
stripped; the restricted normalized `email` column is retained for matching.
The private Discord supporter alert includes payment and match identifiers,
not the payer's email or name. Anything that fails after the
row exists lands in `kofi_events.error` and the webhook still answers 200 so
Ko-fi stops retrying; a retry with the same `message_id` is answered as a
duplicate and re-attempts an unapplied grant.

Matching runs in this order: an admin-made link (`private.kofi_links`,
Supporters tab → Link…), then the account whose sign-in email equals the
payer's Ko-fi email, then the Ko-fi message. The message may name the account
as `@username` (or `username: name`) or carry its eight-digit friend code as
shown in Settings; a message that names two accounts is ignored. A message
match also links the payer's email to that account so renewals, which carry
no message, keep matching. A payment that arrives before the account exists
is applied when the profile is created, and one that arrives before a
username account links its email is applied when that address is linked.
Unmatched payments stay in the Supporters tab until linked. `DISCORD_SUPPORTERS_WEBHOOK_URL` (optional, synced to Vault as
`discord_supporters_webhook`) posts every delivery to Discord with its match
result. The daily pg_cron job `pocketpass-supporter-lapse` sends lapsed
supporters a system notification; the app takes a no-longer-allowed hat off
the Mii draft, and the server refuses to save a Mii wearing one.

Break-glass with psql (`docker exec supabase-db psql -U postgres`):

```sql
insert into public.supporter_status (user_id, active_until, source)
values ('<user uuid>', now() + interval '36 days', 'admin')
on conflict (user_id) do update
  set active_until = excluded.active_until, source = 'admin', updated_at = now();

update public.supporter_status set active_until = now() where user_id = '<user uuid>';

select message_id, received_at, event_type, email, error
from private.kofi_events where user_id is null order by received_at desc;
```

## Restoring Edge Functions

No Edge Functions are deployed, so the surface is removed end to end. When
functions ship: re-add the `functions-v1` service block to `kong/kong.yml`
(with `key-auth` this time), restore `/functions/v1/*` to the `@supabase_api`
path list in `caddy/Caddyfile`, and delete the `functions` profile override in
`compose.production.yml`.

The committed Android App Links file contains package
`com.pocketpass.app` and release certificate SHA-256 fingerprint:

```text
8A:EB:D2:A2:25:02:5C:6A:E3:1F:08:9A:35:7F:98:C8:EB:61:B5:DB:1E:00:27:46:16:65:2F:DA:3B:DA:F3:58
```

Replace it when the Android signing identity changes.

## Start and migrate

Render the complete configuration before the first start:

```bash
export ENV_FILE=/opt/pocketpass/app/infra/supabase/.env.production
cd /opt/pocketpass/app
docker compose \
  --env-file "$ENV_FILE" \
  -f /opt/pocketpass/supabase-upstream/docker/docker-compose.yml \
  -f /opt/pocketpass/supabase-upstream/docker/docker-compose.pg17.yml \
  -f infra/supabase/compose.production.yml \
  config --quiet
```

Then start the official stack and apply application migrations:

```bash
docker compose \
  --env-file "$ENV_FILE" \
  -f /opt/pocketpass/supabase-upstream/docker/docker-compose.yml \
  -f /opt/pocketpass/supabase-upstream/docker/docker-compose.pg17.yml \
  -f infra/supabase/compose.production.yml \
  up -d --wait

ENV_FILE="$ENV_FILE" infra/supabase/scripts/migrate.sh
ENV_FILE="$ENV_FILE" infra/supabase/scripts/test-database.sh
ENV_FILE="$ENV_FILE" infra/supabase/scripts/health.sh
```

`migrate.sh` checks the pinned checkout, applies each migration transactionally,
records its SHA-256, and refuses to continue if an applied file was edited.
Never rewrite an applied migration; add a later migration.

For local Supabase CLI development, the CLI project root is `infra/`, because
the conventional config is `infra/supabase/config.toml`. `seed.sql` is local
fixture data and is never part of production migration deployment.

## Data and authorization contract

Public application tables:

- `profiles`
- `friend_requests`
- `friendships`
- `user_blocks`
- `conversations`
- `conversation_members`
- `messages`
- `interaction_events`
- `friend_codes`
- `notifications`
- `nearby_encounters`
- `shop_categories`
- `shop_items`
- `token_balances`
- `user_shop_items`
- `supporter_status`
- `puzzle_panels`
- `puzzle_progress`
- `puzzle_pieces`

RLS is enabled on all account-bound application tables. Anonymous access is denied. Sensitive mutation is
RPC-only; authenticated users receive direct update access only to their own
public profile fields. `age` and `country_code` are intentionally display-only;
date of birth and precise location are not stored.

Mutation RPCs use caller-supplied UUID operation IDs. Repeating identical input
returns the original result, while reusing an operation ID for different input
fails. Removing friends, blocking, accepting requests, creating direct
conversations, and every group RPC (`create_group_conversation`,
`add_group_members`, `remove_group_member`, `leave_group_conversation`,
`rename_group_conversation`) also use a private transactional operation ledger.

Messages are durable PostgreSQL rows. The database publishes message changes to
private `conversation:<uuid>` Realtime topics. Authenticated clients may receive
Broadcast and Presence only when they are active conversation members, and may
track Presence only in those same conversation-scoped topics; connected apps
with `messages:read` plus `presence:read` or `presence:write` get the same
Presence access through the public-API policies. Clients must
refetch message rows after reconnect rather than treating Realtime as durable.

Group conversations are `conversations` rows with `kind = 'group'`, a trimmed
1–80 character title, and up to 20 active members (`private.group_member_limit`).
The creator is the `owner`. Any active member may add their own friends as long
as no block exists in either direction between the two; the owner may rename the
group and remove members; anyone may leave. When the owner leaves, ownership
passes to the earliest-joined remaining member, and a group left with no active
members is deleted with its messages. Blocks do not gate messages inside a group
(direct conversations keep the hard rule). Membership and title changes are
broadcast on the `conversation:<uuid>` topic as the custom `membership` and
`conversation` events, and the affected user receives a `system` notification
carrying the `conversation_id` when they are added, removed, or handed
ownership. `delete_my_account` leaves the caller's groups (transferring
ownership and moving `created_by` to the surviving owner) and deletes only
direct conversations and groups that end up empty.

Friendship and friend-request changes publish invalidations to private
`friends:<user-uuid>` topics. Accepted friends track ephemeral online state in
canonical `friend-presence:<lower-uuid>:<higher-uuid>` channels. Only the two
members of the current accepted friendship can read or track that pair’s
Presence; pending, removed, blocked, and unrelated users are denied (a
connected app additionally needs `friends:read` plus `presence:read` or
`presence:write`). Clients
always reconcile accepted friendships through REST and Room after reconnect.

Nearby credentials and raw receipts live in the private schema. Clients receive
bounded batches of one-time tokens tied to client-generated P-256 public keys;
private signing keys never leave Android's Keystore-backed encrypted storage.
One cryptographically valid receipt resolves the encounter for both
participants, consumes both credentials atomically, and creates participant-only
interaction and notification rows. The same pair is deduplicated for a rolling
24-hour period. Exact-match Kong routes limit credential issuance to 5/minute
and 20/hour per client IP and receipt submission to 30/minute and 120/hour.
When the matching receipt from the other side confirms an encounter, both
participants are credited tokens in the same transaction: 30 the first time a
pair is rewarded, 5 on later days, at most once per pair per UTC day
(`private.encounter_token_rewards`), and each receives a `system`
notification saying so. The same confirmation also hands each participant one
Puzzle Swap piece through the `nearby_encounters_grant_puzzle_pieces` trigger:
a piece the other player owns in the receiver's open panel when there is one
(a handover, recorded with `from_user_id`), otherwise a random missing piece of
the receiver's current puzzle. `private.puzzle_encounter_grants` keeps this to
once per encounter, so retried receipts hand out nothing more.

Devices signed in to the same account do not pass each other.
`public.get_nearby_device_tag_secret()` gives every account a 32-byte secret
(`private.nearby_device_tag_secrets`, created on the first call). Each device
derives a 32-bit tag from it for the current UTC day and carries that tag in
the low half of the invitation nonce it advertises, so its sibling devices
recognise it and skip the connection before either side spends a pass. The
tag rotates daily and cannot be computed without the secret. Older clients
advertise a fully random nonce and keep working unchanged.

Passing streaks and the weekly recap come from
`public.get_passing_stats(p_utc_offset_minutes)`: the run of consecutive local
days with a confirmed encounter (yesterday still counts until today ends), the
best run, and this week's passes, people and regions, with day boundaries taken
from the offset the device sends. The offset is kept in
`private.user_clock_offsets` so the hourly pg_cron job `pocketpass-weekly-recap`
can send each player one `system` notification once their local clock passes
18:00 on Sunday. `private.weekly_recaps` records every week considered; weeks
without a pass are recorded silently.

The private `avatars` bucket allows JPEG, PNG, and WebP objects up to 5 MiB.
Paths must be `<auth-user-uuid>/<filename>`. Users may mutate only their own
folder; reads follow the same profile/block visibility policy as profile rows.
The public `puzzle-panels` bucket holds Puzzle Swap artwork. Only the service
role uploads to it; a panel enters players' collections once its row is active
and its object exists.

## Puzzle Swap

Every account collects puzzles in a fixed order. The first puzzle is always the
player's own Piip (4x4, the app renders the portrait itself) and it starts with
piece 5, which the app also shows before its first sync. After that come the
artwork panels in `public.puzzle_panels` by `sort_order`, one at a time: there
is exactly one open puzzle per player (`puzzle_progress_one_open_per_user`),
and finishing it opens the next available panel with one piece. Pieces arrive
from confirmed encounters (see above), from walking (`report_daily_steps` grants
one piece at 5,000 steps and a second at 10,000 per local day, tracked in
`private.step_reward_days.pieces_awarded`) and from `buy_puzzle_piece`, which
charges `private.puzzle_piece_price()` (15 tokens) for a random missing piece
of the open puzzle and replays by client operation id. Its errors are
`PT402 INSUFFICIENT_TOKENS` and `PT409 COLLECTION_COMPLETE`.

`get_puzzle_collection()` returns one JSON object: `current_index` (null when
everything is complete), `piece_price`, `pieces_owned_total` and `puzzles`,
each with `kind` (`own_piip` or `panel`), `puzzle_key`, `slug`, `title`,
`image_path`, `columns`, `rows`, `total_pieces`, the sorted `owned_pieces`
indexes (row-major from 0), `started_at`, `completed_at` and
`completed_by_handover`. Not yet started panels are listed with no pieces so
the app can show how many puzzles are left. The app caches the collection in
Room and downloads each panel's artwork into app storage, so the game works
offline after one sync.

The achievements `full_set` (every available puzzle complete, at least one
panel) and `missing_piece` (a puzzle finished by a handed-over piece) are
evaluated by `private.achievement_metrics` like the others and never revoked.

To add a panel, write a migration that inserts the row (`slug`, `title`,
`image_path` such as `panels/<slug>.png`, `grid_columns`, `grid_rows`,
`sort_order`) and upload the artwork with the service key (`SERVICE_ROLE_KEY` in `.env.production`), adding `-H "x-upsert: true"` to replace an existing file:

```bash
curl -X POST "https://api.pocketpass.xyz/storage/v1/object/puzzle-panels/panels/<slug>.png" \
  -H "Authorization: Bearer $SERVICE_ROLE_KEY" \
  -H "Content-Type: image/png" \
  -H "x-upsert: true" \
  --data-binary @<file>
```

Until the object exists the panel is skipped. Retire a panel with
`is_active = false`; rows that reference it cannot be deleted. Check what
players can reach with:

```sql
select panel.slug, panel.is_active,
  exists (
    select 1 from storage.objects as object
    where object.bucket_id = 'puzzle-panels' and object.name = panel.image_path
  ) as artwork
from public.puzzle_panels as panel
order by panel.sort_order;
```

## Auth setup

Register this exact Discord callback:

```text
https://api.pocketpass.xyz/auth/v1/callback
```

The post-auth Android redirect is:

```text
https://links.pocketpass.xyz/auth/callback
```

The custom-scheme fallback `pocketpass://auth/callback` is also allow-listed.
Use PKCE in Android. The Discord client secret and Resend API key remain only
in `.env.production`.

For six-digit email OTP, `templates/auth-email/otp.html` is wired to every
GoTrue template that a client can trigger: Magic Link, Confirmation (new or
still-unconfirmed accounts signing in through `/otp`), Recovery, Email Change,
and Reauthentication. Its `{{ .Token }}` placeholder is intentional. The
branded template contains no confirmation link, and every subject is
`Your PocketPass verification code`. Only Invite keeps GoTrue's stock
template because it is never sent.
The production OTP expiry is ten minutes. Apply the direct-OTP policy to an
existing production environment:

```bash
ENV_FILE=/opt/pocketpass/app/infra/supabase/.env.production \
  /opt/pocketpass/app/infra/supabase/scripts/configure-otp-rate-limits.sh
```

Protection is layered:

- Kong accepts only authenticated `POST /auth/v1/otp` requests and limits each
  Caddy-supplied client IP to 10 requests per minute and 60 per hour.
- GoTrue permits one email to the same address every 60 seconds.
- GoTrue permits 1,000 authentication emails per hour across the deployment.

The Kong limiter uses `policy: local` because this release runs one Kong node.
Its counters reset when Kong restarts. Multiple legitimate users behind one
public IP share the same allowance, and a distributed attack can still consume
the global GoTrue allowance. Reconsider CAPTCHA, device attestation, or a
shared counter if that becomes material.

Caddy overwrites `X-PocketPass-Client-IP` with the actual connecting address.
Kong trusts that header only on its private Docker listener; Kong ports are
never published. To roll back new signup without invalidating existing
sessions, set `DISABLE_SIGNUP=true` and `ENABLE_EMAIL_SIGNUP=false`, then
recreate Auth.

### Username accounts

A username account is an ordinary GoTrue user whose address is
`<username>@users.pocketpass.xyz`, the login domain. Nothing is ever mailed to
that domain (it has no DNS records; a null MX record is a sensible addition).
The app maps the username to that address on the device and calls the normal
`POST /auth/v1/signup` and `POST /auth/v1/token?grant_type=password`
endpoints, so GoTrue needs no username concept of its own. Usernames are 3-12
lowercase letters, digits or single dots, a subset of the profile rule that is
also a valid mailbox name.

- `migrations/20260906000100_username_accounts.sql` makes `handle_new_user()`
  claim the username at creation (the `profiles.username` unique constraint
  rejects duplicates, which GoTrue reports as a 500 and the app re-checks
  with `public.username_available(text)`), and adds the `before insert`
  trigger `private.guard_password_signups()`, which runs only for
  `supabase_auth_admin` (or when the session setting
  `pocketpass.enforce_signup_guard` is `on`, which the pgTAP test uses) and refuses any
  login-domain address without a password or whose local part breaks the
  username rule. For every other address it clears the password hash, so
  email-code and Discord users stay passwordless. Until
  `20260926000200_email_otp_signup_guard.sql` it refused those inserts instead,
  which broke new email-code sign-ups because GoTrue sets a temporary hash.
- `ENABLE_EMAIL_AUTOCONFIRM=true` (GoTrue `MAILER_AUTOCONFIRM`) makes a
  username sign-up usable immediately. Kong's signup restriction
  (login-domain addresses only, since 26 September 2026) and the guard stop
  that setting from being abused to register someone else's real address with
  a password.
  `GOTRUE_PASSWORD_MIN_LENGTH=8` matches the app's rule.
- There is no password reset. `POST /auth/v1/recover` is terminated by Kong
  with 404. A username account can link a real email address from Settings:
  `PUT /auth/v1/user {email}` sends the six-digit Email Change code to the
  new address only (`GOTRUE_MAILER_SECURE_EMAIL_CHANGE_ENABLED=false`, since
  the login-domain address cannot confirm anything), and
  `POST /auth/v1/verify {type: email_change}` completes it. From then on the
  account signs in with the email code or with email + password; the profile
  keeps its username. Any account with a real address can change its
  password after `GET /auth/v1/reauthenticate` mails a code that is passed as
  the `nonce` of `PUT /auth/v1/user {password}`
  (`GOTRUE_SECURITY_UPDATE_PASSWORD_REQUIRE_REAUTHENTICATION=true` stays).
- Kong: `auth-v1-signup` (5/minute, 20/hour per IP; login-domain addresses
  only since 26 September 2026), `auth-v1-token-password`
  (10/minute, 60/hour; refresh grants stay on the catch-all),
  `auth-v1-user-update` and `auth-v1-reauthenticate` (bearer endpoints with
  the connected-app token block), and `auth-v1-recover-blocked`.
  `scripts/test-password-rate-limit.sh` exercises the password limiter the
  way `test-otp-rate-limit.sh` exercises the OTP one, and
  `scripts/validate-auth-production.sh` asserts the GoTrue settings, the
  route names and the 404 on `/auth/v1/recover`.
- Ko-fi payments reach a username account when the supporter writes
  `@username` or the friend code in the Ko-fi message, or once the account
  links the email address used at Ko-fi (see "Ko-fi memberships"). Admin
  lookups by email keep working because the login-domain address contains the
  username.

After deployment, verify the dedicated route without sending mail:

```bash
/opt/pocketpass/app/infra/supabase/scripts/test-otp-rate-limit.sh
```

The script sends an intentionally invalid body through Kong. The first ten
requests from one synthetic private test IP reach Auth, the eleventh must
return 429, and a separate test IP must retain its own allowance.

Friend-code resolution has a separate exact-match Kong route with 10 requests
per minute and 50 per hour per client IP. PostgreSQL independently enforces 50
lookups per hour per authenticated account. Verify the gateway layer with:

```bash
/opt/pocketpass/app/infra/supabase/scripts/test-friend-code-rate-limit.sh
```

## Backups

Install `age`, keep its private identity off the VM, and run:

```bash
ENV_FILE=/opt/pocketpass/app/infra/supabase/.env.production \
  /opt/pocketpass/app/infra/supabase/scripts/backup.sh
```

The encrypted archive contains:

- a custom-format logical dump and role snapshot
- local Storage objects
- the `pgsodium_root.key` when present
- the active environment, Caddy, overlay, manifest, and checksums

Before applying `20260926000100_email_privacy.sql` in production, run
`scripts/backup.sh`, check its `.sha256` sidecar, and pull the encrypted archive
off the VM. Only then run `scripts/migrate.sh`. The migration removes duplicate
email keys from historical Ko-fi payloads and admin-audit JSON; the restricted
`auth.users.email`, `private.kofi_events.email`, and `private.kofi_links.email`
fields remain for sign-in and supporter matching. Existing encrypted backup
archives keep pre-cleanup copies until their normal retention expires. Verify
restore with a disposable database, never by running `restore.sh` on the live
server. This change requires fresh production-deployment approval.

OCI boot-volume snapshots are a second layer, not a substitute for this backup.
OCI encrypts boot volumes and their backups at rest by default; verify that the
production database and local Storage mounts actually reside on those volumes
and check the instance's volume-encryption state in the OCI console before
deployment. Logical backup archives use `age` independently of volume
encryption.
After each successful run, archives older than `BACKUP_RETENTION_DAYS` (7) are
deleted from the VM's backup directory. `systemd/pocketpass-backup.timer` runs
the backup nightly at 03:30 UTC (see "Host hardening"). The VM is therefore only
a short-lived staging area; durable retention lives off-site (below).

The archive embeds `.env.production` verbatim, so every backup is a full
secret dump. The `age` private identity is the single point of compromise —
store it off the VM with the same care as the secrets themselves.

### Off-site pull

The VM cannot reach the operator's LAN, so a **local server pulls** each
archive rather than the VM pushing it. The pull is locked down end to end:

- On the VM, `scripts/serve-latest-backup.sh` streams only the newest archive
  and its checksum as a tar on stdout. A dedicated key in `ubuntu`'s
  `authorized_keys` is pinned to it with
  `restrict,command="…/serve-latest-backup.sh"`, so the key grants no shell and
  can read nothing else.
- On the local server, `scripts/pull-backups-offsite.sh` (deployed to
  `~/bin/`) receives the stream, verifies the SHA-256 sidecar, and only then
  replaces the previous local copy — a single-copy mirror. A failed or corrupt
  pull leaves the prior verified archive untouched, and the VM still holds its
  rolling window, so there is never a zero-backup window.
- `systemd/offsite/pocketpass-pull.{service,timer}` are installed as **user**
  units on the local server (`~/.config/systemd/user/`), firing at 04:15 UTC
  after the VM backup. The account needs `loginctl enable-linger` so the timer
  runs headless. Results append to `pull.log` in the backup directory.

These offsite units are intentionally kept out of `systemd/` proper so the
VM's `install-systemd-units.sh` glob does not pick them up.

Restore is deliberately destructive and requires an exact confirmation:

```bash
ENV_FILE=/opt/pocketpass/app/infra/supabase/.env.production \
  /opt/pocketpass/app/infra/supabase/scripts/restore.sh \
  --archive /absolute/path/pocketpass-TIMESTAMP.tar.gz.age \
  --identity /secure/off-vm/age-identity.txt \
  --confirm RESTORE_POCKETPASS
```

The script verifies both checksum layers, stops application services, restores
the Vault key and database, and replaces local Storage. The previous Storage
directory is retained beside the restored one. It does not overwrite the
active environment or automatically apply `roles.sql`. Run `health.sh` and
perform auth/avatar/message checks after every restore drill.

## Public website

`https://pocketpass.xyz` is a static site served by Caddy from `website/`
(bind-mounted read-only at `/srv/website`): `index.html` (landing page),
`privacy.html` and `delete-account.html` (served at `/privacy` and
`/delete-account`; the `.html` paths redirect there), `404.html`, `site.css`,
`site.js`, `backdrop.js`, the Rubik font, `robots.txt`, `sitemap.xml` and the
artwork under `assets/` (copies of the in-app Figma SVGs with
`preserveAspectRatio="none"` stripped, the achievement glyphs recoloured
teal, the five tab icons as `nav-*.svg`, a generated Open Graph image and
touch icon, and the AYN Thor mockups: `thor-black.webp` is the frame,
`thor-<tab>-{top,bottom}.webp` are screenshots taken on the device by
`scripts/capture-thor-tabs.sh` (the Piip Creator pair is captured by hand from
Settings > Social > Edit Piip) and cut to the screen openings by
`scripts/build-website-mockups.py`, whose source render lives in
`website/source/` and is not deployed). The site follows the visitor's light
or dark scheme, loads nothing from other hosts, sets no cookies and has no
analytics; the CSP allows `'self'` only. `site.js` runs on every page: it
drives the mobile menu, the tabbed device tour on the landing page (a tab
click slides the Thor's screens over to that tab) and the release card, which reads
`/updates/latest.json`, mirrored by the apex from `/srv/updates` exactly like
`links.pocketpass.xyz`, and fills in the version, size, date and SHA-256 of
the current release; without it the buttons fall back to the GitHub releases
page. HTML, CSS and JS are served
with `Cache-Control: no-cache` (revalidated through ETags), `assets/` and
`fonts/` with a one-week lifetime.

Deploying a change is a file copy plus a Caddy recreate when the Caddyfile or
the compose file changed (content edits inside `website/` are live at once):
`compose up -d --no-deps --force-recreate --wait caddy`, then `health.sh`,
which probes `/`, `/privacy` and the manifest mirror.

`www.pocketpass.xyz` has no DNS record and is deliberately absent from the
Caddyfile (Caddy would keep retrying ACME for it). Once an A record exists,
add
`www.pocketpass.xyz { import security_headers  redir https://pocketpass.xyz{uri} permanent }`
and recreate Caddy.

## App release channel

The Android app updates itself from GitHub Releases on
`Hinoaaaaaf212/pocketpass-release`, but clients never talk to GitHub's API.
`scripts/poll-app-update.sh` (driven by `pocketpass-app-update.timer`, every
five minutes with ETag conditional requests) merges the latest release's
`update.json` asset with the release metadata and writes
`updates/latest.json`, which Caddy serves read-only at
`https://links.pocketpass.xyz/updates/latest.json` with
`Cache-Control: public, max-age=300` (and mirrors it at
`https://pocketpass.xyz/updates/latest.json` for the website's download
card). The APK itself downloads straight from
the release asset CDN. While the repo has no releases the poller publishes the
sentinel `{"schemaVersion":1,"versionCode":0}`, which clients read as
up-to-date.

Releases are published from the workstation with `scripts/publish-release.ps1`
(repo root), which builds `assembleRelease`, stages `PocketPass.apk` plus a
generated `update.json` (`versionCode`, `versionName`, `apkSha256`,
`apkSizeBytes`, optional `minSupportedVersionCode` for forced updates), and
creates the tagged GitHub release. The release body becomes the in-app
changelog.

`updates/latest.json` and `updates/.cache/` are generated on the VM and stay
untracked. When first deploying this feature: recreate Caddy so the new
`updates/` mount exists (`docker compose ... up -d caddy`), rerun
`sudo scripts/install-systemd-units.sh` to enable the timer, and run
`sudo systemctl start pocketpass-app-update.service` once so `health.sh`'s
manifest probe has a file to check.

## Updates

Do not use Watchtower or floating Supabase image tags. For an update:

1. create and verify an off-host backup
2. review the official self-hosted changelog and new Compose files
3. test the complete new revision on a separate instance
4. update `SUPABASE_UPSTREAM_REF` and the checked-out commit together
5. update the pinned Caddy tag/digest only after validation
6. schedule downtime, pull, recreate, migrate, and run database/health tests

Never run `docker compose down -v` or the upstream reset script in production.
