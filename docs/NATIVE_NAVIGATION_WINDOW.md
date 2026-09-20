# Native HOME navigation window

## Product contract

The standalone launcher does not copy or mirror another application's map. It presents the configured navigation application's real Android task over the launcher-owned map rectangle.

The normal HOME choices are:

- `native_window` - primary TESTING path: display 0, Android freeform `windowingMode=5`, exact live `NativeNavigationPanel` bounds;
- `fullscreen` - explicit safe fallback;
- `leaflet` - legacy online-only last-resort fallback, never automatic.

Historical `raw_freeform` preferences migrate to `native_window`. Standard Android PiP is no longer a selectable or automatic navigation path.

## Exact-device authority

A known-good DoFun/Organic Maps capture on CB's TS18 established a normal Organic Maps task on display 0 in mode 5 at compact bounds. Topway concurrently reported `isPipLauncher ... :navi`, and Organic Maps received real compact Activity/Decor/Surface dimensions. Standard Android PiP was false.

This proves the required task/window shape. It does not prove that raw Android mode 5 alone reproduces every private Topway policy decision. The current TESTING build must first reproduce the Android machine state before any OEM compatibility layer is considered.

Physical TESTING attempt 2 stopped before mode 5: the exact OEM `am help` output advertised `--display` and `--windowingMode` but returned exit 255, and the previous helper treated that exit as unsupported. **Open fullscreen** separately created one resizeable Organic Maps task on display 0 and retained the intended compact non-fullscreen bounds, but this does not qualify mode 5. The corrected helper records the help exit and exact advertised flags separately; the actual launch plus state readback remains authoritative.

## Deterministic transaction

`NativeNavigationPanel` owns the target rectangle in physical screen coordinates. Java resolves the configured package's ordinary exported launcher Activity and passes package, component, display 0, bounds and transaction ID to the narrow root helper.

The helper owns one bounded `present-native` transaction:

1. inspect same-package tasks;
2. adopt exactly one existing task without creating another task;
3. fail closed if multiple pre-existing tasks are ambiguous;
4. only when no task exists, start the resolved ordinary launcher component once with explicit display 0 and `windowingMode=5`;
5. acquire the resulting exact task;
6. for an existing fullscreen task, request mode 5 with `am start --task <id> --windowingMode 5` using its observed same-package component, then verify the same ID/mode before resizing; mark it resizeable, apply the panel rectangle, focus and read state back;
7. accept task configuration only for the configured package, exact task, display 0, mode 5 and exact bounds;
8. keep physical visibility, z-order and touch qualification separate rather than inferring them from task geometry.

The launcher never selects a task because it is focused, recent or frontmost. Organic Maps bootstrap transitions such as `DownloadResourcesActivity -> MwmActivity` are valid while the same package/task remains authoritative.

## Exact Android-10 parsing

The helper follows the real hierarchy:

`Display -> Stack/RootTask -> Task id -> task mBounds -> TaskRecord -> Hist #0 ActivityRecord`

It reads the top component directly from the exact physical `Hist #0: ActivityRecord{... package/component ...}` line, with or without the optional leading `*`, and uses its following `mActivityComponent=` only as a fallback. Only the genuine top-level `* TaskRecord{...}` after `Task id` establishes task identity. Nested Activity `task=TaskRecord{...}` references cannot reinitialise the selected task. Shorthand same-package components are normalised, and nested Activity configuration bounds never override task/root-task bounds.

An unobservable component is `unknown`, not `COMPONENT_MISMATCH`. Package plus exact task ID is sufficient for task-only resize and verification. A positively observed foreign top component fails closed.

## Lifecycle and failure rules

Only one helper operation may be in flight. Bounds and lifecycle callbacks coalesce behind it; one authority generation can make at most one package-only acquisition attempt. A transient HOME stop during freeform launch/convergence does not invalidate the transaction or destroy the backend.

HOME return validates the same authorised task first. Genuine task disappearance starts a new bounded reacquisition generation. App drawer, Settings and unrelated tasks never become navigation authority.

The 2026-09-20 captures establish bounded rendering on PR11-5aea8bc. They also expose a suspension bug (HOME component validated against the map package) and failed warm resizing. Both task authorities are now checked before suspension; each component is checked against the package being inspected. The drawer is delivered only after suspension completes and HOME is visible. HOME quick/source Activity launches use the same barrier, and repeated requests cannot schedule a late second suspension after the app launch. Explicit HOME/fullscreen cancels an unshown drawer request. Once this package is default HOME, ordinary app entry redirects to HomeAlias before creating a second controller.

The helper retains bounded failed-command output in its response/log. The probe uses Android-side deadlines and completion sentinels, clears Termux loader variables, captures rolling lifecycle logs, and records the timestamp of any last-valid final sample. These mechanisms have host behavioural tests; exact TS18 lifecycle and touch qualification remains required. No cache clearing or force-stop is a recovery strategy.

Failure is latched for the current package/mode/generation. HOME remains usable and does not automatically launch navigation again. The user receives separate **Retry** and **Open fullscreen** actions. Retry starts one new generation; fullscreen is always explicit.

Fullscreen handoff uses the same task and its observed same-package top component where available. HOME return reapplies mode 5 and current bounds to that task. The helper never force-stops the navigator and no persistent root daemon is installed.

## Separate Topway contracts

Keep these independent:

1. ordinary navigation task on display 0 in mode 5 plus any Topway `:navi` policy;
2. current-HOME `DESKTOP_WINDOW_SERVICE` marker/read-only provider compatibility;
3. Video-like cooperative `FLOATING_WINDOW_SERVER` app-owned windows.

The launcher observes but does not write force-PIP properties, `sys.df.*`, `/data/tw/navi_name` or `/data/tw/custom_pip_app_name`. It does not fabricate Video/DoFun `WindowInfo` state.

## Qualification

CI validates source, parsing, lifecycle contracts and the APK envelope only. Use `NAVIGATION_WINDOW_PHYSICAL_PLAYBOOK.md` with `scripts/termux/collect-navigation-window-evidence.sh` for the exact TS18 result. The OEM-policy branch in `NAVIGATION_SURFACE_ROADMAP.md` is gated on that physical evidence.

The collector first records focused task/window/input/surface transitions. After the user ends observation it runs a separate broad discovery phase for alternate implementation routes and unexpected OEM surfaces. This avoids both premature narrowing and the opposite mistake of treating a large undifferentiated dump as proof of the immediate physical result.
