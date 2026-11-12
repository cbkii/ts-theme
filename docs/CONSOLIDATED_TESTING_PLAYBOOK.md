# Consolidated TESTING candidate: physical acceptance

Use only the signed final-head TESTING launcher and its companion Auxio build listed in the handoff. Compare BUILD_INFO source/built/head SHA, signer and APK SHA-256 before installation. Keep the previous signed APK for rollback. DoFun stays installed/enabled; do not clear any app data or replace stock software.

Install the two application updates, retaining settings. Music and Radio default to M2 when unset; an explicit Legacy setting remains available for rollback. Confirm the existing HOME selection, app choices and local permissions. Complete real Organic Maps permission/settings screens normally.

Before the first scenario run the collector once (command below) and compare `apk-identity.txt` with the hand-off SHA-256 values. Each line reads `package sha256 path`; match the `com.cbkii.ts18launcher` and `com.tw.media` lines by package name, not by position. A hash mismatch or a `NOT_INSTALLED` line means the campaign is not testing the delivered pair; stop and reinstall. If the `apk-identity` section itself is BLOCKED or WARN in `STATUS.tsv` (for example without root), the collector could not establish identity: compare the versions shown in Android's app details with the hand-off instead and note that the hash was not checked. BLOCKED sections elsewhere are collector limits and do not stop the campaign.

For each row below, record PASS/FAIL/NOT_RUN and a short timestamped observation. Do not repeat a visibly failing scenario many times: capture its earliest failure first.

| Stage | Actions and acceptance |
|---|---|
| First HOME | HOME responds while Music, Radio and Maps prepare independently; no unwanted audio/source change or hidden media foregrounding |
| Music | One Play starts the restored item inside its bounded window; no late automatic playback after an unconfirmed/expired request; Next/Pause/metadata remain correct |
| Radio | Warm session: one Play and transport work. Cold unsupported protocol: truthful app-opening fallback; no guessed tuner/service activation. Verify actual audibility separately |
| Handover | While one source plays, request the other; old source is not paused before replacement playback is observed; no duplicate commands |
| N2 | Navigation rail, Retry, fullscreen, HOME and windowed return preserve the same warm task/active route; map pixels and touch work; unrelated foreground apps stay undisturbed |
| Permissions | Organic Maps permissions/settings can finish without task destruction, focus theft or repeated starts |
| Catalogue | Drawer/picker remains responsive; multi-Activity aliases are distinct; component shortcuts survive export/import and same-package fallback after removal/disable |
| Process restart | Restart launcher and source separately through ordinary Android controls; recover readiness without duplicate playback or loss of valid Maps task |
| Android reboot | Repeat first HOME/Music/Radio/N2 checks and label this Android reboot |
| Cold power | Repeat after genuine power removal/start; label separately |
| ACC sleep/wake | Repeat after a genuine vehicle ACC cycle, recording actual sleep/wake conditions; screen-on alone is not proof |

Optional: Settings → startup/return diagnostics → Sample 10s, then exercise HOME/drawer/navigation. `TS18Perf` reports frame intervals, slow intervals, CPU time and one off-main-thread PSS snapshot. Input received is not action completion; use visible response and timestamped observations for latency. A screen recording proves visual behaviour only unless audio recording was independently verified.

## Reading the media timeline

With root available, `timeline.txt` holds the launcher's `TS18Media` lines. For M2 the useful ones are:

| Line | Meaning |
|---|---|
| `m2/state · MUSIC avail=LIVE state=2 play=1 loaded=0 deadline=1 …` | What a Play tap would meet at that moment. `play` is whether the session advertises Play at all; with `play=0` a tap is refused as unsupported whatever else is set. `loaded` only means the session has published an item id or title. With `play=1`: `loaded=1` gets ordinary Play, `loaded=0 deadline=1` gets one bounded Play, and `loaded=0 deadline=0` is refused with "needs a loaded item". `avail` other than `LIVE` with `startable=1` means a tap first connects the source's media browser |
| `m2/deadline-dispatch` / `m2/dispatch · MUSIC PLAY` | The single command actually sent for a tap, bounded or ordinary |
| `m2/result · MUSIC PLAY_PAUSE ACKNOWLEDGED…` | How that tap ended. `ACKNOWLEDGED`: the session reported the requested state. `SENT_UNCONFIRMED`: sent once, no confirming state before the deadline (not a rejection, never retried). `UNSUPPORTED`: nothing sent. `SOURCE_LOST`: the pinned session disappeared or changed; record what the source app was doing. `FAILED`: nothing could be sent (no session, no access, ambiguous source) |
| `m2/start-offered`, `start-failed` | Outcome of a media-browser connection |

The state line is the launcher's view at the time it was written; always read it together with the dispatch and result lines that follow the tap.

Two captures settle the open Radio questions without changing anything. With NavRadio+ already open and paused, a `RADIO` state line with `play=1 loaded=1` means Play from HOME is expected to work; `loaded=0 deadline=0` means the installed build publishes no station while paused and Play is refused by design: record it as an observation, not a launcher failure. `radio.txt` lists the services and intent filters the installed build actually exports, which is the evidence needed before any background Radio start could be considered.

If Music or Radio misbehaves under M2, set that source back to the Legacy engine in Settings, note the time, and continue the remaining rows; this is the supported rollback and does not need a reinstall.

## Collector

Extract the delivered `TS18-testing-qualification` folder into `Download`, keeping the collector beside this playbook. From a repository checkout instead, copy `scripts/termux/qualify-consolidated-candidate.sh` from the candidate head to that same folder. After each stage run the read-only collector from Termux:

```sh
bash /storage/emulated/0/Download/TS18-testing-qualification/qualify-consolidated-candidate.sh smoke
```

Replace `smoke` with `restart`, `reboot`, `coldboot` or `acc-wake` as appropriate. It does not launch, stop, tap, Play, grant permissions or change settings. Evidence is exported under `Download/TS18/qualification/`. Framework dumps and the launcher timeline need root; if root is unavailable those sections are BLOCKED in `STATUS.tsv` (a collector limit, not an application result) and the remaining captures still seal. Add your audible/visual observations to the accompanying campaign notes; a collector exit code is not a feature PASS.

No physical TS18 acceptance is claimed until this campaign is actually performed. NavRadio cold activation and OEM map visibility/touch remain explicit exact-device questions.
