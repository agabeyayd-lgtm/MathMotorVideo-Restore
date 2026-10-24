#!/usr/bin/env bash
set -euo pipefail

mkdir -p emulator-screenshots

# android-emulator-runner has already waited for the emulator to boot.
# Do not add another 60-second adb wait or a long post-boot sleep here.
boot_complete=""
for attempt in 1 2 3; do
  boot_complete="$(timeout 5s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [ "$boot_complete" = "1" ] && break
  sleep 2
done
if [ "$boot_complete" != "1" ]; then
  echo "Emulator runner started the script, but adb is not responding yet."
  exit 1
fi

timeout 60s adb install -r app/build/outputs/apk/debug/app-debug.apk
timeout 8s adb shell am force-stop com.mathmotor.video.next >/dev/null 2>&1 || true
# Starting the known launcher activity is faster and more deterministic than monkey.
timeout 8s adb shell am start -n com.mathmotor.video.next/com.mathmotor.video.MainActivity
sleep 3

take_screen() {
  local destination="$1"
  timeout 10s adb exec-out screencap -p > "$destination"
  test -s "$destination"
}

dump_hierarchy() {
  local remote="$1" local_file="$2"
  rm -f "$local_file"
  timeout 8s adb shell uiautomator dump "/sdcard/$remote" >/dev/null 2>&1 || true
  timeout 5s adb pull "/sdcard/$remote" "$local_file" >/dev/null 2>&1 || true
  [ -s "$local_file" ]
}

# Capture first; hierarchy dumping can stall on a cold emulator.
take_screen emulator-screenshots/home.png
dump_hierarchy window.xml emulator-screenshots/window.xml || true

# If Android raises its System UI ANR prompt, choose "Wait" and recapture.
if [ -s emulator-screenshots/window.xml ] && grep -q "isn't responding" emulator-screenshots/window.xml; then
  wait_node="$(grep -o '<node[^>]*text="Wait"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' emulator-screenshots/window.xml | head -n 1 || true)"
  wait_coords="$(printf '%s\n' "$wait_node" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | awk '{print int(($1+$3)/2),int(($2+$4)/2)}')"
  if [ -n "$wait_coords" ]; then
    timeout 5s adb shell input tap $wait_coords >/dev/null 2>&1 || true
    sleep 2
    take_screen emulator-screenshots/home.png
    dump_hierarchy window.xml emulator-screenshots/window.xml || true
  fi
fi

# Prefer the actual button bounds from accessibility; use a proportion-based
# fallback only when the emulator fails to provide a UI hierarchy.
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
  screen_size="$(timeout 4s adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1 || true)"
  if [ -n "$screen_size" ]; then
    read -r screen_w screen_h <<< "$screen_size"
    coords="$((screen_w / 2)) $((screen_h * 49 / 100))"
  else
    coords="540 940"
  fi
  echo "UI hierarchy unavailable; using fallback tap position."
fi

[ -n "$coords" ] || { echo "Could not determine a home button position."; exit 2; }
echo "Opening empty editor at $coords"
timeout 8s adb shell input tap $coords
sleep 4
take_screen emulator-screenshots/editor.png
dump_hierarchy editor-window.xml emulator-screenshots/editor-window.xml || true
timeout 8s adb logcat -d > emulator-screenshots/logcat.txt || true
test -s emulator-screenshots/home.png
test -s emulator-screenshots/editor.png
