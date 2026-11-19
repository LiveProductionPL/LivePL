// Wpisuje do App Store Connect teksty i ustawienia z ios/sklep/metadane.json (wersja w przygotowaniu, język polski):
// nazwę, podtytuł, politykę prywatności, kategorie, copyright, opis, słowa kluczowe, tekst promocyjny, adresy,
// „Co nowego” (przy aktualizacji), odpowiedzi do oceny wiekowej i notatkę dla App Review. Niczego nie wysyła do recenzji.
// Imię, telefon i e-mail osoby kontaktowej dla App Review wpisuje Karol sam.
//
//   node ios/narzedzia/sklep-metadane.mjs <AuthKey klucza Admin> <Issuer ID> [--sprawdz] [--tylko <bundle id>]
//
// --sprawdz: tylko pokazuje, co jest teraz w App Store Connect. --tylko: jedna aplikacja, np. com.LiveProduction.TVML.

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const [plikKlucza, issuerId] = process.argv.slice(2);
const tylkoSprawdz = process.argv.includes('--sprawdz');
const tylko = process.argv.includes('--tylko') ? process.argv[process.argv.indexOf('--tylko') + 1] : null;
// notatka dla App Review według bundle id
const NOTATKI = { 'eu.livepl.przegladarka': 'notatkaLivePL', 'eu.livepl.poczta': 'notatkaPoczta', 'eu.livepl.connect': 'notatkaConnect', 'com.LiveProduction.TVML': 'notatkaTVML' };
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
const LOGICZNE = ['gambling', 'lootBox', 'advertising', 'ageAssurance', 'healthOrWellnessTopics', 'parentalControls', 'userGeneratedContent', 'messagingAndChat', 'socialMedia', 'sharedLocation', 'datingAndRelationships'];

for (const [bundle, a] of Object.entries(META.aplikacje)) {
  if (tylko && bundle !== tylko) continue;
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
    console.log('co nowego:', wersjaLok?.attributes.whatsNew);
    continue;
  }

  await patch('appInfoLocalizations', infoLok.id, { name: a.name, subtitle: a.subtitle, privacyPolicyUrl: a.privacyPolicyUrl || META.wspolne.privacyPolicyUrl });
  console.log('nazwa, podtytuł, polityka prywatności: zapisane');
  await patch('appInfos', info.id, null, {
    primaryCategory: { data: { type: 'appCategories', id: a.primaryCategory } },
    secondaryCategory: { data: a.secondaryCategory ? { type: 'appCategories', id: a.secondaryCategory } : null }
  });
  console.log(`kategorie: ${a.primaryCategory}, ${a.secondaryCategory || 'bez drugiej'}`);
  await patch('appStoreVersions', wersja.id, { copyright: META.wspolne.copyright });
  await patch('appStoreVersionLocalizations', wersjaLok.id, {
    description: a.description, keywords: a.keywords, promotionalText: a.promotionalText,
    supportUrl: a.supportUrl || META.wspolne.supportUrl, marketingUrl: a.marketingUrl || META.wspolne.marketingUrl,
    ...(a.whatsNew && wersja.attributes.versionString !== '1.0' ? { whatsNew: a.whatsNew } : {})
  });
  console.log('opis, słowa kluczowe, tekst promocyjny, adresy: zapisane');

  const odpowiedzi = {};
  for (const k of ENUMY) if (k in wiek.attributes) odpowiedzi[k] = 'NONE';
  for (const k of LOGICZNE) if (k in wiek.attributes) odpowiedzi[k] = false;
  odpowiedzi.unrestrictedWebAccess = !!a.ageRating.unrestrictedWebAccess;
  if ('messagingAndChat' in wiek.attributes) odpowiedzi.messagingAndChat = bundle === 'eu.livepl.poczta';
  Object.assign(odpowiedzi, a.ageRating.odpowiedzi || {});
  const nieznane = Object.keys(wiek.attributes).filter((k) => !(k in odpowiedzi) && !['kidsAgeBand', 'ageRatingOverride', 'ageRatingOverrideV2', 'koreaAgeRatingOverride', 'developerAgeRatingInfoUrl', 'gracRatingClassificationNumber', 'socialMediaAgeRestricted'].includes(k));
  try {
    await patch('ageRatingDeclarations', wiek.id, odpowiedzi);
    console.log('ocena wiekowa: zapisana', nieznane.length ? `(bez odpowiedzi: ${nieznane.join(', ')})` : '');
  } catch (e) {
    console.log('ocena wiekowa: NIE zapisana –', e.message);
  }

  const notatka = META.przegladApple[NOTATKI[bundle]];
  if (notatka) {
    const rev = await api('GET', `/v1/appStoreVersions/${wersja.id}/appStoreReviewDetail`).catch(() => null);
    if (rev?.data) await patch('appStoreReviewDetails', rev.data.id, { notes: notatka });
    else await api('POST', '/v1/appStoreReviewDetails', { data: { type: 'appStoreReviewDetails', attributes: { notes: notatka },
      relationships: { appStoreVersion: { data: { type: 'appStoreVersions', id: wersja.id } } } } });
    console.log('notatka dla App Review: zapisana');
  }
}
