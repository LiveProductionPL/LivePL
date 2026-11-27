'use strict';

// Start LivePL: najpierw wgrywa pobraną aktualizację (albo wraca do poprzedniej wersji),
// a dopiero potem wczytuje program. Po podmianie plików uruchamia się jeszcze raz,
// żeby wszystko działało już w nowej wersji.

const { app } = require('electron');
const path = require('path');

app.setName('LivePL');
// LIVEPL_DANE: osobny folder danych, np. do testów obok zwykłego LivePL
if (process.env.LIVEPL_DANE) app.setPath('userData', path.resolve(process.env.LIVEPL_DANE));

// Instalator i odinstalowanie: osobny tryb z własnym oknem, bez blokady „jedno okno”
// (działający LivePL nie może go zablokować) i bez podmiany plików.
const trybInstalatora = process.argv.includes('--instalator') ? 'instaluj' : process.argv.includes('--odinstaluj') ? 'odinstaluj' : null;
const WBUDOWANY = path.join(__dirname, '..');
const LINUX = process.platform === 'linux';

// AppImage: pliki programu leżą w folderze tymczasowym, który znika razem z procesem,
// więc ponownie uruchamiamy sam plik AppImage (zob. też uruchomPonownie w main.js)
function uruchomJeszczeRaz() {
  if (process.env.APPIMAGE) app.relaunch({ execPath: process.env.APPIMAGE, args: process.argv.slice(1) });
  else app.relaunch();
  app.exit(0);
}

// Drugie uruchomienie tylko pokazuje okno pierwszego. Pliki podmienia wyłącznie pierwsze,
// żeby nie zmieniać ich pod działającym programem.
if (LINUX && process.argv.includes('--livepl-bez-piaskownicy')) {
  // AppImage na Ubuntu 23.10+: tylko okienko z prośbą o pakiet .deb (zob. linux-bez-piaskownicy.js)
  require('./linux-bez-piaskownicy').pokaz();
} else if (trybInstalatora) {
  require('./instalator').uruchom(trybInstalatora);
} else if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  let podmieniono = null;
  let katalog = WBUDOWANY;
  try {
    // Linux: aktualizacje w folderze użytkownika (zob. nakladka-linux.js)
    if (LINUX) katalog = require('./nakladka-linux').przygotuj(WBUDOWANY, app.getPath('userData'));
    // na Macu nie podmieniamy plików w podpisanej aplikacji (aktualizacja to nowy .dmg, zob. aktualizacje.js)
    if (process.platform !== 'darwin') podmieniono = require('./aktualizacja-wgraj').przedStartem(katalog, app.getPath('userData'));
    if (LINUX) katalog = require('./nakladka-linux').posprzataj(WBUDOWANY, app.getPath('userData'));
  } catch (e) {
    console.error('Aktualizacja LivePL:', e);
    katalog = WBUDOWANY;
  }
  if (podmieniono) {
    uruchomJeszczeRaz();
  } else if (katalog !== WBUDOWANY) {
    // wersja z nakładki: app.getVersion() podaje wtedy wersję wczytanych plików, nie wbudowanych
    const wersja = require('./nakladka-linux').wersja(katalog);
    if (wersja) app.getVersion = () => wersja;
    require(path.join(katalog, 'src', 'main.js'));
  } else {
    require('./main.js');
  }
}
