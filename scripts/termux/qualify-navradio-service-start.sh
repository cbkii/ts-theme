#!/data/data/com.termux/files/usr/bin/bash
# Explicitly mutating NavRadio+ service-start qualification.
# Performs exactly one discovered service start; it never issues Play, changes routing or force-stops.

set -u

if [[ "${1:-}" != "--qualify-navradio-service-start" || $# -ne 1 ]]; then
  cat >&2 <<'EOF'
Usage: qualify-navradio-service-start.sh --qualify-navradio-service-start

This is NOT a read-only collector. It performs exactly one bounded start of the installed
com.navimods.radio MediaSessionService discovered on this unit, after capturing a baseline.
It does not issue Play, stop the service, force-stop the package or change audio routing.
EOF
  exit 2
fi

PKG="com.navimods.radio"
ACTION_MEDIA3="androidx.media3.session.MediaSessionService"
OUT_BASE="/storage/emulated/0/Download/ts-theme"
STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf unknown)"
OUT="$OUT_BASE/navradio-service-start-$STAMP"
CAP_TIMEOUT=8
umask 077
mkdir -p -- "$OUT" || exit 1

run_capture() {
  local name="$1"
  shift
  timeout -k 2 "$CAP_TIMEOUT" "$@" >"$OUT/$name" 2>&1 || printf '\nexit=%s\n' "$?" >>"$OUT/$name"
}

run_capture_sh() {
  local name="$1"
  shift
  run_capture "$name" sh -c "$*"
}

USER_ID="$(cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true)"
USER_ID="$(printf '%s' "$USER_ID" | tr -cd '0-9')"
if [[ -z "$USER_ID" ]]; then
  printf 'BLOCKED: could not resolve current Android user\n' | tee "$OUT/SUMMARY.txt"
  exit 3
fi

SERVICES="$(cmd package query-intent-services --brief --components --user "$USER_ID" \
  -a "$ACTION_MEDIA3" -p "$PKG" 2>/dev/null || true)"
if [[ -z "$SERVICES" ]]; then
  SERVICES="$(pm query-services --brief --components --user "$USER_ID" \
    -a "$ACTION_MEDIA3" -p "$PKG" 2>/dev/null || true)"
fi
printf '%s\n' "$SERVICES" >"$OUT/discovered-services.txt"
mapfile -t COMPONENTS < <(printf '%s\n' "$SERVICES" | grep -E '^com\.navimods\.radio/[A-Za-z0-9_.$]+$' | sort -u)
if (( ${#COMPONENTS[@]} != 1 )); then
  printf 'BLOCKED: expected exactly one installed NavRadio MediaSessionService; found %s\n' \
    "${#COMPONENTS[@]}" | tee "$OUT/SUMMARY.txt"
  printf 'No service was started.\n' >>"$OUT/SUMMARY.txt"
  exit 4
fi
COMPONENT="${COMPONENTS[0]}"

snapshot() {
  local phase="$1"
  run_capture "$phase-package.txt" dumpsys package "$PKG"
  run_capture_sh "$phase-path.txt" "pm path '$PKG' 2>&1 || true"
  run_capture_sh "$phase-pid.txt" "pidof '$PKG' 2>&1 || true"
  run_capture_sh "$phase-services.txt" "dumpsys activity services '$PKG' 2>&1 || true"
  run_capture "$phase-media-session.txt" dumpsys media_session
  run_capture_sh "$phase-audio.txt" \
    "dumpsys audio 2>&1 | grep -Ei -C 3 'focus|AudioFocus|route|device|com\.navimods\.radio' | tail -n 500 || true"
  run_capture_sh "$phase-task.txt" \
    "dumpsys activity activities 2>&1 | grep -Ei -m 50 'mResumedActivity|topResumedActivity|ResumedActivity|com\.navimods\.radio|com\.cbkii\.ts18launcher' || true"
}

snapshot before
printf 'user=%s\ncomponent=%s\naction=%s\n' "$USER_ID" "$COMPONENT" "$ACTION_MEDIA3" >"$OUT/target.txt"

START_ROUTE="failed"
START_RC=1
if command -v su >/dev/null 2>&1; then
  if timeout -k 2 4 su -c \
      "am start-foreground-service --user $USER_ID -a $ACTION_MEDIA3 -n $COMPONENT" \
      >"$OUT/start-root.txt" 2>&1; then
    START_ROUTE="root"
    START_RC=0
  else
    START_RC=$?
  fi
else
  printf 'BLOCKED: su not found\n' >"$OUT/start-root.txt"
fi

if [[ "$START_ROUTE" == "failed" ]]; then
  if timeout -k 2 4 am start-foreground-service --user "$USER_ID" \
      -a "$ACTION_MEDIA3" -n "$COMPONENT" >"$OUT/start-normal.txt" 2>&1; then
    START_ROUTE="normal-termux-caller"
    START_RC=0
  else
    START_RC=$?
  fi
fi

sleep 0.5
snapshot after-0500ms
sleep 1
snapshot after-1500ms
sleep 2
snapshot after-3500ms

PID_AFTER="$(cat "$OUT/after-3500ms-pid.txt" 2>/dev/null | tr '\n' ' ' | sed 's/[[:space:]]*$//')"
if grep -q "$PKG" "$OUT/after-3500ms-media-session.txt" 2>/dev/null; then
  SESSION_OBSERVED="yes"
else
  SESSION_OBSERVED="no"
fi
if grep -Ei 'mResumedActivity|topResumedActivity|ResumedActivity' "$OUT/after-3500ms-task.txt" 2>/dev/null \
    | grep -q "$PKG"; then
  FOREGROUND_ACTIVITY="yes"
else
  FOREGROUND_ACTIVITY="no"
fi

cat >"$OUT/SUMMARY.txt" <<EOF
NavRadio service-start qualification
====================================
android_user=$USER_ID
component=$COMPONENT
service_action=$ACTION_MEDIA3
start_route=$START_ROUTE
start_command_exit=$START_RC
process_after_3_5s=${PID_AFTER:-none}
media_session_observed=$SESSION_OBSERVED
play_capable=UNVERIFIED_REVIEW_CAPTURED_ACTIONS
foreground_navradio_activity_observed=$FOREGROUND_ACTIVITY
audio_or_route_change=UNVERIFIED_REVIEW_BEFORE_AFTER_AUDIO
audible_observation=REQUIRED_PHYSICAL_OBSERVATION

Interpretation boundary
-----------------------
A successful service command, PID or MediaSession is not by itself proof that passive warm-up is
safe or that one-tap audible playback is ready. Compare the before/after task, session and audio
captures and record whether the physical radio/audio source changed. The normal fallback above is
issued by the Termux caller and does not prove launcher-UID acceptance; the launcher runtime trace
must be used for that boundary.
EOF

printf 'Output: %s\n' "$OUT"
