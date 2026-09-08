# Standalone TS18 launcher

## Status

The `launcher/` module is the staged successor to the DoFun theme runtime.

It is intentionally installable as an ordinary app before it can become HOME. DoFun remains available as recovery during physical validation.

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

## Safe rollout

1. Install the APK without changing HOME.
2. Launch **TS18 Launcher** from the app list.
3. Configure Navigation, Radio, Bluetooth and fallback Music in Settings.
4. Grant notification-listener access for generic media.
5. Grant location if the embedded map is required.
6. Confirm SystemUI remains visible and dashboard geometry is correct.
7. Enable/set HOME from Settings. The public Android role/settings path is available.
8. If Magisk root is available, **Set as HOME with Magisk root** performs the one-time HOME shell operation and verifies the result; if it fails it falls back to Android HOME settings.
9. Keep DoFun installed/enabled until restart/reboot/cold-boot/ACC and vehicle-function validation is complete.

The Termux helper can also install and set HOME with a bounded root operation. For this helper, ensure `aapt` or `aapt2`, `apksigner`, `sha256sum`, `timeout` and Magisk `su` are available in Termux first. The helper parses the APK before installation, rejects any application ID other than `com.cbkii.ts18launcher`, verifies the APK signature, and verifies the APK against an adjacent `SHA256SUMS.txt` when a qualified candidate bundle is used.

```bash
bash scripts/termux/install-standalone-launcher.sh /storage/emulated/0/Download/TS18-Standalone-Launcher.apk --set-home
```

With `--set-home`, the helper refuses to change HOME unless it can preserve a safe non-launcher rollback component first. Installation without `--set-home` does not overwrite the saved rollback target.

## Map

The dashboard map is an in-process WebView backed by project-owned local HTML and OpenStreetMap raster tiles. It is created after the first launcher frame, restricted to the OSM tile origin, uses the WebView cache, identifies its tile requests as TS18 Launcher, and its GPS listeners are active only while the map is visible. The map supports bounded touch/pointer panning and explicit recentering/zoom controls without adding a background worker.

The map is intentionally a lightweight situational display. `OPEN NAV` launches the configured navigation application for full routing.

## Runtime measurement

After the launcher has settled, collect a bounded read-only snapshot rather than guessing about efficiency:

```bash
bash scripts/termux/measure-standalone-launcher.sh
```

The helper records current HOME, package/process state, memory, frame timing, CPU, WebView provider, location/media state and whether DoFun remains available as the recovery launcher. It does not mutate package, HOME, SELinux or vehicle state. Individual failed/timed-out captures are recorded as warnings rather than being misreported as a complete success. A ZIP is only reported as verified when SHA-256 generation succeeds; partial ZIPs are removed before unpacked fallback export.

## Rollback

Use launcher Settings → **Disable HOME candidate / keep app installed**, then select DoFun in Android HOME settings if required.

The Termux installer records the pre-change HOME component in private Termux state and can attempt to restore it:

```bash
bash scripts/termux/install-standalone-launcher.sh --rollback-home
```

Rollback exits non-zero unless the saved HOME is restored, the launcher HOME alias is disabled and the resolved HOME matches the saved component. Do not uninstall or disable DoFun during this phase.

## Physical validation

Record exact results for:

- ordinary app start;
- first HOME selection;
- HOME key;
- app launch/return;
- SystemUI top/right regions;
- map render, pan/recenter and GPS update;
- idle CPU/PSS/frame behaviour;
- generic media;
- radio app/session;
- Bluetooth;
- projection;
- reverse camera and return;
- launcher process restart;
- Android reboot;
- cold boot;
- ACC sleep/wake.

Emulator/CI success is not TS18 proof.
