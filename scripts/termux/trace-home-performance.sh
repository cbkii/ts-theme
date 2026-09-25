#!/data/data/com.termux/files/usr/bin/bash
# Bounded, read-only TS18 HOME trace. No dumpsys, screenshots or polling through su.
# Run while reproducing one Apps, media or navigation transition on the device.

set -u
umask 077

duration="${1:-60}"
case "$duration" in ''|*[!0-9]*) printf 'Duration must be 15..180 seconds\n' >&2; exit 64 ;; esac
if (( duration < 15 || duration > 180 )); then
  printf 'Duration must be 15..180 seconds\n' >&2
  exit 64
fi

out_base=/storage/emulated/0/Download/ts-theme
stamp="$(date +%Y%m%d-%H%M%S)-$$"
out="$out_base/home-trace-$stamp"
mkdir -p -- "$out" || exit 1
status="$out/STATUS.tsv"
printf 'surface\tstatus\tdetail\n' >"$status"
printf 'elapsed_ms\tpackage\tpid\tstart_ticks\tutime_ticks\tstime_ticks\trss_kib\tthreads\tfd_count\tread_bytes\twrite_bytes\n' >"$out/process.tsv"
log_pid=''
samples=0

finish() {
  local result=$?
  trap - EXIT INT TERM HUP
  if [[ -n "$log_pid" ]]; then
    kill -TERM "$log_pid" 2>/dev/null || true
    wait "$log_pid" 2>/dev/null || true
  fi
  if (( samples == 0 )); then
    printf 'process\tFAIL\tno samples\n' >>"$status"
    result=1
  else
    printf 'process\tPASS\tsamples=%s\n' "$samples" >>"$status"
  fi
  if [[ -s "$out/events.txt" ]]; then
    printf 'events\tPASS\tcurrent logcat boundary; inspect timestamps\n' >>"$status"
  else
    printf 'events\tWARN\tno events or logcat unavailable\n' >>"$status"
  fi
  (cd "$out" && sha256sum STATUS.tsv process.tsv events.txt >MANIFEST.sha256 && sha256sum -c MANIFEST.sha256 >MANIFEST_VERIFY.txt) || result=1
  printf 'Output: %s\n' "$out"
  exit "$result"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

# -T 1 starts at the current buffer boundary; never replay historical logcat as new UI events.
if command -v logcat >/dev/null 2>&1; then
  logcat -T 1 -v epoch -s TS18Media:I TS18Nav:I TS18Launcher:I '*:S' >"$out/events.txt" 2>"$out/events.stderr" &
  log_pid=$!
else
  : >"$out/events.txt"
  printf 'logcat command unavailable\n' >"$out/events.stderr"
fi

packages=(com.cbkii.ts18launcher app.organicmaps.incar com.tw.media com.navimods.radio)
start=$SECONDS
start_ms="$(awk '{printf "%.0f", $1 * 1000}' /proc/uptime)"
while (( SECONDS - start < duration )); do
  now_ms="$(awk '{printf "%.0f", $1 * 1000}' /proc/uptime)"
  elapsed=$(( now_ms - start_ms ))
  for package in "${packages[@]}"; do
    pid="$(pidof -s "$package" 2>/dev/null || true)"
    [[ "$pid" =~ ^[0-9]+$ ]] || continue
    [[ -r "/proc/$pid/stat" && -r "/proc/$pid/status" ]] || continue
    # Strip the parenthesised process name; its spaces must not shift /proc/stat fields.
    stat="$(cat "/proc/$pid/stat" 2>/dev/null)" || continue
    fields="${stat#*) }"
    read -r -a parts <<<"$fields"
    (( ${#parts[@]} >= 22 )) || continue
    rss="$(awk '/^VmRSS:/ {print $2; exit}' "/proc/$pid/status" 2>/dev/null)"
    threads="$(awk '/^Threads:/ {print $2; exit}' "/proc/$pid/status" 2>/dev/null)"
    fds=(/proc/"$pid"/fd/*)
    [[ -e "${fds[0]}" ]] && fd_count=${#fds[@]} || fd_count=0
    read_bytes="$(awk '/^read_bytes:/ {print $2; exit}' "/proc/$pid/io" 2>/dev/null)"
    write_bytes="$(awk '/^write_bytes:/ {print $2; exit}' "/proc/$pid/io" 2>/dev/null)"
    # The first field after (comm) is stat field 3; utime/stime are fields 14/15.
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$elapsed" "$package" "$pid" "${parts[19]}" "${parts[11]}" "${parts[12]}" \
      "${rss:-unknown}" "${threads:-unknown}" "$fd_count" \
      "${read_bytes:-unknown}" "${write_bytes:-unknown}" >>"$out/process.tsv"
    samples=$((samples + 1))
  done
  sleep 0.5
done
