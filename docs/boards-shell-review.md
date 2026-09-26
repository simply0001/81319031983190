# Boards shell and soundtrack — 20 September 2026

Review notes from 20 and 21 September 2026, written while the app version was
0.1.10-alpha (24). This work reached the public app in 0.2.0-beta (26) on
23 September 2026.

## Interface

- Boards opens directly, with private messages and activity in the same space.
  The former Chats/Boards chooser is no longer shown. Notification links still
  open their conversation or board thread directly.
- Boards replaces the main app navigation. Thor and compact phones use its own
  header and tabs; wide tablets use a side rail with Boards, Messages, Activity,
  Drafts and a PocketPass exit. System/controller Back leaves nested pages first,
  then returns to Home. Main shoulder-tab shortcuts cannot jump out accidentally.
- A subtle tiled background, lighter cards, author rows and paper previews draw
  from the supplied Miiverse and Swapnote references. Typography, selection green,
  settings switches and navigation assets remain PocketPass components.
- The note editor has white 4:3 paper, tools on either side, ink swatches, and
  expandable size/zoom controls. Thor has a larger paper area and a separate
  author/drawing preview. Drawings and private-message permissions are unchanged.

## Audio

Source: the supplied `PocketPass - Messaging Board(1).wav`, 16,128,184 bytes,
91.428571 seconds, stereo 44.1 kHz PCM.

- Android: `app/src/main/res/raw/bgm_boards.ogg`, Opus at 72 kb/s VBR,
  48 kHz stereo, 993,761 bytes (about 94% smaller).
- iOS: `ios-app/Resources/audio/bgm_boards.m4a`, AAC at 128 kb/s,
  48 kHz stereo, 1,496,950 bytes. The existing audio resource folder includes it.
- The WAV is not copied into either app. No additional player or decoder is
  bundled. The existing player loops the track, uses the music-volume preference,
  pauses with app lifecycle, and selects the normal track when leaving Boards.
  Communities, private conversations and their subpages share the Boards track.

The initial 30% boost and additional 20% boost are applied cumulatively to the
original source audio (`-af volume=1.56`), rather than raising the user's music
setting or re-encoding an already compressed file. The boosted source peaks at
-6.4 dBFS; decoded Android/iOS files peak at -6.1/-6.4 dBFS, leaving headroom.
Encoding used FFmpeg with metadata removed: `-map_metadata -1 -vn`, followed by
`-c:a libopus -b:a 72k -vbr on -ar 48000` for Android, or
`-c:a aac -b:a 128k -ar 48000 -movflags +faststart` for iOS.
Both compressed files were decoded/probed locally; duration is preserved.

## Validation

- Shared host tests: 363 passed. UI host tests: 104 passed.
- Music selection: all five tests passed, including entry, DM navigation, exit,
  editor priority and permission-screen muting.
- Phone/Thor UI checks cover shared navigation, exit, spoilers, private-message
  access while Boards is disabled, existing conversation selection, touch drawing,
  4:3 geometry, PocketKeyboard, controller preview focus and fixed headers.
- The full phone/emulated-Thor run initially passed 10/11 checks. The remaining
  test used unscaled programmatic scroll distances on a scaled preview; changing
  it to actual touch scrolling fixed the check. The three affected Thor checks
  then passed, including the light/dark gallery and drawing input.
- Landscape tablet: four checks passed, including the complete light/dark gallery,
  side-rail navigation, exit and private messages.
- Android release/debug builds and shared/UI iOS metadata compilation passed.
  Native iOS packaging and device playback require a Mac and were not performed.
- A broader Android host run passed 171/174 tests. Three unchanged
  `WidgetBindingStoreTest` cases failed when reopening persisted widget bindings;
  this is outside the Boards/audio changes. The failure log is retained.

Logs and screenshots are under `captures/boards-shell-*` (ignored by Git).
Visual tests use fixtures; no posts, invitations or moderation actions were
published to production for testing.

## Follow-up polish

Invite rows display friends' actual Mii avatars, using the existing avatar loader.
The mirrored header chevron has an optical centering correction. Board disclosures
animate their height, fade their contents and rotate their chevron on both opening
and closing. Platform reduced-motion preferences disable these transitions.

## Boards motion follow-up

- Popular's period filters share a container with the primary filters. Their
  spacing expands with the controls, preventing the extra row from jumping at
  the start or end of its transition.
- The Boards shell no longer receives the old Messages bounce in addition to
  its own page entrance. The message preview uses a calm page entrance and no
  longer floats continuously.
- Background tiles drift diagonally on a seamless 36-second loop. Animation
  state is read during drawing, avoiding per-frame recomposition of the board
  contents. Reduced-motion preferences leave the pattern still.
- Note options use a centered three-dot control. Mark/Unmark spoiler expands
  horizontally beside Yeah and Replies; other actions share a compact animated
  row. Closing options also removes their controller targets during the exit.

For this follow-up, build and install only; visual testing is left to the user
on the Thor as requested. No release publication or backend deployment.

## Controller navigation and entrances

- Remove note now expands beside Yeah, Replies and the spoiler action.
- The larger About this board control has an icon and animated chevron. Explicit
  controller paths connect it to the feed filters, hub navigation and expanded
  rules, avoiding the geometric focus search skipping over it.
- Settings disclosure targets include their full padding and border. Switch
  focus targets follow the switch outline instead of surrounding the label.
- L/R cycles Boards, Messages and Activity, including from their subpages.
  Keyboard and modal input take precedence; the main app tabs stay outside this
  cycle. Held shoulder buttons do not repeat navigation.
- Messages reveals the first conversation immediately, then expands subsequent
  rows in a short stagger from top to bottom. Stable conversation keys prevent
  ordinary refreshes from replaying the entrance. Boards uses a centered zoom
  and fade, while Activity fades in without directional movement.

## Contextual top screen

- Highlighting a conversation now shows its Mii (or group collage), status and
  three recent messages. Preview reads do not mark the conversation as read or
  send typing events. Selection changes cancel pending reads; account changes and
  removal from a conversation clear its preview. Existing realtime updates feed
  the same message observer.
- The directory shows the highlighted board's artwork, description and latest
  note, using the normal authenticated feed API. Spoilers remain hidden in this
  preview. Routine refreshes retain both notes and empty states while reloading;
  access denial clears the preview and its artwork.
- Thread notes and replies have a Pin on top control on dual-screen devices and
  tablet layouts with a preview pane. A pin is an ID into the currently validated
  thread data, so it updates after edits/removal and cannot keep a detached copy
  of content after access is revoked.
- The drawing companion keeps the full paper visible and outlines the lower
  screen's zoomed viewport, alongside the current pen, ink, size and zoom. The
  overlay is temporary UI and never becomes part of a saved or published note.
  Zooming out also clamps panning to the new paper bounds.
- Search has explicit controller links: Search button → Search boards field →
  Explore/Joined tabs → Boards navigation. Left/Right switches between the two
  directory tabs.

This follow-up is built and installed for user review only. Visual and device
interaction checks remain with the user; release publication and deployment are
not part of this change.

## Reply context, spacing and focus — 21 September 2026

- Reply references show the original author's name, time and a short excerpt or
  drawing thumbnail, using only the currently accessible thread content. Hidden
  spoilers, removed notes and unavailable targets get placeholders.
- The inline spoiler/removal controls now share one animated row with consistent
  gaps, including the leading gap after Replies.
- Changing a controller target's directional links updates its registration in
  place. Switching hub tabs no longer unregisters the selected tab or clears its
  highlighter.
- Spacing below the fixed Boards header/navigation is inside the scrolling
  content. The initial layout retains its spacing, but scrolled notes reach the
  navigation edge instead of being clipped below a blank strip.

Build and Thor installation only; visual review remains with the user.

## Popular periods and additional music gain — 21 September 2026

- Activating Popular focuses This Week after the period targets are laid out.
  Focus movement does not change the chosen period until it is activated.
- Today, This Week and All Time have explicit horizontal links, including held
  input and boundaries, so the expanding layout cannot divert focus into the sort
  row. Up returns to Popular; Down from Popular enters This Week.
- Boards music has another 20% gain on top of the previous 30%: 1.3 × 1.2 = 1.56.
  Both platform assets were encoded afresh from the original WAV using the
  existing compressed formats; audio settings and other tracks are unchanged.
- Android release build and all 105 UI host tests passed. The navigation
  regression covers expanding and settled row positions and repeated key input.
  Audio decoding/probing passed with headroom in both compressed assets.
- The review APK was installed on the connected Thor without UI interaction or
  screenshots. Version remains 0.1.10-alpha (24); no app release was published.
  APK SHA-256: `dbf08a57092d04c3915e458ed88d1af44fc3159be299d066c48257c0a0d75b31`.
- With explicit approval, the developer docs update was deployed and verified
  against the served page. Only the two requested central-staff passages were
  removed; API enforcement is unchanged. Backup:
  `/opt/pocketpass/deploy-backups/docs-copy-20260921-jngJmU/docs.html`.
  Docs SHA-256: `8ad23671a367ff1dec7b9d519f4cab5f839aa6bdd6999b283af8af99b9a9144f`.

Build log: `captures/boards-period-volume-build.log`. Visual review remains with
the user.
