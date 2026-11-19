import SwiftUI

/// Pogoda w Lesznie ze stacji TVML. Scena zmienia się jak na stronie: stan pogody, pora dnia, pora roku
/// i wersja z panelu (zima, święta, Sylwester, 11 Listopada).
struct PogodaWidok: View {
    @EnvironmentObject private var m: Magazyn

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                let p = m.pogoda
                ZStack(alignment: .topLeading) {
                    ScenaPogody(stan: StanPogody.z(ikony: p?.ikona ?? "cloud"),
                                pora: PoraDnia.teraz(dzien: p?.dzien ?? true),
                                scena: m.motyw.scena(miesiac: Calendar.current.component(.month, from: .now)))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(p.map { Pogoda.stopnie($0.temperatura) + "C" } ?? "–").font(.system(size: 52, weight: .heavy))
                        Text(p?.opis ?? "").font(.system(size: 16, weight: .semibold))
                        Text("Odczuwalna " + (p.map { Pogoda.stopnie($0.odczuwalna) + "C" } ?? "–")).font(.system(size: 13))
                    }
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.45), radius: 8)
                    .padding(18)
                    Text("POGODA W LESZNIE")
                        .font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 9).padding(.vertical, 5)
                        .background(.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 9))
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .padding(14)
                }
                .frame(height: 300)
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .accessibilityElement(children: .combine)

                HStack(spacing: 10) {
                    Gwiazda().fill(Color(hex: "#e8c26a")).frame(width: 14, height: 14)
                        .frame(width: 30, height: 30).overlay(Circle().stroke(Color(hex: "#e8c26a"), lineWidth: 1.5))
                    VStack(alignment: .leading, spacing: 1) {
                        Text(m.dzisiaj?.etykieta ?? Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide).year().locale(Locale(identifier: "pl_PL"))))
                            .font(.system(size: 13, weight: .bold))
                        if let imieniny = m.dzisiaj?.imieniny, !imieniny.isEmpty {
                            Text("Imieniny: \(imieniny.joined(separator: ", "))").font(.system(size: 12)).foregroundStyle(Color(hex: "#c9d3e3"))
                        }
                        Text("Wschód \(p?.wschod ?? "–") · zachód \(p?.zachod ?? "–")").font(.system(size: 12)).foregroundStyle(Color(hex: "#c9d3e3"))
                    }
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 12).padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(m.motyw.naglowek.first ?? Barwy.granat, in: RoundedRectangle(cornerRadius: 14))

                HStack(spacing: 8) {
                    Pomiar(nazwa: "Wiatr", wartosc: p?.wiatr.map { String(format: "%.1f km/h %@", $0, p?.kierunek ?? "").replacingOccurrences(of: ".", with: ",") } ?? "–")
                    Pomiar(nazwa: "Wilgotność", wartosc: p?.wilgotnosc.map { "\(Int($0.rounded()))%" } ?? "–")
                    Pomiar(nazwa: "Ciśnienie", wartosc: p?.cisnienie.map { String(format: "%.1f hPa", $0).replacingOccurrences(of: ".", with: ",") } ?? "–")
                }

                if let godziny = p?.godziny, !godziny.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(godziny.prefix(24)) { g in
                                VStack(spacing: 4) {
                                    Text(g.czas).font(.system(size: 12, weight: .semibold))
                                    Image(systemName: ikonaPogody(g.ikona)).symbolRenderingMode(.multicolor).font(.system(size: 20))
                                    Text(Pogoda.stopnie(g.temp)).font(.system(size: 13, weight: .bold))
                                    if g.opad > 0 { Text("\(g.opad)%").font(.system(size: 11, weight: .semibold)).foregroundStyle(Barwy.link) }
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
                                Text(d.nazwa).font(.system(size: 15, weight: .bold)).frame(width: 36, alignment: .leading)
                                Image(systemName: ikonaPogody(d.ikona)).symbolRenderingMode(.multicolor).frame(width: 30)
                                Text(d.opis).font(.system(size: 13)).foregroundStyle(Barwy.slaby).lineLimit(1)
                                Spacer()
                                if d.opad > 0 { Text("\(d.opad)%").font(.system(size: 12, weight: .semibold)).foregroundStyle(Barwy.link) }
                                Text(Pogoda.stopnie(d.min)).font(.system(size: 13)).foregroundStyle(Barwy.slaby)
                                Text(Pogoda.stopnie(d.max)).font(.system(size: 15, weight: .heavy))
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
            Text(nazwa).font(.system(size: 11, weight: .semibold)).foregroundStyle(Barwy.slaby)
            Text(wartosc).font(.system(size: 14, weight: .heavy)).foregroundStyle(Barwy.tekst).minimumScaleFactor(0.7).lineLimit(1)
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

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { kontekst in
            Canvas { ctx, size in
                rysuj(ctx: &ctx, size: size, t: kontekst.date.timeIntervalSinceReferenceDate)
            }
        }
        .accessibilityHidden(true)
    }

    private var noc: Bool { pora == .noc }
    private var swiatla: Bool { noc || pora == .zmierzch || scena == .swieta || scena == .sylwester }
    private var zimowo: Bool { scena == .zima || scena == .swieta }

    private func niebo() -> [Color] {
        if scena == .sylwester && noc { return [Color(hex: "#0a0820"), Color(hex: "#1b1640"), Color(hex: "#2b2160")] }
        if noc {
            switch stan {
            case .slonce, .wiatr: return [Color(hex: "#04091e"), Color(hex: "#101c3d"), Color(hex: "#1c2a52")]
            case .przejasnienia: return [Color(hex: "#060d24"), Color(hex: "#1a2747")]
            case .chmury: return [Color(hex: "#0c1222"), Color(hex: "#252e44")]
            case .deszcz: return [Color(hex: "#070a12"), Color(hex: "#1a2130")]
            case .burza: return [Color(hex: "#05070d"), Color(hex: "#141b2a")]
            case .snieg: return [Color(hex: "#0e1626"), Color(hex: "#2c3b55")]
            case .mgla: return [Color(hex: "#1d2330"), Color(hex: "#3a4252")]
            }
        }
        if stan.jasne && pora == .swit { return [Color(hex: "#2b2f63"), Color(hex: "#a2648d"), Color(hex: "#f7a56a")] }
        if stan.jasne && pora == .zmierzch { return [Color(hex: "#1f1846"), Color(hex: "#7a3f73"), Color(hex: "#f2774a")] }
        switch stan {
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

    private func rysuj(ctx: inout GraphicsContext, size: CGSize, t: Double) {
        let w = size.width, h = size.height
        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(Gradient(colors: niebo()), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))

        // gwiazdy
        if noc && (stan.jasne || scena == .sylwester) {
            for i in 0..<45 {
                let x = Double((i * 97) % 1000) / 1000 * w
                let y = Double((i * 61) % 1000) / 1000 * h * 0.55
                ctx.opacity = 0.45 + 0.45 * sin(t * 1.6 + Double(i))
                ctx.fill(Path(ellipseIn: CGRect(x: x, y: y, width: 2, height: 2)), with: .color(.white))
            }
            ctx.opacity = 1
        }
        // słońce albo księżyc
        if !noc && stan.jasne {
            let wysoko = pora == .dzien
            let c = CGPoint(x: w * 0.78, y: wysoko ? h * 0.18 : h * 0.45)
            let kolor = wysoko ? Color(hex: "#fff3b0") : Color(hex: "#ffb36b")
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 70, y: c.y - 70, width: 140, height: 140)),
                     with: .radialGradient(Gradient(colors: [kolor.opacity(0.55), kolor.opacity(0)]), center: c, startRadius: 20, endRadius: 70))
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 26, y: c.y - 26, width: 52, height: 52)), with: .color(kolor))
        } else if noc && stan.jasne {
            var ksiezyc = Path()
            ksiezyc.addEllipse(in: CGRect(x: w * 0.74, y: 26, width: 40, height: 40))
            ksiezyc.addEllipse(in: CGRect(x: w * 0.74 + 12, y: 20, width: 40, height: 40))
            ctx.fill(ksiezyc, with: .color(Color(hex: "#f4f1de")), style: FillStyle(eoFill: true))
        }
        // reflektory i fajerwerki
        if (scena == .sylwester || scena == .listopad) && swiatla {
            let kolory: [Color] = scena == .listopad ? [.white, Color(hex: "#dc143c")] : [Color(hex: "#e8c26a"), Color(hex: "#e8c26a")]
            for (i, kolor) in kolory.enumerated() {
                let kat = sin(t * 0.6 + Double(i) * 2) * 0.25 + (i == 0 ? -0.35 : 0.35)
                let baza = CGPoint(x: w * 0.52, y: h * 0.84)
                var snop = Path()
                snop.move(to: baza)
                snop.addLine(to: CGPoint(x: baza.x + CGFloat(sin(kat - 0.06)) * h, y: baza.y - CGFloat(cos(kat - 0.06)) * h))
                snop.addLine(to: CGPoint(x: baza.x + CGFloat(sin(kat + 0.06)) * h, y: baza.y - CGFloat(cos(kat + 0.06)) * h))
                snop.closeSubpath()
                ctx.fill(snop, with: .color(kolor.opacity(0.18)))
            }
        }
        if scena == .sylwester && swiatla {
            let barwy = [Color(hex: "#e8c26a"), Color(hex: "#f472b6"), Color(hex: "#7dd3fc")]
            for i in 0..<3 {
                let okres = 2.6 + Double(i) * 0.7
                let faza = (t + Double(i) * 0.9).truncatingRemainder(dividingBy: okres) / okres
                let c = CGPoint(x: w * [0.2, 0.55, 0.82][i], y: h * [0.18, 0.1, 0.22][i])
                let r = 6 + faza * 34
                ctx.opacity = 1 - faza
                for k in 0..<12 {
                    let a = Double(k) / 12 * 2 * .pi
                    var p = Path()
                    p.move(to: CGPoint(x: c.x + CGFloat(cos(a)) * r * 0.45, y: c.y + CGFloat(sin(a)) * r * 0.45))
                    p.addLine(to: CGPoint(x: c.x + CGFloat(cos(a)) * r, y: c.y + CGFloat(sin(a)) * r))
                    ctx.stroke(p, with: .color(barwy[i]), lineWidth: 1.6)
                }
            }
            ctx.opacity = 1
        }
        // chmury
        let ileChmur: Int = { switch stan { case .slonce: return 1; case .przejasnienia, .wiatr: return 3; case .chmury, .deszcz: return 6; case .burza: return 7; case .snieg: return 5; case .mgla: return 0 } }()
        let kolorChmur: Color = noc ? Color(hex: "#2a3550") : ([.deszcz, .burza].contains(stan) ? Color(hex: "#5f6b7d") : (stan == .chmury ? Color(hex: "#cdd5e0") : .white))
        for i in 0..<ileChmur {
            let szer = 100 + Double(i % 3) * 30
            let szybkosc = (stan == .wiatr ? 26 : 8) + Double(i) * 1.5
            let x = (Double(i) * 137 + t * szybkosc).truncatingRemainder(dividingBy: w + szer * 2) - szer
            let y = 12 + Double((i * 53) % 70)
            ctx.opacity = 0.75
            ctx.fill(chmura(CGRect(x: x, y: y, width: szer, height: szer * 0.42)), with: .color(kolorChmur))
        }
        ctx.opacity = 1
        // błyskawica
        if stan == .burza {
            let blysk = sin(t * 0.9) > 0.97 || sin(t * 1.7 + 1) > 0.985
            if blysk {
                ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.white.opacity(0.25)))
                var b = Path()
                let x0 = w * 0.3
                b.move(to: CGPoint(x: x0 + 20, y: h * 0.18)); b.addLine(to: CGPoint(x: x0, y: h * 0.42)); b.addLine(to: CGPoint(x: x0 + 14, y: h * 0.42))
                b.addLine(to: CGPoint(x: x0 - 6, y: h * 0.7)); b.addLine(to: CGPoint(x: x0 + 28, y: h * 0.36)); b.addLine(to: CGPoint(x: x0 + 14, y: h * 0.36)); b.closeSubpath()
                ctx.fill(b, with: .color(Color(hex: "#fde68a")))
            }
        }
        // deszcz
        if stan == .deszcz || stan == .burza {
            let ile = stan == .burza ? 80 : 55
            for i in 0..<ile {
                let x0 = Double((i * 151) % 1000) / 1000 * w
                let y = (Double((i * 89) % 1000) / 1000 * h + t * 420).truncatingRemainder(dividingBy: h + 20) - 10
                var kropla = Path()
                kropla.move(to: CGPoint(x: x0 + y * 0.12, y: y)); kropla.addLine(to: CGPoint(x: x0 + y * 0.12 + 3, y: y + 13))
                ctx.stroke(kropla, with: .color(Color(red: 0.84, green: 0.9, blue: 0.98).opacity(0.7)), lineWidth: 1.4)
            }
        }
        // śnieg
        let platki = stan == .snieg ? 70 : (zimowo && ![.deszcz, .burza].contains(stan) ? 28 : 0)
        for i in 0..<platki {
            let r = 1.5 + Double(i % 3)
            let y = (Double((i * 89) % 1000) / 1000 * h + t * (22 + r * 6)).truncatingRemainder(dividingBy: h + 10) - 5
            let x = Double((i * 151) % 1000) / 1000 * w + sin(t + Double(i)) * 10
            ctx.fill(Path(ellipseIn: CGRect(x: x, y: y, width: r * 2, height: r * 2)), with: .color(.white.opacity(0.9)))
        }
        // liście na wietrze
        if stan == .wiatr && scena == .jesien {
            let barwy = [Color(hex: "#ea580c"), Color(hex: "#d97706"), Color(hex: "#b45309")]
            for i in 0..<14 {
                let x = (Double((i * 151) % 1000) / 1000 * w + t * 90).truncatingRemainder(dividingBy: w + 20) - 10
                let y = Double((i * 89) % 1000) / 1000 * h * 0.7 + sin(t * 2 + Double(i)) * 14
                var lisc = ctx
                lisc.translateBy(x: x, y: y)
                lisc.rotate(by: .radians(t * 3 + Double(i)))
                lisc.fill(Path(ellipseIn: CGRect(x: -5, y: -2.5, width: 10, height: 5)), with: .color(barwy[i % 3]))
            }
        }
        panorama(ctx: &ctx, w: w, h: h)
        // mgła
        if stan == .mgla {
            for i in 0..<3 {
                let y = h * (0.4 + Double(i) * 0.18)
                let x = sin(t * 0.2 + Double(i)) * 30 - 40
                ctx.fill(Path(CGRect(x: x, y: y, width: w + 80, height: 46)), with: .color(Color(hex: "#ebeff3").opacity(0.45 + Double(i) * 0.1)))
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

    /// Panorama Leszna z Ratuszem (siatka 358 × 130 jak w projekcie), z drzewami i ozdobami pory roku lub wersji.
    private func panorama(ctx: inout GraphicsContext, w: CGFloat, h: CGFloat) {
        let s = w / 358
        let gora = h - 130 * s
        func r(_ x: CGFloat, _ y: CGFloat, _ sz: CGFloat, _ wy: CGFloat) -> CGRect { CGRect(x: x * s, y: gora + y * s, width: sz * s, height: wy * s) }
        func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: gora + y * s) }
        let miasto = noc ? Color(hex: "#0a1226") : Color(hex: "#1c2b45")
        let ratusz = noc ? Color(hex: "#101a33") : Color(hex: "#223456")
        let ziemia = zimowo ? (noc ? Color(hex: "#c9d6e4") : Color(hex: "#eef3f8")) : (noc ? Color(hex: "#0b1020") : Color(hex: "#2a2620"))
        ctx.fill(Path(r(0, 108, 358, 22)), with: .color(ziemia))
        var domy = Path()
        for (x, y, sz) in [(0.0, 82.0, 46.0), (50, 76, 24), (106, 80, 26), (296, 84, 26)] as [(CGFloat, CGFloat, CGFloat)] {
            domy.addRect(r(x, y, sz, 108 - y))
        }
        for (x, y, sz) in [(78.0, 86.0, 24.0), (240, 78, 50), (326, 74, 32)] as [(CGFloat, CGFloat, CGFloat)] {
            domy.addRect(r(x, y, sz, 108 - y))
            domy.move(to: pt(x, y)); domy.addLine(to: pt(x + sz / 2, y - 12)); domy.addLine(to: pt(x + sz, y)); domy.closeSubpath()
        }
        ctx.fill(domy, with: .color(miasto))
        var rt = Path()
        rt.addRect(r(138, 70, 84, 38))
        rt.move(to: pt(142, 70)); rt.addLine(to: pt(146, 62)); rt.addLine(to: pt(214, 62)); rt.addLine(to: pt(218, 70)); rt.closeSubpath()
        rt.addRect(r(178, 34, 16, 28))
        rt.move(to: pt(174, 36)); rt.addQuadCurve(to: pt(186, 20), control: pt(176, 26)); rt.addQuadCurve(to: pt(198, 36), control: pt(196, 26)); rt.closeSubpath()
        rt.addRect(r(185, 6, 2, 14))
        ctx.fill(rt, with: .color(ratusz))
        ctx.fill(Path(ellipseIn: r(181.5, 40.5, 9, 9)), with: .color(swiatla ? Color(hex: "#fde68a") : Color(hex: "#e5e7eb")))
        if swiatla {
            var okna = Path()
            for (x, y) in [(146.0, 78.0), (158, 78), (170, 78), (196, 78), (208, 78), (220, 78), (146, 92), (158, 92), (208, 92), (220, 92), (56, 84), (64, 84), (112, 88), (122, 88), (246, 86), (302, 90), (334, 84)] as [(CGFloat, CGFloat)] {
                okna.addRect(r(x, y, 5, 7))
            }
            ctx.fill(okna, with: .color(Color(hex: "#fcd34d").opacity(0.95)))
        }
        switch scena {
        case .jesien, .listopad, .lato, .wiosna:
            let barwy: [String] = scena == .wiosna || scena == .lato ? ["#15803d", "#16a34a", "#166534"] : ["#c2410c", "#d97706", "#b45309", "#ea580c"]
            for (i, (x, y, rr)) in ([(22.0, 104.0, 12.0), (44, 108, 9), (118, 106, 11), (252, 105, 12), (286, 108, 9), (330, 104, 12)] as [(CGFloat, CGFloat, CGFloat)]).enumerated() {
                ctx.fill(Path(ellipseIn: r(x - rr, y - rr, rr * 2, rr * 2)), with: .color(Color(hex: barwy[i % barwy.count])))
            }
        case .zima, .swieta, .sylwester:
            var choinki = Path(), czapy = Path()
            for (x, y) in [(22.0, 84.0), (118, 86), (254, 84), (332, 84)] as [(CGFloat, CGFloat)] {
                choinki.move(to: pt(x, y)); choinki.addLine(to: pt(x + 12, y + 24)); choinki.addLine(to: pt(x - 12, y + 24)); choinki.closeSubpath()
                czapy.move(to: pt(x, y)); czapy.addLine(to: pt(x + 5, y + 10)); czapy.addLine(to: pt(x - 5, y + 10)); czapy.closeSubpath()
            }
            ctx.fill(choinki, with: .color(Color(hex: "#1f4f43")))
            if scena != .sylwester {
                czapy.addRect(r(138, 68, 84, 4))
                ctx.fill(czapy, with: .color(Color(hex: "#f1f6fb")))
            }
        }
        if scena == .swieta {
            var drzewko = Path()
            drzewko.move(to: pt(96, 40)); drzewko.addLine(to: pt(122, 108)); drzewko.addLine(to: pt(70, 108)); drzewko.closeSubpath()
            ctx.fill(drzewko, with: .color(Color(hex: "#14532d")))
            ctx.fill(Gwiazda().path(in: r(88, 26, 16, 16)), with: .color(Color(hex: "#e0b45a")))
            for (x, y) in [(90.0, 62.0), (102, 70), (86, 84), (106, 90), (96, 98)] as [(CGFloat, CGFloat)] {
                ctx.fill(Path(ellipseIn: r(x - 2, y - 2, 4, 4)), with: .color(Color(hex: "#fbbf24")))
            }
        }
        if scena == .sylwester {
            ctx.draw(Text("2027").font(.system(size: 30 * s, weight: .heavy)).foregroundColor(Color(hex: "#e8c26a")), at: pt(86, 92))
        }
        if scena == .listopad {
            for (x, y) in [(186.0, 6.0), (150, 48), (218, 48)] as [(CGFloat, CGFloat)] {
                ctx.fill(Path(r(x, y, 12, 3)), with: .color(.white))
                ctx.fill(Path(r(x, y + 3, 12, 3)), with: .color(Color(hex: "#dc143c")))
            }
        }
    }
}
