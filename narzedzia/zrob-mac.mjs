// Buduje LivePL na Maca: LivePL.app (Intel i Apple Silicon w jednym programie, „universal”) w pliku
// LivePL-<wersja>.dmg z odnośnikiem do Programów. Działa tylko na macOS; na GitHubie robi to .github/workflows/mac.yml.
//
//   node narzedzia/zrob-mac.mjs
//
// Podpis: certyfikat „Developer ID Application” z pęku kluczy (LIVEPL_MAC_TOZSAMOSC albo pierwszy znaleziony).
// Notaryzacja (sprawdzenie przez Apple, bez niego macOS nie otworzy programu z internetu): klucz App Store Connect
// z ASC_KEY_PLIK (plik .p8), ASC_KEY_ID i ASC_ISSUER_ID. Notaryzowany jest program i potem plik .dmg.
// Bez certyfikatu powstaje tylko wersja próbna z podpisem doraźnym (ad hoc), do testów u siebie:
// macOS pokaże ostrzeżenie, a otworzyć ją można w Ustawieniach systemowych → Prywatność i ochrona → „Otwórz mimo to”.
//
// Wynik: wydanie/mac/LivePL-<wersja>.dmg i obok LivePL-<wersja>.dmg.sha256.
import { packager } from '@electron/packager';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'darwin') {
  console.error('LivePL na Maca buduje się tylko na macOS (na GitHubie: workflow „LivePL na Maca”).');
  process.exit(1);
}

const katalog = join(dirname(fileURLToPath(import.meta.url)), '..');
const pakiet = JSON.parse(readFileSync(join(katalog, 'package.json'), 'utf8'));
const wersja = pakiet.version;
const wyjscie = join(katalog, 'wydanie', 'mac');
const robocze = join(process.env.RUNNER_TEMP || tmpdir(), `LivePL-mac-${wersja}`);
const UPRAWNIENIA = join(katalog, 'narzedzia', 'mac', 'uprawnienia.plist');
const krok = (t) => console.log('\n== ' + t);
const uruchom = (prog, args, opcje = {}) => execFileSync(prog, args, { stdio: 'inherit', ...opcje });
const odczytaj = (prog, args) => execFileSync(prog, args, { encoding: 'utf8' });

function znajdzTozsamosc() {
  if (process.env.LIVEPL_MAC_TOZSAMOSC) return process.env.LIVEPL_MAC_TOZSAMOSC;
  try {
    const m = odczytaj('security', ['find-identity', '-v', '-p', 'codesigning']).match(/"(Developer ID Application: [^"]+)"/);
    return m ? m[1] : null;
  } catch {
    return null;
  }
}

const tozsamosc = znajdzTozsamosc();
const kluczAsc = process.env.ASC_KEY_PLIK && existsSync(process.env.ASC_KEY_PLIK) && process.env.ASC_KEY_ID && process.env.ASC_ISSUER_ID;
console.log(`LivePL ${wersja} na Maca`);
console.log(tozsamosc ? `podpis: ${tozsamosc}` : 'BEZ CERTYFIKATU: wersja próbna z podpisem doraźnym, nie do rozdawania');
if (tozsamosc && !kluczAsc) {
  console.error('Jest certyfikat, ale brak klucza App Store Connect do notaryzacji (ASC_KEY_PLIK, ASC_KEY_ID, ASC_ISSUER_ID).');
  process.exit(1);
}

rmSync(robocze, { recursive: true, force: true });
mkdirSync(robocze, { recursive: true });
mkdirSync(wyjscie, { recursive: true });

// 1. LivePL.app z silnikiem Electrona dla Intela i Apple Silicon
krok('Program LivePL.app (Intel i Apple Silicon)');
const [folder] = await packager({
  dir: katalog,
  out: robocze,
  overwrite: true,
  platform: 'darwin',
  arch: 'universal',
  name: 'LivePL',
  executableName: 'LivePL',
  appBundleId: 'eu.livepl.przegladarka.mac',
  appVersion: wersja,
  buildVersion: wersja,
  appCopyright: `Copyright © ${new Date().getFullYear()} Live Production Karol Kowalski (licencja MIT)`,
  appCategoryType: 'public.app-category.productivity',
  icon: join(katalog, 'assets', 'mac', 'LivePL.icns'),
  darwinDarkModeSupport: true,
  // pliki programu jak w Windows (bez archiwum asar): src, ui, assets, programy.json, package.json, LICENSE
  asar: false,
  prune: true,
  ignore: [/^\/narzedzia(\/|$)/, /^\/wydanie(\/|$)/, /^\/\.github(\/|$)/, /^\/\.git(\/|$)/, /^\/ios(\/|$)/, /^\/README\.md$/],
  extendInfo: {
    CFBundleDisplayName: 'LivePL',
    CFBundleDevelopmentRegion: 'pl',
    LSMinimumSystemVersion: '12.0',
    NSCameraUsageDescription: 'Strona otwarta w LivePL prosi o kamerę (np. rozmowa wideo).',
    NSMicrophoneUsageDescription: 'Strona otwarta w LivePL prosi o mikrofon (np. rozmowa lub nagranie).',
    NSLocationUsageDescription: 'Strona otwarta w LivePL prosi o Twoje położenie.',
    NSLocationWhenInUseUsageDescription: 'Strona otwarta w LivePL prosi o Twoje położenie.'
  },
  osxSign: tozsamosc
    ? { identity: tozsamosc, optionsForFile: () => ({ hardenedRuntime: true, entitlements: UPRAWNIENIA }) }
    : undefined
});
const program = join(folder, 'LivePL.app');
if (!tozsamosc) {
  // bez podpisu Apple Silicon w ogóle nie uruchomi programu; podpis doraźny wystarczy do testów
  uruchom('codesign', ['--force', '--deep', '--sign', '-', program]);
}
uruchom('codesign', ['--verify', '--deep', '--strict', '--verbose=2', program]);
console.log(odczytaj('lipo', ['-archs', join(program, 'Contents', 'MacOS', 'LivePL')]).trim().replace(/^/, 'architektury: '));

// 2. Notaryzacja programu i „zszycie” biletu z programem (działa wtedy także bez internetu)
function notaryzuj(plik, nazwa) {
  krok(`Notaryzacja: ${nazwa} (Apple sprawdza plik, zwykle kilka minut)`);
  const json = odczytaj('xcrun', ['notarytool', 'submit', plik,
    '--key', process.env.ASC_KEY_PLIK, '--key-id', process.env.ASC_KEY_ID, '--issuer', process.env.ASC_ISSUER_ID,
    '--wait', '--timeout', '45m', '--output-format', 'json']);
  const w = JSON.parse(json);
  console.log(`zgłoszenie ${w.id}: ${w.status}`);
  if (w.status !== 'Accepted') {
    try {
      console.log(odczytaj('xcrun', ['notarytool', 'log', w.id, '--key', process.env.ASC_KEY_PLIK,
        '--key-id', process.env.ASC_KEY_ID, '--issuer', process.env.ASC_ISSUER_ID]));
    } catch { /* bez dziennika */ }
    throw new Error(`Apple nie przyjęło ${nazwa}: ${w.status}`);
  }
}
if (tozsamosc) {
  const zip = join(robocze, 'LivePL-notaryzacja.zip');
  uruchom('ditto', ['-c', '-k', '--keepParent', program, zip]);
  notaryzuj(zip, 'LivePL.app');
  uruchom('xcrun', ['stapler', 'staple', program]);
  uruchom('spctl', ['--assess', '--type', 'execute', '--verbose=2', program]);
}

// 3. Plik .dmg: LivePL i odnośnik do Programów (przeciągnij i upuść)
krok('Plik .dmg');
const nazwaDmg = tozsamosc ? `LivePL-${wersja}.dmg` : `LivePL-${wersja}-proba.dmg`;
const dmg = join(wyjscie, nazwaDmg);
const zawartosc = join(robocze, 'dmg');
mkdirSync(zawartosc, { recursive: true });
uruchom('ditto', [program, join(zawartosc, 'LivePL.app')]);
symlinkSync('/Applications', join(zawartosc, 'Programy'));
rmSync(dmg, { force: true });
uruchom('hdiutil', ['create', '-volname', 'LivePL', '-srcfolder', zawartosc, '-ov', '-format', 'ULFO', dmg]);
if (tozsamosc) {
  uruchom('codesign', ['--sign', tozsamosc, '--timestamp', dmg]);
  notaryzuj(dmg, nazwaDmg);
  uruchom('xcrun', ['stapler', 'staple', dmg]);
  uruchom('spctl', ['--assess', '--type', 'open', '--context', 'context:primary-signature', '--verbose=2', dmg]);
}

const suma = createHash('sha256').update(readFileSync(dmg)).digest('hex');
writeFileSync(dmg + '.sha256', `${suma}  ${nazwaDmg}\n`);
krok('Gotowe');
console.log(`${dmg}\nSHA-256 ${suma}`);
