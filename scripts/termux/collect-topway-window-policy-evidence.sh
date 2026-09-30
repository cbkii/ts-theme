#!/data/data/com.termux/files/usr/bin/bash
# Read-only collector for unresolved Topway/DoFun navigation-window policy.
# Framework jars are discovered from runtime classpaths; no protected path is hard-coded.
set -u

OUT_BASE=/storage/emulated/0/Download
OBSERVE=25
CAP_TIMEOUT=10
LIVE_PID=""
ROOT_OK=0

usage() {
  echo "Usage: $0 [--observe-seconds 5..120] [--out-base DIR]"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out-base) OUT_BASE="${2:-}"; shift 2 ;;
    --observe-seconds) OBSERVE="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 64 ;;
  esac
done
case "$OBSERVE" in ''|*[!0-9]*) exit 64 ;; esac
[ "$OBSERVE" -ge 5 ] && [ "$OBSERVE" -le 120 ] || exit 64

STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf unknown)"
OUT="$OUT_BASE/TS18-topway-window-policy-$STAMP"
ZIP="$OUT.zip"
mkdir -p "$OUT"/{framework,topway,logs,window} || exit 1
printf 'status\tdetail\n' >"$OUT/status.tsv"

status() {
  printf '%s\t%s\n' "$1" "$2" >>"$OUT/status.tsv"
}

cap() {
  rel="$1"
  shift
  timeout -k 2 "$CAP_TIMEOUT" "$@" >"$OUT/$rel" 2>&1
  rc=$?
  if [ "$rc" -ne 0 ]; then
    printf '\nexit=%s\n' "$rc" >>"$OUT/$rel"
  fi
  return 0
}

rootcap() {
  rel="$1"
  shift
  if [ "$ROOT_OK" -ne 1 ]; then
    echo BLOCKED >"$OUT/$rel"
    return 0
  fi
  timeout -k 2 "$CAP_TIMEOUT" su -c "$*" >"$OUT/$rel" 2>&1
  rc=$?
  if [ "$rc" -ne 0 ]; then
    printf '\nexit=%s\n' "$rc" >>"$OUT/$rel"
  fi
  return 0
}

stop_log() {
  if [ -n "$LIVE_PID" ]; then
    kill "$LIVE_PID" 2>/dev/null || true
    wait "$LIVE_PID" 2>/dev/null || true
    LIVE_PID=""
  fi
}

finalize() {
  rc=$?
  manifest_failed=0
  trap - EXIT INT TERM HUP
  stop_log
  if command -v sha256sum >/dev/null 2>&1; then
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
    printf 'BLOCKED: sha256sum unavailable\n' >"$OUT/MANIFEST_VERIFY.txt"
  fi
  rm -f "$ZIP" "$ZIP.sha256"
  if command -v zip >/dev/null 2>&1; then
    parent="$(dirname "$OUT")"
    base="$(basename "$OUT")"
    (cd "$parent" && find "$base" -type f -print | LC_ALL=C sort | zip -q -X "$ZIP" -@) || true
  fi
  if [ -f "$ZIP" ] && command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$ZIP" >"$ZIP.sha256" 2>/dev/null || true
  fi
  printf '%s\n' "$ZIP"
  if [ "$manifest_failed" -ne 0 ] && [ "$rc" -eq 0 ]; then
    rc=4
  fi
  exit "$rc"
}
trap finalize EXIT INT TERM HUP

if ! command -v timeout >/dev/null 2>&1; then
  status FAIL timeout-missing
  exit 2
fi
if command -v su >/dev/null 2>&1; then
  root_uid="$(timeout -k 1 4 su -c 'id -u' 2>/dev/null | tail -n 1)"
  if [ "$root_uid" = 0 ]; then
    ROOT_OK=1
    status PASS root-uid0
  else
    status BLOCKED root-unavailable
  fi
else
  status BLOCKED root-unavailable
fi

# These command strings intentionally expand their variables in the child/root shell.
# shellcheck disable=SC2016
rootcap framework/classpaths.txt '
printf "BOOTCLASSPATH=%s\n" "$BOOTCLASSPATH"
printf "SYSTEMSERVERCLASSPATH=%s\n" "$SYSTEMSERVERCLASSPATH"
for list in "$BOOTCLASSPATH" "$SYSTEMSERVERCLASSPATH"; do
  oldifs=$IFS; IFS=:
  for f in $list; do
    case "$f" in */framework.jar|*/services.jar)
      [ -f "$f" ] || continue
      ls -l "$f"
      sha256sum "$f" 2>&1
      ;;
    esac
  done
  IFS=$oldifs
done'

# shellcheck disable=SC2016
rootcap topway/properties-files.txt '
for p in persist.tw.forcepip sys.tw.forcepip sys.tw.forcepip.x sys.tw.forcepip.y sys.tw.forcepip.w sys.tw.forcepip.h sys.df.desktop sys.df.variety.theme.window; do
  printf "%s=" "$p"; getprop "$p"
done
for f in /data/tw/custom_pip_app_name /data/tw/navi_name; do
  printf "%s=" "$f"; cat "$f" 2>/dev/null || printf unreadable; printf "\n"
done'

# shellcheck disable=SC2016
cap topway/packages.txt sh -c '
for p in com.dofun.variety com.tw.service.xt com.cbkii.ts18launcher app.organicmaps.incar; do
  echo "===== $p ====="
  dumpsys package "$p" 2>&1 | grep -Ei "versionName|versionCode|userId=|codePath=|primaryCpuAbi|supportsPictureInPicture|resizeable" || true
done'

cap window/before-activity.txt dumpsys activity activities
cap window/before-window.txt dumpsys window windows

# shellcheck disable=SC2016
rootcap framework/anchor-strings.txt '
for list in "$BOOTCLASSPATH" "$SYSTEMSERVERCLASSPATH"; do
  oldifs=$IFS; IFS=:
  for f in $list; do
    case "$f" in */framework.jar|*/services.jar)
      [ -f "$f" ] || continue
      echo "===== $f ====="
      if command -v strings >/dev/null 2>&1; then
        strings "$f" 2>/dev/null | grep -Ei "isPipLauncher|forcepip|custom_pip_app_name|navi_name|sendNaviType|tw_navi|windowingMode" | head -n 500
      else
        grep -aE "isPipLauncher|forcepip|custom_pip_app_name|navi_name|sendNaviType|tw_navi|windowingMode" "$f" 2>/dev/null | head -n 200
      fi
      ;;
    esac
  done
  IFS=$oldifs
done'

# Keep the live collector narrow: only system/window/navigation lines needed to discriminate the
# Topway policy. Do not persist an unrestricted device log stream.
logcat -v threadtime \
  | grep -Ei 'isPipLauncher|sendNaviType|forcepip|windowingMode|ActivityTaskManager|WindowManager|dofun|organicmaps|tw.service.xt|TS18Nav' \
  >"$OUT/logs/live-relevant.txt" 2>&1 &
LIVE_PID=$!
printf 'During the next %s seconds, exercise one known-good DoFun HOME navigation window normally.\n' "$OBSERVE" >"$OUT/INSTRUCTIONS.txt"
sleep "$OBSERVE"
stop_log

cap window/after-activity.txt dumpsys activity activities
cap window/after-window.txt dumpsys window windows
tail -n 2500 "$OUT/logs/live-relevant.txt" >"$OUT/logs/relevant.txt" 2>/dev/null || true
printf '%s\n' 'Read-only evidence. Missing optimized-framework strings are UNKNOWN, not proof of absence. Do not infer an actuator from a correlated force-PIP property/file.' >"$OUT/README.txt"
