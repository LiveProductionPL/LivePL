// Certyfikat „Developer ID Application” do podpisu LivePL na Maca (z Windows, bez Maca).
// Klucz prywatny powstaje i zostaje w tym komputerze (%USERPROFILE%\LivePL-klucze\apple), do GitHuba idzie
// tylko zaszyfrowany plik .p12 (ustaw-sekrety-mac.mjs, uruchamia właściciel konta).
//
//   node narzedzia/mac/developer-id.mjs <AuthKey_XXXX.p8 z rolą Admin> <Issuer ID>
//   node narzedzia/mac/developer-id.mjs --cer <pobrany developerID_application.cer>
//
// Pierwsze polecenie robi klucz i wniosek (CSR) i prosi Apple o certyfikat przez App Store Connect API.
// Jeśli Apple pozwala na to tylko właścicielowi konta w przeglądarce, skrypt zostawia wniosek
// developer-id.csr: wgraj go na developer.apple.com → Certificates → + → Developer ID Application (G2),
// pobierz .cer i uruchom drugie polecenie. Skrypt nie wypisuje żadnych sekretów.

import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const API = 'https://api.appstoreconnect.apple.com';
const FOLDER = path.join(os.homedir(), 'LivePL-klucze', 'apple');
const KLUCZ = path.join(FOLDER, 'developer-id-klucz.pem');
const CSR = path.join(FOLDER, 'developer-id.csr');
const P12 = path.join(FOLDER, 'developer-id.p12');
const HASLO = path.join(FOLDER, 'developer-id-haslo.txt');
const INFO = path.join(FOLDER, 'developer-id.json');
fs.mkdirSync(FOLDER, { recursive: true });

function openssl(argumenty, env = {}) {
  const kandydaci = ['openssl', 'C:\\Program Files\\Git\\usr\\bin\\openssl.exe', 'C:\\Program Files\\Git\\mingw64\\bin\\openssl.exe'];
  for (const k of kandydaci) {
    const w = spawnSync(k, argumenty, { encoding: 'utf8', env: { ...process.env, ...env } });
    if (w.error) continue;
    if (w.status !== 0) throw new Error(`openssl ${argumenty[0]}: ${w.stderr.trim()}`);
    return w.stdout;
  }
  throw new Error('Nie znaleziono openssl (jest w Git for Windows).');
}

const pem = (der) => `-----BEGIN CERTIFICATE-----\n${der.toString('base64').match(/.{1,64}/g).join('\n')}\n-----END CERTIFICATE-----\n`;

// .cer (DER albo PEM) + klucz -> .p12 z łańcuchem Apple; klucz prywatny zostaje tylko w .p12
async function zrobP12(certDer, info) {
  const certPem = path.join(FOLDER, 'developer-id.pem');
  fs.writeFileSync(certPem, pem(certDer));
  const temat = openssl(['x509', '-in', certPem, '-noout', '-subject', '-enddate']);
  if (!/Developer ID Application/.test(temat)) throw new Error('To nie jest certyfikat Developer ID Application.');
  // pośrednie Apple dla Developer ID (G2 i starsze), żeby Mac na pewno złożył łańcuch
  let posrednie = '';
  for (const nazwa of ['DeveloperIDG2CA.cer', 'DeveloperIDCA.cer']) {
    const odp = await fetch(`https://www.apple.com/certificateauthority/${nazwa}`);
    if (odp.ok) posrednie += pem(Buffer.from(await odp.arrayBuffer()));
  }
  const plikPosrednich = path.join(FOLDER, 'apple-developer-id-ca.pem');
  fs.writeFileSync(plikPosrednich, posrednie);
  const haslo = crypto.randomBytes(24).toString('base64url');
  fs.writeFileSync(HASLO, haslo);
  // stare szyfrowanie PKCS#12 (3DES/SHA1), bo tylko takie czyta `security import` na Macu
  openssl(['pkcs12', '-export', '-inkey', KLUCZ, '-in', certPem, '-certfile', plikPosrednich,
    '-name', 'LivePL Developer ID Application', '-out', P12, '-passout', 'env:LIVEPL_P12',
    '-keypbe', 'PBE-SHA1-3DES', '-certpbe', 'PBE-SHA1-3DES', '-macalg', 'sha1'], { LIVEPL_P12: haslo });
  fs.writeFileSync(INFO, JSON.stringify({ ...info, temat: temat.trim() }, null, 2));
  for (const p of [KLUCZ, CSR]) fs.rmSync(p, { force: true });
  console.log('Gotowe: developer-id.p12 i developer-id-haslo.txt w', FOLDER);
  console.log(temat.trim().replace(/^/gm, '  '));
  console.log('Dalej (właściciel konta): node narzedzia/mac/ustaw-sekrety-mac.mjs');
}

if (fs.existsSync(P12)) {
  console.log('developer-id.p12 już jest w', FOLDER, '— nic nie robię (usuń go, żeby zrobić nowy).');
  process.exit(0);
}

const iCer = process.argv.indexOf('--cer');
if (iCer > 0) {
  if (!fs.existsSync(KLUCZ)) throw new Error('Brak developer-id-klucz.pem: wniosek musi pochodzić z pierwszego polecenia tego skryptu.');
  const dane = fs.readFileSync(process.argv[iCer + 1]);
  const der = /BEGIN CERTIFICATE/.test(dane.toString('latin1'))
    ? Buffer.from(dane.toString('latin1').replace(/-----[^-]+-----|\s/g, ''), 'base64')
    : dane;
  await zrobP12(der, { zrodlo: 'portal' });
  process.exit(0);
}

const [plikKlucza, issuerId] = process.argv.slice(2);
if (!plikKlucza || !issuerId) {
  console.error('Użycie: node narzedzia/mac/developer-id.mjs <AuthKey_XXXX.p8> <Issuer ID>   albo   --cer <plik.cer>');
  process.exit(2);
}
const keyId = path.basename(plikKlucza).replace(/^AuthKey_/, '').replace(/\.p8$/, '');
const kluczApi = fs.readFileSync(plikKlucza, 'utf8');
const b64url = (b) => Buffer.from(b).toString('base64url');
function token() {
  const teraz = Math.floor(Date.now() / 1000);
  const n = b64url(JSON.stringify({ alg: 'ES256', kid: keyId, typ: 'JWT' }));
  const t = b64url(JSON.stringify({ iss: issuerId, iat: teraz, exp: teraz + 15 * 60, aud: 'appstoreconnect-v1' }));
  return `${n}.${t}.${b64url(crypto.sign('sha256', Buffer.from(`${n}.${t}`), { key: kluczApi, dsaEncoding: 'ieee-p1363' }))}`;
}
async function api(metoda, sciezka, cialo) {
  const odp = await fetch(API + sciezka, { method: metoda, headers: { Authorization: `Bearer ${token()}`, 'Content-Type': 'application/json' }, body: cialo ? JSON.stringify(cialo) : undefined });
  const json = await odp.json().catch(() => ({}));
  if (!odp.ok) throw new Error(`${odp.status} ${(json.errors || []).map((e) => `${e.title}: ${e.detail}`).join('; ')}`);
  return json;
}

// klucz i wniosek (nowe tylko wtedy, gdy nie ma jeszcze wniosku czekającego na .cer z portalu)
if (!fs.existsSync(KLUCZ) || !fs.existsSync(CSR)) {
  openssl(['req', '-new', '-newkey', 'rsa:2048', '-nodes', '-keyout', KLUCZ, '-out', CSR,
    '-subj', '/CN=LivePL Developer ID Application/O=Live Production/C=PL']);
}
let cert = null;
for (const typ of ['DEVELOPER_ID_APPLICATION_G2', 'DEVELOPER_ID_APPLICATION']) {
  try {
    cert = (await api('POST', '/v1/certificates', { data: { type: 'certificates', attributes: { certificateType: typ, csrContent: fs.readFileSync(CSR, 'utf8') } } })).data;
    console.log(`Certyfikat ${typ}: wydany, ważny do ${cert.attributes.expirationDate.slice(0, 10)}`);
    break;
  } catch (e) {
    console.log(`${typ}: Apple odmówiło przez API (${e.message})`);
  }
}
if (!cert) {
  console.log(`\nWniosek zostaje w ${CSR}.`);
  console.log('Właściciel konta: developer.apple.com → Certificates → + → Developer ID Application (G2) → wgraj ten plik,');
  console.log('pobierz .cer i uruchom:  node narzedzia/mac/developer-id.mjs --cer <ścieżka do .cer>');
  process.exit(3);
}
await zrobP12(Buffer.from(cert.attributes.certificateContent, 'base64'), { id: cert.id, nazwa: cert.attributes.name, waznyDo: cert.attributes.expirationDate, zrodlo: 'api' });
