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


timeout -k 2s 8s adb -s emulator-5554 shell am force-stop com.mathmotor.video.next || true
# Avoid -W for the same slow-emulator first-frame wait that affected the home launch.
# Clear stale markers so only this launch can satisfy the smoke assertion.
timeout -k 2s 5s adb -s emulator-5554 logcat -c || true
timeout -k 2s 15s adb -s emulator-5554 shell am start \
  -n com.mathmotor.video.next/com.mathmotor.video.EditorTestEntryActivity \
  --es video_uri "file://$app_internal_video" \
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

clip_tool_coords="$(find_bottom_tool emulator-screenshots/editor-window.xml "Düzenle" || true)"
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

echo "Step 9/12: align a thumbnail cell with the playhead and compare the rendered pixels"
# HorizontalScrollView consumes ViewConfiguration.scaledTouchSlop before it
# scrolls. That value is density-scaled (pixels), so a fixed 12px allowance
# undershoots on the Pixel 2 emulator and creates a false alignment failure.
touch_slop_px="$(timeout -k 2s 8s adb -s emulator-5554 logcat -d -v brief -s TimelineSyncTest:I \
  | sed -n 's/.*TIMELINE_TOUCH_SLOP_PX=\\([0-9][0-9]*\\).*/\\1/p' | tail -n 1)"
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
  if [ "$absolute_delta" -le 3 ]; then
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
# viewport, not the clip's full off-screen width. Scale the inset to that
# visible width instead of discarding a fixed 35px from both sides.
swipe_inset="$((clip_width / 10))"
if [ "$swipe_inset" -lt 10 ]; then swipe_inset=10; fi
if [ "$swipe_inset" -gt 24 ]; then swipe_inset=24; fi
forward_start="$((clip_x2 - swipe_inset))"
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
