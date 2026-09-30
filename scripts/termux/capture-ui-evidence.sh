#!/data/data/com.termux/files/usr/bin/bash
# Bounded TS18 UI evidence capture.
# Default mode is one manual screenshot. --watch listens only from the current TS18 app-log boundary
# for a small whitelist of high-value events, rejects stale records and coalesces duplicate triggers.

set -u
umask 077

out_base=/storage/emulated/0/Download/ts-theme/ui-captures
label=''
watch_seconds=0
while (( $# )); do
  case "$1" in
    --out-base) (( $# >= 2 )) || exit 64; out_base="$2"; shift 2 ;;
    --watch) (( $# >= 2 )) || exit 64; watch_seconds="$2"; shift 2 ;;
    -h|--help)
      printf 'Usage: %s [--out-base ABSOLUTE_DIR] LABEL\n' "$0"
      printf '       %s [--out-base ABSOLUTE_DIR] --watch SECONDS\n' "$0"
      exit 0 ;;
    *)
      if [[ -n "$label" ]]; then printf 'Only one LABEL is allowed\n' >&2; exit 64; fi
      label="$1"; shift ;;
  esac
done

case "$watch_seconds" in ''|*[!0-9]*) printf 'Watch duration must be 0 or 10..180 seconds\n' >&2; exit 64 ;; esac
if (( watch_seconds > 0 && (watch_seconds < 10 || watch_seconds > 180) )); then
  printf 'Watch duration must be 10..180 seconds\n' >&2
  exit 64
fi
if (( watch_seconds == 0 )); then
  [[ -n "$label" ]] || { printf 'LABEL is required in manual mode\n' >&2; exit 64; }
else
  [[ -z "$label" ]] || { printf 'LABEL is not used with --watch\n' >&2; exit 64; }
fi
case "$out_base" in /*) ;; *) printf 'Output base must be absolute\n' >&2; exit 64 ;; esac
case "$out_base" in *'..'*|*$'\n'*|*$'\r'*) exit 64 ;; esac

termux_bin="${TS18_TERMUX_BIN:-${PREFIX:-/data/data/com.termux/files/usr}/bin}"
android_path="${TS18_ANDROID_PATH:-/system/bin:/system/xbin:/vendor/bin:/product/bin}"
export PATH="$termux_bin:$android_path"
for tool in date mkdir sha256sum timeout cat awk sed tr find sort xargs rm mv grep; do
  command -v "$tool" >/dev/null 2>&1 || { printf 'Missing prerequisite: %s\n' "$tool" >&2; exit 127; }
done

uptime_ms() { awk '{printf "%.0f\n", $1 * 1000}' /proc/uptime; }
safe_name() {
  local value
  value="$(printf '%s' "$1" | tr -cs 'A-Za-z0-9._-' '_' | sed 's/^_*//;s/_*$//')"
  [[ -n "$value" ]] || value=event
  printf '%s' "$value"
}
seal_dir() {
  local dir="$1" work="$2"
  (cd "$dir" && find . -type f ! -name MANIFEST.sha256 ! -name MANIFEST_VERIFY.txt -print0 \
    | sort -z | xargs -0 sha256sum) >"$work" || return 1
  mv -- "$work" "$dir/MANIFEST.sha256" || return 1
  (cd "$dir" && sha256sum -c MANIFEST.sha256 >MANIFEST_VERIFY.txt)
}

capture_one() {
  local dir="$1" display_label="$2" trigger="$3" source_epoch="$4" source_line="$5"
  local png="$dir/screen.png" meta="$dir/METADATA.txt"
  local wall_start start_ms method rc end_ms wall_end
  mkdir -p -- "$dir" || return 1
  wall_start="$(date -Iseconds 2>/dev/null || date '+%Y-%m-%dT%H:%M:%S%z')"
  start_ms="$(uptime_ms)"
  method=direct
  rc=0

  if timeout -k 1 6 /system/bin/screencap -p "$png" 2>"$dir/screencap.stderr"; then
    rc=0
  else
    rc=$?
    rm -f -- "$png"
    if command -v su >/dev/null 2>&1; then
      method=root-stdout
      if timeout -k 1 6 su -c 'exec /system/bin/screencap -p' >"$png" 2>"$dir/screencap.stderr"; then
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
    printf 'label=%s\n' "$display_label"
    printf 'trigger=%s\n' "$trigger"
    printf 'source_event_epoch=%s\n' "$source_epoch"
    printf 'source_event=%s\n' "$source_line"
    printf 'capture_request_wall=%s\n' "$wall_start"
    printf 'capture_complete_wall=%s\n' "$wall_end"
    printf 'capture_request_uptime_ms=%s\n' "$start_ms"
    printf 'capture_complete_uptime_ms=%s\n' "$end_ms"
    printf 'capture_method=%s\n' "$method"
    printf 'capture_exit=%s\n' "$rc"
  } >"$meta"

  # Correlation snapshots happen only after screencap. They are bounded one-shot evidence, never
  # part of the low-overhead performance sampler.
  timeout -k 1 6 /system/bin/wm size >"$dir/wm-size.txt" 2>&1 || true
  timeout -k 1 8 /system/bin/dumpsys activity activities >"$dir/activity.txt" 2>&1 || true
  if [[ "$trigger" == nav-* || "$trigger" == manual ]]; then
    timeout -k 1 8 /system/bin/dumpsys window windows >"$dir/window.txt" 2>&1 || true
  fi
  timeout -k 1 5 /system/bin/logcat -d -t 80 -v epoch -s TS18Media:I TS18Nav:I TS18Launcher:I '*:S' \
    >"$dir/events.txt" 2>&1 || true

  seal_dir "$dir" "$out_base/.ui-manifest-$$-$RANDOM.work" || return 1
  [[ -s "$png" ]]
}

if (( watch_seconds == 0 )); then
  safe_label="$(safe_name "$label")"
  stamp="$(date +%Y%m%d-%H%M%S)-$$"
  out="$out_base/$stamp-$safe_label"
  mkdir -p -- "$out_base" || exit 1
  if capture_one "$out" "$label" manual manual "manual operator trigger"; then
    printf 'Captured: %s\n' "$out"
    exit 0
  fi
  printf 'Screenshot unavailable; correlation text retained at: %s\n' "$out" >&2
  exit 1
fi

command -v logcat >/dev/null 2>&1 || { printf 'logcat command unavailable\n' >&2; exit 127; }
mkdir -p -- "$out_base" || exit 1
watch_stamp="$(date +%Y%m%d-%H%M%S)-$$"
session="$out_base/watch-$watch_stamp"
mkdir -p -- "$session" || exit 1
events="$session/WATCH_EVENTS.tsv"
printf 'source_epoch\treceived_uptime_ms\tcategory\tdecision\tdetail\n' >"$events"
declare -A last_capture_ms
captures=0

classify_event() {
  local line="$1"
  case "$line" in
    *'drawer/first-draw'*) printf 'drawer-first-draw' ;;
    *'startup/complete'*) printf 'startup-complete' ;;
    *'startup/source-timeout'*|*'startup/source-launch-failed'*) printf 'startup-failure' ;;
    *'fullscreen task='*) printf 'nav-fullscreen' ;;
    *'fullscreen helper failed'*|*'native navigation failed'*) printf 'nav-failure' ;;
    *) return 1 ;;
  esac
}

# -T 1 establishes a current logcat boundary. The age check below is an additional guard against
# vendor/logcat timestamp oddities and prevents old buffered events from triggering screenshots.
while IFS= read -r line; do
  category="$(classify_event "$line" 2>/dev/null || true)"
  [[ -n "$category" ]] || continue
  source_epoch="${line%% *}"
  if [[ ! "$source_epoch" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    printf 'unknown\t%s\t%s\tREJECT\tunparseable event timestamp\n' "$(uptime_ms)" "$category" >>"$events"
    continue
  fi
  source_sec="${source_epoch%%.*}"
  now_sec="$(date +%s)"
  age=$(( now_sec - source_sec ))
  received_ms="$(uptime_ms)"
  if (( age < -1 || age > 2 )); then
    printf '%s\t%s\t%s\tREJECT\tstale age_seconds=%s\n' "$source_epoch" "$received_ms" "$category" "$age" >>"$events"
    continue
  fi
  previous="${last_capture_ms[$category]:-0}"
  if (( previous > 0 && received_ms - previous < 2000 )); then
    printf '%s\t%s\t%s\tCOALESCED\tduplicate within 2000ms\n' "$source_epoch" "$received_ms" "$category" >>"$events"
    continue
  fi
  last_capture_ms[$category]="$received_ms"
  captures=$((captures + 1))
  printf '%s\t%s\t%s\tCAPTURE\taccepted current-boundary event\n' "$source_epoch" "$received_ms" "$category" >>"$events"
  safe_category="$(safe_name "$category")"
  capture_dir="$session/$(printf '%02d' "$captures")-$safe_category"
  capture_one "$capture_dir" "$category" "$category" "$source_epoch" "$line" || true
done < <(timeout -k 1 "$watch_seconds" /system/bin/logcat -T 1 -v epoch \
  -s TS18Media:I TS18Nav:I TS18Launcher:I '*:S' 2>"$session/logcat.stderr" || true)

printf 'watch_seconds=%s\ncaptures=%s\n' "$watch_seconds" "$captures" >"$session/WATCH_SUMMARY.txt"
seal_dir "$session" "$out_base/.ui-watch-manifest-$watch_stamp.work" || exit 1
printf 'Watch complete: %s (captures=%s)\n' "$session" "$captures"
exit 0
