#!/system/bin/sh
# TS18 cold-start / Music + Maps validation v2. No automatic app actions.
umask 077
BB=/data/adb/magisk/busybox
BASE=/storage/emulated/0/Download/TS18-ColdStart
SERVICE=/data/adb/service.d/97-ts18-coldstart.sh
EXPECTED_COLLECTOR=5bfb2bdf7ac88c37bab0833c3ed39931adbbcbab3fe689cadf0e091a415826f4
PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin
export PATH
unset LD_PRELOAD LD_LIBRARY_PATH
help() {
  while IFS= read -r help_line; do printf '%s\n' "$help_line"; done <<'HELP'
TS18 Music + Maps cold-start validation v2
  no argument / guide : beginner-friendly menu
  install             : install verified collector as Magisk service.d script
  start               : start one manual eight-minute capture (never called cold boot)
  status              : show installed collector and current/latest capture
  stop                : stop this kit's collector only
  export              : archive latest CLOSED capture + validation results in Download
  uninstall           : remove only this kit's verified service.d script
  help                : no root required

Use the existing Termux s/st root lane. The scripts never invoke su themselves.
No app launch/stop/Play, root-helper invocation, provider query or settings mutation.
P = works reliably at an acceptable speed; S = works but noticeably slow;
F = fails; N = not run / blocked / timing unknown. Notes optional.
Use a phone video with audio for timing. Logs alone cannot prove pixels or sound.
All generated output is in Download/TS18-ColdStart.
HELP
}
case "${1:-}" in help|--help|-h) help; exit 0;; esac
[ -x "$BB" ] || { echo 'Magisk BusyBox unavailable. Stop; do not install packages or change permissions.'; exit 69; }
[ "$("$BB" id -u)" = 0 ] || { echo 'Enter your existing s/st root lane, then run this same command again.'; exit 77; }
SELF=$("$BB" readlink -f "$0") || exit 69
DIR=${SELF%/*}
COLLECTOR=$DIR/ts18-coldstart-boot.sh
die() { printf 'STOPPED: %s\n' "$*" >&2; exit 1; }
ms() {
  read -r vm_up _vm_rest < /proc/uptime
  vm_s=${vm_up%%.*}; vm_f=${vm_up#*.}000; vm_f=${vm_f%"${vm_f#???}"}
  printf '%s\n' "$((vm_s * 1000 + 1$vm_f - 1000))"
}
verify_collector() {
  [ -f "$1" ] && [ ! -L "$1" ] || return 1
  vc_size=$("$BB" stat -c %s "$1") || return 1
  [ "$vc_size" -le 48000 ] || return 1
  vc_hash=$("$BB" sha256sum "$1") || return 1
  [ "${vc_hash%% *}" = "$EXPECTED_COLLECTOR" ]
}
base_ready() {
  if [ ! -d /storage/emulated/0/Download ] || [ ! -w /storage/emulated/0/Download ]; then die 'Unlock Android and make Download available first.'; fi
  [ ! -L "$BASE" ] || die 'Output folder is a symbolic link.'
  "$BB" mkdir -p "$BASE" || die 'Cannot create Download output folder.'
  TMPDIR=$BASE; export TMPDIR
}
run_path() {
  RP=''
  [ -f "$BASE/latest" ] && [ ! -L "$BASE/latest" ] || return 1
  IFS= read -r rn < "$BASE/latest"
  case "$rn" in run-boot-*|run-manual-*) ;; *) return 1;; esac
  case "$rn" in *[!A-Za-z0-9_-]*) return 1;; esac
  [ -d "$BASE/$rn" ] && [ ! -L "$BASE/$rn" ] || return 1
  rp_real=$("$BB" readlink -f "$BASE/$rn") || return 1
  rp_base=$("$BB" readlink -f "$BASE") || return 1
  case "$rp_real/" in "$rp_base/"*) RP=$BASE/$rn;; *) return 1;; esac
}
is_active() {
  run_path || return 1
  [ -f "$BASE/active/owner" ] && [ ! -L "$BASE/active" ] && [ ! -L "$BASE/active/owner" ] || return 1
  read -r ac_boot ac_pid ac_ticks ac_run < "$BASE/active/owner"
  case "$ac_pid:$ac_ticks" in *[!0-9:]*|:*) return 1;; esac
  IFS= read -r ac_current < /proc/sys/kernel/random/boot_id
  [ "$ac_boot" = "$ac_current" ] && [ "$ac_run" = "${RP##*/}" ] || return 1
  "$BB" grep -qx 'status=RUNNING' "$RP/STATUS.txt" || return 1
  [ -r "/proc/$ac_pid/stat" ] || return 1
  IFS= read -r ac_stat < "/proc/$ac_pid/stat"
  # shellcheck disable=SC2086 # Numeric token splitting, literal AWK or Magisk ash pipefail.
  ac_stat=${ac_stat##*) }
  # shellcheck disable=SC2086 # Split numeric /proc fields after removing comm.
  set -- $ac_stat
  [ "$1" != Z ] || return 1
  shift 19
  [ "$1" = "$ac_ticks" ]
}
status() {
  printf '\nInstalled boot collector: '
  if verify_collector "$SERVICE"; then echo 'MATCH (enabled for future Android boots)'; else echo 'not installed / different version'; fi
  if is_active; then
    printf 'Capture RUNNING: %s\n' "${RP##*/}"
    "$BB" cat "$RP/CONTEXT.txt"
  elif run_path; then
    printf 'Latest capture: %s\n' "${RP##*/}"
    "$BB" cat "$RP/STATUS.txt" 2>/dev/null || echo 'INCOMPLETE — not a pass.'
    [ -f "$RP/COMPLETE.txt" ] || echo 'No completion seal. Preserve and inspect partial evidence.'
  else
    echo 'No capture yet. An installed script alone does not prove boot capture worked.'
  fi
}
install() {
  verify_collector "$COLLECTOR" || die 'Collector missing, modified or over 48,000 bytes. Re-extract the matching kit.'
  if [ ! -d /data/adb ] || [ -L /data/adb ]; then die 'Unexpected Magisk directory.'; fi
  [ ! -L /data/adb/service.d ] || die 'service.d is a symbolic link.'
  "$BB" mkdir -p /data/adb/service.d || die 'Cannot create service.d.'
  [ "$("$BB" readlink -f /data/adb/service.d)" = /data/adb/service.d ] || die 'Unexpected service.d path.'
  if [ -e "$SERVICE" ]; then
    [ ! -L "$SERVICE" ] || die 'Reserved service is a symbolic link.'
    if verify_collector "$SERVICE"; then echo 'This exact collector is already installed.'; return; fi
    old_size=$("$BB" stat -c %s "$SERVICE") || die 'Cannot identify installed collector.'
    old_hash=$("$BB" sha256sum "$SERVICE") || die 'Cannot hash installed collector.'
    if [ "$old_size" -gt 48000 ] || [ "${old_hash%% *}" != 983b1ec7bb3500bbd40523e20eeb835ae8bce29de777714dd605f41d0918b2a1 ]; then die 'A different file occupies the reserved service name; nothing overwritten.'; fi
    echo 'Upgrading the recognised v1 collector; captures remain in Download.'
  fi
  if [ -e /data/adb/service.d/.ts18-coldstart.new ] || [ -L /data/adb/service.d/.ts18-coldstart.new ]; then die 'Reserved installer staging name exists. Nothing overwritten.'; fi
  # One <=48k source file; at most <=96k for old+staged managed copies. No logs here.
  "$BB" cp "$COLLECTOR" /data/adb/service.d/.ts18-coldstart.new || die 'Copy failed.'
  if ! verify_collector /data/adb/service.d/.ts18-coldstart.new; then
    "$BB" rm -f /data/adb/service.d/.ts18-coldstart.new; die 'Installed bytes did not verify.'
  fi
  "$BB" chmod 0755 /data/adb/service.d/.ts18-coldstart.new || die 'Cannot make collector executable.'
  "$BB" mv /data/adb/service.d/.ts18-coldstart.new "$SERVICE" || die 'Atomic install failed.'
  printf 'version=2\ncollector_sha256=%s\ninstalled_bytes=%s\ngenerated_capture_bytes_under_data_adb=0\nuptime_ms=%s\n' "$EXPECTED_COLLECTOR" "$vc_size" "$(ms)" > "$BASE/INSTALL.txt"
  echo 'Installed for the next real Android boot. No reboot or app launch performed.'
}
start() {
  if is_active; then echo 'Using the already-running capture; no duplicate collector started.'; return; fi
  verify_collector "$COLLECTOR" || die 'Companion collector does not match this kit.'
  "$BB" sh "$COLLECTOR" start || die 'Could not start collector.'
  vs_until=$(( $(ms) + 4000 ))
  while ! is_active && [ "$(ms)" -lt "$vs_until" ]; do "$BB" sleep 0.2; done
  is_active || die 'Collector did not become active. Check free space, storage, lock and Magisk context.'
  echo 'Manual capture running, up to eight minutes. This is a warm/manual campaign, not a cold-boot claim.'
}
request() {
  rq_action=$1; rq_label=$2; rq_delay=$3
  is_active || return 1
  rq_time=$(ms); rq_due=$((rq_time + rq_delay))
  rq_file=$RP/requests/req-$rq_time-$$
  # Multiple controls from the same shell can share one millisecond.
  while [ -e "$rq_file" ] || [ -e "$rq_file.processed" ]; do rq_time=$((rq_time + 1)); rq_file=$RP/requests/req-$rq_time-$$; done
  [ ! -e "$rq_file" ] && [ ! -L "$rq_file" ] || return 1
  rq_tmp=$RP/requests/.pending-$rq_time-$$
  [ ! -e "$rq_tmp" ] && [ ! -L "$rq_tmp" ] || return 1
  printf '%s %s %s\n' "$rq_action" "$rq_label" "$rq_due" > "$rq_tmp" && "$BB" mv "$rq_tmp" "$rq_file" || return 1
  LAST_REQUEST=$rq_file
}
stop() {
  if is_active; then
    [ ! -L "$BASE/active/STOP" ] || die 'Unexpected stop path.'
    : > "$BASE/active/STOP"
    echo 'Stop requested. Wait for CLOSED before exporting; never reboot a running collector.'
  else echo 'No live collector from this kit.'; fi
}
uninstall() {
  verify_collector "$SERVICE" || die 'Installed file is absent/different; nothing removed.'
  "$BB" rm -f "$SERVICE" || die 'Cannot remove the verified script.'
  echo 'Boot collector removed. Captures retained. A current capture, if any, still has its own eight-minute deadline.'
}
record() {
  rr_case=$1
  while :; do
    printf '\nResult: P works acceptably | S works but slow | F fails | N not run/blocked/unknown: '
    IFS= read -r rr_answer || return 1
    case "$rr_answer" in p|P) rr_result=PASS_FUNCTIONAL; break;; s|S) rr_result=SUCCESS_SLOW; break;; f|F) rr_result=FAIL_FUNCTION; break;; n|N|'') rr_result=NOT_RUN_OR_UNKNOWN; break;; *) echo 'Press P, S, F or N, then Enter.';; esac
  done
  printf 'Optional short note (Enter skips): '
  IFS= read -r rr_note || rr_note=''
  rr_note=$(printf '%.240s' "$rr_note" | "$BB" tr '\t\r\n' '   ')
  rr_run=NO_ACTIVE_CAPTURE; run_path && rr_run=${RP##*/}
  printf '%s\t%s\t%s\t%s\t%s\n' "$(ms)" "$rr_case" "$rr_result" "$rr_run" "$rr_note" >> "$SESSION/results.tsv"
  case "$rr_result" in FAIL_*)
    echo 'Keep the failed screen unchanged; do not Retry or open the source app yet.'
    echo 'Return to that screen now for the delayed failure snapshot. No automatic app action occurs.'
    request BURST "FAIL_$rr_case" 8000 || echo 'No live capture: preserve the phone video and existing logs.';;
  esac
}
skip_case() {
  sk_run=NO_CAPTURE; run_path && sk_run=${RP##*/}
  printf '%s\t%s\tNOT_RUN_OR_UNKNOWN\t%s\t%s\n' "$(ms)" "$1" "$sk_run" "$2" >> "$SESSION/results.tsv"
}
trial() {
  tt_id=$1; tt_action=$2; tt_accept=$3
  printf '\n--- %s ---\n%s\nPass: %s\n' "$tt_id" "$tt_action" "$tt_accept"
  printf 'Enter = do this test; N = skip: '
  IFS= read -r tt_go || return 1
  case "$tt_go" in n|N) skip_case "$tt_id" operator_skipped; return;; esac
  if ! is_active; then
    echo 'No live capture. Enter starts a new MANUAL capture, N records this case unrun.'
    IFS= read -r tt_go || return 1
    case "$tt_go" in n|N) skip_case "$tt_id" collector_not_running; return;; esac
    start
  fi
  echo 'Preparing a 25-second lightweight-only timing window; wait here for Ready.'
  request QUIET "${tt_id}_QUIET" 0 || return 1
  tt_ack=$LAST_REQUEST; tt_wait=$(( $(ms) + 45000 ))
  while [ ! -f "$tt_ack.processed" ] && [ "$(ms)" -lt "$tt_wait" ] && is_active; do "$BB" sleep 0.2; done
  [ -f "$tt_ack.processed" ] || { echo 'Quiet window not acknowledged. Case recorded N; no timing PASS claimed.'; skip_case "$tt_id" quiet_window_unavailable; return; }
  request MARK "${tt_id}_ARMED" 0 || :
  # Do not start Binder dumps inside the critical first-three-second observation window.
  request BURST "${tt_id}_AFTER" 28000 || :
  echo 'Ready.'
  echo 'Return HOME now. After about 5 seconds perform the action ONCE.'
  echo 'Observe for at least 10 seconds after the action, then return here.'
  echo 'Phone video/audio provides the exact tap-to-pixels/tap-to-sound timing.'
  printf 'Press Enter when you have returned after observing: '
  IFS= read -r _tt_done || return 1
  record "$tt_id"
}
guide() {
  SESSION=$BASE/validation-$(ms)-$$
  "$BB" mkdir "$SESSION" || die 'Cannot create validation session.'
  printf 'uptime_ms\tcase\tmanual_result\tcapture\toptional_note\n' > "$SESSION/results.tsv"
  echo 'Focus: Auxio-TS Music and Organic Maps on ts-theme. Safely parked only.'
  echo 'Use a phone video with audio. Judge real pixels/audible sound. About 3 seconds is a target, not an absolute cutoff.'
  echo 'App/session/process existence is not readiness. Do not manually prime apps before the first cold observation.'
  status
  echo 'FIRST: did you film a real Android boot before opening Termux or source apps?'
  printf 'Y = filmed real reboot without priming; N/Enter = no/unknown: '
  IFS= read -r filmed || return
  boot_ok=0
  if run_path; then
    IFS= read -r boot_now < /proc/sys/kernel/random/boot_id
    "$BB" grep -qx 'origin=boot' "$RP/CONTEXT.txt" && "$BB" grep -qx "boot_id=$boot_now" "$RP/CONTEXT.txt" && boot_ok=1
  fi
  case "$filmed:$boot_ok" in
    y:1|Y:1)
      echo 'B0_MAP: measure HOME first visible -> usable compact map pixels/touch.'; record B0_MAP || return
      echo 'B0_MUSIC: measure first HOME Music Play tap -> audible Auxio, without opening Auxio first.'; record B0_MUSIC || return;;
    *) echo 'No confirmed current-boot capture + film. B0 recorded N; manual tests remain available.'
      skip_case B0_MAP no_confirmed_unprimed_boot; skip_case B0_MUSIC no_confirmed_unprimed_boot;;
  esac
  trial M1 'On HOME tap Music Play once (pause first only if already playing). Do not open Auxio or tap Play twice.' 'One tap gives audible intended music promptly (aim about 3s); no unexpected app foreground or source switch.'
  trial M2 'Tap the Music app icon on HOME once. Do this only after recording automatic cold-start results; manual opening is not a startup prerequisite.' 'Correct Auxio screen is interactive promptly (aim about 3s); only one intended app/task opens.'
  trial H1 'From Auxio press the normal HOME control once. Do not use Recents or Retry.' 'Compact map pixels/touch and HOME controls return promptly (aim about 3s); intended playback state is preserved.'
  trial NAV_OPEN 'From HOME tap the fixed Navigation rail control once.' 'The same intended Maps task becomes usable fullscreen promptly (aim about 3s).'
  trial H2 'From fullscreen Organic Maps press normal HOME once.' 'Touchable compact map returns promptly (aim about 3s) without Retry, duplicate task, or focus bounce.'
  trial MAP_BACK 'From HOME open fullscreen Maps. Close any search/sheet first; with no active route press BACK once at the root map.' 'Returns HOME promptly with the same map task compact; no Retry or fresh map launch. Active navigation keeps its existing End/Back policy.'
  trial MUSIC_BACK 'Open Auxio, close any internal panels, then use BACK to return HOME.' 'Map and transport recover promptly; existing playback intent is preserved.'
  trial H3 'Open ordinary Android Settings manually, wait 5s, then press normal HOME once.' 'Map and Music controls are immediately usable promptly (aim about 3s); no delayed foreign app appears.'
  trial M3 'After returning HOME from Settings, tap Music Play/Pause once as appropriate; listen and observe metadata.' 'One action reaches the intended audible/paused state promptly (aim about 3s); no duplicate/queued command.'
  trial R1 'Repeat Auxio -> HOME -> Maps fullscreen -> HOME three times, using normal controls only.' 'Each HOME return is usable promptly (aim about 3s); same intended navigation task; no growing delays, duplicate Activities or Retry.'
  echo "Results saved in ${SESSION##*/}. Use menu Stop, wait CLOSED, then Export."
}
export_run() {
  if is_active; then echo 'Capture still running. Choose Stop, wait CLOSED, then Export.'; return 1; fi
  run_path || die 'No capture to export.'
  if "$BB" grep -qx 'status=CLOSING' "$RP/STATUS.txt"; then echo 'Cleanup/hashing still running. Wait for CLOSED, then export.'; return 1; fi
  ex_name=${RP##*/}; ex_dest=$BASE/$ex_name.tar.gz
  if [ -L "$ex_dest" ] || [ -L "$ex_dest.partial" ]; then die 'Unexpected archive path.'; fi
  [ -f "$RP/COMPLETE.txt" ] || echo 'WARNING: incomplete/unsealed capture; archive is partial evidence, not a pass.'
  if [ -f "$RP/SHA256SUMS" ]; then
    (cd "$RP" && "$BB" sha256sum -c SHA256SUMS >/dev/null) || die 'Capture hashes changed. Retain files for investigation.'
  fi
  verify_collector "$COLLECTOR" || die 'Companion collector missing/changed; export not started.'
  ex_work=$BASE/export-$(ms)-$$
  "$BB" mkdir "$ex_work" || die 'Cannot create export work directory.'
  "$BB" setsid "$BB" sh "$COLLECTOR" --worker "$ex_work" 33554432 40 "$BB" sh "$SELF" --pack "$ex_name" </dev/null >/dev/null 2>&1 &
  ex_pid=$!; ex_wait=$(( $(ms) + 1500 ))
  while [ ! -s "$ex_work/ready" ] && [ "$(ms)" -lt "$ex_wait" ]; do "$BB" sleep 0.05; done
  [ -s "$ex_work/ready" ] || { "$BB" kill -KILL "$ex_pid" 2>/dev/null || :; die 'Archive worker identity unavailable.'; }
  read -r ex_seen ex_ticks < "$ex_work/ready"
  [ "$ex_seen" = "$ex_pid" ] || die 'Archive process identity mismatch.'
  ex_wait=$(( $(ms) + 40000 ))
  while [ ! -f "$ex_work/done" ] && [ "$(ms)" -lt "$ex_wait" ]; do "$BB" sleep 0.2; done
  ex_rc=UNKNOWN; [ ! -f "$ex_work/done" ] || read -r ex_rc < "$ex_work/done"
  # The worker remains a live proven process-group leader until killed.
  if [ -r "/proc/$ex_pid/stat" ]; then
    # shellcheck disable=SC2086 # Numeric token splitting, literal AWK or Magisk ash pipefail.
    IFS= read -r ex_stat < "/proc/$ex_pid/stat"; ex_stat=${ex_stat##*) }
    # shellcheck disable=SC2086 # Split numeric /proc fields after removing comm.
    set -- $ex_stat; shift 19
    [ "$1" = "$ex_ticks" ] || die 'Archive ownership changed; partial retained.'
    "$BB" kill -KILL "-$ex_pid" 2>/dev/null || die 'Archive cleanup unproven.'
    wait "$ex_pid" 2>/dev/null || :
  else die 'Archive worker lost; partial retained.'; fi
  [ "$ex_rc" = 0 ] || { echo 'Archive timed out/failed/capped; partial evidence retained in Download.'; return 1; }
  "$BB" timeout -s KILL 20 "$BB" gzip -t "$ex_work/output.txt" || die 'Archive CRC failed; partial retained.'
  "$BB" mv "$ex_work/output.txt" "$ex_dest" || die 'Cannot finalise archive.'
  "$BB" sha256sum "$ex_dest" > "$ex_dest.sha256"
  printf 'Send this archive, its .sha256 and the phone video:\n%s\n' "$ex_dest"
}
menu() {
  while :; do
    printf '\n1 Install boot collector\n2 Status / latest boot capture\n3 Guided Music + Maps tests\n4 Start manual capture\n5 Stop capture\n6 Export latest capture\n7 Uninstall boot collector\n8 Optional delayed failure screenshot\n0 Exit\nChoice: '
    IFS= read -r choice || exit 0
    case "$choice" in
      1) install;; 2) status;; 3) guide;; 4) start;; 5) stop;; 6) export_run;;
      7) uninstall;;
      8) request SHOT MANUAL 8000 && echo 'Return to the failed screen now. Screenshot requested after 8s, first-60s and any quiet window.' || echo 'No live collector.';;
      0) exit 0;; *) echo 'Use 0 through 8.';;
    esac
  done
}
base_ready
case "${1:-guide}" in
  --pack)
    case "$2" in run-boot-*|run-manual-*) ;; *) exit 64;; esac
    case "$2" in *[!A-Za-z0-9_-]*) exit 64;; esac
    cd "$BASE" || exit 1
    set -- "$2"
    for pack_entry in validation-* INSTALL.txt; do
      [ -e "$pack_entry" ] && [ ! -L "$pack_entry" ] && set -- "$@" "$pack_entry"
    done
    # shellcheck disable=SC3040 # Numeric token splitting, literal AWK or Magisk ash pipefail.
    set -o pipefail
    "$BB" tar -cf - "$@" | "$BB" gzip -c;;
  guide) menu;; install) install;; start) start;; status) status;; stop) stop;; export) export_run;; uninstall) uninstall;; *) help; exit 64;;
esac
