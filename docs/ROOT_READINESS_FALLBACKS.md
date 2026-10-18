# Root readiness fallbacks — PR #24

## Decision and evidence

Root is worthwhile as an alternative **caller at a proven permission/admission
boundary**. It does not establish library, tuner, GNSS, audible output or renderer
readiness, and does not make applications immortal.

Authority: current PR #24 runtime at `6489c512ee223d5d4ec700dfd1101660ea171c6e`,
the 15 October physical archive and the exact-device root/task contracts recorded
in the prior repair ledger. That archive records the navigation helper's `am task focus`
operations, but does not demonstrate these new
media/HOME fallback sequences. Their physical acceptance is NOT_RUN.
[Android 10's ActivityManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-10.0.0_r1/services/core/java/com/android/server/am/ActivityManagerShellCommand.java) exposes task focus and Activity start as
separate operations; a different caller may overcome an ordinary-app refusal.
Root can still fail in its UID/SELinux/namespace or OEM policy. No success is inferred
from privilege, exit status or process presence alone.

## Alternatives assessed

| Approach | Decision | Reason / boundary |
| --- | --- | --- |
| Root Activity admission after definite normal rejection | Implement | A distinct caller for configured Auxio/NavRadio entry only, with fresh HOME/task/focus checks and one shared admission claim |
| Explicit root-first staging comparator | Implement in Aggressive | Compares callers before any accepted start. Normal is permitted afterwards only if root definitively did not start the Activity |
| Root focus of existing HOME task after API SecurityException | Implement | Repairs a proven caller permission barrier; no HOME component launch, task creation, source resize or Activity redelivery |
| Bounded second read-only root observation after blank focus | Implement | One transient blank sample is not sufficient authority; unavailable/contradictory evidence remains uncertain |
| Existing root Maps acquisition/transition | Retain | Navigation controller/helper already owns the atomic launch claim and validated task/window operations; no parallel Maps path |
| Existing root-first NavRadio service on explicit Play | Retain | Interactive contract only; one command and observed acknowledgement; no passive activation |
| Auxio root `startservice` | Reject | Exact TS18 evidence already rejects it; browser/PREPARE remains the source-owned background contract |
| Passive NavRadio/stock Radio service or Activity startup | No expansion | Non-disruption is not established; stock protected Radio has no exported background service |
| Root media-key injection / alternate media session authority | Reject | Loses exact-source/exactly-once semantics and does not prove tuner/audio routing |
| Force-stop, data clearing, oom_adj, persistent keep-alive, Doze/AppOps whitelists, SELinux changes | Reject | No causal device evidence; disruptive, persistent or broader than the owning contract |
| Invented Topway ACC/source/MCU commands or a new boot daemon | Reject | Exact private contracts and recovery are not established; Android boot/HOME/wake/session/storage events remain the activation lane |

## Implemented contract

The ordinary Automatic mode still starts normally. Only a definite admission
refusal selects root; generic runtime errors, accepted-but-unobserved starts and
timeouts do not. Aggressive selects root first under the same safety checks. A
root pre-admission failure or explicit source rejection can permit one normal
attempt; uncertain root outcomes never do. No persistent tuning is installed.

The root adapter resolves the **current installed exported/enabled launcher entry**,
requires a normal non-system application in the launcher's Android user, and allows
only `com.tw.media` / `com.navimods.radio`. Stock/vendor apps, disabled/non-exported
entries, other users and substituted components are blocked. No new exported
Android endpoint, permission grant or root service is added.

New verbs in the existing private 0700 navigation helper:

- `startup-source`: validate the known HOME task on display 0 in fullscreen,
  require a single launcher/HomeAlias Activity with no extra settings/permission
  history, confirm the current resumed task and
  focused window, then issue one normal MAIN/LAUNCHER Activity admission as root
  for the exact configured entry. Existing tasks use normal Android reuse rules.
  `STARTUP_SOURCE_DISPATCHED` is admission only; the coordinator must observe the
  actual source task/content, settle unwanted playback and return HOME.
- `startup-home`: validate both occupied tasks, exact source entry/fullscreen mode,
  current user, foreground and window focus. Focus **only the recorded existing
  HOME task**, then verify HOME foreground/window ownership. This verb never sends
  another source/HOME Activity intent or changes geometry. It is admitted only
  after a normal HOME move throws SecurityException, not after silent/uncertain
  return or arbitrary runtime exceptions.

A per-stage cancellation lease is revoked on user input, screen-off or cancellation;
stale executor work cannot begin another root request. Existing source/session
ownership, command generations, no-playing-opposite rule, notification-access gate,
call/keyguard checks and media-before-Maps sequencing remain in force. Root helper
state is rechecked immediately before each mutation. As with ordinary Android
calls, an interaction after command admission cannot revoke a completed framework
operation; phone/camera and same-entry interaction ordering need physical tests.

Source READY is now recorded **after observed HOME restoration**, rather than
before a return that might fail. A source token or successful root command never
qualifies the complete HOME-readiness transaction. HOME-return failure remains
UNCERTAIN, preserving the first failing boundary.

## Recovery while HOME is in use

Existing process/session/HOME/wake/storage events continue to reconcile healthy and
lost sources. The root routes are bounded alternatives inside that owner, not an
always-running polling loop or a second playback service. Healthy sources are not
restarted, unknown starts are not repeated, user overrides cancel foreground work,
and unavailable USB/permissions remain genuine dependencies. Root cannot guarantee
uninterrupted readiness through every process, vehicle or storage transition.

## Qualification

Deterministic production-code tests cover definite vs uncertain admission,
root-first/normal-first selection, no duplicate fallback after timeout, exact HOME
focus, changed/permission/settings ownership, source identity/UID/export gates,
stock-package exclusion and source readiness only after return. Stateful shell
models execute the actual helper verbs, not a reimplementation of their policy.

Before device testing use an exact signed candidate and preserve the PR17 rollback
and DoFun HOME. Run existing B1–B10/S0 smoke gates first. In particular compare
Automatic vs Aggressive from matched task/source states, record source APK/signer,
root UID/domain/namespace, first admission result, task IDs, startup silence,
single-press audible output and HOME return. Confirm N1/N2 remain single-flight.
Normal and root refusal can be tested deterministically; do not revoke device
permissions or damage a source merely to manufacture a physical failure.

No physical PASS, firmware/OEM write, device install, main merge or TESTING draft
replacement is implied by CI. A new signed TESTING snapshot and device acceptance
remain required before release qualification.
