#!/system/bin/sh
# Optional one-shot, bounded read-only power observer. Power wake is not proof of vehicle ACC.
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
ROOT=$(download_probe "$(b cat /proc/sys/kernel/random/boot_id)") || exit 1
if [ "${1:-}" != worker ]; then
  b mkdir "$ROOT/.wake-watch" 2>/dev/null || { echo 'A wake watch is already armed'; exit 1; }
  job "$MODDIR/arm-wake.sh" worker
  echo 'Armed for up to 30 minutes. It must observe asleep/dozing then awake. No power commands are sent.'
  exit 0
fi
trap 'b rmdir "$ROOT/.wake-watch" 2>/dev/null' EXIT
until=$(( $(up)+1800 )); sleeping=no
printf 'state=ARMED start_uptime=%s\n' "$(stamp)" > "$ROOT/WAKE-WATCH.txt"
while [ "$(up)" -lt "$until" ]; do
  state=$(bounded 2 /system/bin/dumpsys power | b sed -n 's/.*mWakefulness=\([A-Za-z]*\).*/\1/p' | b head -n 1)
  case "$state" in Asleep|Dozing) sleeping=yes;;
    Awake) if [ "$sleeping" = yes ]; then
      printf 'state=OBSERVED_POWER_WAKE uptime=%s acc=REQUIRES_OPERATOR_CONFIRMATION\n' "$(stamp)" >> "$ROOT/WAKE-WATCH.txt"
      b rmdir "$ROOT/.wake-watch"
      exec "$BB" sh "$MODDIR/collector.sh" followup
    fi;;
  esac
  b sleep 5
done
printf 'state=TIMED_OUT_NO_PROVEN_WAKE uptime=%s\n' "$(stamp)" >> "$ROOT/WAKE-WATCH.txt"
b rmdir "$ROOT/.wake-watch"
