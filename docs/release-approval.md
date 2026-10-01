# Release approval

The user requested on 19 September 2026 that every further publication requires
their explicit approval again. Ask before publishing a GitHub release, replacing
a published APK, changing the release feed, or making a new deployment.

On 22 September 2026, the user explicitly authorized deployment of the
Board-profile/Block Messages friend-request change and installation on the AYN
Thor, and explicitly said **no alpha release**. The database migration and
developer-docs update were deployed; the signed APK was installed only on Thor.
That authorization does not cover a later publication or deployment.

On 23 September 2026, the user gave standing permission to install future
PocketPass builds directly on their connected AYN Thor without asking each
time. This supersedes earlier instructions to obtain fresh approval for each
Thor installation. It does not authorize server deployment, publishing an
alpha or GitHub release, replacing a published APK, or changing the update
feed; those actions still need fresh approval.

Later on 23 September 2026, the user explicitly requested a new release with
their supplied notes. That authorizes publication of `v0.1.11-alpha` and its
normal update-feed propagation only; it is not standing approval for future
releases or deployments.

The user then explicitly asked to change that release to `0.2.0-beta` and
supplied replacement release notes. This authorizes publishing the beta update
and its normal update-feed propagation. Build 26 is required so devices on
build 25 receive the new version. This is not standing approval for subsequent
releases or deployments.

On 26 September 2026, the user explicitly requested a new public release with
the notes stored in `releases/0.2.1-beta.md`. They separately approved deploying
the Block Invites migration and updated privacy notice, and then approved the
email-privacy migration and SMTP/TLS configuration before publication. These
approvals cover this release and these prerequisites, not future deployments or
releases.

Later on 26 September 2026, after reporting that new email sign-ups returned
"PocketPass sign in is temporarily unavailable," the user explicitly approved
deploying the focused email-OTP sign-up hotfix after testing and a fresh
encrypted backup. That approval covers the gateway sign-up restriction and
`20260926000200_email_otp_signup_guard.sql`, not another public app release.

Later on 26 September 2026, the user approved deploying the corrected developer
docs, which describe Block Messages and Block Invites separately. Only
`developer/docs.html` changed (SHA-256
`c1f110b6e646b1a18e9ad8b747fdb8d71d533170bbfd912b4adf560d8060a0a6`, matching the
live page); the previous file is at
`/opt/pocketpass/deploy-backups/docs-block-invites-20260926/docs.html` and
`health.sh` passed. This does not cover later deployments or releases.

On 27 September 2026, the user approved deploying the developer docs without
the "Original protocol and walkthrough examples" archive and its links. Only
`developer/docs.html` changed on the VM (SHA-256
`8b051ce8d161f975a74a1b1dd9218d2914ebd7b1dee83c51296b7b58c2459ebe`, matching the
live page); the previous file is at
`/opt/pocketpass/deploy-backups/docs-archive-removal-20260927/docs.html` and
`health.sh` passed. This does not cover later deployments or releases.

Later on 27 September 2026, the user approved pushing everything to the server
("push everything, so server, private repo and then make new ios build, but no
public release"). Deployed: migrations `20260927000100_worker_rpc_service_role_checks`,
`20260927000200_drop_unused_rpcs` and `20260927000300_piip_wording`; the updated
`backup.sh`, `restore.sh`, `health.sh`, `configure-production-env.sh`,
`rotate-resend-key.sh` and comment-only script changes; the website, admin and
developer copy and asset cleanup. A fresh backup was taken first with the new
`backup.sh` (`pocketpass-20260927T122416Z.tar.gz.age`, SHA-256 `ee3ed38c2c1340c67163ee9736aab0f29c8bc798b6f3881196786071f11f9d47`) and copied
to `PocketPass-backups\production-backups\`; the previous files are in
`/opt/pocketpass/deploy-backups/cleanup-20260927/`. The migrations passed a
rolled-back dry run, then `health.sh` (48 passes), `validate-auth-production.sh`
and `validate-public-api-production.sh` passed. No app release or update-feed
change was made. This does not cover later deployments or releases.

On 28 September 2026 (27 September UTC), the user approved deploying account
bans ("Do 1 and 2": the server change and the admin console files). Deployed:
migration `20260927000400_account_bans`; `compose.production.yml` with
`PGRST_DB_PRE_REQUEST` on `rest` and the `before-user-created` Auth hook on
`auth` (both recreated, Kong restarted); the updated `health.sh`,
`validate-auth-production.sh`, README and test files; and the admin console
`admin.js`, `index.html` and `admin.css`. A fresh backup was taken first
(`pocketpass-20260927T222905Z.tar.gz.age`, SHA-256
`07c4826d64cbffc5c4c641b54e1ff2a00cd31a42147584401afdb5b7fdbeabac`) and copied to
`PocketPass-backups\production-backups\`; the previous files are in
`/opt/pocketpass/deploy-backups/account-bans-20260928/`. The migration passed a
rolled-back dry run with every pgTAP file compared with and without it, then
`health.sh` (50 passes), `validate-auth-production.sh`,
`validate-public-api-production.sh` and the `account_bans`, `admin_console` and
`admin_permissions` pgTAP files passed on production. Not approved or deployed:
the privacy and delete-account page text, and any live ban test. No app release
or update-feed change was made. This does not cover later deployments or
releases.

Later on 28 September 2026, the user approved publishing the ban wording ("Just
push the text to the website"). Deployed: `website/privacy.html` (a Ban records
paragraph in A2, ban retention in A7, banning in A8, last updated 27 September
2026) and `website/delete-account.html` (ban records under "What remains"). The
previous pages are in
`/opt/pocketpass/deploy-backups/account-bans-20260928/infra/supabase/website/`,
and the served pages match the repo. This does not cover later deployments or
releases.

Later on 28 September 2026, the user approved deploying the connected-app
friend presence fix ("Yes deploy the fix"). Realtime v2.102.3 refuses a private
channel join unless broadcast read is allowed, so connected apps with presence
access could never join `friend-presence:` channels. Deployed: migration
`20260928000100_api_friend_presence_join` (the `pocketpass_api_realtime_read`
policy allows broadcast read on a `friend-presence:` topic exactly when presence
read is allowed) and the updated `public_api_presence_tokens_encounters` pgTAP
file. A fresh backup was taken first (`pocketpass-20260927T231733Z.tar.gz.age`,
SHA-256 `c36ad6e212ed14e51bffef32736ada69c56c54ff553268d59a170e901580a243`) and
copied to `PocketPass-backups\production-backups\`; the previous test file is in
`/opt/pocketpass/deploy-backups/presence-join-20260928/`. The migration passed a
rolled-back dry run (the new join test failed without it and passed with it;
other files unchanged), then `health.sh` (50 passes) and the presence pgTAP file
(68 of 69; the remaining failure is existing scope-catalog drift) passed on
production. This does not cover later deployments or releases.

On 28 September 2026, the user approved deploying the public API fixes ("Yes
deploy"): bans now stop connected apps, the per-app rate counter is split over
16 rows, profile changes reach `friends:` as a user id only, friend-code misses
count and refusals carry `Retry-After`, every `/v1` error uses the documented
envelope, Kong's per-IP `/v1` limit is 1,000,000 a minute with its headers
hidden, `privacy.get`/`privacy.set` and the `privacy:` topic cover Block Invites,
`notifications.list` accepts `updated_after` and Boards drafts sort with an id
tie-break. Deployed: migration `20260928000200_api_hardening`; the Caddyfile
(Caddy recreated) and `kong.yml` (Kong restarted); `developer/docs.html`; the
updated README, `validate-public-api-production.sh`, `build-examples.py` and
pgTAP files. A fresh backup was taken first
(`pocketpass-20260928T090755Z.tar.gz.age`, SHA-256
`370927fdd7e11e631d0b15f3fd7650e78eef4e5f0e7493d5e5a670adc0e58897`) and copied to
`PocketPass-backups\production-backups\`; the previous files are in
`/opt/pocketpass/deploy-backups/api-hardening-20260928/`. The migration passed a
rolled-back dry run against every pgTAP file, the Caddy rewrite was tested in a
throwaway container and the full Caddyfile validated, then `health.sh` (50
passes), `validate-auth-production.sh`, `validate-public-api-production.sh`
(including the new `/v1` error checks) and the `api_hardening` (28),
`account_bans` (55), `developer_limit_requests` (70) and `public_api_v1` (223)
pgTAP files passed on production. The PostgREST pool was not changed. This does
not cover later deployments or releases.

Later on 28 September 2026, the user approved deploying the API follow-ups
("Yes deploy"): message retries return the message after an edit, a delete,
leaving the chat or a block; `messages.send` skips its attachment check on a
replay; an over-long `blocks.list` cursor is `INVALID_CURSOR`; Boards artwork
upload errors use the documented envelope; the consent page shows the app
description; the developer portal and admin console no longer offer per-app
Realtime connections; and the developer docs cover the Realtime events,
media retries, consent scopes, revocation timing and the smaller drift.
Deployed: migration `20260928000300_api_followups`; `board-media/server.py`
(image rebuilt, container recreated); `caddy/site/oauth/consent.js` and
`oauth.css`; `developer/developer.js`, `index.html` and `docs.html`;
`admin/admin.js`; the README and the `api_hardening` pgTAP file. A fresh backup
was taken first (`pocketpass-20260928T093333Z.tar.gz.age`, SHA-256
`0a187f2ad6ac0a6c0f1ad571a2caf20f2515df7a998a1199340220f8b20996d1`) and copied to
`PocketPass-backups\production-backups\`; the previous files are in
`/opt/pocketpass/deploy-backups/api-followups-20260928/`. The migration passed a
rolled-back dry run against every pgTAP file; the board-media unit tests passed
(14); then `health.sh` (50 passes), both validation scripts and the
`api_hardening` (36), `account_bans` (55), `group_conversations` (68),
`public_api_v1` (223) and `message_edit_delete` (26) pgTAP files passed on
production. This does not cover later deployments or releases.

Later on 28 September 2026, the user approved deploying the OpenID `profile`
fix ("Yes deploy"). A live test with disposable accounts showed the `profile`
scope returned the account email (as `name` and inside `user_metadata`) and,
for Discord accounts, the Discord email, ID, name and avatar, even without the
`email` scope. Deployed: migration `20260928000400_oauth_profile_username`
(triggers keep `auth.users.raw_user_meta_data` to `{ username, name }` with the
PocketPass username, or `PocketPass user` before one is chosen; all 130
existing accounts cleaned; consent text "your PocketPass username"), the new
`oauth_profile_metadata` and updated `developer_portal`/`public_api_role`
pgTAP files, the README and `developer/docs.html`. A fresh backup was taken
first (`pocketpass-20260928T104040Z.tar.gz.age`, SHA-256
`e6555b1bc26b62f61ffc400d3c48add1309ef674b8adb55c0da73d0c7e58c15e`) and copied to
`PocketPass-backups\production-backups\`; the previous files are in
`/opt/pocketpass/deploy-backups/oauth-profile-20260928/`. The migration passed a
rolled-back dry run against every pgTAP file; after deploy `health.sh` (50
passes), both validation scripts, the `oauth_profile_metadata` (10) pgTAP file
and a live OAuth check with a disposable account and app (14 of 14, both deleted
afterwards) passed. This does not cover later deployments or releases.

On 30 September 2026, the user approved updating the release repository's page
("Push", then "Push the website push to github"). `Hinoaaaaaf212/pocketpass-release`
got a new README listing PocketPass features with a 7-second intro GIF from
presentation v9 (`bba8861`), then a commit removing the AYN Thor design credit
(`866275b`). No release, APK or update feed changed; 0.2.1-beta (27) stays latest.

The same day, the user approved pushing the website ("Push the website"). The
home page Thor now uses the cream Thor from the presentation
(`assets/thor-cream.webp`, drawn on the old frame's canvas so the layout is
unchanged, with the screenshots above its glass), and the "AYN Thor design by
lnkd" footer note is removed. Deployed `index.html`, `site.css` and
`assets/thor-cream.webp`; the live files match the local SHA-256 hashes
(`18a7c016b048eef6`, `51035f1b059c2cdf`, `224788212e784f7a`). The previous
`index.html`, `site.css` and `thor-black.webp` are in
`/opt/pocketpass/deploy-backups/website-thor-2026-09-30/`; `thor-black.webp`
remains on the server, unused. This does not cover later deployments or releases.

Later on 30 September 2026, the user asked for buttons on the release repository
page ("Make nice buttons ... on the github"). `89a7414` adds SVG buttons in the
website's button style (Rubik Bold outlined as paths; Website and Ko-fi have
light and dark versions) for Download the latest APK, Website, Join the Discord
and Support on Ko-fi. No release, APK or update feed changed.

On 30 September 2026, the user approved pushing the website restyle ("Push the
website"). pocketpass.xyz now uses the developer docs' look in light and dark:
the thick-bordered header with pill nav, thick rounded card borders, glossy pill
buttons, teal text and the plain mint gradient instead of the triangle backdrop.
Layout and sections are unchanged; the Privacy & Terms, Delete your account and
404 pages share the stylesheet. Deployed `index.html`, `privacy.html`,
`delete-account.html`, `404.html` (backdrop canvas and script removed) and
`site.css` (a new style layer, then a follow-up so the hero's two buttons fit on
one line again with the live release label). Live files match the local SHA-256
hashes (`index.html` `0463a235013f0e74`, `privacy.html` `81b487cb81b56818`,
`delete-account.html` `b4549332348221b7`, `404.html` `3161ca4cdfae08f7`,
`site.css` `dfb98598087e48ff`). The previous files are in
`/opt/pocketpass/deploy-backups/website-docs-look-2026-09-30/`; `backdrop.js`
remains on the server, unused. This does not cover later deployments or releases.

On 1 October 2026, the user approved the Boards alerts database update and the
developer docs deploy ("Deploy to developer docs and database update"). Before
the change, `backup.sh` made `pocketpass-20261001T180653Z.tar.gz.age` (SHA-256
`8a7655779097e031`), copied to `PocketPass-backups\production-backups\`. A
rolled-back dry run applied the migration and passed the catalog checks;
`public_api_boards`, `account_bans` and `api_hardening` pgTAP gave the same
results before and after (29, 55 and 36 passing). `migrate.sh` then applied
`20261001000100_board_mentions_and_alert_levels` (checksum `f5ff976ae7761691`):
@mentions, per-member alert kinds, the `push_level` alert setting and push data
with the kind, actor and board name. `health.sh`, `validate-auth-production.sh`
and `validate-public-api-production.sh` passed, and the three pgTAP files pass
on the live database. `developer/docs.html` now lists the `mentions` note field
and the inbox kinds (live SHA-256 `c6bc10bfb815fc65`). The previous docs and
function definitions are in `/opt/pocketpass/deploy-backups/board-alerts-20261001/`.
No release, APK or update feed changed. This does not cover later deployments or
releases.

On 1 October 2026, the user approved publishing a new Android release with
their release notes ("make a new version in the public releases with these
release notes"), plus pushing to both GitHub repositories and a new iOS build.
This is 0.2.2-beta, versionCode 28, floor `minSupportedVersionCode` 23, notes in
`releases/0.2.2-beta.md`. Its only backend prerequisite,
`20261001000100_board_mentions_and_alert_levels`, was already applied that day.
This does not cover later deployments or releases.
