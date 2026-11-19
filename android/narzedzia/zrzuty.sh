#!/bin/bash
# Zrzuty ekranu LivePL z emulatora Androida (na GitHubie albo lokalnie, gdy emulator już działa).
#   android/narzedzia/zrzuty.sh <plik APK> <folder na zrzuty>
# CZEKAJ=<sekundy> zmienia czas na załadowanie strony (domyślnie 15).
set -euo pipefail

APK="$1"
WYJSCIE="$2"
CZEKAJ="${CZEKAJ:-15}"
mkdir -p "$WYJSCIE"

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'

adb install -r "$APK"
ID=$(adb shell pm list packages eu.livepl.przegladarka | sed 's/^package://' | tr -d '\r' | head -1)
echo "aplikacja: $ID"

# pasek stanu jak na zdjęciach reklamowych: 9:41, pełna bateria, bez powiadomień
adb shell settings put global sysui_demo_allowed 1
# okna „… nie odpowiada” z przeciążonego emulatora nie zasłaniają zrzutów
adb shell settings put global hide_error_dialogs 1
adb shell am broadcast -a com.android.systemui.demo -e command enter > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false > /dev/null

# nazwa, tryb nocny Androida (yes/no), dodatkowe argumenty dla am start
zrzut() {
  local nazwa="$1" noc="$2"
  shift 2
  adb shell cmd uimode night "$noc" > /dev/null
  adb shell am force-stop "$ID"
  adb shell pm clear "$ID" > /dev/null
  # aplikacja po polsku, jak u użytkowników: strony (np. Poczta) dostają Accept-Language: pl
  adb shell cmd locale set-app-locales "$ID" --locales pl-PL > /dev/null 2>&1 || true
  adb shell am start -W -n "$ID/eu.livepl.przegladarka.MainActivity" "$@" > /dev/null
  sleep "$CZEKAJ"
  adb exec-out screencap -p > "$WYJSCIE/$nazwa.png"
  echo "zrzut: $nazwa"
}

zrzut 1-rezyserka-dzien no
zrzut 2-rezyserka-noc yes
zrzut 3-poczta-dzien no --es otworz poczta
zrzut 4-poczta-noc yes --es otworz poczta
zrzut 5-tvml-dzien no --es otworz tvml
zrzut 6-tvml-noc yes --es otworz tvml

adb shell cmd uimode night no > /dev/null
# dziennik aplikacji (błędy WebView, Compose)
adb logcat -d -v time > "$WYJSCIE/dziennik.txt" || true
