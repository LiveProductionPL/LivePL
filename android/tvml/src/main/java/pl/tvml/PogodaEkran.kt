package pl.tvml

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

val cienNapisu = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 12f)

/** Pogoda w Lesznie ze stacji TVML. Scena zmienia się jak na stronie: stan, pora dnia, pora roku i wersja z panelu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PogodaEkran(m: Magazyn) {
    val b = LocalBarwy.current
    val p = m.pogoda
    val zakres = rememberCoroutineScope()
    PullToRefreshBox(isRefreshing = m.laduje, onRefresh = { zakres.launch { m.odswiezTeraz(wymus = true) } }, modifier = Modifier.fillMaxSize().background(b.tlo)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(18.dp))
                    .semantics(mergeDescendants = true) { contentDescription = tl("app.weatherLeszno", "Pogoda w Lesznie") + ": ${p?.let { Pogoda.stopnie(it.temperatura) + "C" } ?: ""}, ${p?.opis.orEmpty()}" },
            ) {
                // ta sama scena ze strony co na karcie Startu (harmonogram motywów, stan i noc z /api/v1/weather)
                val stan = p?.ikona?.ifEmpty { null } ?: "cloudy"
                ScenaStrony(m.scenaPogody(stan), stan, m.nocSceny(), m.opcjeSceny(stan), p?.efekt, Modifier.fillMaxSize(), m.scenaSvgTeraz(), poraRoku = m.motyw.scena())
                Column(Modifier.padding(18.dp).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val styl = TextStyle(color = Color.White, shadow = cienNapisu)
                    Text(p?.let { Pogoda.stopnie(it.temperatura) + "C" } ?: "–", style = styl.copy(fontSize = 52.sp, fontWeight = FontWeight.Black, lineHeight = 56.sp))
                    Text(p?.opis.orEmpty(), style = styl.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                    Text(tl("app.feelsLike", "Odczuwalna") + " " + (p?.let { Pogoda.stopnie(it.odczuwalna) + "C" } ?: "–"), style = styl.copy(fontSize = 13.sp))
                }
                Text(
                    tl("app.weatherLeszno", "Pogoda w Lesznie").uppercase(Jezyk.locale),
                    Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 9.dp, vertical = 5.dp).clearAndSetSemantics { },
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(m.motyw.naglowek.first().w(LocalCiemny.current))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(30.dp).border(1.5.dp, kolor(0xe8c26a), CircleShape), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(14.dp)) { drawPath(gwiazda(Rect(Offset.Zero, size)), kolor(0xe8c26a)) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(m.dzisiaj?.etykieta ?: Daty.dzisiaj(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val imieniny = m.dzisiaj?.imieniny.orEmpty()
                    if (imieniny.isNotEmpty()) Text(tl("app.nameDays", "Imieniny") + ": ${imieniny.joinToString(", ")}", color = kolor(0xc9d3e3), fontSize = 12.sp)
                    Text(
                        tl("app.sunriseSunset", "Wschód {sunrise} · zachód {sunset}")
                            .replace("{sunrise}", p?.wschod?.ifEmpty { null } ?: "–").replace("{sunset}", p?.zachod?.ifEmpty { null } ?: "–"),
                        color = kolor(0xc9d3e3), fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pomiar(tl("app.wind", "Wiatr"), p?.wiatr?.let { String.format(Jezyk.locale, "%.1f km/h %s", it, p.kierunek).trim() } ?: "–", Modifier.weight(1f))
                Pomiar(tl("app.humidity", "Wilgotność"), p?.wilgotnosc?.let { "${Math.round(it)}%" } ?: "–", Modifier.weight(1f))
                Pomiar(tl("app.pressure", "Ciśnienie"), p?.cisnienie?.let { String.format(Jezyk.locale, "%.1f hPa", it) } ?: "–", Modifier.weight(1f))
            }

            val godziny = p?.godziny.orEmpty()
            if (godziny.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp))
                        .horizontalScroll(rememberScrollState()).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    godziny.take(24).forEach { g ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(g.czas, color = b.tekst, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            IkonaPogody(g.ikona, Modifier.size(24.dp))
                            Text(Pogoda.stopnie(g.temp), color = b.tekst, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (g.opad > 0) Text("${g.opad}%", color = b.link, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            val dni = p?.dni.orEmpty()
            if (dni.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp)) {
                    dni.forEachIndexed { i, d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(d.nazwa, Modifier.width(36.dp), color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            IkonaPogody(d.ikona, Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(d.opis, Modifier.weight(1f), color = b.slaby, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (d.opad > 0) Text("${d.opad}%", Modifier.padding(start = 6.dp), color = b.link, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(Pogoda.stopnie(d.min), Modifier.padding(start = 8.dp), color = b.slaby, fontSize = 13.sp)
                            Text(Pogoda.stopnie(d.max), Modifier.padding(start = 8.dp), color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        }
                        if (i < dni.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
                    }
                }
            }
        }
    }
}

@Composable
fun Pomiar(nazwa: String, wartosc: String, modifier: Modifier = Modifier) {
    val b = LocalBarwy.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(nazwa, color = b.slaby, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        // jak minimumScaleFactor na iPhonie: długa wartość (wiatr z kierunkiem) zmniejsza się zamiast urywać
        BasicText(
            wartosc, style = TextStyle(color = b.tekst, fontSize = 14.sp, fontWeight = FontWeight.Black), maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
        )
    }
}

/** Kolorowa ikonka pogody (jak symbole wielobarwne na iPhonie). bialy = biała, do banera. */
@Composable
fun IkonaPogody(ikona: String, modifier: Modifier = Modifier, bialy: Boolean = false) {
    val i = ikona.lowercase()
    val rodzaj = when {
        "storm" in i || "thunder" in i -> "burza"
        "snow" in i -> "snieg"
        "rain" in i || "sleet" in i || "hail" in i || "shower" in i -> "deszcz"
        "fog" in i -> "mgla"
        "wind" in i -> "wiatr"
        "night" in i && ("partly" in i || "cloud" in i) -> "chmuraNoc"
        "night" in i -> "ksiezyc"
        "cloud-sun" in i || "partly" in i -> "chmuraSlonce"
        "sun" in i || "clear" in i -> "slonce"
        else -> "chmura"
    }
    Canvas(modifier.clearAndSetSemantics { }) { rysujIkone(rodzaj, bialy) }
}

private fun DrawScope.rysujIkone(rodzaj: String, bialy: Boolean) {
    val s = size.minDimension / 24
    val sloneczny = if (bialy) Color.White else kolor(0xf5b301)
    val chmurny = if (bialy) Color.White else kolor(0x9aa7ba)
    val niebieski = if (bialy) Color.White else kolor(0x2f80ed)
    val szary = if (bialy) Color.White else kolor(0x8b96a8)
    fun slonce(cx: Float, cy: Float, r: Float) {
        drawCircle(sloneczny, r * s, Offset(cx * s, cy * s))
        for (k in 0 until 8) {
            val a = k * Math.PI / 4
            drawLine(
                sloneczny, Offset((cx + kotlin.math.cos(a) * (r + 2)).toFloat() * s, (cy + kotlin.math.sin(a) * (r + 2)).toFloat() * s),
                Offset((cx + kotlin.math.cos(a) * (r + 4)).toFloat() * s, (cy + kotlin.math.sin(a) * (r + 4)).toFloat() * s), 1.8f * s, StrokeCap.Round,
            )
        }
    }
    fun chmura(dx: Float, dy: Float, c: Color) {
        val p = Path().apply {
            addOval(Rect(Offset((3 + dx) * s, (9 + dy) * s), Size(9 * s, 8 * s)))
            addOval(Rect(Offset((7 + dx) * s, (5 + dy) * s), Size(10 * s, 10 * s)))
            addOval(Rect(Offset((12 + dx) * s, (8 + dy) * s), Size(9 * s, 9 * s)))
            addRect(Rect(Offset((7 + dx) * s, (12 + dy) * s), Size(10 * s, 5 * s)))
        }
        drawPath(p, c)
    }
    fun ksiezyc(cx: Float, cy: Float, r: Float, c: Color) {
        val p = Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            addOval(Rect(Offset((cx - r) * s, (cy - r) * s), Size(2 * r * s, 2 * r * s)))
            addOval(Rect(Offset((cx - r + r * 0.7f) * s, (cy - r - r * 0.35f) * s), Size(2 * r * s, 2 * r * s)))
        }
        drawPath(p, c)
    }
    when (rodzaj) {
        "slonce" -> slonce(12f, 12f, 5f)
        "ksiezyc" -> ksiezyc(11f, 12f, 8f, if (bialy) Color.White else kolor(0xf4d35e))
        "chmuraSlonce" -> { slonce(8f, 8f, 3.5f); chmura(1f, 2f, chmurny) }
        "chmuraNoc" -> { ksiezyc(8f, 7f, 4.5f, if (bialy) Color.White else kolor(0xf4d35e)); chmura(1f, 2f, chmurny) }
        "deszcz" -> {
            chmura(0f, -2f, szary)
            for (x in listOf(8f, 12f, 16f)) drawLine(niebieski, Offset(x * s, 18 * s), Offset((x - 1.2f) * s, 22 * s), 1.8f * s, StrokeCap.Round)
        }
        "burza" -> {
            chmura(0f, -2f, szary)
            drawPath(Path().apply {
                moveTo(13 * s, 14 * s); lineTo(9.5f * s, 19 * s); lineTo(12 * s, 19 * s); lineTo(10.5f * s, 23 * s); lineTo(15 * s, 17.5f * s); lineTo(12.5f * s, 17.5f * s); close()
            }, sloneczny)
        }
        "snieg" -> {
            chmura(0f, -2f, chmurny)
            for (x in listOf(8f, 12f, 16f)) drawCircle(if (bialy) Color.White else kolor(0x7dd3fc), 1.4f * s, Offset(x * s, 20 * s))
        }
        "mgla" -> {
            chmura(0f, -3f, chmurny)
            for ((y, x0) in listOf(17f to 4f, 20f to 6f)) drawLine(szary, Offset(x0 * s, y * s), Offset((x0 + 14) * s, y * s), 1.8f * s, StrokeCap.Round)
        }
        "wiatr" -> {
            val p = Path().apply {
                moveTo(3 * s, 9 * s); lineTo(15 * s, 9 * s); quadraticTo(19 * s, 9 * s, 19 * s, 6 * s); quadraticTo(19 * s, 3 * s, 16 * s, 3.5f * s)
                moveTo(3 * s, 14 * s); lineTo(19 * s, 14 * s); quadraticTo(22 * s, 14 * s, 22 * s, 17 * s); quadraticTo(22 * s, 20 * s, 19 * s, 19.5f * s)
                moveTo(3 * s, 19 * s); lineTo(11 * s, 19 * s)
            }
            drawPath(p, if (bialy) Color.White else kolor(0x5b8bd0), style = Stroke(1.8f * s, cap = StrokeCap.Round))
        }
        else -> chmura(0f, 0f, chmurny)
    }
}
