# Mono Drive UX acceptance

This document is the acceptance contract for the confirmed TS18 Mono Drive UI package. It does not create a parked-only runtime mode: every launcher feature remains available at all times. The emulator and controlled physical checks below are qualification methods only.

## Source gates and UX targets

Verify the applicable source/CI checks and measure the UX targets separately; this list does not claim every target is automatically asserted:

- the known panel is 1280 x 720; historical conservative bounds are top 55 px, safe-right x=1225 and safe-bottom y=702. Live display/decor/insets are authoritative and fitted insets must not be subtracted twice;
- no primary driver-facing touch target is smaller than 76dp; Mono Drive targets 80dp minimum and 88dp for the primary transport action;
- inner layout spacing is normally 8-12dp, with 16dp section separation where practical;
- functional fixed-control icons come from the pinned Material Symbols Rounded source family documented in `ICON_SOURCES.md`;
- minimum text/icon contrast is 4.5:1 for the defined effective Day and Night colour tokens;
- common driving interactions require no more than two short visible text lines;
- no weather, speed, compass, album-art or general widget surface is added to persistent HOME;
- motion has no spring, bounce, overshoot, scale or parallax animation; configured timings are 120ms state/icon crossfade, 140ms press/focus feedback, 160ms panel/status reveal and 180ms drawer open/close.

## Emulator acceptance - 1280 x 720

Run the current testing APK in an API 29 landscape emulator configured to 1280 x 720. Exercise both rail positions and both independent Radio/Music-side arrangements.

PASS requires all of the following:

1. Topway-equivalent reserved regions remain unobscured in the geometry harness: no interactive content right of x=1225 or above y=55 when full physical bounds are supplied.
2. Apps remains the top rail endpoint, Navigation remains the bottom endpoint and 3, 4, 5 and 6 middle-slot configurations fit without overlap.
3. Radio and Music share exactly one active metadata region: the selected source's primary metadata is one slow-marquee line and secondary context is one static clipped/faded line; neither overlaps either source/control group.
4. Radio/Music sides changes only which group is left/right. It does not mutate rail side, source identity or navigation-task state.
5. Play/Pause icon changes crossfade in 120ms; map/status reveal is 160ms; drawer open/close is 180ms.
6. Source selection/focus is distinguishable without colour. Map follow/camera controls belong to the external navigation app; no retired launcher follow control is required.
7. Text search and voice-search controls remain visible in the drawer; absence of a speech recogniser disables only the mic action.
8. Day and Night screenshots remain legible at normal emulator brightness and retain the defined contrast target. Legacy High contrast/Dim values migrate to Day/Night.

9. Repeated HOME intents close the drawer, clear search/keyboard state and restore the dashboard without resetting media selection or the valid external map task/route.
10. HOME/drawer/media controls remain responsive without network; offline navigation belongs to the configured app and its installed maps. No launcher tile cache/WebView is present.
11. Task/window recovery is bounded and preserves healthy navigation and legitimate permissions. A missing/blocked map does not block HOME, drawer or Settings; pixels/touch need separate qualification.

Record screenshots for Right rail + Right media controls, Right rail + Left media controls, Left rail + Right media controls, Day and Night.

## Physical TS18 acceptance

Use `TS18-Standalone-Launcher-TESTING.apk` as an ordinary Activity while DoFun remains the recovery HOME. Record failures rather than inferring a pass from emulator/CI results.

### Common-task interaction targets

For at least 20 repetitions per action after one familiarisation pass:

| Task | Pass criterion |
| --- | --- |
| Recognise the intended common control | median glance <= 1.0 s |
| Open configured Navigation from HOME | 1 tap; >=98% correct target |
| Play/Pause selected media | 1 tap; >=98% correct target |
| Launch an app through Apps drawer | <=2 taps after Apps opens; >=98% correct target |
| Press any primary HOME control | first visible response begins <100 ms in >=95% of trials |
| Complete a common HOME action | 95th percentile <=2.0 s, excluding external app launch/load time |
| Wrong target / accidental adjacent action | <2% across the common-action sample |

Measure glance/task timing from video at 60fps or better where practical. If video timing is unavailable, mark the metric BLOCKED rather than estimating it.

### Reachability and controls

- test rail Left and Right independently from media controls Left and Right;
- from the normal driver posture, every visible primary control must be reachable without leaning across the display or contacting an adjacent target;
- verify Previous -> Play/Pause -> Next ordering remains stable for both media-cluster sides;
- verify focus/DPAD movement follows the explicit rail, transport and drawer focus graphs if the TS18 input surface emits those navigation events;
- transport targets keep stable positions and accept the original intent through supported preparation; a missing contract yields a truthful bounded failure rather than a silent disabled first tap;
- voice search must fall back cleanly to touch/keyboard search if the installed recogniser is missing or fails.

### Appearance and high-sun/night checks

Test explicit Day and Night plus scheduled Auto. Verify legacy High contrast/Dim/Sensor imports resolve to Day/Night/Schedule. Launcher chrome updates in place without recreating HOME or its map task; the external map app retains independent appearance authority. Check readable metadata and non-colour selection/focus cues.

Check the 07:00/19:00 defaults, custom anchors, overnight schedules, rejected equal anchors, local clock/time-zone changes and return from Settings. There is no lux-sensor or shared Leaflet appearance requirement.

### Performance evidence

After a settled map session, use `scripts/termux/measure-standalone-launcher.sh`. Compare with the immediately preceding qualified launcher candidate on the same unit/state where possible.

PASS requires:

- no sustained CPU regression greater than 10 percentage points attributable to the launcher during an idle settled-map sample;
- no launcher PSS regression greater than 25% without an explained feature cost;
- no repeated launcher-driven map task creation/restart or geometry churn while a healthy map is stationary or receiving ordinary location updates;
- no recurring frame stalls that produce visible control/drawer animation hitching in the captured frame statistics.

If a prior equivalent baseline is unavailable, capture the numbers and mark comparative thresholds BASELINE REQUIRED rather than manufacturing a pass.

## Completion record

A merge-ready repository state requires all static/CI gates green. Emulator/physical criteria remain explicit qualification boundaries: they may be PENDING without invalidating source correctness, but must never be recorded as PASS until actually exercised. HOME assignment, reboot/cold boot, ACC sleep/wake, native Topway music/radio and other exact-device lifecycle checks remain separate gates in `STANDALONE_LAUNCHER.md`.
