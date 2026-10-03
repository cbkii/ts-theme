#!/data/data/com.termux/files/usr/bin/bash
# Focused TS18 navigation collector. No APK/HOME/root identity admission gates.
# --probe-mode additionally tests the SAME current mode with toTop=false; it is a
# reversible Binder capability exercise, not part of the default read-only run.
# shellcheck disable=SC2016 # Literal programs are evaluated in the Android root shell.
umask 077
duration=600
probe=0
pkg=app.organicmaps.incar
out_base=/storage/emulated/0/Download/ts-theme
while (( $# )); do
  case "$1" in
    --seconds) [[ $# -ge 2 && "$2" =~ ^[0-9]+$ ]] || exit 64; duration="$2"; shift 2 ;;
    --package) [[ $# -ge 2 && "$2" =~ ^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$ ]] || exit 64; pkg="$2"; shift 2 ;;
    --out-base) [[ $# -ge 2 && "$2" == /* ]] || exit 64; out_base="$2"; shift 2 ;;
    --probe-mode) probe=1; shift ;;
    -h|--help)
      printf 'Usage: %s [--seconds 5..1200] [--package PKG] [--out-base DIR] [--probe-mode]\n' "$0"
      printf 'Default: read-only; repeat/modify physical app switching as required. Ctrl-C seals early.\n'
      printf -- '--probe-mode: reapply the current Maps mode, toTop=false, without Activity launch.\n'
      exit 0 ;;
    *) exit 64 ;;
  esac
done
(( duration >= 5 && duration <= 1200 )) || exit 64
termux_bin="${PREFIX:-/data/data/com.termux/files/usr}/bin"
raw_su="$termux_bin/su"
[[ -x "$raw_su" ]] || raw_su="$(command -v su 2>/dev/null)"
android_path=/system/bin:/system/xbin:/vendor/bin:/product/bin
work="$(mktemp -d "${TMPDIR:-$HOME}/ts18-nav-lifecycle.XXXXXX")" || exit 73
out="$work/TS18-nav-lifecycle-$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$out/checkpoints" || exit 73
stop=0
logger_started=0
sequence=0
trap 'stop=1' INT TERM HUP
printf 'surface\texit\tstatus\n' >"$out/results.tsv"
cp -- "$0" "$out/collector.sh"

quote() { printf "'%s'" "${1//\'/\'\\\'\'}"; }

# Proven completion-sentinel runner: Magisk su can outlive the finished child.
# Each child has its own Android timeout; an outer deadline is only a cleanup guard.
root_capture() {
  local rel="$1" seconds="$2" program="$3"
  local token command_file status_file deadline rc pid safe_file safe_status
  token="$sequence-$RANDOM"
  sequence=$((sequence + 1))
  command_file="$work/command-$token.sh"
  status_file="$work/status-$token"
  {
    printf 'PATH=%s\nHOME=/\nexport PATH HOME\nunset LD_PRELOAD LD_LIBRARY_PATH\n' "$android_path"
    printf '%s\n' "$program"
  } >"$command_file"
  safe_file="$(quote "$command_file")"
  safe_status="$(quote "$status_file")"
  : >"$status_file" # Keep Termux ownership; root must overwrite, not create this sentinel.
  "$raw_su" -c "/system/bin/toybox timeout -k 1 $seconds /system/bin/sh $safe_file; rc=\$?; printf '%s\\n' \"\$rc\" >$safe_status" \
    >"$out/$rel" 2>"$out/$rel.stderr" &
  pid=$!
  deadline=$((SECONDS + seconds + 4))
  while [[ ! -s "$status_file" ]] && kill -0 "$pid" 2>/dev/null && (( SECONDS < deadline )); do
    sleep 0.1
  done
  rc=124
  if [[ -s "$status_file" ]]; then
    read -r rc <"$status_file"
    [[ "$rc" =~ ^[0-9]+$ ]] || rc=125
  fi
  # Completion is defined by the child sentinel, not by Magisk wrapper lifetime.
  kill -TERM "$pid" 2>/dev/null || true
  kill -KILL "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  printf '%s\t%s\t%s\n' "$rel" "$rc" "$([[ "$rc" == 0 ]] && printf PASS || printf WARN)" >>"$out/results.tsv"
  rm -f "$command_file" "$status_file"
  return 0
}

checkpoint() {
  local name="$1" dir="checkpoints/$1"
  mkdir -p "$out/$dir"
  printf 'timestamp=%s\nuptime=%s\n' "$(date -u +%FT%TZ)" "$SECONDS" >"$out/$dir/time.txt"
  root_capture "$dir/activities.txt" 6 'dumpsys activity activities'
  root_capture "$dir/recents.txt" 6 'dumpsys activity recents'
  root_capture "$dir/stacks.txt" 6 'am stack list'
  root_capture "$dir/windows.txt" 6 'dumpsys window windows'
  root_capture "$dir/process.txt" 4 "pidof '$pkg'; ps -AZ | grep -F '$pkg'"
  root_capture "$dir/helper.txt" 6 "user=\$(am get-current-user); /data/adb/ts18-launcher/nav-window.sh status \"\$user\" '$pkg' 0"
  root_capture "$dir/bridge.txt" 5 "user=\$(am get-current-user); apk=\$(pm path --user \"\$user\" com.cbkii.ts18launcher | head -n 1); apk=\${apk#package:}; CLASSPATH=\"\$apk\" app_process /system/bin com.cbkii.ts18launcher.NavTaskBridge status \"\$user\" '$pkg' 0"
  printf 'checkpoint=%s\n' "$name"
}

root_capture identity.txt 5 'id; cat /proc/self/attr/current; readlink /proc/self/ns/mnt; cat /proc/sys/kernel/random/boot_id; am get-current-user; getprop ro.build.fingerprint'
root_capture packages.txt 8 "for package in com.cbkii.ts18launcher '$pkg'; do pm path \"\$package\"; dumpsys package \"\$package\" | grep -E 'versionCode=|versionName=|userId=|signatures='; done; apk=\$(pm path com.cbkii.ts18launcher | head -n 1); apk=\${apk#package:}; sha256sum \"\$apk\""
root_capture home.txt 5 'cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME'
root_capture vendor-before.txt 5 'getprop persist.tw.forcepip; getprop sys.tw.forcepip; cat /data/tw/navi_name /data/tw/custom_pip_app_name'
root_capture helper-before.txt 5 'sha256sum /data/adb/ts18-launcher/nav-window.sh; cat /data/adb/ts18-launcher/task-miss-latest.txt'
checkpoint initial
root_capture surfaces-initial.txt 6 'dumpsys SurfaceFlinger'
if (( probe )); then
  printf 'Capability exercise: reapplying observed task mode with toTop=false.\n'
  root_capture capability-probe.txt 8 "user=\$(am get-current-user); /data/adb/ts18-launcher/nav-window.sh probe-task-mode \"\$user\" '$pkg' 0"
  checkpoint after-probe
fi

# The exact-unit startup collector proved Magisk BusyBox setsid + detached logcat.
# This finite group kills itself on timeout and can be stopped independently of su.
logger_out="$(quote "$out/live-logcat.txt")"
logger_owner="$(quote "$work/logger-owner")"
: >"$work/logger-owner"
: >"$out/live-logcat.txt"
root_capture logger-start.txt 4 "bb=/data/adb/magisk/busybox; test -x \"\$bb\" || exit 127; \"\$bb\" setsid /system/bin/sh -c 'echo \"\$\$\" >\"\$1\"; exec /system/bin/toybox timeout -k 1 $((duration + 60)) /system/bin/logcat -v threadtime -T 1 -s TS18Nav:V ActivityTaskManager:I WindowManager:I Configuration:I AndroidRuntime:E' sh $logger_owner >$logger_out 2>&1 </dev/null &"
[[ -s "$work/logger-owner" ]] && logger_started=1
printf 'OBSERVATION READY. Switch HOME, another app, Maps/fullscreen and Recents at your own pace.\n'
printf 'Repeats are supported. Do not press Retry until the failure has been visible for a few seconds.\n'
printf 'Return here and press Ctrl-C once when finished; automatic duration limit is %ss.\n' "$duration"
deadline=$((SECONDS + duration))
last=""
count=0
while (( !stop && SECONDS < deadline && count < 40 )); do
  root_capture sample.txt 6 'dumpsys activity activities'
  signature="$(awk -v pkg="$pkg" '
    /TaskRecord/ && index($0," A=" pkg " ") { print }
    /mResumedActivity:|topResumedActivity=|^[[:space:]]*ResumedActivity:/ { print }
    /^[[:space:]]*(Stack|RootTask) #[0-9]+/ { print }
  ' "$out/sample.txt")"
  if [[ "$signature" != "$last" ]]; then
    count=$((count + 1)); last="$signature"
    checkpoint "$(printf '%03d' "$count")"
  fi
  sleep 2
done

if (( logger_started )); then
  logger_pid="$(cat "$work/logger-owner")"
  if [[ "$logger_pid" =~ ^[0-9]+$ ]]; then
    root_capture logger-stop.txt 4 "bb=/data/adb/magisk/busybox; "\$bb" kill -TERM -- '-$logger_pid' 2>/dev/null; "\$bb" sleep 0.2; "\$bb" kill -KILL -- '-$logger_pid' 2>/dev/null; exit 0"
  fi
fi
# Freeze the file before sealing even if root/logcat shutdown was unobservable.
if [[ -f "$out/live-logcat.txt" ]]; then
  mv "$out/live-logcat.txt" "$work/live-logcat-unsealed.txt"
  head -c 8388608 "$work/live-logcat-unsealed.txt" >"$out/live-logcat.txt"
fi
checkpoint final
root_capture surfaces-final.txt 6 'dumpsys SurfaceFlinger'
root_capture vendor-after.txt 5 'getprop persist.tw.forcepip; getprop sys.tw.forcepip; cat /data/tw/navi_name /data/tw/custom_pip_app_name'
root_capture task-miss-final.txt 4 'cat /data/adb/ts18-launcher/task-miss-latest.txt'
printf 'Collector complete. Warnings describe missing observations, not target failure.\nPhysical qualification is NOT inferred from archive creation.\n' >"$out/README.txt"
(cd "$out" && find . -type f ! -name MANIFEST.sha256 ! -name MANIFEST_VERIFY.txt -print0 | sort -z | xargs -0 sha256sum) >"$out/MANIFEST.sha256"
(cd "$out" && sha256sum -c MANIFEST.sha256) >"$out/MANIFEST_VERIFY.txt" 2>&1
archive="$out.zip"
if command -v zip >/dev/null 2>&1; then
  (cd "$work" && zip -qr "$archive" "${out##*/}")
else
  archive="$out.tar.gz"
  tar -czf "$archive" -C "$work" "${out##*/}"
fi
if [[ -s "$archive" ]] && mkdir -p "$out_base" && cp -- "$archive" "$out_base/"; then
  final="$out_base/${archive##*/}"
  sha256sum "$final" >"$final.sha256"
  printf 'Saved: %s\n%s.sha256\n' "$final" "$final"
else
  printf 'WARN export/archive unavailable; evidence preserved at %s\n' "$out"
fi
exit 0
