'use strict';

// Wschód i zachód słońca dla trybu dzień i noc, liczone na miejscu (bez internetu)
// wzorami NOAA. Dokładność około minuty; dla Leszna te same godziny co Open-Meteo,
// z którego korzysta tvml.pl.

const RAD = Math.PI / 180;

// słońce schowane pod horyzontem (z refrakcją i promieniem tarczy), jak w Open-Meteo
const ZENIT = 90.833;

// minuty od północy UTC danego dnia do wschodu (znak -1) albo zachodu (znak 1)
function minutyUTC(polnocUTC, szerokosc, dlugosc, znak) {
  const licz = (minuty) => {
    const T = (polnocUTC / 864e5 + 2440587.5 + minuty / 1440 - 2451545) / 36525;
    const L0 = ((280.46646 + T * (36000.76983 + T * 0.0003032)) % 360 + 360) % 360;
    const M = 357.52911 + T * (35999.05029 - 0.0001537 * T);
    const e = 0.016708634 - T * (0.000042037 + 0.0000001267 * T);
    const C = Math.sin(M * RAD) * (1.914602 - T * (0.004817 + 0.000014 * T)) +
      Math.sin(2 * M * RAD) * (0.019993 - 0.000101 * T) + Math.sin(3 * M * RAD) * 0.000289;
    const omega = 125.04 - 1934.136 * T;
    const dlugoscSlonca = L0 + C - 0.00569 - 0.00478 * Math.sin(omega * RAD);
    const nachylenie = 23 + (26 + (21.448 - T * (46.815 + T * (0.00059 - T * 0.001813))) / 60) / 60 +
      0.00256 * Math.cos(omega * RAD);
    const deklinacja = Math.asin(Math.sin(nachylenie * RAD) * Math.sin(dlugoscSlonca * RAD));
    const y = Math.tan(nachylenie * RAD / 2) ** 2;
    const rownanieCzasu = 4 / RAD * (y * Math.sin(2 * L0 * RAD) - 2 * e * Math.sin(M * RAD) +
      4 * e * y * Math.sin(M * RAD) * Math.cos(2 * L0 * RAD) - 0.5 * y * y * Math.sin(4 * L0 * RAD) -
      1.25 * e * e * Math.sin(2 * M * RAD));
    const cosH = Math.cos(ZENIT * RAD) / (Math.cos(szerokosc * RAD) * Math.cos(deklinacja)) -
      Math.tan(szerokosc * RAD) * Math.tan(deklinacja);
    // dzień albo noc polarna: kąt przycięty, żeby nie było NaN
    const H = Math.acos(Math.min(1, Math.max(-1, cosH))) / RAD;
    return 720 - 4 * dlugosc - rownanieCzasu + znak * 4 * H;
  };
  // dwa przybliżenia: najpierw dla południa, potem dla samej chwili wschodu albo zachodu
  return licz(licz(720 - 4 * dlugosc));
}

// wschód i zachód słońca w dniu kalendarzowym (czas lokalny), z którego jest „dzien”
function wschodZachod(dzien, szerokosc, dlugosc) {
  const polnocUTC = Date.UTC(dzien.getFullYear(), dzien.getMonth(), dzien.getDate());
  const chwila = (znak) => new Date(polnocUTC + minutyUTC(polnocUTC, szerokosc, dlugosc, znak) * 6e4);
  return { wschod: chwila(-1), zachod: chwila(1) };
}

// Noc trwająca albo najbliższa: od zachodu do wschodu. Noc trwa, gdy teraz >= od
// (zawsze teraz < do).
function noc(teraz, szerokosc, dlugosc) {
  const dzien = (przesuniecie) => new Date(teraz.getFullYear(), teraz.getMonth(), teraz.getDate() + przesuniecie);
  const dzis = wschodZachod(dzien(0), szerokosc, dlugosc);
  if (teraz < dzis.wschod) return { od: wschodZachod(dzien(-1), szerokosc, dlugosc).zachod, do: dzis.wschod };
  return { od: dzis.zachod, do: wschodZachod(dzien(1), szerokosc, dlugosc).wschod };
}

// Do testów: udawany czas tylko dla trybu dzień i noc, np. „15:57” (dziś)
// albo „2026-11-19T15:57”. Zwraca przesunięcie w ms; zegar biegnie od niego dalej.
function przesuniecie(tekst) {
  if (!tekst) return 0;
  const godzina = String(tekst).trim().match(/^(\d{1,2}):(\d{2})$/);
  let cel;
  if (godzina) {
    cel = new Date();
    cel.setHours(Number(godzina[1]), Number(godzina[2]), 0, 0);
  } else {
    cel = new Date(String(tekst).trim());
  }
  if (isNaN(cel)) {
    console.error('LIVEPL_CZAS_MOTYWU: nie rozumiem czasu', tekst);
    return 0;
  }
  return cel.getTime() - Date.now();
}

module.exports = { wschodZachod, noc, przesuniecie };
