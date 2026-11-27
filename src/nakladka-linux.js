'use strict';

// Linux: pliki programu w AppImage są tylko do odczytu, a w pakiecie .deb należą do administratora (/opt/LivePL).
// Aktualizacje (te same podpisane paczki co w Windows) trafiają więc do folderu użytkownika
// ~/.config/LivePL/program, a start.js wczytuje LivePL stamtąd, gdy ta wersja jest nowsza od wbudowanej.
// Nowy plik AppImage albo nowy pakiet .deb z wyższą wersją wygrywa: stara nakładka jest wtedy usuwana.
// Wbudowane pliki nigdy się nie zmieniają, więc zawsze można do nich wrócić (usuwając folder program).

const fs = require('fs');
const path = require('path');
const { porownaj } = require('./aktualizacja-wgraj');

const KATALOGI = ['src', 'ui', 'assets'];
const PLIKI = ['package.json', 'programy.json', 'README.md'];

function wersja(katalog) {
  try { return JSON.parse(fs.readFileSync(path.join(katalog, 'package.json'), 'utf8')).version || null; } catch { return null; }
}

function kompletna(katalog) {
  return !!wersja(katalog) && fs.existsSync(path.join(katalog, 'src', 'main.js')) && fs.existsSync(path.join(katalog, 'ui'));
}

function skopiuj(zrodlo, cel) {
  const tymczasowy = cel + '.nowy';
  fs.rmSync(tymczasowy, { recursive: true, force: true });
  fs.mkdirSync(tymczasowy, { recursive: true });
  for (const k of KATALOGI) {
    const z = path.join(zrodlo, k);
    if (fs.existsSync(z)) fs.cpSync(z, path.join(tymczasowy, k), { recursive: true });
  }
  for (const p of PLIKI) {
    const z = path.join(zrodlo, p);
    if (fs.existsSync(z)) fs.copyFileSync(z, path.join(tymczasowy, p));
  }
  fs.rmSync(cel, { recursive: true, force: true });
  fs.renameSync(tymczasowy, cel);
}

// Zwraca folder, z którego trzeba wczytać LivePL i do którego wolno wgrać aktualizację.
//  - jest pobrana aktualizacja albo prośba o powrót: nakładka (przy pierwszej aktualizacji kopia wbudowanych plików);
//  - nakładka jest nowsza od wbudowanej wersji: nakładka;
//  - w pozostałych przypadkach wbudowane pliki (nakładkę w starszej lub tej samej wersji usuwamy).
function przygotuj(wbudowany, katalogDanych) {
  const nakladka = path.join(katalogDanych, 'program');
  const baza = path.join(katalogDanych, 'aktualizacja');
  const vWbud = wersja(wbudowany);
  const jestNakladka = kompletna(nakladka);
  const vNakl = jestNakladka ? wersja(nakladka) : null;
  const czeka = fs.existsSync(path.join(baza, 'gotowa', 'gotowa.json')) || (jestNakladka && fs.existsSync(path.join(baza, 'przywroc.json')));

  if (jestNakladka && porownaj(vNakl, vWbud) > 0) return nakladka;
  if (czeka) {
    // pierwsza aktualizacja (albo nowy AppImage/.deb wyprzedził nakładkę): zaczynamy od wbudowanych plików
    skopiuj(wbudowany, nakladka);
    return nakladka;
  }
  if (fs.existsSync(nakladka)) fs.rmSync(nakladka, { recursive: true, force: true });
  return wbudowany;
}

// Po przedStartem nakładka może mieć tę samą wersję co wbudowana (powrót do poprzedniej albo odrzucona starsza
// paczka): wtedy ją usuwamy. Zwraca folder, z którego trzeba wczytać LivePL.
function posprzataj(wbudowany, katalogDanych) {
  const nakladka = path.join(katalogDanych, 'program');
  if (kompletna(nakladka) && porownaj(wersja(nakladka), wersja(wbudowany)) > 0) return nakladka;
  fs.rmSync(nakladka, { recursive: true, force: true });
  return wbudowany;
}

module.exports = { przygotuj, posprzataj, wersja };
