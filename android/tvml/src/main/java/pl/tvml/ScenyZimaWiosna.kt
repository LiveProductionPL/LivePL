package pl.tvml

// Sceny zimowe, sylwestrowa i wiosenna ze strony tvml.pl (weather-effects.js, gałęzie _drawCustomScene)
// przeniesione linia w linię na [Plotno]. Niebo, gwiazdy i przyciemnienie dołu rysuje ScenyStrony.rysuj.

/** Śnieżne szczyty i zorza polarna (alpine-peaks), weather-effects.js 1817–1900. */
internal fun RamaSceny.alpinePeaks() {
    // Zorza polarna (Aurora Borealis) w nocy lub o zmierzchu
    if (isNight) {
        ctx.save()
        for (band in 0 until 2) {
            ctx.beginPath()
            val baseY = horizonY * (0.28 + band * 0.16)
            ctx.moveTo(0.0, baseY + 35)
            var x = 0.0
            while (x <= w) {
                val ay = baseY + Math.sin(x * 0.015 + t * 0.7 + band * 2.0) * 14 + Math.cos(x * 0.032 - t * 0.5) * 7
                ctx.lineTo(x, ay)
                x += 6.0
            }
            ctx.lineTo(w, baseY + 45)
            ctx.lineTo(0.0, baseY + 45)
            ctx.closePath()
            val aurGrad = ctx.createLinearGradient(0.0, baseY - 20, 0.0, baseY + 45)
            if (band == 0) {
                aurGrad.addColorStop(0.0, "rgba(30, 235, 160, 0)")
                aurGrad.addColorStop(0.45, "rgba(30, 235, 160, 0.28)")
                aurGrad.addColorStop(1.0, "rgba(90, 130, 255, 0)")
            } else {
                aurGrad.addColorStop(0.0, "rgba(130, 90, 255, 0)")
                aurGrad.addColorStop(0.45, "rgba(50, 215, 200, 0.22)")
                aurGrad.addColorStop(1.0, "rgba(30, 235, 160, 0)")
            }
            ctx.fillStyle = aurGrad
            ctx.fill()
        }
        ctx.restore()
    }

    // Pasmo górskie ze śnieżnymi czapami
    class Szczyt(val x: Double, val y: Double, val w: Double, val col: String)
    val peaks = listOf(
        Szczyt(w * 0.16, horizonY - 54, w * 0.34, if (isNight) "#1b2c47" else "#536f8a"),
        Szczyt(w * 0.80, horizonY - 48, w * 0.36, if (isNight) "#182740" else "#4a647d"),
        Szczyt(w * 0.48, horizonY - 72, w * 0.42, if (isNight) "#243a5e" else "#6585a3"),
    )
    peaks.forEach { p ->
        ctx.beginPath()
        ctx.moveTo(p.x - p.w, horizonY + 10)
        ctx.lineTo(p.x, p.y)
        ctx.lineTo(p.x + p.w, horizonY + 10)
        ctx.closePath()
        ctx.fillStyle = p.col
        ctx.fill()

        // Ośnieżony wierzchołek
        ctx.beginPath()
        ctx.moveTo(p.x, p.y)
        ctx.lineTo(p.x - p.w * 0.36, p.y + (horizonY + 10 - p.y) * 0.36)
        ctx.lineTo(p.x - p.w * 0.12, p.y + (horizonY + 10 - p.y) * 0.28)
        ctx.lineTo(p.x + p.w * 0.08, p.y + (horizonY + 10 - p.y) * 0.38)
        ctx.lineTo(p.x + p.w * 0.36, p.y + (horizonY + 10 - p.y) * 0.34)
        ctx.closePath()
        ctx.fillStyle = if (isNight) "rgba(215,235,255,0.78)" else "rgba(255,255,255,0.92)"
        ctx.fill()
    }

    // Zamarznięte jezioro alpejskie
    val lakeGrad = ctx.createLinearGradient(0.0, horizonY + 8, 0.0, h)
    if (isNight) {
        lakeGrad.addColorStop(0.0, "#0d1b30")
        lakeGrad.addColorStop(0.4, "#132845")
        lakeGrad.addColorStop(1.0, "#091221")
    } else {
        lakeGrad.addColorStop(0.0, "#3e6280")
        lakeGrad.addColorStop(0.5, "#2b4a66")
        lakeGrad.addColorStop(1.0, "#1c3247")
    }
    ctx.fillStyle = lakeGrad
    ctx.fillRect(0.0, horizonY + 8, w, h - (horizonY + 8))

    // Poziome refleksy lodu i zorzy na wodzie
    for (i in 0 until 16) {
        val ry = horizonY + 14 + i * ((foreY - horizonY) / 16.0)
        val rw = 35 + Math.sin(i * 2.3 + t) * 20
        val rx = w * 0.5 + Math.sin(t * 0.9 + i) * (w * 0.18)
        ctx.fillStyle = if (isNight) "rgba(90, 235, 190, 0.12)" else "rgba(255, 255, 255, 0.18)"
        ctx.fillRect(rx - rw * 0.5, ry, rw, 1.8)
    }
}

/** Świąteczna zima i choinka (winter-christmas), weather-effects.js 1902–2010. */
internal fun RamaSceny.winterChristmas() {
    // Ośnieżone wzgórza w tle
    drawHill(horizonY - 6, 14.0, 0.012, 6.0, 0.025, if (isNight) "#182744" else "#7f9eb8", 0.8)
    drawHill(horizonY + 18, 11.0, 0.016, 5.0, 0.032, if (isNight) "#213559" else "#9ebad1", 2.1)
    drawHill(horizonY + 46, 9.0, 0.015, 4.0, 0.03, if (isNight) "#1b2c4a" else "#8aa8c2", 1.2)

    // Ciepła świąteczna łuna za choinką
    val treeX = w * 0.80
    val treeBaseY = foreY - 2
    val aura = ctx.createRadialGradient(treeX, treeBaseY - 38, 4.0, treeX, treeBaseY - 38, 65.0)
    aura.addColorStop(0.0, "rgba(255, 210, 90, 0.35)")
    aura.addColorStop(0.5, "rgba(255, 140, 50, 0.14)")
    aura.addColorStop(1.0, "rgba(255, 140, 50, 0)")
    ctx.fillStyle = aura
    ctx.beginPath()
    ctx.arc(treeX, treeBaseY - 38, 65.0, 0.0, Math.PI * 2)
    ctx.fill()

    // Duża świąteczna choinka po prawej stronie (tak aby nie zasłaniała temperatury po lewej)
    ctx.save()
    ctx.translate(treeX, treeBaseY)
    // Pień
    ctx.fillStyle = "#3b2316"
    ctx.fillRect(-5.0, -8.0, 10.0, 10.0)

    // 4 piętra gałęzi świerkowych
    for (l in 0 until 4) {
        val ly = -8.0 - l * 16
        val lw = 30.0 - l * 6
        ctx.fillStyle = if (isNight) "#0f3325" else "#1b4d38"
        ctx.beginPath()
        ctx.moveTo(0.0, ly - 24)
        ctx.lineTo(-lw, ly)
        ctx.lineTo(lw, ly)
        ctx.closePath()
        ctx.fill()

        // Śnieg na końcówkach gałęzi
        ctx.fillStyle = "rgba(240,248,255,0.75)"
        ctx.beginPath()
        ctx.moveTo(-lw, ly)
        ctx.lineTo(-lw * 0.55, ly - 5)
        ctx.lineTo(0.0, ly - 2)
        ctx.lineTo(lw * 0.55, ly - 5)
        ctx.lineTo(lw, ly)
        ctx.closePath()
        ctx.fill()
    }

    // Złota gwiazda betlejemska na czubku choinki
    val starY = -80.0
    val starPulse = 0.85 + 0.15 * Math.sin(t * 4)
    ctx.shadowColor = "rgba(255, 225, 80, 0.95)"
    ctx.shadowBlur = 14 * starPulse
    ctx.fillStyle = "#ffe066"
    ctx.beginPath()
    for (s in 0 until 10) {
        val r = if (s % 2 == 0) 8.0 else 3.5
        val a = (s * Math.PI) / 5 - Math.PI / 2
        val sx = Math.cos(a) * r
        val sy = starY + Math.sin(a) * r
        if (s == 0) ctx.moveTo(sx, sy) else ctx.lineTo(sx, sy)
    }
    ctx.closePath()
    ctx.fill()

    // Migoczące kolorowe lampki choinkowe
    val bulbColors = listOf("#ff4d4d", "#ffd11a", "#33ccff", "#ff66b3", "#66ff66", "#ffffff")
    val bulbs = listOf(
        -16.0 to -14.0, -4.0 to -16.0, 10.0 to -13.0,
        -12.0 to -28.0, 2.0 to -30.0, 14.0 to -27.0,
        -9.0 to -44.0, 4.0 to -45.0, -3.0 to -58.0, 5.0 to -60.0,
    )
    bulbs.forEachIndexed { bi, b ->
        val tw = 0.45 + 0.55 * Math.abs(Math.sin(t * 3.5 + bi * 1.3))
        val col = bulbColors[bi % bulbColors.size]
        ctx.shadowColor = col
        ctx.shadowBlur = 8 * tw
        ctx.fillStyle = col
        ctx.beginPath()
        ctx.arc(b.first, b.second, 2.3, 0.0, Math.PI * 2)
        ctx.fill()
    }
    ctx.shadowBlur = 0.0

    // Prezenty pod choinką
    ctx.fillStyle = "#d92626"
    ctx.fillRect(-16.0, -4.0, 9.0, 7.0)
    ctx.fillStyle = "#ffd11a"
    ctx.fillRect(-12.5, -4.0, 2.0, 7.0)
    ctx.fillStyle = "#2563eb"
    ctx.fillRect(7.0, -3.0, 8.0, 6.0)
    ctx.fillStyle = "#f8fafc"
    ctx.fillRect(10.0, -3.0, 2.0, 6.0)
    ctx.restore()

    // Świąteczna girlanda świetlna wzdłuż horyzontu po lewej
    var gx = 16.0
    while (gx < w * 0.62) {
        val gy = horizonY + 34 + Math.sin(gx * 0.08) * 4
        val tw = 0.5 + 0.5 * Math.sin(t * 2.8 + gx)
        ctx.fillStyle = Plotno.rgba(255, 215, 110, 0.4 + 0.5 * tw)
        ctx.beginPath()
        ctx.arc(gx, gy, 2.0, 0.0, Math.PI * 2)
        ctx.fill()
        gx += 18.0
    }

    drawHill(foreY + 4, 7.0, 0.014, 3.0, 0.03, if (isNight) "#142038" else "#7896b0", 0.5)
}

/** Sylwester / Nowy Rok nad miastem z fajerwerkami (new-year), weather-effects.js 2012–2118. */
internal fun RamaSceny.newYear() {
    // Łuna miasta nad horyzontem
    val cityGlow = ctx.createLinearGradient(0.0, horizonY - 45, 0.0, horizonY + 10)
    cityGlow.addColorStop(0.0, "rgba(255,160,60,0)")
    cityGlow.addColorStop(1.0, "rgba(255,175,75,0.28)")
    ctx.fillStyle = cityGlow
    ctx.fillRect(0.0, horizonY - 45, w, 55.0)

    // Sylwetka nowoczesnego miasta (wieżowce i kamienice z oświetlonymi oknami)
    val bCount = Math.max(12, Math.floor(w / 22).toInt())
    val bWidth = w / bCount
    for (i in 0 until bCount) {
        val bx = i * bWidth
        val bh = 18 + ((i * 37 + 13) % 36)
        val bY = horizonY - bh + 6 // w JS „by”
        ctx.fillStyle = if (i % 2 == 0) "#0c1021" else "#121830"
        ctx.fillRect(bx, bY, bWidth + 1, bh.toDouble())

        // Świecące okna w budynkach
        ctx.fillStyle = "rgba(255, 220, 130, 0.65)"
        var wy = bY + 4
        while (wy < horizonY - 2) {
            var wx = bx + 3
            while (wx < bx + bWidth - 4) {
                // JS: reszta z dzielenia liczb zmiennoprzecinkowych (wx, wy nie są całkowite), porównanie z zerem
                if (((wx * 13 + wy * 7 + i) % 5) != 0.0) {
                    ctx.fillRect(wx, wy, 2.2, 3.0)
                }
                wx += 5.0
            }
            wy += 6.0
        }
    }

    // Rzeka / zatoka odbijająca światła miasta i fajerwerki
    val waterGrad = ctx.createLinearGradient(0.0, horizonY + 6, 0.0, h)
    waterGrad.addColorStop(0.0, "#10152e")
    waterGrad.addColorStop(0.5, "#0b0f24")
    waterGrad.addColorStop(1.0, "#060814")
    ctx.fillStyle = waterGrad
    ctx.fillRect(0.0, horizonY + 6, w, h - (horizonY + 6))

    // Animowane fajerwerki (4 niezależne rozbłyski w pętli czasowej)
    val fwPalettes = listOf(
        listOf("#ffd700", "#ffae00", "#ffffff"),
        listOf("#ff3366", "#ff80bf", "#ffd700"),
        listOf("#00e5ff", "#80ffff", "#ffffff"),
        listOf("#a855f7", "#fb7185", "#fde047"),
    )
    for (f in 0 until 4) {
        val cycle = 2.6
        val localT = (t + f * 0.65) % cycle
        val fx = w * (0.20 + f * 0.21 + Math.sin(f * 3.1) * 0.04)
        val fy = horizonY * (0.24 + (f % 2) * 0.16)
        val pal = fwPalettes[f % fwPalettes.size]

        if (localT < 0.55) {
            // Faza wznoszenia rakiety
            val p = localT / 0.55
            val ry = (horizonY - 5) - p * (horizonY - 5 - fy)
            ctx.fillStyle = "#ffe066"
            ctx.beginPath()
            ctx.arc(fx, ry, 2.0, 0.0, Math.PI * 2)
            ctx.fill()
            // Smuga iskier za rakietą
            val trailGrad = ctx.createLinearGradient(fx, ry, fx, ry + 22)
            trailGrad.addColorStop(0.0, "rgba(255,220,100,0.8)")
            trailGrad.addColorStop(1.0, "rgba(255,160,50,0)")
            ctx.strokeStyle = trailGrad
            ctx.lineWidth = 1.6
            ctx.beginPath()
            ctx.moveTo(fx, ry)
            ctx.lineTo(fx, Math.min(horizonY, ry + 22))
            ctx.stroke()
        } else {
            // Faza rozbłysku (eksplozja cząsteczek)
            val p = (localT - 0.55) / (cycle - 0.55)
            val ease = 1 - Math.pow(1 - p, 2.5)
            val maxR = 32.0 + (f % 2) * 12
            val alpha = Math.max(0.0, 1 - p * 1.1)
            val rays = 18

            ctx.save()
            for (r in 0 until rays) {
                val angle = (r.toDouble() / rays) * Math.PI * 2 + f * 0.4
                val dist = ease * maxR * (0.85 + (r % 3) * 0.12)
                val gravity = p * p * 16
                val px = fx + Math.cos(angle) * dist
                val py = fy + Math.sin(angle) * dist + gravity
                val col = pal[r % pal.size]

                ctx.globalAlpha = alpha
                ctx.fillStyle = col
                ctx.shadowColor = col
                ctx.shadowBlur = 6.0
                ctx.beginPath()
                ctx.arc(px, py, 1.8 * (1 - p * 0.5), 0.0, Math.PI * 2)
                ctx.fill()
            }
            // Odbicie rozbłysku fajerwerku w wodzie
            val refY = horizonY + 18 + f * 12
            ctx.globalAlpha = alpha * 0.25
            ctx.fillStyle = pal[0]
            ctx.shadowBlur = 0.0
            ctx.beginPath()
            ctx.ellipse(fx, refY, maxR * ease * 0.65, 6.0, 0.0, 0.0, Math.PI * 2)
            ctx.fill()
            ctx.restore()
        }
    }
}

/** Wielkanoc / wiosenna łąka (easter-spring), weather-effects.js 2120–2234. */
internal fun RamaSceny.easterSpring() {
    // Zielone, słoneczne wiosenne wzgórza
    drawHill(horizonY - 8, 14.0, 0.012, 6.0, 0.025, if (isNight) "#152e26" else "#52b788", 0.6)
    drawHill(horizonY + 18, 12.0, 0.016, 5.0, 0.032, if (isNight) "#11241d" else "#40916c", 1.9)
    drawHill(horizonY + 46, 9.0, 0.015, 4.0, 0.03, if (isNight) "#0d1c16" else "#2d6a4f", 3.2)

    // Kwitnące na różowo drzewa wiosenne (jabłonie / wiśnie) po bokach
    class DrzewoWiosenne(val x: Double, val y: Double, val r: Double)
    val springTrees = listOf(
        DrzewoWiosenne(w * 0.14, horizonY + 24, 18.0),
        DrzewoWiosenne(w * 0.86, horizonY + 20, 20.0),
    )
    springTrees.forEachIndexed { idx, st ->
        val sway = Math.sin(t * 1.2 + idx) * 2
        ctx.strokeStyle = if (isNight) "#1f1410" else "#4a2e21"
        ctx.lineWidth = 3.0
        ctx.beginPath()
        ctx.moveTo(st.x, st.y + st.r + 12)
        ctx.lineTo(st.x + sway * 0.4, st.y)
        ctx.stroke()

        ctx.fillStyle = if (isNight) "#592e46" else "#ff9ebb"
        ctx.beginPath()
        ctx.arc(st.x + sway, st.y, st.r, 0.0, Math.PI * 2)
        ctx.arc(st.x - st.r * 0.55 + sway, st.y + 4, st.r * 0.75, 0.0, Math.PI * 2)
        ctx.arc(st.x + st.r * 0.55 + sway, st.y + 4, st.r * 0.75, 0.0, Math.PI * 2)
        ctx.fill()

        ctx.fillStyle = if (isNight) "#7a4462" else "#ffc2d4"
        ctx.beginPath()
        ctx.arc(st.x + sway - 3, st.y - 3, st.r * 0.6, 0.0, Math.PI * 2)
        ctx.fill()
    }

    // Kolorowe wiosenne kwiaty (tulipany i żonkile) oraz pisanki wielkanocne w trawie
    val flowerColors = listOf("#ffd166", "#ef476f", "#ffffff", "#ff9f1c", "#c77dff")
    val fCount = Math.max(12, Math.floor(w / 22).toInt())
    for (i in 0 until fCount) {
        val fx = ((i + 0.5) / fCount) * w + Math.sin(i * 4.1) * 6
        val fy = horizonY + 52 + (i % 3) * 14
        val sway = Math.sin(t * 1.8 + i) * 1.8

        // Łodyżka
        ctx.strokeStyle = if (isNight) "#1b4332" else "#52b788"
        ctx.lineWidth = 1.4
        ctx.beginPath()
        ctx.moveTo(fx, fy + 8)
        ctx.lineTo(fx + sway, fy)
        ctx.stroke()

        // Kielich kwiatu
        ctx.fillStyle = flowerColors[i % flowerColors.size]
        ctx.beginPath()
        ctx.arc(fx + sway, fy, 3.2, 0.0, Math.PI * 2)
        ctx.fill()
    }

    // Dekoracyjne pisanki wielkanocne ukryte w trawie
    val eggColors = listOf(
        "#ff6b6b" to "#ffd166", // base to stripe
        "#4cc9f0" to "#ffffff",
        "#ffd166" to "#06d6a0",
        "#b5179e" to "#f72585",
    )
    val eggPositions = listOf(0.22, 0.48, 0.76)
    eggPositions.forEachIndexed { ei, ep ->
        val ex = w * ep
        val ey = foreY - 6 + (ei % 2) * 5
        val ec = eggColors[ei % eggColors.size]
        ctx.save()
        ctx.translate(ex, ey)
        ctx.rotate((ei - 1) * 0.18)
        ctx.fillStyle = ec.first
        ctx.beginPath()
        ctx.ellipse(0.0, 0.0, 5.5, 7.5, 0.0, 0.0, Math.PI * 2)
        ctx.fill()
        ctx.strokeStyle = ec.second
        ctx.lineWidth = 1.8
        ctx.beginPath()
        ctx.moveTo(-5.0, 0.0)
        ctx.lineTo(5.0, 0.0)
        ctx.stroke()
        ctx.restore()
    }

    // Sylwetka wielkanocnego zajączka na łące po prawej stronie
    val bx = w * 0.64
    val bY = horizonY + 46 // w JS „by”
    ctx.save()
    ctx.translate(bx, bY)
    ctx.fillStyle = if (isNight) "#2b211b" else "#f8f9fa"
    // Tułów i głowa
    ctx.beginPath()
    ctx.ellipse(0.0, 0.0, 6.5, 8.5, 0.0, 0.0, Math.PI * 2)
    ctx.arc(0.0, -10.0, 5.0, 0.0, Math.PI * 2)
    ctx.fill()
    // Długie uszy zajączka
    ctx.beginPath()
    ctx.ellipse(-2.5, -18.0, 1.8, 6.0, -0.15, 0.0, Math.PI * 2)
    ctx.ellipse(2.5, -18.0, 1.8, 6.0, 0.15, 0.0, Math.PI * 2)
    ctx.fill()
    ctx.restore()

    // Unoszące się wiosenne pyłki / motylki w słońcu
    for (b in 0 until 6) {
        val mx = ((b * 67 + t * 14) % w)
        val my = horizonY * 0.45 + Math.sin(t * 2.2 + b * 1.7) * 18 + b * 8
        ctx.fillStyle = if (isNight) "rgba(180,255,160,0.65)" else "rgba(255,245,150,0.85)"
        ctx.beginPath()
        ctx.arc(mx, my, 2.0, 0.0, Math.PI * 2)
        ctx.fill()
    }

    drawHill(foreY + 4, 7.0, 0.014, 3.0, 0.03, if (isNight) "#08130e" else "#1b4332", 1.1)
}
