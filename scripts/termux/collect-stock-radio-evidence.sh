#!/data/data/com.termux/files/usr/bin/bash
# Read-only stock TW Radio evidence collector.
# Run once cold, then again after manually opening known-good Radio and returning HOME.

set -u

PHASE="${1:-}"
OBSERVE_SECONDS="${2:-12}"
case "$PHASE" in
  --phase=cold) PHASE=cold ;;
  --phase=known-good) PHASE=known-good ;;
  *)
    printf 'Usage: collect-stock-radio-evidence.sh --phase=cold|--phase=known-good [observe_seconds]\n' >&2
    exit 2
    ;;
esac
case "$OBSERVE_SECONDS" in ''|*[!0-9]*) OBSERVE_SECONDS=12 ;; esac
if ((OBSERVE_SECONDS < 1)); then OBSERVE_SECONDS=1; fi
if ((OBSERVE_SECONDS > 30)); then OBSERVE_SECONDS=30; fi

PKG="com.tw.radio"
OUT_BASE="${TS18_EXPORT_ROOT:-/storage/emulated/0/Download/ts-theme}"
STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf 'unknown')"
OUT="$OUT_BASE/stock-radio-$PHASE-$STAMP"
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
    printf '\n[collector] exit_status=%s\n' "$rc" >>"$dst"
    record "$name" UNVERIFIED "command failed/timed out rc=$rc"
  fi
}
capture_sh() { local name="$1"; shift; capture "$name" sh -c "$*"; }

capture identity/date.txt date -Ins
capture identity/current-user.txt sh -c \
  'cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true'
capture identity/package.txt dumpsys package "$PKG"
capture_sh identity/path.txt "pm path '$PKG' 2>&1 || true"
capture_sh identity/apk-sha256.txt \
  "pm path '$PKG' 2>/dev/null | sed 's/^package://' | while IFS= read -r p; do [ -r \"\$p\" ] && sha256sum \"\$p\" || printf 'UNREADABLE %s\\n' \"\$p\"; done"

capture runtime/radio-pid.txt sh -c "pidof '$PKG' 2>&1 || true"
capture runtime/radio-services.txt dumpsys activity services "$PKG"
capture_sh runtime/topway-processes.txt \
  "ps -A 2>&1 | grep -E 'com\.tw\.(radio|service|service\.xt|core)|com\.cbkii\.ts18launcher' || true"
capture_sh runtime/topway-services.txt \
  "dumpsys activity services 2>&1 | grep -Ei -C 5 'com\.tw\.radio|com\.tw\.service|com\.tw\.service\.xt|com\.tw\.core' || true"
capture_sh runtime/resumed-task.txt \
  "dumpsys activity activities 2>&1 | grep -Ei -m 16 'mResumedActivity|topResumedActivity|ResumedActivity|mFocusedApp|com\.tw\.radio|com\.cbkii\.ts18launcher' || true"

capture media/media-session.txt dumpsys media_session
capture_sh media/radio-session.txt \
  "dumpsys media_session 2>&1 | grep -Ei -C 12 'com\.tw\.radio|state=|actions=|metadata|token' || true"
capture media/audio.txt dumpsys audio
capture_sh media/audio-focus-route.txt \
  "dumpsys audio 2>&1 | grep -Ei -C 6 'focus|route|device|radio|com\.tw\.radio|com\.tw\.service' || true"

capture storage/mounts.txt cat /proc/mounts
capture_sh launcher/trace.txt \
  "logcat -d -v threadtime -t 240 -s TS18MediaTrace:I '*:S' 2>&1 || true"

printf 'Starting bounded %ss read-only log window. Perform only the manual Radio control actions you intend to observe.\n' \
  "$OBSERVE_SECONDS" | tee "$OUT/OBSERVATION.txt"
timeout -k 2 "$OBSERVE_SECONDS" logcat -v threadtime \
  | grep -Ei 'com\.tw\.radio|com\.tw\.service|com\.tw\.service\.xt|radioPre|radioSetChannel|radioOpenChannel|MediaSession|AudioFocus|focus|Topway|TWService' \
  >"$OUT/manual-window-logcat.txt" 2>&1 || true
record manual-window-logcat.txt UNVERIFIED "read-only observation window captured; human actions/context required"

cat >"$OUT/SUMMARY.txt" <<EOF
Stock TW Radio evidence phase: $PHASE
====================================
This collector is read-only. It does not call radioPre/radioSetChannel/radioOpenChannel, send guessed
broadcasts, invoke Binder transactions, start RadioActivity, or mutate Topway state.

Use
---
1. Keep this directory as the $PHASE snapshot.
2. For the paired run, use the opposite phase:
   - cold: capture before stock Radio has been manually opened in this lifecycle;
   - known-good: manually open stock Radio, establish normal operation, return HOME, then capture.
3. Compare process/service, exact MediaSession token/state/actions/metadata, audio focus/route and bounded
   logcat. Static XTService method names are only search leads until caller authority and semantics are proven.

Required conclusion
-------------------
Do not claim a stock-radio cold-readiness contract unless the paired evidence identifies an exact callable
surface and shows that its caller identity, lifecycle behaviour and rollback are understood. Otherwise the
launcher remains existing-session/external-route-only for com.tw.radio.
EOF
printf 'Output: %s\n' "$OUT"
