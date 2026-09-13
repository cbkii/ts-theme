# TS18 Dashboard

Android dashboard development for CB's Topway TS18 (Android 10/API 29, 1280 x 720).

The repository has two deliberately separate runtime lanes:

- **`launcher/` - standalone TS18 HOME candidate (primary direction).** An ordinary independently signed Android launcher that owns the dashboard directly and removes DoFun/RePlugin from the normal runtime path.
- **`theme/` - legacy DoFun/RePlugin theme lane.** Retained as rollback/reference while standalone HOME is physically qualified.

DoFun remains installed and enabled during launcher qualification.

## Project posture: local, offline and private by default

**Local, offline and private by default** is the repository-wide position for `ts-theme`.

- **Network availability is optional.** Network access is not an assumed prerequisite for HOME operation.
- Prefer on-device state, local media libraries and offline-capable navigation applications where practical.
- There is **no remote analytics** by default. Do not add tracking, remote configuration, cloud logging or user-behaviour telemetry by default.
- Diagnostics are explicit, bounded and local/exportable; the user decides whether to share them.
- Remote services may be used only when a feature explicitly requires them and the dependency is visible, bounded and justified.

Recommended offline-capable ecosystem choices include **Organic Maps** and **OsmAnd/OsmAnd+** for navigation, and **Auxio-TS, Auxio and VLC** for local media. These are recommendations, not launcher dependencies.

## Standalone launcher

Package: `com.cbkii.ts18launcher`.

The launcher stays deliberately small: platform Android Views/Java, API 29, no Compose/AppCompat/Material runtime/Room/DataStore/Rx/DI, no launcher-owned player/queue/MediaSession/audio focus, no native libraries, and a one-DEX release envelope enforced by CI. A notification-listener service observes existing Android media sessions; bounded media bootstrap uses only standard MediaSession/MediaBrowser surfaces plus a last-resort ordinary app launch.

### TS18 Mono Drive HOME

Exact TS18 testing preserves the physical Topway/SystemUI authority: 1280 x 720 panel, 55 px top boundary, safe-right x=1225 and safe-bottom y=702. HOME uses a **96 px side rail** and **88 px `[Radio controls] [shared active metadata] [Music controls] [DD MMM]` strip**. Physical SystemUI boundaries remain raw pixels; inner UI uses semantic Android dimensions where appropriate.

The rail can be Driver side, Left or Right; on this exact Australian RHD unit Driver side means right. Radio/Music control groups can be swapped independently with `Radio / Music sides`.

The rail order is fixed as **Apps at the top, optional 3-6 configurable quick slots in the middle, and Navigation at the bottom**. Quick 1 defaults to the **Settings** role. The HOME-shortcuts section can be disabled entirely from Settings; when disabled the middle quick slots disappear and their count/editor settings are hidden. When enabled, the visible middle slots divide the available middle rail height evenly rather than being packed as though all six were always present. Navigation remains fixed at the bottom.

The fixed Navigation button uses the selected accent hue. The date is display-only, transparent, non-clickable and non-focusable. Its compact 128 px allocation leaves more width for shared metadata.

### Shared media presentation

Radio and Music remain separate playback authorities but share one metadata area. The selected/last-explicit source has the strongest visual emphasis:

- selected source group uses a restrained charcoal-to-accent gradient pointing inward toward metadata;
- selected Play/Pause uses the filled semantic accent treatment;
- the inactive source remains fully actionable and retains a subtle accent gradient/ring rather than appearing disabled;
- source icons also distinguish selected versus inactive state without removing the inactive source's affordance.

The shared metadata surface uses a 22sp slow-marquee primary title/station plus static 16sp secondary artist/program/source. The last explicitly selected source resolves simultaneous stale `PLAYING` reports.

The Radio source icon always opens configured/default Radio. The Music source icon always opens configured/default Music. Previous / Play-Pause / Next remain usable at all times. A press uses the bounded public-API sequence: exact package MediaSession -> exported MediaBrowserService -> one ordinary source-app launch and bounded exact-session retry (~4.5 s maximum) -> short visible failure status. The launcher creates no MediaSession and never owns audio focus.

A **Warm media sources on HOME start** switch optionally pre-connects exported MediaBrowser services without intentionally opening their Activity UI. Starting non-playing Music sends one best-effort Pause to genuinely playing Radio first; starting Radio similarly pauses Music.

On this exact unit **`com.tw.media` is Auxio-TS**, not native Topway music. Third-party **NavRadio+ is `com.navimods.radio`**. Native Topway music/radio remain separately unverified; no private Topway command is guessed.

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
- `scripts/termux/collect-window-media-evidence.sh` - bounded task/window/freeform/PiP, package/service, MediaBrowser/MediaSession, display-density and relevant-log capture.

These diagnostics do not mutate protected state. Permission/time-out/root gaps are BLOCKED/UNKNOWN rather than evidence of absence.

## UX and physical qualification

[Mono Drive UX acceptance](docs/MONO_DRIVE_UX_ACCEPTANCE.md) defines quantitative contrast/touch/motion gates, the 1280x720 API29 emulator matrix, and physical TS18 measures.

Before any further typography, icon-size or touch-target redesign, capture and validate the actual device's `wm size`, `wm density`, `densityDpi` and relevant display metrics. Do not convert proven SystemUI boundaries away from raw pixels merely for stylistic consistency.

Physical validation is still required for the selected/inactive media hierarchy and gradients, 128 px date, adaptive rail spacing and shortcut-disable state, actual app-icon Settings preview, colour-circle accent selector, media bootstrap/warm-up, appearance modes and exact sidebar/decor-fitted geometry.

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
