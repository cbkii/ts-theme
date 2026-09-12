# TS18 Dashboard

Android dashboard development for CB's Topway TS18 (Android 10/API 29, 1280 x 720).

The repository has two deliberately separate runtime lanes:

- **`launcher/` - standalone TS18 HOME candidate (primary direction).** An ordinary independently signed Android launcher that owns the dashboard directly and removes DoFun/RePlugin from the normal runtime path.
- **`theme/` - legacy DoFun/RePlugin theme lane.** Retained as rollback/reference while standalone HOME is physically qualified.

DoFun remains installed and enabled during launcher qualification.

## Standalone launcher

Package: `com.cbkii.ts18launcher`.

The launcher stays deliberately small: platform Android Views/Java, API 29, no Compose/AppCompat/Material/Room/DataStore/Rx/DI, no launcher-owned player/queue/MediaSession/audio focus, no native libraries, and a one-DEX release envelope enforced by CI. A notification-listener service observes/controls existing Android media sessions only.

### TS18 Mono Drive HOME

Exact TS18 testing preserves the 55 px top/right Topway/SystemUI boundaries. The redesigned HOME uses a **96 px side rail** and **88 px Radio | Music | DD MMM strip**. The rail can be placed on Driver side, Left or Right; on this exact Australian right-hand-drive unit Driver side means right. The mirror operation keeps content out of Topway's right-side SystemUI.

The rail order is fixed as **Apps at the top, 3-6 configurable monochrome role-icon slots in the middle, and Navigation at the bottom**. Settings remains in the app-drawer header; tapping the date remains an intentional Settings shortcut.

Radio/music use stable Previous | Play/Pause | Next ordering with an accent-primary centre control and larger touch regions. Each card now has a **primary title/station** slow marquee and a **static secondary artist/program/source** line. The transport cluster has its own Left/Right setting independent of rail/Driver-side placement.

Fixed functional UI glyphs use one pinned family: **Google Material Symbols Rounded**, vendored from `google/material-design-icons@40a7a292a79d9394157e1ea24f83d52d5e17c556` under Apache-2.0. There is no runtime icon dependency. See [icon sources](docs/ICON_SOURCES.md). The full app grid continues to show installed-app icons because that grid's purpose is application identity.

The UI uses black/charcoal surfaces, the warm orange accent, semantic dp/sp dimensions for inner controls and redundant pressed/focused/disabled/selected feedback. Motion is deliberately restrained: 120 ms state/icon crossfade, 140 ms input feedback, 160 ms transient-panel reveal and 180 ms drawer transition. Physical SystemUI geometry remains exact raw pixels.

### Appearance

Launcher chrome and map share Auto/Day/High contrast/Dim/Night appearance. Auto can use the ambient-light sensor or a schedule. Unavailable/stale sensor state falls back to schedule. Without a user schedule, the generic bounded fallback uses 07:00/19:00 day/night anchors, dim transition periods around those anchors and a central daytime high-glare window. User-selected anchors replace the defaults and may cross midnight. This is a generic approximation, not astronomical sunrise/sunset or vehicle-illumination authority.

### Apps drawer

Apps opens an in-HOME overlay over the map surface. While covered, map GPS/WebView work is suspended and resumes when the drawer closes. The drawer has Settings and Close actions, a user-configurable five-slot quick-access row, always-visible local search with a prominent voice-search action, and a five-column grid with 72dp installed-app icons and readable labels. Search is local to already-discovered launcher activities; no service/background index is added.

### Map

The map uses pinned **Leaflet 1.9.4** in the existing lifecycle-bound WebView for pinch/double-tap zoom, inertial pan, follow/recentre, GPS marker, accuracy circle and bearing indication. Leaflet is SHA-256 verified at build time and bundled locally.

Raster OpenStreetMap tiles are supplied through the framework-only `TileBroker`, with identifying User-Agent, bounded timeouts, HTTP freshness/conditional revalidation, stale-cache fallback and a bounded 64 MiB cache. No bulk prefetch, JavaScript bridge, arbitrary browsing or native vector engine is added.

Driver-facing map actions are icon-only zoom in, zoom out, follow/recentre and one filled navigation action. Settings can hide the map controls as a group. Follow state is redundant rather than colour-only: selected Follow uses orange tint plus a 3dp accent outline/halo. Healthy map status disappears, while locating/loading/offline/error states remain visible. Map brightness/contrast consumes the same appearance mode as launcher chrome.

Full routing/navigation remains owned by the configured Google Maps, Waze, Organic Maps or OsmAnd/OsmAnd+ app through public hand-off intents. Current Organic Maps location/driving modes have been analysed and are recorded in [Organic Maps future driving-mode integration](docs/ORGANIC_MAPS_MODES.md); no private cross-package mode control is implemented.

### Media and radio

Generic music uses `MediaSessionManager` through user-granted notification-listener access. Auto follows Android active-session priority while excluding the resolved radio/telecom sessions; Prefer music app selects the configured package while it has a session. Capability-aware previous/play-pause/next actions are sent exactly once to one controller. A visible-only one-second reconciliation fallback addresses the physical TS18 observation that metadata callbacks were not always delivered for every track change.

On this exact unit **`com.tw.media` is Auxio-TS**, not the native Topway music app. Auxio-TS and Spotify have been demonstrated through the generic MediaSession path.

Third-party **NavRadio+ is `com.navimods.radio`** and exposed a usable separate MediaSession during testing. This is not evidence for native Topway radio. Stock Topway music and radio remain separately unverified; their exact package/session contracts must be established on-device before adding any Topway-specific adapter. No guessed private broadcasts, MCU controls or root key injection are used.

## UX qualification

[Mono Drive UX acceptance](docs/MONO_DRIVE_UX_ACCEPTANCE.md) is the quantitative acceptance contract. It defines static contrast/touch/motion gates, the 1280 x 720 API29 emulator matrix, and physical TS18 metrics for glance time, action taps, first-response latency, completion time, wrong-target rate and comparative CPU/PSS/frame evidence. These are qualification methods only; there is no parked-only runtime feature restriction.

## Safe HOME rollout

The APK installs as an ordinary app first; its HOME alias is disabled by default. Configure and physically qualify the launcher while DoFun remains HOME, then explicitly enable/select the standalone HOME only after the ordinary-Activity gates pass. DoFun remains the rollback launcher through reboot/cold-boot/ACC qualification.

Reverse-camera hand-off/return is a **roadmapped physical lifecycle validation item only**. The launcher contains no reverse-camera implementation at this stage.

See [Standalone launcher](docs/STANDALONE_LAUNCHER.md) for the current qualification playbook.

## Magisk root policy

This user-owned TS18 is Magisk-rooted. Root may be used for bounded one-time HOME assignment, diagnostics and reversible setup where it materially reduces runtime cost. It is not treated as platform signing, UID 1000, protected SELinux, MCU/CAN or partition-write authority. The launcher does not poll through `su` or disable protected Topway packages.

A bounded Termux installer/rollback helper is provided at `scripts/termux/install-standalone-launcher.sh`; read-only runtime measurement is provided at `scripts/termux/measure-standalone-launcher.sh`.

## Legacy DoFun theme

The legacy theme retains application/plugin identity `launcher.variety.theme.plugin.sfp_cbk_black` / `sfp_cbk_black`, independent signing and the established clean-room RePlugin compatibility work. The standalone launcher does not depend on it or reuse its identity.

## Development

```bash
python3 tools/ts18_theme.py validate
python3 -m unittest discover -s tests -v

gradle :theme:lintDebug :theme:assembleDebug
gradle :launcher:lintDebug :launcher:testDebugUnitTest :launcher:assembleDebug
```

`launcher:preBuild` fetches and verifies pinned Leaflet 1.9.4 deployment assets. CI also requires an empty launcher `releaseRuntimeClasspath`, builds the signed/minified release, verifies one DEX/no native/Kotlin/AndroidX/RePlugin payload, checks the packaged Material Symbols attribution notice and validates the APK signature.

## Testing and candidate releases

Successful same-repository PR `Validate` runs refresh the fixed draft **000 Testing Only Version**. Use `TS18-Standalone-Launcher-TESTING.apk` for upgrade-compatible physical testing. **Refresh Testing APK Draft** can also build a PR number, branch, tag or commit SHA manually.

The separate **Standalone Launcher Candidate** workflow produces an explicitly versioned qualified bundle and may optionally publish a prerelease. The legacy **Manual Release** workflow remains separate.

## Physical validation boundary

CI does not prove head-unit behaviour. Current physical evidence passes the prior SystemUI geometry, quick-launch/drawer/navigation hand-off, Auxio-TS/Spotify generic media and NavRadio+ MediaSession behaviour. The current Mono Drive HOME, two-level media presentation, independent media-control side, Leaflet map, appearance automation, voice search, native Topway music/radio, HOME selection/recovery, Bluetooth/projection, reboot/cold boot and ACC sleep/wake still require exact-device validation. Reverse-camera hand-off/return remains roadmapped for later lifecycle validation, not current launcher functionality.

Project-authored code/docs/assets are Apache-2.0. Vendored Material Symbols Rounded are Apache-2.0 and retain provenance/notice; bundled Leaflet retains its BSD-2-Clause licence. Vendor APKs, private signing material and device data are not committed.
