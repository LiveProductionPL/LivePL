// Wpisuje do GitHuba (LiveProductionPL/LivePL) sekrety LivePL na Androida.
// Uruchamia go właściciel konta, nie AI: wartości idą prosto z plików do `gh secret set` i nigdzie się nie wyświetlają.
//
//   node android/narzedzia/ustaw-sekrety.mjs                    klucze podpisu z %USERPROFILE%\LivePL-klucze\android
//   node android/narzedzia/ustaw-sekrety.mjs --play <plik.json>  dodatkowo klucz konta usługi Google Play
//   node android/narzedzia/ustaw-sekrety.mjs --firebase <google-services.json>  dodatkowo Firebase TVML (push)
//
// Klucze tworzy zrob-klucz.mjs (bez argumentu: klucz aplikacji, z „przesylanie”: klucz przesyłania do Play,
// z „tvml”: osobny klucz aplikacji TVML).

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const REPO = 'LiveProductionPL/LivePL';
const FOLDER = path.join(os.homedir(), 'LivePL-klucze', 'android');
const plik = (nazwa) => path.join(FOLDER, nazwa);

const sekrety = {
  ANDROID_KLUCZ: fs.readFileSync(plik('livepl.keystore')).toString('base64'),
  ANDROID_KLUCZ_HASLO: fs.readFileSync(plik('livepl-haslo.txt'), 'utf8').trim(),
  ANDROID_KLUCZ_ALIAS: 'livepl',
};
if (fs.existsSync(plik('livepl-przesylanie.keystore'))) {
  sekrety.ANDROID_PRZESYLANIE = fs.readFileSync(plik('livepl-przesylanie.keystore')).toString('base64');
  sekrety.ANDROID_PRZESYLANIE_HASLO = fs.readFileSync(plik('livepl-przesylanie-haslo.txt'), 'utf8').trim();
}
if (fs.existsSync(plik('tvml.keystore'))) {
  sekrety.ANDROID_KLUCZ_TVML = fs.readFileSync(plik('tvml.keystore')).toString('base64');
  sekrety.ANDROID_KLUCZ_TVML_HASLO = fs.readFileSync(plik('tvml-haslo.txt'), 'utf8').trim();
}
const i = process.argv.indexOf('--play');
if (i > 0) {
  const json = fs.readFileSync(process.argv[i + 1], 'utf8');
  if (!JSON.parse(json).client_email) throw new Error('To nie wygląda na klucz konta usługi Google (brak client_email).');
  sekrety.PLAY_KONTO_USLUGI = json;
}
const f = process.argv.indexOf('--firebase');
if (f > 0) {
  const json = fs.readFileSync(process.argv[f + 1], 'utf8');
  const d = JSON.parse(json);
  const pakiety = (d.client || []).map((k) => k.client_info?.android_client_info?.package_name);
  if (!pakiety.includes('pl.tvml')) throw new Error('To nie jest google-services.json aplikacji pl.tvml (projekt tvml-3f2b5).');
  sekrety.TVML_FIREBASE = json;
}

for (const [nazwa, wartosc] of Object.entries(sekrety)) {
  const w = spawnSync('gh', ['secret', 'set', nazwa, '--repo', REPO], { input: wartosc, encoding: 'utf8' });
  if (w.status !== 0) {
    console.error(`Nie udało się ustawić ${nazwa}: ${(w.stderr || '').trim()}`);
    process.exit(1);
  }
  console.log(`ustawiony: ${nazwa}`);
}
console.log('\nGotowe. Następna zmiana w android/ na gałęzi android zbuduje podpisany APK i AAB (artefakt livepl-wydanie-N).');
if (sekrety.PLAY_KONTO_USLUGI) console.log('Zmiana z [play] w opisie wyśle też AAB do Google Play (test wewnętrzny, jako szkic).');
