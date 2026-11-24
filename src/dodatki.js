'use strict';

// Dodatki LivePL: nieobowiązkowe części przeglądarki w osobnych plikach .livepl-dodatek
// (Karol 24.11: „miał być osobny plik, jak ktoś chce zainstalować, to może, ale nie musi; wersja A miała być domyślna”).
// Domyślny LivePL ma wtyczki (src/wtyczki.js). Dodatek, np. Rozszerzenia Chrome, instaluje tylko ten, kto chce:
// pobiera plik ze strony app.livepl.eu (LivePL sam proponuje instalację po pobraniu) albo wybiera
// menu ⋮ → Ustawienia → Dodatki → „Zainstaluj z pliku…”. Usunąć go można w tym samym miejscu.
//
// Plik dodatku to JSON { format: 1, dodatek: "<JSON jako tekst>", podpis: "<base64>" }. Podpis Ed25519 kluczem
// aktualizacji LivePL obejmuje przedrostek PRZEDROSTEK i tekst dodatku, więc żaden inny podpisany plik (manifest
// aktualizacji, lista wtyczek) nie przejdzie jako dodatek. Dodatek to jeden moduł Node (kod) z funkcjami start(sesja)
// i zatrzymaj(); działa w procesie głównym jak reszta LivePL, dlatego bez poprawnego podpisu nic się nie ładuje.
// Kod jest uruchamiany z tego samego, sprawdzonego bufora (bez ponownego czytania z dysku). Budowa: npm run dodatek.

const { app } = require('electron');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const Module = require('module');
const { KLUCZ_PUBLICZNY } = require('./klucz-aktualizacji');
const { porownaj } = require('./aktualizacja-wgraj');

const PRZEDROSTEK = 'LivePL-dodatek-1\n';
const ROZSZERZENIE = '.livepl-dodatek';
const MAKS = 5 * 1024 * 1024;

let sesja = null;
const zainstalowane = new Map(); // id → { opis, modul, blad, poRestarcie }

const katalog = () => path.join(app.getPath('userData'), 'dodatki');
const plikDodatku = (id) => path.join(katalog(), id + ROZSZERZENIE);

// Sprawdza podpis i budowę; zwraca opis dodatku z kodem albo rzuca błąd zrozumiały dla użytkownika.
function odczytaj(bufor, klucz = KLUCZ_PUBLICZNY) {
  if (bufor.length > MAKS) throw new Error('plik dodatku jest za duży');
  let j;
  try { j = JSON.parse(bufor.toString('utf8')); } catch { throw new Error('to nie jest plik dodatku LivePL'); }
  if (!j || j.format !== 1 || typeof j.dodatek !== 'string' || typeof j.podpis !== 'string') throw new Error('to nie jest plik dodatku LivePL');
  const dane = Buffer.concat([Buffer.from(PRZEDROSTEK, 'utf8'), Buffer.from(j.dodatek, 'utf8')]);
  if (!crypto.verify(null, dane, klucz, Buffer.from(j.podpis, 'base64'))) {
    throw new Error('podpis dodatku się nie zgadza: plik nie pochodzi od Live Production albo jest uszkodzony');
  }
  const d = JSON.parse(j.dodatek);
  if (!d || !/^[a-z0-9-]{1,40}$/.test(d.id) || typeof d.nazwa !== 'string' || typeof d.wersja !== 'string' || typeof d.kod !== 'string') {
    throw new Error('nieznany format dodatku');
  }
  return { id: d.id, nazwa: d.nazwa, wersja: d.wersja, opis: typeof d.opis === 'string' ? d.opis : '', minLivePL: String(d.minLivePL || '0.0.0'), kod: d.kod };
}

function zgodny(d) {
  if (porownaj(app.getVersion(), d.minLivePL) < 0) throw new Error(`dodatek „${d.nazwa}” wymaga LivePL ${d.minLivePL} albo nowszego`);
}

async function uruchom(d) {
  const nazwa = path.join(katalog(), d.id + '.js'); // tylko nazwa w komunikatach błędów; kod idzie z pamięci
  const m = new Module(nazwa, module);
  m.filename = nazwa;
  m.paths = module.paths;
  m._compile(d.kod, nazwa);
  const modul = m.exports || {};
  if (typeof modul.start === 'function') await modul.start(sesja);
  return modul;
}

async function wczytaj(id) {
  let opis = null;
  try {
    opis = odczytaj(fs.readFileSync(plikDodatku(id)));
    if (opis.id !== id) throw new Error('plik dodatku ma inną nazwę niż jego zawartość');
    zgodny(opis);
    const modul = await uruchom(opis);
    zainstalowane.set(id, { opis, modul, blad: null, poRestarcie: false });
  } catch (e) {
    zainstalowane.set(id, { opis: opis || { id, nazwa: id, wersja: '', opis: '' }, modul: null, blad: e.message || String(e), poRestarcie: false });
  }
}

// ---------------------------------------------------------------- instalacja i usuwanie

// Etap 1: sprawdza plik i zwraca, co to za dodatek (do okienka „Zainstalować?”).
function przygotuj(sciezka) {
  const bufor = fs.readFileSync(sciezka);
  const d = odczytaj(bufor);
  zgodny(d);
  const obecny = zainstalowane.get(d.id);
  return { id: d.id, nazwa: d.nazwa, wersja: d.wersja, opis: d.opis, obecna: obecny && !obecny.blad ? obecny.opis.wersja : null, bufor };
}

// Etap 2 (po zgodzie): zapisuje plik i uruchamia dodatek. Nowsza wersja działającego dodatku: po ponownym uruchomieniu.
async function zainstaluj(p) {
  const d = odczytaj(p.bufor);
  fs.mkdirSync(katalog(), { recursive: true });
  const tymczasowy = plikDodatku(d.id) + '.nowy';
  fs.writeFileSync(tymczasowy, p.bufor);
  fs.renameSync(tymczasowy, plikDodatku(d.id));
  const obecny = zainstalowane.get(d.id);
  if (obecny && obecny.modul) {
    obecny.poRestarcie = true;
    obecny.nowaWersja = d.wersja;
    return { nazwa: d.nazwa, poRestarcie: true };
  }
  await wczytaj(d.id);
  const z = zainstalowane.get(d.id);
  if (z.blad) throw new Error(z.blad);
  return { nazwa: d.nazwa, poRestarcie: false };
}

function usun(id) {
  const z = zainstalowane.get(id);
  if (z && z.modul && typeof z.modul.zatrzymaj === 'function') {
    try { z.modul.zatrzymaj(); } catch { /* dodatek i tak znika */ }
  }
  zainstalowane.delete(id);
  fs.rmSync(plikDodatku(id), { force: true });
}

// ---------------------------------------------------------------- dla reszty LivePL

const modul = (id) => {
  const z = zainstalowane.get(id);
  return z && z.modul ? z.modul : null;
};

const czyPlikDodatku = (nazwa) => String(nazwa || '').toLowerCase().endsWith(ROZSZERZENIE);

function stan() {
  return [...zainstalowane.values()].map(z => ({
    id: z.opis.id, nazwa: z.opis.nazwa, wersja: z.opis.wersja, opis: z.opis.opis || '',
    blad: z.blad, poRestarcie: !!z.poRestarcie, nowaWersja: z.nowaWersja || null
  }));
}

async function start(opcje = {}) {
  sesja = opcje.sesja || null;
  let pliki = [];
  try { pliki = fs.readdirSync(katalog()).filter(czyPlikDodatku); } catch { /* brak dodatków */ }
  for (const plik of pliki.sort()) {
    const id = plik.slice(0, -ROZSZERZENIE.length);
    if (/^[a-z0-9-]{1,40}$/.test(id)) await wczytaj(id);
  }
}

module.exports = { start, przygotuj, zainstaluj, usun, modul, stan, czyPlikDodatku, odczytaj, PRZEDROSTEK, ROZSZERZENIE };
