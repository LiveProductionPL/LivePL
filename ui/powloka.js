'use strict';

// Pasek LivePL w układzie „Reżyserka”: kanały po lewej, na dole nawigacja,
// pasek wiadomości, pole adresu (Ctrl+K), pobrane pliki, menu i zegar.

const api = window.livepl;
const $ = (s) => document.querySelector(s);

let S = null;
let ostatniaNakladka = -1;

$('#nowa').innerHTML = ikona('plus');
$('#wstecz').innerHTML = ikona('wstecz');
$('#odswiez').innerHTML = ikona('odswiez');
$('#pobrane').innerHTML = ikona('pobrane');
$('#przypnij').innerHTML = ikona('plus') + '<span>Dodaj</span>';
$('#przypnij').addEventListener('click', () => api.wyslij('przypnij'));
$('#menu').innerHTML = ikona('menu');
$('#adres-ikona').innerHTML = ikona('szukaj');
$('#szukaj-ikona').innerHTML = ikona('szukaj');
$('#szukaj-wstecz').innerHTML = ikona('gora');
$('#szukaj-dalej').innerHTML = ikona('dol');
$('#szukaj-zamknij').innerHTML = ikona('zamknij');

// ---------------------------------------------------------------- kanały (programy)

const elementyProgramow = new Map();

// ikona programu: nasze mają swoje rysunki, przypięte strony ikonkę strony (albo globus)
function ustawIkoneProgramu(el, p) {
  const klucz = p.ikonka ? 'img:' + p.ikonka + (p.szeroki ? ':s' : '') : 'ik:' + p.ikona;
  if (el.dataset.ikona === klucz) return;
  el.dataset.ikona = klucz;
  const stara = el.querySelector('.ik');
  let nowa;
  if (p.ikonka) {
    nowa = new Image();
    nowa.className = 'ik ikonka-strony' + (p.szeroki ? ' szeroka' : '');
    nowa.alt = '';
    nowa.draggable = false;
    nowa.onerror = () => { el.dataset.ikona = ''; ustawIkoneProgramu(el, { ...p, ikonka: '' }); };
    nowa.src = p.ikonka;
  } else {
    const t = document.createElement('template');
    t.innerHTML = ikona(p.ikona);
    nowa = t.content.firstElementChild;
  }
  if (stara) stara.replaceWith(nowa);
  else el.prepend(nowa);
}

function rysujProgramy() {
  const nav = $('#programy');
  const obecne = new Set(S.programy.map((p) => p.id));
  for (const [id, el] of elementyProgramow) {
    if (!obecne.has(id)) {
      el.remove();
      elementyProgramow.delete(id);
    }
  }
  S.programy.forEach((p, idx) => {
    let el = elementyProgramow.get(p.id);
    if (!el) {
      el = document.createElement('button');
      el.type = 'button';
      el.className = 'program';
      el.addEventListener('click', () => api.wyslij('program', p.id));
      el.addEventListener('contextmenu', (e) => {
        e.preventDefault();
        api.wyslij('menu-programu', p.id, Math.round(e.clientX), Math.round(e.clientY));
      });
      elementyProgramow.set(p.id, el);
    }
    if (nav.children[idx] !== el) nav.insertBefore(el, nav.children[idx] || null);
    ustawIkoneProgramu(el, p);
    el.classList.toggle('wlasny', !!p.wlasny);
    // bez podpisów pod ikonami: nazwa jest w podpowiedzi i dla czytników ekranu
    el.title = p.nazwa + (idx < 8 ? ` (Ctrl+${idx + 1})` : '');
    el.setAttribute('aria-label', p.nazwa);
    el.classList.toggle('aktywny', !!S.aktywna && S.aktywna.id === p.id);
    el.classList.toggle('otwarty', p.otwarty);
    el.classList.toggle('laduje', p.laduje && !p.licznik);

    let licznik = el.querySelector('.licznik');
    if (p.licznik > 0) {
      if (!licznik) {
        licznik = document.createElement('span');
        licznik.className = 'licznik';
        el.appendChild(licznik);
      }
      licznik.textContent = p.licznik > 99 ? '99+' : String(p.licznik);
    } else if (licznik) {
      licznik.remove();
    }

    let kropka = el.querySelector('.kropka');
    if (p.blad && !kropka) {
      kropka = document.createElement('span');
      kropka.className = 'kropka';
      kropka.title = 'Strona się nie wczytała';
      el.appendChild(kropka);
    } else if (!p.blad && kropka) {
      kropka.remove();
    }
  });
}

// ---------------------------------------------------------------- dodatkowe karty

const elementyKart = new Map();

function rysujKarty() {
  const nav = $('#karty');
  const obecne = new Set(S.karty.map((k) => k.id));
  for (const [id, el] of elementyKart) {
    if (!obecne.has(id)) {
      el.remove();
      elementyKart.delete(id);
    }
  }
  S.karty.forEach((k, i) => {
    let el = elementyKart.get(k.id);
    if (!el) {
      el = document.createElement('button');
      el.type = 'button';
      el.className = 'karta';
      el.innerHTML = '<span class="ikonka"></span><span class="x" title="Zamknij kartę (Ctrl+W)">' + ikona('zamknij') + '</span>';
      el.addEventListener('click', (e) => {
        if (e.target.closest('.x')) api.wyslij('zamknij', k.id);
        else api.wyslij('karta', k.id);
      });
      el.addEventListener('auxclick', (e) => { if (e.button === 1) api.wyslij('zamknij', k.id); });
      el.addEventListener('mousedown', (e) => { if (e.button === 1) e.preventDefault(); });
      el.addEventListener('contextmenu', (e) => {
        e.preventDefault();
        api.wyslij('menu-karty', k.id, Math.round(e.clientX), Math.round(e.clientY));
      });
      elementyKart.set(k.id, el);
    }
    if (nav.children[i] !== el) nav.insertBefore(el, nav.children[i] || null);
    el.title = k.odAI ? `${k.tytul} (otwarta przez AI)` : k.tytul;
    el.classList.toggle('od-ai', !!k.odAI);
    el.classList.toggle('aktywna', !!S.aktywna && S.aktywna.id === k.id);
    el.classList.toggle('laduje', k.laduje);
    const miejsce = el.querySelector('.ikonka');
    const zrodlo = k.ikonka || '';
    if (miejsce.dataset.zrodlo !== zrodlo) {
      miejsce.dataset.zrodlo = zrodlo;
      if (zrodlo) {
        const img = new Image();
        img.alt = '';
        img.draggable = false;
        img.onerror = () => { miejsce.innerHTML = ikona('strona'); };
        img.src = zrodlo;
        miejsce.replaceChildren(img);
      } else {
        miejsce.innerHTML = ikona('strona');
      }
    }
  });
  $('#kreska').hidden = S.karty.length === 0;
}

// ---------------------------------------------------------------- pasek na dole

const forma = $('#adres-forma');
const pole = $('#adres');

function ladnyAdres(url) {
  try {
    const u = new URL(url);
    if (!/^https?:$/.test(u.protocol)) return url;
    let reszta = u.pathname + u.search;
    if (reszta === '/') reszta = '';
    try { reszta = decodeURI(reszta); } catch { /* zostaje zakodowane */ }
    return u.host.replace(/^www\./, '') + reszta;
  } catch {
    return url;
  }
}

function rysujDol() {
  const a = S.aktywna;
  $('#przypnij').hidden = !(a && a.przypinalna);
  $('#wstecz').disabled = !a || !a.wstecz;

  const odswiez = $('#odswiez');
  const laduje = !!(a && a.laduje);
  if (odswiez.dataset.tryb !== String(laduje)) {
    odswiez.dataset.tryb = String(laduje);
    odswiez.innerHTML = ikona(laduje ? 'stop' : 'odswiez');
    odswiez.title = laduje ? 'Zatrzymaj' : 'Odśwież (F5)';
    odswiez.setAttribute('aria-label', laduje ? 'Zatrzymaj' : 'Odśwież');
  }
  $('#ladowanie').classList.toggle('tak', laduje);

  // gdzie jesteśmy
  let nazwa = '';
  let adres = '';
  if (a) {
    const p = a.program ? S.programy.find((x) => x.id === a.id) : null;
    nazwa = p ? p.nazwa : (a.adres ? (a.tytul || 'Strona') : 'Nowa karta');
    adres = a.blad ? 'strona się nie wczytała' : ladnyAdres(a.adres || '');
  }
  $('#miejsce-nazwa').textContent = nazwa;
  $('#miejsce-adres').textContent = adres;
  $('#miejsce').title = a && a.adres ? a.adres : '';
  rysujSsl(a ? a.ssl : null);
  rysujAI();

  rysujWiadomosci();

  const zoom = $('#zoom');
  zoom.hidden = !a || a.zoom === 100;
  if (a) zoom.textContent = a.zoom + '%';

  const menu = $('#menu');
  menu.classList.toggle('nowe', !!S.aktualizacjaGotowa);
  menu.title = S.aktualizacjaGotowa ? 'Menu: jest nowa wersja LivePL (O programie)' : 'Menu';

  const pob = $('#pobrane');
  pob.classList.toggle('trwa', S.pobieranie.trwa > 0);
  pob.style.setProperty('--postep', String(S.pobieranie.procent || 0));

  if (document.activeElement === pole && a && !pole.dataset.edytowane) {
    pole.value = a.adres || '';
  }

  document.title = a && a.tytul ? `${a.tytul} – LivePL` : 'LivePL';
}

// wyraźna informacja, czy strona ma aktywny certyfikat SSL
function rysujSsl(ssl) {
  const b = $('#ssl');
  b.hidden = !ssl;
  if (!ssl) return;
  let klasa = 'ok';
  let ik = 'klodka';
  let tekst = 'SSL aktywny';
  let opis = 'Połączenie szyfrowane. Kliknij, żeby zobaczyć certyfikat.';
  const dni = ssl.waznyDo ? Math.floor((ssl.waznyDo - Date.now()) / 86400000) : null;
  if (ssl.stan === 'brak') {
    klasa = 'zly'; ik = 'klodka-otwarta'; tekst = 'Brak SSL';
    opis = 'Połączenie nieszyfrowane. Nie wpisuj tu haseł.';
  } else if (ssl.stan === 'zly') {
    klasa = 'zly'; ik = 'uwaga'; tekst = 'Certyfikat nieprawidłowy';
    opis = 'Certyfikat strony jest nieprawidłowy.';
  } else if (dni !== null && dni < 14) {
    klasa = 'uwaga'; tekst = dni <= 0 ? 'SSL wygasa dziś' : `SSL wygasa za ${dni} ${dni === 1 ? 'dzień' : 'dni'}`;
  }
  if (b.dataset.klucz !== klasa + tekst) {
    b.dataset.klucz = klasa + tekst;
    b.className = klasa;
    b.innerHTML = ikona(ik) + '<span></span>';
    b.querySelector('span').textContent = klasa === 'ok' ? '' : tekst;
    b.querySelector('span').hidden = klasa === 'ok';
  }
  b.title = klasa === 'ok' ? 'SSL aktywny. ' + opis : opis;
  b.setAttribute('aria-label', klasa === 'ok' ? 'SSL aktywny' : tekst);
}

function rysujWiadomosci() {
  const box = $('#wiadomosci');
  const lista = S.wiadomosci || [];
  const klucz = JSON.stringify(lista) + '|' + (S.aktywna ? S.aktywna.tytul : '');
  if (box.dataset.klucz === klucz) return;
  box.dataset.klucz = klucz;
  box.replaceChildren();
  if (!lista.length) {
    const c = document.createElement('span');
    c.className = 'cisza';
    c.textContent = S.aktywna && S.aktywna.tytul ? S.aktywna.tytul : '';
    box.appendChild(c);
    return;
  }
  for (const w of lista) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'wiad' + (w.nowa ? ' nowa' : '');
    b.title = w.tekst;
    const kropka = document.createElement('span');
    kropka.className = 'kropka-wiad';
    const czas = document.createElement('span');
    czas.className = 'czas';
    czas.textContent = w.czas;
    const t = document.createElement('span');
    t.textContent = w.tekst;
    const x = document.createElement('span');
    x.className = 'usun-wiad';
    x.setAttribute('role', 'button');
    x.setAttribute('aria-label', 'Usuń z paska');
    x.title = 'Usuń z paska';
    x.innerHTML = window.ikona('zamknij');
    b.append(kropka, czas, t, x);
    b.addEventListener('click', (e) => {
      if (e.target.closest('.usun-wiad')) api.wyslij('wiadomosc-usun', w.id);
      else api.wyslij('wiadomosc', w.id);
    });
    box.appendChild(b);
  }
}

// pole adresu
forma.addEventListener('submit', (e) => {
  e.preventDefault();
  const tekst = pole.value.trim();
  if (!tekst) return;
  api.wyslij('idz', tekst, false);
  pole.blur();
});

pole.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && e.altKey) {
    e.preventDefault();
    api.wyslij('idz', pole.value.trim(), true);
    pole.blur();
  } else if (e.key === 'Escape') {
    e.preventDefault();
    pole.blur();
    api.wyslij('oddaj-fokus');
  }
});

pole.addEventListener('input', () => { pole.dataset.edytowane = '1'; });

pole.addEventListener('focus', () => {
  if (!pole.dataset.edytowane && S && S.aktywna) pole.value = S.aktywna.adres || '';
  requestAnimationFrame(() => pole.select());
});

pole.addEventListener('blur', () => {
  pole.value = '';
  delete pole.dataset.edytowane;
});

api.na('fokus-adres', (adres) => {
  delete pole.dataset.edytowane;
  pole.value = typeof adres === 'string' ? adres : (S && S.aktywna ? S.aktywna.adres || '' : '');
  pole.focus();
  pole.select();
});

$('#wstecz').addEventListener('click', () => api.wyslij('wstecz'));
$('#odswiez').addEventListener('click', () => {
  api.wyslij(S && S.aktywna && S.aktywna.laduje ? 'zatrzymaj' : 'odswiez');
});
$('#nowa').addEventListener('click', () => api.wyslij('nowa'));
$('#zoom').addEventListener('click', () => api.wyslij('powiekszenie', 0));

// panel otwiera się pod przyciskiem (górny pasek), wyrównany do jego prawej krawędzi
function podPrzyciskiem(el) {
  const r = el.getBoundingClientRect();
  return [Math.round(r.right), Math.round(r.bottom)];
}

// lampka „AI” jak „na antenie” w studiu: przygaszona, gdy dostęp jest włączony, czerwona, gdy AI działa;
// ramka strony świeci, gdy AI działa na karcie, którą właśnie widać
function rysujAI() {
  const ai = S.ai || { poziom: 'wylaczony' };
  const b = $('#ai');
  const wlaczony = ai.poziom && ai.poziom !== 'wylaczony';
  b.hidden = !wlaczony;
  b.classList.toggle('dziala', !!ai.dziala);
  $('#ai-opis').textContent = ai.dziala ? `${ai.kto} ${ai.opis}` : '';
  const rodzaj = ai.poziom === 'pelny' ? 'czytanie i działanie' : 'tylko czytanie';
  b.title = ai.dziala
    ? `${ai.kto} ${ai.opis}. Kliknij, żeby zobaczyć albo zatrzymać`
    : `Dostęp dla AI włączony (${rodzaj}). Kliknij, żeby zmienić albo wyłączyć`;
  b.setAttribute('aria-label', b.title);
  $('#ramka-strony').classList.toggle('ai', !!ai.dziala && !!S.aktywna && ai.kartaId === S.aktywna.id);
}

$('#ai').addEventListener('click', (e) => {
  const r = e.currentTarget.getBoundingClientRect();
  api.wyslij('ai', Math.round(r.right), Math.round(r.top));
});

$('#ssl').addEventListener('click', (e) => {
  const r = e.currentTarget.getBoundingClientRect();
  api.wyslij('ssl', Math.round(r.left), Math.round(r.top));
});

$('#menu').addEventListener('click', (e) => api.wyslij('menu', ...podPrzyciskiem(e.currentTarget)));
$('#pobrane').addEventListener('click', (e) => {
  e.currentTarget.classList.remove('nowe');
  api.wyslij('pobrane', ...podPrzyciskiem(e.currentTarget));
});

// ---------------------------------------------------------------- szukanie na stronie

const dol = $('#dol');
const szukajBox = $('#szukaj');
const szukajPole = $('#szukaj-tekst');
const szukajWynik = $('#szukaj-wynik');

function otworzSzukanie(opcje) {
  const bylo = !szukajBox.hidden;
  szukajBox.hidden = false;
  dol.classList.add('szuka');
  szukajPole.focus();
  szukajPole.select();
  if (opcje && opcje.dalej && szukajPole.value) {
    api.wyslij('szukaj', szukajPole.value, { wstecz: !!opcje.wstecz, nowe: !bylo });
  }
}

function zamknijSzukanie(oddajFokus) {
  if (szukajBox.hidden) return;
  szukajBox.hidden = true;
  dol.classList.remove('szuka');
  szukajWynik.textContent = '';
  if (oddajFokus) api.wyslij('szukaj-koniec');
}

szukajPole.addEventListener('input', () => {
  api.wyslij('szukaj', szukajPole.value, { nowe: true });
  if (!szukajPole.value) szukajWynik.textContent = '';
});

szukajPole.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    if (szukajPole.value) api.wyslij('szukaj', szukajPole.value, { wstecz: e.shiftKey, nowe: false });
  } else if (e.key === 'Escape') {
    e.preventDefault();
    zamknijSzukanie(true);
  }
});

$('#szukaj-dalej').addEventListener('click', () => { if (szukajPole.value) api.wyslij('szukaj', szukajPole.value, { nowe: false }); });
$('#szukaj-wstecz').addEventListener('click', () => { if (szukajPole.value) api.wyslij('szukaj', szukajPole.value, { wstecz: true, nowe: false }); });
$('#szukaj-zamknij').addEventListener('click', () => zamknijSzukanie(true));

api.na('szukaj-otworz', otworzSzukanie);
api.na('szukaj-zamknij', () => zamknijSzukanie(false));
api.na('szukaj-wynik', (w) => {
  if (!szukajPole.value) { szukajWynik.textContent = ''; return; }
  szukajWynik.textContent = w.wszystkie ? `${w.biezacy} z ${w.wszystkie}` : 'brak';
  szukajWynik.classList.toggle('brak', !w.wszystkie);
});

api.na('pobrano', (p) => {
  if (!p.blad) $('#pobrane').classList.add('nowe');
});

// ---------------------------------------------------------------- zegar

function zegar() {
  const d = new Date();
  $('#zegar').textContent = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
}
zegar();
setInterval(zegar, 10000);

// ---------------------------------------------------------------- tryb dzień i noc (przy zegarze)
// klik: auto → dzień → noc → auto; LivePL zapamiętuje wybór

const MOTYWY = {
  auto: { ikona: 'automat', nazwa: 'automatycznie, jak Windows', dalej: 'dzien' },
  dzien: { ikona: 'slonce', nazwa: 'dzień', dalej: 'noc' },
  noc: { ikona: 'ksiezyc', nazwa: 'noc', dalej: 'auto' }
};
const przyciskMotywu = $('#motyw');
let narysowanyMotyw = '';

function rysujMotyw() {
  const m = MOTYWY[S && S.motyw] ? S.motyw : 'auto';
  if (m === narysowanyMotyw) return;
  narysowanyMotyw = m;
  przyciskMotywu.innerHTML = ikona(MOTYWY[m].ikona);
  przyciskMotywu.title = 'Tryb: ' + MOTYWY[m].nazwa + '. Kliknij: ' + MOTYWY[MOTYWY[m].dalej].nazwa;
  przyciskMotywu.dataset.motyw = m;
}

przyciskMotywu.addEventListener('click', () => {
  const m = MOTYWY[S && S.motyw] ? S.motyw : 'auto';
  api.wyslij('motyw', MOTYWY[m].dalej);
});

// ---------------------------------------------------------------- liczba nieprzeczytanych na pasku zadań

function rysujNakladke() {
  const n = S.nieprzeczytane || 0;
  if (n === ostatniaNakladka) return;
  ostatniaNakladka = n;
  if (!n) { api.wyslij('nakladka', null); return; }
  const c = document.createElement('canvas');
  c.width = 32;
  c.height = 32;
  const g = c.getContext('2d');
  g.fillStyle = '#b0261f';
  g.beginPath();
  g.roundRect(1, 1, 30, 30, 7);
  g.fill();
  const tekst = n > 99 ? '99+' : String(n);
  g.fillStyle = '#fff';
  g.font = `bold ${tekst.length >= 3 ? 13 : tekst.length === 2 ? 17 : 20}px "Segoe UI", sans-serif`;
  g.textAlign = 'center';
  g.textBaseline = 'middle';
  g.fillText(tekst, 16, 17);
  api.wyslij('nakladka', c.toDataURL('image/png'), `Nieprzeczytane: ${n}`);
}

// ---------------------------------------------------------------- start

api.na('stan', (s) => {
  S = s;
  rysujProgramy();
  rysujKarty();
  rysujDol();
  rysujNakladke();
  rysujMotyw();
});

document.addEventListener('contextmenu', (e) => {
  if (!e.target.closest('input')) e.preventDefault();
});

api.wyslij('gotowa');
