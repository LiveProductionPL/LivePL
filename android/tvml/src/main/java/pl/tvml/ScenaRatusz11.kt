package pl.tvml

/*
 * Sceny ze strony tvml.pl przeniesione linia w linię przez [Plotno] (weather-effects.js, _drawCustomScene):
 * Ratusz w Lesznie na 11 Listopada (2236–2719) i ognisko powstańcze we mgle (2721–2821).
 * Niebo, gwiazdy i przyciemnienie dołu rysuje już [ScenyStrony.rysuj].
 */

/**
 * Geometria i kolory Ratusza (weather-effects.js 2238–2241, 2474–2475, 2511–2525): yRynek, yBase, scR, cxR,
 * przeliczenie „jednostek” Ratusza X(dx) / Y(dy) (y rośnie w górę) i pomocnik fillRectXY.
 */
private class Ratusz11Uklad(r: RamaSceny, panorama: Boolean = false) {
    private val plotno = r.ctx
    val yRynek = r.h * 0.645
    // ratusz bliżej i większy na szerokiej karcie aplikacji (PlanRatusza w ScenyRatuszSezon.kt, jak na iOS)
    val yBase = PlanRatusza.podstawa(r.w, r.h)
    val scR = (r.h / 276) * 0.96 * PlanRatusza.skala(r.w, r.h)
    val cxR = if (r.w < 560) (r.w * 0.666) else (r.w * 0.68)

    // 4. Architektura Ratusza w Lesznie (Merlini / Ferrari — Dzień w słońcu vs Noc Biało-Czerwona Iluminacja).
    // Panorama na zwykłe dni ([panoramaRatusza]): w dzień te same barwy, w nocy ciepłe światło latarni zamiast
    // biało-czerwonej iluminacji (jak PaletaRatusza(panoramaNoc:) na iOS).
    private val cieplaNoc = panorama && r.isNight
    val colPlinth = if (cieplaNoc) "#6b5d48" else if (r.isNight) "#ac1834" else "#b8a686"
    val colWall = if (cieplaNoc) "#d9cdb4" else if (r.isNight) "#e2dedc" else "#e7dcc9"
    val colWallSh = if (cieplaNoc) "#bfb196" else if (r.isNight) "#c4bcc0" else "#d2c6b1"
    val colPilaster = if (cieplaNoc) "#f3eee2" else if (r.isNight) "#fcfafc" else "#f9f7f2"
    val colTerracotta = if (cieplaNoc) "#7a3a2e" else if (r.isNight) "#d2203e" else "#a84e3c"
    val colRoof = if (r.isNight) "#1a202e" else "#383e48"
    val colHelmet = if (r.isNight) "#181d2a" else "#2a2f38"
    val colHelmetHi = if (r.isNight) "#343c4e" else "#444c58"
    val colWin = if (r.isNight) "rgba(255,218,128,0.94)" else "#3a4452"
    val bw = 39.0

    // const X = (dx) => cxR + dx * scR;  const Y = (dy) => yBase - dy * scR;
    fun X(dx: Double): Double = cxR + dx * scR
    fun X(dx: Int): Double = cxR + dx * scR
    fun Y(dy: Double): Double = yBase - dy * scR
    fun Y(dy: Int): Double = yBase - dy * scR

    fun fillRectXY(x1: Double, yTop: Double, x2: Double, yBot: Double, color: Any) {
        plotno.fillStyle = color
        plotno.fillRect(X(x1), Y(yTop), (x2 - x1) * scR, (yTop - yBot) * scR)
    }
}

/** weather-effects.js 2236–2719: Ratusz w Lesznie, Narodowe Święto Niepodległości (dzień i noc z biało-czerwoną iluminacją). */
internal fun RamaSceny.ratusz11Listopada() {
    // ══ 9. RATUSZ W LESZNIE — NARODOWE ŚWIĘTO NIEPODLEGŁOŚCI (Dzień & Noc Biało-Czerwona Iluminacja) ══
    val u = Ratusz11Uklad(this)
    // Tylko w aplikacji: przy zerowym rozmiarze krok okien kamienic (8·scR) byłby zerem i pętla nie miałaby końca.
    if (!(u.scR > 0.0) || !(w > 0.0)) return
    ratusz11Rynek(u)
    ratusz11Kamienice(u)
    ratusz11Iluminacja(u)
    ratusz11Korpus(u)
    ratusz11Wieza(u)
    with(u) {
        // 5. Trzy maszty flagowe obok schodów Ratusza (dokładnie jak na fotografii!)
        ratusz11Flaga(u, cxR - 17 * scR, yBase - 17 * scR, yBase + 2 * scR, 7.8 * scR, 4.5 * scR, 0.0, 0.0)
        ratusz11Flaga(u, cxR - 13 * scR, yBase - 19 * scR, yBase + 2 * scR, 7.8 * scR, 4.5 * scR, 0.0, 0.9)
        ratusz11Flaga(u, cxR - 9 * scR, yBase - 17 * scR, yBase + 2 * scR, 7.8 * scR, 4.5 * scR, 0.0, 1.8)

        // 6. Uroczysty maszt z powiewającą Flagą RP na pierwszym planie Rynku po lewej
        val fgPoleX = if (w < 560) (40 * (w / 380)) else (w * 0.085)
        ratusz11Flaga(u, fgPoleX, yBase - 42 * scR, h - 18 * scR, 30 * scR, 17 * scR, -1.5 * scR, 0.4)
    }
}

/** weather-effects.js 2243–2289: powiewająca jedwabna flaga biało-czerwona na maszcie (drawWavingPolishFlag). */
private fun RamaSceny.ratusz11Flaga(
    u: Ratusz11Uklad, poleX: Double, poleTopY: Double, poleBotY: Double,
    flagW: Double, flagH: Double, tiltX: Double, phaseOff: Double,
) {
    // Pomocnik: powiewająca jedwabna flaga biało-czerwona
    val scR = u.scR
    ctx.save()
    ctx.strokeStyle = "rgba(240,244,250,0.96)"
    ctx.lineWidth = Math.max(1.4, 1.9 * scR)
    ctx.lineCap = "round"
    ctx.beginPath()
    ctx.moveTo(poleX, poleBotY)
    ctx.lineTo(poleX + tiltX, poleTopY)
    ctx.stroke()

    ctx.fillStyle = "#f5d782"
    ctx.beginPath()
    ctx.arc(poleX + tiltX, poleTopY, Math.max(1.5, 2.0 * scR), 0.0, Math.PI * 2)
    ctx.fill()

    val steps = 18
    val px0 = poleX + tiltX
    val py0 = poleTopY + 1.8 * scR
    val waveT = t * 3.2 + phaseOff
    for (i in 0 until steps) {
        val u0 = i.toDouble() / steps
        val u1 = (i + 1).toDouble() / steps
        val fx0 = px0 + u0 * flagW
        val fx1 = px0 + u1 * flagW + 0.35
        val w0 = Math.sin(u0 * Math.PI * 2.2 - waveT) * (2.5 * u0 + 0.35) * scR
        val w1 = Math.sin(u1 * Math.PI * 2.2 - waveT) * (2.5 * u1 + 0.35) * scR
        val slope = Math.cos(u0 * Math.PI * 2.2 - waveT)
        val sh = 1.0 + 0.12 * slope
        val yTop0 = py0 + w0; val yTop1 = py0 + w1
        val yMid0 = py0 + w0 + 0.5 * flagH; val yMid1 = py0 + w1 + 0.5 * flagH
        val yBot0 = py0 + w0 + flagH; val yBot1 = py0 + w1 + flagH

        val wr = Math.min(255, Math.round(248 * sh).toInt())
        val wg = Math.min(255, Math.round(250 * sh).toInt())
        val wb = Math.min(255, Math.round(253 * sh).toInt())
        ctx.fillStyle = Plotno.rgba(wr, wg, wb, 0.98)
        ctx.beginPath()
        ctx.moveTo(fx0, yTop0); ctx.lineTo(fx1, yTop1); ctx.lineTo(fx1, yMid1); ctx.lineTo(fx0, yMid0)
        ctx.closePath(); ctx.fill()

        val rr = Math.min(255, Math.round(220 * sh).toInt())
        val rg = Math.min(255, Math.round(20 * sh).toInt())
        val rb = Math.min(255, Math.round(58 * sh).toInt())
        ctx.fillStyle = Plotno.rgba(rr, rg, rb, 0.98)
        ctx.beginPath()
        ctx.moveTo(fx0, yMid0); ctx.lineTo(fx1, yMid1); ctx.lineTo(fx1, yBot1); ctx.lineTo(fx0, yBot0)
        ctx.closePath(); ctx.fill()
    }
    ctx.restore()
}

/** weather-effects.js 2291–2341: obłoki w dzień, płyta Rynku z nocnymi odbiciami iluminacji i wachlarzowe łuki bruku. */
private fun RamaSceny.ratusz11Rynek(u: Ratusz11Uklad) {
    with(u) {
        // Delikatne jasne obłoki pierzaste w dzień
        if (!isNight && !isStormy && !isRainy) {
            ctx.save()
            val cDrift = Math.sin(t * 0.18) * 10
            val c1 = ctx.createRadialGradient(w * 0.32 + cDrift, h * 0.22, 0.0, w * 0.32 + cDrift, h * 0.22, w * 0.22)
            c1.addColorStop(0.0, "rgba(255,255,255,0.22)")
            c1.addColorStop(1.0, "rgba(255,255,255,0)")
            ctx.fillStyle = c1
            ctx.fillRect(0.0, 0.0, w, yRynek)
            ctx.restore()
        }

        // 1. Płyta Rynku w Lesznie (granitowy bruk + łuki perspektywiczne)
        val sqGrad = ctx.createLinearGradient(0.0, yRynek, 0.0, h)
        if (isNight) {
            sqGrad.addColorStop(0.0, "#151c2e")
            sqGrad.addColorStop(0.45, "#0e1422")
            sqGrad.addColorStop(1.0, "#080c16")
        } else {
            sqGrad.addColorStop(0.0, "#9197a2")
            sqGrad.addColorStop(0.45, "#747b87")
            sqGrad.addColorStop(1.0, "#505662")
        }
        ctx.fillStyle = sqGrad
        ctx.fillRect(0.0, yRynek, w, h - yRynek)

        // Nocne odbicia biało-czerwonej iluminacji Ratusza na bruku Rynku
        if (isNight) {
            val refRed = ctx.createRadialGradient(cxR - 10 * scR, yBase + 22 * scR, 2.0, cxR - 10 * scR, yBase + 22 * scR, 48 * scR)
            refRed.addColorStop(0.0, "rgba(220,24,60,0.34)")
            refRed.addColorStop(1.0, "rgba(220,24,60,0)")
            ctx.fillStyle = refRed
            ctx.fillRect(cxR - 65 * scR, yRynek, 130 * scR, h - yRynek)

            val refWhite = ctx.createRadialGradient(cxR + 14 * scR, yBase + 16 * scR, 2.0, cxR + 14 * scR, yBase + 16 * scR, 36 * scR)
            refWhite.addColorStop(0.0, "rgba(255,245,235,0.20)")
            refWhite.addColorStop(1.0, "rgba(255,245,235,0)")
            ctx.fillStyle = refWhite
            ctx.fillRect(cxR - 30 * scR, yRynek, 90 * scR, h - yRynek)
        }

        // Wachlarzowe łuki bruku Rynku
        ctx.save()
        ctx.strokeStyle = if (isNight) "rgba(255,255,255,0.045)" else "rgba(255,255,255,0.12)"
        ctx.lineWidth = Math.max(0.7, 0.8 * scR)
        for (rad in 28 until 260 step 20) {
            ctx.beginPath()
            ctx.ellipse(cxR, yBase + 4 * scR, rad * 1.45 * scR, rad * 0.42 * scR, 0.0, 0.06 * Math.PI, 0.94 * Math.PI)
            ctx.stroke()
        }
        ctx.restore()
    }
}

/**
 * Wygląd pierzei kamienic (jak StylKamienic na iOS): na 11 Listopada flagi i zielone lipy, w Panoramie drzewa
 * w barwach pory roku, zimą śnieg na gzymsach i świerki.
 */
private data class StylKamienic(
    /** pionowe biało-czerwone flagi na elewacjach */
    val flagi: Boolean = true,
    /** zapalone okna (noc, zmierzch, Święta) */
    val swiatla: Boolean,
    /** korona drzewa i jej jaśniejszy blask */
    val korona: String,
    val blask: String,
    /** śnieg na gzymsach i czubkach świerków */
    val snieg: Boolean = false,
    /** świerki zamiast lip (zima, Święta, Sylwester) */
    val swierki: Boolean = false,
) {
    companion object {
        fun listopad(noc: Boolean) = StylKamienic(
            swiatla = noc,
            korona = if (noc) "rgba(22,38,34,0.96)" else "rgba(78,108,64,0.96)",
            blask = if (noc) "rgba(32,52,44,0.96)" else "rgba(106,136,76,0.96)",
        )
    }
}

/** weather-effects.js 2343–2471: pierzeje kamieniczek wokół Rynku (przerwa na Ratusz), okna, flagi i szpaler lip. */
private fun RamaSceny.ratusz11Kamienice(u: Ratusz11Uklad, styl: StylKamienic = StylKamienic.listopad(isNight)) {
    with(u) {
        // 2. Pierzeje zabytkowych kamieniczek wokół Rynku w Lesznie + szpaler lip
        val dayPalettes = listOf(
            Triple("#d6e0e4", "#444c58", intArrayOf(30, 34, 38)),
            Triple("#e8c68a", "#5c4034", intArrayOf(34, 30, 22)),
            Triple("#a8d4cc", "#3a444e", intArrayOf(22, 32, 34)),
            Triple("#ece0d0", "#9c4836", intArrayOf(34, 32, 30)),
            Triple("#e2b6bc", "#4e3e48", intArrayOf(34, 26, 30)),
            Triple("#ded4c4", "#404652", intArrayOf(32, 30, 28)),
        )
        val hwBase = Math.max(28.0, 34 * scR)
        val gapL = cxR - 43 * scR
        val gapR = cxR + 43 * scR
        var hxCursor = 3 * scR
        var hIdx = 0
        while (hxCursor < w - 12 * scR) {
            if (hxCursor + hwBase * 0.65 > gapL && hxCursor < gapR) {
                hxCursor = gapR + 3 * scR
                if (hxCursor >= w - 12 * scR) break
            }
            var hw = hwBase + ((hIdx % 3) - 1) * 3 * scR
            if (hxCursor < gapL && hxCursor + hw > gapL) {
                hw = Math.max(18 * scR, gapL - hxCursor - 1 * scR)
            }
            if (hxCursor + hw > w - 2 * scR) {
                hw = w - 2 * scR - hxCursor
            }
            if (hw < 16 * scR) break

            val hh = (23.5 + (hIdx % 3) * 1.4) * scR
            val hy = yRynek - hh
            val pal = dayPalettes[hIdx % dayPalettes.size]
            val hasGable = (hIdx % 2 == 1)
            val wallCol: Any = if (isNight) Plotno.rgb(pal.third[0], pal.third[1], pal.third[2]) else pal.first
            val roofCol = if (isNight) "#0e1320" else pal.second
            val trimCol = if (isNight) "rgba(55,68,94,0.82)" else "rgba(250,250,252,0.92)"

            ctx.fillStyle = wallCol
            ctx.fillRect(hxCursor, hy, hw, hh)
            ctx.strokeStyle = "rgba(0,0,0,0.16)"
            ctx.lineWidth = 0.8
            ctx.strokeRect(hxCursor, hy, hw, hh)

            // Dach i barokowe szczyty kamieniczek
            ctx.fillStyle = roofCol
            ctx.beginPath()
            if (hasGable) {
                ctx.moveTo(hxCursor, hy)
                ctx.lineTo(hxCursor + hw * 0.18, hy - 6.8 * scR)
                ctx.lineTo(hxCursor + hw * 0.82, hy - 6.8 * scR)
                ctx.lineTo(hxCursor + hw, hy)
                ctx.closePath()
                ctx.fill()

                ctx.fillStyle = wallCol
                ctx.strokeStyle = trimCol
                ctx.lineWidth = Math.max(0.8, 0.8 * scR)
                ctx.beginPath()
                ctx.moveTo(hxCursor + hw * 0.25, hy)
                ctx.lineTo(hxCursor + hw * 0.50, hy - 9.2 * scR)
                ctx.lineTo(hxCursor + hw * 0.75, hy)
                ctx.closePath()
                ctx.fill()
                ctx.stroke()
            } else {
                ctx.moveTo(hxCursor, hy)
                ctx.lineTo(hxCursor + 4 * scR, hy - 6.2 * scR)
                ctx.lineTo(hxCursor + hw - 4 * scR, hy - 6.2 * scR)
                ctx.lineTo(hxCursor + hw, hy)
                ctx.closePath()
                ctx.fill()
            }

            // Gzyms kamienicy (w Panoramie zimą śnieżna czapa)
            if (styl.snieg) {
                ctx.strokeStyle = if (isNight) "#d8ecff" else "#ffffff"
                ctx.lineWidth = Math.max(1.4, 1.9 * scR)
            } else {
                ctx.strokeStyle = trimCol
                ctx.lineWidth = Math.max(0.9, 1.0 * scR)
            }
            ctx.beginPath()
            ctx.moveTo(hxCursor, hy)
            ctx.lineTo(hxCursor + hw, hy)
            ctx.stroke()

            // Dwa rzędy okien kamienic
            val winStep = 8 * scR
            for (rI in 0 until 2) {
                val wy = hy + (5 + rI * 8.5) * scR
                var wx = hxCursor + 4.5 * scR
                while (wx < hxCursor + hw - 5.5 * scR) {
                    if (styl.swiatla) {
                        val lit = (((Math.round(wx).toInt() * 7 + rI * 3 + hIdx * 5) % 4) != 0)
                        ctx.fillStyle = if (lit) "rgba(255,214,120,0.82)" else "#161e30"
                        ctx.fillRect(wx, wy, 3.3 * scR, 5.0 * scR)
                    } else {
                        ctx.fillStyle = trimCol
                        ctx.fillRect(wx - 0.5 * scR, wy - 0.5 * scR, 4.3 * scR, 6.0 * scR)
                        ctx.fillStyle = "rgba(62,76,94,0.92)"
                        ctx.fillRect(wx, wy, 3.3 * scR, 5.0 * scR)
                    }
                    wx += winStep
                }
            }

            // Pionowe biało-czerwone flagi świąteczne na kamienicach Rynku
            if (styl.flagi) {
                val bfx = hxCursor + hw * 0.5
                val bfy = hy + 10.8 * scR
                ctx.fillStyle = "rgba(255,255,255,0.94)"
                ctx.fillRect(bfx - 1.3 * scR, bfy, 1.25 * scR, 7.2 * scR)
                ctx.fillStyle = "rgba(220,20,60,0.94)"
                ctx.fillRect(bfx - 0.05 * scR, bfy, 1.25 * scR, 7.2 * scR)
            }

            // Zielone lipy wzdłuż pierzei Rynku (co druga kamienica), w Panoramie zimą świerki
            if (hIdx % 2 == 0) {
                val tx = hxCursor + hw * 0.82
                if (styl.swierki) {
                    swierk(tx, yRynek, scR, styl)
                } else {
                    val ty = yRynek - 5 * scR
                    ctx.strokeStyle = "#372a22"
                    ctx.lineWidth = Math.max(1.0, 1.4 * scR)
                    ctx.beginPath()
                    ctx.moveTo(tx, yRynek)
                    ctx.lineTo(tx, ty + 2 * scR)
                    ctx.stroke()
                    ctx.fillStyle = styl.korona
                    ctx.beginPath()
                    ctx.ellipse(tx, ty - 1.5 * scR, 6.4 * scR, 4.5 * scR, 0.0, 0.0, Math.PI * 2)
                    ctx.fill()
                    ctx.fillStyle = styl.blask
                    ctx.beginPath()
                    ctx.ellipse(tx - 1.0 * scR, ty - 2.6 * scR, 4.2 * scR, 3.6 * scR, 0.0, 0.0, Math.PI * 2)
                    ctx.fill()
                }
            }

            hxCursor += hw
            hIdx++
        }
    }
}

/** weather-effects.js 2473–2508: nocne smugi reflektorów i biało-czerwona łuna za Ratuszem. */
private fun RamaSceny.ratusz11Iluminacja(u: Ratusz11Uklad) {
    with(u) {
        // 3. Nocne smugi reflektorów i łuna Biało-Czerwona za Ratuszem w Lesznie
        if (isNight) {
            ctx.save()
            val beamSway = Math.sin(t * 0.55) * 9 * scR
            val bWhite = ctx.createLinearGradient(0.0, Y(25), 0.0, 0.0)
            bWhite.addColorStop(0.0, "rgba(255,255,255,0.22)")
            bWhite.addColorStop(1.0, "rgba(255,255,255,0.02)")
            ctx.fillStyle = bWhite
            ctx.beginPath()
            ctx.moveTo(X(-26), Y(20)); ctx.lineTo(X(-66) + beamSway, 0.0); ctx.lineTo(X(-22) + beamSway, 0.0); ctx.lineTo(X(-6), Y(20))
            ctx.closePath(); ctx.fill()

            val bRed = ctx.createLinearGradient(0.0, Y(25), 0.0, 0.0)
            bRed.addColorStop(0.0, "rgba(220,20,60,0.34)")
            bRed.addColorStop(1.0, "rgba(220,20,60,0.03)")
            ctx.fillStyle = bRed
            ctx.beginPath()
            ctx.moveTo(X(4), Y(20)); ctx.lineTo(X(14) - beamSway, 0.0); ctx.lineTo(X(60) - beamSway, 0.0); ctx.lineTo(X(28), Y(20))
            ctx.closePath(); ctx.fill()

            val glowC = ctx.createRadialGradient(cxR, Y(52), 4.0, cxR, Y(52), 76 * scR)
            glowC.addColorStop(0.0, "rgba(220,25,60,0.38)")
            glowC.addColorStop(1.0, "rgba(220,25,60,0)")
            ctx.fillStyle = glowC
            ctx.beginPath(); ctx.arc(cxR, Y(52), 76 * scR, 0.0, Math.PI * 2); ctx.fill()

            val glowW = ctx.createRadialGradient(cxR, Y(96), 2.0, cxR, Y(96), 48 * scR)
            glowW.addColorStop(0.0, "rgba(255,245,235,0.30)")
            glowW.addColorStop(1.0, "rgba(255,245,235,0)")
            ctx.fillStyle = glowW
            ctx.beginPath(); ctx.arc(cxR, Y(96), 48 * scR, 0.0, Math.PI * 2); ctx.fill()
            ctx.restore()
        }
    }
}

/**
 * weather-effects.js 2527–2603: korpus Ratusza: cień, cokół, gzymsy, pilastry, okna, kartusz, portal, schody, dach
 * i attyka. [luna]: czerwona poświata na cokole nocą (11 Listopada; w Panoramie bez niej).
 */
private fun RamaSceny.ratusz11Korpus(u: Ratusz11Uklad, luna: Boolean = true) {
    with(u) {
        // Cień pod bryłą Ratusza na bruku
        ctx.fillStyle = "rgba(0,0,0,0.26)"
        ctx.beginPath()
        ctx.ellipse(cxR, Y(-0.5), (bw + 4) * scR, 3.5 * scR, 0.0, 0.0, Math.PI * 2)
        ctx.fill()

        // Korpus główny (piętra + boniowany cokół przyziemia)
        fillRectXY(-bw, 34.0, bw, 11.0, colWall)
        fillRectXY(-bw, 34.0, -24.0, 11.0, colWallSh)
        fillRectXY(-bw, 11.0, bw, 0.0, colPlinth)
        if (isNight && luna) {
            val upWash = ctx.createLinearGradient(0.0, Y(11), 0.0, Y(18))
            upWash.addColorStop(0.0, "rgba(210,32,68,0.68)")
            upWash.addColorStop(1.0, "rgba(210,32,68,0)")
            ctx.fillStyle = upWash
            ctx.fillRect(X(-bw), Y(18), bw * 2 * scR, 7 * scR)
        }

        // Białe gzymsy kordonowe
        fillRectXY(-bw - 0.5, 11.8, bw + 0.5, 10.5, colPilaster)
        fillRectXY(-bw - 0.5, 1.4, bw + 0.5, 0.0, colPilaster)

        // Kolosalne białe pilastry w wielkim porządku
        for (px in doubleArrayOf(-38.0, -25.0, -13.5, -5.8, 5.8, 13.5, 25.0, 38.0)) {
            fillRectXY(px - 1.5, 34.0, px + 1.5, 11.5, colPilaster)
            fillRectXY(px - 1.25, 10.8, px + 1.25, 1.2, if (isNight) "rgba(238,192,202,0.86)" else "rgba(242,238,228,0.92)")
        }

        // Okna elewacji w białych opaskach
        for (wx in doubleArrayOf(-31.5, -19.2, -9.6, 9.6, 19.2, 31.5)) {
            fillRectXY(wx - 2.4, 31.0, wx + 2.4, 24.8, colPilaster)
            fillRectXY(wx - 1.6, 30.1, wx + 1.6, 25.6, colWin)
            fillRectXY(wx - 2.4, 21.2, wx + 2.4, 14.8, colPilaster)
            fillRectXY(wx - 1.6, 20.3, wx + 1.6, 15.6, colWin)
            fillRectXY(wx - 1.7, 7.8, wx + 1.7, 3.8, colPilaster)
            fillRectXY(wx - 1.1, 7.2, wx + 1.1, 4.4, if (isNight) colWin else "#2d2a28")
        }

        // Kartusz herbowy Leszna nad portalem
        ctx.fillStyle = colPilaster
        ctx.beginPath(); ctx.ellipse(cxR, Y(21.15), 2.2 * scR, 2.35 * scR, 0.0, 0.0, Math.PI * 2); ctx.fill()
        ctx.fillStyle = "#c33034"
        ctx.beginPath(); ctx.ellipse(cxR, Y(21.15), 1.2 * scR, 1.35 * scR, 0.0, 0.0, Math.PI * 2); ctx.fill()

        // Portal główny z naczółkiem i dwubiegowymi wachlarzowymi schodami z kutą balustradą
        ctx.fillStyle = colPilaster
        ctx.beginPath()
        ctx.moveTo(X(-5.6), Y(13.2)); ctx.lineTo(X(0), Y(17.2)); ctx.lineTo(X(5.6), Y(13.2))
        ctx.closePath(); ctx.fill()
        fillRectXY(-3.3, 13.2, 3.3, 2.5, colPilaster)
        ctx.fillStyle = "#40281c"
        ctx.beginPath()
        ctx.arc(cxR, Y(7.2), 2.2 * scR, Math.PI, 0.0)
        ctx.lineTo(X(2.2), Y(2.5)); ctx.lineTo(X(-2.2), Y(2.5))
        ctx.closePath(); ctx.fill()

        ctx.fillStyle = "#d8dce2"
        ctx.beginPath()
        ctx.moveTo(X(-8.5), Y(0)); ctx.lineTo(X(-4.0), Y(3.0)); ctx.lineTo(X(4.0), Y(3.0)); ctx.lineTo(X(8.5), Y(0))
        ctx.closePath(); ctx.fill()
        ctx.strokeStyle = "#262a30"
        ctx.lineWidth = Math.max(0.8, 0.85 * scR)
        ctx.beginPath()
        ctx.moveTo(X(-8.5), Y(1.2)); ctx.lineTo(X(-4.0), Y(4.2))
        ctx.moveTo(X(8.5), Y(1.2)); ctx.lineTo(X(4.0), Y(4.2))
        ctx.stroke()

        // Czterospadowy dach + terakotowy gzyms + biała attyka tralkowa
        ctx.fillStyle = colRoof
        ctx.beginPath()
        ctx.moveTo(X(-bw + 2), Y(40.2)); ctx.lineTo(X(-bw + 9), Y(46.5)); ctx.lineTo(X(bw - 9), Y(46.5)); ctx.lineTo(X(bw - 2), Y(40.2))
        ctx.closePath(); ctx.fill()
        fillRectXY(-bw - 1, 35.8, bw + 1, 34.0, colTerracotta)
        fillRectXY(-bw - 0.5, 40.2, bw + 0.5, 35.8, colPilaster)
        for (bx in -35..35 step 4) {
            fillRectXY(bx - 0.9, 39.0, bx + 0.9, 36.8, colWallSh)
        }
    }
}

/** weather-effects.js 2605–2709: wieża Ratusza: kondygnacje, zegar, galeria, bęben, hełm z latarnią i dzwonem, kula i iglica. */
private fun RamaSceny.ratusz11Wieza(u: Ratusz11Uklad) {
    with(u) {
        // Dolna kondygnacja wieży Ratusza (z półkoliście zwieńczonym oknem)
        val tw1 = 12.5
        fillRectXY(-tw1, 62.0, tw1, 40.0, colWall)
        fillRectXY(-tw1, 62.0, -tw1 + 2.2, 40.0, colPilaster)
        fillRectXY(tw1 - 2.2, 62.0, tw1, 40.0, colPilaster)
        ctx.fillStyle = colPilaster
        ctx.beginPath(); ctx.arc(cxR, Y(52.5), 3.4 * scR, Math.PI, 0.0); ctx.fill()
        fillRectXY(-3.2, 52.5, 3.2, 45.0, colPilaster)
        fillRectXY(-1.9, 53.2, 1.9, 46.0, colWin)
        fillRectXY(-tw1 - 1.5, 64.0, tw1 + 1.5, 61.8, colPilaster)

        // Kondygnacja zegarowa wieży + taras widokowy z balustradą
        val tw2 = 11.8
        fillRectXY(-tw2, 85.0, tw2, 64.0, colWall)
        fillRectXY(-tw2, 85.0, -tw2 + 2.0, 64.0, colPilaster)
        fillRectXY(tw2 - 2.0, 85.0, tw2, 64.0, colPilaster)
        fillRectXY(-2.4, 70.5, 2.4, 66.2, colPilaster)
        fillRectXY(-1.4, 69.8, 1.4, 66.8, colWin)

        ratusz11Zegar(u)

        // Galeria widokowa i kuta balustrada
        fillRectXY(-tw2 - 1.8, 86.8, tw2 + 1.8, 85.0, colPilaster)
        fillRectXY(-tw2 - 1.2, 89.5, tw2 + 1.2, 88.6, "#2d323a")
        ctx.strokeStyle = "#2d323a"
        ctx.lineWidth = Math.max(0.6, 0.65 * scR)
        for (rx in -12..12 step 2) {
            ctx.beginPath(); ctx.moveTo(X(rx), Y(86.8)); ctx.lineTo(X(rx), Y(89.2)); ctx.stroke()
        }

        // Ośmioboczny bęben pod hełmem (3 widoczne fasetowane ścianki)
        fillRectXY(-7.5, 97.0, 7.5, 86.8, colWall)
        fillRectXY(-7.5, 97.0, -3.6, 86.8, colPilaster)
        fillRectXY(3.6, 97.0, 7.5, 86.8, colWallSh)
        fillRectXY(-8.0, 97.8, 8.0, 96.4, colPilaster)
        fillRectXY(-2.1, 95.0, 2.1, 89.5, "#343840")

        // Dwuczęściowy barokowy hełm z PRZEŚWITUJĄCĄ ażurową latarnią i dzwonem!
        ctx.fillStyle = colHelmet
        ctx.beginPath()
        ctx.moveTo(X(-7.2), Y(97.8)); ctx.lineTo(X(-9.2), Y(102.5)); ctx.lineTo(X(-8.4), Y(107.0))
        ctx.lineTo(X(-5.2), Y(111.5)); ctx.lineTo(X(5.2), Y(111.5)); ctx.lineTo(X(8.4), Y(107.0))
        ctx.lineTo(X(9.2), Y(102.5)); ctx.lineTo(X(7.2), Y(97.8))
        ctx.closePath(); ctx.fill()

        ctx.fillStyle = colHelmetHi
        ctx.beginPath()
        ctx.moveTo(X(-3.5), Y(98)); ctx.lineTo(X(-5.5), Y(103)); ctx.lineTo(X(-3.0), Y(111))
        ctx.lineTo(X(-1.0), Y(111)); ctx.lineTo(X(-2.2), Y(103)); ctx.lineTo(X(-1.0), Y(98))
        ctx.closePath(); ctx.fill()

        // Ażurowa latarnia dzwonowa (w nocy rozświetlona ciepłym złotem, w dzień prześwituje niebo)
        if (isNight) {
            fillRectXY(-4.2, 120.8, 4.2, 112.0, "rgba(255,215,115,0.82)")
        }
        ctx.strokeStyle = colHelmet
        ctx.lineWidth = Math.max(1.0, 1.15 * scR)
        for (lx in doubleArrayOf(-4.5, -1.6, 1.6, 4.5)) {
            ctx.beginPath(); ctx.moveTo(X(lx), Y(111.5)); ctx.lineTo(X(lx), Y(121.2)); ctx.stroke()
        }
        // Dzwon zawieszony w środku latarni
        ctx.fillStyle = "#4b412d"
        ctx.beginPath()
        ctx.moveTo(X(-1.6), Y(115.5)); ctx.lineTo(X(0), Y(119.2)); ctx.lineTo(X(1.6), Y(115.5))
        ctx.closePath(); ctx.fill()
        fillRectXY(-5.6, 122.2, 5.6, 120.8, colHelmet)

        // Górna smukła cebula hełmu + złota kula i wysoka iglica
        ctx.fillStyle = colHelmet
        ctx.beginPath()
        ctx.moveTo(X(-4.8), Y(122.2)); ctx.lineTo(X(-6.0), Y(126.0)); ctx.lineTo(X(-4.2), Y(131.0))
        ctx.lineTo(X(-1.6), Y(137.0)); ctx.lineTo(X(0), Y(143.0)); ctx.lineTo(X(1.6), Y(137.0))
        ctx.lineTo(X(4.2), Y(131.0)); ctx.lineTo(X(6.0), Y(126.0)); ctx.lineTo(X(4.8), Y(122.2))
        ctx.closePath(); ctx.fill()

        ctx.strokeStyle = colHelmetHi
        ctx.lineWidth = Math.max(0.8, 1.0 * scR)
        ctx.beginPath(); ctx.moveTo(X(-1.5), Y(123)); ctx.lineTo(X(-0.4), Y(138)); ctx.stroke()

        ctx.fillStyle = if (isNight) "#c3a85c" else "#48443a"
        ctx.beginPath(); ctx.arc(cxR, Y(143.8), 1.8 * scR, 0.0, Math.PI * 2); ctx.fill()
        ctx.strokeStyle = if (isNight) "#d7c37d" else "#3c4048"
        ctx.lineWidth = Math.max(0.9, 1.0 * scR)
        ctx.beginPath(); ctx.moveTo(cxR, Y(145.6)); ctx.lineTo(cxR, Y(153.5)); ctx.stroke()
    }
}

/** weather-effects.js 2624–2645: tarcza zegara ratuszowego ze wskazówkami (bieżący czas warszawski z [RamaSceny.teraz]). */
private fun RamaSceny.ratusz11Zegar(u: Ratusz11Uklad) {
    with(u) {
        // Tarcza zegara ratuszowego (wskazująca aktualny czas!)
        val cr = 5.2
        val cyClk = 77.5
        ctx.fillStyle = colPilaster
        ctx.beginPath(); ctx.arc(cxR, Y(cyClk), (cr + 0.8) * scR, 0.0, Math.PI * 2); ctx.fill()
        ctx.fillStyle = if (isNight) "#ffecaf" else "#242830"
        ctx.beginPath(); ctx.arc(cxR, Y(cyClk), cr * scR, 0.0, Math.PI * 2); ctx.fill()
        val clkNow = teraz // na stronie new Date() (czas urządzenia), w aplikacji Europe/Warsaw
        val minAng = ((clkNow.minute + clkNow.second / 60.0) / 60) * Math.PI * 2 - Math.PI * 0.5
        val hrAng = (((clkNow.hour % 12) + clkNow.minute / 60.0) / 12) * Math.PI * 2 - Math.PI * 0.5
        ctx.strokeStyle = if (isNight) "#1e1814" else "#eec65f"
        ctx.lineCap = "round"
        ctx.lineWidth = Math.max(1.0, 1.1 * scR)
        ctx.beginPath()
        ctx.moveTo(cxR, Y(cyClk))
        ctx.lineTo(cxR + Math.cos(hrAng) * 2.8 * scR, Y(cyClk) + Math.sin(hrAng) * 2.8 * scR)
        ctx.stroke()
        ctx.lineWidth = Math.max(0.8, 0.9 * scR)
        ctx.beginPath()
        ctx.moveTo(cxR, Y(cyClk))
        ctx.lineTo(cxR + Math.cos(minAng) * 4.0 * scR, Y(cyClk) + Math.sin(minAng) * 4.0 * scR)
        ctx.stroke()
    }
}

/** Mały świerk przed kamienicą (zima, Święta, Sylwester), ze śniegiem na czubkach gałęzi, gdy jest śnieg. Jak na iOS. */
private fun RamaSceny.swierk(tx: Double, yDol: Double, s: Double, styl: StylKamienic) {
    ctx.strokeStyle = "#372a22"
    ctx.lineWidth = Math.max(1.0, 1.4 * s)
    ctx.beginPath()
    ctx.moveTo(tx, yDol)
    ctx.lineTo(tx, yDol - 2.5 * s)
    ctx.stroke()
    // trzy piętra gałęzi: połowa szerokości, wierzchołek i dół (jednostki nad ziemią)
    val pietra = listOf(Triple(5.6, 9.0, 2.0), Triple(4.4, 12.6, 6.0), Triple(3.0, 15.4, 9.6))
    pietra.forEachIndexed { i, (pol, gora, dol) ->
        val polowa = pol * s
        val yGora = yDol - gora * s
        val yDolPietra = yDol - dol * s
        ctx.fillStyle = if (i % 2 == 0) styl.korona else styl.blask
        ctx.beginPath()
        ctx.moveTo(tx - polowa, yDolPietra); ctx.lineTo(tx, yGora); ctx.lineTo(tx + polowa, yDolPietra)
        ctx.closePath(); ctx.fill()
        if (styl.snieg) {
            // śnieg na górnych 40% piętra
            val f = 0.4
            val yCzapy = yGora + (yDolPietra - yGora) * f
            ctx.fillStyle = "rgba(241,246,251,0.95)"
            ctx.beginPath()
            ctx.moveTo(tx - polowa * f, yCzapy); ctx.lineTo(tx, yGora); ctx.lineTo(tx + polowa * f, yCzapy)
            ctx.closePath(); ctx.fill()
        }
    }
}

/**
 * Panorama Leszna na zwykłe dni z prawdziwym ratuszem Ferrariego i kamienicami wokół Rynku, tymi samymi co w scenie
 * 11 Listopada (Karol 25.11: „Czy tą grafikę w pogodzie też możesz poprawić na naszą nową z nowym ratuszem ?”).
 * Bez reflektorów, flagi z masztami i łuny; drzewa w barwach pory roku, zimą śnieg i świerki. Układ jak w scenach
 * z ratuszem, więc przy zmianie z Panoramy na zimę ratusz zostaje w tym samym miejscu. Niebo, słońce, księżyc,
 * chmury, opady i liście rysują jak dotąd [ScenyStrony.rysuj] i nakładka pogody. 1:1 z iOS (ScenaRatusza.panorama).
 * [ziemia]: kolor ziemi Panoramy (ten sam ma belka z datą), [pora]: pora roku albo motywu.
 */
internal fun RamaSceny.panoramaRatusza(ziemia: String, pora: Motyw.Scena, swiatla: Boolean) {
    val u = Ratusz11Uklad(this, panorama = true)
    // przy zerowym rozmiarze krok okien kamienic byłby zerem i pętla nie miałaby końca
    if (!(u.scR > 0.0) || !(w > 0.0) || !(h > 0.0)) return
    val zimowa = pora == Motyw.Scena.ZIMA || pora == Motyw.Scena.SWIETA
    panoramaBruk(u, zimowa, ziemia)
    ratusz11Kamienice(u, stylPanoramy(pora, swiatla))
    ratusz11Korpus(u, luna = false)
    with(u) {
        if (zimowa) {
            // śnieg na dachu ratusza
            ctx.fillStyle = if (isNight) "#cbe0f5" else "#e8f4ff"
            ctx.beginPath()
            ctx.moveTo(X(-37.0), Y(40.2)); ctx.lineTo(X(-30.0), Y(46.5)); ctx.lineTo(X(30.0), Y(46.5)); ctx.lineTo(X(37.0), Y(40.2))
            ctx.closePath(); ctx.fill()
            ctx.strokeStyle = "#ffffff"
            ctx.lineWidth = Math.max(1.5, 2.1 * scR)
            ctx.beginPath(); ctx.moveTo(X(-39.8), Y(40.5)); ctx.lineTo(X(39.8), Y(40.5)); ctx.stroke()
        }
    }
    ratusz11Wieza(u)
    with(u) {
        if (pora == Motyw.Scena.SYLWESTER) {
            // w grudniu następny rok, w styczniu bieżący
            val rok = if (teraz.monthValue == 12) teraz.year + 1 else teraz.year
            ctx.save()
            ctx.font = "800 ${18 * scR}px 'Segoe UI',system-ui,sans-serif"
            ctx.textAlign = "center"
            ctx.textBaseline = "middle"
            ctx.fillStyle = "#e8c26a"
            ctx.fillText(rok.toString(), w * 0.2, yRynek - 40 * scR)
            ctx.restore()
        }
        if (pora == Motyw.Scena.SWIETA && !kartaStartu) {
            // duża choinka na Rynku (w karcie Startu byłaby pod kolumnami prognozy)
            val styl = StylKamienic(flagi = false, swiatla = true, korona = "#14532d", blask = "#1f6a3a", snieg = true, swierki = true)
            ctx.save()
            ctx.translate(w * 0.2, yRynek + 14 * scR)
            ctx.scale(3.0, 3.0)
            swierk(0.0, 0.0, scR, styl)
            ctx.restore()
        }
    }
}

/** Drzewa i okna Panoramy według pory roku (jak stylPanoramy na iOS). */
private fun RamaSceny.stylPanoramy(pora: Motyw.Scena, swiatla: Boolean): StylKamienic {
    val noc = isNight
    return when (pora) {
        // jesienne lipy; 11 Listopada także flagi na kamienicach
        Motyw.Scena.JESIEN, Motyw.Scena.LISTOPAD -> StylKamienic(
            flagi = pora == Motyw.Scena.LISTOPAD, swiatla = swiatla,
            korona = if (noc) "rgba(70,38,16,0.96)" else "rgba(180,83,9,0.96)",
            blask = if (noc) "rgba(96,56,22,0.96)" else "rgba(217,119,6,0.96)",
        )
        Motyw.Scena.WIOSNA -> StylKamienic(
            flagi = false, swiatla = swiatla,
            korona = if (noc) "rgba(26,46,34,0.96)" else "rgba(96,150,72,0.96)",
            blask = if (noc) "rgba(36,60,44,0.96)" else "rgba(144,190,100,0.96)",
        )
        Motyw.Scena.LATO -> StylKamienic(
            flagi = false, swiatla = swiatla,
            korona = if (noc) "rgba(20,40,30,0.96)" else "rgba(52,110,48,0.96)",
            blask = if (noc) "rgba(30,54,40,0.96)" else "rgba(84,140,64,0.96)",
        )
        Motyw.Scena.ZIMA, Motyw.Scena.SWIETA, Motyw.Scena.SYLWESTER -> StylKamienic(
            flagi = false, swiatla = swiatla,
            korona = if (noc) "#123129" else "#1f4f43",
            blask = if (noc) "#18392f" else "#2a6253",
            snieg = pora != Motyw.Scena.SYLWESTER, swierki = true,
        )
    }
}

/** Płyta Rynku w Panoramie: gradient kończy się kolorem ziemi (tym samym co belka z datą), nocą ciepła poświata latarni, łuki bruku. */
private fun RamaSceny.panoramaBruk(u: Ratusz11Uklad, zimowa: Boolean, ziemia: String) {
    with(u) {
        val g = ctx.createLinearGradient(0.0, yRynek, 0.0, h)
        if (zimowa) {
            g.addColorStop(0.0, if (isNight) "#9fb0c6" else "#f4f8fc")
            g.addColorStop(0.45, if (isNight) "#b4c3d6" else "#e6eef7")
        } else {
            g.addColorStop(0.0, if (isNight) "#151c2e" else "#8d877c")
            g.addColorStop(0.45, if (isNight) "#0e1422" else "#6a645a")
        }
        g.addColorStop(1.0, ziemia)
        ctx.fillStyle = g
        ctx.fillRect(0.0, yRynek, w, h - yRynek)

        if (isNight) {
            val c = ctx.createRadialGradient(cxR, yBase + 18 * scR, 2.0, cxR, yBase + 18 * scR, 52 * scR)
            c.addColorStop(0.0, "rgba(255,214,140,0.22)")
            c.addColorStop(1.0, "rgba(255,214,140,0)")
            ctx.fillStyle = c
            ctx.fillRect(cxR - 70 * scR, yRynek, 140 * scR, h - yRynek)
        }

        ctx.save()
        ctx.strokeStyle = if (zimowa) (if (isNight) "rgba(200,228,255,0.08)" else "rgba(120,150,185,0.16)")
        else (if (isNight) "rgba(255,255,255,0.045)" else "rgba(255,255,255,0.10)")
        ctx.lineWidth = Math.max(0.7, 0.8 * scR)
        for (rad in 28 until 260 step 20) {
            ctx.beginPath()
            ctx.ellipse(cxR, yBase + 4 * scR, rad * 1.45 * scR, rad * 0.42 * scR, 0.0, 0.06 * Math.PI, 0.94 * Math.PI)
            ctx.stroke()
        }
        ctx.restore()
    }
}

/** weather-effects.js 2721–2821: ognisko powstańcze we mgle: wzgórza, sosny, mgła, ognisko z iskrami, lanca z proporcem. */
internal fun RamaSceny.powstanczyBiwak() {
    // ══ 10. OGNISKO POWSTAŃCZE WE MGLE (powstanczy-biwak) ══
    drawHill(horizonY - 10, 14.0, 0.012, 7.0, 0.026, "#141a2b", 0.5)
    drawHill(horizonY + 14, 12.0, 0.015, 6.0, 0.030, "#0d1220", 1.8)
    drawHill(horizonY + 36, 6.0, 0.011, 3.0, 0.022, "#080c16", 3.0)

    // Sylwetki sosen w listopadowej mgle
    val pCount = Math.max(9, Math.floor(w / 36).toInt())
    for (i in 0..pCount) {
        val px = (i.toDouble() / pCount) * w + Math.sin(i * 2.7) * 8
        if (Math.abs(px - w * 0.56) < 42) continue
        val py = horizonY + 26 + (i % 3) * 10
        val ph = (32 + (i % 3) * 8).toDouble()
        ctx.fillStyle = if (i % 2 == 0) "rgba(12,18,32,0.92)" else "rgba(18,26,44,0.86)"
        for (tier in 0 until 3) {
            val ty = py - ph + tier * (ph * 0.28)
            val tw = (10 + tier * 5).toDouble()
            ctx.beginPath()
            ctx.moveTo(px, ty)
            ctx.lineTo(px - tw, ty + ph * 0.42)
            ctx.lineTo(px + tw, ty + ph * 0.42)
            ctx.closePath()
            ctx.fill()
        }
    }

    // Snująca się listopadowa mgła nad polaną
    for (m in 0 until 3) {
        val my = horizonY + 12 + m * 18
        val mShift = Math.sin(t * 0.45 + m * 1.4) * 16
        val mGr = ctx.createLinearGradient(0.0, my - 12, 0.0, my + 14)
        mGr.addColorStop(0.0, "rgba(215,225,240,0)")
        mGr.addColorStop(0.5, "rgba(215,225,240,0.16)")
        mGr.addColorStop(1.0, "rgba(215,225,240,0)")
        ctx.fillStyle = mGr
        ctx.fillRect(mShift - 20, my - 12, w + 40, 26.0)
    }

    // Ognisko powstańcze z ciepłą łuną i unoszącymi się iskrami
    val fx = w * 0.55
    val fy = horizonY + 42
    val fireFlick = 1 + 0.08 * Math.sin(t * 9.5) + 0.05 * Math.cos(t * 14.2)
    val fireGlow = ctx.createRadialGradient(fx, fy - 8, 2.0, fx, fy - 8, 68 * fireFlick)
    fireGlow.addColorStop(0.0, "rgba(255,145,40,0.52)")
    fireGlow.addColorStop(0.5, "rgba(220,45,35,0.22)")
    fireGlow.addColorStop(1.0, "rgba(220,45,35,0)")
    ctx.fillStyle = fireGlow
    ctx.beginPath(); ctx.arc(fx, fy - 8, 68 * fireFlick, 0.0, Math.PI * 2); ctx.fill()

    // Polana w ognisku
    ctx.strokeStyle = "#24140e"
    ctx.lineWidth = 4.0
    ctx.lineCap = "round"
    ctx.beginPath()
    ctx.moveTo(fx - 15, fy + 4); ctx.lineTo(fx + 14, fy - 2)
    ctx.moveTo(fx + 15, fy + 4); ctx.lineTo(fx - 14, fy - 2)
    ctx.stroke()

    // Płomienie
    val tongues = listOf(Triple(-5.0, 18.0, "#ff5412"), Triple(4.0, 15.0, "#ff7b1c"), Triple(0.0, 22.0, "#ffd15c"))
    tongues.forEachIndexed { ti, (ox, fh, col) ->
        val sway = Math.sin(t * (7 + ti * 2.3) + ti) * 2.5
        ctx.fillStyle = col
        ctx.beginPath()
        ctx.moveTo(fx + ox - 6, fy)
        ctx.quadraticCurveTo(fx + ox + sway, fy - fh * fireFlick, fx + ox + sway * 0.6, fy - fh * 1.15 * fireFlick)
        ctx.quadraticCurveTo(fx + ox + 6, fy - fh * 0.4, fx + ox + 6, fy)
        ctx.closePath()
        ctx.fill()
    }

    // Unoszące się iskry z ogniska
    for (e in 0 until 12) {
        val ep = ((t * (0.45 + (e % 4) * 0.12) + e * 0.23) % 1.0)
        val ex = fx + Math.sin(ep * 5 + e * 1.7) * (8 + ep * 18)
        val ey = fy - 10 - ep * 68
        ctx.fillStyle = Plotno.rgba(255, 190 + (e % 3) * 25, 80, (1 - ep) * 0.9)
        ctx.beginPath(); ctx.arc(ex, ey, 1.5 * (1 - ep * 0.4), 0.0, Math.PI * 2); ctx.fill()
    }

    // Lanca z proporcem powstańczym biało-czerwonym z wcięciem jaskółczym
    val lx0 = fx + 34; val lyBot = fy + 8; val lyTop = fy - 52
    ctx.strokeStyle = "#8b6d4b"
    ctx.lineWidth = 2.2
    ctx.beginPath(); ctx.moveTo(lx0, lyBot); ctx.lineTo(lx0 + 2, lyTop); ctx.stroke()
    val pw = 28.0; val ph2 = 12.0; val waveP = Math.sin(t * 3.5) * 2.2
    ctx.fillStyle = "rgba(248,250,252,0.95)"
    ctx.beginPath()
    ctx.moveTo(lx0 + 2, lyTop + 2)
    ctx.quadraticCurveTo(lx0 + 2 + pw * 0.5, lyTop + waveP, lx0 + 2 + pw, lyTop + 2 - waveP * 0.5)
    ctx.lineTo(lx0 + 2 + pw * 0.76, lyTop + 2 + ph2 * 0.5)
    ctx.lineTo(lx0 + 2, lyTop + 2 + ph2 * 0.5)
    ctx.closePath(); ctx.fill()
    ctx.fillStyle = "rgba(220,20,60,0.95)"
    ctx.beginPath()
    ctx.moveTo(lx0 + 2, lyTop + 2 + ph2 * 0.5)
    ctx.lineTo(lx0 + 2 + pw * 0.76, lyTop + 2 + ph2 * 0.5)
    ctx.lineTo(lx0 + 2 + pw, lyTop + 2 + ph2 - waveP * 0.5)
    ctx.quadraticCurveTo(lx0 + 2 + pw * 0.5, lyTop + ph2 + waveP, lx0 + 2, lyTop + 2 + ph2)
    ctx.closePath(); ctx.fill()
}
