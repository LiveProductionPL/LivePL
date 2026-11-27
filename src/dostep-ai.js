'use strict';

// Dostęp dla AI: LivePL jako „dodatek” dla Claude i innych AI, tak jak Claude w Chrome czy Edge.
// Aplikacja AI uruchamia most MCP (ai-most.js), a most łączy się z tym modułem przez lokalny
// potok Windows (named pipe) z jednorazowym kluczem z pliku dostep-ai.json w danych LivePL.
// Nic nie nasłuchuje w sieci.
//
// Poziomy (ustawienia.ai):
//   'wylaczony' – domyślnie; potok zamknięty, AI nic nie widzi,
//   'czytanie'  – AI widzi listę kart, czyta strony, przewija i robi zrzuty ekranu,
//   'pelny'     – do tego otwiera strony, klika, wpisuje tekst i naciska klawisze.
// Zawsze:
//   - programy z "dlaAI": false w programy.json (panel serwera, urząd certyfikacji) są dla AI zamknięte,
//   - AI nie wpisze nic w pole hasła i nie dostanie wartości pól haseł,
//   - każda akcja zapala lampkę „AI” na pasku i ramkę strony; „Zatrzymaj” wyłącza dostęp od razu.

const net = require('net');
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { NARZEDZIA } = require('./ai-narzedzia');

const SWIAT = 1771; // osobny świat JavaScriptu: strona nie widzi naszego kodu
const POZIOMY = { wylaczony: 0, czytanie: 1, pelny: 2 };
const LAMPKA_MS = 4000;
const MAKS_ZNAKOW = 20000;

let L = null; // funkcje LivePL z main.js
let serwer = null;
let token = null;
const polaczenia = new Set();
let ostatnio = 0;
let opisAkcji = '';
let kartaAkcji = null;
let klient = '';
let gaszenie = null;
let ostatniKomunikat = 0;

const spij = (ms) => new Promise(r => setTimeout(r, ms));

// ---------------------------------------------------------------- włączanie i wyłączanie

function poziom() {
  return POZIOMY[L.ustawienia().ai] || 0;
}

function plikDostepu() {
  return path.join(L.userData(), 'dostep-ai.json');
}

function nazwaRury() {
  // macOS: gniazdo w danych LivePL (tylko dla tego użytkownika); Windows: potok o nazwie z folderu danych
  if (process.platform !== 'win32') return path.join(L.userData(), 'dostep-ai.sock');
  const skrot = crypto.createHash('sha256').update(L.userData().toLowerCase()).digest('hex').slice(0, 16);
  return '\\\\.\\pipe\\livepl-ai-' + skrot;
}

function usunPlik() {
  try { fs.rmSync(plikDostepu(), { force: true }); } catch { /* nie było */ }
}

function wlacz() {
  if (serwer) return;
  token = crypto.randomBytes(32).toString('base64url');
  serwer = net.createServer(polaczono);
  serwer.on('error', (e) => {
    console.error('Dostęp dla AI:', e.message);
    serwer = null;
    token = null;
    usunPlik();
  });
  // stare gniazdo po awarii blokowałoby listen (jeden LivePL naraz, więc nikt go nie używa)
  if (process.platform !== 'win32') try { if (fs.statSync(nazwaRury()).isSocket()) fs.rmSync(nazwaRury()); } catch { /* nie było */ }
  serwer.listen(nazwaRury(), () => {
    try {
      fs.writeFileSync(plikDostepu(), JSON.stringify({ rura: nazwaRury(), token, wersja: 1 }), { mode: 0o600 });
    } catch (e) {
      console.error('Dostęp dla AI: nie udało się zapisać klucza:', e.message);
    }
  });
}

function wylacz() {
  usunPlik();
  token = null;
  for (const s of polaczenia) s.destroy();
  polaczenia.clear();
  if (serwer) { serwer.close(); serwer = null; }
  ostatnio = 0;
  L.wyslijStan();
}

function ustawPoziom(nowy) {
  if (!(nowy in POZIOMY)) return;
  const byl = poziom();
  L.ustawienia().ai = nowy;
  L.zapiszUstawienia();
  if (POZIOMY[nowy]) wlacz(); else wylacz();
  if (byl && !POZIOMY[nowy]) L.dodajWiadomosc('Dostęp dla AI wyłączony');
  if (!byl && POZIOMY[nowy]) L.dodajWiadomosc(nowy === 'pelny' ? 'Dostęp dla AI włączony: czytanie i działanie' : 'Dostęp dla AI włączony: tylko czytanie');
  L.wyslijStan();
}

function uruchom(funkcje) {
  L = funkcje;
  if (poziom()) wlacz(); else usunPlik();
}

// ---------------------------------------------------------------- potok

function rowne(a, b) {
  const x = Buffer.from(String(a || ''));
  const y = Buffer.from(String(b || ''));
  return x.length === y.length && x.length > 0 && crypto.timingSafeEqual(x, y);
}

function polaczono(s) {
  polaczenia.add(s);
  s.setEncoding('utf8');
  let bufor = '';
  let zalogowany = false;
  let kolejka = Promise.resolve();
  const koniec = () => polaczenia.delete(s);
  s.on('close', koniec);
  s.on('error', koniec);
  s.on('data', (d) => {
    bufor += d;
    if (bufor.length > 1024 * 1024) { s.destroy(); return; }
    let i;
    while ((i = bufor.indexOf('\n')) >= 0) {
      const linia = bufor.slice(0, i);
      bufor = bufor.slice(i + 1);
      let m;
      try { m = JSON.parse(linia); } catch { s.destroy(); return; }
      if (!zalogowany) {
        if (!token || !rowne(m.token, token)) { s.destroy(); return; }
        zalogowany = true;
        klient = String(m.klient || '').slice(0, 60);
        continue;
      }
      if (!token) { s.destroy(); return; }
      // jedno narzędzie naraz: kolejne czeka, aż poprzednie skończy
      kolejka = kolejka
        .then(() => wykonaj(String(m.narzedzie || ''), m.argumenty && typeof m.argumenty === 'object' ? m.argumenty : {}))
        .catch(e => wynikBlad(String((e && e.message) || e)))
        .then(w => { if (!s.destroyed) s.write(JSON.stringify({ id: m.id, wynik: w }) + '\n'); });
    }
  });
}

// ---------------------------------------------------------------- lampka „AI” i pasek wiadomości

function nazwaKlienta() {
  if (/claude/i.test(klient)) return 'Claude';
  if (/codex|openai|chatgpt/i.test(klient)) return 'Codex';
  if (/gemini/i.test(klient)) return 'Gemini';
  if (/copilot|vscode|visual studio code/i.test(klient)) return 'Copilot';
  return 'AI';
}

function zglos(k, opis, naPasek = false) {
  ostatnio = Date.now();
  opisAkcji = opis;
  kartaAkcji = k ? k.id : null;
  if (Date.now() - ostatniKomunikat > 10 * 60 * 1000) {
    L.dodajWiadomosc(`${nazwaKlienta()} korzysta z LivePL. Kliknij, żeby zobaczyć albo zatrzymać`, { akcja: 'ai' }, 60 * 1000);
  } else if (naPasek) {
    L.dodajWiadomosc(`${nazwaKlienta()}: ${opis}`, {}, 20 * 1000);
  }
  ostatniKomunikat = Date.now();
  L.wyslijStan();
  clearTimeout(gaszenie);
  gaszenie = setTimeout(() => L.wyslijStan(), LAMPKA_MS + 50);
}

function stan() {
  const p = L ? poziom() : 0;
  return {
    poziom: (L && L.ustawienia().ai) || 'wylaczony',
    dziala: !!p && Date.now() - ostatnio < LAMPKA_MS,
    opis: opisAkcji,
    kartaId: kartaAkcji,
    kto: nazwaKlienta()
  };
}

// ---------------------------------------------------------------- karty i blokady

const tekst = (t) => ({ content: [{ type: 'text', text: t }] });
const wynikBlad = (t) => ({ content: [{ type: 'text', text: t }], isError: true });

function originZ(url) {
  try { const u = new URL(url); return /^https?:$/.test(u.protocol) ? u.protocol + '//' + u.host : ''; } catch { return ''; }
}

function zablokowane() {
  return L.wszystkieProgramy().filter(p => p.dlaAI === false).map(p => ({ id: p.id, nazwa: p.nazwa, origin: originZ(p.adres) }));
}

function adresKarty(k) {
  if (k.blad) return k.blad.url;
  try { return k.wc.getURL() || k.adres || ''; } catch { return k.adres || ''; }
}

function czyZablokowany(url, k) {
  if (k && k.program && k.program.dlaAI === false) return true;
  const o = originZ(url);
  return !!o && zablokowane().some(z => z.origin === o);
}

function nazwaKarty(k) {
  return k.program ? k.program.nazwa : (k.nowa ? 'Nowa karta' : (k.tytul || 'Strona'));
}

function karta(id) {
  const k = id ? L.karty().get(String(id)) : L.karty().get(L.aktywnaId());
  if (!k || k.wc.isDestroyed()) throw new Error(id ? `There is no tab "${id}". Call list_tabs.` : 'No tab is open. Use open_tab.');
  if (czyZablokowany(adresKarty(k), k)) {
    throw new Error(`"${nazwaKarty(k)}" is blocked for AI in LivePL (server panel / certificate authority). Ask the user to do this part themselves.`);
  }
  return k;
}

// ostrzeżenie o certyfikacie (strona błędu LivePL) zostaje dla człowieka: AI tu nie klika i nie pisze
function bezOstrzezeniaCertyfikatu(k) {
  const kod = k.blad ? Number(k.blad.kod) : 0;
  if (kod <= -200 && kod > -300) {
    throw new Error('This tab shows a certificate warning. Only the user can decide to continue; ask them to do it themselves.');
  }
  return k;
}

function oknoWidoczne() {
  const w = L.okno();
  return !!w && !w.isDestroyed() && w.isVisible() && !w.isMinimized();
}

// klikanie i pisanie działa tylko na widocznej karcie: AI pokazuje ją użytkownikowi
function pokaz(k) {
  if (!oknoWidoczne()) throw new Error('The LivePL window is minimized or hidden in the tray. Ask the user to open LivePL, then try again.');
  if (L.aktywnaId() !== k.id) L.aktywuj(k.id);
}

function adresZ(t) {
  let a = String(t || '').trim();
  if (!a) throw new Error('Empty address.');
  if (!/^[a-z][a-z0-9+.-]*:/i.test(a)) a = 'https://' + a;
  let u;
  try { u = new URL(a); } catch { throw new Error('This is not a valid address.'); }
  if (!/^https?:$/.test(u.protocol)) throw new Error('Only http and https addresses can be opened.');
  return u.href;
}

async function poczekaj(k, ms = 10000) {
  const start = Date.now();
  await spij(350);
  while (!k.wc.isDestroyed() && k.wc.isLoading() && Date.now() - start < ms) await spij(150);
  await spij(150);
}

function gdzieJestes(k) {
  if (k.wc.isDestroyed()) return '';
  const blad = k.blad ? ' (the page did not load: error ' + k.blad.kod + ')' : '';
  return `Tab ${k.id}: "${k.wc.getTitle()}" — ${adresKarty(k)}${blad}`;
}

// ---------------------------------------------------------------- kod w stronie (osobny świat JS)

// Pomocnicze funkcje w stronie, wstawiane raz na dokument. Znaczniki ref_N trzymamy w pamięci
// naszego świata (WeakRef), bez dopisywania czegokolwiek do strony. Obejmuje ramki z tej samej domeny.
function podstawa() {
  if (globalThis.__lpai) return;
  const refy = new Map();
  const numery = new WeakMap();
  let nr = 0;
  const ref = (e) => {
    let r = numery.get(e);
    if (!r) { r = 'ref_' + (++nr); numery.set(e, r); refy.set(r, new WeakRef(e)); }
    return r;
  };
  const el = (r) => { const w = refy.get(String(r)); const e = w && w.deref(); return e && e.isConnected ? e : null; };
  const krotko = (t, n) => { t = String(t || '').replace(/\s+/g, ' ').trim(); return t.length > n ? t.slice(0, n - 1) + '…' : t; };
  const widoczny = (e) => {
    const okno = e.ownerDocument.defaultView;
    const s = okno.getComputedStyle(e);
    if (s.visibility === 'hidden' || s.display === 'none' || Number(s.opacity) === 0) return false;
    return e.getClientRects().length > 0;
  };
  // przesunięcie ramki (iframe) względem głównej strony
  const przesuniecie = (e) => {
    let x = 0, y = 0, okno = e.ownerDocument.defaultView;
    while (okno && okno.frameElement) {
      const f = okno.frameElement, r = f.getBoundingClientRect();
      x += r.left + f.clientLeft; y += r.top + f.clientTop;
      okno = f.ownerDocument.defaultView;
    }
    return { x, y };
  };
  // dokument główny i ramki z tej samej domeny (do 3 poziomów)
  const dokumenty = () => {
    const lista = [document];
    for (let i = 0; i < lista.length && lista.length < 30; i++) {
      for (const f of lista[i].querySelectorAll('iframe, frame')) {
        try { if (f.contentDocument && widoczny(f)) lista.push(f.contentDocument); } catch { /* inna domena */ }
      }
    }
    return lista;
  };
  const rola = (e) => {
    const r = e.getAttribute('role');
    if (r) return r;
    const t = e.tagName.toLowerCase();
    if (t === 'a') return e.hasAttribute('href') ? 'link' : 'text';
    if (t === 'button' || t === 'summary') return 'button';
    if (t === 'select') return 'combobox';
    if (t === 'textarea') return 'textbox';
    if (t === 'img') return 'img';
    if (/^h[1-6]$/.test(t)) return 'heading';
    if (t === 'input') {
      const ty = (e.type || 'text').toLowerCase();
      return ({ checkbox: 'checkbox', radio: 'radio', submit: 'button', button: 'button', reset: 'button', image: 'button', password: 'password', range: 'slider', file: 'file', search: 'searchbox', number: 'spinbutton' })[ty] || 'textbox';
    }
    if (e.isContentEditable) return 'textbox';
    return t;
  };
  const nazwa = (e) => {
    let n = e.getAttribute('aria-label') || '';
    if (!n && e.getAttribute('aria-labelledby')) {
      n = e.getAttribute('aria-labelledby').split(/\s+/).map(id => { const x = e.ownerDocument.getElementById(id); return x ? x.textContent : ''; }).join(' ');
    }
    if (!n && e.labels && e.labels.length) n = [...e.labels].map(l => l.innerText).join(' ');
    if (!n && e.tagName === 'IMG') n = e.alt;
    if (!n && e.tagName === 'INPUT' && ['submit', 'button', 'reset'].includes(e.type)) n = e.value;
    if (!n && !['INPUT', 'TEXTAREA', 'SELECT'].includes(e.tagName)) n = e.innerText || e.textContent;
    if (!n) n = e.getAttribute('placeholder') || e.getAttribute('title') || e.getAttribute('name') || '';
    return krotko(n, 90);
  };
  const INTER = 'a[href],button,input:not([type=hidden]),select,textarea,summary,[role=button],[role=link],[role=checkbox],[role=radio],[role=tab],[role=menuitem],[role=menuitemcheckbox],[role=option],[role=switch],[role=textbox],[role=combobox],[role=searchbox],[role=treeitem],[role=row],[contenteditable=""],[contenteditable=true],[onclick],[tabindex]:not([tabindex="-1"])';
  const haslo = (e) => !!e && e.tagName === 'INPUT' && String(e.type).toLowerCase() === 'password';
  const opisz = (e) => {
    const r = rola(e);
    let s = '[' + ref(e) + '] ' + r;
    const n = nazwa(e);
    if (n) s += ' "' + n.replace(/"/g, '\'') + '"';
    if (haslo(e)) { if (e.value) s += ' (filled)'; }
    else if (e.tagName === 'INPUT' && ['checkbox', 'radio'].includes(e.type)) { if (e.checked) s += ' checked'; }
    else if ((e.tagName === 'INPUT' || e.tagName === 'TEXTAREA') && e.value) s += ' value="' + krotko(e.value, 80).replace(/"/g, '\'') + '"';
    else if (e.tagName === 'SELECT' && e.selectedOptions[0]) s += ' selected="' + krotko(e.selectedOptions[0].textContent, 60) + '"';
    else if (e.getAttribute('aria-checked') === 'true' || e.getAttribute('aria-selected') === 'true') s += ' selected';
    if (e.disabled || e.getAttribute('aria-disabled') === 'true') s += ' disabled';
    if (r === 'link') { const h = e.getAttribute('href'); if (h && !/^javascript:/i.test(h)) s += ' -> ' + krotko(h, 80); }
    if (r === 'heading' && /^H[1-6]$/.test(e.tagName)) s += ' (h' + e.tagName[1] + ')';
    const b = e.getBoundingClientRect(), o = przesuniecie(e);
    if (b.bottom + o.y < 0 || b.top + o.y > innerHeight || b.right + o.x < 0 || b.left + o.x > innerWidth) s += ' (offscreen)';
    return s;
  };
  // najgłębszy aktywny element (także w ramkach)
  const aktywny = () => {
    let e = document.activeElement;
    try { while (e && /^I?FRAME$/.test(e.tagName) && e.contentDocument) e = e.contentDocument.activeElement; } catch { /* inna domena */ }
    return e;
  };
  globalThis.__lpai = { ref, el, krotko, widoczny, przesuniecie, dokumenty, rola, nazwa, opisz, INTER, haslo, aktywny };
}

function stronaCzytaj(o) {
  const A = globalThis.__lpai;
  const max = o.max;
  const wszystko = o.filtr === 'all';
  const sel = wszystko ? A.INTER + ',h1,h2,h3,h4,h5,h6,img[alt],p,li,td,th,label,dt,dd,blockquote,pre,figcaption' : A.INTER;
  const linie = [];
  const teksty = new Set();
  let dl = 0, przyciete = false, ile = 0;
  for (const doc of A.dokumenty()) {
    for (const e of doc.querySelectorAll(sel)) {
      if (++ile > 6000) { przyciete = true; break; }
      if (!A.widoczny(e)) continue;
      let s;
      if (e.matches(A.INTER)) s = A.opisz(e);
      else if (/^H[1-6]$/.test(e.tagName)) s = 'heading "' + A.krotko(e.innerText, 140) + '" (h' + e.tagName[1] + ')';
      else if (e.tagName === 'IMG') s = 'img "' + A.krotko(e.alt, 100) + '"';
      else {
        if (e.querySelector(A.INTER)) continue;
        const t = A.krotko(e.innerText, 240);
        if (!t || teksty.has(t)) continue;
        teksty.add(t);
        s = 'text "' + t + '"';
      }
      if (dl + s.length > max) { przyciete = true; break; }
      linie.push(s);
      dl += s.length + 1;
    }
    if (przyciete) break;
  }
  const wys = Math.max(0, document.documentElement.scrollHeight - innerHeight);
  return {
    naglowek: `Page: "${document.title}" — ${location.href}\nViewport ${innerWidth}×${innerHeight}, scrolled ${Math.round(scrollY)} of ${wys} px`,
    linie, przyciete
  };
}

function stronaTekst(o) {
  const A = globalThis.__lpai;
  const czesci = [];
  for (const doc of A.dokumenty()) {
    const glowny = doc.querySelector('main, article, [role=main]');
    const zrodlo = glowny && glowny.innerText.trim().length > 300 ? glowny : doc.body;
    if (zrodlo) czesci.push(zrodlo.innerText);
  }
  let t = czesci.join('\n\n').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
  const przyciete = t.length > o.max;
  if (przyciete) t = t.slice(0, o.max);
  return { naglowek: `Page: "${document.title}" — ${location.href}`, tekst: t, przyciete };
}

function stronaSzukaj(o) {
  const A = globalThis.__lpai;
  const q = String(o.q || '').toLowerCase();
  const wyniki = [];
  const juz = new Set();
  for (const doc of A.dokumenty()) {
    for (const e of doc.querySelectorAll(A.INTER + ',h1,h2,h3,h4,h5,h6,label,img[alt]')) {
      if (wyniki.length >= 20) break;
      if (!A.widoczny(e)) continue;
      const opis = A.opisz(e);
      const t = (opis + ' ' + (e.innerText || '')).toLowerCase();
      if (t.includes(q)) { wyniki.push(opis); juz.add(e); }
    }
  }
  // zwykły tekst (np. pozycja listy, wiersz tabeli): element, w którym ten tekst stoi
  for (const doc of A.dokumenty()) {
    const w = doc.createTreeWalker(doc.body || doc.documentElement, NodeFilter.SHOW_TEXT);
    let n;
    while ((n = w.nextNode()) && wyniki.length < 20) {
      if (!n.nodeValue.toLowerCase().includes(q)) continue;
      const e = n.parentElement;
      if (!e || juz.has(e) || /^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE)$/.test(e.tagName) || !A.widoczny(e)) continue;
      if ([...juz].some(x => x.contains(e))) continue;
      juz.add(e);
      wyniki.push('[' + A.ref(e) + '] text "' + A.krotko(e.innerText, 160) + '"');
    }
  }
  return wyniki;
}

function stronaPunkt(o) {
  const A = globalThis.__lpai;
  const e = A.el(o.ref);
  if (!e) return { blad: 'nieaktualny' };
  e.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' });
  const r = e.getBoundingClientRect(), p = A.przesuniecie(e);
  return { x: r.left + r.width / 2 + p.x, y: r.top + r.height / 2 + p.y, opis: A.opisz(e), haslo: A.haslo(e) };
}

function stronaFokus(o) {
  const A = globalThis.__lpai;
  let e;
  if (o.ref) {
    e = A.el(o.ref);
    if (!e) return { blad: 'nieaktualny' };
    if (A.haslo(e)) return { haslo: true };
    e.scrollIntoView({ block: 'center', behavior: 'instant' });
    e.focus();
  } else {
    e = A.aktywny();
  }
  if (!e || e === e.ownerDocument.body) return { blad: 'brak-pola' };
  if (A.haslo(e)) return { haslo: true };
  const edytowalne = e.isContentEditable || ['INPUT', 'TEXTAREA'].includes(e.tagName);
  if (!edytowalne) return { blad: 'nie-pole', opis: A.opisz(e) };
  if (o.wyczysc) {
    if (typeof e.select === 'function') e.select();
    else { const s = e.ownerDocument.getSelection(); s.selectAllChildren(e); }
  } else if (typeof e.setSelectionRange === 'function' && typeof e.value === 'string') {
    try { e.setSelectionRange(e.value.length, e.value.length); } catch { /* np. pole e-mail */ }
  }
  return { opis: A.opisz(e) };
}

function stronaCzyHaslo() {
  const A = globalThis.__lpai;
  return A.haslo(A.aktywny());
}

function stronaPrzewin(o) {
  const A = globalThis.__lpai;
  let cel = null;
  if (o.ref) { const e = A.el(o.ref); if (!e) return { blad: 'nieaktualny' }; cel = e; }
  else if (o.x != null && o.y != null) cel = document.elementFromPoint(o.x, o.y);
  // najbliższy przewijany kontener (np. lista wiadomości w poczcie), inaczej cała strona
  let e = cel;
  const przewijany = (x) => {
    const s = x.ownerDocument.defaultView.getComputedStyle(x);
    return (/(auto|scroll|overlay)/.test(s.overflowY) && x.scrollHeight > x.clientHeight + 4) ||
      (/(auto|scroll|overlay)/.test(s.overflowX) && x.scrollWidth > x.clientWidth + 4);
  };
  while (e && e.nodeType === 1 && !przewijany(e)) e = e.parentElement;
  const kontener = e && e.nodeType === 1 ? e : document.scrollingElement;
  const krok = (o.kierunek === 'up' || o.kierunek === 'down' ? kontener.clientHeight || innerHeight : kontener.clientWidth || innerWidth) * o.ile / 10;
  const dx = o.kierunek === 'left' ? -krok : o.kierunek === 'right' ? krok : 0;
  const dy = o.kierunek === 'up' ? -krok : o.kierunek === 'down' ? krok : 0;
  kontener.scrollBy({ left: dx, top: dy, behavior: 'instant' });
  return { y: Math.round(kontener.scrollTop), max: Math.max(0, kontener.scrollHeight - kontener.clientHeight) };
}

async function wStronie(k, funkcja, argumenty = {}) {
  const kod = `(${podstawa.toString()})();\n(${funkcja.toString()})(${JSON.stringify(argumenty)});`;
  try {
    return await k.wc.executeJavaScriptInIsolatedWorld(SWIAT, [{ code: kod }], false);
  } catch (e) {
    throw new Error('Could not read this page: ' + ((e && e.message) || e));
  }
}

function nieaktualny(ref) {
  return new Error(`Element ${ref} is no longer on the page. Call read_page or find again.`);
}

// ---------------------------------------------------------------- myszka i klawiatura

async function kliknijW(k, x, y, podwojnie) {
  x = Math.round(x);
  y = Math.round(y);
  k.wc.sendInputEvent({ type: 'mouseMove', x, y });
  await spij(30);
  for (let c = 1; c <= (podwojnie ? 2 : 1); c++) {
    k.wc.sendInputEvent({ type: 'mouseDown', x, y, button: 'left', clickCount: c });
    k.wc.sendInputEvent({ type: 'mouseUp', x, y, button: 'left', clickCount: c });
  }
}

const KLAWISZE = {
  enter: 'Enter', return: 'Enter', tab: 'Tab', escape: 'Escape', esc: 'Escape', backspace: 'Backspace',
  delete: 'Delete', del: 'Delete', arrowup: 'Up', arrowdown: 'Down', arrowleft: 'Left', arrowright: 'Right',
  up: 'Up', down: 'Down', left: 'Left', right: 'Right', home: 'Home', end: 'End', pageup: 'PageUp',
  pagedown: 'PageDown', space: 'Space', ' ': 'Space', insert: 'Insert'
};

function klawisz(k, nazwa, modyfikatory = []) {
  const n = String(nazwa || '');
  let kod = KLAWISZE[n.toLowerCase()] || (/^f([1-9]|1[01])$/i.test(n) ? n.toUpperCase() : null);
  if (!kod && [...n].length === 1) kod = n;
  if (!kod) throw new Error(`Unknown key "${n}".`);
  const mod = modyfikatory.map(m => ({ ctrl: 'control', control: 'control', shift: 'shift', alt: 'alt' })[String(m).toLowerCase()]).filter(Boolean);
  k.wc.sendInputEvent({ type: 'keyDown', keyCode: kod, modifiers: mod });
  const znak = kod === 'Enter' ? '\r' : kod === 'Space' ? ' ' : kod.length === 1 ? kod : null;
  if (znak && !mod.includes('control') && !mod.includes('alt')) k.wc.sendInputEvent({ type: 'char', keyCode: znak, modifiers: mod });
  k.wc.sendInputEvent({ type: 'keyUp', keyCode: kod, modifiers: mod });
  return kod;
}

// ---------------------------------------------------------------- narzędzia

const WYKONAWCY = {
  async list_tabs() {
    zglos(null, 'przegląda karty');
    const karty = L.karty();
    const aktywna = L.aktywnaId();
    const linie = [`LivePL — AI access: ${L.ustawienia().ai === 'pelny' ? 'read and act' : 'read only'}. The user is looking at: ${aktywna || '(nothing)'}.`, '', 'Programs (left bar):'];
    for (const p of L.programy()) {
      const k = karty.get(p.id);
      if (p.dlaAI === false) { linie.push(`- ${p.id}: ${p.nazwa} — BLOCKED for AI`); continue; }
      let s = `- ${p.id}: ${p.nazwa} — `;
      if (k) {
        s += adresKarty(k) + (k.licznik ? ` (unread ${k.licznik})` : '') + (k.blad ? ' (did not load)' : '');
      } else s += `not open (${p.adres})`;
      linie.push(s);
    }
    const dodatkowe = L.dodatkowe().map(id => karty.get(id)).filter(Boolean);
    linie.push('', 'Tabs:');
    if (!dodatkowe.length) linie.push('- (none)');
    for (const k of dodatkowe) {
      if (czyZablokowany(adresKarty(k), k)) { linie.push(`- ${k.id}: BLOCKED for AI`); continue; }
      linie.push(`- ${k.id}${k.id === aktywna ? ' (active)' : ''}: "${k.nowa ? 'New tab' : k.tytul}" — ${k.nowa ? '' : adresKarty(k)}${k.odAI ? ' (opened by AI)' : ''}`);
    }
    const ukryte = L.wszystkieProgramy().filter(p => p.ukryty && p.dlaAI !== false);
    if (ukryte.length) linie.push('', 'More LivePL programs (open with open_tab): ' + ukryte.map(p => `${p.nazwa} ${p.adres}`).join(', '));
    const blok = zablokowane();
    if (blok.length) linie.push('', 'Blocked for AI: ' + blok.map(b => `${b.nazwa} (${b.origin})`).join(', ') + '.');
    return tekst(linie.join('\n'));
  },

  async get_page_text(a) {
    const k = karta(a.tab_id);
    zglos(k, 'czyta stronę');
    const max = Math.min(Math.max(Number(a.max_chars) || MAKS_ZNAKOW, 500), 100000);
    const w = await wStronie(k, stronaTekst, { max });
    return tekst(`${gdzieJestes(k)}\n\n${w.tekst || '(no text on this page)'}${w.przyciete ? '\n\n… (truncated; use max_chars for more)' : ''}`);
  },

  async read_page(a) {
    const k = karta(a.tab_id);
    zglos(k, 'czyta stronę');
    const max = Math.min(Math.max(Number(a.max_chars) || MAKS_ZNAKOW, 500), 100000);
    const w = await wStronie(k, stronaCzytaj, { filtr: a.filter === 'all' ? 'all' : 'interactive', max });
    return tekst(`${gdzieJestes(k)}\n${w.naglowek}\n\n${w.linie.join('\n') || '(no elements found)'}${w.przyciete ? '\n… (truncated: use find, or max_chars)' : ''}`);
  },

  async find(a) {
    const k = karta(a.tab_id);
    if (!String(a.query || '').trim()) throw new Error('Give a query.');
    zglos(k, `szuka „${String(a.query).slice(0, 40)}”`);
    const w = await wStronie(k, stronaSzukaj, { q: String(a.query).trim() });
    return tekst(w.length ? w.join('\n') : `Nothing matches "${a.query}". Try read_page.`);
  },

  async screenshot(a) {
    const k = karta(a.tab_id);
    if (L.aktywnaId() !== k.id) throw new Error(`Tab ${k.id} is not visible. Call switch_tab first.`);
    if (!oknoWidoczne()) throw new Error('The LivePL window is minimized or hidden. Ask the user to open it.');
    zglos(k, 'robi zrzut ekranu');
    let obraz = await k.wc.capturePage();
    if (obraz.isEmpty()) throw new Error('The screenshot is empty (the page is not drawn yet). Try again in a moment.');
    const b = k.widok.getBounds();
    if (obraz.getSize().width !== b.width) obraz = obraz.resize({ width: b.width, height: b.height, quality: 'good' });
    const { width, height } = obraz.getSize();
    return {
      content: [
        { type: 'image', data: obraz.toJPEG(75).toString('base64'), mimeType: 'image/jpeg' },
        { type: 'text', text: `${gdzieJestes(k)}\nScreenshot ${width}×${height}. Use these pixel coordinates with click (x, y).` }
      ]
    };
  },

  async switch_tab(a) {
    const id = String(a.tab_id || '');
    const p = L.programy().find(x => x.id === id);
    if (p && p.dlaAI === false) throw new Error(`"${p.nazwa}" is blocked for AI.`);
    if (!p && !L.karty().has(id)) throw new Error(`There is no tab "${id}". Call list_tabs.`);
    if (L.karty().has(id)) karta(id);
    zglos(L.karty().get(id), 'przełącza kartę');
    L.przejdzDo(id);
    const k = L.karty().get(id);
    if (k) await poczekaj(k, 8000);
    return tekst(k ? `Now showing: ${gdzieJestes(k)}` : `Opened ${id}.`);
  },

  async wait(a) {
    const k = karta(a.tab_id);
    zglos(k, 'czeka na stronę');
    const ms = Math.min(Math.max(Number(a.seconds) || 2, 0.2), 10) * 1000;
    const start = Date.now();
    await spij(Math.min(ms, 500));
    while (k.wc.isLoading() && Date.now() - start < ms) await spij(150);
    if (Date.now() - start < ms && !k.wc.isLoading()) await spij(Math.max(0, Math.min(ms - (Date.now() - start), 300)));
    return tekst(`${gdzieJestes(k)}${k.wc.isLoading() ? ' (still loading)' : ''}`);
  },

  async open_tab(a) {
    const url = adresZ(a.url);
    if (czyZablokowany(url)) throw new Error('This address is blocked for AI in LivePL.');
    const k = L.nowaKarta(url, { aktywuj: !a.background });
    k.odAI = true;
    zglos(k, `otwiera ${new URL(url).host}`, true);
    await poczekaj(k, 15000);
    return tekst(`Opened ${gdzieJestes(k)}`);
  },

  async navigate(a) {
    const k = karta(a.tab_id);
    const cel = String(a.url || '').trim().toLowerCase();
    if (cel === 'back' || cel === 'forward') {
      const h = k.wc.navigationHistory;
      if (cel === 'back' ? !h.canGoBack() : !h.canGoForward()) throw new Error(`Cannot go ${cel} in this tab.`);
      zglos(k, cel === 'back' ? 'wraca' : 'idzie dalej');
      if (cel === 'back') h.goBack(); else h.goForward();
    } else if (cel === 'reload') {
      zglos(k, 'odświeża stronę');
      k.wc.reload();
    } else {
      const url = adresZ(a.url);
      if (czyZablokowany(url)) throw new Error('This address is blocked for AI in LivePL.');
      zglos(k, `przechodzi do ${new URL(url).host}`, true);
      L.laduj(k, url);
    }
    await poczekaj(k, 15000);
    return tekst(gdzieJestes(k));
  },

  async close_tab(a) {
    const k = karta(a.tab_id);
    if (k.program) throw new Error('Programs on the left bar cannot be closed.');
    zglos(null, 'zamyka kartę');
    L.zamknij(k.id);
    return tekst(`Closed tab ${k.id}.`);
  },

  async click(a) {
    const k = bezOstrzezeniaCertyfikatu(karta(a.tab_id));
    let x, y, opis;
    if (a.ref) {
      const p = await wStronie(k, stronaPunkt, { ref: String(a.ref) });
      if (p.blad) throw nieaktualny(a.ref);
      pokaz(k);
      await spij(60);
      const z = k.wc.getZoomFactor();
      x = p.x * z; y = p.y * z; opis = p.opis;
    } else if (Number.isFinite(Number(a.x)) && Number.isFinite(Number(a.y))) {
      pokaz(k);
      x = Number(a.x); y = Number(a.y); opis = `point ${Math.round(x)},${Math.round(y)}`;
    } else {
      throw new Error('Give ref (from read_page/find) or x and y (from screenshot).');
    }
    zglos(k, 'klika ' + (a.ref ? opis.replace(/^\[ref_\d+\]\s*/, '').slice(0, 50) : 'w punkt'));
    await kliknijW(k, x, y, !!a.double);
    await poczekaj(k, 8000);
    return tekst(`Clicked ${opis}.\n${gdzieJestes(k)}`);
  },

  async type(a) {
    const k = bezOstrzezeniaCertyfikatu(karta(a.tab_id));
    const t = String(a.text == null ? '' : a.text);
    if (t.length > 20000) throw new Error('Text is too long (max 20000 characters).');
    const f = await wStronie(k, stronaFokus, { ref: a.ref ? String(a.ref) : null, wyczysc: !!a.clear });
    if (f.haslo) throw new Error('LivePL does not let AI type into password fields. Ask the user to type the password themselves.');
    if (f.blad === 'nieaktualny') throw nieaktualny(a.ref);
    if (f.blad === 'brak-pola') throw new Error('No text field is focused. Pass ref of the field (read_page or find).');
    if (f.blad === 'nie-pole') throw new Error(`${f.opis} is not a text field.`);
    pokaz(k);
    zglos(k, 'wpisuje tekst');
    k.wc.focus();
    if (a.clear && !t) klawisz(k, 'Delete');
    if (t) await k.wc.insertText(t);
    if (a.submit) { await spij(80); klawisz(k, 'Enter'); await poczekaj(k, 8000); }
    return tekst(`Typed ${t.length} characters into ${f.opis}${a.submit ? ' and pressed Enter' : ''}.\n${gdzieJestes(k)}`);
  },

  async press_key(a) {
    const k = bezOstrzezeniaCertyfikatu(karta(a.tab_id));
    const mod = Array.isArray(a.modifiers) ? a.modifiers : [];
    if (/^f12$/i.test(String(a.key || ''))) throw new Error('F12 (developer tools) is not available to AI.');
    // w polu hasła AI może tylko z niego wyjść (Tab, Esc): żadnych znaków ani wklejania (Ctrl+V)
    if (!/^(tab|escape|esc)$/i.test(String(a.key || '')) && await wStronie(k, stronaCzyHaslo)) {
      throw new Error('A password field is focused. LivePL does not let AI type or paste into password fields; ask the user.');
    }
    pokaz(k);
    k.wc.focus();
    const kod = klawisz(k, a.key, mod);
    zglos(k, `naciska ${[...mod, kod].join('+')}`);
    await poczekaj(k, 5000);
    return tekst(`Pressed ${[...mod, kod].join('+')}.\n${gdzieJestes(k)}`);
  },

  async scroll(a) {
    const k = karta(a.tab_id);
    const kierunek = ['up', 'down', 'left', 'right'].includes(a.direction) ? a.direction : 'down';
    const ile = Math.min(Math.max(Number(a.amount) || 5, 1), 30);
    const z = k.wc.getZoomFactor();
    const w = await wStronie(k, stronaPrzewin, {
      kierunek, ile, ref: a.ref ? String(a.ref) : null,
      x: a.x != null ? Number(a.x) / z : null, y: a.y != null ? Number(a.y) / z : null
    });
    if (w.blad) throw nieaktualny(a.ref);
    zglos(k, 'przewija');
    return tekst(`Scrolled ${kierunek}. Position ${w.y} of ${w.max} px.`);
  }
};

async function wykonaj(nazwa, a) {
  const n = NARZEDZIA.find(x => x.name === nazwa);
  if (!n || !WYKONAWCY[nazwa]) return wynikBlad(`Unknown tool: ${nazwa}`);
  const p = poziom();
  if (!p) return wynikBlad('AI access in LivePL is turned off.');
  if (POZIOMY[n.poziom] > p) {
    return wynikBlad('LivePL lets the AI only read pages (setting "Tylko czytanie"). Ask the user to allow actions: LivePL menu ⋮ → Ustawienia → Dostęp dla AI.');
  }
  return WYKONAWCY[nazwa](a);
}

// ---------------------------------------------------------------- podłączanie aplikacji AI

function konfiguracjaMCP() {
  const env = { ELECTRON_RUN_AS_NODE: '1' };
  if (process.env.LIVEPL_DANE) env.LIVEPL_DANE = path.resolve(process.env.LIVEPL_DANE);
  // Linux (AppImage i .deb): pliki programu nie mają stałej ścieżki (AppImage) albo aktualizacje leżą w nakładce,
  // więc AI uruchamia polecenie LivePL z --ai-most, a ono samo wybiera właściwy most (narzedzia/linux/livepl.sh)
  if (process.platform === 'linux' && (process.env.APPIMAGE || require('./linux').uruchamiacz() === '/usr/bin/livepl')) {
    delete env.ELECTRON_RUN_AS_NODE;
    return { command: require('./linux').uruchamiacz(), args: ['--ai-most'], env };
  }
  return { command: process.execPath, args: [path.join(__dirname, 'ai-most.js')], env };
}

function polecenieClaudeCode() {
  const c = konfiguracjaMCP();
  const zm = Object.entries(c.env).map(([k, v]) => `-e ${k}=${/\s/.test(v) ? `"${v}"` : v}`).join(' ');
  return `claude mcp add livepl --scope user ${zm} -- "${c.command}" "${c.args[0]}"`;
}

function tekstKonfiguracji() {
  return JSON.stringify({ mcpServers: { livepl: konfiguracjaMCP() } }, null, 2);
}

// Claude (aplikacja na Windows) czyta claude_desktop_config.json z %APPDATA%\Claude;
// wersja ze sklepu Microsoft może mieć ten folder w swoim pakiecie, więc zapisujemy w obu miejscach.
// Mac: ~/Library/Application Support/Claude; Linux (wersje Claude budowane przez społeczność): ~/.config/Claude.
function plikiClaudeDesktop() {
  if (process.platform === 'darwin') return [path.join(os.homedir(), 'Library', 'Application Support', 'Claude', 'claude_desktop_config.json')];
  if (process.platform === 'linux') return [path.join(process.env.XDG_CONFIG_HOME || path.join(os.homedir(), '.config'), 'Claude', 'claude_desktop_config.json')];
  const appdata = process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming');
  const lista = [path.join(appdata, 'Claude', 'claude_desktop_config.json')];
  const pakiety = path.join(process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local'), 'Packages');
  try {
    for (const d of fs.readdirSync(pakiety)) {
      if (/^Claude_/i.test(d)) {
        const f = path.join(pakiety, d, 'LocalCache', 'Roaming', 'Claude');
        if (fs.existsSync(f)) lista.push(path.join(f, 'claude_desktop_config.json'));
      }
    }
  } catch { /* brak folderu pakietów */ }
  return lista;
}

function claudeDesktopZainstalowany() {
  return plikiClaudeDesktop().some(f => fs.existsSync(path.dirname(f)));
}

function claudeDesktopPodlaczony() {
  return plikiClaudeDesktop().some(f => {
    try { return !!JSON.parse(fs.readFileSync(f, 'utf8')).mcpServers.livepl; } catch { return false; }
  });
}

// dopisuje LivePL do ustawień Claude, zostawiając resztę bez zmian (kopia obok); zwraca opis wyniku
function podlaczClaudeDesktop() {
  const pliki = plikiClaudeDesktop().filter(f => fs.existsSync(path.dirname(f)));
  if (!pliki.length) return { ok: false, tekst: 'Nie znaleziono aplikacji Claude na tym komputerze.' };
  let zapisano = 0;
  for (const f of pliki) {
    let dane = {};
    if (fs.existsSync(f)) {
      const surowe = fs.readFileSync(f, 'utf8');
      try { dane = surowe.trim() ? JSON.parse(surowe) : {}; } catch {
        return { ok: false, tekst: 'Plik ustawień Claude jest uszkodzony, więc LivePL go nie zmienił: ' + f };
      }
      fs.writeFileSync(f + '.przed-livepl', surowe);
    }
    if (!dane.mcpServers || typeof dane.mcpServers !== 'object') dane.mcpServers = {};
    dane.mcpServers.livepl = konfiguracjaMCP();
    fs.writeFileSync(f, JSON.stringify(dane, null, 2));
    zapisano++;
  }
  return { ok: zapisano > 0, tekst: 'Claude zobaczy LivePL po ponownym uruchomieniu (zamknij Claude także przy zegarze).' };
}

function danePanelu() {
  if (!L) return { poziom: 'wylaczony', polaczony: false, kto: 'AI', zablokowane: [], claudeDesktop: false, claudeDesktopPodlaczony: false };
  return {
    poziom: L.ustawienia().ai || 'wylaczony',
    polaczony: polaczenia.size > 0,
    kto: nazwaKlienta(),
    zablokowane: zablokowane().map(z => z.nazwa),
    claudeDesktop: claudeDesktopZainstalowany(),
    claudeDesktopPodlaczony: claudeDesktopPodlaczony()
  };
}

module.exports = {
  uruchom, ustawPoziom, stan, danePanelu,
  podlaczClaudeDesktop, polecenieClaudeCode, tekstKonfiguracji
};
