# TS18 physical-validation second pass and repairs

## Authority and scope

Parent: PR #19, `integration/ts18-standalone-baseline`,
`4e59089cce98b7f768c1788c612b3a19a62957fc`.
These repairs belong to a separate child PR against that branch. The frozen parent,
`main`, concurrent PRs #21/#22 and other repositories are not modified.
This is the post-validation repair portion of issue #20, not completion of its full
boot/ACC three-source readiness programme. PR #21/#22 contain competing startup
coordinators; their physically unqualified automatic foreground staging is not
imported here. Integrate any later coordinator with the single media owner here.

Original evidence: `TS18-PR-Validation-20261015T161858Z-10744.tar.gz`, SHA-256
`16bcfa42ba330ccc051bb397f7cc890a76f6efdc9d3e476237cce42ef09c3ff5`.
All 4,268 `EXPORT.sha256` entries were rehashed successfully. Installed launcher
reported source `bc061116b420affcd6d33118ad8a0a54a1bf5ccb` and APK SHA-256
`f7e0fd5f5e05e607a87ac2f9ea813067403f73d4b1baaba8dd1ec92e6a5fdc9d`.
The collector did not establish the signer. Maps APK SHA-256 was
`8027f421660c41bb456f81a55c6eb6f81ef3297fa28ea449b202abcc81bafe6e`;
its source/signer compatibility remains unqualified. Device: Android 10/API29,
user 0, standalone `com.cbkii.ts18launcher/.HomeAlias` HOME.

The operator's added T11 note is included in this assessment: compact acquisition
unreliable after app/HOME changes; fullscreen sometimes looked compact; loading
appeared to block controls; Retry appeared inert; orange Nav produced a top-right
crop with black margins and sometimes eventually expanded.

## Findings and implemented disposition

| ID | Finding / confidence | Repair / remaining limit |
| --- | --- | --- |
| V01 P1 | Confirmed code: explicit source launches entered the Maps parking gate, which cancelled on HOME stop and could never deliver while HOME was hidden. | Dispatch explicit app intent immediately, cancel stale Maps work, retain known task identity for the next HOME. No parking deadline or visibility prerequisite. |
| V02 P1 | Confirmed code: HOME stop destroyed pending media work; storage preparation created a separate bootstrapper. | One application-owned media coordinator survives Activity stop/recreation. Configuration changes replace its owner and cancel obsolete work; storage uses the same owner. |
| V03 P1 | Confirmed code: ticker ignored the Activity's reconciled snapshot, observed only the notification service, and independently chose a source from preferences. Both apps supplied metadata in captured sessions. | Render the effective snapshot and displayed source used by the transport UI. Remove the second observer. Command/status strings cannot overwrite track/station text. Physical text bounds remain a retest gate. |
| V04 P1 | Confirmed runtime: T11 transactions 71/72 accepted mode 1 with compact `0,141,1131,702` bounds. | Clear stale override bounds through API29 `resizeStack(null)` only for the exact exclusive standard display-0 stack with a known source-owned top Activity. Require the observed `0,0,0,0` inherited-bounds representation before reporting fullscreen. Permission/shared/unknown stacks fail closed; no fixed display rectangle or OEM writes. Pixels/touch still require physical testing. |
| V05 P1 | Confirmed: empty old Recents entries (`StackId=-1 sz=0`) could veto absence. Task 9661 finished; 9663 subsequently existed. | Ignore positively empty historical records. Use bounded activities/ATM/live-stack corroboration; a package process alone cannot veto a complete structured task absence. Reacquire a replacement or launch only after corroborated absence. Unreadable/ambiguous producers remain uncertain. |
| V06 P1 | Confirmed: N2 returned `NORMAL_OPEN_REQUIRED` when cold. Retry could repeat that policy failure. | N2 admits one normal mode-1 bootstrap under the existing atomic launch claim, observes real `MwmActivity`, then converts the same task. Only the exact task recorded in its outstanding bootstrap journal may complete across HOME stop/restore. Existing fullscreen maps retain the HOME foreground guard. Accepted uncertain starts are not replayed. |
| V07 P1 | Confirmed code: `showStarting` hid both buttons and all useful phase text. | Keep Retry and fullscreen actions available within the map panel; show phase/queued-Retry feedback. Unrelated HOME controls and source launches remain independent. |
| V08 P1 | Confirmed timing defect: NavRadio guard returned zero if its not-before time exceeded the command deadline. T13 did not capture the initial two Play taps. | Reserve one monotonic source-specific budget for readiness, 3.2 s cold guard and acknowledgement; never erase the guard. Exactly one Play dispatch. This fixes a proven defect, not a proven complete explanation of the audible cold-radio failure. |
| V09 P1 | Confirmed code: buffering/connecting acknowledged Play, and opposite Pause dispatch was labelled paused without observation. | Keep buffering on the Pause side for buttons, but require actual PLAYING to commit a handover. Send opposite Pause once, observe directional acknowledgement and report unconfirmed/unsupported Pause truthfully. No launcher audio focus/session or vendor source manipulation. |

Correction to the earlier audit: T13's sampled media-session dumps do **not** prove
simultaneous PLAYING sessions. `media-before` and phase 0 show Radio PLAYING;
subsequent phases show both paused. The trace contains Radio Pause and a later
Music Pause, with no fresh Music Play in that captured interval. The operator's
unexpected audible music remains a valid failure, but overlap, app behaviour and
Topway routing cannot be distinguished from these samples. V09 is an independently
confirmed code defect; it is not claimed as conclusive physical attribution.

T11's app-request finish establishes Activity termination, not its internal cause.
Google Play services ANR and substantial GNSS/storage logging are possible latency
contributors; neither is proven to cause the deterministic policy/ticker defects.

## Approaches and fallbacks

- N0: ordinary user-requested app launch, independent of native root/window recovery.
- N1: one direct freeform cold launch, then same-task verification/repair.
- N2: one normal bootstrap, observe renderer Activity, same-task compact conversion
  and guarded restoration of the same HOME. No ordinary full-screen task may be
  compacted merely because a transient HOME lifecycle event occurred.
- Retain a valid warm task; read-only recovery precedes repair. An unchanged repaired
  observation does not authorise repeated identical mutations. Retry is an explicit
  new bounded action; switch profiles to compare methods.
- If the fullscreen bounds API is refused or bounds remain compact, report
  `FULLSCREEN_GEOMETRY_UNCONFIRMED`. Keep the task and offer ordinary opening;
  never hide failure, clear app data, force-stop, change SELinux or modify vendor policy.
- Auxio uses its browser/advertised PREPARE contract. NavRadio passive preparation
  remains disabled until non-disruption is qualified. Normal source icons provide
  explicit foreground opening. No new automatic media Activity staging is enabled.

## Verification and physical acceptance

Production-code JVM/Android-command doubles exercise ticker updates without a
listener instance, source changes/removal, cold guard/coalescing, buffering versus
play acknowledgement, unconfirmed handover, independent icon delivery, HOME stop,
N2 bootstrap/return, stale tasks, duplicate-launch prevention, fullscreen override
clearing, denied/shared/permission stacks and compact-bounds rejection.
Negative controls against the frozen baseline reproduce the ticker and premature
handover failures. These tests do not prove TS18 Binder permissions, pixels or audio.
The PR's Validate workflow owns full Android build/lint/unit/R8/signing checks.

Keep the prior acceptance scope: T10 PASS; T16 PASS for tested settings/layout;
warm Auxio transport has partial positive evidence. T11/T13/T14 remain FAIL until
retested. Q0 is partial, T15 unknown/partial, T17 blocked, T12 and D15a NOT_RUN as
specified. No new physical result is created by these source changes.

Use the existing beginner-friendly kit, P/F/N results and exact APK identity gate.
Begin a short capture before touching controls:

1. Reidentify installed launcher and Maps APK/source/signer; record profiles. Retain
   DoFun recovery HOME. Do not reuse historical APK labels as source proof.
2. With Maps failing/loading, tap Music and Radio app icons once: verify immediate
   foreground launch. Return HOME. Verify drawer/settings/rail and visible Retry.
3. Repeat T11 separately for N0, N1 and N2 from recorded cold/warm states. Record
   task ID, mode, bounds, drawn surface, touch and HOME restoration separately.
   Use the existing mapwindow capture/transition screenshots. Stop at the first
   failure; record it before Retry. Never infer visual PASS from mode 1 alone.
4. Start the media trace before the first cold Radio Play. Press once, wait for the
   bounded result, and record audible output, focus, exact session and Topway state.
   Switch from playing Music to Radio; verify Music paused before testing Radio Pause.
5. Repeat T14 with Auxio warm/cold and late USB; confirm track/artist and station
   text on HOME, including after listener reconnection and Activity recreation.
6. Only after those gates pass, run reboot/cold boot/ACC and non-disruption testing.
   Full automatic three-source boot/ACC readiness remains separate issue #20 scope.

No device install, production release or TESTING snapshot publication is part of
this repair commit. A later installable TESTING candidate must carry this child
head's source/build/hash/signing identity; the PR #17 rollback remains historical.
