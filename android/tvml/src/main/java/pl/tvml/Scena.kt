package pl.tvml

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class StanPogody {
    SLONCE, PRZEJASNIENIA, CHMURY, DESZCZ, BURZA, SNIEG, MGLA, WIATR;

    val jasne: Boolean get() = this == SLONCE || this == PRZEJASNIENIA || this == WIATR

    companion object {
        /** Te same stany co silnik pogody na stronie (resolveState w weather-effects.js), np. rainy-moderate, night-foggy. */
        fun z(ikona: String): StanPogody {
            val i = ikona.lowercase()
            return when {
                "storm" in i || "thunder" in i || "lightning" in i -> BURZA
                "snow" in i -> SNIEG
                "rain" in i || "sleet" in i || "hail" in i || "shower" in i || "sprinkle" in i -> DESZCZ
                "fog" in i -> MGLA
                "wind" in i -> WIATR
                "cloud-sun" in i || "partly" in i || "cloudy-high" in i -> PRZEJASNIENIA
                "cloud" in i -> CHMURY
                else -> SLONCE
            }
        }
    }
}

enum class PoraDnia {
    SWIT, DZIEN, ZMIERZCH, NOC;

    companion object {
        /** Jak na stronie: świt 5–8, dzień 8–17, zmierzch 17–20, noc. Stacja może powiedzieć, że już noc. */
        fun teraz(dzien: Boolean, godzina: Int = LocalTime.now(Daty.strefa).hour): PoraDnia {
            if (!dzien) return NOC
            return when (godzina) {
                in 5..7 -> SWIT
                in 8..16 -> DZIEN
                in 17..19 -> ZMIERZCH
                else -> NOC
            }
        }
    }
}

/**
 * Efekt tła pogody z serwera (API 1.7.2: effect, intensity, windy, leaves, phase). Mapę warunków na efekt liczy serwer
 * (tą samą co strona), więc aplikacja niczego nie zgaduje, tylko rysuje [rodzaj].
 */
data class EfektPogody(val rodzaj: String, val intensywnosc: Float, val wietrznie: Boolean, val liscie: Boolean, val faza: String) {
    /** Niebo i panorama jak w dotychczasowej scenie dla podobnego stanu. */
    val stan: StanPogody
        get() = when (rodzaj) {
            "clear-day", "clear-night" -> StanPogody.SLONCE
            "clouds" -> if (intensywnosc < 0.5f) StanPogody.PRZEJASNIENIA else StanPogody.CHMURY
            "fog" -> StanPogody.MGLA
            "drizzle", "rain", "rain-heavy" -> StanPogody.DESZCZ
            "storm" -> StanPogody.BURZA
            "snow", "sleet" -> StanPogody.SNIEG
            else -> StanPogody.WIATR
        }

    /** phase: day, dusk (do 45 min od wschodu lub zachodu), night. */
    val pora: PoraDnia
        get() = when (faza) {
            "night" -> PoraDnia.NOC
            "dusk" -> if (LocalTime.now(Daty.strefa).hour < 12) PoraDnia.SWIT else PoraDnia.ZMIERZCH
            else -> PoraDnia.DZIEN
        }

    companion object {
        val rodzaje = setOf("clear-day", "clear-night", "clouds", "fog", "drizzle", "rain", "rain-heavy", "storm", "snow", "sleet", "wind")

        /**
         * Efekt z API do sceny. Wersja okolicznościowa z panelu (każda inna niż zwykła: 11 Listopada, zima, święta,
         * Sylwester, żałoba) ma pierwszeństwo: wtedy jej dotychczasowa scena bez nakładki pogody, jak na iPhonie.
         */
        fun doSceny(p: Pogoda?, motyw: Motyw): EfektPogody? = p?.efekt?.takeIf { motyw.rodzaj == Motyw.Rodzaj.ZWYKLY }
    }
}

/**
 * Animowana scena: niebo, słońce lub księżyc, chmury, opady, mgła, błyskawice i panorama Leszna z Ratuszem.
 * Z [efekt] (API 1.7.2) rysuje aktualne warunki jak strona; bez niego według stanu ze stacji.
 * Przy „Usuń animacje” w systemie (skala animacji 0) jedna nieruchoma klatka, bez błysków burzy.
 * Animacja stoi sama, gdy scena nie jest na ekranie albo aplikacja jest w tle (zegar klatek wtedy nie chodzi).
 */
@Composable
fun ScenaPogody(stan: StanPogody, pora: PoraDnia, scena: Motyw.Scena, modifier: Modifier = Modifier, efekt: EfektPogody? = null, kafel: Boolean = false) {
    if (!ValueAnimator.areAnimatorsEnabled()) {
        Canvas(modifier.clearAndSetSemantics { }) { RysunekSceny(stan, pora, scena, efekt, this, kafel).rysuj(KLATKA_BEZ_RUCHU, ruch = false) }
        return
    }
    val czas by czasAnimacji()
    val start = remember { System.nanoTime() / 1e9 }
    Canvas(modifier.clearAndSetSemantics { }) { RysunekSceny(stan, pora, scena, efekt, this, kafel).rysuj(czas - start, ruch = true) }
}

/** Chwila, z której jest nieruchoma klatka: cząstki rozrzucone po scenie, bez błyskawicy. */
private const val KLATKA_BEZ_RUCHU = 37.0

private fun rgb(r: Int, g: Int, b: Int) = Color(r, g, b)

private class RysunekSceny(
    stanStacji: StanPogody, poraStacji: PoraDnia, val scena: Motyw.Scena, val efekt: EfektPogody?, val d: DrawScope,
    /** Karta pogody na Starcie (jak kafel na iPhonie): cienki pas ziemi, bo pod grafiką jest belka z datą, i bledsze chmury. */
    val kafel: Boolean = false,
) {
    val krycieChmur = if (kafel) 0.5f else 1f
    val stan = efekt?.stan ?: stanStacji
    val pora = efekt?.pora ?: poraStacji
    val noc = pora == PoraDnia.NOC
    val swiatla = noc || pora == PoraDnia.ZMIERZCH || scena == Motyw.Scena.SWIETA || scena == Motyw.Scena.SYLWESTER
    val zimowo = scena == Motyw.Scena.ZIMA || scena == Motyw.Scena.SWIETA
    // projekt jest w punktach (scena 358 × 300); na Androidzie te same wymiary w dp
    val p = d.density

    fun c(v: Long) = kolor(v)

    fun niebo(): List<Color> {
        if (scena == Motyw.Scena.SYLWESTER && noc) return listOf(c(0x0a0820), c(0x1b1640), c(0x2b2160))
        if (noc) return when (stan) {
            StanPogody.SLONCE, StanPogody.WIATR -> listOf(c(0x04091e), c(0x101c3d), c(0x1c2a52))
            StanPogody.PRZEJASNIENIA -> listOf(c(0x060d24), c(0x1a2747))
            StanPogody.CHMURY -> listOf(c(0x0c1222), c(0x252e44))
            StanPogody.DESZCZ -> listOf(c(0x070a12), c(0x1a2130))
            StanPogody.BURZA -> listOf(c(0x05070d), c(0x141b2a))
            StanPogody.SNIEG -> listOf(c(0x0e1626), c(0x2c3b55))
            StanPogody.MGLA -> listOf(c(0x1d2330), c(0x3a4252))
        }
        if (stan.jasne && pora == PoraDnia.SWIT) return listOf(c(0x2b2f63), c(0xa2648d), c(0xf7a56a))
        if (stan.jasne && pora == PoraDnia.ZMIERZCH) return listOf(c(0x1f1846), c(0x7a3f73), c(0xf2774a))
        return when (stan) {
            StanPogody.SLONCE -> listOf(c(0x0d5bb5), c(0x4f9be6), c(0xa9d2f5))
            StanPogody.PRZEJASNIENIA -> listOf(c(0x2a6db0), c(0x89b6e2))
            StanPogody.CHMURY -> listOf(c(0x56657d), c(0x9aa8bc))
            StanPogody.DESZCZ -> listOf(c(0x34404f), c(0x6b7787))
            StanPogody.BURZA -> listOf(c(0x1b202b), c(0x454e5f))
            StanPogody.SNIEG -> listOf(c(0x8197b0), c(0xd3dee9))
            StanPogody.MGLA -> listOf(c(0xa7afb8), c(0xd9dee3))
            StanPogody.WIATR -> listOf(c(0x3f6593), c(0x9cb9d8))
        }
    }

    fun rysuj(t: Double, ruch: Boolean) {
        if (efekt != null) EfektSceny(this, efekt, t, ruch).rysuj() else rysujWedlugStanu(t)
    }

    /** Dotychczasowa scena według stanu ze stacji (starszy serwer bez pola effect i wersje okolicznościowe). */
    fun rysujWedlugStanu(t: Double) = with(d) {
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        drawRect(Brush.verticalGradient(niebo()))

        // gwiazdy
        if (noc && (stan.jasne || scena == Motyw.Scena.SYLWESTER)) {
            for (i in 0 until 45) {
                val x = ((i * 97) % 1000) / 1000.0 * w
                val y = ((i * 61) % 1000) / 1000.0 * h * 0.55
                val a = (0.45 + 0.45 * sin(t * 1.6 + i)).toFloat().coerceIn(0f, 1f)
                drawCircle(Color.White.copy(alpha = a), 1f * p, Offset(x.toFloat() + p, y.toFloat() + p))
            }
        }
        // słońce albo księżyc
        if (!noc && stan.jasne) {
            val wysoko = pora == PoraDnia.DZIEN
            val srodek = Offset((w * 0.78).toFloat(), (if (wysoko) h * 0.18 else h * 0.45).toFloat())
            val k = if (wysoko) c(0xfff3b0) else c(0xffb36b)
            drawCircle(Brush.radialGradient(listOf(k.copy(alpha = 0.55f), k.copy(alpha = 0f)), srodek, 70 * p), 70 * p, srodek)
            drawCircle(k, 26 * p, srodek)
        } else if (noc && stan.jasne) {
            val ksiezyc = Path().apply {
                fillType = PathFillType.EvenOdd
                addOval(Rect(Offset((w * 0.74).toFloat(), 26 * p), Size(40 * p, 40 * p)))
                addOval(Rect(Offset((w * 0.74).toFloat() + 12 * p, 20 * p), Size(40 * p, 40 * p)))
            }
            drawPath(ksiezyc, c(0xf4f1de))
        }
        // reflektory i fajerwerki
        if ((scena == Motyw.Scena.SYLWESTER || scena == Motyw.Scena.LISTOPAD) && swiatla) {
            val kolory = if (scena == Motyw.Scena.LISTOPAD) listOf(Color.White, c(0xdc143c)) else listOf(c(0xe8c26a), c(0xe8c26a))
            kolory.forEachIndexed { i, k ->
                val kat = sin(t * 0.6 + i * 2) * 0.25 + (if (i == 0) -0.35 else 0.35)
                val bx = w * 0.52
                val by = h * 0.84
                val snop = Path().apply {
                    moveTo(bx.toFloat(), by.toFloat())
                    lineTo((bx + sin(kat - 0.06) * h).toFloat(), (by - cos(kat - 0.06) * h).toFloat())
                    lineTo((bx + sin(kat + 0.06) * h).toFloat(), (by - cos(kat + 0.06) * h).toFloat())
                    close()
                }
                drawPath(snop, k.copy(alpha = 0.18f))
            }
        }
        if (scena == Motyw.Scena.SYLWESTER && swiatla) {
            val barwy = listOf(c(0xe8c26a), c(0xf472b6), c(0x7dd3fc))
            for (i in 0 until 3) {
                val okres = 2.6 + i * 0.7
                val faza = ((t + i * 0.9) % okres) / okres
                val cx = w * listOf(0.2, 0.55, 0.82)[i]
                val cy = h * listOf(0.18, 0.1, 0.22)[i]
                val r = (6 + faza * 34) * p
                for (k in 0 until 12) {
                    val a = k / 12.0 * 2 * Math.PI
                    drawLine(
                        barwy[i].copy(alpha = (1 - faza).toFloat()),
                        Offset((cx + cos(a) * r * 0.45).toFloat(), (cy + sin(a) * r * 0.45).toFloat()),
                        Offset((cx + cos(a) * r).toFloat(), (cy + sin(a) * r).toFloat()),
                        1.6f * p,
                    )
                }
            }
        }
        // chmury
        val ileChmur = when (stan) {
            StanPogody.SLONCE -> 1
            StanPogody.PRZEJASNIENIA, StanPogody.WIATR -> 3
            StanPogody.CHMURY, StanPogody.DESZCZ -> 6
            StanPogody.BURZA -> 7
            StanPogody.SNIEG -> 5
            StanPogody.MGLA -> 0
        }
        val kolorChmur = when {
            noc -> c(0x2a3550)
            stan == StanPogody.DESZCZ || stan == StanPogody.BURZA -> c(0x5f6b7d)
            stan == StanPogody.CHMURY -> c(0xcdd5e0)
            else -> Color.White
        }
        for (i in 0 until ileChmur) {
            val szer = (100 + (i % 3) * 30) * p
            val szybkosc = ((if (stan == StanPogody.WIATR) 26 else 8) + i * 1.5) * p
            val x = (i * 137 * p + t * szybkosc) % (w + szer * 2) - szer
            val y = (12 + (i * 53) % 70) * p
            drawPath(chmura(Rect(Offset(x.toFloat(), y), Size(szer, szer * 0.42f))), kolorChmur.copy(alpha = 0.75f * krycieChmur))
        }
        // błyskawica
        if (stan == StanPogody.BURZA && (sin(t * 0.9) > 0.97 || sin(t * 1.7 + 1) > 0.985)) {
            drawRect(Color.White.copy(alpha = 0.25f))
            val x0 = (w * 0.3).toFloat()
            val hf = h.toFloat()
            drawPath(Path().apply {
                moveTo(x0 + 20 * p, hf * 0.18f); lineTo(x0, hf * 0.42f); lineTo(x0 + 14 * p, hf * 0.42f)
                lineTo(x0 - 6 * p, hf * 0.7f); lineTo(x0 + 28 * p, hf * 0.36f); lineTo(x0 + 14 * p, hf * 0.36f); close()
            }, c(0xfde68a))
        }
        // deszcz
        if (stan == StanPogody.DESZCZ || stan == StanPogody.BURZA) {
            val ile = if (stan == StanPogody.BURZA) 80 else 55
            val kropla = Color(0.84f, 0.9f, 0.98f, 0.7f)
            for (i in 0 until ile) {
                val x0 = ((i * 151) % 1000) / 1000.0 * w
                val y = (((i * 89) % 1000) / 1000.0 * h + t * 420 * p) % (h + 20 * p) - 10 * p
                drawLine(kropla, Offset((x0 + y * 0.12).toFloat(), y.toFloat()), Offset((x0 + y * 0.12 + 3 * p).toFloat(), (y + 13 * p).toFloat()), 1.4f * p)
            }
        }
        // śnieg
        val platki = if (stan == StanPogody.SNIEG) 70 else if (zimowo && stan != StanPogody.DESZCZ && stan != StanPogody.BURZA) 28 else 0
        for (i in 0 until platki) {
            val r = (1.5 + i % 3) * p
            val y = (((i * 89) % 1000) / 1000.0 * h + t * (22 + r / p * 6) * p) % (h + 10 * p) - 5 * p
            val x = ((i * 151) % 1000) / 1000.0 * w + sin(t + i) * 10 * p
            drawCircle(Color.White.copy(alpha = 0.9f), r.toFloat(), Offset((x + r).toFloat(), (y + r).toFloat()))
        }
        // liście na wietrze
        if (stan == StanPogody.WIATR && scena == Motyw.Scena.JESIEN) {
            val barwy = listOf(c(0xea580c), c(0xd97706), c(0xb45309))
            for (i in 0 until 14) {
                val x = (((i * 151) % 1000) / 1000.0 * w + t * 90 * p) % (w + 20 * p) - 10 * p
                val y = ((i * 89) % 1000) / 1000.0 * h * 0.7 + sin(t * 2 + i) * 14 * p
                translate(x.toFloat(), y.toFloat()) {
                    rotate(Math.toDegrees(t * 3 + i).toFloat() % 360f, Offset.Zero) {
                        drawOval(barwy[i % 3], Offset(-5 * p, -2.5f * p), Size(10 * p, 5 * p))
                    }
                }
            }
        }
        panorama(w.toFloat(), h.toFloat())
        // mgła
        if (stan == StanPogody.MGLA) {
            for (i in 0 until 3) {
                val y = h * (0.4 + i * 0.18)
                val x = sin(t * 0.2 + i) * 30 * p - 40 * p
                drawRect(c(0xebeff3).copy(alpha = (0.45 + i * 0.1).toFloat()), Offset(x.toFloat(), y.toFloat()), Size((w + 80 * p).toFloat(), 46 * p))
            }
        }
    }

    fun chmura(r: Rect) = Path().apply {
        addOval(Rect(Offset(r.left, r.top + r.height * 0.35f), Size(r.width * 0.5f, r.height * 0.65f)))
        addOval(Rect(Offset(r.left + r.width * 0.22f, r.top), Size(r.width * 0.5f, r.height * 0.9f)))
        addOval(Rect(Offset(r.left + r.width * 0.48f, r.top + r.height * 0.25f), Size(r.width * 0.52f, r.height * 0.75f)))
    }

    /** Panorama Leszna z Ratuszem (siatka 358 × 130 jak w projekcie), z drzewami i ozdobami pory roku lub wersji. */
    fun panorama(w: Float, h: Float) = with(d) {
        val s = w / 358
        // kafel: cienki pas ziemi o stałej wysokości (4 dp), pod nim belka z datą w kolorze ziemi (jak na iPhonie)
        val gora = if (kafel) h - 4 * p - 108 * s else h - 130 * s
        fun r(x: Float, y: Float, sz: Float, wy: Float) = Rect(Offset(x * s, gora + y * s), Size(sz * s, wy * s))
        fun Path.do_(x: Float, y: Float) = lineTo(x * s, gora + y * s)
        fun Path.od(x: Float, y: Float) = moveTo(x * s, gora + y * s)
        val miasto = if (noc) c(0x0a1226) else c(0x1c2b45)
        val ratusz = if (noc) c(0x101a33) else c(0x223456)
        val ziemia = if (zimowo) (if (noc) c(0xc9d6e4) else c(0xeef3f8)) else (if (noc) c(0x0b1020) else c(0x2a2620))
        drawRect(ziemia, Offset(0f, gora + 108 * s), Size(w, h - (gora + 108 * s)))
        val domy = Path()
        for ((x, y, sz) in listOf(Triple(0f, 82f, 46f), Triple(50f, 76f, 24f), Triple(106f, 80f, 26f), Triple(296f, 84f, 26f))) {
            domy.addRect(r(x, y, sz, 108 - y))
        }
        for ((x, y, sz) in listOf(Triple(78f, 86f, 24f), Triple(240f, 78f, 50f), Triple(326f, 74f, 32f))) {
            domy.addRect(r(x, y, sz, 108 - y))
            domy.od(x, y); domy.do_(x + sz / 2, y - 12); domy.do_(x + sz, y); domy.close()
        }
        drawPath(domy, miasto)
        val rt = Path()
        rt.addRect(r(138f, 70f, 84f, 38f))
        rt.od(142f, 70f); rt.do_(146f, 62f); rt.do_(214f, 62f); rt.do_(218f, 70f); rt.close()
        rt.addRect(r(178f, 34f, 16f, 28f))
        rt.od(174f, 36f)
        rt.quadraticTo(176 * s, gora + 26 * s, 186 * s, gora + 20 * s)
        rt.quadraticTo(196 * s, gora + 26 * s, 198 * s, gora + 36 * s)
        rt.close()
        rt.addRect(r(185f, 6f, 2f, 14f))
        drawPath(rt, ratusz)
        val zegar = r(181.5f, 40.5f, 9f, 9f)
        drawOval(if (swiatla) c(0xfde68a) else c(0xe5e7eb), zegar.topLeft, zegar.size)
        if (swiatla) {
            val okna = Path()
            for ((x, y) in listOf(146f to 78f, 158f to 78f, 170f to 78f, 196f to 78f, 208f to 78f, 220f to 78f, 146f to 92f, 158f to 92f, 208f to 92f, 220f to 92f, 56f to 84f, 64f to 84f, 112f to 88f, 122f to 88f, 246f to 86f, 302f to 90f, 334f to 84f)) {
                okna.addRect(r(x, y, 5f, 7f))
            }
            drawPath(okna, c(0xfcd34d).copy(alpha = 0.95f))
        }
        when (scena) {
            Motyw.Scena.JESIEN, Motyw.Scena.LISTOPAD, Motyw.Scena.LATO, Motyw.Scena.WIOSNA -> {
                // małe drzewa z pniem i koroną z trzech kół, jak na iPhonie (916840b); wcześniej same duże koła na ziemi
                // wyglądały jak pomarańczowe kule, a jesień widać po spadających liściach z efektu pogody
                val barwy = if (scena == Motyw.Scena.WIOSNA || scena == Motyw.Scena.LATO) listOf(0x166534L, 0x15803dL, 0x14532dL) else listOf(0x9a3412L, 0xa16207L, 0x7c2d12L)
                val pnie = Path()
                listOf(22f, 44f, 118f, 252f, 286f, 330f).forEachIndexed { i, x ->
                    val wys = if (i % 2 == 0) 14f else 10f
                    val rr = if (i % 2 == 0) 5f else 4f
                    pnie.addRect(r(x - 1, 108 - wys, 2f, wys))
                    val cy = 108 - wys - rr * 0.4f
                    val korona = Path()
                    for ((dx, dy) in listOf(-rr * 0.6f to 0f, rr * 0.6f to 0f, 0f to -rr * 0.8f)) {
                        korona.addOval(r(x + dx - rr, cy + dy - rr, rr * 2, rr * 2))
                    }
                    drawPath(korona, c(barwy[i % barwy.size]).copy(alpha = if (noc) 0.75f else 0.95f))
                }
                drawPath(pnie, miasto)
            }
            Motyw.Scena.ZIMA, Motyw.Scena.SWIETA, Motyw.Scena.SYLWESTER -> {
                val choinki = Path()
                val czapy = Path()
                for ((x, y) in listOf(22f to 84f, 118f to 86f, 254f to 84f, 332f to 84f)) {
                    choinki.od(x, y); choinki.do_(x + 12, y + 24); choinki.do_(x - 12, y + 24); choinki.close()
                    czapy.od(x, y); czapy.do_(x + 5, y + 10); czapy.do_(x - 5, y + 10); czapy.close()
                }
                drawPath(choinki, c(0x1f4f43))
                if (scena != Motyw.Scena.SYLWESTER) {
                    czapy.addRect(r(138f, 68f, 84f, 4f))
                    drawPath(czapy, c(0xf1f6fb))
                }
            }
        }
        if (scena == Motyw.Scena.SWIETA) {
            val drzewko = Path().apply { od(96f, 40f); do_(122f, 108f); do_(70f, 108f); close() }
            drawPath(drzewko, c(0x14532d))
            drawPath(gwiazda(r(88f, 26f, 16f, 16f)), c(0xe0b45a))
            for ((x, y) in listOf(90f to 62f, 102f to 70f, 86f to 84f, 106f to 90f, 96f to 98f)) {
                drawCircle(c(0xfbbf24), 2 * s, Offset(x * s, gora + y * s))
            }
        }
        if (scena == Motyw.Scena.SYLWESTER) {
            val farba = android.graphics.Paint().apply {
                color = c(0xe8c26a).toArgb(); textSize = 30 * s; isAntiAlias = true
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText("2027", 86 * s, gora + 92 * s + farba.textSize * 0.35f, farba)
        }
        if (scena == Motyw.Scena.LISTOPAD) {
            for ((x, y) in listOf(186f to 6f, 150f to 48f, 218f to 48f)) {
                drawRect(Color.White, r(x, y, 12f, 3f).topLeft, r(x, y, 12f, 3f).size)
                drawRect(c(0xdc143c), r(x, y + 3, 12f, 3f).topLeft, r(x, y + 3, 12f, 3f).size)
            }
        }
    }
}

/**
 * Scena według efektu z serwera (API 1.7.2), jak na stronie i w TVML na iPhonie: aktualna pogoda, a nie sama pora roku.
 * Gwiazdy i księżyc tylko przy czystym niebie (nieliczne przy rzadkich chmurach nocą), nigdy we mgle, w deszczu ani w śniegu.
 * Najwyżej 70 cząstek; ruch liczony z czasu, więc tempo nie zależy od liczby klatek. Miary w dp jak punkty na iPhonie.
 */
private class EfektSceny(val s: RysunekSceny, val e: EfektPogody, val t: Double, val ruch: Boolean) {
    fun rysuj() = with(s.d) {
        val p = s.p.toDouble()
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val wNocy = e.faza == "night"
        val oZmierzchu = e.faza == "dusk"
        val n = e.intensywnosc.toDouble()
        fun pt(v: Double) = (v * p).toFloat()
        drawRect(Brush.verticalGradient(s.niebo()))
        val deszczowo = e.rodzaj == "rain" || e.rodzaj == "rain-heavy" || e.rodzaj == "storm"
        if ((e.rodzaj == "clouds" && n >= 0.6) || deszczowo) drawRect(Color(0.5f, 0.5f, 0.5f, 0.12f))
        if (e.rodzaj == "snow") drawRect(Color.White.copy(alpha = 0.06f))

        // gwiazdy: przy clear-night, nieliczne przy rzadkich chmurach nocą
        val rzadkieChmury = e.rodzaj == "clouds" && n < 0.5
        val gwiazdy = if (e.rodzaj == "clear-night") 30 else if (rzadkieChmury && wNocy) 8 else 0
        for (k in 0 until gwiazdy) {
            val x = ((k * 97) % 1000) / 1000.0 * w
            val y = ((k * 61) % 1000) / 1000.0 * h * 0.6
            val czestotliwosc = 0.6 + (k % 7) * 0.2
            val a = (if (rzadkieChmury) 0.5 else 1.0) * (0.5 + 0.45 * sin(t * czestotliwosc * 2 * PI + k))
            drawCircle(Color.White.copy(alpha = a.toFloat().coerceIn(0f, 1f)), pt(1.0), Offset(x.toFloat() + pt(1.0), y.toFloat() + pt(1.0)))
        }
        // sierp księżyca z poświatą
        if (wNocy && (e.rodzaj == "clear-night" || rzadkieChmury)) {
            val c = Offset((w * 0.8).toFloat(), pt(46.0))
            val jasny = kolor(0xf4f1de)
            drawCircle(
                Brush.radialGradient(0f to jasny.copy(alpha = 0.25f), 10f / 46f to jasny.copy(alpha = 0.25f), 1f to jasny.copy(alpha = 0f), center = c, radius = pt(46.0)),
                pt(46.0), c,
            )
            // sierp: tarcza bez drugiego koła (różnica ścieżek, żeby nie było widać jego brzegu)
            val ksiezyc = Path.combine(
                PathOperation.Difference,
                Path().apply { addOval(Rect(Offset(c.x - pt(20.0), c.y - pt(20.0)), Size(pt(40.0), pt(40.0)))) },
                Path().apply { addOval(Rect(Offset(c.x - pt(8.0), c.y - pt(26.0)), Size(pt(40.0), pt(40.0)))) },
            )
            drawPath(ksiezyc, jasny)
        }
        // słońce z 8 bladymi promieniami (obrót ok. 1 na 40 s) i złoty pył
        if (!wNocy && (e.rodzaj == "clear-day" || rzadkieChmury)) {
            val c = Offset((w * 0.8).toFloat(), (h * 0.2).toFloat())
            val k0 = if (oZmierzchu) kolor(0xffd2a0) else kolor(0xfff3b0)
            rotate(Math.toDegrees(t / 40 * 2 * PI).toFloat() % 360f, c) {
                for (r in 0 until 8) {
                    val a = r / 8.0 * 2 * PI
                    drawPath(Path().apply {
                        moveTo(c.x, c.y)
                        lineTo(c.x + (cos(a - 0.08) * 90 * p).toFloat(), c.y + (sin(a - 0.08) * 90 * p).toFloat())
                        lineTo(c.x + (cos(a + 0.08) * 90 * p).toFloat(), c.y + (sin(a + 0.08) * 90 * p).toFloat())
                        close()
                    }, k0.copy(alpha = 0.12f))
                }
            }
            drawCircle(
                Brush.radialGradient(0f to k0.copy(alpha = 0.5f), 18f / 60f to k0.copy(alpha = 0.5f), 1f to k0.copy(alpha = 0f), center = c, radius = pt(60.0)),
                pt(60.0), c,
            )
            drawCircle(k0, pt(24.0), c)
            if (e.rodzaj == "clear-day") {
                for (k in 0 until 7) {
                    val v = 4 + (k % 4) * 2.0
                    val y = h - (((k * 89) % 1000) / 1000.0 * h + t * v * p) % h
                    val x = ((k * 151) % 1000) / 1000.0 * w + sin(t * 0.5 + k) * 8 * p
                    drawCircle(kolor(0xffd76a).copy(alpha = 0.7f), pt(1.2), Offset((x + 1.2 * p).toFloat(), (y + 1.2 * p).toFloat()))
                }
            }
        }
        // chmury: kolory według pory, przy deszczu i burzy ciemne
        val ileChmur = when (e.rodzaj) {
            "clouds" -> 3 + Math.round(4 * n).toInt()
            "drizzle" -> 3
            "rain", "rain-heavy", "storm", "sleet" -> 5
            "snow" -> 4
            "wind" -> 2
            else -> 0
        }
        val kolorChmur = when {
            deszczowo -> if (wNocy) rgb(58, 66, 88) else rgb(96, 106, 128)
            wNocy -> rgb(118, 132, 168)
            oZmierzchu -> rgb(244, 210, 196)
            else -> rgb(238, 242, 248)
        }
        for (k in 0 until ileChmur) {
            val szer = (100 + (k % 3) * 30) * p
            val v = (4 + k % 6) * (if (e.wietrznie) 2.5 else 1.0) * p
            val x = (k * 137 * p + t * v) % (w + szer * 2) - szer
            val y = (10 + (k * 53) % 60) * p
            drawPath(s.chmura(Rect(Offset(x.toFloat(), y.toFloat()), Size(szer.toFloat(), (szer * 0.42).toFloat()))), kolorChmur.copy(alpha = 0.8f * s.krycieChmur))
        }
        // krople: mżawka, deszcz, ulewa, burza, deszcz ze śniegiem
        val (krople, dlugosc, predkosc) = when (e.rodzaj) {
            "drizzle" -> Triple(18 + (24 * n).toInt(), 6.0, 260.0)
            "rain" -> Triple(30 + (36 * n).toInt(), 11.0, 460.0)
            "rain-heavy" -> Triple(36 + (36 * n).toInt(), 16.0, 620.0)
            "storm" -> Triple(60, 16.0, 620.0)
            "sleet" -> Triple(21, 10.0, 420.0)
            else -> Triple(0, 0.0, 0.0)
        }
        val skos = if (e.wietrznie) 0.35 else 0.12
        val kolorKropli = (if (wNocy) rgb(178, 198, 235) else rgb(205, 222, 245)).copy(alpha = 0.75f)
        for (k in 0 until minOf(krople, 70)) {
            val x0 = ((k * 151) % 1000) / 1000.0 * (w + 60 * p) - 30 * p
            val v = predkosc * (0.85 + (k % 5) * 0.06) * p
            val y = (((k * 89) % 1000) / 1000.0 * h + t * v) % (h + 20 * p) - 10 * p
            val x = x0 + y * skos
            drawLine(kolorKropli, Offset(x.toFloat(), y.toFloat()), Offset((x + dlugosc * p * skos).toFloat(), (y + dlugosc * p).toFloat()), pt(1.3))
        }
        // płatki śniegu (1–3,6 dp), kołyszą się, przy wietrze znoszone w prawo
        val platki = if (e.rodzaj == "snow") 24 + (36 * n).toInt() else if (e.rodzaj == "sleet") 18 else 0
        val kolorPlatkow = (if (wNocy) rgb(232, 240, 255) else Color.White).copy(alpha = 0.9f)
        for (k in 0 until minOf(platki, 70)) {
            val r = (0.5 + (k % 5) * 0.32) * p
            val v = (18 + (k % 7) * 5.6) * p
            val y = (((k * 89) % 1000) / 1000.0 * h + t * v) % (h + 10 * p) - 5 * p
            val dryf = if (e.wietrznie) t * 20 * p else 0.0
            val x = (((k * 151) % 1000) / 1000.0 * w + sin(t + k) * 10 * p + dryf) % w
            drawCircle(kolorPlatkow, r.toFloat(), Offset((x + r).toFloat(), (y + r).toFloat()))
        }
        // wiatr: jasne, lekko wygięte smugi
        if (e.rodzaj == "wind") {
            val smuga = Color.White.copy(alpha = if (wNocy) 0.25f else 0.45f)
            for (k in 0 until 10 + (5 * n).toInt()) {
                val v = (160 + (k % 8) * 20) * p
                val x = (((k * 151) % 1000) / 1000.0 * w + t * v) % (w + 120 * p) - 60 * p
                val y = ((k * 89) % 1000) / 1000.0 * h * 0.75
                drawPath(Path().apply {
                    moveTo(x.toFloat(), y.toFloat())
                    quadraticTo((x + 25 * p).toFloat(), (y + 4 * p).toFloat(), (x + 50 * p).toFloat(), (y - 3 * p).toFloat())
                }, smuga, style = Stroke(pt(1.2)))
            }
        }
        // jesienne liście (tylko gdy serwer je włączy)
        if (e.liscie) {
            val barwy = listOf(kolor(0xf2b705), kolor(0xe88d14), kolor(0xd9531e), kolor(0xb8291d))
            val krycie = if (wNocy) 0.55f else 0.85f
            // jak na stronie (pogoda-efekty.js) i na iPhonie: 10 liści, przy wietrze 14, każdy obraca się w swoją stronę
            for (k in 0 until if (e.rodzaj == "wind") 14 else 10) {
                val v = (14 + (k % 5) * 4) * p
                val y = (((k * 89) % 1000) / 1000.0 * h + t * v) % (h + 10 * p) - 5 * p
                val boczny = (if (e.rodzaj == "wind") t * (120 + k * 10) else sin(t * 0.8 + k) * 18) * p
                val x = (((k * 151) % 1000) / 1000.0 * w + boczny) % (w + 20 * p)
                translate(x.toFloat(), y.toFloat()) {
                    val obrot = t * (0.6 + (k % 5) * 0.25) * (if (k % 2 == 0) 1 else -1) + k
                    rotate(Math.toDegrees(obrot).toFloat() % 360f, Offset.Zero) {
                        drawOval(barwy[k % 4].copy(alpha = krycie), Offset(-pt(4.4), -pt(2.1)), Size(pt(8.8), pt(4.2)))
                    }
                }
            }
        }
        s.panorama(w.toFloat(), h.toFloat())
        // mgła: 5 półprzezroczystych pasm na przemian w lewo i w prawo i mleczny dół, bez gwiazd i liści
        if (e.rodzaj == "fog") {
            val kolorMgly = when {
                wNocy -> rgb(168, 180, 204)
                oZmierzchu -> rgb(236, 218, 214)
                else -> rgb(236, 240, 245)
            }
            drawRect(
                Brush.verticalGradient(listOf(kolorMgly.copy(alpha = 0f), kolorMgly.copy(alpha = 0.35f)), startY = (h * 0.5).toFloat(), endY = h.toFloat()),
                Offset(0f, (h * 0.5).toFloat()), Size(w.toFloat(), (h * 0.5).toFloat()),
            )
            for (k in 0 until 5) {
                val v = (5 + k * 1.5) * (if (k % 2 == 0) 1 else -1) * p
                val x = (t * v) % (120 * p) - 120 * p
                val y = h * (0.22 + k * 0.14)
                // miękkie pasmo jak na iPhonie (c0d5b2c): przezroczyste u góry i u dołu, najgęstsze w środku
                val krycie = (0.16 + k * 0.035).toFloat()
                val wys = pt(56.0)
                drawRect(
                    Brush.verticalGradient(
                        listOf(kolorMgly.copy(alpha = 0f), kolorMgly.copy(alpha = krycie), kolorMgly.copy(alpha = 0f)),
                        startY = y.toFloat(), endY = y.toFloat() + wys,
                    ),
                    Offset(x.toFloat(), y.toFloat()), Size((w + 240 * p).toFloat(), wys),
                )
            }
        }
        if (oZmierzchu) drawRect(Color(1f, 150 / 255f, 90 / 255f, 0.07f))
        // burza: łagodny błysk całego tła, pierwszy 6 s od pojawienia się sceny, potem co 14 s, gaśnie przez 0,6 s
        if (e.rodzaj == "storm" && ruch && t > 6) {
            val x = (t - 6) % 14
            if (x < 0.6) drawRect(Color.White.copy(alpha = (0.2 * (1 - x / 0.6)).toFloat()))
        }
    }
}
