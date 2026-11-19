import SwiftUI

/// Wersja wyglądu ustawiana w panelu tvml.pl (Ustawienia → Wygląd): żałoba, 11 Listopada, zima, Boże Narodzenie,
/// Sylwester i Nowy Rok. Ta sama reguła pierwszeństwa co na stronie (portal-chrome.js):
/// żałoba > 11 Listopada > Boże Narodzenie > Sylwester i Nowy Rok > zima.
struct Motyw: Equatable {
    enum Rodzaj: String { case zwykly, zaloba, listopad, zima, mikolajki, czapeczka, wigilia, swieta, sylwester, nowyRok }
    struct Pasek: Equatable {
        var etykieta: String
        var tekst: String
        var stopka: String?
        var odliczanieDo: Date?
    }
    enum Scena { case wiosna, lato, jesien, zima, swieta, sylwester, listopad }

    var rodzaj: Rodzaj
    var wariant: String = ""
    var pasek: Pasek?

    static let zwykly = Motyw(rodzaj: .zwykly)

    // MARK: wygląd

    var naglowek: [Color] {
        switch rodzaj {
        case .zima: return [Color(hex: "#040d1a"), Color(hex: "#0a1e38"), Color(hex: "#0f3156")]
        case .wigilia, .swieta: return [Color(hex: "#14070c"), Color(hex: "#24101a")]
        case .sylwester, .nowyRok: return [Color(hex: "#0d0b24"), Color(hex: "#1b1640")]
        case .zaloba: return [Color(hex: "#16181c"), Color(hex: "#23262c")]
        default: return [Color(hex: "#0a1f44"), Color(hex: "#0d2856")]
        }
    }
    var linia: Color? {
        switch rodzaj {
        case .zima: return Color(hex: "#38bdf8")
        case .listopad: return Color(hex: "#dc143c")
        case .mikolajki, .czapeczka: return Color(hex: "#c8102e")
        case .wigilia, .swieta: return Color(hex: "#e0b45a")
        case .sylwester, .nowyRok: return Color(hex: "#e8c26a")
        case .zaloba: return Color(hex: "#4b5563")
        case .zwykly: return nil
        }
    }
    var naZywo: Color {
        switch rodzaj {
        case .zima: return Color(hex: "#0369a1")
        case .mikolajki, .czapeczka: return Color(hex: "#b91c1c")
        case .wigilia, .swieta: return Color(hex: "#9b1c1c")
        case .sylwester, .nowyRok: return Color(hex: "#5b21b6")
        case .zaloba: return Color(hex: "#374151")
        default: return Color(hex: "#c8102e")
        }
    }
    /// Kolor zaznaczonej zakładki i kategorii.
    var akcent: Color {
        switch rodzaj {
        case .zwykly: return Color.tryb(0x0a1f44, 0xf0b03a)
        case .listopad: return Color(hex: "#a50e26")
        case .zaloba: return Color.tryb(0x111827, 0xd1d5db)
        default: return naZywo
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
        case .mikolajki, .czapeczka: CzapkaMikolaja().frame(width: 30, height: 24).rotationEffect(.degrees(14)).offset(x: 12, y: -14)
        case .wigilia, .swieta: Gwiazda().fill(Color(hex: "#e0b45a")).frame(width: 24, height: 24).offset(x: 14, y: -12)
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
