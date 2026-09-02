#!/system/bin/sh
# shellcheck shell=sh disable=SC2016,SC2046
# TS18 / DoFun activation diagnostic for Magisk service.d.
# Read-only against DoFun/package state. Version 3.0.0.

SCRIPT_VERSION="3.0.0"
SERVICE_PATH="/data/adb/service.d/99-ts18-theme-runtime-diag.sh"
STATE_DIR="/data/adb/ts18-theme-runtime-diag-state"
EXPORT_ROOT="/storage/emulated/0/Download/TS18-theme-runtime-diagnostic"
DOFUN_PACKAGE="com.dofun.variety"
CUSTOM_PACKAGE="launcher.variety.theme.plugin.sfp_cbk_black"
MAX_RUNS=2
HARD_SECONDS=170
RUNTIME_SECONDS=90
STORAGE_WAIT_SECONDS=25
MAX_TREE_LINES=30000
MAX_LOG_LINES=12000
MAX_RUNTIME_LINES=16000

RUN_NO=0
RUN_DIR=""
LIVE=""
LOGCAT_PID=""
SAMPLER_PID=""
WATCHDOG_PID=""
PACKAGER_KIND=""
PACKAGER_PATH=""
WARNINGS=0

now() { date -Iseconds 2>/dev/null || date '+%Y-%m-%dT%H:%M:%S%z' 2>/dev/null || date; }
log() { line="$(now) $*"; [ -n "$LIVE" ] && printf '%s\n' "$line" >>"$LIVE" 2>/dev/null; printf '%s\n' "$line" 2>/dev/null; }
warn() { WARNINGS=$((WARNINGS + 1)); log "WARNING: $*"; }
have() { command -v "$1" >/dev/null 2>&1; }
boot_id() { cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r\n'; }

run_timeout() {
  rt_s="$1"; shift
  if [ -x /system/bin/timeout ]; then /system/bin/timeout "$rt_s" "$@"; return $?; fi
  if [ -x /system/bin/toybox ] && /system/bin/toybox timeout 1 /system/bin/true >/dev/null 2>&1; then
    /system/bin/toybox timeout "$rt_s" "$@"; return $?
  fi
  "$@" & rt_pid=$!; rt_start="$(date +%s 2>/dev/null || echo 0)"
  while kill -0 "$rt_pid" 2>/dev/null; do
    rt_now="$(date +%s 2>/dev/null || echo 0)"
    if [ $((rt_now - rt_start)) -ge "$rt_s" ] 2>/dev/null; then
      kill -TERM "$rt_pid" 2>/dev/null; sleep 1; kill -KILL "$rt_pid" 2>/dev/null; wait "$rt_pid" 2>/dev/null; return 124
    fi
    sleep 1
  done
  wait "$rt_pid" 2>/dev/null
}

capture() {
  cp_label="$1"; cp_s="$2"; cp_file="$3"; cp_cmd="$4"
  {
    printf '# label=%s\n# captured_at=%s\n# identity=%s\n# timeout_seconds=%s\n# command=%s\n# --- output ---\n' \
      "$cp_label" "$(now)" "$(id 2>/dev/null | tr '\n' ' ')" "$cp_s" "$cp_cmd"
  } >"$cp_file" 2>/dev/null || return 1
  cp_tmp="${cp_file}.tmp.$$"; rm -f "$cp_tmp" 2>/dev/null
  run_timeout "$cp_s" /system/bin/sh -c "$cp_cmd" >"$cp_tmp" 2>&1; cp_rc=$?
  [ -f "$cp_tmp" ] && cat "$cp_tmp" >>"$cp_file" 2>/dev/null
  rm -f "$cp_tmp" 2>/dev/null
  printf '# --- status ---\n# exit_status=%s\n' "$cp_rc" >>"$cp_file" 2>/dev/null
  return "$cp_rc"
}

safe_install_path() {
  case "$1" in /*) ;; *) return 1 ;; esac
  case "$1" in *[!A-Za-z0-9_./+@%=-]*) return 1 ;; esac
  return 0
}

find_termux_tool() {
  for p in "/data/data/com.termux/files/usr/bin/$1" "/data/user/0/com.termux/files/usr/bin/$1" "$(command -v "$1" 2>/dev/null)"; do
    [ -n "$p" ] && [ -x "$p" ] && { printf '%s\n' "$p"; return 0; }
  done
  return 1
}

install_self() {
  self="$(readlink -f "$0" 2>/dev/null)"; [ -n "$self" ] || self="$0"
  [ -r "$self" ] || { printf 'FAILED: cannot read %s\n' "$self" >&2; return 1; }
  have su || { printf 'FAILED: Magisk su is unavailable.\n' >&2; return 1; }
  safe_install_path "$self" || { printf 'FAILED: unsupported script path: %s\n' "$self" >&2; return 1; }
  uid="$(run_timeout 10 su -c 'id -u' 2>/dev/null | head -n 1)"
  [ "$uid" = 0 ] || { printf 'FAILED: su did not provide UID 0.\n' >&2; return 1; }
  zp="$(find_termux_tool zip 2>/dev/null)"; py="$(find_termux_tool python 2>/dev/null)"
  if [ -n "$zp" ]; then kind=zip; pack="$zp"; elif [ -n "$py" ]; then kind=python; pack="$py"; else
    printf 'FAILED: install Termux zip first: pkg install -y zip\n' >&2; return 1
  fi
  safe_install_path "$pack" || { printf 'FAILED: unsupported packager path: %s\n' "$pack" >&2; return 1; }
  run_timeout 15 su -c "mkdir -p '$STATE_DIR' /data/adb/service.d && cp '$self' '$SERVICE_PATH' && chmod 0755 '$SERVICE_PATH'" >/dev/null 2>&1 || {
    printf 'FAILED: could not install %s\n' "$SERVICE_PATH" >&2; return 1
  }
  run_timeout 10 su -c "printf '%s\n' 'PACKAGER_KIND=$kind' 'PACKAGER_PATH=$pack' > '$STATE_DIR/packager.conf' && chmod 0600 '$STATE_DIR/packager.conf'" >/dev/null 2>&1 || {
    printf 'FAILED: could not save packager configuration.\n' >&2; return 1
  }
  count="$(run_timeout 8 su -c "cat '$STATE_DIR/run-count' 2>/dev/null" 2>/dev/null | tr -d '\r\n')"; case "$count" in ''|*[!0-9]*) count=0;; esac
  printf 'SUCCESS\nservice=%s\nrun_count=%s\nmax_runs=%s\n' "$SERVICE_PATH" "$count" "$MAX_RUNS"
}

show_status() {
  if ! have su; then printf 'service=unknown\nrun_count=unknown\nexport_root=%s\n' "$EXPORT_ROOT"; return 0; fi
  run_timeout 10 su -c "if [ -x '$SERVICE_PATH' ]; then echo service=installed; else echo service=missing; fi; c=\$(cat '$STATE_DIR/run-count' 2>/dev/null); [ -n \"\$c\" ] || c=0; echo run_count=\$c; echo max_runs=$MAX_RUNS" 2>/dev/null
  printf 'export_root=%s\n' "$EXPORT_ROOT"
}

find_roots() {
  for p in /data/user/[0-9]*/com.dofun.variety /data/user_de/[0-9]*/com.dofun.variety /data/data/com.dofun.variety; do
    [ -d "$p" ] || continue; readlink -f "$p" 2>/dev/null || printf '%s\n' "$p"
  done | awk '!seen[$0]++'
}

sha_file() {
  if have sha256sum; then sha256sum "$1" 2>/dev/null | awk '{print $1}';
  elif [ -x /system/bin/toybox ]; then /system/bin/toybox sha256sum "$1" 2>/dev/null | awk '{print $1}'; else printf 'unavailable'; fi
}

snapshot_tree() {
  out="$1"; : >"$out"
  for root in $(find_roots); do
    find "$root" -xdev -maxdepth 6 \( -type f -o -type d \) -print 2>/dev/null
  done | sort -u | head -n "$MAX_TREE_LINES" | while IFS= read -r p; do
    [ -e "$p" ] || continue
    stat -c '%n\t%F\t%s\t%Y\t%i\t%a\t%u\t%g' "$p" 2>/dev/null || printf '%s\tstat_failed\n' "$p"
  done >"$out"
}

capture_plugins() {
  tag="$1"
  mkdir -p "$RUN_DIR/plugins" 2>/dev/null
  : >"$RUN_DIR/plugins/p.l-${tag}.txt"; : >"$RUN_DIR/plugins/plugin-files-${tag}.tsv"
  for root in $(find_roots); do
    find "$root" -xdev -maxdepth 6 -type f -name 'p.l' -print 2>/dev/null | while IFS= read -r p; do
      printf '\n### %s\n' "$p" >>"$RUN_DIR/plugins/p.l-${tag}.txt"; cat "$p" >>"$RUN_DIR/plugins/p.l-${tag}.txt" 2>/dev/null
    done
    find "$root" -xdev -maxdepth 6 -type f \( -name '*.jar' -o -name '*.apk' -o -name '*.dex' -o -name '*.odex' -o -name '*.vdex' -o -name '*.so' -o -name 'p.l' -o -iname '*theme*' -o -iname '*plugin*' -o -iname '*sfp*' \) -print 2>/dev/null | head -n 10000 | while IFS= read -r p; do
      st="$(stat -c '%s\t%Y\t%i\t%a\t%u\t%g' "$p" 2>/dev/null)"; h="$(sha_file "$p")"; printf '%s\t%s\t%s\n' "$p" "$st" "$h"
    done >>"$RUN_DIR/plugins/plugin-files-${tag}.tsv"
  done
  sed 's#\\/#/#g' "$RUN_DIR/plugins/p.l-${tag}.txt" >"$RUN_DIR/plugins/p.l-${tag}-normalised.txt" 2>/dev/null
}

capture_theme_refs() {
  out="$1"; : >"$out"
  for root in $(find_roots); do
    for sub in shared_prefs files; do
      [ -d "$root/$sub" ] || continue
      find "$root/$sub" -xdev -maxdepth 4 -type f -size -2M -print 2>/dev/null | head -n 2000 | while IFS= read -r f; do
        grep -aHinE 'theme|skin|plugin|sfp_|launcher\.variety|app_p_|dofun' "$f" 2>/dev/null | head -n 100
      done
    done
  done | head -n 10000 >"$out"
}

package_state() {
  mkdir -p "$RUN_DIR/packages" "$RUN_DIR/mounts" 2>/dev/null
  capture 'DoFun package' 8 "$RUN_DIR/packages/dofun.txt" "dumpsys package '$DOFUN_PACKAGE'" >/dev/null 2>&1 || warn 'dumpsys package DoFun failed; continuing'
  capture 'custom package' 6 "$RUN_DIR/packages/custom.txt" "dumpsys package '$CUSTOM_PACKAGE'" >/dev/null 2>&1 || warn 'custom package dumpsys not available; continuing'
  capture 'theme-related package list' 8 "$RUN_DIR/packages/theme-packages.txt" "if command -v pm >/dev/null 2>&1; then pm list packages -f; elif command -v cmd >/dev/null 2>&1; then cmd package list packages -f; else exit 127; fi | grep -Ei 'dofun|variety|theme|sfp_|launcher\\.variety' | head -n 2000" >/dev/null 2>&1 || warn 'pm/cmd package list unavailable; continuing'
  capture 'init mountinfo' 6 "$RUN_DIR/mounts/init.txt" "grep -E '(/data/adb|magisk|com\\.dofun\\.variety|app_p_)' /proc/1/mountinfo | head -n 5000" >/dev/null 2>&1 || warn 'init mountinfo filter failed; continuing'
}

capture_environment() {
  capture 'environment' 8 "$RUN_DIR/environment.txt" 'echo "id=$(id 2>/dev/null)"; echo "context=$(cat /proc/self/attr/current 2>/dev/null)"; echo "getenforce=$(getenforce 2>/dev/null)"; echo "self_mntns=$(readlink /proc/self/ns/mnt 2>/dev/null)"; echo "init_mntns=$(readlink /proc/1/ns/mnt 2>/dev/null)"; echo "android=$(getprop ro.build.version.release 2>/dev/null)"; echo "sdk=$(getprop ro.build.version.sdk 2>/dev/null)"; echo "tw=$(getprop ro.tw.version 2>/dev/null)"; echo "wm_size=$(wm size 2>/dev/null | tr "\n" ";")"; echo "wm_density=$(wm density 2>/dev/null | tr "\n" ";")"' >/dev/null 2>&1 || warn 'environment probe incomplete'
}

start_logcat() {
  if ! have logcat; then printf '# logcat unavailable\n' >"$RUN_DIR/runtime/logcat-filtered.txt"; LOGCAT_PID=""; return 0; fi
  pat='dofun|variety|replugin|plugin|theme|skin|sfp_|app_p_|launcher\.variety|PluginInfo|PluginManager|PluginFastInstall|PackageParser|PackageManager|ClassLoader|DexPathList|Resources\$NotFound|InflateException|ClassNotFound|NoClassDefFound|VerifyError|NoSuchMethod|NoSuchField|IllegalAccess|JSONException|SecurityException|certificate|signature|verify|AndroidRuntime|FATAL EXCEPTION|avc: denied'
  ( logcat -b all -v threadtime -T 1 2>/dev/null | grep -Ei "$pat" | head -n "$MAX_LOG_LINES" >"$RUN_DIR/runtime/logcat-filtered.txt" ) & LOGCAT_PID=$!
}

sample_once() {
  ts="$(now)"
  for d in /proc/[0-9]*; do
    [ -r "$d/cmdline" ] || continue
    cmd="$(tr '\000' ' ' <"$d/cmdline" 2>/dev/null)"
    case "$cmd" in *com.dofun.variety*) ;; *) continue;; esac
    pid="${d#/proc/}"; printf 'PROC\t%s\t%s\t%s\n' "$ts" "$pid" "$cmd"
    if [ -r "$d/maps" ]; then
      awk -v t="$ts" -v p="$pid" -v c="$cmd" '{x=$NF; low=tolower($0); if (x ~ /^\/data\// || low ~ /(app_p_|dofun|variety|plugin|theme|sfp_|\.jar$|\.apk$|\.dex$|\.odex$|\.vdex$|\.so$)/) print "MAP\t" t "\t" p "\t" c "\t" x}' "$d/maps" 2>/dev/null | head -n 1000
    fi
    if [ -d "$d/fd" ]; then
      for fd in "$d"/fd/*; do target="$(readlink "$fd" 2>/dev/null)"; case "$target" in *com.dofun.variety*|*app_p_*|*.jar|*.apk|*.dex|*.odex|*.vdex|*.so|*theme*|*plugin*|*sfp_*) printf 'FD\t%s\t%s\t%s\t%s\n' "$ts" "$pid" "$cmd" "$target";; esac; done
    fi
    [ -r "$d/mountinfo" ] && grep -E '(/data/adb|magisk|com\.dofun\.variety|app_p_)' "$d/mountinfo" 2>/dev/null | head -n 200 | while IFS= read -r l; do printf 'MOUNT\t%s\t%s\t%s\t%s\n' "$ts" "$pid" "$cmd" "$l"; done
  done
}

start_sampler() {
  ( i=0; while [ "$i" -lt 18 ]; do sample_once; i=$((i + 1)); sleep 5; done ) | head -n "$MAX_RUNTIME_LINES" >"$RUN_DIR/runtime/process-paths.tsv" & SAMPLER_PID=$!
}

stop_bg() {
  for p in "$LOGCAT_PID" "$SAMPLER_PID"; do [ -n "$p" ] || continue; kill -TERM "$p" 2>/dev/null; wait "$p" 2>/dev/null; done
  LOGCAT_PID=""; SAMPLER_PID=""
}

make_analysis() {
  mkdir -p "$RUN_DIR/analysis" 2>/dev/null
  diff -u "$RUN_DIR/snapshots/tree-before.tsv" "$RUN_DIR/snapshots/tree-after.tsv" 2>/dev/null | head -n 10000 >"$RUN_DIR/analysis/tree.diff"
  diff -u "$RUN_DIR/snapshots/refs-before.txt" "$RUN_DIR/snapshots/refs-after.txt" 2>/dev/null | head -n 8000 >"$RUN_DIR/analysis/theme-refs.diff"
  diff -u "$RUN_DIR/plugins/plugin-files-before.tsv" "$RUN_DIR/plugins/plugin-files-after.tsv" 2>/dev/null | head -n 10000 >"$RUN_DIR/analysis/plugin-files.diff"
  diff -u "$RUN_DIR/plugins/p.l-before-normalised.txt" "$RUN_DIR/plugins/p.l-after-normalised.txt" 2>/dev/null | head -n 8000 >"$RUN_DIR/analysis/p.l.diff"
  : >"$RUN_DIR/analysis/overlay-candidates.tsv"
  printf 'source\tpath\n' >>"$RUN_DIR/analysis/overlay-candidates.tsv"
  awk -F '\t' '$1=="MAP" || $1=="FD" {print $1 "\t" $5}' "$RUN_DIR/runtime/process-paths.tsv" 2>/dev/null | grep -E 'app_p_|com\.dofun\.variety|\.jar$|\.apk$|\.dex$|\.odex$|\.vdex$|\.so$' >>"$RUN_DIR/analysis/overlay-candidates.tsv"
  grep -aoE '/data/[^[:space:]"}]+app_p_[^[:space:]"}]+' "$RUN_DIR/plugins/p.l-after-normalised.txt" 2>/dev/null | sort -u | sed 's/^/P_L\t/' >>"$RUN_DIR/analysis/overlay-candidates.tsv"
  sort -u "$RUN_DIR/analysis/overlay-candidates.tsv" -o "$RUN_DIR/analysis/overlay-candidates.tsv" 2>/dev/null
  capture 'kernel tail' 5 "$RUN_DIR/runtime/kernel-filtered.txt" "dmesg 2>/dev/null | tail -n 1800 | grep -Ei 'avc: denied|dofun|variety|replugin|plugin|theme|sfp_|app_p_' | tail -n 1000" >/dev/null 2>&1 || warn 'kernel log unavailable; continuing'
}

load_packager() {
  [ -r "$STATE_DIR/packager.conf" ] || return 1
  PACKAGER_KIND="$(sed -n 's/^PACKAGER_KIND=//p' "$STATE_DIR/packager.conf" 2>/dev/null | head -n1)"
  PACKAGER_PATH="$(sed -n 's/^PACKAGER_PATH=//p' "$STATE_DIR/packager.conf" 2>/dev/null | head -n1)"
  [ -x "$PACKAGER_PATH" ]
}

package_zip() {
  load_packager || { warn 'configured ZIP packager unavailable; live folder retained'; return 1; }
  zipfile="$EXPORT_ROOT/TS18-theme-runtime-diagnostic-run${RUN_NO}-$(date +%Y%m%d-%H%M%S).zip"
  if [ "$PACKAGER_KIND" = zip ]; then
    ( cd "$EXPORT_ROOT" 2>/dev/null && run_timeout 15 "$PACKAGER_PATH" -q -r "$zipfile" "$(basename "$RUN_DIR")" ) >/dev/null 2>&1; rc=$?
  else
    helper="$STATE_DIR/make-zip.py"; cat >"$helper" <<'PY'
from pathlib import Path
import sys, zipfile
src=Path(sys.argv[1]).resolve(); out=Path(sys.argv[2]).resolve()
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for p in sorted(src.rglob('*')):
        if p.is_file(): z.write(p,p.relative_to(src.parent).as_posix())
PY
    run_timeout 15 "$PACKAGER_PATH" "$helper" "$RUN_DIR" "$zipfile" >/dev/null 2>&1; rc=$?
  fi
  [ "$rc" -eq 0 ] && [ -s "$zipfile" ] || { rm -f "$zipfile" 2>/dev/null; warn 'ZIP creation failed; live folder retained'; return 1; }
  h="$(sha_file "$zipfile")"; printf '%s  %s\n' "$h" "$(basename "$zipfile")" >"${zipfile}.sha256.txt" 2>/dev/null
  log "ZIP_READY=$zipfile"; log "ZIP_SHA256=$h"; return 0
}

cleanup() {
  rc=$?; trap - EXIT INT TERM HUP; stop_bg
  [ -n "$WATCHDOG_PID" ] && kill -TERM "$WATCHDOG_PID" 2>/dev/null && wait "$WATCHDOG_PID" 2>/dev/null
  rm -rf "$STATE_DIR/lock" 2>/dev/null
  if [ -n "$LIVE" ]; then
    if [ "$rc" -eq 0 ]; then [ "$WARNINGS" -gt 0 ] && log 'FINAL_STATUS=COMPLETED WITH WARNINGS' || log 'FINAL_STATUS=SUCCESS'; else log "FINAL_STATUS=FAILED rc=$rc"; fi
  fi
  exit "$rc"
}

acquire_slot() {
  mkdir -p "$STATE_DIR" 2>/dev/null || return 1
  bid="$(boot_id)"; [ -n "$bid" ] || bid=unknown
  if [ -d "$STATE_DIR/lock" ]; then old="$(cat "$STATE_DIR/lock/boot-id" 2>/dev/null)"; [ "$old" = "$bid" ] && return 2; rm -rf "$STATE_DIR/lock" 2>/dev/null; fi
  mkdir "$STATE_DIR/lock" 2>/dev/null || return 2; printf '%s\n' "$bid" >"$STATE_DIR/lock/boot-id"
  last="$(cat "$STATE_DIR/last-boot" 2>/dev/null)"; [ "$last" = "$bid" ] && { rm -rf "$STATE_DIR/lock"; return 2; }
  count="$(cat "$STATE_DIR/run-count" 2>/dev/null | tr -d '\r\n')"; case "$count" in ''|*[!0-9]*) count=0;; esac
  [ "$count" -ge "$MAX_RUNS" ] 2>/dev/null && { rm -rf "$STATE_DIR/lock"; return 3; }
  RUN_NO=$((count + 1)); BOOT_ID="$bid"; return 0
}

wait_download() { i=0; while [ "$i" -lt "$STORAGE_WAIT_SECONDS" ]; do [ -d /storage/emulated/0/Download ] && [ -w /storage/emulated/0/Download ] && { mkdir -p "$EXPORT_ROOT" 2>/dev/null; return $?; }; i=$((i + 1)); sleep 1; done; return 1; }

run_worker() {
  [ "$(id -u 2>/dev/null)" = 0 ] || { printf 'FAILED: worker requires UID 0\n' >&2; return 1; }
  trap cleanup EXIT; trap 'exit 130' INT TERM HUP
  acquire_slot; slot=$?; case "$slot" in 0) ;; 2|3) return 0;; *) return 1;; esac
  wait_download || { rm -rf "$STATE_DIR/lock" 2>/dev/null; return 0; }
  stamp="$(date +%Y%m%d-%H%M%S 2>/dev/null || echo unknown)"; RUN_DIR="$EXPORT_ROOT/live-run${RUN_NO}-$stamp"
  mkdir -p "$RUN_DIR/runtime" "$RUN_DIR/snapshots" "$RUN_DIR/analysis" "$RUN_DIR/plugins" "$RUN_DIR/packages" "$RUN_DIR/mounts" || return 1
  LIVE="$RUN_DIR/LIVE.txt"; : >"$LIVE"
  printf '%s\n' "$RUN_NO" >"$STATE_DIR/run-count" || return 1; printf '%s\n' "$BOOT_ID" >"$STATE_DIR/last-boot" || return 1
  ( sleep "$HARD_SECONDS"; kill -TERM $$ 2>/dev/null ) & WATCHDOG_PID=$!
  log "TS18 activation diagnostic v$SCRIPT_VERSION run=$RUN_NO/$MAX_RUNS hard_cap=${HARD_SECONDS}s"
  log 'READ_ONLY: no DoFun files, p.l, packages, mounts or SELinux state will be changed.'
  log 'PLAYBOOK_NOW: apply several known-working themes (including SFP_TW/TS10-family if available), then TS18 Dashboard Theme, wait for its disappearance/fallback, then return to a working theme.'
  capture_environment; package_state; capture_plugins before
  snapshot_tree "$RUN_DIR/snapshots/tree-before.tsv"; capture_theme_refs "$RUN_DIR/snapshots/refs-before.txt"
  log "Runtime capture active for ${RUNTIME_SECONDS}s. Perform the theme-selection sequence now."
  start_logcat; start_sampler; sleep "$RUNTIME_SECONDS"; stop_bg
  log 'Runtime window ended; collecting after-state.'
  snapshot_tree "$RUN_DIR/snapshots/tree-after.tsv"; capture_theme_refs "$RUN_DIR/snapshots/refs-after.txt"; capture_plugins after; make_analysis
  {
    printf 'script_version=%s\nrun=%s/%s\nboot_id=%s\nhard_limit_seconds=%s\nruntime_seconds=%s\nwarnings=%s\n' "$SCRIPT_VERSION" "$RUN_NO" "$MAX_RUNS" "$BOOT_ID" "$HARD_SECONDS" "$RUNTIME_SECONDS" "$WARNINGS"
    printf 'interpretation=DoFun preview/listing succeeded previously; this capture targets local activation/RePlugin failure while offline.\n'
    printf 'overlay_note=overlay-candidates.tsv is evidence only; do not mount every listed path.\n'
  } >"$RUN_DIR/SUMMARY.txt"
  package_zip
  zip_rc=$?
  [ "$zip_rc" -eq 0 ] || warn 'ZIP was not produced; the complete live-run folder remains available.'
  log 'Capture complete. Upload the run ZIP from Download; if ZIP packaging failed, upload the live-run folder.'
  return 0
}

service_entry() {
  [ "$(id -u 2>/dev/null)" = 0 ] || return 0
  c="$(cat "$STATE_DIR/run-count" 2>/dev/null | tr -d '\r\n')"; case "$c" in ''|*[!0-9]*) c=0;; esac
  [ "$c" -ge "$MAX_RUNS" ] 2>/dev/null && return 0
  /system/bin/sh "$SERVICE_PATH" --worker >/dev/null 2>&1 &
}

usage() { printf 'TS18 activation diagnostic v%s\n  sh %s --install\n  sh %s --status\n' "$SCRIPT_VERSION" "$0" "$0"; }
case "${1:-}" in
  --install) install_self; exit $?;;
  --status) show_status; exit $?;;
  --worker) run_worker; exit $?;;
  -h|--help) usage; exit 0;;
  '') service_entry; exit 0;;
  *) usage >&2; exit 1;;
esac
