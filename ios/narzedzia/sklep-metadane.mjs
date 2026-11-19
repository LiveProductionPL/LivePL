// Wpisuje do App Store Connect teksty i ustawienia z ios/sklep/metadane.json (obie aplikacje, wersja 1.0, język polski):
// nazwę, podtytuł, politykę prywatności, kategorie, copyright, opis, słowa kluczowe, tekst promocyjny, adresy
// i odpowiedzi do oceny wiekowej. Niczego nie wysyła do recenzji.
//
//   node ios/narzedzia/sklep-metadane.mjs <AuthKey klucza Admin> <Issuer ID> [--sprawdz]
//
// --sprawdz: tylko pokazuje, co jest teraz w App Store Connect.

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const [plikKlucza, issuerId] = process.argv.slice(2);
const tylkoSprawdz = process.argv.includes('--sprawdz');
if (!plikKlucza || !issuerId) { console.error('Użycie: node ios/narzedzia/sklep-metadane.mjs <AuthKey_XXXX.p8> <Issuer ID> [--sprawdz]'); process.exit(2); }
const keyId = path.basename(plikKlucza).replace(/^AuthKey_/, '').replace(/\.p8$/, '');
const kluczApi = fs.readFileSync(plikKlucza, 'utf8');
const META = JSON.parse(fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'sklep', 'metadane.json'), 'utf8'));

const b64url = (b) => Buffer.from(b).toString('base64url');
function token() {
  const t = Math.floor(Date.now() / 1000);
  const d = b64url(JSON.stringify({ alg: 'ES256', kid: keyId, typ: 'JWT' })) + '.' + b64url(JSON.stringify({ iss: issuerId, iat: t, exp: t + 900, aud: 'appstoreconnect-v1' }));
  return d + '.' + b64url(crypto.sign('sha256', Buffer.from(d), { key: kluczApi, dsaEncoding: 'ieee-p1363' }));
}
async function api(metoda, sciezka, cialo) {
  const odp = await fetch('https://api.appstoreconnect.apple.com' + sciezka, {
    method: metoda, headers: { Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' },
    body: cialo ? JSON.stringify(cialo) : undefined
  });
  if (odp.status === 204) return null;
  const j = await odp.json().catch(() => ({}));
  if (!odp.ok) throw new Error(`${metoda} ${sciezka.split('?')[0]} → ${odp.status} ${(j.errors || []).map((e) => e.detail || e.title).join('; ')}`);
  return j;
}
const patch = (typ, id, attributes, relationships) =>
  api('PATCH', `/v1/${typ}/${id}`, { data: { type: typ, id, ...(attributes ? { attributes } : {}), ...(relationships ? { relationships } : {}) } });

// odpowiedzi do oceny wiekowej: brak wszystkiego, poza dostępem do internetu (przeglądarka) i wiadomościami (poczta)
const ENUMY = ['alcoholTobaccoOrDrugUseOrReferences', 'contests', 'gamblingSimulated', 'gunsOrOtherWeapons', 'horrorOrFearThemes',
  'matureOrSuggestiveThemes', 'medicalOrTreatmentInformation', 'profanityOrCrudeHumor', 'sexualContentGraphicAndNudity',
  'sexualContentOrNudity', 'violenceCartoonOrFantasy', 'violenceRealistic', 'violenceRealisticProlongedGraphicOrSadistic'];
const LOGICZNE = ['gambling', 'lootBox', 'advertising', 'ageAssurance', 'healthOrWellnessTopics', 'parentalControls', 'userGeneratedContent', 'messagingAndChat', 'socialMedia'];

for (const [bundle, a] of Object.entries(META.aplikacje)) {
  const app = (await api('GET', `/v1/apps?filter[bundleId]=${bundle}`)).data[0];
  if (!app) { console.log(`${bundle}: brak aplikacji w App Store Connect`); continue; }
  console.log(`\n== ${a.name} (${bundle}, ${app.id})`);
  const info = (await api('GET', `/v1/apps/${app.id}/appInfos`)).data.find((i) => i.attributes.appStoreState !== 'READY_FOR_SALE') || null;
  const wersja = (await api('GET', `/v1/apps/${app.id}/appStoreVersions?filter[platform]=IOS`)).data
    .find((v) => ['PREPARE_FOR_SUBMISSION', 'DEVELOPER_REJECTED', 'REJECTED', 'METADATA_REJECTED'].includes(v.attributes.appStoreState));
  if (!info || !wersja) { console.log('Brak wersji w przygotowaniu.'); continue; }
  const infoLok = (await api('GET', `/v1/appInfos/${info.id}/appInfoLocalizations`)).data.find((l) => l.attributes.locale.startsWith('pl'));
  const wersjaLok = (await api('GET', `/v1/appStoreVersions/${wersja.id}/appStoreVersionLocalizations`)).data.find((l) => l.attributes.locale.startsWith('pl'));
  const wiek = (await api('GET', `/v1/appInfos/${info.id}/ageRatingDeclaration`)).data;

  if (tylkoSprawdz) {
    console.log('wersja', wersja.attributes.versionString, wersja.attributes.appStoreState, 'copyright:', wersja.attributes.copyright);
    console.log('info:', JSON.stringify(infoLok?.attributes));
    console.log('opis:', (wersjaLok?.attributes.description || '').slice(0, 60), '… słowa:', wersjaLok?.attributes.keywords);
    console.log('wiek:', JSON.stringify(wiek.attributes));
    continue;
  }

  await patch('appInfoLocalizations', infoLok.id, { name: a.name, subtitle: a.subtitle, privacyPolicyUrl: META.wspolne.privacyPolicyUrl });
  console.log('nazwa, podtytuł, polityka prywatności: zapisane');
  await patch('appInfos', info.id, null, {
    primaryCategory: { data: { type: 'appCategories', id: a.primaryCategory } },
    secondaryCategory: { data: { type: 'appCategories', id: a.secondaryCategory } }
  });
  console.log(`kategorie: ${a.primaryCategory}, ${a.secondaryCategory}`);
  await patch('appStoreVersions', wersja.id, { copyright: META.wspolne.copyright });
  await patch('appStoreVersionLocalizations', wersjaLok.id, {
    description: a.description, keywords: a.keywords, promotionalText: a.promotionalText,
    supportUrl: META.wspolne.supportUrl, marketingUrl: META.wspolne.marketingUrl
  });
  console.log('opis, słowa kluczowe, tekst promocyjny, adresy: zapisane');

  const odpowiedzi = {};
  for (const k of ENUMY) if (k in wiek.attributes) odpowiedzi[k] = 'NONE';
  for (const k of LOGICZNE) if (k in wiek.attributes) odpowiedzi[k] = false;
  odpowiedzi.unrestrictedWebAccess = !!a.ageRating.unrestrictedWebAccess;
  if ('messagingAndChat' in wiek.attributes) odpowiedzi.messagingAndChat = bundle === 'eu.livepl.poczta';
  const nieznane = Object.keys(wiek.attributes).filter((k) => !(k in odpowiedzi) && !['kidsAgeBand', 'ageRatingOverride', 'ageRatingOverrideV2', 'koreaAgeRatingOverride', 'developerAgeRatingInfoUrl', 'gracRatingClassificationNumber', 'socialMediaAgeRestricted'].includes(k));
  try {
    await patch('ageRatingDeclarations', wiek.id, odpowiedzi);
    console.log('ocena wiekowa: zapisana', nieznane.length ? `(bez odpowiedzi: ${nieznane.join(', ')})` : '');
  } catch (e) {
    console.log('ocena wiekowa: NIE zapisana –', e.message);
  }
}
