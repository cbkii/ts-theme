# PR14 TS18 physical qualification

This gate applies to `fix/final-startup-runtime-hardening`. It does **not** claim physical qualification from CI, installation or static task identity alone.

## Preconditions

1. Use a TESTING launcher APK built from the exact PR head being qualified. Record the commit SHA and APK SHA-256 before installation.
2. Preserve the current HOME selection (`com.cbkii.ts18launcher/.HomeAlias`) and existing launcher/media/navigation configuration. Do not clear Topway, DoFun, radio, media or launcher data as part of this gate.
3. Put `scripts/termux/collect-pr14-cold-runtime-evidence.sh` in Termux. The collector is read-only and never invokes `su` itself.

## Qualification sequence

### A. Immediate install/restart

- Install the exact-head TESTING launcher using the repository's normal standalone launcher installation procedure.
- Return to HOME without opening music/radio applications manually.
- Confirm HOME remains foreground and usable.
- Confirm neither `com.tw.media/com.tw.music.MusicActivity` nor the configured radio Activity is foregrounded merely to make media ready.
- Confirm launcher geometry is stable: no visible repeated resizing/re-layout and no sustained `requestLayout()` warning storm.
- Toggle rail/radio side preferences, return from Settings, and exercise fullscreen/windowed returns. Confirm placement updates once for changed bounds/insets/preferences and remains stable on unchanged callbacks. Decor-fitted content must not acquire a second top/right inset. Newly enabled experimental map views still require placement.
- Confirm HOME media controls can observe an existing music/radio MediaSession without waiting for the notification-listener service UI to be opened.

### B. Navigation task retention

With Organic Maps already acquired as the launcher-managed task:

- Exercise HOME → navigation → HOME repeatedly.
- Open and close the app drawer once while navigation is managed.
- Exercise fullscreen navigation and return to HOME.
- A transient task-parser miss must not launch a second Organic Maps task, clear known task authority, or enter a latched `Navigation app closed · use Retry` state unless independent recents/process evidence establishes absence.
- Verify the same task ID is retained across successful HOME/windowed transitions. Treat one empty/transient dumpsys parse as inconclusive.

### C. Media source behaviour

- From HOME, use Play/Pause once for configured music and once for configured radio.
- Verify the playback app remains the only playback/MediaSession authority.
- Confirm a phone/telecom MediaSession is not displayed as the Music source.
- Change track/station and verify metadata does not revert to a previous same-package session after that source recreates its MediaSession.
- If notification-listener access is removed for a negative test, HOME must clear process-owned media authority rather than retain stale process snapshots. Restore access through the normal Android/launcher path afterwards; do not edit secure settings directly.

### D. Reboot and vehicle lifecycle

After A-C pass:

- Reboot once and repeat HOME/media/navigation checks before manually opening the music or radio UI.
- Perform one applicable ACC sleep/wake cycle and repeat the same checks.
- Do not mark these levels PASS unless the physical unit was actually exercised.

## Evidence collection

Immediately after each reproduced or successful cold-start sequence, run:

```sh
bash collect-pr14-cold-runtime-evidence.sh
```

Run that once in ordinary Termux. If root-context evidence is required, enter through the established TS18 Termux Kit `s`/`st` route and run the collector again. Do **not** nest raw `su` inside the collector.

The collector writes a sealed ZIP plus SHA-256 under:

`/storage/emulated/0/Download/ts-theme/`

Keep the ordinary-Termux and root-lane archives separate. Attach both when available; a failed collector prerequisite makes dependent conclusions BLOCKED/UNKNOWN rather than proving target failure.

## Acceptance report

Report these independently:

- exact repo head and PR;
- exact TESTING APK identity/SHA-256;
- install result;
- immediate runtime result;
- reboot result;
- cold-boot result;
- ACC sleep/wake result;
- any BLOCKED/NOT_RUN level;
- evidence archive names and SHA-256 values.
