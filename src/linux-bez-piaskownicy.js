'use strict';

// Linux, plik AppImage na Ubuntu 23.10 i nowszym: system (AppArmor) nie pozwala włączyć piaskownicy Chromium,
// więc przeglądarki nie uruchamiamy (strony nie byłyby od siebie odizolowane). AppRun (narzedzia/linux/livepl.sh)
// startuje wtedy LivePL z --no-sandbox i --livepl-bez-piaskownicy, a my pokazujemy tylko okienko systemu (GTK),
// bez żadnej strony internetowej, z prośbą o instalację z pakietu .deb.

const { app, dialog, shell } = require('electron');

const STRONA = 'https://app.livepl.eu/';

function pokaz() {
  app.whenReady().then(async () => {
    const { response } = await dialog.showMessageBox({
      type: 'warning',
      title: 'LivePL',
      message: 'Zainstaluj LivePL z pakietu .deb',
      detail: 'Ten Linux (Ubuntu 23.10 lub nowszy) nie pozwala programom z plików AppImage włączyć piaskownicy, '
        + 'która oddziela od siebie otwarte strony. Bez niej LivePL nie byłby bezpieczny, więc z tego pliku się nie uruchomi.\n\n'
        + 'Pobierz ze strony app.livepl.eu pakiet .deb i otwórz go: Ubuntu zainstaluje LivePL razem z potrzebną zgodą systemu.',
      buttons: ['Otwórz app.livepl.eu', 'Zamknij'],
      defaultId: 0,
      cancelId: 1,
      noLink: true
    });
    if (response === 0) await shell.openExternal(STRONA).catch(() => {});
    app.exit(0);
  });
}

module.exports = { pokaz };
