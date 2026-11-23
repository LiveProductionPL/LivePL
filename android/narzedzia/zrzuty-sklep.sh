#!/bin/bash
# Zrzuty ekranu do Google Play z działającego emulatora: telefon 1080×1920 i tablet 10" 1440×2560 (proporcja 9:16,
# jak wymaga Sklep). Najlepiej z wydania, żeby w stopce nie było „-debug”:
#   android/narzedzia/zrzuty-sklep.sh <APK LivePL> <APK Poczty LivePL> [APK LivePL Connect] [APK TVML]
# „-” zamiast APK pomija tę aplikację (np. tylko TVML: zrzuty-sklep.sh - - - tvml.apk).
# Zrzuty trafiają do android/sklep/<pakiet>/pl-PL/images (układ katalogów fastlane).
set -euo pipefail
# Git Bash na Windows nie może zamieniać ścieżek telefonu (/sdcard/…) na ścieżki Windows
export MSYS_NO_PATHCONV=1

APK_LIVEPL="$1"
APK_POCZTY="$2"
APK_CONNECT="${3:-}"
APK_TVML="${4:-}"
[ "$APK_CONNECT" = "-" ] && APK_CONNECT=""
[ "$APK_TVML" = "-" ] && APK_TVML=""
SKLEP="$(cd "$(dirname "$0")/.." && pwd)/sklep"
L=eu.livepl.przegladarka
P=eu.livepl.poczta
OBRAZY_L="$SKLEP/$L/pl-PL/images"
OBRAZY_P="$SKLEP/$P/pl-PL/images"
C=eu.livepl.connect
OBRAZY_C="$SKLEP/$C/pl-PL/images"
T=pl.tvml
OBRAZY_T="$SKLEP/$T/pl-PL/images"

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
[ "$APK_LIVEPL" != "-" ] && adb install -r "$APK_LIVEPL" > /dev/null
[ "$APK_POCZTY" != "-" ] && adb install -r "$APK_POCZTY" > /dev/null

# pasek stanu jak na zdjęciach reklamowych: 9:41, pełna bateria, bez powiadomień
adb shell settings put global sysui_demo_allowed 1
adb shell settings put global hide_error_dialogs 1
adb shell svc power stayon true
demo() {
  # wyjście i ponowne wejście, bo po zmianie rozmiaru ekranu tryb demo dokładał kolejne ikony Wi-Fi
  adb shell am broadcast -a com.android.systemui.demo -e command exit > /dev/null
  sleep 1
  adb shell am broadcast -a com.android.systemui.demo -e command enter > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false > /dev/null
}

# szerokość, wysokość, gęstość ekranu
ekran() {
  adb shell wm size "$1x$2"
  adb shell wm density "$3"
  sleep 4
  demo
}

# stuknięcie w element o tym tekście albo opisie (czytnik ekranu)
stuknij() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  local b
  b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -F -- "$1" | head -1 |
    grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ' || true)
  if [ -z "$b" ]; then echo "nie ma: $1" >&2; return 1; fi
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

czekajNa() {
  for _ in $(seq 1 "$2"); do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null
    if adb shell cat /sdcard/ui.xml | grep -qF -- "$1"; then return 0; fi
    sleep 1
  done
  return 1
}

zrzut() {
  mkdir -p "$(dirname "$1")"
  adb exec-out screencap -p > "$1"
  echo "zrzut: ${1#"$SKLEP"/}"
}

przygotuj() {
  adb shell cmd uimode night no > /dev/null
  adb shell am force-stop "$1"
  adb shell pm clear "$1" > /dev/null
  adb shell cmd locale set-app-locales "$1" --locales pl-PL > /dev/null 2>&1 || true
}

livepl() {
  adb shell am start -W -n "$L/eu.livepl.przegladarka.MainActivity" "$@" > /dev/null
}

tlumacz() {
  livepl --es otworz https://en.wikipedia.org/wiki/Leszno
  sleep 12
  stuknij "Przetłumacz z Google" || true
  czekajNa "Przetłumaczono" 180 || true
  sleep 3
}

# ---------- LivePL, telefon ----------
ekran 1080 1920 420
if [ "$APK_LIVEPL" != "-" ]; then
przygotuj "$L"
livepl; sleep 6; zrzut "$OBRAZY_L/phoneScreenshots/1.png"
livepl --es otworz poczta; sleep 14; zrzut "$OBRAZY_L/phoneScreenshots/2.png"
tlumacz; zrzut "$OBRAZY_L/phoneScreenshots/3.png"
livepl --es otworz liveproduction; sleep 14; zrzut "$OBRAZY_L/phoneScreenshots/4.png"
stuknij "Start"; sleep 2
stuknij "Menu przeglądarki"; sleep 2; zrzut "$OBRAZY_L/phoneScreenshots/5.png"
adb shell input keyevent KEYCODE_BACK; sleep 1
stuknij "Karty"; sleep 3; zrzut "$OBRAZY_L/phoneScreenshots/6.png"
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell cmd uimode night yes > /dev/null; sleep 5; demo
stuknij "Start" || true; sleep 3; zrzut "$OBRAZY_L/phoneScreenshots/7.png"
stuknij "Poczta" || true; sleep 12; zrzut "$OBRAZY_L/phoneScreenshots/8.png"

# ---------- LivePL, tablet 10" ----------
ekran 1440 2560 320
adb shell cmd uimode night no > /dev/null; sleep 4; demo
# karty z telefonu zostają w jego powiększeniu, więc tablet zaczyna od nowych kart
stuknij "Karty" || true; sleep 3
stuknij "Zamknij wszystkie" || true; sleep 2
stuknij "Start" || true; sleep 4; zrzut "$OBRAZY_L/tenInchScreenshots/1.png"
stuknij "Poczta" || true; sleep 12; zrzut "$OBRAZY_L/tenInchScreenshots/2.png"
tlumacz; zrzut "$OBRAZY_L/tenInchScreenshots/3.png"
adb shell cmd uimode night yes > /dev/null; sleep 5; demo
stuknij "Start" || true; sleep 4; zrzut "$OBRAZY_L/tenInchScreenshots/4.png"
ekran 1080 1920 420
fi

# ---------- Poczta LivePL, telefon ----------
if [ "$APK_POCZTY" != "-" ]; then
przygotuj "$P"
adb shell am start -W -n "$P/eu.livepl.przegladarka.PocztaActivity" > /dev/null; sleep 14
zrzut "$OBRAZY_P/phoneScreenshots/1.png"
adb shell cmd uimode night yes > /dev/null; sleep 8; demo; sleep 2
zrzut "$OBRAZY_P/phoneScreenshots/2.png"
fi

# ---------- LivePL Connect, telefon ----------
if [ -n "$APK_CONNECT" ]; then
  adb install -r "$APK_CONNECT" > /dev/null
  przygotuj "$C"
  adb shell am start -W -n "$C/eu.livepl.przegladarka.ConnectActivity" > /dev/null; sleep 12
  zrzut "$OBRAZY_C/phoneScreenshots/1.png"
  adb shell cmd uimode night yes > /dev/null; sleep 8; demo; sleep 2
  zrzut "$OBRAZY_C/phoneScreenshots/2.png"
fi

# ---------- TVML, telefon ----------
if [ -n "$APK_TVML" ]; then
  adb install -r "$APK_TVML" > /dev/null
  tvml() {
    adb shell am force-stop "$T"
    # zrzuty po polsku niezależnie od języka emulatora (TVML ma wybór PL/UK/EN, domyślnie jak w systemie)
    adb shell am start -W -n "$T/pl.tvml.MainActivity" --es jezyk pl "$@" > /dev/null
  }
  przygotuj "$T"
  tvml; sleep 12; zrzut "$OBRAZY_T/phoneScreenshots/1.png"
  tvml --es ekran artykul; sleep 10; zrzut "$OBRAZY_T/phoneScreenshots/2.png"
  tvml --es ekran pogoda; sleep 8; zrzut "$OBRAZY_T/phoneScreenshots/3.png"
  tvml --es ekran wideo; sleep 8; zrzut "$OBRAZY_T/phoneScreenshots/4.png"
  tvml --es ekran wiecej; sleep 6; zrzut "$OBRAZY_T/phoneScreenshots/5.png"
  tvml --es motyw zima; sleep 8; zrzut "$OBRAZY_T/phoneScreenshots/6.png"
  adb shell cmd uimode night yes > /dev/null; sleep 5; demo
  tvml --es motyw wigilia; sleep 8; zrzut "$OBRAZY_T/phoneScreenshots/7.png"
fi

adb shell cmd uimode night no > /dev/null
adb shell wm size reset
adb shell wm density reset
adb shell am broadcast -a com.android.systemui.demo -e command exit > /dev/null
