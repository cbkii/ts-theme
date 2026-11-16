#!/bin/bash
# Host test adapter only. This is NOT a BusyBox/device equivalence claim.
export PATH=/usr/bin:/bin
cmd=$1; shift
if [ "$cmd" = sh ]; then exec bash "$@"; fi
exec "$cmd" "$@"
