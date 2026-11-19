'use strict';
// Ikony rysowane kreską 1.8 px na siatce 24×24.
window.IKONY = {
  poczta: '<rect x="3" y="5.5" width="18" height="13" rx="2.5"/><path d="m3.8 7.6 8.2 5.6 8.2-5.6"/>',
  klient: '<circle cx="12" cy="8.6" r="3.6"/><path d="M5 19.6c1.1-3.3 3.8-5.1 7-5.1s5.9 1.8 7 5.1"/>',
  lekcje: '<rect x="3" y="4" width="18" height="12.5" rx="2"/><path d="M8.5 20h7M12 16.5V20"/><path d="M10.4 8.2v4.6l3.9-2.3z"/>',
  egzamin: '<path d="M2.6 9.2 12 4.8l9.4 4.4L12 13.6z"/><path d="M6.6 11.2v4.3c1.5 1.4 3.4 2.1 5.4 2.1s3.9-.7 5.4-2.1v-4.3"/><path d="M21.4 9.2v5.1"/>',
  serwer: '<rect x="3.5" y="4" width="17" height="7" rx="1.8"/><rect x="3.5" y="13" width="17" height="7" rx="1.8"/><path d="M7.4 7.5h.01M7.4 16.5h.01" stroke-width="2.6"/>',
  strona: '<circle cx="12" cy="12" r="8.6"/><path d="M3.6 12h16.8M12 3.4c2.3 2.4 3.5 5.3 3.5 8.6s-1.2 6.2-3.5 8.6c-2.3-2.4-3.5-5.3-3.5-8.6s1.2-6.2 3.5-8.6z"/>',
  wstecz: '<path d="M14.8 5.5 8.3 12l6.5 6.5"/>',
  dalej: '<path d="m9.2 5.5 6.5 6.5-6.5 6.5"/>',
  odswiez: '<path d="M19 12.2a7 7 0 1 1-2.1-5"/><path d="M19.2 4.6v3.9h-3.9"/>',
  stop: '<path d="m6.5 6.5 11 11M17.5 6.5l-11 11"/>',
  plus: '<path d="M12 5.5v13M5.5 12h13"/>',
  zamknij: '<path d="m7.5 7.5 9 9M16.5 7.5l-9 9"/>',
  gora: '<path d="m6.5 14.5 5.5-5.5 5.5 5.5"/>',
  dol: '<path d="m6.5 9.5 5.5 5.5 5.5-5.5"/>',
  pobrane: '<path d="M12 4.5v10M7.8 10.6 12 14.8l4.2-4.2"/><path d="M4.8 16.2v1.9c0 .9.7 1.6 1.6 1.6h11.2c.9 0 1.6-.7 1.6-1.6v-1.9"/>',
  menu: '<circle cx="12" cy="5.8" r="1.1" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.1" fill="currentColor" stroke="none"/><circle cx="12" cy="18.2" r="1.1" fill="currentColor" stroke="none"/>',
  'klodka-otwarta': '<rect x="5.8" y="10.6" width="12.4" height="9" rx="2"/><path d="M8.6 10.6V8.2a3.4 3.4 0 0 1 6.6-1.2"/>',
  slonce: '<circle cx="12" cy="12" r="3.8"/><path d="M12 2.8v2.2M12 19v2.2M2.8 12H5M19 12h2.2M5.5 5.5l1.6 1.6M16.9 16.9l1.6 1.6M5.5 18.5l1.6-1.6M16.9 7.1l1.6-1.6"/>',
  ksiezyc: '<path d="M19.5 14.6A7.9 7.9 0 0 1 9.4 4.5a7.9 7.9 0 1 0 10.1 10.1z"/>',
  automat: '<circle cx="12" cy="12" r="8.2"/><path d="M12 3.8a8.2 8.2 0 0 1 0 16.4z" fill="currentColor" stroke="none"/>',
  tarcza: '<path d="M12 3.2 5 6v5.4c0 4.3 2.9 7.9 7 9.4 4.1-1.5 7-5.1 7-9.4V6z"/><path d="m9 12 2.2 2.2L15.3 10"/>',
  klodka: '<rect x="5.8" y="10.6" width="12.4" height="9" rx="2"/><path d="M8.6 10.6V8.2a3.4 3.4 0 0 1 6.8 0v2.4"/>',
  uwaga: '<path d="M12 4.2 2.9 19.5h18.2z"/><path d="M12 10v4.2M12 16.9h.01"/>',
  szukaj: '<circle cx="10.8" cy="10.8" r="6"/><path d="m15.3 15.3 4.4 4.4"/>',
  drukuj: '<path d="M7 9V4.5h10V9"/><rect x="4" y="9" width="16" height="7.5" rx="1.8"/><path d="M7 14h10v5.5H7z"/>',
  zewnetrzna: '<path d="M14 4.5h5.5V10"/><path d="M19.5 4.5 11 13"/><path d="M17.5 13.5v4.2c0 1-.8 1.8-1.8 1.8H6.3c-1 0-1.8-.8-1.8-1.8V8.3c0-1 .8-1.8 1.8-1.8h4.2"/>',
  kopiuj: '<rect x="8.5" y="8.5" width="11" height="11" rx="2"/><path d="M15.5 8.5V6.3c0-1-.8-1.8-1.8-1.8H6.3c-1 0-1.8.8-1.8 1.8v7.4c0 1 .8 1.8 1.8 1.8h2.2"/>',
  folder: '<path d="M3.5 7.5c0-1.1.9-2 2-2h4l2 2.2h7c1.1 0 2 .9 2 2v7.8c0 1.1-.9 2-2 2h-13c-1.1 0-2-.9-2-2z"/>',
  ustawienia: '<path d="M4.5 7h9M17.5 7h2M4.5 17h2M10.5 17h9"/><circle cx="15.5" cy="7" r="2"/><circle cx="8.5" cy="17" r="2"/>',
  kod: '<path d="m8.5 8-4 4 4 4M15.5 8l4 4-4 4"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8h.01"/>',
  wyjscie: '<path d="M12 4v7"/><path d="M7.4 6.9a7 7 0 1 0 9.2 0"/>',
  przywroc: '<path d="M5 12.2a7 7 0 1 0 2.1-5"/><path d="M4.8 4.6v3.9h3.9"/>',
  minus: '<path d="M5.5 12h13"/>',
  wyloguj: '<path d="M14 4.5h3.7c1 0 1.8.8 1.8 1.8v11.4c0 1-.8 1.8-1.8 1.8H14"/><path d="M10 8l-4 4 4 4M6 12h9"/>',
  kosz: '<path d="M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12"/>',
  lista: '<path d="M8.5 7h11M8.5 12h11M8.5 17h11M4.5 7h.01M4.5 12h.01M4.5 17h.01"/>',
  plik: '<path d="M7 3.5h6.5l4.5 4.5v11c0 .8-.7 1.5-1.5 1.5h-9.5c-.8 0-1.5-.7-1.5-1.5v-14c0-.8.7-1.5 1.5-1.5z"/><path d="M13.5 3.5V8H18"/>',
  znacznik: '<path d="m5.5 12.5 4 4 9-9"/>',
  karty: '<rect x="4" y="7" width="13" height="12" rx="2"/><path d="M8 4h10a2 2 0 0 1 2 2v10"/>',
  pinezka: '<path d="M9.2 4.5h5.6l-.9 5.2 3.1 3.1H7l3.1-3.1z"/><path d="M12 12.8v6.7"/>',
  odepnij: '<path d="M9.2 4.5h5.6l-.9 5.2 3.1 3.1H7l3.1-3.1z"/><path d="M12 12.8v6.7M4.5 4.5l15 15"/>',
  klucz: '<circle cx="8" cy="15" r="3.6"/><path d="m10.6 12.4 7.9-7.9M15.6 7.4l2.2 2.2M13.4 9.6l1.7 1.7"/>',
  ai: '<path d="M10.5 4c.7 4.1 2.6 6 6.7 6.7-4.1.7-6 2.6-6.7 6.7-.7-4.1-2.6-6-6.7-6.7 4.1-.7 6-2.6 6.7-6.7z"/><path d="M18 14.6c.3 1.8 1.1 2.6 2.9 2.9-1.8.3-2.6 1.1-2.9 2.9-.3-1.8-1.1-2.6-2.9-2.9 1.8-.3 2.6-1.1 2.9-2.9z"/>',
  // tłumacz: znak pisma i litera A
  tlumacz: '<path d="M3.5 6.2h8.6M7.8 4v2.2"/><path d="M10.3 6.2c-.7 3.4-2.9 6.1-6.3 7.6"/><path d="M5.6 8.6c1 2 2.7 3.5 4.9 4.4"/><path d="m12.6 20 3.9-9.2 3.9 9.2M14 16.9h5"/>'
};

window.ikona = function ikona(nazwa, klasa) {
  const tresc = window.IKONY[nazwa] || window.IKONY.strona;
  return '<svg viewBox="0 0 24 24" class="ik' + (klasa ? ' ' + klasa : '') + '" aria-hidden="true">' + tresc + '</svg>';
};
