#!/data/data/com.termux/files/usr/bin/bash
# Bounded, reversible standalone TS18 launcher installer/home helper.

SCRIPT_VERSION="1.1.0"
PACKAGE="com.cbkii.ts18launcher"
HOME_COMPONENT="com.cbkii.ts18launcher/.HomeAlias"
STATE_DIR="$HOME/.local/state/ts18-standalone-launcher"
PREVIOUS_HOME_FILE="$STATE_DIR/previous-home.txt"
TIMEOUT_SECONDS=15

APK=""
SET_HOME=0
ROLLBACK=0
WARNINGS=0
OP_FAILURE=0

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
  cat <<EOF
TS18 standalone launcher installer v$SCRIPT_VERSION

Usage:
  install-standalone-launcher.sh /path/to/launcher.apk [--set-home]
  install-standalone-launcher.sh --rollback-home

The script uses Magisk root only for bounded package/HOME operations.
It validates the APK package identity and APK signature before install. When an
adjacent SHA256SUMS.txt exists, the APK digest must match it exactly.
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

is_candidate_home() {
  [[ "$1" == "$PACKAGE/"* ]]
}

root_cmd() {
  local command_text="$1"
  timeout -k 2 "$TIMEOUT_SECONDS" su -c "$command_text"
}

resolve_home() {
  root_cmd "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null" \
    | awk 'NF { line=$0 } END { print line }'
}

find_aapt() {
  if command -v aapt >/dev/null 2>&1; then
    command -v aapt
  elif command -v aapt2 >/dev/null 2>&1; then
    command -v aapt2
  elif [[ -x /system/bin/aapt ]]; then
    printf '%s\n' /system/bin/aapt
  elif [[ -x /system/bin/aapt2 ]]; then
    printf '%s\n' /system/bin/aapt2
  else
    return 1
  fi
}

validate_apk_identity() {
  local aapt_bin badging app_id
  aapt_bin="$(find_aapt)" || stop "aapt/aapt2 is required to validate the APK before installation"
  if ! badging="$(timeout -k 2 12 "$aapt_bin" dump badging "$APK" 2>&1)"; then
    stop "APK package metadata could not be parsed: $badging"
  fi
  app_id="$(printf '%s\n' "$badging" \
    | sed -n "s/^package: name='\([^']*\)'.*/\1/p" \
    | head -n 1)"
  [[ "$app_id" == "$PACKAGE" ]] \
    || stop "APK application ID mismatch: expected $PACKAGE, found ${app_id:-unknown}"
  log "Verified APK application ID: $app_id"
}

verify_apk_signature() {
  local apksigner_bin
  apksigner_bin="$(command -v apksigner 2>/dev/null || true)"
  [[ -n "$apksigner_bin" ]] \
    || stop "apksigner is required to verify the APK signature before installation"
  if ! timeout -k 2 20 "$apksigner_bin" verify --verbose "$APK" >/dev/null 2>&1; then
    stop "APK signature verification failed"
  fi
  log "Verified APK signature structure"
}

verify_adjacent_checksum() {
  local sums base expected actual count
  sums="$(dirname -- "$APK")/SHA256SUMS.txt"
  base="$(basename -- "$APK")"
  if [[ ! -f "$sums" ]]; then
    warn "no adjacent SHA256SUMS.txt; package/signature checks pass but this is not checksum-qualified"
    return 0
  fi

  expected="$(awk -v file="$base" '
    $2 == file || $2 == "*" file { print $1 }
  ' "$sums")"
  count="$(printf '%s\n' "$expected" | awk 'NF { n++ } END { print n+0 }')"
  [[ "$count" == "1" ]] \
    || stop "SHA256SUMS.txt must contain exactly one entry for $base"
  [[ "$expected" =~ ^[0-9A-Fa-f]{64}$ ]] \
    || stop "SHA256SUMS.txt contains an invalid digest for $base"

  actual="$(sha256sum -- "$APK" | awk '{print $1}')" \
    || stop "could not calculate APK SHA-256"
  [[ "${actual,,}" == "${expected,,}" ]] \
    || stop "APK SHA-256 does not match adjacent SHA256SUMS.txt"
  log "Verified APK SHA-256 against adjacent SHA256SUMS.txt"
}

capture_rollback_home_for_change() {
  local previous captured
  previous="$(resolve_home 2>/dev/null)"
  if safe_component "$previous" && ! is_candidate_home "$previous"; then
    printf '%s\n' "$previous" >"$PREVIOUS_HOME_FILE" \
      || stop "cannot save rollback HOME"
    chmod 600 "$PREVIOUS_HOME_FILE" 2>/dev/null || true
    log "Captured previous HOME: $previous"
    return 0
  fi

  if is_candidate_home "$previous" && [[ -f "$PREVIOUS_HOME_FILE" ]]; then
    captured="$(head -n 1 "$PREVIOUS_HOME_FILE")"
    if safe_component "$captured" && ! is_candidate_home "$captured"; then
      log "Current HOME is the candidate; preserving captured rollback HOME: $captured"
      return 0
    fi
  fi

  stop "--set-home requires a safely captured non-launcher rollback HOME; current HOME was ${previous:-unresolved}"
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
command -v sha256sum >/dev/null 2>&1 || stop "sha256sum is unavailable"

log "TS18 standalone launcher installer v$SCRIPT_VERSION"

root_uid="$(root_cmd 'id -u' 2>/dev/null | head -n 1)"
[[ "$root_uid" == "0" ]] || stop "su did not provide UID 0"

mkdir -p -- "$STATE_DIR" || stop "cannot create private state directory"
chmod 700 "$STATE_DIR" 2>/dev/null || true

if ((ROLLBACK)); then
  [[ -z "$APK" ]] || stop "do not supply an APK with --rollback-home"
  log "Rollback requested; launcher APK will remain installed."
  previous=""
  if [[ -f "$PREVIOUS_HOME_FILE" ]]; then
    previous="$(head -n 1 "$PREVIOUS_HOME_FILE")"
  fi
  safe_component "$previous" && ! is_candidate_home "$previous" \
    || stop "no safe non-launcher previous HOME component was captured"

  log "Restoring previously captured HOME: $previous"
  if ! root_cmd "cmd package set-home-activity --user 0 '$previous'"; then
    warn "previous HOME could not be restored automatically"
    OP_FAILURE=1
  fi

  if ! root_cmd "pm disable --user 0 '$HOME_COMPONENT'"; then
    warn "launcher HOME alias could not be disabled"
    OP_FAILURE=1
  fi

  current="$(resolve_home 2>/dev/null)"
  log "Current HOME after rollback attempt: ${current:-unknown}"
  if [[ "$current" != "$previous" ]]; then
    warn "resolved HOME does not match the captured rollback component"
    OP_FAILURE=1
  fi

  if ((OP_FAILURE)); then
    log "FAILED: rollback did not complete cleanly"
    exit 1
  fi
  log "SUCCESS"
  exit 0
fi

[[ -n "$APK" ]] || stop "APK path is required"
safe_apk_path "$APK" || stop "APK path failed safety validation: $APK"
[[ -f "$APK" ]] || stop "APK not found: $APK"

validate_apk_identity
verify_apk_signature
verify_adjacent_checksum

if ((SET_HOME)); then
  capture_rollback_home_for_change
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
  root_cmd "pm enable --user 0 '$HOME_COMPONENT'" >/dev/null \
    || stop "HOME alias enable failed"

  log "Assigning HOME with one bounded root command."
  if ! root_cmd "cmd package set-home-activity --user 0 '$HOME_COMPONENT'"; then
    warn "root HOME assignment failed; use launcher Settings → Set as HOME (system UI)"
    OP_FAILURE=1
  fi

  current="$(resolve_home 2>/dev/null)"
  log "Current HOME: ${current:-unknown}"
  if [[ "$current" != "$HOME_COMPONENT" && "$current" != "$PACKAGE/"* ]]; then
    warn "TS18 Launcher is not the resolved HOME"
    OP_FAILURE=1
  fi
else
  log "HOME was not changed. Launch TS18 Launcher normally and validate it first."
  log "When ready, rerun with --set-home or use launcher Settings."
fi

if ((OP_FAILURE)); then
  log "FAILED: requested HOME change did not complete"
  exit 1
fi
if ((WARNINGS)); then
  log "COMPLETED WITH WARNINGS"
else
  log "SUCCESS"
fi
