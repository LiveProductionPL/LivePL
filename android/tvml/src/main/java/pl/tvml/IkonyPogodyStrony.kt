package pl.tvml

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ikony pogody 1:1 ze strony tvml.pl (assets/js/weather-icons.js): 17 stanów × dzień/noc na siatce 64×64,
 * styl „naklejki” z gradientem i obrysem. Kod tworzy ten sam SVG co strona (resolve + _draw), a rysuje go
 * androidsvg (jak sanie przy logo). Bez cienia feDropShadow (androidsvg nie obsługuje filtrów) i bez animacji.
 * Karol 24.11 o karcie pogody: ikony rysowane w stylu strony, nie systemowe.
 */
object IkonyPogodyStrony {
    // ALIASES: klucz API → stan zestawu i wymuszona noc (null = według parametru noc)
    private val ALIASY: Map<String, Pair<String, Boolean?>> = mapOf(
        "sun" to ("clear" to null), "cloud-sun" to ("partly-cloudy" to null), "cloud" to ("overcast" to null), "rain" to ("rain" to null),
        "storm" to ("storm" to null), "snow" to ("snow" to null), "sleet" to ("sleet" to null), "hail" to ("hail" to null), "fog" to ("fog" to null),
        "wind" to ("wind" to null), "clear-night" to ("clear" to true), "partly-night" to ("partly-cloudy" to true),
        "sunny" to ("clear" to false), "partly-cloudy" to ("partly-cloudy" to null), "cloudy" to ("overcast" to null), "cloudy-night" to ("overcast" to true),
        "rainy-light" to ("rain-light" to null), "rainy-moderate" to ("rain" to null), "rainy-heavy" to ("rain-heavy" to null), "stormy" to ("storm" to null),
        "snowy" to ("snow" to null), "windy" to ("wind" to null), "foggy" to ("fog" to null), "sunny-foggy" to ("fog" to false), "night-foggy" to ("fog" to true),
        "clear" to ("clear" to null), "few" to ("mostly-clear" to null), "change" to ("partly-cloudy" to null), "light" to ("rain-light" to null),
        "moderate" to ("rain" to null), "heavy" to ("rain-heavy" to null), "clear-fog" to ("fog" to null), "few-fog" to ("fog" to null),
        "change-fog" to ("fog" to null), "cloud-fog" to ("fog" to null),
        "wi-day-sunny" to ("clear" to false), "wi-night-clear" to ("clear" to true), "wi-day-cloudy-high" to ("mostly-clear" to false),
        "wi-night-alt-cloudy-high" to ("mostly-clear" to true), "wi-day-cloudy" to ("partly-cloudy" to false), "wi-night-alt-cloudy" to ("partly-cloudy" to true),
        "wi-cloud" to ("overcast" to null), "wi-day-haze" to ("fog" to false), "wi-night-fog" to ("fog" to true), "wi-day-fog" to ("fog" to null),
        "wi-fog" to ("fog" to null), "wi-sprinkle" to ("drizzle" to null), "wi-showers" to ("rain" to null), "wi-rain" to ("rain-heavy" to null),
        "wi-snow" to ("snow" to null),
        "overcast" to ("overcast" to null), "mostly-clear" to ("mostly-clear" to null), "drizzle" to ("drizzle" to null), "rain-light" to ("rain-light" to null),
        "rain-heavy" to ("rain-heavy" to null), "freezing-rain" to ("freezing-rain" to null), "heat" to ("heat" to null), "frost" to ("frost" to null),
    )

    /** resolve(icon, opts): stan zestawu i wariant nocny z klucza API, opisu i temperatury (bez kodu WMO i godziny). */
    fun stan(klucz: String, noc: Boolean, opis: String? = null, temp: Double? = null): Pair<String, Boolean> {
        val k = klucz.trim().lowercase()
        val al = ALIASY[k] ?: ("clear" to null)
        var s = al.first
        val d = opis?.lowercase().orEmpty()
        if (d.isNotEmpty()) {
            when (s) {
                "rain", "rain-light" -> s = when {
                    "mżawk" in d || "mzawk" in d -> "drizzle"
                    "marzn" in d -> "freezing-rain"
                    "ze śniegiem" in d || "ze sniegiem" in d -> "sleet"
                    Regex("ulew|silny|intensywn|mocny|obfit").containsMatchIn(d) -> "rain-heavy"
                    Regex("lekk|słab|slab|przelotn").containsMatchIn(d) -> "rain-light"
                    else -> s
                }
                "snow" -> if ("deszcz ze śniegiem" in d || "deszcz ze sniegiem" in d) s = "sleet"
                "storm" -> s = when {
                    "ulew" in d && !Regex("burz|pioru").containsMatchIn(d) -> "rain-heavy"
                    "grad" in d && !Regex("burz|pioru").containsMatchIn(d) -> "hail"
                    else -> s
                }
                "clear" -> if (Regex("małe zachm|male zachm|przeważnie słon|przewaznie slon|lekkie zachm").containsMatchIn(d)) s = "mostly-clear"
                "partly-cloudy" -> if (Regex("małe zachm|male zachm").containsMatchIn(d)) s = "mostly-clear"
            }
        }
        if ((s == "clear" || s == "mostly-clear") && temp != null) {
            if (temp >= 30) s = "heat" else if (temp <= -10) s = "frost"
        }
        return s to (al.second ?: noc)
    }

    // ── Paleta (C ze strony) ──
    private const val sunA = "#ffe58a"; private const val sunB = "#f7b928"; private const val sunC = "#e8930c"
    private const val sunLine = "#b9740a"; private const val ray = "#f5a91c"
    private const val moonA = "#fff6d6"; private const val moonB = "#f1d27a"; private const val moonLine = "#b48a2c"; private const val star = "#ffe7a3"
    private const val cloudA = "#ffffff"; private const val cloudB = "#e2e9f3"; private const val cloudLine = "#7f95b5"
    private const val nCloudA = "#eef2f8"; private const val nCloudB = "#bfcadb"; private const val nCloudLine = "#5f7396"
    private const val backA = "#d3dcea"; private const val backB = "#a9b8cd"; private const val backLine = "#6e83a3"
    private const val stormA = "#9aa7bb"; private const val stormB = "#5f6d86"; private const val stormLine = "#3c4a64"
    private const val dropA = "#6cb8ff"; private const val dropB = "#1f6fd6"; private const val dropLine = "#1757ad"
    private const val iceA = "#e3f7ff"; private const val iceB = "#7fd0f0"; private const val iceLine = "#3b93bd"
    private const val flake = "#3f8fe0"; private const val hail = "#ffffff"; private const val hailLine = "#5a8cc4"
    private const val boltA = "#ffe35c"; private const val boltB = "#f59e0b"; private const val boltLine = "#a85a06"
    private const val fog = "#8b9db8"; private const val fogN = "#a3b2c9"; private const val wind = "#5d7ca6"; private const val windN = "#86a0c4"
    private const val hot = "#ef4444"; private const val hotLine = "#a8201f"; private const val cold = "#38a3f1"; private const val coldLine = "#1d6fb0"
    private const val tube = "#ffffff"; private const val tubeLine = "#6e83a3"

    /** liczba jak w szablonie JS: bez zbędnych zer (22, 16.75, 6.48) */
    private fun n(v: Double): String = String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }
    private fun n(v: Int) = v.toString()
    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

    private fun grad(id: String, a: String, b: String, x2: Int = 0, y2: Int = 1) =
        """<linearGradient id="$id" x1="0" y1="0" x2="$x2" y2="$y2"><stop offset="0" stop-color="$a"/><stop offset="1" stop-color="$b"/></linearGradient>"""

    private fun cloudPath(c1: DoubleArray, c2: DoubleArray, c3: DoubleArray, base: Double): String {
        fun inter(a: DoubleArray, b: DoubleArray): DoubleArray {
            val dx = b[0] - a[0]; val dy = b[1] - a[1]; val d = hypot(dx, dy)
            val aa = (a[2] * a[2] - b[2] * b[2] + d * d) / (2 * d); val h = sqrt(max(0.0, a[2] * a[2] - aa * aa))
            val mx = a[0] + aa * dx / d; val my = a[1] + aa * dy / d
            val p1 = doubleArrayOf(mx + h * dy / d, my - h * dx / d); val p2 = doubleArrayOf(mx - h * dy / d, my + h * dx / d)
            return if (p1[1] < p2[1]) p1 else p2
        }
        fun fr(v: Double) = n(round(v * 100) / 100)
        fun arc(c: DoubleArray, from: DoubleArray, to: DoubleArray): String {
            val s = atan2(from[1] - c[1], from[0] - c[0]); val e = atan2(to[1] - c[1], to[0] - c[0])
            var sw = e - s; while (sw < 0) sw += PI * 2
            return "A${n(c[2])} ${n(c[2])} 0 ${if (sw > PI) 1 else 0} 1 ${fr(to[0])} ${fr(to[1])}"
        }
        val p0 = doubleArrayOf(c1[0], base); val p12 = inter(c1, c2); val p23 = inter(c2, c3); val p3 = doubleArrayOf(c3[0], base)
        return "M${fr(p0[0])} ${fr(p0[1])}${arc(c1, p0, p12)}${arc(c2, p12, p23)}${arc(c3, p23, p3)}Z"
    }

    private fun cloud(x: Double, base: Double, s: Double, fill: String, line: String): String {
        fun p(dx: Double, dy: Double, r: Double) = doubleArrayOf(x + dx * s, base + dy * s, r * s)
        val d = cloudPath(p(-13.0, -9.0, 9.0), p(-1.0, -15.5, 12.5), p(13.0, -10.0, 10.0), base)
        return """<path d="$d" fill="$fill" stroke="$line" stroke-width="1.6" stroke-linejoin="round"/>"""
    }

    private fun sun(cx: Double, cy: Double, r: Double, withRays: Boolean = true): String {
        var rays = ""
        if (withRays) {
            val r1 = r + 4.2; val r2 = r + 9
            val d = StringBuilder()
            for (i in 0 until 8) {
                val a = PI * i / 4
                d.append("M${f2(cx + cos(a) * r1)} ${f2(cy + sin(a) * r1)}L${f2(cx + cos(a) * r2)} ${f2(cy + sin(a) * r2)}")
            }
            rays = """<path d="$d" stroke="$ray" stroke-width="4" stroke-linecap="round"/>"""
        }
        return rays + """<circle cx="${n(cx)}" cy="${n(cy)}" r="${n(r)}" fill="url(#sun-t)" stroke="$sunLine" stroke-width="1.6"/>"""
    }

    private fun moon(cx: Double, cy: Double, r: Double): String {
        val ox = r * 0.62; val oy = -r * 0.5; val rc = r * 0.86
        val bx = cx + ox; val by = cy + oy
        val dx = bx - cx; val dy = by - cy; val d = hypot(dx, dy)
        val a = (r * r - rc * rc + d * d) / (2 * d); val h = sqrt(r * r - a * a)
        val mx = cx + a * dx / d; val my = cy + a * dy / d
        val p1x = mx + h * dy / d; val p1y = my - h * dx / d; val p2x = mx - h * dy / d; val p2y = my + h * dx / d
        return """<path d="M${f2(p1x)} ${f2(p1y)}A${n(r)} ${n(r)} 0 1 0 ${f2(p2x)} ${f2(p2y)}A${n(rc)} ${n(rc)} 0 0 1 ${f2(p1x)} ${f2(p1y)}Z" fill="url(#moon-t)" stroke="$moonLine" stroke-width="1.6" stroke-linejoin="round"/>"""
    }

    private fun starP(x: Double, y: Double, k: Double = 1.0): String =
        """<path d="M${n(x)} ${n(y - 3.4 * k)}Q${n(x + 0.7 * k)} ${n(y - 0.7 * k)} ${n(x + 3.4 * k)} ${n(y)}Q${n(x + 0.7 * k)} ${n(y + 0.7 * k)} ${n(x)} ${n(y + 3.4 * k)}Q${n(x - 0.7 * k)} ${n(y + 0.7 * k)} ${n(x - 3.4 * k)} ${n(y)}Q${n(x - 0.7 * k)} ${n(y - 0.7 * k)} ${n(x)} ${n(y - 3.4 * k)}Z" fill="$star"/>"""

    private fun drop(x: Double, y: Double, k: Double = 1.0, ice: Boolean = false): String {
        val line = if (ice) iceLine else dropLine
        val d = "M0 -6.2C1.4 -3.9 3.2 -1.7 3.2 0.6A3.2 3.2 0 0 1 -3.2 0.6C-3.2 -1.7 -1.4 -3.9 0 -6.2Z"
        return """<path transform="translate(${n(x)} ${n(y)}) rotate(14) scale(${n(k)})" d="$d" fill="url(#${if (ice) "ice" else "drop"}-t)" stroke="$line" stroke-width="${f2(1.2 / k)}" stroke-linejoin="round"/>"""
    }

    private fun flakeP(x: Double, y: Double, k: Double = 1.0): String {
        val r = 4.2 * k
        val d = StringBuilder()
        for (i in 0 until 3) {
            val a = PI / 2 + i * PI / 3
            val dx = cos(a) * r; val dy = sin(a) * r
            d.append("M${f2(x - dx)} ${f2(y - dy)}L${f2(x + dx)} ${f2(y + dy)}")
        }
        return """<path d="$d" stroke="#ffffff" stroke-width="${f2(4.6 * k)}" stroke-linecap="round"/><path d="$d" stroke="$flake" stroke-width="${f2(2.4 * k)}" stroke-linecap="round"/>"""
    }

    private fun bigFlake(x: Double, y: Double, r: Double): String {
        val d = StringBuilder()
        for (i in 0 until 6) {
            val a = -PI / 2 + i * PI / 3; val c = cos(a); val s = sin(a)
            d.append("M${n(x)} ${n(y)}L${f2(x + c * r)} ${f2(y + s * r)}")
            val bx = x + c * r * 0.58; val by = y + s * r * 0.58; val b = r * 0.3
            for (t in listOf(-1, 1)) {
                val a2 = a + t * PI / 4
                d.append("M${f2(bx)} ${f2(by)}L${f2(bx + cos(a2) * b)} ${f2(by + sin(a2) * b)}")
            }
        }
        return """<g stroke-linecap="round" stroke-linejoin="round"><path d="$d" stroke="#ffffff" stroke-width="6.4"/><path d="$d" stroke="$flake" stroke-width="3.2"/></g>"""
    }

    private fun hailP(x: Double, y: Double) = """<circle cx="${n(x)}" cy="${n(y)}" r="3.3" fill="$hail" stroke="$hailLine" stroke-width="1.6"/>"""

    private fun thermo(x: Double, y: Double, goraco: Boolean): String {
        val col = if (goraco) hot else cold; val line = if (goraco) hotLine else coldLine
        val lvl = if (goraco) 6.0 else 17.0
        return """<rect x="${n(x - 4)}" y="${n(y)}" width="8" height="26" rx="4" fill="$tube" stroke="$tubeLine" stroke-width="1.6"/>""" +
            """<circle cx="${n(x)}" cy="${n(y + 29)}" r="6.4" fill="$col" stroke="$line" stroke-width="1.6"/>""" +
            """<rect x="${n(x - 1.6)}" y="${n(y + lvl)}" width="3.2" height="${n(26 - lvl + 2)}" rx="1.6" fill="$col"/>""" +
            """<path d="M${n(x + 4)} ${n(y + 6)}h3M${n(x + 4)} ${n(y + 11)}h3M${n(x + 4)} ${n(y + 16)}h3" stroke="$tubeLine" stroke-width="1.6" stroke-linecap="round"/>"""
    }

    /** _draw(state, night): SVG ikony 64×64 (z definicjami gradientów). */
    fun svg(stan: String, noc: Boolean): String {
        val defs = StringBuilder()
        val uzyte = HashSet<String>()
        fun use(k: String) {
            if (!uzyte.add(k)) return
            defs.append(
                when (k) {
                    "sun" -> """<radialGradient id="sun-t" cx="40%" cy="36%" r="70%"><stop offset="0" stop-color="$sunA"/><stop offset="0.6" stop-color="$sunB"/><stop offset="1" stop-color="$sunC"/></radialGradient>"""
                    "moon" -> grad("moon-t", moonA, moonB, 1, 1)
                    "cloud" -> if (noc) grad("cloud-t", nCloudA, nCloudB) else grad("cloud-t", cloudA, cloudB)
                    "back" -> grad("back-t", backA, backB)
                    "dark" -> grad("dark-t", stormA, stormB)
                    "drop" -> grad("drop-t", dropA, dropB)
                    "ice" -> grad("ice-t", iceA, iceB)
                    "bolt" -> grad("bolt-t", boltA, boltB)
                    else -> ""
                },
            )
        }
        fun cl(): Pair<String, String> { use("cloud"); return "url(#cloud-t)" to (if (noc) nCloudLine else cloudLine) }
        fun companion(x: Double, y: Double, r: Double): String =
            if (noc) { use("moon"); moon(x, y, r) } else { use("sun"); sun(x, y, r * 0.8) }
        fun rainCloud(dark: Boolean): String {
            val comp = companion(44.0, 17.0, 9.0)
            if (dark) { use("dark"); return comp + cloud(30.0, 40.0, 1.0, "url(#dark-t)", stormLine) }
            val c = cl()
            return comp + cloud(30.0, 40.0, 1.0, c.first, c.second)
        }
        val g = when (stan) {
            "clear" -> if (noc) { use("moon"); moon(30.0, 33.0, 17.0) + starP(49.0, 14.0, 1.15) + starP(54.0, 29.0, 0.75) + starP(39.0, 9.0, 0.6) }
            else { use("sun"); sun(32.0, 32.0, 13.0) }
            "mostly-clear" -> {
                val c = cl()
                (if (noc) { use("moon"); moon(27.0, 27.0, 15.0) + starP(50.0, 12.0, 0.9) } else { use("sun"); sun(28.0, 27.0, 11.5) }) +
                    cloud(42.0, 53.0, 0.62, c.first, c.second)
            }
            "partly-cloudy" -> {
                val c = cl()
                (if (noc) { use("moon"); moon(24.0, 22.0, 12.5) + starP(46.0, 10.0, 0.8) } else { use("sun"); sun(24.0, 22.0, 9.5) }) +
                    cloud(35.0, 51.0, 0.88, c.first, c.second)
            }
            "overcast" -> {
                use("back"); val c = cl()
                (if (noc) starP(12.0, 13.0, 0.75) else "") + cloud(41.0, 36.0, 0.72, "url(#back-t)", backLine) + cloud(29.0, 50.0, 1.0, c.first, c.second)
            }
            "fog" -> {
                val c = cl(); val fc = if (noc) fogN else fog
                companion(44.0, 15.0, 9.0) + cloud(29.0, 36.0, 0.9, c.first, c.second) +
                    """<g stroke-linecap="round"><path d="M11 43h36" stroke="$fc" stroke-width="4"/><path d="M19 51h34" stroke="$fc" stroke-width="4"/><path d="M13 59h26" stroke="$fc" stroke-width="4"/></g>"""
            }
            "drizzle" -> { use("drop"); rainCloud(false) + drop(21.0, 48.0, 0.62) + drop(31.0, 53.0, 0.62) + drop(41.0, 48.0, 0.62) + drop(26.0, 59.0, 0.55) + drop(36.0, 60.0, 0.55) }
            "rain-light" -> { use("drop"); rainCloud(false) + drop(24.0, 51.0) + drop(37.0, 55.0) }
            "rain" -> { use("drop"); rainCloud(false) + drop(20.0, 51.0) + drop(31.0, 55.0) + drop(42.0, 51.0) }
            "rain-heavy" -> {
                use("drop")
                rainCloud(true) + drop(17.0, 50.0, 1.05) + drop(27.0, 54.0, 1.05) + drop(37.0, 50.0, 1.05) + drop(47.0, 54.0, 1.05) +
                    drop(22.0, 60.0, 0.8) + drop(42.0, 60.0, 0.8)
            }
            "freezing-rain" -> {
                use("ice")
                rainCloud(false) + drop(21.0, 50.0, 0.95, true) + drop(32.0, 53.0, 0.95, true) + drop(43.0, 50.0, 0.95, true) +
                    """<path d="M13 60.5h38" stroke="$iceLine" stroke-width="4" stroke-linecap="round"/><path d="M13 60.5h38" stroke="$iceA" stroke-width="1.6" stroke-linecap="round"/>"""
            }
            "snow" -> rainCloud(false) + flakeP(20.0, 51.0) + flakeP(32.0, 56.0) + flakeP(44.0, 51.0)
            "sleet" -> { use("drop"); rainCloud(false) + drop(21.0, 51.0) + flakeP(32.0, 56.0) + drop(43.0, 51.0) }
            "hail" -> rainCloud(true) + hailP(20.0, 50.0) + hailP(31.0, 55.0) + hailP(42.0, 50.0) + hailP(25.0, 60.0) + hailP(37.0, 60.0)
            "storm" -> {
                use("bolt"); use("drop")
                rainCloud(true) +
                    """<path d="M33.5 40L24 53.5h7L27.5 63L40.5 48h-7.2L37.5 40Z" fill="url(#bolt-t)" stroke="$boltLine" stroke-width="1.4" stroke-linejoin="round"/>""" +
                    drop(17.0, 50.0, 0.85) + drop(46.0, 50.0, 0.85)
            }
            "wind" -> {
                val wc = if (noc) windN else wind
                companion(48.0, 14.0, 8.0) +
                    """<g fill="none" stroke-linecap="round" stroke-linejoin="round">""" +
                    """<path d="M7 27h29a6.5 6.5 0 1 0-6.5-6.5" stroke="#ffffff" stroke-width="7"/><path d="M7 27h29a6.5 6.5 0 1 0-6.5-6.5" stroke="$wc" stroke-width="4"/>""" +
                    """<path d="M7 38h41a7 7 0 1 1-7 7" stroke="#ffffff" stroke-width="7"/><path d="M7 38h41a7 7 0 1 1-7 7" stroke="$wc" stroke-width="4"/>""" +
                    """<path d="M12 49h17a5 5 0 1 1-5 5" stroke="#ffffff" stroke-width="7"/><path d="M12 49h17a5 5 0 1 1-5 5" stroke="$wc" stroke-width="4"/></g>"""
            }
            "heat" -> (if (noc) { use("moon"); moon(24.0, 28.0, 15.0) } else { use("sun"); sun(23.0, 28.0, 11.0) }) + thermo(50.0, 22.0, true)
            "frost" -> (if (noc) { use("moon"); moon(49.0, 11.0, 7.0) } else "") + bigFlake(24.0, 31.0, 15.0) + thermo(50.0, 22.0, false)
            else -> { use("sun"); sun(32.0, 32.0, 13.0) }
        }
        return """<svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg"><defs>$defs</defs><g>$g</g></svg>"""
    }

    private val pamiec = LruCache<String, ImageBitmap>(48)

    /** Ikona jako obrazek [px]×[px] (pamięć według stanu, nocy i rozmiaru). */
    fun obraz(stan: String, noc: Boolean, px: Int): ImageBitmap? {
        val klucz = "$stan|$noc|$px"
        pamiec.get(klucz)?.let { return it }
        return runCatching {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            // miękki cień w granacie marki jak feDropShadow na stronie (dy 1,2, przezroczystość 0,28)
            val cien = svg(stan, noc).replace(Regex("""(fill|stroke)="(?!none)[^"]*""""), "$1=\"#0a1f44\"")
            val dy = px * 1.2f / 64f
            val warstwa = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            com.caverock.androidsvg.SVG.getFromString(cien).renderToCanvas(android.graphics.Canvas(warstwa), RectF(0f, dy, px.toFloat(), px + dy))
            c.drawBitmap(warstwa, 0f, 0f, android.graphics.Paint().apply { alpha = (0.28f * 255).toInt() })
            warstwa.recycle()
            com.caverock.androidsvg.SVG.getFromString(svg(stan, noc)).renderToCanvas(c, RectF(0f, 0f, px.toFloat(), px.toFloat()))
            bmp.asImageBitmap()
        }.getOrNull()?.also { pamiec.put(klucz, it) }
    }
}

/** Ikona pogody w stylu tvml.pl: [klucz] z API (state / icon), noc, opis i temperatura doprecyzowują stan jak na stronie. */
@Composable
fun IkonaPogodyStrony(klucz: String, noc: Boolean, rozmiar: Dp, modifier: Modifier = Modifier, opis: String? = null, temp: Double? = null) {
    val px = with(LocalDensity.current) { rozmiar.roundToPx() }.coerceAtLeast(1)
    val (stan, wariantNocny) = IkonyPogodyStrony.stan(klucz, noc, opis, temp)
    val obraz = remember(stan, wariantNocny, px) { IkonyPogodyStrony.obraz(stan, wariantNocny, px) }
    if (obraz != null) Image(obraz, null, modifier.size(rozmiar).clearAndSetSemantics { })
}
