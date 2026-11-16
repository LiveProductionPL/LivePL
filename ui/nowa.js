'use strict';

// Strona nowej karty i strona startowa LivePL: wyszukiwanie, programy z paska, więcej programów
// i dodawanie własnej strony na pasek (przez window.liveplNowa z preload-strona.js).

const $ = (s) => document.querySelector(s);
const parametry = new URLSearchParams(location.search);
let dane = { naPasku: [], wiecej: [] };
try {
  const d = JSON.parse(parametry.get('programy') || '{}');
  dane = Array.isArray(d) ? { naPasku: d, wiecej: [] } : { naPasku: d.naPasku || [], wiecej: d.wiecej || [] };
} catch { /* pusta strona startowa */ }

// ---------------------------------------------------------------- powitanie

const teraz = new Date();
const godzina = teraz.getHours();
$('#powitanie').textContent = godzina < 5 ? 'Dobranoc' : godzina < 18 ? 'Dzień dobry' : 'Dobry wieczór';
const dzis = teraz.toLocaleDateString('pl-PL', { weekday: 'long', day: 'numeric', month: 'long' });
$('#data').textContent = dzis.charAt(0).toUpperCase() + dzis.slice(1);

// ---------------------------------------------------------------- wyszukiwanie (jak pole adresu LivePL)

function naAdres(tekst) {
  const t = String(tekst || '').trim();
  if (!t) return null;
  if (/^https?:\/\//i.test(t)) return t;
  if (/^localhost(:\d+)?(\/|$)/i.test(t) || /^\d{1,3}(\.\d{1,3}){3}(:\d+)?(\/|$)/.test(t)) return 'http://' + t;
  if (!/\s/.test(t) && /^[^/]+\.[a-z]{2,}(:\d+)?(\/.*)?$/i.test(t)) return 'https://' + t;
  return 'https://www.google.com/search?q=' + encodeURIComponent(t);
}

$('#szukaj-ik').innerHTML = ikona('szukaj');
$('#szukaj').addEventListener('submit', (e) => {
  e.preventDefault();
  const url = naAdres($('#szukaj-pole').value);
  if (url) location.href = url;
});
setTimeout(() => $('#szukaj-pole').focus(), 50);

// ---------------------------------------------------------------- kafle

function host(adres) {
  try { return new URL(adres).host.replace(/^www\./, ''); } catch { return adres; }
}

function znak(p) {
  const z = document.createElement('span');
  z.className = 'znak';
  const dozwolony = (s) => typeof s === 'string' && /^\.\.\/assets\/programy\/[a-z0-9-]+\.(png|svg)$/.test(s);
  if (dozwolony(p.obrazek)) {
    // <picture>: przeglądarka sama podmienia logo, gdy zmienia się tryb dzień/noc
    const obraz = document.createElement('picture');
    if (dozwolony(p.obrazekNoc)) {
      const noc = document.createElement('source');
      noc.media = '(prefers-color-scheme: dark)';
      noc.srcset = p.obrazekNoc;
      obraz.appendChild(noc);
    }
    const img = new Image();
    img.className = 'obrazek';
    img.alt = '';
    img.src = p.obrazek;
    obraz.appendChild(img);
    z.classList.add('z-obrazkiem');
    z.appendChild(obraz);
  } else if (p.wlasny) {
    z.classList.add('literka');
    z.textContent = (p.nazwa || host(p.adres)).charAt(0).toUpperCase();
  } else {
    z.innerHTML = ikona(p.ikona || 'strona');
  }
  return z;
}

function kafel(p, nr) {
  const a = document.createElement('a');
  a.className = 'kafel';
  a.href = p.adres;
  a.appendChild(znak(p));
  const t = document.createElement('span');
  t.className = 'kafel-tekst';
  const b = document.createElement('b');
  b.textContent = p.nazwa;
  const o = document.createElement('span');
  o.className = 'kafel-opis';
  o.textContent = p.opis || host(p.adres);
  t.append(b, o);
  if (p.opis) {
    const s = document.createElement('small');
    s.textContent = host(p.adres);
    t.appendChild(s);
  }
  a.appendChild(t);
  if (nr <= 8) {
    const k = document.createElement('kbd');
    k.className = 'skrot';
    k.textContent = 'Ctrl+' + nr;
    a.appendChild(k);
  }
  return a;
}

function maly(p) {
  const a = document.createElement('a');
  a.className = 'maly-kafel';
  a.href = p.adres;
  a.appendChild(znak(p));
  const t = document.createElement('span');
  const b = document.createElement('b');
  b.textContent = p.nazwa;
  const s = document.createElement('small');
  s.textContent = p.opis || host(p.adres);
  t.append(b, s);
  a.appendChild(t);
  return a;
}

function rysuj() {
  const naPasku = $('#na-pasku');
  naPasku.replaceChildren(...dane.naPasku.map((p, i) => kafel(p, i + 1)));
  $('#wiecej').replaceChildren(...dane.wiecej.map(maly));
  $('#sekcja-wiecej').hidden = dane.wiecej.length === 0;
}
rysuj();

// ---------------------------------------------------------------- dodaj stronę na pasek

const api = window.liveplNowa;
if (api && typeof api.dodaj === 'function') {
  $('#sekcja-dodaj').hidden = false;
  const wynik = $('#dodaj-wynik');
  $('#dodaj-forma').addEventListener('submit', async (e) => {
    e.preventDefault();
    const adres = $('#dodaj-adres').value.trim();
    const nazwa = $('#dodaj-nazwa').value.trim();
    const przycisk = $('#dodaj-przycisk');
    przycisk.disabled = true;
    try {
      const blad = await api.dodaj(adres, nazwa);
      wynik.hidden = false;
      if (blad) {
        wynik.className = 'wynik zly';
        wynik.textContent = blad;
        return;
      }
      wynik.className = 'wynik ok';
      const pelny = /^[a-z][a-z0-9+.-]*:\/\//i.test(adres) ? adres : 'https://' + adres;
      wynik.textContent = 'Dodano na pasek: ' + (nazwa || host(pelny)) + '. Znajdziesz ją po lewej.';
      dane.naPasku.push({ nazwa: nazwa || host(pelny), adres: pelny, wlasny: true });
      rysuj();
      $('#dodaj-adres').value = '';
      $('#dodaj-nazwa').value = '';
    } catch {
      wynik.hidden = false;
      wynik.className = 'wynik zly';
      wynik.textContent = 'Nie udało się dodać strony.';
    } finally {
      przycisk.disabled = false;
    }
  });
}
