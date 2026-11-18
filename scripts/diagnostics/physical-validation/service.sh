#!/system/bin/sh
# Late-start independent wake-up; Download-claim ensures single flight.
MODDIR=${0%/*}
BB=/data/adb/magisk/busybox
[ -x "$BB" ] || exit 0
ASH_STANDALONE=1 "$BB" setsid "$BB" sh "$MODDIR/collector.sh" late_start </dev/null >/dev/null 2>&1 &
exit 0
