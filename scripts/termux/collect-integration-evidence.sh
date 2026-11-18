#!/system/bin/sh
# TS18 integration evidence collector (integration review 2026-11-13, deferred items F2/F5/F7/F8,
# multi-resume and Maps own-screen hold). One guided, read-only, bounded run.
#
#   su -c 'sh /path/to/collect-integration-evidence.sh'            # guided (recommended)
#   su -c 'sh /path/to/collect-integration-evidence.sh --phase identity'
#
# Phases (run all in order unless --phase is given):
#   identity  installed APK identity (package, version, SHA-256), HOME, boot/unlock timing
#   bind      F2: why a cold Music bind fails. Run FIRST after a fresh boot, before touching Music.
#   play      F5: cold Play timing from tap to audible state (session PLAYING) and Auxio window
#   maps      F7/F8 + multi-resume: map stack visibility/focus around a cold Music Play
#   settings  F6: Maps' own Settings screen held without "Navigation unavailable"
#
# Read-only: never launches apps, sends media keys, clears logcat, changes settings or packages.
# Output: /storage/emulated/0/Download/ts18-integration-evidence/<UTC time>/ plus MANIFEST.sha256.
# Logs are filtered to the launcher, Auxio, Maps-task and framework service/session lines; no
# queue paths, coordinates or account data are requested.

set -u
PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin
unset LD_PRELOAD LD_LIBRARY_PATH

LAUNCHER=com.cbkii.ts18launcher
MUSIC=com.tw.media
MAPS=app.organicmaps.incar
PHASE=all
[ "${1:-}" = "--phase" ] && PHASE="${2:-all}"

if [ "$(id -u)" != 0 ]; then
  echo "Run as root: su -c 'sh $0 $*'" >&2
  exit 2
fi

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT=/storage/emulated/0/Download/ts18-integration-evidence/$STAMP
mkdir -p "$OUT" || { echo "Cannot create $OUT" >&2; exit 3; }
STATUS="$OUT/STATUS.txt"
: > "$STATUS"

# bounded CMD SECONDS FILE -- run a command with a hard timeout, recording BLOCKED on failure.
bounded() {
  secs=$1; file=$2; shift 2
  if command -v timeout >/dev/null 2>&1; then timeout "$secs" "$@" > "$file" 2>&1
  else "$@" > "$file" 2>&1; fi
  rc=$?
  [ $rc -eq 0 ] || echo "BLOCKED $(basename "$file") rc=$rc" >> "$STATUS"
  return 0
}

note() { echo "$*"; echo "$(date -u +%H:%M:%S) $*" >> "$OUT/TIMELINE.txt"; }

pause() {
  # $1 prompt, $2 max seconds (the step continues automatically when it elapses)
  note "STEP: $1"
  printf '%s\n> Press Enter when done (auto-continues in %ss): ' "$1" "$2"
  if command -v timeout >/dev/null 2>&1; then timeout "$2" sh -c 'read -r _' </dev/tty 2>/dev/null
  else read -r _ </dev/tty 2>/dev/null; fi
  echo
}

logcat_filtered() {
  # $1 output file. Main/system/events since boot, relevant tags or packages only.
  bounded 20 "$1.raw" logcat -b main,system,events -d -v threadtime
  grep -E "TS18Media|TS18Browser|TS18Nav|TS18Startup|MediaBrowser|MediaSessionService|MediaSessionRecord|ActivityManager|ActivityTaskManager|boot_progress|user_unlocked|am_proc_start|am_proc_died|am_kill|$LAUNCHER|$MUSIC|$MAPS|first-audio|HOME_PLAY|MediaSession transport" \
    "$1.raw" | grep -vE "ACCOUNT|password|token=" > "$1" 2>/dev/null
  rm -f "$1.raw"
}

snapshot_tasks() {
  # $1 label. Stack/task/focus/visibility facts for HOME, Maps and Auxio only.
  bounded 15 "$OUT/$1-activities.raw" dumpsys activity activities
  grep -E "Stack #|mResumedActivity|ResumedActivity|mFocusedApp|topActivity|realActivity|visible=|mode=|TaskRecord|$LAUNCHER|$MUSIC|$MAPS" \
    "$OUT/$1-activities.raw" > "$OUT/$1-activities.txt" 2>/dev/null
  rm -f "$OUT/$1-activities.raw"
  bounded 15 "$OUT/$1-windows.raw" dumpsys window windows
  grep -E -A4 "Window\{[^}]*($LAUNCHER|$MUSIC|$MAPS)" "$OUT/$1-windows.raw" \
    | grep -E "Window\{|isVisible|isOnScreen|mHasSurface|mViewVisibility|mFrame=|mCurrentFocus|mFocusedApp" \
    > "$OUT/$1-windows.txt" 2>/dev/null
  grep -E "mCurrentFocus|mFocusedApp" "$OUT/$1-windows.raw" >> "$OUT/$1-windows.txt" 2>/dev/null
  rm -f "$OUT/$1-windows.raw"
}

phase_identity() {
  note "identity"
  {
    echo "boot_id=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
    echo "uptime=$(cut -d' ' -f1 /proc/uptime)"
    echo "build=$(getprop ro.build.display.id)"
    echo "sdk=$(getprop ro.build.version.sdk)"
    echo "selinux=$(getenforce 2>/dev/null)"
    echo "sys.user.0.ce_available=$(getprop sys.user.0.ce_available)"
    for pkg in $LAUNCHER $MUSIC $MAPS; do
      path=$(pm path "$pkg" 2>/dev/null | head -n1 | cut -d: -f2)
      echo "[$pkg] path=${path:-NOT_INSTALLED}"
      [ -n "$path" ] && echo "[$pkg] sha256=$(sha256sum "$path" | cut -d' ' -f1)"
      dumpsys package "$pkg" 2>/dev/null | grep -E "versionName|versionCode|userId=|enabled=|stopped=" | head -6 | sed "s/^/[$pkg] /"
    done
    echo "home=$(cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN 2>/dev/null | tail -n1)"
  } > "$OUT/identity.txt" 2>&1
  bounded 10 "$OUT/identity-music-services.txt" dumpsys activity services $MUSIC
  bounded 10 "$OUT/identity-bad-processes.txt" sh -c "dumpsys activity processes | grep -iE 'bad|crash' | head -40"
}

phase_bind() {
  note "bind"
  echo "F2 bind: this phase is meaningful only on a FRESH BOOT before Music was touched."
  logcat_filtered "$OUT/bind-before-tap.log"
  bounded 10 "$OUT/bind-services-before.txt" dumpsys activity services $MUSIC
  pause "Tap the HOME Music Play button ONCE, then wait ~20 seconds (do not tap again)" 40
  logcat_filtered "$OUT/bind-after-tap.log"
  bounded 10 "$OUT/bind-services-after.txt" dumpsys activity services $MUSIC
  # Capture 2026-11-14: binds were refused with no ActivityManager line while Auxio was not
  # running; look for OEM/Unisoc start-control lines that name the package.
  grep -E "start-diagnostic|start-failed|start-offered|TS18Browser|onConnectFailed|Failed binding|Unable to start service|not found|bringUpService|process is bad|[Aa]uto[Ss]tart|associat|PowerController|forbid|restrict|denied|not allowed" \
    "$OUT/bind-after-tap.log" > "$OUT/bind-summary.txt" 2>/dev/null
}

phase_play() {
  note "play"
  echo "F5 timing: Auxio must NOT be running. If it is, reboot first (do not force-stop protected apps)."
  pidof $MUSIC > "$OUT/play-music-pid-before.txt" 2>&1
  pause "Tap the HOME Music Play button ONCE and note whether/when audio starts; wait 30 seconds" 45
  logcat_filtered "$OUT/play.log"
  grep -E "deadline-dispatch|dispatch |result MUSIC|state MUSIC|HOME_PLAY|first-audio|MediaSession transport|didn't start in time" \
    "$OUT/play.log" > "$OUT/play-summary.txt" 2>/dev/null
  printf 'Audible? (y/n/unsure) and roughly how many seconds after the tap: '
  read -r answer </dev/tty 2>/dev/null; echo "audible_report=$answer" >> "$OUT/play-summary.txt"
}

phase_maps() {
  note "maps"
  pause "Make sure Maps shows in the HOME map panel and Music is NOT playing (reboot if Auxio is running)" 60
  snapshot_tasks maps-1-before
  pause "Touch an empty part of the HOME media strip (not a button) to give HOME focus" 20
  snapshot_tasks maps-2-home-focused
  pause "Tap Music Play once; wait until music plays or 25 seconds pass" 40
  snapshot_tasks maps-3-after-cold-play
  logcat_filtered "$OUT/maps.log"
  grep -E "TS18Nav|re-present-after-staging|user-activity-held|stack not visible|startup-loader|navigation" \
    "$OUT/maps.log" > "$OUT/maps-summary.txt" 2>/dev/null
  printf 'Was the map visible in the HOME panel after Music started? (y/n): '
  read -r answer </dev/tty 2>/dev/null; echo "map_visible_report=$answer" >> "$OUT/maps-summary.txt"
}

phase_settings() {
  note "settings"
  pause "With Maps in the HOME panel, open Maps' own Settings from its InCar menu" 40
  snapshot_tasks settings-1-open
  pause "Stay on the Maps Settings screen for 60 seconds, then press Enter" 90
  snapshot_tasks settings-2-after-60s
  pause "Return to the map (Back) and wait 10 seconds" 30
  snapshot_tasks settings-3-returned
  logcat_filtered "$OUT/settings.log"
  grep -E "TS18Nav|USER_ACTIVITY|BOOTSTRAP_PENDING|native navigation failed|user-activity-held|recovery code" \
    "$OUT/settings.log" > "$OUT/settings-summary.txt" 2>/dev/null
  printf 'Did HOME ever show "Navigation unavailable"? (y/n): '
  read -r answer </dev/tty 2>/dev/null; echo "unavailable_report=$answer" >> "$OUT/settings-summary.txt"
}

phase_identity
case "$PHASE" in
  all) phase_bind; phase_play; phase_maps; phase_settings ;;
  identity) ;;
  bind) phase_bind ;;
  play) phase_play ;;
  maps) phase_maps ;;
  settings) phase_settings ;;
  *) echo "Unknown phase: $PHASE" >&2; exit 4 ;;
esac

MANIFEST_TMP="$OUT.manifest.$$"
( cd "$OUT" && find . -type f -exec sha256sum {} + ) > "$MANIFEST_TMP" && mv "$MANIFEST_TMP" "$OUT/MANIFEST.sha256"
[ -s "$STATUS" ] || echo "PASS collection completed" > "$STATUS"
note "done"
echo "Evidence: $OUT"
echo "Share the folder (or zip it); it contains no music paths, coordinates or account data by design."
