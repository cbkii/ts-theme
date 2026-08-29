#!/usr/bin/env bash

find_app_pa() {
  local candidate
  for candidate in \
    /data/user/0/com.dofun.variety/app_p_a \
    /data/data/com.dofun.variety/app_p_a; do
    if su -c "test -d '$candidate'" 2>/dev/null; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

list_donor_records() {
  local app_pa="$1"
  su -c "test -f '$app_pa/p.l' && cat '$app_pa/p.l'" 2>/dev/null |
    grep -E 'launcher\.variety\.theme\.plugin\.sfp_[A-Za-z0-9_]+' || return 0
}

candidate_donor_basenames() {
  local app_pa="$1"
  list_donor_records "$app_pa" |
    grep -oE '/data/(user/0|data)/com\.dofun\.variety/app_p_a/[0-9]+\.jar' |
    sed 's#.*/##' |
    awk '!seen[$0]++'
}
