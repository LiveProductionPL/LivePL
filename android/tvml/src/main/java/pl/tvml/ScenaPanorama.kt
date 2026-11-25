package pl.tvml

/**
 * Panorama Leszna, domyślna scena na co dzień (`leszno-panorama`): od 25.11 prawdziwy ratusz Ferrariego, kamienice
 * wokół Rynku i bruk ze sceny 11 Listopada, w barwach pory roku ([panoramaRatusza]; Karol: „Czy tą grafikę w pogodzie
 * też możesz poprawić na naszą nową z nowym ratuszem ? Bo ja widzę nadal stary”). Dawny zarys ratusza z kółkiem
 * zegara i domkami (siatka 358 × 130 ze strony) zastąpiony, tak samo na iOS. Niebo, gwiazdy i przyciemnienie dołu
 * rysuje [ScenyStrony.rysuj], pogodę (mgła, chmury, opady, słońce) nakładka [NakladkaPogody].
 */
internal fun RamaSceny.lesznoPanorama() {
    val hr = teraz.hour
    // okna świecą wieczorem i nocą, a w Święta i Sylwestra cały dzień (jak na iOS)
    val swiatla = isNight || hr >= 16 || hr < 7 || poraRoku == Motyw.Scena.SWIETA || poraRoku == Motyw.Scena.SYLWESTER
    panoramaRatusza(ziemiaPanoramy(poraRoku, isNight), poraRoku, swiatla)
}

/** Kolor ziemi Panoramy: zimą i w Święta śnieg, poza tym ciemna ziemia. Ten sam ma belka z datą ([PaletaBelki]). */
fun ziemiaPanoramy(pora: Motyw.Scena, noc: Boolean): String =
    if (pora == Motyw.Scena.ZIMA || pora == Motyw.Scena.SWIETA) (if (noc) "#c9d6e4" else "#eef3f8") else (if (noc) "#0b1020" else "#2a2620")

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
