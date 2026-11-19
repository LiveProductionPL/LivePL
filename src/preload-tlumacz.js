'use strict';

// Tłumacz stron LivePL – część w stronie. Działa w odizolowanym świecie LivePL (jak hasła):
// strona nie widzi tego kodu ani kanałów do LivePL.
//
// - Po wczytaniu strony zgłasza LivePL atrybut lang i kawałek widocznego tekstu (do rozpoznania języka).
// - Tłumaczy dopiero na polecenie LivePL (kliknięcie albo „zawsze tłumacz z tego języka”).
// - Zmienia tylko treść węzłów tekstowych, więc układ strony, linki i skrypty zostają na miejscu.
//   Akapit idzie do tłumacza w całości (z granicami węzłów jako <span id>), żeby zdanie miało sens.
// - Najpierw tłumaczy to, co widać, potem resztę; treść dopisaną później też (MutationObserver).
// - Nie dotyka pól formularzy, edytowalnych miejsc, kodu ani formularzy z polem hasła.
// - „Pokaż oryginał” przywraca dokładnie poprzedni tekst.

const { ipcRenderer } = require('electron');

(function tlumacz() {
  // tylko główna ramka zwykłej strony (nie ramki w środku i nie strony LivePL)
  if (window.top !== window || !/^https?:$/.test(location.protocol)) return;

  const POMIN = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'TEXTAREA', 'INPUT', 'SELECT', 'OPTION', 'OPTGROUP',
    'DATALIST', 'OUTPUT', 'CODE', 'PRE', 'KBD', 'SAMP', 'VAR', 'CANVAS', 'IFRAME', 'FRAME', 'OBJECT', 'EMBED', 'VIDEO',
    'AUDIO', 'HEAD', 'TITLE', 'META', 'LINK']);
  // elementy w linii: ich tekst należy do tego samego zdania co tekst obok
  const LINIOWE = new Set(['A', 'ABBR', 'ACRONYM', 'B', 'BDI', 'BDO', 'BIG', 'CITE', 'DATA', 'DEL', 'DFN', 'EM', 'FONT',
    'I', 'IMG', 'INS', 'LABEL', 'MARK', 'NOBR', 'Q', 'RP', 'RT', 'RUBY', 'S', 'SMALL', 'SPAN', 'STRIKE', 'STRONG', 'SUB',
    'SUP', 'TIME', 'TT', 'U', 'WBR']);
  const XHTML = 'http://www.w3.org/1999/xhtml';
  const LITERA = /\p{L}/u;

  // ---------------------------------------------------------------- rozpoznanie języka

  function pomijany(el) {
    if (el.namespaceURI !== XHTML) return true; // SVG, MathML
    if (POMIN.has(el.tagName)) return true;
    if (el.getAttribute('translate') === 'no' || el.classList.contains('notranslate')) return true;
    if (el.isContentEditable) return true;
    if (el.tagName === 'FORM' && el.querySelector('input[type="password"]')) return true;
    return false;
  }

  function probka(maks) {
    const body = document.body;
    if (!body) return '';
    let tekst = '';
    const w = document.createTreeWalker(body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, {
      acceptNode: (n) => (n.nodeType === 1 && pomijany(n) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT)
    });
    for (let n = w.nextNode(); n && tekst.length < maks; n = w.nextNode()) {
      if (n.nodeType === 3 && LITERA.test(n.data)) tekst += n.data.replace(/\s+/g, ' ') + ' ';
    }
    return tekst.slice(0, maks);
  }

  function jezykStrony() {
    const html = document.documentElement;
    const meta = document.querySelector('meta[http-equiv="content-language" i]');
    return (html && (html.getAttribute('lang') || html.getAttribute('xml:lang'))) || (meta && meta.content) || '';
  }

  let zgloszono = 0;
  function zglosJezyk() {
    const lang = jezykStrony();
    const p = /^pl\b/i.test(lang) ? probka(600) : probka(3000);
    ipcRenderer.send('tlumacz:jezyk', { lang: lang.slice(0, 30), probka: p });
    zgloszono++;
    // strona składana przez skrypty: mało tekstu po wczytaniu, więc zgłaszamy jeszcze raz później
    if (p.length < 300 && zgloszono < 3) setTimeout(zglosJezyk, 4000);
  }

  if (document.readyState === 'complete') setTimeout(zglosJezyk, 600);
  else window.addEventListener('load', () => setTimeout(zglosJezyk, 600), { once: true });

  // ---------------------------------------------------------------- jednostki do tłumaczenia

  let sesja = 0;        // numer tłumaczenia od LivePL; po „Pokaż oryginał” stare odpowiedzi są ignorowane
  let aktywny = false;
  let tryb = 'calosc';
  let licznik = 0;
  const jednostki = new Map();      // id → { id, wezly: Text[], blok, stan: 'czeka'|'kolejka'|'wyslana'|'gotowa' }
  const info = new WeakMap();       // Text → { oryg, przetl, j, poprawki }
  const blokiJednostek = new Map(); // Element → Set(jednostek)
  const kolejka = [];
  let wTrakcie = 0;
  let gotowe = 0;
  let tytul = null;

  const liniowy = (el) => LINIOWE.has(el.tagName) ||
    (el.tagName.includes('-') && /^inline/.test(getComputedStyle(el).display));

  function blokDla(t) {
    let e = t.parentElement;
    while (e && e !== document.body && liniowy(e)) e = e.parentElement;
    return e || document.body;
  }

  function wykluczonyPrzodek(n) {
    for (let e = n.nodeType === 1 ? n : n.parentElement; e && e !== document.documentElement; e = e.parentElement) {
      if (pomijany(e)) return true;
    }
    return false;
  }

  // zbiera nowe jednostki z poddrzewa: kolejne węzły tekstowe tego samego bloku tworzą jedną jednostkę
  function zbierz(korzen) {
    const nowe = [];
    let biezaca = null;
    // do tłumacza idą tylko węzły z literami: przecinki między linkami i przypisy [1] zostają na swoich
    // miejscach (z nimi silnik przesuwał granice linków)
    const dodaj = (t) => {
      if (info.has(t)) { biezaca = null; return; }
      if (!LITERA.test(t.data)) return;
      const blok = blokDla(t);
      if (!biezaca || biezaca.blok !== blok || biezaca.wezly.length >= 60 || biezaca.dl > 3000) {
        biezaca = { id: ++licznik, wezly: [], blok, dl: 0, stan: 'czeka' };
        nowe.push(biezaca);
      }
      biezaca.wezly.push(t);
      biezaca.dl += t.data.length;
      info.set(t, { oryg: t.data, przetl: null, j: biezaca.id, poprawki: 0 });
    };
    if (korzen.nodeType === 3) {
      dodaj(korzen);
    } else {
      const w = document.createTreeWalker(korzen, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, {
        acceptNode: (n) => (n.nodeType === 1 && pomijany(n) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT)
      });
      for (let n = w.nextNode(); n; n = w.nextNode()) {
        if (n.nodeType === 3) dodaj(n);
        else if (n.tagName === 'BR' || !liniowy(n)) biezaca = null;
      }
    }
    return nowe;
  }

  // czy między dwoma kolejnymi węzłami jednostki jest odstęp (w nich samych albo w węzłach pomiędzy)
  function odstepMiedzy(a, b) {
    if (/\s$/.test(a.data) || /^\s/.test(b.data)) return true;
    return odstepPoza(a, b);
  }

  // odstęp w węzłach pomiędzy a i b (pomin: węzły tej samej jednostki, które LivePL i tak przepisze)
  function odstepPoza(a, b, pomin) {
    const w = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
    w.currentNode = a;
    for (let n = w.nextNode(), i = 0; n && n !== b; n = w.nextNode(), i++) {
      if (i > 40) return true;
      if (pomin && pomin.has(n)) continue;
      if (n.nodeType === 3 ? /\s/.test(n.data) : (n.tagName === 'IMG' || pomijany(n))) return true;
    }
    return false;
  }

  // kolejność: najpierw to, co jest na ekranie, od góry; potem zapas nad i pod ekranem
  const widocznosc = new IntersectionObserver((wpisy) => {
    const odleglosc = (r) => (r.bottom > 0 && r.top < innerHeight ? r.top : innerHeight + Math.abs(r.top));
    const widoczne = wpisy.filter(w => w.isIntersecting)
      .sort((a, b) => odleglosc(a.boundingClientRect) - odleglosc(b.boundingClientRect));
    const nowe = [];
    for (const w of widoczne) {
      widocznosc.unobserve(w.target);
      for (const j of blokiJednostek.get(w.target) || []) {
        if (j.stan === 'czeka') { j.stan = 'kolejka'; nowe.push(j); }
      }
      blokiJednostek.delete(w.target);
    }
    kolejka.unshift(...nowe);
    pompa();
  }, { rootMargin: '700px 0px' });

  function zarejestruj(lista) {
    for (const j of lista) {
      jednostki.set(j.id, j);
      let zbior = blokiJednostek.get(j.blok);
      if (!zbior) {
        zbior = new Set();
        blokiJednostek.set(j.blok, zbior);
        widocznosc.observe(j.blok);
      }
      zbior.add(j);
    }
    if (tryb === 'calosc') zaplanujReszte();
    zglosPostep();
  }

  function doKolejki(j, przod) {
    if (j.stan !== 'czeka') return;
    j.stan = 'kolejka';
    if (przod) kolejka.unshift(j); else kolejka.push(j);
  }

  // tryb „całość” (silnik w komputerze): po tym, co widać, w tle reszta strony
  let reszta = null;
  function zaplanujReszte() {
    if (reszta) return;
    reszta = setTimeout(() => {
      reszta = null;
      if (!aktywny || tryb !== 'calosc') return;
      if (kolejka.length || wTrakcie) { zaplanujReszte(); return; }
      let ile = 0;
      for (const j of jednostki.values()) {
        if (j.stan === 'czeka') { doKolejki(j, false); if (++ile >= 40) break; }
      }
      if (ile) { pompa(); zaplanujReszte(); }
    }, 400);
  }

  // ---------------------------------------------------------------- wysyłanie do LivePL i wstawianie wyniku

  const ucieknij = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  // granice węzłów jako <span id>; spacja między nimi tylko tam, gdzie była w oryginale (np. „W<b>iki</b>” zostaje razem)
  const doHtml = (j) => j.wezly.map((t, i) => (i && odstepMiedzy(j.wezly[i - 1], t) ? ' ' : '') +
    `<span id="${i}">${ucieknij(t.data.trim())}</span>`).join('');

  const ENCJE = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ', '#39': "'" };
  const dekoduj = (s) => s.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+\d*);/gi, (m, e) => {
    if (e[0] === '#') {
      const kod = e[1] === 'x' || e[1] === 'X' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
      return Number.isFinite(kod) && kod > 0 && kod < 0x110000 ? String.fromCodePoint(kod) : m;
    }
    return ENCJE[e.toLowerCase()] ?? m;
  });

  // Wynik tłumacza rozdzielony z powrotem na węzły według <span id>, bez parsera HTML strony.
  // Silnik czasem powtarza znacznik i wkłada do niego sam nawias albo przecinek z sąsiedztwa:
  // taki kawałek bez liter dołącza do tekstu, który stoi przed nim w tłumaczeniu (np. „ang.)” zamiast „ang” + „)”).
  // Tekst poza znacznikami (np. spacja między nimi) też dołącza do poprzedniego kawałka.
  function rozbierz(html, n) {
    const kawalki = [];
    const stos = [];
    let poz = 0;
    const dopisz = (s) => { if (s) kawalki.push({ id: stos.length ? stos[stos.length - 1] : null, tekst: dekoduj(s) }); };
    const znacznik = /<(\/?)([a-zA-Z][\w-]*)([^>]*)>/g;
    for (let m = znacznik.exec(html); m; m = znacznik.exec(html)) {
      dopisz(html.slice(poz, m.index));
      poz = znacznik.lastIndex;
      if (m[2].toLowerCase() !== 'span') continue;
      if (m[1]) { stos.pop(); continue; }
      const idm = /\bid\s*=\s*["']?(\d+)/.exec(m[3]);
      const id = idm ? Number(idm[1]) : NaN;
      stos.push(Number.isInteger(id) && id >= 0 && id < n ? id : (stos.length ? stos[stos.length - 1] : null));
    }
    dopisz(html.slice(poz));
    // główny kawałek każdego węzła: ten z największą liczbą liter
    const glowny = new Map();
    kawalki.forEach((k, i) => {
      if (k.id === null) return;
      const litery = (k.tekst.match(/\p{L}/gu) || []).length;
      const g = glowny.get(k.id);
      if (!g || litery > g.litery) glowny.set(k.id, { i, litery });
    });
    const cel = [];
    let ostatni = null;
    kawalki.forEach((k, i) => {
      const poboczny = k.id !== null && glowny.get(k.id).i !== i && !LITERA.test(k.tekst);
      if (k.id === null || poboczny) cel.push(ostatni);
      else { cel.push(k.id); ostatni = k.id; }
    });
    const pierwszy = cel.find(x => x !== null) ?? 0;
    const wynik = new Array(n).fill(null);
    kawalki.forEach((k, i) => {
      const id = cel[i] ?? pierwszy;
      let przed = wynik[id] ?? '';
      let tekst = k.tekst;
      // przy sklejaniu bez spacji przed przecinkiem i po nawiasie otwierającym: „jest ” + „, …” → „jest, …”
      if (/\s$/.test(przed) && /^\s*[,.;:!?…)\]}%»”]/.test(tekst)) { przed = przed.trimEnd(); tekst = tekst.trimStart(); }
      if (/[([{«„“]$/.test(przed)) tekst = tekst.trimStart();
      wynik[id] = przed + tekst;
    });
    return wynik;
  }

  // Nowy tekst węzłów: odstępy na brzegach jak w oryginale. Gdy tłumacz przestawi słowa i na styku
  // węzłów (np. link i tekst za nim) zabraknie spacji, a tłumacz ją postawił, LivePL ją dopisuje.
  function zastosuj(j, wynik) {
    const czesci = rozbierz(String(wynik || ''), j.wezly.length);
    // silnik zgubił większość znaczników akapitu: zamiast dublować tekst, każdy kawałek idzie osobno
    if (j.wezly.length > 1) {
      const zgubione = czesci.filter((c, i) => (c === null || !c.trim()) && LITERA.test(j.wezly[i].data)).length;
      if (zgubione * 2 >= j.wezly.length) { rozdziel(j); return; }
    }
    const nowe = j.wezly.map((t, i) => {
      const inf = info.get(t);
      // strona zmieniła tekst w międzyczasie: tym zajmie się obserwator zmian
      if (!inf || inf.j !== j.id || t.data !== inf.oryg) return null;
      const rdzen = czesci[i] === null ? null : czesci[i].trim();
      // zgubiony kawałek zostaje po staremu; pusty jest pusty tylko wtedy, gdy nie miał liter (np. przeniesiony przecinek)
      if (rdzen === null || (!rdzen && LITERA.test(inf.oryg))) return null;
      const [, przed, , po] = inf.oryg.match(/^(\s*)([\s\S]*?)(\s*)$/);
      // przed przecinkiem czy kropką, która trafiła na początek kawałka, nie ma spacji
      return rdzen ? (/^[,.;:!?…)\]}%»”]/.test(rdzen) ? '' : przed) + rdzen + po : '';
    });
    const swoje = new Set(j.wezly);
    let k = -1; // ostatni węzeł z widocznym tekstem po lewej
    for (let i = 0; i < nowe.length; i++) {
      const prawy = nowe[i] ?? j.wezly[i].data;
      if (!prawy.trim()) continue;
      if (k >= 0) {
        // oryginalne spacje, które po przestawieniu słów stoją przy interpunkcji: „ang .” → „ang.”, „( MÜ” → „(MÜ”
        if (nowe[k] !== null && /\s$/.test(nowe[k]) && /^\s*[,.;:!?…)\]}%»”]/.test(prawy)) nowe[k] = nowe[k].trimEnd();
        if (nowe[i] !== null && /^\s/.test(nowe[i]) && /[([{«„“]$/.test(nowe[k] ?? j.wezly[k].data)) nowe[i] = nowe[i].trimStart();
      }
      if (k >= 0 && nowe[i] !== null) {
        const lewy = nowe[k] ?? j.wezly[k].data;
        const jestOdstep = /\s$/.test(lewy) || /^\s/.test(prawy) || odstepPoza(j.wezly[k], j.wezly[i], swoje);
        let tlumaczChce = /^\s/.test(czesci[i] || '');
        for (let m = k; m < i && !tlumaczChce; m++) tlumaczChce = /\s$/.test(czesci[m] || '') || (m > k && /\s/.test(czesci[m] || ''));
        // bez spacji przed przecinkiem, kropką, nawiasem zamykającym i po nawiasie otwierającym
        const interpunkcja = /^\s*[,.;:!?…)\]}%»”]/.test(prawy) || /[([{«„“]\s*$/.test(lewy);
        if (!jestOdstep && tlumaczChce && !interpunkcja) nowe[i] = ' ' + nowe[i];
      }
      k = i;
    }
    j.wezly.forEach((t, i) => {
      if (nowe[i] === null) return;
      const inf = info.get(t);
      inf.przetl = nowe[i];
      if (t.data !== inf.przetl) t.data = inf.przetl;
    });
    j.stan = 'gotowa';
    gotowe++;
  }

  function rozdziel(j) {
    usunJednostke(j);
    const nowe = [];
    for (const t of j.wezly) {
      const inf = info.get(t);
      if (!inf || inf.j !== j.id || t.data !== inf.oryg) continue;
      const pojedyncza = { id: ++licznik, wezly: [t], blok: j.blok, dl: t.data.length, stan: 'kolejka' };
      inf.j = pojedyncza.id;
      jednostki.set(pojedyncza.id, pojedyncza);
      nowe.push(pojedyncza);
    }
    kolejka.unshift(...nowe);
  }

  function wezPaczke() {
    const paczka = [];
    let dl = 0;
    // małe paczki: silnik w komputerze tłumaczy ok. 100 słów na sekundę, więc pierwsze akapity pojawiają się po 1–2 s
    while (kolejka.length && paczka.length < 12 && dl < 1500) {
      const j = kolejka.shift();
      if (j.stan !== 'kolejka') continue;
      if (!j.wezly.some(t => t.isConnected)) { usunJednostke(j); continue; }
      j.stan = 'wyslana';
      paczka.push(j);
      dl += j.dl;
    }
    return paczka;
  }

  function pompa() {
    while (aktywny && wTrakcie < 2) {
      const paczka = wezPaczke();
      if (!paczka.length) break;
      wTrakcie++;
      const nr = sesja;
      ipcRenderer.invoke('tlumacz:tekst', { nr, teksty: paczka.map(doHtml) })
        .then((wyniki) => {
          if (nr !== sesja || !aktywny) return;
          paczka.forEach((j, i) => zastosuj(j, wyniki[i]));
        })
        .catch(() => {
          // błąd silnika: LivePL pokazuje komunikat; to, co już przetłumaczone, zostaje
          if (nr !== sesja) return;
          paczka.forEach((j) => { j.stan = 'czeka'; });
          aktywny = false;
        })
        .finally(() => {
          wTrakcie--;
          if (nr === sesja) { zglosPostep(); pompa(); }
        });
    }
    zglosPostep();
  }

  let postepZaplanowany = null;
  function zglosPostep() {
    if (postepZaplanowany) return;
    postepZaplanowany = setTimeout(() => {
      postepZaplanowany = null;
      if (!aktywny && !wTrakcie) return;
      // w trybie „całość” praca trwa, dopóki zostały nieprzetłumaczone kawałki strony
      let czeka = false;
      if (tryb === 'calosc' && aktywny) for (const j of jednostki.values()) if (j.stan === 'czeka') { czeka = true; break; }
      const praca = wTrakcie > 0 || kolejka.length > 0 || czeka;
      ipcRenderer.send('tlumacz:postep', { nr: sesja, praca, gotowe, wszystkie: jednostki.size });
    }, 250);
  }

  function usunJednostke(j) {
    jednostki.delete(j.id);
    const zbior = blokiJednostek.get(j.blok);
    if (zbior) {
      zbior.delete(j);
      if (!zbior.size) { blokiJednostek.delete(j.blok); widocznosc.unobserve(j.blok); }
    }
  }

  // ---------------------------------------------------------------- treść dopisywana później

  const zmiany = new MutationObserver((rekordy) => {
    if (!aktywny) return;
    const korzenie = new Set();
    for (const r of rekordy) {
      if (r.type === 'characterData') {
        const t = r.target;
        const inf = info.get(t);
        if (inf) {
          if (t.data === inf.przetl || (inf.przetl === null && t.data === inf.oryg)) continue;
          if (t.data === inf.oryg && inf.przetl !== null) {
            // strona przywróciła oryginał (np. odświeżenie widoku): wraca tłumaczenie, najwyżej kilka razy
            if (inf.poprawki++ < 5) t.data = inf.przetl;
            continue;
          }
          // strona wpisała nowy tekst: tłumaczymy go od nowa
          info.delete(t);
          const j = jednostki.get(inf.j);
          if (j) j.wezly = j.wezly.filter(x => x !== t);
        }
        if (t.parentNode) korzenie.add(t);
      } else {
        for (const n of r.addedNodes) if (n.nodeType === 1 || n.nodeType === 3) korzenie.add(n);
      }
    }
    const nowe = [];
    for (const n of korzenie) {
      if (!n.isConnected || wykluczonyPrzodek(n.nodeType === 3 ? n.parentElement : n)) continue;
      nowe.push(...zbierz(n));
    }
    if (nowe.length) { zarejestruj(nowe); pompa(); }
  });

  // ---------------------------------------------------------------- start i cofnięcie

  async function przetlumaczTytul(nr) {
    const t = document.title;
    if (!t || !LITERA.test(t)) return;
    try {
      const [w] = await ipcRenderer.invoke('tlumacz:tekst', { nr, teksty: [`<span id="0">${ucieknij(t.trim())}</span>`] });
      const [nowy] = rozbierz(String(w || ''), 1);
      if (nr === sesja && aktywny && nowy && document.title === t) {
        tytul = { oryg: t, przetl: nowy.trim() };
        document.title = tytul.przetl;
      }
    } catch { /* tytuł zostaje */ }
  }

  function start(d) {
    // ponowny start (np. po błędzie albo zmianie języka): najpierw oryginał, potem od nowa
    if (aktywny || jednostki.size) cofnij();
    if (!document.body) return;
    sesja = Number(d && d.nr) || 0;
    tryb = d && d.tryb === 'widoczne' ? 'widoczne' : 'calosc';
    aktywny = true;
    gotowe = 0;
    zarejestruj(zbierz(document.body));
    zmiany.observe(document.body, { childList: true, subtree: true, characterData: true });
    przetlumaczTytul(sesja);
    pompa();
  }

  function cofnij() {
    aktywny = false;
    sesja++;
    zmiany.disconnect();
    widocznosc.disconnect();
    clearTimeout(reszta);
    reszta = null;
    kolejka.length = 0;
    for (const j of jednostki.values()) {
      for (const t of j.wezly) {
        const inf = info.get(t);
        if (inf && inf.przetl !== null && t.data === inf.przetl) t.data = inf.oryg;
        info.delete(t);
      }
    }
    jednostki.clear();
    blokiJednostek.clear();
    if (tytul && document.title === tytul.przetl) document.title = tytul.oryg;
    tytul = null;
  }

  ipcRenderer.on('tlumacz:start', (e, d) => start(d));
  ipcRenderer.on('tlumacz:cofnij', () => cofnij());
})();
