# TS18 Dashboard

Android dashboard development for CB's Topway TS18 (Android 10/API 29, 1280 x 720).

The repository has two deliberately separate runtime lanes:

- **`launcher/` - standalone TS18 HOME candidate (primary direction).** An ordinary independently signed Android launcher that owns the dashboard directly and removes DoFun/RePlugin from the normal runtime path.
- **`theme/` - legacy DoFun/RePlugin theme lane.** Retained as rollback/reference while standalone HOME is physically qualified.

DoFun remains installed and enabled during launcher qualification.

## Standalone launcher

Package: `com.cbkii.ts18launcher`.

The launcher stays deliberately small: platform Android Views/Java, API 29, no Compose/AppCompat/Material/Room/DataStore/Rx/DI, no launcher-owned player/queue/MediaSession/audio focus, no native libraries, and a one-DEX release envelope enforced by CI. A notification-listener service observes/controls existing Android media sessions only.

### Dashboard

Physical TS18 testing preserved the 55 px top/right Topway/SystemUI boundaries but showed the original controls were too small. The current layout therefore uses a **96 px left rail** and **72 px Radio | Music | DD MMM strip**, with visible press feedback. All launcher content remains at or left of x=1225 and SystemUI is not hidden.

The rail provides four configurable quick-launch slots plus Apps and Settings. Unset slots fall back to Navigation, Radio, Music and Bluetooth. Apps opens an in-HOME drawer over the map surface.

### Map

The first hand-written raster renderer was retired after physical testing showed blank OSM tiles and incomplete gesture behaviour despite working GPS and WebView script execution.

The current map keeps the already-present WebView but uses pinned **Leaflet 1.9.4** for mature touch interaction. Leaflet JS/CSS are SHA-256-verified at build time and bundled into the APK; no mapping library is downloaded by HOME at runtime.

Map features include pinch and double-tap zoom, inertial panning, recenter/follow, GPS marker, accuracy circle, optional bearing indication and the existing full-navigation hand-off. Raster tiles remain OpenStreetMap. Exact tile requests are intercepted by a small framework-only `TileBroker` using `HttpURLConnection`, an identifying User-Agent, bounded timeouts, HTTP freshness/conditional revalidation, stale-cache fallback and a bounded 64 MiB cache. No bulk prefetch, JavaScript bridge, arbitrary browsing or native vector engine is added.

`OPEN NAV` hands off to the configured Google Maps, Waze, Organic Maps or OsmAnd/OsmAnd+ provider through public intents. Full routing/navigation remains owned by those applications.

### Media and radio

Generic music uses `MediaSessionManager` through user-granted notification-listener access. Auto follows Android active-session priority while excluding the resolved radio/telecom sessions; Prefer music app selects the configured package while it has a session. Capability-aware previous/play-pause/next actions are sent exactly once to one controller. A visible-only one-second reconciliation fallback addresses the physical TS18 observation that metadata callbacks were not always delivered for every track change.

On this exact unit **`com.tw.media` is Auxio-TS**, not the native Topway music app. Auxio-TS and Spotify have been demonstrated through the generic MediaSession path.

Third-party **NavRadio+ is `com.navimods.radio`** and exposed a usable separate MediaSession during testing. This is not evidence for native Topway radio. Stock Topway music and radio remain separately unverified; their exact package/session contracts must be established on-device before adding any Topway-specific adapter. No guessed private broadcasts, MCU controls or root key injection are used.

## Safe HOME rollout

The APK installs as an ordinary app first; its HOME alias is disabled by default. Configure and physically qualify the launcher while DoFun remains HOME, then explicitly enable/select the standalone HOME only after Gate B passes. DoFun remains the rollback launcher through reboot/cold-boot/ACC qualification.

Reverse-camera hand-off/return is a **roadmapped physical lifecycle validation item only**. The launcher contains no reverse-camera implementation at this stage.

See [Standalone launcher](docs/STANDALONE_LAUNCHER.md) for the current Gate B/C playbook.

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

`launcher:preBuild` fetches and verifies pinned Leaflet 1.9.4 deployment assets. CI also requires an empty launcher `releaseRuntimeClasspath`, builds the signed/minified release, verifies one DEX/no native/Kotlin/AndroidX/RePlugin payload and validates the APK signature.

## Testing and candidate releases

Successful same-repository PR `Validate` runs refresh the fixed draft **000 Testing Only Version**. Use `TS18-Standalone-Launcher-TESTING.apk` for upgrade-compatible physical testing. **Refresh Testing APK Draft** can also build a PR number, branch, tag or commit SHA manually.

The separate **Standalone Launcher Candidate** workflow produces an explicitly versioned qualified bundle and may optionally publish a prerelease. The legacy **Manual Release** workflow remains separate.

## Physical validation boundary

CI does not prove head-unit behaviour. Current physical evidence passes SystemUI geometry, quick-launch/drawer/navigation hand-off, Auxio-TS/Spotify generic media and NavRadio+ MediaSession behaviour. The Leaflet map pivot, native Topway music/radio, HOME selection/recovery, Bluetooth/projection, reboot/cold boot and ACC sleep/wake still require exact-device validation. Reverse-camera hand-off/return remains roadmapped for later lifecycle validation, not current launcher functionality.

Project-authored code/docs/assets are Apache-2.0. Bundled Leaflet retains its BSD-2-Clause licence. Vendor APKs, private signing material and device data are not committed.
