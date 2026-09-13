#!/usr/bin/env bash
set -euo pipefail

repo_dir="${1:-.}"
out_dir="${2:-testing-input}"

repo_dir="$(cd "$repo_dir" && pwd)"
mkdir -p "$out_dir"
out_dir="$(cd "$out_dir" && pwd)"

cd "$repo_dir"

test -f launcher/build.gradle.kts || {
  echo "Selected source does not contain the standalone launcher module." >&2
  exit 1
}

: "${RUNNER_TEMP:?RUNNER_TEMP must be set}"
: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT must be set}"

version_name="0.0.0-testing"
version_code="999999"
keystore_file="${TS_THEME_KEYSTORE_FILE:-$RUNNER_TEMP/temporary-testing-validation.jks}"
keystore_password="${KEYSTORE_PASSWORD:-temporary-testing-validation}"
key_alias="${KEY_ALIAS:-temporary-testing-validation}"
key_password="${KEY_PASSWORD:-temporary-testing-validation}"
gradle_cmd="${GRADLE_CMD:-gradle}"

umask 077
rm -rf "$out_dir"/*

if [[ ! -s "$keystore_file" ]]; then
  keytool -genkeypair -noprompt \
    -alias "$key_alias" -keyalg RSA -keysize 2048 -validity 1 \
    -keystore "$keystore_file" \
    -storepass "$keystore_password" -keypass "$key_password" \
    -dname "CN=Temporary testing build"
fi

TS_THEME_KEYSTORE_FILE="$keystore_file" \
KEYSTORE_PASSWORD="$keystore_password" \
KEY_ALIAS="$key_alias" \
KEY_PASSWORD="$key_password" \
  "$gradle_cmd" --no-daemon --stacktrace \
    :launcher:lintDebug :launcher:testDebugUnitTest :launcher:assembleDebug

TS_THEME_KEYSTORE_FILE="$keystore_file" \
KEYSTORE_PASSWORD="$keystore_password" \
KEY_ALIAS="$key_alias" \
KEY_PASSWORD="$key_password" \
  "$gradle_cmd" --no-daemon --stacktrace \
    -PVERSION_NAME="$version_name" -PVERSION_CODE="$version_code" \
    :launcher:assembleRelease

debug_apk="launcher/build/outputs/apk/debug/launcher-debug.apk"
release_apk="launcher/build/outputs/apk/release/launcher-release.apk"
test -s "$debug_apk"
test -s "$release_apk"

# The standalone envelope is intentionally a release-only contract. Debug is
# allowed to be multidex and is not release-qualification evidence.
if [[ -f tools/launcher_apk_check.py ]]; then
  python3 tools/launcher_apk_check.py "$release_apk"
fi

apksigner="${ANDROID_SDK_ROOT}/build-tools/36.0.0/apksigner"
test -x "$apksigner"
"$apksigner" verify --verbose "$release_apk" >/dev/null

cp "$debug_apk" "$out_dir/launcher-debug.apk"
cp "$release_apk" "$out_dir/launcher-release.apk"

field() {
  local key="$1" value="${2:-}"
  value="${value//$'\n'/ }"
  value="${value//$'\r'/ }"
  printf '%s=%s\n' "$key" "$value"
}

{
  field repository "${GITHUB_REPOSITORY:-}"
  field source_kind "${TESTING_SOURCE_KIND:-manual}"
  field requested_source "${TESTING_REQUESTED_SOURCE:-}"
  field source_sha "${TESTING_SOURCE_SHA:-$(git rev-parse HEAD)}"
  field built_sha "$(git rev-parse HEAD)"
  field source_ref "${TESTING_SOURCE_REF:-}"
  field pr_number "${TESTING_PR_NUMBER:-}"
  field pr_head_sha "${TESTING_PR_HEAD_SHA:-}"
  field pr_base_ref "${TESTING_PR_BASE_REF:-}"
  field pr_base_sha "${TESTING_PR_BASE_SHA:-}"
  field trigger_type "${TESTING_TRIGGER_TYPE:-manual}"
  field trigger_actor "${TESTING_TRIGGER_ACTOR:-${GITHUB_ACTOR:-}}"
  field testing_version_name "$version_name"
  field testing_version_code "$version_code"
  field build_run_id "${GITHUB_RUN_ID:-}"
  field build_run_number "${GITHUB_RUN_NUMBER:-}"
  field build_run_url "${TESTING_BUILD_RUN_URL:-https://github.com/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}}"
} > "$out_dir/BUILD_INFO.txt"
