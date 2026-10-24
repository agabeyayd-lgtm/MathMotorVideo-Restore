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
timeout -k 2s 12s adb -s emulator-5554 shell am start -W -n com.mathmotor.video.next/com.mathmotor.video.MainActivity
sleep 3

take_screen() {
  local destination="$1"
  timeout -k 2s 12s adb -s emulator-5554 exec-out screencap -p > "$destination"
  test -s "$destination"
}
dump_hierarchy() {
  local remote="$1" local_file="$2"
  rm -f "$local_file"
  timeout -k 2s 10s adb -s emulator-5554 shell uiautomator dump "/sdcard/$remote" >/dev/null 2>&1 || true
  timeout -k 2s 6s adb -s emulator-5554 pull "/sdcard/$remote" "$local_file" >/dev/null 2>&1 || true
  [ -s "$local_file" ]
}

echo "Step 5/7: capture home screen"
take_screen emulator-screenshots/home.png
dump_hierarchy window.xml emulator-screenshots/window.xml || true

# If the platform raises its System UI ANR prompt, choose "Wait" and recapture.
if [ -s emulator-screenshots/window.xml ] && grep -q "isn't responding" emulator-screenshots/window.xml; then
  wait_node="$(grep -o '<node[^>]*text="Wait"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' emulator-screenshots/window.xml | head -n 1 || true)"
  wait_coords="$(printf '%s\n' "$wait_node" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | awk '{print int(($1+$3)/2),int(($2+$4)/2)}')"
  if [ -n "$wait_coords" ]; then
    timeout -k 2s 8s adb -s emulator-5554 shell input tap $wait_coords || true
    sleep 2
    take_screen emulator-screenshots/home.png
    dump_hierarchy window.xml emulator-screenshots/window.xml || true
  fi
fi

echo "Step 6/7: open editor"
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
timeout -k 2s 8s adb -s emulator-5554 shell input tap $coords
sleep 4
take_screen emulator-screenshots/editor.png
dump_hierarchy editor-window.xml emulator-screenshots/editor-window.xml || true

echo "Step 7/7: save diagnostics"
timeout -k 2s 8s adb -s emulator-5554 logcat -d > emulator-screenshots/logcat.txt || true
test -s emulator-screenshots/home.png
test -s emulator-screenshots/editor.png
echo "Both home and editor screenshots captured."
