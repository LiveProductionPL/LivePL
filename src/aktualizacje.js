'use strict';

// Zdalne aktualizacje LivePL.
// Na serwerze leżą: manifest.json, jego podpis manifest.json.sig (Ed25519) i paczka z kodem programu.
// LivePL sprawdza podpis naszym kluczem publicznym, potem sumę paczki i sumę każdego pliku.
// Gotowa wersja czeka w danych programu, a podmienia ją start.js przy następnym uruchomieniu.

const { app, net } = require('electron');
const crypto = require('crypto');
const zlib = require('zlib');
const fs = require('fs');
const path = require('path');
const { KLUCZ_PUBLICZNY } = require('./klucz-aktualizacji');
const { porownaj, dozwolonaSciezka } = require('./aktualizacja-wgraj');

const ADRES = (process.env.LIVEPL_AKTUALIZACJE || 'https://aktualizacje.livepl.eu/livepl/').replace(/\/?$/, '/');
// macOS: pliki w podpisanej aplikacji (LivePL.app) nie mogą się zmieniać, bo podpis Apple przestałby się zgadzać.
// Aktualizacją jest więc cały nowy LivePL w pliku .dmg (podpisany Developer ID i sprawdzony przez Apple), opisany
// w tym samym podpisanym manifeście w polu „mac”: { wersja, plik, rozmiar, notatki }. Bez tego pola nic nie proponujemy.
const MAC = process.platform === 'darwin';

let stan = { stan: 'nieznany', wersja: null, blad: null, notatki: '', sprawdzono: null };
let sluchacz = () => {};

function katalog() {
  return path.join(app.getPath('userData'), 'aktualizacja');
}

function ustaw(zmiany) {
  stan = { ...stan, ...zmiany };
  try { sluchacz(stan); } catch { /* bez znaczenia */ }
}

async function pobierz(url) {
  // tylko HTTPS; zwykłe HTTP wyłącznie na tym komputerze (testy)
  if (!/^https:\/\//i.test(url) && !/^http:\/\/127\.0\.0\.1[:/]/.test(url)) throw new Error('niedozwolony adres aktualizacji');
  const odp = await net.fetch(url, { cache: 'no-store' });
  if (!odp.ok) throw new Error(`serwer aktualizacji odpowiedział ${odp.status}`);
  return Buffer.from(await odp.arrayBuffer());
}

function przygotowana() {
  try {
    return JSON.parse(fs.readFileSync(path.join(katalog(), 'gotowa', 'gotowa.json'), 'utf8'));
  } catch {
    return null;
  }
}

function kopia() {
  try {
    return JSON.parse(fs.readFileSync(path.join(katalog(), 'poprzednia', 'wersja.json'), 'utf8'));
  } catch {
    return null;
  }
}

// Najpierw tylko sprawdzamy (stan „dostepna”); paczkę pobieramy dopiero, gdy użytkownik się zgodzi
// (pobierz()). Pobrana wersja wchodzi przy następnym uruchomieniu (start.js).
async function sprawdz(opcje = {}) {
  if (stan.stan === 'sprawdzam' || stan.stan === 'pobieram') return stan;
  ustaw({ stan: 'sprawdzam', blad: null });
  try {
    const [manifest, podpisTekst] = await Promise.all([
      pobierz(ADRES + 'manifest.json'),
      pobierz(ADRES + 'manifest.json.sig')
    ]);
    const podpis = Buffer.from(podpisTekst.toString('utf8').trim(), 'base64');
    if (!crypto.verify(null, manifest, KLUCZ_PUBLICZNY, podpis)) throw new Error('podpis aktualizacji się nie zgadza');
    const m = JSON.parse(manifest.toString('utf8'));
    if (m.produkt !== 'LivePL' || !m.wersja || !m.plik || !m.sha256) throw new Error('to nie jest aktualizacja LivePL');
    if (MAC) return wersjaNaMaca(m);

    const obecna = app.getVersion();
    if (porownaj(m.wersja, obecna) <= 0) {
      ustaw({ stan: 'aktualna', wersja: obecna, notatki: '', sprawdzono: Date.now() });
      return stan;
    }
    if (m.minSilnik && porownaj(process.versions.electron, m.minSilnik) < 0) {
      ustaw({ stan: 'silnik', wersja: m.wersja, notatki: m.notatki || '', sprawdzono: Date.now() });
      return stan;
    }
    const juz = przygotowana();
    if (juz && juz.wersja === m.wersja) {
      ustaw({ stan: 'gotowa', wersja: m.wersja, notatki: juz.notatki || '', sprawdzono: Date.now() });
      return stan;
    }

    if (!opcje.pobierz) {
      ustaw({ stan: 'dostepna', wersja: m.wersja, notatki: m.notatki || '', rozmiar: Number(m.rozmiar) || 0, sprawdzono: Date.now() });
      return stan;
    }

    ustaw({ stan: 'pobieram', wersja: m.wersja, notatki: m.notatki || '' });
    const paczka = await pobierz(new URL(m.plik, ADRES).href);
    if (crypto.createHash('sha256').update(paczka).digest('hex') !== m.sha256) throw new Error('paczka aktualizacji jest uszkodzona');
    const dane = JSON.parse(zlib.gunzipSync(paczka).toString('utf8'));
    if (dane.produkt !== 'LivePL' || dane.wersja !== m.wersja || !Array.isArray(dane.pliki)) throw new Error('paczka nie pasuje do manifestu');

    const tymczasowy = path.join(katalog(), 'pobierana');
    const gotowa = path.join(katalog(), 'gotowa');
    fs.rmSync(tymczasowy, { recursive: true, force: true });
    for (const p of dane.pliki) {
      if (!dozwolonaSciezka(p.sciezka)) throw new Error('niedozwolony plik w paczce: ' + p.sciezka);
      const tresc = Buffer.from(String(p.dane || ''), 'base64');
      if (crypto.createHash('sha256').update(tresc).digest('hex') !== p.sha256) throw new Error('uszkodzony plik w paczce: ' + p.sciezka);
      const cel = path.join(tymczasowy, 'pliki', ...p.sciezka.split('/'));
      fs.mkdirSync(path.dirname(cel), { recursive: true });
      fs.writeFileSync(cel, tresc);
    }
    if (!dane.pliki.some(p => p.sciezka === 'src/main.js') || !dane.pliki.some(p => p.sciezka === 'package.json')) {
      throw new Error('paczka jest niepełna');
    }
    fs.writeFileSync(path.join(tymczasowy, 'gotowa.json'), JSON.stringify({
      wersja: m.wersja,
      notatki: m.notatki || '',
      data: m.data || '',
      pliki: dane.pliki.map(p => p.sciezka)
    }, null, 2));
    fs.rmSync(gotowa, { recursive: true, force: true });
    fs.renameSync(tymczasowy, gotowa);
    ustaw({ stan: 'gotowa', wersja: m.wersja, notatki: m.notatki || '', sprawdzono: Date.now() });
  } catch (e) {
    ustaw({ stan: 'blad', blad: e.message, sprawdzono: Date.now() });
  }
  return stan;
}

function wersjaNaMaca(m) {
  const obecna = app.getVersion();
  const mm = m.mac;
  if (!mm || !mm.wersja || !mm.plik || porownaj(mm.wersja, obecna) <= 0) {
    ustaw({ stan: 'aktualna', wersja: obecna, notatki: '', adresMac: null, sprawdzono: Date.now() });
    return stan;
  }
  const adres = new URL(mm.plik, ADRES);
  if (adres.protocol !== 'https:' || !/(^|\.)livepl\.eu$/i.test(adres.hostname) || !/\.dmg$/i.test(adres.pathname)) {
    throw new Error('niedozwolony adres wersji na Maca');
  }
  ustaw({ stan: 'dostepna', wersja: mm.wersja, notatki: mm.notatki || m.notatki || '', rozmiar: Number(mm.rozmiar) || 0, adresMac: adres.href, sprawdzono: Date.now() });
  return stan;
}

// powrót do poprzedniej wersji robi start.js przy następnym uruchomieniu
function zaplanujPowrot() {
  if (!kopia()) return false;
  fs.writeFileSync(path.join(katalog(), 'przywroc.json'), JSON.stringify({ kiedy: new Date().toISOString() }));
  return true;
}

function start(fn) {
  sluchacz = fn;
  const juz = przygotowana();
  if (juz && porownaj(juz.wersja, app.getVersion()) > 0) {
    ustaw({ stan: 'gotowa', wersja: juz.wersja, notatki: juz.notatki || '' });
  }
}

module.exports = {
  ADRES,
  sprawdz,
  pobierz: () => sprawdz({ pobierz: !MAC }),
  start,
  stan: () => stan,
  kopia,
  zaplanujPowrot
};
