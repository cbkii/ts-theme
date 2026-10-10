#!/system/bin/sh
# Deterministic TS18 HOME native-navigation task controller.
# Root-only, bounded, fail-closed and read-only with respect to Topway state.

PATH=/system/bin:/system/xbin:/vendor/bin
export PATH
unset LD_PRELOAD LD_LIBRARY_PATH
umask 077

ROOT_DIR=/data/adb/ts18-launcher
SNAPSHOT="$ROOT_DIR/.activity.$$"
START_HELP="$ROOT_DIR/.am-help.$$"
LAUNCH_OUTPUT="$ROOT_DIR/.am-start.$$"
RECENTS="$ROOT_DIR/.recents.$$"
STACKS="$ROOT_DIR/.stacks.$$"
WINDOWS="$ROOT_DIR/.windows.$$"
read -r start_uptime _ < /proc/uptime
DEADLINE=$(( ${start_uptime%%.*} + 10 ))
PHASE=admission
VISIBLE=unknown
DRAWN=unknown
IDENTITY_SOURCE=unknown
trap 'rm -f "$SNAPSHOT" "$START_HELP" "$LAUNCH_OUTPUT" "$RECENTS" "$STACKS" "$WINDOWS"' EXIT HUP INT TERM

TASK_SNAPSHOT_AWK='
function digits_after_hash(line, value) {
  value=line
  sub(/^.*#/, "", value)
  sub(/[^0-9].*$/, "", value)
  return value
}
function mode_of(line, value) {
  value=line
  if (value ~ /(mWindowingMode|windowingMode)[[:space:]]*=[[:space:]]*[0-9]+/) {
    sub(/^.*(mWindowingMode|windowingMode)[[:space:]]*=[[:space:]]*/, "", value)
    sub(/[^0-9].*$/, "", value)
    return value
  }
  if (line ~ /mode=freeform([[:space:]]|$)/) return "5"
  if (line ~ /mode=fullscreen([[:space:]]|$)/) return "1"
  if (line ~ /mode=pinned([[:space:]]|$)/) return "2"
  return ""
}
function bounds_of(line, value) {
  value=line
  sub(/^.*mBounds=Rect\(/, "", value)
  sub(/\).*$/, "", value)
  gsub(/, /, ",", value)
  gsub(/ - /, ",", value)
  return value
}
function normalize_component(value, slash, owner, class_name) {
  slash=index(value, "/")
  if (slash < 2) return value
  owner=substr(value, 1, slash - 1)
  class_name=substr(value, slash + 1)
  if (substr(class_name, 1, 1) == ".") return owner "/" owner class_name
  return value
}
function activity_record_component(line, value) {
  value=line
  sub(/^.*ActivityRecord\{[^[:space:]]*[[:space:]]+u[0-9][0-9]*[[:space:]]+/, "", value)
  sub(/[[:space:]}].*$/, "", value)
  if (index(value, "/") < 2) return ""
  return normalize_component(value)
}
function finish() {
  if (!matched) return
  count++
  if (count == 1) {
    out_task=task
    out_stack=task_stack
    out_display=task_display
    out_mode=task_mode
    out_bounds=task_bounds
    out_component=task_component
    out_pip=task_pip
  }
  matched=0
  hist0=0
}
/^[[:space:]]*Display #[0-9]+/ {
  finish()
  current_display=digits_after_hash($0)
  current_stack=""
  current_mode=""
  current_stack_bounds="unknown"
  in_task=0
  next
}
/^[[:space:]]*(Stack|RootTask) #[0-9]+/ {
  finish()
  current_stack=digits_after_hash($0)
  current_mode=mode_of($0)
  current_stack_bounds="unknown"
  in_task=0
  next
}
!in_task && /mBounds=Rect\(/ {
  current_stack_bounds=bounds_of($0)
  next
}
/^[[:space:]]*Task id #[0-9]+/ {
  finish()
  task=digits_after_hash($0)
  pending_bounds="unknown"
  in_task=1
  hist0=0
  next
}
in_task && !matched && /mBounds=Rect\(/ {
  pending_bounds=bounds_of($0)
  next
}
in_task && !matched && /^[[:space:]]*\* TaskRecord\{/ && (index($0, " A=" pkg " ") || index($0, " A=" pkg "}")) && index($0, " U=" target_user " ") {
  record_task=digits_after_hash($0)
  if (record_task == "" || (hint != "0" && record_task != hint)) next
  task=record_task
  matched=1
  hist0=0
  task_stack=current_stack
  task_display=current_display
  task_mode=current_mode
  task_bounds=(pending_bounds != "unknown" ? pending_bounds : current_stack_bounds)
  task_component="unknown"
  task_pip="unknown"
  line=$0
  if (line ~ /StackId=[0-9]+/) {
    sub(/^.*StackId=/, "", line)
    sub(/[^0-9].*$/, "", line)
    task_stack=line
  }
  explicit_mode=mode_of($0)
  if (explicit_mode != "") task_mode=explicit_mode
  next
}
matched && /^[[:space:]]*\*?[[:space:]]*Hist #0:/ {
  hist0=1
  component=activity_record_component($0)
  if (component != "") task_component=component
  next
}
matched && hist0 && /mActivityComponent=/ && task_component == "unknown" {
  line=$0
  sub(/^.*mActivityComponent=/, "", line)
  sub(/[[:space:]].*$/, "", line)
  task_component=normalize_component(line)
  next
}
matched && /(mSupportsPictureInPicture|supportsPictureInPicture)=/ {
  if ($0 ~ /(mSupportsPictureInPicture|supportsPictureInPicture)=true/) task_pip="1"
  else if ($0 ~ /(mSupportsPictureInPicture|supportsPictureInPicture)=false/) task_pip="0"
  next
}
END {
  finish()
  if (count == 0) {
    print "NONE"
    exit
  }
  if (count > 1 && hint == "0") {
    print "AMBIGUOUS " count
    exit
  }
  if (out_stack == "") out_stack="unknown"
  if (out_display == "") out_display="unknown"
  if (out_mode == "") out_mode="unknown"
  if (out_bounds == "") out_bounds="unknown"
  print "FOUND " out_task " " out_stack " " out_display " " out_mode " " out_bounds " " out_component " " out_pip
}'

FOREGROUND_TASK_AWK='
function task_from_record(line, value) {
  value=line
  if (index(value, "ActivityRecord{") < 1 || index(value, " t") < 1) return ""
  sub(/^.* t/, "", value)
  sub(/[^0-9].*$/, "", value)
  return value
}
/mResumedActivity: ActivityRecord\{|topResumedActivity=ActivityRecord\{|^[[:space:]]*ResumedActivity:[[:space:]]*ActivityRecord\{/ {
  task=task_from_record($0)
  if (task != "") {
    if ($0 ~ /topResumedActivity=/) top=task
    else if ($0 ~ /^[[:space:]]*ResumedActivity:/) global=task
    else if (fallback == "") fallback=task
  }
}
END {
  if (top != "") print top
  else if (global != "") print global
  else if (fallback != "") print fallback
}'

PKG=unknown
ANDROID_USER=unknown
TASK_ID=unknown
STACK_ID=unknown
DISPLAY_ID=unknown
WINDOWING_MODE=unknown
TASK_BOUNDS=unknown
TASK_COMPONENT=unknown
SUPPORTS_PIP=unknown
LAUNCHED=0
TRANSACTION=0
HELP_EXIT=not-run
HELP_WINDOWING_MODE=0
HELP_DISPLAY=0
LAUNCH_EXIT=not-run

# Per-producer timeout plus one monotonic operation budget. Reconciliation starts a
# new bounded observation, never blindly replays a previously accepted cold launch.
check_deadline() {
  read -r current_uptime _ < /proc/uptime
  [ "${current_uptime%%.*}" -lt "$DEADLINE" ] || fail PHASE_TIMEOUT
}
bounded() {
  check_deadline
  /system/bin/toybox timeout -k 1 2 "$@"
}

log_event() {
  if command -v log >/dev/null 2>&1; then
    log -t TS18Nav "$*" 2>/dev/null || true
  fi
}

valid_package() {
  case "$1" in
    ''|*[!A-Za-z0-9._]*) return 1 ;;
    *.*) return 0 ;;
    *) return 1 ;;
  esac
}

valid_component() {
  owner="${1%%/*}"
  class_name="${1#*/}"
  [ "$owner" != "$1" ] || return 1
  valid_package "$owner" || return 1
  case "$class_name" in
    ''|*[!A-Za-z0-9_.$]*) return 1 ;;
    *) return 0 ;;
  esac
}

valid_uint() {
  case "$1" in
    ''|*[!0-9]*) return 1 ;;
    *) return 0 ;;
  esac
}

validate_bounds() {
  left="$1"
  top="$2"
  right="$3"
  bottom="$4"
  valid_uint "$left" && valid_uint "$top" && valid_uint "$right" && valid_uint "$bottom" || return 1
  [ "$right" -gt "$left" ] && [ "$bottom" -gt "$top" ] || return 1
  [ "$right" -le 10000 ] && [ "$bottom" -le 10000 ]
}

sanitize_value() {
  printf '%s' "$1" | tr -d '\r\n\t ' | cut -c1-160
}

read_file_value() {
  path="$1"
  if [ -r "$path" ]; then
    sanitize_value "$(cat "$path" 2>/dev/null)"
  else
    printf unreadable
  fi
}

vendor_state_fields() {
  printf 'forcepip=%s runtime_forcepip=%s forcepip_x=%s forcepip_y=%s forcepip_w=%s forcepip_h=%s df_desktop=%s df_theme_window=%s customPipApp=%s naviName=%s' \
    "$(sanitize_value "$(getprop persist.tw.forcepip 2>/dev/null)")" \
    "$(sanitize_value "$(getprop sys.tw.forcepip 2>/dev/null)")" \
    "$(sanitize_value "$(getprop sys.tw.forcepip.x 2>/dev/null)")" \
    "$(sanitize_value "$(getprop sys.tw.forcepip.y 2>/dev/null)")" \
    "$(sanitize_value "$(getprop sys.tw.forcepip.w 2>/dev/null)")" \
    "$(sanitize_value "$(getprop sys.tw.forcepip.h 2>/dev/null)")" \
    "$(sanitize_value "$(getprop sys.df.desktop 2>/dev/null)")" \
    "$(sanitize_value "$(getprop sys.df.variety.theme.window 2>/dev/null)")" \
    "$(read_file_value /data/tw/custom_pip_app_name)" \
    "$(read_file_value /data/tw/navi_name)"
}

emit_protocol() {
  outcome="$1"
  code="$2"
  printf '%s code=%s user=%s task=%s stack=%s package=%s component=%s display=%s windowingMode=%s bounds=%s supportsPip=%s launched=%s transaction=%s helpExit=%s helpWindowingMode=%s helpDisplay=%s launchExit=%s phase=%s visible=%s drawn=%s observation=%s ' \
    "$outcome" "$code" "$ANDROID_USER" "$TASK_ID" "$STACK_ID" "$PKG" "$TASK_COMPONENT" "$DISPLAY_ID" \
    "$WINDOWING_MODE" "$TASK_BOUNDS" "$SUPPORTS_PIP" "$LAUNCHED" "$TRANSACTION" \
    "$HELP_EXIT" "$HELP_WINDOWING_MODE" "$HELP_DISPLAY" "$LAUNCH_EXIT" "$PHASE" "$VISIBLE" "$DRAWN" "$IDENTITY_SOURCE"
  vendor_state_fields
  printf '\n'
}

fail() {
  code="$1"
  shift
  if [ -s "$LAUNCH_OUTPUT" ]; then
    printf 'command_output_begin\n'
    # Leave room for the final protocol within the Java reader's 48-line cap.
    head -c 4096 "$LAUNCH_OUTPUT" | head -n 20
    printf '\ncommand_output_end\n'
    log_event "command failure code=$code output=$(head -c 1024 "$LAUNCH_OUTPUT" | tr '\n' ' ')"
  fi
  log_event "FAIL $code $*"
  emit_protocol FAIL "$code"
  exit 1
}

capture_activity() {
  bounded dumpsys activity activities >"$SNAPSHOT" 2>/dev/null || return 1
  [ -s "$SNAPSHOT" ]
}

parse_task_snapshot() {
  pkg="$1"
  hint="$2"
  awk -v pkg="$pkg" -v hint="$hint" -v target_user="$ANDROID_USER" "$TASK_SNAPSHOT_AWK" "$SNAPSHOT"
}

parse_foreground_task_snapshot() {
  awk "$FOREGROUND_TASK_AWK" "$SNAPSHOT"
}

# Independent structured API surface, loaded from the active launcher APK. No transaction IDs.
run_bridge() {
  check_deadline
  bridge_action="$1"
  shift
  command -v app_process >/dev/null 2>&1 || return 3
  bridge_apk="$(pm path --user "$ANDROID_USER" com.cbkii.ts18launcher 2>/dev/null | head -n 1)"
  bridge_apk="${bridge_apk#package:}"
  [ -r "$bridge_apk" ] || return 3
  CLASSPATH="$bridge_apk" /system/bin/toybox timeout -k 1 3 app_process /system/bin \
    com.cbkii.ts18launcher.NavTaskBridge "$bridge_action" "$ANDROID_USER" "$@"
}

# A rich dump miss is never task death by itself. Contrary package evidence on any surface
# conservatively prevents a cold launch; unavailable/malformed producers remain UNKNOWN.
corroborate_absence() {
  absence_pkg="$1"
  bounded dumpsys activity recents >"$RECENTS" 2>&1 || return 3
  grep -q 'ACTIVITY MANAGER RECENT TASKS' "$RECENTS" || return 3
  grep -F "$absence_pkg" "$RECENTS" >/dev/null && return 3
  bounded am stack list >"$STACKS" 2>&1 || return 3
  grep -q 'Stack id=' "$STACKS" || return 3
  grep -F "$absence_pkg" "$STACKS" >/dev/null && return 3
  # A process may own a service without any Activity task. A complete structured
  # ATM query plus clean Recents/stack misses proves task absence even then.
  # When ATM is unavailable, retain the conservative no-process fallback.
  [ "$bridge_rc" = 0 ] && [ "$bridge_record" = NONE ] && return 1
  command -v pidof >/dev/null 2>&1 || return 3
  pidof "$absence_pkg" >/dev/null 2>&1
  process_rc=$?
  [ "$process_rc" = 1 ] || return 3
  return 1
}

parse_task_record() {
  record="$1"
  kind=""; value1=""; value2=""; value3=""; value4=""; value5=""; value6=""; value7=""
  read -r kind value1 value2 value3 value4 value5 value6 value7 <<EOF_RECORD
$record
EOF_RECORD
  case "$kind" in
    NONE) return 1 ;;
    AMBIGUOUS) TASK_COUNT="${value1:-unknown}"; return 2 ;;
    FOUND)
      TASK_ID="$value1"; STACK_ID="${value2:-unknown}"; DISPLAY_ID="${value3:-unknown}"
      WINDOWING_MODE="${value4:-unknown}"; TASK_BOUNDS="${value5:-unknown}"
      TASK_COMPONENT="${value6:-unknown}"; SUPPORTS_PIP="${value7:-unknown}"
      valid_uint "$TASK_ID" || return 3
      return 0 ;;
    *) return 3 ;;
  esac
}

read_task_once() {
  observed_pkg="$1"
  observed_hint="$2"
  if capture_activity; then
    record="$(parse_task_snapshot "$observed_pkg" "$observed_hint")"
    parse_task_record "$record"
    observed_rc=$?
    case "$observed_rc" in 0|2) IDENTITY_SOURCE=activity; return "$observed_rc" ;; esac
  fi
  # Preserve bounded parser-miss context locally for the next collector.
  if [ -s "$SNAPSHOT" ]; then
    head -c 65536 "$SNAPSHOT" >"$ROOT_DIR/task-miss-latest.txt"
  fi
  bridge_record="$(run_bridge status "$observed_pkg" "$observed_hint" 2>/dev/null)"
  bridge_rc=$?
  if [ "$bridge_rc" = 0 ]; then
    parse_task_record "$bridge_record"
    observed_rc=$?
    [ "$observed_rc" = 0 ] && { IDENTITY_SOURCE=ATM; log_event "task resolver recovered package=$observed_pkg task=$TASK_ID via=ATM"; return 0; }
    [ "$observed_rc" = 2 ] && return 2
  fi
  case "$bridge_record" in *TASK_AMBIGUOUS*) return 2 ;; esac
  corroborate_absence "$observed_pkg"
}

read_task() {
  pkg="$1"
  hint="$2"
  tries="${3:-1}"
  n=0
  rc=1
  while [ "$n" -lt "$tries" ]; do
    check_deadline
    read_task_once "$pkg" "$hint"
    rc=$?
    case "$rc" in
      0|2) return "$rc" ;;
    esac
    n=$((n + 1))
    if [ "$n" -lt "$tries" ]; then sleep 0.1; fi
  done
  return "$rc"
}

validate_observed_component() {
  case "$TASK_COMPONENT" in
    unknown|'') return 0 ;;
    "${1:-$PKG}"/*) return 0 ;;
    *) fail COMPONENT_MISMATCH ;;
  esac
}

require_task() {
  pkg="$1"
  hint="$2"
  tries="${3:-1}"
  read_task "$pkg" "$hint" "$tries"
  rc=$?
  case "$rc" in
    0) ;;
    1) fail TASK_NOT_FOUND ;;
    2) fail TASK_AMBIGUOUS "count=${TASK_COUNT:-unknown}" ;;
    *) fail TASK_OBSERVATION_UNCERTAIN ;;
  esac
  validate_observed_component "$pkg"
}

wait_state() {
  pkg="$1"
  task="$2"
  mode="$3"
  expected_bounds="$4"
  tries=0
  while [ "$tries" -lt 30 ]; do
    check_deadline
    if read_task_once "$pkg" "$task"; then
      validate_observed_component "$pkg"
      if [ "$DISPLAY_ID" = 0 ] && [ "$WINDOWING_MODE" = "$mode" ]; then
        if [ "$expected_bounds" = any ] || [ "$TASK_BOUNDS" = "$expected_bounds" ]; then
          return 0
        fi
      fi
    fi
    tries=$((tries + 1))
    sleep 0.1
  done
  return 1
}

wait_foreground_task() {
  expected_task="$1"
  tries=0
  while [ "$tries" -lt 30 ]; do
    check_deadline
    capture_activity || return 1
    focused_task="$(parse_foreground_task_snapshot)"
    if [ "$focused_task" = "$expected_task" ]; then
      return 0
    fi
    tries=$((tries + 1))
    sleep 0.1
  done
  return 1
}

require_foreground_task() {
  expected_task="$1"
  code="$2"
  wait_foreground_task "$expected_task" || fail "$code"
}

require_handoff_foreground() {
  # Never restore HOME over an unrelated app selected during a transaction.
  capture_activity || fail TASK_STATE_UNREADABLE
  foreground_task="$(parse_foreground_task_snapshot)"
  [ "$foreground_task" = "$1" ] || [ "$foreground_task" = "$2" ] || fail FOREGROUND_CHANGED
}

require_home_presentation() {
  allowed_navigation="$1"
  capture_activity || fail TASK_OBSERVATION_UNCERTAIN
  home_identity="$(parse_task_snapshot com.cbkii.ts18launcher 0)"
  read -r home_kind home_identity_task _ <<EOF_HOME
$home_identity
EOF_HOME
  [ "$home_kind" = FOUND ] || fail FOREGROUND_CHANGED
  presentation_foreground="$(parse_foreground_task_snapshot)"
  [ "$presentation_foreground" = "$home_identity_task" ] && return 0
  [ "$allowed_navigation" -gt 0 ] && [ "$presentation_foreground" = "$allowed_navigation" ] && return 0
  fail FOREGROUND_CHANGED
}

verify_state() {
  expected_mode="$1"
  expected_bounds="$2"
  [ "$DISPLAY_ID" = 0 ] || fail DISPLAY_MISMATCH
  [ "$WINDOWING_MODE" = "$expected_mode" ] || fail WINDOWING_MODE_MISMATCH
  if [ "$expected_bounds" != any ]; then
    [ "$TASK_BOUNDS" = "$expected_bounds" ] || fail BOUNDS_MISMATCH
  fi
}

native_launch_supported() {
  bounded am help >"$START_HELP" 2>&1
  HELP_EXIT=$?
  HELP_WINDOWING_MODE=0
  HELP_DISPLAY=0
  if grep -q -- '--windowingMode' "$START_HELP"; then HELP_WINDOWING_MODE=1; fi
  if grep -q -- '--display' "$START_HELP"; then HELP_DISPLAY=1; fi
  [ "$HELP_WINDOWING_MODE" = 1 ] && [ "$HELP_DISPLAY" = 1 ]
}

launch_freeform_once() {
  component="$1"
  valid_component "$component" || fail BAD_COMPONENT
  case "$component" in
    "$PKG"/*) ;;
    *) fail COMPONENT_PACKAGE_MISMATCH ;;
  esac
  PHASE=launch
  native_launch_supported || fail FREEFORM_LAUNCH_UNSUPPORTED
  claim_cold_launch
  bounded am start --user "$ANDROID_USER" --display 0 --windowingMode 5 \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    -f 0x10000000 -n "$component" >"$LAUNCH_OUTPUT" 2>&1
  LAUNCH_EXIT=$?
  check_cold_launch_result FREEFORM_LAUNCH_FAILED FREEFORM_LAUNCH_UNSUPPORTED
  LAUNCHED=1
  PHASE=bootstrap
  log_event "cold launch transaction=$TRANSACTION package=$PKG component=$component display=0 mode=5"
}

launch_claim_path() {
  current_boot=$(cat /proc/sys/kernel/random/boot_id) || return 1
  case "$current_boot" in ''|*[!a-f0-9-]*) return 1 ;; esac
  launch_marker="$ROOT_DIR/launch-$ANDROID_USER-$PKG-$current_boot"
}

claim_cold_launch() {
  launch_claim_path || fail LAUNCH_MARKER_FAILED
  # Atomic across every cold-launch path and helper process. Time alone cannot
  # resolve an accepted/uncertain dispatch, including an explicit fullscreen one.
  mkdir "$launch_marker" 2>/dev/null || fail LAUNCH_PENDING
}

check_cold_launch_result() {
  failure_code="$1"
  unsupported_code="$2"
  if grep -q -i -E 'Unknown option.*(--windowingMode|--display)' "$LAUNCH_OUTPUT"; then
    rmdir "$launch_marker" 2>/dev/null || true
    fail "$unsupported_code"
  fi
  if [ "$LAUNCH_EXIT" -ne 0 ] || grep -Eq '^(Error:|Error type|Exception|Security exception:)' "$LAUNCH_OUTPUT"; then
    # Timeout/signal or an unclassified exception can occur after acceptance.
    # Retain that claim. Only an explicit pre-dispatch rejection may retry;
    # a nonzero producer exit alone does not prove server-side cancellation.
    if grep -Eq '^(Error type|Error: Activity not started|Error: [Uu]nable to resolve Intent|Security exception:|Permission Denial:)' "$LAUNCH_OUTPUT"; then
      rmdir "$launch_marker" 2>/dev/null || true
    fi
    fail "$failure_code"
  fi
}

resolve_launch_claim_if_ready() {
  [ "$DISPLAY_ID" = 0 ] || return 0
  [ "$TASK_COMPONENT" != unknown ] || return 0
  case "$TASK_COMPONENT" in "$PKG"/*) ;; *) return 0 ;; esac
  case "$PKG:$TASK_COMPONENT" in
    app.organicmaps.incar:app.organicmaps.incar/app.organicmaps.MwmActivity) ;;
    app.organicmaps.incar:*) return 0 ;;
  esac
  # Cleanup is optional on a proven warm task; metadata refusal must not
  # invalidate an already verified fullscreen/windowed transition.
  launch_claim_path || return 0
  rmdir "$launch_marker" 2>/dev/null || true
}

require_bootstrap_ready() {
  [ "$DISPLAY_ID" = 0 ] || fail DISPLAY_MISMATCH
  [ "$TASK_COMPONENT" != unknown ] || fail COMPONENT_UNKNOWN
  case "$PKG:$TASK_COMPONENT" in
    app.organicmaps.incar:app.organicmaps.incar/app.organicmaps.MwmActivity) ;;
    app.organicmaps.incar:*) PHASE=bootstrap; fail BOOTSTRAP_PENDING ;;
  esac
  # Every supported package resolves its accepted-launch claim on validated task
  # identity; Organic Maps must additionally have advanced past bootstrap.
  resolve_launch_claim_if_ready
}

observe_window() {
  VISIBLE=unknown; DRAWN=unknown
  bounded dumpsys window windows > "$WINDOWS" 2>/dev/null || return 0
  window_record=$(awk -v component="$TASK_COMPONENT" -v user="$ANDROID_USER" -v stack="$STACK_ID" '
    function finish() {
      if (matched && valid_stack && display && surface == 1 && onscreen == 1 && visible == 1) {
        positive++; if (drawn == 1) drawn_positive++
      }
    }
    /^[[:space:]]*Window #[0-9]+ Window\{/ {
      finish(); matched=(index($0, " u" user " ") && index($0, component "}"));
      valid_stack=0; display=0; surface=0; onscreen=0; visible=0; drawn=0
    }
    matched && /mDisplayId=0([[:space:]]|$)/ {display=1}
    matched && $0 ~ ("stackId=" stack "([[:space:]]|$)") {valid_stack=1}
    matched && /mHasSurface=true/ {surface=1}
    matched && /isOnScreen=true/ {onscreen=1}
    matched && /isVisible=true/ {visible=1}
    matched && /Surface: shown=true|mDrawState=HAS_DRAWN/ {drawn=1}
    END {finish(); if (positive == 1) print "1 " (drawn_positive == 1 ? "1" : "unknown"); else print "unknown unknown"}
  ' "$WINDOWS")
  read -r VISIBLE DRAWN <<EOF_WINDOW
$window_record
EOF_WINDOW
  [ -n "$VISIBLE" ] || VISIBLE=unknown
  [ -n "$DRAWN" ] || DRAWN=unknown
}

move_task_fullscreen() {
  pkg="$1"
  task="$2"
  require_task "$pkg" "$task" 1
  wanted_task="$TASK_ID"
  component="$TASK_COMPONENT"
  if [ "$WINDOWING_MODE" != 1 ]; then
    [ "$component" != unknown ] || fail COMPONENT_UNKNOWN
    if ! run_bridge mode "$pkg" "$wanted_task" 1 1 >"$LAUNCH_OUTPUT" 2>&1; then
      # Never redeliver an Activity over a legitimate foreign permission/settings flow.
      if grep -q -E 'LEGITIMATE_FOREIGN_ACTIVITY|TASK_STACK_NOT_EXCLUSIVE|MODE_NOT_VERIFIED|TOP_ACTIVITY_UNOBSERVED' "$LAUNCH_OUTPUT"; then
        fail FULLSCREEN_POLICY_BLOCKED
      fi
      # Explicit-user fallback only: the already-proven exact-task transition.
      bounded am start --user "$ANDROID_USER" --display 0 --windowingMode 1 --task "$wanted_task" \
        -f 0x20000000 -n "$component" >"$LAUNCH_OUTPUT" 2>&1 || fail FULLSCREEN_FAILED
    fi
  else
    PHASE=focus
    bounded am task focus "$wanted_task" >"$LAUNCH_OUTPUT" 2>&1 || fail FOCUS_FAILED
  fi
  wait_state "$pkg" "$wanted_task" 1 any || fail FULLSCREEN_REJECTED
  require_foreground_task "$wanted_task" FULLSCREEN_NOT_FOREGROUND
  require_task "$pkg" "$wanted_task" 1
}

[ "$(id -u 2>/dev/null)" = 0 ] || fail ROOT_REQUIRED
for required in am dumpsys awk getprop grep tr cut cat head; do
  command -v "$required" >/dev/null 2>&1 || fail "${required}_MISSING"
done

action="${1:-}"
ANDROID_USER="${2:-}"
valid_uint "$ANDROID_USER" || fail BAD_ANDROID_USER
case "$action" in
  probe)
    native_launch=0
    if native_launch_supported; then native_launch=1; fi
    bridge_query=unknown
    probe_pkg="${3:-}"
    if valid_package "$probe_pkg"; then
      probe_record=$(run_bridge status "$probe_pkg" 0 2>/dev/null)
      probe_rc=$?
      case "$probe_record" in
        NONE|FOUND\ *) [ "$probe_rc" = 0 ] && bridge_query=1 ;;
        UNKNOWN\ *) bridge_query=0 ;;
      esac
    fi
    printf 'OK code=READY uid=0 bridgeQuery=%s modeMutation=not-run nativeLaunch=%s helpExit=%s helpWindowingMode=%s helpDisplay=%s '  \
      "$bridge_query" "$native_launch" "$HELP_EXIT" "$HELP_WINDOWING_MODE" "$HELP_DISPLAY"
    vendor_state_fields
    printf '\n'
    ;;

  status)
    PKG="${3:-}"
    hint="${4:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    require_task "$PKG" "$hint" 1
    emit_protocol OK STATUS
    ;;

  present-native)
    PKG="${3:-}"
    launch_component="${4:-}"
    left="${5:-}"
    top="${6:-}"
    right="${7:-}"
    bottom="${8:-}"
    hint="${9:-0}"
    TRANSACTION="${10:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_component "$launch_component" || fail BAD_COMPONENT
    valid_uint "$hint" || fail BAD_TASK
    valid_uint "$TRANSACTION" || fail BAD_TRANSACTION
    validate_bounds "$left" "$top" "$right" "$bottom" || fail BAD_BOUNDS
    expected="$left,$top,$right,$bottom"

    if [ "$hint" -gt 0 ]; then
      require_task "$PKG" "$hint" 1
    else
      read_task_once "$PKG" 0
      rc=$?
      case "$rc" in
        0) validate_observed_component ;;
        1)
          require_home_presentation 0
          launch_freeform_once "$launch_component"
          PHASE=bootstrap
          require_task "$PKG" 0 1
          ;;
        2) fail TASK_AMBIGUOUS "count=${TASK_COUNT:-unknown}" ;;
        *) fail TASK_OBSERVATION_UNCERTAIN ;;
      esac
    fi

    wanted_task="$TASK_ID"
    require_bootstrap_ready
    PHASE=mode
    [ "$DISPLAY_ID" = 0 ] || fail DISPLAY_MISMATCH
    [ "$TASK_COMPONENT" != unknown ] || fail COMPONENT_UNKNOWN
    # Android Q may reject resizeTask on a fullscreen configuration even when
    # resizeable=2. Establish mode 5 on this exact task before applying bounds.
    if [ "$WINDOWING_MODE" != 5 ]; then
      require_home_presentation 0
      [ "$TASK_COMPONENT" != unknown ] || fail COMPONENT_UNKNOWN
      bounded am start --user "$ANDROID_USER" --display 0 --windowingMode 5 --task "$wanted_task" \
        -f 0x20000000 -n "$TASK_COMPONENT" >"$LAUNCH_OUTPUT" 2>&1 || fail FREEFORM_TRANSITION_FAILED
      wait_state "$PKG" "$wanted_task" 5 any || fail FREEFORM_TRANSITION_REJECTED
    fi
    require_home_presentation "$wanted_task"
    PHASE=resize
    bounded am task resizeable "$wanted_task" 2 >"$LAUNCH_OUTPUT" 2>&1 || fail RESIZEABLE_FAILED
    bounded am task resize "$wanted_task" "$left" "$top" "$right" "$bottom" \
      >"$LAUNCH_OUTPUT" 2>&1 || fail RESIZE_FAILED
    if ! wait_state "$PKG" "$wanted_task" 5 "$expected"; then
      require_task "$PKG" "$wanted_task" 1
      verify_state 5 "$expected"
    fi
    require_task "$PKG" "$wanted_task" 1
    verify_state 5 "$expected"
    require_home_presentation "$wanted_task"
    PHASE=focus
    bounded am task focus "$wanted_task" >"$LAUNCH_OUTPUT" 2>&1 || fail FOCUS_FAILED
    require_foreground_task "$wanted_task" NATIVE_NOT_FOREGROUND
    require_task "$PKG" "$wanted_task" 1
    verify_state 5 "$expected"
    PHASE=visibility
    observe_window
    emit_protocol OK PRESENTED_NATIVE
    log_event "OK native transaction=$TRANSACTION package=$PKG task=$TASK_ID display=$DISPLAY_ID mode=$WINDOWING_MODE bounds=$TASK_BOUNDS launched=$LAUNCHED component=$TASK_COMPONENT"
    ;;

  verify-native)
    PKG="${3:-}"
    left="${4:-}"
    top="${5:-}"
    right="${6:-}"
    bottom="${7:-}"
    hint="${8:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    [ "$hint" -gt 0 ] || fail TASK_AUTHORITY_REQUIRED
    validate_bounds "$left" "$top" "$right" "$bottom" || fail BAD_BOUNDS
    require_task "$PKG" "$hint" 1
    verify_state 5 "$left,$top,$right,$bottom"
    PHASE=visibility
    observe_window
    emit_protocol OK VERIFIED_NATIVE
    ;;

  resume-windowed)
    PKG="${3:-}"
    left="${4:-}"
    top="${5:-}"
    right="${6:-}"
    bottom="${7:-}"
    hint="${8:-0}"
    home_pkg="${9:-}"
    home_task="${10:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_package "$home_pkg" || fail BAD_HOME_PACKAGE
    valid_uint "$hint" || fail TASK_AUTHORITY_REQUIRED
    [ "$hint" -gt 0 ] || fail TASK_AUTHORITY_REQUIRED
    valid_uint "$home_task" || fail HOME_TASK_AUTHORITY_REQUIRED
    [ "$home_task" -gt 0 ] || fail HOME_TASK_AUTHORITY_REQUIRED
    validate_bounds "$left" "$top" "$right" "$bottom" || fail BAD_BOUNDS
    expected="$left,$top,$right,$bottom"
    require_task "$PKG" "$hint" 1
    verify_state 5 "$expected"
    require_bootstrap_ready
    # The navigation/Home/foreground check consumes the SAME pre-operation dump.
    # Do not multiply snapshots while the server is reparenting/stopping Activities.
    foreground_task="$(parse_foreground_task_snapshot)"
    [ "$foreground_task" = "$home_task" ] || [ "$foreground_task" = "$hint" ] || fail FOREGROUND_CHANGED
    home_record="$(parse_task_snapshot "$home_pkg" "$home_task")"
    parse_task_record "$home_record" || fail TASK_OBSERVATION_UNCERTAIN
    validate_observed_component "$home_pkg"
    [ "$DISPLAY_ID" = 0 ] || fail HOME_DISPLAY_MISMATCH
    [ "$WINDOWING_MODE" = 1 ] || fail HOME_MODE_MISMATCH
    bounded am task focus "$hint" >"$LAUNCH_OUTPUT" 2>&1 || fail FOCUS_FAILED
    require_foreground_task "$hint" NATIVE_NOT_FOREGROUND
    require_task "$PKG" "$hint" 1
    verify_state 5 "$expected"
    TRANSACTION=1
    PHASE=visibility
    observe_window
    emit_protocol OK RESUMED_NATIVE
    ;;

  fullscreen)
    PKG="${3:-}"
    hint="${4:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    cold_component="${5:-}"
    read_task_once "$PKG" "$hint"
    resolve_rc=$?
    case "$resolve_rc" in
      0) hint="$TASK_ID" ;;
      1)
        # Read-only resolution has proved absence across recents, stacks and process.
        valid_component "$cold_component" || fail COMPONENT_UNKNOWN
        case "$cold_component" in "$PKG"/*) ;; *) fail COMPONENT_PACKAGE_MISMATCH ;; esac
        PHASE=launch
        native_launch_supported || fail FULLSCREEN_LAUNCH_UNSUPPORTED
        claim_cold_launch
        bounded am start --user "$ANDROID_USER" --display 0 --windowingMode 1 \
          -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
          -f 0x10000000 -n "$cold_component" >"$LAUNCH_OUTPUT" 2>&1
        LAUNCH_EXIT=$?
        check_cold_launch_result FULLSCREEN_FAILED FULLSCREEN_LAUNCH_UNSUPPORTED
        LAUNCHED=1
        PHASE=bootstrap
        require_task "$PKG" 0 30
        hint="$TASK_ID"
        ;;
      2) fail TASK_AMBIGUOUS ;;
      *) fail TASK_OBSERVATION_UNCERTAIN ;;
    esac
    move_task_fullscreen "$PKG" "$hint"
    resolve_launch_claim_if_ready
    emit_protocol OK FULLSCREEN
    ;;

  background-fullscreen|probe-task-mode)
    PKG="${3:-}"
    hint="${4:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    require_task "$PKG" "$hint" 1
    wanted_task="$TASK_ID"
    [ "$DISPLAY_ID" = 0 ] || fail DISPLAY_MISMATCH
    if [ "$action" = background-fullscreen ]; then
      home_pkg="${5:-}"
      home_task="${6:-0}"
      external_only="${7:-0}"
      valid_package "$home_pkg" || fail BAD_HOME_PACKAGE
      valid_uint "$home_task" || fail BAD_HOME_TASK
      # Same raw snapshot as task lookup; a HOME return cancels stale departure work.
      foreground_task="$(parse_foreground_task_snapshot)"
      valid_uint "$foreground_task" || fail FOREGROUND_CHANGED
      [ "$foreground_task" != "$home_task" ] || fail FOREGROUND_CHANGED
      if [ "$external_only" = 1 ] && [ "$foreground_task" = "$wanted_task" ]; then
        fail FOREGROUND_CHANGED
      fi
    fi
    if [ "$action" = probe-task-mode ]; then
      run_bridge probe-mode "$PKG" "$wanted_task" "$WINDOWING_MODE" 0 >"$LAUNCH_OUTPUT" 2>&1 || fail BACKGROUND_MODE_UNSUPPORTED
      emit_protocol OK MODE_CAPABILITY
    else
      if [ "$WINDOWING_MODE" != 1 ]; then
        # There is deliberately no Activity/focus fallback when HOME leaves.
        run_bridge mode "$PKG" "$wanted_task" 1 0 >"$LAUNCH_OUTPUT" 2>&1 || fail BACKGROUND_MODE_UNSUPPORTED
      fi
      wait_state "$PKG" "$wanted_task" 1 any || fail FULLSCREEN_REJECTED
      emit_protocol OK BACKGROUND_FULLSCREEN
    fi
    ;;

  park-windowed)
    PKG="${3:-}"
    hint="${4:-0}"
    HOME_PKG="${5:-}"
    home_task="${6:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    valid_package "$HOME_PKG" || fail BAD_HOME_PACKAGE
    valid_uint "$home_task" || fail BAD_HOME_TASK
    [ "$hint" -gt 0 ] || fail TASK_AUTHORITY_REQUIRED
    [ "$home_task" -gt 0 ] || fail HOME_TASK_AUTHORITY_REQUIRED

    # Routine parking must preserve the exact freeform navigation task; mode changes are separate
    # explicit transactions because Android Q may require Activity re-delivery for those changes.
    navigation_task="$hint"
    require_task "$PKG" "$navigation_task" 1
    [ "$DISPLAY_ID" = 0 ] || fail DISPLAY_MISMATCH
    [ "$WINDOWING_MODE" = 5 ] || fail SUSPEND_MODE_MISMATCH
    [ "$TASK_BOUNDS" != unknown ] || fail BOUNDS_UNKNOWN
    navigation_bounds="$TASK_BOUNDS"
    require_task "$HOME_PKG" "$home_task" 1
    [ "$DISPLAY_ID" = 0 ] || fail HOME_DISPLAY_MISMATCH
    [ "$WINDOWING_MODE" = 1 ] || fail HOME_MODE_MISMATCH
    require_handoff_foreground "$home_task" "$navigation_task"
    bounded am task focus "$home_task" >"$LAUNCH_OUTPUT" 2>&1 || fail HOME_FOCUS_FAILED
    require_foreground_task "$home_task" HOME_NOT_FOREGROUND
    require_task "$HOME_PKG" "$home_task" 1
    [ "$DISPLAY_ID" = 0 ] || fail HOME_DISPLAY_MISMATCH
    [ "$WINDOWING_MODE" = 1 ] || fail HOME_MODE_MISMATCH
    PKG="${3:-}"
    require_task "$PKG" "$navigation_task" 1
    [ "$DISPLAY_ID" = 0 ] || fail DISPLAY_MISMATCH
    [ "$WINDOWING_MODE" = 5 ] || fail SUSPEND_STATE_CHANGED
    [ "$TASK_BOUNDS" = "$navigation_bounds" ] || fail SUSPEND_STATE_CHANGED
    # A navigation task can replace its top Activity during this handoff. require_task
    # already verifies that the observed component still belongs to the same package.
    emit_protocol OK SUSPENDED
    ;;

  *) fail BAD_ACTION ;;
esac
