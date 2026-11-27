'use strict';

// Rejestracja tej przeglądarki w urzędzie LivePL (certyfikat „Urządzenie LivePL”).
// Klucz powstaje w module TPM komputera (Microsoft Platform Crypto Provider) i nie da się go
// skopiować ani wyeksportować; bez TPM w magazynie Windows, też bez eksportu. Wniosek idzie do
// cert.livepl.eu, administrator porównuje krótki kod i zatwierdza, a LivePL sam odbiera certyfikat
// i łączy go z kluczem (certreq -accept).

const { app, net, safeStorage } = require('electron');
const { execFile } = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { powershell } = require('./powershell');

const ADRES = (process.env.LIVEPL_URZAD || 'https://cert.livepl.eu').replace(/\/+$/, '');
const plikStanu = () => path.join(app.getPath('userData'), 'urzadzenie.json');
const ALFABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

const CA1 = (() => {
  try {
    const plik = process.env.LIVEPL_TEST_CA1 || path.join(__dirname, '..', 'assets', 'ca', 'posrednie', 'livepl-ca-1.crt');
    return new crypto.X509Certificate(fs.readFileSync(plik));
  } catch {
    return null;
  }
})();

// ---------------------------------------------------------------- stan

let stan = null;
function wczytaj() {
  if (stan) return stan;
  try { stan = JSON.parse(fs.readFileSync(plikStanu(), 'utf8')); } catch { stan = { stan: 'brak' }; }
  return stan;
}
function zapisz() {
  fs.writeFileSync(plikStanu(), JSON.stringify(stan, null, 2));
}
const ostatniStan = () => {
  const s = wczytaj();
  return {
    stan: s.stan, kod: s.kod || '', tpm: !!s.tpm, nazwa: s.nazwa || '', waznyDo: s.waznyDo || 0, wyslano: s.wyslano || 0, blad: s.blad || '',
    polaczenie: s.polaczenie || '', dysk: s.dysk || '', dpapi: s.dpapi, raport: s.raport || 0, odzyskano: s.odzyskano || 0
  };
};

// ---------------------------------------------------------------- DER i kod

function tlv(buf, off) {
  let dl = buf[off + 1];
  let p = off + 2;
  if (dl & 0x80) {
    const n = dl & 0x7f;
    dl = 0;
    for (let i = 0; i < n; i++) dl = (dl * 256) + buf[p + i];
    p += n;
  }
  return { tag: buf[off], start: off, tresc: p, koniec: p + dl };
}

function spkiZWniosku(pem) {
  const der = Buffer.from(pem.replace(/-----[^-]+-----|\s+/g, ''), 'base64');
  const wniosek = tlv(der, 0);
  const info = tlv(der, wniosek.tresc);
  let o = info.tresc;
  const wersja = tlv(der, o); o = wersja.koniec;
  const temat = tlv(der, o); o = temat.koniec;
  const spki = tlv(der, o);
  return der.subarray(spki.start, spki.koniec);
}

// ten sam algorytm co w panelu urzędu: 30 bitów SHA-256 klucza, alfabet bez I, O, 0, 1
function kodZKlucza(spkiDer) {
  const h = crypto.createHash('sha256').update(spkiDer).digest();
  const bity = (h[0] << 22) | (h[1] << 14) | (h[2] << 6) | (h[3] >> 2);
  let kod = '';
  for (let i = 5; i >= 0; i--) kod += ALFABET[(bity >> (i * 5)) & 31];
  return kod.slice(0, 3) + '-' + kod.slice(3);
}

// ---------------------------------------------------------------- certreq

function wykonaj(program, args) {
  return new Promise((ok, zle) => {
    execFile(program, args, { windowsHide: true, timeout: 120000 }, (blad, wyjscie, bledy) => {
      if (blad) zle(new Error(String(bledy || wyjscie || blad.message).trim().split(/\r?\n/).slice(-2).join(' ')));
      else ok(String(wyjscie || ''));
    });
  });
}
const certreq = (args) => wykonaj('certreq.exe', args);
const certutil = (args) => wykonaj('certutil.exe', args);

const nazwaKomputera = () => (os.hostname().replace(/[^A-Za-z0-9-]/g, '') || 'komputer').slice(0, 40);

async function utworzWniosek() {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'livepl-urzadzenie-'));
  try {
    for (const [dostawca, tpm] of [['Microsoft Platform Crypto Provider', true], ['Microsoft Software Key Storage Provider', false]]) {
      const inf = [
        '[Version]',
        'Signature="$Windows NT$"',
        '[NewRequest]',
        `Subject = "CN=LivePL ${nazwaKomputera()}, O=Live Production, C=PL"`,
        'KeyAlgorithm = RSA',
        'KeyLength = 2048',
        `ProviderName = "${dostawca}"`,
        'Exportable = FALSE',
        'MachineKeySet = FALSE',
        'KeyUsage = 0x80',
        'RequestType = PKCS10',
        'HashAlgorithm = SHA256',
        'FriendlyName = "LivePL - urzadzenie"',
        '[EnhancedKeyUsageExtension]',
        'OID=1.3.6.1.5.5.7.3.2',
        ''
      ].join('\r\n');
      const plikInf = path.join(tmp, 'wniosek.inf');
      const plikCsr = path.join(tmp, 'wniosek.csr');
      fs.writeFileSync(plikInf, inf, 'ascii');
      try { fs.unlinkSync(plikCsr); } catch { /* nie było */ }
      try {
        await certreq(['-new', '-q', '-f', plikInf, plikCsr]);
        const surowy = fs.readFileSync(plikCsr, 'utf8');
        const b64 = surowy.replace(/-----[^-]+-----|\s+/g, '');
        const pem = '-----BEGIN CERTIFICATE REQUEST-----\n' + b64.match(/.{1,64}/g).join('\n') + '\n-----END CERTIFICATE REQUEST-----\n';
        return { pem, tpm };
      } catch (e) {
        if (!tpm) throw e;
        // TPM niedostępny albo odmówił: spróbujemy kluczem programowym Windows (też bez eksportu)
      }
    }
    throw new Error('Nie udało się utworzyć klucza');
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

// ---------------------------------------------------------------- urząd

async function zapytaj(sciezka, opcje = {}) {
  const r = await net.fetch(ADRES + sciezka, { ...opcje, headers: { 'Content-Type': 'application/json', ...(opcje.headers || {}) } });
  let dane = {};
  try { dane = await r.json(); } catch { /* bez treści */ }
  if (!r.ok && r.status !== 404) throw new Error(dane.blad || `urząd odpowiedział ${r.status}`);
  return { status: r.status, dane };
}

// ---------------------------------------------------------------- numer rejestracji przy certyfikacie
// Numer rejestracji zapisujemy też w nazwie certyfikatu w Windows („LivePL - urzadzenie <numer>”).
// Gdy plik urzadzenie.json zniknie (np. nowy profil LivePL), przeglądarka odnajduje swój certyfikat,
// pyta urząd o ten numer i wraca do tej samej rejestracji zamiast zakładać nową z nowym kluczem.

const NAZWA_CERT = 'LivePL - urzadzenie';

async function oznaczCertyfikat(s) {
  if (process.platform !== 'win32' || !s.odcisk || !s.id || s.oznaczony === s.id) return;
  await powershell(
    "$c = Get-Item ('Cert:\\CurrentUser\\My\\' + $env:LIVEPL_ODCISK) -ErrorAction Stop; $c.FriendlyName = $env:LIVEPL_NAZWA",
    { LIVEPL_ODCISK: s.odcisk, LIVEPL_NAZWA: `${NAZWA_CERT} ${s.id}` });
  s.oznaczony = s.id;
  zapisz();
}

async function certyfikatyZNumerem() {
  if (process.platform !== 'win32') return [];
  const json = (await powershell(
    "$l = @(Get-ChildItem Cert:\\CurrentUser\\My | Where-Object { $_.HasPrivateKey -and $_.NotAfter -gt (Get-Date) -and $_.FriendlyName -like ($env:LIVEPL_NAZWA + ' *') } | " +
    'Sort-Object NotBefore -Descending | ForEach-Object { $tpm = $null; ' +
    'try { $tpm = [System.Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPrivateKey($_).Key.Provider.Provider -eq "Microsoft Platform Crypto Provider" } catch {} ' +
    '[pscustomobject]@{ nazwa = $_.FriendlyName; der = [Convert]::ToBase64String($_.RawData); tpm = $tpm } }); ' +
    'ConvertTo-Json -InputObject $l -Compress',
    { LIVEPL_NAZWA: NAZWA_CERT })).trim();
  try {
    const w = JSON.parse(json || '[]');
    return Array.isArray(w) ? w : (w ? [w] : []);
  } catch {
    return [];
  }
}

// zwraca true, gdy przywróciliśmy wcześniejszą rejestrację tego komputera
async function odzyskaj() {
  const s = wczytaj();
  if (s.stan !== 'brak' || s.csr || s.id) return false;
  for (const c of await certyfikatyZNumerem()) {
    const id = (String(c.nazwa).match(/ ([a-f0-9]{32})$/) || [])[1];
    if (!id) continue;
    let x;
    try { x = new crypto.X509Certificate(Buffer.from(c.der, 'base64')); } catch { continue; }
    if (CA1 && !(x.checkIssued(CA1) && x.verify(CA1.publicKey))) continue;
    const { status, dane } = await zapytaj('/urzadzenia/stan/' + id);
    if (status !== 200 || dane.stan !== 'wydany' || !dane.certyfikat) continue;
    // urząd musi znać dokładnie ten certyfikat, nie tylko ten numer
    if (new crypto.X509Certificate(dane.certyfikat).fingerprint !== x.fingerprint) continue;
    const spki = x.publicKey.export({ type: 'spki', format: 'der' });
    stan = {
      stan: 'gotowe', id, kod: kodZKlucza(spki), nazwa: nazwaKomputera(), tpm: c.tpm !== false,
      odcisk: x.fingerprint.replace(/:/g, ''), waznyDo: new Date(x.validTo).getTime(),
      oznaczony: id, odzyskano: Date.now(), blad: ''
    };
    zapisz();
    return true;
  }
  return false;
}

let trwa = false;

// pierwszy krok: klucz, wniosek i wysłanie (albo ponowne wysłanie tego samego wniosku)
async function zarejestruj() {
  // klucz przeglądarki powstaje w TPM przez certreq (Windows); na Macu będzie w pęku kluczy, w kolejnej wersji
  if (process.platform !== 'win32') throw new Error('Rejestracja w urzędzie LivePL działa na razie tylko w Windows');
  if (trwa) throw new Error('Rejestracja już trwa');
  // ten komputer był już zarejestrowany: wracamy do tamtej rejestracji, bez nowego wpisu w urzędzie
  try {
    if (await odzyskaj()) return ostatniStan();
  } catch { /* nie udało się sprawdzić: zwykła rejestracja */ }
  trwa = true;
  try {
    const s = wczytaj();
    if (!s.csr || ['odrzucony', 'uniewazniony', 'blad-klucza'].includes(s.stan)) {
      const w = await utworzWniosek();
      Object.assign(s, { csr: w.pem, tpm: w.tpm, kod: kodZKlucza(spkiZWniosku(w.pem)), nazwa: nazwaKomputera() });
    }
    const { dane } = await zapytaj('/urzadzenia/wniosek', {
      method: 'POST',
      body: JSON.stringify({
        csr: s.csr,
        nazwa: s.nazwa,
        uzytkownik: (() => { try { return os.userInfo().username; } catch { return ''; } })(),
        system: `${os.version ? os.version() : 'Windows'} ${os.release()}`,
        wersja: app.getVersion(),
        tpm: s.tpm
      })
    });
    if (!dane.id) throw new Error(dane.blad || 'urząd nie przyjął wniosku');
    if (dane.kod && dane.kod !== s.kod) throw new Error('Kod z urzędu nie zgadza się z kluczem');
    Object.assign(s, { id: dane.id, stan: 'wyslany', wyslano: Date.now(), blad: '' });
    stan = s;
    zapisz();
  } finally {
    trwa = false;
  }
  // urząd wystawia certyfikat od razu (rejestracja automatyczna): odbieramy go bez czekania;
  // jeśli się nie uda, wniosek jest wysłany i LivePL spróbuje później sam
  try { await sprawdz(); } catch (e) { zapiszBlad(e); }
  return ostatniStan();
}

// odbiór certyfikatu po zatwierdzeniu; zwraca true, gdy właśnie się zarejestrowaliśmy
async function sprawdz() {
  const s = wczytaj();
  if (!s.id || s.stan !== 'wyslany') return false;
  const { status, dane } = await zapytaj('/urzadzenia/stan/' + s.id);
  s.sprawdzono = Date.now();
  if (status === 404 || dane.stan === 'nieznany') {
    // urząd nie zna wniosku (np. dane odtworzone): wyślemy ten sam wniosek jeszcze raz
    s.stan = 'brak';
    zapisz();
    return false;
  }
  if (dane.stan === 'odrzucony' || dane.stan === 'uniewazniony') {
    s.stan = dane.stan;
    zapisz();
    return false;
  }
  if (dane.stan !== 'wydany' || !dane.certyfikat) {
    zapisz();
    return false;
  }
  const x = new crypto.X509Certificate(dane.certyfikat);
  if (CA1 && !(x.checkIssued(CA1) && x.verify(CA1.publicKey))) throw new Error('Certyfikat nie pochodzi z LivePL CA 1');
  const naszKlucz = crypto.createPublicKey({ key: spkiZWniosku(s.csr), format: 'der', type: 'spki' }).export({ type: 'spki', format: 'der' });
  if (!x.publicKey.export({ type: 'spki', format: 'der' }).equals(naszKlucz)) throw new Error('Certyfikat nie pasuje do klucza tej przeglądarki');

  const odcisk = x.fingerprint.replace(/:/g, '');
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'livepl-urzadzenie-'));
  try {
    // Windows sprawdza podpis certyfikatu przy łączeniu z kluczem, więc potrzebuje LivePL CA 1
    // w „Pośrednich urzędach” (bierzemy kopię z LivePL, nie z odpowiedzi serwera; to nie daje zaufania)
    if (CA1) {
      const plikCa = path.join(tmp, 'livepl-ca-1.cer');
      fs.writeFileSync(plikCa, CA1.raw);
      await certutil(['-user', '-f', '-addstore', 'CA', plikCa]);
    }
    const plik = path.join(tmp, 'urzadzenie.cer');
    fs.writeFileSync(plik, x.raw);
    const maKlucz = async () => (await powershell(
      "[bool](Get-Item ('Cert:\\CurrentUser\\My\\' + $env:LIVEPL_ODCISK) -ErrorAction SilentlyContinue).HasPrivateKey",
      { LIVEPL_ODCISK: odcisk })).trim() === 'True';
    try {
      await certreq(['-accept', '-q', '-user', plik]);
    } catch {
      // np. Windows jeszcze nie ufa rootowi LivePL: wgrywamy certyfikat sami; Windows łączy go
      // z kluczem z TPM po kluczu publicznym (oczekujący wniosek wskazuje, gdzie jest klucz)
      await powershell('Import-Certificate -FilePath $env:LIVEPL_PLIK -CertStoreLocation Cert:\\CurrentUser\\My | Out-Null', { LIVEPL_PLIK: plik });
      if (!(await maKlucz())) {
        await wykonaj('certutil.exe', ['-user', '-silent', '-repairstore', 'My', x.serialNumber]).catch(() => {});
      }
    }
    if (!(await maKlucz())) throw new Error('Windows nie połączył certyfikatu z kluczem tej przeglądarki');
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
  Object.assign(s, { stan: 'gotowe', odcisk, waznyDo: new Date(x.validTo).getTime(), blad: '' });
  zapisz();
  try { await oznaczCertyfikat(s); } catch { /* spróbujemy przy następnym raporcie */ }
  return true;
}

// ---------------------------------------------------------------- raport bezpieczeństwa
// Zarejestrowana przeglądarka mówi urzędowi co kilka godzin, czy dysk systemowy jest zaszyfrowany
// (BitLocker) i czy LivePL szyfruje swoje dane (DPAPI). Urząd odpowiada stanem certyfikatu
// i tym, jakim połączeniem przyszedł raport (HTTPS, wersja TLS). Panel urzędu to pokazuje.

const CO_ILE_RAPORT = 6 * 3600 * 1000;

// BitLocker dysku systemowego bez uprawnień administratora (właściwość powłoki Windows)
async function dyskSystemowy() {
  if (process.platform !== 'win32') return 'nieznany';
  try {
    const w = (await powershell("$v = (New-Object -ComObject Shell.Application).NameSpace($env:SystemDrive + '\\').Self.ExtendedProperty('System.Volume.BitLockerProtection'); [Console]::Out.Write([string]$v)")).trim();
    if (w === '1' || w === '6') return 'bitlocker';
    if (w === '3') return 'szyfrowanie';
    if (/^(0|2|4|5|8)$/.test(w)) return 'brak';
  } catch { /* bez znaczenia, raport pójdzie z „nieznany” */ }
  return 'nieznany';
}

async function szyfrowanieDanych() {
  try {
    return typeof safeStorage.isAsyncEncryptionAvailable === 'function'
      ? !!(await safeStorage.isAsyncEncryptionAvailable())
      : safeStorage.isEncryptionAvailable();
  } catch {
    return false;
  }
}

// zwraca true, gdy urząd unieważnił tę przeglądarkę
async function raport(teraz = false) {
  const s = wczytaj();
  if (!s.id || s.stan !== 'gotowe') return false;
  if (!teraz && s.raport && Date.now() - s.raport < CO_ILE_RAPORT) return false;
  const [dysk, dpapi] = await Promise.all([dyskSystemowy(), szyfrowanieDanych()]);
  const { status, dane } = await zapytaj('/urzadzenia/raport/' + s.id, {
    method: 'POST',
    body: JSON.stringify({ dysk, dpapi, wersja: app.getVersion() })
  });
  Object.assign(s, { dysk, dpapi, raport: Date.now() });
  if (status === 200 && typeof dane.polaczenie === 'string') s.polaczenie = dane.polaczenie.slice(0, 80);
  const uniewazniony = dane.stan === 'uniewazniony';
  if (uniewazniony) s.stan = 'uniewazniony';
  zapisz();
  // rejestracje sprzed 0.1.10 dostają numer przy certyfikacie przy pierwszym raporcie
  if (!uniewazniony) { try { await oznaczCertyfikat(s); } catch { /* następnym razem */ } }
  return uniewazniony;
}

function zapiszBlad(e) {
  const s = wczytaj();
  s.blad = String(e && e.message || e).slice(0, 200);
  zapisz();
}

module.exports = { ostatniStan, zarejestruj, odzyskaj, sprawdz, raport, zapiszBlad, kodZKlucza, spkiZWniosku };
