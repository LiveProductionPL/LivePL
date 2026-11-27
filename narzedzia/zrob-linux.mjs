// Buduje LivePL na Linuxa (64-bitowe komputery z procesorem Intel/AMD):
//   wydanie/linux/LivePL-<wersja>-x86_64.AppImage   jeden plik dla każdego Linuxa
//   wydanie/linux/livepl_<wersja>_amd64.deb         pakiet dla Ubuntu, Debiana i Minta
// i obok każdego plik .sha256. Działa na Linuxie (na GitHubie: .github/workflows/linux.yml).
//
//   node narzedzia/zrob-linux.mjs
//
// Potrzebne: dpkg-deb, mksquashfs (squashfs-tools) i środowisko AppImage (type2-runtime) w pliku wskazanym przez
// LIVEPL_APPIMAGE_RUNTIME; skrypt sprawdza jego sumę SHA-256 (RUNTIME_SHA256 niżej, wydanie 20251108).
//
// Program w obu plikach to te same pliki co w Windows (src, ui, assets, bez archiwum asar) z silnikiem Electrona
// dla Linuxa. Aktualizacje przychodzą tymi samymi podpisanymi paczkami co w Windows i trafiają do
// ~/.config/LivePL/program (src/nakladka-linux.js), więc nowy AppImage/.deb potrzebny jest tylko przy nowym silniku.
import { packager } from '@electron/packager';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { chmodSync, copyFileSync, cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'linux') {
  console.error('LivePL na Linuxa buduje się tylko na Linuxie (na GitHubie: workflow „LivePL na Linuxa”).');
  process.exit(1);
}

// type2-runtime z https://github.com/AppImage/type2-runtime/releases/tag/20251108 (runtime-x86_64)
const RUNTIME_SHA256 = '2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d';

const katalog = join(dirname(fileURLToPath(import.meta.url)), '..');
const LINUX = join(katalog, 'narzedzia', 'linux');
const pakiet = JSON.parse(readFileSync(join(katalog, 'package.json'), 'utf8'));
const wersja = pakiet.version;
const wyjscie = join(katalog, 'wydanie', 'linux');
const robocze = join(process.env.RUNNER_TEMP || tmpdir(), `LivePL-linux-${wersja}`);
const krok = (t) => console.log('\n== ' + t);
const uruchom = (prog, args, opcje = {}) => execFileSync(prog, args, { stdio: 'inherit', ...opcje });
const sha256 = (plik) => createHash('sha256').update(readFileSync(plik)).digest('hex');

console.log(`LivePL ${wersja} na Linuxa`);
rmSync(robocze, { recursive: true, force: true });
mkdirSync(robocze, { recursive: true });
rmSync(wyjscie, { recursive: true, force: true });
mkdirSync(wyjscie, { recursive: true });

// 1. program z silnikiem Electrona dla Linuxa (x64)
krok('Program LivePL (Electron dla Linuxa, x64)');
const [folder] = await packager({
  dir: katalog,
  out: join(robocze, 'pakowanie'),
  overwrite: true,
  platform: 'linux',
  arch: 'x64',
  name: 'LivePL',
  executableName: 'livepl',
  appVersion: wersja,
  appCopyright: `Copyright © ${new Date().getFullYear()} Live Production Karol Kowalski (licencja MIT)`,
  // pliki programu jak w Windows (bez archiwum asar): src, ui, assets, programy.json, package.json, LICENSE
  asar: false,
  prune: true,
  ignore: [/^\/narzedzia(\/|$)/, /^\/wydanie(\/|$)/, /^\/\.github(\/|$)/, /^\/\.git(\/|$)/, /^\/ios(\/|$)/, /^\/README\.md$/]
});
// profil AppArmor leży przy programie; postinst kopiuje go do /etc/apparmor.d
copyFileSync(join(LINUX, 'apparmor-livepl'), join(folder, 'apparmor-livepl'));

// pliki wspólne dla .deb i AppImage: ikony, wpis w menu, typ pliku wtyczki
function dodajUsr(cel) {
  for (const r of [16, 24, 32, 48, 64, 128, 256, 512]) {
    const d = join(cel, 'usr', 'share', 'icons', 'hicolor', `${r}x${r}`, 'apps');
    mkdirSync(d, { recursive: true });
    copyFileSync(join(katalog, 'assets', 'linux', `livepl-${r}.png`), join(d, 'livepl.png'));
  }
  mkdirSync(join(cel, 'usr', 'share', 'applications'), { recursive: true });
  copyFileSync(join(LINUX, 'livepl.desktop'), join(cel, 'usr', 'share', 'applications', 'livepl.desktop'));
  mkdirSync(join(cel, 'usr', 'share', 'mime', 'packages'), { recursive: true });
  copyFileSync(join(LINUX, 'livepl.xml'), join(cel, 'usr', 'share', 'mime', 'packages', 'livepl.xml'));
}

function rozmiarKB(d) {
  let s = 0;
  for (const w of readdirSync(d, { withFileTypes: true })) {
    const p = join(d, w.name);
    if (w.isDirectory()) s += rozmiarKB(p) * 1024;
    else if (w.isFile()) s += statSync(p).size;
  }
  return Math.ceil(s / 1024);
}

// 2. pakiet .deb: /opt/LivePL, polecenie /usr/bin/livepl, wpis w menu, ikony, typ pliku, profil AppArmor
krok('Pakiet .deb');
const deb = join(robocze, 'deb');
mkdirSync(join(deb, 'opt'), { recursive: true });
cpSync(folder, join(deb, 'opt', 'LivePL'), { recursive: true, verbatimSymlinks: true });
mkdirSync(join(deb, 'usr', 'bin'), { recursive: true });
copyFileSync(join(LINUX, 'livepl.sh'), join(deb, 'usr', 'bin', 'livepl'));
chmodSync(join(deb, 'usr', 'bin', 'livepl'), 0o755);
dodajUsr(deb);
mkdirSync(join(deb, 'DEBIAN'), { recursive: true });
// biblioteki Chromium jak w pakietach Chrome i VS Code; na Ubuntu 24.04 libasound2 i libgtk-3-0 dają pakiety *t64
const ZALEZNOSCI = [
  'ca-certificates', 'libasound2', 'libatk-bridge2.0-0', 'libatk1.0-0', 'libatspi2.0-0', 'libc6 (>= 2.28)', 'libcairo2',
  'libcups2', 'libdbus-1-3', 'libdrm2', 'libexpat1', 'libgbm1', 'libglib2.0-0', 'libgtk-3-0', 'libnspr4', 'libnss3',
  'libpango-1.0-0', 'libsecret-1-0', 'libx11-6', 'libxcb1', 'libxcomposite1', 'libxdamage1', 'libxext6', 'libxfixes3',
  'libxkbcommon0', 'libxrandr2', 'xdg-utils'
];
writeFileSync(join(deb, 'DEBIAN', 'control'), [
  'Package: livepl',
  `Version: ${wersja}`,
  'Section: web',
  'Priority: optional',
  'Architecture: amd64',
  `Installed-Size: ${rozmiarKB(join(deb, 'opt'))}`,
  `Depends: ${ZALEZNOSCI.join(', ')}`,
  'Recommends: gnome-keyring | kwalletmanager, libnotify4',
  'Maintainer: Live Production <biuro@liveproduction.pl>',
  'Homepage: https://app.livepl.eu/',
  'Description: Przeglądarka LivePL',
  ' Poczta, strefa klienta, Sevetoo i programy Live Production w jednym oknie.',
  ' Aktualizuje się sama (podpisane paczki z aktualizacje.livepl.eu).',
  ''
].join('\n'));
for (const s of ['postinst', 'postrm']) {
  copyFileSync(join(LINUX, s), join(deb, 'DEBIAN', s));
  chmodSync(join(deb, 'DEBIAN', s), 0o755);
}
const plikDeb = join(wyjscie, `livepl_${wersja}_amd64.deb`);
uruchom('dpkg-deb', ['--root-owner-group', '-Zxz', '--build', deb, plikDeb]);

// 3. AppImage: AppDir (AppRun, wpis, ikona, program w opt/LivePL) w obrazie squashfs za środowiskiem type2-runtime
krok('Plik AppImage');
const runtime = process.env.LIVEPL_APPIMAGE_RUNTIME;
if (!runtime || !existsSync(runtime)) {
  console.error('Brak środowiska AppImage: ustaw LIVEPL_APPIMAGE_RUNTIME na plik runtime-x86_64 (type2-runtime 20251108).');
  process.exit(1);
}
if (sha256(runtime) !== RUNTIME_SHA256) {
  console.error(`Środowisko AppImage ma inną sumę SHA-256 niż oczekiwana (${RUNTIME_SHA256}).`);
  process.exit(1);
}
const appdir = join(robocze, 'LivePL.AppDir');
mkdirSync(join(appdir, 'opt'), { recursive: true });
cpSync(folder, join(appdir, 'opt', 'LivePL'), { recursive: true, verbatimSymlinks: true });
copyFileSync(join(LINUX, 'livepl.sh'), join(appdir, 'AppRun'));
chmodSync(join(appdir, 'AppRun'), 0o755);
dodajUsr(appdir);
copyFileSync(join(LINUX, 'livepl.desktop'), join(appdir, 'livepl.desktop'));
copyFileSync(join(katalog, 'assets', 'linux', 'livepl-256.png'), join(appdir, 'livepl.png'));
symlinkSync('livepl.png', join(appdir, '.DirIcon'));
const obraz = join(robocze, 'LivePL.squashfs');
uruchom('mksquashfs', [appdir, obraz, '-root-owned', '-noappend', '-comp', 'zstd', '-Xcompression-level', '19', '-quiet']);
const plikAppImage = join(wyjscie, `LivePL-${wersja}-x86_64.AppImage`);
writeFileSync(plikAppImage, Buffer.concat([readFileSync(runtime), readFileSync(obraz)]));
chmodSync(plikAppImage, 0o755);

// 4. sumy SHA-256 (format sha256sum, do sprawdzenia: sha256sum -c plik.sha256)
krok('Sumy SHA-256');
for (const p of [plikAppImage, plikDeb]) {
  const nazwa = p.split('/').pop();
  const linia = `${sha256(p)}  ${nazwa}\n`;
  writeFileSync(p + '.sha256', linia);
  console.log(linia.trim(), `(${Math.round(statSync(p).size / 1048576)} MB)`);
}
rmSync(robocze, { recursive: true, force: true });
console.log('\nGotowe:', wyjscie);
