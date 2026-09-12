#!/system/bin/sh
# TS18 native-navigation task controller. Root-only, bounded, property-read-only.
# Installed systemlessly by NavigationRootHelper under /data/adb/ts18-launcher/.

PATH=/system/bin:/system/xbin:/vendor/bin
export PATH

fail() {
  code="$1"
  shift
  printf 'FAIL code=%s' "$code"
  for item in "$@"; do printf ' %s' "$item"; done
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
  case "$1" in ''|*[!0-9]*) return 1 ;; *) return 0 ;; esac
}

activity_dump() {
  dumpsys activity activities 2>/dev/null
}

# task_hint=0 means acquire the first current task whose TaskRecord affinity is exactly pkg.
# A non-zero task_hint requires BOTH package and task id to match, so another task cannot be adopted.
find_task_record() {
  pkg="$1"
  task_hint="${2:-0}"
  activity_dump | awk -v pkg="$pkg" -v task_hint="$task_hint" '
    /^[[:space:]]*Task id #[0-9]+/ {
      task=$0
      sub(/^.*#/, "", task)
      sub(/[^0-9].*$/, "", task)
    }
    /TaskRecord\{/ && index($0, " A=" pkg " ") {
      if (task_hint != "0" && task != task_hint) next
      stack=$0
      sub(/^.*StackId=/, "", stack)
      sub(/[^0-9].*$/, "", stack)
      if (task != "" && stack != "") { print task " " stack; exit }
    }
  '
}

find_task_wait() {
  pkg="$1"
  task_hint="${2:-0}"
  tries=0
  while [ "$tries" -lt 24 ]; do
    record="$(find_task_record "$pkg" "$task_hint")"
    [ -n "$record" ] && { printf '%s\n' "$record"; return 0; }
    tries=$((tries + 1))
    sleep 0.1
  done
  return 1
}

bounds_for_task() {
  wanted="$1"
  activity_dump | awk -v wanted="$wanted" '
    /^[[:space:]]*Task id #[0-9]+/ {
      task=$0
      sub(/^.*#/, "", task)
      sub(/[^0-9].*$/, "", task)
      active=(task == wanted)
      next
    }
    active && /mBounds=Rect\(/ {
      line=$0
      sub(/^.*mBounds=Rect\(/, "", line)
      sub(/\).*$/, "", line)
      gsub(/, /, ",", line)
      gsub(/ - /, ",", line)
      print line
      exit
    }
  '
}

wait_for_bounds() {
  wanted="$1"
  expected="$2"
  tries=0
  actual=""
  while [ "$tries" -lt 15 ]; do
    actual="$(bounds_for_task "$wanted")"
    [ "$actual" = "$expected" ] && { printf '%s\n' "$actual"; return 0; }
    tries=$((tries + 1))
    sleep 0.1
  done
  printf '%s\n' "${actual:-unknown}"
  return 1
}

# Use Hist #0 rather than TaskRecord.mActivityComponent: the latter can retain a splash/root
# identity while a different Activity is actually the current top of the task.
top_component_for_task() {
  wanted="$1"
  activity_dump | awk -v wanted="$wanted" '
    /^[[:space:]]*Task id #[0-9]+/ {
      task=$0
      sub(/^.*#/, "", task)
      sub(/[^0-9].*$/, "", task)
      active=(task == wanted)
      hist=0
      next
    }
    active && /\* Hist #0:/ { hist=1; next }
    active && hist && /mActivityComponent=/ {
      line=$0
      sub(/^.*mActivityComponent=/, "", line)
      sub(/[[:space:]].*$/, "", line)
      print line
      exit
    }
  '
}

read_task() {
  pkg="$1"
  task_hint="${2:-0}"
  valid_uint "$task_hint" || return 1
  record="$(find_task_wait "$pkg" "$task_hint")" || return 1
  TASK_ID="${record%% *}"
  STACK_ID="${record#* }"
  valid_uint "$TASK_ID" || return 1
  valid_uint "$STACK_ID" || return 1
  return 0
}

validate_bounds() {
  left="$1"; top="$2"; right="$3"; bottom="$4"
  valid_uint "$left" && valid_uint "$top" && valid_uint "$right" && valid_uint "$bottom" || return 1
  [ "$right" -gt "$left" ] && [ "$bottom" -gt "$top" ] || return 1
  [ "$right" -le 10000 ] && [ "$bottom" -le 10000 ] || return 1
  return 0
}

[ "$(id -u 2>/dev/null)" = "0" ] || fail ROOT_REQUIRED
command -v am >/dev/null 2>&1 || fail AM_MISSING
command -v dumpsys >/dev/null 2>&1 || fail DUMPSYS_MISSING
command -v awk >/dev/null 2>&1 || fail AWK_MISSING

action="${1:-}"
case "$action" in
  probe)
    printf 'OK code=READY uid=0 forcepip=%s runtime_forcepip=%s\n' \
      "$(getprop persist.tw.forcepip 2>/dev/null)" "$(getprop sys.tw.forcepip 2>/dev/null)"
    ;;

  status)
    pkg="${2:-}"; task_hint="${3:-0}"
    valid_package "$pkg" || fail BAD_PACKAGE
    valid_uint "$task_hint" || fail BAD_TASK
    read_task "$pkg" "$task_hint" || fail TASK_NOT_FOUND "task=$task_hint" "package=$pkg"
    bounds="$(bounds_for_task "$TASK_ID")"
    component="$(top_component_for_task "$TASK_ID")"
    [ -n "$bounds" ] || bounds=unknown
    [ -n "$component" ] || component=unknown
    printf 'OK code=STATUS task=%s stack=%s package=%s component=%s bounds=%s forcepip=%s\n' \
      "$TASK_ID" "$STACK_ID" "$pkg" "$component" "$bounds" "$(getprop sys.tw.forcepip 2>/dev/null)"
    ;;

  window|verify)
    pkg="${2:-}"; left="${3:-}"; top="${4:-}"; right="${5:-}"; bottom="${6:-}"; task_hint="${7:-0}"
    valid_package "$pkg" || fail BAD_PACKAGE
    valid_uint "$task_hint" || fail BAD_TASK
    validate_bounds "$left" "$top" "$right" "$bottom" || fail BAD_BOUNDS
    read_task "$pkg" "$task_hint" || fail TASK_NOT_FOUND "task=$task_hint" "package=$pkg"
    expected="$left,$top,$right,$bottom"

    if [ "$action" = "window" ]; then
      am task resizeable "$TASK_ID" 2 >/dev/null 2>&1 || fail RESIZEABLE_FAILED "task=$TASK_ID" "stack=$STACK_ID" "package=$pkg"
      am task resize "$TASK_ID" "$left" "$top" "$right" "$bottom" >/dev/null 2>&1 || \
        fail RESIZE_FAILED "task=$TASK_ID" "stack=$STACK_ID" "package=$pkg"
      record="$(find_task_record "$pkg" "$TASK_ID")"
      [ -n "$record" ] || fail TASK_REPLACED "task=$TASK_ID" "package=$pkg"
      STACK_ID="${record#* }"
    fi

    actual="$(wait_for_bounds "$TASK_ID" "$expected")" || \
      fail BOUNDS_MISMATCH "task=$TASK_ID" "stack=$STACK_ID" "package=$pkg" "bounds=$actual" "expected=$expected"
    component="$(top_component_for_task "$TASK_ID")"
    [ -n "$component" ] || component=unknown
    if [ "$action" = "window" ]; then result_code=WINDOW; else result_code=VERIFY; fi
    printf 'OK code=%s task=%s stack=%s package=%s component=%s bounds=%s forcepip=%s\n' \
      "$result_code" "$TASK_ID" "$STACK_ID" "$pkg" "$component" "$actual" "$(getprop sys.tw.forcepip 2>/dev/null)"
    ;;

  focus)
    pkg="${2:-}"; task_hint="${3:-0}"
    valid_package "$pkg" || fail BAD_PACKAGE
    valid_uint "$task_hint" || fail BAD_TASK
    read_task "$pkg" "$task_hint" || fail TASK_NOT_FOUND "task=$task_hint" "package=$pkg"
    am task focus "$TASK_ID" >/dev/null 2>&1 || fail FOCUS_FAILED "task=$TASK_ID" "stack=$STACK_ID" "package=$pkg"
    printf 'OK code=FOCUS task=%s stack=%s package=%s\n' "$TASK_ID" "$STACK_ID" "$pkg"
    ;;

  fullscreen)
    pkg="${2:-}"; task_hint="${3:-0}"
    valid_package "$pkg" || fail BAD_PACKAGE
    valid_uint "$task_hint" || fail BAD_TASK
    read_task "$pkg" "$task_hint" || fail TASK_NOT_FOUND "task=$task_hint" "package=$pkg"
    component="$(top_component_for_task "$TASK_ID")"
    [ -n "$component" ] || fail COMPONENT_NOT_FOUND "task=$TASK_ID" "package=$pkg"
    # Android 10 ActivityManagerShellCommand supports both --task and --windowingMode. Launching
    # the current top Activity SINGLE_TOP into the same task requests true fullscreen mode without
    # resizing an entire shared freeform stack or manufacturing another task.
    am start --user 0 --windowingMode 1 --task "$TASK_ID" -f 0x20000000 -n "$component" >/dev/null 2>&1 || \
      fail FULLSCREEN_FAILED "task=$TASK_ID" "stack=$STACK_ID" "package=$pkg" "component=$component"
    record="$(find_task_record "$pkg" "$TASK_ID")"
    [ -n "$record" ] || fail TASK_REPLACED "task=$TASK_ID" "package=$pkg"
    STACK_ID="${record#* }"
    # Fullscreen TaskRecord bounds are empty (0,0,0,0) on this Android 10 family. Bounded polling
    # tolerates normal WindowManager/configuration propagation without accepting a stale freeform task.
    actual="$(wait_for_bounds "$TASK_ID" "0,0,0,0")" || \
      fail FULLSCREEN_REJECTED "task=$TASK_ID" "package=$pkg" "bounds=$actual"
    printf 'OK code=FULLSCREEN task=%s stack=%s package=%s component=%s bounds=%s\n' \
      "$TASK_ID" "$STACK_ID" "$pkg" "$component" "$actual"
    ;;

  *)
    fail BAD_ACTION
    ;;
esac
