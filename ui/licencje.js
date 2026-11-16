'use strict';
document.getElementById('zamknij').addEventListener('click', () => window.close());

// licencje silnika leżą obok LivePL.exe (w trybie deweloperskim obok electron.exe); folder podaje main.js
const silnik = new URLSearchParams(location.search).get('silnik');
if (silnik) {
  const adres = (plik) => encodeURI('file:///' + silnik.replace(/\\/g, '/') + '/' + plik);
  document.getElementById('lic-chromium').href = adres('LICENSES.chromium.html');
  document.getElementById('lic-electron').href = adres('LICENSE');
}
