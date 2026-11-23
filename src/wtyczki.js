'use strict';

// Wtyczki LivePL: małe dodatki i poprawki stron bez nowej wersji przeglądarki
// (Karol, 24.11: „opcja dogrywania pluginów jako rozszerzenia, a nie przebudowa zawsze całej przeglądarki”).
//
// Na serwerze aktualizacji leżą wtyczki/lista.json i jej podpis wtyczki/lista.json.sig (Ed25519, ten sam klucz
// co aktualizacje LivePL; publikuje je `npm run wtyczki`). Lista opisuje wtyczki: id, nazwa, opis, wersja,
// czy domyślnie włączona i na jakich stronach działa (styl CSS i skrypt). LivePL sprawdza podpis, zapisuje listę
// w swoich danych (działa także bez sieci) i dokłada włączone wtyczki do każdej pasującej strony po jej wczytaniu.
// Skrypt wtyczki działa w osobnym, izolowanym świecie: widzi stronę (DOM), ale nie jej zmienne ani skrypty.
// Wtyczki włącza i wyłącza użytkownik w menu ⋮ → Ustawienia → Wtyczki.

const { app, net } = require('electron');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { KLUCZ_PUBLICZNY } = require('./klucz-aktualizacji');

const ADRES = (process.env.LIVEPL_WTYCZKI || 'https://aktualizacje.livepl.eu/livepl/wtyczki/').replace(/\/?$/, '/');
const SWIAT = 1500; // izolowany świat skryptów wtyczek (999 zajmuje preload Electrona)
const MAKS_LISTA = 1024 * 1024;
const MAKS_KOD = 200 * 1024;

let lista = { format: 1, data: null, wtyczki: [] };
let stanPobrania = { sprawdzono: null, blad: null };
let czyWlaczona = () => null; // z ustawień: true/false albo null (nie ruszane = domyślnie)
let sluchacz = () => {};

const katalog = () => path.join(app.getPath('userData'), 'wtyczki');

// ---------------------------------------------------------------- lista z podpisem

// Sprawdza podpis i budowę listy; zwraca listę albo rzuca błąd. Wtyczka z niepoprawnym opisem jest pomijana.
function odczytaj(tresc, podpisTekst) {
  if (tresc.length > MAKS_LISTA) throw new Error('lista wtyczek jest za duża');
  const podpis = Buffer.from(String(podpisTekst).trim(), 'base64');
  if (!crypto.verify(null, tresc, KLUCZ_PUBLICZNY, podpis)) throw new Error('podpis listy wtyczek się nie zgadza');
  const j = JSON.parse(tresc.toString('utf8'));
  if (j.format !== 1 || !Array.isArray(j.wtyczki)) throw new Error('nieznany format listy wtyczek');
  const wtyczki = j.wtyczki.filter(w =>
    w && /^[a-z0-9-]{1,40}$/.test(w.id) && typeof w.nazwa === 'string' && Array.isArray(w.strony) &&
    w.strony.every(s => typeof s === 'string' && /^(\*|\*\.)?[a-z0-9.-]*$/i.test(s)) &&
    (w.css === undefined || (typeof w.css === 'string' && w.css.length <= MAKS_KOD)) &&
    (w.js === undefined || (typeof w.js === 'string' && w.js.length <= MAKS_KOD)));
  return { format: 1, data: typeof j.data === 'string' ? j.data : null, wtyczki };
}

function wczytajZapisana() {
  try {
    const tresc = fs.readFileSync(path.join(katalog(), 'lista.json'));
    const podpis = fs.readFileSync(path.join(katalog(), 'lista.json.sig'), 'utf8');
    lista = odczytaj(tresc, podpis);
  } catch { /* brak zapisanej listy albo uszkodzona: pobierze się z serwera */ }
}

async function pobierz(url) {
  if (!/^https:\/\//i.test(url) && !/^http:\/\/127\.0\.0\.1[:/]/.test(url)) throw new Error('niedozwolony adres wtyczek');
  const odp = await net.fetch(url, { cache: 'no-store' });
  if (odp.status === 404) return null; // na serwerze jeszcze nie ma wtyczek
  if (!odp.ok) throw new Error(`serwer wtyczek odpowiedział ${odp.status}`);
  return Buffer.from(await odp.arrayBuffer());
}

// Nowa lista z serwera. Starszej niż zapisana nie przyjmuje (data z listy), żeby nikt nie podsunął starej wersji.
async function sprawdz() {
  try {
    const [tresc, podpis] = await Promise.all([pobierz(ADRES + 'lista.json'), pobierz(ADRES + 'lista.json.sig')]);
    if (tresc && podpis) {
      const nowa = odczytaj(tresc, podpis.toString('utf8'));
      if (!lista.data || !nowa.data || nowa.data >= lista.data) {
        fs.mkdirSync(katalog(), { recursive: true });
        fs.writeFileSync(path.join(katalog(), 'lista.json'), tresc);
        fs.writeFileSync(path.join(katalog(), 'lista.json.sig'), podpis);
        lista = nowa;
      }
    }
    stanPobrania = { sprawdzono: new Date().toISOString(), blad: null };
  } catch (e) {
    stanPobrania = { sprawdzono: new Date().toISOString(), blad: e.message || String(e) };
  }
  try { sluchacz(); } catch { /* bez znaczenia */ }
  return stan();
}

// ---------------------------------------------------------------- włączone wtyczki na stronach

function wlaczona(w) {
  const u = czyWlaczona(w.id);
  return u === null || u === undefined ? !!w.domyslnie : !!u;
}

// „apple.com” (dokładnie), „*.apple.com” (domena i poddomeny), „*” (każda strona)
function pasuje(w, host) {
  return w.strony.some(s => {
    s = s.toLowerCase();
    if (s === '*') return true;
    if (s.startsWith('*.')) return host === s.slice(2) || host.endsWith(s.slice(1));
    return host === s;
  });
}

// tylkoId: dołóż tylko tę jedną (po włączeniu w ustawieniach), żeby skrypty innych nie uruchomiły się drugi raz
async function zastosuj(wc, tylkoId) {
  let url;
  try { url = new URL(wc.getURL()); } catch { return; }
  if (!/^https?:$/.test(url.protocol)) return;
  const host = url.hostname.toLowerCase();
  for (const w of lista.wtyczki) {
    if ((tylkoId && w.id !== tylkoId) || !wlaczona(w) || !pasuje(w, host)) continue;
    try {
      if (w.css) await wc.insertCSS(w.css, { cssOrigin: 'author' });
      if (w.js) await wc.executeJavaScriptInIsolatedWorld(SWIAT, [{ code: w.js }]);
    } catch { /* strona zamknięta albo błąd w skrypcie wtyczki: nie psuje strony ani innych wtyczek */ }
  }
}

function podepnij(wc) {
  wc.on('dom-ready', () => { zastosuj(wc); });
}

// ---------------------------------------------------------------- dla panelu i ustawień

function stan() {
  return {
    wtyczki: lista.wtyczki.map(w => ({
      id: w.id, nazwa: w.nazwa, opis: w.opis || '', wersja: w.wersja || '',
      strony: w.strony.join(', '), wlaczona: wlaczona(w)
    })),
    data: lista.data,
    ...stanPobrania
  };
}

function start(opcje = {}) {
  if (typeof opcje.czyWlaczona === 'function') czyWlaczona = opcje.czyWlaczona;
  if (typeof opcje.sluchacz === 'function') sluchacz = opcje.sluchacz;
  wczytajZapisana();
}

module.exports = { start, sprawdz, stan, podepnij, zastosuj, odczytaj, pasuje, ADRES };
