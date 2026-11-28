import SwiftUI

/// Wersja wyglądu ustawiana w panelu tvml.pl (Ustawienia → Wygląd): żałoba, 11 Listopada, zima, Boże Narodzenie,
/// Sylwester i Nowy Rok. Ta sama reguła pierwszeństwa co na stronie (portal-chrome.js):
/// żałoba > 11 Listopada > Boże Narodzenie > Sylwester i Nowy Rok > zima.
struct Motyw: Equatable {
    enum Rodzaj: String { case zwykly, zaloba, listopad, andrzejki, zima, mikolajki, czapeczka, wigilia, swieta, sylwester, nowyRok }
    struct Pasek: Equatable {
        var etykieta: String
        var tekst: String
        var stopka: String?
        var odliczanieDo: Date?
        /// API 1.5.1: theme.topbar.url, np. https://tvml.pl/strona/andrzejki; stuknięcie w belkę go otwiera
        var adres: URL?
    }
    enum Scena { case wiosna, lato, jesien, zima, swieta, sylwester, listopad }

    /// Kolory wersji z panelu (API 1.1: theme.colors.light i .dark). nil = kolory z projektu.
    struct Paleta: Equatable {
        var jasna: [String: String]
        var ciemna: [String: String]
        var gradientJasny: [String]
        var gradientCiemny: [String]
        var paski: [String]

        init?(_ c: Slownik) {
            let j = c.slownik("light"), d = c.slownik("dark")
            guard !j.isEmpty else { return nil }
            func pola(_ s: Slownik) -> [String: String] {
                var w: [String: String] = [:]
                for k in ["accent", "live", "tabActive", "headerLine", "gold"] where !s.s(k).isEmpty { w[k] = s.s(k) }
                return w
            }
            jasna = pola(j); ciemna = pola(d.isEmpty ? j : d)
            gradientJasny = (j["headerGradient"] as? [String]) ?? []
            gradientCiemny = (d["headerGradient"] as? [String]) ?? gradientJasny
            paski = (j["headerLineStripes"] as? [String]) ?? []
        }

        func kolor(_ k: String) -> Color? {
            guard let a = jasna[k] else { return nil }
            return Color.tryb(Paleta.liczba(a), Paleta.liczba(ciemna[k] ?? a))
        }
        func napis(_ k: String) -> Color? {
            guard let a = jasna[k] else { return nil }
            return Color.tryb(Kontrast.napisHex(na: a), Kontrast.napisHex(na: ciemna[k] ?? a))
        }
        var naglowek: [Color]? {
            guard gradientJasny.count >= 2 else { return nil }
            return gradientJasny.enumerated().map { i, h in
                Color.tryb(Paleta.liczba(h), Paleta.liczba(i < gradientCiemny.count ? gradientCiemny[i] : h))
            }
        }
        static func liczba(_ hex: String) -> UInt32 {
            var t = hex
            if t.hasPrefix("#") { t.removeFirst() }
            return UInt32(t, radix: 16) ?? 0x0a1f44
        }
    }

    var rodzaj: Rodzaj
    var wariant: String = ""
    var pasek: Pasek?
    var paleta: Paleta?

    static let zwykly = Motyw(rodzaj: .zwykly)

    // MARK: wygląd

    var naglowek: [Color] {
        if let n = paleta?.naglowek { return n }
        return naglowekProjektu
    }
    /// Dwukolorowa linia pod banerem (11 Listopada: biało-czerwona).
    var paskiLinii: [Color] { (paleta?.paski ?? []).map { Color(hex: $0) } }

    private var naglowekProjektu: [Color] {
        switch rodzaj {
        case .zima: return [Color(hex: "#040d1a"), Color(hex: "#0a1e38"), Color(hex: "#0f3156")]
        // Andrzejki (Noc wróżb 28–30.11): jak w panelu, gdy API nie poda kolorów
        case .andrzejki: return [Color(hex: "#07040f"), Color(hex: "#160b2c"), Color(hex: "#2e1046")]
        case .wigilia, .swieta: return [Color(hex: "#14070c"), Color(hex: "#24101a")]
        case .sylwester, .nowyRok: return [Color(hex: "#0d0b24"), Color(hex: "#1b1640")]
        case .zaloba: return [Color(hex: "#16181c"), Color(hex: "#23262c")]
        default: return [Color(hex: "#0a1f44"), Color(hex: "#0d2856")]
        }
    }
    var linia: Color? {
        if let k = paleta?.kolor("headerLine") { return k }
        switch rodzaj {
        case .zima: return Color(hex: "#38bdf8")
        case .andrzejki: return Color(hex: "#f5b942")
        case .listopad: return Color(hex: "#dc143c")
        case .mikolajki, .czapeczka: return Color(hex: "#c8102e")
        case .wigilia, .swieta: return Color(hex: "#e0b45a")
        case .sylwester, .nowyRok: return Color(hex: "#e8c26a")
        case .zaloba: return Color(hex: "#4b5563")
        case .zwykly: return nil
        }
    }
    var naZywo: Color {
        if let k = paleta?.kolor("live") { return k }
        switch rodzaj {
        case .zima: return Color(hex: "#0369a1")
        case .andrzejki: return Color(hex: "#5b21b6")
        case .mikolajki, .czapeczka: return Color(hex: "#b91c1c")
        case .wigilia, .swieta: return Color(hex: "#9b1c1c")
        case .sylwester, .nowyRok: return Color(hex: "#5b21b6")
        case .zaloba: return Color(hex: "#374151")
        default: return Color(hex: "#c8102e")
        }
    }
    /// Kolor zaznaczonej zakładki i kategorii.
    var akcent: Color {
        if let k = paleta?.kolor("tabActive") ?? paleta?.kolor("accent") { return k }
        switch rodzaj {
        case .zwykly: return Color.tryb(0x0a1f44, 0xf0b03a)
        case .listopad: return Color(hex: "#a50e26")
        case .andrzejki: return Color.tryb(0x7c3aed, 0xa78bfa)
        case .zaloba: return Color.tryb(0x111827, 0xd1d5db)
        default: return naZywo
        }
    }
    /// Napis na kolorze akcentu: w nocy akcent zwykły jest złoty, a żałobny jasnoszary, więc wtedy ciemny napis.
    var napisNaAkcencie: Color {
        if let k = paleta?.napis("tabActive") ?? paleta?.napis("accent") { return k }
        switch rodzaj {
        case .zwykly: return Color.tryb(0xffffff, 0x1d1400)
        case .zaloba: return Color.tryb(0xffffff, 0x111827)
        default: return .white
        }
    }
    var snieg: Bool { [.zima, .mikolajki, .czapeczka, .wigilia, .swieta].contains(rodzaj) }
    var iskry: Bool { rodzaj == .sylwester || rodzaj == .nowyRok }
    var kolorPlatkow: Color {
        switch rodzaj {
        case .wigilia: return Color(hex: "#f6e3b0")
        case .sylwester, .nowyRok: return Color(hex: "#e8c26a")
        default: return .white
        }
    }
    var szarosc: Bool { rodzaj == .zaloba }
    var logo: String { rodzaj == .zaloba ? "LogoZaloba" : "LogoNoc" }

    var tloPaska: [Color] {
        switch rodzaj {
        case .sylwester, .nowyRok: return [Color(hex: "#0a0820"), Color(hex: "#17123a")]
        case .listopad: return [Color(hex: "#7f0d1f"), Color(hex: "#a50e26")]
        case .zima: return [Color(hex: "#040d1a"), Color(hex: "#0f3156")]
        default: return [Color(hex: "#1c0609"), Color(hex: "#2b0b12")]
        }
    }
    var zlotoPaska: Color {
        switch rodzaj {
        case .sylwester, .nowyRok: return Color(hex: "#e8c26a")
        case .listopad: return .white
        case .zima: return Color(hex: "#7dd3fc")
        case .mikolajki: return Color(hex: "#f3d27a")
        default: return Color(hex: "#e0b45a")
        }
    }

    /// Ikona aplikacji wbudowana w aplikację (Assets: IkonaZima, IkonaSwieta). nil = zwykła.
    var ikona: String? {
        switch rodzaj {
        case .zima: return "IkonaZima"
        case .mikolajki, .czapeczka, .wigilia, .swieta: return "IkonaSwieta"
        default: return nil
        }
    }

    func scena(miesiac: Int) -> Scena {
        switch rodzaj {
        case .zima: return .zima
        case .mikolajki, .czapeczka, .wigilia, .swieta: return .swieta
        case .sylwester, .nowyRok: return .sylwester
        case .listopad: return .listopad
        default:
            switch miesiac {
            case 12, 1, 2: return .zima
            case 3, 4, 5: return .wiosna
            case 6, 7, 8: return .lato
            default: return .jesien
            }
        }
    }

    // MARK: wyliczenie z portal-config

    static func wylicz(z c: Slownik, teraz: Date = .now) -> Motyw {
        let strefa = TimeZone(identifier: "Europe/Warsaw") ?? .current
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = strefa
        f.dateFormat = "yyyy-MM-dd'T'HH:mm"
        var kal = Calendar(identifier: .gregorian)
        kal.timeZone = strefa
        let dzien = kal.component(.day, from: teraz)
        let miesiac = kal.component(.month, from: teraz)
        let rok = kal.component(.year, from: teraz)

        func data(_ k: String) -> Date? { f.date(from: c.s(k)) }
        func wOknie(_ od: String, _ do_: String) -> Bool {
            guard let a = data(od), let b = data(do_) else { return false }
            return teraz >= a && teraz <= b.addingTimeInterval(59)
        }
        func aktywny(_ p: String) -> (on: Bool, pasek: Bool) {
            let tryb = c.s(p + "Mode")
            switch tryb {
            case "scheduled": return (wOknie(p + "ThemeFrom", p + "ThemeUntil"), false)
            case "on_with_bar": return (true, true)
            case "", "off": return (false, false)
            default: return (tryb.hasPrefix("on"), false)
            }
        }
        func pasek(_ p: String, wymus: Bool) -> Bool {
            wymus || (c.b(p + "TopbarEnabled") && wOknie(p + "TopbarFrom", p + "TopbarUntil"))
        }

        if c.b("mourningMode") {
            if let koniec = data("mourningUntil"), teraz > koniec.addingTimeInterval(59) {
                // żałoba minęła
            } else {
                return Motyw(rodzaj: .zaloba)
            }
        }

        let indep = aktywny("indep")
        if indep.on {
            return Motyw(rodzaj: .listopad, wariant: c.s("indepLogoVariant"),
                         pasek: pasek("indep", wymus: indep.pasek) ? Pasek(etykieta: "ŚWIĘTO NIEPODLEGŁOŚCI", tekst: c.s("indepTopbarText")) : nil)
        }

        let xmasTryb = c.s("xmasMode")
        let xmas = aktywny("xmas")
        if xmas.on {
            let rodzaj: Rodzaj
            if xmasTryb == "on_mikolajki" { rodzaj = .mikolajki }
            else if xmasTryb == "on_wigilia" { rodzaj = .wigilia }
            else if miesiac == 12 && dzien == 6 { rodzaj = .mikolajki }
            else if miesiac == 12 && dzien == 24 { rodzaj = .wigilia }
            else if miesiac == 12 && dzien >= 25 { rodzaj = .swieta }
            else { rodzaj = .czapeczka }
            let stopka = "WYDANIE ŚWIĄTECZNE • GRUDZIEŃ \(rok)"
            var p: Pasek?
            if rodzaj == .mikolajki, !c.s("xmasMikolajkiText").isEmpty {
                p = Pasek(etykieta: "6 GRUDNIA · MIKOŁAJKI", tekst: c.s("xmasMikolajkiText"), stopka: stopka)
            } else if pasek("xmas", wymus: xmas.pasek) {
                p = Pasek(etykieta: "BOŻE NARODZENIE", tekst: c.s("xmasTopbarText"), stopka: stopka)
            }
            return Motyw(rodzaj: rodzaj, pasek: p)
        }

        let nye = aktywny("nye")
        if nye.on {
            let nowyRok = miesiac == 1 || c.s("nyeMode") == "on_new_year"
            var p: Pasek?
            if pasek("nye", wymus: nye.pasek) {
                if nowyRok {
                    p = Pasek(etykieta: "NOWY ROK \(rok)", tekst: c.s("nyeTopbarText"))
                } else {
                    var dc = DateComponents(); dc.year = rok + 1; dc.month = 1; dc.day = 1
                    p = Pasek(etykieta: "SYLWESTER \(rok)/\(rok + 1)", tekst: c.s("nyeTopbarText"), odliczanieDo: kal.date(from: dc))
                }
            }
            return Motyw(rodzaj: nowyRok ? .nowyRok : .sylwester, wariant: c.s("nyeLogoVariant"), pasek: p)
        }

        let zima = aktywny("winter")
        if zima.on {
            return Motyw(rodzaj: .zima, wariant: c.s("winterLogoVariant"),
                         pasek: pasek("winter", wymus: zima.pasek) ? Pasek(etykieta: "ZIMA", tekst: c.s("winterTopbarText")) : nil)
        }
        return .zwykly
    }

    /// Wersja wyliczona przez serwer (API v1, pole theme). nil, gdy odpowiedź jej nie ma.
    static func zV1(_ t: Slownik, wersjaApi: String = "1.0.0", teraz: Date = .now) -> Motyw? {
        let id = t.s("id")
        guard !id.isEmpty else { return nil }
        let wariant = t.s("variant")
        let miesiac = Calendar.current.component(.month, from: teraz)
        let rodzaj: Rodzaj
        switch id {
        case "mourning", "zaloba": rodzaj = .zaloba
        case "indep", "11listopada": rodzaj = .listopad
        // Andrzejki: wcześniej nieznane, więc cały wygląd zostawał zwykły (Karol, 24.11: „Tak, dodaj”)
        case "andrzejki": rodzaj = .andrzejki
        case "winter": rodzaj = .zima
        case "xmas":
            switch wariant {
            case "mikolajki": rodzaj = .mikolajki
            case "wigilia": rodzaj = .wigilia
            case "swieta": rodzaj = .swieta
            default: rodzaj = .czapeczka
            }
        case "nye": rodzaj = (wariant == "new_year" || wariant == "nowy_rok" || miesiac == 1) ? .nowyRok : .sylwester
        default: rodzaj = .zwykly
        }
        var pasek: Pasek?
        let p = t.slownik("topbar")
        if !p.s("text").isEmpty {
            let domyslna: String
            switch rodzaj {
            case .listopad: domyslna = "ŚWIĘTO NIEPODLEGŁOŚCI"
            case .andrzejki: domyslna = "ANDRZEJKI"
            case .zima: domyslna = "ZIMA"
            case .mikolajki: domyslna = "6 GRUDNIA · MIKOŁAJKI"
            case .czapeczka, .wigilia, .swieta: domyslna = "BOŻE NARODZENIE"
            case .sylwester: domyslna = "SYLWESTER"
            case .nowyRok: domyslna = "NOWY ROK"
            default: domyslna = "TVML"
            }
            pasek = Pasek(etykieta: p.s("label").isEmpty ? domyslna : p.s("label"),
                          tekst: p.s("text"),
                          stopka: p.s("foot").isEmpty ? nil : p.s("foot"),
                          odliczanieDo: ISO8601DateFormatter().date(from: p.s("countdownTo")),
                          adres: p.s("url").isEmpty ? nil : URL(string: p.s("url")))
        }
        // API 1.0 podawało tu kolory bazowe portalu, dopiero 1.1 kolory wersji
        let kolory = rodzaj != .zwykly && wersjaApi.compare("1.1", options: .numeric) != .orderedAscending ? Paleta(t.slownik("colors")) : nil
        return Motyw(rodzaj: rodzaj, wariant: wariant, pasek: pasek, paleta: kolory)
    }

    /// Do zrzutów i testów: -motyw zima itp.
    static func zArgumentu(_ nazwa: String?) -> Motyw? {
        guard let nazwa, let r = Rodzaj(rawValue: nazwa) else { return nil }
        switch r {
        case .wigilia, .swieta:
            return Motyw(rodzaj: r, pasek: Pasek(etykieta: "BOŻE NARODZENIE", tekst: "Spokojnych, pełnych ciepła i rodzinnej atmosfery Świąt życzy Zespół Redakcyjny TVML.PL", stopka: "WYDANIE ŚWIĄTECZNE • GRUDZIEŃ 2026"))
        case .sylwester:
            return Motyw(rodzaj: r, pasek: Pasek(etykieta: "SYLWESTER 2026/2027", tekst: "Szampańskiej nocy sylwestrowej oraz pomyślności i sukcesów w Nowym Roku 2027 życzy Zespół Redakcyjny TVML.PL", odliczanieDo: Date().addingTimeInterval(42 * 86400)))
        case .zima: return Motyw(rodzaj: r, wariant: "c")
        case .listopad: return Motyw(rodzaj: r, wariant: "b")
        default: return Motyw(rodzaj: r)
        }
    }
}

/// Ozdoba na logo, jak na stronie: bałwanek, czapka Mikołaja, gwiazda, flaga, „2027”.
struct OzdobaLogo: View {
    let motyw: Motyw

    var body: some View {
        switch motyw.rodzaj {
        case .zima: Balwan().frame(width: 22, height: 28).offset(x: 14, y: -12)
        case .andrzejki: AndrzejkiLogo().frame(width: 26.7, height: 15.2).offset(x: 0, y: 0.6)
        case .mikolajki, .czapeczka: CzapkaMikolaja().frame(width: 30, height: 24).rotationEffect(.degrees(14)).offset(x: 12, y: -14)
        // Wigilia: przelatujące sanie jak na stronie (Karol, 24.11: „Tak, teraz”); Boże Narodzenie: gwiazda
        case .wigilia: SanieLogo().frame(width: 30.5, height: 14).offset(x: 5, y: 1)
        case .swieta: Gwiazda().fill(Color(hex: "#e0b45a")).frame(width: 24, height: 24).offset(x: 14, y: -12)
        case .listopad: Flaga().frame(width: 26, height: 22).offset(x: 14, y: -12)
        case .sylwester, .nowyRok:
            Text(verbatim: String(Calendar.current.component(.year, from: .now) + (Calendar.current.component(.month, from: .now) == 12 ? 1 : 0)))
                .font(.system(size: 9, weight: .heavy))
                .foregroundStyle(Color(hex: "#1b1640"))
                .padding(.horizontal, 5).padding(.vertical, 2)
                .background(Color(hex: "#e8c26a"), in: RoundedRectangle(cornerRadius: 6))
                .offset(x: 18, y: -10)
        default: EmptyView()
        }
    }
}

struct Balwan: View {
    var body: some View {
        Canvas { ctx, size in
            let s = size.width / 22
            func k(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: (x - r) * s, y: (y - r) * s, width: 2 * r * s, height: 2 * r * s)) }
            ctx.fill(k(11, 20, 7), with: .color(Color(hex: "#f8fbff")))
            ctx.fill(k(11, 10, 5), with: .color(Color(hex: "#f8fbff")))
            ctx.fill(Path(CGRect(x: 6 * s, y: 1 * s, width: 10 * s, height: 5 * s)), with: .color(Color(hex: "#0f3156")))
            ctx.fill(Path(CGRect(x: 4 * s, y: 5 * s, width: 14 * s, height: 2 * s)), with: .color(Color(hex: "#0f3156")))
            var nos = Path(); nos.move(to: CGPoint(x: 11 * s, y: 11 * s)); nos.addLine(to: CGPoint(x: 14 * s, y: 12 * s)); nos.addLine(to: CGPoint(x: 11 * s, y: 13 * s))
            ctx.fill(nos, with: .color(Color(hex: "#f97316")))
            ctx.fill(k(9, 9, 0.9), with: .color(Color(hex: "#0b1830")))
            ctx.fill(k(13, 9, 0.9), with: .color(Color(hex: "#0b1830")))
            var szal = Path(); szal.move(to: CGPoint(x: 6 * s, y: 15 * s)); szal.addLine(to: CGPoint(x: 16 * s, y: 15 * s))
            ctx.stroke(szal, with: .color(Color(hex: "#38bdf8")), style: StrokeStyle(lineWidth: 2 * s, lineCap: .round))
        }
    }
}

/// Andrzejki przy logu, jak na stronie (Panel-TVML panel-special-themes.js, klucz „andrzejki”, viewBox logo
/// 0 0 270 126, tu x 186…270, y 2…50): sierp księżyca, gwiazdki, poświata i świeca z płomieniem.
struct AndrzejkiLogo: View {
    var body: some View {
        Canvas { ctx, size in
            let k = size.height / 48
            var g = ctx
            g.scaleBy(x: k, y: k)
            g.translateBy(x: -186, y: -2)
            g.addFilter(.shadow(color: Color(hex: "#07040f").opacity(0.6), radius: 1.4, x: 0, y: 1.4))
            func kolo(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)) }
            // sierp: dłuższy łuk koła r 15 (środek 214,56; 20,29) z (210,6) do (226,30) i z powrotem półkole r 14,42
            // (środek 218; 18), jak łuki SVG ze strony (r 12 jest za mały na tę cięciwę, więc przeglądarka go powiększa)
            var sierp = Path()
            let n = 40
            for i in 0...n {
                let a = (-107.7 - 212.0 * Double(i) / Double(n)) * .pi / 180
                let p = CGPoint(x: 214.56 + 15 * cos(a), y: 20.29 + 15 * sin(a))
                if i == 0 { sierp.move(to: p) } else { sierp.addLine(to: p) }
            }
            for i in 0...n {
                let a = (56.3 + 180.0 * Double(i) / Double(n)) * .pi / 180
                sierp.addLine(to: CGPoint(x: 218 + 14.42 * cos(a), y: 18 + 14.42 * sin(a)))
            }
            sierp.closeSubpath()
            g.fill(sierp, with: .color(Color(hex: "#f5d98a")))
            var gwiazdka = Path()
            let punkty: [(Double, Double)] = [(236, 4), (237.4, 7.2), (240.6, 8.6), (237.4, 10), (236, 13.2), (234.6, 10), (231.4, 8.6), (234.6, 7.2)]
            for (i, p) in punkty.enumerated() {
                if i == 0 { gwiazdka.move(to: CGPoint(x: p.0, y: p.1)) } else { gwiazdka.addLine(to: CGPoint(x: p.0, y: p.1)) }
            }
            gwiazdka.closeSubpath()
            g.fill(gwiazdka, with: .color(.white))
            g.fill(kolo(191, 10, 1.8), with: .color(.white))
            g.fill(kolo(266, 12, 1.5), with: .color(.white))
            // poświata i świeca
            g.fill(kolo(254, 24, 13), with: .radialGradient(Gradient(colors: [Color(hex: "#f5b942").opacity(0.55), Color(hex: "#f5b942").opacity(0)]),
                                                         center: CGPoint(x: 254, y: 24), startRadius: 0, endRadius: 13))
            let swieca = Path(roundedRect: CGRect(x: 249, y: 28, width: 10, height: 22), cornerRadius: 2)
            g.fill(swieca, with: .linearGradient(Gradient(colors: [Color(hex: "#e7d3a8"), Color(hex: "#f8ecd0"), Color(hex: "#d9c08a")]),
                                                 startPoint: CGPoint(x: 249, y: 39), endPoint: CGPoint(x: 259, y: 39)))
            g.stroke(swieca, with: .color(Color(hex: "#b7791f")), lineWidth: 0.6)
            var knot = Path()
            knot.move(to: CGPoint(x: 254, y: 28)); knot.addLine(to: CGPoint(x: 254, y: 24.5))
            g.stroke(knot, with: .color(Color(hex: "#2a1300")), lineWidth: 1)
            var plomien = Path()
            plomien.move(to: CGPoint(x: 254, y: 11))
            plomien.addCurve(to: CGPoint(x: 254, y: 25), control1: CGPoint(x: 258.5, y: 16), control2: CGPoint(x: 258.5, y: 21))
            plomien.addCurve(to: CGPoint(x: 254, y: 11), control1: CGPoint(x: 249.5, y: 21), control2: CGPoint(x: 249.5, y: 16))
            g.fill(plomien, with: .color(Color(hex: "#f5b942")))
            var srodek = Path()
            srodek.move(to: CGPoint(x: 254, y: 16.5))
            srodek.addCurve(to: CGPoint(x: 254, y: 23.5), control1: CGPoint(x: 256, y: 19), control2: CGPoint(x: 256, y: 21.5))
            srodek.addCurve(to: CGPoint(x: 254, y: 16.5), control1: CGPoint(x: 252, y: 21.5), control2: CGPoint(x: 252, y: 19))
            g.fill(srodek, with: .color(Color(hex: "#fff4cf")))
        }
        .accessibilityHidden(true)
    }
}

/// Złote sanie z Mikołajem i reniferem przelatujące przy logu w Wigilię, jak na stronie: Panel-TVML
/// panel-special-themes.js, wariant „sleigh”, SVG w viewBox logo 0 0 270 126 (tu x 190…286, y 3…47 tej skali).
struct SanieLogo: View {
    var body: some View {
        Canvas { ctx, size in
            let k = size.height / 44
            var g = ctx
            g.scaleBy(x: k, y: k)
            g.translateBy(x: -190, y: -3)
            g.addFilter(.shadow(color: .black.opacity(0.55), radius: 1.6, x: 0.6, y: 1.8))
            let zloto = GraphicsContext.Shading.linearGradient(
                Gradient(colors: [Color(hex: "#fff6d1"), Color(hex: "#e5c158"), Color(hex: "#9e7618")]),
                startPoint: CGPoint(x: 192, y: 4), endPoint: CGPoint(x: 284, y: 46))
            func kolo(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)) }
            // ślad sań
            var slad = Path()
            slad.move(to: CGPoint(x: 192, y: 42)); slad.addQuadCurve(to: CGPoint(x: 276, y: 8), control: CGPoint(x: 228, y: 24))
            g.stroke(slad, with: zloto, style: StrokeStyle(lineWidth: 1.8, dash: [2, 3]))
            var slad2 = Path()
            slad2.move(to: CGPoint(x: 196, y: 45)); slad2.addQuadCurve(to: CGPoint(x: 274, y: 14), control: CGPoint(x: 232, y: 30))
            g.stroke(slad2, with: .color(Color(red: 245 / 255, green: 226 / 255, blue: 168 / 255).opacity(0.45)), lineWidth: 1)
            // sanie, Mikołaj, worek i renifer w grupie obróconej o −16° wokół (235, 22)
            var r = g
            r.translateBy(x: 235, y: 22); r.rotate(by: .degrees(-16)); r.translateBy(x: -235, y: -22)
            var sanie = Path()
            sanie.move(to: CGPoint(x: 198, y: 28)); sanie.addLine(to: CGPoint(x: 222, y: 28))
            sanie.addQuadCurve(to: CGPoint(x: 231, y: 21), control: CGPoint(x: 228, y: 28)); sanie.addLine(to: CGPoint(x: 226, y: 21))
            sanie.addQuadCurve(to: CGPoint(x: 220, y: 25), control: CGPoint(x: 224, y: 25)); sanie.addLine(to: CGPoint(x: 202, y: 25))
            sanie.addQuadCurve(to: CGPoint(x: 195, y: 22), control: CGPoint(x: 198, y: 20)); sanie.closeSubpath()
            r.fill(sanie, with: zloto)
            var ploza = Path()
            ploza.move(to: CGPoint(x: 196, y: 31)); ploza.addLine(to: CGPoint(x: 225, y: 31))
            ploza.addQuadCurve(to: CGPoint(x: 234, y: 26), control: CGPoint(x: 231, y: 31))
            r.stroke(ploza, with: zloto, style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
            r.fill(kolo(214, 17, 4.2), with: zloto)
            var mikolaj = Path()
            mikolaj.move(to: CGPoint(x: 208, y: 25)); mikolaj.addQuadCurve(to: CGPoint(x: 221, y: 25), control: CGPoint(x: 214, y: 19)); mikolaj.closeSubpath()
            r.fill(mikolaj, with: zloto)
            r.fill(kolo(204, 20, 5.2), with: .color(Color(hex: "#b91c1c")))
            r.stroke(kolo(204, 20, 5.2), with: zloto, lineWidth: 1.2)
            var lejce = Path()
            lejce.move(to: CGPoint(x: 222, y: 21)); lejce.addQuadCurve(to: CGPoint(x: 248, y: 18), control: CGPoint(x: 236, y: 22))
            r.stroke(lejce, with: zloto, lineWidth: 1)
            r.fill(Path(ellipseIn: CGRect(x: 245.5, y: 15.4, width: 15, height: 7.2)), with: zloto)
            var glowa = Path()
            glowa.move(to: CGPoint(x: 258, y: 17)); glowa.addLine(to: CGPoint(x: 262, y: 11)); glowa.addLine(to: CGPoint(x: 266, y: 12))
            glowa.addLine(to: CGPoint(x: 261, y: 19)); glowa.closeSubpath()
            r.fill(glowa, with: zloto)
            var poroze = Path()
            poroze.move(to: CGPoint(x: 262, y: 11)); poroze.addLine(to: CGPoint(x: 260, y: 6))
            poroze.move(to: CGPoint(x: 262, y: 11)); poroze.addLine(to: CGPoint(x: 265, y: 7))
            r.stroke(poroze, with: zloto, style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
            var nogi = Path()
            for (a, b) in [((248.0, 21.0), (243.0, 27.0)), ((251, 21), (248, 27)), ((257, 21), (263, 26)), ((259, 20), (267, 24))] {
                nogi.move(to: CGPoint(x: a.0, y: a.1)); nogi.addLine(to: CGPoint(x: b.0, y: b.1))
            }
            r.stroke(nogi, with: zloto, style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
            r.fill(kolo(266.5, 12, 1.8), with: .color(Color(hex: "#ef233c")))
            // iskierki
            var iskra = Path()
            for (i, (x, y)) in [(204.0, 12.0), (205.2, 15.2), (208.5, 16.4), (205.2, 17.6), (204, 20.8), (202.8, 17.6), (199.5, 16.4), (202.8, 15.2)].enumerated() {
                if i == 0 { iskra.move(to: CGPoint(x: x, y: y)) } else { iskra.addLine(to: CGPoint(x: x, y: y)) }
            }
            iskra.closeSubpath()
            g.fill(iskra, with: .color(Color(hex: "#fff6d1")))
            g.fill(kolo(228, 6, 1.6), with: .color(Color(hex: "#fff6d1")))
        }
        .accessibilityHidden(true)
    }
}

struct CzapkaMikolaja: View {
    var body: some View {
        Canvas { ctx, size in
            let s = size.width / 30
            var c = Path()
            c.move(to: CGPoint(x: 3 * s, y: 19 * s))
            c.addQuadCurve(to: CGPoint(x: 24 * s, y: 5 * s), control: CGPoint(x: 10 * s, y: 4 * s))
            c.addQuadCurve(to: CGPoint(x: 26 * s, y: 17 * s), control: CGPoint(x: 22 * s, y: 11 * s))
            c.closeSubpath()
            ctx.fill(c, with: .color(Color(hex: "#c8102e")))
            ctx.fill(Path(roundedRect: CGRect(x: 1 * s, y: 17 * s, width: 24 * s, height: 5 * s), cornerRadius: 2.5 * s), with: .color(Color(hex: "#f8fafc")))
            ctx.fill(Path(ellipseIn: CGRect(x: 21.8 * s, y: 1.8 * s, width: 6.4 * s, height: 6.4 * s)), with: .color(Color(hex: "#f8fafc")))
        }
    }
}

struct Gwiazda: Shape {
    func path(in r: CGRect) -> Path {
        let c = CGPoint(x: r.midX, y: r.midY)
        let zew = min(r.width, r.height) / 2
        let wew = zew * 0.32
        var p = Path()
        for i in 0..<8 {
            let kat = Double(i) * .pi / 4 - .pi / 2
            let promien = i % 2 == 0 ? zew : wew
            let pt = CGPoint(x: c.x + CGFloat(cos(kat)) * promien, y: c.y + CGFloat(sin(kat)) * promien)
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath()
        return p
    }
}

struct Flaga: View {
    var body: some View {
        Canvas { ctx, size in
            let s = size.width / 26
            ctx.fill(Path(roundedRect: CGRect(x: 2 * s, y: 1 * s, width: 1.6 * s, height: 21 * s), cornerRadius: 0.8 * s), with: .color(Color(hex: "#cbd5e1")))
            ctx.fill(Path(CGRect(x: 3.6 * s, y: 2 * s, width: 19 * s, height: 6 * s)), with: .color(.white))
            ctx.fill(Path(CGRect(x: 3.6 * s, y: 8 * s, width: 19 * s, height: 6 * s)), with: .color(Color(hex: "#dc143c")))
        }
    }
}
