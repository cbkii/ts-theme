#!/system/bin/sh
# TS18 consolidated validation: run from Termux via the existing s/st root lane.
# Embedded reviewed collector + bounded checkpoints. No network and no raw su.
set -u
umask 077
usage() {
cat <<'HELP'
TS18 consolidated qualification 2026-10-11 — repair candidate
  help
  guide                     interactive, resumable one-letter test flow
  cases                     show every case and latest manual result
  preflight                 establish root/device/provenance checkpoint
  start [forensic|performance|mapwindow] [30..300]
  status
  mark IDLE|AUXIO_PLAY|AUXIO_PAUSE|NAVRADIO_PLAY|NAVRADIO_PAUSE
  stop                      early stop: skips heavy post-capture inventory
  checkpoint LABEL [0..30]   background snapshot after delay (default 8 s)
  result CASE P|F|N [short observation]  (full statuses also accepted)
  export                    one immutable campaign tar.gz and SHA256

Keep this script in Termux-private storage. Enter the established s/st root lane;
run with /system/bin/sh. No installer, settings writes, reboot or app launch.
Checkpoint labels/case IDs: 1..64 ASCII letters/numbers/underscore/hyphen.
Guide: select a group, follow the action, return here, press P/F/N then Enter.
N opens Not Run / Blocked / Uncertain / Warning. Empty comment is allowed.
Physical results are operator observations, never inferred from successful probes.
HELP
}
case "${1:-help}" in help|--help|-h) usage; exit 0 ;; esac
PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin:/odm/bin
export PATH
unset LD_PRELOAD LD_LIBRARY_PATH ANDROID_LOG_TAGS ANDROID_PRINTF_LOG
BB=/data/adb/magisk/busybox
[ -x "$BB" ] || { echo 'STOPPED FOR SAFETY: enter the established Termux Kit s/st root lane; Magisk BusyBox is required.'; exit 1; }
[ "$("$BB" id -u)" = 0 ] || { echo 'STOPPED FOR SAFETY: this comprehensive collector requires the explicit s/st root lane. It never calls su.'; exit 1; }
SELF=$("$BB" readlink -f "$0") || exit 1
ROOT=/data/data/com.termux/files/home/.ts18-pr-validation-repair-20261011-v3
BASE=$ROOT/toolkit
EXPORT=/storage/emulated/0/Download/TS18-PR-Validation
valid_label() {
  case "$1" in ''|*[!A-Za-z0-9_-]*) return 1 ;; esac
  [ "${#1}" -le 64 ]
}
die() { printf 'STOPPED FOR SAFETY: %s\n' "$*" >&2; exit 1; }
# Validate before materialising private files or taking locks.
case "$1" in
 preflight|status|stop|export|guide|cases) [ "$#" = 1 ] || die 'unexpected arguments' ;;
 checkpoint)
   [ "$#" = 2 ] || [ "$#" = 3 ] || die 'checkpoint LABEL [0..30]'
   valid_label "$2" || die 'invalid checkpoint label'
   case "${3:-8}" in ''|*[!0-9]*) die 'delay must be 0..30 seconds' ;; esac
   [ "${3:-8}" -le 30 ] || die 'delay must be 0..30 seconds' ;;
 start)
   [ "$#" -le 3 ] || die 'start [forensic|performance|mapwindow] [30..300]'
   case "${2:-forensic}" in forensic|performance|mapwindow) ;; *) die 'invalid capture mode' ;; esac
   case "${3:-180}" in ''|*[!0-9]*) die 'duration must be 30..300 seconds' ;; esac
   [ "${3:-180}" -ge 30 ] && [ "${3:-180}" -le 300 ] || die 'duration must be 30..300 seconds' ;;
 mark)
   [ "$#" = 2 ] || die 'mark requires one label'
   case "$2" in IDLE|AUXIO_PLAY|AUXIO_PAUSE|NAVRADIO_PLAY|NAVRADIO_PAUSE) ;; *) die 'invalid media marker' ;; esac ;;
 result)
   [ "$#" -ge 3 ] && [ "$#" -le 5 ] || die 'result CASE P|F|N [observation]'
   valid_label "$2" || die 'invalid case ID'
   case "$3" in P|F|N|PASS|FAIL|WARN|UNKNOWN|BLOCKED|NOT_RUN) ;; *) die 'invalid physical result' ;; esac
   observation=${4:-}
   [ "${#observation}" -le 2000 ] || die 'observation exceeds 2000 characters'
   case "${5:-}" in ''|--none|--checkpoint-only) ;; *) die 'invalid evidence option' ;; esac ;;
 *) usage; exit 64 ;;
esac
install_payload() {
  [ ! -L "$ROOT" ] || die 'private root is a symlink'
  "$BB" mkdir -p "$ROOT" || exit 1
  [ "$("$BB" stat -c %u "$ROOT")" = 0 ] || die 'private directory is not owned by root'
  "$BB" chmod 700 "$ROOT" || exit 1
  if [ ! -d "$BASE" ]; then
    stage=$("$BB" mktemp -d "$ROOT/stage.XXXXXX") || exit 1
    cat > "$stage/capture-lib.sh" <<'TS18_EMBEDDED_PAYLOAD_0_END'
# Bounded producer primitives for TS18 diagnostics 1.3; sourced, no entry point.
# BB, SELF, OUT and END are set by the collector. No su or device mutations.
uptime_s() { read -r up rest < /proc/uptime; printf '%s\n' "${up%%.*}"; }
ticks() { "$BB" sed 's/.*) //' "/proc/$1/stat" 2>/dev/null | "$BB" awk "\$1 != \"Z\" {print \$20}"; }
same_process() { [ -n "$2" ] && [ "$(ticks "$1")" = "$2" ]; }
atomic() { printf '%s\n' "$2" > "$1.new" && "$BB" mv "$1.new" "$1"; }
event() { printf '%s\t%s\n' "$(uptime_s)" "$*" >> "$OUT/events.tsv"; }
CAPTURE_UNSAFE=${CAPTURE_UNSAFE:-0}

# One isolated session per producer. The child proves its session/process identity
# before any external producer command is started. The parent owns termination.
produce() {
    p_dir=$1; shift
    p_ids=$("$BB" sed 's/.*) //' /proc/$$/stat 2>/dev/null | "$BB" awk "{print \$3, \$4}")
    [ "$p_ids" = "$$ $$" ] || exit 70
    p_ticks=$(ticks "$$")
    [ -n "$p_ticks" ] || exit 70
    atomic "$p_dir/producer.ready" "$$ $p_ticks" || exit 72
    "$@" > "$p_dir/pipe" 2>&1
    p_rc=$?
    atomic "$p_dir/producer.done" "$p_rc" || exit 72
    # Keep the verified group leader alive until its parent terminates the owned
    # group, so descendants cannot outlive a vanished leader and escape cleanup.
    while :; do "$BB" sleep 1; done
}

capture() {
    c_name=$1; c_secs=$2; c_limit=$3; shift 3
    c_start=$(uptime_s)
    [ "$c_start" -lt "$END" ] || { event "BLOCKED $c_name overall_deadline"; return 1; }
    c_free=$("$BB" df -Pk "$OUT" 2>/dev/null | "$BB" awk "END {print \$4}")
    case "$c_free" in ''|*[!0-9]*) event "WARN $c_name free-space UNKNOWN" ;;
        *) [ "$c_free" -ge 262144 ] || { event "BLOCKED $c_name low-private-storage"; return 1; } ;; esac
    c_dir=$OUT/commands/$c_name
    "$BB" mkdir "$c_dir" || return 1
    {
        printf 'start_uptime=%s\ntimeout_s=%s\nlimit_bytes=%s\ncontext=../../CONTEXT.txt\n' "$c_start" "$c_secs" "$c_limit"
        c_arg=0
        for c_value in "$@"; do printf 'argv_%s=%s\n' "$c_arg" "$c_value"; c_arg=$((c_arg + 1)); done
    } > "$c_dir/meta.txt"
    "$BB" mkfifo "$c_dir/pipe" || return 1

    # Keep the bounded reader outside the isolated producer group. On timeout the
    # producer group is killed first; EOF then lets head flush partial evidence.
    "$BB" head -c "$c_limit" < "$c_dir/pipe" > "$c_dir/output.txt" &
    c_reader=$!; c_reader_ticks=$(ticks "$c_reader")
    atomic "$c_dir/reader-owner" "$c_reader $c_reader_ticks"

    "$BB" setsid "$BB" sh "$SELF" --produce "$c_dir" "$@" </dev/null >/dev/null 2>&1 &
    c_pid=$!

    c_ready_until=$((c_start + 2))
    while [ ! -r "$c_dir/producer.ready" ] && "$BB" kill -0 "$c_pid" 2>/dev/null && [ "$(uptime_s)" -lt "$c_ready_until" ]; do
        "$BB" sleep 1
    done
    if [ ! -r "$c_dir/producer.ready" ]; then
        "$BB" kill -KILL "$c_pid" 2>/dev/null || true
        wait "$c_pid" 2>/dev/null || true
        "$BB" kill -KILL "$c_reader" 2>/dev/null || true
        wait "$c_reader" 2>/dev/null || true
        "$BB" rm -f "$c_dir/pipe"
        CAPTURE_UNSAFE=1
        printf 'end_uptime=%s\nduration_s=%s\nproducer_rc=UNKNOWN\nresult=BLOCKED\nbytes=0\n' \
            "$(uptime_s)" "$(( $(uptime_s) - c_start ))" >> "$c_dir/meta.txt"
        event "BLOCKED $c_name producer-identity-unavailable"
        return 1
    fi
    read -r c_ready_pid c_ticks < "$c_dir/producer.ready"
    if [ "$c_ready_pid" != "$c_pid" ] || ! same_process "$c_pid" "$c_ticks"; then
        "$BB" kill -KILL "$c_pid" 2>/dev/null || true
        wait "$c_pid" 2>/dev/null || true
        "$BB" kill -KILL "$c_reader" 2>/dev/null || true
        wait "$c_reader" 2>/dev/null || true
        "$BB" rm -f "$c_dir/pipe"
        CAPTURE_UNSAFE=1
        event "BLOCKED $c_name producer-identity-mismatch"
        return 1
    fi
    atomic "$c_dir/owner" "$c_pid $c_ticks"

    c_until=$((c_start + c_secs)); [ "$c_until" -le "$END" ] || c_until=$END
    while same_process "$c_pid" "$c_ticks" && [ ! -f "$c_dir/producer.done" ] && [ "$(uptime_s)" -lt "$c_until" ]; do
        "$BB" sleep 1
    done
    c_result=WARN; c_rc=UNKNOWN
    if [ -r "$c_dir/producer.done" ]; then
        read -r c_rc < "$c_dir/producer.done"
        [ "$c_rc" = 0 ] && c_result=PASS
    elif [ "$(uptime_s)" -ge "$c_until" ]; then
        c_result=WARN_TIMEOUT
    else
        c_result=BLOCKED
        CAPTURE_UNSAFE=1
    fi

    # A valid seal requires proven termination of the entire owned producer group.
    c_group_stopped=0
    if same_process "$c_pid" "$c_ticks"; then
        if "$BB" kill -KILL "-$c_pid" 2>/dev/null; then
            c_group_stopped=1
        fi
    fi
    wait "$c_pid" 2>/dev/null || true
    if [ "$c_group_stopped" != 1 ]; then
        CAPTURE_UNSAFE=1
        c_result=BLOCKED
        event "BLOCKED $c_name producer-group-cleanup-unproven"
    fi

    c_reader_until=$(( $(uptime_s) + 2 ))
    while same_process "$c_reader" "$c_reader_ticks" && [ "$(uptime_s)" -lt "$c_reader_until" ]; do
        "$BB" sleep 1
    done
    if same_process "$c_reader" "$c_reader_ticks"; then
        "$BB" kill -KILL "$c_reader" 2>/dev/null || true
        CAPTURE_UNSAFE=1
        c_result=BLOCKED
        event "BLOCKED $c_name reader-cleanup-unproven"
    fi
    wait "$c_reader" 2>/dev/null || true
    "$BB" rm -f "$c_dir/pipe"

    c_size=$("$BB" stat -c %s "$c_dir/output.txt" 2>/dev/null) || c_size=0
    [ "$c_size" -ge "$c_limit" ] && [ "$c_result" != BLOCKED ] && c_result=WARN_TRUNCATED
    printf 'end_uptime=%s\nduration_s=%s\nproducer_rc=%s\nresult=%s\nbytes=%s\n' \
        "$(uptime_s)" "$(( $(uptime_s) - c_start ))" "$c_rc" "$c_result" "$c_size" >> "$c_dir/meta.txt"
    event "$c_result $c_name rc=$c_rc bytes=$c_size"
    [ "$c_result" = PASS ]
}

seal() {
    # Caller has joined capture/stream workers and excludes further markers.
    [ "${CAPTURE_UNSAFE:-0}" = 0 ] || return 1
    printf 'producer=COMPLETE\nqualification=NOT_RUN\n' > "$OUT/COMPLETE.txt"
    (cd "$OUT" && "$BB" find . -type f ! -name MANIFEST.sha256 ! -name SEALED.txt -print | "$BB" sort |
        while IFS= read -r s_file; do "$BB" sha256sum "$s_file" || exit 1; done) > "$OUT/MANIFEST.sha256" || return 1
    (cd "$OUT" && "$BB" sha256sum -c MANIFEST.sha256 >/dev/null) || return 1
    printf 'PASS: writers closed, file hashes verified; command failures remain in events.tsv\n' > "$OUT/SEALED.txt"
}
TS18_EMBEDDED_PAYLOAD_0_END
    cat > "$stage/checkpoint.sh" <<'TS18_EMBEDDED_PAYLOAD_1_END'
#!/system/bin/sh
# Read-only checkpoint worker. Started in a private session by the entry point.
PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin:/odm/bin
export PATH
unset LD_PRELOAD LD_LIBRARY_PATH ANDROID_LOG_TAGS ANDROID_PRINTF_LOG
umask 077
BB=/data/adb/magisk/busybox
SELF=$("$BB" readlink -f "$0") || exit 1
BASE=${SELF%/*}
. "$BASE/capture-lib.sh" || exit 1
case "${1:-}" in
 --produce) shift; produce "$@"; exit ;;
 --nav-state)
    echo 'Read-only helper evidence; never execute helper, remove claim or repair task.'
    for dir in /data/adb/ts18-launcher /data/user/*/com.cbkii.ts18launcher/no_backup; do
      [ -d "$dir" ] || continue
      "$BB" find "$dir" -maxdepth 4 -type d -name 'launch-*' -print
      "$BB" find "$dir" -maxdepth 4 -type f \( -name 'nav-window.sh' -o -name '*result*' \) -exec "$BB" stat -c '%u:%g %a %s %Y %n' '{}' \;
    done
    for journal in /data/adb/ts18-launcher/launch-*/state; do
      [ -f "$journal" ] && [ ! -L "$journal" ] || continue
      printf 'JOURNAL %s\n' "$journal"; "$BB" head -c 2048 "$journal"
    done
    echo 'Topway/window properties (observations, not actuators):'
    /system/bin/getprop | "$BB" grep -iE 'topway|dofun|freeform|window|display|desktop|multiwindow|lcd|panel|density'
    exit 0 ;;
 --app-files)
    user=$(/system/bin/am get-current-user | "$BB" awk '/^[0-9]+$/ {print; exit} /^Current user: [0-9]+$/ {print $3; exit}') || exit 1
    case "$user" in ''|*[!0-9]*) echo 'current-user UNKNOWN'; exit 1 ;; esac
    seen=0
    for dir in "/storage/emulated/$user/Android/data/app.organicmaps.incar/files/logs" "/data/user/$user/app.organicmaps.incar/files/logs"; do
      for name in app.log app.1.log app.2.log app.3.log app.4.log app.5.log; do
        file=$dir/$name
        [ -f "$file" ] && [ ! -L "$file" ] || continue
        seen=1
        printf '\nFILE %s\n' "$file"
        "$BB" stat -c 'original_bytes=%s modified_epoch=%Y' "$file"
        echo 'retained_tail_limit=1048576; live non-atomic read, may rotate while captured'
        "$BB" tail -c 1048576 "$file"
      done
    done
    [ "$seen" = 1 ] || { echo 'file_logs=UNAVAILABLE (not enabled, inaccessible or no logs yet)'; exit 1; }
    exit 0 ;;
 --launcher-prefs)
    user=$(/system/bin/am get-current-user | "$BB" awk '/^[0-9]+$/ {print; exit} /^Current user: [0-9]+$/ {print $3; exit}') || exit 1
    case "$user" in ''|*[!0-9]*) exit 1 ;; esac
    file=/data/user/$user/com.cbkii.ts18launcher/shared_prefs/ts18_launcher.xml
    [ -f "$file" ] && [ ! -L "$file" ] || exit 1
    echo 'Whitelisted configuration fragments only; raw preferences are not copied.'
    "$BB" grep -oE '<(string|boolean|int) name="(app\.navigation|app\.radio|app\.music|ui\.rail\.position|ui\.radio\.side|map\.enabled|media\.selection\.mode|media\.last\.explicit\.source)"[^<]*' "$file"
    profiles=/data/user/$user/com.cbkii.ts18launcher/shared_prefs/testing_profiles.xml
    if [ -f "$profiles" ] && [ ! -L "$profiles" ]; then
      echo 'Testing methods:'
      "$BB" grep -oE '<string name="(navigation|transition|departure|music|radio)"[^<]*' "$profiles"
    else echo 'testing_profile=DEFAULT (N1 bridge L2 M1 R0)'; fi
    exit 0 ;;
esac
ROOT=${BASE%/*}
LABEL=$1; DELAY=$2
LOCK=$ROOT/checkpoint-active
START=$(uptime_s); END=$((START + 300))
OUT=$ROOT/checkpoints/$("$BB" date -u +%Y%m%dT%H%M%SZ)-$LABEL-p$$
"$BB" mkdir -p "$OUT/commands" || exit 1
worker_ticks=$(ticks "$$")
[ -n "$worker_ticks" ] || exit 1
worker_ids=$("$BB" sed 's/.*) //' /proc/$$/stat | "$BB" awk '{print $3, $4}')
[ "$worker_ids" = "$$ $$" ] || { echo BLOCKED > "$OUT/UNSEALED.txt"; exit 1; }
atomic "$LOCK/owner" "$$ $worker_ticks"
atomic "$LOCK/output" "$OUT"
atomic "$ROOT/latest-checkpoint" "$OUT"
"$BB" setsid "$BB" sh "$BASE/ts18-startup-1.3.sh" --watchdog "$$" "$worker_ticks" "$((START + 330))" "$OUT" </dev/null >/dev/null 2>&1 &
trap 'echo interrupted > "$OUT/UNSEALED.txt"; exit 130' INT TERM HUP
{
  printf 'collector=consolidated-1\nlabel=%s\nstart_epoch=%s\nstart_uptime=%s\n' "$LABEL" "$("$BB" date +%s)" "$START"
  "$BB" id; "$BB" cat /proc/self/attr/current /proc/sys/kernel/random/boot_id
  "$BB" readlink /proc/self/ns/mnt /proc/1/ns/mnt
  printf 'measurement=forensic checkpoint; includes observer load\n'
} > "$OUT/CONTEXT.txt" 2>&1
"$BB" cp "$BASE/candidates.tsv" "$OUT/candidates.tsv"
"$BB" cp "$BASE/pr-heads.json" "$OUT/pr-heads.json"
"$BB" sleep "$DELAY"
cap() { name=$1; shift; capture "$name" 6 1048576 "$@"; }
# Capture the prepared UI before slower diagnostic calls. output.txt is PNG only
# if the PNG signature is valid; failures stay ordinary evidence, not fake images.
capture screenshot 6 8388608 /system/bin/screencap -p
cap clock "$BB" sh -c 'date -u; cat /proc/uptime; cat /proc/sys/kernel/random/boot_id'
cap activity /system/bin/dumpsys activity activities
cap windows /system/bin/dumpsys window windows
cap window-full /system/bin/dumpsys window
capture surfaceflinger 8 8388608 /system/bin/dumpsys SurfaceFlinger
cap surface-layers /system/bin/dumpsys SurfaceFlinger --list
cap input-routing /system/bin/dumpsys input
cap nav-private-state "$BB" sh "$SELF" --nav-state
cap wm-help /system/bin/wm help
cap am-help /system/bin/am help
cap recents /system/bin/dumpsys activity recents
cap stacks /system/bin/dumpsys activity stacks
cap media-session /system/bin/dumpsys media_session
cap audio /system/bin/dumpsys audio
cap audio-flinger /system/bin/dumpsys media.audio_flinger
cap audio-policy /system/bin/dumpsys media.audio_policy
cap location /system/bin/dumpsys location
cap sensors /system/bin/dumpsys sensorservice
cap power /system/bin/dumpsys power
cap deviceidle /system/bin/dumpsys deviceidle
cap display /system/bin/dumpsys display
cap wm-size /system/bin/wm size
cap wm-density /system/bin/wm density
cap current-user /system/bin/am get-current-user
cap home /system/bin/cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
cap listeners /system/bin/settings get secure enabled_notification_listeners
cap font-scale /system/bin/settings get system font_scale
cap night-mode /system/bin/dumpsys uimode
cap fingerprint /system/bin/getprop ro.build.fingerprint
cap api /system/bin/getprop ro.build.version.sdk
cap abi /system/bin/getprop ro.product.cpu.abilist
cap locale /system/bin/getprop persist.sys.locale
cap launcher-preferences "$BB" sh "$SELF" --launcher-prefs
cap processes /system/bin/ps -A -Z
cap proc-sample "$BB" sh "$BASE/ts18-startup-1.3.sh" --sample "$$"
cap packages-ready-1 /system/bin/pm list packages -U
if "$BB" grep -q '^package:.* uid:[0-9]' "$OUT/commands/packages-ready-1/output.txt" &&
   "$BB" grep -qx 'result=PASS' "$OUT/commands/packages-ready-1/meta.txt"; then
  atomic "$OUT/UID_MAP_SOURCE.txt" 'commands/packages-ready-1'
fi
for pkg in com.cbkii.ts18launcher app.organicmaps.incar com.tw.media com.navimods.radio; do
  cap "package-$pkg" /system/bin/dumpsys package "$pkg"
  capture "apk-$pkg" 12 1048576 "$BB" sh "$BASE/ts18-startup-1.3.sh" --apk "$pkg"
  cap "appops-$pkg" /system/bin/cmd appops get "$pkg"
  cap "meminfo-$pkg" /system/bin/dumpsys meminfo "$pkg"
  cap "gfxinfo-$pkg" /system/bin/dumpsys gfxinfo "$pkg" framestats
done
cap mounts "$BB" cat /proc/mounts /proc/self/mountinfo
cap storage /system/bin/dumpsys mount
cap disks "$BB" df -k
cap thermal /system/bin/dumpsys thermalservice
cap kernel /system/bin/dmesg
capture logcat 8 16777216 /system/bin/logcat -d -b all -t 12000 -v epoch
capture organicmaps-file-logs 10 14680064 "$BB" sh "$SELF" --app-files
cap dropbox-index /system/bin/dumpsys dropbox
# Bounded recent tombstones/ANRs; never clear or modify their source.
capture crashes 8 8388608 "$BB" sh -c '
 count=0
 for f in /data/tombstones/tombstone_* /data/anr/*; do
   [ -f "$f" ] || continue
   find_recent=$(/data/adb/magisk/busybox find "$f" -mmin -120 -type f)
   [ -n "$find_recent" ] || continue
   printf "\nFILE %s\n" "$f"
   /data/adb/magisk/busybox head -c 1048576 "$f"
   count=$((count + 1)); [ "$count" -lt 6 ] || break
 done'
{
  printf 'package\tinstalled_apk_hash\texpected_hash\tresult\n'
  while IFS="$(printf '\t')" read -r pkg expected source; do
    [ -n "$pkg" ] || continue
    case "$pkg" in \#*) continue ;; esac
    file=$OUT/commands/apk-$pkg/output.txt
    actual=$("$BB" awk '$2 ~ /\/base.apk$/ && length($1)==64 {print $1}' "$file")
    state=UNKNOWN
    if "$BB" grep -qx 'result=PASS' "$OUT/commands/apk-$pkg/meta.txt"; then
      case "$actual" in ''|*' '*|*'
'*) state=UNKNOWN ;; *)
        case ",$expected," in *",$actual,"*) state=MATCH ;; *) state=MISMATCH ;; esac ;;
      esac
    fi
    printf '%s\t%s\t%s\t%s\n' "$pkg" "${actual:-UNKNOWN}" "$expected" "$state"
  done < "$BASE/candidates.tsv"
} > "$OUT/PROVENANCE.tsv"
event "PHASE checkpoint-complete elapsed_s=$(( $(uptime_s) - START ))"
seal || { echo 'cleanup/seal failed' > "$OUT/UNSEALED.txt"; exit 1; }
printf 'SEALED %s\nPhysical qualification remains NOT_RUN; inspect command statuses and PROVENANCE.tsv.\n' "$OUT"
"$BB" rm -f "$LOCK/owner" "$LOCK/output"
"$BB" rmdir "$LOCK"
TS18_EMBEDDED_PAYLOAD_1_END
    cat > "$stage/guide.sh" <<'TS18_EMBEDDED_PAYLOAD_2_END'
#!/system/bin/sh
# UI only. All writes/captures go through the verified single entry point.
set -u
SELF=$1 ROOT=$2 BB=$3
CASES=$ROOT/toolkit/cases.tsv
export TS18_GUIDE=1

ask() {
  printf '\n%s ' "$1"
  IFS= read -r answer || { echo; exit 0; }
  answer=$(printf '%s' "$answer" | "$BB" tr '[:upper:]' '[:lower:]')
}
pause() { printf '\nPress Enter to continue (Ctrl+C leaves all evidence in place): '; IFS= read -r ignored || exit 0; }
run() { "$BB" sh "$SELF" "$@"; }
result_for() {
  [ -f "$ROOT/results.tsv" ] || return 0
  "$BB" awk -F '\t' -v id="$1" '$3==id {v=$4} END {print v}' "$ROOT/results.tsv"
}
wait_checkpoint() {
  elapsed=0
  while [ -d "$ROOT/checkpoint-active" ] && [ "$elapsed" -lt 345 ]; do
    [ $((elapsed % 30)) -ne 0 ] || printf '  Snapshot still gathering; %s seconds.\n' "$elapsed"
    "$BB" sleep 5; elapsed=$((elapsed + 5))
  done
  if [ -d "$ROOT/checkpoint-active" ]; then
    echo 'Snapshot still active/stale. Preserve it; use status. It is not a pass.'
    return 1
  fi
  if [ -r "$ROOT/latest-checkpoint" ]; then
    IFS= read -r cp < "$ROOT/latest-checkpoint"
    if [ -r "$cp/SEALED.txt" ]; then
      printf 'Snapshot sealed: %s\n' "$cp"
      return 0
    fi
  fi
  echo 'No sealed snapshot. Inspect status and preserve partial evidence.'
  return 1
}
wait_capture() {
  elapsed=0
  while [ -d "$ROOT/engine/active" ] && [ "$elapsed" -lt 720 ]; do
    [ $((elapsed % 30)) -ne 0 ] || printf '  Trace still gathering/sealing; %s seconds.\n' "$elapsed"
    "$BB" sleep 5; elapsed=$((elapsed + 5))
  done
  if [ -d "$ROOT/engine/active" ]; then
    echo 'Trace is still active/stale. Preserve it and inspect status before starting another.'
    return 1
  fi
  if [ -r "$ROOT/engine/latest" ]; then
    IFS= read -r trace < "$ROOT/engine/latest"
    if [ -r "$trace/SEALED.txt" ] && [ -r "$trace/COMPLETE.txt" ]; then
      printf 'Trace sealed: %s\n' "$trace"
      return 0
    fi
  fi
  echo 'Trace did not seal. Keep partial evidence; do not call it a diagnostic pass.'
  return 1
}
start_trace() {
  mode=$1 duration=$2
  run start "$mode" "$duration" || return 1
  elapsed=0
  while [ "$elapsed" -lt 180 ]; do
    if [ -r "$ROOT/engine/active/output" ]; then
      IFS= read -r trace < "$ROOT/engine/active/output"
      if [ -r "$trace/events.tsv" ] && "$BB" grep -q 'PHASE measurement-start' "$trace/events.tsv"; then
        printf 'Trace is measuring now (%s seconds). Switch to the app and do the manual actions.\n' "$duration"
        return 0
      fi
    fi
    [ -d "$ROOT/engine/active" ] || { echo 'Trace ended before measurement; inspect status.'; return 1; }
    [ $((elapsed % 20)) -ne 0 ] || printf '  Waiting for measurement phase; %s seconds.\n' "$elapsed"
    "$BB" sleep 5; elapsed=$((elapsed + 5))
  done
  echo 'Measurement phase was not observed in 180 seconds. Inspect status; no pass inferred.'
  return 1
}
baseline() {
  echo 'BASELINE: Have you installed the exact two TESTING APKs and retained DoFun as recovery HOME?'
  echo 'This script cannot install an APK or change HOME. You must check the download hashes first.'
  ask 'Enter=queue preflight snapshot, n=record Not Run, b=back:'
  case "$answer" in b) return ;; n) record Q0 N 0; return ;; '') ;; *) echo 'No action selected.'; return ;; esac
  run preflight || { echo 'Preflight did not queue; use status and the recovery section.'; record Q0 n 0; return; }
  echo 'Wait here. The snapshot may take several minutes. No app interaction is required.'
  wait_checkpoint || { run status; record Q0 n 0; return; }
  if [ -r "$ROOT/latest-checkpoint" ]; then
    IFS= read -r cp < "$ROOT/latest-checkpoint"
    printf '\nInstalled base APK comparison (both must read MATCH):\n'
    "$BB" cat "$cp/PROVENANCE.tsv" 2>/dev/null || echo 'PROVENANCE unavailable.'
    echo 'Also check Android user, API/build, HOME and notification access in the captured files.'
    echo 'MATCH proves the base bytes only; per-user grants and signer may still need review.'
  fi
  record Q0 '' 2
}
checkpoint_case() {
  id=$1
  printf 'Snapshot starts after an 8-second countdown. Switch to the relevant app screen now.\n'
  run checkpoint "$id" 8 || return 1
  wait_checkpoint
}
record() {
  id=$1 selected=${2:-} evidence=${3:-0}
  while :; do
    if [ -n "$selected" ]; then answer=$selected; selected=; else ask 'Result: p=Pass, f=Fail, n=Not Run / other, b=back:'; fi
    case "$answer" in
      p) verdict=PASS; break ;;
      f) verdict=FAIL; break ;;
      n)
        ask '1=Not Run, 2=Blocked, 3=Uncertain, 4=Warning (Enter=Not Run):'
        case "$answer" in ''|1) verdict=NOT_RUN ;; 2) verdict=BLOCKED ;;
          3) verdict=UNKNOWN ;; 4) verdict=WARN ;; *) echo 'Choose 1, 2, 3 or 4.'; continue ;; esac
        break ;;
      b) return 1 ;;
      *) echo 'Type one letter, then Enter.' ;;
    esac
  done
  if [ "$verdict" = FAIL ]; then
    echo 'Keep the first failure. Avoid Retry/restart until its state is captured.'
    ask 'Current failure screen available? Enter=take delayed snapshot, n=skip snapshot:'
    case "$answer" in '' ) if checkpoint_case "$id"; then [ "$evidence" = 0 ] && evidence=2; else echo 'Snapshot incomplete; physical result remains separate.'; fi ;; esac
  elif [ "$verdict" = PASS ] && [ "$id" != Q0 ]; then
    ask 'Capture current app screen? y=yes (8-second delay), Enter=continue:'
    case "$answer" in y) if checkpoint_case "$id"; then [ "$evidence" = 0 ] && evidence=2; else echo 'Snapshot incomplete; physical result remains separate.'; fi ;; esac
  fi
  printf 'Optional note (one line; Enter leaves it blank): '
  IFS= read -r note || exit 0
  if [ "$verdict" = FAIL ] && [ -z "$note" ]; then
    echo 'A failure note is valuable: expected/actual action, source, time, and whether Retry was touched.'
  fi
  case "$evidence" in 0) run result "$id" "$verdict" "$note" --none ;;
    2) run result "$id" "$verdict" "$note" --checkpoint-only ;;
    *) run result "$id" "$verdict" "$note" ;; esac
  echo 'Recorded. Repeating later adds a new row; it does not erase this result.'
}
special_d15a() {
  echo 'The Guide will run a 180-second forensic trace and a 90-second performance trace.'
  echo 'Wait for measurement before taking manual actions. Press Enter to begin.'
  pause
  start_trace forensic 180 || return 1
  echo 'Leave source idle, then return here. A marker records what you just observed; it does not press a button.'
  ask 'Enter=mark observed IDLE, n=skip markers:'
  if [ "$answer" != n ]; then
    run mark IDLE || echo 'Marker unavailable; record the gap.'
    echo 'Switch to HOME, press Auxio Play once, listen, then return here.'
    pause
    run mark AUXIO_PLAY || echo 'Marker unavailable; record the gap.'
    echo 'Switch to HOME, press Pause once, then return here.'
    pause
    run mark AUXIO_PAUSE || echo 'Marker unavailable; record the gap.'
    echo 'Switch to HOME, press Play again, then return here. This repeats a label as a distinct occurrence.'
    pause
    run mark AUXIO_PLAY || echo 'Marker unavailable; record the gap.'
  fi
  wait_capture || return 1
  start_trace performance 90 || return 1
  echo 'During measurement, use an ordinary source. Do not take a checkpoint or run another collector.'
  wait_capture
}
special_d15b() {
  echo 'The Guide starts a 30-second trace and requests early stop. This is a separate cleanup trial.'
  pause
  start_trace forensic 30 || return 1
  run stop || return 1
  wait_capture || return 1
  echo 'Next is an ordinary 30-second run. Do not request stop for this one.'
  start_trace forensic 30 || return 1
  wait_capture
}
case_action() {
  id=$1
  line=$("$BB" awk -F '\t' -v target="$id" '$1==target {print; exit}' "$CASES")
  [ -n "$line" ] || { echo 'Unknown case.'; return 1; }
  IFS="$(printf '\t')" read -r cid group title actions expected evidence <<EOF
$line
EOF
  printf '\n%s [%s] %s\n\nWHAT TO DO:\n%s\n\nPASS ONLY WHEN:\n%s\n\nEVIDENCE:\n%s\n' "$cid" "$group" "$title" "$actions" "$expected" "$evidence"
  previous=$(result_for "$id")
  [ -z "$previous" ] || printf '\nLatest recorded result: %s (repeat is kept as a new observation).\n' "$previous"
  ask 'Enter=start this case, n=skip/other, b=back to menu:'
  case "$answer" in b) return 2 ;; n) record "$id" n 0; return 0 ;; '') ;; *) echo 'No action selected.'; return 0 ;; esac
  if [ "$id" = D15a ]; then
    special_d15a; trace_ok=$?
  elif [ "$id" = D15b ]; then
    special_d15b; trace_ok=$?
  else
    trace_mode=forensic
    [ "$id" != D15c ] || trace_mode=performance
    case "$id" in W*|C*) trace_mode=mapwindow ;; esac
    trial_seconds=300; [ "$group" != Screening ] || trial_seconds=90
    printf 'Prepare the app before the %s trace. The 300-second measurement window begins after initial probes.\n' "$trace_mode"
    echo 'A long sleep/boot/idle sequence may exceed it. Note the actual boundary and use N for uncovered variants.'
    ask 'Enter=start bounded trace, m=manual action without trace, b=back:'
    case "$answer" in b) return 2 ;; m) trace_ok=2 ;;
      '')
        if start_trace "$trace_mode" "$trial_seconds"; then
          echo 'Switch to the app, do the action, then return to this screen.'
          pause
          wait_capture; trace_ok=$?
          echo 'If measurement ended while you were still doing the action, note that coverage gap or choose N/Uncertain.'
        else trace_ok=1; fi ;;
      *) echo 'No action selected.'; return 0 ;;
    esac
  fi
  if [ "$trace_ok" = 1 ]; then
    echo 'Diagnostic trace failed or lacks a seal. Record a physical failure only if you observed it; otherwise use N/Uncertain.'
    run status
    record "$id" '' 0
  elif [ "$trace_ok" = 2 ]; then
    echo 'No trace was collected. Perform the actions now, return, and then record the result.'
    pause
    record "$id" '' 0
  else
    echo 'Review what you actually saw. A sealed trace never proves app behaviour.'
    record "$id" '' 1
  fi
  return 0
}
group_flow() {
  group=$1
  for id in $("$BB" awk -F '\t' -v group="$group" '$2==group {print $1}' "$CASES"); do
    previous=$(result_for "$id")
    if [ -n "$previous" ]; then
      printf '\n%s already recorded: %s. Enter=next, r=repeat, b=menu: ' "$id" "$previous"
      IFS= read -r reply || exit 0
      case "$reply" in r|R) ;; b|B) return ;; *) continue ;; esac
    fi
    while :; do
      case_action "$id"; rc=$?
      [ "$rc" != 2 ] || return
      [ "$group" = 'Map window' ] || [ "$group" = Screening ] || break
      echo 'One row is one observed trial; target counts apply separately to each exact APK pair.'
      ask 'r=another trial of this case, Enter=next case, b=menu:'
      case "$answer" in r) ;; b) return ;; *) break ;; esac
    done
  done
  echo 'End of group. Review missing variants and failures in Cases. Earlier failures are retained.'
  pause
}

smoke_flow() {
  echo 'SHORT SMOKE GATE: one continuous capture; no long repetition campaign.'
  echo 'Keep Maps fixed. Prepare music/storage/station normally. No force-stop or data clearing.'
  echo 'Use Settings > Diagnostics & system > Testing methods to choose N1, Bridge and L2.'
  echo 'Cold state is recorded from tasks/processes; do not guess that reboot means cold.'
  ask 'Enter=start capture, b=back:'
  [ "$answer" != b ] || return
  start_trace mapwindow 300 || return
  for id in S1 S2 S3 S4 S5 S6 S7 S8 S9; do
    line=$("$BB" awk -F '\t' -v id="$id" '$1==id {print $3 "\n" $4 "\nPASS: " $5}' "$CASES")
    printf '\n%s\nSwitch to HOME, do this action once, then return here.\n' "$line"
    ask 'p=Pass, f=Fail, n=Not Run/uncertain, b=stop:'
    case "$answer" in p) verdict=PASS ;; f) verdict=FAIL ;; n) verdict=UNKNOWN ;; *) verdict=NOT_RUN ;; esac
    run result "$id" "$verdict" 'Smoke gate; see correlated continuous trace' || return
    if [ "$id" = S9 ] && [ "$verdict" = UNKNOWN ]; then
      echo 'Retry case not available on this healthy run; recorded separately.'; break
    fi
    if [ "$verdict" != PASS ]; then
      echo 'Smoke stopped. Do not Retry/reboot yet. Original failure is retained.'
      run stop; wait_capture || :
      if [ "$verdict" = FAIL ]; then checkpoint_case "$id" || :; fi
      printf 'Optional note (Enter=blank): '; IFS= read -r note || return
      [ -z "$note" ] || run result "$id" "$verdict" "$note"
      run export
      echo 'Share this archive. Do not start the long qualification run.'
      return
    fi
    # A timed-out capture is not continued under a stale evidence reference.
    if [ ! -d "$ROOT/engine/active" ]; then
      wait_capture || return
      start_trace mapwindow 300 || return
    fi
  done
  run stop; wait_capture || :
  run export
  echo 'Smoke observations passed. Next use Screening; no physical qualification is inferred.'
}
screen_flow() {
  echo 'Select one method in Settings > Diagnostics & system > Testing methods, then return here.'
  echo 'N0: normal opening only. N1: direct compact. N2: normal open first, then HOME; Bridge required.'
  echo 'Hold launch method fixed when comparing Bridge vs Intent (N1 only), then L1 vs L2.'
  echo 'Compare M0/M1 or R1/R2 separately. Keep APKs, media/storage and permissions unchanged.'
  echo 'Profiles and exact installed hashes are read automatically for every trial.'
  group_flow Screening
}

echo 'TS18 TESTING guided validation — one key plus Enter; no case ID to type.'
echo 'This guide never installs apps, changes HOME/settings or decides physical success for you.'
echo 'Use it while parked. A passenger must handle any capture in a moving vehicle.'
while :; do
  printf '\n1 Baseline / APK identity  2 Launcher  3 Organic Maps  4 Diagnostics  5 Integration\n6 Cases/progress  7 Export archive  8 Status  9 Map-window trials  s Smoke gate  c Screening  0 Exit\n'
  ask 'Choose one number:'
  case "$answer" in
    s) smoke_flow ;;
    c) screen_flow ;;
    1) baseline ;;
    2) group_flow Launcher ;;
    3) group_flow 'Organic Maps' ;;
    4) group_flow Diagnostics ;;
    5) group_flow Integration ;;
    9)
      echo 'Use the repair candidate with ONE unchanged Maps APK. Finish Smoke and Screening first.'
      echo 'Run baseline after each APK change. Record one transition per trial; no Retry before failure capture.'
      group_flow 'Map window' ;;
    6) run cases; pause ;;
    7) echo 'Export copies current evidence to Downloads. Incomplete trials stay incomplete.'; run export; pause ;;
    8) run status; pause ;;
    0) exit 0 ;;
    *) echo 'Choose 0 through 9, s or c.' ;;
  esac
done
TS18_EMBEDDED_PAYLOAD_2_END
    cat > "$stage/cases.tsv" <<'TS18_EMBEDDED_PAYLOAD_3_END'
Q0	Setup	Candidate identity and safe baseline	Install the two exact TESTING APKs through Android package UI after comparing downloaded SHA-256 values; keep DoFun HOME as recovery. Use Guide baseline to capture installed package hashes and HOME. Read the displayed PROVENANCE rows; check both say MATCH and the current user/build/API/notification listener are plausible.	Both candidate base APK hashes MATCH; correct device/user/HOME are seen; no conflicting diagnostic owner. A missing or different hash is not a pass.	Preflight PROVENANCE.tsv, package/appops/home/context and screenshots.
T10	Launcher	Drawer and long press	On HOME open the app drawer twice. Hold an ordinary grid app icon until Android App Info appears. Return HOME; hold a configurable quick shortcut and check that its edit/reassign screen appears. Scroll the drawer and reopen it.	Labels/icons appear without long freezes; normal grid long press opens App Info; quick shortcut long press edits that shortcut.	Drawer and App Info screens, activity/window/launcher logs. PR #10/#13.
T11	Launcher	Map task survives HOME return	Open Organic Maps from HOME. Open a different app, then press HOME; repeat three times. Open and close the drawer. Enter map fullscreen and return HOME. Tap the restored map. If missing, leave it missing for the failure capture before any Retry.	Same intended map task reappears and responds; no Retry or second map task is needed. Note each trial separately if results differ.	Task IDs, activities/windows, screens and TS18Nav logs. PR #11/#13/#14.
T12	Launcher	Route and compact window	Plan a normal route in Organic Maps. From HOME enter fullscreen map and return, then open/close drawer. Repeat while a permission or download dialog is normally present if reachable; never fabricate a dialog.	Route/task persists; HOME compact map fits its allocated area; launcher does not unexpectedly bring a source app to foreground. Unavailable dialog variant is N.	Route screen, task IDs, bounds/insets, focus and logs. PR #11/#14.
T13	Launcher	Cold radio Play	Choose NavRadio as the HOME music source. Close it normally so the service is cold. Tap HOME Play exactly once and wait for the documented bounded readiness interval; listen. Tap Pause once. Record whether a second Play was needed.	Audible radio starts from one Play; Pause works once; preparation does not unexpectedly foreground NavRadio.	Phone video/audio, session/focus/service/logs. PR #10/#13/#14.
T14	Launcher	Auxio controls and now playing	Choose Auxio as HOME source. Tap Play, Pause, Previous and Next once each. Watch title/art and Play/Pause icon after changes. Switch to radio and back. Listen for duplicate skips or stale labels.	One command per tap; current metadata and icon follow real playback, including after source changes.	Audio/video, media sessions, metadata, focus and logs. PR #10/#13/#14.
T15	Launcher	Notification listener recovery	Restart the launcher normally. Play Auxio or radio and check HOME title/button. In Android Settings, inspect notification access. If safe and familiar, revoke then regrant launcher access through Settings and retry; do not edit secure settings in Termux.	Listener available or recovers; media state refreshes; a phone call must not appear as the chosen music source. Mark optional revoke variant N if not attempted.	Listener setting and event/session logs. PR #13/#14.
T16	Launcher	Layout after a change	In launcher Settings change the normal rail or radio-side option, return HOME, and inspect placement. Toggle fullscreen/compact map and return. If an experimental map option exists in UI, try it once. Restore the original setting afterwards.	Map/control bounds are correct, system bars are not double-counted, and layout stabilises when nothing changes. Unavailable option is N.	Before/after screenshots, display/window/gfx evidence. PR #14.
T17	Launcher	Cold start and media	With the unit safely parked, make three separate normal cold starts if practical. Each time observe HOME before opening Auxio or NavRadio, tap one Play, and restore Organic Maps after visiting another app. Note boot type/time.	HOME becomes useful, source Activities do not steal focus for preparation, Play and map return work on each run. Mark unattempted boots N.	Boot IDs, post-boot screens and timing/video. PR #14.
O55a	Organic Maps	HUD readability	Browse a route in light and dark appearance while safely stationary. Inspect immediate turn/road, AFTER, LANES and END when reachable; note any warning tint. Avoid driving to force a warning.	Light text on dark turn/road surface remains readable; no NOW/NEXT captions; AFTER is quieter and LANES stronger; END usable. Unreachable variants N.	Screens and renderer/window logs. PR #55.
O55b	Organic Maps	Footer and text sizes	On a route inspect remaining distance and duration in normal and compact window, font size 1.0 and 1.3 if available, long units and an RTL locale only if supported. Note a normal short and long route; extreme values require a safe fixture.	Values/units fit without overlap or wrong conversion. Record each attempted variant; mark fixture-only extremes N rather than inventing a pass.	Screens, locale/font/window and route data. PR #55.
O55c	Organic Maps	Search and quick destinations	Search for a long place name. Expand/collapse Quick Destinations, open action menu and driver dialog if available, and inspect missing metadata examples if naturally encountered. Repeat in compact window/enlarged font.	Text may truncate clearly but intended actions remain reachable and unambiguous; missing fields do not leave misleading controls.	Search/modal screens and window bounds. PR #55.
O55d	Organic Maps	Route edit and alternatives	Create route START to END, add an intermediate stop, select a non-transit alternative, start guidance, then edit route once and reopen planning. Observe first turn and after-turn reroute when safe.	Selected route/points survive the expected transitions and only one requested rebuild occurs; no loss of incomplete/passed route data.	Route UI, planning and routing logs. PR #55/#56.
O55e	Organic Maps	Known posted limit	Use a safe known road with mapped speed data. Compare displayed limit at route START, stationary/low speed and after a turn with the actual road/map data. Note an explicit unrestricted segment only if known. Do not speed to test.	Route limit wins when known; unknown route may use a fresh current-road limit; no invented value or unexplained 10 km/h delay.	Road/map provenance, timestamps, sign/screen and route state. PR #55.
O55f	Organic Maps	Free drive fallback	Without a route, observe a mapped road, a turn, parking/off-road and GPS degradation if it occurs naturally. Use Android UI to turn Location off/on only if safe, then restore it; try a normal map replacement if available.	Fresh directed-road limit may appear and clears when unknown/stale; a fresh usable fix restores it; no stale sign persists.	Before/after screens, provider/fix age and logs. PR #55.
O55g	Organic Maps	My Position modes	On map drag away and tap My Position. Try Follow and Follow-and-Rotate through normal UI, change heading if naturally possible and tap again. Repeat with a stale/no-fix state only if it occurs; do not inject locations.	Recentre retains chosen follow/rotation mode and route intent; a usable fix does not start unnecessary recovery or flip explicit-off choice.	Map screens, mode/provider state and timestamps. PR #55.
O55h	Organic Maps	No-fix recovery handoff	Only if normal no-fix occurs, tap My Position; use Android permission/Location Settings if prompted, return to map and observe the deferred recentre. If supported, rotate/recreate normally; then drag or change mode to cancel pending intent.	Recovery reaches current Activity once on a fresh accepted fix, while deliberate drag/mode change cancels it. If no safe no-fix state, record N.	Activity/permission/provider logs and screens. PR #55.
O55i	Organic Maps	Track Recording switch	Open main menu Track Recording. Toggle ON; handle permission prompt normally. Reopen menu; toggle OFF and choose Save/Stop/Cancel as intended. In another run stop from notification while menu had shown ON, reopen and tap stale switch once. Try one rapid gesture only if safe.	Visible switch follows real recording; one action per tap; stale ON does not restart; saved track visible through app UI. Denied-permission variant N if not tried.	Screens/notifications, recorder/service logs. PR #55/#56.
O55j	Organic Maps	Map controls in each layout	On normal, compact and landscape map views inspect My Position and main/Advanced menus. Open one ordinary menu row. Restore previous layout.	Exactly one usable My Position control; no map recording FAB, obsolete visibility switch or duplicate Advanced recording row; ordinary menu still works.	Screens in each layout/window state. PR #55.
O56a	Organic Maps	Missing map and route planning	When a region is genuinely missing, try START and accept/decline the normal map download prompt in separate trials. Otherwise mark that variant N. In a normal mapped route, edit one stop and alternative once.	Prompt handles either choice; route metadata and selected alternatives stay coherent, without duplicate rebuild. No simulated missing map.	Download/map status, route UI and logs. PR #56.
O56b	Organic Maps	Resume intent and idle expiry	Plan a route and note whether auto-resume is explicitly enabled. Leave it idle for a short period; then a policy-expired period if practical, recording actual times. Repeat after ordinary reboot only if safe. Stop a recording and check resume choice persists.	Short idle preserves intended route; after configured expiry a passive fix does not revive it; recorder teardown does not erase explicit consent.	UTC/uptime/boot, route/provider/service and screenshots. PR #56.
O56c	Organic Maps	Shared location consumers	Run navigation and Track Recording together; stop one and confirm the other still gets position. If Driving mode is available repeat with it. Exercise car compass reconnect only when a supported host is present.	Remaining consumer continues; no extra listener/wake leak is observed. Unavailable host/Driving variants N.	Location/sensors/power/service evidence and screen. PR #56.
O57	Organic Maps	Default arrow and optional texture	With default settings observe route arrow in normal and compact map. If the app exposes a supported custom arrow option or fixture, try a valid path and missing/invalid optional path; otherwise skip those variants.	Default arrow renders without a known-missing PNG probe; optional valid texture works and invalid one falls back safely. Do not edit private preferences to force this.	Arrow screens and renderer/file-log evidence. PR #57.
D15a	Diagnostics	Normal forensic and performance	Use the Guide diagnostic routine for a short forensic capture followed by a short performance capture; compare completion, seals, hashes and repeated marker occurrences if a media source is available.	Both bounded runs finish and seal with manifest verification; physical media states must still be judged by a person.	Capture events, command meta, seals, markers and export check. PR #15.
D15b	Diagnostics	Early stop and recovery	Use the Guide routine to start a 30-second capture, request stop promptly, wait for cleanup, then start a normal 30-second run. Read warnings rather than assuming stop is a full diagnostic pass.	Stop is recorded and worker cleans up; next run starts and seals. Missing optional APIs are explicit warnings, not silent PASS.	STOP/COMPLETE/SEALED, owner identity and producer meta. PR #15.
D15c	Diagnostics	Read-only causality evidence	During ordinary idle and active Auxio/radio/map use, collect performance traces. Review UID/package mapping, remount events, GMS/GSF state, audio focus and app CPU/memory with actual time boundaries.	Evidence is sufficient to distinguish observation from cause; unknown UID or noisy logs are recorded as uncertain. Do not alter GMS/ART/SELinux/modules.	UID map, mount, GMS, media/audio, process/memory and timing. PR #15.
X1	Integration	ACC sleep and wake	If safe on a parked unit, record a short and a policy-expired ACC sleep/wake separately. After wake check HOME, Play, route, posted limit and recording. Note exact duration and boot ID; do not touch Termux while driving.	Each attempted lifecycle returns usable app state in line with saved intent. Mark unavailable long interval N; assess #14/Organic Maps requirements distinctly from roadmap items.	Boot/uptime and pre/post checkpoints/traces. Cross-app.
X2	Integration	Ordinary interruptions	If available, enter and exit reverse camera, an ordinary phone call, SystemUI panel, and normal USB removal/reinsert one at a time; check map and audio return. Never kill SystemUI or disconnect unsafe equipment.	User-visible app state recovers for attempted interruptions; each unavailable branch N. These are primarily roadmap scenarios for #10/#11/#13.	Before/after screen, task/focus/media/provider and logs. Cross-app.
X3	Integration	Restart, reboot and PiP	Use normal UI to restart an app process if available, then ordinary reboot/cold power separately when practical. Recheck candidate hash after any reinstall and repeat affected map/media tests. Enter/exit PiP only if supported.	Behaviour after each attempted lifecycle matches saved intent; distinguish restart from reboot/cold power. Unavailable PiP N.	Boot ID, package hash, task/window/media and screens. Cross-app.
W1	Map window	Compact map reference	Return HOME with a loaded map. Wait 10 seconds. Pan left/right once, tap My Position once, open and close the main menu. Leave the map visible for 20 seconds.	Actual map pixels draw and change; controls respond at their visible positions; no blank/stale frame or focus theft.	During-transition screenshot/ATM/WMS/input/layer samples; full checkpoint; phone video and exact candidate hashes.
W2	Map window	Other app to HOME — target 30 trials	From working HOME open an ordinary app. Stay there 10 seconds: Maps must not steal focus. Press HOME once, wait up to 45 seconds without Retry. Pan the compact map and tap one map control. Leave HOME visible 20 seconds.	Automatic compact return; route retained; aligned touch; no duplicate launch or unexpected foreground. One result represents one return.	During-transition screenshot/ATM/WMS/input/layer samples; full checkpoint; phone video and exact candidate hashes.
W3	Map window	Fullscreen to HOME — target 20 trials	From HOME use Open fullscreen once. Pan map and open/close menu. Press HOME once. Wait up to 45 seconds without Retry, pan compact map and tap My Position. Leave HOME visible 20 seconds.	Both states render and accept correctly aligned touch; route retained; automatic return. Record any blank frame, crop, stretch or misplaced touch.	During-transition screenshot/ATM/WMS/input/layer samples; full checkpoint; phone video and exact candidate hashes.
W4	Map window	Cold startup — target 20 genuine cold trials	Use only a normally available Android app-close/restart control; never force-stop from Termux. Start recording first, then open HOME/Maps by normal UI. Wait up to 45 seconds without Retry and test pan/menu. A reboot/cold boot needs a separate post-boot trace and cannot be covered by a killed preboot collector.	Observed cold Activity startup reaches interactive compact map without Retry. If cold state cannot be established choose N/Uncertain; warm launches do not count.	During-transition screenshot/ATM/WMS/input/layer samples; full checkpoint; phone video and exact candidate hashes.
W5	Map window	Bootstrap and permission ownership	Only when a real download/permission/settings screen occurs: leave it visible 10 seconds, complete it normally, return HOME and wait. Do not revoke permissions or delete maps to manufacture the screen.	Prompt stays usable; no focus theft/redelivery; map becomes interactive after normal completion. Unavailable stage is Not Run.	During-transition screenshot/ATM/WMS/input/layer samples; full checkpoint; phone video and exact candidate hashes.
W6	Map window	Matched failed and recovered map	When the map is blank, frozen, cropped or touch is misplaced: do not press Retry. Run this trace while leaving failure visible 30 seconds. With phone video running use Retry ONCE only after the first 30 seconds, wait and test pan/menu. Do not reboot/clear data.	This is diagnostic comparison, not a recovery acceptance pass: record FAIL for original failure even if Retry recovers. Note whether capture began before or after failure.	During-transition screenshot/ATM/WMS/input/layer samples; full checkpoint; phone video and exact candidate hashes.
W7	Map window	Overlay return and touch	One naturally available overlay at a time: SystemUI shade, regular phone call or reverse camera while parked. Dismiss it normally, return HOME, wait, pan and open menu. Record each overlay separately.	Overlay owns focus while active; map returns and touch is aligned afterwards. Unavailable variants Not Run.	During-transition screenshot/ATM/WMS/input/layer samples; full checkpoint; phone video and exact candidate hashes.
W8	Map window	Layout and day/night lifecycle	Through ordinary launcher settings change rail/radio-side once; return HOME and test pan/menu. Use fullscreen and return. Change Maps day/night once through normal UI, test again. Restore original settings.	Map geometry, pixels and touch agree; unchanged mode returns do not lose route; real theme change remains usable. Record each variant separately.	During-transition screenshot/ATM/WMS/input/layer samples; full checkpoint; phone video and exact candidate hashes.
S1	Smoke	Music Play	Press Music Play once. Listen for music.	Audible music; opposite source is not interrupted before music starts.	Continuous trace and automatic installed/profile identity; phone video for touch/audio.
S2	Smoke	Radio Play	Press Radio Play once. Listen for radio.	Audible radio with one tap; no repeated hidden service starts.	Continuous trace and automatic installed/profile identity; phone video for touch/audio.
S3	Smoke	Music app icon	Tap the Music app icon once, including while Maps is starting.	Music app opens within the bounded handoff, even if compact Maps fails.	Continuous trace and automatic installed/profile identity; phone video for touch/audio.
S4	Smoke	Radio app icon	Return HOME and tap the Radio app icon once.	Radio app opens once; no stale music app opens later.	Continuous trace and automatic installed/profile identity; phone video for touch/audio.
S5	Smoke	Normal navigation	Settings > Diagnostics & system > Testing methods > Open navigation normally.	Maps opens and its controls respond, independent of compact-mode failure.	Continuous trace and automatic installed/profile identity; phone video for touch/audio.
S6	Smoke	Compact map	Return HOME. Wait at most 45 seconds; pan map and tap My Position.	Visible updating map; correctly aligned touch; HOME controls remain accessible.	Continuous trace and automatic installed/profile identity; phone video for touch/audio.
S7	Smoke	Navigation rail	Tap the bottom Navigation rail button once.	Fullscreen request remains fullscreen; visible map accepts touch.	Continuous trace and automatic installed/profile identity; phone video for touch/audio.
S8	Smoke	HOME return	Press HOME once; wait at most 45 seconds; pan compact map.	Compact map returns without Retry and without duplicated launch.	Continuous trace and automatic installed/profile identity; phone video for touch/audio.
S9	Smoke	Retry acknowledgement	If a natural failure exists, tap Retry twice quickly and wait. Otherwise select n; this conditional case is not required on a healthy run.	Visible queued/recovery result; no silent tap loss, duplicate cold launch or unbounded loop.	Continuous trace and automatic installed/profile identity; phone video for touch/audio.
C1	Screening	Selected method trial	Select one method through the app UI. Note the recorded starting state. For N0 use normal open; N1 use HOME compact; N2 open normally until map is ready then HOME. Pan and open menu. For media press one Play. Repeat this case twice from comparable recorded states, using r; use a third trial if results differ.	Requested method completes visibly/audibly; no hidden fallback. A blocked method is N/Blocked, not a fail of a different method. Warm and cold trials are separate.	Automatic profile, installed hashes, initial tasks/processes, correlated navigation/media events, window/input samples.
TS18_EMBEDDED_PAYLOAD_3_END
    cat > "$stage/pr-heads.json" <<'TS18_EMBEDDED_PAYLOAD_4_END'
[
  {
    "repo": "organicmaps",
    "number": 57,
    "title": "Avoid known-missing default arrow texture probe",
    "sha": "969a736b1d13644ae9cf3b136a1bebbc28ee821c",
    "branch": "fix/skip-missing-default-arrow-texture",
    "base": "sync/upstream-curated-zero-behind"
  },
  {
    "repo": "organicmaps",
    "number": 56,
    "title": "Curated upstream sync: zero-behind history with selected InCar fixes",
    "sha": "61d48015b0140dbd46abd1cddda24f1635642b04",
    "branch": "sync/upstream-curated-zero-behind",
    "base": "master"
  },
  {
    "repo": "organicmaps",
    "number": 55,
    "title": "InCar: complete automotive guidance HUD and fresh current-road speed limits",
    "sha": "db57091f5daa0cb53b481b4205fad6db0ccb1c6f",
    "branch": "ui/incar-landscape-navigation-ribbon",
    "base": "master"
  },
  {
    "repo": "ts-theme",
    "number": 14,
    "title": "Harden TS18 cold start and navigation task/fullscreen lifecycle",
    "sha": "15775b5d6369ebe92ab4e475e6419aeebd4a2634",
    "branch": "fix/final-startup-runtime-hardening",
    "base": "fix/simple-media-controller"
  },
  {
    "repo": "ts-theme",
    "number": 13,
    "title": "Simplify media controls and restore real now-playing metadata",
    "sha": "a214d4573a85f7b34966e5ae9180f993c04be353",
    "branch": "fix/simple-media-controller",
    "base": "feat/topway-native-navigation-window"
  },
  {
    "repo": "ts-theme",
    "number": 11,
    "title": "Implement deterministic TS18 HOME native navigation window",
    "sha": "540f3a8cd1610ad6bd341752a6f2a523bd3f792e",
    "branch": "feat/topway-native-navigation-window",
    "base": "codex/launcher-automotive-ui"
  },
  {
    "repo": "ts-theme",
    "number": 10,
    "title": "Refine standalone launcher automotive UI",
    "sha": "ba6e5358cd48e51e41c388d7a25897459a36eeb2",
    "branch": "codex/launcher-automotive-ui",
    "base": "main"
  },
  {
    "repo": "ts-theme",
    "number": 15,
    "sha": "f9f9d473902f854536237da622f216ee498936ab"
  },
  {
    "repo": "ts-theme",
    "number": 16,
    "sha": "ae2dd07f1aed9411838f7a3d3387ed8dec121d0b"
  },
  {
    "repo": "organicmaps",
    "number": 58,
    "sha": "c454a66e040f92a96ebaf4f4d20c8038bfc984ce"
  }
]
TS18_EMBEDDED_PAYLOAD_4_END
    cat > "$stage/candidates.tsv" <<'TS18_EMBEDDED_PAYLOAD_5_END'
# package	allowed_sha256_comma_separated	source_in_same_order
app.organicmaps.incar	debc44fd7ef096e8796be2911cb9580b732800a77c27afe92de6d6241532994a,205586cbecad377ad605eacd8ec04eed3d4b6ac1b1157aea562e4064da2fcf13	db57091f5daa0cb53b481b4205fad6db0ccb1c6f,c454a66e040f92a96ebaf4f4d20c8038bfc984ce
com.cbkii.ts18launcher	FROM_SIGNED_HANDOFF	fix/testing-integration-recovery
TS18_EMBEDDED_PAYLOAD_5_END
    cat > "$stage/ts18-startup-1.3.sh" <<'TS18_EMBEDDED_PAYLOAD_6_END'
#!/system/bin/sh
# TS18 startup 1.3.0: bounded, read-only forensic/performance capture.
# Consolidates startup 1.2 readiness/sealing and deepdiag v3 provenance.
PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin:/odm/bin
export PATH
unset LD_PRELOAD LD_LIBRARY_PATH ANDROID_LOG_TAGS ANDROID_PRINTF_LOG
umask 077
BB=/data/adb/magisk/busybox
[ -x "$BB" ] || { echo 'BLOCKED: Magisk BusyBox required'; exit 1; }
SELF=$("$BB" readlink -f "$0") || exit 1
BASE=${SELF%/*}
# shellcheck source=capture-lib.sh
. "$BASE/capture-lib.sh" || exit 1
STATE=$BASE/../engine
EXPORT=/storage/emulated/0/Download/TS18-PR-Validation/individual
NATIVE=/system/bin
PACKAGES='com.cbkii.ts18launcher app.organicmaps.incar com.tw.media com.navimods.radio com.dofun.variety com.tw.radio com.tw.music com.tw.core com.google.android.gms com.google.android.gsf'

case "${1:-}" in
 --produce) shift; produce "$@"; exit ;;
 --watchdog)
    w_pid=$2; w_ticks=$3; w_end=$4; w_out=$5
    while :; do
        if ! same_process "$w_pid" "$w_ticks"; then
            for owner in "$w_out"/commands/*/owner; do
                [ -r "$owner" ] || continue
                read -r owned_pid owned_ticks < "$owner"
                same_process "$owned_pid" "$owned_ticks" && "$BB" kill -KILL "-$owned_pid" 2>/dev/null
            done
            for reader_owner in "$w_out"/commands/*/reader-owner; do
                [ -r "$reader_owner" ] || continue
                read -r reader_pid reader_ticks < "$reader_owner"
                same_process "$reader_pid" "$reader_ticks" && "$BB" kill -KILL "$reader_pid" 2>/dev/null
            done
            exit 0
        fi
        if [ "$(uptime_s)" -ge "$w_end" ]; then
            for owner in "$w_out"/commands/*/owner; do
                [ -r "$owner" ] || continue
                read -r owned_pid owned_ticks < "$owner"
                same_process "$owned_pid" "$owned_ticks" && "$BB" kill -KILL "-$owned_pid" 2>/dev/null
            done
            for reader_owner in "$w_out"/commands/*/reader-owner; do
                [ -r "$reader_owner" ] || continue
                read -r reader_pid reader_ticks < "$reader_owner"
                same_process "$reader_pid" "$reader_ticks" && "$BB" kill -KILL "$reader_pid" 2>/dev/null
            done
            "$BB" kill -KILL "-$w_pid" 2>/dev/null
            "$BB" sleep 1
            "$BB" rm -f "$w_out/SEALED.txt"
            printf 'FORCED: overall deadline; evidence partial, no valid seal\n' > "$w_out/FORCED_STOP.txt"
            exit 1
        fi
        "$BB" sleep 1
    done ;;
 --sample)
    [ -n "${2:-}" ] && { echo 'OBSERVER worker proc stat (ticks, not percent)'; "$BB" cat "/proc/$2/stat"; }
    printf 'uptime=%s\n' "$(uptime_s)"
    "$BB" cat /proc/meminfo /proc/swaps /proc/stat /proc/sys/vm/swappiness
    for f in /proc/pressure/* /sys/block/zram*/disksize /sys/block/zram*/mm_stat /sys/block/zram*/stat; do
        [ -r "$f" ] && { printf '\nFILE %s\n' "$f"; "$BB" cat "$f"; }
    done
    for p in /proc/[0-9]*; do
        [ -r "$p/cmdline" ] || continue
        name=$("$BB" tr '\000' '\n' < "$p/cmdline" 2>/dev/null | "$BB" head -n 1)
        case "$name" in
          com.google.android.gms*|com.google.android.gsf*|com.cbkii.ts18launcher*|app.organicmaps.incar*|com.tw.media*|com.navimods.radio*|com.dofun.variety*|com.tw.radio*|com.tw.music*|com.tw.core*|*ts18-startup-1.3*)
            printf '\nPROCESS %s %s\n' "${p##*/}" "$name"
            "$BB" cat "$p/stat" "$p/status" "$p/io" 2>/dev/null
            ;;
        esac
    done
    exit 0 ;;
 --runtime)
    for p in /proc/[0-9]*; do
        [ -r "$p/cmdline" ] || continue
        name=$("$BB" tr '\000' '\n' < "$p/cmdline" 2>/dev/null | "$BB" head -n 1)
        case "$name" in com.google.android.gms*|com.google.android.gsf*)
            echo "PROCESS ${p##*/} $name"
            "$BB" grep -E '\.(oat|vdex|odex|art|apk)|zygisk|lspd|vector' "$p/maps"
            ;; esac
    done
    exit 0 ;;
 --inventory)
    for f in "$SELF" "$BASE/capture-lib.sh" /data/adb/service.d/* /data/adb/post-fs-data.d/* /data/adb/modules/*/module.prop /data/adb/modules/*/service.sh /data/adb/modules/*/post-fs-data.sh /data/adb/modules/*/*.conf /data/adb/modules/*/*.json; do
        [ -f "$f" ] || continue
        "$BB" stat -c '%u:%g %a %s %n' "$f"
        "$NATIVE/ls" -lZ "$f"
        "$BB" sha256sum "$f"
        case "$f" in
          */module.prop) "$BB" grep -E '^(id|version|versionCode)=[A-Za-z0-9._ ()/-]+$' "$f" ;;
          *.sh) "$BB" awk '/appops|remount|legacy_storage|swappiness|deviceidle|compiler-filter/ {printf "suspect_line=%d contents=REDACTED\n",NR}' "$f" ;;
        esac
    done
    for m in /data/adb/modules/*; do
        [ -d "$m" ] || continue
        printf 'MODULE %s' "${m##*/}"
        for flag in disable remove update; do [ -e "$m/$flag" ] && printf ' %s' "$flag"; done
        printf '\n'
    done
    exit 0 ;;
 --apk)
    pkg=$2
    paths=$("$NATIVE/pm" path "$pkg") || exit $?
    printf '%s\n' "$paths"
    printf '%s\n' "$paths" | "$BB" grep -q '^package:/' || { echo 'apk_paths=UNKNOWN'; exit 1; }
    printf '%s\n' "$paths" | while IFS= read -r line; do
        case "$line" in package:/*) apk=${line#package:} ;; *) continue ;; esac
        "$BB" sha256sum "$apk" || exit 1
        "$BB" ls -l "${apk%/*}"/oat/*/* 2>/dev/null || true
        echo 'embedded_revision=UNKNOWN unless reported by a known metadata asset below'
        for entry in assets/BUILD_INFO.txt assets/build-info.properties; do
            "$BB" unzip -p "$apk" "$entry" 2>/dev/null || true
        done
        if [ -x /system/bin/apksigner ]; then
            /system/bin/apksigner verify --print-certs "$apk" || exit 1
        else
            printf 'signer=UNKNOWN (no native apksigner); inspect exact hashed APK offline\n'
        fi
    done
    exit $? ;;
 --help|-h)
    echo 'Usage: ts18-startup-1.3.sh --start [forensic|performance|mapwindow] [30..300] | --status | --stop | --mark LABEL'
    echo 'Labels: IDLE, AUXIO_PLAY, AUXIO_PAUSE, NAVRADIO_PLAY, NAVRADIO_PAUSE.'
    echo 'Default boot entry: forensic 180. Capture budget 600 seconds, hard deadline 700 seconds; no automatic device mutation.'
    exit 0 ;;
esac

uid=$("$BB" id -u)
if [ "$uid" != 0 ]; then
    STATE=${TMPDIR:-/data/local/tmp}/ts18-startup-logs-1.3-uid$uid
fi
case "$STATE" in /*) ;; *) echo 'BLOCKED: private state path unavailable'; exit 1 ;; esac
LOCK=$STATE/active
case "${1:---start}" in
 --status)
    if [ -r "$LOCK/owner" ]; then
        read -r pid identity < "$LOCK/owner"
        if same_process "$pid" "$identity"; then echo "RUNNING pid=$pid"; else echo 'WARN stale lock; preserve evidence and inspect before manual recovery'; fi
    elif [ -d "$LOCK" ]; then echo 'STARTING or stale lock; inspect before recovery'
    else echo INACTIVE; fi
    if [ -r "$STATE/latest" ]; then read -r latest < "$STATE/latest"; echo "latest=$latest"; "$BB" cat "$latest/COMPLETE.txt" "$latest/SEALED.txt" 2>/dev/null; fi
    exit 0 ;;
 --stop)
    [ -d "$LOCK" ] || { echo INACTIVE; exit 0; }
    if [ ! -r "$LOCK/owner" ]; then
        : > "$LOCK/STOP"; echo 'Stop requested during startup; worker will observe it.'; exit 0
    fi
    read -r pid identity < "$LOCK/owner"
    same_process "$pid" "$identity" || { echo 'WARN stale lock'; exit 1; }
    : > "$LOCK/STOP"; echo 'Stop requested; bounded producers will finish and seal.'; exit 0 ;;
 --mark)
    [ $# = 2 ] || exit 64
    case "$2" in IDLE|AUXIO_PLAY|AUXIO_PAUSE|NAVRADIO_PLAY|NAVRADIO_PAUSE) ;;
      *) echo 'Use one of: IDLE AUXIO_PLAY AUXIO_PAUSE NAVRADIO_PLAY NAVRADIO_PAUSE'; exit 64 ;; esac
    "$BB" mkdir "$LOCK/control" 2>/dev/null || { echo 'BLOCKED: inactive/busy'; exit 1; }
    if [ -f "$LOCK/FINISHING" ] || [ ! -r "$LOCK/output" ]; then "$BB" rmdir "$LOCK/control"; exit 1; fi
    read -r OUT < "$LOCK/output"
    atomic "$OUT/marker-$(uptime_s)-$$.txt" "$(uptime_s) $2"
    mark_rc=$?; "$BB" rmdir "$LOCK/control"; exit "$mark_rc" ;;
 --start) MODE=${2:-forensic}; DURATION=${3:-180} ;;
 --worker) MODE=$2; DURATION=$3 ;;
 *) echo 'Unknown command; --help'; exit 64 ;;
esac
case "$MODE" in forensic|performance|mapwindow) ;; *) exit 64 ;; esac
case "$DURATION" in ''|*[!0-9]*) exit 64 ;; esac
[ "$DURATION" -ge 30 ] && [ "$DURATION" -le 300 ] || exit 64

if [ "${1:---start}" != --worker ]; then
    for legacy in /data/adb/ts18-startup-logs/active/worker /data/adb/ts18-deepdiag-v3/worker.lock/pid; do
        if [ -r "$legacy" ]; then
            read -r oldpid oldticks < "$legacy"
            if [ -n "$oldticks" ]; then
                same_process "$oldpid" "$oldticks" && { echo "BLOCKED: older collector is running ($legacy)"; exit 1; }
            elif "$BB" kill -0 "$oldpid" 2>/dev/null; then
                echo "BLOCKED: older collector may be running without start identity ($legacy)"; exit 1
            fi
        fi
    done
    "$BB" mkdir -p "$STATE/runs" || exit 1
    "$BB" chmod 700 "$STATE" "$STATE/runs" || exit 1
    "$BB" mkdir "$LOCK" 2>/dev/null || { echo 'BLOCKED: active/stale lock; use --status'; exit 1; }
    "$BB" setsid "$BB" sh "$SELF" --worker "$MODE" "$DURATION" </dev/null > "$STATE/launcher.txt" 2>&1 &
    echo 'Dispatched; --status and COMPLETE.txt report producer state independently of outer su exit.'
    exit 0
fi

START=$(uptime_s); END=$((START + 600))
RUN=$("$BB" date -u +%Y%m%dT%H%M%SZ)-p$$
OUT=$STATE/runs/$RUN
"$BB" mkdir -p "$OUT/commands" || exit 1
worker_ids=$("$BB" sed 's/.*) //' /proc/$$/stat | "$BB" awk "{print \$3, \$4}")
if [ "$worker_ids" != "$$ $$" ]; then
    echo 'BLOCKED: isolated worker identity unavailable; no capture started' > "$OUT/BLOCKED.txt"
    atomic "$STATE/latest" "$OUT"
    exit 1
fi
worker_ticks=$(ticks "$$")
[ -n "$worker_ticks" ] || { echo 'BLOCKED: worker start identity unavailable' > "$OUT/BLOCKED.txt"; exit 1; }
atomic "$LOCK/owner" "$$ $worker_ticks" || exit 1
atomic "$LOCK/output" "$OUT" || exit 1
atomic "$STATE/latest" "$OUT" || exit 1
"$BB" setsid "$BB" sh "$SELF" --watchdog "$$" "$worker_ticks" "$((START + 700))" "$OUT" </dev/null >/dev/null 2>&1 &
trap ': > "$LOCK/STOP"' INT TERM HUP
{
    echo 'collector=1.3.0'; echo "start_epoch=$($BB date +%s)"; echo "mode=$MODE"; echo "start_uptime=$START"; echo "PATH=$PATH"
    "$BB" id; "$BB" cat /proc/self/attr/current /proc/sys/kernel/random/boot_id
    "$BB" readlink /proc/self/ns/mnt /proc/1/ns/mnt
    echo 'mount visibility=observed namespace only; UID0 is not platform/ADB authority'
    [ "$uid" = 0 ] || echo 'WARN root admission mismatch; root-only persistent inventory BLOCKED; ordinary-context read-only capture retained'
} > "$OUT/CONTEXT.txt" 2>&1
cap() {
    name=$1; shift
    if [ -f "$LOCK/STOP" ]; then event "BLOCKED $name stop-requested"; return 1; fi
    capture "$name" 6 524288 "$@"
}
cap collector-identity "$BB" stat -c '%u:%g %a %n' "$SELF" "$BASE/capture-lib.sh"
cap collector-selinux "$NATIVE/ls" -lZ "$SELF" "$BASE/capture-lib.sh"
cap collector-hashes "$BB" sha256sum "$SELF" "$BASE/capture-lib.sh"
cap fingerprint "$NATIVE/getprop" ro.build.fingerprint
cap history "$NATIVE/logcat" -d -b all -t 2000 -v epoch
cap packages-early "$NATIVE/pm" list packages -U
for pkg in $PACKAGES; do cap "appops-before-$pkg" "$NATIVE/cmd" appops get "$pkg"; done
cap testing-profile "$BB" sh "$BASE/checkpoint.sh" --launcher-prefs
cap tasks-before "$NATIVE/dumpsys" activity activities
cap recents-before "$NATIVE/dumpsys" activity recents
cap media-before "$NATIVE/dumpsys" media_session
for pkg in com.cbkii.ts18launcher app.organicmaps.incar com.tw.media com.navimods.radio; do
    cap "identity-before-$pkg" "$BB" sh "$SELF" --apk "$pkg"
done
event "PHASE measurement-start mode=$MODE seconds=$DURATION"
MEASURE_END=$(( $(uptime_s) + DURATION ))
if [ ! -f "$LOCK/STOP" ]; then
    (capture live-log "$DURATION" 16777216 "$NATIVE/logcat" -b all -v epoch -T 1; [ "$CAPTURE_UNSAFE" = 0 ] || echo live-log-cleanup-unproven > "$OUT/UNSEALED.txt") & LOG_JOB=$!
else
    LOG_JOB=
fi
index=0; next_audio=0; next_window=0
while [ "$(uptime_s)" -lt "$MEASURE_END" ] && [ ! -f "$LOCK/STOP" ]; do
    cap "sample-$index" "$BB" sh "$SELF" --sample "$$"
    if [ "$MODE" = mapwindow ] && [ "$(uptime_s)" -ge "$next_window" ]; then
        event "WINDOW_SAMPLE_BEGIN index=$index; sequential observations, not an atomic snapshot"
        capture "window-$index-screen" 3 8388608 "$NATIVE/screencap" -p
        capture "window-$index-activities" 2 4194304 "$NATIVE/dumpsys" activity activities
        capture "window-$index-wms" 2 4194304 "$NATIVE/dumpsys" window windows
        capture "window-$index-input" 2 4194304 "$NATIVE/dumpsys" input
        capture "window-$index-layers" 2 1048576 "$NATIVE/dumpsys" SurfaceFlinger --list
        event "WINDOW_SAMPLE_END index=$index"
        next_window=$(( $(uptime_s) + 10 ))
    fi
    if [ "$MODE" = forensic ] && [ "$(uptime_s)" -ge "$next_audio" ]; then
        event 'INFO forensic observer-heavy audio snapshots; unsuitable for latency comparison'
        for service in media_session media.audio_flinger media.audio_policy; do
            cap "phase-$index-$service" "$NATIVE/dumpsys" "$service"
        done
        next_audio=$(( $(uptime_s) + 20 ))
    fi
    index=$((index + 1))
    "$BB" sleep 5
done
[ -z "$LOG_JOB" ] || wait "$LOG_JOB" || true
[ ! -f "$OUT/UNSEALED.txt" ] || CAPTURE_UNSAFE=1
event 'PHASE measurement-end; subsequent heavy diagnostics excluded from latency/CPU comparisons'


if [ ! -f "$LOCK/STOP" ]; then
    for attempt in 1 2 3 4 5; do
        if cap "packages-ready-$attempt" "$NATIVE/pm" list packages -U; then
            if "$BB" grep -q '^package:.* uid:[0-9]' "$OUT/commands/packages-ready-$attempt/output.txt"; then
                atomic "$OUT/UID_MAP_SOURCE.txt" "commands/packages-ready-$attempt"; break
            fi
        fi
        "$BB" sleep 2
    done
    [ -f "$OUT/UID_MAP_SOURCE.txt" ] || event 'UNKNOWN package-UID map; failed/empty queries never establish absence'
    for pkg in $PACKAGES; do
        [ -f "$LOCK/STOP" ] && break
        cap "package-$pkg" "$NATIVE/dumpsys" package "$pkg"
        capture "apk-$pkg" 10 524288 "$BB" sh "$SELF" --apk "$pkg"
        cap "appops-$pkg" "$NATIVE/cmd" appops get "$pkg"
        cap "pss-$pkg" "$NATIVE/dumpsys" meminfo "$pkg"
    done
    if [ "$uid" = 0 ]; then cap persistent-inventory "$BB" sh "$SELF" --inventory; else event 'BLOCKED persistent-inventory requires root'; fi
    cap framework-hashes "$BB" sha256sum /system/framework/framework.jar /system/framework/services.jar
    for service in media_session audio media.audio_flinger media.audio_policy activity window storage mount; do
        cap "service-$service" "$NATIVE/dumpsys" "$service"
    done
    if [ "$MODE" = mapwindow ]; then
        capture sf-final 8 8388608 "$NATIVE/dumpsys" SurfaceFlinger
        cap input-final "$NATIVE/dumpsys" input
        cap window-final "$NATIVE/dumpsys" window
        cap display-final "$NATIVE/dumpsys" display
        capture map-file-logs 10 14680064 "$BB" sh "$BASE/checkpoint.sh" --app-files
        cap nav-private-state "$BB" sh "$BASE/checkpoint.sh" --nav-state
    fi
    cap recents "$NATIVE/dumpsys" activity recents
    cap stacks "$NATIVE/dumpsys" activity stacks
    cap kernel "$NATIVE/dmesg"
    cap gms-runtime-maps "$BB" sh "$SELF" --runtime
    cap final-memory "$BB" sh "$SELF" --sample "$$"
    if [ "$MODE" = forensic ]; then
        cap dexopt "$NATIVE/dumpsys" package dexopt
        cap thermal "$NATIVE/dumpsys" thermalservice
        cap cpuinfo "$NATIVE/dumpsys" cpuinfo
    fi
else
    event 'INFO stop requested; post-measurement heavy diagnostics skipped'
fi

: > "$LOCK/FINISHING"
tries=0
until "$BB" mkdir "$LOCK/control" 2>/dev/null; do
    tries=$((tries + 1)); [ "$tries" -lt 5 ] || { echo UNSEALED > "$OUT/UNSEALED.txt"; exit 1; }
    "$BB" sleep 1
done
event "PHASE finish elapsed_s=$(( $(uptime_s) - START )) observer=sample-durations-and-collector-PID-in-proc-stat"
seal || { echo 'FAIL: seal or producer cleanup unproven' > "$OUT/UNSEALED.txt"; exit 1; }
archive=$STATE/$RUN.tar.gz
if "$BB" timeout -s KILL 20 "$BB" tar -czf "$archive" -C "$STATE/runs" "$RUN" && "$BB" timeout -s KILL 15 "$BB" gzip -t "$archive"; then
    "$BB" sha256sum "$archive" > "$archive.sha256"
    if [ -d /storage/emulated/0/Download ] && "$BB" timeout -s KILL 3 "$BB" mkdir -p "$EXPORT"; then
        if "$BB" timeout -s KILL 15 "$BB" cp "$archive" "$EXPORT/$RUN.tar.gz.partial" &&
          [ "$("$BB" sha256sum "$archive" | "$BB" cut -d ' ' -f 1)" = "$("$BB" timeout -s KILL 10 "$BB" sha256sum "$EXPORT/$RUN.tar.gz.partial" | "$BB" cut -d ' ' -f 1)" ] &&
          "$BB" mv "$EXPORT/$RUN.tar.gz.partial" "$EXPORT/$RUN.tar.gz" &&
          (cd "$EXPORT" && "$BB" sha256sum "$RUN.tar.gz") > "$EXPORT/$RUN.tar.gz.sha256"; then
            atomic "$STATE/export-status" "PASS $EXPORT/$RUN.tar.gz"
        else
            "$BB" rm -f "$EXPORT/$RUN.tar.gz.partial"
            atomic "$STATE/export-status" "WARN export finalisation failed; retained $archive"
        fi
    else atomic "$STATE/export-status" "WARN Downloads unavailable; retained $archive"; fi
else atomic "$STATE/export-status" "WARN archive/CRC failed; retained $OUT"; fi
"$BB" rm -f "$LOCK/owner" "$LOCK/output" "$LOCK/STOP" "$LOCK/FINISHING"
"$BB" rmdir "$LOCK/control" "$LOCK"
TS18_EMBEDDED_PAYLOAD_6_END
    cat > "$stage/SHA256SUMS" <<'TS18_EMBEDDED_PAYLOAD_7_END'
89d6f722aee167156574409fbd08c79a7581505d445abaef41d96179426bfd0f  capture-lib.sh
cadfe044442e4c42fe15b1cc0152e8ddaaf5b4ebe34def5a8a27c62ee7ffa728  checkpoint.sh
41250da9f78973f894d0adad8987beb3653f203f0869cd3cd41aea23a553f0d3  guide.sh
57f467701a6923ea72fffc8ceca36dea1a5845a0982e77bbc9f4828dd5ad5f49  cases.tsv
13ba176983ebab71b2f7de3fd4caaccb857e76eef915ea3e592f97452a44fbe1  pr-heads.json
f27eb0ba7c2b5f22e139627a9c4a03ad405c32ae5b983f5de369ece7a52cad79  candidates.tsv
49e7cffda51dbf04adc863f69435bedf670111d4ebb3c48cfcc8cabe7cfd5f77  ts18-startup-1.3.sh
TS18_EMBEDDED_PAYLOAD_7_END

    (cd "$stage" && "$BB" sha256sum -c SHA256SUMS >/dev/null) || die 'embedded payload verification failed'
    "$BB" mv "$stage" "$BASE" || exit 1
  fi
  (cd "$BASE" && "$BB" sha256sum -c SHA256SUMS >/dev/null) || die 'private toolkit changed; preserve evidence and inspect it'
  "$BB" mkdir -p "$ROOT/checkpoints" "$ROOT/engine" || exit 1
}
install_payload
ENGINE=$BASE/ts18-startup-1.3.sh
label=${2:-}
# Serialize admission and result/export writes; readers/stop/markers stay usable.
OPLOCK=/data/data/com.termux/files/home/.ts18-pr-validation-operation-active
owns_oplock=0
owns_export=0
finish() {
  rc=$?
  if [ "$owns_export" = 1 ]; then "$BB" rmdir "$ROOT/export-active" 2>/dev/null || :; fi
  if [ "$owns_oplock" = 1 ]; then
    "$BB" rm -f "$OPLOCK/pid"; "$BB" rmdir "$OPLOCK" 2>/dev/null || :
  fi
  if [ "$rc" = 0 ]; then
    echo 'SUCCESS: requested operation completed/dispatched; inspect worker state, probe warnings and physical results separately.'
  else
    echo 'FAILED: requested operation did not complete; private/partial evidence retained.' >&2
  fi
}
trap finish EXIT
case "$1" in status|mark|stop|guide|cases) ;; *)
  "$BB" mkdir "$OPLOCK" 2>/dev/null || die 'another validation operation is active/stale; preserve evidence and inspect before recovery'
  owns_oplock=1
  printf '%s\n' "$$" > "$OPLOCK/pid"
  ;;
esac
check_other_collectors() {
  for lock in /data/adb/ts18-startup-logs/active /data/adb/ts18-deepdiag-v3/worker.lock /data/adb/ts18-startup-logs-1.3/active; do
    [ ! -d "$lock" ] || die "existing diagnostic owner/lock: $lock; inspect and stop through its normal interface first"
  done
  # Never silently overlap another revision of this validation campaign.
  for dir in /data/data/com.termux/files/home/.ts18-pr-validation-*/engine/active /data/data/com.termux/files/home/.ts18-pr-validation-*/checkpoint-active; do
    [ ! -d "$dir" ] || die "collector active or stale: $dir; use status, preserve any partial evidence"
  done
}
case "$1" in
 preflight|checkpoint)
    [ ! -d "$ROOT/export-active" ] || die 'export in progress'
    if [ "$1" = preflight ]; then label=Q0-baseline; delay=0; else delay=${3:-8}; fi
    valid_label "$label" || die 'invalid checkpoint label'
    case "$delay" in ''|*[!0-9]*) die 'delay must be 0..30 seconds' ;; esac
    [ "$delay" -le 30 ] || die 'delay must be 0..30 seconds'
    check_other_collectors
    "$BB" mkdir "$ROOT/checkpoint-active" || die 'checkpoint already active'
    "$BB" setsid "$BB" sh "$BASE/checkpoint.sh" "$label" "$delay" </dev/null > "$ROOT/checkpoint-worker.txt" 2>&1 &
    printf 'Checkpoint queued: %s; return to the target UI within %s seconds.\nUse status to verify completion.\n' "$label" "$delay"
    ;;
 start)
    [ ! -d "$ROOT/export-active" ] || die 'export in progress'
    check_other_collectors
    "$BB" sh "$ENGINE" --start "${2:-forensic}" "${3:-180}"
    ;;
 mark) "$BB" sh "$ENGINE" --mark "$label" ;;
 stop) "$BB" sh "$ENGINE" --stop ;;
 status)
    printf 'Campaign: %s\n' "$ROOT"
    "$BB" sh "$ENGINE" --status
    if [ -d "$ROOT/checkpoint-active" ]; then
      echo 'CHECKPOINT active or stale (do not remove the lock blindly)'
      "$BB" cat "$ROOT/checkpoint-active/owner" "$ROOT/checkpoint-active/output" 2>/dev/null
    fi
    if [ -f "$ROOT/latest-checkpoint" ]; then
      read -r latest < "$ROOT/latest-checkpoint"
      printf 'checkpoint=%s\n' "$latest"
      "$BB" cat "$latest/SEALED.txt" "$latest/PROVENANCE.tsv" "$latest/UNSEALED.txt" 2>/dev/null || true
    fi
    "$BB" cat "$ROOT/engine/export-status" 2>/dev/null || true
    ;;
 result)
    [ ! -d "$ROOT/export-active" ] || die 'export in progress'
    [ "$#" -ge 3 ] && [ "$#" -le 5 ] || die 'result CASE P|F|N [observation]'
    valid_label "$label" || die 'invalid case ID'
    case "$3" in P|PASS) verdict=PASS ;; F|FAIL) verdict=FAIL ;; N|NOT_RUN) verdict=NOT_RUN ;;
      WARN|UNKNOWN|BLOCKED) verdict=$3 ;; *) die 'invalid physical result' ;; esac
    observation=${4:-}
    [ "${#observation}" -le 2000 ] || die 'observation exceeds 2000 characters'
    "$BB" mkdir "$ROOT/result-active" || die 'another result write is in progress'
    note=$(printf '%s' "${4:-}" | "$BB" tr '\t\r\n' '   ')
    checkpoint=NONE
    [ ! -r "$ROOT/latest-checkpoint" ] || read -r checkpoint < "$ROOT/latest-checkpoint"
    engine=NONE
    [ ! -r "$ROOT/engine/latest" ] || read -r engine < "$ROOT/engine/latest"
    case "$checkpoint" in *"-$label-"*) ;; *) checkpoint=NONE ;; esac
    case "${5:-}" in --none) checkpoint=NONE; engine=NONE ;;
      --checkpoint-only) engine=NONE ;; esac
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$("$BB" date -u +%Y-%m-%dT%H:%M:%SZ)" "$("$BB" cat /proc/sys/kernel/random/boot_id)" \
      "$label" "$verdict" "$note" "$checkpoint" "$engine" "$("$BB" cut -d ' ' -f 1 /proc/uptime)" >> "$ROOT/results.tsv"
    result_rc=$?
    "$BB" rmdir "$ROOT/result-active"
    [ "$result_rc" = 0 ] || exit "$result_rc"
    echo 'Recorded operator observation; evidence association is a hint, verify case/time/boot match.'
    ;;
 export)
    check_other_collectors
    [ ! -d "$ROOT/result-active" ] || die 'result write active'
    "$BB" mkdir "$ROOT/export-active" || die 'export active/stale'
    owns_export=1
    # Recheck after locking to detect producers started while admission was checked.
    check_other_collectors
    stamp=$("$BB" date -u +%Y%m%dT%H%M%SZ)-$$
    staged=$("$BB" mktemp -d "$ROOT/export-$stamp.XXXXXX") || exit 1
    size=$("$BB" du -sk "$ROOT/checkpoints" "$ROOT/engine/runs" 2>/dev/null | "$BB" awk '{s+=$1} END {print s+0}')
    [ "$size" -le 786432 ] || die 'campaign exceeds 768 MiB; preserve it for manual transfer'
    free=$("$BB" df -Pk "$ROOT" | "$BB" awk 'END {print $4}')
    [ "$free" -ge "$((size * 3 + 262144))" ] || die 'insufficient private storage for a verified export'
    "$BB" cp -R "$ROOT/checkpoints" "$staged/checkpoints" || exit 1
    if [ -d "$ROOT/engine/runs" ]; then "$BB" cp -R "$ROOT/engine/runs" "$staged/captures" || exit 1; fi
    "$BB" cp "$BASE/candidates.tsv" "$BASE/pr-heads.json" "$BASE/cases.tsv" "$BASE/SHA256SUMS" "$staged/" || exit 1
    "$BB" cp "$SELF" "$staged/ts18-validate.sh" || exit 1
    if [ -f "$ROOT/results.tsv" ]; then "$BB" cp "$ROOT/results.tsv" "$staged/results.tsv" || exit 1; fi
    printf 'physical_results=operator observations only\nAll cases in cases.tsv; missing cases=NOT_RUN\nUnsealed children=INCOMPLETE; never use as passing evidence\n' > "$staged/SUMMARY.txt"
    (cd "$staged" && "$BB" find . -type f ! -name EXPORT.sha256 | "$BB" sort | while IFS= read -r f; do "$BB" sha256sum "$f" || exit 1; done) > "$staged/EXPORT.sha256" || exit 1
    (cd "$staged" && "$BB" sha256sum -c EXPORT.sha256 >/dev/null) || die 'export manifest failed'
    archive=$ROOT/TS18-PR-Validation-$stamp.tar.gz
    "$BB" timeout -s KILL 120 "$BB" tar -czf "$archive" -C "$ROOT" "${staged##*/}" || die 'archive creation failed; private evidence retained'
    "$BB" timeout -s KILL 60 "$BB" gzip -t "$archive" || die 'archive CRC failed'
    "$BB" sha256sum "$archive" > "$archive.sha256" || exit 1
    "$BB" timeout -s KILL 5 "$BB" mkdir -p "$EXPORT" || die "shared storage unavailable; retained $archive"
    dest=$EXPORT/${archive##*/}
    "$BB" timeout -s KILL 120 "$BB" cp "$archive" "$dest.partial" || die "copy failed; retained $archive"
    expected=$("$BB" sha256sum "$archive" | "$BB" cut -d ' ' -f 1)
    actual=$("$BB" timeout -s KILL 60 "$BB" sha256sum "$dest.partial" | "$BB" cut -d ' ' -f 1)
    [ "$expected" = "$actual" ] || die 'export hash mismatch; private archive retained'
    "$BB" mv "$dest.partial" "$dest" || exit 1
    (cd "$EXPORT" && "$BB" sha256sum "${dest##*/}") > "$dest.sha256" || exit 1
    printf 'EXPORT VERIFIED: %s\nSHA256: %s\nThis confirms transfer integrity, not physical test success.\n' "$dest" "$expected"
    ;;
 cases)
    if [ -f "$ROOT/results.tsv" ]; then
      "$BB" awk -F '\t' 'FILENAME==ARGV[1] {s[$3]=$4; next} {printf "%-5s %-14s %-38s %s\n", $1,$2,$3,($1 in s ? s[$1] : "NOT_RUN")}' "$ROOT/results.tsv" "$BASE/cases.tsv"
    else
      "$BB" awk -F '\t' '{printf "%-5s %-14s %-38s NOT_RUN\n",$1,$2,$3}' "$BASE/cases.tsv"
    fi
    ;;
 guide)
    "$BB" sh "$BASE/guide.sh" "$SELF" "$ROOT" "$BB"
    ;;
 *) usage; exit 64 ;;
esac
