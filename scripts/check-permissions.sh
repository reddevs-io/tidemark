#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Fails unless an APK requests exactly Tidemark's allowed permissions.
#
# Tidemark promises no network access. A library can add INTERNET or ACCESS_NETWORK_STATE
# through manifest merging, so CI checks the built APK, whose manifest is the final merged one.
#
# Usage: scripts/check-permissions.sh <path/to/app.apk>
#
# aapt2 comes from $AAPT2 if set, otherwise from the newest build-tools under
# $ANDROID_HOME (or $ANDROID_SDK_ROOT).
#
# Exit codes: 0 allowed set matches, 1 unexpected or missing permission, 2 usage or tool error.

set -euo pipefail
# Byte order for sort and comm, whatever the caller's locale.
export LC_ALL=C

# The nine Health Connect read permissions, plus the app-private signature permission that
# androidx.core declares for non-exported dynamic receivers. Keep in sync with
# app/src/main/AndroidManifest.xml and docs/play/health-declaration.md.
readonly ALLOWED=(
  android.permission.health.READ_ACTIVE_CALORIES_BURNED
  android.permission.health.READ_DISTANCE
  android.permission.health.READ_EXERCISE
  android.permission.health.READ_HEALTH_DATA_HISTORY
  android.permission.health.READ_HEART_RATE
  android.permission.health.READ_RESTING_HEART_RATE
  android.permission.health.READ_SLEEP
  android.permission.health.READ_STEPS
  android.permission.health.READ_WEIGHT
  io.reddevs.tidemark.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
)

die() {
  echo "check-permissions: $*" >&2
  exit 2
}

[[ $# -eq 1 ]] || die "usage: $0 <path/to/app.apk>"
apk=$1
[[ -f $apk ]] || die "APK not found: $apk"

find_aapt2() {
  if [[ -n ${AAPT2:-} ]]; then
    echo "$AAPT2"
    return
  fi
  local sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
  [[ -n $sdk ]] || die "set ANDROID_HOME or AAPT2"
  local dir
  dir=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)
  [[ -n $dir && -x $dir/aapt2 ]] || die "no aapt2 under $sdk/build-tools"
  echo "$dir/aapt2"
}

aapt2=$(find_aapt2)
dump=$("$aapt2" dump permissions "$apk") || die "aapt2 could not read $apk"

# aapt2 prints "uses-permission: name='X'" and "uses-permission-sdk-23: name='X'" for requested
# permissions, and "permission: X" for permissions the app declares. Only requests matter here.
mapfile -t requested < <(
  sed -n -E "s/^uses-permission(-sdk-23)?: name='([^']+)'.*/\2/p" <<<"$dump" | sort -u
)
mapfile -t allowed < <(printf '%s\n' "${ALLOWED[@]}" | sort -u)

mapfile -t unexpected < <(comm -23 <(printf '%s\n' "${requested[@]}") <(printf '%s\n' "${allowed[@]}") | sed '/^$/d')
mapfile -t missing < <(comm -13 <(printf '%s\n' "${requested[@]}") <(printf '%s\n' "${allowed[@]}") | sed '/^$/d')

echo "APK: $apk"
echo "Requested permissions (${#requested[@]}):"
printf '  %s\n' "${requested[@]}"

status=0
if ((${#unexpected[@]} > 0)); then
  status=1
  echo "FAIL: permissions outside the allow-list:"
  for p in "${unexpected[@]}"; do
    case $p in
      android.permission.INTERNET | android.permission.ACCESS_NETWORK_STATE | android.permission.ACCESS_WIFI_STATE)
        echo "  $p  <- network access breaks Tidemark's privacy promise"
        ;;
      *)
        echo "  $p"
        ;;
    esac
  done
fi
if ((${#missing[@]} > 0)); then
  status=1
  echo "FAIL: allowed permissions the APK no longer requests (update the allow-list and the Play declaration together):"
  printf '  %s\n' "${missing[@]}"
fi

if ((status == 0)); then
  echo "OK: the APK requests exactly the ${#allowed[@]} allowed permissions."
fi
exit "$status"
