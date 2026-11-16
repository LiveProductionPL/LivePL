// Tworzy LivePL.exe obok silnika: kopia pliku z naszą nazwą, ikoną i opisem firmy.
// Uruchom: npm run exe   (po każdej aktualizacji silnika albo zmianie ikony)
import { copyFileSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { rcedit } from 'rcedit';

const katalog = join(dirname(fileURLToPath(import.meta.url)), '..');
const silnik = join(katalog, 'node_modules', 'electron', 'dist');
const pakiet = JSON.parse(readFileSync(join(katalog, 'package.json'), 'utf8'));
const wersja = pakiet.version;
const exe = join(silnik, 'LivePL.exe');

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
console.log('Gotowe:', exe);
