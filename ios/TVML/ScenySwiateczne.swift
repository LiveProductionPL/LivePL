import SwiftUI

/// Ratusz w scenach Leszna (zima, święta, Sylwester, Nowy Rok, 11 Listopada) na szerokiej karcie aplikacji (ok. 1,85:1):
/// bliżej i większy niż na prawie kwadratowej karcie strony, żeby wyglądał jak na tvml.pl
/// (Karol 24.11: „Raz ratusz może być bliżej raz dalej tak by pasowało graficznie”, „popraw wygląd ratusza”).
/// Rysunek ratusza jest 1:1 ze strony; zmienia się tylko jego skala i linia podstawy. Kamienice i postacie bez zmian.
enum PlanRatusza {
    static func szeroka(_ w: CGFloat, _ h: CGFloat) -> Bool { h > 0 && w / h >= 1.5 }
    /// mnożnik skali ratusza (na stronie 1)
    static func skala(_ w: CGFloat, _ h: CGFloat) -> CGFloat { szeroka(w, h) ? 1.3 : 1 }
    /// linia podstawy ratusza (na stronie h * 0.667); niżej = bliżej, a iglica dalej mieści się w karcie
    static func podstawa(_ w: CGFloat, _ h: CGFloat) -> CGFloat { szeroka(w, h) ? h * 0.76 : h * 0.667 }
}

/// Sezonowe sceny Leszna (weather-effects.js 3047-3811): zima, Mikołajki, Wigilia, Święta, Sylwester, Nowy Rok.
enum ScenySwiateczne {
    /// scena: .zima, .mikolajki, .wigilia, .swieta, .sylwester albo .nowyRok (dla innych nic nie rysuje)
    static func rysuj(_ scena: ScenaStrony, _ ctx: inout GraphicsContext, size: CGSize, t: Double, warunki: WarunkiSceny) {
        let obslugiwane: [ScenaStrony] = [.zima, .mikolajki, .wigilia, .swieta, .sylwester, .nowyRok]
        guard obslugiwane.contains(scena) else { return }
        guard size.width > 1, size.height > 1 else { return }
        // Ograniczony ruch: jedna stała klatka (jak andrzejki na stronie, t = 2.2)
        let czas: Double = warunki.bezRuchu ? 2.2 : t
        let u = uklad(scena, size: size, t: czas, warunki: warunki)
        var g = ctx
        g.clip(to: Path(CGRect(origin: .zero, size: size)))

        // Kolejność warstw jak w JS
        niebo(g, u)
        if u.noc { gwiazdy(g, u) }
        if u.scena == .wigilia { gwiazdaBetlejemska(g, u) }
        if u.nye { fajerwerki(g, u) }
        plyta(g, u)
        kamienice(g, u)
        if u.scena == .mikolajki || u.scena == .swieta { girlandy(g, u) }
        if u.noc { reflektory(g, u) }
        let kr = koloryRatusza(u)
        ratuszKorpus(g, u, kr)
        ratuszWieza(g, u, kr)
        // poduszki pod kolumnami rysuje karta (SwiftUI), nie scena
        pierwszyPlan(g, u)
        if u.scena != .sylwester { platki(g, u) }
    }
}

// MARK: - Typy pomocnicze

fileprivate extension ScenySwiateczne {
    /// Wspólne wymiary i stan klatki
    struct Uklad {
        let scena: ScenaStrony
        let w: CGFloat
        let h: CGFloat
        let t: CGFloat
        let noc: Bool
        let karta: Bool
        let scH: CGFloat
        let scR: CGFloat
        /// skala ratusza (scR razy PlanRatusza.skala)
        let rR: CGFloat
        let yRynek: CGFloat
        let yBase: CGFloat
        let cxR: CGFloat
        let yg: CGFloat
        let fgX: CGFloat
        let rok: Int
        let miesiac: Int
        let godzina: Int
        let minuta: Int
        let sekunda: Int

        var nye: Bool { scena == .sylwester || scena == .nowyRok }
        var gapL: CGFloat { cxR - 43 * rR }
        var gapR: CGFloat { cxR + 43 * rR }
        /// Lokalny układ ratusza: X(dx), Y(dy) z osią y w górę
        func X(_ dx: CGFloat) -> CGFloat { cxR + dx * rR }
        func Y(_ dy: CGFloat) -> CGFloat { yBase - dy * rR }
        func L(_ dx: CGFloat, _ dy: CGFloat) -> CGPoint { CGPoint(x: X(dx), y: Y(dy)) }
    }

    /// Lokalny układ postaci (SX/SY z JS); ky = -1 gdy oś y w górę
    struct Lok {
        let cx: CGFloat
        let cy: CGFloat
        let sc: CGFloat
        let ky: CGFloat
        func x(_ d: CGFloat) -> CGFloat { cx + d * sc }
        func y(_ d: CGFloat) -> CGFloat { cy + ky * d * sc }
        func p(_ dx: CGFloat, _ dy: CGFloat) -> CGPoint { CGPoint(x: x(dx), y: y(dy)) }
    }

    struct KoloryRatusza {
        let cokol: Color
        let sciana: Color
        let cien: Color
        let pilaster: Color
        let okno: Color
        let helm: Color
    }

    static func uklad(_ scena: ScenaStrony, size: CGSize, t: Double, warunki: WarunkiSceny) -> Uklad {
        let w: CGFloat = size.width
        let h: CGFloat = size.height
        let scH: CGFloat = h / 276
        let karta: Bool = warunki.karta
        // karta Startu: postacie i instalacja wyżej, nad kolumnami i plakietką z datą (Karol 24.11, test Androida 16)
        let ygKarta: CGFloat = h * 0.79
        let yg: CGFloat = karta ? ygKarta : h * 0.86
        let waska: Bool = w < 560
        let cxR: CGFloat = waska ? w * 0.666 : w * 0.68
        let fgX: CGFloat = waska ? w * 0.332 : w * 0.24
        // Czas lokalny urządzenia (zegar ratusza, rok na napisie)
        let dc = Calendar.current.dateComponents([.year, .month, .hour, .minute, .second], from: Date())
        return Uklad(
            scena: scena, w: w, h: h, t: CGFloat(t), noc: warunki.noc, karta: karta,
            scH: scH, scR: scH * 0.96, rR: scH * 0.96 * PlanRatusza.skala(w, h), yRynek: h * 0.645,
            yBase: PlanRatusza.podstawa(w, h),
            cxR: cxR, yg: yg, fgX: fgX,
            rok: dc.year ?? 2026, miesiac: dc.month ?? 12,
            godzina: dc.hour ?? 0, minuta: dc.minute ?? 0, sekunda: dc.second ?? 0
        )
    }
}

// MARK: - Kolory, ścieżki, wypełnienia

fileprivate extension ScenySwiateczne {
    /// Kolor z 0xRRGGBB
    static func hx(_ v: Int) -> Color {
        let r: Double = Double((v >> 16) & 0xff) / 255
        let gg: Double = Double((v >> 8) & 0xff) / 255
        let b: Double = Double(v & 0xff) / 255
        return Color(red: r, green: gg, blue: b)
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        let aa: Double = min(1, max(0, a))
        return Color(red: r / 255, green: g / 255, blue: b / 255, opacity: aa)
    }

    static func st(_ v: Int, _ l: CGFloat) -> Gradient.Stop {
        Gradient.Stop(color: hx(v), location: l)
    }

    static func stC(_ c: Color, _ l: CGFloat) -> Gradient.Stop {
        Gradient.Stop(color: c, location: l)
    }

    static func kolo(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path {
        let rr: CGFloat = max(0, r)
        return Path(ellipseIn: CGRect(x: x - rr, y: y - rr, width: rr * 2, height: rr * 2))
    }

    static func elipsa(_ x: CGFloat, _ y: CGFloat, _ rx: CGFloat, _ ry: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: x - rx, y: y - ry, width: rx * 2, height: ry * 2))
    }

    static func wielokat(_ pkt: [CGPoint]) -> Path {
        var p = Path()
        guard let pierwszy = pkt.first else { return p }
        p.move(to: pierwszy)
        for q in pkt.dropFirst() { p.addLine(to: q) }
        p.closeSubpath()
        return p
    }

    static func lamana(_ pkt: [CGPoint]) -> Path {
        var p = Path()
        guard let pierwszy = pkt.first else { return p }
        p.move(to: pierwszy)
        for q in pkt.dropFirst() { p.addLine(to: q) }
        return p
    }

    static func linia(_ a: CGPoint, _ b: CGPoint) -> Path {
        var p = Path()
        p.move(to: a)
        p.addLine(to: b)
        return p
    }

    static func prost(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> Path {
        Path(CGRect(x: x, y: y, width: w, height: h))
    }

    /// Zaokrąglony prostokąt (_rrPath); promień przycięty do połowy boku
    static func zaokr(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ r: CGFloat) -> Path {
        guard w > 0, h > 0 else { return Path() }
        let polowa: CGFloat = min(w, h) * 0.5
        let rr: CGFloat = max(0, min(r, polowa))
        return Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: rr, style: .circular)
    }

    /// Łuk elipsy jak ctx.ellipse(cx, cy, rx, ry, 0, a0, a1)
    static func lukElipsy(_ cx: CGFloat, _ cy: CGFloat, _ rx: CGFloat, _ ry: CGFloat, _ a0: CGFloat, _ a1: CGFloat, _ n: Int) -> Path {
        var p = Path()
        let kroki: Int = max(1, n)
        for k in 0...kroki {
            let f: CGFloat = CGFloat(k) / CGFloat(kroki)
            let a: CGFloat = a0 + (a1 - a0) * f
            let pt = CGPoint(x: cx + rx * cos(a), y: cy + ry * sin(a))
            if k == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        return p
    }

    static func wypelnij(_ g: GraphicsContext, _ p: Path, _ c: Color) {
        g.fill(p, with: .color(c))
    }

    static func obrys(_ g: GraphicsContext, _ p: Path, _ c: Color, _ lw: CGFloat) {
        g.stroke(p, with: .color(c), style: StrokeStyle(lineWidth: lw, lineCap: .round))
    }

    static func wypelnijLiniowo(_ g: GraphicsContext, _ p: Path, _ stopy: [Gradient.Stop], _ a: CGPoint, _ b: CGPoint) {
        g.fill(p, with: .linearGradient(Gradient(stops: stopy), startPoint: a, endPoint: b))
    }

    static func wypelnijRadialnie(_ g: GraphicsContext, _ p: Path, _ stopy: [Gradient.Stop], _ c: CGPoint, _ r0: CGFloat, _ r1: CGFloat) {
        g.fill(p, with: .radialGradient(Gradient(stops: stopy), center: c, startRadius: r0, endRadius: r1))
    }

    /// Okrągła poświata z gradientem radialnym (r0 → r1)
    static func poswiata(_ g: GraphicsContext, _ c: CGPoint, _ r0: CGFloat, _ r1: CGFloat, _ stopy: [Gradient.Stop]) {
        guard r1 > 0.5 else { return }
        wypelnijRadialnie(g, kolo(c.x, c.y, r1), stopy, c, r0, r1)
    }

    /// Dodatnie modulo (JS % zachowuje znak)
    static func fmodP(_ a: CGFloat, _ b: CGFloat) -> CGFloat {
        guard b > 0 else { return 0 }
        let r: CGFloat = a.truncatingRemainder(dividingBy: b)
        return r < 0 ? r + b : r
    }

    static func pw(_ a: CGFloat, _ b: CGFloat) -> CGFloat {
        let v: Double = pow(Double(a), Double(b))
        return CGFloat(v)
    }

    /// drawStar8: 4 cienkie romby + biała kropka
    static func gwiazda8(_ g: GraphicsContext, _ sx: CGFloat, _ sy: CGFloat, _ rLong: CGFloat, _ rShort: CGFloat, _ kolor: Color, _ puls: CGFloat) {
        let rl: CGFloat = rLong * puls
        let rs: CGFloat = rShort * puls
        let pol: CGFloat = rs * 0.22
        let ramiona: [(CGFloat, CGFloat)] = [
            (0, rl),
            (CGFloat.pi * 0.5, rl * 1.12),
            (CGFloat.pi * 0.25, rs),
            (CGFloat.pi * 0.75, rs)
        ]
        for (ang, dl) in ramiona {
            let dx: CGFloat = cos(ang) * dl
            let dy: CGFloat = sin(ang) * dl
            let px: CGFloat = -sin(ang) * pol
            let py: CGFloat = cos(ang) * pol
            let romb = wielokat([
                CGPoint(x: sx + dx, y: sy + dy),
                CGPoint(x: sx + px, y: sy + py),
                CGPoint(x: sx - dx, y: sy - dy),
                CGPoint(x: sx - px, y: sy - py)
            ])
            wypelnij(g, romb, kolor)
        }
        let rc: CGFloat = max(1.2, rs * 0.28)
        wypelnij(g, kolo(sx, sy, rc), Color.white)
    }
}

// MARK: - Niebo, gwiazdy, fajerwerki

fileprivate extension ScenySwiateczne {
    static func niebo(_ g: GraphicsContext, _ u: Uklad) {
        let dol: CGFloat = u.h * 0.65
        let stopy: [Gradient.Stop]
        if !u.noc {
            if u.scena == .wigilia {
                stopy = [st(0x203e7c, 0), st(0x5870a8, 0.54), st(0xd6a892, 0.84), st(0xf2cca2, 1)]
            } else if u.scena == .zima {
                stopy = [st(0x0e5cac, 0), st(0x3894de, 0.58), st(0xb0defa, 1)]
            } else {
                stopy = [st(0x16488c, 0), st(0x447ec4, 0.58), st(0xc4d8ee, 1)]
            }
        } else if u.scena == .zima {
            stopy = [st(0x040c1a, 0), st(0x091c36, 0.58), st(0x103862, 1)]
        } else if u.nye {
            stopy = [st(0x040914, 0), st(0x091226, 0.58), st(0x16203a, 1)]
        } else {
            stopy = [st(0x060916, 0), st(0x140e24, 0.58), st(0x420e20, 1)]
        }
        let rect = CGRect(x: 0, y: 0, width: u.w, height: dol + 4)
        wypelnijLiniowo(g, Path(rect), stopy, CGPoint(x: 0, y: 0), CGPoint(x: 0, y: dol))
    }

    /// 28 migających gwiazd (1412-1427), zawsze w nocy
    static func gwiazdy(_ g: GraphicsContext, _ u: Uklad) {
        let horyzont: CGFloat = u.h * 0.58
        for i in 0..<28 {
            let fx: CGFloat = CGFloat((i * 73 + 19) % 100) / 100
            let fy: CGFloat = CGFloat((i * 41 + 7) % 100) / 100
            let sx: CGFloat = fx * u.w
            let sy: CGFloat = fy * (horyzont * 0.82)
            let czest: CGFloat = 1.1 + CGFloat(i % 5) * 0.3
            let fala: CGFloat = abs(sin(u.t * czest + CGFloat(i)))
            let a: CGFloat = 0.25 + 0.65 * fala
            let r: CGFloat = (i % 3 == 0) ? 1.4 : 0.9
            wypelnij(g, kolo(sx, sy, r), rgba(255, 250, 235, Double(a)))
        }
    }

    /// Gwiazda Betlejemska (Wigilia)
    static func gwiazdaBetlejemska(_ g: GraphicsContext, _ u: Uklad) {
        let s = u.scH
        let puls: CGFloat = 0.92 + 0.14 * sin(u.t * 2.6)
        let gx: CGFloat = u.w * 0.90
        let gy: CGFloat = u.h * 0.13
        let r1: CGFloat = 26 * s * puls
        let a0: Double = u.noc ? 0.72 : 0.48
        let stopy: [Gradient.Stop] = [stC(rgba(255, 238, 160, a0), 0), stC(rgba(255, 238, 160, 0), 1)]
        poswiata(g, CGPoint(x: gx, y: gy), 1, r1, stopy)
        gwiazda8(g, gx, gy, 15 * s, 7 * s, hx(0xfff4b8), puls)
    }

    /// Rakiety i rozbłyski nad Rynkiem (Sylwester, Nowy Rok)
    static func fajerwerki(_ g: GraphicsContext, _ u: Uklad) {
        let kol1: Int = u.scena == .nowyRok ? 0x38bdf8 : 0xf43f5e
        // x0, yPeak, okres, przesunięcie, kolor, rdzeń
        let sloty: [(CGFloat, CGFloat, CGFloat, CGFloat, Int, Int)] = [
            (0.565, 0.152, 3.1, 0.0, 0xf5d060, 0xfffce6),
            (0.852, 0.174, 3.5, 1.1, kol1, 0xffe4eb),
            (0.752, 0.095, 3.8, 2.0, 0x38bdf8, 0xe0f8ff),
            (0.925, 0.268, 3.3, 2.6, 0xfde68a, 0xffffff)
        ]
        for (idx, slot) in sloty.enumerated() {
            let x0: CGFloat = u.w * slot.0
            let yPeak: CGFloat = u.h * slot.1
            let okres: CGFloat = slot.2
            let cyc: CGFloat = fmodP(u.t + slot.3, okres) / okres
            if cyc < 0.26 {
                let uu: CGFloat = cyc / 0.26
                rakieta(g, u, x0: x0, yPeak: yPeak, uu: uu, idx: idx)
            } else {
                let uu: CGFloat = (cyc - 0.26) / 0.74
                wybuch(g, u, x0: x0, yPeak: yPeak, uu: uu, kolor: hx(slot.4), rdzen: hx(slot.5))
            }
        }
    }

    static func rakieta(_ g: GraphicsContext, _ u: Uklad, x0: CGFloat, yPeak: CGFloat, uu: CGFloat, idx: Int) {
        let s = u.scH
        let ry: CGFloat = u.yRynek - uu * (u.yRynek - yPeak)
        let drg: CGFloat = sin(uu * 6 + CGFloat(idx)) * 2.2 * s
        let rx: CGFloat = x0 + drg
        let poczatek = CGPoint(x: rx, y: ry)
        let stopy: [Gradient.Stop] = [stC(Color.white, 0), stC(rgba(245, 208, 96, 0), 1)]
        let smuga = linia(poczatek, CGPoint(x: rx, y: ry + 24 * s))
        let cieniowanie = GraphicsContext.Shading.linearGradient(
            Gradient(stops: stopy), startPoint: poczatek, endPoint: CGPoint(x: rx, y: ry + 26 * s))
        let lw: CGFloat = max(1.2, 1.8 * s)
        g.stroke(smuga, with: cieniowanie, style: StrokeStyle(lineWidth: lw, lineCap: .round))
        wypelnij(g, kolo(rx, ry, 1.8 * s), Color.white)
    }

    static func wybuch(_ g: GraphicsContext, _ u: Uklad, x0: CGFloat, yPeak: CGFloat, uu: CGFloat, kolor: Color, rdzen: Color) {
        let s = u.scH
        let ease: CGFloat = 1 - pw(1 - uu, 2.2)
        let alpha: CGFloat = max(0, 1 - pw(uu, 1.6))
        let rad: CGFloat = 28 * s * ease
        if u.noc {
            let a0: Double = Double(0.38 * alpha)
            let stopy: [Gradient.Stop] = [stC(rgba(245, 208, 96, a0), 0), stC(rgba(245, 208, 96, 0), 1)]
            poswiata(g, CGPoint(x: x0, y: yPeak), 1, rad * 1.25, stopy)
        }
        var g2 = g
        let mnoznik: CGFloat = u.noc ? 1 : 0.86
        g2.opacity = Double(alpha * mnoznik)
        let opad1: CGFloat = uu * uu * 4 * s
        let opad2: CGFloat = uu * uu * 11 * s
        let lw: CGFloat = max(1.0, 1.2 * s)
        let r0: CGFloat = rad * 0.18
        for k in 0..<20 {
            let nieparz: Bool = k % 2 == 1
            let baza: CGFloat = 2 * CGFloat.pi * CGFloat(k) / 20
            let a: CGFloat = baza + (nieparz ? 0.12 : 0)
            let rLen: CGFloat = rad * (nieparz ? 0.65 : 1)
            let x1: CGFloat = x0 + cos(a) * r0
            let y1: CGFloat = yPeak + sin(a) * r0 + opad1
            let x2: CGFloat = x0 + cos(a) * rLen
            let y2: CGFloat = yPeak + sin(a) * rLen + opad2
            obrys(g2, linia(CGPoint(x: x1, y: y1), CGPoint(x: x2, y: y2)), kolor, lw)
            wypelnij(g2, kolo(x2, y2, 1.3 * s), rdzen)
        }
    }
}

// MARK: - Płyta Rynku, kamienice, girlandy, reflektory

fileprivate extension ScenySwiateczne {
    /// Ośnieżona płyta, nocna poświata i łuki bruku
    static func plyta(_ g: GraphicsContext, _ u: Uklad) {
        let gora: Int = u.noc ? (u.nye ? 0x10182a : 0x16243c) : (u.nye ? 0xc8d4e4 : 0xe4effa)
        let dolK: Int = u.noc ? (u.nye ? 0x080e1a : 0x0c1628) : (u.nye ? 0xaebed2 : 0xcde0f2)
        let rect = CGRect(x: 0, y: u.yRynek, width: u.w, height: u.h - u.yRynek)
        wypelnijLiniowo(g, Path(rect), [st(gora, 0), st(dolK, 1)], CGPoint(x: 0, y: u.yRynek), CGPoint(x: 0, y: u.h))

        if u.noc {
            let kol: (Double, Double, Double, Double)
            if u.scena == .zima {
                kol = (56, 189, 248, 0.34)
            } else if u.nye {
                kol = (212, 175, 55, 0.36)
            } else {
                kol = (215, 48, 72, 0.32)
            }
            let c = CGPoint(x: u.cxR, y: u.yBase + (u.yRynek - u.h * 0.667) + 16 * u.rR)
            let stopy: [Gradient.Stop] = [stC(rgba(kol.0, kol.1, kol.2, kol.3), 0), stC(rgba(kol.0, kol.1, kol.2, 0), 1)]
            poswiata(g, c, 4, 64 * u.rR, stopy)
        }

        // Wachlarzowe łuki bruku spod śniegu
        let kolLuku: Color = u.noc ? rgba(200, 228, 255, 0.06) : rgba(120, 150, 185, 0.16)
        let lw: CGFloat = max(0.7, 0.8 * u.rR)
        let cy: CGFloat = u.yBase + 4 * u.rR
        let a0: CGFloat = 0.06 * CGFloat.pi
        let a1: CGFloat = 0.94 * CGFloat.pi
        var promien: CGFloat = 28
        while promien < 260 {
            let rx: CGFloat = promien * 1.45 * u.rR
            let ry: CGFloat = promien * 0.42 * u.rR
            obrys(g, lukElipsy(u.cxR, cy, rx, ry, a0, a1, 28), kolLuku, lw)
            promien += 22
        }
    }

    /// Pierzeje kamieniczek wokół Rynku (układ jak w JS)
    static func kamienice(_ g: GraphicsContext, _ u: Uklad) {
        let scR = u.scR
        let hwBase: CGFloat = max(28, 34 * scR)
        let gapL: CGFloat = u.gapL
        let gapR: CGFloat = u.gapR
        let koniec: CGFloat = u.w - 12 * scR
        var xc: CGFloat = 3 * scR
        var idx: Int = 0
        while xc < koniec && idx < 200 {
            if xc + hwBase * 0.65 > gapL && xc < gapR {
                xc = gapR + 3 * scR
                if xc >= koniec { break }
            }
            var hw: CGFloat = hwBase + CGFloat(idx % 3 - 1) * 3 * scR
            if xc < gapL && xc + hw > gapL {
                hw = max(18 * scR, gapL - xc - 1 * scR)
            }
            if xc + hw > u.w - 2 * scR {
                hw = u.w - 2 * scR - xc
            }
            if hw < 16 * scR { break }
            kamienica(g, u, x: xc, hw: hw, idx: idx)
            xc += hw
            idx += 1
        }
    }

    static func kamienica(_ g: GraphicsContext, _ u: Uklad, x: CGFloat, hw: CGFloat, idx: Int) {
        let scR = u.scR
        let dzienSciana: [Int] = [0xd6e0e4, 0xe8c68a, 0xa8d4cc, 0xece0d0, 0xe2b6bc, 0xded4c4]
        let dzienDach: [Int] = [0x444c58, 0x5c4034, 0x3a444e, 0x9c4836, 0x4e3e48, 0x404652]
        let nocSciana: [(Double, Double, Double)] = [(24, 32, 46), (28, 30, 40), (20, 32, 42), (28, 32, 44), (28, 26, 40), (26, 30, 42)]
        let p: Int = idx % 6
        let hh: CGFloat = (23.5 + CGFloat(idx % 3) * 1.4) * scR
        let hy: CGFloat = u.yRynek - hh
        let n = nocSciana[p]
        let sciana: Color = u.noc ? rgba(n.0, n.1, n.2, 1) : hx(dzienSciana[p])
        let dach: Color = u.noc ? hx(0x101624) : hx(dzienDach[p])

        wypelnij(g, prost(x, hy, hw, hh), sciana)
        let gDach: CGFloat = hy - 6.4 * scR
        let dachP = wielokat([
            CGPoint(x: x, y: hy),
            CGPoint(x: x + 4 * scR, y: gDach),
            CGPoint(x: x + hw - 4 * scR, y: gDach),
            CGPoint(x: x + hw, y: hy)
        ])
        wypelnij(g, dachP, dach)
        if idx % 2 == 1 {
            let szczyt = wielokat([
                CGPoint(x: x + hw * 0.25, y: hy),
                CGPoint(x: x + hw * 0.50, y: hy - 9.2 * scR),
                CGPoint(x: x + hw * 0.75, y: hy)
            ])
            wypelnij(g, szczyt, sciana)
        }
        // Śnieżna czapa na gzymsie
        let kolSn: Color = u.noc ? hx(0xd8ecff) : Color.white
        obrys(g, linia(CGPoint(x: x, y: hy), CGPoint(x: x + hw, y: hy)), kolSn, max(1.4, 1.9 * scR))

        // Dwa rzędy okien; wzór świecenia z indeksu okna (w Wigilię wszystkie)
        let wszystkie: Bool = u.scena == .wigilia
        let krok: CGFloat = 8 * scR
        let granica: CGFloat = x + hw - 5.5 * scR
        let ow: CGFloat = 3.3 * scR
        let oh: CGFloat = 5.0 * scR
        for r in 0..<2 {
            let wy: CGFloat = hy + (5 + CGFloat(r) * 8.5) * scR
            var wx: CGFloat = x + 4.5 * scR
            var kol: Int = 0
            while wx < granica && kol < 60 {
                let wzor: Int = (kol * 7 + r * 3 + idx * 5) % 4
                let swieci: Bool = wszystkie || wzor != 0
                let c: Color
                if u.noc || wszystkie {
                    c = swieci ? rgba(255, 218, 126, 0.88) : hx(0x121a2a)
                } else {
                    c = hx(0x3e4e62)
                }
                wypelnij(g, prost(wx, wy, ow, oh), c)
                wx += krok
                kol += 1
            }
        }
    }

    /// Migoczące girlandy nad Rynkiem (Mikołajki, Święta)
    static func girlandy(_ g: GraphicsContext, _ u: Uklad) {
        let scR = u.scR
        let przesla: [(CGFloat, CGFloat, CGFloat)] = [
            (u.w * 0.04, u.gapL - 4 * scR, u.yRynek - 30 * scR),
            (u.gapR + 4 * scR, u.w * 0.98, u.yRynek - 29 * scR)
        ]
        let kolDrutu: Color = rgba(65, 50, 42, 0.72)
        let lw: CGFloat = max(0.8, 0.95 * scR)
        for (xs, xe, ys) in przesla {
            if xe <= xs + 20 { continue }
            var drut = Path()
            for k in 0...16 {
                let pt = punktGirlandy(xs, xe, ys, k, scR)
                if k == 0 { drut.move(to: pt) } else { drut.addLine(to: pt) }
            }
            obrys(g, drut, kolDrutu, lw)
            for k in stride(from: 1, to: 16, by: 2) {
                let pt = punktGirlandy(xs, xe, ys, k, scR)
                let blysk: CGFloat = 0.55 + 0.45 * sin(u.t * 4.2 + CGFloat(k) * 1.1)
                let a: Double = Double(blysk)
                let c: Color = (k % 4 == 1) ? rgba(255, 232, 135, a) : rgba(255, 82, 102, a)
                wypelnij(g, kolo(pt.x, pt.y + 0.6 * scR, 1.45 * scR), c)
            }
        }
    }

    static func punktGirlandy(_ xs: CGFloat, _ xe: CGFloat, _ ys: CGFloat, _ k: Int, _ scR: CGFloat) -> CGPoint {
        let f: CGFloat = CGFloat(k) / 16
        let x: CGFloat = xs + (xe - xs) * f
        let y: CGFloat = ys + sin(f * CGFloat.pi) * 6.5 * scR
        return CGPoint(x: x, y: y)
    }

    /// Nocne smugi reflektorów za ratuszem
    static func reflektory(_ g: GraphicsContext, _ u: Uklad) {
        let scR = u.rR
        let sway: CGFloat = sin(u.t * 0.85) * 9 * scR
        let cB1: Color
        if u.scena == .zima {
            cB1 = rgba(56, 189, 248, 0.28)
        } else if u.nye {
            cB1 = rgba(212, 175, 55, 0.34)
        } else {
            cB1 = rgba(205, 28, 58, 0.30)
        }
        let cB2: Color = u.scena == .zima ? rgba(186, 230, 253, 0.24) : rgba(245, 214, 128, 0.26)
        let b2 = wielokat([
            u.L(-26, 20),
            CGPoint(x: u.X(-66) + sway, y: 0),
            CGPoint(x: u.X(-24) + sway, y: 0),
            u.L(-6, 20)
        ])
        wypelnij(g, b2, cB2)
        let b1 = wielokat([
            u.L(4, 20),
            CGPoint(x: u.X(14) - sway, y: 0),
            CGPoint(x: u.X(60) - sway, y: 0),
            u.L(28, 20)
        ])
        wypelnij(g, b1, cB1)
        if u.nye {
            let sw2: CGFloat = cos(u.t * 1.15) * 18 * scR
            let c3: Color = rgba(255, 238, 170, 0.20)
            let b3 = wielokat([
                u.L(-8, 35),
                CGPoint(x: u.X(-92) + sw2, y: 0),
                CGPoint(x: u.X(-70) + sw2, y: 0),
                u.L(2, 35)
            ])
            let b4 = wielokat([
                u.L(8, 35),
                CGPoint(x: u.X(74) - sw2, y: 0),
                CGPoint(x: u.X(96) - sw2, y: 0),
                u.L(-2, 35)
            ])
            wypelnij(g, b3, c3)
            wypelnij(g, b4, c3)
        }
    }

    /// Poduszki czytelności pod 3 kolumnami (tryb karty)
    static func poduszki(_ g: GraphicsContext, _ u: Uklad) {
        let scPill: CGFloat = min(u.w / 380, u.h / 240)
        let wys: CGFloat = max(28, (34 * scPill).rounded())
        let pTop: CGFloat = u.h * 0.60 - wys - 4 * u.scH
        let pBot: CGFloat = u.yg - 2 * u.scH
        guard pBot > pTop + 20 else { return }
        let kol: Color = u.noc ? rgba(6, 12, 26, 0.30) : rgba(6, 12, 26, 0.36)
        let srodki: [CGFloat] = [u.w / 6, u.w * 0.5, u.w * 5 / 6]
        for cx in srodki {
            let p = zaokr(cx - 35 * scPill, pTop, 70 * scPill, pBot - pTop, 11 * scPill)
            wypelnij(g, p, kol)
        }
    }
}

// MARK: - Ratusz (uproszczona, ośnieżona wersja 3380-3494)

fileprivate extension ScenySwiateczne {
    static func koloryRatusza(_ u: Uklad) -> KoloryRatusza {
        let n: Bool = u.noc
        let zima: Bool = u.scena == .zima
        let cokol: Int = !n ? 0xb8a688 : (zima ? 0x125c98 : (u.nye ? 0x1c2640 : 0x8e1428))
        let sciana: Int = !n ? 0xe8ddcb : (zima ? 0xd0e8fa : 0xecdcba)
        let cien: Int = !n ? 0xd2c6b1 : (zima ? 0xabcce8 : 0xcfbda0)
        let pil: Int = !n ? 0xfaf9f6 : 0xfffaf0
        let okno: Int = (!n && u.scena != .wigilia) ? 0x3a4656 : 0xffdc80
        let helm: Int = n ? 0x181e2c : 0x2a303a
        return KoloryRatusza(cokol: hx(cokol), sciana: hx(sciana), cien: hx(cien),
                             pilaster: hx(pil), okno: hx(okno), helm: hx(helm))
    }

    /// fRect: prostokąt w lokalnych jednostkach ratusza (x1, góra, x2, dół)
    static func lokProst(_ g: GraphicsContext, _ u: Uklad, _ x1: CGFloat, _ yt: CGFloat, _ x2: CGFloat, _ yb: CGFloat, _ c: Color) {
        let w: CGFloat = (x2 - x1) * u.rR
        let h: CGFloat = (yt - yb) * u.rR
        wypelnij(g, prost(u.X(x1), u.Y(yt), w, h), c)
    }

    static func ratuszKorpus(_ g: GraphicsContext, _ u: Uklad, _ kr: KoloryRatusza) {
        let scR = u.rR
        lokProst(g, u, -39, 34, 39, 11, kr.sciana)
        lokProst(g, u, -39, 34, -24, 11, kr.cien)
        lokProst(g, u, -39, 11, 39, 0, kr.cokol)
        lokProst(g, u, -39.5, 11.8, 39.5, 10.5, kr.pilaster)
        let pilastry: [CGFloat] = [-38, -25, -13.5, -5.8, 5.8, 13.5, 25, 38]
        for px in pilastry {
            lokProst(g, u, px - 1.5, 34, px + 1.5, 11.5, kr.pilaster)
        }
        let okna: [CGFloat] = [-31.5, -19.2, -9.6, 9.6, 19.2, 31.5]
        for wx in okna {
            lokProst(g, u, wx - 2.3, 31, wx + 2.3, 24.8, kr.pilaster)
            lokProst(g, u, wx - 1.5, 30.1, wx + 1.5, 25.6, kr.okno)
            lokProst(g, u, wx - 2.3, 21.2, wx + 2.3, 14.8, kr.pilaster)
            lokProst(g, u, wx - 1.5, 20.3, wx + 1.5, 15.6, kr.okno)
        }

        // Portal, drzwi, schody
        wypelnij(g, wielokat([u.L(-5.6, 13.2), u.L(0, 17.0), u.L(5.6, 13.2)]), kr.pilaster)
        lokProst(g, u, -3.3, 13.2, 3.3, 2.5, kr.pilaster)
        lokProst(g, u, -2.1, 10.5, 2.1, 2.5, hx(0x40281c))
        wypelnij(g, wielokat([u.L(-8.5, 0), u.L(-4.0, 3.0), u.L(4.0, 3.0), u.L(8.5, 0)]), hx(0xe8f2fc))

        // Wieniec nad portalem
        if u.scena == .mikolajki || u.scena == .swieta || u.scena == .wigilia {
            let wc = u.L(0, 12.8)
            obrys(g, kolo(wc.x, wc.y, 2.3 * scR), hx(0x1c6c34), max(1.2, 1.7 * scR))
            let kc = u.L(0, 15.0)
            wypelnij(g, kolo(kc.x, kc.y, 0.9 * scR), hx(0xdc143c))
        }

        // Ośnieżony dach, attyka, linia śniegu
        let dachP = wielokat([u.L(-37, 40.2), u.L(-30, 46.4), u.L(30, 46.4), u.L(37, 40.2)])
        wypelnij(g, dachP, u.noc ? hx(0xcbe0f5) : hx(0xe8f4ff))
        lokProst(g, u, -39.5, 40.2, 39.5, 35.8, kr.pilaster)
        obrys(g, linia(u.L(-39.8, 40.5), u.L(39.8, 40.5)), Color.white, max(1.5, 2.1 * scR))

        // Sople (tylko zima)
        if u.scena == .zima {
            let kolS: Color = u.noc ? rgba(186, 230, 253, 0.85) : rgba(224, 242, 254, 0.92)
            for ix in stride(from: -36, through: 36, by: 6) {
                let dl: CGFloat = 2.2 + CGFloat(abs(ix) % 3) * 0.9
                let xf: CGFloat = CGFloat(ix)
                let sopel = wielokat([u.L(xf - 0.6, 35.8), u.L(xf + 0.6, 35.8), u.L(xf, 35.8 - dl)])
                wypelnij(g, sopel, kolS)
            }
        }
    }

    static func ratuszWieza(_ g: GraphicsContext, _ u: Uklad, _ kr: KoloryRatusza) {
        let scR = u.rR
        lokProst(g, u, -12.5, 62, 12.5, 40, kr.sciana)
        lokProst(g, u, -12.5, 62, -10.3, 40, kr.pilaster)
        lokProst(g, u, 10.3, 62, 12.5, 40, kr.pilaster)
        lokProst(g, u, -1.9, 53, 1.9, 46, kr.okno)
        lokProst(g, u, -14, 64, 14, 61.8, kr.pilaster)
        lokProst(g, u, -11.8, 85, 11.8, 64, kr.sciana)
        lokProst(g, u, -13.6, 86.8, 13.6, 85, kr.pilaster)

        zegar(g, u, kr)

        // Bęben i dolny hełm
        lokProst(g, u, -7.5, 97, 7.5, 86.8, kr.sciana)
        let helmP = wielokat([
            u.L(-7.2, 97.8), u.L(-9.2, 102.5), u.L(-8.4, 107), u.L(-5.2, 111.5),
            u.L(5.2, 111.5), u.L(8.4, 107), u.L(9.2, 102.5), u.L(7.2, 97.8)
        ])
        wypelnij(g, helmP, kr.helm)

        // Latarnia (nocą świeci)
        if u.noc {
            let swiatlo: Color = u.scena == .zima ? rgba(125, 211, 252, 0.84) : rgba(255, 220, 120, 0.86)
            lokProst(g, u, -4.2, 120.8, 4.2, 112, swiatlo)
        }
        let slupki: [CGFloat] = [-4.4, -1.5, 1.5, 4.4]
        let lwS: CGFloat = max(1.0, 1.2 * scR)
        for lx in slupki {
            obrys(g, linia(u.L(lx, 111.5), u.L(lx, 121.2)), kr.helm, lwS)
        }
        lokProst(g, u, -5.6, 122.2, 5.6, 120.8, kr.helm)

        // Cebulasty hełm i iglica
        let cebula = wielokat([
            u.L(-4.8, 122.2), u.L(-6.0, 126), u.L(-4.0, 131), u.L(0, 143),
            u.L(4.0, 131), u.L(6.0, 126), u.L(4.8, 122.2)
        ])
        wypelnij(g, cebula, kr.helm)
        let kolI: Color = u.noc ? hx(0xebd07d) : hx(0x3c4048)
        obrys(g, linia(u.L(0, 143), u.L(0, 153)), kolI, max(0.9, 1.0 * scR))
    }

    /// Zegar: Sylwester tuż przed północą, Nowy Rok 12:00, inaczej czas rzeczywisty
    static func zegar(_ g: GraphicsContext, _ u: Uklad, _ kr: KoloryRatusza) {
        let scR = u.rR
        let c = u.L(0, 77.5)
        wypelnij(g, kolo(c.x, c.y, 5.8 * scR), kr.pilaster)
        wypelnij(g, kolo(c.x, c.y, 5.0 * scR), u.noc ? hx(0xffeeb4) : hx(0x242830))
        let kol: Color = u.noc ? hx(0x1e1814) : hx(0xeec65f)
        let lw: CGFloat = max(0.9, 1.1 * scR)
        var p = Path()
        if u.scena == .nowyRok {
            p.move(to: c)
            p.addLine(to: u.L(0, 81.6))
        } else if u.scena == .sylwester {
            p.move(to: c)
            p.addLine(to: u.L(-1.1, 81.5))
            p.move(to: c)
            p.addLine(to: u.L(-0.3, 80.6))
        } else {
            let minuty: CGFloat = CGFloat(u.minuta) + CGFloat(u.sekunda) / 60
            let godziny: CGFloat = CGFloat(u.godzina % 12) + CGFloat(u.minuta) / 60
            let pol: CGFloat = CGFloat.pi * 0.5
            let pelny: CGFloat = CGFloat.pi * 2
            let minAng: CGFloat = minuty / 60 * pelny - pol
            let hrAng: CGFloat = godziny / 12 * pelny - pol
            let dl1: CGFloat = 2.8 * scR
            let dl2: CGFloat = 4.0 * scR
            p.move(to: c)
            p.addLine(to: CGPoint(x: c.x + cos(hrAng) * dl1, y: c.y + sin(hrAng) * dl1))
            p.move(to: c)
            p.addLine(to: CGPoint(x: c.x + cos(minAng) * dl2, y: c.y + sin(minAng) * dl2))
        }
        obrys(g, p, kol, lw)
    }
}

// MARK: - Pierwszy plan

fileprivate extension ScenySwiateczne {
    static func pierwszyPlan(_ g: GraphicsContext, _ u: Uklad) {
        let s = u.scH
        switch u.scena {
        case .zima:
            balwan(g, u)
        case .mikolajki:
            mikolaj(g, u)
            choinka(g, u, tx: u.w * 0.935, tyg: u.yg - 4 * s, wys: 56 * s, prezenty: false)
        case .wigilia:
            choinka(g, u, tx: u.fgX, tyg: u.yg, wys: 84 * s, prezenty: true)
            sanie(g, u)
        case .swieta:
            choinka(g, u, tx: u.fgX, tyg: u.yg, wys: 88 * s, prezenty: true)
            straganik(g, u)
        case .sylwester, .nowyRok:
            noworoczne(g, u)
        default:
            break
        }
    }

    /// Bałwan w cylindrze i błękitnym szaliku (zima)
    static func balwan(_ g: GraphicsContext, _ u: Uklad) {
        let s = u.scH
        let sc: CGFloat = 1.70 * s
        let bob: CGFloat = sin(u.t * 2.0) * 1.2 * s
        let machanie: CGFloat = sin(u.t * 4.2) * 4.5 * s
        let L = Lok(cx: u.fgX, cy: u.yg + bob, sc: sc, ky: -1)

        let kopiec: Color = u.noc ? hx(0xd4eafc) : hx(0xf6fbff)
        wypelnij(g, elipsa(u.fgX, u.yg + 1 * s, 31 * s, 5.5 * s), kopiec)

        // Ręce z gałązek, prawa macha
        let kolR: Color = hx(0x603e26)
        let lwR: CGFloat = max(1.6, 2.2 * s)
        obrys(g, linia(L.p(-6.2, 22.5), L.p(-14.8, 27.5)), kolR, lwR)
        let dlon = L.p(14.8, 30.2)
        obrys(g, linia(L.p(6.2, 22.8), CGPoint(x: dlon.x, y: dlon.y + machanie)), kolR, lwR)

        // Trzy kule
        let kule: [(CGFloat, CGFloat)] = [(8.8, 9.6), (21.2, 7.3), (31.6, 5.7)]
        let wypK: Color = u.noc ? hx(0xeaf4ff) : Color.white
        let obrK: Color = u.noc ? hx(0x9ecaf0) : hx(0xc4dcf2)
        let lwK: CGFloat = max(1.0, 1.3 * s)
        for (dyc, br) in kule {
            let kula = kolo(L.x(0), L.y(dyc), br * sc)
            wypelnij(g, kula, wypK)
            obrys(g, kula, obrK, lwK)
        }

        // Guziki, oczy, nos
        let wegiel: Color = hx(0x161e2c)
        let guziki: [CGFloat] = [22.2, 18.5, 14.8]
        for gy in guziki {
            wypelnij(g, kolo(L.x(0), L.y(gy), 1.8 * s), wegiel)
        }
        let oczy: [CGFloat] = [-2.0, 1.8]
        for ex in oczy {
            wypelnij(g, kolo(L.x(ex), L.y(32.8), 1.6 * s), wegiel)
        }
        wypelnij(g, wielokat([L.p(0, 32.1), L.p(6.8, 30.8), L.p(0, 30.0)]), hx(0xf97316))

        // Szalik z powiewającym końcem
        wypelnij(g, zaokr(L.x(-6.4), L.y(27.5), 12.8 * sc, 3.2 * sc, 3 * s), hx(0x0284c7))
        let sway: CGFloat = sin(u.t * 3.4) * 3.2 * s
        let ogon = wielokat([
            L.p(2, 25.8),
            L.p(5.8, 25.8),
            CGPoint(x: L.x(8.0) + sway, y: L.y(16.2)),
            CGPoint(x: L.x(3.8) + sway, y: L.y(16.0))
        ])
        wypelnij(g, ogon, hx(0x38bdf8))

        // Cylinder z opaską
        let czern: Color = hx(0x141c2c)
        wypelnij(g, zaokr(L.x(-7.8), L.y(37.4), 15.6 * sc, 2.1 * sc, 2 * s), czern)
        wypelnij(g, prost(L.x(-5.1), L.y(45.8), 10.2 * sc, 9.2 * sc), czern)
        wypelnij(g, prost(L.x(-5.1), L.y(38.9), 10.2 * sc, 2.0 * sc), hx(0x38bdf8))
    }

    /// Św. Mikołaj z workiem, laską i prezentami (Mikołajki)
    static func mikolaj(_ g: GraphicsContext, _ u: Uklad) {
        let s = u.scH
        let cx: CGFloat = u.fgX * 0.92
        let sc: CGFloat = 1.68 * s
        let machanie: CGFloat = sin(u.t * 3.8) * 3.8 * s
        let L = Lok(cx: cx, cy: u.yg, sc: sc, ky: -1)

        let kopiec: Color = u.noc ? hx(0xd8e8f8) : hx(0xf6fbff)
        wypelnij(g, elipsa(cx + 10 * s, u.yg + 1 * s, 38 * s, 5.5 * s), kopiec)

        // Worek i laska cukrowa
        wypelnij(g, elipsa(L.x(-8), L.y(12.5), 7 * sc, 11 * sc), hx(0xac7c4a))
        obrys(g, laskaCukrowa(L), hx(0xdc143c), max(1.5, 2.1 * s))

        // Buty i płaszcz
        let but: Color = hx(0x1a161a)
        wypelnij(g, prost(L.x(-4.6), L.y(5.2), 3.8 * sc, 5.2 * sc), but)
        wypelnij(g, prost(L.x(1.0), L.y(5.2), 3.8 * sc, 5.2 * sc), but)
        wypelnij(g, wielokat([L.p(-8.8, 5), L.p(-6.2, 24.8), L.p(6.4, 24.8), L.p(9.0, 5)]), hx(0xcc1630))

        // Machająca ręka w białej rękawicy
        let reka = L.p(13.2, 27.5)
        obrys(g, linia(L.p(6.2, 21.5), CGPoint(x: reka.x, y: reka.y + machanie)), hx(0xd01832), max(3.5, 5.2 * s))
        let mit = L.p(13.6, 27.8)
        wypelnij(g, kolo(mit.x, mit.y + machanie, 3.0 * s), Color.white)

        // Futro, pas, klamra
        let futro: Color = hx(0xfffdfa)
        wypelnij(g, zaokr(L.x(-9.4), L.y(7.5), 19 * sc, 3.2 * sc, 3 * s), futro)
        wypelnij(g, prost(L.x(-1.6), L.y(24.5), 3.2 * sc, 19.5 * sc), futro)
        wypelnij(g, prost(L.x(-7.5), L.y(15.2), 15.2 * sc, 2.8 * sc), hx(0x1c1616))
        obrys(g, prost(L.x(-2.1), L.y(15.6), 4.2 * sc, 3.5 * sc), hx(0xf5cc4e), max(1.2, 1.8 * s))

        mikolajGlowa(g, u, L, machanie)
        prezentyMikolaja(g, L)
    }

    /// Hak laski: łuk od π do 2π, potem w dół
    static func laskaCukrowa(_ L: Lok) -> Path {
        var p = Path()
        let c = L.p(-9.5, 24.5)
        let r: CGFloat = 2.4 * L.sc
        for k in 0...12 {
            let a: CGFloat = CGFloat.pi + CGFloat.pi * CGFloat(k) / 12
            let pt = CGPoint(x: c.x + cos(a) * r, y: c.y + sin(a) * r)
            if k == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.addLine(to: L.p(-7.1, 21))
        return p
    }

    static func mikolajGlowa(_ g: GraphicsContext, _ u: Uklad, _ L: Lok, _ machanie: CGFloat) {
        let s = u.scH
        let sc = L.sc
        wypelnij(g, kolo(L.x(0), L.y(28.5), 4.8 * sc), hx(0xfed6bc))
        let broda = wielokat([L.p(-5.8, 28), L.p(5.8, 28), L.p(4.4, 20.8), L.p(0, 17.4), L.p(-4.4, 20.8)])
        wypelnij(g, broda, Color.white)
        // Złote okulary
        let lwO: CGFloat = max(1.0, 1.3 * s)
        obrys(g, kolo(L.x(-2.0), L.y(30), 1.5 * sc), hx(0xebbe3e), lwO)
        obrys(g, kolo(L.x(2.0), L.y(30), 1.5 * sc), hx(0xebbe3e), lwO)
        // Czapka z pomponem
        let w35: CGFloat = machanie * 0.35
        let szpic = L.p(9.6, 39.2)
        let czapka = wielokat([L.p(-5.6, 31.8), L.p(5.6, 31.8), CGPoint(x: szpic.x, y: szpic.y + w35), L.p(-1.8, 41.2)])
        wypelnij(g, czapka, hx(0xd61630))
        wypelnij(g, zaokr(L.x(-6.4), L.y(33.8), 12.8 * sc, 3.0 * sc, 3 * s), Color.white)
        let pom = L.p(10.0, 39.0)
        wypelnij(g, kolo(pom.x, pom.y + w35, 3.4 * s), Color.white)
    }

    static func prezentyMikolaja(_ g: GraphicsContext, _ L: Lok) {
        let sc = L.sc
        // x, góra, szer., wys., pudełko, wstążka
        let dane: [(CGFloat, CGFloat, CGFloat, CGFloat, Int, Int)] = [
            (10.5, 11.2, 9.5, 11.2, 0xc01630, 0xf5ce58),
            (20.8, 8.4, 8.4, 8.4, 0x14764a, 0xf8d669),
            (14.0, 17.8, 7.2, 6.6, 0xdaac36, 0xc61630)
        ]
        for (gx, gyt, gw, gh, bc, rc) in dane {
            wypelnij(g, prost(L.x(gx), L.y(gyt), gw * sc, gh * sc), hx(bc))
            let wstazka: Color = hx(rc)
            wypelnij(g, prost(L.x(gx + gw * 0.42), L.y(gyt), gw * 0.18 * sc, gh * sc), wstazka)
            wypelnij(g, prost(L.x(gx), L.y(gyt - gh * 0.42), gw * sc, gh * 0.18 * sc), wstazka)
        }
    }

    /// drawFestiveSpruce: choinka z bombkami, migotaniem, prezentami i gwiazdą
    static func choinka(_ g: GraphicsContext, _ u: Uklad, tx: CGFloat, tyg: CGFloat, wys: CGFloat, prezenty: Bool) {
        let s = u.scH
        if u.noc {
            let c = CGPoint(x: tx, y: tyg - wys * 0.5)
            let stopy: [Gradient.Stop] = [stC(rgba(255, 205, 95, 0.42), 0), stC(rgba(255, 205, 95, 0), 1)]
            poswiata(g, c, 2, wys * 0.65, stopy)
        }
        let kopiec: Color = u.noc ? hx(0xd6e8fa) : hx(0xf6fbff)
        wypelnij(g, elipsa(tx, tyg + 1 * s, 26 * s, 4.5 * s), kopiec)
        wypelnij(g, prost(tx - 5.5 * s, tyg - 8 * s, 11 * s, 8 * s), hx(0x762822))
        let maxW: CGFloat = wys * 0.34
        for tr in 0..<5 {
            pietroChoinki(g, u, tx: tx, tyg: tyg, wys: wys, maxW: maxW, tr: tr)
        }
        if prezenty {
            prezentyPodChoinka(g, u, tx: tx, tyg: tyg)
        }
        let puls: CGFloat = 0.92 + 0.12 * sin(u.t * 3.2)
        gwiazda8(g, tx, tyg - wys - 2.5 * s, 9.5 * s, 4.8 * s, hx(0xffe46e), puls)
    }

    static func pietroChoinki(_ g: GraphicsContext, _ u: Uklad, tx: CGFloat, tyg: CGFloat, wys: CGFloat, maxW: CGFloat, tr: Int) {
        let s = u.scH
        let trf: CGFloat = CGFloat(tr)
        let frac: CGFloat = trf / 5
        let yb: CGFloat = tyg - 6 * s - frac * (wys - 10 * s)
        let frac2: CGFloat = (trf + 1.3) / 5
        let yt: CGFloat = tyg - 6 * s - frac2 * (wys - 5 * s)
        let tw: CGFloat = (1 - frac * 0.73) * maxW
        let zielen: Double = u.noc ? Double(82 + tr * 7) : Double(108 + tr * 7)
        let kol: Color = u.noc ? rgba(18, zielen, 46, 1) : rgba(24, zielen, 56, 1)
        let trojkat = wielokat([CGPoint(x: tx - tw, y: yb), CGPoint(x: tx, y: yt), CGPoint(x: tx + tw, y: yb)])
        wypelnij(g, trojkat, kol)

        // Śnieg na końcach gałęzi
        let lw: CGFloat = max(1.1, 1.6 * s)
        let ySn: CGFloat = yb - 1.8 * s
        obrys(g, linia(CGPoint(x: tx - tw, y: yb), CGPoint(x: tx - tw * 0.52, y: ySn)), Color.white, lw)
        obrys(g, linia(CGPoint(x: tx + tw, y: yb), CGPoint(x: tx + tw * 0.52, y: ySn)), Color.white, lw)

        // Bombki i migoczące światełka
        let znak: CGFloat = tr % 2 == 0 ? 1.3 : -1.3
        for k in 0..<3 {
            let kf: CGFloat = CGFloat(k)
            let bx: CGFloat = tx + (kf - 1) * tw * 0.48 + znak * s
            let bY: CGFloat = yb - (3 + kf * 1.9) * s
            let bomb: Color = (tr + k) % 2 == 0 ? hx(0xe41a38) : hx(0xf8ce52)
            wypelnij(g, kolo(bx, bY, 1.9 * s), bomb)
            let fazaM: CGFloat = u.t * 4.8 + trf * 1.7 + kf * 2.1
            let twk: CGFloat = 0.45 + 0.55 * sin(fazaM)
            wypelnij(g, kolo(bx + 2.4 * s, bY - 1.5 * s, 1.3 * s), rgba(255, 248, 195, Double(twk)))
        }
    }

    static func prezentyPodChoinka(_ g: GraphicsContext, _ u: Uklad, tx: CGFloat, tyg: CGFloat) {
        let s = u.scH
        // dx, szer., wys., pudełko, wstążka
        let dane: [(CGFloat, CGFloat, CGFloat, Int, Int)] = [
            (-19.5, 10.5, 10, 0xc01630, 0xf5ce58),
            (9.0, 11.2, 11.5, 0x147448, 0xf8d666),
            (-10.5, 8.5, 7.5, 0xd8aa34, 0xc41630)
        ]
        for (dx, gw, gh, bc, rc) in dane {
            let x0: CGFloat = tx + dx * s
            let y0: CGFloat = tyg - gh * s
            wypelnij(g, prost(x0, y0, gw * s, gh * s), hx(bc))
            let wstazka: Color = hx(rc)
            let xw: CGFloat = tx + (dx + gw * 0.41) * s
            wypelnij(g, prost(xw, y0, gw * 0.18 * s, gh * s), wstazka)
            wypelnij(g, prost(x0, tyg - gh * 0.58 * s, gw * s, gh * 0.18 * s), wstazka)
        }
    }

    /// Złote sanie z reniferami i Rudolfem (Wigilia)
    static func sanie(_ g: GraphicsContext, _ u: Uklad) {
        let s = u.scH
        let flyU: CGFloat = fmodP(u.t * 0.085, 1)
        let sx0: CGFloat = -75 * s + flyU * (u.w + 150 * s)
        let sy0: CGFloat = u.h * 0.16 + sin(u.t * 2.2) * 3.5 * s
        let scS: CGFloat = 1.48 * s
        let L = Lok(cx: sx0, cy: sy0, sc: scS, ky: 1)
        let zloto: Color = u.noc ? hx(0xf8d66c) : hx(0xda9e20)

        // Świetlna smuga za saniami
        for i in 0..<24 {
            let fi: CGFloat = CGFloat(i)
            let uu: CGFloat = fi / 23
            let dx: CGFloat = -34 + uu * 38
            let fala: CGFloat = sin(u.t * 5 + fi) * 1.1
            let dy: CGFloat = 10 - sin(uu * CGFloat.pi * 0.65) * 10 + fala
            let r: CGFloat = (0.8 + uu * 1.1) * scS
            let a: Double = Double(0.18 + uu * 0.72)
            wypelnij(g, kolo(L.x(dx), L.y(dy), r), rgba(255, 236, 150, a))
        }

        // Płoza, kadłub, Mikołaj
        obrys(g, linia(L.p(-2, 5.6), L.p(17, 4.5)), zloto, max(1.4, 2.0 * s))
        let kadlub = wielokat([L.p(-0.5, 3.8), L.p(13.5, 2.6), L.p(16.2, -2.8), L.p(6.5, -1.4), L.p(-2.0, -4.2)])
        wypelnij(g, kadlub, zloto)
        let mk = L.p(7.5, -4)
        wypelnij(g, kolo(mk.x, mk.y, 3.6 * s), hx(0xd01832))
        let mb = L.p(8.0, -6.8)
        wypelnij(g, kolo(mb.x, mb.y, 2.3 * s), Color.white)

        // Lejce i renifery
        obrys(g, linia(L.p(14.5, -1.2), L.p(42, -5.8)), rgba(255, 242, 180, 0.82), max(0.9, 1.1 * s))
        let renifery: [(CGFloat, CGFloat)] = [(25.5, -3.2), (41.5, -6.4)]
        for (ri, rp) in renifery.enumerated() {
            renifer(g, u, L, rx: rp.0, ry: rp.1, ri: ri, zloto: zloto)
        }
    }

    static func renifer(_ g: GraphicsContext, _ u: Uklad, _ L: Lok, rx: CGFloat, ry: CGFloat, ri: Int, zloto: Color) {
        let s = u.scH
        let gallop: CGFloat = sin(u.t * 8 + CGFloat(ri) * 1.6) * 2.2 * s
        wypelnij(g, elipsa(L.x(rx), L.y(ry), 4.6 * L.sc, 2.1 * L.sc), zloto)
        let szyja = wielokat([L.p(rx + 2.5, ry - 1), L.p(rx + 5.4, ry - 5.4), L.p(rx + 8.6, ry - 4.0), L.p(rx + 4.4, ry + 0.6)])
        wypelnij(g, szyja, zloto)
        // Nogi w galopie
        let lwN: CGFloat = max(1.1, 1.5 * s)
        let n1 = L.p(rx - 6.8, ry + 4.8)
        obrys(g, linia(L.p(rx - 3.2, ry + 1.2), CGPoint(x: n1.x + gallop, y: n1.y)), zloto, lwN)
        let n2 = L.p(rx + 6.8, ry + 3.8)
        obrys(g, linia(L.p(rx + 2.8, ry + 1.0), CGPoint(x: n2.x - gallop, y: n2.y)), zloto, lwN)
        // Poroże
        obrys(g, linia(L.p(rx + 5.2, ry - 5), L.p(rx + 4.0, ry - 8.6)), zloto, max(0.9, 1.1 * s))
        // Rudolf: świecący nos
        if ri == 1 {
            let nos = L.p(rx + 8.8, ry - 4.0)
            let stopy: [Gradient.Stop] = [stC(rgba(255, 38, 68, 0.95), 0), stC(rgba(255, 38, 68, 0), 1)]
            poswiata(g, nos, 0.5, 8 * s, stopy)
            wypelnij(g, kolo(nos.x, nos.y, 2.1 * s), hx(0xff2644))
        }
    }

    /// Domek jarmarku z parującymi kubkami (Święta)
    static func straganik(_ g: GraphicsContext, _ u: Uklad) {
        let s = u.scH
        let wc: CGFloat = 38 * s
        let hc: CGFloat = 23 * s
        let x0: CGFloat = u.w - wc - 6 * s
        let ygC: CGFloat = u.yg - 3 * s
        let yt: CGFloat = ygC - hc
        wypelnij(g, prost(x0, yt, wc, hc), u.noc ? hx(0x603822) : hx(0x885230))
        wypelnij(g, prost(x0 + 4.5 * s, yt + 5.5 * s, wc - 9 * s, 9.2 * s), hx(0xffe284))
        let kubek: Color = hx(0xcc1830)
        wypelnij(g, prost(x0 + 9 * s, yt + 11.2 * s, 3.2 * s, 3.2 * s), kubek)
        wypelnij(g, prost(x0 + 16 * s, yt + 11.2 * s, 3.2 * s, 3.2 * s), kubek)

        // Para znad kubków
        let kubki: [CGFloat] = [10.6, 17.6]
        let lwP: CGFloat = max(0.9, 1.1 * s)
        for (mi, msx) in kubki.enumerated() {
            let mf: CGFloat = CGFloat(mi)
            let stY: CGFloat = fmodP(u.t * 10 + mf * 5, 9) * s
            let a: CGFloat = 0.65 * (1 - stY / (9 * s))
            let px: CGFloat = x0 + msx * s
            let ctrlX: CGFloat = px + sin(u.t * 4 + mf) * 2 * s
            var para = Path()
            para.move(to: CGPoint(x: px, y: yt + 10.5 * s - stY))
            para.addQuadCurve(to: CGPoint(x: px, y: yt + 4.5 * s - stY), control: CGPoint(x: ctrlX, y: yt + 7.5 * s - stY))
            obrys(g, para, rgba(255, 255, 255, Double(a)), lwP)
        }

        // Dach z czapą śniegu
        let dach = wielokat([
            CGPoint(x: x0 - 3.5 * s, y: yt + 0.8 * s),
            CGPoint(x: x0 + wc * 0.5, y: yt - 9.8 * s),
            CGPoint(x: x0 + wc + 3.5 * s, y: yt + 0.8 * s)
        ])
        wypelnij(g, dach, hx(0x4a261c))
        let snieg = lamana([
            CGPoint(x: x0 - 3.5 * s, y: yt + 0.5 * s),
            CGPoint(x: x0 + wc * 0.5, y: yt - 10 * s),
            CGPoint(x: x0 + wc + 3.5 * s, y: yt + 0.5 * s)
        ])
        obrys(g, snieg, Color.white, max(1.6, 2.4 * s))
    }

    /// Sylwester / Nowy Rok: poświata, cokół, kieliszki lub fontanny, napis roku
    static func noworoczne(_ g: GraphicsContext, _ u: Uklad) {
        let s = u.scH
        let cx: CGFloat = u.fgX
        let a0: Double = u.noc ? 0.48 : 0.28
        let stopy: [Gradient.Stop] = [stC(rgba(212, 175, 55, a0), 0), stC(rgba(212, 175, 55, 0), 1)]
        poswiata(g, CGPoint(x: cx, y: u.yg - 26 * s), 2, 44 * s, stopy)

        // Sylwester: „2026/2027” na szerszym cokole (Karol: „Tu rok 2026/2027 bym dał i by go było widać”)
        let szer: CGFloat = u.scena == .sylwester ? 98 : 66
        let cokol = zaokr(cx - szer / 2 * s, u.yg - 8.5 * s, szer * s, 8.5 * s, 2.5 * s)
        wypelnij(g, cokol, hx(0x0e1628))
        obrys(g, cokol, hx(0xdabe48), max(1.1, 1.4 * s))

        if u.scena == .nowyRok {
            fontanny(g, u)
        } else {
            kieliszki(g, u)
        }
        napisRoku(g, u)
    }

    /// Dwie fontanny złotych iskier i gwiazda (Nowy Rok)
    static func fontanny(_ g: GraphicsContext, _ u: Uklad) {
        let s = u.scH
        let cx: CGFloat = u.fgX
        let y0: CGFloat = u.yg - 13 * s
        let pozycje: [CGFloat] = [cx - 29 * s, cx + 29 * s]
        for (fi, fx) in pozycje.enumerated() {
            wypelnij(g, prost(fx - 2.2 * s, y0, 4.4 * s, 4.5 * s), hx(0xd4af37))
            for k in 0..<12 {
                let kf: CGFloat = CGFloat(k)
                let faza: CGFloat = u.t * 2.6 + kf * 0.19 + CGFloat(fi) * 0.4
                let uu: CGFloat = fmodP(faza, 1)
                let odchyl: CGFloat = sin(kf * 2.3 + u.t * 3) * 0.48
                let ang: CGFloat = -CGFloat.pi * 0.5 + odchyl
                let dist: CGFloat = uu * 24 * s
                let px: CGFloat = fx + cos(ang) * dist
                let py: CGFloat = y0 + sin(ang) * dist + uu * uu * 5 * s
                let r: CGFloat = 1.35 * s * (1 - uu * 0.5)
                wypelnij(g, kolo(px, py, r), rgba(255, 240, 150, Double(1 - uu)))
            }
        }
        let puls: CGFloat = 0.9 + 0.16 * sin(u.t * 4)
        gwiazda8(g, cx, u.yg - 46 * s, 11 * s, 5.5 * s, hx(0xffec8a), puls)
    }

    /// Dwa stukające się kieliszki z bąbelkami (Sylwester)
    static func kieliszki(_ g: GraphicsContext, _ u: Uklad) {
        let s = u.scH
        let cx: CGFloat = u.fgX
        let clink: CGFloat = sin(u.t * 2.8) * 1.6
        let dod: CGFloat = max(0, clink)
        let rant: Color = rgba(255, 252, 240, 0.92)
        let lw: CGFloat = max(1.0, 1.15 * s)
        let gyb: CGFloat = u.yg - 31 * s
        let gyt: CGFloat = u.yg - 52 * s
        let strony: [CGFloat] = [-1, 1]
        for side in strony {
            let gxb: CGFloat = cx + side * (11.5 - dod * 0.5) * s
            let gxt: CGFloat = cx + side * (4.2 - dod * 0.9) * s
            // Nóżka
            let stopa = CGPoint(x: gxb + side * 2.2 * s, y: gyb + 5.5 * s)
            obrys(g, linia(stopa, CGPoint(x: gxb, y: gyb)), rant, lw)
            // Czasza z szampanem
            let czasza = wielokat([
                CGPoint(x: gxb - 2.2 * s, y: gyb),
                CGPoint(x: gxt - 3.5 * s, y: gyt + 5 * s),
                CGPoint(x: gxt + 3.5 * s, y: gyt + 5 * s),
                CGPoint(x: gxb + 2.2 * s, y: gyb)
            ])
            wypelnij(g, czasza, rgba(245, 202, 78, 0.90))
            obrys(g, prost(gxt - 3.6 * s, gyt, 7.2 * s, 5 * s), rant, lw)
            // Bąbelki
            let przes: CGFloat = side > 0 ? 0.2 : 0
            for b in 0..<3 {
                let bu: CGFloat = fmodP(u.t * 1.4 + CGFloat(b) * 0.33 + przes, 1)
                let byy: CGFloat = gyb - (3 + bu * 16) * s
                wypelnij(g, kolo(gxt, byy, 1.0 * s), rgba(255, 252, 220, Double(1 - bu)))
            }
        }
        if clink > 0.6 {
            gwiazda8(g, cx, u.yg - 53.5 * s, 8 * s, 4 * s, hx(0xfff4b0), clink)
        }
    }

    /// Złoty napis roku: w styczniu bieżący, w pozostałych miesiącach następny
    static func napisRoku(_ g: GraphicsContext, _ u: Uklad) {
        let s = u.scH
        let rok: Int = u.miesiac == 1 ? u.rok : u.rok + 1
        // Sylwester: stary i nowy rok, mniejszą czcionką, żeby zmieścił się na cokole; Nowy Rok: sam nowy rok
        let sylwester: Bool = u.scena == .sylwester
        let tekst: String = sylwester ? "\(rok - 1)/\(rok)" : String(rok)
        let fs: CGFloat = sylwester ? max(13, (17 * s).rounded()) : max(16, (23 * s).rounded())
        // JS: linia bazowa w yg - 13.5scH; środek cyfr ok. 0.355 em wyżej
        let bazowa: CGFloat = u.yg - 13.5 * s
        let srodekY: CGFloat = bazowa - fs * 0.355
        let font: Font = .system(size: fs, weight: .heavy)
        let cien = Text(tekst).font(font).foregroundColor(hx(0x6c440c))
        g.draw(cien, at: CGPoint(x: u.fgX + 1.8 * s, y: srodekY + 1.8 * s), anchor: .center)
        let kol: Color = u.noc ? hx(0xfff6c6) : hx(0xf5cc44)
        let glowny = Text(tekst).font(font).foregroundColor(kol)
        g.draw(glowny, at: CGPoint(x: u.fgX, y: srodekY), anchor: .center)
    }
}

// MARK: - Płatki śniegu / złote konfetti

fileprivate extension ScenySwiateczne {
    static func platki(_ g: GraphicsContext, _ u: Uklad) {
        let s = u.scH
        let n: Int = u.w < 560 ? 56 : 84
        let konfetti: Bool = u.scena == .nowyRok
        var sciezka = Path()
        for i in 0..<n {
            if konfetti && i % 2 == 0 { continue }
            let fi: CGFloat = CGFloat(i)
            let r: CGFloat = (0.9 + CGFloat(i % 4) * 0.5) * s
            let vy: CGFloat = (14 + CGFloat(i % 5) * 6) * s
            let vx: CGFloat = (-6 + CGFloat(i % 7) * 2) * s
            let fy: CGFloat = fmodP(fi * 43 * s + u.t * vy, u.yg)
            let bazaX: CGFloat = (fi * 61).truncatingRemainder(dividingBy: u.w)
            let chwiej: CGFloat = sin(u.t * 1.6 + fi * 0.7) * 5 * s
            let fx: CGFloat = fmodP(bazaX + u.t * vx + chwiej + u.w * 4, u.w)
            // W trybie karty część płatków omija strefę temperatury
            if u.karta && i % 2 == 0 {
                let wPoziomie: Bool = fx > u.w * 0.18 && fx < u.w * 0.50
                let wPionie: Bool = fy > u.h * 0.13 && fy < u.h * 0.34
                if wPoziomie && wPionie { continue }
            }
            sciezka.addEllipse(in: CGRect(x: fx - r, y: fy - r, width: r * 2, height: r * 2))
        }
        let kol: Color = konfetti ? rgba(255, 232, 140, 0.82) : rgba(255, 255, 255, 0.84)
        wypelnij(g, sciezka, kol)
    }
}
