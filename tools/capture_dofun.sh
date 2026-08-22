#!/usr/bin/env bash
# Read-only current DoFun launcher capture for Termux on CB's rooted TS18.

status=0
stage="initialisation"

finish() {
    original_status=$?
    if [[ $original_status -eq 130 ]]; then
        printf '%s\n' 'INTERRUPTED' >&2
    fi
}
trap finish EXIT
trap 'exit 130' INT TERM HUP

for required in su timeout sha256sum date sed head id grep content; do
    if ! command -v "$required" >/dev/null 2>&1; then
        printf 'ERROR: required command missing: %s\nFAILED\n' "$required" >&2
        exit 1
    fi
done

stage="root identity"
root_identity="$(timeout 10 su -c id 2>&1)"
status=$?
if [[ $status -ne 0 || "$root_identity" != *"uid=0"* ]]; then
    printf 'STOP: root identity was not established (%s): %s\nSTOPPED FOR SAFETY\n' "$status" "$root_identity" >&2
    exit 2
fi

stage="package path"
package_output="$(timeout 15 su -c 'pm path com.dofun.variety' 2>&1)"
status=$?
if [[ $status -ne 0 ]]; then
    printf 'ERROR: pm path failed (%s): %s\nFAILED\n' "$status" "$package_output" >&2
    exit 1
fi
apk_path="$(printf '%s\n' "$package_output" | sed -n 's/^package://p' | head -n 1)"
if [[ "$apk_path" != /data/app/*/base.apk && "$apk_path" != /system/priv-app/*/*.apk ]]; then
    printf 'STOP: unexpected DoFun APK path: %s\nSTOPPED FOR SAFETY\n' "$apk_path" >&2
    exit 2
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
output_dir="/storage/emulated/0/Download/ts18-dashboard-theme/dofun-$timestamp"
case "$output_dir" in
    /storage/emulated/0/Download/ts18-dashboard-theme/dofun-[0-9]*Z) ;;
    *)
        printf 'STOP: unsafe output path: %s\nSTOPPED FOR SAFETY\n' "$output_dir" >&2
        exit 2
        ;;
esac

stage="read-only copies"
timeout 20 su -c "mkdir -p '$output_dir' && cp -p '$apk_path' '$output_dir/com.dofun.variety-base.apk' && dumpsys package com.dofun.variety > '$output_dir/package-com.dofun.variety.txt' && chmod 0644 '$output_dir/'*"
status=$?
if [[ $status -ne 0 ]]; then
    printf 'ERROR: capture failed during %s (%s)\nFAILED\n' "$stage" "$status" >&2
    exit 1
fi

stage="bounded media observations"
timeout 20 content query \
    --uri content://com.dofun.variety.ExportedProvider/hotseat_app_music \
    > "$output_dir/provider-selection-app-uid.txt" 2>&1 || true
timeout 20 su -c \
    "content query --uri content://com.dofun.variety.ExportedProvider/hotseat_app_music" \
    > "$output_dir/provider-selection-root-observation.txt" 2>&1 || true
timeout 20 su -c "dumpsys media_session" \
    > "$output_dir/media-session.txt" 2>&1 || true
timeout 20 su -c \
    "dumpsys notification --noredact | grep -i -E 'com.dofun.variety|com.tw.media|com.tw.music|NotifyService|notification listener'" \
    > "$output_dir/notification-media-filtered.txt" 2>&1 || true
timeout 20 su -c \
    "dumpsys activity services com.dofun.variety | grep -i -E 'NotifyService|MediaSourceService|RemoteMediaService|music|media|listener'" \
    > "$output_dir/dofun-media-services-filtered.txt" 2>&1 || true
timeout 20 su -c \
    "cmd package resolve-activity --user 0 --brief -c android.intent.category.LAUNCHER -a android.intent.action.MAIN com.tw.media" \
    > "$output_dir/resolve-com.tw.media.txt" 2>&1 || true
chmod 0644 "$output_dir"/*.txt 2>/dev/null || true

stage="hash and context"
{
    printf 'captured_utc=%s\n' "$timestamp"
    printf 'caller_identity=%s\n' "$(id 2>&1)"
    printf 'root_identity=%s\n' "$root_identity"
    printf 'source_apk=%s\n' "$apk_path"
    sha256sum "$output_dir/com.dofun.variety-base.apk"
} > "$output_dir/capture-summary.txt"
status=$?
if [[ $status -ne 0 ]]; then
    printf 'ERROR: summary failed (%s)\nFAILED\n' "$status" >&2
    exit 1
fi

printf 'Output: %s\nSUCCESS\n' "$output_dir"
