#!/data/data/com.termux/files/usr/bin/bash
# Read-only TS18 navigation-window collector.
# Layer 1 records focused task/window/input/surface transitions during the physical playbook.
# Layer 2 separately records broad platform/Topway/DoFun evidence for alternative approaches.
# shellcheck disable=SC2016 # Literal snippets are expanded by the target Android shell under su.

OUT_BASE=/storage/emulated/0/Download
EXPECT_PACKAGE=""
CAPTURE_TIMEOUT=10
DISCOVERY_TIMEOUT=24
POLL_SECONDS=2
MAX_CHECKPOINTS=80
MAX_EXPORT_MIB=192
RUN_DISCOVERY=1
LAUNCHER=com.cbkii.ts18launcher
DOFUN=com.dofun.variety
HELPER=/data/adb/ts18-launcher/nav-window.sh
ANDROID_PATH=/system/bin:/system/xbin:/vendor/bin
ROOT_OK=0
STOP_REQUESTED=0
FAILS=0
BLOCKED=0
WARNS=0
CHECKPOINTS=0
CHECKPOINT_LIMIT_REPORTED=0
SAMPLE_FAILURE_REPORTED=0
LAST_SIGNATURE=""
TARGET=""
MODE=unknown
WORK=""

usage() {
  printf '%s\n' \
    "Usage: $0 [--expect-package PKG] [--out-base DIR]" \
    "          [--max-export-mib 32..256] [--skip-discovery]" \
    "" \
    "Run from Termux while the standalone launcher is HOME. Perform the" \
    "physical playbook at your own pace, then press Ctrl-C once. By default" \
    "the archive contains a focused transition capture and a separate broad" \
    "read-only discovery capture."
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
    --skip-discovery)
      RUN_DISCOVERY=0
      shift
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
mkdir -p "$OUT"/{identity,packages,window/checkpoints,input/checkpoints,surface/checkpoints,logs,screens,topway,helper,root} || exit 1
mkdir -p "$OUT"/discovery/{context,framework,packages,services,topway,window,static/bytes,logs} || exit 1
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

capture_with_timeout() {
  seconds="$1"
  rel="$2"
  shift 2
  mkdir -p "$(dirname "$OUT/$rel")"
  timeout -k 2 "$seconds" "$@" >"$OUT/$rel" 2>&1
  rc=$?
  if [ "$rc" -ne 0 ]; then
    printf '\n[capture] exit=%s\n' "$rc" >>"$OUT/$rel"
  fi
  return 0
}

capture() {
  rel="$1"
  shift
  capture_with_timeout "$CAPTURE_TIMEOUT" "$rel" "$@"
}

root_capture_with_timeout() {
  seconds="$1"
  rel="$2"
  command_text="$3"
  mkdir -p "$(dirname "$OUT/$rel")"
  if [ "$ROOT_OK" -ne 1 ]; then
    printf 'BLOCKED: root unavailable\n' >"$OUT/$rel"
    return 0
  fi
  timeout -k 2 "$seconds" su -c "PATH=$ANDROID_PATH; export PATH; $command_text" \
    >"$OUT/$rel" 2>&1
  rc=$?
  if [ "$rc" -ne 0 ]; then
    printf '\n[root capture] exit=%s\n' "$rc" >>"$OUT/$rel"
  fi
  return 0
}

root_capture() {
  root_capture_with_timeout "$CAPTURE_TIMEOUT" "$1" "$2"
}

root_capture_long() {
  root_capture_with_timeout "$DISCOVERY_TIMEOUT" "$1" "$2"
}

# shellcheck disable=SC2317 # Invoked indirectly by the EXIT-trap finalizer.
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
  [ "$manifest_failed" -eq 0 ]
}

# shellcheck disable=SC2317 # Invoked indirectly by the EXIT trap.
finalize() {
  rc=$?
  trap - EXIT INT TERM HUP
  if [ -n "$WORK" ] && [ -d "$WORK" ]; then rm -rf -- "$WORK"; fi
  printf 'fails=%s\nblocked=%s\nwarns=%s\ncheckpoints=%s\ntarget=%s\nmode=%s\nbroad_discovery=%s\n' \
    "$FAILS" "$BLOCKED" "$WARNS" "$CHECKPOINTS" "${TARGET:-unknown}" "$MODE" \
    "$RUN_DISCOVERY" >"$OUT/summary.txt"
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
  log "Stop requested; taking final focused state, then broad discovery and archive sealing"
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

# shellcheck disable=SC2016 # Expanded by the target Android shell under su.
root_capture helper/am-help.txt \
  'am help 2>&1; rc=$?; printf "\nam_help_exit=%s\n" "$rc"'
# shellcheck disable=SC2016 # Expanded by the target Android shell under su.
root_capture helper/cmd-activity-help.txt \
  'cmd activity help 2>&1; rc=$?; printf "\ncmd_activity_help_exit=%s\n" "$rc"'

capture_state_file() {
  destination="$1"
  command_text="$2"
  if [ "$ROOT_OK" -eq 1 ]; then
    timeout -k 2 "$CAPTURE_TIMEOUT" su -c \
      "PATH=$ANDROID_PATH; export PATH; $command_text" >"$destination" 2>&1
  else
    timeout -k 2 "$CAPTURE_TIMEOUT" /system/bin/sh -c "$command_text" \
      >"$destination" 2>&1
  fi
}

take_checkpoint() {
  reason="$1"
  signature="$2"
  if [ "$CHECKPOINTS" -ge "$MAX_CHECKPOINTS" ]; then
    if [ "$CHECKPOINT_LIMIT_REPORTED" -eq 0 ]; then
      CHECKPOINT_LIMIT_REPORTED=1
      result WARN checkpoints "capture limit reached"
    fi
    return 0
  fi
  CHECKPOINTS=$((CHECKPOINTS + 1))
  index="$(printf '%03d' "$CHECKPOINTS")"
  cp "$WORK/activity.txt" "$OUT/window/checkpoints/$index-activity.txt"
  cp "$WORK/window.txt" "$OUT/window/checkpoints/$index-window.txt"
  cp "$WORK/input.txt" "$OUT/input/checkpoints/$index-input.txt"
  {
    printf 'reason=%s\n' "$reason"
    printf 'signature=%s\n' "$signature"
    date -Ins 2>/dev/null || date
  } >"$OUT/window/checkpoints/$index-metadata.txt"
  root_capture "window/checkpoints/$index-display.txt" 'dumpsys window displays'
  root_capture "surface/checkpoints/$index-list.txt" 'dumpsys SurfaceFlinger --list'
  surface_pattern="$LAUNCHER|$DOFUN|organicmaps|NavigationBar|StatusBar|SurfaceView|Task=|Bounds"
  if [ -n "$TARGET" ]; then surface_pattern="$surface_pattern|$TARGET"; fi
  root_capture "surface/checkpoints/$index-relevant.txt" \
    "dumpsys SurfaceFlinger 2>&1 | grep -Ei '$surface_pattern' | head -n 5000"
  if [ "$ROOT_OK" -eq 1 ]; then
    timeout -k 2 6 su -c "PATH=$ANDROID_PATH; export PATH; screencap -p" \
      >"$OUT/screens/$index.png" 2>/dev/null || rm -f "$OUT/screens/$index.png"
  fi
  if [ -n "$TARGET" ]; then root_capture "helper/$index-status.txt" "$HELPER status '$TARGET' 0"; fi
  log "CHECKPOINT $index $reason"
}

sample_state() {
  activity_rc=0
  window_rc=0
  input_rc=0
  capture_state_file "$WORK/activity.next.txt" 'dumpsys activity activities' || activity_rc=$?
  capture_state_file "$WORK/window.next.txt" 'dumpsys window windows' || window_rc=$?
  capture_state_file "$WORK/input.next.txt" 'dumpsys input' || input_rc=$?
  if [ "$activity_rc" -ne 0 ] || [ "$window_rc" -ne 0 ] || [ "$input_rc" -ne 0 ] \
      || [ ! -s "$WORK/activity.next.txt" ] || [ ! -s "$WORK/window.next.txt" ] \
      || [ ! -s "$WORK/input.next.txt" ] \
      || grep -Eqi 'Permission Denial|not found|Can.t find service' \
        "$WORK/activity.next.txt" "$WORK/window.next.txt" "$WORK/input.next.txt"; then
    {
      printf 'activity_rc=%s window_rc=%s input_rc=%s\n' "$activity_rc" "$window_rc" "$input_rc"
      for next_file in "$WORK/activity.next.txt" "$WORK/window.next.txt" "$WORK/input.next.txt"; do
        printf '\n===== %s =====\n' "$(basename "$next_file")"
        head -n 80 "$next_file" 2>/dev/null || true
      done
    } >"$OUT/logs/focused-state-latest-error.txt"
    rm -f "$WORK/activity.next.txt" "$WORK/window.next.txt" "$WORK/input.next.txt"
    return 1
  fi
  mv "$WORK/activity.next.txt" "$WORK/activity.txt"
  mv "$WORK/window.next.txt" "$WORK/window.txt"
  mv "$WORK/input.next.txt" "$WORK/input.txt"
  if [ "$SAMPLE_FAILURE_REPORTED" -eq 1 ]; then
    SAMPLE_FAILURE_REPORTED=0
    result INFO focused-state "activity/window/input sampling recovered"
  fi

  : >"$WORK/signature.txt"
  if [ -n "$TARGET" ]; then
    grep -E "Display #|Stack #|RootTask #|Task id #|TaskRecord|Hist #0|mBounds=|windowingMode|mode=|mResumedActivity|topResumedActivity|mFocusedActivity|$LAUNCHER|$TARGET" \
      "$WORK/activity.txt" >>"$WORK/signature.txt" 2>/dev/null || true
    grep -E "mCurrentFocus|mFocusedApp|mTopFocusedDisplayId|Window #[0-9]+|mHasSurface|isOnScreen|mViewVisibility|mFrame=|mGivenTouchableRegion|InputChannel|$LAUNCHER|$TARGET" \
      "$WORK/window.txt" >>"$WORK/signature.txt" 2>/dev/null || true
    grep -E "FocusedApplication|FocusedWindow|TouchStates|displayId=|name=.*($LAUNCHER|$TARGET)|$LAUNCHER|$TARGET" \
      "$WORK/input.txt" >>"$WORK/signature.txt" 2>/dev/null || true
  else
    grep -E 'Display #|Stack #|RootTask #|Task id #|TaskRecord|Hist #0|mBounds=|windowingMode|mResumedActivity|topResumedActivity|mFocusedActivity' \
      "$WORK/activity.txt" >>"$WORK/signature.txt" 2>/dev/null || true
    grep -E 'mCurrentFocus|mFocusedApp|mTopFocusedDisplayId|Window #[0-9]+|mHasSurface|isOnScreen|mViewVisibility|mFrame=|mGivenTouchableRegion|InputChannel' \
      "$WORK/window.txt" >>"$WORK/signature.txt" 2>/dev/null || true
    grep -E 'FocusedApplication|FocusedWindow|TouchStates|displayId=' \
      "$WORK/input.txt" >>"$WORK/signature.txt" 2>/dev/null || true
  fi
  signature="$(sha256sum "$WORK/signature.txt" | awk '{print $1}')"
  if [ "$signature" != "$LAST_SIGNATURE" ]; then
    LAST_SIGNATURE="$signature"
    take_checkpoint state-change "$signature"
  fi
  return 0
}

run_broad_discovery() {
  result INFO broad-discovery "starting separate read-only platform capture"
  log "BROAD DISCOVERY START — no task/input/settings/Topway mutation"

  capture discovery/context/termux-context.txt /system/bin/sh -c \
    'id; cat /proc/self/attr/current 2>&1; readlink /proc/self/ns/mnt 2>&1; pwd; printf "PATH=%s\n" "$PATH"'
  # shellcheck disable=SC2016 # Expanded by the target Android shell under su.
  root_capture_long discovery/context/root-context.txt \
    'id; cat /proc/self/attr/current 2>&1; readlink /proc/self/ns/mnt 2>&1; getenforce 2>&1; printf "PATH=%s\n" "$PATH"'
  # shellcheck disable=SC2016 # Expanded by the target Android shell under su.
  root_capture_long discovery/context/system-server.txt \
    'pid="$(pidof system_server 2>/dev/null)"; printf "pid=%s\n" "$pid"; [ -n "$pid" ] || exit 0; ps -AZ | grep "[[:space:]]$pid[[:space:]]" || true; cat "/proc/$pid/attr/current" 2>&1; readlink "/proc/$pid/ns/mnt" 2>&1; cat "/proc/$pid/cmdline" 2>&1; printf "\n"'
  root_capture_long discovery/context/system-server-maps.txt \
    'pid="$(pidof system_server 2>/dev/null)"; [ -n "$pid" ] && cat "/proc/$pid/maps" 2>/dev/null || true'
  root_capture_long discovery/context/process-contexts.txt 'ps -AZ'
  root_capture_long discovery/context/binder-nodes.txt \
    'ls -lZ /dev/binder /dev/vndbinder /dev/hwbinder /dev/binderfs 2>&1 || true'
  root_capture_long discovery/context/magisk-lsposed-metadata.txt \
    'magisk -v 2>&1 || true; printf "modules:\n"; for d in /data/adb/modules/*; do [ -d "$d" ] && basename "$d"; done; ls -ldZ /data/adb/lspd /data/adb/modules 2>&1 || true'

  capture_with_timeout "$DISCOVERY_TIMEOUT" discovery/framework/features.txt /system/bin/pm list features
  capture_with_timeout "$DISCOVERY_TIMEOUT" discovery/framework/overlays.txt /system/bin/cmd overlay list --user 0
  capture_with_timeout "$DISCOVERY_TIMEOUT" discovery/framework/am-help.txt /system/bin/am help
  capture_with_timeout "$DISCOVERY_TIMEOUT" discovery/framework/cmd-activity-help.txt /system/bin/cmd activity help
  capture_with_timeout "$DISCOVERY_TIMEOUT" discovery/framework/wm-help.txt /system/bin/wm help
  capture_with_timeout "$DISCOVERY_TIMEOUT" discovery/framework/cmd-window-help.txt /system/bin/cmd window help
  root_capture_long discovery/framework/window-policy.txt 'dumpsys window policy'
  root_capture_long discovery/framework/display.txt 'dumpsys display'
  root_capture_long discovery/framework/resource-features.txt \
    'for f in /system/etc/permissions/*.xml /product/etc/permissions/*.xml /vendor/etc/permissions/*.xml; do [ -f "$f" ] || continue; grep -HiEi "freeform|multi.?window|picture.?in.?picture|pip|desktop" "$f" 2>/dev/null && printf "source=%s\n" "$f"; done'
  root_capture_long discovery/framework/windowing-settings.txt \
    'for namespace in global secure system; do printf "===== %s =====\n" "$namespace"; settings list "$namespace" 2>&1 | grep -Ei "freeform|multi.?window|desktop|pip|picture.?in.?picture|activity|task|overlay|display" || true; done'
  root_capture_long discovery/framework/relevant-properties.txt \
    'getprop | grep -Ei "tw|dofun|cardoor|window|freeform|multi.?window|desktop|pip|navi|display|theme" || true'

  root_capture_long discovery/services/service-list.txt 'service list'
  root_capture_long discovery/services/dumpsys-list.txt 'dumpsys -l'
  root_capture_long discovery/services/activity-services.txt 'dumpsys activity services'
  root_capture_long discovery/services/activity-containers.txt \
    'dumpsys activity containers 2>&1; printf "\nexit=%s\n" "$?"'

  discovery_packages="android com.android.systemui $LAUNCHER $DOFUN com.dofun.carsetting com.tw.service com.tw.service.xt com.tw.core com.tw.coreservice com.tw.video"
  if [ -n "$TARGET" ]; then discovery_packages="$discovery_packages $TARGET"; fi
  for package_name in $discovery_packages; do
    [ "$package_name" = android ] || valid_package "$package_name" || continue
    root_capture_long "discovery/packages/$package_name.txt" "dumpsys package '$package_name'"
  done
  root_capture_long discovery/packages/all-package-paths.txt \
    'pm list packages -f -U --show-versioncode 2>&1 || pm list packages -f -U 2>&1'
  root_capture_long discovery/packages/launcher-home-resolution.txt \
    'cmd package resolve-activity --brief --components --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1'
  if [ -n "$TARGET" ]; then
    root_capture_long discovery/packages/target-launch-resolution.txt \
      "cmd package resolve-activity --brief --components --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER '$TARGET' 2>&1"
  fi

  root_capture_long discovery/topway/data-inventory.txt \
    'for d in /data/tw /twdataconfig /data/user/0/com.dofun.variety /data/data/com.dofun.variety; do printf "===== %s =====\n" "$d"; ls -ldZ "$d" 2>&1 || continue; find "$d" -maxdepth 3 -print 2>&1 | head -n 6000; done'
  root_capture_long discovery/topway/dofun-registry-metadata.txt \
    'for d in /data/user/0/com.dofun.variety /data/data/com.dofun.variety; do [ -d "$d" ] || continue; find "$d" -maxdepth 4 -type f \( -name "plugins.xml" -o -name "overseas_variety.db" -o -name "*.jar" -o -name "*.apk" -o -name "*.lock" \) -print 2>/dev/null | while IFS= read -r f; do ls -lZ "$f" 2>&1; wc -c <"$f" 2>/dev/null; sha256sum "$f" 2>/dev/null; done; done'
  root_capture_long discovery/topway/relevant-files-properties.txt \
    'for p in persist.tw.forcepip sys.tw.forcepip sys.tw.forcepip.x sys.tw.forcepip.y sys.tw.forcepip.w sys.tw.forcepip.h sys.df.desktop sys.df.variety.theme.window; do printf "%s=" "$p"; getprop "$p"; done; for f in /data/tw/custom_pip_app_name /data/tw/navi_name; do printf "%s=" "$f"; cat "$f" 2>/dev/null || printf unreadable; printf "\n"; done'

  root_capture_long discovery/window/activity.txt 'dumpsys activity activities'
  root_capture_long discovery/window/recents.txt 'dumpsys activity recents'
  root_capture_long discovery/window/windows.txt 'dumpsys window windows'
  root_capture_long discovery/window/input.txt 'dumpsys input'
  root_capture_long discovery/window/surfaceflinger-list.txt 'dumpsys SurfaceFlinger --list'
  root_capture_long discovery/window/surfaceflinger-bounded.txt \
    'dumpsys SurfaceFlinger 2>&1 | head -c 12582912; printf "\n[bounded at 12 MiB if output was longer]\n"'

  # shellcheck disable=SC2016 # Expanded by the target Android shell under su.
  root_capture_long discovery/static/classpaths.txt \
    'printf "BOOTCLASSPATH=%s\n" "$BOOTCLASSPATH"; printf "SYSTEMSERVERCLASSPATH=%s\n" "$SYSTEMSERVERCLASSPATH"; for list in "$BOOTCLASSPATH" "$SYSTEMSERVERCLASSPATH"; do oldifs=$IFS; IFS=:; for f in $list; do [ -f "$f" ] || continue; printf "%s\t" "$f"; wc -c <"$f"; sha256sum "$f"; done; IFS=$oldifs; done'
  # shellcheck disable=SC2016 # Expanded by the target Android shell under su.
  root_capture_long discovery/static/anchor-strings.txt \
    'candidates=""; for list in "$BOOTCLASSPATH" "$SYSTEMSERVERCLASSPATH"; do candidates="$candidates:$list"; done; oldifs=$IFS; IFS=:; for f in $candidates; do [ -f "$f" ] || continue; case "$f" in */framework.jar|*/services.jar|*/framework-minus-apex.jar) ;; *) continue ;; esac; printf "===== %s =====\n" "$f"; if command -v strings >/dev/null 2>&1; then strings "$f" 2>/dev/null; else cat "$f" 2>/dev/null; fi | grep -Ei "isPipLauncher|forcepip|custom_pip_app_name|navi_name|sendNaviType|tw_navi|windowingMode|WINDOWING_MODE_FREEFORM|setLaunchWindowingMode|setLaunchBounds|TaskView|ActivityView|ShellTaskOrganizer|WindowContainerTransaction|VirtualDisplay|SurfaceControlViewHost|DESKTOP_WINDOW_SERVICE|DESKTOP_FLOATING_APP_SERVICE|FLOATING_WINDOW_SERVER|WindowInfo|RePlugin|PluginPit|freeform|multi.?window|picture.?in.?picture|pinned|desktop" | head -n 4000; done; IFS=$oldifs'

  export_limit=$((MAX_EXPORT_MIB * 1024 * 1024))
  export_total=0
  export_packages="com.android.systemui $LAUNCHER $DOFUN com.dofun.carsetting com.tw.service com.tw.service.xt com.tw.core com.tw.coreservice com.tw.video"
  if [ -n "$TARGET" ]; then export_packages="$export_packages $TARGET"; fi
  # shellcheck disable=SC2016 # Expanded by the target Android shell under su.
  root_capture_long discovery/static/export-candidates.txt \
    "for list in \"\$BOOTCLASSPATH\" \"\$SYSTEMSERVERCLASSPATH\"; do oldifs=\$IFS; IFS=:; for f in \$list; do case \"\$f\" in */framework.jar|*/services.jar|*/framework-minus-apex.jar) [ -f \"\$f\" ] && printf '%s\\n' \"\$f\" ;; esac; done; IFS=\$oldifs; done; [ -f /system/framework/framework-res.apk ] && printf '%s\\n' /system/framework/framework-res.apk; for p in $export_packages; do pm path \"\$p\" 2>/dev/null | sed 's/^package://'; done"
  printf 'status\tsize\tsha256\tsource\tstored_name\n' >"$OUT/discovery/static/export-index.tsv"
  # Preserve priority: framework/service jars first, then framework resources and OEM apps.
  # A lexical sort would put potentially large /data/app targets ahead of system evidence.
  awk 'NF && !seen[$0]++' "$OUT/discovery/static/export-candidates.txt" \
    >"$WORK/export-candidates.txt"
  while IFS= read -r source_path; do
    case "$source_path" in
      /system/*|/system_ext/*|/product/*|/vendor/*|/apex/*|/data/app/*) ;;
      *) continue ;;
    esac
    size="$(timeout -k 1 5 su -c "PATH=$ANDROID_PATH; export PATH; wc -c <'$source_path'" 2>/dev/null | tail -n 1)"
    case "$size" in ''|*[!0-9]*) continue ;; esac
    source_sha="$(timeout -k 1 12 su -c "PATH=$ANDROID_PATH; export PATH; sha256sum '$source_path'" 2>/dev/null | awk '{print $1}' | tail -n 1)"
    if [ $((export_total + size)) -gt "$export_limit" ]; then
      printf 'SKIPPED_LIMIT\t%s\t%s\t%s\t-\n' "$size" "${source_sha:-unknown}" "$source_path" \
        >>"$OUT/discovery/static/export-index.tsv"
      continue
    fi
    safe_name="$(printf '%s' "$source_path" | tr '/:' '__')"
    destination="$OUT/discovery/static/bytes/$safe_name"
    if timeout -k 2 "$DISCOVERY_TIMEOUT" su -c \
        "PATH=$ANDROID_PATH; export PATH; cat '$source_path'" >"$destination" 2>/dev/null \
        && [ "$(wc -c <"$destination")" = "$size" ]; then
      export_total=$((export_total + size))
      printf 'EXPORTED\t%s\t%s\t%s\t%s\n' "$size" "${source_sha:-unknown}" "$source_path" "$safe_name" \
        >>"$OUT/discovery/static/export-index.tsv"
    else
      rm -f "$destination"
      printf 'BLOCKED\t%s\t%s\t%s\t-\n' "$size" "${source_sha:-unknown}" "$source_path" \
        >>"$OUT/discovery/static/export-index.tsv"
    fi
  done <"$WORK/export-candidates.txt"

  capture_with_timeout "$DISCOVERY_TIMEOUT" discovery/logs/relevant-tail.txt /system/bin/sh -c \
    "/system/bin/logcat -d -v threadtime -t 12000 2>&1 | /system/bin/grep -Ei 'TS18Nav|isPipLauncher|sendNaviType|tw_navi|forcepip|freeform|windowingMode|ActivityTaskManager|ActivityManager|WindowManager|InputDispatcher|SurfaceFlinger|TaskView|ActivityView|dofun|cardoor|organicmaps|com.cbkii.ts18launcher'"

  : >"$OUT/discovery/CAPTURE_GAPS.txt"
  find "$OUT/discovery" -type f ! -name CAPTURE_GAPS.txt -print | while IFS= read -r gap_file; do
    if grep -Eq '^BLOCKED:|^\[(root )?capture\] exit=' "$gap_file" 2>/dev/null; then
      printf '%s\n' "$gap_file" >>"$OUT/discovery/CAPTURE_GAPS.txt"
    fi
  done
  capture_gaps="$(wc -l <"$OUT/discovery/CAPTURE_GAPS.txt" | tr -d ' ')"

  cat >"$OUT/discovery/APPROACH_MATRIX.txt" <<EOF
BROAD DISCOVERY ROUTES

This directory is deliberately separate from the focused physical validation.
It records evidence that may support or eliminate several implementation routes:

1. AOSP Android 10 freeform task: framework features, shell grammar, task/window/input/surface state.
2. OEM Topway navigation policy: DoFun/Topway packages, services, properties/files and log anchors.
3. System task embedding/organising: TaskView, ActivityView, ShellTaskOrganizer,
   WindowContainerTransaction, SurfaceControlViewHost and related framework anchors.
4. Virtual-display or mirroring approaches: VirtualDisplay and surface/input framework anchors.
5. Standard PiP: feature/config/package declarations, kept distinct from arbitrary-app navigation.
6. DoFun/RePlugin or desktop-window service contracts: manifest/service/provider and registry evidence.
7. Privilege feasibility: UID, SELinux context, Binder nodes, package identity and system-server classpaths.

No string, property, service name or installed feature alone proves that a route is callable or correct.
Missing or optimised-away strings are UNKNOWN, not evidence of absence. Exact exported bytes may be
analysed offline but must not be committed to the repository.
EOF
  if [ "$ROOT_OK" -ne 1 ]; then
    result BLOCKED broad-discovery \
      "non-root subset completed; capture_gaps=$capture_gaps exported_bytes=$export_total"
  elif [ "$capture_gaps" -gt 0 ]; then
    result WARN broad-discovery \
      "completed with capture_gaps=$capture_gaps; exported_bytes=$export_total limit_bytes=$export_limit"
  else
    result PASS broad-discovery \
      "completed; exported_bytes=$export_total limit_bytes=$export_limit"
  fi
}

if ! sample_state; then
  SAMPLE_FAILURE_REPORTED=1
  result FAIL focused-state "activity/window/input unreadable"
fi
cat >"$OUT/OBSERVATION_READY.txt" <<EOF
OBSERVATION READY

Perform docs/NAVIGATION_WINDOW_PHYSICAL_PLAYBOOK.md at your own pace.
Do not run other diagnostic or window-mutation commands in parallel.
Press Ctrl-C once when the ordinary UI actions are complete.

Focused checkpoints include ActivityTaskManager, WindowManager, InputDispatcher,
SurfaceFlinger and screenshots. A separate broad discovery capture runs afterwards.
EOF
log "OBSERVATION READY — perform the playbook, then press Ctrl-C once"

while [ "$STOP_REQUESTED" -eq 0 ]; do
  sleep "$POLL_SECONDS"
  [ "$STOP_REQUESTED" -eq 0 ] || break
  if ! sample_state && [ "$SAMPLE_FAILURE_REPORTED" -eq 0 ]; then
    SAMPLE_FAILURE_REPORTED=1
    result WARN focused-state "activity/window/input sample unreadable"
  fi
done

sample_state || true
if [ -s "$WORK/activity.txt" ]; then cp "$WORK/activity.txt" "$OUT/window/final-activity.txt"; fi
if [ -s "$WORK/window.txt" ]; then cp "$WORK/window.txt" "$OUT/window/final-window.txt"; fi
if [ -s "$WORK/input.txt" ]; then cp "$WORK/input.txt" "$OUT/input/final-input.txt"; fi
root_capture window/final-display.txt 'dumpsys window displays'
root_capture surface/final-surfaceflinger.txt 'dumpsys SurfaceFlinger --list'
if [ -n "$TARGET" ]; then root_capture helper/status-final.txt "$HELPER status '$TARGET' 0"; fi
# shellcheck disable=SC2016 # Expanded by the target Android shell under su.
root_capture topway/state-final.txt \
  'for p in persist.tw.forcepip sys.tw.forcepip sys.tw.forcepip.x sys.tw.forcepip.y sys.tw.forcepip.w sys.tw.forcepip.h sys.df.desktop sys.df.variety.theme.window; do printf "%s=" "$p"; getprop "$p"; done; for f in /data/tw/custom_pip_app_name /data/tw/navi_name; do printf "%s=" "$f"; cat "$f" 2>/dev/null || printf unreadable; printf "\n"; done'
capture logs/focused-relevant-tail.txt /system/bin/sh -c \
  "/system/bin/logcat -d -v threadtime -t 12000 2>&1 | /system/bin/grep -Ei 'TS18Nav|isPipLauncher|sendNaviType|tw_navi|forcepip|freeform|windowingMode|ActivityTaskManager|ActivityManager|WindowManager|InputDispatcher|SurfaceFlinger|dofun|organicmaps|com.cbkii.ts18launcher'"

if [ "$RUN_DISCOVERY" -eq 1 ]; then
  run_broad_discovery
else
  result INFO broad-discovery "skipped by explicit option"
fi

cat >"$OUT/README.txt" <<EOF
Read-only TS18 native-navigation-window evidence.

Target: ${TARGET:-unknown}
Stored mode: $MODE
Focused checkpoints: $CHECKPOINTS
Broad discovery: $RUN_DISCOVERY

The focused layer records full activity/window/input state, relevant SurfaceFlinger
state, helper readback and screenshots whenever its combined state signature changes.
It does not infer visibility or touch success merely from mode 5 and exact bounds.

The discovery/ layer separately records broad read-only framework, service, package,
DoFun, Topway, privilege and static-byte evidence so an unexpected or alternative
implementation route is not excluded by the current hypothesis.

No Activity/task/window/input/settings/package/Topway mutation is performed.
Window transitions came from the TESTING launcher or ordinary user UI actions.
Missing, denied, optimised-away or truncated evidence is UNKNOWN/BLOCKED, not absence.
Framework/APK exports are exact bytes from the user-owned unit and are bounded to
$MAX_EXPORT_MIB MiB. Do not commit device exports, logs, identifiers or proprietary
binaries to the repository.
EOF

if [ "$FAILS" -gt 0 ]; then exit 2; fi
exit 0
