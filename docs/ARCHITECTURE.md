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

## Geometry and automotive UI

The physical panel is 1280 x 720. Exact-device testing established the ~55 px top/right Topway/SystemUI boundaries. Those physical boundaries remain raw-pixel authority while inner controls use semantic resources.

The automotive HOME now uses:

- 96 px side rail, user-selectable as Driver side/Left/Right; Driver side is right on this exact Australian RHD TS18;
- 88 px Radio | Music | DD MMM strip;
- safe-right x=1225;
- mirrored content geometry that keeps the rail and map outside the Topway right SystemUI;
- black base, charcoal cards, warm orange primary state, local monochrome role/vector icons and 140 ms pressed/focus feedback;
- stable Previous | Play/Pause | Next ordering with the centre action visually primary.

`Ts18Geometry` handles both full-physical and decor-fitted Activity surfaces and never hides SystemUI.

## Dashboard/app surface

The rail contains 3-6 configurable monochrome role-icon quick slots with Apps fixed last. The first four unset slots fall back to Navigation, Radio, Music and Bluetooth roles. Settings is intentionally moved out of the prime rail and into the drawer header; tapping the date remains a Settings shortcut.

The app drawer remains an in-HOME overlay covering only the map surface; map GPS/WebView work is suspended while fully covered. It uses a five-column app grid with larger installed-app icons/labels, an explicit Settings gear and Close control, a five-slot user-configurable quick-access row, and local label/package search. Search does not introduce a service or background index.

## Generic media

`MediaListenerService` obtains active Android sessions through notification-listener authority. It follows platform-priority sessions, compares tokens, registers metadata/playback callbacks, excludes the resolved radio package and telecom sessions, and dispatches capability-aware previous/play-pause/next exactly once to one selected controller.

A visible-only one-second reconciliation fallback exists because physical TS18 testing showed callback delivery alone did not update Auxio-TS metadata for every track change. It stops outside the visible launcher lifecycle.

On this exact TS18, `com.tw.media` is **Auxio-TS**. That result must not be treated as stock Topway music evidence. `com.tw.music` remains only an installed-package fallback candidate until its native-app runtime/session behaviour is tested.

## Radio

Radio is a separate authority from generic music. Third-party NavRadio+ (`com.navimods.radio`) exposed a usable MediaSession on the physical TS18 and may therefore be controlled through that exact session when actions are advertised.

This does not establish the native Topway radio contract. No stock-radio package or private command surface is guessed. If native Topway radio/music do not expose adequate Android MediaSessions, exact-device evidence must identify the owning Topway contract before a dedicated adapter is added.

## Home-screen map

The map uses bundled **Leaflet 1.9.4** inside the lifecycle-bound WebView, with mature touch zoom, double-tap zoom, inertial panning, current-position marker, accuracy circle, bearing indicator and follow/recentre behaviour without a native vector renderer or Android runtime dependency.

Leaflet JS/CSS are fetched only during build from pinned HTTPS distribution endpoints and accepted only when their exact SHA-256 values match. HOME never downloads the map library at runtime.

Driver-facing map actions are project-authored icon buttons: zoom in, zoom out, follow/recentre and one primary navigation action. They may be hidden as a group from Settings. Follow state is visible through the location icon's selected accent state. Healthy map status is hidden; only locating/loading/offline/error states are surfaced. Map appearance supports Auto/Normal/Dim through a local overlay; Auto follows Android night mode rather than guessing MCU illumination state.

### Tile broker

WebView does not own tile HTTP. `TileBroker` intercepts only exact `https://tile.openstreetmap.org/{z}/{x}/{y}.png` requests and uses framework `HttpURLConnection` with identifying User-Agent, bounded connect/read timeouts, normal platform TLS, HTTP freshness/conditional revalidation, stale-cache fallback and a bounded 64 MiB disk cache. No bulk download or prefetch is used.

WebView remains restricted to local map assets plus the exact OSM tile origin and exposes no JavaScript-to-Java bridge. Full route calculation/navigation remains with Organic Maps, Google Maps, Waze or OsmAnd via public hand-off intents.

## App discovery/preferences

The drawer queries `ACTION_MAIN` + `CATEGORY_LAUNCHER`. Preferences use `SharedPreferences` for selected roles/components, 3-6 HOME quick slots, five drawer quick slots, rail side, media mode, map visibility, map-control visibility and map appearance.

## Topway adapter

`platform/TopwayAdapter` remains narrow. DoFun (`com.dofun.variety`) is recognised as environment/recovery evidence. `com.tw.music` may be used only as an installed fallback candidate. There is no guessed stock-radio package or private service/binder/property/MCU/CAN call.

## Reverse camera

Reverse-camera hand-off/return is a **roadmapped physical vehicle-lifecycle validation boundary**, not current launcher functionality. No reverse-camera code is introduced unless later exact-device evidence demonstrates a launcher-owned regression and identifies the relevant contract.

## Legacy DoFun/RePlugin lane

`theme/` remains independent from `launcher/`: unique `launcher.variety.theme.plugin.sfp_cbk_black` / `sfp_cbk_black` identity, independent signing and the established clean declarative RePlugin compatibility envelope. No vendor APK/resource/DEX/signer/private device data is a build input.

## Qualification boundary

CI proves source contracts, empty launcher release-runtime dependency graph, lint/unit/build, signed/minified one-DEX/no-native/no-Kotlin/no-AndroidX/no-RePlugin envelope and required Leaflet assets/signature. It does not prove TS18 runtime behaviour.

Current physical evidence proves the prior build's SystemUI geometry, quick slots/drawer/navigation hand-off, generic Auxio-TS/Spotify MediaSession path and third-party NavRadio+ session behaviour. The redesigned automotive HOME, Leaflet map, native Topway music/radio, HOME selection/recovery, Bluetooth/projection, reboot/cold boot and ACC sleep/wake remain exact-device checks. Reverse-camera hand-off/return remains a later roadmapped lifecycle check.
