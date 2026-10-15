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
STATE=/data/adb/ts18-startup-logs-1.3
EXPORT=/storage/emulated/0/Download/TS18-startup-logs
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
    echo 'Usage: ts18-startup-1.3.sh --start [forensic|performance] [30..300] | --status | --stop | --mark LABEL'
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
case "$MODE" in forensic|performance) ;; *) exit 64 ;; esac
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
[ -n "$worker_ticks" ] || {
    echo 'BLOCKED: worker start identity unavailable' > "$OUT/BLOCKED.txt"
    atomic "$STATE/latest" "$OUT"
    exit 1
}
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
event "PHASE measurement-start mode=$MODE seconds=$DURATION"
MEASURE_END=$(( $(uptime_s) + DURATION ))
if [ ! -f "$LOCK/STOP" ]; then
    (capture live-log "$DURATION" 16777216 "$NATIVE/logcat" -b all -v epoch -T 1
     [ "${CAPTURE_UNSAFE:-0}" = 0 ] || exit 70
     exit 0) & LOG_JOB=$!
else
    LOG_JOB=
fi
index=0; next_audio=0
while [ "$(uptime_s)" -lt "$MEASURE_END" ] && [ ! -f "$LOCK/STOP" ]; do
    cap "sample-$index" "$BB" sh "$SELF" --sample "$$"
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
if [ -n "$LOG_JOB" ]; then wait "$LOG_JOB" || mark_unsafe; fi
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
        if [ -f "$LOCK/STOP" ]; then
            event "BLOCKED apk-$pkg stop-requested"
        else
            capture "apk-$pkg" 10 524288 "$BB" sh "$SELF" --apk "$pkg"
        fi
        cap "appops-$pkg" "$NATIVE/cmd" appops get "$pkg"
        cap "pss-$pkg" "$NATIVE/dumpsys" meminfo "$pkg"
    done
    if [ "$uid" = 0 ]; then cap persistent-inventory "$BB" sh "$SELF" --inventory; else event 'BLOCKED persistent-inventory requires root'; fi
    cap framework-hashes "$BB" sha256sum /system/framework/framework.jar /system/framework/services.jar
    for service in media_session audio media.audio_flinger media.audio_policy activity window storage mount; do
        cap "service-$service" "$NATIVE/dumpsys" "$service"
    done
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
