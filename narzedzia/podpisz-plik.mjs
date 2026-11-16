// Podpis dowolnego pliku .exe/.dll/.msi certyfikatem Certum (do ręcznego użycia).
//   node podpisz-plik.mjs <plik> [<plik> ...]            podpisuje pliki bez podpisu
//   node podpisz-plik.mjs --zastap <plik> [...]          zdejmuje stary podpis (np. własny certyfikat) i podpisuje od nowa
// Najprościej: przeciągnij plik(i) na Dokumenty\LivePL\Podpisz.cmd.
// Wcześniej musi działać i być zalogowany SimplySign Desktop (mail i kod z aplikacji na telefonie).
// Po każdym podpisie sprawdzamy, że jest jeden, ważny i ze znacznikiem czasu (narzedzia/podpis.mjs).
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { wybierzCertyfikat, sprawdzSesje, podpisz, maPodpis, zdejmijPodpis } from './podpis.mjs';

const arg = process.argv.slice(2);
const zastap = arg.includes('--zastap');
const pliki = arg.filter((a) => !a.startsWith('--')).map((p) => resolve(p));

if (!pliki.length) {
  console.log('Przeciągnij plik .exe na Podpisz.cmd albo: node podpisz-plik.mjs [--zastap] <plik.exe>');
  process.exit(1);
}

let certyfikat;
try {
  certyfikat = wybierzCertyfikat(process.env.LIVEPL_PODPIS || 'certum');
  sprawdzSesje(certyfikat); // próbny podpis: certyfikat bywa widoczny także przy wygasłej sesji SimplySign
} catch (e) {
  console.log('Nie mogę podpisać: ' + e.message);
  if (!/Zaloguj/.test(e.message)) console.log('Zaloguj się w SimplySign Desktop (ikona przy zegarze) i spróbuj jeszcze raz.');
  process.exit(1);
}
console.log('Certyfikat: ' + certyfikat.opis);

let bledy = 0;
for (const plik of pliki) {
  const nazwa = plik.split(/[\\/]/).pop();
  try {
    if (!existsSync(plik)) throw new Error('nie ma takiego pliku');
    if (!/\.(exe|dll|msi|sys|ocx|node)$/i.test(plik)) throw new Error('podpisuję tylko pliki .exe, .dll, .msi, .sys, .ocx i .node');
    if (maPodpis(plik)) {
      const kto = execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
        '$s = Get-AuthenticodeSignature -LiteralPath $env:PLIK; "" + $s.Status + " | " + $s.SignerCertificate.Subject'],
      { env: { ...process.env, PLIK: plik }, encoding: 'utf8' }).trim();
      if (!zastap) throw new Error(`ma już podpis (${kto}). Żeby go zastąpić Twoim, użyj --zastap (albo Podpisz-zastap.cmd).`);
      if (/Microsoft/i.test(kto)) throw new Error(`ma podpis Microsoftu (${kto}); takiego nie zastępuję`);
      zdejmijPodpis(plik);
      console.log(`  ${nazwa}: zdjąłem stary podpis (${kto})`);
    }
    podpisz([plik], certyfikat);
  } catch (e) {
    bledy++;
    console.log(`  ${nazwa}: NIE podpisano – ${e.message.split('\n')[0]}`);
  }
}
console.log(bledy ? `Gotowe, ale ${bledy} plik(ów) nie podpisano.` : 'Gotowe. Podpis sprawdzisz też w Eksploratorze: prawy przycisk → Właściwości → Podpisy cyfrowe.');
process.exit(bledy ? 1 : 0);
