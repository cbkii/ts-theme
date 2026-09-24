#!/data/data/com.termux/files/usr/bin/bash
# Explicitly mutating NavRadio service-start qualification.
# Starts exactly one discovered installed NavRadio media-session service. It never sends Play,
# changes routing, force-stops/stops the app, or edits package/settings state.

set -u

if (($# != 1)) || [[ "$1" != "--qualify-navradio-service-start" ]]; then
  cat >&2 <<'EOF'
Usage: qualify-navradio-service-start.sh --qualify-navradio-service-start

WARNING: this is NOT a read-only collector. It performs exactly one bounded service-start request
against the currently installed com.navimods.radio service discovered from PackageManager.
It does not issue Play and does not stop the service afterwards.
EOF
  exit 2
fi

OUT_BASE="${TS18_EXPORT_ROOT:-/storage/emulated/0/Download/ts-theme}"
STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf 'unknown')"
OUT="$OUT_BASE/navradio-service-start-$STAMP"
STATUS="$OUT/STATUS.tsv"
CAP_TIMEOUT=8
ACTION="androidx.media3.session.MediaSessionService"
WARNINGS=0
umask 077
mkdir -p -- "$OUT" || { printf 'FAILED: cannot create %s\n' "$OUT" >&2; exit 1; }
printf 'surface\tstatus\tdetail\n' >"$STATUS"

record() { printf '%s\t%s\t%s\n' "$1" "$2" "$3" >>"$STATUS"; }

capture() {
  local name="$1"; shift
  local dst="$OUT/$name" rc
  mkdir -p -- "$(dirname -- "$dst")"
  if timeout -k 2 "$CAP_TIMEOUT" "$@" >"$dst" 2>&1; then
    record "$name" PASS "captured"; return 0
  fi
  rc=$?
  printf '\n[collector] exit_status=%s\n' "$rc" >>"$dst"
  record "$name" UNVERIFIED "command failed/timed out rc=$rc"
  WARNINGS=$((WARNINGS + 1))
  return 0
}

capture_sh() { local name="$1"; shift; capture "$name" sh -c "$*"; }

root_available() {
  command -v su >/dev/null 2>&1 || return 1
  timeout -k 1 3 su -c 'test "$(id -u)" = 0' >/dev/null 2>&1
}

snapshot() {
  local phase="$1"
  capture "$phase/package.txt" dumpsys package com.navimods.radio
  capture_sh "$phase/path-hash.txt" \
    "pm path com.navimods.radio 2>/dev/null | sed 's/^package://' | while IFS= read -r p; do test -r \"\$p\" && sha256sum \"\$p\" || printf 'UNREADABLE %s\\n' \"\$p\"; done"
  capture_sh "$phase/process.txt" "pidof com.navimods.radio 2>&1 || true"
  capture_sh "$phase/services.txt" "dumpsys activity services com.navimods.radio 2>&1 || true"
  capture "$phase/media-session.txt" dumpsys media_session
  capture_sh "$phase/navradio-session.txt" \
    "dumpsys media_session 2>&1 | grep -Ei -C 10 'com\.navimods\.radio|state=|actions=|metadata' || true"
  capture_sh "$phase/audio-route.txt" \
    "dumpsys audio 2>&1 | grep -Ei -C 5 'focus|route|device|stream|com\.navimods\.radio' || true"
  capture_sh "$phase/resumed-task.txt" \
    "dumpsys activity activities 2>&1 | grep -Ei -m 12 'mResumedActivity|topResumedActivity|ResumedActivity|com\.navimods\.radio|com\.cbkii\.ts18launcher' || true"
}

printf 'CONTROLLED MUTATION: one NavRadio service-start request will be issued; no Play command.\n' \
  | tee "$OUT/run.log"

capture identity/current-user.txt sh -c \
  'cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true'
USER_ID="$(tr -cd '0-9' <"$OUT/identity/current-user.txt")"
case "$USER_ID" in ''|*[!0-9]*) record service-start BLOCKED "current Android user unresolved"; exit 1;; esac

QUERY="$OUT/identity/service-query.txt"
if timeout -k 2 "$CAP_TIMEOUT" sh -c \
  "cmd package query-intent-services -a '$ACTION' 2>&1 || pm query-services -a '$ACTION' 2>&1 || true" \
  >"$QUERY" 2>&1; then
  record identity/service-query.txt PASS "queried installed Media3 service action"
else
  record identity/service-query.txt UNVERIFIED "service query failed"
fi

COMPONENTS="$OUT/identity/navradio-components.txt"
grep -Eo 'com\.navimods\.radio/[A-Za-z0-9_.$]+' "$QUERY" | sort -u >"$COMPONENTS" || true
COUNT="$(grep -c . "$COMPONENTS" 2>/dev/null || printf '0')"
if [[ "$COUNT" != "1" ]]; then
  record service-start BLOCKED "expected exactly one installed NavRadio Media3 service; found $COUNT"
  printf 'BLOCKED: could not resolve exactly one installed NavRadio Media3 service.\nOutput: %s\n' "$OUT"
  exit 1
fi
COMPONENT="$(cat "$COMPONENTS")"
case "$COMPONENT" in
  com.navimods.radio/*) ;;
  *) record service-start BLOCKED "unsafe discovered component"; exit 1 ;;
esac

snapshot before
cp "$OUT/before/audio-route.txt" "$OUT/before/audio-route.baseline.txt" 2>/dev/null || true

START_OUT="$OUT/service-start.txt"
if root_available; then
  printf 'root\n' >"$OUT/identity/start-authority.txt"
  if timeout -k 2 6 su -c \
      "am start-foreground-service --user '$USER_ID' -a '$ACTION' -n '$COMPONENT'" \
      >"$START_OUT" 2>&1; then
    record service-start PASS "root service-start command accepted"
  else
    rc=$?
    printf '\n[start] exit_status=%s\n' "$rc" >>"$START_OUT"
    record service-start FAIL "root service-start rejected/timed out rc=$rc"
  fi
else
  printf 'normal\n' >"$OUT/identity/start-authority.txt"
  if timeout -k 2 6 am start-foreground-service --user "$USER_ID" -a "$ACTION" -n "$COMPONENT" \
      >"$START_OUT" 2>&1; then
    record service-start PASS "normal service-start command accepted"
  else
    rc=$?
    printf '\n[start] exit_status=%s\n' "$rc" >>"$START_OUT"
    record service-start FAIL "normal service-start rejected/timed out rc=$rc"
  fi
fi

sleep 1
snapshot after-1s
sleep 2
snapshot after-3s

process_before="$(tr -d '\r\n ' <"$OUT/before/process.txt" 2>/dev/null || true)"
process_after="$(tr -d '\r\n ' <"$OUT/after-3s/process.txt" 2>/dev/null || true)"
session_before="$(grep -c 'com.navimods.radio' "$OUT/before/media-session.txt" 2>/dev/null || true)"
session_after="$(grep -c 'com.navimods.radio' "$OUT/after-3s/media-session.txt" 2>/dev/null || true)"
if cmp -s "$OUT/before/audio-route.txt" "$OUT/after-3s/audio-route.txt"; then
  route_result="no textual change in filtered dumpsys audio"
else
  route_result="filtered dumpsys audio changed; physical/audible interpretation required"
fi

cat >"$OUT/SUMMARY.txt" <<EOF
NavRadio service-start qualification
===================================
Installed component: $COMPONENT
Android user: $USER_ID
Start authority: $(cat "$OUT/identity/start-authority.txt")
Service command: see STATUS.tsv and service-start.txt
Process before: ${process_before:-none}
Process after 3s: ${process_after:-none}
Session mentions before: $session_before
Session mentions after 3s: $session_after
Audio/routing comparison: $route_result
Play-capable session: inspect after-3s/navradio-session.txt actions; this script does not send Play.
Audible/radio activation: HUMAN OBSERVATION REQUIRED. Playback/session state is not proof of sound.

No stop/force-stop or rollback mutation was attempted because no exact safe stop contract is assumed.
EOF

printf 'Qualification capture complete. Output: %s\n' "$OUT"
if ((WARNINGS)); then printf 'Note: %s read-only captures were unverified.\n' "$WARNINGS"; fi
