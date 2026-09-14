#!/data/data/com.termux/files/usr/bin/bash
# PR #11 exact-TS18 navigation-surface qualification.
# Non-interactive apart from the physical reverse phase; stationary use only.
set -u

OUT_BASE=/storage/emulated/0/Download
EXPECT_PACKAGE=""
PHASE=baseline
CAP_TIMEOUT=8
SETTLE=3
LAUNCHER=com.cbkii.ts18launcher
DOFUN=com.dofun.variety
HELPER=/data/adb/ts18-launcher/nav-window.sh
FAILS=0
BLOCKED=0
WARNS=0
LIVE_PID=""
ROOT_OK=0
HOME_OK=0
TASK_STATE_OK=0
GESTURE_BOUNDS=""

usage() {
  echo "Usage: $0 [--expect-package PKG] [--phase baseline|reverse|post-reboot|post-coldboot|post-acc] [--out-base DIR]"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --expect-package) EXPECT_PACKAGE="${2:-}"; shift 2 ;;
    --phase) PHASE="${2:-}"; shift 2 ;;
    --out-base) OUT_BASE="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 64 ;;
  esac
done

case "$PHASE" in
  baseline|reverse|post-reboot|post-coldboot|post-acc) ;;
  *) exit 64 ;;
esac
case "$EXPECT_PACKAGE" in
  '') ;;
  *[!A-Za-z0-9._]*) exit 64 ;;
esac

STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf unknown)"
OUT="$OUT_BASE/TS18-PR11-nav-$PHASE-$STAMP"
ZIP="$OUT.zip"
mkdir -p "$OUT"/{identity,packages,window,logs,screens,topway} || exit 1
printf 'time\tstatus\tcheck\tdetail\n' >"$OUT/results.tsv"

log() {
  printf '[%s] %s\n' "$(date +%H:%M:%S 2>/dev/null || printf --:--:--)" "$*" | tee -a "$OUT/run.log"
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
  timeout -k 2 "$CAP_TIMEOUT" "$@" >"$OUT/$rel" 2>&1
  rc=$?
  if [ "$rc" -ne 0 ]; then
    printf '\n[capture] exit=%s\n' "$rc" >>"$OUT/$rel"
  fi
  return 0
}

capture_sh() {
  rel="$1"
  shift
  capture "$rel" sh -c "$*"
}

root_capture() {
  rel="$1"
  shift
  mkdir -p "$(dirname "$OUT/$rel")"
  if [ "$ROOT_OK" -ne 1 ]; then
    echo 'BLOCKED: root unavailable' >"$OUT/$rel"
    return 0
  fi
  timeout -k 2 "$CAP_TIMEOUT" su -c "$*" >"$OUT/$rel" 2>&1
  rc=$?
  if [ "$rc" -ne 0 ]; then
    printf '\n[root capture] exit=%s\n' "$rc" >>"$OUT/$rel"
  fi
  return 0
}

screenshot() {
  name="$1"
  if [ "$ROOT_OK" -ne 1 ]; then
    return 0
  fi
  if ! timeout -k 2 5 su -c 'screencap -p' >"$OUT/screens/$name.png" 2>/dev/null; then
    rm -f "$OUT/screens/$name.png"
  fi
}

stop_log() {
  if [ -n "$LIVE_PID" ]; then
    kill "$LIVE_PID" 2>/dev/null || true
    wait "$LIVE_PID" 2>/dev/null || true
    LIVE_PID=""
  fi
}

# shellcheck disable=SC2317
finalize() {
  rc=$?
  trap - EXIT INT TERM HUP
  stop_log
  printf 'fails=%s\nblocked=%s\nwarns=%s\nphase=%s\n' \
    "$FAILS" "$BLOCKED" "$WARNS" "$PHASE" >"$OUT/summary.txt"

  if have sha256sum; then
    (cd "$OUT" && find . -type f ! -name SHA256SUMS.txt ! -name MANIFEST_VERIFY.txt -print0 \
      | sort -z | xargs -0 -r sha256sum) >"$OUT/SHA256SUMS.txt" 2>/dev/null || true
    if (cd "$OUT" && sha256sum -c SHA256SUMS.txt) >"$OUT/MANIFEST_VERIFY.txt" 2>&1; then
      printf '\nmanifest=PASS\n' >>"$OUT/summary.txt"
    else
      printf '\nmanifest=FAIL\n' >>"$OUT/summary.txt"
    fi
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
  printf 'Evidence: %s\n' "$OUT"
  if [ -f "$ZIP" ]; then
    printf 'Archive: %s\n' "$ZIP"
    [ -f "$ZIP.sha256" ] && printf 'Archive hash: %s\n' "$ZIP.sha256"
  fi
  exit "$rc"
}
trap finalize EXIT INT TERM HUP

if ! have timeout; then
  result FAIL prerequisite 'Termux timeout missing'
  exit 2
fi
if have su; then
  root_uid="$(timeout -k 1 4 su -c 'id -u' 2>/dev/null | tail -n 1)"
  if [ "$root_uid" = 0 ]; then
    ROOT_OK=1
    result PASS root uid0
  else
    result BLOCKED root unavailable
  fi
else
  result BLOCKED root unavailable
fi

if have logcat; then
  logcat -v threadtime -s TS18Nav:I ActivityTaskManager:I ActivityManager:I WindowManager:I \
    >"$OUT/logs/live.log" 2>&1 &
  LIVE_PID=$!
fi

capture identity/date.txt date -Ins
capture identity/wm-size.txt wm size
capture identity/wm-density.txt wm density
capture identity/home.txt sh -c 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1 || true'
capture identity/launcher-path.txt pm path "$LAUNCHER"
capture packages/launcher.txt dumpsys package "$LAUNCHER"
capture packages/dofun.txt dumpsys package "$DOFUN"
for pkg in app.organicmaps.incar com.google.android.apps.maps net.osmand net.osmand.plus com.sygic.aura; do
  capture "packages/$pkg.txt" dumpsys package "$pkg"
done

home_component="$(grep -E '^[A-Za-z0-9._]+/' "$OUT/identity/home.txt" | tail -n 1)"
case "$home_component" in
  "$LAUNCHER"/*)
    HOME_OK=1
    result PASS home-authority "$home_component"
    ;;
  *) result BLOCKED home-authority "expected=$LAUNCHER actual=${home_component:-unknown}" ;;
esac

launcher_apk="$(sed -n 's/^package://p' "$OUT/identity/launcher-path.txt" | head -n 1)"
if [ -n "$launcher_apk" ] && have sha256sum; then
  capture_sh identity/launcher-apk-sha256.txt "sha256sum '$launcher_apk' 2>&1 || true"
fi

# These strings intentionally expand only inside the root shell.
# shellcheck disable=SC2016
root_capture packages/launcher-prefs.xml 'cat /data/user/0/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>/dev/null || cat /data/data/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml 2>/dev/null || true'
# shellcheck disable=SC2016
root_capture topway/state.txt 'for p in persist.tw.forcepip sys.tw.forcepip sys.tw.forcepip.x sys.tw.forcepip.y sys.tw.forcepip.w sys.tw.forcepip.h sys.df.desktop sys.df.variety.theme.window; do printf "%s=" "$p"; getprop "$p"; done; for f in /data/tw/custom_pip_app_name /data/tw/navi_name; do printf "%s=" "$f"; cat "$f" 2>/dev/null || printf unreadable; printf "\n"; done'

TARGET=""
TARGET_SOURCE=unresolved
MODE=unknown
prefs="$OUT/packages/launcher-prefs.xml"
if [ "$ROOT_OK" -eq 1 ] && [ -s "$prefs" ]; then
  found="$(sed -n 's/.*<string name="app.navigation">\([^<]*\)<\/string>.*/\1/p' "$prefs" | tail -n 1)"
  if [ -n "$found" ]; then
    TARGET="$found"
    TARGET_SOURCE=configured
  fi
  foundmode="$(sed -n 's/.*<string name="navigation.surface.mode">\([^<]*\)<\/string>.*/\1/p' "$prefs" | tail -n 1)"
  if [ -n "$foundmode" ]; then
    MODE="$foundmode"
  elif grep -q '<boolean name="map.enabled" value="true"' "$prefs"; then
    MODE=leaflet
  else
    MODE=fullscreen
  fi
else
  result BLOCKED configuration-authority 'launcher preferences not readable'
fi
if [ -z "$TARGET" ] && pm path app.organicmaps.incar >/dev/null 2>&1; then
  TARGET=app.organicmaps.incar
  TARGET_SOURCE=implicit-organicmaps-fallback
fi
if [ -z "$TARGET" ] && [ -n "$EXPECT_PACKAGE" ]; then
  TARGET="$EXPECT_PACKAGE"
  TARGET_SOURCE=argument-only
fi
printf '%s\n' "$TARGET" >"$OUT/selected-package.txt"
printf '%s\n' "$TARGET_SOURCE" >"$OUT/selected-package-source.txt"
printf '%s\n' "$MODE" >"$OUT/selected-mode.txt"
if [ -n "$TARGET" ]; then
  result PASS selected-package "$TARGET source=$TARGET_SOURCE"
else
  result FAIL selected-package none
fi
result PASS selected-mode "$MODE"
if [ -n "$EXPECT_PACKAGE" ] && [ "$TARGET" != "$EXPECT_PACKAGE" ]; then
  result FAIL expected-package "expected=$EXPECT_PACKAGE actual=$TARGET"
fi

if [ "$ROOT_OK" -eq 1 ] && [ "$HOME_OK" -eq 1 ]; then
  timeout -k 2 5 su -c 'input keyevent 3' >/dev/null 2>&1 || true
else
  result BLOCKED home-input 'HOME/root authority not established; no synthetic HOME input sent'
fi
sleep "$SETTLE"
screenshot home
capture window/activity-home.txt dumpsys activity activities
capture window/windows-home.txt dumpsys window windows
capture_sh window/focus-home.txt "dumpsys activity activities 2>&1 | grep -Ei 'mResumedActivity|topResumedActivity|TaskRecord|windowingMode|mBounds|$LAUNCHER|$TARGET' || true"

if [ "$MODE" = raw_freeform ] || [ "$MODE" = android_pip ]; then
  if [ "$ROOT_OK" -ne 1 ]; then
    result BLOCKED helper-status root-unavailable
  elif [ ! -x "$HELPER" ]; then
    result FAIL helper-status "helper not staged: $HELPER"
  elif [ -z "$TARGET" ]; then
    result FAIL helper-status target-unresolved
  else
    root_capture window/helper-status.txt "$HELPER status $TARGET 0"
    line="$(grep -E '^(OK|FAIL) ' "$OUT/window/helper-status.txt" | tail -n 1)"
    case "$line" in
      OK*)
        wm="$(printf '%s\n' "$line" | sed -n 's/.* windowingMode=\([^ ]*\).*/\1/p')"
        display="$(printf '%s\n' "$line" | sed -n 's/.* display=\([^ ]*\).*/\1/p')"
        component="$(printf '%s\n' "$line" | sed -n 's/.* component=\([^ ]*\).*/\1/p')"
        bounds="$(printf '%s\n' "$line" | sed -n 's/.* bounds=\([^ ]*\).*/\1/p')"
        expected=5
        if [ "$MODE" = android_pip ]; then expected=2; fi
        if [ "$display" = 0 ] && [ "$wm" = "$expected" ] && printf '%s' "$component" | grep -q "^$TARGET/"; then
          TASK_STATE_OK=1
          GESTURE_BOUNDS="$bounds"
          result PASS task-state "mode=$wm display=$display component=$component bounds=$bounds"
        else
          result FAIL task-state "$line"
        fi
        ;;
      FAIL*) result FAIL helper-status "$line" ;;
      *) result FAIL helper-status no-protocol-line ;;
    esac
  fi
else
  result PASS task-state "mode=$MODE has no foreign task requirement"
fi

if [ "$ROOT_OK" -eq 1 ] && [ "$HOME_OK" -eq 1 ] && [ "$TASK_STATE_OK" -eq 1 ]; then
  oldifs=$IFS
  IFS=,
  read -r left top right bottom <<EOF_BOUNDS
$GESTURE_BOUNDS
EOF_BOUNDS
  IFS=$oldifs
  case "$left:$top:$right:$bottom" in
    *[!0-9:]*|:::*) result WARN map-gesture "unparseable bounds=$GESTURE_BOUNDS" ;;
    *)
      if [ "$right" -gt "$left" ] && [ "$bottom" -gt "$top" ]; then
        x=$(((left + right) / 2))
        y=$(((top + bottom) / 2))
        x2=$((x + 50))
        if [ "$x2" -ge "$right" ]; then x2=$((x - 50)); fi
        screenshot before-map-input
        if timeout -k 2 5 su -c "input swipe $x $y $x2 $y 250" >/dev/null 2>&1; then
          result INFO map-gesture "input dispatched inside confirmed bounds=$GESTURE_BOUNDS; compare screenshots"
        else
          result WARN map-gesture failed
        fi
        sleep 1
        screenshot after-map-input
      else
        result WARN map-gesture "invalid bounds=$GESTURE_BOUNDS"
      fi
      ;;
  esac
fi

if [ "$PHASE" = reverse ]; then
  result INFO reverse 'engage/release reverse during next 20 seconds'
  sleep 20
  screenshot post-reverse
fi

capture window/activity-final.txt dumpsys activity activities
capture window/windows-final.txt dumpsys window windows
capture_sh logs/relevant.txt "logcat -d -v threadtime -t 1600 2>&1 | grep -Ei 'TS18Nav|isPipLauncher|sendNaviType|forcepip|$LAUNCHER|$TARGET|ActivityTaskManager|WindowManager' || true"
stop_log

cat >"$OUT/README.txt" <<EOF
PR #11 navigation-surface evidence: mode=$MODE target=$TARGET target_source=$TARGET_SOURCE phase=$PHASE
Raw freeform machine-state PASS requires mode 5; Android PiP machine-state PASS requires mode 2. Neither proves the HOME goal.
Confirm map visible in intended rectangle, HOME visible, map input, launcher input outside it, app-drawer round trip, fullscreen/HOME coherence and unrelated-task isolation.
PiP may legitimately prove glance-only. BLOCKED is dependency-blocked, not absence. Topway state is read only.
EOF

if [ "$FAILS" -gt 0 ]; then exit 2; fi
if [ "$BLOCKED" -gt 0 ]; then exit 3; fi
exit 0
