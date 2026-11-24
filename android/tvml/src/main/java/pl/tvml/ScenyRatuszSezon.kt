package pl.tvml

/*
 * Sześć sezonowych scen Ratusza w Lesznie ze strony tvml.pl: leszno-zima, leszno-mikolajki, leszno-wigilia,
 * leszno-swieta, leszno-sylwester, leszno-nowy-rok. Tłumaczenie linia w linię gałęzi „11. ZIMOWO-ŚWIĄTECZNY
 * I NOWOROCZNY RATUSZ W LESZNIE” z _drawCustomScene (weather-effects.js 3047–3811). Niebo i gwiazdy rysuje
 * wcześniej ScenyStrony.rysuj; te sceny nie mają przyciemnienia dołu ani nakładki pogody.
 */

/**
 * Ratusz w scenach Leszna (zima, święta, Sylwester, Nowy Rok, 11 Listopada) na szerokiej karcie aplikacji (ok. 1,85:1):
 * bliżej i większy niż na prawie kwadratowej karcie strony, żeby wyglądał jak na tvml.pl
 * (Karol 24.11: „Raz ratusz może być bliżej raz dalej tak by pasowało graficznie”, „popraw wygląd ratusza”).
 * Rysunek ratusza jest 1:1 ze strony; zmienia się tylko jego skala i linia podstawy. Te same liczby co PlanRatusza na iOS.
 */
internal object PlanRatusza {
    fun szeroka(w: Double, h: Double) = h > 0 && w / h >= 1.5

    /** mnożnik skali ratusza (na stronie 1) */
    fun skala(w: Double, h: Double) = if (szeroka(w, h)) 1.3 else 1.0

    /** linia podstawy ratusza (na stronie h * 0.667); niżej = bliżej, a iglica dalej mieści się w karcie */
    fun podstawa(w: Double, h: Double) = if (szeroka(w, h)) h * 0.76 else h * 0.667
}

/**
 * Wspólne wartości gałęzi (weather-effects.js 3049–3057, 3252–3253, 3347–3348 i 3381–3393):
 * stałe położenia, kolory Ratusza i pomocnicze X, Y, fRect.
 * [rR] to skala samego ratusza (scR razy [PlanRatusza.skala]); kamienice i postacie zostają przy scR.
 */
private class RatuszSezonowyGeo(
    private val plotno: Plotno,
    val yRynek: Double,
    val yBase: Double,
    val scH: Double,
    val scR: Double,
    val rR: Double,
    val cxR: Double,
    val yg: Double,
    val fgX: Double,
    val colPlinth: String,
    val colWall: String,
    val colWallSh: String,
    val colPil: String,
    val colWin: String,
    val colHelm: String,
) {
    val gapL = cxR - 43 * rR
    val gapR = cxR + 43 * rR
    val bw = 39.0

    /** const X = (dx) => cxR + dx * scR (3347), ze skalą ratusza [rR]. */
    fun X(dx: Double) = cxR + dx * rR

    /** const Y = (dy) => yBase - dy * scR (3348), ze skalą ratusza [rR]. */
    fun Y(dy: Double) = yBase - dy * rR

    /** const fRect (3390–3393): prostokąt w jednostkach Ratusza między (x1, yt) a (x2, yb). */
    fun fRect(x1: Double, yt: Double, x2: Double, yb: Double, c: Any) {
        plotno.fillStyle = c
        plotno.fillRect(X(x1), Y(yt), (x2 - x1) * rR, (yt - yb) * rR)
    }
}

/** dayPalettes (weather-effects.js 3243–3250): ściana w dzień, dach w dzień, ściana w nocy (r, g, b). */
private val paletyKamienicRatusza = listOf(
    Triple("#d6e0e4", "#444c58", intArrayOf(24, 32, 46)),
    Triple("#e8c68a", "#5c4034", intArrayOf(28, 30, 40)),
    Triple("#a8d4cc", "#3a444e", intArrayOf(20, 32, 42)),
    Triple("#ece0d0", "#9c4836", intArrayOf(28, 32, 44)),
    Triple("#e2b6bc", "#4e3e48", intArrayOf(28, 26, 40)),
    Triple("#ded4c4", "#404652", intArrayOf(26, 30, 42)),
)

/** JS `x.toFixed(2)` w szablonach rgba(…): alfa zaokrąglona do dwóch miejsc po przecinku. */
private fun alfaToFixed2(v: Double): Double = Math.round(v * 100.0) / 100.0

/**
 * Gałąź 11 z _drawCustomScene (weather-effects.js 3047–3811), wspólna dla sześciu scen sezonowych Ratusza.
 * Kolejność warstw jak na stronie: gwiazda wigilijna, fajerwerki, płyta Rynku, kamienice, girlandy, reflektory,
 * Ratusz z wieżą, poduszki czytelności, pierwszy plan sceny, opadający śnieg.
 */
internal fun RamaSceny.ratuszSezonowy() {
    // Nie ma na stronie: przy zerowym rozmiarze płótna pętle kamienic i okien nie posuwałyby się naprzód.
    if (!(w > 0.0 && h > 0.0)) return

    // ══ 11. ZIMOWO-ŚWIĄTECZNY I NOWOROCZNY RATUSZ W LESZNIE (6 animowanych scen 60 FPS z dużym pierwszym planem) ══
    val yRynek = h * 0.645
    // ratusz bliżej i większy na szerokiej karcie aplikacji (PlanRatusza; na stronie h * 0.667 i skala 1)
    val yBase = PlanRatusza.podstawa(w, h)
    val scH = h / 276
    val scR = scH * 0.96
    val rR = scR * PlanRatusza.skala(w, h)
    val cxR = if (w < 560) (w * 0.666) else (w * 0.68)
    // Na stronie (this._sidebarMode && this._cardData): karta główna z wypełnionymi danymi (data, imieniny).
    // Aplikacja zawsze pokazuje kartę z danymi, więc tryb karty zależy tylko od sidebarMode.
    val karta = sidebarMode
    // Karta na Starcie: przy dole leży plakietka z datą, więc postacie i instalacja stoją nad nią (Karol 24.11 o roku
    // w Sylwestra: „by go było widać”); na stronie i w zakładce Pogoda bez zmian.
    val yg = if (kartaStartu) h * 0.79 else if (karta) (h * 0.80 + (h * 0.20) * 0.28 - 2 * scH) else (h * 0.86)
    val fgX = if (w < 560) (w * 0.332) else (w * 0.24)
    val noworoczna = sceneType == "leszno-sylwester" || sceneType == "leszno-nowy-rok"

    // 4. kolory Ratusza (3381–3388)
    val colPlinth = if (!isNight) "#b8a688"
    else (if (sceneType == "leszno-zima") "#125c98" else if (noworoczna) "#1c2640" else "#8e1428")
    val colWall = if (!isNight) "#e8ddcb" else (if (sceneType == "leszno-zima") "#d0e8fa" else "#ecdcba")
    val colWallSh = if (!isNight) "#d2c6b1" else (if (sceneType == "leszno-zima") "#abcce8" else "#cfbda0")
    val colPil = if (!isNight) "#faf9f6" else "#fffaf0"
    val colWin = if (!isNight && sceneType != "leszno-wigilia") "#3a4656" else "#ffdc80"
    val colHelm = if (isNight) "#181e2c" else "#2a303a"

    val g = RatuszSezonowyGeo(ctx, yRynek, yBase, scH, scR, rR, cxR, yg, fgX, colPlinth, colWall, colWallSh, colPil, colWin, colHelm)

    // A. Pierwsza Gwiazdka Betlejemska na niebie (Wigilia 24.12)
    if (sceneType == "leszno-wigilia") gwiazdkaBetlejemska(g)

    // B. Żywe rakiety i rozbłyski fajerwerków nad Rynkiem (Sylwester 31.12 & Nowy Rok 1.01)
    if (noworoczna) fajerwerkiNadRynkiem(g)

    // 1. Ośnieżona płyta Rynku w Lesznie + odbicia iluminacji nocnej
    plytaRynku(g)

    // 2. Pierzeje zabytkowych kamieniczek wokół Rynku z czapami śniegu na dachach
    kamienicePierzei(g)

    // Migoczące girlandy świetlne nad Rynkiem w Mikołajki i Święta
    if (sceneType == "leszno-mikolajki" || sceneType == "leszno-swieta") girlandyNadRynkiem(g)

    // 3. Nocna iluminacja i smugi reflektorów za Ratuszem w Lesznie
    if (isNight) reflektoryZaRatuszem(g)

    // 4. Ośnieżona architektura Ratusza w Lesznie (Merlini / Ferrari)
    ratuszBryla(g)
    ratuszWieza(g)

    // Lokalizowane poduszki czytelności pod 3 kolumnami statystyk/prognozy (przed postaciami pierwszego planu!)
    // W aplikacji wyłączone: leżą pod kolumnami karty strony, a karta aplikacji ma inny układ napisów (prognoza u góry).
    if (karta && PODUSZKI_KARTY_STRONY) poduszkiCzytelnosci(g)

    // ══ 5. DUŻE ANIMOWANE POSTACIE I MOTYWY NA PIERWSZYM PLANIE (60 FPS) ══
    if (sceneType == "leszno-zima") {
        balwanWCylindrze(g)
    } else if (sceneType == "leszno-mikolajki") {
        swietyMikolajZWorkiem(g)
    } else if (sceneType == "leszno-wigilia" || sceneType == "leszno-swieta") {
        choinkaSwiateczna(g, fgX, yg, (if (sceneType == "leszno-swieta") 88 else 84) * scH, true)
        if (sceneType == "leszno-swieta") domekJarmarku(g)
        if (sceneType == "leszno-wigilia") zloteSanie(g)
    } else if (noworoczna) {
        instalacjaNoworoczna(g)
    }

    // 6. Płynnie opadające płatki śniegu / złote konfetti (60 FPS)
    if (sceneType != "leszno-sylwester") platkiSniegu(g)
}

/** drawStar8 (weather-effects.js 3059–3079): ośmioramienna gwiazda z białym środkiem. */
private fun RamaSceny.gwiazdaOsmioramienna(sx: Double, sy: Double, rLong: Double, rShort: Double, color: Any, pulse: Double = 1.0) {
    ctx.save()
    ctx.fillStyle = color
    val rl = rLong * pulse
    val rs = rShort * pulse
    for ((ang, len) in listOf(0.0 to rl, Math.PI * 0.5 to rl * 1.12, Math.PI * 0.25 to rs, Math.PI * 0.75 to rs)) {
        val dx = Math.cos(ang) * len
        val dy = Math.sin(ang) * len
        val px = -Math.sin(ang) * (rs * 0.22)
        val py = Math.cos(ang) * (rs * 0.22)
        ctx.beginPath()
        ctx.moveTo(sx + dx, sy + dy)
        ctx.lineTo(sx + px, sy + py)
        ctx.lineTo(sx - dx, sy - dy)
        ctx.lineTo(sx - px, sy - py)
        ctx.closePath()
        ctx.fill()
    }
    ctx.fillStyle = "#ffffff"
    ctx.beginPath()
    ctx.arc(sx, sy, Math.max(1.2, rs * 0.28), 0.0, Math.PI * 2)
    ctx.fill()
    ctx.restore()
}

/** drawFestiveSpruce (weather-effects.js 3081–3127): ośnieżona choinka z bombkami, światełkami, prezentami i gwiazdą. */
private fun RamaSceny.choinkaSwiateczna(g: RatuszSezonowyGeo, tx: Double, tyg: Double, hTree: Double, withGifts: Boolean) = with(g) {
    if (isNight) {
        val rg = ctx.createRadialGradient(tx, tyg - hTree * 0.5, 2.0, tx, tyg - hTree * 0.5, hTree * 0.65)
        rg.addColorStop(0.0, "rgba(255,205,95,0.42)")
        rg.addColorStop(1.0, "rgba(255,205,95,0)")
        ctx.fillStyle = rg
        ctx.beginPath(); ctx.arc(tx, tyg - hTree * 0.5, hTree * 0.65, 0.0, Math.PI * 2); ctx.fill()
    }
    ctx.fillStyle = if (isNight) "#d6e8fa" else "#f6fbff"
    ctx.beginPath(); ctx.ellipse(tx, tyg + 1 * scH, 26 * scH, 4.5 * scH, 0.0, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#762822"
    ctx.fillRect(tx - 5.5 * scH, tyg - 8 * scH, 11 * scH, 8 * scH)
    val maxW = hTree * 0.34
    for (tr in 0 until 5) {
        val frac = tr / 5.0
        val yb = tyg - 6 * scH - frac * (hTree - 10 * scH)
        val yt = tyg - 6 * scH - ((tr + 1.3) / 5) * (hTree - 5 * scH)
        val tw = (1 - frac * 0.73) * maxW
        ctx.fillStyle = if (isNight) Plotno.rgb(18, 82 + tr * 7, 46) else Plotno.rgb(24, 108 + tr * 7, 56)
        ctx.beginPath()
        ctx.moveTo(tx - tw, yb); ctx.lineTo(tx, yt); ctx.lineTo(tx + tw, yb)
        ctx.closePath(); ctx.fill()
        ctx.strokeStyle = "#ffffff"
        ctx.lineWidth = Math.max(1.1, 1.6 * scH)
        ctx.beginPath(); ctx.moveTo(tx - tw, yb); ctx.lineTo(tx - tw * 0.52, yb - 1.8 * scH); ctx.stroke()
        ctx.beginPath(); ctx.moveTo(tx + tw, yb); ctx.lineTo(tx + tw * 0.52, yb - 1.8 * scH); ctx.stroke()
        for (k in 0 until 3) {
            val bx = tx + (k - 1) * tw * 0.48 + (if (tr % 2 == 0) 1.3 else -1.3) * scH
            val by = yb - (3 + k * 1.9) * scH
            ctx.fillStyle = if ((tr + k) % 2 == 0) "#e41a38" else "#f8ce52"
            ctx.beginPath(); ctx.arc(bx, by, 1.9 * scH, 0.0, Math.PI * 2); ctx.fill()
            val twk = 0.45 + 0.55 * Math.sin(t * 4.8 + tr * 1.7 + k * 2.1)
            ctx.fillStyle = Plotno.rgba(255, 248, 195, alfaToFixed2(twk))
            ctx.beginPath(); ctx.arc(bx + 2.4 * scH, by - 1.5 * scH, 1.3 * scH, 0.0, Math.PI * 2); ctx.fill()
        }
    }
    if (withGifts) {
        fun prezent(dx: Double, gw: Double, gh: Double, bc: String, rc: String) {
            ctx.fillStyle = bc
            ctx.fillRect(tx + dx * scH, tyg - gh * scH, gw * scH, gh * scH)
            ctx.fillStyle = rc
            ctx.fillRect(tx + (dx + gw * 0.41) * scH, tyg - gh * scH, gw * 0.18 * scH, gh * scH)
            ctx.fillRect(tx + dx * scH, tyg - gh * 0.58 * scH, gw * scH, gh * 0.18 * scH)
        }
        prezent(-19.5, 10.5, 10.0, "#c01630", "#f5ce58")
        prezent(9.0, 11.2, 11.5, "#147448", "#f8d666")
        prezent(-10.5, 8.5, 7.5, "#d8aa34", "#c41630")
    }
    gwiazdaOsmioramienna(tx, tyg - hTree - 2.5 * scH, 9.5 * scH, 4.8 * scH, "#ffe46e", 0.92 + 0.12 * Math.sin(t * 3.2))
}

/** A. Pierwsza Gwiazdka Betlejemska na niebie, tylko Wigilia (weather-effects.js 3129–3139). */
private fun RamaSceny.gwiazdkaBetlejemska(g: RatuszSezonowyGeo) = with(g) {
    val pulse = 0.92 + 0.14 * Math.sin(t * 2.6)
    val gx = w * 0.90
    val gy = h * 0.13
    val gr = ctx.createRadialGradient(gx, gy, 1.0, gx, gy, 26 * scH * pulse)
    gr.addColorStop(0.0, if (isNight) "rgba(255,238,160,0.72)" else "rgba(255,238,160,0.48)")
    gr.addColorStop(1.0, "rgba(255,238,160,0)")
    ctx.fillStyle = gr
    ctx.beginPath(); ctx.arc(gx, gy, 26 * scH * pulse, 0.0, Math.PI * 2); ctx.fill()
    gwiazdaOsmioramienna(gx, gy, 15 * scH, 7 * scH, "#fff4b8", pulse)
}

/**
 * B. Rakiety i rozbłyski fajerwerków nad Rynkiem, Sylwester i Nowy Rok (weather-effects.js 3141–3194).
 * Tablica fwSlots z forEach przechodzi w cztery wywołania lokalnej funkcji slot w tej samej kolejności.
 */
private fun RamaSceny.fajerwerkiNadRynkiem(g: RatuszSezonowyGeo) = with(g) {
    fun slot(idx: Int, x0: Double, yPeak: Double, period: Double, offset: Double, col: String, core: String) {
        val cyc = ((t + offset) % period) / period
        if (cyc < 0.26) {
            val u = cyc / 0.26
            val ry = yRynek - u * (yRynek - yPeak)
            val rx = x0 + Math.sin(u * 6 + idx) * 2.2 * scH
            val tr = ctx.createLinearGradient(rx, ry, rx, ry + 26 * scH)
            tr.addColorStop(0.0, "#ffffff")
            tr.addColorStop(1.0, "rgba(245,208,96,0)")
            ctx.strokeStyle = tr
            ctx.lineWidth = Math.max(1.2, 1.8 * scH)
            ctx.beginPath(); ctx.moveTo(rx, ry); ctx.lineTo(rx, ry + 24 * scH); ctx.stroke()
            ctx.fillStyle = "#ffffff"
            ctx.beginPath(); ctx.arc(rx, ry, 1.8 * scH, 0.0, Math.PI * 2); ctx.fill()
        } else {
            val u = (cyc - 0.26) / 0.74
            val ease = 1 - Math.pow(1 - u, 2.2)
            val alpha = Math.max(0.0, 1 - Math.pow(u, 1.6))
            val rad = 28 * scH * ease
            if (isNight) {
                val bg = ctx.createRadialGradient(x0, yPeak, 1.0, x0, yPeak, rad * 1.25)
                bg.addColorStop(0.0, Plotno.rgba(245, 208, 96, alfaToFixed2(0.38 * alpha)))
                bg.addColorStop(1.0, "rgba(245,208,96,0)")
                ctx.fillStyle = bg
                ctx.beginPath(); ctx.arc(x0, yPeak, rad * 1.25, 0.0, Math.PI * 2); ctx.fill()
            }
            ctx.save()
            ctx.globalAlpha = alpha * (if (isNight) 1.0 else 0.86)
            val rays = 20
            for (k in 0 until rays) {
                val a = (2 * Math.PI * k) / rays + (if (k % 2 != 0) 0.12 else 0.0)
                val rLen = rad * (if (k % 2 == 0) 1.0 else 0.65)
                val x1 = x0 + Math.cos(a) * (rad * 0.18)
                val y1 = yPeak + Math.sin(a) * (rad * 0.18) + u * u * 4 * scH
                val x2 = x0 + Math.cos(a) * rLen
                val y2 = yPeak + Math.sin(a) * rLen + u * u * 11 * scH
                ctx.strokeStyle = col
                ctx.lineWidth = Math.max(1.0, 1.2 * scH)
                ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke()
                ctx.fillStyle = core
                ctx.beginPath(); ctx.arc(x2, y2, 1.3 * scH, 0.0, Math.PI * 2); ctx.fill()
            }
            ctx.restore()
        }
    }
    slot(0, w * 0.565, h * 0.152, 3.1, 0.0, "#f5d060", "#fffce6")
    slot(1, w * 0.852, h * 0.174, 3.5, 1.1, if (sceneType == "leszno-nowy-rok") "#38bdf8" else "#f43f5e", "#ffe4eb")
    slot(2, w * 0.752, h * 0.095, 3.8, 2.0, "#38bdf8", "#e0f8ff")
    slot(3, w * 0.925, h * 0.268, 3.3, 2.6, "#fde68a", "#ffffff")
}

/** 1. Ośnieżona płyta Rynku, nocna poświata pod Ratuszem i łuki bruku (weather-effects.js 3196–3240). */
private fun RamaSceny.plytaRynku(g: RatuszSezonowyGeo) = with(g) {
    val sqGrad = ctx.createLinearGradient(0.0, yRynek, 0.0, h)
    if (!isNight) {
        if (sceneType == "leszno-sylwester" || sceneType == "leszno-nowy-rok") {
            sqGrad.addColorStop(0.0, "#c8d4e4")
            sqGrad.addColorStop(1.0, "#aebed2")
        } else {
            sqGrad.addColorStop(0.0, "#e4effa")
            sqGrad.addColorStop(1.0, "#cde0f2")
        }
    } else {
        if (sceneType == "leszno-sylwester" || sceneType == "leszno-nowy-rok") {
            sqGrad.addColorStop(0.0, "#10182a")
            sqGrad.addColorStop(1.0, "#080e1a")
        } else {
            sqGrad.addColorStop(0.0, "#16243c")
            sqGrad.addColorStop(1.0, "#0c1628")
        }
    }
    ctx.fillStyle = sqGrad
    ctx.fillRect(0.0, yRynek, w, h - yRynek)

    if (isNight) {
        val gCol = if (sceneType == "leszno-zima") "rgba(56,189,248,0.34)"
        else if (sceneType == "leszno-sylwester" || sceneType == "leszno-nowy-rok") "rgba(212,175,55,0.36)"
        else "rgba(215,48,72,0.32)"
        // poświata pod ratuszem idzie za ratuszem: na stronie yRynek + 16 * scR (yBase = h * 0.667)
        val gy = yBase + (yRynek - h * 0.667) + 16 * rR
        val rg = ctx.createRadialGradient(cxR, gy, 4.0, cxR, gy, 64 * rR)
        rg.addColorStop(0.0, gCol)
        rg.addColorStop(1.0, "rgba(0,0,0,0)")
        ctx.fillStyle = rg
        ctx.beginPath(); ctx.arc(cxR, gy, 64 * rR, 0.0, Math.PI * 2); ctx.fill()
    }

    // Wachlarzowe łuki bruku spod śniegu (wokół ratusza, więc w skali ratusza)
    ctx.save()
    ctx.strokeStyle = if (isNight) "rgba(200,228,255,0.06)" else "rgba(120,150,185,0.16)"
    ctx.lineWidth = Math.max(0.7, 0.8 * rR)
    for (rad in 28 until 260 step 22) {
        ctx.beginPath()
        ctx.ellipse(cxR, yBase + 4 * rR, rad * 1.45 * rR, rad * 0.42 * rR, 0.0, 0.06 * Math.PI, 0.94 * Math.PI)
        ctx.stroke()
    }
    ctx.restore()
}

/** 2. Pierzeje kamieniczek wokół Rynku z czapami śniegu i dwoma rzędami okien (weather-effects.js 3242–3323). */
private fun RamaSceny.kamienicePierzei(g: RatuszSezonowyGeo) = with(g) {
    val dayPalettes = paletyKamienicRatusza
    val hwBase = Math.max(28.0, 34 * scR)
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
        val roofCol = if (isNight) "#101624" else pal.second

        ctx.fillStyle = wallCol
        ctx.fillRect(hxCursor, hy, hw, hh)
        ctx.fillStyle = roofCol
        ctx.beginPath()
        ctx.moveTo(hxCursor, hy)
        ctx.lineTo(hxCursor + 4 * scR, hy - 6.4 * scR)
        ctx.lineTo(hxCursor + hw - 4 * scR, hy - 6.4 * scR)
        ctx.lineTo(hxCursor + hw, hy)
        ctx.closePath()
        ctx.fill()

        if (hasGable) {
            ctx.fillStyle = wallCol
            ctx.beginPath()
            ctx.moveTo(hxCursor + hw * 0.25, hy)
            ctx.lineTo(hxCursor + hw * 0.50, hy - 9.2 * scR)
            ctx.lineTo(hxCursor + hw * 0.75, hy)
            ctx.closePath()
            ctx.fill()
        }

        // Śnieżna czapa na gzymsie i dachu kamienicy
        ctx.strokeStyle = if (isNight) "#d8ecff" else "#ffffff"
        ctx.lineWidth = Math.max(1.4, 1.9 * scR)
        ctx.beginPath()
        ctx.moveTo(hxCursor, hy)
        ctx.lineTo(hxCursor + hw, hy)
        ctx.stroke()

        // Dwa rzędy okien (w Wigilię wszystkie ciepło rozświetlone!)
        val winStep = 8 * scR
        for (rI in 0 until 2) {
            val wy = hy + (5 + rI * 8.5) * scR
            var wx = hxCursor + 4.5 * scR
            while (wx < hxCursor + hw - 5.5 * scR) {
                val lit = if (sceneType == "leszno-wigilia") true else (((Math.round(wx) * 7 + rI * 3 + hIdx * 5) % 4) != 0L)
                if (isNight || sceneType == "leszno-wigilia") {
                    ctx.fillStyle = if (lit) "rgba(255,218,126,0.88)" else "#121a2a"
                } else {
                    ctx.fillStyle = "#3e4e62"
                }
                ctx.fillRect(wx, wy, 3.3 * scR, 5.0 * scR)
                wx += winStep
            }
        }

        hxCursor += hw
        hIdx++
    }
}

/** Migoczące girlandy świetlne nad Rynkiem, Mikołajki i Święta (weather-effects.js 3325–3344). */
private fun RamaSceny.girlandyNadRynkiem(g: RatuszSezonowyGeo) = with(g) {
    for ((xs, xe, ys) in listOf(Triple(w * 0.04, gapL - 4 * scR, yRynek - 30 * scR), Triple(gapR + 4 * scR, w * 0.98, yRynek - 29 * scR))) {
        if (xe <= xs + 20) continue
        ctx.strokeStyle = "rgba(65,50,42,0.72)"
        ctx.lineWidth = Math.max(0.8, 0.95 * scR)
        ctx.beginPath()
        for (s in 0..16) {
            val u = s / 16.0
            val gx = xs + (xe - xs) * u
            val gy = ys + Math.sin(u * Math.PI) * 6.5 * scR
            if (s == 0) ctx.moveTo(gx, gy) else ctx.lineTo(gx, gy)
        }
        ctx.stroke()
        for (s in 1 until 16 step 2) {
            val u = s / 16.0
            val gx = xs + (xe - xs) * u
            val gy = ys + Math.sin(u * Math.PI) * 6.5 * scR
            val blink = 0.55 + 0.45 * Math.sin(t * 4.2 + s * 1.1)
            ctx.fillStyle = if (s % 4 == 1) Plotno.rgba(255, 232, 135, alfaToFixed2(blink)) else Plotno.rgba(255, 82, 102, alfaToFixed2(blink))
            ctx.beginPath(); ctx.arc(gx, gy + 0.6 * scR, 1.45 * scR, 0.0, Math.PI * 2); ctx.fill()
        }
    }
}

/** 3. Nocne smugi reflektorów za Ratuszem, tylko w nocy (weather-effects.js 3346–3378). */
private fun RamaSceny.reflektoryZaRatuszem(g: RatuszSezonowyGeo) = with(g) {
    val scR = rR // reflektory stoją przy ratuszu, więc w jego skali
    ctx.save()
    val sway = Math.sin(t * 0.85) * 9 * scR
    val cB1 = if (sceneType == "leszno-zima") "rgba(56,189,248,0.28)"
    else if (sceneType == "leszno-sylwester" || sceneType == "leszno-nowy-rok") "rgba(212,175,55,0.34)"
    else "rgba(205,28,58,0.30)"
    val cB2 = if (sceneType == "leszno-zima") "rgba(186,230,253,0.24)" else "rgba(245,214,128,0.26)"
    ctx.fillStyle = cB2
    ctx.beginPath()
    ctx.moveTo(X(-26.0), Y(20.0)); ctx.lineTo(X(-66.0) + sway, 0.0); ctx.lineTo(X(-24.0) + sway, 0.0); ctx.lineTo(X(-6.0), Y(20.0))
    ctx.closePath(); ctx.fill()
    ctx.fillStyle = cB1
    ctx.beginPath()
    ctx.moveTo(X(4.0), Y(20.0)); ctx.lineTo(X(14.0) - sway, 0.0); ctx.lineTo(X(60.0) - sway, 0.0); ctx.lineTo(X(28.0), Y(20.0))
    ctx.closePath(); ctx.fill()
    if (sceneType == "leszno-sylwester" || sceneType == "leszno-nowy-rok") {
        val sw2 = Math.cos(t * 1.15) * 18 * scR
        ctx.fillStyle = "rgba(255,238,170,0.20)"
        ctx.beginPath()
        ctx.moveTo(X(-8.0), Y(35.0)); ctx.lineTo(X(-92.0) + sw2, 0.0); ctx.lineTo(X(-70.0) + sw2, 0.0); ctx.lineTo(X(2.0), Y(35.0))
        ctx.closePath(); ctx.fill()
        ctx.beginPath()
        ctx.moveTo(X(8.0), Y(35.0)); ctx.lineTo(X(74.0) - sw2, 0.0); ctx.lineTo(X(96.0) - sw2, 0.0); ctx.lineTo(X(-2.0), Y(35.0))
        ctx.closePath(); ctx.fill()
    }
    ctx.restore()
}

/**
 * 4. Bryła Ratusza: ściany, cokół, pilastry, okna, portal ze schodami i wieńcem, ośnieżony dach z attyką
 * i sople na Zimę (weather-effects.js 3389–3437; kolory 3381–3388 w [RatuszSezonowyGeo]).
 */
private fun RamaSceny.ratuszBryla(g: RatuszSezonowyGeo) = with(g) {
    val scR = rR // grubości linii i kółka w skali ratusza
    fRect(-bw, 34.0, bw, 11.0, colWall)
    fRect(-bw, 34.0, -24.0, 11.0, colWallSh)
    fRect(-bw, 11.0, bw, 0.0, colPlinth)
    fRect(-bw - 0.5, 11.8, bw + 0.5, 10.5, colPil)
    for (px in listOf(-38.0, -25.0, -13.5, -5.8, 5.8, 13.5, 25.0, 38.0)) fRect(px - 1.5, 34.0, px + 1.5, 11.5, colPil)
    for (wx in listOf(-31.5, -19.2, -9.6, 9.6, 19.2, 31.5)) {
        fRect(wx - 2.3, 31.0, wx + 2.3, 24.8, colPil); fRect(wx - 1.5, 30.1, wx + 1.5, 25.6, colWin)
        fRect(wx - 2.3, 21.2, wx + 2.3, 14.8, colPil); fRect(wx - 1.5, 20.3, wx + 1.5, 15.6, colWin)
    }

    // Portal główny, schody wachlarzowe i wieniec świąteczny
    ctx.fillStyle = colPil
    ctx.beginPath(); ctx.moveTo(X(-5.6), Y(13.2)); ctx.lineTo(X(0.0), Y(17.0)); ctx.lineTo(X(5.6), Y(13.2)); ctx.closePath(); ctx.fill()
    fRect(-3.3, 13.2, 3.3, 2.5, colPil)
    fRect(-2.1, 10.5, 2.1, 2.5, "#40281c")
    ctx.fillStyle = "#e8f2fc"
    ctx.beginPath(); ctx.moveTo(X(-8.5), Y(0.0)); ctx.lineTo(X(-4.0), Y(3.0)); ctx.lineTo(X(4.0), Y(3.0)); ctx.lineTo(X(8.5), Y(0.0)); ctx.closePath(); ctx.fill()
    if (sceneType == "leszno-mikolajki" || sceneType == "leszno-swieta" || sceneType == "leszno-wigilia") {
        ctx.strokeStyle = "#1c6c34"
        ctx.lineWidth = Math.max(1.2, 1.7 * scR)
        ctx.beginPath(); ctx.arc(X(0.0), Y(12.8), 2.3 * scR, 0.0, Math.PI * 2); ctx.stroke()
        ctx.fillStyle = "#dc143c"
        ctx.beginPath(); ctx.arc(X(0.0), Y(15.0), 0.9 * scR, 0.0, Math.PI * 2); ctx.fill()
    }

    // Ośnieżony czterospadowy dach, biała attyka tralkowa i sople lodu
    ctx.fillStyle = if (isNight) "#cbe0f5" else "#e8f4ff"
    ctx.beginPath()
    ctx.moveTo(X(-37.0), Y(40.2)); ctx.lineTo(X(-30.0), Y(46.4)); ctx.lineTo(X(30.0), Y(46.4)); ctx.lineTo(X(37.0), Y(40.2))
    ctx.closePath(); ctx.fill()
    fRect(-39.5, 40.2, 39.5, 35.8, colPil)
    ctx.strokeStyle = "#ffffff"
    ctx.lineWidth = Math.max(1.5, 2.1 * scR)
    ctx.beginPath(); ctx.moveTo(X(-39.8), Y(40.5)); ctx.lineTo(X(39.8), Y(40.5)); ctx.stroke()
    if (sceneType == "leszno-zima") {
        ctx.fillStyle = if (isNight) "rgba(186,230,253,0.85)" else "rgba(224,242,254,0.92)"
        for (ix in -36..36 step 6) {
            val ilen = (2.2 + (Math.abs(ix) % 3) * 0.9)
            ctx.beginPath()
            ctx.moveTo(X(ix - 0.6), Y(35.8)); ctx.lineTo(X(ix + 0.6), Y(35.8)); ctx.lineTo(X(ix.toDouble()), Y(35.8 - ilen))
            ctx.closePath(); ctx.fill()
        }
    }
}

/**
 * Wieża Ratusza z zegarem, ażurową latarnią i barokowym hełmem (weather-effects.js 3439–3494).
 * Zegar: `new Date()` strony zastępuje [RamaSceny.teraz] (czas warszawski).
 */
private fun RamaSceny.ratuszWieza(g: RatuszSezonowyGeo) = with(g) {
    val scR = rR // zegar, wskazówki i latarnia w skali ratusza
    // Wieża Ratusza + Zegar + Ażurowa Latarnia i Barokowy Hełm
    fRect(-12.5, 62.0, 12.5, 40.0, colWall)
    fRect(-12.5, 62.0, -10.3, 40.0, colPil); fRect(10.3, 62.0, 12.5, 40.0, colPil)
    fRect(-1.9, 53.0, 1.9, 46.0, colWin)
    fRect(-14.0, 64.0, 14.0, 61.8, colPil)
    fRect(-11.8, 85.0, 11.8, 64.0, colWall)
    fRect(-13.6, 86.8, 13.6, 85.0, colPil)

    ctx.fillStyle = colPil
    ctx.beginPath(); ctx.arc(X(0.0), Y(77.5), 5.8 * scR, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = if (isNight) "#ffeeb4" else "#242830"
    ctx.beginPath(); ctx.arc(X(0.0), Y(77.5), 5.0 * scR, 0.0, Math.PI * 2); ctx.fill()
    ctx.strokeStyle = if (isNight) "#1e1814" else "#eec65f"
    ctx.lineCap = "round"
    ctx.lineWidth = Math.max(0.9, 1.1 * scR)
    ctx.beginPath()
    if (sceneType == "leszno-nowy-rok") {
        ctx.moveTo(X(0.0), Y(77.5)); ctx.lineTo(X(0.0), Y(81.6))
    } else if (sceneType == "leszno-sylwester") {
        ctx.moveTo(X(0.0), Y(77.5)); ctx.lineTo(X(-1.1), Y(81.5))
        ctx.moveTo(X(0.0), Y(77.5)); ctx.lineTo(X(-0.3), Y(80.6))
    } else {
        val clkNow = teraz
        val minAng = ((clkNow.minute + clkNow.second / 60.0) / 60) * Math.PI * 2 - Math.PI * 0.5
        val hrAng = (((clkNow.hour % 12) + clkNow.minute / 60.0) / 12) * Math.PI * 2 - Math.PI * 0.5
        ctx.moveTo(X(0.0), Y(77.5))
        ctx.lineTo(X(0.0) + Math.cos(hrAng) * 2.8 * scR, Y(77.5) + Math.sin(hrAng) * 2.8 * scR)
        ctx.moveTo(X(0.0), Y(77.5))
        ctx.lineTo(X(0.0) + Math.cos(minAng) * 4.0 * scR, Y(77.5) + Math.sin(minAng) * 4.0 * scR)
    }
    ctx.stroke()

    fRect(-7.5, 97.0, 7.5, 86.8, colWall)
    ctx.fillStyle = colHelm
    ctx.beginPath()
    ctx.moveTo(X(-7.2), Y(97.8)); ctx.lineTo(X(-9.2), Y(102.5)); ctx.lineTo(X(-8.4), Y(107.0))
    ctx.lineTo(X(-5.2), Y(111.5)); ctx.lineTo(X(5.2), Y(111.5)); ctx.lineTo(X(8.4), Y(107.0))
    ctx.lineTo(X(9.2), Y(102.5)); ctx.lineTo(X(7.2), Y(97.8))
    ctx.closePath(); ctx.fill()
    if (isNight) {
        fRect(-4.2, 120.8, 4.2, 112.0, if (sceneType == "leszno-zima") "rgba(125,211,252,0.84)" else "rgba(255,220,120,0.86)")
    }
    ctx.strokeStyle = colHelm
    ctx.lineWidth = Math.max(1.0, 1.2 * scR)
    for (lx in listOf(-4.4, -1.5, 1.5, 4.4)) {
        ctx.beginPath(); ctx.moveTo(X(lx), Y(111.5)); ctx.lineTo(X(lx), Y(121.2)); ctx.stroke()
    }
    fRect(-5.6, 122.2, 5.6, 120.8, colHelm)
    ctx.fillStyle = colHelm
    ctx.beginPath()
    ctx.moveTo(X(-4.8), Y(122.2)); ctx.lineTo(X(-6.0), Y(126.0)); ctx.lineTo(X(-4.0), Y(131.0))
    ctx.lineTo(X(0.0), Y(143.0)); ctx.lineTo(X(4.0), Y(131.0)); ctx.lineTo(X(6.0), Y(126.0)); ctx.lineTo(X(4.8), Y(122.2))
    ctx.closePath(); ctx.fill()
    ctx.strokeStyle = if (isNight) "#ebd07d" else "#3c4048"
    ctx.lineWidth = Math.max(0.9, 1.0 * scR)
    ctx.beginPath(); ctx.moveTo(X(0.0), Y(143.0)); ctx.lineTo(X(0.0), Y(153.0)); ctx.stroke()
}

/**
 * Poduszki czytelności pod trzema kolumnami statystyk i prognozy karty (weather-effects.js 3496–3508);
 * na stronie tylko w trybie karty (this._sidebarMode && this._cardData).
 */
private fun RamaSceny.poduszkiCzytelnosci(g: RatuszSezonowyGeo) = with(g) {
    val scPill = Math.min(w / 380.0, h / 240.0)
    val pTop = h * 0.60 - Math.max(28L, Math.round(34 * scPill)).toDouble() - 4 * scH
    val pBot = yg - 2 * scH
    if (pBot > pTop + 20) {
        for (cxCol in listOf(w / 6, w * 0.5, (w * 5) / 6)) {
            ctx.fillStyle = if (isNight) "rgba(6,12,26,0.30)" else "rgba(6,12,26,0.36)"
            rrPath(cxCol - 35 * scPill, pTop, 70 * scPill, pBot - pTop, 11 * scPill)
            ctx.fill()
        }
    }
}

/** 5. Zima: duży bałwanek w cylindrze i błękitnym szaliku machający gałązką (weather-effects.js 3511–3558). */
private fun RamaSceny.balwanWCylindrze(g: RatuszSezonowyGeo) = with(g) {
    // Duży Bałwanek w cylindrze i błękitnym szaliku machający gałązką!
    val cx = fgX
    val sc = 1.70 * scH
    val bob = Math.sin(t * 2.0) * 1.2 * scH
    val wave = Math.sin(t * 4.2) * 4.5 * scH
    fun SX(d: Double) = cx + d * sc
    fun SY(d: Double) = yg - d * sc + bob

    ctx.fillStyle = if (isNight) "#d4eafc" else "#f6fbff"
    ctx.beginPath(); ctx.ellipse(cx, yg + 1 * scH, 31 * scH, 5.5 * scH, 0.0, 0.0, Math.PI * 2); ctx.fill()

    ctx.strokeStyle = "#603e26"
    ctx.lineWidth = Math.max(1.6, 2.2 * scH)
    ctx.lineCap = "round"
    ctx.beginPath(); ctx.moveTo(SX(-6.2), SY(22.5)); ctx.lineTo(SX(-14.8), SY(27.5)); ctx.stroke()
    ctx.beginPath(); ctx.moveTo(SX(6.2), SY(22.8)); ctx.lineTo(SX(14.8), SY(30.2) + wave); ctx.stroke()

    for ((dyc, br) in listOf(8.8 to 9.6, 21.2 to 7.3, 31.6 to 5.7)) {
        ctx.fillStyle = if (isNight) "#eaf4ff" else "#ffffff"
        ctx.strokeStyle = if (isNight) "#9ecaf0" else "#c4dcf2"
        ctx.lineWidth = Math.max(1.0, 1.3 * scH)
        ctx.beginPath(); ctx.arc(SX(0.0), SY(dyc), br * sc, 0.0, Math.PI * 2); ctx.fill(); ctx.stroke()
    }

    ctx.fillStyle = "#161e2c"
    for (by in listOf(22.2, 18.5, 14.8)) {
        ctx.beginPath(); ctx.arc(SX(0.0), SY(by), 1.8 * scH, 0.0, Math.PI * 2); ctx.fill()
    }
    for (ex in listOf(-2.0, 1.8)) {
        ctx.beginPath(); ctx.arc(SX(ex), SY(32.8), 1.6 * scH, 0.0, Math.PI * 2); ctx.fill()
    }

    ctx.fillStyle = "#f97316"
    ctx.beginPath(); ctx.moveTo(SX(0.0), SY(32.1)); ctx.lineTo(SX(6.8), SY(30.8)); ctx.lineTo(SX(0.0), SY(30.0)); ctx.closePath(); ctx.fill()

    val scarfSway = Math.sin(t * 3.4) * 3.2 * scH
    ctx.fillStyle = "#0284c7"
    rrPath(SX(-6.4), SY(27.5), 12.8 * sc, 3.2 * sc, 3 * scH)
    ctx.fill()
    ctx.fillStyle = "#38bdf8"
    ctx.beginPath()
    ctx.moveTo(SX(2.0), SY(25.8)); ctx.lineTo(SX(5.8), SY(25.8))
    ctx.lineTo(SX(8.0) + scarfSway, SY(16.2)); ctx.lineTo(SX(3.8) + scarfSway, SY(16.0))
    ctx.closePath(); ctx.fill()

    ctx.fillStyle = "#141c2c"
    rrPath(SX(-7.8), SY(37.4), 15.6 * sc, 2.1 * sc, 2 * scH)
    ctx.fill()
    ctx.fillRect(SX(-5.1), SY(45.8), 10.2 * sc, 9.2 * sc)
    ctx.fillStyle = "#38bdf8"
    ctx.fillRect(SX(-5.1), SY(38.9), 10.2 * sc, 2.0 * sc)
}

/**
 * 5. Mikołajki: duży św. Mikołaj w złotych okularach z workiem, laską cukrową, prezentami
 * i małą choinką przy prawej krawędzi (weather-effects.js 3560–3633).
 */
private fun RamaSceny.swietyMikolajZWorkiem(g: RatuszSezonowyGeo) = with(g) {
    // Duży Św. Mikołaj w złotych okularach z workiem i górą prezentów na pierwszym planie!
    val cx = fgX * 0.92
    val sc = 1.68 * scH
    val wave = Math.sin(t * 3.8) * 3.8 * scH
    fun SX(d: Double) = cx + d * sc
    fun SY(d: Double) = yg - d * sc

    ctx.fillStyle = if (isNight) "#d8e8f8" else "#f6fbff"
    ctx.beginPath(); ctx.ellipse(cx + 10 * scH, yg + 1 * scH, 38 * scH, 5.5 * scH, 0.0, 0.0, Math.PI * 2); ctx.fill()

    // Mikołaj narysowany od nowa (na stronie bryły bez twarzy i z płaską czapką; Karol 24.11: „6 grudnia Mikołaj też
    // wygląda jak by go ktoś wypluł”): worek z prezentami i laską cukrową, buty, płaszcz rozszerzony do dołu z białym
    // futrem i pasem ze złotą klamrą, ręka trzymająca worek i druga machająca, twarz z rumieńcami, puszysta broda
    // z wąsami, złote okulary i opadająca czapka z futrzanym otokiem i pomponem. Wymiary i miejsce jak na stronie.
    // Worek jutowy ze związaną szyjką, wystające prezenty i laska cukrowa
    ctx.fillStyle = "#a87545"
    ctx.beginPath(); ctx.ellipse(SX(-9.5), SY(11.5), 7.6 * sc, 10.2 * sc, -0.12, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#8c5e34"
    ctx.beginPath(); ctx.ellipse(SX(-11.5), SY(9.0), 3.2 * sc, 6.0 * sc, -0.12, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#14764a"; ctx.fillRect(SX(-13.2), SY(24.6), 4.4 * sc, 4.0 * sc)
    ctx.fillStyle = "#f5ce58"; ctx.fillRect(SX(-11.4), SY(24.6), 0.8 * sc, 4.0 * sc)
    ctx.fillStyle = "#c01630"; ctx.fillRect(SX(-8.6), SY(23.6), 3.6 * sc, 3.2 * sc)
    ctx.fillStyle = "#a87545"
    ctx.beginPath(); ctx.moveTo(SX(-13.6), SY(20.4)); ctx.lineTo(SX(-5.4), SY(20.4)); ctx.lineTo(SX(-7.6), SY(21.6)); ctx.lineTo(SX(-11.4), SY(21.6)); ctx.closePath(); ctx.fill()
    ctx.strokeStyle = "#6b4423"
    ctx.lineWidth = Math.max(1.0, 1.2 * scH)
    ctx.beginPath(); ctx.moveTo(SX(-12.6), SY(20.6)); ctx.lineTo(SX(-6.4), SY(20.6)); ctx.stroke()
    ctx.strokeStyle = "#dc143c"
    ctx.lineWidth = Math.max(1.5, 2.1 * scH)
    ctx.beginPath(); ctx.arc(SX(-3.6), SY(25.2), 1.8 * sc, Math.PI, 0.0); ctx.lineTo(SX(-1.8), SY(20.0)); ctx.stroke()

    // Buty z noskami
    ctx.fillStyle = "#1a161a"
    rrPath(SX(-5.6), SY(4.4), 5.0 * sc, 4.4 * sc, 1.6 * sc); ctx.fill()
    rrPath(SX(0.6), SY(4.4), 5.0 * sc, 4.4 * sc, 1.6 * sc); ctx.fill()

    // Płaszcz rozszerzony do dołu, zaokrąglony brzeg
    ctx.fillStyle = "#cc1630"
    ctx.beginPath()
    ctx.moveTo(SX(-9.4), SY(6.2))
    ctx.quadraticCurveTo(SX(-8.6), SY(17.0), SX(-6.4), SY(25.6))
    ctx.quadraticCurveTo(SX(0.0), SY(27.4), SX(6.4), SY(25.6))
    ctx.quadraticCurveTo(SX(8.6), SY(17.0), SX(9.4), SY(6.2))
    ctx.quadraticCurveTo(SX(0.0), SY(4.4), SX(-9.4), SY(6.2))
    ctx.closePath(); ctx.fill()
    // ciemniejszy bok płaszcza (bryła)
    ctx.fillStyle = "#a8102a"
    ctx.beginPath()
    ctx.moveTo(SX(5.0), SY(25.2)); ctx.quadraticCurveTo(SX(8.6), SY(17.0), SX(9.4), SY(6.2)); ctx.lineTo(SX(6.4), SY(5.6))
    ctx.quadraticCurveTo(SX(6.8), SY(16.0), SX(5.0), SY(25.2)); ctx.closePath(); ctx.fill()

    // Białe futro: dół płaszcza i pas z przodu
    ctx.fillStyle = "#fffdfa"
    rrPath(SX(-9.8), SY(8.2), 19.6 * sc, 3.4 * sc, 1.7 * sc); ctx.fill()
    rrPath(SX(-1.7), SY(25.4), 3.4 * sc, 17.6 * sc, 1.2 * sc); ctx.fill()

    // Pas ze złotą klamrą
    ctx.fillStyle = "#1c1616"
    ctx.fillRect(SX(-8.3), SY(15.4), 16.6 * sc, 2.8 * sc)
    ctx.fillStyle = "#f5cc4e"
    rrPath(SX(-2.3), SY(15.9), 4.6 * sc, 3.8 * sc, 0.6 * sc); ctx.fill()
    ctx.fillStyle = "#1c1616"
    ctx.fillRect(SX(-1.1), SY(14.9), 2.2 * sc, 1.8 * sc)

    // Lewa ręka trzyma worek, prawa macha
    ctx.lineCap = "round"
    ctx.strokeStyle = "#cc1630"
    ctx.lineWidth = Math.max(3.5, 4.6 * scH)
    ctx.beginPath(); ctx.moveTo(SX(-6.0), SY(23.4)); ctx.quadraticCurveTo(SX(-9.6), SY(21.0), SX(-9.0), SY(18.6)); ctx.stroke()
    ctx.beginPath(); ctx.moveTo(SX(6.0), SY(23.4)); ctx.lineTo(SX(12.6), SY(28.4) + wave); ctx.stroke()
    ctx.fillStyle = "#ffffff"
    ctx.beginPath(); ctx.arc(SX(-9.0), SY(18.2), 2.2 * sc, 0.0, Math.PI * 2); ctx.fill()
    ctx.beginPath(); ctx.arc(SX(13.4), SY(29.0) + wave, 2.3 * sc, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#fffdfa"
    ctx.beginPath(); ctx.arc(SX(11.6), SY(27.6) + wave * 0.8, 1.6 * sc, 0.0, Math.PI * 2); ctx.fill()

    // Głowa: twarz, rumieńce, oczy, nos
    ctx.fillStyle = "#fed6bc"
    ctx.beginPath(); ctx.arc(SX(0.0), SY(29.4), 4.6 * sc, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "rgba(232,120,120,0.55)"
    ctx.beginPath(); ctx.arc(SX(-2.7), SY(28.4), 1.1 * sc, 0.0, Math.PI * 2); ctx.fill()
    ctx.beginPath(); ctx.arc(SX(2.7), SY(28.4), 1.1 * sc, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#2a1a12"
    ctx.beginPath(); ctx.arc(SX(-1.6), SY(30.4), 0.55 * sc, 0.0, Math.PI * 2); ctx.fill()
    ctx.beginPath(); ctx.arc(SX(1.6), SY(30.4), 0.55 * sc, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#f0a98e"
    ctx.beginPath(); ctx.arc(SX(0.0), SY(29.0), 1.1 * sc, 0.0, Math.PI * 2); ctx.fill()

    // Puszysta broda z wąsami
    ctx.fillStyle = "#ffffff"
    listOf(-3.8 to 26.4, 3.8 to 26.4, -2.6 to 23.4, 2.6 to 23.4, 0.0 to 21.4, 0.0 to 24.6).forEach { (bx, by) ->
        ctx.beginPath(); ctx.arc(SX(bx), SY(by), 2.7 * sc, 0.0, Math.PI * 2); ctx.fill()
    }
    ctx.beginPath(); ctx.ellipse(SX(-1.9), SY(27.9), 2.2 * sc, 1.1 * sc, 0.25, 0.0, Math.PI * 2); ctx.fill()
    ctx.beginPath(); ctx.ellipse(SX(1.9), SY(27.9), 2.2 * sc, 1.1 * sc, -0.25, 0.0, Math.PI * 2); ctx.fill()

    // Złote okulary jak na stronie
    ctx.strokeStyle = "#ebbe3e"
    ctx.lineWidth = Math.max(0.9, 1.1 * scH)
    ctx.beginPath(); ctx.arc(SX(-1.7), SY(30.4), 1.4 * sc, 0.0, Math.PI * 2); ctx.stroke()
    ctx.beginPath(); ctx.arc(SX(1.7), SY(30.4), 1.4 * sc, 0.0, Math.PI * 2); ctx.stroke()

    // Czapka opadająca na bok, futrzany otok i pompon
    ctx.fillStyle = "#d61630"
    ctx.beginPath()
    ctx.moveTo(SX(-5.0), SY(32.4))
    ctx.quadraticCurveTo(SX(-2.6), SY(42.0), SX(4.2), SY(41.4))
    ctx.quadraticCurveTo(SX(8.4), SY(40.6) + wave * 0.35, SX(9.4), SY(36.4) + wave * 0.35)
    ctx.quadraticCurveTo(SX(5.6), SY(37.6), SX(5.0), SY(32.4))
    ctx.closePath(); ctx.fill()
    ctx.fillStyle = "#ffffff"
    rrPath(SX(-6.2), SY(34.6), 12.4 * sc, 3.2 * sc, 1.6 * sc); ctx.fill()
    ctx.beginPath(); ctx.arc(SX(9.6), SY(35.8) + wave * 0.35, 2.2 * sc, 0.0, Math.PI * 2); ctx.fill()

    fun prezent(gx: Double, gyt: Double, gw: Double, gh: Double, bc: String, rc: String) {
        ctx.fillStyle = bc; ctx.fillRect(SX(gx), SY(gyt), gw * sc, gh * sc)
        ctx.fillStyle = rc
        ctx.fillRect(SX(gx + gw * 0.42), SY(gyt), gw * 0.18 * sc, gh * sc)
        ctx.fillRect(SX(gx), SY(gyt - gh * 0.42), gw * sc, gh * 0.18 * sc)
    }
    prezent(10.5, 11.2, 9.5, 11.2, "#c01630", "#f5ce58")
    prezent(20.8, 8.4, 8.4, 8.4, "#14764a", "#f8d669")
    prezent(14.0, 17.8, 7.2, 6.6, "#daac36", "#c61630")

    choinkaSwiateczna(g, w * 0.935, yg - 4 * scH, 56 * scH, false)
}

/** 5. Święta: drewniany domek Jarmarku Świątecznego z parą znad kubków (weather-effects.js 3638–3663). */
private fun RamaSceny.domekJarmarku(g: RatuszSezonowyGeo) = with(g) {
    // Drewniany domek Jarmarku Świątecznego na pierwszym planie z unoszącą się parą znad kubków!
    val wc = 38 * scH
    val hc = 23 * scH
    val x0 = w - wc - 6 * scH
    val ygC = yg - 3 * scH
    val yt = ygC - hc
    ctx.fillStyle = if (isNight) "#603822" else "#885230"
    ctx.fillRect(x0, yt, wc, hc)
    ctx.fillStyle = "#ffe284"
    ctx.fillRect(x0 + 4.5 * scH, yt + 5.5 * scH, wc - 9 * scH, 9.2 * scH)
    ctx.fillStyle = "#cc1830"
    ctx.fillRect(x0 + 9 * scH, yt + 11.2 * scH, 3.2 * scH, 3.2 * scH)
    ctx.fillRect(x0 + 16 * scH, yt + 11.2 * scH, 3.2 * scH, 3.2 * scH)
    listOf(10.6, 17.6).forEachIndexed { mi, msx ->
        val stY = ((t * 10 + mi * 5) % 9.0) * scH
        ctx.strokeStyle = Plotno.rgba(255, 255, 255, alfaToFixed2(0.65 * (1 - stY / (9 * scH))))
        ctx.lineWidth = Math.max(0.9, 1.1 * scH)
        ctx.beginPath()
        ctx.moveTo(x0 + msx * scH, yt + 10.5 * scH - stY)
        ctx.quadraticCurveTo(x0 + msx * scH + Math.sin(t * 4 + mi) * 2 * scH, yt + 7.5 * scH - stY, x0 + msx * scH, yt + 4.5 * scH - stY)
        ctx.stroke()
    }
    ctx.fillStyle = "#4a261c"
    ctx.beginPath(); ctx.moveTo(x0 - 3.5 * scH, yt + 0.8 * scH); ctx.lineTo(x0 + wc * 0.5, yt - 9.8 * scH); ctx.lineTo(x0 + wc + 3.5 * scH, yt + 0.8 * scH); ctx.closePath(); ctx.fill()
    ctx.strokeStyle = "#ffffff"
    ctx.lineWidth = Math.max(1.6, 2.4 * scH)
    ctx.beginPath(); ctx.moveTo(x0 - 3.5 * scH, yt + 0.5 * scH); ctx.lineTo(x0 + wc * 0.5, yt - 10 * scH); ctx.lineTo(x0 + wc + 3.5 * scH, yt + 0.5 * scH); ctx.stroke()
}

/** 5. Wigilia: złote sanie św. Mikołaja z reniferami i Rudolfem lecące nad iglicą Ratusza (weather-effects.js 3665–3722). */
private fun RamaSceny.zloteSanie(g: RatuszSezonowyGeo) = with(g) {
    // Złote Sanie Św. Mikołaja z galopującymi reniferami i Rudolfem lecące na żywo nad iglicą Ratusza!
    val flyU = (t * 0.085) % 1.0
    // Karta na Starcie: sanie większe i niżej, w wolnym pasie między temperaturą a kolumnami (nad nimi zasłaniały napisy)
    val wiekszeSanie = if (kartaStartu) 1.35 else 1.0
    val sx0 = -75 * scH * wiekszeSanie + flyU * (w + 150 * scH * wiekszeSanie)
    val sy0 = (if (kartaStartu) h * 0.43 else h * 0.16) + Math.sin(t * 2.2) * 3.5 * scH
    val scS = 1.48 * scH * wiekszeSanie
    fun SX(d: Double) = sx0 + d * scS
    fun SY(d: Double) = sy0 + d * scS
    val gold = if (isNight) "#f8d66c" else "#da9e20"

    for (i in 0 until 24) {
        val u = i / 23.0
        val dx = -34 + u * 38
        val dy = 10 - Math.sin(u * Math.PI * 0.65) * 10 + Math.sin(t * 5 + i) * 1.1
        ctx.fillStyle = Plotno.rgba(255, 236, 150, alfaToFixed2(0.18 + u * 0.72))
        ctx.beginPath(); ctx.arc(SX(dx), SY(dy), (0.8 + u * 1.1) * scS, 0.0, Math.PI * 2); ctx.fill()
    }
    // płoza z podwiniętym przodem
    ctx.strokeStyle = gold
    ctx.lineWidth = Math.max(1.4, 2.0 * scH)
    ctx.beginPath(); ctx.moveTo(SX(-2.0), SY(5.6)); ctx.lineTo(SX(17.0), SY(4.5))
    ctx.quadraticCurveTo(SX(20.5), SY(4.2), SX(19.6), SY(1.4)); ctx.stroke()

    // Mikołaj w saniach (na stronie tylko czerwone i białe kółko; Karol 24.11: „Mikołaj jak przelatuje wygląda
    // tragicznie i czapki nie ma”): worek z prezentem z tyłu, czerwony płaszcz z pasem, ręka na lejcach,
    // twarz, biała broda i czerwona czapka z białym otokiem i pomponem. Dół zasłania pudło sań.
    ctx.fillStyle = "#7a3b1d"
    ctx.beginPath(); ctx.ellipse(SX(1.6), SY(-5.6), 3.4 * scS, 3.9 * scS, -0.25, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#2e7d32"
    ctx.fillRect(SX(-1.4), SY(-10.4), 3.6 * scS, 3.2 * scS)
    ctx.fillStyle = gold
    ctx.fillRect(SX(0.1), SY(-10.4), 0.7 * scS, 3.2 * scS)
    ctx.fillStyle = "#d01832"
    ctx.beginPath(); ctx.ellipse(SX(8.2), SY(-5.4), 3.3 * scS, 3.9 * scS, 0.0, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#ffffff"
    ctx.fillRect(SX(8.0), SY(-8.9), 0.9 * scS, 6.2 * scS)
    ctx.fillStyle = "#1b1b1b"
    ctx.fillRect(SX(5.1), SY(-4.4), 6.3 * scS, 0.9 * scS)
    ctx.strokeStyle = "#d01832"
    ctx.lineCap = "round"
    ctx.lineWidth = Math.max(1.2, 1.5 * scS)
    ctx.beginPath(); ctx.moveTo(SX(9.6), SY(-6.6)); ctx.lineTo(SX(12.6), SY(-4.6)); ctx.stroke()
    ctx.fillStyle = "#ffffff"
    ctx.beginPath(); ctx.arc(SX(12.8), SY(-4.5), 0.75 * scS, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#f4c7a1"
    ctx.beginPath(); ctx.arc(SX(8.9), SY(-10.9), 2.0 * scS, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#ffffff"
    ctx.beginPath()
    ctx.moveTo(SX(6.8), SY(-10.5)); ctx.quadraticCurveTo(SX(7.6), SY(-6.6), SX(9.6), SY(-7.2))
    ctx.quadraticCurveTo(SX(11.4), SY(-8.0), SX(11.0), SY(-10.4)); ctx.closePath(); ctx.fill()
    ctx.fillStyle = "#2a1a12"
    ctx.beginPath(); ctx.arc(SX(9.9), SY(-11.4), 0.32 * scS, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#e8a08a"
    ctx.beginPath(); ctx.arc(SX(10.8), SY(-10.5), 0.5 * scS, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#d01832"
    ctx.beginPath()
    ctx.moveTo(SX(6.6), SY(-12.2)); ctx.quadraticCurveTo(SX(7.6), SY(-16.4), SX(4.0), SY(-16.2))
    ctx.quadraticCurveTo(SX(9.0), SY(-17.2), SX(11.2), SY(-12.2)); ctx.closePath(); ctx.fill()
    ctx.fillStyle = "#ffffff"
    ctx.fillRect(SX(6.3), SY(-13.0), 5.2 * scS, 1.3 * scS)
    ctx.beginPath(); ctx.arc(SX(3.9), SY(-16.2), 0.95 * scS, 0.0, Math.PI * 2); ctx.fill()

    // złote pudło sań z ozdobnym zawijasem z przodu
    ctx.fillStyle = gold
    ctx.beginPath()
    ctx.moveTo(SX(-0.5), SY(3.8)); ctx.lineTo(SX(13.5), SY(2.6)); ctx.lineTo(SX(16.2), SY(-2.8))
    ctx.lineTo(SX(6.5), SY(-1.4)); ctx.lineTo(SX(-2.0), SY(-4.2))
    ctx.closePath(); ctx.fill()
    ctx.strokeStyle = gold
    ctx.lineWidth = Math.max(1.0, 1.2 * scS)
    ctx.beginPath(); ctx.arc(SX(16.6), SY(-4.2), 1.5 * scS, Math.PI * 0.5, Math.PI * 2.2); ctx.stroke()
    ctx.strokeStyle = "#d01832"
    ctx.lineWidth = Math.max(0.8, 0.7 * scS)
    ctx.beginPath(); ctx.moveTo(SX(0.4), SY(0.6)); ctx.lineTo(SX(13.0), SY(-0.4)); ctx.stroke()

    ctx.strokeStyle = "rgba(255,242,180,0.82)"
    ctx.lineWidth = Math.max(0.9, 1.1 * scH)
    ctx.beginPath(); ctx.moveTo(SX(14.5), SY(-1.2)); ctx.lineTo(SX(42.0), SY(-5.8)); ctx.stroke()
    listOf(25.5 to -3.2, 41.5 to -6.4).forEachIndexed { ri, (rx, ry) ->
        val gallop = Math.sin(t * 8 + ri * 1.6) * 2.2 * scH
        ctx.fillStyle = gold
        ctx.beginPath(); ctx.ellipse(SX(rx), SY(ry), 4.6 * scS, 2.1 * scS, 0.0, 0.0, Math.PI * 2); ctx.fill()
        ctx.beginPath()
        ctx.moveTo(SX(rx + 2.5), SY(ry - 1)); ctx.lineTo(SX(rx + 5.4), SY(ry - 5.4))
        ctx.lineTo(SX(rx + 8.6), SY(ry - 4.0)); ctx.lineTo(SX(rx + 4.4), SY(ry + 0.6))
        ctx.closePath(); ctx.fill()
        ctx.strokeStyle = gold
        ctx.lineWidth = Math.max(1.1, 1.5 * scH)
        ctx.beginPath(); ctx.moveTo(SX(rx - 3.2), SY(ry + 1.2)); ctx.lineTo(SX(rx - 6.8) + gallop, SY(ry + 4.8)); ctx.stroke()
        ctx.beginPath(); ctx.moveTo(SX(rx + 2.8), SY(ry + 1.0)); ctx.lineTo(SX(rx + 6.8) - gallop, SY(ry + 3.8)); ctx.stroke()
        ctx.lineWidth = Math.max(0.9, 1.1 * scH)
        ctx.beginPath(); ctx.moveTo(SX(rx + 5.2), SY(ry - 5)); ctx.lineTo(SX(rx + 4.0), SY(ry - 8.6)); ctx.stroke()
        if (ri == 1) {
            val nr = ctx.createRadialGradient(SX(rx + 8.8), SY(ry - 4.0), 0.5, SX(rx + 8.8), SY(ry - 4.0), 8 * scH)
            nr.addColorStop(0.0, "rgba(255,38,68,0.95)")
            nr.addColorStop(1.0, "rgba(255,38,68,0)")
            ctx.fillStyle = nr
            ctx.beginPath(); ctx.arc(SX(rx + 8.8), SY(ry - 4.0), 8 * scH, 0.0, Math.PI * 2); ctx.fill()
            ctx.fillStyle = "#ff2644"
            ctx.beginPath(); ctx.arc(SX(rx + 8.8), SY(ry - 4.0), 2.1 * scH, 0.0, Math.PI * 2); ctx.fill()
        }
    }
}

/**
 * 5. Sylwester i Nowy Rok: złota poświata, ciemny cokół ze złotą krawędzią, fontanny iskier (Nowy Rok)
 * albo kieliszki szampana (Sylwester) i napis roku (weather-effects.js 3725–3794).
 */
private fun RamaSceny.instalacjaNoworoczna(g: RatuszSezonowyGeo) = with(g) {
    // Duża złota instalacja 3D "2027" na pierwszym planie + kieliszki szampana / fontanny iskier!
    val cx = fgX
    val glowG = ctx.createRadialGradient(cx, yg - 26 * scH, 2.0, cx, yg - 26 * scH, 44 * scH)
    glowG.addColorStop(0.0, if (isNight) "rgba(212,175,55,0.48)" else "rgba(212,175,55,0.28)")
    glowG.addColorStop(1.0, "rgba(212,175,55,0)")
    ctx.fillStyle = glowG
    ctx.beginPath(); ctx.arc(cx, yg - 26 * scH, 44 * scH, 0.0, Math.PI * 2); ctx.fill()

    ctx.fillStyle = "#0e1628"
    ctx.strokeStyle = "#dabe48"
    ctx.lineWidth = Math.max(1.1, 1.4 * scH)
    // w Sylwestra szerszy cokół pod napis „2026/2027” (mniejsza czcionka, żeby mieścił się między kolumnami prognozy)
    val polCokolu = if (sceneType == "leszno-sylwester") 38.0 else 33.0
    rrPath(cx - polCokolu * scH, yg - 8.5 * scH, 2 * polCokolu * scH, 8.5 * scH, 2.5 * scH)
    ctx.fill()
    ctx.stroke()

    if (sceneType == "leszno-nowy-rok") {
        fontannyIskier(g, cx)
    } else {
        kieliszkiSzampana(g, cx)
    }

    napisRoku(g, cx)
}

/** Nowy Rok: dwie fontanny sceniczne ze złotymi iskrami i ośmioramienna gwiazda (weather-effects.js 3741–3756). */
private fun RamaSceny.fontannyIskier(g: RatuszSezonowyGeo, cx: Double) = with(g) {
    // Dwie żywe fontanny sceniczne tryskające złotymi iskrami!
    listOf(cx - 29 * scH, cx + 29 * scH).forEachIndexed { fi, fx ->
        ctx.fillStyle = "#d4af37"
        ctx.fillRect(fx - 2.2 * scH, yg - 13 * scH, 4.4 * scH, 4.5 * scH)
        for (s in 0 until 12) {
            val u = ((t * 2.6 + s * 0.19 + fi * 0.4) % 1.0)
            val ang = -Math.PI * 0.5 + Math.sin(s * 2.3 + t * 3) * 0.48
            val dist = u * 24 * scH
            val px = fx + Math.cos(ang) * dist
            val py = (yg - 13 * scH) + Math.sin(ang) * dist + u * u * 5 * scH
            ctx.fillStyle = Plotno.rgba(255, 240, 150, alfaToFixed2(1 - u))
            ctx.beginPath(); ctx.arc(px, py, 1.35 * scH * (1 - u * 0.5), 0.0, Math.PI * 2); ctx.fill()
        }
    }
    gwiazdaOsmioramienna(cx, yg - 46 * scH, 11 * scH, 5.5 * scH, "#ffec8a", 0.9 + 0.16 * Math.sin(t * 4))
}

/** Sylwester: dwa stukające się kieliszki z bąbelkami i błysk gwiazdy przy stuknięciu (weather-effects.js 3757–3781). */
private fun RamaSceny.kieliszkiSzampana(g: RatuszSezonowyGeo, cx: Double) = with(g) {
    // Dwa stukające się kryształowe kieliszki z musującymi bąbelkami!
    val clink = Math.sin(t * 2.8) * 1.6
    for (side in listOf(-1, 1)) {
        val gxb = cx + side * (11.5 - Math.max(0.0, clink) * 0.5) * scH
        val gyb = yg - 31 * scH
        val gxt = cx + side * (4.2 - Math.max(0.0, clink) * 0.9) * scH
        val gyt = yg - 52 * scH
        ctx.strokeStyle = "rgba(255,252,240,0.92)"
        ctx.lineWidth = Math.max(1.0, 1.15 * scH)
        ctx.beginPath(); ctx.moveTo(gxb + side * 2.2 * scH, gyb + 5.5 * scH); ctx.lineTo(gxb, gyb); ctx.stroke()
        ctx.fillStyle = "rgba(245,202,78,0.90)"
        ctx.beginPath()
        ctx.moveTo(gxb - 2.2 * scH, gyb); ctx.lineTo(gxt - 3.5 * scH, gyt + 5 * scH)
        ctx.lineTo(gxt + 3.5 * scH, gyt + 5 * scH); ctx.lineTo(gxb + 2.2 * scH, gyb)
        ctx.closePath(); ctx.fill()
        ctx.strokeRect(gxt - 3.6 * scH, gyt, 7.2 * scH, 5 * scH)
        for (b in 0 until 3) {
            val bu = (t * 1.4 + b * 0.33 + (if (side > 0) 0.2 else 0.0)) % 1.0
            ctx.fillStyle = Plotno.rgba(255, 252, 220, alfaToFixed2(1 - bu))
            ctx.beginPath(); ctx.arc(gxt, gyb - (3 + bu * 16) * scH, 1.0 * scH, 0.0, Math.PI * 2); ctx.fill()
        }
    }
    if (clink > 0.6) gwiazdaOsmioramienna(cx, yg - 53.5 * scH, 8 * scH, 4 * scH, "#fff4b0", clink)
}

/**
 * Napis roku na cokole (weather-effects.js 3783–3793): w styczniu bieżący rok, w pozostałych miesiącach następny.
 * `new Date()` strony zastępuje [RamaSceny.teraz] (getMonth() = monthValue − 1, getFullYear() = year).
 */
private fun RamaSceny.napisRoku(g: RatuszSezonowyGeo, cx: Double) = with(g) {
    ctx.save()
    // Rok Sylwestra / Nowego Roku: w styczniu bieżący, w pozostałych miesiącach następny (wcześniej na sztywno 2027).
    val nowy = if (teraz.monthValue - 1 == 0) teraz.year else teraz.year + 1
    // W Sylwestra oba lata, „2026/2027” (Karol 24.11: „Tu rok 2026/2027 bym dał i by go było widać”); w Nowy Rok sam nowy.
    val sylwester = sceneType == "leszno-sylwester"
    val nyLabel = if (sylwester) "${nowy - 1}/$nowy" else nowy.toString()
    val rozmiar = if (sylwester) Math.max(11L, Math.round(12 * scH)) else Math.max(16L, Math.round(23 * scH))
    ctx.font = "800 ${rozmiar}px 'Segoe UI',system-ui,sans-serif"
    ctx.textAlign = "center"
    ctx.textBaseline = "alphabetic"
    ctx.fillStyle = "#6c440c"
    ctx.fillText(nyLabel, cx + 1.8 * scH, yg - 13.5 * scH + 1.8 * scH)
    ctx.fillStyle = if (isNight) "#fff6c6" else "#f5cc44"
    ctx.fillText(nyLabel, cx, yg - 13.5 * scH)
    ctx.restore()
}

/**
 * 6. Opadające płatki śniegu (na Nowy Rok co drugi, złote konfetti), bez Sylwestra (weather-effects.js 3796–3810).
 * W trybie karty część płatków omija pole temperatury.
 */
private fun RamaSceny.platkiSniegu(g: RatuszSezonowyGeo) = with(g) {
    val flakeCount = if (w < 560) 56 else 84
    for (i in 0 until flakeCount) {
        if (sceneType == "leszno-nowy-rok" && i % 2 == 0) continue
        val r = (0.9 + (i % 4) * 0.5) * scH
        val vy = (14 + (i % 5) * 6) * scH
        val vx = (-6 + (i % 7) * 2) * scH
        val fy = ((i * 43 * scH) + t * vy) % yg
        // na stronie (… + w*4) % w: po kilku minutach płatki lecące w lewo dają ujemne x i znikają; tu reszta zawsze dodatnia
        val fx = ((((i * 61).toDouble() % w) + t * vx + Math.sin(t * 1.6 + i * 0.7) * 5 * scH + w * 4) % w + w) % w
        if (sidebarMode && fx > w * 0.18 && fx < w * 0.50 && fy > h * 0.13 && fy < h * 0.34 && i % 2 == 0) continue
        ctx.fillStyle = if (sceneType == "leszno-nowy-rok") "rgba(255,232,140,0.82)" else "rgba(255,255,255,0.84)"
        ctx.beginPath(); ctx.arc(fx, fy, r, 0.0, Math.PI * 2); ctx.fill()
    }
}
