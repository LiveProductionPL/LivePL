package pl.tvml

import kotlin.math.sin

/**
 * Panorama Leszna jak na stronie (weather-effects.js, `sceneType === 'leszno-panorama'`, domyślna scena na co dzień):
 * siatka 358 × 130 przyklejona do dołu (miasto najwyżej 45 % wysokości), kamienice powtórzone po bokach, Ratusz
 * z wieżą i zegarem, okna zapalone wieczorem, pas ziemi, drzewa z pniem (wiosna/lato zielone, jesień rude) albo
 * zimą choinki i śnieg na attyce. Niebo, gwiazdy i przyciemnienie dołu rysuje [ScenyStrony.rysuj], pogodę
 * (mgła, chmury, opady, słońce) nakładka [NakladkaPogody], tak jak pogoda-efekty.js na stronie.
 */
internal fun RamaSceny.lesznoPanorama() {
    val mon = teraz.monthValue
    val zima = mon == 12 || mon <= 2
    val wiosnaLato = mon in 3..8
    val hr = teraz.hour
    val swiatla = isNight || hr >= 16 || hr < 7
    // Skala: do szerokości, ale miasto zajmuje najwyżej ok. 45 % wysokości
    val s = minOf(w / 358, (h * 0.45) / 130)
    val left = (w - 358 * s) / 2
    val top = h - 130 * s
    fun X(x: Double) = left + x * s
    fun Y(y: Double) = top + y * s
    val miasto = if (isNight) "#0a1226" else "#1c2b45"
    val ratusz = if (isNight) "#101a33" else "#223456"
    val ziemia = if (zima) (if (isNight) "#c9d6e4" else "#eef3f8") else (if (isNight) "#0b1020" else "#2a2620")

    // Słaba łuna nad dachami (w dzień ciepła, w nocy chłodna)
    val glow = ctx.createLinearGradient(0.0, Y(40.0), 0.0, Y(108.0))
    glow.addColorStop(0.0, "rgba(255,200,120,0)")
    glow.addColorStop(1.0, if (isNight) "rgba(90,120,200,0.18)" else "rgba(255,214,150,0.32)")
    ctx.fillStyle = glow
    ctx.fillRect(0.0, Y(40.0), w, Y(108.0) - Y(40.0))

    ctx.save()
    // Kamienice (płaskie i ze szczytami); na szerokim ekranie powtórzone po bokach, żeby nie było pustki
    val domyPlaskie = listOf(doubleArrayOf(0.0, 82.0, 46.0), doubleArrayOf(50.0, 76.0, 24.0), doubleArrayOf(106.0, 80.0, 26.0), doubleArrayOf(296.0, 84.0, 26.0))
    val domySzczyt = listOf(doubleArrayOf(78.0, 86.0, 24.0), doubleArrayOf(240.0, 78.0, 50.0), doubleArrayOf(326.0, 74.0, 32.0))
    fun rysujDomy(dx: Double, alpha: Double) {
        ctx.globalAlpha = alpha
        ctx.fillStyle = miasto
        ctx.beginPath()
        domyPlaskie.forEach { d -> ctx.rect(X(d[0] + dx), Y(d[1]), d[2] * s, (108 - d[1]) * s) }
        domySzczyt.forEach { d ->
            ctx.rect(X(d[0] + dx), Y(d[1]), d[2] * s, (108 - d[1]) * s)
            ctx.moveTo(X(d[0] + dx), Y(d[1])); ctx.lineTo(X(d[0] + dx + d[2] / 2), Y(d[1] - 12)); ctx.lineTo(X(d[0] + dx + d[2]), Y(d[1])); ctx.closePath()
        }
        ctx.fill()
        ctx.globalAlpha = 1.0
    }
    var k = 1
    while (left - (k - 1) * 358 * s > 0 && k < 8) {
        rysujDomy(-358.0 * k, 0.75)
        rysujDomy(358.0 * k, 0.75)
        k++
    }
    rysujDomy(0.0, 1.0)

    // Ratusz: korpus, attyka, wieża z hełmem i iglicą, zegar
    ctx.fillStyle = ratusz
    ctx.beginPath()
    ctx.rect(X(138.0), Y(70.0), 84 * s, 38 * s)
    ctx.moveTo(X(142.0), Y(70.0)); ctx.lineTo(X(146.0), Y(62.0)); ctx.lineTo(X(214.0), Y(62.0)); ctx.lineTo(X(218.0), Y(70.0)); ctx.closePath()
    ctx.rect(X(178.0), Y(34.0), 16 * s, 28 * s)
    ctx.moveTo(X(174.0), Y(36.0)); ctx.quadraticCurveTo(X(176.0), Y(26.0), X(186.0), Y(20.0)); ctx.quadraticCurveTo(X(196.0), Y(26.0), X(198.0), Y(36.0)); ctx.closePath()
    ctx.rect(X(185.0), Y(6.0), 2 * s, 14 * s)
    ctx.fill()
    ctx.fillStyle = if (swiatla) "#fde68a" else "#e5e7eb"
    ctx.beginPath(); ctx.arc(X(186.0), Y(45.0), 4.5 * s, 0.0, Math.PI * 2); ctx.fill()

    // Okna zapalone wieczorem i w nocy (lekkie migotanie)
    if (swiatla) {
        val okna = listOf(146 to 78, 158 to 78, 170 to 78, 196 to 78, 208 to 78, 220 to 78, 146 to 92, 158 to 92, 208 to 92, 220 to 92, 56 to 84, 64 to 84, 112 to 88, 122 to 88, 246 to 86, 302 to 90, 334 to 84)
        okna.forEachIndexed { i, (ox, oy) ->
            ctx.globalAlpha = 0.82 + 0.13 * sin(t * 0.7 + i * 1.7)
            ctx.fillStyle = "#fcd34d"
            ctx.fillRect(X(ox.toDouble()), Y(oy.toDouble()), 5 * s, 7 * s)
        }
        ctx.globalAlpha = 1.0
    }

    // Ziemia (pas pod miastem)
    ctx.fillStyle = ziemia
    ctx.fillRect(0.0, Y(108.0), w, h - Y(108.0))

    if (zima) {
        // Choinki ze śniegiem i śnieg na attyce Ratusza
        listOf(22 to 84, 118 to 86, 254 to 84, 332 to 84).forEach { (cx, cy) ->
            val x = cx.toDouble()
            val y = cy.toDouble()
            ctx.fillStyle = "#1f4f43"
            ctx.beginPath(); ctx.moveTo(X(x), Y(y)); ctx.lineTo(X(x + 12), Y(y + 24)); ctx.lineTo(X(x - 12), Y(y + 24)); ctx.closePath(); ctx.fill()
            ctx.fillStyle = "#f1f6fb"
            ctx.beginPath(); ctx.moveTo(X(x), Y(y)); ctx.lineTo(X(x + 5), Y(y + 10)); ctx.lineTo(X(x - 5), Y(y + 10)); ctx.closePath(); ctx.fill()
        }
        ctx.fillStyle = "#f1f6fb"
        ctx.fillRect(X(138.0), Y(68.0), 84 * s, 4 * s)
    } else {
        // Drzewa z pniem i koroną z trzech kół (wiosna/lato zielone, jesień rude), lekko kołyszące się na wietrze
        val barwy = if (wiosnaLato) listOf("#166534", "#15803d", "#14532d") else listOf("#9a3412", "#a16207", "#7c2d12")
        val wiatr = if (state == "windy" || state == "stormy") 1.6 else 0.6
        listOf(22, 44, 118, 252, 286, 330).forEachIndexed { i, xi ->
            val x = xi.toDouble()
            val wys = if (i % 2 == 0) 14.0 else 10.0
            val rr = if (i % 2 == 0) 5.0 else 4.0
            ctx.fillStyle = miasto
            ctx.fillRect(X(x - 1), Y(108 - wys), 2 * s, wys * s)
            val sway = sin(t * 1.3 + i) * wiatr * 0.4
            val cy = 108 - wys - rr * 0.4
            ctx.globalAlpha = if (isNight) 0.75 else 0.95
            ctx.fillStyle = barwy[i % barwy.size]
            ctx.beginPath()
            listOf(-rr * 0.6 to 0.0, rr * 0.6 to 0.0, 0.0 to -rr * 0.8).forEach { (ddx, ddy) ->
                ctx.moveTo(X(x + ddx + sway) + rr * s, Y(cy + ddy))
                ctx.arc(X(x + ddx + sway), Y(cy + ddy), rr * s, 0.0, Math.PI * 2)
            }
            ctx.fill()
            ctx.globalAlpha = 1.0
        }
    }
    ctx.restore()
}

/**
 * Tło karty pogody pod płótnem (portal.css: .weather-card.color-sky-day-N / -night-N, gradient 160°), widoczne
 * na stronie między niebem (do 58 % wysokości) a dachami panoramy. Klasa jak getSkyClass w weather-effects-v2.js:
 * skyClass z panelu dla stanu, a bez niego według stanu pogody.
 */
internal fun RamaSceny.tloKarty() {
    val klasa = activeCfg.skyClass?.takeIf { it.startsWith("color-sky-") } ?: run {
        val n = when (state) {
            "sunny", "clear-night", "sunny-foggy" -> 1
            "partly-cloudy", "partly-night" -> 2
            "cloudy", "cloudy-night" -> 4
            "foggy" -> if (isNight) 1 else 4
            "night-foggy" -> 1
            "rainy-light" -> 5
            "rainy-moderate" -> 6
            "rainy-heavy", "stormy", "snowy" -> 7
            else -> 0
        }
        "color-sky-" + (if (isNight) "night-" else "day-") + n
    }
    val (a, b) = when (klasa) {
        "color-sky-day-1" -> "#3b82f6" to "#1d4ed8"
        "color-sky-day-2" -> "#60a5fa" to "#2563eb"
        "color-sky-day-3" -> "#94a3b8" to "#475569"
        "color-sky-day-4" -> "#64748b" to "#334155"
        "color-sky-day-5" -> "#8ca6c2" to "#b5c7d8"
        "color-sky-day-6" -> "#6c8096" to "#92a4b8"
        "color-sky-day-7" -> "#4b5a6c" to "#6c7e92"
        "color-sky-night-1" -> "#0f172a" to "#020617"
        "color-sky-night-2" -> "#1e1b4b" to "#090514"
        "color-sky-night-3" -> "#312e81" to "#0c0a0f"
        "color-sky-night-4" -> "#1e293b" to "#0f172a"
        "color-sky-night-5" -> "#111827" to "#030712"
        "color-sky-night-6" -> "#0b0f19" to "#020408"
        "color-sky-night-7" -> "#05070a" to "#000000"
        "color-sky-night-0" -> "#1f2937" to "#111827"
        else -> "#374151" to "#1f2937"
    }
    // CSS linear-gradient(160deg): kierunek 160° od pionu w górę, linia gradientu przez środek, długość jak w CSS
    val kat = Math.toRadians(160.0)
    val dx = Math.sin(kat)
    val dy = -Math.cos(kat)
    val pol = (Math.abs(w * dx) + Math.abs(h * dy)) / 2
    val g = ctx.createLinearGradient(w / 2 - dx * pol, h / 2 - dy * pol, w / 2 + dx * pol, h / 2 + dy * pol)
    g.addColorStop(0.0, a)
    g.addColorStop(1.0, b)
    ctx.fillStyle = g
    ctx.fillRect(0.0, 0.0, w, h)
}
