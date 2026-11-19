#!/system/bin/sh
# No activity/service initiation, no generic service call, no settings put, no mutation.
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
OUT=$1; RUN=$2
case "$OUT" in /storage/emulated/0/Download/TS18-Validation-v3.6.0/boot-*|/sdcard/Download/TS18-Validation-v3.6.0/boot-*|/mnt/user/0/primary/Download/TS18-Validation-v3.6.0/boot-*) ;; *) exit 2;; esac
number "$RUN" && [ "$RUN" -le 3 ] && [ "$RUN" -ge 1 ] || exit 2
USERID=$(android_user)
if ! number "$USERID"; then USERID=0; USER_FALLBACK=LAST_VERIFIED_TS18_USER0; else USER_FALLBACK=LIVE_CURRENT_USER; fi
P="$OUT/preflight"
b mkdir -p -m 755 "$P" "$P/identity" "$P/packages" "$P/m2" "$P/n3" "$P/topway" "$P/startup" "$P/gnss" "$P/performance" "$P/policy" || exit 3
printf 'run=%s\nboot_id=%s\nstarted_uptime=%s\nmode=%s\nandroid_user=%s\nuser_source=%s\n' "$RUN" "$(b cat /proc/sys/kernel/random/boot_id)" "$(stamp)" PASSIVE_5MIN "$USERID" "$USER_FALLBACK" > "$P/PROBE_RUN.txt"
# Commands must be literal and read-only; never run input/su/properties writes.
probe() { name=$1; sec=$2; cap=$3; shift 3; [ ! -f "$OUT/STOP" ] || return 1; capture "$P/$name.txt" "$sec" "$cap" "$@"; }
sub() { name=$1; sec=$2; cap=$3; shift 3; [ ! -f "$OUT/STOP" ] || return 1; capture "$P/$name.txt" "$sec" "$cap" "$@"; }
record() { printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >> "$P/CAPABILITY_MATRIX.tsv"; }
printf 'id\tstatus\tartifact\tinterpretation\n' > "$P/CAPABILITY_MATRIX.tsv"

# PR#31 and M2 implementation need APK/user/signing/exact HOME first.
probe identity/context 2 8192 "$BB" sh -c 'id; cat /proc/self/attr/current; readlink /proc/self/ns/mnt; cat /proc/sys/kernel/random/boot_id; cat /proc/uptime'
probe identity/build 2 12288 "$BB" sh "$MODDIR/props.sh"
probe identity/users 3 12288 /system/bin/pm list users
probe identity/home 3 8192 /system/bin/cmd package resolve-activity --brief --user "$USERID" -a android.intent.action.MAIN -c android.intent.category.HOME
probe identity/default-home 3 8192 /system/bin/pm resolve-activity --brief --user "$USERID" -a android.intent.action.MAIN -c android.intent.category.HOME
probe identity/package-list 4 98304 /system/bin/pm list packages -U
probe identity/package-installed 3 65536 /system/bin/pm list packages -3
probe identity/display-size 2 4096 /system/bin/wm size
probe identity/display-density 2 4096 /system/bin/wm density
probe identity/selinux 2 4096 /system/bin/getenforce
probe identity/date 2 4096 /system/bin/date -u
record HOME_OWNER OBSERVE identity/home.txt 'Resolved launcher is NOT proof of display or first frame'
record DEVICE_ID OBSERVE identity/build.txt 'Pinned API29 and exact device must be checked before any OEM adapter'

# Package identities/manifest components are runtime evidence, not downloaded binaries.
PKGS='com.cbkii.ts18launcher com.tw.media com.navimods.radio com.tw.radio com.tw.music com.tw.service com.tw.service.xt com.tw.core com.tw.coreservice com.dofun.variety com.dofun.carsetting com.tw.bt com.tw.eq com.android.systemui com.android.documentsui app.organicmaps.incar app.organicmaps net.osmand.plus com.google.android.apps.maps ru.yandex.yandexnavi com.waze'
for pkg in $PKGS; do
  [ ! -f "$OUT/STOP" ] || break
  case "$pkg" in *[!a-zA-Z0-9._-]*|'') continue;; esac
  probe "packages/$pkg.path" 2 8192 /system/bin/pm path --user "$USERID" "$pkg"
  if b grep -q '^package:/' "$P/packages/$pkg.path.txt"; then
    probe "packages/$pkg.dump" 3 98304 /system/bin/dumpsys package "$pkg"
    printf '%s\tOBSERVED_PATH\t%s\n' "$pkg" "packages/$pkg.path.txt" >> "$P/PACKAGE_SCOPE.tsv"
  else
    printf '%s\tNOT_RESOLVED_IN_PM_PATH_SCOPE\t%s\n' "$pkg" "packages/$pkg.path.txt" >> "$P/PACKAGE_SCOPE.tsv"
  fi
done
record SOURCE_INSTALLS OBSERVE packages/ 'pm path was checked per candidate; unsuccessful pm path is not universal absence'

# Source discovery: Android media, native controls, system grants.
probe m2/active-sessions 3 196608 /system/bin/dumpsys media_session
probe m2/audio-focus 3 131072 /system/bin/dumpsys audio
probe m2/notification-access 3 16384 /system/bin/settings get secure enabled_notification_listeners
# Avoid dumping notification titles, message bodies, contact names or tokens.
probe m2/auxio-appops 3 16384 /system/bin/appops get --user "$USERID" com.tw.media
probe m2/launcher-appops 3 16384 /system/bin/appops get --user "$USERID" com.cbkii.ts18launcher
probe m2/navradio-appops 3 16384 /system/bin/appops get --user "$USERID" com.navimods.radio
probe m2/service-discovery 3 98304 /system/bin/dumpsys activity services
record M2_LISTENER OBSERVE m2/notification-access.txt 'Actual listener connection and callback delivery still require process evidence'
record M2_SESSIONS OBSERVE m2/active-sessions.txt 'Only sessions active during capture; do not infer cold-start contracts'
record M2_COLD_ADAPTER OBSERVE m2/service-discovery.txt 'Activity service dumps do not establish exported MediaBrowser access to launcher UID'
record M2_TRANSPORT NOT_RUN m2/active-sessions.txt 'No synthetic Play/Pause/Next commands are sent by this observational module'
record WP1_NEXT_20 USER_OBSERVATION m2/active-sessions.txt 'Count 20 distinct committed source identities during user presses; ACK or title change alone is insufficient'
record WP1_PREVIOUS_REWIND USER_OBSERVATION m2/active-sessions.txt 'User presses Previous >3s into actual playback; correlate TS18Transport/session identity and absence of passive PREPARE'
record WP4_SHUFFLE_DURABILITY USER_OBSERVATION m2/active-sessions.txt 'Raw OFF/ALL shuffle before library hydration; test post-restart queue scope/order, source and position'
record WP4_MEDIA_HANDOVER USER_AUDIBLE m2/audio-focus.txt 'Music-to-Radio and Radio-to-Music one-tap plus audible output; no auto foreground priming'

# Native Topway broker and control discovery: never invoke an OEM Binder method.
probe topway/service-list 3 131072 /system/bin/service list
probe topway/service-probe 3 24576 /system/bin/dumpsys activity service com.tw.service.xt
probe topway/radio-package 3 98304 /system/bin/dumpsys package com.tw.radio
probe topway/xtservice-package 3 98304 /system/bin/dumpsys package com.tw.service.xt
probe topway/related-properties 2 8192 "$BB" sh "$MODDIR/props.sh"
record XT_SERVICE_DESCRIPTOR INSPECT topway/service-list.txt 'Do not issue service call or assume bind permission based on listed service'
record TWUTIL_NATIVE INSPECT topway/xtservice-package.txt 'Static framework API presence must be verified in exact APK bytes and caller domain'
record TOPWAY_RADIO_CONTROL NOT_RUN topway/radio-package.txt 'Tuner/source mutation intentionally excluded without exact contract and approval'
record ILLUMINATION_READONLY PENDING topway/related-properties.txt 'Property state does not prove headlight event or true actuator'

# Navigation: installed Activities, real task, touch target and windows (N0/N1/N2).
probe n3/activity 3 131072 /system/bin/dumpsys activity activities
probe n3/recents 3 98304 /system/bin/dumpsys activity recents
probe n3/activity-stacks-help 2 12288 /system/bin/am help
probe n3/window 3 131072 /system/bin/dumpsys window windows
probe n3/window-policy 3 65536 /system/bin/dumpsys window policy
probe n3/display 3 131072 /system/bin/dumpsys display
probe n3/input 3 131072 /system/bin/dumpsys input
probe n3/surfaceflinger-list 3 98304 /system/bin/dumpsys SurfaceFlinger --list
probe n3/window-help 2 16384 /system/bin/wm help
probe n3/platform-features 2 32768 /system/bin/pm list features
probe n3/geo-activity-resolve 3 32768 /system/bin/cmd package query-activities --brief --user "$USERID" -a android.intent.action.VIEW -d 'geo:0,0'
probe n3/map-navigation-props 2 8192 /system/bin/settings get global enable_freeform_support
probe n3/force-resizable 2 8192 /system/bin/settings get global force_resizable_activities
record N0_N2_TASK_STATE OBSERVE n3/activity.txt 'Need task/display/window correspondence; one failed focus parse is inconclusive'
record WP2_FULLSCREEN TASK_GEOMETRY n3/activity.txt 'Compare current task user/display/windowMode/effective full bounds and empty compact override independently'
record WP3_RETAINED_RECENTS USER_OBSERVATION n3/recents.txt 'Back once from fullscreen map root; if screen exits while task remains in Recents, one explicit Retry/Fullscreen must reuse exact task ID; do not auto-retry'
record WP3_TASK_EVENTS OBSERVE n3/recents.txt 'Correlate am_finish_activity/am_destroy_activity/am_create_activity/am_task_removed from dedicated events stream and task snapshots'


# GNSS/location to support Organic Maps, without leaking coordinate fixes from dumpsys location.
probe gnss/location-flags 2 8192 /system/bin/settings get secure location_mode
probe gnss/gps-appops 3 16384 /system/bin/appops get --user "$USERID" app.organicmaps.incar
probe gnss/location-summary 3 24576 "$BB" sh -c '/system/bin/dumpsys location 2>&1 | grep -Ei "provider|gnss|gps|request|enabled|active|registered|listener|status|last fix|ttff" | head -n 110 | sed -E "s/(-?[0-9]{1,3}\\.[0-9]{4,})/[COORD_REDACTED]/g"'
# Vendor ignores location --help and dumps coordinates; the filtered summary above is authoritative.
record GNSS_TTFF NOT_RUN gnss/location-summary.txt 'Provider/requests are not proof of first valid fix delivered to target app'

# Boot, power, storage, persistent writers and performance budgets.
probe startup/bootstat 2 24576 "$BB" sh -c '/system/bin/getprop | grep -E "^\[(ro[.]boottime[.]|sys[.]boot_completed|dev[.]bootcomplete|init[.]svc[.](zygote|surfaceflinger|bootanim))" | head -n 100'
probe startup/proc-memory 2 32768 "$BB" cat /proc/meminfo
probe startup/proc-pressure 2 16384 "$BB" sh -c 'for p in cpu io memory; do echo "=== $p ==="; cat "/proc/pressure/$p" 2>&1; done'
probe startup/boot-stats 3 32768 /system/bin/dumpsys activity processes
probe startup/power 3 65536 /system/bin/dumpsys power
probe startup/battery 3 32768 /system/bin/dumpsys battery
probe startup/lmk 3 32768 /system/bin/dumpsys meminfo com.cbkii.ts18launcher
probe startup/organic-meminfo 3 32768 /system/bin/dumpsys meminfo app.organicmaps.incar
probe startup/media-meminfo 3 32768 /system/bin/dumpsys meminfo com.tw.media
probe startup/gfxinfo 3 65536 /system/bin/dumpsys gfxinfo com.cbkii.ts18launcher
probe startup/app-standby 3 32768 /system/bin/dumpsys deviceidle
probe startup/modules 2 16384 "$BB" sh -c 'for d in /data/adb/modules/*; do [ -d "$d" ] || continue; printf "%s " "${d##*/}"; [ -r "$d/module.prop" ] && grep -E "^(id|version|versionCode)=" "$d/module.prop" | tr "\\n" " "; for m in disable remove update; do [ -f "$d/$m" ] && printf " %s=yes" "$m"; done; echo; done'
probe startup/boot-writers 2 32768 "$BB" sh -c 'for d in /data/adb/post-fs-data.d /data/adb/service.d; do echo "$d"; for p in "$d"/*; do [ -f "$p" ] || continue; ls -ld "$p"; sha256sum "$p"; done; done'
probe startup/mounts 3 49152 "$BB" sh -c 'cat /proc/self/mountinfo | grep -E "(/data |/storage/|/mnt/runtime|/data/adb)" | head -n 100'
probe startup/thermal 2 24576 /system/bin/dumpsys thermalservice
record STARTUP_OWNER OBSERVE startup/boot-stats.txt 'Android boot vs HOME vs navigation initialisation must be measured separately'
record WAKE_ACC_ONLY USER_OBSERVATION startup/power.txt 'A genuine ACC event requires user observation; a new boot ID proves reboot only'
record CPU_JANK OBSERVE startup/gfxinfo.txt 'Observational only; no profile or synthetic touch injected'
record BOOT_WRITER_CONFLICT INSPECT startup/boot-writers.txt 'Names/hashes only; scripts are not executed or copied'
for pkg in com.cbkii.ts18launcher com.tw.media app.organicmaps.incar; do
  sub "startup/$pkg-identity-summary" 4 16384 "$BB" sh -c '/system/bin/dumpsys package "$1" | grep -E "versionCode=|versionName=|userId=|codePath=|primaryCpuAbi=|signatures=|User [0-9]+:|metaData=|PREPARE_HOME|LauncherPreparationActivity|CompactStartup"' sh "$pkg"
done
record CURRENT_REPO_SHA NOT_RUN packages/com.cbkii.ts18launcher.path.txt 'Installed APK SHA cannot alone establish its Git commit; match build metadata'

# Diagnostic authority boundaries and final status: completion is NOT a device PASS.
{
 echo '# TS18 runtime validation source and knowledge-gap register'
 echo '# Generated automatically; no app/MediaSession/Topway tuner/virtual display was started or mutated.'
 echo '# Status OBSERVE means a probe was attempted; inspect the corresponding .rc before claiming valid output.'
 echo '# Current exact-device physical behaviour and permission checks from app UID remain distinct.'
 echo '# Details: CAPABILITY_MATRIX.tsv, package scope, snapshot metadata and run log.'
 echo 'M2: pin Music and Radio by exact installed package/usable MediaSession token; prepare cold adapters only after source-specific runtime evidence.'
 echo 'Native Topway radio: only inspect service descriptors and APK package metadata; ordinary launcher Binder access UNKNOWN.'
 echo 'GPS: provider/location and permissions collected where available, true fresh first fix to navigation NOT_RUN.'
 echo 'Lifecycle: boot trace observable; ACC and physical fullscreen interactions NOT_RUN without a genuine event.'
 echo 'Source rights are not part of device diagnostics.'
 echo 'Privacy: video and logs can capture coordinates, names, notifications and tokens. Review before sharing.'
} > "$P/KNOWLEDGE_GAPS.txt"
printf 'completed_uptime=%s\nstatus=COMMANDS_ATTEMPTED_NOT_VALIDATED\n' "$(stamp)" >> "$P/PROBE_RUN.txt"

printf 'status=COMPLETED_SCOPE per_probe_results=CAPABILITY_MATRIX.tsv\n' > "$P/COMPLETE.txt"
