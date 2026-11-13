# Navigation surfaces

`HomeNavigationSurfacePolicy` selects exactly one HOME surface. `NavigationWindowController` owns task presentation; the external app owns maps, routes, renderer, location and camera.

| Surface | Mechanism | Current qualification |
|---|---|---|
| N0 / Fullscreen | Ordinary selected-app launch or same-task fullscreen handoff | Explicit safe fallback; latest TS18 lifecycle acceptance remains separate |
| N1/N2 / Native window | Root-assisted reuse/placement on display 0, mode 5, live panel bounds | Primary TESTING path; latest integrated pixels/touch/lifecycle NOT_RUN |
| N3 | Launcher-owned private VirtualDisplay and SurfaceView presenter | Separate developer screen, default OFF; never a selectable HOME surface |

The launcher-owned Leaflet/WebView map is retired. Old preferences migrate through `HomeNavigationSurfacePolicy`; they cannot reactivate a third surface. See [native window](NATIVE_NAVIGATION_WINDOW.md) and [compact startup](COMPACT_STARTUP_PROTOCOL.md).

## N2 ownership and recovery

Reuse a valid warm task before cold acquisition. Verify user/package/task/display/mode/bounds and the current top Activity; retain routes, bootstrap and real permission/settings flows. Broker verbs and the shell's remaining cold/fullscreen compatibility path share the same authority. A failed focus read or one TASK_NOT_FOUND is inconclusive. Keep HOME, drawer, Settings and unrelated foreground tasks usable during bounded recovery.

Task geometry, renderer evidence, visible map pixels, touch, GNSS fixes and active route state are different observations. Screen-on is not proven ACC. See [continuous readiness](CONTINUOUS_READINESS.md) and [the consolidated campaign](CONSOLIDATED_TESTING_PLAYBOOK.md).

## N3 developer experiment

`navigation/n3` is isolated from N0–N2. Settings → Experimental · N3 display host offers an explicit enable/open developer path. `NavigationFeatureFacade` prevents competing legacy automatic navigation while N3 holds its display. Turning it off releases the session.

- `N3Session` is a generation-tagged state machine with per-phase deadlines, detach/reattach, failure and explicit Retry. `N3NavigationBackend` implements the Android display/launch edge; it can launch the exact selected component after admission. The state machine alone does not launch Activities.
- `N3DisplayProbe` creates/releases an off-screen private display and asks `isActivityStartAllowedOnDisplay` the same trampoline-admission question the real path asks, without launching the navigator. Refusal is caller/target-specific, not a whole-ROM verdict.
- The backend's public-API display path uses a launcher-owned trampoline for API29 cross-UID admission, then launches the exact target once. Accepted launch is RENDER_AWAITING, not interactive. An acquired post-launch frame is not proof of meaningful map content.
- Surface reattachment retains the existing display/task. Expand enlarges that display; it does not transfer the task to display 0. Open navigation normally releases N3 and uses N0.
- Pointer mapping preserves gesture ownership, pointer IDs and cancellation. The reflective display-owner input forwarder is unavailable in the recorded stock API29 emulator (`NoSuchMethodException`), so that path is view-only there. Private-display IME remains on the main screen. No root input injection or hidden-API exemption is provided.
- Third-party hosting needs actual embedding admission. The inspected Organic Maps companion does not establish it. Root/PID/display creation does not supply platform signing or input authority.

The inert `n3host` APK, Binder protocol, client and admission skeleton are **retired**. Old optional-host preferences are inert; no host installation is a current acceptance step. A new privileged host is not an approved substitute for N2 reliability.

Classifications distinguish STATIC_POC_READY, BLOCKED_NO_AUTHORITY, RENDER_OBSERVED_VIEW_ONLY and RENDER_AND_INPUT_PATH_OBSERVED. None equals exact-device feasibility. N3 needs observed map pixels, usable touch/Back, safe focus/HOME return and a demonstrated advantage before it can be considered for a product surface. Latest TS18 N3 remains NOT_RUN; see [private-contract evidence](PRIVATE_CONTRACT_REVIEW_2026-11-12.md).

N2 remains the default. Settings → Startup diagnostics → Navigation method retains explicit N1 direct-window and N0 fullscreen rollback choices. The chosen method is local to the device and not imported from a configuration backup.
