// Jednorazowa konfiguracja podpisu LivePL na iPhone'a przez App Store Connect API (z Windows, bez Maca).
// Robi: identyfikator aplikacji eu.livepl.przegladarka, certyfikat Apple Distribution
// (klucz prywatny powstaje i zostaje w tym komputerze) oraz profil „LivePL App Store”.
//
//   node ios/narzedzia/apple-konfiguracja.mjs <AuthKey_XXXX.p8> <Issuer ID> [--bundle eu.livepl.poczta --nazwa "Poczta LivePL" --profil "Poczta LivePL App Store"]
//
// Bez opcji: przeglądarka LivePL (eu.livepl.przegladarka, profil „LivePL App Store”). Certyfikat dystrybucyjny
// jest wspólny dla wszystkich aplikacji; drugi przebieg tylko zakłada identyfikator i profil.
//
// Klucz API musi mieć rolę Admin (tylko Admin może tworzyć certyfikaty). Ten klucz zostaje na komputerze.
// Wynik trafia do %USERPROFILE%\LivePL-klucze\apple. Skrypt nie wypisuje żadnych sekretów.

import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const opcja = (nazwa, domyslnie) => { const i = process.argv.indexOf('--' + nazwa); return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : domyslnie; };
const BUNDLE_ID = opcja('bundle', 'eu.livepl.przegladarka');
const NAZWA_APLIKACJI = opcja('nazwa', 'LivePL');
const NAZWA_PROFILU = opcja('profil', 'LivePL App Store');
const API = 'https://api.appstoreconnect.apple.com';
const FOLDER = path.join(os.homedir(), 'LivePL-klucze', 'apple');

const [plikKlucza, issuerId] = process.argv.slice(2);
if (!plikKlucza || !issuerId) {
  console.error('Użycie: node ios/narzedzia/apple-konfiguracja.mjs <AuthKey_XXXX.p8> <Issuer ID>');
  process.exit(2);
}
const keyId = path.basename(plikKlucza).replace(/^AuthKey_/, '').replace(/\.p8$/, '');
const kluczApi = fs.readFileSync(plikKlucza, 'utf8');
fs.mkdirSync(FOLDER, { recursive: true });

const odcisk = (dane) => crypto.createHash('sha256').update(dane).digest('hex').slice(0, 8);
const b64url = (b) => Buffer.from(b).toString('base64url');

function token() {
  const teraz = Math.floor(Date.now() / 1000);
  const naglowek = b64url(JSON.stringify({ alg: 'ES256', kid: keyId, typ: 'JWT' }));
  const tresc = b64url(JSON.stringify({ iss: issuerId, iat: teraz, exp: teraz + 15 * 60, aud: 'appstoreconnect-v1' }));
  const podpis = crypto.sign('sha256', Buffer.from(`${naglowek}.${tresc}`), { key: kluczApi, dsaEncoding: 'ieee-p1363' });
  return `${naglowek}.${tresc}.${b64url(podpis)}`;
}

async function api(metoda, sciezka, cialo) {
  const odp = await fetch(API + sciezka, {
    method: metoda,
    headers: { Authorization: `Bearer ${token()}`, 'Content-Type': 'application/json' },
    body: cialo ? JSON.stringify(cialo) : undefined
  });
  if (odp.status === 204) return null;
  const json = await odp.json().catch(() => ({}));
  if (!odp.ok) {
    const opis = (json.errors || []).map((e) => `${e.title}: ${e.detail}`).join('; ') || odp.statusText;
    throw new Error(`${metoda} ${sciezka.split('?')[0]} → ${odp.status} ${opis}`);
  }
  return json;
}

function openssl(argumenty, env = {}) {
  const kandydaci = ['openssl', 'C:\\Program Files\\Git\\usr\\bin\\openssl.exe', 'C:\\Program Files\\Git\\mingw64\\bin\\openssl.exe'];
  for (const exe of kandydaci) {
    const w = spawnSync(exe, argumenty, { env: { ...process.env, ...env }, encoding: 'utf8' });
    if (w.error && w.error.code === 'ENOENT') continue;
    if (w.status !== 0) throw new Error(`openssl ${argumenty[0]}: ${w.stderr.trim()}`);
    return w.stdout;
  }
  throw new Error('Nie znaleziono openssl (jest w Git for Windows).');
}

// 1. Identyfikator aplikacji
let bundle = (await api('GET', `/v1/bundleIds?filter[identifier]=${BUNDLE_ID}`)).data.find((b) => b.attributes.identifier === BUNDLE_ID);
if (!bundle) {
  bundle = (await api('POST', '/v1/bundleIds', {
    data: { type: 'bundleIds', attributes: { identifier: BUNDLE_ID, name: NAZWA_APLIKACJI, platform: 'IOS' } }
  })).data;
  console.log(`Identyfikator ${BUNDLE_ID}: założony`);
} else {
  console.log(`Identyfikator ${BUNDLE_ID}: już był`);
}

// 2. Certyfikat Apple Distribution (klucz prywatny tylko tutaj)
const plikP12 = path.join(FOLDER, 'dystrybucja.p12');
const plikHasla = path.join(FOLDER, 'dystrybucja-haslo.txt');
const plikInfo = path.join(FOLDER, 'dystrybucja.json');
let certId;
if (fs.existsSync(plikP12) && fs.existsSync(plikInfo)) {
  certId = JSON.parse(fs.readFileSync(plikInfo, 'utf8')).id;
  console.log('Certyfikat Apple Distribution: już był, używam istniejącego');
} else {
  const kluczPem = path.join(FOLDER, 'dystrybucja-klucz.pem');
  const csrPem = path.join(FOLDER, 'dystrybucja.csr');
  const certPem = path.join(FOLDER, 'dystrybucja.pem');
  const posrednie = path.join(FOLDER, 'apple-wwdr.pem');
  openssl(['req', '-new', '-newkey', 'rsa:2048', '-nodes', '-keyout', kluczPem, '-out', csrPem,
    '-subj', '/CN=LivePL Apple Distribution/O=Live Production/C=PL']);
  const cert = (await api('POST', '/v1/certificates', {
    data: { type: 'certificates', attributes: { certificateType: 'DISTRIBUTION', csrContent: fs.readFileSync(csrPem, 'utf8') } }
  })).data;
  certId = cert.id;
  const der = Buffer.from(cert.attributes.certificateContent, 'base64');
  fs.writeFileSync(certPem, `-----BEGIN CERTIFICATE-----\n${der.toString('base64').match(/.{1,64}/g).join('\n')}\n-----END CERTIFICATE-----\n`);

  // certyfikaty pośrednie Apple (WWDR G3 i G4), żeby Mac na pewno złożył łańcuch
  let pem = '';
  for (const nazwa of ['AppleWWDRCAG3.cer', 'AppleWWDRCAG4.cer']) {
    const odp = await fetch(`https://www.apple.com/certificateauthority/${nazwa}`);
    if (!odp.ok) continue;
    const b = Buffer.from(await odp.arrayBuffer());
    pem += `-----BEGIN CERTIFICATE-----\n${b.toString('base64').match(/.{1,64}/g).join('\n')}\n-----END CERTIFICATE-----\n`;
  }
  fs.writeFileSync(posrednie, pem);

  const haslo = crypto.randomBytes(24).toString('base64url');
  fs.writeFileSync(plikHasla, haslo);
  // stare szyfrowanie PKCS#12 (3DES/SHA1), bo tylko takie czyta `security import` na Macu
  openssl(['pkcs12', '-export', '-inkey', kluczPem, '-in', certPem, '-certfile', posrednie,
    '-name', 'LivePL Apple Distribution', '-out', plikP12, '-passout', 'env:LIVEPL_P12',
    '-keypbe', 'PBE-SHA1-3DES', '-certpbe', 'PBE-SHA1-3DES', '-macalg', 'sha1'], { LIVEPL_P12: haslo });
  fs.writeFileSync(plikInfo, JSON.stringify({ id: certId, nazwa: cert.attributes.name, waznyDo: cert.attributes.expirationDate }, null, 2));
  // klucz prywatny żyje dalej tylko w zaszyfrowanym .p12
  for (const p of [kluczPem, csrPem]) fs.rmSync(p, { force: true });
  console.log(`Certyfikat Apple Distribution: wydany, ważny do ${cert.attributes.expirationDate.slice(0, 10)}`);
}

// 3. Profil App Store (zawsze świeży, z bieżącym certyfikatem)
const stare = (await api('GET', `/v1/profiles?filter[name]=${encodeURIComponent(NAZWA_PROFILU)}`)).data;
for (const p of stare) await api('DELETE', `/v1/profiles/${p.id}`);
const profil = (await api('POST', '/v1/profiles', {
  data: {
    type: 'profiles',
    attributes: { name: NAZWA_PROFILU, profileType: 'IOS_APP_STORE' },
    relationships: {
      bundleId: { data: { type: 'bundleIds', id: bundle.id } },
      certificates: { data: [{ type: 'certificates', id: certId }] }
    }
  }
})).data;
const plikProfilu = path.join(FOLDER, NAZWA_PROFILU.replace(/\s+/g, '_') + '.mobileprovision');
fs.writeFileSync(plikProfilu, Buffer.from(profil.attributes.profileContent, 'base64'));
console.log(`Profil „${NAZWA_PROFILU}”: ${stare.length ? 'odnowiony' : 'założony'}, ważny do ${profil.attributes.expirationDate.slice(0, 10)}`);

// 4. Czy aplikacja jest już w App Store Connect
const aplikacje = (await api('GET', `/v1/apps?filter[bundleId]=${BUNDLE_ID}`)).data;
console.log(aplikacje.length
  ? `Aplikacja w App Store Connect: jest („${aplikacje[0].attributes.name}”)`
  : 'Aplikacja w App Store Connect: BRAK, trzeba ją dodać ręcznie (Aplikacje → + → Nowa aplikacja)');

console.log('\nPliki w', FOLDER);
for (const p of [plikP12, plikHasla, plikProfilu]) {
  console.log(`  ${path.basename(p)}  (${fs.statSync(p).size} B, sha256 ${odcisk(fs.readFileSync(p))}…)`);
}
