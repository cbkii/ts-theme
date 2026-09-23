# ts-theme

`ts-theme` is a standalone Android 10/API 29 launcher/theme implementation for the exact CB TS18 environment. It is intentionally lightweight: framework Java/Views, one DEX, no native runtime payload, no Kotlin/AndroidX runtime and no launcher-owned media session/audio focus.

The project preserves DoFun as the recoverable HOME authority while the standalone launcher is qualified. Protected Topway/SystemUI/vehicle services are not replaced or guessed.

## Standalone launcher

The standalone launcher provides a fixed landscape automotive HOME built around the observed TS18 geometry and safe-area contracts. It includes:

- fixed Apps and Navigation rail endpoints with 3–6 optional quick shortcuts;
- separate Radio and Music transport groups around one shared now-playing metadata surface;
- local app drawer/search and configurable quick-access slots;
- appearance modes and semantic accent hue;
- optional experimental Leaflet/WebView map comparator, off by default;
- bounded read-only diagnostics and explicit HOME rollback tooling.

A notification-listener service observes existing Android media sessions; bounded media readiness is background-only and uses exact per-source service adapters plus standard MediaSession/MediaBrowser surfaces. No readiness/transport path opens a source Activity.

### Radio / Music interaction

Radio and Music remain separate authorities. Selection is a launcher presentation choice, not proof of which hardware path is audible.

- each source has its own source icon, Previous, Play/Pause and Next controls;
- selected Play/Pause uses the filled semantic accent treatment;
- the inactive source remains fully actionable and retains a subtle accent gradient/ring rather than appearing disabled;
- source icons also distinguish selected versus inactive state without removing the inactive source's affordance.

The shared metadata surface uses a 22sp slow-marquee primary title/station plus static 16sp secondary artist/program/source. Identical one-second session snapshots do not restart the marquee hold; bound MediaBrowser controllers are observed by their real MediaSession token, and empty metadata fields fall through to valid display-title/subtitle fields. The last explicitly selected source resolves simultaneous stale `PLAYING` reports.

The Radio source icon always opens configured/default Radio. The Music source icon always opens configured/default Music. Those explicit source/app icons are the only media-strip controls allowed to foreground the source apps; the shared metadata surface is display-only. Previous / Play-Pause / Next remain usable at all times. A press uses the bounded background sequence: exact package MediaSession -> evidence-backed source adapter -> exported MediaBrowser/session service -> bounded session discovery -> directional playback acknowledgement. A Play/Pause intent is resolved once at tap time and retained through preparation; duplicate in-flight Play/Pause requests are coalesced. There is no Activity-launch fallback. The launcher creates no MediaSession and never owns audio focus.

A **Warm media sources on HOME start** switch runs idempotent Media Ready reconciliation on HOME start/resume/focus. Auxio-TS may be passively prepared through its exact exported MediaBrowser wrapper with bounded Magisk-root priming and ordinary binding fallback. Generic sources may use an exported standard MediaBrowser service when present. NavRadio+ is deliberately **interactive-Play preparation only** until the installed API-29 build proves that starting its service while idle does not activate radio or switch routing. Preparing either source does not pause the opposite source; the opposite source is paused only after the newly requested source has acknowledged Play.

On this exact unit **`com.tw.media` is Auxio-TS**, not native Topway music. NavRadio+ `com.navimods.radio` is adapted through its exported Media3 `RadioService` only when that component resolves in the installed build. Exact stock **`com.tw.radio`** declares no Android service component, so it remains existing-session/external-route only and is never secretly foregrounded for readiness. Private Topway radio commands remain outside this adapter until their transport/authority is separately qualified. A masked Activity fallback is not enabled: overlay permission, source survival after Activity loss and OEM camera/call/SystemUI ordering are not yet physically qualified. See [background media readiness adapters](docs/MEDIA_BACKGROUND_READINESS.md).

### Shortcut App / Icon model

HOME and drawer quick slots use one card with two clearly labelled halves:

- **App** - choose an installed app, use the semantic role default, or change role;
- **Icon** - `Auto` or a curated visual-only Material Symbols Rounded override.

`Auto` is truthful. An explicit app target previews and displays the installed application's real, untinted icon; a role shortcut uses its semantic monochrome role glyph. Explicit Icon overrides alter appearance only and never launch authority.

All fixed/user-selectable monochrome glyphs use one pinned family: **Google Material Symbols Rounded**, vendored from `google/material-design-icons@40a7a292a79d9394157e1ea24f83d52d5e17c556` under Apache-2.0. Only the curated set is shipped. See [icon sources](docs/ICON_SOURCES.md).

### Appearance and accent hue

Launcher chrome and the experimental map share Auto / Day / High contrast / Dim / Night appearance. Auto can use ambient light or schedule; stale/unavailable sensor evidence falls back to schedule. Generic unset schedule uses 07:00/19:00 anchors with transition/high-glare periods; user anchors may cross midnight.

Accent hue is deliberately simple and independent of appearance mode. Settings presents a **single strip of coloured circles** for Orange, Amber, Lime, Green, Teal, Cyan, Blue, Purple, Pink and Red. Hue affects only the semantic accent role: primary/selected/focus elements, subtle source accents, section accents, fixed Navigation and experimental-map location accent. Neutral black/charcoal/white hierarchy remains unchanged.

Do not add a first-run wizard unless later physical/user testing establishes a concrete discoverability problem; sensible defaults plus the default Settings quick slot are the current onboarding model.

### Apps drawer

Apps opens an in-HOME overlay. The drawer has Settings and Close actions, five configurable quick-access slots, always-visible local search with a prominent voice-search action, and a five-column installed-app grid with real application icons. Drawer shortcuts use the same App/Icon semantics as HOME slots.

### Exact-device responsiveness only

This launcher is not a generic resizable/split-screen HOME. Responsive work is limited to known TS18 landscape states: the physical 1280x720 panel, the decor-fitted surface, and exact right-sidebar shown/hidden geometry when runtime evidence supports it. The known 1225 px safe-right remains the default full-panel authority while the Topway right sidebar is present. Do not generalise to phone/tablet/split-screen breakpoints.

### Experimental HOME map

The Leaflet/WebView implementation remains **only an experimental physical-test comparator and is OFF by default on a clean install**. Existing explicit map preference is preserved on upgrade. No further Leaflet visual expansion belongs in PR #10.

When enabled it retains pinned Leaflet 1.9.4, restricted OSM raster requests through the native TileBroker, bounded cache/revalidation, renderer recovery, process-local state, no arbitrary browsing and no JS bridge. The duplicate map Navigation action is removed; zoom/follow controls remain opposite the side rail and may be hidden together.

The raster cache is not an Organic Maps/OsmAnd/Google/Yandex offline database. A polished `Map unavailable`/retry placeholder may be reconsidered later **only if physical testing proves the experimental WebView approach worth retaining**; otherwise replacement/windowing belongs to the separate navigation architecture work.

## Configuration and diagnostics

Versioned SAF JSON export/import is whitelisted and transactional. It includes app/role assignments, HOME shortcut visibility/count, rail and Radio/Music sides, map settings, media mode, startup media warm-up, accent hue, icon overrides and appearance schedule. It does not export secrets, caches or location history.

Read-only physical evidence helpers:

- `scripts/termux/measure-standalone-launcher.sh` - CPU/RAM/frame measurements;
- `scripts/termux/collect-window-media-evidence.sh` - bounded task/window/freeform/PiP, package/service, MediaBrowser/MediaSession, display-density and relevant-log capture;
- `scripts/termux/collect-fast-media-evidence.sh` - bounded source/user/root-domain/service/session readiness capture plus a fast-media physical qualification playbook under `/storage/emulated/0/Download/ts-theme/`.

These diagnostics do not mutate protected state. Permission/time-out/root gaps are BLOCKED/UNVERIFIED rather than evidence of absence.

## UX and physical qualification

[Mono Drive UX acceptance](docs/MONO_DRIVE_UX_ACCEPTANCE.md) defines quantitative contrast/touch/motion gates, the 1280x720 API29 emulator matrix, and physical TS18 measures.

Before any further typography, icon-size or touch-target redesign, capture and validate the actual device's `wm size`, `wm density`, `densityDpi` and relevant display metrics. Do not convert proven SystemUI boundaries away from raw pixels merely for stylistic consistency.

Physical validation is still required for selected/inactive media hierarchy and gradients, 128 px date, adaptive rail spacing and shortcut-disable state, actual app-icon Settings preview, colour-circle accent selector, installed-source media readiness, appearance modes and exact sidebar/decor-fitted geometry. Fast-media qualification separately covers cold boot, launcher/player restart, repeated HOME returns, root unavailable, USB late/unavailable, opposite-source playback, reboot and ACC sleep/wake; audible onset remains a physical observation rather than a CI claim.

## Safe HOME rollout

Install/test the APK as an ordinary Activity first; its HOME alias is disabled by default. Configure and physically qualify while DoFun remains HOME, then explicitly enable/select standalone HOME only after ordinary-Activity gates pass. DoFun remains rollback HOME through reboot/cold-boot/ACC qualification.

Reverse-camera hand-off/return remains roadmapped physical validation only. There is no reverse-camera implementation in this launcher.

## Development

```bash
python3 tools/ts18_theme.py validate
python3 -m unittest discover -s tests -v

gradle :theme:lintDebug :theme:assembleDebug
gradle :launcher:lintDebug :launcher:testDebugUnitTest :launcher:assembleDebug
```

CI also requires an empty launcher release runtime dependency graph, signed/minified one-DEX/no-native/no-Kotlin/no-AndroidX/no-RePlugin release envelope, pinned Leaflet assets, Material Symbols attribution and valid APK signature. Successful same-repository PR validation refreshes the fixed draft **000 Testing Only Version**.

CI proves source/build/release contracts only. It does not prove physical head-unit behaviour.
