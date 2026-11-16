'use strict';

// Certyfikat osobisty LivePL na YubiKey (karta inteligentna PIV).
// Klucz prywatny jest wyłącznie na YubiKey i nigdy go nie opuszcza. Windows widzi certyfikat z karty
// w magazynie użytkownika (Cert:\CurrentUser\My), a podpis robi sama karta: Windows pyta o PIN,
// potem trzeba dotknąć klucza. LivePL nie przechowuje żadnego klucza do logowania.

const { powershell } = require('./powershell');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const CLIENT_AUTH = '1.3.6.1.5.5.7.3.2';
const CA1 = (() => {
  try {
    // LIVEPL_TEST_CA1: tylko do testów z testowym urzędem; prawdziwe sprawdzenie i tak robi serwer
    const plik = process.env.LIVEPL_TEST_CA1 || path.join(__dirname, '..', 'assets', 'ca', 'posrednie', 'livepl-ca-1.crt');
    return new crypto.X509Certificate(fs.readFileSync(plik));
  } catch {
    return null;
  }
})();

let ostatnia = { lista: [], sprawdzono: 0 };

function opisz(x) {
  const cn = (/(?:^|\n)CN=([^\n]+)/.exec(x.subject) || [])[1] || 'Certyfikat osobisty';
  const email = (/email:([^,\s]+)/.exec(x.subjectAltName || '') || [])[1] || '';
  return {
    nazwa: cn.trim(),
    email,
    waznyDo: new Date(x.validTo).getTime(),
    odcisk: x.fingerprint256,
    odciskWindows: x.fingerprint.replace(/:/g, ''),
    pem: x.toString(),
    x509: x
  };
}

// certyfikaty osobiste LivePL CA 1 do logowania, z kluczem na karcie (YubiKey)
async function lista() {
  if (process.platform !== 'win32') return [];
  const json = await powershell(
    "$w = @(Get-ChildItem Cert:\\CurrentUser\\My | Where-Object { $_.HasPrivateKey -and $_.Issuer -match 'CN=LivePL CA 1(,|$)' } | ForEach-Object { [Convert]::ToBase64String($_.RawData) }); "
    + 'ConvertTo-Json -Compress -InputObject $w'
  );
  const teraz = Date.now();
  const wynik = [];
  for (const b64 of [].concat(JSON.parse(json || '[]'))) {
    try {
      const x = new crypto.X509Certificate(Buffer.from(b64, 'base64'));
      if (CA1 ? !x.verify(CA1.publicKey) : !/CN=LivePL CA 1/.test(x.issuer)) continue;
      if (teraz < new Date(x.validFrom).getTime() || teraz > new Date(x.validTo).getTime()) continue;
      if (!(x.keyUsage || []).includes(CLIENT_AUTH)) continue;
      // osobisty ma adres e-mail; certyfikat urządzenia (przeglądarki LivePL, klucz w TPM) go nie ma
      // i do panelu urzędu się nie nadaje, więc nie może trafić na listę certyfikatów osobistych
      const c = opisz(x);
      if (!c.email) continue;
      wynik.push(c);
    } catch { /* uszkodzony wpis w magazynie */ }
  }
  wynik.sort((a, b) => b.waznyDo - a.waznyDo);
  ostatnia = { lista: wynik, sprawdzono: teraz };
  return wynik;
}

// to, co może zobaczyć panel ustawień i strona urzędu (bez obiektów)
const krotko = (c) => ({ nazwa: c.nazwa, email: c.email, waznyDo: c.waznyDo, odcisk: c.odcisk });
const ostatniaLista = () => ({ lista: ostatnia.lista.map(krotko), sprawdzono: ostatnia.sprawdzono });

// ECDSA z Windows przychodzi jako r||s, serwer (OpenSSL) chce DER
function p1363naDer(sig) {
  const n = sig.length / 2;
  const liczba = (b) => {
    let i = 0;
    while (i < b.length - 1 && b[i] === 0) i++;
    let v = b.subarray(i);
    if (v[0] & 0x80) v = Buffer.concat([Buffer.from([0]), v]);
    return Buffer.concat([Buffer.from([0x02, v.length]), v]);
  };
  const r = liczba(sig.subarray(0, n));
  const s = liczba(sig.subarray(n));
  return Buffer.concat([Buffer.from([0x30, r.length + s.length]), r, s]);
}

function komunikat(e) {
  const kod = String(e.kod || '');
  const m = String(e.message || '');
  if (/^(8010006E|800704C7)$/.test(kod) || /anulowan|cancel/i.test(m)) return 'anulowano';
  if (kod === '8010006B' || /wrong PIN|zły kod PIN|nieprawidłowy kod PIN/i.test(m)) return 'YubiKey: zły PIN. Spróbuj jeszcze raz.';
  if (kod === '8010006C' || /blocked|zablokowan/i.test(m)) return 'YubiKey: PIN jest zablokowany. Odblokuj go kodem PUK w YubiKey Manager.';
  if (/^(8010000C|80100069|8010002E|80100017)$/.test(kod)) return 'YubiKey: włóż klucz i spróbuj jeszcze raz.';
  if (kod === '8010006A' || /naruszenia zabezpiecze|security violation/i.test(m)) return 'YubiKey: klucz nie został dotknięty na czas. Kliknij jeszcze raz i dotknij YubiKey, gdy zacznie migać.';
  if (e.przekroczono) return 'YubiKey: minął czas na PIN i dotknięcie klucza.';
  return 'YubiKey: podpis się nie udał (' + (m || kod).slice(0, 160) + ')';
}

const SKRYPT_PODPISU = [
  "$c = Get-Item ('Cert:\\CurrentUser\\My\\' + $env:LIVEPL_PODPIS_ODCISK)",
  '$dane = [Convert]::FromBase64String($env:LIVEPL_PODPIS_DANE)',
  '$alg = [Security.Cryptography.HashAlgorithmName]::SHA256',
  '$okno = if ($env:LIVEPL_PODPIS_OKNO) { [IntPtr][long]$env:LIVEPL_PODPIS_OKNO } else { [IntPtr]::Zero }',
  '$rsa = [Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPrivateKey($c)',
  'if ($rsa) {',
  '  if ($rsa -is [Security.Cryptography.RSACng] -and $okno -ne [IntPtr]::Zero) { $rsa.Key.ParentWindowHandle = $okno }',
  "  $p = $rsa.SignData($dane, $alg, [Security.Cryptography.RSASignaturePadding]::Pkcs1); [Console]::Out.Write('rsa:' + [Convert]::ToBase64String($p))",
  '} else {',
  '  $ec = [Security.Cryptography.X509Certificates.ECDsaCertificateExtensions]::GetECDsaPrivateKey($c)',
  "  if (-not $ec) { throw 'Nieobsługiwany rodzaj klucza' }",
  '  if ($ec -is [Security.Cryptography.ECDsaCng] -and $okno -ne [IntPtr]::Zero) { $ec.Key.ParentWindowHandle = $okno }',
  "  $p = $ec.SignData($dane, $alg); [Console]::Out.Write('ec:' + [Convert]::ToBase64String($p))",
  '}'
].join('\n');

// podpis jednorazowego wyzwania panelu „host|logowanie|wyzwanie” kluczem z YubiKey
async function podpisz(c, host, wyzwanie, okno) {
  if (!/^[A-Za-z0-9_-]{20,200}$/.test(String(wyzwanie))) throw new Error('Nieprawidłowe wyzwanie');
  const dane = Buffer.from(`${host}|logowanie|${wyzwanie}`);
  let wynik;
  try {
    wynik = await powershell(SKRYPT_PODPISU, {
      LIVEPL_PODPIS_ODCISK: c.odciskWindows,
      LIVEPL_PODPIS_DANE: dane.toString('base64'),
      LIVEPL_PODPIS_OKNO: okno ? String(okno) : ''
    }, 120000);
  } catch (e) {
    throw new Error(komunikat(e));
  }
  const [typ, b64] = wynik.split(':');
  let podpis = Buffer.from(b64 || '', 'base64');
  if (typ === 'ec') podpis = p1363naDer(podpis);
  if (!crypto.verify('sha256', dane, c.x509.publicKey, podpis)) throw new Error('YubiKey: podpis się nie zgadza z certyfikatem');
  return { certyfikat: c.pem, podpis: podpis.toString('base64') };
}

module.exports = { lista, ostatniaLista, podpisz, krotko };
