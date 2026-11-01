#!/usr/bin/env bash
set -euo pipefail
set -x

fixture="app/build/test-fixtures/editor-test-video.mp4"
if [ ! -s "$fixture" ]; then
  echo "Missing deterministic gesture-test fixture: $fixture"
  exit 3
fi

device_state="$(timeout -k 2s 8s adb -s emulator-5554 get-state 2>/dev/null || true)"
if [ "$device_state" != "device" ]; then
  echo "Emulator is not ready for the timeline gesture test (state='$device_state')"
  adb devices -l || true
  exit 1
fi

echo "Install the debug APK for the isolated timeline gesture test"
timeout -k 3s 180s adb -s emulator-5554 install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk

echo "Stage the deterministic video directly into the app's private files directory"
timeout -k 2s 30s adb -s emulator-5554 shell -T \
  "run-as com.mathmotor.video.next sh -c 'mkdir -p files && cat > files/editor-test-video.mp4'" \
  < "$fixture"
timeout -k 2s 8s adb -s emulator-5554 shell \
  "run-as com.mathmotor.video.next test -s files/editor-test-video.mp4"

echo "Run only the targeted pinch/anchor/shared-scroll instrumentation test"
gradle --no-daemon --console=plain connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mathmotor.video.TimelinePinchGestureTest
