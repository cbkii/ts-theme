#!/usr/bin/env bash
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$SCRIPT_DIR/lib/common.sh"
. "$SCRIPT_DIR/lib/dofun.sh"

APK=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk) [[ $# -ge 2 ]] || { fail "--apk needs a path"; exit 1; }; APK="$2"; shift 2 ;;
    *) fail "Unknown argument: $1"; exit 1 ;;
  esac
done

prepare_dirs || { fail "Cannot prepare output directories"; exit 1; }
STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || printf 'unknown')"
REPORT="$TS18_EXPORT_ROOT/preflight-$STAMP.txt"
TMP="$TS18_PRIVATE_ROOT/preflight-$STAMP.tmp"
: > "$TMP" || { fail "Cannot create private report"; exit 1; }

PHYSICAL="$(detect_physical_size 2>/dev/null || true)"
SAFE_RIGHT="$(detect_safe_right 2>/dev/null || true)"
DOFUN_VERSION="$(dofun_version 2>/dev/null || true)"
MISMATCHES="$(compatibility_mismatches)"

{
  printf 'timestamp=%s\n' "$STAMP"
  printf 'termux_identity='; id 2>&1
  printf 'android_release=%s\n' "$(getprop ro.build.version.release 2>/dev/null)"
  printf 'sdk=%s\n' "$(getprop ro.build.version.sdk 2>/dev/null)"
  printf 'tw_version=%s\n' "$(getprop ro.tw.version 2>/dev/null)"
  printf 'fota_device=%s\n' "$(getprop ro.fota.device 2>/dev/null)"
  printf 'physical_size=%s\n' "${PHYSICAL:-unknown}"
  printf 'observed_safe_right=%s\n' "${SAFE_RIGHT:-unknown}"
  printf 'expected_safe_right=%s\n' "$TS18_EXPECTED_SAFE_RIGHT"
  printf 'dofun_version=%s\n' "${DOFUN_VERSION:-unknown}"
  printf '%s\n' '--- display ---'
  wm size 2>&1 || true
  wm density 2>&1 || true
  window_dump 2>&1 | grep -E 'mStable|stable|DisplayFrames|cur=' | head -n 30 || true
  printf '%s\n' '--- home ---'
  resolve_home
  printf '%s\n' '--- dofun package ---'
  dumpsys package "$TS18_DOFUN_PACKAGE" 2>&1 | grep -E 'versionName=|versionCode=|userId=|codePath=' | head -n 30 || true
  printf '%s\n' '--- legacy theme package ---'
  dumpsys package "$TS18_LEGACY_THEME_PACKAGE" 2>&1 | grep -E 'versionName=|versionCode=|codePath=' | head -n 10 || true
  if [[ -n "$APK" ]]; then
    printf 'apk_path=%s\n' "$APK"
    if [[ -f "$APK" ]]; then printf 'apk_sha256=%s\n' "$(sha256_file "$APK")"; else printf 'apk_state=missing\n'; fi
  fi
  if root_available; then
    printf 'root=yes\n'
    APP_PA="$(find_app_pa || true)"
    printf 'app_p_a=%s\n' "$APP_PA"
    if [[ -n "$APP_PA" ]]; then
      printf '%s\n' '--- ranked donor candidates ---'
      donor_candidates "$APP_PA" | head -n 40
    fi
  else
    printf 'root=no\n'
  fi
  if [[ -n "$MISMATCHES" ]]; then
    printf '%s\n' '--- compatibility mismatches ---'
    printf '%s\n' "$MISMATCHES"
  fi
} >> "$TMP"

cp -- "$TMP" "$REPORT" || { fail "Cannot export preflight report"; exit 1; }
rm -f -- "$TMP"
log "Preflight report: $REPORT"
if [[ -n "$MISMATCHES" ]]; then warn "Release-profile mismatch detected; donor mutation will require explicit OVERRIDE."; fi
if [[ -z "$SAFE_RIGHT" ]]; then warn "Safe-right was not found in the inspected window scope; x=1225 remains the last-observed device baseline until physically rechecked."; fi
final_status 0
