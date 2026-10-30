#!/usr/bin/env bash
set -euo pipefail

asset_dir="app/src/androidTest/assets"
mkdir -p "$asset_dir"

if [[ "${INSTRUMENTATION_TEST_FILTER:-}" == *"EmulatorScreenshotTest"* ]]; then
  # The focused screenshot test imports a real sample clip after the viewport
  # restart. Package the checked-in deterministic source so the test can copy
  # it into the target app's private files directory without depending on the
  # full screenshot-preparation script having pushed files through ADB.
  fixture="app/build/test-fixtures/editor-test-video.mp4"
  asset="$asset_dir/editor-test-video.mp4"
  if [ ! -s "$fixture" ]; then
    echo "Missing deterministic screenshot fixture: $fixture"
    exit 3
  fi
  cp "$fixture" "$asset"
  test -s "$asset"
  echo "Packaged screenshot editor fixture into androidTest APK assets: $asset"
else
  fixture="app/build/test-fixtures/editor-test-video-10s.mp4"
  asset="$asset_dir/editor-test-video-10s.mp4"
  if [ ! -s "$fixture" ]; then
    echo "Missing dedicated 10-second fixture: $fixture"
    exit 3
  fi
  cp "$fixture" "$asset"
  test -s "$asset"
  echo "Packaged exact 10-second fixture into androidTest APK assets: $asset"
fi
