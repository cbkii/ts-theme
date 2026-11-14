#!/data/data/com.termux/files/usr/bin/bash
# Read-only, narrowly scoped post-scenario capture; feature acceptance remains manual.
umask 077
label=${1:-smoke}
case "$label" in smoke|restart|reboot|coldboot|acc-wake) ;; *) printf 'Use smoke, restart, reboot, coldboot or acc-wake\n'; exit 2 ;; esac
for required in timeout mktemp tar date head wc sha256sum mkdir mv rm env grep sed sleep; do
    command -v "$required" >/dev/null 2>&1 || { printf 'BLOCKED required command: %s\n' "$required"; exit 2; }
done
work=$(mktemp -d "${TMPDIR:-/data/data/com.termux/files/usr/tmp}/ts18-qualified.XXXXXXXX") || exit 2
stamp=$(date -u +%Y%m%dT%H%M%SZ)
export_dir=/storage/emulated/0/Download/ts-theme/qualification
partial=
cleanup() { [ -z "$partial" ] || rm -f -- "$partial"; rm -rf -- "$work"; }
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
status="$work/STATUS.tsv"
printf 'section\tstatus\texit\n' > "$status"
max_capture_bytes=2097152
capture() {
    local name=$1 result bytes outcome
    shift
    # Stream cap bounds storage even if a command floods or ignores its deadline.
    timeout -k 1 8 "$@" 2>&1 | head -c "$((max_capture_bytes + 1))" > "$work/$name.txt"
    result=${PIPESTATUS[0]}
    bytes=$(wc -c < "$work/$name.txt")
    if [ "$bytes" -gt "$max_capture_bytes" ]; then
        head -c "$max_capture_bytes" "$work/$name.txt" > "$work/$name.trim"
        mv -- "$work/$name.trim" "$work/$name.txt"
        outcome=WARN_TRUNCATED
    elif [ "$result" -eq 0 ]; then outcome=PASS; else outcome=WARN; fi
    printf '%s\t%s\t%s\n' "$name" "$outcome" "$result" >> "$status"
}
# Platform tools run without the Termux preload/library path; the caller's environment is untouched.
android_path=/system/bin:/system/xbin:/vendor/bin:/product/bin
plain=(env -u LD_PRELOAD -u LD_LIBRARY_PATH "PATH=$android_path")
su_bin="${PREFIX:-/data/data/com.termux/files/usr}/bin/su"
root=0
if [ -x "$su_bin" ] && [ "$(timeout -k 1 5 "$su_bin" -c '/system/bin/id -u' 2>/dev/null)" = 0 ]; then root=1; fi
# Root captures use the completion-sentinel pattern already proven on this unit. The Magisk client
# can outlive its finished child, and a hung child can keep inherited output open, so output goes
# straight to a file (never a pipe this script waits on), the child is bounded in time and size on
# the root side, and completion is the sentinel root writes, not the client's lifetime. The client
# is then stopped whatever state it is in.
quote() { printf "'%s'" "${1//\'/\'\\\'\'}"; }
root_capture() {
    local name=$1 command=$2 command_file sentinel pid deadline result=124 bytes outcome
    command_file="$work/.command-$name"
    sentinel="$work/.status-$name"
    # The file-size limit (512-byte blocks) stops a flooding command just past the capture cap.
    printf 'PATH=%s\nexport PATH\nunset LD_PRELOAD LD_LIBRARY_PATH\nulimit -f %s\n%s\n' \
        "$android_path" "$((max_capture_bytes / 512 + 2))" "$command" > "$command_file"
    : > "$sentinel" # Created here so it stays owned by this user; root only overwrites it.
    # Both paths derive from TMPDIR, so they are shell-quoted before root parses them.
    "$su_bin" -c "/system/bin/toybox timeout -k 1 8 /system/bin/sh $(quote "$command_file") 2>&1; printf '%s\\n' \"\$?\" > $(quote "$sentinel")" \
        > "$work/$name.txt" 2>/dev/null &
    pid=$!
    deadline=$((SECONDS + 12))
    while [ ! -s "$sentinel" ] && kill -0 "$pid" 2>/dev/null && [ "$SECONDS" -lt "$deadline" ]; do sleep 0.1; done
    if [ -s "$sentinel" ]; then
        read -r result < "$sentinel"
        case "$result" in ''|*[!0-9]*) result=125 ;; esac
    fi
    kill -TERM "$pid" 2>/dev/null; kill -KILL "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
    rm -f -- "$command_file" "$sentinel"
    bytes=$(wc -c < "$work/$name.txt")
    if [ "$bytes" -gt "$max_capture_bytes" ]; then
        head -c "$max_capture_bytes" "$work/$name.txt" > "$work/$name.trim"
        mv -- "$work/$name.trim" "$work/$name.txt"
        outcome=WARN_TRUNCATED
    elif [ "$result" -eq 0 ]; then outcome=PASS; else outcome=WARN; fi
    printf '%s\t%s\t%s\n' "$name" "$outcome" "$result" >> "$status"
}
# Framework dumps and other applications' log lines need DUMP/READ_LOGS, which the Termux UID
# lacks. They are taken through root when it is available. Without root, a refused capture is
# BLOCKED (a collector limit), never a PASS and never evidence about the application.
framework() {
    local name=$1 command=$2
    if [ "$root" -eq 1 ]; then root_capture "$name" "$command"; return; fi
    capture "$name" "${plain[@]}" sh -c "$command"
    if grep -q 'Permission Denial' "$work/$name.txt" 2>/dev/null; then
        sed -i '$d' "$status"
        printf '%s\tBLOCKED\troot-unavailable\n' "$name" >> "$status"
    fi
}
capture identity id
capture selinux "${plain[@]}" cat /proc/self/attr/current
capture boot "${plain[@]}" cat /proc/sys/kernel/random/boot_id
capture uptime "${plain[@]}" cat /proc/uptime
capture home "${plain[@]}" cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
# Installed bytes, for comparison with the hand-off hashes: one "package sha256 path" line per
# installed APK file. A package that is absent or cannot be hashed fails the capture. Reads only.
# The loop is expanded by the capturing shell, not this one.
# shellcheck disable=SC2016
framework apk-identity 'rc=0
for p in com.cbkii.ts18launcher com.tw.media app.organicmaps.incar; do
  paths=$(pm path "$p" | sed "s/^package://")
  if [ -z "$paths" ]; then echo "$p NOT_INSTALLED"; rc=1; continue; fi
  for f in $paths; do
    if h=$(sha256sum "$f"); then echo "$p ${h%% *} $f"; else echo "$p UNREADABLE $f"; rc=1; fi
  done
done
exit $rc'
framework launcher 'dumpsys package com.cbkii.ts18launcher'
framework auxio 'dumpsys package com.tw.media'
framework maps 'dumpsys package app.organicmaps.incar'
framework audio 'dumpsys audio'
framework radio 'dumpsys package com.navimods.radio'
framework media 'dumpsys media_session'
framework listeners 'settings get secure enabled_notification_listeners'
if [ "$root" -eq 1 ]; then
    root_capture timeline "logcat -d -t 5000 -v threadtime TS18Startup:I TS18Perf:I TS18Nav:I TS18NavPerm:I TS18Media:I TS18Browser:I MediaBrowser:I MediaBrowserService:I AudioService:I AudioManager:I AndroidRuntime:E '*:S'"
    root_capture root-context 'id; cat /proc/self/attr/current; readlink /proc/self/ns/mnt'
    root_capture tasks 'dumpsys activity activities'
    root_capture windows 'dumpsys window displays'
    root_capture window-owners 'dumpsys window windows'
else
    # An unprivileged logcat shows only this shell's own lines, which would read as an empty timeline.
    printf 'timeline\tBLOCKED\troot-unavailable\n' >> "$status"
    printf 'root-only-sections\tBLOCKED\troot-unavailable\n' >> "$status"
fi
printf 'scenario=%s\nutc=%s\nfeature_results=NOT_RUN_by_collector\n' "$label" "$stamp" > "$work/CAMPAIGN.txt"
(cd "$work" && sha256sum ./*.txt ./*.tsv > SHA256SUMS.txt) || exit 2
mkdir -p "$export_dir" || { printf 'Could not export evidence; private state will be cleaned.\n'; exit 2; }
archive="$export_dir/$stamp-$label-$$.tar.gz"
partial="$archive.partial"
if tar -czf "$partial" -C "$work" . && tar -tzf "$partial" >/dev/null && mv -- "$partial" "$archive"; then
    partial=
    printf 'Evidence: %s\nReview STATUS.tsv; feature acceptance remains manual.\n' "$archive"
else
    printf 'Could not seal evidence.\n'; exit 2
fi
