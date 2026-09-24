#!/data/data/com.termux/files/usr/bin/bash
# Read-only bounded media/power lifecycle collector for physical reboot/ACC testing.

set -u

DURATION="${1:-90}"
case "$DURATION" in ''|*[!0-9]*) DURATION=90 ;; esac
if ((DURATION < 15)); then DURATION=15; fi
if ((DURATION > 300)); then DURATION=300; fi
INTERVAL=3
OUT_BASE="${TS18_EXPORT_ROOT:-/storage/emulated/0/Download/ts-theme}"
STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf 'unknown')"
OUT="$OUT_BASE/media-lifecycle-$STAMP"
STATUS="$OUT/STATUS.tsv"
umask 077
mkdir -p -- "$OUT/snapshots" || { printf 'FAILED: cannot create %s\n' "$OUT" >&2; exit 1; }
printf 'surface\tstatus\tdetail\n' >"$STATUS"

record() { printf '%s\t%s\t%s\n' "$1" "$2" "$3" >>"$STATUS"; }

snapshot() {
  local index="$1" now="$2" dir="$OUT/snapshots/$index-$now"
  mkdir -p -- "$dir"
  {
    date -Ins 2>/dev/null || true
    cat /proc/uptime 2>/dev/null || true
  } >"$dir/time.txt"
  {
    dumpsys power 2>/dev/null | grep -Ei 'Wakefulness|Display Power|mWakefulness|mScreenBrightness|mHoldingWakeLockSuspendBlocker|mHoldingDisplaySuspendBlocker' || true
    dumpsys display 2>/dev/null | grep -Ei -m 24 'DisplayDeviceInfo|state=|mDisplayState|FLAG_SECURE' || true
  } >"$dir/power-display.txt"
  dumpsys activity activities 2>/dev/null \
    | grep -Ei -m 18 'mResumedActivity|topResumedActivity|ResumedActivity|mFocusedApp|com\.cbkii\.ts18launcher|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio' \
    >"$dir/activity.txt" || true
  ps -A 2>/dev/null \
    | grep -E 'com\.cbkii\.ts18launcher|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|com\.tw\.(service|core)' \
    >"$dir/processes.txt" || true
  dumpsys media_session >"$dir/media-session.txt" 2>&1 || true
  dumpsys media_session 2>/dev/null \
    | grep -Ei -C 8 'com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|state=|actions=|metadata|token' \
    >"$dir/selected-sessions.txt" || true
  dumpsys audio 2>/dev/null \
    | grep -Ei -C 4 'focus|route|device|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|radio' \
    >"$dir/audio-focus-route.txt" || true
  {
    cat /proc/mounts 2>/dev/null | grep -Ei '/storage|usb|vold|media_rw' || true
    sm list-volumes all 2>/dev/null || cmd storage list-volumes all 2>/dev/null || true
  } >"$dir/storage.txt"
}

printf 'Read-only lifecycle collection for %ss. Physically perform only the intended reboot/ACC/sleep-wake action.\n' \
  "$DURATION" | tee "$OUT/README.txt"
printf 'Screen-on is not treated as proof of ACC. No MCU/CAN/property/Ylog write is performed.\n' \
  | tee -a "$OUT/README.txt"

{
  date -Ins 2>/dev/null || true
  printf 'current_user='
  cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true
  getprop 2>/dev/null \
    | grep -Ei 'acc|sleep|wake|boot|power|tw\.|topway|sys\.boot_completed' || true
} >"$OUT/before-properties.txt"
record before-properties.txt PASS "read-only baseline"

# Bounded filtered log window; no logcat clearing and no persistent OEM logger.
timeout -k 2 "$DURATION" logcat -v threadtime 2>&1 \
  | grep -Ei 'ACC|YZS_ACC|sleep|wake|PowerManager|DisplayPower|com\.tw\.|Topway|TWService|MediaSession|AudioFocus|TS18MediaTrace' \
  >"$OUT/lifecycle-logcat.txt" &
LOG_PID=$!

START="$(date +%s 2>/dev/null || printf '0')"
index=0
while ((index * INTERVAL <= DURATION)); do
  now="$(date +%s 2>/dev/null || printf '%s' "$index")"
  snapshot "$index" "$now"
  index=$((index + 1))
  if ((index * INTERVAL > DURATION)); then break; fi
  sleep "$INTERVAL"
done
wait "$LOG_PID" 2>/dev/null || true
record lifecycle-logcat.txt PASS "bounded filtered logcat window"

{
  date -Ins 2>/dev/null || true
  printf 'current_user='
  cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true
  getprop 2>/dev/null \
    | grep -Ei 'acc|sleep|wake|boot|power|tw\.|topway|sys\.boot_completed' || true
} >"$OUT/after-properties.txt"
record after-properties.txt PASS "read-only final snapshot"

cat >"$OUT/INTERPRETATION.txt" <<'EOF'
Interpretation boundary
=======================
This capture is evidence of correlated Android/Topway/power/media/storage observations only.
Do not infer ACC from screen-on alone and do not promote a string such as YZS_ACC_ON/OFF into a
runtime receiver contract without proving the sender, receiver, user/process context and lifecycle
on this exact unit. Use the earliest causal transition in the timestamped snapshots/logcat.

If the unit powers down far enough that Termux/processes stop, an incomplete capture is expected;
classify later dependent checks BLOCKED rather than claiming an Android lifecycle failure.
EOF
printf 'Output: %s\n' "$OUT"
