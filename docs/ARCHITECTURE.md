# Architecture

## Direction

`ts-theme` carries two separate runtime lanes.

| Lane | Purpose | Runtime authority |
| --- | --- | --- |
| `launcher/` | Primary standalone TS18 HOME candidate | Ordinary Android app + public Android APIs + narrow evidence-backed adapters |
| `theme/` | Legacy DoFun/RePlugin rollback/reference lane | DoFun `com.dofun.variety` + RePlugin |

DoFun remains installed and enabled until standalone physical qualification is complete.

## Authority boundary

vehicle/MCU/CAN/radio/reverse/amp/DSP/panel/keys
-> Unisoc kernel/HALs/vendor daemons
-> privileged Topway/TW services
-> Android framework/SystemUI
-> `com.cbkii.ts18launcher`

The standalone launcher owns only HOME UI, app launching, its context map and observation/control of existing Android media sessions. It is not a vehicle service, native radio/music service, Bluetooth stack, projection host, SystemUI, player or audio-focus owner.

Magisk root may perform bounded one-time setup/diagnostics but does not grant platform signing, UID 1000, protected SELinux, MCU/CAN or safe partition-write authority.

## Identity and lifecycle

- package `com.cbkii.ts18launcher`;
- Android 10/API 29;
- ordinary Activity always available for pre-HOME testing;
- HOME provided by a disabled-by-default `activity-alias` enabled only after explicit user action;
- Android HOME settings/role are the normal setup path;
- bounded Magisk `cmd package set-home-activity` is an optional one-time equivalent;
- DoFun is never automatically disabled or removed.

## Geometry

The physical panel is 1280 x 720. Physical testing confirmed the existing ~55 px top and 55 px right Topway/SystemUI boundaries while showing the first 81/64 px launcher controls were too small. The current dashboard therefore uses:

- 96 px left rail;
- 72 px Radio | Music | DD MMM strip;
- safe-right x=1225;
- map below the strip inside the remaining safe application area.

`Ts18Geometry` handles both full-physical and decor-fitted Activity surfaces and never hides SystemUI.

## Dashboard/app surface

The rail contains four configurable quick slots plus Apps and Settings. Unset quick slots fall back to Navigation, Radio, Music and Bluetooth roles. The app drawer is an in-HOME overlay covering only the map surface; map GPS/WebView work is suspended while fully covered.

## Generic media

`MediaListenerService` obtains active Android sessions through notification-listener authority. It follows platform-priority sessions, compares tokens, registers metadata/playback callbacks, excludes the resolved radio package and telecom sessions, and dispatches capability-aware previous/play-pause/next exactly once to one selected controller.

A visible-only one-second reconciliation fallback exists because physical TS18 testing showed callback delivery alone did not update Auxio-TS metadata for every track change. It stops outside the visible launcher lifecycle.

On this exact TS18, `com.tw.media` is **Auxio-TS**. That result must not be treated as stock Topway music evidence. `com.tw.music` remains only an installed-package fallback candidate until its native-app runtime/session behaviour is tested.

## Radio

Radio is a separate authority from generic music. Third-party NavRadio+ (`com.navimods.radio`) exposed a usable MediaSession on the physical TS18 and may therefore be controlled through that exact session when actions are advertised.

This does not establish the native Topway radio contract. No stock-radio package or private command surface is guessed. If native Topway radio/music do not expose adequate Android MediaSessions, exact-device evidence must identify the owning Topway contract before a dedicated adapter is added.

## Home-screen map

The original hand-written 5 x 5 tile renderer was retired after physical testing established that GPS and local WebView scripting worked but OSM tiles did not render reliably and gesture support was incomplete.

The replacement uses **bundled Leaflet 1.9.4** inside the existing lifecycle-bound WebView. Leaflet provides mature touch zoom, double-tap zoom, inertial panning, current-position marker, accuracy circle, bearing indicator and follow/recentre behaviour without introducing a native vector renderer or Android runtime dependency.

Leaflet JS/CSS are fetched only during build from pinned HTTPS distribution endpoints and accepted only when their exact SHA-256 values match. The APK includes the BSD-2-Clause licence. HOME never downloads the map library at runtime.

### Tile broker

WebView does not own tile HTTP. `TileBroker` intercepts only exact `https://tile.openstreetmap.org/{z}/{x}/{y}.png` requests and uses framework `HttpURLConnection` with:

- identifying TS18 Launcher User-Agent;
- bounded connect/read timeouts;
- normal platform TLS with no bypass;
- HTTP `max-age`/`Expires` freshness;
- ETag/Last-Modified conditional revalidation;
- seven-day fallback freshness where the response provides no usable lifetime;
- stale-cache fallback after a refresh failure;
- bounded 64 MiB disk cache;
- no bulk download or prefetch.

WebView remains restricted to local map assets plus the exact OSM tile origin and exposes no JavaScript-to-Java bridge. Full route calculation/navigation remains with Organic Maps, Google Maps, Waze or OsmAnd via public hand-off intents.

The raster/Leaflet design is intentional: it is more capable than the prototype but avoids MapLibre/native-vector APK, GPU and renderer-lifecycle cost in permanent HOME.

## App discovery/preferences

The drawer queries `ACTION_MAIN` + `CATEGORY_LAUNCHER`. Preferences use `SharedPreferences` for selected components/roles, media mode and map state.

## Topway adapter

`platform/TopwayAdapter` remains narrow. DoFun (`com.dofun.variety`) is recognised as environment/recovery evidence. `com.tw.music` may be used only as an installed fallback candidate. There is no guessed stock-radio package or private service/binder/property/MCU/CAN call.

## Reverse camera

Reverse-camera hand-off/return is a **roadmapped physical vehicle-lifecycle validation boundary**, not current launcher functionality. No reverse-camera code is introduced unless later exact-device evidence demonstrates a launcher-owned regression and identifies the relevant contract.

## Legacy DoFun/RePlugin lane

`theme/` remains independent from `launcher/`: unique `launcher.variety.theme.plugin.sfp_cbk_black` / `sfp_cbk_black` identity, independent signing and the established clean declarative RePlugin compatibility envelope. No vendor APK/resource/DEX/signer/private device data is a build input.

## Qualification boundary

CI proves source contracts, empty launcher release-runtime dependency graph, lint/unit/build, signed/minified one-DEX/no-native/no-Kotlin/no-AndroidX/no-RePlugin envelope and required Leaflet assets/signature. It does not prove TS18 runtime behaviour.

Current physical evidence proves the prior build's SystemUI geometry, quick slots/drawer/navigation hand-off, generic Auxio-TS/Spotify MediaSession path and third-party NavRadio+ session behaviour. The new Leaflet map, native Topway music/radio, HOME selection/recovery, Bluetooth/projection, reboot/cold boot and ACC sleep/wake remain exact-device checks. Reverse-camera hand-off/return remains a later roadmapped lifecycle check.
