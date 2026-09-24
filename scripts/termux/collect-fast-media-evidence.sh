#!/data/data/com.termux/files/usr/bin/bash
# Read-only TS18 fast-media readiness collector.
# No playback, package, task, HOME, audio-route, SELinux or OEM state is changed.

set -u

OUT_BASE="${TS18_EXPORT_ROOT:-/storage/emulated/0/Download/ts-theme}"
STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf 'unknown')"
OUT="$OUT_BASE/media-readiness-$STAMP"
PRIVATE="${TS18_PRIVATE_ROOT:-${TMPDIR:-$HOME/.cache}/ts-theme-media-readiness-$$}"
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

root_available() {
  command -v su >/dev/null 2>&1 || return 1
  timeout -k 1 3 su -c 'id -u' 2>/dev/null | grep -qx '0'
}

capture_root() {
  local name="$1"
  shift
  local dst="$OUT/$name"
  local command="$*"
  local rc
  mkdir -p -- "$(dirname -- "$dst")"
  if ! root_available; then
    printf 'BLOCKED: Magisk/root unavailable or denied\n' >"$dst"
    record "$name" BLOCKED "root unavailable/denied"
    return 0
  fi
  if timeout -k 2 "$CAP_TIMEOUT" su -c "$command" >"$dst" 2>&1; then
    record "$name" PASS "root read-only capture"
    return 0
  fi
  rc=$?
  printf '\n[collector] root_exit_status=%s\n' "$rc" >>"$dst"
  record "$name" BLOCKED "root capture timed out/failed rc=$rc"
  WARNINGS=$((WARNINGS + 1))
  return 0
}

capture_package_identity() {
  local pkg="$1"
  capture "packages/$pkg-package.txt" dumpsys package "$pkg"
  capture_sh "packages/$pkg-path.txt" "pm path '$pkg' 2>&1 || true"
  capture_sh "packages/$pkg-apk-sha256.txt" \
    "pm path '$pkg' 2>/dev/null | sed 's/^package://' | while IFS= read -r p; do [ -r \"\$p\" ] && sha256sum \"\$p\" || printf 'UNREADABLE %s\\n' \"\$p\"; done"
  capture_sh "runtime/$pkg-pid.txt" "pidof '$pkg' 2>&1 || true"
  capture_sh "runtime/$pkg-services.txt" "dumpsys activity services '$pkg' 2>&1 || true"
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
log "READ ONLY: baseline capture does not start or control a media source"

capture identity/date.txt date -Ins
capture identity/uptime.txt cat /proc/uptime
capture identity/id.txt id
capture identity/id-z.txt id -Z
capture identity/current-user.txt sh -c \
  'cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true'
capture identity/launcher-version.txt sh -c \
  "dumpsys package com.cbkii.ts18launcher 2>&1 | grep -E 'versionCode=|versionName=|firstInstallTime=|lastUpdateTime=' || true"
capture_root identity/root-id.txt \
  'id; id -Z 2>/dev/null || true; printf "current_user="; cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || true'

for pkg in com.cbkii.ts18launcher com.tw.media com.tw.radio com.navimods.radio; do
  capture_package_identity "$pkg"
done

capture media/media-session.txt dumpsys media_session
capture_sh media/selected-sessions.txt \
  "dumpsys media_session 2>&1 | grep -Ei -C 8 'com\.tw\.media|com\.tw\.radio|com\.navimods\.radio|state=|actions=|metadata|token' || true"
capture media/audio.txt dumpsys audio
capture_sh media/audio-focus-route.txt \
  "dumpsys audio 2>&1 | grep -Ei -C 4 'focus|route|device|music|radio|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio' || true"
capture_sh media/launcher-trace.txt \
  "logcat -d -v threadtime -t 240 -s TS18MediaTrace:I '*:S' 2>&1 || true"

capture_sh packages/media-browser-services.txt \
  "cmd package query-intent-services --brief -a android.media.browse.MediaBrowserService 2>&1 || pm query-services -a android.media.browse.MediaBrowserService 2>&1 || true"
capture_sh packages/media3-session-services.txt \
  "cmd package query-intent-services --brief -a androidx.media3.session.MediaSessionService 2>&1 || pm query-services -a androidx.media3.session.MediaSessionService 2>&1 || true"

capture runtime/activity-top.txt dumpsys activity activities
capture_sh runtime/resumed-task.txt \
  "dumpsys activity activities 2>&1 | grep -Ei -m 12 'mResumedActivity|topResumedActivity|ResumedActivity|mFocusedApp|com\.cbkii\.ts18launcher|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio' || true"
capture_sh runtime/notification-listener.txt \
  "settings get secure enabled_notification_listeners 2>&1; dumpsys notification 2>&1 | grep -Ei -C 3 'com\.cbkii\.ts18launcher|NotificationListener' || true"
capture_sh runtime/topway-processes.txt \
  "ps -A 2>&1 | grep -E 'com\.tw\.(service|core|radio|media)|com\.navimods\.radio|com\.cbkii\.ts18launcher' || true"
capture_sh runtime/topway-services.txt \
  "dumpsys activity services 2>&1 | grep -Ei -C 4 'com\.tw\.service|com\.tw\.service\.xt|com\.tw\.core|com\.tw\.radio' || true"

capture storage/mounts.txt cat /proc/mounts
capture_sh storage/removable-mounts.txt \
  "cat /proc/mounts 2>&1 | grep -Ei '/storage|usb|vold|media_rw' || true"
capture_sh storage/volumes.txt \
  "sm list-volumes all 2>&1 || cmd storage list-volumes all 2>&1 || true"
capture storage/mount-service.txt dumpsys mount

capture_root runtime/root-process-contexts.txt \
  "ps -AZ 2>/dev/null | grep -E 'com\.cbkii\.ts18launcher|com\.tw\.media|com\.tw\.radio|com\.navimods\.radio' || true"
capture_root runtime/launcher-prefs.txt \
  "user=\$(cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null); case \"\$user\" in ''|*[!0-9]*) exit 2;; esac; cat /data/user/\$user/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>/dev/null || true"

cat >"$OUT/PLAYBOOK.txt" <<'EOF'
Fast-media physical qualification
=================================

Repository diagnostics separate service/session/playback acknowledgement from physical audible sound.
A MediaSession state of PLAYING does not by itself prove audible output.

Minimum manual matrix
---------------------
1. Capture this read-only baseline before touching Radio/Music.
2. For each configured source, separately establish a healthy manually-opened baseline and return HOME.
3. Cold/background test: after ordinary process death/reboot as applicable, return HOME and tap Play once.
   Record visible feedback, session appearance, playback acknowledgement, metadata render and audible onset
   as separate observations. Use the TS18MediaTrace timeline for monotonic software timing.
4. Repeat with Magisk denied/unavailable. Root-dependent probes are BLOCKED, not source FAILs; a qualified
   ordinary Android fallback should still operate where one exists.
5. Repeat with the opposite source already playing. Passive preparation must not interrupt it; the old
   source may be paused only after the requested source acknowledges Play.
6. Auxio-TS: repeat with USB present, late-mounted and unavailable. Do not clear data, queue or SAF grants.
   A system-visible mount does not prove Auxio/provider visibility.
7. Run qualify-navradio-service-start.sh only when ready to perform the explicit controlled service-start
   qualification. Passive NavRadio warm-up remains disabled until that installed build is proven safe.
8. Stock TW Radio: use collect-stock-radio-evidence.sh once cold and once after manually opening known-good
   Radio. Do not infer a private Topway API from static method names.
9. Reboot/ACC: run collect-media-lifecycle-evidence.sh while physically performing the requested cycle.
   Screen-on alone is not treated as proof of ACC.

Masked Activity fallback
------------------------
The production masked source-Activity fallback remains disabled. Before enabling it, separately prove
overlay permission/app-op, draw-before-launch, HOME-draw-before-dismiss, input blocking, navigation
handoff serialisation, source controllability after Activity loss, and safe OEM reverse-camera/call/
SystemUI ordering. Missing evidence remains BLOCKED, not an invitation to hide-launch the source.

Status terms
------------
PASS       command/capture succeeded in the inspected identity and scope
FAIL       a tested contract behaved incorrectly
BLOCKED    prerequisite such as root/permission/source contract was unavailable
UNVERIFIED capture/test did not establish the result
EOF

log "baseline capture complete"
