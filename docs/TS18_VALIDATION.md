# TS18 physical validation

Nothing is passed until it is run on CB's exact TS18.

## Baseline / preflight

Record before mutation:

- timestamp and boot ID;
- Android release/API and Topway build;
- physical/base display size and density;
- current usable/window bounds sufficient to confirm or challenge safe-right x=1225;
- DoFun package version, path, UID, signer and granted permissions;
- current HOME resolver and confirmation that `com.dofun.variety` remains the launcher;
- active theme package/ID and selected navigation app;
- selected music target from DoFun's exported provider where accessible;
- Magisk/root state for donor-slot testing;
- existence and metadata of `/data/user/0/com.dofun.variety/app_p_a` (or equivalent) and `p.l` without modifying them.

The Termux preflight exports a bounded report to `/storage/emulated/0/Download/TS18-theme-install/`. A present-state geometry or RePlugin-layout mismatch must be treated as a warning/guard before donor mutation, not forced through.

## Stage A — theme discovery

Use the least invasive method that works:

1. Confirm a known vendor theme remains installed/selectable.
2. Download the APK, `.sha256`, metadata and installation-tools assets from the same GitHub Release and verify them.
3. Confirm the APK package is `launcher.variety.theme.plugin.sfp_cbk_black`, its plug-in ID is `sfp_cbk_black`, its version matches the Release tag and its signer matches release metadata.
4. Try ordinary Android package installation/discovery.
5. If not discovered, prepare the 4PDA local/U-disk import path and try DoFun's hidden import UI.
6. If DoFun reports the historical `damaged file`/signature-rejection behaviour and current `app_p_a`/`p.l` matches the known contract, use the guarded rooted donor-slot fallback with a genuine imported `sfp_*` donor.
7. Record the result, DoFun restart behaviour and any catalogue entry/preview mismatch.
8. Apply the theme and capture a screenshot plus a bounded log window if needed.
9. Reboot and confirm selection.

Independent signing acceptance remains unproven until direct physical discovery/import passes.

## Rooted donor-slot safety acceptance

Before any donor write, prove:

- selected donor is a regular non-symlink file contained by DoFun's `app_p_a` directory;
- donor record and path are visible in the successfully read `p.l` scope;
- a timestamped backup contains donor bytes, `p.l`, hashes, stat/SELinux metadata and DoFun version details;
- DoFun is force-stopped before the write;
- replacement writes to the existing donor file in place and verifies exact APK SHA-256 afterwards;
- failed/interrupted verification restores the original donor bytes;
- `p.l` is unchanged by the normal fallback path;
- no broad chmod/chown, SELinux change, `/system`/`/vendor` write or DoFun APK mutation occurred.

Rollback must be tested before the donor result is considered releasable.

## Stage B — layout / right SystemUI

Validate:

- one continuous 64 px strip in radio/music/date order;
- hotseat ends at x=81;
- all strip/map content ends at or before x=1225 on the exact release profile;
- the right Topway/SystemUI region remains fully visible and untouched;
- no gap, overlap or map occlusion;
- map touch, pan, zoom and guidance across the full 1144 x 583 release window;
- rightmost map interactions are not intercepted by SystemUI;
- date renders `DD MMM` with no year/time and remains fully visible;
- warm accents only; no album art, visualiser or icon tiles;
- radio/music information regions launch the correct default apps if supported;
- individual buttons do not trigger a surrounding launch action.

If the present device exposes a different safe-right boundary, record it and stop treating the 1225 profile as current. Do not compensate with random density/SystemUI changes.

## Stage C — radio

Run separately for stock `com.tw.radio` and NavRadio+:

- record current channel and saved preset;
- press previous once and record whether it selects preset, seeks or steps frequency;
- press next once and record the same;
- tap the frequency region and confirm app launch if implemented by the host;
- restart DoFun and confirm state remains current.

## Stage D — generic media

Test Auxio-TS/`com.tw.media`, one unrelated player and Bluetooth separately.

For each test record:

- selected DoFun target and active MediaSession package;
- notification-listener and RemoteMediaService state where accessible;
- rendered `Artist - Track Name`;
- short and long title behaviour;
- previous, play/pause and next with exactly one playback change;
- ticker/info-region launch target;
- behaviour when another player becomes active;
- player force-stop and session destruction;
- number of playback services, sessions and notifications before/after.

## Lifecycle matrix

| Boundary | Theme/layout | Media/title | App launch | Radio | Donor persistence |
| --- | --- | --- | --- | --- | --- |
| Immediate apply | Unrun | Unrun | Unrun | Unrun | Unrun |
| Launcher restart | Unrun | Unrun | Unrun | Unrun | Unrun |
| Player force-stop/relaunch | N/A | Unrun | Unrun | N/A | N/A |
| Android reboot | Unrun | Unrun | Unrun | Unrun | Unrun |
| Cold boot/full power removal | Unrun | Unrun | Unrun | Unrun | Unrun |
| ACC sleep/wake | Unrun | Unrun | Unrun | Unrun | Unrun |
| Internet reconnect | Unrun | Unrun | Unrun | Unrun | Unrun |

## Release rule

Static/CI success is development evidence only. Release notes must state every unrun physical row.
