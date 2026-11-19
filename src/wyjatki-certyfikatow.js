'use strict';

// Wejście mimo błędu certyfikatu („Wiem, co robię – wejdź mimo to” na stronie błędu).
//
// Domyślnie LivePL blokuje stronę z nieprawidłowym certyfikatem. Użytkownik może świadomie wejść:
//   - tylko teraz (wyjątek w pamięci, do zamknięcia LivePL),
//   - albo zapamiętać wyjątek (ustawienia.wyjatkiCertyfikatow, lista w Ustawieniach).
// Wyjątek to zawsze host:port + odcisk SHA-256 jednego, konkretnego certyfikatu. Gdy serwer pokaże
// inny certyfikat, ostrzeżenie wraca. Nigdy nie da się obejść błędu dla naszych domen
// (assets/ca/domeny.json) ani dla stron z HSTS (lista wbudowana w silnik i strony, które same
// zażądały HSTS) — tam błąd zostaje twardy.

const { net } = require('electron');
const { X509Certificate } = require('crypto');
const { isIP } = require('net');
const naszeCA = require('./nasze-ca');

let L = null; // z main.js: ustawienia(), zapiszUstawienia(), sesja()
const tymczasowe = new Map(); // host:port → { odcisk, ... } do zamknięcia LivePL
const ostatnie = new Map(); // id karty (webContents) → ostatni błąd certyfikatu strony głównej
const pamiecHsts = new Map(); // host → { wynik, kiedy }
const CZAS_HSTS = 5 * 60 * 1000;
const MAKS_WPISOW = 200;

function uruchom(funkcje) {
  L = funkcje;
}

function zapamietane() {
  const u = L.ustawienia();
  if (!Array.isArray(u.wyjatkiCertyfikatow)) u.wyjatkiCertyfikatow = [];
  return u.wyjatkiCertyfikatow;
}

// host i port z adresu https:// albo wss:// (IPv6 w nawiasach, jak w adresie)
function adresZ(url) {
  try {
    const u = new URL(url);
    if (u.protocol !== 'https:' && u.protocol !== 'wss:') return null;
    const host = u.hostname.toLowerCase().replace(/\.$/, '');
    if (!host) return null;
    const port = u.port || '443';
    return { host, port, klucz: host + ':' + port };
  } catch {
    return null;
  }
}

function bezNawiasow(host) {
  return String(host || '').replace(/^\[|\]$/g, '');
}

function opisCertyfikatu(cert) {
  try {
    const x = new X509Certificate(cert.data);
    const nazwy = String(x.subjectAltName || '')
      .split(/,\s*/)
      .map(s => s.replace(/^(DNS|IP Address):/i, '').trim())
      .filter(Boolean)
      .slice(0, 8);
    return {
      odcisk: x.fingerprint256,
      dla: (cert.subject && cert.subject.commonName) || '',
      nazwy,
      wystawca: (cert.issuer && cert.issuer.commonName) || cert.issuerName || '',
      waznyOd: Date.parse(x.validFrom) || null,
      waznyDo: Date.parse(x.validTo) || null,
      samopodpisany: x.checkIssued(x)
    };
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------- HSTS
// Pytamy sam silnik: zapytanie o http://host:1/ dla hosta z HSTS silnik zamienia w środku na https
// (przekierowanie 307 „HSTS”), zanim cokolwiek wyjdzie do sieci. Port 1 jest zablokowany
// (ERR_UNSAFE_PORT), więc dla zwykłego hosta też nic nie wychodzi. Silnik zna i wbudowaną listę
// HSTS, i strony, które same zażądały HSTS w tym profilu. Niepewna odpowiedź = traktujemy jak HSTS.

function zapytajSilnik(host) {
  return new Promise((gotowe) => {
    let r;
    let koniec = false;
    const wynik = (w) => {
      if (koniec) return;
      koniec = true;
      clearTimeout(zegar);
      try { r.abort(); } catch { /* już zakończone */ }
      gotowe(w);
    };
    const zegar = setTimeout(() => wynik('nie-wiem'), 4000);
    try {
      r = net.request({ url: `http://${host}:1/`, session: L.sesja(), redirect: 'manual', useSessionCookies: false });
    } catch {
      wynik('nie-wiem');
      return;
    }
    r.on('redirect', (status, metoda, url) => wynik(/^https:/i.test(url) ? 'hsts' : 'nie-wiem'));
    r.on('response', () => wynik('nie-wiem'));
    r.on('error', (e) => wynik(/ERR_UNSAFE_PORT/.test(String(e && e.message)) ? 'brak' : 'nie-wiem'));
    r.end();
  });
}

async function hsts(host) {
  if (isIP(bezNawiasow(host))) return 'brak'; // HSTS nie dotyczy adresów IP
  const p = pamiecHsts.get(host);
  if (p && Date.now() - p.kiedy < CZAS_HSTS) return p.wynik;
  const wynik = await zapytajSilnik(host);
  if (wynik !== 'nie-wiem') pamiecHsts.set(host, { wynik, kiedy: Date.now() });
  return wynik;
}

// powód, dla którego nie wolno obejść błędu: 'nasza', 'hsts', 'nie-wiem' albo null (wolno)
async function zakaz(host) {
  if (naszeCA.wNaszychDomenach(host)) return 'nasza';
  const h = await hsts(host);
  if (h === 'hsts') return 'hsts';
  if (h === 'nie-wiem') return 'nie-wiem';
  return null;
}

// ---------------------------------------------------------------- wyjątki

function wyjatek(klucz) {
  return zapamietane().find(w => w.klucz === klucz) || null;
}

function pasuje(klucz, odcisk) {
  if (!odcisk) return null;
  const t = tymczasowe.get(klucz);
  if (t && t.odcisk === odcisk) return { ...t, trwaly: false };
  const z = wyjatek(klucz);
  if (z && z.odcisk === odcisk) return { ...z, trwaly: true };
  return null;
}

// app.on('certificate-error'): wyjątek z pasującym odciskiem przepuszcza (stronę i jej pliki),
// wszystko inne odrzucamy. Błąd strony głównej zapamiętujemy dla strony błędu.
function bladCertyfikatu(e, wc, url, blad, cert, odpowiedz, glowna) {
  e.preventDefault();
  const a = adresZ(url);
  const opis = a ? opisCertyfikatu(cert || {}) : null;
  (async () => {
    if (!a || !opis) return false;
    const powod = await zakaz(a.host);
    if (!powod && pasuje(a.klucz, opis.odcisk)) return true;
    if (glowna && wc && !wc.isDestroyed()) {
      const z = wyjatek(a.klucz) || tymczasowe.get(a.klucz);
      const id = wc.id;
      if (!ostatnie.has(id)) wc.once('destroyed', () => ostatnie.delete(id));
      ostatnie.set(id, {
        url, ...a, ...opis, blad: String(blad || ''), powod,
        // był wyjątek dla tego adresu, ale z innym certyfikatem
        zmieniony: z && z.odcisk !== opis.odcisk ? { odcisk: z.odcisk, dodano: z.dodano || null } : null
      });
    }
    return false;
  })().then(
    (tak) => odpowiedz(!!tak),
    () => odpowiedz(false)
  );
}

// dla strony błędu: czy wolno wejść mimo błędu (tylko gdy ostatni błąd dotyczy tego adresu)
function dlaStronyBledu(wc, url) {
  const r = wc && ostatnie.get(wc.id);
  const a = adresZ(url);
  if (!r || !a || r.klucz !== a.klucz) return null;
  return r;
}

// „Wejdź mimo to”: sprawdzamy jeszcze raz (HSTS, nasza domena) i dodajemy wyjątek dla certyfikatu,
// który LivePL sam widział przy błędzie (strona błędu nie podaje odcisku). Zwraca adres do wczytania.
async function wejdz(wc, url, trwale) {
  const r = dlaStronyBledu(wc, url);
  if (!r) throw new Error('Nie wiem, o który certyfikat chodzi. Odśwież stronę i spróbuj jeszcze raz.');
  const powod = await zakaz(r.host);
  if (powod) throw new Error('Tej strony nie można otworzyć z błędnym certyfikatem.');
  const wpis = {
    klucz: r.klucz, host: r.host, port: r.port, odcisk: r.odcisk,
    dla: r.dla, wystawca: r.wystawca, waznyDo: r.waznyDo, blad: r.blad, dodano: Date.now()
  };
  if (trwale) {
    const lista = zapamietane().filter(w => w.klucz !== r.klucz);
    lista.unshift(wpis);
    L.ustawienia().wyjatkiCertyfikatow = lista.slice(0, MAKS_WPISOW);
    tymczasowe.delete(r.klucz);
    L.zapiszUstawienia();
  } else {
    tymczasowe.set(r.klucz, wpis);
  }
  ostatnie.delete(wc.id);
  return r.url;
}

// wyjątek obowiązujący teraz dla adresu strony (do okienka kłódki)
function dlaAdresu(url) {
  const a = adresZ(url);
  if (!a) return null;
  const t = tymczasowe.get(a.klucz);
  if (t) return { ...t, trwaly: false };
  const z = wyjatek(a.klucz);
  return z ? { ...z, trwaly: true } : null;
}

function lista() {
  return zapamietane().map(w => ({
    klucz: w.klucz, host: w.host, port: w.port, odcisk: w.odcisk,
    dla: w.dla || '', wystawca: w.wystawca || '', dodano: w.dodano || null, waznyDo: w.waznyDo || null
  }));
}

// usunięcie wyjątku (tymczasowego i zapamiętanego) zamyka otwarte połączenia, żeby następne
// wczytanie strony znowu sprawdziło certyfikat
function usun(klucz) {
  const k = String(klucz || '');
  const zapisany = zapamietane().some(w => w.klucz === k);
  const tymczasowy = tymczasowe.delete(k);
  if (zapisany) {
    L.ustawienia().wyjatkiCertyfikatow = zapamietane().filter(w => w.klucz !== k);
    L.zapiszUstawienia();
  }
  try { L.sesja().closeAllConnections().catch(() => {}); } catch { /* starszy Electron */ }
  return zapisany || tymczasowy;
}

module.exports = { uruchom, bladCertyfikatu, dlaStronyBledu, wejdz, dlaAdresu, lista, usun, adresZ };
