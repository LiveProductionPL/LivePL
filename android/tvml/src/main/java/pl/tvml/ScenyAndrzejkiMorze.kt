package pl.tvml

import java.time.ZonedDateTime

// Sceny „leszno-andrzejki” (noc wróżb) oraz „beach” i „ocean” ze strony tvml.pl, przeniesione linia w linię
// z public/assets/js/weather-effects.js (silnik V1) i weather-effects-v2.js (silnik V2, który działa na stronie).
// Rysują przez [Plotno] (nakładka z API CanvasRenderingContext2D), pod nazwami z JS udostępnionymi przez [RamaSceny].

// ═════════════════════════════════════════ ANDRZEJKI ═════════════════════════════════════════

/**
 * Andrzejki — noc wróżb (weather-effects.js 2823–3045). Niebo i wspólne gwiazdy rysuje wcześniej [ScenyStrony.rysuj];
 * ta scena nie ma przyciemnienia dołu. Przy „Usuń animacje” (`!ruch`) klatka stoi na t = 2.2, jak na stronie.
 */
internal fun RamaSceny.andrzejki() {
    // ══ 10b. ANDRZEJKI — NOC WRÓŻB (leszno-andrzejki): fioletowe niebo, księżyc, świeca, klucz i lanie wosku ══
    // Animacje (płomień, migotanie gwiazd, spadająca kropla) zatrzymują się w trybie WCAG
    // (html.wcag-stop-animations) i przy prefers-reduced-motion — wtedy rysujemy nieruchomą klatkę.
    val reduceMotion = !ruch
    val ta = if (reduceMotion) 2.2 else t
    val scH = h / 276
    // Na stronie: !!(this._sidebarMode && this._cardData); widżet strony głównej (tryb karty) ma zawsze dane karty.
    val isCard = sidebarMode
    // Linia „podłogi” pierwszego planu: w karcie bocznej tuż nad kartą daty (jak w scenach Ratusza).
    // W widoku z paskiem daty HTML (bez trybu karty) pasek zakrywa dolne ~20% — stawiamy obiekty tuż nad nim.
    // karta na Starcie: obiekty nad plakietką z datą, jak w scenach Ratusza (yg = h·0,79, tak samo na iOS)
    val yg = if (kartaStartu) h * 0.79 else if (isCard) (h * 0.80 + (h * 0.20) * 0.28 - 2 * scH) else (h * 0.755)

    andrzejkiTlo(reduceMotion, ta, scH, isCard, yg)

    // Kolumny obiektów: w karcie bocznej w przerwach między kolumnami prognozy
    // (karta: 3 kolumny prognozy na w/6, w/2, 5w/6; widok HTML: zwykle 4 kolumny na w/8 … 7w/8)
    val xKey = if (isCard) w / 3 else w * 0.25
    val xCandle = if (isCard) (w * 2) / 3 else w * 0.75
    val xShoe = if (isCard) Math.max(14 * scH, w * 0.045) else w * 0.5

    // ── Świeca z poświatą i drgającym płomieniem ──
    andrzejkiSwieca(reduceMotion, ta, scH, yg, xCandle)

    // ── Stary klucz (oczko u góry) — przez oczko kapie wosk ──
    val keyR = 7 * scH
    val keyBowX = xKey - 4 * scH
    val keyBowY = yg - 38 * scH
    andrzejkiKlucz(scH, keyR, keyBowX, keyBowY)

    // ── Kropla wosku spadająca przez oczko klucza ──
    andrzejkiKropla(reduceMotion, ta, scH, yg, keyBowX, keyBowY)

    // ── Zastygły kształt wosku (cień wróżby) pod kluczem ──
    andrzejkiWosk(scH, yg, keyBowX)

    // ── But (wróżba z butami ustawianymi do progu) ──
    andrzejkiBut(scH, yg, xShoe)
}

/**
 * Andrzejki, tło (weather-effects.js 2840–2903): domknięcie pod niebem, mgiełka ametystu, 34 gwiazdy, sierp księżyca,
 * wzgórza, ziemia pierwszego planu i poduszki czytelności pod kolumnami karty.
 */
private fun RamaSceny.andrzejkiTlo(reduceMotion: Boolean, ta: Double, scH: Double, isCard: Boolean, yg: Double) {
    // Domknięcie tła pod niebem (bez tego półprzezroczysta mgiełka kumulowałaby się między klatkami)
    ctx.fillStyle = if (isNight) "#3d1a5c" else "#8a5fb8"
    ctx.fillRect(0.0, skyBottomY, w, h - skyBottomY)

    // Mgiełka ametystu przy horyzoncie
    val haze = ctx.createLinearGradient(0.0, h * 0.45, 0.0, h * 0.70)
    haze.addColorStop(0.0, "rgba(124,58,237,0)")
    haze.addColorStop(1.0, if (isNight) "rgba(124,58,237,0.22)" else "rgba(196,181,253,0.22)")
    ctx.fillStyle = haze
    ctx.fillRect(0.0, h * 0.45, w, h * 0.25)

    // Gwiazdy (zawsze — to „noc wróżb”; w dzień przygaszone)
    ctx.save()
    for (i in 0 until 34) {
        val sx = ((i * 73 + 11) % 100) / 100.0 * w
        val sy = ((i * 37 + 5) % 100) / 100.0 * (h * 0.52)
        val tw = if (reduceMotion) 0.75 else (0.25 + 0.7 * Math.abs(Math.sin(ta * (0.9 + (i % 5) * 0.27) + i * 1.7)))
        ctx.fillStyle = Plotno.rgba(255, 248, 235, (if (isNight) 1.0 else 0.45) * tw)
        ctx.beginPath()
        ctx.arc(sx, sy, if (i % 4 == 0) 1.5 * Math.max(0.8, scH) else 0.9 * Math.max(0.8, scH), 0.0, Math.PI * 2)
        ctx.fill()
    }
    ctx.restore()

    // Sierp księżyca (prawy górny róg, pod napisem „więcej →”)
    run {
        val mx = w * 0.865
        val my = h * 0.25
        val mr = 15 * scH
        val mg = ctx.createRadialGradient(mx, my, mr * 0.4, mx, my, mr * 2.6)
        mg.addColorStop(0.0, "rgba(245,217,138,0.30)")
        mg.addColorStop(1.0, "rgba(245,217,138,0)")
        ctx.fillStyle = mg
        ctx.beginPath(); ctx.arc(mx, my, mr * 2.6, 0.0, Math.PI * 2); ctx.fill()
        // Sierp = tarcza minus przesunięte koło (przycięcie evenodd: prostokąt bez koła cienia)
        ctx.save()
        ctx.beginPath()
        ctx.rect(mx - mr * 2, my - mr * 2, mr * 4, mr * 4)
        ctx.arc(mx + mr * 0.55, my - mr * 0.35, mr * 0.86, 0.0, Math.PI * 2)
        ctx.clip("evenodd")
        ctx.beginPath()
        ctx.arc(mx, my, mr, 0.0, Math.PI * 2)
        ctx.fillStyle = "#f5d98a"
        ctx.fill()
        ctx.restore()
    }

    // Wzgórza / ciemna łąka pod pierwszym planem
    drawHill(h * 0.70, 6.0, 0.011, 4.0, 0.024, if (isNight) "#1f0f3d" else "#2a1650", 0.6)
    drawHill(yg - 6 * scH, 4.0, 0.016, 3.0, 0.031, if (isNight) "#140a2a" else "#1d0f3a", 2.1)
    ctx.fillStyle = if (isNight) "#0f0820" else "#170c30"
    ctx.fillRect(0.0, yg, w, h - yg)

    // Poduszki czytelności pod 3 kolumnami statystyk/prognozy (przed obiektami pierwszego planu).
    // W aplikacji wyłączone: leżą pod kolumnami karty strony, a karta aplikacji ma inny układ napisów (prognoza u góry).
    if (isCard && PODUSZKI_KARTY_STRONY) {
        val scPill = Math.min(w / 380, h / 240)
        val pTop = h * 0.60 - Math.max(28.0, Math.round(34 * scPill).toDouble()) - 4 * scH
        val pBot = yg - 2 * scH
        if (pBot > pTop + 20) {
            listOf(w / 6, w * 0.5, (w * 5) / 6).forEach { cxCol ->
                ctx.fillStyle = "rgba(11,7,24,0.34)"
                rrPath(cxCol - 35 * scPill, pTop, 70 * scPill, pBot - pTop, 11 * scPill)
                ctx.fill()
            }
        }
    }
}

/** Andrzejki, świeca z poświatą i drgającym płomieniem (weather-effects.js 2911–2961). */
private fun RamaSceny.andrzejkiSwieca(reduceMotion: Boolean, ta: Double, scH: Double, yg: Double, xCandle: Double) {
    val cw = 12 * scH
    val ch = 40 * scH
    val cx0 = xCandle - cw / 2
    val cyTop = yg - ch
    val flick = if (reduceMotion) 1.0 else (1 + 0.06 * Math.sin(ta * 9.3) + 0.04 * Math.sin(ta * 15.1))
    val glowR = 42 * scH * flick
    val gl = ctx.createRadialGradient(xCandle, cyTop - 8 * scH, 2.0, xCandle, cyTop - 8 * scH, glowR)
    gl.addColorStop(0.0, if (isNight) "rgba(245,185,66,0.55)" else "rgba(245,185,66,0.38)")
    gl.addColorStop(0.5, "rgba(245,185,66,0.16)")
    gl.addColorStop(1.0, "rgba(245,185,66,0)")
    ctx.fillStyle = gl
    ctx.beginPath(); ctx.arc(xCandle, cyTop - 8 * scH, glowR, 0.0, Math.PI * 2); ctx.fill()
    // Spodek
    ctx.fillStyle = "#b7791f"
    ctx.beginPath(); ctx.ellipse(xCandle, yg - 1 * scH, cw * 1.05, 3.2 * scH, 0.0, 0.0, Math.PI * 2); ctx.fill()
    // Wosk
    val wg = ctx.createLinearGradient(cx0, 0.0, cx0 + cw, 0.0)
    wg.addColorStop(0.0, "#e7d3a8"); wg.addColorStop(0.45, "#f8ecd0"); wg.addColorStop(1.0, "#d9c08a")
    ctx.fillStyle = wg
    rrPath(cx0, cyTop, cw, ch - 1 * scH, 2.5 * scH)
    ctx.fill()
    // Zaciek wosku
    ctx.strokeStyle = "#e7d3a8"
    ctx.lineWidth = Math.max(1.2, 2.2 * scH)
    ctx.lineCap = "round"
    ctx.beginPath()
    ctx.moveTo(cx0 + 1.6 * scH, cyTop + 3 * scH)
    ctx.quadraticCurveTo(cx0 + 4.2 * scH, cyTop + 9 * scH, cx0 + 1.8 * scH, cyTop + 14 * scH)
    ctx.stroke()
    // Knot
    ctx.strokeStyle = "#2a1300"
    ctx.lineWidth = Math.max(0.8, 1.1 * scH)
    ctx.beginPath(); ctx.moveTo(xCandle, cyTop); ctx.lineTo(xCandle, cyTop - 4 * scH); ctx.stroke()
    // Płomień
    val sway = if (reduceMotion) 0.0 else Math.sin(ta * 6.1) * 1.4 * scH
    val fh = 16 * scH * (if (reduceMotion) 1.0 else (1 + 0.08 * Math.sin(ta * 11.7)))
    val fw = 5.2 * scH
    val fBase = cyTop - 2.5 * scH
    ctx.fillStyle = "#f5b942"
    ctx.beginPath()
    ctx.moveTo(xCandle, fBase)
    ctx.bezierCurveTo(xCandle - fw, fBase - fh * 0.25, xCandle - fw * 0.6 + sway, fBase - fh * 0.7, xCandle + sway, fBase - fh)
    ctx.bezierCurveTo(xCandle + fw * 0.6 + sway, fBase - fh * 0.7, xCandle + fw, fBase - fh * 0.25, xCandle, fBase)
    ctx.fill()
    ctx.fillStyle = "#fff4cf"
    ctx.beginPath()
    ctx.moveTo(xCandle, fBase - 0.5 * scH)
    ctx.bezierCurveTo(xCandle - fw * 0.45, fBase - fh * 0.25, xCandle - fw * 0.3 + sway * 0.6, fBase - fh * 0.55, xCandle + sway * 0.6, fBase - fh * 0.66)
    ctx.bezierCurveTo(xCandle + fw * 0.3 + sway * 0.6, fBase - fh * 0.55, xCandle + fw * 0.45, fBase - fh * 0.25, xCandle, fBase - 0.5 * scH)
    ctx.fill()
}

/** Andrzejki, stary klucz z oczkiem u góry (weather-effects.js 2963–2984). */
private fun RamaSceny.andrzejkiKlucz(scH: Double, keyR: Double, keyBowX: Double, keyBowY: Double) {
    ctx.save()
    ctx.translate(keyBowX, keyBowY)
    // trzon rysowany w stronę -x, więc obrót o ~-120° kieruje go w dół i w prawo
    ctx.rotate(-2.05)
    ctx.strokeStyle = "#d6b25e"
    ctx.fillStyle = "#d6b25e"
    ctx.lineWidth = Math.max(1.6, 3.2 * scH)
    ctx.beginPath(); ctx.arc(0.0, 0.0, keyR, 0.0, Math.PI * 2); ctx.stroke()
    // Trzon klucza w lewo (z dala od środkowej kolumny prognozy)
    rrPath(-keyR - 21 * scH, -1.8 * scH, 21 * scH, 3.6 * scH, 1.2 * scH)
    ctx.fill()
    // Pióro (zęby) na końcu trzonu
    ctx.fillRect(-keyR - 21 * scH, 1.8 * scH, 3.6 * scH, 6 * scH)
    ctx.fillRect(-keyR - 15.5 * scH, 1.8 * scH, 3 * scH, 4.4 * scH)
    // Ozdobny pierścień przy oczku
    ctx.fillRect(-keyR - 3.4 * scH, -3 * scH, 2.4 * scH, 6 * scH)
    ctx.restore()
}

/** Andrzejki, kropla wosku spadająca przez oczko klucza, okres 3,4 s, widoczna przez 72 % (weather-effects.js 2986–3004). */
private fun RamaSceny.andrzejkiKropla(reduceMotion: Boolean, ta: Double, scH: Double, yg: Double, keyBowX: Double, keyBowY: Double) {
    val period = 3.4
    val ph = if (reduceMotion) -1.0 else ((ta % period) / period)
    val yStart = keyBowY - 22 * scH
    val yEnd = yg - 4 * scH
    if (ph >= 0.0 && ph < 0.72) {
        val p = ph / 0.72
        val dy = yStart + (yEnd - yStart) * (p * p)
        val alpha = if (p < 0.08) p / 0.08 else (if (p > 0.92) (1 - p) / 0.08 else 1.0)
        ctx.fillStyle = Plotno.rgba(242, 227, 189, Math.max(0.0, Math.min(1.0, alpha)))
        val dr = 2.6 * scH
        ctx.beginPath()
        ctx.moveTo(keyBowX, dy - dr * 1.9)
        ctx.quadraticCurveTo(keyBowX + dr * 1.05, dy - dr * 0.2, keyBowX, dy + dr)
        ctx.quadraticCurveTo(keyBowX - dr * 1.05, dy - dr * 0.2, keyBowX, dy - dr * 1.9)
        ctx.fill()
    }
}

/** Andrzejki, zastygły kształt wosku (cień wróżby) pod kluczem (weather-effects.js 3006–3021). */
private fun RamaSceny.andrzejkiWosk(scH: Double, yg: Double, keyBowX: Double) {
    val wx = keyBowX
    val wy = yg - 3 * scH
    val s = scH * 0.8
    ctx.fillStyle = "rgba(167,139,250,0.35)"
    ctx.beginPath(); ctx.ellipse(wx + 1 * s, wy + 3 * s, 15 * s, 3.2 * s, 0.0, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#f2e3bd"
    ctx.beginPath()
    ctx.moveTo(wx - 12 * s, wy + 1 * s)
    ctx.bezierCurveTo(wx - 13 * s, wy - 6 * s, wx - 6 * s, wy - 9 * s, wx - 3 * s, wy - 5 * s)
    ctx.bezierCurveTo(wx - 1 * s, wy - 12 * s, wx + 7 * s, wy - 11 * s, wx + 6 * s, wy - 4 * s)
    ctx.bezierCurveTo(wx + 12 * s, wy - 6 * s, wx + 15 * s, wy + 0 * s, wx + 10 * s, wy + 2.5 * s)
    ctx.bezierCurveTo(wx + 4 * s, wy + 4 * s, wx - 6 * s, wy + 4 * s, wx - 12 * s, wy + 1 * s)
    ctx.fill()
    ctx.fillStyle = "rgba(255,255,255,0.55)"
    ctx.beginPath(); ctx.ellipse(wx - 1 * s, wy - 6 * s, 2.2 * s, 1.3 * s, -0.4, 0.0, Math.PI * 2); ctx.fill()
}

/** Andrzejki, but (wróżba z butami ustawianymi do progu) (weather-effects.js 3023–3044). */
private fun RamaSceny.andrzejkiBut(scH: Double, yg: Double, xShoe: Double) {
    val s = scH
    val bx = xShoe
    val by = yg - 1 * s
    ctx.fillStyle = "rgba(0,0,0,0.25)"
    ctx.beginPath(); ctx.ellipse(bx + 3 * s, by + 1.2 * s, 13 * s, 2.2 * s, 0.0, 0.0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = "#7c3aed"
    ctx.beginPath()
    ctx.moveTo(bx - 8 * s, by)
    ctx.lineTo(bx - 8 * s, by - 17 * s)
    ctx.lineTo(bx - 1 * s, by - 17 * s)
    ctx.lineTo(bx + 0 * s, by - 8 * s)
    ctx.quadraticCurveTo(bx + 12 * s, by - 7 * s, bx + 14 * s, by - 2.5 * s)
    ctx.lineTo(bx + 14 * s, by)
    ctx.closePath()
    ctx.fill()
    ctx.fillStyle = "#5b21b6"
    ctx.fillRect(bx - 8 * s, by - 2.4 * s, 22 * s, 2.4 * s)
    ctx.strokeStyle = "#c4b5fd"
    ctx.lineWidth = Math.max(0.8, 1.2 * s)
    ctx.beginPath(); ctx.moveTo(bx - 8 * s, by - 15 * s); ctx.lineTo(bx - 1.2 * s, by - 15 * s); ctx.stroke()
    ctx.beginPath(); ctx.moveTo(bx - 4 * s, by - 12 * s); ctx.lineTo(bx - 0.6 * s, by - 10.5 * s); ctx.stroke()
}

// ═════════════════════════════════════════ PLAŻA I OCEAN ═════════════════════════════════════════

/**
 * Plaża i ocean (weather-effects-v2.js 1143–1351, część od 1192 — silnik V2, który działa na stronie; to samo co
 * weather-effects.js 989–1212, część od 1026, poza niebem). Rysuje cały obraz: niebo V2 (pora roku i pora dnia), poświatę horyzontu, morze, odbicie
 * (tylko poza trybem karty), 5 warstw fal, a dalej głębię oceanu albo piasek plaży. Bez wspólnego nieba i przyciemnienia.
 */
internal fun RamaSceny.plazaIMorze() {
    // Płótno bez wymiarów: pętle z krokiem w * 0,12 nigdy by się nie skończyły (strona też by tu utknęła).
    if (w <= 0.0 || h <= 0.0) return
    val isOcean = sceneType == "ocean"

    val waveSpeed = activeCfg.waveSpeed ?: 1.0
    val waveIntensity = activeCfg.waveIntensity ?: 1.0
    val biolum = activeCfg.beachBiolum == true

    // Strefy wysokości (ocean: więcej wody, brak piasku)
    val seaY = if (isOcean) h * 0.52 else h * 0.60  // granica niebo↔morze
    val beachY = h * 0.80  // granica morze↔plaża (tylko dla beach)

    // ====  NIEBO  ====
    // V2: gradient pory roku i pory dnia (V1 brał tu skyColor i isNight, ale na stronie działa V2)
    val skyGrad = getSkyGradient(ctx, seaY, getSeason(teraz), getTimeOfDay(teraz), state)
    ctx.fillStyle = skyGrad
    ctx.fillRect(0.0, 0.0, w, seaY)

    // ====  POSWATA HORYZONTU  ====
    val horizGrad = ctx.createLinearGradient(0.0, seaY - 24, 0.0, seaY + 12)
    if (isNight) {
        horizGrad.addColorStop(0.0, "rgba(30,60,120,0)")
        horizGrad.addColorStop(1.0, "rgba(10,20,50,0.55)")
    } else {
        horizGrad.addColorStop(0.0, "rgba(255,190,80,0)")
        horizGrad.addColorStop(1.0, "rgba(255,230,140,0.3)")
    }
    ctx.fillStyle = horizGrad
    ctx.fillRect(0.0, seaY - 24, w, 36.0)

    // ====  MORZE (tło)  ====
    val waterColor = activeCfg.waterColor ?: (if (isNight) "#0a1828" else "#006994")
    val seaGrad = ctx.createLinearGradient(0.0, seaY, 0.0, beachY)
    if (isNight) {
        seaGrad.addColorStop(0.0, "#0a1828")
        seaGrad.addColorStop(0.6, "#0d1e32")
        seaGrad.addColorStop(1.0, "#122438")
    } else {
        seaGrad.addColorStop(0.0, waterColor)
        seaGrad.addColorStop(0.45, "#0090b8")
        seaGrad.addColorStop(1.0, "#00b8d4")
    }
    ctx.fillStyle = seaGrad
    ctx.fillRect(0.0, seaY, w, beachY - seaY + 2)

    // ====  ODBICIE (reflekcja słońca lub księżyca na wodzie)  ====
    if (!sidebarMode) plazaOdbicie(seaY, beachY, waveSpeed, waveIntensity)

    // ====  FALE  ====
    // (na stronie jest tu jeszcze nieużywane: const oceanSeaBottom = isOcean ? h : beachY;)
    drawBeachWaves(
        ctx, w, h, seaY, if (isOcean) h * 0.88 else beachY, t, waveSpeed,
        if (isOcean) waveIntensity * 1.4 else waveIntensity, isNight, biolum,
    )

    if (isOcean) {
        oceanGlebia(biolum)
    } else {
        plazaPiasek(beachY)
    }
}

/** weather-effects-v2.js 31–38 (getSeason): pora roku z miesiąca (bez seasonOverride, który ustawia tylko panel). */
private fun getSeason(teraz: ZonedDateTime): String {
    val month = teraz.monthValue - 1 // 0-11
    if (month >= 2 && month <= 4) return "spring"
    if (month >= 5 && month <= 7) return "summer"
    if (month >= 8 && month <= 10) return "autumn"
    return "winter"
}

/** weather-effects-v2.js 40–47 (getTimeOfDay): pora dnia z godziny (bez timeOfDayOverride, który ustawia tylko panel). */
private fun getTimeOfDay(teraz: ZonedDateTime): String {
    val hour = teraz.hour
    if (hour >= 5 && hour < 8) return "dawn"
    if (hour >= 8 && hour < 17) return "day"
    if (hour >= 17 && hour < 20) return "dusk"
    return "night"
}

/** weather-effects-v2.js 49–98 (getSkyGradient): gradient nieba V2 od 0 do [height] według pory dnia, pogody i pory roku. */
private fun getSkyGradient(ctx: Plotno, height: Double, season: String, timeOfDay: String, state: String): Plotno.Gradient {
    val skyGrad = ctx.createLinearGradient(0.0, 0.0, 0.0, height)
    if (timeOfDay == "night") {
        skyGrad.addColorStop(0.0, "#020617")
        skyGrad.addColorStop(0.7, "#0f172a")
        skyGrad.addColorStop(1.0, "#1e293b")
    } else if (timeOfDay == "dawn") {
        skyGrad.addColorStop(0.0, "#311042")
        skyGrad.addColorStop(0.5, "#70224d")
        skyGrad.addColorStop(0.8, "#d35c5c")
        skyGrad.addColorStop(1.0, "#ffc078")
    } else if (timeOfDay == "dusk") {
        skyGrad.addColorStop(0.0, "#0c1b33")
        skyGrad.addColorStop(0.4, "#241b35")
        skyGrad.addColorStop(0.7, "#5e354a")
        skyGrad.addColorStop(1.0, "#9e5a42")
    } else {
        val isStormy = state.contains("stormy") || state == "rainy-heavy"
        val isRainy = state.startsWith("rainy") || state == "cloudy"
        val isFoggy = state.contains("foggy")

        if (isStormy) {
            skyGrad.addColorStop(0.0, "#1c1c2e")
            skyGrad.addColorStop(0.6, "#2e3a50")
            skyGrad.addColorStop(1.0, "#4a5568")
        } else if (isRainy) {
            skyGrad.addColorStop(0.0, "#3a4a5e")
            skyGrad.addColorStop(0.6, "#5a7080")
            skyGrad.addColorStop(1.0, "#8aabb0")
        } else if (isFoggy) {
            skyGrad.addColorStop(0.0, "#a8b8c8")
            skyGrad.addColorStop(1.0, "#dde8f0")
        } else {
            if (season == "winter") {
                skyGrad.addColorStop(0.0, "#b4c6d6")
                skyGrad.addColorStop(1.0, "#e3eef5")
            } else if (season == "autumn") {
                skyGrad.addColorStop(0.0, "#5b768f")
                skyGrad.addColorStop(1.0, "#a6b8c7")
            } else if (season == "spring") {
                skyGrad.addColorStop(0.0, "#53a8e2")
                skyGrad.addColorStop(1.0, "#cbe3f5")
            } else {
                skyGrad.addColorStop(0.0, "#00b4db")
                skyGrad.addColorStop(1.0, "#0083b0")
            }
        }
    }
    return skyGrad
}

/**
 * Odbicie słońca lub księżyca na wodzie (weather-effects-v2.js 1233–1259). Na stronie tylko poza trybem karty
 * (`!this._sidebarMode`), więc w aplikacji, która zawsze rysuje kartę, się nie pokazuje.
 */
private fun RamaSceny.plazaOdbicie(seaY: Double, beachY: Double, waveSpeed: Double, waveIntensity: Double) {
    val refX = if (isNight) w * 0.78 else w * 0.13
    val refW = w * 0.055
    val refGrad = ctx.createLinearGradient(0.0, seaY, 0.0, beachY)
    if (isNight) {
        refGrad.addColorStop(0.0, "rgba(170,200,255,0.30)")
        refGrad.addColorStop(0.7, "rgba(170,200,255,0.08)")
        refGrad.addColorStop(1.0, "rgba(170,200,255,0)")
    } else {
        refGrad.addColorStop(0.0, "rgba(255,240,100,0.40)")
        refGrad.addColorStop(0.6, "rgba(255,240,100,0.12)")
        refGrad.addColorStop(1.0, "rgba(255,240,100,0)")
    }
    ctx.fillStyle = refGrad
    ctx.beginPath()
    ctx.moveTo(refX - refW, seaY)
    var xi = 0.0
    while (xi <= w * 0.12) {
        val rx = refX - refW + xi
        val ry = seaY + Math.sin(rx * 0.06 + t * waveSpeed * 2.2) * 2.5 * waveIntensity
        ctx.lineTo(rx, ry)
        xi += 2.0
    }
    ctx.lineTo(refX + refW * 0.5, beachY - 4)
    ctx.lineTo(refX - refW * 0.5, beachY - 4)
    ctx.closePath()
    ctx.fill()
}

/** Głębia oceanu zamiast plaży, kaustyki w dzień, bioluminescencja w nocy (weather-effects-v2.js 1265–1302). */
private fun RamaSceny.oceanGlebia(biolum: Boolean) {
    // ====  GŁĘBIA OCEANU (dolna cześć zamiast plaży)  ====
    val deepGrad = ctx.createLinearGradient(0.0, h * 0.82, 0.0, h)
    if (isNight) {
        deepGrad.addColorStop(0.0, "#040d1a")
        deepGrad.addColorStop(0.5, "#020810")
        deepGrad.addColorStop(1.0, "#010508")
    } else {
        deepGrad.addColorStop(0.0, "#004d6e")
        deepGrad.addColorStop(0.5, "#003850")
        deepGrad.addColorStop(1.0, "#002030")
    }
    ctx.fillStyle = deepGrad
    ctx.fillRect(0.0, h * 0.82, w, h * 0.18)

    // Efekt podwodnego światła / kaustyki (prostokl. plamki światła)
    if (!isNight) {
        var cx2 = w * 0.08
        while (cx2 < w * 0.92) {
            val cy2 = h * 0.86 + Math.sin(t * 0.8 + cx2 * 0.01) * h * 0.03
            val lr = w * 0.03 + Math.sin(t * 1.1 + cx2) * w * 0.01
            val lGrad = ctx.createRadialGradient(cx2, cy2, 0.0, cx2, cy2, lr)
            lGrad.addColorStop(0.0, "rgba(0,200,220,0.18)")
            lGrad.addColorStop(1.0, "rgba(0,200,220,0)")
            ctx.fillStyle = lGrad
            ctx.beginPath(); ctx.ellipse(cx2, cy2, lr, lr * 0.4, 0.0, 0.0, Math.PI * 2); ctx.fill()
            cx2 += w * 0.12
        }
    } else if (biolum) {
        // Bioluminescencja — rozbłyski w głębinie
        var bx2 = w * 0.05
        while (bx2 < w * 0.95) {
            val by2 = h * 0.88 + Math.random() * h * 0.10
            val ba2 = 0.06 + Math.sin(t * 2.0 + bx2 * 0.05) * 0.04
            if (ba2 > 0.0) {
                ctx.fillStyle = Plotno.rgba(0, 200, 255, ba2)
                ctx.beginPath(); ctx.arc(bx2, by2, 3.0, 0.0, Math.PI * 2); ctx.fill()
            }
            bx2 += w * 0.08
        }
    }
}

/** Ziarno piasku plaży (pole obiektu w this._sandGrain na stronie). */
private class ZiarnoPiaskuPlazy(val x: Double, val y: Double, val r: Double, val o: Double)

/** Ziarna piasku z szerokością, dla której je wylosowano (this._sandGrain i this._sandGrainW na stronie). */
private class PiasekPlazyPamiec(val szerokosc: Long, val ziarna: List<ZiarnoPiaskuPlazy>)

/** Piasek plaży, mokry pas przy wodzie i losowe ziarna (weather-effects-v2.js 1303–1349). */
private fun RamaSceny.plazaPiasek(beachY: Double) {
    // ====  PLAŻA (tylko scena beach)  ====
    val beachColor = activeCfg.beachColor ?: (if (isNight) "#2c2416" else "#f4d03f")
    val beachGrad = ctx.createLinearGradient(0.0, beachY, 0.0, h)
    if (isNight) {
        beachGrad.addColorStop(0.0, "#1e1808")
        beachGrad.addColorStop(0.4, beachColor)
        beachGrad.addColorStop(1.0, "#3a3020")
    } else {
        beachGrad.addColorStop(0.0, beachColor)
        beachGrad.addColorStop(0.5, "#e8c87c")
        beachGrad.addColorStop(1.0, "#d4a96a")
    }
    ctx.fillStyle = beachGrad
    ctx.fillRect(0.0, beachY, w, h - beachY)

    // Mokry piasek przy linii wody
    val wetGrad = ctx.createLinearGradient(0.0, beachY - 6, 0.0, beachY + 14)
    if (isNight) {
        wetGrad.addColorStop(0.0, "rgba(10,18,35,0.7)")
        wetGrad.addColorStop(1.0, "rgba(10,18,35,0)")
    } else {
        wetGrad.addColorStop(0.0, "rgba(0,80,130,0.5)")
        wetGrad.addColorStop(1.0, "rgba(0,80,130,0)")
    }
    ctx.fillStyle = wetGrad
    ctx.fillRect(0.0, beachY - 6, w, 20.0)

    // Ziarnistość piasku (losowe plamki)
    val stary = pamiec["sandGrain"] as? PiasekPlazyPamiec
    val piasek = if (stary == null || stary.szerokosc != Math.round(w)) {
        val sandH = h - beachY
        val ziarna = ArrayList<ZiarnoPiaskuPlazy>()
        repeat(Math.floor(w * 0.6).toInt()) {
            ziarna.add(
                ZiarnoPiaskuPlazy(
                    x = Math.random() * w,
                    y = beachY + Math.random() * sandH,
                    r = Math.random() * 1.4 + 0.4,
                    o = Math.random() * 0.14 + 0.04,
                ),
            )
        }
        PiasekPlazyPamiec(Math.round(w), ziarna).also { pamiec["sandGrain"] = it }
    } else {
        stary
    }
    piasek.ziarna.forEach { g ->
        ctx.fillStyle = if (isNight) Plotno.rgba(255, 230, 160, g.o * 0.5) else Plotno.rgba(120, 80, 20, g.o)
        ctx.beginPath()
        ctx.arc(g.x, g.y, g.r, 0.0, Math.PI * 2)
        ctx.fill()
    }
}

/**
 * Rysuje warstwy animowanych fal morskich (weather-effects-v2.js 1355–1425, _drawBeachWaves; to samo co
 * weather-effects.js 1216–1288): 5 warstw z wypełnieniem, pianą na grzbiecie i bąbelkami przy ostatniej fali.
 */
private fun drawBeachWaves(
    ctx: Plotno, w: Double, h: Double, seaY: Double, beachY: Double, t: Double,
    speed: Double, intensity: Double, isNight: Boolean, biolum: Boolean,
) {
    val wCount = 5
    for (wi in 0 until wCount) {
        val prog = wi.toDouble() / (wCount - 1)
        val waveY = seaY + (beachY - seaY - 6) * prog
        val amp = (2.5 + wi * 1.8) * intensity
        val freq = 0.020 - wi * 0.0018
        val phase = t * speed * (0.55 + wi * 0.14)
        val alpha = 0.10 + prog * 0.18

        // Kolor fali
        val r: Int
        val g: Int
        val b: Int
        if (isNight) { r = 15 + wi * 7; g = 35 + wi * 10; b = 75 + wi * 18 } else { r = 0; g = 115 + wi * 18; b = 180 + wi * 14 }

        // Wypełnienie fali (gradient)
        ctx.beginPath()
        ctx.moveTo(0.0, h)
        var x = 0.0
        while (x <= w) {
            val y = waveY +
                Math.sin(x * freq + phase) * amp +
                Math.sin(x * freq * 1.65 + phase * 1.4) * amp * 0.38 +
                Math.sin(x * freq * 2.8 + phase * 0.9) * amp * 0.18
            ctx.lineTo(x, y)
            x += 3.0
        }
        ctx.lineTo(w, h); ctx.closePath()
        val wGrad = ctx.createLinearGradient(0.0, waveY - amp * 1.5, 0.0, beachY + 10)
        wGrad.addColorStop(0.0, Plotno.rgba(r, g, b, alpha + 0.12))
        wGrad.addColorStop(1.0, Plotno.rgba(r, g, b, 0.0))
        ctx.fillStyle = wGrad
        ctx.fill()

        // Grzbiet fali (biała piana)
        ctx.beginPath()
        x = 0.0
        while (x <= w) {
            val y = waveY +
                Math.sin(x * freq + phase) * amp +
                Math.sin(x * freq * 1.65 + phase * 1.4) * amp * 0.38 +
                Math.sin(x * freq * 2.8 + phase * 0.9) * amp * 0.18
            if (x == 0.0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
            x += 3.0
        }
        ctx.lineWidth = if (wi == wCount - 1) 2.2 else 1.2
        val foamAlpha = 0.18 + prog * 0.30
        if (isNight && biolum && wi >= wCount - 2) {
            // Efekt bioluminescencji: niebiesko-zielona świecąca piana
            ctx.shadowColor = "rgba(0, 200, 255, 0.9)"
            ctx.shadowBlur = 10.0
            ctx.strokeStyle = Plotno.rgba(80, 220, 255, foamAlpha + 0.15)
        } else {
            ctx.shadowBlur = 0.0
            ctx.strokeStyle = Plotno.rgba(255, 255, 255, foamAlpha)
        }
        ctx.stroke()
        ctx.shadowBlur = 0.0

        // Małe bąbelki/krople przy ostatniej fali
        if (wi == wCount - 1) {
            val step = Math.max(1.0, Math.floor(w / 18))
            var bx = 0.0
            while (bx < w) {
                val by = beachY +
                    Math.sin(bx * freq * 1.2 + phase * 1.1) * amp * 0.5 -
                    3
                val ba = 0.12 + Math.random() * 0.15
                ctx.fillStyle = if (biolum && isNight) Plotno.rgba(80, 220, 255, ba) else Plotno.rgba(255, 255, 255, ba)
                ctx.beginPath()
                ctx.arc(bx + Math.sin(t * 1.5 + bx) * 4, by, 2.2, 0.0, Math.PI * 2)
                ctx.fill()
                bx += step
            }
        }
    }
}
