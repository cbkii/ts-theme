#!/data/data/com.termux/files/usr/bin/bash
# Read-only TS18 fast-media readiness collector.
# No playback, package, task, HOME, audio-route, SELinux or OEM state is changed.

set -u

OUT_BASE="/storage/emulated/0/Download/ts-theme"
STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf 'unknown')"
OUT="$OUT_BASE/media-readiness-$STAMP"
PRIVATE="${TMPDIR:-$HOME/.cache}/ts-theme-media-readiness-$$"
STATUS="$OUT/STATUS.tsv"
CAP_TIMEOUT=8
WARNINGS=0
umask 077

mkdir -p -- "$OUT_BASE" "$OUT" "$PRIVATE" || {
  printf 'FAILED: cannot create output/private state\n' >&2
  exit 1
}
chmod 700 "$PRIVATE" 2>/dev/null || true

log() {
  printf '[%s] %s\n' "$(date +%H:%M:%S 2>/dev/null || printf '%s' '--:--:--')" "$*" \
    | tee -a "$OUT/run.log"
}

record() {
  printf '%s\t%s\t%s\n' "$1" "$2" "$3" >>"$STATUS"
}

capture() {
  local name="$1"
  shift
  local dst="$OUT/$name"
  local rc
  mkdir -p -- "$(dirname -- "$dst")"
  if timeout -k 2 "$CAP_TIMEOUT" "$@" >"$dst" 2>&1; then
    record "$name" PASS "captured"
    return 0
  fi
  rc=$?
  printf '\n[collector] exit_status=%s\n' "$rc" >>"$dst"
  record "$name" UNVERIFIED "command failed/timed out rc=$rc"
  WARNINGS=$((WARNINGS + 1))
  return 0
}

capture_sh() {
  local name="$1"
  shift
  capture "$name" sh -c "$*"
}

capture_root() {
  local name="$1"
  shift
  local dst="$OUT/$name"
  local command="$*"
  local rc
  mkdir -p -- "$(dirname -- "$dst")"
  if ! command -v su >/dev/null 2>&1; then
    printf 'BLOCKED: Magisk su not found\n' >"$dst"
    record "$name" BLOCKED "su not found"
    return 0
  fi
  if timeout -k 2 "$CAP_TIMEOUT" su -c "$command" >"$dst" 2>&1; then
    record "$name" PASS "root read-only capture"
    return 0
  fi
  rc=$?
  printf '\n[collector] root_exit_status=%s\n' "$rc" >>"$dst"
  record "$name" BLOCKED "root denied/timed out rc=$rc"
  WARNINGS=$((WARNINGS + 1))
  return 0
}

cleanup() {
  local rc=$?
  trap - EXIT INT TERM HUP
  rm -rf -- "$PRIVATE"
  printf '\nOutput: %s\n' "$OUT" | tee -a "$OUT/run.log"
  if ((WARNINGS)); then
    printf 'Collector completed with %s blocked/unverified capture(s).\n' "$WARNINGS" \
      | tee -a "$OUT/run.log"
  else
    printf 'Collector completed without capture warnings.\n' | tee -a "$OUT/run.log"
  fi
  exit "$rc"
}
trap cleanup EXIT INT TERM HUP

printf 'surface\tstatus\tdetail\n' >"$STATUS"
log "TS18 fast-media readiness collector"
log "READ ONLY: do not press source Play until baseline capture is complete"

capture identity/date.txt date -Ins
capture identity/id.txt id
capture identity/id-z.txt id -Z
capture identity/getprop.txt getprop
capture identity/current-user.txt sh -c \
  'cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true'
capture_root identity/root-id.txt 'id; id -Z 2>/dev/null || true; printf "user="; cmd activity get-current-user 2>/dev/null || true'

capture media/media-session.txt dumpsys media_session
capture_sh media/selected-sessions.txt \
  "dumpsys media_session 2>&1 | grep -Ei -C 6 'com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|state=|actions=|metadata' || true"

for pkg in com.cbkii.ts18launcher com.tw.media com.tw.radio com.navimods.radio; do
  capture "packages/$pkg-package.txt" dumpsys package "$pkg"
  capture_sh "packages/$pkg-path.txt" "pm path '$pkg' 2>&1 || true"
  capture_sh "runtime/$pkg-pid.txt" "pidof '$pkg' 2>&1 || true"
  capture_sh "runtime/$pkg-services.txt" \
    "dumpsys activity services '$pkg' 2>&1 || true"
done

capture_sh packages/media-browser-services.txt \
  "cmd package query-intent-services -a android.media.browse.MediaBrowserService 2>&1 || pm query-services -a android.media.browse.MediaBrowserService 2>&1 || true"
capture_sh packages/media3-session-services.txt \
  "cmd package query-intent-services -a androidx.media3.session.MediaSessionService 2>&1 || pm query-services -a androidx.media3.session.MediaSessionService 2>&1 || true"

capture_root runtime/root-process-contexts.txt \
  "ps -AZ 2>/dev/null | grep -E 'com\.cbkii\.ts18launcher|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio' || true"
capture_root runtime/launcher-prefs.txt \
  "cat /data/user/0/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>/dev/null || cat /data/data/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>/dev/null || true"

cat >"$OUT/PLAYBOOK.txt" <<'EOF'
Fast-media physical qualification
=================================

1. Capture this directory before touching Radio/Music. Record which source is selected on HOME.
2. For each configured source, separately record a healthy manually-opened baseline:
   - app version/package/user
   - service/process state
   - exact MediaSession state/actions/metadata
   - whether HOME controls work after returning normally to HOME.
3. Cold/background test (do not force-stop as a substitute for ordinary process death):
   - reboot or ordinary process-death scenario as required
   - return to HOME
   - note monotonic elapsed time if available
   - tap HOME Play once
   - note visible feedback, service/session appearance, playback-state acknowledgement,
     metadata render and physical audible onset as separate observations.
4. Repeat with Magisk root denied/unavailable. Root failure must fall back cleanly where a
   normal Android path exists; record BLOCKED where the source contract itself is unavailable.
5. Repeat with the opposite source already playing. Passive preparation must not interrupt it.
6. Auxio-TS: repeat with USB present, late-mounted and unavailable. Do not clear app data/grants.
7. NavRadio+: specifically observe whether service start alone changes radio/audio routing.
   Passive warm-up remains unqualified until this is proven non-disruptive on the installed build.
8. Stock TW Radio: capture any exact session/vendor service/callback evidence. Absence of a service
   in the Radio APK alone is not proof that no external Topway route exists.
9. Lifecycle matrix: launcher restart, player process death, cold boot, repeated HOME returns,
   reboot and ACC sleep/wake. Screen-on is not treated as proof of ACC wake.

Loader/overlay qualification
============================
No masked source-Activity fallback is enabled by the repository implementation. Before enabling
one, separately prove overlay permission/app-op, draw-before-launch, HOME-draw-before-dismiss,
input blocking, navigation handoff serialisation, source controllability after Activity loss, and
that OEM reverse-camera/call/SystemUI surfaces are not obscured or delayed. If any item is not
proven, leave masked fallback disabled.

Status terms
============
PASS       command/capture succeeded in the inspected identity and scope
FAIL       a tested contract behaved incorrectly
BLOCKED    prerequisite such as root/permission/source contract was unavailable
UNVERIFIED capture/test did not establish the result
EOF

log "baseline capture complete"
