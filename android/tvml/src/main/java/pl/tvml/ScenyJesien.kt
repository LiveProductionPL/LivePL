package pl.tvml

// Sceny krajobrazowe ze strony tvml.pl (weather-effects.js, gałęzie _drawCustomScene) przeniesione linia w linię
// na nakładkę Plotno: jesienny las, jesienne jezioro, aleja parkowa z latarniami i zimowa osada.
// Niebo, gwiazdy i przyciemnienie dołu rysuje ScenyStrony.rysuj; tu tylko krajobraz danej sceny.

/** Scena `autumn-forest` — jesienny las i mgliste wzgórza (weather-effects.js 1447–1516). */
internal fun RamaSceny.autumnForest() {
    val glowGrad = ctx.createLinearGradient(0.0, horizonY - 40, 0.0, horizonY + 20)
    glowGrad.addColorStop(0.0, "rgba(255,150,50,0)")
    glowGrad.addColorStop(1.0, if (isNight) "rgba(180,85,30,0.28)" else "rgba(255,175,70,0.48)")
    ctx.fillStyle = glowGrad
    ctx.fillRect(0.0, horizonY - 40, w, 60.0)

    // Dalekie wzgórza
    drawHill(horizonY - 12, 16.0, 0.012, 8.0, 0.025, if (isNight) "#1b1d36" else "#5e4663", 0.5)
    // Środkowe wzgórza (rudo-bursztynowe)
    drawHill(horizonY + 14, 14.0, 0.015, 6.0, 0.032, if (isNight) "#2a1c29" else "#8a4829", 1.7)

    // Snująca się mgła w dolinie
    for (m in 0 until 3) {
        val my = horizonY + 10 + m * 22.0
        val mShift = Math.sin(t * 0.4 + m) * 18
        val mGrad = ctx.createLinearGradient(0.0, my - 12, 0.0, my + 16)
        val ma = if (isNight) 0.14 else 0.24
        mGrad.addColorStop(0.0, "rgba(255,235,215,0)")
        mGrad.addColorStop(0.5, Plotno.rgba(255, 235, 215, ma))
        mGrad.addColorStop(1.0, "rgba(255,235,215,0)")
        ctx.fillStyle = mGrad
        ctx.fillRect(mShift - 20, my - 12, w + 40, 28.0)
    }

    // Bliższe wzgórze
    val meadowGrad = ctx.createLinearGradient(0.0, horizonY + 26, 0.0, h)
    meadowGrad.addColorStop(0.0, if (isNight) "#281c2d" else "#7d4627")
    meadowGrad.addColorStop(1.0, if (isNight) "#1c1422" else "#5e3219")
    drawHill(horizonY + 32, 4.0, 0.010, 2.0, 0.022, meadowGrad, 3.1)

    // Jesienne drzewa (korony w odcieniach złota, czerwieni i bursztynu)
    val treeColsDay = listOf("#d9531e", "#e88d14", "#b8291d", "#f2b705", "#9e331e")
    val treeColsNight = listOf("#542317", "#663a14", "#471518", "#6e4f11", "#3b1812")
    val cols = if (isNight) treeColsNight else treeColsDay
    val treeCount = Math.max(10, Math.floor(w / 24).toInt())
    for (i in 0..treeCount) {
        val tx = (i.toDouble() / treeCount) * w + Math.sin(i * 3.7) * 8
        val ty = horizonY + 34 + (i % 3) * 14.0 + Math.sin(i * 1.9) * 5
        val sway = Math.sin(t * 1.4 + i * 0.9) * 2.2
        val r = 11.0 + (i % 4) * 3

        ctx.strokeStyle = if (isNight) "#120b0a" else "#2e1610"
        ctx.lineWidth = 2.2
        ctx.beginPath()
        ctx.moveTo(tx, ty + r + 8)
        ctx.lineTo(tx + sway * 0.5, ty)
        ctx.stroke()

        ctx.fillStyle = cols[i % cols.size]
        ctx.beginPath()
        ctx.arc(tx + sway, ty, r, 0.0, Math.PI * 2)
        ctx.arc(tx - r * 0.45 + sway, ty + 3, r * 0.72, 0.0, Math.PI * 2)
        ctx.arc(tx + r * 0.45 + sway, ty + 3, r * 0.72, 0.0, Math.PI * 2)
        ctx.fill()
    }

    // Pierwszy plan (pod kartą imienin)
    // Dywan opadłych złotych i bursztynowych liści na polanie (bez czarnej fali)
    val leafCount = Math.max(18, Math.floor(w / 16).toInt())
    val leafSpread = Math.max(12, Math.floor(h - horizonY - 46).toInt())
    for (l in 0 until leafCount) {
        val lx = ((l * 47 + 13) % 100) / 100.0 * w
        val ly = horizonY + 44 + ((l * 29) % leafSpread)
        ctx.fillStyle = cols[l % cols.size]
        ctx.globalAlpha = if (isNight) 0.45 else 0.75
        ctx.beginPath()
        ctx.ellipse(lx, ly, 3.2, 1.4, (l % 5) * 0.3, 0.0, Math.PI * 2)
        ctx.fill()
    }
    ctx.globalAlpha = 1.0
}

/** Scena `autumn-lake` — jesienne jezioro i drewniany pomost (weather-effects.js 1518–1621). */
internal fun RamaSceny.autumnLake() {
    // Daleki brzeg z jesiennym lasem
    drawHill(horizonY - 10, 10.0, 0.014, 5.0, 0.03, if (isNight) "#1c1829" else "#63382c", 0.4)
    val treeCols = if (isNight) listOf("#4a2218", "#5c3614", "#3d1618", "#614612")
    else listOf("#d94e1f", "#eb9815", "#b5281c", "#f5b807")
    val fCount = Math.max(14, Math.floor(w / 18).toInt())
    for (i in 0..fCount) {
        val tx = (i.toDouble() / fCount) * w
        val ty = horizonY - 5 + Math.sin(i * 2.1) * 4
        val r = 7.0 + (i % 3) * 2.5
        ctx.fillStyle = treeCols[i % treeCols.size]
        ctx.beginPath()
        ctx.arc(tx, ty, r, 0.0, Math.PI * 2)
        ctx.fill()
    }

    // Tafla jeziora (od horizonY do h)
    val lakeGrad = ctx.createLinearGradient(0.0, horizonY, 0.0, h)
    if (isNight) {
        lakeGrad.addColorStop(0.0, "#181930")
        lakeGrad.addColorStop(0.45, "#111629")
        lakeGrad.addColorStop(1.0, "#090c18")
    } else {
        lakeGrad.addColorStop(0.0, "#a3583b")
        lakeGrad.addColorStop(0.35, "#424866")
        lakeGrad.addColorStop(0.75, "#24314a")
        lakeGrad.addColorStop(1.0, "#162033")
    }
    ctx.fillStyle = lakeGrad
    ctx.fillRect(0.0, horizonY, w, h - horizonY)

    // Złociste refleksy jesiennych drzew w wodzie
    for (i in 0 until 20) {
        val ry = horizonY + 8 + i * ((foreY - horizonY) / 20)
        val rw = 28.0 + (i % 4) * 18
        val rx = ((i * 53).toDouble() % w) + Math.sin(t * 1.4 + i) * 6
        ctx.fillStyle = if (isNight) "rgba(240,170,80,0.09)" else "rgba(255,185,75,0.20)"
        ctx.fillRect(rx - rw * 0.5, ry, rw, 2.0)
    }

    // Snująca się mgiełka nad taflą jeziora
    val mistShift = Math.sin(t * 0.45) * 15
    val mistGrad = ctx.createLinearGradient(0.0, horizonY - 8, 0.0, horizonY + 26)
    mistGrad.addColorStop(0.0, "rgba(255,240,220,0)")
    mistGrad.addColorStop(0.5, if (isNight) "rgba(200,215,240,0.16)" else "rgba(255,240,220,0.32)")
    mistGrad.addColorStop(1.0, "rgba(255,240,220,0)")
    ctx.fillStyle = mistGrad
    ctx.fillRect(mistShift - 20, horizonY - 8, w + 40, 34.0)

    // Drewniany pomost wchodzący z prawej strony do jeziora
    val pierY = foreY - 12
    val pierStartX = w * 0.58
    val pierEndX = w * 1.02
    ctx.strokeStyle = if (isNight) "#1a110d" else "#3d2518"
    ctx.lineWidth = 4.0
    // Słupki pomostu + odbicia
    var px = pierStartX + 6
    while (px < w) {
        ctx.beginPath()
        ctx.moveTo(px, pierY - 6)
        ctx.lineTo(px, pierY + 22)
        ctx.stroke()
        px += 22.0
    }
    // Pokład pomostu
    ctx.fillStyle = if (isNight) "#291a14" else "#573624"
    ctx.fillRect(pierStartX, pierY - 3, pierEndX - pierStartX, 5.0)
    // Latarenka na końcu pomostu
    val lx = pierStartX + 8
    val ly = pierY - 22
    ctx.strokeStyle = if (isNight) "#241712" else "#42291b"
    ctx.lineWidth = 2.2
    ctx.beginPath()
    ctx.moveTo(lx, pierY - 3)
    ctx.lineTo(lx, ly)
    ctx.lineTo(lx - 6, ly)
    ctx.stroke()
    // Światło latarenki nad wodą
    val lGlow = ctx.createRadialGradient(lx - 6, ly + 3, 1.0, lx - 6, ly + 3, 28.0)
    lGlow.addColorStop(0.0, "rgba(255,220,120,0.95)")
    lGlow.addColorStop(0.4, "rgba(255,170,60,0.35)")
    lGlow.addColorStop(1.0, "rgba(255,140,40,0)")
    ctx.fillStyle = lGlow
    ctx.beginPath()
    ctx.arc(lx - 6, ly + 3, 28.0, 0.0, Math.PI * 2)
    ctx.fill()

    // Kołyszące się trzciny (pałki wodne) po lewej stronie
    for (r in 0 until 9) {
        val rx = 12.0 + r * 11
        val ry = foreY + 18
        val sway = Math.sin(t * 1.5 + r * 0.6) * 4
        ctx.strokeStyle = if (isNight) "#141a14" else "#33291a"
        ctx.lineWidth = 1.6
        ctx.beginPath()
        ctx.moveTo(rx, ry)
        ctx.quadraticCurveTo(rx + sway * 0.5, ry - 22, rx + sway, ry - 42 - (r % 3) * 6)
        ctx.stroke()
        if (r % 2 == 0) {
            ctx.fillStyle = if (isNight) "#1f120d" else "#4a2514"
            ctx.beginPath()
            ctx.ellipse(rx + sway * 0.85, ry - 34 - (r % 3) * 6, 2.2, 6.0, sway * 0.03, 0.0, Math.PI * 2)
            ctx.fill()
        }
    }
}

/** Scena `park-lanterns` — aleja parkowa z latarniami (weather-effects.js 1623–1709). */
internal fun RamaSceny.parkLanterns() {
    // Tło parku i korony drzew w oddali
    drawHill(horizonY + 8, 12.0, 0.015, 5.0, 0.03, if (isNight) "#121829" else "#3b3345", 1.1)

    // Mokra aleja / bruk od horizonY w dół
    val roadGrad = ctx.createLinearGradient(0.0, horizonY + 15, 0.0, h)
    roadGrad.addColorStop(0.0, if (isNight) "#0e1320" else "#282633")
    roadGrad.addColorStop(0.5, if (isNight) "#151d2e" else "#333140")
    roadGrad.addColorStop(1.0, if (isNight) "#090d16" else "#1f1d29")
    ctx.fillStyle = roadGrad
    ctx.fillRect(0.0, horizonY + 15, w, h - (horizonY + 15))

    // Sylwetki drzew parkowych po bokach
    val parkTrees = listOf(0.06, 0.24, 0.76, 0.94)
    parkTrees.forEachIndexed { idx, px ->
        val tx = w * px
        val ty = horizonY - 12 + (idx % 2) * 10.0
        val sway = Math.sin(t * 1.1 + idx) * 2
        ctx.strokeStyle = if (isNight) "#0a0e17" else "#241914"
        ctx.lineWidth = 3.5
        ctx.beginPath()
        ctx.moveTo(tx, horizonY + 25)
        ctx.lineTo(tx + sway * 0.4, ty)
        ctx.stroke()

        ctx.fillStyle = if (isNight) "rgba(65,40,30,0.72)" else "rgba(185,95,35,0.82)"
        ctx.beginPath()
        ctx.arc(tx + sway, ty, 22.0, 0.0, Math.PI * 2)
        ctx.arc(tx - 12 + sway, ty + 8, 16.0, 0.0, Math.PI * 2)
        ctx.arc(tx + 12 + sway, ty + 8, 16.0, 0.0, Math.PI * 2)
        ctx.fill()
    }

    // Latarnie parkowe ze stożkami ciepłego światła i odbiciem na mokrym bruku
    class Lampa(val x: Double, val yBase: Double, val h: Double, val scale: Double)
    val lamps = listOf(
        Lampa(x = w * 0.16, yBase = horizonY + 58, h = 66.0, scale = 1.0),
        Lampa(x = w * 0.84, yBase = horizonY + 58, h = 66.0, scale = 1.0),
        Lampa(x = w * 0.33, yBase = horizonY + 28, h = 44.0, scale = 0.68),
        Lampa(x = w * 0.67, yBase = horizonY + 28, h = 44.0, scale = 0.68),
    )

    lamps.forEachIndexed { i, l ->
        val topY = l.yBase - l.h
        val flicker = 0.92 + 0.08 * Math.sin(t * 4.5 + i * 2.1)

        // Stożek światła we mgle
        val coneGrad = ctx.createLinearGradient(l.x, topY, l.x, l.yBase + 18)
        coneGrad.addColorStop(0.0, Plotno.rgba(255, 195, 95, 0.36 * flicker))
        coneGrad.addColorStop(1.0, "rgba(255,170,60,0)")
        ctx.fillStyle = coneGrad
        ctx.beginPath()
        ctx.moveTo(l.x - 4 * l.scale, topY + 4)
        ctx.lineTo(l.x + 4 * l.scale, topY + 4)
        ctx.lineTo(l.x + 30 * l.scale, l.yBase + 18)
        ctx.lineTo(l.x - 30 * l.scale, l.yBase + 18)
        ctx.closePath()
        ctx.fill()

        // Słup latarni
        ctx.strokeStyle = "#141821"
        ctx.lineWidth = 2.6 * l.scale
        ctx.beginPath()
        ctx.moveTo(l.x, l.yBase)
        ctx.lineTo(l.x, topY)
        ctx.stroke()

        // Aureola (Halo) wokół klosza
        val halo = ctx.createRadialGradient(l.x, topY, 1.0, l.x, topY, 24 * l.scale)
        halo.addColorStop(0.0, Plotno.rgba(255, 235, 170, 0.95 * flicker))
        halo.addColorStop(0.4, Plotno.rgba(255, 175, 65, 0.42 * flicker))
        halo.addColorStop(1.0, "rgba(255,140,30,0)")
        ctx.fillStyle = halo
        ctx.beginPath()
        ctx.arc(l.x, topY, 24 * l.scale, 0.0, Math.PI * 2)
        ctx.fill()

        // Odbicie światła na mokrym chodniku
        val refGrad = ctx.createLinearGradient(l.x, l.yBase, l.x, l.yBase + 42 * l.scale)
        refGrad.addColorStop(0.0, Plotno.rgba(255, 190, 80, 0.38 * flicker))
        refGrad.addColorStop(1.0, "rgba(255,160,50,0)")
        ctx.fillStyle = refGrad
        ctx.beginPath()
        ctx.ellipse(l.x, l.yBase + 18 * l.scale, 10 * l.scale, 20 * l.scale, 0.0, 0.0, Math.PI * 2)
        ctx.fill()
    }
}

/** Scena `winter-village` — zimowa osada, chatki ze światełkami (weather-effects.js 1711–1815). */
internal fun RamaSceny.winterVillage() {
    // Dalekie ośnieżone wzgórza
    drawHill(horizonY - 8, 15.0, 0.012, 7.0, 0.025, if (isNight) "#1b2b48" else "#89a7c2", 1.0)
    drawHill(horizonY + 18, 12.0, 0.016, 5.0, 0.035, if (isNight) "#233659" else "#a8c2d8", 2.4)

    // Zaśnieżone sosny/świerki
    fun drawPine(px: Double, py: Double, scale: Double) {
        ctx.save()
        ctx.translate(px, py)
        ctx.scale(scale, scale)
        for (layer in 0 until 3) {
            val ly = -layer * 9.0
            val lw = 14 - layer * 3.2
            ctx.fillStyle = if (isNight) "#122624" else "#2b4d43"
            ctx.beginPath()
            ctx.moveTo(0.0, ly - 14)
            ctx.lineTo(-lw, ly)
            ctx.lineTo(lw, ly)
            ctx.closePath()
            ctx.fill()
            // Czapa śniegu
            ctx.fillStyle = if (isNight) "rgba(210,230,255,0.72)" else "rgba(255,255,255,0.9)"
            ctx.beginPath()
            ctx.moveTo(0.0, ly - 14)
            ctx.lineTo(-lw * 0.65, ly - 5)
            ctx.lineTo(lw * 0.65, ly - 5)
            ctx.closePath()
            ctx.fill()
        }
        ctx.restore()
    }

    val pineCount = Math.max(8, Math.floor(w / 32).toInt())
    for (i in 0 until pineCount) {
        val px = ((i + 0.4) / pineCount) * w
        val py = horizonY + 26 + (i % 3) * 10.0
        drawPine(px, py, 0.75 + (i % 3) * 0.2)
    }

    // Bliższa zaspa śnieżna
    drawHill(horizonY + 48, 9.0, 0.015, 4.0, 0.03, if (isNight) "#2b4066" else "#c6dced", 0.4)

    // Drewniane chatki z ciepłymi oknami i dymem z kominów
    class Chatka(val x: Double, val y: Double, val s: Double)
    val cabins = listOf(
        Chatka(x = w * 0.22, y = horizonY + 46, s = 1.0),
        Chatka(x = w * 0.74, y = horizonY + 50, s = 0.92),
    )
    cabins.forEachIndexed { idx, c ->
        ctx.save()
        ctx.translate(c.x, c.y)
        ctx.scale(c.s, c.s)

        // Unoszący się dym z komina
        for (k in 0 until 5) {
            val prog = ((t * 0.45 + k * 0.2 + idx * 0.5) % 1.0)
            val sx = 9 + Math.sin(prog * Math.PI * 2 + t) * 7
            val sy = -22 - prog * 30
            val sr = 3 + prog * 7
            ctx.fillStyle = Plotno.rgba(220, 230, 245, (1 - prog) * 0.30)
            ctx.beginPath()
            ctx.arc(sx, sy, sr, 0.0, Math.PI * 2)
            ctx.fill()
        }

        // Komin
        ctx.fillStyle = "#3b231e"
        ctx.fillRect(6.0, -22.0, 6.0, 10.0)

        // Ściany chatki
        ctx.fillStyle = if (isNight) "#261715" else "#59372b"
        ctx.fillRect(-16.0, -12.0, 32.0, 20.0)

        // Ośnieżony dach
        ctx.beginPath()
        ctx.moveTo(-21.0, -11.0)
        ctx.lineTo(0.0, -25.0)
        ctx.lineTo(21.0, -11.0)
        ctx.closePath()
        ctx.fillStyle = if (isNight) "#c8ddf5" else "#ffffff"
        ctx.fill()

        // Świecące ciepłe okna + łuna na śniegu
        val glow = 0.85 + 0.15 * Math.sin(t * 3 + idx * 2)
        ctx.shadowColor = "rgba(255, 190, 70, 0.95)"
        ctx.shadowBlur = 12.0
        ctx.fillStyle = Plotno.rgba(255, 215, 110, glow)
        ctx.fillRect(-10.0, -7.0, 7.0, 7.0)
        ctx.fillRect(3.0, -7.0, 7.0, 7.0)
        ctx.shadowBlur = 0.0

        val snowGlow = ctx.createRadialGradient(0.0, 12.0, 2.0, 0.0, 12.0, 22.0)
        snowGlow.addColorStop(0.0, Plotno.rgba(255, 190, 80, 0.32 * glow))
        snowGlow.addColorStop(1.0, "rgba(255,190,80,0)")
        ctx.fillStyle = snowGlow
        ctx.beginPath()
        ctx.ellipse(0.0, 12.0, 22.0, 8.0, 0.0, 0.0, Math.PI * 2)
        ctx.fill()

        ctx.restore()
    }

    // Pierwszy plan zaspy
    drawHill(foreY + 4, 8.0, 0.014, 4.0, 0.03, if (isNight) "#192742" else "#9ab6cc", 2.0)
}
