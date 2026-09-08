# Standalone TS18 launcher

## Status

The `launcher/` module is the staged successor to the DoFun theme runtime.

It is intentionally installable as an ordinary app before it can become HOME. DoFun remains available as recovery during physical validation.

## Build

Requirements match the repository CI: JDK 17, Gradle 9.5, Android platform 29 and Build Tools 36.

```bash
gradle :launcher:lintDebug :launcher:testDebugUnitTest :launcher:assembleDebug
python3 tools/launcher_apk_check.py launcher/build/outputs/apk/debug/launcher-debug.apk
```

For a release build, provide the existing four signing inputs used by the repository and run:

```bash
gradle -PVERSION_NAME=0.1.0 -PVERSION_CODE=1000 :launcher:assembleRelease
python3 tools/launcher_apk_check.py launcher/build/outputs/apk/release/launcher-release.apk
```

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

The Termux helper can also install and set HOME with a bounded root operation:

```bash
bash scripts/termux/install-standalone-launcher.sh /storage/emulated/0/Download/TS18-Standalone-Launcher.apk --set-home
```

## Map

The dashboard map is an in-process WebView backed by project-owned local HTML and OpenStreetMap raster tiles. It is created after the first launcher frame, restricted to the OSM tile origin, uses the WebView cache, identifies its tile requests as TS18 Launcher, and its GPS listeners are active only while the map is visible.

The map is intentionally a lightweight situational display. `OPEN NAV` launches the configured navigation application for full routing.

## Runtime measurement

After the launcher has settled, collect a bounded read-only snapshot rather than guessing about efficiency:

```bash
bash scripts/termux/measure-standalone-launcher.sh
```

The helper records current HOME, package/process state, memory, frame timing, CPU, WebView provider, location/media state and whether DoFun remains available as the recovery launcher. It does not mutate package, HOME, SELinux or vehicle state.

## Rollback

Use launcher Settings → **Disable HOME candidate / keep app installed**, then select DoFun in Android HOME settings if required.

The Termux installer records the pre-change HOME component in private Termux state and can attempt to restore it:

```bash
bash scripts/termux/install-standalone-launcher.sh --rollback-home
```

Do not uninstall or disable DoFun during this phase.

## Physical validation

Record exact results for:

- ordinary app start;
- first HOME selection;
- HOME key;
- app launch/return;
- SystemUI top/right regions;
- map render and GPS update;
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
