# Message privacy

Social settings includes **Block Messages**, off by default. When enabled:

- Incoming direct messages are rejected, including friends, attachments and connected-app sends.
- Other people cannot add this account to a new or existing group, or re-add it after it leaves. The app displays an explanatory popup when the server rejects the addition.
- New friend requests are rejected from every PocketPass screen and connected app, including profiles opened from Boards. Existing pending requests remain for the recipient to accept or decline.
- Existing groups continue working. No memberships, history or friendships are removed. Outgoing messages remain available.

This is an account preference. The toggle changes only after the server confirms the save; offline failures retain the last confirmed value and show a retry message. Room persists it across restarts and refreshes it without overwriting pending bio or chat-colour changes. Other signed-in devices refresh through the existing private friends realtime topic.

Database migration `20260919000100_message_privacy.sql` adds the default-false profile column, owner-only RPC and write-boundary triggers. It was deployed on 2026-09-19, with a schema backup in `/opt/pocketpass/backups/message-privacy-20260919/schema-before.sql`. Database tests roll back all fixture data. Existing Android installations remain compatible and retain their current behavior until this setting is enabled from an updated client.

Validation passed: 25 privacy database assertions (also rerun after deployment), 200 existing group/GIF/public-API assertions, 622 app/shared/UI unit tests, nine Android UI/persistence/migration tests, and three additional phone portrait UI tests. Light/dark layouts were captured and inspected. Release compilation, signature/16 KB alignment verification and iOS shared/UI metadata compilation passed. iOS native runtime testing requires macOS and is separate from metadata compilation.

The signed Android build at `captures/PocketPass-message-privacy.apk` is a local build only. No GitHub release, release notes, version code or update feed was changed for this feature.

The setting uses the standard teal circular settings glyph, Rubik bold heading and semibold supporting copy. The explanatory copy is shortened to match the surrounding settings typography without reducing its font size.

## Friend requests and Board profiles — 22 September 2026

Board note authors and members in Board settings open the existing profile viewer. A nonfriend can use Add Friend unless the profile has Block Messages enabled. The recipient's preference is refreshed from the profile; the database trigger is the final authority when a client has stale data.

Migration `20260922000400_friend_request_message_privacy.sql` is deployed. It rejects new pending friend requests at the table boundary, including native RPCs and the connected-app API, and maps the API refusal to HTTP 403 with `FRIEND_REQUESTS_BLOCKED`. Turning the setting off permits new requests again. The developer documentation and Social settings copy describe this behavior.

The migration and nine pgTAP assertions passed in a rollback-only transaction against the production schema before application, and the same nine assertions passed after application. The 25 existing message-privacy and 29 friend-code assertions passed. Production health checks passed. The full database suite still has unrelated historical expectations: `admin_console.test.sql` expects 8 effective permissions where the current server has 18, and `public_api_followups.test.sql` expects the older scope catalogue and pre-GIF attachment types. See [2026-09-22-handoff.md](2026-09-22-handoff.md).

The updated signed Android APK was installed on the AYN Thor as version 0.1.11-alpha (25), preserving the app installation. No GitHub alpha release, update feed, or version number was changed.

## Split Social privacy — 25 September 2026

The new Social settings separate **Block Messages** (incoming direct messages only) from **Block Invites** (new group and Board invitations, plus friend requests). Existing group chats and earlier requests remain available. The 21→22 Room migration and pending server migration copy an existing enabled `block_messages` value to `block_invites`, preserving the old combined choice until the owner changes either setting. The new invite RPC is owner-only; direct-message and invitation gates are enforced at their respective database write boundaries.

**Show Boards** is a local display preference. Turning it off shows only conversations in Messages on phone and dual-screen layouts, hides Board navigation there, and ignores Board deep links until re-enabled. It does not delete Board membership or history.

Migration `20260925000100_split_social_privacy.sql` and the updated app are implemented locally but **not deployed or published**. Deployment requires a fresh approval under [release-approval.md](release-approval.md), a verified backup, migration application, and production checks. The local Board/API contract suite passes 65 tests; Android host builds and focused emulator settings, persistence and migration tests pass. The full emulator UI class was interrupted by an emulator crash while taking its older settings screenshots, so it is not claimed as passing.
