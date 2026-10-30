#!/usr/bin/env bash
set -uo pipefail

mkdir -p emulator-screenshots
test_status=0

echo "Run connected Android instrumentation tests and retain diagnostics"
gradle_args=(--no-daemon connectedDebugAndroidTest)
if [ -n "${INSTRUMENTATION_TEST_FILTER:-}" ]; then
  gradle_args+=("-Pandroid.testInstrumentationRunnerArguments.class=$INSTRUMENTATION_TEST_FILTER")
  echo "Running focused instrumentation selection: $INSTRUMENTATION_TEST_FILTER"
fi
if gradle "${gradle_args[@]}" > emulator-screenshots/android-test-gradle.log 2>&1; then
  test_status=0
else
  test_status=$?
fi

# The focused 10-second test deliberately excludes the already-green screenshot
# class. Preserve the existing screenshot requirements for normal full-suite runs.
mkdir -p emulator-screenshots/instrumentation-screenshots
pull_required_screenshot() {
  local name="$1"
  local remote="/sdcard/Download/MathMotorTestScreenshots/mathmotor-$name"
  local local_file="emulator-screenshots/instrumentation-screenshots/$name"
  if ! timeout -k 2s 10s adb -s emulator-5554 shell test -s "$remote"; then
    echo "Required screenshot not found: $remote" >&2
    adb -s emulator-5554 shell ls -la /sdcard/Download/MathMotorTestScreenshots \
      > emulator-screenshots/instrumentation-screenshots-pull.txt 2>&1 || true
    return 1
  fi
  if ! timeout -k 2s 12s adb -s emulator-5554 pull "$remote" "$local_file" \
      >> emulator-screenshots/instrumentation-screenshots-pull.txt 2>&1 ||
      ! test -s "$local_file"; then
    echo "Required screenshot existed on device but could not be pulled: $name" >&2
    cat emulator-screenshots/instrumentation-screenshots-pull.txt || true
    return 1
  fi
  echo "Collected $name from shared MediaStore Downloads."
}
if [ -z "${INSTRUMENTATION_TEST_FILTER:-}" ] || [[ "${INSTRUMENTATION_TEST_FILTER:-}" == *"EmulatorScreenshotTest"* ]]; then
  pull_required_screenshot "editor-phone-size.png" || exit 14
  pull_required_screenshot "editor-phone-size-loaded.png" || exit 14
else
  echo "Skipping screenshot pull because the focused instrumentation selection excludes screenshot tests."
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
