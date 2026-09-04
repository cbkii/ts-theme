# TS18 DoFun activation and LSPosed feasibility diagnostic

Use this when DoFun can **list and preview** TS18 Dashboard Theme but **Use** fails, falls back, or removes the custom tile. This pass keeps the existing RePlugin activation investigation and adds the evidence needed to decide whether a narrow LSPosed/libxposed adapter is practical on CB's exact TS18.

The collector is deliberately diagnostic only. It does not install an Xposed module, change LSPosed scope, bypass signature checks, edit `p.l`, replace a donor, force-stop DoFun, clear app data, mount overlays or change SELinux.

## Questions this pass is designed to answer

It collects enough current-device evidence to distinguish these separate questions:

1. **Root/injection platform:** exact Magisk version, Zygisk state, zygote model and 32/64-bit ABI support.
2. **Installed LSPosed implementation:** module identity/version, framework paths, `lspd`/zygote processes, optional framework CLI status and bounded framework logs.
3. **Modern API capability evidence:** modern-module metadata (`META-INF/xposed/java_init.list`, `module.prop`, `scope.list`) versus legacy `assets/xposed_init`, including any explicit `minApiVersion`/`targetApiVersion` values present on-device. The script records exact version/API tokens; it does not guess API 100 support from a product name alone.
4. **Scope:** whether any enabled LSPosed module is actually scoped to `com.dofun.variety`, read directly from the LSPosed configuration database when a read-only SQLite CLI is available.
5. **Target-process injection:** whether DoFun and its zygote/process maps, file descriptors, mount namespace and logs contain LSPosed/Zygisk/Xposed injection evidence.
6. **DoFun runtime architecture:** current package state, process parent/executable and ABI evidence, important because an ARM64 Android system may still run DoFun in a 32-bit zygote.
7. **Jiagu boundary:** static host-APK markers for 360 Jiagu/`StubApp`, class-loader markers and the resulting limit on what shell-only inspection can establish before decrypted classes are loaded.
8. **RePlugin boundary:** current `p.l`, `app_p_*` files, hashes, archive/class markers, runtime class-loader paths and before/after activation state.
9. **Next discriminator:** whether the remaining unknown is small enough to justify a separate **log-only, fail-open, DoFun-scoped discovery module** for post-Jiagu class/method discovery. The shell collector itself does not hook anything.

## Safety, privacy and limits

Persistent coordination state remains under:

```text
/data/adb/ts18-theme-runtime-diag-state
```

Exported evidence remains under:

```text
/storage/emulated/0/Download/TS18-theme-runtime-diagnostic/
```

The worker remains capped at **two boots/runs**. Each worker has a **210-second hard watchdog** and a **100-second live interaction window**, so the maximum diagnostic-worker time is 420 seconds (7 minutes) if both runs are consumed. The service can wait up to 90 seconds for shared storage before starting; a storage-wait failure does not consume a run.

The collector keeps potentially large or sensitive sources bounded:

- `p.l`: discover at most 512 registries; capture at most 64, up to 256 KiB each, with explicit coverage/omission files;
- live logcat: filtered while collected and capped at 14,000 lines; no unrestricted all-buffer log is persisted;
- logcat history: at most the latest 8,000 source lines are considered, then only matching lines are exported;
- LSPosed logs: at most 8 log files and 8,000 filtered exported lines total;
- LSPosed database: only metadata/schema, module rows and **DoFun scope rows** are queried; the raw DB and per-module configuration values are not exported;
- installed Xposed module APKs and DoFun/RePlugin archives: only hashes, bounded member lists and targeted metadata/class markers are exported; APK/JAR bytes are not copied into the result.

Review `SHARING_NOTICE.txt` before sharing a ZIP.

## One-time installation / update

For the strongest pass, install the optional read-only inspection tools as well as the required ZIP tool:

```bash
pkg install -y zip unzip sqlite
sh scripts/magisk/ts18-theme-runtime-diagnostic.sh --install
sh scripts/magisk/ts18-theme-runtime-diagnostic.sh --status
```

`zip` (or Termux Python) is required for final packaging. `unzip` and `sqlite3` are optional: if absent, the diagnostic still runs but records the specific static/module/scope evidence that could not be collected.

Installing this newer script preserves the existing run counter. If run 1 has already been consumed, the next reboot remains run 2 rather than silently resetting diagnostic state.

## Run 1 / run 2 interaction sequence

Reboot normally. As soon as DoFun is usable, open **Theme**. During the live window:

1. apply a known-working built-in/default theme; wait about 5 seconds;
2. apply a known-working imported/downloaded `sfp_*` theme if available; wait about 10 seconds;
3. apply another working imported theme if available; wait about 5 seconds;
4. attempt **TS18 Dashboard Theme**; wait about 15 seconds even if it falls back or disappears;
5. return to the known-working `sfp_*` theme; wait about 10 seconds;
6. if time remains, attempt TS18 Dashboard Theme once more.

Do not use Termux during the live window. The second run should favour repeatability: working `sfp_*` theme → custom theme → working theme → custom theme.

## Output most useful for the LSPosed decision

In addition to the existing plugin/activation files, inspect:

```text
root/magisk-state.txt
root/zygisk-native-files.tsv
lsposed/framework.txt
lsposed/framework-files.tsv
lsposed/database.txt
lsposed/modules.tsv
lsposed/dofun-scope.tsv
lsposed/module-apk-markers.txt
lsposed/logs-filtered.txt
lsposed/zygote-injection.txt
static/dofun-apk.txt
plugins/archive-markers-before.txt
plugins/archive-markers-after.txt
runtime/logcat-history-filtered.txt
runtime/logcat-filtered.txt
runtime/process-paths.tsv
analysis/lsposed-feasibility.txt
analysis/overlay-candidates.tsv
```

`analysis/lsposed-feasibility.txt` is a compact evidence index, not an automatic verdict. In particular, absence of a DB scope row is not treated as proof of no scope if the read-only SQL query failed; query exit status and bounded stderr are recorded in `lsposed/database.txt`.

## What this still cannot prove

A root shell cannot reliably enumerate the Java implementation that Jiagu decrypts only inside the running DoFun process, nor can it prove the stable method signature of a future hook. If this pass establishes a compatible LSPosed/libxposed framework, correct ABI injection into DoFun, and a safe package-scoping path, the next step is a separate diagnostic module that:

- scopes only to `com.dofun.variety`;
- starts log-only and changes no return values;
- discovers the post-Jiagu class-loader/theme/RePlugin call boundary;
- is version/fingerprint gated, bounded and fail-open;
- has an obvious disable/recovery path.

Only after that evidence should `ts-theme` choose between a theme-selection/context redirect, a narrower asset-source redirect, or retaining the existing declarative/donor routes without LSPosed.
