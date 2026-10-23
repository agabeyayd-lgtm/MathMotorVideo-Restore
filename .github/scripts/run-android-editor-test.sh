#!/usr/bin/env bash
set +e

mkdir -p app/build/diagnostics
READY_LOG="app/build/diagnostics/emulator-readiness.txt"
echo "Waiting for Android boot and package/activity services to remain healthy" | tee "$READY_LOG"

adb wait-for-device
SERVICE_READY=0
STABLE_CHECKS=0
for i in $(seq 1 90); do
  BOOT_COMPLETED="$(adb shell getprop sys.boot_completed 2>&1 | tr -d '\r')"
  PACKAGE_STATE="$(adb shell service check package 2>&1 | tr -d '\r')"
  ACTIVITY_STATE="$(adb shell service check activity 2>&1 | tr -d '\r')"
  PACKAGE_PATH="$(adb shell pm path android 2>&1 | tr -d '\r')"

  if [ "$BOOT_COMPLETED" = "1" ] &&
     echo "$PACKAGE_STATE" | grep -Eqi ": found[[:space:]]*$" &&
     echo "$ACTIVITY_STATE" | grep -Eqi ": found[[:space:]]*$" &&
     echo "$PACKAGE_PATH" | grep -Eq '^package:'; then
    STABLE_CHECKS=$((STABLE_CHECKS + 1))
  else
    STABLE_CHECKS=0
  fi

  echo "check $i: boot=$BOOT_COMPLETED; package=$PACKAGE_STATE; activity=$ACTIVITY_STATE; pm_path=$PACKAGE_PATH; consecutive_healthy=$STABLE_CHECKS" | tee -a "$READY_LOG"

  if [ "$STABLE_CHECKS" -ge 3 ]; then
    SERVICE_READY=1
    break
  fi
  sleep 5
done

collect_diagnostics() {
  adb logcat -b all -d -v threadtime > app/build/diagnostics/emulator-logcat.txt 2>&1
  adb shell getprop > app/build/diagnostics/emulator-properties.txt 2>&1
  adb shell dumpsys activity > app/build/diagnostics/activity-dump.txt 2>&1
  adb shell dumpsys package > app/build/diagnostics/package-dump.txt 2>&1
}

if [ "$SERVICE_READY" -ne 1 ]; then
  echo "Android boot/package/activity services did not remain healthy; skipping test installation." | tee -a "$READY_LOG"
  collect_diagnostics
  exit 1
fi

gradle --no-daemon connectedDebugAndroidTest
TEST_EXIT=$?
collect_diagnostics
exit "$TEST_EXIT"
