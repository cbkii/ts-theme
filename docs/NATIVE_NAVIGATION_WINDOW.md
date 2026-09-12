# Native navigation window

This document defines the standalone launcher's experimental native HOME navigation implementation for CB's exact Topway TS18. It is deliberately separate from the Leaflet/WebView comparator retained by PR #10.

## Exact-device evidence

The 2026-09-12 physical-state capture established a concrete bounded-task mechanism on the exact Android 10/API 29 unit:

- `persist.tw.forcepip=1`;
- `sys.tw.forcepip.x=524`, `y=77`, `w=650`, `h=376`;
- Organic Maps InCar task `#9681` recorded `mLastNonFullscreenBounds=Rect(524,77 - 1174,453)`, exactly the same 650 x 376 rectangle;
- that task recorded `RESIZE_MODE_RESIZEABLE` and `isResizeable=true`;
- it also recorded `mSupportsPictureInPicture=false` and `mLastReportedPictureInPictureMode=false`.

The proven surface is therefore a real resizable Android task under the Topway window policy, not standard Android picture-in-picture.

Android 10 ActivityManager exposes `am task resizeable` and `am task resize`. The latter is explicitly intended to force a task resizable and place it in a stack with the supplied bounds. Android 10 activity-start options also expose shell `--task` and `--windowingMode`, which lets the root helper request a known task in fullscreen mode without resizing every task in a shared freeform stack. This PR uses that Android task authority through a narrow Magisk-root helper instead of granting the launcher UID 1000, platform signing or signature permissions.

## Architecture

The map is not a child View of `LauncherActivity`.

`NativeNavigationPanel` is only the launcher geometry/status surface. Its physical bounds come from `getLocationOnScreen()`, width and height after layout.

`NavigationWindowController` owns selection and lifecycle. The configured navigation package remains authority. After the first successful window operation it also records the exact task ID; subsequent verify, focus and fullscreen operations require package plus task ID. An observed recent or focused task can never redefine the selected navigator.

`TopwayFreeformBackend` performs bounded asynchronous operations through `NavigationRootHelper`.

`NavigationRootHelper` stages the packaged helper and installs it systemlessly as `/data/adb/ts18-launcher/nav-window.sh` with mode 0700. There is no persistent root process or polling daemon.

The helper uses Android 10 ActivityManager shell operations only. It reads Topway force-PIP properties for diagnostics but never writes them.

## Window transition

For the normal HOME path:

1. Resolve the selected navigation package. If none is explicitly configured and `app.organicmaps.incar` is installed, Organic Maps InCar is the Tier-1 implicit default.
2. Resolve the package's normal launcher Activity.
3. Start it with `ActivityOptions.setLaunchBounds()` using the real HOME navigation-panel rectangle. Do not use `FLAG_ACTIVITY_MULTIPLE_TASK`.
4. On first acquisition only, the root helper identifies a task whose `TaskRecord` affinity is exactly the configured package. Once validated, the launcher records that task ID.
5. `am task resizeable <task> 2` establishes resizable authority.
6. `am task resize <task> <left> <top> <right> <bottom>` establishes the desired physical rectangle.
7. The helper re-reads ActivityManager state. Success requires the same package/task pair to still exist and its actual `mBounds` to equal the requested rectangle.
8. Later restores validate the recorded task first. A missing recorded task is reacquired only after an explicit package launch/reconciliation, never simply because another task became recent or focused.

If the task is replaced or the bounds differ, the operation fails closed and the launcher exposes a retry/fallback surface.

## Fullscreen handoff

The fixed Navigation rail endpoint remains the normal fullscreen navigation action. For a validated task, the helper resolves the current top Activity from that exact task and invokes Android 10 ActivityManager with `--windowingMode 1 --task <taskId>` plus `FLAG_ACTIVITY_SINGLE_TOP`. This requests true fullscreen mode for the existing task without resizing an entire freeform stack or deliberately creating another task. The helper verifies that the same package/task pair remains and that the TaskRecord bounds clear to the Android-10 fullscreen state.

The launcher then performs the normal package/navigation launch as the universal public fallback and to carry any requested location/deep-link data. Returning HOME clears the intentional fullscreen handoff state and reconciles the same selected task back into the current HOME rectangle.

## Leaflet comparator

The existing `Experimental Map` preference retains the Leaflet/WebView path from PR #10. When enabled, the native navigation placeholder is hidden and the launcher brings its own task forward before presenting Leaflet. Leaflet remains off by default on clean installs.

When Experimental Map is disabled, the launcher does not create the Leaflet WebView, start TileBroker or request GPS on Leaflet's behalf.

## Authority and safeguards

The implementation does not:

- platform-sign the launcher or request `android.uid.system`;
- use ActivityView/TaskView hidden APIs;
- call standard Android PiP as the primary mechanism;
- write `persist.tw.*` or `sys.tw.*` properties;
- patch WindowManager/framework;
- modify system/vendor partitions;
- clear or disable DoFun/Topway packages;
- disable SELinux;
- force-stop navigation apps as normal lifecycle behaviour;
- run a permanent root daemon.

DoFun remains installed and enabled as recovery HOME.

## Physical qualification

CI validates source/build/package contracts only. The exact TS18 must separately prove:

- first HOME launch with Organic Maps already running and not running;
- fully offline Organic Maps rendering;
- pan/zoom/search/navigation touch inside the window;
- rail/media controls outside the map;
- exact task bounds and SystemUI safe area;
- repeated HOME presses;
- HOME -> fullscreen navigation -> HOME with the same validated task where the platform permits it;
- Apps drawer -> HOME;
- unrelated app -> HOME without wrong-app takeover;
- navigation-app switching;
- Organic Maps process death and deliberate task reacquisition;
- launcher Activity/process recreation;
- rail mirroring;
- reverse-camera takeover/return;
- reboot and cold boot;
- ACC sleep/wake.

A failed boundary remains failed or blocked; it is not inferred from CI or another transition.
