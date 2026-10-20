#!/system/bin/sh
# TS18_COLDSTART_MANAGED_V2 — standalone Magisk service.d collector; no module.
# Capture data, locks, status, temporary files and receipts ONLY in Download.
# No app/service launch, provider call, playback, task change, log clear or settings write.
umask 077
BB=/data/adb/magisk/busybox
BASE=/storage/emulated/0/Download/TS18-ColdStart
APPS='com.cbkii.ts18launcher com.tw.media app.organicmaps.incar'
PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin
export PATH
unset LD_PRELOAD LD_LIBRARY_PATH TMPDIR
SELF=$0

help() {
  printf '%s\n' 'TS18 cold-start collector v2' \
    'No arguments: detached 480-second boot capture (service.d).' \
    'start: detached 480-second manual capture; never claims a cold boot.' \
    'All generated output: Download/TS18-ColdStart. No fallback storage.' \
    'Use ts18-coldstart-validate.sh for installation, status, P/S/F/N and export.'
}
case "${1:-}" in help|--help|-h) help; exit 0;; esac
[ -x "$BB" ] || exit 69
[ "$("$BB" id -u)" = 0 ] || exit 77
SELF=$("$BB" readlink -f "$SELF") || exit 69

# BEGIN PRIMITIVES (also exercised with a host BusyBox-API shim)
ms() {
  read -r raw_up rest < /proc/uptime || return 1
  up_sec=${raw_up%%.*}; up_frac=${raw_up#*.}000
  up_frac=${up_frac%"${up_frac#???}"}
  printf '%s\n' "$((up_sec * 1000 + 1$up_frac - 1000))"
}
identity() {
  [ -r "/proc/$1/stat" ] || return 1
  IFS= read -r stat_line < "/proc/$1/stat" || return 1
  stat_line=${stat_line##*) }
  # shellcheck disable=SC2086 # Numeric token splitting, literal AWK or Magisk ash pipefail.
  set -- $stat_line
  [ "$1" != Z ] || return 1
  # state ppid pgrp session ... starttime (field 20 after the comm field)
  stat_group=$3; stat_session=$4
  shift 19
  printf '%s %s %s\n' "$1" "$stat_group" "$stat_session"
}
same() {
  same_id=$(identity "$1") || return 1
  [ "${same_id%% *}" = "$2" ]
}
safe_dir() {
  [ ! -L "$BASE" ] || return 1
  [ -d "$1" ] && [ ! -L "$1" ] || return 1
  safe_real=$("$BB" readlink -f "$1") || return 1
  safe_base=$("$BB" readlink -f "$BASE") || return 1
  case "$safe_real/" in "$safe_base/"*) return 0;; *) return 1;; esac
}
event() { printf '%s\t%s\n' "$(ms)" "$*" >> "$OUT/events.tsv"; }
room() {
  # shellcheck disable=SC2016 # Numeric token splitting, literal AWK or Magisk ash pipefail.
  room_kb=$("$BB" df -Pk "$BASE" 2>/dev/null | "$BB" awk 'END {print $4}')
  case "$room_kb" in ''|*[!0-9]*) return 1;; esac
  [ "$room_kb" -ge 262144 ]
}
reserve() {
  [ "$((RESERVED + $1))" -le 100663296 ] || return 1
  [ "$COUNT" -lt 180 ] || return 1
  RESERVED=$((RESERVED + $1)); COUNT=$((COUNT + 1))
}
# END PRIMITIVES

worker() {
  w_dir=$1; w_cap=$2; w_seconds=$3; shift 3
  safe_dir "$w_dir" || exit 70
  case "$w_cap:$w_seconds" in *[!0-9:]*|:*) exit 70;; esac
  [ "$w_cap" -ge 1 ] && [ "$w_cap" -le 33554432 ] || exit 70
  [ "$w_seconds" -ge 1 ] && [ "$w_seconds" -le 480 ] || exit 70
  for w_name in output.txt ready 'done'; do [ ! -L "$w_dir/$w_name" ] || exit 70; done
  w_identity=$(identity "$$") || exit 70
  set -- "$w_dir" "$w_cap" "$w_seconds" "$w_identity" "$@"
  w_ticks=${w_identity%% *}; w_gs=${w_identity#* }
  [ "$w_gs" = "$$ $$" ] || exit 70
  printf '%s %s\n' "$$" "$w_ticks" > "$w_dir/ready" || exit 70
  # Independent watchdog: even if the supervisor dies, descendants cannot run forever.
  # This child remains in the owned group, keeping the PGID allocated even if its leader dies.
  ( "$BB" sleep "$((w_seconds + 2))"; "$BB" kill -KILL "-$$" ) </dev/null >/dev/null 2>&1 &
  shift 4
  # shellcheck disable=SC3040 # Numeric token splitting, literal AWK or Magisk ash pipefail.
  set -o pipefail
  "$@" 2>&1 | "$BB" head -c "$w_cap" > "$w_dir/output.txt"
  w_rc=$?
  printf '%s\n' "$w_rc" > "$w_dir/done"
  # Keep the proven process-group leader alive until the supervisor kills its group.
  while :; do "$BB" sleep 1; done
}

start_worker() {
  sw_label=$1; sw_cap=$2; sw_secs=$3; shift 3
  case "$sw_label" in ''|*[!A-Za-z0-9_-]*) return 1;; esac
  sw_remaining=$(((WORK_END - $(ms)) / 1000))
  [ "$sw_remaining" -ge 1 ] || return 1
  [ "$sw_secs" -le "$sw_remaining" ] || sw_secs=$sw_remaining
  reserve "$sw_cap" || { event "SKIP budget $sw_label"; return 1; }
  sw_dir=$OUT/probes/$COUNT-$sw_label
  "$BB" mkdir "$sw_dir" || return 1
  printf 'label=%s\nstart_uptime_ms=%s\ncap_bytes=%s\nbudget_s=%s\n' "$sw_label" "$(ms)" "$sw_cap" "$sw_secs" > "$sw_dir/meta.txt"
  "$BB" setsid "$BB" sh "$SELF" --worker "$sw_dir" "$sw_cap" "$sw_secs" "$@" </dev/null >/dev/null 2>&1 &
  sw_pid=$!; sw_wait=$(( $(ms) + 1500 ))
  while [ ! -s "$sw_dir/ready" ] && [ "$(ms)" -lt "$sw_wait" ]; do "$BB" sleep 0.05; done
  if [ ! -s "$sw_dir/ready" ]; then
    sw_check=$(identity "$sw_pid") || sw_check=''
    case "$sw_check" in *" $sw_pid $sw_pid") "$BB" kill -KILL "-$sw_pid" 2>/dev/null || :;; *) "$BB" kill -KILL "$sw_pid" 2>/dev/null || :;; esac
    wait "$sw_pid" 2>/dev/null || :
    event "BLOCKED worker identity $sw_label"; : > "$OUT/CLEANUP_UNPROVEN"
    return 1
  fi
  read -r sw_seen sw_ticks < "$sw_dir/ready"
  if [ "$sw_seen" != "$sw_pid" ] || ! same "$sw_pid" "$sw_ticks"; then
    event "BLOCKED worker mismatch $sw_label"; : > "$OUT/CLEANUP_UNPROVEN"
    return 1
  fi
  sw_end=$(( $(ms) + sw_secs * 1000 ))
  printf '%s\t%s\t%s\t%s\n' "$sw_pid" "$sw_ticks" "$sw_dir" "$sw_end" >> "$OUT/workers.tsv"
  LAST_WORKER="$sw_pid $sw_ticks $sw_dir $sw_end"
}
stop_worker() {
  st_pid=$1; st_ticks=$2; st_dir=$3
  [ ! -f "$st_dir/closed" ] || return 0
  st_status=UNKNOWN; st_rc=UNKNOWN
  [ ! -r "$st_dir/done" ] || read -r st_rc < "$st_dir/done"
  if same "$st_pid" "$st_ticks"; then
    "$BB" kill -KILL "-$st_pid" 2>/dev/null || : > "$OUT/CLEANUP_UNPROVEN"
    wait "$st_pid" 2>/dev/null || :
  else
    : > "$OUT/CLEANUP_UNPROVEN"
  fi
  st_bytes=$("$BB" stat -c %s "$st_dir/output.txt" 2>/dev/null) || st_bytes=0
  st_cap=$("$BB" sed -n 's/^cap_bytes=//p' "$st_dir/meta.txt")
  if [ "$st_bytes" -ge "$st_cap" ]; then st_status=TRUNCATED
  elif [ "$st_rc" = 0 ]; then st_status=OK
  elif [ "$st_rc" = UNKNOWN ]; then
    case "$st_dir" in *-logcat-*|*-input-events|*-process-samples) st_status=PLANNED_STREAM_STOP;; *) st_status=TIMEOUT_OR_STOP;; esac
  else st_status=COMMAND_FAILED; fi
  printf 'end_uptime_ms=%s\nrc=%s\nbytes=%s\nresult=%s\n' "$(ms)" "$st_rc" "$st_bytes" "$st_status" >> "$st_dir/meta.txt"
  case "$st_dir:$st_status" in *-screen-*:OK) "$BB" mv "$st_dir/output.txt" "$st_dir/screenshot.png";; esac
  : > "$st_dir/closed"
  event "$st_status ${st_dir##*/} rc=$st_rc bytes=$st_bytes"
}
probe() {
  [ ! -f "$BASE/active/STOP" ] || return 1
  start_worker "$@" || return 1
  # shellcheck disable=SC2086 # Numeric token splitting, literal AWK or Magisk ash pipefail.
  set -- $LAST_WORKER
  pr_pid=$1; pr_ticks=$2; pr_dir=$3; pr_end=$4
  while [ ! -f "$pr_dir/done" ] && [ "$(ms)" -lt "$pr_end" ] && same "$pr_pid" "$pr_ticks"; do "$BB" sleep 0.1; done
  stop_worker "$pr_pid" "$pr_ticks" "$pr_dir" "$pr_end"
}

sample() {
  sample_end=$1; sample_begin=$(ms); sample_scan=0; sample_pids=''
  printf 'uptime_ms\tkind\tpid\tstart_ticks\tstate\tppid\tutime_ticks\tstime_ticks\tthreads\tvsize_bytes\trss_pages\twchan\n'
  while [ "$(ms)" -lt "$sample_end" ]; do
    sample_now=$(ms)
    if [ "$sample_now" -ge "$sample_scan" ]; then
      sample_pids=$("$BB" pidof com.cbkii.ts18launcher com.tw.media app.organicmaps.incar 2>/dev/null)
      printf '%s\tPIDS\t%s\n' "$sample_now" "$sample_pids"
      for sample_pid in $sample_pids; do
        printf '%s\tCMDLINE\t%s\t' "$sample_now" "$sample_pid"
        "$BB" head -c 256 "/proc/$sample_pid/cmdline" 2>/dev/null | "$BB" tr '\000' ' '
        printf '\n'
      done
      sample_scan=$((sample_now + 1000))
    fi
    for sample_pid in $sample_pids; do
      [ -r "/proc/$sample_pid/stat" ] || continue
      IFS= read -r sample_stat < "/proc/$sample_pid/stat" || continue
      sample_stat=${sample_stat##*) }
      # shellcheck disable=SC2086 # Numeric token splitting, literal AWK or Magisk ash pipefail.
      set -- $sample_stat
      sample_state=$1; sample_ppid=$2
      shift 11; sample_utime=$1; sample_stime=$2
      shift 6; sample_threads=$1
      shift 2; sample_start=$1; sample_vsize=$2; sample_rss=$3
      sample_wchan=UNKNOWN
      [ ! -r "/proc/$sample_pid/wchan" ] || IFS= read -r sample_wchan < "/proc/$sample_pid/wchan"
      printf '%s\tPROC\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$sample_now" "$sample_pid" "$sample_start" "$sample_state" "$sample_ppid" "$sample_utime" "$sample_stime" "$sample_threads" "$sample_vsize" "$sample_rss" "$sample_wchan"
    done
    read -r sample_load _sample_rest < /proc/loadavg
    printf '%s\tLOAD\t%s\n' "$sample_now" "$sample_load"
    if [ "$((sample_now - sample_begin))" -lt 60000 ]; then "$BB" sleep 0.25; else "$BB" sleep 1; fi
  done
}

private_state() {
  # Read exact known files only. Never copy media databases, bookmarks, maps or credentials.
  ps_user=$1
  case "$ps_user" in ''|*[!0-9]*) exit 64;; esac
  for ps_pkg in com.cbkii.ts18launcher app.organicmaps.incar; do
    ps_root=/data/user/$ps_user/$ps_pkg/shared_prefs
    case "$ps_pkg" in
      com.cbkii.ts18launcher) ps_files='ts18_launcher.xml testing_profiles.xml startup_readiness.xml compact_startup.xml';;
      *) ps_files='';;
    esac
    for ps_name in $ps_files; do
      ps_file=$ps_root/$ps_name
      if [ ! -f "$ps_file" ] || [ -L "$ps_file" ]; then continue; fi
      printf '\nFILE %s\n' "$ps_file"
      "$BB" head -c 32768 "$ps_file"
    done
  done
  for ps_dir in /data/user/0/com.cbkii.ts18launcher/no_backup/navigation-root/auto-v1-* /data/user/0/com.cbkii.ts18launcher/no_backup/navigation-root/launch-* /data/adb/ts18-launcher/auto-v1-* /data/adb/ts18-launcher/launch-*; do
    if [ ! -d "$ps_dir" ] || [ -L "$ps_dir" ]; then continue; fi
    printf '\nHELPER_JOURNAL %s\n' "$ps_dir"
    for ps_name in identity state ready-owner; do
      [ ! -f "$ps_dir/$ps_name" ] || "$BB" head -c 4096 "$ps_dir/$ps_name"
    done
  done
}
apk_hashes() {
  for ah_pkg in $APPS; do
    printf '\nPACKAGE %s\n' "$ah_pkg"
    /system/bin/pm path "$ah_pkg" | while IFS= read -r ah_line; do
      ah_file=${ah_line#package:}
      case "$ah_file" in /data/app/*/*.apk|/system/app/*/*.apk|/system/priv-app/*/*.apk|/product/app/*/*.apk|/vendor/app/*/*.apk)
        [ -f "$ah_file" ] && [ ! -L "$ah_file" ] && "$BB" sha256sum "$ah_file";;
        *) printf 'UNSUPPORTED_APK_PATH %s\n' "$ah_file";;
      esac
    done
  done
}
thread_state() {
  for th_pid in $("$BB" pidof com.cbkii.ts18launcher com.tw.media app.organicmaps.incar 2>/dev/null); do
    for th_dir in "/proc/$th_pid/task"/[0-9]*; do
      [ -r "$th_dir/stat" ] || continue
      printf '\nUPTIME_MS %s PID %s TID %s\n' "$(ms)" "$th_pid" "${th_dir##*/}"
      "$BB" cat "$th_dir/stat" "$th_dir/wchan" 2>&1
    done
  done
}
snapshot() {
  sn=$1
  probe "$sn-activities" 196608 2 /system/bin/dumpsys -t 2 activity activities
  probe "$sn-windows" 196608 2 /system/bin/dumpsys -t 2 window windows
  probe "$sn-sessions" 262144 2 /system/bin/dumpsys -t 2 media_session
}
extended() {
  ex=$1
  probe "$ex-audio" 262144 3 /system/bin/dumpsys -t 3 audio
  probe "$ex-audioflinger" 262144 3 /system/bin/dumpsys -t 3 media.audio_flinger
  probe "$ex-audiopolicy" 131072 2 /system/bin/dumpsys -t 2 media.audio_policy
  probe "$ex-surfaces" 196608 2 /system/bin/dumpsys -t 2 SurfaceFlinger --list
  for ex_pkg in $APPS; do
    ex_label=$(printf '%s' "$ex_pkg" | "$BB" tr . _)
    probe "$ex_label-meminfo" 131072 3 /system/bin/dumpsys -t 3 meminfo "$ex_pkg"
    probe "$ex_label-gfxinfo" 131072 2 /system/bin/dumpsys -t 2 gfxinfo "$ex_pkg" framestats
  done
  case "$CURRENT_USER" in ''|*[!0-9]*) event 'SKIP private preferences: current Android user unknown';;
    *) probe "$ex-private-readonly" 262144 2 "$BB" sh "$SELF" --private "$CURRENT_USER";;
  esac
}
baseline() {
  probe properties 49152 2 "$BB" sh "$SELF" --properties
  probe mounts 65536 2 "$BB" cat /proc/mounts
  probe media-storage 65536 2 /system/bin/dumpsys -t 2 mount
  probe user 16384 2 /system/bin/am get-current-user
  # A separate value read is bounded; an unknown result never defaults to user 0.
  CURRENT_USER=$("$BB" timeout -s KILL 2 /system/bin/am get-current-user 2>/dev/null)
  case "$CURRENT_USER" in *[!0-9]*|'') CURRENT_USER=UNKNOWN;; esac
  printf 'current_android_user=%s\n' "$CURRENT_USER" >> "$OUT/CONTEXT.txt"
  probe home-resolution 32768 2 /system/bin/cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
  probe notification-access 32768 2 /system/bin/settings get secure enabled_notification_listeners
  probe display 65536 2 /system/bin/dumpsys -t 2 display
  probe power 65536 2 /system/bin/dumpsys -t 2 power
  for bl_pkg in $APPS; do
    bl_label=$(printf '%s' "$bl_pkg" | "$BB" tr . _)
    probe "$bl_label-package" 262144 3 /system/bin/dumpsys -t 3 package "$bl_pkg"
    probe "$bl_label-services" 131072 2 /system/bin/dumpsys -t 2 activity services "$bl_pkg"
    probe "$bl_label-appops" 65536 2 /system/bin/cmd appops get "$bl_pkg"
  done
}

finish() {
  [ "${FINISHING:-0}" = 0 ] || return
  FINISHING=1
  trap '' HUP INT TERM
  printf 'status=CLOSING\n' > "$OUT/STATUS.txt"
  event "capture_stop reason=${REASON:-deadline}"
  if [ -r "$OUT/workers.tsv" ]; then
    while IFS="$(printf '\t')" read -r fi_pid fi_ticks fi_dir fi_end; do
      stop_worker "$fi_pid" "$fi_ticks" "$fi_dir" "$fi_end"
    done < "$OUT/workers.tsv"
  fi
  printf 'status=CLOSED\nstop_uptime_ms=%s\nreserved_output_bytes=%s\nphysical_acceptance=NOT_RUN\n' "$(ms)" "$RESERVED" > "$OUT/STATUS.txt"
  [ ! -f "$OUT/CLEANUP_UNPROVEN" ] || printf 'cleanup=UNPROVEN_REVIEW_REQUIRED\n' >> "$OUT/STATUS.txt"
  # Hash small bounded capture files only; hashing is in the final reserved time.
  fi_hash_ok=0
  # shellcheck disable=SC3040 # Numeric token splitting, literal AWK or Magisk ash pipefail.
  if (set -o pipefail; cd "$OUT" && "$BB" find . -type f ! -name SHA256SUMS ! -name COMPLETE.txt | "$BB" sort |
    while IFS= read -r fi_file; do "$BB" sha256sum "$fi_file" || exit 1; done) > "$OUT/SHA256SUMS"; then fi_hash_ok=1; fi
  if [ "$fi_hash_ok" = 1 ] && [ ! -f "$OUT/CLEANUP_UNPROVEN" ] && [ "$(ms)" -lt "$HARD_END" ]; then
    printf 'Writers closed; hashes recorded. Command failures/truncation are in meta.txt. This is not a physical PASS.\n' > "$OUT/COMPLETE.txt"
  fi
  if [ -r "$BASE/active/owner" ]; then
    read -r _fi_boot fi_pid fi_ticks fi_run < "$BASE/active/owner"
    if [ "$fi_pid" = "$$" ] && [ "$fi_run" = "${OUT##*/}" ]; then
      "$BB" rm -f "$BASE/active/owner" "$BASE/active/STOP"
      "$BB" rmdir "$BASE/active" 2>/dev/null || :
    fi
  fi
}
collect() {
  START_MS=$1; HARD_END=$2; ORIGIN=$3
  WORK_END=$((HARD_END - 12000)); RESERVED=0; COUNT=0; FINISHING=0; CURRENT_USER=UNKNOWN
  exec </dev/null >/dev/null 2>&1
  # Start the absolute guard before storage checks or any potentially stalled command.
  cs_identity=$(identity "$$") || exit 73
  [ "${cs_identity#* }" = "$$ $$" ] || exit 73
  trap '"$BB" kill -KILL "-$$"' EXIT
  trap '"$BB" kill -KILL "-$$"; exit 0' HUP INT TERM
  cs_rem=$(((HARD_END - $(ms)) / 1000))
  [ "$cs_rem" -ge 2 ] || exit 73
  ( "$BB" sleep "$((cs_rem - 1))"; "$BB" kill -TERM "$$"; "$BB" sleep 1; "$BB" kill -KILL "-$$" ) </dev/null >/dev/null 2>&1 &
  _cs_watch=$!
  # No files are written before emulated Download already exists and is writable.
  while [ ! -d /storage/emulated/0/Download ] || [ ! -w /storage/emulated/0/Download ]; do
    [ "$(ms)" -lt "$WORK_END" ] || exit 73
    "$BB" sleep 1
  done
  [ ! -L "$BASE" ] || exit 73
  "$BB" mkdir -p "$BASE" || exit 73
  if ! safe_dir "$BASE" || ! room; then exit 73; fi
  TMPDIR=$BASE; export TMPDIR
  # shellcheck disable=SC2016 # Numeric token splitting, literal AWK or Magisk ash pipefail.
  cs_total=$("$BB" timeout -s KILL 2 "$BB" du -sk "$BASE" 2>/dev/null | "$BB" awk '{print $1}')
  case "$cs_total" in ''|*[!0-9]*) exit 73;; esac
  [ "$cs_total" -lt 655360 ] || exit 73
  IFS= read -r BOOT_ID < /proc/sys/kernel/random/boot_id || exit 73
  [ ! -L "$BASE/active" ] || exit 73
  if [ -d "$BASE/active" ]; then
    [ -f "$BASE/active/owner" ] && [ ! -L "$BASE/active/owner" ] || exit 73
    read -r cs_boot cs_pid cs_ticks _cs_run < "$BASE/active/owner"
    case "$cs_pid:$cs_ticks" in *[!0-9:]*|:*) exit 73;; esac
    if [ "$cs_boot" = "$BOOT_ID" ] && same "$cs_pid" "$cs_ticks"; then "$BB" kill -KILL "-$$"; exit 0; fi
    "$BB" mv "$BASE/active" "$BASE/stale-active-$(ms)-$$" || exit 73
  fi
  "$BB" mkdir "$BASE/active" || exit 73
  OUT=$BASE/run-$ORIGIN-${BOOT_ID%%-*}-$START_MS-$$
  "$BB" mkdir "$OUT" "$OUT/probes" "$OUT/requests" || exit 73
  safe_dir "$OUT" || exit 73
  cs_identity=$(identity "$$") || exit 73
  cs_ticks=${cs_identity%% *}
  [ "${cs_identity#* }" = "$$ $$" ] || exit 73
  printf '%s %s %s %s\n' "$BOOT_ID" "$$" "$cs_ticks" "${OUT##*/}" > "$BASE/active/owner"
  [ ! -L "$BASE/latest" ] || exit 73
  printf '%s\n' "${OUT##*/}" > "$BASE/latest"
  : > "$OUT/workers.tsv"
  printf 'script_version=2\norigin=%s\nboot_id=%s\nservice_entry_uptime_ms=%s\noutput_ready_uptime_ms=%s\nhard_deadline_uptime_ms=%s\nmax_duration_ms=480000\noutput_root=%s\nmagisk_capture_output_bytes=0\nphysical_acceptance=NOT_RUN\n' "$ORIGIN" "$BOOT_ID" "$START_MS" "$(ms)" "$HARD_END" "$OUT" > "$OUT/CONTEXT.txt"
  printf 'status=RUNNING\n' > "$OUT/STATUS.txt"
  trap 'REASON=signal; finish; "$BB" kill -KILL "-$$"; exit 0' HUP INT TERM
  # The already-running absolute guard also bounds final hashing and cleanup.
  event 'capture_start; first-60s avoid heavy probes; timestamps are evidence, not audible/pixel readiness'
  cs_secs=$(((WORK_END - $(ms)) / 1000))
  start_worker logcat-main-system-crash 25165824 "$cs_secs" /system/bin/logcat -b main -b system -b crash -v threadtime -v monotonic '*:I' TS18Media:V TS18Nav:V InCarCompactStartup:V
  MAIN_LOG_WORKER=$LAST_WORKER
  start_worker logcat-events 6291456 "$cs_secs" /system/bin/logcat -b events -v threadtime -v monotonic '*:I'
  EVENT_LOG_WORKER=$LAST_WORKER
  start_worker input-events 6291456 "$cs_secs" /system/bin/getevent -lt
  start_worker process-samples 6291456 "$cs_secs" "$BB" sh "$SELF" --sample "$WORK_END"
  cs_light_until=$((START_MS + 60000)); QUIET_UNTIL=0; cs_next=0; cs_extended=0; cs_hashed=0; cs_baseline=0; cs_requests=0; cs_fallback=0; cs_clock=0
  while [ "$(ms)" -lt "$WORK_END" ]; do
    cs_now=$(ms)
    if [ "$cs_now" -ge "$cs_clock" ]; then
      printf '%s\t%s\n' "$cs_now" "$("$BB" date +%s)" >> "$OUT/clock-anchors.tsv"
      cs_clock=$((cs_now + 10000))
    fi
    if [ "$cs_fallback" = 0 ] && [ "$cs_now" -ge "$((START_MS + 3000))" ]; then
      cs_fallback=1
      for cs_original in "$MAIN_LOG_WORKER" "$EVENT_LOG_WORKER"; do
        # shellcheck disable=SC2086 # Numeric token splitting, literal AWK or Magisk ash pipefail.
        set -- $cs_original; cs_ldir=$3
        if [ -f "$cs_ldir/done" ] && "$BB" grep -Eqi 'unknown|invalid|unrecognized|unsupported' "$cs_ldir/output.txt"; then
          event 'logcat format/capability fallback to threadtime only (original error retained)'
          case "$cs_ldir" in *main-system-crash)
            start_worker logcat-main-fallback 8388608 "$cs_secs" /system/bin/logcat -b main -b system -b crash -v threadtime '*:I' TS18Media:V TS18Nav:V InCarCompactStartup:V;;
            *) start_worker logcat-events-fallback 2097152 "$cs_secs" /system/bin/logcat -b events -v threadtime '*:I';;
          esac
        fi
      done
    fi
    [ ! -f "$BASE/active/STOP" ] || { REASON=operator_stop; break; }
    # Public control records are DATA only. Never source/eval or execute a supplied command.
    for cs_req in "$OUT"/requests/req-*; do
      case "$cs_req" in *.processed*|*.pending*) continue;; esac
      if [ ! -f "$cs_req" ] || [ -L "$cs_req" ]; then continue; fi
      [ "$cs_requests" -lt 40 ] || break
      IFS=' ' read -r cs_action cs_label cs_due _cs_extra < "$cs_req"
      case "$cs_label" in ''|*[!A-Za-z0-9_-]*) continue;; esac
      case "$cs_due" in ''|*[!0-9]*) continue;; esac
      case "$cs_action" in MARK|BURST|SHOT|QUIET) ;; *) continue;; esac
      [ "$cs_now" -ge "$cs_due" ] || continue
      if [ "$cs_action" != QUIET ] && [ "$cs_action" != MARK ] && [ "$cs_now" -lt "$QUIET_UNTIL" ]; then continue; fi
      if [ "$cs_action" != QUIET ] && [ "$cs_action" != MARK ] && [ "$cs_now" -lt "$cs_light_until" ]; then continue; fi
      cs_requests=$((cs_requests + 1)); "$BB" mv "$cs_req" "$cs_req.processed" || continue
      event "operator $cs_action $cs_label scheduled=$cs_due"
      case "$cs_action" in
        QUIET) QUIET_UNTIL=$((cs_now + 25000)); event "quiet_window_until=$QUIET_UNTIL";;
        BURST)
          snapshot "$cs_label-$COUNT"
          probe "$cs_label-audio-$COUNT" 262144 2 /system/bin/dumpsys -t 2 audio
          probe "$cs_label-surfaces-$COUNT" 131072 2 /system/bin/dumpsys -t 2 SurfaceFlinger --list
          probe "$cs_label-threads-$COUNT" 131072 2 "$BB" sh "$SELF" --threads;;
        SHOT) probe "$cs_label-screen-$COUNT" 2097152 3 /system/bin/screencap -p;;
      esac
    done
    if [ "$cs_now" -ge "$QUIET_UNTIL" ]; then
    if [ "$cs_now" -ge "$cs_light_until" ] && [ "$cs_baseline" = 0 ]; then
      cs_baseline=1; event 'heavy_probe_phase_start (observer overhead begins here)'; baseline
    fi
    if [ "$cs_baseline" = 1 ] && [ "$cs_now" -ge "$cs_next" ]; then
      room || { REASON=low_space; break; }
      snapshot "periodic-$COUNT"
      cs_next=$(( $(ms) + 20000 ))
    fi
    if [ "$cs_now" -ge "$((START_MS + 90000))" ] && [ "$cs_extended" = 0 ]; then cs_extended=1; extended baseline; fi
    if [ "$cs_now" -ge "$((START_MS + 150000))" ] && [ "$cs_hashed" = 0 ]; then
      cs_hashed=1; event 'deferred_APK_hash_IO_start'; probe installed-apk-hashes 65536 15 "$BB" sh "$SELF" --hashes
    fi
    fi
    "$BB" sleep 0.5
  done
  REASON=${REASON:-deadline}; finish
  "$BB" kill -KILL "-$$"
}

case "${1:-}" in
  --worker) shift; worker "$@";;
  --sample) sample "$2";;
  --private) private_state "$2";;
  --hashes) apk_hashes;;
  --threads) thread_state;;
  --properties)
    for prop in ro.build.fingerprint ro.build.version.sdk ro.product.cpu.abilist ro.config.low_ram sys.boot_completed dev.bootcomplete sys.user.0.ce_available; do
      printf '%s=' "$prop"; /system/bin/getprop "$prop"
    done;;
  --collect)
    case "$2:$3" in *[!0-9:]*|:*) exit 64;; esac
    [ "$(( $3 - $2 ))" = 480000 ] || exit 64
    [ "$2" -le "$(ms)" ] && [ "$3" -le "$(( $(ms) + 480000 ))" ] || exit 64
    case "$4" in boot|manual) ;; *) exit 64;; esac
    collect "$2" "$3" "$4";;
  ''|start)
    case "${1:-}" in start) origin=manual;; *) origin=boot;; esac
    start=$(ms) || exit 69; end=$((start + 480000))
    "$BB" setsid "$BB" sh "$SELF" --collect "$start" "$end" "$origin" </dev/null >/dev/null 2>&1 &
    exit 0;;
  *) help; exit 64;;
esac
