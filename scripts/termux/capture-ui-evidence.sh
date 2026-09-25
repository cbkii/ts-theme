#!/data/data/com.termux/files/usr/bin/bash
# Bounded, manually-triggered TS18 UI evidence capture.
# Use at the exact physical moment named in the qualification runbook.

set -u
umask 077

out_base=/storage/emulated/0/Download/ts-theme/ui-captures
label=''
while (( $# )); do
  case "$1" in
    --out-base) (( $# >= 2 )) || exit 64; out_base="$2"; shift 2 ;;
    -h|--help)
      printf 'Usage: %s [--out-base ABSOLUTE_DIR] LABEL\n' "$0"
      exit 0 ;;
    *)
      if [[ -n "$label" ]]; then printf 'Only one LABEL is allowed\n' >&2; exit 64; fi
      label="$1"; shift ;;
  esac
done
[[ -n "$label" ]] || { printf 'LABEL is required\n' >&2; exit 64; }
case "$out_base" in /*) ;; *) printf 'Output base must be absolute\n' >&2; exit 64 ;; esac
case "$out_base" in *'..'*|*$'\n'*|*$'\r'*) exit 64 ;; esac

safe_label="$(printf '%s' "$label" | tr -cs 'A-Za-z0-9._-' '_' | sed 's/^_*//;s/_*$//')"
[[ -n "$safe_label" ]] || safe_label=event
termux_bin="${TS18_TERMUX_BIN:-${PREFIX:-/data/data/com.termux/files/usr}/bin}"
android_path="${TS18_ANDROID_PATH:-/system/bin:/system/xbin:/vendor/bin:/product/bin}"
export PATH="$termux_bin:$android_path"
for tool in date mkdir sha256sum timeout cat awk sed tr find sort xargs rm; do
  command -v "$tool" >/dev/null 2>&1 || { printf 'Missing prerequisite: %s\n' "$tool" >&2; exit 127; }
done

stamp="$(date +%Y%m%d-%H%M%S)-$$"
out="$out_base/$stamp-$safe_label"
mkdir -p -- "$out" || exit 1
meta="$out/METADATA.txt"
png="$out/screen.png"

uptime_ms() { awk '{printf "%.0f\n", $1 * 1000}' /proc/uptime; }
wall_start="$(date -Iseconds 2>/dev/null || date '+%Y-%m-%dT%H:%M:%S%z')"
start_ms="$(uptime_ms)"
method=direct
rc=0

if timeout -k 1 6 /system/bin/screencap -p "$png" 2>"$out/screencap.stderr"; then
  rc=0
else
  rc=$?
  rm -f -- "$png"
  if command -v su >/dev/null 2>&1; then
    method=root-stdout
    if timeout -k 1 6 su -c 'exec /system/bin/screencap -p' >"$png" 2>"$out/screencap.stderr"; then
      rc=0
    else
      rc=$?
    fi
  fi
fi
end_ms="$(uptime_ms)"
wall_end="$(date -Iseconds 2>/dev/null || date '+%Y-%m-%dT%H:%M:%S%z')"

if [[ ! -s "$png" ]]; then
  rm -f -- "$png"
  (( rc == 0 )) && rc=1
fi

{
  printf 'label=%s\n' "$label"
  printf 'safe_label=%s\n' "$safe_label"
  printf 'wall_start=%s\n' "$wall_start"
  printf 'wall_end=%s\n' "$wall_end"
  printf 'uptime_start_ms=%s\n' "$start_ms"
  printf 'uptime_end_ms=%s\n' "$end_ms"
  printf 'capture_method=%s\n' "$method"
  printf 'capture_exit=%s\n' "$rc"
} >"$meta"

# One-time correlation snapshots are acceptable here because this script is manually triggered,
# not part of the low-overhead performance loop.
timeout -k 1 6 /system/bin/wm size >"$out/wm-size.txt" 2>&1 || true
timeout -k 1 8 /system/bin/dumpsys activity activities >"$out/activity.txt" 2>&1 || true
timeout -k 1 8 /system/bin/dumpsys window windows >"$out/window.txt" 2>&1 || true
timeout -k 1 5 /system/bin/logcat -d -t 160 -v epoch -s TS18Media:I TS18Nav:I TS18Launcher:I '*:S' \
  >"$out/events.txt" 2>&1 || true

(
  cd "$out" || exit 1
  find . -maxdepth 1 -type f ! -name MANIFEST.sha256 ! -name MANIFEST_VERIFY.txt -print0 \
    | sort -z | xargs -0 sha256sum >MANIFEST.sha256
  sha256sum -c MANIFEST.sha256 >MANIFEST_VERIFY.txt
) || exit 1

if [[ -s "$png" ]]; then
  printf 'Captured: %s\n' "$out"
  exit 0
fi
printf 'Screenshot unavailable; correlation text retained at: %s\n' "$out" >&2
exit 1
