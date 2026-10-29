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

# Always collect screenshots produced by EmulatorScreenshotTest, including the
# larger phone-sized responsive-layout checkpoint. The target app may be absent after
# connectedDebugAndroidTest, so screenshots are saved under the instrumentation APK cache.
mkdir -p emulator-screenshots/instrumentation-screenshots
test_package="$(adb -s emulator-5554 shell pm list packages 2>/dev/null | tr -d '\r' | sed 's/^package://' | grep -E '^com[.]mathmotor[.]video([.]next)?[.]test' | head -n 1 || true)"
if [ -z "$test_package" ]; then
  echo "WARNING: could not discover the instrumentation test package."
  adb -s emulator-5554 shell pm list packages > emulator-screenshots/instrumentation-packages.txt 2>&1 || true
  echo "Test APK screenshot cache could not be collected; test result remains authoritative." \
    | tee emulator-screenshots/instrumentation-screenshots-pull.txt
  exit 14
else
  echo "Pulling screenshots from instrumentation package: $test_package"
  if timeout -k 2s 12s adb -s emulator-5554 shell -T \
      "run-as $test_package tar -C cache/emulator-screenshots -cf -" \
      > emulator-screenshots/instrumentation-screenshots.tar 2>emulator-screenshots/instrumentation-screenshots-pull.txt; then
    if tar -xf emulator-screenshots/instrumentation-screenshots.tar \
        -C emulator-screenshots/instrumentation-screenshots; then
      echo "Collected instrumentation screenshots:"
      find emulator-screenshots/instrumentation-screenshots -maxdepth 1 -type f -print
      test -s emulator-screenshots/instrumentation-screenshots/editor-phone-size.png || {
        echo "WARNING: editor-phone-size.png is missing from the screenshot archive"
        exit 14
      }
    else
      echo "Could not extract instrumentation screenshot archive." >&2
      exit 14
    fi
  else
    echo "Could not pull instrumentation screenshots from $test_package" >&2
    exit 14
  fi
fi
rm -f emulator-screenshots/instrumentation-screenshots.tar

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
 | head -n 1 || true)"
if [ -z "$test_package" ]; then
  echo "WARNING: could not discover the instrumentation test package."
  adb -s emulator-5554 shell pm list packages > emulator-screenshots/instrumentation-packages.txt 2>&1 || true
  echo "Test APK screenshot cache could not be collected; test result remains authoritative." \
    | tee emulator-screenshots/instrumentation-screenshots-pull.txt
  exit 14
else
  echo "Pulling screenshots from instrumentation package: $test_package"
  if timeout -k 2s 12s adb -s emulator-5554 shell -T \
      "run-as $test_package tar -C cache/emulator-screenshots -cf -" \
      > emulator-screenshots/instrumentation-screenshots.tar 2>emulator-screenshots/instrumentation-screenshots-pull.txt; then
    if tar -xf emulator-screenshots/instrumentation-screenshots.tar \
        -C emulator-screenshots/instrumentation-screenshots; then
      echo "Collected instrumentation screenshots:"
      find emulator-screenshots/instrumentation-screenshots -maxdepth 1 -type f -print
      test -s emulator-screenshots/instrumentation-screenshots/editor-phone-size.png || {
        echo "WARNING: editor-phone-size.png is missing from the screenshot archive"
        exit 14
      }
    else
      echo "Could not extract instrumentation screenshot archive." >&2
      exit 14
    fi
  else
    echo "Could not pull instrumentation screenshots from $test_package" >&2
    exit 14
  fi
fi
rm -f emulator-screenshots/instrumentation-screenshots.tar

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
