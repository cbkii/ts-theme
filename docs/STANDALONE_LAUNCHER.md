# Standalone TS18 launcher

## Status

`launcher/` is the staged Android 10/API 29 HOME successor path. Install and test it as an ordinary Activity before HOME changes. DoFun remains installed/enabled as recovery during qualification. CI is source/build evidence only, never physical TS18 proof.

Exact-device evidence already established the Topway/SystemUI boundaries, basic quick-launch/drawer/navigation hand-off, `com.tw.media` as Auxio-TS, generic Auxio-TS/Spotify MediaSession behaviour and a usable independent NavRadio+ (`com.navimods.radio`) session. Native Topway music/radio remain separately unverified.

## Build/runtime envelope

JDK 17, Gradle 9.5, Android platform 29 and Build Tools 36. The release remains one DEX, no native libraries, no Kotlin/AndroidX/RePlugin runtime and no launcher-owned MediaSession/audio-focus authority.

```bash
gradle :launcher:lintDebug :launcher:testDebugUnitTest :launcher:assembleDebug
```

Successful same-repository PR validation feeds the fixed draft release **000 Testing Only Version**. Use `TS18-Standalone-Launcher-TESTING.apk` for physical testing.

## Automotive HOME UI

Physical safe-area authority remains: panel 1280 x 720, top content boundary 55 px, safe-right x=1225, safe-bottom y=702. Proven device boundaries remain raw pixels.

Mono Drive uses:

- 96 px Driver-side/Left/Right rail;
- Apps fixed at top, 3-6 configurable middle slots, Navigation fixed at bottom;
- Quick 1 defaults to **Settings**, directly below Apps, but remains configurable;
- fixed Navigation uses the selected accent hue;
- 88 px `[Radio controls] [shared active metadata] [Music controls] [DD MMM]` strip;
- independent **Radio / Music sides** setting;
- display-only transparent date; it launches nothing;
- one shared two-level metadata surface;
- stable Previous | Play/Pause | Next ordering and permanently actionable transport targets;
- semantic resource dimensions for inner UI while exact Topway geometry stays physical.

### Shortcut APP / ICON model

Each HOME middle slot and drawer quick slot is one editor card with two labelled halves:

- **APP**: Choose app / Use role default / Change role;
- **ICON**: Auto or a curated visual-only icon override.

Auto displays the real app icon for an explicit app target or the semantic monochrome role icon for a role target. An icon override never changes launch authority. Quick 1's default role is Settings.

All fixed/user-selectable monochrome glyphs use **Google Material Symbols Rounded**, pinned to `google/material-design-icons@40a7a292a79d9394157e1ea24f83d52d5e17c556`, Apache-2.0. Only the bounded catalogue is vendored; no runtime icon dependency is added. See [Icon sources](ICON_SOURCES.md).

### Accent hue

Orange is the default. Settings provides the bounded Material-derived choices Orange, Amber, Lime, Green, Teal, Cyan, Blue, Purple, Pink and Red. The setting changes only the semantic accent role: selected/primary/focus surfaces or strokes, Settings section accents, the fixed Navigation icon and experimental-map location accent. Neutral black/charcoal/white surfaces and typography do not change.

### Media controls and bootstrap

Radio and generic Music remain separate authorities. The Radio source glyph launches the configured/default Radio app; the Music source glyph launches the configured/default Music app. The shared metadata surface may open the source currently being displayed.

Transport buttons are never disabled merely because a session/action has not yet appeared. On a press, the launcher performs one bounded public-API chain:

1. exact active MediaSession for the target package when available and capable;
2. connect to an exported `android.media.browse.MediaBrowserService` and use its controller;
3. if still unavailable, launch the configured source app once and retry the exact package session for at most about 4.5 seconds;
4. show a short failure status instead of silently ignoring or greying the control.

A source that already exposes a session but is not ready is still allowed through the bootstrap path; the existence of a dormant session is not treated as proof the control is impossible.

Settings exposes **Warm media sources on HOME start**. When enabled (default), HOME pre-connects only exported standard MediaBrowser services; it does not foreground-launch their Activities. Disabling it leaves on-demand bootstrap intact.

When Play/Pause is used to start a non-playing Music source, one best-effort Pause is first sent to genuinely playing Radio; starting Radio does the converse. The launcher never requests audio focus.

The selected source owns the shared primary title/station slow marquee and static secondary artist/program/source. The last explicit source resolves simultaneous stale PLAYING claims. Generic music also remembers the last explicitly selected eligible package and retains the visible-only one-second reconciliation fallback from physical Auxio-TS testing.

## Appearance

Auto / Day / High contrast / Dim / Night remain one shared launcher/map appearance authority. Auto may use ambient light or schedule; stale/unavailable sensor evidence falls back to schedule. Generic unset schedule uses 07:00 / 19:00 anchors with bounded transition/high-glare periods; user anchors may cross midnight.

The accent hue is orthogonal to appearance mode: appearance controls neutral luminance hierarchy, hue controls only the semantic accent.

## Experimental HOME map

The Leaflet/WebView surface is retained only as an **experimental physical-test comparator** and is **off by default on clean installs**. Upgrades preserve an already-stored explicit map setting.

When enabled:

- pinned local Leaflet 1.9.4 remains SHA-256 verified;
- exact OSM raster tile requests stay inside native `TileBroker` with bounded HTTPS/cache/revalidation policy;
- renderer recovery, process-local map state and offline-first stale cache behaviour remain available;
- only zoom in, zoom out and follow/recentre remain as map controls;
- those controls are always placed on the **opposite edge from the side rail**;
- Settings can hide them all;
- the duplicate map Navigation action has been removed because Navigation is already the fixed accented rail endpoint.

The raster cache is not a full offline map database and cannot consume Organic Maps/OsmAnd/Google/Yandex offline data. The separate future windowed/native navigation architecture is intentionally outside this PR batch.

## Offline-first policy

Internet availability is optional. Core HOME operation must remain usable without it. Prefer offline-capable navigation/media applications such as Organic Maps, OsmAnd/OsmAnd+, Auxio-TS, Auxio and VLC where appropriate. They remain external authorities rather than launcher dependencies.

## App drawer

The drawer keeps Settings and Close in its header, five configurable quick-access slots, always-visible local search/voice search and a five-column installed-app grid. Drawer quick slots use the same APP/ICON editor semantics as HOME slots.

## Configuration

SAF JSON export/import is versioned, whitelisted and transactional. It includes app/role assignments, slot count, rail and Radio/Music sides, map settings, media mode, startup media warm-up, accent hue, quick-slot icon overrides and appearance settings/schedule. It excludes secrets, caches and location history.

## Read-only physical evidence collector

`scripts/termux/collect-window-media-evidence.sh` is a bounded read-only discriminator for the next physical round and the separate future map-windowing investigation. It captures:

- `wm size` / `wm density` and display state;
- freeform/PiP feature/settings reads;
- DoFun, Organic Maps, launcher, NavRadio+, Auxio-TS/native-music package state;
- task/window/surface information;
- exported MediaBrowser services and active MediaSessions/actions;
- launcher preferences when root-readable;
- bounded relevant logcat.

It does not press playback keys, launch/stop tasks, change settings, alter packages or manipulate windows. Permission/timeout/root failures are BLOCKED/UNKNOWN, not negative evidence.

For the most useful DoFun-window capture, run it while DoFun is visibly hosting Organic Maps:

```bash
bash scripts/termux/collect-window-media-evidence.sh
```

## Physical requalification after this batch

Keep DoFun as HOME and test the launcher as an ordinary Activity first.

1. Verify Apps -> default Settings slot -> other configured slots -> accented Navigation order and rail geometry.
2. Confirm date is inert/transparent and metadata has no hidden Settings shortcut.
3. Configure HOME/drawer slots through the APP and ICON columns; verify Auto versus explicit icon override never changes launch target.
4. Test all accent hues for contrast and ensure only accent-role elements change.
5. Test Radio and Music source glyphs: each must open its configured/default source.
6. With sources stopped/cold, press Play, Previous and Next and record bootstrap latency/result. Controls must remain enabled.
7. Test startup warm-up ON and OFF. ON must not visibly open source Activities at HOME start.
8. Start Music while Radio is genuinely playing and confirm Radio receives best-effort Pause before Music starts; test the reverse direction.
9. Reconfirm shared metadata arbitration when both sessions claim PLAYING.
10. Confirm the experimental map is off on a clean/default configuration. When deliberately enabled, verify no duplicate map Navigation action and that zoom/follow controls sit opposite the rail.
11. Run `collect-window-media-evidence.sh` while DoFun visibly hosts Organic Maps for the separate map architecture thread.
12. Re-run the quantitative protocol in `MONO_DRIVE_UX_ACCEPTANCE.md` plus `measure-standalone-launcher.sh`.
13. Only after ordinary-Activity gates pass continue HOME/reboot/cold-boot/ACC qualification.

Native Topway radio/music, Bluetooth/projection, reverse-camera behaviour and the future windowed navigation architecture remain unclaimed unless separately physically exercised.

## Rollback

DoFun remains installed/enabled throughout qualification. Launcher Settings can disable the HOME candidate. The Termux helper can restore the saved pre-change HOME:

```bash
bash scripts/termux/install-standalone-launcher.sh --rollback-home
```

Rollback must verify the saved HOME is restored and the standalone HOME alias is disabled.
