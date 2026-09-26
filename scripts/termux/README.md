# TS18 Termux tools

Use these helpers from Termux on the target TS18. Keep diagnostic collection read-only unless a specific installer/rollback action is intended.

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

## Fast-media qualification

Start with the read-only baseline collector:

```bash
bash scripts/termux/collect-fast-media-evidence.sh
```

It captures the current Android user/root context, source package/service/process state, MediaSessions, audio focus/route evidence, resumed task, notification listener, removable storage and the launcher's bounded `TS18Media` monotonic trace.

NavRadio service-start qualification is deliberately a separate, explicit mutation. Run it only when you intend to test whether the installed Android-10-compatible NavRadio service can be started without foreground UI or disruptive source/routing changes:

```bash
bash scripts/termux/qualify-navradio-service-start.sh --qualify-navradio-service-start
```

The script performs one discovered service start. It does not issue Play, stop/force-stop NavRadio or change the route. Physical observation is still required before enabling passive NavRadio warm-up.

Compare stock TW Radio before and after a normal manual app open without inventing an OEM control route:

```bash
bash scripts/termux/collect-stock-radio-compare.sh --session radio1 --phase cold
# Manually open stock Radio, verify normal operation, then return HOME.
bash scripts/termux/collect-stock-radio-compare.sh --session radio1 --phase opened
```

For ACC/reboot lifecycle evidence, run the bounded read-only collector while physically performing the transition:

```bash
bash scripts/termux/collect-media-lifecycle-evidence.sh 120
```

The collector correlates uptime/power, tasks, processes, MediaSessions, storage and filtered vendor events. Display/screen-on alone is not classified as ACC.

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
