'use strict';

// Przeglądarka LivePL – główny proces.
// Okno ma pasek boczny z programami LivePL i górny pasek z adresem; każda strona
// to osobny WebContentsView ułożony pod spodem.

const {
  app, BrowserWindow, WebContentsView, ipcMain, Menu, Tray, nativeImage,
  nativeTheme, session, shell, dialog, clipboard, desktopCapturer
} = require('electron');
const path = require('path');
const { execFile, execFileSync } = require('child_process');
const aktualizacje = require('./aktualizacje');
const naszeCA = require('./nasze-ca');
const certOsobisty = require('./certyfikat-osobisty');
const yubikey = require('./yubikey');
const zaufanie = require('./zaufanie');
const hasla = require('./hasla');
const certWin = require('./certyfikaty-windows');
const urzadzenie = require('./urzadzenie');
const dostepAI = require('./dostep-ai');
const fs = require('fs');

const ID_APLIKACJI = 'pl.livepl.przegladarka';
// Układ „Reżyserka”: kanały po lewej, strona jak monitor w ramce, pasek wiadomości na dole.
const PASEK_BOCZNY = 72;
const PASEK_GORNY = 38;
// przyciski okna (minimalizuj, maksymalizuj, zamknij) niższe niż górny pasek, żeby nie dotykały ramki strony
const WYSOKOSC_PRZYCISKOW_OKNA = 28;
const MARGINES_PRAWY = 12;
const PASEK_DOLNY = 48;
const ZAOKRAGLENIE = 10;
const KATALOG = path.join(__dirname, '..');
const UI = path.join(KATALOG, 'ui');
// czerwona wstęga na jasne tła, biała wstęga z czerwonymi falami na ciemne
const IKONA_JASNA = path.join(KATALOG, 'assets', 'wstega-jasna.ico');
const IKONA_CIEMNA = path.join(KATALOG, 'assets', 'wstega-ciemna.ico');
let IKONA = IKONA_JASNA;
const PARTYCJA = 'persist:livepl';
const KOLORY = {
  jasny: { tlo: '#e9ecef', symbole: '#1d2733', strona: '#ffffff' },
  ciemny: { tlo: '#0d0f12', symbole: '#d6dbe1', strona: '#16191e' }
};
const POWIEKSZENIA = [0.5, 0.67, 0.75, 0.8, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2, 2.5, 3];
const STARTUJ_W_TLE = process.argv.includes('--w-tle');

app.setAppUserModelId(ID_APLIKACJI);
app.setName('LivePL');

if (!app.hasSingleInstanceLock() && !app.requestSingleInstanceLock()) {
  app.quit();
  return;
}

// ---------------------------------------------------------------- ustawienia

const ustawienia = {
  okno: null,
  chowajDoZasobnika: true,
  autoAktualizacje: true,
  dymekZasobnika: false,
  ostatnia: null,
  motyw: 'auto', // auto (od zachodu do wschodu słońca noc, w dzień jak Windows), dzien, noc
  ai: 'wylaczony' // dostęp dla AI (Claude i inne przez MCP): wylaczony, czytanie, pelny
};
const plikUstawien = () => path.join(app.getPath('userData'), 'ustawienia.json');
let zapisUstawien = null;

function wczytajUstawienia() {
  try {
    Object.assign(ustawienia, JSON.parse(fs.readFileSync(plikUstawien(), 'utf8')));
  } catch { /* pierwszy start */ }
}

function zapiszUstawieniaTeraz() {
  clearTimeout(zapisUstawien);
  try {
    fs.writeFileSync(plikUstawien(), JSON.stringify(ustawienia, null, 2));
  } catch { /* bez znaczenia */ }
}

function zapiszUstawienia() {
  clearTimeout(zapisUstawien);
  zapisUstawien = setTimeout(zapiszUstawieniaTeraz, 500);
}

// tryb dzień i noc: przełącznik przy zegarze; obejmuje pasek, panele i strony (prefers-color-scheme).
// „Automatycznie”: od zachodu do wschodu słońca w Lesznie noc, w dzień jak Windows
// (ciemny Windows daje więc ciemny LivePL także w dzień). Przełącza się samo, bez ponownego uruchamiania.
const slonce = require('./slonce');
const TRYBY_MOTYWU = { auto: 'system', dzien: 'light', noc: 'dark' };
const LESZNO = { szerokosc: 51.8406, dlugosc: 16.5751 };
// LIVEPL_CZAS_MOTYWU (do testów): udawany czas tylko dla trybu dzień i noc, np. 15:57 albo 2026-11-19T15:57
const przesuniecieCzasuMotywu = slonce.przesuniecie(process.env.LIVEPL_CZAS_MOTYWU);
let nocMotywu = null; // noc trwająca albo najbliższa: { od: zachód, do: wschód }
let zegarMotywu = null;

function zastosujMotyw() {
  if (!TRYBY_MOTYWU[ustawienia.motyw]) ustawienia.motyw = 'auto';
  clearTimeout(zegarMotywu);
  const teraz = new Date(Date.now() + przesuniecieCzasuMotywu);
  nocMotywu = slonce.noc(teraz, LESZNO.szerokosc, LESZNO.dlugosc);
  let zrodlo = TRYBY_MOTYWU[ustawienia.motyw];
  if (ustawienia.motyw === 'auto') {
    const trwaNoc = teraz >= nocMotywu.od;
    if (trwaNoc) zrodlo = 'dark';
    // następne sprawdzenie o najbliższym wschodzie albo zachodzie, a najpóźniej za 10 minut
    // (po uśpieniu komputera albo przestawieniu zegara)
    const doZmiany = (trwaNoc ? nocMotywu.do : nocMotywu.od) - teraz;
    zegarMotywu = setTimeout(odswiezMotyw, Math.min(doZmiany + 1000, 10 * 60 * 1000));
  }
  if (nativeTheme.themeSource !== zrodlo) nativeTheme.themeSource = zrodlo;
}

function odswiezMotyw() {
  zastosujMotyw();
  wyslijStan(); // godziny w podpowiedzi przycisku
}

// godziny zachodu i wschodu do podpowiedzi przycisku przy zegarze, np. { od: '15:58', do: '7:21' }
function nocMotywuTekst() {
  if (!nocMotywu) return null;
  const g = (chwila) => {
    const d = new Date(chwila.getTime() + 30 * 1000); // do pełnej minuty, jak na tvml.pl
    return d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0');
  };
  return { od: g(nocMotywu.od), do: g(nocMotywu.do) };
}

// ---------------------------------------------------------------- programy

let programy = [];
let programyPodstawowe = [];
let programyUkryte = []; // nie na pasku, tylko kafelki na nowej karcie (np. Klient, Serwer)
let yubikeyObecny = false;
let zaufaneHosty = []; // tylko nasze programy z programy.json, nie strony dopisane przez użytkownika

function wczytajProgramy() {
  const plik = path.join(KATALOG, 'programy.json');
  try {
    const lista = JSON.parse(fs.readFileSync(plik, 'utf8'));
    return lista
      .filter(p => p && p.id && p.adres)
      .map(p => ({ ...p, host: new URL(p.adres).hostname }));
  } catch (e) {
    dialog.showErrorBox('LivePL', 'Nie udało się wczytać listy programów (programy.json).\n\n' + e.message);
    return [];
  }
}

// ---------------------------------------------------------------- własne strony na pasku
// Każdy może przypiąć dowolną stronę (bieżącą kartę albo wpisany adres). Lista jest w ustawieniach.

const ID_STRONY = /^w-[a-f0-9]{8}$/;

function bezpiecznaIkonka(u) {
  if (typeof u !== 'string') return null;
  if (/^https?:\/\//i.test(u) && u.length < 600) return u;
  if (/^data:image\/(png|x-icon|vnd\.microsoft\.icon|gif|webp|jpeg|svg\+xml);base64,/i.test(u) && u.length < 24000) return u;
  return null;
}

function skrotNazwy(nazwa, host) {
  const zNazwy = String(nazwa || '').trim().split(/\s+/)[0] || '';
  const zHosta = String(host || '').replace(/^www\./, '').split('.')[0] || '';
  const s = zNazwy || zHosta || 'Strona';
  return (s.charAt(0).toUpperCase() + s.slice(1)).slice(0, 10);
}

function listaWlasnych() {
  return (Array.isArray(ustawienia.wlasneStrony) ? ustawienia.wlasneStrony : [])
    .filter(s => s && ID_STRONY.test(String(s.id)) && /^https?:\/\//i.test(String(s.adres)));
}

// Urząd (cert.livepl.eu) jest na pasku tylko z włożonym YubiKey albo gdy jego karta jest otwarta
function widoczny(p) {
  return !p.tylkoZYubiKey || yubikeyObecny || karty.has(p.id);
}

// obrazek programu ze strony (np. logo TVML), w nocy wersja nocna; ścieżka względem folderu ui
function obrazekProgramu(p) {
  if (!p.obrazek) return '';
  return '../' + ((nativeTheme.shouldUseDarkColors && p.obrazekNoc) || p.obrazek);
}

function zbudujProgramy() {
  // strona dopisana przez użytkownika, która jest już naszym programem, nie dubluje się na pasku
  const juzSa = new Set(programyPodstawowe.map(p => bezKoncowegoUkosnika(p.adres)));
  const wlasne = listaWlasnych().filter(s => !juzSa.has(bezKoncowegoUkosnika(s.adres))).map(s => ({
    id: s.id,
    nazwa: String(s.nazwa || hostZ(s.adres)),
    skrot: String(s.skrot || skrotNazwy(s.nazwa, hostZ(s.adres))),
    adres: s.adres,
    ikona: 'strona',
    ikonka: bezpiecznaIkonka(s.ikonka),
    wlasny: true,
    host: hostZ(s.adres)
  }));
  programy = ulozWedlugKolejnosci([...programyPodstawowe, ...wlasne]);
  // otwarte karty przypiętych stron wskazują na aktualne wpisy
  for (const p of wlasne) {
    const k = karty.get(p.id);
    if (k) k.program = p;
  }
}

// kolejność ikon na pasku ustawiona przeciąganiem (ustawienia.kolejnoscProgramow);
// programy spoza zapisanej listy (np. nowe albo świeżo przypięte) idą na koniec w zwykłej kolejności
function ulozWedlugKolejnosci(lista) {
  const zapisana = Array.isArray(ustawienia.kolejnoscProgramow) ? ustawienia.kolejnoscProgramow : [];
  const miejsce = new Map(zapisana.map((id, i) => [id, i]));
  return lista
    .map((p, i) => ({ p, klucz: miejsce.has(p.id) ? miejsce.get(p.id) : zapisana.length + i }))
    .sort((a, b) => a.klucz - b.klucz)
    .map(x => x.p);
}

// pasek przysyła nową kolejność widocznych ikon; ukryte (np. Urząd bez YubiKey) zostają na swoich miejscach
function ustawKolejnoscProgramow(ids) {
  const widoczne = programy.filter(widoczny).map(p => p.id);
  if (!Array.isArray(ids) || ids.length !== widoczne.length || new Set(ids).size !== ids.length ||
      !ids.every(id => typeof id === 'string' && widoczne.includes(id))) {
    wyslijStan();
    return;
  }
  let j = 0;
  ustawienia.kolejnoscProgramow = programy.map(p => (widoczny(p) ? ids[j++] : p.id));
  zapiszUstawienia();
  zbudujProgramy();
  wyslijStan();
}

// karta przeciągnięta w inne miejsce listy kart
function przesunKarte(id, gdzie) {
  const i = dodatkowe.indexOf(id);
  const n = Math.max(0, Math.min(dodatkowe.length - 1, Math.round(Number(gdzie) || 0)));
  if (i >= 0 && i !== n) {
    dodatkowe.splice(i, 1);
    dodatkowe.splice(n, 0, id);
  }
  wyslijStan();
}

function zapiszWlasne(lista) {
  ustawienia.wlasneStrony = lista;
  zapiszUstawienia();
  zbudujProgramy();
}

const nowyIdStrony = () => 'w-' + require('crypto').randomBytes(4).toString('hex');

// bieżąca karta staje się przypiętą stroną (bez przeładowania)
function przypnijKarte(id) {
  const k = karty.get(id);
  if (!k || k.program || k.nowa || !/^https?:/i.test(k.adres || '')) return;
  const host = hostZ(k.adres);
  const nazwa = skroc(String(k.tytul || '').trim() || host.replace(/^www\./, ''), 40);
  const wpis = { id: nowyIdStrony(), nazwa, skrot: skrotNazwy('', host), adres: k.adres, ikonka: bezpiecznaIkonka(k.ikonka) };
  zapiszWlasne([...listaWlasnych(), wpis]);
  karty.delete(id);
  dodatkowe = dodatkowe.filter(x => x !== id);
  k.id = wpis.id;
  k.program = programy.find(p => p.id === wpis.id);
  karty.set(wpis.id, k);
  if (aktywnaId === id) aktywnaId = wpis.id;
  if (pelnyEkran === id) pelnyEkran = wpis.id;
  dodajWiadomosc(`Dodano do paska: ${nazwa}`);
  wyslijStan();
}

// dopisanie strony z adresu (ekran „Moje strony na pasku”); zwraca opis błędu albo null
function dodajStrone(dane) {
  let adres = String((dane && dane.adres) || '').trim();
  if (!adres) return 'Wpisz adres strony';
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(adres)) adres = 'https://' + adres;
  let u;
  try { u = new URL(adres); } catch { return 'To nie jest poprawny adres strony'; }
  if (!/^https?:$/.test(u.protocol) || !u.hostname.includes('.')) return 'Adres ma wyglądać np. tak: https://example.com';
  if (listaWlasnych().some(s => bezKoncowegoUkosnika(s.adres) === bezKoncowegoUkosnika(u.href))) return 'Ta strona już jest na pasku';
  const nazwa = skroc(String((dane && dane.nazwa) || '').trim() || u.hostname.replace(/^www\./, ''), 40);
  zapiszWlasne([...listaWlasnych(), { id: nowyIdStrony(), nazwa, skrot: skrotNazwy((dane && dane.nazwa) || '', u.hostname), adres: u.href, ikonka: null }]);
  dodajWiadomosc(`Dodano do paska: ${nazwa}`);
  wyslijStan();
  return null;
}

function zmienNazweStrony(id, nazwa) {
  const n = skroc(String(nazwa || '').trim(), 40);
  if (!n) return;
  zapiszWlasne(listaWlasnych().map(s => (s.id === id ? { ...s, nazwa: n, skrot: skrotNazwy(n, hostZ(s.adres)) } : s)));
  wyslijStan();
}

// odpięta strona, jeśli jest otwarta, zostaje zwykłą kartą
function odepnijStrone(id) {
  const lista = listaWlasnych();
  if (!lista.some(s => s.id === id)) return;
  zapiszWlasne(lista.filter(s => s.id !== id));
  const k = karty.get(id);
  if (k) {
    const noweId = 'k' + (licznikKart++);
    karty.delete(id);
    k.id = noweId;
    k.program = null;
    karty.set(noweId, k);
    dodatkowe.push(noweId);
    if (aktywnaId === id) aktywnaId = noweId;
    if (pelnyEkran === id) pelnyEkran = noweId;
  }
  if (ustawienia.ostatnia === id) ustawienia.ostatnia = null;
  dodajWiadomosc('Odpięto stronę z paska');
  wyslijStan();
}

// ikonka przypiętej strony zapamiętana na następny start
function zapamietajIkonke(k) {
  if (!k.program || !k.program.wlasny) return;
  const ikonka = bezpiecznaIkonka(k.ikonka);
  if (!ikonka) return;
  const lista = listaWlasnych();
  const s = lista.find(x => x.id === k.program.id);
  if (!s || s.ikonka === ikonka) return;
  s.ikonka = ikonka;
  ustawienia.wlasneStrony = lista;
  zapiszUstawienia();
}

function hostZ(url) {
  try { return new URL(url).hostname; } catch { return ''; }
}

function pasujeHost(host, wzor) {
  return !!host && (host === wzor || host.endsWith('.' + wzor));
}

function nasza(url) {
  const h = hostZ(url);
  return zaufaneHosty.some(z => pasujeHost(h, z));
}

function bezKoncowegoUkosnika(url) {
  return String(url || '').replace(/#.*$/, '').replace(/\/+$/, '');
}

function programStartowy(url) {
  const u = bezKoncowegoUkosnika(url);
  return programy.find(p => bezKoncowegoUkosnika(p.adres) === u) || null;
}

function licznikZTytulu(tytul) {
  const t = String(tytul || '');
  const m = t.match(/^\s*\((\d+)\)/) || t.match(/(\d+)\s+nieprzeczytan/i);
  return m ? Number(m[1]) : 0;
}

// ---------------------------------------------------------------- okno i karty

let win = null;
let tray = null;
let wychodzimy = false;
const karty = new Map();
let dodatkowe = [];
let aktywnaId = null;
let licznikKart = 1;
let pelnyEkran = null;
const zamkniete = [];

function kolory() {
  return nativeTheme.shouldUseDarkColors ? KOLORY.ciemny : KOLORY.jasny;
}

function wyslij(kanal, dane) {
  if (win && !win.isDestroyed()) win.webContents.send(kanal, dane);
}

function laduj(k, url) {
  k.wc.loadURL(url).catch(() => { /* błąd obsługuje did-fail-load */ });
}

function utworzWidok(program) {
  const widok = new WebContentsView({
    webPreferences: {
      partition: PARTYCJA,
      contextIsolation: true,
      sandbox: true,
      spellcheck: true,
      plugins: true,
      backgroundThrottling: !(program && program.wTle),
      preload: path.join(__dirname, 'preload-strona.js')
    }
  });
  widok.setBackgroundColor(kolory().strona);
  try { widok.setBorderRadius(ZAOKRAGLENIE); } catch { /* starszy Electron */ }
  return widok;
}

function nowyObiektKarty(id, program, widok) {
  return {
    id, program, widok,
    wc: widok.webContents,
    tytul: program ? program.nazwa : 'Nowa karta',
    adres: '',
    ikonka: null,
    laduje: false,
    licznik: 0,
    blad: null,
    nowa: !program,
    szukanie: false
  };
}

function dodajWidok(k) {
  win.contentView.addChildView(k.widok);
  k.widok.setVisible(false);
  podepnij(k);
  ulozWidoki();
}

function kartaProgramu(p) {
  let k = karty.get(p.id);
  if (!k) {
    k = nowyObiektKarty(p.id, p, utworzWidok(p));
    karty.set(p.id, k);
    dodajWidok(k);
    laduj(k, p.adres);
  }
  return k;
}

function nowaKarta(url, { aktywuj: czyAktywowac = true, po = null } = {}) {
  const id = 'k' + (licznikKart++);
  const k = nowyObiektKarty(id, null, utworzWidok(null));
  karty.set(id, k);
  const i = po ? dodatkowe.indexOf(po) : -1;
  if (i >= 0) {
    // kolejne karty otwierane z tej samej karty ustawiają się za nią po kolei
    let j = i + 1;
    while (j < dodatkowe.length && karty.get(dodatkowe[j]) && karty.get(dodatkowe[j]).otwartaZ === po) j++;
    dodatkowe.splice(j, 0, id);
  } else {
    dodatkowe.push(id);
  }
  k.otwartaZ = po;
  dodajWidok(k);
  if (url) {
    k.nowa = false;
    k.adres = url;
    laduj(k, url);
  } else {
    otworzStroneNowejKarty(k);
  }
  if (czyAktywowac) aktywuj(id); else wyslijStan();
  return k;
}

function otworzStroneNowejKarty(k) {
  // oba obrazki: strona sama wybiera dzienny albo nocny, także po przełączeniu trybu przy otwartej karcie
  const opis = p => ({
    nazwa: p.nazwa, opis: p.opis || '', ikona: p.ikona, adres: p.adres, wlasny: !!p.wlasny,
    obrazek: p.obrazek ? '../' + p.obrazek : '', obrazekNoc: p.obrazekNoc ? '../' + p.obrazekNoc : ''
  });
  const dane = { naPasku: programy.filter(widoczny).map(opis), wiecej: programyUkryte.map(opis) };
  k.wc.loadFile(path.join(UI, 'nowa.html'), { query: { programy: JSON.stringify(dane) } }).catch(() => {});
}

function aktywuj(id) {
  const k = karty.get(id);
  if (!k) return;
  if (aktywnaId && aktywnaId !== id) {
    const poprzednia = karty.get(aktywnaId);
    if (poprzednia) {
      poprzednia.widok.setVisible(false);
      if (poprzednia.szukanie) {
        poprzednia.wc.stopFindInPage('keepSelection');
        poprzednia.szukanie = false;
        wyslij('szukaj-zamknij');
      }
    }
  }
  aktywnaId = id;
  k.widok.setVisible(true);
  ulozWidoki();
  if (k.program) {
    ustawienia.ostatnia = id;
    zapiszUstawienia();
    // program otwarty: jego zapis „nieprzeczytane” na pasku już nic nie mówi
    usunWiadomosci(w => w.licznik && w.kartaId === id);
  }
  if (k.nowa) {
    win.webContents.focus();
    wyslij('fokus-adres', adresAktywnej());
  } else if (win.isFocused()) {
    k.wc.focus();
  }
  wyslijStan();
}

function adresAktywnej() {
  const k = karty.get(aktywnaId);
  if (!k || k.nowa) return '';
  return k.blad ? k.blad.url : k.adres;
}

function przejdzDo(id) {
  if (karty.has(id)) return aktywuj(id);
  const p = programy.find(x => x.id === id);
  if (p) {
    kartaProgramu(p);
    aktywuj(p.id);
  }
}

function kliknietoProgram(id) {
  const p = programy.find(x => x.id === id);
  const k = karty.get(id);
  // ponowne kliknięcie aktywnego programu wraca na jego stronę, jeśli ktoś z niej wyszedł
  if (p && k && aktywnaId === id && (k.blad || !pasujeHost(hostZ(k.adres), p.host))) {
    k.blad = null;
    laduj(k, p.adres);
  }
  przejdzDo(id);
}

function kolejnosc() {
  return [...programy.filter(widoczny).map(p => p.id), ...dodatkowe];
}

function przelacz(krok) {
  const lista = kolejnosc();
  if (!lista.length) return;
  const i = Math.max(0, lista.indexOf(aktywnaId));
  przejdzDo(lista[(i + krok + lista.length) % lista.length]);
}

function zamknij(id) {
  const k = karty.get(id);
  if (!k || k.program) return;
  const i = dodatkowe.indexOf(id);
  if (k.adres && !k.nowa) {
    zamkniete.push(k.adres);
    if (zamkniete.length > 25) zamkniete.shift();
  }
  dodatkowe.splice(i, 1);
  karty.delete(id);
  if (pelnyEkran === id) pelnyEkran = null;
  win.contentView.removeChildView(k.widok);
  try { k.wc.close(); } catch { /* już zamknięta */ }
  if (aktywnaId === id) {
    aktywnaId = null;
    const nastepna = (k.otwartaZ && karty.has(k.otwartaZ) && k.otwartaZ) ||
      dodatkowe[i] || dodatkowe[i - 1] || ustawienia.ostatnia || (programy[0] && programy[0].id);
    if (nastepna) przejdzDo(nastepna);
  }
  wyslijStan();
}

function ulozWidoki() {
  if (!win || win.isDestroyed() || win.isMinimized()) return;
  const [w, h] = win.getContentSize();
  if (!w || !h) return;
  const zwykle = {
    x: PASEK_BOCZNY,
    y: PASEK_GORNY,
    width: Math.max(0, w - PASEK_BOCZNY - MARGINES_PRAWY),
    height: Math.max(0, h - PASEK_GORNY - PASEK_DOLNY)
  };
  for (const k of karty.values()) {
    const pelny = pelnyEkran === k.id;
    k.widok.setBounds(pelny ? { x: 0, y: 0, width: w, height: h } : zwykle);
    try { k.widok.setBorderRadius(pelny ? 0 : ZAOKRAGLENIE); } catch { /* bez zaokrągleń */ }
  }
}

// ---------------------------------------------------------------- pasek wiadomości

const wiadomosci = [];
let licznikWiadomosci = 1;
const ostatniePowiadomienie = new Map();

function godzina() {
  const d = new Date();
  return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
}

// jak długo komunikat zostaje na pasku: krótkie znikają same, pobrane po 2 minutach,
// poczta i aktualizacja zostają do kliknięcia (albo krzyżyka)
function czasWiadomosci(cel) {
  if (cel.akcja) return Infinity;
  if (cel.kartaId) return 30 * 60 * 1000;
  if (cel.plikId) return 2 * 60 * 1000;
  return 12 * 1000;
}

let sprzatanieWiadomosci = null;
function zaplanujSprzatanie() {
  clearTimeout(sprzatanieWiadomosci);
  const najblizsza = Math.min(...wiadomosci.map(w => w.znika));
  if (!Number.isFinite(najblizsza)) return;
  sprzatanieWiadomosci = setTimeout(() => {
    const teraz = Date.now();
    for (let i = wiadomosci.length - 1; i >= 0; i--) if (wiadomosci[i].znika <= teraz) wiadomosci.splice(i, 1);
    wyslijStan();
    zaplanujSprzatanie();
  }, Math.max(500, najblizsza - Date.now()));
}

function dodajWiadomosc(tekst, cel = {}, czas = null) {
  const w = { id: licznikWiadomosci++, czas: godzina(), kiedy: Date.now(), tekst: skroc(tekst, 140), ...cel };
  w.znika = Date.now() + (czas ?? czasWiadomosci(cel));
  wiadomosci.unshift(w);
  if (wiadomosci.length > 30) wiadomosci.pop();
  wyslijStan();
  zaplanujSprzatanie();
  return w.id;
}

function usunWiadomosc(id) {
  const i = wiadomosci.findIndex(x => x.id === id);
  if (i < 0) return;
  wiadomosci.splice(i, 1);
  wyslijStan();
  zaplanujSprzatanie();
}

function usunWiadomosci(pasuje) {
  let zmiana = false;
  for (let i = wiadomosci.length - 1; i >= 0; i--) {
    if (pasuje(wiadomosci[i])) { wiadomosci.splice(i, 1); zmiana = true; }
  }
  if (zmiana) { wyslijStan(); zaplanujSprzatanie(); }
}

// Licznik nieprzeczytanych z tytułu strony programu; zapis na pasku tylko wtedy, gdy naprawdę przybyło.
// k.znany to liczba, o której użytkownik już wie. Strona „nie wczytała się” nie zmienia licznika,
// a spadek (przeładowanie, chwilowy tytuł bez liczby, ponowne wczytanie po powrocie sieci) obniża
// k.znany dopiero, gdy trzyma się 20 s. Dlatego po wybudzeniu komputera ta sama liczba nie wraca
// jako nowy komunikat. Na pasku jest najwyżej jeden taki komunikat na program, z najnowszą liczbą.
const SPADEK_LICZNIKA_MS = 20 * 1000;

function policzNieprzeczytane(k, tytul) {
  if (k.blad) return;
  const nowy = licznikZTytulu(tytul);
  k.licznik = nowy;
  if (!k.zaladowanaRaz || k.znany == null) {
    k.znany = nowy;
    return;
  }
  if (nowy < k.znany) {
    if (!k.spadekLicznika) {
      k.spadekLicznika = setTimeout(() => {
        k.spadekLicznika = null;
        if (!k.blad && k.licznik < k.znany) k.znany = k.licznik;
      }, SPADEK_LICZNIKA_MS);
    }
    return;
  }
  clearTimeout(k.spadekLicznika);
  k.spadekLicznika = null;
  if (nowy === k.znany) return;
  k.znany = nowy;
  // strona dała własne powiadomienie: drugi zapis na pasku byłby powtórzeniem
  if (Date.now() - (ostatniePowiadomienie.get(k.id) || 0) < 8000) return;
  usunWiadomosci(w => w.licznik && w.kartaId === k.id);
  dodajWiadomosc(`${k.program.nazwa}: nieprzeczytane ${nowy}`, { kartaId: k.id, licznik: true });
}

function kliknietoWiadomosc(id) {
  const w = wiadomosci.find(x => x.id === id);
  if (!w) return;
  if (w.akcja === 'aktualizacja') {
    pokazOkno();
    pokazPanel('aktualizacja', { srodek: true });
  } else if (w.akcja === 'zaufanie' || w.akcja === 'urzadzenie' || w.akcja === 'ai') {
    pokazOkno();
    pokazPanel(w.akcja, { srodek: true });
  } else if (w.plikId) {
    const p = pobrane.find(x => x.id === w.plikId);
    if (p) shell.openPath(p.sciezka);
  } else if (w.kartaId && (karty.has(w.kartaId) || programy.some(p => p.id === w.kartaId))) {
    przejdzDo(w.kartaId);
  }
  usunWiadomosc(id);
}

function kartaDla(wc) {
  for (const k of karty.values()) if (k.wc === wc) return k;
  return null;
}

// ---------------------------------------------------------------- stan dla paska

let stanZaplanowany = null;

function wyslijStan() {
  if (stanZaplanowany) return;
  stanZaplanowany = setTimeout(() => {
    stanZaplanowany = null;
    wyslij('stan', stan());
    odswiezZasobnik();
    if (panelStan) wyslijDoPanelu();
  }, 16);
}

function sslKarty(k) {
  if (!k || k.nowa) return null;
  const adres = k.blad ? k.blad.url : k.adres;
  if (!adres) return null;
  if (k.blad) {
    const kod = Number(k.blad.kod);
    return kod <= -200 && kod > -300 ? { stan: 'zly', host: hostZ(adres) } : null;
  }
  if (/^http:/i.test(adres)) return { stan: 'brak', host: hostZ(adres) };
  if (!/^https:/i.test(adres)) return null;
  const info = naszeCA.infoHosta(hostZ(adres));
  return { stan: !info || info.ok ? 'ok' : 'zly', host: hostZ(adres), ...(info || {}) };
}

function stan() {
  const a = karty.get(aktywnaId);
  const programyStan = programy.filter(widoczny).map(p => {
    const k = karty.get(p.id);
    return {
      id: p.id,
      nazwa: p.nazwa,
      skrot: p.skrot || p.nazwa,
      ikona: p.ikona || 'strona',
      ikonka: p.wlasny ? (bezpiecznaIkonka(k && k.ikonka) || p.ikonka || '') : obrazekProgramu(p),
      szeroki: !!p.szeroki,
      wlasny: !!p.wlasny,
      licznik: k ? k.licznik : 0,
      laduje: !!(k && k.laduje),
      blad: !!(k && k.blad),
      otwarty: !!k
    };
  });
  let aktywna = null;
  if (a && !a.wc.isDestroyed()) {
    const adres = a.nowa ? '' : (a.blad ? a.blad.url : a.adres);
    aktywna = {
      id: a.id,
      program: !!a.program,
      adres,
      tytul: a.tytul,
      wstecz: a.wc.navigationHistory.canGoBack(),
      dalej: a.wc.navigationHistory.canGoForward(),
      laduje: a.laduje,
      zoom: Math.round(a.wc.getZoomFactor() * 100),
      bezpieczna: adres.startsWith('https:') && !a.blad,
      ssl: sslKarty(a),
      blad: !!a.blad,
      przypinalna: !a.program && !a.nowa && !a.blad && /^https?:/i.test(a.adres || '')
    };
  }
  const trwa = pobrane.filter(p => p.stan === 'trwa');
  return {
    programy: programyStan,
    karty: dodatkowe.map(id => {
      const k = karty.get(id);
      return { id, tytul: k.tytul || k.adres || 'Nowa karta', ikonka: k.ikonka, laduje: k.laduje, adres: k.adres, odAI: !!k.odAI };
    }),
    ai: dostepAI.stan(),
    aktywna,
    nieprzeczytane: programyStan.reduce((s, p) => s + (p.licznik || 0), 0),
    pobieranie: {
      trwa: trwa.length,
      procent: trwa.length ? Math.round(trwa.reduce((s, p) => s + (p.procent || 0), 0) / trwa.length) : null,
      ile: pobrane.length
    },
    zamkniete: zamkniete.length,
    aktualizacjaGotowa: ['gotowa', 'dostepna'].includes(aktualizacje.stan().stan),
    motyw: ustawienia.motyw,
    motywNoc: nocMotywuTekst(),
    wiadomosci: wiadomosci.slice(0, 8).map(w => ({
      id: w.id,
      czas: w.czas,
      tekst: w.tekst,
      nowa: Date.now() - w.kiedy < 15 * 60 * 1000
    }))
  };
}

// ---------------------------------------------------------------- zdarzenia stron

function podepnij(k) {
  const wc = k.wc;

  wc.on('page-title-updated', (e, tytul) => {
    k.tytul = tytul;
    if (k.program) policzNieprzeczytane(k, tytul);
    wyslijStan();
  });
  wc.on('did-finish-load', () => {
    setTimeout(() => { k.zaladowanaRaz = true; }, 4000);
    if (!k.program && !k.nowa && /^https?:/i.test(k.adres || '') && !ustawienia.podpowiedzPrzypinania) {
      ustawienia.podpowiedzPrzypinania = true;
      zapiszUstawienia();
      dodajWiadomosc('Ta strona może być zawsze pod ręką: kliknij „Dodaj” u góry, obok adresu', {}, 30 * 1000);
    }
  });
  wc.on('page-favicon-updated', (e, ikony) => {
    k.ikonka = (ikony && ikony[0]) || null;
    zapamietajIkonke(k);
    wyslijStan();
  });
  wc.on('did-start-loading', () => { k.laduje = true; wyslijStan(); });
  wc.on('did-stop-loading', () => { k.laduje = false; wyslijStan(); });

  const poNawigacji = (url) => {
    if (/^https?:|^blob:|^data:/i.test(url)) {
      k.adres = url;
      k.nowa = false;
      k.blad = null;
    }
    wyslijStan();
  };
  wc.on('did-navigate', (e, url) => poNawigacji(url));
  wc.on('did-navigate-in-page', (e, url, glowna) => { if (glowna) poNawigacji(url); });

  wc.on('did-fail-load', (e, kod, opis, url, glowna) => {
    if (!glowna || kod === -3) return; // -3: przerwane (np. przejście w trakcie ładowania)
    pokazBlad(k, String(kod), opis, url);
  });
  wc.on('render-process-gone', (e, szczegoly) => {
    if (szczegoly.reason === 'clean-exit') return;
    pokazBlad(k, 'awaria', szczegoly.reason, k.adres);
  });
  wc.on('unresponsive', () => { k.zawieszona = true; wyslijStan(); });
  wc.on('responsive', () => { k.zawieszona = false; wyslijStan(); });

  wc.on('will-navigate', (e) => {
    const url = e.url;
    if (/^mailto:/i.test(url)) {
      e.preventDefault();
      napiszMail(url);
      return;
    }
    // z pustej nowej karty: adres programu przełącza na jego zakładkę
    if (k.nowa) {
      const p = programStartowy(url);
      if (p) {
        e.preventDefault();
        przejdzDo(p.id);
        zamknij(k.id);
      }
    }
  });

  wc.setWindowOpenHandler(({ url, disposition }) => {
    if (/^mailto:/i.test(url)) {
      napiszMail(url);
      return { action: 'deny' };
    }
    // okienka pisane przez stronę (np. wydruk wiadomości) i pliki blob muszą znać okno, które je otworzyło
    if (!url || url === 'about:blank' || /^blob:/i.test(url) || disposition === 'new-window') {
      return {
        action: 'allow',
        overrideBrowserWindowOptions: {
          icon: IKONA,
          autoHideMenuBar: true,
          backgroundColor: kolory().strona,
          webPreferences: { partition: PARTYCJA, sandbox: true, contextIsolation: true, plugins: true }
        }
      };
    }
    if (!/^(https?|file|data):/i.test(url)) return { action: 'deny' };
    nowaKarta(url, { aktywuj: disposition !== 'background-tab', po: k.program ? null : k.id });
    return { action: 'deny' };
  });

  wc.on('did-create-window', (okienko) => {
    okienko.setMenuBarVisibility(false);
    const owc = okienko.webContents;
    owc.on('context-menu', (e, p) => menuKontekstowe(owc, p, null, okienko));
    owc.on('before-input-event', (e, wejscie) => {
      if (wejscie.type === 'keyDown' && wejscie.key === 'Escape' && !wejscie.control) okienko.close();
    });
    owc.setWindowOpenHandler(({ url }) => {
      if (/^https?:/i.test(url)) nowaKarta(url);
      return { action: 'deny' };
    });
  });

  wc.on('context-menu', (e, p) => menuKontekstowe(wc, p, k, win));
  wc.on('before-input-event', (e, wejscie) => skrot(e, wejscie));

  wc.on('found-in-page', (e, wynik) => {
    if (k.id === aktywnaId) wyslij('szukaj-wynik', { biezacy: wynik.activeMatchOrdinal, wszystkie: wynik.matches });
  });

  wc.on('zoom-changed', (e, kierunek) => zmienPowiekszenie(k, kierunek === 'in' ? 1 : -1));

  wc.on('enter-html-full-screen', () => {
    pelnyEkran = k.id;
    win.setFullScreen(true);
    ulozWidoki();
  });
  wc.on('leave-html-full-screen', () => {
    pelnyEkran = null;
    win.setFullScreen(false);
    ulozWidoki();
  });
}

function pokazBlad(k, kod, opis, url) {
  // kolejna nieudana próba tego samego adresu: strona błędu wydłuża przerwę przed następną
  k.proby = k.blad && k.blad.url === url ? (k.proby || 0) + 1 : 0;
  k.blad = { kod, url };
  k.adres = url || k.adres;
  k.laduje = false;
  k.wc.loadFile(path.join(UI, 'blad.html'), {
    query: { kod, opis: opis || '', adres: url || '', nazwa: k.program ? k.program.nazwa : '', proba: String(k.proby) }
  }).catch(() => {});
  wyslijStan();
}

function napiszMail(mailto) {
  const p = programy.find(x => x.id === 'poczta');
  if (!p) return;
  const k = kartaProgramu(p);
  // ten sam adres, który Afterlogic rejestruje jako obsługę linków mailto
  laduj(k, bezKoncowegoUkosnika(p.adres) + '/#mail-compose/to/' + encodeURIComponent(mailto));
  aktywuj(k.id);
}

// ---------------------------------------------------------------- powiększenie, druk, szukanie

function zmienPowiekszenie(k, kierunek) {
  if (!k) return;
  const teraz = k.wc.getZoomFactor();
  let nowe = 1;
  if (kierunek > 0) nowe = POWIEKSZENIA.find(z => z > teraz + 0.001) || POWIEKSZENIA[POWIEKSZENIA.length - 1];
  if (kierunek < 0) nowe = [...POWIEKSZENIA].reverse().find(z => z < teraz - 0.001) || POWIEKSZENIA[0];
  k.wc.setZoomFactor(nowe);
  wyslijStan();
}

function drukuj(wc) {
  if (!wc) return;
  wc.print({ printBackground: true }, () => {});
}

function szukaj(tekst, { wstecz = false, nowe = false } = {}) {
  const k = karty.get(aktywnaId);
  if (!k) return;
  if (!tekst) {
    k.wc.stopFindInPage('clearSelection');
    wyslij('szukaj-wynik', { biezacy: 0, wszystkie: 0 });
    return;
  }
  k.szukanie = true;
  k.wc.findInPage(tekst, { forward: !wstecz, findNext: nowe });
}

function zakonczSzukanie() {
  const k = karty.get(aktywnaId);
  if (k && k.szukanie) {
    k.wc.stopFindInPage('keepSelection');
    k.szukanie = false;
    k.wc.focus();
  }
}

// ---------------------------------------------------------------- skróty klawiszowe

function skrot(e, w) {
  if (w.type !== 'keyDown') return;
  const ctrl = w.control || w.meta;
  const k = karty.get(aktywnaId);
  const kod = w.code;
  let ok = true;

  if (ctrl && !w.shift && kod === 'KeyT') nowaKarta(null);
  else if (ctrl && w.shift && kod === 'KeyT') otworzZamknieta();
  else if (ctrl && (kod === 'KeyW' || kod === 'F4')) { if (k && !k.program) zamknij(k.id); }
  else if ((ctrl && w.shift && kod === 'KeyR') || (ctrl && kod === 'F5') || (w.shift && kod === 'F5')) odswiez(k, true);
  else if ((ctrl && kod === 'KeyR') || (!ctrl && kod === 'F5')) odswiez(k, false);
  else if ((ctrl && (kod === 'KeyL' || kod === 'KeyK')) || (w.alt && kod === 'KeyD') || kod === 'F6') { win.webContents.focus(); wyslij('fokus-adres', adresAktywnej()); }
  else if (ctrl && kod === 'KeyF') { win.webContents.focus(); wyslij('szukaj-otworz'); }
  else if (kod === 'F3') { win.webContents.focus(); wyslij('szukaj-otworz', { dalej: true, wstecz: w.shift }); }
  else if ((w.alt && kod === 'ArrowLeft') || kod === 'BrowserBack') { if (k) k.wc.navigationHistory.goBack(); }
  else if ((w.alt && kod === 'ArrowRight') || kod === 'BrowserForward') { if (k) k.wc.navigationHistory.goForward(); }
  else if (ctrl && (kod === 'Tab' || kod === 'PageDown' || kod === 'PageUp')) przelacz((w.shift && kod === 'Tab') || kod === 'PageUp' ? -1 : 1);
  else if (ctrl && !w.shift && /^Digit[1-9]$/.test(kod)) {
    const lista = kolejnosc();
    const n = Number(kod.slice(5));
    const id = n === 9 ? lista[lista.length - 1] : lista[n - 1];
    if (id) przejdzDo(id);
  }
  else if (ctrl && (kod === 'Equal' || kod === 'NumpadAdd')) zmienPowiekszenie(k, 1);
  else if (ctrl && (kod === 'Minus' || kod === 'NumpadSubtract')) zmienPowiekszenie(k, -1);
  else if (ctrl && (kod === 'Digit0' || kod === 'Numpad0')) zmienPowiekszenie(k, 0);
  else if (ctrl && kod === 'KeyP') { if (k) drukuj(k.wc); }
  else if (ctrl && kod === 'KeyJ') otworzFolderPobranych();
  else if ((ctrl && w.shift && kod === 'KeyI') || kod === 'F12') { if (k) k.wc.openDevTools({ mode: 'detach' }); }
  else if (kod === 'F11') win.setFullScreen(!win.isFullScreen());
  else ok = false;

  if (ok) e.preventDefault();
}

function odswiez(k, bezPamieci) {
  if (!k) return;
  if (k.blad && k.blad.url) {
    laduj(k, k.blad.url);
  } else if (k.nowa) {
    otworzStroneNowejKarty(k);
  } else if (bezPamieci) {
    k.wc.reloadIgnoringCache();
  } else {
    k.wc.reload();
  }
}

function otworzZamknieta() {
  const url = zamkniete.pop();
  if (url) nowaKarta(url);
}

// ---------------------------------------------------------------- menu pod prawym przyciskiem

function skroc(t, n) {
  t = String(t).replace(/\s+/g, ' ').trim();
  return t.length > n ? t.slice(0, n - 1) + '…' : t;
}

function menuKontekstowe(wc, p, k, okno) {
  const t = [];
  const sep = () => { if (t.length && t[t.length - 1].type !== 'separator') t.push({ type: 'separator' }); };
  const po = k && !k.program ? k.id : null;

  if (p.misspelledWord) {
    for (const s of (p.dictionarySuggestions || []).slice(0, 5)) {
      t.push({ label: s, click: () => wc.replaceMisspelling(s) });
    }
    if (!(p.dictionarySuggestions || []).length) t.push({ label: 'Brak podpowiedzi', enabled: false });
    t.push({ label: 'Dodaj do słownika', click: () => wc.session.addWordToSpellCheckerDictionary(p.misspelledWord) });
    sep();
  }

  if (p.linkURL && !/^javascript:/i.test(p.linkURL)) {
    const mail = /^mailto:/i.test(p.linkURL);
    if (mail) {
      t.push({ label: 'Napisz wiadomość', click: () => napiszMail(p.linkURL) });
      t.push({ label: 'Kopiuj adres e-mail', click: () => clipboard.writeText(decodeURIComponent(p.linkURL.replace(/^mailto:/i, '').split('?')[0])) });
    } else {
      t.push({ label: 'Otwórz link w nowej karcie', click: () => nowaKarta(p.linkURL, { po }) });
      t.push({ label: 'Otwórz w zwykłej przeglądarce', click: () => shell.openExternal(p.linkURL) });
      t.push({ label: 'Kopiuj adres linku', click: () => clipboard.writeText(p.linkURL) });
      t.push({ label: 'Pobierz plik z linku', click: () => wc.downloadURL(p.linkURL) });
    }
    sep();
  }

  if (p.mediaType === 'image' && p.srcURL) {
    t.push({ label: 'Kopiuj obraz', click: () => wc.copyImageAt(p.x, p.y) });
    t.push({ label: 'Zapisz obraz', click: () => wc.downloadURL(p.srcURL) });
    if (/^https?:/i.test(p.srcURL)) t.push({ label: 'Otwórz obraz w nowej karcie', click: () => nowaKarta(p.srcURL, { po }) });
    sep();
  }

  if (p.isEditable) {
    const f = p.editFlags || {};
    t.push({ label: 'Cofnij', enabled: f.canUndo, click: () => wc.undo() });
    t.push({ label: 'Ponów', enabled: f.canRedo, click: () => wc.redo() });
    sep();
    t.push({ label: 'Wytnij', enabled: f.canCut, click: () => wc.cut() });
    t.push({ label: 'Kopiuj', enabled: f.canCopy, click: () => wc.copy() });
    t.push({ label: 'Wklej', enabled: f.canPaste, click: () => wc.paste() });
    t.push({ label: 'Wklej bez formatowania', enabled: f.canPaste, click: () => wc.pasteAndMatchStyle() });
    t.push({ label: 'Zaznacz wszystko', enabled: f.canSelectAll, click: () => wc.selectAll() });
  } else if (p.selectionText && p.selectionText.trim()) {
    const tekst = p.selectionText.trim();
    t.push({ label: 'Kopiuj', click: () => wc.copy() });
    t.push({
      label: `Szukaj w Google: „${skroc(tekst, 28)}”`,
      click: () => nowaKarta('https://www.google.com/search?q=' + encodeURIComponent(tekst), { po })
    });
  } else if (!p.linkURL && p.mediaType === 'none') {
    const h = wc.navigationHistory;
    t.push({ label: 'Wstecz', enabled: h.canGoBack(), click: () => h.goBack() });
    t.push({ label: 'Dalej', enabled: h.canGoForward(), click: () => h.goForward() });
    t.push({ label: 'Odśwież', click: () => (k ? odswiez(k, false) : wc.reload()) });
    sep();
    t.push({ label: 'Drukuj…', click: () => drukuj(wc) });
  }

  sep();
  t.push({ label: 'Zbadaj element', click: () => wc.inspectElement(p.x, p.y) });

  Menu.buildFromTemplate(t).popup({ window: okno || win });
}

// ---------------------------------------------------------------- pobieranie plików

const pobrane = [];
let licznikPobran = 1;

// pobrane pliki trafiają prosto do folderu Pobrane, jak w każdej przeglądarce
function folderPobranych() {
  return app.getPath('downloads');
}

function otworzFolderPobranych() {
  const f = folderPobranych();
  fs.mkdirSync(f, { recursive: true });
  shell.openPath(f);
}

function bezpiecznaNazwa(n) {
  const czysta = String(n || '').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').replace(/^\.+/, '').trim();
  return (czysta || 'plik').slice(0, 180);
}

function wolnaNazwa(folder, nazwa) {
  const ext = path.extname(nazwa);
  const rdzen = path.basename(nazwa, ext);
  let sciezka = path.join(folder, nazwa);
  for (let i = 1; fs.existsSync(sciezka); i++) sciezka = path.join(folder, `${rdzen} (${i})${ext}`);
  return sciezka;
}

function obslugaPobierania(ses) {
  ses.on('will-download', (e, item) => {
    const folder = folderPobranych();
    fs.mkdirSync(folder, { recursive: true });
    const sciezka = wolnaNazwa(folder, bezpiecznaNazwa(item.getFilename()));
    item.setSavePath(sciezka);
    const wpis = { id: licznikPobran++, nazwa: path.basename(sciezka), sciezka, stan: 'trwa', procent: 0, item };
    pobrane.unshift(wpis);
    if (pobrane.length > 30) pobrane.pop();
    let ostatnio = 0;
    item.on('updated', (ev, st) => {
      const cale = item.getTotalBytes();
      wpis.procent = cale ? Math.round((item.getReceivedBytes() / cale) * 100) : 0;
      wpis.stan = st === 'interrupted' ? 'przerwane' : 'trwa';
      const teraz = Date.now();
      if (teraz - ostatnio > 300) { ostatnio = teraz; wyslijStan(); }
    });
    item.once('done', (ev, st) => {
      wpis.stan = st === 'completed' ? 'gotowe' : st === 'cancelled' ? 'anulowane' : 'blad';
      wpis.procent = 100;
      wpis.item = null;
      wyslijStan();
      if (wpis.stan === 'gotowe') {
        wyslij('pobrano', { id: wpis.id, nazwa: wpis.nazwa });
        dodajWiadomosc(`Pobrano: ${wpis.nazwa}`, { plikId: wpis.id });
      }
      if (wpis.stan === 'blad') wyslij('pobrano', { id: wpis.id, nazwa: wpis.nazwa, blad: true });
    });
    wyslijStan();
  });
}

// ---------------------------------------------------------------- uprawnienia

const decyzje = new Map();
const ZAWSZE = ['clipboard-sanitized-write', 'fullscreen', 'pointerLock', 'speaker-selection', 'background-sync', 'storage-access', 'top-level-storage-access'];
const DLA_NASZYCH = ['notifications', 'media', 'clipboard-read', 'display-capture', 'fileSystem', 'idle-detection', 'keyboardLock', 'window-management', 'openExternal'];
const PYTAJ = {
  notifications: 'wyświetlać powiadomienia',
  media: 'używać kamery lub mikrofonu',
  geolocation: 'znać położenie tego komputera',
  'clipboard-read': 'odczytywać schowek'
};

function uprawnienia(ses) {
  ses.setPermissionRequestHandler((wc, uprawnienie, cb, szczegoly) => {
    const url = (szczegoly && szczegoly.requestingUrl) || (wc && wc.getURL()) || '';
    if (ZAWSZE.includes(uprawnienie)) return cb(true);
    if (nasza(url)) return cb(DLA_NASZYCH.includes(uprawnienie));
    const opis = PYTAJ[uprawnienie];
    if (!opis) return cb(false);
    const host = hostZ(url);
    const klucz = host + '|' + uprawnienie;
    if (decyzje.has(klucz)) return cb(decyzje.get(klucz));
    dialog.showMessageBox(win, {
      type: 'question',
      title: 'LivePL',
      message: `Strona ${host} chce ${opis}.`,
      detail: 'Decyzja obowiązuje do zamknięcia programu.',
      buttons: ['Zezwól', 'Nie zezwalaj'],
      defaultId: 1,
      cancelId: 1,
      noLink: true
    }).then(r => {
      const zgoda = r.response === 0;
      decyzje.set(klucz, zgoda);
      cb(zgoda);
    }).catch(() => cb(false));
  });

  ses.setPermissionCheckHandler((wc, uprawnienie, origin) => {
    if (ZAWSZE.includes(uprawnienie)) return true;
    if (nasza(origin)) return !['hid', 'serial', 'usb'].includes(uprawnienie);
    const klucz = hostZ(origin) + '|' + uprawnienie;
    if (decyzje.has(klucz)) return decyzje.get(klucz);
    return !['notifications', 'media', 'geolocation', 'clipboard-read', 'display-capture', 'hid', 'serial', 'usb', 'midi', 'midiSysex'].includes(uprawnienie);
  });

  ses.setDisplayMediaRequestHandler(wybierzEkran);
}

// udostępnianie ekranu (np. tablica w Sevetoo) – własne okienko wyboru
let wyborOtwarty = false;

async function wybierzEkran(zadanie, callback) {
  if (wyborOtwarty) return callback(null);
  let zrodla;
  try {
    zrodla = await desktopCapturer.getSources({ types: ['screen', 'window'], thumbnailSize: { width: 320, height: 200 } });
  } catch {
    return callback(null);
  }
  wyborOtwarty = true;
  const okno = new BrowserWindow({
    parent: win,
    modal: true,
    width: 780,
    height: 580,
    resizable: false,
    minimizable: false,
    maximizable: false,
    title: 'Udostępnij ekran',
    icon: IKONA,
    autoHideMenuBar: true,
    backgroundColor: kolory().tlo,
    show: false,
    webPreferences: { preload: path.join(__dirname, 'preload-wybor.js'), contextIsolation: true, sandbox: true }
  });
  let zakonczone = false;
  const zakoncz = (wynik) => {
    if (zakonczone) return;
    zakonczone = true;
    wyborOtwarty = false;
    ipcMain.removeHandler('wybor:lista');
    ipcMain.removeAllListeners('wybor:wynik');
    try { callback(wynik); } catch { /* strona mogła się zamknąć */ }
    if (!okno.isDestroyed()) okno.close();
  };
  ipcMain.handle('wybor:lista', () => ({
    host: hostZ(zadanie.securityOrigin || (zadanie.frame && zadanie.frame.url) || ''),
    dzwiek: !!zadanie.audioRequested,
    zrodla: zrodla.map(z => ({
      id: z.id,
      nazwa: z.name,
      ekran: z.id.startsWith('screen:'),
      miniatura: z.thumbnail.toDataURL()
    }))
  }));
  ipcMain.on('wybor:wynik', (e, w) => {
    const z = w && zrodla.find(x => x.id === w.id);
    if (!z) return zakoncz(null);
    const strumienie = { video: z };
    if (w.dzwiek) strumienie.audio = 'loopback';
    zakoncz(strumienie);
  });
  okno.on('closed', () => zakoncz(null));
  okno.once('ready-to-show', () => okno.show());
  okno.loadFile(path.join(UI, 'wybor.html'));
}

// ---------------------------------------------------------------- panel LivePL (menu ⋮, pobrane, menu karty)
// Własny panel w kolorach programu zamiast menu Windows. To osobny widok nad stroną,
// bo pasek LivePL nie może rysować na wierzchu strony.

const SZER_PANELU = 300;
let panel = null;
let panelStan = null;
let panelNr = 0;
let panelZamkniety = 0;
let panelOstatni = null;

function utworzPanel() {
  panel = new WebContentsView({
    webPreferences: { preload: path.join(__dirname, 'preload-panel.js'), contextIsolation: true, sandbox: true }
  });
  panel.setBackgroundColor('#00000000');
  panel.setVisible(false);
  // test: LIVEPL_PANEL_TEST=1 zostawia panel otwarty, gdy okno nie jest na wierzchu
  panel.webContents.on('blur', () => {
    if (process.env.LIVEPL_PANEL_TEST) return;
    if (panelStan && Date.now() - panelStan.otwartyO < 250) return;
    schowajPanel(false);
  });
  panel.webContents.on('before-input-event', (e, w) => {
    if (w.type === 'keyDown' && w.key === 'Escape') {
      e.preventDefault();
      schowajPanel(true);
    }
  });
  panel.webContents.on('did-finish-load', wyslijDoPanelu);
  panel.webContents.loadFile(path.join(UI, 'panel.html'));
}

function danePanelu() {
  const k = karty.get(aktywnaId);
  const adres = k && !k.nowa ? (k.blad ? k.blad.url : k.adres) : '';
  const kp = panelStan.kartaId ? karty.get(panelStan.kartaId) : null;
  let nazwa = '';
  if (k) nazwa = k.program ? k.program.nazwa : (k.nowa ? 'Nowa karta' : (k.tytul || 'Strona'));
  return {
    nr: panelStan.nr,
    rodzaj: panelStan.rodzaj,
    nazwa,
    host: hostZ(adres),
    adres,
    zewnetrzny: /^https?:/i.test(adres),
    zamkniete: zamkniete.length,
    zoom: k ? Math.round(k.wc.getZoomFactor() * 100) : 100,
    chowaj: ustawienia.chowajDoZasobnika,
    autostart: czyAutostart(),
    wersja: app.getVersion(),
    silnik: process.versions.chrome,
    certOsobisty: certOsobisty.ostatniaLista(),
    zaufanie: zaufanie.ostatniStan(),
    moznaPrzypiac: !!(k && !k.program && !k.nowa && /^https?:/i.test(k.adres || '')),
    wlasneStrony: programy.filter(p => p.wlasny).map(p => ({
      id: p.id, nazwa: p.nazwa, adres: p.adres,
      ikonka: bezpiecznaIkonka((karty.get(p.id) || {}).ikonka) || p.ikonka || ''
    })),
    bladStrony: panelStan.bladStrony || null,
    certyfikatyWindows: panelStan.rodzaj === 'menu' ? certWin.ostatnieListy() : null,
    urzadzenie: urzadzenie.ostatniStan(),
    urzadzenieTrwa: trwaRejestracja,
    zaufanieTrwa: trwaZaufanie,
    haslo: panelStan.haslo || null,
    haslaWybor: panelStan.haslaWybor || null,
    hasla: panelStan.rodzaj === 'menu' || panelStan.rodzaj === 'hasla'
      ? { wlaczone: ustawienia.hasla !== false, dostepne: hasla.dostepne(), ...(() => { try { return hasla.lista(); } catch { return { wpisy: [], nigdy: [] }; } })() }
      : null,
    programMenu: (() => {
      const p = panelStan.programId ? programy.find(x => x.id === panelStan.programId) : null;
      return p ? { id: p.id, nazwa: p.nazwa, adres: p.adres, wlasny: !!p.wlasny } : null;
    })(),
    podpis: panelStan.podpis || null,
    ssl: sslKarty(k),
    aktualizacja: { ...aktualizacje.stan(), kopia: aktualizacje.kopia(), auto: ustawienia.autoAktualizacje !== false },
    folderPobranych: folderPobranych(),
    folderDanych: app.getPath('userData'),
    pobrane: pobrane.slice(0, 12).map(p => ({ id: p.id, nazwa: p.nazwa, stan: p.stan, procent: p.procent || 0 })),
    ai: dostepAI.danePanelu(),
    karta: kp ? { id: kp.id, adres: kp.blad ? kp.blad.url : kp.adres, nowa: !!kp.nowa, inne: dodatkowe.length > 1, przypinalna: !kp.nowa && /^https?:/i.test(kp.adres || '') } : null
  };
}

function wyslijDoPanelu() {
  if (panel && panelStan && !panel.webContents.isLoading()) panel.webContents.send('panel:dane', danePanelu());
}

function pokazPanel(rodzaj, kotwica, dodatki = {}) {
  // drugie kliknięcie w ten sam przycisk zamyka panel
  if (panelOstatni === rodzaj && Date.now() - panelZamkniety < 350 && !dodatki.kartaId) return;
  if (!panel) utworzPanel();
  if (rodzaj === 'aktualizacja') aktualizacjaCzeka = false;
  panelStan = { rodzaj, kotwica, wys: 0, nr: ++panelNr, otwartyO: Date.now(), ...dodatki };
  win.contentView.addChildView(panel);
  ulozPanel();
  panel.setVisible(true);
  wyslijDoPanelu();
  panel.webContents.focus();
}

function ulozPanel() {
  if (!panel || !panelStan || !win || win.isDestroyed()) return;
  const [w, h] = win.getContentSize();
  const wys = Math.max(48, Math.min(panelStan.wys || 320, h - 16));
  const k = panelStan.kotwica;
  let x;
  let y;
  if (k.prawo !== undefined) {
    // nad przyciskiem (dol) albo pod nim (gora), wyrównany do jego prawej krawędzi
    x = k.prawo - SZER_PANELU;
    y = k.dol !== undefined ? k.dol - wys - 6 : k.gora;
  } else if (k.srodek) {
    x = (w - SZER_PANELU) / 2;
    y = (h - wys) / 2;
  } else if (k.dol !== undefined) {
    x = k.lewo;
    y = k.dol - wys - 6;
  } else {
    x = k.lewo;
    y = k.gora;
  }
  x = Math.max(8, Math.min(x, w - SZER_PANELU - 8));
  y = Math.max(8, Math.min(y, h - wys - 8));
  panel.setBounds({ x: Math.round(x), y: Math.round(y), width: SZER_PANELU, height: Math.round(wys) });
}

function schowajPanel(oddajFokus) {
  if (!panel || !panelStan) return;
  if (oczekujacyPodpis) {
    const p = oczekujacyPodpis;
    oczekujacyPodpis = null;
    p.odrzuc(new Error('anulowano'));
  }
  panelOstatni = panelStan.rodzaj;
  panelZamkniety = Date.now();
  panelStan = null;
  panel.setVisible(false);
  if (oddajFokus) {
    const k = karty.get(aktywnaId);
    if (k && !k.nowa) k.wc.focus();
  }
  pokazCzekajacaAktualizacje();
}

ipcMain.on('panel:wysokosc', (e, h) => {
  if (!panel || e.sender !== panel.webContents || !panelStan) return;
  panelStan.wys = Math.ceil(Number(h) || 0);
  ulozPanel();
});

ipcMain.on('panel:akcja', (e, nazwa, arg) => {
  if (!panel || e.sender !== panel.webContents) return;
  akcjaPanelu(String(nazwa), arg);
});

// licencje w osobnym okienku: zamyka się przyciskiem, krzyżykiem albo Esc
let oknoLicencji = null;

function otworzOknoLicencji() {
  if (oknoLicencji && !oknoLicencji.isDestroyed()) {
    oknoLicencji.focus();
    return;
  }
  oknoLicencji = new BrowserWindow({
    parent: win,
    width: 680,
    height: 460,
    minWidth: 460,
    minHeight: 320,
    title: 'Licencje i podziękowania',
    icon: IKONA,
    autoHideMenuBar: true,
    minimizable: false,
    backgroundColor: kolory().strona,
    show: false,
    webPreferences: { contextIsolation: true, sandbox: true }
  });
  oknoLicencji.setMenuBarVisibility(false);
  oknoLicencji.webContents.on('before-input-event', (e, w) => {
    if (w.type === 'keyDown' && w.key === 'Escape') oknoLicencji.close();
  });
  oknoLicencji.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  oknoLicencji.once('ready-to-show', () => oknoLicencji.show());
  oknoLicencji.on('closed', () => { oknoLicencji = null; });
  oknoLicencji.loadFile(path.join(UI, 'licencje.html'), { query: { silnik: path.dirname(process.execPath) } });
}

const ZOSTAW_OTWARTY = ['cert-osobisty-odswiez', 'zaufanie-odswiez', 'strona-dodaj', 'strona-nazwa', 'strona-usun',
  'haslo-usun', 'haslo-nigdy-usun', 'hasla-wlacz', 'hasla-odswiez', 'cert-win-odswiez', 'cert-win-kopiuj',
  'urzadzenie-zarejestruj', 'urzadzenie-sprawdz', 'zoom-', 'zoom+', 'zoom0', 'tlo', 'autostart', 'pobrany-anuluj', 'aktualizacje-sprawdz', 'aktualizacje-pobierz', 'auto-aktualizacje',
  'ai-poziom', 'ai-claude', 'ai-kopiuj-code', 'ai-kopiuj-mcp'];

function akcjaPanelu(nazwa, arg) {
  if (nazwa === 'podpis-tak' || nazwa === 'podpis-nie') {
    const p = oczekujacyPodpis;
    oczekujacyPodpis = null;
    if (p) {
      if (nazwa === 'podpis-tak') p.potwierdz();
      else p.odrzuc(new Error('anulowano'));
    }
    schowajPanel(false);
    return;
  }
  const k = karty.get(aktywnaId);
  const kp = typeof arg === 'string' ? karty.get(arg) : null;
  if (!ZOSTAW_OTWARTY.includes(nazwa)) schowajPanel(!['szukaj', 'nowa'].includes(nazwa));
  switch (nazwa) {
    case 'nowa': nowaKarta(null); break;
    case 'przywroc': otworzZamknieta(); break;
    case 'drukuj': if (k) drukuj(k.wc); break;
    case 'szukaj': win.webContents.focus(); wyslij('szukaj-otworz'); break;
    case 'zoom-': zmienPowiekszenie(k, -1); break;
    case 'zoom+': zmienPowiekszenie(k, 1); break;
    case 'zoom0': zmienPowiekszenie(k, 0); break;
    case 'zewnetrzna': {
      const a = adresAktywnej();
      if (/^https?:/i.test(a)) shell.openExternal(a);
      break;
    }
    case 'kopiuj': {
      const a = adresAktywnej();
      if (a) { clipboard.writeText(a); dodajWiadomosc('Skopiowano adres strony'); }
      break;
    }
    case 'narzedzia': if (k) k.wc.openDevTools({ mode: 'detach' }); break;
    case 'zakoncz': zakoncz(); break;
    // dostęp dla AI (ustawienia → Dostęp dla AI, lampka „AI” na pasku)
    case 'ai-poziom': dostepAI.ustawPoziom(arg); break;
    case 'ai-zatrzymaj': dostepAI.ustawPoziom('wylaczony'); break;
    case 'ai-claude': {
      let w;
      try { w = dostepAI.podlaczClaudeDesktop(); } catch (e) { w = { ok: false, tekst: 'Nie udało się zapisać ustawień Claude: ' + e.message }; }
      dodajWiadomosc((w.ok ? 'Podłączono LivePL do Claude. ' : '') + w.tekst, {}, 30 * 1000);
      break;
    }
    case 'ai-kopiuj-code': clipboard.writeText(dostepAI.polecenieClaudeCode()); dodajWiadomosc('Skopiowano polecenie dla Claude Code: wklej je w terminalu'); break;
    case 'ai-kopiuj-mcp': clipboard.writeText(dostepAI.tekstKonfiguracji()); dodajWiadomosc('Skopiowano ustawienia MCP: wklej je w ustawieniach swojego AI'); break;
    case 'licencje': otworzOknoLicencji(); break;
    case 'cert-osobisty-odswiez':
      certOsobisty.lista().catch(() => {}).then(() => wyslijDoPanelu());
      break;
    case 'zaufanie-odswiez':
      zaufanie.stan().catch(() => {}).then(() => wyslijDoPanelu());
      break;
    case 'zaufanie-komputer':
    case 'zaufanie-uzytkownik':
      zainstalujZaufanie(nazwa === 'zaufanie-komputer');
      break;
    case 'zaufanie-pozniej':
      ustawienia.zaufaniePozniej = Date.now();
      zapiszUstawienia();
      break;
    case 'haslo-zapisz': case 'haslo-nigdy': case 'haslo-nie': case 'haslo-wypelnij': case 'haslo-kopiuj':
    case 'haslo-usun': case 'haslo-nigdy-usun': case 'hasla-wlacz': case 'hasla-odswiez':
      akcjaHasla(nazwa, arg);
      break;
    case 'urzadzenie-zarejestruj': zarejestrujUrzadzenie({ zaufanie: !!(arg && arg.zaufanie) }); break;
    case 'urzadzenie-sprawdz':
      sprawdzUrzadzenie(true);
      zaufanie.stan().catch(() => {}).then(() => { if (panelStan) wyslijDoPanelu(); });
      break;
    case 'urzadzenie-pozniej':
      ustawienia.urzadzeniePozniej = Date.now();
      zapiszUstawienia();
      break;
    case 'cert-win-odswiez':
      certWin.lista().catch((e) => dodajWiadomosc('Nie udało się odczytać certyfikatów: ' + e.message)).then(() => { if (panelStan) wyslijDoPanelu(); });
      break;
    case 'cert-win-pokaz':
      try { shell.openPath(certWin.zapiszDoPliku(arg.odcisk, arg.magazyn)); } catch (e) { dodajWiadomosc(e.message); }
      break;
    case 'cert-win-eksport': {
      const nazwaPliku = String(arg.nazwa || 'certyfikat').replace(/[^\p{L}\p{N} ._-]+/gu, '-').slice(0, 60) + '.cer';
      dialog.showSaveDialog(win, { title: 'Zapisz certyfikat', defaultPath: path.join(folderPobranych(), nazwaPliku), filters: [{ name: 'Certyfikat', extensions: ['cer'] }] })
        .then((w) => {
          if (w.canceled || !w.filePath) return;
          certWin.zapiszDoPliku(arg.odcisk, arg.magazyn, w.filePath);
          dodajWiadomosc(`Zapisano certyfikat: ${path.basename(w.filePath)}`);
        })
        .catch((e) => dodajWiadomosc('Nie zapisano certyfikatu: ' + e.message));
      break;
    }
    case 'cert-win-kopiuj':
      if (typeof arg === 'string') { clipboard.writeText(arg); dodajWiadomosc('Skopiowano odcisk certyfikatu'); }
      break;
    case 'cert-win-menedzer':
      shell.openPath(path.join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'certmgr.msc'));
      break;
    case 'przypnij': przypnijKarte(aktywnaId); break;
    case 'karta-przypnij': if (kp) przypnijKarte(kp.id); break;
    case 'program-odepnij':
    case 'strona-usun':
      if (typeof arg === 'string') odepnijStrone(arg);
      if (panelStan) wyslijDoPanelu();
      break;
    case 'strona-dodaj':
      if (panelStan) {
        panelStan.bladStrony = dodajStrone(arg);
        wyslijDoPanelu();
      }
      break;
    case 'strona-nazwa':
      if (arg && typeof arg.id === 'string') zmienNazweStrony(arg.id, arg.nazwa);
      break;
    case 'program-zewnetrzna': {
      const p = programy.find(x => x.id === arg);
      if (p) shell.openExternal(p.adres);
      break;
    }
    case 'program-kopiuj': {
      const p = programy.find(x => x.id === arg);
      if (p) { clipboard.writeText(p.adres); dodajWiadomosc('Skopiowano adres strony'); }
      break;
    }
    case 'aktualizacje-sprawdz': aktualizacje.sprawdz(); break;
    case 'aktualizacje-pobierz': aktualizacje.pobierz(); break;
    case 'aktualizacje-pozniej': break; // okienko się zamyka; zapytamy przy następnym uruchomieniu
    case 'aktualizacje-uruchom': uruchomPonownie(); break;
    case 'aktualizacje-przywroc': if (aktualizacje.zaplanujPowrot()) uruchomPonownie(); break;
    case 'auto-aktualizacje': ustawienia.autoAktualizacje = ustawienia.autoAktualizacje === false; zapiszUstawienia(); break;
    case 'folder-pobranych': otworzFolderPobranych(); break;
    case 'pobrany-otworz': {
      const p = pobrane.find(x => x.id === arg);
      if (p) shell.openPath(p.sciezka);
      break;
    }
    case 'pobrany-folder': {
      const p = pobrane.find(x => x.id === arg);
      if (p) shell.showItemInFolder(p.sciezka);
      break;
    }
    case 'pobrany-anuluj': {
      const p = pobrane.find(x => x.id === arg);
      if (p && p.item) p.item.cancel();
      break;
    }
    case 'tlo': ustawienia.chowajDoZasobnika = !ustawienia.chowajDoZasobnika; zapiszUstawienia(); break;
    case 'autostart': ustawAutostart(!czyAutostart()); break;
    case 'programy': shell.openPath(path.join(KATALOG, 'programy.json')); break;
    case 'restart': app.relaunch(); wychodzimy = true; app.quit(); break;
    case 'pamiec':
      session.fromPartition(PARTYCJA).clearCache().then(() => dodajWiadomosc('Wyczyszczono pamięć podręczną, logowania zostały'));
      break;
    case 'wyloguj':
      session.fromPartition(PARTYCJA).clearStorageData().then(() => {
        for (const x of karty.values()) odswiez(x, true);
        dodajWiadomosc('Wylogowano ze wszystkich stron');
      });
      break;
    case 'karta-odswiez': if (kp) odswiez(kp, false); break;
    case 'karta-zewnetrzna': {
      const a = kp ? (kp.blad ? kp.blad.url : kp.adres) : '';
      if (/^https?:/i.test(a)) shell.openExternal(a);
      break;
    }
    case 'karta-kopiuj': {
      const a = kp ? (kp.blad ? kp.blad.url : kp.adres) : '';
      if (a) { clipboard.writeText(a); dodajWiadomosc('Skopiowano adres strony'); }
      break;
    }
    case 'karta-zamknij': if (kp) zamknij(kp.id); break;
    case 'karta-zamknij-inne': if (kp) dodatkowe.filter(x => x !== kp.id).forEach(zamknij); break;
  }
  if (ZOSTAW_OTWARTY.includes(nazwa)) setTimeout(wyslijDoPanelu, 50);
}

// ---------------------------------------------------------------- autostart i zasobnik

function argumentyAutostartu() {
  return app.isPackaged ? ['--w-tle'] : [path.resolve(KATALOG), '--w-tle'];
}

function czyAutostart() {
  try {
    return app.getLoginItemSettings({ path: process.execPath, args: argumentyAutostartu() }).openAtLogin;
  } catch {
    return false;
  }
}

function ustawAutostart(wl) {
  app.setLoginItemSettings({ openAtLogin: wl, path: process.execPath, args: argumentyAutostartu() });
}

function pokazOkno() {
  if (!win || win.isDestroyed()) return;
  if (win.isMinimized()) win.restore();
  win.show();
  win.focus();
  const k = karty.get(aktywnaId);
  if (k && !k.nowa) k.wc.focus();
}

// okienko na środku, gdy LivePL jest na ekranie i nic innego nie jest otwarte. Gdy akurat jest inne
// okienko (na nowym komputerze zaraz po starcie: certyfikat urzędu albo rejestracja) albo okno jest
// schowane: wiadomość na pasku, a okienko aktualizacji pokaże się samo, gdy tamto się zamknie.
let aktualizacjaCzeka = false;
let czekajacaAktualizacja = null;
function zaproponujAktualizacje(s) {
  if (panelStan && panelStan.rodzaj === 'aktualizacja') return wyslijDoPanelu();
  if (panelStan || !win || win.isDestroyed() || !win.isVisible() || win.isMinimized()) {
    usunWiadomosci(w => w.akcja === 'aktualizacja');
    dodajWiadomosc(s.stan === 'gotowa'
      ? `Nowa wersja LivePL ${s.wersja} jest pobrana. Kliknij, żeby uruchomić ponownie`
      : `Jest nowa wersja LivePL ${s.wersja}. Kliknij, żeby ją pobrać`, { akcja: 'aktualizacja' });
    aktualizacjaCzeka = true;
    return;
  }
  pokazPanel('aktualizacja', { srodek: true });
}

// odłożone okienko aktualizacji: chwilę po zamknięciu innego okienka albo po powrocie do okna, żeby
// następne okienko tamtej sprawy (np. rejestracja po certyfikacie) mogło pokazać się pierwsze
function pokazCzekajacaAktualizacje() {
  if (!aktualizacjaCzeka) return;
  clearTimeout(czekajacaAktualizacja);
  czekajacaAktualizacja = setTimeout(() => {
    if (!aktualizacjaCzeka || panelStan || !win || win.isDestroyed() || !win.isVisible() || win.isMinimized() || !win.isFocused()) return;
    aktualizacjaCzeka = false;
    if (!['dostepna', 'gotowa'].includes(aktualizacje.stan().stan)) return;
    usunWiadomosci(w => w.akcja === 'aktualizacja');
    pokazPanel('aktualizacja', { srodek: true });
  }, 1500);
}

function uruchomPonownie() {
  app.relaunch();
  wychodzimy = true;
  app.quit();
}

function zakoncz() {
  wychodzimy = true;
  app.quit();
}

let ostatniTooltip = '';
let ostatnieMenuZasobnika = '';

function odswiezZasobnik() {
  if (!tray) return;
  const suma = programy.reduce((s, p) => s + ((karty.get(p.id) || {}).licznik || 0), 0);
  const tooltip = suma ? `LivePL – nieprzeczytane: ${suma}` : 'LivePL';
  if (tooltip !== ostatniTooltip) {
    tray.setToolTip(tooltip);
    ostatniTooltip = tooltip;
  }
  const klucz = programy.filter(widoczny).map(p => p.id + ':' + ((karty.get(p.id) || {}).licznik || 0)).join('|');
  if (klucz === ostatnieMenuZasobnika) return;
  ostatnieMenuZasobnika = klucz;
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Otwórz LivePL', click: pokazOkno },
    { type: 'separator' },
    ...programy.filter(widoczny).map(p => {
      const ile = (karty.get(p.id) || {}).licznik || 0;
      return { label: ile ? `${p.nazwa} (${ile})` : p.nazwa, click: () => { pokazOkno(); przejdzDo(p.id); } };
    }),
    { type: 'separator' },
    { label: 'Zakończ LivePL', click: zakoncz }
  ]));
}

// Pasek zadań i zasobnik mają w Windows własny tryb („tryb systemu”),
// niezależny od trybu aplikacji, więc czytamy go z rejestru.
const KLUCZ_MOTYWU = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize';

function czyPasekCiemny(wyjscie) {
  const m = String(wyjscie || '').match(/SystemUsesLightTheme\s+REG_DWORD\s+0x([0-9a-f]+)/i);
  return m ? parseInt(m[1], 16) === 0 : nativeTheme.shouldUseDarkColors;
}

function ustawIkony(ciemny) {
  IKONA = ciemny ? IKONA_CIEMNA : IKONA_JASNA;
  const obraz = nativeImage.createFromPath(IKONA);
  if (win && !win.isDestroyed()) win.setIcon(obraz);
  if (tray) tray.setImage(obraz);
  odswiezSkrot();
}

// Skrót LivePL na pulpicie dostaje ikonę według trybu. Zmieniamy tylko nasz
// skrót (prowadzący do tego programu) i tylko wtedy, gdy ikona jest inna.
function odswiezSkrot() {
  try {
    const lnk = path.join(app.getPath('desktop'), 'LivePL.lnk');
    if (!fs.existsSync(lnk)) return;
    const skrot = shell.readShortcutLink(lnk);
    const ten = (p) => path.resolve(String(p || '')).toLowerCase();
    if (ten(skrot.target) !== ten(process.execPath)) return;
    if (ten(skrot.icon) === ten(IKONA)) return;
    shell.writeShortcutLink(lnk, 'update', { icon: IKONA, iconIndex: 0 });
  } catch { /* skrót przeniesiony albo bez dostępu */ }
}

function ikonyWedlugTrybuOdRazu() {
  try {
    const wynik = execFileSync('reg', ['query', KLUCZ_MOTYWU, '/v', 'SystemUsesLightTheme'], { windowsHide: true, encoding: 'utf8' });
    IKONA = czyPasekCiemny(wynik) ? IKONA_CIEMNA : IKONA_JASNA;
  } catch {
    IKONA = nativeTheme.shouldUseDarkColors ? IKONA_CIEMNA : IKONA_JASNA;
  }
}

function ikonyWedlugTrybu() {
  execFile('reg', ['query', KLUCZ_MOTYWU, '/v', 'SystemUsesLightTheme'], { windowsHide: true }, (blad, wynik) => {
    ustawIkony(blad ? nativeTheme.shouldUseDarkColors : czyPasekCiemny(wynik));
  });
}

function utworzZasobnik() {
  tray = new Tray(nativeImage.createFromPath(IKONA));
  tray.setToolTip('LivePL');
  tray.on('click', pokazOkno);
  odswiezZasobnik();
}

// ---------------------------------------------------------------- okno główne

function naKtorymsEkranie(b) {
  const { screen } = require('electron');
  return screen.getAllDisplays().some(d => {
    const a = d.workArea;
    return b.x < a.x + a.width - 80 && b.x + b.width > a.x + 80 && b.y >= a.y - 20 && b.y < a.y + a.height - 80;
  });
}

function zapamietajOkno() {
  if (!win || win.isDestroyed() || win.isFullScreen()) return;
  ustawienia.okno = { ...win.getNormalBounds(), zmaksymalizowane: win.isMaximized() };
  zapiszUstawienia();
}

function utworzOkno() {
  const kol = kolory();
  const o = ustawienia.okno && naKtorymsEkranie(ustawienia.okno) ? ustawienia.okno : null;
  win = new BrowserWindow({
    width: o ? o.width : 1440,
    height: o ? o.height : 920,
    x: o ? o.x : undefined,
    y: o ? o.y : undefined,
    minWidth: 760,
    minHeight: 480,
    show: false,
    title: 'LivePL',
    icon: IKONA,
    backgroundColor: kol.tlo,
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: kol.tlo, symbolColor: kol.symbole, height: WYSOKOSC_PRZYCISKOW_OKNA },
    webPreferences: {
      preload: path.join(__dirname, 'preload-powloka.js'),
      contextIsolation: true,
      sandbox: true,
      spellcheck: false
    }
  });
  if (o && o.zmaksymalizowane) win.maximize();

  win.loadFile(path.join(UI, 'powloka.html'));
  win.once('ready-to-show', () => {
    if (!STARTUJ_W_TLE) win.show();
  });

  for (const z of ['resize', 'maximize', 'unmaximize', 'enter-full-screen', 'leave-full-screen', 'restore']) {
    win.on(z, ulozWidoki);
  }
  win.on('resize', () => schowajPanel(false));
  win.on('blur', () => { if (!process.env.LIVEPL_PANEL_TEST) schowajPanel(false); });
  win.on('leave-full-screen', () => {
    const k = pelnyEkran && karty.get(pelnyEkran);
    pelnyEkran = null;
    ulozWidoki();
    if (k) k.wc.executeJavaScript('document.fullscreenElement && document.exitFullscreen()', true).catch(() => {});
  });
  win.on('resized', zapamietajOkno);
  win.on('moved', zapamietajOkno);
  win.on('maximize', zapamietajOkno);
  win.on('unmaximize', zapamietajOkno);

  win.on('focus', () => {
    const k = karty.get(aktywnaId);
    if (k && !k.nowa && !win.webContents.isFocused()) k.wc.focus();
    pokazCzekajacaAktualizacje();
  });

  win.on('close', (e) => {
    zapamietajOkno();
    if (!wychodzimy && ustawienia.chowajDoZasobnika) {
      e.preventDefault();
      win.hide();
      if (!ustawienia.dymekZasobnika && tray) {
        tray.displayBalloon({
          iconType: 'info',
          title: 'LivePL działa dalej',
          content: 'Poczta sprawdza nowe wiadomości w tle. Program zamkniesz prawym przyciskiem na ikonie przy zegarze.'
        });
        ustawienia.dymekZasobnika = true;
        zapiszUstawienia();
      }
    }
  });

  win.webContents.on('before-input-event', (e, w) => skrot(e, w));
  win.webContents.on('context-menu', (e, p) => {
    if (p.isEditable) menuKontekstowe(win.webContents, p, null, win);
  });

  nativeTheme.on('updated', () => {
    const k = kolory();
    if (!win || win.isDestroyed()) return;
    win.setTitleBarOverlay({ color: k.tlo, symbolColor: k.symbole, height: WYSOKOSC_PRZYCISKOW_OKNA });
    win.setBackgroundColor(k.tlo);
    ikonyWedlugTrybu();
    wyslijStan(); // obrazki programów w wersji dziennej albo nocnej
  });
}

// ---------------------------------------------------------------- komunikacja z paskiem

ipcMain.on('powloka', (e, akcja, ...a) => {
  if (!win || e.sender !== win.webContents) return;
  const k = karty.get(aktywnaId);
  switch (akcja) {
    case 'gotowa': wyslijStan(); break;
    case 'program': kliknietoProgram(a[0]); break;
    case 'karta': przejdzDo(a[0]); break;
    case 'zamknij': zamknij(a[0]); break;
    case 'nowa': nowaKarta(null); break;
    case 'wstecz': if (k) k.wc.navigationHistory.goBack(); break;
    case 'dalej': if (k) k.wc.navigationHistory.goForward(); break;
    case 'odswiez': odswiez(k, false); break;
    case 'zatrzymaj': if (k) k.wc.stop(); break;
    case 'idz': idzDo(a[0], a[1]); break;
    case 'szukaj': szukaj(a[0], a[1]); break;
    case 'szukaj-koniec': zakonczSzukanie(); break;
    case 'powiekszenie': zmienPowiekszenie(k, a[0]); break;
    case 'menu': pokazPanel('menu', { prawo: a[0], gora: a[1] + 6 }); break;
    case 'menu-karty': pokazPanel('karta', { lewo: a[1], gora: a[2] }, { kartaId: a[0] }); break;
    case 'menu-programu': pokazPanel('program', { lewo: a[1], gora: a[2] }, { programId: a[0] }); break;
    case 'przesun-karte': przesunKarte(a[0], a[1]); break;
    case 'kolejnosc-programow': ustawKolejnoscProgramow(a[0]); break;
    case 'pobrane': pokazPanel('pobrane', { prawo: a[0], gora: a[1] + 6 }); break;
    case 'ssl': pokazPanel('ssl', { lewo: a[0], dol: a[1] }); break;
    case 'ai': pokazPanel('ai', { prawo: a[0], dol: a[1] }); break;
    case 'ai-zatrzymaj': dostepAI.ustawPoziom('wylaczony'); break;
    case 'pobrane-otworz': {
      const p = pobrane.find(x => x.id === a[0]);
      if (p) shell.openPath(p.sciezka);
      break;
    }
    case 'pobrane-folder': {
      const p = pobrane.find(x => x.id === a[0]);
      if (p) shell.showItemInFolder(p.sciezka);
      break;
    }
    case 'nakladka': ustawNakladke(a[0], a[1]); break;
    case 'wiadomosc': kliknietoWiadomosc(a[0]); break;
    case 'wiadomosc-usun': usunWiadomosc(a[0]); break;
    case 'oddaj-fokus': if (k && !k.nowa) k.wc.focus(); break;
    case 'przypnij': przypnijKarte(aktywnaId); break;
    case 'motyw':
      if (!TRYBY_MOTYWU[a[0]] || a[0] === ustawienia.motyw) break;
      ustawienia.motyw = a[0];
      zastosujMotyw();
      zapiszUstawienia();
      wyslijStan();
      break;
  }
});

// ---------------------------------------------------------------- logowanie do panelu urzędu (cert.livepl.eu)

let oczekujacyPodpis = null;

// tylko główna ramka strony cert.livepl.eu (przez HTTPS) może prosić o podpis
function hostPaneluCA(e) {
  try {
    if (e.senderFrame !== e.sender.mainFrame) return null;
    const url = new URL(e.senderFrame.url);
    if (url.protocol === 'https:' && url.hostname === 'cert.livepl.eu') return url.host;
    const test = process.env.LIVEPL_CA_PANEL_HOST;
    if (test && url.host === test && /^https?:$/.test(url.protocol)) return url.host;
  } catch { /* zły adres */ }
  return null;
}

// ---------------------------------------------------------------- hasła
// Strona zgłasza próbę logowania i fokus w polu logowania; hasła i wybór konta są tylko w LivePL.

function zrodloKarty(e) {
  if (!e.senderFrame || e.senderFrame !== e.sender.mainFrame) return null;
  return hasla.zrodlo(e.senderFrame.url);
}

ipcMain.on('hasla:proba', (e, d) => {
  const k = kartaDla(e.sender);
  const origin = zrodloKarty(e);
  if (!k || !origin || ustawienia.hasla === false || !hasla.dostepne()) return;
  const login = String((d && d.login) || '').slice(0, 200);
  const haslo = String((d && d.haslo) || '');
  if (!haslo || haslo.length > 500) return;
  const rodzaj = hasla.ocen(origin, login, haslo);
  if (!rodzaj) return;
  k.oczekujaceHaslo = { origin, login, haslo, rodzaj };
  setTimeout(() => {
    if (!karty.has(k.id) || !k.oczekujaceHaslo || k.oczekujaceHaslo.haslo !== haslo) return;
    if (aktywnaId !== k.id || !win || win.isDestroyed() || !win.isVisible()) return;
    const b = k.widok.getBounds();
    pokazPanel('haslo', { lewo: b.x + b.width - SZER_PANELU - 12, gora: b.y + 12 },
      { haslo: { kartaId: k.id, host: new URL(origin).host, login, rodzaj } });
  }, 1200);
});

ipcMain.handle('hasla:konta', (e) => {
  const origin = zrodloKarty(e);
  if (!origin || ustawienia.hasla === false) return 0;
  return hasla.konta(origin).length;
});

ipcMain.on('hasla:pole', (e, r) => {
  const k = kartaDla(e.sender);
  const origin = zrodloKarty(e);
  if (!k || !origin || aktywnaId !== k.id || ustawienia.hasla === false) return;
  const konta = hasla.konta(origin);
  if (!konta.length) return;
  const b = k.widok.getBounds();
  const z = k.wc.getZoomFactor();
  pokazPanel('hasla-wybor', { lewo: Math.round(b.x + Number(r.x || 0) * z), gora: Math.round(b.y + Number(r.y || 0) * z + 4) },
    { haslaWybor: { kartaId: k.id, host: new URL(origin).host, konta } });
});

function akcjaHasla(nazwa, arg) {
  switch (nazwa) {
    case 'haslo-zapisz':
    case 'haslo-nigdy':
    case 'haslo-nie': {
      const k = karty.get(arg);
      const p = k && k.oczekujaceHaslo;
      if (!p) return;
      k.oczekujaceHaslo = null;
      try {
        if (nazwa === 'haslo-zapisz') {
          hasla.dodaj(p.origin, p.login, p.haslo);
          dodajWiadomosc(`Zapamiętano hasło dla ${new URL(p.origin).host}`);
        } else if (nazwa === 'haslo-nigdy') {
          hasla.nigdy(p.origin);
          dodajWiadomosc(`LivePL nie będzie proponować haseł na ${new URL(p.origin).host}`);
        }
      } catch (err) {
        dodajWiadomosc('Nie udało się zapisać hasła: ' + err.message);
      }
      return;
    }
    case 'haslo-wypelnij': {
      const k = arg && karty.get(arg.kartaId);
      const w = arg && hasla.wpis(arg.id);
      if (!k || !w || hasla.zrodlo(k.wc.getURL()) !== w.origin) return;
      k.wc.send('hasla:wypelnij', { login: w.login, haslo: w.haslo });
      hasla.uzyto(w.id);
      k.wc.focus();
      return;
    }
    case 'haslo-kopiuj': {
      const w = hasla.wpis(arg);
      if (!w) return;
      clipboard.writeText(w.haslo);
      dodajWiadomosc('Skopiowano hasło. Schowek wyczyści się za 30 sekund');
      setTimeout(() => { if (clipboard.readText() === w.haslo) clipboard.clear(); }, 30000);
      return;
    }
    case 'haslo-usun': hasla.usun(arg); break;
    case 'haslo-nigdy-usun': hasla.usunNigdy(arg); break;
    case 'hasla-wlacz': ustawienia.hasla = ustawienia.hasla === false; zapiszUstawienia(); break;
    default: break;
  }
  if (panelStan) wyslijDoPanelu();
}

// ---------------------------------------------------------------- rejestracja przeglądarki (urządzenie)

let trwaRejestracja = false;
// opcje.zaufanie: najpierw certyfikat urzędu w Windows (Windows sam pyta o zgodę), potem rejestracja;
// odmowa w oknie Windows nie przerywa rejestracji
function zarejestrujUrzadzenie(opcje = {}) {
  if (trwaRejestracja) return;
  trwaRejestracja = true;
  if (panelStan) wyslijDoPanelu();
  const przedtem = opcje && opcje.zaufanie
    ? zaufanie.stan().then((s) => (s && !s.zaufany ? zainstalujZaufanie(false) : null)).catch(() => {})
    : Promise.resolve();
  przedtem
    .then(() => urzadzenie.zarejestruj())
    .then((s) => {
      dodajWiadomosc(s.stan === 'gotowe'
        ? (s.odzyskano && Date.now() - s.odzyskano < 60 * 1000
          ? 'Przywrócono wcześniejszą rejestrację tej przeglądarki w urzędzie LivePL'
          : 'Przeglądarka jest zarejestrowana w urzędzie LivePL')
        : `Wysłano wniosek o rejestrację. Kod tej przeglądarki: ${s.kod}`, {}, 60 * 1000);
      if (s.stan === 'gotowe') urzadzenie.raport(true).catch(() => {}).finally(() => { if (panelStan) wyslijDoPanelu(); });
    })
    .catch((e) => {
      urzadzenie.zapiszBlad(e);
      dodajWiadomosc('Nie udało się wysłać wniosku o rejestrację: ' + e.message);
    })
    .finally(() => {
      trwaRejestracja = false;
      if (panelStan) wyslijDoPanelu();
    });
}

function sprawdzUrzadzenie(recznie = false) {
  const stanUrzadzenia = urzadzenie.ostatniStan().stan;
  if (stanUrzadzenia === 'gotowe') {
    // zarejestrowana: raport bezpieczeństwa (co kilka godzin) i przy okazji stan certyfikatu
    urzadzenie.raport(recznie)
      .then((uniewazniony) => {
        if (uniewazniony) dodajWiadomosc('Urząd LivePL unieważnił rejestrację tej przeglądarki', { akcja: 'urzadzenie' });
      })
      .catch(() => { /* bez połączenia: spróbujemy przy następnym sprawdzeniu */ })
      .finally(() => { if (panelStan) wyslijDoPanelu(); });
    return;
  }
  if (stanUrzadzenia !== 'wyslany') return;
  urzadzenie.sprawdz()
    .then((gotowe) => {
      if (gotowe) {
        dodajWiadomosc('Przeglądarka jest zarejestrowana w urzędzie LivePL', {}, 60 * 1000);
        urzadzenie.raport(true).catch(() => {}).finally(() => { if (panelStan) wyslijDoPanelu(); });
      } else if (recznie) dodajWiadomosc('Wniosek wciąż czeka na zatwierdzenie w urzędzie');
    })
    .catch((e) => {
      urzadzenie.zapiszBlad(e);
      if (recznie) dodajWiadomosc('Nie udało się sprawdzić wniosku: ' + e.message);
    })
    .finally(() => { if (panelStan) wyslijDoPanelu(); });
}

// nasze strony mogą poprosić o certyfikat: dostają certyfikat urządzenia LivePL (inne strony bez zmian)
app.on('select-client-certificate', (e, wc, url, lista, wybierz) => {
  if (!/(^|\.)(livepl\.eu|liveproduction\.pl|egzamin\.info\.pl)$/i.test(hostZ(url))) return;
  const c = (lista || []).find(x => x.issuerName === 'LivePL CA 1' && /^LivePL /.test(x.subjectName || ''));
  if (!c) return;
  e.preventDefault();
  wybierz(c);
});

// instalacja certyfikatu urzędu LivePL w Windows (zgoda użytkownika albo administratora)
let trwaZaufanie = false;
function zainstalujZaufanie(komputer) {
  if (trwaZaufanie) return Promise.resolve();
  trwaZaufanie = true;
  if (panelStan) wyslijDoPanelu();
  const podpowiedz = dodajWiadomosc(komputer
    ? 'Windows zapyta o zgodę administratora na instalację certyfikatu urzędu LivePL'
    : 'Windows zapyta, czy zainstalować certyfikat urzędu LivePL. Sprawdź odcisk (zaczyna się od E2:7C:75) i kliknij Tak', {}, 3 * 60 * 1000);
  return (komputer ? zaufanie.zainstalujDlaKomputera() : zaufanie.zainstalujDlaMnie())
    .then((s) => dodajWiadomosc(s && s.zaufany
      ? 'Zainstalowano certyfikat urzędu LivePL. Windows rozpoznaje teraz nasze certyfikaty'
      : 'Windows nie potwierdził instalacji certyfikatu urzędu LivePL'))
    .catch((e) => dodajWiadomosc(e.message === 'anulowano'
      ? 'Instalację certyfikatu urzędu LivePL anulowano'
      : 'Nie udało się zainstalować certyfikatu urzędu: ' + e.message))
    .finally(() => {
      trwaZaufanie = false;
      usunWiadomosc(podpowiedz);
      if (panelStan) wyslijDoPanelu();
    });
}

// logowanie tylko certyfikatem osobistym z YubiKey: LivePL nie ma własnego klucza do panelu
ipcMain.handle('ca-admin:stan', async (e) => {
  if (!hostPaneluCA(e)) throw new Error('niedozwolone');
  const lista = await certOsobisty.lista();
  return { certyfikaty: lista.map(certOsobisty.krotko) };
});

let trwaPodpis = false;
ipcMain.handle('ca-admin:podpisz', async (e, wyzwanie, odcisk) => {
  const host = hostPaneluCA(e);
  if (!host) throw new Error('niedozwolone');
  if (oczekujacyPodpis || trwaPodpis) throw new Error('trwa inne logowanie');
  const c = (await certOsobisty.lista()).find(x => x.odcisk === String(odcisk));
  if (!c) throw new Error('YubiKey: nie widzę certyfikatu osobistego. Włóż klucz.');
  await new Promise((potwierdz, odrzuc) => {
    oczekujacyPodpis = { potwierdz, odrzuc };
    pokazOkno();
    pokazPanel('podpis', { srodek: true }, { podpis: { host, nazwa: c.nazwa, email: c.email, waznyDo: c.waznyDo } });
  });
  trwaPodpis = true;
  const podpowiedz = dodajWiadomosc('Wpisz PIN do YubiKey w okienku Windows, potem dotknij klucza', {}, 2 * 60 * 1000);
  try {
    const uchwyt = win && !win.isDestroyed() ? win.getNativeWindowHandle() : null;
    const okno = uchwyt ? (uchwyt.length >= 8 ? uchwyt.readBigUInt64LE(0) : BigInt(uchwyt.readUInt32LE(0))).toString() : '';
    return await certOsobisty.podpisz(c, host, wyzwanie, okno);
  } finally {
    trwaPodpis = false;
    usunWiadomosc(podpowiedz);
  }
});

// powiadomienie strony (np. nowa wiadomość w poczcie) trafia też na pasek wiadomości
ipcMain.on('strona:powiadomienie', (e, tytul, tresc) => {
  const k = kartaDla(e.sender);
  if (!k || !nasza(k.wc.getURL())) return;
  ostatniePowiadomienie.set(k.id, Date.now());
  const zrodlo = k.program ? k.program.nazwa : hostZ(k.adres);
  const opis = [tytul, tresc].map(x => String(x || '').trim()).filter(Boolean).join(', ');
  if (opis) dodajWiadomosc(`${zrodlo}: ${opis}`, { kartaId: k.id });
});

// strona startowa (nowa karta): „Dodaj stronę na pasek” bez otwierania menu
ipcMain.handle('nowa:dodaj', (e, adres, nazwa) => {
  const k = kartaDla(e.sender);
  let plik = '';
  try { plik = new URL(e.senderFrame ? e.senderFrame.url : '').pathname; } catch { /* zostaje pusty */ }
  if (!k || !k.nowa || !/\/ui\/nowa\.html$/i.test(plik)) return 'Dodawanie działa tylko na stronie nowej karty';
  return dodajStrone({ adres: String(adres || '').slice(0, 500), nazwa: String(nazwa || '').slice(0, 40) });
});

ipcMain.on('strona:pokaz', (e) => {
  const k = kartaDla(e.sender);
  if (!k) return;
  pokazOkno();
  aktywuj(k.id);
});

function naAdres(tekst) {
  const t = String(tekst || '').trim();
  if (!t) return null;
  if (/^(https?|file):\/\//i.test(t) || /^(about|mailto):/i.test(t)) return t;
  if (/^localhost(:\d+)?(\/|$)/i.test(t) || /^\d{1,3}(\.\d{1,3}){3}(:\d+)?(\/|$)/.test(t)) return 'http://' + t;
  if (!/\s/.test(t) && /^[^/]+\.[a-z]{2,}(:\d+)?(\/.*)?$/i.test(t)) return 'https://' + t;
  return 'https://www.google.com/search?q=' + encodeURIComponent(t);
}

function idzDo(tekst, wNowej) {
  const url = naAdres(tekst);
  if (!url) return;
  if (/^mailto:/i.test(url)) return napiszMail(url);
  const p = programStartowy(url);
  const k = karty.get(aktywnaId);
  if (p && (!k || k.nowa || k.program)) {
    if (k && k.nowa) zamknij(k.id);
    const kp = kartaProgramu(p);
    if (kp.blad) laduj(kp, p.adres);
    return przejdzDo(p.id);
  }
  // w zakładce programu wpisany obcy adres otwiera się obok, żeby program został na miejscu
  if (wNowej || !k || (k.program && !pasujeHost(hostZ(url), k.program.host))) {
    nowaKarta(url);
    return;
  }
  k.nowa = false;
  k.blad = null;
  laduj(k, url);
  k.wc.focus();
}

function ustawNakladke(dataUrl, opis) {
  if (!win || win.isDestroyed()) return;
  if (!dataUrl) return win.setOverlayIcon(null, '');
  win.setOverlayIcon(nativeImage.createFromDataURL(dataUrl), opis || '');
}

// ---------------------------------------------------------------- start

app.on('second-instance', pokazOkno);
app.on('before-quit', () => {
  wychodzimy = true;
  zapiszUstawieniaTeraz();
});

app.on('web-contents-created', (e, wc) => {
  wc.on('will-attach-webview', ev => ev.preventDefault());
});

app.whenReady().then(() => {
  wczytajUstawienia();
  zastosujMotyw();
  // po uśpieniu komputera od razu sprawdzamy, czy nie minął wschód albo zachód słońca
  require('electron').powerMonitor.on('resume', odswiezMotyw);
  const wszystkieProgramy = wczytajProgramy();
  programyPodstawowe = wszystkieProgramy.filter(p => !p.ukryty);
  programyUkryte = wszystkieProgramy.filter(p => p.ukryty);
  zaufaneHosty = [...new Set(wszystkieProgramy.map(p => p.host))];
  zbudujProgramy();

  // YubiKey włożony: na pasku pojawia się Urząd (cert.livepl.eu); wyjęty: znika (chyba że karta jest otwarta)
  yubikey.obserwuj((jest) => {
    yubikeyObecny = jest;
    wyslijStan();
  });
  app.on('will-quit', () => yubikey.zatrzymaj());

  // bez dopisku „Electron”, żeby strony traktowały nas jak zwykłego Chrome
  app.userAgentFallback = app.userAgentFallback.replace(/\s*Electron\/\S+/, '');

  const ses = session.fromPartition(PARTYCJA);
  try { ses.setSpellCheckerLanguages(['pl', 'en-US']); } catch { /* słowniki dociągną się później */ }
  uprawnienia(ses);
  obslugaPobierania(ses);
  naszeCA.wlacz(ses);

  Menu.setApplicationMenu(null);
  ikonyWedlugTrybuOdRazu();
  utworzOkno();
  utworzZasobnik();
  odswiezSkrot();
  utworzPanel();

  // aktualizacje: kilka sekund po włączeniu, potem co 4 godziny (jeśli włączone). Nowa wersja:
  // okienko „Jest nowa wersja. Pobrać?”; po pobraniu „Uruchomić ponownie?”. Bez zgody nic się nie pobiera.
  const zaproponowano = new Set();
  aktualizacje.start((s) => {
    if ((s.stan === 'dostepna' || s.stan === 'gotowa') && !zaproponowano.has(s.stan + s.wersja)) {
      zaproponowano.add(s.stan + s.wersja);
      zaproponujAktualizacje(s);
    }
    wyslijStan();
    if (panelStan) wyslijDoPanelu();
  });
  const sprawdzAuto = () => { if (ustawienia.autoAktualizacje !== false) aktualizacje.sprawdz(); };
  setTimeout(sprawdzAuto, 6000);
  setInterval(sprawdzAuto, 4 * 60 * 60 * 1000);

  // po starcie najwyżej jedno okienko (po „Nie teraz” każde wraca dopiero po dobie):
  // 1. „Zarejestruj tę przeglądarkę” — jednym przyciskiem; przy okazji instaluje certyfikat urzędu,
  //    jeśli Windows go jeszcze nie ma. Rejestracja nie czeka już na ten certyfikat.
  //    Komputer zarejestrowany wcześniej (certyfikat z numerem w Windows) wraca do swojej rejestracji sam.
  // 2. gdy przeglądarka jest zarejestrowana, a Windows nie ufa urzędowi: „Zainstaluj certyfikat urzędu”
  const DOBA = 24 * 60 * 60 * 1000;
  const okienkoPoStarcie = (rodzaj, tekst) => {
    if (panelStan || !win || win.isDestroyed() || !win.isVisible()) {
      dodajWiadomosc(tekst, { akcja: rodzaj });
      return;
    }
    pokazPanel(rodzaj, { srodek: true });
  };
  setTimeout(async () => {
    try {
      const s = await zaufanie.stan().catch(() => null);
      if (urzadzenie.ostatniStan().stan === 'brak') {
        if (await urzadzenie.odzyskaj().catch(() => false)) {
          dodajWiadomosc('Przywrócono wcześniejszą rejestrację tej przeglądarki w urzędzie LivePL', {}, 60 * 1000);
          urzadzenie.raport(true).catch(() => {});
          return;
        }
        if (Date.now() - (ustawienia.urzadzeniePozniej || 0) >= DOBA) {
          okienkoPoStarcie('urzadzenie', 'Zarejestruj tę przeglądarkę w urzędzie LivePL. Kliknij, żeby zacząć');
        }
        return;
      }
      if (s && !s.zaufany && Date.now() - (ustawienia.zaufaniePozniej || 0) >= DOBA) {
        okienkoPoStarcie('zaufanie', 'Windows nie zna jeszcze urzędu LivePL. Kliknij, żeby zainstalować jego certyfikat');
      }
    } catch { /* bez okienek przy tym starcie */ }
  }, 2500);

  // wniosek wysłany: sprawdzamy, czy urząd go zatwierdził (po starcie i co 15 minut)
  setTimeout(() => sprawdzUrzadzenie(false), 8000);
  setInterval(() => sprawdzUrzadzenie(false), 15 * 60 * 1000);

  // po włączeniu strona startowa (nowa karta); Poczta ładuje się w tle, żeby działały powiadomienia
  for (const p of programy) if (p.przyStarcie) kartaProgramu(p);
  nowaKarta(null);

  // dostęp dla AI (domyślnie wyłączony): moduł dostaje tylko to, czego potrzebuje do kart
  dostepAI.uruchom({
    ustawienia: () => ustawienia,
    zapiszUstawienia,
    userData: () => app.getPath('userData'),
    okno: () => win,
    karty: () => karty,
    dodatkowe: () => dodatkowe,
    aktywnaId: () => aktywnaId,
    programy: () => programy,
    wszystkieProgramy: () => [...programy, ...programyUkryte],
    aktywuj, przejdzDo, nowaKarta, zamknij, laduj, dodajWiadomosc, wyslijStan
  });
});
