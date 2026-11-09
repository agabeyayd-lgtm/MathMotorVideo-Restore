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
  local attempt dump_status remote_state
  for attempt in 1 2 3; do
    rm -f "$local_file"
    timeout -k 2s 5s adb -s emulator-5554 shell rm -f "/sdcard/$remote" >/dev/null 2>&1 || true

    # API 35 occasionally reports a successful uiautomator process before its
    # hierarchy file is visible to the next ADB shell. Preserve its output and
    # poll the device filesystem instead of assuming process exit == file ready.
    dump_status=0
    timeout -k 3s 25s adb -s emulator-5554 shell uiautomator dump --compressed "/sdcard/$remote" \
      > "emulator-screenshots/$remote.dump-output.txt" 2>&1 || dump_status=$?

    remote_state=""
    for ready_attempt in $(seq 1 8); do
      remote_state="$(timeout -k 2s 5s adb -s emulator-5554 shell \
        'if [ -s "/sdcard/'"$remote"'" ]; then echo READY; else echo MISSING; fi' 2>/dev/null | tr -d "\r" || true)"
      if [ "$remote_state" = "READY" ]; then break; fi
      sleep 1
    done

    if [ "$remote_state" = "READY" ] && \
       timeout -k 3s 30s adb -s emulator-5554 pull "/sdcard/$remote" "$local_file" \
         >/dev/null 2>&1 && [ -s "$local_file" ]; then
      echo "UI hierarchy captured on attempt $attempt (uiautomator exit=$dump_status)."
      return 0
    fi

    echo "UI hierarchy attempt $attempt/3 failed (uiautomator exit=$dump_status, remote=$remote_state)."
    cat "emulator-screenshots/$remote.dump-output.txt" || true
    sleep 1
  done
  return 1
}

find_content_description_bounds() {
  local hierarchy="$1" description="$2"
  python3 - "$hierarchy" "$description" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError) as error:
    raise SystemExit(f"Cannot parse accessibility hierarchy: {error}")
for node in root.iter("node"):
    if node.attrib.get("content-desc") != sys.argv[2]:
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        continue
    x1, y1, x2, y2 = map(int, match.groups())
    if x2 > x1 and y2 > y1:
        print((x1 + x2) // 2, (y1 + y2) // 2, x2 - x1, y2 - y1, x1, y1, x2, y2)
        break
PY
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

# The draft controls are intentionally below the tools grid. The first home
# hierarchy is at scroll position zero, so it cannot prove these lower controls
# exist. Scroll the content to the bottom before checking the empty-draft state.
for _ in 1 2 3; do
  timeout -k 2s 8s adb -s emulator-5554 shell input swipe 540 1650 540 700 450
  sleep 1
done
if ! dump_hierarchy empty-drafts-home.xml emulator-screenshots/empty-drafts-home.xml; then
  echo "Could not inspect the lower home section after scrolling to the draft area."
  collect_capture_diagnostics empty-draft-home-hierarchy-missing
  exit 31
fi
for backup_action in "Taslakları yedekle" "Yedekten geri yükle"; do
  if ! grep -q "$backup_action" emulator-screenshots/empty-drafts-home.xml; then
    echo "The scrolled home screen did not expose '$backup_action' when no drafts exist."
    collect_capture_diagnostics "empty-draft-$backup_action-missing"
    exit 31
  fi
done
if ! python3 - emulator-screenshots/empty-drafts-home.xml <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
forbidden = {
    "taslaklar",
    "kaldığın yerden devam et",
    "henüz kaydedilmiş taslak yok",
}
for node in root.iter("node"):
    for attribute in ("text", "content-desc"):
        value = " ".join(node.attrib.get(attribute, "").casefold().split())
        if value in forbidden:
            print(f"Unexpected empty-draft label is visible: {attribute}={value!r}")
            raise SystemExit(1)
PY
then
  echo "The empty draft heading or placeholder remained visible even though there are no saved drafts."
  collect_capture_diagnostics empty-draft-area-visible
  exit 32
fi
echo "PASS: the scrolled empty-draft area hides its heading/placeholder and keeps backup/restore reachable."

# Restore the home scroll position before locating and tapping the empty-project
# card below. The fixed footer remains outside the ScrollView.
for _ in 1 2 3; do
  timeout -k 2s 8s adb -s emulator-5554 shell input swipe 540 700 540 1650 450
  sleep 1
done
if ! dump_hierarchy window.xml emulator-screenshots/window.xml; then
  echo "Could not restore the home hierarchy after the empty-draft check."
  collect_capture_diagnostics home-hierarchy-after-draft-check-missing
  exit 33
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

photo_fixture="app/build/test-fixtures/editor-test-photo.png"
app_internal_photo="/data/user/0/com.mathmotor.video.next/files/editor-test-photo.png"
if [ ! -s "$photo_fixture" ]; then
  echo "Missing full-frame home-photo fixture: $photo_fixture"
  exit 3
fi
if ! timeout -k 2s 30s adb -s emulator-5554 shell -T \
  "run-as com.mathmotor.video.next sh -c 'cat > files/editor-test-photo.png'" \
  < "$photo_fixture" 2>&1 | tee -a emulator-screenshots/video-fixture-private-copy.txt; then
  echo "Streaming the full-frame photo fixture into the app sandbox failed."
  exit 10
fi
timeout -k 2s 8s adb -s emulator-5554 shell run-as com.mathmotor.video.next ls -l files/editor-test-photo.png \
  2>&1 | tee -a emulator-screenshots/video-fixture-private-copy.txt

music_fixture="app/build/test-fixtures/editor-test-music.wav"
sound_fixture="app/build/test-fixtures/editor-test-sound.wav"
app_internal_music="/data/user/0/com.mathmotor.video.next/files/editor-test-music.wav"
app_internal_sound="/data/user/0/com.mathmotor.video.next/files/editor-test-sound.wav"
for audio_fixture in "$music_fixture" "$sound_fixture"; do
  if [ ! -s "$audio_fixture" ]; then
    echo "Missing generated audio fixture: $audio_fixture"
    exit 3
  fi
done
if ! timeout -k 2s 30s adb -s emulator-5554 shell -T \
  "run-as com.mathmotor.video.next sh -c 'cat > files/editor-test-music.wav'" \
  < "$music_fixture" 2>&1 | tee -a emulator-screenshots/video-fixture-private-copy.txt; then
  echo "Streaming the music test fixture into the app sandbox failed."
  exit 10
fi
if ! timeout -k 2s 30s adb -s emulator-5554 shell -T \
  "run-as com.mathmotor.video.next sh -c 'cat > files/editor-test-sound.wav'" \
  < "$sound_fixture" 2>&1 | tee -a emulator-screenshots/video-fixture-private-copy.txt; then
  echo "Streaming the sound-effect test fixture into the app sandbox failed."
  exit 10
fi
timeout -k 2s 8s adb -s emulator-5554 shell run-as com.mathmotor.video.next ls -l files/editor-test-music.wav files/editor-test-sound.wav \
  2>&1 | tee -a emulator-screenshots/video-fixture-private-copy.txt

timeout -k 2s 8s adb -s emulator-5554 shell am force-stop com.mathmotor.video.next || true
# Avoid -W for the same slow-emulator first-frame wait that affected the home launch.
# Clear stale markers so only this launch can satisfy the smoke assertion.
# Test-only zoom keeps both four-second halves visible for the direct drag check.
timeout -k 2s 5s adb -s emulator-5554 logcat -c || true
timeout -k 2s 15s adb -s emulator-5554 shell am start \
  -n com.mathmotor.video.next/com.mathmotor.video.EditorTestEntryActivity \
  --es video_uri "file://$app_internal_video" \
  --es overlay_uri "file://$app_internal_overlay" \
  --es replacement_video_uri "file://$app_internal_replacement_video" \
  --es test_music_uri "file://$app_internal_music" \
  --es test_sound_uri "file://$app_internal_sound" \
  --es draft_id "11111111-1111-4111-8111-111111111111" \
  --ez timeline_sync_test true \
  --ef timeline_test_px_per_sec_dp 30 | tee emulator-screenshots/editor-video-launch.txt

# Capture the emulator touch-slop immediately after the test activity starts.
# Later interaction checks clear logcat to isolate their own events, so reading
# this startup marker at the end loses it from the bounded ring buffer.
touch_slop_px=""
for attempt in $(seq 1 8); do
  touch_slop_px="$(timeout -k 2s 8s adb -s emulator-5554 logcat -d -v brief -s TimelineSyncTest:I \
    | sed -n 's/.*TIMELINE_TOUCH_SLOP_PX=\([0-9][0-9]*\).*/\1/p' | tail -n 1)"
  if [[ "$touch_slop_px" =~ ^[0-9]+$ ]] && [ "$touch_slop_px" -gt 0 ]; then break; fi
  sleep 1
done
if ! [[ "$touch_slop_px" =~ ^[0-9]+$ ]] || [ "$touch_slop_px" -le 0 ]; then
  echo "Could not capture Android touch-slop at editor startup."
  collect_capture_diagnostics timeline-touch-slop-startup-missing
  exit 29
fi
echo "Captured Android ViewConfiguration touch slop at startup: ${touch_slop_px}px"
# Preserve the startup enable marker before later UI interaction checks clear logcat.
# The editor remains test-enabled, but the live-playback validator needs proof
# that this exact editor instance received the requested launch extra.
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s TimelineSyncTest:I \
  > emulator-screenshots/timeline-sync-startup-logcat.txt 2>&1 || true
if ! grep -q "TIMELINE_SYNC_TEST_ENABLED" emulator-screenshots/timeline-sync-startup-logcat.txt; then
  echo "Editor launch did not confirm timeline sync test mode."
  collect_capture_diagnostics timeline-sync-test-mode-not-enabled
  exit 30
fi

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

# Preserve both audio-import completion markers before later interaction tests
# intentionally clear logcat to isolate Undo/Redo and gesture diagnostics.
audio_fixtures_ready=false
for attempt in $(seq 1 25); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorTouchTest:I \
    > emulator-screenshots/audio-fixture-import-logcat.txt 2>&1 || true
  if grep -Fq "EDITOR_SMOKE_AUDIO_REJECTED" emulator-screenshots/audio-fixture-import-logcat.txt; then
    echo "At least one deterministic audio fixture was rejected by the production importer."
    collect_capture_diagnostics audio-fixture-import-rejected
    exit 75
  fi
  if grep -Fq "EDITOR_SMOKE_AUDIO_ADDED kind=music" emulator-screenshots/audio-fixture-import-logcat.txt \
      && grep -Fq "EDITOR_SMOKE_AUDIO_ADDED kind=sound" emulator-screenshots/audio-fixture-import-logcat.txt; then
    audio_fixtures_ready=true
    break
  fi
  echo "Waiting for startup audio fixtures (attempt $attempt/25)"
  sleep 1
done
if [ "$audio_fixtures_ready" != true ]; then
  echo "Music and sound-effect fixtures were not both confirmed during editor startup."
  collect_capture_diagnostics audio-fixture-startup-timeout
  exit 76
fi
echo "PASS: both music and sound-effect fixtures were imported and their startup markers preserved."
echo "Step 6a-Waveform: verify music and sound effects render real PCM waveforms off the UI thread"
waveform_music_ready=false
waveform_sound_ready=false
for waveform_attempt in $(seq 1 25); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s AudioWaveform:I > emulator-screenshots/audio-waveform-logcat.txt 2>&1 || true
  if grep -Fq "AUDIO_WAVEFORM_READY kind=music" emulator-screenshots/audio-waveform-logcat.txt; then waveform_music_ready=true; fi
  if grep -Fq "AUDIO_WAVEFORM_READY kind=sound" emulator-screenshots/audio-waveform-logcat.txt; then waveform_sound_ready=true; fi
  if [ "$waveform_music_ready" = true ] && [ "$waveform_sound_ready" = true ]; then break; fi
  sleep 1
done
if [ "$waveform_music_ready" != true ] || [ "$waveform_sound_ready" != true ]; then
  echo "Music or sound-effect waveform was not decoded into visible timeline buckets."
  cat emulator-screenshots/audio-waveform-logcat.txt || true
  collect_capture_diagnostics audio-waveform-generation-failed
  exit 77
fi
if ! take_screen emulator-screenshots/editor-audio-waveforms.png; then
  collect_capture_diagnostics audio-waveform-screenshot-failed
  exit 77
fi
python3 - emulator-screenshots/editor-audio-waveforms.png <<'PY'
from PIL import Image
import sys

image = Image.open(sys.argv[1]).convert("RGB")
green_bars = 0
orange_bars = 0
for red, green, blue in image.getdata():
    if abs(red - 151) <= 34 and abs(green - 226) <= 34 and abs(blue - 181) <= 34:
        green_bars += 1
    if red >= 220 and 165 <= green <= 235 and 95 <= blue <= 180 and red - green >= 20:
        orange_bars += 1
if green_bars < 24:
    raise SystemExit(f"Music waveform color was not visible in the rendered timeline: {green_bars} pixels.")
if orange_bars < 24:
    raise SystemExit(f"Sound-effect waveform color was not visible in the rendered timeline: {orange_bars} pixels.")
print(f"PASS: waveform bars rendered in the screenshot (music={green_bars}px, effect={orange_bars}px).")
PY
echo "PASS: real music and effect audio samples were decoded asynchronously into cached waveform bars."

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
# Measure only the editor preview region. The timeline below it contains
# thumbnails from the same fixture color; including those disconnected pixels
# in the bounding box falsely changes the measured aspect ratio and fill.
preview = image.crop((0, 0, image.width, int(image.height * 0.55)))
points = []
for y in range(preview.height):
    for x in range(preview.width):
        red, green, blue = preview.getpixel((x, y))
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

echo "Step 7a-Cover: verify independent cover tile opens, saves, and reloads its own still image"
# Re-select the main video clip: the preceding overlay-canvas test intentionally
# cleared video selection, and Kapak always edits the currently selected clip.
if ! dump_hierarchy editor-cover-select-video.xml emulator-screenshots/editor-cover-select-video.xml; then
  collect_capture_diagnostics cover-video-reselect-hierarchy-missing
  exit 86
fi
cover_video_coords="$(python3 - emulator-screenshots/editor-cover-select-video.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
matches = []
for node in root.iter("node"):
    desc = node.attrib.get("content-desc", "").strip()
    if not desc.startswith("Klip 1:") or node.attrib.get("enabled", "false").lower() != "true":
        continue
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        continue
    x1,y1,x2,y2 = map(int, m.groups())
    if x2 > x1 and y2 > y1:
        matches.append(((x2-x1)*(y2-y1),(x1+x2)//2,(y1+y2)//2))
if not matches:
    raise SystemExit("The first main-video clip is not exposed as an accessible selection target.")
_,x,y = max(matches)
print(x,y)
PY
)" || { collect_capture_diagnostics cover-video-reselect-target-missing; exit 86; }
timeout -k 2s 8s adb -s emulator-5554 shell input tap $cover_video_coords
sleep 1
if ! dump_hierarchy editor-cover-tile-before.xml emulator-screenshots/editor-cover-tile-before.xml; then
  collect_capture_diagnostics cover-tile-hierarchy-missing
  exit 86
fi
cover_tile_bounds="$(find_content_description_bounds emulator-screenshots/editor-cover-tile-before.xml "Kapak görselini düzenle" || true)"
if [ -z "$cover_tile_bounds" ]; then
  echo "The fixed left-gutter cover tile is missing or not accessible."
  collect_capture_diagnostics cover-tile-control-missing
  exit 86
fi
read -r cover_tile_x cover_tile_y cover_tile_w cover_tile_h cover_tile_x1 cover_tile_y1 cover_tile_x2 cover_tile_y2 <<< "$cover_tile_bounds"
if ! take_screen emulator-screenshots/editor-cover-tile-before.png; then
  collect_capture_diagnostics cover-tile-before-capture-failed
  exit 86
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap "$cover_tile_x" "$cover_tile_y"
sleep 1
cover_preview_ready=false
for preview_attempt in $(seq 1 15); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ClipCover:I > emulator-screenshots/cover-picker-preview-logcat.txt 2>&1 || true
  if grep -Fq "COVER_PICKER_FRAME_READY" emulator-screenshots/cover-picker-preview-logcat.txt; then
    cover_preview_ready=true
    break
  fi
  sleep 1
done
if [ "$cover_preview_ready" != true ]; then
  echo "The cover-frame preview did not finish decoding before the Save control became available."
  cat emulator-screenshots/cover-picker-preview-logcat.txt || true
  collect_capture_diagnostics cover-picker-preview-not-ready
  exit 86
fi
if ! dump_hierarchy editor-cover-picker.xml emulator-screenshots/editor-cover-picker.xml; then
  collect_capture_diagnostics cover-picker-did-not-open
  exit 86
fi
if ! grep -Fq "Kapak karesi zaman seçici" emulator-screenshots/editor-cover-picker.xml; then
  echo "Tapping the cover tile did not open the dedicated cover-frame picker."
  collect_capture_diagnostics cover-picker-control-missing
  exit 86
fi
cover_save_coords="$(python3 - emulator-screenshots/editor-cover-picker.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
matches = []
for node in root.iter("node"):
    if node.attrib.get("text", "").strip().casefold() != "kaydet":
        continue
    if node.attrib.get("enabled", "false").lower() != "true":
        continue
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        continue
    x1,y1,x2,y2 = map(int, m.groups())
    if x2 > x1 and y2 > y1:
        matches.append((x1,y1,x2,y2))
if not matches:
    raise SystemExit("The dedicated cover picker Save button is not enabled/visible.")
x1,y1,x2,y2 = matches[-1]
print((x1+x2)//2, (y1+y2)//2)
PY
)" || { collect_capture_diagnostics cover-picker-save-button-missing; exit 86; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $cover_save_coords
cover_saved=false
for attempt in $(seq 1 15); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ClipCover:I > emulator-screenshots/cover-save-logcat.txt 2>&1 || true
  if grep -Fq "COVER_STILL_SAVED" emulator-screenshots/cover-save-logcat.txt; then
    cover_saved=true
    break
  fi
  sleep 1
done
if [ "$cover_saved" != true ]; then
  echo "The cover picker Save action did not persist an independent project still image."
  cat emulator-screenshots/cover-save-logcat.txt || true
  collect_capture_diagnostics cover-still-save-failed
  exit 86
fi
cover_tile_reloaded=false
for attempt in $(seq 1 15); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ClipCover:I > emulator-screenshots/cover-tile-reload-logcat.txt 2>&1 || true
  if grep -Fq "COVER_TILE_READY source=saved-file" emulator-screenshots/cover-tile-reload-logcat.txt; then
    cover_tile_reloaded=true
    break
  fi
  sleep 1
done
if [ "$cover_tile_reloaded" != true ]; then
  echo "The cover file saved successfully but the independent tile did not reload from that saved file."
  cat emulator-screenshots/cover-tile-reload-logcat.txt || true
  collect_capture_diagnostics cover-still-display-failed
  exit 86
fi
if ! take_screen emulator-screenshots/editor-cover-tile-saved.png; then
  collect_capture_diagnostics cover-tile-after-save-capture-failed
  exit 86
fi
echo "Step 7b-CoverHistory: verify cover still image participates in project Undo/Redo"
dump_hierarchy editor-cover-undo-controls.xml emulator-screenshots/editor-cover-undo-controls.xml || {
  collect_capture_diagnostics cover-undo-controls-missing
  exit 86
}
cover_undo_coords="$(python3 - emulator-screenshots/editor-cover-undo-controls.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje işlemini geri al":
        continue
    if node.attrib.get("enabled", "false").lower() != "true":
        continue
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if m:
        x1,y1,x2,y2 = map(int, m.groups())
        print((x1+x2)//2, (y1+y2)//2)
        raise SystemExit(0)
raise SystemExit("Project Undo was not enabled after saving the cover still.")
PY
)" || { collect_capture_diagnostics cover-undo-disabled; exit 86; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $cover_undo_coords
cover_undo_restored=false
for attempt in $(seq 1 12); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ClipCover:I > emulator-screenshots/cover-undo-logcat.txt 2>&1 || true
  if grep -Fq "COVER_TILE_READY source=video" emulator-screenshots/cover-undo-logcat.txt; then
    cover_undo_restored=true
    break
  fi
  sleep 1
done
if [ "$cover_undo_restored" != true ]; then
  echo "Project Undo did not restore the clip's original video-frame cover image."
  cat emulator-screenshots/cover-undo-logcat.txt || true
  collect_capture_diagnostics cover-undo-still-restore-failed
  exit 86
fi
dump_hierarchy editor-cover-redo-controls.xml emulator-screenshots/editor-cover-redo-controls.xml || {
  collect_capture_diagnostics cover-redo-controls-missing
  exit 86
}
cover_redo_coords="$(python3 - emulator-screenshots/editor-cover-redo-controls.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje işlemini yinele":
        continue
    if node.attrib.get("enabled", "false").lower() != "true":
        continue
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if m:
        x1,y1,x2,y2 = map(int, m.groups())
        print((x1+x2)//2, (y1+y2)//2)
        raise SystemExit(0)
raise SystemExit("Project Redo was not enabled after cover Undo.")
PY
)" || { collect_capture_diagnostics cover-redo-disabled; exit 86; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $cover_redo_coords
cover_redo_restored=false
for attempt in $(seq 1 12); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ClipCover:I > emulator-screenshots/cover-redo-logcat.txt 2>&1 || true
  if grep -Eq "COVER_TILE_READY source=saved-(file|cache)" emulator-screenshots/cover-redo-logcat.txt; then
    cover_redo_restored=true
    break
  fi
  sleep 1
done
if [ "$cover_redo_restored" != true ]; then
  echo "Project Redo did not restore the separately saved cover still image."
  cat emulator-screenshots/cover-redo-logcat.txt || true
  collect_capture_diagnostics cover-redo-still-restore-failed
  exit 86
fi
echo "PASS: cover Save, Undo and Redo switch the independent tile between the source frame and the saved still image."
echo "PASS: fixed cover tile opened its picker, saved a project-local still image, and reloaded the tile from that saved file."

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

target = " ".join(sys.argv[2].casefold().split())
matches = []
for node in root.iter("node"):
    # Prefer exact visible labels and real dock buttons. A substring-only
    # search for "Metin" previously selected the timeline row "Metin ekle"
    # instead of the bottom-dock Metin button, producing a false emulator
    # failure after the export drawer closed.
    visible_text = " ".join(node.attrib.get("text", "").casefold().split())
    content_desc = " ".join(node.attrib.get("content-desc", "").casefold().split())
    exact_text = visible_text == target
    exact_desc = content_desc == target
    partial_text = bool(target) and target in visible_text
    if not (exact_text or exact_desc or partial_text):
        continue
    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not bounds:
        continue
    x1, y1, x2, y2 = map(int, bounds.groups())
    if x2 <= x1 or y2 <= y1:
        continue
    is_button = node.attrib.get("class") == "android.widget.Button"
    is_clickable = node.attrib.get("clickable") == "true" and node.attrib.get("enabled") == "true"
    score = (8 if exact_desc else 0) + (6 if exact_text else 0) + (3 if is_button else 0) + (1 if is_clickable else 0)
    matches.append((score, y1, (x1 + x2) // 2, (y1 + y2) // 2))
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
python3 - emulator-screenshots/preview-canvas-logcat-after-drag.txt emulator-screenshots/editor-overlay-after-drag.png emulator-screenshots/preview-canvas-logcat.txt <<'PY'
from PIL import Image
import re
import sys

log_path, screenshot_path, baseline_log_path = sys.argv[1:]
matches = [
    line for line in open(log_path, errors="replace")
    if "PREVIEW_CANVAS_BOUNDS" in line
]
if not matches:
    # The editor logs bounds only when geometry changes. A drag that leaves
    # the stage unchanged correctly emits no duplicate line, so reuse the
    # measured startup bounds from this same emulator/editor session.
    matches = [
        line for line in open(baseline_log_path, errors="replace")
        if "PREVIEW_CANVAS_BOUNDS" in line
    ]
if not matches:
    raise SystemExit("No measured preview-canvas bounds were recorded in this editor session.")
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
echo "Step 8a-Undo: verify a real overlay canvas drag is restored by project Undo/Redo"
dump_hierarchy editor-overlay-history-before-undo.xml emulator-screenshots/editor-overlay-history-before-undo.xml || {
  collect_capture_diagnostics project-history-overlay-undo-controls-missing
  exit 91
}
overlay_undo_coords="$(python3 - emulator-screenshots/editor-overlay-history-before-undo.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje işlemini geri al" or node.attrib.get("enabled", "false").lower() != "true":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1,y1,x2,y2 = map(int, match.groups())
        print((x1+x2)//2, (y1+y2)//2)
        raise SystemExit(0)
raise SystemExit("Enabled project Undo control is unavailable after overlay drag.")
PY
)" || { collect_capture_diagnostics project-history-overlay-undo-disabled; exit 91; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $overlay_undo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-overlay-undo.txt 2>&1 || true
python3 - emulator-screenshots/project-history-overlay-undo.txt <<'PY'
import re, sys
lines = open(sys.argv[1], errors="replace").read().splitlines()
matches = [line for line in lines if "PROJECT_HISTORY_APPLIED action=undo " in line]
if not matches: raise SystemExit("No project Undo diagnostic followed the overlay drag.")
match = re.search(r"beforeOverlayTransform=([^ ]+) afterOverlayTransform=([^ ]+)", matches[-1])
if not match: raise SystemExit("Undo diagnostic is missing selected overlay transform values: " + matches[-1])
before, after = match.groups()
if before == after or before == "none" or after == "none":
    raise SystemExit("Undo did not restore a selected overlay transform: " + matches[-1])
print("PASS: Undo changed overlay model transform from " + before + " back to " + after)
PY
if ! take_screen emulator-screenshots/editor-overlay-after-undo.png; then
  collect_capture_diagnostics project-history-overlay-undo-capture-failure
  exit 91
fi
overlay_undo_center="$(magenta_center emulator-screenshots/editor-overlay-after-undo.png)" || {
  collect_capture_diagnostics project-history-overlay-undo-render-missing
  exit 91
}
read -r overlay_undo_x overlay_undo_y overlay_undo_pixels <<< "$overlay_undo_center"
if [ "$((overlay_undo_x - before_x))" -lt -7 ] || [ "$((overlay_undo_x - before_x))" -gt 7 ] ||
   [ "$((overlay_undo_y - before_y))" -lt -7 ] || [ "$((overlay_undo_y - before_y))" -gt 7 ]; then
  echo "Overlay Undo did not restore its original visible position: expected=($before_x,$before_y), actual=($overlay_undo_x,$overlay_undo_y)."
  collect_capture_diagnostics project-history-overlay-undo-position-wrong
  exit 91
fi
dump_hierarchy editor-overlay-history-before-redo.xml emulator-screenshots/editor-overlay-history-before-redo.xml || {
  collect_capture_diagnostics project-history-overlay-redo-controls-missing
  exit 92
}
overlay_redo_coords="$(python3 - emulator-screenshots/editor-overlay-history-before-redo.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje işlemini yinele" or node.attrib.get("enabled", "false").lower() != "true":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1,y1,x2,y2 = map(int, match.groups())
        print((x1+x2)//2, (y1+y2)//2)
        raise SystemExit(0)
raise SystemExit("Enabled project Redo control is unavailable after overlay Undo.")
PY
)" || { collect_capture_diagnostics project-history-overlay-redo-disabled; exit 92; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $overlay_redo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-overlay-redo.txt 2>&1 || true
python3 - emulator-screenshots/project-history-overlay-redo.txt <<'PY'
import re, sys
lines = open(sys.argv[1], errors="replace").read().splitlines()
matches = [line for line in lines if "PROJECT_HISTORY_APPLIED action=redo " in line]
if not matches: raise SystemExit("No project Redo diagnostic followed the overlay Undo.")
match = re.search(r"beforeOverlayTransform=([^ ]+) afterOverlayTransform=([^ ]+)", matches[-1])
if not match: raise SystemExit("Redo diagnostic is missing selected overlay transform values: " + matches[-1])
before, after = match.groups()
if before == after or before == "none" or after == "none":
    raise SystemExit("Redo did not reapply the selected overlay transform: " + matches[-1])
print("PASS: Redo reapplied overlay model transform from " + before + " to " + after)
PY
if ! take_screen emulator-screenshots/editor-overlay-after-redo.png; then
  collect_capture_diagnostics project-history-overlay-redo-capture-failure
  exit 92
fi
overlay_redo_center="$(magenta_center emulator-screenshots/editor-overlay-after-redo.png)" || {
  collect_capture_diagnostics project-history-overlay-redo-render-missing
  exit 92
}
read -r overlay_redo_x overlay_redo_y overlay_redo_pixels <<< "$overlay_redo_center"
if [ "$((overlay_redo_x - after_x))" -lt -7 ] || [ "$((overlay_redo_x - after_x))" -gt 7 ] ||
   [ "$((overlay_redo_y - after_y))" -lt -7 ] || [ "$((overlay_redo_y - after_y))" -gt 7 ]; then
  echo "Overlay Redo did not restore its moved visible position: expected=($after_x,$after_y), actual=($overlay_redo_x,$overlay_redo_y)."
  collect_capture_diagnostics project-history-overlay-redo-position-wrong
  exit 92
fi
echo "PASS: project Undo/Redo restored the overlay's visible canvas position in both directions."
overlay_back_coords="$(find_bottom_tool emulator-screenshots/editor-window.xml "Ana araçlara dön" || true)"
if [ -z "$overlay_back_coords" ]; then
  echo "Could not find the overlay row's return-to-main-dock control."
  collect_capture_diagnostics overlay-main-back-missing
  exit 36
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $overlay_back_coords
sleep 1
dump_hierarchy editor-main-dock.xml emulator-screenshots/editor-main-dock.xml || true

echo "Step 8b-AudioHistory: verify sound-volume Undo/Redo restores both the mix model and visible slider"
audio_tab_coords="$(find_bottom_tool emulator-screenshots/editor-main-dock.xml "Ses" || true)"
if [ -z "$audio_tab_coords" ]; then
  echo "Global editor dock did not expose the audio panel after overlay history tests."
  collect_capture_diagnostics project-history-audio-tab-missing
  exit 93
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $audio_tab_coords
sleep 1
if ! dump_hierarchy editor-project-audio-panel.xml emulator-screenshots/editor-project-audio-panel.xml; then
  collect_capture_diagnostics project-history-audio-panel-missing
  exit 93
fi
# The audio panel is a vertically scrollable settings sheet. On a standard
# viewport only the music controls are initially visible; scroll its own sheet
# (not the timeline) to expose the sound-effect volume slider.
audio_sheet_bounds="$(python3 - emulator-screenshots/editor-project-audio-panel.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); candidates=[]
for n in root.iter("node"):
    if n.attrib.get("class")!="android.widget.ScrollView" or n.attrib.get("scrollable")!="true": continue
    m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2-x1>900 and y1>1300 and y2-y1>250: candidates.append((x1,y1,x2,y2))
if not candidates: raise SystemExit("Could not locate the vertical audio settings ScrollView.")
x1,y1,x2,y2=max(candidates,key=lambda b:(b[2]-b[0])*(b[3]-b[1]))
print((x1+x2)//2,y1+35,y2-35)
PY
)" || { collect_capture_diagnostics project-history-audio-sheet-scrollview-missing; exit 93; }
read -r audio_scroll_x audio_scroll_top audio_scroll_bottom <<< "$audio_sheet_bounds"
sound_volume_bounds=""
sound_volume_visible_height=0
for scroll_attempt in $(seq 1 8); do
  timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$audio_scroll_x" "$audio_scroll_bottom" "$audio_scroll_x" "$audio_scroll_top" 650
  sleep 0.4
  audio_scroll_xml="editor-project-audio-panel-scrolled-$scroll_attempt.xml"
  if ! dump_hierarchy "$audio_scroll_xml" "emulator-screenshots/$audio_scroll_xml"; then
    collect_capture_diagnostics project-history-audio-panel-scroll-capture-failed
    exit 93
  fi
  sound_volume_bounds="$(find_content_description_bounds "emulator-screenshots/$audio_scroll_xml" "Ses efekti ses seviyesi" || true)"
  if [ -n "$sound_volume_bounds" ]; then
    read -r _ _ _ sound_volume_visible_height _ _ _ _ <<< "$sound_volume_bounds"
    # A non-empty accessibility node may be only a few pixels of a SeekBar
    # peeking above the ScrollView's clipped edge. It is not a usable touch
    # target. Keep scrolling until the full control has a practical visible area.
    if [ "$sound_volume_visible_height" -ge 40 ]; then
      cp "emulator-screenshots/$audio_scroll_xml" emulator-screenshots/editor-project-audio-panel-scrolled.xml
      break
    fi
    echo "Sound-effect volume slider is only partially visible ($sound_volume_visible_height px); scrolling farther (attempt $scroll_attempt/8)."
    sound_volume_bounds=""
  fi
done
if [ -z "$sound_volume_bounds" ]; then
  echo "Sound-effect volume slider never became fully visible after scrolling the audio settings sheet."
  collect_capture_diagnostics project-history-sound-volume-slider-still-offscreen
  exit 93
fi
if [ -z "$sound_volume_bounds" ]; then
  echo "Audio panel did not expose the accessible sound-effect volume slider."
  collect_capture_diagnostics project-history-sound-volume-slider-missing
  exit 93
fi
read -r sound_cx sound_cy sound_w sound_h sound_x1 sound_y1 sound_x2 sound_y2 <<< "$sound_volume_bounds"
# Start on the actual max-position thumb center (SeekBar horizontal padding is
# included in the view bounds), then move far enough to produce a clear value change.
sound_drag_start=$((sound_x2 - 16))
sound_drag_end=$((sound_x1 + sound_w * 42 / 100))
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$sound_drag_start" "$sound_cy" "$sound_drag_end" "$sound_cy" 1000
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s AudioVolumeTouchTest:I > emulator-screenshots/sound-volume-touch-logcat.txt 2>&1 || true
if ! grep -Fq "VOLUME_SEEK_START" emulator-screenshots/sound-volume-touch-logcat.txt; then
  echo "The swipe did not acquire the actual sound-volume SeekBar thumb; no application volume change can be tested yet."
  cat emulator-screenshots/sound-volume-touch-logcat.txt || true
  collect_capture_diagnostics project-history-sound-volume-thumb-not-acquired
  exit 94
fi
if ! grep -Fq "VOLUME_SEEK_PROGRESS" emulator-screenshots/sound-volume-touch-logcat.txt; then
  echo "The sound-volume SeekBar received touch tracking but emitted no fromUser progress change."
  cat emulator-screenshots/sound-volume-touch-logcat.txt || true
  collect_capture_diagnostics project-history-sound-volume-progress-not-changed
  exit 94
fi
if ! grep -Fq "VOLUME_SEEK_STOP" emulator-screenshots/sound-volume-touch-logcat.txt; then
  echo "The sound-volume SeekBar did not finish its drag gesture."
  cat emulator-screenshots/sound-volume-touch-logcat.txt || true
  collect_capture_diagnostics project-history-sound-volume-gesture-not-finished
  exit 94
fi
dump_hierarchy editor-project-audio-after-volume.xml emulator-screenshots/editor-project-audio-after-volume.xml || {
  collect_capture_diagnostics project-history-sound-volume-after-drag-missing
  exit 93
}
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-sound-volume-gesture.txt 2>&1 || true
if ! grep -Fq "PROJECT_HISTORY_GESTURE_END" emulator-screenshots/project-history-sound-volume-gesture.txt; then
  echo "The sound-volume drag reached the SeekBar but did not finish a project-history gesture."
  cat emulator-screenshots/project-history-sound-volume-gesture.txt || true
  collect_capture_diagnostics project-history-sound-volume-gesture-not-committed
  exit 94
fi
if ! grep -Fq "PROJECT_HISTORY_GESTURE_COMMITTED" emulator-screenshots/project-history-sound-volume-gesture.txt; then
  echo "The sound-volume gesture did not complete the project-history commit stage."
  cat emulator-screenshots/project-history-sound-volume-gesture.txt || true
  collect_capture_diagnostics project-history-sound-volume-commit-missing
  exit 94
fi
if grep -Fq "PROJECT_HISTORY_GESTURE_END" emulator-screenshots/project-history-sound-volume-gesture.txt &&
   ! grep -Fq "afterSoundVolume=" emulator-screenshots/project-history-sound-volume-gesture.txt; then
  echo "Sound-volume history diagnostics are missing the final model value."
  cat emulator-screenshots/project-history-sound-volume-gesture.txt || true
  collect_capture_diagnostics project-history-sound-volume-model-log-missing
  exit 94
fi
# Tool detail panels replace the global timeline/Undo dock with a full-width
# "Araçlara dön" action. Close the audio sheet before querying project history;
# the application already committed the changed volume (verified above), but
# Undo is deliberately not present in the detail panel's accessibility tree.
volume_panel_back_coords="$(find_bottom_tool emulator-screenshots/editor-project-audio-after-volume.xml "Araçlara dön" || true)"
if [ -z "$volume_panel_back_coords" ]; then
  echo "The audio detail panel did not expose its return-to-tools control after the volume swipe."
  collect_capture_diagnostics project-history-sound-volume-return-control-missing
  exit 93
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap "$volume_panel_back_coords"
sleep 1
dump_hierarchy editor-main-dock-after-volume.xml emulator-screenshots/editor-main-dock-after-volume.xml || {
  collect_capture_diagnostics project-history-sound-volume-main-dock-missing
  exit 93
}
volume_undo_coords="$(python3 - emulator-screenshots/editor-main-dock-after-volume.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje işlemini geri al" or node.attrib.get("enabled", "false").lower() != "true": continue
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if m:
        x1,y1,x2,y2 = map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Project Undo is unavailable after the volume gesture.")
PY
)" || {
  echo "Undo is unavailable after the real SFX-volume swipe. Captured touch and history values follow."
  cat emulator-screenshots/sound-volume-touch-logcat.txt || true
  cat emulator-screenshots/project-history-sound-volume-gesture.txt || true
  collect_capture_diagnostics project-history-sound-volume-undo-disabled
  exit 93
}
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $volume_undo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-sound-volume-undo.txt 2>&1 || true
volume_history_values="$(python3 - emulator-screenshots/project-history-sound-volume-undo.txt <<'PY'
import re, sys
lines = open(sys.argv[1], errors="replace").read().splitlines()
events = [line for line in lines if "PROJECT_HISTORY_APPLIED action=undo " in line]
controls = [line for line in lines if "PROJECT_HISTORY_CONTROLS " in line]
if not events or not controls: raise SystemExit("Undo did not log both history transition and restored control state.")
event = re.search(r"beforeSoundVolume=(\d+) afterSoundVolume=(\d+)", events[-1])
ui = re.search(r"soundModel=(\d+) soundSeek=(-?\d+)", controls[-1])
if not event or not ui: raise SystemExit("Undo diagnostics do not include sound mix, SeekBar, and label values.")
changed, restored = map(int,event.groups()); model, seek = map(int,ui.groups())
label = re.search(r"soundLabel=Efekt sesi: (\\d+)%", controls[-1])
if changed == restored: raise SystemExit(f"Sound volume Undo did not change the model: {events[-1]}")
if model != restored or seek != restored or not label or int(label.group(1)) != restored:
    raise SystemExit(f"Undo did not restore the model, SeekBar, and label together: model={model}, seek={seek}, label={label.group(1) if label else 'missing'}, expected={restored}; {controls[-1]}")
print(changed, restored)
PY
)" || { collect_capture_diagnostics project-history-sound-volume-undo-failed; exit 94; }
read -r changed_sound_volume original_sound_volume <<< "$volume_history_values"
echo "PASS: one Undo restored sound volume $changed_sound_volume -> $original_sound_volume and its SeekBar progress matches."
dump_hierarchy editor-main-dock-before-redo.xml emulator-screenshots/editor-main-dock-before-redo.xml || {
  collect_capture_diagnostics project-history-sound-volume-redo-controls-missing
  exit 94
}
volume_redo_coords="$(python3 - emulator-screenshots/editor-main-dock-before-redo.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje işlemini yinele" or node.attrib.get("enabled", "false").lower() != "true": continue
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if m:
        x1,y1,x2,y2 = map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Project Redo is unavailable after sound-volume Undo.")
PY
)" || { collect_capture_diagnostics project-history-sound-volume-redo-disabled; exit 94; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $volume_redo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-sound-volume-redo.txt 2>&1 || true
python3 - emulator-screenshots/project-history-sound-volume-redo.txt "$changed_sound_volume" "$original_sound_volume" <<'PY'
import re, sys
lines = open(sys.argv[1], errors="replace").read().splitlines()
events = [line for line in lines if "PROJECT_HISTORY_APPLIED action=redo " in line]
controls = [line for line in lines if "PROJECT_HISTORY_CONTROLS " in line]
if not events or not controls: raise SystemExit("Redo did not log both history transition and restored control state.")
event = re.search(r"beforeSoundVolume=(\d+) afterSoundVolume=(\d+)", events[-1])
ui = re.search(r"soundModel=(\d+) soundSeek=(-?\d+)", controls[-1])
if not event or not ui: raise SystemExit("Redo diagnostics do not include sound mix, SeekBar, and label values.")
before, after = map(int,event.groups()); model, seek = map(int,ui.groups())
expected_new, expected_old = map(int,sys.argv[2:4])
label = re.search(r"soundLabel=Efekt sesi: (\\d+)%", controls[-1])
if before != expected_old or after != expected_new: raise SystemExit(f"Redo applied the wrong sound volume: {events[-1]}")
if model != after or seek != after or not label or int(label.group(1)) != after:
    raise SystemExit(f"Redo did not restore the model, SeekBar, and label together: model={model}, seek={seek}, label={label.group(1) if label else 'missing'}, expected={after}; {controls[-1]}")
print(f"PASS: one Redo reapplied sound volume {before} -> {after} and its SeekBar progress matches.")
PY

# Restore the default source mix before later audio playback and trim checks.
dump_hierarchy editor-main-dock-after-redo.xml emulator-screenshots/editor-main-dock-after-redo.xml || { collect_capture_diagnostics project-history-sound-volume-cleanup-controls-missing; exit 94; }
cleanup_undo_coords="$(python3 - emulator-screenshots/editor-main-dock-after-redo.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if n.attrib.get("content-desc","").strip()=="Proje işlemini geri al" and n.attrib.get("enabled","false").lower()=="true":
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Undo unavailable while restoring default test audio volume.")
PY
)" || { collect_capture_diagnostics project-history-sound-volume-cleanup-undo-disabled; exit 94; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap "$cleanup_undo_coords"
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-sound-volume-cleanup.txt 2>&1 || true
python3 - emulator-screenshots/project-history-sound-volume-cleanup.txt "$changed_sound_volume" "$original_sound_volume" <<'PY'
import re,sys
lines=open(sys.argv[1],errors="replace").read().splitlines()
events=[x for x in lines if "PROJECT_HISTORY_APPLIED action=undo " in x]
controls=[x for x in lines if "PROJECT_HISTORY_CONTROLS " in x]
if not events or not controls: raise SystemExit("Default-volume cleanup did not log the final Undo transition and controls.")
event=re.search(r"beforeSoundVolume=(\d+) afterSoundVolume=(\d+)",events[-1])
m=re.search(r"soundModel=(\d+) soundSeek=(-?\d+)",controls[-1])
changed,expected=map(int,sys.argv[1:3])
if not event or tuple(map(int,event.groups()))!=(changed,expected): raise SystemExit("Cleanup Undo did not restore the original sound value: "+events[-1])
label=re.search(r"soundLabel=Efekt sesi: (\\d+)%",controls[-1])
if not m or tuple(map(int,m.groups()))!=(expected,expected) or not label or int(label.group(1))!=expected:
    raise SystemExit("Test audio model, SeekBar, or label was not restored before fixture import: "+controls[-1])
print(f"PASS: Undo/Redo round-trip restored the model, SeekBar and label to initial sound volume {expected}.")
PY

# The audio panel was closed before Undo/Redo, so the global dock is already
# visible here. Continue directly with the independent filter-history checks.
dump_hierarchy editor-main-dock-before-filter.xml emulator-screenshots/editor-main-dock-before-filter.xml || { collect_capture_diagnostics project-history-filter-dock-missing; exit 95; }
filter_tab_coords="$(find_bottom_tool emulator-screenshots/editor-main-dock-before-filter.xml "Efektler" || true)"
if [ -z "$filter_tab_coords" ]; then
  collect_capture_diagnostics project-history-filter-tab-missing
  exit 95
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $filter_tab_coords
sleep 1
dump_hierarchy editor-filter-panel.xml emulator-screenshots/editor-filter-panel.xml || { collect_capture_diagnostics project-history-filter-panel-missing; exit 95; }
mono_preset_coords="$(find_bottom_tool emulator-screenshots/editor-filter-panel.xml "Siyah-beyaz" || true)"
if [ -z "$mono_preset_coords" ]; then
  collect_capture_diagnostics project-history-monochrome-preset-missing
  exit 95
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $mono_preset_coords
sleep 1
dump_hierarchy editor-filter-after-monochrome.xml emulator-screenshots/editor-filter-after-monochrome.xml || { collect_capture_diagnostics project-history-filter-undo-controls-missing; exit 95; }
filter_undo_coords="$(python3 - emulator-screenshots/editor-filter-after-monochrome.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje işlemini geri al" or node.attrib.get("enabled", "false").lower() != "true": continue
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if m:
        x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Undo unavailable after monochrome preset.")
PY
)" || { collect_capture_diagnostics project-history-filter-undo-disabled; exit 95; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $filter_undo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-filter-preset-undo.txt 2>&1 || true
python3 - emulator-screenshots/project-history-filter-preset-undo.txt <<'PY'
import re, sys
lines=open(sys.argv[1],errors="replace").read().splitlines()
event=[x for x in lines if "PROJECT_HISTORY_APPLIED action=undo " in x]
controls=[x for x in lines if "PROJECT_HISTORY_CONTROLS " in x]
if not event or not controls: raise SystemExit("Filter Undo did not log restored model and controls.")
if not re.search(r"beforeFilter=MONOCHROME afterFilter=ORIGINAL",event[-1]): raise SystemExit("Filter preset Undo did not restore ORIGINAL: "+event[-1])
if not re.search(r"filterPreset=ORIGINAL brightnessModel=0 brightnessSeek=100",controls[-1]): raise SystemExit("Filter preset Undo restored model but not color controls: "+controls[-1])
print("PASS: one Undo restored the Original filter preset and synchronized the brightness control.")
PY
dump_hierarchy editor-filter-before-preset-redo.xml emulator-screenshots/editor-filter-before-preset-redo.xml || { collect_capture_diagnostics project-history-filter-redo-controls-missing; exit 96; }
filter_redo_coords="$(python3 - emulator-screenshots/editor-filter-before-preset-redo.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc","").strip()!="Proje işlemini yinele" or node.attrib.get("enabled","false").lower()!="true": continue
    m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",node.attrib.get("bounds",""))
    if m:
        x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Redo unavailable after filter Undo.")
PY
)" || { collect_capture_diagnostics project-history-filter-redo-disabled; exit 96; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $filter_redo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-filter-preset-redo.txt 2>&1 || true
python3 - emulator-screenshots/project-history-filter-preset-redo.txt <<'PY'
import re, sys
lines=open(sys.argv[1],errors="replace").read().splitlines()
event=[x for x in lines if "PROJECT_HISTORY_APPLIED action=redo " in x]
controls=[x for x in lines if "PROJECT_HISTORY_CONTROLS " in x]
if not event or not controls: raise SystemExit("Filter Redo did not log restored model and controls.")
if not re.search(r"beforeFilter=ORIGINAL afterFilter=MONOCHROME",event[-1]): raise SystemExit("Filter preset Redo did not reapply MONOCHROME: "+event[-1])
if not re.search(r"filterPreset=MONOCHROME",controls[-1]): raise SystemExit("Filter preset Redo left the UI control stale: "+controls[-1])
print("PASS: one Redo reapplied the Monochrome preset.")
PY

echo "Step 8b-FilterSlider: verify brightness gesture Undo/Redo restores actual slider progress"
brightness_bounds="$(find_content_description_bounds emulator-screenshots/editor-filter-panel.xml "Parlaklık filtresi" || true)"
if [ -z "$brightness_bounds" ]; then collect_capture_diagnostics project-history-brightness-slider-missing; exit 97; fi
read -r brightness_cx brightness_cy brightness_w brightness_h brightness_x1 brightness_y1 brightness_x2 brightness_y2 <<< "$brightness_bounds"
brightness_drag_start=$((brightness_x1 + brightness_w / 2))
brightness_drag_end=$((brightness_x1 + brightness_w * 62 / 100))
timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$brightness_drag_start" "$brightness_cy" "$brightness_drag_end" "$brightness_cy" 650
sleep 1
dump_hierarchy editor-filter-after-brightness-drag.xml emulator-screenshots/editor-filter-after-brightness-drag.xml || { collect_capture_diagnostics project-history-brightness-undo-controls-missing; exit 97; }
brightness_undo_coords="$(python3 - emulator-screenshots/editor-filter-after-brightness-drag.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if n.attrib.get("content-desc","").strip()=="Proje işlemini geri al" and n.attrib.get("enabled","false").lower()=="true":
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Undo unavailable after brightness gesture.")
PY
)" || { collect_capture_diagnostics project-history-brightness-undo-disabled; exit 97; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $brightness_undo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-brightness-undo.txt 2>&1 || true
brightness_undo_values="$(python3 - emulator-screenshots/project-history-brightness-undo.txt <<'PY'
import re,sys
lines=open(sys.argv[1],errors="replace").read().splitlines()
events=[x for x in lines if "PROJECT_HISTORY_APPLIED action=undo " in x]
controls=[x for x in lines if "PROJECT_HISTORY_CONTROLS " in x]
if not events or not controls: raise SystemExit("Brightness Undo did not emit model and control diagnostics.")
e=re.search(r"beforeFilterBrightness=(-?\d+) afterFilterBrightness=(-?\d+)",events[-1])
u=re.search(r"brightnessModel=(-?\d+) brightnessSeek=(-?\d+)",controls[-1])
if not e or not u: raise SystemExit("Brightness diagnostics missing from Undo.")
changed,restored=map(int,e.groups()); model,seek=map(int,u.groups())
if changed==restored: raise SystemExit("Brightness gesture did not change the filter model: "+events[-1])
if model!=restored or seek!=restored+100: raise SystemExit(f"Undo left brightness slider out of sync: model={model}, seek={seek}, expectedSeek={restored+100}; {controls[-1]}")
print(changed,restored)
PY
)" || { collect_capture_diagnostics project-history-brightness-undo-failed; exit 98; }
read -r changed_brightness original_brightness <<< "$brightness_undo_values"
echo "PASS: one Undo restored brightness $changed_brightness -> $original_brightness and its slider progress."
dump_hierarchy editor-filter-before-brightness-redo.xml emulator-screenshots/editor-filter-before-brightness-redo.xml || { collect_capture_diagnostics project-history-brightness-redo-controls-missing; exit 98; }
brightness_redo_coords="$(python3 - emulator-screenshots/editor-filter-before-brightness-redo.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if n.attrib.get("content-desc","").strip()=="Proje işlemini yinele" and n.attrib.get("enabled","false").lower()=="true":
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Redo unavailable after brightness Undo.")
PY
)" || { collect_capture_diagnostics project-history-brightness-redo-disabled; exit 98; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $brightness_redo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-brightness-redo.txt 2>&1 || true
python3 - emulator-screenshots/project-history-brightness-redo.txt "$changed_brightness" "$original_brightness" <<'PY'
import re,sys
lines=open(sys.argv[1],errors="replace").read().splitlines()
events=[x for x in lines if "PROJECT_HISTORY_APPLIED action=redo " in x]
controls=[x for x in lines if "PROJECT_HISTORY_CONTROLS " in x]
if not events or not controls: raise SystemExit("Brightness Redo did not emit model and control diagnostics.")
e=re.search(r"beforeFilterBrightness=(-?\d+) afterFilterBrightness=(-?\d+)",events[-1])
u=re.search(r"brightnessModel=(-?\d+) brightnessSeek=(-?\d+)",controls[-1])
if not e or not u: raise SystemExit("Brightness diagnostics missing from Redo.")
before,after=map(int,e.groups()); model,seek=map(int,u.groups())
expected_new,expected_old=map(int,sys.argv[2:4])
if before!=expected_old or after!=expected_new: raise SystemExit("Redo reapplied wrong brightness: "+events[-1])
if model!=after or seek!=after+100: raise SystemExit("Redo left brightness slider out of sync: "+controls[-1])
print(f"PASS: one Redo reapplied brightness {before} -> {after} and its slider progress.")
PY

# Reset to the unfiltered project appearance before the video/export smoke tests.
original_preset_coords="$(find_bottom_tool emulator-screenshots/editor-filter-panel.xml "Orijinal" || true)"
if [ -n "$original_preset_coords" ]; then
  timeout -k 2s 8s adb -s emulator-5554 shell input tap $original_preset_coords
  sleep 1
fi
filter_panel_back="$(find_bottom_tool emulator-screenshots/editor-filter-panel.xml "Araçlara dön" || true)"
if [ -z "$filter_panel_back" ]; then collect_capture_diagnostics project-history-filter-close-control-missing; exit 99; fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $filter_panel_back
sleep 1
dump_hierarchy editor-main-dock-after-filter.xml emulator-screenshots/editor-main-dock-after-filter.xml || true
video_clip_coords="$(python3 - emulator-screenshots/editor-main-dock.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if not node.attrib.get("content-desc", "").startswith("Klip 1:"):
        continue
    if node.attrib.get("clickable") != "true":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)"
if [ -z "$video_clip_coords" ]; then
  echo "Could not locate the video clip block after returning from overlay tools."
  collect_capture_diagnostics editor-video-reselect-target-missing
  exit 25
fi
echo "Reselecting the main video clip before testing its canvas transform at $video_clip_coords"
timeout -k 2s 8s adb -s emulator-5554 shell input tap $video_clip_coords
sleep 1
if ! take_screen emulator-screenshots/editor-panel-clip-open.png; then
  collect_capture_diagnostics editor-panel-open-capture-failure
  exit 6
fi
if ! dump_hierarchy editor-panel-clip-open.xml emulator-screenshots/editor-panel-clip-open.xml; then
  collect_capture_diagnostics editor-panel-clip-open-hierarchy-unavailable
  exit 25
fi
if ! grep -q "Seçili video klibi sınırı" emulator-screenshots/editor-panel-clip-open.xml; then
  echo "Tapping the main video track did not restore its selected preview frame."
  collect_capture_diagnostics editor-video-reselect-frame-missing
  exit 25
fi

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
if ! dump_hierarchy editor-panel-trim.xml emulator-screenshots/editor-panel-trim.xml; then
  echo "Could not capture the selected video transform surface before the drag test."
  collect_capture_diagnostics video-canvas-transform-hierarchy-unavailable
  exit 63
fi

# With the clip detail tool active, the main-video gesture surface must receive
# the canvas drag above paused media and text/overlay content. Verify its frame
# moves, then reverse the gesture so later export checks use the original transform.
clip_canvas_center="$(python3 - emulator-screenshots/editor-panel-trim.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc") != "Seçili video klibi sınırı":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)"
if [ -z "$clip_canvas_center" ]; then
  echo "Selected video clip did not expose its preview transform bounds."
  collect_capture_diagnostics video-canvas-transform-target-missing
  exit 64
fi
read -r clip_before_x clip_before_y <<< "$clip_canvas_center"
timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$clip_before_x" "$clip_before_y" "$((clip_before_x + 40))" "$((clip_before_y + 24))" 500
sleep 1
if ! dump_hierarchy editor-panel-trim-after-drag.xml emulator-screenshots/editor-panel-trim-after-drag.xml; then
  echo "Could not capture the video selection frame after its direct canvas drag."
  collect_capture_diagnostics video-canvas-transform-after-hierarchy-unavailable
  exit 65
fi
if ! grep -q "Seçili video klibi sınırı" emulator-screenshots/editor-panel-trim-after-drag.xml; then
  echo "Video selection frame disappeared after the direct canvas drag."
  collect_capture_diagnostics video-canvas-transform-after-target-missing
  exit 65
fi
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorTouchTest:I \
  > emulator-screenshots/video-canvas-touch-logcat.txt 2>&1 || true
python3 - emulator-screenshots/video-canvas-touch-logcat.txt <<'PY'
import re
import sys

lines = open(sys.argv[1], errors="replace").read().splitlines()
down_lines = [line for line in lines if "VIDEO_CANVAS_TOUCH_DOWN" in line]
up_lines = [line for line in lines if "VIDEO_CANVAS_TOUCH_UP" in line]
if not down_lines or not up_lines:
    raise SystemExit("The video canvas gesture surface did not report both ACTION_DOWN and ACTION_UP.")
down, up = down_lines[-1], up_lines[-1]
canvas = re.search(r"canvas=(\d+)x(\d+)", down)
start = re.search(r"startTransform=([-+0-9.eE]+),([-+0-9.eE]+)", down)
end = re.search(r"transform=([-+0-9.eE]+),([-+0-9.eE]+)", up)
if not canvas or not start or not end:
    raise SystemExit(f"Incomplete video transform diagnostics. DOWN={down} UP={up}")
width, height = map(float, canvas.groups())
sx, sy = map(float, start.groups())
ex, ey = map(float, end.groups())
moved_x = (ex - sx) * width / 2
moved_y = (ey - sy) * height / 2
if not (28 <= moved_x <= 52 and 14 <= moved_y <= 34):
    raise SystemExit(
        "Video transform model did not retain the expected canvas movement: "
        f"observed=({moved_x:.1f},{moved_y:.1f})px expected=(40,24)px; DOWN={down} UP={up}"
    )
print(
    "PASS: direct video drag changed the saved transform by "
    f"({moved_x:.1f},{moved_y:.1f}) project-canvas px, independent of the clipped selection-frame bounds."
)
PY
timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$((clip_before_x + 40))" "$((clip_before_y + 24))" "$clip_before_x" "$clip_before_y" 500
sleep 1
if ! dump_hierarchy editor-panel-trim-restored.xml emulator-screenshots/editor-panel-trim-restored.xml; then
  echo "Could not verify restoration of the video transform after the reverse gesture."
  collect_capture_diagnostics video-canvas-transform-restore-hierarchy-unavailable
  exit 67
fi
python3 - emulator-screenshots/editor-panel-trim-restored.xml "$clip_before_x" "$clip_before_y" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
expected_x, expected_y = map(int, sys.argv[2:4])
for node in root.iter("node"):
    if node.attrib.get("content-desc") != "Seçili video klibi sınırı":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        actual_x, actual_y = (x1 + x2) // 2, (y1 + y2) // 2
        if abs(actual_x - expected_x) > 6 or abs(actual_y - expected_y) > 6:
            raise SystemExit(f"Reverse gesture did not restore the video transform: expected=({expected_x},{expected_y}), actual=({actual_x},{actual_y})")
        print(f"PASS: direct video drag moved the preview frame and the reverse gesture restored it at ({actual_x},{actual_y})")
        break
else:
    raise SystemExit("Selected video frame disappeared after the reverse transform gesture.")
PY

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

# Capture panel geometry before later Undo/Redo/audio gestures clear logcat. These
# measurements belong to the panel workflow above and must not depend on logs
# surviving unrelated test phases.
timeout -k 2s 10s adb -s emulator-5554 logcat -d -v threadtime -s EditorPanelLayoutTest:I \
  > emulator-screenshots/editor-panel-layout-logcat.txt 2>&1 || true

# Audio imports were waited for and snapshotted immediately after editor startup.
# Never reread the live ring buffer here: Undo/Redo tests clear it intentionally.
if ! grep -Fq "EDITOR_SMOKE_AUDIO_ADDED kind=music" emulator-screenshots/audio-fixture-import-logcat.txt \
    || ! grep -Fq "EDITOR_SMOKE_AUDIO_ADDED kind=sound" emulator-screenshots/audio-fixture-import-logcat.txt; then
  echo "The preserved editor-startup audio fixture markers are incomplete."
  collect_capture_diagnostics audio-fixture-startup-markers-missing
  exit 76
fi
echo "PASS: both audio fixtures were imported through the production importer (startup markers preserved)."

echo "Step 8c/12: drag the music and sound-effect edge targets independently"
run_audio_edge_smoke() {
  local kind="$1" edge="$2" description="$3" direction="$4"
  local before_xml="editor-audio-${kind}-${edge}-before.xml"
  local after_xml="editor-audio-${kind}-${edge}-after.xml"
  local coords cx cy target_w target_h x1 y1 x2 y2 delta marker
  if ! dump_hierarchy "$before_xml" "emulator-screenshots/$before_xml"; then
    echo "Could not inspect the $kind $edge trim target."
    collect_capture_diagnostics "audio-$kind-$edge-before-hierarchy-failed"
    exit 70
  fi
  coords="$(find_content_description_bounds "emulator-screenshots/$before_xml" "$description")"
  if [ -z "$coords" ]; then
    echo "The $kind $edge trim handle ('$description') is missing from the timeline."
    collect_capture_diagnostics "audio-$kind-$edge-target-missing"
    exit 71
  fi
  read -r cx cy target_w target_h x1 y1 x2 y2 <<< "$coords"
  if [ "$target_w" -lt 40 ] || [ "$target_h" -lt 20 ]; then
    echo "The $kind $edge touch target is undersized: ${target_w}x${target_h}px bounds=[$x1,$y1][$x2,$y2]."
    collect_capture_diagnostics "audio-$kind-$edge-target-undersized"
    exit 72
  fi
  delta=42
  if [ "$direction" = "left" ]; then
    delta=-42
  fi
  echo "Dragging $description from ($cx,$cy) by ${delta}px; hit area=${target_w}x${target_h}px."
  timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$cx" "$cy" "$((cx + delta))" "$cy" 500
  sleep 1
  if ! dump_hierarchy "$after_xml" "emulator-screenshots/$after_xml"; then
    echo "Could not inspect the timeline after dragging $kind $edge."
    collect_capture_diagnostics "audio-$kind-$edge-after-hierarchy-failed"
    exit 73
  fi
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorTouchTest:I \
    > "emulator-screenshots/audio-$kind-$edge-touch-logcat.txt" 2>&1 || true
  marker="$(grep -F "AUDIO_EDGE_TRIM kind=$kind edge=$edge " "emulator-screenshots/audio-$kind-$edge-touch-logcat.txt" | tail -n 1 || true)"
  if [ -z "$marker" ]; then
    echo "The $kind $edge gesture did not reach its edge-trim handler."
    collect_capture_diagnostics "audio-$kind-$edge-gesture-not-received"
    exit 74
  fi
  python3 - "$marker" "$kind" "$edge" <<'PY'
import re
import sys
line, kind, edge = sys.argv[1:]
fields = dict(re.findall(r"([A-Za-z]+)=([^ ]+)", line))
required = ["beforeStartMs", "beforeDurationMs", "afterStartMs", "afterDurationMs"]
missing = [key for key in required if key not in fields]
if missing:
    raise SystemExit("Trim event was incomplete: " + ", ".join(missing) + " :: " + line)
before_start = int(fields["beforeStartMs"])
before_duration = int(fields["beforeDurationMs"])
after_start = int(fields["afterStartMs"])
after_duration = int(fields["afterDurationMs"])
if edge == "start" and not (after_start > before_start and after_duration < before_duration):
    raise SystemExit(f"{kind} start edge did not trim the start: {line}")
if edge == "end" and not after_duration < before_duration:
    raise SystemExit(f"{kind} end edge did not shorten duration: {line}")
print(f"PASS: {kind} {edge} edge changed its saved timing independently ({before_start}ms/{before_duration}ms -> {after_start}ms/{after_duration}ms)")
PY
}

run_audio_edge_smoke music start "Müzik başlangıç tutamacı" right
run_audio_edge_smoke music end "Müzik bitiş tutamacı" left
run_audio_edge_smoke sound start "Efekt başlangıç tutamacı" right
run_audio_edge_smoke sound end "Efekt bitiş tutamacı" left

echo "Step 8c-Undo: verify project Undo/Redo restores an actual sound-effect end trim"
sound_trim_values="$(python3 - emulator-screenshots/audio-sound-end-touch-logcat.txt <<'PY'
import re
import sys
lines = open(sys.argv[1], errors="replace").read().splitlines()
matches = [line for line in lines if "AUDIO_EDGE_TRIM kind=sound edge=end " in line]
if not matches:
    raise SystemExit("The latest sound-effect end-trim measurement is missing.")
fields = dict(re.findall(r"([A-Za-z]+)=([^ ]+)", matches[-1]))
for key in ("beforeDurationMs", "afterDurationMs"):
    if key not in fields:
        raise SystemExit("Missing " + key + " in audio trim marker: " + matches[-1])
before, after = int(fields["beforeDurationMs"]), int(fields["afterDurationMs"])
if after >= before:
    raise SystemExit(f"The sound end trim did not shorten the effect: {before} -> {after}")
print(before, after)
PY
)" || {
  collect_capture_diagnostics project-history-sound-trim-marker-missing
  exit 88
}
read -r sound_before_duration sound_after_duration <<< "$sound_trim_values"
dump_hierarchy editor-history-audio-before-undo.xml emulator-screenshots/editor-history-audio-before-undo.xml || {
  collect_capture_diagnostics project-history-audio-controls-missing
  exit 88
}
history_audio_undo="$(python3 - emulator-screenshots/editor-history-audio-before-undo.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje işlemini geri al" or node.attrib.get("enabled","false").lower() != "true":
        continue
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds",""))
    if m:
        x1,y1,x2,y2 = map(int,m.groups())
        print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Enabled project Undo control is unavailable.")
PY
)" || {
  collect_capture_diagnostics project-history-audio-undo-disabled
  exit 88
}
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $history_audio_undo
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-sound-trim-undo.txt 2>&1 || true
if ! grep -Eq "PROJECT_HISTORY_APPLIED action=undo .*beforeSoundDurationMs=$sound_after_duration afterSoundDurationMs=$sound_before_duration" emulator-screenshots/project-history-sound-trim-undo.txt; then
  echo "Undo did not restore the sound-effect duration from $sound_after_duration to $sound_before_duration."
  cat emulator-screenshots/project-history-sound-trim-undo.txt || true
  collect_capture_diagnostics project-history-sound-trim-undo-failed
  exit 89
fi
echo "PASS: one project Undo restored sound-effect duration $sound_after_duration -> $sound_before_duration ms."
dump_hierarchy editor-history-audio-before-redo.xml emulator-screenshots/editor-history-audio-before-redo.xml || {
  collect_capture_diagnostics project-history-audio-redo-controls-missing
  exit 89
}
history_audio_redo="$(python3 - emulator-screenshots/editor-history-audio-before-redo.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje işlemini yinele" or node.attrib.get("enabled","false").lower() != "true":
        continue
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds",""))
    if m:
        x1,y1,x2,y2 = map(int,m.groups())
        print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Enabled project Redo control is unavailable.")
PY
)" || {
  collect_capture_diagnostics project-history-audio-redo-disabled
  exit 89
}
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $history_audio_redo
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-sound-trim-redo.txt 2>&1 || true
if ! grep -Eq "PROJECT_HISTORY_APPLIED action=redo .*beforeSoundDurationMs=$sound_before_duration afterSoundDurationMs=$sound_after_duration" emulator-screenshots/project-history-sound-trim-redo.txt; then
  echo "Redo did not reapply the sound-effect trim from $sound_before_duration to $sound_after_duration."
  cat emulator-screenshots/project-history-sound-trim-redo.txt || true
  collect_capture_diagnostics project-history-sound-trim-redo-failed
  exit 90
fi
echo "PASS: one project Redo reapplied sound-effect duration $sound_before_duration -> $sound_after_duration ms."

run_overlay_edge_smoke() {
  local edge="$1" description="$2" direction="$3"
  local before_xml="editor-overlay-${edge}-before.xml"
  local after_xml="editor-overlay-${edge}-after.xml"
  local coords cx cy target_w target_h x1 y1 x2 y2 delta marker
  if ! dump_hierarchy "$before_xml" "emulator-screenshots/$before_xml"; then
    echo "Could not inspect overlay trim target."
    collect_capture_diagnostics "overlay-$edge-before-hierarchy-failed"
    exit 81
  fi
  # The clip itself owns horizontal drags for trim/move, so swiping on the
  # overlay row is consumed by the clip gesture instead of its parent scroller.
  # All timeline rows share the ruler's horizontal scroll position; swipe the
  # ruler viewport to reveal the far edge without touching the selected clip.
  if [ "$edge" = "end" ] && [ -z "$(find_content_description_bounds "emulator-screenshots/$before_xml" "$description")" ]; then
    ruler_bounds="$(python3 - "emulator-screenshots/$before_xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
def walk(node):
    if node.attrib.get("class") == "android.widget.HorizontalScrollView" and node.attrib.get("scrollable") == "true":
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            left, top, right, bottom = map(int, match.groups())
            if right - left > 500 and 0 < bottom - top <= 100 and top < 1150:
                print(left, top, right, bottom)
                return True
    for child in node:
        if walk(child):
            return True
    return False
walk(root)
PY
)"
    if [ -z "$ruler_bounds" ]; then
      echo "Could not locate the horizontally scrollable timeline ruler."
      collect_capture_diagnostics overlay-end-ruler-not-found
      exit 82
    fi
    read -r ruler_left ruler_top ruler_right ruler_bottom <<< "$ruler_bounds"
    ruler_y=$(((ruler_top + ruler_bottom) / 2))
    ruler_width=$((ruler_right - ruler_left))
    # Start well inside the ruler viewport. The previous gesture began only
    # 35px from its right edge, close enough to Android's system-back region
    # on API 35 to dismiss the editor instead of reliably scrolling the lane.
    # A centered swipe still covers enough content to reveal the offscreen end.
    scroll_start=$((ruler_left + ruler_width * 3 / 4))
    scroll_end=$((ruler_left + ruler_width / 4))
    echo "Timeline ruler bounds=[$ruler_left,$ruler_top][$ruler_right,$ruler_bottom]; safe scroll=($scroll_start,$ruler_y)->($scroll_end,$ruler_y)."
    for scroll_attempt in $(seq 1 8); do
      visible_bounds="$(find_content_description_bounds "emulator-screenshots/$before_xml" "$description")"
      if [ -n "$visible_bounds" ]; then
        read -r _ _ visible_width visible_height _ <<< "$visible_bounds"
        if [ "$visible_width" -ge 40 ] && [ "$visible_height" -ge 20 ]; then
          echo "Overlay end hit target is fully reachable: ${visible_width}x${visible_height}px."
          break
        fi
        echo "Overlay end hit target is only partially visible (${visible_width}x${visible_height}px); applying a short inner-ruler nudge."
        # A large initial swipe can move the far edge to the extreme viewport
        # boundary, where only a sliver of its transparent hit box is exposed.
        # Nudge by 40 screen pixels from the ruler centre so the full 84px
        # target is inside the viewport without approaching system navigation.
        ruler_center=$(((ruler_left + ruler_right) / 2))
        scroll_start=$((ruler_center + 20))
        scroll_end=$((ruler_center - 20))
      else
        echo "Revealing overlay end handle through the inner timeline ruler (scroll $scroll_attempt/8)."
      fi
      timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$scroll_start" "$ruler_y" "$scroll_end" "$ruler_y" 450
      sleep 0.4
      dump_hierarchy "$before_xml" "emulator-screenshots/$before_xml" || true
      root_package="$(python3 - "emulator-screenshots/$before_xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(0)
node = root.find("node")
print(node.attrib.get("package", "") if node is not None else "")
PY
)"
      if [ "$root_package" != "com.mathmotor.video.next" ]; then
        echo "Ruler scrolling unexpectedly left MathMotor (foreground hierarchy package='$root_package')."
        collect_capture_diagnostics overlay-end-scroll-left-app
        exit 86
      fi
    done
  fi
  coords="$(find_content_description_bounds "emulator-screenshots/$before_xml" "$description")"
  if [ -z "$coords" ]; then
    echo "The overlay end trim handle is not visible after scrolling the timeline ruler: $description"
    collect_capture_diagnostics "overlay-$edge-target-missing"
    exit 82
  fi
  read -r cx cy target_w target_h x1 y1 x2 y2 <<< "$coords"
  if [ "$target_w" -lt 40 ] || [ "$target_h" -lt 20 ]; then
    echo "Overlay hit target is undersized: ${target_w}x${target_h}px bounds=[$x1,$y1][$x2,$y2]."
    collect_capture_diagnostics "overlay-$edge-target-undersized"
    exit 83
  fi
  if [ "$edge" = "end" ]; then
    timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorTouchTest:I > emulator-screenshots/overlay-end-target-logcat.txt 2>&1 || true
    if ! grep -Fq "OVERLAY_EDGE_TARGET edge=end" emulator-screenshots/overlay-end-target-logcat.txt ||
       ! grep -F "OVERLAY_EDGE_TARGET edge=end" emulator-screenshots/overlay-end-target-logcat.txt | tail -n 1 | grep -Fq "gestureExclusion=true"; then
      echo "The trailing trim handle did not register its Android gesture-navigation exclusion."
      collect_capture_diagnostics overlay-end-gesture-exclusion-missing
      exit 87
    fi
  fi
  delta=42
  if [ "$direction" = "left" ]; then delta=-42; fi
  echo "Dragging $description from ($cx,$cy) by ${delta}px; hit area=${target_w}x${target_h}px."
  timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$cx" "$cy" "$((cx + delta))" "$cy" 500
  sleep 1
  if ! dump_hierarchy "$after_xml" "emulator-screenshots/$after_xml"; then
    echo "Could not inspect the timeline after overlay trim."
    collect_capture_diagnostics "overlay-$edge-after-hierarchy-failed"
    exit 84
  fi
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorTouchTest:I > "emulator-screenshots/overlay-$edge-touch-logcat.txt" 2>&1 || true
  marker="$(grep -F "OVERLAY_EDGE_TRIM kind=image edge=$edge " "emulator-screenshots/overlay-$edge-touch-logcat.txt" | tail -n 1 || true)"
  if [ -z "$marker" ]; then
    echo "The overlay trim gesture did not reach its handler."
    collect_capture_diagnostics "overlay-$edge-gesture-not-received"
    exit 85
  fi
  python3 - "$marker" "$edge" <<PY
import re
import sys
line, edge = sys.argv[1:]
fields = dict(re.findall(r"([A-Za-z]+)=([^ ]+)", line))
required = ["beforeStartMs", "beforeDurationMs", "afterStartMs", "afterDurationMs"]
missing = [key for key in required if key not in fields]
if missing:
    raise SystemExit("Overlay trim event was incomplete: " + ", ".join(missing) + " :: " + line)
before_start = int(fields["beforeStartMs"])
before_duration = int(fields["beforeDurationMs"])
after_start = int(fields["afterStartMs"])
after_duration = int(fields["afterDurationMs"])
if edge == "start" and not (after_start > before_start and after_duration < before_duration):
    raise SystemExit("Overlay start edge did not trim the start: " + line)
if edge == "end" and not after_duration < before_duration:
    raise SystemExit("Overlay end edge did not shorten duration: " + line)
print(f"PASS: overlay {edge} edge changed timing independently ({before_start}ms/{before_duration}ms -> {after_start}ms/{after_duration}ms)")
PY
}

run_overlay_edge_smoke start "Katman başlangıcını kırp" right
run_overlay_edge_smoke end "Katman bitişini kırp" left

# Each direct trim gesture opens its selected track's contextual tools.
# Return to the global dock after audio and overlay trimming using a fresh hierarchy.
if ! dump_hierarchy editor-layer-tools-final.xml emulator-screenshots/editor-layer-tools-final.xml; then
  echo "Could not inspect the final audio contextual dock before returning to the main dock."
  collect_capture_diagnostics layer-tools-final-hierarchy-unavailable
  exit 77
fi
layer_main_back_coords="$(find_bottom_tool emulator-screenshots/editor-layer-tools-final.xml "Ana araçlara dön" || true)"
if [ -z "$layer_main_back_coords" ]; then
  echo "The final audio tool strip did not expose its return-to-main-dock control."
  collect_capture_diagnostics layer-tools-main-back-missing
  exit 78
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $layer_main_back_coords
sleep 1
if ! dump_hierarchy editor-main-dock-after-audio.xml emulator-screenshots/editor-main-dock-after-audio.xml; then
  echo "Could not verify that the global editor dock returned after the audio trim tests."
  collect_capture_diagnostics audio-tools-main-dock-restore-failed
  exit 79
fi
if ! grep -q "Proje" emulator-screenshots/editor-main-dock-after-audio.xml; then
  echo "The global Proje tab did not return after closing the audio contextual tools."
  collect_capture_diagnostics audio-tools-main-dock-proje-missing
  exit 80
fi
echo "PASS: returned from the audio contextual strip to the global editor dock."

# The panel geometry log was snapshotted immediately after its workflow. Do not
# reread logcat here: later test phases intentionally clear its ring buffer.
python3 - emulator-screenshots/editor-panel-layout-logcat.txt <<'PY'
import re
import sys

states = {}
pattern = re.compile(
    r"EDITOR_PANEL_GEOMETRY state=([^ ]+) "
    r"canvas=(\d+x\d+@\d+,\d+) "
    r"timeline=(\d+x\d+@\d+,\d+) "
    r"panel=(\d+x\d+@\d+,\d+) "
    r"root=(\d+x\d+@\d+,\d+)"
)
for line in open(sys.argv[1], errors="replace"):
    match = pattern.search(line)
    if match:
        states[match.group(1)] = (
            match.group(2), match.group(3), match.group(4), match.group(5)
        )

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
baseline_preview, baseline_timeline, _, baseline_root = states["before-open:clip-tools"]
def rect(value):
    size, origin = value.split("@")
    width, height = map(int, size.split("x"))
    left, top = map(int, origin.split(","))
    return left, top, width, height

base_preview = rect(baseline_preview)
base_timeline = rect(baseline_timeline)
for state in required[1:]:
    preview_value, timeline_value, panel_value, root_value = states[state]
    preview = rect(preview_value)
    timeline = rect(timeline_value)
    panel = rect(panel_value)
    root = rect(root_value)
    if preview[2:] != base_preview[2:]:
        raise SystemExit(
            f"Project canvas dimensions changed during {state}: "
            f"baseline={base_preview[2:]}, observed={preview[2:]}. "
            "The project canvas width and height must remain identical."
        )
    if state in ("open:clip", "open:clipVolume"):
        if timeline[0] != base_timeline[0] or timeline[2] != base_timeline[2]:
            raise SystemExit(
                f"Timeline width/position changed while opening {state}: "
                f"baseline={base_timeline}, observed={timeline}."
            )
        # Vertical re-centering is permitted: the preview canvas dimensions are
        # invariant, while its surrounding stage can shift the timeline on screen.
        # What matters is that the timeline is compacted, does not overlap the
        # detail controls, and both remain inside the editor's measured root.
        if timeline[3] >= base_timeline[3]:
            raise SystemExit(
                f"Detail tools did not reduce the timeline viewport height during {state}: "
                f"baseline={base_timeline}, observed={timeline}."
            )
        if panel[2] != timeline[2] or panel[3] <= 0:
            raise SystemExit(
                f"Detail panel is not fully measured/visible during {state}: panel={panel}."
            )
        timeline_bottom = timeline[1] + timeline[3]
        panel_bottom = panel[1] + panel[3]
        root_bottom = root[1] + root[3]
        if timeline_bottom > panel[1] or panel_bottom > root_bottom:
            raise SystemExit(
                f"Timeline/detail panel overlap or extend beyond editor viewport during {state}: "
                f"timeline={timeline}, panel={panel}, root={root}."
            )
    elif timeline != base_timeline:
        raise SystemExit(
            f"Timeline did not restore after closing/switching {state}: "
            f"baseline={base_timeline}, observed={timeline}."
        )
print("PASS: project canvas dimensions remain pixel-identical; detail tools reduce the timeline viewport, remain below it, and fit inside the editor root. Vertical stage re-centering is allowed.")
PY

echo "Step 8b/12: open the Proje panel and prove the MP4 button receives a real tap"
proje_coords="$(find_bottom_tool emulator-screenshots/editor-main-dock-after-audio.xml "Proje" || true)"
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
# The detail panel hides the global dock. Return through its own visible,
# sticky action instead of tapping the old dock coordinate underneath the panel.
return_coords="$(find_bottom_tool emulator-screenshots/editor-export-panel.xml "Araçlara dön" || true)"
if [ -z "$return_coords" ]; then
  echo "The export panel did not expose its reachable Araçlara dön action."
  collect_capture_diagnostics export-panel-return-action-missing
  exit 41
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $return_coords
sleep 1
dump_hierarchy editor-export-closed.xml emulator-screenshots/editor-export-closed.xml || true
if ! grep -q "Metin" emulator-screenshots/editor-export-closed.xml; then
  echo "The export panel return action did not restore the global editor dock."
  collect_capture_diagnostics export-panel-return-did-not-restore-dock
  exit 42
fi
echo "PASS: visible MP4 button tap reached the export handler, started Transformer, saved an MP4 output, and the panel return action restored the global dock."

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
# Measure the pinned return button while the text panel is first opened. Its
# sibling-header position is independent of the scrolling settings/IME.
text_panel_back="$(find_bottom_tool emulator-screenshots/editor-text-panel.xml "Araçlara dön" || true)"
if [ -z "$text_panel_back" ]; then
  echo "The text panel did not expose its pinned return control when opened."
  collect_capture_diagnostics text-canvas-panel-return-missing-on-open
  exit 52
fi
new_text_coords="$(find_bottom_tool emulator-screenshots/editor-text-panel.xml "＋ Yeni" || true)"
if [ -z "$new_text_coords" ]; then
  echo "The Metin panel did not expose its Yeni text-layer action."
  collect_capture_diagnostics text-canvas-add-action-missing
  exit 51
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $new_text_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 shell input text "Canvas"
# A deliberate pause longer than the former 650 ms debounce must not split
# one focused text-edit session into two history entries.
sleep 1
timeout -k 2s 8s adb -s emulator-5554 shell input text "Smoke"
sleep 1

# Keep the IME open long enough to verify the real keyboard layout. The old
# smoke path dismissed it immediately, so keyboard-induced preview/timeline
# compression could never be observed.
if ! take_screen emulator-screenshots/editor-text-keyboard-open.png; then
  collect_capture_diagnostics text-keyboard-open-capture-failure
  exit 6
fi
timeout -k 2s 8s adb -s emulator-5554 shell dumpsys input_method \
  > emulator-screenshots/text-keyboard-input-method.txt 2>&1 || true
if ! grep -Eiq "mInputShown=true|isInputViewShown=true|mShowRequested=true" \
    emulator-screenshots/text-keyboard-input-method.txt; then
  echo "Text input did not keep the Android keyboard visibly open for layout verification."
  collect_capture_diagnostics text-keyboard-not-visible
  exit 90
fi
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s PreviewCanvasTest:I \
  > emulator-screenshots/text-keyboard-preview-canvas-logcat.txt 2>&1 || true
python3 - emulator-screenshots/text-keyboard-preview-canvas-logcat.txt <<'PY'
import re
import sys
lines = open(sys.argv[1], errors="replace").read().splitlines()
matches = [line for line in lines if "PREVIEW_CANVAS_BOUNDS" in line]
if not matches:
    raise SystemExit("No measured project-canvas bounds were logged while the keyboard was open.")
match = re.search(r"canvas=(\d+)x(\d+).*aspect=([0-9.]+)", matches[-1])
if not match:
    raise SystemExit("Malformed project-canvas measurement while the keyboard was open: " + matches[-1])
width, height = map(int, match.groups()[:2])
aspect = float(match.group(3))
if width <= 0 or height <= 0 or abs(width / height - 9 / 16) > 0.002 or abs(aspect - 9 / 16) > 0.002:
    raise SystemExit(
        f"Keyboard changed the 9:16 project canvas geometry: canvas={width}x{height}, aspect={aspect}"
    )

# The log captured at editor startup can contain transient pre-layout sizes
# (500x889) before the workspace settles. Compare against the actual measured
# project canvas from the text panel with the keyboard still closed.
import xml.etree.ElementTree as ET
baseline_root = ET.parse("emulator-screenshots/editor-text-panel.xml").getroot()
baseline_bounds = []
for node in baseline_root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje tuvali":
        continue
    baseline_match = re.fullmatch(
        r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",
        node.attrib.get("bounds", ""),
    )
    if baseline_match:
        baseline_bounds.append(tuple(map(int, baseline_match.groups())))
if len(baseline_bounds) != 1:
    raise SystemExit(
        "The keyboard-closed text-panel hierarchy did not expose exactly one measured project canvas."
    )
baseline_width = baseline_bounds[0][2] - baseline_bounds[0][0]
baseline_height = baseline_bounds[0][3] - baseline_bounds[0][1]
if (width, height) != (baseline_width, baseline_height):
    raise SystemExit(
        "Keyboard changed the project preview size; the canvas must remain pixel-identical: "
        f"normal={baseline_width}x{baseline_height}, keyboard={width}x{height}."
    )

print(
    "PASS: keyboard remained visible and the project preview kept its exact "
    f"normal dimensions ({width}x{height}); no shrinking or scaling is allowed."
)
PY
echo "Captured the text editor with the IME open; now dismissing it for the remaining touch tests."
timeout -k 2s 8s adb -s emulator-5554 shell input keyevent 4
sleep 1
# Keep the canvas metrics for diagnosis, but the pixel assertion reads the
# exact project-canvas rectangle from this fresh hierarchy snapshot.
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s PreviewCanvasTest:I \
  > emulator-screenshots/editor-text-selected-canvas-logcat.txt 2>&1 || true
if ! take_screen emulator-screenshots/editor-text-selected.png; then
  collect_capture_diagnostics text-canvas-selection-capture-failure
  exit 6
fi
# Snapshot the current project-canvas bounds alongside each selection screenshot.
# The IME and editor-panel transitions change the canvas rectangle independently.
detect_text_selection_bounds() {
  python3 - "$1" "$2" "$3" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
from PIL import Image

image_path, log_path, hierarchy_path = sys.argv[1:4]
root = ET.parse(hierarchy_path).getroot()
root_node = root.find("node")
root_bounds = re.fullmatch(
    r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",
    root_node.attrib.get("bounds", "") if root_node is not None else "",
)
if not root_bounds:
    raise SystemExit("Could not read the Android root-window bounds for screenshot scaling.")
window_left, window_top, window_right, window_bottom = map(int, root_bounds.groups())
window_width, window_height = window_right - window_left, window_bottom - window_top
if window_width <= 0 or window_height <= 0:
    raise SystemExit("Invalid Android root-window dimensions in hierarchy.")

snapshot = None
for line in open(log_path, errors="replace"):
    match = re.search(
        r"PREVIEW_CANVAS_SNAPSHOT reason=text-panel-close canvas=(\d+)x(\d+) origin=(-?\d+),(-?\d+)",
        line,
    )
    if match:
        snapshot = tuple(map(int, match.groups()))
if snapshot is not None:
    canvas_w, canvas_h, origin_x, origin_y = snapshot
    canvas_left, canvas_top = origin_x, origin_y
else:
    canvas_nodes = []
    for node in root.iter("node"):
        if node.attrib.get("content-desc", "").strip() != "Proje tuvali":
            continue
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            canvas_nodes.append(tuple(map(int, match.groups())))
    if len(canvas_nodes) != 1:
        raise SystemExit(f"Expected exactly one measurable 'Proje tuvali' node, found {len(canvas_nodes)}")
    canvas_left, canvas_top, canvas_right, canvas_bottom = canvas_nodes[0]
    canvas_w, canvas_h = canvas_right - canvas_left, canvas_bottom - canvas_top

if canvas_w <= 0 or canvas_h <= 0:
    raise SystemExit("Invalid project-canvas dimensions.")
image = Image.open(image_path).convert("RGB")
scale_x = image.width / window_width
scale_y = image.height / window_height
left = round((canvas_left - window_left) * scale_x)
top = round((canvas_top - window_top) * scale_y)
right = round((canvas_left + canvas_w - window_left) * scale_x) - 1
bottom = round((canvas_top + canvas_h - window_top) * scale_y) - 1
if left < 0 or top < 0 or right >= image.width or bottom >= image.height:
    raise SystemExit(
        f"Project canvas bounds lie outside screenshot: "
        f"({left},{top})-({right},{bottom}) in {image.width}x{image.height}"
    )

def is_white(pixel):
    red, green, blue = pixel
    return min(red, green, blue) >= 220 and max(red, green, blue) - min(red, green, blue) <= 35

rows = []
minimum_width = max(80, int(canvas_w * 0.60 * scale_x))
for y in range(top, bottom + 1):
    xs = [x for x in range(left, right + 1) if is_white(image.getpixel((x, y)))]
    if not xs:
        continue
    runs = []
    run_start = previous = xs[0]
    for x in xs[1:]:
        if x > previous + 2:
            runs.append((run_start, previous))
            run_start = x
        previous = x
    runs.append((run_start, previous))
    for x1, x2 in runs:
        if x2 - x1 + 1 >= minimum_width:
            rows.append((y, x1, x2))

matches = []
for i, (top_y, x1, x2) in enumerate(rows):
    for bottom_y, bx1, bx2 in rows[i + 1:]:
        height = bottom_y - top_y + 1
        if height < 35 or abs(x1 - bx1) > 8 or abs(x2 - bx2) > 8:
            continue
        width = x2 - x1 + 1
        if abs(width - (bx2 - bx1 + 1)) > 10:
            continue
        left_strength = max(
            sum(is_white(image.getpixel((x, y))) for y in range(top_y + 2, bottom_y - 1))
            for x in range(max(left, x1 - 5), min(right, x1 + 5) + 1)
        )
        right_strength = max(
            sum(is_white(image.getpixel((x, y))) for y in range(top_y + 2, bottom_y - 1))
            for x in range(max(left, x2 - 5), min(right, x2 + 5) + 1)
        )
        if left_strength < height * 0.70 or right_strength < height * 0.70:
            continue
        interior_white = sum(
            1
            for y in range(top_y + 4, bottom_y - 3)
            for x in range(x1 + 4, x2 - 3)
            if is_white(image.getpixel((x, y)))
        )
        if interior_white < 40:
            continue
        matches.append((width * height, x1, top_y, x2, bottom_y))

if not matches:
    raise SystemExit("A complete white text-selection rectangle with visible text was not found inside the project canvas.")
_, x1, y1, x2, y2 = max(matches)
print(x1, y1, x2, y2, (x1 + x2) // 2, (y1 + y2) // 2)
PY
}

if ! text_selection_bounds="$(detect_text_selection_bounds emulator-screenshots/editor-text-selected.png emulator-screenshots/editor-text-selected-canvas-logcat.txt emulator-screenshots/editor-text-panel.xml)"; then
  echo "The selected text did not render a measurable white selection frame inside the project canvas."
  collect_capture_diagnostics text-canvas-selection-frame-missing
  exit 60
fi
read -r text_frame_left text_frame_top text_frame_right text_frame_bottom text_before_x text_before_y <<< "$text_selection_bounds"
echo "PASS: new text and its white selection frame are visible inside the measured project canvas at ($text_before_x,$text_before_y)."

# The text detail panel normally opens with direct editing already enabled.
# Only toggle it on if the accessibility state says it is currently closed.
text_edit_toggle_info="$(python3 - emulator-screenshots/editor-text-panel.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    label = node.attrib.get("content-desc", "")
    if "Önizleme üzerindeki yazıyı sürükleme" not in label:
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        state = "kapalı" if "kapalı" in label.casefold() else "açık"
        print((x1 + x2) // 2, (y1 + y2) // 2, state)
        break
PY
)"
if [ -z "$text_edit_toggle_info" ]; then
  echo "The text panel did not expose its shared preview-gesture toggle."
  collect_capture_diagnostics text-canvas-edit-toggle-missing
  exit 61
fi
read -r text_toggle_x text_toggle_y text_toggle_state <<< "$text_edit_toggle_info"
text_edit_toggle_changed=false
if [ "$text_toggle_state" = "kapalı" ]; then
  timeout -k 2s 8s adb -s emulator-5554 shell input tap "$text_toggle_x" "$text_toggle_y"
  sleep 1
  text_edit_toggle_changed=true
fi

# The default text fixture is bottom-centred and nearly fills the canvas
# width. Drag right/up so the full selection outline remains inside the project
# canvas; a right/down swipe moves the box beyond the canvas edge and clips the
# white outline, which made the pixel assertion mistake a clipped frame for a
# disappeared selection. The diagonal remains above Android's measured slop.
text_drag_to_x="$text_before_x"
text_drag_to_y=$((text_before_y - 28))
timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$text_before_x" "$text_before_y" "$text_drag_to_x" "$text_drag_to_y" 500
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s PreviewCanvasTest:I \
  > emulator-screenshots/editor-text-after-drag-canvas-logcat.txt 2>&1 || true
if ! take_screen emulator-screenshots/editor-text-after-drag.png; then
  collect_capture_diagnostics text-canvas-drag-capture-failure
  exit 6
fi
if ! text_after_bounds="$(detect_text_selection_bounds emulator-screenshots/editor-text-after-drag.png emulator-screenshots/editor-text-after-drag-canvas-logcat.txt emulator-screenshots/editor-text-panel.xml)"; then
  echo "The selected text frame was not fully visible after the in-canvas direct drag."
  collect_capture_diagnostics text-canvas-drag-selection-frame-missing
  exit 62
fi
read -r text_after_left text_after_top text_after_right text_after_bottom text_after_x text_after_y <<< "$text_after_bounds"
text_drag_dx=$((text_after_x - text_before_x))
text_drag_dy=$((text_after_y - text_before_y))
if [ "$text_drag_dx" -lt -6 ] || [ "$text_drag_dx" -gt 6 ] || [ "$text_drag_dy" -gt -12 ]; then
  echo "Direct vertical text drag did not move the rendered selection frame as expected: before=($text_before_x,$text_before_y), after=($text_after_x,$text_after_y), delta=($text_drag_dx,$text_drag_dy)."
  collect_capture_diagnostics text-canvas-drag-did-not-move
  exit 62
fi
echo "PASS: vertical in-canvas text drag moved the rendered selection frame from ($text_before_x,$text_before_y) to ($text_after_x,$text_after_y) without shifting it beyond the canvas edges."

echo "Step 8b: verify project-wide Undo/Redo restores a real text-canvas drag"
dump_hierarchy editor-text-history-before-undo.xml emulator-screenshots/editor-text-history-before-undo.xml || {
  echo "Could not inspect the editor controls before the project-history test."
  collect_capture_diagnostics project-history-undo-controls-missing
  exit 63
}
history_undo_coords="$(python3 - emulator-screenshots/editor-text-history-before-undo.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje işlemini geri al":
        continue
    if node.attrib.get("enabled", "false").lower() != "true":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        raise SystemExit(0)
raise SystemExit("Enabled project Undo button was not found.")
PY
)"
if [ -z "$history_undo_coords" ]; then
  echo "Project Undo was not enabled after the text drag."
  collect_capture_diagnostics project-history-undo-not-enabled
  exit 63
fi
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $history_undo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-undo-logcat.txt 2>&1 || true
if ! grep -Eq "PROJECT_HISTORY_APPLIED action=undo .*beforeTexts=1 afterTexts=1" emulator-screenshots/project-history-undo-logcat.txt; then
  echo "Project Undo did not restore the previous text-layer state."
  cat emulator-screenshots/project-history-undo-logcat.txt || true
  collect_capture_diagnostics project-history-undo-not-applied
  exit 63
fi
if ! take_screen emulator-screenshots/editor-text-after-undo.png; then
  collect_capture_diagnostics project-history-undo-capture-failure
  exit 6
fi
text_undo_bounds="$(detect_text_selection_bounds emulator-screenshots/editor-text-after-undo.png emulator-screenshots/editor-text-after-drag-canvas-logcat.txt emulator-screenshots/editor-text-panel.xml)" || {
  echo "The text selection frame disappeared after Undo."
  collect_capture_diagnostics project-history-undo-selection-missing
  exit 63
}
read -r _ _ _ _ text_undo_x text_undo_y <<< "$text_undo_bounds"
if [ "$((text_undo_y - text_before_y))" -lt -8 ] || [ "$((text_undo_y - text_before_y))" -gt 8 ]; then
  echo "Undo did not restore the text's pre-drag vertical position: before=$text_before_y afterUndo=$text_undo_y."
  collect_capture_diagnostics project-history-undo-position-wrong
  exit 63
fi
echo "PASS: project Undo restored the selected text to its measured pre-drag position ($text_undo_x,$text_undo_y)."

dump_hierarchy editor-text-history-before-redo.xml emulator-screenshots/editor-text-history-before-redo.xml || {
  echo "Could not inspect the editor controls before the project Redo test."
  collect_capture_diagnostics project-history-redo-controls-missing
  exit 64
}
history_redo_coords="$(python3 - emulator-screenshots/editor-text-history-before-redo.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje işlemini yinele":
        continue
    if node.attrib.get("enabled", "false").lower() != "true":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        raise SystemExit(0)
raise SystemExit("Enabled project Redo button was not found.")
PY
)"
if [ -z "$history_redo_coords" ]; then
  echo "Project Redo was not enabled after Undo."
  collect_capture_diagnostics project-history-redo-not-enabled
  exit 64
fi
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $history_redo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-redo-logcat.txt 2>&1 || true
if ! grep -Eq "PROJECT_HISTORY_APPLIED action=redo .*beforeTexts=1 afterTexts=1" emulator-screenshots/project-history-redo-logcat.txt; then
  echo "Project Redo did not reapply the text-layer state."
  cat emulator-screenshots/project-history-redo-logcat.txt || true
  collect_capture_diagnostics project-history-redo-not-applied
  exit 64
fi
if ! take_screen emulator-screenshots/editor-text-after-redo.png; then
  collect_capture_diagnostics project-history-redo-capture-failure
  exit 6
fi
text_redo_bounds="$(detect_text_selection_bounds emulator-screenshots/editor-text-after-redo.png emulator-screenshots/editor-text-after-drag-canvas-logcat.txt emulator-screenshots/editor-text-panel.xml)" || {
  echo "The text selection frame disappeared after Redo."
  collect_capture_diagnostics project-history-redo-selection-missing
  exit 64
}
read -r _ _ _ _ text_redo_x text_redo_y <<< "$text_redo_bounds"
if [ "$((text_redo_y - text_after_y))" -lt -8 ] || [ "$((text_redo_y - text_after_y))" -gt 8 ]; then
  echo "Redo did not restore the text's post-drag vertical position: afterDrag=$text_after_y afterRedo=$text_redo_y."
  collect_capture_diagnostics project-history-redo-position-wrong
  exit 64
fi
echo "PASS: project Redo restored the selected text to its measured post-drag position ($text_redo_x,$text_redo_y)."

echo "Step 8c: verify a sentence with a long typing pause is one Undo/Redo step"
# First undo the restored drag. The text remains complete.
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $history_undo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-undo-drag-before-text.txt 2>&1 || true
if ! grep -Eq "PROJECT_HISTORY_APPLIED action=undo .*beforeTextChars=10 afterTextChars=10" emulator-screenshots/project-history-undo-drag-before-text.txt; then
  echo "Undoing the text drag unexpectedly changed the text contents."
  cat emulator-screenshots/project-history-undo-drag-before-text.txt || true
  collect_capture_diagnostics project-history-text-before-typing-undo-wrong
  exit 65
fi
# The next Undo must remove both halves of CanvasSmoke, despite the one-second pause.
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $history_undo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-undo-whole-text.txt 2>&1 || true
if ! grep -Eq "PROJECT_HISTORY_APPLIED action=undo .*beforeTextChars=10 afterTextChars=0" emulator-screenshots/project-history-undo-whole-text.txt; then
  echo "One Undo did not remove the entire CanvasSmoke text session; typing may have split across pause."
  cat emulator-screenshots/project-history-undo-whole-text.txt || true
  collect_capture_diagnostics project-history-text-was-split
  exit 65
fi
echo "PASS: one Undo removed all 10 characters across a one-second typing pause."
# One Redo must restore the complete sentence in a single step.
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $history_redo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-redo-whole-text.txt 2>&1 || true
if ! grep -Eq "PROJECT_HISTORY_APPLIED action=redo .*beforeTextChars=0 afterTextChars=10" emulator-screenshots/project-history-redo-whole-text.txt; then
  echo "One Redo did not restore the complete text session."
  cat emulator-screenshots/project-history-redo-whole-text.txt || true
  collect_capture_diagnostics project-history-text-redo-incomplete
  exit 65
fi
# The second Redo restores the separate canvas-drag action.
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $history_redo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-redo-drag-after-text.txt 2>&1 || true
if ! grep -Eq "PROJECT_HISTORY_APPLIED action=redo .*beforeTextChars=10 afterTextChars=10" emulator-screenshots/project-history-redo-drag-after-text.txt; then
  echo "Redoing the text drag changed the text content unexpectedly."
  cat emulator-screenshots/project-history-redo-drag-after-text.txt || true
  collect_capture_diagnostics project-history-text-after-redo-wrong
  exit 65
fi
if ! take_screen emulator-screenshots/editor-text-after-grouped-redo.png; then
  collect_capture_diagnostics project-history-grouped-redo-capture-failure
  exit 6
fi
text_group_redo_bounds="$(detect_text_selection_bounds emulator-screenshots/editor-text-after-grouped-redo.png emulator-screenshots/editor-text-after-drag-canvas-logcat.txt emulator-screenshots/editor-text-panel.xml)" || {
  echo "The selected text frame disappeared after grouped text Undo/Redo."
  collect_capture_diagnostics project-history-grouped-redo-selection-missing
  exit 65
}
read -r _ _ _ _ text_group_redo_x text_group_redo_y <<< "$text_group_redo_bounds"
if [ "$((text_group_redo_y - text_after_y))" -lt -8 ] || [ "$((text_group_redo_y - text_after_y))" -gt 8 ]; then
  echo "Grouped Undo/Redo did not return the text to its post-drag position."
  collect_capture_diagnostics project-history-grouped-redo-position-wrong
  exit 65
fi
echo "PASS: one Redo restored the complete text session and the next Redo restored its separate drag."

if [ "$text_edit_toggle_changed" = true ]; then
  timeout -k 2s 8s adb -s emulator-5554 shell input tap "$text_toggle_x" "$text_toggle_y"
  sleep 1
fi

assert_preview_canvas_unchanged_from_snapshot() {
  python3 - "$1" "$2" "$3" "$4" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
from PIL import Image

baseline_path, snapshot_path, image_path, label = sys.argv[1:5]
baseline_root = ET.parse(baseline_path).getroot()
canvas_nodes = []
for node in baseline_root.iter("node"):
    if node.attrib.get("content-desc", "").strip() != "Proje tuvali":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match:
        canvas_nodes.append(tuple(map(int, match.groups())))
if len(canvas_nodes) != 1:
    raise SystemExit(
        f"{baseline_path}: expected exactly one measured baseline project canvas; found {len(canvas_nodes)}"
    )
baseline = canvas_nodes[0]
baseline_width, baseline_height = baseline[2] - baseline[0], baseline[3] - baseline[1]

snapshots = []
for line in open(snapshot_path, errors="replace"):
    match = re.search(
        r"PREVIEW_CANVAS_SNAPSHOT reason=text-panel-close canvas=(\d+)x(\d+) origin=(-?\d+),(-?\d+)",
        line,
    )
    if match:
        snapshots.append(tuple(map(int, match.groups())))
if not snapshots:
    raise SystemExit(f"{label}: app did not emit its post-layout project-canvas snapshot.")
canvas_width, canvas_height, origin_x, origin_y = snapshots[-1]
if (canvas_width, canvas_height) != (baseline_width, baseline_height):
    raise SystemExit(
        f"{label}: project canvas changed size: baseline={baseline_width}x{baseline_height}, "
        f"after close={canvas_width}x{canvas_height}"
    )

root_node = baseline_root.find("node")
root_match = re.fullmatch(
    r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",
    root_node.attrib.get("bounds", "") if root_node is not None else "",
)
if not root_match:
    raise SystemExit(f"{baseline_path}: could not read Android root-window bounds")
window_left, window_top, window_right, window_bottom = map(int, root_match.groups())
window_width, window_height = window_right - window_left, window_bottom - window_top
if window_width <= 0 or window_height <= 0:
    raise SystemExit(f"{baseline_path}: invalid Android root-window dimensions")

image = Image.open(image_path).convert("RGB")
scale_x, scale_y = image.width / window_width, image.height / window_height
left = round((origin_x - window_left) * scale_x)
top = round((origin_y - window_top) * scale_y)
right = round((origin_x + canvas_width - window_left) * scale_x) - 1
bottom = round((origin_y + canvas_height - window_top) * scale_y) - 1
if left < 0 or top < 0 or right >= image.width or bottom >= image.height or right <= left or bottom <= top:
    raise SystemExit(
        f"{label}: snapshot canvas ({left},{top})-({right},{bottom}) "
        f"falls outside screenshot {image.width}x{image.height}"
    )

green_points = []
for y in range(top, bottom + 1):
    for x in range(left, right + 1):
        red, green, blue = image.getpixel((x, y))
        if green > 150 and red < 120 and blue < 120:
            green_points.append((x, y))
if len(green_points) < 10000:
    raise SystemExit(f"{label}: only {len(green_points)} fixture-green pixels remain inside the project canvas")
xs = [point[0] for point in green_points]
ys = [point[1] for point in green_points]
visible_width, visible_height = max(xs) - min(xs) + 1, max(ys) - min(ys) + 1
if visible_width < (right - left + 1) * 0.90 or visible_height < (bottom - top + 1) * 0.90:
    raise SystemExit(
        f"{label}: fixture preview does not fill the measured canvas; "
        f"pixels={visible_width}x{visible_height}, view={(right-left+1)}x{(bottom-top+1)}"
    )
ratio = visible_width / max(1, visible_height)
if not 0.50 <= ratio <= 0.62:
    raise SystemExit(f"{label}: visible fixture is not portrait-shaped ({visible_width}x{visible_height})")
print(
    f"PASS: {label} kept the project canvas exactly {canvas_width}x{canvas_height}; "
    f"measured screenshot fixture={visible_width}x{visible_height}."
)
PY
}
# The keyboard changed the vertical layout. Use the application's
# measured post-layout bounds for the sticky sibling return control; do not
# traverse the large timeline accessibility hierarchy again.
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorTouchTest:I \
  > emulator-screenshots/text-panel-return-bounds-logcat.txt 2>&1 || true
text_panel_back="$(python3 - emulator-screenshots/text-panel-return-bounds-logcat.txt <<'PY'
import re
import sys

for line in reversed(open(sys.argv[1], errors="replace").read().splitlines()):
    match = re.search(
        r"TEXT_PANEL_RETURN_BOUNDS visible=true x=(\d+) y=(\d+) width=(\d+) height=(\d+)",
        line,
    )
    if not match:
        continue
    x, y, width, height = map(int, match.groups())
    if width > 0 and height > 0:
        print(x + width // 2, y + height // 2)
        raise SystemExit(0)
raise SystemExit("No visible measured text-panel return bounds were logged after keyboard dismissal.")
PY
)"
if [ -z "$text_panel_back" ]; then
  echo "The text-panel return control did not report visible post-keyboard bounds."
  collect_capture_diagnostics text-panel-return-after-keyboard-missing
  exit 56
fi
echo "Tapping the measured text-panel return control at $text_panel_back after text editing."
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $text_panel_back
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorTouchTest:I > emulator-screenshots/text-panel-return-logcat.txt 2>&1 || true
if ! grep -Fq "TEXT_PANEL_RETURN_TAPPED" emulator-screenshots/text-panel-return-logcat.txt ||
   ! grep -Fq "TEXT_PANEL_CLOSED activePanel=none" emulator-screenshots/text-panel-return-logcat.txt; then
  echo "The text detail panel did not confirm its return-to-tools action."
  collect_capture_diagnostics text-canvas-panel-return-not-confirmed
  exit 56
fi
if ! take_screen emulator-screenshots/editor-text-panel-closed.png; then
  collect_capture_diagnostics text-canvas-panel-closed-capture-failure
  exit 6
fi
# Read the actual post-animation FrameLayout bounds from the app. This is
# independent of a second UIAutomator dump, which can time out on this screen.
snapshot_ready=false
for attempt in $(seq 1 10); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s PreviewCanvasTest:I \
    > emulator-screenshots/editor-text-panel-closed-canvas-logcat.txt 2>&1 || true
  if grep -q "PREVIEW_CANVAS_SNAPSHOT reason=text-panel-close" \
      emulator-screenshots/editor-text-panel-closed-canvas-logcat.txt; then
    snapshot_ready=true
    break
  fi
  sleep 1
done
if [ "$snapshot_ready" != true ]; then
  echo "The app did not report the laid-out project canvas after closing the text panel."
  collect_capture_diagnostics text-canvas-panel-closed-snapshot-missing
  exit 53
fi
assert_preview_canvas_unchanged_from_snapshot \
  emulator-screenshots/editor-text-panel.xml \
  emulator-screenshots/editor-text-panel-closed-canvas-logcat.txt \
  emulator-screenshots/editor-text-panel-closed.png "text panel close"
text_preview_bounds="$(detect_text_selection_bounds emulator-screenshots/editor-text-panel-closed.png emulator-screenshots/editor-text-panel-closed-canvas-logcat.txt emulator-screenshots/editor-text-panel.xml)" || {
  echo "The text layer did not retain a complete selection frame after the detail panel closed."
  collect_capture_diagnostics text-canvas-preview-hit-target-missing
  exit 53
}
read -r text_preview_left text_preview_top text_preview_right text_preview_bottom text_preview_x text_preview_y <<< "$text_preview_bounds"
text_preview_center="$text_preview_x $text_preview_y"
if [ -z "$text_preview_center" ]; then
  echo "Could not locate the visible text layer after closing its settings panel."
  collect_capture_diagnostics text-canvas-preview-hit-target-missing
  exit 53
fi
echo "Tapping the remeasured in-canvas text centre at $text_preview_center after confirming the keyboard-free full preview."
timeout -k 2s 8s adb -s emulator-5554 shell input tap $text_preview_center
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s PreviewCanvasTest:I \
  > emulator-screenshots/editor-text-reselected-canvas-logcat.txt 2>&1 || true
if ! take_screen emulator-screenshots/editor-text-reselected.png; then
  collect_capture_diagnostics text-canvas-reselection-capture-failure
  exit 6
fi
# Canvas geometry and screen origin are already measured after the panel-close
# animation; selecting the overlay must not resize or move the project canvas.
assert_preview_canvas_unchanged_from_snapshot \
  emulator-screenshots/editor-text-panel.xml \
  emulator-screenshots/editor-text-panel-closed-canvas-logcat.txt \
  emulator-screenshots/editor-text-reselected.png "text preview reselection"
text_reselected_bounds="$(detect_text_selection_bounds emulator-screenshots/editor-text-reselected.png emulator-screenshots/editor-text-panel-closed-canvas-logcat.txt emulator-screenshots/editor-text-panel.xml)" || {
  echo "Tapping the preview text did not restore a complete canvas selection frame."
  collect_capture_diagnostics text-canvas-tap-selection-frame-missing
  exit 55
}
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorTouchTest:I > emulator-screenshots/text-preview-reselection-logcat.txt 2>&1 || true
if ! grep -Fq "TEXT_TOOLS_DOCK_VISIBLE childCount=3" emulator-screenshots/text-preview-reselection-logcat.txt ||
   ! grep -Fq "Düzenle" emulator-screenshots/text-preview-reselection-logcat.txt ||
   ! grep -Fq "Sil" emulator-screenshots/text-preview-reselection-logcat.txt; then
  echo "Tapping the text did not emit proof that the three-action contextual text dock was rebuilt."
  collect_capture_diagnostics text-canvas-tap-did-not-reopen-tools
  exit 54
fi
echo "PASS: tapping the preview text restored its three-action text dock and selection frame at $text_reselected_bounds."

# UIAutomator can time out on this large panel even after the direct interaction
# has succeeded. The app-side dock marker plus the full-canvas screenshot and
# pixel-verified selection frame are authoritative; hierarchy-based cleanup is
# best-effort so a slow accessibility dump cannot turn a valid editor state red.
if dump_hierarchy editor-text-reselected.xml emulator-screenshots/editor-text-reselected.xml; then
  text_delete_coords="$(find_bottom_tool emulator-screenshots/editor-text-reselected.xml "Sil" || true)"
  if [ -n "$text_delete_coords" ]; then
    timeout -k 2s 8s adb -s emulator-5554 shell input tap $text_delete_coords
    sleep 1
    text_main_back="$(find_bottom_tool emulator-screenshots/editor-text-reselected.xml "Ana araçlara dön" || true)"
    if [ -n "$text_main_back" ]; then
      timeout -k 2s 8s adb -s emulator-5554 shell input tap $text_main_back
      sleep 1
    fi
  fi
else
  echo "UIAutomator cleanup hierarchy unavailable; retaining the verified text layer for the remaining timeline checks."
fi
dump_hierarchy editor-text-cleanup.xml emulator-screenshots/editor-text-cleanup.xml || true

# Resolve the ruler's actual screen Y coordinate once. Clip surfaces now own
# horizontal one-finger drags; ruler swipes remain the timeline-scrub gesture.
ruler_y="$(python3 - emulator-screenshots/editor-window.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
candidates = []
for node in root.iter("node"):
    if node.attrib.get("class") != "android.widget.HorizontalScrollView":
        continue
    if node.attrib.get("scrollable") != "true":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        continue
    left, top, right, bottom = map(int, match.groups())
    width, height = right - left, bottom - top
    if width > 500 and 20 <= height <= 100 and 0 < top < 1200:
        candidates.append((top, bottom, (top + bottom) // 2, left, right, height))
if not candidates:
    raise SystemExit("Could not locate the horizontally scrollable timeline ruler in the editor hierarchy.")
# The ruler is the first/uppermost short scroll row; audio/text lanes follow below it.
top, bottom, y, left, right, height = min(candidates, key=lambda item: (item[0], item[1]))
print(y)
PY
)"
if [ -z "$ruler_y" ]; then
  echo "Could not locate the timeline ruler touch row for scrubbing."
  collect_capture_diagnostics timeline-ruler-scrub-target-missing
  exit 98
fi

echo "Step 9/12: align a thumbnail cell with the playhead and compare the rendered pixels"
# Use app-measured Android View bounds. UIAutomator's full accessibility-tree
# dump becomes unreliable after repeated panel/keyboard operations.
echo "Using startup-captured Android ViewConfiguration touch slop: ${touch_slop_px}px"
# ADB-injected HorizontalScrollView gestures on API 35 showed a 3px dead zone
# beyond ViewConfiguration’s measured touch slop (21px): 24px swipes could
# leave scrollX unchanged, while 27px moved it by 3px. Include that measured
# input-dispatch margin so the final 2–4px alignment correction is not a no-op.
alignment_gesture_slop_px="$((touch_slop_px + 3))"

# Timeline geometry can settle after the first activity sync callback on slower
# API 35 emulators. Wait briefly for a real app-measured snapshot; do not turn a
# transient log timing race into an immediate red run.
measurement_ready=false
for snapshot_attempt in $(seq 1 12); do
  timeout -k 2s 10s adb -s emulator-5554 logcat -d -v threadtime \
    -s TimelineAlignmentTest:I TimelineSyncTest:I TimelineFrameSyncTest:I TimelineThumbnail:I \
    > emulator-screenshots/thumbnail-alignment-measurements.txt 2>&1 || true
  if alignment="$(python3 .github/scripts/assert-thumbnail-alignment.py prepare \
      emulator-screenshots/thumbnail-alignment-measurements.txt \
      2> emulator-screenshots/thumbnail-alignment-prepare-error.txt)"; then
    measurement_ready=true
    break
  fi
  echo "Waiting for app-measured timeline geometry (attempt $snapshot_attempt/12)."
  sleep 1
done
if [ "$measurement_ready" != true ]; then
  cat emulator-screenshots/thumbnail-alignment-prepare-error.txt || true
  collect_capture_diagnostics timeline-alignment-snapshot-missing
  exit 23
fi

thumbnail_aligned=false
for attempt in $(seq 1 12); do
  timeout -k 2s 10s adb -s emulator-5554 logcat -d -v threadtime \
    -s TimelineAlignmentTest:I TimelineSyncTest:I TimelineFrameSyncTest:I TimelineThumbnail:I \
    > emulator-screenshots/thumbnail-alignment-measurements.txt 2>&1 || true
  alignment="$(python3 .github/scripts/assert-thumbnail-alignment.py prepare emulator-screenshots/thumbnail-alignment-measurements.txt)"
  read -r thumb_center_x thumb_center_y play_center_x thumb_sample_ms <<< "$alignment"
  delta_x="$((play_center_x - thumb_center_x))"
  absolute_delta="$((delta_x < 0 ? -delta_x : delta_x))"
  echo "Alignment attempt $attempt: sample=${thumb_sample_ms}ms center=${thumb_center_x}px playhead=${play_center_x}px delta=${delta_x}px"
  if [ "$absolute_delta" -le 2 ]; then
    thumbnail_aligned=true
    break
  fi
  gesture_delta="$((delta_x + (delta_x < 0 ? -alignment_gesture_slop_px : alignment_gesture_slop_px)))"
  # Start outside the fixed playhead overlay hit region. The centre guide is
  # intentionally visual-only, but keeping the gesture origin clear of it also
  # ensures the HorizontalScrollView receives the horizontal correction drag.
  if [ "$gesture_delta" -lt 0 ]; then
    gesture_start_x="$((play_center_x + 64))"
  else
    gesture_start_x="$((play_center_x - 64))"
  fi
  gesture_end_x="$((gesture_start_x + gesture_delta))"
  echo "  ruler correction swipe: x=${gesture_start_x}->${gesture_end_x} y=${ruler_y}"
  timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$gesture_start_x" "$ruler_y" "$gesture_end_x" "$ruler_y" 400
  sleep 1
done
if [ "$thumbnail_aligned" != true ]; then
  echo "Could not center any filmstrip thumbnail under the fixed playhead after 12 measured drags."
  collect_capture_diagnostics thumbnail-alignment-not-centered
  exit 24
fi
sleep 1
if ! take_screen emulator-screenshots/editor-frame-aligned.png; then
  collect_capture_diagnostics thumbnail-alignment-capture-failure
  exit 6
fi
timeout -k 2s 10s adb -s emulator-5554 logcat -d -v threadtime \
  -s TimelineAlignmentTest:I TimelineSyncTest:I TimelineFrameSyncTest:I TimelineThumbnail:I \
  > emulator-screenshots/thumbnail-alignment-logcat.txt 2>&1
cat emulator-screenshots/video-replacement-logcat.txt \
  emulator-screenshots/thumbnail-alignment-logcat.txt \
  > emulator-screenshots/thumbnail-alignment-combined-logcat.txt
python3 .github/scripts/assert-thumbnail-alignment.py verify \
  emulator-screenshots/thumbnail-alignment-combined-logcat.txt \
  emulator-screenshots/editor-frame-aligned.png

echo "Step 10/12: swipe directly on the filmstrip and verify every timeline lane stays synchronized"
clip_bounds="$(python3 - emulator-screenshots/thumbnail-alignment-logcat.txt <<'PY'
import re
import sys
latest = None
for line in open(sys.argv[1], errors="replace"):
    match = re.search(r"TIMELINE_ALIGNMENT_SNAPSHOT .*?clipBounds=(none|\d+,\d+,\d+,\d+)", line)
    if match and match.group(1) != "none":
        latest = match.group(1).replace(",", " ")
if latest:
    print(latest)
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
# gesture area on density-scaled emulator configurations. Gesture directly on
# the thumbnails to verify ordinary filmstrip swipes pan the shared time axis.
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
  echo "Swiping left on the filmstrip did not advance the shared playhead."
  echo "Observed playheadMs=$forward_ms scrollX=$forward_scroll"
  collect_capture_diagnostics filmstrip-scrub-forward-not-advanced
  exit 20
fi
# Every lane must retain exactly the same horizontal offset; a different clamp
# would make the ruler and gray origin area drift away from the filmstrip.
forward_scrolls="$(grep "TIMELINE_SYNC_SAMPLE" emulator-screenshots/filmstrip-scrub-forward-logcat.txt | tail -n 1 | sed -n 's/.*scrollXs=\([0-9,]*\).*/\1/p')"
if [ -z "$forward_scrolls" ] || [ "$(echo "$forward_scrolls" | tr ',' '\n' | sort -u | wc -l)" -ne 1 ]; then
  echo "Timeline lanes diverged after a filmstrip swipe: scrollXs=$forward_scrolls"
  collect_capture_diagnostics filmstrip-scrub-lane-scroll-diverged
  exit 24
fi

timeout -k 2s 10s adb -s emulator-5554 logcat -d -v threadtime \
  -s TimelineAlignmentTest:I TimelineSyncTest:I \
  > emulator-screenshots/filmstrip-scrub-forward-logcat.txt 2>&1
reverse_bounds="$(python3 - emulator-screenshots/filmstrip-scrub-forward-logcat.txt <<'PY'
import re
import sys
latest = None
for line in open(sys.argv[1], errors="replace"):
    match = re.search(r"TIMELINE_ALIGNMENT_SNAPSHOT .*?clipBounds=(none|\d+,\d+,\d+,\d+)", line)
    if match and match.group(1) != "none":
        latest = match.group(1).replace(",", " ")
if latest:
    print(latest)
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

timeout -k 2s 10s adb -s emulator-5554 logcat -d -v threadtime -s TimelineAlignmentTest:I \
  > emulator-screenshots/timeline-play-button-measurement.txt 2>&1
play_coords="$(python3 - emulator-screenshots/timeline-play-button-measurement.txt <<'PY'
import re
import sys
latest = None
for line in open(sys.argv[1], errors="replace"):
    match = re.search(
        r"TIMELINE_ALIGNMENT_SNAPSHOT playheadCenterPx=(\d+) playheadCenterYpx=(\d+)",
        line
    )
    if match:
        latest = f"{match.group(1)} {match.group(2)}"
if latest:
    print(latest)
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
cat emulator-screenshots/timeline-sync-startup-logcat.txt >> emulator-screenshots/timeline-sync-logcat.txt
if ! python3 .github/scripts/assert-timeline-sync.py emulator-screenshots/timeline-sync-logcat.txt 2>&1 | tee emulator-screenshots/timeline-sync-validation.txt; then
  echo "Live playback/timeline synchronization assertions failed."
  collect_capture_diagnostics timeline-sync-assertion-failure
  exit 16
fi

echo "Step 11b: split the selected video through its real clip menu, then direct-drag it"
# The main-video filmstrip is now a move surface. Use its existing stationary
# long-press context menu to invoke the same production splitSelectedClipAtPlayhead()
# handler after import/thumbnail readiness has already passed above.
# Move the real playhead close to the fixture midpoint. HorizontalScrollView
# consumes the measured touch-slop before scrolling, so add it to each gesture;
# otherwise small correction swipes systematically undershoot the target.
# The drag target and expected reflow below use the measured split boundary.
midpoint_ready=false
for attempt in $(seq 1 5); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s TimelineSyncTest:I \
    > emulator-screenshots/video-midpoint-seek-logcat.txt 2>&1 || true
  midpoint_action="$(python3 - emulator-screenshots/video-midpoint-seek-logcat.txt "$touch_slop_px" <<'PY'
import re
import sys

pattern = re.compile(r"TIMELINE_SYNC_SAMPLE .*?playheadMs=(\d+).*?pxPerSecond=([0-9.]+)")
latest = None
for line in open(sys.argv[1], errors="replace"):
    match = pattern.search(line)
    if match:
        latest = (int(match.group(1)), float(match.group(2)))
if latest is None:
    raise SystemExit("No measured timeline playhead sample before midpoint seek.")
current_ms, pixels_per_second = latest
target_ms = 4_000
delta_px = round((target_ms - current_ms) * pixels_per_second / 1000.0)
if abs(target_ms - current_ms) <= 80:
    print("READY", current_ms)
else:
    delta_px = max(-520, min(520, delta_px))
    touch_slop_px = int(sys.argv[2])
    gesture_delta_px = delta_px + (touch_slop_px if delta_px > 0 else -touch_slop_px)
    # The editor's verified scrub gesture advances time with a leftward swipe.
    start_x = 900 if delta_px > 0 else 500
    end_x = start_x - gesture_delta_px
    if not (128 <= start_x <= 952 and 128 <= end_x <= 952):
        raise SystemExit(f"Midpoint scrub would leave the safe ruler range: {start_x}->{end_x}")
    print(start_x, end_x, current_ms)
PY
)"
  read -r midpoint_first midpoint_second midpoint_current <<< "$midpoint_action"
  if [ "$midpoint_first" = "READY" ]; then
    midpoint_ready=true
    echo "Playhead already at fixture midpoint: ${midpoint_current}ms"
    break
  fi
  echo "Moving ruler playhead from ${midpoint_current}ms toward 4000ms (attempt $attempt/5, touchSlop=${touch_slop_px}px): ${midpoint_first}->${midpoint_second}"
  timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$midpoint_first" "$ruler_y" "$midpoint_second" "$ruler_y" 600
  sleep 1
done
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s TimelineSyncTest:I \
  > emulator-screenshots/video-midpoint-seek-logcat.txt 2>&1 || true
if ! python3 - emulator-screenshots/video-midpoint-seek-logcat.txt <<'PY'
import re
import sys
pattern = re.compile(r"TIMELINE_SYNC_SAMPLE .*?playheadMs=(\d+)")
latest = None
for line in open(sys.argv[1], errors="replace"):
    match = pattern.search(line)
    if match:
        latest = int(match.group(1))
if latest is None or abs(latest - 4_000) > 100:
    raise SystemExit(f"Playhead did not reach the 4s frame-snapped split target within 100ms: {latest}")
print(f"PASS: ruler scrub positioned the playhead at {latest}ms for a frame-snapped split.")
PY
then
  echo "Could not position the playhead at the fixture midpoint before split."
  collect_capture_diagnostics video-midpoint-seek-failed
  exit 90
fi
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s TimelineAlignmentTest:I \
  > emulator-screenshots/video-before-split-measurements.txt 2>&1 || true
split_clip_coords="$(python3 - emulator-screenshots/video-before-split-measurements.txt <<'PY'
import re
import sys

# Use the editor's own measured global bounds for the first main-video clip.
# UIAutomator can report "could not get idle state" while the timeline remains
# visually correct; the app emits this same visible clip rectangle with its
# thumbnail/playhead geometry, so do not make the production interaction depend
# on a full accessibility-tree dump.
pattern = re.compile(
    r"TIMELINE_ALIGNMENT_SNAPSHOT .*?clipBounds=(\d+),(\d+),(\d+),(\d+)"
)
latest = None
for line in open(sys.argv[1], errors="replace"):
    match = pattern.search(line)
    if match:
        latest = tuple(map(int, match.groups()))
if latest is None:
    raise SystemExit("The editor did not report visible bounds for the first main-video clip.")
left, top, right, bottom = latest
if right - left < 40 or bottom - top < 20:
    raise SystemExit(f"The measured main-video clip bounds are too small: {latest}")
center_x = (left + right) // 2
center_y = (top + bottom) // 2
if not (0 <= center_x < 1080 and 0 <= center_y < 1920):
    raise SystemExit(f"The measured main-video clip centre is outside the emulator screen: {latest}")
print(f"PASS: using app-measured main-video clip bounds {left},{top},{right},{bottom}", file=sys.stderr)
print(center_x, center_y)
PY
)"
if [ -z "$split_clip_coords" ]; then
  echo "Could not resolve the replacement clip's long-press hit target."
  collect_capture_diagnostics video-split-long-press-target-missing
  exit 91
fi
read -r split_clip_x split_clip_y <<< "$split_clip_coords"
timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$split_clip_x" "$split_clip_y" "$split_clip_x" "$split_clip_y" 1100
sleep 1
if ! dump_hierarchy video-split-context-menu.xml emulator-screenshots/video-split-context-menu.xml; then
  echo "The replacement clip's long-press did not expose its context menu."
  collect_capture_diagnostics video-split-context-menu-missing
  exit 91
fi
split_menu_coords="$(python3 - emulator-screenshots/video-split-context-menu.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
candidates = []
for node in root.iter("node"):
    text = " ".join(node.attrib.get("text", "").split())
    desc = " ".join(node.attrib.get("content-desc", "").split())
    if text != "Böl" and desc != "Böl":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        continue
    left, top, right, bottom = map(int, match.groups())
    candidates.append((node.attrib.get("clickable") == "true", left, top, right, bottom))
if not candidates:
    raise SystemExit("The clip context menu did not expose the Böl action.")
_, left, top, right, bottom = max(candidates)
print((left + right) // 2, (top + bottom) // 2)
PY
)"
if [ -z "$split_menu_coords" ]; then
  echo "Could not resolve the Böl context-menu action."
  collect_capture_diagnostics video-split-context-action-missing
  exit 91
fi
timeout -k 2s 8s adb -s emulator-5554 shell input tap $split_menu_coords

split_marker=""
for attempt in $(seq 1 12); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s TimelineSyncTest:I \
    > emulator-screenshots/video-split-drag-logcat.txt 2>&1 || true
  split_marker="$(grep "VIDEO_CLIP_SPLIT" emulator-screenshots/video-split-drag-logcat.txt | tail -n 1 || true)"
  if [ -n "$split_marker" ]; then break; fi
  sleep 1
done
if [ -z "$split_marker" ]; then
  echo "The production split handler did not run for the deterministic video fixture."
  collect_capture_diagnostics video-clip-split-marker-missing
  exit 92
fi
if ! echo "$split_marker" | grep -Eq 'gapMs=0([[:space:]]|$)'; then
  echo "Split left a non-zero timeline seam: $split_marker"
  collect_capture_diagnostics video-clip-split-seam
  exit 93
fi
split_boundary_ms="$(printf '%s\n' "$split_marker" | sed -n 's/.*firstEndMs=\([0-9][0-9]*\).*/\1/p')"
if [ -z "$split_boundary_ms" ]; then
  echo "The split log did not include the calculated first-segment boundary: $split_marker"
  collect_capture_diagnostics video-clip-split-boundary-missing
  exit 93
fi
split_second_duration_ms="$(echo "$split_marker" | grep -oE 'secondDurationMs=[0-9]+' | cut -d= -f2)"
if [ -z "$split_second_duration_ms" ]; then
  echo "The split log did not include the measured second-segment duration: $split_marker"
  collect_capture_diagnostics video-clip-split-duration-missing
  exit 93
fi

# Read both clip surfaces from the app's measured View geometry. UIAutomator
# can fail to obtain an idle accessibility snapshot while many thumbnail cells
# are attached; the debug snapshot records every visible clip by index instead.
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s TimelineAlignmentTest:I \
  > emulator-screenshots/video-split-before-drag-measurements.txt 2>&1
# The split is performed while playback is paused, so its event log is not
# guaranteed to contain a fresh TIMELINE_SYNC_SAMPLE. Reuse the live measured
# playhead centre/scale captured during the immediately preceding midpoint seek.
clip_drag_coords="$(python3 - emulator-screenshots/video-split-before-drag-measurements.txt emulator-screenshots/video-midpoint-seek-logcat.txt "$split_boundary_ms" <<'PY'
import re
import sys

bounds = {}
pattern = re.compile(r"TIMELINE_ALIGNMENT_SNAPSHOT .*?clipBoundsByIndex=([0-9:,;]+)")
for line in open(sys.argv[1], errors="replace"):
    match = pattern.search(line)
    if not match:
        continue
    measured = {}
    for entry in match.group(1).split(";"):
        key, raw = entry.split(":", 1)
        measured[int(key)] = tuple(map(int, raw.split(",")))
    if set(measured) >= {1, 2}:
        bounds = measured
if set(bounds) < {1, 2}:
    raise SystemExit(f"Expected two app-measured split clip surfaces; found {bounds}")
pixel_seam_px = bounds[2][0] - bounds[1][2]
if abs(pixel_seam_px) > 1:
    raise SystemExit(
        f"Split clips are not visually edge-to-edge: first={bounds[1]}, "
        f"second={bounds[2]}, seam={pixel_seam_px}px (maximum ±1px)"
    )
print(f"PASS: split clip screen edges meet within 1px (measured seam={pixel_seam_px}px)", file=sys.stderr)
second = bounds[2]
start_x = (second[0] + second[2]) // 2
start_y = (second[1] + second[3]) // 2

# The second segment currently starts at split_boundary_ms. Drag its centre
# backwards by that exact time so its new start clamps to project time zero.
# Use live px/s; a 16px bias ensures the requested start is strictly before zero.
split_boundary_ms = int(sys.argv[3])
sample = None
for line in open(sys.argv[2], errors="replace"):
    # The production log writes pxPerSecond before playheadCenterPx.
    # Match the emitted field order; the old expression expected the reverse
    # order and silently rejected every valid live sync sample.
    match = re.search(
        r"TIMELINE_SYNC_SAMPLE .*?pxPerSecond=([0-9.]+).*?playheadCenterPx=(\d+)",
        line
    )
    if match:
        sample = (int(match.group(2)), float(match.group(1)))
if sample is None:
    raise SystemExit("No measured fixed-playhead position/scale for the split reorder.")
_, pixels_per_second = sample
drop_x = round(start_x - split_boundary_ms * pixels_per_second / 1000.0 - 16)
if start_x <= drop_x or drop_x < 128:
    raise SystemExit(
        f"Second segment cannot reach project zero from a safe coordinate: "
        f"second={second}, drop={drop_x}, start={start_x}, split={split_boundary_ms}"
    )
print(start_x, start_y, drop_x)
PY
)"
if [ -z "$clip_drag_coords" ]; then
  echo "Could not resolve the second split clip's direct-drag hit target."
  collect_capture_diagnostics video-split-drag-target-missing
  exit 95
fi
read -r drag_start_x drag_y drag_drop_x <<< "$clip_drag_coords"
timeout -k 2s 8s adb -s emulator-5554 shell input draganddrop "$drag_start_x" "$drag_y" "$drag_drop_x" "$drag_y" 900
drag_marker=""
for attempt in $(seq 1 12); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s TimelineSyncTest:I \
    > emulator-screenshots/video-split-drag-logcat.txt 2>&1 || true
  drag_marker="$(grep "VIDEO_CLIP_DROP" emulator-screenshots/video-split-drag-logcat.txt | tail -n 1 || true)"
  if [ -n "$drag_marker" ]; then break; fi
  sleep 1
done
if [ -z "$drag_marker" ]; then
  echo "A direct one-finger horizontal clip drag did not reach the production drop handler."
  collect_capture_diagnostics video-direct-clip-drag-not-received
  exit 96
fi
# Use the measured duration of the moved segment, not a nominal 8,000ms fixture duration.
# Container/decoder duration metadata can differ slightly from the nominal FFmpeg input.
expected_first_start_ms="$split_second_duration_ms"
if ! echo "$drag_marker" | grep -Eq "ordered=1,0 .*starts=1:0,0:${expected_first_start_ms}([[:space:]]|$)"; then
  echo "The direct drag did not place the second half at 00:00 and push the first by its own duration (${expected_first_start_ms}ms): $drag_marker"
  collect_capture_diagnostics video-direct-clip-drag-order
  exit 97
fi
if ! take_screen emulator-screenshots/video-split-after-drag.png; then
  collect_capture_diagnostics video-split-after-drag-capture
  exit 6
fi
# Validate the ACTUAL post-drop Views and pixels. The earlier split check only
# measured the pre-drag geometry; it could not catch a divider drawn by the two
# adjacent clip frames after they were reordered.
post_drop_seam_ready=false
for attempt in $(seq 1 10); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s TimelineAlignmentTest:I \
    > emulator-screenshots/video-split-after-drag-measurements.txt 2>&1 || true
  if grep -q "TIMELINE_ALIGNMENT_SNAPSHOT.*clipBoundsByIndex=" \
      emulator-screenshots/video-split-after-drag-measurements.txt; then
    post_drop_seam_ready=true
    break
  fi
  sleep 1
done
if [ "$post_drop_seam_ready" != true ]; then
  echo "No fresh post-drop clip geometry was captured."
  collect_capture_diagnostics video-split-after-drag-geometry-missing
  exit 98
fi
if ! python3 - emulator-screenshots/video-split-after-drag.png emulator-screenshots/video-split-after-drag-measurements.txt <<'PY'
from PIL import Image
import re
import sys

image = Image.open(sys.argv[1]).convert("RGB")
pattern = re.compile(r"TIMELINE_ALIGNMENT_SNAPSHOT .*?clipBoundsByIndex=([0-9:,;]+)")
snapshots = []
for line in open(sys.argv[2], errors="replace"):
    match = pattern.search(line)
    if not match:
        continue
    bounds = {}
    for item in match.group(1).split(";"):
        index, raw = item.split(":", 1)
        bounds[int(index)] = tuple(map(int, raw.split(",")))
    if len(bounds) >= 2:
        snapshots.append(bounds)
if not snapshots:
    raise SystemExit("The post-drop snapshot did not include both split clip Views.")
bounds = sorted(snapshots[-1].values(), key=lambda rect: rect[0])
left, right = bounds[0], bounds[1]
seam = left[2] - right[0]
if abs(seam) > 1:
    raise SystemExit(
        f"Post-drop clip Views are not edge-to-edge: left={left}, right={right}, seam={seam}px"
    )
sample_x = (left[2] + right[0]) // 2
sample_y = (max(left[1], right[1]) + min(left[3], right[3])) // 2
if not (0 <= sample_y < image.height):
    raise SystemExit(f"Post-drop seam sample is outside screenshot: y={sample_y}, size={image.size}")
# The selected clip's rounded trim handle overlays the exact seam.
# Geometry above proves the Views meet; sample well inside both clips so the
# expected white handle is not mistaken for a dark/empty filmstrip divider.
def fixture_frame_pixel(rgb):
    red, green, blue = rgb
    return green > 120 and green > red * 1.5 and green > blue * 1.5
left_pixels = [
    image.getpixel((sample_x - offset, sample_y))
    for offset in range(18, 23)
]
right_pixels = [
    image.getpixel((sample_x + offset, sample_y))
    for offset in range(18, 23)
]
left_visible = sum(fixture_frame_pixel(pixel) for pixel in left_pixels)
right_visible = sum(fixture_frame_pixel(pixel) for pixel in right_pixels)
if left_visible < 4 or right_visible < 4:
    raise SystemExit(
        f"Filmstrip frame is not visible on both sides of the joined clips: "
        f"seam={sample_x}px y={sample_y}, left={left_visible}/5 {left_pixels}, "
        f"right={right_visible}/5 {right_pixels}"
    )
print(
    f"PASS: post-drop clip Views meet within 1px and both filmstrip frames remain visible "
    f"(left={left_visible}/5, right={right_visible}/5; seam={sample_x}px)"
)
PY
then
  collect_capture_diagnostics video-split-after-drag-visible-seam
  exit 99
fi
# Exercise the real video trim grips with Android touch events. The app's
# measured View bounds are the source of truth; do not hard-code device geometry.
resolve_video_edge_point() {
  local edge="$1"
  local direction="$2"
  local bounds_file="emulator-screenshots/video-edge-trim-measurements.txt"
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s TimelineAlignmentTest:I \
    > "$bounds_file" 2>&1 || true
  python3 - "$bounds_file" "$edge" "$direction" <<'PY'
import re
import sys

path, edge, direction = sys.argv[1:]
pattern = re.compile(r"TIMELINE_ALIGNMENT_SNAPSHOT .*?clipBoundsByIndex=([0-9:,;]+)")
latest = None
for line in open(path, errors="replace"):
    match = pattern.search(line)
    if not match:
        continue
    measured = {}
    for item in match.group(1).split(";"):
        index, raw = item.split(":", 1)
        measured[int(index)] = tuple(map(int, raw.split(",")))
    if 1 in measured:
        latest = measured[1]
if latest is None:
    raise SystemExit("No measured bounds for the selected first video clip (UI clip ids are 1-based).")
left, top, right, bottom = latest
width, height = right - left, bottom - top
if width < 70 or height < 20:
    raise SystemExit(f"Selected clip is too small for reliable edge gesture: {latest}")
# Android's gesture slop is 21px on this emulator. Begin inside the
# transparent edge target, but outside the stationary playhead overlay, and
# travel far enough to make the production handler classify this as a drag.
travel = min(42, max(34, width // 8))
if edge == "start":
    # 420dpi emulator: keep the DOWN point beyond the 28dp playhead overlay
    # while staying inside the edge target (clipWidth/3 is the limiting width).
    x1, x2 = (left + 45, left + 45 + travel) if direction == "trim" else (left + 45, left + 45 - travel)
else:
    x1, x2 = (right - 8, right - 8 - travel) if direction == "trim" else (right - 36, right - 36 + travel)
y = (top + bottom) // 2
if not (left <= x1 < right and left <= x2 < right and top <= y < bottom):
    raise SystemExit(f"Resolved touch is outside the measured clip: bounds={latest}, points={x1},{x2},{y}")
print(f"PASS: {edge} {direction} touch uses measured clip bounds {latest}", file=sys.stderr)
print(x1, y, x2)
PY
}

assert_video_edge_trim() {
  local edge="$1"
  local direction="$2"
  local marker_file="emulator-screenshots/video-edge-trim-logcat.txt"
  local marker=""
  for attempt in $(seq 1 15); do
    timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s TimelineSyncTest:I TimelineAlignmentTest:I \
      > "$marker_file" 2>&1 || true
    marker="$(grep "VIDEO_CLIP_EDGE_TRIM edge=$edge " "$marker_file" | tail -n 1 || true)"
    if [ -n "$marker" ]; then
      local actual_delta
      actual_delta="$(printf '%s\n' "$marker" | sed -n 's/.*deltaPx=\(-\{0,1\}[0-9.]*\).*/\1/p')"
      if [ "$direction" = "trim" ]; then
        if [ "$edge" = "start" ] && awk "BEGIN {exit !($actual_delta > 5)}"; then break; fi
        if [ "$edge" = "end" ] && awk "BEGIN {exit !($actual_delta < -5)}"; then break; fi
      else
        if [ "$edge" = "start" ] && awk "BEGIN {exit !($actual_delta < -5)}"; then break; fi
        if [ "$edge" = "end" ] && awk "BEGIN {exit !($actual_delta > 5)}"; then break; fi
      fi
      marker=""
    fi
    sleep 1
  done
  if [ -z "$marker" ]; then
    echo "The real video $edge trim grip did not emit a fresh $direction gesture marker."
    collect_capture_diagnostics "video-$edge-$direction-trim-marker-missing"
    exit 100
  fi
  if ! python3 - "$marker" "$edge" "$direction" <<'PY'
import re
import sys

line, edge, direction = sys.argv[1:]
fields = dict(re.findall(r"([A-Za-z][A-Za-z0-9]*)=([^ ]+)", line))
required = [
    "beforeTimelineStartMs", "beforeSourceStartMs", "beforeSourceEndMs",
    "beforeDurationMs", "afterTimelineStartMs", "afterSourceStartMs",
    "afterSourceEndMs", "afterDurationMs", "laneStarts", "deltaPx"
]
missing = [key for key in required if key not in fields]
if missing:
    raise SystemExit(f"Trim marker is missing fields {missing}: {line}")
before_start = int(fields["beforeSourceStartMs"])
before_end = int(fields["beforeSourceEndMs"])
before_duration = int(fields["beforeDurationMs"])
after_start = int(fields["afterSourceStartMs"])
after_end = int(fields["afterSourceEndMs"])
after_duration = int(fields["afterDurationMs"])
if int(fields["afterTimelineStartMs"]) != 0:
    raise SystemExit(f"First video clip moved away from project zero: {line}")
if before_duration <= 0 or after_duration <= 0:
    raise SystemExit(f"Video trim produced a non-positive duration: {line}")
if edge == "start":
    if after_end != before_end:
        raise SystemExit(f"Left grip changed the source OUT point: {line}")
    valid = after_start > before_start if direction == "trim" else after_start < before_start
else:
    if after_start != before_start:
        raise SystemExit(f"Right grip changed the source IN point: {line}")
    valid = after_end < before_end if direction == "trim" else after_end > before_end
if not valid:
    raise SystemExit(f"{edge} grip did not change only its own source edge in {direction} direction: {line}")
duration_valid = after_duration < before_duration if direction == "trim" else after_duration > before_duration
if not duration_valid:
    raise SystemExit(f"{edge} grip did not update clip duration as expected: {line}")
starts = dict((int(index), int(start)) for index, start in re.findall(r"(\d+):(\d+)", fields["laneStarts"]))
if starts.get(0) != 0 or starts.get(1) != after_duration:
    raise SystemExit(
        f"Main video lane did not reflow gaplessly after {edge} {direction}: "
        f"duration={after_duration}, starts={starts}, marker={line}"
    )
print(
    f"PASS: video {edge} grip {direction}; only the intended source edge changed, "
    f"duration={before_duration}->{after_duration}ms, next clip starts at {starts[1]}ms"
)
PY
  then
    collect_capture_diagnostics "video-$edge-$direction-trim-invalid"
    exit 101
  fi
}

for edge_direction in "start trim" "start extend" "end trim" "end extend"; do
  read -r edge direction <<< "$edge_direction"
  edge_coords="$(resolve_video_edge_point "$edge" "$direction")"
  if [ -z "$edge_coords" ]; then
    echo "Could not resolve the measured $edge video trim grip."
    collect_capture_diagnostics "video-$edge-$direction-trim-target-missing"
    exit 102
  fi
  read -r edge_x1 edge_y edge_x2 <<< "$edge_coords"
  timeout -k 2s 8s adb -s emulator-5554 shell input swipe "$edge_x1" "$edge_y" "$edge_x2" "$edge_y" 700
  assert_video_edge_trim "$edge" "$direction"
done

echo "Step 11a-VideoHistory: verify the last real video edge trim is one global Undo/Redo step"
dump_hierarchy editor-video-trim-history-after.xml emulator-screenshots/editor-video-trim-history-after.xml || { collect_capture_diagnostics project-history-video-trim-controls-missing; exit 103; }
video_trim_undo_coords="$(python3 - emulator-screenshots/editor-video-trim-history-after.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if n.attrib.get("content-desc","").strip()=="Proje işlemini geri al" and n.attrib.get("enabled","false").lower()=="true":
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Global Undo unavailable after a physical video trim.")
PY
)" || { collect_capture_diagnostics project-history-video-trim-undo-disabled; exit 103; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $video_trim_undo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-video-trim-undo.txt 2>&1 || true
video_trim_history_values="$(python3 - emulator-screenshots/project-history-video-trim-undo.txt <<'PY'
import re,sys
lines=open(sys.argv[1],errors="replace").read().splitlines()
events=[x for x in lines if "PROJECT_HISTORY_APPLIED action=undo " in x]
if not events: raise SystemExit("No global project Undo diagnostic followed the video trim.")
m=re.search(r"beforeClipRanges=([^ ]+) afterClipRanges=([^ ]+)",events[-1])
if not m: raise SystemExit("Undo diagnostics did not contain complete video clip ranges: "+events[-1])
trimmed,restored=m.groups()
if trimmed==restored: raise SystemExit("Video trim Undo did not restore a previous clip range: "+events[-1])
def first_clip(raw):
    return tuple(raw.split(";")[0].split(","))
before,after=first_clip(trimmed),first_clip(restored)
if len(before)<9 or len(after)<9: raise SystemExit("Video history range tuple is incomplete.")
if before[0]!="0" or after[0]!="0": raise SystemExit(f"First main-video clip moved away from zero during Undo: {before}, {after}")
print(trimmed,restored)
PY
)" || { collect_capture_diagnostics project-history-video-trim-undo-failed; exit 104; }
read -r trimmed_video_ranges restored_video_ranges <<< "$video_trim_history_values"
echo "PASS: one global Undo restored the previous main-video clip range."
dump_hierarchy editor-video-trim-history-before-redo.xml emulator-screenshots/editor-video-trim-history-before-redo.xml || { collect_capture_diagnostics project-history-video-trim-redo-controls-missing; exit 104; }
video_trim_redo_coords="$(python3 - emulator-screenshots/editor-video-trim-history-before-redo.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if n.attrib.get("content-desc","").strip()=="Proje işlemini yinele" and n.attrib.get("enabled","false").lower()=="true":
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Global Redo unavailable after video trim Undo.")
PY
)" || { collect_capture_diagnostics project-history-video-trim-redo-disabled; exit 104; }
timeout -k 2s 8s adb -s emulator-5554 logcat -c
timeout -k 2s 8s adb -s emulator-5554 shell input tap $video_trim_redo_coords
sleep 1
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s ProjectHistoryTest:I > emulator-screenshots/project-history-video-trim-redo.txt 2>&1 || true
python3 - emulator-screenshots/project-history-video-trim-redo.txt "$trimmed_video_ranges" "$restored_video_ranges" <<'PY'
import re,sys
lines=open(sys.argv[1],errors="replace").read().splitlines()
events=[x for x in lines if "PROJECT_HISTORY_APPLIED action=redo " in x]
if not events: raise SystemExit("No global project Redo diagnostic followed the video trim Undo.")
m=re.search(r"beforeClipRanges=([^ ]+) afterClipRanges=([^ ]+)",events[-1])
if not m: raise SystemExit("Redo diagnostics did not contain complete video clip ranges: "+events[-1])
before,after=m.groups()
expected_trimmed,expected_restored=sys.argv[2:4]
if before!=expected_restored or after!=expected_trimmed: raise SystemExit("Video trim Redo did not reapply the exact post-trim clip arrangement: "+events[-1])
print("PASS: one global Redo reapplied the exact video trim and timeline arrangement.")
PY

echo "PASS: both video trim grips were physically dragged inward and outward; source edges stayed independent and the main-video lane remained gapless."
echo "PASS: split seam is 0ms; direct drag, visible filmstrip seam and reorder geometry verified; draft save/reopen follows."

echo "Step 12/12: validate automatic draft save and resume list"
# Android Back is context-sensitive: with a text/overlay layer selected it only
# exits that tool mode, leaving VideoEditorActivity in the foreground. Use the
# editor's explicit close control so onStop() runs and the draft is actually saved.
close_editor_center="$(python3 - emulator-screenshots/editor-window.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if (
        node.attrib.get("content-desc") == "Düzenleyiciyi kapat"
        and node.attrib.get("clickable") == "true"
        and node.attrib.get("enabled") == "true"
    ):
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            left, top, right, bottom = map(int, match.groups())
            print((left + right) // 2, (top + bottom) // 2)
            break
PY
)"
if [ -z "$close_editor_center" ]; then
  echo "Could not locate the editor's accessible close control in the captured hierarchy."
  collect_capture_diagnostics draft-close-control-missing
  exit 11
fi
read -r close_editor_x close_editor_y <<< "$close_editor_center"
echo "Closing the editor through its real close control at ${close_editor_x},${close_editor_y}."
timeout -k 2s 8s adb -s emulator-5554 shell input tap "$close_editor_x" "$close_editor_y"
draft_saved=false
for attempt in $(seq 1 12); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s MathMotorDraft:I > emulator-screenshots/draft-autosave-logcat.txt 2>&1 || true
  if grep -Fq "LOCAL_DRAFT_SAVED id=11111111-1111-4111-8111-111111111111 clips=2" emulator-screenshots/draft-autosave-logcat.txt; then
    draft_saved=true
    break
  fi
  sleep 1
done
if [ "$draft_saved" != true ]; then
  echo "Editor closed, but did not confirm automatic save of both split video segments."
  cat emulator-screenshots/draft-autosave-logcat.txt || true
  collect_capture_diagnostics draft-autosave-failure
  exit 11
fi
echo "PASS: editor close triggered automatic local draft save."

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

echo "Step 12c/12: force-stop/reopen the split-video draft and verify both segments"
timeout -k 2s 8s adb -s emulator-5554 shell am force-stop com.mathmotor.video.next || true
timeout -k 2s 5s adb -s emulator-5554 logcat -c || true
if ! timeout -k 2s 15s adb -s emulator-5554 shell am start \
  -n com.mathmotor.video.next/com.mathmotor.video.EditorTestEntryActivity \
  --es draft_id "11111111-1111-4111-8111-111111111111" | tee emulator-screenshots/video-split-draft-reopen.txt; then
  echo "Could not reopen the saved split-video draft after force-stop."
  collect_capture_diagnostics video-split-draft-reopen-launch-failure
  exit 14
fi
split_draft_restored=false
for attempt in $(seq 1 20); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorSmokeTest:I \
    > emulator-screenshots/video-split-draft-restore-logcat.txt 2>&1 || true
  if grep -Fq "EDITOR_SMOKE_DRAFT_VIDEO_RESTORED id=11111111-1111-4111-8111-111111111111 clips=2" \
      emulator-screenshots/video-split-draft-restore-logcat.txt; then
    split_draft_restored=true
    break
  fi
  sleep 1
done
if [ "$split_draft_restored" != true ]; then
  echo "The force-stopped video draft did not restore both split segments."
  cat emulator-screenshots/video-split-draft-restore-logcat.txt || true
  collect_capture_diagnostics video-split-draft-restore-failure
  exit 14
fi
if ! python3 - emulator-screenshots/video-split-draft-restore-logcat.txt <<'PY'
import re
import sys

pattern = re.compile(
    r"EDITOR_SMOKE_DRAFT_VIDEO_RESTORED "
    r"id=11111111-1111-4111-8111-111111111111 "
    r"clips=(\d+) ranges=([0-9:,]+)"
)
matches = [pattern.search(line) for line in open(sys.argv[1], errors="replace")]
matches = [match for match in matches if match]
if not matches:
    raise SystemExit("Restored video clip ranges were not logged.")
count = int(matches[-1].group(1))
ranges = [tuple(map(int, part.split(":"))) for part in matches[-1].group(2).split(",")]
if count != 2 or len(ranges) != 2:
    raise SystemExit(f"Expected exactly two restored split segments; count={count}, ranges={ranges}")
if ranges[0][1] != ranges[1][0]:
    raise SystemExit(
        f"Restored split segments no longer meet edge-to-edge: ranges={ranges}, "
        f"seam={ranges[1][0] - ranges[0][1]}ms"
    )
if ranges[0][0] != 0 or ranges[1][1] <= ranges[1][0]:
    raise SystemExit(f"Restored split segments have invalid project ranges: {ranges}")
print(f"PASS: force-stop/reopen restored both split segments with a 0ms seam: {ranges}")
PY
then
  collect_capture_diagnostics video-split-draft-restore-seam
  exit 14
fi
if ! take_screen emulator-screenshots/video-split-draft-restored.png; then
  collect_capture_diagnostics video-split-draft-restore-capture-failure
  exit 6
fi

echo "Step 12b/12: verify home-screen photo opens as a full-frame editable layer"
timeout -k 2s 8s adb -s emulator-5554 shell am force-stop com.mathmotor.video.next || true
timeout -k 2s 5s adb -s emulator-5554 logcat -c || true
if ! timeout -k 2s 15s adb -s emulator-5554 shell am start \
  -n com.mathmotor.video.next/com.mathmotor.video.EditorTestEntryActivity \
  --es video_uri "file://$app_internal_video" \
  --es initial_photo_uri "file://$app_internal_photo" \
  --ez publish_initial_photo_to_media_store true \
  --ez timeline_sync_test true | tee emulator-screenshots/initial-photo-launch.txt; then
  echo "Could not launch the editor through the debug-only initial-photo entry point."
  collect_capture_diagnostics initial-photo-entry-launch-failure
  exit 60
fi
initial_photo_ready=false
for attempt in $(seq 1 20); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorSmokeTest:I \
    > emulator-screenshots/initial-photo-logcat.txt 2>&1 || true
  if grep -Fq "EDITOR_SMOKE_INITIAL_PHOTO_FULL_FRAME scale=1.0 xFraction=0.5 yFraction=0.5 opacity=1.0" \
      emulator-screenshots/initial-photo-logcat.txt; then
    initial_photo_ready=true
    break
  fi
  sleep 1
done
if [ "$initial_photo_ready" != true ]; then
  echo "Initial home photo did not produce the expected full-frame transform marker."
  collect_capture_diagnostics initial-photo-full-frame-transform-missing
  exit 61
fi

photo_visual_ready=false
for attempt in $(seq 1 15); do
  if ! take_screen emulator-screenshots/editor-initial-photo-full-frame.png; then
    collect_capture_diagnostics initial-photo-preview-capture-failure
    exit 6
  fi
  # Measure against the actual project canvas, not the wider dark preview stage.
  # A 9:16 image correctly occupies only the centered portrait canvas on a landscape stage.
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s PreviewCanvasTest:I \
    > emulator-screenshots/initial-photo-canvas-logcat.txt 2>&1 || true
  photo_metrics="$(python3 - emulator-screenshots/editor-initial-photo-full-frame.png emulator-screenshots/initial-photo-canvas-logcat.txt <<'PY'
from PIL import Image
import re
import sys

image = Image.open(sys.argv[1]).convert("RGB")
log_lines = open(sys.argv[2], errors="replace").read().splitlines()
matches = [line for line in log_lines if "PREVIEW_CANVAS_BOUNDS" in line]
if not matches:
    raise SystemExit("No measured project-canvas bounds were logged for the photo-only editor.")
match = re.search(
    r"stage=(\d+)x(\d+) canvas=(\d+)x(\d+) "
    r"offset=(\d+),(\d+) origin=(-?\d+),(-?\d+) aspect=([0-9.]+)",
    matches[-1],
)
if not match:
    raise SystemExit("Malformed project-canvas bounds: " + matches[-1])
stage_w, stage_h, canvas_w, canvas_h, offset_x, offset_y, origin_x, origin_y = map(
    int, match.groups()[:8]
)
aspect = float(match.group(9))
left = origin_x + offset_x
top = origin_y + offset_y
right = left + canvas_w
bottom = top + canvas_h
if left < 0 or top < 0 or right > image.width or bottom > image.height:
    raise SystemExit(
        f"Measured canvas is outside the screenshot: {left},{top}..{right},{bottom} in {image.size}"
    )
if canvas_w <= 0 or canvas_h <= 0 or abs(canvas_w / canvas_h - aspect) > 0.01:
    raise SystemExit(f"Invalid measured project-canvas aspect: {canvas_w}x{canvas_h}, aspect={aspect}")
canvas = image.crop((left, top, right, bottom))
points = []
for y in range(canvas.height):
    for x in range(canvas.width):
        red, green, blue = canvas.getpixel((x, y))
        if red > 180 and blue > 180 and green < 110:
            points.append((x, y))
if len(points) < 100:
    raise SystemExit("No meaningful magenta photo pixels were found inside the project canvas.")
xs = [x for x, _ in points]
ys = [y for _, y in points]
pixel_left, pixel_right = min(xs), max(xs)
pixel_top, pixel_bottom = min(ys), max(ys)
width = pixel_right - pixel_left + 1
height = pixel_bottom - pixel_top + 1
area = width * height
fill = len(points) / area if area else 0
ratio = width / height if height else 0
print(
    width, height, len(points), fill, ratio,
    f"canvas={canvas_w}x{canvas_h}@{left},{top}"
)
if height < canvas.height * 0.95:
    raise SystemExit(2)
if width < canvas.width * 0.95:
    raise SystemExit(3)
if abs(ratio - aspect) > 0.03:
    raise SystemExit(4)
if fill < 0.88:
    raise SystemExit(5)
PY
)" && {
    photo_visual_ready=true
    echo "Full-frame photo pixels inside measured project canvas: $photo_metrics"
    break
  }
  sleep 1
done
if [ "$photo_visual_ready" != true ]; then
  echo "The initial photo did not fill the portrait project canvas; pixel bounds/fill check failed."
  collect_capture_diagnostics initial-photo-not-full-frame
  exit 62
fi
dump_hierarchy initial-photo-preview.xml emulator-screenshots/initial-photo-preview.xml || true
if [ ! -s emulator-screenshots/initial-photo-preview.xml ]; then
  echo "Could not inspect the photo-only editor preview accessibility hierarchy."
  collect_capture_diagnostics initial-photo-preview-hierarchy-missing
  exit 63
fi
if grep -Fq 'content-desc="Galeriden video ekle"' emulator-screenshots/initial-photo-preview.xml || \
   grep -Fq 'text="Videonu ekle"' emulator-screenshots/initial-photo-preview.xml; then
  echo "The video-empty-state card remained visible over the photo-only project preview."
  collect_capture_diagnostics initial-photo-empty-card-still-visible
  exit 64
fi
timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s PreviewCanvasTest:I \
  > emulator-screenshots/initial-photo-canvas-logcat.txt 2>&1 || true
echo "PASS: home-screen photo used the full-frame transform, rendered across the 9:16 canvas, and replaced the empty-video card."

echo "Step 12c/13: verify content:// photo survives a complete app restart"
cp emulator-screenshots/initial-photo-logcat.txt emulator-screenshots/content-photo-import-logcat.txt
if ! grep -Fq "EDITOR_SMOKE_MEDIASTORE_CONTENT_URI uri=content://" emulator-screenshots/content-photo-import-logcat.txt; then
  echo "Debug entry did not publish the gallery fixture as a content:// URI."
  collect_capture_diagnostics content-photo-media-uri-missing
  exit 65
fi
if ! grep -Fq "OVERLAY_URI_COPIED_TO_PRIVATE_STORAGE source=content://" emulator-screenshots/content-photo-import-logcat.txt; then
  echo "The content:// gallery fixture was not copied into durable app-private storage."
  collect_capture_diagnostics content-photo-not-made-durable
  exit 65
fi
if ! dump_hierarchy content-photo-editor.xml emulator-screenshots/content-photo-editor.xml; then
  echo "Could not inspect editor before saving the content:// photo draft."
  collect_capture_diagnostics content-photo-editor-hierarchy-missing
  exit 66
fi
close_photo_editor="$(python3 - emulator-screenshots/content-photo-editor.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("content-desc") == "Düzenleyiciyi kapat" and node.attrib.get("clickable") == "true":
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",node.attrib.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
)"
if [ -z "$close_photo_editor" ]; then
  echo "Could not locate the editor close action for the content:// photo draft."
  collect_capture_diagnostics content-photo-close-control-missing
  exit 67
fi
read -r close_photo_x close_photo_y <<< "$close_photo_editor"
timeout -k 2s 8s adb -s emulator-5554 shell input tap "$close_photo_x" "$close_photo_y"
content_photo_draft_id=""
for attempt in $(seq 1 15); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s MathMotorDraft:I > emulator-screenshots/content-photo-draft-save-logcat.txt 2>&1 || true
  content_photo_draft_id="$(sed -n 's/.*LOCAL_DRAFT_SAVED id=\([^ ]*\).*/\1/p' emulator-screenshots/content-photo-draft-save-logcat.txt | tail -n 1)"
  if [[ "$content_photo_draft_id" =~ ^[0-9a-fA-F-]{36}$ ]]; then break; fi
  sleep 1
done
if ! [[ "$content_photo_draft_id" =~ ^[0-9a-fA-F-]{36}$ ]]; then
  echo "The content:// photo project did not produce a saved local draft ID."
  cat emulator-screenshots/content-photo-draft-save-logcat.txt || true
  collect_capture_diagnostics content-photo-draft-save-failed
  exit 68
fi
timeout -k 2s 8s adb -s emulator-5554 shell am force-stop com.mathmotor.video.next
timeout -k 2s 5s adb -s emulator-5554 logcat -c || true
if ! timeout -k 2s 15s adb -s emulator-5554 shell am start \
  -n com.mathmotor.video.next/com.mathmotor.video.EditorTestEntryActivity \
  --es draft_id "$content_photo_draft_id" | tee emulator-screenshots/content-photo-draft-reopen.txt; then
  echo "Could not relaunch the saved photo draft after force-stop."
  collect_capture_diagnostics content-photo-draft-relaunch-failed
  exit 69
fi
content_photo_restored=false
for attempt in $(seq 1 20); do
  timeout -k 2s 8s adb -s emulator-5554 logcat -d -v threadtime -s EditorSmokeTest:I > emulator-screenshots/content-photo-draft-restore-logcat.txt 2>&1 || true
  if grep -Fq "EDITOR_SMOKE_DRAFT_RESTORED id=$content_photo_draft_id overlays=1" emulator-screenshots/content-photo-draft-restore-logcat.txt; then
    content_photo_restored=true
    break
  fi
  sleep 1
done
if [ "$content_photo_restored" != true ]; then
  echo "The saved draft did not restore exactly one photo overlay after the app process was killed."
  cat emulator-screenshots/content-photo-draft-restore-logcat.txt || true
  collect_capture_diagnostics content-photo-draft-restore-failed
  exit 70
fi
sleep 2
if ! take_screen emulator-screenshots/content-photo-draft-restored.png; then
  collect_capture_diagnostics content-photo-draft-restored-capture-failure
  exit 6
fi
echo "PASS: content:// gallery photo was copied durably, saved in a draft, and restored after force-stop/relaunch."


echo "Step 13/13: save diagnostics"
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
test -s emulator-screenshots/editor-initial-photo-full-frame.png
test -s emulator-screenshots/initial-photo-logcat.txt
test -s emulator-screenshots/initial-photo-preview.xml
test -s emulator-screenshots/content-photo-draft-restored.png
test -s emulator-screenshots/content-photo-draft-restore-logcat.txt
echo "Home, empty editor, initial/playing editor, two-stage clip tool strip/detail/return geometry, live timeline sync, and autosaved-draft screenshots captured."
