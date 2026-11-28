// Wydanie nowej wersji LivePL: pakuje kod programu, podpisuje i zapisuje w folderze wydanie/.
// Uruchom:  npm run wydanie -- 0.2.0 "Krótki opis zmian"
// Potem wgraj zawartość folderu wydanie/ na serwer aktualizacji (aktualizacje.livepl.eu/livepl/).
import { createHash, createPrivateKey, sign } from 'node:crypto';
import { readFileSync, writeFileSync, readdirSync, statSync, mkdirSync, rmSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { homedir } from 'node:os';
import { join, dirname, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const katalog = join(dirname(fileURLToPath(import.meta.url)), '..');
const { porownaj, dozwolonaSciezka } = require('../src/aktualizacja-wgraj.js');

const [, , nowaWersja, ...opis] = process.argv;
const plikPakietu = join(katalog, 'package.json');
const pakiet = JSON.parse(readFileSync(plikPakietu, 'utf8'));

if (nowaWersja) {
  if (!/^\d+\.\d+\.\d+$/.test(nowaWersja)) throw new Error('Wersja ma wyglądać tak: 0.2.0');
  if (porownaj(nowaWersja, pakiet.version) <= 0) throw new Error(`Nowa wersja musi być wyższa niż ${pakiet.version}`);
  pakiet.version = nowaWersja;
  writeFileSync(plikPakietu, JSON.stringify(pakiet, null, 2) + '\n');
}
const wersja = pakiet.version;

const sciezkaKlucza = process.env.LIVEPL_KLUCZ_WYDAN || join(homedir(), 'LivePL-klucze', 'aktualizacje-prywatny.pem');
const klucz = createPrivateKey(readFileSync(sciezkaKlucza));

function zbierz(folder) {
  const wynik = [];
  for (const nazwa of readdirSync(folder)) {
    const pelna = join(folder, nazwa);
    if (statSync(pelna).isDirectory()) wynik.push(...zbierz(pelna));
    else wynik.push(pelna);
  }
  return wynik;
}

const pliki = [];
for (const k of ['src', 'ui', 'assets']) pliki.push(...zbierz(join(katalog, k)));
for (const p of ['package.json', 'programy.json', 'README.md']) pliki.push(join(katalog, p));

const sha = (b) => createHash('sha256').update(b).digest('hex');
const wpisy = pliki.map((pelna) => {
  const sciezka = relative(katalog, pelna).split(sep).join('/');
  if (!dozwolonaSciezka(sciezka)) throw new Error('Plik spoza programu: ' + sciezka);
  const tresc = readFileSync(pelna);
  return { sciezka, sha256: sha(tresc), dane: tresc.toString('base64') };
});

const paczka = gzipSync(Buffer.from(JSON.stringify({ produkt: 'LivePL', wersja, pliki: wpisy })), { level: 9 });
const nazwaPaczki = `livepl-${wersja}.json.gz`;
const silnik = require('electron/package.json').version;
const daneManifestu = {
  produkt: 'LivePL',
  wersja,
  plik: nazwaPaczki,
  rozmiar: paczka.length,
  sha256: sha(paczka),
  minSilnik: silnik.split('.')[0] + '.0.0',
  data: new Date().toISOString(),
  notatki: opis.join(' ').trim()
};
// LivePL na Maca nie podmienia plików (podpis Apple), tylko proponuje nowy plik .dmg z app.livepl.eu/pobierz/.
// LIVEPL_MAC_DMG=<ścieżka do LivePL-x.y.z.dmg> dopisuje pole „mac” (zob. wersjaNaMaca w src/aktualizacje.js),
// a LIVEPL_MAC_NOTATKI jego opis, gdy plik na Maca jest z wcześniejszego wydania niż paczka dla Windows i Linuxa.
if (process.env.LIVEPL_MAC_DMG) {
  const dmg = readFileSync(process.env.LIVEPL_MAC_DMG);
  const nazwa = process.env.LIVEPL_MAC_DMG.split(/[\\/]/).pop();
  const m = /^LivePL-(\d+\.\d+\.\d+)\.dmg$/.exec(nazwa);
  if (!m) throw new Error('Plik na Maca musi się nazywać LivePL-x.y.z.dmg: ' + nazwa);
  daneManifestu.mac = { wersja: m[1], plik: 'https://app.livepl.eu/pobierz/' + nazwa, rozmiar: dmg.length, sha256: sha(dmg), notatki: process.env.LIVEPL_MAC_NOTATKI || daneManifestu.notatki };
}
const manifest = Buffer.from(JSON.stringify(daneManifestu, null, 2));
const podpis = sign(null, manifest, klucz).toString('base64');

const wyjscie = join(katalog, 'wydanie');
rmSync(wyjscie, { recursive: true, force: true });
mkdirSync(wyjscie, { recursive: true });
writeFileSync(join(wyjscie, nazwaPaczki), paczka);
writeFileSync(join(wyjscie, 'manifest.json'), manifest);
writeFileSync(join(wyjscie, 'manifest.json.sig'), podpis + '\n');

// kopia w Dokumenty\LivePL\Wydania\LivePL\<wersja>\na-serwer\aktualizacja (tam leżą wszystkie gotowe wersje)
const archiwum = join(katalog, '..', 'Wydania', 'LivePL', wersja, 'na-serwer', 'aktualizacja');
rmSync(archiwum, { recursive: true, force: true });
mkdirSync(archiwum, { recursive: true });
for (const n of [nazwaPaczki, 'manifest.json', 'manifest.json.sig']) writeFileSync(join(archiwum, n), readFileSync(join(wyjscie, n)));

console.log(`Wydanie LivePL ${wersja}: ${wpisy.length} plików, paczka ${(paczka.length / 1024).toFixed(0)} KB`);
if (daneManifestu.mac) console.log(`Mac: ${daneManifestu.mac.plik} (${daneManifestu.mac.wersja}, SHA-256 ${daneManifestu.mac.sha256})`);
console.log('Folder do wgrania: ' + wyjscie);
console.log('Kopia: ' + archiwum);
