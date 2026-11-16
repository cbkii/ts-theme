#!/system/bin/sh
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
echo 'TS18 Execution Preflight v3.6.0 — STATUS ONLY'
BOOTID=$(b cat /proc/sys/kernel/random/boot_id 2>/dev/null)
ROOT=$(download_probe "$BOOTID") || { echo 'Download not yet proven in this caller namespace; no capture started by Action.'; exit 0; }
[ ! -f "$ROOT/.campaign-state" ] || { printf 'Latest boot | attempts committed: '; b cat "$ROOT/.campaign-state"; }
[ -f "$ROOT/.campaign-state" ] || echo 'No committed run; next boot with verified Download starts run 1.'
printf 'Last result: '; b cat "$ROOT/.last-status" 2>/dev/null || echo 'None'
echo "Output: $ROOT"
for f in "$ROOT"/READY-TO-UPLOAD-boot-*.txt; do [ ! -f "$f" ] || b cat "$f"; done
if [ -r "$ROOT/.active" ]; then
  latest=$(b cat "$ROOT/.active")
  case "$latest" in "$ROOT"/boot-*)
    [ ! -r "$latest/APK-MATCH.tsv" ] || { echo 'Installed TESTING-byte match (observed):'; b cat "$latest/APK-MATCH.tsv"; };;
  esac
fi
active=$(b cat "$ROOT/.active" 2>/dev/null)
case "$active" in "$ROOT"/boot-*) [ ! -f "$active/CAPTURE-IN-PROGRESS.txt" ] || b cat "$active/CAPTURE-IN-PROGRESS.txt";; esac
echo 'Three distinct boot IDs, maximum three committed runs. No automatic reboot.'
echo 'Default VALIDATION: up to seven consecutive 45s video segments per boot if 32MB run headroom allows.'
echo 'No app driving. A new boot ID is necessary for another run; ACC without reboot is not a new run.'
echo 'Module need not write /data/adb to self-disable; counter is in Download.'
