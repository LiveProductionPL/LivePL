// Tworzy klucze LivePL na Androida (raz na zawsze) w %USERPROFILE%\LivePL-klucze\android:
//
//   node android/narzedzia/zrob-klucz.mjs              klucz podpisu aplikacji (livepl.keystore)
//   node android/narzedzia/zrob-klucz.mjs przesylanie  klucz przesyłania do Google Play (livepl-przesylanie.keystore)
//   node android/narzedzia/zrob-klucz.mjs tvml         osobny klucz podpisu aplikacji TVML (tvml.keystore)
//
// Hasło jest losowe i leży w pliku obok klucza. Skrypt niczego nie wypisuje poza odciskiem certyfikatu,
// który jest publiczny. Klucz przesyłania dostaje też certyfikat .pem do wgrania w Play Console.
//
// Potrzebny keytool z Javy 17+ (JAVA_HOME albo PATH). Istniejącego klucza nie nadpisuje:
// zgubiony albo zmieniony klucz aplikacji oznacza, że zainstalowanej LivePL nie da się już zaktualizować.

import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const przesylanie = process.argv[2] === 'przesylanie';
const tvml = process.argv[2] === 'tvml';
const FOLDER = path.join(os.homedir(), 'LivePL-klucze', 'android');
const NAZWA = przesylanie ? 'livepl-przesylanie' : (tvml ? 'tvml' : 'livepl');
const KLUCZ = path.join(FOLDER, `${NAZWA}.keystore`);
const HASLO = path.join(FOLDER, `${NAZWA}-haslo.txt`);
const CERTYFIKAT = path.join(FOLDER, `${NAZWA}-certyfikat.pem`);
const ALIAS = przesylanie ? 'przesylanie' : (tvml ? 'tvml' : 'livepl');

if (fs.existsSync(KLUCZ)) {
  console.error(`Klucz już jest: ${KLUCZ}\nNie nadpisuję go.`);
  process.exit(1);
}

const keytool = (() => {
  const nazwa = process.platform === 'win32' ? 'keytool.exe' : 'keytool';
  if (process.env.JAVA_HOME) {
    const p = path.join(process.env.JAVA_HOME, 'bin', nazwa);
    if (fs.existsSync(p)) return p;
  }
  return nazwa;
})();

fs.mkdirSync(FOLDER, { recursive: true });
const haslo = crypto.randomBytes(24).toString('base64url');
fs.writeFileSync(HASLO, haslo + '\n', { mode: 0o600 });
const env = { ...process.env, LIVEPL_HASLO: haslo };

// hasło idzie przez zmienną środowiska, nie w wierszu poleceń
const w = spawnSync(keytool, [
  '-genkeypair', '-v',
  '-keystore', KLUCZ, '-storetype', 'PKCS12',
  '-alias', ALIAS, '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10950',
  '-dname', tvml ? 'CN=TVML, O=Live Production, C=PL' : 'CN=Karol Kowalski, O=Live Production, C=PL',
  '-storepass:env', 'LIVEPL_HASLO', '-keypass:env', 'LIVEPL_HASLO',
], { env, encoding: 'utf8' });

if (w.status !== 0) {
  fs.rmSync(HASLO, { force: true });
  fs.rmSync(KLUCZ, { force: true });
  console.error('keytool nie utworzył klucza:\n' + (w.stderr || w.error || ''));
  process.exit(1);
}

const lista = spawnSync(keytool, ['-list', '-v', '-keystore', KLUCZ, '-alias', ALIAS, '-storepass:env', 'LIVEPL_HASLO'],
  { env, encoding: 'utf8' });
const odcisk = (lista.stdout.match(/SHA256:\s*([0-9A-F:]+)/) || [])[1] || '?';
const sha8 = crypto.createHash('sha256').update(fs.readFileSync(KLUCZ)).digest('hex').slice(0, 8);

console.log(`Klucz: ${KLUCZ} (sha8 pliku ${sha8})`);
console.log(`Hasło: ${HASLO} (${haslo.length} znaków)`);
console.log(`Alias: ${ALIAS}`);
console.log(`Odcisk SHA-256 certyfikatu (publiczny): ${odcisk}`);

if (przesylanie) {
  // publiczny certyfikat klucza przesyłania: Play Console go prosi przy włączaniu podpisywania przez Google Play
  spawnSync(keytool, ['-exportcert', '-rfc', '-keystore', KLUCZ, '-alias', ALIAS, '-file', CERTYFIKAT,
    '-storepass:env', 'LIVEPL_HASLO'], { env, encoding: 'utf8' });
  console.log(`Certyfikat do Play Console: ${CERTYFIKAT}`);
}
console.log('\nZrób kopię klucza i hasła w bezpiecznym miejscu.');
