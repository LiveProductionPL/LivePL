package pl.tvml

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.sin

/** Czas animacji w sekundach (do scen rysowanych na płótnie), ok. 30 klatek na sekundę. */
@Composable
fun czasAnimacji(): State<Double> = produceState(System.nanoTime() / 1e9) {
    while (true) {
        androidx.compose.runtime.withFrameNanos { }
        value = System.nanoTime() / 1e9
        delay(33)
    }
}

/** Baner nad każdym ekranem: pasek życzeń z panelu, logo z ozdobą, pogoda, szukaj i pasek „NA ŻYWO”. */
@Composable
fun Baner(m: Magazyn) {
    val motyw = m.motyw
    val ciemny = LocalCiemny.current
    Column {
        val pasek = motyw.pasek
        if (pasek != null) {
            Box(Modifier.background(Brush.verticalGradient(motyw.tloPaska))) {
                PasekZyczen(pasek, motyw, Modifier.windowInsetsPadding(WindowInsets.statusBars))
            }
        }
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(motyw.naglowek.map { it.w(ciemny) }))) {
            if (motyw.snieg || motyw.iskry) Platki(motyw.kolorPlatkow, motyw.iskry, Modifier.matchParentSize())
            Row(
                Modifier
                    .then(if (pasek == null) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .clickable(role = Role.Button, onClickLabel = "Strona główna") { m.naStart() }
                        .semantics { contentDescription = "Telewizja TVML, strona główna" },
                ) {
                    Image(painterResource(motyw.logo), null, Modifier.height(40.dp).width(86.dp))
                    OzdobaLogo(motyw, Modifier.align(Alignment.TopEnd))
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(role = Role.Button) { m.pokaz(ZakladkaApp.Cel.Pogoda) }
                        .padding(horizontal = 12.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "Pogoda: " + (m.pogoda?.let { Pogoda.stopnie(it.temperatura) + "C" } ?: "") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IkonaPogody(m.pogoda?.ikona ?: "cloud", Modifier.size(20.dp), bialy = true)
                    Text(m.pogoda?.let { Pogoda.stopnie(it.temperatura) + "C" } ?: "–", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(role = Role.Button, onClickLabel = "Szukaj") { m.arkuszSzukaj = true }
                        .semantics { contentDescription = "Szukaj" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ikony.szukaj, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            // linia pod banerem: jednolita albo dwa paski (11 Listopada)
            val paski = motyw.paskiLinii
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                if (paski.size >= 2) paski.forEach { Box(Modifier.fillMaxWidth().height(2.dp).background(it)) }
                else motyw.linia?.let { Box(Modifier.fillMaxWidth().height(2.dp).background(it.w(ciemny))) }
            }
        }
        if (m.pasekWlaczony) NaZywo(m)
    }
}

@Composable
fun PasekZyczen(pasek: Motyw.Pasek, motyw: Motyw, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(12.dp)) { drawPath(gwiazda(Rect(Offset.Zero, size)), motyw.zlotoPaska) }
            Text(pasek.etykieta, color = motyw.zlotoPaska, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        }
        Text(pasek.tekst, color = kolor(0xf6efe3), fontSize = 13.sp)
        val doKiedy = pasek.odliczanieDo
        if (doKiedy != null) {
            val teraz by produceState(ZonedDateTime.now(Daty.strefa)) { while (true) { delay(1000); value = ZonedDateTime.now(Daty.strefa) } }
            Text(
                "● ODLICZANIE DO ${doKiedy.year} • ${odliczanie(teraz, doKiedy)}",
                color = motyw.zlotoPaska, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
        } else pasek.stopka?.let {
            Text(it, color = motyw.zlotoPaska, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

fun odliczanie(od: ZonedDateTime, koniec: ZonedDateTime): String {
    val s = max(0L, Duration.between(od, koniec).seconds)
    return "%dD %02d:%02d:%02d".format(s / 86400, (s % 86400) / 3600, (s % 3600) / 60, s % 60)
}

/** Pasek „NA ŻYWO”: najnowsze wiadomości przesuwają się jak na stronie. Stuknięcie otwiera pierwszą z nich. */
@Composable
fun NaZywo(m: Magazyn) {
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    val tekst = m.pasek.joinToString("     •     ") { it.tytul }
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(b.karta)
            .clickable(role = Role.Button) { m.pasek.firstOrNull()?.let { m.otworz(it) } }
            .semantics(mergeDescendants = true) { contentDescription = "${m.etykietaPaska}: ${m.pasek.firstOrNull()?.tytul.orEmpty()}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.fillMaxHeight().background(m.motyw.naZywo.w(ciemny)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            Text(m.etykietaPaska, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        Text(
            tekst,
            Modifier.weight(1f).padding(start = 10.dp).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0, velocity = 40.dp),
            color = b.tekst, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
}

/** Śnieg albo iskry w banerze, delikatnie opadające. */
@Composable
fun Platki(kolor: Color, iskry: Boolean, modifier: Modifier = Modifier) {
    val czas by czasAnimacji()
    Canvas(modifier) {
        val t = czas
        val skala = density
        for (i in 0 until 18) {
            val x0 = ((i * 73) % 390) / 390.0 * size.width
            val szybkosc = (8 + (i % 5) * 3) * skala
            val y = ((i * 37 % 60) * skala + t * szybkosc) % (size.height + 10 * skala) - 5 * skala
            val x = x0 + sin(t * 0.8 + i) * 6 * skala
            val r = (if (iskry) 1.4 else if (i % 3 == 0) 2.2 else 1.6) * skala
            val alfa = if (iskry) 0.5 + 0.5 * sin(t * 3 + i) else 0.85
            drawCircle(kolor.copy(alpha = alfa.toFloat().coerceIn(0f, 1f)), r.toFloat(), Offset(x.toFloat(), y.toFloat()))
        }
    }
}

/** Ozdoba na logo, jak na stronie: bałwanek, czapka Mikołaja, gwiazda, flaga, „2027”. */
@Composable
fun OzdobaLogo(motyw: Motyw, modifier: Modifier = Modifier) {
    when (motyw.rodzaj) {
        Motyw.Rodzaj.ZIMA -> Canvas(modifier.offset(x = 14.dp, y = (-12).dp).size(22.dp, 28.dp)) { balwan() }
        Motyw.Rodzaj.MIKOLAJKI, Motyw.Rodzaj.CZAPECZKA ->
            Canvas(modifier.offset(x = 12.dp, y = (-14).dp).size(30.dp, 24.dp).rotate(14f)) { czapka() }
        Motyw.Rodzaj.WIGILIA, Motyw.Rodzaj.SWIETA ->
            Canvas(modifier.offset(x = 14.dp, y = (-12).dp).size(24.dp)) { drawPath(gwiazda(Rect(Offset.Zero, size)), kolor(0xe0b45a)) }
        Motyw.Rodzaj.LISTOPAD -> Canvas(modifier.offset(x = 14.dp, y = (-12).dp).size(26.dp, 22.dp)) { flaga() }
        Motyw.Rodzaj.SYLWESTER, Motyw.Rodzaj.NOWY_ROK -> {
            val dzis = LocalDate.now(Daty.strefa)
            Text(
                (dzis.year + if (dzis.monthValue == 12) 1 else 0).toString(),
                modifier.offset(x = 18.dp, y = (-10).dp).clip(RoundedCornerShape(6.dp)).background(kolor(0xe8c26a)).padding(horizontal = 5.dp, vertical = 2.dp),
                color = kolor(0x1b1640), fontSize = 9.sp, fontWeight = FontWeight.Black,
            )
        }
        else -> {}
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.balwan() {
    val s = size.width / 22
    fun k(x: Float, y: Float, r: Float, c: Color) = drawCircle(c, r * s, Offset(x * s, y * s))
    k(11f, 20f, 7f, kolor(0xf8fbff))
    k(11f, 10f, 5f, kolor(0xf8fbff))
    drawRect(kolor(0x0f3156), Offset(6 * s, 1 * s), Size(10 * s, 5 * s))
    drawRect(kolor(0x0f3156), Offset(4 * s, 5 * s), Size(14 * s, 2 * s))
    drawPath(Path().apply { moveTo(11 * s, 11 * s); lineTo(14 * s, 12 * s); lineTo(11 * s, 13 * s); close() }, kolor(0xf97316))
    k(9f, 9f, 0.9f, kolor(0x0b1830))
    k(13f, 9f, 0.9f, kolor(0x0b1830))
    drawLine(kolor(0x38bdf8), Offset(6 * s, 15 * s), Offset(16 * s, 15 * s), 2 * s, StrokeCap.Round)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.czapka() {
    val s = size.width / 30
    drawPath(Path().apply {
        moveTo(3 * s, 19 * s)
        quadraticTo(10 * s, 4 * s, 24 * s, 5 * s)
        quadraticTo(22 * s, 11 * s, 26 * s, 17 * s)
        close()
    }, kolor(0xc8102e))
    drawRoundRect(kolor(0xf8fafc), Offset(1 * s, 17 * s), Size(24 * s, 5 * s), CornerRadius(2.5f * s))
    drawCircle(kolor(0xf8fafc), 3.2f * s, Offset(25 * s, 5 * s))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.flaga() {
    val s = size.width / 26
    drawRoundRect(kolor(0xcbd5e1), Offset(2 * s, 1 * s), Size(1.6f * s, 21 * s), CornerRadius(0.8f * s))
    drawRect(Color.White, Offset(3.6f * s, 2 * s), Size(19 * s, 6 * s))
    drawRect(kolor(0xdc143c), Offset(3.6f * s, 8 * s), Size(19 * s, 6 * s))
}

/** Gwiazda o czterech ramionach (pasek życzeń, Wigilia, Pogoda). */
fun gwiazda(r: Rect): Path {
    val c = r.center
    val zew = minOf(r.width, r.height) / 2
    val wew = zew * 0.32f
    return Path().apply {
        for (i in 0 until 8) {
            val kat = i * Math.PI / 4 - Math.PI / 2
            val promien = if (i % 2 == 0) zew else wew
            val x = c.x + (kotlin.math.cos(kat) * promien).toFloat()
            val y = c.y + (sin(kat) * promien).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
