package pl.tvml

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Start: kategorie i bloki w kolejności z panelu (karuzela, pogoda, wpisy, wideo, gminy, fotorelacje…). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartEkran(m: Magazyn) {
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    val zakres = rememberCoroutineScope()
    var kategoria by rememberSaveable { mutableStateOf<String?>(null) }
    val stan = rememberLazyListState()
    PullToRefreshBox(
        isRefreshing = m.laduje,
        onRefresh = { zakres.launch { m.odswiezTeraz(wymus = true); kategoria?.let { m.kategoria(it) } } },
        modifier = Modifier.fillMaxSize().background(b.tlo),
    ) {
        LazyColumn(state = stan, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "kategorie") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(m.kategorie, key = { it.nazwa }) { k ->
                        val wybrana = kategoria == k.slug
                        Box(
                            Modifier.height(34.dp).clip(CircleShape)
                                .background(if (wybrana) m.motyw.akcent.w(ciemny) else b.karta)
                                .border(1.dp, if (wybrana) Color.Transparent else b.ramka, CircleShape)
                                .clickable(role = Role.Tab) {
                                    kategoria = k.slug
                                    k.slug?.let { s -> zakres.launch { m.kategoria(s) } }
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(k.nazwa, color = if (wybrana) m.motyw.napisNaAkcencie.w(ciemny) else b.tekst, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            m.blad?.let { item(key = "blad") { Text(it, color = b.slaby, fontSize = 14.sp) } }
            if (m.najnowsze.isEmpty() && m.laduje) item(key = "laduje") { Postep() }
            val slug = kategoria
            if (slug != null) {
                val lista = m.wKategorii[slug].orEmpty()
                if (lista.isEmpty()) item(key = "kat-laduje") { Postep() }
                lista.firstOrNull()?.let { a -> item(key = "kat-$slug-${a.id}") { KartaArtykulu(a, 260.dp) } }
                items(lista.drop(1), key = { "kat-$slug-${it.id}" }) { WierszArtykulu(it) }
            } else {
                m.bloki.forEach { blok -> blok(m, blok) }
            }
        }
    }
}

/** Jeden blok ekranu Start, tak jak ustawia go redakcja w panelu (Page builder). */
private fun androidx.compose.foundation.lazy.LazyListScope.blok(m: Magazyn, blok: BlokStartu) {
    val tytul: () -> Unit = {
        if (!blok.ukryjTytul && blok.tytul.isNotEmpty()) item(key = "${blok.id}-tytul") { Naglowek(blok.tytul) }
    }
    when (blok.typ) {
        "hero" -> if (m.najnowsze.isNotEmpty()) item(key = blok.id) {
            Karuzela(m.najnowsze.take(if (blok.limit > 0) blok.limit else 5))
        }
        "weather" -> item(key = blok.id) { KafelPogody(m) }
        "alert" -> item(key = blok.id) {
            val kontekst = LocalContext.current
            Row(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(kolor(0xc8102e))
                    .clickable(role = Role.Button) { otworzStrone(kontekst, Siec.ALERT) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ikony.alarm, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text("  " + tl("app.reportIncident", "Zgłoś zdarzenie (Alert TVML)"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        "video" -> {
            val filmy = m.filmy
            if (filmy.isNotEmpty()) {
                tytul()
                if (blok.limit == 1) item(key = blok.id) {
                    KafelFilmu(filmy.first(), Modifier.fillMaxWidth())
                } else item(key = blok.id) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filmy.take(if (blok.limit > 0) blok.limit else 6), key = { it.id }) { f -> KafelFilmu(f, Modifier.width(220.dp)) }
                    }
                }
            }
        }
        "posts" -> {
            val limit = if (blok.limit > 0) blok.limit else 4
            val lista = if (blok.kategoria != null) m.wKategorii[blok.kategoria].orEmpty().take(limit)
            else {
                val karuzela = m.bloki.firstOrNull { it.typ == "hero" }?.let { if (it.limit > 0) it.limit else 5 } ?: 0
                m.najnowsze.drop(karuzela).take(limit)
            }
            if (lista.isNotEmpty()) {
                tytul()
                items(lista, key = { "${blok.id}-${it.id}" }) { WierszArtykulu(it) }
            }
        }
        "mostread" -> {
            val lista = m.najczytniejsze.take(if (blok.limit > 0) blok.limit else 5)
            if (lista.isNotEmpty()) {
                tytul()
                item(key = blok.id) { Najczytniejsze(lista) }
            }
        }
        "gallery" -> {
            val lista = m.wKategorii["_fotorelacje"].orEmpty()
            if (lista.isNotEmpty()) tytul()
            item(key = blok.id) {
                LaunchedEffect(Unit) { if (m.wKategorii["_fotorelacje"] == null) m.fotorelacje(blok.limit) }
                if (lista.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(lista, key = { it.id }) { a -> KartaArtykulu(a, 200.dp, Modifier.width(260.dp)) }
                }
            }
        }
        "counties" -> if (m.gminy.isNotEmpty()) {
            tytul()
            item(key = blok.id) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(m.gminy, key = { it.id }) { g -> PigulkaGminy(g) }
                }
            }
        }
        "social" -> {
            tytul()
            item(key = blok.id) { Spolecznosc() }
        }
        // sonda tylko wtedy, gdy trwa, a w pozostałym czasie w tym miejscu kalendarz wydarzeń (/api/v1/spotlight)
        "poll" -> when (val s = m.spotlight[blok.id]) {
            is Spotlight.ZSonda -> {
                if (!blok.ukryjTytul) item(key = "${blok.id}-tytul") { Naglowek(blok.tytul.ifEmpty { tl("app.poll", "Sonda") }) }
                item(key = "${blok.id}-sonda-${s.sonda.id}") { WidokSondy(m, s.sonda, blok) }
            }
            is Spotlight.ZKalendarzem -> {
                if (!blok.ukryjTytul) item(key = "${blok.id}-tytul-kalendarz") { Naglowek(tl("app.events", "Kalendarz wydarzeń")) }
                item(key = "${blok.id}-kalendarz") { KalendarzWydarzen(m, s.kalendarz) }
            }
            null -> {}
        }
        // przy bloku poll kalendarz jest w miejscu nieaktywnej sondy, więc osobnego bloku nie powtarzamy
        "events" -> if (m.wydarzenia.isNotEmpty() && m.bloki.none { it.typ == "poll" }) {
            if (!blok.ukryjTytul) item(key = "${blok.id}-tytul") { Naglowek(blok.tytul.ifEmpty { tl("app.events", "Kalendarz wydarzeń") }) }
            item(key = blok.id) { ListaWydarzen(m, m.wydarzenia.take(if (blok.limit > 0) blok.limit else 5)) }
        }
    }
}

@Composable
fun Najczytniejsze(lista: List<Artykul>) {
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).padding(horizontal = 14.dp)) {
        lista.forEachIndexed { i, a ->
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { m.otworz(a) }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${i + 1}", Modifier.width(30.dp), color = m.motyw.akcent.w(LocalCiemny.current), fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = Szeryf)
                Text(a.tytul, Modifier.weight(1f), color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            if (i < lista.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
        }
    }
}

@Composable
fun PigulkaGminy(g: Gmina) {
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    Row(
        Modifier.height(44.dp).clip(CircleShape).background(b.karta).border(1.dp, b.ramka, CircleShape)
            .clickable(role = Role.Button) { m.otworz(Ekran.ZGminy(g)) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        coil3.compose.AsyncImage(g.herb, null, Modifier.size(22.dp, 26.dp))
        Text(g.nazwa, color = b.tekst, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Spolecznosc() {
    val b = LocalBarwy.current
    val kontekst = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((nazwa, adres) in SPOLECZNOSC) {
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { otworzStrone(kontekst, adres) },
                contentAlignment = Alignment.Center,
            ) {
                Text(nazwa, color = b.tekst, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

val SPOLECZNOSC = listOf(
    "Facebook" to "https://www.facebook.com/TVMLpl",
    "YouTube" to "https://www.youtube.com/@TVMLlive",
    "Instagram" to "https://www.instagram.com/tvmlpl/",
    "TikTok" to "https://www.tiktok.com/@tvml.pl",
)

@Composable
fun Karuzela(artykuly: List<Artykul>) {
    val b = LocalBarwy.current
    val strony = rememberPagerState { artykuly.size }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(strony, pageSpacing = 12.dp, modifier = Modifier.fillMaxWidth().height(300.dp)) { i ->
            KartaArtykulu(artykuly[i], 300.dp)
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.Center) {
            artykuly.indices.forEach { i ->
                val wybrany = i == strony.currentPage
                Box(Modifier.padding(horizontal = 3.dp).size(if (wybrany) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (wybrany) Barwy.zloto else b.ramka))
            }
        }
    }
}

/**
 * Kafel pogody na Start, 1:1 z iPhone'em (KartaPogody w StartWidok.swift, budowa 58 zatwierdzona przez Karola):
 * grafika sceny w proporcjach 1,85 : 1 z „POGODA W LESZNIE”, temperaturą, opisem i pigułkami prognozy, a pod nią
 * belka w kolorze podłoża sceny (śnieg, ziemia, piasek, woda, bruk Rynku) z medalem, datą, świętem albo
 * przedrostkiem motywu i imieninami ([PaletaBelki]). Zasada Karola: pod tytułem od razu temperatura, data
 * i imieniny razem na belce, w każdej scenie i motywie, w dzień i w nocy.
 */
@Composable
fun KafelPogody(m: Magazyn) {
    val p = m.pogoda
    // scena ze strony: z harmonogramu motywów (theme.weatherScene) albo ogólna z panelu; stan i noc z /api/v1/weather
    val stan = p?.ikona?.ifEmpty { null } ?: "cloudy"
    val noc = m.nocSceny()
    val opcje = m.opcjeSceny(stan)
    val scena = m.scenaPogody(stan, m.chwila())
    val svg = m.scenaSvgTeraz(m.chwila())
    val teraz = m.chwila()
    // pasek daty motywu z panelu (API 1.8.3 theme.dateBar albo etap z harmonogramu); bez niego reguły jak na iPhonie
    val pasek = m.pasekDatyTeraz(teraz)
    val paleta = PaletaBelki.dla(scena, noc, svg != null, teraz, pasek, m.motyw.scena())
    val data = dataKarty(m.dzisiaj, teraz.toLocalDate())
    // config.today z innego dnia (bez sieci po północy): bez wczorajszych imion i święta; w motywie imiona z dateBar.days[]
    val dzisiajAktualne = m.dzisiaj?.data.let { it == null || it == teraz.toLocalDate() }
    val imieniny = m.dzisiaj?.imieniny.orEmpty().takeIf { it.isNotEmpty() && dzisiajAktualne }
        ?.let { tl("app.nameDays", "Imieniny") + ": " + it.joinToString(", ") }
    val swieto = m.dzisiaj?.swieto?.takeIf { dzisiajAktualne }
    val (wyroznienie, imionaLinia) = PaletaBelki.linie(scena, svg != null, swieto, imieniny, teraz, pasek, m.dzisiaj?.data)
    val tytul = tl("app.weatherLeszno", "Pogoda w Lesznie")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) { m.pokaz(ZakladkaApp.Cel.Pogoda) }
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    tytul + ": " + (p?.let { Pogoda.stopnie(it.temperatura) } ?: "") + ", " + p?.opis.orEmpty(), data, wyroznienie, imionaLinia,
                ).joinToString(". ")
            },
    ) {
        // grafika: co najmniej 1,85 : 1, przy dużym tekście rośnie razem z nim
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().heightIn(min = maxWidth / 1.85f)) {
                ScenaStrony(scena, stan, noc, opcje, p?.efekt, Modifier.matchParentSize(), svg)
                // przyciemnienie pod tekstem: mocniej u góry, słabnie ku scenie, i lekko z lewej pod temperaturą
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.34f), 0.5f to Color.Black.copy(alpha = 0.2f), 0.78f to Color.Transparent),
                    ),
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.24f), 0.38f to Color.Black.copy(alpha = 0.1f), 0.6f to Color.Transparent),
                    ),
                )
                Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp).clearAndSetSemantics { }) {
                    val styl = TextStyle(color = Color.White, shadow = cienKarty)
                    Text(tytul.uppercase(Jezyk.locale), style = styl.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p?.let { Pogoda.stopnie(it.temperatura) + "C" } ?: "–", style = styl.copy(fontSize = 30.sp, fontWeight = FontWeight.Black))
                            Text(p?.opis.orEmpty(), style = styl.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            p?.dni.orEmpty().drop(1).take(3).forEach { d ->
                                // pigułka jak na karcie ze strony: prognoza czytelna także na wieży Ratusza i śniegu
                                Column(
                                    Modifier.clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.26f)).padding(horizontal = 6.dp, vertical = 5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(d.nazwa, style = styl.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f)))
                                    Text(Pogoda.stopnie(d.max), style = styl.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }
        }
        // belka pod grafiką w kolorze podłoża sceny: medal, data, święto albo przedrostek motywu, imieniny
        Box(Modifier.fillMaxWidth().background(paleta.tlo).clearAndSetSemantics { }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MedalBelki(paleta)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(data, color = paleta.data, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    wyroznienie?.let { Text(it, color = paleta.data, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 15.sp) }
                    imionaLinia?.let { Text(it, color = paleta.drugaLinia, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 15.sp) }
                }
            }
            paleta.akcent?.let { Box(Modifier.fillMaxWidth().height(3.dp).background(it)) }
        }
    }
}

/** Cień napisów na grafice karty pogody (na iPhonie czarny 60 %, promień 4 pt). */
private val cienKarty = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset.Zero, 10f)

/**
 * „Poniedziałek, 23 listopada”: data z config.today.label (po uk/en przetłumaczona przez serwer) bez roku,
 * z wielkiej litery; bez niej albo gdy config.today jest z innego dnia (bez sieci po północy) z kalendarza
 * telefonu w bieżącym języku.
 */
fun dataKarty(d: Dzisiaj?, dzien: java.time.LocalDate = java.time.LocalDate.now(Daty.strefa)): String {
    val aktualna = d?.data == null || d.data == dzien
    val z = if (aktualna) d?.etykieta.orEmpty().replace(Regex("""\s*\d{4}(\s*р\.)?\s*$"""), "") else ""
    val data = z.ifEmpty { Daty.dzienTygodniaIData(dzien) }
    return data.replaceFirstChar { it.titlecase(Jezyk.locale) }
}

/** Lista jednego działu (zakładka Sport, subportale z panelu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaKategorii(m: Magazyn, slug: String, tytul: String) {
    val b = LocalBarwy.current
    val zakres = rememberCoroutineScope()
    val lista = m.wKategorii[slug].orEmpty()
    var laduje by remember { mutableStateOf(false) }
    LaunchedEffect(slug) { if (m.wKategorii[slug] == null) m.kategoria(slug) }
    PullToRefreshBox(isRefreshing = laduje, onRefresh = { zakres.launch { laduje = true; m.kategoria(slug); laduje = false } }, modifier = Modifier.fillMaxSize().background(b.tlo)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "tytul") { Naglowek(tytul, wielkosc = 26) }
            if (lista.isEmpty()) item(key = "laduje") { Postep() }
            lista.firstOrNull()?.let { a -> item(key = a.id) { KartaArtykulu(a, 240.dp) } }
            items(lista.drop(1), key = { it.id }) { WierszArtykulu(it) }
        }
    }
}

/** Wiadomości z jednej gminy powiatu albo dowolna lista z API (fotorelacje, najczęściej czytane, zapisane). */
@Composable
fun ListaArtykulow(tytul: String, herb: String? = null, pusta: String = tl("app.noResults", "Brak wiadomości."), wczytaj: suspend () -> List<Artykul>) {
    val b = LocalBarwy.current
    var lista by remember { mutableStateOf<List<Artykul>?>(null) }
    LaunchedEffect(tytul) { lista = wczytaj() }
    LazyColumn(Modifier.fillMaxSize().background(b.tlo), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "wroc") { Wroc() }
        item(key = "tytul") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (herb != null) coil3.compose.AsyncImage(herb, null, Modifier.size(30.dp, 36.dp))
                Naglowek(tytul, wielkosc = 26)
            }
        }
        val l = lista
        when {
            l == null -> item(key = "laduje") { Postep() }
            l.isEmpty() -> item(key = "pusta") { Text(pusta, color = b.slaby, fontSize = 15.sp, modifier = Modifier.padding(vertical = 24.dp)) }
            else -> items(l, key = { it.id }) { WierszArtykulu(it) }
        }
    }
}

/** Gminy powiatu (z Więcej): herby i nazwy z panelu. */
@Composable
fun ListaGmin(m: Magazyn) {
    val b = LocalBarwy.current
    LazyColumn(Modifier.fillMaxSize().background(b.tlo), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "wroc") { Wroc() }
        item(key = "tytul") { Naglowek(tl("app.communes", "Gminy powiatu"), wielkosc = 26) }
        if (m.gminy.isEmpty()) item(key = "laduje") { Postep() }
        items(m.gminy, key = { it.id }) { g ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { m.otworz(Ekran.ZGminy(g)) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                coil3.compose.AsyncImage(g.herb, null, Modifier.size(30.dp, 36.dp))
                Text(g.nazwa, Modifier.weight(1f), color = b.tekst, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Icon(Ikony.dalej, null, tint = b.slaby, modifier = Modifier.size(18.dp))
            }
        }
    }
}
