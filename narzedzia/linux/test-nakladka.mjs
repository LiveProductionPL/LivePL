// Test aktualizacji LivePL na Linuxie (src/nakladka-linux.js razem z src/aktualizacja-wgraj.js) na zwykłych folderach,
// bez Electrona. Działa na każdym systemie:  node narzedzia/linux/test-nakladka.mjs
import { createRequire } from 'node:module';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const SRC = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'src');
const { przygotuj, posprzataj, wersja } = require(join(SRC, 'nakladka-linux.js'));
const { przedStartem } = require(join(SRC, 'aktualizacja-wgraj.js'));

let bledy = 0;
const ok = (nazwa, warunek, szczegoly = '') => {
  console.log(`${warunek ? 'OK  ' : 'BŁĄD'} ${nazwa}${!warunek && szczegoly ? ' — ' + szczegoly : ''}`);
  if (!warunek) bledy++;
};

function program(katalog, v, tekst) {
  mkdirSync(join(katalog, 'src'), { recursive: true });
  mkdirSync(join(katalog, 'ui'), { recursive: true });
  mkdirSync(join(katalog, 'assets'), { recursive: true });
  writeFileSync(join(katalog, 'package.json'), JSON.stringify({ name: 'przegladarka-livepl', version: v }));
  writeFileSync(join(katalog, 'src', 'main.js'), `// ${tekst}\n`);
  writeFileSync(join(katalog, 'ui', 'powloka.html'), tekst);
  writeFileSync(join(katalog, 'assets', 'ikona.txt'), 'ikona ' + v);
}

// pobrana aktualizacja, tak jak zostawia ją aktualizacje.js (folder gotowa z plikami i opisem)
function pobrana(dane, v, tekst) {
  const g = join(dane, 'aktualizacja', 'gotowa');
  rmSync(g, { recursive: true, force: true });
  program(join(g, 'pliki'), v, tekst);
  writeFileSync(join(g, 'gotowa.json'), JSON.stringify({ wersja: v, pliki: ['package.json', 'src/main.js', 'ui/powloka.html'] }));
}

// to samo co start.js na Linuxie
function start(wbudowany, dane) {
  const katalog = przygotuj(wbudowany, dane);
  const podmieniono = przedStartem(katalog, dane);
  return { katalog: posprzataj(wbudowany, dane), podmieniono };
}

const tmp = mkdtempSync(join(tmpdir(), 'livepl-nakladka-'));
const wbud = join(tmp, 'opt', 'LivePL', 'resources', 'app');
const dane = join(tmp, 'config', 'LivePL');
const nakl = join(dane, 'program');
program(wbud, '0.1.27', 'wbudowany 0.1.27');
mkdirSync(dane, { recursive: true });

// 1. zwykły start bez aktualizacji: wbudowane pliki, bez nakładki
let s = start(wbud, dane);
ok('bez aktualizacji: wbudowane pliki', s.katalog === wbud && !s.podmieniono && !existsSync(nakl));

// 2. pobrana 0.1.28: nakładka z kopii wbudowanej + nowe pliki, wbudowane bez zmian
pobrana(dane, '0.1.28', 'aktualizacja 0.1.28');
s = start(wbud, dane);
ok('aktualizacja: podmieniono do 0.1.28', s.podmieniono === '0.1.28', String(s.podmieniono));
ok('aktualizacja: nakładka ma 0.1.28', wersja(nakl) === '0.1.28');
ok('aktualizacja: nowe pliki w nakładce', readFileSync(join(nakl, 'ui', 'powloka.html'), 'utf8') === 'aktualizacja 0.1.28');
ok('aktualizacja: pliki spoza paczki skopiowane z wbudowanej', existsSync(join(nakl, 'assets', 'ikona.txt')));
ok('aktualizacja: wbudowane pliki bez zmian', wersja(wbud) === '0.1.27' && readFileSync(join(wbud, 'ui', 'powloka.html'), 'utf8') === 'wbudowany 0.1.27');

// 3. ponowne uruchomienie po podmianie: wczytuje nakładkę
s = start(wbud, dane);
ok('po podmianie: start z nakładki', s.katalog === nakl && !s.podmieniono);

// 4. kolejna aktualizacja 0.1.29 idzie do istniejącej nakładki
pobrana(dane, '0.1.29', 'aktualizacja 0.1.29');
s = start(wbud, dane);
ok('druga aktualizacja: 0.1.29 w nakładce', s.podmieniono === '0.1.29' && wersja(nakl) === '0.1.29');
ok('druga aktualizacja: kopia 0.1.28 do powrotu', JSON.parse(readFileSync(join(dane, 'aktualizacja', 'poprzednia', 'wersja.json'), 'utf8')).wersja === '0.1.28');

// 5. powrót do poprzedniej (0.1.28)
writeFileSync(join(dane, 'aktualizacja', 'przywroc.json'), '{}');
s = start(wbud, dane);
ok('powrót: 0.1.28 w nakładce', s.podmieniono === '0.1.28' && wersja(nakl) === '0.1.28');

// 6. nowy AppImage/.deb 0.1.30 (nowszy od nakładki): wbudowane pliki, nakładka usunięta
program(wbud, '0.1.30', 'wbudowany 0.1.30');
s = start(wbud, dane);
ok('nowszy AppImage/.deb: wbudowane pliki', s.katalog === wbud && !existsSync(nakl));

// 7. pobrana starsza wersja niż wbudowana: odrzucona, bez nakładki
pobrana(dane, '0.1.29', 'stara 0.1.29');
s = start(wbud, dane);
ok('starsza paczka: odrzucona', s.katalog === wbud && !s.podmieniono && !existsSync(nakl) && !existsSync(join(dane, 'aktualizacja', 'gotowa')));

// 8. powrót z pierwszej aktualizacji do wersji wbudowanej: nakładka znika
pobrana(dane, '0.1.31', 'aktualizacja 0.1.31');
start(wbud, dane);
writeFileSync(join(dane, 'aktualizacja', 'przywroc.json'), '{}');
s = start(wbud, dane);
ok('powrót do wbudowanej: nakładka usunięta', s.podmieniono === '0.1.30' && !existsSync(nakl));
s = start(wbud, dane);
ok('powrót do wbudowanej: start z wbudowanych', s.katalog === wbud);

// 9. ścieżki spoza programu w paczce: odrzucone, nic nie wyszło poza nakładkę
pobrana(dane, '0.1.32', 'zła 0.1.32');
writeFileSync(join(dane, 'aktualizacja', 'gotowa', 'gotowa.json'), JSON.stringify({ wersja: '0.1.32', pliki: ['../../../.bashrc'] }));
s = start(wbud, dane);
ok('zła paczka: odrzucona', !s.podmieniono && s.katalog === wbud && !existsSync(nakl));

rmSync(tmp, { recursive: true, force: true });
console.log(bledy ? `\n${bledy} BŁĘDÓW` : '\nWszystko OK');
process.exit(bledy ? 1 : 0);
