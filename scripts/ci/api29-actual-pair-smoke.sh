#!/usr/bin/env bash
# Explicit ephemeral API29 emulator qualification with the actual two app binaries.
set -euo pipefail
[[ $# == 5 ]] || { echo 'Usage: api29-actual-pair-smoke.sh launcher.apk launcher-test.apk auxio-debug.apk auxio-test.apk proof-dir' >&2; exit 64; }
: "${ANDROID_SERIAL:?Select the ephemeral emulator explicitly}"
: "${LAUNCHER_SOURCE_SHA:?Exact launcher source SHA required}"
: "${AUXIO_SOURCE_SHA:?Exact Auxio source SHA required}"
[[ $LAUNCHER_SOURCE_SHA =~ ^[a-f0-9]{40}$ && $AUXIO_SOURCE_SHA =~ ^[a-f0-9]{40}$ ]] || exit 64
bounded_adb() { timeout --kill-after=5s 20s adb "$@"; }
stage() { printf 'actual-pair stage=%s\n' "$1"; }
stop_probe() { timeout --kill-after=2s 5s adb shell "run-as com.tw.media.debug /system/bin/sh -c ': > cache/pair-gap-stop'"; }
stage identity
[[ $(bounded_adb shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]] || { echo 'BLOCKED: emulator required'; exit 77; }
[[ $(bounded_adb shell getprop ro.build.version.sdk | tr -d '\r') == 29 ]] || exit 77
for apk in "$1" "$2" "$3" "$4"; do [[ -f $apk ]] || exit 66; done
unzip -p "$1" assets/build-info.properties | grep -Fxq "source_revision=$LAUNCHER_SOURCE_SHA"
proof=$5
mkdir -p "$proof"
# Preserve bounded real callback evidence even when seed/transport assertions fail.
trap 'stop_probe >/dev/null 2>&1 || true; timeout --kill-after=2s 10s adb shell logcat -d -t 2000 -v monotonic -s TS18Transport:V TS18Media:V AndroidRuntime:E > "$proof/callbacks.txt" 2>&1 || true; timeout --kill-after=2s 10s adb shell dumpsys activity processes > "$proof/processes.txt" 2>&1 || true' EXIT
sha256sum "$1" "$2" "$3" "$4" > "$proof/APK-SHA256SUMS.txt"
printf 'launcher_source=%s\nauxio_source=%s\nqualification=API29_EMULATOR_ONLY\n' \
  "$LAUNCHER_SOURCE_SHA" "$AUXIO_SOURCE_SHA" > "$proof/SOURCE-PAIR.txt"
stage install
# API29's streamed second install stalled before the probe; use push/pm installation.
for apk in "$1" "$2" "$3" "$4"; do timeout --kill-after=5s 60s adb install --no-streaming -r "$apk"; done
# Seed is opt-in, debug-only, emulator-only and refuses an existing persisted queue.
stage source-gap-probe
# `test` and `:` are Android shell builtins, not executables for run-as to exec.
bounded_adb shell 'run-as com.tw.media.debug /system/bin/sh -c "exit 0"'
timeout --kill-after=5s 90s adb shell am instrument -w -e pair_seed 1 -e pair_gaps 1 -e expected_source_sha "$AUXIO_SOURCE_SHA" \
  -e class org.oxycblt.auxio.CrossUidTransitionSeedTest \
  com.tw.media.debug.test/androidx.test.runner.AndroidJUnitRunner > "$proof/seed.txt" 2>&1 &
probe_pid=$!
ready=0
for _ in $(seq 1 200); do
  if bounded_adb shell "run-as com.tw.media.debug /system/bin/sh -c '[ -f cache/pair-gap-ready ]'"; then
    ready=1; break
  else
    ready_rc=$?
    [[ $ready_rc == 1 ]] || { cat "$proof/seed.txt"; echo 'BLOCKED: peer marker command failed'; exit "$ready_rc"; }
  fi
  if ! kill -0 "$probe_pid" 2>/dev/null; then cat "$proof/seed.txt"; exit 1; fi
  sleep 0.1
done
[[ $ready == 1 ]] || { cat "$proof/seed.txt"; echo 'Source gap probe not ready'; exit 1; }
stage launcher-transitions
timeout --kill-after=5s 60s adb shell am instrument -w -e auxio_browser 1 -e auxio_transitions 1 -e auxio_package com.tw.media.debug \
  com.cbkii.ts18launcher.test/com.cbkii.ts18launcher.MediaSessionApi29Instrumentation | tee "$proof/actual-transitions.txt"
grep -Fq 'PASS API29 actual Auxio cross-UID' "$proof/actual-transitions.txt"
grep -Fq 'actual restore-next callbacks' "$proof/actual-transitions.txt"
stage source-settlement
stop_probe
wait "$probe_pid"
cat "$proof/seed.txt"
grep -Eq 'OK \(1 test\)' "$proof/seed.txt"
printf 'PASS actual Auxio decoder/restore/Next -> actual launcher engine, four forced source-publication gaps, four commits, no gap ACK or passive reprepare\n'
stage complete
