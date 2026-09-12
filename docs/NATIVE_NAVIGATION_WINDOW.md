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

Android 10 ActivityManager exposes `am task resizeable` and `am task resize`. The latter is explicitly intended to force a task resizable and place it in a stack with the supplied bounds. This PR uses that Android task authority through a narrow Magisk-root helper instead of granting the launcher UID 1000, platform signing or signature permissions.

## Architecture

The map is not a child View of `LauncherActivity`.

`NativeNavigationPanel` is only the launcher geometry/status surface. Its physical bounds come from `getLocationOnScreen()`, width and height after layout.

`NavigationWindowController` owns selection and lifecycle. The configured navigation package remains authority. An observed recent or focused task can never redefine the selected navigator.

`TopwayFreeformBackend` performs bounded asynchronous operations through `NavigationRootHelper`.

`NavigationRootHelper` stages the packaged helper and installs it systemlessly as `/data/adb/ts18-launcher/nav-window.sh` with mode 0700. There is no persistent root process or polling daemon.

The helper uses Android 10 ActivityManager shell operations only. It reads Topway force-PIP properties for diagnostics but never writes them.

## Window transition

For the normal HOME path:

1. Resolve the selected navigation package. If none is explicitly configured and `app.organicmaps.incar` is installed, Organic Maps InCar is the Tier-1 implicit default.
2. Resolve the package's normal launcher Activity.
3. Start it with `ActivityOptions.setLaunchBounds()` using the real HOME navigation-panel rectangle. Do not use `FLAG_ACTIVITY_MULTIPLE_TASK`.
4. The root helper identifies a task whose `TaskRecord` affinity is exactly the configured package.
5. `am task resizeable <task> 2` establishes resizable authority.
6. `am task resize <task> <left> <top> <right> <bottom>` establishes the desired physical rectangle.
7. The helper re-reads ActivityManager state. Success requires the same package-owned task to still exist and its actual `mBounds` to equal the requested rectangle.

If the task is replaced or the bounds differ, the operation fails closed and the launcher exposes a retry/fallback surface.

## Fullscreen handoff

The fixed Navigation rail endpoint remains the normal fullscreen navigation action. The helper first restores the selected task's stack to null/fullscreen bounds with Android 10 `am stack resize-animated <stack> null`, then focuses the task. The launcher subsequently performs the normal package/navigation launch, which is also the universal fallback if the root transition fails.

Returning HOME causes the controller to reconcile the same configured package back into the current HOME rectangle.

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
- HOME -> fullscreen navigation -> HOME;
- Apps drawer -> HOME;
- unrelated app -> HOME without wrong-app takeover;
- navigation-app switching;
- Organic Maps process death;
- launcher Activity/process recreation;
- rail mirroring;
- reverse-camera takeover/return;
- reboot and cold boot;
- ACC sleep/wake.

A failed boundary remains failed or blocked; it is not inferred from CI or another transition.
