#!/usr/bin/env bash
# Explicit ephemeral API29 emulator qualification with the actual two app binaries.
set -euo pipefail
[[ $# == 5 ]] || { echo 'Usage: api29-actual-pair-smoke.sh launcher.apk launcher-test.apk auxio-debug.apk auxio-test.apk proof-dir' >&2; exit 64; }
: "${ANDROID_SERIAL:?Select the ephemeral emulator explicitly}"
: "${LAUNCHER_SOURCE_SHA:?Exact launcher source SHA required}"
: "${AUXIO_SOURCE_SHA:?Exact Auxio source SHA required}"
[[ $LAUNCHER_SOURCE_SHA =~ ^[a-f0-9]{40}$ && $AUXIO_SOURCE_SHA =~ ^[a-f0-9]{40}$ ]] || exit 64
[[ $(adb shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]] || { echo 'BLOCKED: emulator required'; exit 77; }
[[ $(adb shell getprop ro.build.version.sdk | tr -d '\r') == 29 ]] || exit 77
for apk in "$1" "$2" "$3" "$4"; do [[ -f $apk ]] || exit 66; done
unzip -p "$1" assets/build-info.properties | grep -Fxq "source_revision=$LAUNCHER_SOURCE_SHA"
proof=$5
mkdir -p "$proof"
# Preserve bounded real callback evidence even when seed/transport assertions fail.
trap 'adb shell run-as com.tw.media.debug touch cache/pair-gap-stop >/dev/null 2>&1 || true; timeout 10s adb shell logcat -d -t 2000 -v monotonic -s TS18Transport:V TS18Media:V > "$proof/callbacks.txt" 2>&1 || true' EXIT
sha256sum "$1" "$2" "$3" "$4" > "$proof/APK-SHA256SUMS.txt"
printf 'launcher_source=%s\nauxio_source=%s\nqualification=API29_EMULATOR_ONLY\n' \
  "$LAUNCHER_SOURCE_SHA" "$AUXIO_SOURCE_SHA" > "$proof/SOURCE-PAIR.txt"
for apk in "$1" "$2" "$3" "$4"; do adb install -r "$apk"; done
# Seed is opt-in, debug-only, emulator-only and refuses an existing persisted queue.
timeout 90s adb shell am instrument -w -e pair_seed 1 -e pair_gaps 1 -e expected_source_sha "$AUXIO_SOURCE_SHA" \
  -e class org.oxycblt.auxio.CrossUidTransitionSeedTest \
  com.tw.media.debug.test/androidx.test.runner.AndroidJUnitRunner > "$proof/seed.txt" 2>&1 &
probe_pid=$!
ready=0
for attempt in $(seq 1 200); do
  if adb shell run-as com.tw.media.debug test -f cache/pair-gap-ready; then ready=1; break; fi
  if ! kill -0 "$probe_pid" 2>/dev/null; then cat "$proof/seed.txt"; exit 1; fi
  sleep 0.1
done
[[ $ready == 1 ]] || { cat "$proof/seed.txt"; echo 'Source gap probe not ready'; exit 1; }
adb shell am instrument -w -e auxio_browser 1 -e auxio_transitions 1 -e auxio_package com.tw.media.debug \
  com.cbkii.ts18launcher.test/com.cbkii.ts18launcher.MediaSessionApi29Instrumentation | tee "$proof/actual-transitions.txt"
grep -Fq 'PASS API29 actual Auxio cross-UID' "$proof/actual-transitions.txt"
grep -Fq 'actual restore-next callbacks' "$proof/actual-transitions.txt"
adb shell run-as com.tw.media.debug touch cache/pair-gap-stop
wait "$probe_pid"
cat "$proof/seed.txt"
grep -Eq 'OK \(1 test\)' "$proof/seed.txt"
printf 'PASS actual Auxio decoder/restore/Next -> actual launcher engine, four forced source-publication gaps, four commits, no gap ACK or passive reprepare\n'
