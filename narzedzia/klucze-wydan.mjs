// Jednorazowo: tworzy parę kluczy do podpisywania wydań LivePL.
// Klucz prywatny trafia do C:\Users\<ty>\LivePL-klucze (poza OneDrive), publiczny do src/klucz-aktualizacji.js.
// Uruchom: npm run klucze
import { generateKeyPairSync } from 'node:crypto';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const katalog = join(dirname(fileURLToPath(import.meta.url)), '..');
const folderKluczy = process.env.LIVEPL_KLUCZE || join(homedir(), 'LivePL-klucze');
const prywatny = join(folderKluczy, 'aktualizacje-prywatny.pem');

if (existsSync(prywatny)) {
  console.error('Klucz już istnieje: ' + prywatny + '\nNie nadpisuję go. Nowy klucz unieważniłby wszystkie zainstalowane LivePL.');
  process.exit(1);
}

const { publicKey, privateKey } = generateKeyPairSync('ed25519');
mkdirSync(folderKluczy, { recursive: true });
writeFileSync(prywatny, privateKey.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600 });
const pem = publicKey.export({ type: 'spki', format: 'pem' }).trim();
writeFileSync(join(katalog, 'src', 'klucz-aktualizacji.js'),
  "'use strict';\n// Klucz publiczny do sprawdzania podpisu aktualizacji LivePL (Ed25519).\n// Prywatny jest tylko na komputerze, z którego wydajemy wersje.\n" +
  'module.exports = {\n  KLUCZ_PUBLICZNY: `' + pem + '`\n};\n');
console.log('Klucz prywatny: ' + prywatny);
console.log('Klucz publiczny zapisany w src/klucz-aktualizacji.js');
