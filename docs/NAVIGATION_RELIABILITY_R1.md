# Navigation reliability R1 — launcher ownership and presentation

The candidate builds on PR15 (diagnostics), which builds on PR14/13/11/10. It changes
only launcher-owned navigation and its regression coverage. No HOME default,
protected package, vendor property, firmware or system component is changed.

## Outcome and authority

| Layer | Evidence used | Boundary |
|---|---|---|
| Activity task | User/package/component, task/stack/display/mode/bounds; dumpsys with API29 ATM fallback | Process existence is not Activity existence |
| Bootstrap | InCar Splash/download/foreign permission Activity versus MwmActivity | No automatic resize/focus or Activity redelivery during bootstrap |
| Window | Same user/component/stack on display 0, has surface, visible/on-screen, shown/drawn | WMS flags do not establish map pixels, touch or GPU correctness |
| HOME | Real visible/overlay lifecycle and guarded HOME/navigation foreground pair | Unrelated applications retain focus |
| OEM | Existing read-only property/capture evidence | Topway composition capability remains unqualified |

## Implemented recovery

Initial acquisition or known-task verification → bootstrap observation → same-task
mode/bounds repair → focus → window observation. Successful geometry without
window evidence remains `PRESENTATION_UNCONFIRMED`; it cannot mark WINDOWED.

Transient observations schedule a guaranteed follow-up without requiring another
layout/lifecycle callback. There are at most six follow-ups (350, 900, 1800, 3000,
5000 and 8000 ms) and a 45-second budget checked between transactions. A root
operation has a 10-second shell deadline inside the existing 12-second outer cap;
individual dumpsys/am producers are limited to 2 seconds. Active operations stay
single-flight. Lifecycle callbacks coalesce while a timer or operation owns work.
HOME stop/destroy/fullscreen cancels scheduled recovery; a real HOME return,
authority change or manual Retry resets the bounded budget.

Each automatic acquisition first observes tasks. An accepted cold launch is
protected by an atomic private boot/user/package directory claim before dispatch.
A launcher timeout/restart cannot replay the launch; the claim never expires on
time alone. Definite command rejection or validated task identity resolves it. The claim
is cleared for every validated package; Organic Maps must first reach MwmActivity. A live service-only process
can cold-launch only after complete ATM NONE plus negative Recents and stack
observations. A failed query cannot prove disappearance. Independently discovered
replacement tasks are adopted by package/user/component, with fresh geometry
verification before repair.

The current departure fullscreen/freeform policy is preserved. HOME/native focus
is accepted for a warm mode-5 presentation; a fullscreen map is not compacted over
an unrelated or transient foreground. Retaining mode 5 across departures is a
later physical experiment, not an assumed improvement.

## Fallback and diagnostics

Explicit Open fullscreen first reuses the native task path. Root/backend discovery
refusal can fall back to the ordinary Android package launch, triggered only by
that user action. A known root refusal avoids another root attempt. Permission/
foreign-Activity policy refusal does not permit this fallback. No CLEAR_TASK,
force-stop, injected input, second navigation authority or automatic Leaflet/HOME
switch is introduced. A normal Android launch is dispatch evidence, not a verified
fullscreen window.

A terminal panel includes the helper code and attempt number. TS18Nav records
attempt, phase, task, mode and window-visible/drawn evidence. The read-only helper
`probe USER PACKAGE` reports help/native-launch and ATM query capability separately;
mode mutation remains `not-run` until an explicit exact-task capability test.
Existing collectors retain task/window/SurfaceFlinger/logcat/screenshot evidence.
Capture a failure before Retry so its original task state remains available.

## Qualification and execution order

Host shell doubles cover warm task reuse, bootstrap deferral, process-only absence,
accepted launch with delayed task visibility, independent window evidence and
foreign focus/permission guards. The production-controller JVM harness exercises
recovery without lifecycle callbacks, bootstrap reuse, foreground retry, hidden
window recovery, bounded exhaustion, HOME stop/return, destroy and ordinary fallback.
Run the harness on canonical CI if the local host has no JDK. Other process-group
capture tests remain skipped when the host's /proc PID namespace differs.

No TS18 is connected. Installation, actual rendered map/touch, reboot/cold boot,
ACC, reverse/call transitions and all device acceptance counts are NOT_RUN.
This is a test candidate; the release gate remains blocked on physical evidence.

1. Qualify R1 with the existing PR55 Organic Maps TESTING build as a fixed baseline.
2. Qualify R2 separately, changing only Organic Maps, to isolate lifecycle effects.
3. Proceed to R3 only if a verified task/window still fails actual composition/touch.
   Obtain matching working/failed Topway captures before choosing an OEM adapter.

| Physical scenario | Acceptance | Record before any recovery tap |
|---|---|---|
| Cold HOME/Maps startup | 20/20 visible interactive maps without Retry | Task identity, bootstrap stage, phase and window/surface screenshot |
| Unrelated app → HOME | 30/30 restores | Same task/route, no focus theft or duplicate Activity |
| Maps fullscreen → HOME | 20/20 compact restores | Mode/bounds, Activity recreation, visible pixels and touch |
| Permission/download screen | Preserved and usable | No forced focus, bypass or cold relaunch |
| Missing root/backend | HOME usable; explicit navigation dispatch works | Actual helper refusal and fallback outcome |
| Transient uncertainty/hidden window | Bounded automatic recovery or truthful degraded state | Attempt IDs, timings and first failing layer |
| Reboot/cold boot/ACC/process death | Separate qualification levels | Exact installed APK source/signer/hash and boot ID |

Use the existing diagnostics toolkit and navigation playbook. Do not install the
boot collector or change diagnostic service entries merely to collect a navigation
checkpoint. Retain a previous signed TESTING snapshot and DoFun recovery HOME.
Revert this PR's commit (on its own branch) for source rollback; APK rollback needs
signer/version compatibility and preserved app data verified on the device.
