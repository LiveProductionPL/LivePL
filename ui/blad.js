'use strict';

// Strona „nie udało się wczytać”. Rodzaj błędu wybiera minę telewizorka, tekst, rady
// i to, czy LivePL ma sam ponawiać (z coraz dłuższą przerwą; licznik prób podaje main.js).

const q = new URLSearchParams(location.search);
const kod = q.get('kod') || '';
const adres = q.get('adres') || '';
const nazwa = q.get('nazwa') || '';
const opisChromium = q.get('opis') || '';
const proba = Math.max(0, Number(q.get('proba')) || 0);
const n = Number(kod);

let host = adres;
try { host = new URL(adres).host || adres; } catch { /* zostaje adres */ }
const http = /^https?:/i.test(adres);

// nazwy błędów Chromium do szczegółów, gdy strona nie podała własnego opisu
const NAZWY = {
  '-2': 'ERR_FAILED', '-6': 'ERR_FILE_NOT_FOUND', '-7': 'ERR_TIMED_OUT', '-15': 'ERR_SOCKET_NOT_CONNECTED',
  '-20': 'ERR_BLOCKED_BY_CLIENT', '-21': 'ERR_NETWORK_CHANGED', '-27': 'ERR_BLOCKED_BY_RESPONSE', '-30': 'ERR_BLOCKED_BY_CSP',
  '-100': 'ERR_CONNECTION_CLOSED', '-101': 'ERR_CONNECTION_RESET', '-102': 'ERR_CONNECTION_REFUSED',
  '-103': 'ERR_CONNECTION_ABORTED', '-104': 'ERR_CONNECTION_FAILED', '-105': 'ERR_NAME_NOT_RESOLVED',
  '-106': 'ERR_INTERNET_DISCONNECTED', '-107': 'ERR_SSL_PROTOCOL_ERROR', '-109': 'ERR_ADDRESS_UNREACHABLE',
  '-113': 'ERR_SSL_VERSION_OR_CIPHER_MISMATCH', '-118': 'ERR_CONNECTION_TIMED_OUT', '-130': 'ERR_PROXY_CONNECTION_FAILED',
  '-137': 'ERR_NAME_RESOLUTION_FAILED', '-200': 'ERR_CERT_COMMON_NAME_INVALID', '-201': 'ERR_CERT_DATE_INVALID',
  '-202': 'ERR_CERT_AUTHORITY_INVALID', '-300': 'ERR_INVALID_URL', '-310': 'ERR_TOO_MANY_REDIRECTS',
  '-320': 'ERR_INVALID_RESPONSE', '-324': 'ERR_EMPTY_RESPONSE', '-501': 'ERR_INSECURE_RESPONSE'
};

// Opis to lista kawałków: zwykły tekst albo { b: '…' } (pogrubione). Składany przez textContent.
const H = { b: host };
const SAM = 'LivePL spróbuje jeszcze raz sam.';
let r = {
  mina: 'zdziwiony',
  naglowek: 'Nie udało się otworzyć strony',
  opis: ['Coś poszło nie tak przy wczytywaniu ', H, '.'],
  rady: ['Kliknij „Spróbuj teraz”.', 'Jeśli inne strony też nie działają, sprawdź internet.'],
  ponawiaj: false
};

if (kod === 'awaria') {
  r = {
    mina: 'ups',
    naglowek: 'Ups, ta karta się zawiesiła',
    opis: ['Strona przestała działać, więc LivePL ją wyłączył. Kliknij poniżej, a wczyta się od nowa.'],
    rady: ['Jeśli to się powtarza, zamknij inne ciężkie strony albo uruchom LivePL ponownie.'],
    ponawiaj: false
  };
} else if (n === -106) {
  r = {
    mina: 'spi', bezSygnalu: true,
    naglowek: 'Nie ma internetu',
    opis: ['Ten komputer jest teraz offline. Gdy internet wróci, strona wczyta się sama.'],
    rady: ['Sprawdź, czy Wi-Fi jest włączone.', 'Jeśli komputer ma kabel, sprawdź, czy jest wpięty.', 'Nic nie pomaga? Uruchom ponownie router.'],
    ponawiaj: true
  };
} else if (n === -21) {
  r = {
    mina: 'usmiech',
    naglowek: 'Zmieniła się sieć',
    opis: ['Komputer przełączył się na inne połączenie. Za chwilę wczytam ', H, ' jeszcze raz.'],
    rady: [],
    ponawiaj: true, pierwszaPrzerwa: 2
  };
} else if (n === -105 || n === -137) {
  r = {
    mina: 'zdziwiony',
    naglowek: 'Nie mogę znaleźć tej strony',
    opis: ['Pod adresem ', H, ' nikt nie odpowiada. Może w adresie jest literówka?'],
    rady: ['Sprawdź pisownię adresu (Ctrl+K, żeby go poprawić).', 'Jeśli adres jest dobry, sprawdź internet.'],
    // literówka nie zniknie sama: przy obcej stronie tylko kilka prób, program LivePL próbuje bez końca
    ponawiaj: !!nazwa || proba < 3
  };
} else if ([-100, -101, -103].includes(n)) {
  r = {
    mina: 'usmiech',
    naglowek: 'Połączenie się urwało',
    opis: [H, ' przerwał połączenie w trakcie wczytywania. Zwykle to tylko chwilowa przerwa. ', SAM],
    rady: ['Jeśli to trwa dłużej, sprawdź, czy inne strony działają.'],
    ponawiaj: true
  };
} else if (n === -102) {
  r = {
    mina: 'zdziwiony',
    naglowek: 'Serwer nie przyjmuje połączeń',
    opis: [H, ' jest teraz wyłączony albo właśnie się uruchamia. ', SAM],
    rady: ['Odczekaj chwilę.', 'Jeśli to nasz serwer i nie wraca po kilku minutach, daj znać informatykowi.'],
    ponawiaj: true
  };
} else if (n === -7 || n === -118) {
  r = {
    mina: 'usmiech',
    naglowek: 'Strona długo nie odpowiada',
    opis: [H, ' nie odpowiedział na czas. Może mieć teraz dużo pracy. ', SAM],
    rady: ['Jeśli inne strony też się nie wczytują, sprawdź internet.'],
    ponawiaj: true
  };
} else if ([-104, -109, -15, -130, -324, -320].includes(n)) {
  r = {
    mina: 'usmiech',
    naglowek: 'Nie udało się połączyć',
    opis: ['LivePL nie może teraz połączyć się z ', H, '. ', SAM],
    rady: ['Sprawdź, czy inne strony działają.', 'Jeśli korzystasz z VPN albo serwera proxy, sprawdź, czy jest włączony.'],
    ponawiaj: true
  };
} else if ((n <= -200 && n > -300) || n === -107 || n === -113 || n === -501) {
  r = {
    mina: 'powazny', tarcza: true,
    naglowek: n === -107 || n === -113 ? 'Nie da się połączyć bezpiecznie' : 'Ta strona nie jest bezpieczna',
    opis: ['Certyfikat strony ', H, ' jest nieprawidłowy albo wygasł, więc LivePL ją zatrzymał, żeby chronić Twoje dane.'],
    rady: [
      ...(n === -201 ? ['Sprawdź, czy data i godzina w komputerze są dobre.'] : []),
      'Nie wpisuj na tej stronie haseł ani danych.',
      'Jeśli to nasz serwer, sprawdź certyfikat w DirectAdminie.'
    ],
    ponawiaj: false
  };
} else if (n === -310) {
  r = {
    mina: 'zdziwiony',
    naglowek: 'Strona kręci się w kółko',
    opis: [H, ' bez końca przekierowuje na samą siebie.'],
    rady: ['Wyloguj się z tej strony i zaloguj ponownie.', 'Jeśli to nie pomoże, problem jest po stronie serwera.'],
    ponawiaj: false
  };
} else if ([-20, -27, -30].includes(n)) {
  r = {
    mina: 'powazny',
    naglowek: 'Ta strona nie chce się tu wyświetlić',
    opis: [H, ' nie pozwala otworzyć się w tym miejscu.'],
    rady: ['Kliknij prawym przyciskiem i otwórz link w zwykłej przeglądarce.'],
    ponawiaj: false
  };
} else if (n === -300 || n === -6) {
  r = {
    mina: 'zdziwiony',
    naglowek: n === -6 ? 'Nie ma takiego pliku' : 'Ten adres jest nieprawidłowy',
    opis: ['Sprawdź adres i spróbuj jeszcze raz.'],
    rady: [],
    ponawiaj: false
  };
}

// ---------------------------------------------------------------- telewizorek

const PASY = ['#e9e9e9', '#e8d44d', '#4cc3d6', '#55bb63', '#c862c4', '#e2524f', '#4a67d6'];
const MINY = {
  usmiech: '<circle class="pelne" cx="74" cy="86" r="4.6"/><circle class="pelne" cx="102" cy="86" r="4.6"/><path d="M74 101q14 12 28 0"/>',
  zdziwiony: '<circle class="pelne" cx="74" cy="86" r="4.6"/><circle class="pelne" cx="102" cy="86" r="4.6"/><circle cx="88" cy="105" r="5.2"/>',
  spi: '<path d="M68 88q6 5 12 0M96 88q6 5 12 0"/><path d="M82 105q6 3 12 0"/>',
  powazny: '<circle class="pelne" cx="74" cy="86" r="4.6"/><circle class="pelne" cx="102" cy="86" r="4.6"/><path d="M77 105h22"/>',
  ups: '<path d="M70 82l8 8M78 82l-8 8M98 82l8 8M106 82l-8 8"/><path d="M71 106q4.25-5 8.5 0t8.5 0 8.5 0 8.5 0"/>'
};

function telewizor() {
  const pasy = PASY.map((c, i) => `<rect x="${36 + i * 104 / 7}" y="54" width="${104 / 7 + 0.5}" height="76" fill="${c}"/>`).join('');
  const dodatek = r.mina === 'spi'
    ? '<g class="tv-dodatek tv-zzz"><text x="26" y="34">z</text><text x="12" y="20">Z</text></g>'
    : r.mina === 'zdziwiony' ? '<g class="tv-dodatek"><text x="18" y="30">?</text></g>' : '';
  const tarcza = r.tarcza
    ? '<path class="tv-tarcza" d="M176 112l-16 6v11c0 9 6.6 16.4 16 19.5 9.4-3.1 16-10.5 16-19.5v-11z"/><path class="tv-tarcza-znak" d="M176 122v9M176 137.5h.01"/>'
    : '';
  const glosnik = r.tarcza ? '' : '<path class="tv-nogi" d="M150 112h18M150 119h18M150 126h18"/>';
  return `<svg viewBox="0 0 200 165" class="${r.bezSygnalu ? 'brak-sygnalu' : ''}">
    <defs><clipPath id="ekran"><rect x="36" y="54" width="104" height="76" rx="12"/></clipPath></defs>
    <g class="tv-antena"><path d="M100 40 78 13M100 40 124 10"/><circle cx="78" cy="13" r="4.5"/><circle cx="124" cy="10" r="4.5"/></g>
    <g class="tv-fale"><path d="M134 2q6 8 0 16"/><path d="M142-4q11 14 0 28"/></g>
    ${dodatek}
    <rect class="tv-obudowa" x="22" y="40" width="156" height="104" rx="22"/>
    <rect class="tv-ekran" x="36" y="54" width="104" height="76" rx="12"/>
    <g class="tv-pasy" clip-path="url(#ekran)">${pasy}</g>
    <g class="tv-twarz">${MINY[r.mina] || MINY.zdziwiony}</g>
    <circle class="tv-pokretlo" cx="159" cy="72" r="6.5"/><circle class="tv-pokretlo" cx="159" cy="92" r="6.5"/>
    ${glosnik}
    <path class="tv-nogi" d="M58 144 50 157M142 144l8 13"/>
    ${tarcza}
  </svg>`;
}

// ---------------------------------------------------------------- treść

const $ = (id) => document.getElementById(id);
$('telewizor').innerHTML = telewizor();

const gdzie = $('gdzie');
if (host || nazwa) {
  gdzie.innerHTML = ikona(r.tarcza ? 'klodka-otwarta' : 'strona');
  const s = document.createElement('span');
  s.textContent = nazwa && host ? `${nazwa} · ${host}` : (nazwa || host);
  gdzie.appendChild(s);
  gdzie.hidden = false;
}

$('naglowek').textContent = r.naglowek;
document.title = r.naglowek;
const opis = $('opis');
for (const kawalek of r.opis) {
  if (typeof kawalek === 'string') opis.append(kawalek);
  else { const b = document.createElement('b'); b.textContent = kawalek.b; opis.appendChild(b); }
}

if (r.rady.length) {
  for (const t of r.rady) {
    const li = document.createElement('li');
    li.textContent = t;
    $('rady-lista').appendChild(li);
  }
  $('rady').hidden = false;
}

// szczegóły dla informatyka
const szczegoly = [];
if (adres) szczegoly.push(['Adres', adres]);
if (kod && kod !== 'awaria') szczegoly.push(['Błąd', `${opisChromium || NAZWY[kod] || 'ERR'} (${kod})`]);
if (kod === 'awaria' && opisChromium) szczegoly.push(['Powód', opisChromium]);
if (proba) szczegoly.push(['Próba', String(proba + 1)]);
szczegoly.push(['Godzina', new Date().toLocaleTimeString('pl-PL')]);
for (const [dt, dd] of szczegoly) {
  const a = document.createElement('dt'); a.textContent = dt;
  const b = document.createElement('dd'); b.textContent = dd;
  $('szczegoly-lista').append(a, b);
}
$('szczegoly').hidden = false;

// ---------------------------------------------------------------- ponawianie

const ponow = $('ponow');
let wczytuje = false;
function wczytajPonownie() {
  if (wczytuje || !http) return;
  wczytuje = true;
  $('odliczanie-tekst').textContent = 'Wczytuję…';
  location.href = adres;
}

if (http) {
  ponow.href = adres;
  if (kod === 'awaria') ponow.textContent = 'Wczytaj od nowa';
  ponow.addEventListener('click', (e) => { e.preventDefault(); wczytajPonownie(); });
} else {
  ponow.hidden = true;
}

if (r.ponawiaj && http) {
  // 5 s, 10 s, 20 s, 30 s, potem co minutę
  const PRZERWY = [5, 10, 20, 30, 60];
  const przerwa = proba === 0 && r.pierwszaPrzerwa ? r.pierwszaPrzerwa : PRZERWY[Math.min(proba, PRZERWY.length - 1)];
  const koniec = Date.now() + przerwa * 1000;
  const postep = $('postep');
  const tekst = $('odliczanie-tekst');
  $('odliczanie').hidden = false;

  // zwykły zegar, nie requestAnimationFrame: program w tle (np. poczta) też ma ponawiać, choć go nie widać
  const tik = () => {
    if (wczytuje) return clearInterval(zegar);
    const zostalo = Math.max(0, koniec - Date.now());
    postep.style.width = `${100 - (zostalo / (przerwa * 1000)) * 100}%`;
    if (zostalo <= 0) { clearInterval(zegar); return wczytajPonownie(); }
    tekst.textContent = n === -106 && !navigator.onLine
      ? `Czekam na internet… sprawdzę za ${Math.ceil(zostalo / 1000)} s`
      : `Spróbuję ponownie za ${Math.ceil(zostalo / 1000)} s`;
  };
  const zegar = setInterval(tik, 200);
  tik();
  window.addEventListener('online', wczytajPonownie);
}
