# Binary Day/Night implementation and qualification

Owner decisions of 20 October 2026 supersede the previous five-mode appearance roadmap.
Source base: PR #27 `11ec69a0c53f723cca0c046a255ddf0f4322b006`, including PR #26.
Target: confirmed TS18 Android 10/API29, 1280×720 landscape touchscreen, no lux sensor.

## Behaviour contract

- Exactly two effective appearances: true light, high-contrast Day and dark Night.
- Scheduled is the clean-install default. Dawn is inclusive; dusk immediately starts Night.
  Default anchors are 07:00 and 19:00 local time. Overnight and short day windows are valid.
- Equal anchors are rejected, including imports where the other anchor uses its default.
  Already-corrupt equal stored anchors resolve with the default schedule; Settings and exports
  use the same effective anchors, so the displayed/portable schedule agrees with the resolver.
- Manual Day and Night remain authoritative. Legacy `high_contrast` maps to Day;
  legacy `dim` maps to Night. Legacy sensor selection resolves to Schedule.
- One process-owned foreground controller caches the effective mode. HOME, Settings and
  the app picker subscribe only while started. The final subscriber removes the receiver,
  preference listener and timer. Time/time-zone/screen-on and foreground resume reconcile.
- Ordinary theme changes recolour existing screens; HOME and its map/task are not recreated.
  Drawer refreshes preserve primary/secondary labels, search icons and actual app identity.
- Day uses light surfaces and dark text/glyphs, including the rail and source controls.
  Bright accent fills retain black glyphs; accent-only foregrounds use a darker tone.
  Framework dialogs and all three shortcut-editor popups share the local policy and
  live appearance updates, preserving Auto app identity and selected icons. An open time picker is rebuilt on transition
  with its selected hour/minute retained, so framework internals use the correct theme.
- Organic Maps owns its policy and renderer independently. No cross-package IPC, global
  night-mode write, root theme injection or shared setting is introduced.

## Verification

Production-owner host test: two foreground subscribers share one listener/receiver/timer;
no redundant colour-resolution preference reads; stopped observers receive no updates;
manual changes, restart and all 1,440 schedule minutes are exercised. JVM tests cover
inclusive/exclusive, overnight, short, equal and partial-default anchors and legacy modes.
Source contracts cover semantic drawer roles. Canonical Android builds/lint/JVM/release
checks must be attached to the final PR head; host stubs do not verify Android rendering.

## Physical acceptance — NOT_RUN

1. Day ↔ Night on HOME, Settings and picker, with and without an open dialog/time picker.
2. Both exact anchors; clock/time-zone change; ACC suspend/wake; process recreation.
3. Day glare readability of rail, music/radio metadata, selected/inactive and disabled controls;
   Night glare/brightness. Confirm no layout or actual app-icon identity regression.
4. Compact/fullscreen Maps and running Music/Radio retain their existing task/playback ownership.
5. Compare startup, frame timing and memory on the same exact APK/workload. No measured
   speed, CPU, GPU or memory improvement is claimed by source/host checks.

## Roadmap

- Now: binary resolver, schedule validation, shared state, in-place propagation, semantic drawer
  recolouring, real light Day and local dialogs (this patch).
- Before device acceptance: final-head canonical CI and the physical cases above.
- Future only: validated hardware with a lux sensor, then hysteresis/dwell and a schedule fallback.
- Deferred by owner: Leaflet tile/marker filtering and effective-theme status/next-transition UI.
  The experimental map asset remains unchanged. No new feature is inferred from blank item 10.
