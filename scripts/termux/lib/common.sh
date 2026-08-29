#!/usr/bin/env bash
# Shared TS18 theme installer helpers. Intentionally no global `set -euo pipefail`.

TS18_EXPORT_ROOT="${TS18_EXPORT_ROOT:-/storage/emulated/0/Download/TS18-theme-install}"
TS18_PRIVATE_ROOT="${TS18_PRIVATE_ROOT:-${HOME}/.local/state/ts18-theme-install}"
TS18_DOFUN_PACKAGE="com.dofun.variety"
TS18_EXPECTED_DOFUN_VERSION="V9.7.2.367.260312"
# shellcheck disable=SC2034 # Consumed by scripts that source this library.
TS18_THEME_PACKAGE="launcher.variety.theme.plugin.sfp_cbk_black"
# shellcheck disable=SC2034 # Consumed by scripts that source this library.
TS18_LEGACY_THEME_PACKAGE="launcher.variety.theme.plugin.cbk_black"
TS18_EXPECTED_PHYSICAL_SIZE="1280x720"
TS18_EXPECTED_SAFE_RIGHT=1225
TS18_FIXED_STAGE="${TS18_EXPORT_ROOT}/.staged-theme.apk"

log() { printf '%s\n' "$*" >&2; }
warn() { printf 'WARNING: %s\n' "$*" >&2; }
fail() { printf 'FAILED: %s\n' "$*" >&2; return 1; }
stop_safety() { printf 'STOP: %s\nSTOPPED FOR SAFETY\n' "$*" >&2; return 2; }

have() { command -v "$1" >/dev/null 2>&1; }
sha256_file() {
  if have sha256sum; then sha256sum -- "$1" | awk '{print $1}'; return "${PIPESTATUS[0]}"; fi
  if have toybox; then toybox sha256sum "$1" | awk '{print $1}'; return "${PIPESTATUS[0]}"; fi
  fail "sha256sum is unavailable"
}

prepare_dirs() {
  mkdir -p -- "$TS18_PRIVATE_ROOT" || return 1
  mkdir -p -- "$TS18_EXPORT_ROOT" || return 1
}

safe_local_path() {
  [[ "$1" =~ ^/[A-Za-z0-9._/-]+$ ]]
}

root_available() {
  have su || return 1
  local out rc
  out="$(su -c 'id -u' 2>/dev/null)"; rc=$?
  [[ $rc -eq 0 && "$out" == "0" ]]
}

stage_apk_fixed() {
  local apk="$1" expected actual
  [[ -f "$apk" ]] || { fail "APK not found: $apk"; return 1; }
  prepare_dirs || { fail "Cannot create installer directories"; return 1; }
  safe_local_path "$TS18_FIXED_STAGE" || { stop_safety "Unsafe staged-APK path"; return 2; }
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

window_dump() {
  if root_available; then su -c 'dumpsys window displays' 2>/dev/null
  elif have dumpsys; then dumpsys window displays 2>/dev/null
  else return 1
  fi
}

detect_physical_size() {
  have wm || return 1
  wm size 2>/dev/null | sed -nE 's/.*Physical size:[[:space:]]*([0-9]+x[0-9]+).*/\1/p' | head -n 1
}

detect_safe_right() {
  local dump value
  dump="$(window_dump)" || return 1
  value="$(printf '%s\n' "$dump" | sed -nE 's/.*mStable=Rect\(0,[[:space:]]*[0-9]+[[:space:]]*-[[:space:]]*([0-9]+),[[:space:]]*[0-9]+\).*/\1/p' | head -n 1)"
  if [[ -z "$value" ]]; then
    value="$(printf '%s\n' "$dump" | sed -nE 's/.*stable=\[0,[0-9]+\]\[([0-9]+),[0-9]+\].*/\1/p' | head -n 1)"
  fi
  [[ "$value" =~ ^[0-9]+$ ]] || return 1
  printf '%s\n' "$value"
}

dofun_version() {
  have dumpsys || return 1
  local command_text="dumpsys package $TS18_DOFUN_PACKAGE"
  if root_available; then su -c "$command_text" 2>/dev/null
  else dumpsys package "$TS18_DOFUN_PACKAGE" 2>/dev/null
  fi | sed -nE 's/.*versionName=([^[:space:]]+).*/\1/p' | head -n 1
}

compatibility_mismatches() {
  local physical safe_right version
  physical="$(detect_physical_size 2>/dev/null || true)"
  safe_right="$(detect_safe_right 2>/dev/null || true)"
  version="$(dofun_version 2>/dev/null || true)"
  [[ -n "$physical" && "$physical" != "$TS18_EXPECTED_PHYSICAL_SIZE" ]] && printf 'physical_size=%s expected=%s\n' "$physical" "$TS18_EXPECTED_PHYSICAL_SIZE"
  [[ -n "$safe_right" && "$safe_right" != "$TS18_EXPECTED_SAFE_RIGHT" ]] && printf 'safe_right=%s expected=%s\n' "$safe_right" "$TS18_EXPECTED_SAFE_RIGHT"
  [[ -n "$version" && "$version" != "$TS18_EXPECTED_DOFUN_VERSION" ]] && printf 'dofun_version=%s expected=%s\n' "$version" "$TS18_EXPECTED_DOFUN_VERSION"
}

confirm_mutation_compatibility() {
  local mismatches answer
  mismatches="$(compatibility_mismatches)"
  if [[ -z "$mismatches" ]]; then return 0; fi
  warn "Current device evidence differs from the qualified release profile:"
  printf '%s\n' "$mismatches" >&2
  read -r -p 'Type OVERRIDE to continue donor mutation with this mismatch: ' answer
  [[ "$answer" == "OVERRIDE" ]] || { stop_safety "Compatibility mismatch not overridden"; return 2; }
}

safe_donor_basename() { [[ "$1" =~ ^[0-9]+\.jar$ ]]; }

final_status() {
  local rc="$1"
  case "$rc" in
    0) log "SUCCESS" ;;
    2) log "STOPPED FOR SAFETY" ;;
    130) log "INTERRUPTED" ;;
    *) log "FAILED" ;;
  esac
}
