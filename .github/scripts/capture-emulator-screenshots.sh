#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-screenshots
timeout 60s adb wait-for-device
boot_complete=""
for attempt in 1 2 3 4 5 6; do
  boot_complete="$(timeout 5s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [ "$boot_complete" = "1" ] && break
  sleep 2
done
if [ "$boot_complete" != "1" ]; then echo "Android boot timeout"; exit 1; fi
sleep 25
timeout 120s adb install -r app/build/outputs/apk/debug/app-debug.apk
timeout 10s adb shell am force-stop com.mathmotor.video.next || true
timeout 20s adb shell monkey -p com.mathmotor.video.next 1
sleep 10
capture() {
  local dest="$1" hierarchy="$2" path="emulator-screenshots/$2"
  for attempt in 1 2 3 4; do
    rm -f "$path"
    timeout 10s adb shell rm -f "/sdcard/$hierarchy" >/dev/null 2>&1 || true
    timeout 12s adb shell uiautomator dump "/sdcard/$hierarchy" >/dev/null 2>&1 || true
    timeout 12s adb pull "/sdcard/$hierarchy" "$path" >/dev/null 2>&1 || true
    [ -s "$path" ] || { sleep 2; continue; }
    if grep -q "isn't responding" "$path"; then
      coords="$(grep -o '<node[^>]*text="Wait"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$path" | head -n 1 | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | awk '{print int(($1+$3)/2),int(($2+$4)/2)}' || true)"
      [ -n "$coords" ] || return 1
      timeout 10s adb shell input tap $coords >/dev/null 2>&1 || true
      sleep 4
      continue
    fi
    timeout 15s adb exec-out screencap -p > "$dest"
    test -s "$dest"
    return 0
  done
  return 1
}
capture emulator-screenshots/home.png window.xml
node=""
for target in "Boş proje oluştur" "Boş düzenleyiciyi aç" "Yeni düzenleme başlat" "Galeriden video seç"; do
  node="$(grep -o '<node[^>]*text="[^"]*'"$target"'[^"]*"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' emulator-screenshots/window.xml | head -n 1 || true)"
  [ -n "$node" ] && break
done
[ -n "$node" ] || { echo "No usable home action button found"; exit 2; }
coords="$(printf '%s\n' "$node" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | awk '{print int(($1+$3)/2),int(($2+$4)/2)}')"
echo "Opening editor at $coords"
timeout 10s adb shell input tap $coords
sleep 5
capture emulator-screenshots/editor.png editor-window.xml
timeout 15s adb logcat -d > emulator-screenshots/logcat.txt
test -s emulator-screenshots/home.png
test -s emulator-screenshots/editor.png
