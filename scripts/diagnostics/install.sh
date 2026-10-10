#!/system/bin/sh
# Explicit installer; NOT invoked by the collector. No reboot or capture starts.
PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin
export PATH
unset LD_PRELOAD LD_LIBRARY_PATH
umask 077
BB=/data/adb/magisk/busybox
if [ ! -x "$BB" ] || [ "$("$BB" id -u 2>/dev/null)" != 0 ]; then
    echo 'BLOCKED: native Magisk root required'
    exit 1
fi
[ "${1:-}" = --install ] || { echo 'Usage: install.sh --install [EXACT_OLD_SERVICE_SCRIPT ...]'; exit 64; }
shift
SRC=$("$BB" dirname "$("$BB" readlink -f "$0")")
DEST=/data/adb/ts18-diagnostics-toolkit
SERVICE=/data/adb/service.d
ENTRY=$SERVICE/76-ts18-startup-1.3.sh
STAGE=/data/adb/ts18-diagnostics-toolkit.stage-$$
(cd "$SRC" && "$BB" sha256sum -c SHA256SUMS) || exit 1
for old in "$@"; do
    case "$old" in
      /data/adb/service.d/*ts18*startup*.sh|/data/adb/service.d/*ts18*deepdiag*.sh) ;;
      *) echo 'BLOCKED: unexpected prior collector path'; exit 1 ;;
    esac
    case "$old" in *..*|*' '*|*'
'*) exit 1 ;; esac
    [ "${old%/*}" = "$SERVICE" ] || exit 1
    [ -f "$old" ] && [ ! -L "$old" ] || exit 1
done
for existing in "$SERVICE"/*ts18*startup*.sh "$SERVICE"/*ts18*deepdiag*.sh; do
    [ -f "$existing" ] || continue
    selected=0
    for old in "$@"; do [ "$old" = "$existing" ] && selected=1; done
    [ "$selected" = 1 ] || { echo "BLOCKED: also select prior entry $existing"; exit 1; }
done
for lock in /data/adb/ts18-startup-logs/active /data/adb/ts18-deepdiag-v3/worker.lock /data/adb/ts18-startup-logs-1.3/active; do
    [ ! -e "$lock" ] || { echo "BLOCKED: inspect/stop existing collector: $lock"; exit 1; }
done
[ ! -e "$DEST" ] || { echo 'BLOCKED: existing toolkit; back up and review upgrade explicitly'; exit 1; }
[ ! -e "$ENTRY" ] || { echo "BLOCKED: target boot entry already exists: $ENTRY"; exit 1; }
for prior_stage in /data/adb/ts18-diagnostics-toolkit.stage-*; do
    [ ! -e "$prior_stage" ] && [ ! -L "$prior_stage" ] || {
        echo "BLOCKED: inspect stale installer stage: $prior_stage"; exit 1;
    }
done

service_created=0
if [ ! -d "$SERVICE" ]; then
    "$BB" mkdir -m 700 "$SERVICE" || exit 1
    "$BB" chown 0:0 "$SERVICE" || exit 1
    service_created=1
fi
BACKUP=/data/adb/ts18-diagnostics-backup-$("$BB" date -u +%Y%m%dT%H%M%SZ)-p$$
"$BB" mkdir -m 700 "$BACKUP" "$STAGE" || exit 1
"$BB" chown 0:0 "$BACKUP" "$STAGE" || exit 1
printf 'state=STAGING\nservice_created=%s\n' "$service_created" > "$BACKUP/TRANSACTION.txt" || exit 1

for file in ts18-startup-1.3.sh capture-lib.sh; do
    "$BB" cp "$SRC/$file" "$STAGE/$file" && "$BB" chown 0:0 "$STAGE/$file" && "$BB" chmod 700 "$STAGE/$file" || exit 1
    [ "$("$BB" sha256sum "$SRC/$file" | "$BB" cut -d ' ' -f 1)" = "$("$BB" sha256sum "$STAGE/$file" | "$BB" cut -d ' ' -f 1)" ] || exit 1
done
printf '#!/system/bin/sh\nexec /system/bin/sh /data/adb/ts18-diagnostics-toolkit/ts18-startup-1.3.sh --start forensic 180\n' > "$STAGE/boot-entry.new" || exit 1
"$BB" chown 0:0 "$STAGE/boot-entry.new" && "$BB" chmod 700 "$STAGE/boot-entry.new" || exit 1

rollback() {
    trap - HUP INT TERM
    rollback_ok=1
    if [ -e "$ENTRY" ]; then "$BB" mv "$ENTRY" "$BACKUP/new-entry.failed" || rollback_ok=0; fi
    for restore in "$@"; do
        saved=$BACKUP/${restore##*/}
        if [ -e "$saved" ] && [ ! -e "$restore" ]; then "$BB" mv "$saved" "$restore" || rollback_ok=0; fi
    done
    if [ -d "$DEST" ]; then "$BB" mv "$DEST" "$BACKUP/toolkit.failed" || rollback_ok=0; fi
    if [ -d "$STAGE" ]; then "$BB" mv "$STAGE" "$BACKUP/stage.failed" || rollback_ok=0; fi
    if [ "$rollback_ok" = 1 ]; then
        printf 'state=ROLLED_BACK\n' > "$BACKUP/TRANSACTION.txt" || echo "RECOVERY_REQUIRED: transaction record unavailable; inspect $BACKUP"
        echo "BLOCKED: install failed and prior boot entries were restored; inspect $BACKUP"
    else
        printf 'state=RECOVERY_REQUIRED\n' > "$BACKUP/TRANSACTION.txt" || echo "RECOVERY_REQUIRED: transaction record unavailable; inspect $BACKUP"
        echo "BLOCKED: install failed and automatic rollback was incomplete; do not reboot; inspect $BACKUP, $SERVICE and $DEST"
    fi
    exit 1
}
trap 'rollback "$@"' HUP INT TERM

"$BB" mv "$STAGE" "$DEST" || rollback "$@"
"$BB" mv "$DEST/boot-entry.new" "$ENTRY" || rollback "$@"
printf 'state=NEW_ENTRY_INSTALLED\n' > "$BACKUP/TRANSACTION.txt" || rollback "$@"
for old in "$@"; do
    "$BB" mv "$old" "$BACKUP/" || rollback "$@"
done
printf 'state=PASS\nentry=%s\ntoolkit=%s\n' "$ENTRY" "$DEST" > "$BACKUP/TRANSACTION.txt" || rollback "$@"
trap - HUP INT TERM
echo "PASS installed; older scripts retained in $BACKUP. No reboot/capture performed."
echo 'Review README rollback and --status before starting.'
