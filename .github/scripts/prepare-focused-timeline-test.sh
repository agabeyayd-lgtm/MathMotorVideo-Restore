#!/usr/bin/env bash
set -euo pipefail

mkdir -p emulator-screenshots
device_state=""
for attempt in $(seq 1 20); do
  device_state="$(timeout -k 2s 5s adb -s emulator-5554 get-state 2>/dev/null || true)"
  if [ "$device_state" = "device" ]; then break; fi
  echo "Waiting for focused-test emulator (attempt $attempt/20; state='$device_state')"
  sleep 2
done
if [ "$device_state" != "device" ]; then
  adb devices -l > emulator-screenshots/focused-test-adb-devices.txt 2>&1 || true
  echo "Focused-test emulator did not become ready"
  exit 1
fi

boot_complete=""
for attempt in $(seq 1 30); do
  boot_complete="$(timeout -k 2s 5s adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d "\r" || true)"
  if [ "$boot_complete" = "1" ]; then break; fi
  sleep 2
done
if [ "$boot_complete" != "1" ]; then
  adb -s emulator-5554 logcat -d -t 1500 > emulator-screenshots/focused-test-boot-logcat.txt 2>&1 || true
  echo "Focused-test emulator framework did not finish booting"
  exit 1
fi

echo "Install debug app for the focused exact-10-second interaction test"
timeout -k 3s 180s adb -s emulator-5554 install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk

fixture="app/build/test-fixtures/editor-test-video-10s.mp4"
if [ ! -s "$fixture" ]; then
  echo "Missing dedicated 10-second fixture: $fixture"
  exit 3
fi
if ! timeout -k 2s 30s adb -s emulator-5554 shell -T \
  "run-as com.mathmotor.video.next sh -c 'mkdir -p files && cat > files/editor-test-video-10s.mp4'" \
  < "$fixture" 2>&1 | tee emulator-screenshots/ten-second-fixture-private-copy.txt; then
  echo "Could not copy exact 10-second fixture into the app sandbox"
  adb -s emulator-5554 shell run-as com.mathmotor.video.next id > emulator-screenshots/focused-test-run-as.txt 2>&1 || true
  exit 10
fi
timeout -k 2s 8s adb -s emulator-5554 shell run-as com.mathmotor.video.next ls -l files/editor-test-video-10s.mp4 \
  | tee -a emulator-screenshots/ten-second-fixture-private-copy.txt
echo "Focused-test app setup complete; no previously-green UI smoke tests were rerun."
