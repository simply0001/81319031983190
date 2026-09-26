# PocketPass Boards implementation

Implemented and released on 19 September 2026 as **0.1.10-alpha (24)**.

Release: https://github.com/Hinoaaaaaf212/pocketpass-release/releases/tag/v0.1.10-alpha

APK: `releases/PocketPass-0.1.10-alpha.apk` (36,869,342 bytes)

SHA-256: `4b3824048d0ee9af478b832222ef33de2498f6211d436357058b64095cc66332`

Production Boards is enabled, board requests are open, and zero communities were seeded. Both public update feeds serve version 24 with the matching APK hash. Minimum supported version remains 23. The existing release channel uses alpha version names with normal GitHub releases because its poller reads `releases/latest`.

## Delivered

- Separate board schema, authenticated paginated APIs, server-side membership/role/block/restriction enforcement, private media and realtime invalidation. Connected third-party applications receive no board access.
- Public/private proposals, staff approval/direct creation, join approval, accepted direct invitations, revocable codes, member invitation policy, branding, owner/moderator management, accepted ownership transfer, archive/reopen, public-to-private conversion and permanent staff deletion.
- Chats/Boards chooser, joined/Explore directory, feeds and threads, text/drawing notes, replies and reply targets, Yeah reactions, sort/period filters, editing markers, removal placeholders, spoilers, local/cloud drafts with conflict recovery and explicit idempotent publishing.
- White drawing paper, pixel/smooth pens, palette, sizes, eraser, undo/redo, zoom/pan, touch dots, controller focus and Thor keyboard/top-screen preview. Phone/tablet layouts reuse the existing PocketPass design system.
- Per-board mute and push settings, independent board push preferences, grouped activity inbox and Android notification destinations. Queue eligibility is rechecked at delivery, with generic spoiler-safe payloads.
- Dashboard permissions and controls, in-app moderation, central private-review auditing, reports, appeals, board/global restrictions, targeted word rules, protected originals, rejected bio recovery, 30-day retention and per-account burst limits.
- Versioned stationery foundation with only plain paper supplied. Token purchases, achievement access, supporter access, permanent ownership and published appearance snapshots are enforced by the server.
- Additive Room 20-to-21 migration, preserving existing chats/profiles. Existing unrelated app and website edits were retained.
- Account-deletion compatibility: deleting a board owner archives the board for its remaining audience; authorised staff can appoint a replacement owner.

## Production deployment

Applied migrations `20260919000200` through `20260919000700`. Deployed admin controls, authenticated branding image processor, updated push worker, Caddy route and factual privacy copy. The retention cron job is active. An encrypted pre-deployment database/storage backup is at `/var/backups/pocketpass/pocketpass-20260919T135403Z.tar.gz.age`; previous deployed configuration files are saved in `/opt/pocketpass/boards-predeploy-20260919.tar.gz`.

Live checks verified authenticated settings/directory reads, anonymous branding denial, dashboard CORS preflight, served dashboard/privacy files, worker startup, migration ledger and both update feeds. Boards was enabled only after publishing the signed APK. See `boards-operations.md` for the emergency switch and permission/catalogue details.

## Validation

- 36 local PostgreSQL contract scenarios passed: visibility and media access, invitations, blocks, hierarchy/private review, lifecycle, idempotence, drafts, feeds, spoilers, filters/bios, appeals/retention, throttling, entitlements, branding, push eligibility and account deletion.
- All migrations and an integration transaction passed in an isolated copy of the complete production schema, including real authentication functions, pgcrypto, token balances, push registration, branding and the existing account-deletion function. No customer data was copied into that test database.
- 358 shared, 104 UI and 173 Android host tests passed (635 total).
- 7 image-service tests and 9 push-worker tests passed, including HTTP authorization-before-decoding and sanitized commit payloads.
- Android emulator checks passed for 5 Boards UI cases plus Room migration and bio-draft persistence. The UI cases cover spoilers/themes, touch drawing, Thor keyboard, controller focus and the long-caption top preview. The tablet layout check passed in light/dark themes.
- Android debug/release builds and shared/UI iOS metadata compilation passed. Production signing certificate and 16 KB APK/native-library alignment verified.
- Local browser fixture verified the dashboard feature controls and board joining/code/invitation settings. No real board was created for UI testing.

Evidence is under ignored `captures/`: `boards-db-tests.txt`, `boards-full-validation.txt`, `boards-final-build-check.txt`, `boards-release-final.txt`, `boards-ui-tests-final.txt`, `boards-migration-test.txt`, `boards-tablet-tests.txt`, release/deployment logs and emulator screenshots.

## Device review

The release APK is provided for Thor visual review; this implementation did not install it on or take screenshots of the Thor. Real handset push receipt remains a device acceptance check. iOS distribution and a native Xcode build/signing remain deferred; shared iOS metadata compilation was checked on Windows.
