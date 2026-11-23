package pl.tvml

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

/**
 * Artykuł: baner zostaje u góry, pod nim „Wróć”, rozmiar tekstu i udostępnianie.
 * „Odsłuchaj” po lewej, ikony „Zapisz” i „Komentarze” po prawej, tagi na samym końcu (jak w projekcie).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtykulEkran(m: Magazyn, poczatkowy: Artykul) {
    val b = LocalBarwy.current
    val kontekst = LocalContext.current
    var pelny by remember(poczatkowy.id) { mutableStateOf(if (poczatkowy.tresc != null) poczatkowy else null) }
    var komentarze by remember { mutableStateOf(false) }
    var film by remember { mutableStateOf<String?>(null) }
    val lektor = remember { Lektor(kontekst.applicationContext) }
    DisposableEffect(Unit) { onDispose { lektor.zamknij() } }
    LaunchedEffect(poczatkowy.id) { if (pelny == null) pelny = m.artykul(poczatkowy.id) }
    // tłumaczenie na żądanie (API 1.8.0): po powrocie aplikacji z tła artykuł w przygotowaniu pobiera się jeszcze raz,
    // bo w międzyczasie mogło przyjść tłumaczenie
    LaunchedEffect(m.wznowienia) {
        if (m.wznowienia > 0 && pelny?.tlumaczenieWDrodze == true) m.artykul(poczatkowy.id)?.let { pelny = it }
    }
    // tłumaczenie od razu (API 1.8.1, translationEta „now”): serwer tłumaczy w tle, więc artykuł pobiera się co 4,5 s
    // najwyżej przez 90 s, póki aplikacja jest na ekranie; gotowe tłumaczenie podmienia tytuł i treść w miejscu
    LaunchedEffect(poczatkowy.id, pelny?.terminTlumaczenia, pelny?.przetlumaczony, m.wznowienia) {
        val start = System.currentTimeMillis()
        while (pelny?.tlumaczenieWDrodze == true && pelny?.terminTlumaczenia == "now" &&
            System.currentTimeMillis() - start < 90_000 && m.naEkranie
        ) {
            delay(4_500)
            if (!m.naEkranie) break
            m.artykul(poczatkowy.id)?.let { nowy -> if (nowy.przetlumaczony || nowy.terminTlumaczenia != "now") pelny = nowy }
        }
    }
    // „Pokaż oryginał po polsku” w przetłumaczonym artykule: ten sam artykuł z lang=pl, a drugie stuknięcie wraca do tłumaczenia
    var oryginal by remember(poczatkowy.id) { mutableStateOf<Artykul?>(null) }
    var pokazOryginal by remember(poczatkowy.id) { mutableStateOf(false) }
    val zakresArtykulu = rememberCoroutineScope()
    val tlumaczenie = pelny ?: poczatkowy
    val a = if (pokazOryginal) oryginal ?: tlumaczenie else tlumaczenie
    // tekst po polsku przy innym języku aplikacji (bez tłumaczenia albo oryginał): TalkBack czyta go polskim głosem
    val polskiTekst = Jezyk.biezacy != "pl" && (a.jezyk == "pl" || pokazOryginal)
    fun pl(t: String): AnnotatedString = if (polskiTekst) AnnotatedString(t, SpanStyle(localeList = LocaleList("pl-PL"))) else AnnotatedString(t)
    val wielkosc = listOf(16, 18, 21)[m.rozmiar.coerceIn(0, 2)]
    val przewijanie = rememberScrollState()
    // klucze to treść: ten sam artykuł po przetłumaczeniu ma inną treść
    val bloki = remember(pelny?.tresc, oryginal?.tresc, pokazOryginal) {
        (if (pokazOryginal && oryginal != null) oryginal else pelny)?.tresc?.let { Html.bloki(it) }
    }

    Column(Modifier.fillMaxSize().background(b.karta)) {
        // pasek artykułu z postępem czytania
        Box {
            Row(Modifier.fillMaxWidth().height(44.dp).background(b.karta).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { m.wroc() }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ikony.wstecz, null, tint = b.tekst, modifier = Modifier.size(20.dp))
                    Text(tl("app.back", "Wróć"), color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { m.ustawRozmiar((m.rozmiar + 1) % 3) }
                        .semantics { contentDescription = tl("app.textSize", "Rozmiar tekstu") },
                    contentAlignment = Alignment.Center,
                ) { Text("Aa", color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { udostepnij(kontekst, a.tytul, a.link) }
                        .semantics { contentDescription = tl("app.share", "Udostępnij") },
                    contentAlignment = Alignment.Center,
                ) { Icon(Ikony.udostepnij, null, tint = b.tekst, modifier = Modifier.size(20.dp)) }
            }
            // postęp czytania liczony przy rysowaniu, żeby przewijanie nie przebudowywało ekranu
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).drawBehind {
                    val postep = if (przewijanie.maxValue > 0) przewijanie.value.toFloat() / przewijanie.maxValue else 0f
                    drawRect(Barwy.zloto, size = Size(size.width * postep.coerceIn(0f, 1f), size.height))
                },
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))

        Column(Modifier.fillMaxSize().verticalScroll(przewijanie).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (a.kategoria.isNotEmpty()) EtykietaKategorii(a)
            Text(pl(a.tytul), color = b.tekst, fontSize = (wielkosc + 10).sp, lineHeight = (wielkosc + 14).sp, fontWeight = FontWeight.Black, fontFamily = Szeryf)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Barwy.granat), contentAlignment = Alignment.Center) {
                    Text(inicjaly(a.autor), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(a.autor.ifEmpty { tl("app.newsroom", "Redakcja TVML") }, color = b.tekst, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(opisDaty(a, pelny), color = b.slaby, fontSize = 12.sp)
                }
            }
            // przetłumaczony artykuł: oryginał po polsku na życzenie (zwykły odnośnik, bez notki o tłumaczeniu)
            if (tlumaczenie.przetlumaczony) {
                Text(
                    if (pokazOryginal) tl("app.showTranslation", "Pokaż tłumaczenie") else tl("app.originalPolish", "Pokaż oryginał po polsku"),
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) {
                        if (pokazOryginal) pokazOryginal = false
                        else zakresArtykulu.launch {
                            if (oryginal == null) oryginal = m.artykul(poczatkowy.id, jezyk = "pl")
                            pokazOryginal = oryginal != null
                        }
                    }.padding(vertical = 4.dp),
                    color = b.link, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // lektor czyta polskim głosem, więc w przetłumaczonym tekście go nie ma (tak robi strona)
                if (a.jezyk == "pl") Row(
                    Modifier.height(40.dp).clip(CircleShape).background(Barwy.granat)
                        .clickable(role = Role.Button) { lektor.przelacz(tekstDoCzytania(a, if (pokazOryginal) oryginal else pelny)) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(if (lektor.mowi) Ikony.stop else Ikony.glosnik, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(if (lektor.mowi) tl("app.stopListening", "Zatrzymaj") else tl("app.listen", "Odsłuchaj"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                val zapisany = a.id in m.zapisane
                PrzyciskObrysowany(if (zapisany) Ikony.zakladkaPelna else Ikony.zakladka, if (zapisany) tl("app.unsave", "Usuń z zapisanych") else tl("app.save", "Zapisz")) { m.przelaczZapis(a) }
                PrzyciskObrysowany(Ikony.komentarze, tl("app.comments", "Komentarze") + ": ${a.komentarze}") { komentarze = true }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Obraz(a.miniatura(1200), Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)))
                if (a.zrodloZdjecia.isNotEmpty()) Text(a.zrodloZdjecia, color = b.slaby, fontSize = 12.sp)
            }
            // tłumaczenie w przygotowaniu: jedno zdanie w wybranym języku nad polskim tekstem, bez paska i ikony
            if (a.tlumaczenieWDrodze && !pokazOryginal) {
                Text(
                    when (a.terminTlumaczenia) {
                        "now" -> tl("app.translationPendingNow", "Tłumaczenie w przygotowaniu — za chwilę. Poniżej oryginalna wersja po polsku.")
                        "morning" -> tl("app.translationPendingNight", "Tłumaczenie w przygotowaniu — będzie rano. Poniżej oryginalna wersja po polsku.")
                        else -> tl("app.translationPending", "Tłumaczenie w przygotowaniu. Poniżej oryginalna wersja po polsku.")
                    },
                    color = b.slaby, fontSize = 14.sp, lineHeight = 19.sp,
                )
            }
            if (bloki != null) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        bloki.forEachIndexed { i, blok ->
                            when (blok) {
                                is Html.Blok.Akapit -> Text(
                                    pl(blok.tekst), color = b.tekst, fontSize = wielkosc.sp, lineHeight = (wielkosc * 1.5f).sp,
                                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                is Html.Blok.Srodtytul -> Text(pl(blok.tekst), color = b.tekst, fontSize = (wielkosc + 4).sp, fontWeight = FontWeight.Black, fontFamily = Szeryf)
                                is Html.Blok.Obraz -> Obraz(Siec.obraz(blok.adres, 1200), Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)))
                                is Html.Blok.Film -> Box(
                                    Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp))
                                        .clickable(role = Role.Button, onClickLabel = tl("app.playVideo", "Odtwórz film")) { film = blok.id },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Obraz("https://i.ytimg.com/vi/${blok.id}/hqdefault.jpg", Modifier.matchParentSize())
                                    Box(Modifier.size(60.dp).clip(CircleShape).background(Barwy.zloto), contentAlignment = Alignment.Center) {
                                        Icon(Ikony.odtworz, tl("app.playVideo", "Odtwórz film"), tint = kolor(0x1d1400), modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text(pl(a.lead), color = b.tekst, fontSize = wielkosc.sp, lineHeight = (wielkosc * 1.5f).sp, fontWeight = FontWeight.SemiBold)
                Postep()
            }
            if (a.tagi.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    a.tagi.forEach { t ->
                        Text(
                            "#$t", Modifier.clip(CircleShape).background(b.link.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 5.dp),
                            color = b.link, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (komentarze) Komentarze(m, a) { komentarze = false }
    film?.let { Odtwarzacz(it) { film = null } }
}

@Composable
private fun PrzyciskObrysowany(ikona: androidx.compose.ui.graphics.vector.ImageVector, opis: String, akcja: () -> Unit) {
    val b = LocalBarwy.current
    Box(
        Modifier.size(44.dp, 40.dp).clip(CircleShape).border(1.dp, b.ramka, CircleShape).clickable(role = Role.Button, onClick = akcja)
            .semantics { contentDescription = opis },
        contentAlignment = Alignment.Center,
    ) { Icon(ikona, null, tint = b.tekst, modifier = Modifier.size(18.dp)) }
}

private fun opisDaty(a: Artykul, pelny: Artykul?): String {
    val czesci = mutableListOf<String>()
    a.data?.let { czesci += Daty.dzien(it) }
    val slowa = (pelny?.tresc?.let { Html.tekst(it) } ?: a.lead).split(' ').count { it.isNotBlank() }
    czesci += tl("app.readingMinutes", "czytanie ok. {n} min", maxOf(1, slowa / 200))
    if (a.odslony > 0) czesci += Jezyk.liczba("app.views", a.odslony.toLong(), Triple("odsłona", "odsłony", "odsłon"))
    return czesci.joinToString(" · ")
}

private fun tekstDoCzytania(a: Artykul, pelny: Artykul?): List<String> =
    listOf(a.tytul + ".") + (pelny?.tresc?.let { Html.akapity(it) } ?: listOf(a.lead))

private fun inicjaly(s: String): String {
    val litery = s.split(' ').mapNotNull { it.firstOrNull() }.take(2)
    return if (litery.isEmpty()) "TV" else String(litery.toCharArray()).uppercase()
}

/** „Odsłuchaj”: czyta artykuł polskim głosem telefonu (silnik mowy Androida), także przy wygaszonym ekranie. */
class Lektor(private val kontekst: Context) {
    var mowi by mutableStateOf(false)
        private set
    private var tts: TextToSpeech? = null
    private var gotowy = false
    private var czeka: List<String>? = null

    fun przelacz(akapity: List<String>) {
        if (mowi) { zatrzymaj(); return }
        mowi = true
        val t = tts
        if (t == null) {
            czeka = akapity
            tts = TextToSpeech(kontekst) { stan ->
                gotowy = stan == TextToSpeech.SUCCESS
                if (!gotowy) { mowi = false; komunikat(tl("app.ttsMissing", "Telefon nie ma silnika mowy.")); return@TextToSpeech }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { if (id == "koniec") naGlownym { mowi = false } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { naGlownym { mowi = false } }
                })
                czeka?.let { mow(it) }
                czeka = null
            }
        } else if (gotowy) mow(akapity)
    }

    private fun mow(akapity: List<String>) {
        val t = tts ?: return
        val jezyk = t.setLanguage(Locale.forLanguageTag("pl-PL"))
        if (jezyk == TextToSpeech.LANG_MISSING_DATA || jezyk == TextToSpeech.LANG_NOT_SUPPORTED) {
            mowi = false
            komunikat(tl("app.ttsNoPolish", "Brak polskiego głosu. Dodasz go w ustawieniach telefonu: Zamiana tekstu na mowę."))
            return
        }
        // silnik mowy przyjmuje ograniczoną długość tekstu, więc akapit po akapicie
        val granica = TextToSpeech.getMaxSpeechInputLength() - 1
        val kawalki = akapity.flatMap { it.chunked(granica) }
        kawalki.forEachIndexed { i, k ->
            t.speak(k, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == kawalki.size - 1) "koniec" else "a$i")
        }
    }

    private fun naGlownym(akcja: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(akcja)
    }

    private fun komunikat(tekst: String) = naGlownym { Toast.makeText(kontekst, tekst, Toast.LENGTH_LONG).show() }

    fun zatrzymaj() {
        tts?.stop()
        mowi = false
    }

    fun zamknij() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        mowi = false
    }
}

/** Komentarze czytelników (zatwierdzone przez redakcję). Dodawanie z aplikacji przyjdzie z nowym API. */
@Composable
fun Komentarze(m: Magazyn, a: Artykul, zamknij: () -> Unit) {
    val b = LocalBarwy.current
    val kontekst = LocalContext.current
    var lista by remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(a.id) { lista = m.komentarze(a) }
    Arkusz(tl("app.comments", "Komentarze"), zamknij) {
        val l = lista
        when {
            l == null -> Postep()
            l.isEmpty() -> Text(tl("app.noComments", "Nie ma jeszcze komentarzy."), color = b.slaby, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
            else -> l.forEach { k ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(k.s("author_name").ifEmpty { k.s("author") }, color = b.tekst, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(Html.tekst(k.s("content").ifEmpty { k.s("text") }), color = b.tekst, fontSize = 15.sp)
                    Text(k.s("created_at").ifEmpty { k.s("date") }, color = b.slaby, fontSize = 12.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
            }
        }
        Text(
            tl("app.addComment", "Dodaj komentarz na tvml.pl"),
            Modifier.padding(vertical = 16.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { otworzStrone(kontekst, a.link) }.padding(4.dp),
            color = b.link, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}
