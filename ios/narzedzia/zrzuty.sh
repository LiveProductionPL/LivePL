#!/bin/bash
# Zrzuty ekranu LivePL z symulatora iPhone'a (na Macach GitHuba, po zbudowaniu na symulator).
#   ios/narzedzia/zrzuty.sh <ścieżka do LivePL.app> <folder na zrzuty> [Poczta.app] [Connect.app] [TVML.app]
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

# TVML (piąty argument: ścieżka do TVML.app)
if [ -n "${5:-}" ] && [ -d "$5" ]; then
  xcrun simctl terminate "$UDID" "$ID" 2>/dev/null || true
  ID="com.LiveProduction.TVML"
  xcrun simctl install "$UDID" "$5"
  zrzut 11-tvml-dzien light
  zrzut 12-tvml-noc dark
  # po polsku niezależnie od języka symulatora (-tvml-jezyk trafia do UserDefaults tylko na to uruchomienie)
  zrzut 12b-tvml-dzien-pl light -tvml-jezyk pl
  zrzut 13-tvml-pogoda light -ekran pogoda
  zrzut 13b-tvml-powiadomienia light -ekran powiadomienia -tvml-jezyk pl
  zrzut 14-tvml-artykul light -ekran artykul
  # „Większy tekst” na poziomie AX3 (górna granica w aplikacji)
  xcrun simctl ui "$UDID" content_size accessibility-extra-large || true
  zrzut 15-tvml-duzy-tekst light
  zrzut 16-tvml-artykul-duzy-tekst light -ekran artykul
  zrzut 17-tvml-pogoda-duzy-tekst dark -ekran pogoda
  xcrun simctl ui "$UDID" content_size large || true
  # karta pogody ze Startu we wszystkich scenach (GaleriaPogody.swift) i raport czytelności (galeria-pogody.py)
  xcrun simctl terminate "$UDID" "$ID" 2>/dev/null || true
  xcrun simctl launch "$UDID" "$ID" -galeria-pogody YES -tvml-jezyk pl
  DANE=$(xcrun simctl get_app_container "$UDID" "$ID" data 2>/dev/null || true)
  for i in $(seq 1 72); do
    if [ -n "$DANE" ] && [ -f "$DANE/Documents/galeria/gotowe.txt" ]; then break; fi
    sleep 5
  done
  if [ -n "$DANE" ] && [ -f "$DANE/Documents/galeria/gotowe.txt" ]; then
    cp -R "$DANE/Documents/galeria" "$WYJSCIE/galeria"
    { python3 -m venv /tmp/galeria-venv && /tmp/galeria-venv/bin/pip install --quiet pillow numpy \
      && /tmp/galeria-venv/bin/python "$(dirname "$0")/galeria-pogody.py" "$WYJSCIE/galeria"; } || echo "galeria: analiza się nie udała"
    # same tła tylko do liczenia, w artefakcie zbędne
    rm -rf "$WYJSCIE/galeria/scena"
  else
    echo "galeria: aplikacja nie skończyła (brak gotowe.txt)"
  fi
fi

# dziennik aplikacji z ostatnich minut (błędy WebKit, SwiftUI)
xcrun simctl spawn "$UDID" log show --last 5m --style compact \
  --predicate 'process == "LivePL" OR process == "Poczta" OR process == "Connect" OR process == "TVML"' > "$WYJSCIE/dziennik.txt" 2>/dev/null || true
