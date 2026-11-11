#!/data/data/com.termux/files/usr/bin/bash
# Read-only, narrowly scoped post-scenario capture; feature acceptance remains manual.
umask 077
label=${1:-smoke}
case "$label" in smoke|restart|reboot|coldboot|acc-wake) ;; *) printf 'Use smoke, restart, reboot, coldboot or acc-wake\n'; exit 2 ;; esac
for required in timeout mktemp tar date head wc sha256sum mkdir mv rm; do
    command -v "$required" >/dev/null 2>&1 || { printf 'BLOCKED required command: %s\n' "$required"; exit 2; }
done
work=$(mktemp -d "${TMPDIR:-/data/data/com.termux/files/usr/tmp}/ts18-qualified.XXXXXXXX") || exit 2
stamp=$(date -u +%Y%m%dT%H%M%SZ)
export_dir=/storage/emulated/0/Download/TS18/qualification
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
capture identity id
capture selinux /system/bin/cat /proc/self/attr/current
capture boot /system/bin/cat /proc/sys/kernel/random/boot_id
capture uptime /system/bin/cat /proc/uptime
capture home /system/bin/cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
capture launcher /system/bin/dumpsys package com.cbkii.ts18launcher
capture auxio /system/bin/dumpsys package com.tw.media
capture radio /system/bin/dumpsys package com.navimods.radio
capture media /system/bin/dumpsys media_session
capture timeline /system/bin/logcat -d -t 5000 -v threadtime TS18Startup:I TS18Perf:I TS18Nav:I TS18Media:I '*:S'
su_bin="${PREFIX:-/data/data/com.termux/files/usr}/bin/su"
if [ -x "$su_bin" ] && [ "$(timeout -k 1 5 "$su_bin" -c '/system/bin/id -u' 2>/dev/null)" = 0 ]; then
    capture root-context "$su_bin" -c 'export PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin; id; cat /proc/self/attr/current; readlink /proc/self/ns/mnt'
    capture tasks "$su_bin" -c 'export PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin; dumpsys activity activities'
    capture windows "$su_bin" -c 'export PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin; dumpsys window displays'
else
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
