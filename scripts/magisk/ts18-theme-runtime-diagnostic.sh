#!/system/bin/sh
# shellcheck shell=sh disable=SC2016,SC2046,SC2317
# TS18 / DoFun activation + LSPosed feasibility diagnostic for Magisk service.d.
# Read-only against DoFun, LSPosed and package state. Version 4.0.0.

SCRIPT_VERSION="4.0.0"
SERVICE_PATH="/data/adb/service.d/99-ts18-theme-runtime-diag.sh"
STATE_DIR="/data/adb/ts18-theme-runtime-diag-state"
EXPORT_ROOT="/storage/emulated/0/Download/TS18-theme-runtime-diagnostic"
DOFUN_PACKAGE="com.dofun.variety"
CUSTOM_PACKAGE="launcher.variety.theme.plugin.sfp_cbk_black"
MAX_RUNS=2
HARD_SECONDS=210
RUNTIME_SECONDS=100
STORAGE_WAIT_SECONDS=90
SAMPLER_INTERVAL_SECONDS=4
SAMPLER_SAMPLES=25
MAX_TREE_LINES=30000
MAX_LOG_LINES=14000
MAX_RUNTIME_LINES=24000
MAX_PL_FILES=64
MAX_PL_DISCOVERED=512
MAX_PL_BYTES=262144
MAX_LSPOSED_LOG_FILES=8
MAX_LSPOSED_LOG_LINES=8000
MAX_XPOSED_MODULES=96
MAX_FRAMEWORK_FILES=512
MAX_ARCHIVES=24
MAX_ARCHIVE_LINES=2500
MAX_STATIC_MARKERS=4000

RUN_NO=0
RUN_DIR=""
LIVE=""
LOGCAT_PID=""
LOGCAT_FILTER_PID=""
LOGCAT_FIFO=""
LOGCAT_FILTERED_TMP=""
SAMPLER_PID=""
SAMPLER_RAW=""
WATCHDOG_PID=""
PACKAGER_KIND=""
PACKAGER_PATH=""
WARNINGS=0

now() { date -Iseconds 2>/dev/null || date '+%Y-%m-%dT%H:%M:%S%z' 2>/dev/null || date; }
log() { line="$(now) $*"; [ -n "$LIVE" ] && printf '%s\n' "$line" >>"$LIVE" 2>/dev/null; printf '%s\n' "$line" 2>/dev/null; }
warn() { WARNINGS=$((WARNINGS + 1)); log "WARNING: $*"; }
have() { command -v "$1" >/dev/null 2>&1; }
boot_id() { tr -d '\r\n' </proc/sys/kernel/random/boot_id 2>/dev/null; }

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

safe_apk_path() {
  rp="$(readlink -f "$1" 2>/dev/null)"; [ -n "$rp" ] || rp="$1"
  case "$rp" in
    /data/app/*.apk|/data/app/*/*.apk|/data/app/*/*/*.apk|/system/*.apk|/system/*/*.apk|/product/*.apk|/product/*/*.apk|/system_ext/*.apk|/system_ext/*/*.apk|/vendor/*.apk|/vendor/*/*.apk) printf '%s\n' "$rp"; return 0;;
  esac
  return 1
}

find_termux_tool() {
  for p in "/data/data/com.termux/files/usr/bin/$1" "/data/user/0/com.termux/files/usr/bin/$1" "$(command -v "$1" 2>/dev/null)"; do
    [ -n "$p" ] && [ -x "$p" ] && { printf '%s\n' "$p"; return 0; }
  done
  return 1
}

find_magisk() {
  for p in "$(command -v magisk 2>/dev/null)" /data/adb/magisk/magisk; do
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
  printf 'recommended_optional_tools=unzip sqlite\n'
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

capture_pl_file() {
  pl_path="$1"; pl_out="$2"
  pl_size="$(stat -c '%s' "$pl_path" 2>/dev/null)"
  case "$pl_size" in ''|*[!0-9]*) pl_size=unknown;; esac
  printf '\n### %s\n' "$pl_path" >>"$pl_out"
  dd if="$pl_path" bs=4096 count=64 2>/dev/null >>"$pl_out"
  if [ "$pl_size" != unknown ] && [ "$pl_size" -gt "$MAX_PL_BYTES" ] 2>/dev/null; then
    printf '\n### TRUNCATED original_bytes=%s limit_bytes=%s\n' "$pl_size" "$MAX_PL_BYTES" >>"$pl_out"
  fi
}

capture_plugins() {
  tag="$1"
  mkdir -p "$RUN_DIR/plugins" 2>/dev/null
  : >"$RUN_DIR/plugins/p.l-${tag}.txt"; : >"$RUN_DIR/plugins/plugin-files-${tag}.tsv"
  pl_paths="$RUN_DIR/plugins/p.l-${tag}-paths.txt"
  pl_omitted="$RUN_DIR/plugins/p.l-${tag}-omitted.txt"
  pl_coverage="$RUN_DIR/plugins/p.l-${tag}-coverage.txt"
  for root in $(find_roots); do
    find "$root" -xdev -maxdepth 6 -type f -name 'p.l' -print 2>/dev/null
  done | sort -u | head -n "$MAX_PL_DISCOVERED" >"$pl_paths"
  pl_total="$(wc -l <"$pl_paths" 2>/dev/null | tr -d '[:space:]')"; case "$pl_total" in ''|*[!0-9]*) pl_total=0;; esac
  pl_captured="$pl_total"; [ "$pl_captured" -gt "$MAX_PL_FILES" ] 2>/dev/null && pl_captured="$MAX_PL_FILES"
  pl_omitted_count=$((pl_total - pl_captured))
  printf 'discovered_up_to_cap=%s\ncaptured=%s\nomitted_listed=%s\ncontent_limit_files=%s\ndiscovery_path_limit=%s\n' \
    "$pl_total" "$pl_captured" "$pl_omitted_count" "$MAX_PL_FILES" "$MAX_PL_DISCOVERED" >"$pl_coverage"
  head -n "$MAX_PL_FILES" "$pl_paths" | while IFS= read -r p; do
    capture_pl_file "$p" "$RUN_DIR/plugins/p.l-${tag}.txt"
  done
  awk -v max="$MAX_PL_FILES" 'NR > max { print }' "$pl_paths" >"$pl_omitted"
  for root in $(find_roots); do
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
  capture 'theme-related package list' 8 "$RUN_DIR/packages/theme-packages.txt" "if command -v pm >/dev/null 2>&1; then pm list packages -f; elif command -v cmd >/dev/null 2>&1; then cmd package list packages -f; else exit 127; fi | grep -Ei 'dofun|variety|theme|sfp_|launcher\\.variety|xposed|lsposed' | head -n 2000" >/dev/null 2>&1 || warn 'pm/cmd package list unavailable; continuing'
  capture 'init mountinfo' 6 "$RUN_DIR/mounts/init.txt" "grep -E '(/data/adb|magisk|lspd|lsposed|zygisk|xposed|com\\.dofun\\.variety|app_p_)' /proc/1/mountinfo | head -n 5000" >/dev/null 2>&1 || warn 'init mountinfo filter failed; continuing'
}

capture_environment() {
  capture 'environment' 8 "$RUN_DIR/environment.txt" 'echo "id=$(id 2>/dev/null)"; echo "context=$(cat /proc/self/attr/current 2>/dev/null)"; echo "getenforce=$(getenforce 2>/dev/null)"; echo "self_mntns=$(readlink /proc/self/ns/mnt 2>/dev/null)"; echo "init_mntns=$(readlink /proc/1/ns/mnt 2>/dev/null)"; echo "android=$(getprop ro.build.version.release 2>/dev/null)"; echo "sdk=$(getprop ro.build.version.sdk 2>/dev/null)"; echo "security_patch=$(getprop ro.build.version.security_patch 2>/dev/null)"; echo "fingerprint=$(getprop ro.build.fingerprint 2>/dev/null)"; echo "tw=$(getprop ro.tw.version 2>/dev/null)"; echo "zygote=$(getprop ro.zygote 2>/dev/null)"; echo "abi=$(getprop ro.product.cpu.abi 2>/dev/null)"; echo "abilist=$(getprop ro.product.cpu.abilist 2>/dev/null)"; echo "abilist32=$(getprop ro.product.cpu.abilist32 2>/dev/null)"; echo "abilist64=$(getprop ro.product.cpu.abilist64 2>/dev/null)"; echo "wm_size=$(wm size 2>/dev/null | tr "\n" ";")"; echo "wm_density=$(wm density 2>/dev/null | tr "\n" ";")"' >/dev/null 2>&1 || warn 'environment probe incomplete'
}

capture_magisk_state() {
  mkdir -p "$RUN_DIR/root" 2>/dev/null
  mg="$(find_magisk 2>/dev/null)"
  {
    printf '# captured_at=%s\n' "$(now)"
    if [ -n "$mg" ]; then
      printf 'magisk_path=%s\n' "$mg"
      printf 'magisk_version='; run_timeout 5 "$mg" -v 2>&1 | head -n 1
      printf 'magisk_version_code='; run_timeout 5 "$mg" -V 2>&1 | head -n 1
      printf '\n## zygisk settings\n'
      run_timeout 6 "$mg" --sqlite "SELECT key,value FROM settings WHERE key LIKE '%zygisk%' OR key LIKE '%denylist%' ORDER BY key;" 2>&1 | head -n 200
      printf '\n## DoFun denylist rows\n'
      run_timeout 6 "$mg" --denylist ls 2>&1 | grep -F "$DOFUN_PACKAGE" | head -n 100
    else
      printf 'magisk_path=unavailable\n'
    fi
    printf '\n## /data/adb/modules\n'
    for d in /data/adb/modules/*; do
      [ -d "$d" ] || continue
      printf '\n### module_dir=%s\n' "$d"
      stat -c 'stat=%n\t%F\t%s\t%Y\t%i\t%a\t%u\t%g' "$d" 2>/dev/null
      for flag in disable remove update; do [ -e "$d/$flag" ] && printf 'flag=%s\n' "$flag"; done
      if [ -r "$d/module.prop" ]; then sed -n '1,120p' "$d/module.prop" 2>/dev/null; fi
    done
  } >"$RUN_DIR/root/magisk-state.txt" 2>&1

  : >"$RUN_DIR/root/zygisk-native-files.tsv"
  for d in /data/adb/modules/*; do
    [ -d "$d" ] || continue
    find "$d" -xdev -maxdepth 4 -type f \( -name '*.so' -o -name 'module.prop' \) -print 2>/dev/null | head -n 3000 | while IFS= read -r f; do
      case "$f" in *zygisk*|*lsposed*|*lspd*|*xposed*|*riru*) ;; *) continue;; esac
      st="$(stat -c '%s\t%Y\t%i\t%a\t%u\t%g' "$f" 2>/dev/null)"; h="$(sha_file "$f")"; printf '%s\t%s\t%s\n' "$f" "$st" "$h"
    done
  done >>"$RUN_DIR/root/zygisk-native-files.tsv"

  capture 'root injection processes' 8 "$RUN_DIR/root/processes.txt" "ps -A 2>/dev/null | grep -Ei 'magisk|zygisk|lspd|lsposed|xposed|riru|zygote|dofun' | head -n 2000" >/dev/null 2>&1 || warn 'root/zygisk process listing unavailable'
}

find_lsposed_db() {
  for p in /data/adb/lspd/config/modules_config.db /data/adb/lspd/modules_config.db; do
    [ -f "$p" ] && { printf '%s\n' "$p"; return 0; }
  done
  find /data/adb/lspd -xdev -maxdepth 4 -type f -name 'modules_config.db' -print 2>/dev/null | head -n 1
}

sqlite_readonly_ok() {
  [ -n "$1" ] || return 1
  run_timeout 5 "$1" -help 2>&1 | grep -q -- '-readonly'
}

capture_lsposed_db() {
  db="$(find_lsposed_db 2>/dev/null)"
  sqlite="$(find_termux_tool sqlite3 2>/dev/null)"
  out="$RUN_DIR/lsposed/database.txt"
  modules_out="$RUN_DIR/lsposed/modules.tsv"
  scope_out="$RUN_DIR/lsposed/dofun-scope.tsv"
  printf 'mid\tmodule_pkg_name\tapk_path\tenabled\tauto_include\n' >"$modules_out"
  printf 'module_pkg_name\tapp_pkg_name\tuser_id\n' >"$scope_out"

  {
    printf '# captured_at=%s\n' "$(now)"
    if [ -z "$db" ]; then
      printf 'database=not_found\n'
    else
      printf 'database=%s\nsha256=%s\n' "$db" "$(sha_file "$db")"
      stat -c 'stat=%n\t%F\t%s\t%Y\t%i\t%a\t%u\t%g' "$db" 2>/dev/null
      for side in "${db}-wal" "${db}-shm"; do
        [ -e "$side" ] && stat -c 'sidecar=%n\t%F\t%s\t%Y\t%i\t%a\t%u\t%g' "$side" 2>/dev/null
      done
      if [ -z "$sqlite" ]; then
        printf 'sqlite3=unavailable\n'
      elif ! sqlite_readonly_ok "$sqlite"; then
        printf 'sqlite3=%s\nreadonly_query=unsupported_by_cli; rows_not_queried\n' "$sqlite"
      else
        printf 'sqlite3=%s\nreadonly_query=supported\n' "$sqlite"
        printf '\n## tables\n'; run_timeout 6 "$sqlite" -readonly "$db" '.tables' 2>&1
        printf '\n## modules schema\n'; run_timeout 6 "$sqlite" -readonly "$db" 'PRAGMA table_info(modules);' 2>&1 | head -n 200
        printf '\n## scope schema\n'; run_timeout 6 "$sqlite" -readonly "$db" 'PRAGMA table_info(scope);' 2>&1 | head -n 200
        printf '\n## configs schema only (values intentionally excluded)\n'; run_timeout 6 "$sqlite" -readonly "$db" 'PRAGMA table_info(configs);' 2>&1 | head -n 200
      fi
    fi
  } >"$out" 2>&1

  [ -n "$db" ] && [ -n "$sqlite" ] && sqlite_readonly_ok "$sqlite" || return 0

  sql_tmp="$STATE_DIR/sqlite-query.$$"; sql_err="$STATE_DIR/sqlite-query.$$.err"
  query="SELECT mid,module_pkg_name,apk_path,enabled,auto_include FROM modules ORDER BY mid LIMIT $MAX_XPOSED_MODULES;"
  rm -f "$sql_tmp" "$sql_err" 2>/dev/null
  run_timeout 8 "$sqlite" -readonly -separator '|' "$db" "$query" >"$sql_tmp" 2>"$sql_err"; sql_rc=$?
  printf '\nmodules_query_primary_exit=%s\n' "$sql_rc" >>"$out"
  [ -s "$sql_err" ] && { printf 'modules_query_primary_stderr=\n'; head -n 30 "$sql_err"; } >>"$out"
  [ "$sql_rc" -eq 0 ] && tr '|' '\t' <"$sql_tmp" >>"$modules_out"

  module_lines="$(wc -l <"$modules_out" 2>/dev/null | tr -d '[:space:]')"; case "$module_lines" in ''|*[!0-9]*) module_lines=0;; esac
  if [ "$sql_rc" -ne 0 ] || [ "$module_lines" -le 1 ] 2>/dev/null; then
    printf 'mid\tmodule_pkg_name\tapk_path\tenabled\tauto_include\n' >"$modules_out"
    query="SELECT mid,module_pkg_name,apk_path,enabled,NULL FROM modules ORDER BY mid LIMIT $MAX_XPOSED_MODULES;"
    rm -f "$sql_tmp" "$sql_err" 2>/dev/null
    run_timeout 8 "$sqlite" -readonly -separator '|' "$db" "$query" >"$sql_tmp" 2>"$sql_err"; sql_rc=$?
    printf '\nmodules_query_fallback_exit=%s\n' "$sql_rc" >>"$out"
    [ -s "$sql_err" ] && { printf 'modules_query_fallback_stderr=\n'; head -n 30 "$sql_err"; } >>"$out"
    [ "$sql_rc" -eq 0 ] && tr '|' '\t' <"$sql_tmp" >>"$modules_out"
  fi

  query="SELECT m.module_pkg_name,s.app_pkg_name,s.user_id FROM scope s LEFT JOIN modules m ON m.mid=s.mid WHERE s.app_pkg_name='$DOFUN_PACKAGE' ORDER BY m.module_pkg_name,s.user_id LIMIT 256;"
  rm -f "$sql_tmp" "$sql_err" 2>/dev/null
  run_timeout 8 "$sqlite" -readonly -separator '|' "$db" "$query" >"$sql_tmp" 2>"$sql_err"; sql_rc=$?
  printf '\ndofun_scope_query_exit=%s\n' "$sql_rc" >>"$out"
  [ -s "$sql_err" ] && { printf 'dofun_scope_query_stderr=\n'; head -n 30 "$sql_err"; } >>"$out"
  [ "$sql_rc" -eq 0 ] && tr '|' '\t' <"$sql_tmp" >>"$scope_out"
  rm -f "$sql_tmp" "$sql_err" 2>/dev/null
}

zip_has_member() {
  zh_unzip="$1"; zh_apk="$2"; zh_member="$3"
  run_timeout 6 "$zh_unzip" -Z1 "$zh_apk" 2>/dev/null | grep -Fx "$zh_member" >/dev/null 2>&1
}

capture_xposed_module_apks() {
  unzip_tool="$(find_termux_tool unzip 2>/dev/null)"
  out="$RUN_DIR/lsposed/module-apk-markers.txt"
  : >"$out"
  [ -n "$unzip_tool" ] || { printf 'unzip=unavailable; module APK metadata inspection skipped\n' >"$out"; return 0; }
  [ -s "$RUN_DIR/lsposed/modules.tsv" ] || { printf 'module_database_rows=unavailable\n' >"$out"; return 0; }
  awk -F '\t' -v max="$MAX_XPOSED_MODULES" 'NR>1 && NR<=max+1 {print $2 "\t" $3 "\t" $4 "\t" $5}' "$RUN_DIR/lsposed/modules.tsv" | while IFS="$(printf '\t')" read -r pkg apk enabled auto_include; do
    [ -n "$pkg" ] || continue
    safe="$(printf '%s' "$pkg" | tr -c 'A-Za-z0-9._-' '_')"
    resolved="$(safe_apk_path "$apk" 2>/dev/null)"
    {
      printf '\n### module=%s enabled=%s auto_include=%s\n' "$pkg" "$enabled" "$auto_include"
      if [ -z "$resolved" ] || [ ! -f "$resolved" ]; then printf 'apk_path_rejected_or_missing=%s\n' "$apk"; continue; fi
      printf 'apk=%s\nsha256=%s\n' "$resolved" "$(sha_file "$resolved")"
      stat -c 'stat=%n\t%F\t%s\t%Y\t%i\t%a\t%u\t%g' "$resolved" 2>/dev/null
      for member in META-INF/xposed/java_init.list META-INF/xposed/native_init.list META-INF/xposed/scope.list META-INF/xposed/module.prop assets/xposed_init; do
        if zip_has_member "$unzip_tool" "$resolved" "$member"; then printf 'member=%s\n' "$member"; fi
      done
      for member in META-INF/xposed/module.prop META-INF/xposed/scope.list META-INF/xposed/java_init.list assets/xposed_init; do
        if zip_has_member "$unzip_tool" "$resolved" "$member"; then
          printf -- '--- %s ---\n' "$member"
          run_timeout 6 "$unzip_tool" -p "$resolved" "$member" 2>/dev/null | dd bs=4096 count=16 2>/dev/null
          printf '\n'
        fi
      done
      printf 'marker_summary='; run_timeout 6 "$unzip_tool" -Z1 "$resolved" 2>/dev/null | grep -E '^(META-INF/xposed/|assets/xposed_init$)' | tr '\n' ',' | head -c 2048; printf '\n'
      printf 'inspection_file=%s\n' "$safe"
    } >>"$out" 2>&1
  done
}

LSPOSED_LOG_PATTERN='LSPosed|LSPosedLogcat|lspd|Xposed|libxposed|Zygisk|Riru|API[ _-]?version|framework[ _-]?version|injection|scope|module|hook|deopt|com\.dofun\.variety|dofun|variety|jiagu|replugin|ClassLoader|DexFile|DexPathList'

capture_lsposed_logs() {
  out="$RUN_DIR/lsposed/logs-filtered.txt"; paths="$RUN_DIR/lsposed/log-paths.txt"
  : >"$out"; : >"$paths"
  [ -d /data/adb/lspd/log ] || { printf 'lsposed_log_dir=not_found\n' >"$out"; return 0; }
  find /data/adb/lspd/log -xdev -maxdepth 1 -type f -name '*.log' -print 2>/dev/null | sort | tail -n "$MAX_LSPOSED_LOG_FILES" >"$paths"
  while IFS= read -r f; do
    [ -r "$f" ] || continue
    printf '\n### %s\n' "$f" >>"$out"
    tail -n 6000 "$f" 2>/dev/null | grep -Eia "$LSPOSED_LOG_PATTERN" | tail -n "$MAX_LSPOSED_LOG_LINES" >>"$out"
  done <"$paths"
  head -n "$MAX_LSPOSED_LOG_LINES" "$out" >"${out}.cap" 2>/dev/null && mv "${out}.cap" "$out" 2>/dev/null
}

capture_lsposed_state() {
  mkdir -p "$RUN_DIR/lsposed" 2>/dev/null
  {
    printf '# captured_at=%s\n' "$(now)"
    found=0
    for p in /data/adb/lspd /data/adb/modules/*lsposed* /data/adb/modules/*lspd* /data/adb/modules/*xposed* /data/adb/modules/*riru*; do
      [ -e "$p" ] || continue; found=1
      printf '\n### root=%s\n' "$p"
      stat -c 'stat=%n\t%F\t%s\t%Y\t%i\t%a\t%u\t%g' "$p" 2>/dev/null
      [ -r "$p/module.prop" ] && sed -n '1,160p' "$p/module.prop" 2>/dev/null
      find "$p" -xdev -maxdepth 3 -type f \( -name 'module.prop' -o -name '*.apk' -o -name '*.so' -o -name '*.db' -o -name '*.log' \) -print 2>/dev/null | head -n 3000
    done
    [ "$found" -eq 1 ] || printf 'lsposed_roots=not_found\n'
    if [ -x /data/adb/lspd/bin/cli ]; then
      printf '\n## cli status\n'; run_timeout 6 /data/adb/lspd/bin/cli status 2>&1 | head -n 300
    else
      printf '\ncli=not_found_or_not_executable\n'
    fi
  } >"$RUN_DIR/lsposed/framework.txt" 2>&1

  : >"$RUN_DIR/lsposed/framework-files.tsv"
  for p in /data/adb/lspd /data/adb/modules/*lsposed* /data/adb/modules/*lspd* /data/adb/modules/*xposed* /data/adb/modules/*riru*; do
    [ -e "$p" ] || continue
    find "$p" -xdev -maxdepth 5 -type f \( -name '*.apk' -o -name '*.jar' -o -name '*.so' -o -name 'module.prop' -o -name '*daemon*' -o -name '*lspd*' \) -print 2>/dev/null
  done | sort -u | head -n "$MAX_FRAMEWORK_FILES" | while IFS= read -r f; do
    [ -f "$f" ] || continue
    st="$(stat -c '%s\t%Y\t%i\t%a\t%u\t%g' "$f" 2>/dev/null)"; h="$(sha_file "$f")"
    printf '%s\t%s\t%s\n' "$f" "$st" "$h"
  done >>"$RUN_DIR/lsposed/framework-files.tsv"

  capture_lsposed_db
  capture_xposed_module_apks
  capture_lsposed_logs
}

capture_zygotes() {
  mkdir -p "$RUN_DIR/lsposed" 2>/dev/null
  out="$RUN_DIR/lsposed/zygote-injection.txt"; : >"$out"
  for d in /proc/[0-9]*; do
    [ -r "$d/cmdline" ] || continue
    cmd="$(tr '\000' ' ' <"$d/cmdline" 2>/dev/null)"
    case "$cmd" in *zygote*|*lspd*|*lsposed*) ;; *) continue;; esac
    pid="${d#/proc/}"
    printf '\n### pid=%s cmd=%s\n' "$pid" "$cmd" >>"$out"
    printf 'exe=%s\ncontext=%s\nmntns=%s\n' "$(readlink "$d/exe" 2>/dev/null)" "$(cat "$d/attr/current" 2>/dev/null)" "$(readlink "$d/ns/mnt" 2>/dev/null)" >>"$out"
    grep -E '^(Name|Pid|PPid|Uid|Gid|Threads):' "$d/status" 2>/dev/null >>"$out"
    grep -Eia 'lspd|lsposed|zygisk|xposed|riru|libart|app_process|magisk' "$d/maps" 2>/dev/null | head -n 2500 >>"$out"
  done
}

inspect_archive_markers() {
  ia_tag="$1"; ia_filelist="$RUN_DIR/plugins/plugin-files-${ia_tag}.tsv"; ia_out="$RUN_DIR/plugins/archive-markers-${ia_tag}.txt"
  : >"$ia_out"
  unzip_tool="$(find_termux_tool unzip 2>/dev/null)"
  [ -n "$unzip_tool" ] || { printf 'unzip=unavailable; archive member/class marker inspection skipped\n' >"$ia_out"; return 0; }
  [ -s "$ia_filelist" ] || return 0
  awk -F '\t' '$1 ~ /\/app_p_a\// && $1 ~ /\.(jar|apk)$/ {print $1}' "$ia_filelist" | sort -u | head -n "$MAX_ARCHIVES" | while IFS= read -r arc; do
    [ -f "$arc" ] || continue
    printf '\n### archive=%s\nsha256=%s\n' "$arc" "$(sha_file "$arc")" >>"$ia_out"
    run_timeout 7 "$unzip_tool" -Z1 "$arc" 2>/dev/null | grep -Eai '(^AndroidManifest\.xml$|classes[0-9]*\.dex$|^assets/|^lib/|theme|plugin|replugin|sfp_)' | head -n "$MAX_ARCHIVE_LINES" >>"$ia_out"
    for dex in classes.dex classes2.dex classes3.dex; do
      if zip_has_member "$unzip_tool" "$arc" "$dex"; then
        printf -- '--- markers %s ---\n' "$dex" >>"$ia_out"
        run_timeout 8 "$unzip_tool" -p "$arc" "$dex" 2>/dev/null | grep -aoE 'Lcom/qihoo360/replugin/[^;]{1,220};|Lcom/stub/StubApp;|DexClassLoader|BaseDexClassLoader|PathClassLoader|ClassLoader|theme_config\.json|view_config\.json|media_config\.json|sfp_[A-Za-z0-9_]+' | head -n "$MAX_STATIC_MARKERS" >>"$ia_out"
      fi
    done
  done
}

capture_dofun_static() {
  mkdir -p "$RUN_DIR/static" 2>/dev/null
  paths="$RUN_DIR/static/dofun-apk-paths.txt"; : >"$paths"
  if have pm; then run_timeout 8 pm path "$DOFUN_PACKAGE" 2>/dev/null | sed -n 's/^package://p' | head -n 32 >"$paths"; fi
  if [ ! -s "$paths" ] && have cmd; then run_timeout 8 cmd package path "$DOFUN_PACKAGE" 2>/dev/null | sed -n 's/^package://p' | head -n 32 >"$paths"; fi
  out="$RUN_DIR/static/dofun-apk.txt"; : >"$out"
  unzip_tool="$(find_termux_tool unzip 2>/dev/null)"
  while IFS= read -r apk; do
    resolved="$(safe_apk_path "$apk" 2>/dev/null)"
    [ -n "$resolved" ] && [ -f "$resolved" ] || { printf 'rejected_or_missing=%s\n' "$apk" >>"$out"; continue; }
    printf '\n### apk=%s\nsha256=%s\n' "$resolved" "$(sha_file "$resolved")" >>"$out"
    stat -c 'stat=%n\t%F\t%s\t%Y\t%i\t%a\t%u\t%g' "$resolved" 2>/dev/null >>"$out"
    if [ -n "$unzip_tool" ]; then
      printf -- '--- relevant members ---\n' >>"$out"
      run_timeout 8 "$unzip_tool" -Z1 "$resolved" 2>/dev/null | grep -Eai '(^classes[0-9]*\.dex$|^lib/|^assets/.*jiagu|replugin|qihoo|xposed)' | head -n "$MAX_ARCHIVE_LINES" >>"$out"
      for dex in classes.dex classes2.dex classes3.dex; do
        if zip_has_member "$unzip_tool" "$resolved" "$dex"; then
          printf -- '--- runtime/packer markers %s ---\n' "$dex" >>"$out"
          run_timeout 8 "$unzip_tool" -p "$resolved" "$dex" 2>/dev/null | grep -aoE 'Lcom/stub/StubApp;|jiagu(_x86|_x64)?|libjiagu|Lcom/qihoo360/replugin/[^;]{1,220};|DexClassLoader|BaseDexClassLoader|PathClassLoader|ClassLoader|loadClass' | head -n "$MAX_STATIC_MARKERS" >>"$out"
        fi
      done
    else
      printf 'unzip=unavailable; static APK member/DEX marker inspection skipped\n' >>"$out"
    fi
  done <"$paths"
}

LOGCAT_PATTERN='dofun|variety|replugin|plugin|theme|skin|sfp_|app_p_|launcher\.variety|PluginInfo|PluginManager|PluginFastInstall|PackageParser|PackageManager|ClassLoader|BaseDexClassLoader|PathClassLoader|DexClassLoader|DexPathList|DexFile|ClassLoaderContext|Unsupported class loader|Opening an oat|dex2oat|jiagu|StubApp|LSPosed|LSPosedLogcat|lspd|Xposed|libxposed|Zygisk|Riru|Resources\$NotFound|InflateException|ClassNotFound|NoClassDefFound|VerifyError|NoSuchMethod|NoSuchField|IllegalAccess|JSONException|SecurityException|certificate|signature|verify|AndroidRuntime|FATAL EXCEPTION|avc: denied'

capture_logcat_history() {
  out="$RUN_DIR/runtime/logcat-history-filtered.txt"
  if ! have logcat; then printf '# logcat unavailable\n' >"$out"; return 0; fi
  tmp="$STATE_DIR/logcat-history.$$"; rm -f "$tmp" 2>/dev/null
  run_timeout 12 logcat -b all -d -v threadtime -t 8000 >"$tmp" 2>/dev/null
  grep -Eia "$LOGCAT_PATTERN" "$tmp" 2>/dev/null | tail -n "$MAX_LOG_LINES" >"$out"
  rm -f "$tmp" 2>/dev/null
}

make_fifo() {
  if have mkfifo; then mkfifo "$1" 2>/dev/null; return $?; fi
  if [ -x /system/bin/toybox ]; then /system/bin/toybox mkfifo "$1" 2>/dev/null; return $?; fi
  return 1
}

clean_stale_runtime_temp() {
  rm -f "$STATE_DIR"/logcat-run*.fifo "$STATE_DIR"/logcat-filtered-run*.txt "$STATE_DIR"/process-paths-run* "$STATE_DIR"/logcat-history.* "$STATE_DIR"/sqlite-query.* 2>/dev/null
}

start_logcat() {
  if ! have logcat; then printf '# logcat unavailable\n' >"$RUN_DIR/runtime/logcat-filtered.txt"; LOGCAT_PID=""; LOGCAT_FILTER_PID=""; return 0; fi
  LOGCAT_FIFO="$STATE_DIR/logcat-run${RUN_NO}.$$.fifo"
  LOGCAT_FILTERED_TMP="$STATE_DIR/logcat-filtered-run${RUN_NO}.$$.txt"
  rm -f "$LOGCAT_FIFO" "$LOGCAT_FILTERED_TMP" 2>/dev/null
  if ! make_fifo "$LOGCAT_FIFO"; then
    warn 'mkfifo unavailable; logcat capture skipped'
    printf '# logcat capture unavailable: mkfifo failed\n' >"$RUN_DIR/runtime/logcat-filtered.txt"
    LOGCAT_FIFO=""; LOGCAT_FILTERED_TMP=""; return 0
  fi
  ( grep -Ei "$LOGCAT_PATTERN" <"$LOGCAT_FIFO" 2>/dev/null | head -n "$MAX_LOG_LINES" >"$LOGCAT_FILTERED_TMP" ) & LOGCAT_FILTER_PID=$!
  logcat -b all -v threadtime -T 1 >"$LOGCAT_FIFO" 2>/dev/null & LOGCAT_PID=$!
}

sample_once() {
  ts="$(now)"
  for d in /proc/[0-9]*; do
    [ -r "$d/cmdline" ] || continue
    cmd="$(tr '\000' ' ' <"$d/cmdline" 2>/dev/null)"
    case "$cmd" in *com.dofun.variety*) ;; *) continue;; esac
    pid="${d#/proc/}"; ppid="$(sed -n 's/^PPid:[[:space:]]*//p' "$d/status" 2>/dev/null | head -n 1)"
    exe="$(readlink "$d/exe" 2>/dev/null)"; ctx="$(cat "$d/attr/current" 2>/dev/null)"; mnt="$(readlink "$d/ns/mnt" 2>/dev/null)"
    printf 'PROC\t%s\t%s\t%s\tppid=%s\texe=%s\tctx=%s\tmntns=%s\n' "$ts" "$pid" "$cmd" "$ppid" "$exe" "$ctx" "$mnt"
    if [ -r "$d/maps" ]; then
      awk -v t="$ts" -v p="$pid" -v c="$cmd" '{x=$NF; low=tolower($0); if (x ~ /^\/data\// || low ~ /(app_p_|dofun|variety|plugin|theme|sfp_|lspd|lsposed|zygisk|xposed|riru|jiagu|replugin|memfd|dalvik|\.jar$|\.apk$|\.dex$|\.odex$|\.vdex$|\.so$)/) print "MAP\t" t "\t" p "\t" c "\t" x}' "$d/maps" 2>/dev/null | head -n 1800
    fi
    if [ -d "$d/fd" ]; then
      for fd in "$d"/fd/*; do target="$(readlink "$fd" 2>/dev/null)"; case "$target" in *com.dofun.variety*|*app_p_*|*lspd*|*lsposed*|*zygisk*|*xposed*|*riru*|*jiagu*|*replugin*|*.jar|*.apk|*.dex|*.odex|*.vdex|*.so|*theme*|*plugin*|*sfp_*) printf 'FD\t%s\t%s\t%s\t%s\n' "$ts" "$pid" "$cmd" "$target";; esac; done
    fi
    [ -r "$d/mountinfo" ] && grep -E '(/data/adb|magisk|lspd|lsposed|zygisk|xposed|com\.dofun\.variety|app_p_)' "$d/mountinfo" 2>/dev/null | head -n 300 | while IFS= read -r l; do printf 'MOUNT\t%s\t%s\t%s\t%s\n' "$ts" "$pid" "$cmd" "$l"; done
  done
}

start_sampler() {
  SAMPLER_RAW="$STATE_DIR/process-paths-run${RUN_NO}.$$"
  rm -f "$SAMPLER_RAW" 2>/dev/null
  ( i=0; while [ "$i" -lt "$SAMPLER_SAMPLES" ]; do sample_once; i=$((i + 1)); [ "$i" -lt "$SAMPLER_SAMPLES" ] && sleep "$SAMPLER_INTERVAL_SECONDS"; done ) >"$SAMPLER_RAW" 2>/dev/null & SAMPLER_PID=$!
}

stop_pid() {
  sp_pid="$1"; [ -n "$sp_pid" ] || return 0
  if kill -0 "$sp_pid" 2>/dev/null; then
    kill -TERM "$sp_pid" 2>/dev/null
    sp_i=0
    while kill -0 "$sp_pid" 2>/dev/null && [ "$sp_i" -lt 4 ]; do sleep 1; sp_i=$((sp_i + 1)); done
    if kill -0 "$sp_pid" 2>/dev/null; then kill -KILL "$sp_pid" 2>/dev/null; fi
  fi
  wait "$sp_pid" 2>/dev/null || :
}

stop_bg() {
  stop_pid "$LOGCAT_PID"
  stop_pid "$LOGCAT_FILTER_PID"
  stop_pid "$SAMPLER_PID"
  LOGCAT_PID=""; LOGCAT_FILTER_PID=""; SAMPLER_PID=""
}

finalize_runtime_capture() {
  if [ -n "$LOGCAT_FILTERED_TMP" ] && [ -f "$LOGCAT_FILTERED_TMP" ]; then
    head -n "$MAX_LOG_LINES" "$LOGCAT_FILTERED_TMP" >"$RUN_DIR/runtime/logcat-filtered.txt"
  elif [ ! -f "$RUN_DIR/runtime/logcat-filtered.txt" ]; then
    printf '# logcat capture unavailable\n' >"$RUN_DIR/runtime/logcat-filtered.txt"
  fi
  rm -f "$LOGCAT_FIFO" "$LOGCAT_FILTERED_TMP" 2>/dev/null
  LOGCAT_FIFO=""; LOGCAT_FILTERED_TMP=""
  if [ -n "$SAMPLER_RAW" ] && [ -f "$SAMPLER_RAW" ]; then
    head -n "$MAX_RUNTIME_LINES" "$SAMPLER_RAW" >"$RUN_DIR/runtime/process-paths.tsv"
    rm -f "$SAMPLER_RAW" 2>/dev/null
  else
    : >"$RUN_DIR/runtime/process-paths.tsv"
  fi
  SAMPLER_RAW=""
}

count_data_rows() {
  cdr_file="$1"
  [ -r "$cdr_file" ] || { printf '0\n'; return 0; }
  cdr_n="$(wc -l <"$cdr_file" 2>/dev/null | tr -d '[:space:]')"
  case "$cdr_n" in ''|*[!0-9]*) cdr_n=0;; esac
  [ "$cdr_n" -gt 0 ] 2>/dev/null && cdr_n=$((cdr_n - 1))
  printf '%s\n' "$cdr_n"
}

make_analysis() {
  mkdir -p "$RUN_DIR/analysis" 2>/dev/null
  diff -u "$RUN_DIR/snapshots/tree-before.tsv" "$RUN_DIR/snapshots/tree-after.tsv" 2>/dev/null | head -n 10000 >"$RUN_DIR/analysis/tree.diff"
  diff -u "$RUN_DIR/snapshots/refs-before.txt" "$RUN_DIR/snapshots/refs-after.txt" 2>/dev/null | head -n 8000 >"$RUN_DIR/analysis/theme-refs.diff"
  diff -u "$RUN_DIR/plugins/plugin-files-before.tsv" "$RUN_DIR/plugins/plugin-files-after.tsv" 2>/dev/null | head -n 10000 >"$RUN_DIR/analysis/plugin-files.diff"
  diff -u "$RUN_DIR/plugins/p.l-before-normalised.txt" "$RUN_DIR/plugins/p.l-after-normalised.txt" 2>/dev/null | head -n 8000 >"$RUN_DIR/analysis/p.l.diff"
  {
    printf 'source\tpath\n'
    awk -F '\t' '$1=="MAP" || $1=="FD" {print $1 "\t" $5}' "$RUN_DIR/runtime/process-paths.tsv" 2>/dev/null | grep -E 'app_p_|com\.dofun\.variety|\.jar$|\.apk$|\.dex$|\.odex$|\.vdex$|\.so$'
    grep -aoE '/data/[^[:space:]"}]+app_p_[^[:space:]"}]+' "$RUN_DIR/plugins/p.l-after-normalised.txt" 2>/dev/null | sort -u | sed 's/^/P_L\t/'
  } >"$RUN_DIR/analysis/overlay-candidates.tsv"
  sort -u "$RUN_DIR/analysis/overlay-candidates.tsv" -o "$RUN_DIR/analysis/overlay-candidates.tsv" 2>/dev/null
  capture 'kernel tail' 5 "$RUN_DIR/runtime/kernel-filtered.txt" "dmesg 2>/dev/null | tail -n 2200 | grep -Ei 'avc: denied|dofun|variety|replugin|plugin|theme|sfp_|app_p_|lspd|lsposed|zygisk|xposed|jiagu' | tail -n 1400" >/dev/null 2>&1 || warn 'kernel log unavailable; continuing'

  {
    printf '# LSPosed / ts-theme feasibility evidence summary\n'
    printf 'generated_at=%s\n' "$(now)"
    printf 'framework_roots_found=%s\n' "$(grep -Ec '^### root=' "$RUN_DIR/lsposed/framework.txt" 2>/dev/null || echo 0)"
    printf 'dofun_scope_rows=%s\n' "$(count_data_rows "$RUN_DIR/lsposed/dofun-scope.tsv")"
    printf 'dofun_runtime_injection_markers=%s\n' "$(grep -Eic 'lspd|lsposed|zygisk|xposed|riru|libxposed' "$RUN_DIR/runtime/process-paths.tsv" 2>/dev/null || echo 0)"
    printf 'zygote_injection_markers=%s\n' "$(grep -Eic 'lspd|lsposed|zygisk|xposed|riru|libxposed' "$RUN_DIR/lsposed/zygote-injection.txt" 2>/dev/null || echo 0)"
    printf 'modern_module_markers=%s\n' "$(grep -Ec 'member=META-INF/xposed/java_init.list' "$RUN_DIR/lsposed/module-apk-markers.txt" 2>/dev/null || echo 0)"
    printf 'legacy_module_markers=%s\n' "$(grep -Ec 'member=assets/xposed_init' "$RUN_DIR/lsposed/module-apk-markers.txt" 2>/dev/null || echo 0)"
    printf 'jiagu_markers=%s\n' "$(grep -Eic 'jiagu|Lcom/stub/StubApp;' "$RUN_DIR/static/dofun-apk.txt" 2>/dev/null || echo 0)"
    printf 'replugin_runtime_markers=%s\n' "$(grep -Eic 'replugin|app_p_a|PluginInfo|PluginManager|Unsupported class loader' "$RUN_DIR/runtime/logcat-history-filtered.txt" "$RUN_DIR/runtime/logcat-filtered.txt" 2>/dev/null || echo 0)"
    printf '\n## explicit API/framework-version tokens (no inferred compatibility)\n'
    grep -Eio 'API[ _-]?version[^0-9]{0,8}[0-9]{2,4}|framework[ _-]?version[^0-9]{0,8}[0-9][^[:space:]]*|LSPosedService[^\n]{0,40}version[^\n]{0,40}' "$RUN_DIR/lsposed/framework.txt" "$RUN_DIR/lsposed/logs-filtered.txt" 2>/dev/null | head -n 120
    printf '\n## installed modern-module API declarations\n'
    grep -E '^(minApiVersion|targetApiVersion|staticScope|exceptionMode|autoHotReload)=' "$RUN_DIR/lsposed/module-apk-markers.txt" 2>/dev/null | head -n 240
    printf '\n## LSPosed database query status\n'
    grep -E '^(readonly_query|modules_query_[a-z_]*exit|dofun_scope_query_exit)=' "$RUN_DIR/lsposed/database.txt" 2>/dev/null | head -n 120
    printf '\n## DoFun process / parent / bitness evidence\n'
    grep '^PROC' "$RUN_DIR/runtime/process-paths.tsv" 2>/dev/null | head -n 20
    printf '\n## DoFun scope rows\n'
    cat "$RUN_DIR/lsposed/dofun-scope.tsv" 2>/dev/null | head -n 260
    printf '\n## important boundary\n'
    printf '%s\n' 'This shell diagnostic cannot enumerate post-Jiagu Java classes/methods or prove a stable hook signature. If framework/API, DoFun scope and target-process injection are established, the remaining discriminator is a narrow log-only LSPosed discovery module in com.dofun.variety.'
  } >"$RUN_DIR/analysis/lsposed-feasibility.txt"
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
    ( cd "$EXPORT_ROOT" 2>/dev/null && run_timeout 20 "$PACKAGER_PATH" -q -r "$zipfile" "$(basename "$RUN_DIR")" ) >/dev/null 2>&1; rc=$?
  else
    helper="$STATE_DIR/make-zip.py"; cat >"$helper" <<'PY'
from pathlib import Path
import sys, zipfile
src=Path(sys.argv[1]).resolve(); out=Path(sys.argv[2]).resolve()
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for p in sorted(src.rglob('*')):
        if p.is_file(): z.write(p,p.relative_to(src.parent).as_posix())
PY
    run_timeout 20 "$PACKAGER_PATH" "$helper" "$RUN_DIR" "$zipfile" >/dev/null 2>&1; rc=$?
  fi
  if [ "$rc" -ne 0 ] || [ ! -s "$zipfile" ]; then
    rm -f "$zipfile" 2>/dev/null
    warn 'ZIP creation failed; live folder retained'
    return 1
  fi
  h="$(sha_file "$zipfile")"; printf '%s  %s\n' "$h" "$(basename "$zipfile")" >"${zipfile}.sha256.txt" 2>/dev/null
  log "ZIP_READY=$zipfile"; log "ZIP_SHA256=$h"; return 0
}

cleanup() {
  rc=$?; trap - EXIT INT TERM HUP; stop_bg
  rm -f "$LOGCAT_FIFO" "$LOGCAT_FILTERED_TMP" "$SAMPLER_RAW" "$STATE_DIR"/logcat-history.* "$STATE_DIR"/sqlite-query.* 2>/dev/null
  if [ -n "$WATCHDOG_PID" ]; then stop_pid "$WATCHDOG_PID"; fi
  rm -rf "$STATE_DIR/lock" 2>/dev/null
  if [ -n "$LIVE" ]; then
    if [ "$rc" -eq 0 ]; then
      if [ "$WARNINGS" -gt 0 ]; then log 'FINAL_STATUS=COMPLETED WITH WARNINGS'; else log 'FINAL_STATUS=SUCCESS'; fi
    else
      log "FINAL_STATUS=FAILED rc=$rc"
    fi
  fi
  exit "$rc"
}

acquire_slot() {
  mkdir -p "$STATE_DIR" 2>/dev/null || return 1
  bid="$(boot_id)"; [ -n "$bid" ] || bid=unknown
  if [ -d "$STATE_DIR/lock" ]; then old="$(cat "$STATE_DIR/lock/boot-id" 2>/dev/null)"; [ "$old" = "$bid" ] && return 2; rm -rf "$STATE_DIR/lock" 2>/dev/null; fi
  mkdir "$STATE_DIR/lock" 2>/dev/null || return 2; printf '%s\n' "$bid" >"$STATE_DIR/lock/boot-id"
  last="$(cat "$STATE_DIR/last-boot" 2>/dev/null)"; [ "$last" = "$bid" ] && { rm -rf "$STATE_DIR/lock"; return 2; }
  count="$(tr -d '\r\n' <"$STATE_DIR/run-count" 2>/dev/null)"; case "$count" in ''|*[!0-9]*) count=0;; esac
  [ "$count" -ge "$MAX_RUNS" ] 2>/dev/null && { rm -rf "$STATE_DIR/lock"; return 3; }
  RUN_NO=$((count + 1)); BOOT_ID="$bid"; return 0
}

download_ready() {
  [ -d /storage/emulated/0/Download ] && [ -w /storage/emulated/0/Download ] || return 1
  mkdir -p "$EXPORT_ROOT" 2>/dev/null
}

wait_download() {
  wd_i=0
  while [ "$wd_i" -lt "$STORAGE_WAIT_SECONDS" ]; do
    download_ready && return 0
    wd_i=$((wd_i + 1)); sleep 1
  done
  return 1
}

run_worker() {
  [ "$(id -u 2>/dev/null)" = 0 ] || { printf 'FAILED: worker requires UID 0\n' >&2; return 1; }
  acquire_slot; slot=$?; case "$slot" in 0) ;; 2|3) return 0;; *) return 1;; esac
  trap cleanup EXIT; trap 'exit 130' INT TERM HUP
  clean_stale_runtime_temp
  download_ready || return 0
  stamp="$(date +%Y%m%d-%H%M%S 2>/dev/null || echo unknown)"; RUN_DIR="$EXPORT_ROOT/live-run${RUN_NO}-$stamp"
  mkdir -p "$RUN_DIR/runtime" "$RUN_DIR/snapshots" "$RUN_DIR/analysis" "$RUN_DIR/plugins" "$RUN_DIR/packages" "$RUN_DIR/mounts" "$RUN_DIR/root" "$RUN_DIR/lsposed" "$RUN_DIR/static" || return 1
  LIVE="$RUN_DIR/LIVE.txt"; : >"$LIVE"
  cat >"$RUN_DIR/SHARING_NOTICE.txt" <<'NOTICE'
This diagnostic is local-only and never uploads data automatically.
The shareable output intentionally contains targeted DoFun package/process paths, RePlugin registration metadata, hashes, filtered activation logs, Magisk module names, LSPosed framework/module/scope metadata, and filtered LSPosed logs needed to assess a narrow DoFun hook.
It does not export the raw LSPosed configuration database or unrestricted LSPosed/logcat buffers, and it intentionally excludes LSPosed per-module config values.
Review the ZIP contents before sharing them outside your own diagnostic workflow.
NOTICE
  printf '%s\n' "$RUN_NO" >"$STATE_DIR/run-count" || return 1; printf '%s\n' "$BOOT_ID" >"$STATE_DIR/last-boot" || return 1
  ( sleep "$HARD_SECONDS"; kill -TERM $$ 2>/dev/null ) & WATCHDOG_PID=$!
  log "TS18 activation/LSPosed diagnostic v$SCRIPT_VERSION run=$RUN_NO/$MAX_RUNS hard_cap=${HARD_SECONDS}s"
  log 'READ_ONLY: no DoFun, LSPosed database, p.l, packages, mounts or SELinux state will be changed.'
  log 'PLAYBOOK_NOW: apply several known-working themes, then TS18 Dashboard Theme, wait for disappearance/fallback, then return to a working theme.'

  capture_environment
  capture_magisk_state
  capture_lsposed_state
  capture_zygotes
  package_state
  capture_dofun_static
  capture_plugins before
  inspect_archive_markers before
  snapshot_tree "$RUN_DIR/snapshots/tree-before.tsv"
  capture_theme_refs "$RUN_DIR/snapshots/refs-before.txt"
  capture_logcat_history

  log "Runtime capture active for ${RUNTIME_SECONDS}s. Perform the theme-selection sequence now."
  start_logcat; start_sampler; sleep "$RUNTIME_SECONDS"; stop_bg; finalize_runtime_capture

  log 'Runtime window ended; collecting after-state.'
  snapshot_tree "$RUN_DIR/snapshots/tree-after.tsv"
  capture_theme_refs "$RUN_DIR/snapshots/refs-after.txt"
  capture_plugins after
  inspect_archive_markers after
  capture_lsposed_logs
  make_analysis
  {
    printf 'script_version=%s\nrun=%s/%s\nboot_id=%s\nhard_limit_seconds=%s\nruntime_seconds=%s\nsampler_interval_seconds=%s\nsampler_samples=%s\nwarnings=%s\n' "$SCRIPT_VERSION" "$RUN_NO" "$MAX_RUNS" "$BOOT_ID" "$HARD_SECONDS" "$RUNTIME_SECONDS" "$SAMPLER_INTERVAL_SECONDS" "$SAMPLER_SAMPLES" "$WARNINGS"
    printf 'interpretation=This pass captures both DoFun/RePlugin activation evidence and the installed Magisk/Zygisk/LSPosed capability/scope/injection envelope for a possible narrow ts-theme adapter.\n'
    printf 'lsposed_boundary=Shell evidence can establish framework/API/scope/ABI/process injection, but not post-Jiagu Java class/method signatures; see analysis/lsposed-feasibility.txt.\n'
    printf 'overlay_note=overlay-candidates.tsv is evidence only; do not mount every listed path.\n'
    printf 'sharing_note=See SHARING_NOTICE.txt before sharing the archive.\n'
  } >"$RUN_DIR/SUMMARY.txt"
  package_zip
  zip_rc=$?
  [ "$zip_rc" -eq 0 ] || warn 'ZIP was not produced; the complete live-run folder remains available.'
  log 'Capture complete. Upload the run ZIP from Download; if ZIP packaging failed, upload the live-run folder.'
  return 0
}

service_entry() {
  [ "$(id -u 2>/dev/null)" = 0 ] || return 0
  c="$(tr -d '\r\n' <"$STATE_DIR/run-count" 2>/dev/null)"; case "$c" in ''|*[!0-9]*) c=0;; esac
  [ "$c" -ge "$MAX_RUNS" ] 2>/dev/null && return 0
  ( wait_download && exec /system/bin/sh "$SERVICE_PATH" --worker ) >/dev/null 2>&1 &
}

usage() { printf 'TS18 activation/LSPosed diagnostic v%s\n  sh %s --install\n  sh %s --status\n' "$SCRIPT_VERSION" "$0" "$0"; }
case "${1:-}" in
  --install) install_self; exit $?;;
  --status) show_status; exit $?;;
  --worker) run_worker; exit $?;;
  -h|--help) usage; exit 0;;
  '') service_entry; exit 0;;
  *) usage >&2; exit 1;;
esac
