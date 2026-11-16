#!/system/bin/sh
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
OUT=$1; RUN=$2
USERID=$(android_user); number "$USERID" || USERID=0
b mkdir -p "$OUT/snapshots" "$OUT/apps"
capture "$OUT/snapshots/context.txt" 2 16384 "$BB" sh -c 'id; cat /proc/self/attr/current; readlink /proc/self/ns/mnt; cat /proc/mounts'
capture "$OUT/snapshots/identity-properties.txt" 2 16384 "$BB" sh "$MODDIR/props.sh"
# Kernel ring can contain private addresses; do not collect unfiltered dmesg.
# Event/boot traces already retain bounded non-kernel startup evidence.
capture "$OUT/snapshots/services.txt" 3 65536 /system/bin/service list
capture "$OUT/snapshots/screenrecord-help.txt" 2 8192 /system/bin/screenrecord --help
capture "$OUT/snapshots/logcat-events-ring.txt" 3 262144 /system/bin/logcat -b events -v threadtime -v monotonic -d -t 1000
capture "$OUT/snapshots/modules.txt" 3 32768 "$BB" sh -c 'for d in /data/adb/modules/*; do [ -d "$d" ] || continue; echo "$d"; cat "$d/module.prop"; for f in disable remove; do [ ! -f "$d/$f" ] || echo "$f"; done; done; for d in /data/adb/post-fs-data.d /data/adb/service.d; do ls -l "$d"; done'
# Hash actual installed packages, including split APKs. Expected source refs are
# labels, not proof that these APK bytes were built from those revisions.
inspect_apps() {
for pkg in com.cbkii.ts18launcher com.tw.media app.organicmaps.incar com.navimods.radio com.tw.radio com.tw.service.xt com.tw.service com.dofun.variety; do
  [ ! -f "$OUT/STOP" ] || exit 0
  [ ! -f "$OUT/apps/$pkg-hash-complete" ] || continue
  capture "$OUT/apps/$pkg-package.txt" 3 131072 /system/bin/dumpsys package "$pkg"
  capture "$OUT/apps/$pkg-paths.txt" 2 8192 /system/bin/pm path --user "$USERID" "$pkg"
  b grep '^package:/' "$OUT/apps/$pkg-paths.txt" | b cut -c9- > "$OUT/apps/$pkg-path-list.txt"
  if [ ! -s "$OUT/apps/$pkg-path-list.txt" ]; then status APK_HASH BLOCKED "$pkg path query not ready; retry later"; continue; fi
  ok=yes
  while IFS= read -r apk; do
    case "$apk" in /data/app/*/*.apk|/system/*/*.apk|/product/*/*.apk|/vendor/*/*.apk)
      hashfile="$OUT/apps/$pkg-apk-$(b basename "$apk").sha256.txt"
      capture "$hashfile" 8 4096 "$BB" sha256sum "$apk"
      [ "$(b cat "$hashfile.rc" 2>/dev/null)" = 0 ] || ok=no;;
      *) ok=no; status APK_HASH BLOCKED "$pkg unrecognised APK path; not opened: $apk";;
    esac
  done < "$OUT/apps/$pkg-path-list.txt"
  [ "$ok" != yes ] || : > "$OUT/apps/$pkg-hash-complete"
done
}
inspect_apps
# Match captured split/base hashes to the supplied immutable TESTING selection.
"$BB" sh "$MODDIR/verify-apks.sh" "$OUT"
i=0
while [ ! -f "$OUT/STOP" ] && [ "$i" -lt 8 ]; do
  case "$i" in 2|5) inspect_apps; "$BB" sh "$MODDIR/verify-apks.sh" "$OUT";; esac
  n=$(up)
  capture "$OUT/snapshots/$n-activity.txt" 2 131072 /system/bin/dumpsys activity activities
  if [ "$i" -eq 0 ] || [ "$i" -eq 3 ] || [ "$i" -eq 7 ]; then
    capture "$OUT/snapshots/$n-recents.txt" 2 98304 /system/bin/dumpsys activity recents
  fi
  capture "$OUT/snapshots/$n-windows.txt" 2 131072 /system/bin/dumpsys window windows
  capture "$OUT/snapshots/$n-window-policy.txt" 2 32768 /system/bin/dumpsys window policy
  capture "$OUT/snapshots/$n-statusbar.txt" 2 32768 /system/bin/dumpsys statusbar
  if [ "$i" -eq 0 ] || [ "$i" -eq 7 ]; then
    capture "$OUT/snapshots/$n-overlays.txt" 2 32768 /system/bin/cmd overlay list --user "$USERID"
  fi
  capture "$OUT/snapshots/$n-media.txt" 2 49152 /system/bin/dumpsys media_session
  capture "$OUT/snapshots/$n-audio.txt" 2 32768 /system/bin/dumpsys audio
  if [ "$i" -eq 0 ] || [ "$i" -eq 4 ] || [ "$i" -eq 7 ]; then
    capture "$OUT/snapshots/$n-app-services.txt" 2 65536 "$BB" sh -c '/system/bin/dumpsys activity services com.tw.media; /system/bin/dumpsys activity services com.cbkii.ts18launcher; /system/bin/dumpsys activity services app.organicmaps.incar'
  fi
  if [ "$i" -eq 0 ] || [ "$i" -eq 7 ]; then capture "$OUT/snapshots/$n-processes.txt" 2 24576 /system/bin/ps -A; fi
  # Discrete stdout/ring approach survives a failed continuous file writer.
  if [ "$i" -eq 0 ] || [ "$i" -eq 7 ]; then capture "$OUT/snapshots/$n-logcat-events.txt" 2 32768 /system/bin/logcat -b events -v threadtime -v monotonic -d -t 300; fi
  if [ "$i" -eq 0 ] || [ "$i" -eq 4 ] || [ "$i" -eq 7 ]; then
    capture "$OUT/snapshots/$n-display.txt" 2 49152 /system/bin/dumpsys display
    capture "$OUT/snapshots/$n-location.txt" 3 24576 "$BB" sh -c '/system/bin/dumpsys location 2>&1 | grep -Ei "provider|gnss|gps|request|enabled|active|listener|status|last fix|ttff" | head -n 95 | sed -E "s/(-?[0-9]{1,3}\.[0-9]{4,})/[DECIMAL_REDACTED]/g"'
    capture "$OUT/snapshots/$n-power.txt" 2 32768 /system/bin/dumpsys power
    capture "$OUT/snapshots/$n-mountinfo.txt" 2 32768 "$BB" cat /proc/self/mountinfo
  fi
  i=$((i+1)); b sleep 32
done
