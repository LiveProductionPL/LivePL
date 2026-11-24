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
                           prognoza: [(nazwa: "Wt", max: "−10,7°", ikona: "snowy"), (nazwa: "Śr", max: "−11,4°", ikona: "snowy"), (nazwa: "Cz", max: "−9,6°", ikona: "snowy")],
                                        pomiary: [("Wiatr", "22,0 km/h"), ("Wilgotność", "92,0%"), ("Ciśnienie", "1018,5 hPa")], ikonaTeraz: "cloudy",
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
        await zapiszDni(folder)
        try? "gotowe".write(to: folder.appendingPathComponent("gotowe.txt"), atomically: true, encoding: .utf8)
    }

    /// today.holiday, jakie serwer poda w te dni (Panel-TVML backend/lib/calendar.php, tvml_calendar_holiday_label:
    /// najwyżej 2 pozycje rozdzielone „ · ”), żeby arkusz miał te same dane co prawdziwa aplikacja w dany dzień.
    static let swietaKalendarza: [String: String] = [
        "2026-11-11": "Narodowe Święto Niepodległości 🇵🇱",
        "2026-11-29": "Andrzejki 🕯️ · Dzień Podchorążego",
        "2026-12-01": "Światowy Dzień AIDS 🎗️",
        "2026-12-03": "Międzynarodowy Dzień Osób z Niepełnosprawnościami",
        "2026-12-04": "Barbórka ⚒️",
        "2026-12-05": "Międzynarodowy Dzień Wolontariusza 🤝",
        "2026-12-06": "Mikołajki 🎅",
        "2026-12-10": "Dzień Praw Człowieka",
        "2026-12-13": "Dzień Pamięci Ofiar Stanu Wojennego 🕯️",
        "2026-12-21": "Początek astronomicznej zimy ❄️",
        "2026-12-24": "Wigilia Bożego Narodzenia 🎄",
        "2026-12-25": "Boże Narodzenie 🎄",
        "2026-12-26": "Drugi dzień świąt Bożego Narodzenia 🎄",
        "2026-12-27": "Narodowy Dzień Zwycięskiego Powstania Wielkopolskiego 🇵🇱",
        "2026-12-31": "Sylwester 🥂",
        "2027-01-01": "Nowy Rok 🎆"
    ]

    /// Karta na każdy dzień z harmonogramu motywów z panelu (config.theme i upcomingThemes, etapy z dateBar.days),
    /// liczona tak jak na Starcie: scena z WyborSceny.scena(na:), belka z PaletaBelki.linie(dzisiaj:).
    /// Karol, 24.11: „nie ma screenów wszystkich dat co dodałem”. Imiona bierzemy z linii dnia z serwera
    /// („… • Imieniny: Adama, Ewy”), bo config.today zna tylko dzisiejsze; pogoda jest przykładowa.
    /// Zapis: dni/<rrrr-mm-dd>-<dzien|wieczor>.png i dni/opis.tsv (nazwa, scena, wyróżnienie, imiona).
    static func zapiszDni(_ folder: URL) async {
        let fm = FileManager.default
        let dniFolder = folder.appendingPathComponent("dni", isDirectory: true)
        try? fm.createDirectory(at: dniFolder, withIntermediateDirectories: true)
        guard let konf = (try? await Siec.v1("config", ["features": "ai-themes", "lang": "pl"], swiezy: true)) as? Slownik else {
            try? "nie udało się pobrać /api/v1/config".write(to: dniFolder.appendingPathComponent("blad.txt"), atomically: true, encoding: .utf8)
            return
        }
        let wybor = WyborSceny(konf)
        let warszawa = TimeZone(identifier: "Europe/Warsaw") ?? .current
        var kal = Calendar(identifier: .gregorian)
        kal.timeZone = warszawa
        let klucz = DateFormatter()
        klucz.locale = Locale(identifier: "en_US_POSIX")
        klucz.timeZone = warszawa
        klucz.dateFormat = "yyyy-MM-dd"
        let etykieta = DateFormatter()
        etykieta.locale = Locale(identifier: "pl_PL")
        etykieta.timeZone = warszawa
        etykieta.dateFormat = "EEEE, d MMMM"

        // linie dni z serwera (dateBar.days[].line) ze wszystkich motywów i etapów, do imion na dany dzień
        var linieDni: [String: String] = [:]
        func zbierz(_ t: Slownik) {
            for d in t.slownik("dateBar").lista("days") where !d.s("date").isEmpty { linieDni[d.s("date")] = d.s("line") }
            for e in t.lista("stages") { zbierz(e) }
        }
        zbierz(konf.slownik("theme"))
        for t in konf.lista("upcomingThemes") { zbierz(t) }

        // każdy dzień, który obejmuje któreś okno harmonogramu
        var dni: [Date] = []
        for o in wybor.okna {
            var d = kal.startOfDay(for: o.od)
            while d <= o.doKonca {
                if !dni.contains(where: { kal.isDate($0, inSameDayAs: d) }) { dni.append(d) }
                guard let nast = kal.date(byAdding: .day, value: 1, to: d) else { break }
                d = nast
            }
        }
        dni.sort()

        var opis = "nazwa\tscena\twyroznienie\timiona\n"
        for (i, dzien) in dni.enumerated() {
            let k = klucz.string(from: dzien)
            var imiona: String?
            if let linia = linieDni[k], let r = linia.range(of: "Imieniny: ") {
                imiona = "Imieniny: " + linia[r.upperBound...]
            }
            for (faza, godzina) in [("dzien", 12), ("wieczor", 20)] {
                guard let chwila = kal.date(bySettingHour: godzina, minute: 0, second: 0, of: dzien) else { continue }
                let jasno = faza == "dzien"
                let scena = wybor.scena(na: chwila)
                let wMotywie = wybor.okna.contains { $0.od <= chwila && chwila <= $0.doKonca }
                let linie = PaletaBelki.linie(scena, swieto: swietaKalendarza[k], imieniny: imiona, dzisiaj: chwila)
                var data = etykieta.string(from: chwila)
                data = data.prefix(1).uppercased(with: Locale(identifier: "pl_PL")) + data.dropFirst()
                let warunki = WarunkiSceny.z(stan: jasno ? "sunny" : "clear-night", efekt: nil, dzien: jasno,
                                             ustawienia: nil, karta: true, bezRuchu: false)
                let karta = KartaPogody(temperatura: "−3,0°C", opis: "Zachmurzenie duże",
                                        prognoza: [(nazwa: "Pn", max: "−1,0°", ikona: "snowy"), (nazwa: "Wt", max: "0,5°", ikona: "snowy"), (nazwa: "Śr", max: "−2,4°", ikona: "snowy")],
                                        pomiary: [("Wiatr", "22,0 km/h"), ("Wilgotność", "92,0%"), ("Ciśnienie", "1018,5 hPa")], ikonaTeraz: "cloudy",
                                        data: data, wyroznienie: linie.wyroznienie, imieniny: linie.imieniny,
                                        stan: .chmury, pora: jasno ? .dzien : .noc,
                                        scena: Motyw.zwykly.scena(miesiac: kal.component(.month, from: chwila)),
                                        efekt: nil, okolicznosciowy: wMotywie,
                                        scenaKarty: scena, warunki: warunki,
                                        czas: chwile[1], warstwa: .calosc)
                    .frame(width: szerokosc)
                    .environment(\.colorScheme, .light)
                let nazwa = "\(k)-\(faza)"
                zapisz(karta, do: dniFolder.appendingPathComponent("\(nazwa).png"), skala: 2)
                let id = scena.svg != nil ? "SVG z panelu (w galerii się nie rysuje)" : (scena.id.isEmpty ? "(pusta → autumn-forest)" : scena.id)
                opis += "\(nazwa)\t\(id)\t\(linie.wyroznienie ?? "")\t\(linie.imieniny ?? "")\n"
            }
            if i % 4 == 0 { await Task.yield() }
        }
        try? opis.write(to: dniFolder.appendingPathComponent("opis.tsv"), atomically: true, encoding: .utf8)
    }
}
