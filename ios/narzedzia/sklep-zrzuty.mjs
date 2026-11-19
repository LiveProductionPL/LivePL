// Wgrywa zrzuty z zrzuty-sklep.sh (artefakt „sklep-N” z GitHuba) do App Store Connect, wersja 1.0, język polski.
// Zastępuje zrzuty, które już tam są.
//
//   node ios/narzedzia/sklep-zrzuty.mjs <AuthKey klucza Admin> <Issuer ID> <folder ze zrzutami>
//
// W folderze: iphone-69/*.png (1320×2868) i ipad-13/*.png (2064×2752); pliki livepl-* idą do LivePL, poczta-* do Poczty LivePL,
// connect-* do LivePL Connect. Aplikację bez zrzutów w folderze pomija (jej zrzuty w App Store zostają).

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const [plikKlucza, issuerId, folder] = process.argv.slice(2);
if (!plikKlucza || !issuerId || !folder) { console.error('Użycie: node ios/narzedzia/sklep-zrzuty.mjs <AuthKey_XXXX.p8> <Issuer ID> <folder>'); process.exit(2); }
const keyId = path.basename(plikKlucza).replace(/^AuthKey_/, '').replace(/\.p8$/, '');
const kluczApi = fs.readFileSync(plikKlucza, 'utf8');
const b64url = (b) => Buffer.from(b).toString('base64url');
function token() {
  const t = Math.floor(Date.now() / 1000);
  const d = b64url(JSON.stringify({ alg: 'ES256', kid: keyId, typ: 'JWT' })) + '.' + b64url(JSON.stringify({ iss: issuerId, iat: t, exp: t + 900, aud: 'appstoreconnect-v1' }));
  return d + '.' + b64url(crypto.sign('sha256', Buffer.from(d), { key: kluczApi, dsaEncoding: 'ieee-p1363' }));
}
async function api(metoda, sciezka, cialo) {
  const odp = await fetch('https://api.appstoreconnect.apple.com' + sciezka, {
    method: metoda, headers: { Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' }, body: cialo ? JSON.stringify(cialo) : undefined
  });
  if (odp.status === 204) return null;
  const j = await odp.json().catch(() => ({}));
  if (!odp.ok) throw new Error(`${metoda} ${sciezka.split('?')[0]} → ${odp.status} ${(j.errors || []).map((e) => e.detail || e.title).join('; ')}`);
  return j;
}

const APLIKACJE = { 'eu.livepl.przegladarka': 'livepl-', 'eu.livepl.poczta': 'poczta-', 'eu.livepl.connect': 'connect-' };
const URZADZENIA = { 'iphone-69': 'APP_IPHONE_67', 'ipad-13': 'APP_IPAD_PRO_3GEN_129' };

for (const [bundle, przedrostek] of Object.entries(APLIKACJE)) {
  const app = (await api('GET', `/v1/apps?filter[bundleId]=${bundle}`)).data[0];
  if (!app) { console.log(`${bundle}: brak aplikacji w App Store Connect`); continue; }
  const wersja = (await api('GET', `/v1/apps/${app.id}/appStoreVersions?filter[platform]=IOS`)).data.find((v) => v.attributes.appStoreState === 'PREPARE_FOR_SUBMISSION');
  if (!wersja) { console.log(`${bundle}: brak wersji w przygotowaniu`); continue; }
  const lok = (await api('GET', `/v1/appStoreVersions/${wersja.id}/appStoreVersionLocalizations`)).data.find((l) => l.attributes.locale.startsWith('pl'));
  const zestawy = (await api('GET', `/v1/appStoreVersionLocalizations/${lok.id}/appScreenshotSets`)).data;
  for (const [katalog, typ] of Object.entries(URZADZENIA)) {
    const dir = path.join(folder, katalog);
    if (!fs.existsSync(dir)) continue;
    const pliki = fs.readdirSync(dir).filter((f) => f.startsWith(przedrostek) && f.endsWith('.png')).sort();
    if (!pliki.length) continue;
    let zestaw = zestawy.find((z) => z.attributes.screenshotDisplayType === typ);
    if (!zestaw) {
      zestaw = (await api('POST', '/v1/appScreenshotSets', { data: { type: 'appScreenshotSets', attributes: { screenshotDisplayType: typ },
        relationships: { appStoreVersionLocalization: { data: { type: 'appStoreVersionLocalizations', id: lok.id } } } } })).data;
    }
    for (const stary of (await api('GET', `/v1/appScreenshotSets/${zestaw.id}/appScreenshots`)).data) {
      await api('DELETE', `/v1/appScreenshots/${stary.id}`);
    }
    for (const plik of pliki) {
      const dane = fs.readFileSync(path.join(dir, plik));
      const zrzut = (await api('POST', '/v1/appScreenshots', { data: { type: 'appScreenshots', attributes: { fileName: plik, fileSize: dane.length },
        relationships: { appScreenshotSet: { data: { type: 'appScreenshotSets', id: zestaw.id } } } } })).data;
      for (const op of zrzut.attributes.uploadOperations) {
        const naglowki = Object.fromEntries((op.requestHeaders || []).map((h) => [h.name, h.value]));
        const odp = await fetch(op.url, { method: op.method, headers: naglowki, body: dane.subarray(op.offset, op.offset + op.length) });
        if (!odp.ok) throw new Error(`wysyłka ${plik}: ${odp.status}`);
      }
      await api('PATCH', `/v1/appScreenshots/${zrzut.id}`, { data: { type: 'appScreenshots', id: zrzut.id,
        attributes: { uploaded: true, sourceFileChecksum: crypto.createHash('md5').update(dane).digest('hex') } } });
      console.log(`${bundle} ${typ}: ${plik}`);
    }
  }
}
console.log('Gotowe. Apple przetwarza zrzuty kilka minut.');
