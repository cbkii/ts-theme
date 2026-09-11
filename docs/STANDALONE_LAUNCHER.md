# Standalone TS18 launcher

## Status

The `launcher/` module is the staged Android 10/API 29 HOME successor path. It can be installed and tested as an ordinary Activity before HOME changes. DoFun remains installed and enabled as recovery during qualification.

Exact-device evidence currently establishes that the prior launcher preserved Topway/SystemUI boundaries, quick-launch/drawer/navigation hand-off worked, `com.tw.media` is Auxio-TS on this unit, Auxio-TS/Spotify worked through generic MediaSession, and third-party NavRadio+ (`com.navimods.radio`) exposed a usable independent session. Native Topway music/radio and the current Leaflet/Mono Drive UI remain separately subject to physical validation. CI is never represented as physical TS18 proof.

## Build/runtime envelope

Requirements match CI: JDK 17, Gradle 9.5, Python 3, Android platform 29 and Build Tools 36.

Leaflet 1.9.4 is fetched and SHA-256 verified during `launcher:preBuild`, then bundled into the APK. The release envelope remains one DEX, no native libraries, no Kotlin/AndroidX/RePlugin runtime and no added Android runtime dependency.

```bash
gradle :launcher:lintDebug :launcher:testDebugUnitTest :launcher:assembleDebug
```

Successful same-repository PR validation feeds the fixed draft release **000 Testing Only Version**. Use `TS18-Standalone-Launcher-TESTING.apk` for physical testing.

## Automotive HOME UI

Physical safe-area authority remains unchanged: full panel 1280 x 720, top content boundary 55 px, safe-right x=1225 and safe-bottom y=702. Those device coordinates remain raw pixels.

The driver-facing redesign uses:

- a 96 px rail on Driver side/Left/Right; Driver side is right for this exact Australian RHD TS18;
- fixed rail order: Apps at the top, 3-6 configurable role-icon quick slots in the middle, Navigation at the bottom;
- an 88 px Radio | Music | DD MMM strip using a 12-column 4/6/2 split across the usable content area;
- Settings in the drawer header rather than consuming a prime rail slot; tapping DD MMM still opens Settings;
- stable Previous | Play/Pause | Next media positions with the centre action visually primary;
- larger action regions and local vector icons rather than Unicode/text button glyphs;
- black + charcoal surfaces, warm orange primary/selected state, 8dp gutters, rounded cards and 140 ms pressed/focus feedback;
- semantic resource dimensions for inner UI while preserving exact physical Topway geometry.

Radio/music metadata uses an endless low-speed one-line marquee: it starts at the readable beginning, holds for five seconds, scrolls, pauses at the end, returns to the start and repeats. Tapping metadata opens its source app. Unsupported media actions remain disabled in place rather than shifting control positions.

## Appearance

Display and map appearance use one shared effective mode:

- **Auto** - source selectable as ambient-light sensor or schedule;
- **Day**;
- **High contrast** for high-glare conditions;
- **Dim** around day/night transitions and low ambient light;
- **Night**.

When Auto uses the sensor, stale or unavailable light-sensor evidence falls back to schedule. If the user has not supplied a schedule, the bounded generic schedule uses 07:00 day and 19:00 night anchors, a 45-minute transition period around each anchor, and a central daytime high-glare window. This is deliberately a location-independent approximation, not a claim to astronomical sunrise/sunset or vehicle illumination authority. User-set day/night times replace the generic anchors, including schedules crossing midnight.

The launcher chrome and Leaflet map consume the same resolved mode. A setting changed while HOME is behind Settings is detected on return and causes one launcher recreation so the visible chrome does not retain stale colours.

## App drawer

Apps opens a five-column in-HOME overlay over the map only. While fully covered, map GPS/WebView work is suspended and resumes when the drawer closes.

The drawer provides:

- a visible Settings gear and Close action in the header;
- five user-configurable quick-access slots above the grid;
- an always-visible local search field with a prominent voice-search action; the microphone remains visible and is disabled with an accessibility description if no speech recogniser is available;
- local search by application label/package;
- 72dp installed-app icons and readable labels in the full grid;
- lazy app-icon loading retained for the main grid.

Fixed control/source/role surfaces use project-authored monochrome icons. The full app grid intentionally retains installed application icons because it is an application browser.

## Home-screen map

The map remains bundled Leaflet 1.9.4 inside a restricted lifecycle-bound WebView. `TileBroker` supplies only exact `https://tile.openstreetmap.org/{z}/{x}/{y}.png` requests through framework `HttpURLConnection`, preserving identifying User-Agent, bounded timeouts, normal TLS, HTTP freshness/revalidation, stale fallback, 64 MiB disk ceiling and no bulk prefetch.

The automotive map surface adds icon-only zoom in, zoom out, follow/recentre and primary open-navigation actions. All map buttons can be hidden together from Settings without changing the map/network lifecycle. A manual pan leaves follow mode and visibly deactivates the follow icon; recenter restores selected/accent follow state.

Healthy GPS/tile state has no permanent status text. Only locating, loading, cached/offline and meaningful error states are surfaced. Map brightness/contrast follows the shared appearance resolver rather than maintaining a separate theme authority.

Full route calculation/navigation remains owned by Organic Maps, Google Maps, Waze or OsmAnd through public hand-off intents.

### Organic Maps future modes

The current `cbkii/organicmaps` fork defines exact location modes `PENDING_POSITION=0`, `NOT_FOLLOW_NO_POSITION=1`, `NOT_FOLLOW=2`, `FOLLOW=3`, and `FOLLOW_AND_ROTATE=4` in `LocationState`. Those modes and `setDrivingViewEnabled()` are recorded in [Organic Maps future driving-mode integration](ORGANIC_MAPS_MODES.md) as future planned targets only. They are not a supported cross-package API, so this launcher does not invoke private/JNI methods, reflection, root input injection or guessed broadcasts to control them.

## Generic music authority

Generic music uses one existing Android MediaSession selected by notification-listener authority. It creates no player, queue, session or audio-focus owner.

- **Auto** follows Android active-session priority while excluding the resolved radio and telecom/call sessions.
- **Prefer music app** selects the configured Music package while it has an active session, then falls back to Auto.
- previous/play-pause/next dispatch once to the selected controller only when advertised.
- a visible-only one-second reconciliation fallback remains because physical TS18 testing showed callback delivery alone did not reliably update Auxio-TS metadata for every track change.

On this TS18, `com.tw.media` is **Auxio-TS**. It is not stock Topway evidence. `com.tw.music` is only an installed fallback candidate until physically tested.

## Radio authority

Radio remains independent from generic music. Third-party NavRadio+ (`com.navimods.radio`) exposed a usable MediaSession in the tested state. Native Topway radio remains unverified; no stock-radio package/private broadcast/root key/MCU command is guessed.

## Settings

Settings uses framework-only automotive rows rather than generic full-width buttons:

- icon + primary label + current value for rows;
- native `Switch` for Dashboard map and Map controls;
- single-choice dialogs for rail position, 3-6 middle quick-slot count, display appearance, Auto appearance source and media selection mode;
- time pickers for user-configured day/night schedule anchors plus one action to restore the generic schedule;
- package and role-icon picker rows for six HOME middle slots and five drawer quick slots;
- Advanced HOME/recovery section at the bottom;
- confirmation before Magisk HOME assignment or disabling the HOME candidate.

## Physical requalification after this UI change

Keep DoFun as HOME and test the launcher as an ordinary Activity first.

1. Confirm both left and right rail positions keep all interactive content out of the Topway top/right SystemUI and that Driver side resolves to the desired right-side layout.
2. Exercise middle quick-slot counts 3, 4, 5 and 6; Apps must remain fixed at the top and Navigation fixed at the bottom, with every visible target usable.
3. Confirm the 88 px media strip, 4/6/2 content split, role icons and larger transport targets fit without clipping; Previous/Play-Pause/Next must remain stable and capability-aware.
4. Confirm the date still opens Settings, Apps opens only the drawer, the drawer gear opens Settings and Close closes the overlay.
5. Configure and launch all five drawer quick-access entries; verify local text search and voice-to-search filtering in both the drawer and picker.
6. Confirm Leaflet tiles/GPS/bearing/accuracy, pan/inertia, pinch/double-tap, follow/recentre and navigation hand-off. Test Map controls ON/OFF.
7. Exercise Auto with Sensor and Schedule, explicit Day/High contrast/Dim/Night, a custom day/night schedule, and the generic 07:00/19:00 fallback. Confirm the launcher chrome and map change together and returning from Settings does not leave stale colours.
8. Confirm healthy map status disappears and locating/loading/offline/error states remain readable.
9. Recheck Auxio-TS/Spotify metadata/actions and NavRadio+ only where relevant to changed presentation.
10. Separately test native Topway music and native Topway radio; capture package/session evidence before proposing any adapter if public MediaSession is inadequate.
11. Use `scripts/termux/measure-standalone-launcher.sh` after a settled map session to compare CPU/RAM/frame impact.

Only after these ordinary-Activity checks pass should HOME/reboot/cold-boot/ACC qualification continue. Reverse-camera hand-off/return remains a roadmapped lifecycle regression check only; there is no reverse-camera implementation in this launcher.

## Rollback

DoFun remains installed/enabled throughout qualification. Launcher Settings can disable the HOME candidate while keeping the app installed. The Termux helper can restore its saved pre-change HOME:

```bash
bash scripts/termux/install-standalone-launcher.sh --rollback-home
```

Rollback must verify the saved HOME is restored and the standalone HOME alias is disabled.
