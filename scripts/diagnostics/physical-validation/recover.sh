#!/system/bin/sh
# Only seal retained bytes from a DIFFERENT, ended Android boot. Never recover
# a same-boot active writer or turn an interrupted capture into a completed test.
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
ROOT=$1; OLD=$2; N=$3
case "$ROOT" in /storage/emulated/0/Download/TS18-Validation-v3.6.0|/sdcard/Download/TS18-Validation-v3.6.0|/mnt/user/0/primary/Download/TS18-Validation-v3.6.0) ;; *) exit 2;; esac
case "$OLD" in ''|*[!a-f0-9-]*) exit 2;; esac
case "$N" in 1|2|3) ;; *) exit 2;; esac
CURRENT=$(b cat /proc/sys/kernel/random/boot_id)
[ -n "$CURRENT" ] && [ "$CURRENT" != "$OLD" ] || exit 3
OUT="$ROOT/boot-$N-$OLD"
[ -d "$OUT" ] && [ ! -L "$OUT" ] && [ ! -s "$OUT/SUMMARY.txt" ] || exit 0
[ -z "$(b find "$OUT" -type l -print -quit)" ] || exit 4
printf 'INTERRUPTED before finalisation; next boot sealed retained bytes. Not a completed validation.\n' > "$OUT/INTERRUPTED.txt"
b rm -f "$OUT/CAPTURE-IN-PROGRESS.txt"
{
 printf 'module=3.6.0\nboot_id=%s\nattempt=%s/3\nreason=previous_boot_interrupted\n' "$OLD" "$N"
 printf 'status=PARTIAL_RECOVERED\nstop_uptime=UNKNOWN\nvideo_decode_and_visible_content=NOT_RUN\n'
} > "$OUT/SUMMARY.txt"
"$BB" sh "$MODDIR/ai-summary.sh" "$OUT" "$N"
(cd "$OUT" && b find . -type f ! -path './.control/*' ! -name MANIFEST.sha256 ! -name manifest-check.txt | b sort | while IFS= read -r f; do b sha256sum "$f" || exit 1; done) > "$OUT/MANIFEST.sha256"
(cd "$OUT" && bounded 20 "$BB" sha256sum -c MANIFEST.sha256 > manifest-check.txt 2>&1) || { printf 'Recovered manifest invalid\n' > "$OUT/MANIFEST_VERIFY_FAILED.txt"; exit 5; }
