# Consolidated TESTING candidate: physical acceptance

Use only the signed final-head TESTING launcher and its companion Auxio build listed in the handoff. Compare BUILD_INFO source/built/head SHA, signer and APK SHA-256 before installation. Keep the previous signed APK for rollback. DoFun stays installed/enabled; do not clear any app data or replace stock software.

Install the two application updates, retaining settings. Music and Radio default to M2 when unset; an explicit Legacy setting remains available for rollback. Confirm the existing HOME selection, app choices and local permissions. Complete real Organic Maps permission/settings screens normally.

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

Extract the delivered `TS18-testing-qualification` folder into `Download`, keeping the collector beside this playbook. From a repository checkout instead, copy `scripts/termux/qualify-consolidated-candidate.sh` from the candidate head to that same folder. After each stage run the read-only collector from Termux:

```sh
bash /storage/emulated/0/Download/TS18-testing-qualification/qualify-consolidated-candidate.sh smoke
```

Replace `smoke` with `restart`, `reboot`, `coldboot` or `acc-wake` as appropriate. It does not launch, stop, tap, Play, grant permissions or change settings. Evidence is exported under `Download/TS18/qualification/`. If root is unavailable, root-only sections are BLOCKED and ordinary captures still seal. Add your audible/visual observations to the accompanying campaign notes; a collector exit code is not a feature PASS.

No physical TS18 acceptance is claimed until this campaign is actually performed. NavRadio cold activation and OEM map visibility/touch remain explicit exact-device questions.
