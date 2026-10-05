# Bounded producer primitives for TS18 diagnostics 1.3; sourced, no entry point.
# BB, SELF, OUT and END are set by the collector. No su or device mutations.
uptime_s() { read -r up rest < /proc/uptime; printf '%s\n' "${up%%.*}"; }
ticks() { "$BB" sed 's/.*) //' "/proc/$1/stat" 2>/dev/null | "$BB" awk '$1 != "Z" {print $20}'; }
same_process() { [ -n "$2" ] && [ "$(ticks "$1")" = "$2" ]; }
atomic() { printf '%s\n' "$2" > "$1.new" && "$BB" mv "$1.new" "$1"; }
event() { printf '%s\t%s\n' "$(uptime_s)" "$*" >> "$OUT/events.tsv"; }
# One isolated session per producer. The parent owns termination, including
# descendants which keep stdout open after the original command has returned.
produce() {
    p_dir=$1; p_limit=$2; shift 2
    p_ids=$("$BB" sed 's/.*) //' /proc/$$/stat | "$BB" awk '{print $3, $4}')
    [ "$p_ids" = "$$ $$" ] || exit 70
    "$BB" mkfifo "$p_dir/pipe" || exit 71
    "$BB" head -c "$p_limit" < "$p_dir/pipe" > "$p_dir/output.txt" & p_reader=$!
    "$@" > "$p_dir/pipe" 2>&1
    p_rc=$?
    wait "$p_reader" 2>/dev/null || true
    atomic "$p_dir/producer.done" "$p_rc" || exit 72
    # Keep group identity alive until parent has observed the sentinel and
    # killed the whole owned group. Never write evidence after done.
    while :; do "$BB" sleep 1; done
}
capture() {
    c_name=$1; c_secs=$2; c_limit=$3; shift 3
    c_start=$(uptime_s)
    [ "$c_start" -lt "$END" ] || { event "BLOCKED $c_name overall_deadline"; return 1; }
    c_free=$("$BB" df -Pk "$OUT" 2>/dev/null | "$BB" awk 'END {print $4}')
    case "$c_free" in ''|*[!0-9]*) event "WARN $c_name free-space UNKNOWN" ;;
        *) [ "$c_free" -ge 262144 ] || { event "BLOCKED $c_name low-private-storage"; return 1; } ;; esac
    c_dir=$OUT/commands/$c_name
    "$BB" mkdir "$c_dir" || return 1
    {
        printf 'start_uptime=%s\ntimeout_s=%s\nlimit_bytes=%s\ncontext=../../CONTEXT.txt\n' "$c_start" "$c_secs" "$c_limit"
        c_arg=0
        for c_value in "$@"; do printf 'argv_%s=%s\n' "$c_arg" "$c_value"; c_arg=$((c_arg + 1)); done
    } > "$c_dir/meta.txt"
    "$BB" setsid "$BB" sh "$SELF" --produce "$c_dir" "$c_limit" "$@" </dev/null >/dev/null 2>&1 &
    c_pid=$!; c_ticks=$(ticks "$c_pid")
    atomic "$c_dir/owner" "$c_pid $c_ticks"
    c_until=$((c_start + c_secs)); [ "$c_until" -le "$END" ] || c_until=$END
    while same_process "$c_pid" "$c_ticks" && [ ! -f "$c_dir/producer.done" ] && [ "$(uptime_s)" -lt "$c_until" ]; do
        "$BB" sleep 1
    done
    c_result=WARN; c_rc=UNKNOWN
    if [ -r "$c_dir/producer.done" ]; then
        read -r c_rc < "$c_dir/producer.done"
        [ "$c_rc" = 0 ] && c_result=PASS
    elif [ "$(uptime_s)" -ge "$c_until" ]; then
        c_result=WARN_TIMEOUT
    else
        c_result=BLOCKED
    fi
    # Group is ours only when its leader retains the recorded start identity.
    if same_process "$c_pid" "$c_ticks"; then
        "$BB" kill -KILL "-$c_pid" 2>/dev/null || "$BB" kill -KILL "$c_pid" 2>/dev/null
    fi
    wait "$c_pid" 2>/dev/null || true
    "$BB" rm -f "$c_dir/pipe"
    c_size=$("$BB" stat -c %s "$c_dir/output.txt" 2>/dev/null) || c_size=0
    [ "$c_size" -ge "$c_limit" ] && c_result=WARN_TRUNCATED
    printf 'end_uptime=%s\nduration_s=%s\nproducer_rc=%s\nresult=%s\nbytes=%s\n' \
        "$(uptime_s)" "$(( $(uptime_s) - c_start ))" "$c_rc" "$c_result" "$c_size" >> "$c_dir/meta.txt"
    event "$c_result $c_name rc=$c_rc bytes=$c_size"
    [ "$c_result" = PASS ]
}
seal() {
    # Caller has joined capture/stream workers and excludes further markers.
    printf 'producer=COMPLETE\nqualification=NOT_RUN\n' > "$OUT/COMPLETE.txt"
    (cd "$OUT" && "$BB" find . -type f ! -name MANIFEST.sha256 ! -name SEALED.txt -print | "$BB" sort |
        while IFS= read -r s_file; do "$BB" sha256sum "$s_file" || exit 1; done) > "$OUT/MANIFEST.sha256" || return 1
    (cd "$OUT" && "$BB" sha256sum -c MANIFEST.sha256 >/dev/null) || return 1
    printf 'PASS: writers closed, file hashes verified; command failures remain in events.tsv\n' > "$OUT/SEALED.txt"
}
