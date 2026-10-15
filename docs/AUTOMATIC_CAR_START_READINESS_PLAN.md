# Automatic car-start readiness — engineering handoff

**Status: DESIGN / NOT_IMPLEMENTED / PHYSICAL FAIL REPORTED.** Prepared 16 October 2026 for the next `cbkii/ts-theme` code PR; this documentation commit changes no runtime behaviour.

## Authority and priority

- On 4 October 2026, `AGENTS.md` last changed at `15775b5d`. Between that revision and PR #17 `bc061116` are **26 commits**: PR #15's bounded diagnostics/qualification toolkit, PR #16's single-task/uncertain-launch recovery and PR #17's startup, source/media method selectors and signed-build identity. These are present in code; physical effectiveness is unqualified.
- PR #17 `bc061116` is based on PR #16 `ae2dd07` → PR #15 `f9f9d473` → PR #14 `15775b5d` → PR #13 `a214d457`. Re-fetch live refs before implementation; `main` is not this integrated candidate.
- The user reported on 15 October that **cold Music, Radio and Maps all fail across profiles**, so the full testing campaign should not be repeated unchanged. This is direct physical user observation; a single shared root cause has **not** been proven.
- Exact unit: Topway TS18 Android 10/API29, 1280×720, Magisk-capable. Actual user/HOME/firmware/installed source APKs, SELinux/caller namespace and vehicle ACC contracts must be rechecked, not inferred from static donors.

## Current code: do not misrepresent as the proposed implementation

| Component | Implemented in PR #17 | Missing for the requested outcome |
| --- | --- | --- |
| `LauncherActivity` + `Ts18LauncherApplication` | HOME-bound media warm-up and removable-storage reconciliation | Single application-owned boot/wake/source readiness transaction; retained ownership when HOME stops |
| `MediaSourceBootstrapper` / adapters | Auxio Browser/PREPARE and interactive NavRadio service admission | Verified playable Auxio queue/USB library, silent Radio tuner/session readiness, separate admission/command/ack budgets |
| `NavigationWindowController`, `nav-window.sh` | N0 ordinary open, N1 direct freeform, N2 normal cold bootstrap then compact (post-validation repair; physically unqualified); bounded observation and atomic uncertain launch claims | Automatic *real* cold Activity/renderer readiness then same-task compact conversion, regardless of N1 helper suitability |
| `NavigationOverlayGate` / source icons | Pending launch gate and focus/lifecycle cancellation | Unrelated Music/Radio app icons must work even if Maps is blocked; inspect cancellation path first |
| `TestingProfiles`, `ts18-validate.sh` | N0/N1/N2, Bridge/Intent, M0/M1, Radio root/normal, P/F/N guided comparisons, exact-head build identity | Unattended automatic car-start smoke gate and final-head evidence of three ready sources together |

The current manifest does **not** declare a boot receiver; `SYSTEM_ALERT_WINDOW` declaration is not evidence of a functioning loader. The existing PR #17 code deliberately does **not** launch source Activities for readiness. CI passing, installed APK, running process, accepted `am start`, MediaSession token or WMS visibility does not establish usable cold start.

## Required code outcome (future PR)

From a genuine Android boot or verified ACC wake, automatically prepare **all three** configured integrations, while HOME remains responsive:

1. **Music `com.tw.media`:** bind actual exported Auxio browser → source-owned PREPARE/queue/library/USB readiness → existing playback authority and one normal HOME Play. Do not replay rejected root `startservice` or issue Play merely to warm.
2. **Radio `com.navimods.radio`:** inspect the installed API29-compatible service/tuner contract (not supplied v4.00 donor); compare ordinary FGS vs root admission; prevent unwanted tuning/audio switching. Separate service settlement from one transport Play and physically audible output. Stock `com.tw.radio` is a separate contract with no declared service.
3. **Maps `app.organicmaps.incar`:** reuse correct existing task, otherwise one normal/root-supported Activity launch; wait for real `MwmActivity`, map pixels and input, then compact the same task. Preserve real Splash, permission/settings screens, routes, fullscreen and HOME. If freeform fails, retain usable fullscreen; do not repeat uncertain accepted launches.
4. **Coordinated lifecycle:** single source admission each, deduplicated boot/HOME/wake/USB triggers, method and generation tracking, event-driven readiness/recovery. Do not confuse screen-on with ACC; distinguish full boot, same-boot sleep/wake, HOME recreation and source process death.
5. **Alternative, not retry:** if genuine service readiness fails, allow **one controlled source-Activity foreground bootstrap** with exact-task HOME restoration, only when authorised/qualified and without interrupting a foreign foreground task or protected reverse camera/call UI. An overlay loader is a later optional UX enhancement, not a functional prerequisite. No blind root input, task clearing, fake session/audio focus or vehicle property writes.

Prefer the smallest coherent Android-10 Java change; reuse existing single-flight methods and validated shells. Consider a systemless Magisk companion only if a device-proven boot gap remains and rollback is ready; never assume Magisk equals platform UID/signature/MCU authority. The launcher must remain usable without this companion.

## Investigation order and acceptance

1. Fix explicit icon launch delivery and the 3.2 s NavRadio settle vs 4.5 s command deadline conflict. Verify normal Maps launch independently of root windowing. Do not assume all source failures share a cause.
2. Establish owned source-specific readiness states (discovery, accepted/uncertain start, service/task available, **operational**, blocked/failed). Preserve user-initiated requests through startup, send one Play, and keep healthy opposite audio until handover is confirmed.
3. Implement independent, source-appropriate startup methods and qualified Activity fallback; always respect HOME/task and UI authority.
4. Extend `scripts/qualification/ts18-validate.sh` and `docs/TESTING_REPAIR_PLAYBOOK.md` with a **first-stage, three-source car-start smoke gate**: genuine boot or verified ACC wake; no manual source openings; one music Play audible; one radio Play audible; compact map rendered/touchable/fullscreen/HOME; icons work if Maps fails. Tester selects P/F/N and optional note; the collector remains read-only and uses bounded local capture. Storage delay, call/reverse UI, power transitions and process death are separate tests.
5. Only after repeated smoke success, conduct extended boot, warm return, Maps task, ACC, USB and vehicle integration gates. Record startup method, generation, deadlines, package/version/signing/source, tasks/sessions and first failed boundary. A second press/Retry is not a pass.

## Delivery and safety

- Create the focused **new stacked code PR** from the **frozen head recorded in [baseline PR #19](https://github.com/cbkii/ts-theme/pull/19)**, targeting `integration/ts18-standalone-baseline` (not stale `main` or the superseded docs branch). The full Work Packages A–H and physical acceptance contract are in [handoff issue #20](https://github.com/cbkii/ts-theme/issues/20). Preserve ancestry, these instructions and the P1 release-blocking qualifications.
- Run the repository-required Python validation/tests, shell syntax/ShellCheck, Android lint/JVM tests and build tasks relevant to actual changes. Exact CI/head and tests run must be reported; device-only checks remain NOT_RUN until observed.
- Request one final-head signed **TESTING** snapshot using the established explicit `/testing-apk` workflow only for installable code changes, pinning PR/ref/commit/package/signer/hash. Keep previous rollback and DoFun installed/enabled.
- No vendor APKs, protected data clearing, broad chmod, SELinux changes, private MCU/CAN commands, protected firmware/partition writes, auto-Play or duplicate player/route ownership.
- Report code/CI/APK/install/three-source physical readiness/boot/ACC separately. Do not call the repair complete on source-level tests alone.

**Primary success test:** all three applications automatically become operational *in the same car-start session*, with no manual per-source foreground opening and no repeated presses, while HOME and protected vehicle functions remain usable.
