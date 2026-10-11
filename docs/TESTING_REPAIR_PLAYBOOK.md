# TS18 repair candidate — guided validation

11 October 2026. Physical qualification is **NOT_RUN**. Use the signed repair candidate identified in the PR handoff; do not choose an APK by its upload date. PR16 `ae2dd07` is the comparison baseline with reported regressions, not the repaired build. Keep a known rollback APK and DoFun available.

## 1. Install and start once

1. Park safely. Install the exact launcher APK named in the handoff through Android's normal installer. Keep the currently selected Maps APK unchanged throughout launcher comparisons. Recommended fixed baseline: PR55 `db57091`; PR58 `c454a66` is a separate subsequent comparison, not a simultaneous upgrade.
2. Put the supplied `ts18-validate.sh` in **Download** on the TS18. In the ordinary Termux shell, paste:

```sh
mkdir -p "$HOME/bin" && cp /storage/emulated/0/Download/ts18-validate.sh "$HOME/bin/ts18-validate.sh" && chmod 700 "$HOME/bin/ts18-validate.sh"
```

This copies the script into private storage and makes it executable. If access is denied, use your normal Termux storage-permission setup; do not change Android system permissions or file ownership manually.

3. Enter the existing TS18 Termux Kit **s/st root lane** as usual, then paste:

```sh
/system/bin/sh /data/data/com.termux/files/home/bin/ts18-validate.sh guide
```

The same command resumes later; use the Up arrow to recall it. No package names, task IDs, paths or timestamps need typing during tests. Every answer is followed by **Enter**. The Android Back button does not answer the prompt.

4. Select **1** for baseline. The guide reads installed APK paths, hashes, versions, selected sources, user, HOME and permissions. Compare its installed hash with the signed handoff. A repository-source copy of the collector deliberately contains `FROM_SIGNED_HANDOFF` until the final APK exists; this is a mismatch, never a pass. The delivered copy is pinned after publication. A new launcher includes text build identity inside its APK and in **Settings → Diagnostics & system → Testing methods → Build and test identity**.

The kit is local and read-only for device state. It writes its own private captures and exports, but never installs/launches apps, changes settings, force-stops, clears data, resets a launch claim or calls the map helper. The new campaign is separate from older captures. Export old evidence with the old script before switching.

## 2. Run the smoke gate first

Select **s**. Prepare a normal music library/storage connection and a radio station. In the launcher, select **Settings → Diagnostics & system → Testing methods** and begin with N1 / Bridge / L2. The guide starts a bounded capture with a measurement window of up to 300 seconds; preflight and post-capture inventory take additional time. Wait for **Trace is measuring now** before acting. If the measurement window expires between steps, the guide starts a new capture. Treat each capture as separate evidence: there can be a gap between them, so do not infer continuity across a restart.

For each prompt, switch to HOME, perform the action once, return to Termux and press **p** (pass), **f** (fail), or **n** (not run/uncertain). Only report what you personally saw/heard. These are separate observations:

| Step | Action | Pass requires |
|---|---|---|
| S1 | Music Play once | Audible music, not just a PLAYING flag |
| S2 | Radio Play once | Audible radio without a second Play |
| S3 | Music app icon, including while Maps starts | Music app opens despite compact-map failure |
| S4 | Radio app icon | Radio opens once; no earlier queued app appears later |
| S5 | Testing methods → Open navigation normally | Maps opens with usable controls independently of root windowing |
| S6 | HOME, wait at most 45 s, pan and My Position | Visible updating compact map, aligned touch, accessible HOME controls |
| S7 | Bottom Navigation rail button once | Fullscreen intent stays fullscreen; map responds |
| S8 | HOME once, then pan | Compact return without Retry or duplicated launch |
| S9 | Only after a natural failure: Retry twice quickly | Acknowledged/coalesced recovery; bounded outcome |

S9 is conditional: choose **n** if there is no failure/Retry button. It remains untested rather than manufacturing a failure. A critical failure or uncertainty in S1–S8 stops the smoke gate and exports evidence. Do not proceed to the long campaign. On failure, leave the failed screen untouched while capture runs; do not Retry/reboot first. The guide offers an eight-second delayed checkpoint: switch back to the failed screen during that countdown. A failure note is optional. Earlier failures remain in the archive even if a later trial succeeds.

Returning to Termux changes focus. Phone video showing the full screen and finger is necessary to establish touch alignment or a brief fault between samples. If the state has already recovered, say so; a recovered screenshot does not show the original failure. A sealed archive proves collection completed, not that the app passed.

## 3. Compare methods in the same APK

After smoke passes, select **c** for Screening. Choose methods through the app UI; the collector reads the selected profile automatically. It never silently changes it. Use **r** to repeat one case; do two comparable trials per method, with a third if they disagree. Each 90-second trial has its own hashes, starting tasks/processes, profile and logs.

| Selector | Method | Instructions and interpretation |
|---|---|---|
| Navigation | N0 normal open | Compact disabled. Tap Navigation or Open navigation normally; root windowing is not required. |
| Navigation | N1 direct compact | Start at HOME. A genuinely absent task can receive one freeform launch; pending/unknown dispatch is observed rather than relaunched. |
| Navigation | N2 normal then compact | Choose Bridge. Open navigation normally, finish startup/downloads, wait for interactive map, then HOME. Convert that same task without Activity redelivery. An absent task reports NORMAL_OPEN_REQUIRED. |
| Task transition | Bridge | Same-task mode API; unsupported/refused operation reports BLOCKED, with no automatic Intent substitute. |
| Task transition | Intent comparator | N1 only. Same-task firmware Activity transition may redeliver current Activity. N2+Intent is BLOCKED. Compare on a warm fullscreen task returned to HOME; a task already in mode 5 does not exercise this variable. |
| Departure | L1 retain / L2 background fullscreen | Change only after selecting a viable launch/transition method. Other app → HOME; record whether window-mode changes cause loss. |
| Music | M0 bind / M1 advertised PREPARE | Keep Auxio fixed. M1 sends PREPARE at most once per connected token only if the installed session advertises it and is not already ready. Unsupported contracts remain blocked; no source Activity priming. |
| Radio | R1 root / R2 normal Android | Interactive service admission routes, separately selected. Neither is passive radio warming; each keeps one exact session/playback authority. |
| Radio | R0 guarded automatic | Root first; ordinary admission only after definite refusal. Timeout/uncertain results get observation, not a duplicate start. |

Record comparable **observed** initial states: no process/no task, live service without task, retained task/process restart, warm return, or first-run/bootstrap. These are different trials. Reboot is not proof of a cold app. The collector records raw evidence; ambiguous/truncated/denied observations remain unknown. Do not clear data or force-stop merely to create a label. Existing sessions can make a media admission test inapplicable; use N/Blocked and normal app-icon opening as a separate control.

A missing advertised Auxio PREPARE capability needs an exact installed-player contract review or coordinated Auxio fix, not a hidden foreground workaround. This launcher does not claim to repair arbitrary incompatible installed players. It preserves notification-access requirements and pauses the opposite source only after requested Play is acknowledged. Audible output still requires your observation.

## 4. Export and only then qualify survivors

Main menu **7** exports one `.tar.gz` plus `.sha256` to **Download/TS18-PR-Validation**. Share both and the phone video. Menu **6** shows progress; **8** shows collector state; **0** exits. Optional notes need only describe the visible/audible problem. Do not type timestamps or task IDs.

After screening selects a viable fixed profile, menu **9** retains the extended map-window cases: W1 reference; W2 other-app return (30 trials); W3 fullscreen→HOME (20); W4 genuine cold startup (20); W5 natural bootstrap/permission ownership; W6 first failure/recovery comparison; W7 overlay return; W8 layout/theme. One result is one trial. Keep every failure and report latency distributions from matched evidence. Two screening passes do not qualify a release. Change Maps to R2 only in a separate stage with a new baseline. Camera/call/ACC/boot remain separate physical gates; R3 requires device evidence of OEM composition/input failure.

Menus 2–5 retain the broader launcher, Maps, diagnostics and integration cases. Each case prints its exact action and acceptance. **n** opens Not Run/Blocked/Uncertain/Warning; a blank note is accepted. Use only the surviving profile for the long campaign.

## Evidence and interpretation

Each trial records installed base/split APK hashes and embedded source identity, selected profile/apps, user/boot/process/task state before the action, bounded logcat with helper transaction/deadline/method/phase and service admission/connection/PREPARE/Play/acknowledgement events. Window mode adds periodic screenshots, Activity/WindowManager, input routing and layer lists; failure/method checkpoints add recents, full SurfaceFlinger, insets/display, helper dispatch journals and audio state. Probe elapsed times, timeouts, truncation and collection cost remain visible. Heavy capture can perturb timing; use performance mode separately for latency claims.

Journal phases reserved/dispatching/accepted/uncertain preserve the boot/user/package claim. Elapsed time is never proof that an accepted launch was cancelled. A stale or empty claim remains conservative; **Open navigation normally** is an explicit separate escape route, not an automatic retry. Do not delete claims manually.

Look for the earliest failed boundary: exact candidate → discovery → bootstrap → mode/bounds → foreground → window/composition → pixels → touch. A live process is not a ready map Activity. A token is not readiness; PLAYING is not audible output. WMS visibility is not actual pixels. Unknown evidence stays unknown.

## Troubleshooting and optional commands

If a trace/lock is still active, use **8** and wait. If interrupted, preserve partial evidence. Never delete locks to force overlap; use the script's `stop` command and inspect status. Export refuses unsealed/active work. Storage, timeout or permission failures are collection gaps, not physical passes.

In the existing root lane, the following commands are optional; the guide invokes them for you:

```sh
/system/bin/sh /data/data/com.termux/files/home/bin/ts18-validate.sh status
/system/bin/sh /data/data/com.termux/files/home/bin/ts18-validate.sh stop
/system/bin/sh /data/data/com.termux/files/home/bin/ts18-validate.sh export
```

`status` only reports progress. `stop` requests early termination and skips heavy post-capture inventory. `export` seals a snapshot of retained evidence into Downloads; it does not upload anything. If the device reboots, the old process cannot record it: keep preboot phone video and start a fresh baseline/capture after boot, explicitly marking the missing interval.
