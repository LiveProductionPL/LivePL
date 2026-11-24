'use strict';

// Panel LivePL: menu (⋮), ustawienia, o programie, pobrane pliki i menu karty.
// Dane przychodzą z procesu głównego, akcje wracają tą samą drogą.

const P = window.panel;
const panelEl = document.getElementById('panel');
let D = null;
let ekran = null;
let nr = -1;

const podajWysokosc = () => P.wysokosc(Math.ceil(panelEl.getBoundingClientRect().height));

P.na((dane) => {
  const nowyPanel = dane.nr !== nr;
  if (nowyPanel) {
    nr = dane.nr;
    ekran = dane.rodzaj;
  }
  D = dane;
  rysuj();
  // LivePL przy każdym otwarciu zaczyna od wysokości domyślnej, a ResizeObserver milczy,
  // gdy menu ma tę samą wysokość co ostatnio: wtedy dół menu był ucięty. Podajemy ją zawsze,
  // od razu (odczyt rozmiaru sam przelicza układ; schowany panel nie rysuje klatek, więc
  // requestAnimationFrame mógłby się nie wykonać) i jeszcze raz chwilę później, po czcionkach.
  if (nowyPanel) {
    podajWysokosc();
    setTimeout(podajWysokosc, 120);
  }
});

new ResizeObserver(podajWysokosc).observe(panelEl);

// ---------------------------------------------------------------- klocki

function el(tag, klasa, tekst) {
  const e = document.createElement(tag);
  if (klasa) e.className = klasa;
  if (tekst !== undefined) e.textContent = tekst;
  return e;
}

function ikonaEl(nazwa, klasa) {
  const s = el('span');
  s.innerHTML = ikona(nazwa, klasa);
  return s.firstChild;
}

function poz(nazwaIkony, tekst, opcje = {}) {
  const b = el('button', 'poz' + (opcje.czerwona ? ' czerwona' : ''));
  b.type = 'button';
  b.setAttribute('role', 'menuitem');
  if (nazwaIkony) b.appendChild(ikonaEl(nazwaIkony));
  b.appendChild(el('span', 'tekst', tekst));
  if (opcje.skrot) b.appendChild(el('span', 'skrot', opcje.skrot));
  if (opcje.dalej) b.appendChild(ikonaEl('dalej', 'dalej-ik'));
  if (opcje.wylaczona) b.disabled = true;
  b.addEventListener('click', () => {
    if (typeof opcje.klik === 'function') opcje.klik();
    else if (opcje.akcja) P.akcja(opcje.akcja, opcje.arg);
  });
  return b;
}

function zaznaczenie(tekst, wlaczone, akcja, przelacz) {
  const b = el('button', 'poz');
  b.type = 'button';
  b.setAttribute('role', 'menuitemcheckbox');
  b.setAttribute('aria-checked', String(!!wlaczone));
  const pole = el('span', 'pole' + (wlaczone ? ' wl' : ''));
  pole.appendChild(ikonaEl('znacznik'));
  b.append(pole, el('span', 'tekst', tekst));
  b.addEventListener('click', () => { przelacz(); P.akcja(akcja); rysuj(); });
  return b;
}

const kreska = () => el('div', 'kreska');

// nazwa przeglądarki na górze menu, jak w logo poziomym
function logoWstega() {
  const pic = el('picture');
  const src = el('source');
  src.srcset = '../assets/wstega-ciemna.png';
  src.media = '(prefers-color-scheme: dark)';
  const img = el('img');
  img.src = '../assets/wstega-jasna.png';
  img.alt = '';
  pic.append(src, img);
  return pic;
}

function marka() {
  const m = el('div', 'marka');
  const napis = el('div', 'napis');
  const nazwa = el('b');
  nazwa.append(document.createTextNode('Live'), el('em', '', 'PL'));
  napis.append(nazwa, el('small', '', 'Przeglądarka'));
  m.append(logoWstega(), napis);
  return m;
}

function naglowek(tytul, wstecz) {
  const n = el('div', 'naglowek');
  if (wstecz) {
    const b = el('button', 'maly');
    b.type = 'button';
    b.title = 'Wstecz';
    b.appendChild(ikonaEl('wstecz'));
    b.addEventListener('click', () => { ekran = wstecz; rysuj(); });
    n.appendChild(b);
  }
  n.appendChild(el('span', '', tytul));
  return n;
}

// ---------------------------------------------------------------- ekrany

function ekranMenu() {
  const f = document.createDocumentFragment();
  f.appendChild(marka());
  f.appendChild(poz('plus', 'Nowa karta', { skrot: 'Ctrl+T', akcja: 'nowa' }));
  f.appendChild(poz('przywroc', 'Przywróć zamkniętą kartę', { skrot: 'Ctrl+Shift+T', akcja: 'przywroc', wylaczona: !D.zamkniete }));
  f.appendChild(kreska());

  f.appendChild(poz('drukuj', 'Drukuj', { skrot: 'Ctrl+P', akcja: 'drukuj', wylaczona: !D.adres }));
  f.appendChild(poz('szukaj', 'Znajdź na stronie', { skrot: 'Ctrl+F', akcja: 'szukaj', wylaczona: !D.adres }));
  const tk = D.tlumacz && D.tlumacz.karta;
  const przetlumaczona = !!tk && (tk.stan === 'gotowe' || tk.stan === 'trwa');
  f.appendChild(poz('tlumacz', przetlumaczona ? 'Pokaż oryginał' : 'Przetłumacz stronę na polski', {
    akcja: przetlumaczona ? 'tlumacz-oryginal' : 'tlumacz-strone', wylaczona: !tk
  }));
  const w = el('div', 'wiersz');
  w.append(ikonaEl('szukaj'), el('span', 'tekst', 'Powiększenie'));
  const s = el('div', 'sterowanie');
  const minus = el('button', 'maly'); minus.type = 'button'; minus.title = 'Pomniejsz (Ctrl −)'; minus.appendChild(ikonaEl('minus'));
  minus.addEventListener('click', () => P.akcja('zoom-'));
  const wart = el('button', 'wartosc', D.zoom + '%'); wart.type = 'button'; wart.title = 'Domyślny rozmiar (Ctrl+0)';
  wart.addEventListener('click', () => P.akcja('zoom0'));
  const plus = el('button', 'maly'); plus.type = 'button'; plus.title = 'Powiększ (Ctrl +)'; plus.appendChild(ikonaEl('plus'));
  plus.addEventListener('click', () => P.akcja('zoom+'));
  s.append(minus, wart, plus);
  w.appendChild(s);
  f.appendChild(w);
  f.appendChild(poz('zewnetrzna', 'Otwórz w zwykłej przeglądarce', { akcja: 'zewnetrzna', wylaczona: !D.zewnetrzny }));
  f.appendChild(poz('kopiuj', 'Kopiuj adres strony', { akcja: 'kopiuj', wylaczona: !D.adres }));
  f.appendChild(poz('pinezka', 'Dodaj stronę do paska', { akcja: 'przypnij', wylaczona: !D.moznaPrzypiac }));
  f.appendChild(kreska());

  f.appendChild(poz('pobrane', 'Pobrane pliki', { skrot: 'Ctrl+J', dalej: true, klik: () => { ekran = 'pobrane'; rysuj(); } }));
  f.appendChild(poz('klodka', 'Certyfikaty', { dalej: true, klik: () => { ekran = 'certyfikaty'; rysuj(); P.akcja('cert-win-odswiez'); } }));
  f.appendChild(poz('ustawienia', 'Ustawienia', { dalej: true, klik: () => { ekran = 'ustawienia'; rysuj(); } }));
  f.appendChild(poz('kod', 'Narzędzia programisty', { skrot: 'F12', akcja: 'narzedzia', wylaczona: !D.adres }));
  f.appendChild(poz('info', 'O programie', { dalej: true, klik: () => { ekran = 'o-programie'; rysuj(); } }));
  f.appendChild(kreska());
  f.appendChild(poz('wyjscie', 'Zakończ LivePL', { akcja: 'zakoncz', czerwona: true }));
  return f;
}

function ekranUstawienia() {
  const f = document.createDocumentFragment();
  f.appendChild(naglowek('Ustawienia', 'menu'));
  f.appendChild(zaznaczenie('Działaj w tle po zamknięciu okna', D.chowaj, 'tlo', () => { D.chowaj = !D.chowaj; }));
  f.appendChild(zaznaczenie('Uruchamiaj razem z Windows', D.autostart, 'autostart', () => { D.autostart = !D.autostart; }));
  f.appendChild(zaznaczenie('Sprawdzaj aktualizacje przy starcie', D.aktualizacja && D.aktualizacja.auto, 'auto-aktualizacje', () => { D.aktualizacja.auto = !D.aktualizacja.auto; }));
  f.appendChild(kreska());
  f.appendChild(poz('klodka', 'Certyfikat osobisty (YubiKey)', { dalej: true, klik: () => { ekran = 'cert-osobisty'; rysuj(); P.akcja('cert-osobisty-odswiez'); } }));
  f.appendChild(poz('znacznik', 'Urząd LivePL w Windows', { dalej: true, klik: () => { ekran = 'zaufanie'; rysuj(); P.akcja('zaufanie-odswiez'); } }));
  f.appendChild(poz('serwer', 'Urządzenie LivePL', { dalej: true, klik: () => { ekran = 'urzadzenie'; rysuj(); P.akcja('urzadzenie-sprawdz'); } }));
  f.appendChild(poz('pinezka', 'Moje strony na pasku', { dalej: true, klik: () => { ekran = 'strony'; rysuj(); } }));
  f.appendChild(poz('klucz', 'Hasła', { dalej: true, klik: () => { ekran = 'hasla'; rysuj(); P.akcja('hasla-odswiez'); } }));
  f.appendChild(poz('uwaga', 'Wyjątki certyfikatów', { dalej: true, klik: () => { ekran = 'wyjatki'; rysuj(); } }));
  f.appendChild(poz('tarcza', 'Bezpieczny DNS', { dalej: true, klik: () => { ekran = 'dns'; rysuj(); } }));
  f.appendChild(poz('ai', 'Dostęp dla AI (Claude i inne)', { dalej: true, klik: () => { ekran = 'ai'; rysuj(); } }));
  f.appendChild(poz('tlumacz', 'Tłumacz stron', { dalej: true, klik: () => { ekran = 'tlumacz-ustawienia'; rysuj(); } }));
  f.appendChild(poz('kod', 'Wtyczki', { dalej: true, klik: () => { ekran = 'wtyczki'; rysuj(); P.akcja('wtyczki-sprawdz'); } }));
  f.appendChild(poz('pobrane', 'Dodatki', { dalej: true, klik: () => { ekran = 'dodatki'; rysuj(); } }));
  // tylko z zainstalowanym dodatkiem Rozszerzenia Chrome (domyślny LivePL go nie ma)
  if (D.rozszerzenia) f.appendChild(poz('plus', 'Rozszerzenia Chrome', { dalej: true, klik: () => { ekran = 'rozszerzenia'; rysuj(); } }));
  f.appendChild(poz('przywroc', 'Uruchom LivePL ponownie', { akcja: 'restart' }));
  f.appendChild(kreska());
  f.appendChild(poz('kosz', 'Wyczyść pamięć podręczną', { akcja: 'pamiec' }));
  f.appendChild(poz('wyloguj', 'Wyloguj ze wszystkich stron', { czerwona: true, dalej: true, klik: () => { ekran = 'wyloguj'; rysuj(); } }));
  return f;
}

// ustawienia → Wtyczki: dodatki i poprawki stron od Live Production, bez nowej wersji LivePL (src/wtyczki.js)
function ekranWtyczki() {
  const f = document.createDocumentFragment();
  f.appendChild(naglowek('Wtyczki', 'ustawienia'));
  const W = D.wtyczki || { wtyczki: [] };
  f.appendChild(el('div', 'opis', 'Dodatki i poprawki stron od Live Production, podpisane kluczem LivePL. Przychodzą same, bez nowej wersji przeglądarki. Włączona wtyczka działa od razu, wyłączona znika po odświeżeniu strony.'));
  if (!W.wtyczki.length) {
    f.appendChild(el('div', 'opis' + (W.blad ? ' zly' : ''), W.blad ? 'Nie udało się pobrać listy wtyczek: ' + W.blad : 'Na razie nie ma żadnych wtyczek.'));
  }
  for (const w of W.wtyczki) {
    const b = el('button', 'poz');
    b.type = 'button';
    b.setAttribute('role', 'menuitemcheckbox');
    b.setAttribute('aria-checked', String(!!w.wlaczona));
    const pole = el('span', 'pole' + (w.wlaczona ? ' wl' : ''));
    pole.appendChild(ikonaEl('znacznik'));
    b.append(pole, el('span', 'tekst', w.nazwa + (w.wersja ? ` (${w.wersja})` : '')));
    b.addEventListener('click', () => { w.wlaczona = !w.wlaczona; P.akcja('wtyczka', w.id); rysuj(); });
    f.appendChild(b);
    f.appendChild(el('div', 'opis slaby-maly', [w.opis, 'Strony: ' + w.strony].filter(Boolean).join(' · ')));
  }
  f.appendChild(kreska());
  f.appendChild(poz('przywroc', 'Sprawdź teraz', { akcja: 'wtyczki-sprawdz' }));
  if (W.sprawdzono) f.appendChild(el('div', 'opis slaby-maly', 'Sprawdzono: ' + new Date(W.sprawdzono).toLocaleString('pl-PL')));
  return f;
}

// ustawienia → Dodatki: nieobowiązkowe pliki .livepl-dodatek od Live Production (src/dodatki.js)
function ekranDodatki() {
  const f = document.createDocumentFragment();
  f.appendChild(naglowek('Dodatki', 'ustawienia'));
  const lista = D.dodatki || [];
  f.appendChild(el('div', 'opis', 'Nieobowiązkowe części LivePL w osobnych plikach od Live Production. Instaluje je tylko ten, kto chce, i można je w każdej chwili usunąć.'));
  if (!lista.length) f.appendChild(el('div', 'opis', 'Nie masz zainstalowanych dodatków.'));
  for (const d of lista) {
    f.appendChild(el('div', 'opis', d.nazwa + (d.wersja ? ` (${d.wersja})` : '')));
    if (d.opis) f.appendChild(el('div', 'opis slaby-maly', d.opis));
    if (d.blad) f.appendChild(el('div', 'opis zly', 'Nie działa: ' + d.blad));
    if (d.poRestarcie) f.appendChild(el('div', 'opis slaby-maly', `Wersja ${d.nowaWersja || 'nowa'} zadziała po ponownym uruchomieniu LivePL.`));
    if (d.id === 'rozszerzenia-chrome' && D.rozszerzenia) {
      f.appendChild(poz('plus', 'Rozszerzenia Chrome', { dalej: true, klik: () => { ekran = 'rozszerzenia'; rysuj(); } }));
    }
    f.appendChild(poz('kosz', 'Usuń dodatek „' + d.nazwa + '”', { akcja: 'dodatek-usun', arg: d.id, czerwona: true }));
  }
  f.appendChild(kreska());
  if (!lista.some(d => d.id === 'rozszerzenia-chrome')) {
    f.appendChild(el('div', 'opis slaby-maly', 'Do pobrania: Rozszerzenia Chrome, czyli dodawanie rozszerzeń Chrome z pliku albo z folderu. Po pobraniu LivePL sam zapyta, czy zainstalować.'));
  }
  f.appendChild(poz('pobrane', 'Dodatki do pobrania (app.livepl.eu)', { akcja: 'dodatki-strona' }));
  f.appendChild(poz('plus', 'Zainstaluj z pliku…', { akcja: 'dodatek-z-pliku' }));
  return f;
}

// ustawienia → Rozszerzenia Chrome: z pliku .crx / .zip albo z folderu (dodatek dodatki/rozszerzenia-chrome)
function ekranRozszerzenia() {
  if (!D.rozszerzenia) { ekran = 'dodatki'; return ekranDodatki(); } // dodatek usunięty albo jeszcze niezainstalowany
  const f = document.createDocumentFragment();
  f.appendChild(naglowek('Rozszerzenia Chrome', 'ustawienia'));
  const R = D.rozszerzenia;
  f.appendChild(el('div', 'opis', 'Rozszerzenia działają na stronach wczytanych po dodaniu. Silnik LivePL obsługuje część możliwości Chrome: skrypty na stronach i zapis danych tak, przyciski rozszerzeń na pasku będą w kolejnej wersji.'));
  if (!R.length) f.appendChild(el('div', 'opis', 'Nie masz jeszcze żadnych rozszerzeń.'));
  for (const r of R) {
    const b = el('button', 'poz');
    b.type = 'button';
    b.setAttribute('role', 'menuitemcheckbox');
    b.setAttribute('aria-checked', String(!!r.wlaczone));
    const pole = el('span', 'pole' + (r.wlaczone ? ' wl' : ''));
    pole.appendChild(ikonaEl('znacznik'));
    b.append(pole, el('span', 'tekst', r.nazwa + (r.wersja ? ` (${r.wersja})` : '')));
    b.addEventListener('click', () => { r.wlaczone = !r.wlaczone; P.akcja('rozszerzenie-przelacz', r.katalog); rysuj(); });
    f.appendChild(b);
    const opis = [r.opis, r.uprawnienia.length ? 'Dostęp: ' + r.uprawnienia.join(', ') : ''].filter(Boolean).join(' · ');
    if (opis) f.appendChild(el('div', 'opis slaby-maly', opis));
    if (r.blad) f.appendChild(el('div', 'opis zly', 'Nie działa: ' + r.blad));
    f.appendChild(poz('kosz', 'Usuń „' + r.nazwa + '”', { akcja: 'rozszerzenie-usun', arg: r.katalog, czerwona: true }));
  }
  f.appendChild(kreska());
  f.appendChild(poz('plus', 'Dodaj z pliku (.crx, .zip)…', { akcja: 'rozszerzenie-dodaj', arg: 'plik' }));
  f.appendChild(poz('pobrane', 'Dodaj z folderu…', { akcja: 'rozszerzenie-dodaj', arg: 'folder' }));
  return f;
}

function ekranWyloguj() {
  const f = document.createDocumentFragment();
  f.appendChild(naglowek('Wylogować ze wszystkich stron?', 'ustawienia'));
  f.appendChild(el('div', 'opis', 'Usunięte zostaną zapamiętane logowania i dane stron. Do każdego programu trzeba będzie zalogować się od nowa.'));
  const p = el('div', 'przyciski');
  const tak = el('button', 'przycisk czerwony', 'Wyloguj'); tak.type = 'button';
  tak.addEventListener('click', () => P.akcja('wyloguj'));
  const nie = el('button', 'przycisk', 'Anuluj'); nie.type = 'button';
  nie.addEventListener('click', () => { ekran = 'ustawienia'; rysuj(); });
  p.append(tak, nie);
  f.appendChild(p);
  return f;
}

function ekranOProgramie() {
  const f = document.createDocumentFragment();
  f.appendChild(naglowek('O programie', 'menu'));
  const o = el('div', 'o-programie');
  const pic = logoWstega();
  const t = el('div');
  t.append(el('b', '', 'LivePL'), el('small', '', 'Przeglądarka, wersja ' + D.wersja));
  o.append(pic, t);
  f.appendChild(o);
  for (const [a, b] of [['Silnik LivePL', D.silnik], ['Pobrane pliki', D.folderPobranych], ['Dane programu', D.folderDanych]]) {
    const l = el('div', 'linia');
    l.append(el('span', '', a + ': '), el('b', '', b));
    f.appendChild(l);
  }
  f.appendChild(kreska());
  f.appendChild(blokAktualizacji());
  f.appendChild(kreska());
  f.appendChild(poz('plik', 'Licencje i podziękowania', { akcja: 'licencje' }));
  return f;
}

function godzinaZ(ms) {
  const d = new Date(ms);
  return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
}

function blokAktualizacji() {
  const f = document.createDocumentFragment();
  const a = D.aktualizacja || {};
  const opisy = {
    sprawdzam: 'Sprawdzam, czy jest nowa wersja…',
    dostepna: `Jest nowa wersja ${a.wersja} (masz ${D.wersja}).`,
    pobieram: `Pobieram wersję ${a.wersja}…`,
    aktualna: `Masz najnowszą wersję${a.sprawdzono ? ', sprawdzono o ' + godzinaZ(a.sprawdzono) : ''}.`,
    gotowa: `Wersja ${a.wersja} jest pobrana i czeka na ponowne uruchomienie.`,
    silnik: `Wersja ${a.wersja} wymaga nowego silnika LivePL. Potrzebny jest nowy instalator.`,
    blad: `Nie udało się sprawdzić aktualizacji: ${a.blad || 'nieznany błąd'}.`
  };
  const tekst = opisy[a.stan];
  if (tekst) f.appendChild(el('div', 'opis' + (a.stan === 'blad' ? ' zly' : ''), tekst));
  if ((a.stan === 'gotowa' || a.stan === 'dostepna') && a.notatki) f.appendChild(el('div', 'opis', a.notatki));
  if (a.stan === 'dostepna') {
    f.appendChild(poz('pobrane', `Pobierz wersję ${a.wersja}`, { akcja: 'aktualizacje-pobierz' }));
  } else if (a.stan === 'gotowa') {
    f.appendChild(poz('przywroc', 'Uruchom ponownie i zaktualizuj', { akcja: 'aktualizacje-uruchom' }));
  } else {
    f.appendChild(poz('pobrane', 'Sprawdź aktualizacje', {
      akcja: 'aktualizacje-sprawdz',
      wylaczona: a.stan === 'sprawdzam' || a.stan === 'pobieram'
    }));
  }
  if (a.kopia && a.kopia.wersja) {
    f.appendChild(poz('wstecz', `Wróć do wersji ${a.kopia.wersja}`, { akcja: 'aktualizacje-przywroc' }));
  }
  return f;
}

// okienko po włączeniu: „Jest nowa wersja. Pobrać?”, potem pobieranie i „Uruchomić ponownie?”
function ekranAktualizacja() {
  const f = document.createDocumentFragment();
  const a = D.aktualizacja || {};
  f.appendChild(marka());
  const naglowekStanu = (klasa, ikona, tekst) => {
    const n = el('div', 'ssl-naglowek ' + klasa);
    n.append(ikonaEl(ikona), el('span', '', tekst));
    f.appendChild(n);
  };
  const przyciski = (glowny, tekstGlowny, drugi, tekstDrugi) => {
    const p = el('div', 'przyciski');
    const tak = el('button', 'przycisk czerwony', tekstGlowny); tak.type = 'button';
    tak.addEventListener('click', () => P.akcja(glowny));
    p.appendChild(tak);
    if (drugi) {
      const nie = el('button', 'przycisk tekstowy', tekstDrugi); nie.type = 'button';
      nie.addEventListener('click', () => P.akcja(drugi));
      p.appendChild(nie);
    }
    f.appendChild(p);
    setTimeout(() => tak.focus(), 30);
  };
  const coNowego = () => {
    if (!a.notatki) return;
    f.appendChild(el('div', 'sekcja-tytul', 'Co nowego'));
    f.appendChild(el('div', 'opis', a.notatki));
  };
  if (a.stan === 'dostepna') {
    naglowekStanu('ok', 'pobrane', `Jest nowa wersja LivePL ${a.wersja}`);
    f.appendChild(el('div', 'opis', `Masz wersję ${D.wersja}.` + (a.rozmiar ? ` Do pobrania ${Math.max(1, Math.round(a.rozmiar / 1024))} kB, podpisane kluczem LivePL.` : '')));
    coNowego();
    przyciski('aktualizacje-pobierz', 'Pobierz i zainstaluj', 'aktualizacje-pozniej', 'Później');
  } else if (a.stan === 'pobieram' || a.stan === 'sprawdzam') {
    naglowekStanu('ok', 'pobrane', a.stan === 'pobieram' ? `Pobieram wersję ${a.wersja}…` : 'Sprawdzam aktualizacje…');
    f.appendChild(el('div', 'opis', 'To potrwa chwilę. Możesz dalej korzystać z LivePL.'));
  } else if (a.stan === 'gotowa') {
    naglowekStanu('ok', 'klodka', `Wersja ${a.wersja} jest gotowa`);
    f.appendChild(el('div', 'opis', 'Podpis i sumy plików się zgadzają. Nowa wersja wejdzie po ponownym uruchomieniu LivePL; otwarte programy wrócą same.'));
    coNowego();
    przyciski('aktualizacje-uruchom', 'Uruchom ponownie teraz', 'aktualizacje-pozniej', 'Przy następnym uruchomieniu');
  } else if (a.stan === 'blad') {
    naglowekStanu('zly', 'uwaga', 'Nie udało się pobrać aktualizacji');
    f.appendChild(el('div', 'opis zly', a.blad || 'Nieznany błąd.'));
    przyciski('aktualizacje-pobierz', 'Spróbuj jeszcze raz', 'aktualizacje-pozniej', 'Zamknij');
  } else if (a.stan === 'silnik') {
    naglowekStanu('uwaga', 'uwaga', `Wersja ${a.wersja} wymaga nowego instalatora`);
    f.appendChild(el('div', 'opis', 'Ta wersja potrzebuje nowego silnika LivePL. Pobierz instalator ze strony LivePL.'));
    przyciski('aktualizacje-pozniej', 'Zamknij');
  } else {
    naglowekStanu('ok', 'klodka', 'Masz najnowszą wersję LivePL');
    f.appendChild(el('div', 'opis', `Wersja ${D.wersja}.`));
    przyciski('aktualizacje-pozniej', 'Zamknij');
  }
  return f;
}

function dataPL(ms) {
  const d = new Date(ms);
  return String(d.getDate()).padStart(2, '0') + '.' + String(d.getMonth() + 1).padStart(2, '0') + '.' + d.getFullYear();
}

// szczegóły certyfikatu SSL bieżącej strony
function ekranSsl() {
  const f = document.createDocumentFragment();
  const s = D.ssl;
  if (!s) {
    f.appendChild(el('div', 'pusto', 'Ta strona nie łączy się z internetem.'));
    return f;
  }
  const tytuly = { ok: 'Połączenie szyfrowane (SSL)', brak: 'Brak SSL', zly: 'Certyfikat nieprawidłowy' };
  const opisy = {
    ok: 'Dane między Tobą a stroną są zaszyfrowane, a certyfikat jest ważny.',
    brak: 'Dane między Tobą a stroną nie są szyfrowane. Nie wpisuj tu haseł ani danych klientów.',
    zly: 'LivePL zablokował tę stronę, bo jej certyfikat jest nieprawidłowy albo wystawił go nieznany urząd.'
  };
  const w = s.stan === 'zly' ? s.wyjatek : null;
  const n = el('div', 'ssl-naglowek ' + (s.stan === 'ok' ? 'ok' : 'zly'));
  n.append(ikonaEl(s.stan === 'ok' ? 'klodka' : s.stan === 'brak' ? 'klodka-otwarta' : 'uwaga'), el('span', '', tytuly[s.stan] || ''));
  f.appendChild(n);
  f.appendChild(el('div', 'opis', w
    ? `Ta strona jest otwarta mimo ostrzeżenia (${w.trwaly ? 'zapamiętany certyfikat' : 'do zamknięcia LivePL'}). LivePL nie może potwierdzić, kto jest po drugiej stronie. Nie wpisuj tu haseł, jeśli nie musisz.`
    : (opisy[s.stan] || '')));
  const linie = [['Strona', w ? w.klucz : s.host]];
  if (w) {
    linie.push(['Odcisk SHA-256', w.odcisk]);
    if (w.trwaly && w.dodano) linie.push(['Zapamiętano', dataPL(w.dodano)]);
  }
  if (s.stan === 'ok' && s.wystawca) {
    linie.push(['Wystawca', s.nasz ? s.wystawca + ' (nasz urząd LivePL)' : [s.wystawca, s.wystawcaFirma].filter(Boolean).join(', ')]);
  }
  if (s.stan === 'ok' && s.waznyDo) {
    const dni = Math.floor((s.waznyDo - Date.now()) / 86400000);
    linie.push(['Ważny do', dataPL(s.waznyDo) + (dni >= 0 ? ' (jeszcze ' + dni + ' ' + (dni === 1 ? 'dzień' : 'dni') + ')' : '')]);
  }
  for (const [a, b] of linie) {
    const l = el('div', 'linia' + (a === 'Odcisk SHA-256' ? ' odcisk' : ''));
    l.append(el('span', '', a + ': '), el('b', '', b || ''));
    f.appendChild(l);
  }
  if (w) {
    f.appendChild(kreska());
    f.appendChild(poz('kosz', w.trwaly ? 'Usuń wyjątek i zablokuj znowu' : 'Cofnij wejście i zablokuj znowu', { akcja: 'wyjatek-usun', arg: w.klucz, czerwona: true }));
  }
  const odstep = el('div');
  odstep.style.height = '8px';
  f.appendChild(odstep);
  return f;
}

// ustawienia → Wyjątki certyfikatów: strony otwierane mimo błędu certyfikatu (host:port + odcisk)
function ekranWyjatki() {
  const f = document.createDocumentFragment();
  const lista = D.wyjatkiCertyfikatow || [];
  f.appendChild(naglowek('Wyjątki certyfikatów', 'ustawienia'));
  if (!lista.length) {
    f.appendChild(el('div', 'opis', 'Nie ma zapamiętanych wyjątków. Gdy strona ma nieprawidłowy certyfikat, LivePL ją blokuje. Na stronie błędu możesz świadomie wejść mimo to i zapamiętać jej certyfikat.'));
  }
  for (const w of lista) {
    const r = el('div', 'wiersz strona-wiersz');
    const ik = el('span', 'ikonka-wiersza'); ik.appendChild(ikonaEl('uwaga'));
    const opis = el('div', 'strona-opis');
    const odcisk = String(w.odcisk || '').split(':').slice(0, 8).join(':') + '…';
    const pod = [w.dla, w.dodano ? 'od ' + dataPL(w.dodano) : ''].filter(Boolean).join(' · ');
    opis.append(el('b', 'haslo-host', w.klucz), el('div', 'strona-adres', pod), el('div', 'strona-adres odcisk', odcisk));
    opis.title = 'Odcisk SHA-256: ' + w.odcisk;
    const usun = el('button', 'maly usun-strone'); usun.type = 'button'; usun.title = 'Usuń wyjątek (strona znowu będzie zablokowana)';
    usun.appendChild(ikonaEl('kosz'));
    usun.addEventListener('click', () => P.akcja('wyjatek-usun', w.klucz));
    r.append(ik, opis, usun);
    f.appendChild(r);
  }
  f.appendChild(kreska());
  f.appendChild(el('div', 'opis', 'Każdy wyjątek dotyczy jednego adresu z portem i jednego certyfikatu. Gdy serwer pokaże inny certyfikat, LivePL znowu ostrzeże. Dla adresów Live Production i stron z HSTS wyjątków nie ma.'));
  return f;
}

// ustawienia → Bezpieczny DNS (DNS przez HTTPS)
function ekranDns() {
  const f = document.createDocumentFragment();
  const d = D.dns || { tryb: 'wylaczony', dostawcy: [] };
  f.appendChild(naglowek('Bezpieczny DNS', 'ustawienia'));
  f.appendChild(el('div', 'opis', 'DNS zamienia adres strony (np. server.tvml.pl) na numer serwera. Zwykle robi to sieć, z której korzystasz, i czasem pamięta stare albo błędne wpisy. Bezpieczny DNS pyta zaufanego dostawcę przez szyfrowane połączenie.'));
  const opcje = [
    ['wylaczony', 'Wyłączony', 'DNS z Windows i Twojej sieci'],
    ...d.dostawcy.map(x => [x.id, x.nazwa, x.id === 'cloudflare' ? '1.1.1.1, szyfrowany (DNS przez HTTPS)' : '8.8.8.8, szyfrowany (DNS przez HTTPS)'])
  ];
  for (const [id, tekst, podpis] of opcje) {
    const wl = d.tryb === id;
    const b = el('button', 'poz poz-wysoka');
    b.type = 'button';
    b.setAttribute('role', 'menuitemradio');
    b.setAttribute('aria-checked', String(wl));
    const pole = el('span', 'pole okragle' + (wl ? ' wl' : ''));
    pole.appendChild(ikonaEl('znacznik'));
    const t = el('span', 'tekst');
    t.append(el('span', '', tekst), el('small', 'podpis-pozycji', podpis));
    b.append(pole, t);
    b.addEventListener('click', () => {
      if (wl) return;
      d.tryb = id;
      P.akcja('dns', id);
      rysuj();
    });
    f.appendChild(b);
  }
  f.appendChild(kreska());
  f.appendChild(el('div', 'opis', 'Gdy dostawca nie odpowiada, LivePL sam wraca do DNS z Windows, więc strony nie przestają działać. Zmiana działa od razu.'));
  return f;
}

// certyfikat osobisty LivePL z YubiKey (do logowania w panelu urzędu i do poczty)
function ekranCertOsobisty() {
  const f = document.createDocumentFragment();
  const s = D.certOsobisty || { lista: [], sprawdzono: 0 };
  f.appendChild(naglowek('Certyfikat osobisty', 'ustawienia'));
  if (!s.lista.length) {
    f.appendChild(el('div', 'opis', s.sprawdzono
      ? 'Nie widzę certyfikatu osobistego LivePL. Włóż YubiKey z certyfikatem i odczekaj kilka sekund.'
      : 'Sprawdzam certyfikaty w Windows…'));
  }
  for (const c of s.lista) {
    const n = el('div', 'ssl-naglowek ok');
    n.append(ikonaEl('klodka'), el('span', '', c.nazwa));
    f.appendChild(n);
    const linie = [['E-mail', c.email], ['Ważny do', dataPL(c.waznyDo)], ['Odcisk', String(c.odcisk || '').slice(0, 23) + '…']];
    for (const [a, b] of linie) {
      if (!b) continue;
      const l = el('div', 'linia');
      l.append(el('span', '', a + ': '), el('b', '', b));
      f.appendChild(l);
    }
  }
  f.appendChild(el('div', 'opis', 'Klucz jest tylko na YubiKey i nigdy go nie opuszcza. Przy logowaniu Windows zapyta o PIN, potem dotknij klucza.'));
  f.appendChild(kreska());
  f.appendChild(poz('odswiez', 'Sprawdź jeszcze raz', { akcja: 'cert-osobisty-odswiez' }));
  return f;
}

// rejestracja tej przeglądarki w urzędzie LivePL (klucz w TPM, certyfikat urządzenia)
let zaufanieZRejestracja = true;
function ekranUrzadzenie() {
  const f = document.createDocumentFragment();
  const s = D.urzadzenie || { stan: 'brak' };
  const samodzielny = D.rodzaj === 'urzadzenie';
  if (samodzielny) f.appendChild(marka());
  else f.appendChild(naglowek('Urządzenie LivePL', 'ustawienia'));
  const linia = (a, b) => {
    const l = el('div', 'linia');
    l.append(el('span', '', a + ': '), el('b', '', b));
    f.appendChild(l);
  };
  const naglowekStanu = (klasa, ikona, tekst) => {
    const n = el('div', 'ssl-naglowek ' + klasa);
    n.append(ikonaEl(ikona), el('span', '', tekst));
    f.appendChild(n);
  };
  const klucz = s.tpm ? 'w module TPM (nie da się go skopiować)' : 'w magazynie Windows (bez eksportu)';

  if (D.urzadzenieTrwa) {
    if (D.zaufanieTrwa) {
      naglowekStanu('uwaga', 'znacznik', 'Windows pyta o certyfikat urzędu');
      f.appendChild(el('div', 'opis', 'W okienku Windows sprawdź odcisk (zaczyna się od E2:7C:75) i kliknij Tak. Zaraz potem LivePL zarejestruje przeglądarkę.'));
    } else {
      naglowekStanu('ok', 'klucz', 'Rejestruję tę przeglądarkę…');
      f.appendChild(el('div', 'opis', 'Moduł TPM tworzy klucz, a urząd LivePL wystawia certyfikat. To trwa kilka sekund.'));
    }
    return f;
  }
  if (s.stan === 'brak' || s.stan === 'odrzucony' || s.stan === 'uniewazniony') {
    if (s.stan === 'brak') naglowekStanu('uwaga', 'klucz', 'Zarejestruj tę przeglądarkę');
    else naglowekStanu('zly', 'uwaga', s.stan === 'odrzucony' ? 'Urząd odrzucił wniosek' : 'Rejestracja została unieważniona');
    f.appendChild(el('div', 'opis', 'Jedno kliknięcie: LivePL utworzy klucz w module TPM tego komputera (nie da się go skopiować) i zarejestruje przeglądarkę w urzędzie LivePL. Urząd dostanie nazwę komputera, użytkownika Windows i wersję systemu.'));
    // certyfikat urzędu w Windows przy okazji (zaznaczone, gdy Windows go jeszcze nie ma)
    const bezZaufania = !(D.zaufanie && D.zaufanie.zaufany);
    if (bezZaufania) {
      const b = el('button', 'poz poz-wysoka');
      b.type = 'button';
      b.setAttribute('role', 'checkbox');
      b.setAttribute('aria-checked', String(zaufanieZRejestracja));
      const pole = el('span', 'pole' + (zaufanieZRejestracja ? ' wl' : ''));
      pole.appendChild(ikonaEl('znacznik'));
      const t = el('span', 'tekst');
      t.append(el('span', '', 'Zainstaluj też certyfikat urzędu w Windows'), el('span', 'podpis-pozycji', 'Wtedy Windows, Edge i Outlook też rozpoznają nasze certyfikaty. Windows zapyta o zgodę.'));
      b.append(pole, t);
      b.addEventListener('click', () => { zaufanieZRejestracja = !zaufanieZRejestracja; rysuj(); });
      f.appendChild(b);
    }
    const p = el('div', 'przyciski');
    const tak = el('button', 'przycisk czerwony', s.stan === 'brak' ? 'Zarejestruj' : 'Wyślij nowy wniosek'); tak.type = 'button';
    tak.addEventListener('click', () => P.akcja('urzadzenie-zarejestruj', { zaufanie: bezZaufania && zaufanieZRejestracja }));
    p.appendChild(tak);
    if (samodzielny) {
      const nie = el('button', 'przycisk tekstowy', 'Nie teraz'); nie.type = 'button';
      nie.addEventListener('click', () => P.akcja('urzadzenie-pozniej'));
      p.appendChild(nie);
    }
    f.appendChild(p);
  } else if (s.stan === 'wyslany') {
    naglowekStanu('uwaga', 'klucz', 'Wniosek czeka na urząd');
    f.appendChild(el('div', 'opis', 'Urząd jeszcze nie wystawił certyfikatu (np. brak połączenia). LivePL sprawdza to sam co 15 minut. Kod tej przeglądarki:'));
    f.appendChild(el('div', 'kod-duzy', s.kod));
    linia('Komputer', s.nazwa);
    linia('Klucz', klucz);
    if (s.wyslano) linia('Wysłano', new Date(s.wyslano).toLocaleString('pl-PL', { dateStyle: 'short', timeStyle: 'short' }));
    f.appendChild(poz('odswiez', 'Sprawdź teraz', { akcja: 'urzadzenie-sprawdz' }));
  } else if (s.stan === 'gotowe') {
    naglowekStanu('ok', 'klodka', 'Przeglądarka zarejestrowana');
    linia('Komputer', s.nazwa);
    linia('Klucz', klucz);
    linia('Certyfikat ważny do', dataPL(s.waznyDo));
    linia('Kod', s.kod);
    f.appendChild(el('div', 'opis', 'Strony LivePL mogą rozpoznać tę przeglądarkę po certyfikacie urządzenia. Zgubiony komputer unieważnisz w panelu urzędu.'));

    // bezpieczeństwo: połączenie z urzędem, klucz, dysk i dane LivePL (to samo widzi panel urzędu)
    f.appendChild(el('div', 'sekcja-tytul', 'Bezpieczeństwo'));
    const wiersz = (ok, tekst) => {
      const w = el('div', 'linia-bezp ' + (ok === true ? 'ok' : ok === false ? 'zly' : 'slaby'));
      w.append(ikonaEl(ok === true ? 'klodka' : 'uwaga'), el('span', '', tekst));
      f.appendChild(w);
    };
    const szyfrowane = s.polaczenie && !/bez szyfrowania/.test(s.polaczenie);
    wiersz(s.polaczenie ? szyfrowane : null,
      !s.polaczenie ? 'Połączenie z urzędem: jeszcze nie sprawdzone'
        : szyfrowane ? 'Połączenie z urzędem: szyfrowane (' + s.polaczenie + ')' : 'Połączenie z urzędem: ' + s.polaczenie);
    wiersz(true, s.tpm ? 'Klucz przeglądarki w module TPM, nie do skopiowania' : 'Klucz przeglądarki w magazynie Windows, bez eksportu');
    if (s.dysk) {
      wiersz(s.dysk === 'bitlocker' ? true : s.dysk === 'nieznany' ? null : false,
        s.dysk === 'bitlocker' ? 'Dysk systemowy zaszyfrowany (BitLocker)'
          : s.dysk === 'szyfrowanie' ? 'Dysk systemowy: BitLocker właśnie szyfruje'
            : s.dysk === 'brak' ? 'Dysk systemowy bez szyfrowania: włącz BitLocker w Windows' : 'Dysk systemowy: nie udało się sprawdzić');
    }
    if (s.dpapi !== undefined) wiersz(!!s.dpapi, s.dpapi ? 'Hasła w LivePL szyfruje Windows (DPAPI)' : 'Windows nie szyfruje haseł LivePL');
    if (s.raport) f.appendChild(el('div', 'opis', 'Ostatni raport do urzędu: ' + new Date(s.raport).toLocaleString('pl-PL', { dateStyle: 'short', timeStyle: 'short' })));
    f.appendChild(poz('odswiez', 'Sprawdź teraz', { akcja: 'urzadzenie-sprawdz' }));
  }
  if (s.blad && s.stan !== 'gotowe') f.appendChild(el('div', 'opis zly', 'Ostatni błąd: ' + s.blad));
  return f;
}

// certyfikat urzędu LivePL w Windows: okienko po pierwszym uruchomieniu i ekran w ustawieniach
function ekranZaufanie() {
  const f = document.createDocumentFragment();
  const s = D.zaufanie;
  const samodzielny = D.rodzaj === 'zaufanie';
  if (samodzielny) f.appendChild(marka());
  else f.appendChild(naglowek('Urząd LivePL w Windows', 'ustawienia'));
  if (!s) {
    f.appendChild(el('div', 'opis', 'Sprawdzam certyfikaty w Windows…'));
    return f;
  }
  const n = el('div', 'ssl-naglowek ' + (s.zaufany ? 'ok' : 'uwaga'));
  n.append(ikonaEl(s.zaufany ? 'klodka' : 'uwaga'), el('span', '', s.zaufany ? 'Windows ufa urzędowi LivePL' : 'Zainstaluj certyfikat urzędu LivePL'));
  f.appendChild(n);
  f.appendChild(el('div', 'opis', s.zaufany
    ? 'Windows, Edge, Chrome i poczta rozpoznają certyfikaty LivePL: strony, podpisy e-maili i logowanie.'
    : 'Bez niego Windows, Edge, Chrome i poczta nie rozpoznają certyfikatów LivePL (strony, podpisy e-maili, logowanie). Przeglądarka LivePL ufa im sama. Certyfikat trafi na Twoje konto Windows; Windows pokaże okienko z odciskiem do potwierdzenia.'));
  const linie = [
    ['Certyfikat', 'LivePL Root CA R1, ważny do ' + dataPL(s.waznyDo)],
    ['Odcisk SHA-256', String(s.odcisk || '').slice(0, 23) + '…']
  ];
  if (s.zaufany) {
    linie.push(['Zainstalowany', s.komputer ? 'dla całego komputera' : 'dla tego użytkownika']);
    linie.push(['LivePL CA 1 (pośredni)', s.posredni ? 'zainstalowany' : 'brak']);
  }
  for (const [a, b] of linie) {
    const l = el('div', 'linia');
    l.append(el('span', '', a + ': '), el('b', '', b));
    if (a === 'Odcisk SHA-256') l.title = s.odcisk;
    f.appendChild(l);
  }
  const odstep = el('div');
  odstep.style.height = '10px';
  f.appendChild(odstep);
  if (!s.zaufany) {
    const p = el('div', 'przyciski');
    // domyślnie tylko dla zalogowanego użytkownika (bez uprawnień administratora)
    const tak = el('button', 'przycisk czerwony', 'Zainstaluj'); tak.type = 'button';
    tak.title = 'Dla Twojego konta Windows (bez uprawnień administratora)';
    tak.addEventListener('click', () => P.akcja('zaufanie-uzytkownik'));
    const komputer = el('button', 'przycisk', 'Dla całego komputera'); komputer.type = 'button';
    komputer.title = 'Dla wszystkich kont na tym komputerze, po zgodzie administratora';
    komputer.addEventListener('click', () => P.akcja('zaufanie-komputer'));
    p.append(tak, komputer);
    if (samodzielny) {
      const nie = el('button', 'przycisk tekstowy', 'Nie teraz'); nie.type = 'button';
      nie.addEventListener('click', () => P.akcja('zaufanie-pozniej'));
      p.append(nie);
    }
    f.appendChild(p);
    setTimeout(() => tak.focus(), 50);
  } else if (!s.posredni) {
    f.appendChild(poz('plus', 'Dodaj LivePL CA 1 do pośrednich urzędów', { akcja: 'zaufanie-uzytkownik' }));
  }
  if (!samodzielny) {
    f.appendChild(kreska());
    f.appendChild(poz('odswiez', 'Sprawdź jeszcze raz', { akcja: 'zaufanie-odswiez' }));
  }
  return f;
}

// potwierdzenie logowania do panelu urzędu
function ekranPodpis() {
  const f = document.createDocumentFragment();
  const s = D.podpis || {};
  f.appendChild(marka());
  const n = el('div', 'ssl-naglowek ok');
  n.append(ikonaEl('klodka'), el('span', '', 'Logowanie do panelu urzędu'));
  f.appendChild(n);
  f.appendChild(el('div', 'opis', `Strona ${s.host || ''} prosi o logowanie Twoim certyfikatem osobistym. Po kliknięciu Zaloguj Windows zapyta o PIN do YubiKey, potem dotknij klucza.`));
  const l = el('div', 'linia');
  l.append(el('span', '', 'Certyfikat: '), el('b', '', (s.nazwa || '') + (s.waznyDo ? ', ważny do ' + dataPL(s.waznyDo) : '')));
  f.appendChild(l);
  const odstep = el('div');
  odstep.style.height = '10px';
  f.appendChild(odstep);
  const p = el('div', 'przyciski');
  const tak = el('button', 'przycisk czerwony', 'Zaloguj'); tak.type = 'button';
  tak.addEventListener('click', () => P.akcja('podpis-tak'));
  const nie = el('button', 'przycisk', 'Anuluj'); nie.type = 'button';
  nie.addEventListener('click', () => P.akcja('podpis-nie'));
  p.append(tak, nie);
  f.appendChild(p);
  setTimeout(() => tak.focus(), 50);
  return f;
}

function ekranPobrane() {
  const f = document.createDocumentFragment();
  f.appendChild(naglowek('Pobrane pliki', D.rodzaj === 'menu' ? 'menu' : null));
  if (!D.pobrane.length) {
    f.appendChild(el('div', 'pusto', 'Nic jeszcze nie pobrano.'));
  }
  for (const p of D.pobrane) {
    const w = el('div', 'plik');
    w.appendChild(ikonaEl('plik'));
    const s = el('div', 'srodek');
    s.appendChild(el('div', 'nazwa', p.nazwa));
    if (p.stan === 'trwa' || p.stan === 'przerwane') {
      s.appendChild(el('div', 'stan', p.stan === 'trwa' ? `Pobieranie ${p.procent}%` : 'Wstrzymane'));
      const pas = el('div', 'postep');
      const i = el('i');
      i.style.width = p.procent + '%';
      pas.appendChild(i);
      s.appendChild(pas);
    } else {
      const opis = { gotowe: 'Gotowe, kliknij, żeby otworzyć', anulowane: 'Anulowane', blad: 'Nie udało się pobrać' }[p.stan] || '';
      s.appendChild(el('div', 'stan' + (p.stan === 'blad' ? ' zly' : ''), opis));
    }
    w.appendChild(s);
    if (p.stan === 'gotowe') {
      w.addEventListener('click', () => P.akcja('pobrany-otworz', p.id));
      const b = el('button', 'maly');
      b.type = 'button';
      b.title = 'Pokaż w folderze';
      b.appendChild(ikonaEl('folder'));
      b.addEventListener('click', (e) => { e.stopPropagation(); P.akcja('pobrany-folder', p.id); });
      w.appendChild(b);
    } else if (p.stan === 'trwa' || p.stan === 'przerwane') {
      const b = el('button', 'maly');
      b.type = 'button';
      b.title = 'Anuluj pobieranie';
      b.appendChild(ikonaEl('zamknij'));
      b.addEventListener('click', () => P.akcja('pobrany-anuluj', p.id));
      w.appendChild(b);
    }
    f.appendChild(w);
  }
  f.appendChild(kreska());
  f.appendChild(poz('folder', 'Otwórz folder Pobrane', { akcja: 'folder-pobranych' }));
  return f;
}

function ekranKarta() {
  const f = document.createDocumentFragment();
  const k = D.karta;
  if (!k) return f;
  f.appendChild(poz('odswiez', 'Odśwież', { akcja: 'karta-odswiez', arg: k.id }));
  f.appendChild(poz('zewnetrzna', 'Otwórz w zwykłej przeglądarce', { akcja: 'karta-zewnetrzna', arg: k.id, wylaczona: !/^https?:/i.test(k.adres || '') }));
  f.appendChild(poz('kopiuj', 'Kopiuj adres', { akcja: 'karta-kopiuj', arg: k.id, wylaczona: !k.adres || k.nowa }));
  f.appendChild(poz('pinezka', 'Dodaj do paska', { akcja: 'karta-przypnij', arg: k.id, wylaczona: !k.przypinalna }));
  f.appendChild(kreska());
  f.appendChild(poz('zamknij', 'Zamknij kartę', { skrot: 'Ctrl+W', akcja: 'karta-zamknij', arg: k.id }));
  f.appendChild(poz('karty', 'Zamknij pozostałe karty', { akcja: 'karta-zamknij-inne', arg: k.id, wylaczona: !k.inne }));
  return f;
}

// certyfikaty zainstalowane w Windows (podgląd)
const MAGAZYNY_CERT = [
  ['osobiste', 'Osobiste'],
  ['glowne', 'Główne urzędy'],
  ['posrednie', 'Pośrednie'],
  ['komputer', 'Komputer']
];
let zakladkaCert = 'osobiste';
let szukajCert = '';
let wybranyCert = null;

function znaczek(tekst, klasa) {
  return el('span', 'znaczek ' + (klasa || ''), tekst);
}

function ekranCertyfikaty() {
  const f = document.createDocumentFragment();
  f.appendChild(naglowek('Certyfikaty w Windows', 'menu'));
  const c = D.certyfikatyWindows;
  if (!c) {
    f.appendChild(el('div', 'opis', 'Odczytuję certyfikaty z Windows…'));
    return f;
  }
  const zakladki = el('div', 'zakladki-cert');
  for (const [k, nazwa] of MAGAZYNY_CERT) {
    const b = el('button', 'zakladka' + (k === zakladkaCert ? ' wl' : ''), `${nazwa} ${c[k].length}`);
    b.type = 'button';
    b.addEventListener('click', () => { zakladkaCert = k; rysuj(); });
    zakladki.appendChild(b);
  }
  f.appendChild(zakladki);

  const szukaj = el('input', 'pole-tekstu szukaj-cert');
  szukaj.placeholder = 'Szukaj nazwy albo wystawcy';
  szukaj.value = szukajCert;
  f.appendChild(szukaj);

  const lista = el('div', 'lista-cert');
  const wiersze = [];
  for (const cert of c[zakladkaCert]) {
    const w = el('button', 'wiersz-cert' + (cert.wygasl ? ' wygasl' : ''));
    w.type = 'button';
    const ik = el('span', 'ikonka-wiersza');
    ik.appendChild(ikonaEl(cert.klucz ? 'klucz' : 'klodka'));
    const opis = el('span', 'cert-opis');
    const tytul = el('span', 'cert-tytul');
    tytul.append(el('b', '', cert.nazwa));
    if (cert.livepl) tytul.append(znaczek('LivePL', 'livepl'));
    if (cert.wygasl) tytul.append(znaczek('wygasł', 'zly'));
    // wygasłe mówią kiedy: certyfikaty o tej samej nazwie (np. kolejne lata) wtedy się nie mylą
    opis.append(tytul, el('span', 'strona-adres', `${cert.wystawca} · ${cert.wygasl ? 'wygasł' : 'do'} ${dataPL(cert.do)}`));
    w.append(ik, opis, ikonaEl('dalej'));
    w.dataset.szukaj = (cert.nazwa + ' ' + cert.wystawca + ' ' + cert.rodzaj).toLowerCase();
    w.addEventListener('click', () => { wybranyCert = cert; ekran = 'certyfikat-win'; rysuj(); });
    lista.appendChild(w);
    wiersze.push(w);
  }
  if (!wiersze.length) lista.appendChild(el('div', 'opis', 'Brak certyfikatów w tym magazynie.'));
  const filtruj = () => {
    szukajCert = szukaj.value;
    const t = szukajCert.trim().toLowerCase();
    for (const w of wiersze) w.hidden = !!t && !w.dataset.szukaj.includes(t);
  };
  szukaj.addEventListener('input', filtruj);
  filtruj();
  f.appendChild(lista);
  f.appendChild(kreska());
  f.appendChild(poz('zewnetrzna', 'Menedżer certyfikatów Windows', { akcja: 'cert-win-menedzer' }));
  f.appendChild(poz('odswiez', 'Odśwież', { akcja: 'cert-win-odswiez' }));
  return f;
}

function ekranCertyfikatWin() {
  const f = document.createDocumentFragment();
  const c = wybranyCert;
  f.appendChild(naglowek('Certyfikat', 'certyfikaty'));
  if (!c) return f;
  const n = el('div', 'ssl-naglowek ' + (c.wygasl ? 'zly' : 'ok'));
  n.append(ikonaEl(c.wygasl ? 'uwaga' : (c.klucz ? 'klucz' : 'klodka')), el('span', '', c.nazwa));
  f.appendChild(n);
  const magazyn = (MAGAZYNY_CERT.find(([k]) => k === c.magazyn) || [, c.magazyn])[1];
  const linie = [
    ['Rodzaj', c.rodzaj || '—'],
    ['Wystawca', c.wystawca + (c.livepl ? ' (urząd LivePL)' : '')],
    ['Ważny', `${dataPL(c.od)} – ${dataPL(c.do)}${c.wygasl ? ' (wygasł)' : ''}`],
    ['Klucz prywatny', c.klucz ? (c.livepl && c.magazyn === 'osobiste' ? 'tak (YubiKey albo ten komputer)' : 'tak, na tym komputerze') : 'nie'],
    ['Magazyn', magazyn],
    ['Odcisk SHA-1', c.odcisk],
    ['Odcisk SHA-256', c.sha256 ? c.sha256.slice(0, 23) + '…' : '—']
  ];
  for (const [a, b] of linie) {
    const l = el('div', 'linia');
    l.append(el('span', '', a + ': '), el('b', '', b));
    if (a === 'Odcisk SHA-256') l.title = c.sha256;
    f.appendChild(l);
  }
  const odstep = el('div');
  odstep.style.height = '8px';
  f.appendChild(odstep);
  const arg = { odcisk: c.odcisk, magazyn: c.magazyn, nazwa: c.nazwa };
  f.appendChild(poz('zewnetrzna', 'Otwórz w oknie Windows', { akcja: 'cert-win-pokaz', arg }));
  f.appendChild(poz('pobrane', 'Zapisz jako plik .cer', { akcja: 'cert-win-eksport', arg }));
  if (c.sha256) f.appendChild(poz('kopiuj', 'Kopiuj odcisk SHA-256', { akcja: 'cert-win-kopiuj', arg: c.sha256 }));
  return f;
}

// po zalogowaniu na stronie: zapamiętać hasło?
function ekranHaslo() {
  const f = document.createDocumentFragment();
  const h = D.haslo;
  if (!h) return f;
  const n = el('div', 'ssl-naglowek ok');
  n.append(ikonaEl('klucz'), el('span', '', h.rodzaj === 'aktualizacja' ? 'Zaktualizować zapisane hasło?' : 'Zapamiętać hasło?'));
  f.appendChild(n);
  for (const [a, b] of [['Strona', h.host], ['Login', h.login || '(bez loginu)'], ['Hasło', '••••••••']]) {
    const l = el('div', 'linia');
    l.append(el('span', '', a + ': '), el('b', '', b));
    f.appendChild(l);
  }
  f.appendChild(el('div', 'opis', 'Hasło zostanie zaszyfrowane przez Windows i będzie działać tylko na tym koncie.'));
  const p = el('div', 'przyciski');
  const tak = el('button', 'przycisk czerwony', h.rodzaj === 'aktualizacja' ? 'Zaktualizuj' : 'Zapamiętaj'); tak.type = 'button';
  tak.addEventListener('click', () => P.akcja('haslo-zapisz', h.kartaId));
  const nigdy = el('button', 'przycisk tekstowy', 'Nigdy dla tej strony'); nigdy.type = 'button';
  nigdy.addEventListener('click', () => P.akcja('haslo-nigdy', h.kartaId));
  const nie = el('button', 'przycisk tekstowy', 'Nie teraz'); nie.type = 'button';
  nie.addEventListener('click', () => P.akcja('haslo-nie', h.kartaId));
  p.append(tak, nigdy, nie);
  f.appendChild(p);
  setTimeout(() => tak.focus(), 50);
  return f;
}

// przy polu logowania: wybór zapisanego konta
function ekranHaslaWybor() {
  const f = document.createDocumentFragment();
  const w = D.haslaWybor;
  if (!w) return f;
  f.appendChild(el('div', 'opis', 'Zapisane konta dla ' + w.host));
  for (const k of w.konta) {
    f.appendChild(poz('klucz', k.login || '(bez loginu)', { akcja: 'haslo-wypelnij', arg: { kartaId: w.kartaId, id: k.id } }));
  }
  f.appendChild(kreska());
  f.appendChild(poz('ustawienia', 'Zarządzaj hasłami', { dalej: true, klik: () => { ekran = 'hasla'; rysuj(); P.akcja('hasla-odswiez'); } }));
  return f;
}

// ustawienia → hasła
function ekranHasla() {
  const f = document.createDocumentFragment();
  const h = D.hasla || { wpisy: [], nigdy: [], wlaczone: true, dostepne: true };
  f.appendChild(naglowek('Hasła', 'ustawienia'));
  f.appendChild(zaznaczenie('Proponuj zapamiętywanie haseł', h.wlaczone, 'hasla-wlacz', () => { h.wlaczone = !h.wlaczone; }));
  if (!h.dostepne) f.appendChild(el('div', 'opis zly', 'Windows nie pozwala teraz szyfrować danych, więc LivePL nie zapisze haseł.'));
  if (!h.wpisy.length) {
    f.appendChild(el('div', 'opis', 'Nie ma jeszcze zapisanych haseł. Po zalogowaniu na stronie LivePL zapyta, czy je zapamiętać.'));
  }
  for (const w of h.wpisy) {
    const r = el('div', 'wiersz strona-wiersz');
    const ik = el('span', 'ikonka-wiersza'); ik.appendChild(ikonaEl('klucz'));
    const opis = el('div', 'strona-opis');
    opis.append(el('b', 'haslo-host', w.host), el('div', 'strona-adres', w.login || '(bez loginu)'));
    const kopiuj = el('button', 'maly'); kopiuj.type = 'button'; kopiuj.title = 'Kopiuj hasło (schowek wyczyści się za 30 s)';
    kopiuj.appendChild(ikonaEl('kopiuj'));
    kopiuj.addEventListener('click', () => P.akcja('haslo-kopiuj', w.id));
    const usun = el('button', 'maly usun-strone'); usun.type = 'button'; usun.title = 'Usuń zapisane hasło';
    usun.appendChild(ikonaEl('kosz'));
    usun.addEventListener('click', () => P.akcja('haslo-usun', w.id));
    r.append(ik, opis, kopiuj, usun);
    f.appendChild(r);
  }
  if (h.nigdy.length) {
    f.appendChild(kreska());
    f.appendChild(el('div', 'opis', 'Nigdy nie proponuj na:'));
    for (const n of h.nigdy) {
      const r = el('div', 'wiersz strona-wiersz');
      const opis = el('div', 'strona-opis'); opis.appendChild(el('span', '', n.host));
      const usun = el('button', 'maly usun-strone'); usun.type = 'button'; usun.title = 'Usuń z wyjątków';
      usun.appendChild(ikonaEl('zamknij'));
      usun.addEventListener('click', () => P.akcja('haslo-nigdy-usun', n.origin));
      r.append(opis, usun);
      f.appendChild(r);
    }
  }
  f.appendChild(kreska());
  f.appendChild(el('div', 'opis', 'Hasła są zaszyfrowane przez Windows (DPAPI) i działają tylko na tym koncie Windows. Strony nie widzą listy haseł; LivePL wypełnia formularz dopiero po wybraniu konta.'));
  return f;
}

// ustawienia → Dostęp dla AI (też po kliknięciu lampki „AI” na pasku)
function wyborAI(tekst, podpis, wartosc) {
  const wl = D.ai.poziom === wartosc;
  const b = el('button', 'poz poz-wysoka');
  b.type = 'button';
  b.setAttribute('role', 'menuitemradio');
  b.setAttribute('aria-checked', String(wl));
  const pole = el('span', 'pole okragle' + (wl ? ' wl' : ''));
  pole.appendChild(ikonaEl('znacznik'));
  const t = el('span', 'tekst');
  t.append(el('span', '', tekst), el('small', 'podpis-pozycji', podpis));
  b.append(pole, t);
  b.addEventListener('click', () => {
    if (wl) return;
    D.ai.poziom = wartosc;
    P.akcja('ai-poziom', wartosc);
    rysuj();
  });
  return b;
}

function ekranAI() {
  const f = document.createDocumentFragment();
  const a = D.ai || { poziom: 'wylaczony', zablokowane: [] };
  const wlaczony = a.poziom !== 'wylaczony';
  f.appendChild(naglowek('Dostęp dla AI', 'ustawienia'));
  f.appendChild(el('div', 'opis', 'Claude i inne AI mogą korzystać z LivePL jak z dodatku w Chrome: czytać strony, a jeśli pozwolisz, także klikać i pisać. Gdy AI działa, świeci lampka „AI” na dole i ramka strony.'));
  f.appendChild(wyborAI('Wyłączony', 'AI nie widzi niczego w LivePL', 'wylaczony'));
  f.appendChild(wyborAI('Tylko czytanie', 'widzi karty, czyta strony, robi zrzuty ekranu', 'czytanie'));
  f.appendChild(wyborAI('Czytanie i działanie', 'do tego otwiera strony, klika i wpisuje tekst', 'pelny'));
  if (wlaczony) {
    const s = el('div', 'linia-bezp' + (a.polaczony ? ' ok' : ' slaby'));
    s.append(ikonaEl(a.polaczony ? 'znacznik' : 'info'), el('span', '', a.polaczony ? `Połączono: ${a.kto}` : 'Czeka, aż AI się połączy'));
    f.appendChild(s);
    f.appendChild(kreska());
    f.appendChild(el('div', 'sekcja-tytul', 'Podłącz AI'));
    f.appendChild(poz('ai', a.claudeDesktopPodlaczony ? 'Claude: podłączony (podłącz ponownie)' : 'Podłącz do aplikacji Claude', {
      akcja: 'ai-claude', wylaczona: !a.claudeDesktop
    }));
    f.appendChild(poz('kopiuj', 'Kopiuj polecenie dla Claude Code', { akcja: 'ai-kopiuj-code' }));
    f.appendChild(poz('kopiuj', 'Kopiuj ustawienia MCP dla innych AI', { akcja: 'ai-kopiuj-mcp' }));
  }
  f.appendChild(kreska());
  const zawsze = a.zablokowane && a.zablokowane.length ? `Zawsze zablokowane dla AI: ${a.zablokowane.join(', ')}. ` : '';
  f.appendChild(el('div', 'opis', zawsze + 'AI nie wpisze hasła i nie zobaczy zapisanych haseł. Połączenie działa tylko na tym komputerze.'));
  if (wlaczony) f.appendChild(poz('stop', 'Zatrzymaj AI teraz', { akcja: 'ai-zatrzymaj', czerwona: true }));
  return f;
}

// ---------------------------------------------------------------- tłumacz stron

function wybor(tekst, podpis, wlaczony, klik) {
  const b = el('button', 'poz poz-wysoka');
  b.type = 'button';
  b.setAttribute('role', 'menuitemradio');
  b.setAttribute('aria-checked', String(!!wlaczony));
  const pole = el('span', 'pole okragle' + (wlaczony ? ' wl' : ''));
  pole.appendChild(ikonaEl('znacznik'));
  const t = el('span', 'tekst');
  t.append(el('span', '', tekst), el('small', 'podpis-pozycji', podpis));
  b.append(pole, t);
  b.addEventListener('click', () => { if (!wlaczony) klik(); });
  return b;
}

function przyciskPanelu(tekst, klasa, klik) {
  const b = el('button', 'przycisk ' + klasa, tekst);
  b.type = 'button';
  b.addEventListener('click', klik);
  return b;
}

// okienko przy przycisku tłumacza (strzałka obok „Przetłumacz”)
function ekranTlumacz() {
  const f = document.createDocumentFragment();
  const t = D.tlumacz || {};
  const k = t.karta;
  const n = el('div', 'ssl-naglowek ok');
  n.append(ikonaEl('tlumacz'), el('span', '', 'Tłumacz stron'));
  f.appendChild(n);
  if (!k) {
    f.appendChild(el('div', 'opis', 'Tłumaczyć można zwykłe strony internetowe.'));
    return f;
  }
  // język strony, z możliwością poprawienia
  const w = el('div', 'wiersz-jezyk');
  w.appendChild(el('span', '', 'Język strony:'));
  const lista = el('select', 'pole-tekstu');
  lista.setAttribute('aria-label', 'Język strony');
  if (!k.jezyk || !t.jezyki.some(j => j.kod === k.jezyk)) {
    const o = el('option', '', k.jezyk === 'pl' ? 'polski' : 'wybierz…');
    o.value = '';
    lista.appendChild(o);
  }
  for (const j of t.jezyki || []) {
    const o = el('option', '', j.nazwa);
    o.value = j.kod;
    o.selected = j.kod === k.jezyk;
    lista.appendChild(o);
  }
  lista.addEventListener('change', () => { if (lista.value) P.akcja('tlumacz-jezyk', lista.value); });
  w.appendChild(lista);
  f.appendChild(w);

  const opisStanu = {
    pobieranie: `Pobieram słownik${k.pobieranie != null ? ': ' + k.pobieranie + '%' : '…'} To tylko za pierwszym razem dla tego języka.`,
    trwa: `Tłumaczę na polski${k.procent != null ? ': ' + k.procent + '%' : '…'}`,
    gotowe: 'Strona jest przetłumaczona na polski.'
  };
  if (opisStanu[k.stan]) f.appendChild(el('div', 'opis', opisStanu[k.stan]));
  if (k.stan === 'blad' && k.blad) f.appendChild(el('div', 'opis zly', k.blad));
  if (k.chroniona) f.appendChild(el('div', 'opis', 'To chroniony program LivePL: tłumaczy się tylko po kliknięciu i zawsze w tym komputerze.'));

  const p = el('div', 'przyciski');
  if (k.stan === 'gotowe' || k.stan === 'trwa') {
    p.appendChild(przyciskPanelu('Pokaż oryginał', 'czerwony', () => P.akcja('tlumacz-oryginal')));
  } else if (k.stan !== 'pobieranie') {
    const tak = przyciskPanelu(k.stan === 'blad' ? 'Spróbuj jeszcze raz' : 'Przetłumacz na polski', 'czerwony', () => P.akcja('tlumacz-strone', lista.value || undefined));
    tak.disabled = !lista.value || lista.value === 'pl';
    p.appendChild(tak);
    setTimeout(() => tak.focus(), 50);
  }
  f.appendChild(p);

  if (!k.chroniona && k.jezyk && k.jezyk !== 'pl') {
    f.appendChild(zaznaczenie(`Zawsze tłumacz z języka: ${k.nazwa}`, k.zawsze, 'tlumacz-zawsze', () => { k.zawsze = !k.zawsze; }));
  }
  if (k.host) {
    f.appendChild(zaznaczenie(`Nigdy nie proponuj na ${k.host}`, k.nigdy, 'tlumacz-nigdy', () => { k.nigdy = !k.nigdy; }));
  }
  f.appendChild(kreska());
  const s = el('div', 'linia-bezp ' + (k.silnik === 'deepl' ? 'slaby' : 'ok'));
  s.append(ikonaEl(k.silnik === 'deepl' ? 'info' : 'klodka'), el('span', '', k.silnik === 'deepl'
    ? 'Tłumaczy DeepL: tekst tej strony idzie do DeepL (UE).'
    : 'Tłumaczy ten komputer. Tekst strony nigdzie nie wychodzi.'));
  f.appendChild(s);
  f.appendChild(poz('ustawienia', 'Ustawienia tłumacza', { dalej: true, klik: () => { ekran = 'tlumacz-ustawienia'; rysuj(); } }));
  return f;
}

// ustawienia → Tłumacz stron
function ekranTlumaczUstawienia() {
  const f = document.createDocumentFragment();
  const t = D.tlumacz || { zawsze: [], nigdy: [], modele: [] };
  f.appendChild(naglowek('Tłumacz stron', 'ustawienia'));
  f.appendChild(el('div', 'opis', 'LivePL tłumaczy strony w innych językach na polski. Przycisk „Przetłumacz” pojawia się obok adresu. Działa też prawy przycisk myszy: „Przetłumacz stronę” i „Przetłumacz zaznaczenie”.'));
  f.appendChild(zaznaczenie('Proponuj tłumaczenie obcych stron', t.proponuj, 'tlumacz-proponuj', () => { t.proponuj = !t.proponuj; }));

  f.appendChild(el('div', 'sekcja-tytul', 'Kto tłumaczy'));
  f.appendChild(wybor('Ten komputer (zalecane)', 'modele Mozilli (jak w Firefoksie); tekst nigdzie nie wychodzi; słownik pobiera się raz na język, ok. 25 MB',
    t.silnik === 'lokalny', () => { t.silnik = 'lokalny'; P.akcja('tlumacz-silnik', 'lokalny'); rysuj(); }));
  f.appendChild(wybor('DeepL', 'najlepsza jakość po polsku; tekst stron idzie do DeepL SE (Niemcy, UE); potrzebny klucz API',
    t.silnik === 'deepl', () => { t.silnik = 'deepl'; P.akcja('tlumacz-silnik', 'deepl'); rysuj(); }));

  if (t.silnik === 'deepl' || t.klucz) {
    f.appendChild(el('div', 'opis', 'Z DeepL wysyłany jest tekst stron, które tłumaczysz (bez pól formularzy i haseł). Programy LivePL (poczta, panele, urząd) i tak tłumaczy ten komputer. Darmowy klucz API (500 tys. znaków miesięcznie) założysz na deepl.com/pro-api.'));
    if (t.klucz) {
      const l = el('div', 'linia');
      l.append(el('span', '', 'Klucz DeepL: '), el('b', '', t.klucz));
      f.appendChild(l);
      const p = el('div', 'przyciski');
      p.append(przyciskPanelu('Sprawdź klucz', '', () => P.akcja('tlumacz-klucz-sprawdz')),
        przyciskPanelu('Usuń klucz', 'tekstowy', () => P.akcja('tlumacz-klucz-usun')));
      f.appendChild(p);
    } else if (!t.szyfrowanie) {
      f.appendChild(el('div', 'opis zly', 'Windows nie pozwala teraz szyfrować danych, więc LivePL nie zapisze klucza.'));
    } else {
      const forma = el('form', 'dodaj-strone');
      const pole = el('input', 'pole-tekstu');
      pole.type = 'password';
      pole.placeholder = 'Klucz API DeepL';
      pole.autocomplete = 'off';
      pole.spellcheck = false;
      const zapisz = el('button', 'przycisk czerwony', 'Zapisz klucz');
      zapisz.type = 'submit';
      forma.append(pole, zapisz);
      forma.addEventListener('submit', (e) => {
        e.preventDefault();
        P.akcja('tlumacz-klucz', pole.value);
        pole.value = '';
      });
      f.appendChild(forma);
      f.appendChild(el('div', 'opis', 'Klucz zostanie zaszyfrowany przez Windows, jak zapisane hasła.'));
    }
    if (t.stanKlucza) f.appendChild(el('div', 'opis' + (t.stanKlucza.zly ? ' zly' : ''), t.stanKlucza.tekst));
    if (t.silnik === 'deepl' && !t.klucz) f.appendChild(el('div', 'opis', 'Bez klucza dalej tłumaczy ten komputer.'));
  }

  if (t.zawsze.length) {
    f.appendChild(el('div', 'sekcja-tytul', 'Zawsze tłumacz z języka'));
    for (const j of t.zawsze) f.appendChild(wierszUsun(j.nazwa, 'Nie tłumacz już automatycznie', () => P.akcja('tlumacz-zawsze-usun', j.kod)));
  }
  if (t.nigdy.length) {
    f.appendChild(el('div', 'sekcja-tytul', 'Nigdy nie proponuj na'));
    for (const h of t.nigdy) f.appendChild(wierszUsun(h, 'Usuń z wyjątków', () => P.akcja('tlumacz-nigdy-usun', h)));
  }
  f.appendChild(el('div', 'sekcja-tytul', 'Pobrane słowniki'));
  if (!t.modele.length) f.appendChild(el('div', 'opis', 'Jeszcze żadnych. Słownik pobiera się przy pierwszym tłumaczeniu z danego języka.'));
  for (const m of t.modele) {
    const l = el('div', 'linia');
    l.append(el('span', '', m.nazwa + ': '), el('b', '', m.mb + ' MB'));
    f.appendChild(l);
  }
  if (t.modele.length) f.appendChild(poz('kosz', 'Usuń pobrane słowniki', { akcja: 'tlumacz-modele-usun' }));
  return f;
}

function wierszUsun(tekst, podpowiedz, klik) {
  const r = el('div', 'wiersz strona-wiersz');
  const opis = el('div', 'strona-opis');
  opis.appendChild(el('span', '', tekst));
  const usun = el('button', 'maly usun-strone');
  usun.type = 'button';
  usun.title = podpowiedz;
  usun.appendChild(ikonaEl('zamknij'));
  usun.addEventListener('click', klik);
  r.append(opis, usun);
  return r;
}

// prawy przycisk → „Przetłumacz zaznaczenie”: wynik w okienku obok, strona bez zmian
function ekranTlumaczenie() {
  const f = document.createDocumentFragment();
  const t = D.tlumaczenie;
  if (!t) return f;
  const n = el('div', 'ssl-naglowek ok');
  n.append(ikonaEl('tlumacz'), el('span', '', t.zrodlo && t.zrodlo !== 'pl' ? `Tłumaczenie z języka: ${t.nazwa}` : 'Tłumaczenie'));
  f.appendChild(n);
  if (t.trwa) f.appendChild(el('div', 'opis', t.pobieranie != null ? `Pobieram słownik: ${t.pobieranie}% (tylko za pierwszym razem)` : 'Tłumaczę…'));
  if (t.blad) f.appendChild(el('div', 'opis zly', t.blad));
  if (t.wynik) {
    const w = el('div', 'tlumaczenie-tekst', t.wynik);
    f.appendChild(w);
  }
  const p = el('div', 'przyciski');
  if (t.wynik) {
    const kopiuj = przyciskPanelu('Kopiuj', 'czerwony', () => P.akcja('tlumacz-kopiuj'));
    p.appendChild(kopiuj);
    setTimeout(() => kopiuj.focus(), 50);
  }
  p.appendChild(przyciskPanelu('Zamknij', 'tekstowy', () => P.akcja('tlumacz-zamknij')));
  f.appendChild(p);
  f.appendChild(el('div', 'opis slaby-maly', t.silnik === 'deepl' ? 'Przetłumaczył DeepL.' : 'Przetłumaczył ten komputer.'));
  return f;
}

// prawy klik na programie albo przypiętej stronie na pasku bocznym
function ekranProgram() {
  const f = document.createDocumentFragment();
  const p = D.programMenu;
  if (!p) return f;
  f.appendChild(el('div', 'opis', p.nazwa));
  f.appendChild(poz('zewnetrzna', 'Otwórz w zwykłej przeglądarce', { akcja: 'program-zewnetrzna', arg: p.id }));
  f.appendChild(poz('kopiuj', 'Kopiuj adres', { akcja: 'program-kopiuj', arg: p.id }));
  f.appendChild(poz('olowek', p.podpis ? `Podpis pod ikoną: „${p.podpis}”` : 'Dodaj podpis pod ikoną', { dalej: true, klik: () => { ekran = 'podpis-ikony'; rysuj(); } }));
  f.appendChild(kreska());
  if (p.wlasny) {
    f.appendChild(poz('odepnij', 'Odepnij z paska', { akcja: 'program-odepnij', arg: p.id, czerwona: true }));
  } else {
    f.appendChild(poz('pinezka', 'Moje strony na pasku', { dalej: true, klik: () => { ekran = 'strony'; rysuj(); } }));
  }
  return f;
}

// podpis pod ikoną na pasku bocznym (np. „Redakcja”), gdy same ikony nie wystarczają
function ekranPodpisIkony() {
  const f = document.createDocumentFragment();
  const p = D.programMenu;
  if (!p) return f;
  f.appendChild(naglowek('Podpis pod ikoną', 'program'));
  f.appendChild(el('div', 'opis', `${p.nazwa}: krótki napis pod ikoną na pasku po lewej, do 14 znaków. Puste pole to ikona bez podpisu.`));
  const forma = el('form', 'dodaj-strone');
  const pole = el('input', 'pole-tekstu');
  pole.value = p.podpis || '';
  pole.maxLength = 14;
  pole.placeholder = 'np. Redakcja';
  pole.setAttribute('aria-label', 'Podpis pod ikoną');
  const przyciski = el('div', 'przyciski-podpisu');
  const zapisz = el('button', 'przycisk czerwony', 'Zapisz'); zapisz.type = 'submit';
  przyciski.appendChild(zapisz);
  if (p.podpis) {
    const usun = el('button', 'przycisk tekstowy', 'Usuń podpis'); usun.type = 'button';
    usun.addEventListener('click', () => P.akcja('program-podpis', { id: p.id, podpis: '' }));
    przyciski.appendChild(usun);
  }
  forma.append(pole, przyciski);
  forma.addEventListener('submit', (e) => {
    e.preventDefault();
    P.akcja('program-podpis', { id: p.id, podpis: pole.value });
  });
  f.appendChild(forma);
  setTimeout(() => { pole.focus(); pole.select(); }, 0);
  return f;
}

// własne strony na pasku: dowolny adres, zmiana nazwy, usuwanie
function ekranStrony() {
  const f = document.createDocumentFragment();
  f.appendChild(naglowek('Moje strony na pasku', 'ustawienia'));
  const lista = D.wlasneStrony || [];
  if (!lista.length) {
    f.appendChild(el('div', 'opis', 'Dodaj dowolną stronę: wpisz jej adres poniżej albo otwórz ją i kliknij „Dodaj” u góry, obok adresu.'));
  }
  for (const s of lista) {
    const w = el('div', 'wiersz strona-wiersz');
    const ik = el('span', 'ikonka-wiersza');
    if (s.ikonka) {
      const img = new Image();
      img.alt = '';
      img.src = s.ikonka;
      img.onerror = () => { ik.replaceChildren(ikonaEl('strona')); };
      ik.appendChild(img);
    } else {
      ik.appendChild(ikonaEl('strona'));
    }
    const opis = el('div', 'strona-opis');
    const nazwa = el('input', 'pole-tekstu pole-nazwa');
    nazwa.value = s.nazwa;
    nazwa.maxLength = 40;
    nazwa.title = 'Nazwa na pasku (Enter zapisuje)';
    const zapisz = () => { if (nazwa.value.trim() && nazwa.value.trim() !== s.nazwa) P.akcja('strona-nazwa', { id: s.id, nazwa: nazwa.value }); };
    nazwa.addEventListener('change', zapisz);
    nazwa.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); nazwa.blur(); } });
    opis.append(nazwa, el('div', 'strona-adres', s.adres));
    const usun = el('button', 'maly usun-strone'); usun.type = 'button'; usun.title = 'Odepnij z paska';
    usun.appendChild(ikonaEl('kosz'));
    usun.addEventListener('click', () => P.akcja('strona-usun', s.id));
    w.append(ik, opis, usun);
    f.appendChild(w);
  }
  f.appendChild(kreska());
  const forma = el('form', 'dodaj-strone');
  const adres = el('input', 'pole-tekstu'); adres.placeholder = 'Adres, np. example.com'; adres.required = true; adres.inputMode = 'url';
  const nazwaNowej = el('input', 'pole-tekstu'); nazwaNowej.placeholder = 'Nazwa (opcjonalnie)'; nazwaNowej.maxLength = 40;
  const dodaj = el('button', 'przycisk czerwony', 'Dodaj do paska'); dodaj.type = 'submit';
  forma.append(adres, nazwaNowej, dodaj);
  forma.addEventListener('submit', (e) => {
    e.preventDefault();
    P.akcja('strona-dodaj', { adres: adres.value, nazwa: nazwaNowej.value });
    adres.value = '';
    nazwaNowej.value = '';
  });
  f.appendChild(forma);
  if (D.bladStrony) f.appendChild(el('div', 'opis blad-strony', D.bladStrony));
  if (D.moznaPrzypiac) f.appendChild(poz('pinezka', 'Dodaj bieżącą stronę', { akcja: 'przypnij' }));
  f.appendChild(poz('lista', 'Edytuj listę programów LivePL (zaawansowane)', { akcja: 'programy' }));
  return f;
}

function rysuj() {
  if (!D) return;
  const ekrany = {
    menu: ekranMenu,
    ustawienia: ekranUstawienia,
    wtyczki: ekranWtyczki,
    rozszerzenia: ekranRozszerzenia,
    dodatki: ekranDodatki,
    wyloguj: ekranWyloguj,
    'o-programie': ekranOProgramie,
    pobrane: ekranPobrane,
    ssl: ekranSsl,
    'cert-osobisty': ekranCertOsobisty,
    zaufanie: ekranZaufanie,
    urzadzenie: ekranUrzadzenie,
    aktualizacja: ekranAktualizacja,
    program: ekranProgram,
    'podpis-ikony': ekranPodpisIkony,
    strony: ekranStrony,
    haslo: ekranHaslo,
    certyfikaty: ekranCertyfikaty,
    'certyfikat-win': ekranCertyfikatWin,
    'hasla-wybor': ekranHaslaWybor,
    hasla: ekranHasla,
    wyjatki: ekranWyjatki,
    dns: ekranDns,
    podpis: ekranPodpis,
    karta: ekranKarta,
    ai: ekranAI,
    tlumacz: ekranTlumacz,
    'tlumacz-ustawienia': ekranTlumaczUstawienia,
    tlumaczenie: ekranTlumaczenie
  };
  panelEl.replaceChildren((ekrany[ekran] || ekranMenu)());
}

document.addEventListener('contextmenu', (e) => e.preventDefault());
