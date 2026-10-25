#!/usr/bin/env bash
set -uo pipefail

mkdir -p emulator-screenshots
test_status=0

echo "Run connected Android instrumentation tests and retain diagnostics"
if gradle --no-daemon connectedDebugAndroidTest > emulator-screenshots/android-test-gradle.log 2>&1; then
  test_status=0
else
  test_status=$?
fi

# Always collect these files, including on assertion failure or app/instrumentation crash.
adb -s emulator-5554 logcat -d -b all -v threadtime > emulator-screenshots/after-instrumentation-logcat.txt 2>&1 || true
mkdir -p emulator-screenshots/android-test-report emulator-screenshots/android-test-results
if [ -d app/build/reports/androidTests/connected/debug ]; then
  cp -R app/build/reports/androidTests/connected/debug/. emulator-screenshots/android-test-report/ || true
fi
if [ -d app/build/outputs/androidTest-results/connected/debug ]; then
  cp -R app/build/outputs/androidTest-results/connected/debug/. emulator-screenshots/android-test-results/ || true
fi

echo "connectedDebugAndroidTest exit code: $test_status"
echo "--- Connected test output (last 220 lines) ---"
tail -n 220 emulator-screenshots/android-test-gradle.log || true
if [ "$test_status" -ne 0 ]; then
  echo "--- Relevant Android/runtime/test failure lines ---"
  grep -E "TimelineInteractionTest|EditorTimelineInteractionTest|FATAL EXCEPTION|AndroidRuntime|AssertionError|FAILED|INSTRUMENTATION_RESULT|Process crashed" \
    emulator-screenshots/after-instrumentation-logcat.txt | tail -n 220 || true
  echo "--- Test report files ---"
  find emulator-screenshots/android-test-report emulator-screenshots/android-test-results -type f -maxdepth 4 -print || true
  grep -R -n -E "failure|error|stacktrace|Expected|AssertionError|Process crashed" \
    emulator-screenshots/android-test-report emulator-screenshots/android-test-results 2>/dev/null | tail -n 180 || true
  exit "$test_status"
fi

echo "Connected Android instrumentation tests passed."
