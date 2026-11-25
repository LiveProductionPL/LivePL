import SwiftUI

/// Pogoda w Lesznie ze stacji TVML. Scena jak na stronie: wybrana przez serwer (motyw i harmonogram, także SVG
/// z panelu), z pogodą z serwera jako nakładką na scenach zwykłych.
struct PogodaWidok: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dynamicTypeSize) private var rozmiarTekstu
    @Environment(\.accessibilityReduceMotion) private var bezRuchu

    @ViewBuilder private func scena(_ p: Pogoda?) -> some View {
        let s = m.aktywnaScena
        let warunki = WarunkiSceny.z(stan: p?.ikona ?? "", efekt: p?.efekt, dzien: p?.dzien ?? true,
                                     ustawienia: m.ustawieniaScen[p?.ikona ?? ""], karta: false, bezRuchu: bezRuchu)
        if let svg = s.svg {
            TloSVG(adres: warunki.noc ? (s.svgNoc ?? svg) : svg, przyciemnij: warunki.noc && s.svgNoc == nil)
        } else {
            ScenaPogody(stan: StanPogody.z(ikony: p?.ikona ?? "cloud"),
                        pora: PoraDnia.teraz(dzien: p?.dzien ?? true),
                        scena: m.motyw.scena(miesiac: Calendar.current.component(.month, from: .now)),
                        efekt: p?.efekt, okolicznosciowy: m.motyw.rodzaj != .zwykly,
                        scenaStrony: s.doRysowania, warunki: warunki)
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                let p = m.pogoda
                ZStack(alignment: .topLeading) {
                    scena(p)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(p.map { Pogoda.stopnie($0.temperatura) + "C" } ?? "–").czcionka(52, .heavy)
                        Text(p?.opis ?? "").czcionka(16, .semibold)
                        Text(Napisy.t("app.feelsLike", "Odczuwalna") + " " + (p.map { Pogoda.stopnie($0.odczuwalna) + "C" } ?? "–")).czcionka(13)
                    }
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.45), radius: 8)
                    .padding(18)
                    // przy bardzo dużym tekście plakietka zasłaniałaby temperaturę (i tak jesteśmy na ekranie Pogoda)
                    if !rozmiarTekstu.isAccessibilitySize {
                        Text(Napisy.t("app.weatherLeszno", "Pogoda w Lesznie").uppercased(with: Jezyk.locale()))
                            .czcionka(11, .bold).foregroundStyle(.white)
                            .padding(.horizontal, 9).padding(.vertical, 5)
                            .background(.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 9))
                            .frame(maxWidth: .infinity, alignment: .trailing)
                            .padding(14)
                    }
                }
                .frame(height: 300)
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .accessibilityElement(children: .combine)

                HStack(spacing: 10) {
                    Gwiazda().fill(Color(hex: "#e8c26a")).frame(width: 14, height: 14)
                        .frame(width: 30, height: 30).overlay(Circle().stroke(Color(hex: "#e8c26a"), lineWidth: 1.5))
                    VStack(alignment: .leading, spacing: 1) {
                        Text((Jezyk.polski ? m.dzisiaj?.etykieta : nil) ?? Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide).year().locale(Jezyk.locale())))
                            .czcionka(13, .bold)
                        if let imieniny = m.dzisiaj?.imieniny, !imieniny.isEmpty {
                            Text(Napisy.t("app.nameDays", "Imieniny") + ": " + imieniny.joined(separator: ", ")).czcionka(12).foregroundStyle(Color(hex: "#c9d3e3"))
                        }
                        Text(Napisy.t("app.sunriseSunset", "Wschód {sunrise} · zachód {sunset}")
                                .replacingOccurrences(of: "{sunrise}", with: p?.wschod ?? "–")
                                .replacingOccurrences(of: "{sunset}", with: p?.zachod ?? "–")).czcionka(12).foregroundStyle(Color(hex: "#c9d3e3"))
                    }
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 12).padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(m.motyw.naglowek.first ?? Barwy.granat, in: RoundedRectangle(cornerRadius: 14))

                HStack(spacing: 8) {
                    Pomiar(nazwa: Napisy.t("app.wind", "Wiatr"), wartosc: p?.wiatr.map { String(format: "%.1f km/h %@", $0, p?.kierunek ?? "").replacingOccurrences(of: ".", with: Jezyk.przecinek) } ?? "–")
                    Pomiar(nazwa: Napisy.t("app.humidity", "Wilgotność"), wartosc: p?.wilgotnosc.map { "\(Int($0.rounded()))%" } ?? "–")
                    Pomiar(nazwa: Napisy.t("app.pressure", "Ciśnienie"), wartosc: p?.cisnienie.map { String(format: "%.1f hPa", $0).replacingOccurrences(of: ".", with: Jezyk.przecinek) } ?? "–")
                }

                if let godziny = p?.godziny, !godziny.isEmpty {
                    // kropki w karcie, pod godzinami (Karol 25.11: przy każdej karuzeli wskaźnik przewijania)
                    KaruzelaPozioma(przycinaj: true, odstep: -4, dolnyOdstep: 10) {
                        HStack(spacing: 14) {
                            ForEach(godziny.prefix(24)) { g in
                                VStack(spacing: 4) {
                                    Text(g.czas).czcionka(12, .semibold)
                                    // noc według godziny, jak isNightAt na stronie (wschód i zachód z API)
                                    IkonaRysowana(stan: g.ikona, noc: NocWedlugGodziny.noc(g.czas, wschod: p?.wschod ?? "", zachod: p?.zachod ?? ""))
                                        .frame(width: 28, height: 28).accessibilityHidden(true)
                                    Text(Pogoda.stopnie(g.temp)).czcionka(13, .bold)
                                    if g.opad > 0 { Text("\(g.opad)%").czcionka(11, .semibold).foregroundStyle(Barwy.link) }
                                }
                                .foregroundStyle(Barwy.tekst)
                            }
                        }
                        .padding(14)
                    }
                    .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Barwy.ramka))
                }

                if let dni = p?.dni, !dni.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(Array(dni.enumerated()), id: \.element.id) { i, d in
                            HStack {
                                Text(d.nazwa).czcionka(15, .bold).frame(width: 36, alignment: .leading)
                                IkonaRysowana(stan: d.ikona, noc: false).frame(width: 30, height: 30).accessibilityHidden(true)
                                Text(d.opis).czcionka(13).foregroundStyle(Barwy.slaby).lineLimit(1)
                                Spacer()
                                if d.opad > 0 { Text("\(d.opad)%").czcionka(12, .semibold).foregroundStyle(Barwy.link) }
                                Text(Pogoda.stopnie(d.min)).czcionka(13).foregroundStyle(Barwy.slaby)
                                Text(Pogoda.stopnie(d.max)).czcionka(15, .heavy)
                            }
                            .foregroundStyle(Barwy.tekst)
                            .padding(.vertical, 10)
                            .overlay(alignment: .bottom) { if i < dni.count - 1 { Barwy.ramka.frame(height: 1) } }
                        }
                    }
                    .padding(.horizontal, 14)
                    .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Barwy.ramka))
                }
            }
            .padding(16)
        }
        .refreshable { await m.odswiez(wymus: true) }
        .background(Barwy.tlo)
    }
}

struct Pomiar: View {
    let nazwa: String
    let wartosc: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(nazwa).czcionka(11, .semibold).foregroundStyle(Barwy.slaby)
            Text(wartosc).czcionka(14, .heavy).foregroundStyle(Barwy.tekst).minimumScaleFactor(0.7).lineLimit(1)
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Barwy.ramka))
    }
}

enum StanPogody {
    case slonce, przejasnienia, chmury, deszcz, burza, snieg, mgla, wiatr

    /// Te same ikony co silnik pogody na stronie (resolveState w weather-effects.js).
    static func z(ikony ikona: String) -> StanPogody {
        let i = ikona.lowercased()
        if i.contains("storm") || i.contains("thunder") || i.contains("lightning") { return .burza }
        if i.contains("snow") { return .snieg }
        if i.contains("rain") || i.contains("sleet") || i.contains("hail") || i.contains("shower") || i.contains("sprinkle") { return .deszcz }
        if i.contains("fog") { return .mgla }
        if i.contains("wind") { return .wiatr }
        if i.contains("cloud-sun") || i.contains("partly") || i.contains("cloudy-high") { return .przejasnienia }
        if i.contains("cloud") { return .chmury }
        return .slonce
    }
    var jasne: Bool { self == .slonce || self == .przejasnienia || self == .wiatr }
}

enum PoraDnia {
    case swit, dzien, zmierzch, noc
    /// Jak na stronie: świt 5–8, dzień 8–17, zmierzch 17–20, noc. Stacja może powiedzieć, że już noc.
    static func teraz(dzien: Bool, data: Date = .now) -> PoraDnia {
        if !dzien { return .noc }
        let h = Calendar.current.component(.hour, from: data)
        switch h {
        case 5..<8: return .swit
        case 8..<17: return .dzien
        case 17..<20: return .zmierzch
        default: return .noc
        }
    }
}

/// Animowana scena: niebo, słońce lub księżyc, chmury, opady, mgła, błyskawice i panorama Leszna z Ratuszem.
struct ScenaPogody: View {
    let stan: StanPogody
    let pora: PoraDnia
    let scena: Motyw.Scena
    /// Efekt z serwera (API 1.7.2); nil = scena według ikony, jak dotąd.
    var efekt: EfektPogody? = nil
    /// Aktywny motyw okolicznościowy z panelu: wtedy jego scena, bez nakładki pogody.
    var okolicznosciowy = false
    /// Kafel na Start (tekst na całej karcie): panorama niżej i nie większa niż ok. 1,2 raza, żeby drzewa
    /// i ozdoby stały w pasie pod tekstem; słońce i księżyc w prawym górnym rogu nad datą, fajerwerki
    /// i błyskawica w przerwie między temperaturą a prognozą, chmury i mgła bledsze.
    var kafel = false
    /// Wysokość pasa ziemi w kafelku (punkty): cienki, bo pod grafiką jest belka z datą w kolorze ziemi (KartaPogody).
    static let pasZiemiKafla: CGFloat = 4

    /// Śnieg na ziemi: zima i Święta.
    static func zimowa(_ scena: Motyw.Scena) -> Bool { scena == .zima || scena == .swieta }

    /// Kolor ziemi panoramy, także belki z datą pod kafelkiem.
    static func kolorZiemi(scena: Motyw.Scena, noc: Bool) -> Color {
        zimowa(scena) ? (noc ? Color(hex: "#c9d6e4") : Color(hex: "#eef3f8")) : (noc ? Color(hex: "#0b1020") : Color(hex: "#2a2620"))
    }
    /// Stała chwila animacji (galeria scen do sprawdzania czytelności); nil = scena żyje.
    var czas: Double? = nil
    /// Scena ze strony tvml.pl (ScenyStrony.swift) zamiast dawnej panoramy; nil = panorama według pory roku.
    var scenaStrony: ScenaStrony? = nil
    /// Warunki sceny ze strony (noc, burza, deszcz, mgła, ustawienia z panelu); nil = z danych tej sceny.
    var warunki: WarunkiSceny? = nil
    @Environment(\.accessibilityReduceMotion) private var bezRuchu
    @State private var start = Date().timeIntervalSinceReferenceDate

    var body: some View {
        Group {
            if let czas {
                Canvas { ctx, size in rysuj(ctx: &ctx, size: size, t: czas) }
            } else {
                // „Ogranicz ruch”: jedna nieruchoma klatka sceny, bez błyskawic
                TimelineView(.animation(minimumInterval: 1.0 / 30, paused: bezRuchu)) { kontekst in
                    Canvas { ctx, size in
                        rysuj(ctx: &ctx, size: size, t: kontekst.date.timeIntervalSinceReferenceDate)
                    }
                }
            }
        }
        .accessibilityHidden(true)
    }

    /// Kafel: środek słońca albo księżyca w prawym górnym rogu, nad linią daty (od ok. 28 pkt w dół jest tekst).
    private func srodekCiala(w: CGFloat, h: CGFloat, domyslny: CGPoint) -> CGPoint {
        kafel ? CGPoint(x: w - 40, y: 6) : domyslny
    }
    /// Kafel: chmury i mgła bledsze, bo leżą pod napisem, datą i prognozą.
    private var krycieChmur: Double { kafel ? 0.5 : 1 }

    private var noc: Bool { biezacaPora == .noc }
    private var swiatla: Bool { noc || pora == .zmierzch || scena == .swieta || scena == .sylwester }
    private var zimowo: Bool { scena == .zima || scena == .swieta }

    private func niebo() -> [Color] {
        if scena == .sylwester && noc { return [Color(hex: "#0a0820"), Color(hex: "#1b1640"), Color(hex: "#2b2160")] }
        if noc {
            switch biezacyStan {
            case .slonce, .wiatr: return [Color(hex: "#04091e"), Color(hex: "#101c3d"), Color(hex: "#1c2a52")]
            case .przejasnienia: return [Color(hex: "#060d24"), Color(hex: "#1a2747")]
            case .chmury: return [Color(hex: "#0c1222"), Color(hex: "#252e44")]
            case .deszcz: return [Color(hex: "#070a12"), Color(hex: "#1a2130")]
            case .burza: return [Color(hex: "#05070d"), Color(hex: "#141b2a")]
            case .snieg: return [Color(hex: "#0e1626"), Color(hex: "#2c3b55")]
            case .mgla: return [Color(hex: "#1d2330"), Color(hex: "#3a4252")]
            }
        }
        if biezacyStan.jasne && biezacaPora == .swit { return [Color(hex: "#2b2f63"), Color(hex: "#a2648d"), Color(hex: "#f7a56a")] }
        if biezacyStan.jasne && biezacaPora == .zmierzch { return [Color(hex: "#1f1846"), Color(hex: "#7a3f73"), Color(hex: "#f2774a")] }
        switch biezacyStan {
        case .slonce: return [Color(hex: "#0d5bb5"), Color(hex: "#4f9be6"), Color(hex: "#a9d2f5")]
        case .przejasnienia: return [Color(hex: "#2a6db0"), Color(hex: "#89b6e2")]
        case .chmury: return [Color(hex: "#56657d"), Color(hex: "#9aa8bc")]
        case .deszcz: return [Color(hex: "#34404f"), Color(hex: "#6b7787")]
        case .burza: return [Color(hex: "#1b202b"), Color(hex: "#454e5f")]
        case .snieg: return [Color(hex: "#8197b0"), Color(hex: "#d3dee9")]
        case .mgla: return [Color(hex: "#a7afb8"), Color(hex: "#d9dee3")]
        case .wiatr: return [Color(hex: "#3f6593"), Color(hex: "#9cb9d8")]
        }
    }

    /// Scena ze strony na całej grafice (współrzędne jak na stronie: ułamki szerokości i wysokości), bez kadrowania:
    /// przy kadrowaniu dołu wyższej sceny kamienice i Ratusz wypadały za temperaturą i prognozą (galeria, budowa 54).
    /// Na scenach zwykłych pogoda z serwera jako nakładka (chmury, opady, słońce, księżyc, mgła), na specjalnych
    /// (Leszno, Sylwester, biwak…) bez niej, jak na stronie.
    private func rysujStrony(_ s: ScenaStrony, ctx: inout GraphicsContext, size: CGSize, t: Double) {
        var c = ctx
        let war = warunki ?? WarunkiSceny(noc: noc)
        s.rysuj(&c, size: size, t: t, warunki: war)
        // kafel: śnieg na dole winter-village i winter-christmas przechodzi w belkę z datą (Karol: biały pas jak
        // śnieg, jak w zimie w Lesznie); na stronie ten śnieg jest niebieskawy i przyciemniony nakładką kontrastu
        if kafel && (s == .winterVillage || s == .winterChristmas) {
            let snieg = war.noc ? Color(hex: "#24324a") : Color(hex: "#eef3f8")
            let gora = size.height * 0.84
            ctx.fill(Path(CGRect(x: 0, y: gora, width: size.width, height: size.height - gora)),
                     with: .linearGradient(Gradient(stops: [.init(color: snieg.opacity(0), location: 0),
                                                            .init(color: snieg.opacity(0.9), location: 0.55),
                                                            .init(color: snieg, location: 1)]),
                                           startPoint: CGPoint(x: 0, y: gora), endPoint: CGPoint(x: 0, y: size.height)))
        }
        if !s.specjalna, let e = efekt {
            rysujEfekt(ctx: &ctx, size: size, t: t, e: e, tylkoNakladka: true)
        }
    }

    private func rysuj(ctx: inout GraphicsContext, size: CGSize, t: Double) {
        if let s = scenaStrony { rysujStrony(s, ctx: &ctx, size: size, t: t); return }
        // Panorama Leszna (leszno-panorama) jak na stronie od 25.11 (Karol: „na stronie jest już idealnie”): Rynek
        // z ratuszem ze sceny 11 Listopada w wersji codziennej (ScenaRatusza, codzienny), a na nim pogoda z serwera
        // jako nakładka, bo na stronie Panorama nie jest sceną specjalną (pogoda-efekty.js, scenaSpecjalna).
        // Zastępuje dawny zarys ratusza z kółkiem zegara (siatka 358 × 130) i niebo według ikony.
        var c = ctx
        ScenaRatusza.rysuj(&c, size: size, t: t, warunki: warunki ?? WarunkiSceny(noc: noc), codzienny: true)
        if let e = wgEfektu { rysujEfekt(ctx: &ctx, size: size, t: t, e: e, tylkoNakladka: true) }
    }

    // MARK: efekt z serwera (API 1.7.2), jak na stronie: aktualna pogoda, a nie sama pora roku

    /// Efekt rysowany zamiast dotychczasowej sceny; motyw okolicznościowy (11 Listopada, zima, Święta, Sylwester…)
    /// ma pierwszeństwo i wtedy zostaje jego scena bez nakładki pogody.
    private var wgEfektu: EfektPogody? { okolicznosciowy ? nil : efekt }

    /// Stan nieba (kolory gradientu i panoramy) wyliczony z efektu, a bez efektu z ikony.
    private var biezacyStan: StanPogody {
        guard let e = wgEfektu else { return stan }
        switch e.rodzaj {
        case "clear-day", "clear-night": return .slonce
        case "clouds": return e.natezenie < 0.5 ? .przejasnienia : .chmury
        case "fog": return .mgla
        case "drizzle", "rain", "rain-heavy": return .deszcz
        case "storm": return .burza
        case "snow", "sleet": return .snieg
        case "wind": return .wiatr
        default: return stan
        }
    }

    private var biezacaPora: PoraDnia {
        guard let e = wgEfektu else { return pora }
        switch e.faza {
        case "night": return .noc
        case "dusk": return Calendar.current.component(.hour, from: .now) < 12 ? .swit : .zmierzch
        default: return .dzien
        }
    }

    /// tylkoNakladka: sama pogoda na scenie ze strony (bez nieba i panoramy).
    private func rysujEfekt(ctx: inout GraphicsContext, size: CGSize, t: Double, e: EfektPogody, tylkoNakladka: Bool = false) {
        let w = size.width, h = size.height
        let calosc = Path(CGRect(origin: .zero, size: size))
        let wNocy = e.faza == "night", oZmierzchu = e.faza == "dusk"
        let n = e.natezenie
        if !tylkoNakladka {
            ctx.fill(calosc, with: .linearGradient(Gradient(colors: niebo()), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
        }
        let deszczowo = ["rain", "rain-heavy", "storm"].contains(e.rodzaj)
        if (e.rodzaj == "clouds" && n >= 0.6) || deszczowo {
            ctx.fill(calosc, with: .color(Color(white: 0.5).opacity(0.12)))
        }
        if e.rodzaj == "snow" { ctx.fill(calosc, with: .color(.white.opacity(0.06))) }

        // gwiazdy: przy clear-night, nieliczne przy rzadkich chmurach nocą; nigdy we mgle, w deszczu ani w śniegu
        let rzadkieChmury = e.rodzaj == "clouds" && n < 0.5
        let gwiazdy = e.rodzaj == "clear-night" ? 30 : (rzadkieChmury && wNocy ? 8 : 0)
        for k in 0..<gwiazdy {
            let x = Double((k * 97) % 1000) / 1000 * w
            let y = Double((k * 61) % 1000) / 1000 * h * 0.6
            let czestotliwosc = 0.6 + Double(k % 7) * 0.2
            ctx.opacity = (rzadkieChmury ? 0.5 : 1) * (0.5 + 0.45 * sin(t * czestotliwosc * 2 * .pi + Double(k)))
            ctx.fill(Path(ellipseIn: CGRect(x: x, y: y, width: 2, height: 2)), with: .color(.white))
        }
        ctx.opacity = 1
        // sierp księżyca z poświatą
        if wNocy && (e.rodzaj == "clear-night" || rzadkieChmury) {
            let c = srodekCiala(w: w, h: h, domyslny: CGPoint(x: w * 0.8, y: 46))
            let k: CGFloat = kafel ? 0.6 : 1
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 46 * k, y: c.y - 46 * k, width: 92 * k, height: 92 * k)),
                     with: .radialGradient(Gradient(colors: [Color(hex: "#f4f1de").opacity(0.25), Color(hex: "#f4f1de").opacity(0)]), center: c, startRadius: 10 * k, endRadius: 46 * k))
            let ksiezyc = Path(ellipseIn: CGRect(x: c.x - 20 * k, y: c.y - 20 * k, width: 40 * k, height: 40 * k))
                .subtracting(Path(ellipseIn: CGRect(x: c.x - 8 * k, y: c.y - 26 * k, width: 40 * k, height: 40 * k)))
            ctx.fill(ksiezyc, with: .color(Color(hex: "#f4f1de")))
        }
        // słońce z 8 bladymi promieniami (obrót ~1 na 40 s) i złoty pył
        if !wNocy && (e.rodzaj == "clear-day" || rzadkieChmury) {
            let c = srodekCiala(w: w, h: h, domyslny: CGPoint(x: w * 0.8, y: h * 0.2))
            let kolor = oZmierzchu ? Color(hex: "#ffd2a0") : Color(hex: "#fff3b0")
            let k: CGFloat = kafel ? 0.62 : 1
            var promienie = ctx
            promienie.translateBy(x: c.x, y: c.y)
            promienie.rotate(by: .radians(t / 40 * 2 * .pi))
            for r in 0..<8 {
                let a = Double(r) / 8 * 2 * .pi
                var p = Path()
                p.move(to: .zero)
                p.addLine(to: CGPoint(x: cos(a - 0.08) * 90 * k, y: sin(a - 0.08) * 90 * k))
                p.addLine(to: CGPoint(x: cos(a + 0.08) * 90 * k, y: sin(a + 0.08) * 90 * k))
                p.closeSubpath()
                promienie.fill(p, with: .color(kolor.opacity(kafel ? 0.07 : 0.12)))
            }
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 60 * k, y: c.y - 60 * k, width: 120 * k, height: 120 * k)),
                     with: .radialGradient(Gradient(colors: [kolor.opacity(0.5), kolor.opacity(0)]), center: c, startRadius: 18 * k, endRadius: 60 * k))
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 24 * k, y: c.y - 24 * k, width: 48 * k, height: 48 * k)), with: .color(kolor))
            if e.rodzaj == "clear-day" {
                for k in 0..<7 {
                    let v = 4 + Double(k % 4) * 2
                    let y = h - (Double((k * 89) % 1000) / 1000 * h + t * v).truncatingRemainder(dividingBy: h)
                    let x = Double((k * 151) % 1000) / 1000 * w + sin(t * 0.5 + Double(k)) * 8
                    ctx.fill(Path(ellipseIn: CGRect(x: x, y: y, width: 2.4, height: 2.4)), with: .color(Color(hex: "#ffd76a").opacity(0.7)))
                }
            }
        }
        // chmury: kolory według pory, przy deszczu i burzy ciemne
        let ileChmur: Int
        switch e.rodzaj {
        case "clouds": ileChmur = 3 + Int((4 * n).rounded())
        case "drizzle": ileChmur = 3
        case "rain", "rain-heavy", "storm", "sleet": ileChmur = 5
        case "snow": ileChmur = 4
        case "wind": ileChmur = 2
        default: ileChmur = 0
        }
        let kolorChmur: Color = deszczowo
            ? (wNocy ? Color(red: 58 / 255, green: 66 / 255, blue: 88 / 255) : Color(red: 96 / 255, green: 106 / 255, blue: 128 / 255))
            : (wNocy ? Color(red: 118 / 255, green: 132 / 255, blue: 168 / 255)
                : (oZmierzchu ? Color(red: 244 / 255, green: 210 / 255, blue: 196 / 255) : Color(red: 238 / 255, green: 242 / 255, blue: 248 / 255)))
        for k in 0..<ileChmur {
            let szer = 100 + Double(k % 3) * 30
            let v = (4 + Double(k % 6)) * (e.wietrznie ? 2.5 : 1)
            let x = (Double(k) * 137 + t * v).truncatingRemainder(dividingBy: w + szer * 2) - szer
            let y = 10 + Double((k * 53) % 60)
            ctx.opacity = 0.8 * krycieChmur
            ctx.fill(chmura(CGRect(x: x, y: y, width: szer, height: szer * 0.42)), with: .color(kolorChmur))
        }
        ctx.opacity = 1
        // krople: mżawka, deszcz, ulewa, burza, deszcz ze śniegiem
        let krople: Int, dlugosc: Double, predkosc: Double
        switch e.rodzaj {
        case "drizzle": krople = 18 + Int(24 * n); dlugosc = 6; predkosc = 260
        case "rain": krople = 30 + Int(36 * n); dlugosc = 11; predkosc = 460
        case "rain-heavy": krople = 36 + Int(36 * n); dlugosc = 16; predkosc = 620
        case "storm": krople = 60; dlugosc = 16; predkosc = 620
        case "sleet": krople = 21; dlugosc = 10; predkosc = 420
        default: krople = 0; dlugosc = 0; predkosc = 0
        }
        let skos = e.wietrznie ? 0.35 : 0.12
        let kolorKropli = wNocy ? Color(red: 178 / 255, green: 198 / 255, blue: 235 / 255) : Color(red: 205 / 255, green: 222 / 255, blue: 245 / 255)
        for k in 0..<min(krople, 70) {
            let x0 = Double((k * 151) % 1000) / 1000 * (w + 60) - 30
            let v = predkosc * (0.85 + Double(k % 5) * 0.06)
            let y = (Double((k * 89) % 1000) / 1000 * h + t * v).truncatingRemainder(dividingBy: h + 20) - 10
            let x = x0 + y * skos
            var p = Path()
            p.move(to: CGPoint(x: x, y: y))
            p.addLine(to: CGPoint(x: x + dlugosc * skos, y: y + dlugosc))
            ctx.stroke(p, with: .color(kolorKropli.opacity(0.75)), lineWidth: 1.3)
        }
        // płatki śniegu (1–3,6 pt), kołyszą się, przy wietrze znoszone w prawo
        let platki = e.rodzaj == "snow" ? 24 + Int(36 * n) : (e.rodzaj == "sleet" ? 18 : 0)
        let kolorPlatkow = wNocy ? Color(red: 232 / 255, green: 240 / 255, blue: 1) : .white
        for k in 0..<min(platki, 70) {
            let r = 0.5 + Double(k % 5) * 0.32
            let v = 18 + Double(k % 7) * 5.6
            let y = (Double((k * 89) % 1000) / 1000 * h + t * v).truncatingRemainder(dividingBy: h + 10) - 5
            let dryf = e.wietrznie ? t * 20 : 0
            let x = (Double((k * 151) % 1000) / 1000 * w + sin(t + Double(k)) * 10 + dryf).truncatingRemainder(dividingBy: w)
            ctx.fill(Path(ellipseIn: CGRect(x: x, y: y, width: r * 2, height: r * 2)), with: .color(kolorPlatkow.opacity(0.9)))
        }
        // wiatr: jasne, lekko wygięte smugi
        if e.rodzaj == "wind" {
            for k in 0..<(10 + Int(5 * n)) {
                let v = 160 + Double(k % 8) * 20
                let x = (Double((k * 151) % 1000) / 1000 * w + t * v).truncatingRemainder(dividingBy: w + 120) - 60
                let y = Double((k * 89) % 1000) / 1000 * h * 0.75
                var p = Path()
                p.move(to: CGPoint(x: x, y: y))
                p.addQuadCurve(to: CGPoint(x: x + 50, y: y - 3), control: CGPoint(x: x + 25, y: y + 4))
                ctx.stroke(p, with: .color(.white.opacity(wNocy ? 0.25 : 0.45)), lineWidth: 1.2)
            }
        }
        // jesienne liście (tylko gdy serwer je włączy)
        if e.liscie {
            let barwy = [Color(hex: "#f2b705"), Color(hex: "#e88d14"), Color(hex: "#d9531e"), Color(hex: "#b8291d")]
            // jak na stronie (pogoda-efekty.js): 10 liści, przy wietrze 14, każdy obraca się w swoją stronę
            for k in 0..<(e.rodzaj == "wind" ? 14 : 10) {
                let v = 14 + Double(k % 5) * 4
                let y = (Double((k * 89) % 1000) / 1000 * h + t * v).truncatingRemainder(dividingBy: h + 10) - 5
                let boczny = e.rodzaj == "wind" ? t * (120 + Double(k) * 10) : sin(t * 0.8 + Double(k)) * 18
                let x = (Double((k * 151) % 1000) / 1000 * w + boczny).truncatingRemainder(dividingBy: w + 20)
                var lisc = ctx
                lisc.opacity = wNocy ? 0.55 : 0.85
                lisc.translateBy(x: x, y: y)
                lisc.rotate(by: .radians(t * (0.6 + Double(k % 5) * 0.25) * (k % 2 == 0 ? 1 : -1) + Double(k)))
                lisc.fill(Path(ellipseIn: CGRect(x: -4.4, y: -2.1, width: 8.8, height: 4.2)), with: .color(barwy[k % 4]))
            }
        }
        // mgła: 5 półprzezroczystych pasm na przemian w lewo i w prawo i mleczny dół, bez gwiazd i liści
        if e.rodzaj == "fog" {
            let kolorMgly = wNocy ? Color(red: 168 / 255, green: 180 / 255, blue: 204 / 255)
                : (oZmierzchu ? Color(red: 236 / 255, green: 218 / 255, blue: 214 / 255) : Color(red: 236 / 255, green: 240 / 255, blue: 245 / 255))
            ctx.fill(Path(CGRect(x: 0, y: h * 0.5, width: w, height: h * 0.5)),
                     with: .linearGradient(Gradient(colors: [kolorMgly.opacity(0), kolorMgly.opacity(0.35 * krycieChmur)]),
                                           startPoint: CGPoint(x: 0, y: h * 0.5), endPoint: CGPoint(x: 0, y: h)))
            for k in 0..<5 {
                let v = (5 + Double(k) * 1.5) * (k % 2 == 0 ? 1 : -1)
                let x = (t * v).truncatingRemainder(dividingBy: 120) - 120
                let y = h * (0.22 + Double(k) * 0.14)
                // miękkie pasmo: przezroczyste u góry i u dołu, najgęstsze w środku (bez twardych krawędzi)
                let krycie = (0.16 + Double(k) * 0.035) * krycieChmur
                let wys = 56.0
                ctx.fill(Path(CGRect(x: x, y: y, width: w + 240, height: wys)),
                         with: .linearGradient(Gradient(colors: [kolorMgly.opacity(0), kolorMgly.opacity(krycie), kolorMgly.opacity(0)]),
                                               startPoint: CGPoint(x: 0, y: y), endPoint: CGPoint(x: 0, y: y + wys)))
            }
        }
        if oZmierzchu { ctx.fill(calosc, with: .color(Color(red: 1, green: 150 / 255, blue: 90 / 255).opacity(0.07))) }
        // burza: łagodny błysk całego tła, pierwszy po 6 s, potem co 14 s, gaśnie przez 0,6 s; bez błysków przy „Ogranicz ruch”
        if e.rodzaj == "storm" && !bezRuchu {
            let odStartu = t - start
            if odStartu > 6 {
                let x = (odStartu - 6).truncatingRemainder(dividingBy: 14)
                if x < 0.6 { ctx.fill(calosc, with: .color(.white.opacity(0.2 * (1 - x / 0.6)))) }
            }
        }
    }

    private func chmura(_ r: CGRect) -> Path {
        var p = Path()
        p.addEllipse(in: CGRect(x: r.minX, y: r.minY + r.height * 0.35, width: r.width * 0.5, height: r.height * 0.65))
        p.addEllipse(in: CGRect(x: r.minX + r.width * 0.22, y: r.minY, width: r.width * 0.5, height: r.height * 0.9))
        p.addEllipse(in: CGRect(x: r.minX + r.width * 0.48, y: r.minY + r.height * 0.25, width: r.width * 0.52, height: r.height * 0.75))
        return p
    }
}
