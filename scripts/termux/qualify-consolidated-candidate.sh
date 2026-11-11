#!/data/data/com.termux/files/usr/bin/bash
# Read-only post-scenario capture. User actions remain manual; no package/task/media mutation.
umask 077
label=${1:-smoke}
case "$label" in smoke|restart|reboot|coldboot|acc-wake) ;; *) printf 'Use smoke, restart, reboot, coldboot or acc-wake\n'; exit 2 ;; esac
for required in timeout mktemp tar date; do
    command -v "$required" >/dev/null 2>&1 || { printf 'BLOCKED required command: %s\n' "$required"; exit 2; }
done
work=$(mktemp -d "${TMPDIR:-/data/data/com.termux/files/usr/tmp}/ts18-qualified.XXXXXXXX") || exit 2
stamp=$(date -u +%Y%m%dT%H%M%SZ)
export_dir=/storage/emulated/0/Download/TS18/qualification
cleanup() { rm -rf -- "$work"; }
trap cleanup EXIT
status="$work/STATUS.tsv"
printf 'section\tstatus\texit\n' > "$status"
capture() {
    name=$1
    shift
    timeout -k 1 8 "$@" > "$work/$name.txt" 2> "$work/$name.stderr"
    result=$?
    if [ "$result" -eq 0 ]; then outcome=PASS; else outcome=WARN; fi
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
if [ -x "$su_bin" ] && timeout -k 1 5 "$su_bin" -c 'test "$(id -u)" = 0' >/dev/null 2>&1; then
    capture root-context "$su_bin" -c 'id; cat /proc/self/attr/current; readlink /proc/self/ns/mnt'
    capture tasks "$su_bin" -c 'dumpsys activity activities'
    capture windows "$su_bin" -c 'dumpsys window displays'
else
    printf 'root-only-sections\tBLOCKED\troot-unavailable\n' >> "$status"
fi
printf 'scenario=%s\nutc=%s\nfeature_results=NOT_RUN_by_collector\n' "$label" "$stamp" > "$work/CAMPAIGN.txt"
mkdir -p "$export_dir" || { printf 'Could not export evidence; private state will be cleaned.\n'; exit 2; }
archive="$export_dir/$stamp-$label-$$.tar.gz"
if tar -czf "$archive" -C "$work" .; then
    printf 'Evidence: %s\nReview STATUS.tsv; feature acceptance remains manual.\n' "$archive"
else
    printf 'Could not seal evidence.\n'; exit 2
fi
