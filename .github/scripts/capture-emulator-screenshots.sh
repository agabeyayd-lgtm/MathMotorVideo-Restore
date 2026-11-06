#!/usr/bin/env bash
set -euo pipefail
set -x
mkdir -p emulator-screenshots

# The emulator runner owns booting the device. Re-establish and verify ADB
# explicitly: on slow cold boots the client can otherwise hang without output.
echo "Step 1/6: start ADB server"
timeout -k 2s 8s adb start-server || true

device_state=""
for attempt in $(seq 1 15); do
  device_state="$(timeout -k 2s 4s adb -s emulator-5554 get-state 2>/dev/null || true)"
  if [ "$device_state" = "device" ]; then break; fi
  echo "Waiting for ADB device (attempt $attempt/15; state='$device_state')"
  sleep 2
done
if [ "$device_state" != "device" ]; then
  echo "ADB device did not become ready; current devices:"
  timeout -k 2s 5s adb devices -l || true
  exit 1
fi

echo "Step 2/7: wait for Android framework and package manager"
boot_complete=""
for attempt in $(seq 1 30); do
  boot_complete=$(timeout -k 1s 5s adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d "\r" || true)
  if [ "$boot_complete" = "1" ]; then
    if timeout -k 1s 8s adb -s emulator-5554 shell pm path android >/dev/null 2>&1; then
      break
    fi
  fi
  echo "Android framework not ready (attempt $attempt/30; boot=$boot_complete)"
  sleep 2
done
if [ "$boot_complete" != "1" ]; then
  echo "Android framework did not finish booting; collecting diagnostics"
  timeout -k 1s 8s adb -s emulator-5554 shell getprop > emulator-screenshots/getprop.txt 2>&1 || true
  timeout -k 1s 8s adb -s emulator-5554 logcat -d -t 500 > emulator-screenshots/boot-logcat.txt 2>&1 || true
  exit 1
fi

echo "Step 3/7: install debug APK without streaming"
# Capture Android's logs while installation runs. With set -e, a bare timeout
# exits before the artifact-upload step can preserve any useful diagnostics.
timeout -k 2s 5s adb -s emulator-5554 logcat -c || true
timeout -k 2s 5s adb -s emulator-5554 logcat -v threadtime > emulator-screenshots/install-live-logcat.txt 2>&1 &
logcat_pid=$!
if ! timeout -k 3s 180s adb -s emulator-5554 install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | tee emulator-screenshots/install-output.txt; then
  install_status=${PIPESTATUS[0]}
  echo "APK installation failed or timed out (status=$install_status); collecting diagnostics"
  kill "$logcat_pid" 2>/dev/null || true
  wait "$logcat_pid" 2>/dev/null || true
  timeout -k 2s 8s adb -s emulator-5554 shell getprop > emulator-screenshots/install-getprop.txt 2>&1 || true
  timeout -k 2s 8s adb -s emulator-5554 shell dumpsys package > emulator-screenshots/install-packages.txt 2>&1 || true
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -t 1500 > emulator-screenshots/install-logcat.txt 2>&1 || true
  exit 1
fi
kill "$logcat_pid" 2>/dev/null || true
wait "$logcat_pid" 2>/dev/null || true

echo "Step 4/7: open home activity"
timeout -k 2s 10s adb -s emulator-5554 shell am force-stop com.mathmotor.video.next || true
# Do not use am start -W here: Android may block waiting for the first frame
# on this slow emulator even after the activity has been launched. Start
# asynchronously, then poll the focused window and preserve diagnostics on failure.
timeout -k 2s 12s adb -s emulator-5554 shell am start -n com.mathmotor.video.next/com.mathmotor.video.MainActivity
home_ready=""
for attempt in $(seq 1 12); do
  focused="$(timeout -k 2s 5s adb -s emulator-5554 shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | tail -n 2 || true)"
  echo "Home launch check $attempt/12: $focused"
  if echo "$focused" | grep -q "com.mathmotor.video.next"; then
    home_ready="yes"
    break
  fi
  sleep 2
done
if [ "$home_ready" != "yes" ]; then
  echo "Home activity did not become focused; preserving launch diagnostics"
  timeout -k 2s 8s adb -s emulator-5554 shell dumpsys activity activities > emulator-screenshots/home-activities.txt 2>&1 || true
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -t 1500 > emulator-screenshots/home-launch-logcat.txt 2>&1 || true
  exit 5
fi
sleep 2

# The emulator occasionally drops its ADB transport while the UI is recovering.
# Reconnect before issuing more shell commands, and preserve diagnostics if it stays offline.
wait_for_adb_device() {
  local state=""
  for attempt in $(seq 1 10); do
    state="$(timeout -k 1s 4s adb -s emulator-5554 get-state 2>/dev/null || true)"
    if [ "$state" = "device" ]; then return 0; fi
    echo "ADB transport not ready (attempt $attempt/10; state='$state'); reconnecting"
    timeout -k 1s 4s adb reconnect offline >/dev/null 2>&1 || true
    timeout -k 1s 4s adb start-server >/dev/null 2>&1 || true
    sleep 2
  done
  timeout -k 1s 4s adb devices -l > emulator-screenshots/adb-devices-after-reconnect.txt 2>&1 || true
  return 1
}

take_screen() {
  local destination="$1"
  local remote="/sdcard/$(basename "$destination")"
  rm -f "$destination"

  # Prefer saving on-device and pulling the completed PNG. Streaming raw PNG
  # bytes over exec-out can stall on a slow emulator's shell/transport.
  if timeout -k 2s 15s adb -s emulator-5554 shell screencap -p "$remote" \
      && timeout -k 2s 10s adb -s emulator-5554 pull "$remote" "$destination" \
      && [ -s "$destination" ]; then
    return 0
  fi

  echo "Device-file screenshot failed; trying to recover ADB before exec-out fallback for $destination"
  wait_for_adb_device || true
  rm -f "$destination"
  if timeout -k 2s 15s adb -s emulator-5554 exec-out screencap -p > "$destination" \
      && [ -s "$destination" ]; then
    return 0
  fi

  rm -f "$destination"
  echo "Both screenshot paths failed for $destination"
  return 1
}

collect_capture_diagnostics() {
  local label="$1"
  echo "Collecting diagnostics after screenshot failure ($label)"
  timeout -k 2s 10s adb -s emulator-5554 logcat -d -b all -v threadtime > "emulator-screenshots/$label-logcat.txt" 2>&1 || true
  timeout -k 2s 8s adb -s emulator-5554 shell dumpsys activity activities > "emulator-screenshots/$label-activities.txt" 2>&1 || true
  timeout -k 2s 8s adb -s emulator-5554 shell dumpsys window > "emulator-screenshots/$label-window.txt" 2>&1 || true
  timeout -k 2s 8s adb -s emulator-5554 shell dumpsys SurfaceFlinger > "emulator-screenshots/$label-surfaceflinger.txt" 2>&1 || true
  timeout -k 2s 8s adb -s emulator-5554 shell ls -la /data/anr > "emulator-screenshots/$label-anr-files.txt" 2>&1 || true
}
dump_hierarchy() {
  local remote="$1" local_file="$2"
  rm -f "$local_file"
  timeout -k 2s 10s adb -s emulator-5554 shell uiautomator dump "/sdcard/$remote" >/dev/null 2>&1 || true
  timeout -k 2s 6s adb -s emulator-5554 pull "/sdcard/$remote" "$local_file" >/dev/null 2>&1 || true
  [ -s "$local_file" ]
}

echo "Step 5/7: inspect the home screen and capture it"
# Inspect accessibility before screencap so an Android ANR dialog cannot make
# the screenshot call the first point of failure without any diagnostics.
dump_hierarchy window.xml emulator-screenshots/window.xml || true
if ! grep -q "Güncelle" emulator-screenshots/window.xml; then
  echo "The redesigned home screen did not expose the prominent update action."
  collect_capture_diagnostics home-update-action-missing
  exit 30
fi

# Emulator system dialogs can appear between a hierarchy dump and a screenshot.
# Parse XML attributes instead of relying on grep's attribute order.
SYSTEM_ANR_RECOVERED=false
recover_system_anr() {
  local xml="$1"
  local remote_name
  SYSTEM_ANR_RECOVERED=false
  remote_name="$(basename "$xml")"
  for attempt in 1 2 3; do
    if [ ! -s "$xml" ] || ! grep -Eiq "isn.t responding|not responding" "$xml"; then
      return 0
    fi
    # Recover Android-owned UI ANRs, including the launcher. On cold API 35
    # boots Pixel/Nexus Launcher can briefly ANR before the app is displayed;
    # that is not an app failure. Never dismiss a MathMotor ANR.
    if ! grep -Eiq "(Process system|System UI|Pixel Launcher|Nexus Launcher|NexusLauncher|com\\.google\\.android\\.apps\\.nexuslauncher).*isn.t responding" "$xml"; then
      echo "Application-level ANR detected in $xml; refusing to dismiss it as emulator noise"
      return 1
    fi

    # The API 35 Google APIs image can leave a stalled Pixel/Nexus Launcher
    # ANR dialog above an already-running app. Close that launcher process so
    # Android can restart it; do not leave the modal dialog covering MathMotor.
    # For other explicitly system-owned ANRs, retain the non-destructive Wait action.
    local recovery_action="wait"
    if grep -Eiq "(Pixel Launcher|Nexus Launcher|NexusLauncher|com\\.google\\.android\\.apps\\.nexuslauncher).*isn.t responding" "$xml"; then
      recovery_action="close app"
    fi

    local recovery_coords
    recovery_coords="$(python3 - "$xml" "$recovery_action" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(0)

wanted = sys.argv[2]
for node in root.iter("node"):
    if node.attrib.get("text", "").strip().lower() == wanted:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)"
    if [ -z "$recovery_coords" ]; then
      echo "System ANR dialog detected but its '$recovery_action' button could not be located in $xml"
      return 1
    fi

    echo "System ANR dialog detected (attempt $attempt/3); tapping '$recovery_action' at $recovery_coords"
    timeout -k 2s 8s adb -s emulator-5554 shell input tap $recovery_coords || true
    SYSTEM_ANR_RECOVERED=true
    sleep 5
    dump_hierarchy "$remote_name" "$xml" || true
  done

  if [ -s "$xml" ] && grep -Eiq "(Process system|System UI|Pixel Launcher|Nexus Launcher|NexusLauncher|com\\.google\\.android\\.apps\\.nexuslauncher).*isn.t responding" "$xml"; then
    echo "System ANR dialog is still present after recovery attempts"
    return 1
  fi
  return 0
}

if ! recover_system_anr emulator-screenshots/window.xml; then
  collect_capture_diagnostics home-system-anr
  exit 8
fi

if ! take_screen emulator-screenshots/home.png; then
  collect_capture_diagnostics home-capture-failure
  exit 6
fi
dump_hierarchy window.xml emulator-screenshots/window.xml || true
if ! recover_system_anr emulator-screenshots/window.xml; then
  collect_capture_diagnostics home-system-anr-after-screenshot
  exit 8
fi
if [ "$SYSTEM_ANR_RECOVERED" = true ]; then
  echo "Re-capturing home after recovering the emulator system dialog"
  if ! take_screen emulator-screenshots/home.png; then collect_capture_diagnostics home-recapture-failure; exit 6; fi
  dump_hierarchy window.xml emulator-screenshots/window.xml || true
  if [ -s emulator-screenshots/window.xml ] && grep -Eiq "isn.t responding|not responding" emulator-screenshots/window.xml; then
    collect_capture_diagnostics home-anr-after-recapture
    exit 8
  fi
fi

echo "Step 6/8: capture the empty editor for layout comparison"
node=""
if [ -s emulator-screenshots/window.xml ]; then
  for target in "Boş proje oluştur" "Boş düzenleyiciyi aç" "Yeni düzenleme başlat"; do
    node="$(grep -o '<node[^>]*text="[^"]*'"$target"'[^"]*"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' emulator-screenshots/window.xml | head -n 1 || true)"
    [ -n "$node" ] && break
  done
fi

if [ -n "$node" ]; then
  coords="$(printf '%s\n' "$node" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | awk '{print int(($1+$3)/2),int(($2+$4)/2)}')"
else
  screen_size="$(timeout -k 2s 5s adb -s emulator-5554 shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1 || true)"
  if [ -n "$screen_size" ]; then
    read -r screen_w screen_h <<< "$screen_size"
    coords="$((screen_w / 2)) $((screen_h * 49 / 100))"
  else
    coords="540 940"
  fi
  echo "Accessibility hierarchy unavailable; fallback tap at $coords"
fi
[ -n "$coords" ] || { echo "Could not determine editor tap position"; exit 2; }
if ! wait_for_adb_device; then
  echo "ADB did not recover after home capture; collect diagnostics and stop before the editor tap"
  collect_capture_diagnostics home-tap-adb-offline
  exit 7
fi
if ! timeout -k 2s 8s adb -s emulator-5554 shell input tap $coords; then
  echo "Editor-launch tap failed; preserving logs instead of letting set -e discard context"
  collect_capture_diagnostics home-tap-failure
  exit 7
fi
sleep 4
if ! take_screen emulator-screenshots/editor-empty.png; then
  collect_capture_diagnostics empty-editor-capture-failure
  exit 6
fi
dump_hierarchy editor-empty-window.xml emulator-screenshots/editor-empty-window.xml || true
if ! recover_system_anr emulator-screenshots/editor-empty-window.xml; then
  collect_capture_diagnostics empty-editor-system-anr
  exit 8
fi
if [ "$SYSTEM_ANR_RECOVERED" = true ]; then
  echo "Re-capturing empty editor after recovering the emulator system dialog"
  if ! take_screen emulator-screenshots/editor-empty.png; then collect_capture_diagnostics empty-editor-recapture-failure; exit 6; fi
  dump_hierarchy editor-empty-window.xml emulator-screenshots/editor-empty-window.xml || true
  if [ -s emulator-screenshots/editor-empty-window.xml ] && grep -Eiq "isn.t responding|not responding" emulator-screenshots/editor-empty-window.xml; then
    collect_capture_diagnostics empty-editor-anr-after-recapture
    exit 8
  fi
fi

echo "Step 7/8: load a real sample video into the editor"
fixture="app/build/test-fixtures/editor-test-video.mp4"
if [ ! -s "$fixture" ]; then
  echo "Missing generated test fixture: $fixture"
  exit 3
fi
# The visual frame check samples screenshot pixels. Install Pillow only when needed.
if ! python3 -c "from PIL import Image" >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y python3-pil
fi
# Android's scoped-storage policy blocks shell access to another app's
# /sdcard/Android/data directory. Stage the fixture in /data/local/tmp, then
# copy it into this debuggable test APK's private files directory with run-as.
app_internal_video="/data/user/0/com.mathmotor.video.next/files/editor-test-video.mp4"
# Stream the fixture through ADB stdin directly into the debuggable app's
# own sandbox. This avoids both scoped-storage restrictions and SELinux
# denying the app UID access to shell-owned /data/local/tmp files.
if ! timeout -k 2s 30s adb -s emulator-5554 shell -T \
  "run-as com.mathmotor.video.next sh -c 'mkdir -p files && cat > files/editor-test-video.mp4'" \
  < "$fixture" 2>&1 | tee emulator-screenshots/video-fixture-private-copy.txt; then
  echo "Streaming the test fixture into the app sandbox failed; preserving diagnostics"
  timeout -k 2s 8s adb -s emulator-5554 shell run-as com.mathmotor.video.next id > emulator-screenshots/run-as-preflight.txt 2>&1 || true
  timeout -k 2s 8s adb -s emulator-5554 shell dumpsys package com.mathmotor.video.next > emulator-screenshots/package-debuggable.txt 2>&1 || true
  exit 10
fi
timeout -k 2s 8s adb -s emulator-5554 shell run-as com.mathmotor.video.next ls -l files/editor-test-video.mp4 2>&1 | tee -a emulator-screenshots/video-fixture-private-copy.txt

replacement_fixture="app/build/test-fixtures/editor-test-video-replacement.mp4"
app_internal_replacement_video="/data/user/0/com.mathmotor.video.next/files/editor-test-video-replacement.mp4"
if [ ! -s "$replacement_fixture" ]; then
  echo "Missing replacement video fixture: $replacement_fixture"
  exit 3
fi
if ! timeout -k 2s 30s adb -s emulator-5554 shell -T \
  "run-as com.mathmotor.video.next sh -c 'cat > files/editor-test-video-replacement.mp4'" \
  < "$replacement_fixture" 2>&1 | tee -a emulator-screenshots/video-fixture-private-copy.txt; then
  echo "Streaming the replacement video fixture into the app sandbox failed."
  exit 10
fi
timeout -k 2s 8s adb -s emulator-5554 shell run-as com.mathmotor.video.next ls -l files/editor-test-video-replacement.mp4 2>&1 | tee -a emulator-screenshots/video-fixture-private-copy.txt

overlay_fixture="app/build/test-fixtures/editor-test-overlay.png"
app_internal_overlay="/data/user/0/com.mathmotor.video.next/files/editor-test-overlay.png"
if [ ! -s "$overlay_fixture" ]; then
  echo "Missing generated overlay fixture: $overlay_fixture"
  exit 3
fi
if ! timeout -k 2s 30s adb -s emulator-5554 shell -T \
  "run-as com.mathmotor.video.next sh -c 'cat > files/editor-test-overlay.png'" \
  < "$overlay_fixture" 2>&1 | tee -a emulator-screenshots/video-fixture-private-copy.txt; then
  echo "Streaming the overlay fixture into the app sandbox failed."
  exit 10
fi
timeout -k 2s 8s adb -s emulator-5554 shell run-as com.mathmotor.video.next ls -l files/editor-test-overlay.png 2>&1 | tee -a emulator-screenshots/video-fixture-private-copy.txt


timeout -k 2s 8s adb -s emulator-5554 shell am force-stop com.mathmotor.video.next || true
# Avoid -W for the same slow-emulator first-frame wait that affected the home launch.
# Clear stale markers so only this launch can satisfy the smoke assertion.
timeout -k 2s 5s adb -s emulator-5554 logcat -c || true
timeout -k 2s 15s adb -s emulator-5554 shell am start \
  -n com.mathmotor.video.next/com.mathmotor.video.EditorTestEntryActivity \
  --es video_uri "file://$app_internal_video" \
  --es overlay_uri "file://$app_internal_overlay" \
  --es replacement_video_uri "file://$app_internal_replacement_video" \
  --es draft_id "11111111-1111-4111-8111-111111111111" \
  --ez timeline_sync_test true \
  --ef timeline_test_px_per_sec_dp 120 | tee emulator-screenshots/editor-video-launch.txt

# Do not assert that a filename appears in uiautomator XML: on these images
# uiautomator frequently fails, and the editor is free to display a friendly
# clip title rather than a source filename. Verify the real addClip outcome
# through a debug-only app log marker instead.
video_clip_loaded=false
for attempt in $(seq 1 15); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime > emulator-screenshots/video-load-logcat.txt 2>&1 || true
  if grep -q "EDITOR_SMOKE_CLIP_ADDED" emulator-screenshots/video-load-logcat.txt; then
    video_clip_loaded=true
    break
  fi
  if grep -q "EDITOR_SMOKE_CLIP_REJECTED" emulator-screenshots/video-load-logcat.txt; then
    echo "Editor rejected the fixture video while reading its duration."
    collect_capture_diagnostics video-editor-rejected-fixture
    exit 4
  fi
  echo "Waiting for editor to add the fixture clip (attempt $attempt/15)"
  sleep 2
done
if [ "$video_clip_loaded" != true ]; then
  echo "Editor did not confirm sample clip import within 30 seconds."
  collect_capture_diagnostics video-editor-clip-import-timeout
  exit 4
fi

# Verify the editor's real inner project canvas is fitted and centered inside
# the dark work stage. The deterministic fixture is 360x640 (9:16).
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s PreviewCanvasTest:I \
  > emulator-screenshots/preview-canvas-logcat.txt 2>&1 || true
python3 - emulator-screenshots/preview-canvas-logcat.txt <<'PY'
import math
import re
import sys

lines = open(sys.argv[1], errors="replace").read().splitlines()
matches = [line for line in lines if "PREVIEW_CANVAS_BOUNDS" in line]
if not matches:
    raise SystemExit("Editor did not log the measured preview project-canvas bounds.")
line = matches[-1]
match = re.search(
    r"stage=(\d+)x(\d+) canvas=(\d+)x(\d+) offset=(\d+),(\d+)(?: origin=-?\d+,-?\d+)? aspect=([0-9.]+)",
    line,
)
if not match:
    raise SystemExit(f"Malformed preview canvas bounds marker: {line}")
stage_w, stage_h, canvas_w, canvas_h, left, top = map(int, match.groups()[:6])
aspect = float(match.group(7))
expected_w = max(1, min(stage_w, int(stage_h * aspect)))
expected_h = max(1, min(stage_h, int(stage_w / aspect)))
expected_left = (stage_w - expected_w) // 2
expected_top = (stage_h - expected_h) // 2
if stage_w <= 0 or stage_h <= 0:
    raise SystemExit(f"Invalid preview work-stage size: {stage_w}x{stage_h}")
if (canvas_w, canvas_h, left, top) != (
    expected_w, expected_h, expected_left, expected_top
):
    raise SystemExit(
        "Project canvas is not aspect-fitted and centered: "
        f"stage={stage_w}x{stage_h}, observed={canvas_w}x{canvas_h}@{left},{top}, "
        f"expected={expected_w}x{expected_h}@{expected_left},{expected_top}"
    )
if abs(canvas_w / canvas_h - 9 / 16) > 0.002:
    raise SystemExit(
        f"Project canvas does not match the 9:16 source fixture: {canvas_w}x{canvas_h}"
    )
print(
    "PASS: project canvas is centered, clipped to the stage and preserves 9:16: "
    f"{canvas_w}x{canvas_h} inside {stage_w}x{stage_h} at {left},{top}"
)
PY

overlay_loaded=false
for attempt in $(seq 1 20); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime > emulator-screenshots/overlay-load-logcat.txt 2>&1 || true
  if grep -q "EDITOR_SMOKE_OVERLAY_ADDED" emulator-screenshots/overlay-load-logcat.txt; then
    overlay_loaded=true
    break
  fi
  echo "Waiting for paused overlay fixture to be inserted (attempt $attempt/20)"
  sleep 2
done
if [ "$overlay_loaded" != true ]; then
  echo "Editor did not confirm overlay insertion in the paused preview."
  collect_capture_diagnostics overlay-editor-import-timeout
  exit 32
fi

# Regression: after the overlay is present, the debug bridge calls the production
# removeClipAt() path for the original red/hue-cycling source, then imports a
# distinct solid-green video through addClip(replaceExisting=true).
replacement_complete=false
for attempt in $(seq 1 30); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime > emulator-screenshots/video-replacement-logcat.txt 2>&1 || true
  if grep -Fq "EDITOR_SMOKE_CLIP_REPLACED newUri=file://$app_internal_replacement_video" emulator-screenshots/video-replacement-logcat.txt \
     && grep -Fq "EDITOR_SMOKE_REPLACEMENT_THUMBNAIL_READY uri=file://$app_internal_replacement_video" emulator-screenshots/video-replacement-logcat.txt; then
    replacement_complete=true
    break
  fi
  if grep -q "EDITOR_SMOKE_REPLACEMENT_SKIPPED" emulator-screenshots/video-replacement-logcat.txt; then
    echo "Video replacement regression path was skipped."
    collect_capture_diagnostics video-replacement-skipped
    exit 41
  fi
  echo "Waiting for old-video removal, new-video import, and its first exact thumbnail (attempt $attempt/30)"
  sleep 2
done
if [ "$replacement_complete" != true ]; then
  echo "Old filmstrip was not replaced by the new video fixture within 60 seconds."
  collect_capture_diagnostics video-replacement-timeout
  exit 42
fi
if ! grep -F "EDITOR_SMOKE_CLIP_REPLACED newUri=file://$app_internal_replacement_video" emulator-screenshots/video-replacement-logcat.txt | grep -Fq "clipStartMs=0"; then
  echo "Gallery multi-select replacement did not place its first video at 00:00."
  collect_capture_diagnostics video-replacement-not-at-zero
  exit 46
fi
if ! take_screen emulator-screenshots/editor-after-video-replacement.png; then
  collect_capture_diagnostics video-replacement-capture-failure
  exit 6
fi
dump_hierarchy editor-after-video-replacement.xml emulator-screenshots/editor-after-video-replacement.xml || true
if ! grep -Fq "editor-test-video-replacement.mp4" emulator-screenshots/editor-after-video-replacement.xml; then
  echo "The visible video lane does not identify the replacement source."
  collect_capture_diagnostics video-replacement-source-not-visible
  exit 43
fi
if grep -Fq "editor-test-video.mp4" emulator-screenshots/editor-after-video-replacement.xml; then
  echo "The deleted source is still exposed in the current video lane hierarchy."
  collect_capture_diagnostics old-video-still-in-lane
  exit 44
fi
replacement_visual_ready=false
for attempt in $(seq 1 25); do
  if ! take_screen emulator-screenshots/editor-after-video-replacement.png; then
    collect_capture_diagnostics video-replacement-capture-failure
    exit 6
  fi
  replacement_green_ratio="$(python3 - emulator-screenshots/editor-after-video-replacement.xml emulator-screenshots/editor-after-video-replacement.png <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
from PIL import Image

try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError) as error:
    raise SystemExit(f"Cannot parse replacement editor hierarchy: {error}")

bounds = None
for node in root.iter("node"):
    desc = node.attrib.get("content-desc", "")
    if desc.startswith("Klip 1:") and "editor-test-video-replacement.mp4" in desc:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            bounds = tuple(map(int, match.groups()))
            break
if bounds is None:
    raise SystemExit("Replacement clip accessibility bounds were not found.")

x1, y1, x2, y2 = bounds
image = Image.open(sys.argv[2]).convert("RGB")
crop = image.crop((x1 + 3, y1 + 3, x2 - 3, y2 - 3))
pixels = list(crop.getdata())
green = sum(1 for r, g, b in pixels if g > 115 and g > r * 1.45 and g > b * 1.20)
ratio = green / max(1, len(pixels))
print(f"{ratio:.4f}")
PY
)"
  echo "Replacement filmstrip visible green-pixel ratio attempt $attempt/25: $replacement_green_ratio"
  if python3 - "$replacement_green_ratio" <<'PY'
import sys
try:
    ratio = float(sys.argv[1])
except (ValueError, IndexError):
    raise SystemExit(1)
raise SystemExit(0 if ratio >= 0.75 else 1)
PY
  then
    replacement_visual_ready=true
    break
  fi
  sleep 2
done
if [ "$replacement_visual_ready" != true ]; then
  echo "Replacement filmstrip did not finish rendering the visible green frames; stale/blank cells remain."
  collect_capture_diagnostics video-replacement-filmstrip-not-fully-refreshed
  exit 45
fi
echo "PASS: the visible replacement filmstrip is populated with the new green source frames (ratio >= 0.75)."

# Select the main-video timeline clip and verify that its white transform
# selection frame appears around the transformed source canvas.
video_clip_center="$(python3 - emulator-screenshots/editor-after-video-replacement.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
candidates = []
for node in root.iter("node"):
    desc = node.attrib.get("content-desc", "")
    text = node.attrib.get("text", "")
    if "editor-test-video-replacement.mp4" not in desc + " " + text:
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        if x2 > x1 and y2 > y1:
            candidates.append((node.attrib.get("clickable") == "true", (x1 + x2) // 2, (y1 + y2) // 2))
if not candidates:
    raise SystemExit("Could not locate the replacement video timeline clip hit target.")
candidates.sort(reverse=True)
print(candidates[0][1], candidates[0][2])
PY
)"
if [ -z "$video_clip_center" ]; then
  echo "Could not find the replacement video clip to test selection chrome."
  collect_capture_diagnostics video-clip-selection-target-missing
  exit 56
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $video_clip_center
sleep 1
if ! take_screen emulator-screenshots/editor-video-clip-selected.png; then
  collect_capture_diagnostics video-clip-selection-capture-failure
  exit 6
fi
dump_hierarchy editor-video-clip-selected.xml emulator-screenshots/editor-video-clip-selected.xml || true
if ! grep -q "Seçili video klibi sınırı" emulator-screenshots/editor-video-clip-selected.xml; then
  echo "Selecting the main-video timeline clip did not show its white preview selection frame."
  collect_capture_diagnostics video-clip-selection-frame-missing
  exit 57
fi
echo "PASS: selecting the main video clip shows its own white transform selection frame."

# Return to the global dock, then tap the visible overlay to restore its own
# selection and tools before the visual-overlay direct-manipulation checks.
video_tools_back="$(python3 - emulator-screenshots/editor-video-clip-selected.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
matches = []
for node in root.iter("node"):
    label = node.attrib.get("text", "") + " " + node.attrib.get("content-desc", "")
    if "Ana araçlara dön" not in label:
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        if x2 > x1 and y2 > y1:
            matches.append(((x1 + x2) // 2, (y1 + y2) // 2))
if not matches:
    raise SystemExit("Video contextual tool strip return button was not found.")
print(*matches[-1])
PY
)"
if [ -z "$video_tools_back" ]; then
  collect_capture_diagnostics video-clip-tools-return-missing
  exit 58
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $video_tools_back
sleep 1
if ! take_screen emulator-screenshots/editor-video-clip-deselected.png; then
  collect_capture_diagnostics video-clip-return-capture-failure
  exit 6
fi
overlay_reselect_center="$(python3 - emulator-screenshots/editor-video-clip-deselected.png <<'PY'
from PIL import Image
import sys

image = Image.open(sys.argv[1]).convert("RGB")
points = []
for y in range(image.height):
    for x in range(image.width):
        red, green, blue = image.getpixel((x, y))
        if red > 180 and blue > 180 and green < 110:
            points.append((x, y))
if len(points) < 30:
    raise SystemExit("Magenta overlay fixture is not visible after returning from video selection.")
xs = [x for x, _ in points]
ys = [y for _, y in points]
print((min(xs) + max(xs)) // 2, (min(ys) + max(ys)) // 2)
PY
)"
if [ -z "$overlay_reselect_center" ]; then
  collect_capture_diagnostics overlay-reselect-after-video-selection-missing
  exit 59
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $overlay_reselect_center
sleep 1
if ! take_screen emulator-screenshots/editor.png; then
  collect_capture_diagnostics editor-recapture-after-video-selection-failure
  exit 6
fi
dump_hierarchy editor-window.xml emulator-screenshots/editor-window.xml || true

sleep 1
if ! take_screen emulator-screenshots/editor.png; then
  collect_capture_diagnostics video-editor-capture-failure
  exit 6
fi
dump_hierarchy editor-window.xml emulator-screenshots/editor-window.xml || true
if ! recover_system_anr emulator-screenshots/editor-window.xml; then
  collect_capture_diagnostics video-editor-system-anr
  exit 8
fi
if [ "$SYSTEM_ANR_RECOVERED" = true ]; then
  echo "Re-capturing video editor after recovering the emulator system dialog"
  if ! take_screen emulator-screenshots/editor.png; then collect_capture_diagnostics video-editor-recapture-failure; exit 6; fi
  dump_hierarchy editor-window.xml emulator-screenshots/editor-window.xml || true
  if [ -s emulator-screenshots/editor-window.xml ] && grep -Eiq "isn.t responding|not responding" emulator-screenshots/editor-window.xml; then
    collect_capture_diagnostics video-editor-anr-after-recapture
    exit 8
  fi
fi

# The app's success marker is emitted only after metadata is readable and the
# clip is inserted into the timeline, so inaccessible UI hierarchy is diagnostic
# only and does not turn a successful import into a false failure.

echo "Step 8/12: verify the one-row clip tool strip and its upward detail panel"
find_bottom_tool() {
  local hierarchy="$1"
  local tool="$2"
  python3 - "$hierarchy" "$tool" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(1)

target = sys.argv[2].casefold()
matches = []
for node in root.iter("node"):
    # Match the visible button text first. Only accept an exact content-desc
    # fallback: substring matching would confuse "Araçlara dön" with the
    # Geri button's description, "Ana araçlara dön".
    visible_text = node.attrib.get("text", "").casefold()
    content_desc = node.attrib.get("content-desc", "").casefold()
    match_kind = 2 if target in visible_text else (1 if target == content_desc else 0)
    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match_kind and bounds:
        x1, y1, x2, y2 = map(int, bounds.groups())
        if x2 > x1 and y2 > y1:
            matches.append((match_kind, y1, (x1 + x2) // 2, (y1 + y2) // 2))
if not matches:
    raise SystemExit(1)
_, _, x, y = max(matches)
print(x, y)
PY
}

# The sample overlay must be selected in the paused preview, not hidden by a
# settings sheet. Confirm its contextual dock and direct canvas gesture target.
for overlay_tool in "Bindirme ekle" "Böl" "Birleştir" "Animasyonlar" "Sil"; do
  if [ -z "$(find_bottom_tool emulator-screenshots/editor-window.xml "$overlay_tool" || true)" ]; then
    echo "Selected overlay dock is missing '$overlay_tool'."
    collect_capture_diagnostics "overlay-tool-missing"
    exit 33
  fi
done
if ! grep -q "Bindirmeyi önizlemede tek parmakla taşı" emulator-screenshots/editor-window.xml; then
  echo "Selected overlay does not expose its direct-canvas touch surface."
  collect_capture_diagnostics overlay-canvas-touch-surface-missing
  exit 34
fi
cp emulator-screenshots/editor.png emulator-screenshots/editor-overlay-paused.png
magenta_center() {
  python3 - "$1" <<'PY'
from PIL import Image
import sys
image = Image.open(sys.argv[1]).convert("RGB")
points = []
for y in range(image.height):
    for x in range(image.width):
        red, green, blue = image.getpixel((x, y))
        if red > 180 and blue > 180 and green < 110:
            points.append((x, y))
if len(points) < 30:
    raise SystemExit("Paused overlay pixels were not rendered (magenta sample not found)")
xs = [point[0] for point in points]
ys = [point[1] for point in points]
print((min(xs) + max(xs)) // 2, (min(ys) + max(ys)) // 2, len(points))
PY
}

# Leave the selected overlay tools, tap the visible layer itself, and require
# the overlay-specific dock and gesture surface to reopen from that canvas tap.
overlay_back_coords="$(find_bottom_tool emulator-screenshots/editor-window.xml "Ana araçlara dön" || true)"
if [ -z "$overlay_back_coords" ]; then
  echo "Could not find the overlay tools' return control before preview tap selection."
  collect_capture_diagnostics overlay-tap-select-back-missing
  exit 47
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $overlay_back_coords
sleep 1
dump_hierarchy editor-overlay-tools-closed.xml emulator-screenshots/editor-overlay-tools-closed.xml || true
if ! take_screen emulator-screenshots/editor-overlay-unselected.png; then
  collect_capture_diagnostics overlay-tap-select-target-capture-failure
  exit 6
fi
overlay_target="$(magenta_center emulator-screenshots/editor-overlay-unselected.png)"
read -r overlay_target_x overlay_target_y overlay_target_pixels <<< "$overlay_target"
timeout -k 2s 8s adb -s emulator-5554 shell input tap "$overlay_target_x" "$overlay_target_y"
sleep 2
if ! take_screen emulator-screenshots/editor-overlay-reselected.png; then
  collect_capture_diagnostics overlay-tap-select-result-capture-failure
  exit 6
fi
dump_hierarchy editor-overlay-reselected.xml emulator-screenshots/editor-overlay-reselected.xml || true
for overlay_tool in "Bindirme ekle" "Böl" "Birleştir" "Animasyonlar" "Sil"; do
  if [ -z "$(find_bottom_tool emulator-screenshots/editor-overlay-reselected.xml "$overlay_tool" || true)" ]; then
    echo "Tapping a visual layer did not reopen its contextual dock ('$overlay_tool' missing)."
    collect_capture_diagnostics overlay-tap-select-did-not-reopen-tools
    exit 48
  fi
done
if ! grep -q "Bindirmeyi önizlemede tek parmakla taşı" emulator-screenshots/editor-overlay-reselected.xml; then
  echo "Tapping the visual layer did not reactivate its direct-canvas gesture surface."
  collect_capture_diagnostics overlay-tap-select-gesture-surface-missing
  exit 49
fi
echo "PASS: tapping an unselected visual layer on the preview reopens its layer tools and gesture surface."
cp emulator-screenshots/editor-overlay-reselected.png emulator-screenshots/editor-overlay-paused.png
before_overlay="$(magenta_center emulator-screenshots/editor-overlay-paused.png)"
read -r before_x before_y before_pixels <<< "$before_overlay"
echo "Paused overlay rendered at ($before_x,$before_y) with $before_pixels magenta pixels."
drag_end_x="$((before_x + 22))"
drag_end_y="$((before_y + 14))"
timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$before_x" "$before_y" "$drag_end_x" "$drag_end_y" 500
sleep 2
if ! take_screen emulator-screenshots/editor-overlay-after-drag.png; then
  collect_capture_diagnostics overlay-canvas-drag-capture-failure
  exit 6
fi
after_overlay="$(magenta_center emulator-screenshots/editor-overlay-after-drag.png)"
read -r after_x after_y after_pixels <<< "$after_overlay"
# The selected visual layer must be clipped to the actual project-canvas
# rectangle measured in the Activity, not inferred from unrelated green UI pixels.
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s PreviewCanvasTest:I \
  > emulator-screenshots/preview-canvas-logcat-after-drag.txt 2>&1 || true
python3 - emulator-screenshots/preview-canvas-logcat-after-drag.txt emulator-screenshots/editor-overlay-after-drag.png <<'PY'
from PIL import Image
import re
import sys

log_path, screenshot_path = sys.argv[1:]
matches = [
    line for line in open(log_path, errors="replace")
    if "PREVIEW_CANVAS_BOUNDS" in line
]
if not matches:
    raise SystemExit("Measured preview canvas bounds were not logged after the overlay drag.")
match = re.search(
    r"stage=(\d+)x(\d+) canvas=(\d+)x(\d+) "
    r"offset=(\d+),(\d+) origin=(-?\d+),(-?\d+) aspect=([0-9.]+)",
    matches[-1],
)
if not match:
    raise SystemExit(f"Malformed measured canvas bounds: {matches[-1]}")
(
    stage_w, stage_h, canvas_w, canvas_h,
    offset_x, offset_y, origin_x, origin_y
) = map(int, match.groups()[:8])
canvas_left = origin_x + offset_x
canvas_top = origin_y + offset_y
canvas_right = canvas_left + canvas_w - 1
canvas_bottom = canvas_top + canvas_h - 1

image = Image.open(screenshot_path).convert("RGB")
magenta = []
for y in range(image.height):
    for x in range(image.width):
        red, green, blue = image.getpixel((x, y))
        if red > 180 and blue > 180 and green < 110:
            magenta.append((x, y))
if len(magenta) < 30:
    raise SystemExit("Paused overlay pixels were not visible after direct drag.")
outside = [
    (x, y) for x, y in magenta
    if x < canvas_left or x > canvas_right or y < canvas_top or y > canvas_bottom
]
if outside:
    raise SystemExit(
        "Overlay pixels escaped the actual project canvas: "
        f"canvas=({canvas_left},{canvas_top})-({canvas_right},{canvas_bottom}), "
        f"outside_count={len(outside)}"
    )

# The turquoise canvas border is a workspace guide, outside the inner canvas
# content and therefore outside the exported frame. Verify all four edges.
def turquoise(pixel):
    red, green, blue = pixel
    return red < 130 and green > 145 and blue > 120

edge_hits = {"left": 0, "right": 0, "top": 0, "bottom": 0}
for y in range(max(0, canvas_top - 3), min(image.height, canvas_bottom + 4)):
    for x in range(max(0, canvas_left - 3), min(image.width, canvas_right + 4)):
        if not turquoise(image.getpixel((x, y))):
            continue
        if abs(x - canvas_left) <= 3:
            edge_hits["left"] += 1
        if abs(x - canvas_right) <= 3:
            edge_hits["right"] += 1
        if abs(y - canvas_top) <= 3:
            edge_hits["top"] += 1
        if abs(y - canvas_bottom) <= 3:
            edge_hits["bottom"] += 1
missing_edges = [name for name, count in edge_hits.items() if count < 3]
if missing_edges:
    raise SystemExit(
        "Turquoise project-canvas outline is not visible on all four edges: "
        + ", ".join(missing_edges)
        + f"; edge hits={edge_hits}"
    )
print(
    "PASS: overlay pixels are clipped to the canvas and the separate turquoise "
    f"workspace border is visible on all four edges ({edge_hits})"
)
PY
if [ "$((after_x - before_x))" -lt 12 ] || [ "$((after_x - before_x))" -gt 34 ] ||
   [ "$((after_y - before_y))" -lt 4 ] || [ "$((after_y - before_y))" -gt 26 ]; then
  echo "Direct canvas drag did not move the paused overlay by the expected amount: before=($before_x,$before_y), after=($after_x,$after_y)."
  collect_capture_diagnostics overlay-canvas-drag-not-applied
  exit 35
fi
echo "PASS: paused overlay was visible and moved directly on the canvas without pressing Play."
overlay_back_coords="$(find_bottom_tool emulator-screenshots/editor-window.xml "Ana araçlara dön" || true)"
if [ -z "$overlay_back_coords" ]; then
  echo "Could not find the overlay row's return-to-main-dock control."
  collect_capture_diagnostics overlay-main-back-missing
  exit 36
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $overlay_back_coords
sleep 1
dump_hierarchy editor-main-dock.xml emulator-screenshots/editor-main-dock.xml || true

clip_tool_coords="$(find_bottom_tool emulator-screenshots/editor-main-dock.xml "Düzenle" || true)"
if [ -z "$clip_tool_coords" ]; then
  echo "Could not locate the bottom Düzenle tool; refusing to guess its tap coordinates."
  collect_capture_diagnostics editor-panel-edit-tool-missing
  exit 25
fi
echo "Opening the one-row clip tool strip at $clip_tool_coords"
timeout -k 2s 8s adb -s emulator-5554 shell input tap $clip_tool_coords
sleep 1
if ! take_screen emulator-screenshots/editor-panel-clip-open.png; then
  collect_capture_diagnostics editor-panel-open-capture-failure
  exit 6
fi
dump_hierarchy editor-panel-clip-open.xml emulator-screenshots/editor-panel-clip-open.xml || true

trim_tool_coords="$(find_bottom_tool emulator-screenshots/editor-panel-clip-open.xml "Kırp" || true)"
if [ -z "$trim_tool_coords" ]; then
  echo "The clip tool strip did not expose the Kırp option."
  collect_capture_diagnostics editor-panel-trim-tool-missing
  exit 26
fi
echo "Opening the upward-growing trim detail panel at $trim_tool_coords"
timeout -k 2s 8s adb -s emulator-5554 shell input tap $trim_tool_coords
sleep 1
if ! take_screen emulator-screenshots/editor-panel-trim.png; then
  collect_capture_diagnostics editor-panel-trim-capture-failure
  exit 6
fi
dump_hierarchy editor-panel-trim.xml emulator-screenshots/editor-panel-trim.xml || true

trim_back_coords="$(find_bottom_tool emulator-screenshots/editor-panel-trim.xml "Araçlara dön" || true)"
if [ -z "$trim_back_coords" ]; then
  echo "The expanded trim panel did not expose its return-to-tools control."
  collect_capture_diagnostics editor-panel-trim-back-missing
  exit 27
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $trim_back_coords
sleep 1
dump_hierarchy editor-panel-tool-strip-after-trim.xml emulator-screenshots/editor-panel-tool-strip-after-trim.xml || true

volume_tool_coords="$(find_bottom_tool emulator-screenshots/editor-panel-tool-strip-after-trim.xml "Ses düzeyi" || true)"
if [ -z "$volume_tool_coords" ]; then
  echo "The clip tool strip did not expose the Ses düzeyi option after trim details closed."
  collect_capture_diagnostics editor-panel-volume-tool-missing
  exit 26
fi
echo "Opening the upward-growing volume detail panel at $volume_tool_coords"
timeout -k 2s 8s adb -s emulator-5554 shell input tap $volume_tool_coords
sleep 1
if ! take_screen emulator-screenshots/editor-panel-audio.png; then
  collect_capture_diagnostics editor-panel-detail-capture-failure
  exit 6
fi
dump_hierarchy editor-panel-audio.xml emulator-screenshots/editor-panel-audio.xml || true

detail_back_coords="$(find_bottom_tool emulator-screenshots/editor-panel-audio.xml "Araçlara dön" || true)"
if [ -z "$detail_back_coords" ]; then
  echo "The expanded volume panel did not expose its return-to-tools control."
  collect_capture_diagnostics editor-panel-detail-back-missing
  exit 27
fi
echo "Returning from expanded detail to the single tool strip at $detail_back_coords"
timeout -k 2s 8s adb -s emulator-5554 shell input tap $detail_back_coords
sleep 1
if ! take_screen emulator-screenshots/editor-panel-tool-strip.png; then
  collect_capture_diagnostics editor-panel-strip-return-capture-failure
  exit 6
fi
dump_hierarchy editor-panel-tool-strip.xml emulator-screenshots/editor-panel-tool-strip.xml || true

main_back_coords="$(find_bottom_tool emulator-screenshots/editor-panel-tool-strip.xml "Ana araçlara dön" || true)"
if [ -z "$main_back_coords" ]; then
  echo "The contextual strip did not expose its return-to-main-dock control."
  collect_capture_diagnostics editor-panel-main-back-missing
  exit 28
fi
echo "Restoring the main editor dock at $main_back_coords"
timeout -k 2s 8s adb -s emulator-5554 shell input tap $main_back_coords
sleep 1
if ! take_screen emulator-screenshots/editor-panel-closed.png; then
  collect_capture_diagnostics editor-panel-close-capture-failure
  exit 6
fi
dump_hierarchy editor-panel-closed.xml emulator-screenshots/editor-panel-closed.xml || true
timeout -k 2s 10s adb -s emulator-5554 logcat -d -v threadtime -s EditorPanelLayoutTest:I > emulator-screenshots/editor-panel-layout-logcat.txt 2>&1 || true

python3 - emulator-screenshots/editor-panel-layout-logcat.txt <<'PY'
import re
import sys

states = {}
pattern = re.compile(
    r"EDITOR_PANEL_GEOMETRY state=([^ ]+) "
    r"preview=(\d+x\d+@\d+,\d+) "
    r"timeline=(\d+x\d+@\d+,\d+)"
)
for line in open(sys.argv[1], errors="replace"):
    match = pattern.search(line)
    if match:
        states[match.group(1)] = (match.group(2), match.group(3))

required = [
    "before-open:clip-tools",
    "clip-tools",
    "before-open:clip",
    "open:clip",
    "before-open:clipVolume",
    "open:clipVolume",
    "clip-tool-strip",
    "closed",
]
missing = [state for state in required if state not in states]
if missing:
    raise SystemExit("Missing two-stage editor geometry markers: " + ", ".join(missing))
baseline = states["before-open:clip-tools"]
for state in required[1:]:
    if states[state] != baseline:
        raise SystemExit(
            f"Editor layout moved during {state}: baseline={baseline}, observed={states[state]}"
        )
print("PASS: preview and timeline stayed fixed while the one-row tool strip opened, detail rose above it, the strip returned, and the main dock was restored.")
PY

echo "Step 8b/12: open the Proje panel and prove the MP4 button receives a real tap"
proje_coords="$(find_bottom_tool emulator-screenshots/editor-panel-closed.xml "Proje" || true)"
if [ -z "$proje_coords" ]; then
  echo "Could not locate the Proje tab before testing MP4 export."
  collect_capture_diagnostics mp4-export-project-tab-missing
  exit 37
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $proje_coords
sleep 1
dump_hierarchy editor-export-panel.xml emulator-screenshots/editor-export-panel.xml || true
find_mp4_export_button() {
  python3 - "$1" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(1)
for node in root.iter("node"):
    text = " ".join(node.attrib.get("text", "").split()).casefold()
    words = text.split()
    # Turkish case-folding maps capital dotted/dotless I differently from
    # a hand-written lowercase literal. Match the invariant words instead.
    is_mp4_export = len(words) >= 3 and words[0] == "mp4" and words[-1] == "aktar"
    if is_mp4_export and node.attrib.get("class") == "android.widget.Button":
        if node.attrib.get("clickable") != "true" or node.attrib.get("enabled") != "true":
            continue
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            if x2 > x1 and y2 > y1:
                print((x1 + x2) // 2, (y1 + y2) // 2)
                break
PY
}
mp4_coords="$(find_mp4_export_button emulator-screenshots/editor-export-panel.xml || true)"
if [ -z "$mp4_coords" ]; then
  echo "The MP4 button is not visible/clickable in the initial Proje panel viewport."
  collect_capture_diagnostics mp4-export-button-not-visible
  exit 38
fi
echo "Tapping the visible, enabled MP4 export button at $mp4_coords"
timeout -k 2s 8s adb -s emulator-5554 shell input tap $mp4_coords
export_started=false
for attempt in $(seq 1 20); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorExportTest:V > emulator-screenshots/mp4-export-tap-logcat.txt 2>&1 || true
  if grep -q "EXPORT_TRANSFORMER_STARTED" emulator-screenshots/mp4-export-tap-logcat.txt; then
    export_started=true
    break
  fi
  if grep -qE "EXPORT_GUARD_NO_CLIPS|EXPORT_GUARD_INVALID_CLIP|EXPORT_COMPOSITION_FAILED|EXPORT_TRANSFORMER_START_FAILED|EXPORT_TRANSFORMER_BUILD_FAILED" emulator-screenshots/mp4-export-tap-logcat.txt; then
    break
  fi
  echo "Waiting for the MP4 tap to reach Transformer.start (attempt $attempt/20)"
  sleep 2
done
if [ "$export_started" != true ]; then
  echo "The MP4 button tap did not reach a successful Transformer.start call."
  collect_capture_diagnostics mp4-export-button-tap-failed
  exit 39
fi
export_saved=false
for attempt in $(seq 1 40); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorExportTest:V > emulator-screenshots/mp4-export-tap-logcat.txt 2>&1 || true
  if grep -q "EXPORT_OUTPUT_SAVED" emulator-screenshots/mp4-export-tap-logcat.txt; then
    export_saved=true
    break
  fi
  if grep -qE "EXPORT_ENCODING_FAILED|EXPORT_OUTPUT_SAVE_FAILED" emulator-screenshots/mp4-export-tap-logcat.txt; then
    break
  fi
  echo "Waiting for a saved MP4 output from the fixture export (attempt $attempt/40)"
  sleep 2
done
if [ "$export_saved" != true ]; then
  echo "The Transformer started, but no successfully saved MP4 was confirmed."
  collect_capture_diagnostics mp4-export-output-not-saved
  exit 40
fi
if ! take_screen emulator-screenshots/editor-export-saved.png; then
  collect_capture_diagnostics mp4-export-saved-capture-failure
  exit 6
fi
# Close the Proje drawer so it cannot cover the timeline checks that follow.
timeout -k 2s 8s adb -s emulator-5554 shell input tap $proje_coords
sleep 1
dump_hierarchy editor-export-closed.xml emulator-screenshots/editor-export-closed.xml || true
echo "PASS: visible MP4 button tap reached the export handler, started Transformer, saved an MP4 output, and closed the drawer."

echo "Step 8c/12: add text, verify its canvas selection frame, and reselect it by tapping the preview"
text_tab_coords="$(find_bottom_tool emulator-screenshots/editor-export-closed.xml "Metin" || true)"
if [ -z "$text_tab_coords" ]; then
  echo "Could not locate the Metin tab to verify direct text selection."
  collect_capture_diagnostics text-canvas-tab-missing
  exit 50
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $text_tab_coords
sleep 1
dump_hierarchy editor-text-panel.xml emulator-screenshots/editor-text-panel.xml || true
new_text_coords="$(find_bottom_tool emulator-screenshots/editor-text-panel.xml "＋ Yeni" || true)"
if [ -z "$new_text_coords" ]; then
  echo "The Metin panel did not expose its Yeni text-layer action."
  collect_capture_diagnostics text-canvas-add-action-missing
  exit 51
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $new_text_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 shell input text "CanvasSmoke"
sleep 1
timeout -k 2s 8s adb -s emulator-5554 shell input keyevent 4
sleep 1
if ! take_screen emulator-screenshots/editor-text-selected.png; then
  collect_capture_diagnostics text-canvas-selection-capture-failure
  exit 6
fi
dump_hierarchy editor-text-selected.xml emulator-screenshots/editor-text-selected.xml || true
python3 - emulator-screenshots/editor-text-selected.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
selection = []
text_nodes = []
for node in root.iter("node"):
    desc = node.attrib.get("content-desc", "")
    value = node.attrib.get("text", "")
    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if bounds:
        box = tuple(map(int, bounds.groups()))
        if desc == "Seçili metin katmanı sınırı":
            selection.append(box)
        if value == "CanvasSmoke" and node.attrib.get("class") == "android.widget.TextView":
            text_nodes.append(box)
if not selection:
    raise SystemExit("Selected text layer did not expose its white canvas selection frame.")
if not text_nodes:
    raise SystemExit("New text is missing from the visible preview accessibility tree.")
print(f"PASS: selected text has a visible selection box {selection[-1]} and preview text bounds {text_nodes[-1]}")
PY

text_panel_back="$(find_bottom_tool emulator-screenshots/editor-text-selected.xml "Araçlara dön" || true)"
if [ -z "$text_panel_back" ]; then
  echo "Text detail panel did not expose its return-to-tools control."
  collect_capture_diagnostics text-canvas-panel-back-missing
  exit 52
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $text_panel_back
sleep 1
dump_hierarchy editor-text-panel-closed.xml emulator-screenshots/editor-text-panel-closed.xml || true
text_preview_center="$(python3 - emulator-screenshots/editor-text-panel-closed.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("text") != "CanvasSmoke":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)"
if [ -z "$text_preview_center" ]; then
  echo "Could not locate the visible text layer after closing its settings panel."
  collect_capture_diagnostics text-canvas-preview-hit-target-missing
  exit 53
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $text_preview_center
sleep 1
if ! take_screen emulator-screenshots/editor-text-reselected.png; then
  collect_capture_diagnostics text-canvas-reselection-capture-failure
  exit 6
fi
dump_hierarchy editor-text-reselected.xml emulator-screenshots/editor-text-reselected.xml || true
for text_tool in "Düzenle" "Sil"; do
  if [ -z "$(find_bottom_tool emulator-screenshots/editor-text-reselected.xml "$text_tool" || true)" ]; then
    echo "Tapping the text in the preview did not reopen its text tool strip ('$text_tool' missing)."
    collect_capture_diagnostics text-canvas-tap-did-not-reopen-tools
    exit 54
  fi
done
if ! grep -q "Seçili metin katmanı sınırı" emulator-screenshots/editor-text-reselected.xml; then
  echo "Tapping the preview text did not restore its canvas selection frame."
  collect_capture_diagnostics text-canvas-tap-selection-frame-missing
  exit 55
fi
echo "PASS: tapping text in the preview restores the text layer tool strip and white selection frame."
text_delete_coords="$(find_bottom_tool emulator-screenshots/editor-text-reselected.xml "Sil" || true)"
timeout -k 2s 8s adb -s emulator-5554 shell input tap $text_delete_coords
sleep 1
text_main_back="$(find_bottom_tool emulator-screenshots/editor-text-reselected.xml "Ana araçlara dön" || true)"
if [ -n "$text_main_back" ]; then
  timeout -k 2s 8s adb -s emulator-5554 shell input tap $text_main_back
  sleep 1
fi
dump_hierarchy editor-text-cleanup.xml emulator-screenshots/editor-text-cleanup.xml || true

echo "Step 9/12: align a thumbnail cell with the playhead and compare the rendered pixels"
# HorizontalScrollView consumes ViewConfiguration.scaledTouchSlop before it
# scrolls. Android reports integer-pixel view bounds and ADB gesture coordinates;
# accept a 5px center residual (under 3dp on this emulator) rather than treating
# coordinate rounding as a timeline synchronization failure.
touch_slop_px="$(timeout -k 2s 8s adb -s emulator-5554 logcat -d -v brief -s TimelineSyncTest:I \
  | sed -n 's/.*TIMELINE_TOUCH_SLOP_PX=\([0-9][0-9]*\).*/\1/p' | tail -n 1)"
if ! [[ "$touch_slop_px" =~ ^[0-9]+$ ]] || [ "$touch_slop_px" -le 0 ]; then
  echo "Could not read the emulator's actual Android touch-slop value."
  collect_capture_diagnostics timeline-touch-slop-missing
  exit 29
fi
echo "Using Android ViewConfiguration touch slop: ${touch_slop_px}px"
thumbnail_aligned=false
for attempt in 1 2 3 4 5; do
  hierarchy="emulator-screenshots/editor-frame-align-${attempt}.xml"
  dump_hierarchy "editor-frame-align-${attempt}.xml" "$hierarchy" || true
  alignment="$(python3 .github/scripts/assert-thumbnail-alignment.py prepare "$hierarchy")"
  read -r thumb_center_x thumb_center_y play_center_x thumb_sample_ms <<< "$alignment"
  delta_x="$((play_center_x - thumb_center_x))"
  absolute_delta="$((delta_x < 0 ? -delta_x : delta_x))"
  echo "Alignment attempt $attempt: sample=${thumb_sample_ms}ms center=${thumb_center_x}px playhead=${play_center_x}px delta=${delta_x}px"
  if [ "$absolute_delta" -le 5 ]; then
    thumbnail_aligned=true
    break
  fi
  gesture_delta="$((delta_x + (delta_x < 0 ? -touch_slop_px : touch_slop_px)))"
  gesture_end_x="$((thumb_center_x + gesture_delta))"
  timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$thumb_center_x" "$thumb_center_y" "$gesture_end_x" "$thumb_center_y" 400
  sleep 1
done
if [ "$thumbnail_aligned" != true ]; then
  echo "Could not center any filmstrip thumbnail under the fixed playhead after 5 measured drags."
  collect_capture_diagnostics thumbnail-alignment-not-centered
  exit 24
fi
sleep 1
if ! take_screen emulator-screenshots/editor-frame-aligned.png; then
  collect_capture_diagnostics thumbnail-alignment-capture-failure
  exit 6
fi
dump_hierarchy editor-frame-aligned.xml emulator-screenshots/editor-frame-aligned.xml || true
timeout -k 2s 10s adb -s emulator-5554 logcat -d -v threadtime > emulator-screenshots/thumbnail-alignment-logcat.txt 2>&1
python3 .github/scripts/assert-thumbnail-alignment.py verify \
  emulator-screenshots/editor-frame-aligned.xml \
  emulator-screenshots/editor-frame-aligned.png \
  emulator-screenshots/thumbnail-alignment-logcat.txt

echo "Step 10/12: exercise filmstrip scrubbing in both directions"
clip_bounds="$(python3 - emulator-screenshots/editor-window.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(0)
for node in root.iter("node"):
    desc = node.attrib.get("content-desc", "")
    bounds = node.attrib.get("bounds", "")
    if desc.startswith("Klip 1:"):
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if match:
            print(*map(int, match.groups()))
            break
PY
)"
if [ -z "$clip_bounds" ]; then
  echo "Could not locate the first video filmstrip for the scrub test."
  collect_capture_diagnostics filmstrip-scrub-target-missing
  exit 17
fi
read -r clip_x1 clip_y1 clip_x2 clip_y2 <<< "$clip_bounds"
scrub_y="$(((clip_y1 + clip_y2) / 2))"
clip_width="$((clip_x2 - clip_x1))"
# UIAutomator reports the visible intersection of a long clip with the lane
# viewport, not the clip's full off-screen width. Scale the regular inset to
# that visible width, but keep the initial touch safely inside Android's
# gesture-navigation exclusion zone. Starting near the right screen edge can
# invoke system Back and close the editor instead of scrolling the filmstrip.
swipe_inset="$((clip_width / 10))"
if [ "$swipe_inset" -lt 10 ]; then swipe_inset=10; fi
if [ "$swipe_inset" -gt 24 ]; then swipe_inset=24; fi
screen_width="$(timeout -k 2s 5s adb -s emulator-5554 shell wm size | sed -n 's/.*: \([0-9]*\)x[0-9]*.*/\1/p' | tail -n 1 || true)"
density_dpi="$(timeout -k 2s 5s adb -s emulator-5554 shell wm density | sed -n 's/.*Physical density: \([0-9]*\).*/\1/p' | tail -n 1 || true)"
if ! [[ "$screen_width" =~ ^[0-9]+$ ]] || [ "$screen_width" -le 0 ]; then
  echo "Could not read emulator screen width for safe filmstrip gesture positioning."
  collect_capture_diagnostics filmstrip-screen-width-missing
  exit 30
fi
if ! [[ "$density_dpi" =~ ^[0-9]+$ ]] || [ "$density_dpi" -le 0 ]; then
  echo "Could not read emulator density for safe filmstrip gesture positioning."
  collect_capture_diagnostics filmstrip-screen-density-missing
  exit 31
fi
# Start at least 48dp from the right edge, safely outside the system Back
# gesture area on density-scaled emulator configurations.
right_edge_safe_margin_px="$(((48 * density_dpi + 159) / 160))"
right_edge_safe_x="$((screen_width - right_edge_safe_margin_px))"
forward_start="$((clip_x2 - swipe_inset))"
if [ "$forward_start" -gt "$right_edge_safe_x" ]; then
  forward_start="$right_edge_safe_x"
fi
forward_end="$((clip_x1 + swipe_inset))"
forward_distance="$((forward_start - forward_end))"
if [ "$forward_distance" -lt 80 ]; then
  echo "The visible filmstrip range is too small for a meaningful scrub (${clip_width}px wide; ${forward_distance}px usable after ${swipe_inset}px insets)."
  collect_capture_diagnostics filmstrip-scrub-distance-too-small
  exit 18
fi
timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$forward_start" "$scrub_y" "$forward_end" "$scrub_y" 700
sleep 1
timeout -k 2s 10s adb -s emulator-5554 logcat -d -v threadtime > emulator-screenshots/filmstrip-scrub-forward-logcat.txt 2>&1
forward_values="$(python3 - emulator-screenshots/filmstrip-scrub-forward-logcat.txt <<'PY'
import re
import sys
latest = None
for line in open(sys.argv[1], errors="replace"):
    if "TIMELINE_SYNC_SAMPLE" not in line:
        continue
    p = re.search(r"playheadMs=(\d+)", line)
    s = re.search(r"scrollXs=([0-9,]+)", line)
    c = re.search(r"playheadCenterPx=(\d+)", line)
    if p and s and c:
        latest = (int(p.group(1)), int(s.group(1).split(",")[0]), int(c.group(1)))
if latest:
    print(*latest)
PY
)"
if [ -z "$forward_values" ]; then
  echo "No timeline synchronization sample was recorded after dragging the filmstrip left."
  collect_capture_diagnostics filmstrip-scrub-forward-no-sample
  exit 19
fi
read -r forward_ms forward_scroll playhead_center <<< "$forward_values"
if [ "$forward_ms" -le 0 ] || [ "$forward_scroll" -le 0 ]; then
  echo "Dragging the filmstrip left did not advance the shared playhead."
  echo "Observed playheadMs=$forward_ms scrollX=$forward_scroll"
  collect_capture_diagnostics filmstrip-scrub-forward-not-advanced
  exit 20
fi
dump_hierarchy editor-after-forward-scrub.xml emulator-screenshots/editor-after-forward-scrub.xml || true
reverse_bounds="$(python3 - emulator-screenshots/editor-after-forward-scrub.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(0)
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").startswith("Klip 1:"):
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            print(*map(int, match.groups()))
            break
PY
)"
if [ -z "$reverse_bounds" ]; then
  echo "Could not relocate the filmstrip after forward scrubbing."
  collect_capture_diagnostics filmstrip-scrub-reverse-target-missing
  exit 21
fi
read -r reverse_x1 reverse_y1 reverse_x2 reverse_y2 <<< "$reverse_bounds"
reverse_y="$(((reverse_y1 + reverse_y2) / 2))"
reverse_width="$((reverse_x2 - reverse_x1))"
reverse_inset="$((reverse_width / 10))"
if [ "$reverse_inset" -lt 10 ]; then reverse_inset=10; fi
if [ "$reverse_inset" -gt 24 ]; then reverse_inset=24; fi
# Keep the reverse gesture entirely to the right of the fixed playhead's
# 28dp touch target. Crossing that overlay hands the gesture to playhead seeking
# instead of the HorizontalScrollView and can falsely look like reverse scrub.
reverse_start="$((reverse_x1 + reverse_inset))"
playhead_safe_start="$((playhead_center + 20))"
if [ "$reverse_start" -lt "$playhead_safe_start" ]; then
  reverse_start="$playhead_safe_start"
fi
reverse_end="$((reverse_x2 - reverse_inset))"
reverse_distance="$((reverse_end - reverse_start))"
if [ "$reverse_distance" -lt 80 ]; then
  echo "The visible filmstrip range became too small for the reverse scrub (${reverse_width}px wide; ${reverse_distance}px usable after ${reverse_inset}px insets)."
  collect_capture_diagnostics filmstrip-scrub-reverse-distance-too-small
  exit 23
fi
timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$reverse_start" "$reverse_y" "$reverse_end" "$reverse_y" 700
sleep 1
timeout -k 2s 10s adb -s emulator-5554 logcat -d -v threadtime > emulator-screenshots/filmstrip-scrub-reverse-logcat.txt 2>&1
reverse_ms="$(python3 - emulator-screenshots/filmstrip-scrub-reverse-logcat.txt <<'PY'
import re
import sys
latest = None
for line in open(sys.argv[1], errors="replace"):
    if "TIMELINE_SYNC_SAMPLE" not in line:
        continue
    p = re.search(r"playheadMs=(\d+)", line)
    if p:
        latest = int(p.group(1))
if latest is not None:
    print(latest)
PY
)"
if [ -z "$reverse_ms" ] || [ "$reverse_ms" -ge "$forward_ms" ]; then
  echo "Dragging the filmstrip right did not move the playhead backward."
  echo "Forward=$forward_ms ms reverse=$reverse_ms ms"
  collect_capture_diagnostics filmstrip-scrub-reverse-not-reversed
  exit 22
fi
echo "PASS: filmstrip left advanced to \${forward_ms}ms; right returned to \${reverse_ms}ms"

echo "Step 11/12: exercise live playback and validate ruler/filmstrip synchronization"
dump_hierarchy timeline-play-button.xml emulator-screenshots/timeline-play-button.xml || true
play_coords="$(python3 - emulator-screenshots/timeline-play-button.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(0)

for node in root.iter("node"):
    description = node.attrib.get("content-desc", "")
    bounds = node.attrib.get("bounds", "")
    if "Önizlemeyi oynat" in description:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)"
if [ -z "$play_coords" ]; then
  echo "Could not locate the editor's accessible Play button; refusing to infer a tap coordinate."
  collect_capture_diagnostics timeline-play-button-missing
  exit 14
fi
echo "Tapping the real editor Play button at $play_coords"
# Keep the test-enabled marker emitted at editor startup; logcat was already
# cleared immediately before the editor launch above.
if ! timeout -k 2s 8s adb -s emulator-5554 shell input tap $play_coords; then
  echo "Could not tap the editor Play button."
  collect_capture_diagnostics timeline-play-tap-failed
  exit 15
fi
sleep 4
if ! take_screen emulator-screenshots/editor-playing.png; then
  collect_capture_diagnostics timeline-playing-capture-failure
  exit 6
fi
# The full Android log buffer can exceed the 10-second timeout and leave this
# file truncated before the editor's playback samples. Keep only the dedicated
# debug sync tag so the validator receives the startup marker and all samples.
timeout -k 2s 10s adb -s emulator-5554 logcat -d -v threadtime -s TimelineSyncTest:I > emulator-screenshots/timeline-sync-logcat.txt 2>&1 || true
if ! python3 .github/scripts/assert-timeline-sync.py emulator-screenshots/timeline-sync-logcat.txt 2>&1 | tee emulator-screenshots/timeline-sync-validation.txt; then
  echo "Live playback/timeline synchronization assertions failed."
  collect_capture_diagnostics timeline-sync-assertion-failure
  exit 16
fi

echo "Step 12/12: validate automatic draft save and resume list"
timeout -k 2s 8s adb -s emulator-5554 shell input keyevent 4 || true
sleep 3
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime > emulator-screenshots/draft-autosave-logcat.txt 2>&1 || true
if ! grep -q "LOCAL_DRAFT_SAVED id=11111111-1111-4111-8111-111111111111 clips=1" emulator-screenshots/draft-autosave-logcat.txt; then
  echo "Editor did not confirm automatic draft save after leaving the editor."
  collect_capture_diagnostics draft-autosave-failure
  exit 11
fi

# The editor's MP4 save picker can remain the foreground system activity after
# returning from export. Restart the app's home activity explicitly so the
# draft assertion inspects MathMotor rather than DocumentsUI.
if ! timeout -k 2s 15s adb -s emulator-5554 shell am start -S \
  -n com.mathmotor.video.next/com.mathmotor.video.MainActivity; then
  echo "Could not return to the home screen to verify the saved draft."
  collect_capture_diagnostics draft-home-launch-failure
  exit 12
fi
sleep 3
# Confirm MathMotor is still foregrounded. A swipe beginning at y=1850 lands
# in Android's bottom gesture-navigation area on this 1920px emulator and
# launches the system home screen instead of scrolling the app.
focused_home="$(timeout -k 2s 5s adb -s emulator-5554 shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | tail -n 2 || true)"
if ! echo "$focused_home" | grep -q "com.mathmotor.video.next"; then
  echo "MathMotor home activity is not foregrounded before draft-list inspection: $focused_home"
  collect_capture_diagnostics draft-home-not-focused
  exit 12
fi
# Keep the gesture inside the app content, above its fixed bottom navigation
# and safely away from Android's system navigation gesture region.
for _ in 1 2 3; do
  timeout -k 2s 8s adb -s emulator-5554 shell input swipe 540 1650 540 700 450 || true
  sleep 1
done
focused_home="$(timeout -k 2s 5s adb -s emulator-5554 shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | tail -n 2 || true)"
if ! echo "$focused_home" | grep -q "com.mathmotor.video.next"; then
  echo "Draft-list scroll unexpectedly left MathMotor: $focused_home"
  collect_capture_diagnostics draft-home-scroll-left-app
  exit 12
fi
dump_hierarchy drafts-home.xml emulator-screenshots/drafts-home-window.xml || true
if [ -s emulator-screenshots/drafts-home-window.xml ] && ! grep -Eqi "TASLAKLAR|Taslaklar" emulator-screenshots/drafts-home-window.xml; then
  echo "Local draft was saved, but the home screen did not expose the draft section."
  collect_capture_diagnostics draft-home-section-missing
  exit 13
fi
if [ -s emulator-screenshots/drafts-home-window.xml ]; then
  for backup_action in "Taslakları yedekle" "Yedekten geri yükle"; do
    if ! grep -q "$backup_action" emulator-screenshots/drafts-home-window.xml; then
      echo "The home screen did not expose the '$backup_action' action."
      collect_capture_diagnostics "draft-backup-action-missing"
      exit 29
    fi
  done
fi
if ! take_screen emulator-screenshots/home-with-drafts.png; then
  collect_capture_diagnostics draft-home-capture-failure
  exit 6
fi

echo "Step 12/12: save diagnostics"
timeout -k 2s 8s adb -s emulator-5554 logcat -d > emulator-screenshots/logcat.txt || true
test -s emulator-screenshots/home.png
test -s emulator-screenshots/editor-empty.png
test -s emulator-screenshots/editor.png
test -s emulator-screenshots/editor-playing.png
test -s emulator-screenshots/editor-panel-clip-open.png
test -s emulator-screenshots/editor-panel-trim.png
test -s emulator-screenshots/editor-panel-audio.png
test -s emulator-screenshots/editor-panel-tool-strip-after-trim.xml
test -s emulator-screenshots/editor-panel-tool-strip.png
test -s emulator-screenshots/editor-panel-closed.png
test -s emulator-screenshots/editor-panel-layout-logcat.txt
test -s emulator-screenshots/timeline-sync-validation.txt
test -s emulator-screenshots/home-with-drafts.png
echo "Home, empty editor, initial/playing editor, two-stage clip tool strip/detail/return geometry, live timeline sync, and autosaved-draft screenshots captured."
