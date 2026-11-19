#!/system/bin/sh
# Small, machine-readable INDEX generated after all observers/recorders stop.
# No new Android operations, no changes to user apps, no runtime write outside OUT.
MODDIR=${0%/*}
. "$MODDIR/lib.sh"
OUT=$1; RUN=$2
case "$OUT" in
  /storage/emulated/0/Download/TS18-Validation-v3.6.0/boot-*|/sdcard/Download/TS18-Validation-v3.6.0/boot-*|/mnt/user/0/primary/Download/TS18-Validation-v3.6.0/boot-*) ;;
  *) exit 2;;
esac
[ -d "$OUT" ] && [ ! -L "$OUT" ] || exit 3
# TSV rows use file/line evidence, not arbitrary interpreted PASS claims.
REPORT="$OUT/AI-EVENTS.tsv"
{
  printf 'kind\tsource\tobserved_monotonic_or_uptime\ttext_bounded\n'
  for f in "$OUT/events.tsv" "$OUT/foreground-changes.tsv" "$OUT/video-timeline.tsv"; do
    [ -s "$f" ] || continue
    b awk -v src="${f##*/}" 'NR<=250 {gsub(/\t/," "); gsub(/[^\040-\176]/," "); print "OBSERVED\t" src "\t" "raw" "\t" substr($0,1,500)}' "$f"
  done
  for f in "$OUT"/target-logcat-*.txt "$OUT"/framework-admission-*.txt "$OUT"/task-lifecycle-events-*.log; do
    [ -s "$f" ] || continue
    b awk -v src="${f##*/}" '
      /TS18Browser|TS18Startup|TS18Transport|TS18Perf|TS18Media|TS18Nav|TS18NavPerm|TS18Launcher|InCarCompactStartup|Displayed|Fully drawn|am_finish_activity|am_destroy_activity|am_create_activity|am_task_removed|am_pause_activity|am_restart_activity|GnssLocationProvider|LocationManagerService|DisplayManagerService|MediaSessionService|MediaFocusControl|virtual.display|VirtualDisplay/ {
        if (++n>600) next;
        x=$0; gsub(/\t/," ",x);
        gsub(/-?[0-9]{1,3}\.[0-9]{5,}/,"[DECIMAL_REDACTED]",x);
        gsub(/[^\040-\176]/," ",x);
        print "LOG\t" src "\t" "logcat_timestamp" "\t" substr(x,1,550)
      }' "$f"
  done
} | b head -c 350000 > "$REPORT"
# Do not assert evidence that was not captured in the correct process/caller.
{
  printf '# TS18 physical validation run %s/3 — AI evidence map\n\n' "$RUN"
  printf '## Provenance and evidence limits\n'
  printf -- '- Module: v3.6.0; boot ID: `%s`.\n' "$(b cat "$OUT/boot-id.txt" 2>/dev/null)"
  printf -- '- Instrumentation: passive snapshots/logcat/screen recording; all Music/Radio/Nav actions are user-driven.\n'
  printf -- '- Video structure checked only; meaningful pixels and touches require visual inspection. No audible audio is captured.\n'
  printf -- '- This root-domain collector does **not** establish launcher-UID Binder rights or the application-UID input/touch boundary.\n'
  printf -- '- Video gaps and probe failures are meaningful. Failed snapshots do not prove a capability absent.\n\n'
  printf '## Evidence to inspect, in order\n'
  printf '1. `APK-MATCH.tsv` and `SUMMARY.txt`, then `AI-EVENTS.tsv` (bounded markers/timeline); `events.tsv` and `foreground-changes.tsv`.\n'
  printf '2. `video-timeline.tsv` + `video-*.mp4` or fallback, when present; correlate by uptime.\n'
  printf '3. `target-logcat-*.txt`, `task-lifecycle-events-*.log`, `framework-admission-*.txt` and `home-events.log` for transport, Maps finish/destroy/Recents and startup. Check `LOG-STREAM-STATUS.tsv` and `.rc`/`.err` before asserting absence.\n'
  printf '4. `snapshots/` for independent sessions/audio/tasks/WMS/display/location at spaced sample points.\n'
  printf '5. `preflight/` for APK identities, media/Topway/navigation/GNSS capability and execution domains.\n'
  printf '6. `status.tsv`, `*.rc`, `SUMMARY.txt`, `MANIFEST.sha256` for validity/collection failures.\n\n'
  printf '## Qualification dimensions (require source+video+user evidence)\n'
  printf -- '- HOME: first useful pixels/touch, status/right navigation safe areas, app drawer and settings.\n'
  printf -- '- M1/M2: Music and Radio each selected engine/package, cold/warm play, explicit command/tokens, handover, audio heard.\n'
  printf -- '- N0/N1/N2: task/display/window authority, actual pixels/touch, compact/fullscreen, Back/IME and HOME restoration.\n'
  printf -- '- GNSS/Maps: permission/AppOps/provider -> fresh fix -> map use -> route START/END/Driving View.\n'
  printf -- '- OEM: service identity versus safe status read; no native radio mutation in collector.\n'
  printf -- '- LIFECYCLE: verified boot ID distinguishes reboot; true cold-power/ACC still require an explicit operator note.\n'
  printf -- '- WP1: 20 real committed Next replacements; Previous >3s rewind/no passive PREPARE; launcher ACK is not source commit.\n'
  printf -- '- WP2: API29 fullscreen bounds and task user/display/window mode; geometry != pixels.\n'
  printf -- '- WP3: fullscreen Maps Back then one explicit Retry/Fullscreen; original retained Recents task identity, `reacquired retained task=`, no second cold launch.\n'
  printf -- '- WP4: raw shuffle persisted through source process restart; audible Music/Radio handover requires operator ears/phone.\n'
  printf -- '- PERFORMANCE: uptime, first useful screen, latency, PSS/gfxinfo, log timestamps; compare like-for-like only.\n\n'
  printf '## Session metadata\n'
  printf '```\n'; b head -c 5000 "$OUT/boot-id.txt"; printf '\n```\n'
} > "$OUT/AI-READ-FIRST.md"
exit 0
