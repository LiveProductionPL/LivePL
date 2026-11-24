// Budowa nieobowiązkowego dodatku LivePL (osobny plik .livepl-dodatek, podpisany kluczem aktualizacji).
// Uruchom:  npm run dodatek -- rozszerzenia-chrome
// Wynik: wydanie-dodatki/<plik>-<wersja>.livepl-dodatek. Plik wgrywamy na app.livepl.eu/pobierz/ obok instalatorów;
// LivePL po jego pobraniu sam pyta, czy zainstalować (src/dodatki.js sprawdza podpis przed uruchomieniem).
//
// Dodatek to folder dodatki/<id>/ z plikiem dodatek.json:
//   { "nazwa": "…", "wersja": "1.0.0", "opis": "…", "minLivePL": "0.1.20", "plik": "LivePL-…", "glowny": "modul.js" }
// i jednym modułem Node (glowny) z funkcjami start(sesja) i zatrzymaj(). Moduł może używać tylko modułów
// wbudowanych (electron, fs, path, crypto, zlib…), bez require do własnych plików.

import { createPrivateKey, createPublicKey, sign, verify, createHash } from 'node:crypto';
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const katalog = join(dirname(fileURLToPath(import.meta.url)), '..');
const { KLUCZ_PUBLICZNY } = require('../src/klucz-aktualizacji.js');
const PRZEDROSTEK = 'LivePL-dodatek-1\n'; // to samo co w src/dodatki.js

const id = process.argv[2];
if (!id || !/^[a-z0-9-]{1,40}$/.test(id)) {
  console.error('Użycie: npm run dodatek -- <id>   (folder dodatki/<id>/ z plikiem dodatek.json)');
  process.exit(1);
}
const folder = join(katalog, 'dodatki', id);
const opis = JSON.parse(readFileSync(join(folder, 'dodatek.json'), 'utf8'));
for (const pole of ['nazwa', 'wersja', 'minLivePL', 'plik', 'glowny']) {
  if (!opis[pole] || typeof opis[pole] !== 'string') throw new Error(`dodatek.json: brak pola „${pole}”`);
}
if (!/^\d+\.\d+\.\d+$/.test(opis.wersja)) throw new Error('dodatek.json: wersja w postaci X.Y.Z');
const kod = readFileSync(join(folder, opis.glowny), 'utf8');
const wlasne = kod.match(/require\(\s*['"]\.{1,2}\//);
if (wlasne) throw new Error(`${opis.glowny}: dodatek nie może mieć require do własnych plików (${wlasne[0]})`);

const dodatek = JSON.stringify({
  id, nazwa: opis.nazwa, wersja: opis.wersja, opis: opis.opis || '', minLivePL: opis.minLivePL,
  data: new Date().toISOString(), kod
});
const dane = Buffer.concat([Buffer.from(PRZEDROSTEK, 'utf8'), Buffer.from(dodatek, 'utf8')]);
const sciezkaKlucza = process.env.LIVEPL_KLUCZ_WYDAN || join(homedir(), 'LivePL-klucze', 'aktualizacje-prywatny.pem');
const prywatny = createPrivateKey(readFileSync(sciezkaKlucza));
const podpis = sign(null, dane, prywatny);
// klucz wydań musi pasować do klucza wbudowanego w LivePL, inaczej żaden LivePL nie przyjmie dodatku
if (!verify(null, dane, createPublicKey(KLUCZ_PUBLICZNY), podpis)) throw new Error('klucz wydań nie pasuje do klucza w src/klucz-aktualizacji.js');

const wyjscie = join(katalog, 'wydanie-dodatki');
if (!existsSync(wyjscie)) mkdirSync(wyjscie, { recursive: true });
const nazwaPliku = `${opis.plik}-${opis.wersja}.livepl-dodatek`;
const plik = join(wyjscie, nazwaPliku);
writeFileSync(plik, JSON.stringify({ format: 1, dodatek, podpis: podpis.toString('base64') }) + '\n');

const tresc = readFileSync(plik);
console.log(`Dodatek ${opis.nazwa} ${opis.wersja} (wymaga LivePL ${opis.minLivePL})`);
console.log(`  ${plik}`);
console.log(`  ${(tresc.length / 1024).toFixed(1)} kB, SHA-256 ${createHash('sha256').update(tresc).digest('hex')}`);
