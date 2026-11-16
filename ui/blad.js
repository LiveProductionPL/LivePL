'use strict';

const q = new URLSearchParams(location.search);
const kod = q.get('kod') || '';
const adres = q.get('adres') || '';
const nazwa = q.get('nazwa') || '';
const n = Number(kod);

let host = adres;
try { host = new URL(adres).host; } catch { /* zostaje adres */ }

const SIEC = [-106, -21, -100, -101, -102, -104, -105, -109, -118, -130, -137, -7, -15];
let naglowek = 'Nie udało się otworzyć strony';
let opis = 'Coś poszło nie tak przy wczytywaniu. Spróbuj ponownie za chwilę.';
let samoPonawiaj = false;

if (kod === 'awaria') {
  naglowek = 'Strona przestała działać';
  opis = 'Karta zamknęła się nieoczekiwanie. Po kliknięciu poniżej wczyta się od nowa.';
} else if (n === -106) {
  naglowek = 'Brak połączenia z internetem';
  opis = 'Sprawdź Wi-Fi albo kabel. Strona wczyta się sama, gdy internet wróci.';
  samoPonawiaj = true;
} else if (n === -105 || n === -137) {
  naglowek = 'Nie można znaleźć tej strony';
  opis = `Adres ${host} nie odpowiada. Sprawdź, czy jest wpisany poprawnie, albo spróbuj za chwilę.`;
  samoPonawiaj = true;
} else if (n <= -200 && n > -300) {
  naglowek = 'Połączenie nie jest bezpieczne';
  opis = `Certyfikat strony ${host} jest nieprawidłowy albo wygasł, więc LivePL jej nie otworzył. Jeśli to nasz serwer, sprawdź certyfikat w DirectAdminie.`;
} else if (SIEC.includes(n)) {
  naglowek = 'Serwer nie odpowiada';
  opis = `${host} nie odpowiedział na czas. Może to być chwilowa przerwa – spróbuj ponownie.`;
  samoPonawiaj = true;
}

document.getElementById('znak').innerHTML = ikona('uwaga');
document.getElementById('gdzie').textContent = nazwa || host;
document.getElementById('naglowek').textContent = naglowek;
document.getElementById('opis').textContent = opis;
document.getElementById('kod').textContent = kod && kod !== 'awaria' ? `Kod błędu: ${kod}${q.get('opis') ? ' (' + q.get('opis') + ')' : ''}` : '';
document.title = naglowek;

const ponow = document.getElementById('ponow');
if (/^https?:/i.test(adres)) {
  ponow.href = adres;
} else {
  ponow.hidden = true;
}

if (samoPonawiaj && /^https?:/i.test(adres)) {
  window.addEventListener('online', () => { location.href = adres; });
}
