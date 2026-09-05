# TS18 LSPosed roadmap playbook

This playbook is for CB's exact Android 10/API 29 Topway TS18 running DoFun `com.dofun.variety`. Its purpose is to collect the current-device evidence needed to decide whether `ts-theme` should add any LSPosed/Xposed support, and if so, what the smallest safe next step should be.

The first phase is **diagnostic only**. Do not change LSPosed scope, install a new hook, edit DoFun private state, modify `p.l`, replace a donor, change SELinux, or clear DoFun data while collecting this evidence.

## Phase 0 — prepare the collector

Preferred Termux packages:

```bash
pkg update
pkg install -y zip unzip sqlite
```

If Termux has not previously been granted shared-storage access:

```bash
termux-setup-storage
```

Approve the Android storage prompt if one appears. The collector needs a writable `Download` directory for its output.

Obtain the current repository copy of:

```text
scripts/magisk/ts18-theme-runtime-diagnostic.sh
```

Either extract it from a current `TS18-Dashboard-Theme-...-install-tools.zip`, copy it to the unit by USB, or download/copy the file from this repository's `main` branch after this change is merged. Keep the script in normal Termux-accessible storage; the installer copies the verified file into Magisk `service.d` itself.

## Phase 1 — install and verify

From the directory containing the script:

```bash
sh scripts/magisk/ts18-theme-runtime-diagnostic.sh --install
sh scripts/magisk/ts18-theme-runtime-diagnostic.sh --status
```

Grant the Termux root request in Magisk when prompted.

Expected status before the first diagnostic boot is similar to:

```text
service=installed
run_count=0
max_runs=2
export_root=/storage/emulated/0/Download/TS18-theme-runtime-diagnostic
```

If `run_count=1`, do not reset it: the next boot is intentionally the final automatic run. If `run_count=2`, stop and analyse the existing captures before deciding whether another run is justified.

The installed service is:

```text
/data/adb/service.d/99-ts18-theme-runtime-diag.sh
```

Persistent run/lock state is:

```text
/data/adb/ts18-theme-runtime-diag-state
```

Do not delete either merely to obtain extra attempts.

## Phase 2 — Run 1: broad activation comparison

1. Reboot Android normally.
2. Wait for DoFun to become usable. Allow roughly 30–45 seconds for the collector's pre-live inventory.
3. Open **DoFun → Theme**.
4. During the following minute, perform this sequence:
   - apply a known-working built-in/default theme; wait ~5 seconds;
   - apply a known-working imported/downloaded `sfp_*` theme; wait ~10 seconds;
   - apply another working imported theme if available; wait ~5 seconds;
   - attempt **TS18 Dashboard Theme**; wait ~15 seconds even if it immediately falls back or disappears;
   - return to the known-working `sfp_*` theme; wait ~10 seconds;
   - if time remains, attempt TS18 Dashboard Theme once more.
5. Leave the unit alone for another 1–2 minutes so after-state/static inspection and ZIP creation can finish.

The live window is 100 seconds. Expensive APK/JAR inspection is deliberately deferred until after that live window and shares a 45-second aggregate budget, so slow or numerous archives cannot consume the interaction phase.

Do not use Termux, install/uninstall apps, change LSPosed scope, change Magisk settings, clear logs, or restart DoFun during the live sequence.

## Phase 3 — confirm Run 1 completed

Open:

```text
/storage/emulated/0/Download/TS18-theme-runtime-diagnostic/
```

Look for:

```text
TS18-theme-runtime-diagnostic-run1-YYYYMMDD-HHMMSS.zip
TS18-theme-runtime-diagnostic-run1-YYYYMMDD-HHMMSS.zip.sha256.txt
```

If a ZIP is absent, retain the complete `live-run1-*` folder. Check its `LIVE.txt` and `SUMMARY.txt` for `FINAL_STATUS` and packaging warnings.

The most important decision files are:

```text
analysis/lsposed-feasibility.txt
lsposed/database.txt
lsposed/dofun-scope.tsv
lsposed/module-apk-markers.txt
lsposed/zygote-injection.txt
root/magisk-state.txt
runtime/process-paths.tsv
runtime/logcat-history-filtered.txt
runtime/logcat-filtered.txt
static/dofun-apk.txt
plugins/p.l-after-normalised.txt
plugins/plugin-files-after.tsv
plugins/archive-markers-after.txt
analysis/overlay-candidates.tsv
```

Do not edit or repackage the evidence before analysis unless ZIP creation failed.

## Phase 4 — Run 2: repeatability

Run 2 is valuable if Run 1 does not already establish the boundary clearly.

Reboot normally and perform a shorter, repeatable sequence inside the live window:

```text
working sfp_* theme
→ TS18 Dashboard Theme
→ working theme
→ working sfp_* theme
→ TS18 Dashboard Theme
```

Use approximately 10–15 seconds between the important transitions. Again, leave the unit idle afterwards until the run-2 ZIP appears.

Expected output:

```text
TS18-theme-runtime-diagnostic-run2-YYYYMMDD-HHMMSS.zip
```

After run 2, `--status` should report `run_count=2`. The service then becomes inert on later boots; it does not keep collecting indefinitely.

## Phase 5 — provide the evidence for analysis

Upload both ZIPs to the same ChatGPT project/conversation if available:

```text
TS18-theme-runtime-diagnostic-run1-....zip
TS18-theme-runtime-diagnostic-run2-....zip
```

Also provide a short manual note containing only things the logs may not establish reliably:

```text
Run 1:
- working theme used:
- TS18 theme visible before Use: yes/no
- result of Use: activated / fallback / tile disappeared / other
- approximate time of each TS18 attempt:

Run 2:
- same fields

Other:
- LSPosed Manager showed framework active: yes/no/unknown
- any LSPosed scope you knowingly changed before these captures: none / describe
- any reboot/crash/DoFun restart during capture: none / describe
```

Do not manually transcribe long logs. The archives are the primary evidence.

## Phase 6 — decision gate after the captures

The next implementation depends on what the exact-device evidence establishes.

### A. LSPosed framework or DoFun injection is not established

Do **not** build a `ts-theme` hook yet. First identify whether the blocker is Zygisk/framework state, 32/64-bit zygote coverage, DenyList/injection exclusion, LSPosed implementation/API mismatch, or simply an incomplete probe. Keep this separate from theme behaviour.

### B. LSPosed injection is established, but only legacy Xposed-module support is evidenced

Use a legacy Xposed bridge if a discovery module is required. The existing TS18 engineering programme already has an API-29 legacy-bridge precedent in `ts-sysui`; reuse only its proven module-loading/scoping pattern, not its SystemUI hooks.

The first `ts-theme` module must still be:

- scoped only to `com.dofun.variety`;
- log-only;
- fail-open;
- version/fingerprint gated;
- bounded/rate-limited;
- trivially disableable from LSPosed/Magisk recovery.

### C. Modern libxposed/API metadata and DoFun injection are established

Build a separate **discovery module**, not the final behavioural hook. Its only purpose is to identify the post-Jiagu class loader and the exact DoFun/RePlugin/theme-selection call boundary after protected classes are available in-process.

The discovery module should log candidate class loaders/classes/method entries for a short bounded window around a theme-selection attempt and then stop. It must not alter method return values, arguments, files or preferences.

### D. Jiagu/RePlugin evidence already exposes a reliable file/runtime path and Java hooking adds no value

Prefer the simpler reversible mechanism. A Magisk bind-overlay or existing donor-slot strategy may be lower risk than adding an in-process hook, provided the exact target path and lifecycle are proven by both captures.

### E. The independently signed theme now activates normally

Do not introduce LSPosed solely because it is available. Keep LSPosed work limited to later features that cannot be delivered safely through the declarative theme/plugin contract.

## Phase 7 — the planned LSPosed development ladder

If the evidence supports LSPosed, progress in this order:

1. **Discovery build:** DoFun-only, log-only class-loader/method discovery.
2. **Exact-hook proof:** one identified method/path, still observation-only, with package/version/fingerprint gates.
3. **Single reversible behaviour change:** change one theme-selection/context/asset decision only, with stock behaviour on every uncertainty or exception.
4. **Lifecycle validation:** DoFun relaunch, launcher restart, Android reboot, cold boot/full power removal and ACC sleep/wake.
5. **Regression boundary:** verify built-in themes, working imported themes, radio/media/navigation behaviour and DoFun recovery remain unchanged.
6. **Only then** decide whether the LSPosed adapter belongs in `ts-theme` permanently or whether the simpler declarative/donor/overlay route should remain authoritative.

## Recovery / stop conditions

Stop the experiment and revert to stock behaviour if any of the following occurs:

- DoFun repeatedly crashes or loses HOME/launcher functionality;
- LSPosed cannot inject the exact DoFun process/ABI reliably;
- a proposed hook requires broad package/system-server scope;
- the target method cannot be tied to the exact installed DoFun build;
- the only workable approach requires bypassing signer/security checks broadly rather than redirecting a narrow theme decision;
- recovery requires deleting DoFun data or protected system packages.

After the two evidence runs are safely copied off-device, the diagnostic service may be removed if desired:

```bash
su -c 'rm -f /data/adb/service.d/99-ts18-theme-runtime-diag.sh'
```

Preserve `/data/adb/ts18-theme-runtime-diag-state` until the captures have been analysed; it records the bounded-run state and avoids accidental repeat collection.
