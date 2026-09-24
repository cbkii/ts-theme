#!/data/data/com.termux/files/usr/bin/bash
# Read-only stock TW Radio evidence collector.
# Run once cold and once after manually opening Radio/returning HOME.

set -u

OUT_BASE="${TS18_EXPORT_ROOT:-/storage/emulated/0/Download/ts-theme}"
PHASE=""
LOG_SECONDS=15
CAP_TIMEOUT=8
WARNINGS=0

usage() {
  cat <<'EOF'
Usage: collect-stock-radio-evidence.sh --phase cold|opened [--log-seconds 5..60]

Read-only. The user performs any Radio UI/transport action manually; this script sends no radio,
Binder, broadcast, XTService or media transport command and does not clear logcat.
EOF
}

while (($#)); do
  case "$1" in
    --phase) shift; (($#)) || { usage >&2; exit 2; }; PHASE="$1" ;;
    --log-seconds) shift; (($#)) || { usage >&2; exit 2; }; LOG_SECONDS="$1" ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

case "$PHASE" in cold|opened) ;; *) usage >&2; exit 2;; esac
case "$LOG_SECONDS" in ''|*[!0-9]*) usage >&2; exit 2;; esac
if ((LOG_SECONDS < 5 || LOG_SECONDS > 60)); then usage >&2; exit 2; fi

STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf 'unknown')"
OUT="$OUT_BASE/stock-radio-$PHASE-$STAMP"
STATUS="$OUT/STATUS.tsv"
umask 077
mkdir -p -- "$OUT" || { printf 'FAILED: cannot create output\n' >&2; exit 1; }
printf 'surface\tstatus\tdetail\n' >"$STATUS"

record() { printf '%s\t%s\t%s\n' "$1" "$2" "$3" >>"$STATUS"; }

capture() {
  local name="$1"; shift
  local dst="$OUT/$name" rc
  mkdir -p -- "$(dirname -- "$dst")"
  if timeout -k 2 "$CAP_TIMEOUT" "$@" >"$dst" 2>&1; then
    record "$name" PASS captured; return 0
  fi
  rc=$?
  printf '\n[collector] exit_status=%s\n' "$rc" >>"$dst"
  record "$name" UNVERIFIED "failed/timed out rc=$rc"
  WARNINGS=$((WARNINGS + 1))
  return 0
}

capture_sh() { local name="$1"; shift; capture "$name" sh -c "$*"; }

capture_root() {
  local name="$1"; shift
  local dst="$OUT/$name" command="$*" rc
  mkdir -p -- "$(dirname -- "$dst")"
  if ! command -v su >/dev/null 2>&1; then
    printf 'BLOCKED: su unavailable\n' >"$dst"; record "$name" BLOCKED "su unavailable"; return 0
  fi
  if timeout -k 2 "$CAP_TIMEOUT" su -c "$command" >"$dst" 2>&1; then
    record "$name" PASS "root read-only capture"; return 0
  fi
  rc=$?; printf '\n[root] exit_status=%s\n' "$rc" >>"$dst"
  record "$name" BLOCKED "root denied/timed out rc=$rc"; WARNINGS=$((WARNINGS + 1)); return 0
}

printf 'Read-only stock Radio evidence phase: %s\n' "$PHASE" | tee "$OUT/run.log"
capture identity/date.txt date -Ins
capture identity/current-user.txt sh -c 'cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true'
capture package/radio.txt dumpsys package com.tw.radio
capture_sh package/radio-path.txt 'pm path com.tw.radio 2>&1 || true'
capture_sh package/radio-hash-readable.txt \
  "pm path com.tw.radio 2>/dev/null | sed 's/^package://' | while IFS= read -r p; do test -r \"\$p\" && sha256sum \"\$p\" || printf 'UNREADABLE %s\\n' \"\$p\"; done"
capture_root package/radio-hash-root.txt \
  'pm path com.tw.radio 2>/dev/null | sed '\''s/^package://'\'' | while IFS= read -r p; do sha256sum "$p" 2>/dev/null || true; done'

capture runtime/radio-process.txt sh -c 'pidof com.tw.radio 2>&1 || true'
capture runtime/radio-services.txt sh -c 'dumpsys activity services com.tw.radio 2>&1 || true'
capture_sh runtime/resumed-task.txt \
  "dumpsys activity activities 2>&1 | grep -Ei -m 16 'mResumedActivity|topResumedActivity|ResumedActivity|com\.tw\.radio|com\.cbkii\.ts18launcher' || true"
capture media/media-session.txt dumpsys media_session
capture_sh media/radio-session.txt \
  "dumpsys media_session 2>&1 | grep -Ei -C 12 'com\.tw\.radio|state=|actions=|metadata' || true"
capture_sh media/audio-focus-route.txt \
  "dumpsys audio 2>&1 | grep -Ei -C 6 'focus|route|device|stream|com\.tw\.radio' || true"

for pkg in com.tw.service com.tw.service.xt com.tw.core; do
  capture_sh "topway/$pkg-process.txt" "pidof '$pkg' 2>&1 || true"
  capture_sh "topway/$pkg-services.txt" "dumpsys activity services '$pkg' 2>&1 || true"
done
capture_root topway/process-contexts.txt \
  "ps -AZ 2>/dev/null | grep -E 'com\.tw\.radio|com\.tw\.service|com\.tw\.service\.xt|com\.tw\.core' || true"

printf 'Capturing %ss bounded filtered logcat. Perform only the manual Radio action appropriate to this phase.\n' "$LOG_SECONDS" \
  | tee -a "$OUT/run.log"
LOG_FILTER="ActivityManager|MediaSession|AudioManager|AudioService|com.tw.radio|com.tw.service|radioPre|radioSetChannel|radioOpenChannel"
LOG_FILE="$OUT/logcat-filtered.txt"
if timeout -k 2 $((LOG_SECONDS + 3)) sh -c \
  "timeout '$LOG_SECONDS' logcat -v threadtime 2>&1 | grep -Ei '$LOG_FILTER' || true" \
  >"$LOG_FILE" 2>&1; then
  if grep -Eqi 'permission denied|not permitted|unable to open|read logs' "$LOG_FILE"; then
    record logcat-filtered.txt UNVERIFIED "logcat permission unavailable"
    WARNINGS=$((WARNINGS + 1))
  else
    record logcat-filtered.txt PASS "bounded filtered logcat"
  fi
else
  rc=$?; record logcat-filtered.txt UNVERIFIED "unprivileged logcat unavailable/timed out rc=$rc"
  WARNINGS=$((WARNINGS + 1))
fi

cat >"$OUT/INTERPRETATION.txt" <<EOF
Stock TW Radio evidence phase: $PHASE
========================================
This run does not call XTService, send radio broadcasts, transact Binder, issue MediaSession
transport or start/stop the Radio Activity.

Compare a cold capture with an opened capture. The useful discriminators are:
- Does an exact com.tw.radio MediaSession appear, and which actions/metadata does it advertise?
- Do com.tw.service / com.tw.service.xt / com.tw.core process or service states change?
- Does filtered audio-focus/route state change?
- Which bounded log lines occur around the manually performed station/Play action?

Strings/method names such as radioPre, radioSetChannel and radioOpenChannel are search leads only.
They do not establish caller authority, transport, argument semantics or rollback.
EOF

printf 'Output: %s\n' "$OUT" | tee -a "$OUT/run.log"
if ((WARNINGS)); then printf 'Blocked/unverified captures: %s\n' "$WARNINGS" | tee -a "$OUT/run.log"; fi
