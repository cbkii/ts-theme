# TS18 physical validation

Nothing is passed until it is run on CB's exact TS18.

## Baseline

- timestamp, boot ID and Android build fingerprint;
- DoFun package version, path, UID, signer and granted permissions;
- active theme package/ID and selected navigation app;
- selected music target from DoFun's exported provider;
- active sessions, notification listeners and media-source service state;
- Magisk/LSPosed state when an adapter is under test.

## Stage A — theme discovery

1. Confirm FYD remains installed/selectable.
2. Install the separately signed `cbk_black` probe.
3. Restart only `com.dofun.variety`.
4. Apply the theme and capture a screenshot plus a bounded log window.
5. Reboot and confirm selection.
6. Uninstall the probe and confirm the previous themes still work.

Rollback: uninstall only `launcher.variety.theme.plugin.cbk_black`.

## Stage B — layout

Validate:

- one continuous 64 px strip in radio/music/date order;
- no gap, overlap or map occlusion;
- map touch, pan, zoom and guidance across the full 1154 x 583 window;
- date renders `DD MMM` with no year/time;
- warm accents only; no album art, visualiser or icon tiles;
- radio/music information regions launch the correct default apps;
- individual buttons do not trigger the surrounding launch action.

## Stage C — radio

Run separately for stock `com.tw.radio` and NavRadio+:

- record current channel and saved preset;
- press previous once and record whether it selects preset, seeks or steps frequency;
- press next once and record the same;
- tap the frequency region and confirm app launch;
- restart DoFun and confirm state remains current.

## Stage D — generic media

Test Auxio-TS/`com.tw.media`, one unrelated player and Bluetooth separately.

For each test record:

- selected DoFun target and active MediaSession package;
- notification-listener and RemoteMediaService state;
- rendered `Artist - Track Name`;
- short title (no scroll) and long title (smooth ticker);
- previous, play/pause and next with exactly one playback change;
- ticker/info-region launch target;
- behaviour when another player becomes active;
- player force-stop and session destruction;
- number of playback services, sessions and notifications before/after.

## Stage E — integration lanes

Test native-only, Android-session, Topway hybrid and any DoFun view adapter separately. For a broad
combined mode, prove:

- duplicate state inputs are correlated;
- one button press does not arrive twice;
- a rejected primary route has bounded failover;
- a late acknowledgement cannot produce a duplicate action;
- loss of notification access or Binder service fails open;
- kill switch restores native DoFun behaviour.

## Lifecycle matrix

| Boundary | Theme/layout | Media/ticker | App launch | Radio |
| --- | --- | --- | --- | --- |
| Immediate apply | Unrun | Unrun | Unrun | Unrun |
| Launcher restart | Unrun | Unrun | Unrun | Unrun |
| Player force-stop/relaunch | N/A | Unrun | Unrun | N/A |
| Android reboot | Unrun | Unrun | Unrun | Unrun |
| Cold boot/full power removal | Unrun | Unrun | Unrun | Unrun |
| ACC sleep/wake | Unrun | Unrun | Unrun | Unrun |

## Release rule

Static/CI success is development evidence only. Release notes must state every unrun physical row.
