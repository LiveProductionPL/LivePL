// Wpisuje do GitHuba (LiveProductionPL/LivePL) sekrety potrzebne do wysyłki LivePL do TestFlight.
// Uruchamia go właściciel konta, nie AI: wartości idą prosto z plików do `gh secret set` i nigdzie się nie wyświetlają.
//
//   node ios/narzedzia/ustaw-sekrety.mjs <AuthKey_XXXX.p8 klucza „LivePL GitHub”> <Issuer ID>
//
// Klucz dla GitHuba wystarczy z rolą App Manager (tylko wysyła budowy). Certyfikat i profil
// bierze z %USERPROFILE%\LivePL-klucze\apple (tworzy je apple-konfiguracja.mjs).

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const REPO = 'LiveProductionPL/LivePL';
const FOLDER = path.join(os.homedir(), 'LivePL-klucze', 'apple');
const [plikKlucza, issuerId] = process.argv.slice(2);
if (!plikKlucza || !issuerId) {
  console.error('Użycie: node ios/narzedzia/ustaw-sekrety.mjs <AuthKey_XXXX.p8> <Issuer ID>');
  process.exit(2);
}

const sekrety = {
  APPLE_CERT_P12: fs.readFileSync(path.join(FOLDER, 'dystrybucja.p12')).toString('base64'),
  APPLE_CERT_HASLO: fs.readFileSync(path.join(FOLDER, 'dystrybucja-haslo.txt'), 'utf8').trim(),
  APPLE_PROFIL: fs.readFileSync(path.join(FOLDER, 'LivePL_App_Store.mobileprovision')).toString('base64'),
  ASC_KEY_ID: path.basename(plikKlucza).replace(/^AuthKey_/, '').replace(/\.p8$/, ''),
  ASC_ISSUER_ID: issuerId.trim(),
  ASC_KEY_P8: fs.readFileSync(plikKlucza, 'utf8')
};

for (const [nazwa, wartosc] of Object.entries(sekrety)) {
  const w = spawnSync('gh', ['secret', 'set', nazwa, '--repo', REPO], { input: wartosc, encoding: 'utf8' });
  if (w.status !== 0) {
    console.error(`Nie udało się ustawić ${nazwa}: ${(w.stderr || '').trim()}`);
    process.exit(1);
  }
  console.log(`ustawiony: ${nazwa}`);
}
console.log('\nGotowe. Zmiana z [testflight] w opisie wyśle LivePL do TestFlight.');
