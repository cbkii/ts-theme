#!/system/bin/sh
# No writes in /data/adb or /tmp. Worker waits read-only until Download is proven.
MODDIR=${0%/*}
BB=/data/adb/magisk/busybox
[ -x "$BB" ] || exit 0
ASH_STANDALONE=1 "$BB" setsid "$BB" sh "$MODDIR/collector.sh" post-fs-data </dev/null >/dev/null 2>&1 &
exit 0
