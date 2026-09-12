# Native navigation window

This document defines the standalone launcher's native HOME navigation implementation for CB's exact Topway TS18. It is deliberately separate from the Leaflet/WebView comparator retained by PR #10.

## Exact-device evidence

The 2026-09-12 physical-state capture established a concrete bounded-task mechanism on the exact Android 10/API 29 unit:

- `persist.tw.forcepip=1`;
- `sys.tw.forcepip.x=524`, `y=77`, `w=650`, `h=376`;
- Organic Maps InCar task `#9681` recorded `mLastNonFullscreenBounds=Rect(524,77 - 1174,453)`, exactly the same 650 x 376 rectangle;
- that task recorded `RESIZE_MODE_RESIZEABLE` and `isResizeable=true`;
- it also recorded `mSupportsPictureInPicture=false` and `mLastReportedPictureInPictureMode=false`.

The proven surface is therefore a real resizable Android task under the Topway window policy, not standard Android picture-in-picture.

A second exact evidence source is the pinned current `com.tw.video` APK, `TW_THEME.20241022` / versionCode 119, SHA-256 `07c37275f86c495f8e62a23bcfae32e75674f4ad30ccdbc269f25235cc159562`. Its ordinary DEX exposes the Topway desktop/floating-window library and corrects the earlier assumption that the HOME `DESKTOP_WINDOW_SERVICE` itself supplies `WindowInfo` over Binder.

## Two distinct Topway window contracts

The recovered Video client establishes two separate contracts which must not be collapsed into one.

### HOME discovery/configuration surface

The current HOME is package-scoped queried for a service resolving:

`cn.cardoor.desktop.window.DESKTOP_WINDOW_SERVICE`

In the exact Video path this is a capability marker. The client does not bind that service. It then queries the current HOME's provider authority:

`<HOME_PACKAGE>.ExportedProvider`

using these exact URIs/keys:

- `/kv_config/desktop_window_setting`;
- `/kv_config/isCurrentUsedThemeInstanceDesktopWindow`.

`com.cbkii.ts18launcher` therefore exposes a resolution-only marker service plus a read-only `com.cbkii.ts18launcher.ExportedProvider`. Until an actual cooperative Topway floating window is hosted, `desktop_window_setting` intentionally returns no row and the theme flag returns `false`. The launcher does not fabricate DoFun's still-unknown `windowName` or host-selection policy merely to manufacture a non-null `WindowInfo`.

### Cooperative app floating-window Binder

`com.tw.video` itself, not HOME, exports:

`cn.cardoor.desktop.window.floating.intent.action.FLOATING_WINDOW_SERVER`

The returned master/controller Binder lets a desktop host command an app-owned `IWindowView` such as `com.tw.video.CustomWindowView`. This is useful for cooperative Topway apps, but it is not a universal navigation-app transport.

CB has separately reported that **Organic Maps, Google Maps, OsmAnd+ and Sygic all work well in the DoFun HOME and none implements this Topway floating-window contract**. That is user-observed behaviour of the exact environment; it does not by itself reveal DoFun's internal implementation. It is nevertheless decisive contrary evidence against making `FLOATING_WINDOW_SERVER` a prerequisite for the normal navigation surface.

Accordingly PR #11 keeps the generic validated bounded-task lane for ordinary navigation apps and treats the recovered HOME marker/provider plus cooperative Binder as a separate OEM compatibility lane.

See `docs/TOPWAY_DESKTOP_WINDOW_CONTRACT.md` for the recovered wire-level boundary.

## Generic navigation architecture

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

This is an implementation of the currently proven Android task authority, not a claim that DoFun internally invokes these exact shell commands. Physical qualification must establish whether the same apps retain the quality, touch and lifecycle behaviour observed under DoFun.

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
- require `FLOATING_WINDOW_SERVER` from ordinary navigation apps;
- invent a Binder implementation for the HOME marker service;
- publish a guessed DoFun `WindowInfo`/`windowName`;
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
- Google Maps, OsmAnd+ and Sygic windowing as separate compatibility cases rather than inferred from Organic Maps;
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
- current `com.tw.video` no longer logging that the replacement HOME lacks `DESKTOP_WINDOW_SERVICE`;
- current `com.tw.video` querying the replacement HOME provider without crash while the provider reports the safe inactive state;
- reverse-camera takeover/return;
- reboot and cold boot;
- ACC sleep/wake.

A failed boundary remains failed or blocked; it is not inferred from CI or another transition.
