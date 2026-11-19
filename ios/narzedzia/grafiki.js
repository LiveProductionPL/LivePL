'use strict';
// Grafiki aplikacji LivePL na iPhone'a: ikona (jasna i ciemna), Wstęga do nagłówka i ikony programów.
// Wszystko powstaje z wektorowej Wstęgi (logo.js) i z plików w assets/programy.
// Uruchom z katalogu przegladarka-livepl:  npx electron ios/narzedzia/grafiki.js

const { app, BrowserWindow } = require('electron');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const logo = require('./logo.js');

const KATALOG = path.resolve(__dirname, '..', '..');
const ZASOBY = path.join(KATALOG, 'ios', 'LivePL', 'Assets.xcassets');

const JASNA = { trzon: '#861a15', wstega: '#b0261f', sygnal: '#b0261f' };
const CIEMNA = { trzon: '#bdbdbd', wstega: '#ffffff', sygnal: '#e3463a' };

// logo w siatce 512 zajmuje x 62–412, y 130–382; w ikonie stoi pośrodku, ok. 2/3 szerokości
function svgIkony(kolory, tlo) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="1024" height="1024">
<rect width="512" height="512" fill="${tlo}"/>
<g transform="translate(256 259) scale(0.965) translate(-237 -256)">${logo(kolory)}</g></svg>`;
}

function svgWstegi(kolory, szer, wys) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="56 122 362 264" width="${szer}" height="${wys}">${logo(kolory)}</svg>`;
}

// PNG bez kanału alfa: App Store odrzuca ikonę z przezroczystością
function pngRGB(szer, wys, rgba) {
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
  ihdr.writeUInt32BE(szer, 0); ihdr.writeUInt32BE(wys, 4); ihdr[8] = 8; ihdr[9] = 2; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), kawalek('IHDR', ihdr),
    kawalek('IDAT', zlib.deflateSync(surowe, { level: 9 })), kawalek('IEND', Buffer.alloc(0))]);
}

function zapiszJSON(plik, obiekt) {
  fs.mkdirSync(path.dirname(plik), { recursive: true });
  fs.writeFileSync(plik, JSON.stringify(obiekt, null, 2) + '\n');
}

app.whenReady().then(async () => {
  const okno = new BrowserWindow({ show: false, webPreferences: { offscreen: true } });
  await okno.loadURL('data:text/html,<body></body>');

  // rysuje SVG na płótnie; zwraca piksele RGBA albo gotowy PNG z przezroczystością
  const rysuj = (svg, szer, wys, jak) => okno.webContents.executeJavaScript(`(async () => {
    const i = new Image(); i.src = 'data:image/svg+xml;base64,' + ${JSON.stringify(Buffer.from(svg).toString('base64'))};
    await i.decode();
    const c = document.createElement('canvas'); c.width = ${szer}; c.height = ${wys};
    const x = c.getContext('2d'); x.imageSmoothingQuality = 'high'; x.drawImage(i, 0, 0, ${szer}, ${wys});
    return ${jak === 'rgba' ? `Array.from(x.getImageData(0, 0, ${szer}, ${wys}).data)` : `c.toDataURL('image/png')`};
  })()`);
  const zapiszPng = async (plik, svg, szer, wys) => {
    const url = await rysuj(svg, szer, wys, 'png');
    fs.mkdirSync(path.dirname(plik), { recursive: true });
    fs.writeFileSync(plik, Buffer.from(url.split(',')[1], 'base64'));
  };

  zapiszJSON(path.join(ZASOBY, 'Contents.json'), { info: { author: 'xcode', version: 1 } });

  // ikona aplikacji: jasna (domyślna) i ciemna (tryb ciemny iOS 18+)
  const ikona = path.join(ZASOBY, 'AppIcon.appiconset');
  for (const [plik, kolory, tlo] of [['ikona.png', JASNA, '#ffffff'], ['ikona-ciemna.png', CIEMNA, '#0d0f12']]) {
    const px = await rysuj(svgIkony(kolory, tlo), 1024, 1024, 'rgba');
    fs.mkdirSync(ikona, { recursive: true });
    fs.writeFileSync(path.join(ikona, plik), pngRGB(1024, 1024, px));
  }
  zapiszJSON(path.join(ikona, 'Contents.json'), {
    images: [
      { filename: 'ikona.png', idiom: 'universal', platform: 'ios', size: '1024x1024' },
      { appearances: [{ appearance: 'luminosity', value: 'dark' }], filename: 'ikona-ciemna.png', idiom: 'universal', platform: 'ios', size: '1024x1024' }
    ],
    info: { author: 'xcode', version: 1 }
  });

  // Wstęga do nagłówka i paska: 44×32 pt, jasna i ciemna
  const wstega = path.join(ZASOBY, 'Wstega.imageset');
  const obrazy = [];
  for (const [nazwa, kolory, wyglad] of [['wstega', JASNA, null], ['wstega-ciemna', CIEMNA, 'dark']]) {
    for (const s of [1, 2, 3]) {
      const plik = `${nazwa}@${s}x.png`;
      await zapiszPng(path.join(wstega, plik), svgWstegi(kolory, 44 * s, 32 * s), 44 * s, 32 * s);
      obrazy.push({ filename: plik, idiom: 'universal', scale: `${s}x`, ...(wyglad ? { appearances: [{ appearance: 'luminosity', value: wyglad }] } : {}) });
    }
  }
  zapiszJSON(path.join(wstega, 'Contents.json'), { images: obrazy, info: { author: 'xcode', version: 1 } });

  // ikona Poczty z assets/programy/poczta.svg
  const poczta = fs.readFileSync(path.join(KATALOG, 'assets', 'programy', 'poczta.svg'), 'utf8');
  const pocztaDir = path.join(ZASOBY, 'program-poczta.imageset');
  const pocztaObrazy = [];
  for (const s of [1, 2, 3]) {
    const plik = `poczta@${s}x.png`;
    await zapiszPng(path.join(pocztaDir, plik), poczta.replace('<svg ', `<svg width="${40 * s}" height="${40 * s}" `), 40 * s, 40 * s);
    pocztaObrazy.push({ filename: plik, idiom: 'universal', scale: `${s}x` });
  }
  zapiszJSON(path.join(pocztaDir, 'Contents.json'), { images: pocztaObrazy, info: { author: 'xcode', version: 1 } });

  // szerokie logotypy programów (dzień i noc) bez zmian, jako @2x
  for (const id of ['liveproduction', 'tvml']) {
    const dir = path.join(ZASOBY, `program-${id}.imageset`);
    fs.mkdirSync(dir, { recursive: true });
    fs.copyFileSync(path.join(KATALOG, 'assets', 'programy', `${id}-dzien.png`), path.join(dir, `${id}-dzien.png`));
    fs.copyFileSync(path.join(KATALOG, 'assets', 'programy', `${id}-noc.png`), path.join(dir, `${id}-noc.png`));
    zapiszJSON(path.join(dir, 'Contents.json'), {
      images: [
        { filename: `${id}-dzien.png`, idiom: 'universal', scale: '2x' },
        { appearances: [{ appearance: 'luminosity', value: 'dark' }], filename: `${id}-noc.png`, idiom: 'universal', scale: '2x' }
      ],
      info: { author: 'xcode', version: 1 }
    });
  }

  // kolor ekranu startowego (jak tło Reżyserki)
  const kolor = (r, g, b) => ({ 'color-space': 'srgb', components: { red: r, green: g, blue: b, alpha: '1.000' } });
  zapiszJSON(path.join(ZASOBY, 'TloStartu.colorset', 'Contents.json'), {
    colors: [
      { idiom: 'universal', color: kolor('0xE9', '0xEC', '0xEF') },
      { idiom: 'universal', appearances: [{ appearance: 'luminosity', value: 'dark' }], color: kolor('0x0D', '0x0F', '0x12') }
    ],
    info: { author: 'xcode', version: 1 }
  });
  zapiszJSON(path.join(ZASOBY, 'AccentColor.colorset', 'Contents.json'), {
    colors: [
      { idiom: 'universal', color: kolor('0xB0', '0x26', '0x1F') },
      { idiom: 'universal', appearances: [{ appearance: 'luminosity', value: 'dark' }], color: kolor('0xE3', '0x46', '0x3A') }
    ],
    info: { author: 'xcode', version: 1 }
  });

  console.log('Grafiki gotowe w', path.relative(KATALOG, ZASOBY));
  app.quit();
});
