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
# Generated, non-distributable fixture: no app code and a fresh CI-only signer.
# The root reader exercises real API29 PM/process/Os/XML boundaries. The ordinary
# launcher must reject its untrusted signer, despite its correct package/capability.
sdk=${ANDROID_SDK_ROOT:?Android SDK required}
build_tools="$sdk/build-tools/36.0.0"
"$build_tools/aapt2" link --manifest tests/fixtures/cold-maps-peer/AndroidManifest.xml \
  -I "$sdk/platforms/android-29/android.jar" --version-code 1 --version-name ci-fixture \
  -o "$proof/cold-peer-unsigned.apk"
keytool -genkeypair -noprompt -alias fixture -keyalg RSA -keysize 2048 -validity 1 \
  -keystore "$proof/fixture.jks" -storepass ci-fixture-only -keypass ci-fixture-only \
  -dname 'CN=Non-distributable cold peer fixture' > /dev/null 2>&1
"$build_tools/apksigner" sign --ks "$proof/fixture.jks" --ks-key-alias fixture \
  --ks-pass pass:ci-fixture-only --key-pass pass:ci-fixture-only \
  --out "$proof/cold-peer.apk" "$proof/cold-peer-unsigned.apk"
adb install "$proof/cold-peer.apk"
observe compact-consent | tee "$proof/unset-consent.txt"
grep -Fq 'OK code=COMPACT_CONSENT_ENABLED user=0 package=app.organicmaps.incar' "$proof/unset-consent.txt"
peer_uid=$(adb shell cmd package list packages -U app.organicmaps.incar | tr -d '\r' | sed -n 's/^package:app.organicmaps.incar uid:\([0-9]*\)$/\1/p')
[[ $peer_uid =~ ^[0-9]+$ && $peer_uid -ge 10000 ]]
prefs=/data/user/0/app.organicmaps.incar/shared_prefs
adb shell "mkdir -p $prefs; chown $peer_uid:$peer_uid $prefs; chmod 700 $prefs"
printf '<map><boolean name="InCarCompactStartup" value="false"/></map>\n' |
  adb shell "cat > $prefs/OrganicMapsPrefs.xml; chown $peer_uid:$peer_uid $prefs/OrganicMapsPrefs.xml; chmod 600 $prefs/OrganicMapsPrefs.xml"
observe compact-consent | tee "$proof/off-consent.txt"
grep -Fq 'OK code=COMPACT_CONSENT_DISABLED user=0 package=app.organicmaps.incar' "$proof/off-consent.txt"
adb shell "cp $prefs/OrganicMapsPrefs.xml $prefs/OrganicMapsPrefs.xml.bak"
observe compact-consent | tee "$proof/backup-consent.txt"
grep -Fq 'FAIL code=CONSENT_FILE_UNSTABLE' "$proof/backup-consent.txt"
adb shell "rm $prefs/OrganicMapsPrefs.xml.bak"
adb shell am start -W -n app.organicmaps.incar/android.app.Activity > "$proof/live-peer-start.txt"
observe compact-consent | tee "$proof/live-consent.txt"
grep -Fq 'FAIL code=CONSENT_PEER_RUNNING' "$proof/live-consent.txt"
adb shell am instrument -w -e cold_consent 1 \
  com.cbkii.ts18launcher.test/com.cbkii.ts18launcher.MediaSessionApi29Instrumentation |
  tee "$proof/untrusted-peer.txt"
grep -Fq 'PASS untrusted cold-consent peer rejected' "$proof/untrusted-peer.txt"
# Ephemeral fixture signing material and APKs are never uploaded as evidence.
rm "$proof/fixture.jks" "$proof/cold-peer-unsigned.apk" "$proof/cold-peer.apk"
adb shell logcat -d -v brief -s 'TS18Nav:*' > "$proof/probe-log.txt"
printf 'PASS real API29 broker/dumpsys/HOME/foreign ownership/cold consent/untrusted peer/MediaSession IPC\n'
