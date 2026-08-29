#!/usr/bin/env bash
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$SCRIPT_DIR/lib/common.sh"
. "$SCRIPT_DIR/lib/dofun.sh"

prepare_dirs || { fail "Cannot prepare output directories"; exit 1; }
root_available || { stop_safety "Root is required for donor rollback"; exit 2; }

mapfile -t BACKUPS < <(find "$TS18_EXPORT_ROOT" -maxdepth 1 -type d -name 'backup-*' -print 2>/dev/null | sort -r)
[[ ${#BACKUPS[@]} -gt 0 ]] || { fail "No rollback sets found under $TS18_EXPORT_ROOT"; exit 1; }
printf 'Rollback sets:\n'
for i in "${!BACKUPS[@]}"; do printf '  %d) %s\n' "$((i+1))" "${BACKUPS[$i]}"; done
read -r -p 'Select rollback set: ' PICK
[[ "$PICK" =~ ^[0-9]+$ ]] || { stop_safety "Invalid selection"; exit 2; }
INDEX=$((PICK-1))
[[ $INDEX -ge 0 && $INDEX -lt ${#BACKUPS[@]} ]] || { stop_safety "Selection out of range"; exit 2; }
BACK="${BACKUPS[$INDEX]}"
safe_local_path "$BACK" || { stop_safety "Unsafe rollback-set path"; exit 2; }
[[ -f "$BACK/target-basename.txt" && -f "$BACK/donor-original.jar" && -f "$BACK/original-sha256.txt" ]] || { stop_safety "Rollback set is incomplete"; exit 2; }
BASENAME="$(cat "$BACK/target-basename.txt")"
safe_donor_basename "$BASENAME" || { stop_safety "Unsafe donor basename in rollback set"; exit 2; }
APP_PA="$(find_app_pa)" || { stop_safety "Current DoFun app_p_a/p.l not found"; exit 2; }
[[ "$APP_PA" == "/data/user/0/com.dofun.variety/app_p_a" || "$APP_PA" == "/data/data/com.dofun.variety/app_p_a" ]] || { stop_safety "Unexpected DoFun plug-in directory"; exit 2; }
TARGET="$APP_PA/$BASENAME"
EXPECTED="$(cat "$BACK/original-sha256.txt")"
[[ "$EXPECTED" =~ ^[0-9a-fA-F]{64}$ ]] || { stop_safety "Invalid saved donor hash"; exit 2; }
su -c "test -f '$TARGET' && test ! -L '$TARGET'" || { stop_safety "Current rollback target is missing, non-regular or a symlink"; exit 2; }
BACKUP_ACTUAL="$(sha256_file "$BACK/donor-original.jar")"
[[ "$BACKUP_ACTUAL" == "$EXPECTED" ]] || { stop_safety "Saved donor backup no longer matches its recorded hash"; exit 2; }

log "Restoring $TARGET from $BACK"
read -r -p 'Type RESTORE to continue: ' CONFIRM
[[ "$CONFIRM" == "RESTORE" ]] || { log "Cancelled"; final_status 0; exit 0; }

su -c 'am force-stop com.dofun.variety' || { fail "Unable to force-stop DoFun"; exit 1; }
su -c "cat '$BACK/donor-original.jar' > '$TARGET'; sync" || { fail "Root restore command failed"; exit 1; }
ACTUAL="$(su -c "sha256sum '$TARGET'" | awk '{print $1}')"
[[ "$ACTUAL" == "$EXPECTED" ]] || { stop_safety "Restored donor hash does not match backup"; exit 2; }
log "Rollback verified. p.l was not modified by this toolkit."
final_status 0
