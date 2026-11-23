#!/usr/bin/env bash
# Wgrywa listę wtyczek LivePL na aktualizacje.livepl.eu/livepl/wtyczki/. Uruchamiaj przez sudo:
#   sudo bash wgraj-livepl-wtyczki.sh <folder z plikami> <sha lista.json> <sha lista.json.sig>
# Podpis sprawdza LivePL u użytkownika (klucz publiczny aktualizacji); tu sprawdzamy sumy i format.
# Najpierw podpis, potem lista pod nazwami tymczasowymi i mv. Poprzednie pliki do /root/kopie-livepl-<data>.
set -euo pipefail
STAGE=${1:?folder z plikami}; SHA_L=${2:?}; SHA_S=${3:?}
CEL=/home/admin/domains/aktualizacje.livepl.eu/public_html/livepl/wtyczki
for f in lista.json lista.json.sig; do test -s "$STAGE/$f" || { echo "brak $f"; exit 1; }; done
suma() { sha256sum "$1" | cut -d' ' -f1; }
[ "$(suma "$STAGE/lista.json")" = "$SHA_L" ] || { echo "suma lista.json się nie zgadza"; exit 1; }
[ "$(suma "$STAGE/lista.json.sig")" = "$SHA_S" ] || { echo "suma lista.json.sig się nie zgadza"; exit 1; }
grep -q '"format": *1' "$STAGE/lista.json" || { echo "lista.json w nieznanym formacie"; exit 1; }
echo "sumy zgodne"

mkdir -p "$CEL"; chown admin:admin "$CEL"; chmod 755 "$CEL"
if [ -e "$CEL/lista.json" ]; then
  KOPIA=/root/kopie-livepl-$(date +%Y%m%d)/wtyczki-przed-$(date +%H%M%S)
  mkdir -p "$KOPIA"; chmod 700 "$(dirname "$KOPIA")"
  cp -p "$CEL/lista.json" "$CEL/lista.json.sig" "$KOPIA/" 2>/dev/null || true
  echo "kopia poprzedniej listy: $KOPIA"
fi
install -m 0644 -o admin -g admin "$STAGE/lista.json.sig" "$CEL/.lista.json.sig.nowy"
install -m 0644 -o admin -g admin "$STAGE/lista.json" "$CEL/.lista.json.nowy"
mv -f "$CEL/.lista.json.sig.nowy" "$CEL/lista.json.sig"
mv -f "$CEL/.lista.json.nowy" "$CEL/lista.json"
[ "$(suma "$CEL/lista.json")" = "$SHA_L" ] && [ "$(suma "$CEL/lista.json.sig")" = "$SHA_S" ] || { echo "BŁĄD: sumy po wgraniu"; exit 1; }
ls -la "$CEL"
echo "wtyczki LivePL: wgrane"
