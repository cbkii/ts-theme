#!/data/data/com.termux/files/usr/bin/bash
# Read-only bounded TS18 ACC/reboot/media lifecycle collector.
# The user physically performs the ACC transition while this script samples Android/Topway state.

set -u

OUT_BASE="${TS18_EXPORT_ROOT:-/storage/emulated/0/Download/ts-theme}"
DURATION=120
INTERVAL=2
WARNINGS=0
LOG_PID=""

usage() {
  cat <<'EOF'
Usage: collect-acc-media-lifecycle.sh [--seconds 30..300]

Read-only. Start while HOME is stable, then physically perform the intended ACC off/on transition.
Screen-on/off is captured as one Android signal only and is NOT classified as ACC by this script.
EOF
}

while (($#)); do
  case "$1" in
    --seconds) shift; (($#)) || { usage >&2; exit 2; }; DURATION="$1" ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done
case "$DURATION" in ''|*[!0-9]*) usage >&2; exit 2;; esac
if ((DURATION < 30 || DURATION > 300)); then usage >&2; exit 2; fi

STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf 'unknown')"
OUT="$OUT_BASE/acc-media-lifecycle-$STAMP"
STATUS="$OUT/STATUS.tsv"
CAP_TIMEOUT=8
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
  rc=$?; printf '\n[collector] exit_status=%s\n' "$rc" >>"$dst"
  record "$name" UNVERIFIED "failed/timed out rc=$rc"; WARNINGS=$((WARNINGS + 1)); return 0
}

capture_sh() { local name="$1"; shift; capture "$name" sh -c "$*"; }

cleanup() {
  local rc=$?
  trap - EXIT INT TERM HUP
  if [[ -n "$LOG_PID" ]] && kill -0 "$LOG_PID" 2>/dev/null; then
    kill "$LOG_PID" 2>/dev/null || true
    wait "$LOG_PID" 2>/dev/null || true
  fi
  printf 'Output: %s\n' "$OUT" | tee -a "$OUT/run.log"
  exit "$rc"
}
trap cleanup EXIT INT TERM HUP

printf 'Read-only ACC/media lifecycle capture for %ss. Perform the physical transition now.\n' "$DURATION" \
  | tee "$OUT/run.log"

capture before/date.txt date -Ins
capture before/uptime.txt cat /proc/uptime
capture_sh before/properties.txt \
  "getprop 2>&1 | grep -Ei 'acc|sleep|wake|power|boot|tw|topway' || true"
capture before/power.txt dumpsys power
capture before/media-session.txt dumpsys media_session
capture_sh before/storage.txt 'printf "-- volumes --\\n"; sm list-volumes all 2>&1 || true; printf "-- mounts --\\n"; mount 2>&1 || true'

LOG_FILTER='ACC|YZS_ACC|sleep|wake|PowerManager|DisplayPower|ActivityTaskManager|MediaSession|AudioService|com.tw.service|com.tw.core|com.tw.radio|com.navimods.radio|com.tw.media|com.cbkii.ts18launcher'
(
  timeout -k 2 $((DURATION + 2)) logcat -v threadtime 2>&1 \
    | grep -Ei "$LOG_FILTER" || true
) >"$OUT/logcat-filtered.txt" 2>&1 &
LOG_PID=$!
record logcat-filtered.txt PASS "bounded live logcat requested"

SAMPLES="$OUT/timeline.tsv"
printf 'wall_time\tuptime\tpower\tresumed\tlauncher_pid\tauxio_pid\tnavradio_pid\tstockradio_pid\tmedia\tstorage\n' >"$SAMPLES"
START=$SECONDS
while ((SECONDS - START < DURATION)); do
  wall="$(date +%H:%M:%S 2>/dev/null || printf unknown)"
  uptime="$(cut -d' ' -f1 /proc/uptime 2>/dev/null || printf '?')"
  power="$(dumpsys power 2>/dev/null | grep -Ei -m 3 'mWakefulness=|Display Power: state=|mScreenOn=' | tr '\n\t' '  ' | sed 's/  */ /g' || true)"
  resumed="$(dumpsys activity activities 2>/dev/null | grep -Ei -m 1 'mResumedActivity|topResumedActivity|ResumedActivity' | tr '\t' ' ' || true)"
  launcher="$(pidof com.cbkii.ts18launcher 2>/dev/null || true)"
  auxio="$(pidof com.tw.media 2>/dev/null || true)"
  nav="$(pidof com.navimods.radio 2>/dev/null || true)"
  stock="$(pidof com.tw.radio 2>/dev/null || true)"
  media="$(dumpsys media_session 2>/dev/null | grep -E -m 4 'com.tw.media|com.tw.radio|com.navimods.radio|state=' | tr '\n\t' '  ' | sed 's/  */ /g' || true)"
  storage="$(sm list-volumes all 2>/dev/null | tr '\n\t' '  ' | sed 's/  */ /g' || true)"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$wall" "$uptime" "$power" "$resumed" "$launcher" "$auxio" "$nav" "$stock" "$media" "$storage" \
    >>"$SAMPLES"
  sleep "$INTERVAL"
done
record timeline.tsv PASS "bounded $DURATION-second Android lifecycle sampling"

if [[ -n "$LOG_PID" ]]; then
  wait "$LOG_PID" 2>/dev/null || true
  LOG_PID=""
fi

capture after/date.txt date -Ins
capture after/uptime.txt cat /proc/uptime
capture_sh after/properties.txt \
  "getprop 2>&1 | grep -Ei 'acc|sleep|wake|power|boot|tw|topway' || true"
capture after/power.txt dumpsys power
capture after/media-session.txt dumpsys media_session
capture_sh after/storage.txt 'printf "-- volumes --\\n"; sm list-volumes all 2>&1 || true; printf "-- mounts --\\n"; mount 2>&1 || true'
capture_sh after/processes.txt \
  "ps -A 2>&1 | grep -E 'com\.cbkii\.ts18launcher|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|com\.tw\.service|com\.tw\.core' || true"
capture_sh after/services.txt \
  "dumpsys activity services 2>&1 | grep -Ei -C 3 'com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|com\.tw\.service|com\.tw\.core' || true"

cat >"$OUT/INTERPRETATION.txt" <<'EOF'
ACC/media lifecycle interpretation boundary
===========================================
- This capture does not write MCU/CAN, OEM services, properties, packages, playback or power state.
- Screen/display wakefulness is an Android observation only; it is not proof of ACC state.
- Look for temporal correlation between physical ACC action, Topway/ACC log events, Android
  wakefulness/tasks, process/service survival, MediaSession state and storage remount.
- A missing event in unprivileged logcat is UNVERIFIED, not proof that the OEM event does not exist.
- Only after the owning wake/lifecycle surface is identified should launcher runtime handling change.
EOF

if ((WARNINGS)); then printf 'Blocked/unverified captures: %s\n' "$WARNINGS" | tee -a "$OUT/run.log"; fi
