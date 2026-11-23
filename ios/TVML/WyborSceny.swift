import SwiftUI
import WebKit

/// Tło karty z motywu z panelu (scena SVG z /api/v1/theme-scene/<id>.svg): obraz wypełnia kartę (jak object-fit: cover),
/// animacje CSS z wnętrza SVG działają; nocą bez nocnej wersji przyciemniony jak na stronie.
struct TloSVG: UIViewRepresentable {
    let adres: URL
    var przyciemnij = false

    func makeUIView(context: Context) -> WKWebView {
        let konf = WKWebViewConfiguration()
        konf.suppressesIncrementalRendering = true
        let w = WKWebView(frame: .zero, configuration: konf)
        w.isOpaque = false
        w.backgroundColor = .clear
        w.scrollView.backgroundColor = .clear
        w.scrollView.isScrollEnabled = false
        w.isUserInteractionEnabled = false
        w.accessibilityElementsHidden = true
        laduj(w)
        return w
    }

    func updateUIView(_ w: WKWebView, context: Context) {
        if w.accessibilityIdentifier != klucz { laduj(w) }
    }

    private var klucz: String { adres.absoluteString + (przyciemnij ? "#noc" : "") }

    private func laduj(_ w: WKWebView) {
        w.accessibilityIdentifier = klucz
        let filtr = przyciemnij ? "filter:brightness(.42) saturate(.75) hue-rotate(-10deg);" : ""
        let zrodlo = adres.absoluteString.replacingOccurrences(of: "\"", with: "%22")
        let html = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">\
        <style>html,body{margin:0;height:100%;background:transparent;overflow:hidden}\
        img{width:100%;height:100%;object-fit:cover;display:block;\(filtr)}</style></head>\
        <body><img src="\(zrodlo)" alt=""></body></html>
        """
        w.loadHTMLString(html, baseURL: URL(string: "https://tvml.pl/"))
    }
}

/// Scena karty pogody tak, jak wybiera ją strona (API 1.8.x, tvml-app.php):
/// - teraz: config.theme.weatherScene, liczona na serwerze z harmonogramu motywów (etapy, priorytety, czas warszawski);
///   puste = tło z panelu albo żałoba, wtedy jak na stronie autumn-forest;
/// - później: config.upcomingThemes[] z from/until i własną sceną; aplikacja przełącza się sama na granicy okna
///   i od razu pobiera config na nowo (etapy w środku motywu, np. Mikołajki → Święta, zna tylko serwer);
/// - motyw z panelu ze sceną SVG (weatherSceneType "svg", przy features=ai-themes): adres obrazu dzienny i nocny.
struct WyborSceny: Equatable {
    /// Belka z datą z serwera (API 1.8.3, theme.dateBar / stages[].dateBar): medal, kolory motywu i teksty na każdy dzień.
    struct PasekDaty: Equatable {
        struct Dzien: Equatable {
            var przedrostek = ""
            var jutro = ""
            var bezImienin = false
        }
        var akcent: String?          // palette.bar
        var tloDzien: String?        // palette.bgD (do scen SVG, których podłoża aplikacja nie zna)
        var tloNoc: String?          // palette.bgN
        var dataDzien: String?       // palette.dColD
        var dataNoc: String?         // palette.dColN
        var drugaDzien: String?      // palette.sColD
        var drugaNoc: String?        // palette.sColN
        var medal: String?           // palette.medBg
        var obwodka: String?         // palette.medRim
        var ikona: String?           // medal.icon: snowflake, santa-hat, star, tree, candle, year
        var napisMedalu: String?     // medal.text (Nowy Rok: „27”)
        var ogolny = Dzien()
        var dni: [String: Dzien] = [:]   // „2026-11-29” → teksty tego dnia

        init?(_ j: Slownik?) {
            guard let j, !j.isEmpty else { return nil }
            let p = j.slownik("palette")
            func k(_ n: String) -> String? { p.s(n).isEmpty ? nil : p.s(n) }
            akcent = k("bar"); tloDzien = k("bgD"); tloNoc = k("bgN")
            dataDzien = k("dColD"); dataNoc = k("dColN"); drugaDzien = k("sColD"); drugaNoc = k("sColN")
            medal = k("medBg"); obwodka = k("medRim")
            let m = j.slownik("medal")
            ikona = m.s("icon").isEmpty ? nil : m.s("icon")
            napisMedalu = m.s("text").isEmpty ? nil : m.s("text")
            func dzien(_ d: Slownik) -> Dzien { Dzien(przedrostek: d.s("prefix"), jutro: d.s("tomorrow"), bezImienin: d.b("hideNameday")) }
            ogolny = dzien(j)
            for d in j.lista("days") where !d.s("date").isEmpty { dni[d.s("date")] = dzien(d) }
        }

        /// Teksty na dany dzień (czas warszawski), a bez wpisu ogólne.
        func dzien(_ chwila: Date) -> Dzien {
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.timeZone = TimeZone(identifier: "Europe/Warsaw")
            f.dateFormat = "yyyy-MM-dd"
            return dni[f.string(from: chwila)] ?? ogolny
        }
    }

    struct Scena: Equatable {
        var id = ""
        var svg: URL?
        var svgNoc: URL?
        /// Belka z serwera; nil przy API 1.8.3 = zwykła belka bez motywu.
        var pasek: PasekDaty?
        /// API 1.8.3: wszystko, co dotyczy belki, przyszło z serwera (bez tabel w aplikacji).
        var zSerwera = false
        /// Scena narysowana w aplikacji; nieznany albo pusty id → autumn-forest (strona robi tak samo).
        var strony: ScenaStrony { ScenaStrony(rawValue: id) ?? .autumnForest }
    }

    struct Okno: Equatable {
        let od: Date
        let doKonca: Date
        let scena: Scena
    }

    var biezaca = Scena()
    /// Koniec bieżącego motywu (theme.until), żeby po nim pobrać tło na nowo.
    var biezacaDo: Date?
    var okna: [Okno] = []

    init() {}

    init(_ konf: Slownik) {
        let iso = ISO8601DateFormatter()
        // API 1.8.3: backgroundScene to scena, którą rysuje strona (z panelu albo z motywu i etapu), typ i adresy SVG
        // zawsze; wcześniej weatherScene (puste przy żałobie i pustym tle z panelu) i SVG tylko z features=ai-themes
        let nowe = konf.slownik("theme")["backgroundScene"] != nil
        func scena(_ t: Slownik) -> Scena {
            let svg = t.s("weatherSceneType") == "svg"
            return Scena(id: nowe ? t.s("backgroundScene") : t.s("weatherScene"),
                         svg: svg ? URL(string: t.s("weatherSceneSvgUrl")) : nil,
                         svgNoc: svg ? URL(string: t.s("weatherSceneSvgNightUrl")) : nil,
                         pasek: PasekDaty(t["dateBar"] as? Slownik), zSerwera: nowe)
        }
        /// Okna motywu: jego etapy z datami (xmas: Mikołajki → Święta → Wigilia → Święta), a bez nich cały motyw.
        func oknaMotywu(_ t: Slownik) -> [Okno] {
            let etapy: [Okno] = t.lista("stages").compactMap { e in
                guard let od = iso.date(from: e.s("from")), let d = iso.date(from: e.s("until")) else { return nil }
                return Okno(od: od, doKonca: d, scena: scena(e))
            }
            if !etapy.isEmpty { return etapy }
            guard let od = iso.date(from: t.s("from")), let d = iso.date(from: t.s("until")) else { return [] }
            let s = scena(t)
            guard !s.id.isEmpty || s.svg != nil else { return [] }
            return [Okno(od: od, doKonca: d, scena: s)]
        }
        let th = konf.slownik("theme")
        biezaca = scena(th)
        biezacaDo = iso.date(from: th.s("until"))
        okna = th.lista("stages").isEmpty ? [] : oknaMotywu(th)
        okna += konf.lista("upcomingThemes").flatMap { oknaMotywu($0) }
    }

    /// Scena w danej chwili: okno z harmonogramu, które trwa, a bez niego bieżąca z serwera.
    func scena(na chwila: Date) -> Scena {
        if let o = okna.first(where: { $0.od <= chwila && chwila <= $0.doKonca }) { return o.scena }
        return biezaca
    }

    /// Najbliższa chwila, w której scena może się zmienić: początek albo koniec okna, koniec bieżącego motywu,
    /// a najpóźniej północ w Warszawie (etapy motywów są dzienne).
    func nastepnaZmiana(po chwila: Date) -> Date? {
        var granice = okna.flatMap { [$0.od, $0.doKonca.addingTimeInterval(1)] }
        if let d = biezacaDo { granice.append(d.addingTimeInterval(1)) }
        var kal = Calendar(identifier: .gregorian)
        kal.timeZone = TimeZone(identifier: "Europe/Warsaw") ?? .current
        if let polnoc = kal.nextDate(after: chwila, matching: DateComponents(hour: 0, minute: 0, second: 5), matchingPolicy: .nextTime) {
            granice.append(polnoc)
        }
        return granice.filter { $0 > chwila }.min()
    }
}

extension WarunkiSceny {
    /// Warunki jak na stronie (weather-effects.js resolveState i isStormy/isRainy, pogoda-efekty.js _peNoStars)
    /// z danych pogody aplikacji i ustawień sceny z panelu (config.weatherAnimation[stan]).
    static func z(stan: String, efekt: EfektPogody?, dzien: Bool, ustawienia: Slownik?, karta: Bool, bezRuchu: Bool) -> WarunkiSceny {
        let s = stan.lowercased()
        let noc = efekt.map { $0.faza == "night" } ?? !dzien
        var w = WarunkiSceny(noc: noc)
        w.burzowo = s.contains("stormy") || s == "rainy-heavy" || efekt?.rodzaj == "storm" || efekt?.rodzaj == "rain-heavy"
        w.deszczowo = s.hasPrefix("rainy") || s == "cloudy"
        w.mglisto = s.contains("foggy") || efekt?.rodzaj == "fog"
        // gwiazdy w scenach niespecjalnych tylko przy pogodnej nocy albo rzadkich chmurach
        if let e = efekt {
            w.gwiazdy = e.rodzaj == "clear-night" || (e.rodzaj == "clouds" && e.natezenie < 0.5)
        } else {
            w.gwiazdy = s == "clear-night" || s == "partly-night"
        }
        w.karta = karta
        w.bezRuchu = bezRuchu
        if let u = ustawienia {
            func kolor(_ k: String) -> Color? { u.s(k).count == 7 ? Color(hex: u.s(k)) : nil }
            w.kolorNieba = kolor("skyColor")
            w.kolorWody = kolor("waterColor")
            w.kolorPlazy = kolor("beachColor")
            w.predkoscFal = u.d("waveSpeed") ?? 1
            w.silaFal = u.d("waveIntensity") ?? 1
            w.bioluminescencja = u.b("beachBiolum")
        }
        return w
    }
}

/// Belka z datą pod grafiką jako przedłużenie podłoża sceny (Karol, 23.11: „dopasowany pod wydarzenie, jak śnieg
/// to biały wyróżniający się, a jak pole to brązowy jak ziemia”): śnieg, ziemia, trawa, piasek, woda albo bruk
/// Rynku, w nocy przyciemnione; tekst ciemny albo jasny, zawsze z kontrastem co najmniej 4,5 : 1. Motywy
/// okolicznościowe mają u góry pasek w swoim kolorze i medal z ikoną jak karta daty na stronie.
struct PaletaBelki {
    enum Ikona { case kalendarz, rozeta, sniezynka, czapka, gwiazda, choinka, swieca, rok(String), brak }

    let tlo: Color
    /// Pasek akcentu motywu u góry belki (nil = belka zlewa się z podłożem).
    let akcent: Color?
    let data: Color
    let drugaLinia: Color
    let medal: Color
    let obwodka: Color
    let ikona: Ikona

    /// Podłoże sceny (dzień, noc) i czy jest jasne (wtedy tekst ciemny).
    private static func podloze(_ s: ScenaStrony, noc: Bool) -> (String, Bool) {
        switch s {
        case .zima, .mikolajki, .wigilia, .swieta, .winterVillage, .winterChristmas, .alpinePeaks:
            return noc ? ("#24324a", false) : ("#eef3f8", true)          // śnieg
        case .sylwester, .nowyRok:
            return noc ? ("#121a2c", false) : ("#e4ebf3", true)          // zaśnieżony Rynek
        case .autumnForest:
            return noc ? ("#241812", false) : ("#5e3219", false)         // ziemia jesiennej łąki
        case .easterSpring:
            return noc ? ("#0c1a14", false) : ("#1b4332", false)         // trawa
        case .parkLanterns:
            return noc ? ("#141824", false) : ("#2b2936", false)         // mokra alejka
        case .powstanczyBiwak:
            return noc ? ("#1a120c", false) : ("#3a2a1c", false)         // ziemia przy ognisku
        case .beach:
            return noc ? ("#2c2416", false) : ("#e3c27a", true)          // piasek
        case .ocean:
            return noc ? ("#04101c", false) : ("#00405a", false)         // głęboka woda
        case .autumnLake:
            return noc ? ("#10162a", false) : ("#24314a", false)         // jezioro
        case .newYear:
            return ("#0b0f24", false)                                    // woda nocą
        case .ratusz11Listopada:
            return noc ? ("#161a24", false) : ("#4a505c", false)         // bruk Rynku
        case .andrzejki:
            return noc ? ("#26103e", false) : ("#5b3a8a", false)         // fioletowe wzgórza
        }
    }

    /// Kolor z CSS z serwera: #rrggbb albo rgba(r,g,b,a).
    static func kolorCSS(_ tekst: String?) -> Color? {
        guard let s = tekst?.trimmingCharacters(in: .whitespaces), !s.isEmpty else { return nil }
        if s.hasPrefix("#") { return s.count == 7 ? Color(hex: s) : nil }
        guard s.hasPrefix("rgb"), let a = s.firstIndex(of: "("), let b = s.lastIndex(of: ")"), a < b else { return nil }
        let liczby: [Double] = s[s.index(after: a)..<b].split(separator: ",")
            .compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        guard liczby.count >= 3 else { return nil }
        let krycie: Double = liczby.count > 3 ? liczby[3] : 1
        return Color(red: liczby[0] / 255, green: liczby[1] / 255, blue: liczby[2] / 255).opacity(krycie)
    }

    /// API 1.8.3: belka z theme.dateBar. Tło to nadal podłoże sceny (decyzja Karola); z serwera medal, akcent
    /// i barwy medalu, a przy scenie SVG (podłoża aplikacja nie zna) także tło i kolory tekstu motywu.
    private static func zSerwera(_ scena: WyborSceny.Scena, noc: Bool, rok: Int) -> PaletaBelki {
        let (hex, jasne) = scena.svg != nil ? ("#111827", false) : podloze(scena.strony, noc: noc)
        var tlo = Color(hex: hex)
        var data = jasne ? Color(hex: "#0f172a") : Color.white
        var druga = jasne ? Color(hex: "#334155") : Color(hex: "#e2e8f0")
        guard let p = scena.pasek else {
            return PaletaBelki(tlo: tlo, akcent: nil, data: data, drugaLinia: druga,
                               medal: Color(hex: "#f5a623"), obwodka: Color(hex: "#e8890a"), ikona: .kalendarz)
        }
        if scena.svg != nil {
            tlo = kolorCSS(noc ? p.tloNoc : p.tloDzien) ?? tlo
            data = kolorCSS(noc ? p.dataNoc : p.dataDzien) ?? data
            druga = kolorCSS(noc ? p.drugaNoc : p.drugaDzien) ?? (noc ? Color(hex: "#e2e8f0") : Color(hex: "#0f172a"))
        }
        let ikona: Ikona
        switch p.ikona ?? "" {
        case "snowflake": ikona = .sniezynka
        case "santa-hat": ikona = .czapka
        case "star": ikona = .gwiazda
        case "tree": ikona = .choinka
        case "candle": ikona = .swieca
        case "year": ikona = .rok(p.napisMedalu ?? String(rok % 100))
        default:
            let listopad = scena.strony == .ratusz11Listopada || scena.strony == .powstanczyBiwak
            ikona = scena.svg != nil ? .brak : (listopad ? .rozeta : .kalendarz)
        }
        return PaletaBelki(tlo: tlo, akcent: kolorCSS(p.akcent), data: data, drugaLinia: druga,
                           medal: kolorCSS(p.medal) ?? Color(hex: "#f5a623"), obwodka: kolorCSS(p.obwodka) ?? Color(hex: "#e8890a"),
                           ikona: ikona)
    }

    static func dla(_ scena: WyborSceny.Scena, noc: Bool, rok: Int) -> PaletaBelki {
        if scena.zSerwera { return zSerwera(scena, noc: noc, rok: rok) }
        // zapas dla serwera sprzed API 1.8.3: barwy i medale wpisane w aplikacji
        let (hex, jasne) = scena.svg != nil ? ("#111827", false) : podloze(scena.strony, noc: noc)
        let data = jasne ? Color(hex: "#0f172a") : Color.white
        let druga = jasne ? Color(hex: "#334155") : Color(hex: "#e2e8f0")
        func p(_ akcent: String?, _ medal: String, _ obwodka: String, _ ikona: Ikona) -> PaletaBelki {
            PaletaBelki(tlo: Color(hex: hex), akcent: akcent.map { Color(hex: $0) }, data: data, drugaLinia: druga,
                        medal: Color(hex: medal), obwodka: Color(hex: obwodka), ikona: ikona)
        }
        if scena.svg != nil { return p("#d4af37", "#0a1f44", "#d4af37", .brak) }
        switch scena.strony {
        case .ratusz11Listopada, .powstanczyBiwak: return p("#dc143c", "#dc143c", "#9e0b22", .rozeta)
        case .zima: return p("#0284c7", "#0284c7", "#7dd3fc", .sniezynka)
        case .mikolajki: return p("#b91830", "#991b2e", "#f5d287", .czapka)
        case .wigilia: return p("#d4af37", "#7a1220", "#f5de9e", .gwiazda)
        case .swieta: return p("#c8a45d", "#7a1220", "#e5c98b", .choinka)
        case .sylwester: return p("#d4af37", "#0a162c", "#d4af37", .gwiazda)
        case .nowyRok: return p("#d4af37", "#0a162c", "#ffecaf", .rok(String(rok % 100)))
        case .andrzejki: return p("#f5b942", "#5b21b6", "#f5b942", .swieca)
        default: return p(nil, "#f5a623", "#e8890a", .kalendarz)
        }
    }

    /// Linie pod datą jak na stronie (TvmlSeasonal.dateBar), rozdzielone, żeby nic nie ucinało się w pół słowa:
    /// wyróżnienie (święto z kalendarza albo przedrostek motywu) i imieniny.
    static func linie(_ scena: WyborSceny.Scena, swieto: String?, imieniny: String?, dzisiaj: Date = Date()) -> (wyroznienie: String?, imieniny: String?) {
        // API 1.8.3: przedrostek, „jutro imieniny” i ukrywanie imienin z theme.dateBar, na każdy dzień (days[])
        if scena.zSerwera {
            guard let p = scena.pasek else { return (swieto, imieniny) }
            let d = p.dzien(dzisiaj)
            var imiona = imieniny
            if !d.jutro.isEmpty {
                imiona = d.jutro.prefix(1).uppercased() + d.jutro.dropFirst()
            } else if d.bezImienin {
                imiona = nil
            }
            return (swieto ?? (d.przedrostek.isEmpty ? nil : d.przedrostek), imiona)
        }
        // zapas dla serwera sprzed API 1.8.3: przedrostki wpisane w aplikacji
        var kal = Calendar(identifier: .gregorian)
        kal.timeZone = TimeZone(identifier: "Europe/Warsaw") ?? .current
        let m = kal.component(.month, from: dzisiaj), d = kal.component(.day, from: dzisiaj), r = kal.component(.year, from: dzisiaj)
        let nastepny = m == 1 ? r : r + 1
        var przedrostek: String?
        var imiona = imieniny
        if scena.svg == nil {
            switch scena.strony {
            case .ratusz11Listopada: przedrostek = (m == 11 && d == 11) ? "Święto Niepodległości" : nil
            case .zima: przedrostek = "Zima w Lesznie"
            case .mikolajki: przedrostek = "Mikołajki w Lesznie"
            case .wigilia: przedrostek = "Wigilia Bożego Narodzenia"
            case .swieta: przedrostek = m == 12 && (d == 25 || d == 26) ? "Boże Narodzenie" : "Świąteczne Leszno"
            case .sylwester: przedrostek = m == 12 && d == 31 ? "Sylwester • Gala \(nastepny)" : "Odliczanie do \(nastepny)"
            case .nowyRok:
                przedrostek = "Nowy Rok \(nastepny) • Szczęśliwego Nowego Roku!"
                imiona = nil
            case .andrzejki:
                if m == 11 && d == 29 {
                    przedrostek = "Wieczór andrzejkowy"
                    imiona = "Jutro imieniny: Andrzeja"
                } else {
                    przedrostek = m == 11 && d == 28 ? "Andrzejkowy weekend" : "Andrzejki"
                }
            default: break
            }
        }
        // święto z kalendarza ma pierwszeństwo (11 listopada: „Narodowe Święto Niepodległości” zamiast przedrostka)
        return (swieto ?? przedrostek, imiona)
    }
}

/// Medal na belce: koło w barwie sceny z ikoną (kalendarz, rozeta 11 Listopada, śnieżynka, czapka Mikołaja…).
struct MedalBelki: View {
    let paleta: PaletaBelki

    var body: some View {
        ZStack {
            switch paleta.ikona {
            case .kalendarz:
                Circle().fill(RadialGradient(colors: [Color(hex: "#ffc04d"), Color(hex: "#e8890a")], center: UnitPoint(x: 0.38, y: 0.38), startRadius: 1, endRadius: 22))
                    .shadow(color: Color(hex: "#f5a623").opacity(0.5), radius: 4)
                Image(systemName: "calendar").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
            case .rozeta:
                Circle().fill(Color(hex: "#dc143c"))
                Circle().strokeBorder(Color(hex: "#9e0b22"), style: StrokeStyle(lineWidth: 5, dash: [2.6, 2.6]))
                Circle().fill(Color(hex: "#780616")).padding(9)
                Circle().fill(Color.white).padding(10.5)
                Circle().fill(Color(hex: "#f8fafc")).padding(14).overlay(Circle().stroke(Color(hex: "#cbd5e1"), lineWidth: 0.8).padding(14))
            default:
                Circle().fill(paleta.medal).overlay(Circle().stroke(paleta.obwodka, lineWidth: 1.5))
                ikona
            }
        }
        .frame(width: 34, height: 34)
        .accessibilityHidden(true)
    }

    @ViewBuilder private var ikona: some View {
        switch paleta.ikona {
        case .sniezynka: Image(systemName: "snowflake").font(.system(size: 16, weight: .bold)).foregroundStyle(.white)
        case .czapka: Image(systemName: "gift.fill").font(.system(size: 14, weight: .bold)).foregroundStyle(Color(hex: "#e11d48"), .white)
        case .gwiazda: Image(systemName: "star.fill").font(.system(size: 15, weight: .bold)).foregroundStyle(Color(hex: "#ffe88a"))
        case .choinka: Image(systemName: "tree.fill").font(.system(size: 15, weight: .bold)).foregroundStyle(Color(hex: "#16a34a"))
        case .swieca: Image(systemName: "flame.fill").font(.system(size: 15, weight: .bold)).foregroundStyle(Color(hex: "#f5b942"))
        case .rok(let r): Text(r).font(.system(size: 14, weight: .heavy)).foregroundStyle(Color(hex: "#ffecaf"))
        default: EmptyView()
        }
    }
}
