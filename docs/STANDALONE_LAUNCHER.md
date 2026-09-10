# Standalone TS18 launcher

## Status

The `launcher/` module is the staged successor to the DoFun theme runtime.

It is intentionally installable as an ordinary app before it can become HOME. DoFun remains available as recovery during physical validation.

The current standalone release-completion scope now includes the maps/app-launch batch and the generic-media/radio authority batch. Static/CI qualification is not physical TS18 proof.

## Build

Requirements match the repository CI: JDK 17, Gradle 9.5, Android platform 29 and Build Tools 36.

For ordinary development validation:

```bash
gradle :launcher:lintDebug :launcher:testDebugUnitTest :launcher:assembleDebug
```

The debug APK is useful for compilation/lint/unit testing, but its unminified DEX layout is not treated as release-envelope evidence.

For a release build, provide the existing four signing inputs used by the repository and run:

```bash
gradle -PVERSION_NAME=0.1.0 -PVERSION_CODE=1000 :launcher:assembleRelease
python3 tools/launcher_apk_check.py launcher/build/outputs/apk/release/launcher-release.apk
```

The release-envelope check is fail-closed: the signed/minified candidate must stay within the standalone package-size/runtime contract, contain exactly one DEX, contain no native libraries or Kotlin/AndroidX/RePlugin runtime payload, and retain the required manifest-resolved launcher classes and local map asset. CI also requires an empty `releaseRuntimeClasspath` and verifies the APK signature with Android Build Tools `apksigner`.

## Candidate workflow

Use **Standalone Launcher Candidate** for physical-test APKs. It builds and qualifies the signed APK, then uploads a 14-day artifact containing:

- the exact signed launcher APK;
- `SHA256SUMS.txt` and `BUILD_INFO.txt`;
- the bounded root installer/HOME rollback helper;
- the read-only performance/runtime measurement helper;
- this standalone validation document.

Publishing a GitHub prerelease is explicit and optional. The build job has read-only repository permission; only the separate publisher job receives `contents: write`, and it re-verifies the downloaded qualified bundle before publication. This candidate lane does not replace the existing DoFun-theme Manual Release workflow.

## Dashboard controls

The left HOME rail contains four configurable application slots plus **Apps** and **Settings**.

- Quick 1 falls back to the configured Navigation app when unset.
- Quick 2 falls back to the resolved Radio app when unset.
- Quick 3 falls back to Music, including the existing Topway music fallback when applicable.
- Quick 4 falls back to Bluetooth.
- Long-pressing a quick slot opens its app picker.
- **Apps** opens an in-HOME five-column app drawer over the map surface only. The radio/music/date strip remains visible. Closing the drawer resumes the map; launching an app collapses the drawer before the next HOME resume.

The separate Navigation, Radio, Bluetooth and Music role settings remain authoritative for the map/media surfaces. Quick slots can override those role defaults without changing those authorities.

## Generic media authority

Generic music is controlled only through one existing Android `MediaSession` selected by the notification-listener service. The launcher does not create a player, queue, playback service, MediaSession or audio-focus owner.

Settings exposes two selection modes:

- **Auto**: use Android's active-session priority order and select the highest-priority eligible non-radio/non-telecom session. This is the default.
- **Prefer music app**: if the configured Music app currently exposes an active session, use that exact package first; if it does not, fall back to Auto.

The preferred/fallback music role therefore works with Auxio-TS, Spotify or another ordinary Android media application without hard-coding those applications into the control path.

Previous, play/pause and next are capability-aware. Each button is enabled only when the selected session's `PlaybackState` advertises the corresponding action. A press is dispatched once to that selected controller. Unsupported generic commands are not broadcast to alternate apps.

**Media session diagnostics** in Settings is read-only. It reports active sessions in the order Android returned them, marks the currently selected `[music]` and `[radio]` sessions, and reports state, metadata and previous/play-pause/next capability. It does not poll in the background.

## Radio and NavRadio+

Radio remains a separate authority from generic music.

- An explicitly configured Radio package always wins.
- When no Radio package is configured and `com.navimods.radio` is installed, the launcher may resolve that package as the default NavRadio+ app.
- Auto-detection is package discovery only. It is not proof that NavRadio+ exposes a usable MediaSession on the exact TS18.
- Radio metadata/control is used only when an active MediaSession from the resolved radio package exists.
- Previous/next are dispatched once only when that session advertises the requested action. Otherwise the launcher opens the resolved radio app rather than inventing a broadcast, key event, root command or private Topway path.

Any deeper NavRadio+/Topway contract remains blocked until exact-device evidence demonstrates that the public MediaSession route is insufficient and identifies the owning interface.

## Safe rollout

1. Install the APK without changing HOME.
2. Launch **TS18 Launcher** from the existing launcher/app list.
3. Configure Navigation, Radio, Bluetooth and preferred/fallback Music in Settings.
4. Configure any quick-launch overrides required for the left rail.
5. Grant notification-listener access for generic media.
6. Grant location if the embedded map is required.
7. Complete the ordinary-Activity Gate B tests below.
8. Only after those tests pass, enable/set HOME from Settings. The public Android role/settings path is available.
9. If Magisk root is available, **Set as HOME with Magisk root** performs the one-time HOME shell operation and verifies the result; if it fails it falls back to Android HOME settings.
10. Keep DoFun installed/enabled until restart/reboot/cold-boot/ACC and vehicle-function validation is complete.

The Termux helper can also install and set HOME with a bounded root operation. For this helper, ensure `aapt` or `aapt2`, `apksigner`, `sha256sum`, `timeout` and Magisk `su` are available in Termux first. The helper parses the APK before installation, rejects any application ID other than `com.cbkii.ts18launcher`, verifies the APK signature, and verifies the APK against an adjacent `SHA256SUMS.txt` when a qualified candidate bundle is used.

```bash
bash scripts/termux/install-standalone-launcher.sh /storage/emulated/0/Download/TS18-Standalone-Launcher.apk --set-home
```

With `--set-home`, the helper refuses to change HOME unless it can preserve a safe non-launcher rollback component first. Installation without `--set-home` does not overwrite the saved rollback target.

## Map and navigation handoff

The dashboard map is an in-process WebView backed by project-owned local HTML and OpenStreetMap raster tiles. It is created after the first launcher frame, restricted to the OSM tile origin, uses the WebView cache, identifies its tile requests as TS18 Launcher, and its GPS listeners are active only while the map is visible.

The map supports bounded touch/pointer panning and explicit recentering/zoom controls without adding a background worker. Its 5 x 5 tile layer is rebuilt only when the integer centre tile or zoom changes; ordinary pointer movement, resize and same-tile GPS updates reuse the existing image nodes and update only their transform.

`OPEN NAV` hands the latest GPS location to the configured navigation authority where that app exposes a matching public deep-link intent, then falls back to the app's ordinary launcher Activity. The lightweight provider layer includes the known package identities for Google Maps, Waze, Organic Maps and OsmAnd/OsmAnd+ but does not embed or impersonate those applications. No private navigation-app API, root task embedding or JS-to-Java bridge is used.

## Gate B - ordinary Activity physical qualification

Do not make TS18 Launcher the default HOME for this gate. Keep DoFun as the active/recovery launcher and test the standalone Activity directly.

### B1 - geometry, map and app surfaces

Confirm on the physical 1280 x 720 TS18:

- Topway/SystemUI top/right regions remain visible and usable;
- radio/music/date strip and 81 px rail fit the observed application bounds;
- map renders, receives GPS, pans, recentres and zooms;
- same-tile GPS movement does not cause visible full-tile-layer churn;
- `OPEN NAV` hands off correctly to every installed navigation provider intended for release support;
- all four quick slots launch their configured or role-fallback app exactly once;
- the in-HOME drawer opens/closes over the map, pauses the map while covered, launches an app and returns cleanly.

A failure here blocks HOME qualification but does not imply a media failure.

### B2 - generic media selection and controls

Grant notification access, then use **Media session diagnostics** after each state change.

1. With only the intended Music app active, confirm it is marked `[music]`, metadata is correct and only advertised controls are enabled.
2. Repeat with another generic player such as Spotify. Confirm Auto follows the Android-priority active session rather than a hard-coded package.
3. With two generic players exposing sessions, record the diagnostics order and selected `[music]` authority in Auto.
4. Set the preferred Music app, switch to **Prefer music app**, and confirm that exact package is selected while its session exists.
5. Stop/remove that preferred session and confirm selection falls back to Auto without sending a command to both sessions.
6. Exercise previous, play/pause and next. A supported action must affect only the selected player; an unsupported action must remain disabled.
7. Disconnect/reconnect notification-listener access or restart the launcher process and confirm media observation recovers without creating a second authority.

If a result differs, preserve the diagnostics and `dumpsys media_session` evidence before changing selection logic.

### B3 - radio / NavRadio+

1. Leave Radio explicitly configured if the exact desired package is known. Otherwise, with NavRadio+ installed, confirm Settings reports the resolved `com.navimods.radio` package.
2. Launch/play radio and open **Media session diagnostics**.
3. If `com.navimods.radio` exposes an active session, confirm it is marked `[radio]` and is not also selected as `[music]`.
4. Exercise radio previous/next only where the diagnostics report support. Confirm one physical action per press.
5. Where no matching session/action exists, confirm the dashboard opens NavRadio+ instead of silently sending an alternate control path.
6. If NavRadio+ has no useful MediaSession on this TS18, record that as the earliest causal boundary. Do not add guessed broadcasts/private Topway controls until a targeted exact-device probe identifies a real contract.

### B4 - read-only evidence capture

After the launcher has settled and after reproducing any media/radio discrepancy, run:

```bash
bash scripts/termux/measure-standalone-launcher.sh
```

The capture includes package/process state, memory, frame timing, CPU, WebView/location state and a bounded `dumpsys media_session` surface. Note the corresponding in-app **Media session diagnostics** result in the test record. Failed/timed-out capture components are warnings rather than being misreported as a complete success.

Gate B passes only when the relevant B1-B3 tests have been physically exercised. CI/emulator success does not satisfy it.

## Gate C - HOME and vehicle lifecycle qualification

Only after Gate B passes:

1. preserve/verify the DoFun rollback HOME;
2. enable the standalone HOME alias and select TS18 Launcher;
3. test HOME key, app launch/return and launcher process restart;
4. test reverse camera entry and return;
5. test Bluetooth and projection transitions that are used on this unit;
6. reboot Android and confirm HOME/recovery behaviour;
7. perform a cold boot;
8. perform applicable ACC sleep/wake cycles;
9. rerun the read-only measurement capture after a settled HOME session.

Any failure must be attributed to its owning boundary. A reverse-camera/vehicle lifecycle failure does not justify altering the generic MediaSession selection logic unless direct evidence connects them.

## Rollback

Use launcher Settings -> **Disable HOME candidate / keep app installed**, then select DoFun in Android HOME settings if required.

The Termux installer records the pre-change HOME component in private Termux state and can attempt to restore it:

```bash
bash scripts/termux/install-standalone-launcher.sh --rollback-home
```

Rollback exits non-zero unless the saved HOME is restored, the launcher HOME alias is disabled and the resolved HOME matches the saved component. Do not uninstall or disable DoFun during this phase.

## Release boundary

A release candidate is technically qualified only when its exact source head passes repository validation, empty standalone release-runtime dependency inspection, launcher lint/unit/debug build, signed/minified release build, APK-envelope validation and signature verification.

A fully qualified TS18 release additionally requires the physical Gate B and Gate C results above. Record unrun checks as unverified rather than inferred from CI.
