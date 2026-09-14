# Navigation surface roadmap

## Product target

Display the selected real navigation app as a large, fully functional map on standalone HOME while preserving launcher controls, offline navigation data, normal app lifecycle and DoFun recovery.

The primary architecture is one external task on physical display 0 in Android freeform `windowingMode=5`, reconciled to the exact `NativeNavigationPanel` screen rectangle. Fullscreen is an explicit fallback. Leaflet is a legacy online-only last-resort surface and is never an automatic fallback.

## Evidence already established

- Exact-device DoFun/Organic Maps captures prove a normal third-party navigation task can run on display 0 in mode 5 at compact bounds.
- Organic Maps receives real compact Activity/Decor/Surface geometry in that state; it is not standard Android PiP.
- The first PR #11 TESTING build failed before a valid freeform transition: it launched fullscreen first, cancelled delayed repair when HOME stopped, misparsed the physical `Hist #0` component and repeatedly relaunched.
- The standalone HOME did not reproduce the historical DoFun `isPipLauncher ... :navi` classification in that failed run, but this remains a downstream policy question until a valid standalone mode-5 state is reached.

## Phase 1 - deterministic mode-5 TESTING candidate

Status: current PR #11 implementation phase.

- [x] Make native mode 5 the primary TESTING surface.
- [x] Remove standard Android PiP from selectable and automatic production paths.
- [x] Demote Leaflet to explicit legacy online fallback.
- [x] Parse the exact `Hist #0: ActivityRecord{... package/component ...}` grammar and preserve task-level bounds.
- [x] Treat an unobservable component as UNKNOWN, not a proven mismatch.
- [x] Resolve the ordinary exported launcher Activity in Java.
- [x] Adopt exactly one existing same-package task before launching.
- [x] If none exists, perform one helper-owned display-0/mode-5 launch, then acquire, resize and verify the exact task.
- [x] Gate in-flight work, coalesce bounds/lifecycle callbacks and keep the transaction alive across transient HOME stop.
- [x] Latch failure so HOME remains usable; require explicit Retry or Open fullscreen.
- [x] Preserve the same task for fullscreen handoff and HOME return where Android exposes enough component state.
- [x] Add event-driven read-only evidence collection and this roadmap/playbook.
- [ ] Obtain exact-head CI-green TESTING release and verify remote asset provenance.

Repository acceptance: source checks, parser fixtures, controller contracts, Android lint/unit/debug, signed/minified release, APK envelope, signature and exact-head CI all pass.

## Phase 2 - Organic Maps physical core gate

Status: blocked on the Phase 1 TESTING APK and exact TS18 run.

Use `NAVIGATION_WINDOW_PHYSICAL_PLAYBOOK.md` and require independent results for:

- [ ] exactly one selected-package task;
- [ ] display 0;
- [ ] actual mode 5;
- [ ] exact current panel bounds;
- [ ] map visible inside the panel;
- [ ] map touch inside the panel;
- [ ] launcher touch outside the panel;
- [ ] no automatic launch loop;
- [ ] drawer round-trip;
- [ ] same-task fullscreen/HOME return;
- [ ] unrelated-app isolation;
- [ ] fail-open HOME and one explicit Retry.

Do not change Organic Maps rendering merely because task/window placement fails. The launcher owns placement; Organic Maps owns rendering after real compact bounds arrive.

## Phase 3 - conditional Topway policy recovery

Status: gated; do not start unless Phase 2 proves mode 5 and bounds are correct but physical composition/input is still wrong, or Android explicitly rejects/reverts mode 5.

1. Analyse the collector's exact current `framework.jar`, `services.jar`, system-server maps/classpaths, relevant Topway APKs and logs.
2. Recover the call path around `Configuration.isPipLauncher`, the `:navi` classification, `sendNaviType`/`tw_navi`, and caller/current-HOME/task predicates.
3. Keep `persist.tw.forcepip`, `sys.tw.forcepip.*`, `/data/tw/navi_name` and `/data/tw/custom_pip_app_name` read-only until a writer/consumer contract is proven.
4. If exact-byte static analysis is insufficient, use a narrowly scoped, log-only, exact-build-hash-gated LSPosed trace at the recovered call site.
5. Do not modify arguments initially. Any later compatibility hook requires a fail-open breaker, narrow package/task scope and boot-loop recovery.

Do not fabricate the separate Video-style `WindowInfo`/`FLOATING_WINDOW_SERVER` contract for ordinary navigation apps.

## Phase 4 - navigator compatibility

Status: blocked on Organic Maps core success.

Qualify one run per app:

- [ ] Google Maps;
- [ ] OsmAnd/OsmAnd+;
- [ ] Sygic;
- [ ] any additional configured navigator.

For each, record exported launcher component, bootstrap Activity changes, task reuse/new-task behaviour, resizeability, mode/bounds, rendering, touch, fullscreen/HOME restoration and process death. A donor app's different valid lifecycle must not be normalised to Organic Maps.

## Phase 5 - lifecycle and vehicle boundaries

Status: blocked on at least one fully working navigator.

- [ ] launcher recreation and process death;
- [ ] navigator process death and task disappearance;
- [ ] reverse-camera takeover and return;
- [ ] Android reboot;
- [ ] full cold boot/power removal;
- [ ] ACC sleep/wake;
- [ ] SystemUI/sidebar state transitions that are actually supported on this unit;
- [ ] network loss/reconnect without degrading offline-capable navigation.

Report runtime, physical and lifecycle evidence separately. A green build or immediate run cannot pass these rows.

## Phase 6 - production hardening

Status: future.

- [ ] remove TESTING wording only after the supported physical matrix passes;
- [ ] bound recovery latency and root-helper overhead from measured evidence;
- [ ] finalise concise user-facing failure/status language;
- [ ] document supported navigators and exact incompatible behaviours;
- [ ] retain explicit fullscreen fallback and DoFun recovery;
- [ ] keep standard PiP, ActivityView/TaskView, VirtualDisplay and mirroring out unless a later proven requirement and authority model changes the decision.

## STOP conditions

Stop and request new recovery/authority evidence before protected framework/APK replacement, system/vendor/boot/vbmeta/AVB changes, platform-signature or UID impersonation, SELinux weakening, persistent root daemon work, or speculative Topway/MCU/CAN writes.
