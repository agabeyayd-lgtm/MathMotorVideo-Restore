#!/usr/bin/env bash
set -u

screenshot_status=0
bash .github/scripts/capture-emulator-screenshots.sh || screenshot_status=$?

# Run the interaction test in the same emulator session and do not reinstall
# the APK: the screenshot flow has already staged the deterministic video in
# the app sandbox. A reinstall here can invalidate the fixture under test.
gesture_status=0
bash .github/scripts/run-timeline-pinch-test.sh || gesture_status=$?

if [ "$screenshot_status" -ne 0 ]; then
  echo "Emulator screenshot flow failed with status $screenshot_status"
  exit "$screenshot_status"
fi
exit "$gesture_status"
