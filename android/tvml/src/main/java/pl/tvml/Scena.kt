package pl.tvml

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import java.time.LocalTime
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

/** Animowana scena: niebo, słońce lub księżyc, chmury, opady, mgła, błyskawice i panorama Leszna z Ratuszem. */
@Composable
fun ScenaPogody(stan: StanPogody, pora: PoraDnia, scena: Motyw.Scena, modifier: Modifier = Modifier) {
    val czas by czasAnimacji()
    Canvas(modifier.clearAndSetSemantics { }) { RysunekSceny(stan, pora, scena, this).rysuj(czas) }
}

private class RysunekSceny(val stan: StanPogody, val pora: PoraDnia, val scena: Motyw.Scena, val d: DrawScope) {
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

    fun rysuj(t: Double) = with(d) {
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
            drawPath(chmura(Rect(Offset(x.toFloat(), y), Size(szer, szer * 0.42f))), kolorChmur.copy(alpha = 0.75f))
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
        val gora = h - 130 * s
        fun r(x: Float, y: Float, sz: Float, wy: Float) = Rect(Offset(x * s, gora + y * s), Size(sz * s, wy * s))
        fun Path.do_(x: Float, y: Float) = lineTo(x * s, gora + y * s)
        fun Path.od(x: Float, y: Float) = moveTo(x * s, gora + y * s)
        val miasto = if (noc) c(0x0a1226) else c(0x1c2b45)
        val ratusz = if (noc) c(0x101a33) else c(0x223456)
        val ziemia = if (zimowo) (if (noc) c(0xc9d6e4) else c(0xeef3f8)) else (if (noc) c(0x0b1020) else c(0x2a2620))
        drawRect(ziemia, r(0f, 108f, 358f, 22f).topLeft, r(0f, 108f, 358f, 22f).size)
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
                val barwy = if (scena == Motyw.Scena.WIOSNA || scena == Motyw.Scena.LATO) listOf(0x15803dL, 0x16a34aL, 0x166534L) else listOf(0xc2410cL, 0xd97706L, 0xb45309L, 0xea580cL)
                listOf(Triple(22f, 104f, 12f), Triple(44f, 108f, 9f), Triple(118f, 106f, 11f), Triple(252f, 105f, 12f), Triple(286f, 108f, 9f), Triple(330f, 104f, 12f)).forEachIndexed { i, (x, y, rr) ->
                    val o = r(x - rr, y - rr, rr * 2, rr * 2)
                    drawOval(c(barwy[i % barwy.size]), o.topLeft, o.size)
                }
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
