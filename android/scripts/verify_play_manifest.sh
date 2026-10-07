#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
manifest="$(find "$root/app/build/intermediates" -path '*playRelease*' -name AndroidManifest.xml -print | head -n 1 || true)"

if [[ -z "$manifest" ]]; then
  echo "Play release merged manifest was not found."
  exit 1
fi

if grep -q 'android.permission.QUERY_ALL_PACKAGES' "$manifest"; then
  echo "Release gate failed: QUERY_ALL_PACKAGES remains in the merged Play manifest."
  exit 1
fi

echo "Play manifest gate passed: unrestricted package visibility is absent."
