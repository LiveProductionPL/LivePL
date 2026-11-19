// Ikony interfejsu LivePL na Androida: Material Symbols (Google, licencja Apache 2.0) jako VectorDrawable.
// Pobiera każdą ikonę z fonts.gstatic.com i zapisuje w android/app/src/main/res/drawable/ic_<nazwa>.xml.
// Uruchom z katalogu przegladarka-livepl:  node android/narzedzia/ikony.mjs

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const KATALOG = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'app', 'src', 'main', 'res', 'drawable');

// nazwa w LivePL → [symbol Material, wariant]; fill1 = wypełniona, jak .fill na iPhonie
const IKONY = {
  szukaj: ['search', 'default'],
  zamknij: ['close', 'default'],
  wyczysc: ['cancel', 'fill1'],
  wstecz: ['arrow_back', 'default'],
  dalej: ['arrow_forward', 'default'],
  odswiez: ['refresh', 'default'],
  menu: ['more_vert', 'default'],
  udostepnij: ['share', 'default'],
  znajdz: ['find_in_page', 'default'],
  dodaj: ['add', 'default'],
  kopiuj: ['content_copy', 'default'],
  przegladarka: ['open_in_browser', 'default'],
  klodka: ['lock', 'fill1'],
  uwaga: ['warning', 'fill1'],
  usun: ['delete', 'default'],
  poczta: ['mail', 'default'],
  klient: ['account_circle', 'default'],
  lekcje: ['smart_display', 'default'],
  egzamin: ['school', 'default'],
  serwer: ['dns', 'default'],
  tarcza: ['verified_user', 'default'],
  strona: ['public', 'default'],
  dzien: ['light_mode', 'default'],
  noc: ['dark_mode', 'default'],
  auto: ['contrast', 'default'],
  brak_sieci: ['wifi_off', 'default'],
  ok: ['check', 'default'],
  rozwin: ['expand_more', 'default'],
  w_gore: ['keyboard_arrow_up', 'default'],
  w_dol: ['keyboard_arrow_down', 'default'],
  tlumacz: ['translate', 'default'],
};

fs.mkdirSync(KATALOG, { recursive: true });
for (const [nazwa, [symbol, wariant]] of Object.entries(IKONY)) {
  const adres = `https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/${symbol}/${wariant}/24px.svg`;
  const odp = await fetch(adres);
  if (!odp.ok) throw new Error(`${symbol}: HTTP ${odp.status}`);
  const svg = await odp.text();
  const sciezki = [...svg.matchAll(/<path[^>]*\sd="([^"]+)"/g)].map((m) => m[1]);
  if (!sciezki.length) throw new Error(`${symbol}: brak ścieżki w SVG`);
  // Material Symbols rysują w polu 0 -960 960 960, więc całość przesuwamy o 960 w dół
  const xml = `<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols „${symbol}” (Google, Apache 2.0); plik tworzy android/narzedzia/ikony.mjs -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
${sciezki.map((d) => `        <path android:fillColor="#FF000000" android:pathData="${d}" />`).join('\n')}
    </group>
</vector>
`;
  fs.writeFileSync(path.join(KATALOG, `ic_${nazwa}.xml`), xml);
  console.log(`ic_${nazwa}.xml  ← ${symbol}`);
}
