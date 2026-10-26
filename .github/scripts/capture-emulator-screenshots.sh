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
    # Recover Android-owned system, System UI, or Pixel/Nexus Launcher ANR dialogs.
    # Do not dismiss a dialog raised by MathMotor itself; application ANRs must fail the smoke test.
    if ! grep -Eiq "(Process system|System UI|Pixel Launcher|Nexus Launcher|com\\.google\\.android\\.apps\\.nexuslauncher).*isn.t responding" "$xml"; then
      echo "Application-level ANR detected in $xml; refusing to dismiss it as emulator noise"
      return 1
    fi

    local wait_coords
    wait_coords="$(python3 - "$xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(0)

for node in root.iter("node"):
    if node.attrib.get("text", "").strip().lower() == "wait":
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)"
    if [ -z "$wait_coords" ]; then
      echo "System ANR dialog detected but its Wait button could not be located in $xml"
      return 1
    fi

    echo "System ANR dialog detected (attempt $attempt/3); tapping Wait at $wait_coords"
    timeout -k 2s 8s adb -s emulator-5554 shell input tap $wait_coords || true
    SYSTEM_ANR_RECOVERED=true
    sleep 5
    dump_hierarchy "$remote_name" "$xml" || true
  done

  if [ -s "$xml" ] && grep -Eiq "(Process system|System UI|Pixel Launcher|Nexus Launcher|com\\.google\\.android\\.apps\\.nexuslauncher).*isn.t responding" "$xml"; then
    # If a system launcher remains ANR after repeated Wait taps, close that
    # Android-owned launcher process to clear its modal dialog. The MathMotor
    # activity has already been launched and is the intended foreground screen.
    # Never use this fallback for an ANR whose title belongs to MathMotor.
    local close_coords
    close_coords="$(python3 - "$xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(0)

for node in root.iter("node"):
    if node.attrib.get("text", "").strip().lower() in {"close app", "force close"}:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)"
    if [ -n "$close_coords" ]; then
      echo "System launcher ANR persisted after Wait; dismissing only the Android-owned launcher at $close_coords"
      timeout -k 2s 8s adb -s emulator-5554 shell input tap $close_coords || true
      sleep 3
      dump_hierarchy "$remote_name" "$xml" || true
      if [ ! -s "$xml" ] || ! grep -Eiq "isn.t responding|not responding" "$xml"; then
        SYSTEM_ANR_RECOVERED=true
        echo "Android-owned launcher ANR dialog cleared; continuing with MathMotor UI checks"
        return 0
      fi
    fi
    echo "System ANR dialog is still present after Wait and Close-app recovery attempts"
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

second_fixture="app/build/test-fixtures/editor-test-video-second.mp4"
if [ ! -s "$second_fixture" ]; then
  echo "Missing generated second test fixture: $second_fixture"
  exit 3
fi
second_app_internal_video="/data/user/0/com.mathmotor.video.next/files/editor-test-video-second.mp4"
if ! timeout -k 2s 30s adb -s emulator-5554 shell -T \
  "run-as com.mathmotor.video.next sh -c 'mkdir -p files && cat > files/editor-test-video-second.mp4'" \
  < "$second_fixture" 2>&1 | tee emulator-screenshots/video-fixture-second-private-copy.txt; then
  echo "Streaming the second test fixture into the app sandbox failed; preserving diagnostics"
  timeout -k 2s 8s adb -s emulator-5554 shell run-as com.mathmotor.video.next id > emulator-screenshots/run-as-second-preflight.txt 2>&1 || true
  exit 10
fi
timeout -k 2s 8s adb -s emulator-5554 shell run-as com.mathmotor.video.next ls -l files/editor-test-video-second.mp4 2>&1 | tee -a emulator-screenshots/video-fixture-second-private-copy.txt


timeout -k 2s 8s adb -s emulator-5554 shell am force-stop com.mathmotor.video.next || true
# Avoid -W for the same slow-emulator first-frame wait that affected the home launch.
# Clear stale markers so only this launch can satisfy the smoke assertion.
timeout -k 2s 5s adb -s emulator-5554 logcat -c || true
timeout -k 2s 15s adb -s emulator-5554 shell am start \
  -n com.mathmotor.video.next/com.mathmotor.video.EditorTestEntryActivity \
  --es video_uri "file://$app_internal_video" \
  --es draft_id "11111111-1111-4111-8111-111111111111" | tee emulator-screenshots/editor-video-launch.txt

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
sleep 2
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

echo "Step 8/9: validate automatic draft save and resume list"
timeout -k 2s 8s adb -s emulator-5554 shell input keyevent 4 || true
sleep 3
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime > emulator-screenshots/draft-autosave-logcat.txt 2>&1 || true
if ! grep -q "LOCAL_DRAFT_SAVED id=11111111-1111-4111-8111-111111111111 clips=1" emulator-screenshots/draft-autosave-logcat.txt; then
  echo "Editor did not confirm automatic draft save after leaving the editor."
  collect_capture_diagnostics draft-autosave-failure
  exit 11
fi

if ! timeout -k 2s 15s adb -s emulator-5554 shell am start \
  -n com.mathmotor.video.next/com.mathmotor.video.MainActivity; then
  echo "Could not return to the home screen to verify the saved draft."
  collect_capture_diagnostics draft-home-launch-failure
  exit 12
fi
sleep 3
dump_hierarchy drafts-home.xml emulator-screenshots/drafts-home-window.xml || true
if [ -s emulator-screenshots/drafts-home-window.xml ] && ! grep -q "TASLAKLAR" emulator-screenshots/drafts-home-window.xml; then
  echo "Local draft was saved, but the home screen did not expose the draft section."
  collect_capture_diagnostics draft-home-section-missing
  exit 13
fi
if ! take_screen emulator-screenshots/home-with-drafts.png; then
  collect_capture_diagnostics draft-home-capture-failure
  exit 6
fi

echo "Step 9/9: save diagnostics"
timeout -k 2s 8s adb -s emulator-5554 logcat -d > emulator-screenshots/logcat.txt || true
test -s emulator-screenshots/home.png
test -s emulator-screenshots/editor-empty.png
test -s emulator-screenshots/editor.png
test -s emulator-screenshots/home-with-drafts.png
echo "Home, empty editor, sample-video editor, and autosaved-draft home screenshots captured. Editor auto-save verified."
