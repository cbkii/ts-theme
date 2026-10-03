# TS18 navigation-task lifecycle correction

Owner: `cbkii/ts-theme`, cumulative PR #14, after #10 → #11 → #13.
Organic Maps, SystemUI and Topway protected state are unchanged by this correction.

## Evidence and implemented policy

The supplied 2 October captures retain Organic Maps task 9472 / PID 4889 across
app switching. That task reaches mode 1 and later returns to HOME mode 5. The
historical repeated TASK_NOT_FOUND results therefore do not prove task death.

The helper now tries the rich activities dump and an independent API-29 ATM
stack query. Successful recents/stack inspection and no package process are all
required before returning TASK_NOT_FOUND or cold-launching. Unreadable or
contradictory evidence remains TASK_OBSERVATION_UNCERTAIN. A bounded raw dump is
retained at `/data/adb/ts18-launcher/task-miss-latest.txt` on a parser miss.

Fullscreen requests reacquire a suitable existing task before any launch. Warm
tasks prefer the named `setTaskWindowingMode` API; an explicit-user fallback may
use the already-proven exact-task Activity transition when that API is unavailable.
There is no ordinary package-launch fallback for an uncertain warm task. A cold
fullscreen launch requires confirmed absence and explicitly requests mode 1.

Drawer/launcher overlay parking retains mode 5. HOME stopping, or a bounded
focus-loss check proving an unrelated task owns presentation, changes the managed
task to mode 1 with `toTop=false`. It has no Activity/focus fallback. HOME return
cancels pending departure work and restores mode 5 with the current panel bounds.
The bridge checks API 29, exact task/package/user/display, standard activity type,
exclusive owning stack and absence of a foreign top Activity before changing mode.
Q changes a stack's mode, so shared stacks are deliberately rejected.

Repository/JVM tests do not establish actual TS18 root Binder permission or visual
behaviour. Unsupported background transitions preserve authority and log
BACKGROUND_MODE_UNSUPPORTED; this remains an incomplete physical qualification.

## Install and collect

Use the final PR #14 TESTING APK, whose BUILD_INFO, APK SHA-256 and signer record
match the final source commit. Do not substitute the older PR #13 a214d45 APK.
Keep DoFun available for recovery. Do not change HOME, vendor properties or module
settings for this test. Record the installed candidate hash rather than gating
collection against a historical hash.

Copy the script to Termux-private storage, then run from ordinary Termux:

```bash
cp /storage/emulated/0/Download/collect-nav-task-lifecycle.sh "$HOME/"
chmod 700 "$HOME/collect-nav-task-lifecycle.sh"
bash "$HOME/collect-nav-task-lifecycle.sh" --probe-mode --seconds 600
```

`--probe-mode` performs a same-current-mode Binder capability exercise with
`toTop=false`. It does not launch/focus an Activity or write vendor/system state.
Omit this option for an entirely read-only run. Identity/SHA mismatches, optional
command failures and missing root evidence are recorded, not admission gates.
Invalid arguments or inability to create private output are fatal.

Wait for OBSERVATION READY, then repeat or modify these manual transitions at
your own pace. Returning to Termux itself is an ordinary external-app transition.

1. HOME has the compact map; open/close the app drawer. Maps must stay mode 5.
2. Open MiXplorer/Termux from HOME. Maps must become mode 1 without covering the
   selected app. Open Maps from Recents/direct SystemUI switching: fullscreen.
3. Return HOME: the same task returns to mode 5 / current panel bounds. Repeat.
4. Tap the rail Maps control and failure-panel Open fullscreen if exposed. Both
   must resolve the existing task and verify mode 1. No spurious download Activity
   or navigation restart should be introduced for a warm task.
5. Exercise legitimate Maps permission/download Activities if naturally present.
   Do not clear app data or revoke grants to manufacture this case.
6. Repeat after launcher-process restart. Reboot/cold boot/ACC/call/reverse-camera
   remain separately recorded qualification boundaries; do not infer them passed.

On a failure, leave it visible for a few seconds before Retry. Then return to
Termux and press Ctrl-C once. Collection also stops at its bounded duration or
40 checkpoints. It exports ZIP (tar.gz fallback) plus SHA-256 under
`/storage/emulated/0/Download/ts-theme/`. Attach the archive and hash, with short
observations of compact/fullscreen behaviour and whether another app lost focus.
Do not upload sensitive raw evidence publicly; inspect it before sharing.

## Acceptance

Pass requires the same warm task/PID, correct HOME/external mode transitions,
no foreground theft, successful explicit fullscreen recovery, and no false
task-death decision from a single observation surface. Capability refusal,
collector warnings and conflicting observations remain BLOCKED/UNKNOWN as
appropriate. Archive creation alone is not a successful physical result.

Rollback: select Fullscreen only in launcher Navigation settings or restore the
previous qualified launcher APK with the matching signer. Keep DoFun enabled.
No protected files/partitions, firmware, SELinux or OEM registration are changed.
