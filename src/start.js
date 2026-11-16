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

// Drugie uruchomienie tylko pokazuje okno pierwszego. Pliki podmienia wyłącznie pierwsze,
// żeby nie zmieniać ich pod działającym programem.
if (trybInstalatora) {
  require('./instalator').uruchom(trybInstalatora);
} else if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  let podmieniono = null;
  try {
    podmieniono = require('./aktualizacja-wgraj').przedStartem(path.join(__dirname, '..'), app.getPath('userData'));
  } catch (e) {
    console.error('Aktualizacja LivePL:', e);
  }
  if (podmieniono) {
    app.relaunch();
    app.exit(0);
  } else {
    require('./main.js');
  }
}
