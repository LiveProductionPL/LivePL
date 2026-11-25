package pl.tvml

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
                // scena prosto ze strony (tryb zakładki), pod nią wbudowana jako zapas, jak na iOS
                ScenaZZapasem(AdresSceny.dla(m, "zakladka"), Modifier.fillMaxSize()) { stoi ->
                    ScenaStrony(m.scenaPogody(stan), stan, m.nocSceny(), m.opcjeSceny(stan), p?.efekt, Modifier.fillMaxSize(), m.scenaSvgTeraz(), stoi = stoi)
                }
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
                // kropki w karcie, pod godzinami (Karol 25.11: przy każdej karuzeli wskaźnik przewijania), jak na iOS
                KaruzelaPozioma(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp)),
                    odstepElementow = 14.dp, wewnatrz = 14.dp, odstep = 10.dp, dolnyOdstep = 10.dp,
                ) {
                    godziny.take(24).forEach { g ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(g.czas, color = b.tekst, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            // ikony 1:1 ze strony (Karol 25.11: „ikony pogody zawsze muszą być zjednolicone”), noc według godziny
                            IkonaPogodyStrony(g.ikona, IkonyPogodyStrony.nocO(g.czas, p?.wschod, p?.zachod) ?: false, 28.dp, temp = g.temp)
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
                            IkonaPogodyStrony(d.ikona, false, 28.dp, opis = d.opis, temp = d.max)
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
