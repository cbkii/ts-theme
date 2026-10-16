# PR #24 finalisation and qualification ledger

Target: `cbkii/ts-theme`, `copilot/fix-cold-start-reliability`, stacked on PR #25
`a9effde5fef9b1d52ec9f3e4d167a1629dcde210`, itself on frozen PR #19
`4e59089cce98b7f768c1788c612b3a19a62957fc`. Starting #24 head:
`cb0221c9a925c57c0aeb5bee4e5f8fa83b736389`. Parents, main and alternative
PRs #21/#22 are preserved. No merge, installation or protected-system changes.

## Confirmed corrections

- A delayed N2 Activity admission can leave `task=unknown` in the launch claim.
  Adopt the unique positively identified current-user/display-0 MwmActivity into
  the accepted/uncertain/observed N2 claim before it is cleared. Preserve the
  original admission journal identity; different-task/reserved claims do not grant
  restoration. Existing foreground/foreign-Activity guards still own mutation.
- Replacing/cancelling N2 revokes `normalBootstrapInFlight`. Set it only after a
  transaction is actually admitted; a rejected admission cannot leak the exception.
- Process media-owner configuration uses effective Music/Radio package and current
  Android user. Unset Music to the installed default keeps the healthy owner.
  HOME reacquires the current owner before each source command, including the
  opposite-source handover nomination.
- Reserve independent primary and opposite-Pause acknowledgement budgets for a
  handover, within one monotonic deadline. The 3.2-second NavRadio cold interval
  applies only to Play. Dispatch remains exactly once.
- Task-present and buffering diagnostics describe their actual evidence rather
  than claiming Recents/advertised Play evidence that was never observed.

## PR #25 review dispositions

All 15 inline findings were inspected in full. These corrections are intentionally
on the integrated child #24; #25 remains unchanged and must not be merged alone.
A parent thread is not resolved merely because its child contains a fix.

| Finding (review comment suffix) | Classification at starting #24 | Integrated disposition |
|---|---|---|
| 4288961128 delayed N2 ownership | Confirmed | Adopt accepted unknown task claim; executable delayed-task test |
| 4288961151 PR #17 attribution | Confirmed | Separate historical baseline from current #25/#24 implementation |
| 4288961168 radio ticker assertion | Confirmed | Placeholder Radio title must resolve to FM |
| 4288961176 manual N2 playbook | Confirmed | Cold N2 explicitly starts without manual priming; warm comparison separate |
| 4288961189 N2 cancellation flag | Confirmed | Revoke flag on cancellation; production controller regression |
| 4288961195 configuration assertion | Confirmed | Assert reset inside reconciliation, not anywhere in Activity |
| 4288961206 compiler capability | Confirmed | Check jdk.compiler before the executable media harness |
| 4288961211 destroyed media owner | Confirmed | Effective package key, current-owner reconciliation and executable factory test |
| 4288961218 N1 default assertion | Confirmed | Assert mode 5 as well as N2 mode 1 |
| 4288961226 immediate launch assertion | Confirmed | Assert delivery before the outstanding Maps callback |
| 4288961232 absent N2 regression | Confirmed | N2 profile plus TASK_NOT_FOUND status; require second presentation |
| 4288961239 Pause acknowledgement budget | Confirmed | Late Play plus delayed opposite Pause regression |
| 4288961250 evidence label | Confirmed | TASK_PRESENT rather than PRESENT_IN_RECENTS |
| 4288961260 buffering detail | Confirmed | No advertised-Play claim without the action bit |
| 4288961266 non-Play cold delay | Confirmed | Pause/Next budgets exclude the cold Play interval |

Numeric suffixes identify the original GitHub review comments, not runtime evidence.
Exact thread identities and remote dispositions are retained in GitHub.

## Verification and deliverable contract

Focused executable regressions cover the production shell/controller/media/factory;
CI owns Android lint, JVM unit tests, debug and minified release packaging, runtime
envelope, strict ShellCheck and configuration-cache validation. Local Android SDK,
Gradle and ShellCheck are unavailable; no local Android result is claimed.
The final PR handoff records the exact head and terminal canonical run separately.

The guided source collector has regenerated embedded hashes and a new private
campaign directory, so an older installed toolkit cannot silently supply stale
instructions. Its source hash placeholder deliberately does not pass APK identity.
After the trusted TESTING workflow signs the final source, the delivered copy must
replace that placeholder with the actual signed APK SHA-256/source SHA, regenerate
embedded hashes, and include BUILD_INFO, SIGNER and workflow SHA256SUMS. Do not alter
or replace the rollback PR17 APK. P/F/N and bounded read-only capture remain intact.

## Physical gate: NOT_RUN

No TS18/ADB target is connected here. Keep draft and do not merge. Historical
T11/T13/T14 failures remain open until a matching candidate passes. Check:

1. Film genuine boot before opening Termux; leave HOME untouched up to 90 seconds.
2. Music Play once produces audible music; Radio Play once produces audible radio.
3. Compact Maps shows real pixels and aligned touch, then fullscreen/HOME without Retry.
4. Source icons respond immediately even when Maps fails. No unwanted audio/source
   switching, permission obstruction, duplicate starts or foreground theft.
5. Stop/export at the first failure. Classify dependent checks BLOCKED; do not replay
   an accepted/uncertain launch to manufacture a pass.

Only after smoke passes, run B1–B10 and extended boot, cold power, genuine ACC,
late USB, process loss, handover, permission, phone and reverse-camera boundaries.
Screen-on is a wake hint, not verified ACC. Persistent `mCurrentFocus=null` remains
BLOCKED pending authoritative exact-device observation; do not weaken ownership
checks based on missing evidence. Radio staging is an experimental TESTING path,
not qualified silent tuner preparation; Pause cannot prove OEM route restoration.
