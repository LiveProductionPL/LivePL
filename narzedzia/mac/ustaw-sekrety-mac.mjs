// Wpisuje do GitHuba (LiveProductionPL/LivePL) certyfikat Developer ID do podpisu LivePL na Maca.
// Uruchamia go właściciel konta, nie AI: wartości idą prosto z plików do `gh secret set` i nigdzie się nie wyświetlają.
//
//   node narzedzia/mac/ustaw-sekrety-mac.mjs
//
// Pliki robi narzedzia/mac/developer-id.mjs (%USERPROFILE%\LivePL-klucze\apple). Klucz App Store Connect do
// notaryzacji (ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8) jest już w GitHubie, ten sam co dla iPhone'a.

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const REPO = 'LiveProductionPL/LivePL';
const FOLDER = path.join(os.homedir(), 'LivePL-klucze', 'apple');
const sekrety = {
  MAC_CERT_P12: fs.readFileSync(path.join(FOLDER, 'developer-id.p12')).toString('base64'),
  MAC_CERT_HASLO: fs.readFileSync(path.join(FOLDER, 'developer-id-haslo.txt'), 'utf8').trim()
};
for (const [nazwa, wartosc] of Object.entries(sekrety)) {
  const w = spawnSync('gh', ['secret', 'set', nazwa, '--repo', REPO], { input: wartosc, encoding: 'utf8' });
  if (w.status !== 0) {
    console.error(`Nie udało się ustawić ${nazwa}: ${(w.stderr || '').trim()}`);
    process.exit(1);
  }
  console.log(`ustawiony: ${nazwa}`);
}
console.log('\nGotowe. Następna budowa „LivePL na Maca” będzie podpisana i sprawdzona przez Apple.');
