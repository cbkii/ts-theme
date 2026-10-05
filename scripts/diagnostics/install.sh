#!/system/bin/sh
# Explicit installer; NOT invoked by the collector. No reboot or capture starts.
PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin
export PATH
unset LD_PRELOAD LD_LIBRARY_PATH
umask 077
BB=/data/adb/magisk/busybox
[ -x "$BB" ] && [ "$("$BB" id -u)" = 0 ] || { echo 'BLOCKED: native Magisk root required'; exit 1; }
[ "${1:-}" = --install ] || { echo 'Usage: install.sh --install [EXACT_OLD_SERVICE_SCRIPT ...]'; exit 64; }
shift
SRC=$("$BB" dirname "$("$BB" readlink -f "$0")")
DEST=/data/adb/ts18-diagnostics-toolkit
# Validate entire bundle against the independently compared SHA256SUMS first.
(cd "$SRC" && "$BB" sha256sum -c SHA256SUMS) || exit 1
for old in "$@"; do
    case "$old" in
      /data/adb/service.d/*ts18*startup*.sh|/data/adb/service.d/*ts18*deepdiag*.sh) ;;
      *) echo 'BLOCKED: unexpected prior collector path'; exit 1 ;;
    esac
    case "$old" in *..*|*' '*|*'
'*) exit 1 ;; esac
    [ "${old%/*}" = /data/adb/service.d ] || exit 1
    [ -f "$old" ] && [ ! -L "$old" ] || exit 1
done
# Every recognised prior boot entry must be explicitly selected for backup.
for existing in /data/adb/service.d/*ts18*startup*.sh /data/adb/service.d/*ts18*deepdiag*.sh; do
    [ -f "$existing" ] || continue
    selected=0
    for old in "$@"; do [ "$old" = "$existing" ] && selected=1; done
    [ "$selected" = 1 ] || { echo "BLOCKED: also select prior entry $existing"; exit 1; }
done
# Refuse all active/stale locks until the operator inspects/finishes their run.
for lock in /data/adb/ts18-startup-logs/active /data/adb/ts18-deepdiag-v3/worker.lock /data/adb/ts18-startup-logs-1.3/active; do
    [ ! -e "$lock" ] || { echo "BLOCKED: inspect/stop existing collector: $lock"; exit 1; }
done
[ ! -e "$DEST" ] || { echo 'BLOCKED: existing toolkit; back up and review upgrade explicitly'; exit 1; }
BACKUP=/data/adb/ts18-diagnostics-backup-$("$BB" date -u +%Y%m%dT%H%M%SZ)
"$BB" mkdir -m 700 "$BACKUP" "$DEST" || exit 1
"$BB" chown 0:0 "$BACKUP" "$DEST" || exit 1
for file in ts18-startup-1.3.sh capture-lib.sh; do
    "$BB" cp "$SRC/$file" "$DEST/$file" && "$BB" chown 0:0 "$DEST/$file" && "$BB" chmod 700 "$DEST/$file" || exit 1
    [ "$("$BB" sha256sum "$SRC/$file" | "$BB" cut -d ' ' -f 1)" = "$("$BB" sha256sum "$DEST/$file" | "$BB" cut -d ' ' -f 1)" ] || exit 1
done
# Move only explicitly selected older entry points. Preserve bytes and metadata.
for old in "$@"; do
    "$BB" mv "$old" "$BACKUP/" || exit 1
done
entry=/data/adb/service.d/76-ts18-startup-1.3.sh
[ ! -e "$entry" ] || exit 1
printf '#!/system/bin/sh\nexec /system/bin/sh /data/adb/ts18-diagnostics-toolkit/ts18-startup-1.3.sh --start forensic 180\n' > "$DEST/boot-entry.new" || exit 1
"$BB" chown 0:0 "$DEST/boot-entry.new" && "$BB" chmod 700 "$DEST/boot-entry.new" && "$BB" mv "$DEST/boot-entry.new" "$entry" || exit 1
echo "PASS installed; older scripts retained in $BACKUP. No reboot/capture performed."
echo 'Review README rollback and --status before starting.'
