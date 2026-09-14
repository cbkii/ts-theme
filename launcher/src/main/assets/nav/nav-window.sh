#!/system/bin/sh
# TS18 HOME navigation experiment controller.
# Root-only, bounded, fail-closed and read-only with respect to Topway state.

PATH=/system/bin:/system/xbin:/vendor/bin
export PATH
umask 077

ROOT_DIR=/data/adb/ts18-launcher
SNAPSHOT="$ROOT_DIR/.activity.$$"
trap 'rm -f "$SNAPSHOT"' EXIT HUP INT TERM

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
in_task && /TaskRecord\{/ && index($0, " A=" pkg " ") {
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
matched && /\* Hist #0:/ {
  hist0=1
  next
}
matched && hist0 && /mActivityComponent=/ {
  line=$0
  sub(/^.*mActivityComponent=/, "", line)
  sub(/[[:space:]].*$/, "", line)
  task_component=line
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

PINNED_SNAPSHOT_AWK='
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
  if (line ~ /mode=pinned([[:space:]]|$)/) return "2"
  if (line ~ /mode=freeform([[:space:]]|$)/) return "5"
  if (line ~ /mode=fullscreen([[:space:]]|$)/) return "1"
  return ""
}
/^[[:space:]]*(Stack|RootTask) #[0-9]+/ {
  stack=digits_after_hash($0)
  mode=mode_of($0)
  task=""
  next
}
/^[[:space:]]*Task id #[0-9]+/ {
  task=digits_after_hash($0)
  next
}
/TaskRecord\{/ && mode == "2" {
  line=$0
  pkg=line
  sub(/^.* A=/, "", pkg)
  sub(/[[:space:]].*$/, "", pkg)
  if (task == "") task=digits_after_hash($0)
  print pkg " " task " " stack
  exit
}'

log_event() {
  if command -v log >/dev/null 2>&1; then
    log -t TS18Nav "$*" 2>/dev/null || true
  fi
}

fail() {
  code="$1"
  shift
  log_event "FAIL $code $*"
  printf 'FAIL code=%s' "$code"
  for item in "$@"; do
    printf ' %s' "$item"
  done
  printf '\n'
  exit 1
}

valid_package() {
  case "$1" in
    ''|*[!A-Za-z0-9._]*) return 1 ;;
    *.*) return 0 ;;
    *) return 1 ;;
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

capture_activity() {
  dumpsys activity activities >"$SNAPSHOT" 2>/dev/null || return 1
  [ -s "$SNAPSHOT" ]
}

parse_task_snapshot() {
  pkg="$1"
  hint="$2"
  awk -v pkg="$pkg" -v hint="$hint" "$TASK_SNAPSHOT_AWK" "$SNAPSHOT"
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

require_task() {
  pkg="$1"
  hint="$2"
  tries="${3:-1}"
  read_task "$pkg" "$hint" "$tries"
  rc=$?
  case "$rc" in
    0) ;;
    1) fail TASK_NOT_FOUND "task=$hint" "package=$pkg" ;;
    2) fail TASK_AMBIGUOUS "package=$pkg" "count=$TASK_COUNT" ;;
    *) fail TASK_STATE_UNREADABLE "task=$hint" "package=$pkg" ;;
  esac
  case "$TASK_COMPONENT" in
    "$pkg"/*) ;;
    *) fail COMPONENT_MISMATCH "task=$TASK_ID" "package=$pkg" "component=$TASK_COMPONENT" ;;
  esac
}

parse_pinned_snapshot() {
  awk "$PINNED_SNAPSHOT_AWK" "$SNAPSHOT"
}

pinned_owner() {
  if ! capture_activity; then
    PINNED_PACKAGE=unknown
    PINNED_TASK=unknown
    PINNED_STACK=unknown
    return 1
  fi
  record="$(parse_pinned_snapshot)"
  if [ -z "$record" ]; then
    PINNED_PACKAGE=none
    PINNED_TASK=none
    PINNED_STACK=none
    return 0
  fi
  read -r PINNED_PACKAGE PINNED_TASK PINNED_STACK <<EOF_PINNED
$record
EOF_PINNED
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

emit_ok() {
  code="$1"
  pinned_owner >/dev/null 2>&1 || true
  printf 'OK code=%s task=%s stack=%s package=%s component=%s display=%s windowingMode=%s bounds=%s supportsPip=%s pinnedPackage=%s pinnedTask=%s pinnedStack=%s ' \
    "$code" "$TASK_ID" "$STACK_ID" "$PKG" "$TASK_COMPONENT" "$DISPLAY_ID" "$WINDOWING_MODE" \
    "$TASK_BOUNDS" "$SUPPORTS_PIP" "$PINNED_PACKAGE" "$PINNED_TASK" "$PINNED_STACK"
  vendor_state_fields
  printf '\n'
  log_event "OK $code package=$PKG task=$TASK_ID stack=$STACK_ID display=$DISPLAY_ID mode=$WINDOWING_MODE bounds=$TASK_BOUNDS component=$TASK_COMPONENT"
}

wait_state() {
  pkg="$1"
  task="$2"
  mode="$3"
  bounds="$4"
  tries=0
  while [ "$tries" -lt 20 ]; do
    if read_task_once "$pkg" "$task"; then
      if [ "$DISPLAY_ID" = 0 ] && [ "$WINDOWING_MODE" = "$mode" ]; then
        if [ "$bounds" = any ] || [ "$TASK_BOUNDS" = "$bounds" ]; then
          return 0
        fi
      fi
    fi
    tries=$((tries + 1))
    sleep 0.1
  done
  return 1
}

move_task_fullscreen() {
  pkg="$1"
  task="$2"
  require_task "$pkg" "$task" 1
  wanted_task="$TASK_ID"
  component="$TASK_COMPONENT"
  if [ "$WINDOWING_MODE" != 1 ]; then
    am start --user 0 --windowingMode 1 --task "$wanted_task" -f 0x20000000 -n "$component" >/dev/null 2>&1 || \
      fail FULLSCREEN_FAILED "task=$wanted_task" "package=$pkg" "component=$component"
  fi
  wait_state "$pkg" "$wanted_task" 1 any || \
    fail FULLSCREEN_REJECTED "task=$wanted_task" "package=$pkg"
  require_task "$pkg" "$wanted_task" 1
}

[ "$(id -u 2>/dev/null)" = 0 ] || fail ROOT_REQUIRED
for required in am dumpsys awk getprop pm settings grep tr cut cat; do
  command -v "$required" >/dev/null 2>&1 || fail "${required}_MISSING"
done

action="${1:-}"
case "$action" in
  probe)
    if pm list features 2>/dev/null | grep -q android.software.picture_in_picture; then
      pip_feature=1
    else
      pip_feature=0
    fi
    force_resizable="$(settings get global force_resizable_activities 2>/dev/null)"
    printf 'OK code=READY uid=0 pipFeature=%s forceResizable=%s ' \
      "$pip_feature" "$(sanitize_value "$force_resizable")"
    vendor_state_fields
    printf '\n'
    ;;

  status)
    PKG="${2:-}"
    hint="${3:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    require_task "$PKG" "$hint" 1
    emit_ok STATUS
    ;;

  freeform|verify-freeform|pip|verify-pip)
    PKG="${2:-}"
    left="${3:-}"
    top="${4:-}"
    right="${5:-}"
    bottom="${6:-}"
    hint="${7:-0}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    validate_bounds "$left" "$top" "$right" "$bottom" || fail BAD_BOUNDS
    require_task "$PKG" "$hint" 24
    wanted_task="$TASK_ID"
    expected="$left,$top,$right,$bottom"

    if [ "$action" = freeform ]; then
      am task resizeable "$wanted_task" 2 >/dev/null 2>&1 || \
        fail RESIZEABLE_FAILED "task=$wanted_task" "package=$PKG"
      am task resize "$wanted_task" "$left" "$top" "$right" "$bottom" >/dev/null 2>&1 || \
        fail RESIZE_FAILED "task=$wanted_task" "package=$PKG"
    elif [ "$action" = pip ]; then
      pm list features 2>/dev/null | grep -q android.software.picture_in_picture || \
        fail PIP_FEATURE_MISSING "package=$PKG"
      if [ "$SUPPORTS_PIP" = 0 ]; then
        fail PIP_UNSUPPORTED "task=$wanted_task" "package=$PKG"
      fi
      [ "$SUPPORTS_PIP" = 1 ] || \
        fail PIP_SUPPORT_UNKNOWN "task=$wanted_task" "package=$PKG"
      pinned_owner || fail PIP_STATE_UNREADABLE "package=$PKG"
      if [ "$PINNED_PACKAGE" != none ] && [ "$PINNED_PACKAGE" != "$PKG" ]; then
        fail PIP_OCCUPIED_BY_OTHER_APP "package=$PKG" "pinnedPackage=$PINNED_PACKAGE"
      fi
      if [ "$WINDOWING_MODE" != 2 ]; then
        [ "$STACK_ID" != unknown ] || fail STACK_UNKNOWN "task=$wanted_task" "package=$PKG"
        am task focus "$wanted_task" >/dev/null 2>&1 || \
          fail FOCUS_FAILED "task=$wanted_task" "package=$PKG"
        sleep 0.1
        require_task "$PKG" "$wanted_task" 1
        am stack move-top-activity-to-pinned-stack "$STACK_ID" \
          "$left" "$top" "$right" "$bottom" >/dev/null 2>&1 || \
          fail PIP_ENTER_FAILED "task=$wanted_task" "stack=$STACK_ID" "package=$PKG"
      fi
      wait_state "$PKG" "$wanted_task" 2 any || \
        fail PIP_MODE_REJECTED "task=$wanted_task" "package=$PKG"
      [ "$STACK_ID" != unknown ] || fail PIP_STACK_UNKNOWN "task=$wanted_task" "package=$PKG"
      am stack resize "$STACK_ID" "$left" "$top" "$right" "$bottom" >/dev/null 2>&1 || \
        fail PIP_RESIZE_FAILED "task=$wanted_task" "stack=$STACK_ID" "package=$PKG"
    fi

    expected_mode=5
    result_code=FREEFORM
    case "$action" in
      verify-freeform) result_code=VERIFY_FREEFORM ;;
      pip) expected_mode=2; result_code=PIP ;;
      verify-pip) expected_mode=2; result_code=VERIFY_PIP ;;
    esac

    if ! wait_state "$PKG" "$wanted_task" "$expected_mode" "$expected"; then
      require_task "$PKG" "$wanted_task" 1
      [ "$DISPLAY_ID" = 0 ] || \
        fail DISPLAY_MISMATCH "task=$TASK_ID" "package=$PKG" "display=$DISPLAY_ID" "expectedDisplay=0"
      [ "$WINDOWING_MODE" = "$expected_mode" ] || \
        fail WINDOWING_MODE_MISMATCH "task=$TASK_ID" "package=$PKG" "windowingMode=$WINDOWING_MODE" "expectedWindowingMode=$expected_mode"
      fail BOUNDS_MISMATCH "task=$TASK_ID" "package=$PKG" "bounds=$TASK_BOUNDS" "expected=$expected"
    fi
    require_task "$PKG" "$wanted_task" 1
    emit_ok "$result_code"
    ;;

  fullscreen)
    PKG="${2:-}"
    hint="${3:-}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    [ "$hint" -gt 0 ] || fail TASK_AUTHORITY_REQUIRED "package=$PKG"
    move_task_fullscreen "$PKG" "$hint"
    emit_ok FULLSCREEN
    ;;

  suspend)
    PKG="${2:-}"
    hint="${3:-}"
    HOME_PKG="${4:-}"
    home_task="${5:-}"
    valid_package "$PKG" || fail BAD_PACKAGE
    valid_uint "$hint" || fail BAD_TASK
    valid_package "$HOME_PKG" || fail BAD_HOME_PACKAGE
    valid_uint "$home_task" || fail BAD_HOME_TASK
    [ "$hint" -gt 0 ] || fail TASK_AUTHORITY_REQUIRED "package=$PKG"
    [ "$home_task" -gt 0 ] || fail HOME_TASK_AUTHORITY_REQUIRED "package=$HOME_PKG"

    move_task_fullscreen "$PKG" "$hint"
    wanted_task="$TASK_ID"
    require_task "$HOME_PKG" "$home_task" 1
    am task focus "$home_task" >/dev/null 2>&1 || \
      fail HOME_FOCUS_FAILED "task=$home_task" "package=$HOME_PKG"
    PKG="${2:-}"
    require_task "$PKG" "$wanted_task" 1
    [ "$WINDOWING_MODE" = 1 ] || \
      fail SUSPEND_MODE_MISMATCH "task=$wanted_task" "package=$PKG" "windowingMode=$WINDOWING_MODE"
    emit_ok SUSPENDED
    ;;

  *) fail BAD_ACTION ;;
esac
