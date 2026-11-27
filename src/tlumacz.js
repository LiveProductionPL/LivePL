'use strict';

// Tłumacz stron LivePL (proces główny).
//
// Silniki:
//   'lokalny' – domyślny. Tłumaczy ten komputer: silnik Firefox Translations (Bergamot, WASM)
//               w osobnym procesie (tlumacz-silnik.js) i modele Mozilli pobierane przy pierwszym
//               użyciu danego języka. Tekst strony nie wychodzi z komputera.
//   'deepl'   – opcjonalny, włączany w Ustawieniach, z własnym kluczem API (zapisanym przez safeStorage).
//               Tekst strony idzie wtedy do DeepL SE (UE). Nigdy dla programów LivePL (poczta, panele)
//               ani programów zamkniętych dla AI: te zawsze tłumaczy komputer.
//
// Zasady:
//   - programy z "dlaAI": false i Urząd (tylkoZYubiKey) tłumaczą się tylko po wyraźnym kliknięciu
//     (bez propozycji i bez „zawsze tłumacz”),
//   - część w stronie (preload-tlumacz.js) działa w odizolowanym świecie i nie dotyka pól formularzy,
//   - języki spoza par z polskim idą przez angielski (np. niemiecki → angielski → polski).

const { app, net, safeStorage, utilityProcess } = require('electron');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const jezyki = require('./jezyki');

const RS = 'https://firefox.settings.services.mozilla.com/v1/buckets/main/collections/translations-models-v2/records';
const RS_PLIKI = 'https://firefox-settings-attachments.cdn.mozilla.net/';
// serwer plików Mozilli odrzuca (406) zapytania z nagłówkiem przeglądarki Chrome, więc LivePL
// przedstawia się krótko, bez wersji i innych danych
const ZAPYTANIE_MOZILLI = { headers: { 'User-Agent': 'LivePL' }, credentials: 'omit', cache: 'no-store' };
// modele 3.x pasują do silnika WASM 4.x dołączonego do LivePL (src/bergamot)
const WERSJA_MODELI = 3;
const FILTRY_DESKTOP = ['', "env.appinfo.OS != 'Android'"];
const DOBA = 24 * 60 * 60 * 1000;
const CZAS_BEZCZYNNOSCI = 10 * 60 * 1000;
const MAKS_PAMIECI = 5000;

// języki, z których Mozilla ma modele do angielskiego (stan: listopad 2026); polski i angielski osobno
const LOKALNE = new Set(['af', 'ar', 'az', 'be', 'bg', 'bn', 'bs', 'ca', 'cs', 'da', 'de', 'el', 'es', 'et', 'eu', 'fa',
  'fi', 'fr', 'gl', 'gu', 'he', 'hi', 'hr', 'hu', 'id', 'is', 'it', 'ja', 'kn', 'ko', 'lt', 'lv', 'ml', 'mr', 'ms', 'nb',
  'nl', 'nn', 'pt', 'ro', 'ru', 'sk', 'sl', 'sq', 'sr', 'sv', 'ta', 'te', 'th', 'tl', 'tr', 'ug', 'uk', 'ur', 'vi',
  'zh-Hans', 'zh-Hant', 'en']);
// języki źródłowe DeepL (inne DeepL rozpozna sam)
const DEEPL = new Set(['ar', 'bg', 'cs', 'da', 'de', 'el', 'en', 'es', 'et', 'fi', 'fr', 'he', 'hu', 'id', 'it', 'ja',
  'ko', 'lt', 'lv', 'nb', 'nl', 'pt', 'ro', 'ru', 'sk', 'sl', 'sv', 'th', 'tr', 'uk', 'vi', 'zh-Hans', 'zh-Hant']);

let L = null; // funkcje LivePL z main.js

// ---------------------------------------------------------------- ustawienia

function opcje() {
  const u = L.ustawienia();
  const t = u.tlumacz && typeof u.tlumacz === 'object' ? u.tlumacz : {};
  u.tlumacz = {
    proponuj: t.proponuj !== false,
    silnik: t.silnik === 'deepl' ? 'deepl' : 'lokalny',
    zawsze: Array.isArray(t.zawsze) ? t.zawsze.filter(x => typeof x === 'string').slice(0, 80) : [],
    nigdy: Array.isArray(t.nigdy) ? t.nigdy.filter(x => typeof x === 'string').slice(0, 500) : []
  };
  return u.tlumacz;
}

function hostZ(url) {
  try { return new URL(url).hostname.replace(/^www\./, ''); } catch { return ''; }
}

function adresKarty(k) {
  try { return k.wc.getURL() || k.adres || ''; } catch { return k.adres || ''; }
}

function originZ(url) {
  try { const u = new URL(url); return /^https?:$/.test(u.protocol) ? u.origin : ''; } catch { return ''; }
}

// programy zamknięte dla AI i Urząd: tylko po wyraźnym kliknięciu i tylko w tym komputerze
function chroniona(k) {
  if (k.program && (k.program.dlaAI === false || k.program.tylkoZYubiKey)) return true;
  const o = originZ(adresKarty(k));
  return !!o && L.wszystkieProgramy().some(p => (p.dlaAI === false || p.tylkoZYubiKey) && originZ(p.adres) === o);
}

function stronaWWW(k) {
  return !!k && !k.nowa && !k.blad && /^https?:/i.test(adresKarty(k));
}

// ---------------------------------------------------------------- klucz DeepL (szyfrowany przez Windows)

const plikKlucza = () => path.join(app.getPath('userData'), 'tlumacz-deepl.bin');
let klucz = undefined;

function kluczDeepL() {
  if (klucz !== undefined) return klucz;
  klucz = null;
  try {
    if (fs.existsSync(plikKlucza())) klucz = safeStorage.decryptString(fs.readFileSync(plikKlucza())) || null;
  } catch { klucz = null; }
  return klucz;
}

function zapiszKlucz(k) {
  if (!require('./szyfrowanie').dostepne()) throw require('./szyfrowanie').blad();
  const tymczasowy = plikKlucza() + '.tmp';
  fs.writeFileSync(tymczasowy, safeStorage.encryptString(k));
  fs.renameSync(tymczasowy, plikKlucza());
  klucz = k;
}

function usunKlucz() {
  try { fs.rmSync(plikKlucza(), { force: true }); } catch { /* nie było */ }
  klucz = null;
  stanDeepL = null;
}

// tylko ostatnie znaki, żeby było wiadomo, który klucz jest zapisany
function opisKlucza() {
  const k = kluczDeepL();
  return k ? '…' + k.slice(-7) : null;
}

const adresDeepL = (k) => (/:fx$/.test(k) ? 'https://api-free.deepl.com' : 'https://api.deepl.com');
let stanDeepL = null; // { tekst, zly } – wynik ostatniego sprawdzenia klucza

async function zapytajDeepL(sciezka, opcjeZapytania = {}) {
  const k = kluczDeepL();
  if (!k) throw new Error('Brak klucza DeepL. Wpisz go w Ustawieniach tłumacza');
  let proba = 0;
  for (;;) {
    const r = await net.fetch(adresDeepL(k) + sciezka, {
      method: opcjeZapytania.body ? 'POST' : 'GET',
      headers: { Authorization: 'DeepL-Auth-Key ' + k, 'Content-Type': 'application/json', 'User-Agent': 'LivePL' },
      body: opcjeZapytania.body ? JSON.stringify(opcjeZapytania.body) : undefined,
      credentials: 'omit',
      cache: 'no-store'
    });
    if (r.status === 429 && proba++ < 3) { await new Promise(ok => setTimeout(ok, 1500 * proba)); continue; }
    if (r.status === 403 || r.status === 401) throw new Error('DeepL nie przyjął klucza API. Sprawdź klucz w Ustawieniach tłumacza');
    if (r.status === 456) throw new Error('Wykorzystano miesięczny limit znaków DeepL');
    if (!r.ok) throw new Error(`DeepL odpowiedział błędem ${r.status}`);
    return r.json();
  }
}

async function sprawdzKlucz() {
  try {
    const j = await zapytajDeepL('/v2/usage');
    const tys = (n) => Math.round((Number(n) || 0) / 1000).toLocaleString('pl-PL');
    stanDeepL = { tekst: `Klucz działa. W tym miesiącu wykorzystano ${tys(j.character_count)} tys. z ${tys(j.character_limit)} tys. znaków.` };
  } catch (e) {
    stanDeepL = { tekst: e.message, zly: true };
  }
  return stanDeepL;
}

async function tlumaczDeepL(zrodlo, teksty, html) {
  const body = { text: teksty, target_lang: 'PL', preserve_formatting: true };
  if (html) body.tag_handling = 'html';
  if (zrodlo && DEEPL.has(zrodlo)) body.source_lang = zrodlo.split('-')[0].toUpperCase();
  const wyniki = [];
  // DeepL przyjmuje do 50 tekstów naraz
  for (let i = 0; i < teksty.length; i += 50) {
    const j = await zapytajDeepL('/v2/translate', { body: { ...body, text: teksty.slice(i, i + 50) } });
    wyniki.push(...(j.translations || []).map(t => String(t.text || '')));
  }
  return wyniki;
}

// ---------------------------------------------------------------- modele Mozilli

const folderModeli = () => path.join(app.getPath('userData'), 'tlumacz', 'modele');
let rekordy = null;
let rekordyCzas = 0;

async function pobierzRekordy() {
  const plik = path.join(app.getPath('userData'), 'tlumacz', 'rekordy.json');
  if (rekordy && Date.now() - rekordyCzas < DOBA) return rekordy;
  try {
    const zapisane = JSON.parse(fs.readFileSync(plik, 'utf8'));
    if (Date.now() - zapisane.czas < DOBA && Array.isArray(zapisane.dane)) {
      rekordy = zapisane.dane;
      rekordyCzas = zapisane.czas;
      return rekordy;
    }
  } catch { /* pierwszy raz */ }
  try {
    const r = await net.fetch(RS, ZAPYTANIE_MOZILLI);
    if (!r.ok) throw new Error('serwer Mozilli odpowiedział ' + r.status);
    const j = await r.json();
    rekordy = (j.data || []).map(x => ({
      z: x.sourceLanguage, na: x.targetLanguage, typ: x.fileType, wersja: String(x.version || ''),
      filtr: x.filter_expression || '', nazwa: x.name || '', hash: x.attachment && x.attachment.hash,
      rozmiar: x.attachment && x.attachment.size, miejsce: x.attachment && x.attachment.location,
      hashRozpakowany: x.decompressedHash || null
    }));
    rekordyCzas = Date.now();
    fs.mkdirSync(path.dirname(plik), { recursive: true });
    fs.writeFileSync(plik, JSON.stringify({ czas: rekordyCzas, dane: rekordy }));
    return rekordy;
  } catch (e) {
    // bez internetu: lista z ostatniego razu, nawet starsza
    try {
      rekordy = JSON.parse(fs.readFileSync(plik, 'utf8')).dane;
      if (Array.isArray(rekordy)) return rekordy;
    } catch { /* brak */ }
    throw new Error('Nie udało się pobrać listy słowników Mozilli: ' + e.message);
  }
}

const porownajWersje = (a, b) => {
  const x = a.split('.').map(Number);
  const y = b.split('.').map(Number);
  for (let i = 0; i < Math.max(x.length, y.length); i++) if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) - (y[i] || 0);
  return 0;
};

// pliki jednej pary (np. de→en): najnowsza zgodna wersja każdego rodzaju pliku
function wybierzPliki(lista, z, na) {
  const pasujace = lista.filter(r => r.z === z && r.na === na && FILTRY_DESKTOP.includes(r.filtr) &&
    /^\d+\.\d+$/.test(r.wersja) && Number(r.wersja.split('.')[0]) === WERSJA_MODELI && r.hash && r.miejsce);
  const wynik = {};
  for (const r of pasujace) if (!wynik[r.typ] || porownajWersje(r.wersja, wynik[r.typ].wersja) > 0) wynik[r.typ] = r;
  if (!wynik.model || !(wynik.vocab || (wynik.srcvocab && wynik.trgvocab))) return null;
  if (wynik.vocab) { delete wynik.srcvocab; delete wynik.trgvocab; }
  return wynik;
}

// pary potrzebne do tłumaczenia z danego języka na polski
const sciezka = (zrodlo) => (zrodlo === 'en' ? [['en', 'pl']] : [[zrodlo, 'en'], ['en', 'pl']]);

const opisPary = (z, na) => `${jezyki.nazwa(z)} → ${jezyki.nazwa(na)}`;

function opisPobranych() {
  const wynik = [];
  try {
    for (const para of fs.readdirSync(folderModeli())) {
      const opis = JSON.parse(fs.readFileSync(path.join(folderModeli(), para, 'opis.json'), 'utf8'));
      const [z, na] = para.split('_');
      wynik.push({ para, nazwa: opisPary(z, na), mb: Math.round(Object.values(opis.pliki).reduce((s, p) => s + (p.rozmiar || 0), 0) / 1e6) });
    }
  } catch { /* nic nie pobrano */ }
  return wynik;
}

const trwajacePobrania = new Map();

// zwraca opis plików pary w tym komputerze; brakujące pobiera (postep: bajty pobrane, wszystkie)
function zapewnijPare(z, na, postep) {
  const klucz = z + '_' + na;
  if (trwajacePobrania.has(klucz)) return trwajacePobrania.get(klucz);
  const zadanie = (async () => {
    const folder = path.join(folderModeli(), klucz);
    const plikOpisu = path.join(folder, 'opis.json');
    try {
      const opis = JSON.parse(fs.readFileSync(plikOpisu, 'utf8'));
      if (Object.values(opis.pliki).every(p => fs.existsSync(path.join(folder, p.plik)))) return { folder, opis };
    } catch { /* trzeba pobrać */ }
    const wybrane = wybierzPliki(await pobierzRekordy(), z, na);
    if (!wybrane) throw new Error(`Mozilla nie ma słownika ${opisPary(z, na)}`);
    fs.mkdirSync(folder, { recursive: true });
    const razem = Object.values(wybrane).reduce((s, r) => s + (r.rozmiar || 0), 0);
    let pobrano = 0;
    const pliki = {};
    for (const [typ, r] of Object.entries(wybrane)) {
      const plik = typ + '.zst';
      await pobierzPlik(RS_PLIKI + r.miejsce, path.join(folder, plik), r.hash, (n) => { pobrano += n; postep(pobrano, razem); });
      pliki[typ] = { plik, nazwa: r.nazwa, wersja: r.wersja, hash: r.hash, hashRozpakowany: r.hashRozpakowany, rozmiar: r.rozmiar };
    }
    const opis = { z, na, pobrano: new Date().toISOString(), pliki };
    fs.writeFileSync(plikOpisu, JSON.stringify(opis, null, 1));
    return { folder, opis };
  })();
  trwajacePobrania.set(klucz, zadanie);
  zadanie.finally(() => trwajacePobrania.delete(klucz)).catch(() => {});
  return zadanie;
}

// pobieranie ze sprawdzeniem SHA-256; plik pojawia się dopiero, gdy suma się zgadza
async function pobierzPlik(url, cel, hash, przybylo) {
  const r = await net.fetch(url, ZAPYTANIE_MOZILLI);
  if (!r.ok || !r.body) throw new Error('Nie udało się pobrać słownika (serwer odpowiedział ' + r.status + ')');
  const tymczasowy = cel + '.pobieranie';
  const suma = crypto.createHash('sha256');
  const strumien = fs.createWriteStream(tymczasowy);
  try {
    const czytnik = r.body.getReader();
    for (;;) {
      const { done, value } = await czytnik.read();
      if (done) break;
      suma.update(value);
      if (!strumien.write(value)) await new Promise(ok => strumien.once('drain', ok));
      przybylo(value.byteLength);
    }
    await new Promise((ok, zle) => strumien.end((e) => (e ? zle(e) : ok())));
  } catch (e) {
    strumien.destroy();
    fs.rmSync(tymczasowy, { force: true });
    throw new Error('Przerwano pobieranie słownika: ' + e.message);
  }
  if (suma.digest('hex') !== String(hash).toLowerCase()) {
    fs.rmSync(tymczasowy, { force: true });
    throw new Error('Pobrany słownik ma złą sumę kontrolną, więc LivePL go nie użyje');
  }
  fs.renameSync(tymczasowy, cel);
}

function usunModele() {
  zatrzymajSilnik();
  fs.rmSync(folderModeli(), { recursive: true, force: true });
}

// ---------------------------------------------------------------- silnik lokalny (osobny proces)

let proces = null;
let numer = 0;
const czekajace = new Map();
let bezczynnosc = null;

function zatrzymajSilnik() {
  clearTimeout(bezczynnosc);
  if (proces) { try { proces.kill(); } catch { /* już nie działa */ } }
  proces = null;
}

function procesSilnika() {
  if (proces) return proces;
  proces = utilityProcess.fork(path.join(__dirname, 'tlumacz-silnik.js'), [], { serviceName: 'LivePL – tłumacz stron', stdio: 'ignore' });
  const ten = proces;
  ten.on('message', (m) => {
    const z = m && czekajace.get(m.nr);
    if (!z) return;
    czekajace.delete(m.nr);
    if (m.blad) z.zle(new Error(m.blad)); else z.ok(m.wynik);
  });
  ten.on('exit', () => {
    if (proces === ten) proces = null;
    for (const [nr, z] of czekajace) { czekajace.delete(nr); z.zle(new Error('Silnik tłumacza się zatrzymał')); }
  });
  return ten;
}

function zapytajSilnik(dane) {
  clearTimeout(bezczynnosc);
  bezczynnosc = setTimeout(() => { if (!czekajace.size) zatrzymajSilnik(); }, CZAS_BEZCZYNNOSCI);
  const nr = ++numer;
  return new Promise((ok, zle) => {
    czekajace.set(nr, { ok, zle });
    procesSilnika().postMessage({ nr, ...dane });
  });
}

// pliki modeli dla procesu silnika (pełne ścieżki i sumy po rozpakowaniu)
const dlaSilnika = ({ folder, opis }) => ({
  para: opis.z + '_' + opis.na, z: opis.z, na: opis.na,
  pliki: Object.fromEntries(Object.entries(opis.pliki).map(([typ, p]) => [typ, { plik: path.join(folder, p.plik), nazwa: p.nazwa, hash: p.hashRozpakowany }]))
});

async function przygotujModele(zrodlo, postep) {
  const pary = sciezka(zrodlo);
  const bajty = new Map();
  const wyniki = await Promise.all(pary.map(([z, na]) => zapewnijPare(z, na, (pobrano, razem) => {
    bajty.set(z + na, [pobrano, razem]);
    let p = 0;
    let r = 0;
    for (const [a, b] of bajty.values()) { p += a; r += b; }
    if (postep) postep(p, r);
  })));
  return wyniki.map(dlaSilnika);
}

// ---------------------------------------------------------------- tłumaczenie (wspólne)

const pamiec = new Map();

function zPamieci(klucz) {
  if (!pamiec.has(klucz)) return undefined;
  const w = pamiec.get(klucz);
  pamiec.delete(klucz);
  pamiec.set(klucz, w);
  return w;
}

function doPamieci(klucz, w) {
  pamiec.set(klucz, w);
  if (pamiec.size > MAKS_PAMIECI) pamiec.delete(pamiec.keys().next().value);
}

// atrapa silnika do testów (LIVEPL_TLUMACZ_TEST=1): dopisuje „[pl]” do każdego kawałka tekstu
async function tlumaczTestowo(zrodlo, teksty, html) {
  await new Promise(ok => setTimeout(ok, 120));
  return teksty.map(t => (html ? t.replace(/>([^<]*\S[^<]*)</g, (m, s) => '>[pl] ' + s.trim() + '<') : '[pl] ' + t));
}

// teksty: tablica napisów (HTML ze <span id> albo zwykły tekst); postep: pobieranie słowników
async function tlumacz(silnik, zrodlo, teksty, html, postep) {
  const wynik = new Array(teksty.length);
  const brakujace = [];
  teksty.forEach((t, i) => {
    const w = zPamieci(`${silnik}|${zrodlo}|${html ? 1 : 0}|${t}`);
    if (w !== undefined) wynik[i] = w; else brakujace.push(i);
  });
  if (brakujace.length) {
    const doTlumaczenia = brakujace.map(i => teksty[i]);
    let przetlumaczone;
    if (silnik === 'test') przetlumaczone = await tlumaczTestowo(zrodlo, doTlumaczenia, html);
    else if (silnik === 'deepl') przetlumaczone = await tlumaczDeepL(zrodlo, doTlumaczenia, html);
    else przetlumaczone = await zapytajSilnik({ typ: 'tlumacz', modele: await przygotujModele(zrodlo, postep), teksty: doTlumaczenia, html });
    brakujace.forEach((i, j) => {
      wynik[i] = String(przetlumaczone[j] ?? '');
      doPamieci(`${silnik}|${zrodlo}|${html ? 1 : 0}|${teksty[i]}`, wynik[i]);
    });
  }
  return wynik;
}

function silnikDla(k) {
  if (process.env.LIVEPL_TLUMACZ_TEST) return 'test';
  if (opcje().silnik === 'deepl' && kluczDeepL() && !(k && (chroniona(k) || L.nasza(adresKarty(k))))) return 'deepl';
  return 'lokalny';
}

const obslugiwany = (silnik, kod) => !!kod && kod !== 'pl' && (silnik !== 'lokalny' || LOKALNE.has(kod));

// ---------------------------------------------------------------- karty

function nowyStan() {
  return { jezyk: null, pewny: false, stan: 'brak', silnik: null, pobieranie: null, praca: false, gotowe: 0, wszystkie: 0, blad: null, nr: 0 };
}

function stanKarty(k) {
  if (!k.tl) k.tl = nowyStan();
  return k.tl;
}

// nowa strona w karcie (nie przewinięcie w tej samej): stan od zera, ale „tłumacz dalej” zostaje
function poNawigacji(k) {
  const s = k.tl;
  if (s && s.wiadomosc) L.usunWiadomosc(s.wiadomosc);
  k.tl = nowyStan();
  L.wyslijStan();
}

function propozycja(k) {
  const s = stanKarty(k);
  const o = opcje();
  return stronaWWW(k) && !chroniona(k) && o.proponuj && !o.nigdy.includes(hostZ(adresKarty(k))) &&
    obslugiwany(silnikDla(k), s.jezyk);
}

// strona zgłasza język (atrybut lang i kawałek tekstu); karta sama decyduje, co z tym zrobić
function raportJezyka(k, dane) {
  if (!stronaWWW(k)) return;
  const s = stanKarty(k);
  if (['pobieranie', 'trwa', 'gotowe'].includes(s.stan)) return;
  const r = jezyki.rozpoznaj(String((dane && dane.probka) || '').slice(0, 6000), String((dane && dane.lang) || '').slice(0, 30));
  if (!r) return;
  s.jezyk = r.kod;
  s.pewny = r.pewny;
  if (r.kod === 'pl') { s.stan = 'brak'; L.wyslijStan(); return; }
  s.stan = propozycja(k) ? 'propozycja' : 'brak';
  // „zawsze tłumacz z tego języka” albo dalsze strony po tłumaczeniu w tej karcie
  const o = opcje();
  const auto = s.stan === 'propozycja' && r.pewny &&
    (o.zawsze.includes(r.kod) || (k.tlDalej && k.tlDalej === r.kod));
  if (auto) przetlumaczStrone(k, { auto: true });
  L.wyslijStan();
}

function wyslijDoStrony(k, kanal, dane) {
  try { k.wc.mainFrame.send(kanal, dane); } catch { /* strona się zamyka */ }
}

async function przetlumaczStrone(k, { auto = false, jezyk = null } = {}) {
  if (!stronaWWW(k)) return;
  const s = stanKarty(k);
  if (jezyk) { s.jezyk = jezyk; s.pewny = true; }
  if (s.stan === 'trwa' || s.stan === 'pobieranie') return;
  if (s.stan === 'gotowe') wyslijDoStrony(k, 'tlumacz:cofnij');
  if (!s.jezyk) {
    // język nieznany: wybór w okienku tłumacza
    L.pokazPanelTlumacza(k, { wybierzJezyk: true });
    return;
  }
  if (s.jezyk === 'pl') {
    L.dodajWiadomosc('Ta strona jest już po polsku');
    return;
  }
  const silnik = silnikDla(k);
  if (!obslugiwany(silnik, s.jezyk)) {
    s.stan = 'blad';
    s.blad = `Tłumacz w tym komputerze nie zna języka: ${jezyki.nazwa(s.jezyk)}`;
    L.wyslijStan();
    return;
  }
  const nr = ++s.nr;
  Object.assign(s, { stan: 'pobieranie', silnik, pobieranie: null, blad: null, praca: true, gotowe: 0, wszystkie: 0 });
  L.wyslijStan();
  if (silnik === 'lokalny') {
    try {
      await przygotujModele(s.jezyk, (pobrano, razem) => {
        if (k.tl !== s || s.nr !== nr) return;
        s.pobieranie = razem ? Math.round((pobrano / razem) * 100) : 0;
        if (!s.wiadomosc) s.wiadomosc = L.dodajWiadomosc(`Pobieram słownik do tłumaczenia z języka: ${jezyki.nazwa(s.jezyk)} (${Math.round(razem / 1e6)} MB, tylko za pierwszym razem)`, {}, 10 * 60 * 1000);
        L.wyslijStan();
      });
    } catch (e) {
      if (k.tl !== s || s.nr !== nr) return;
      Object.assign(s, { stan: 'blad', blad: e.message, praca: false });
      L.dodajWiadomosc('Tłumacz: ' + e.message);
      L.wyslijStan();
      return;
    } finally {
      if (s.wiadomosc) { L.usunWiadomosc(s.wiadomosc); s.wiadomosc = null; }
    }
    if (k.tl !== s || s.nr !== nr) return;
  }
  s.stan = 'trwa';
  s.pobieranie = null;
  k.tlDalej = s.jezyk;
  // lokalnie tłumaczy się cała strona (najpierw to, co widać); DeepL tylko to, co widać, żeby oszczędzać limit
  wyslijDoStrony(k, 'tlumacz:start', { nr, tryb: silnik === 'deepl' ? 'widoczne' : 'calosc', auto });
  L.wyslijStan();
}

function pokazOryginal(k) {
  const s = stanKarty(k);
  s.nr++;
  wyslijDoStrony(k, 'tlumacz:cofnij');
  // po „Pokaż oryginał” przycisk zostaje, żeby można było przetłumaczyć jeszcze raz
  Object.assign(s, { stan: s.jezyk && s.jezyk !== 'pl' ? 'propozycja' : 'brak', praca: false, blad: null, pobieranie: null });
  k.tlDalej = null;
  L.wyslijStan();
}

// przycisk przy adresie: tłumaczy albo pokazuje oryginał
function przelacz(k) {
  if (!k) return;
  const s = stanKarty(k);
  if (s.stan === 'gotowe' || s.stan === 'trwa') pokazOryginal(k);
  else if (s.stan !== 'pobieranie') przetlumaczStrone(k);
}

// dla paska LivePL
function stanDlaPaska(k) {
  if (!k || !k.tl) return null;
  const s = k.tl;
  if (s.stan === 'brak') return null;
  return {
    stan: s.stan,
    jezyk: s.jezyk,
    nazwa: jezyki.nazwa(s.jezyk),
    pobieranie: s.pobieranie,
    praca: s.praca,
    procent: s.wszystkie ? Math.round((s.gotowe / s.wszystkie) * 100) : null,
    blad: s.blad
  };
}

// ---------------------------------------------------------------- tłumaczenie zaznaczenia

async function przetlumaczZaznaczenie(k, tekst, kotwica) {
  const t = String(tekst || '').trim().slice(0, 5000);
  if (!t) return;
  const r = jezyki.rozpoznaj(t, '') || (k && k.tl && k.tl.jezyk ? { kod: k.tl.jezyk } : null);
  const zrodlo = r ? r.kod : null;
  const dane = { tekst: t, zrodlo, nazwa: jezyki.nazwa(zrodlo), wynik: null, blad: null, trwa: true, pobieranie: null, silnik: silnikDla(k) };
  L.pokazPanel('tlumaczenie', kotwica, { tlumaczenie: dane });
  const odswiez = () => L.wyslijDoPanelu();
  if (zrodlo === 'pl') {
    Object.assign(dane, { trwa: false, blad: 'Zaznaczony tekst jest już po polsku.' });
    return odswiez();
  }
  if (!zrodlo || !obslugiwany(dane.silnik, zrodlo)) {
    Object.assign(dane, { trwa: false, blad: zrodlo ? `Tłumacz w tym komputerze nie zna języka: ${jezyki.nazwa(zrodlo)}.` : 'Nie rozpoznano języka zaznaczenia.' });
    return odswiez();
  }
  try {
    const [wynik] = await tlumacz(dane.silnik, zrodlo, [t], false, (pobrano, razem) => {
      dane.pobieranie = razem ? Math.round((pobrano / razem) * 100) : 0;
      odswiez();
    });
    Object.assign(dane, { wynik, trwa: false, pobieranie: null });
  } catch (e) {
    Object.assign(dane, { blad: e.message, trwa: false, pobieranie: null });
  }
  odswiez();
}

// ---------------------------------------------------------------- panel (okienko tłumacza i ustawienia)

function danePanelu(k) {
  const o = opcje();
  const s = k && k.tl ? k.tl : nowyStan();
  const host = k ? hostZ(adresKarty(k)) : '';
  return {
    karta: k && stronaWWW(k) ? {
      jezyk: s.jezyk, nazwa: jezyki.nazwa(s.jezyk), pewny: s.pewny, stan: s.stan, blad: s.blad,
      pobieranie: s.pobieranie, praca: s.praca,
      procent: s.wszystkie ? Math.round((s.gotowe / s.wszystkie) * 100) : null,
      host, chroniona: chroniona(k), silnik: silnikDla(k),
      zawsze: !!s.jezyk && o.zawsze.includes(s.jezyk), nigdy: o.nigdy.includes(host)
    } : null,
    proponuj: o.proponuj,
    silnik: o.silnik,
    klucz: opisKlucza(),
    stanKlucza: stanDeepL,
    szyfrowanie: require('./szyfrowanie').dostepne(),
    zawsze: o.zawsze.map(kod => ({ kod, nazwa: jezyki.nazwa(kod) })),
    nigdy: o.nigdy,
    modele: opisPobranych(),
    jezyki: [...LOKALNE].map(kod => ({ kod, nazwa: jezyki.nazwa(kod) })).sort((a, b) => a.nazwa.localeCompare(b.nazwa, 'pl'))
  };
}

// akcje z panelu; zwraca true, gdy panel ma zostać otwarty
function akcja(nazwa, arg, k) {
  const o = opcje();
  switch (nazwa) {
    case 'tlumacz-strone': if (k) przetlumaczStrone(k, { jezyk: typeof arg === 'string' && LOKALNE.has(arg) ? arg : null }); return false;
    case 'tlumacz-oryginal': if (k) pokazOryginal(k); return false;
    case 'tlumacz-jezyk': {
      // ręczny wybór języka strony; przetłumaczona strona tłumaczy się od nowa
      if (!k || typeof arg !== 'string' || !LOKALNE.has(arg)) return true;
      const s = stanKarty(k);
      const bylo = s.stan === 'gotowe' || s.stan === 'trwa';
      s.jezyk = arg;
      s.pewny = true;
      if (bylo || s.stan === 'brak' || s.stan === 'blad') { s.stan = 'propozycja'; przetlumaczStrone(k); }
      return true;
    }
    case 'tlumacz-zawsze': {
      const j = k && k.tl && k.tl.jezyk;
      if (!j || j === 'pl') return true;
      o.zawsze = o.zawsze.includes(j) ? o.zawsze.filter(x => x !== j) : [...o.zawsze, j];
      L.zapiszUstawienia();
      if (o.zawsze.includes(j) && !chroniona(k) && k.tl.stan === 'propozycja') przetlumaczStrone(k, { auto: true });
      return true;
    }
    case 'tlumacz-zawsze-usun': o.zawsze = o.zawsze.filter(x => x !== arg); L.zapiszUstawienia(); return true;
    case 'tlumacz-nigdy': {
      const h = k ? hostZ(adresKarty(k)) : '';
      if (!h) return true;
      o.nigdy = o.nigdy.includes(h) ? o.nigdy.filter(x => x !== h) : [...o.nigdy, h];
      L.zapiszUstawienia();
      if (o.nigdy.includes(h) && k.tl && k.tl.stan === 'propozycja') k.tl.stan = 'brak';
      else if (k.tl && k.tl.stan === 'brak' && propozycja(k)) k.tl.stan = 'propozycja';
      L.wyslijStan();
      return true;
    }
    case 'tlumacz-nigdy-usun': o.nigdy = o.nigdy.filter(x => x !== arg); L.zapiszUstawienia(); return true;
    case 'tlumacz-zamknij': return false;
    case 'tlumacz-proponuj': o.proponuj = !o.proponuj; L.zapiszUstawienia(); return true;
    case 'tlumacz-silnik':
      if (arg === 'lokalny' || arg === 'deepl') { o.silnik = arg; L.zapiszUstawienia(); }
      return true;
    case 'tlumacz-klucz': {
      const nowy = String(arg || '').trim();
      if (!/^[A-Za-z0-9-]{20,60}(:fx)?$/.test(nowy)) { stanDeepL = { tekst: 'To nie wygląda na klucz API DeepL (np. 1a2b3c4d-…:fx).', zly: true }; return true; }
      try { zapiszKlucz(nowy); } catch (e) { stanDeepL = { tekst: 'Nie udało się zapisać klucza: ' + e.message, zly: true }; return true; }
      stanDeepL = { tekst: 'Sprawdzam klucz…' };
      sprawdzKlucz().then(() => L.wyslijDoPanelu());
      return true;
    }
    case 'tlumacz-klucz-sprawdz': stanDeepL = { tekst: 'Sprawdzam klucz…' }; sprawdzKlucz().then(() => L.wyslijDoPanelu()); return true;
    case 'tlumacz-klucz-usun': usunKlucz(); if (o.silnik === 'deepl') { o.silnik = 'lokalny'; L.zapiszUstawienia(); } return true;
    case 'tlumacz-modele-usun': usunModele(); L.dodajWiadomosc('Usunięto pobrane słowniki tłumacza'); return true;
    default: return true;
  }
}

// ---------------------------------------------------------------- start

function uruchom(funkcje, ipcMain) {
  L = funkcje;
  opcje();

  // strona zgłasza swój język (tylko główna ramka karty LivePL)
  ipcMain.on('tlumacz:jezyk', (e, dane) => {
    if (e.senderFrame !== e.sender.mainFrame) return;
    const k = L.kartaDla(e.sender);
    if (k) raportJezyka(k, dane);
  });

  // strona prosi o przetłumaczenie kawałków; tylko gdy użytkownik włączył tłumaczenie tej karty
  ipcMain.handle('tlumacz:tekst', async (e, dane) => {
    if (e.senderFrame !== e.sender.mainFrame) throw new Error('niedozwolone');
    const k = L.kartaDla(e.sender);
    const s = k && k.tl;
    if (!s || !dane || dane.nr !== s.nr || !['trwa', 'gotowe'].includes(s.stan)) throw new Error('tłumaczenie wyłączone');
    const teksty = Array.isArray(dane.teksty) ? dane.teksty.slice(0, 60).map(t => String(t).slice(0, 20000)) : [];
    try {
      return await tlumacz(s.silnik, s.jezyk, teksty, true);
    } catch (err) {
      if (k.tl === s && s.nr === dane.nr) {
        Object.assign(s, { stan: 'blad', blad: err.message, praca: false });
        L.dodajWiadomosc('Tłumacz: ' + err.message);
        L.wyslijStan();
      }
      throw err;
    }
  });

  ipcMain.on('tlumacz:postep', (e, d) => {
    if (e.senderFrame !== e.sender.mainFrame || !d) return;
    const k = L.kartaDla(e.sender);
    const s = k && k.tl;
    if (!s || d.nr !== s.nr || !['trwa', 'gotowe'].includes(s.stan)) return;
    s.praca = !!d.praca;
    s.gotowe = Math.max(0, Number(d.gotowe) || 0);
    s.wszystkie = Math.max(0, Number(d.wszystkie) || 0);
    s.stan = s.praca ? 'trwa' : 'gotowe';
    L.wyslijStan();
    if (L.panelTlumacza()) L.wyslijDoPanelu();
  });

  app.on('will-quit', zatrzymajSilnik);
}

module.exports = {
  uruchom, poNawigacji, przetlumaczStrone, pokazOryginal, przelacz, przetlumaczZaznaczenie,
  stanDlaPaska, danePanelu, akcja, chroniona, stronaWWW
};
