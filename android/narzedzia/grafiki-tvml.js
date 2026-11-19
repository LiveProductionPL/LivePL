'use strict';
// Grafiki TVML na Androida z ikon TVML na iPhonie (gałąź ios: ios/TVML/Assets.xcassets, appicon.png,
// ikonazima.png, ikonaswieta.png, 1024×1024), żeby obie aplikacje wyglądały tak samo:
// - trzy ikony adaptacyjne (zwykła, zimowa, świąteczna) do activity-alias w AndroidManifest.xml,
// - ikona jednobarwna do ikon motywowych Androida 13+ (z logo TVML),
// - ikona 512 px i grafika funkcji 1024×500 do Google Play (granat TVML, logo i nocna panorama Leszna z Ratuszem,
//   jak scena pogody w aplikacji).
//
// Uruchom z katalogu przegladarka-livepl:  npx electron android/narzedzia/grafiki-tvml.js <folder z ikonami iOS>

const { app, BrowserWindow } = require('electron');
const fs = require('fs');
const path = require('path');

const KATALOG = path.resolve(__dirname, '..', '..');
const RES = path.join(KATALOG, 'android', 'tvml', 'src', 'main', 'res');
const OBRAZY = path.join(KATALOG, 'android', 'sklep', 'pl.tvml', 'pl-PL', 'images');
const IKONY_IOS = process.argv.slice(2).find((a) => !a.startsWith('-') && !a.endsWith('grafiki-tvml.js'));
if (!IKONY_IOS) {
  console.error('Podaj folder z appicon.png, ikonazima.png i ikonaswieta.png z ios/TVML/Assets.xcassets.');
  process.exit(1);
}


/** PNG bez kanału alfa (Google Play wymaga tego dla grafiki funkcji); to samo co w grafiki.js. */
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

/** Nocna panorama Leszna z Ratuszem w siatce 358 × 130 (ta sama co ScenaPogody w Scena.kt), ze światłami w oknach. */
function panorama() {
  const prost = (x, y, w, h, kolor) => `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${kolor}"/>`;
  let domy = '';
  for (const [x, y, w] of [[0, 82, 46], [50, 76, 24], [106, 80, 26], [296, 84, 26]]) domy += prost(x, y, w, 108 - y, '#0a1226');
  for (const [x, y, w] of [[78, 86, 24], [240, 78, 50], [326, 74, 32]]) {
    domy += prost(x, y, w, 108 - y, '#0a1226') + `<path d="M${x} ${y}L${x + w / 2} ${y - 12}L${x + w} ${y}z" fill="#0a1226"/>`;
  }
  const ratusz = prost(138, 70, 84, 38, '#101a33') + '<path d="M142 70L146 62L214 62L218 70z" fill="#101a33"/>' +
    prost(178, 34, 16, 28, '#101a33') + '<path d="M174 36Q176 26 186 20Q196 26 198 36z" fill="#101a33"/>' + prost(185, 6, 2, 14, '#101a33') +
    '<circle cx="186" cy="45" r="4.5" fill="#fde68a"/>';
  const okna = [[146, 78], [158, 78], [170, 78], [196, 78], [208, 78], [220, 78], [146, 92], [158, 92], [208, 92], [220, 92],
    [56, 84], [64, 84], [112, 88], [122, 88], [246, 86], [302, 90], [334, 84]].map(([x, y]) => prost(x, y, 5, 7, '#fcd34d')).join('');
  const drzewa = [[22, 104, 12, '#c2410c'], [44, 108, 9, '#d97706'], [118, 106, 11, '#b45309'], [252, 105, 12, '#ea580c'], [286, 108, 9, '#c2410c'], [330, 104, 12, '#d97706']]
    .map(([x, y, r, k]) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${k}"/>`).join('');
  return domy + ratusz + okna + drzewa;
}

const base64 = (plik) => fs.readFileSync(plik).toString('base64');
const IKONY = { zwykla: 'appicon.png', zima: 'ikonazima.png', swieta: 'ikonaswieta.png' };

// Ikona adaptacyjna ma 108 dp, a widać środek 72 dp (w okrągłej masce koło 66 dp). Najdalszy rysunek ikony iOS
// (róg czapki Mikołaja) jest 387 px od środka kwadratu 1024, więc kwadrat zajmuje 87 dp i wszystko mieści się w kole.
// Brzegi poza kwadratem wypełnia ta sama ikona w pełnym rozmiarze (pod spodem), tak że gradient tła się nie urywa;
// pasek u dołu ikon zimowej i świątecznej wypada poza widoczną częścią, jak na iPhonie pod zaokrągleniem.
const KWADRAT = 87 / 108;

app.whenReady().then(async () => {
  const okno = new BrowserWindow({ show: false, webPreferences: { offscreen: true } });
  await okno.loadURL('data:text/html,<body></body>');
  const zapisz = (plik, tresc) => {
    fs.mkdirSync(path.dirname(plik), { recursive: true });
    fs.writeFileSync(plik, tresc);
    console.log(path.relative(KATALOG, plik));
  };
  // rysunek w <canvas> przeglądarki: kod JS dostaje x (kontekst 2D), c (canvas) i obrazy img[0..]
  const rysuj = async (plik, szer, wys, obrazy, kod) => {
    const url = await okno.webContents.executeJavaScript(`(async () => {
      const img = await Promise.all(${JSON.stringify(obrazy)}.map(async (s) => { const i = new Image(); i.src = s; await i.decode(); return i; }));
      const c = document.createElement('canvas'); c.width = ${szer}; c.height = ${wys};
      const x = c.getContext('2d'); x.imageSmoothingQuality = 'high';
      ${kod}
      return c.toDataURL('image/png');
    })()`);
    zapisz(plik, Buffer.from(url.split(',')[1], 'base64'));
  };

  // jedna gęstość (xxxhdpi, 432 px): launcher sam zmniejsza warstwy
  const PX = 432;
  const MIPMAP = path.join(RES, 'mipmap-xxxhdpi');
  const przezroczysty = path.join(MIPMAP, 'ikona_pusta.png');
  await rysuj(przezroczysty, PX, PX, [], '');
  for (const [nazwa, plik] of Object.entries(IKONY)) {
    const zrodlo = 'data:image/png;base64,' + base64(path.join(IKONY_IOS, plik));
    const bok = Math.round(PX * KWADRAT), od = Math.round((PX - bok) / 2);
    await rysuj(path.join(MIPMAP, `ikona_${nazwa}.png`), PX, PX, [zrodlo],
      `x.drawImage(img[0], 0, 0, ${PX}, ${PX}); x.drawImage(img[0], ${od}, ${od}, ${bok}, ${bok});`);
    const xml = nazwa === 'zwykla' ? 'ic_launcher' : `ic_launcher_${nazwa}`;
    zapisz(path.join(RES, 'mipmap-anydpi', `${xml}.xml`), `<?xml version="1.0" encoding="utf-8"?>
<!-- ikona adaptacyjna TVML (${nazwa}) z ikony TVML na iPhonie; tworzy ją android/narzedzia/grafiki-tvml.js -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ikona_${nazwa}" />
    <foreground android:drawable="@mipmap/ikona_pusta" />
    <monochrome android:drawable="@mipmap/ikona_jednobarwna" />
</adaptive-icon>
`);
  }

  // Ikona motywowa: system barwi ją jednym kolorem według krycia, więc jasne litery TV i ML są pełne,
  // a szara i brązowa tablica półprzezroczyste (inaczej logo byłoby jedną plamą). Logo jak na ikonie iOS:
  // ok. 63% szerokości kwadratu.
  const logo = 'data:image/png;base64,' + base64(path.join(RES, 'drawable-nodpi', 'logo_noc.png'));
  const szerLogo = Math.round(PX * KWADRAT * 0.63), wysLogo = Math.round(szerLogo * 126 / 270);
  await rysuj(path.join(MIPMAP, 'ikona_jednobarwna.png'), PX, PX, [logo], `
    x.drawImage(img[0], ${(PX - szerLogo) / 2}, ${(PX - wysLogo) / 2}, ${szerLogo}, ${wysLogo});
    const d = x.getImageData(0, 0, c.width, c.height);
    for (let p = 0; p < d.data.length; p += 4) {
      const jasnosc = (0.299 * d.data[p] + 0.587 * d.data[p + 1] + 0.114 * d.data[p + 2]) / 255;
      d.data[p + 3] = Math.round(d.data[p + 3] * Math.max(0.35, Math.pow(jasnosc, 1.3)));
      d.data[p] = d.data[p + 1] = d.data[p + 2] = 255;
    }
    x.putImageData(d, 0, 0);`);

  // grafika funkcji 1024×500 (bez kanału alfa): granat TVML, logo, hasło i nocna panorama z Ratuszem
  const gwiazdy = Array.from({ length: 40 }, (_, i) => `<circle cx="${((i * 97) % 1000) / 1000 * 1024}" cy="${((i * 61) % 1000) / 1000 * 230}" r="${1 + (i % 3) * 0.5}" fill="#ffffff" opacity="${0.35 + (i % 4) * 0.15}"/>`).join('');
  const czcionka = `font-family="'Segoe UI', Roboto, Arial, sans-serif"`;
  const funkcja = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<defs><linearGradient id="n" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#04091e"/><stop offset="0.6" stop-color="#0a1f44"/><stop offset="1" stop-color="#1c2a52"/></linearGradient></defs>
<rect width="1024" height="500" fill="url(#n)"/>${gwiazdy}
<path d="M900 52a26 26 0 1 0 22 40a22 22 0 1 1 -22 -40z" fill="#f4f1de"/>
<g transform="translate(424 282) scale(${600 / 358})">${panorama()}</g>
<rect x="0" y="463" width="1024" height="37" fill="#0b1020"/>
<image href="data:image/png;base64,${base64(path.join(RES, 'drawable-nodpi', 'logo_noc.png'))}" x="56" y="60" width="300" height="140"/>
<text x="60" y="262" ${czcionka} font-size="40" font-weight="800" fill="#ffffff">Wiadomości z Leszna</text>
<text x="60" y="310" ${czcionka} font-size="40" font-weight="800" fill="#ffffff">i powiatu</text>
<text x="60" y="366" ${czcionka} font-size="24" font-weight="600" fill="#e29818">Wideo · Pogoda · Alert TVML</text>
</svg>`;
  const px = await okno.webContents.executeJavaScript(`(async () => {
    const i = new Image(); i.src = 'data:image/svg+xml;base64,' + ${JSON.stringify(Buffer.from(funkcja).toString('base64'))};
    await i.decode();
    const c = document.createElement('canvas'); c.width = 1024; c.height = 500;
    const x = c.getContext('2d'); x.drawImage(i, 0, 0, 1024, 500);
    return Array.from(x.getImageData(0, 0, 1024, 500).data);
  })()`);
  zapisz(path.join(OBRAZY, 'featureGraphic.png'), pngRGB(1024, 500, px));

  // ikona 512×512 do Google Play: zwykła ikona TVML z iPhone'a (pełny kwadrat, Sklep sam zaokrągla rogi)
  await rysuj(path.join(OBRAZY, 'icon.png'), 512, 512, ['data:image/png;base64,' + base64(path.join(IKONY_IOS, 'appicon.png'))],
    'x.drawImage(img[0], 0, 0, 512, 512);');
  app.quit();
});
