#!/system/bin/sh
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
CONTROL=$1; MAIN=$2
while [ ! -f "$CONTROL/finished" ]; do
  deadline=$(b cat "$CONTROL/deadline" 2>/dev/null)
  if ! alive "$MAIN" || { number "$deadline" && [ "$(up)" -ge "$deadline" ]; }; then
    printf 'watchdog_or_parent_exit uptime=%s\n' "$(up)" > "$CONTROL/guard-fired"
    alive "$MAIN" && b kill -TERM "${MAIN%:*}" 2>/dev/null
    n=0
    while alive "$MAIN" && [ ! -f "$CONTROL/finished" ] && [ "$n" -lt 15 ]; do b sleep 1; n=$((n+1)); done
    [ ! -f "$CONTROL/finished" ] || exit 0
    for f in "$CONTROL"/*.job; do
      [ -f "$f" ] || continue
      id=$(b cat "$f"); signal_job "$id" INT
    done
    b sleep 2
    for f in "$CONTROL"/*.job; do
      [ -f "$f" ] || continue
      id=$(b cat "$f"); signal_job "$id" TERM
    done
    b sleep 1
    for f in "$CONTROL"/*.job; do
      [ -f "$f" ] || continue
      id=$(b cat "$f"); signal_job "$id" KILL
    done
    alive "$MAIN" && b kill -KILL "${MAIN%:*}" 2>/dev/null
    exit 0
  fi
  b sleep 1
done
