#!/system/bin/sh
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
dest=$1; seconds=$2; profile=$3
# Direct file in verified Download only. No /tmp, /data/adb or shared-storage FIFO.
# The AOSP recorder installs SIGINT. Its wrapper must wait for MP4 finalisation.
trap '' INT
# Production default is 640x360; the previously decoded 768x432 profile remains
# a bounded manual comparison option, rather than a second recording mechanism.
cap=4194304
case "$profile" in
  0) set -- --size 1280x720 --bit-rate 2000000;;
  1) set -- --size 960x540 --bit-rate 1200000;;
  2) set -- --size 640x360 --bit-rate 600000;;
  3) set -- --size 768x432 --bit-rate 650000;;
  *) set -- --bit-rate 1100000;;
esac
recorder=''
binaries='/system/bin/screenrecord /vendor/bin/screenrecord /product/bin/screenrecord'
for binary in $binaries; do
  [ -x "$binary" ] || continue
  help=$(bounded 2 "$binary" --help 2>&1 | b head -c 8192)
  { printf '# %s\n' "$binary"; printf '%s\n' "$help" | b head -c 1024; } >> "$dest.probes.txt"
  case "$help" in *--time-limit*--bit-rate*|*--bit-rate*--time-limit*) recorder=$binary; break;; esac
done
[ -n "$recorder" ] || { printf 'BLOCKED: no recorder with required option contract\n' > "$dest.encoder.txt"; exit 1; }
# Keep v1.2's minimal argument vector as an option when --verbose is unsupported.
case "$help" in *--verbose*) set -- --verbose "$@";; esac
{
  printf 'binary=%s io=direct profile=%s seconds=%s\n' "$recorder" "$profile" "$seconds"
  printf 'argv: '; printf '%s ' "$recorder" --time-limit "$seconds" "$@" "$dest"; printf '\n'
} > "$dest.method.txt"
# --verbose writes to stdout on this recorder; retain both channels under a
# separate small output cap while keeping the native file limit on the MP4.
( (limit_file_bytes "$cap" || exit 125
   "$recorder" --time-limit "$seconds" "$@" "$dest" &
   encoder=$!
   printf 'encoder_pid=%s command_start_uptime=%s first_frame_uptime=UNKNOWN\n' "$encoder" "$(stamp)" > "$dest.encoder-lifecycle.txt"
   wait "$encoder"
  ); printf '%s\n' "$?" > "$dest.rc") 2>&1 | b head -c 16384 > "$dest.encoder.txt" &
# The pipeline PID is the reader. Record the recorder's actual status separately
# in its own wrapper below instead of treating a pipe exit as encoder success.
native=$!
wait "$native"
rc=$(b cat "$dest.rc" 2>/dev/null); number "$rc" || rc=125
exit "$rc"
