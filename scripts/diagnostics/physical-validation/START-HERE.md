# TS18 physical validation v3.6.0 — one operator guide

Use only the APKs named in the final accompanying selection. Keep the existing rollback APKs.
The module records the screen and logs automatically; normal recording needs no Termux commands.
Recordings are silent. Your notes establish whether the right track/station was audible.

## Phase 1 — Install and prepare

1. Park safely. Allow at least 256 MiB free in internal shared storage.
2. Install the matched Launcher and Auxio APKs as updates. Keep their data. Keep Maps unchanged
   unless the supplied selection explicitly includes a replacement.
3. Enable Developer options → Show taps if desired. Disable other boot recorders.
4. Install the supplied validation ZIP using Magisk → Modules → Install from storage.
5. The new folder is `Download/TS18-Validation-v3.6.0/`. The v3.5.0 results remain separate.
6. Reboot. From the first visible HOME, recording should start automatically once Download is
   mounted. Up to seven consecutive 45-second clips cover the approximately five-minute run.
   Encoder start/stop gaps are logged; continuous coverage is a goal, not an assumed result.

## Phase 2 — Boot 1: Music and initial readiness

1. Watch startup. Note the loading text and whether the status-bar clock remains visible.
2. On HOME, press Music PLAY once. Do not open Auxio or press NEXT to make it work. Note the
   delay and audible track. If it fails, wait for the bounded result and write that down.
3. Once playing, press NEXT 30 times, allowing about one second between taps. Watch that the
   title follows each audible track. Pause for ten seconds and check the notification title.
4. Test PREVIOUS after more than three seconds of playback, then NEXT → PREVIOUS quickly,
   then NEXT → NEXT → PREVIOUS quickly. Record which track/start you heard. The app's configured
   rewind setting controls semantics; distinguish a rewind from a preceding-track change.
5. Continue using HOME normally until the capture ends. Do not force-stop apps during this
   first startup test. If you do so later, note the exact action and time.
6. Wait for `READY-TO-UPLOAD-boot-1.txt` in the output folder. Magisk's module Action shows status.
   An active folder with `CAPTURE-IN-PROGRESS.txt` is not sealed. Allow roughly six minutes total.

## Phase 3 — Boot 2: Maps presentation

1. Reboot. This creates the second boot-ID run, not a continuation of Boot 1.
2. Set a route and press START. Note whether normal compact HOME presentation appears without Retry.
3. Press the launcher's bottom navigation icon for fullscreen Maps; press SystemUI HOME to
   return to the dashboard. Repeat ten times if the transitions remain healthy.
4. Exercise SystemUI BACK separately. BACK is Maps' own lifecycle/navigation action and may
   close a screen; it is not interchangeable with HOME. Record any route or window change.
5. Note black map content even if route instructions/buttons remain visible. Try ordinary map
   touch/zoom when stopped safely. Do not repeatedly Retry or relaunch to hide a failure.
6. If a Maps screen closes and a retained Recents card remains, press the existing Retry or
   fullscreen icon once, then note whether it recovers. Logs establish exact task identity;
   an unchanged route alone cannot prove the same task.
7. Watch the clock through transitions and once after opening/closing the notification shade.
8. Wait for `READY-TO-UPLOAD-boot-2.txt` before the next power operation.

## Phase 4 — Boot 3: power procedure, Radio and persistence

1. Record your actual head-unit/car power-off procedure and off duration. A new kernel boot
   does not alone prove that the MCU/head unit was electrically cold. Do not disconnect vehicle
   wiring or change firmware for this test.
2. Start the unit. Test HOME Music PLAY once before opening Auxio. Record audible result.
3. Press Radio PLAY once. Alternate Music and Radio five times, watching metadata and listening
   for missing audio, overlap and delay. Preserve successful handover; never infer audio from icons.
4. If time permits, test a committed shuffle OFF → ALL → OFF change and check it after a normal
   app restart. Record the indicator/current item. This is the lowest-priority check.
5. Wait for `READY-TO-UPLOAD-boot-3.txt` and combined-part completion. The original three folders
   remain available even if combined packaging fails.

## Phase 5 — Optional same-boot or wake follow-up

Only use these if the targeted behaviour still needs evidence. Boot runs are not overwritten.
In Termux, use its existing unmodified `su`; do not change PATH, root tools or app data.

Start another unattended five-minute capture after the previous one is sealed:

    su -c 'sh /data/adb/modules/ts18_execution_preflight/collector.sh followup'

To arm one bounded power sleep/wake observation before your normal ACC procedure:

    su -c 'sh /data/adb/modules/ts18_execution_preflight/arm-wake.sh'

It watches for up to 30 minutes and must see Asleep/Dozing followed by Awake. It sends no power
commands. Some TS18 ACC implementations do not expose that sequence; `WAKE-WATCH.txt` will then
say timed out. This is a limitation, not a failed ACC system. The separate follow-up folder is
`boot-followup-...`; copy it too. Describe the physical ACC procedure in your notes.

## Phase 6 — Export and return

1. Add your observations to `MY-OBSERVATIONS.txt`: boot/run, action, approximate time and audible/
   visible result. Note any manual app launch, force-stop or recovery intervention.
2. Wait for all relevant `READY-TO-UPLOAD-...` files. Copy the entire v3.6.0 output folder to USB
   using your file manager. Do not move/delete the only copy.
3. Keep all numbered `.tar.gz.part-000` through the final numbered part and their MANIFEST.
   These are pieces of one archive, not separate ZIP files. Copy follow-up folders separately;
   they are not in the three-boot combined bundle.
4. Upload the parts/manifest and notes here, or ZIP the complete sealed folders on another computer.
   Review for private screen/log content first. Never edit a sealed evidence file in place.
5. If combined packaging reports BLOCKED, send the individual sealed boot folders and its status
   file. Do not delete valid evidence to force another archive attempt.

The collector does not qualify sound, map pixels, true cold power or ACC automatically. Those
results are assessed from original logs, footage and your observations after capture.
