#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Checks a release tag against gradle.properties before anything is built or signed.
#
#   1. The tag must equal "v" + tidemark.versionName.
#   2. tidemark.versionCode must be greater than every versionCode already published. GitHub
#      Releases and Google Play share one sequence, so a lower code would block updates.
#   3. The release notes fastlane/metadata/android/en-US/changelogs/<versionCode>.txt must exist.
#
# Each release made by .github/workflows/release.yml carries a plain-text "versionCode" asset.
# This script reads those assets with the gh CLI (GH_TOKEN must be set in CI). To run it
# offline, set PREVIOUS_VERSION_CODE to the highest published code, or 0 if there is none.
#
# Usage: scripts/check-release-version.sh <tag>
#
# When $GITHUB_OUTPUT is set it also writes version_name, version_code, prerelease and
# changelog for later workflow steps.

set -euo pipefail

die() {
  echo "check-release-version: $*" >&2
  exit 1
}

[[ $# -eq 1 ]] || die "usage: $0 <tag>"
tag=$1
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
props=$root/gradle.properties

prop() {
  local value
  value=$(sed -n -E "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*(.*[^[:space:]])[[:space:]]*$/\1/p" "$props" | tail -n 1)
  [[ -n $value ]] || die "$1 is missing from gradle.properties"
  echo "$value"
}

version_name=$(prop 'tidemark\.versionName')
version_code=$(prop 'tidemark\.versionCode')
[[ $version_code =~ ^[1-9][0-9]*$ ]] || die "tidemark.versionCode must be a positive integer, got '$version_code'"

if [[ $tag != "v$version_name" ]]; then
  die "tag '$tag' does not match tidemark.versionName '$version_name' (expected 'v$version_name')"
fi
echo "Tag $tag matches versionName $version_name."

# Highest versionCode attached to any earlier release, drafts excluded.
previous_code() {
  if [[ -n ${PREVIOUS_VERSION_CODE:-} ]]; then
    echo "$PREVIOUS_VERSION_CODE"
    return
  fi
  command -v gh >/dev/null || die "gh CLI not found; set PREVIOUS_VERSION_CODE to run offline"
  local max=0 other code tmp tags
  # Listed up front so a gh failure stops the script instead of looking like "no releases".
  tags=$(gh release list --limit 1000 --exclude-drafts --json tagName --jq '.[].tagName') ||
    die "could not list releases"
  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' RETURN
  while IFS= read -r other; do
    [[ -n $other && $other != "$tag" ]] || continue
    rm -f "$tmp/versionCode"
    if ! gh release download "$other" --pattern versionCode --dir "$tmp" </dev/null >/dev/null 2>&1; then
      die "release $other has no versionCode asset; attach one with its code before releasing again"
    fi
    code=$(tr -d '[:space:]' <"$tmp/versionCode")
    [[ $code =~ ^[0-9]+$ ]] || die "release $other has an invalid versionCode asset: '$code'"
    if ((code > max)); then
      max=$code
    fi
  done <<<"$tags"
  echo "$max"
}

prev=$(previous_code)
[[ $prev =~ ^[0-9]+$ ]] || die "previous versionCode must be an integer, got '$prev'"
if ((version_code <= prev)); then
  die "tidemark.versionCode $version_code must be greater than the highest published versionCode $prev"
fi
echo "versionCode $version_code is greater than the highest published versionCode $prev."

changelog=fastlane/metadata/android/en-US/changelogs/$version_code.txt
[[ -s $root/$changelog ]] || die "release notes $changelog are missing or empty"
echo "Release notes: $changelog"

prerelease=false
if [[ $tag == *-rc* ]]; then
  prerelease=true
fi
echo "Pre-release: $prerelease"

if [[ -n ${GITHUB_OUTPUT:-} ]]; then
  {
    echo "version_name=$version_name"
    echo "version_code=$version_code"
    echo "prerelease=$prerelease"
    echo "changelog=$changelog"
  } >>"$GITHUB_OUTPUT"
fi
