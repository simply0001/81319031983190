# Boards operations

Boards uses separate private PostgreSQL tables and authenticated first-party RPCs. Anonymous sessions cannot use the feature. The public API additions in `20260921000100`–`20260921000300` give connected applications explicit opt-in Boards scopes, local owner/moderator tools, blocking and Block Messages controls; central staff tools remain dashboard-only. Existing applications gain no permissions automatically. No communities are seeded. See [public-api-boards.md](public-api-boards.md) for rollout status and validation.

## Dashboard permissions

Dashboard owners retain full access. Other staff receive these independent capabilities:

| Permission | Capability |
| --- | --- |
| `board_requests` | Approve/reject board proposals |
| `boards` | Create boards, edit branding/settings, archive/reopen |
| `board_content` | Review content, reports and retained originals |
| `board_members` | Membership, board staff, replacement owners and appeals |
| `board_suspensions` | Global board participation suspensions |
| `board_private_review` | Additional permission for central private-board review |
| `board_delete` | Permanent board deletion |
| `board_filters` | Literal word/phrase rules |
| `board_stationery` | Stationery catalogue |
| `board_settings` | Feature/request switches and burst limits |

Dashboard requests use `review` and `staff_review`. This keeps central decisions central when staff also hold a local board role. Local moderators cannot revoke central restrictions. Private review and moderation actions are audited. Cloud drafts are never exposed through review permission.

In **Global board participation**, staff enter an account UUID and reason, then confirm the suspension. The dashboard shows the resulting case ID and lists active global suspensions by name, account ID, reason and case ID. Search by any of those identifiers and use **Revoke suspension** on the matching case. This limits Board participation across all Boards; it does not suspend the PocketPass account. Board-specific mutes and bans, with their case IDs and revoke controls, appear under **Board restrictions** after opening a Board. An audit ID is a separate record and cannot be used to revoke a restriction.

**Board audit** shows named decisions and changes first, with time, Board, actor, affected account and reason. **All entries** also includes read-access records. Expand **Show record IDs** when exact database identifiers are needed. `board_content` is required to read this audit; `board_private_review` additionally covers private Boards.

**Word and phrase filters** appear near the top of the Boards dashboard for staff with `board_filters`. The current saved rules are listed with their action, reason and active state; search narrows the list. Use **Add rule** or **Edit**, enter both a phrase and a reason, then save. Save errors appear beside the form, and a successful save refreshes the list. Disable a rule by clearing **Enabled** and saving its edit. Board icon and cover previews and imports are collapsed under **Board artwork** until opened; importing an image requires `boards` permission.

## Emergency controls

Boards → Feature controls → clear **Enable Boards** → Save feature settings. Normal board APIs and push delivery stop while stored boards and drafts remain intact. Staff controls stay available. Re-enable through the same panel. **Accept board proposals** separately controls new requests.

Initial per-account limits are 30 submissions, 120 reaction changes, and 10 invitations/reports per minute. There are no daily allowances or board-wide caps.

If the dashboard is unavailable, an operator with database access can update `private.board_settings.enabled` inside a transaction and insert a central `private.board_audit` record with the incident reason. Do not delete tables or roll back Room to disable Boards.

## Media, drafts and retention

Drawing documents use version 1, an 800×600 canvas, at most 1,000 strokes and 20,000 total points, and a bounded serialized size. Posts cannot import media. Static previews come from validated strokes. Branding imports go through `board-media`: authorize before decoding, bound input and decoded image size, flatten animation, remove metadata, resize, and encode WebP. Media reads check current access.

Cloud draft revisions are immutable and owner-private. Conflicts remain separate; expired offline bases recover as independent copies. Publishing is explicit and keeps the same operation ID/payload across uncertain retries. Sync and publishing are serialized to avoid resurrecting published drafts.

The `pocketpass-boards-retention` pg_cron job runs daily at 03:23 UTC. Old/removed content expires after 30 days unless an active case holds it. Superseded draft revisions expire after 30 days; current heads remain. Permanent deletion purges the board's content, drafts, assets and cases, retaining only a minimal deletion audit.

Deleting an owner account archives its boards and revokes invitations without removing the remaining audience. Central staff can appoint a replacement owner and reopen the board.

## Stationery manifest v1

Only plain paper ships initially. The dashboard accepts this versioned format for later artwork:

```json
{"version":1,"background":"#FFFFFF","drawing":{"version":1,"width":800,"height":600,"strokes":[]}}
```

The optional drawing uses the same bounded stroke format as notes: `pen` (`pixel`, `smooth`, `eraser`), permitted palette `color`, pen `size`, and `points` as `[x,y]`. External URLs, arbitrary SVG/HTML and unknown fields are rejected. Artwork is drawn below the user's ink; the white eraser matches the static preview. Catalogue edits increment versions, while published notes keep their stationery snapshot.

Items can be free, token-priced or achievement-linked. Active supporters can use active stationery and still buy permanent ownership. Purchases are idempotent and remain owned after membership expiry or catalogue access changes. Expiry only affects new use.

## Deployment and notifications

Deploy migrations in checksum order, admin files, `board-media`, the updated `message-push`, then validate and recreate Caddy for `/boards/media`. Keep Boards off until the Android alpha is published. Applied migrations are immutable; fixes use new migrations.

The push worker handles independent chat and board queues. Board alerts group by thread, exclude the actor, and recheck membership, blocks, mutes, preferences and the feature switch. Payloads contain routing IDs and generic text, without board titles or note content. Android has a separate Boards notification channel. Older registrations remain chat-only until upgraded.

Shared iOS metadata compilation is checked on this Windows host. Native Xcode signing/distribution remains deferred. Real handset push receipt and Thor visual acceptance remain device checks for the user; no screenshots were taken on the Thor during this implementation.


## Settings hotfix — 19 September 2026

With user approval, migration `20260919000800_board_settings_safe_update.sql` was
applied to production. The feature-settings UPDATE now explicitly targets
`where singleton = true`, allowing the dashboard save through `safeupdate`.
The prior RPC was backed up at
`/opt/pocketpass/board-settings-rpc-before-20260919000800.sql`.

Validation: 37 local Boards database tests passed. On the production database,
a rollback-only transaction loaded `safeupdate`, assumed the authenticated role
with an existing dashboard owner, and exercised disabling and enabling through
`boards_mutate`, checking each result through `boards_query`. The old function
reproduced the reported error; the deployed function passed. The transaction
rolled back, preserving both `enabled=true` and `requests_open=true` without
persistent test audit records. Only this database migration was deployed.
Evidence: `captures/board-settings-{deployment,before-test,after-test}.txt`.
