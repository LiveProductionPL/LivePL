'use strict';

// Rozszerzenia Chrome w LivePL (część 1, Karol 24.11): dodawanie z pliku .crx / .zip albo z folderu,
// lista z włączaniem, wyłączaniem i usuwaniem (menu ⋮ → Ustawienia → Rozszerzenia).
// Rozszerzenie trafia do danych LivePL (rozszerzenia/<folder>) i ładuje się do sesji stron, więc jego skrypty
// działają na stronach w kartach. Silnik (Electron) obsługuje tylko część możliwości Chrome: skrypty na stronach,
// tło rozszerzenia i zapis danych tak; przyciski na pasku i ich okienka dojdą w części 2.
// Rozpakowanie ZIP jest własne (bez poleceń systemu): bez ścieżek wychodzących poza folder i z limitem rozmiaru.

const { app } = require('electron');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const MAKS_ROZMIAR = 300 * 1024 * 1024;

let sesja = null;
let lista = []; // { katalog, id, nazwa, wersja, opis, uprawnienia, wlaczone, dodano, blad }

const glowny = () => path.join(app.getPath('userData'), 'rozszerzenia');
const plikListy = () => path.join(glowny(), 'lista.json');
const api = () => (sesja && sesja.extensions) || sesja; // Electron 35+: ses.extensions, wcześniej ses

function zapisz() {
  fs.mkdirSync(glowny(), { recursive: true });
  const doZapisu = lista.map(({ katalog, nazwa, wersja, opis, uprawnienia, wlaczone, dodano }) => ({ katalog, nazwa, wersja, opis, uprawnienia, wlaczone, dodano }));
  fs.writeFileSync(plikListy(), JSON.stringify(doZapisu, null, 2));
}

// ---------------------------------------------------------------- CRX i ZIP

// CRX to ZIP z nagłówkiem podpisu: wersja 3 (nagłówek protobuf) albo 2 (klucz i podpis). Podpisu nie sprawdzamy:
// rozszerzenie z pliku dodaje użytkownik świadomie, po okienku z uprawnieniami.
function zipZCrx(b) {
  if (b.length < 16 || b.toString('latin1', 0, 4) !== 'Cr24') return b;
  const wersja = b.readUInt32LE(4);
  if (wersja === 3) return b.subarray(12 + b.readUInt32LE(8));
  if (wersja === 2) return b.subarray(16 + b.readUInt32LE(8) + b.readUInt32LE(12));
  throw new Error('nieznana wersja pliku CRX');
}

function rozpakujZip(bufor, cel) {
  let koniec = -1;
  for (let i = bufor.length - 22; i >= Math.max(0, bufor.length - 65557); i--) {
    if (bufor.readUInt32LE(i) === 0x06054b50) { koniec = i; break; }
  }
  if (koniec < 0) throw new Error('to nie jest rozszerzenie (.crx ani .zip)');
  const ile = bufor.readUInt16LE(koniec + 10);
  const korzen = path.resolve(cel);
  let p = bufor.readUInt32LE(koniec + 16);
  let razem = 0;
  for (let n = 0; n < ile; n++) {
    if (bufor.readUInt32LE(p) !== 0x02014b50) throw new Error('uszkodzony plik ZIP');
    const metoda = bufor.readUInt16LE(p + 10);
    const skompresowany = bufor.readUInt32LE(p + 20);
    const rozmiar = bufor.readUInt32LE(p + 24);
    const dlNazwy = bufor.readUInt16LE(p + 28);
    const dlDodatkow = bufor.readUInt16LE(p + 30);
    const dlKomentarza = bufor.readUInt16LE(p + 32);
    const lokalny = bufor.readUInt32LE(p + 42);
    const nazwa = bufor.toString('utf8', p + 46, p + 46 + dlNazwy).replace(/\\/g, '/');
    p += 46 + dlNazwy + dlDodatkow + dlKomentarza;
    if (nazwa.endsWith('/')) continue;
    const sciezka = path.resolve(korzen, nazwa);
    if (!sciezka.startsWith(korzen + path.sep)) throw new Error('niebezpieczna ścieżka w pliku: ' + nazwa);
    if (bufor.readUInt32LE(lokalny) !== 0x04034b50) throw new Error('uszkodzony plik ZIP');
    const start = lokalny + 30 + bufor.readUInt16LE(lokalny + 26) + bufor.readUInt16LE(lokalny + 28);
    const dane = bufor.subarray(start, start + skompresowany);
    let tresc;
    if (metoda === 0) tresc = dane;
    else if (metoda === 8) tresc = zlib.inflateRawSync(dane);
    else throw new Error('nieobsługiwana kompresja w pliku: ' + nazwa);
    if (tresc.length !== rozmiar) throw new Error('uszkodzony plik w archiwum: ' + nazwa);
    razem += rozmiar;
    if (razem > MAKS_ROZMIAR) throw new Error('rozszerzenie jest za duże');
    fs.mkdirSync(path.dirname(sciezka), { recursive: true });
    fs.writeFileSync(sciezka, tresc);
  }
}

// ---------------------------------------------------------------- manifest

function czytajJson(plik) {
  return JSON.parse(fs.readFileSync(plik, 'utf8').replace(/^﻿/, ''));
}

// „__MSG_nazwa__” z _locales (najpierw polski, potem domyślny, potem angielski)
function przetlumacz(katalog, m, tekst) {
  const msg = /^__MSG_(.+)__$/.exec(tekst || '');
  if (!msg) return tekst || '';
  for (const jezyk of ['pl', m.default_locale, 'en'].filter(Boolean)) {
    try {
      const j = czytajJson(path.join(katalog, '_locales', jezyk, 'messages.json'));
      const klucz = Object.keys(j).find(k => k.toLowerCase() === msg[1].toLowerCase());
      if (klucz && j[klucz].message) return j[klucz].message;
    } catch { /* następny język */ }
  }
  return msg[1];
}

// Do czego rozszerzenie prosi o dostęp, po ludzku (do okienka przed dodaniem)
function uprawnienia(m) {
  const wynik = new Set();
  const strony = [...(m.host_permissions || []), ...(m.permissions || []).filter(x => /[:/*]/.test(x))];
  for (const cs of m.content_scripts || []) strony.push(...(cs.matches || []));
  if (strony.some(s => s === '<all_urls>' || /^\*:\/\/\*\/|^https?:\/\/\*\//.test(s))) wynik.add('wszystkie strony');
  else for (const s of strony) wynik.add(s.replace(/^\*?:\/\//, '').replace(/\/\*$/, ''));
  const nazwy = {
    tabs: 'karty', storage: 'zapisywanie danych', cookies: 'ciasteczka', history: 'historia', downloads: 'pobieranie',
    clipboardRead: 'odczyt schowka', clipboardWrite: 'zapis do schowka', webRequest: 'ruch sieciowy',
    declarativeNetRequest: 'blokowanie i zmiana ruchu sieciowego', scripting: 'skrypty na stronach', notifications: 'powiadomienia'
  };
  for (const u of m.permissions || []) if (nazwy[u]) wynik.add(nazwy[u]);
  return [...wynik];
}

function opisz(katalog) {
  const plik = path.join(katalog, 'manifest.json');
  if (!fs.existsSync(plik)) throw new Error('w środku nie ma pliku manifest.json, to nie jest rozszerzenie Chrome');
  const m = czytajJson(plik);
  if (![2, 3].includes(m.manifest_version)) throw new Error('nieznana wersja manifestu rozszerzenia');
  return {
    nazwa: przetlumacz(katalog, m, m.name) || 'Rozszerzenie',
    wersja: String(m.version || ''),
    opis: przetlumacz(katalog, m, m.description).slice(0, 300),
    uprawnienia: uprawnienia(m)
  };
}

// ---------------------------------------------------------------- dodawanie, włączanie, usuwanie

// Etap 1: rozpakowanie (z pliku) albo skopiowanie (z folderu) do nowego folderu i odczyt manifestu.
// Zwraca opis do okienka z uprawnieniami; nic się jeszcze nie ładuje.
function przygotuj(zrodlo) {
  const katalog = 'r-' + Date.now().toString(36) + '-' + crypto.randomBytes(3).toString('hex');
  const cel = path.join(glowny(), katalog);
  fs.mkdirSync(cel, { recursive: true });
  try {
    if (fs.statSync(zrodlo).isDirectory()) {
      fs.cpSync(zrodlo, cel, { recursive: true, dereference: true });
    } else {
      const b = fs.readFileSync(zrodlo);
      if (b.length > MAKS_ROZMIAR) throw new Error('rozszerzenie jest za duże');
      rozpakujZip(zipZCrx(b), cel);
    }
    // rozszerzenie spakowane razem z folderem nadrzędnym: manifest piętro niżej
    if (!fs.existsSync(path.join(cel, 'manifest.json'))) {
      const podfoldery = fs.readdirSync(cel, { withFileTypes: true }).filter(d => d.isDirectory());
      if (podfoldery.length === 1 && fs.existsSync(path.join(cel, podfoldery[0].name, 'manifest.json'))) {
        return { katalog: path.join(katalog, podfoldery[0].name), ...opisz(path.join(cel, podfoldery[0].name)), folderGlowny: katalog };
      }
    }
    return { katalog, ...opisz(cel), folderGlowny: katalog };
  } catch (e) {
    fs.rmSync(cel, { recursive: true, force: true });
    throw e;
  }
}

function odrzuc(przygotowane) {
  if (przygotowane && przygotowane.folderGlowny) fs.rmSync(path.join(glowny(), przygotowane.folderGlowny), { recursive: true, force: true });
}

async function zaladuj(r) {
  const ext = await api().loadExtension(path.join(glowny(), r.katalog), { allowFileAccess: false });
  r.id = ext.id;
  r.blad = null;
}

// Etap 2 (po zgodzie użytkownika): ładuje rozszerzenie i zapisuje na liście.
async function zatwierdz(przygotowane) {
  const r = {
    katalog: przygotowane.katalog, nazwa: przygotowane.nazwa, wersja: przygotowane.wersja, opis: przygotowane.opis,
    uprawnienia: przygotowane.uprawnienia, wlaczone: true, dodano: new Date().toISOString(), folderGlowny: przygotowane.folderGlowny
  };
  try {
    await zaladuj(r);
  } catch (e) {
    odrzuc(przygotowane);
    throw new Error('silnik LivePL nie przyjął rozszerzenia: ' + (e.message || e));
  }
  lista.push(r);
  zapisz();
  return r;
}

async function przelacz(katalog) {
  const r = lista.find(x => x.katalog === katalog);
  if (!r) return;
  if (r.wlaczone) {
    try { if (r.id) api().removeExtension(r.id); } catch { /* już wyładowane */ }
    r.wlaczone = false;
    r.id = null;
  } else {
    r.wlaczone = true;
    try { await zaladuj(r); } catch (e) { r.blad = e.message || String(e); }
  }
  zapisz();
}

function usun(katalog) {
  const i = lista.findIndex(x => x.katalog === katalog);
  if (i < 0) return;
  const r = lista[i];
  try { if (r.id) api().removeExtension(r.id); } catch { /* już wyładowane */ }
  lista.splice(i, 1);
  zapisz();
  // folder główny rozszerzenia (gdy manifest był piętro niżej, usuwamy cały rozpakowany folder)
  const folder = r.katalog.split(/[\\/]/)[0];
  fs.rmSync(path.join(glowny(), folder), { recursive: true, force: true });
}

function stan() {
  return lista.map(r => ({
    katalog: r.katalog, nazwa: r.nazwa, wersja: r.wersja, opis: r.opis || '',
    uprawnienia: r.uprawnienia || [], wlaczone: !!r.wlaczone, blad: r.blad || null
  }));
}

async function start(s) {
  sesja = s;
  try { lista = czytajJson(plikListy()); } catch { lista = []; }
  if (!Array.isArray(lista)) lista = [];
  for (const r of lista) {
    if (!r.wlaczone) continue;
    try { await zaladuj(r); } catch (e) { r.blad = e.message || String(e); }
  }
}

module.exports = { start, przygotuj, odrzuc, zatwierdz, przelacz, usun, stan, rozpakujZip, zipZCrx, opisz };
