'use strict';

// Podmiana plików LivePL przy starcie (wywołuje start.js, zanim wczyta się main.js).
// Przed podmianą zostaje kopia obecnej wersji, do której można wrócić.

const fs = require('fs');
const path = require('path');

const KATALOGI = ['src', 'ui', 'assets'];
const PLIKI = ['package.json', 'programy.json', 'README.md'];

function porownaj(a, b) {
  const x = String(a).split('.').map(n => parseInt(n, 10) || 0);
  const y = String(b).split('.').map(n => parseInt(n, 10) || 0);
  for (let i = 0; i < 3; i++) {
    const d = (x[i] || 0) - (y[i] || 0);
    if (d) return d > 0 ? 1 : -1;
  }
  return 0;
}

// w paczce wolno mieć tylko pliki programu, nigdy ścieżki wychodzące poza jego folder
function dozwolonaSciezka(p) {
  if (typeof p !== 'string' || !p || p.length > 260 || p.includes('\\') || p.startsWith('/') || p.includes(':')) return false;
  const czesci = p.split('/');
  if (czesci.some(c => c === '' || c === '.' || c === '..')) return false;
  if (czesci.length === 1) return PLIKI.includes(p);
  return KATALOGI.includes(czesci[0]);
}

function wersjaProgramu(katalogProgramu) {
  return JSON.parse(fs.readFileSync(path.join(katalogProgramu, 'package.json'), 'utf8')).version;
}

function zrobKopie(katalogProgramu, cel, wersja) {
  fs.rmSync(cel, { recursive: true, force: true });
  fs.mkdirSync(path.join(cel, 'pliki'), { recursive: true });
  for (const k of KATALOGI) {
    const z = path.join(katalogProgramu, k);
    if (fs.existsSync(z)) fs.cpSync(z, path.join(cel, 'pliki', k), { recursive: true });
  }
  for (const p of PLIKI) {
    const z = path.join(katalogProgramu, p);
    if (fs.existsSync(z)) fs.copyFileSync(z, path.join(cel, 'pliki', p));
  }
  fs.writeFileSync(path.join(cel, 'wersja.json'), JSON.stringify({ wersja, kiedy: new Date().toISOString() }));
}

// wgrywa pliki z folderu `zrodlo` tak, żeby katalogi programu miały dokładnie ich zawartość
function wgrajPliki(zrodlo, katalogProgramu) {
  for (const k of KATALOGI) {
    const nowy = path.join(zrodlo, k);
    const stary = path.join(katalogProgramu, k);
    if (!fs.existsSync(nowy)) continue;
    fs.rmSync(stary, { recursive: true, force: true });
    fs.cpSync(nowy, stary, { recursive: true });
  }
  for (const p of PLIKI) {
    const nowy = path.join(zrodlo, p);
    if (fs.existsSync(nowy)) fs.copyFileSync(nowy, path.join(katalogProgramu, p));
  }
}

function zapiszDziennik(baza, wpis) {
  try {
    fs.appendFileSync(path.join(baza, 'dziennik.txt'), `${new Date().toISOString()} ${wpis}\n`);
  } catch { /* bez znaczenia */ }
}

// Zwraca nową wersję, gdy coś zostało podmienione (wtedy trzeba uruchomić program jeszcze raz).
function przedStartem(katalogProgramu, katalogDanych) {
  const baza = path.join(katalogDanych, 'aktualizacja');
  const obecna = wersjaProgramu(katalogProgramu);

  // 1. powrót do poprzedniej wersji, jeśli ktoś o niego poprosił
  const flaga = path.join(baza, 'przywroc.json');
  if (fs.existsSync(flaga)) {
    fs.rmSync(flaga, { force: true });
    const kopia = path.join(baza, 'poprzednia');
    try {
      const { wersja } = JSON.parse(fs.readFileSync(path.join(kopia, 'wersja.json'), 'utf8'));
      wgrajPliki(path.join(kopia, 'pliki'), katalogProgramu);
      fs.rmSync(path.join(baza, 'gotowa'), { recursive: true, force: true });
      zapiszDziennik(baza, `powrót z ${obecna} do ${wersja}`);
      return wersja;
    } catch (e) {
      zapiszDziennik(baza, `powrót nieudany: ${e.message}`);
    }
  }

  // 2. pobrana i sprawdzona nowa wersja
  const gotowa = path.join(baza, 'gotowa');
  let opis;
  try {
    opis = JSON.parse(fs.readFileSync(path.join(gotowa, 'gotowa.json'), 'utf8'));
  } catch {
    return null;
  }
  if (porownaj(opis.wersja, obecna) <= 0 || !Array.isArray(opis.pliki) || !opis.pliki.every(dozwolonaSciezka)) {
    fs.rmSync(gotowa, { recursive: true, force: true });
    return null;
  }
  zrobKopie(katalogProgramu, path.join(baza, 'poprzednia'), obecna);
  wgrajPliki(path.join(gotowa, 'pliki'), katalogProgramu);
  fs.rmSync(gotowa, { recursive: true, force: true });
  zapiszDziennik(baza, `aktualizacja z ${obecna} do ${opis.wersja}`);
  return opis.wersja;
}

module.exports = { porownaj, dozwolonaSciezka, przedStartem };
