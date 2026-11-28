package pl.tvml

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
            val kontekst = LocalContext.current
            // belka z adresem (theme.topbar.url) otwiera go: podstronę albo artykuł w aplikacji, resztę w przeglądarce
            // (Karol 28.11: „górna belka w telefonie nie działa, nic nie otwiera, jak klikam”), jak na iOS (37c1cdf)
            val otworz: (() -> Unit)? = pasek.adres?.let { a -> { otworzAdresAplikacji(m, kontekst, a) } }
            Box(
                Modifier.background(Brush.verticalGradient(motyw.tloPaska))
                    .then(if (otworz != null) Modifier.clickable(role = Role.Button, onClickLabel = pasek.etykieta) { otworz() } else Modifier),
            ) {
                PasekZyczen(pasek, motyw, Modifier.windowInsetsPadding(WindowInsets.statusBars), zLinkiem = otworz != null)
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
                        .clickable(role = Role.Button, onClickLabel = tl("app.tab.home", "Strona główna")) { m.naStart() }
                        .semantics { contentDescription = tl("app.homeLogo", "Telewizja TVML, strona główna") },
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
                        .semantics(mergeDescendants = true) { contentDescription = tl("app.weather", "Pogoda") + ": " + (m.pogoda?.let { Pogoda.stopnie(it.temperatura) + "C" } ?: "") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // ta sama ikona co na karcie i na stronie (wszędzie jeden zestaw z weather-icons.js)
                    IkonaPogodyStrony(m.pogoda?.ikona ?: "cloud", m.pogoda?.dzien == false, 22.dp, opis = m.pogoda?.opis, temp = m.pogoda?.temperatura)
                    Text(m.pogoda?.let { Pogoda.stopnie(it.temperatura) + "C" } ?: "–", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                PrzyciskJezyka(m)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(role = Role.Button, onClickLabel = tl("app.search", "Szukaj")) { m.arkuszSzukaj = true }
                        .semantics { contentDescription = tl("app.search", "Szukaj") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ikony.szukaj, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            // linia pod banerem: jednolita albo dwa paski (11 Listopada)
            val paski = motyw.paskiLinii
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                // kreska nad paskiem „NAJNOWSZE” zawsze, nie tylko w motywach (Karol 24.11: „belka oddzielająca …
                // nie zawsze ją widać i dziwnie to wygląda”); bez motywu złota jak linia pod nagłówkiem tvml.pl
                if (paski.size >= 2) paski.forEach { Box(Modifier.fillMaxWidth().height(2.dp).background(it)) }
                else Box(Modifier.fillMaxWidth().height(2.dp).background(motyw.linia?.w(ciemny) ?: Barwy.zloto))
            }
        }
        if (m.pasekWlaczony) NaZywo(m)
    }
}

@Composable
fun PasekZyczen(pasek: Motyw.Pasek, motyw: Motyw, modifier: Modifier = Modifier, zLinkiem: Boolean = false) {
    Column(modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(12.dp)) { drawPath(gwiazda(Rect(Offset.Zero, size)), motyw.zlotoPaska) }
            Text(pasek.etykieta, color = motyw.zlotoPaska, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            // strzałka: belkę można stuknąć (jak „›” na iOS)
            if (zLinkiem) Text("›", color = motyw.zlotoPaska, fontSize = 15.sp, fontWeight = FontWeight.Black, lineHeight = 11.sp)
        }
        Text(pasek.tekst, color = kolor(0xf6efe3), fontSize = 13.sp)
        val doKiedy = pasek.odliczanieDo
        if (doKiedy != null) {
            val teraz by produceState(ZonedDateTime.now(Daty.strefa)) { while (true) { delay(1000); value = ZonedDateTime.now(Daty.strefa) } }
            Text(
                "● " + tl("app.countdownTo", "Odliczanie do {year}").replace("{year}", doKiedy.year.toString()).uppercase(Jezyk.locale) + " • ${odliczanie(teraz, doKiedy)}",
                color = motyw.zlotoPaska, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
        } else pasek.stopka?.let {
            Text(it, color = motyw.zlotoPaska, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

/**
 * Język w banerze (prośba Karola, 23.11.2026): pigułka jak pogoda z flagą bieżącego języka, a po stuknięciu wybór
 * pozostałych z config.languages. To samo ustawienie co „Więcej → Język”. Bez kilku języków na serwerze przycisku nie ma.
 */
@Composable
private fun PrzyciskJezyka(m: Magazyn) {
    if (Jezyk.dostepne.size < 2) return
    var otwarte by remember { mutableStateOf(false) }
    val b = LocalBarwy.current
    val biezacy = Jezyk.biezacy
    // „Język: polski”, „Мова: українська”, „Language: English”
    val nazwa = Jezyk.nazwy.getValue(biezacy).let { if (biezacy == "en") it else it.lowercase(Jezyk.locale) }
    Box {
        Box(
            Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(role = Role.Button, onClickLabel = tl("app.language", "Język")) { otwarte = true }
                .padding(horizontal = 12.dp)
                .semantics(mergeDescendants = true) { contentDescription = tl("app.language", "Język") + ": " + nazwa },
            contentAlignment = Alignment.Center,
        ) {
            // jak na iPhonie (916840b): flaga 22×15 i mała strzałka w dół
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Flaga(biezacy, Modifier.size(22.dp, 15.dp))
                Icon(Ikony.wDol, null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
        }
        DropdownMenu(otwarte, { otwarte = false }, containerColor = b.karta, shape = RoundedCornerShape(14.dp)) {
            // kolejność jak w prośbie Karola: polski, English, Українська
            listOf("pl", "en", "uk").filter { it != biezacy && it in Jezyk.dostepne }.forEach { j ->
                DropdownMenuItem(
                    text = { Text(Jezyk.nazwy.getValue(j), color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Flaga(j, Modifier.size(22.dp, 15.dp)) },
                    onClick = {
                        otwarte = false
                        m.ustawJezyk(j)
                    },
                )
            }
        }
    }
}

/** Flaga języka (vector drawable z res/drawable), z cienką obwódką, żeby biel flagi polskiej nie znikała na jasnym tle. */
@Composable
fun Flaga(jezyk: String, modifier: Modifier = Modifier) {
    val rys = when (jezyk) {
        "uk" -> R.drawable.flaga_ua
        "en" -> R.drawable.flaga_gb
        else -> R.drawable.flaga_pl
    }
    Image(
        painterResource(rys), null,
        modifier.clip(RoundedCornerShape(2.5.dp)).border(0.5.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(2.5.dp)),
        contentScale = ContentScale.Crop,
    )
}

/**
 * Adres z panelu (belka motywu): tvml.pl/strona/{slug} jako podstrona w aplikacji (jak w „Więcej”), tvml.pl/artykul/…
 * jako artykuł w aplikacji, Pogoda w zakładce, inne adresy http(s) w karcie przeglądarki.
 */
fun otworzAdresAplikacji(m: Magazyn, kontekst: android.content.Context, adres: String) {
    val u = android.net.Uri.parse(adres)
    val strona = Linki.podstrona(u)
    val artykul = Linki.artykul(u)
    when {
        strona != null -> m.otworz(Ekran.Strona(strona))
        artykul != null -> m.otworzArtykul(artykul) { otworzStrone(kontekst, adres) }
        Linki.pogoda(u) -> m.pokaz(ZakladkaApp.Cel.Pogoda)
        u.scheme == "https" || u.scheme == "http" -> otworzStrone(kontekst, adres)
    }
}

fun odliczanie(od: ZonedDateTime, koniec: ZonedDateTime): String {
    val s = max(0L, Duration.between(od, koniec).seconds)
    return "%dD %02d:%02d:%02d".format(s / 86400, (s % 86400) / 3600, (s % 3600) / 60, s % 60)
}

/**
 * Pasek pod banerem: etykieta z panelu (config.ticker.label, np. „NAJNOWSZE”), a kropka i „NA ŻYWO” tylko przy
 * transmisji (config.ticker.live). Wiadomości przesuwają się jak na stronie i każda otwiera swój artykuł
 * (wcześniej stuknięcie w dowolne miejsce paska otwierało zawsze pierwszy).
 */
@Composable
fun NaZywo(m: Magazyn) {
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    val etykieta = if (m.pasekNaZywo) tl("app.live", "NA ŻYWO").uppercase(Jezyk.locale) else m.etykietaPaska.uppercase(Jezyk.locale)
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(b.karta),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.fillMaxHeight().background(m.motyw.naZywo.w(ciemny)).padding(horizontal = 12.dp)
                .semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (m.pasekNaZywo) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            Text(etykieta, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        PrzewijaneWiadomosci(m.pasek, Modifier.weight(1f).fillMaxHeight())
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
}

/**
 * Wiadomości paska przesuwane w lewo (40 dp na sekundę), w pętli. Przesunięcie zmienia położenie wierszy, a nie tylko
 * rysunek, więc stuknięcie trafia w tę wiadomość, którą widać; kopia do zapętlenia wskazuje te same artykuły.
 */
@Composable
private fun PrzewijaneWiadomosci(lista: List<Artykul>, modifier: Modifier) {
    val gestosc = LocalDensity.current
    var szerokoscPola by remember { mutableIntStateOf(0) }
    var szerokoscTekstu by remember(lista) { mutableIntStateOf(0) }
    val przewijaj = szerokoscPola in 1 until szerokoscTekstu
    val odstep = 60.dp
    val odstepPx = with(gestosc) { odstep.roundToPx() }
    val predkosc = with(gestosc) { 40.dp.toPx() }
    val czas = if (przewijaj) czasAnimacji() else null
    val start = remember(lista, przewijaj) { System.nanoTime() / 1e9 }
    Box(modifier.clipToBounds().onSizeChanged { szerokoscPola = it.width }, contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier.fillMaxHeight().wrapContentWidth(Alignment.Start, unbounded = true).offset {
                // czytane przy rozmieszczaniu, więc animacja nie przebudowuje paska co klatkę
                val okres = szerokoscTekstu + odstepPx
                val t = (czas?.value ?: start) - start
                IntOffset(if (przewijaj && okres > 0) -((t * predkosc) % okres).toInt() else 0, 0)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wiadomosci(lista, Modifier.fillMaxHeight().onSizeChanged { szerokoscTekstu = it.width })
            if (przewijaj) {
                Spacer(Modifier.width(odstep))
                Wiadomosci(lista, Modifier.fillMaxHeight().clearAndSetSemantics { })
            }
        }
    }
}

@Composable
private fun Wiadomosci(lista: List<Artykul>, modifier: Modifier) {
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    Row(modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        lista.forEachIndexed { i, a ->
            if (i > 0) Text("•", Modifier.padding(horizontal = 14.dp).clearAndSetSemantics { }, color = b.slaby, fontSize = 13.sp)
            Box(
                Modifier.fillMaxHeight().clickable(role = Role.Button, onClickLabel = tl("app.readMore", "Czytaj")) { m.otworz(a) }.padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(a.tytul, color = b.tekst, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
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
        // 24.12 jak na stronie (logo „sleigh”): złote sanie z reniferem przelatujące nad logo; 25–26.12 gwiazda
        Motyw.Rodzaj.WIGILIA -> SanieLogo(modifier)
        Motyw.Rodzaj.ANDRZEJKI -> AndrzejkiLogo(modifier)
        Motyw.Rodzaj.SWIETA ->
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

/**
 * Sanie ze strony (panel-special-themes.js, wariant „sleigh”): SVG na viewBox 0 0 270 126 nałożony na logo 86 × 40 dp,
 * z cieniem jak feDropShadow (dx 1, dy 3, odchylenie 2,5, czarny 0,58). Tutaj wycinek 180…300 × −10…60, bo sanie
 * zajmują x 192–283 i y 4–45 i wystają poza logo w prawo. Obraz liczony raz na gęstość ekranu.
 */
@Composable
private fun SanieLogo(modifier: Modifier) {
    val gestosc = LocalDensity.current.density
    val obraz = remember(gestosc) {
        runCatching { svgZCieniem(SANIE_SVG, gestosc, 120f, 70f, dx = 1f, dy = 3f, odchylenie = 2.5f, cien = android.graphics.Color.argb(148, 0, 0, 0)) }.getOrNull()
    } ?: return
    Canvas(modifier.size(86.dp, 40.dp)) {
        val k = size.width / 270f
        drawIntoCanvas { it.nativeCanvas.drawBitmap(obraz, 180 * k, -10 * k, null) }
    }
}

/**
 * Andrzejki jak na stronie (panel-special-themes.js, klucz „andrzejki”): sierp księżyca, poświata i świeca z cieniem
 * #07040f 0,6 (dy 2, odchylenie 2) jako obraz, a płomień i gwiazdki rysowane co klatkę: płomień drga jak
 * tvmlPanelAndrzejkiFlame (1,6 s), gwiazdki migają jak tvmlPanelAndrzejkiTwinkle (3 s, opóźnienia 0, 1,2 i 2 s).
 * Przy „Usuń animacje” wszystko stoi.
 */
@Composable
private fun AndrzejkiLogo(modifier: Modifier) {
    val gestosc = LocalDensity.current.density
    val obraz = remember(gestosc) {
        runCatching { svgZCieniem(ANDRZEJKI_SVG, gestosc, 100f, 72f, dx = 0f, dy = 2f, odchylenie = 2f, cien = android.graphics.Color.argb(153, 7, 4, 15)) }.getOrNull()
    }
    val ruch = android.animation.ValueAnimator.areAnimatorsEnabled()
    val czas = if (ruch) czasAnimacji().value else 0.0
    val start = remember { System.nanoTime() / 1e9 }
    Canvas(modifier.size(86.dp, 40.dp)) {
        val k = size.width / 270f
        if (obraz != null) drawIntoCanvas { it.nativeCanvas.drawBitmap(obraz, 180 * k, -14 * k, null) }
        val t = if (ruch) czas - start else 0.0
        fun pt(x: Float, y: Float) = Offset(x * k, y * k)
        // gwiazdki: krycie 1 → 0,25 → 1, ease-in-out
        fun mig(opoznienie: Double): Float {
            val f = (((t - opoznienie) % 3.0) + 3.0) % 3.0 / 3.0
            val pol = if (f < 0.5) f * 2 else (1 - f) * 2
            return 1f - 0.75f * wygladzenie(pol.toFloat())
        }
        val gwiazdka = Path().apply {
            moveTo(236 * k, 4 * k)
            listOf(1.4f to 3.2f, 3.2f to 1.4f, -3.2f to 1.4f, -1.4f to 3.2f, -1.4f to -3.2f, -3.2f to -1.4f, 3.2f to -1.4f).fold(236f to 4f) { (x, y), (ddx, ddy) ->
                val n = (x + ddx) to (y + ddy); lineTo(n.first * k, n.second * k); n
            }
            close()
        }
        drawPath(gwiazdka, Color.White.copy(alpha = mig(0.0)))
        drawCircle(Color.White.copy(alpha = mig(1.2)), 1.8f * k, pt(191f, 10f))
        drawCircle(Color.White.copy(alpha = mig(2.0)), 1.5f * k, pt(266f, 12f))
        // płomień: skala i obrót wokół dołu płomienia (transform-origin 50 % 100 %), klatki 0/35/70/100 %
        val f = ((t % 1.6) / 1.6).toFloat()
        val (sx, sy, kat) = klatkaPlomienia(f)
        plomienDokladny(this, k, sx, sy, kat)
    }
}

/** Płomień świecy z rysunku strony (dwie ścieżki), każda skalowana i obracana wokół swojego dołu. */
private fun plomienDokladny(d: androidx.compose.ui.graphics.drawscope.DrawScope, k: Float, sx: Float, sy: Float, kat: Float) = with(d) {
    fun rysuj(dol: Float, kolor: Color, sciezka: Path.() -> Unit) {
        val p = Path().apply(sciezka)
        withTransform({
            scale(sx, sy, pivot = Offset(254 * k, dol * k))
            rotate(kat, pivot = Offset(254 * k, dol * k))
        }) { drawPath(p, kolor) }
    }
    rysuj(25f, kolor(0xf5b942)) {
        moveTo(254 * k, 11 * k)
        cubicTo(258.5f * k, 16 * k, 258.5f * k, 21 * k, 254 * k, 25 * k)
        cubicTo(249.5f * k, 21 * k, 249.5f * k, 16 * k, 254 * k, 11 * k)
        close()
    }
    rysuj(23.5f, kolor(0xfff4cf)) {
        moveTo(254 * k, 16.5f * k)
        cubicTo(256 * k, 19 * k, 256 * k, 21.5f * k, 254 * k, 23.5f * k)
        cubicTo(252 * k, 21.5f * k, 252 * k, 19 * k, 254 * k, 16.5f * k)
        close()
    }
}

/** CSS ease-in-out (cubic-bezier 0,42 0 0,58 1). */
private val easeInOut = androidx.compose.animation.core.CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private fun wygladzenie(x: Float) = easeInOut.transform(x.coerceIn(0f, 1f))

/** tvmlPanelAndrzejkiFlame: 0 % i 100 % (1, 1, −2°), 35 % (0,93, 1,07, 2°), 70 % (1,04, 0,95, −1°). */
private fun klatkaPlomienia(f: Float): Triple<Float, Float, Float> {
    val klatki = listOf(0f to Triple(1f, 1f, -2f), 0.35f to Triple(0.93f, 1.07f, 2f), 0.7f to Triple(1.04f, 0.95f, -1f), 1f to Triple(1f, 1f, -2f))
    val i = klatki.indexOfLast { it.first <= f }.coerceIn(0, klatki.size - 2)
    val (f0, a) = klatki[i]
    val (f1, b) = klatki[i + 1]
    val u = wygladzenie((f - f0) / (f1 - f0))
    fun m(x: Float, y: Float) = x + (y - x) * u
    return Triple(m(a.first, b.first), m(a.second, b.second), m(a.third, b.third))
}

private const val ANDRZEJKI_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="180 -14 100 72">
<defs><radialGradient id="AG" cx="50%" cy="50%" r="50%"><stop offset="0%" stop-color="#f5b942" stop-opacity="0.55"/><stop offset="100%" stop-color="#f5b942" stop-opacity="0"/></radialGradient>
<linearGradient id="AW" x1="0%" y1="0%" x2="100%" y2="0%"><stop offset="0%" stop-color="#e7d3a8"/><stop offset="45%" stop-color="#f8ecd0"/><stop offset="100%" stop-color="#d9c08a"/></linearGradient></defs>
<path d="M 210 6 A 15 15 0 1 0 226 30 A 12 12 0 0 1 210 6 Z" fill="#f5d98a"/>
<circle cx="254" cy="24" r="13" fill="url(#AG)"/>
<rect x="249" y="28" width="10" height="22" rx="2" fill="url(#AW)" stroke="#b7791f" stroke-width="0.6"/>
<line x1="254" y1="28" x2="254" y2="24.5" stroke="#2a1300" stroke-width="1"/>
</svg>"""

private const val SANIE_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="180 -10 120 70">
<defs><linearGradient id="GR" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="#fff6d1"/><stop offset="50%" stop-color="#e5c158"/><stop offset="100%" stop-color="#9e7618"/></linearGradient></defs>
<path d="M 192 42 Q 228 24 276 8" stroke="url(#GR)" stroke-width="1.8" stroke-dasharray="2 3" fill="none"/>
<path d="M 196 45 Q 232 30 274 14" stroke="#f5e2a8" stroke-opacity="0.45" stroke-width="1" fill="none"/>
<g transform="rotate(-16 235 22)">
<path d="M 198 28 L 222 28 Q 228 28 231 21 L 226 21 Q 224 25 220 25 L 202 25 Q 198 20 195 22 Z" fill="url(#GR)"/>
<path d="M 196 31 L 225 31 Q 231 31 234 26" stroke="url(#GR)" stroke-width="1.6" fill="none" stroke-linecap="round"/>
<circle cx="214" cy="17" r="4.2" fill="url(#GR)"/>
<path d="M 208 25 Q 214 19 221 25 Z" fill="url(#GR)"/>
<circle cx="204" cy="20" r="5.2" fill="#b91c1c" stroke="url(#GR)" stroke-width="1.2"/>
<path d="M 222 21 Q 236 22 248 18" stroke="url(#GR)" stroke-width="1" fill="none"/>
<ellipse cx="253" cy="19" rx="7.5" ry="3.6" fill="url(#GR)"/>
<path d="M 258 17 L 262 11 L 266 12 L 261 19 Z" fill="url(#GR)"/>
<path d="M 262 11 L 260 6 M 262 11 L 265 7" stroke="url(#GR)" stroke-width="1.3" stroke-linecap="round"/>
<path d="M 248 21 L 243 27 M 251 21 L 248 27 M 257 21 L 263 26 M 259 20 L 267 24" stroke="url(#GR)" stroke-width="1.4" stroke-linecap="round"/>
<circle cx="266.5" cy="12" r="1.8" fill="#ef233c"/>
</g>
<path d="M 204 12 L 205.2 15.2 L 208.5 16.4 L 205.2 17.6 L 204 20.8 L 202.8 17.6 L 199.5 16.4 L 202.8 15.2 Z" fill="#fff6d1"/>
<circle cx="228" cy="6" r="1.6" fill="#fff6d1"/>
</svg>"""

/**
 * Ozdoba SVG na logo (viewBox logo 0 0 270 126 = 86 × 40 dp; [svg] ma własny viewBox o wymiarach [szer] × [wys]
 * jednostek) z cieniem jak feDropShadow: kształt rozmyty o [odchylenie], przesunięty o ([dx], [dy]), w kolorze [cien].
 */
private fun svgZCieniem(svg: String, gestosc: Float, szer: Float, wys: Float, dx: Float, dy: Float, odchylenie: Float, cien: Int): android.graphics.Bitmap {
    val k = 86f * gestosc / 270f // pikseli na jednostkę viewBoxu logo
    val w = (szer * k).toInt().coerceAtLeast(1)
    val h = (wys * k).toInt().coerceAtLeast(1)
    val rysunek = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    com.caverock.androidsvg.SVG.getFromString(svg)
        .renderToCanvas(android.graphics.Canvas(rysunek), android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()))
    val sigma = odchylenie * k
    val promien = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
    val przes = IntArray(2)
    val ksztalt = rysunek.extractAlpha(android.graphics.Paint().apply { maskFilter = android.graphics.BlurMaskFilter(promien, android.graphics.BlurMaskFilter.Blur.NORMAL) }, przes)
    val wynik = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(wynik).apply {
        drawBitmap(ksztalt, przes[0] + dx * k, przes[1] + dy * k, android.graphics.Paint().apply { color = cien })
        drawBitmap(rysunek, 0f, 0f, null)
    }
    ksztalt.recycle()
    rysunek.recycle()
    return wynik
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
