'use strict';

// Silnik tłumacza LivePL: osobny proces (Electron utilityProcess), żeby tłumaczenie nie spowalniało okna.
// Działa w nim Bergamot – silnik Firefox Translations skompilowany do WebAssembly (src/bergamot,
// licencja MPL-2.0). Modele (słowniki) pobiera i sprawdza proces główny (tlumacz.js); tutaj są tylko
// rozpakowywane (zstd), sprawdzane sumą SHA-256 i ładowane do pamięci silnika.
// Proces nie łączy się z siecią. Po 10 minutach bez pracy LivePL go zamyka i zwalnia pamięć.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const crypto = require('crypto');
const vm = require('vm');

const KATALOG = path.join(__dirname, 'bergamot');
// suma SHA-256 skompresowanego pliku WASM (Remote Settings Mozilli: translations-wasm-v2, wersja 4.0)
const WASM_SHA256 = '327fcfc7b7e9d95f6fa0844ebf899cfc05469872ef02d3aef5783182839a6255';
// wyrównanie pamięci dla plików modelu (jak w Firefoksie)
const WYROWNANIE = { model: 256, lex: 64, vocab: 64, srcvocab: 64, trgvocab: 64 };
const MAKS_MODELI = 3;

let bergamot = null;
let usluga = null;
const modele = new Map(); // para → { model, uzyto }

const sha256 = (buf) => crypto.createHash('sha256').update(buf).digest('hex');

function zaladujSilnik() {
  if (bergamot) return Promise.resolve(bergamot);
  const spakowany = fs.readFileSync(path.join(KATALOG, 'bergamot-translator.wasm.zst'));
  if (sha256(spakowany) !== WASM_SHA256) throw new Error('Plik silnika tłumacza jest uszkodzony');
  const wasmBinary = zlib.zstdDecompressSync(spakowany);
  // kod kleju z Firefoksa bez zmian; definiuje funkcję loadBergamot
  const kod = fs.readFileSync(path.join(KATALOG, 'bergamot-translator.js'), 'utf8');
  const loadBergamot = vm.runInThisContext('(function () {\n' + kod + '\nreturn loadBergamot;\n})()', { filename: 'bergamot-translator.js' });
  return new Promise((ok, zle) => {
    const modul = loadBergamot({
      INITIAL_MEMORY: 41943040,
      print: () => {},
      printErr: () => {},
      onAbort: () => zle(new Error('Nie udało się uruchomić silnika tłumacza')),
      wasmBinary,
      onRuntimeInitialized: () => Promise.resolve().then(() => {
        bergamot = modul;
        usluga = new bergamot.BlockingService({ cacheSize: 0 });
        ok(bergamot);
      })
    });
  });
}

function konfiguracja(nazwaModelu) {
  const opcje = {
    'beam-size': '1', normalize: '1.0', 'word-penalty': '0', 'max-length-break': '128',
    'mini-batch-words': '1024', workspace: '128', 'max-length-factor': '2.0', 'skip-cost': 'true',
    'cpu-threads': '0', quiet: 'true', 'quiet-translation': 'true',
    'gemm-precision': /intgemm8\.bin$/.test(nazwaModelu || '') ? 'int8shiftAll' : 'int8shiftAlphaAll',
    alignment: 'soft'
  };
  const wciecie = '            ';
  return '\n' + Object.entries(opcje).map(([k, v]) => `${wciecie}${k}: ${v}`).join('\n') + '\n' + wciecie;
}

// opis: { para, z, na, pliki: { model: { plik, nazwa, hash }, lex, vocab | srcvocab + trgvocab } }
function model(opis) {
  const juz = modele.get(opis.para);
  if (juz) { juz.uzyto = Date.now(); return juz.model; }
  const pamiec = {};
  for (const [typ, p] of Object.entries(opis.pliki)) {
    if (!WYROWNANIE[typ]) continue;
    const dane = zlib.zstdDecompressSync(fs.readFileSync(p.plik));
    if (p.hash && sha256(dane) !== p.hash) throw new Error('Słownik tłumacza jest uszkodzony. Usuń pobrane słowniki w Ustawieniach tłumacza');
    const a = new bergamot.AlignedMemory(dane.byteLength, WYROWNANIE[typ]);
    a.getByteArrayView().set(dane);
    pamiec[typ] = a;
  }
  const slowniki = new bergamot.AlignedMemoryList();
  if (pamiec.vocab) slowniki.push_back(pamiec.vocab);
  else { slowniki.push_back(pamiec.srcvocab); slowniki.push_back(pamiec.trgvocab); }
  const m = new bergamot.TranslationModel(opis.z, opis.na, konfiguracja(opis.pliki.model.nazwa), pamiec.model, pamiec.lex || null, slowniki, null);
  modele.set(opis.para, { model: m, uzyto: Date.now() });
  // najdawniej używane modele wypadają z pamięci
  while (modele.size > MAKS_MODELI) {
    const [para, stary] = [...modele.entries()].sort((a, b) => a[1].uzyto - b[1].uzyto)[0];
    try { stary.model.delete(); } catch { /* już zwolniony */ }
    modele.delete(para);
  }
  return m;
}

// jeden krok tłumaczenia jednym modelem
function krok(m, teksty, html) {
  const wiadomosci = new bergamot.VectorString();
  const opcje = new bergamot.VectorResponseOptions();
  const puste = [];
  teksty.forEach((t, i) => {
    // pusty tekst psuje tłumaczenie całej paczki
    if (!String(t).trim()) { puste.push(i); return; }
    wiadomosci.push_back(String(t));
    opcje.push_back({ qualityScores: false, alignment: true, html: !!html });
  });
  let odpowiedzi = null;
  try {
    if (wiadomosci.size() === 0) return teksty.map(() => '');
    odpowiedzi = usluga.translate(m, wiadomosci, opcje);
    const wyniki = [];
    for (let i = 0; i < odpowiedzi.size(); i++) wyniki.push(odpowiedzi.get(i).getTranslatedText());
    let j = 0;
    return teksty.map((t, i) => (puste.includes(i) ? '' : wyniki[j++]));
  } finally {
    wiadomosci.delete();
    opcje.delete();
    if (odpowiedzi) { try { odpowiedzi.delete(); } catch { /* bez znaczenia */ } }
  }
}

// Przez angielski (np. niemiecki → angielski → polski) to dwa osobne kroki, a nie translateViaPivoting:
// ten przy HTML wrzuca cały tekst do pierwszego znacznika i gubi resztę.
async function tlumacz({ modele: opisy, teksty, html }) {
  await zaladujSilnik();
  let wynik = teksty;
  for (const m of opisy.map(model)) wynik = krok(m, wynik, html);
  return wynik;
}

process.parentPort.on('message', async (e) => {
  const z = e.data || {};
  try {
    if (z.typ !== 'tlumacz') throw new Error('nieznane polecenie');
    process.parentPort.postMessage({ nr: z.nr, wynik: await tlumacz(z) });
  } catch (err) {
    process.parentPort.postMessage({ nr: z.nr, blad: String((err && err.message) || err) });
  }
});
