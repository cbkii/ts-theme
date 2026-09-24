#!/data/data/com.termux/files/usr/bin/bash
# Read-only TS18 fast-media readiness collector.
# No playback, package, task, HOME, audio-route, SELinux or OEM state is changed.

set -u

OUT_BASE="${TS18_EXPORT_ROOT:-/storage/emulated/0/Download/ts-theme}"
LABEL="baseline"
CAP_TIMEOUT=8
WARNINGS=0

usage() {
  cat <<'EOF'
Usage: collect-fast-media-evidence.sh [--label NAME]

Read-only baseline collector. NAME may contain letters, numbers, dot, underscore and dash only.
EOF
}

while (($#)); do
  case "$1" in
    --label)
      shift
      (($#)) || { usage >&2; exit 2; }
      LABEL="$1"
      ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

case "$LABEL" in
  ''|*[!A-Za-z0-9._-]*) printf 'Invalid label: %s\n' "$LABEL" >&2; exit 2 ;;
esac

STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf 'unknown')"
OUT="$OUT_BASE/media-readiness-$LABEL-$STAMP"
PRIVATE="${TMPDIR:-$HOME/.cache}/ts-theme-media-readiness-$$"
STATUS="$OUT/STATUS.tsv"
umask 077

mkdir -p -- "$OUT_BASE" "$OUT" "$PRIVATE" || {
  printf 'FAILED: cannot create output/private state\n' >&2
  exit 1
}
chmod 700 "$PRIVATE" 2>/dev/null || true

log() {
  printf '[%s] %s\n' "$(date +%H:%M:%S 2>/dev/null || printf '%s' '--:--:--')" "$*" \
    | tee -a "$OUT/run.log"
}

record() {
  printf '%s\t%s\t%s\n' "$1" "$2" "$3" >>"$STATUS"
}

capture() {
  local name="$1"
  shift
  local dst="$OUT/$name"
  local rc
  mkdir -p -- "$(dirname -- "$dst")"
  if timeout -k 2 "$CAP_TIMEOUT" "$@" >"$dst" 2>&1; then
    record "$name" PASS "captured"
    return 0
  fi
  rc=$?
  printf '\n[collector] exit_status=%s\n' "$rc" >>"$dst"
  record "$name" UNVERIFIED "command failed/timed out rc=$rc"
  WARNINGS=$((WARNINGS + 1))
  return 0
}

capture_sh() {
  local name="$1"
  shift
  capture "$name" sh -c "$*"
}

capture_root() {
  local name="$1"
  shift
  local dst="$OUT/$name"
  local command="$*"
  local rc
  mkdir -p -- "$(dirname -- "$dst")"
  if ! command -v su >/dev/null 2>&1; then
    printf 'BLOCKED: Magisk su not found\n' >"$dst"
    record "$name" BLOCKED "su not found"
    return 0
  fi
  if timeout -k 2 "$CAP_TIMEOUT" su -c "$command" >"$dst" 2>&1; then
    record "$name" PASS "root read-only capture"
    return 0
  fi
  rc=$?
  printf '\n[collector] root_exit_status=%s\n' "$rc" >>"$dst"
  record "$name" BLOCKED "root denied/timed out rc=$rc"
  WARNINGS=$((WARNINGS + 1))
  return 0
}

package_capture() {
  local pkg="$1"
  capture "packages/$pkg-package.txt" dumpsys package "$pkg"
  capture_sh "packages/$pkg-path.txt" "pm path '$pkg' 2>&1 || true"
  capture_sh "packages/$pkg-hash-readable.txt" \
    "pm path '$pkg' 2>/dev/null | sed 's/^package://' | while IFS= read -r p; do test -r \"\$p\" && sha256sum \"\$p\" || printf 'UNREADABLE %s\\n' \"\$p\"; done"
  capture_root "packages/$pkg-hash-root.txt" \
    "pm path '$pkg' 2>/dev/null | sed 's/^package://' | while IFS= read -r p; do sha256sum \"\$p\" 2>/dev/null || true; done"
  capture_sh "runtime/$pkg-pid.txt" "pidof '$pkg' 2>&1 || true"
  capture_sh "runtime/$pkg-services.txt" "dumpsys activity services '$pkg' 2>&1 || true"
}

cleanup() {
  local rc=$?
  trap - EXIT INT TERM HUP
  rm -rf -- "$PRIVATE"
  printf '\nOutput: %s\n' "$OUT" | tee -a "$OUT/run.log"
  if ((WARNINGS)); then
    printf 'Collector completed with %s blocked/unverified capture(s).\n' "$WARNINGS" \
      | tee -a "$OUT/run.log"
  else
    printf 'Collector completed without capture warnings.\n' | tee -a "$OUT/run.log"
  fi
  exit "$rc"
}
trap cleanup EXIT INT TERM HUP

printf 'surface\tstatus\tdetail\n' >"$STATUS"
log "TS18 fast-media readiness collector [$LABEL]"
log "READ ONLY: no media control or package/task mutation is performed"

capture identity/date.txt date -Ins
capture identity/id.txt id
capture identity/id-z.txt id -Z
capture identity/uptime.txt cat /proc/uptime
capture identity/current-user.txt sh -c \
  'cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true'
capture_root identity/root-id.txt 'id; id -Z 2>/dev/null || true; printf "user="; cmd activity get-current-user 2>/dev/null || true'

for pkg in com.cbkii.ts18launcher com.tw.media com.tw.radio com.navimods.radio; do
  package_capture "$pkg"
done

capture packages/media-browser-services.txt sh -c \
  'cmd package query-intent-services -a android.media.browse.MediaBrowserService 2>&1 || pm query-services -a android.media.browse.MediaBrowserService 2>&1 || true'
capture packages/media3-session-services.txt sh -c \
  'cmd package query-intent-services -a androidx.media3.session.MediaSessionService 2>&1 || pm query-services -a androidx.media3.session.MediaSessionService 2>&1 || true'

capture media/media-session.txt dumpsys media_session
capture_sh media/selected-sessions.txt \
  "dumpsys media_session 2>&1 | grep -Ei -C 8 'com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|state=|actions=|metadata' || true"
capture media/audio.txt dumpsys audio
capture_sh media/audio-focus-route.txt \
  "dumpsys audio 2>&1 | grep -Ei -C 5 'focus|route|device|stream|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio' || true"

capture_sh runtime/resumed-task.txt \
  "dumpsys activity activities 2>&1 | grep -Ei -m 12 'mResumedActivity|topResumedActivity|ResumedActivity|com\.cbkii\.ts18launcher|com\.tw\.radio|com\.navimods\.radio|com\.tw\.media' || true"
capture_sh runtime/notification-listener.txt \
  "dumpsys notification 2>&1 | grep -Ei -C 4 'com\.cbkii\.ts18launcher|MediaListenerService|notification listener' || true"
capture_root runtime/root-process-contexts.txt \
  "ps -AZ 2>/dev/null | grep -E 'com\.cbkii\.ts18launcher|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|com\.tw\.service|com\.tw\.core' || true"
capture_root runtime/launcher-prefs.txt \
  "u=\$(cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null); case \"\$u\" in ''|*[!0-9]*) exit 1;; esac; cat /data/user/\"\$u\"/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>/dev/null || true"

capture_sh media/launcher-trace.txt \
  "logcat -d -t 1200 -s TS18MediaTrace:D '*:S' 2>&1 || true"
capture_root media/launcher-trace-root.txt \
  "logcat -d -t 1200 -s TS18MediaTrace:D '*:S' 2>&1 || true"

capture storage/mount.txt mount
capture storage/volumes.txt sh -c 'sm list-volumes all 2>&1 || true'
capture storage/dumpsys-mount.txt sh -c 'dumpsys mount 2>&1 || true'
capture_sh storage/provider-state.txt \
  "dumpsys package com.android.documentsui 2>&1 | grep -Ei -C 3 'enabled=|stopped=|DocumentsProvider' || true"

capture_sh topway/processes.txt \
  "ps -A 2>&1 | grep -E 'com\.tw\.service|com\.tw\.service\.xt|com\.tw\.core|com\.tw\.radio|com\.navimods\.radio|com\.tw\.media' || true"
for pkg in com.tw.service com.tw.service.xt com.tw.core; do
  capture_sh "topway/$pkg-services.txt" "dumpsys activity services '$pkg' 2>&1 || true"
done

capture overlay/appops.txt sh -c \
  'cmd appops get com.cbkii.ts18launcher android:system_alert_window 2>&1 || appops get com.cbkii.ts18launcher SYSTEM_ALERT_WINDOW 2>&1 || true'

cat >"$OUT/PLAYBOOK.txt" <<'EOF'
Fast-media physical qualification
=================================

This collector is read-only. Playback-state acknowledgement is not proof of audible sound.

1. Baseline: run this collector before touching Radio/Music.
2. Cold/warm one-tap: return HOME, tap Play exactly once, then run another labelled capture.
   Record physical audible onset separately using a monotonic stopwatch/video if latency matters.
3. Launcher/player/listener lifecycle: repeat after ordinary launcher restart, player-process death
   and notification-listener reconnect. Do not substitute force-stop for every lifecycle case.
4. Root fallback: deny/unavailable Magisk for the launcher, repeat one Play attempt and capture.
5. Opposite source: start source A, request source B once, confirm A continues until B really starts.
6. Auxio removable media: repeat with USB present, late-mounted, ejected and unavailable. Do not
   clear app data, queue, library or SAF grants; root-visible storage is not proof of Auxio access.
7. NavRadio service-start qualification uses the separate explicitly mutating script:
     qualify-navradio-service-start.sh --qualify-navradio-service-start
8. Stock TW Radio comparison uses the separate read-only collector before and after manual open.
9. ACC/reboot uses collect-acc-media-lifecycle.sh during the physical power transition.

Masked Activity fallback remains OFF. Overlay app-op output here is preflight evidence only; it
must not be treated as proof of safe draw/order/input/reverse-camera/call behaviour.
EOF

log "capture complete"
