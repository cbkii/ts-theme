# Research evidence

## Supplied theme plug-ins

Static inspection was completed on 22 August 2026. Exact hashes are in
`research/reference-hashes.json`.

### FYD

- package `launcher.variety.theme.plugin.sfp_fyd18`;
- version `235_230411170935.land`;
- min/target/compile SDK 16/26/29;
- metadata `launcher.variety.theme.plugin=sfp_fyd18`;
- theme/OEM IDs `20221031103531`/`1132`;
- 48 DEX classes: RePlugin adapters/resources and no FYD feature implementation.

### GB2

- package `launcher.variety.theme.plugin.sfp_gb2`;
- version `94_210616135406`;
- min/target SDK 16/26;
- metadata `launcher.variety.theme.plugin=sfp_gb2`;
- theme/OEM IDs `20200818154716`/`190`;
- same vendor certificate and 48-class structural pattern.

### Additional random samples

| Local evidence name | Package / plug-in ID | Version | SDK min/target/compile | Theme/OEM ID | DEX classes |
| --- | --- | --- | --- | --- | --- |
| `theme_spf_ts10.apk` | `launcher.variety.theme.plugin.sfp_ts10` / `sfp_ts10` | `161_230609141148.land` | 16/26/29 | `20201030172202` / `190` | 60 |
| `theme_ts10_tw.apk` | `launcher.variety.theme.plugin.ts10_tw` / `ts10_tw` | `187_230530104436.land` | 16/26/29 | `20200903164745` / `190` | 60 |
| `launcher.variety.theme.plugin.tw23.apk` | `launcher.variety.theme.plugin.tw23` / `tw23` | `66_240102114725.land` | 16/26/29 | `20220413171802` / `190` | 59 |
| `launcher.variety.theme.plugin.carplay.apk` | `launcher.variety.theme.plugin.carplay` / `carplay` | `105_230422170206.port` | 16/26/29 | `20230304101853` / `190` | 61 |
| `launcher.variety.theme.plugin.t7_theme.apk` | `launcher.variety.theme.plugin.t7_theme` / `t7_theme` | `52_200825103947` | 16/26/not recorded | `20200825100808` / `190` | 48 |

All five share the same minimal no-component plug-in manifest pattern, three shared compatibility
permissions and the same vendor certificate as FYD/GB2. Their JSON/resource inventories vary widely,
which confirms the skin itself is declarative rather than a fixed UI implementation. One sample also
contains two malformed vendor JSON files; those files are recorded as evidence, not treated as a
quality baseline. This repository requires every maintained JSON file to parse.

### Executable-payload provenance

The common non-resource classes were compared with Qihoo360's official
`replugin-plugin-lib:2.3.4` AAR. Its 43 loader/library classes exactly account for the executable
payload in the smallest sample; the remaining sample classes are generated application `R` classes.
The official AAR used by this project has SHA-256
`0c3132e90dc372056bd9601788ee67a1c97fb64d15f6074826825addadf6a89f`.

The repository fetches that AAR from Qihoo360's documented Maven host into ignored build storage and
verifies the digest before Gradle sees it. No DEX, resource, layout, image or signing material is
copied from any supplied APK.

Across all seven samples, theme schema establishes explicit geometry, independent date/time fields,
radio frequency/band/unit and media title/control views. No supplied JSON exposes a marquee/ticker
property.

## Installed DoFun host

The supplied current APK is `com.dofun.variety` `V9.7.2.367.260312`, SHA-256
`75e7ea9b46d68754253aa385e6ac750aae957a5b72196fec5449ccf2782c60b1`.

Observed unprotected evidence:

- same certificate as FYD/GB2;
- 360 Jiagu loader/protected main implementation;
- `MEDIA_CONTENT_CONTROL`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`;
- `cn.cardoor.basic.media.NotifyService` as a notification listener;
- exported `cn.cardoor.libs.media.impl.MediaSourceService`;
- service action `cn.cardoor.libs.media.RemoteMediaService`;
- exported provider authority `com.dofun.variety.ExportedProvider`;
- fixed music candidates `com.tw.media/com.tw.music.MusicActivity` and
  `com.tw.music/com.tw.music.MusicActivity`;
- built-in media source headers for local music, Bluetooth, Spotify, Apple Music, YouTube Music and
  others;
- built-in `kp` theme is another declarative RePlugin payload with no media feature classes.

See `research/current-dofun-host-audit.json`.

## Auxio-TS integration precedent

Repository source inspected at `dev` commit
`cdd1fbedb211b3b137c02d8814090de9bcaa9d22`.

Auxio-TS currently covers:

- canonical Android MediaSession, MediaBrowser, media-button and notification handling;
- public Android metadata/play-state broadcasts;
- observed Topway metadata/progress broadcasts;
- incoming Topway previous/play-next/update/seek actions;
- verified Topway CommandService callbacks;
- DoFun selected-target diagnostics through the exported provider;
- generic, Topway-hybrid and optional genuine-stock relay modes.

Its current selectable integration modes are `Disabled`, `AndroidMediaSessionOnly`,
`GenericDofunMedia`, `TopwayBroadcastOnly`, `TopwayCommandOnly`,
`TopwayBroadcastAndCommand`, `AutoAllSafePaths` and `DiagnosticsOnly`. The dashboard adapter should
retain equivalent lane isolation for diagnosis even if its user-facing settings are simplified.

This supports a broad compatibility adapter architecture while preserving a single playback owner.

Primary repository references:

- <https://github.com/cbkii/Auxio-TS/blob/dev/docs/DOFUN_VARIETY_COMPATIBILITY.md>
- <https://github.com/cbkii/Auxio-TS/blob/dev/docs/ts18/launcher-integration/TOPWAY_MUSIC_WIDGET_CONTRACT.md>
- <https://github.com/cbkii/Auxio-TS/blob/dev/docs/ts18/launcher-integration/TOPWAY_COMMAND_SERVICE_BRIDGE.md>
- <https://github.com/cbkii/Auxio-TS/blob/dev/app/src/main/java/org/oxycblt/auxio/headunit/topway/TopwayMusicContract.kt>

## Historical physical evidence

On 10 June 2026, the fixed DoFun music card selected/controlled stock `com.tw.music` and did not
control the tested active `com.tw.media` session. The radio widget controlled NavRadio+ in that
configuration. This remains historical evidence, not proof of behaviour after every current setting
or integration-mode change.

## External primary sources

- DoFun launcher/theme capabilities:
  <https://www.dofun.cc/car-desktop/car-desktop-en.html>
- Qihoo360 RePlugin plug-in guide:
  <https://github.com/Qihoo360/RePlugin/wiki/%E6%8F%92%E4%BB%B6%E6%8E%A5%E5%85%A5%E6%8C%87%E5%8D%97>
- Android active media sessions:
  <https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName)>
- Android transport controls:
  <https://developer.android.com/reference/android/media/session/MediaController.TransportControls>
- Android notification listeners:
  <https://developer.android.com/reference/android/service/notification/NotificationListenerService>

## Evidence limits

- Jiagu protection prevents ordinary static inspection of the host's main runtime classes.
- Requested permissions and declared services do not prove the physical unit grants/uses every path.
- Matching vendor certificates do not prove a plug-in signer allowlist.
- Theme configuration does not prove marquee, surrounding click routing or saved-preset semantics.
- MediaSession availability does not prove DoFun fixed-widget selection or exactly-once control.
