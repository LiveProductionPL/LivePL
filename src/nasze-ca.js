'use strict';

// Zaufanie do naszego urzędu certyfikacji (LivePL Root CA) w przeglądarce LivePL.
// Działa tylko dla naszych domen (assets/ca/domeny.json). Sprawdzamy cały łańcuch aż do
// naszego roota: podpisy, daty, nazwę strony i przeznaczenie certyfikatu (serwer WWW).
// Windows i inne przeglądarki o naszym CA nic nie wiedzą, chyba że ktoś doda go ręcznie.

const { X509Certificate } = require('crypto');
const fs = require('fs');
const path = require('path');

const FOLDER = path.join(__dirname, '..', 'assets', 'ca');
const EKU_SERWER = '1.3.6.1.5.5.7.3.1';
const ERR_CERT_AUTHORITY_INVALID = -202;

let rooty = [];
let domeny = [];
const zaufaneHosty = new Map();

function wczytaj() {
  rooty = [];
  const pliki = fs.existsSync(FOLDER)
    ? fs.readdirSync(FOLDER).filter(f => /\.(crt|pem)$/i.test(f)).map(f => path.join(FOLDER, f))
    : [];
  // LIVEPL_DODATKOWY_ROOT: próbny root do testów
  if (process.env.LIVEPL_DODATKOWY_ROOT) pliki.push(process.env.LIVEPL_DODATKOWY_ROOT);
  for (const p of pliki) {
    try {
      const c = new X509Certificate(fs.readFileSync(p));
      if (c.ca && c.checkIssued(c) && c.verify(c.publicKey)) rooty.push(c);
    } catch { /* uszkodzony plik pomijamy */ }
  }
  try {
    domeny = JSON.parse(fs.readFileSync(path.join(FOLDER, 'domeny.json'), 'utf8')).domeny || [];
  } catch {
    domeny = [];
  }
  if (process.env.LIVEPL_DODATKOWE_DOMENY) domeny.push(...process.env.LIVEPL_DODATKOWE_DOMENY.split(','));
  domeny = domeny.map(d => String(d).trim().toLowerCase()).filter(Boolean);
}

function wNaszychDomenach(host) {
  const h = String(host || '').toLowerCase().replace(/\.$/, '');
  return domeny.some(d => h === d || h.endsWith('.' + d));
}

function wazny(c, teraz) {
  return new Date(c.validFrom) <= teraz && teraz <= new Date(c.validTo);
}

function nazwa(c) {
  const m = /CN=([^\n,]+)/.exec(c.subject);
  return m ? m[1].trim() : 'LivePL CA';
}

// ---------------------------------------------------------------- ograniczenia nazw (RFC 5280, 4.2.1.10)
// Node nie odczytuje rozszerzenia nameConstraints, więc czytamy je sami z DER.
// Bierzemy nazwy DNS z listy dozwolonych i wykluczonych. Uszkodzone dane = odrzucamy certyfikat.

function tlv(buf, off) {
  if (off + 2 > buf.length) throw new Error('DER');
  const tag = buf[off];
  let dl = buf[off + 1];
  let p = off + 2;
  if (dl & 0x80) {
    const n = dl & 0x7f;
    if (n < 1 || n > 4) throw new Error('DER');
    dl = 0;
    for (let i = 0; i < n; i++) dl = dl * 256 + buf[p + i];
    p += n;
  }
  if (p + dl > buf.length) throw new Error('DER');
  return { tag, start: p, end: p + dl };
}

function dzieci(buf, rodzic) {
  const wynik = [];
  let o = rodzic.start;
  while (o < rodzic.end) {
    const t = tlv(buf, o);
    wynik.push(t);
    o = t.end;
  }
  return wynik;
}

function oid(buf, t) {
  const b = buf.subarray(t.start, t.end);
  const czesci = [Math.floor(b[0] / 40), b[0] % 40];
  let v = 0;
  for (let i = 1; i < b.length; i++) {
    v = v * 128 + (b[i] & 0x7f);
    if (!(b[i] & 0x80)) { czesci.push(v); v = 0; }
  }
  return czesci.join('.');
}

function ograniczeniaNazw(der) {
  const cert = tlv(der, 0);
  const tbs = dzieci(der, cert)[0];
  const rozszerzenia = dzieci(der, tbs).find(t => t.tag === 0xa3);
  if (!rozszerzenia) return null;
  const lista = dzieci(der, rozszerzenia)[0];
  for (const e of dzieci(der, lista)) {
    const cz = dzieci(der, e);
    if (oid(der, cz[0]) !== '2.5.29.30') continue;
    const wartosc = cz[cz.length - 1];
    const nc = tlv(der, wartosc.start);
    const wynik = { dozwolone: [], wykluczone: [] };
    for (const czesc of dzieci(der, nc)) {
      const cel = czesc.tag === 0xa0 ? wynik.dozwolone : czesc.tag === 0xa1 ? wynik.wykluczone : null;
      if (!cel) continue;
      for (const poddrzewo of dzieci(der, czesc)) {
        const baza = dzieci(der, poddrzewo)[0];
        if (baza && baza.tag === 0x82) cel.push(der.subarray(baza.start, baza.end).toString('ascii').toLowerCase());
      }
    }
    return wynik;
  }
  return null;
}

function nazwaDozwolona(host, nc) {
  const pasuje = (d) => {
    const domena = d.replace(/^\./, '');
    return host === domena || host.endsWith('.' + domena);
  };
  if (nc.wykluczone.some(pasuje)) return false;
  if (nc.dozwolone.length && !nc.dozwolone.some(pasuje)) return false;
  return true;
}

// łańcuch, który przysłał serwer (Chromium podaje go jako certyfikat z issuerCert)
function lancuch(cert) {
  const wynik = [];
  const widziane = new Set();
  let c = cert;
  while (c && c.data && !widziane.has(c.fingerprint) && wynik.length < 6) {
    widziane.add(c.fingerprint);
    try { wynik.push(new X509Certificate(c.data)); } catch { break; }
    c = c.issuerCert;
  }
  return wynik;
}

// Zwraca nazwę naszego roota, jeśli certyfikat strony jest w porządku, albo null.
function sprawdz(host, certyfikat) {
  if (!rooty.length || !wNaszychDomenach(host)) return null;
  const teraz = new Date();
  const ch = lancuch(certyfikat);
  if (!ch.length) return null;
  const lisc = ch[0];
  if (lisc.ca || !lisc.checkHost(host) || !wazny(lisc, teraz)) return null;
  const eku = lisc.keyUsage;
  if (Array.isArray(eku) && eku.length && !eku.includes(EKU_SERWER)) return null;

  const h = String(host).toLowerCase().replace(/\.$/, '');
  for (let i = 0; i < ch.length; i++) {
    const c = ch[i];
    if (!wazny(c, teraz)) return null;
    if (i > 0 && !c.ca) return null;
    // każdy urząd w łańcuchu może mieć wpisane domeny, dla których wolno mu wystawiać
    if (i > 0) {
      let nc;
      try { nc = ograniczeniaNazw(c.raw); } catch { return null; }
      if (nc && !nazwaDozwolona(h, nc)) return null;
    }
    const root = rooty.find(r => c.checkIssued(r) && c.verify(r.publicKey));
    if (root) return nazwa(root);
    const wystawca = ch[i + 1];
    if (!wystawca || !wystawca.ca || !c.checkIssued(wystawca) || !c.verify(wystawca.publicKey)) return null;
  }
  return null;
}

// co wiemy o certyfikacie każdej strony (do wskaźnika SSL na pasku)
const infoHostow = new Map();

function wlacz(ses) {
  wczytaj();
  ses.setCertificateVerifyProc((zadanie, odpowiedz) => {
    const host = String(zadanie.hostname || '').toLowerCase();
    const c = zadanie.certificate || {};
    const info = {
      wystawca: (c.issuer && c.issuer.commonName) || c.issuerName || '',
      wystawcaFirma: (c.issuer && c.issuer.organizations && c.issuer.organizations[0]) || '',
      waznyDo: c.validExpiry ? c.validExpiry * 1000 : null,
      ok: zadanie.errorCode === 0,
      nasz: null
    };
    // Chromium uznał certyfikat (np. Let's Encrypt): zostawiamy jego wynik
    if (zadanie.errorCode !== ERR_CERT_AUTHORITY_INVALID) {
      infoHostow.set(host, info);
      odpowiedz(-3);
      return;
    }
    const root = sprawdz(host, zadanie.certificate);
    if (root) {
      zaufaneHosty.set(host, root);
      infoHostow.set(host, { ...info, ok: true, nasz: root });
      odpowiedz(0);
    } else {
      infoHostow.set(host, info);
      odpowiedz(-3);
    }
  });
}

module.exports = {
  wlacz,
  // nazwa naszego urzędu, jeśli ta strona ma certyfikat od niego
  dlaHosta: (host) => zaufaneHosty.get(String(host || '').toLowerCase()) || null,
  infoHosta: (host) => infoHostow.get(String(host || '').toLowerCase()) || null,
  ileRootow: () => rooty.length
};
