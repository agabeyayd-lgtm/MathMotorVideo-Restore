#!/usr/bin/env bash
set -euo pipefail

fixture="app/build/test-fixtures/editor-test-video-10s.mp4"
asset_dir="app/src/androidTest/assets"
asset="$asset_dir/editor-test-video-10s.mp4"

if [ ! -s "$fixture" ]; then
  echo "Missing dedicated 10-second fixture: $fixture"
  exit 3
fi
mkdir -p "$asset_dir"
cp "$fixture" "$asset"
test -s "$asset"
echo "Packaged exact 10-second fixture into androidTest APK assets: $asset"
