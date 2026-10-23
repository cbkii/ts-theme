#!/usr/bin/env bash
# API29 emulator only. Execute production dumpsys/IPC and MediaSession boundaries.
set -euo pipefail
apk=${1:?debug APK required}
test_apk=${2:?instrumentation APK required}
proof=${3:?proof directory required}
mkdir -p "$proof"
[[ $(adb shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]]
[[ $(adb shell getprop ro.build.version.sdk | tr -d '\r') == 29 ]]
adb root
adb wait-for-device
[[ $(adb shell id -u | tr -d '\r') == 0 ]]
adb install -r "$apk"
adb install -r "$test_apk"
adb shell input keyevent KEYCODE_WAKEUP
adb shell input keyevent 82
adb shell am start -W -n com.cbkii.ts18launcher/.LauncherActivity > "$proof/start.txt"

installed=$(adb shell pm path com.cbkii.ts18launcher | tr -d '\r' | sed -n 's/^package://p')
[[ $installed =~ ^/[A-Za-z0-9_./=+-]+$ ]]
observe() {
  local action=$1 uptime deadline
  uptime=$(adb shell cat /proc/uptime | tr -d '\r')
  uptime=${uptime%%.*}
  [[ $uptime =~ ^[0-9]+$ ]]
  deadline=$((uptime * 1000 + 5000))
  printf 'EPOCH\t1\nREQUEST\t1\t%s\t1\t%s\t0\n' "$deadline" "$action" |
    adb shell "CLASSPATH=$installed /system/bin/app_process /system/bin com.cbkii.ts18launcher.NavTaskBridge serve"
}

# This runs the real production child executable, drainer, reflection and IPC.
# No substitution of ProcessBuilder, dump text or Android/Binder methods.
observe observe | tee "$proof/home-probe.txt"
grep -Eq 'TS18_RESULT 1 OK code=FOREGROUND user=0 task=[1-9][0-9]* component=com.cbkii.ts18launcher/com.cbkii.ts18launcher.LauncherActivity' "$proof/home-probe.txt"
# The cold-consent root method must reach the actual API29 PM boundary and fail
# closed because no signed Maps peer is installed in this isolated emulator.
observe compact-consent | tee "$proof/absent-peer.txt"
grep -Fq 'TS18_RESULT 1 FAIL code=CONSENT_PEER_UNAVAILABLE' "$proof/absent-peer.txt"

adb shell am instrument -w \
  com.cbkii.ts18launcher.test/com.cbkii.ts18launcher.MediaSessionApi29Instrumentation |
  tee "$proof/media-framework.txt"
grep -Fq 'PASS API29 MediaSession/controller' "$proof/media-framework.txt"
adb shell am start -W -a android.settings.SETTINGS > "$proof/settings-start.txt"
observe observe | tee "$proof/foreign-probe.txt"
grep -Eq 'TS18_RESULT 1 OK code=FOREGROUND .*component=com.android.settings/' "$proof/foreign-probe.txt"
adb shell logcat -d -v brief -s 'TS18Nav:*' > "$proof/probe-log.txt"
printf 'PASS real API29 broker/dumpsys/HOME/foreign ownership/absent peer/MediaSession IPC\n'
