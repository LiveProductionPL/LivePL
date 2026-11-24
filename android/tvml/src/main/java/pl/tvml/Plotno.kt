package pl.tvml

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Cienka nakładka na android.graphics.Canvas z API CanvasRenderingContext2D przeglądarki (to, czego używają sceny
 * pogody tvml.pl w weather-effects.js i pogoda-efekty.js). Dzięki niej sceny przechodzą do Kotlina prawie linia
 * w linię: `ctx.fillStyle = "#1e3c58"`, `ctx.beginPath()`, `ctx.arc(…)`, `ctx.fill()`.
 *
 * Jednostki jak na stronie: piksele CSS = dp (płótno jest wcześniej przeskalowane przez gęstość ekranu).
 * Kolory: tekst CSS ("#rgb", "#rrggbb", "rgba(…)", "rgb(…)", "hsl(a)(…)", "white", "transparent"…), liczba ARGB
 * z [rgba] albo gradient z [createLinearGradient] / [createRadialGradient]. Liczby to Double.
 *
 * Różnice wobec przeglądarki: cienie kształtów rysuje sprzęt dopiero od Androida 9 (wcześniej tylko tekst),
 * clearRect nic nie robi (scena i tak maluje całe tło), filtry CSS i globalCompositeOperation nie są obsługiwane.
 */
class Plotno(var canvas: Canvas) {
    private class Stan(
        var fill: Any, var stroke: Any, var lineWidth: Double, var alpha: Double,
        var cap: String, var join: String, var shadowBlur: Double, var shadowColor: Any,
        var shadowX: Double, var shadowY: Double, var font: String, var align: String, var baseline: String,
    ) {
        fun kopia() = Stan(fill, stroke, lineWidth, alpha, cap, join, shadowBlur, shadowColor, shadowX, shadowY, font, align, baseline)
    }

    private var s = Stan("#000", "#000", 1.0, 1.0, "butt", "miter", 0.0, 0, 0.0, 0.0, "10px sans-serif", "start", "alphabetic")
    private val stos = ArrayList<Stan>()
    private val sciezka = Path()
    private var punkt = false
    private var px = 0.0
    private var py = 0.0
    private var startX = 0.0
    private var startY = 0.0
    private val pedzel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val olowek = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val owal = RectF()
    private val pomocnicza = Path()
    private val macierz = Matrix()

    var fillStyle: Any
        get() = s.fill
        set(v) { s.fill = v }
    var strokeStyle: Any
        get() = s.stroke
        set(v) { s.stroke = v }
    var lineWidth: Double
        get() = s.lineWidth
        set(v) { if (v > 0 && v.isFinite()) s.lineWidth = v }
    var globalAlpha: Double
        get() = s.alpha
        set(v) { if (v.isFinite()) s.alpha = v.coerceIn(0.0, 1.0) }
    var lineCap: String
        get() = s.cap
        set(v) { s.cap = v }
    var lineJoin: String
        get() = s.join
        set(v) { s.join = v }
    var shadowBlur: Double
        get() = s.shadowBlur
        set(v) { s.shadowBlur = max(0.0, v) }
    var shadowColor: Any
        get() = s.shadowColor
        set(v) { s.shadowColor = v }
    var shadowOffsetX: Double
        get() = s.shadowX
        set(v) { s.shadowX = v }
    var shadowOffsetY: Double
        get() = s.shadowY
        set(v) { s.shadowY = v }
    var font: String
        get() = s.font
        set(v) { s.font = v }
    var textAlign: String
        get() = s.align
        set(v) { s.align = v }
    var textBaseline: String
        get() = s.baseline
        set(v) { s.baseline = v }

    /** Nowa klatka na [c]: stan jak w świeżym kontekście przeglądarki (płótno jest używane wiele razy). */
    fun nowaKlatka(c: Canvas) {
        canvas = c
        s = Stan("#000", "#000", 1.0, 1.0, "butt", "miter", 0.0, 0, 0.0, 0.0, "10px sans-serif", "start", "alphabetic")
        stos.clear()
        beginPath()
    }

    // ---------- stan i przekształcenia ----------

    fun save() {
        stos += s.kopia()
        canvas.save()
    }

    fun restore() {
        if (stos.isEmpty()) return
        s = stos.removeAt(stos.size - 1)
        canvas.restore()
    }

    fun translate(x: Double, y: Double) = canvas.translate(x.toFloat(), y.toFloat())
    fun rotate(kat: Double) = canvas.rotate(Math.toDegrees(kat).toFloat())
    fun scale(x: Double, y: Double) = canvas.scale(x.toFloat(), y.toFloat())

    /** Mnoży bieżące przekształcenie (jak ctx.transform). */
    fun transform(a: Double, b: Double, c: Double, d: Double, e: Double, f: Double) {
        macierz.setValues(floatArrayOf(a.toFloat(), c.toFloat(), e.toFloat(), b.toFloat(), d.toFloat(), f.toFloat(), 0f, 0f, 1f))
        canvas.concat(macierz)
    }

    // ---------- ścieżka ----------

    fun beginPath() {
        sciezka.reset()
        punkt = false
    }

    fun moveTo(x: Double, y: Double) {
        sciezka.moveTo(x.toFloat(), y.toFloat())
        px = x; py = y; startX = x; startY = y
        punkt = true
    }

    fun lineTo(x: Double, y: Double) {
        if (!punkt) return moveTo(x, y)
        sciezka.lineTo(x.toFloat(), y.toFloat())
        px = x; py = y
    }

    fun closePath() {
        if (!punkt) return
        sciezka.close()
        px = startX; py = startY
    }

    fun quadraticCurveTo(cx: Double, cy: Double, x: Double, y: Double) {
        if (!punkt) moveTo(cx, cy)
        sciezka.quadTo(cx.toFloat(), cy.toFloat(), x.toFloat(), y.toFloat())
        px = x; py = y
    }

    fun bezierCurveTo(c1x: Double, c1y: Double, c2x: Double, c2y: Double, x: Double, y: Double) {
        if (!punkt) moveTo(c1x, c1y)
        sciezka.cubicTo(c1x.toFloat(), c1y.toFloat(), c2x.toFloat(), c2y.toFloat(), x.toFloat(), y.toFloat())
        px = x; py = y
    }

    fun rect(x: Double, y: Double, w: Double, h: Double) {
        moveTo(x, y)
        lineTo(x + w, y)
        lineTo(x + w, y + h)
        lineTo(x, y + h)
        closePath()
        moveTo(x, y)
    }

    /** Łuk jak w przeglądarce: kąty w radianach, zgodnie z ruchem wskazówek zegara, chyba że [przeciwnie]. */
    fun arc(x: Double, y: Double, r: Double, a0: Double, a1: Double, przeciwnie: Boolean = false) =
        ellipse(x, y, r, r, 0.0, a0, a1, przeciwnie)

    fun ellipse(x: Double, y: Double, rx: Double, ry: Double, obrot: Double, a0: Double, a1: Double, przeciwnie: Boolean = false) {
        if (rx < 0 || ry < 0 || !x.isFinite() || !y.isFinite()) return
        val pelny = 2 * PI
        var kat = a1 - a0
        kat = if (!przeciwnie) {
            if (kat >= pelny) pelny else ((kat % pelny) + pelny) % pelny
        } else {
            if (-kat >= pelny) -pelny else -((((-kat) % pelny) + pelny) % pelny)
        }
        // początek łuku (z obrotem elipsy) łączy się linią z bieżącym punktem, jak w przeglądarce
        val sx0 = rx * cos(a0)
        val sy0 = ry * sin(a0)
        val sx = x + sx0 * cos(obrot) - sy0 * sin(obrot)
        val sy = y + sx0 * sin(obrot) + sy0 * cos(obrot)
        if (punkt) lineTo(sx, sy) else moveTo(sx, sy)
        owal.set((x - rx).toFloat(), (y - ry).toFloat(), (x + rx).toFloat(), (y + ry).toFloat())
        val start = Math.toDegrees(a0).toFloat()
        val zakres = Math.toDegrees(kat).toFloat()
        if (obrot == 0.0) {
            dodajLuk(sciezka, start, zakres)
        } else {
            pomocnicza.reset()
            pomocnicza.moveTo(sx0.toFloat() + x.toFloat(), sy0.toFloat() + y.toFloat())
            dodajLuk(pomocnicza, start, zakres)
            macierz.reset()
            macierz.setRotate(Math.toDegrees(obrot).toFloat(), x.toFloat(), y.toFloat())
            pomocnicza.transform(macierz)
            sciezka.addPath(pomocnicza)
        }
        val ex0 = rx * cos(a0 + kat)
        val ey0 = ry * sin(a0 + kat)
        px = x + ex0 * cos(obrot) - ey0 * sin(obrot)
        py = y + ex0 * sin(obrot) + ey0 * cos(obrot)
    }

    /** Path.arcTo bierze kąt modulo 360, więc pełne koło idzie w dwóch połówkach. */
    private fun dodajLuk(p: Path, start: Float, zakres: Float) {
        if (abs(zakres) >= 359.99f) {
            val pol = zakres / 2
            p.arcTo(owal, start, pol, false)
            p.arcTo(owal, start + pol, pol, false)
        } else if (zakres != 0f) {
            p.arcTo(owal, start, zakres, false)
        }
    }

    /** Łuk styczny do dwóch odcinków (ctx.arcTo), jak w przeglądarce. */
    fun arcTo(x1: Double, y1: Double, x2: Double, y2: Double, r: Double) {
        if (!punkt) moveTo(x1, y1)
        val x0 = px
        val y0 = py
        val ax = x0 - x1
        val ay = y0 - y1
        val bx = x2 - x1
        val by = y2 - y1
        val la = hypot(ax, ay)
        val lb = hypot(bx, by)
        if (r <= 0 || la == 0.0 || lb == 0.0 || abs(ax * by - ay * bx) < 1e-9) return lineTo(x1, y1)
        val kat = acos(((ax * bx + ay * by) / (la * lb)).coerceIn(-1.0, 1.0))
        val d = r / tan(kat / 2)
        val t1x = x1 + ax / la * d
        val t1y = y1 + ay / la * d
        val t2x = x1 + bx / lb * d
        val t2y = y1 + by / lb * d
        // środek okręgu: od wierzchołka wzdłuż dwusiecznej
        val dwx = ax / la + bx / lb
        val dwy = ay / la + by / lb
        val dl = hypot(dwx, dwy)
        val odl = r / sin(kat / 2)
        val cx = x1 + dwx / dl * odl
        val cy = y1 + dwy / dl * odl
        val a0 = atan2(t1y - cy, t1x - cx)
        val a1 = atan2(t2y - cy, t2x - cx)
        val wPrawo = (ax * by - ay * bx) < 0
        lineTo(t1x, t1y)
        ellipse(cx, cy, r, r, 0.0, a0, a1, !wPrawo)
    }

    fun fill(regula: String = "nonzero") {
        sciezka.fillType = if (regula == "evenodd") Path.FillType.EVEN_ODD else Path.FillType.WINDING
        if (ustawPedzel(pedzel, s.fill)) canvas.drawPath(sciezka, pedzel)
    }

    fun stroke() {
        if (ustawPedzel(olowek, s.stroke)) canvas.drawPath(sciezka, olowek)
    }

    fun clip(regula: String = "nonzero") {
        sciezka.fillType = if (regula == "evenodd") Path.FillType.EVEN_ODD else Path.FillType.WINDING
        canvas.clipPath(sciezka)
    }

    fun fillRect(x: Double, y: Double, w: Double, h: Double) {
        if (ustawPedzel(pedzel, s.fill)) canvas.drawRect(min(x, x + w).toFloat(), min(y, y + h).toFloat(), max(x, x + w).toFloat(), max(y, y + h).toFloat(), pedzel)
    }

    fun strokeRect(x: Double, y: Double, w: Double, h: Double) {
        if (ustawPedzel(olowek, s.stroke)) canvas.drawRect(min(x, x + w).toFloat(), min(y, y + h).toFloat(), max(x, x + w).toFloat(), max(y, y + h).toFloat(), olowek)
    }

    /** W przeglądarce czyści piksele; tu scena zawsze maluje tło od nowa, więc nic nie robi. */
    @Suppress("UNUSED_PARAMETER")
    fun clearRect(x: Double, y: Double, w: Double, h: Double) {}

    // ---------- gradienty ----------

    class Gradient internal constructor(private val liniowy: Boolean, private val p: DoubleArray) {
        private val pozycje = ArrayList<Float>(4)
        private val kolory = ArrayList<Int>(4)
        private var shader: Shader? = null

        fun addColorStop(pozycja: Double, kolor: Any) {
            pozycje += pozycja.toFloat().coerceIn(0f, 1f)
            kolory += Plotno.kolor(kolor)
            shader = null
        }

        internal fun shader(): Shader? {
            if (kolory.isEmpty()) return null
            shader?.let { return it }
            // sceny tworzą te same gradienty w każdej klatce: wspólny obiekt zamiast nowego co 16 ms (mniej pracy
            // dla grafiki; w emulatorze ciągłe nowe gradienty mgły kończyły się awarią po kilkudziesięciu sekundach)
            val klucz = Klucz(liniowy, p.copyOf(), pozycje.toFloatArray(), kolory.toIntArray())
            synchronized(PAMIEC) { PAMIEC[klucz] }?.let { shader = it; return it }
            // gradient z jednym kolorem albo z tą samą pozycją wszędzie: Android wymaga dwóch punktów
            val k = if (kolory.size == 1) intArrayOf(kolory[0], kolory[0]) else kolory.toIntArray()
            val poz = if (kolory.size == 1) floatArrayOf(0f, 1f) else pozycje.toFloatArray()
            for (i in 1 until poz.size) if (poz[i] < poz[i - 1]) poz[i] = poz[i - 1]
            val sh = if (liniowy) {
                val x1 = if (p[0] == p[2] && p[1] == p[3]) p[2] + 0.001 else p[2]
                LinearGradient(p[0].toFloat(), p[1].toFloat(), x1.toFloat(), p[3].toFloat(), k, poz, Shader.TileMode.CLAMP)
            } else {
                // przeglądarka: gradient między dwoma okręgami; sceny używają prawie zawsze wspólnego środka i r0 = 0,
                // więc tu okrąg zewnętrzny, a r0 przesuwa pozycje kolorów
                val r0 = p[2]
                val r1 = max(p[5], 0.001)
                val przes = (r0 / r1).toFloat().coerceIn(0f, 1f)
                val pp = FloatArray(poz.size) { przes + poz[it] * (1f - przes) }
                RadialGradient(p[3].toFloat(), p[4].toFloat(), r1.toFloat(), k, pp, Shader.TileMode.CLAMP)
            }
            shader = sh
            synchronized(PAMIEC) { PAMIEC[klucz] = sh }
            return sh
        }

        private class Klucz(val liniowy: Boolean, val p: DoubleArray, val poz: FloatArray, val kol: IntArray) {
            override fun equals(other: Any?) = other is Klucz && other.liniowy == liniowy &&
                other.p.contentEquals(p) && other.poz.contentEquals(poz) && other.kol.contentEquals(kol)
            override fun hashCode() = ((p.contentHashCode() * 31 + poz.contentHashCode()) * 31 + kol.contentHashCode()) * 2 + (if (liniowy) 1 else 0)
        }

        private companion object {
            /** Ostatnio użyte gradienty (LRU), wspólne dla wszystkich płócien. */
            val PAMIEC = object : LinkedHashMap<Klucz, Shader>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Klucz, Shader>?) = size > 160
            }
        }
    }

    /**
     * Miękka plama mgły (pogoda-efekty.js: gradient promienisty a → a/2 przy 0,6 → 0) wpisana w owal x, y, w, h,
     * w kolorze [kolor] (z kryciem), z bieżącym przekształceniem i globalAlpha płótna.
     */
    fun plamaMgly(x: Double, y: Double, w: Double, h: Double, kolor: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.color = kolor
        p.alpha = (p.alpha * s.alpha).roundToInt().coerceIn(0, 255)
        canvas.drawBitmap(MASKA_MGLY, null, RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()), p)
    }

    fun createLinearGradient(x0: Double, y0: Double, x1: Double, y1: Double) = Gradient(true, doubleArrayOf(x0, y0, x1, y1))

    fun createRadialGradient(x0: Double, y0: Double, r0: Double, x1: Double, y1: Double, r1: Double) =
        Gradient(false, doubleArrayOf(x0, y0, r0, x1, y1, r1))

    // ---------- tekst ----------

    private fun ustawCzcionke(p: Paint) {
        val f = s.font
        val rozmiar = ROZMIAR.find(f)?.groupValues?.get(1)?.toFloatOrNull() ?: 10f
        val gruby = GRUBY.containsMatchIn(f)
        val pochyly = f.contains("italic")
        val szeryf = SZERYF.containsMatchIn(f.replace("sans-serif", ""))
        val styl = when {
            gruby && pochyly -> Typeface.BOLD_ITALIC
            gruby -> Typeface.BOLD
            pochyly -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        p.typeface = Typeface.create(if (szeryf) Typeface.SERIF else Typeface.SANS_SERIF, styl)
        p.textSize = rozmiar
    }

    private fun wyrownaj(p: Paint) {
        p.textAlign = when (s.align) {
            "center" -> Paint.Align.CENTER
            "right", "end" -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
    }

    private fun linia(p: Paint, y: Double): Float {
        val m = p.fontMetrics
        return when (s.baseline) {
            "top", "hanging" -> (y - m.ascent).toFloat()
            "middle" -> (y - (m.ascent + m.descent) / 2).toFloat()
            "bottom", "ideographic" -> (y - m.descent).toFloat()
            else -> y.toFloat()
        }
    }

    fun fillText(tekst: String, x: Double, y: Double) {
        if (!ustawPedzel(pedzel, s.fill)) return
        ustawCzcionke(pedzel)
        wyrownaj(pedzel)
        canvas.drawText(tekst, x.toFloat(), linia(pedzel, y), pedzel)
    }

    fun strokeText(tekst: String, x: Double, y: Double) {
        if (!ustawPedzel(olowek, s.stroke)) return
        ustawCzcionke(olowek)
        wyrownaj(olowek)
        canvas.drawText(tekst, x.toFloat(), linia(olowek, y), olowek)
    }

    class Miara(val width: Double)

    fun measureText(tekst: String): Miara {
        ustawCzcionke(pedzel)
        return Miara(pedzel.measureText(tekst).toDouble())
    }

    // ---------- farba ----------

    private fun ustawPedzel(p: Paint, styl: Any): Boolean {
        val a = s.alpha
        if (a <= 0.0) return false
        p.shader = null
        when (styl) {
            is Gradient -> {
                p.shader = styl.shader() ?: return false
                p.color = 0xFF000000.toInt()
                p.alpha = (a * 255).roundToInt()
            }
            else -> {
                val k = kolor(styl)
                val ka = ((k ushr 24) and 0xff) * a
                if (ka <= 0.0) return false
                p.color = (k and 0x00ffffff) or (ka.roundToInt().coerceIn(0, 255) shl 24)
            }
        }
        if (p === olowek) {
            p.strokeWidth = s.lineWidth.toFloat()
            p.strokeCap = when (s.cap) { "round" -> Paint.Cap.ROUND; "square" -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT }
            p.strokeJoin = when (s.join) { "round" -> Paint.Join.ROUND; "bevel" -> Paint.Join.BEVEL; else -> Paint.Join.MITER }
        }
        val cien = kolor(s.shadowColor)
        if (s.shadowBlur > 0 && (cien ushr 24) != 0 || (s.shadowX != 0.0 || s.shadowY != 0.0) && (cien ushr 24) != 0) {
            val ca = ((cien ushr 24) and 0xff) * a
            val c = (cien and 0x00ffffff) or (ca.roundToInt().coerceIn(0, 255) shl 24)
            p.setShadowLayer(max(0.01, s.shadowBlur * 0.866).toFloat(), s.shadowX.toFloat(), s.shadowY.toFloat(), c)
        } else {
            p.clearShadowLayer()
        }
        return true
    }

    companion object {
        /** Maska plamy mgły 256 × 256 (sam kanał krycia): 1 w środku, 0,5 przy 0,6 promienia, 0 na brzegu. */
        private val MASKA_MGLY: android.graphics.Bitmap by lazy {
            val n = 256
            val b = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ALPHA_8)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(n / 2f, n / 2f, n / 2f, intArrayOf(0xFFFFFFFF.toInt(), 0x80FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            Canvas(b).drawCircle(n / 2f, n / 2f, n / 2f, p)
            b
        }
        private val pamiec = HashMap<String, Int>()
        private val ROZMIAR = Regex("""([\d.]+)px""")
        private val SZERYF = Regex("""(?i)(georgia|playfair|times|serif)""")
        private val GRUBY = Regex("""\b(bold|bolder|[6-9]00)\b""")

        /** Kolor ARGB z liczb jak rgba(r, g, b, a) w CSS (a od 0 do 1). */
        fun rgba(r: Number, g: Number, b: Number, a: Number = 1.0): Int {
            fun k(v: Number) = v.toDouble().roundToInt().coerceIn(0, 255)
            return (((a.toDouble().coerceIn(0.0, 1.0) * 255).roundToInt()) shl 24) or (k(r) shl 16) or (k(g) shl 8) or k(b)
        }

        fun rgb(r: Number, g: Number, b: Number): Int = rgba(r, g, b, 1.0)

        /** Kolor ARGB z hsla jak w CSS (h w stopniach, s i l od 0 do 100). */
        fun hsla(h: Number, sat: Number, l: Number, a: Number = 1.0): Int {
            val hh = ((h.toDouble() % 360) + 360) % 360 / 360
            val ss = sat.toDouble().coerceIn(0.0, 100.0) / 100
            val ll = l.toDouble().coerceIn(0.0, 100.0) / 100
            fun f(n: Double): Double {
                val k = (n + hh * 12) % 12
                return ll - ss * min(ll, 1 - ll) * max(-1.0, min(min(k - 3, 9 - k), 1.0))
            }
            return rgba(f(0.0) * 255, f(8.0) * 255, f(4.0) * 255, a)
        }

        fun kolor(v: Any?): Int = when (v) {
            is Int -> v
            is Long -> v.toInt()
            is String -> pamiec.getOrPut(v) { parsuj(v) }
            else -> 0
        }

        private fun parsuj(t: String): Int {
            val s = t.trim().lowercase()
            if (s.startsWith("#")) {
                val h = s.substring(1)
                return when (h.length) {
                    3 -> (0xff shl 24) or (h.map { "$it$it".toInt(16) }.let { (it[0] shl 16) or (it[1] shl 8) or it[2] })
                    4 -> h.map { "$it$it".toInt(16) }.let { (it[3] shl 24) or (it[0] shl 16) or (it[1] shl 8) or it[2] }
                    6 -> (0xff shl 24) or h.toInt(16)
                    8 -> (h.substring(6, 8).toInt(16) shl 24) or h.substring(0, 6).toInt(16)
                    else -> 0
                }
            }
            val nawias = Regex("""^(rgba?|hsla?)\(([^)]*)\)$""").find(s)
            if (nawias != null) {
                val czesci = nawias.groupValues[2].split(Regex("""[\s,/]+""")).filter { it.isNotEmpty() }
                fun liczba(i: Int, d: Double): Double {
                    val c = czesci.getOrNull(i) ?: return d
                    return if (c.endsWith("%")) c.dropLast(1).toDouble() / 100 * (if (i == 3) 1.0 else 255.0) else c.toDouble()
                }
                return if (nawias.groupValues[1].startsWith("rgb")) {
                    rgba(liczba(0, 0.0), liczba(1, 0.0), liczba(2, 0.0), liczba(3, 1.0))
                } else {
                    val a = czesci.getOrNull(3)?.let { if (it.endsWith("%")) it.dropLast(1).toDouble() / 100 else it.toDouble() } ?: 1.0
                    hsla(czesci[0].removeSuffix("deg").toDouble(), czesci[1].removeSuffix("%").toDouble(), czesci[2].removeSuffix("%").toDouble(), a)
                }
            }
            return when (s) {
                "white" -> 0xffffffff.toInt()
                "black" -> 0xff000000.toInt()
                "red" -> 0xffff0000.toInt()
                "gold" -> 0xffffd700.toInt()
                "orange" -> 0xffffa500.toInt()
                "yellow" -> 0xffffff00.toInt()
                "transparent", "none" -> 0
                else -> 0xff000000.toInt()
            }
        }
    }
}
