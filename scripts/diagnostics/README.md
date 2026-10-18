# TS18 diagnostics 1.3

This directory contains the bounded TS18 diagnostics collector, offline analyser,
transactional installer, deterministic bundle builder and physical qualification
ledger. Collection is read-only: it does not launch apps, send media transport,
clear logcat, change AppOps/settings/modules/SELinux, or write protected Android
state. Installation is a separate explicit operation.

## Execution context

Keep `ts18-startup-1.3.sh` and `capture-lib.sh` together. The collector requires
the existing Magisk BusyBox plus native Android commands. Native PATH is fixed and
Termux loader variables are removed. UID, GID, SELinux context, mount namespace,
boot ID and epoch/uptime are captured. Root, shell and app contexts remain distinct;
a UID0 result is not treated as platform/app authority.

A UID0 capture stores private state under `/data/adb/ts18-startup-logs-1.3/`.
An ordinary-context capture instead uses the caller's `${TMPDIR}` or
`/data/local/tmp`, suffixed with the caller UID. Do not mistake an ordinary-context
capture for root evidence.

## Bundle and installer

`build-bundle.py` creates a deterministic ZIP with fixed metadata and stored entries.
It refuses an output path that would replace a bundle input. Verify the bundle hash
and its internal `SHA256SUMS` before use.

The installer is explicit and does not reboot or start capture. It stages and
hashes the replacement before changing service entries. The new toolkit and boot
entry are committed before explicitly selected older entries are moved to a backup.
Failures and signals after rollback traps are installed run rollback. Earlier staging failures can leave STAGING artefacts requiring inspection before retry; `TRANSACTION.txt` records whether the
operation is STAGING, NEW_ENTRY_INSTALLED, PASS, ROLLED_BACK or RECOVERY_REQUIRED.
A hard power loss or SIGKILL cannot run a shell trap, so an interrupted install must
be inspected before reboot or retry. Existing diagnostic evidence is never deleted.

Rollback after a successful installation must stop the new collector, preserve any
partial or sealed evidence, remove only its recorded boot entry, and restore the
recorded prior service entry from the backup. Do not run old and new boot collectors
together.

## Capture modes and limits

`--start forensic 180` includes bounded periodic audio/session snapshots and is
observer-heavy. `--start performance 180` keeps Binder/PM/dumpsys work outside the
measured phase. Duration accepts 30–300 seconds. Normal producer timeout is 6 s;
APK timeout is 10 s; individual snapshots are capped at 512 KiB and live logcat at
16 MiB. The capture budget is 600 s and watchdog deadline 700 s. Allow at least
256 MiB free private storage before capture.

The bounded reader is outside each isolated producer group. On timeout, the owned
producer group is terminated first and EOF then allows already-written bytes to be
flushed. If producer-group or reader cleanup cannot be proved, sealing is blocked.
The watchdog also cleans registered producers/readers after abnormal worker death.
A valid seal proves only that retained bytes were closed and hashed; it does not
mean every command or physical requirement passed.

`--stop` is retained even during the initial owner handshake, so an immediate stop
request is not lost. Historical lock files use recorded process start identity when
available, avoiding false blocks from PID reuse.

`--mark` accepts only `IDLE`, `AUXIO_PLAY`, `AUXIO_PAUSE`, `NAVRADIO_PLAY` and
`NAVRADIO_PAUSE`. Use comparable 30 s intervals. Repeating a label is supported and
is represented as a separate analyser occurrence (`AUXIO_PLAY#1`, `AUXIO_PLAY#2`,
etc.). Do not put account, location or file details in markers.

Periodic process samples include launcher, Organic Maps, Auxio, NavRadio+, DoFun,
stock Topway radio/music/core and all matching GMS/GSF processes when present.

Finished archives export to `/storage/emulated/0/Download/TS18-startup-logs/` only
after archive validation and copy-hash comparison. Rename and SHA-256 sidecar
creation are also part of export success. If export finalisation fails, the private
archive is retained and the status is WARN rather than PASS.

## Offline analysis

`analyse.py` never extracts archives. It rejects traversal, duplicate members,
links, devices, sockets/FIFOs, sparse entries and size/file-budget violations.
Integrity PASS requires complete manifest coverage, matching hashes,
`producer=COMPLETE`, a PASS seal, and absence of forced/unsealed markers. Failed or
empty package-manager evidence remains UNKNOWN rather than establishing absence.
Shared UID mappings retain every package seen in the successful live map.

Action markers are ordered by numeric uptime. Repeated labels retain occurrence
identity, preventing separate trials from overwriting audio/chatty counters or
rates. Remount START-like records are counted once; END-like records are excluded;
unrecognised formats remain unclassified. A target UID is never treated as proof
of the initiating caller.

## Qualification use

Use one collector at a time. Mark manual IDLE/Auxio/NavRadio phases during the
relevant physical qualification stage, and use performance mode only for comparable
CPU/latency trials. Use forensic mode for broader state attribution. Treat observer
load, log suppression and truncation as measurement caveats. Exact-device behaviour
is PASS only when physically observed on the candidate bytes listed in
`QUALIFICATION.md`.

D2 storage attribution, D3 GMS investigation and D4 audio/app profiling remain
read-only evidence tasks until a fresh current-build capture identifies a causal
owner. Any later system mutation or private instrumentation requires its own
matched-build evidence, rollback plan and explicit approval.
