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

echo "Verify the deterministic video staged by the preceding editor smoke flow"
# Do not reinstall the app here: install -r can reset app state on some runners.
# The screenshot flow already installed the exact same debug APK and staged the fixture.
timeout -k 2s 8s adb -s emulator-5554 shell \
  "run-as com.mathmotor.video.next test -s /data/user/0/com.mathmotor.video.next/files/editor-test-video.mp4"

echo "Run only the targeted pinch/anchor/shared-scroll instrumentation test"
gradle --no-daemon --console=plain connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mathmotor.video.TimelinePinchGestureTest
