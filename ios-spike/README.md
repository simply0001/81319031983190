# iOS spike — Phase 0

A throwaway probe answering the four questions the iOS plan rests on, before any of the
real migration starts. Standalone Gradle build: the root project does not include it, so
`:app` is untouched.

**Verdict: go on all four.** Nothing found here argues against Kotlin/Compose Multiplatform.

## What it contains

| Path | What it is |
|---|---|
| `probe/src/commonMain` | `DesignSystem.kt` and `PhoneSurface.kt` lifted from the app, plus a demo screen |
| `probe/src/skikoMain` | The AGSL pattern shader ported to Skia (`RuntimeEffect`) |
| `probe/src/iosMain` | WKWebView host for the Mii renderer, and the Compose entry point |
| `probe/src/desktopMain` | Window host + offscreen renderer that writes PNGs at iPhone geometries |
| `iosApp/` | XcodeGen spec and a minimal Swift host |
| `tools/serve-renderer.ts` | Serves the real Mii renderer over the LAN so it can be opened in Safari on an iPhone |

## Running it

```powershell
# render the probe screen at four device geometries -> probe/build/probe-renders/*.png
.\gradlew.bat -p ios-spike :probe:run --args="--render build/probe-renders"

# interactive window at iPhone 15 Pro logical size
.\gradlew.bat -p ios-spike :probe:run

# construct a supabase-kt client and hit api.pocketpass.xyz
.\gradlew.bat -p ios-spike :probe:run --args="--probe-backend"

# type-check commonMain against every target, iOS included
.\gradlew.bat -p ios-spike :probe:compileCommonMainKotlinMetadata

# serve the Mii renderer to an iPhone on the same Wi-Fi
~\.bun\bun-windows-x64\bun.exe run ios-spike\tools\serve-renderer.ts
```

## Proved on Windows, no Mac involved

1. **The design system is already platform-free.** `PhoneSurface.kt` moved across
   verbatim apart from a single call to `WindowInsets.statusBarsIgnoringVisibility`, the
   one Android-only API in it — `safeDrawing`, `systemBars`, `displayCutout`, `ime` and
   `union` are all in CMP common. `DesignSystem.kt`'s scaling and layout logic moved
   verbatim too; what changed there was only its resource plumbing (the font families and
   the `Assets` object, which the probe replaces with `composeResources` accessors) and
   dropping the `@RawRes`/`@DrawableRes` annotations.

2. **`PhoneSurface` computes the right geometry at iPhone dimensions.** iPhone 15 Pro
   portrait resolves to 1240.0 × 2688.2 design units at 0.9508 px/unit — the short side
   lands exactly on the 1240-unit design width, as intended. Landscape correctly
   classifies as `Wide` (2688.2 × 1240.0), so an iPhone gets the rail + stage + deck
   layout the same way a wide Android phone does. Renders for iPhone 15 Pro (both
   orientations), iPhone SE and iPad Pro 11" are in `probe/build/probe-renders/`.

3. **The AGSL shader runs on Skia unmodified.** `PATTERN_SHADER` — uniform arrays
   (`float bandY[16]`), an `int` uniform, a loop, `half4`/`half3` — compiled and rendered
   through `org.jetbrains.skia.RuntimeEffect` with **zero changes to the shader source**.
   `RuntimeShaderBuilder.uniform(name, FloatArray)` exists, which was the open question.

4. **Figma SVGs render through CMP `composeResources`**, including `<defs>` +
   `<linearGradient>` + `url(#…)` fills — exactly what the exported assets use. The 108
   `.asset` files are plain SVG and only need renaming to `.svg`.

5. **Rubik loads** at Normal/Medium/Bold via `Res.font.*`.

6. **supabase-kt 3.6.0** (auth, postgrest, realtime, storage), **Coil 3.3.0** (+svg,
   +ktor) and kotlinx-serialization all resolve and compile for `iosArm64` and
   `iosSimulatorArm64`. The client also constructs and reaches `api.pocketpass.xyz` at
   runtime (`HTTP 401` without an apikey, which is the expected answer).

7. **Kotlin/Native's Windows toolchain installs and commonizes**, so common and iOS
   *type-checking* works with no Mac. Only linking needs macOS.

## Findings that change the plan

- **CMP 1.12.0 does not publish `iosX64`.** Target `iosArm64` + `iosSimulatorArm64` only.
  Harmless — GitHub's `macos-15` runners are Apple Silicon.
- **`androidx.compose.ui.graphics.Shader` is no longer a typealias** for the Skia shader;
  it is a wrapper class. The conversion is `org.jetbrains.skia.Shader.asComposeShader()`.
- **Shader code needs a `skikoMain` source set** spanning desktop + iOS, declared with
  `applyDefaultHierarchyTemplate { common { group("skiko") { withJvm(); withIos() } } }`.
  Android keeps `RuntimeShader`; skiko targets use `RuntimeEffect`. That is the
  `expect`/`actual` seam, and it is the same shape `PatternSurface` uses here.
- **Material3 is a non-issue.** CMP's Material3 is on its own release train with no stable
  build aligned to CMP 1.10–1.12 (latest stable is 1.9.0) — but the app imports only seven
  Material3 symbols in total (27× `Text`, 3× `Icon`, and one each of `Surface`,
  `MaterialTheme`, `LocalTextStyle`, `lightColorScheme`, `darkColorScheme`). The probe
  drops Material3 entirely and uses `BasicText`.
- **Fonts become `@Composable` getters.** CMP's `Font(FontResource, …)` is `@Composable`,
  so `val Rubik = FontFamily(…)` has to become `val Rubik: FontFamily @Composable get() = …`.
  Mechanical, but it touches every font declaration and every call site's context.
- **Gradle 9 rejects the deprecated `compose.*` DSL accessors** as build-script errors.
  Use direct coordinates (`org.jetbrains.compose.foundation:foundation`, …).
- **Coil 3.3.0 pulls skiko 0.9.4.2** while CMP 1.12.0 uses 0.150.1. Gradle resolves
  upward and the Kotlin plugin warns; the migration should constrain this explicitly.
- **No JDK 17 on this machine.** The spike uses JDK 21 (auto-provisioned Adoptium) via
  `jvmToolchain(21)`; worth aligning the main build at some point.

## The Mii renderer — testable on the iPhones right now

Whether the FFL WASM bundle runs is a WebKit question, and mobile Safari is the same
engine as `WKWebView`. `tools/serve-renderer.ts` serves the real
`app/src/main/assets/mii_renderer/` over the LAN with correct MIME types, injects a
`PocketPassNative` shim so the renderer's bridge calls do not throw, and prints an
on-screen log of what the renderer reports.

Verified locally: the shim is injected ahead of the module script, `.wasm` is served as
`application/wasm`, the 4.6 MB `FFLResHigh.dat` is served, `/` redirects to
`index.html?mii=<default>`, and path traversal 404s.

Two things worth knowing, both found while building this:

- **The first render needs no native bridge.** The renderer takes its initial Mii from a
  query parameter (`index.html?mii=<canonicalBase64>`), so opening the URL is enough.
- **The bridge shapes differ.** Android injects a global `PocketPassNative` object via
  `WebViewCompat.addWebMessageListener`; iOS provides
  `window.webkit.messageHandlers.PocketPassNative`. One `WKUserScript` at document start
  reconciles them (already written into `MiiRendererWebView.kt`). The native→JS direction
  is `globalThis.PocketPassMiiRenderer.receiveBase64(…)` on both, so it needs no change.

## Not proved yet

- **`iosMain` has never been compiled.** `MiiRendererWebView.kt` and
  `ProbeViewController.kt` are written against the WebKit/CMP APIs but not verified —
  expect a round or two of fixes on the first macOS run. The `iOS spike` workflow's
  `framework` job is what checks them.
- **`skikoMain` is only compile-verified on the JVM leaf.** There is no metadata
  compilation for a mixed jvm+native source set, so the iOS leaf first compiles on CI.
  Low risk (same skiko version, same API) but not zero.
- **Safe-area insets are all zero in these renders**, because desktop has no system bars.
  Only a real iPhone answers that.
- **The Xcode project has never been generated.** `iosApp/project.yml` is a first draft.

## CI

`.github/workflows/ios-spike.yml`. The Linux job (1× minutes) runs on every push to
`ios-spike/**` and compiles the desktop target, type-checks commonMain against all
targets, and uploads the renders. The two macOS jobs (10× minutes) are
`workflow_dispatch` only, cache `~/.konan`, and build the framework first — no secrets and
no signing — before attempting the simulator app.
