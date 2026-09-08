# Architecture

## Direction

`ts-theme` now carries two deliberately separate runtime lanes.

| Lane | Purpose | Runtime authority |
| --- | --- | --- |
| `launcher/` | Primary standalone TS18 HOME candidate | Ordinary Android app + public Android APIs + narrow optional Topway/root adapters |
| `theme/` | Legacy DoFun/RePlugin compatibility and rollback/reference lane | DoFun `com.dofun.variety` + RePlugin |

The standalone launcher removes DoFun/RePlugin from the normal dashboard control path. DoFun remains installed and enabled as the recovery HOME until standalone physical qualification is complete.

## Standalone controlling layers

vehicle/MCU/CAN/radio/reverse/amp/DSP/panel/keys
→ Unisoc kernel/HALs/vendor daemons
→ privileged Topway/TW services
→ ordinary Android framework surfaces
→ `com.cbkii.ts18launcher`

The launcher owns only its HOME UI, app launching, its WebView map, and observation/control of Android media sessions. It does not become a vehicle service, radio service, Bluetooth stack, projection host, SystemUI or audio-focus owner.

### Identity and lifecycle

- package: `com.cbkii.ts18launcher`;
- exact target: Android 10/API 29;
- ordinary Activity is always launchable for safe pre-HOME testing;
- HOME is a disabled `activity-alias` enabled only after explicit user action;
- Android `RoleManager`/HOME settings are the normal setup path;
- Magisk root may perform the equivalent one-time `cmd package set-home-activity` setup when available;
- DoFun is not disabled or removed by the launcher.

### Geometry

The canonical physical panel is 1280 × 720:

- top system region: ~55 px;
- right Topway/SystemUI region: 55 px;
- hotseat: 81 px;
- dashboard strip: 64 px;
- safe-right: x=1225;
- map physical origin: x=81, y=119;
- map physical size: 1144 × 583.

`Ts18Geometry` supports both cases seen on aftermarket Android:

1. a full physical Activity surface, where the project applies the known top/right safe bounds itself; and
2. a decor-fitted Activity surface, where Android has already removed those system bars and the dashboard begins at local y=0.

The launcher never hides SystemUI.

## Dashboard

### Left rail

The 81 px rail provides Navigation, Apps, Bluetooth and Settings entry points. Navigation/Bluetooth roles are user-selected package targets rather than hard-coded vendor identities.

### Radio

Radio remains a separate authority.

The first implementation has a `MediaSession` radio adapter for a user-selected radio package. It can display that package's metadata and dispatch previous/next only to that exact session. If no usable session exists, tapping the panel opens the configured radio app.

No SzChoiceWay/FYT/other-vendor MCU protocol is copied onto TS18.

### Generic media

`MediaListenerService` is a notification-listener service used only to obtain active-session authority. It:

- watches the current platform-priority session set;
- registers callbacks on every active session so metadata/playback changes are not missed;
- compares session tokens rather than `MediaController` object identity;
- excludes the configured radio package from generic music selection;
- prefers playing/buffering sessions, then paused/stopped sessions;
- emits title/artist/play state only (no album art);
- sends previous/play-pause/next exactly once to the selected controller.

The launcher does not create a MediaSession, player, queue, playback service, notification or focus owner.

## Map — approved #10B path

The embedded dashboard map is deliberately not Android `ActivityView`/task embedding. Those paths require platform/system privileges not granted by Magisk root or an independently signed APK.

Instead the launcher uses a lazily-created in-process `WebView`:

- local project-owned HTML/JavaScript only;
- raster OpenStreetMap tiles over HTTPS;
- network restricted to `tile.openstreetmap.org`;
- no JavaScript interface/bridge exposed to remote content;
- no arbitrary browsing;
- a fixed centre marker driven by lifecycle-bound Android `LocationManager`;
- tile images are rebuilt only when the integer slippy-map tile changes;
- WebView cache is preferred before network;
- GPS listeners exist only while the dashboard map is visible;
- an `OPEN NAV` control launches the configured full navigation app.

This is a lightweight context map, not a replacement navigation engine. Organic Maps remains the full navigation authority.

## App discovery and preferences

The app drawer queries `ACTION_MAIN` + `CATEGORY_LAUNCHER` only. API 29 does not require the broad API-30 package-visibility permission for this use.

Preferences use platform `SharedPreferences` and hold only selected navigation/radio/Bluetooth/music packages plus map enable state.

## Topway adapter

`platform/TopwayAdapter` is intentionally narrow. The initial implementation only recognises evidence-backed packages:

- DoFun (`com.dofun.variety`) as environment/recovery-host evidence;
- stock music (`com.tw.music`) as a fallback launch target when installed.

It performs no private service, binder, property, MCU or CAN calls. Those can be added only after exact contracts are recovered and physically validated.

## Magisk root policy

Magisk root is authorised on this user-owned TS18 and should be used when it reduces ongoing runtime cost or makes integration materially simpler.

Preferred examples:

- one-time HOME assignment instead of a persistent helper;
- exact private-data diagnostics;
- systemless/reversible overlays;
- bounded setup/recovery operations.

Root is not a substitute for platform signing or protected service authority. Root-backed code must be explicit, bounded, fail-open/reversible and must not become a periodic runtime dependency when public callbacks suffice.

## Legacy DoFun/RePlugin lane

`theme/` remains available while the standalone launcher is being physically qualified. It continues to follow its own clean declarative compatibility contract.

Do not merge the two identities or make the standalone launcher depend on RePlugin.

## Physical validation boundary

Repository CI can prove compilation, lint, unit geometry, one-DEX/no-native/no-Kotlin/no-RePlugin launcher packaging and static resource constraints.

Only the TS18 can prove:

- HOME selection and recovery;
- correct SystemUI geometry;
- launcher return behaviour;
- media/radio behaviour with the installed apps;
- WebView/GPS rendering/performance;
- reverse-camera takeover/return;
- Bluetooth/projection behaviour;
- steering keys;
- launcher restart;
- reboot/cold boot;
- ACC sleep/wake.

Do not promote the standalone lane to sole release path until these pass.
