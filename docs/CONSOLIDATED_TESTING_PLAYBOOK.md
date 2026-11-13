# Consolidated TESTING candidate: physical acceptance

Use only the signed final-head TESTING launcher and its companion Auxio and Organic Maps builds listed in the handoff. Compare BUILD_INFO source/built/head SHA, signer and APK SHA-256 before installation. Keep the previous signed APK for rollback. DoFun stays installed/enabled; do not clear any app data or replace stock software.

Install Organic Maps, Auxio, then the launcher as application updates, retaining settings. Music and Radio default to M2 when unset; an explicit Legacy setting remains available for rollback. Confirm the existing HOME selection, app choices and local permissions. Complete real Organic Maps permission/settings screens normally.

Before the first scenario run the collector once (command below) and compare `apk-identity.txt` with the hand-off SHA-256 values. Each line reads `package sha256 path`; match the `com.cbkii.ts18launcher` and `com.tw.media` and `app.organicmaps.incar` lines by package name, not by position. A hash mismatch or a `NOT_INSTALLED` line means the campaign is not testing the delivered combination; stop and reinstall. If the `apk-identity` section itself is BLOCKED or WARN in `STATUS.tsv` (for example without root), stop the campaign and obtain a successful hash/signer check before continuing: app-detail versions cannot establish the delivered signer or APK bytes, so a stale or mixed install could be accepted as the TESTING pair. BLOCKED sections elsewhere are collector limits and do not stop the campaign.

## Regression checks for this candidate

The owner physically observed three failures on the previous TESTING combination. Automated replacements remain **PHYSICAL RE-TEST REQUIRED**.

1. Film first HOME without pressing Retry. Record the complete wording and time of any “Audio refused…” message, including whether it says “Auxio”. Do not treat a browser refusal as proof of failed audio focus. The collector now includes source-browser and audio diagnostics.
2. Start one source, then use launcher Pause/Play/Previous/Next once each. The correct item/station must remain visible. Repeat with external playback keys against the same source; record any difference and the first disappearing title.
3. After the first visible Maps frame, wait at least 45 seconds, pan the map and return HOME. It must stay usable; no delayed BOOTSTRAP PENDING/unavailable state or repeated resize/launch is acceptable. Complete genuine permission/settings screens normally.

On failure preserve evidence before tapping Retry, opening the source app or rebooting. A successful command/session state does not prove audible output; task bounds do not prove map pixels or touch. N2 remains default. Settings → Startup diagnostics → Navigation method also offers explicit N1 and N0 fallback; report a fallback result separately.

For each row below, record PASS/FAIL/NOT_RUN and a short timestamped observation. Do not repeat a visibly failing scenario many times: capture its earliest failure first.

| Stage | Actions and acceptance |
|---|---|
| First HOME | Maps is admitted first, then Music and safe Radio service readiness; HOME responds and no unsolicited playback/source change occurs |
| Music | One Play starts the restored item; Auxio cold Play honours its source-enforced deadline. Record SENT_UNCONFIRMED separately: timeout of a sent standard command does not prove cancellation and must not trigger replay. No late playback after an actually cancelled/expired bounded Auxio request; Next/Pause/metadata remain correct |
| Radio | One Play works with a supported session or configured NavRadio+ automatically uses the bounded masked cold fallback. Mark other unsupported cold packages BLOCKED by their missing contract; opening their icon is a setup/diagnostic path, not a one-tap playback pass. No manual app priming, bright splash or second tap; HOME/N2 returns and the source survives backgrounding. Genuine setup/permission screens remain visible. Verify audibility separately |
| Handover | While one source plays, request the other; old source is not paused before replacement playback is observed; no duplicate commands |
| N2 | Navigation rail, Retry, fullscreen, HOME and windowed return preserve the same warm task/active route; map pixels and touch work; unrelated foreground apps stay undisturbed |
| Permissions | Organic Maps permissions/settings can finish without task destruction, focus theft or repeated starts |
| Catalogue | Drawer/picker remains responsive; multi-Activity aliases are distinct; component shortcuts survive export/import and same-package fallback after removal/disable |
| Process restart | Restart launcher and source separately through ordinary Android controls; recover readiness without duplicate playback or loss of valid Maps task. Cancel cold Radio and verify no late unmasked launch or stuck loader |
| Android reboot | Repeat first HOME/Music/Radio/N2 checks and label this Android reboot |
| Cold power | Repeat after genuine power removal/start; label separately |
| ACC sleep/wake | Repeat after a genuine vehicle ACC cycle, recording actual sleep/wake conditions; screen-on alone is not proof |

Optional: Settings → startup/return diagnostics → Sample 10s, then exercise HOME/drawer/navigation. `TS18Perf` reports frame intervals, slow intervals, CPU time and one off-main-thread PSS snapshot. Input received is not action completion; use visible response and timestamped observations for latency. A screen recording proves visual behaviour only unless audio recording was independently verified.

## Reading the media timeline

With root available, `timeline.txt` holds the launcher's `TS18Media` lines. For M2 the useful ones are:

| Line | Meaning |
|---|---|
| `m2/state · MUSIC avail=LIVE state=2 play=1 loaded=0 deadline=1 …` | `play` records the advertised action. A pending tap waits for late actions within its original budget. `loaded` requires an item identity plus PAUSED/PLAYING. Loaded Auxio gets ordinary Play; unloaded Auxio requires its bounded HOME_PLAY_V1 capability. Third-party Radio may use ordinary advertised Play to load/resume its own station. A startable absent source first connects its owning service |
| `m2/deadline-dispatch` / `m2/dispatch · MUSIC PLAY` | The single command actually sent for a tap, bounded or ordinary |
| `m2/result · MUSIC PLAY_PAUSE ACKNOWLEDGED…` | How that tap ended. `ACKNOWLEDGED`: the session reported the requested state. `SENT_UNCONFIRMED`: sent once, no confirming state before the deadline (not a rejection, never retried). `UNSUPPORTED`: nothing sent. `SOURCE_LOST`: the pinned session disappeared or changed; record what the source app was doing. `FAILED`: nothing could be sent (no session, no access, ambiguous source) |
| `m2/start-offered`, `start-failed` | Outcome of a media-browser connection |

The state line is the launcher's view at the time it was written; always read it together with the dispatch and result lines that follow the tap.

The installed API29 NavRadio binary remains the authority for its service and tuner contracts. An empty paused item does not by itself invalidate an advertised Play action. `radio.txt` records actual exported components. Do not treat an Android12+ reference APK as proof of the installed Android10 contract. See [readiness behaviour and qualification](CONTINUOUS_READINESS.md), including when passive Radio activation is deferred to first Play.

If Music or Radio misbehaves under M2, set that source back to the Legacy engine in Settings, note the time, and continue the remaining rows; this is the supported rollback and does not need a reinstall.

## Collector

Extract the delivered `TS18-testing-qualification` folder into `Download`, keeping the collector beside this playbook. From a repository checkout instead, copy `scripts/termux/qualify-consolidated-candidate.sh` from the candidate head to that same folder. After each stage run the read-only collector from Termux:

```sh
bash /storage/emulated/0/Download/TS18-testing-qualification/qualify-consolidated-candidate.sh smoke
```

Replace `smoke` with `restart`, `reboot`, `coldboot` or `acc-wake` as appropriate. It does not launch, stop, tap, Play, grant permissions or change settings. Evidence is exported under `Download/ts-theme/qualification/`. Framework dumps and the launcher timeline need root; if root is unavailable those sections are BLOCKED in `STATUS.tsv` (a collector limit, not an application result) and the remaining captures still seal. Add your audible/visual observations to the accompanying campaign notes; a collector exit code is not a feature PASS.

No physical TS18 acceptance is claimed until this campaign is actually performed. NavRadio cold activation and OEM map visibility/touch remain explicit exact-device questions.

## Optional synchronised video, up to five runs

Reuse `scripts/diagnostics/campaign/campaign.py` and its [existing guide](../scripts/diagnostics/campaign/START-HERE.md) for a failure that needs a live timeline/video. It uses the current caller, never starts apps or changes HOME, and needs the existing Termux Python and authorised root for local screen recording. Keep the script in private Termux storage; use the original `$PREFIX/bin/su` as that guide describes.

From the same authorised caller, run (replace `/absolute/private/path/campaign.py` with its real private location):

```sh
/data/data/com.termux/files/usr/bin/python /absolute/private/path/campaign.py capture \
  --transport local --campaign rc-regressions --trial 1 --lifecycle home_resume \
  --duration 300 --state-root /data/ts18-campaign \
  --out /storage/emulated/0/Download/ts-theme/campaign
```

Use trial numbers 1–5 and the actual lifecycle label `home_resume`, `process_restart`, `reboot`, `cold_boot` or `acc_wake`. Start it before the actions being analysed. Recording is 1280×720 at 2 Mbit/s in segments of at most 150 seconds, with clock anchors, hashes and cleanup status; Android 10 offers no FPS control here. Add `--no-video` for the low-load comparison. Screen recording captures no speaker/radio audio and adds load: use phone video with sound for the primary cold-start/audibility result. A pre-reboot process cannot capture the next boot; film it or use the existing coldstart kit, with its separate bounds and evidence labels. Nothing is uploaded automatically.
