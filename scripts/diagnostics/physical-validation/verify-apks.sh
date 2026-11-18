#!/system/bin/sh
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
OUT=$1
case "$OUT" in /storage/emulated/0/Download/TS18-Validation-v3.6.0/boot-*|/sdcard/Download/TS18-Validation-v3.6.0/boot-*|/mnt/user/0/primary/Download/TS18-Validation-v3.6.0/boot-*) ;; *) exit 2;; esac
# An operator may supply the final consolidated selection as DATA in Download.
# It is never sourced/executed, and only the three exact package names are accepted.
selection="$MODDIR/expected-apks.tsv"
candidate="${OUT%/*}/EXPECTED-APKS.tsv"
if [ -f "$candidate" ] && [ ! -L "$candidate" ] && [ "$(size "$candidate")" -le 8192 ]; then
  selection=$candidate
fi
report="$OUT/APK-MATCH.tsv.tmp"
printf 'package\texpected_source\tresult\texpected_base_sha256\tobserved_base_sha256\n' > "$report"
while read -r pkg source expected; do
  case "$pkg" in ''|\#*|package) continue;; esac
  case "$pkg" in com.cbkii.ts18launcher|com.tw.media|app.organicmaps.incar) ;; *) continue;; esac
  file="$OUT/apps/$pkg-apk-base.apk.sha256.txt"
  actual=''; result=BLOCKED_HASH_NOT_CAPTURED
  if [ "$(b cat "$file.rc" 2>/dev/null)" = 0 ]; then
    actual=$(b awk '$1 ~ /^[a-f0-9]+$/ && length($1)==64 {print $1; exit}' "$file")
    if [ -n "$actual" ]; then
      result=MISMATCH
      [ "$expected" != PENDING_BUILD ] || result=UNKNOWN_BUILD_NOT_PINNED
      [ "$actual" != "$expected" ] || result=MATCH
    fi
  fi
  printf '%s\t%s\t%s\t%s\t%s\n' "$pkg" "$source" "$result" "$expected" "$actual" >> "$report"
done < "$selection"
b cp "$selection" "$OUT/apk-selection-used.tsv"
for pkg in com.cbkii.ts18launcher com.tw.media app.organicmaps.incar; do
  rows=$(b awk -F '\t' -v p="$pkg" '$1==p {n++} END {print n+0}' "$report")
  if [ "$rows" -eq 0 ]; then
    printf '%s\tUNKNOWN\tBLOCKED_SELECTION_MISSING\tUNKNOWN\tUNKNOWN\n' "$pkg" >> "$report"
  elif [ "$rows" -gt 1 ]; then
    b awk -F '\t' -v OFS='\t' -v p="$pkg" '$1==p {$3="BLOCKED_SELECTION_DUPLICATE"} {print}' "$report" > "$report.dedup"
    b mv "$report.dedup" "$report"
  fi
done
b mv "$report" "$OUT/APK-MATCH.tsv"
