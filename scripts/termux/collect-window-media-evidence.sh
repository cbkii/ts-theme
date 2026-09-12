#!/data/data/com.termux/files/usr/bin/bash
# Read-only TS18 window/media evidence collector for the launcher/DoFun/Organic Maps investigation.
# Designed to be safe to run while DoFun is visibly hosting Organic Maps.

set -u

OUT_BASE="/storage/emulated/0/Download"
STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf 'unknown')"
OUT="$OUT_BASE/TS18-launcher-window-media-$STAMP"
ARCHIVE="$OUT.zip"
CAP_TIMEOUT=8
LOG_LINES=600
WARNINGS=0

mkdir -p "$OUT" || { printf 'FAILED: cannot create %s\n' "$OUT" >&2; exit 1; }

log() { printf '[%s] %s\n' "$(date +%H:%M:%S 2>/dev/null || printf '--:--:--')" "$*" | tee -a "$OUT/run.log"; }

capture() {
  local rel="$1"; shift
  local dst="$OUT/$rel"
  mkdir -p "$(dirname "$dst")"
  log "capture $rel"
  if timeout -k 2 "$CAP_TIMEOUT" "$@" >"$dst" 2>&1; then
    return 0
  fi
  local rc=$?
  printf '\n[collector] exit_status=%s\n' "$rc" >>"$dst"
  WARNINGS=$((WARNINGS + 1))
  return 0
}

capture_sh() {
  local rel="$1"; shift
  local command="$*"
  capture "$rel" sh -c "$command"
}

capture_root_readonly() {
  local rel="$1"; shift
  local command="$*"
  local dst="$OUT/$rel"
  mkdir -p "$(dirname "$dst")"
  log "capture root-readonly $rel"
  if ! command -v su >/dev/null 2>&1; then
    printf 'BLOCKED: su not found\n' >"$dst"
    return 0
  fi
  if timeout -k 2 "$CAP_TIMEOUT" su -c "$command" >"$dst" 2>&1; then
    return 0
  fi
  local rc=$?
  printf '\n[collector] root exit_status=%s\n' "$rc" >>"$dst"
  WARNINGS=$((WARNINGS + 1))
  return 0
}

finalize() {
  local rc=$?
  log "finalize rc=$rc warnings=$WARNINGS"
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "$OUT" && find . -type f ! -name SHA256SUMS.txt -print0 | sort -z | xargs -0 sha256sum >SHA256SUMS.txt 2>/dev/null) || true
  fi
  rm -f "$ARCHIVE"
  if command -v zip >/dev/null 2>&1; then
    (cd "$(dirname "$OUT")" && zip -qr "$ARCHIVE" "$(basename "$OUT")") || true
  elif command -v python >/dev/null 2>&1; then
    python - "$OUT" "$ARCHIVE" <<'PY' || true
import os, sys, zipfile
src, out = sys.argv[1:3]
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    root_parent = os.path.dirname(src)
    for root, _, files in os.walk(src):
        for name in files:
            path = os.path.join(root, name)
            z.write(path, os.path.relpath(path, root_parent))
PY
  fi
  printf '\nEvidence directory: %s\n' "$OUT" | tee -a "$OUT/run.log"
  [ -f "$ARCHIVE" ] && printf 'Archive: %s\n' "$ARCHIVE" | tee -a "$OUT/run.log"
  exit "$rc"
}
trap finalize EXIT INT TERM HUP

log "TS18 launcher/window/media evidence collector"
log "READ ONLY: no settings, tasks, packages, playback or window state will be changed"

capture identity/date.txt date -Ins
capture identity/getprop.txt getprop
capture identity/id.txt id
capture identity/uname.txt uname -a
capture display/wm-size.txt wm size
capture display/wm-density.txt wm density
capture display/dumpsys-display.txt dumpsys display
capture display/settings-freeform.txt sh -c 'for k in enable_freeform_support force_resizable_activities development_force_resizable_activities; do printf "%s=" "$k"; settings get global "$k" 2>&1; done'

capture packages/features.txt pm list features
capture_sh packages/window-features.txt "pm list features 2>&1 | grep -Ei 'freeform|picture.in.picture|pip|automotive|leanback|screen|touch' || true"
for pkg in com.cbkii.ts18launcher com.dofun.variety app.organicmaps com.navimods.radio com.tw.media com.tw.music; do
  capture "packages/$pkg.txt" dumpsys package "$pkg"
done
capture packages/media-browser-services.txt sh -c 'cmd package query-intent-services -a android.media.browse.MediaBrowserService 2>&1 || pm query-services -a android.media.browse.MediaBrowserService 2>&1 || true'

capture window/activity-activities.txt dumpsys activity activities
capture window/activity-recents.txt dumpsys activity recents
capture window/window-windows.txt dumpsys window windows
capture window/window-displays.txt dumpsys window displays
capture window/window-policy.txt dumpsys window policy
capture_sh window/relevant-activity-lines.txt "dumpsys activity activities 2>&1 | grep -Ei 'dofun|organicmaps|ts18launcher|windowingMode|bounds|mResumed|topResumed|taskId|displayId' || true"
capture_sh window/relevant-window-lines.txt "dumpsys window windows 2>&1 | grep -Ei 'dofun|organicmaps|ts18launcher|Window\{|mBounds|frame=|displayId|mCurrentFocus|mFocusedApp' || true"

capture surface/surface-list.txt sh -c 'dumpsys SurfaceFlinger --list 2>&1 || true'
capture_sh surface/relevant-surfaces.txt "dumpsys SurfaceFlinger --list 2>&1 | grep -Ei 'dofun|organicmaps|ts18launcher|surfaceview' || true"

capture media/media-session.txt dumpsys media_session
capture_sh media/relevant-sessions.txt "dumpsys media_session 2>&1 | grep -Ei -C 4 'com.navimods.radio|com.tw.media|com.tw.music|state=|actions=|package=' || true"

capture_root_readonly launcher/shared-prefs.xml 'cat /data/user/0/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>&1 || cat /data/data/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>&1 || true'

capture logs/relevant-logcat.txt sh -c "logcat -d -v threadtime -t '$LOG_LINES' 2>&1 | grep -Ei 'TS18Launcher|cbkii.ts18launcher|dofun|organicmaps|navimods|MediaSession|MediaBrowser|DesktopWindow|FloatingApp|cardoor' || true"

cat >"$OUT/README.txt" <<'TXT'
Purpose
=======
This is a bounded, read-only discriminator for the standalone launcher media bootstrap and the separate future map-windowing investigation.

Best windowing capture
======================
For the most useful DoFun evidence, run this collector while DoFun itself is visibly displaying Organic Maps in its desktop/navigation window. Do not change window/task state during the collection.

Media capture
=============
The package/service/session files show whether configured Radio/Music applications expose standard MediaBrowserService and MediaSession surfaces. The collector does not press Play/Pause/Next/Previous; physical action observations should be noted separately.

Interpretation
==============
Absence should only be claimed within a successfully captured surface. Timed-out/permission-denied/root-blocked captures are BLOCKED/UNKNOWN, not negative evidence.
TXT

log "capture complete"
