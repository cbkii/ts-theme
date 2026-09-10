# Standalone TS18 launcher

## Status

The `launcher/` module is a staged Android 10/API 29 HOME successor path that can be installed and tested as an ordinary Activity before HOME is changed. DoFun remains installed and enabled as recovery during qualification.

Physical TS18 evidence collected on 10 September 2026 currently establishes:

- Topway/SystemUI top/right geometry remained visible and usable;
- the launcher/app drawer/quick-launch/navigation hand-off surfaces worked;
- `com.tw.media` on this exact unit is Auxio-TS, not the stock Topway music application;
- Auxio-TS and Spotify worked through the generic Android MediaSession path;
- third-party NavRadio+ (`com.navimods.radio`) exposed a usable independent MediaSession in the tested state;
- the original hand-written WebView raster-map shell received GPS and accepted some gestures but its OSM tile images did not render;
- stock TS18/Topway music and radio applications have not yet been qualified and must be tested independently from Auxio-TS/NavRadio+.

Static/CI qualification is never represented as physical TS18 proof.

## Build and runtime envelope

Requirements match CI: JDK 17, Gradle 9.5, Python 3, Android platform 29 and Build Tools 36.

`launcher:preBuild` fetches the pinned Leaflet 1.9.4 deployment assets from one of two HTTPS distribution endpoints and verifies exact SHA-256 values before Android packaging. Leaflet is bundled into the APK; HOME does not fetch mapping-library code at runtime.

```bash
gradle :launcher:lintDebug :launcher:testDebugUnitTest :launcher:assembleDebug
```

Release builds require the existing repository signing inputs:

```bash
gradle -PVERSION_NAME=0.1.0 -PVERSION_CODE=1000 :launcher:assembleRelease
python3 tools/launcher_apk_check.py launcher/build/outputs/apk/release/launcher-release.apk
```

The release envelope remains deliberately small: one DEX, no native libraries, no Kotlin/AndroidX/RePlugin runtime, no added Android runtime dependency, and a 2.5 MB APK ceiling. The APK check also requires the local Leaflet JS/CSS and bundled BSD-2-Clause licence.

## Testing-build workflow

Successful same-repository PR validation feeds the fixed draft release **000 Testing Only Version**. The installable asset is `TS18-Standalone-Launcher-TESTING.apk`; the exact CI debug build is also retained separately. The privileged publisher consumes only validated artifacts and does not execute PR source.

The manual **Refresh Testing APK Draft** action accepts a PR number, branch, tag or commit SHA. Commits predating the standalone launcher naturally cannot produce its APK.

## HOME dashboard

The current physical-feedback geometry uses a 96 px left rail and 72 px radio/music/date strip while preserving the already-verified Topway/SystemUI safe boundary.

The rail contains four configurable quick-launch slots plus **Apps** and **Settings**. Unset slots fall back to Navigation, Radio, Music and Bluetooth roles respectively. Long-pressing a quick slot opens its picker. **Apps** opens a five-column in-HOME drawer over the map only; while fully covered, map GPS/WebView work is suspended and resumes when the drawer closes.

## Home-screen map architecture

The original custom 5 x 5 tile renderer is retired after the physical TS18 test showed a blank tile surface and incomplete gesture behaviour.

The replacement keeps WebView because that renderer is already present and functioning on the unit, but delegates mature map interaction to bundled **Leaflet 1.9.4**. This provides pinch zoom, double-tap zoom, inertial drag, smooth recenter/follow behaviour, location accuracy circle and a bearing indicator without adding a native vector-map engine or Android runtime library.

Tile networking is not delegated to opaque WebView subresource networking. `TileBroker` intercepts only exact `https://tile.openstreetmap.org/{z}/{x}/{y}.png` requests and performs them with framework `HttpURLConnection` using:

- a TS18 Launcher identifying User-Agent;
- bounded connect/read timeouts;
- ordinary platform TLS validation with no bypass;
- HTTP `max-age` / `Expires` handling;
- ETag / Last-Modified conditional revalidation;
- at least seven-day fallback cache lifetime where the server gives no usable freshness lifetime;
- stale cached-tile fallback when a refresh fails;
- a bounded 64 MiB on-disk tile cache;
- no bulk download or prefetch.

The map library itself is entirely local. WebView remains origin-restricted and no JavaScript-to-Java bridge is exposed. Full route planning/navigation remains owned by the configured navigation application rather than duplicated inside HOME.

`OPEN NAV` sends the latest available location through the provider adapter for Google Maps, Waze, Organic Maps or OsmAnd/OsmAnd+, then falls back to the application's ordinary launch surface. These external navigation applications are not embedded or impersonated.

### Map performance intent

The Leaflet pivot intentionally remains raster-based. It adds roughly 160 KB of uncompressed JavaScript/CSS source before APK compression and reuses the WebView renderer already required by the launcher. It avoids MapLibre/native vector-engine libraries, background routing, vector style parsing and native/GPU renderer lifecycle inside HOME.

Actual CPU/RAM/frame impact on the TS18 must still be measured physically. If the richer map materially regresses the unit, the owning map surface should be reduced rather than compensating elsewhere in the launcher.

## Generic music authority

Generic music uses one existing Android MediaSession selected by the notification-listener service. The launcher creates no player, queue, MediaSession or audio-focus owner.

Selection modes:

- **Auto** follows Android active-session priority while excluding the resolved radio package and telecom/call sessions.
- **Prefer music app** uses the explicitly configured Music package whenever that package has an active session, then falls back to Auto.

Previous/play-pause/next are dispatched once to that selected controller only when its PlaybackState advertises support. A visible-only one-second reconciliation fallback exists because the physical TS18 test showed that callback delivery alone did not reliably refresh Auxio-TS metadata for every track change. The fallback stops when the launcher is no longer visible.

### Exact package clarification

On the tested TS18, `com.tw.media` is **Auxio-TS**. Its Topway-looking package name must not be interpreted as evidence for the stock Topway music app.

The code currently knows `com.tw.music` only as an installed-package fallback candidate. Its actual stock-app runtime identity, MediaSession behaviour and control semantics still require exact-device testing. Do not infer those semantics from Auxio-TS.

## Radio authority

Radio remains independent from generic music.

Third-party NavRadio+ uses package `com.navimods.radio`. Physical testing showed a usable MediaSession in the captured state, so previous/play-pause/next may be controlled through that exact session when the actions are advertised. Unsupported controls stay disabled; only tapping the radio title intentionally opens the full radio app.

This does **not** establish the stock Topway radio contract. The native TS18/Topway radio application must be selected/tested separately. If it exposes no usable Android MediaSession, that result is a boundary for targeted Topway integration research; it is not justification to copy NavRadio+, Auxio-TS or another donor's command path.

No guessed stock-radio package, private broadcast, root key injection or MCU command is added at this stage.

## Gate B - ordinary Activity physical qualification

Keep DoFun as HOME. Test TS18 Launcher directly.

### B1 - geometry, map and app surfaces

Already passed on the previous build except for the map. Re-test the changed surfaces only:

1. confirm the 96 px rail / 72 px strip remain inside the proven SystemUI bounds and the larger controls give visible press feedback;
2. confirm Leaflet map tiles actually render;
3. confirm GPS position, accuracy circle and bearing indicator where bearing is available;
4. confirm drag, inertial pan, pinch zoom, double-tap zoom, +/- and recenter/follow;
5. confirm opening/closing the app drawer still cleanly suspends/resumes the map;
6. confirm navigation hand-off remains correct.

A blank map is a Gate B blocker. Preserve the visible map-status text and a fresh diagnostic ZIP before changing another map variable.

### B2 - generic players

Previously demonstrated with Auxio-TS (`com.tw.media`) and Spotify. Re-test metadata refresh after the visible-only reconciliation change:

1. change tracks using launcher controls;
2. change tracks inside Auxio-TS;
3. change tracks from any other working system/media control surface;
4. confirm HOME metadata changes without leaving/re-entering the launcher;
5. confirm exactly one selected `[music]` authority receives each command.

### B3 - stock Topway music

This remains **unverified** and is separate from B2.

1. select the native TS18/Topway music application explicitly in launcher Settings;
2. start playback in that native app;
3. record the selected package and **Media session diagnostics**;
4. confirm whether it exposes a session, metadata and previous/play-pause/next actions;
5. exercise only actions that are actually advertised;
6. if no usable session is exposed, mark native-music control as BLOCKED at the Android MediaSession boundary and capture evidence before proposing a Topway-specific adapter.

### B4 - third-party NavRadio+

The prior capture establishes a usable `com.navimods.radio` MediaSession in that tested state. Re-test only the changed control presentation if desired: previous/play-pause/next should not launch the app; tapping the radio title should.

### B5 - stock Topway radio

This remains **unverified** and is not represented by the NavRadio+ result.

1. select the native TS18/Topway radio application explicitly;
2. tune/play radio using the native app;
3. record the exact package and **Media session diagnostics**;
4. establish whether it exposes metadata and transport actions;
5. exercise only proven actions;
6. if the Android session path is absent/inadequate, capture `dumpsys media_session` and package state and stop there for targeted Topway contract analysis.

## Read-only evidence capture

After a settled state or immediately after reproducing a discrepancy:

```bash
bash scripts/termux/measure-standalone-launcher.sh
```

The capture covers package/process state, memory, frame timing, CPU, WebView/location state and bounded MediaSession evidence. Failed/timed-out capture components are warnings, not successful observations.

## Gate C - HOME and lifecycle

Only after the relevant Gate B checks pass:

1. preserve and verify DoFun as rollback HOME;
2. enable/select TS18 Launcher as HOME;
3. test HOME key, app launch/return and launcher process restart;
4. test Bluetooth/projection transitions actually used on the unit;
5. reboot Android and confirm HOME/recovery behaviour;
6. cold boot;
7. applicable ACC sleep/wake cycles;
8. rerun read-only measurement after a settled HOME session.

**Reverse-camera hand-off/return remains a roadmapped physical vehicle-lifecycle validation item only. There is no reverse-camera implementation in the launcher at this stage, and none should be added unless later evidence shows the launcher itself owns a regression at that boundary.**

## Rollback

Use launcher Settings -> **Disable HOME candidate / keep app installed**, then select DoFun in Android HOME settings if required.

The Termux installer can restore its saved pre-change HOME:

```bash
bash scripts/termux/install-standalone-launcher.sh --rollback-home
```

Rollback must verify the saved HOME is restored and the standalone HOME alias is disabled. Do not uninstall/disable DoFun during qualification.
