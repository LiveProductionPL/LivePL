import SwiftUI

// Sceny karty pogody przeniesione ze strony (public/assets/js/weather-effects.js, drawScene i _drawCustomScene).
// Współrzędne jak na stronie: ułamki szerokości i wysokości karty plus kilka stałych punktów.
// Bez nakładki pogody (PogodaEfekty), bez HUD i bez karty daty: to osobne warstwy.

/// Warunki sceny jak na stronie (weather-effects.js): isNight, isStormy, isRainy, isFoggy i ustawienia z panelu.
struct WarunkiSceny {
    var noc: Bool
    var burzowo = false      // state includes 'stormy' || state == 'rainy-heavy'
    var deszczowo = false    // state starts with 'rainy' || state == 'cloudy'
    var mglisto = false      // state includes 'foggy' (beach day sky)
    /// gwiazdy w nocy dla scen niespecjalnych (!_peNoStars: czysta noc albo nocne chmury < 0.5); sceny specjalne mają je zawsze
    var gwiazdy = true
    /// tryb karty (sidebar): andrzejki ustawia kolumny inaczej, plaża bez odbicia słońca
    var karta = true
    var kolorNieba: Color? = nil      // skyColor (zastępuje stop 0 tam, gdzie strona go używa)
    var kolorWody: Color? = nil       // waterColor
    var kolorPlazy: Color? = nil      // beachColor
    var predkoscFal: Double = 1       // waveSpeed
    var silaFal: Double = 1           // waveIntensity
    var bioluminescencja = false      // beachBiolum
    var bezRuchu = false              // „Ogranicz ruch”: andrzejki zamrożone (strona: reduced motion)
}

enum ScenaStrony: String, CaseIterable {
    case beach, ocean
    case autumnForest = "autumn-forest", autumnLake = "autumn-lake", parkLanterns = "park-lanterns"
    case winterVillage = "winter-village", alpinePeaks = "alpine-peaks", winterChristmas = "winter-christmas"
    case newYear = "new-year", easterSpring = "easter-spring", powstanczyBiwak = "powstanczy-biwak"
    case ratusz11Listopada = "leszno-ratusz-11listopada", andrzejki = "leszno-andrzejki"
    case zima = "leszno-zima", mikolajki = "leszno-mikolajki", wigilia = "leszno-wigilia"
    case swieta = "leszno-swieta", sylwester = "leszno-sylwester", nowyRok = "leszno-nowy-rok"

    /// Na stronie „specjalne”: bez nakładki pogody (pogoda-efekty.js:157-160), gwiazdy w nocy zawsze.
    var specjalna: Bool {
        switch self {
        case .ratusz11Listopada, .andrzejki, .zima, .mikolajki, .wigilia, .swieta, .sylwester, .nowyRok,
             .newYear, .powstanczyBiwak, .winterChristmas:
            return true
        default:
            return false
        }
    }

    /// Rysuje scenę na prostokącie 0,0,size (współrzędne jak na stronie: ułamki w i h). t w sekundach.
    func rysuj(_ ctx: inout GraphicsContext, size: CGSize, t: Double, warunki: WarunkiSceny) {
        switch self {
        case .ratusz11Listopada: ScenaRatusza.rysuj(&ctx, size: size, t: t, warunki: warunki)
        case .andrzejki: ScenaAndrzejek.rysuj(&ctx, size: size, t: t, warunki: warunki)
        case .zima, .mikolajki, .wigilia, .swieta, .sylwester, .nowyRok:
            ScenySwiateczne.rysuj(self, &ctx, size: size, t: t, warunki: warunki)
        default:
            RysunkiStrony.rysuj(self, &ctx, size: size, t: t, warunki: warunki)
        }
    }
}

// MARK: - Rysowanie scen tego pliku

fileprivate enum RysunkiStrony {
    static func rysuj(_ scena: ScenaStrony, _ ctx: inout GraphicsContext, size: CGSize, t: Double, warunki wa: WarunkiSceny) {
        let w: CGFloat = size.width
        let h: CGFloat = size.height
        guard w >= 1, h >= 1 else { return }
        let tc: CGFloat = CGFloat(t)
        let noc: Bool = wa.noc
        switch scena {
        case .beach:
            plaza(&ctx, w: w, h: h, t: tc, wa: wa, ocean: false)
            return
        case .ocean:
            plaza(&ctx, w: w, h: h, t: tc, wa: wa, ocean: true)
            return
        case .ratusz11Listopada, .andrzejki, .zima, .mikolajki, .wigilia, .swieta, .sylwester, .nowyRok:
            // sceny z innych plików
            return
        default:
            break
        }
        // niebo
        niebo(scena, &ctx, w: w, h: h, wa: wa)
        // gwiazdy: Sylwester zawsze, specjalne w nocy zawsze, pozostałe tylko przy pogodnej nocy
        let zGwiazdami: Bool = scena == .newYear || (noc && (scena.specjalna || wa.gwiazdy))
        if zGwiazdami { gwiazdy(&ctx, w: w, h: h, t: tc) }
        switch scena {
        case .autumnForest: lasJesienny(&ctx, w: w, h: h, t: tc, noc: noc)
        case .autumnLake: jezioroJesienne(&ctx, w: w, h: h, t: tc, noc: noc)
        case .parkLanterns: alejaParkowa(&ctx, w: w, h: h, t: tc, noc: noc)
        case .winterVillage: osadaZimowa(&ctx, w: w, h: h, t: tc, noc: noc)
        case .alpinePeaks: szczytyAlpejskie(&ctx, w: w, h: h, t: tc, noc: noc)
        case .winterChristmas: zimaSwiateczna(&ctx, w: w, h: h, t: tc, noc: noc)
        case .newYear: miastoNoworoczne(&ctx, w: w, h: h, t: tc)
        case .easterSpring: wielkanoc(&ctx, w: w, h: h, t: tc, noc: noc)
        case .powstanczyBiwak: biwak(&ctx, w: w, h: h, t: tc)
        default: break
        }
        // przyciemnienie dolnej strefy (kontrast napisów)
        kontrast(&ctx, w: w, h: h)
    }
}

// MARK: - Pomocnicy (tylko w tym pliku)

extension RysunkiStrony {
    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }

    static func hex(_ kod: String) -> Color { Color(hex: kod) }

    static func stop(_ kolor: Color, _ miejsce: CGFloat) -> Gradient.Stop {
        Gradient.Stop(color: kolor, location: miejsce)
    }

    /// Gradient pionowy od y0 do y1 (jak createLinearGradient(0, y0, 0, y1)).
    static func pion(_ stopy: [Gradient.Stop], _ y0: CGFloat, _ y1: CGFloat) -> GraphicsContext.Shading {
        .linearGradient(Gradient(stops: stopy), startPoint: CGPoint(x: 0, y: y0), endPoint: CGPoint(x: 0, y: y1))
    }

    /// Gradient kołowy (jak createRadialGradient(x, y, r0, x, y, r1)).
    static func promien(_ stopy: [Gradient.Stop], _ x: CGFloat, _ y: CGFloat, _ r0: CGFloat, _ r1: CGFloat) -> GraphicsContext.Shading {
        .radialGradient(Gradient(stops: stopy), center: CGPoint(x: x, y: y), startRadius: r0, endRadius: r1)
    }

    static func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x, y: y) }

    static func prost(_ x: CGFloat, _ y: CGFloat, _ szer: CGFloat, _ wys: CGFloat) -> Path {
        Path(CGRect(x: x, y: y, width: szer, height: wys))
    }

    static func kolo(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    /// Elipsa o środku (x, y), obrócona o kąt w radianach wokół środka (jak ctx.ellipse).
    static func elipsa(_ x: CGFloat, _ y: CGFloat, _ rx: CGFloat, _ ry: CGFloat, _ kat: CGFloat = 0) -> Path {
        var p = Path()
        let przeksztalcenie = CGAffineTransform(translationX: x, y: y).rotated(by: kat)
        p.addEllipse(in: CGRect(x: -rx, y: -ry, width: rx * 2, height: ry * 2), transform: przeksztalcenie)
        return p
    }

    static func linia(_ x0: CGFloat, _ y0: CGFloat, _ x1: CGFloat, _ y1: CGFloat) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    static func wielokat(_ punkty: [CGPoint]) -> Path {
        var p = Path()
        p.addLines(punkty)
        p.closeSubpath()
        return p
    }

    /// Część ułamkowa (jak x % 1 na stronie dla dodatnich x).
    static func ulamek(_ x: CGFloat) -> CGFloat { x - floor(x) }

    /// Reszta zawsze nieujemna.
    static func modulo(_ x: CGFloat, _ m: CGFloat) -> CGFloat {
        guard m > 0 else { return 0 }
        let r: CGFloat = x.truncatingRemainder(dividingBy: m)
        return r < 0 ? r + m : r
    }

    /// Stała „losowość” z indeksu (zamiast Math.random): 0..<1, ta sama w każdej klatce.
    static func los(_ i: Int, _ k: Int) -> CGFloat {
        let a: Double = Double(i) * 12.9898 + Double(k) * 78.233
        let v: Double = sin(a) * 43758.5453
        return CGFloat(v - floor(v))
    }

    /// drawHill ze strony (1431-1444): fala od lewej do prawej, zamknięta do dołu karty.
    static func sciezkaWzgorza(_ w: CGFloat, _ h: CGFloat, _ yBase: CGFloat, _ a1: CGFloat, _ f1: CGFloat,
                               _ a2: CGFloat, _ f2: CGFloat, _ faza: CGFloat) -> Path {
        let skala: CGFloat = 380 / max(380, w)
        var p = Path()
        p.move(to: CGPoint(x: 0, y: h))
        var x: CGFloat = 0
        while x <= w {
            let k1: CGFloat = x * f1 * skala + faza
            let k2: CGFloat = x * f2 * skala - faza * 0.5
            let y: CGFloat = yBase + sin(k1) * a1 + cos(k2) * a2
            p.addLine(to: CGPoint(x: x, y: y))
            x += 4
        }
        p.addLine(to: CGPoint(x: w, y: h))
        p.closeSubpath()
        return p
    }

    static func wzgorze(_ ctx: inout GraphicsContext, _ w: CGFloat, _ h: CGFloat, _ yBase: CGFloat, _ a1: CGFloat, _ f1: CGFloat,
                        _ a2: CGFloat, _ f2: CGFloat, _ kolor: Color, _ faza: CGFloat) {
        ctx.fill(sciezkaWzgorza(w, h, yBase, a1, f1, a2, f2, faza), with: .color(kolor))
    }

    /// Przyciemnienie dolnej strefy (3815-3822).
    static func kontrast(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat) {
        let y0: CGFloat = h * 0.58 - 15
        let stopy: [Gradient.Stop] = [stop(rgba(6, 10, 22, 0), 0), stop(rgba(6, 10, 22, 0.28), 0.35), stop(rgba(6, 10, 22, 0.58), 1)]
        ctx.fill(prost(0, y0, w, h - y0), with: pion(stopy, y0, h))
    }

    /// 28 migoczących gwiazd sceny (1412-1427).
    static func gwiazdy(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat) {
        let zakresY: CGFloat = h * 0.58 * 0.82
        for i in 0..<28 {
            let fi = CGFloat(i)
            let sx: CGFloat = CGFloat((i * 73 + 19) % 100) / 100 * w
            let sy: CGFloat = CGFloat((i * 41 + 7) % 100) / 100 * zakresY
            let tempo: CGFloat = 1.1 + CGFloat(i % 5) * 0.3
            let tw: CGFloat = 0.25 + 0.65 * abs(sin(t * tempo + fi))
            let r: CGFloat = (i % 3 == 0) ? 1.4 : 0.9
            ctx.fill(kolo(sx, sy, r), with: .color(rgba(255, 250, 235, Double(tw))))
        }
    }

    /// Niebo scen własnych (tabela 1303-1410), od 0 do horyzontu.
    static func niebo(_ scena: ScenaStrony, _ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, wa: WarunkiSceny) {
        let hy: CGFloat = h * 0.58
        let stopy: [Gradient.Stop] = stopyNieba(scena, wa)
        ctx.fill(prost(0, 0, w, hy + 4), with: pion(stopy, 0, hy))
    }

    static func stopyNieba(_ scena: ScenaStrony, _ wa: WarunkiSceny) -> [Gradient.Stop] {
        if scena == .newYear {
            let s0: Color = wa.kolorNieba ?? hex("#040614")
            return [stop(s0, 0), stop(hex("#0d1333"), 0.55), stop(hex("#231942"), 1)]
        }
        if scena == .powstanczyBiwak {
            if wa.noc {
                return [stop(hex("#050812"), 0), stop(hex("#14192b"), 0.58), stop(hex("#3b1520"), 1)]
            }
            return [stop(hex("#18263b"), 0), stop(hex("#3b4a62"), 0.55), stop(hex("#8c4a3c"), 1)]
        }
        if wa.noc {
            let s0: Color = wa.kolorNieba ?? hex("#050b1a")
            return [stop(s0, 0), stop(hex("#0f1c38"), 0.55), stop(hex("#1d2d4e"), 1)]
        }
        if wa.burzowo {
            return [stop(hex("#1c2030"), 0), stop(hex("#2e384d"), 0.6), stop(hex("#4a5568"), 1)]
        }
        if wa.deszczowo {
            return [stop(hex("#37475a"), 0), stop(hex("#566c7d"), 0.6), stop(hex("#829eab"), 1)]
        }
        switch scena {
        case .autumnForest, .autumnLake, .parkLanterns:
            let s0: Color = wa.kolorNieba ?? hex("#1e3c58")
            return [stop(s0, 0), stop(hex("#4b6cb7"), 0.5), stop(hex("#c77952"), 0.85), stop(hex("#e8a86b"), 1)]
        case .easterSpring:
            let s0: Color = wa.kolorNieba ?? hex("#1e69b8")
            return [stop(s0, 0), stop(hex("#4fa3e3"), 0.5), stop(hex("#9ad4f5"), 0.85), stop(hex("#d8f3dc"), 1)]
        default:
            // zimowe sceny w dzień
            let s0: Color = wa.kolorNieba ?? hex("#1b3b64")
            return [stop(s0, 0), stop(hex("#3b6998"), 0.55), stop(hex("#7ea4c4"), 0.85), stop(hex("#c8dce8"), 1)]
        }
    }
}

// MARK: - Plaża i ocean (1026-1286)

extension RysunkiStrony {
    static func plaza(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat, wa: WarunkiSceny, ocean: Bool) {
        let noc: Bool = wa.noc
        let predkosc: CGFloat = CGFloat(wa.predkoscFal)
        let sila: CGFloat = CGFloat(wa.silaFal)
        let seaY: CGFloat = ocean ? h * 0.52 : h * 0.60
        let beachY: CGFloat = h * 0.80
        // niebo
        ctx.fill(prost(0, 0, w, seaY), with: pion(stopyNiebaPlazy(wa), 0, seaY))
        // poświata horyzontu
        let poswiata: [Gradient.Stop]
        if noc {
            poswiata = [stop(rgba(30, 60, 120, 0), 0), stop(rgba(10, 20, 50, 0.55), 1)]
        } else {
            poswiata = [stop(rgba(255, 190, 80, 0), 0), stop(rgba(255, 230, 140, 0.3), 1)]
        }
        ctx.fill(prost(0, seaY - 24, w, 36), with: pion(poswiata, seaY - 24, seaY + 12))
        // morze
        let morze: [Gradient.Stop]
        if noc {
            morze = [stop(hex("#0a1828"), 0), stop(hex("#0d1e32"), 0.6), stop(hex("#122438"), 1)]
        } else {
            let woda: Color = wa.kolorWody ?? hex("#006994")
            morze = [stop(woda, 0), stop(hex("#0090b8"), 0.45), stop(hex("#00b8d4"), 1)]
        }
        ctx.fill(prost(0, seaY, w, beachY - seaY + 2), with: pion(morze, seaY, beachY))
        // odbicie słońca lub księżyca (strona: tylko poza kartą)
        if !wa.karta {
            odbicie(&ctx, w: w, seaY: seaY, beachY: beachY, t: t, predkosc: predkosc, sila: sila, noc: noc)
        }
        // fale (ocean: dłuższe i mocniejsze)
        let dol: CGFloat = ocean ? h * 0.88 : beachY
        let silaFal: CGFloat = ocean ? sila * 1.4 : sila
        fale(&ctx, w: w, h: h, seaY: seaY, dol: dol, t: t, predkosc: predkosc, sila: silaFal, noc: noc, biolum: wa.bioluminescencja)
        if ocean {
            glebia(&ctx, w: w, h: h, t: t, noc: noc, biolum: wa.bioluminescencja)
        } else {
            piasek(&ctx, w: w, h: h, beachY: beachY, wa: wa)
        }
    }

    static func stopyNiebaPlazy(_ wa: WarunkiSceny) -> [Gradient.Stop] {
        if wa.noc {
            let s0: Color = wa.kolorNieba ?? hex("#04091e")
            return [stop(s0, 0), stop(hex("#0b1535"), 0.55), stop(hex("#1a2850"), 1)]
        }
        if wa.burzowo {
            return [stop(hex("#1c1c2e"), 0), stop(hex("#2e3a50"), 0.6), stop(hex("#4a5568"), 1)]
        }
        if wa.deszczowo {
            return [stop(hex("#3a4a5e"), 0), stop(hex("#5a7080"), 0.6), stop(hex("#8aabb0"), 1)]
        }
        if wa.mglisto {
            return [stop(hex("#a8b8c8"), 0), stop(hex("#dde8f0"), 1)]
        }
        let s0: Color = wa.kolorNieba ?? hex("#1565c0")
        return [stop(s0, 0), stop(hex("#2196f3"), 0.35), stop(hex("#64b5f6"), 0.75), stop(hex("#bbdefb"), 1)]
    }

    static func odbicie(_ ctx: inout GraphicsContext, w: CGFloat, seaY: CGFloat, beachY: CGFloat, t: CGFloat,
                        predkosc: CGFloat, sila: CGFloat, noc: Bool) {
        let refX: CGFloat = noc ? w * 0.78 : w * 0.13
        let refW: CGFloat = w * 0.055
        let koniec: CGFloat = w * 0.12
        var p = Path()
        p.move(to: pt(refX - refW, seaY))
        var xi: CGFloat = 0
        while xi <= koniec {
            let rx: CGFloat = refX - refW + xi
            let arg: CGFloat = rx * 0.06 + t * predkosc * 2.2
            let ry: CGFloat = seaY + sin(arg) * 2.5 * sila
            p.addLine(to: pt(rx, ry))
            xi += 2
        }
        p.addLine(to: pt(refX + refW * 0.5, beachY - 4))
        p.addLine(to: pt(refX - refW * 0.5, beachY - 4))
        p.closeSubpath()
        let stopy: [Gradient.Stop]
        if noc {
            stopy = [stop(rgba(170, 200, 255, 0.30), 0), stop(rgba(170, 200, 255, 0.08), 0.7), stop(rgba(170, 200, 255, 0), 1)]
        } else {
            stopy = [stop(rgba(255, 240, 100, 0.40), 0), stop(rgba(255, 240, 100, 0.12), 0.6), stop(rgba(255, 240, 100, 0), 1)]
        }
        ctx.fill(p, with: pion(stopy, seaY, beachY))
    }

    static func yFali(_ x: CGFloat, _ y0: CGFloat, _ amp: CGFloat, _ freq: CGFloat, _ faza: CGFloat) -> CGFloat {
        let a1: CGFloat = sin(x * freq + faza) * amp
        let a2: CGFloat = sin(x * freq * 1.65 + faza * 1.4) * amp * 0.38
        let a3: CGFloat = sin(x * freq * 2.8 + faza * 0.9) * amp * 0.18
        return y0 + a1 + a2 + a3
    }

    /// 5 warstw fal (_drawBeachWaves 1216-1286).
    static func fale(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, seaY: CGFloat, dol: CGFloat, t: CGFloat,
                     predkosc: CGFloat, sila: CGFloat, noc: Bool, biolum: Bool) {
        for wi in 0..<5 {
            let fw = CGFloat(wi)
            let prog: CGFloat = fw / 4
            let waveY: CGFloat = seaY + (dol - seaY - 6) * prog
            let amp: CGFloat = (2.5 + fw * 1.8) * sila
            let freq: CGFloat = 0.020 - fw * 0.0018
            let faza: CGFloat = t * predkosc * (0.55 + fw * 0.14)
            let alfa: Double = 0.10 + Double(prog) * 0.18
            let r: Double = noc ? Double(15 + wi * 7) : 0
            let g: Double = noc ? Double(35 + wi * 10) : Double(115 + wi * 18)
            let b: Double = noc ? Double(75 + wi * 18) : Double(180 + wi * 14)
            var cialo = Path()
            var grzbiet = Path()
            cialo.move(to: pt(0, h))
            var x: CGFloat = 0
            while x <= w {
                let p: CGPoint = pt(x, yFali(x, waveY, amp, freq, faza))
                cialo.addLine(to: p)
                if x == 0 { grzbiet.move(to: p) } else { grzbiet.addLine(to: p) }
                x += 3
            }
            cialo.addLine(to: pt(w, h))
            cialo.closeSubpath()
            let stopy: [Gradient.Stop] = [stop(rgba(r, g, b, alfa + 0.12), 0), stop(rgba(r, g, b, 0), 1)]
            ctx.fill(cialo, with: pion(stopy, waveY - amp * 1.5, dol + 10))
            // piana na grzbiecie (nocą z bioluminescencją świeci na dwóch ostatnich falach)
            let grubosc: CGFloat = wi == 4 ? 2.2 : 1.2
            let piana: Double = 0.18 + Double(prog) * 0.30
            if noc && biolum && wi >= 3 {
                var c = ctx
                c.addFilter(.shadow(color: rgba(0, 200, 255, 0.9), radius: 5))
                c.stroke(grzbiet, with: .color(rgba(80, 220, 255, piana + 0.15)), lineWidth: grubosc)
            } else {
                ctx.stroke(grzbiet, with: .color(rgba(255, 255, 255, piana)), lineWidth: grubosc)
            }
            if wi == 4 {
                babelki(&ctx, w: w, dol: dol, t: t, amp: amp, freq: freq, faza: faza, cyjan: biolum && noc)
            }
        }
    }

    /// Bąbelki przy ostatniej fali; krycie stałe z indeksu (strona losuje w każdej klatce).
    static func babelki(_ ctx: inout GraphicsContext, w: CGFloat, dol: CGFloat, t: CGFloat, amp: CGFloat,
                        freq: CGFloat, faza: CGFloat, cyjan: Bool) {
        let krok: CGFloat = max(1, floor(w / 18))
        var bx: CGFloat = 0
        var i: Int = 0
        while bx < w {
            let arg: CGFloat = bx * freq * 1.2 + faza * 1.1
            let by: CGFloat = dol + sin(arg) * amp * 0.5 - 3
            let cx: CGFloat = bx + sin(t * 1.5 + bx) * 4
            let a: Double = 0.12 + Double(los(i, 5)) * 0.15
            let kolor: Color = cyjan ? rgba(80, 220, 255, a) : rgba(255, 255, 255, a)
            ctx.fill(kolo(cx, by, 2.2), with: .color(kolor))
            bx += krok
            i += 1
        }
    }

    /// Plaża: piasek, mokry pas przy wodzie i ziarnistość.
    static func piasek(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, beachY: CGFloat, wa: WarunkiSceny) {
        let noc: Bool = wa.noc
        let stopy: [Gradient.Stop]
        if noc {
            let srodek: Color = wa.kolorPlazy ?? hex("#2c2416")
            stopy = [stop(hex("#1e1808"), 0), stop(srodek, 0.4), stop(hex("#3a3020"), 1)]
        } else {
            let gora: Color = wa.kolorPlazy ?? hex("#f4d03f")
            stopy = [stop(gora, 0), stop(hex("#e8c87c"), 0.5), stop(hex("#d4a96a"), 1)]
        }
        ctx.fill(prost(0, beachY, w, h - beachY), with: pion(stopy, beachY, h))
        // mokry piasek przy linii wody
        let mokry: [Gradient.Stop]
        if noc {
            mokry = [stop(rgba(10, 18, 35, 0.7), 0), stop(rgba(10, 18, 35, 0), 1)]
        } else {
            mokry = [stop(rgba(0, 80, 130, 0.5), 0), stop(rgba(0, 80, 130, 0), 1)]
        }
        ctx.fill(prost(0, beachY - 6, w, 20), with: pion(mokry, beachY - 6, beachY + 14))
        // ziarna piasku: stałe z indeksu, krycie 0.04-0.18 w 6 poziomach (mniej wywołań rysowania)
        let ile: Int = Int(floor(w * 0.6))
        let wys: CGFloat = h - beachY
        var poziomy: [Path] = [Path(), Path(), Path(), Path(), Path(), Path()]
        for i in 0..<max(0, ile) {
            let x: CGFloat = los(i, 1) * w
            let y: CGFloat = beachY + los(i, 2) * wys
            let r: CGFloat = los(i, 3) * 1.4 + 0.4
            let k: Int = min(5, Int(los(i, 4) * 6))
            poziomy[k].addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
        }
        for k in 0..<6 {
            let o: Double = 0.04 + (Double(k) + 0.5) / 6 * 0.14
            let kolor: Color = noc ? rgba(255, 230, 160, o * 0.5) : rgba(120, 80, 20, o)
            ctx.fill(poziomy[k], with: .color(kolor))
        }
    }

    /// Ocean: głębia zamiast plaży, w dzień kaustyki, nocą bioluminescencja.
    static func glebia(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat, noc: Bool, biolum: Bool) {
        let y0: CGFloat = h * 0.82
        let stopy: [Gradient.Stop]
        if noc {
            stopy = [stop(hex("#040d1a"), 0), stop(hex("#020810"), 0.5), stop(hex("#010508"), 1)]
        } else {
            stopy = [stop(hex("#004d6e"), 0), stop(hex("#003850"), 0.5), stop(hex("#002030"), 1)]
        }
        ctx.fill(prost(0, y0, w, h * 0.18), with: pion(stopy, y0, h))
        if !noc {
            // plamki podwodnego światła
            let krok: CGFloat = w * 0.12
            let koniec: CGFloat = w * 0.92
            let swiatlo: [Gradient.Stop] = [stop(rgba(0, 200, 220, 0.18), 0), stop(rgba(0, 200, 220, 0), 1)]
            var cx: CGFloat = w * 0.08
            while cx < koniec {
                let falaY: CGFloat = sin(t * 0.8 + cx * 0.01)
                let cy: CGFloat = h * 0.86 + falaY * h * 0.03
                let falaR: CGFloat = sin(t * 1.1 + cx)
                let lr: CGFloat = w * 0.03 + falaR * w * 0.01
                ctx.fill(elipsa(cx, cy, lr, lr * 0.4), with: promien(swiatlo, cx, cy, 0, lr))
                cx += krok
            }
        } else if biolum {
            // rozbłyski w głębinie; wysokość stała z indeksu (strona losuje w każdej klatce)
            let krok: CGFloat = w * 0.08
            let koniec: CGFloat = w * 0.95
            var bx: CGFloat = w * 0.05
            var i: Int = 0
            while bx < koniec {
                let by: CGFloat = h * 0.88 + los(i, 7) * h * 0.10
                let ba: Double = 0.06 + Double(sin(t * 2.0 + bx * 0.05)) * 0.04
                if ba > 0 {
                    ctx.fill(kolo(bx, by, 3), with: .color(rgba(0, 200, 255, ba)))
                }
                bx += krok
                i += 1
            }
        }
    }
}

// MARK: - Jesień: las, jezioro, aleja parkowa (1446-1709)

extension RysunkiStrony {
    static func lasJesienny(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat, noc: Bool) {
        let hy: CGFloat = h * 0.58
        // łuna nad horyzontem
        let luna: Color = noc ? rgba(180, 85, 30, 0.28) : rgba(255, 175, 70, 0.48)
        let stopyLuny: [Gradient.Stop] = [stop(rgba(255, 150, 50, 0), 0), stop(luna, 1)]
        ctx.fill(prost(0, hy - 40, w, 60), with: pion(stopyLuny, hy - 40, hy + 20))
        // dalekie i środkowe wzgórza
        wzgorze(&ctx, w, h, hy - 12, 16, 0.012, 8, 0.025, noc ? hex("#1b1d36") : hex("#5e4663"), 0.5)
        wzgorze(&ctx, w, h, hy + 14, 14, 0.015, 6, 0.032, noc ? hex("#2a1c29") : hex("#8a4829"), 1.7)
        // mgła w dolinie
        let ma: Double = noc ? 0.14 : 0.24
        let stopyMgly: [Gradient.Stop] = [stop(rgba(255, 235, 215, 0), 0), stop(rgba(255, 235, 215, ma), 0.5), stop(rgba(255, 235, 215, 0), 1)]
        for m in 0..<3 {
            let fm = CGFloat(m)
            let my: CGFloat = hy + 10 + fm * 22
            let przes: CGFloat = sin(t * 0.4 + fm) * 18
            ctx.fill(prost(przes - 20, my - 12, w + 40, 28), with: pion(stopyMgly, my - 12, my + 16))
        }
        // bliższa łąka
        let laka: [Gradient.Stop]
        if noc {
            laka = [stop(hex("#281c2d"), 0), stop(hex("#1c1422"), 1)]
        } else {
            laka = [stop(hex("#7d4627"), 0), stop(hex("#5e3219"), 1)]
        }
        ctx.fill(sciezkaWzgorza(w, h, hy + 32, 4, 0.010, 2, 0.022, 3.1), with: pion(laka, hy + 26, h))
        // jesienne drzewa
        let dzienne: [Color] = [hex("#d9531e"), hex("#e88d14"), hex("#b8291d"), hex("#f2b705"), hex("#9e331e")]
        let nocne: [Color] = [hex("#542317"), hex("#663a14"), hex("#471518"), hex("#6e4f11"), hex("#3b1812")]
        let kolory: [Color] = noc ? nocne : dzienne
        let pien: Color = noc ? hex("#120b0a") : hex("#2e1610")
        let n: Int = max(10, Int(floor(w / 24)))
        let fn = CGFloat(n)
        for i in 0...n {
            let fi = CGFloat(i)
            let tx: CGFloat = fi / fn * w + sin(fi * 3.7) * 8
            let ty0: CGFloat = hy + 34 + CGFloat(i % 3) * 14
            let ty: CGFloat = ty0 + sin(fi * 1.9) * 5
            let sway: CGFloat = sin(t * 1.4 + fi * 0.9) * 2.2
            let r: CGFloat = 11 + CGFloat(i % 4) * 3
            ctx.stroke(linia(tx, ty + r + 8, tx + sway * 0.5, ty), with: .color(pien), lineWidth: 2.2)
            var korona = kolo(tx + sway, ty, r)
            korona.addPath(kolo(tx - r * 0.45 + sway, ty + 3, r * 0.72))
            korona.addPath(kolo(tx + r * 0.45 + sway, ty + 3, r * 0.72))
            ctx.fill(korona, with: .color(kolory[i % 5]))
        }
        // dywan opadłych liści
        let ileLisci: Int = max(18, Int(floor(w / 16)))
        let modY: Int = max(12, Int(floor(h - hy - 46)))
        let alfa: Double = noc ? 0.45 : 0.75
        for l in 0..<ileLisci {
            let lx: CGFloat = CGFloat((l * 47 + 13) % 100) / 100 * w
            let ly: CGFloat = hy + 44 + CGFloat((l * 29) % modY)
            let kat: CGFloat = CGFloat(l % 5) * 0.3
            ctx.fill(elipsa(lx, ly, 3.2, 1.4, kat), with: .color(kolory[l % 5].opacity(alfa)))
        }
    }

    static func jezioroJesienne(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat, noc: Bool) {
        let hy: CGFloat = h * 0.58
        let fy: CGFloat = h * 0.80
        // daleki brzeg z jesiennym lasem
        wzgorze(&ctx, w, h, hy - 10, 10, 0.014, 5, 0.03, noc ? hex("#1c1829") : hex("#63382c"), 0.4)
        let nocne: [Color] = [hex("#4a2218"), hex("#5c3614"), hex("#3d1618"), hex("#614612")]
        let dzienne: [Color] = [hex("#d94e1f"), hex("#eb9815"), hex("#b5281c"), hex("#f5b807")]
        let kolory: [Color] = noc ? nocne : dzienne
        let n: Int = max(14, Int(floor(w / 18)))
        let fn = CGFloat(n)
        for i in 0...n {
            let fi = CGFloat(i)
            let tx: CGFloat = fi / fn * w
            let ty: CGFloat = hy - 5 + sin(fi * 2.1) * 4
            let r: CGFloat = 7 + CGFloat(i % 3) * 2.5
            ctx.fill(kolo(tx, ty, r), with: .color(kolory[i % 4]))
        }
        // tafla jeziora
        let tafla: [Gradient.Stop]
        if noc {
            tafla = [stop(hex("#181930"), 0), stop(hex("#111629"), 0.45), stop(hex("#090c18"), 1)]
        } else {
            tafla = [stop(hex("#a3583b"), 0), stop(hex("#424866"), 0.35), stop(hex("#24314a"), 0.75), stop(hex("#162033"), 1)]
        }
        ctx.fill(prost(0, hy, w, h - hy), with: pion(tafla, hy, h))
        // złote refleksy drzew w wodzie
        let refleks: Color = noc ? rgba(240, 170, 80, 0.09) : rgba(255, 185, 75, 0.20)
        let krokY: CGFloat = (fy - hy) / 20
        for i in 0..<20 {
            let fi = CGFloat(i)
            let ry: CGFloat = hy + 8 + fi * krokY
            let rw: CGFloat = 28 + CGFloat(i % 4) * 18
            let rx0: CGFloat = CGFloat(i * 53).truncatingRemainder(dividingBy: w)
            let rx: CGFloat = rx0 + sin(t * 1.4 + fi) * 6
            ctx.fill(prost(rx - rw * 0.5, ry, rw, 2), with: .color(refleks))
        }
        // mgiełka nad taflą
        let przes: CGFloat = sin(t * 0.45) * 15
        let srodekMgly: Color = noc ? rgba(200, 215, 240, 0.16) : rgba(255, 240, 220, 0.32)
        let mgla: [Gradient.Stop] = [stop(rgba(255, 240, 220, 0), 0), stop(srodekMgly, 0.5), stop(rgba(255, 240, 220, 0), 1)]
        ctx.fill(prost(przes - 20, hy - 8, w + 40, 34), with: pion(mgla, hy - 8, hy + 26))
        // drewniany pomost z prawej
        let pierY: CGFloat = fy - 12
        let x0: CGFloat = w * 0.58
        let x1: CGFloat = w * 1.02
        var slupki = Path()
        var px: CGFloat = x0 + 6
        while px < w {
            slupki.move(to: pt(px, pierY - 6))
            slupki.addLine(to: pt(px, pierY + 22))
            px += 22
        }
        let kolorSlupkow: Color = noc ? hex("#1a110d") : hex("#3d2518")
        ctx.stroke(slupki, with: .color(kolorSlupkow), lineWidth: 4)
        let kolorPokladu: Color = noc ? hex("#291a14") : hex("#573624")
        ctx.fill(prost(x0, pierY - 3, x1 - x0, 5), with: .color(kolorPokladu))
        // latarenka zawsze zapalona
        let lx: CGFloat = x0 + 8
        let ly: CGFloat = pierY - 22
        var slup = Path()
        slup.move(to: pt(lx, pierY - 3))
        slup.addLine(to: pt(lx, ly))
        slup.addLine(to: pt(lx - 6, ly))
        let kolorSlupa: Color = noc ? hex("#241712") : hex("#42291b")
        ctx.stroke(slup, with: .color(kolorSlupa), lineWidth: 2.2)
        let swiatlo: [Gradient.Stop] = [stop(rgba(255, 220, 120, 0.95), 0), stop(rgba(255, 170, 60, 0.35), 0.4), stop(rgba(255, 140, 40, 0), 1)]
        ctx.fill(kolo(lx - 6, ly + 3, 28), with: promien(swiatlo, lx - 6, ly + 3, 1, 28))
        // kołyszące się trzciny z pałkami
        let lodyga: Color = noc ? hex("#141a14") : hex("#33291a")
        let palka: Color = noc ? hex("#1f120d") : hex("#4a2514")
        for r in 0..<9 {
            let fr = CGFloat(r)
            let rx: CGFloat = 12 + fr * 11
            let ry: CGFloat = fy + 18
            let sway: CGFloat = sin(t * 1.5 + fr * 0.6) * 4
            let dodatek: CGFloat = CGFloat(r % 3) * 6
            var p = Path()
            p.move(to: pt(rx, ry))
            p.addQuadCurve(to: pt(rx + sway, ry - 42 - dodatek), control: pt(rx + sway * 0.5, ry - 22))
            ctx.stroke(p, with: .color(lodyga), lineWidth: 1.6)
            if r % 2 == 0 {
                ctx.fill(elipsa(rx + sway * 0.85, ry - 34 - dodatek, 2.2, 6, sway * 0.03), with: .color(palka))
            }
        }
    }

    static func alejaParkowa(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat, noc: Bool) {
        let hy: CGFloat = h * 0.58
        // korony drzew w oddali
        wzgorze(&ctx, w, h, hy + 8, 12, 0.015, 5, 0.03, noc ? hex("#121829") : hex("#3b3345"), 1.1)
        // mokra aleja
        let y0: CGFloat = hy + 15
        let bruk: [Gradient.Stop]
        if noc {
            bruk = [stop(hex("#0e1320"), 0), stop(hex("#151d2e"), 0.5), stop(hex("#090d16"), 1)]
        } else {
            bruk = [stop(hex("#282633"), 0), stop(hex("#333140"), 0.5), stop(hex("#1f1d29"), 1)]
        }
        ctx.fill(prost(0, y0, w, h - y0), with: pion(bruk, y0, h))
        // drzewa parkowe po bokach
        let pien: Color = noc ? hex("#0a0e17") : hex("#241914")
        let korona: Color = noc ? rgba(65, 40, 30, 0.72) : rgba(185, 95, 35, 0.82)
        let pozycje: [CGFloat] = [0.06, 0.24, 0.76, 0.94]
        for (idx, udzial) in pozycje.enumerated() {
            let tx: CGFloat = w * udzial
            let ty: CGFloat = hy - 12 + CGFloat(idx % 2) * 10
            let sway: CGFloat = sin(t * 1.1 + CGFloat(idx)) * 2
            ctx.stroke(linia(tx, hy + 25, tx + sway * 0.4, ty), with: .color(pien), lineWidth: 3.5)
            var k = kolo(tx + sway, ty, 22)
            k.addPath(kolo(tx - 12 + sway, ty + 8, 16))
            k.addPath(kolo(tx + 12 + sway, ty + 8, 16))
            ctx.fill(k, with: .color(korona))
        }
        // latarnie zawsze zapalone
        let latarnie: [(CGFloat, CGFloat, CGFloat, CGFloat)] = [
            (w * 0.16, hy + 58, 66, 1.0),
            (w * 0.84, hy + 58, 66, 1.0),
            (w * 0.33, hy + 28, 44, 0.68),
            (w * 0.67, hy + 28, 44, 0.68)
        ]
        for (i, l) in latarnie.enumerated() {
            latarnia(&ctx, x: l.0, yBase: l.1, wysokosc: l.2, skala: l.3, i: i, t: t)
        }
    }

    static func latarnia(_ ctx: inout GraphicsContext, x: CGFloat, yBase: CGFloat, wysokosc: CGFloat, skala sk: CGFloat, i: Int, t: CGFloat) {
        let top: CGFloat = yBase - wysokosc
        let arg: CGFloat = t * 4.5 + CGFloat(i) * 2.1
        let f: Double = 0.92 + 0.08 * Double(sin(arg))
        // stożek światła
        let dolY: CGFloat = yBase + 18
        let stozek: Path = wielokat([pt(x - 4 * sk, top + 4), pt(x + 4 * sk, top + 4), pt(x + 30 * sk, dolY), pt(x - 30 * sk, dolY)])
        let stopyStozka: [Gradient.Stop] = [stop(rgba(255, 195, 95, 0.36 * f), 0), stop(rgba(255, 170, 60, 0), 1)]
        ctx.fill(stozek, with: pion(stopyStozka, top, dolY))
        // słup
        ctx.stroke(linia(x, yBase, x, top), with: .color(hex("#141821")), lineWidth: 2.6 * sk)
        // aureola wokół klosza
        let rHalo: CGFloat = 24 * sk
        let halo: [Gradient.Stop] = [stop(rgba(255, 235, 170, 0.95 * f), 0), stop(rgba(255, 175, 65, 0.42 * f), 0.4), stop(rgba(255, 140, 30, 0), 1)]
        ctx.fill(kolo(x, top, rHalo), with: promien(halo, x, top, 1, rHalo))
        // odbicie na mokrym bruku
        let odb: [Gradient.Stop] = [stop(rgba(255, 190, 80, 0.38 * f), 0), stop(rgba(255, 160, 50, 0), 1)]
        ctx.fill(elipsa(x, yBase + 18 * sk, 10 * sk, 20 * sk), with: pion(odb, yBase, yBase + 42 * sk))
    }
}

// MARK: - Zima: osada, szczyty, choinka (1711-2010)

extension RysunkiStrony {
    static func osadaZimowa(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat, noc: Bool) {
        let hy: CGFloat = h * 0.58
        let fy: CGFloat = h * 0.80
        // dalekie ośnieżone wzgórza
        wzgorze(&ctx, w, h, hy - 8, 15, 0.012, 7, 0.025, noc ? hex("#1b2b48") : hex("#89a7c2"), 1.0)
        wzgorze(&ctx, w, h, hy + 18, 12, 0.016, 5, 0.035, noc ? hex("#233659") : hex("#a8c2d8"), 2.4)
        // zaśnieżone świerki
        let n: Int = max(8, Int(floor(w / 32)))
        let fn = CGFloat(n)
        for i in 0..<n {
            let px: CGFloat = (CGFloat(i) + 0.4) / fn * w
            let py: CGFloat = hy + 26 + CGFloat(i % 3) * 10
            let sk: CGFloat = 0.75 + CGFloat(i % 3) * 0.2
            swierk(&ctx, x: px, y: py, skala: sk, noc: noc)
        }
        // bliższa zaspa
        wzgorze(&ctx, w, h, hy + 48, 9, 0.015, 4, 0.03, noc ? hex("#2b4066") : hex("#c6dced"), 0.4)
        // chatki z oknami i dymem
        chatka(&ctx, x: w * 0.22, y: hy + 46, skala: 1.0, idx: 0, t: t, noc: noc)
        chatka(&ctx, x: w * 0.74, y: hy + 50, skala: 0.92, idx: 1, t: t, noc: noc)
        // zaspa na pierwszym planie
        wzgorze(&ctx, w, h, fy + 4, 8, 0.014, 4, 0.03, noc ? hex("#192742") : hex("#9ab6cc"), 2.0)
    }

    static func swierk(_ ctx: inout GraphicsContext, x: CGFloat, y: CGFloat, skala: CGFloat, noc: Bool) {
        var c = ctx
        c.translateBy(x: x, y: y)
        c.scaleBy(x: skala, y: skala)
        let igly: Color = noc ? hex("#122624") : hex("#2b4d43")
        let snieg: Color = noc ? rgba(210, 230, 255, 0.72) : rgba(255, 255, 255, 0.9)
        for warstwa in 0..<3 {
            let fw = CGFloat(warstwa)
            let ly: CGFloat = -fw * 9
            let lw: CGFloat = 14 - fw * 3.2
            c.fill(wielokat([pt(0, ly - 14), pt(-lw, ly), pt(lw, ly)]), with: .color(igly))
            c.fill(wielokat([pt(0, ly - 14), pt(-lw * 0.65, ly - 5), pt(lw * 0.65, ly - 5)]), with: .color(snieg))
        }
    }

    static func chatka(_ ctx: inout GraphicsContext, x: CGFloat, y: CGFloat, skala: CGFloat, idx: Int, t: CGFloat, noc: Bool) {
        var c = ctx
        c.translateBy(x: x, y: y)
        c.scaleBy(x: skala, y: skala)
        let fi = CGFloat(idx)
        // dym z komina
        for k in 0..<5 {
            let prog: CGFloat = ulamek(t * 0.45 + CGFloat(k) * 0.2 + fi * 0.5)
            let arg: CGFloat = prog * 2 * CGFloat.pi + t
            let sx: CGFloat = 9 + sin(arg) * 7
            let sy: CGFloat = -22 - prog * 30
            let sr: CGFloat = 3 + prog * 7
            let alfa: Double = Double(1 - prog) * 0.30
            c.fill(kolo(sx, sy, sr), with: .color(rgba(220, 230, 245, alfa)))
        }
        // komin
        c.fill(prost(6, -22, 6, 10), with: .color(hex("#3b231e")))
        // ściany
        let sciany: Color = noc ? hex("#261715") : hex("#59372b")
        c.fill(prost(-16, -12, 32, 20), with: .color(sciany))
        // ośnieżony dach
        let dach: Color = noc ? hex("#c8ddf5") : hex("#ffffff")
        c.fill(wielokat([pt(-21, -11), pt(0, -25), pt(21, -11)]), with: .color(dach))
        // ciepłe okna z poświatą
        let g: Double = 0.85 + 0.15 * Double(sin(t * 3 + fi * 2))
        var okna = c
        okna.addFilter(.shadow(color: rgba(255, 190, 70, 0.95), radius: 6))
        var szyby = prost(-10, -7, 7, 7)
        szyby.addRect(CGRect(x: 3, y: -7, width: 7, height: 7))
        okna.fill(szyby, with: .color(rgba(255, 215, 110, g)))
        // łuna na śniegu
        let luna: [Gradient.Stop] = [stop(rgba(255, 190, 80, 0.32 * g), 0), stop(rgba(255, 190, 80, 0), 1)]
        c.fill(elipsa(0, 12, 22, 8), with: promien(luna, 0, 12, 2, 22))
    }

    static func szczytyAlpejskie(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat, noc: Bool) {
        let hy: CGFloat = h * 0.58
        let fy: CGFloat = h * 0.80
        // zorza polarna nocą
        if noc { zorza(&ctx, w: w, hy: hy, t: t) }
        // pasmo górskie ze śnieżnymi czapami
        let podstawa: CGFloat = hy + 10
        let czapa: Color = noc ? rgba(215, 235, 255, 0.78) : rgba(255, 255, 255, 0.92)
        let k0: Color = noc ? hex("#1b2c47") : hex("#536f8a")
        let k1: Color = noc ? hex("#182740") : hex("#4a647d")
        let k2: Color = noc ? hex("#243a5e") : hex("#6585a3")
        let lista: [(CGFloat, CGFloat, CGFloat, Color)] = [
            (w * 0.16, hy - 54, w * 0.34, k0),
            (w * 0.80, hy - 48, w * 0.36, k1),
            (w * 0.48, hy - 72, w * 0.42, k2)
        ]
        for szczyt in lista {
            let x: CGFloat = szczyt.0
            let y: CGFloat = szczyt.1
            let pw: CGFloat = szczyt.2
            ctx.fill(wielokat([pt(x - pw, podstawa), pt(x, y), pt(x + pw, podstawa)]), with: .color(szczyt.3))
            let hh: CGFloat = podstawa - y
            let punkty: [CGPoint] = [
                pt(x, y),
                pt(x - pw * 0.36, y + hh * 0.36),
                pt(x - pw * 0.12, y + hh * 0.28),
                pt(x + pw * 0.08, y + hh * 0.38),
                pt(x + pw * 0.36, y + hh * 0.34)
            ]
            ctx.fill(wielokat(punkty), with: .color(czapa))
        }
        // zamarznięte jezioro
        let y0: CGFloat = hy + 8
        let lod: [Gradient.Stop]
        if noc {
            lod = [stop(hex("#0d1b30"), 0), stop(hex("#132845"), 0.4), stop(hex("#091221"), 1)]
        } else {
            lod = [stop(hex("#3e6280"), 0), stop(hex("#2b4a66"), 0.5), stop(hex("#1c3247"), 1)]
        }
        ctx.fill(prost(0, y0, w, h - y0), with: pion(lod, y0, h))
        // refleksy lodu i zorzy
        let refleks: Color = noc ? rgba(90, 235, 190, 0.12) : rgba(255, 255, 255, 0.18)
        let krok: CGFloat = (fy - hy) / 16
        for i in 0..<16 {
            let fi = CGFloat(i)
            let ry: CGFloat = hy + 14 + fi * krok
            let rw: CGFloat = 35 + sin(fi * 2.3 + t) * 20
            let rx: CGFloat = w * 0.5 + sin(t * 0.9 + fi) * (w * 0.18)
            ctx.fill(prost(rx - rw * 0.5, ry, rw, 1.8), with: .color(refleks))
        }
    }

    static func zorza(_ ctx: inout GraphicsContext, w: CGFloat, hy: CGFloat, t: CGFloat) {
        for pas in 0..<2 {
            let fp = CGFloat(pas)
            let baseY: CGFloat = hy * (0.28 + fp * 0.16)
            var p = Path()
            p.move(to: pt(0, baseY + 35))
            var x: CGFloat = 0
            while x <= w {
                let k1: CGFloat = x * 0.015 + t * 0.7 + fp * 2
                let k2: CGFloat = x * 0.032 - t * 0.5
                let ay: CGFloat = baseY + sin(k1) * 14 + cos(k2) * 7
                p.addLine(to: pt(x, ay))
                x += 6
            }
            p.addLine(to: pt(w, baseY + 45))
            p.addLine(to: pt(0, baseY + 45))
            p.closeSubpath()
            let stopy: [Gradient.Stop]
            if pas == 0 {
                stopy = [stop(rgba(30, 235, 160, 0), 0), stop(rgba(30, 235, 160, 0.28), 0.45), stop(rgba(90, 130, 255, 0), 1)]
            } else {
                stopy = [stop(rgba(130, 90, 255, 0), 0), stop(rgba(50, 215, 200, 0.22), 0.45), stop(rgba(30, 235, 160, 0), 1)]
            }
            ctx.fill(p, with: pion(stopy, baseY - 20, baseY + 45))
        }
    }

    static func zimaSwiateczna(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat, noc: Bool) {
        let hy: CGFloat = h * 0.58
        let fy: CGFloat = h * 0.80
        // ośnieżone wzgórza
        wzgorze(&ctx, w, h, hy - 6, 14, 0.012, 6, 0.025, noc ? hex("#182744") : hex("#7f9eb8"), 0.8)
        wzgorze(&ctx, w, h, hy + 18, 11, 0.016, 5, 0.032, noc ? hex("#213559") : hex("#9ebad1"), 2.1)
        wzgorze(&ctx, w, h, hy + 46, 9, 0.015, 4, 0.03, noc ? hex("#1b2c4a") : hex("#8aa8c2"), 1.2)
        // ciepła łuna za choinką
        let tx: CGFloat = w * 0.80
        let baza: CGFloat = fy - 2
        let aura: [Gradient.Stop] = [stop(rgba(255, 210, 90, 0.35), 0), stop(rgba(255, 140, 50, 0.14), 0.5), stop(rgba(255, 140, 50, 0), 1)]
        ctx.fill(kolo(tx, baza - 38, 65), with: promien(aura, tx, baza - 38, 4, 65))
        // choinka po prawej
        drzewko(&ctx, x: tx, y: baza, t: t, noc: noc)
        // girlanda świetlna wzdłuż horyzontu po lewej
        let gMax: CGFloat = w * 0.62
        var gx: CGFloat = 16
        while gx < gMax {
            let gy: CGFloat = hy + 34 + sin(gx * 0.08) * 4
            let tw: CGFloat = 0.5 + 0.5 * sin(t * 2.8 + gx)
            let alfa: Double = 0.4 + 0.5 * Double(tw)
            ctx.fill(kolo(gx, gy, 2), with: .color(rgba(255, 215, 110, alfa)))
            gx += 18
        }
        // zaspa na pierwszym planie
        wzgorze(&ctx, w, h, fy + 4, 7, 0.014, 3, 0.03, noc ? hex("#142038") : hex("#7896b0"), 0.5)
    }

    static func drzewko(_ ctx: inout GraphicsContext, x: CGFloat, y: CGFloat, t: CGFloat, noc: Bool) {
        var c = ctx
        c.translateBy(x: x, y: y)
        // pień
        c.fill(prost(-5, -8, 10, 10), with: .color(hex("#3b2316")))
        // 4 piętra gałęzi ze śniegiem
        let igly: Color = noc ? hex("#0f3325") : hex("#1b4d38")
        let snieg: Color = rgba(240, 248, 255, 0.75)
        for l in 0..<4 {
            let fl = CGFloat(l)
            let ly: CGFloat = -8 - fl * 16
            let lw: CGFloat = 30 - fl * 6
            c.fill(wielokat([pt(0, ly - 24), pt(-lw, ly), pt(lw, ly)]), with: .color(igly))
            let punkty: [CGPoint] = [pt(-lw, ly), pt(-lw * 0.55, ly - 5), pt(0, ly - 2), pt(lw * 0.55, ly - 5), pt(lw, ly)]
            c.fill(wielokat(punkty), with: .color(snieg))
        }
        // złota gwiazda na czubku
        let puls: CGFloat = 0.85 + 0.15 * sin(t * 4)
        var gwiazda = Path()
        for s in 0..<10 {
            let r: CGFloat = (s % 2 == 0) ? 8 : 3.5
            let a: CGFloat = CGFloat(s) * CGFloat.pi / 5 - CGFloat.pi / 2
            let p: CGPoint = pt(cos(a) * r, -80 + sin(a) * r)
            if s == 0 { gwiazda.move(to: p) } else { gwiazda.addLine(to: p) }
        }
        gwiazda.closeSubpath()
        var cg = c
        cg.addFilter(.shadow(color: rgba(255, 225, 80, 0.95), radius: 7 * puls))
        cg.fill(gwiazda, with: .color(hex("#ffe066")))
        // migoczące lampki
        let barwy: [Color] = [hex("#ff4d4d"), hex("#ffd11a"), hex("#33ccff"), hex("#ff66b3"), hex("#66ff66"), hex("#ffffff")]
        let lampki: [CGPoint] = [
            pt(-16, -14), pt(-4, -16), pt(10, -13),
            pt(-12, -28), pt(2, -30), pt(14, -27),
            pt(-9, -44), pt(4, -45), pt(-3, -58), pt(5, -60)
        ]
        for (bi, b) in lampki.enumerated() {
            let tw: CGFloat = 0.45 + 0.55 * abs(sin(t * 3.5 + CGFloat(bi) * 1.3))
            let kolor: Color = barwy[bi % 6]
            var cl = c
            cl.addFilter(.shadow(color: kolor, radius: 4 * tw))
            cl.fill(kolo(b.x, b.y, 2.3), with: .color(kolor))
        }
        // prezenty pod choinką
        c.fill(prost(-16, -4, 9, 7), with: .color(hex("#d92626")))
        c.fill(prost(-12.5, -4, 2, 7), with: .color(hex("#ffd11a")))
        c.fill(prost(7, -3, 8, 6), with: .color(hex("#2563eb")))
        c.fill(prost(10, -3, 2, 6), with: .color(hex("#f8fafc")))
    }
}

// MARK: - Sylwester (new-year), Wielkanoc, biwak powstańczy (2012-2234, 2721-2821)

extension RysunkiStrony {
    static func miastoNoworoczne(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat) {
        let hy: CGFloat = h * 0.58
        // łuna miasta nad horyzontem
        let luna: [Gradient.Stop] = [stop(rgba(255, 160, 60, 0), 0), stop(rgba(255, 175, 75, 0.28), 1)]
        ctx.fill(prost(0, hy - 45, w, 55), with: pion(luna, hy - 45, hy + 10))
        // sylwetka miasta; okna zbierane w jedną ścieżkę (nie nachodzą na sąsiednie budynki)
        let n: Int = max(12, Int(floor(w / 22)))
        let bw: CGFloat = w / CGFloat(n)
        let ciemny: Color = hex("#0c1021")
        let jasny: Color = hex("#121830")
        var okna = Path()
        for i in 0..<n {
            let fi = CGFloat(i)
            let bx: CGFloat = fi * bw
            let bh: CGFloat = 18 + CGFloat((i * 37 + 13) % 36)
            let by: CGFloat = hy - bh + 6
            ctx.fill(prost(bx, by, bw + 1, bh), with: .color(i % 2 == 0 ? ciemny : jasny))
            let koniecX: CGFloat = bx + bw - 4
            let koniecY: CGFloat = hy - 2
            var wy: CGFloat = by + 4
            while wy < koniecY {
                var wx: CGFloat = bx + 3
                while wx < koniecX {
                    // jak na stronie: reszta z liczb niecałkowitych, więc prawie wszystkie okna świecą
                    let v: CGFloat = wx * 13 + wy * 7 + fi
                    if v.truncatingRemainder(dividingBy: 5) != 0 {
                        okna.addRect(CGRect(x: wx, y: wy, width: 2.2, height: 3))
                    }
                    wx += 5
                }
                wy += 6
            }
        }
        ctx.fill(okna, with: .color(rgba(255, 220, 130, 0.65)))
        // zatoka odbijająca światła
        let y0: CGFloat = hy + 6
        let woda: [Gradient.Stop] = [stop(hex("#10152e"), 0), stop(hex("#0b0f24"), 0.5), stop(hex("#060814"), 1)]
        ctx.fill(prost(0, y0, w, h - y0), with: pion(woda, y0, h))
        // fajerwerki
        for f in 0..<4 {
            fajerwerk(&ctx, f: f, w: w, hy: hy, t: t)
        }
    }

    static func fajerwerk(_ ctx: inout GraphicsContext, f: Int, w: CGFloat, hy: CGFloat, t: CGFloat) {
        let ff = CGFloat(f)
        let cykl: CGFloat = 2.6
        let lt: CGFloat = modulo(t + ff * 0.65, cykl)
        let udzialX: CGFloat = 0.20 + ff * 0.21 + sin(ff * 3.1) * 0.04
        let fx: CGFloat = w * udzialX
        let fy: CGFloat = hy * (0.24 + CGFloat(f % 2) * 0.16)
        let pal: [Color]
        switch f {
        case 0: pal = [hex("#ffd700"), hex("#ffae00"), hex("#ffffff")]
        case 1: pal = [hex("#ff3366"), hex("#ff80bf"), hex("#ffd700")]
        case 2: pal = [hex("#00e5ff"), hex("#80ffff"), hex("#ffffff")]
        default: pal = [hex("#a855f7"), hex("#fb7185"), hex("#fde047")]
        }
        if lt < 0.55 {
            // wznoszenie rakiety
            let p: CGFloat = lt / 0.55
            let ry: CGFloat = (hy - 5) - p * (hy - 5 - fy)
            ctx.fill(kolo(fx, ry, 2), with: .color(hex("#ffe066")))
            let smuga: [Gradient.Stop] = [stop(rgba(255, 220, 100, 0.8), 0), stop(rgba(255, 160, 50, 0), 1)]
            let konSmugi: CGFloat = min(hy, ry + 22)
            ctx.stroke(linia(fx, ry, fx, konSmugi), with: pion(smuga, ry, ry + 22), lineWidth: 1.6)
        } else {
            // rozbłysk
            let p: CGFloat = (lt - 0.55) / (cykl - 0.55)
            let potega: Double = pow(Double(1 - p), 2.5)
            let ease: CGFloat = 1 - CGFloat(potega)
            let maxR: CGFloat = 32 + CGFloat(f % 2) * 12
            let alfa: Double = max(0, 1 - Double(p) * 1.1)
            let grawitacja: CGFloat = p * p * 16
            let rKropki: CGFloat = 1.8 * (1 - p * 0.5)
            var kropki: [Path] = [Path(), Path(), Path()]
            for r in 0..<18 {
                let kat: CGFloat = CGFloat(r) / 18 * 2 * CGFloat.pi + ff * 0.4
                let dist: CGFloat = ease * maxR * (0.85 + CGFloat(r % 3) * 0.12)
                let px: CGFloat = fx + cos(kat) * dist
                let py: CGFloat = fy + sin(kat) * dist + grawitacja
                kropki[r % 3].addEllipse(in: CGRect(x: px - rKropki, y: py - rKropki, width: rKropki * 2, height: rKropki * 2))
            }
            for k in 0..<3 {
                let kolor: Color = pal[k].opacity(alfa)
                var c = ctx
                c.addFilter(.shadow(color: kolor, radius: 3))
                c.fill(kropki[k], with: .color(kolor))
            }
            // odbicie rozbłysku w wodzie
            let refY: CGFloat = hy + 18 + ff * 12
            ctx.fill(elipsa(fx, refY, maxR * ease * 0.65, 6), with: .color(pal[0].opacity(alfa * 0.25)))
        }
    }

    static func wielkanoc(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat, noc: Bool) {
        let hy: CGFloat = h * 0.58
        let fy: CGFloat = h * 0.80
        // zielone wzgórza
        wzgorze(&ctx, w, h, hy - 8, 14, 0.012, 6, 0.025, noc ? hex("#152e26") : hex("#52b788"), 0.6)
        wzgorze(&ctx, w, h, hy + 18, 12, 0.016, 5, 0.032, noc ? hex("#11241d") : hex("#40916c"), 1.9)
        wzgorze(&ctx, w, h, hy + 46, 9, 0.015, 4, 0.03, noc ? hex("#0d1c16") : hex("#2d6a4f"), 3.2)
        // kwitnące drzewa po bokach
        let drzewa: [(CGFloat, CGFloat, CGFloat)] = [(w * 0.14, hy + 24, 18), (w * 0.86, hy + 20, 20)]
        let pien: Color = noc ? hex("#1f1410") : hex("#4a2e21")
        let kwiecie: Color = noc ? hex("#592e46") : hex("#ff9ebb")
        let blask: Color = noc ? hex("#7a4462") : hex("#ffc2d4")
        for (idx, d) in drzewa.enumerated() {
            let x: CGFloat = d.0
            let y: CGFloat = d.1
            let r: CGFloat = d.2
            let sway: CGFloat = sin(t * 1.2 + CGFloat(idx)) * 2
            ctx.stroke(linia(x, y + r + 12, x + sway * 0.4, y), with: .color(pien), lineWidth: 3)
            var k = kolo(x + sway, y, r)
            k.addPath(kolo(x - r * 0.55 + sway, y + 4, r * 0.75))
            k.addPath(kolo(x + r * 0.55 + sway, y + 4, r * 0.75))
            ctx.fill(k, with: .color(kwiecie))
            ctx.fill(kolo(x + sway - 3, y - 3, r * 0.6), with: .color(blask))
        }
        // kwiaty
        let barwy: [Color] = [hex("#ffd166"), hex("#ef476f"), hex("#ffffff"), hex("#ff9f1c"), hex("#c77dff")]
        let lodyga: Color = noc ? hex("#1b4332") : hex("#52b788")
        let n: Int = max(12, Int(floor(w / 22)))
        let fn = CGFloat(n)
        for i in 0..<n {
            let fi = CGFloat(i)
            let kx: CGFloat = (fi + 0.5) / fn * w + sin(fi * 4.1) * 6
            let ky: CGFloat = hy + 52 + CGFloat(i % 3) * 14
            let sway: CGFloat = sin(t * 1.8 + fi) * 1.8
            ctx.stroke(linia(kx, ky + 8, kx + sway, ky), with: .color(lodyga), lineWidth: 1.4)
            ctx.fill(kolo(kx + sway, ky, 3.2), with: .color(barwy[i % 5]))
        }
        // pisanki w trawie
        let pisanki: [(CGFloat, String, String)] = [(0.22, "#ff6b6b", "#ffd166"), (0.48, "#4cc9f0", "#ffffff"), (0.76, "#ffd166", "#06d6a0")]
        for (ei, e) in pisanki.enumerated() {
            let ex: CGFloat = w * e.0
            let ey: CGFloat = fy - 6 + CGFloat(ei % 2) * 5
            var c = ctx
            c.translateBy(x: ex, y: ey)
            c.rotate(by: .radians(Double(ei - 1) * 0.18))
            c.fill(elipsa(0, 0, 5.5, 7.5), with: .color(hex(e.1)))
            c.stroke(linia(-5, 0, 5, 0), with: .color(hex(e.2)), lineWidth: 1.8)
        }
        // zajączek
        let zajac: Color = noc ? hex("#2b211b") : hex("#f8f9fa")
        let bx: CGFloat = w * 0.64
        let by: CGFloat = hy + 46
        var cialo = elipsa(bx, by, 6.5, 8.5)
        cialo.addPath(kolo(bx, by - 10, 5))
        ctx.fill(cialo, with: .color(zajac))
        var uszy = elipsa(bx - 2.5, by - 18, 1.8, 6, -0.15)
        uszy.addPath(elipsa(bx + 2.5, by - 18, 1.8, 6, 0.15))
        ctx.fill(uszy, with: .color(zajac))
        // pyłki w słońcu
        let pylek: Color = noc ? rgba(180, 255, 160, 0.65) : rgba(255, 245, 150, 0.85)
        for b in 0..<6 {
            let fb = CGFloat(b)
            let mx: CGFloat = modulo(fb * 67 + t * 14, w)
            let my0: CGFloat = hy * 0.45 + sin(t * 2.2 + fb * 1.7) * 18
            let my: CGFloat = my0 + fb * 8
            ctx.fill(kolo(mx, my, 2), with: .color(pylek))
        }
        // wzgórze na pierwszym planie
        wzgorze(&ctx, w, h, fy + 4, 7, 0.014, 3, 0.03, noc ? hex("#08130e") : hex("#1b4332"), 1.1)
    }

    static func biwak(_ ctx: inout GraphicsContext, w: CGFloat, h: CGFloat, t: CGFloat) {
        let hy: CGFloat = h * 0.58
        // wzgórza (te same barwy w dzień i w nocy)
        wzgorze(&ctx, w, h, hy - 10, 14, 0.012, 7, 0.026, hex("#141a2b"), 0.5)
        wzgorze(&ctx, w, h, hy + 14, 12, 0.015, 6, 0.030, hex("#0d1220"), 1.8)
        wzgorze(&ctx, w, h, hy + 36, 6, 0.011, 3, 0.022, hex("#080c16"), 3.0)
        // sosny we mgle (bez sosen przy ognisku)
        let n: Int = max(9, Int(floor(w / 36)))
        let fn = CGFloat(n)
        let ciemna: Color = rgba(12, 18, 32, 0.92)
        let jasna: Color = rgba(18, 26, 44, 0.86)
        let srodek: CGFloat = w * 0.56
        for i in 0...n {
            let fi = CGFloat(i)
            let px: CGFloat = fi / fn * w + sin(fi * 2.7) * 8
            if abs(px - srodek) < 42 { continue }
            let py: CGFloat = hy + 26 + CGFloat(i % 3) * 10
            let ph: CGFloat = 32 + CGFloat(i % 3) * 8
            let kolor: Color = i % 2 == 0 ? ciemna : jasna
            for pietro in 0..<3 {
                let fp = CGFloat(pietro)
                let ty: CGFloat = py - ph + fp * (ph * 0.28)
                let tw: CGFloat = 10 + fp * 5
                let dol: CGFloat = ty + ph * 0.42
                ctx.fill(wielokat([pt(px, ty), pt(px - tw, dol), pt(px + tw, dol)]), with: .color(kolor))
            }
        }
        // listopadowa mgła
        let stopyMgly: [Gradient.Stop] = [stop(rgba(215, 225, 240, 0), 0), stop(rgba(215, 225, 240, 0.16), 0.5), stop(rgba(215, 225, 240, 0), 1)]
        for m in 0..<3 {
            let fm = CGFloat(m)
            let my: CGFloat = hy + 12 + fm * 18
            let przes: CGFloat = sin(t * 0.45 + fm * 1.4) * 16
            ctx.fill(prost(przes - 20, my - 12, w + 40, 26), with: pion(stopyMgly, my - 12, my + 14))
        }
        ognisko(&ctx, fx: w * 0.55, fy: hy + 42, t: t)
    }

    static func ognisko(_ ctx: inout GraphicsContext, fx: CGFloat, fy: CGFloat, t: CGFloat) {
        let f1: CGFloat = 0.08 * sin(t * 9.5)
        let f2: CGFloat = 0.05 * cos(t * 14.2)
        let flick: CGFloat = 1 + f1 + f2
        // łuna ogniska
        let rLuny: CGFloat = 68 * flick
        let luna: [Gradient.Stop] = [stop(rgba(255, 145, 40, 0.52), 0), stop(rgba(220, 45, 35, 0.22), 0.5), stop(rgba(220, 45, 35, 0), 1)]
        ctx.fill(kolo(fx, fy - 8, rLuny), with: promien(luna, fx, fy - 8, 2, rLuny))
        // polana na krzyż
        var polana = linia(fx - 15, fy + 4, fx + 14, fy - 2)
        polana.addPath(linia(fx + 15, fy + 4, fx - 14, fy - 2))
        ctx.stroke(polana, with: .color(hex("#24140e")), style: StrokeStyle(lineWidth: 4, lineCap: .round))
        // płomienie
        let jezyki: [(CGFloat, CGFloat, String)] = [(-5, 18, "#ff5412"), (4, 15, "#ff7b1c"), (0, 22, "#ffd15c")]
        for (ti, j) in jezyki.enumerated() {
            let fti = CGFloat(ti)
            let fh: CGFloat = j.1
            let sway: CGFloat = sin(t * (7 + fti * 2.3) + fti) * 2.5
            let bx: CGFloat = fx + j.0
            let wierzch: CGFloat = fy - fh * 1.15 * flick
            var p = Path()
            p.move(to: pt(bx - 6, fy))
            p.addQuadCurve(to: pt(bx + sway * 0.6, wierzch), control: pt(bx + sway, fy - fh * flick))
            p.addQuadCurve(to: pt(bx + 6, fy), control: pt(bx + 6, fy - fh * 0.4))
            p.closeSubpath()
            ctx.fill(p, with: .color(hex(j.2)))
        }
        // iskry
        for e in 0..<12 {
            let fe = CGFloat(e)
            let tempo: CGFloat = 0.45 + CGFloat(e % 4) * 0.12
            let ep: CGFloat = ulamek(t * tempo + fe * 0.23)
            let ex: CGFloat = fx + sin(ep * 5 + fe * 1.7) * (8 + ep * 18)
            let ey: CGFloat = fy - 10 - ep * 68
            let g: Double = 190 + Double(e % 3) * 25
            let alfa: Double = Double(1 - ep) * 0.9
            ctx.fill(kolo(ex, ey, 1.5 * (1 - ep * 0.4)), with: .color(rgba(255, g, 80, alfa)))
        }
        // lanca z proporcem biało-czerwonym (jaskółczy ogon)
        let lx0: CGFloat = fx + 34
        let lyBot: CGFloat = fy + 8
        let lyTop: CGFloat = fy - 52
        ctx.stroke(linia(lx0, lyBot, lx0 + 2, lyTop), with: .color(hex("#8b6d4b")), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
        let pw: CGFloat = 28
        let ph2: CGFloat = 12
        let fala: CGFloat = sin(t * 3.5) * 2.2
        let x0: CGFloat = lx0 + 2
        let srodekY: CGFloat = lyTop + 2 + ph2 * 0.5
        var biala = Path()
        biala.move(to: pt(x0, lyTop + 2))
        biala.addQuadCurve(to: pt(x0 + pw, lyTop + 2 - fala * 0.5), control: pt(x0 + pw * 0.5, lyTop + fala))
        biala.addLine(to: pt(x0 + pw * 0.76, srodekY))
        biala.addLine(to: pt(x0, srodekY))
        biala.closeSubpath()
        ctx.fill(biala, with: .color(rgba(248, 250, 252, 0.95)))
        let dolY: CGFloat = lyTop + 2 + ph2
        var czerwona = Path()
        czerwona.move(to: pt(x0, srodekY))
        czerwona.addLine(to: pt(x0 + pw * 0.76, srodekY))
        czerwona.addLine(to: pt(x0 + pw, dolY - fala * 0.5))
        czerwona.addQuadCurve(to: pt(x0, dolY), control: pt(x0 + pw * 0.5, lyTop + ph2 + fala))
        czerwona.closeSubpath()
        ctx.fill(czerwona, with: .color(rgba(220, 20, 60, 0.95)))
    }
}
