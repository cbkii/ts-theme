#!/system/bin/sh
[ "$BOOTMODE" = true ] || abort 'Install via the Magisk app, not recovery.'
[ "$API" = 29 ] || abort 'Android 10/API29 required for this exact-device evidence module.'
[ -x /data/adb/magisk/busybox ] || abort 'Magisk BusyBox required.'
for a in sh setsid timeout awk stat od head sha256sum dd readlink find tar gzip; do
  /data/adb/magisk/busybox --list | grep -x "$a" >/dev/null || abort "Magisk BusyBox missing: $a"
done
# Segmented streaming requires dd to read whole blocks from a pipe.
/data/adb/magisk/busybox dd --help 2>&1 | grep -q fullblock || abort 'Magisk BusyBox dd lacks iflag=fullblock.'
D=$(getprop ro.product.device); B=$(getprop ro.product.board)
[ "$D" = s9863a1h10 ] && [ "$B" = uis8581a2h10 ] || abort "Device mismatch: $D / $B"
# Persistent run counts and metadata are in verified Download, never MODPATH.
# Magisk installs the module itself in its usual location; runtime writes none.
set_perm_recursive "$MODPATH" 0 0 0755 0644
for a in post-fs-data.sh service.sh collector.sh observer.sh stream.sh snapshots.sh preflight.sh props.sh record.sh arm-wake.sh guard.sh recover.sh bundle.sh ai-summary.sh action.sh stop.sh verify-apks.sh; do
  set_perm "$MODPATH/$a" 0 0 0755
done
if [ -d /data/adb/modules/ts18_boottrace ] && [ ! -f /data/adb/modules/ts18_boottrace/disable ]; then
  ui_print 'WARNING: legacy ts18_boottrace appears enabled; disable it separately to avoid competing collectors.'
fi
if [ -e /data/adb/service.d/ts18-startup-hook.sh ]; then
  ui_print 'WARNING: old startup-kit service.d hook exists; inspect/disable it separately before benchmarking.'
fi
ui_print 'At most three boot IDs; each has 5-min passive capture and optional screen video.'
ui_print 'No app launches, service calls, permission grants, setting or OEM control mutations.'
ui_print 'Output: verified Download/TS18-Validation-v3.6.0/; 32MB x 3 sampled raw budget.'
ui_print 'Default validation mode; consecutive 45s video segments per boot if storage allows.'
ui_print 'At most five <=20MB compressed parts; raw run folders are preserved.'
ui_print 'No runtime write in /data/adb or /tmp. Action shows status only.'
