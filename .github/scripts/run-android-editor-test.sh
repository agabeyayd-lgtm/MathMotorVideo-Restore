#!/usr/bin/env bash
set +e

mkdir -p app/build/diagnostics
READY_LOG="app/build/diagnostics/emulator-readiness.txt"
echo "Waiting for Android package and activity services" | tee "$READY_LOG"

adb wait-for-device
SERVICE_READY=0
for i in $(seq 1 60); do
  PACKAGE_STATE="$(adb shell service check package 2>&1 | tr -d '\r')"
  ACTIVITY_STATE="$(adb shell service check activity 2>&1 | tr -d '\r')"
  echo "check $i: package=$PACKAGE_STATE; activity=$ACTIVITY_STATE" | tee -a "$READY_LOG"
  if echo "$PACKAGE_STATE" | grep -Eqi ": found[[:space:]]*$" && echo "$ACTIVITY_STATE" | grep -Eqi ": found[[:space:]]*$"; then
    SERVICE_READY=1
    break
  fi
  sleep 5
done

if [ "$SERVICE_READY" -ne 1 ]; then
  echo "Android system services never became ready; skipping test installation." | tee -a "$READY_LOG"
  adb logcat -b all -d -v threadtime > app/build/diagnostics/emulator-logcat.txt 2>&1
  adb shell getprop > app/build/diagnostics/emulator-properties.txt 2>&1
  adb shell dumpsys activity > app/build/diagnostics/activity-dump.txt 2>&1
  exit 1
fi

gradle --no-daemon connectedDebugAndroidTest
TEST_EXIT=$?

adb logcat -b all -d -v threadtime > app/build/diagnostics/emulator-logcat.txt 2>&1
adb shell getprop > app/build/diagnostics/emulator-properties.txt 2>&1
adb shell dumpsys activity > app/build/diagnostics/activity-dump.txt 2>&1
exit "$TEST_EXIT"
