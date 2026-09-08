#!/data/data/com.termux/files/usr/bin/bash
# Bounded, reversible standalone TS18 launcher installer/home helper.

SCRIPT_VERSION="1.0.0"
PACKAGE="com.cbkii.ts18launcher"
HOME_COMPONENT="com.cbkii.ts18launcher/.HomeAlias"
STATE_DIR="$HOME/.local/state/ts18-standalone-launcher"
PREVIOUS_HOME_FILE="$STATE_DIR/previous-home.txt"
TIMEOUT_SECONDS=15

APK=""
SET_HOME=0
ROLLBACK=0
WARNINGS=0

log() {
  printf '%s %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*"
}

warn() {
  WARNINGS=$((WARNINGS + 1))
  log "WARNING: $*"
}

stop() {
  log "STOP: $*"
  exit 2
}

usage() {
  cat <<'EOF'
Usage:
  install-standalone-launcher.sh /path/to/launcher.apk [--set-home]
  install-standalone-launcher.sh --rollback-home

The script uses Magisk root only for bounded package/HOME operations.
It does not disable/uninstall DoFun, clear app data, change SELinux, or write
system/vendor partitions.
EOF
}

safe_apk_path() {
  local value="$1"
  [[ "$value" == /* ]] || return 1
  [[ "$value" != *"'"* ]] || return 1
  [[ "$value" =~ ^[A-Za-z0-9_./+@%=:~,\ -]+\.apk$ ]]
}

safe_component() {
  [[ "$1" =~ ^[A-Za-z0-9._]+/[A-Za-z0-9._$]+$ ]]
}

root_cmd() {
  local command_text="$1"
  timeout -k 2 "$TIMEOUT_SECONDS" su -c "$command_text"
}

resolve_home() {
  root_cmd "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null" \
    | awk 'NF { line=$0 } END { print line }'
}

while (($#)); do
  case "$1" in
    --set-home)
      SET_HOME=1
      shift
      ;;
    --rollback-home)
      ROLLBACK=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --*)
      stop "unknown option: $1"
      ;;
    *)
      if [[ -n "$APK" ]]; then
        stop "only one APK path is accepted"
      fi
      APK="$1"
      shift
      ;;
  esac
done

command -v timeout >/dev/null 2>&1 || stop "timeout is missing; pkg install coreutils"
command -v su >/dev/null 2>&1 || stop "Magisk su is unavailable"

root_uid="$(root_cmd 'id -u' 2>/dev/null | head -n 1)"
[[ "$root_uid" == "0" ]] || stop "su did not provide UID 0"

mkdir -p -- "$STATE_DIR" || stop "cannot create private state directory"
chmod 700 "$STATE_DIR" 2>/dev/null || true

if ((ROLLBACK)); then
  log "Rollback requested; launcher APK will remain installed."
  previous=""
  if [[ -f "$PREVIOUS_HOME_FILE" ]]; then
    previous="$(head -n 1 "$PREVIOUS_HOME_FILE")"
  fi

  if safe_component "$previous"; then
    log "Restoring previously captured HOME: $previous"
    if ! root_cmd "cmd package set-home-activity --user 0 '$previous'"; then
      warn "previous HOME could not be restored automatically"
    fi
  else
    warn "no safe previous HOME component was captured"
  fi

  if ! root_cmd "pm disable --user 0 '$HOME_COMPONENT'"; then
    warn "launcher HOME alias could not be disabled"
  fi

  current="$(resolve_home 2>/dev/null)"
  log "Current HOME after rollback attempt: ${current:-unknown}"
  if ((WARNINGS)); then
    log "COMPLETED WITH WARNINGS"
  else
    log "SUCCESS"
  fi
  exit 0
fi

[[ -n "$APK" ]] || stop "APK path is required"
safe_apk_path "$APK" || stop "APK path failed safety validation: $APK"
[[ -f "$APK" ]] || stop "APK not found: $APK"

previous="$(resolve_home 2>/dev/null)"
if safe_component "$previous"; then
  printf '%s\n' "$previous" >"$PREVIOUS_HOME_FILE" || stop "cannot save rollback HOME"
  chmod 600 "$PREVIOUS_HOME_FILE" 2>/dev/null || true
  log "Captured previous HOME: $previous"
else
  warn "current HOME could not be captured as a safe component"
fi

log "Installing/updating standalone launcher without touching DoFun."
if ! root_cmd "pm install -r --user 0 '$APK'"; then
  stop "package installation failed"
fi

installed_path="$(root_cmd "pm path '$PACKAGE' 2>/dev/null" | head -n 1)"
[[ "$installed_path" == package:* ]] || stop "PackageManager cannot resolve installed launcher"
log "Installed: $installed_path"

if ((SET_HOME)); then
  log "Enabling launcher HOME alias."
  root_cmd "pm enable --user 0 '$HOME_COMPONENT'" >/dev/null || stop "HOME alias enable failed"

  log "Assigning HOME with one bounded root command."
  if ! root_cmd "cmd package set-home-activity --user 0 '$HOME_COMPONENT'"; then
    warn "root HOME assignment failed; use launcher Settings → Set as HOME (system UI)"
  fi

  current="$(resolve_home 2>/dev/null)"
  log "Current HOME: ${current:-unknown}"
  if [[ "$current" != "$HOME_COMPONENT" && "$current" != "$PACKAGE/"* ]]; then
    warn "TS18 Launcher is not yet the resolved HOME"
  fi
else
  log "HOME was not changed. Launch TS18 Launcher normally and validate it first."
  log "When ready, rerun with --set-home or use launcher Settings."
fi

if ((WARNINGS)); then
  log "COMPLETED WITH WARNINGS"
else
  log "SUCCESS"
fi
