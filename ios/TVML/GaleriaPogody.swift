import SwiftUI
import UIKit

/// Do sprawdzania czytelności karty pogody ze Startu (CI, -galeria-pogody 1): karta narysowana w każdej scenie,
/// porze dnia i pogodzie, zapisana do Documents/galeria. Trzy rodzaje plików:
///   calosc/<wariant>.png – karta z tekstem, do obejrzenia (2×),
///   scena/<wariant>@<t>.png – samo tło z przyciemnieniem w kilku chwilach animacji (1×),
///   _tekst.png – sam tekst na przezroczystym tle (1×), czyli maska, pod którą skrypt liczy kontrast.
/// Na koniec gotowe.txt. Zwykłe uruchomienie aplikacji tego nie robi.
@MainActor
enum GaleriaPogody {
    static var wlaczona: Bool { UserDefaults.standard.bool(forKey: "galeria-pogody") }

    struct Wariant {
        let nazwa: String
        let scena: Motyw.Scena
        let stan: StanPogody
        let pora: PoraDnia
        let efekt: EfektPogody?
        let okolicznosciowy: Bool
    }

    /// Chwile animacji do tła (chmury, liście, fajerwerki są wtedy w innych miejscach).
    static let chwile: [Double] = [3, 17, 37]
    static let szerokosc: CGFloat = 370

    static func warianty() -> [Wariant] {
        var lista: [Wariant] = []
        // wersje z panelu (11 Listopada, zima, Święta, Sylwester): scena wersji, pogoda z ikony, pora z zegara
        let pory: [(String, PoraDnia)] = [("swit", .swit), ("dzien", .dzien), ("zmierzch", .zmierzch), ("noc", .noc)]
        let stany: [(String, StanPogody)] = [("slonce", .slonce), ("przejasnienia", .przejasnienia), ("chmury", .chmury),
                                            ("deszcz", .deszcz), ("burza", .burza), ("snieg", .snieg), ("mgla", .mgla), ("wiatr", .wiatr)]
        let wersje: [(String, Motyw.Scena)] = [("listopad", .listopad), ("zima", .zima), ("swieta", .swieta), ("sylwester", .sylwester)]
        for (nw, sc) in wersje {
            for (np, p) in pory {
                for (ns, st) in stany {
                    lista.append(Wariant(nazwa: "wersja-\(nw)-\(np)-\(ns)", scena: sc, stan: st, pora: p, efekt: nil, okolicznosciowy: true))
                }
            }
        }
        // zwykłe dni: efekt z serwera (API 1.7.2) na panoramie pory roku
        let efekty: [(String, String, Double)] = [
            ("clear-day", "clear-day", 0.5), ("clear-night", "clear-night", 0.5), ("clouds-rzadkie", "clouds", 0.3),
            ("clouds-geste", "clouds", 0.9), ("fog", "fog", 0.6), ("drizzle", "drizzle", 0.5), ("rain", "rain", 0.6),
            ("rain-heavy", "rain-heavy", 0.9), ("storm", "storm", 0.8), ("snow", "snow", 0.7), ("sleet", "sleet", 0.6), ("wind", "wind", 0.7)
        ]
        let pogodne = ["clear-day", "clear-night", "clouds", "fog", "wind"]
        let pory_roku: [(String, Motyw.Scena)] = [("wiosna", .wiosna), ("lato", .lato), ("jesien", .jesien), ("zima", .zima)]
        for (nr, sc) in pory_roku {
            for faza in ["day", "dusk", "night"] {
                for (ne, e, n) in efekty {
                    let j: Slownik = ["effect": e, "intensity": n, "windy": e == "wind", "phase": faza,
                                      "leaves": sc == .jesien && pogodne.contains(e)]
                    lista.append(Wariant(nazwa: "zwykly-\(nr)-\(faza)-\(ne)", scena: sc, stan: .chmury,
                                         pora: faza == "night" ? .noc : .dzien, efekt: EfektPogody(j), okolicznosciowy: false))
                }
            }
        }
        return lista
    }

    private static func karta(_ v: Wariant?, czas: Double, warstwa: KartaPogody.Warstwa) -> some View {
        // najdłuższe teksty, jakie zwykle bywają na karcie (temperatura z minusem, dwa imiona)
        KartaPogody(temperatura: "−12,4°C", opis: "Zachmurzenie duże",
                    prognoza: [(nazwa: "Wt", max: "−10,7°"), (nazwa: "Śr", max: "−11,4°"), (nazwa: "Cz", max: "−9,6°")],
                    dataIImieniny: "Poniedziałek, 23 listopada · Imieniny: Michała, Grzegorza",
                    stan: v?.stan ?? .chmury, pora: v?.pora ?? .dzien, scena: v?.scena ?? .jesien,
                    efekt: v?.efekt, okolicznosciowy: v?.okolicznosciowy ?? false, czas: czas, warstwa: warstwa)
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
