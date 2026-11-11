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

### N3 backend (experimental, default OFF)

To try N3, enable **Settings → Experimental · N3 display host → Enable experimental N3 navigation**,
then open **Open N3 navigation screen**. N3 is never a HOME surface. While N3 holds a display,
`NavigationFeatureFacade` refuses legacy automatic launch, so the two never compete for the
navigator. Turning N3 off ends the session.

**Display ownership.** `N3NavigationBackend` runs one `N3Session` through one display path:

- **Launcher private display (public API 29 path).** The launcher creates a private, non-default
  virtual display (flags 0) at a fixed 1024×600. The first frames go to an off-screen reader, so
  real pixels from the launched app are observed before the display is moved to the
  `N3PresenterView` surface. Allocating the display or having a launch accepted is never reported
  as rendering.
- **Launching.** A user tap starts the exact launcher component with `setLaunchDisplayId`, after
  `isActivityStartAllowedOnDisplay`. The launch is never replayed. Android admits another app's
  Activity onto an app-owned private display only if that Activity allows embedding. Otherwise
  the result is `BLOCKED_NO_AUTHORITY` for this caller; that is not a whole-ROM verdict.

**Input.** `N3MotionConverter` transforms a copy of each MotionEvent, preserving pointer IDs,
action and action index, down and event time, and history. A gesture belongs to the display only
if its first pointer lands on the image. A finger dragged off the image is clamped. Malformed
input cancels the gesture once. Delivery uses Android 10's display-owner input forwarder (the path
ActivityView uses), and the platform checks display ownership. It is reached reflectively and
fails closed: a refusal leaves N3 view-only and the status line says so. **Observed on the stock
Android 10 emulator in CI:** the forwarder method does not exist (`NoSuchMethodException`), so N3
is view-only there. Android 10's own ActivityView reparents the display's content into the
host window instead (`IWindowSession.reparentDisplayContent`, owner-checked). That transport is
the next implementation step. Back is a display-scoped
key pair.

**IME.** Private displays have no IME on Android 10, so the keyboard appears on the main screen.

**Presenter lifecycle.**

- Losing the surface (HOME stops, the screen is recreated) keeps the display and its content. A new
  surface reattaches it without relaunching.
- If the display is removed, the host dies or a deadline passes, the session fails and releases the
  display. Only **Retry N3** tries again.
- **Expand** shows the same display larger. It is not a hand-off of the task to the main screen.
- **Open navigation normally** ends N3 and opens the app on the main screen (N0).
- **Use legacy navigation** ends N3 and turns it off.

**Status classifications:**

| Classification | Meaning |
|---|---|
| `STATIC_POC_READY` | the software is in place; nothing has rendered yet |
| `BLOCKED_NO_AUTHORITY` | this caller may not host the target |
| `HOST_UNAVAILABLE` | the optional host is absent, disabled or refused the request |
| `RENDER_OBSERVED_VIEW_ONLY` | pixels from the target arrived; no input path |
| `RENDER_AND_INPUT_PATH_OBSERVED` | pixels arrived and an input path was granted |

None of these is `DEVICE_FEASIBILITY_PASS`, which needs observed map pixels, touch, Back and a
security review on the unit.

### Privileged host retired

The inert `n3host` APK module, client, Binder protocol and admission skeleton are removed. Existing optional-host preferences have no runtime authority. They cannot supply platform signing, embedding or input privileges.

N3 remains an isolated default-off public-display experiment. API29 reference evidence is view-only when the input forwarder is unavailable; Organic Maps embedding authority is not established. It does not replace N2. See [the bounded assessment](PRIVATE_CONTRACT_REVIEW_2026-11-12.md).

## Gates before N3 can be offered as a surface

An exact-device candidate must demonstrate map pixels, usable touch/Back, safe focus/HOME hand-back, navigation intent/NAVI-key compatibility and a credible advantage over N2 without protected-system changes. Until then N3 stays default-off and cannot become the HOME surface.
