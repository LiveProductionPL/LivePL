'use strict';

// Certyfikaty zainstalowane w Windows (tylko do podglądu): osobiste, zaufane główne urzędy,
// urzędy pośrednie i certyfikaty komputera. LivePL niczego tu nie zmienia ani nie usuwa.

const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { powershell } = require('./powershell');

const MAGAZYNY = {
  osobiste: 'Cert:\\CurrentUser\\My',
  glowne: 'Cert:\\CurrentUser\\Root',
  posrednie: 'Cert:\\CurrentUser\\CA',
  komputer: 'Cert:\\LocalMachine\\My'
};

let ostatnie = null;
let surowe = new Map(); // odcisk|magazyn -> DER (base64), tylko w LivePL

// nasze urzędy: certyfikat jest „LivePL”, jeśli to jeden z nich albo któryś z nich go podpisał
const NASZE = (() => {
  const folder = path.join(__dirname, '..', 'assets', 'ca');
  const pliki = [path.join(folder, 'livepl-root-ca-r1.crt'), path.join(folder, 'posrednie', 'livepl-ca-1.crt')];
  return pliki.map((p) => { try { return new crypto.X509Certificate(fs.readFileSync(p)); } catch { return null; } }).filter(Boolean);
})();

function czyNasz(x) {
  return NASZE.some((ca) => ca.fingerprint256 === x.fingerprint256 || (x.checkIssued(ca) && x.verify(ca.publicKey)));
}

function cn(dn) {
  const m = /(?:^|[,+]\s*)CN=("[^"]*"|[^,+]*)/.exec(String(dn || ''));
  if (m) return m[1].replace(/^"|"$/g, '').trim();
  const o = /(?:^|[,+]\s*)O=("[^"]*"|[^,+]*)/.exec(String(dn || ''));
  return o ? o[1].replace(/^"|"$/g, '').trim() : String(dn || '');
}

async function lista() {
  if (process.platform !== 'win32') return null;
  const skrypt = Object.entries(MAGAZYNY).map(([klucz, sciezka]) =>
    `@(Get-ChildItem '${sciezka}' -ErrorAction SilentlyContinue | ForEach-Object { [pscustomobject]@{ m='${klucz}'; s=$_.Subject; i=$_.Issuer; od=$_.NotBefore.ToString('o'); do=$_.NotAfter.ToString('o'); t=$_.Thumbprint; k=$_.HasPrivateKey; r=[Convert]::ToBase64String($_.RawData) } })`
  ).join(' + ');
  const json = await powershell(`$w = ${skrypt}; ConvertTo-Json -Compress -Depth 3 -InputObject @($w)`, {}, 30000);
  const wpisy = [].concat(JSON.parse(json || '[]'));
  const teraz = Date.now();
  surowe = new Map();
  const wynik = { sprawdzono: teraz, osobiste: [], glowne: [], posrednie: [], komputer: [] };
  for (const w of wpisy) {
    if (!w || !wynik[w.m]) continue;
    // magazyn konta (np. CurrentUser\CA) zawiera też certyfikaty komputera: ten sam certyfikat
    // zainstalowany w obu miejscach Windows podaje dwa razy, a to jeden certyfikat
    if (surowe.has(w.t + '|' + w.m)) continue;
    surowe.set(w.t + '|' + w.m, w.r);
    let sha256 = '';
    let livepl = false;
    let rodzaj = '';
    try {
      const x = new crypto.X509Certificate(Buffer.from(w.r, 'base64'));
      sha256 = x.fingerprint256;
      const eku = x.keyUsage || [];
      rodzaj = x.ca ? 'urząd certyfikacji'
        : eku.includes('1.3.6.1.5.5.7.3.2') && eku.includes('1.3.6.1.5.5.7.3.4') ? 'osobisty (logowanie i poczta)'
          : eku.includes('1.3.6.1.5.5.7.3.2') ? 'logowanie'
            : eku.includes('1.3.6.1.5.5.7.3.1') ? 'strona (serwer)'
              : eku.includes('1.3.6.1.5.5.7.3.3') ? 'podpis kodu'
                : eku.includes('1.3.6.1.5.5.7.3.4') ? 'poczta' : '';
      livepl = czyNasz(x);
    } catch { /* nieczytelny certyfikat: pokazujemy to, co podał Windows */ }
    const od = Date.parse(w.od);
    const doKiedy = Date.parse(w.do);
    wynik[w.m].push({
      magazyn: w.m,
      odcisk: w.t,
      sha256,
      nazwa: cn(w.s) || '(bez nazwy)',
      podmiot: w.s,
      wystawca: w.s === w.i ? 'samopodpisany' : cn(w.i),
      wystawcaPelny: w.i,
      od,
      do: doKiedy,
      wygasl: doKiedy < teraz,
      klucz: !!w.k,
      livepl,
      rodzaj
    });
  }
  for (const k of Object.keys(MAGAZYNY)) {
    // nasze na górze, potem ważne, potem alfabetycznie
    wynik[k].sort((a, b) => (b.livepl - a.livepl) || (a.wygasl - b.wygasl) || a.nazwa.localeCompare(b.nazwa, 'pl'));
  }
  ostatnie = wynik;
  return wynik;
}

const ostatnieListy = () => ostatnie;

// zapis certyfikatu do pliku .cer (bez klucza), np. żeby otworzyć go w oknie Windows
function zapiszDoPliku(odcisk, magazyn, sciezka = null) {
  const r = surowe.get(odcisk + '|' + magazyn);
  if (!r) throw new Error('Nie ma takiego certyfikatu (odśwież listę)');
  const cel = sciezka || path.join(os.tmpdir(), `livepl-certyfikat-${odcisk.slice(0, 12)}.cer`);
  fs.writeFileSync(cel, Buffer.from(r, 'base64'));
  return cel;
}

module.exports = { lista, ostatnieListy, zapiszDoPliku };
