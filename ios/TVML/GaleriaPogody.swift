import SwiftUI
import UIKit

/// Do sprawdzania czytelności karty pogody ze Startu (CI, -galeria-pogody YES): karta narysowana w każdej scenie
/// ze strony (ScenyStrony.swift), w dzień i w nocy, przy kilku pogodach; zapis do Documents/galeria:
///   calosc/<wariant>.png – karta z tekstem i belką, do obejrzenia (2×),
///   scena/<wariant>@<t>.png – samo tło z przyciemnieniem w kilku chwilach animacji (1×),
///   _tekst.png – sam tekst na przezroczystym tle (1×), czyli maska, pod którą skrypt liczy kontrast.
/// Na koniec gotowe.txt. Zwykłe uruchomienie aplikacji tego nie robi. Sceny SVG z panelu (WKWebView) się tu nie rysują.
@MainActor
enum GaleriaPogody {
    static var wlaczona: Bool { UserDefaults.standard.bool(forKey: "galeria-pogody") }

    struct Wariant {
        let nazwa: String
        let scena: ScenaStrony
        let efekt: EfektPogody?
        let warunki: WarunkiSceny
        /// panorama Leszna aplikacji („leszno-panorama”) w danej porze roku; nil = scena ze strony
        var panoramaRoku: Motyw.Scena? = nil
    }

    /// Chwile animacji do tła (chmury, fale, fajerwerki, płomienie są wtedy w innych miejscach).
    static let chwile: [Double] = [3, 17, 37]
    static let szerokosc: CGFloat = 370

    static func warianty() -> [Wariant] {
        var lista: [Wariant] = []
        for s in ScenaStrony.allCases {
            for faza in ["day", "night"] {
                let dzien = faza == "day"
                if s.specjalna {
                    // sceny specjalne: bez nakładki pogody, ze stanu zależy tylko niebo (np. Ratusz przy burzy i deszczu)
                    let stany = dzien ? ["sunny", "cloudy", "stormy"] : ["clear-night", "cloudy-night", "stormy"]
                    for st in stany {
                        let w = WarunkiSceny.z(stan: st, efekt: nil, dzien: dzien, ustawienia: nil, karta: false, bezRuchu: false)
                        lista.append(Wariant(nazwa: "\(s.rawValue)-\(faza)-\(st)", scena: s, efekt: nil, warunki: w))
                    }
                } else {
                    let efekty: [(String, String, Double, String)] = [
                        (dzien ? "clear-day" : "clear-night", dzien ? "clear-day" : "clear-night", 0.5, dzien ? "sunny" : "clear-night"),
                        ("clouds", "clouds", 0.9, dzien ? "cloudy" : "cloudy-night"),
                        ("rain", "rain", 0.7, "rainy-moderate"),
                        ("snow", "snow", 0.7, "snowy"),
                        ("fog", "fog", 0.6, dzien ? "foggy" : "night-foggy")
                    ]
                    for (nazwa, e, n, st) in efekty {
                        let j: Slownik = ["effect": e, "intensity": n, "windy": false, "phase": faza,
                                          "leaves": s == .autumnForest || s == .autumnLake]
                        let ef = EfektPogody(j)
                        let w = WarunkiSceny.z(stan: st, efekt: ef, dzien: dzien, ustawienia: nil, karta: false, bezRuchu: false)
                        lista.append(Wariant(nazwa: "\(s.rawValue)-\(faza)-\(nazwa)", scena: s, efekt: ef, warunki: w))
                    }
                }
            }
        }
        // panorama Leszna aplikacji (domyślna scena w zwykłe dni, gdy serwer poda leszno-panorama)
        let poryRoku: [(String, Motyw.Scena)] = [("jesien", .jesien), ("lato", .lato), ("zima", .zima)]
        for (nr, pr) in poryRoku {
            for faza in ["day", "night"] {
                let dzien = faza == "day"
                let e = dzien ? "clear-day" : "clear-night"
                let j: Slownik = ["effect": e, "intensity": 0.5, "windy": false, "phase": faza, "leaves": pr == .jesien]
                let ef = EfektPogody(j)
                let w = WarunkiSceny.z(stan: dzien ? "sunny" : "clear-night", efekt: ef, dzien: dzien, ustawienia: nil, karta: false, bezRuchu: false)
                lista.append(Wariant(nazwa: "leszno-panorama-\(nr)-\(faza)-\(e)", scena: .autumnForest, efekt: ef, warunki: w, panoramaRoku: pr))
            }
        }
        return lista
    }

    private static func karta(_ v: Wariant?, czas: Double, warstwa: KartaPogody.Warstwa) -> some View {
        let scena = WyborSceny.Scena(id: v?.panoramaRoku != nil ? "leszno-panorama" : (v?.scena.rawValue ?? "autumn-forest"))
        // najdłuższe teksty, jakie zwykle bywają na karcie (temperatura z minusem, święto, trzy imiona)
        return KartaPogody(temperatura: "−12,4°C", opis: "Zachmurzenie duże",
                           prognoza: [(nazwa: "Wt", max: "−10,7°"), (nazwa: "Śr", max: "−11,4°"), (nazwa: "Cz", max: "−9,6°")],
                           data: "Poniedziałek, 11 listopada",
                           wyroznienie: PaletaBelki.linie(scena, swieto: "Narodowe Święto Niepodległości", imieniny: nil).wyroznienie,
                           imieniny: PaletaBelki.linie(scena, swieto: nil, imieniny: "Imieniny: Bartłomieja, Marcina, Prota").imieniny,
                           stan: .chmury, pora: (v?.warunki.noc ?? false) ? .noc : .dzien, scena: v?.panoramaRoku ?? .jesien,
                           efekt: v?.efekt, okolicznosciowy: false,
                           scenaKarty: scena, warunki: v?.warunki,
                           czas: czas, warstwa: warstwa)
            .frame(width: szerokosc)
            .environment(\.colorScheme, .light)
    }

    private static func zapisz(_ widok: some View, do plik: URL, skala: CGFloat) {
        let r = ImageRenderer(content: widok)
        r.scale = skala
        if let png = r.uiImage?.pngData() { try? png.write(to: plik) }
    }

    static func zapisz() async {
        let fm = FileManager.default
        let folder = fm.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("galeria", isDirectory: true)
        try? fm.removeItem(at: folder)
        for podfolder in ["calosc", "scena"] {
            try? fm.createDirectory(at: folder.appendingPathComponent(podfolder, isDirectory: true), withIntermediateDirectories: true)
        }
        zapisz(karta(nil, czas: 0, warstwa: .tekst), do: folder.appendingPathComponent("_tekst.png"), skala: 1)
        for (i, v) in warianty().enumerated() {
            zapisz(karta(v, czas: chwile[1], warstwa: .calosc), do: folder.appendingPathComponent("calosc/\(v.nazwa).png"), skala: 2)
            for t in chwile {
                zapisz(karta(v, czas: t, warstwa: .scena), do: folder.appendingPathComponent("scena/\(v.nazwa)@\(Int(t)).png"), skala: 1)
            }
            if i % 6 == 0 { await Task.yield() }
        }
        try? "gotowe".write(to: folder.appendingPathComponent("gotowe.txt"), atomically: true, encoding: .utf8)
    }
}
