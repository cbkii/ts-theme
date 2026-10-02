#!/data/data/com.termux/files/usr/bin/bash
# Read-only TS18 PR14 cold-start/runtime collector.
#
# This script never invokes raw su and never mutates HOME, tasks, package state, media state,
# permissions, AppOps or settings. Run once as ordinary Termux, then rerun from the TS18 Termux Kit
# s/st root lane when root-context evidence is required. The two runs intentionally remain separate.

set -u
umask 077

out_base=/storage/emulated/0/Download/ts-theme
while (( $# )); do
  case "$1" in
    --out-base) (( $# >= 2 )) || exit 64; out_base="$2"; shift 2 ;;
    -h|--help)
      printf 'Usage: %s [--out-base ABSOLUTE_DIR]\n' "$0"
      printf 'Run after reproducing the cold-start/HOME/navigation/media issue. No mutation is performed.\n'
      exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; exit 64 ;;
  esac
done
case "$out_base" in /*) ;; *) printf 'Output base must be absolute\n' >&2; exit 64 ;; esac
case "$out_base" in *'..'*|*$'\n'*|*$'\r'*) printf 'Unsafe output path\n' >&2; exit 64 ;; esac

termux_bin="${TS18_TERMUX_BIN:-${PREFIX:-/data/data/com.termux/files/usr}/bin}"
android_path="${TS18_ANDROID_PATH:-/system/bin:/system/xbin:/vendor/bin:/product/bin}"
export PATH="$termux_bin:$android_path"
for tool in bash timeout date mkdir sha256sum zip unzip find sort xargs cat grep head wc mv id; do
  command -v "$tool" >/dev/null 2>&1 || {
    printf 'Missing REQUIRED prerequisite: %s\n' "$tool" >&2
    exit 127
  }
done

stamp="$(date +%Y%m%d-%H%M%S)-$$"
lane="uid$(id -u 2>/dev/null || printf unknown)"
out="$out_base/pr14-cold-runtime-$lane-$stamp"
mkdir -p -- "$out" || exit 1
status="$out/STATUS.tsv"
printf 'surface\tclass\tresult\texit_status\n' >"$status"
fails=0
blocked=0
required_blocked=0
warns=0
max_capture_bytes=6291456

record() {
  printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >>"$status"
  case "$3" in
    FAIL) fails=$((fails + 1)) ;;
    BLOCKED)
      blocked=$((blocked + 1))
      [[ "$2" == REQUIRED ]] && required_blocked=$((required_blocked + 1)) ;;
    WARN) warns=$((warns + 1)) ;;
  esac
}

capture() {
  local name="$1" class="$2" seconds="$3" rc=0 bytes truncated=0
  shift 3
  mkdir -p -- "$(dirname -- "$out/$name")"
  timeout -k 1 "$seconds" "$@" >"$out/$name" 2>&1 || rc=$?
  bytes="$(wc -c <"$out/$name")"
  if (( bytes > max_capture_bytes )); then
    head -c "$max_capture_bytes" "$out/$name" >"$out/$name.tmp" || return 1
    mv -- "$out/$name.tmp" "$out/$name" || return 1
    printf '\n# TRUNCATED original_bytes=%s limit_bytes=%s\n' "$bytes" "$max_capture_bytes" >>"$out/$name"
    truncated=1
  fi
  printf '\n# exit_status=%s\n' "$rc" >>"$out/$name"
  if (( truncated )); then
    [[ "$class" == REQUIRED ]] && record "$name" "$class" FAIL "$rc;TRUNCATED" \
      || record "$name" "$class" WARN "$rc;TRUNCATED"
  elif (( rc == 0 )); then
    record "$name" "$class" PASS 0
  elif [[ "$class" == REQUIRED ]]; then
    record "$name" "$class" FAIL "$rc"
  else
    record "$name" "$class" WARN "$rc"
  fi
}

capture_shell() {
  local name="$1" class="$2" seconds="$3" script="$4"
  capture "$name" "$class" "$seconds" bash -c "$script"
}

current_user() {
  local value
  value="$(timeout -k 1 4 cmd activity get-current-user 2>/dev/null)" || value=''
  if [[ "$value" =~ ^[[:space:]]*(Current[[:space:]]user:[[:space:]]*)?([0-9]+)[[:space:]]*$ ]]; then
    printf '%s\n' "${BASH_REMATCH[2]}"; return 0
  fi
  value="$(timeout -k 1 4 am get-current-user 2>/dev/null)" || value=''
  if [[ "$value" =~ ^[[:space:]]*(Current[[:space:]]user:[[:space:]]*)?([0-9]+)[[:space:]]*$ ]]; then
    printf '%s\n' "${BASH_REMATCH[2]}"; return 0
  fi
  return 1
}

capture identity/id.txt REQUIRED 4 id
capture identity/selinux.txt OPTIONAL 4 id -Z
capture identity/mount-namespace.txt OPTIONAL 4 readlink /proc/self/ns/mnt
capture identity/fingerprint.txt REQUIRED 4 getprop ro.build.fingerprint
capture identity/sdk.txt REQUIRED 4 getprop ro.build.version.sdk
capture identity/device.txt OPTIONAL 4 getprop ro.product.device
capture identity/display-size.txt OPTIONAL 6 wm size
capture identity/display-density.txt OPTIONAL 6 wm density

user="$(current_user)" || user=''
if [[ -n "$user" ]]; then
  printf '%s\n' "$user" >"$out/identity/android-user.txt"
  record identity/android-user.txt REQUIRED PASS 0
else
  printf 'Unable to resolve exactly one Android user\n' >"$out/identity/android-user.txt"
  record identity/android-user.txt REQUIRED BLOCKED 1
fi

# Resolve current HOME without launching it.
capture_shell home/resolution.txt REQUIRED 8 \
  'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1; echo ---; dumpsys activity activities | grep -E "mResumedActivity|mFocusedActivity|topResumedActivity|mCurrentFocus" || true'

# Activity/Window authority. Recents is deliberately separate because PR14 distinguishes a parser
# miss from independent recents/process evidence.
capture activity/activities.txt REQUIRED 12 dumpsys activity activities
capture activity/recents.txt REQUIRED 12 dumpsys activity recents
capture activity/processes.txt OPTIONAL 12 dumpsys activity processes
capture window/windows.txt REQUIRED 12 dumpsys window windows
capture window/displays.txt OPTIONAL 10 dumpsys window displays

# Media observation surfaces. These are read-only; no transport command is sent.
capture media/sessions.txt REQUIRED 12 dumpsys media_session
capture media/audio.txt OPTIONAL 12 dumpsys audio
capture_shell media/notification-access.txt REQUIRED 8 \
  'settings get secure enabled_notification_listeners 2>&1; echo ---; dumpsys notification 2>/dev/null | grep -E "Notification listeners|enabled listeners|com.cbkii.ts18launcher" -A8 -B2 || true'
capture activity/launcher-services.txt OPTIONAL 10 dumpsys activity services com.cbkii.ts18launcher

# Package/process identity for the known TS18 owners. Missing optional packages are not target failure.
for package in com.cbkii.ts18launcher app.organicmaps.incar com.tw.media com.navimods.radio com.tw.radio; do
  capture "packages/$package.txt" OPTIONAL 10 dumpsys package "$package"
  capture_shell "process/$package.txt" OPTIONAL 5 \
    "printf 'package=%s\\n' '$package'; pidof '$package' 2>&1 || true; ps -A -o USER,PID,PPID,NAME,ARGS 2>/dev/null | grep -F '$package' | grep -v grep || true"
done

# Capture bounded log history after reproduction. Keep raw and focused views separately so collector
# parsing cannot erase contrary evidence.
capture logs/logcat-raw.txt OPTIONAL 15 logcat -d -t 12000 -v threadtime
capture_shell logs/pr14-focused.txt OPTIONAL 15 \
  "logcat -d -t 20000 -v threadtime 2>&1 | grep -E 'TS18Nav|TS18Media|TS18Launcher|session-monitor|listener-access|ActivityTaskManager|ActivityManager|WindowManager|requestLayout|com\\.tw\\.music\\.MusicActivity|app\\.organicmaps\\.incar|com\\.navimods\\.radio' || true"

# Minimal derived summaries; raw owning dumps remain the authority.
capture_shell summary/nav-task-lines.txt OPTIONAL 6 \
  "grep -E 'app\\.organicmaps\\.incar|Task\\{|taskId=|mTaskId=|windowingMode=|bounds=' '$out/activity/activities.txt' '$out/activity/recents.txt' 2>/dev/null || true"
capture_shell summary/media-task-lines.txt OPTIONAL 6 \
  "grep -E 'com\\.tw\\.media|com\\.tw\\.music|com\\.navimods\\.radio|com\\.tw\\.radio' '$out/activity/activities.txt' '$out/activity/recents.txt' '$out/media/sessions.txt' 2>/dev/null || true"

if [[ "$(id -u 2>/dev/null)" == 0 ]]; then
  capture identity/root-context.txt OPTIONAL 5 bash -c \
    "PATH=$android_path; export PATH; id; id -Z; readlink /proc/self/ns/mnt"
else
  printf '%s\n' \
    'BLOCKED: this is the ordinary Termux lane, not UID0.' \
    'Rerun from the TS18 Termux Kit s/st root lane if root-context evidence is required.' \
    >"$out/identity/root-context.txt"
  record identity/root-context.txt OPTIONAL BLOCKED 0
fi

printf 'fails=%s\nblocked=%s\nrequired_blocked=%s\nwarns=%s\n' \
  "$fails" "$blocked" "$required_blocked" "$warns" >"$out/SUMMARY.txt"

manifest_tmp="$out_base/.pr14-manifest-$stamp.tmp"
(cd "$out" && find . -type f ! -name MANIFEST.sha256 ! -name MANIFEST_VERIFY.txt -print0 \
  | sort -z | xargs -0 sha256sum) >"$manifest_tmp" || exit 1
mv -- "$manifest_tmp" "$out/MANIFEST.sha256" || exit 1
(cd "$out" && sha256sum -c MANIFEST.sha256 >MANIFEST_VERIFY.txt) || exit 1

archive="$out.zip"
archive_verify="$archive.verify.txt"
(cd "$out_base" && zip -q -r "$archive" "${out##*/}") || exit 1
unzip -tq "$archive" >"$archive_verify" || exit 1
sha256sum "$archive" >"$archive.sha256"

printf 'Evidence directory: %s\nArchive: %s\nArchive SHA-256: %s.sha256\n' \
  "$out" "$archive" "$archive"
printf 'Run result: fails=%s required_blocked=%s warns=%s blocked=%s\n' \
  "$fails" "$required_blocked" "$warns" "$blocked"
(( fails == 0 && required_blocked == 0 ))
