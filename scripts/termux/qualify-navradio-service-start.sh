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
STATUS="$OUT/STATUS.tsv"
CAP_TIMEOUT=8
TERMUX_BIN="${PREFIX:-/data/data/com.termux/files/usr}/bin"
ANDROID_ROOT_PATH="/system/bin:/system/xbin:/vendor/bin:/product/bin:/apex/com.android.runtime/bin"
export PATH="$TERMUX_BIN:$ANDROID_ROOT_PATH"
umask 077
mkdir -p -- "$OUT" || exit 1
printf 'surface\tstatus\tdetail\n' >"$STATUS"

record() { printf '%s\t%s\t%s\n' "$1" "$2" "$3" >>"$STATUS"; }

run_capture() {
  local name="$1"
  shift
  local rc
  if timeout -k 2 "$CAP_TIMEOUT" "$@" >"$OUT/$name" 2>&1; then
    record "$name" PASS captured
  else
    rc=$?
    printf '\nexit=%s\n' "$rc" >>"$OUT/$name"
    record "$name" UNVERIFIED "capture failed/timed out rc=$rc"
  fi
}

run_capture_sh() {
  local name="$1"
  shift
  run_capture "$name" env PATH="$PATH" sh -c "$*"
}

root_available() {
  command -v su >/dev/null 2>&1 || return 1
  timeout -k 1 3 su -c "export PATH='$ANDROID_ROOT_PATH'; test \"\$(id -u)\" = 0" >/dev/null 2>&1
}

resolve_current_user() {
  local value
  value="$(cmd activity get-current-user 2>/dev/null || true)"
  value="${value//$'\r'/}"
  if [[ "$value" =~ ^[[:space:]]*([0-9]+)[[:space:]]*$ ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  value="$(am get-current-user 2>/dev/null || true)"
  value="${value//$'\r'/}"
  if [[ "$value" =~ ^[[:space:]]*([0-9]+)[[:space:]]*$ ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  return 1
}

USER_ID="$(resolve_current_user || true)"
if [[ -z "$USER_ID" ]]; then
  printf 'BLOCKED: could not resolve current Android user\n' | tee "$OUT/SUMMARY.txt"
  record service-start BLOCKED "current Android user unresolved"
  exit 3
fi

# Android 10/API29 PackageManagerShellCommand uses query-services; newer builds may also expose
# aliases through pm. Capture each attempt so a missing command is not mistaken for no service.
SERVICES=""
DISCOVERY_ROUTE=""
if SERVICES="$(cmd package query-services --brief --components --user "$USER_ID" \
    -a "$ACTION_MEDIA3" -p "$PKG" 2>"$OUT/discovery-cmd-package.err")"; then
  DISCOVERY_ROUTE="cmd-package-query-services"
fi
if [[ -z "$SERVICES" ]]; then
  if SERVICES="$(pm query-services --brief --components --user "$USER_ID" \
      -a "$ACTION_MEDIA3" -p "$PKG" 2>"$OUT/discovery-pm.err")"; then
    DISCOVERY_ROUTE="pm-query-services"
  fi
fi

# Last-resort read-only API29-compatible package dump. It is accepted only when exactly one
# component in this package is adjacent to the exact Media3 service action.
if [[ -z "$SERVICES" ]]; then
  dumpsys package "$PKG" >"$OUT/discovery-package-dump.txt" 2>&1 || true
  mapfile -t DUMP_COMPONENTS < <(awk -v action="$ACTION_MEDIA3" -v pkg="$PKG" '
    /^[[:space:]]+[0-9a-f]+[[:space:]]+'"$PKG"'\/[A-Za-z0-9_.$]+[[:space:]]+filter/ {
      component=$2
    }
    index($0, action) && component ~ ("^" pkg "/") { print component }
  ' "$OUT/discovery-package-dump.txt" | sort -u)
  if (( ${#DUMP_COMPONENTS[@]} == 1 )); then
    SERVICES="${DUMP_COMPONENTS[0]}"
    DISCOVERY_ROUTE="dumpsys-package-intent-filter"
  fi
fi

printf 'route=%s\n%s\n' "${DISCOVERY_ROUTE:-unresolved}" "$SERVICES" >"$OUT/discovered-services.txt"
mapfile -t COMPONENTS < <(printf '%s\n' "$SERVICES" \
  | grep -E '^com\.navimods\.radio/[A-Za-z0-9_.$]+$' | sort -u)
if (( ${#COMPONENTS[@]} != 1 )); then
  printf 'BLOCKED: expected exactly one installed NavRadio MediaSessionService; found %s\n' \
    "${#COMPONENTS[@]}" | tee "$OUT/SUMMARY.txt"
  printf 'No service was started. Discovery route=%s. Review discovery error/package-dump files.\n' \
    "${DISCOVERY_ROUTE:-unresolved}" >>"$OUT/SUMMARY.txt"
  record service-start BLOCKED "service discovery unresolved/ambiguous; found ${#COMPONENTS[@]}"
  exit 4
fi
COMPONENT="${COMPONENTS[0]}"

snapshot() {
  local phase="$1"
  run_capture "$phase-package.txt" dumpsys package "$PKG"
  run_capture_sh "$phase-path.txt" "pm path '$PKG' 2>&1 || true"
  run_capture_sh "$phase-hash-readable.txt" \
    "pm path '$PKG' 2>/dev/null | sed 's/^package://' | while IFS= read -r p; do if test -r \"\$p\"; then sha256sum \"\$p\"; else printf 'UNREADABLE %s\\n' \"\$p\"; fi; done"
  run_capture_sh "$phase-pid.txt" "pidof '$PKG' 2>&1 || true"
  run_capture_sh "$phase-services.txt" "dumpsys activity services '$PKG' 2>&1 || true"
  run_capture "$phase-media-session.txt" dumpsys media_session
  run_capture_sh "$phase-audio.txt" \
    "dumpsys audio 2>&1 | grep -Ei -C 3 'focus|AudioFocus|route|device|com\.navimods\.radio' | tail -n 500 || true"
  run_capture_sh "$phase-task.txt" \
    "dumpsys activity activities 2>&1 | grep -Ei -m 50 'mResumedActivity|topResumedActivity|ResumedActivity|com\.navimods\.radio|com\.cbkii\.ts18launcher' || true"
}

snapshot before
if root_available; then
  timeout -k 2 "$CAP_TIMEOUT" su -c \
    "export PATH='$ANDROID_ROOT_PATH'; pm path '$PKG' 2>/dev/null | sed 's/^package://' | while IFS= read -r p; do sha256sum \"\$p\" 2>/dev/null || true; done" \
    >"$OUT/before-hash-root.txt" 2>&1 || true
  record before-hash-root.txt PASS "root read-only hash attempted"
else
  printf 'BLOCKED: root unavailable; readable hash remains authoritative if present\n' >"$OUT/before-hash-root.txt"
  record before-hash-root.txt BLOCKED "root unavailable"
fi
printf 'user=%s\ncomponent=%s\naction=%s\ndiscovery_route=%s\n' \
  "$USER_ID" "$COMPONENT" "$ACTION_MEDIA3" "$DISCOVERY_ROUTE" >"$OUT/target.txt"

# Choose the caller before mutation so exactly one service-start command is ever issued.
START_ROUTE="normal-termux-caller"
if root_available; then START_ROUTE="root"; fi
START_RC=1
if [[ "$START_ROUTE" == "root" ]]; then
  if timeout -k 2 4 su -c \
      "export PATH='$ANDROID_ROOT_PATH'; am start-foreground-service --user $USER_ID -a $ACTION_MEDIA3 -n $COMPONENT" \
      >"$OUT/start.txt" 2>&1; then
    START_RC=0
    record service-start PASS "single root service-start command accepted"
  else
    START_RC=$?
    record service-start FAIL "single root service-start rejected/timed out rc=$START_RC"
  fi
else
  if timeout -k 2 4 am start-foreground-service --user "$USER_ID" \
      -a "$ACTION_MEDIA3" -n "$COMPONENT" >"$OUT/start.txt" 2>&1; then
    START_RC=0
    record service-start PASS "single normal service-start command accepted"
  else
    START_RC=$?
    record service-start FAIL "single normal service-start rejected/timed out rc=$START_RC"
  fi
fi

sleep 0.5
snapshot after-0500ms
sleep 1
snapshot after-1500ms
sleep 2
snapshot after-3500ms

PID_BEFORE="$(tr '\n' ' ' <"$OUT/before-pid.txt" 2>/dev/null | sed 's/[[:space:]]*$//')"
PID_AFTER="$(tr '\n' ' ' <"$OUT/after-3500ms-pid.txt" 2>/dev/null | sed 's/[[:space:]]*$//')"
if grep -q "$PKG" "$OUT/after-3500ms-media-session.txt" 2>/dev/null; then SESSION_OBSERVED="yes"; else SESSION_OBSERVED="no"; fi
if grep -Ei 'mResumedActivity|topResumedActivity|ResumedActivity' "$OUT/after-3500ms-task.txt" 2>/dev/null \
    | grep -q "$PKG"; then FOREGROUND_ACTIVITY="yes"; else FOREGROUND_ACTIVITY="no"; fi
if cmp -s "$OUT/before-audio.txt" "$OUT/after-3500ms-audio.txt"; then
  AUDIO_TEXT_CHANGED="no"
else
  AUDIO_TEXT_CHANGED="yes-review-before-after"
fi

cat >"$OUT/SUMMARY.txt" <<EOF
NavRadio service-start qualification
====================================
android_user=$USER_ID
component=$COMPONENT
service_action=$ACTION_MEDIA3
discovery_route=$DISCOVERY_ROUTE
start_route=$START_ROUTE
service_start_commands_issued=1
start_command_exit=$START_RC
process_before=${PID_BEFORE:-none}
process_after_3_5s=${PID_AFTER:-none}
media_session_observed=$SESSION_OBSERVED
play_capable=UNVERIFIED_REVIEW_CAPTURED_ACTIONS
foreground_navradio_activity_observed=$FOREGROUND_ACTIVITY
filtered_audio_text_changed=$AUDIO_TEXT_CHANGED
audible_observation=REQUIRED_PHYSICAL_OBSERVATION

Interpretation boundary
-----------------------
A successful service command, PID or MediaSession is not by itself proof that passive warm-up is
safe or that one-tap audible playback is ready. Compare the before/after task, session and audio
captures and record whether the physical radio/audio source changed. A normal Termux-caller start
does not prove launcher-UID acceptance; the launcher runtime trace must be used for that boundary.
No stop/force-stop is attempted because no exact safe stop contract is assumed.
EOF

printf 'Output: %s\n' "$OUT"
