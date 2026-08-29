#!/usr/bin/env bash
# Shared TS18 theme installer helpers. Intentionally no global `set -euo pipefail`.

TS18_EXPORT_ROOT="${TS18_EXPORT_ROOT:-/storage/emulated/0/Download/TS18-theme-install}"
TS18_PRIVATE_ROOT="${TS18_PRIVATE_ROOT:-${HOME}/.local/state/ts18-theme-install}"
TS18_DOFUN_PACKAGE="com.dofun.variety"
TS18_THEME_PACKAGE="launcher.variety.theme.plugin.sfp_cbk_black"
TS18_EXPECTED_SAFE_RIGHT=1225
TS18_FIXED_STAGE="${TS18_EXPORT_ROOT}/.staged-theme.apk"

log() { printf '%s\n' "$*" >&2; }
warn() { printf 'WARNING: %s\n' "$*" >&2; }
fail() { printf 'FAILED: %s\n' "$*" >&2; return 1; }
stop_safety() { printf 'STOP: %s\nSTOPPED FOR SAFETY\n' "$*" >&2; return 2; }

have() { command -v "$1" >/dev/null 2>&1; }
sha256_file() {
  if have sha256sum; then sha256sum -- "$1" | awk '{print $1}'; return ${PIPESTATUS[0]}; fi
  if have toybox; then toybox sha256sum "$1" | awk '{print $1}'; return ${PIPESTATUS[0]}; fi
  fail "sha256sum is unavailable"
}

prepare_dirs() {
  mkdir -p -- "$TS18_PRIVATE_ROOT" || return 1
  mkdir -p -- "$TS18_EXPORT_ROOT" || return 1
}

root_available() {
  have su || return 1
  local out rc
  out="$(su -c 'id -u' 2>/dev/null)"; rc=$?
  [[ $rc -eq 0 && "$out" == "0" ]]
}

root_read() {
  local command_text="$1"
  su -c "$command_text"
}

stage_apk_fixed() {
  local apk="$1" expected actual
  [[ -f "$apk" ]] || { fail "APK not found: $apk"; return 1; }
  prepare_dirs || { fail "Cannot create installer directories"; return 1; }
  expected="$(sha256_file "$apk")" || return 1
  cp -- "$apk" "$TS18_FIXED_STAGE" || { fail "Cannot stage APK"; return 1; }
  actual="$(sha256_file "$TS18_FIXED_STAGE")" || return 1
  [[ "$actual" == "$expected" ]] || { fail "Staged APK hash mismatch"; return 1; }
  printf '%s\n' "$actual"
}

resolve_home() {
  if have cmd; then
    cmd package resolve-activity --brief --components --user 0 \
      -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1 | head -n 3
  else
    printf 'cmd unavailable\n'
  fi
}

safe_donor_basename() {
  [[ "$1" =~ ^[0-9]+\.jar$ ]]
}

final_status() {
  local rc="$1"
  case "$rc" in
    0) log "SUCCESS" ;;
    2) log "STOPPED FOR SAFETY" ;;
    130) log "INTERRUPTED" ;;
    *) log "FAILED" ;;
  esac
}
