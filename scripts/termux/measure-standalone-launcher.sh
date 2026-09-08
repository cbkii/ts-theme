#!/data/data/com.termux/files/usr/bin/bash
# Read-only exact-device snapshot for standalone TS18 launcher qualification.

PACKAGE="com.cbkii.ts18launcher"
EXPORT_ROOT="/storage/emulated/0/Download/TS18-launcher-diagnostics"
STATE_ROOT="$HOME/.local/state/ts18-launcher-diagnostics"
TIMEOUT_SECONDS=12
WARNINGS=0

log() {
  printf '%s %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*"
}

warn() {
  WARNINGS=$((WARNINGS + 1))
  log "WARNING: $*"
}

capture_root() {
  local label="$1" outfile="$2" command_text="$3"
  {
    printf '# %s\n' "$label"
    printf '# captured_at=%s\n' "$(date -Iseconds 2>/dev/null || date)"
    timeout -k 2 "$TIMEOUT_SECONDS" su -c "$command_text"
    printf '# exit_status=%s\n' "$?"
  } >"$outfile" 2>&1
}

command -v timeout >/dev/null 2>&1 || {
  log "STOP: timeout is missing; pkg install coreutils"
  exit 2
}
command -v su >/dev/null 2>&1 || {
  log "STOP: Magisk su is unavailable"
  exit 2
}
[[ "$(timeout -k 2 8 su -c 'id -u' 2>/dev/null | head -n 1)" == "0" ]] || {
  log "STOP: su did not provide UID 0"
  exit 2
}

mkdir -p -- "$STATE_ROOT" "$EXPORT_ROOT" || {
  log "STOP: cannot create state/export directories"
  exit 2
}
work="$(mktemp -d "$STATE_ROOT/run.XXXXXX")" || exit 1
stamp="$(date '+%Y%m%d-%H%M%S')"
run="$work/TS18-launcher-diagnostics-$stamp"
mkdir -p "$run" || exit 1

capture_root "identity and Android build" "$run/identity.txt" \
  'id; cat /proc/self/attr/current 2>/dev/null; getenforce 2>/dev/null; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.build.fingerprint; readlink /proc/self/ns/mnt'

capture_root "package state" "$run/package.txt" \
  "dumpsys package '$PACKAGE'"

capture_root "current HOME" "$run/home.txt" \
  'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME; dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -n 20'

capture_root "process state" "$run/process.txt" \
  "pid=\$(pidof '$PACKAGE' 2>/dev/null | awk '{print \$1}'); echo pid=\$pid; if [ -n \"\$pid\" ]; then ps -A -o USER,PID,PPID,VSZ,RSS,STAT,NAME,ARGS | grep -E \"(^|[[:space:]])\$pid([[:space:]]|$)\"; echo mntns=\$(readlink /proc/\$pid/ns/mnt 2>/dev/null); cat /proc/\$pid/status; fi"

capture_root "memory" "$run/meminfo.txt" \
  "dumpsys meminfo '$PACKAGE'"

capture_root "frame statistics" "$run/gfxinfo.txt" \
  "dumpsys gfxinfo '$PACKAGE' framestats"

capture_root "CPU snapshot" "$run/cpu.txt" \
  "top -b -n 1 -m 30 2>/dev/null || top -n 1 2>/dev/null"

capture_root "WebView provider" "$run/webview.txt" \
  'dumpsys webviewupdate 2>/dev/null || cmd webviewupdate getCurrentWebViewPackage 2>/dev/null'

capture_root "location providers" "$run/location.txt" \
  'dumpsys location | head -n 1200'

capture_root "media-session surface" "$run/media.txt" \
  'dumpsys media_session | head -n 1600'

capture_root "Topway recovery host state" "$run/dofun.txt" \
  'pm path com.dofun.variety 2>/dev/null; pidof com.dofun.variety 2>/dev/null; dumpsys package com.dofun.variety | grep -E "versionName=|versionCode=|enabled=" | head -n 40'

cat >"$run/SUMMARY.txt" <<EOF
TS18 standalone launcher diagnostic
captured_at=$(date -Iseconds 2>/dev/null || date)
package=$PACKAGE
mode=read-only
warnings=$WARNINGS

This capture does not force-stop/start packages, clear graphics counters, change
HOME, modify notification access, change SELinux, or write protected app data.
EOF

archive="$EXPORT_ROOT/TS18-launcher-diagnostics-$stamp.zip"
if command -v zip >/dev/null 2>&1; then
  if (cd "$run" && timeout -k 2 30 zip -q -r "$archive" .) && [[ -s "$archive" ]]; then
    sha256sum "$archive" >"$archive.sha256.txt"
    log "SUCCESS: $archive"
    rm -rf -- "$work"
    exit 0
  fi
  warn "ZIP packaging failed"
fi

fallback="$EXPORT_ROOT/TS18-launcher-diagnostics-$stamp"
if cp -a "$run" "$fallback"; then
  log "COMPLETED WITH WARNINGS: unpacked export $fallback"
  rm -rf -- "$work"
  exit 0
fi

log "FAILED: could not export diagnostics; private work preserved at $work"
exit 1
