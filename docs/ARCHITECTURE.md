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

## Geometry and Mono Drive UI

The physical panel is 1280 x 720. Exact-device testing established the ~55 px top/right Topway/SystemUI boundaries. Those physical boundaries remain raw-pixel authority while inner controls use semantic resources.

The automotive HOME uses:

- 96 px side rail, user-selectable as Driver side/Left/Right; Driver side is right on this exact Australian RHD TS18;
- fixed rail order Apps -> 3-6 configurable role-icon middle slots -> Navigation;
- 88 px `[Radio controls] [shared active metadata] [Music controls] [DD MMM]` strip;
- fixed-width Radio and Music control groups with one shared metadata region and a compact DD MMM date surface;
- safe-right x=1225;
- mirrored content geometry that keeps rail and map outside the Topway right SystemUI;
- **independent Radio-left/Radio-right group placement**, not derived from rail or RHD/LHD state;
- black base, charcoal cards and warm orange primary state;
- 120 ms state/icon crossfade, 140 ms press/focus feedback, 160 ms transient reveal and 180 ms drawer animation;
- stable Previous | Play/Pause | Next ordering with the centre action visually primary;
- primary media title/station as the slow endless marquee and static secondary artist/program/source context.

`Ts18Geometry` handles both full-physical and decor-fitted Activity surfaces and never hides SystemUI.

### Functional icon authority

Fixed functional glyphs are vendored from one family: **Google Material Symbols Rounded** at `google/material-design-icons@40a7a292a79d9394157e1ea24f83d52d5e17c556`, Apache-2.0. Upstream vector path geometry is retained; intrinsic size is adapted to 36dp and theme tint is removed because launcher state tinting is applied by the owning views. See `docs/ICON_SOURCES.md` and packaged `assets/licenses/MATERIAL_SYMBOLS_NOTICE.txt`.

`ic_launcher.xml` remains project branding. Installed application icons remain full-colour in the app browser because they represent app identity, not fixed launcher roles.

## Appearance authority

Launcher chrome and map use one effective appearance resolver rather than separate theme state. Supported modes are Auto, Day, High contrast, Dim and Night. Auto source is either ambient-light sensor or schedule. Sensor samples expire after five minutes; absent/stale sensor evidence falls back to the schedule.

Without a user schedule, the location-independent fallback anchors day at 07:00 and night at 19:00, treats +/-45 minutes around those transitions as Dim, and uses a central daylight window for High contrast. User-configured day/night anchors replace those defaults and may cross midnight. This deliberately does not claim vehicle illumination or astronomical sunrise/sunset authority.

The appearance controller updates visible launcher and map colours in place when sensor/schedule mode changes; it does not repeatedly recreate HOME or WebView. Explicit Settings changes may use one controlled recreation. Sensor samples expire after five minutes and then use the schedule fallback.

## Dashboard/app surface

The rail contains Apps fixed at the top, 3-6 configurable monochrome role-icon quick slots in the middle and Navigation fixed at the bottom. Unset middle slots may resolve through their configured role fallbacks. Settings is intentionally moved out of the prime rail and into the drawer header; tapping the date remains a Settings shortcut.

The app drawer remains an in-HOME overlay covering only the map surface; map GPS/WebView work is suspended while fully covered. It uses a five-column app grid with larger installed-app icons/labels, explicit Settings and Close actions, a five-slot user-configurable quick-access row, and always-visible local text/voice search. Search does not introduce a service or background index.

## Generic media

`MediaListenerService` obtains active Android sessions through notification-listener authority. It follows platform-priority sessions, compares tokens, registers metadata/playback callbacks, excludes the resolved radio package and telecom sessions, and dispatches capability-aware previous/play-pause/next exactly once to one selected controller.

A visible-only one-second reconciliation fallback exists because physical TS18 testing showed callback delivery alone did not update Auxio-TS metadata for every track change. It stops outside the visible launcher lifecycle.

`MediaMetadataView` owns the single shared two-level presentation: the selected source's primary title/station uses the bounded-speed five-second-hold marquee, while secondary artist/program/source is static and end-ellipsised. Radio and Music retain separate source/transport controls and dispatch paths; only the group positions swap according to the independent Radio/Music sides preference, preserving Previous -> Play/Pause -> Next order on either side.

On this exact TS18, `com.tw.media` is **Auxio-TS**. That result must not be treated as stock Topway music evidence. `com.tw.music` remains only an installed-package fallback candidate until its native-app runtime/session behaviour is tested.

## Radio

Radio is a separate authority from generic music. Third-party NavRadio+ (`com.navimods.radio`) exposed a usable MediaSession on the physical TS18 and may therefore be controlled through that exact session when actions are advertised.

This does not establish the native Topway radio contract. No stock-radio package or private command surface is guessed. If native Topway radio/music do not expose adequate Android MediaSessions, exact-device evidence must identify the owning Topway contract before a dedicated adapter is added.

## Home-screen map

The map uses bundled **Leaflet 1.9.4** inside the lifecycle-bound WebView, with mature touch zoom, double-tap zoom, inertial panning, current-position marker, accuracy circle, bearing indicator and follow/recentre behaviour without a native vector renderer or Android runtime dependency.

Leaflet JS/CSS are fetched only during build from pinned HTTPS distribution endpoints and accepted only when their exact SHA-256 values match. HOME never downloads the map library at runtime.

Driver-facing map actions are Material Symbols Rounded icon buttons: zoom in, zoom out, follow/recentre and one primary navigation action. They may be hidden as a group from Settings. Selected Follow uses redundant state: orange tint plus a 3dp accent outline/halo. Healthy map status is hidden; only locating/loading/offline/error states are surfaced. Map filter/overlay state consumes the shared launcher appearance resolver.

### Tile broker

WebView does not own tile HTTP. `TileBroker` intercepts only exact `https://tile.openstreetmap.org/{z}/{x}/{y}.png` requests and uses framework `HttpURLConnection` with identifying User-Agent, bounded connect/read timeouts, normal platform TLS, HTTP freshness/conditional revalidation, stale-cache fallback and a bounded 64 MiB disk cache. With no usable network it returns fresh/stale cache immediately or fails an uncached tile promptly. No bulk download or prefetch is used.

WebView remains restricted to local map assets plus the exact OSM tile origin and exposes no JavaScript-to-Java bridge. Full route calculation/navigation remains with Organic Maps, Google Maps, Waze or OsmAnd via public hand-off intents.

Renderer termination is handled with one bounded automatic WebView recreation. The dead instance is detached and destroyed, local Leaflet is reloaded and the in-memory viewport/follow state plus last GPS fix are restored. Repeated failure stops automatic recreation and exposes an explicit retry; no crash/reload loop or location history is persisted.

### Offline-first policy

Network availability is optional. Core HOME operation must start and remain usable without Internet access. App launching, settings, GPS/location, local media, cached map tiles, navigation hand-off and the underlying MediaSession controls remain available where their installed source permits. Organic Maps, OsmAnd/OsmAnd+, Auxio-TS, Auxio and VLC are recommended offline-capable choices without becoming launcher dependencies; the OSM raster cache is not a complete offline navigation database. `ConnectivityManager` is used only to identify a definite no-network condition, never as proof that the public Internet is reachable.

## Organic Maps future integration

`cbkii/organicmaps` currently defines `LocationState` modes `PENDING_POSITION=0`, `NOT_FOLLOW_NO_POSITION=1`, `NOT_FOLLOW=2`, `FOLLOW=3` and `FOLLOW_AND_ROTATE=4`, plus `nativeSwitchToNextMode()`, `getMode()` and `setDrivingViewEnabled(enabled, autoReturn, recenter)`. See `docs/ORGANIC_MAPS_MODES.md`.

Those are Organic Maps engine/JNI contracts, not a supported external launcher interface. No runtime coupling is added in this PR. Any future synchronisation must first add an explicit versioned interface to the Organic Maps fork and then validate it on the exact TS18.

## App discovery/preferences

The drawer queries `ACTION_MAIN` + `CATEGORY_LAUNCHER`. Preferences use `SharedPreferences` for role packages, per-slot role icons, 3-6 HOME middle slots, five drawer quick slots, rail side, **independent Radio/Music side**, last explicit source/package, media selection mode, map visibility/control visibility, appearance mode, Auto appearance source and optional day/night schedule anchors. Versioned JSON export/import whitelists these settings through Storage Access Framework and validates package availability before a transactional commit.

## Topway adapter

`platform/TopwayAdapter` remains narrow. DoFun (`com.dofun.variety`) is recognised as environment/recovery evidence. `com.tw.music` may be used only as an installed fallback candidate. There is no guessed stock-radio package or private service/binder/property/MCU/CAN call.

## Reverse camera

Reverse-camera hand-off/return is a **roadmapped physical vehicle-lifecycle validation boundary**, not current launcher functionality. No reverse-camera code is introduced unless later exact-device evidence demonstrates a launcher-owned regression and identifies the relevant contract.

## Legacy DoFun/RePlugin lane

`theme/` remains independent from `launcher/`: unique `launcher.variety.theme.plugin.sfp_cbk_black` / `sfp_cbk_black` identity, independent signing and the established clean declarative RePlugin compatibility envelope. No vendor APK/resource/DEX/signer/private device data is a build input.

## Qualification boundary

CI proves source contracts, colour contrast, empty launcher release-runtime dependency graph, lint/unit/build, signed/minified one-DEX/no-native/no-Kotlin/no-AndroidX/no-RePlugin envelope, required Leaflet assets and packaged Material Symbols attribution. It does not prove TS18 runtime behaviour.

`docs/MONO_DRIVE_UX_ACCEPTANCE.md` defines the explicit 1280 x 720 emulator matrix and physical TS18 metrics for glance time, target accuracy, tap counts, visible-response latency, common-task completion and comparative CPU/PSS/frame evidence. These qualification procedures do not restrict runtime features to parked state.

Current physical evidence proves the prior build's SystemUI geometry, quick slots/drawer/navigation hand-off, generic Auxio-TS/Spotify MediaSession path and third-party NavRadio+ session behaviour. The current Mono Drive HOME, two-level media cards, independent transport-side preference, Leaflet map, appearance automation, voice search, native Topway music/radio, HOME selection/recovery, Bluetooth/projection, reboot/cold boot and ACC sleep/wake remain exact-device checks. Reverse-camera hand-off/return remains a later roadmapped lifecycle check.
