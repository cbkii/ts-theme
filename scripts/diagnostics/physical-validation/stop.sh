#!/system/bin/sh
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
BOOTID=$(b cat /proc/sys/kernel/random/boot_id 2>/dev/null)
ROOT=$(download_probe "$BOOTID") || { echo 'BLOCKED: Download not currently verified'; exit 1; }
active=$(b cat "$ROOT/.active" 2>/dev/null)
case "$active" in "$ROOT"/boot-1-*|"$ROOT"/boot-2-*|"$ROOT"/boot-3-*) ;; *) echo 'No recognised active run'; exit 1;; esac
[ -d "$active/.control" ] && [ ! -L "$active" ] || exit 1
[ "$(b cat "$active/boot-id.txt" 2>/dev/null)" = "$BOOTID" ] || { echo 'Not current boot'; exit 1; }
printf 'user\n' > "$active/.control/request-stop"
echo 'Stop requested; run finalisation remains bounded.'
