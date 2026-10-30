#!/usr/bin/env bash
set -euo pipefail

if [ -n "${INSTRUMENTATION_TEST_FILTER:-}" ]; then
  bash .github/scripts/prepare-focused-timeline-test.sh
else
  bash .github/scripts/capture-emulator-screenshots.sh
fi

bash .github/scripts/run-android-instrumentation-tests.sh
