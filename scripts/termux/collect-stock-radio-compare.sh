#!/data/data/com.termux/files/usr/bin/bash
# Read-only cold-vs-manually-opened stock TW Radio comparator.

set -u

PHASE=""
SESSION=""
while (($#)); do
  case "$1" in
    --phase)
      PHASE="${2:-}"
      shift 2
      ;;
    --session)
      SESSION="${2:-}"
      shift 2
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

if [[ "$PHASE" != "cold" && "$PHASE" != "opened" ]]; then
  printf 'Usage: collect-stock-radio-compare.sh --session NAME --phase cold|opened\n' >&2
  exit 2
fi
if [[ -z "$SESSION" || ! "$SESSION" =~ ^[A-Za-z0-9._-]+$ ]]; then
  printf 'A simple --session NAME is required so both phases share one directory.\n' >&2
  exit 2
fi

PKG="com.tw.radio"
OUT_BASE="/storage/emulated/0/Download/ts-theme/stock-radio-$SESSION"
OUT="$OUT_BASE/$PHASE"
CAP_TIMEOUT=8
umask 077
mkdir -p -- "$OUT" || exit 1

capture() {
  local name="$1"
  shift
  timeout -k 2 "$CAP_TIMEOUT" "$@" >"$OUT/$name" 2>&1 || printf '\nexit=%s\n' "$?" >>"$OUT/$name"
}

capture_sh() {
  local name="$1"
  shift
  capture "$name" sh -c "$*"
}

capture timestamp.txt date -Ins
capture current-user.txt sh -c 'cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true'
capture package.txt dumpsys package "$PKG"
capture_sh path.txt "pm path '$PKG' 2>&1 || true"
capture_sh pid.txt "pidof '$PKG' 2>&1 || true"
capture_sh radio-services.txt "dumpsys activity services '$PKG' 2>&1 || true"
capture media-session.txt dumpsys media_session
capture_sh selected-media-session.txt \
  "dumpsys media_session 2>&1 | grep -Ei -C 8 'com\.tw\.radio|state=|actions=|metadata' || true"
capture_sh task.txt \
  "dumpsys activity activities 2>&1 | grep -Ei -m 80 'mResumedActivity|topResumedActivity|ResumedActivity|com\.tw\.radio|com\.cbkii\.ts18launcher' || true"
capture_sh audio.txt \
  "dumpsys audio 2>&1 | grep -Ei -C 4 'focus|AudioFocus|route|device|com\.tw\.radio' | tail -n 600 || true"
capture_sh vendor-services.txt \
  "dumpsys activity services 2>&1 | grep -Ei -C 3 'com\.tw\.service|com\.tw\.service\.xt|com\.tw\.core|com\.tw\.radio' | tail -n 800 || true"
capture_sh recent-radio-log.txt \
  "logcat -d -v threadtime -T '5 minutes ago' 2>&1 | grep -Ei 'com\.tw\.radio|RadioService|radioPre|radioSetChannel|radioOpenChannel|TWUtil|SOURCE_VALUE_RADIO' | tail -n 600 || true"

cat >"$OUT/README.txt" <<EOF
Stock TW Radio comparison phase: $PHASE
Session: $SESSION

This phase is READ ONLY. It did not start Radio, issue transport commands, invoke XTService methods,
change the audio source or force-stop anything.
EOF

if [[ "$PHASE" == "cold" ]]; then
  cat >>"$OUT_BASE/NEXT.txt" <<EOF
1. Preserve this cold capture.
2. Manually open stock TW Radio using its normal UI.
3. Confirm it is operating normally, then return normally to TS18 Launcher HOME.
4. Run:
   collect-stock-radio-compare.sh --session $SESSION --phase opened
5. Compare cold/ and opened/ for the exact session/service/task/audio difference.
EOF
else
  cat >"$OUT_BASE/COMPARE.txt" <<'EOF'
Compare cold/ against opened/.

A new process, service or MediaSession after manual open is a discovery lead, not yet proof that an
ordinary launcher UID may initialise it. Static XTService radio method names are also search leads
only. Do not implement broadcasts/Binder calls until caller authority and exact semantics are proven.
EOF
fi

printf 'Output: %s\n' "$OUT"
