# PocketPass Mii renderer

PocketPass uses the actual `ariankordi/mii-creator` renderer stack, not the
unrelated Android port. The Android editor UI remains native Compose; this
bundle is the local-only FFL.js/Three.js rendering and `.miic` serialization
surface.

## Pinned input

- Repository: <https://github.com/ariankordi/mii-creator>
- Upstream branch: `renderer-ffl.js-prototype`
- Upstream commit: `1cd6b7d1d09e75fffd5c116a10e3e162647ecb78`
- Bundler: Bun `1.3.14`
- Reproducible bundle SHA-256:
  `f9935943358f0bc34bfcec2f678bd3bf4fa74aa057dbd1e15ddc705b225cec18`

`provenance.json` is the machine-readable source of truth for the pinned
commit, PocketPass source hashes, upstream runtime-asset hashes, build command,
and FFL resource provenance. `pocketPassRuntimeAssets` lists packaged runtime files that PocketPass
derives from a pinned upstream asset (the hat bundle with `hat_10.glb` appended):
the script verifies the upstream base inside the checkout and the packaged
file's own hash, and `-UpdateBundle` never overwrites them.

## What is vendored

- `src/`: the small PocketPass renderer entrypoint and adapters.
- `patches/pocketpass-renderer.patch`: the two narrowly scoped upstream source
  changes needed to detach the renderer from the upstream editor/audio/global
  settings, keep PocketPass's fixed full-body camera from being reset to the
  upstream head view, preserve PocketPass's virtual full-screen viewport
  through renderer rebuilds, and enable deterministic profile portraits using
  the upstream dedicated `ViewType.Face` icon renderer.
- `build.ps1`: a clean-checkout build and checksum verifier.

The complete upstream source tree is deliberately not copied into PocketPass.
The script checks out the exact commit, applies the reviewed patch, installs
the pinned lockfile, and builds the local entrypoint. The runtime models,
textures, wasm, and Three.js file in the APK are verified against the same
checkout.

## Rebuild and verify

Install Git and exactly Bun `1.3.14`, then run from the PocketPass root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\mii-renderer\build.ps1
```

That performs a clean-room build and verifies the existing APK assets without
changing them. To replace the generated bundle and re-sync the pinned upstream
runtime assets after a reviewed source change:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\mii-renderer\build.ps1 -UpdateBundle
```

For an already available upstream clone, add:

```powershell
-UpstreamSource C:\path\to\mii-creator
```

The script clones that repository into an isolated temporary checkout, so it
does not modify the supplied clone. It refuses to use a different commit, Bun
version, source hash, asset hash, or bundle hash.

The FFL resource is intentionally not downloaded by the script. It validates
the already secured, packaged resource against the archive and extracted-file
hashes in `provenance.json`.

## Updating the pin

An upstream update is a reviewed dependency update, not an automatic pull:

1. Inspect the new upstream commit and its licensing/asset changes.
2. Update the patch and PocketPass adapter sources.
3. Build from a clean checkout with the intended Bun version.
4. Review the output and update `provenance.json` hashes intentionally.
5. Run `build.ps1 -UpdateBundle`, then run it once more without flags to prove
   the result is reproducible.

Do not auto-update the upstream branch or runtime assets.

## Attribution and license status

See `THIRD_PARTY_NOTICES.md`. The pinned `mii-creator` commit contains no
repository-level license file. PocketPass's project owner confirmed that
permission for this integration and its required assets was secured; that
permission must be retained with the project records. The absence of a public
license is recorded explicitly and is not treated as an open-source license.
