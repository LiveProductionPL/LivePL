package pl.tvml

import android.content.res.Resources
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Nakładka cząstek pogody ze strony tvml.pl: renderer z public/assets/js/pogoda-efekty.js przeniesiony linia
 * w linię na [Plotno] (nazwy funkcji i pól jak w JS, żeby dało się porównać z oryginałem).
 *
 * Z nagłówka pliku JS: wspólne animacje tła wg AKTUALNEJ pogody. Efekty: clear-day (słońce, promienie), clear-night
 * (gwiazdy + księżyc), clouds (chmury), fog (warstwy mgły, bez gwiazd), drizzle / rain / rain-heavy (krople, gęstość
 * wg natężenia), storm (deszcz + łagodny błysk, najwyżej 1 na 10 s), snow (płatki), sleet (krople + płatki), wind
 * (skośne smugi, jesienią liście). Jesienne liście są tylko dodatkiem przy suchej pogodzie jesienią. Pora dnia
 * (day/dusk/night) zmienia kolory. Najwyżej 120 cząstek (na telefonie ok. 60%), przy ograniczonym ruchu jedna
 * nieruchoma klatka, bez błysków.
 *
 * Rysuje się po krajobrazie sceny, tylko na scenach niespecjalnych ([ScenaStrony]). Stan cząstek trzyma instancja
 * (w JS: osobno dla każdego canvasu) i buduje go od nowa, gdy zmieni się podpis: efekt, natężenie, wiatr, liście,
 * noc, telefon albo rozmiar (co 40 dp). Ruch liczy się z czasu (krok najwyżej 50 ms), nie z liczby klatek.
 */
class NakladkaPogody {

    /** Opis efektu jak obiekt fx w JS (zDanych): effect, intensity, isNight, windy, leaves, phase. */
    private class Fx(
        val effect: String, val intensity: Double, val isNight: Boolean, val windy: Boolean, val leaves: Boolean,
        var phase: String = "day",
    )

    /** Co i ile rysować (plan(fx, mobile) w JS). */
    private class Plan {
        var sun = false
        var moon = false
        var stars = 0
        var dust = 0
        var clouds = 0
        var dark = false
        var fog = 0
        var drops = 0
        var flakes = 0
        var streaks = 0
        var leaves = 0
        var flash = false
    }

    /** Kolor „r,g,b” z palety; [a] daje to samo co szablon `rgba(${col},${a})` w JS. */
    private class Rgb(val r: Int, val g: Int, val b: Int) {
        fun a(alfa: Double): Int = Plotno.rgba(r, g, b, alfa)
    }

    /** Paleta pory dnia (PALETTE w JS). */
    private class Paleta(
        val cloud: Rgb, val storm: Rgb, val fog: Rgb, val drop: Rgb, val flake: Rgb, val streak: Rgb, val tint: String?,
    )

    // Cząstki (pola jak w JS)
    private class Gwiazda(val x: Double, val y: Double, val r: Double, val ph: Double, val sp: Double)
    private class Pyl(var x: Double, var y: Double, val r: Double, val vy: Double, val ph: Double)
    /** Chmura; [obraz] to jej kłęby narysowane raz (gradienty jak na stronie) w kolorze [obrazKolor]. */
    private class Chmura(var x: Double, val y: Double, val s: Double, val vx: Double, val a: Double) {
        var obraz: android.graphics.Bitmap? = null
        var obrazKolor = 0
    }
    /** Pasmo mgły; [obraz] to jego owal narysowany raz, dla szerokości płótna [obrazW]. */
    private class PasMgly(val y: Double, val hh: Double, var x: Double, val vx: Double, val a: Double) {
        var obraz: android.graphics.Bitmap? = null
        var obrazKolor = 0
        var obrazW = 0.0
    }
    private class Kropla(var x: Double, var y: Double, val v: Double, val l: Double, val a: Double, val wd: Double, val sl: Double)
    private class Platek(var x: Double, var y: Double, val r: Double, val v: Double, val ph: Double, val a: Double, val dx: Double)
    private class Smuga(var x: Double, var y: Double, val l: Double, val v: Double, val a: Double)
    private class Lisc(
        var x: Double, var y: Double, val vx: Double, val vy: Double, var rot: Double, val vr: Double, val c: String, val ph: Double,
    )

    /** Stan cząstek (st w JS). */
    private class Stan(val sig: String, var last: Double) {
        var frames = 0
        var plan = Plan()
        var stars: List<Gwiazda> = emptyList()
        var dust: List<Pyl> = emptyList()
        var clouds: List<Chmura> = emptyList()
        var fog: List<PasMgly> = emptyList()
        var drops: List<Kropla> = emptyList()
        var flakes: List<Platek> = emptyList()
        var streaks: List<Smuga> = emptyList()
        var leaves: List<Lisc> = emptyList()
        var flashes = 0
        var flash = 0.0
        var flashT = 0.0
        var nextFlash = 0.0
    }

    private var st: Stan? = null

    /** One frame. tMs = milliseconds (monotonic). ruch=false → one static frame (reduced motion). */
    fun rysuj(ctx: Plotno, w: Double, h: Double, tMs: Double, e: EfektPogody, noc: Boolean, ruch: Boolean) {
        if (!(w > 0.0) || !(h > 0.0)) return
        val fx = zDanych(e, noc)
        val staticMode = !ruch
        val now = tMs
        val mobile = isMobile()
        val sig = listOf(
            fx.effect, (fx.intensity * 10).roundToInt(), if (fx.windy) 1 else 0, if (fx.leaves) 1 else 0,
            if (fx.isNight) 1 else 0, if (mobile) 1 else 0, (w / 40).roundToInt(), (h / 40).roundToInt(),
        ).joinToString("|")
        val s = st?.takeIf { it.sig == sig } ?: Stan(sig, now).also {
            build(it, fx, w, h, mobile, now)
            st = it
        }
        val dt = if (staticMode) 0.0 else min(0.05, max(0.0, (now - s.last) / 1000.0))
        s.last = now
        s.frames++
        val phase = fx.phase
        val pal = PALETTE[phase] ?: PALETTE.getValue("day")
        val p = s.plan

        ctx.save()
        if (pal.tint != null) { ctx.fillStyle = pal.tint; ctx.fillRect(0.0, 0.0, w, h) }
        // Zasłona nieba przy zachmurzeniu i opadach (scena ma własne niebo — przyciemniamy/szarzymy górę).
        val veil = when (fx.effect) {
            "clouds" -> if (fx.intensity >= 0.6) 0.28 else 0.0
            "drizzle" -> 0.3
            "rain" -> 0.38
            "rain-heavy" -> 0.48
            "storm" -> 0.55
            "snow" -> 0.4
            "sleet" -> 0.42
            "fog" -> 0.3
            else -> 0.0
        }
        if (veil != 0.0 && "zaslona" !in ScenyStrony.wylaczone) {
            val col = if (fx.effect == "snow" || fx.effect == "fog") pal.fog else (if (p.dark) pal.storm else pal.cloud)
            val g = ctx.createLinearGradient(0.0, 0.0, 0.0, h * 0.7)
            g.addColorStop(0.0, col.a(veil)); g.addColorStop(1.0, col.a(0.0))
            ctx.fillStyle = g; ctx.fillRect(0.0, 0.0, w, h * 0.7)
        }

        drawSky(ctx, s, w, h, now, dt, fx, phase, staticMode)
        drawCloudsAndFog(ctx, s, w, h, dt, fx, pal)
        drawPrecipitation(ctx, s, w, h, now, dt, pal)
        if ("liscie" !in ScenyStrony.wylaczone) drawLeaves(ctx, s, w, h, now, dt, fx)

        // Burza: łagodny błysk (bez stroboskopu), najwyżej raz na 10 s, wyłączony przy ograniczonym ruchu
        if (p.flash && !staticMode) {
            if (s.flashT == 0.0 && now >= s.nextFlash) {
                s.flashT = now; s.flashes++; s.nextFlash = now + 10000.0 + Math.random() * 8000.0
            }
            if (s.flashT != 0.0) {
                val age = now - s.flashT
                if (age > 600.0) {
                    s.flashT = 0.0
                } else {
                    s.flash = 0.2 * (1.0 - age / 600.0)
                    ctx.fillStyle = Plotno.rgba(235, 240, 255, s.flash); ctx.fillRect(0.0, 0.0, w, h)
                }
            }
        }
        ctx.restore()
    }

    /** Gwiazdy, księżyc, słońce i pył w słońcu. */
    private fun drawSky(ctx: Plotno, s: Stan, w: Double, h: Double, now: Double, dt: Double, fx: Fx, phase: String, staticMode: Boolean) {
        // Gwiazdy (tylko bezchmurna noc / małe zachmurzenie nocą)
        for (g in s.stars) {
            val tw = if (staticMode) 0.7 else 0.45 + 0.55 * abs(sin(now * 0.001 * g.sp + g.ph))
            ctx.fillStyle = Plotno.rgba(255, 252, 240, (if (fx.effect == "clouds") 0.5 else 0.9) * tw)
            ctx.beginPath(); ctx.arc(g.x, g.y, g.r, 0.0, PI * 2); ctx.fill()
        }
        if (s.plan.moon) drawMoon(ctx, w, h)
        if (s.plan.sun) drawSun(ctx, w, h, now, phase, staticMode)

        // Pył w słońcu
        for (d in s.dust) {
            d.y += d.vy * dt; d.x += sin(now * 0.0006 + d.ph) * 4.0 * dt
            if (d.y < -4.0) { d.y = h + 4.0; d.x = Math.random() * w }
            ctx.fillStyle = Plotno.rgba(255, 236, 160, if (staticMode) 0.35 else 0.2 + 0.25 * abs(sin(now * 0.0012 + d.ph)))
            ctx.beginPath(); ctx.arc(d.x, d.y, d.r, 0.0, PI * 2); ctx.fill()
        }
    }

    /** Chmury i pasma mgły. */
    private fun drawCloudsAndFog(ctx: Plotno, s: Stan, w: Double, h: Double, dt: Double, fx: Fx, pal: Paleta) {
        // Chmury
        val cloudCol = if (s.plan.dark) pal.storm else pal.cloud
        if ("chmury" !in ScenyStrony.wylaczone) for (c in s.clouds) {
            c.x += c.vx * dt
            if (c.x - c.s * 2 > w) c.x = -c.s * 2
            drawCloud(ctx, c, cloudCol)
        }

        // Mgła: szerokie, półprzezroczyste pasma przesuwające się powoli w obie strony
        for (f in s.fog) {
            f.x += f.vx * dt
            if (f.x > w) f.x -= w
            if (f.x < 0.0) f.x += w
            // na stronie: koło z gradientem promienistym (a, 0,6 → a/2, 1 → 0) ściśnięte w pionie do wysokości pasma.
            // Tu ten sam owal narysowany raz do obrazka, a w klatce tylko przesuwany (gradienty promieniste w każdej
            // klatce wywracały grafikę emulatora, także na CI)
            val r = w * 0.6
            val obraz = obrazMgly(f, r, pal.fog.a(1.0), pal.fog)
            if ("mgla" !in ScenyStrony.wylaczone) for (k in -1..1) {
                val cx = f.x + k * w
                ctx.obraz(obraz, cx - r, f.y - f.hh, 2 * r, 2 * f.hh)
            }
        }
        if (s.plan.fog != 0 && "dol" !in ScenyStrony.wylaczone) {
            val g = ctx.createLinearGradient(0.0, h * 0.35, 0.0, h)
            g.addColorStop(0.0, pal.fog.a(0.0)); g.addColorStop(1.0, pal.fog.a(0.22 + fx.intensity * 0.18))
            ctx.fillStyle = g; ctx.fillRect(0.0, 0.0, w, h)
        }
    }

    /** Smugi wiatru, krople deszczu i płatki śniegu. */
    private fun drawPrecipitation(ctx: Plotno, s: Stan, w: Double, h: Double, now: Double, dt: Double, pal: Paleta) {
        // Wiatr: skośne smugi
        ctx.lineCap = "round"
        for (k in s.streaks) {
            k.x += k.v * dt; k.y += k.v * 0.12 * dt
            if (k.x - k.l > w) { k.x = -rnd(0.0, w * 0.3); k.y = Math.random() * h }
            if (k.y > h) k.y = 0.0
            ctx.strokeStyle = pal.streak.a(k.a); ctx.lineWidth = 1.4
            ctx.beginPath(); ctx.moveTo(k.x, k.y); ctx.quadraticCurveTo(k.x - k.l * 0.5, k.y - 4.0, k.x - k.l, k.y - k.l * 0.12); ctx.stroke()
        }

        // Krople deszczu / mżawki
        for (d in s.drops) {
            d.y += d.v * dt; d.x += d.v * d.sl * dt
            if (d.y > h) { d.y = -d.l - Math.random() * 20.0; d.x = Math.random() * (w + 80.0) - 40.0 - (d.sl * h * 0.5) }
            ctx.strokeStyle = pal.drop.a(d.a); ctx.lineWidth = d.wd
            ctx.beginPath(); ctx.moveTo(d.x, d.y); ctx.lineTo(d.x + d.l * d.sl, d.y + d.l); ctx.stroke()
        }

        // Płatki śniegu
        for (f in s.flakes) {
            f.y += f.v * dt; f.x += (sin(now * 0.0012 + f.ph) * 10.0 + f.dx) * dt
            if (f.y > h + 4.0) { f.y = -4.0; f.x = Math.random() * w }
            if (f.x > w + 4.0) f.x = -4.0
            ctx.fillStyle = pal.flake.a(f.a)
            ctx.beginPath(); ctx.arc(f.x, f.y, f.r, 0.0, PI * 2); ctx.fill()
        }
    }

    /** Jesienne liście (dodatek przy suchej pogodzie jesienią). */
    private fun drawLeaves(ctx: Plotno, s: Stan, w: Double, h: Double, now: Double, dt: Double, fx: Fx) {
        for (l in s.leaves) {
            l.x += l.vx * dt; l.y += (l.vy + sin(now * 0.002 + l.ph) * 10.0) * dt; l.rot += l.vr * dt
            if (l.x > w + 10.0) l.x = -10.0
            if (l.y > h + 10.0) { l.y = -10.0; l.x = Math.random() * w }
            ctx.save(); ctx.translate(l.x, l.y); ctx.rotate(l.rot)
            ctx.globalAlpha = if (fx.isNight) 0.55 else 0.85; ctx.fillStyle = l.c
            ctx.beginPath(); ctx.ellipse(0.0, 0.0, 4.4, 2.1, 0.0, 0.0, PI * 2); ctx.fill()
            ctx.restore()
        }
    }

    /** Cząstki od nowa dla efektu i rozmiaru (build w JS). */
    private fun build(st: Stan, fx: Fx, w: Double, h: Double, mobile: Boolean, now: Double) {
        val p = plan(fx, mobile)
        st.plan = p
        val windX = if (fx.windy || fx.effect == "wind") 1.0 else 0.0
        st.stars = List(p.stars) { Gwiazda(Math.random() * w, Math.random() * h * 0.62, rnd(0.5, 1.5), rnd(0.0, 6.28), rnd(0.6, 1.8)) }
        st.dust = List(p.dust) { Pyl(Math.random() * w, Math.random() * h, rnd(0.8, 2.0), -rnd(4.0, 10.0), rnd(0.0, 6.28)) }
        st.clouds = List(p.clouds) { i ->
            Chmura(
                x = Math.random() * w, y = rnd(0.04, 0.32) * h, s = rnd(0.7, 1.25) * max(40.0, min(w, 900.0) * 0.11),
                vx = rnd(4.0, 9.0) * (1 + windX * 1.5) * (if (i % 2 != 0) 1.0 else 0.75), a = rnd(0.55, 0.85) * (0.5 + fx.intensity * 0.5),
            )
        }
        st.fog = List(p.fog) { i ->
            PasMgly(
                y = h * (0.18 + i * 0.17) + rnd(-8.0, 8.0), hh = h * rnd(0.22, 0.34), x = rnd(0.0, w),
                vx = (if (i % 2 != 0) 1.0 else -1.0) * rnd(5.0, 11.0), a = rnd(0.16, 0.3) * (0.6 + fx.intensity * 0.6),
            )
        }
        val heavy = fx.effect == "rain-heavy" || fx.effect == "storm"
        val slant = if (fx.windy) 0.35 else 0.12
        st.drops = List(p.drops) {
            val z = Math.random()
            val v = (if (fx.effect == "drizzle") 260.0 else if (heavy) 620.0 else 460.0) * (0.7 + z * 0.5)
            Kropla(
                x = Math.random() * (w + 80.0) - 40.0, y = Math.random() * h, v = v,
                l = (if (fx.effect == "drizzle") 6.0 else if (heavy) 16.0 else 11.0) * (0.7 + z * 0.6),
                a = 0.25 + z * 0.45, wd = 0.8 + z * 0.8, sl = slant,
            )
        }
        st.flakes = List(p.flakes) {
            val z = Math.random()
            Platek(
                x = Math.random() * w, y = Math.random() * h, r = 1 + z * 2.6, v = 18 + z * 34, ph = rnd(0.0, 6.28),
                a = 0.5 + z * 0.45, dx = if (fx.windy) 30.0 else 8.0,
            )
        }
        st.streaks = List(p.streaks) {
            Smuga(Math.random() * w, Math.random() * h, rnd(40.0, 110.0), rnd(160.0, 300.0) * (0.7 + fx.intensity * 0.5), rnd(0.18, 0.4))
        }
        st.leaves = List(p.leaves) { i ->
            Lisc(
                x = Math.random() * w, y = Math.random() * h,
                vx = if (fx.effect == "wind") rnd(70.0, 130.0) else rnd(8.0, 22.0), vy = rnd(14.0, 30.0),
                rot = rnd(0.0, 6.28), vr = rnd(-1.6, 1.6), c = LEAF[i % LEAF.size], ph = rnd(0.0, 6.28),
            )
        }
        st.flashes = 0; st.flash = 0.0; st.flashT = 0.0
        st.nextFlash = now + rnd(4000.0, 9000.0)
    }

    private fun drawSun(ctx: Plotno, w: Double, h: Double, t: Double, phase: String, staticMode: Boolean) {
        val x = w - min(90.0, w * 0.14)
        val y = min(46.0, h * 0.22)
        val r = max(12.0, min(20.0, h * 0.09))
        val warm = if (phase == "dusk") Rgb(255, 170, 110) else Rgb(255, 226, 140)
        ctx.save()
        ctx.translate(x, y)
        ctx.rotate(if (staticMode) 0.0 else t * 0.00004)
        ctx.fillStyle = warm.a(0.10)
        repeat(8) {
            ctx.rotate(PI / 4)
            ctx.beginPath(); ctx.moveTo(0.0, 0.0); ctx.lineTo(-r * 0.6, -r * 3.4); ctx.lineTo(r * 0.6, -r * 3.4); ctx.closePath(); ctx.fill()
        }
        ctx.restore()
        val g = ctx.createRadialGradient(x, y, r * 0.3, x, y, r * 3.2)
        g.addColorStop(0.0, warm.a(0.55)); g.addColorStop(0.4, warm.a(0.2)); g.addColorStop(1.0, warm.a(0.0))
        ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, r * 3.2, 0.0, PI * 2); ctx.fill()
        ctx.fillStyle = if (phase == "dusk") "#ffd2a0" else "#fff2b0"
        ctx.beginPath(); ctx.arc(x, y, r, 0.0, PI * 2); ctx.fill()
    }

    private fun drawMoon(ctx: Plotno, w: Double, h: Double) {
        val x = w - min(90.0, w * 0.14)
        val y = min(44.0, h * 0.22)
        val r = max(10.0, min(16.0, h * 0.075))
        val g = ctx.createRadialGradient(x, y, r * 0.5, x, y, r * 3.6)
        g.addColorStop(0.0, "rgba(240,236,210,0.22)"); g.addColorStop(1.0, "rgba(240,236,210,0)")
        ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, r * 3.6, 0.0, PI * 2); ctx.fill()
        // Sierp: tarcza minus przesunięte koło (evenodd w obrębie tarczy) — bez wycinania tła sceny.
        ctx.save()
        ctx.beginPath(); ctx.arc(x, y, r, 0.0, PI * 2); ctx.clip()
        ctx.beginPath(); ctx.rect(x - r, y - r, r * 2, r * 2); ctx.arc(x - r * 0.55, y - r * 0.2, r * 0.95, 0.0, PI * 2)
        ctx.fillStyle = "#f3efd6"; ctx.fill("evenodd")
        ctx.restore()
    }

    /** Chmura z kłębów jak na stronie (drawCloud), narysowana raz do obrazka i tylko przesuwana w klatce. */
    private fun drawCloud(ctx: Plotno, c: Chmura, col: Rgb) {
        val g = GRANICE
        ctx.obraz(obrazChmury(c, col), c.x + g[0] * c.s, c.y + g[1] * c.s, (g[2] - g[0]) * c.s, (g[3] - g[1]) * c.s)
    }

    private fun obrazChmury(c: Chmura, col: Rgb): android.graphics.Bitmap {
        val kolor = col.a(1.0)
        c.obraz?.takeIf { c.obrazKolor == kolor }?.let { return it }
        val q = SKALA_OBRAZOW
        val g = GRANICE
        val b = android.graphics.Bitmap.createBitmap(
            ((g[2] - g[0]) * c.s * q).toInt().coerceAtLeast(1), ((g[3] - g[1]) * c.s * q).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888,
        )
        val cv = android.graphics.Canvas(b)
        for (k in BLOBS) {
            val cx = ((k[0] - g[0]) * c.s * q).toFloat()
            val cy = ((k[1] - g[1]) * c.s * q).toFloat()
            val br = (c.s * k[2] * q).toFloat().coerceAtLeast(1f)
            // createRadialGradient(bx, by, br * 0.1, bx, by, br): a, 0,55 → a/2, 1 → 0 (wewnętrzne koło 0,1 r pełne)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.shader = android.graphics.RadialGradient(
                cx, cy, br, intArrayOf(col.a(c.a), col.a(c.a), col.a(c.a * 0.5), col.a(0.0)), floatArrayOf(0f, 0.1f, 0.595f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
            cv.drawCircle(cx, cy, br, p)
        }
        c.obraz = b
        c.obrazKolor = kolor
        return b
    }

    /** Owal pasma mgły (2r × 2hh) z gradientem promienistym ściśniętym w pionie, jak na stronie, narysowany raz. */
    private fun obrazMgly(f: PasMgly, r: Double, kolor: Int, col: Rgb): android.graphics.Bitmap {
        f.obraz?.takeIf { f.obrazKolor == kolor && f.obrazW == r }?.let { return it }
        // mgła jest bardzo miękka: wystarczy pół piksela na dp, obraz i tak jest rozciągany z wygładzaniem
        val q = 0.5
        val wPx = (2 * r * q).toInt().coerceAtLeast(2)
        val hPx = (2 * f.hh * q).toInt().coerceAtLeast(2)
        val b = android.graphics.Bitmap.createBitmap(wPx, hPx, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(b)
        val rr = wPx / 2f
        cv.scale(1f, hPx / wPx.toFloat())
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.RadialGradient(
            rr, rr, rr, intArrayOf(col.a(f.a), col.a(f.a * 0.5), col.a(0.0)), floatArrayOf(0f, 0.6f, 1f), android.graphics.Shader.TileMode.CLAMP,
        )
        cv.drawCircle(rr, rr, rr, p)
        f.obraz = b
        f.obrazKolor = kolor
        f.obrazW = r
        return b
    }

    companion object {
        private val EFFECTS = setOf("clear-day", "clear-night", "clouds", "fog", "drizzle", "rain", "rain-heavy", "storm", "snow", "sleet", "wind")
        private const val MAX_PARTICLES = 120
        private val PHASES = setOf("day", "dusk", "night")
        private val LEAF_EFFECTS = setOf("clear-day", "clear-night", "clouds", "wind")

        private val PALETTE = mapOf(
            "day" to Paleta(
                cloud = Rgb(238, 242, 248), storm = Rgb(96, 106, 128), fog = Rgb(236, 240, 245), drop = Rgb(205, 222, 245),
                flake = Rgb(255, 255, 255), streak = Rgb(255, 255, 255), tint = null,
            ),
            "dusk" to Paleta(
                cloud = Rgb(244, 210, 196), storm = Rgb(92, 86, 112), fog = Rgb(236, 218, 214), drop = Rgb(218, 214, 238),
                flake = Rgb(255, 246, 242), streak = Rgb(255, 236, 226), tint = "rgba(255,150,90,0.07)",
            ),
            "night" to Paleta(
                cloud = Rgb(118, 132, 168), storm = Rgb(58, 66, 88), fog = Rgb(168, 180, 204), drop = Rgb(178, 198, 235),
                flake = Rgb(232, 240, 255), streak = Rgb(200, 214, 240), tint = null,
            ),
        )
        private val LEAF = listOf("#f2b705", "#e88d14", "#d9531e", "#b8291d")

        /** Kłęby chmury: [dx, dy, k] w jednostkach rozmiaru chmury. */
        private val BLOBS = listOf(
            doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(-0.9, 0.18, 0.7), doubleArrayOf(0.9, 0.2, 0.75),
            doubleArrayOf(0.35, -0.35, 0.72), doubleArrayOf(-0.4, -0.25, 0.6),
        )

        /** Granice kłębów chmury w jednostkach jej rozmiaru: minX, minY, maxX, maxY. */
        private val GRANICE: DoubleArray = BLOBS.fold(doubleArrayOf(1e9, 1e9, -1e9, -1e9)) { g, b ->
            doubleArrayOf(min(g[0], b[0] - b[2]), min(g[1], b[1] - b[2]), max(g[2], b[0] + b[2]), max(g[3], b[1] + b[2]))
        }

        /** Piksele obrazków chmur na dp: połowa gęstości ekranu (kłęby są miękkie, rozciąganie ich nie psuje). */
        private val SKALA_OBRAZOW: Double = try {
            (Resources.getSystem().displayMetrics.density * 0.5).coerceAtLeast(1.0)
        } catch (_: Exception) {
            1.0
        }

        private fun rnd(a: Double, b: Double): Double = a + Math.random() * (b - a)

        private fun round2(v: Double): Double = (max(0.0, min(1.0, if (v.isNaN()) 0.0 else v)) * 100).roundToInt() / 100.0

        /** Like gwiazdy(fx) in pogoda-efekty.js: whether the overlay shows stars for this weather. */
        fun gwiazdy(e: EfektPogody, noc: Boolean): Boolean = gwiazdyFx(zDanych(e, noc))

        /** Gwiazdy tylko przy (prawie) bezchmurnej nocy — nigdy we mgle, w deszczu, śniegu ani przy pełnym zachmurzeniu. */
        private fun gwiazdyFx(fx: Fx): Boolean =
            fx.effect == "clear-night" || (fx.effect == "clouds" && fx.isNight && fx.intensity < 0.5)

        /**
         * Efekt z danych pogody (zDanych w JS): gotowe pola serwera z /api/v1/weather. Gdy efekt jest nieznany,
         * ta sama mapa lokalnie (mapuj), ale aplikacja nie ma ikony ani opisu, więc wychodzi bezchmurne niebo.
         */
        private fun zDanych(e: EfektPogody, noc: Boolean): Fx {
            val fx = if (e.rodzaj in EFFECTS) {
                val night = noc
                var effect = e.rodzaj
                if (effect == "clear-day" && night) effect = "clear-night"
                if (effect == "clear-night" && !night) effect = "clear-day"
                Fx(effect, round2(e.intensywnosc.toDouble()), night, e.wietrznie, e.liscie)
            } else {
                mapujBezDanych(noc)
            }
            fx.phase = if (e.faza in PHASES) e.faza else poraDnia(fx.isNight)
            if (fx.phase == "night" && !fx.isNight) fx.phase = "day"
            if (fx.phase == "day" && fx.isNight) fx.phase = "night"
            return fx
        }

        /** mapuj() bez ikony, opisu, opadu i wiatru: kind() daje wtedy 'clear', liście tylko jesienią. */
        private fun mapujBezDanych(night: Boolean): Fx {
            val effect = if (night) "clear-night" else "clear-day"
            val leaves = isAutumn(warsawMd()) && effect in LEAF_EFFECTS
            return Fx(effect, round2(0.0), night, false, leaves)
        }

        /** Jak tvml_weather_effect_phase() bez godzin wschodu i zachodu: day | night. */
        private fun poraDnia(isNight: Boolean): String = if (isNight) "night" else "day"

        // Dzisiejsza data w Warszawie jako md = miesiąc*100 + dzień.
        private fun warsawMd(): Int = try {
            val d = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"))
            d.monthValue * 100 + d.dayOfMonth
        } catch (e: Exception) {
            val d = LocalDate.now()
            d.monthValue * 100 + d.dayOfMonth
        }

        private fun isAutumn(md: Int): Boolean = md >= 923 && md <= 1221

        /** Jak isMobile() w JS: szerokość okna poniżej 640 px CSS; tu szerokość ekranu w dp. */
        private fun isMobile(): Boolean = try {
            val m = Resources.getSystem().displayMetrics
            m.widthPixels / m.density < 640f
        } catch (e: Exception) {
            false
        }

        private fun plan(fx: Fx, mobile: Boolean): Plan {
            val intensity = fx.intensity
            val p = Plan()
            when (fx.effect) {
                "clear-day" -> { p.sun = true; p.dust = 12 }
                "clear-night" -> { p.moon = true; p.stars = 50 }
                "clouds" -> {
                    p.clouds = 3 + (intensity * 4).roundToInt()
                    if (gwiazdyFx(fx)) { p.stars = 18; p.moon = true }
                    if (!fx.isNight && intensity < 0.5) p.sun = true
                }
                "fog" -> { p.fog = 5 }
                "drizzle" -> { p.drops = 30 + (intensity * 40).roundToInt(); p.clouds = 3 }
                "rain" -> { p.drops = 50 + (intensity * 60).roundToInt(); p.clouds = 4; p.dark = true }
                "rain-heavy" -> { p.drops = 60 + (intensity * 60).roundToInt(); p.clouds = 4; p.dark = true }
                "storm" -> { p.drops = 100; p.clouds = 5; p.dark = true; p.flash = true }
                "snow" -> { p.flakes = 40 + (intensity * 60).roundToInt(); p.clouds = 3 }
                "sleet" -> { p.drops = 35; p.flakes = 30; p.clouds = 3; p.dark = true }
                "wind" -> { p.streaks = 16 + (intensity * 8).roundToInt(); p.clouds = 2 }
            }
            if (fx.leaves && fx.effect in LEAF_EFFECTS) p.leaves = if (fx.effect == "wind") 14 else 10
            val k = if (mobile) 0.6 else 1.0
            p.stars = (p.stars * k).roundToInt(); p.dust = (p.dust * k).roundToInt(); p.drops = (p.drops * k).roundToInt()
            p.flakes = (p.flakes * k).roundToInt(); p.streaks = (p.streaks * k).roundToInt(); p.leaves = (p.leaves * k).roundToInt()
            // Twardy limit cząstek (gwiazdy, pył, krople, płatki, smugi, liście razem).
            val total = p.stars + p.dust + p.drops + p.flakes + p.streaks + p.leaves
            if (total > MAX_PARTICLES) {
                val f = MAX_PARTICLES.toDouble() / total
                p.stars = floor(p.stars * f).toInt(); p.dust = floor(p.dust * f).toInt(); p.drops = floor(p.drops * f).toInt()
                p.flakes = floor(p.flakes * f).toInt(); p.streaks = floor(p.streaks * f).toInt(); p.leaves = floor(p.leaves * f).toInt()
            }
            return p
        }
    }
}
