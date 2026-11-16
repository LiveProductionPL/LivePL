'use strict';

// Zapamiętane loginy i hasła. Cały plik jest zaszyfrowany przez Windows (safeStorage, DPAPI):
// odczyta go tylko to konto Windows na tym komputerze. Strony nigdy nie dostają listy haseł;
// LivePL wypełnia formularz dopiero po kliknięciu konta w swoim okienku, i tylko na tej samej stronie
// (ten sam adres: https, host i port).

const { app, safeStorage } = require('electron');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const plik = () => path.join(app.getPath('userData'), 'hasla.bin');
let dane = null;

function wczytaj() {
  if (dane) return dane;
  dane = { wpisy: [], nigdy: [] };
  try {
    if (fs.existsSync(plik())) {
      const j = JSON.parse(safeStorage.decryptString(fs.readFileSync(plik())));
      dane.wpisy = Array.isArray(j.wpisy) ? j.wpisy : [];
      dane.nigdy = Array.isArray(j.nigdy) ? j.nigdy : [];
    }
  } catch {
    // uszkodzony albo z innego konta Windows: nie nadpisujemy go, zapisujemy obok kopię
    try { fs.renameSync(plik(), plik() + '.uszkodzony-' + Date.now()); } catch { /* trudno */ }
  }
  return dane;
}

function zapisz() {
  if (!safeStorage.isEncryptionAvailable()) throw new Error('Windows nie pozwala teraz szyfrować danych');
  const tymczasowy = plik() + '.tmp';
  fs.writeFileSync(tymczasowy, safeStorage.encryptString(JSON.stringify(dane)));
  fs.renameSync(tymczasowy, plik());
}

// adres strony do porównań: tylko https (albo http na tym komputerze), bez ścieżki
function zrodlo(url) {
  try {
    const u = new URL(url);
    const lokalny = ['localhost', '127.0.0.1', '[::1]'].includes(u.hostname);
    if (u.protocol !== 'https:' && !(u.protocol === 'http:' && lokalny)) return null;
    return u.origin;
  } catch {
    return null;
  }
}

const dostepne = () => safeStorage.isEncryptionAvailable();

function konta(origin) {
  return wczytaj().wpisy
    .filter(w => w.origin === origin)
    .sort((a, b) => (b.uzyto || 0) - (a.uzyto || 0))
    .map(w => ({ id: w.id, login: w.login }));
}

function wpis(id) {
  return wczytaj().wpisy.find(w => w.id === id) || null;
}

const czyNigdy = (origin) => wczytaj().nigdy.includes(origin);

// co zrobić z próbą logowania: nic (już jest takie samo albo „nigdy”), zapis albo aktualizacja hasła
function ocen(origin, login, haslo) {
  if (!origin || !haslo || czyNigdy(origin)) return null;
  const istnieje = wczytaj().wpisy.find(w => w.origin === origin && w.login === login);
  if (istnieje && istnieje.haslo === haslo) {
    istnieje.uzyto = Date.now();
    try { zapisz(); } catch { /* bez znaczenia */ }
    return null;
  }
  return istnieje ? 'aktualizacja' : 'nowe';
}

function dodaj(origin, login, haslo) {
  const d = wczytaj();
  const istnieje = d.wpisy.find(w => w.origin === origin && w.login === login);
  if (istnieje) {
    istnieje.haslo = haslo;
    istnieje.zmieniono = Date.now();
    istnieje.uzyto = Date.now();
  } else {
    d.wpisy.push({ id: crypto.randomBytes(6).toString('hex'), origin, login, haslo, utworzono: Date.now(), uzyto: Date.now() });
  }
  zapisz();
}

function usun(id) {
  const d = wczytaj();
  d.wpisy = d.wpisy.filter(w => w.id !== id);
  zapisz();
}

function nigdy(origin) {
  const d = wczytaj();
  if (!d.nigdy.includes(origin)) d.nigdy.push(origin);
  zapisz();
}

function usunNigdy(origin) {
  const d = wczytaj();
  d.nigdy = d.nigdy.filter(o => o !== origin);
  zapisz();
}

function uzyto(id) {
  const w = wpis(id);
  if (!w) return;
  w.uzyto = Date.now();
  try { zapisz(); } catch { /* bez znaczenia */ }
}

// do ekranu ustawień: bez haseł
function lista() {
  const d = wczytaj();
  return {
    wpisy: d.wpisy
      .map(w => ({ id: w.id, origin: w.origin, host: new URL(w.origin).host, login: w.login, uzyto: w.uzyto || w.utworzono || 0 }))
      .sort((a, b) => a.host.localeCompare(b.host) || a.login.localeCompare(b.login)),
    nigdy: d.nigdy.map(o => ({ origin: o, host: (() => { try { return new URL(o).host; } catch { return o; } })() }))
  };
}

module.exports = { zrodlo, dostepne, konta, wpis, ocen, dodaj, usun, nigdy, usunNigdy, uzyto, lista };
