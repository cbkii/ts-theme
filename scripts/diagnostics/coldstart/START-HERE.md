# One fixed startup profile — validation kit v3

Use the matching launcher, Maps and Auxio TESTING candidates. The profile is fixed to N2 / Bridge / fullscreen departure with root-assisted silent preparation. Do not select or compare alternate profiles. An old Auxio APK without the prepare-only capability is reported blocked; it is never silently automated. A saved Maps startup OFF choice remains OFF; enable its compact-startup setting once if that was your earlier choice.

# TS18 Music + Maps + Radio cold-start diagnostics

This kit investigates ts-theme HOME features: usable Organic Maps, audible Auxio-TS music and configured Radio as fast and reliably as possible (aiming for about **3 seconds**), including real Android boot, switching apps and returning HOME. It collects evidence; it does not repair the integrations or declare device acceptance from logs.

There are exactly two device scripts:

| File | Purpose |
|---|---|
| `ts18-coldstart-boot.sh` | Detached Magisk `service.d` collector, at most 8 minutes per invocation, including storage waiting and cleanup. |
| `ts18-coldstart-validate.sh` | Install/status/stop/export menu and guided P/S/F/N observations. |

Use this only while safely parked. No source app is opened automatically. The kit does not press Play, force-stop or clear apps, bind their services, query their providers, invoke the navigation root helper, clear logcat, change permissions/settings, reboot, or change the default HOME app.

## Quick start

1. Extract the ZIP in Android **Download**. The two scripts should be together at:
   `/storage/emulated/0/Download/TS18-ColdStart-Kit/`
2. Open Termux and enter your **existing `s` / `st` root lane**. Do not install another root helper. Paste this one command:

   ```sh
   /system/bin/sh /storage/emulated/0/Download/TS18-ColdStart-Kit/ts18-coldstart-validate.sh
   ```

3. Choose **1 Install boot collector**, then **0 Exit**. Installation verifies the exact companion collector; it refuses to overwrite a different file. No reboot occurs automatically.
4. Start a **phone video with sound**, then perform a real Android reboot using the normal Android controls. Film the first appearance of HOME. Do not open Auxio, Maps, Termux, or Retry before observing the first map and making the first single HOME Music Play tap. Leave the display observable for at least 10 seconds after a failure. Capture the whole sequence, not only the eventual success.
5. Now open Termux, enter the same root lane and paste the same command. Choose **2 Status**, then **3 Guided tests**. Answer Y only if you filmed a real reboot without priming any source app. The guide separately verifies that the capture belongs to this Android boot and originated from `service.d`.
6. Follow the short actions. Normally only Enter and **P / S / F / N** are needed; notes are optional. The guide requests a quiet period before each timed action. Wait for **Ready**, return HOME, wait about 5 seconds, perform the stated action once, observe for at least 10 seconds, then return to Termux. The next test is not launched automatically.
7. Choose **5 Stop**, wait until **2 Status** says **CLOSED**, then **6 Export**. Share the resulting `.tar.gz`, its `.sha256`, and the phone video. All are in `Download/TS18-ColdStart/`. If the 8-minute capture already ended, export it directly.

Optional integrity check before entering root, if your existing shell has `sha256sum`:

```sh
cd /storage/emulated/0/Download/TS18-ColdStart-Kit
sha256sum -c SHA256SUMS
```

The guide itself verifies the collector even if you skip that optional command. Menu **4** starts another manual capture when needed. A manual capture is labelled manual and cannot replace missing boot evidence. Menu **7** removes only the exact matching installed boot script. Captures remain. A running collector retains its own original deadline after uninstall.

## What counts as success

| Key | Meaning |
|---|---|
| P | Intended function works reliably at an acceptable speed, supported by observation/video. |
| S | Function works but feels noticeably slow; record the time if known. This is not automatically a functional failure. |
| F | Function fails, wrong state/task appears, or repeated actions are necessary. |
| N | Skipped, blocked, no observation, or timing unknown. |

Measure the map boot case from the first visible HOME to meaningful, usable compact map pixels and touch. Measure music from the actual HOME Play tap to audible intended playback. Measure a HOME return from the actual HOME action to usable compact map and controls. A small overrun of three seconds is acceptable. Reliability and the fastest practical result decide acceptance; preserve actual times rather than applying a hard cutoff. A process, Activity, Surface, `PLAYING` MediaSession, or metadata update alone proves neither useful pixels nor sound.

Use the phone video/audio to identify the actual tap and completion. The script's “armed” time is a preparation marker, not the tap timestamp. `getevent`, framework event logs and monotonic clock anchors help align the video, focus changes, task lifecycle and music commands. Do not use an on-device screen recorder for the primary latency measurement: it adds load and may not capture the actual audio route.

An ACC wake, display wake, process restart and full Android boot are different situations. The boot ID establishes Android-boot identity; it does not prove either app was still cold when tapped. Logs/process birth times must establish whether the integration or an OEM service had already started the app. Do not use force-stop to manufacture a supposed boot result.

## Guided coverage

| Case | Observation |
|---|---|
| B0_MAP | First HOME after real reboot: compact map pixels and touch. |
| B0_MUSIC | First HOME Play after that reboot, before opening Auxio. |
| B0_RADIO | First HOME Radio Play after reboot, without opening Radio manually; N if unobserved. |
| M1 | One HOME Music Play action without opening Auxio. |
| M2 | Explicit Music icon opens Auxio; this deliberately primes later comparisons. |
| H1 | Auxio → normal HOME: compact map, controls, preserved playback. |
| NAV_OPEN | HOME navigation control → intended fullscreen Maps task. |
| H2 | Fullscreen Maps → HOME: compact map reattachment and input/focus. |
| MAP_BACK | Root fullscreen map Back: preserve task and return compact; close transient UI first. |
| MUSIC_BACK | Auxio Back: recover HOME and preserve playback intent. |
| H3 | Ordinary Android Settings → HOME: both integrations and no late focus takeover. |
| M3 | One Music Play/Pause action after returning HOME. |
| RADIO_PLAY | Single HOME Radio Play without manually priming Radio. |
| RADIO_HOME / RADIO_BACK | Radio app return via HOME and Back, separately. |
| RADIO_MUSIC | Explicit Radio/Music handover with one tap per action. |
| R1 | Three Auxio → HOME → Maps fullscreen → HOME cycles; every return must work reliably at an acceptable speed. |

If boot film/current-boot evidence is missing, B0 is automatically N. Skipping a case also records N without asking for a success result. If the collector ends during testing, the guide offers a fresh **manual** capture; the original boot run is retained with its own ID. Export each relevant run before starting another if you need a separate archive; the menu exports the latest run.

On failure, keep the failed state. Do not open Auxio/Maps to “help” it, tap Retry, issue a second Play, or reboot until evidence is preserved. A delayed focused probe is requested after a reported failure. Menu **8** requests an optional screenshot after 8 seconds (deferred until the first 60 seconds and any active quiet period have ended). Return to the failed screen promptly. A screenshot is not requested automatically during boot or the primary latency observation. If the state must be changed to return to Termux, the phone video remains the evidence of that original failure.

## Where output goes and how it is bounded

Every generated capture, lock, temporary probe file, marker, validation result, receipt and archive is under:

`/storage/emulated/0/Download/TS18-ColdStart/`

The only installed item is `/data/adb/service.d/97-ts18-coldstart.sh`. The installer enforces a **48,000-byte source limit** and at most **96,000 bytes** for its managed source/staging copies. **Generated capture output under `/data/adb` is zero bytes.** The validator stays in Download. No private-storage or `/data/adb` spooling fallback exists. This guarantee concerns this kit's writes; it cannot limit independent writes made by Magisk, Android, apps or their existing helpers. The kit only reads the navigation helper's known state/identity journals and never executes it.

Download must already exist and be writable. If unavailable early in boot, the collector waits inside its original 8-minute deadline. It then records both service-entry and output-ready uptime. Retained logcat may recover part of the gap; overwritten logs and input events before output readiness cannot be recovered. If Download never becomes available, the collector exits without creating capture files elsewhere. “No capture” is not success.

Per run, reserved probe/stream output is at most **96 MiB** plus small bounded metadata. Limits include 24 MiB main/system/crash logcat, 6 MiB event logcat, 6 MiB input events, 6 MiB process samples, and bounded snapshots. Probe metadata explicitly reports truncation, command failure and timeout. A compatible logcat-format fallback is retained separately. New runs refuse to start below **256 MiB free** or once the kit folder reaches **640 MiB**. These checks are not a filesystem quota against other writers. No previous captures are deleted automatically.

Compressed exports are capped at **32 MiB**. A cap, timeout or failed gzip CRC retains partial evidence and does not report export success. Review the size/error and retain the raw run folder if export cannot complete. Capture writes stop before final hashing; a `COMPLETE.txt` seal requires proven cleanup and time remaining. `CLOSED` without the seal, `CLEANUP_UNPROVEN`, missing files, or changed hashes require investigation. “Complete capture” never means device acceptance.

Installation persists across boots, so every real Android boot may produce a new bounded run until you uninstall. If space limits block a new run, first export the evidence you need, then remove selected old kit captures using Android Files. Do not delete unrelated Download content or Magisk files.

## Evidence and observer overhead

The first 60 seconds use continuous logs, raw input events and lightweight target-process samples; heavy Binder dumps and APK hashing are deferred. Target PIDs are discovered about once per second. Existing target process state/CPU/wait-channel samples are taken about four times per second initially, then once per second. The timing guide requests a 25-second lightweight-only window and waits for acknowledgement. A probe already in progress must finish first. Slow/manual actions outside that window may overlap later probes; use the timestamps to check this before interpreting latency.

After 60 seconds: bounded task/window/MediaSession snapshots, installed package/service/app-op state, HOME resolution, notification-listener access, boot/display/power/storage context. Later probes add audio routing/focus, AudioFlinger/audio policy, Surface names, per-app memory/frame stats, known launcher preferences, and helper journals. Installed APK/split hashes are deferred until 150 seconds to keep that I/O out of initial cold startup. Nothing triggers a source app merely to obtain a missing result. Permission-denied, unsupported service, absent process, failed probe and truncation remain visible as evidence gaps.

Look first at `CONTEXT.txt`, `STATUS.txt`, `events.tsv`, `clock-anchors.tsv`, `workers.tsv`, and each `probes/*/meta.txt`. The corresponding `output.txt` contains evidence; an optional successful screenshot uses `screenshot.png`. `validation-*/results.tsv` has the human outcomes and capture IDs. `SHA256SUMS` covers the closed run; the export has its own checksum. Keep complete chronological logs, including the first failure, for investigation.

Logs can include track paths/titles, other packages, Bluetooth details and location-related messages. Raw input capture records touch/key events, not a video or microphone recording. Avoid entering passwords during capture and review locally before sharing. Nothing is uploaded automatically by the device scripts.

## If something is blocked

- **Not root / BusyBox unavailable:** use your established Magisk `s`/`st` environment. Do not install packages, grant broad permissions or switch SELinux to permissive to make a probe pass.
- **No capture after boot:** unlock, check Download and space, then check Status. Keep the boot video. Start a separate manual capture if needed; record boot N.
- **Quiet window not acknowledged:** the guide records N. Check whether the run expired or a command was blocked. Do not report P from an unperformed test.
- **Different reserved service file / changed kit:** nothing is overwritten. Use the matching old kit to uninstall its verified collector, or investigate the conflicting file. Do not paste over an unknown script.
- **Missing probe / permission denied / no seal:** preserve the raw folder and video. These are evidence gaps, not a reason to call the integration healthy.

Read `SCOPE-AND-EVIDENCE.md` for the exact source baseline and the limits of host verification. This kit has not yet been executed on your TS18.

## v3 expanded radio and resource evidence

The same collector now covers `com.navimods.radio` (NavRadio+), `com.tw.radio` (stock radio, if installed), Auxio, Maps and HOME, including their named secondary `:service` processes. Package inventory and launcher role preferences identify a different configured radio; its raw system/session/audio logs remain available, but per-package hashes/process probes are limited to the named targets. No radio service/broadcast/provider command is sent. Android MediaSession `PLAYING` is not proof of tuner/DSP output.

A five-second, read-only resource stream records CPU time/load, memory, paging, disk counters, available pressure counters, CPU frequencies and temperatures. Deferred probes include kernel ring/SELinux errors, device-idle, input state, app jobs/services/app-ops, media router, audio focus/policy/AudioFlinger, surfaces and frames. Unsupported OEM probes retain their error/timeout status. These observations can contain media titles, station names, touch coordinates, package names, paths and incidental location details in system logs. Review the local archive before sharing it.

The installer upgrades only the exact known v1/v2 collector hashes. All limits remain: 480 seconds, Download-only generated output, 96 MiB reserved capture, 180 probes, 32 MiB compressed export, no deletion of old captures. Extra evidence is best-effort within these limits; exhaustion is recorded as SKIP, never interpreted as absence.
