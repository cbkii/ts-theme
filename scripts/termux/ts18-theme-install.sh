#!/usr/bin/env bash
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$SCRIPT_DIR/lib/common.sh"
. "$SCRIPT_DIR/lib/dofun.sh"

APK=""
NON_INTERACTIVE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk) [[ $# -ge 2 ]] || { fail "--apk needs a path"; exit 1; }; APK="$2"; shift 2 ;;
    --non-interactive) NON_INTERACTIVE=1; shift ;;
    *) fail "Unknown argument: $1"; exit 1 ;;
  esac
done
[[ -n "$APK" && -f "$APK" ]] || { fail "Provide --apk with the release APK"; exit 1; }
prepare_dirs || { fail "Cannot prepare installer directories"; exit 1; }
APK_HASH="$(stage_apk_fixed "$APK")" || exit 1
log "Verified/staged APK SHA-256: $APK_HASH"

run_preflight() { bash "$SCRIPT_DIR/ts18-theme-preflight.sh" --apk "$APK"; }

direct_install() {
  root_available || { stop_safety "Direct PackageManager install requires working root in this Termux context"; return 2; }
  log "Installing the staged independently-signed package; DoFun remains HOME."
  su -c "pm install -r '$TS18_FIXED_STAGE'" || return 1
  su -c "dumpsys package '$TS18_THEME_PACKAGE'" 2>/dev/null | grep -E 'versionName=|versionCode=|codePath=|userId=' | head -n 20
  read -r -p 'Restart only DoFun now? [y/N] ' ANSWER
  if [[ "$ANSWER" =~ ^[Yy]$ ]]; then su -c 'am force-stop com.dofun.variety'; fi
  log "Open DoFun Theme/downloaded themes and check for sfp_cbk_black."
}

prepare_udisk() {
  local target="/storage/emulated/0/theme/$(basename -- "$APK")" actual
  mkdir -p /storage/emulated/0/theme || return 1
  cp -- "$APK" "$target" || return 1
  actual="$(sha256_file "$target")" || return 1
  [[ "$actual" == "$APK_HASH" ]] || { fail "U-disk copy hash mismatch"; return 1; }
  log "Prepared: $target"
  log "Touchscreen: Theme -> downloaded/local themes -> long-press gear/profile/circular-arrow area -> QR window -> U disk import."
  log "Historical 4PDA guidance recommends turning Internet off for this import attempt. This script does not change networking."
}

donor_install() {
  root_available || { stop_safety "Root is required for donor substitution"; return 2; }
  local app_pa donor_list=() pick donor stamp back donor_sha backup_sha new_sha
  app_pa="$(find_app_pa)" || { stop_safety "DoFun app_p_a not found in the inspected root namespace"; return 2; }
  [[ -f "$TS18_FIXED_STAGE" ]] || { fail "Fixed staged APK is missing"; return 1; }
  mapfile -t donor_list < <(candidate_donor_basenames "$app_pa")
  [[ ${#donor_list[@]} -gt 0 ]] || { stop_safety "No imported sfp_* donor JAR was found in p.l"; return 2; }
  log "Candidate donor JARs (prefer the FYD/TS10S tile when its record is visible in p.l):"
  for i in "${!donor_list[@]}"; do printf '  %d) %s\n' "$((i+1))" "${donor_list[$i]}" >&2; done
  read -r -p 'Select donor: ' pick
  [[ "$pick" =~ ^[0-9]+$ ]] || { stop_safety "Invalid donor selection"; return 2; }
  pick=$((pick-1)); [[ $pick -ge 0 && $pick -lt ${#donor_list[@]} ]] || { stop_safety "Donor selection out of range"; return 2; }
  donor="${donor_list[$pick]}"
  safe_donor_basename "$donor" || { stop_safety "Unsafe donor basename"; return 2; }
  su -c "test -f '$app_pa/$donor' && test ! -L '$app_pa/$donor'" || { stop_safety "Donor is missing, not regular, or a symlink"; return 2; }
  stamp="$(date +%Y%m%d-%H%M%S)"; back="$TS18_EXPORT_ROOT/backup-$stamp"
  mkdir -p -- "$back" || return 1
  su -c "cat '$app_pa/$donor'" > "$back/donor-original.jar" || { fail "Cannot export donor backup"; return 1; }
  su -c "cat '$app_pa/p.l'" > "$back/p.l" || { fail "Cannot export p.l backup"; return 1; }
  printf '%s\n' "$donor" > "$back/target-basename.txt"
  donor_sha="$(su -c "sha256sum '$app_pa/$donor'" | awk '{print $1}')"
  backup_sha="$(sha256_file "$back/donor-original.jar")"
  [[ "$donor_sha" == "$backup_sha" ]] || { stop_safety "Backup hash does not match live donor"; return 2; }
  printf '%s\n' "$donor_sha" > "$back/original-sha256.txt"
  su -c "ls -lZ '$app_pa/$donor'; stat '$app_pa/$donor'" > "$back/donor-metadata.txt" 2>&1 || warn "Could not capture all donor metadata"
  dumpsys package "$TS18_DOFUN_PACKAGE" > "$back/dofun-package.txt" 2>&1 || warn "Could not capture full DoFun package summary"
  printf 'apk_sha256=%s\ntheme_package=%s\nplugin_id=sfp_cbk_black\n' "$APK_HASH" "$TS18_THEME_PACKAGE" > "$back/install-metadata.txt"
  log "Verified rollback set: $back"
  read -r -p 'Type REPLACE to overwrite this donor in place: ' CONFIRM
  [[ "$CONFIRM" == "REPLACE" ]] || { log "Cancelled"; return 0; }
  trap 'warn "Interrupted during donor operation; run ts18-theme-rollback.sh with the verified backup."; exit 130' INT TERM HUP
  su -c "am force-stop com.dofun.variety; cat '$TS18_FIXED_STAGE' > '$app_pa/$donor'; sync" || { fail "Donor replacement command failed"; return 1; }
  new_sha="$(su -c "sha256sum '$app_pa/$donor'" | awk '{print $1}')"
  if [[ "$new_sha" != "$APK_HASH" ]]; then
    warn "Replacement verification failed; attempting immediate restoration."
    su -c "cat '$back/donor-original.jar' > '$app_pa/$donor'; sync" || { stop_safety "Automatic restoration failed; use standalone rollback before launching DoFun"; return 2; }
    local restored
    restored="$(su -c "sha256sum '$app_pa/$donor'" | awk '{print $1}')"
    [[ "$restored" == "$donor_sha" ]] || { stop_safety "Automatic restoration hash mismatch"; return 2; }
    fail "Replacement rejected and original donor restored"
    return 1
  fi
  trap - INT TERM HUP
  log "Replacement hash verified. p.l was not changed. The donor tile/preview may still show its original identity."
  log "Reboot/restart DoFun only when you are ready to test the selected donor tile."
}

validation_record() {
  local stamp file
  stamp="$(date +%Y%m%d-%H%M%S)"; file="$TS18_EXPORT_ROOT/physical-validation-$stamp.txt"
  {
    printf 'apk_sha256=%s\n' "$APK_HASH"
    for item in 'theme appeared' 'theme applied' 'right edge/date/map unobstructed' 'map touch/pan/zoom' 'media previous/play-next exactly once' 'radio previous/next semantics observed' 'launcher restart' 'Android reboot' 'cold boot/full power removal' 'ACC sleep/wake'; do
      read -r -p "$item [pass/fail/unverified]: " value
      printf '%s=%s\n' "$item" "$value"
    done
  } > "$file"
  log "Recorded user observations: $file"
}

if [[ $NON_INTERACTIVE -eq 1 ]]; then run_preflight; exit $?; fi
while true; do
  printf '\n1) Read-only preflight\n2) Direct package install/discovery\n3) Prepare 4PDA U-disk import\n4) Rooted donor substitution\n5) Rollback donor\n6) Record physical validation\n0) Exit\n' >&2
  read -r -p 'Select: ' choice
  case "$choice" in
    1) run_preflight ;;
    2) direct_install ;;
    3) prepare_udisk ;;
    4) donor_install ;;
    5) bash "$SCRIPT_DIR/ts18-theme-rollback.sh" ;;
    6) validation_record ;;
    0) final_status 0; exit 0 ;;
    *) warn "Unknown selection" ;;
  esac
done
