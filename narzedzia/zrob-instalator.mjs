// Buduje dwa instalatory LivePL w stylu LivePL i pakiet z całym programem (silnik i kod):
//  - LivePL-Instalator-<wersja>.exe (45 kB): pakiet pobiera w trakcie instalacji z app.livepl.eu/pobierz/,
//  - LivePL-Instalator-<wersja>-pelny.exe: pakiet ma w środku, działa bez internetu,
//  - LivePL-<wersja>-<8 znaków sumy>.pakiet: ten pakiet do wgrania obok instalatorów. Suma w nazwie sprawia,
//    że nowy pakiet nigdy nie nadpisze starego, z którego korzysta wcześniej pobrany instalator.
// Pakiet (LZMA) robi xz z Git for Windows (narzedzia/pakiet.mjs), programy startowe kompiluje kompilator C#
// wbudowany w Windows. Suma SHA-256 pakietu jest wpisana w oba instalatory.
// Podpis (narzedzia/podpis.mjs): podpisuje certyfikatem Certum każdy niepodpisany .exe/.dll w programie i oba
// instalatory (musi działać zalogowany SimplySign Desktop). Budowa do Wydania zawsze podpisuje i bez certyfikatu
// przerywa się na początku; bez podpisu buduje tylko próbnie do innego folderu (chyba że LIVEPL_PODPIS=certum).
// Uruchom:  node narzedzia/zrob-instalator.mjs [folder-wyjściowy]
// Domyślnie wynik trafia do Dokumenty\LivePL\Wydania\LivePL\<wersja> (pakiet do podfolderu na-serwer),
// a pliki robocze do folderu tymczasowego poza OneDrive.
import { cpSync, copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, statSync, readdirSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { rcedit } from 'rcedit';
import { zrobPakiet } from './pakiet.mjs';
import { wybierzCertyfikat, sprawdzSesje, podpisz, maPodpis } from './podpis.mjs';

// stąd instalator internetowy pobiera pakiet (ten sam folder co instalatory na app.livepl.eu)
const ADRES_PAKIETOW = 'https://app.livepl.eu/pobierz/';

const katalog = join(dirname(fileURLToPath(import.meta.url)), '..');
const pakiet = JSON.parse(readFileSync(join(katalog, 'package.json'), 'utf8'));
const wersja = pakiet.version;
const silnik = join(katalog, 'node_modules', 'electron', 'dist');
// gotowe pliki: Dokumenty\LivePL\Wydania\LivePL\<wersja> (instalatory) i …\na-serwer (pakiet); pliki robocze
// (ok. 600 MB) poza OneDrive, w folderze tymczasowym, albo w LIVEPL_ROBOCZE
const wyjscie = resolve(process.argv[2] || join(katalog, '..', 'Wydania', 'LivePL', wersja));
const naSerwer = join(wyjscie, 'na-serwer');
const robocze = resolve(process.env.LIVEPL_ROBOCZE || join(tmpdir(), `LivePL-budowanie-${wersja}`));
const program = join(robocze, 'LivePL');
const krok = (t) => console.log('  ' + t);

console.log(`Instalator LivePL ${wersja}`);
// Wydania\<wersja> to gotowe, przenoszone wersje: nie nadpisujemy ich po cichu (np. podpisanych niepodpisanymi)
for (const n of [`LivePL-Instalator-${wersja}.exe`, `LivePL-Instalator-${wersja}-pelny.exe`]) {
  if (existsSync(join(wyjscie, n)) && process.env.LIVEPL_NADPISZ !== '1') {
    console.error(`W ${wyjscie} jest już ${n}. Podnieś wersję (npm run wydanie -- x.y.z "…"), podaj inny folder albo ustaw LIVEPL_NADPISZ=1.`);
    process.exit(1);
  }
}
// certyfikat sprawdzamy na początku, żeby nie czekać 9 minut na błąd przy podpisie.
// Do Wydania trafia tylko wersja podpisana („Każdy plik musi być podpisywany certyfikatem”);
// bez podpisu da się zbudować tylko próbnie, do innego folderu podanego w poleceniu.
const doWydan = (wyjscie + '\\').toLowerCase().startsWith((resolve(katalog, '..', 'Wydania') + '\\').toLowerCase());
let certyfikat;
try {
  certyfikat = wybierzCertyfikat(process.env.LIVEPL_PODPIS || (doWydan ? 'certum' : undefined));
  sprawdzSesje(certyfikat); // próbny podpis: certyfikat bywa widoczny także przy wygasłej sesji SimplySign
} catch (e) {
  console.error(e.message);
  if (doWydan) console.error('Do Wydania trafiają tylko podpisane instalatory. Próbnie bez podpisu: node narzedzia/zrob-instalator.mjs <inny folder>');
  process.exit(1);
}
krok(certyfikat ? `podpis: ${certyfikat.opis}` : 'BEZ PODPISU, tylko do prób (wersje do Wydania zawsze są podpisywane)');
rmSync(robocze, { recursive: true, force: true });
mkdirSync(program, { recursive: true });

// 1. silnik (bez electron.exe, który dostaje naszą nazwę, i bez domyślnej aplikacji Electron)
krok('kopiuję silnik…');
for (const nazwa of readdirSync(silnik)) {
  if (['electron.exe', 'LivePL.exe'].includes(nazwa)) continue;
  cpSync(join(silnik, nazwa), join(program, nazwa), { recursive: true });
}
rmSync(join(program, 'resources', 'default_app.asar'), { force: true });

// 2. LivePL.exe z ikoną i opisem firmy
krok('przygotowuję LivePL.exe…');
const exe = join(program, 'LivePL.exe');
copyFileSync(join(silnik, 'electron.exe'), exe);
await rcedit(exe, {
  icon: join(katalog, 'assets', 'wstega-jasna.ico'),
  'file-version': wersja,
  'product-version': wersja,
  'version-string': {
    CompanyName: 'Live Production',
    FileDescription: 'LivePL',
    ProductName: 'LivePL',
    InternalName: 'LivePL',
    OriginalFilename: 'LivePL.exe',
    LegalCopyright: `Copyright © ${new Date().getFullYear()} Live Production Karol Kowalski (licencja MIT)`
  }
});

// 3. program: te same pliki co w paczce aktualizacji (resources/app; aktualizacje podmieniają je tutaj)
krok('kopiuję program…');
const app = join(program, 'resources', 'app');
mkdirSync(app, { recursive: true });
for (const nazwa of ['package.json', 'programy.json', 'README.md']) {
  if (existsSync(join(katalog, nazwa))) copyFileSync(join(katalog, nazwa), join(app, nazwa));
}
for (const nazwa of ['src', 'ui', 'assets']) cpSync(join(katalog, nazwa), join(app, nazwa), { recursive: true });

// 3a. bez tłumaczeń Chromium poza polskim i angielskim (−48 MB przed kompresją): LivePL ma własny polski
// interfejs, a gdy języka systemu brakuje, Chromium bierze angielski (sprawdzone: LivePL normalnie startuje).
// dxcompiler.dll zostaje: bez niego WebGPU zgłasza błąd przy pierwszym użyciu zamiast się wyłączyć.
krok('odchudzam silnik…');
for (const nazwa of readdirSync(join(program, 'locales'))) {
  if (!['pl.pak', 'en-US.pak'].includes(nazwa)) rmSync(join(program, 'locales', nazwa));
}

// 3b. podpis przed spakowaniem (pakiet i jego suma obejmują już podpisane pliki): LivePL.exe i każdy inny
// niepodpisany .exe/.dll/.node (ffmpeg, vulkan, swiftshader, dxcompiler, electron_wer). Windows 11 z włączonym
// Inteligentnym sterowaniem aplikacjami potrafi zablokować niepodpisaną bibliotekę także w podpisanym programie.
// Pliki z własnym podpisem (np. d3dcompiler_47.dll od Microsoftu) zostają bez zmian.
if (certyfikat) {
  const programy = [];
  const szukaj = (d) => {
    for (const n of readdirSync(d, { withFileTypes: true })) {
      const p = join(d, n.name);
      if (n.isDirectory()) szukaj(p);
      else if (/\.(exe|dll|node)$/i.test(n.name) && !maPodpis(p)) programy.push(p);
    }
  };
  szukaj(program);
  krok(`podpisuję ${programy.length} plików programu…`);
  podpisz(programy, certyfikat);
}

// 4. pakiet LZMA (narzedzia/pakiet.mjs): jeden plik do instalatora pełnego i do pobrania przez internetowy
krok('pakuję LZMA (to potrwa kilka minut)…');
mkdirSync(naSerwer, { recursive: true });
for (const stary of readdirSync(naSerwer)) if (/\.pakiet$/.test(stary)) rmSync(join(naSerwer, stary));
const roboczyPakiet = join(robocze, 'LivePL.pakiet');
const p = zrobPakiet(program, roboczyPakiet);
const sha = createHash('sha256').update(readFileSync(roboczyPakiet)).digest('hex');
// nazwa z sumą: inny pakiet tej samej wersji (np. po dodaniu podpisu) nie nadpisze tego na serwerze
const nazwaPakietu = `LivePL-${wersja}-${sha.slice(0, 8)}.pakiet`;
const plikPakietu = join(naSerwer, nazwaPakietu);
copyFileSync(roboczyPakiet, plikPakietu);
krok(`pakiet: ${p.pliki} plików, ${(p.rozpakowany / 1048576).toFixed(0)} MB -> ${(p.rozmiar / 1048576).toFixed(1)} MB`);

// 5. dwa programy startowe z tego samego kodu: internetowy (pakiet z app.livepl.eu) i pełny (pakiet w środku)
krok('kompiluję instalatory…');
const csc = join(process.env.WINDIR || 'C:\\Windows', 'Microsoft.NET', 'Framework64', 'v4.0.30319', 'csc.exe');
const logo = join(robocze, 'logo.png');
copyFileSync(join(katalog, 'assets', 'wstega-ciemna.png'), logo);
const info = join(robocze, 'PakietInfo.cs');
writeFileSync(info, [
  '// wygenerowane przez narzedzia/zrob-instalator.mjs',
  'static class PakietInfo',
  '{',
  `    public const string Wersja = "${wersja}";`,
  `    public const string Adres = "${ADRES_PAKIETOW}${nazwaPakietu}";`,
  `    public const string Sha256 = "${sha}";`,
  `    public const long Rozmiar = ${p.rozmiar};`,
  `    public const long Rozpakowany = ${p.kontener};`,
  '}',
  ''
].join('\n'));
const zrodla = join(katalog, 'narzedzia', 'instalator');

// 4a. sprawdzenie: rozpakowanie kodem instalatora (Pakiet.cs + Lzma.cs) i porównanie każdego pliku z oryginałem
krok('sprawdzam pakiet (rozpakowanie kodem instalatora)…');
const sprawdz = join(robocze, 'sprawdz.exe');
execFileSync(csc, ['/nologo', '/optimize+', '/codepage:65001', `/out:${sprawdz}`, '/r:System.Core.dll',
  join(zrodla, 'Sprawdz.cs'), join(zrodla, 'Pakiet.cs'), join(zrodla, 'Lzma.cs')], { stdio: 'inherit' });
execFileSync(sprawdz, [plikPakietu, program, join(robocze, 'sprawdzenie'), String(p.kontener)], { stdio: 'inherit' });

const kompiluj = (wynik, zasoby) => execFileSync(csc, [
  '/nologo', '/target:winexe', '/optimize+', '/codepage:65001',
  `/out:${wynik}`,
  `/win32icon:${join(katalog, 'assets', 'wstega-jasna.ico')}`,
  `/win32manifest:${join(zrodla, 'app.manifest')}`,
  `/resource:${logo},logo.png`,
  ...zasoby,
  '/r:System.Windows.Forms.dll', '/r:System.Drawing.dll', '/r:System.Core.dll',
  join(zrodla, 'Uruchom.cs'), join(zrodla, 'Pakiet.cs'), join(zrodla, 'Lzma.cs'), info
], { stdio: 'inherit' });
const internetowy = join(wyjscie, `LivePL-Instalator-${wersja}.exe`);
const pelny = join(wyjscie, `LivePL-Instalator-${wersja}-pelny.exe`);
kompiluj(internetowy, []);
kompiluj(pelny, [`/resource:${plikPakietu},livepl.pakiet`]);
podpisz([internetowy, pelny], certyfikat);

rmSync(robocze, { recursive: true, force: true });
const opis = (f) => { const b = statSync(f).size; return `${f} (${b < 1048576 ? Math.round(b / 1024) + ' kB' : (b / 1048576).toFixed(1) + ' MB'})`; };
console.log('Gotowe:');
console.log('  instalator internetowy: ' + opis(internetowy));
console.log('  instalator pełny:       ' + opis(pelny));
console.log('  pakiet do pobierania:   ' + opis(plikPakietu) + ' -> wgraj do ' + ADRES_PAKIETOW);
console.log('  SHA-256 pakietu: ' + sha);
