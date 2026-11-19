#!/bin/bash
# Zrzuty ekranu LivePL z symulatora iPhone'a (na Macach GitHuba, po zbudowaniu na symulator).
#   ios/narzedzia/zrzuty.sh <ścieżka do LivePL.app> <folder na zrzuty> [Poczta.app] [Connect.app]
set -euo pipefail

APP="$1"
WYJSCIE="$2"
ID="eu.livepl.przegladarka"
mkdir -p "$WYJSCIE"

# najnowszy symulator iPhone'a w wersji Pro (bez Max), a gdy go nie ma, dowolny iPhone
UDID=$(xcrun simctl list devices available -j | python3 -c '
import json, re, sys
dane = json.load(sys.stdin)["devices"]
def wersja(r):
    m = re.search(r"iOS-(\d+)-(\d+)", r)
    return (int(m.group(1)), int(m.group(2))) if m else (0, 0)
kandydaci = []
for runtime, lista in dane.items():
    if "iOS" not in runtime:
        continue
    for u in lista:
        if u["name"].startswith("iPhone"):
            pro = "Pro" in u["name"] and "Max" not in u["name"]
            kandydaci.append((pro, wersja(runtime), u["name"], u["udid"]))
kandydaci.sort()
print(kandydaci[-1][3] if kandydaci else "")
')
if [ -z "$UDID" ]; then
  echo "Brak symulatora iPhone'a" >&2
  exit 1
fi
xcrun simctl list devices | grep "$UDID" || true

xcrun simctl boot "$UDID" 2>/dev/null || true
xcrun simctl bootstatus "$UDID" -b
xcrun simctl status_bar "$UDID" override --time "9:41" --batteryState charged --batteryLevel 100 \
  --cellularMode active --cellularBars 4 --wifiBars 3 || true
xcrun simctl install "$UDID" "$APP"

zrzut() {
  local nazwa="$1" wyglad="$2"
  shift 2
  xcrun simctl ui "$UDID" appearance "$wyglad"
  xcrun simctl terminate "$UDID" "$ID" 2>/dev/null || true
  xcrun simctl launch "$UDID" "$ID" "$@"
  sleep 14
  xcrun simctl io "$UDID" screenshot "$WYJSCIE/$nazwa.png"
  echo "zrzut: $nazwa"
}

zrzut 1-start-dzien light
zrzut 2-start-noc dark
zrzut 3-poczta-dzien light -otworz poczta
zrzut 4-tvml-noc dark -otworz tvml
zrzut 5-liveproduction-dzien light -otworz liveproduction
zrzut 6-tlumacz-dzien light -otworz https://en.wikipedia.org/wiki/Poland

# Poczta LivePL (trzeci argument: ścieżka do Poczta.app)
if [ -n "${3:-}" ] && [ -d "$3" ]; then
  xcrun simctl terminate "$UDID" "$ID" 2>/dev/null || true
  ID="eu.livepl.poczta"
  xcrun simctl install "$UDID" "$3"
  zrzut 7-poczta-aplikacja-dzien light
  zrzut 8-poczta-aplikacja-noc dark
fi

# LivePL Connect (czwarty argument: ścieżka do Connect.app)
if [ -n "${4:-}" ] && [ -d "$4" ]; then
  xcrun simctl terminate "$UDID" "$ID" 2>/dev/null || true
  ID="eu.livepl.connect"
  xcrun simctl install "$UDID" "$4"
  zrzut 9-connect-dzien light
  zrzut 10-connect-noc dark
fi

# dziennik aplikacji z ostatnich minut (błędy WebKit, SwiftUI)
xcrun simctl spawn "$UDID" log show --last 5m --style compact \
  --predicate 'process == "LivePL" OR process == "Poczta" OR process == "Connect"' > "$WYJSCIE/dziennik.txt" 2>/dev/null || true
