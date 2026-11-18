#!/system/bin/sh
# Native logcat file writers; each has a bounded file ceiling and explicit stop record.
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
dest=$1; cap=$2; mode=$3
case "$mode" in
  focus) set -- -b main -b system -b crash -v threadtime -v monotonic TS18Startup:V TS18Perf:V TS18Media:V TS18Nav:V TS18NavPerm:V TS18Transport:V TS18Launcher:V TS18Browser:V TS18Radio:V InCarCompactStartup:V OrganicMaps:V MapsWithMe:V Map:V MapView:V MwmActivity:V AndroidRuntime:E '*:S';;
  framework) set -- -b main -b system -b crash -v threadtime -v monotonic ActivityManager:I ActivityTaskManager:I MediaBrowser:V MediaBrowserServiceCompat:V MediaSessionService:I '*:S';;
  home) set -- -b events -v threadtime -v monotonic am_resume_activity:I wm_resume_activity:I am_on_resume_called:I wm_on_resume_called:I '*:S';;
  task) set -- -b events -v threadtime -v monotonic am_create_activity:I am_finish_activity:I am_destroy_activity:I am_task_removed:I am_pause_activity:I am_restart_activity:I am_new_intent:I am_on_create_called:I am_on_destroy_called:I wm_create_activity:I wm_finish_activity:I wm_destroy_activity:I '*:S';;
  *) exit 2;;
esac
started=$(stamp)
stop_requested=no
# Separate output from shell stdout: native -f avoids pipe buffering or a stuck reader.
( limit_file_bytes "$cap" || exit 125; exec /system/bin/logcat "$@" -f "$dest" ) > /dev/null 2> "$dest.stderr-tmp" &
writer=$!
on_stop() {
  stop_requested=yes
  b kill -TERM "$writer" 2>/dev/null
}
trap on_stop TERM HUP INT
# Signals interrupt wait in some ash versions. Boundedly ensure producer has ended.
wait "$writer"; rc=$?
i=0
while b kill -0 "$writer" 2>/dev/null && [ "$i" -lt 4 ]; do
  b sleep 1; i=$((i+1))
done
if b kill -0 "$writer" 2>/dev/null; then b kill -KILL "$writer" 2>/dev/null; wait "$writer" 2>/dev/null; fi
reason=producer_exit
[ "$stop_requested" != yes ] || reason=requested_stop_signal
case "$rc" in 153) reason=file_size_limit;; esac
printf '%s\n' "$rc" > "$dest.rc"
{
  printf 'mode=%s started=%s stopped=%s reason=%s producer_exit=%s size=%s cap=%s\n' \
    "$mode" "$started" "$(stamp)" "$reason" "$rc" "$(size "$dest")" "$cap"
} > "$dest.stop-status"
b head -c 4096 "$dest.stderr-tmp" > "$dest.err" 2>/dev/null
b rm -f "$dest.stderr-tmp"
exit 0
