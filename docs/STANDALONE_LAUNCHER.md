# Standalone TS18 launcher

## Status

`launcher/` is the staged Android 10/API 29 HOME successor path. Install/test it as an ordinary Activity before HOME changes. DoFun remains installed/enabled as recovery during qualification. CI is source/build evidence only, never physical TS18 proof.

Exact-device evidence already established the Topway/SystemUI boundaries, basic quick-launch/drawer/navigation hand-off, `com.tw.media` as Auxio-TS, generic Auxio-TS/Spotify MediaSession behaviour and a usable independent NavRadio+ (`com.navimods.radio`) session. Native Topway music/radio remain separately unverified.

## Build/runtime envelope

JDK 17, Gradle 9.5, Android platform 29 and Build Tools 36. The release remains one DEX, no native libraries, no Kotlin/AndroidX/RePlugin runtime and no launcher-owned MediaSession/audio-focus authority.

```bash
gradle :launcher:lintDebug :launcher:testDebugUnitTest :launcher:assembleDebug
```

## Testing-build workflow

The fixed draft release **000 Testing Only Version** is an explicit engineering/physical-validation channel, not an automatic output of every successful PR validation. A TESTING snapshot can be requested by:

1. adding the `testing-apk` label to a PR;
2. putting `/testing-apk` in the newest PR commit message;
3. commenting `/testing-apk` in the PR conversation;
4. manually running **Refresh Testing APK Draft** from the default branch and setting `source_to_build` to a PR number, branch, tag or commit SHA.

Commit/comment requests idempotently ensure the PR carries the `testing-apk` label. The label is visible state, not permission for every later commit to overwrite the draft; later snapshots still require another explicit request.

The workflow builds the exact requested repository source with read-only permissions, then hands only the APK/metadata artifact to the signing publisher. The release-envelope checker remains release-only; the debug APK is not required to satisfy the one-DEX release contract. Manual arbitrary-source Gradle caching is read-only. PR conversation comments are handled from the default-branch workflow definition; inline review-thread comments are intentionally not a signing trigger.

The draft retains the **two newest successfully published snapshot groups**. Each group has uniquely named TESTING and DEBUG APKs plus BUILD_INFO, signer and checksum metadata. The release notes are authoritative for the snapshot PR, PR head, base at request, actual built SHA, trigger, actor and workflow run. If a PR has moved since an older snapshot, the notes show both the snapshot head and the current PR head without invalidating the older APK.

All TESTING APKs deliberately retain `versionName=0.0.0-testing` and `versionCode=999999` so they stay in one reinstall/update lane. Commits predating the standalone launcher naturally cannot produce its APK.

Request a TESTING snapshot at a meaningful install/device-test checkpoint, especially before exact TS18 physical validation of changed map, media/radio, HOME/lifecycle, SystemUI/geometry, root/integration or OEM-specific behaviour; when reproducing a device-only defect; when handing a build to a physical tester; or before a risky follow-up change where preserving the current known build is useful. Docs-only or incidental changes normally need only Validate.

## Automotive HOME UI

Physical safe-area authority remains: panel 1280 x 720, top content boundary 55 px, safe-right x=1225, safe-bottom y=702. Proven device boundaries remain raw pixels.

Mono Drive uses:

- 96 px Driver-side/Left/Right rail;
- Apps fixed at top, optional 3-6 configurable middle slots, Navigation fixed at bottom;
- Quick 1 defaults to Settings while all middle slots remain configurable;
- a Settings switch can hide the entire HOME shortcut section; when hidden its count/editor controls also disappear from Settings;
- visible shortcut cells divide the available middle rail height evenly according to active count; they are not permanently compressed for six;
- fixed Navigation uses the selected accent hue;
- 88 px `[Radio controls] [shared active metadata] [Music controls] [DD MMM]` strip;
- compact 128 px display-only date;
- independent Radio/Music side setting;
- one shared two-level metadata surface;
- stable Previous | Play/Pause | Next ordering and permanently actionable transport targets.

### Media visual hierarchy

Only the selected/last-explicit source receives the strongest primary treatment, but the inactive source remains visually available rather than disabled:

- source groups use restrained tonal gradients pointing inward toward shared metadata;
- selected source receives the stronger accent edge/outline and filled accent Play/Pause;
- inactive source retains a subtle accent gradient/ring and accent cue;
- source icon tint provides an additional selected/inactive distinction.

The result is deliberately asymmetric hierarchy without making either source look unavailable.

### Shortcut App / Icon model

Each HOME middle slot and drawer quick slot is one editor card with two labelled halves:

- **App**: Choose app / Use role default / Change role;
- **Icon**: Auto or a curated visual-only icon override.

Auto displays the actual installed application icon, untinted, for an explicit app target. A role shortcut uses its semantic monochrome role icon. Explicit Icon overrides are Material Symbols Rounded and never alter launch authority. Settings preview and HOME/drawer presentation follow the same identity rule.

All fixed/user-selectable monochrome glyphs use **Google Material Symbols Rounded**, pinned to `google/material-design-icons@40a7a292a79d9394157e1ea24f83d52d5e17c556`, Apache-2.0. Only the bounded catalogue is vendored.

### Accent hue

Accent is orthogonal to Day/Night appearance. Orange is the default; Settings presents a compact strip of colour circles for Orange, Amber, Lime, Green, Teal, Cyan, Blue, Purple, Pink and Red. Hue changes only semantic accent-role elements. Neutral black/charcoal/white hierarchy does not change.

No first-run wizard is currently planned. The default Settings quick slot plus sensible defaults remain the onboarding model unless physical/user evidence shows a discoverability failure.

### Media controls and bootstrap

Radio and generic Music remain separate authorities. Transport buttons are never disabled merely because a session/action has not appeared. A press performs one bounded background-only chain:

1. exact active MediaSession for the target package when available/capable;
2. evidence-backed per-source adapter;
3. bounded Magisk-root service activation first for exact Auxio-TS, followed by its exported MediaBrowser wrapper and ordinary binding fallback;
4. bounded root-first NavRadio service activation only for an interactive Play request while passive service start remains physically unqualified;
5. exported `android.media.browse.MediaBrowserService` for other compatible sources;
6. bounded exact-session retry, one directional command, playback acknowledgement for Play/Pause, and a short visible failure status.

No readiness/transport path launches a source Activity. Settings exposes **Warm media sources on HOME start**. When enabled, HOME reconciles readiness on start/resume/focus only through passively qualified adapters. Exact stock `com.tw.radio` is existing-session/external-route only because its current APK declares no service component. The launcher creates no MediaSession and never requests audio focus.

A Play/Pause intent is resolved once at tap time and retained through preparation. Duplicate in-flight Play/Pause intents are coalesced under one monotonic deadline. Preparing a new source does not stop the currently working opposite source; the opposite source is paused only after the requested source has acknowledged Play. Failed source switches reconcile the displayed source to actual playback rather than leaving a stale selected-source state. Generic Music retains the visible-only one-second reconciliation fallback proven useful in physical Auxio-TS testing.

MediaBrowser-acquired controllers feed the same metadata pipeline as active-session controllers and are deduplicated by real `MediaSession.Token`. Empty/whitespace title/artist fields fall through to valid display title/subtitle values. Identical one-second snapshots do not restart the slow marquee's five-second stationary hold.

## Appearance

Auto / Day / High contrast / Dim / Night remain one shared launcher/map appearance authority. Auto may use ambient light or schedule; stale/unavailable sensor evidence falls back to schedule. Generic unset schedule uses 07:00 / 19:00 anchors with bounded transition/high-glare periods; user anchors may cross midnight.

Physical validation should precede any further typography/icon/touch-target retuning. Capture exact TS18 `wm size`, `wm density`, `densityDpi` and relevant display metrics first.

## Exact-device responsiveness

Do not implement generic compact/split-screen profiles. Supported responsive reasoning is limited to:

- exact 1280x720 physical panel;
- decor-fitted Activity bounds;
- Topway right-sidebar shown/hidden geometry when exact runtime evidence discriminates it;
- modest comparable landscape variation that preserves SystemUI safety and touch targets.

The proven 1225 px safe-right remains the default full-panel authority while the right SystemUI sidebar is present.

## Experimental HOME map

Leaflet/WebView is retained only as an **experimental physical-test comparator** and is off by default on clean installs. Upgrades preserve an explicit stored map preference. No more Leaflet presentation work is part of PR #10.

When enabled, pinned Leaflet/TileBroker restrictions, renderer recovery, process-local state and bounded cache/revalidation remain. Only zoom/follow controls remain, opposite the side rail. The duplicate map Navigation action is removed.

The raster cache is not a full offline navigation database and cannot consume Organic Maps/OsmAnd/Google/Yandex offline data. A polished `Map unavailable`/retry placeholder is a **future consideration only if physical testing proves the experimental WebView approach worth retaining**. Otherwise the separate map/windowing architecture thread owns replacement.

## Repository privacy/offline policy

`ts-theme` is **local, offline and private by default**. Core HOME features must remain useful without Internet access. Do not add remote analytics, tracking, remote configuration, cloud logs or automatic user-behaviour telemetry by default. Diagnostics remain bounded, local/exportable and user-controlled.

Prefer offline-capable navigation/media applications such as Organic Maps, OsmAnd/OsmAnd+, Auxio-TS, Auxio and VLC where appropriate; they remain external authorities rather than dependencies.

## App drawer

The drawer keeps Settings/Close, five configurable quick-access slots, always-visible local search/voice search and a five-column installed-app grid. Drawer shortcuts use the same App/Icon semantics as HOME slots.

## Configuration

SAF JSON export/import is versioned, whitelisted and transactional. It includes app/role assignments, HOME shortcut visibility/count, rail and Radio/Music sides, map settings, media mode, startup warm-up, accent hue, icon overrides and appearance settings/schedule. It excludes secrets, caches and location history.

## Read-only evidence collectors

`scripts/termux/collect-window-media-evidence.sh` is a bounded read-only discriminator for physical validation and the separate map-windowing investigation. It captures `wm size`/`wm density`, display/window/task state, package/services, MediaBrowser/MediaSession state, optional root-readable launcher preferences and bounded relevant logs. It does not mutate settings, packages, tasks, playback or windows.

`scripts/termux/collect-fast-media-evidence.sh` adds bounded source/user/root-domain/service/session readiness capture plus a generated physical fast-media playbook under `/storage/emulated/0/Download/ts-theme/`. PASS/FAIL/BLOCKED/UNVERIFIED status is kept separate from physical audible-onset observations.

## Physical requalification

Keep DoFun as HOME and test the launcher as an ordinary Activity first. Before beginning a new physical-validation round, request `/testing-apk` (or use another explicit TESTING trigger) on the exact commit you intend to install, then use the fixed draft release notes to confirm the APK's PR/head/built SHA before copying it to the TS18.

1. Verify Apps -> default Settings slot -> configured middle shortcuts -> accented Navigation, including 3/4/5/6 slot spacing and shortcuts-disabled mode.
2. Confirm Navigation remains fixed at bottom and visible slots use the available middle rail height evenly.
3. Confirm date is inert/transparent and the 128 px allocation improves metadata width without clipping.
4. Verify selected source gets stronger gradient/accent while inactive source retains subtle accent and remains immediately actionable.
5. Test Radio/Music sides independently from rail side.
6. Configure HOME/drawer slots through App/Icon; verify explicit apps show real untinted icons for App and Auto previews, while overrides remain monochrome Material Symbols.
7. Test all accent colour circles and confirm only semantic accent elements change.
8. Test cold/background one-tap media readiness, duplicate Play/Pause coalescing, deferred opposite-source pause, startup warm-up ON/OFF and shared metadata/marquee arbitration.
9. Capture exact `wm size`, `wm density`, `densityDpi` before proposing further typography/icon/target changes.
10. Validate full physical and decor-fitted/right-sidebar geometry. Do not infer generic split-screen support.
11. Confirm experimental Leaflet remains off by default; do not score future placeholder work unless the WebView itself is retained.
12. Run `collect-fast-media-evidence.sh`, `MONO_DRIVE_UX_ACCEPTANCE.md` and `measure-standalone-launcher.sh` where applicable.
13. Only after ordinary-Activity gates pass continue HOME/reboot/cold-boot/ACC qualification.

NavRadio passive warm-up, a stock TW Radio external control route and any masked Activity fallback remain unqualified until exact physical evidence establishes those contracts. Native Topway radio/music, Bluetooth/projection, reverse-camera behaviour and future windowed navigation remain unclaimed unless separately exercised.

## Rollback

DoFun remains installed/enabled throughout qualification. Launcher Settings can disable the HOME candidate. The Termux helper can restore the saved pre-change HOME:

```bash
bash scripts/termux/install-standalone-launcher.sh --rollback-home
```
