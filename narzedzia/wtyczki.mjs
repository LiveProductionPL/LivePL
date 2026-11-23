// Publikacja wtyczek LivePL: zbiera wtyczki z folderu wtyczki/, składa lista.json i podpisuje ją kluczem aktualizacji.
// Uruchom:  npm run wtyczki
// Potem wgraj wydanie-wtyczki/ (lista.json, lista.json.sig, wgraj-livepl-wtyczki.sh) na serwer:
//   sudo bash wgraj-livepl-wtyczki.sh <folder> <sha lista.json> <sha lista.json.sig>
//
// Wtyczka to folder wtyczki/<id>/ z plikiem wtyczka.json:
//   { "nazwa": "…", "opis": "…", "wersja": "1", "domyslnie": true, "strony": ["appstoreconnect.apple.com", "*.apple.com"] }
// i (co najmniej jednym z) styl.css, skrypt.js. Skrypt działa w izolowanym świecie: widzi DOM strony, nie jej zmienne.
// Strony: „host” dokładnie, „*.domena” (domena i poddomeny) albo „*” (każda strona).

import { createPrivateKey, sign, createHash } from 'node:crypto';
import { readFileSync, writeFileSync, readdirSync, existsSync, mkdirSync, rmSync, copyFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const katalog = join(dirname(fileURLToPath(import.meta.url)), '..');
const zrodla = join(katalog, 'wtyczki');
const wyjscie = join(katalog, 'wydanie-wtyczki');
const MAKS_KOD = 200 * 1024;

const wtyczki = [];
for (const id of existsSync(zrodla) ? readdirSync(zrodla).sort() : []) {
  const folder = join(zrodla, id);
  const plik = join(folder, 'wtyczka.json');
  if (!existsSync(plik)) continue;
  if (!/^[a-z0-9-]{1,40}$/.test(id)) throw new Error(`Nazwa folderu wtyczki „${id}”: tylko małe litery, cyfry i myślniki (do 40 znaków)`);
  const opis = JSON.parse(readFileSync(plik, 'utf8'));
  if (!opis.nazwa || !Array.isArray(opis.strony) || !opis.strony.length) throw new Error(`${id}: brak nazwy albo listy stron`);
  for (const s of opis.strony) if (!/^(\*|\*\.)?[a-z0-9.-]*$/i.test(s)) throw new Error(`${id}: zła strona „${s}”`);
  const w = { id, nazwa: opis.nazwa, opis: opis.opis || '', wersja: String(opis.wersja || '1'), domyslnie: opis.domyslnie !== false, strony: opis.strony };
  for (const [pole, nazwaPliku] of [['css', 'styl.css'], ['js', 'skrypt.js']]) {
    const p = join(folder, nazwaPliku);
    if (!existsSync(p)) continue;
    const kod = readFileSync(p, 'utf8');
    if (kod.length > MAKS_KOD) throw new Error(`${id}: ${nazwaPliku} ma ponad 200 kB`);
    w[pole] = kod;
  }
  if (!w.css && !w.js) throw new Error(`${id}: brak styl.css i skrypt.js`);
  wtyczki.push(w);
}

const lista = Buffer.from(JSON.stringify({ format: 1, data: new Date().toISOString(), wtyczki }, null, 2) + '\n');
const sciezkaKlucza = process.env.LIVEPL_KLUCZ_WYDAN || join(homedir(), 'LivePL-klucze', 'aktualizacje-prywatny.pem');
const podpis = sign(null, lista, createPrivateKey(readFileSync(sciezkaKlucza))).toString('base64');

rmSync(wyjscie, { recursive: true, force: true });
mkdirSync(wyjscie, { recursive: true });
writeFileSync(join(wyjscie, 'lista.json'), lista);
writeFileSync(join(wyjscie, 'lista.json.sig'), podpis + '\n');
copyFileSync(join(katalog, 'narzedzia', 'serwer', 'wgraj-livepl-wtyczki.sh'), join(wyjscie, 'wgraj-livepl-wtyczki.sh'));

const sha = (p) => createHash('sha256').update(readFileSync(p)).digest('hex');
console.log(`Wtyczki LivePL: ${wtyczki.length} (${wtyczki.map(w => w.id).join(', ') || 'brak'})`);
console.log(`Folder do wgrania: ${wyjscie}`);
console.log(`  lista.json     ${sha(join(wyjscie, 'lista.json'))}`);
console.log(`  lista.json.sig ${sha(join(wyjscie, 'lista.json.sig'))}`);
