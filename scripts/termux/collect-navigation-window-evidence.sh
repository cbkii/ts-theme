#!/data/data/com.termux/files/usr/bin/bash
# Event-driven, read-only evidence collector for the TS18 native navigation window.
# Start it on HOME, perform the playbook actions at your own pace, then press Ctrl-C once.

OUT_BASE=/storage/emulated/0/Download
EXPECT_PACKAGE=""
CAPTURE_TIMEOUT=8
MAX_CHECKPOINTS=80
MAX_EXPORT_MIB=192
LAUNCHER=com.cbkii.ts18launcher
DOFUN=com.dofun.variety
HELPER=/data/adb/ts18-launcher/nav-window.sh
ANDROID_PATH=/system/bin:/system/xbin:/vendor/bin
ROOT_OK=0
STOP_REQUESTED=0
LOG_PID=""
FAILS=0
BLOCKED=0
WARNS=0
CHECKPOINTS=0
LAST_SIGNATURE=""
TARGET=""
MODE=unknown
WORK=""

usage() {
  printf '%s\n' \
    "Usage: $0 [--expect-package PKG] [--out-base DIR] [--max-export-mib 32..256]" \
    "" \
    "Run from Termux while the standalone launcher is HOME. Perform the short" \
    "physical playbook at your own pace, then press Ctrl-C once to seal the ZIP."
}

valid_package() {
  case "$1" in
    ''|*[!A-Za-z0-9._]*) return 1 ;;
    *.*) return 0 ;;
    *) return 1 ;;
  esac
}

safe_output_base() {
  case "$1" in
    /*) ;;
    *) return 1 ;;
  esac
  case "$1" in
    *[!A-Za-z0-9._/-]*) return 1 ;;
    *) return 0 ;;
  esac
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --expect-package)
      [ "$#" -ge 2 ] || { usage >&2; exit 64; }
      EXPECT_PACKAGE="$2"
      shift 2
      ;;
    --out-base)
      [ "$#" -ge 2 ] || { usage >&2; exit 64; }
      OUT_BASE="$2"
      shift 2
      ;;
    --max-export-mib)
      [ "$#" -ge 2 ] || { usage >&2; exit 64; }
      MAX_EXPORT_MIB="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 64
      ;;
  esac
done

[ -z "$EXPECT_PACKAGE" ] || valid_package "$EXPECT_PACKAGE" || exit 64
safe_output_base "$OUT_BASE" || exit 64
case "$MAX_EXPORT_MIB" in ''|*[!0-9]*) exit 64 ;; esac
[ "$MAX_EXPORT_MIB" -ge 32 ] && [ "$MAX_EXPORT_MIB" -le 256 ] || exit 64

STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf unknown)"
OUT="$OUT_BASE/TS18-navigation-window-$STAMP"
ZIP="$OUT.zip"
[ ! -e "$OUT" ] && [ ! -e "$ZIP" ] && [ ! -e "$ZIP.sha256" ] || exit 73
mkdir "$OUT" || exit 1
mkdir -p "$OUT"/{identity,packages,window/checkpoints,logs,screens,topway,framework/bytes,helper,root} || exit 1
WORK="$(mktemp -d "${TMPDIR:-/data/data/com.termux/files/usr/tmp}/ts18-nav-window.XXXXXX")" || exit 1
printf 'time\tstatus\tcheck\tdetail\n' >"$OUT/results.tsv"

log() {
  printf '[%s] %s\n' "$(date +%H:%M:%S 2>/dev/null || printf --:--:--)" "$*" \
    | tee -a "$OUT/run.log"
}

result() {
  status="$1"
  check="$2"
  detail="$3"
  printf '%s\t%s\t%s\t%s\n' "$(date +%H:%M:%S 2>/dev/null || printf --:--:--)" \
    "$status" "$check" "$detail" >>"$OUT/results.tsv"
  log "$status $check :: $detail"
  case "$status" in
    FAIL) FAILS=$((FAILS + 1)) ;;
    BLOCKED) BLOCKED=$((BLOCKED + 1)) ;;
    WARN) WARNS=$((WARNS + 1)) ;;
  esac
}

have() {
  command -v "$1" >/dev/null 2>&1
}

capture() {
  rel="$1"
  shift
  mkdir -p "$(dirname "$OUT/$rel")"
  timeout -k 2 "$CAPTURE_TIMEOUT" "$@" >"$OUT/$rel" 2>&1
  rc=$?
  if [ "$rc" -ne 0 ]; then
    printf '\n[capture] exit=%s\n' "$rc" >>"$OUT/$rel"
  fi
  return 0
}

root_capture() {
  rel="$1"
  command_text="$2"
  mkdir -p "$(dirname "$OUT/$rel")"
  if [ "$ROOT_OK" -ne 1 ]; then
    printf 'BLOCKED: root unavailable\n' >"$OUT/$rel"
    return 0
  fi
  timeout -k 2 "$CAPTURE_TIMEOUT" su -c "PATH=$ANDROID_PATH; export PATH; $command_text" \
    >"$OUT/$rel" 2>&1
  rc=$?
  if [ "$rc" -ne 0 ]; then
    printf '\n[root capture] exit=%s\n' "$rc" >>"$OUT/$rel"
  fi
  return 0
}

stop_log() {
  if [ -n "$LOG_PID" ]; then
    kill "$LOG_PID" 2>/dev/null || true
    wait "$LOG_PID" 2>/dev/null || true
    LOG_PID=""
  fi
}

seal_archive() {
  manifest_failed=0
  if have sha256sum; then
    (cd "$OUT" && find . -type f ! -name SHA256SUMS.txt ! -name MANIFEST_VERIFY.txt -print0 \
      | sort -z | xargs -0 -r sha256sum) >"$OUT/SHA256SUMS.txt" 2>/dev/null || manifest_failed=1
    if [ "$manifest_failed" -eq 0 ]; then
      if ! (cd "$OUT" && sha256sum -c SHA256SUMS.txt) >"$OUT/MANIFEST_VERIFY.txt" 2>&1; then
        manifest_failed=1
      fi
    else
      printf 'SHA256SUMS generation failed\n' >"$OUT/MANIFEST_VERIFY.txt"
    fi
  else
    manifest_failed=1
    printf 'BLOCKED: sha256sum unavailable\n' >"$OUT/MANIFEST_VERIFY.txt"
  fi

  rm -f "$ZIP" "$ZIP.sha256"
  if have zip; then
    parent="$(dirname "$OUT")"
    base="$(basename "$OUT")"
    (cd "$parent" && find "$base" -type f -print | LC_ALL=C sort | zip -q -X "$ZIP" -@) || true
  fi
  if [ -f "$ZIP" ] && have sha256sum; then
    sha256sum "$ZIP" >"$ZIP.sha256" 2>/dev/null || true
  fi
  if [ "$manifest_failed" -ne 0 ]; then return 1; fi
  return 0
}

# shellcheck disable=SC2317 # Invoked indirectly by the EXIT trap.
finalize() {
  rc=$?
  trap - EXIT INT TERM HUP
  stop_log
  if [ -n "$WORK" ] && [ -d "$WORK" ]; then rm -rf -- "$WORK"; fi
  printf 'fails=%s\nblocked=%s\nwarns=%s\ncheckpoints=%s\ntarget=%s\nmode=%s\n' \
    "$FAILS" "$BLOCKED" "$WARNS" "$CHECKPOINTS" "${TARGET:-unknown}" "$MODE" \
    >"$OUT/summary.txt"
  seal_archive || { [ "$rc" -ne 0 ] || rc=4; }
  printf 'Evidence: %s\n' "$OUT"
  if [ -f "$ZIP" ]; then
    printf 'Archive: %s\n' "$ZIP"
    [ -f "$ZIP.sha256" ] && printf 'Archive hash: %s\n' "$ZIP.sha256"
  fi
  exit "$rc"
}

# shellcheck disable=SC2317 # Invoked indirectly by signal traps.
request_stop() {
  STOP_REQUESTED=1
  log "Stop requested; taking final checkpoint and sealing evidence"
}

trap finalize EXIT
trap request_stop INT TERM HUP

if ! have timeout || ! have sha256sum || ! have zip; then
  result FAIL prerequisite "Termux timeout, sha256sum and zip are required"
  exit 2
fi

if have su; then
  root_uid="$(timeout -k 1 4 su -c "PATH=$ANDROID_PATH; export PATH; id -u" 2>/dev/null | tail -n 1)"
  if [ "$root_uid" = 0 ]; then
    ROOT_OK=1
    result PASS root uid0
  else
    result BLOCKED root unavailable
  fi
else
  result BLOCKED root unavailable
fi

capture identity/date.txt date -Ins
capture identity/boot-id.txt /system/bin/cat /proc/sys/kernel/random/boot_id
capture identity/properties.txt /system/bin/getprop
capture identity/wm-size.txt /system/bin/wm size
capture identity/wm-density.txt /system/bin/wm density
capture identity/display.txt /system/bin/dumpsys display
capture identity/home.txt /system/bin/sh -c \
  '/system/bin/cmd package resolve-activity --brief --components --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1'
capture packages/launcher.txt /system/bin/dumpsys package "$LAUNCHER"
capture packages/dofun.txt /system/bin/dumpsys package "$DOFUN"

for package_name in app.organicmaps.incar com.google.android.apps.maps net.osmand net.osmand.plus com.sygic.aura; do
  capture "packages/$package_name.txt" /system/bin/dumpsys package "$package_name"
done

root_capture packages/launcher-prefs.xml \
  'cat /data/user/0/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>/dev/null || cat /data/data/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>/dev/null || true'
# shellcheck disable=SC2016 # Expanded by the target Android shell under su.
root_capture topway/state-initial.txt \
  'for p in persist.tw.forcepip sys.tw.forcepip sys.tw.forcepip.x sys.tw.forcepip.y sys.tw.forcepip.w sys.tw.forcepip.h sys.df.desktop sys.df.variety.theme.window; do printf "%s=" "$p"; getprop "$p"; done; for f in /data/tw/custom_pip_app_name /data/tw/navi_name; do printf "%s=" "$f"; cat "$f" 2>/dev/null || printf unreadable; printf "\n"; done'

prefs="$OUT/packages/launcher-prefs.xml"
if [ "$ROOT_OK" -eq 1 ] && [ -s "$prefs" ]; then
  TARGET="$(sed -n 's/.*<string name="app.navigation">\([^<]*\)<\/string>.*/\1/p' "$prefs" | tail -n 1)"
  MODE="$(sed -n 's/.*<string name="navigation.surface.mode">\([^<]*\)<\/string>.*/\1/p' "$prefs" | tail -n 1)"
  [ "$MODE" != raw_freeform ] || MODE=native_window
  if [ -z "$MODE" ]; then
    if grep -q '<boolean name="map.enabled" value="true"' "$prefs"; then MODE=leaflet; else MODE=native_window; fi
  fi
else
  result BLOCKED configuration "launcher preferences unreadable"
fi
if [ -z "$TARGET" ] && /system/bin/pm path app.organicmaps.incar >/dev/null 2>&1; then
  TARGET=app.organicmaps.incar
fi
if [ -z "$TARGET" ] && [ -n "$EXPECT_PACKAGE" ]; then TARGET="$EXPECT_PACKAGE"; fi
if [ -n "$TARGET" ] && ! valid_package "$TARGET"; then
  result FAIL selected-package "invalid value"
  TARGET=""
fi
printf '%s\n' "${TARGET:-unknown}" >"$OUT/selected-package.txt"
printf '%s\n' "$MODE" >"$OUT/selected-mode.txt"
if [ -n "$EXPECT_PACKAGE" ] && [ "$TARGET" != "$EXPECT_PACKAGE" ]; then
  result FAIL expected-package "expected=$EXPECT_PACKAGE actual=${TARGET:-unknown}"
fi
if [ -n "$TARGET" ]; then
  result PASS selected-package "$TARGET"
  capture helper/resolved-launch-component.txt /system/bin/sh -c \
    "/system/bin/cmd package resolve-activity --brief --components --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER '$TARGET' 2>&1"
else
  result FAIL selected-package unresolved
fi
result PASS selected-mode "$MODE"

capture identity/launcher-path.txt /system/bin/pm path "$LAUNCHER"
launcher_apk="$(sed -n 's/^package://p' "$OUT/identity/launcher-path.txt" | head -n 1)"
if [ -n "$launcher_apk" ]; then
  root_capture identity/launcher-apk-sha256.txt "sha256sum '$launcher_apk'"
  if have unzip; then
    timeout -k 2 "$CAPTURE_TIMEOUT" unzip -p "$launcher_apk" assets/nav/nav-window.sh \
      | sha256sum >"$OUT/helper/packaged-sha256.txt" 2>&1 || true
  fi
fi
root_capture helper/staged.txt \
  'ls -lZ /data/adb/ts18-launcher/nav-window.sh 2>&1; sha256sum /data/adb/ts18-launcher/nav-window.sh 2>&1'
root_capture helper/probe.txt "$HELPER probe"
if [ -n "$TARGET" ]; then root_capture helper/status-initial.txt "$HELPER status '$TARGET' 0"; fi

capture helper/am-help.txt /system/bin/am help
capture helper/cmd-activity-help.txt /system/bin/cmd activity help
# shellcheck disable=SC2016 # Expanded by the target Android shell under su.
root_capture root/magisk-lsposed-metadata.txt \
  'magisk -v 2>&1 || true; printf "modules:\n"; for d in /data/adb/modules/*; do [ -d "$d" ] && basename "$d"; done; ls -ldZ /data/adb/lspd /data/adb/modules 2>&1 || true'
# shellcheck disable=SC2016 # Expanded by the target Android shell under su.
root_capture framework/classpaths.txt \
  'printf "BOOTCLASSPATH=%s\n" "$BOOTCLASSPATH"; printf "SYSTEMSERVERCLASSPATH=%s\n" "$SYSTEMSERVERCLASSPATH"; for list in "$BOOTCLASSPATH" "$SYSTEMSERVERCLASSPATH"; do oldifs=$IFS; IFS=:; for f in $list; do [ -f "$f" ] || continue; printf "%s\t" "$f"; wc -c <"$f"; sha256sum "$f"; done; IFS=$oldifs; done'
# shellcheck disable=SC2016 # Expanded by the target Android shell under su.
root_capture framework/system-server-maps.txt \
  'pid="$(pidof system_server 2>/dev/null)"; printf "pid=%s\n" "$pid"; [ -n "$pid" ] && cat "/proc/$pid/maps" 2>/dev/null || true'
# shellcheck disable=SC2016 # Expanded by the target Android shell under su.
root_capture framework/anchor-strings.txt \
  'for list in "$BOOTCLASSPATH" "$SYSTEMSERVERCLASSPATH"; do oldifs=$IFS; IFS=:; for f in $list; do case "$f" in */framework.jar|*/services.jar) ;; *) continue ;; esac; [ -f "$f" ] || continue; printf "===== %s =====\n" "$f"; if command -v strings >/dev/null 2>&1; then strings "$f" 2>/dev/null; else cat "$f" 2>/dev/null; fi | grep -Ei "isPipLauncher|forcepip|custom_pip_app_name|navi_name|sendNaviType|tw_navi|windowingMode" | head -n 600; done; IFS=$oldifs; done'

export_limit=$((MAX_EXPORT_MIB * 1024 * 1024))
export_total=0
# shellcheck disable=SC2016 # Expanded by the target Android shell under su.
root_capture framework/export-candidates.txt \
  'for list in "$BOOTCLASSPATH" "$SYSTEMSERVERCLASSPATH"; do oldifs=$IFS; IFS=:; for f in $list; do case "$f" in */framework.jar|*/services.jar|*/framework-minus-apex.jar) [ -f "$f" ] && printf "%s\n" "$f" ;; esac; done; IFS=$oldifs; done; for p in com.cbkii.ts18launcher com.dofun.variety com.tw.service com.tw.service.xt; do pm path "$p" 2>/dev/null | sed "s/^package://"; done'
sort -u "$OUT/framework/export-candidates.txt" >"$WORK/export-candidates.txt"
while IFS= read -r source_path; do
  case "$source_path" in
    /system/*|/system_ext/*|/product/*|/vendor/*|/apex/*|/data/app/*) ;;
    *) continue ;;
  esac
  size="$(timeout -k 1 4 su -c "PATH=$ANDROID_PATH; export PATH; wc -c <'$source_path'" 2>/dev/null | tail -n 1)"
  case "$size" in ''|*[!0-9]*) continue ;; esac
  if [ $((export_total + size)) -gt "$export_limit" ]; then
    printf 'SKIPPED_LIMIT\t%s\t%s\n' "$size" "$source_path" >>"$OUT/framework/export-index.tsv"
    continue
  fi
  safe_name="$(printf '%s' "$source_path" | tr '/:' '__')"
  destination="$OUT/framework/bytes/$safe_name"
  if timeout -k 2 "$CAPTURE_TIMEOUT" su -c "PATH=$ANDROID_PATH; export PATH; cat '$source_path'" \
      >"$destination" 2>/dev/null && [ "$(wc -c <"$destination")" = "$size" ]; then
    export_total=$((export_total + size))
    printf 'EXPORTED\t%s\t%s\t%s\n' "$size" "$source_path" "$safe_name" >>"$OUT/framework/export-index.tsv"
  else
    rm -f "$destination"
    printf 'BLOCKED\t%s\t%s\n' "$size" "$source_path" >>"$OUT/framework/export-index.tsv"
  fi
done <"$WORK/export-candidates.txt"

if have logcat; then
  logcat -v threadtime \
    | grep -Ei 'TS18Nav|isPipLauncher|sendNaviType|tw_navi|forcepip|windowingMode|ActivityTaskManager|ActivityManager|WindowManager|dofun|organicmaps|osmand|sygic|com.cbkii.ts18launcher' \
    >"$OUT/logs/live-relevant.txt" 2>&1 &
  LOG_PID=$!
else
  result BLOCKED logcat unavailable
fi

take_checkpoint() {
  reason="$1"
  if [ "$CHECKPOINTS" -ge "$MAX_CHECKPOINTS" ]; then
    [ "$CHECKPOINTS" -ne "$MAX_CHECKPOINTS" ] || result WARN checkpoints "capture limit reached"
    CHECKPOINTS=$((CHECKPOINTS + 1))
    return 0
  fi
  CHECKPOINTS=$((CHECKPOINTS + 1))
  index="$(printf '%03d' "$CHECKPOINTS")"
  cp "$WORK/activity.txt" "$OUT/window/checkpoints/$index-activity.txt"
  printf '%s\n' "$reason" >"$OUT/window/checkpoints/$index-reason.txt"
  root_capture "window/checkpoints/$index-window.txt" 'dumpsys window windows'
  root_capture "window/checkpoints/$index-display.txt" 'dumpsys window displays'
  if [ -n "$TARGET" ]; then root_capture "helper/$index-status.txt" "$HELPER status '$TARGET' 0"; fi
  if [ "$ROOT_OK" -eq 1 ]; then
    timeout -k 2 5 su -c "PATH=$ANDROID_PATH; export PATH; screencap -p" \
      >"$OUT/screens/$index.png" 2>/dev/null || rm -f "$OUT/screens/$index.png"
  fi
  log "CHECKPOINT $index $reason"
}

sample_activity() {
  if [ "$ROOT_OK" -eq 1 ]; then
    timeout -k 2 "$CAPTURE_TIMEOUT" su -c \
      "PATH=$ANDROID_PATH; export PATH; dumpsys activity activities" >"$WORK/activity.txt" 2>&1
  else
    timeout -k 2 "$CAPTURE_TIMEOUT" /system/bin/dumpsys activity activities \
      >"$WORK/activity.txt" 2>&1
  fi
  [ -s "$WORK/activity.txt" ] || return 1
  if [ -n "$TARGET" ]; then
    signature="$(grep -E "Display #|Stack #|RootTask #|Task id #|TaskRecord|Hist #0|mBounds=|windowingMode|mode=|mResumedActivity|topResumedActivity|$LAUNCHER|$TARGET" \
      "$WORK/activity.txt" | sha256sum | awk '{print $1}')"
  else
    signature="$(grep -E 'Display #|Stack #|RootTask #|Task id #|TaskRecord|Hist #0|mBounds=|windowingMode|mode=|mResumedActivity|topResumedActivity' \
      "$WORK/activity.txt" | sha256sum | awk '{print $1}')"
  fi
  if [ "$signature" != "$LAST_SIGNATURE" ]; then
    LAST_SIGNATURE="$signature"
    take_checkpoint state-change
  fi
  return 0
}

if ! sample_activity; then result FAIL activity-state unreadable; fi
cat >"$OUT/OBSERVATION_READY.txt" <<EOF
OBSERVATION READY

Perform docs/NAVIGATION_WINDOW_PHYSICAL_PLAYBOOK.md at your own pace.
Do not run other diagnostic or window-mutation commands in parallel.
Press Ctrl-C once when the ordinary UI actions are complete.
EOF
log "OBSERVATION READY — perform the playbook, then press Ctrl-C once"

while [ "$STOP_REQUESTED" -eq 0 ]; do
  sleep 1
  [ "$STOP_REQUESTED" -eq 0 ] || break
  sample_activity || result WARN activity-sample unreadable
done

sample_activity || true
if [ -s "$WORK/activity.txt" ]; then
  cp "$WORK/activity.txt" "$OUT/window/final-activity.txt"
fi
root_capture window/final-window.txt 'dumpsys window windows'
root_capture window/final-display.txt 'dumpsys window displays'
if [ -n "$TARGET" ]; then root_capture helper/status-final.txt "$HELPER status '$TARGET' 0"; fi
root_capture window/final-surfaceflinger.txt 'dumpsys SurfaceFlinger --list'
# shellcheck disable=SC2016 # Expanded by the target Android shell under su.
root_capture topway/state-final.txt \
  'for p in persist.tw.forcepip sys.tw.forcepip sys.tw.forcepip.x sys.tw.forcepip.y sys.tw.forcepip.w sys.tw.forcepip.h sys.df.desktop sys.df.variety.theme.window; do printf "%s=" "$p"; getprop "$p"; done; for f in /data/tw/custom_pip_app_name /data/tw/navi_name; do printf "%s=" "$f"; cat "$f" 2>/dev/null || printf unreadable; printf "\n"; done'
stop_log
capture logs/relevant-tail.txt /system/bin/sh -c \
  "/system/bin/logcat -d -v threadtime -t 2500 2>&1 | /system/bin/grep -Ei 'TS18Nav|isPipLauncher|sendNaviType|tw_navi|forcepip|windowingMode|ActivityTaskManager|ActivityManager|WindowManager|dofun|organicmaps|osmand|sygic|com.cbkii.ts18launcher'"

cat >"$OUT/README.txt" <<EOF
Read-only TS18 native-navigation-window evidence.

Target: ${TARGET:-unknown}
Stored mode: $MODE
Checkpoints: $CHECKPOINTS (created only when the relevant task/window signature changed)

No Activity/task/window/input/settings/package/Topway mutation is performed by this collector.
Window transitions in the archive came from the TESTING launcher or ordinary user UI actions.
Missing, denied or truncated evidence is UNKNOWN/BLOCKED, not proof of absence.
Framework/APK exports are exact bytes from the user-owned unit and are bounded to $MAX_EXPORT_MIB MiB.
Do not commit device exports, logs, identifiers or proprietary binaries to the repository.
EOF

if [ "$FAILS" -gt 0 ]; then exit 2; fi
exit 0
