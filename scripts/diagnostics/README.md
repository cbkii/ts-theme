# TS18 diagnostics 1.3

One bounded collector consolidates startup 1.2 and deepdiag v3. It reads Android
state; it never launches apps, sends transport, clears logcat, changes AppOps,
settings, modules, SELinux or protected files. Installation is a separate explicit
operation. No physical TS18 run is claimed by the host/CI tests.

## Files and execution context

Keep `ts18-startup-1.3.sh` and `capture-lib.sh` together. Requires the existing
Magisk BusyBox (ash, setsid, timeout, head, mkfifo, tar, gzip, sha256sum) and native
Android commands. No downloads/installers run from the collector. Native PATH is
set and Termux loader variables removed. Missing Android services produce partial
WARN/BLOCKED evidence; root mismatch retains an ordinary-context private capture
where the caller can write its TMPDIR. Do not mistake that for a UID0 capture.
Namespace IDs, SELinux, UID/GID, boot ID and epoch/uptime are recorded. A different
namespace is evidence, not permission to force global mounts. Isolated process
group failure blocks capture because safe descendant termination cannot be proved.

Use the CB kit's existing `s` / `st` entry semantics appropriate to the current
shell. Raw `$PREFIX/bin/su` remains a separate escape; do not redefine it. For an
explicit Android-root invocation from ordinary Termux after installation:

```sh
"$PREFIX/bin/su" -c '/system/bin/sh /data/adb/ts18-diagnostics-toolkit/ts18-startup-1.3.sh --start performance 180'
"$PREFIX/bin/su" -c '/system/bin/sh /data/adb/ts18-diagnostics-toolkit/ts18-startup-1.3.sh --status'
```

No internal `su` is added to this or PR14's collector. Normal root is not
necessarily `su -M`, shell-domain UID0 is not adb UID2000, and neither is an app.

## Explicit persistent installation and rollback

First compare the downloaded ZIP hash and inspect `SHA256SUMS`, then unpack the
bundle into a private directory. Verify with `sha256sum -c SHA256SUMS`.
Stop each older collector via **its own** `--stop`, await its seal, and record its
exact service.d filename. Do not delete it or its evidence. The installer refuses
active/stale locks; inspect old status and PID/start identity before manually
moving an abandoned lock aside. Never remove a lock while a writer is alive.

From a reviewed native UID0 shell, invoke (replace the exact old filename):

```sh
/system/bin/sh /PRIVATE/REVIEWED/BUNDLE/install.sh --install /data/adb/service.d/EXACT-ts18-startup-logs.sh
```

Omit the old-path argument only if no older boot entry exists. Installer paths
are allowlisted; files are root-owned mode0700, state uses umask077. It backs up
selected entry points under `/data/adb/ts18-diagnostics-backup-TIMESTAMP/`, installs
one boot entry `76-ts18-startup-1.3.sh`, and does **not** reboot or start capture.
Keep that backup path. If installation stops partway, preserve both directories,
inspect them and restore moved entries before trying again; no automatic deletion.

Rollback: request new collector `--stop`, wait for COMPLETE/SEALED (or preserve an
explicit partial run), move **only** `76-ts18-startup-1.3.sh` into that backup,
then restore the recorded old service entry from the backup with its original
ownership/mode. Do not run both versions. Leave capture directories untouched.
A reboot/module experiment needs separate device-change approval.

## Capture modes, limits and completion

`--start forensic 180` (also boot default) includes periodic bounded audio/session
snapshots. It is observer-heavy. `--start performance 180` captures only bounded
logcat and proc samples during the measured phase; PM, APK hashes and all dumpsys
are outside it. Duration accepts 30–300 seconds. Every producer is isolated,
output is capped (512KiB per snapshot, 16MiB live log), timeout is normally6s,
APK producer10s. Capture budget600s, watchdog hard deadline700s including sealing
and export. End-of-budget producers are explicitly BLOCKED. A 300s run on a slow
unit may therefore have incomplete provenance: take a separate forensic run.
Allow at least256MiB free private storage before capture. Captures are finite,
but retained previous runs are not automatically deleted.

Each command directory records argv, context reference, elapsed seconds, byte
limit, result, actual producer rc or UNKNOWN, and a producer completion sentinel.
Stream timeout at the selected interval is expected but still reported; a bounded
log is never described as complete. The worker's COMPLETE.txt and SEALED.txt are
independent of an outer Termux/su timeout rc. A valid seal means all captured bytes
were sealed, **not** that every command or physical requirement passed. Forced
termination produces FORCED_STOP/UNSEALED and cannot yield a valid integrity pass.

`--mark IDLE`, `--mark AUXIO_PLAY`, `--mark AUXIO_PAUSE`, `--mark NAVRADIO_PLAY`,
`--mark NAVRADIO_PAUSE` write only short timestamped labels. Perform playback
manually, use comparable30s intervals, repeat with quiet external load. Never put
account/location/file details in labels. `--stop` stops sampling; an in-flight
bounded stream may take the remainder of its requested duration before sealing.
No PID-name kill, no `logcat -c`, no automatic stale-lock takeover.

Finished tar.gz exports to `/storage/emulated/0/Download/TS18-startup-logs/` after
gzip CRC and copy-hash verification. Private source/archives always remain under
`/data/adb/ts18-startup-logs-1.3/`; export failure is recorded in `export-status`.
Check `--status`, `export-status`, and the SHA256 sidecar. Share selectively:
package/log/device/process evidence can contain identifiers. Module configs and
suspect script lines are hashed/redacted, not copied verbatim.

## Offline analysis

```sh
python3 analyse.py RUN.tar.gz --out analysis.json
```

The analyser never extracts archives. It rejects traversal, links, devices,
sparse entries, duplicates and excessive expanded bytes/files. Per-file manifest
coverage/hash and completion/seal are mandatory for an integrity PASS. Failed,
empty or truncated PM queries cannot establish absence. The successful ready map
retains all packages sharing10148/10186. A remount command target is never inferred
to be its caller. Historical `Cmd send ... uid` starts and explicit START are
counted once; lower-case `end` / END are excluded; unknown formats stay separate.

RSS/VmRSS and raw process CPU ticks are samples; PSS comes only from successful
post-phase meminfo producers. Global meminfo/swaps/zram/PSI are separate evidence.
All GMS/GSF colon processes are included. Sample command duration plus observer
worker ticks expose collection overhead; do not subtract it blindly. Audio rates
are observed lower bounds, correlated using epoch/uptime and manual markers;
chatty suppression, truncation, clock changes and marker latency limit comparison.

## Read-only attribution handoff

1. Storage: require successful ready UID map, APK/splits, AppOps before/after,
   remount starts, module/script hashes, framework/services hashes. Compare exact
   framework bytes offline to recover the owning call chain; storage target UID
   does not identify policy writer. If still ambiguous, propose instrumentation
   **only after** exact method and hashes are known: allowlist those hashes,
   log at most1 caller stack/s for60s, preserve args/return/exceptions, fail open,
   scoped kill switch, boot-safe disable/recovery. No deployable hook with guessed
   signatures is provided. Installing/enabling it requires separate approval.
2. GMS: correlate all process CPU/RSS/PSS, APK versions/splits, dexopt, oat/vdex
   metadata/runtime maps, classloader evidence where exposed, service timing and
   module enable/disable markers. A module directory is not proof it executed.
   Candidate A/B proposal: if baseline associates one enabled module with repeated
   GMS initialisation, disable **only that identified module**, preserve all other
   state and run equivalent cold boot intervals; then restore and repeat. Require
   exact baseline, backup, known recovery HOME and separate approval first.
   Falsifier: comparable load persists with module inactive, or fails to recur on
   restoration. No ART wipe, global dexopt, GMS reset or exploratory APK replacement.
3. Audio: compare marked idle/Auxio play/pause/NavRadio play/pause against periodic
   forensic AudioFlinger/policy/session samples and audible observations. EPERM
   alone with working sound is not a playback failure. No automatic transport.
4. Residual app CPU: after external noise attribution, compare exact installed
   OM foreground, route-follow, embedded, fullscreen and hidden states. Only a
   persistent attributable residual warrants a short exact-PID Simpleperf/Perfetto
   profile with matching symbols (separate supported tools/access needed). Map hot
   stacks to rendering/routing/GNSS/storage/lifecycle before editing. Apply the same
   rule to Auxio. No guessed quality/resolution/governor workaround.

See `QUALIFICATION.md` for candidate identities, requirements and physical gates.
