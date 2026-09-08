# TS18 Dashboard

Android dashboard development for CB's Topway TS18 (Android 10/API 29, 1280 × 720).

The repository now has two separate runtime lanes:

- **`launcher/` — standalone TS18 HOME candidate (primary direction).** An ordinary independently signed Android launcher that owns the dashboard directly and removes DoFun/RePlugin from the normal runtime path.
- **`theme/` — legacy DoFun/RePlugin theme lane.** Retained as a rollback/reference path while standalone HOME is physically qualified.

DoFun remains installed and enabled during launcher qualification.

## Standalone launcher

Package:

```text
com.cbkii.ts18launcher
```

The launcher is deliberately small:

- platform Android Views/Java;
- Android 10/API 29 only;
- no Compose/AppCompat/Material/Room/DataStore/Rx/DI;
- no player, queue, MediaSession, playback service, notification or audio-focus owner;
- one notification-listener service solely for observing/controlling existing media sessions;
- `SharedPreferences` for the few selected app roles;
- no native libraries;
- one-DEX release envelope enforced by CI.

### Dashboard

The exact-device geometry remains:

```text
physical panel     1280 × 720
top SystemUI       ~55 px
right SystemUI     55 px
hotseat            81 px
top strip          64 px
safe right         x=1225
map physical rect  x=81 y=119 w=1144 h=583
```

The left rail provides Navigation, Apps, Bluetooth and Settings. The top strip remains **Radio | Music | DD MMM**.

SystemUI is preserved; the launcher does not use immersive/fullscreen flags to steal the Topway status/navigation regions.

### Map — #10B

The standalone dashboard uses a lifecycle-bound, lazily-created in-process WebView map rather than privileged Android task embedding.

It uses:

- project-owned local HTML/JavaScript;
- HTTPS OpenStreetMap raster tiles;
- a fixed GPS-centred marker;
- no remote JavaScript framework;
- no JavaScript bridge exposed to remote content;
- network restricted to `tile.openstreetmap.org`;
- cache-first tile loading;
- GPS listeners only while the map is visible.

`OPEN NAV` launches the configured full navigation application. The WebView is a lightweight context map, not a navigation engine.

### Media and radio

Generic music uses `MediaSessionManager` through a user-granted notification-listener service. The service watches all active sessions, compares session tokens, follows metadata/playback callbacks and sends each previous/play-pause/next command once to one selected controller.

Radio remains a separate authority. A configured radio package can be displayed/controlled through that exact app's MediaSession when available; otherwise its panel opens the radio app. No FYT/SzChoiceWay/other-vendor MCU protocol is copied onto TS18.

The narrow Topway adapter currently uses only evidence-backed package information (`com.dofun.variety` and stock `com.tw.music`). Private Topway binder/property/MCU paths remain out until exact contracts justify them.

## Safe HOME rollout

The APK installs as a normal launcher app first. Its HOME `activity-alias` is **disabled by default**.

Recommended order:

1. install and launch TS18 Launcher normally;
2. configure Navigation/Radio/Bluetooth/Music;
3. grant media notification access and optional location;
4. verify SystemUI, map and app launching;
5. explicitly enable/set it as HOME;
6. validate vehicle behaviour;
7. retain DoFun as recovery until reboot/cold-boot/ACC validation is complete.

See [Standalone launcher](docs/STANDALONE_LAUNCHER.md).

## Magisk root policy

This TS18 is user-owned and Magisk-rooted. Root is authorised and should be used when it materially reduces ongoing runtime overhead, improves efficiency/recovery, or provides a cleaner implementation than a permanent helper.

Examples include one-time HOME assignment, exact private-data diagnostics and systemless/reversible integration.

Root remains narrow and bounded: it does not grant platform signing, UID 1000, signature permissions, MCU/CAN authority or make partition writes safe. The launcher therefore does not poll through `su`, disable protected Topway packages or use root as a substitute for public callbacks.

A bounded Termux helper is provided:

```bash
bash scripts/termux/install-standalone-launcher.sh \
  /storage/emulated/0/Download/TS18-Standalone-Launcher.apk --set-home
```

## Legacy DoFun theme

The legacy package remains:

```text
application id: launcher.variety.theme.plugin.sfp_cbk_black
plug-in id:     sfp_cbk_black
```

It retains the existing clean-room DoFun/RePlugin compatibility work, physical-install safety guards and diagnostic tooling. The standalone launcher does **not** depend on it and does not reuse its package identity. Legacy installation/evidence and publication procedures remain documented in [TS18 installation](docs/INSTALL_TS18.md) and [Releasing](docs/RELEASING.md).

## Repository map

- `launcher/` — standalone Android HOME candidate.
- `theme/` — legacy declarative DoFun/RePlugin theme.
- `config/ts18-layout.json` — exact physical geometry reference.
- `design/` — editable project-authored visual sources.
- `tools/launcher_apk_check.py` — standalone one-DEX/no-native/no-Kotlin/no-AndroidX/no-RePlugin release-envelope check.
- `tools/ts18_theme.py` — legacy theme/source validator.
- `scripts/termux/install-standalone-launcher.sh` — bounded root-assisted install/HOME/rollback helper.
- `scripts/termux/` and `scripts/magisk/` — existing DoFun diagnostics/install tooling.
- `docs/ARCHITECTURE.md` — authority and runtime architecture.
- `docs/STANDALONE_LAUNCHER.md` — standalone build/rollout/validation.
- `research/` — hashes/findings for supplied evidence; no vendor APK binaries.

## Development

```bash
python3 tools/ts18_theme.py validate
python3 -m unittest discover -s tests -v

gradle :theme:lintDebug :theme:assembleDebug
gradle :launcher:lintDebug :launcher:testDebugUnitTest :launcher:assembleDebug
```

The unminified debug APK is compilation/lint/test evidence, not release-envelope evidence. CI additionally exercises signed/minified release builds for both modules and applies the standalone runtime-envelope check to `launcher-release.apk`.

For a local signed launcher release, provide the repository signing inputs and run:

```bash
gradle -PVERSION_NAME=0.1.0 -PVERSION_CODE=1000 :launcher:assembleRelease
python3 tools/launcher_apk_check.py \
  launcher/build/outputs/apk/release/launcher-release.apk
```

## Candidate release

The existing **Manual Release** workflow remains the legacy DoFun-theme publication path.

The separate **Standalone Launcher Candidate** workflow builds and qualifies the launcher, uploads a 14-day signed candidate artifact, and can optionally publish an explicitly named `launcher-vX.Y.Z` GitHub prerelease. This keeps physical launcher qualification separate from the legacy theme release state machine.

## Physical validation boundary

CI does not prove head-unit behaviour. Before the standalone lane becomes the sole release path, validate on the exact TS18:

- ordinary start and HOME assignment/recovery;
- app launch/return;
- top/right SystemUI geometry;
- map render/GPS and idle resource use;
- generic music and radio;
- Bluetooth and projection;
- reverse camera takeover/return;
- steering keys;
- launcher process restart;
- reboot;
- cold boot;
- ACC sleep/wake.

Project-authored code/docs/assets are Apache-2.0. Vendor APKs, private signing material and device data are not committed.
