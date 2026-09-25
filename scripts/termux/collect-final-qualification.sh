#!/data/data/com.termux/files/usr/bin/bash
# Read-only final TS18 snapshot. Run after the physical action being qualified.
# Use trace-home-performance.sh during the action for low-overhead timing evidence.

set -u
umask 077

out_base=/storage/emulated/0/Download/ts-theme
probe_root=1
while (( $# )); do
  case "$1" in
    --out-base) (( $# >= 2 )) || exit 64; out_base="$2"; shift 2 ;;
    --no-root) probe_root=0; shift ;;
    -h|--help)
      printf 'Usage: %s [--out-base ABSOLUTE_DIR] [--no-root]\n' "$0"
      exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; exit 64 ;;
  esac
done
case "$out_base" in /*) ;; *) printf 'Output base must be absolute\n' >&2; exit 64 ;; esac
case "$out_base" in *'..'*|*$'\n'*|*$'\r'*) exit 64 ;; esac

termux_bin="${TS18_TERMUX_BIN:-${PREFIX:-/data/data/com.termux/files/usr}/bin}"
android_path="${TS18_ANDROID_PATH:-/system/bin:/system/xbin:/vendor/bin:/product/bin}"
export PATH="$termux_bin:$android_path"
case "$android_path" in *[!A-Za-z0-9._:/-]*) printf 'Invalid Android PATH\n' >&2; exit 64 ;; esac
for tool in bash timeout date mkdir sha256sum zip unzip find sort xargs cat grep tail wc head mv; do
  command -v "$tool" >/dev/null 2>&1 || {
    printf 'Missing prerequisite: %s\n' "$tool" >&2
    exit 127
  }
done

stamp="$(date +%Y%m%d-%H%M%S)-$$"
out="$out_base/final-$stamp"
mkdir -p -- "$out" || exit 1
status="$out/STATUS.tsv"
printf 'surface\tclass\tresult\texit_status\n' >"$status"
fails=0
blocked=0
required_blocked=0
warns=0
max_capture_bytes=4194304

record() {
  printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >>"$status"
  case "$3" in
    FAIL) fails=$((fails + 1)) ;;
    BLOCKED)
      blocked=$((blocked + 1))
      if [[ "$2" == REQUIRED ]]; then required_blocked=$((required_blocked + 1)); fi ;;
    WARN) warns=$((warns + 1)) ;;
  esac
}

capture() {
  local name="$1" class="$2" seconds="$3" rc=0 truncated=0 bytes
  shift 3
  mkdir -p -- "$(dirname -- "$out/$name")"
  timeout -k 1 "$seconds" "$@" >"$out/$name" 2>&1 || rc=$?
  bytes="$(wc -c <"$out/$name")"
  if (( bytes > max_capture_bytes )); then
    head -c "$max_capture_bytes" "$out/$name" >"$out/$name.tmp" || return 1
    mv -- "$out/$name.tmp" "$out/$name" || return 1
    printf '\n# TRUNCATED: original_bytes=%s limit_bytes=%s\n' \
      "$bytes" "$max_capture_bytes" >>"$out/$name"
    truncated=1
  fi
  printf '\n# exit_status=%s\n' "$rc" >>"$out/$name"
  if (( truncated )); then
    if [[ "$class" == REQUIRED ]]; then record "$name" "$class" FAIL "$rc;TRUNCATED"
    else record "$name" "$class" WARN "$rc;TRUNCATED"; fi
  elif (( rc == 0 )); then record "$name" "$class" PASS 0
  elif [[ "$class" == REQUIRED ]]; then record "$name" "$class" FAIL "$rc"
  else record "$name" "$class" WARN "$rc"; fi
}

current_user() {
  local value
  value="$(timeout -k 1 4 cmd activity get-current-user 2>/dev/null)" || value=''
  if [[ "$value" =~ ^[[:space:]]*(Current[[:space:]]user:[[:space:]]*)?([0-9]+)[[:space:]]*$ ]]; then
    printf '%s\n' "${BASH_REMATCH[2]}"; return 0
  fi
  value="$(timeout -k 1 4 am get-current-user 2>/dev/null)" || value=''
  if [[ "$value" =~ ^[[:space:]]*(Current[[:space:]]user:[[:space:]]*)?([0-9]+)[[:space:]]*$ ]]; then
    printf '%s\n' "${BASH_REMATCH[2]}"; return 0
  fi
  return 1
}

capture identity/id.txt REQUIRED 4 id
capture identity/selinux.txt OPTIONAL 4 id -Z
capture identity/build.txt REQUIRED 5 getprop ro.build.fingerprint
capture identity/api.txt REQUIRED 5 getprop ro.build.version.sdk
capture identity/mount-namespace.txt OPTIONAL 4 readlink /proc/self/ns/mnt

user="$(current_user)" || user=''
if [[ -n "$user" ]]; then
  printf '%s\n' "$user" >"$out/identity/android-user.txt"
  record identity/android-user.txt REQUIRED PASS 0
else
  printf 'Unresolved: neither API returned exactly one Android user number\n' >"$out/identity/android-user.txt"
  record identity/android-user.txt REQUIRED BLOCKED 1
fi

capture display/wm-size.txt REQUIRED 6 wm size
capture display/wm-density.txt OPTIONAL 6 wm density
capture window/activity.txt REQUIRED 10 dumpsys activity activities
capture window/wm.txt REQUIRED 10 dumpsys window windows
capture media/sessions.txt REQUIRED 10 dumpsys media_session
capture media/audio.txt OPTIONAL 10 dumpsys audio
capture storage/mounts.txt REQUIRED 4 cat /proc/mounts
capture logs/launcher.txt OPTIONAL 8 logcat -d -t 800 -v threadtime -s TS18Media:I TS18Nav:I TS18Launcher:I '*:S'

if [[ -n "$user" ]]; then
  for package in com.cbkii.ts18launcher app.organicmaps.incar com.tw.media com.navimods.radio com.tw.radio; do
    capture "packages/$package.txt" OPTIONAL 8 dumpsys package "$package"
  done
else
  printf 'Package queries require a valid Android user identity\n' >"$out/packages-BLOCKED.txt"
  record packages-BLOCKED.txt OPTIONAL BLOCKED 1
fi

if (( probe_root )); then
  if command -v su >/dev/null 2>&1; then
    capture identity/root.txt OPTIONAL 5 su -c \
      "PATH=$android_path; export PATH; id; id -Z; readlink /proc/self/ns/mnt"
    if [[ "$(tail -n 1 "$out/identity/root.txt")" == '# exit_status=0' ]] \
        && ! grep -Eq '^uid=0([[:space:]]|$)' "$out/identity/root.txt"; then
      record identity/root-authority.txt OPTIONAL BLOCKED 1
    fi
  else
    printf 'Root transport unavailable\n' >"$out/identity/root.txt"
    record identity/root.txt OPTIONAL BLOCKED 127
  fi
else
  printf 'Root probe explicitly skipped\n' >"$out/identity/root.txt"
  record identity/root.txt OPTIONAL BLOCKED 0
fi

printf 'fails=%s\nblocked=%s\nwarns=%s\n' "$fails" "$blocked" "$warns" >"$out/SUMMARY.txt"
manifest_tmp="$out_base/.manifest-$stamp.tmp"
(cd "$out" && find . -type f ! -name MANIFEST.sha256 ! -name MANIFEST_VERIFY.txt -print0 \
  | sort -z | xargs -0 sha256sum) >"$manifest_tmp" || exit 1
mv -- "$manifest_tmp" "$out/MANIFEST.sha256" || exit 1
(cd "$out" && sha256sum -c MANIFEST.sha256 >MANIFEST_VERIFY.txt) || exit 1
archive="$out.zip"
(cd "$out_base" && zip -q -r "$archive" "${out##*/}") || exit 1
unzip -tq "$archive" >"$out/ARCHIVE_VERIFY.txt" || exit 1
sha256sum "$archive" >"$archive.sha256"
printf 'Output: %s\nArchive: %s\n' "$out" "$archive"
(( fails == 0 && required_blocked == 0 ))
