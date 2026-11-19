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
// grafiki do Google Play w układzie katalogów fastlane: sklep/<pakiet>/pl-PL/images
const OBRAZY_LIVEPL = path.join(SKLEP, 'eu.livepl.przegladarka', 'pl-PL', 'images');
const OBRAZY_POCZTY = path.join(SKLEP, 'eu.livepl.poczta', 'pl-PL', 'images');
const OBRAZY_CONNECT = path.join(SKLEP, 'eu.livepl.connect', 'pl-PL', 'images');

// LivePL Connect: ekran z czerwoną strzałką kursora, przerysowany z connect/server/web/ikona-512.png (siatka 512).
// Treść zajmuje ok. x 93–405, y 98–398; ekran ma przerwę tam, gdzie wchodzi strzałka.
function ikonaConnect(ekran = '#eef0f2', strzalka = 'url(#strzalka)') {
  return `<defs><linearGradient id="strzalka" x1="0" y1="0" x2="0" y2="1">
<stop offset="0" stop-color="#e94b3c"/><stop offset="1" stop-color="#b0261f"/></linearGradient></defs>
<path d="M262 321 H135 Q107 321 107 293 V140 Q107 112 135 112 H365 Q393 112 393 140 V298" fill="none" stroke="${ekran}" stroke-width="28" stroke-linejoin="round"/>
<polygon points="275,192 405,321 346,321 378,385 350,398 318,333 275,375" fill="${strzalka}"/>`;
}
const PODGLAD = process.argv.slice(2).find((a) => !a.startsWith('-') && !a.endsWith('grafiki.js'));

const JASNA = { trzon: '#861a15', wstega: '#b0261f', sygnal: '#b0261f' };
const CIEMNA = { trzon: '#bdbdbd', wstega: '#ffffff', sygnal: '#e3463a' };
const CZARNA = { trzon: '#99000000', wstega: '#ff000000', sygnal: '#ff000000' };

// środek logo w siatce 512 (logo zajmuje x 62–412, y 130–382)
const SRODEK = [237, 256];
// skala w ikonie adaptacyjnej 108 dp: logo ma ok. 2/3 szerokości widocznej części (72 dp), jak ikona na iPhonie
const SKALA_IKONY = 0.14;

const liczba = (v) => Number(v.toFixed(2)).toString();

/** PNG bez kanału alfa (Google Play wymaga tego dla grafiki funkcji). */
function pngRGB(szer, wys, rgba) {
  const zlib = require('zlib');
  const wiersz = szer * 3 + 1;
  const surowe = Buffer.alloc(wiersz * wys);
  for (let y = 0; y < wys; y++) {
    surowe[y * wiersz] = 0;
    for (let x = 0; x < szer; x++) {
      const i = (y * szer + x) * 4, o = y * wiersz + 1 + x * 3;
      surowe[o] = rgba[i]; surowe[o + 1] = rgba[i + 1]; surowe[o + 2] = rgba[i + 2];
    }
  }
  const crcTab = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = (b) => { let c = 0xffffffff; for (const v of b) c = crcTab[(c ^ v) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const kawalek = (typ, dane) => {
    const d = Buffer.alloc(4); d.writeUInt32BE(dane.length);
    const t = Buffer.concat([Buffer.from(typ), dane]);
    const c = Buffer.alloc(4); c.writeUInt32BE(crc(t));
    return Buffer.concat([d, t, c]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(szer, 0); ihdr.writeUInt32BE(wys, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), kawalek('IHDR', ihdr),
    kawalek('IDAT', zlib.deflateSync(surowe, { level: 9 })), kawalek('IEND', Buffer.alloc(0))]);
}
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
  const rysuj = (svg, szer, wys, jak) => okno.webContents.executeJavaScript(`(async () => {
    const i = new Image(); i.src = 'data:image/svg+xml;base64,' + ${JSON.stringify(Buffer.from(svg).toString('base64'))};
    await i.decode();
    const c = document.createElement('canvas'); c.width = ${szer}; c.height = ${wys};
    const x = c.getContext('2d'); x.imageSmoothingQuality = 'high'; x.drawImage(i, 0, 0, ${szer}, ${wys});
    return ${jak === 'rgba' ? `Array.from(x.getImageData(0, 0, ${szer}, ${wys}).data)` : `c.toDataURL('image/png')`};
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
  await zapiszPng(path.join(OBRAZY_LIVEPL, 'icon.png'), `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
<rect width="512" height="512" fill="#ffffff"/>
<g transform="translate(256 259) scale(0.965) translate(-237 -256)">${logo(JASNA)}</g></svg>`, 512, 512);

  // ---------- Poczta LivePL: logo poczty (L + koperta) na granacie nagłówka poczty (#071c2d), jak na iPhonie ----------
  const POCZTA_RES = path.join(KATALOG, 'android', 'app', 'src', 'poczta', 'res');
  const logoPoczty = poczta.replace(/<svg[^>]*>/, '').replace('</svg>', '');
  // treść poczta.svg zajmuje ok. x 4–60, y 8–57 w siatce 64; w ikonie ok. 62% widocznej części (72 z 108 dp), lekko w prawo
  const warstwaPoczty = (tresc, px) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="${px}" height="${px}">
<g transform="translate(57.6 52.2) scale(0.8) translate(-32 -32.5)">${tresc}</g></svg>`;
  // warstwa do ikon motywowych Androida 13+: sam kształt (system go koloruje)
  const jednobarwnePoczty = logoPoczty.replace(/(fill|stroke)="url\(#[^)]+\)"/g, '$1="#ffffff"');
  for (const [folder, px] of [['mipmap-mdpi', 108], ['mipmap-hdpi', 162], ['mipmap-xhdpi', 216], ['mipmap-xxhdpi', 324], ['mipmap-xxxhdpi', 432]]) {
    await zapiszPng(path.join(POCZTA_RES, folder, 'poczta_pierwszy_plan.png'), warstwaPoczty(logoPoczty, px), px, px);
    await zapiszPng(path.join(POCZTA_RES, folder, 'poczta_jednobarwna.png'), warstwaPoczty(jednobarwnePoczty, px), px, px);
  }
  zapisz(path.join(POCZTA_RES, 'mipmap-anydpi', 'ic_launcher.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!-- ikona adaptacyjna Poczty LivePL; warstwy tworzy android/narzedzia/grafiki.js -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ikona_tlo" />
    <foreground android:drawable="@mipmap/poczta_pierwszy_plan" />
    <monochrome android:drawable="@mipmap/poczta_jednobarwna" />
</adaptive-icon>
`);
  const ikonaPoczty512 = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="512" height="512">
<rect width="64" height="64" fill="#071c2d"/><g transform="translate(35.2 30.6) scale(0.7) translate(-32 -32.5)">${logoPoczty}</g></svg>`;
  await zapiszPng(path.join(OBRAZY_POCZTY, 'icon.png'), ikonaPoczty512, 512, 512);

  // ---------- LivePL Connect: ikona adaptacyjna (tło z gradientu w connect/res/drawable) i do Sklepu ----------
  const CONNECT_RES = path.join(KATALOG, 'android', 'app', 'src', 'connect', 'res');
  const warstwaConnect = (tresc, px) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="${px}" height="${px}">
<g transform="translate(54 54) scale(0.145) translate(-249 -248)">${tresc}</g></svg>`;
  for (const [folder, px] of [['mipmap-mdpi', 108], ['mipmap-hdpi', 162], ['mipmap-xhdpi', 216], ['mipmap-xxhdpi', 324], ['mipmap-xxxhdpi', 432]]) {
    await zapiszPng(path.join(CONNECT_RES, folder, 'connect_pierwszy_plan.png'), warstwaConnect(ikonaConnect(), px), px, px);
    await zapiszPng(path.join(CONNECT_RES, folder, 'connect_jednobarwna.png'), warstwaConnect(ikonaConnect('#ffffff', '#ffffff'), px), px, px);
  }
  zapisz(path.join(CONNECT_RES, 'mipmap-anydpi', 'ic_launcher.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!-- ikona adaptacyjna LivePL Connect; warstwy tworzy android/narzedzia/grafiki.js -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/connect_tlo" />
    <foreground android:drawable="@mipmap/connect_pierwszy_plan" />
    <monochrome android:drawable="@mipmap/connect_jednobarwna" />
</adaptive-icon>
`);
  const tloConnect = `<defs><linearGradient id="tc" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#252a31"/><stop offset="1" stop-color="#0d0f12"/></linearGradient></defs>`;
  await zapiszPng(path.join(OBRAZY_CONNECT, 'icon.png'), `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
${tloConnect}<rect width="512" height="512" fill="url(#tc)"/>
<g transform="translate(256 256) scale(0.96) translate(-249 -248)">${ikonaConnect()}</g></svg>`, 512, 512);

  // ---------- grafika funkcji do Google Play: 1024×500, bez kanału alfa ----------
  const zapiszRGB = async (plik, svg, szer, wys) => {
    const px = await rysuj(svg, szer, wys, 'rgba');
    fs.mkdirSync(path.dirname(plik), { recursive: true });
    fs.writeFileSync(plik, pngRGB(szer, wys, px));
    console.log(path.relative(KATALOG, plik));
  };
  const czcionka = `font-family="'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"`;
  const znaczki = (lista, x, y, tlo, kolor) => lista.map((t, i) =>
    `<g transform="translate(${x + i * 0} ${y + i * 52})"><rect width="${Math.round(t.length * 11.4 + 42)}" height="40" rx="20" fill="${tlo}"/>` +
    `<text x="20" y="27" ${czcionka} font-size="21" font-weight="600" fill="${kolor}">${t}</text></g>`).join('');

  await zapiszRGB(path.join(OBRAZY_LIVEPL, 'featureGraphic.png'), `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<defs><linearGradient id="t" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f4f6f8"/><stop offset="1" stop-color="#dfe4e9"/></linearGradient></defs>
<rect width="1024" height="500" fill="url(#t)"/>
<g transform="translate(70 92) scale(0.86) translate(-56 -122)">${logo(JASNA)}</g>
<text x="420" y="205" ${czcionka} font-size="104" font-weight="700" fill="#1d2733">Live<tspan fill="#b0261f">PL</tspan></text>
<text x="424" y="258" ${czcionka} font-size="34" fill="#6b7886">Przeglądarka Live Production</text>
${znaczki(['Programy firmy w jednym miejscu', 'Tłumacz stron w telefonie', 'Dzień, noc i automatycznie'], 424, 300, '#ffffff', '#1d2733')}
</svg>`, 1024, 500);

  await zapiszRGB(path.join(OBRAZY_POCZTY, 'featureGraphic.png'), `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0b2740"/><stop offset="1" stop-color="#071c2d"/></linearGradient></defs>
<rect width="1024" height="500" fill="url(#g)"/>
<g transform="translate(205 250) scale(4.6) translate(-32 -32.5)">${logoPoczty}</g>
<text x="420" y="205" ${czcionka} font-size="88" font-weight="700" fill="#ffffff">Poczta <tspan fill="#35d0b5">LivePL</tspan></text>
<text x="424" y="258" ${czcionka} font-size="34" fill="#a9bccb">Firmowa poczta Live Production</text>
${znaczki(['Cała poczta na ekranie', 'Logowanie na 90 dni', 'Dzień i noc'], 424, 300, '#12324d', '#e6eef5')}
</svg>`, 1024, 500);

  await zapiszRGB(path.join(OBRAZY_CONNECT, 'featureGraphic.png'), `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<defs><linearGradient id="tg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1d2128"/><stop offset="1" stop-color="#0d0f12"/></linearGradient></defs>
<rect width="1024" height="500" fill="url(#tg)"/>
<g transform="translate(205 250) scale(0.86) translate(-249 -248)">${ikonaConnect()}</g>
<text x="420" y="200" ${czcionka} font-size="72" font-weight="700" fill="#ffffff">Live<tspan fill="#e3463a">PL</tspan> Connect</text>
<text x="424" y="258" ${czcionka} font-size="34" fill="#8b95a1">Zdalny pulpit Live Production</text>
${znaczki(['Połączenie po ID i haśle', 'Dotyk, mysz i klawiatura', 'Obraz prosto z komputera'], 424, 300, '#1f242b', '#e6e9ee')}
</svg>`, 1024, 500);

  if (PODGLAD) {
    await zapiszPng(path.join(PODGLAD, 'connect-porownanie.png'), `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="512" viewBox="0 0 1024 512">
<image href="data:image/png;base64,${fs.readFileSync(path.join(KATALOG, '..', 'LivePL-RMM', 'connect', 'server', 'web', 'ikona-512.png')).toString('base64')}" width="512" height="512"/>
<g transform="translate(512 0)">${tloConnect}<rect width="512" height="512" fill="url(#tc)"/>${ikonaConnect()}</g></svg>`, 1024, 512);
  }

  if (PODGLAD) {
    const R2 = 216;
    await zapiszPng(path.join(PODGLAD, 'poczta-kolo.png'), `<svg xmlns="http://www.w3.org/2000/svg" width="${R2}" height="${R2}" viewBox="0 0 108 108">
<defs><clipPath id="k"><circle cx="54" cy="54" r="36"/></clipPath></defs><rect width="108" height="108" fill="#777"/>
<g clip-path="url(#k)"><rect width="108" height="108" fill="#071c2d"/><g transform="translate(57.6 52.2) scale(0.8) translate(-32 -32.5)">${logoPoczty}</g></g></svg>`, R2, R2);
  }

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
