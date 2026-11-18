#!/system/bin/sh
# POSIX shell / Magisk BusyBox ash. No Termux loader inheritance.
BB=/data/adb/magisk/busybox
PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin:/data/adb/magisk
export PATH
LC_ALL=C; export LC_ALL
unset LD_PRELOAD LD_LIBRARY_PATH
umask 077
b() { "$BB" "$@"; }
up() { b awk '{print int($1)}' /proc/uptime; }
stamp() { b cat /proc/uptime | b cut -d ' ' -f 1; }
number() { case "$1" in ''|*[!0-9]*) return 1;; *) return 0;; esac; }
android_user() {
  # Read framework's current foreground user, not the Magisk caller UID.
  bounded 2 /system/bin/am get-current-user 2>/dev/null | b awk '
    /^[0-9]+$/ { print $1; exit }
    /[Cc]urrent user/ { for (i=NF;i>=1;i--) if($i ~ /^[0-9]+$/) { print $i; exit } }'
}

home_time() {
  b awk -v p="$2/" '{t=$1; if(t=="[")t=$2; gsub(/[\[\]]/,"",t); if(t ~ /^[0-9]+\.[0-9]+$/ && /am_resume_activity|wm_resume_activity/ && index($0,p)) {print int(t);exit}}' "$1" 2>/dev/null
}
bounded() {
  local sec=$1 rc; shift
  b timeout -s TERM -k 1 "$sec" "$@"; rc=$?
  case "$rc" in 125|126|127)
    if [ -x /system/bin/timeout ]; then
      printf '# fallback: native Android timeout after runner rc=%s\n' "$rc" >&2
      /system/bin/timeout -s TERM -k 1 "$sec" "$@"; return "$?"
    fi;;
  esac
  return "$rc"
}
size() { b stat -c %s "$1" 2>/dev/null || printf 0; }
# Short bounded lines; separate writers own separate files.
line() {
  local file=$1 cap=$2; shift 2
  [ "$(size "$file")" -lt "$cap" ] || return 0
  printf '%s\t%s\n' "$(stamp)" "$*" | b head -c 900 >> "$file"
}
status() { line "$OUT/status.tsv" 32768 "$1	$2	$3"; }
event() { line "$OUT/events.tsv" 32768 "$1	$2"; }
token() { b sed 's/.*) //' "/proc/$1/stat" 2>/dev/null | b awk '{print $20}'; }
alive() { [ -n "${1#*:}" ] && [ -r "/proc/${1%:*}/stat" ] && [ "$(token "${1%:*}")" = "${1#*:}" ] && ! b sed 's/.*) //' "/proc/${1%:*}/stat" 2>/dev/null | b grep '^Z ' >/dev/null; }
job() {
  # Direct external dispatch: backgrounding a shell function adds an intermediate
  # PID and can make $! differ from the new session/group leader.
  "$BB" setsid "$BB" sh "$@" </dev/null >/dev/null 2>&1 &
  JP=$!; JT="$JP:$(token "$JP")"
}
# Only a collector-created session/process whose original start tick still matches.
# A group can retain children after its leader exits. Check the registered
# session/group too, and never signal a reused leader PID with a new start tick.
group_alive() {
  local id=$1 pid file stat rest pgrp sess ticks
  [ -n "$id" ] && number "${id#*:}" || return 1
  pid=${id%:*}
  if [ -r "/proc/$pid/stat" ] && [ "$(token "$pid")" != "${id#*:}" ]; then return 1; fi
  for file in /proc/[0-9]*/stat; do
    { IFS= read -r stat < "$file"; } 2>/dev/null || continue
    rest=${stat##*) }; set -- $rest
    [ "$#" -ge 20 ] || continue
    [ "$1" != Z ] || continue
    pgrp=$3; sess=$4; shift 19; ticks=$1
    if [ "$pgrp" = "$pid" ] && [ "$sess" = "$pid" ] && [ "$ticks" -ge "${id#*:}" ]; then return 0; fi
  done
  return 1
}
signal_job() { group_alive "$1" && b kill -s "$2" -- -"${1%:*}" 2>/dev/null; return 0; }
stop_job() {
  local id=$1 sig=${2:-TERM} n=0
  [ -n "$id" ] || return 0
  signal_job "$id" "$sig"
  while group_alive "$id" && [ "$n" -lt 3 ]; do b sleep 1; n=$((n+1)); done
  signal_job "$id" KILL
  if group_alive "$id"; then b sleep 1; fi
  # Do not wait indefinitely on an uninterruptible vendor task.
}
stop_video_job() {
  local id=$1 n=0
  [ -n "$id" ] || return 0
  signal_job "$id" INT
  while group_alive "$id" && [ "$n" -lt 4 ]; do b sleep 1; n=$((n+1)); done
  if group_alive "$id"; then signal_job "$id" HUP; b sleep 2; fi
  if group_alive "$id"; then signal_job "$id" TERM; b sleep 1; fi
  if group_alive "$id"; then signal_job "$id" KILL; b sleep 1; fi
}
capture() {
  local file=$1 seconds=$2 cap=$3 rc used remaining; shift 3
  if [ -n "$RUN_BUDGET_BYTES" ] && [ -d "$OUT" ]; then
    used=$(b du -sk "$OUT" 2>/dev/null | b awk '{print $1+0}')
    remaining=$((RUN_BUDGET_BYTES-used*1024-131072))
    if [ "$remaining" -lt 8192 ]; then
      status BUDGET WARN "skipped capture $file; remaining=${remaining}B"
      return 2
    fi
    [ "$cap" -le "$remaining" ] || cap=$remaining
  fi
  b rm -f "$file.rc"
  {
    printf '# uptime=%s timeout=%s cap=%s command=' "$(stamp)" "$seconds" "$cap"
    printf '%s ' "$@"; printf '\n'
    (bounded "$seconds" "$@" 2>&1; printf '%s\n' "$?" > "$file.rc") | b head -c "$cap"
    printf '\n# output capped before writing; rc in companion .rc (missing => interrupted)\n'
  } > "$file"
  rc=$(b cat "$file.rc" 2>/dev/null)
  case "$rc" in 0) status "$file" PASS 'command exited 0; inspect output for readiness/errors';;
    124|137|143) status "$file" WARN "timeout/termination rc=$rc; dependants UNKNOWN";;
    *) status "$file" WARN "rc=${rc:-interrupted}; cap or unavailable service possible";; esac
}
# Exact path must be inside a writable emulated-storage mount in THIS namespace.
# Never create /data/media/0 paths or write an unmounted /storage stub.
download_probe() {
  local p resolved root f mountok
  local ident=${1:-$BOOTID}
  case "$ident" in ''|*[!a-f0-9-]*) return 1;; esac
  for p in /storage/emulated/0/Download /sdcard/Download /mnt/user/0/primary/Download; do
    [ -d "$p" ] || continue
    resolved=$(b readlink -f "$p") || continue
    mountok=$(b awk -v p="$resolved" '
      p==$2 || index(p,$2"/")==1 {if(length($2)>n){n=length($2);fs=$3;opts=$4}}
      END{if(fs ~ /^(sdcardfs|fuse|fuseblk|esdfs)$/ && opts !~ /(^|,)ro(,|$)/)print "yes"}' /proc/mounts)
    if [ "$mountok" != yes ]; then
      # Independent mountinfo parser, still proving THIS path's longest mount.
      mountok=$(b awk -v p="$resolved" '
        p==$5 || index(p,$5"/")==1 {if(length($5)>n){n=length($5);opts=$6;for(i=7;i<=NF;i++)if($i=="-"){fs=$(i+1);break}}}
        END{if(fs ~ /^(sdcardfs|fuse|fuseblk|esdfs)$/ && opts !~ /(^|,)ro(,|$)/)print "yes"}' /proc/self/mountinfo)
    fi
    [ "$mountok" = yes ] || continue
    root="$p/TS18-Validation-v3.6.0"
    [ ! -L "$root" ] || continue
    b mkdir -p "$root" || continue
    f="$root/.probe-$ident-$$"
    (set -C; printf '%s\n' "$ident" > "$f") 2>/dev/null || continue
    if [ "$(b cat "$f")" = "$ident" ] && b mv "$f" "$f.ok" && [ "$(b cat "$f.ok")" = "$ident" ]; then
      b rm -f "$f.ok"; printf '%s\n' "$root"; return 0
    fi
    b rm -f "$f" "$f.ok"
  done
  return 1
}
# Structural MP4 check only, never a decode/playback/first-frame claim.
mp4check() {
  local file=$1 total off=0 count=0 len typ ft=0 mo=0 md=0 bytes
  total=$(size "$file"); [ "$total" -gt 24 ] || return 1
  while [ "$off" -lt "$total" ] && [ "$count" -lt 64 ]; do
    bytes=$(b od -An -tu1 -N8 -j "$off" "$file") || return 1
    set -- $bytes; [ "$#" -eq 8 ] || return 1
    len=$(( $1*16777216 + $2*65536 + $3*256 + $4 ))
    typ="$5,$6,$7,$8"
    [ "$len" -eq 0 ] && len=$((total-off))
    [ "$len" -ge 8 ] && [ $((off+len)) -le "$total" ] || return 1
    case "$typ" in 102,116,121,112) ft=1;; 109,111,111,118) mo=1;; 109,100,97,116) [ "$len" -gt 8 ] && md=1;; esac
    off=$((off+len)); count=$((count+1))
  done
  [ "$off" -eq "$total" ] && [ "$ft$mo$md" = 111 ]
}

# ash and Android shells disagree on ulimit -f units. Inspect inherited limits,
# then request a byte ceiling without guessing 512 versus 1024-byte blocks.
limit_file_bytes() {
  local cap=$1 unit
  number "$cap" || return 1
  unit=$( (ulimit -f 1; b awk '/^Max file size / {print $4; exit}' /proc/self/limits) )
  case "$unit" in 512|1024) ;; *) return 1;; esac
  [ "$cap" -ge "$unit" ] || return 1
  ulimit -f $((cap/unit))
}
