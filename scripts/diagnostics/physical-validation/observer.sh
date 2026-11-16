#!/system/bin/sh
MODDIR=${0%/*}; . "$MODDIR/lib.sh"
OUT=$1
USERID=$(android_user); number "$USERID" || USERID=0
candidate=com.cbkii.ts18launcher
nextresolve=0
last_focus=
while [ ! -f "$OUT/STOP" ]; do
  t=$(up)
  if [ "$t" -ge "$nextresolve" ]; then
    nextresolve=$((t+16))
    capture "$OUT/home-resolve.txt" 2 4096 /system/bin/cmd package resolve-activity --brief --user "$USERID" -a android.intent.action.MAIN -c android.intent.category.HOME
    comp=$(b grep -E '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$' "$OUT/home-resolve.txt" | b tail -n 1)
    case "$comp" in ''|*FallbackHome*|*ResolverActivity*)
      capture "$OUT/home-resolve-pm.txt" 2 4096 /system/bin/pm resolve-activity --brief --user "$USERID" -a android.intent.action.MAIN -c android.intent.category.HOME
      alt=$(b grep -E '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$' "$OUT/home-resolve-pm.txt" | b tail -n 1)
      case "$alt" in ''|*FallbackHome*|*ResolverActivity*) ;; *) comp=$alt; event HOME_RESOLVE_PM_FALLBACK "$comp";; esac;;
    esac
    case "$comp" in
      *FallbackHome*|*ResolverActivity*|'') event HOME_RESOLVE_TRANSIENT "${comp:-unresolved}; not launch proof";;
      *) candidate=${comp%%/*}; event HOME_CANDIDATE "$comp; identity only";;
    esac
    printf '%s\n' "$candidate" > "$OUT/home-package.tmp"
    b mv "$OUT/home-package.tmp" "$OUT/home-package"
  fi
  # Save probe-start time, not its later completion time.
  started=$(up)
  capture "$OUT/home-activity.txt" 2 49152 /system/bin/dumpsys activity activities
  if [ "$(b cat "$OUT/home-activity.txt.rc" 2>/dev/null)" = 0 ]; then
    homecomp=$(b grep 'mHomeActivity' "$OUT/home-activity.txt" | b grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' | b head -n 1)
    case "$homecomp" in ''|*FallbackHome*|*ResolverActivity*) ;; *) candidate=${homecomp%%/*};; esac
    active=$(b grep -E 'mResumedActivity|topResumedActivity|mFocusedActivity' "$OUT/home-activity.txt" | b head -c 1000)
    if printf '%s' "$active" | b grep -F "$candidate/" >/dev/null; then
      focus=$(printf "%s" "$active" | b grep -oE "[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+" | b head -n 1)
  if [ -n "$focus" ] && [ "$focus" != "$last_focus" ]; then
    line "$OUT/foreground-changes.tsv" 49152 "focus=$focus sampled_start=$started prior=${last_focus:-UNKNOWN}"
    event FOREGROUND_CHANGE "focus=$focus sampled_start=$started"
    last_focus=$focus
  fi
  if [ ! -f "$OUT/home-observed" ]; then
        printf '%s\n' "$started" > "$OUT/home-observed.tmp"; b mv "$OUT/home-observed.tmp" "$OUT/home-observed"
        event HOME_RESUMED_OBSERVATION "$candidate probe_start=$started; actual first launch may precede this"
      fi
    fi
  fi
  focus=$(printf "%s" "$active" | b grep -oE "[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+" | b head -n 1)
  if [ -n "$focus" ] && [ "$focus" != "$last_focus" ]; then
    line "$OUT/foreground-changes.tsv" 49152 "focus=$focus sampled_start=$started prior=${last_focus:-UNKNOWN}"
    event FOREGROUND_CHANGE "focus=$focus sampled_start=$started"
    last_focus=$focus
  fi
  if [ ! -f "$OUT/home-observed" ]; then
    started=$(up)
    capture "$OUT/home-window.txt" 2 49152 /system/bin/dumpsys window windows
    focused=$(b grep -E 'mCurrentFocus|mFocusedApp' "$OUT/home-window.txt" | b head -c 1000)
    if [ "$(b cat "$OUT/home-window.txt.rc" 2>/dev/null)" = 0 ] && printf '%s' "$focused" | b grep -F "$candidate/" >/dev/null; then
      printf '%s\n' "$started" > "$OUT/home-observed.tmp"; b mv "$OUT/home-observed.tmp" "$OUT/home-observed"
      event HOME_WINDOW_OBSERVATION "$candidate probe_start=$started; not exact first-frame time"
    fi
  fi
  capture "$OUT/processes-current.txt" 2 49152 /system/bin/ps -A
  line "$OUT/process-samples.tsv" 16384 "epoch=$(b date +%s) boot=$(bounded 1 /system/bin/getprop sys.boot_completed) surface=$(bounded 1 /system/bin/getprop init.svc.surfaceflinger) ce=$(bounded 1 /system/bin/getprop sys.user.0.ce_available)"
  for pkg in com.cbkii.ts18launcher com.tw.media app.organicmaps.incar com.navimods.radio com.dofun.variety com.tw.service; do
    pids=$(bounded 1 /system/bin/pidof "$pkg" 2>/dev/null)
    if [ -z "$pids" ] && [ "$(b cat "$OUT/processes-current.txt.rc" 2>/dev/null)" = 0 ]; then
      pids=$(b grep -F "$pkg" "$OUT/processes-current.txt" | b head -c 160)
    fi
    line "$OUT/process-samples.tsv" 16384 "$pkg pids=${pids:-UNKNOWN_OR_NOT_FOUND}; process != ready"
  done
  b sleep 7
done
