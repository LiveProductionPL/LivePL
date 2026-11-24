import SwiftUI

// Sceny pogody ze strony (weather-effects.js) przeniesione do SwiftUI Canvas:
// Ratusz w Lesznie na 11 Listopada i Andrzejki w Lesznie.
// Współrzędne jak na stronie: ułamki w i h, skala scH = h/276.

// MARK: - Pomocnicy wspólni (tylko ten plik)

fileprivate enum PomRA {
    /// Kolor rgba jak w canvas (składowe 0-255, alfa 0-1).
    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        return Color(.sRGB, red: r / 255.0, green: g / 255.0, blue: b / 255.0, opacity: a)
    }

    static func stop(_ c: Color, _ l: CGFloat) -> Gradient.Stop {
        return Gradient.Stop(color: c, location: l)
    }

    static func stopHex(_ hex: String, _ l: CGFloat) -> Gradient.Stop {
        return Gradient.Stop(color: Color(hex: hex), location: l)
    }

    static func prost(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> Path {
        return Path(CGRect(x: x, y: y, width: w, height: h))
    }

    static func elipsa(_ cx: CGFloat, _ cy: CGFloat, _ rx: CGFloat, _ ry: CGFloat) -> Path {
        return Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2))
    }

    /// Elipsa obrócona o kąt (radiany, jak ctx.ellipse z rotation).
    static func elipsaObrocona(_ cx: CGFloat, _ cy: CGFloat, _ rx: CGFloat, _ ry: CGFloat, _ kat: CGFloat) -> Path {
        let baza = Path(ellipseIn: CGRect(x: -rx, y: -ry, width: rx * 2, height: ry * 2))
        let tr = CGAffineTransform(translationX: cx, y: cy).rotated(by: kat)
        return baza.applying(tr)
    }

    static func kolo(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) -> Path {
        return elipsa(cx, cy, r, r)
    }

    /// Zaokrąglony prostokąt (odpowiednik _rrPath).
    static func zaokr(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ r: CGFloat) -> Path {
        let polowa: CGFloat = min(abs(w), abs(h)) / 2
        let rr: CGFloat = max(0, min(r, polowa))
        return Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: rr, style: .circular)
    }

    static func linia(_ a: CGPoint, _ b: CGPoint) -> Path {
        var p = Path()
        p.move(to: a)
        p.addLine(to: b)
        return p
    }

    static func wielokat(_ pts: [CGPoint]) -> Path {
        var p = Path()
        guard let pierwszy = pts.first else { return p }
        p.move(to: pierwszy)
        for q in pts.dropFirst() {
            p.addLine(to: q)
        }
        p.closeSubpath()
        return p
    }

    /// Gradient pionowy od y0 do y1.
    static func pionowy(_ stopy: [Gradient.Stop], _ y0: CGFloat, _ y1: CGFloat) -> GraphicsContext.Shading {
        return .linearGradient(Gradient(stops: stopy), startPoint: CGPoint(x: 0, y: y0), endPoint: CGPoint(x: 0, y: y1))
    }

    /// Gradient poziomy od x0 do x1.
    static func poziomy(_ stopy: [Gradient.Stop], _ x0: CGFloat, _ x1: CGFloat) -> GraphicsContext.Shading {
        return .linearGradient(Gradient(stops: stopy), startPoint: CGPoint(x: x0, y: 0), endPoint: CGPoint(x: x1, y: 0))
    }

    /// Gradient promienisty (createRadialGradient o wspólnym środku).
    static func promien(_ stopy: [Gradient.Stop], _ c: CGPoint, _ r0: CGFloat, _ r1: CGFloat) -> GraphicsContext.Shading {
        return .radialGradient(Gradient(stops: stopy), center: c, startRadius: r0, endRadius: r1)
    }

    /// Łuk elipsy jako łamana; kąty jak w canvas (0 = w prawo, pi/2 = w dół).
    static func dodajLuk(_ p: inout Path, _ cx: CGFloat, _ cy: CGFloat, _ rx: CGFloat, _ ry: CGFloat,
                         _ a0: Double, _ a1: Double, _ n: Int, _ nowy: Bool) {
        let kroki: Int = max(1, n)
        for k in 0...kroki {
            let u: Double = Double(k) / Double(kroki)
            let a: Double = a0 + (a1 - a0) * u
            let px: CGFloat = cx + CGFloat(cos(a)) * rx
            let py: CGFloat = cy + CGFloat(sin(a)) * ry
            let pt = CGPoint(x: px, y: py)
            if k == 0 && nowy {
                p.move(to: pt)
            } else {
                p.addLine(to: pt)
            }
        }
    }

    /// drawHill ze strony (1431-1444): fala sinus + cosinus, wypełniona do dołu.
    static func wzgorze(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, y0: CGFloat,
                        a1: CGFloat, f1: CGFloat, a2: CGFloat, f2: CGFloat, kolor: Color, faza: CGFloat) {
        let skala: CGFloat = 380 / max(380, w)
        var p = Path()
        p.move(to: CGPoint(x: 0, y: h))
        var x: CGFloat = 0
        while x <= w {
            let k1: Double = Double(x * f1 * skala + faza)
            let k2: Double = Double(x * f2 * skala - faza * 0.5)
            let s1: CGFloat = CGFloat(sin(k1)) * a1
            let s2: CGFloat = CGFloat(cos(k2)) * a2
            p.addLine(to: CGPoint(x: x, y: y0 + s1 + s2))
            x += 4
        }
        p.addLine(to: CGPoint(x: w, y: h))
        p.closeSubpath()
        ctx.fill(p, with: .color(kolor))
    }

    /// Wspólne gwiazdy scen (1412-1427): 28 migoczących punktów.
    static func gwiazdyWspolne(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: Double) {
        let horyzont: CGFloat = h * 0.58
        for i in 0..<28 {
            let fx: CGFloat = CGFloat((i * 73 + 19) % 100) / 100
            let fy: CGFloat = CGFloat((i * 41 + 7) % 100) / 100
            let sx: CGFloat = fx * w
            let sy: CGFloat = fy * horyzont * 0.82
            let predk: Double = 1.1 + Double(i % 5) * 0.3
            let fala: Double = abs(sin(t * predk + Double(i)))
            let tw: Double = 0.25 + 0.65 * fala
            let r: CGFloat = (i % 3 == 0) ? 1.4 : 0.9
            ctx.fill(kolo(sx, sy, r), with: .color(rgba(255, 250, 235, tw)))
        }
    }

    /// Przyciemnienie dolnej strefy dla kontrastu (3815-3822).
    static func kontrast(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat) {
        let y0: CGFloat = h * 0.58 - 15
        let st: [Gradient.Stop] = [
            stop(rgba(6, 10, 22, 0), 0),
            stop(rgba(6, 10, 22, 0.28), 0.35),
            stop(rgba(6, 10, 22, 0.58), 1)
        ]
        ctx.fill(prost(0, y0, w, h - y0), with: pionowy(st, y0, h))
    }
}

/// Układ lokalny Ratusza: x w prawo, y w górę od gruntu (X()/Y() ze strony).
fileprivate struct UkladRatusza {
    let cx: CGFloat
    let yb: CGFloat
    let s: CGFloat

    func x(_ d: CGFloat) -> CGFloat {
        return cx + d * s
    }

    func y(_ d: CGFloat) -> CGFloat {
        return yb - d * s
    }

    func p(_ dx: CGFloat, _ dy: CGFloat) -> CGPoint {
        return CGPoint(x: x(dx), y: y(dy))
    }

    /// fillRectXY ze strony: lewy x, górne y, prawy x, dolne y (jednostki lokalne).
    func prost(_ x1: CGFloat, _ yTop: CGFloat, _ x2: CGFloat, _ yBot: CGFloat) -> Path {
        let szer: CGFloat = (x2 - x1) * s
        let wys: CGFloat = (yTop - yBot) * s
        return Path(CGRect(x: x(x1), y: y(yTop), width: szer, height: wys))
    }

    func wielokat(_ pts: [(CGFloat, CGFloat)]) -> Path {
        var sciezka = Path()
        for (i, q) in pts.enumerated() {
            let pt = p(q.0, q.1)
            if i == 0 {
                sciezka.move(to: pt)
            } else {
                sciezka.addLine(to: pt)
            }
        }
        sciezka.closeSubpath()
        return sciezka
    }

    func linia(_ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat) -> Path {
        return PomRA.linia(p(x1, y1), p(x2, y2))
    }
}

/// Kolory Ratusza: noc (biało-czerwona iluminacja) albo dzień.
fileprivate struct PaletaRatusza {
    let cokol: Color
    let sciana: Color
    let scianaCien: Color
    let pilaster: Color
    let terakota: Color
    let dach: Color
    let helm: Color
    let helmJasny: Color
    let okno: Color

    init(noc: Bool) {
        cokol = Color(hex: noc ? "#ac1834" : "#b8a686")
        sciana = Color(hex: noc ? "#e2dedc" : "#e7dcc9")
        scianaCien = Color(hex: noc ? "#c4bcc0" : "#d2c6b1")
        pilaster = Color(hex: noc ? "#fcfafc" : "#f9f7f2")
        terakota = Color(hex: noc ? "#d2203e" : "#a84e3c")
        dach = Color(hex: noc ? "#1a202e" : "#383e48")
        helm = Color(hex: noc ? "#181d2a" : "#2a2f38")
        helmJasny = Color(hex: noc ? "#343c4e" : "#444c58")
        okno = noc ? PomRA.rgba(255, 218, 128, 0.94) : Color(hex: "#3a4452")
    }
}

// MARK: - Ratusz na 11 Listopada

/// Leszno, Ratusz na 11 Listopada (weather-effects.js 2236-2719).
enum ScenaRatusza {
    static func rysuj(_ ctx: inout GraphicsContext, size: CGSize, t: Double, warunki: WarunkiSceny) {
        let w: CGFloat = size.width
        let h: CGFloat = size.height
        guard w > 1, h > 1 else { return }
        let noc: Bool = warunki.noc
        let yRynek: CGFloat = h * 0.645
        // ratusz bliżej i większy na szerokiej karcie aplikacji (PlanRatusza w ScenySwiateczne.swift)
        let yBase: CGFloat = PlanRatusza.podstawa(w, h)
        let scR: CGFloat = (h / 276) * 0.96 * PlanRatusza.skala(w, h)
        let cxR: CGFloat = (w < 560) ? (w * 0.666) : (w * 0.68)
        let u = UkladRatusza(cx: cxR, yb: yBase, s: scR)
        let kol = PaletaRatusza(noc: noc)

        niebo(&ctx, w: w, h: h, warunki: warunki)
        // scena specjalna: gwiazdy zawsze w nocy
        if noc {
            PomRA.gwiazdyWspolne(&ctx, w: w, h: h, t: t)
        }
        if !noc && !warunki.burzowo && !warunki.deszczowo {
            oblok(&ctx, w: w, h: h, t: t, yRynek: yRynek)
        }
        bruk(&ctx, w: w, h: h, noc: noc, yRynek: yRynek, u: u)
        kamienice(&ctx, w: w, noc: noc, yRynek: yRynek, u: u)
        if noc {
            reflektory(&ctx, t: t, u: u)
        }
        korpus(&ctx, noc: noc, u: u, k: kol)
        portalIDach(&ctx, u: u, k: kol)
        wieza(&ctx, u: u, k: kol)
        zegar(&ctx, noc: noc, u: u, k: kol)
        helm(&ctx, noc: noc, u: u, k: kol)
        flagi(&ctx, w: w, h: h, t: t, u: u)
        PomRA.kontrast(&ctx, w: w, h: h)
    }

    // Niebo: wiersz 4 tabeli (noc / burza / deszcz / dzień), od 0 do 0.65h.
    fileprivate static func niebo(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, warunki: WarunkiSceny) {
        let dol: CGFloat = h * 0.65
        let st: [Gradient.Stop]
        if warunki.noc {
            st = [PomRA.stopHex("#040814", 0), PomRA.stopHex("#0b152c", 0.5), PomRA.stopHex("#182544", 1)]
        } else if warunki.burzowo {
            st = [PomRA.stopHex("#1c2436", 0), PomRA.stopHex("#334158", 0.6), PomRA.stopHex("#5d6d82", 1)]
        } else if warunki.deszczowo {
            st = [PomRA.stopHex("#334862", 0), PomRA.stopHex("#58728c", 0.6), PomRA.stopHex("#94acc0", 1)]
        } else {
            st = [PomRA.stopHex("#1a5296", 0), PomRA.stopHex("#2f78c4", 0.38),
                  PomRA.stopHex("#68ace6", 0.75), PomRA.stopHex("#d8ecfa", 1)]
        }
        ctx.fill(PomRA.prost(0, 0, w, dol + 4), with: PomRA.pionowy(st, 0, dol))
    }

    // Jasny obłok pierzasty w pogodny dzień.
    fileprivate static func oblok(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: Double, yRynek: CGFloat) {
        let dryf: CGFloat = CGFloat(sin(t * 0.18)) * 10
        let c = CGPoint(x: w * 0.32 + dryf, y: h * 0.22)
        let st: [Gradient.Stop] = [
            PomRA.stop(PomRA.rgba(255, 255, 255, 0.22), 0),
            PomRA.stop(PomRA.rgba(255, 255, 255, 0), 1)
        ]
        ctx.fill(PomRA.prost(0, 0, w, yRynek), with: PomRA.promien(st, c, 0, w * 0.22))
    }

    // Płyta Rynku, nocne odbicia iluminacji i wachlarzowe łuki bruku.
    fileprivate static func bruk(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, noc: Bool,
                                 yRynek: CGFloat, u: UkladRatusza) {
        let s: CGFloat = u.s
        let st: [Gradient.Stop]
        if noc {
            st = [PomRA.stopHex("#151c2e", 0), PomRA.stopHex("#0e1422", 0.45), PomRA.stopHex("#080c16", 1)]
        } else {
            st = [PomRA.stopHex("#9197a2", 0), PomRA.stopHex("#747b87", 0.45), PomRA.stopHex("#505662", 1)]
        }
        let wysBruku: CGFloat = h - yRynek
        ctx.fill(PomRA.prost(0, yRynek, w, wysBruku), with: PomRA.pionowy(st, yRynek, h))

        if noc {
            let cR = CGPoint(x: u.cx - 10 * s, y: u.yb + 22 * s)
            let stR: [Gradient.Stop] = [
                PomRA.stop(PomRA.rgba(220, 24, 60, 0.34), 0),
                PomRA.stop(PomRA.rgba(220, 24, 60, 0), 1)
            ]
            let obszarR = PomRA.prost(u.cx - 65 * s, yRynek, 130 * s, wysBruku)
            ctx.fill(obszarR, with: PomRA.promien(stR, cR, 2, 48 * s))

            let cW = CGPoint(x: u.cx + 14 * s, y: u.yb + 16 * s)
            let stW: [Gradient.Stop] = [
                PomRA.stop(PomRA.rgba(255, 245, 235, 0.20), 0),
                PomRA.stop(PomRA.rgba(255, 245, 235, 0), 1)
            ]
            let obszarW = PomRA.prost(u.cx - 30 * s, yRynek, 90 * s, wysBruku)
            ctx.fill(obszarW, with: PomRA.promien(stW, cW, 2, 36 * s))
        }

        // łuki: przednia połowa elips, 0.06pi..0.94pi
        let kolorLuku: Color = noc ? PomRA.rgba(255, 255, 255, 0.045) : PomRA.rgba(255, 255, 255, 0.12)
        let grub: CGFloat = max(0.7, 0.8 * s)
        let cyLuku: CGFloat = u.yb + 4 * s
        let a0: Double = 0.06 * Double.pi
        let a1: Double = 0.94 * Double.pi
        var rad: CGFloat = 28
        while rad < 260 {
            var luk = Path()
            let rx: CGFloat = rad * 1.45 * s
            let ry: CGFloat = rad * 0.42 * s
            PomRA.dodajLuk(&luk, u.cx, cyLuku, rx, ry, a0, a1, 32, true)
            ctx.stroke(luk, with: .color(kolorLuku), lineWidth: grub)
            rad += 20
        }
    }

    // Paleta kamienicy: ściana w dzień, dach w dzień, ściana w nocy (rgb).
    fileprivate static func paletaKamienicy(_ i: Int) -> (String, String, Double, Double, Double) {
        switch i % 6 {
        case 0: return ("#d6e0e4", "#444c58", 30, 34, 38)
        case 1: return ("#e8c68a", "#5c4034", 34, 30, 22)
        case 2: return ("#a8d4cc", "#3a444e", 22, 32, 34)
        case 3: return ("#ece0d0", "#9c4836", 34, 32, 30)
        case 4: return ("#e2b6bc", "#4e3e48", 34, 26, 30)
        default: return ("#ded4c4", "#404652", 32, 30, 28)
        }
    }

    // Pierzeje kamieniczek wokół Rynku z przerwą na Ratusz.
    fileprivate static func kamienice(_ ctx: inout GraphicsContext, w: CGFloat, noc: Bool,
                                      yRynek: CGFloat, u: UkladRatusza) {
        let s: CGFloat = u.s
        let hwBase: CGFloat = max(28, 34 * s)
        let gapL: CGFloat = u.cx - 43 * s
        let gapR: CGFloat = u.cx + 43 * s
        var hx: CGFloat = 3 * s
        var idx: Int = 0
        while hx < w - 12 * s && idx < 120 {
            if hx + hwBase * 0.65 > gapL && hx < gapR {
                hx = gapR + 3 * s
                if hx >= w - 12 * s { break }
            }
            let zmiana: CGFloat = CGFloat((idx % 3) - 1) * 3 * s
            var hw: CGFloat = hwBase + zmiana
            if hx < gapL && hx + hw > gapL {
                hw = max(18 * s, gapL - hx - 1 * s)
            }
            if hx + hw > w - 2 * s {
                hw = w - 2 * s - hx
            }
            if hw < 16 * s { break }
            kamienica(&ctx, x: hx, hw: hw, idx: idx, noc: noc, yRynek: yRynek, s: s)
            hx += hw
            idx += 1
        }
    }

    // Jedna kamienica: ściana, dach/szczyt, gzyms, okna, flaga, lipa.
    fileprivate static func kamienica(_ ctx: inout GraphicsContext, x: CGFloat, hw: CGFloat, idx: Int,
                                      noc: Bool, yRynek: CGFloat, s: CGFloat) {
        let hh: CGFloat = (23.5 + CGFloat(idx % 3) * 1.4) * s
        let hy: CGFloat = yRynek - hh
        let pal = paletaKamienicy(idx)
        let sciana: Color = noc ? PomRA.rgba(pal.2, pal.3, pal.4, 1) : Color(hex: pal.0)
        let dach: Color = noc ? Color(hex: "#0e1320") : Color(hex: pal.1)
        let obrys: Color = noc ? PomRA.rgba(55, 68, 94, 0.82) : PomRA.rgba(250, 250, 252, 0.92)

        let bryla = PomRA.prost(x, hy, hw, hh)
        ctx.fill(bryla, with: .color(sciana))
        ctx.stroke(bryla, with: .color(PomRA.rgba(0, 0, 0, 0.16)), lineWidth: 0.8)

        // dach i barokowy szczyt (co druga)
        if idx % 2 == 1 {
            let yDach: CGFloat = hy - 6.8 * s
            let d1 = CGPoint(x: x, y: hy)
            let d2 = CGPoint(x: x + hw * 0.18, y: yDach)
            let d3 = CGPoint(x: x + hw * 0.82, y: yDach)
            let d4 = CGPoint(x: x + hw, y: hy)
            ctx.fill(PomRA.wielokat([d1, d2, d3, d4]), with: .color(dach))

            let ySzczyt: CGFloat = hy - 9.2 * s
            let g1 = CGPoint(x: x + hw * 0.25, y: hy)
            let g2 = CGPoint(x: x + hw * 0.50, y: ySzczyt)
            let g3 = CGPoint(x: x + hw * 0.75, y: hy)
            let szczyt = PomRA.wielokat([g1, g2, g3])
            ctx.fill(szczyt, with: .color(sciana))
            ctx.stroke(szczyt, with: .color(obrys), lineWidth: max(0.8, 0.8 * s))
        } else {
            let yDach: CGFloat = hy - 6.2 * s
            let d1 = CGPoint(x: x, y: hy)
            let d2 = CGPoint(x: x + 4 * s, y: yDach)
            let d3 = CGPoint(x: x + hw - 4 * s, y: yDach)
            let d4 = CGPoint(x: x + hw, y: hy)
            ctx.fill(PomRA.wielokat([d1, d2, d3, d4]), with: .color(dach))
        }

        // gzyms
        let gzyms = PomRA.linia(CGPoint(x: x, y: hy), CGPoint(x: x + hw, y: hy))
        ctx.stroke(gzyms, with: .color(obrys), lineWidth: max(0.9, 1.0 * s))

        // dwa rzędy okien; wzór zapalonych okien z numeru okna (zamiast round(wx))
        let krok: CGFloat = 8 * s
        let oknoW: CGFloat = 3.3 * s
        let oknoH: CGFloat = 5.0 * s
        let koniec: CGFloat = x + hw - 5.5 * s
        for r in 0..<2 {
            let wy: CGFloat = hy + (5 + CGFloat(r) * 8.5) * s
            var wx: CGFloat = x + 4.5 * s
            var kolumna: Int = 0
            while wx < koniec && kolumna < 40 {
                if noc {
                    let wzor: Int = (kolumna * 7 + r * 3 + idx * 5) % 4
                    let kolorOkna: Color = (wzor != 0) ? PomRA.rgba(255, 214, 120, 0.82) : Color(hex: "#161e30")
                    ctx.fill(PomRA.prost(wx, wy, oknoW, oknoH), with: .color(kolorOkna))
                } else {
                    let ramka = PomRA.prost(wx - 0.5 * s, wy - 0.5 * s, 4.3 * s, 6.0 * s)
                    ctx.fill(ramka, with: .color(obrys))
                    ctx.fill(PomRA.prost(wx, wy, oknoW, oknoH), with: .color(PomRA.rgba(62, 76, 94, 0.92)))
                }
                wx += krok
                kolumna += 1
            }
        }

        // pionowa biało-czerwona flaga na elewacji
        let bfx: CGFloat = x + hw * 0.5
        let bfy: CGFloat = hy + 10.8 * s
        ctx.fill(PomRA.prost(bfx - 1.3 * s, bfy, 1.25 * s, 7.2 * s), with: .color(PomRA.rgba(255, 255, 255, 0.94)))
        ctx.fill(PomRA.prost(bfx - 0.05 * s, bfy, 1.25 * s, 7.2 * s), with: .color(PomRA.rgba(220, 20, 60, 0.94)))

        // lipa (co druga kamienica)
        if idx % 2 == 0 {
            let tx: CGFloat = x + hw * 0.82
            let ty: CGFloat = yRynek - 5 * s
            let pien = PomRA.linia(CGPoint(x: tx, y: yRynek), CGPoint(x: tx, y: ty + 2 * s))
            ctx.stroke(pien, with: .color(Color(hex: "#372a22")), lineWidth: max(1, 1.4 * s))
            let korona: Color = noc ? PomRA.rgba(22, 38, 34, 0.96) : PomRA.rgba(78, 108, 64, 0.96)
            ctx.fill(PomRA.elipsa(tx, ty - 1.5 * s, 6.4 * s, 4.5 * s), with: .color(korona))
            let blask: Color = noc ? PomRA.rgba(32, 52, 44, 0.96) : PomRA.rgba(106, 136, 76, 0.96)
            ctx.fill(PomRA.elipsa(tx - 1.0 * s, ty - 2.6 * s, 4.2 * s, 3.6 * s), with: .color(blask))
        }
    }

    // Nocne smugi reflektorów i biało-czerwona łuna za Ratuszem.
    fileprivate static func reflektory(_ ctx: inout GraphicsContext, t: Double, u: UkladRatusza) {
        let s: CGFloat = u.s
        let sway: CGFloat = CGFloat(sin(t * 0.55)) * 9 * s
        let y20: CGFloat = u.y(20)
        let y25: CGFloat = u.y(25)

        let b1 = CGPoint(x: u.x(-26), y: y20)
        let b2 = CGPoint(x: u.x(-66) + sway, y: 0)
        let b3 = CGPoint(x: u.x(-22) + sway, y: 0)
        let b4 = CGPoint(x: u.x(-6), y: y20)
        let stB: [Gradient.Stop] = [
            PomRA.stop(PomRA.rgba(255, 255, 255, 0.22), 0),
            PomRA.stop(PomRA.rgba(255, 255, 255, 0.02), 1)
        ]
        ctx.fill(PomRA.wielokat([b1, b2, b3, b4]), with: PomRA.pionowy(stB, y25, 0))

        let r1 = CGPoint(x: u.x(4), y: y20)
        let r2 = CGPoint(x: u.x(14) - sway, y: 0)
        let r3 = CGPoint(x: u.x(60) - sway, y: 0)
        let r4 = CGPoint(x: u.x(28), y: y20)
        let stR: [Gradient.Stop] = [
            PomRA.stop(PomRA.rgba(220, 20, 60, 0.34), 0),
            PomRA.stop(PomRA.rgba(220, 20, 60, 0.03), 1)
        ]
        ctx.fill(PomRA.wielokat([r1, r2, r3, r4]), with: PomRA.pionowy(stR, y25, 0))

        let cC = CGPoint(x: u.cx, y: u.y(52))
        let rC: CGFloat = 76 * s
        let stC: [Gradient.Stop] = [
            PomRA.stop(PomRA.rgba(220, 25, 60, 0.38), 0),
            PomRA.stop(PomRA.rgba(220, 25, 60, 0), 1)
        ]
        ctx.fill(PomRA.kolo(cC.x, cC.y, rC), with: PomRA.promien(stC, cC, 4, rC))

        let cW = CGPoint(x: u.cx, y: u.y(96))
        let rW: CGFloat = 48 * s
        let stW: [Gradient.Stop] = [
            PomRA.stop(PomRA.rgba(255, 245, 235, 0.30), 0),
            PomRA.stop(PomRA.rgba(255, 245, 235, 0), 1)
        ]
        ctx.fill(PomRA.kolo(cW.x, cW.y, rW), with: PomRA.promien(stW, cW, 2, rW))
    }

    // Korpus Ratusza: cień, ściany, cokół, gzymsy, pilastry, okna, kartusz.
    fileprivate static func korpus(_ ctx: inout GraphicsContext, noc: Bool, u: UkladRatusza, k: PaletaRatusza) {
        let s: CGFloat = u.s
        let bw: CGFloat = 39

        let cien = PomRA.elipsa(u.cx, u.y(-0.5), (bw + 4) * s, 3.5 * s)
        ctx.fill(cien, with: .color(PomRA.rgba(0, 0, 0, 0.26)))

        ctx.fill(u.prost(-bw, 34, bw, 11), with: .color(k.sciana))
        ctx.fill(u.prost(-bw, 34, -24, 11), with: .color(k.scianaCien))
        ctx.fill(u.prost(-bw, 11, bw, 0), with: .color(k.cokol))
        if noc {
            let st: [Gradient.Stop] = [
                PomRA.stop(PomRA.rgba(210, 32, 68, 0.68), 0),
                PomRA.stop(PomRA.rgba(210, 32, 68, 0), 1)
            ]
            let pas = PomRA.prost(u.x(-bw), u.y(18), bw * 2 * s, 7 * s)
            ctx.fill(pas, with: PomRA.pionowy(st, u.y(11), u.y(18)))
        }

        // gzymsy kordonowe
        ctx.fill(u.prost(-bw - 0.5, 11.8, bw + 0.5, 10.5), with: .color(k.pilaster))
        ctx.fill(u.prost(-bw - 0.5, 1.4, bw + 0.5, 0), with: .color(k.pilaster))

        // pilastry wielkiego porządku
        let pilastry: [CGFloat] = [-38, -25, -13.5, -5.8, 5.8, 13.5, 25, 38]
        let dolnyPil: Color = noc ? PomRA.rgba(238, 192, 202, 0.86) : PomRA.rgba(242, 238, 228, 0.92)
        for px in pilastry {
            ctx.fill(u.prost(px - 1.5, 34, px + 1.5, 11.5), with: .color(k.pilaster))
            ctx.fill(u.prost(px - 1.25, 10.8, px + 1.25, 1.2), with: .color(dolnyPil))
        }

        // okna w białych opaskach (w nocy świecą)
        let okna: [CGFloat] = [-31.5, -19.2, -9.6, 9.6, 19.2, 31.5]
        let oknoCokolu: Color = noc ? k.okno : Color(hex: "#2d2a28")
        for wx in okna {
            ctx.fill(u.prost(wx - 2.4, 31.0, wx + 2.4, 24.8), with: .color(k.pilaster))
            ctx.fill(u.prost(wx - 1.6, 30.1, wx + 1.6, 25.6), with: .color(k.okno))
            ctx.fill(u.prost(wx - 2.4, 21.2, wx + 2.4, 14.8), with: .color(k.pilaster))
            ctx.fill(u.prost(wx - 1.6, 20.3, wx + 1.6, 15.6), with: .color(k.okno))
            ctx.fill(u.prost(wx - 1.7, 7.8, wx + 1.7, 3.8), with: .color(k.pilaster))
            ctx.fill(u.prost(wx - 1.1, 7.2, wx + 1.1, 4.4), with: .color(oknoCokolu))
        }

        // kartusz herbowy nad portalem
        let yKart: CGFloat = u.y(21.15)
        ctx.fill(PomRA.elipsa(u.cx, yKart, 2.2 * s, 2.35 * s), with: .color(k.pilaster))
        ctx.fill(PomRA.elipsa(u.cx, yKart, 1.2 * s, 1.35 * s), with: .color(Color(hex: "#c33034")))
    }

    // Portal, drzwi, schody z balustradą, dach z attyką tralkową.
    fileprivate static func portalIDach(_ ctx: inout GraphicsContext, u: UkladRatusza, k: PaletaRatusza) {
        let s: CGFloat = u.s

        // naczółek i obramienie portalu
        let naczolek: [(CGFloat, CGFloat)] = [(-5.6, 13.2), (0, 17.2), (5.6, 13.2)]
        ctx.fill(u.wielokat(naczolek), with: .color(k.pilaster))
        ctx.fill(u.prost(-3.3, 13.2, 3.3, 2.5), with: .color(k.pilaster))

        // drzwi z łukiem
        var drzwi = Path()
        PomRA.dodajLuk(&drzwi, u.cx, u.y(7.2), 2.2 * s, 2.2 * s, Double.pi, 2 * Double.pi, 14, true)
        drzwi.addLine(to: u.p(2.2, 2.5))
        drzwi.addLine(to: u.p(-2.2, 2.5))
        drzwi.closeSubpath()
        ctx.fill(drzwi, with: .color(Color(hex: "#40281c")))

        // schody i kuta balustrada
        let schody: [(CGFloat, CGFloat)] = [(-8.5, 0), (-4.0, 3.0), (4.0, 3.0), (8.5, 0)]
        ctx.fill(u.wielokat(schody), with: .color(Color(hex: "#d8dce2")))
        var porecze = u.linia(-8.5, 1.2, -4.0, 4.2)
        porecze.addPath(u.linia(8.5, 1.2, 4.0, 4.2))
        ctx.stroke(porecze, with: .color(Color(hex: "#262a30")), lineWidth: max(0.8, 0.85 * s))

        // dach czterospadowy, terakotowy fryz, attyka
        let dach: [(CGFloat, CGFloat)] = [(-37, 40.2), (-30, 46.5), (30, 46.5), (37, 40.2)]
        ctx.fill(u.wielokat(dach), with: .color(k.dach))
        ctx.fill(u.prost(-40, 35.8, 40, 34), with: .color(k.terakota))
        ctx.fill(u.prost(-39.5, 40.2, 39.5, 35.8), with: .color(k.pilaster))
        var bx: CGFloat = -35
        while bx <= 35 {
            ctx.fill(u.prost(bx - 0.9, 39.0, bx + 0.9, 36.8), with: .color(k.scianaCien))
            bx += 4
        }
    }

    // Wieża: dolna kondygnacja z oknem łukowym i kondygnacja zegarowa.
    fileprivate static func wieza(_ ctx: inout GraphicsContext, u: UkladRatusza, k: PaletaRatusza) {
        let s: CGFloat = u.s

        ctx.fill(u.prost(-12.5, 62, 12.5, 40), with: .color(k.sciana))
        ctx.fill(u.prost(-12.5, 62, -10.3, 40), with: .color(k.pilaster))
        ctx.fill(u.prost(10.3, 62, 12.5, 40), with: .color(k.pilaster))

        var luk = Path()
        PomRA.dodajLuk(&luk, u.cx, u.y(52.5), 3.4 * s, 3.4 * s, Double.pi, 2 * Double.pi, 16, true)
        luk.closeSubpath()
        ctx.fill(luk, with: .color(k.pilaster))
        ctx.fill(u.prost(-3.2, 52.5, 3.2, 45), with: .color(k.pilaster))
        ctx.fill(u.prost(-1.9, 53.2, 1.9, 46), with: .color(k.okno))
        ctx.fill(u.prost(-14, 64, 14, 61.8), with: .color(k.pilaster))

        ctx.fill(u.prost(-11.8, 85, 11.8, 64), with: .color(k.sciana))
        ctx.fill(u.prost(-11.8, 85, -9.8, 64), with: .color(k.pilaster))
        ctx.fill(u.prost(9.8, 85, 11.8, 64), with: .color(k.pilaster))
        ctx.fill(u.prost(-2.4, 70.5, 2.4, 66.2), with: .color(k.pilaster))
        ctx.fill(u.prost(-1.4, 69.8, 1.4, 66.8), with: .color(k.okno))
    }

    // Zegar ratuszowy z prawdziwym czasem lokalnym.
    fileprivate static func zegar(_ ctx: inout GraphicsContext, noc: Bool, u: UkladRatusza, k: PaletaRatusza) {
        let s: CGFloat = u.s
        let srodek: CGPoint = u.p(0, 77.5)
        ctx.fill(PomRA.kolo(srodek.x, srodek.y, 6.0 * s), with: .color(k.pilaster))
        let tarcza: Color = Color(hex: noc ? "#ffecaf" : "#242830")
        ctx.fill(PomRA.kolo(srodek.x, srodek.y, 5.2 * s), with: .color(tarcza))

        let teraz = Date()
        let kal = Calendar.current
        let godz: Double = Double(kal.component(.hour, from: teraz) % 12)
        let minuty: Double = Double(kal.component(.minute, from: teraz))
        let sekundy: Double = Double(kal.component(.second, from: teraz))
        let katMin: Double = ((minuty + sekundy / 60) / 60) * Double.pi * 2 - Double.pi * 0.5
        let katGodz: Double = ((godz + minuty / 60) / 12) * Double.pi * 2 - Double.pi * 0.5

        let wskazowki: Color = Color(hex: noc ? "#1e1814" : "#eec65f")
        let dlG: CGFloat = 2.8 * s
        let dlM: CGFloat = 4.0 * s
        let koniecG = CGPoint(x: srodek.x + CGFloat(cos(katGodz)) * dlG, y: srodek.y + CGFloat(sin(katGodz)) * dlG)
        let koniecM = CGPoint(x: srodek.x + CGFloat(cos(katMin)) * dlM, y: srodek.y + CGFloat(sin(katMin)) * dlM)
        ctx.stroke(PomRA.linia(srodek, koniecG), with: .color(wskazowki),
                   style: StrokeStyle(lineWidth: max(1.0, 1.1 * s), lineCap: .round))
        ctx.stroke(PomRA.linia(srodek, koniecM), with: .color(wskazowki),
                   style: StrokeStyle(lineWidth: max(0.8, 0.9 * s), lineCap: .round))
    }

    // Galeria, bęben, dwuczęściowy hełm z latarnią, cebula, kula i iglica.
    fileprivate static func helm(_ ctx: inout GraphicsContext, noc: Bool, u: UkladRatusza, k: PaletaRatusza) {
        let s: CGFloat = u.s
        let kuta: Color = Color(hex: "#2d323a")

        // galeria widokowa
        ctx.fill(u.prost(-13.6, 86.8, 13.6, 85), with: .color(k.pilaster))
        ctx.fill(u.prost(-13, 89.5, 13, 88.6), with: .color(kuta))
        var tralki = Path()
        var rx: CGFloat = -12
        while rx <= 12 {
            tralki.addPath(u.linia(rx, 86.8, rx, 89.2))
            rx += 2
        }
        ctx.stroke(tralki, with: .color(kuta), style: StrokeStyle(lineWidth: max(0.6, 0.65 * s), lineCap: .round))

        // ośmioboczny bęben
        ctx.fill(u.prost(-7.5, 97, 7.5, 86.8), with: .color(k.sciana))
        ctx.fill(u.prost(-7.5, 97, -3.6, 86.8), with: .color(k.pilaster))
        ctx.fill(u.prost(3.6, 97, 7.5, 86.8), with: .color(k.scianaCien))
        ctx.fill(u.prost(-8.0, 97.8, 8.0, 96.4), with: .color(k.pilaster))
        ctx.fill(u.prost(-2.1, 95, 2.1, 89.5), with: .color(Color(hex: "#343840")))

        // dolny hełm z refleksem
        let dolny: [(CGFloat, CGFloat)] = [
            (-7.2, 97.8), (-9.2, 102.5), (-8.4, 107.0), (-5.2, 111.5),
            (5.2, 111.5), (8.4, 107.0), (9.2, 102.5), (7.2, 97.8)
        ]
        ctx.fill(u.wielokat(dolny), with: .color(k.helm))
        let refleks: [(CGFloat, CGFloat)] = [(-3.5, 98), (-5.5, 103), (-3.0, 111), (-1.0, 111), (-2.2, 103), (-1.0, 98)]
        ctx.fill(u.wielokat(refleks), with: .color(k.helmJasny))

        // ażurowa latarnia z dzwonem (w nocy rozświetlona)
        if noc {
            ctx.fill(u.prost(-4.2, 120.8, 4.2, 112), with: .color(PomRA.rgba(255, 215, 115, 0.82)))
        }
        var slupki = Path()
        let pozycje: [CGFloat] = [-4.5, -1.6, 1.6, 4.5]
        for lx in pozycje {
            slupki.addPath(u.linia(lx, 111.5, lx, 121.2))
        }
        ctx.stroke(slupki, with: .color(k.helm), style: StrokeStyle(lineWidth: max(1.0, 1.15 * s), lineCap: .round))
        let dzwon: [(CGFloat, CGFloat)] = [(-1.6, 115.5), (0, 119.2), (1.6, 115.5)]
        ctx.fill(u.wielokat(dzwon), with: .color(Color(hex: "#4b412d")))
        ctx.fill(u.prost(-5.6, 122.2, 5.6, 120.8), with: .color(k.helm))

        // górna cebula
        let cebula: [(CGFloat, CGFloat)] = [
            (-4.8, 122.2), (-6.0, 126.0), (-4.2, 131.0), (-1.6, 137.0), (0, 143.0),
            (1.6, 137.0), (4.2, 131.0), (6.0, 126.0), (4.8, 122.2)
        ]
        ctx.fill(u.wielokat(cebula), with: .color(k.helm))
        ctx.stroke(u.linia(-1.5, 123, -0.4, 138), with: .color(k.helmJasny),
                   style: StrokeStyle(lineWidth: max(0.8, 1.0 * s), lineCap: .round))

        // kula i iglica
        let kula: CGPoint = u.p(0, 143.8)
        ctx.fill(PomRA.kolo(kula.x, kula.y, 1.8 * s), with: .color(Color(hex: noc ? "#c3a85c" : "#48443a")))
        ctx.stroke(u.linia(0, 145.6, 0, 153.5), with: .color(Color(hex: noc ? "#d7c37d" : "#3c4048")),
                   style: StrokeStyle(lineWidth: max(0.9, 1.0 * s), lineCap: .round))
    }

    // Trzy maszty przy schodach i duża flaga RP na pierwszym planie.
    fileprivate static func flagi(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: Double, u: UkladRatusza) {
        let s: CGFloat = u.s
        let yb: CGFloat = u.yb
        let szerM: CGFloat = 7.8 * s
        let wysM: CGFloat = 4.5 * s
        let dolM: CGFloat = yb + 2 * s
        flaga(&ctx, t: t, s: s, maszt: u.cx - 17 * s, gora: yb - 17 * s, dol: dolM, szer: szerM, wys: wysM, przechyl: 0, faza: 0.0)
        flaga(&ctx, t: t, s: s, maszt: u.cx - 13 * s, gora: yb - 19 * s, dol: dolM, szer: szerM, wys: wysM, przechyl: 0, faza: 0.9)
        flaga(&ctx, t: t, s: s, maszt: u.cx - 9 * s, gora: yb - 17 * s, dol: dolM, szer: szerM, wys: wysM, przechyl: 0, faza: 1.8)

        let xDuzej: CGFloat = (w < 560) ? (40 * (w / 380)) : (w * 0.085)
        flaga(&ctx, t: t, s: s, maszt: xDuzej, gora: yb - 42 * s, dol: h - 18 * s,
              szer: 30 * s, wys: 17 * s, przechyl: -1.5 * s, faza: 0.4)
    }

    // Powiewająca flaga biało-czerwona (drawWavingPolishFlag, 2244-2289): 18 pasków.
    fileprivate static func flaga(_ ctx: inout GraphicsContext, t: Double, s: CGFloat, maszt: CGFloat,
                                  gora: CGFloat, dol: CGFloat, szer: CGFloat, wys: CGFloat,
                                  przechyl: CGFloat, faza: Double) {
        let szczytX: CGFloat = maszt + przechyl
        let drzewce = PomRA.linia(CGPoint(x: maszt, y: dol), CGPoint(x: szczytX, y: gora))
        ctx.stroke(drzewce, with: .color(PomRA.rgba(240, 244, 250, 0.96)),
                   style: StrokeStyle(lineWidth: max(1.4, 1.9 * s), lineCap: .round))
        ctx.fill(PomRA.kolo(szczytX, gora, max(1.5, 2.0 * s)), with: .color(Color(hex: "#f5d782")))

        let kroki: Int = 18
        let py0: CGFloat = gora + 1.8 * s
        let fala: Double = t * 3.2 + faza
        for i in 0..<kroki {
            let u0: Double = Double(i) / Double(kroki)
            let u1: Double = Double(i + 1) / Double(kroki)
            let fx0: CGFloat = szczytX + CGFloat(u0) * szer
            let fx1: CGFloat = szczytX + CGFloat(u1) * szer + 0.35
            let arg0: Double = u0 * Double.pi * 2.2 - fala
            let arg1: Double = u1 * Double.pi * 2.2 - fala
            let amp0: Double = 2.5 * u0 + 0.35
            let amp1: Double = 2.5 * u1 + 0.35
            let w0: CGFloat = CGFloat(sin(arg0) * amp0) * s
            let w1: CGFloat = CGFloat(sin(arg1) * amp1) * s
            let sh: Double = 1.0 + 0.12 * cos(arg0)

            let yT0: CGFloat = py0 + w0
            let yT1: CGFloat = py0 + w1
            let yM0: CGFloat = yT0 + 0.5 * wys
            let yM1: CGFloat = yT1 + 0.5 * wys
            let yB0: CGFloat = yT0 + wys
            let yB1: CGFloat = yT1 + wys

            let wr: Double = min(255, (248 * sh).rounded())
            let wg: Double = min(255, (250 * sh).rounded())
            let wb: Double = min(255, (253 * sh).rounded())
            let bialy = PomRA.wielokat([CGPoint(x: fx0, y: yT0), CGPoint(x: fx1, y: yT1),
                                        CGPoint(x: fx1, y: yM1), CGPoint(x: fx0, y: yM0)])
            ctx.fill(bialy, with: .color(PomRA.rgba(wr, wg, wb, 0.98)))

            let rr: Double = min(255, (220 * sh).rounded())
            let rg: Double = min(255, (20 * sh).rounded())
            let rb: Double = min(255, (58 * sh).rounded())
            let czerwony = PomRA.wielokat([CGPoint(x: fx0, y: yM0), CGPoint(x: fx1, y: yM1),
                                           CGPoint(x: fx1, y: yB1), CGPoint(x: fx0, y: yB0)])
            ctx.fill(czerwony, with: .color(PomRA.rgba(rr, rg, rb, 0.98)))
        }
    }
}

// MARK: - Andrzejki

/// Andrzejki w Lesznie (weather-effects.js 2823-3045).
enum ScenaAndrzejek {
    static func rysuj(_ ctx: inout GraphicsContext, size: CGSize, t: Double, warunki: WarunkiSceny) {
        let w: CGFloat = size.width
        let h: CGFloat = size.height
        guard w > 1, h > 1 else { return }
        let noc: Bool = warunki.noc
        let bezRuchu: Bool = warunki.bezRuchu
        // przy ograniczonym ruchu strona zamraża scenę (ta = 2.2)
        let ta: Double = bezRuchu ? 2.2 : t
        let scH: CGFloat = h / 276
        let karta: Bool = warunki.karta
        // linia podłogi: w karcie tuż nad kartą daty
        // karta Startu: wyżej, nad kolumnami i plakietką z datą (jak sceny świąteczne)
        let ygKarty: CGFloat = h * 0.79
        let yg: CGFloat = karta ? ygKarty : (h * 0.755)

        niebo(&ctx, w: w, h: h, noc: noc)
        if noc {
            PomRA.gwiazdyWspolne(&ctx, w: w, h: h, t: bezRuchu ? 0 : t)
        }
        tlo(&ctx, w: w, h: h, noc: noc)
        gwiazdy(&ctx, w: w, h: h, ta: ta, noc: noc, bezRuchu: bezRuchu, scH: scH)
        ksiezyc(&ctx, w: w, h: h, scH: scH)
        wzgorza(&ctx, w: w, h: h, noc: noc, yg: yg, scH: scH)
        if karta {
            poduszki(&ctx, w: w, h: h, yg: yg, scH: scH)
        }

        let xKlucza: CGFloat = karta ? w / 3 : w * 0.25
        let xSwiecy: CGFloat = karta ? (w * 2) / 3 : w * 0.75
        let xButa: CGFloat = karta ? max(14 * scH, w * 0.045) : w * 0.5

        swieca(&ctx, xC: xSwiecy, yg: yg, scH: scH, ta: ta, noc: noc, bezRuchu: bezRuchu)
        let oczkoX: CGFloat = xKlucza - 4 * scH
        let oczkoY: CGFloat = yg - 38 * scH
        klucz(&ctx, bx: oczkoX, by: oczkoY, scH: scH)
        if !bezRuchu {
            kropla(&ctx, bx: oczkoX, by: oczkoY, yg: yg, scH: scH, ta: ta)
        }
        wosk(&ctx, wx: oczkoX, wy: yg - 3 * scH, s: scH * 0.8)
        but(&ctx, bx: xButa, by: yg - 1 * scH, s: scH)
    }

    // Niebo: wiersz 1 tabeli (fioletowe), od 0 do 0.65h.
    fileprivate static func niebo(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, noc: Bool) {
        let dol: CGFloat = h * 0.65
        let st: [Gradient.Stop]
        if noc {
            st = [PomRA.stopHex("#0a0618", 0), PomRA.stopHex("#25124a", 0.6), PomRA.stopHex("#3d1a5c", 1)]
        } else {
            st = [PomRA.stopHex("#1d1140", 0), PomRA.stopHex("#4a2a7e", 0.6), PomRA.stopHex("#8a5fb8", 1)]
        }
        ctx.fill(PomRA.prost(0, 0, w, dol + 4), with: PomRA.pionowy(st, 0, dol))
    }

    // Domknięcie tła pod niebem i ametystowa mgiełka przy horyzoncie.
    fileprivate static func tlo(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, noc: Bool) {
        let dol: CGFloat = h * 0.65
        ctx.fill(PomRA.prost(0, dol, w, h - dol), with: .color(Color(hex: noc ? "#3d1a5c" : "#8a5fb8")))

        let y0: CGFloat = h * 0.45
        let y1: CGFloat = h * 0.70
        let koniec: Color = noc ? PomRA.rgba(124, 58, 237, 0.22) : PomRA.rgba(196, 181, 253, 0.22)
        let st: [Gradient.Stop] = [PomRA.stop(PomRA.rgba(124, 58, 237, 0), 0), PomRA.stop(koniec, 1)]
        ctx.fill(PomRA.prost(0, y0, w, h * 0.25), with: PomRA.pionowy(st, y0, y1))
    }

    // Własne 34 gwiazdy sceny (w dzień przygaszone).
    fileprivate static func gwiazdy(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, ta: Double,
                                    noc: Bool, bezRuchu: Bool, scH: CGFloat) {
        let skala: CGFloat = max(0.8, scH)
        let jasnosc: Double = noc ? 1 : 0.45
        for i in 0..<34 {
            let fx: CGFloat = CGFloat((i * 73 + 11) % 100) / 100
            let fy: CGFloat = CGFloat((i * 37 + 5) % 100) / 100
            let sx: CGFloat = fx * w
            let sy: CGFloat = fy * h * 0.52
            let predk: Double = 0.9 + Double(i % 5) * 0.27
            let arg: Double = ta * predk + Double(i) * 1.7
            let fala: Double = abs(sin(arg))
            let tw: Double = bezRuchu ? 0.75 : (0.25 + 0.7 * fala)
            let bazaR: CGFloat = (i % 4 == 0) ? 1.5 : 0.9
            let r: CGFloat = bazaR * skala
            ctx.fill(PomRA.kolo(sx, sy, r), with: .color(PomRA.rgba(255, 248, 235, jasnosc * tw)))
        }
    }

    // Sierp księżyca z poświatą (prawy górny róg).
    fileprivate static func ksiezyc(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, scH: CGFloat) {
        let mx: CGFloat = w * 0.865
        let my: CGFloat = h * 0.25
        let mr: CGFloat = 15 * scH
        let c = CGPoint(x: mx, y: my)
        let st: [Gradient.Stop] = [
            PomRA.stop(PomRA.rgba(245, 217, 138, 0.30), 0),
            PomRA.stop(PomRA.rgba(245, 217, 138, 0), 1)
        ]
        ctx.fill(PomRA.kolo(mx, my, mr * 2.6), with: PomRA.promien(st, c, mr * 0.4, mr * 2.6))

        // sierp = tarcza minus przesunięte koło
        let tarcza = PomRA.kolo(mx, my, mr)
        let cienKsiezyca = PomRA.kolo(mx + mr * 0.55, my - mr * 0.35, mr * 0.86)
        let sierp = tarcza.subtracting(cienKsiezyca)
        ctx.fill(sierp, with: .color(Color(hex: "#f5d98a")))
    }

    // Wzgórza, ciemna łąka i grunt pierwszego planu.
    fileprivate static func wzgorza(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, noc: Bool,
                                    yg: CGFloat, scH: CGFloat) {
        PomRA.wzgorze(&ctx, w: w, h: h, y0: h * 0.70, a1: 6, f1: 0.011, a2: 4, f2: 0.024,
                      kolor: Color(hex: noc ? "#1f0f3d" : "#2a1650"), faza: 0.6)
        PomRA.wzgorze(&ctx, w: w, h: h, y0: yg - 6 * scH, a1: 4, f1: 0.016, a2: 3, f2: 0.031,
                      kolor: Color(hex: noc ? "#140a2a" : "#1d0f3a"), faza: 2.1)
        ctx.fill(PomRA.prost(0, yg, w, h - yg), with: .color(Color(hex: noc ? "#0f0820" : "#170c30")))
    }

    // Poduszki czytelności pod 3 kolumnami prognozy (tylko karta).
    fileprivate static func poduszki(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, yg: CGFloat, scH: CGFloat) {
        let scPill: CGFloat = min(w / 380, h / 240)
        let odstep: CGFloat = max(28, (34 * scPill).rounded())
        let pTop: CGFloat = h * 0.60 - odstep - 4 * scH
        let pBot: CGFloat = yg - 2 * scH
        guard pBot > pTop + 20 else { return }
        let kolumny: [CGFloat] = [w / 6, w * 0.5, (w * 5) / 6]
        let kolor: Color = PomRA.rgba(11, 7, 24, 0.34)
        for cxCol in kolumny {
            let p = PomRA.zaokr(cxCol - 35 * scPill, pTop, 70 * scPill, pBot - pTop, 11 * scPill)
            ctx.fill(p, with: .color(kolor))
        }
    }

    // Świeca z poświatą, spodkiem, zaciekiem, knotem i drgającym płomieniem.
    fileprivate static func swieca(_ ctx: inout GraphicsContext, xC: CGFloat, yg: CGFloat, scH: CGFloat,
                                   ta: Double, noc: Bool, bezRuchu: Bool) {
        let cw: CGFloat = 12 * scH
        let ch: CGFloat = 40 * scH
        let cx0: CGFloat = xC - cw / 2
        let cyTop: CGFloat = yg - ch
        let m1: Double = 0.06 * sin(ta * 9.3)
        let m2: Double = 0.04 * sin(ta * 15.1)
        let migot: Double = bezRuchu ? 1 : (1 + m1 + m2)

        // poświata
        let glowR: CGFloat = 42 * scH * CGFloat(migot)
        let cGlow = CGPoint(x: xC, y: cyTop - 8 * scH)
        let st: [Gradient.Stop] = [
            PomRA.stop(PomRA.rgba(245, 185, 66, noc ? 0.55 : 0.38), 0),
            PomRA.stop(PomRA.rgba(245, 185, 66, 0.16), 0.5),
            PomRA.stop(PomRA.rgba(245, 185, 66, 0), 1)
        ]
        ctx.fill(PomRA.kolo(cGlow.x, cGlow.y, glowR), with: PomRA.promien(st, cGlow, 2, glowR))

        // spodek
        ctx.fill(PomRA.elipsa(xC, yg - 1 * scH, cw * 1.05, 3.2 * scH), with: .color(Color(hex: "#b7791f")))

        // wosk
        let stW: [Gradient.Stop] = [
            PomRA.stopHex("#e7d3a8", 0), PomRA.stopHex("#f8ecd0", 0.45), PomRA.stopHex("#d9c08a", 1)
        ]
        let trzon = PomRA.zaokr(cx0, cyTop, cw, ch - 1 * scH, 2.5 * scH)
        ctx.fill(trzon, with: PomRA.poziomy(stW, cx0, cx0 + cw))

        // zaciek wosku
        var zaciek = Path()
        zaciek.move(to: CGPoint(x: cx0 + 1.6 * scH, y: cyTop + 3 * scH))
        zaciek.addQuadCurve(to: CGPoint(x: cx0 + 1.8 * scH, y: cyTop + 14 * scH),
                            control: CGPoint(x: cx0 + 4.2 * scH, y: cyTop + 9 * scH))
        ctx.stroke(zaciek, with: .color(Color(hex: "#e7d3a8")),
                   style: StrokeStyle(lineWidth: max(1.2, 2.2 * scH), lineCap: .round))

        // knot
        let knot = PomRA.linia(CGPoint(x: xC, y: cyTop), CGPoint(x: xC, y: cyTop - 4 * scH))
        ctx.stroke(knot, with: .color(Color(hex: "#2a1300")),
                   style: StrokeStyle(lineWidth: max(0.8, 1.1 * scH), lineCap: .round))

        plomien(&ctx, xC: xC, cyTop: cyTop, scH: scH, ta: ta, bezRuchu: bezRuchu)
    }

    // Płomień: zewnętrzny pomarańczowy i wewnętrzny jasny rdzeń.
    fileprivate static func plomien(_ ctx: inout GraphicsContext, xC: CGFloat, cyTop: CGFloat, scH: CGFloat,
                                    ta: Double, bezRuchu: Bool) {
        let kolys: CGFloat = CGFloat(sin(ta * 6.1)) * 1.4 * scH
        let sway: CGFloat = bezRuchu ? 0 : kolys
        let puls: Double = 0.08 * sin(ta * 11.7)
        let wzrost: Double = bezRuchu ? 1 : (1 + puls)
        let fh: CGFloat = 16 * scH * CGFloat(wzrost)
        let fw: CGFloat = 5.2 * scH
        let fBase: CGFloat = cyTop - 2.5 * scH

        var zew = Path()
        zew.move(to: CGPoint(x: xC, y: fBase))
        zew.addCurve(to: CGPoint(x: xC + sway, y: fBase - fh),
                     control1: CGPoint(x: xC - fw, y: fBase - fh * 0.25),
                     control2: CGPoint(x: xC - fw * 0.6 + sway, y: fBase - fh * 0.7))
        zew.addCurve(to: CGPoint(x: xC, y: fBase),
                     control1: CGPoint(x: xC + fw * 0.6 + sway, y: fBase - fh * 0.7),
                     control2: CGPoint(x: xC + fw, y: fBase - fh * 0.25))
        zew.closeSubpath()
        ctx.fill(zew, with: .color(Color(hex: "#f5b942")))

        let sw2: CGFloat = sway * 0.6
        let baza2: CGFloat = fBase - 0.5 * scH
        var wew = Path()
        wew.move(to: CGPoint(x: xC, y: baza2))
        wew.addCurve(to: CGPoint(x: xC + sw2, y: fBase - fh * 0.66),
                     control1: CGPoint(x: xC - fw * 0.45, y: fBase - fh * 0.25),
                     control2: CGPoint(x: xC - fw * 0.3 + sw2, y: fBase - fh * 0.55))
        wew.addCurve(to: CGPoint(x: xC, y: baza2),
                     control1: CGPoint(x: xC + fw * 0.3 + sw2, y: fBase - fh * 0.55),
                     control2: CGPoint(x: xC + fw * 0.45, y: fBase - fh * 0.25))
        wew.closeSubpath()
        ctx.fill(wew, with: .color(Color(hex: "#fff4cf")))
    }

    // Stary klucz (oczko w punkcie bx, by), obrócony o -2.05 rad.
    fileprivate static func klucz(_ ctx: inout GraphicsContext, bx: CGFloat, by: CGFloat, scH: CGFloat) {
        let keyR: CGFloat = 7 * scH
        let zloty: Color = Color(hex: "#d6b25e")
        var c2 = ctx
        c2.translateBy(x: bx, y: by)
        c2.rotate(by: .radians(-2.05))

        // oczko
        c2.stroke(PomRA.kolo(0, 0, keyR), with: .color(zloty), lineWidth: max(1.6, 3.2 * scH))
        // trzon
        let trzon = PomRA.zaokr(-keyR - 21 * scH, -1.8 * scH, 21 * scH, 3.6 * scH, 1.2 * scH)
        c2.fill(trzon, with: .color(zloty))
        // pióro (zęby)
        c2.fill(PomRA.prost(-keyR - 21 * scH, 1.8 * scH, 3.6 * scH, 6 * scH), with: .color(zloty))
        c2.fill(PomRA.prost(-keyR - 15.5 * scH, 1.8 * scH, 3 * scH, 4.4 * scH), with: .color(zloty))
        // pierścień przy oczku
        c2.fill(PomRA.prost(-keyR - 3.4 * scH, -3 * scH, 2.4 * scH, 6 * scH), with: .color(zloty))
    }

    // Kropla wosku spadająca przez oczko klucza (okres 3.4 s, widoczna przez 72%).
    fileprivate static func kropla(_ ctx: inout GraphicsContext, bx: CGFloat, by: CGFloat, yg: CGFloat,
                                   scH: CGFloat, ta: Double) {
        let okres: Double = 3.4
        let faza: Double = ta.truncatingRemainder(dividingBy: okres) / okres
        guard faza >= 0 && faza < 0.72 else { return }
        let p: Double = faza / 0.72
        let yStart: CGFloat = by - 22 * scH
        let yEnd: CGFloat = yg - 4 * scH
        let dy: CGFloat = yStart + (yEnd - yStart) * CGFloat(p * p)
        var alfa: Double = 1
        if p < 0.08 {
            alfa = p / 0.08
        } else if p > 0.92 {
            alfa = (1 - p) / 0.08
        }
        alfa = max(0, min(1, alfa))
        let dr: CGFloat = 2.6 * scH

        var kr = Path()
        kr.move(to: CGPoint(x: bx, y: dy - dr * 1.9))
        kr.addQuadCurve(to: CGPoint(x: bx, y: dy + dr), control: CGPoint(x: bx + dr * 1.05, y: dy - dr * 0.2))
        kr.addQuadCurve(to: CGPoint(x: bx, y: dy - dr * 1.9), control: CGPoint(x: bx - dr * 1.05, y: dy - dr * 0.2))
        kr.closeSubpath()
        ctx.fill(kr, with: .color(PomRA.rgba(242, 227, 189, alfa)))
    }

    // Zastygły kształt wosku (cień wróżby) pod kluczem.
    fileprivate static func wosk(_ ctx: inout GraphicsContext, wx: CGFloat, wy: CGFloat, s: CGFloat) {
        func q(_ dx: CGFloat, _ dy: CGFloat) -> CGPoint {
            return CGPoint(x: wx + dx * s, y: wy + dy * s)
        }
        let cien = PomRA.elipsa(wx + 1 * s, wy + 3 * s, 15 * s, 3.2 * s)
        ctx.fill(cien, with: .color(PomRA.rgba(167, 139, 250, 0.35)))

        var plama = Path()
        plama.move(to: q(-12, 1))
        plama.addCurve(to: q(-3, -5), control1: q(-13, -6), control2: q(-6, -9))
        plama.addCurve(to: q(6, -4), control1: q(-1, -12), control2: q(7, -11))
        plama.addCurve(to: q(10, 2.5), control1: q(12, -6), control2: q(15, 0))
        plama.addCurve(to: q(-12, 1), control1: q(4, 4), control2: q(-6, 4))
        plama.closeSubpath()
        ctx.fill(plama, with: .color(Color(hex: "#f2e3bd")))

        let blask = PomRA.elipsaObrocona(wx - 1 * s, wy - 6 * s, 2.2 * s, 1.3 * s, -0.4)
        ctx.fill(blask, with: .color(PomRA.rgba(255, 255, 255, 0.55)))
    }

    // But (czubek w prawo) z podeszwą i jasnym obszyciem.
    fileprivate static func but(_ ctx: inout GraphicsContext, bx: CGFloat, by: CGFloat, s: CGFloat) {
        func q(_ dx: CGFloat, _ dy: CGFloat) -> CGPoint {
            return CGPoint(x: bx + dx * s, y: by + dy * s)
        }
        let cien = PomRA.elipsa(bx + 3 * s, by + 1.2 * s, 13 * s, 2.2 * s)
        ctx.fill(cien, with: .color(PomRA.rgba(0, 0, 0, 0.25)))

        var cholewka = Path()
        cholewka.move(to: q(-8, 0))
        cholewka.addLine(to: q(-8, -17))
        cholewka.addLine(to: q(-1, -17))
        cholewka.addLine(to: q(0, -8))
        cholewka.addQuadCurve(to: q(14, -2.5), control: q(12, -7))
        cholewka.addLine(to: q(14, 0))
        cholewka.closeSubpath()
        ctx.fill(cholewka, with: .color(Color(hex: "#7c3aed")))

        ctx.fill(PomRA.prost(bx - 8 * s, by - 2.4 * s, 22 * s, 2.4 * s), with: .color(Color(hex: "#5b21b6")))

        var obszycie = PomRA.linia(q(-8, -15), q(-1.2, -15))
        obszycie.addPath(PomRA.linia(q(-4, -12), q(-0.6, -10.5)))
        ctx.stroke(obszycie, with: .color(Color(hex: "#c4b5fd")),
                   style: StrokeStyle(lineWidth: max(0.8, 1.2 * s), lineCap: .round))
    }
}
