#!/data/data/com.termux/files/usr/bin/bash
# Explicit, bounded NavRadio service-start qualification.
# This performs exactly one service-start mutation and never issues Play or force-stops the source.

set -u

if [[ "${1:-}" != "--qualify-navradio-service-start" ]]; then
  cat >&2 <<'EOF'
Usage: qualify-navradio-service-start.sh --qualify-navradio-service-start

This is NOT a read-only collector. It performs exactly one explicit NavRadio background service
start using the service contract discovered from the installed package. It does not issue Play,
change routing intentionally, force-stop NavRadio, or restore its prior process state.
EOF
  exit 2
fi

PKG="com.navimods.radio"
ACTION="androidx.media3.session.MediaSessionService"
OUT_BASE="${TS18_EXPORT_ROOT:-/storage/emulated/0/Download/ts-theme}"
STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf 'unknown')"
OUT="$OUT_BASE/navradio-service-qualification-$STAMP"
STATUS="$OUT/STATUS.tsv"
CAP_TIMEOUT=8
umask 077
mkdir -p -- "$OUT" || { printf 'FAILED: cannot create %s\n' "$OUT" >&2; exit 1; }
printf 'surface\tstatus\tdetail\n' >"$STATUS"

record() { printf '%s\t%s\t%s\n' "$1" "$2" "$3" >>"$STATUS"; }

capture() {
  local name="$1"; shift
  local dst="$OUT/$name" rc
  mkdir -p -- "$(dirname -- "$dst")"
  if timeout -k 2 "$CAP_TIMEOUT" "$@" >"$dst" 2>&1; then
    record "$name" PASS captured
  else
    rc=$?
    printf '\n[qualifier] exit_status=%s\n' "$rc" >>"$dst"
    record "$name" UNVERIFIED "command failed/timed out rc=$rc"
  fi
}

capture_sh() { local name="$1"; shift; capture "$name" sh -c "$*"; }

root_available() {
  command -v su >/dev/null 2>&1 || return 1
  timeout -k 1 3 su -c 'id -u' 2>/dev/null | grep -qx '0'
}

snapshot() {
  local phase="$1"
  capture_sh "$phase/pid.txt" "pidof '$PKG' 2>&1 || true"
  capture "$phase/services.txt" dumpsys activity services "$PKG"
  capture "$phase/media-session.txt" dumpsys media_session
  capture_sh "$phase/navradio-session.txt" \
    "dumpsys media_session 2>&1 | grep -Ei -C 10 'com\.navimods\.radio|state=|actions=|metadata|token' || true"
  capture_sh "$phase/audio-focus-route.txt" \
    "dumpsys audio 2>&1 | grep -Ei -C 5 'focus|route|device|com\.navimods\.radio|radio' || true"
  capture_sh "$phase/resumed-task.txt" \
    "dumpsys activity activities 2>&1 | grep -Ei -m 12 'mResumedActivity|topResumedActivity|ResumedActivity|mFocusedApp|com\.navimods\.radio|com\.cbkii\.ts18launcher' || true"
}

capture identity/date.txt date -Ins
capture identity/current-user.txt sh -c \
  'cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true'
capture identity/package.txt dumpsys package "$PKG"
capture_sh identity/path.txt "pm path '$PKG' 2>&1 || true"
capture_sh identity/apk-sha256.txt \
  "pm path '$PKG' 2>/dev/null | sed 's/^package://' | while IFS= read -r p; do [ -r \"\$p\" ] && sha256sum \"\$p\" || printf 'UNREADABLE %s\\n' \"\$p\"; done"
capture_sh identity/service-query.txt \
  "cmd package query-intent-services --brief -a '$ACTION' 2>&1 || pm query-services -a '$ACTION' 2>&1 || true"

USER_ID="$(cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true)"
case "$USER_ID" in
  ''|*[!0-9]*)
    record qualification BLOCKED "current Android user unresolved"
    printf 'BLOCKED: current Android user unresolved\n' >"$OUT/SUMMARY.txt"
    printf 'Output: %s\n' "$OUT"
    exit 0
    ;;
esac

SERVICE="$(cmd package query-intent-services --brief -a "$ACTION" 2>/dev/null \
  | grep -E '^com\.navimods\.radio/' | head -n 1 || true)"
if [[ -z "$SERVICE" ]]; then
  record qualification BLOCKED "installed NavRadio exposes no discoverable MediaSessionService"
  printf 'BLOCKED: no installed %s service component discovered for %s\n' "$ACTION" "$PKG" >"$OUT/SUMMARY.txt"
  printf 'No service start was attempted.\n' >>"$OUT/SUMMARY.txt"
  printf 'Output: %s\n' "$OUT"
  exit 0
fi
printf '%s\n' "$SERVICE" >"$OUT/identity/discovered-service.txt"
record identity/discovered-service.txt PASS "$SERVICE"

snapshot before
BEFORE_PID="$(cat "$OUT/before/pid.txt" 2>/dev/null || true)"
BEFORE_SESSION=no
if grep -q "$PKG" "$OUT/before/navradio-session.txt" 2>/dev/null; then BEFORE_SESSION=yes; fi

START_OK=no
mkdir -p -- "$OUT/mutation"
if ! root_available; then
  printf 'BLOCKED: Magisk/root unavailable or denied; explicit root-first qualification not run\n' \
    >"$OUT/mutation/start.txt"
  record mutation/start.txt BLOCKED "root unavailable/denied"
else
  printf 'Controlled mutation: one background service start, no Play command.\n' >"$OUT/mutation/NOTICE.txt"
  if timeout -k 2 5 su -c \
      "am start-foreground-service --user '$USER_ID' -a '$ACTION' -n '$SERVICE'" \
      >"$OUT/mutation/start.txt" 2>&1; then
    START_OK=yes
    record mutation/start.txt PASS "service command accepted"
  else
    rc=$?
    printf '\n[qualifier] start_exit_status=%s\n' "$rc" >>"$OUT/mutation/start.txt"
    record mutation/start.txt FAIL "service command rejected/timed out rc=$rc"
  fi
fi

sleep 0.5
snapshot after-0500ms
sleep 1
snapshot after-1500ms
sleep 1.5
snapshot after-3000ms

AFTER_PID="$(cat "$OUT/after-3000ms/pid.txt" 2>/dev/null || true)"
AFTER_SESSION=no
if grep -q "$PKG" "$OUT/after-3000ms/navradio-session.txt" 2>/dev/null; then AFTER_SESSION=yes; fi
PROCESS_CREATED=no
if [[ -z "$BEFORE_PID" && -n "$AFTER_PID" ]]; then PROCESS_CREATED=yes; fi
ROUTE_TEXT_CHANGED=no
if ! cmp -s "$OUT/before/audio-focus-route.txt" "$OUT/after-3000ms/audio-focus-route.txt" 2>/dev/null; then
  ROUTE_TEXT_CHANGED=yes
fi

cat >"$OUT/SUMMARY.txt" <<EOF
NavRadio installed-service qualification
========================================
service=$SERVICE
android_user=$USER_ID
service_command_accepted=$START_OK
process_created_after_start=$PROCESS_CREATED
session_present_before=$BEFORE_SESSION
session_present_after=$AFTER_SESSION
session_play_capable=UNVERIFIED (inspect captured state/actions; do not infer from process existence)
audio_focus_or_route_text_changed=$ROUTE_TEXT_CHANGED
play_command_issued=no
source_force_stopped=no
human_audible_observation=REQUIRED

Interpretation
--------------
A successful service command, process creation or MediaSession appearance does not by itself prove
that passive HOME warm-up is safe. Compare the before/after task and audio-focus/route captures and
record whether radio became audible or interrupted another source. Only a non-disruptive installed-
build result should be used later to reconsider NavRadio passiveWarmSafe.
EOF
record qualification UNVERIFIED "human audible/non-disruption observation still required"
printf 'Output: %s\n' "$OUT"
