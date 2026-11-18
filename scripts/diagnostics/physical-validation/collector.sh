#!/system/bin/sh
# TS18 v3.6.0: persistent runtime writes ONLY beneath a dynamically verified Download mount.
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
PHASE=${1:-late_start}
FOLLOWUP=no
[ "$PHASE" != followup ] || FOLLOWUP=yes
[ -x "$BB" ] || exit 0
BOOTID=$(b cat /proc/sys/kernel/random/boot_id 2>/dev/null)
case "$BOOTID" in ''|*[!a-f0-9-]*) exit 0;; esac
sdk=$(bounded 2 /system/bin/getprop ro.build.version.sdk 2>/dev/null)
dev=$(bounded 2 /system/bin/getprop ro.product.device 2>/dev/null)
board=$(bounded 2 /system/bin/getprop ro.product.board 2>/dev/null)
[ "$sdk:$dev:$board" = '29:s9863a1h10:uis8581a2h10' ] || exit 0
# Never stage under /tmp, /data/local/tmp, /data/adb or an unproven storage path.
# Waiting for Download sacrifices some pre-mount live samples. Early logcat events
# are queried from the framework ring after the mount is proven.
WAIT_START=$(up); ROOT=''; ROOT_DEADLINE=$((WAIT_START+150))
while [ "$(up)" -lt "$ROOT_DEADLINE" ]; do
  ROOT=$(download_probe "$BOOTID") && [ -n "$ROOT" ] && break
  b sleep 2
done
[ -n "$ROOT" ] && [ -d "$ROOT" ] && [ ! -L "$ROOT" ] || exit 0
free_kb=$(b df -Pk "$ROOT" 2>/dev/null | b awk 'END {print $4}')
if ! number "$free_kb" || [ "$free_kb" -lt 262144 ]; then
  printf 'BLOCKED: at least 256MiB free required; existing results preserved.\n' > "$ROOT/SPACE-BLOCKED.txt"
  exit 0
fi
# Do not allocate even a claim folder on a boot following a completed campaign.
STATE="$ROOT/.campaign-state"
FIRST_FILE="$ROOT/.campaign-first"
if [ "$FOLLOWUP" = no ] && [ -f "$STATE" ]; then
  IFS='|' read -r priorboot priorcount < "$STATE"
  number "$priorcount" || exit 0
  [ "$priorcount" -lt 3 ] && [ "$priorboot" != "$BOOTID" ] || exit 0
fi
# Cross-hook single-flight claim lives in Download. The winning worker is alone
# in this boot; reboots replace the process, so no cross-boot writer survives.
CLAIM="$ROOT/.claim-$BOOTID"
[ "$FOLLOWUP" = no ] || CLAIM="$ROOT/.followup-claim-$BOOTID-$(up)"
b mkdir -m 700 "$CLAIM" 2>/dev/null || exit 0
# Same-boot manual follow-ups share a recorder lock with boot capture.
RECORDER_LOCK="$ROOT/.recording-$BOOTID"
b mkdir -m 700 "$RECORDER_LOCK" 2>/dev/null || exit 0
if [ "$FOLLOWUP" = no ]; then
oldboot=''; count=0
if [ "$FOLLOWUP" = no ] && [ -f "$STATE" ]; then
  IFS='|' read -r oldboot count < "$STATE"
  number "$count" || exit 0
fi
[ "$oldboot" != "$BOOTID" ] || exit 0
[ "$count" -lt 3 ] || exit 0
if [ -n "$oldboot" ] && [ "$oldboot" != "$BOOTID" ] && [ "$count" -ge 1 ]; then
  bounded 30 "$BB" sh "$MODDIR/recover.sh" "$ROOT" "$oldboot" "$count" > "$CLAIM/prior-recovery.txt" 2>&1
fi
count=$((count+1))
# State is committed before work starts: interruption consumes one of three attempts.
printf '%s|%s\n' "$BOOTID" "$count" > "$CLAIM/state.tmp" || exit 0
b mv "$CLAIM/state.tmp" "$STATE" || exit 0
if [ "$count" -eq 1 ]; then
  printf '%s\n' "$BOOTID" > "$FIRST_FILE" || exit 0
elif [ ! -s "$FIRST_FILE" ]; then
  printf 'BLOCKED: first campaign ID missing; preserving second run.\n' > "$CLAIM/missing-first.txt"
fi
  RUNLABEL=$count
else
  count=3
  RUNLABEL="followup-$(up)"
fi
OUT="$ROOT/boot-$RUNLABEL-$BOOTID"; CONTROL="$OUT/.control"
b mkdir -m 700 "$OUT" "$CONTROL" || exit 0
printf '%s\n' "$BOOTID" > "$OUT/boot-id.txt"
b cp "$MODDIR/targets.tsv" "$OUT/expected-targets.tsv"
b cp "$MODDIR/expected-apks.tsv" "$OUT/expected-apks.tsv"
b cp "$MODDIR/module.prop" "$OUT/collector-module.prop"
b cp "$MODDIR/TEST-MATRIX.tsv" "$OUT/TEST-MATRIX.tsv"
b cp "$MODDIR/docs/CAMPAIGN-NOTES.md" "$OUT/CAMPAIGN-NOTES-TEMPLATE.md"
printf '%s\n' "$OUT" > "$ROOT/.active"
printf 'CAPTURING: wait for READY-TO-UPLOAD.txt; this is an active, unsealed folder.\n' > "$OUT/CAPTURE-IN-PROGRESS.txt"
START=$(up); FINISHING=no; REASON=deadline
RUN_BUDGET_BYTES=32000000
# V3.5 default VALIDATION mode always respects the same bounded hard-output envelope.
MODE=validation
if [ -r "$ROOT/CAPTURE_MODE.conf" ]; then
  configured=$(b sed -n 's/^CAPTURE_MODE=//p' "$ROOT/CAPTURE_MODE.conf" | b head -n 1)
  case "$configured" in validation|lean|logs_only) MODE=$configured;; esac
elif [ -r "$MODDIR/capture.conf" ]; then
  configured=$(b sed -n 's/^CAPTURE_MODE=//p' "$MODDIR/capture.conf" | b head -n 1)
  case "$configured" in validation|lean|logs_only) MODE=$configured;; esac
fi
export RUN_BUDGET_BYTES
MAIN="$$:$(token $$)"
printf '%s\n' $((START+370)) > "$CONTROL/deadline"
job "$MODDIR/guard.sh" "$CONTROL" "$MAIN"
TASKLOG=''; FRAMELOG=''; OBSERVER=''; LOG=''; HOMELOG=''; VIDEO=''; SNAP=''; PREFLIGHT=''
HIST_HOME=''; OBS_HOME=''; PROFILE=2; VTRIES=0; MAX_VIDEO=0; VFAILED=0; VGOOD=0; VLAST=0
VFILE=''; VSTART=0; VLEN=0; SHOTS=0
LOGTRIES=0; FRAMETRIES=0; TASKTRIES=0; NEXT_VIDEO=0; NEXT_BUDGET=0; NEXT_SHOT=0
register() { printf '%s\n' "$2" > "$CONTROL/$1.job"; }
start_log() {
  [ "$LOGTRIES" -lt 6 ] || return 0
  LOGTRIES=$((LOGTRIES+1))
  cap=1048576
  job "$MODDIR/stream.sh" "$OUT/target-logcat-$LOGTRIES.txt" "$cap" focus
  LOG=$JT; register log "$JT"
  event LOGCAT_START "mode=focus cap=$cap attempt=$LOGTRIES"
}
start_framework() {
  [ "$FRAMETRIES" -lt 4 ] || return 0
  FRAMETRIES=$((FRAMETRIES+1))
  job "$MODDIR/stream.sh" "$OUT/framework-admission-$FRAMETRIES.txt" 524288 framework
  FRAMELOG=$JT; register framework "$JT"
  event FRAMEWORK_LOG_START "stream=$FRAMETRIES native_output=yes"
}
start_tasks() {
  [ "$TASKTRIES" -lt 3 ] || return 0
  TASKTRIES=$((TASKTRIES+1))
  job "$MODDIR/stream.sh" "$OUT/task-lifecycle-events-$TASKTRIES.log" 524288 task
  TASKLOG=$JT; register task "$JT"
  event TASK_EVENTS_START "stream=$TASKTRIES buffer=events"
}
start_observers() {
  job "$MODDIR/observer.sh" "$OUT"; OBSERVER=$JT; register observer "$JT"
  if [ -x /system/bin/logcat ]; then
    job "$MODDIR/stream.sh" "$OUT/home-events.log" 65536 home
    HOMELOG=$JT; register home "$JT"
    start_log
    start_framework
    start_tasks
  else status LOGCAT BLOCKED 'no executable logcat at Android system path'; fi
}
close_video() {
  [ -n "$VIDEO" ] || return 0
  stop_video_job "$VIDEO"
  end=$(up); VLAST=$end
  video_rc=$(b cat "$VFILE.rc" 2>/dev/null)
  if mp4check "$VFILE"; then
    valid=STRUCTURE_OK_DECODE_NOT_RUN; VGOOD=$((VGOOD+1))
    if [ "$video_rc" = 0 ]; then
      status VIDEO PASS "$VFILE MP4 structural verification only; pixels NOT_RUN"
    else
      status VIDEO WARN "$VFILE MP4 structure valid but recorder rc=${video_rc:-UNKNOWN}; playback NOT_RUN"
    fi
  else
    valid=INVALID_OR_INCOMPLETE; VFAILED=$((VFAILED+1))
    VTRIES=$MAX_VIDEO
    status VIDEO WARN "invalid/incomplete MP4 $VFILE; inspect encoder stderr"
    # Keep only first 128 bytes and diagnostics if invalid; low-value raw removed.
    b head -c 128 "$VFILE" > "$VFILE.invalid-header" 2>/dev/null
    b rm -f "$VFILE"
  fi
  line "$OUT/video-timeline.tsv" 16384 "${VFILE##*/} $VSTART $end profile=$PROFILE rc=$(b cat "$VFILE.rc" 2>/dev/null) $valid"
  event VIDEO_STOP "file=${VFILE##*/} end=$end result=$valid"
  VIDEO=''
}
stop_observers() {
  : > "$OUT/STOP"
  for id in "$OBSERVER" "$LOG" "$HOMELOG" "$FRAMELOG" "$TASKLOG" "$SNAP" "$PREFLIGHT"; do signal_job "$id" TERM; done
  close_video
  for id in "$OBSERVER" "$LOG" "$HOMELOG" "$FRAMELOG" "$TASKLOG" "$SNAP" "$PREFLIGHT"; do stop_job "$id" TERM; done
  TASKLOG=''; FRAMELOG=''; OBSERVER=''; LOG=''; HOMELOG=''; SNAP=''; PREFLIGHT=''
}
seal() {
  (cd "$OUT" && b find . -type f ! -path './.control/*' ! -name MANIFEST.sha256 ! -name manifest-check.txt | b sort | while IFS= read -r f; do b sha256sum "$f" || exit 1; done) > "$OUT/MANIFEST.sha256"
  if (cd "$OUT" && bounded 20 "$BB" sha256sum -c MANIFEST.sha256 > manifest-check.txt 2>&1); then
    b rm -f "$OUT/MANIFEST_VERIFY_FAILED.txt"
  else
    printf 'Manifest verification failed; no complete-archive claim.\n' > "$OUT/MANIFEST_VERIFY_FAILED.txt"
  fi
}
finish() {
  [ "$FINISHING" = no ] || return 0
  FINISHING=yes; trap '' TERM HUP INT
  stop_observers
  end=$(up)
  event CAPTURE_END "uptime=$end reason=$REASON"
  if [ -s "$OUT/preflight/COMPLETE.txt" ]; then
    printf 'status=COMPLETED_SCOPE per_probe_results=preflight/CAPABILITY_MATRIX.tsv\n' > "$OUT/PREFLIGHT-STATUS.txt"
  elif [ -s "$OUT/preflight/PROBE_RUN.txt" ]; then
    printf 'status=INCOMPLETE reason=preflight_interrupted_or_failed\n' > "$OUT/PREFLIGHT-STATUS.txt"
  else
    printf 'status=BLOCKED reason=preflight_not_produced\n' > "$OUT/PREFLIGHT-STATUS.txt"
  fi
  {
    printf 'stream\tbytes\tproducer_exit\tstop_reason\n'
    for f in "$OUT"/home-events.log "$OUT"/framework-admission-*.txt "$OUT"/task-lifecycle-events-*.log "$OUT"/target-logcat-*.txt; do
      [ -f "$f" ] || continue
      printf '%s\t%s\t%s\t%s\n' "${f##*/}" "$(size "$f")" "$(b cat "$f.rc" 2>/dev/null || echo UNKNOWN)" "$(b cat "$f.stop-status" 2>/dev/null || echo UNKNOWN)"
    done
  } > "$OUT/LOG-STREAM-STATUS.tsv"
  b sh "$MODDIR/ai-summary.sh" "$OUT" "$count" >/dev/null 2>&1 || status AI_SUMMARY WARN "event index generator failed"
  used=$(b du -sk "$OUT" 2>/dev/null | b awk '{print $1+0}')
  {
    printf 'module=3.6.0\nboot_id=%s\nattempt=%s/3\nphase=%s\nstart_uptime=%s\nstop_uptime=%s\nreason=%s\n' "$BOOTID" "$count" "$PHASE" "$START" "$end" "$REASON"
    printf 'home_event_uptime=%s\nhome_first_sample_start=%s\npre_mount_capture=NOT_RUN_DOWNLOAD_NOT_READY\n' "${HIST_HOME:-UNKNOWN}" "${OBS_HOME:-UNKNOWN}"
    printf 'video_windows_requested=%s\nvideo_structurally_valid=%s\nvideo_failed=%s\nvideo_last_stop_uptime=%s\n' "$MAX_VIDEO" "$VGOOD" "$VFAILED" "$VLAST"
    printf 'capture_kind=%s\n' "$PHASE"
    printf 'downloads_exported=yes\noutput_mount=VERIFIED_READ_WRITE_RENAME\n'
    if [ "$REASON" = run_deadline ]; then printf 'capture_status=COMPLETED_OBSERVATION_WINDOW\n'; else printf 'capture_status=PARTIAL_WINDOW\n'; fi
    printf 'status_counts_scope=bounded_journal_only\n'
    b awk -F '\t' '{n[$3]++} END {for (k in n) print "probe_journal_" k "=" n[k]}' "$OUT/status.tsv"
    printf 'mode=%s\nrun_budget_bytes=%s\nrun_used_kib=%s\n' "$MODE" "$RUN_BUDGET_BYTES" "$used"
    printf 'archive_part_limit_bytes=20000000\narchive_total_limit_bytes=100000000\n'
    printf 'expected_revision_to_installed_APK_binding=SEE_APK_MATCH_TSV\n'
    printf 'video_encoder_limit_bytes=4194304\nlog_app_file_limit_bytes=1048576\n'
    [ ! -s "$OUT/APK-MATCH.tsv" ] || b cat "$OUT/APK-MATCH.tsv"
    printf 'campaign_plan=three_up_to_5min_user_driven_boot_runs;consecutive_bounded_screenrecord_segments_per_run_if_budget_when_enabled\n'
    printf 'video_decode_and_visible_content=NOT_RUN\naudible_output=NOT_TESTED_NO_AUDIO_RECORDING\n'
    printf 'app_UID_or_SAF_storage_visibility=NOT_RUN_ROOT_NAMESPACE_ONLY\n'
    printf 'physical_acceptance=REQUIRES_VIDEO_LOGS_AND_OPERATOR_REVIEW\n'
    [ ! -f "$CONTROL/guard-fired" ] || b cat "$CONTROL/guard-fired"
  } > "$OUT/SUMMARY.txt"
  live=no
  for f in "$CONTROL"/*.job; do [ -f "$f" ] || continue; group_alive "$(b cat "$f")" && live=yes; done
  b rm -f "$OUT/CAPTURE-IN-PROGRESS.txt"
  if [ "$live" = no ]; then seal; else printf 'INCOMPLETE: writer still alive; not sealed\n' > "$OUT/INCOMPLETE.txt"; fi
  if [ "$live" = no ] && [ ! -f "$OUT/MANIFEST_VERIFY_FAILED.txt" ]; then
    printf 'SEALED: upload this boot folder or wait for boot 3 combined parts.\n' > "$ROOT/READY-TO-UPLOAD-boot-$RUNLABEL.txt"
  fi
  printf 'attempt=%s end=%s result=%s\n' "$count" "$end" "$OUT" > "$ROOT/.last-status"
  : > "$CONTROL/finished"
  [ "$live" != no ] || b rmdir "$RECORDER_LOCK" 2>/dev/null
  if [ "$FOLLOWUP" = no ] && [ "$count" -eq 3 ] && [ "$live" = no ] && [ ! -f "$OUT/MANIFEST_VERIFY_FAILED.txt" ] && [ -s "$FIRST_FILE" ]; then
    # Bundle output and all temporary metadata remain in verified Download.
    bounded 145 "$BB" sh "$MODDIR/bundle.sh" "$ROOT" "$BOOTID" > "$ROOT/bundle-status.txt" 2>&1 || :
  fi
}
trap 'REASON=signal_or_watchdog; finish; exit 0' TERM HUP INT
trap 'finish' EXIT
status STORAGE PASS "Verified Download mount and read/write/rename; no /tmp or /data/adb runtime writes"
event CAPTURE_START "phase=$PHASE boot=$BOOTID attempt=$count mode=$MODE"
capture "$OUT/early-ring-events.txt" 4 131072 /system/bin/logcat -b events -v threadtime -v monotonic -d -t 1000
capture "$OUT/early-context.txt" 2 8192 "$BB" sh -c 'id; cat /proc/self/attr/current; readlink /proc/self/ns/mnt; cat /proc/uptime'
start_observers
# Launch the source-contract probes before lower-priority repeated snapshots.
job "$MODDIR/preflight.sh" "$OUT" "$count"; PREFLIGHT=$JT; register preflight "$JT"
job "$MODDIR/snapshots.sh" "$OUT" "$count"; SNAP=$JT; register snapshots "$JT"
NEXT_VIDEO=$((START+3))
case "$MODE" in
  validation) MAX_VIDEO=7;;
  lean) [ "$count" -eq 3 ] && MAX_VIDEO=2 || MAX_VIDEO=0;;
  *) MAX_VIDEO=0;;
esac
if [ "$MAX_VIDEO" -gt 0 ] && [ ! -x /system/bin/screenrecord ] && [ ! -x /vendor/bin/screenrecord ] && [ ! -x /product/bin/screenrecord ]; then
  VFAILED=1; VTRIES=$MAX_VIDEO; status VIDEO BLOCKED 'no native screenrecord; fallback stills limited'
fi
while :; do
  NOW=$(up)
  if [ "$NOW" -ge "$NEXT_BUDGET" ]; then
    NEXT_BUDGET=$((NOW+1))
    used=$(b du -sk "$OUT" 2>/dev/null | b awk '{print $1+0}')
    if number "$used" && [ $((used*1024)) -ge "$RUN_BUDGET_BYTES" ]; then
      REASON=run_byte_budget; break
    fi
  fi
  [ ! -f "$CONTROL/request-stop" ] || { REASON=user_stop; break; }
  pkg=$(b cat "$OUT/home-package" 2>/dev/null); [ -n "$pkg" ] || pkg=com.cbkii.ts18launcher
  candidate=$(home_time "$OUT/home-events.log" "$pkg")
  if number "$candidate" && [ "$candidate" -gt 0 ] && [ "$candidate" -le "$NOW" ]; then
    if [ -z "$HIST_HOME" ] || [ "$candidate" -lt "$HIST_HOME" ]; then
      HIST_HOME=$candidate; event HOME_EVENT "package=$pkg uptime=$HIST_HOME; earlier events may be missing"
    fi
  fi
  observed=$(b cat "$OUT/home-observed" 2>/dev/null)
  if [ -z "$OBS_HOME" ] && number "$observed"; then OBS_HOME=$observed; fi
  [ "$NOW" -lt $((START+295)) ] || { REASON=run_deadline; break; }
  if [ -n "$LOG" ] && ! alive "$LOG" && ! group_alive "$LOG"; then
    LOG=''; start_log
  fi
  if [ -n "$FRAMELOG" ] && ! alive "$FRAMELOG" && ! group_alive "$FRAMELOG"; then
    FRAMELOG=''; start_framework
  fi
  if [ -n "$TASKLOG" ] && ! alive "$TASKLOG" && ! group_alive "$TASKLOG"; then
    TASKLOG=''; start_tasks
  fi
  if [ -n "$VIDEO" ]; then
    if ! alive "$VIDEO" || [ "$NOW" -ge $((VSTART+VLEN+3)) ]; then
      close_video
      # Consecutive segments; actual stop/start timestamps expose recorder gaps.
      NEXT_VIDEO=$(up)
    fi
  fi
  if [ "$MAX_VIDEO" -gt 0 ] && [ -z "$VIDEO" ] && [ "$VTRIES" -lt "$MAX_VIDEO" ] && [ "$NOW" -ge "$NEXT_VIDEO" ]; then
    sf=$(bounded 1 /system/bin/getprop init.svc.surfaceflinger 2>/dev/null)
    if [ "$sf" = running ]; then
      # Reserve headroom for framework logs, preflight and checksum sealing.
      used=$(b du -sk "$OUT" 2>/dev/null | b awk '{print $1+0}')
      if [ $((used*1024+5000000)) -lt "$RUN_BUDGET_BYTES" ]; then
        event VIDEO_GAP "previous_stop=$VLAST next_command=$NOW first_frame=UNKNOWN"
        VTRIES=$((VTRIES+1)); VLEN=45; VFILE="$OUT/video-$(printf '%02d' "$VTRIES").mp4"; VSTART=$NOW
        job "$MODDIR/record.sh" "$VFILE" "$VLEN" "$PROFILE"
        VIDEO=$JT; register video "$JT"
        event VIDEO_START "file=${VFILE##*/} start_uptime=$VSTART; max ${VLEN}s, no audio"
      else
        VTRIES=$MAX_VIDEO; status VIDEO WARN 'budget headroom insufficient; skipping remaining optional video'
      fi
    fi
  fi
  if [ "$MODE" != logs_only ] && [ "$VFAILED" -ge 1 ] && [ -z "$VIDEO" ] && [ "$SHOTS" -lt 2 ] && [ "$NOW" -ge "$NEXT_SHOT" ]; then
    SHOTS=$((SHOTS+1)); NEXT_SHOT=$((NOW+75))
    (bounded 2 /system/bin/screencap -p 2>/dev/null | b head -c 524288) > "$OUT/fallback-$SHOTS-uptime-$NOW.png"
    status SCREEN_STILL WARN 'PNG decode NOT_RUN; output capped at 512KiB'
  fi
  b sleep 1
done
finish
exit 0
