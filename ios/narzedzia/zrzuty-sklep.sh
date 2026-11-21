#!/bin/bash
# Zrzuty do App Store w wymaganych rozmiarach: iPhone 6,9" (1320×2868) i iPad 13" (2064×2752).
#   ios/narzedzia/zrzuty-sklep.sh <LivePL.app> <Poczta.app> <folder> [Connect.app] [TVML.app]
# Na Macach GitHuba, gdy opis zmiany zawiera [zrzuty-sklep]. Wgrywa je ios/narzedzia/sklep-zrzuty.mjs.
set -euo pipefail

LIVEPL="$1"
POCZTA="$2"
WYJSCIE="$3"
CONNECT="${4:-}"
TVML="${5:-}"
mkdir -p "$WYJSCIE"
# polski telefon: język, format daty i 24 h, godziny w aplikacjach jak w Lesznie (Mac GitHuba ma UTC)
export SIMCTL_CHILD_TZ=Europe/Warsaw
PO_POLSKU=(-AppleLanguages "(pl)" -AppleLocale pl_PL)

RUNTIME=$(xcrun simctl list runtimes -j | python3 -c '
import json, sys
r = [x for x in json.load(sys.stdin)["runtimes"] if x.get("isAvailable") and x["platform"] == "iOS"]
r.sort(key=lambda x: [int(p) for p in x["version"].split(".")])
print(r[-1]["identifier"] if r else "")
')
typ() {
  xcrun simctl list devicetypes -j | python3 -c "
import json, re, sys
t = [d for d in json.load(sys.stdin)['devicetypes'] if re.search(sys.argv[1], d['name'])]
# najnowszy model (największy numer w nazwie), np. iPhone 17 Pro Max zamiast iPhone 11 Pro Max
num = lambda d: [int(x) for x in re.findall(r'\d+', d['name'])] or [0]
t.sort(key=num)
print(t[-1]['identifier'] if t else '')
" "$1"
}

zrob() {
  local nazwa="$1" wzorTypu="$2" katalog="$WYJSCIE/$1"
  local TYP UDID
  TYP=$(typ "$wzorTypu")
  [ -z "$TYP" ] && { echo "Brak symulatora: $wzorTypu" >&2; return; }
  UDID=$(xcrun simctl create "Sklep $nazwa" "$TYP" "$RUNTIME")
  mkdir -p "$katalog"
  xcrun simctl boot "$UDID"
  xcrun simctl bootstatus "$UDID" -b
  # język systemu; pasek stanu (data na iPadzie) bierze go dopiero po ponownym rozruchu
  xcrun simctl spawn "$UDID" defaults write -g AppleLanguages -array pl
  xcrun simctl spawn "$UDID" defaults write -g AppleLocale pl_PL
  xcrun simctl shutdown "$UDID"
  xcrun simctl boot "$UDID"
  xcrun simctl bootstatus "$UDID" -b
  xcrun simctl status_bar "$UDID" override --time "9:41" --batteryState charged --batteryLevel 100 \
    --cellularMode active --cellularBars 4 --wifiBars 3 || true
  xcrun simctl install "$UDID" "$LIVEPL"
  xcrun simctl install "$UDID" "$POCZTA"
  if [ -n "$CONNECT" ]; then xcrun simctl install "$UDID" "$CONNECT"; fi
  if [ -n "$TVML" ]; then xcrun simctl install "$UDID" "$TVML"; fi

  ujecie() {
    local plik="$1" id="$2" wyglad="$3"
    shift 3
    xcrun simctl ui "$UDID" appearance "$wyglad"
    xcrun simctl terminate "$UDID" "$id" 2>/dev/null || true
    xcrun simctl launch "$UDID" "$id" "${PO_POLSKU[@]}" "$@"
    sleep 15
    xcrun simctl io "$UDID" screenshot "$katalog/$plik.png"
    echo "zrzut $nazwa: $plik"
  }

  ujecie livepl-1-start eu.livepl.przegladarka light
  ujecie livepl-3-liveproduction eu.livepl.przegladarka light -otworz liveproduction
  ujecie livepl-4-start-noc eu.livepl.przegladarka dark
  ujecie livepl-5-poczta eu.livepl.przegladarka light -otworz poczta
  xcrun simctl terminate "$UDID" eu.livepl.przegladarka 2>/dev/null || true
  ujecie poczta-1-dzien eu.livepl.poczta light
  ujecie poczta-2-noc eu.livepl.poczta dark
  if [ -n "$CONNECT" ]; then
    xcrun simctl terminate "$UDID" eu.livepl.poczta 2>/dev/null || true
    ujecie connect-1-dzien eu.livepl.connect light
    ujecie connect-2-noc eu.livepl.connect dark
  fi
  if [ -n "$TVML" ]; then
    # TVML pobiera wiadomości, pogodę i wygląd z tvml.pl (wersja z panelu); zakladka2 to druga zakładka z panelu (np. Sport)
    xcrun simctl terminate "$UDID" eu.livepl.connect 2>/dev/null || true
    ujecie tvml-1-start com.LiveProduction.TVML light
    ujecie tvml-2-artykul com.LiveProduction.TVML light -ekran artykul
    ujecie tvml-3-pogoda com.LiveProduction.TVML light -ekran pogoda
    ujecie tvml-4-zakladka com.LiveProduction.TVML light -ekran zakladka2
    ujecie tvml-5-noc com.LiveProduction.TVML dark
    ujecie tvml-6-wideo com.LiveProduction.TVML dark -ekran wideo
  fi

  xcrun simctl shutdown "$UDID" || true
  xcrun simctl delete "$UDID" || true
}

# URZADZENIE=iphone-69 albo ipad-13: tylko jeden (workflow robi je równolegle); bez niego oba po kolei
case "${URZADZENIE:-oba}" in
  iphone-69) zrob iphone-69 'iPhone [0-9]+ Pro Max' ;;
  ipad-13) zrob ipad-13 'iPad Pro 13-inch' ;;
  *) zrob iphone-69 'iPhone [0-9]+ Pro Max'; zrob ipad-13 'iPad Pro 13-inch' ;;
esac

python3 - "$WYJSCIE" <<'EOF'
import struct, sys, pathlib
for p in sorted(pathlib.Path(sys.argv[1]).rglob('*.png')):
    with open(p, 'rb') as f:
        f.read(16)
        w, h = struct.unpack('>II', f.read(8))
    print(f'{p.relative_to(sys.argv[1])}: {w}x{h}')
EOF
