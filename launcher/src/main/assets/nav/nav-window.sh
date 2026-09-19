#!/system/bin/sh
# Deterministic TS18 HOME native-navigation task controller.
# Root-only, bounded, fail-closed and read-only with respect to Topway state.

PATH=/system/bin:/system/xbin:/vendor/bin
export PATH
umask 077

ROOT_DIR=/data/adb/ts18-launcher
SNAPSHOT="$ROOT_DIR/.activity.$$"
START_HELP="$ROOT_DIR/.am-help.$$"
LAUNCH_OUTPUT="$ROOT_DIR/.am-start.$$"
trap 'rm -f "$SNAPSHOT" "$START_HELP" "$LAUNCH_OUTPUT"' EXIT HUP INT TERM

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
in_task && !matched && /^[[:space:]]*\* TaskRecord\{/ && (index($0, " A=" pkg " ") || index($0, " A=" pkg "}")) {
  if (hint != "0" && task != hint) next
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
/mResumedActivity: ActivityRecord\{|topResumedActivity=ActivityRecord\{/ {
  task=task_from_record($0)
  if (task != "") {
    print task
    exit
  }
}'

PKG=unknown
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
  printf '%s code=%s task=%s stack=%s package=%s component=%s display=%s windowingMode=%s bounds=%s supportsPip=%s launched=%s transaction=%s helpExit=%s helpWindowingMode=%s helpDisplay=%s launchExit=%s ' \
    "$outcome" "$code" "$TASK_ID" "$STACK_ID" "$PKG" "$TASK_COMPONENT" "$DISPLAY_ID" \
    "$WINDOWING_MODE" "$TASK_BOUNDS" "$SUPPORTS_PIP" "$LAUNCHED" "$TRANSACTION" \
    "$HELP_EXIT" "$HELP_WINDOWING_MODE" "$HELP_DISPLAY" "$LAUNCH_EXIT"
  vendor_state_fields
  printf '\n'
}

fail() {
  code="$1"
  shift
  log_event "FAIL $code $*"
  emit_protocol FAIL "$code"
  exit 1
}

capture_activity() {
  dumpsys activity activities >"$SNAPSHOT" 2>/dev/null || return 1
  [ -s "$SNAPSHOT" ]
}

parse_task_snapshot() {
  pkg="$1"
  hint="$2"
  awk -v pkg="$pkg" -v hint="$hint" "$TASK_SNAPSHOT_AWK" "$SNAPSHOT"
}

parse_foreground_task_snapshot() {
  awk "$FOREGROUND_TASK_AWK" "$SNAPSHOT"
}

read_task_once() {
  pkg="$1"
  hint="$2"
  capture_activity || return 3
  record="$(parse_task_snapshot "$pkg" "$hint")"
  kind=""
  value1=""
  value2=""
  value3=""
  value4=""
  value5=""
  value6=""
  value7=""
  read -r kind value1 value2 value3 value4 value5 value6 value7 <<EOF_RECORD
$record
EOF_RECORD
  case "$kind" in
    NONE) return 1 ;;
    AMBIGUOUS)
      TASK_COUNT="${value1:-unknown}"
      return 2
      ;;
    FOUND)
      TASK_ID="$value1"
      STACK_ID="${value2:-unknown}"
      DISPLAY_ID="${value3:-unknown}"
      WINDOWING_MODE="${value4:-unknown}"
      TASK_BOUNDS="${value5:-unknown}"
      TASK_COMPONENT="${value6:-unknown}"
      SUPPORTS_PIP="${value7:-unknown}"
      valid_uint "$TASK_ID" || return 3
      return 0
      ;;
    *) return 3 ;;
  esac
}

read_task() {
  pkg="$1"
  hint="$2"
  tries="${3:-1}"
  n=0
  rc=1
  while [ "$n" -lt "$tries" ]; do
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
    "$PKG"/*) return 0 ;;
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
    *) fail TASK_STATE_UNREADABLE ;;
  esac
  validate_observed_component
}

wait_state() {
  pkg="$1"
  task="$2"
  mode="$3"
  expected_bounds="$4"
  tries=0
  while [ "$tries" -lt 30 ]; do
    if read_task_once "$pkg" "$task"; then
      validate_observed_component
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
  am help >"$START_HELP" 2>&1
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
  native_launch_supported || fail FREEFORM_LAUNCH_UNSUPPORTED
  am start --user 0 --display 0 --windowingMode 5 \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    -f 0x10000000 -n "$component" >"$LAUNCH_OUTPUT" 2>&1
  LAUNCH_EXIT=$?
  if [ "$LAUNCH_EXIT" -ne 0 ]; then
    if grep -q -i -E 'Unknown option.*(--windowingMode|--display)' "$LAUNCH_OUTPUT"; then
      fail FREEFORM_LAUNCH_UNSUPPORTED
    fi
    fail FREEFORM_LAUNCH_FAILED
  fi
  LAUNCHED=1
  log_event "cold launch transaction=$TRANSACTION package=$PKG component=$component display=0 mode=5"
}

move_task_fullscreen() {
  pkg="$1"
  task="$2"
  require_task "$pkg" "$task" 1
  wanted_task="$TASK_ID"
  component="$TASK_COMPONENT"
  if [ "$WINDOWING_MODE" != 1 ]; then
    [ "$component" != unknown ] || fail COMPONENT_UNKNOWN
    am start --user 0 --display 0 --windowingMode 1 --task "$wanted_task" \
      -f 0x20000000 -n "$component" >"$LAUNCH_OUTPUT" 2>&1 || fail FULLSCREEN_FAILED
  else
    am task focus "$wanted_task" >"$LAUNCH_OUTPUT" 2>&1 || fail FOCUS_FAILED
  fi
  wait_state "$pkg" "$wanted_task" 1 any || fail FULLSCREEN_REJECTED
  require_foreground_task "$wanted_task" FULLSCREEN_NOT_FOREGROUND
  require_task "$pkg" "$wanted_task" 1
}

[ "$(id -u 2>/dev/null)" = 0 ] || fail ROOT_REQUIRED
for required in am dumpsys awk getprop grep tr cut cat; do
  command -v "$required" >/dev/null 2>&1 || fail "${required}_MISSING"
done

action="${1:-}"
case "$action" in
  probe)
    native_launch=0
    if native_launch_supported; then native_launch=1; fi
    printf 'OK code=READY uid=0 nativeLaunch=%s helpExit=%s helpWindowingMode=%s helpDisplay=%s ' \
      "$native_launch" "$HELP_EXIT" "$HELP_WINDOWING_MODE" "$HELP_DISPLAY"
    vendor_state_fields
    printf '\n'
    ;;

  status)
    PKG="${2:-}"
    hint="${3:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    require_task "$PKG" "$hint" 1
    emit_protocol OK STATUS
    ;;

  present-native)
    PKG="${2:-}"
    launch_component="${3:-}"
    left="${4:-}"
    top="${5:-}"
    right="${6:-}"
    bottom="${7:-}"
    hint="${8:-0}"
    TRANSACTION="${9:-0}"
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
          launch_freeform_once "$launch_component"
          require_task "$PKG" 0 30
          ;;
        2) fail TASK_AMBIGUOUS "count=${TASK_COUNT:-unknown}" ;;
        *) fail TASK_STATE_UNREADABLE ;;
      esac
    fi

    wanted_task="$TASK_ID"
    [ "$DISPLAY_ID" = 0 ] || fail DISPLAY_MISMATCH
    am task resizeable "$wanted_task" 2 >"$LAUNCH_OUTPUT" 2>&1 || fail RESIZEABLE_FAILED
    am task resize "$wanted_task" "$left" "$top" "$right" "$bottom" \
      >"$LAUNCH_OUTPUT" 2>&1 || fail RESIZE_FAILED
    if ! wait_state "$PKG" "$wanted_task" 5 "$expected"; then
      require_task "$PKG" "$wanted_task" 1
      verify_state 5 "$expected"
    fi
    require_task "$PKG" "$wanted_task" 1
    verify_state 5 "$expected"
    emit_protocol OK PRESENTED_NATIVE
    log_event "OK native transaction=$TRANSACTION package=$PKG task=$TASK_ID display=$DISPLAY_ID mode=$WINDOWING_MODE bounds=$TASK_BOUNDS launched=$LAUNCHED component=$TASK_COMPONENT"
    ;;

  verify-native)
    PKG="${2:-}"
    left="${3:-}"
    top="${4:-}"
    right="${5:-}"
    bottom="${6:-}"
    hint="${7:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    [ "$hint" -gt 0 ] || fail TASK_AUTHORITY_REQUIRED
    validate_bounds "$left" "$top" "$right" "$bottom" || fail BAD_BOUNDS
    require_task "$PKG" "$hint" 1
    verify_state 5 "$left,$top,$right,$bottom"
    emit_protocol OK VERIFIED_NATIVE
    ;;

  fullscreen)
    PKG="${2:-}"
    hint="${3:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    [ "$hint" -gt 0 ] || fail TASK_AUTHORITY_REQUIRED
    move_task_fullscreen "$PKG" "$hint"
    emit_protocol OK FULLSCREEN
    ;;

  suspend)
    PKG="${2:-}"
    hint="${3:-0}"
    HOME_PKG="${4:-}"
    home_task="${5:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    valid_package "$HOME_PKG" || fail BAD_HOME_PACKAGE
    valid_uint "$home_task" || fail BAD_HOME_TASK
    [ "$hint" -gt 0 ] || fail TASK_AUTHORITY_REQUIRED
    [ "$home_task" -gt 0 ] || fail HOME_TASK_AUTHORITY_REQUIRED

    move_task_fullscreen "$PKG" "$hint"
    wanted_task="$TASK_ID"
    require_task "$HOME_PKG" "$home_task" 1
    am task focus "$home_task" >"$LAUNCH_OUTPUT" 2>&1 || fail HOME_FOCUS_FAILED
    require_foreground_task "$home_task" HOME_NOT_FOREGROUND
    PKG="${2:-}"
    require_task "$PKG" "$wanted_task" 1
    [ "$WINDOWING_MODE" = 1 ] || fail SUSPEND_MODE_MISMATCH
    emit_protocol OK SUSPENDED
    ;;

  *) fail BAD_ACTION ;;
esac
