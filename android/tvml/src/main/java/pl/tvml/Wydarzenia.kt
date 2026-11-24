package pl.tvml

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Sonda na Start (Karol, 22.11.2026): najpierw same odpowiedzi do wyboru i „Zagłosuj”, wyniki dopiero po głosie.
 * Głos zapamiętuje tylko telefon (id sondy → odpowiedź), bez żadnego identyfikatora.
 */
@Composable
fun WidokSondy(m: Magazyn, sonda: Sonda, blok: BlokStartu) {
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    val zakres = rememberCoroutineScope()
    val akcent = m.motyw.akcent.w(ciemny)
    var wybor by rememberSaveable(sonda.id) { mutableStateOf<String?>(null) }
    var wysyla by remember { mutableStateOf(false) }
    var komunikat by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(sonda.pytanie, color = b.tekst, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, fontFamily = Szeryf)
        val moj = m.glosy[sonda.id]
        if (moj != null || !sonda.otwarta) {
            WynikiSondy(m, sonda, moj.orEmpty())
        } else {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sonda.opcje.forEach { o ->
                    val tak = wybor == o.klucz
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(10.dp)).background(b.tlo)
                            .selectable(selected = tak, role = Role.RadioButton) { wybor = o.klucz }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = tak, onClick = null, modifier = Modifier.padding(horizontal = 8.dp),
                            colors = RadioButtonDefaults.colors(selectedColor = akcent, unselectedColor = b.slaby),
                        )
                        Text(o.etykieta, color = b.tekst, fontSize = 15.sp)
                    }
                }
            }
            val mozna = wybor != null && !wysyla
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(akcent.copy(alpha = if (wybor == null) 0.4f else 1f))
                    .clickable(enabled = mozna, role = Role.Button) {
                        val k = wybor ?: return@clickable
                        zakres.launch {
                            wysyla = true
                            komunikat = m.glosuj(sonda, k, blok)
                            wysyla = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (wysyla) tl("app.sending", "Wysyłam…") else tl("app.vote", "Zagłosuj"), color = m.motyw.napisNaAkcencie.w(ciemny), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        sonda.doKiedy?.let { Text(tl("app.pollUntil", "Sonda trwa do {d}").replace("{d}", Sonda.doKiedyTekst(it)), color = b.slaby, fontSize = 12.sp) }
        komunikat?.let { Text(it, color = b.slaby, fontSize = 13.sp) }
    }
}

/** Wyniki po głosie: paski z procentami, wyróżniona odpowiedź („Twój głos”) i liczba głosów. */
@Composable
private fun WynikiSondy(m: Magazyn, sonda: Sonda, moj: String) {
    val b = LocalBarwy.current
    val akcent = m.motyw.akcent.w(LocalCiemny.current)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sonda.opcje.forEach { o ->
            val twoj = o.klucz == moj
            Column(
                Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = "${o.etykieta}, ${o.procent}%, ${Sonda.glosyTekst(o.glosy)}" + if (twoj) ", " + tl("app.yourVote", "twój głos") else ""
                },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(o.etykieta, color = b.tekst, fontSize = 14.sp, fontWeight = if (twoj) FontWeight.Bold else FontWeight.Normal)
                    if (twoj) Text(tl("app.yourVote", "Twój głos"), color = akcent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${o.procent}%", color = b.tekst, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(b.ramka)) {
                    Box(
                        Modifier.fillMaxWidth(o.procent.coerceIn(0, 100) / 100f).fillMaxHeight().clip(CircleShape)
                            .background(if (twoj) akcent else b.slaby.copy(alpha = 0.6f)),
                    )
                }
            }
        }
        Text(tl("app.votesCast", "Oddano {votes}").replace("{votes}", Sonda.glosyTekst(sonda.razem)), color = b.slaby, fontSize = 12.sp)
    }
}

/**
 * Kalendarz wydarzeń w miejscu sondy: miesiąc (Pn–Nd, dziś w obwódce, dni z wydarzeniami z kropką), strzałki
 * od bieżącego miesiąca do miesiąca ostatniego wydarzenia. Pod spodem wydarzenia wybranego dnia: domyślnie dzisiaj,
 * a po stuknięciu dnia ten dzień (Karol, 22.11.2026), razem z wydarzeniami, które wtedy trwają.
 */
@Composable
fun KalendarzWydarzen(m: Magazyn, k: KalendarzSpot) {
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    val kontekst = LocalContext.current
    val akcent = m.motyw.akcent.w(ciemny)
    val baza = remember(k.miesiac) { runCatching { YearMonth.parse(k.miesiac) }.getOrNull() ?: YearMonth.now(Daty.strefa) }
    val maks = remember(k.dni, baza) {
        k.dni.maxOrNull()?.let { d -> runCatching { ChronoUnit.MONTHS.between(baza, YearMonth.from(LocalDate.parse(d))).toInt() }.getOrNull() }
            ?.coerceAtLeast(0) ?: 0
    }
    var przesuniecie by rememberSaveable { mutableIntStateOf(0) }
    var wybrany by rememberSaveable(k.dzisiaj) { mutableStateOf(k.dzisiaj) }
    var listaDnia by remember { mutableStateOf<List<Wydarzenie>?>(null) }
    var dzienNaStronie by remember { mutableStateOf(false) }
    LaunchedEffect(wybrany) {
        listaDnia = null
        dzienNaStronie = false
        // /api/v1/events?from=D&to=D, a bez sieci te z listy kalendarza, które zaczynają się tego dnia
        val z = m.wydarzeniaDnia(wybrany)
        if (z != null) {
            listaDnia = z
        } else {
            val juzSa = k.wydarzenia.filter { it.dzienISO == wybrany }
            listaDnia = juzSa
            dzienNaStronie = juzSa.isEmpty() && wybrany in k.dni
        }
    }
    val miesiac = baza.plusMonths(przesuniecie.toLong())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StrzalkaMiesiaca(Ikony.wstecz, tl("app.prevMonth", "Poprzedni miesiąc"), przesuniecie > 0) { przesuniecie-- }
            Text(Daty.miesiacIRok(miesiac.atDay(1)), Modifier.weight(1f), color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            StrzalkaMiesiaca(Ikony.dalej, tl("app.nextMonth", "Następny miesiąc"), przesuniecie < maks) { przesuniecie++ }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Daty.dniKalendarza().forEach {
                    Text(it, Modifier.weight(1f).clearAndSetSemantics { }, color = b.slaby, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
            }
            val puste = miesiac.atDay(1).dayOfWeek.value - 1
            val komorki = List<LocalDate?>(puste) { null } + (1..miesiac.lengthOfMonth()).map { miesiac.atDay(it) }
            komorki.chunked(7).forEach { tydzien ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tydzien.forEach { d ->
                        if (d == null) Spacer(Modifier.weight(1f).height(38.dp))
                        else DzienKalendarza(d, k, d.toString() == wybrany, Modifier.weight(1f)) { wybrany = d.toString() }
                    }
                    repeat(7 - tydzien.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // bez nagłówka wybranego dnia („Poniedziałek, 23 listopada · dziś”): Karol, 23.11: „tego nie powinno być”;
        // wybrany dzień widać w siatce kalendarza (jak na iPhonie)
        val l = listaDnia
        when {
            l == null -> Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = akcent, strokeWidth = 2.dp)
            }
            l.isNotEmpty() -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(b.tlo.copy(alpha = 0.5f))) {
                l.forEachIndexed { i, w ->
                    WierszWydarzenia(w, naDzien = wybrany) { m.otworz(w) }
                    if (i < l.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
                }
            }
            dzienNaStronie -> Odnosnik(tl("app.dayOnWebsite", "Szczegóły tego dnia są na tvml.pl")) { otworzStrone(kontekst, k.adres) }
            else -> Text(tl("app.noEventsDay", "Brak wydarzeń tego dnia."), color = b.slaby, fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Odnosnik(tl("app.eventSubmit", "Zgłoś wydarzenie")) { otworzStrone(kontekst, Siec.BAZA + "wydarzenia#dodaj") }
            Spacer(Modifier.weight(1f))
            Odnosnik(tl("app.eventsAllShort", "Wszystkie")) { otworzStrone(kontekst, k.adres) }
        }
    }
}

@Composable
private fun StrzalkaMiesiaca(ikona: ImageVector, opis: String, aktywna: Boolean, akcja: () -> Unit) {
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = aktywna, role = Role.Button, onClick = akcja).semantics { contentDescription = opis },
        contentAlignment = Alignment.Center,
    ) {
        Icon(ikona, null, tint = if (aktywna) m.motyw.akcent.w(LocalCiemny.current) else b.ramka, modifier = Modifier.size(20.dp))
    }
}

/** Dzień w kalendarzu: wybrany w kolorze akcentu, dziś w obwódce, z wydarzeniami pogrubiony i z kropką. */
@Composable
private fun DzienKalendarza(d: LocalDate, k: KalendarzSpot, wybrany: Boolean, modifier: Modifier, wybierz: () -> Unit) {
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    val akcent = m.motyw.akcent.w(ciemny)
    val iso = d.toString()
    val maWydarzenia = iso in k.dni
    val dzis = iso == k.dzisiaj
    val ksztalt = RoundedCornerShape(8.dp)
    Column(
        modifier.height(38.dp).clip(ksztalt).background(if (wybrany) akcent else Color.Transparent)
            .border(1.dp, if (dzis && !wybrany) b.slaby else Color.Transparent, ksztalt)
            .selectable(selected = wybrany, enabled = maWydarzenia || dzis, role = Role.Button, onClick = wybierz)
            .semantics { contentDescription = Daty.dzienMiesiaca(d) + (if (maWydarzenia) ": " + tl("app.hasEvents", "są wydarzenia") else "") + if (dzis) ", " + tl("app.today", "dziś").lowercase(Jezyk.locale) else "" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${d.dayOfMonth}", Modifier.clearAndSetSemantics { },
            color = if (wybrany) m.motyw.napisNaAkcencie.w(ciemny) else if (maWydarzenia) akcent else b.tekst,
            fontSize = 13.sp, fontWeight = if (maWydarzenia) FontWeight.Bold else FontWeight.Normal,
        )
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (maWydarzenia && !wybrany) akcent else Color.Transparent))
    }
}

@Composable
private fun Odnosnik(tekst: String, akcja: () -> Unit) {
    Text(
        tekst,
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = akcja).padding(vertical = 8.dp, horizontal = 2.dp),
        color = LocalBarwy.current.link, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
    )
}

/** Blok „Kalendarz wydarzeń” na Start (/api/v1/events), gdy w układzie nie ma bloku sondy. */
@Composable
fun ListaWydarzen(m: Magazyn, lista: List<Wydarzenie>) {
    val b = LocalBarwy.current
    val kontekst = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp))) {
            lista.forEachIndexed { i, w ->
                WierszWydarzenia(w) { m.otworz(w) }
                if (i < lista.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
            }
        }
        Odnosnik(tl("app.allEvents", "Wszystkie wydarzenia na tvml.pl")) { otworzStrone(kontekst, Siec.BAZA + "wydarzenia") }
    }
}

/**
 * Wiersz kalendarza: miniatura albo kafelek z dniem i miesiącem, tytuł, dzień, godzina i miejsce.
 * [naDzien]: dzień wybrany w kalendarzu („2026-11-23”), null = dziś. Wystawa, która trwa od 20 listopada, w dniu
 * 23 listopada ma na kafelku 23 LIS, a pod tytułem zakres „20 lis – 5 gru” (Karol, 23.11; jak na iPhonie).
 */
@Composable
fun WierszWydarzenia(w: Wydarzenie, naDzien: String? = null, akcja: () -> Unit) {
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    val dzienOdniesienia = naDzien?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(Daty.strefa)
    // kafelek: wybrany dzień, gdy wielodniowe już wtedy trwa, inaczej dzień rozpoczęcia
    val (dzienKafelka, miesiacKafelka) = if (w.trwaW(dzienOdniesienia)) {
        dzienOdniesienia.dayOfMonth.toString() to Daty.skrotMiesiaca(dzienOdniesienia).uppercase()
    } else {
        w.dzien to w.miesiac
    }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = akcja).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val miniatura = w.miniatura
        if (miniatura != null) {
            Obraz(miniatura, Modifier.size(72.dp, 56.dp).clip(RoundedCornerShape(10.dp)))
        } else {
            Column(
                Modifier.width(52.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp)).background(m.motyw.akcent.w(ciemny)).clearAndSetSemantics { },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(dzienKafelka, color = m.motyw.napisNaAkcencie.w(ciemny), fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, fontFamily = Szeryf)
                Text(miesiacKafelka, color = m.motyw.napisNaAkcencie.w(ciemny), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(w.tytul, color = b.tekst, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
            // przy wielodniowym zakres „20 lis – 5 gru”, inaczej dzień tygodnia i godzina; potem miejsce
            val czesci = if (w.wielodniowe) {
                listOf(w.zakres)
            } else {
                listOf(if (miniatura == null) w.dzienTygodnia else "${w.dzienTygodnia} ${w.dzien} ${w.miesiac.lowercase()}", w.godzina)
            }
            Text((czesci + w.miejsce).filter { it.isNotBlank() }.joinToString(" · "), color = b.slaby, fontSize = 12.sp, maxLines = 2)
        }
        Icon(Ikony.dalej, null, tint = b.slaby, modifier = Modifier.size(16.dp))
    }
}

/** Wydarzenie w aplikacji: zdjęcie, data i godzina, miejsce, organizator, bilety i opis (/api/v1/events/{id}). */
@Composable
fun WydarzenieEkran(m: Magazyn, poczatkowe: Wydarzenie) {
    val b = LocalBarwy.current
    val kontekst = LocalContext.current
    var pelne by remember(poczatkowe.id) { mutableStateOf<Wydarzenie?>(null) }
    LaunchedEffect(poczatkowe.id) { pelne = m.wydarzenie(poczatkowe.id) }
    val w = pelne ?: poczatkowe
    Column(Modifier.fillMaxSize().background(b.tlo).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Wroc()
        w.obraz?.let { adres ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Obraz(adres, Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp)))
                if (w.zrodloZdjecia.isNotEmpty()) Text(w.zrodloZdjecia, color = b.slaby, fontSize = 12.sp)
            }
        }
        Naglowek(w.tytul, wielkosc = 24)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Szczegol(Ikony.kalendarz, w.dataPelna)
            Szczegol(Ikony.miejsce, w.miejsce)
            Szczegol(Ikony.osoby, w.organizator)
            Szczegol(Ikony.bilet, w.bilety)
        }
        val opis = w.opis.ifEmpty { w.lead }
        if (opis.isNotEmpty()) SelectionContainer { Text(opis, color = b.tekst, fontSize = 16.sp, lineHeight = 24.sp) }
        w.artykulId?.let { id ->
            Odnosnik(tl("app.readMore", "Czytaj artykuł")) { m.otworzArtykul(id) }
        }
        if (w.zrodlo.isNotEmpty()) Odnosnik(tl("app.eventSource", "Źródło informacji")) { otworzWSystemie(kontekst, w.zrodlo.toUri()) }
        Odnosnik(tl("app.seeOnWebsite", "Zobacz na tvml.pl")) { otworzStrone(kontekst, w.adres) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Szczegol(ikona: ImageVector, tekst: String) {
    if (tekst.isEmpty()) return
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(ikona, null, tint = m.motyw.akcent.w(LocalCiemny.current), modifier = Modifier.padding(top = 1.dp).size(20.dp))
        Text(tekst, color = b.tekst, fontSize = 15.sp, lineHeight = 21.sp)
    }
}
