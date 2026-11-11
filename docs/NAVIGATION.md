# Navigation surfaces

HOME shows exactly one navigation surface, chosen by `HomeNavigationSurfacePolicy` and owned by
`NavigationWindowController`. The launcher never renders a map, routes, requests GNSS or changes
the navigation app's camera; those belong to the navigation app (`cbkii/organicmaps` for Organic
Maps In-Car).

| | Mechanism | Status | Rollback |
|---|---|---|---|
| **N0** Fullscreen | Ordinary launch of the selected app | Supported, always available | — |
| **N1/N2** Native window | Root-assisted reuse/placement of the one real navigation task (freeform `windowingMode=5`, display 0) | TESTING default; physical qualification of the latest stack NOT_RUN | Settings → Fullscreen |
| **N3** Virtual display | App-owned display hosting the navigation Activity, shown in a SurfaceView, input scoped to that display | **Experimental, not selectable.** Software: `STATIC_POC_READY` | Not in use |

## N3 status

`navigation/n3` is fully separate from N0–N2: it has no reference to the controller, the root
helper or the task bridge, uses no root, injects no input and never starts an Activity by itself.

- `N3Session` — framework-free, generation-tagged lifecycle (`DISABLED → HOST_CONNECTING →
  DISPLAY_CREATING → APP_LAUNCHING → RENDER_AWAITING → INTERACTIVE`, plus `SURFACE_DETACHED`,
  `REATTACHING`, `RECOVERING`, `FAILED`, `SHUTDOWN`). One owner; each wait has a deadline; a
  failure releases the display and stops — only an explicit **Retry N3** tries again. A launch the
  platform accepts is `RENDER_AWAITING`, never interactive.
- `N3DisplayProbe` — the discriminating device test. Settings → *Experimental · N3 display host*
  creates a **private** launcher-owned display backed by an off-screen buffer, asks Android
  (`ActivityManager.isActivityStartAllowedOnDisplay`) whether the selected navigation Activity may
  start on it, and releases it. It launches nothing.
- `N3Feasibility` classifies the observation: `INFEASIBLE_ON_THIS_ROM`, `NO_TARGET`,
  `BLOCKED_NO_AUTHORITY`, `LAUNCH_ADMITTED_UNVERIFIED` or `PROBE_FAILED`. None of these is
  `DEVICE_FEASIBILITY_PASS`; that needs observed map pixels, touch and Back on the unit.

Expected on Android 10 (inference, not observed): an ordinary app may not place another app's
Activity on its private display unless that Activity opts in, so the probe will likely report
`BLOCKED_NO_AUTHORITY`. If so, N3 needs a separately reviewed, disabled-by-default display host
with caller validation; that host is not part of this build.

### Input mapping and the optional host contract

Two pure classes are in place for the presenter and host, and are unit-tested. Neither is reachable from any runtime path yet:

- **`N3InputMapper`** maps presenter-view touches to display pixels.
  - The display is aspect-fitted and centred inside the view minus its runtime insets.
  - A gesture belongs to the display only if its first pointer goes down inside the fitted image. A gesture that starts in an inset or letterbox is dropped whole. Later moves are clamped.
  - Up to 10 pointers keep their IDs.
  - A malformed event (too many pointers, NaN or mismatched arrays) cancels an owned gesture once, so the navigator is never left pressed.
- **`N3HostPolicy`** is the admission contract any future privileged display host must enforce. Its default configuration admits nothing. It checks:
  - one package for the caller UID, the exact signing certificate and the exact Android user, all derived by the host, never from the request;
  - an exact allowlisted navigator component (no package-only or implicit target);
  - display ownership by that caller, with at most one display per caller;
  - bounded size and DPI;
  - in-bounds coordinates, at most 10 pointers and 240 events/s;
  - on Binder death, every display the caller owned is released.

  It has no shell, intent, global input or wildcard path.

No host APK, service, permission or manifest entry exists. Static tests keep `INJECT_EVENTS`,
trusted-display permissions and any runtime use of the policy out until a separately reviewed
host is approved. Without one, N3 for a third-party navigator reports `BLOCKED_NO_AUTHORITY` /
`HOST_UNAVAILABLE`.

## Gates before N3 can be offered as a surface

1. Probe on the TS18 reports `LAUNCH_ADMITTED_UNVERIFIED` (or a reviewed host makes it so).
2. Real Organic Maps pixels, multi-touch, Back and IME on the display, with density/inset mapping.
3. HOME stop/start, drawer, permission Activities, process death and display removal recovered.
4. Measured CPU/GPU/PSS against N1/N2.
5. Owner approval. N0 stays available throughout.
