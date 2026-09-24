# TS18 Termux tools

Use these helpers from Termux on the target TS18. Keep diagnostic collection read-only unless a specific installer/rollback or explicitly named qualification mutation is intended.

## Legacy DoFun theme installation

Start with read-only preflight or the interactive installer; do not jump directly to donor substitution.

```bash
bash scripts/termux/ts18-theme-preflight.sh --apk /path/to/TS18-Dashboard-Theme-vX.Y.Z.apk
bash scripts/termux/ts18-theme-install.sh --apk /path/to/TS18-Dashboard-Theme-vX.Y.Z.apk
```

The installer attempts normal PackageManager discovery, DoFun/4PDA local import preparation, then guarded rooted RePlugin donor substitution. Donor substitution backs up the donor and `p.l`, leaves `p.l` unchanged, writes only the selected numeric donor JAR, verifies hashes and automatically attempts restoration if replacement verification or an interrupt occurs.

Legacy standalone rollback helper:

```bash
bash scripts/termux/ts18-theme-rollback.sh
```

## Standalone launcher installation / rollback

Use the bounded standalone helper for ordinary install, explicit HOME setup and verified rollback. DoFun remains installed as recovery.

```bash
bash scripts/termux/install-standalone-launcher.sh --apk /path/to/TS18-Standalone-Launcher-TESTING.apk
bash scripts/termux/install-standalone-launcher.sh --rollback-home
```

## Runtime measurement

`measure-standalone-launcher.sh` captures bounded launcher CPU/RAM/frame/runtime evidence. It does not convert a green build into physical proof.

```bash
bash scripts/termux/measure-standalone-launcher.sh
```

## Fast-media evidence and qualification

The default fast-media collector is read-only and labels each capture bundle for easy comparison:

```bash
bash scripts/termux/collect-fast-media-evidence.sh --label baseline
bash scripts/termux/collect-fast-media-evidence.sh --label after-one-tap
```

It captures exact package/path/hash evidence where readable, current Android user, root identity separately from target-app identity, media sessions/actions/metadata, audio focus/route evidence, resumed task, notification-listener state, removable-storage state, relevant Topway processes/services and the launcher's bounded `TS18MediaTrace` event timeline. Outputs stay under `/storage/emulated/0/Download/ts-theme/`.

NavRadio passive-start qualification is deliberately a separate **explicitly mutating** command. It discovers the installed `com.navimods.radio` Media3 service first, then performs exactly one bounded service-start request. It does not issue Play or stop/force-stop the app afterwards:

```bash
bash scripts/termux/qualify-navradio-service-start.sh --qualify-navradio-service-start
```

Stock TW Radio evidence remains read-only. Run it cold, then manually open stock Radio, return HOME and run the opened phase. Any manual station/transport action is performed by the user during the bounded log window; the script does not call XTService/Binder/broadcast/media transport itself.

```bash
bash scripts/termux/collect-stock-radio-evidence.sh --phase cold
bash scripts/termux/collect-stock-radio-evidence.sh --phase opened --log-seconds 20
```

For reboot/ACC evidence, run the bounded lifecycle collector while physically performing the requested transition. It samples Android power/wakefulness, HOME/task, media/session/process/storage state and filtered relevant logs; it does not write MCU/CAN, properties, OEM services or playback, and it does not interpret screen-on as proof of ACC.

```bash
bash scripts/termux/collect-acc-media-lifecycle.sh --seconds 120
```

A missing root/logcat/source prerequisite is BLOCKED or UNVERIFIED for dependent evidence; it is not proof that the downstream contract failed.

## Window/media evidence collector

`collect-window-media-evidence.sh` is a **read-only** targeted evidence bundle for media bootstrap validation and the separate future DoFun/Organic Maps windowing investigation.

```bash
bash scripts/termux/collect-window-media-evidence.sh
```

For the best windowing evidence, run it while **DoFun is visibly displaying Organic Maps in its desktop/navigation window** and do not change task/window state during collection.

The collector uses bounded commands to capture:

- `wm size`, `wm density` and display state;
- freeform/PiP feature and global-setting reads;
- launcher/DoFun/Organic Maps/NavRadio+/music package state;
- task/window/SurfaceFlinger names and bounds where exposed;
- exported `MediaBrowserService` discovery and active MediaSession/actions;
- launcher preferences only when they are root-readable;
- a bounded relevant logcat tail.

It **does not** start/stop tasks, change settings, send playback/key input, alter packages or manipulate windows. Missing permissions, timed-out captures and unavailable root are recorded as blocked/unknown evidence rather than absence.

Substantial outputs are written under `/storage/emulated/0/Download/`; transient work remains private where applicable. The helpers never clear DoFun application data, set SELinux permissive, broadly change ownership/mode, write protected partitions, or modify the `com.dofun.variety` APK outside the explicitly guarded legacy donor workflow.

See `docs/INSTALL_TS18.md`, `docs/STANDALONE_LAUNCHER.md` and `docs/MEDIA_BACKGROUND_READINESS.md` for the associated installation and physical-validation procedures.
