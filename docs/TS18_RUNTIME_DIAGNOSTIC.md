# TS18 DoFun activation diagnostic

Use this only when DoFun can **list and preview** the TS18 Dashboard Theme but pressing **Use** fails to activate it, causes fallback to a working theme, or makes the custom tile disappear. That observed behaviour places the failure after catalogue/preview discovery and inside the local activation/RePlugin path.

The current exact-device observation was made while the unit was offline, so this capture focuses on local plug-in registration, package identity, DEX/class loading, derived RePlugin state, theme configuration loading, SELinux, process/mount namespaces and the exact files used by working themes. It does not spend either limited run testing network validation.

## Safety, privacy and limits

The diagnostic is read-only against DoFun and package state. It does not edit `p.l`, overwrite donor files, install or uninstall packages during capture, force-stop DoFun, clear data, mount overlays or change SELinux. Magisk `service.d` runs the worker as root, so runtime probes do not invoke `su`; `su` is used only by the one-time Termux installer/status modes.

Persistent state under `/data/adb/ts18-theme-runtime-diag-state` enforces **at most two runs**. Each active worker has a **170-second hard watchdog**, so total possible diagnostic-worker time is at most **340 seconds (5m40s)**. Reinstalling the script does not reset the counter. The service launcher can wait up to **90 seconds** for `/storage/emulated/0/Download` to become writable before it starts a worker; if shared storage never becomes ready, no run is consumed.

Each discovered `p.l` registry is captured up to **256 KiB**, with an explicit truncation marker when the source is larger. Unfiltered logcat is written only to private `/data/adb/ts18-theme-runtime-diag-state` while the worker is active, then filtered into the exported evidence and deleted. The exported folder/ZIP intentionally contains targeted DoFun package/process paths, plug-in registration metadata, hashes and filtered activation logs needed for RePlugin diagnosis. Nothing is uploaded automatically; review `SHARING_NOTICE.txt` before manually sharing an archive.

## One-time installation

From Termux, with the release install-tools bundle extracted:

```bash
pkg install -y zip
sh scripts/magisk/ts18-theme-runtime-diagnostic.sh --install
sh scripts/magisk/ts18-theme-runtime-diagnostic.sh --status
```

Before the first reboot, expect `service=installed` and `run_count=0`.

The live and final shareable output is written directly to:

```text
/storage/emulated/0/Download/TS18-theme-runtime-diagnostic/
```

## Run 1 — working themes versus failed activation

Reboot normally. The service may wait briefly for shared storage. As soon as DoFun is usable, open **Theme**. During the 90-second observation window:

1. apply any known-working built-in/default theme and wait about 5 seconds;
2. apply the known-working **SFP_TW / TS10-family** downloaded/imported theme and wait about 10 seconds;
3. apply another working downloaded/imported theme if available and wait about 5 seconds;
4. attempt **TS18 Dashboard Theme**, wait about 15 seconds even if it immediately falls back or disappears from the list;
5. return to the known-working SFP_TW/TS10-family theme and wait about 10 seconds;
6. if time remains, attempt TS18 Dashboard Theme once more.

Do not use Termux during the capture. Progress is appended to `live-run1-*/LIVE.txt`.

At completion, review `SHARING_NOTICE.txt`, then upload if appropriate:

```text
TS18-theme-runtime-diagnostic-run1-YYYYMMDD-HHMMSS.zip
```

If run 1 is decisive, analyse it before consuming run 2.

## Run 2 — repeatability and cache/path evidence

On the second reboot, repeat a shorter comparison: working SFP_TW/TS10-family theme → custom theme → another working theme → working donor → custom theme. This is the final automatic run.

## What is captured

The ZIP contains bounded, timestamped evidence for all discoverable DoFun user/data roots and every `p.l` registry; plug-in-like `app_p_*` JAR/APK/DEX/ODEX/VDEX/SO paths; hashes and metadata; relevant DoFun/custom package state with `dumpsys` first and bounded `pm`/`cmd package` fallbacks; built-in DoFun theme/plugin archive names; theme/plugin preference references before and after activation; all observed `com.dofun.variety*` processes; relevant `/proc/<pid>/maps`, FDs and mountinfo; filtered logcat and kernel/SELinux evidence; and before/after metadata diffs.

`analysis/overlay-candidates.tsv` records paths that were actually mapped/opened or registered in `p.l`. It is evidence for a later Magisk bind-overlay design, **not** an instruction to mount every listed path. Absence from sampled MAP/FD output is not proof a short-lived file open did not occur; correlate both runs with `p.l`, filtered logcat and file-state changes.
