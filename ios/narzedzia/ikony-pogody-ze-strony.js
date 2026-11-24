// Generuje ikony pogody ze strony (Panel-TVML public/assets/js/weather-icons.js) do katalogu zasobów iOS:
// pogoda-<stan>-<dzien|noc>.imageset/…svg (wektor, bez filtra cienia, którego Xcode nie czyta)
// oraz IkonyPogodyStrony.swift z mapą aliasów (klucze API i stany scen → stan zestawu, wymuszona noc).
// node ios/narzedzia/ikony-pogody-ze-strony.js <weather-icons.js> <Assets.xcassets> <plik .swift>
const fs = require('fs');
const path = require('path');
const [zrodlo, katalog, plikSwift] = process.argv.slice(2);
const WI = require(path.resolve(zrodlo));

const folder = path.join(katalog, 'IkonyPogody');
fs.mkdirSync(folder, { recursive: true });
fs.writeFileSync(path.join(folder, 'Contents.json'), JSON.stringify({ info: { author: 'xcode', version: 1 }, properties: { 'provides-namespace': false } }, null, 2) + '\n');

let n = 0;
for (const stan of WI.STATES) {
  for (const noc of [false, true]) {
    let svg = WI.svg(stan, { night: noc, size: 64, animate: false, decorative: true });
    // bez filtra cienia (feDropShadow) i bez klas animacji
    svg = svg.replace(/<filter[\s\S]*?<\/filter>/g, '').replace(/ filter="url\([^)]*\)"/g, '').replace(/ class="[^"]*"/g, '');
    if (!/^<svg[^>]*width=/.test(svg)) svg = svg.replace('<svg', '<svg width="64" height="64"');
    const nazwa = `pogoda-${stan}-${noc ? 'noc' : 'dzien'}`;
    const set = path.join(folder, nazwa + '.imageset');
    fs.mkdirSync(set, { recursive: true });
    fs.writeFileSync(path.join(set, nazwa + '.svg'), svg);
    fs.writeFileSync(path.join(set, 'Contents.json'), JSON.stringify({
      images: [{ filename: nazwa + '.svg', idiom: 'universal' }],
      info: { author: 'xcode', version: 1 },
      properties: { 'preserves-vector-representation': true }
    }, null, 2) + '\n');
    n++;
  }
}

const wpisy = Object.entries(WI.ALIASES)
  .map(([k, [stan, noc]]) => `        "${k}": ("${stan}", ${noc === null ? 'nil' : noc})`)
  .join(',\n');
const swift = `// Wygenerowane z Panel-TVML public/assets/js/weather-icons.js (ikony pogody strony tvml.pl, 1:1).
// Nie edytować ręcznie: ios/narzedzia/ikony-pogody-ze-strony.js.
import Foundation

enum IkonyPogodyStrony {
    /// alias (klucz API, stan sceny, ikonka stacji) → (stan zestawu, wymuszona noc albo nil)
    static let aliasy: [String: (String, Bool?)] = [
${wpisy}
    ]
    static let stany: Set<String> = [${WI.STATES.map((s) => `"${s}"`).join(', ')}]

    /// Nazwa grafiki w Assets (IkonyPogody): pogoda-<stan>-<dzien|noc>, jak WeatherIcons.resolve na stronie (bez opisu i WMO).
    static func nazwa(_ klucz: String, noc: Bool) -> String {
        let k = klucz.trimmingCharacters(in: .whitespaces).lowercased()
        let domyslny: (String, Bool?) = stany.contains(k) ? (k, nil) : ("clear", nil)
        let a = aliasy[k] ?? domyslny
        let wNocy = a.1 ?? noc
        return "pogoda-\\(a.0)-\\(wNocy ? "noc" : "dzien")"
    }
}
`;
fs.writeFileSync(plikSwift, swift);
console.log('ikon:', n, '| aliasów:', Object.keys(WI.ALIASES).length);
