# Navigation surfaces

`HomeNavigationSurfacePolicy` selects exactly one HOME surface. `NavigationWindowController` owns task presentation; the external app owns maps, routes, renderer, location and camera.

| Surface | Mechanism | Current qualification |
|---|---|---|
| N0 / Fullscreen | Ordinary selected-app launch or same-task fullscreen handoff | Explicit safe fallback; latest TS18 lifecycle acceptance remains separate |
| N1/N2 / Native window | Root-assisted reuse/placement on display 0, mode 5, live panel bounds | Primary TESTING path; latest integrated pixels/touch/lifecycle NOT_RUN |

The launcher-owned Leaflet/WebView map is retired. Old preferences migrate through `HomeNavigationSurfacePolicy`; they cannot reactivate a third surface. See [native window](NATIVE_NAVIGATION_WINDOW.md) and [compact startup](COMPACT_STARTUP_PROTOCOL.md).

## N2 ownership and recovery

Reuse a valid warm task before cold acquisition. Verify user/package/task/display/mode/bounds and the current top Activity; retain routes, bootstrap and real permission/settings flows. Broker verbs and the shell's remaining cold/fullscreen compatibility path share the same authority. A failed focus read or one TASK_NOT_FOUND is inconclusive. Keep HOME, drawer, Settings and unrelated foreground tasks usable during bounded recovery.

Task geometry, renderer evidence, visible map pixels, touch, GNSS fixes and active route state are different observations. Screen-on is not proven ACC. See [continuous readiness](CONTINUOUS_READINESS.md) and [the consolidated campaign](CONSOLIDATED_TESTING_PLAYBOOK.md).

## Retired virtual-display experiment

N3 was removed from active code, UI, build sources, tests and instrumentation.
Its separate-display approach was not an accepted TS18 navigation surface.
Historical rationale and results remain in dated repository documents/Git history.
A previously saved `n3.experimental.enabled` preference is inert and is not rewritten.

N2 remains the default. Settings → Startup diagnostics → Navigation method retains explicit N1 direct-window and N0 fullscreen rollback choices. The chosen method is local to the device and not imported from a configuration backup.
