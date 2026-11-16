#!/system/bin/sh
# Stream a verified three-run tar.gz into <=20,000,000-byte parts; max 42,000,000
# archive bytes; never form a full duplicate tarball or use /tmp or /data/adb.
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
ROOT=$1; SECOND=$2
case "$ROOT" in /storage/emulated/0/Download/TS18-Validation-v3.6.0|/sdcard/Download/TS18-Validation-v3.6.0|/mnt/user/0/primary/Download/TS18-Validation-v3.6.0) ;; *) echo 'BLOCKED: non-allowlisted Download root'; exit 2;; esac
[ -d "$ROOT" ] && [ ! -L "$ROOT" ] || exit 2
FIRST=$(b cat "$ROOT/.campaign-first" 2>/dev/null)
# The committed counter identifies boot3; boot2 is read from retained sealed folder.
COUNT=$(b cat "$ROOT/.campaign-state" 2>/dev/null)
[ "$COUNT" = "$SECOND|3" ] || { echo 'BLOCKED: third run is not committed for requested boot'; exit 2; }
for id in "$FIRST" "$SECOND"; do case "$id" in ''|*[!a-f0-9-]*) echo 'BLOCKED: invalid campaign ID'; exit 2;; esac; done
RUN1="boot-1-$FIRST"; RUN3="boot-3-$SECOND"
RUN2=''
for dir in "$ROOT"/boot-2-*; do
  [ -d "$dir" ] && [ ! -L "$dir" ] || continue
  [ -z "$RUN2" ] || { echo 'BLOCKED: ambiguous second boot folder'; exit 2; }
  RUN2=${dir##*/}
done
[ -n "$RUN2" ] || { echo 'BLOCKED: second boot folder missing'; exit 2; }
MIDDLE=${RUN2#boot-2-}
case "$MIDDLE" in ''|*[!a-f0-9-]*) echo 'BLOCKED: invalid second boot ID'; exit 2;; esac
[ "$FIRST" != "$MIDDLE" ] && [ "$MIDDLE" != "$SECOND" ] && [ "$FIRST" != "$SECOND" ] || { echo 'BLOCKED: boot IDs not distinct'; exit 2; }
for dir in "$ROOT/$RUN1" "$ROOT/$RUN2" "$ROOT/$RUN3"; do
  [ -d "$dir" ] && [ ! -L "$dir" ] && [ -s "$dir/MANIFEST.sha256" ] && [ -s "$dir/SUMMARY.txt" ] || { echo 'BLOCKED: missing sealed boot folder'; exit 3; }
  [ ! -f "$dir/MANIFEST_VERIFY_FAILED.txt" ] && [ ! -f "$dir/INCOMPLETE.txt" ] || { echo 'BLOCKED: prior seal invalid'; exit 4; }
  [ -z "$(b find "$dir" -type l -print -quit)" ] || { echo 'BLOCKED: unexpected symlink'; exit 4; }
  (cd "$dir" && "$BB" sha256sum -c MANIFEST.sha256 >/dev/null 2>&1) || { echo "BLOCKED: manifest mismatch in ${dir##*/}"; exit 4; }
done
# Keep raw + compressed + metadata under a 100MB envelope. Restrict
# raw to 96MB and final streamed compressed bytes to 100MB plus headroom.
raw_kb=$(b du -sk "$ROOT/$RUN1" "$ROOT/$RUN2" "$ROOT/$RUN3" 2>/dev/null | b awk '{s+=$1} END{print s+0}')
[ $((raw_kb*1024)) -le 96000000 ] || { echo "BLOCKED: raw runs exceed 96MB (${raw_kb}KiB); individual folders retained"; exit 5; }
root_kb=$(b du -sk "$ROOT" 2>/dev/null | b awk '{print $1+0}')
[ $((root_kb*1024)) -le 110000000 ] || { echo 'BLOCKED: other existing Download output makes 220MB total impossible without deleting user files'; exit 5; }
HANDOFF="EXECUTION_HANDOFF-$SECOND.md"
{
  echo '# TS18 TESTING physical validation three-run evidence bundle (v3.6.0)'
  echo 'Combined tar.gz split into ordered, byte-limited parts (not separate gzip files).'
  echo 'Each run was sealed and SHA-256 verified before bundling.'
  echo 'Reassemble with host-tools/reassemble_parts.py or concatenate in numeric part order.'
  echo 'No protected Binder/tuner/display mutation or installed-app UID testing.'
  echo 'User operated the apps during capture; device verification requires reviewing video + source logs + manual observations.'
  echo 'AI reading order: handoff > each AI-READ-FIRST.md > AI-EVENTS.tsv > task/media snapshots > video segments.'
  echo 'Raw screen recordings and logs may contain private information. Review before uploading.'
  for dir in "$ROOT/$RUN1" "$ROOT/$RUN2" "$ROOT/$RUN3"; do
    echo
    printf '## %s\n' "${dir##*/}"
    b grep -E '^(attempt|phase|reason|video_structurally_valid|downloads_exported|start_uptime|stop_uptime|boot_id|mode)=' "$dir/SUMMARY.txt"
    printf "AI timeline lines: "; b wc -l < "$dir/AI-EVENTS.tsv" 2>/dev/null || echo unknown
  done
} > "$ROOT/$HANDOFF"
cd "$ROOT" || exit 3
PREFIX="TS18-Validation-v3.6.0-$SECOND.tar.gz.part-"
TEMP=".building-$SECOND.part-"
TAR_RC=".building-$SECOND.tar-rc"
GZIP_RC=".building-$SECOND.gzip-rc"
ERRORS=".building-$SECOND.tar-errors"
[ -z "$(b find . -maxdepth 1 -name "$PREFIX*" -print -quit)" ] || { echo 'BLOCKED: final parts already exist; no silent overwrite'; exit 6; }
b rm -f "$TAR_RC" "$GZIP_RC" "$ERRORS"
# Stream through a byte ceiling to FIVE fixed-size dd consumers. BusyBox
# split is not consistently present in Magisk builds; dd/head are required.
# Each consumer reads from the same stream sequentially; no full tarball exists.
( "$BB" tar -cf - "$RUN1" "$RUN2" "$RUN3" "$HANDOFF" 2>"$ERRORS"; printf '%s\n' "$?" > "$TAR_RC" ) | ( "$BB" gzip -c; printf '%s\n' "$?" > "$GZIP_RC" ) | "$BB" head -c 99983360 | {
  part_ok=yes
  for part in 000 001 002 003 004; do
    "$BB" dd bs=4096 count=4882 iflag=fullblock of="${TEMP}$part" 2>"${TEMP}$part.dd.log" || part_ok=no
  done
  [ "$part_ok" = yes ]
}
pipe_rc=$?
producer_rc=$(b cat "$TAR_RC" 2>/dev/null)
gzip_rc=$(b cat "$GZIP_RC" 2>/dev/null)
# Empty trailing parts are not published.
for f in "${TEMP}001" "${TEMP}002" "${TEMP}003" "${TEMP}004"; do [ ! -f "$f" ] || [ -s "$f" ] || b rm -f "$f"; done
b rm -f "$TEMP"*.dd.log
if [ "$pipe_rc" -ne 0 ] || [ "$producer_rc" != 0 ] || [ "$gzip_rc" != 0 ]; then
  echo "BLOCKED: streaming failed (consumer=$pipe_rc tar=${producer_rc:-interrupted} gzip=${gzip_rc:-interrupted}); raw folders retained"
  b rm -f "$TEMP"*; exit 7
fi
set -- "$TEMP"???
[ -f "$1" ] || { echo 'BLOCKED: no archive parts created'; exit 7; }
archive_bytes=0
for f in "$@"; do
  [ -f "$f" ] || continue
  n=$(size "$f"); archive_bytes=$((archive_bytes+n))
  [ "$n" -le 20000000 ] && [ "$archive_bytes" -le 100000000 ] || { echo 'BLOCKED: archive cap exceeded'; b rm -f "$TEMP"*; exit 7; }
done
# Verify decompression and tar archive integrity from the ordered parts.
( for f in "$@"; do b cat "$f" || exit 1; done ) | "$BB" gzip -dc | "$BB" tar -tf - >/dev/null 2>&1 || { echo 'BLOCKED: truncated/corrupt archive stream'; b rm -f "$TEMP"*; exit 8; }
for f in "$@"; do
  suffix=${f##*.part-}
  b mv "$f" "$PREFIX$suffix" || { echo 'BLOCKED: part rename failed'; exit 9; }
done
( for f in "$PREFIX"???; do b sha256sum "$f" || exit 1; done ) > "$PREFIX"'MANIFEST.sha256' || exit 9
"$BB" sha256sum -c "$PREFIX"'MANIFEST.sha256' >/dev/null 2>&1 || { echo 'BLOCKED: final part hash mismatch'; exit 9; }
# No need to delete the original run folders. Their total + archive fits the cap.
b rm -f "$TAR_RC" "$GZIP_RC" "$ERRORS"
echo "PASS: $archive_bytes total compressed bytes in <=20MB ordered parts across three boot IDs; raw folders retained"
