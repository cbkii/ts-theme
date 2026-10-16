# PR #24 startup corrections from physical validation

Scope: `cbkii/ts-theme`, PR #24, preserving its original head
`6b4e6939a82fe2bbaaa116780291bcd20ce21705` and incorporating the reviewed
PR #25 repair head `a9effde5fef9b1d52ec9f3e4d167a1629dcde210` by ancestry.
PR #24 targets the #25 branch; #25 continues to target frozen #19. Neither
parent branch nor main is changed. No other cold-start PR is closed or merged.

Original evidence: `TS18-PR-Validation-20261015T161858Z-10744.tar.gz`,
SHA-256 `16bcfa42ba330ccc051bb397f7cc890a76f6efdc9d3e476237cce42ef09c3ff5`.
The preceding [physical repair ledger](PHYSICAL_VALIDATION_REPAIRS_2026-10-16.md)
records installed identities, the corrected T13 interpretation and inherited fixes.
This pass reread raw results, T11 Activity/window/session checkpoints and helper
traces. T11 HOME is `.HomeAlias`, task 9648, with `mCurrentFocus=null`; Radio
provides station metadata and Auxio track/artist. Later captures still report
`NORMAL_OPEN_REQUIRED` and `TASK_OBSERVATION_UNCERTAIN`. No new device run exists.

## Confirmed additional defects and disposition

| ID | Severity | PR #24 defect | Correction |
| --- | --- | --- | --- |
| CS01 | P1 | Startup constructs another media bootstrapper; HOME and storage have separate command/preparation lifetimes. | Share the application-owned bootstrapper from #25. Preserve one user command generation and pending transport owner. |
| CS02 | P1 | Maps startup launches independently, waits nine seconds, then restores HOME without observing a map Activity, permissions or task ownership. | Remove that launch/timer/hold path entirely. Existing N0/N1/N2 controller owns acquisition, the atomic claim, same-task conversion and presentation verification. READY requires verified compact state, not retained task identity. |
| CS03 | P1 | Foreground stages restore HOME even if user, permission controller, camera or another Activity has taken over; captured interaction generation was unused. | Corroborate exact resumed Activity and focused window, current user and task. Accept known HOME alias spelling. Recheck source task/entry before restoring the existing HOME task. Missing/contradictory focus yields; calls, lock, screen-off, user HOME action or changed commands cancel remaining stages. Non-focusable OEM overlays remain a physical qualification limit. |
| CS04 | P1 | Aggressive root launch/HOME fallback can repeat an uncertain Android start, hardcodes user 0 and does not verify its outcome. | Remove mutating root fallback. Root is bounded read-only observation. An uncertain start is never replayed due to elapsed time or unrelated session callbacks. Legacy Aggressive uses the same guarded path. |
| CS05 | P1 | Any non-null metadata means Music READY; Radio token/Play support means READY without station content. | Require non-empty queue or media ID/title/display title plus Play capability. This is content/session readiness, not tuner, audible output, GNSS or renderer qualification. |
| CS06 | P1 | New boot signal can reset already-active process/HOME startup, with reused generations; source-package changes retain old callback state. | Reset only on an observed changed valid boot count; generations never reuse IDs within the process. Source changes invalidate old callbacks. Missing boot count is unknown. |
| CS07 | P2 | No coordinator observer for real session changes; accepted-launch flag prevents recovery after proven healthy-session loss. | Observe the existing process monitor; healthy content is adopted without reinitialisation. Only actual lost session identity reopens an observed healthy source; metadata churn cannot replay the Activity. |
| CS08 | P1 | Staging can disrupt an already-playing opposite source; Pause dispatch is mistaken for settlement. | Protect playing sources, recheck before launch, share command generation, send at most one startup Pause and observe settlement within 1.6 seconds. Never overwrite a newer HOME transport request. |
| CS09 | P2 | A reboot kills Termux/capture but the smoke instructions request reboot during the live guide. | Separate before-guide phone-video boot observations from post-boot bounded capture. Add B1–B10 one-letter startup cases. No continuous pre/post-reboot trace is claimed. |

Final boundary review also found two related gaps: late USB events were discarded
while HOME was hidden, and Radio Activity staging could run without notification
access needed to observe/suppress unintended playback. Mounts now signal background
reconciliation regardless of HOME visibility; foreground admission still requires
HOME. Radio staging is blocked if notification access is absent or lost.

## Method selection and limits

- Music: browser/advertised PREPARE first; normal Auxio entry once if content remains
  absent and guarded HOME staging is admissible. Missing/late USB remains a source
  dependency, not proof of successful preparation. Its mount event reopens background
  preparation; it does not erase an unresolved Activity launch.
- Radio: observe real NavRadio session/content first. Passive service warm-up remains
  unqualified. Normal NavRadio entry staging is an automatic testing-profile fallback
  only with no playing source and verified HOME ownership. Stock vendor Radio and
  arbitrary third-party packages never receive automatic Activity staging.
- Maps: warm task reuse; N1 direct compact versus N2 normal bootstrap/renderer/same-task
  compact. A permission/setup/download screen remains driver-owned. No fixed timer
  claims renderer readiness and no parallel coordinator acquires another task.
- Background-only disables new media foreground staging. Aggressive does not restore
  unsafe root replay. Loader and private OEM ACC integration remain unimplemented.

Foreground media stages require the read-only root observations; denied/truncated/
ambiguous observations yield safely. Ordinary user source icons and on-demand media
commands still work independently. REORDER_TASKS is declared for the public API that
returns the existing HOME task. Background starts may still be OEM-restricted;
exceptions are not success. A same-entry user interaction inside another application
is not observable as a HOME touch. Normal Activity startup may produce audible or
vendor source side effects before a session Pause can be observed. Therefore the
silence/non-disruption gate remains mandatory, including non-focusable vehicle UI.

## Acceptance and provenance

Retain T10 and tested T16 PASS; warm Auxio controls have partial positive evidence.
T11/T13/T14 remain FAIL pending a new matched candidate; T17 remains BLOCKED in the
old archive. No root timer/test/CI outcome upgrades physical results.

Run startup B1–B10 and S0/S1/S2/S6 first on one exact signed candidate, with independent
N1/N2 comparisons and fixed Maps binary. Record genuine boot versus wake, selected
mode, source APK/signer, first failing boundary, actual audible onset and touch.
Phone video spans reboot; Termux starts afterwards and captures retained post-boot
logcat/history only. Do not reboot a running collector. DoFun rollback and prior
PR17 candidate are preserved. New TESTING publication is a separate explicit step;
this task does not install an APK, merge a PR or replace rollback release assets.

Deterministic executable coverage includes ownership parser, ambiguous/wrong-user
focus, late boot dedupe, single staging, exact HOME return, permission ownership,
user/transport/screen-off cancellation, opposite-player protection, generation
invalidation, plus inherited navigation/ticker/cold-radio/handover regressions.
Full Android lint/build/minification/signing is owned by the final-head Validate CI.
