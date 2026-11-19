'use strict';
// Grafiki LivePL na Androida: Wstęga (dzień i noc), warstwy ikony adaptacyjnej (pierwszy plan
// i monochromatyczna do ikon motywowych), ikony programów i ikona 512 px do Google Play.
// Wstęga powstaje z tej samej wektorowej Wstęgi co na iPhonie (ios/narzedzia/logo.js) jako VectorDrawable.
// VectorDrawable nie zna pochylenia (skewX), więc łuki sygnału liczymy tutaj jako krzywe Béziera.
//
// Uruchom z katalogu przegladarka-livepl:  npx electron android/narzedzia/grafiki.js [folder na podgląd]

const { app, BrowserWindow } = require('electron');
const fs = require('fs');
const path = require('path');
const logo = require('../../ios/narzedzia/logo.js');

const KATALOG = path.resolve(__dirname, '..', '..');
const RES = path.join(KATALOG, 'android', 'app', 'src', 'main', 'res');
const SKLEP = path.join(KATALOG, 'android', 'sklep');
const PODGLAD = process.argv.slice(2).find((a) => !a.startsWith('-') && !a.endsWith('grafiki.js'));

const JASNA = { trzon: '#861a15', wstega: '#b0261f', sygnal: '#b0261f' };
const CIEMNA = { trzon: '#bdbdbd', wstega: '#ffffff', sygnal: '#e3463a' };
const CZARNA = { trzon: '#99000000', wstega: '#ff000000', sygnal: '#ff000000' };

// środek logo w siatce 512 (logo zajmuje x 62–412, y 130–382)
const SRODEK = [237, 256];
// skala w ikonie adaptacyjnej 108 dp: logo ma ok. 2/3 szerokości widocznej części (72 dp), jak ikona na iPhonie
const SKALA_IKONY = 0.14;

const liczba = (v) => Number(v.toFixed(2)).toString();
const punkt = ([x, y]) => `${liczba(x)},${liczba(y)}`;

/** Łuk okręgu (kąty w radianach) jako krzywe Béziera, po przekształceniu T. */
function luk(r, a0, a1, T, zamkniety) {
  const n = Math.max(1, Math.ceil((a1 - a0) / (Math.PI / 2) - 1e-9));
  const krok = (a1 - a0) / n;
  const k = (4 / 3) * Math.tan(krok / 4);
  const P = (a) => [r * Math.cos(a), r * Math.sin(a)];
  let d = 'M' + punkt(T(P(a0)));
  for (let i = 0; i < n; i++) {
    const b0 = a0 + i * krok;
    const b1 = b0 + krok;
    const [x0, y0] = P(b0);
    const [x3, y3] = P(b1);
    const c1 = [x0 - k * r * Math.sin(b0), y0 + k * r * Math.cos(b0)];
    const c2 = [x3 + k * r * Math.sin(b1), y3 - k * r * Math.cos(b1)];
    d += ` C${punkt(T(c1))} ${punkt(T(c2))} ${punkt(T([x3, y3]))}`;
  }
  return zamkniety ? d + ' Z' : d;
}

/** Kształty Wstęgi z logo.js: wielokąty, kropka i dwa łuki sygnału (już pochylone). */
function ksztalty(kolory) {
  const svg = logo(kolory);
  const wynik = [];
  for (const m of svg.matchAll(/<polygon fill="([^"]+)" points="([^"]+)"\/>/g)) {
    const pkt = m[2].trim().split(/\s+/).map((p) => p.split(',').map(Number));
    wynik.push({ fill: m[1], d: 'M' + pkt.map(punkt).join(' L') + ' Z' });
  }
  const g = svg.match(/<g transform="translate\(([-\d.]+) ([-\d.]+)\) skewX\(([-\d.]+)\)" fill="none" stroke="([^"]+)" stroke-width="([\d.]+)"/);
  if (!g) throw new Error('logo.js: nie ma grupy sygnału');
  const [cx, cy, skos, kolor, grubosc] = [+g[1], +g[2], +g[3], g[4], +g[5]];
  const t = Math.tan((skos * Math.PI) / 180);
  const T = ([x, y]) => [cx + x + t * y, cy + y];
  const kolo = svg.match(/<circle r="([\d.]+)" fill="([^"]+)"/);
  wynik.push({ fill: kolo[2], d: luk(+kolo[1], 0, 2 * Math.PI, T, true) });
  for (const m of svg.matchAll(/<path d="M ([-\d.]+) ([-\d.]+) A ([\d.]+) [\d.]+ 0 0 1 ([-\d.]+) ([-\d.]+)"\/>/g)) {
    const [x0, y0, r, x1, y1] = m.slice(1).map(Number);
    const a0 = Math.atan2(y0, x0);
    let a1 = Math.atan2(y1, x1);
    if (a1 < a0) a1 += 2 * Math.PI;
    wynik.push({ stroke: kolor, width: grubosc, d: luk(r, a0, a1, T, false) });
  }
  return wynik;
}

/** VectorDrawable; grupa przenosi siatkę 512 logo w pole rysunku. */
function wektor({ szer, wys, pole, grupa, kolory, opis }) {
  const atrybuty = Object.entries(grupa).map(([k, v]) => `android:${k}="${liczba(v)}"`).join(' ');
  const sciezki = ksztalty(kolory).map((k) => k.fill
    ? `        <path android:fillColor="${k.fill}" android:pathData="${k.d}" />`
    : `        <path android:strokeColor="${k.stroke}" android:strokeWidth="${k.width}" android:strokeLineCap="round" android:pathData="${k.d}" />`);
  return `<?xml version="1.0" encoding="utf-8"?>
<!-- ${opis}; plik tworzy android/narzedzia/grafiki.js z ios/narzedzia/logo.js -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${szer}dp"
    android:height="${wys}dp"
    android:viewportWidth="${pole[0]}"
    android:viewportHeight="${pole[1]}">
    <group ${atrybuty}>
${sciezki.join('\n')}
    </group>
</vector>
`;
}

/** To samo co VectorDrawable, ale jako SVG (do podglądu i ikony do Google Play). */
function svgZKsztaltow(kolory, transform) {
  const kolor = (c) => c.length === 9 ? `#${c.slice(3)}" fill-opacity="${parseInt(c.slice(1, 3), 16) / 255}` : c;
  return `<g transform="${transform}">` + ksztalty(kolory).map((k) => k.fill
    ? `<path fill="${kolor(k.fill)}" d="${k.d}"/>`
    : `<path fill="none" stroke="${k.stroke.length === 9 ? '#' + k.stroke.slice(3) : k.stroke}" stroke-width="${k.width}" stroke-linecap="round" d="${k.d}"/>`).join('') + '</g>';
}

const grupaWstegi = { translateX: -56, translateY: -122 };
const grupaIkony = {
  pivotX: SRODEK[0], pivotY: SRODEK[1], scaleX: SKALA_IKONY, scaleY: SKALA_IKONY,
  translateX: 54 - SRODEK[0], translateY: 54.5 - SRODEK[1],
};
const transformIkony = (rozmiar) => {
  const s = (rozmiar / 108) * SKALA_IKONY;
  return `translate(${rozmiar / 2} ${(54.5 / 108) * rozmiar}) scale(${s}) translate(${-SRODEK[0]} ${-SRODEK[1]})`;
};

function zapisz(plik, tresc) {
  fs.mkdirSync(path.dirname(plik), { recursive: true });
  fs.writeFileSync(plik, tresc);
  console.log(path.relative(KATALOG, plik));
}

app.whenReady().then(async () => {
  // Wstęga do nagłówka i paska: 44×32 dp, jasna na dzień, biała na noc
  zapisz(path.join(RES, 'drawable', 'wstega.xml'),
    wektor({ szer: 44, wys: 32, pole: [362, 264], grupa: grupaWstegi, kolory: JASNA, opis: 'Wstęga LivePL na dzień' }));
  zapisz(path.join(RES, 'drawable-night', 'wstega.xml'),
    wektor({ szer: 44, wys: 32, pole: [362, 264], grupa: grupaWstegi, kolory: CIEMNA, opis: 'Wstęga LivePL na noc' }));

  // ikona adaptacyjna: białe tło, czerwona Wstęga; monochromatyczna do ikon motywowych Androida 13+
  zapisz(path.join(RES, 'drawable', 'ikona_pierwszy_plan.xml'),
    wektor({ szer: 108, wys: 108, pole: [108, 108], grupa: grupaIkony, kolory: JASNA, opis: 'Ikona LivePL: pierwszy plan' }));
  zapisz(path.join(RES, 'drawable', 'ikona_monochromatyczna.xml'),
    wektor({ szer: 108, wys: 108, pole: [108, 108], grupa: grupaIkony, kolory: CZARNA, opis: 'Ikona LivePL: wersja jednobarwna' }));
  zapisz(path.join(RES, 'mipmap-anydpi', 'ic_launcher.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!-- ikona adaptacyjna LivePL; warstwy tworzy android/narzedzia/grafiki.js -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ikona_tlo" />
    <foreground android:drawable="@drawable/ikona_pierwszy_plan" />
    <monochrome android:drawable="@drawable/ikona_monochromatyczna" />
</adaptive-icon>
`);

  const okno = new BrowserWindow({ show: false, webPreferences: { offscreen: true } });
  await okno.loadURL('data:text/html,<body></body>');
  const rysuj = (svg, szer, wys) => okno.webContents.executeJavaScript(`(async () => {
    const i = new Image(); i.src = 'data:image/svg+xml;base64,' + ${JSON.stringify(Buffer.from(svg).toString('base64'))};
    await i.decode();
    const c = document.createElement('canvas'); c.width = ${szer}; c.height = ${wys};
    const x = c.getContext('2d'); x.imageSmoothingQuality = 'high'; x.drawImage(i, 0, 0, ${szer}, ${wys});
    return c.toDataURL('image/png');
  })()`);
  const zapiszPng = async (plik, svg, szer, wys) => {
    const url = await rysuj(svg, szer, wys);
    fs.mkdirSync(path.dirname(plik), { recursive: true });
    fs.writeFileSync(plik, Buffer.from(url.split(',')[1], 'base64'));
    console.log(path.relative(KATALOG, plik));
  };

  // ikona Poczty z assets/programy/poczta.svg (rysunek z gradientami, więc jako PNG)
  const poczta = fs.readFileSync(path.join(KATALOG, 'assets', 'programy', 'poczta.svg'), 'utf8');
  await zapiszPng(path.join(RES, 'drawable-nodpi', 'program_poczta.png'),
    poczta.replace('<svg ', '<svg width="160" height="160" '), 160, 160);

  // szerokie logotypy programów: dzień i noc bez zmian
  for (const id of ['liveproduction', 'tvml']) {
    for (const [pora, folder] of [['dzien', 'drawable-nodpi'], ['noc', 'drawable-night-nodpi']]) {
      const cel = path.join(RES, folder, `program_${id}.png`);
      fs.mkdirSync(path.dirname(cel), { recursive: true });
      fs.copyFileSync(path.join(KATALOG, 'assets', 'programy', `${id}-${pora}.png`), cel);
      console.log(path.relative(KATALOG, cel));
    }
  }

  // ikona 512×512 do Google Play: pełny kwadrat, Sklep sam zaokrągla rogi
  await zapiszPng(path.join(SKLEP, 'ikona-512.png'), `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
<rect width="512" height="512" fill="#ffffff"/>
<g transform="translate(256 259) scale(0.965) translate(-237 -256)">${logo(JASNA)}</g></svg>`, 512, 512);

  if (PODGLAD) {
    // podgląd: ikona w masce koła i „squircle”, ikona motywowa, Wstęga w dzień i w nocy
    const R = 216;
    const maska = (ksztalt) => ksztalt === 'kolo'
      ? `<clipPath id="m"><circle cx="${R / 2}" cy="${R / 2}" r="${(R * 36) / 108}"/></clipPath>`
      : `<clipPath id="m"><rect x="${(R * 18) / 108}" y="${(R * 18) / 108}" width="${(R * 72) / 108}" height="${(R * 72) / 108}" rx="${(R * 22) / 108}"/></clipPath>`;
    const ikona = (ksztalt, kolory, tlo) => `<svg xmlns="http://www.w3.org/2000/svg" width="${R}" height="${R}" viewBox="0 0 ${R} ${R}">
<defs>${maska(ksztalt)}</defs><rect width="${R}" height="${R}" fill="#777"/>
<g clip-path="url(#m)"><rect width="${R}" height="${R}" fill="${tlo}"/>${svgZKsztaltow(kolory, transformIkony(R))}
<circle cx="${R / 2}" cy="${R / 2}" r="${(R * 33) / 108}" fill="none" stroke="#0a0" stroke-opacity="0.35" stroke-dasharray="3 3"/></g></svg>`;
    fs.mkdirSync(PODGLAD, { recursive: true });
    await zapiszPng(path.join(PODGLAD, 'ikona-kolo.png'), ikona('kolo', JASNA, '#ffffff'), R, R);
    await zapiszPng(path.join(PODGLAD, 'ikona-squircle.png'), ikona('squircle', JASNA, '#ffffff'), R, R);
    await zapiszPng(path.join(PODGLAD, 'ikona-motywowa.png'), ikona('kolo', { trzon: '#99d0e8ff', wstega: '#ffd0e8ff', sygnal: '#ffd0e8ff' }, '#1e3a4a'), R, R);
    const wstega = (kolory, tlo) => `<svg xmlns="http://www.w3.org/2000/svg" width="440" height="320" viewBox="0 0 362 264">
<rect width="362" height="264" fill="${tlo}"/>${svgZKsztaltow(kolory, 'translate(-56 -122)')}</svg>`;
    await zapiszPng(path.join(PODGLAD, 'wstega-dzien.png'), wstega(JASNA, '#e9ecef'), 440, 320);
    await zapiszPng(path.join(PODGLAD, 'wstega-noc.png'), wstega(CIEMNA, '#0d0f12'), 440, 320);
    // dla porównania: oryginał z logo.js (z prawdziwym skewX)
    await zapiszPng(path.join(PODGLAD, 'wstega-oryginal.png'), `<svg xmlns="http://www.w3.org/2000/svg" width="440" height="320" viewBox="56 122 362 264">
<rect x="56" y="122" width="362" height="264" fill="#e9ecef"/>${logo(JASNA)}</svg>`, 440, 320);
  }

  app.quit();
});
