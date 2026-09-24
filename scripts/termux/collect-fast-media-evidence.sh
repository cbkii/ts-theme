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
TERMUX_BIN="${PREFIX:-/data/data/com.termux/files/usr}/bin"
ANDROID_ROOT_PATH="/system/bin:/system/xbin:/vendor/bin:/product/bin:/apex/com.android.runtime/bin"
export PATH="$TERMUX_BIN:$ANDROID_ROOT_PATH"
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
  capture "$name" env PATH="$PATH" sh -c "$*"
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
  if timeout -k 2 "$CAP_TIMEOUT" su -c "export PATH='$ANDROID_ROOT_PATH'; $command" >"$dst" 2>&1; then
    record "$name" PASS "root read-only capture"
    return 0
  fi
  rc=$?
  printf '\n[collector] root_exit_status=%s\n' "$rc" >>"$dst"
  record "$name" BLOCKED "root denied/timed out rc=$rc"
  WARNINGS=$((WARNINGS + 1))
  return 0
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

CURRENT_USER="$(resolve_current_user || true)"

capture identity/date.txt date -Ins
capture identity/id.txt id
capture identity/id-z.txt id -Z
capture identity/getprop.txt getprop
printf '%s\n' "${CURRENT_USER:-UNVERIFIED}" >"$OUT/identity/current-user.txt"
if [[ -n "$CURRENT_USER" ]]; then
  record identity/current-user.txt PASS "current Android user resolved"
else
  record identity/current-user.txt UNVERIFIED "current Android user could not be resolved"
  WARNINGS=$((WARNINGS + 1))
fi
capture_root identity/root-id.txt 'id; id -Z 2>/dev/null || true; printf "user="; cmd activity get-current-user 2>/dev/null || true'

capture media/media-session.txt dumpsys media_session
capture_sh media/selected-sessions.txt \
  "dumpsys media_session 2>&1 | grep -Ei -C 6 'com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|state=|actions=|metadata' || true"
capture_sh media/audio-focus-route.txt \
  "dumpsys audio 2>&1 | grep -Ei -C 3 'focus|AudioFocus|route|device|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio' | tail -n 500 || true"
capture_sh media/ts18-media-trace.txt \
  "logcat -d -v threadtime -s TS18Media:I '*:S' 2>&1 | tail -n 320 || true"
capture_root media/ts18-media-trace-root.txt \
  "logcat -d -v threadtime -s TS18Media:I '*:S' 2>&1 | tail -n 320 || true"

capture_sh lifecycle/resumed-task.txt \
  "dumpsys activity activities 2>&1 | grep -Ei -m 60 'mResumedActivity|topResumedActivity|ResumedActivity|com\.cbkii\.ts18launcher|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio' || true"
capture_sh lifecycle/power.txt \
  "dumpsys power 2>&1 | grep -Ei -m 120 'Wakefulness|Display Power|mWakefulness|mScreenBrightness|mHoldingWakeLock|mIsPowered' || true"
capture_sh lifecycle/notification-listener.txt \
  "dumpsys notification 2>&1 | grep -Ei -C 2 'com\.cbkii\.ts18launcher|MediaListenerService|enabled_notification_listeners' | head -n 200 || true"

capture_sh storage/mounts.txt \
  "grep -Ei 'usbdisk|media_rw|mnt/runtime|/storage/' /proc/mounts 2>&1 || true"
capture_sh storage/storage-dirs.txt "ls -la /storage 2>&1 || true"
capture_sh storage/volumes.txt "sm list-volumes all 2>&1 || true"
capture_sh storage/usb.txt "dumpsys usb 2>&1 | head -n 400 || true"

for pkg in com.cbkii.ts18launcher com.tw.media com.tw.radio com.navimods.radio; do
  capture "packages/$pkg-package.txt" dumpsys package "$pkg"
  capture_sh "packages/$pkg-path.txt" "pm path '$pkg' 2>&1 || true"
  capture_sh "packages/$pkg-hash-readable.txt" \
    "pm path '$pkg' 2>/dev/null | sed 's/^package://' | while IFS= read -r p; do if test -r \"\$p\"; then sha256sum \"\$p\"; else printf 'UNREADABLE %s\\n' \"\$p\"; fi; done"
  capture_root "packages/$pkg-hash-root.txt" \
    "pm path '$pkg' 2>/dev/null | sed 's/^package://' | while IFS= read -r p; do sha256sum \"\$p\" 2>/dev/null || true; done"
  capture_sh "runtime/$pkg-pid.txt" "pidof '$pkg' 2>&1 || true"
  capture_sh "runtime/$pkg-services.txt" "dumpsys activity services '$pkg' 2>&1 || true"
done

# API29 uses query-services. Do not classify an unsupported shell alias as proof of no service.
if cmd package query-services --brief --components -a android.media.browse.MediaBrowserService \
    >"$OUT/packages/media-browser-services.txt" 2>"$OUT/packages/media-browser-services.err"; then
  record packages/media-browser-services.txt PASS "API29 query-services captured"
elif pm query-services --brief --components -a android.media.browse.MediaBrowserService \
    >"$OUT/packages/media-browser-services.txt" 2>>"$OUT/packages/media-browser-services.err"; then
  record packages/media-browser-services.txt PASS "pm query-services captured"
else
  printf 'UNVERIFIED: service query unsupported/failed; inspect package dumps instead\n' \
    >"$OUT/packages/media-browser-services.txt"
  record packages/media-browser-services.txt UNVERIFIED "service query unsupported/failed"
  WARNINGS=$((WARNINGS + 1))
fi

if cmd package query-services --brief --components -a androidx.media3.session.MediaSessionService \
    >"$OUT/packages/media3-session-services.txt" 2>"$OUT/packages/media3-session-services.err"; then
  record packages/media3-session-services.txt PASS "API29 query-services captured"
elif pm query-services --brief --components -a androidx.media3.session.MediaSessionService \
    >"$OUT/packages/media3-session-services.txt" 2>>"$OUT/packages/media3-session-services.err"; then
  record packages/media3-session-services.txt PASS "pm query-services captured"
else
  printf 'UNVERIFIED: Media3 service query unsupported/failed; inspect exact package dump\n' \
    >"$OUT/packages/media3-session-services.txt"
  record packages/media3-session-services.txt UNVERIFIED "service query unsupported/failed"
  WARNINGS=$((WARNINGS + 1))
fi

capture_root runtime/root-process-contexts.txt \
  "ps -AZ 2>/dev/null | grep -E 'com\.cbkii\.ts18launcher|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|com\.tw\.service|com\.tw\.service\.xt|com\.tw\.core' || true"
if [[ -n "$CURRENT_USER" ]]; then
  capture_root runtime/launcher-prefs.txt \
    "cat /data/user/$CURRENT_USER/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>/dev/null || cat /data/data/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>/dev/null || true"
else
  printf 'BLOCKED: current Android user unresolved\n' >"$OUT/runtime/launcher-prefs.txt"
  record runtime/launcher-prefs.txt BLOCKED "current Android user unresolved"
fi

capture_sh topway/processes.txt \
  "ps -A 2>&1 | grep -E 'com\.tw\.service|com\.tw\.service\.xt|com\.tw\.core|com\.tw\.radio|com\.navimods\.radio|com\.tw\.media' || true"
for pkg in com.tw.service com.tw.service.xt com.tw.core; do
  capture_sh "topway/$pkg-services.txt" "dumpsys activity services '$pkg' 2>&1 || true"
done

capture_sh overlay/appop.txt \
  "cmd appops get com.cbkii.ts18launcher android:system_alert_window 2>&1 || appops get com.cbkii.ts18launcher SYSTEM_ALERT_WINDOW 2>&1 || true"

cat >"$OUT/PLAYBOOK.txt" <<'EOF'
Fast-media physical qualification
=================================

1. Capture this directory before touching Radio/Music. Record which source is selected on HOME.
2. On a true cold launcher start, note whether the startup mask is visible and whether any source
   Activity leaks above it. Foreground cold priming is permitted only when Android overlay access is
   already granted; without it the launcher must stay on background readiness paths and fail open.
3. For each configured source, note first HOME Play, Previous and Next independently. A command
   dispatch/session acknowledgement is not proof of audible output.
4. Confirm stable now-playing text. Empty transient callbacks should not erase valid metadata for the
   same live session; a genuinely removed session should clear it.
5. Repeat Play with Magisk launcher-root denied/unavailable where practical. Root failure must fall
   back cleanly where a normal Android path exists.
6. Auxio-TS: repeat with USB present, late-mounted and unavailable. Do not clear app data/grants.
7. NavRadio+: run qualify-navradio-service-start.sh --qualify-navradio-service-start separately.
8. Stock TW Radio: use collect-stock-radio-compare.sh for the cold/manual-open comparison.
9. Lifecycle matrix: use collect-media-lifecycle-evidence.sh while physically performing reboot or
   ACC sleep/wake. Screen-on is not treated as proof of ACC wake.

Status terms
============
PASS       command/capture succeeded in the inspected identity and scope
FAIL       a tested contract behaved incorrectly
BLOCKED    prerequisite such as root/permission/source contract was unavailable
UNVERIFIED capture/test did not establish the result
EOF

log "baseline capture complete"
