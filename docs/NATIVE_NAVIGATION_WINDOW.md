# Native HOME navigation surface qualification

## Purpose

The standalone HOME does not pretend to embed another application's View hierarchy. It qualifies controlled ways of presenting the configured external navigation application over the launcher's map rectangle while preserving deterministic package/task authority and DoFun as recovery HOME.

The navigation-surface preference has four mutually exclusive modes:

- `fullscreen` - safe default and ordinary application fallback;
- `leaflet` - launcher-rendered comparator only;
- `raw_freeform` - Android freeform task experiment;
- `android_pip` - standard Android pinned-task experiment, intentionally separate from Topway's OEM use of the word PIP.

Once `navigation.surface.mode` exists it is the sole navigation-surface authority. The old `map.enabled` boolean is consulted only when migrating an install on which the new key does not yet exist.

## Established exact-device result

Historical physical TS18 evidence already establishes the DoFun navigation shape. A normal Organic Maps Activity task ran on display 0 in Android freeform `windowingMode=5`; the framework concurrently reported the Topway `isPipLauncher :navi` classification. Organic Maps received real compact DecorView/Surface/native geometry and handled its own rendering inside that task.

A separate capture showed Topway force-PIP geometry `524,77,650,376` and an Organic Maps `mLastNonFullscreenBounds=Rect(524,77-1174,453)` with standard Android PiP false.

This proves the working DoFun path ends in an ordinary bounded mode-5 navigation task. It does not prove that a root `am task resize` command alone reproduces every private Topway/DoFun policy decision. The vendor properties/files remain read-only correlation surfaces until an exact writer/consumer contract establishes otherwise.

## Authority model

`NativeNavigationPanel` owns only the intended HOME rectangle and visible status.

`NavigationWindowController` owns:

configured navigation package -> explicitly launched package -> validated task ID -> current same-package top component.

It never adopts the current/focused/recent task merely because that task occupies a suitable stack.

Package-only task acquisition fails closed if more than one matching task exists. A previously validated task can be reused only while the same task ID still belongs to the configured package. Legitimate within-package Activity transitions such as Organic Maps bootstrap/Splash -> `MwmActivity` remain valid because the package is authoritative while the current top component is observed separately.

## Exact Android-10 task parsing

The helper parses the actual TS18 Android-10 hierarchy rather than treating arbitrary nested `mBounds`/`windowingMode` text as task state:

`Display #N -> Stack/RootTask #N -> Task id #N -> task mBounds -> TaskRecord -> Hist #0 top Activity`.

This matters because the exact TS18 dump places the authoritative task `mBounds` before `TaskRecord`; nested Activity configuration can contain different bounds/modes and must not overwrite the task result.

Repository fixtures exercise the production AWK parser against representative exact-format freeform, fullscreen and ambiguous-task snapshots. Successful status/window operations expose `task`, `stack`, `package`, `component`, `display`, `windowingMode`, `bounds` and PiP support where observable. Missing fields remain unknown rather than being invented.

## Raw freeform experiment

The selected package is first launched through its normal exported launcher Activity, with public `ActivityOptions.setLaunchBounds()` as a best-effort initial hint. The helper then reconciles the exact selected task using Android's task resize interface.

Machine-state success requires:

- configured package still owns the expected task ID;
- current top component belongs to that package;
- display is 0;
- `windowingMode=5`;
- actual task bounds equal the current `NativeNavigationPanel` screen rectangle.

A zero exit status from `am task resize` is never sufficient by itself.

If an already-known task exists but mode/bounds drifted, the same task is repaired before any relaunch is considered. Only a genuine `TASK_NOT_FOUND` permits a new selected-package launch. Multiple package tasks produce `TASK_AMBIGUOUS` and fail closed.

## Standard Android PiP experiment

Standard Android PiP is a deliberately separate experiment using real pinned `windowingMode=2`; it is not described as the recovered Topway navigation mechanism.

The backend requires positive PiP support from the selected Activity, refuses to displace a pinned stack owned by another package, and retains exact configured-package/task authority. Android-10 shell stack commands receive the required four integer bounds arguments; the resulting task is then re-read and verified rather than trusting shell success.

If standard PiP can render navigation but cannot provide normal map interaction, classify it as glance-only. Do not broaden privileges or change global force-resizable policy to make PiP behave like freeform.

## Launcher lifecycle and mode switching

Focus is not visibility. A freeform navigation Activity may receive focus while HOME remains visible, so launcher `onPause()` is not a teardown signal.

When HOME actually stops, the controller invalidates/cancels pending helper work but deliberately retains the known package/task identity. HOME return re-reads the real panel rectangle and validates that same task before reacquisition.

Explicit launcher-owned overlays such as the in-HOME app drawer, and an explicit switch away from a task-backed experiment, use a bounded `suspend` operation: validate the exact navigation task, return that task to fullscreen state, validate the exact launcher task, then focus the launcher task. This prevents an old freeform/pinned surface from remaining above Leaflet, fullscreen-only HOME or an in-HOME overlay. It does not force-stop the navigation application.

Switching navigator packages first neutralises the previously authorised task and then revokes its task authority. Switching raw-freeform <-> Android-PiP similarly normalises the same known task before the new backend is allowed to manage it.

Fullscreen handoff uses the same validated task. The helper transitions that task to mode 1 and foregrounds its current same-package top component. The controller does not immediately launch the package a second time; an additional public navigation intent is sent only when a location semantic still needs to be delivered. HOME return resets the handoff state and reconciles the known task again.

## Topway compatibility surfaces remain separate

Three contracts remain distinct:

1. Generic navigation: ordinary third-party navigation Activity/task -> display 0 -> mode 5 -> Topway `isPipLauncher :navi` policy.
2. Current-HOME compatibility: Topway clients can resolve `cn.cardoor.desktop.window.DESKTOP_WINDOW_SERVICE` and read the current HOME's exported provider state.
3. Cooperative floating-window apps: apps such as current `com.tw.video` may themselves export `FLOATING_WINDOW_SERVER` with app-owned window content.

The Video cooperative Binder is not a prerequisite for Organic Maps, Google Maps, OsmAnd/OsmAnd+ or Sygic navigation qualification.

The standalone HOME marker service remains a resolution/capability marker and its exported provider remains read-only. It does not fabricate a DoFun `WindowInfo` while no cooperative Topway window is actually hosted.

## Safety boundary

Normal operation and qualification do not write the observed Topway force-PIP properties/files, assume protected platform identity, alter protected packages/partitions, or install a persistent task-management daemon.

DoFun remains installed and enabled as recovery HOME.

## Physical qualification still required

CI proves source/build/protocol contracts, not physical composition. Exact-device testing must still establish separately for each supported navigator:

- correct configured package and task ID;
- display 0 and expected mode;
- exact real panel bounds;
- map visibly rendered in the HOME rectangle;
- touch works inside the map;
- launcher controls work outside it;
- app-drawer round trip;
- unrelated-app round trip;
- fullscreen -> HOME restoration;
- navigator switching;
- navigation/launcher process recreation;
- reverse-camera takeover/return;
- reboot, cold boot and ACC sleep/wake.

Read-only vendor state may be captured for correlation, but a visibly correct and stable mode-5 result must not be rejected merely because an unproven vendor property differs from historical DoFun state.