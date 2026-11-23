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
    struct Scena: Equatable {
        var id = ""
        var svg: URL?
        var svgNoc: URL?
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
        func scena(_ t: Slownik) -> Scena {
            let svg = t.s("weatherSceneType") == "svg"
            return Scena(id: t.s("weatherScene"),
                         svg: svg ? URL(string: t.s("weatherSceneSvgUrl")) : nil,
                         svgNoc: svg ? URL(string: t.s("weatherSceneSvgNightUrl")) : nil)
        }
        let th = konf.slownik("theme")
        biezaca = scena(th)
        biezacaDo = iso.date(from: th.s("until"))
        okna = konf.lista("upcomingThemes").compactMap { u in
            guard let od = iso.date(from: u.s("from")), let d = iso.date(from: u.s("until")) else { return nil }
            let s = scena(u)
            guard !s.id.isEmpty || s.svg != nil else { return nil }
            return Okno(od: od, doKonca: d, scena: s)
        }
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

/// Belka z datą pod grafiką, w barwach karty daty ze strony (weather-effects.js drawSidebarCard i karta sezonowa).
struct PaletaBelki {
    enum Ikona { case kalendarz, rozeta, sniezynka, czapka, gwiazda, choinka, swieca, rok(String), brak }

    let tlo: Color
    let pasek: Color
    let pasekNaGorze: Bool
    let data: Color
    let drugaLinia: Color
    let medal: Color
    let obwodka: Color
    let ikona: Ikona

    static func dla(_ scena: WyborSceny.Scena, noc: Bool, rok: Int) -> PaletaBelki {
        let biala = Color.white.opacity(0.97)
        if scena.svg != nil {
            // motyw z panelu (SVG): domyślne barwy motywów AI ze strony
            return PaletaBelki(tlo: noc ? Color(red: 11 / 255, green: 22 / 255, blue: 40 / 255) : biala, pasek: Color(hex: "#d4af37"), pasekNaGorze: false,
                               data: noc ? Color(hex: "#d4af37") : Color(hex: "#0a1f44"), drugaLinia: noc ? Color(hex: "#e2e8f0") : Color(hex: "#0f172a"),
                               medal: Color(hex: "#0a1f44"), obwodka: Color(hex: "#d4af37"), ikona: .brak)
        }
        let ciemnaZima = Color(red: 11 / 255, green: 22 / 255, blue: 40 / 255)
        let ciemnaSwieta = Color(red: 20 / 255, green: 12 / 255, blue: 24 / 255)
        let ciemnySylwester = Color(red: 9 / 255, green: 16 / 255, blue: 32 / 255)
        let ciemny = Color(hex: "#0f172a")
        switch scena.strony {
        case .ratusz11Listopada, .powstanczyBiwak:
            return PaletaBelki(tlo: noc ? Color(red: 13 / 255, green: 21 / 255, blue: 38 / 255) : biala, pasek: Color(hex: "#dc143c"), pasekNaGorze: false,
                               data: noc ? .white : Color(hex: "#990b22"), drugaLinia: noc ? Color(hex: "#fda4af") : Color(hex: "#1e293b"),
                               medal: Color(hex: "#dc143c"), obwodka: Color(hex: "#9e0b22"), ikona: .rozeta)
        case .zima:
            return PaletaBelki(tlo: noc ? ciemnaZima : biala, pasek: Color(hex: "#0284c7"), pasekNaGorze: false,
                               data: noc ? Color(hex: "#7dd3fc") : Color(hex: "#0369a1"), drugaLinia: noc ? Color(hex: "#e2e8f0") : ciemny,
                               medal: Color(hex: "#0284c7"), obwodka: Color(hex: "#7dd3fc"), ikona: .sniezynka)
        case .mikolajki:
            return PaletaBelki(tlo: noc ? Color(red: 22 / 255, green: 12 / 255, blue: 24 / 255) : biala, pasek: Color(hex: "#b91830"), pasekNaGorze: false,
                               data: noc ? Color(hex: "#f5d287") : Color(hex: "#991b2e"), drugaLinia: noc ? Color(hex: "#f1f5f9") : ciemny,
                               medal: Color(hex: "#991b2e"), obwodka: Color(hex: "#f5d287"), ikona: .czapka)
        case .wigilia:
            return PaletaBelki(tlo: noc ? ciemnaSwieta : Color(red: 1, green: 253 / 255, blue: 246 / 255), pasek: Color(hex: "#d4af37"), pasekNaGorze: false,
                               data: noc ? Color(hex: "#f5de9e") : Color(hex: "#7a1220"), drugaLinia: noc ? Color(hex: "#f1f5f9") : ciemny,
                               medal: Color(hex: "#7a1220"), obwodka: Color(hex: "#f5de9e"), ikona: .gwiazda)
        case .swieta:
            return PaletaBelki(tlo: noc ? ciemnaSwieta : biala, pasek: Color(hex: "#c8a45d"), pasekNaGorze: false,
                               data: noc ? Color(hex: "#e5c98b") : Color(hex: "#7a1220"), drugaLinia: noc ? Color(hex: "#f1f5f9") : ciemny,
                               medal: Color(hex: "#7a1220"), obwodka: Color(hex: "#e5c98b"), ikona: .choinka)
        case .sylwester:
            return PaletaBelki(tlo: noc ? ciemnySylwester : Color(red: 1, green: 252 / 255, blue: 242 / 255), pasek: Color(hex: "#d4af37"), pasekNaGorze: false,
                               data: noc ? Color(hex: "#f5e2a8") : Color(hex: "#926612"), drugaLinia: noc ? Color(hex: "#f1f5f9") : ciemny,
                               medal: Color(hex: "#0a162c"), obwodka: Color(hex: "#d4af37"), ikona: .gwiazda)
        case .nowyRok:
            return PaletaBelki(tlo: noc ? ciemnySylwester : Color(red: 1, green: 252 / 255, blue: 242 / 255), pasek: Color(hex: "#d4af37"), pasekNaGorze: false,
                               data: noc ? Color(hex: "#ffecaf") : Color(hex: "#926612"), drugaLinia: noc ? .white : ciemny,
                               medal: Color(hex: "#0a162c"), obwodka: Color(hex: "#ffecaf"), ikona: .rok(String(rok % 100)))
        case .andrzejki:
            let fiolet = Color(red: 22 / 255, green: 11 / 255, blue: 44 / 255)
            return PaletaBelki(tlo: fiolet, pasek: Color(hex: "#f5b942"), pasekNaGorze: false,
                               data: Color(hex: "#f5c86b"), drugaLinia: Color(hex: "#e9d5ff"),
                               medal: Color(hex: "#5b21b6"), obwodka: Color(hex: "#f5b942"), ikona: .swieca)
        default:
            // zwykła karta daty ze strony: biała, niebieski pasek u góry, pomarańczowy medal z kalendarzem;
            // data ciemniejszym pomarańczem niż na stronie (#f5a623 na białym ma kontrast ok. 2, ten ok. 5)
            return PaletaBelki(tlo: .white, pasek: Color(hex: "#1565c0"), pasekNaGorze: true,
                               data: Color(hex: "#c2410c"), drugaLinia: Color(hex: "#0a1f44"),
                               medal: Color(hex: "#f5a623"), obwodka: Color(hex: "#e8890a"), ikona: .kalendarz)
        }
    }

    /// Druga linia jak na stronie (TvmlSeasonal.dateBar): przedrostek sceny, potem imieniny.
    static func drugaLinia(_ scena: WyborSceny.Scena, imieniny: String?, dzisiaj: Date = Date()) -> String? {
        var kal = Calendar(identifier: .gregorian)
        kal.timeZone = TimeZone(identifier: "Europe/Warsaw") ?? .current
        let m = kal.component(.month, from: dzisiaj), d = kal.component(.day, from: dzisiaj), r = kal.component(.year, from: dzisiaj)
        let nastepny = m == 1 ? r : r + 1
        func z(_ przedrostek: String) -> String { imieniny.map { przedrostek + " • " + $0 } ?? przedrostek }
        if scena.svg != nil { return imieniny }
        switch scena.strony {
        case .ratusz11Listopada: return (m == 11 && d == 11) ? z("Święto Niepodległości") : imieniny
        case .zima: return z("Zima w Lesznie")
        case .mikolajki: return z("Mikołajki w Lesznie")
        case .wigilia: return z("Wigilia Bożego Narodzenia")
        case .swieta: return z(m == 12 && (d == 25 || d == 26) ? "Boże Narodzenie" : "Świąteczne Leszno")
        case .sylwester: return z(m == 12 && d == 31 ? "Sylwester • Gala \(nastepny)" : "Odliczanie do \(nastepny)")
        case .nowyRok: return "Nowy Rok \(nastepny) • Szczęśliwego Nowego Roku!"
        case .andrzejki:
            if m == 11 && d == 29 { return "Wieczór andrzejkowy • jutro imieniny: Andrzeja" }
            return z(m == 11 && d == 28 ? "Andrzejkowy weekend" : "Andrzejki")
        default: return imieniny
        }
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
