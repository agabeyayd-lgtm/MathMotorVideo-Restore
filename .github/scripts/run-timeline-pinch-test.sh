#!/usr/bin/env bash
set -uo pipefail
set -x

fixture="app/build/test-fixtures/editor-test-video.mp4"
mkdir -p emulator-screenshots
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
set +e
gradle --no-daemon --console=plain connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mathmotor.video.TimelinePinchGestureTest
test_status=$?
set -e

# Preserve the actual instrumentation failure details before the emulator runner
# shuts the device down. The previous script exited immediately on Gradle failure,
# leaving only the generic "Process crashed" summary in the job log.
adb -s emulator-5554 logcat -d -v threadtime > emulator-screenshots/timeline-pinch-logcat.txt 2>&1 || true
adb -s emulator-5554 shell dumpsys activity processes > emulator-screenshots/timeline-pinch-processes.txt 2>&1 || true
adb -s emulator-5554 shell dumpsys activity activities > emulator-screenshots/timeline-pinch-activities.txt 2>&1 || true
if [ -d app/build/outputs/androidTest-results/connected ]; then
  mkdir -p emulator-screenshots/androidTest-results
  cp -R app/build/outputs/androidTest-results/connected/. emulator-screenshots/androidTest-results/
fi
if [ -d app/build/reports/androidTests/connected ]; then
  mkdir -p emulator-screenshots/androidTest-reports
  cp -R app/build/reports/androidTests/connected/. emulator-screenshots/androidTest-reports/
fi

exit "$test_status"
