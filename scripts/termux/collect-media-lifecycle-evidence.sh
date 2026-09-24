#!/data/data/com.termux/files/usr/bin/bash
# Read-only bounded lifecycle/ACC evidence capture. User performs the physical transition.

set -u

DURATION="${1:-120}"
if [[ ! "$DURATION" =~ ^[0-9]+$ ]] || ((DURATION < 30 || DURATION > 300)); then
  printf 'Usage: collect-media-lifecycle-evidence.sh [duration-seconds 30..300]\n' >&2
  exit 2
fi

OUT_BASE="/storage/emulated/0/Download/ts-theme"
STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf unknown)"
OUT="$OUT_BASE/media-lifecycle-$STAMP"
TIMELINE="$OUT/timeline.txt"
LOGFILE="$OUT/filtered-logcat.txt"
INTERVAL=2
umask 077
mkdir -p -- "$OUT" || exit 1

cleanup() {
  trap - EXIT INT TERM HUP
  if [[ -n "${LOG_PID:-}" ]]; then
    kill "$LOG_PID" 2>/dev/null || true
    wait "$LOG_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM HUP

printf 'Read-only TS18 media lifecycle capture\n' >"$OUT/README.txt"
printf 'duration_seconds=%s\nstarted=%s\n' "$DURATION" "$(date -Ins 2>/dev/null || true)" >>"$OUT/README.txt"
cat >>"$OUT/README.txt" <<'EOF'
Perform the intended physical lifecycle transition during this bounded window (for example ACC off,
wait, then ACC on). This collector never sends ACC, screen, wake, media, package or audio commands.
Screen-on/display-on evidence is not classified as ACC by itself.
EOF

(timeout -k 2 "$DURATION" logcat -v threadtime 2>&1 \
  | grep -Ei 'TS18Media|YZS_ACC|ACC[_ -]?(ON|OFF)|sleep|wake|PowerManager|com\.tw\.service|com\.tw\.service\.xt|com\.tw\.core|com\.cbkii\.ts18launcher|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio' \
  >"$LOGFILE") &
LOG_PID=$!

START="$(date +%s 2>/dev/null || printf 0)"
END=$((START + DURATION))
SAMPLE=0
while :; do
  NOW="$(date +%s 2>/dev/null || printf 0)"
  ((NOW >= END)) && break
  SAMPLE=$((SAMPLE + 1))
  {
    printf '\n===== sample=%s wall=%s =====\n' "$SAMPLE" "$(date -Ins 2>/dev/null || true)"
    printf '%s\n' '-- uptime --'
    cat /proc/uptime 2>/dev/null || true
    printf '%s\n' '-- power --'
    dumpsys power 2>&1 | grep -Ei -m 120 'Wakefulness|mWakefulness|Display Power|mIsPowered|mHoldingWakeLock|mScreenBrightness' || true
    printf '%s\n' '-- resumed task --'
    dumpsys activity activities 2>&1 | grep -Ei -m 30 'mResumedActivity|topResumedActivity|ResumedActivity|com\.cbkii\.ts18launcher|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio' || true
    printf '%s\n' '-- processes --'
    for pkg in com.cbkii.ts18launcher com.tw.media com.tw.radio com.navimods.radio; do
      printf '%s=' "$pkg"
      pidof "$pkg" 2>/dev/null || true
    done
    printf '%s\n' '-- media sessions --'
    dumpsys media_session 2>&1 | grep -Ei -C 4 'com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|state=|actions=' | head -n 220 || true
    printf '%s\n' '-- storage --'
    grep -Ei 'usbdisk|media_rw|mnt/runtime|/storage/' /proc/mounts 2>/dev/null || true
  } >>"$TIMELINE"
  sleep "$INTERVAL"
done

wait "$LOG_PID" 2>/dev/null || true
LOG_PID=""

cat >"$OUT/INTERPRETATION.txt" <<'EOF'
Interpretation boundary
=======================
Correlate the physical ACC action time with Android uptime/power, process, task, MediaSession,
storage and filtered vendor events. A screen/display transition alone is not ACC proof. If the
Android process never dies, test launcher/session recovery separately from cold boot. If it does die,
record the earliest causal disappearance/recreation rather than treating every dependent session as
an independent failure.
EOF

printf 'Output: %s\n' "$OUT"
