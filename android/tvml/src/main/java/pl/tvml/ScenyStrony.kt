package pl.tvml

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.clearAndSetSemantics
import org.json.JSONObject
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Scena SVG motywu ze Studia motywów (config.theme.weatherSceneType = "svg", API z features=ai-themes).
 * Wersja bez animacji (static=1): animacje CSS w SVG i tak nie działają w Androidzie.
 */
data class ScenaSvg(val dzien: String, val noc: String?) {
    private fun statyczny(u: String) = u + (if ('?' in u) "&" else "?") + "static=1"
    fun adres(wNocy: Boolean): String = statyczny(if (wNocy && noc != null) noc else dzien)
    /** Bez nocnej wersji strona przyciemnia dzienną: brightness(.42) saturate(.75) hue-rotate(-10deg). */
    fun przyciemnic(wNocy: Boolean) = wNocy && noc == null
}

/**
 * Półprzezroczyste „poduszki” pod trzema kolumnami statystyk karty strony (Andrzejki, Ratusz sezonowy).
 * Karta aplikacji ma inny układ napisów, więc poduszki wisiałyby w pustym miejscu: wyłączone.
 */
internal const val PODUSZKI_KARTY_STRONY = false

/**
 * Pasek daty motywu z panelu (API 1.8.3, theme.dateBar i stages[].dateBar): kolor paska akcentu, medal z obwódką
 * i ikoną, przedrostek („Zima w Lesznie”, „Wieczór andrzejkowy”), imieniny jutro i ukrycie imienin (Nowy Rok).
 * Tło belki zostaje w kolorze podłoża sceny (zatwierdzony wzór z iPhone'a), z panelu idą akcent, medal i teksty.
 */
data class PasekDaty(
    val scena: String,
    val akcent: Int?,
    val medal: Int?,
    val obwodka: Int?,
    val ikona: String,
    val tekstMedalu: String?,
    val przedrostek: String?,
    val jutro: String?,
    val bezImienin: Boolean,
    /** dateBar.days[] (API 1.8.3): wpis na każdy dzień etapu, klucz to data ISO; poza siecią też właściwy dzień. */
    val dni: Map<String, DzienPaska> = emptyMap(),
) {
    /** Jeden dzień etapu: przedrostek, imieniny jutro, ukrycie imienin i cała linia jak na stronie (z imionami dnia). */
    data class DzienPaska(val przedrostek: String?, val jutro: String?, val bezImienin: Boolean, val linia: String)

    companion object {
        fun z(j: JSONObject?): PasekDaty? {
            if (j == null || j.length() == 0) return null
            val pal = j.o("palette")
            fun k(n: String) = pal.s(n).ifEmpty { null }?.let { Plotno.kolor(it) }?.takeIf { (it ushr 24) != 0 }
            val medal = j.o("medal")
            val dni = j.lista("days").filter { it.s("date").isNotEmpty() }.associate { d ->
                d.s("date") to DzienPaska(d.s("prefix").trim().ifEmpty { null }, d.s("tomorrow").trim().ifEmpty { null }, d.b("hideNameday"), d.s("line"))
            }
            return PasekDaty(
                j.s("scene"), k("bar"), k("medBg"), k("medRim"), medal.s("icon"), medal.s("text").ifEmpty { null },
                j.s("prefix").trim().ifEmpty { null }, j.s("tomorrow").trim().ifEmpty { null }, j.b("hideNameday"), dni,
            )
        }
    }
}

/** Okno etapu motywu (API 1.8.3 stages[], starsze: cały motyw z upcomingThemes): od, do, scena, SVG i pasek daty. */
data class OknoSceny(val od: ZonedDateTime?, val do_: ZonedDateTime?, val scena: String, val svg: ScenaSvg? = null, val pasek: PasekDaty? = null) {
    fun trwa(teraz: ZonedDateTime) = (od == null || !teraz.isBefore(od)) && (do_ == null || !teraz.isAfter(do_))

    companion object {
        /** Okno z obiektu motywu albo etapu: weatherScene (albo backgroundScene), weatherSceneType svg z adresami, dateBar. */
        fun z(j: JSONObject): OknoSceny? {
            val svg = svgZ(j)
            val scena = j.s("weatherScene").ifEmpty { j.s("backgroundScene") }
            if (scena.isEmpty() && svg == null) return null
            return OknoSceny(Daty.iso(j.s("from")), Daty.iso(j.s("until")), scena, svg, PasekDaty.z(j.optJSONObject("dateBar")))
        }

        fun svgZ(j: JSONObject): ScenaSvg? =
            if (j.s("weatherSceneType") == "svg") j.s("weatherSceneSvgUrl").ifEmpty { null }?.let { ScenaSvg(it, j.s("weatherSceneSvgNightUrl").ifEmpty { null }) } else null
    }
}

/**
 * Opcje sceny dla bieżącego stanu pogody z config.weatherAnimation[state] (panel tvml.pl → Pogoda → Animacje).
 * Krajobraz zmienia tylko skyColor (górny kolor nieba); plaża i morze biorą też wodę, piasek i fale.
 */
data class OpcjeSceny(
    val skyColor: String? = null,
    val waterColor: String? = null,
    val beachColor: String? = null,
    val waveSpeed: Double? = null,
    val waveIntensity: Double? = null,
    val beachBiolum: Boolean? = null,
    val sceneType: String? = null,
    /** Klasa tła karty (color-sky-day-N / -night-N) z panelu; pod panoramą widać ją między niebem a dachami. */
    val skyClass: String? = null,
) {
    companion object {
        fun z(j: JSONObject?): OpcjeSceny {
            if (j == null) return OpcjeSceny()
            fun tekst(k: String) = j.s(k).trim().ifEmpty { null }
            fun liczba(k: String) = if (j.ma(k)) j.optDouble(k).takeIf { it.isFinite() } else null
            return OpcjeSceny(
                tekst("skyColor"), tekst("waterColor"), tekst("beachColor"), liczba("waveSpeed"), liczba("waveIntensity"),
                if (j.ma("beachBiolum")) j.b("beachBiolum") else null, tekst("sceneType"), tekst("skyClass"),
            )
        }
    }
}

/**
 * Jedna klatka sceny ze strony: to, co w weather-effects.js jest dostępne w _drawCustomScene (ctx, w, h, t,
 * isNight, state, activeCfg, horizonY, foreY, drawHill…), pod tymi samymi nazwami, żeby kod scen przechodził
 * do Kotlina prawie linia w linię. Wymiary w dp (piksele CSS strony), czas w sekundach.
 */
class RamaSceny(
    val ctx: Plotno,
    val w: Double,
    val h: Double,
    /** Czas w sekundach (na stronie performance.now() * 0.001). */
    val t: Double,
    val isNight: Boolean,
    /** Stan pogody z /api/v1/weather (sunny, partly-cloudy, cloudy, rainy-light… jak resolveState na stronie). */
    val state: String,
    val sceneType: String,
    val activeCfg: OpcjeSceny,
    /** Bieżąca chwila w Warszawie (zegar na Ratuszu, rok w Sylwestra, przedrostki Andrzejek). */
    val teraz: ZonedDateTime,
    /** false przy „Usuń animacje” w systemie: jedna nieruchoma klatka (Andrzejki zatrzymują się na t = 2.2). */
    val ruch: Boolean,
    /** Pamięć sceny między klatkami (zamiast pól this._… na stronie, np. ziarna piasku na plaży). */
    val pamiec: MutableMap<String, Any?>,
    /** Jak PogodaEfekty.ukryjGwiazdySceny: nakładka pogody nie chce gwiazd sceny (chmury, mgła, opady). */
    val ukryjGwiazdySceny: Boolean,
    /** Strona rysuje kartę główną w trybie „sidebar” (canvas rysuje też napisy); w aplikacji zawsze true. */
    val sidebarMode: Boolean = true,
) {
    val horizonY = h * 0.58
    val foreY = h * 0.80
    val isStormy = state.contains("stormy") || state == "rainy-heavy"
    val isRainy = state.startsWith("rainy") || state == "cloudy"
    val isSeasonalLeszno = sceneType in ScenyStrony.ratuszSezonowe
    val isAndrzejkiScene = sceneType == "leszno-andrzejki"
    val skyBottomY = if (sceneType == "leszno-ratusz-11listopada" || isSeasonalLeszno || isAndrzejkiScene) h * 0.65 else horizonY
    val wScale = 380.0 / max(380.0, w)

    /** Warstwa wzgórz (weather-effects.js 1594–1607): wypełnia obszar pod sinusoidą, co 4 px. */
    fun drawHill(yBase: Double, amp1: Double, freq1: Double, amp2: Double, freq2: Double, color: Any, phaseOffset: Double = 0.0) {
        ctx.beginPath()
        ctx.moveTo(0.0, h)
        var x = 0.0
        while (x <= w) {
            val y = yBase + Math.sin(x * freq1 * wScale + phaseOffset) * amp1 + Math.cos(x * freq2 * wScale - phaseOffset * 0.5) * amp2
            ctx.lineTo(x, y)
            x += 4.0
        }
        ctx.lineTo(w, h)
        ctx.closePath()
        ctx.fillStyle = color
        ctx.fill()
    }

    /** Ścieżka zaokrąglonego prostokąta (weather-effects.js 4888–4905, this._rrPath): jedno [tl] daje wszystkie rogi. */
    fun rrPath(x: Double, y: Double, w: Double, h: Double, tl: Double = 0.0, tr: Double? = null, br: Double? = null, bl: Double? = null) {
        val rTL = tl
        val rTR = tr ?: tl
        val rBR = br ?: tl
        val rBL = bl ?: tl
        ctx.beginPath()
        ctx.moveTo(x + rTL, y)
        ctx.lineTo(x + w - rTR, y)
        ctx.arcTo(x + w, y, x + w, y + rTR, rTR)
        ctx.lineTo(x + w, y + h - rBR)
        ctx.arcTo(x + w, y + h, x + w - rBR, y + h, rBR)
        ctx.lineTo(x + rBL, y + h)
        ctx.arcTo(x, y + h, x, y + h - rBL, rBL)
        ctx.lineTo(x, y + rTL)
        ctx.arcTo(x, y, x + rTL, y, rTL)
        ctx.closePath()
    }
}

/**
 * Sceny pogody ze strony tvml.pl (weather-effects.js, silnik V1, którego krajobrazy rysuje też V2) przeniesione
 * do aplikacji 1:1. Scenę wybiera serwer: config.theme.weatherScene (harmonogram motywów: Andrzejki, Zima,
 * Mikołajki, Święta, Wigilia, Sylwester, Nowy Rok, 11 Listopada), a bez niej sceneType z weatherAnimation.
 * Na scenach niespecjalnych rysuje się jeszcze nakładka pogody (pogoda-efekty.js, [NakladkaPogody]).
 */
object ScenyStrony {
    /**
     * „leszno-panorama” (Karol, 23.11: „Panorama Leszna niech jest wszędzie”), domyślna scena serwera od API PR #123:
     * Ratusz i kamienice jak na stronie ([lesznoPanorama]), a pogoda na niej z nakładki pogoda-efekty.js
     * (Karol, 24.11: animacje jak na szacie graficznej strony — mgła dryfuje, zasłona i mleczny dół).
     */
    const val PANORAMA = "leszno-panorama"

    val ratuszSezonowe = setOf("leszno-zima", "leszno-mikolajki", "leszno-wigilia", "leszno-swieta", "leszno-sylwester", "leszno-nowy-rok")

    /** Wszystkie sceny, które aplikacja umie narysować (lista customScenes ze strony plus plaża i morze). */
    val znane = setOf(
        PANORAMA, "autumn-forest", "autumn-lake", "park-lanterns", "winter-village", "alpine-peaks", "winter-christmas",
        "new-year", "easter-spring", "leszno-ratusz-11listopada", "powstanczy-biwak", "leszno-andrzejki", "beach", "ocean",
    ) + ratuszSezonowe

    /** pogoda-efekty.js scenaSpecjalna: scena motywu ma własne efekty, więc bez nakładki pogody, gwiazdy zawsze w nocy. */
    fun specjalna(id: String): Boolean =
        // panorama to zwykła scena na co dzień (pogoda rysowana normalnie), reszta leszno-* to motywy okolicznościowe
        id != PANORAMA && (id.startsWith("leszno-") || id.startsWith("svg:") || id == "new-year" || id == "powstanczy-biwak" || id == "winter-christmas")

    /**
     * Scena dla aplikacji: theme.weatherScene, a gdy pusta (żałoba, „Brak” w panelu, starszy serwer),
     * sceneType z weatherAnimation[state], a na końcu autumn-forest, jak strona.
     */
    fun wybierz(zMotywu: String?, opcje: OpcjeSceny): String =
        zMotywu?.trim()?.takeIf { it.isNotEmpty() && it in znane }
            ?: opcje.sceneType?.takeIf { it in znane }
            ?: PANORAMA

    /** Jedna klatka: niebo, gwiazdy, krajobraz sceny i przyciemnienie dołu (jak _drawCustomScene na stronie). */
    /** Tylko w wersji testowej (--es wylacz tlo,niebo,panorama,ciemno,nakladka): szukanie elementu, który psuje grafikę emulatora. */
    var wylaczone: Set<String> = emptySet()

    fun rysuj(r: RamaSceny) {
        if (r.sceneType == "beach" || r.sceneType == "ocean") {
            r.plazaIMorze()
            return
        }
        // panorama nie wypełnia płótna w całości: pod nią tło karty jak w CSS strony
        if (r.sceneType == PANORAMA && "tlo" !in wylaczone) r.tloKarty()
        if ("niebo" !in wylaczone) niebo(r)
        gwiazdy(r)
        when (r.sceneType) {
            PANORAMA -> if ("panorama" !in wylaczone) r.lesznoPanorama()
            "autumn-forest" -> r.autumnForest()
            "autumn-lake" -> r.autumnLake()
            "park-lanterns" -> r.parkLanterns()
            "winter-village" -> r.winterVillage()
            "alpine-peaks" -> r.alpinePeaks()
            "winter-christmas" -> r.winterChristmas()
            "new-year" -> r.newYear()
            "easter-spring" -> r.easterSpring()
            "leszno-ratusz-11listopada" -> r.ratusz11Listopada()
            "powstanczy-biwak" -> r.powstanczyBiwak()
            "leszno-andrzejki" -> r.andrzejki()
            in ratuszSezonowe -> r.ratuszSezonowy()
        }
        // weather-effects.js 3813–3823: przyciemnienie dolnej strefy pod napisami (bez Ratusza sezonowego i Andrzejek)
        if (!r.isSeasonalLeszno && !r.isAndrzejkiScene && "ciemno" !in wylaczone) {
            val ctx = r.ctx
            val g = ctx.createLinearGradient(0.0, r.horizonY - 15, 0.0, r.h)
            g.addColorStop(0.0, "rgba(6, 10, 22, 0)")
            g.addColorStop(0.35, "rgba(6, 10, 22, 0.28)")
            g.addColorStop(1.0, "rgba(6, 10, 22, 0.58)")
            ctx.fillStyle = g
            ctx.fillRect(0.0, r.horizonY - 15, r.w, r.h - (r.horizonY - 15))
        }
    }

    /** weather-effects.js 1459–1573: gradient nieba według sceny, pory i pogody. */
    private fun niebo(r: RamaSceny) = with(r) {
        val g = ctx.createLinearGradient(0.0, 0.0, 0.0, skyBottomY)
        fun s(vararg p: Pair<Double, Any>) = p.forEach { (o, k) -> g.addColorStop(o, k) }
        val sky = activeCfg.skyColor
        when {
            isAndrzejkiScene -> if (isNight) s(0.0 to "#0a0618", 0.6 to "#25124a", 1.0 to "#3d1a5c")
            else s(0.0 to "#1d1140", 0.6 to "#4a2a7e", 1.0 to "#8a5fb8")
            sceneType == "new-year" -> s(0.0 to (sky ?: "#040614"), 0.55 to "#0d1333", 1.0 to "#231942")
            isSeasonalLeszno -> if (!isNight) when (sceneType) {
                "leszno-wigilia" -> s(0.0 to "#203e7c", 0.54 to "#5870a8", 0.84 to "#d6a892", 1.0 to "#f2cca2")
                "leszno-zima" -> s(0.0 to "#0e5cac", 0.58 to "#3894de", 1.0 to "#b0defa")
                else -> s(0.0 to "#16488c", 0.58 to "#447ec4", 1.0 to "#c4d8ee")
            } else when (sceneType) {
                "leszno-zima" -> s(0.0 to "#040c1a", 0.58 to "#091c36", 1.0 to "#103862")
                "leszno-sylwester", "leszno-nowy-rok" -> s(0.0 to "#040914", 0.58 to "#091226", 1.0 to "#16203a")
                else -> s(0.0 to "#060916", 0.58 to "#140e24", 1.0 to "#420e20")
            }
            sceneType == "leszno-ratusz-11listopada" -> when {
                isNight -> s(0.0 to "#040814", 0.50 to "#0b152c", 1.0 to "#182544")
                isStormy -> s(0.0 to "#1c2436", 0.6 to "#334158", 1.0 to "#5d6d82")
                isRainy -> s(0.0 to "#334862", 0.6 to "#58728c", 1.0 to "#94acc0")
                else -> s(0.0 to "#1a5296", 0.38 to "#2f78c4", 0.75 to "#68ace6", 1.0 to "#d8ecfa")
            }
            sceneType == "powstanczy-biwak" -> if (isNight) s(0.0 to "#050812", 0.58 to "#14192b", 1.0 to "#3b1520")
            else s(0.0 to "#18263b", 0.55 to "#3b4a62", 1.0 to "#8c4a3c")
            isNight -> s(0.0 to (sky ?: "#050b1a"), 0.55 to "#0f1c38", 1.0 to "#1d2d4e")
            isStormy -> s(0.0 to "#1c2030", 0.6 to "#2e384d", 1.0 to "#4a5568")
            isRainy -> s(0.0 to "#37475a", 0.6 to "#566c7d", 1.0 to "#829eab")
            // Panorama Leszna: czysty błękit nad Rynkiem
            sceneType == PANORAMA -> s(0.0 to (sky ?: "#1a5296"), 0.45 to "#2f78c4", 0.8 to "#68ace6", 1.0 to "#cfe6f7")
            sceneType == "autumn-forest" || sceneType == "autumn-lake" || sceneType == "park-lanterns" ->
                s(0.0 to (sky ?: "#1e3c58"), 0.5 to "#4b6cb7", 0.85 to "#c77952", 1.0 to "#e8a86b")
            sceneType == "easter-spring" -> s(0.0 to (sky ?: "#1e69b8"), 0.5 to "#4fa3e3", 0.85 to "#9ad4f5", 1.0 to "#d8f3dc")
            else -> s(0.0 to (sky ?: "#1b3b64"), 0.55 to "#3b6998", 0.85 to "#7ea4c4", 1.0 to "#c8dce8")
        }
        ctx.fillStyle = g
        ctx.fillRect(0.0, 0.0, w, skyBottomY + 4)
    }

    /** weather-effects.js 1577–1590: 28 migoczących gwiazd w nocy (przy pogodzie tylko przy czystym niebie) i w Sylwestra. */
    private fun gwiazdy(r: RamaSceny) = with(r) {
        if (!((isNight && !ukryjGwiazdySceny) || sceneType == "new-year")) return@with
        ctx.save()
        for (i in 0 until 28) {
            val sx = ((i * 73 + 19) % 100) / 100.0 * w
            val sy = ((i * 41 + 7) % 100) / 100.0 * (horizonY * 0.82)
            val tw = 0.25 + 0.65 * abs(sin(t * (1.1 + (i % 5) * 0.3) + i))
            ctx.fillStyle = Plotno.rgba(255, 250, 235, tw)
            ctx.beginPath()
            ctx.arc(sx, sy, if (i % 3 == 0) 1.4 else 0.9, 0.0, Math.PI * 2)
            ctx.fill()
        }
        ctx.restore()
    }
}

/**
 * Scena ze strony w Compose: płótno w dp, zegar klatek jak reszta animacji (stoi, gdy karta jest poza ekranem),
 * nakładka pogody na scenach niespecjalnych. Przy „Usuń animacje” jedna nieruchoma klatka.
 */
@Composable
fun ScenaStrony(
    id: String, stan: String, noc: Boolean, opcje: OpcjeSceny, efekt: EfektPogody?, modifier: Modifier = Modifier, svg: ScenaSvg? = null,
) {
    if (svg != null) return ScenaZSvg(svg, noc, modifier)
    val ruch = ValueAnimator.areAnimatorsEnabled()
    val plotno = remember { Plotno(android.graphics.Canvas()) }
    val plotnoNakladki = remember { Plotno(android.graphics.Canvas()) }
    val pamiec = remember(id) { HashMap<String, Any?>() }
    val nakladka = remember { NakladkaPogody() }
    val start = remember { System.nanoTime() / 1e9 }
    val czas = if (ruch) czasAnimacji().value - start else 37.0
    val specjalna = ScenyStrony.specjalna(id)
    val zNakladka = !specjalna && efekt != null && "nakladka" !in ScenyStrony.wylaczone
    androidx.compose.foundation.layout.Box(modifier.clearAndSetSemantics { }) {
        Canvas(Modifier.matchParentSize()) {
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val g = density.toDouble()
                // restoreToCount: płótno wraca do stanu sprzed sceny także wtedy, gdy kod sceny zostawi save bez restore
                val stan0 = nc.save()
                nc.scale(density, density)
                plotno.nowaKlatka(nc)
                val rama = RamaSceny(
                    plotno, size.width / g, size.height / g, czas, noc, stan, id, opcje, ZonedDateTime.now(Daty.strefa), ruch, pamiec,
                    ukryjGwiazdySceny = !specjalna && efekt != null && !NakladkaPogody.gwiazdy(efekt, noc),
                )
                ScenyStrony.rysuj(rama)
                nc.restoreToCount(stan0)
            }
        }
        // pogoda (pogoda-efekty.js) na osobnej warstwie z własnym płótnem, nie w tej samej klatce co scena:
        // wspólne płótno z ponownym nowaKlatka w jednej klatce wywracało grafikę emulatora (CI 45374379981)
        if (zNakladka && efekt != null) Canvas(Modifier.matchParentSize()) {
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val g = density.toDouble()
                val stan0 = nc.save()
                nc.scale(density, density)
                plotnoNakladki.nowaKlatka(nc)
                nakladka.rysuj(plotnoNakladki, size.width / g, size.height / g, czas * 1000, efekt, noc, ruch)
                nc.restoreToCount(stan0)
            }
        }
    }
}

/**
 * Scena SVG ze Studia motywów, jak na stronie: obraz na całą kartę (cover), w nocy wersja nocna albo przyciemniona
 * dzienna, i pas przyciemnienia od 55 do 81 % wysokości pod wierszem prognozy. Bez nakładki pogody (scena specjalna).
 */
@Composable
private fun ScenaZSvg(svg: ScenaSvg, noc: Boolean, modifier: Modifier) {
    androidx.compose.foundation.layout.Box(modifier.clearAndSetSemantics { }) {
        coil3.compose.AsyncImage(
            model = svg.adres(noc), contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            colorFilter = if (svg.przyciemnic(noc)) androidx.compose.ui.graphics.ColorFilter.colorMatrix(przyciemnienieNocy()) else null,
        )
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.Transparent, 0.55f to Color.Transparent, 0.55f + 0.26f * 0.22f to Color(4, 10, 24).copy(alpha = 0.58f),
                    0.55f + 0.26f * 0.78f to Color(4, 10, 24).copy(alpha = 0.58f), 0.81f to Color.Transparent, 1f to Color.Transparent,
                ),
            )
        }
    }
}

/** Macierz kolorów dla brightness(.42) saturate(.75) hue-rotate(-10deg) z CSS (filtry Filter Effects, w tej kolejności). */
private fun przyciemnienieNocy(): androidx.compose.ui.graphics.ColorMatrix {
    val m = android.graphics.ColorMatrix()
    m.setScale(0.42f, 0.42f, 0.42f, 1f)
    val nasycenie = android.graphics.ColorMatrix().apply { setSaturation(0.75f) }
    m.postConcat(nasycenie)
    val kat = Math.toRadians(-10.0)
    val c = Math.cos(kat).toFloat()
    val s = Math.sin(kat).toFloat()
    val obrot = android.graphics.ColorMatrix(
        floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    m.postConcat(obrot)
    return androidx.compose.ui.graphics.ColorMatrix(m.array)
}
