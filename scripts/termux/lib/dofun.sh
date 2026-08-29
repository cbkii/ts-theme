#!/usr/bin/env bash

find_app_pa() {
  local candidate
  for candidate in /data/user/0/com.dofun.variety/app_p_a /data/data/com.dofun.variety/app_p_a; do
    if su -c "test -d '$candidate' && test ! -L '$candidate' && test -f '$candidate/p.l'" 2>/dev/null; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

normalise_pl_paths() {
  sed 's#\\/#/#g'
}

parse_donor_records() {
  local record package path basename priority
  normalise_pl_paths | tr '\n' ' ' | sed 's/}/}\n/g' | while IFS= read -r record; do
    [[ "$record" == *"launcher.variety.theme.plugin.sfp_"* ]] || continue
    package="$(printf '%s\n' "$record" | grep -oE 'launcher\.variety\.theme\.plugin\.sfp_[A-Za-z0-9_]+' | head -n 1)"
    path="$(printf '%s\n' "$record" | grep -oE '/data/(user/0|data)/com\.dofun\.variety/app_p_a/[0-9]+\.jar' | head -n 1)"
    [[ -n "$package" && -n "$path" ]] || continue
    basename="${path##*/}"
    case "$package" in
      launcher.variety.theme.plugin.sfp_fyd18) priority=10 ;;
      launcher.variety.theme.plugin.sfp_ts10s|launcher.variety.theme.plugin.sfp_ts10) priority=20 ;;
      *) priority=50 ;;
    esac
    printf '%02d|%s|%s\n' "$priority" "$package" "$basename"
  done | sort -t '|' -k1,1n -k2,2 -u | cut -d '|' -f2-
}

list_donor_records() {
  local app_pa="$1"
  su -c "cat '$app_pa/p.l'" 2>/dev/null | normalise_pl_paths | grep -oE 'launcher\.variety\.theme\.plugin\.sfp_[A-Za-z0-9_]+' | awk '!seen[$0]++'
}

donor_candidates() {
  local app_pa="$1"
  su -c "cat '$app_pa/p.l'" 2>/dev/null | parse_donor_records
}
