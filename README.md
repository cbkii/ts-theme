# TS18 Dashboard

Android dashboard development for CB's Topway TS18 (Android 10/API 29, 1280 x 720).

The repository has two deliberately separate runtime lanes:

- **`launcher/` - standalone TS18 HOME candidate (primary direction).** An ordinary independently signed Android launcher that owns the dashboard directly and removes DoFun/RePlugin from the normal runtime path.
- **`theme/` - legacy DoFun/RePlugin theme lane.** Retained as rollback/reference while standalone HOME is physically qualified.

DoFun remains installed and enabled during launcher qualification.

## Standalone launcher

Package: `com.cbkii.ts18launcher`.

The launcher stays deliberately small: platform Android Views/Java, API 29, no Compose/AppCompat/Material/Room/DataStore/Rx/DI, no launcher-owned player/queue/MediaSession/audio focus, no native libraries, and a one-DEX release envelope enforced by CI. A notification-listener service observes existing Android media sessions; bounded media bootstrap uses only standard MediaSession/MediaBrowser surfaces plus a last-resort ordinary app launch.

### TS18 Mono Drive HOME

Exact TS18 testing preserves the 55 px top/right Topway/SystemUI boundaries. HOME uses a **96 px side rail** and **88 px `[Radio controls] [shared active metadata] [Music controls] [DD MMM]` strip**. The rail can be Driver side, Left or Right; on this exact Australian RHD unit Driver side means right. Radio/Music control groups can be swapped independently with `Radio / Music sides`.

The rail order is fixed as **Apps at the top, 3-6 configurable quick slots in the middle, and Navigation at the bottom**. Quick 1 defaults to the **Settings** role so setup is discoverable directly below Apps, but every middle slot remains configurable. The fixed Navigation button uses the selected accent hue. The date is display-only: it is transparent, non-clickable and non-focusable.

Each quick slot has two independent settings:

- **APP** - choose an installed app, use the semantic role default, or change role;
- **ICON** - `Auto` or a curated Material Symbols Rounded appearance override.

`Auto` is truthful: an explicit app shortcut displays the installed app's icon, while a role shortcut displays its semantic monochrome role glyph. An icon override is visual only and never changes what launches.

Fixed/user-selectable monochrome glyphs use one pinned family: **Google Material Symbols Rounded**, vendored from `google/material-design-icons@40a7a292a79d9394157e1ea24f83d52d5e17c556` under Apache-2.0. The curated picker includes useful automotive/general appearances without shipping a whole icon library. See [icon sources](docs/ICON_SOURCES.md).

The UI uses black/charcoal surfaces and a configurable Material-derived accent palette. **Orange remains the default**, with Amber, Lime, Green, Teal, Cyan, Blue, Purple, Pink and Red available. Hue affects only the existing accent role: primary/selected/focus elements, section accents, fixed Navigation and the map location accent; neutral black/charcoal/white hierarchy is unchanged.

### Media and radio

Radio and generic Music remain separate authorities. The shared metadata surface displays the selected source using a 22sp slow-marquee primary title/station plus a static 16sp secondary artist/program/source. Last explicit source selection resolves stale simultaneous `PLAYING` reports.

The Radio source icon always opens the configured/default Radio application. The Music source icon always opens the configured/default Music application. Shared metadata may open the source currently being displayed.

Previous / Play-Pause / Next controls remain visibly and physically usable at all times. A press uses this bounded public-API sequence:

1. exact matching active MediaSession if usable;
2. exported standard `android.media.browse.MediaBrowserService` if available;
3. one ordinary source-app launch and bounded session retry (maximum about 4.5 seconds);
4. short visible `... unavailable` / `... did not become ready` status rather than a disabled or silent button.

A Settings switch, **Warm media sources on HOME start**, optionally pre-connects exported MediaBrowser services without opening their Activity UI. It is enabled by default but can be disabled. The launcher creates no MediaSession and never owns audio focus.

When Play/Pause is used to start a non-playing Music source, the launcher first sends one best-effort Pause to a genuinely playing Radio source; starting Radio similarly pauses Music. Pausing the selected source does not arbitrarily stop the other source.

Generic music selection still supports Auto and Prefer music app, remembers the last explicitly selected eligible music package, and retains the visible-only one-second reconciliation fallback because physical TS18 testing showed callback-only metadata could become stale.

On this exact unit **`com.tw.media` is Auxio-TS**, not native Topway music. Third-party **NavRadio+ is `com.navimods.radio`**. Stock Topway music/radio remain separately unverified and no private Topway command is guessed.

### Appearance

Launcher chrome and the experimental map share Auto/Day/High contrast/Dim/Night appearance. Auto can use the ambient-light sensor or a schedule. Unavailable/stale sensor state falls back to schedule. Without a user schedule, the generic fallback uses 07:00/19:00 anchors, transition periods around those anchors and a central daytime high-glare window. User-selected anchors replace the defaults and may cross midnight.

### Apps drawer

Apps opens an in-HOME overlay. The drawer has Settings and Close actions, five user-configurable quick-access slots, always-visible local search with a prominent voice-search action, and a five-column grid with 72dp installed-app icons and readable labels. Drawer quick slots use the same APP/ICON model as HOME middle slots.

### Experimental HOME map

The Leaflet/WebView implementation is retained **only as an experimental physical-test comparator and is OFF by default on a clean install**. An existing explicit map preference is preserved during upgrades.

When enabled it uses pinned Leaflet 1.9.4 and launcher-owned `TileBroker` for exact `https://tile.openstreetmap.org/{z}/{x}/{y}.png` raster requests, with identifying User-Agent, bounded timeouts, HTTP freshness/ETag/Last-Modified revalidation, stale-cache fallback, 64 MiB disk ceiling, no bulk prefetch, no arbitrary browsing and no JS bridge. The raster cache is not an Organic Maps/OsmAnd/Google/Yandex offline-map database.

The duplicate map Navigation button has been removed. Navigation remains the permanent accented rail endpoint. Map-only controls are zoom in, zoom out and follow/recentre; when shown they stay on the **opposite edge from the side rail** and may still be hidden together in Settings.

The larger WebView-vs-native/windowed navigation decision is intentionally outside this PR batch. Organic Maps remains the preferred offline navigation authority and full routing/navigation remains a public hand-off to the configured navigation app.

## Offline-first policy

Network availability is optional. Core HOME operation must start and remain usable without Internet access: app launching, settings, GPS/location, local media, cached experimental-map tiles, navigation hand-off and underlying media controls continue where the installed source permits.

Recommended offline-capable ecosystem choices include **Organic Maps** and **OsmAnd/OsmAnd+** for navigation, and **Auxio-TS, Auxio and VLC** for local music. These are recommendations, not launcher dependencies.

## Configuration and diagnostics

Versioned SAF JSON export/import includes app/role assignments, quick-slot count, rail and Radio/Music sides, map settings, media mode, startup media warm-up, accent hue, icon overrides and appearance schedule. It does not export caches, secrets or location history.

Read-only physical evidence helpers:

- `scripts/termux/measure-standalone-launcher.sh` - CPU/RAM/frame and settled runtime measurements;
- `scripts/termux/collect-window-media-evidence.sh` - bounded task/window/freeform/PiP, package/service, MediaBrowser/MediaSession, display-density and relevant-log capture for the separate future map-windowing investigation and media bootstrap validation.

The evidence collector does not change settings/tasks/packages/playback/window state. Permission/time-out/root gaps remain BLOCKED/UNKNOWN rather than negative evidence.

## UX qualification

[Mono Drive UX acceptance](docs/MONO_DRIVE_UX_ACCEPTANCE.md) defines the quantitative acceptance contract: contrast/touch/motion gates, the 1280 x 720 API29 emulator matrix, and physical TS18 metrics for glance time, action taps, response latency, completion time, wrong-target rate and comparative CPU/PSS/frame evidence.

Physical validation is still required for the new always-ready media bootstrap/warm-up, mutual Radio/Music start behaviour, quick-slot APP/ICON editor, accent palette, inert date, fixed accented Navigation endpoint and experimental map defaults/controls.

## Safe HOME rollout

Install/test the APK as an ordinary Activity first; its HOME alias is disabled by default. Configure and physically qualify while DoFun remains HOME, then explicitly enable/select standalone HOME only after ordinary-Activity gates pass. DoFun remains the rollback launcher through reboot/cold-boot/ACC qualification.

Reverse-camera hand-off/return is a roadmapped physical lifecycle item only. The launcher contains no reverse-camera implementation.

See [Standalone launcher](docs/STANDALONE_LAUNCHER.md) for the qualification playbook.

## Magisk root policy

This user-owned TS18 is Magisk-rooted. Root may be used for bounded one-time HOME assignment, read-only diagnostics and reversible setup where it materially reduces runtime cost. It is not treated as platform signing, UID 1000, protected SELinux, MCU/CAN or partition-write authority. The launcher does not poll through `su` or disable protected Topway packages.

A bounded Termux installer/rollback helper is provided at `scripts/termux/install-standalone-launcher.sh`.

## Legacy DoFun theme

The legacy theme retains application/plugin identity `launcher.variety.theme.plugin.sfp_cbk_black` / `sfp_cbk_black`, independent signing and the established clean-room RePlugin compatibility work. The standalone launcher does not depend on it or reuse its identity.

## Development

```bash
python3 tools/ts18_theme.py validate
python3 -m unittest discover -s tests -v

gradle :theme:lintDebug :theme:assembleDebug
gradle :launcher:lintDebug :launcher:testDebugUnitTest :launcher:assembleDebug
```

`launcher:preBuild` fetches and verifies pinned Leaflet 1.9.4 deployment assets. CI also requires an empty launcher `releaseRuntimeClasspath`, builds the signed/minified release, verifies one DEX/no native/Kotlin/AndroidX/RePlugin payload, checks the packaged Material Symbols attribution notice and validates the APK signature.

Successful same-repository PR `Validate` runs refresh the fixed draft **000 Testing Only Version**. Use `TS18-Standalone-Launcher-TESTING.apk` for upgrade-compatible physical testing.

CI proves source/build/release contracts only. It does not prove physical head-unit behaviour.
