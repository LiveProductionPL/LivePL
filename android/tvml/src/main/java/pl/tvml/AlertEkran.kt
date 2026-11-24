package pl.tvml

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// „Zgłoś zdarzenie (Alert TVML)” w aplikacji: ten sam formularz co AlertWidok.swift na iPhonie (kolejność sekcji,
// napisy, sprawdzanie pól, błędy, potwierdzenie). Ustawienia z config.alerts i wysyłka w AlertSiec.kt,
// zdjęcie, film i położenie w AlertPliki.kt.

/** Czerwień przycisku „Zgłoś zdarzenie” i błędów, jak na stronie. */
private val czerwony = kolor(0xc8102e)
private val zielony = kolor(0x1f9d55)

/** Pola w kolejności na ekranie: przewinięcie do pierwszego do poprawy (także pole z error.field z serwera). */
private val kolejnoscPol = listOf("type", "title", "location", "description", "attachment", "reporter_name", "reporter_contact", "consent")

data class PotwierdzenieAlertu(val numer: String, val komunikat: String)

/** Błąd wysyłki; [strona] = przycisk „Otwórz formularz na tvml.pl” (brak sieci, błąd serwera, formularz wyłączony). */
data class BladWysylki(val tekst: String, val strona: Boolean)

/**
 * Stan formularza (FormularzAlertu na iPhonie). Trzymany w Magazynie (formularzAlertu), więc przetrwa przejście
 * do innej zakładki; własny zakres korutyn, żeby wysyłka nie urwała się przy zmianie ekranu.
 */
class FormularzAlertu(kontekst: Context, val ustawienia: UstawieniaAlertu, val przyklad: String? = null) {
    sealed interface Stan {
        data object Edycja : Stan
        data class Wysylanie(val postep: Float) : Stan
        data class Wyslane(val potwierdzenie: PotwierdzenieAlertu) : Stan
    }

    private val aplikacja: Context = kontekst.applicationContext
    private val prefs = aplikacja.getSharedPreferences("tvml", Context.MODE_PRIVATE)
    private val zakres = MainScope()

    var typ by mutableStateOf(ustawienia.typy.firstOrNull()?.id ?: "other")
    var tytul by mutableStateOf("")
    var miejsce by mutableStateOf("")
    var opis by mutableStateOf("")
    var imie by mutableStateOf(prefs.getString(KLUCZ_IMIE, null).orEmpty())
    var kontakt by mutableStateOf(prefs.getString(KLUCZ_KONTAKT, null).orEmpty())
    var zapamietaj by mutableStateOf(prefs.getBoolean(KLUCZ_ZAPAMIETAJ, true))
    var zgoda by mutableStateOf(false)
    var polozenie by mutableStateOf<PolozenieAlertu?>(null)
    var zalacznik by mutableStateOf<ZalacznikAlertu?>(null)
        private set
    var przygotowanie by mutableStateOf(false)
        private set
    var bladZalacznika by mutableStateOf<String?>(null)
        private set
    var szukaPolozenia by mutableStateOf(false)
        private set
    var bladPolozenia by mutableStateOf<String?>(null)
        private set
    var stan by mutableStateOf<Stan>(Stan.Edycja)
        private set
    var blad by mutableStateOf<BladWysylki?>(null)
        private set
    /** Pola do poprawy po „Wyślij” (etykiety na czerwono). */
    var bledyPol by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Rośnie przy każdym błędzie pól, żeby ekran przewinął do pierwszego także przy tych samych polach. */
    var probaZBledem by mutableIntStateOf(0)
        private set

    private var token: TokenAlertu? = null
    private var pobieranieTokenu: Job? = null
    @Volatile private var procent = -1

    init {
        if (przyklad != null) wypelnijPrzykladem(przyklad)
    }

    val wyslane: Boolean get() = stan is Stan.Wyslane

    /** Czy zamknięcie coś straci (wtedy pytanie „Odrzucić zgłoszenie?”). Imię i kontakt mogą być zapamiętane. */
    val maTresc: Boolean get() = !wyslane && (tytul.isNotEmpty() || opis.isNotEmpty() || miejsce.isNotEmpty() || zalacznik != null)

    // ---------- pola ----------

    private fun dlugosc(s: String): Int = s.trim().let { it.codePointCount(0, it.length) }

    /** Te same granice co serwer: tytuł i miejsce ≥ 3, opis ≥ 10, imię ≥ 2, kontakt ≥ 5 znaków, zgoda. */
    private fun sprawdz(): Boolean {
        val b = buildSet {
            if (dlugosc(tytul) < 3) add("title")
            if (dlugosc(miejsce) < 3) add("location")
            if (dlugosc(opis) < 10) add("description")
            if (dlugosc(imie) < 2) add("reporter_name")
            if (dlugosc(kontakt) < 5) add("reporter_contact")
            if (!zgoda) add("consent")
        }
        bledyPol = b
        if (b.isNotEmpty()) probaZBledem++
        return b.isEmpty()
    }

    // ---------- załącznik ----------

    /** Plik z galerii albo aparatu; [film] null = rodzaj z ContentResolver. [plikAparatu] znika po przygotowaniu. */
    fun wczytaj(zrodlo: Uri, film: Boolean? = null, plikAparatu: File? = null) {
        zakres.launch {
            przygotowanie = true
            bladZalacznika = null
            try {
                val mime = when (film) {
                    true -> "video/"
                    false -> "image/"
                    null -> aplikacja.contentResolver.getType(zrodlo).orEmpty()
                }
                val z = when {
                    mime.startsWith("video/") -> PlikiAlertu.film(aplikacja, zrodlo, ustawienia.maxFilmMB, ustawienia.maxFilmSekund)
                    mime.isEmpty() || mime.startsWith("image/") -> PlikiAlertu.zdjecie(aplikacja, zrodlo, ustawienia.maxZdjecieMB)
                    else -> throw BladZalacznika.Format
                }
                ustawZalacznik(z)
            } catch (e: BladZalacznika) {
                bladZalacznika = e.komunikat
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                bladZalacznika = BladZalacznika.Format.komunikat
            } finally {
                przygotowanie = false
                plikAparatu?.delete()
            }
        }
    }

    /** Wynik aparatu: plik w cache/alert, pusty albo bez zapisu, gdy czytelnik zrezygnował. */
    fun zAparatu(zrobione: Boolean, plik: File, film: Boolean) {
        if (!zrobione || !plik.exists() || plik.length() == 0L) {
            plik.delete()
            return
        }
        wczytaj(Uri.fromFile(plik), film, plik)
    }

    fun usunZalacznik() = ustawZalacznik(null)

    private fun ustawZalacznik(z: ZalacznikAlertu?) {
        zalacznik?.let { if (it !== z) it.plik.delete() }
        zalacznik = z
    }

    // ---------- położenie ----------

    /** Po przycisku „Dodaj moje położenie” i zgodzie Androida (albo jej braku). Nigdy samo z siebie. */
    fun dodajPolozenie(jestZgoda: Boolean) {
        if (!jestZgoda) {
            bladPolozenia = tl("app.alertLocationDenied", "Brak zgody na położenie. Możesz ją dać w Ustawieniach telefonu albo wpisać miejsce ręcznie.")
            return
        }
        if (szukaPolozenia) return
        zakres.launch {
            szukaPolozenia = true
            bladPolozenia = null
            try {
                val l = Lokalizator.pobierz(aplikacja)
                if (l == null) {
                    bladPolozenia = tl("app.alertLocationFailed", "Nie udało się ustalić położenia. Wpisz miejsce ręcznie.")
                    return@launch
                }
                val p = PolozenieAlertu(l.latitude, l.longitude, max(1, l.accuracy.roundToInt()))
                polozenie = p
                // adres tylko jako podpowiedź do pustego pola; wpisanego tekstu nie zmieniamy
                if (miejsce.isBlank()) Lokalizator.adres(aplikacja, p)?.let { if (miejsce.isBlank()) miejsce = it }
            } finally {
                szukaPolozenia = false
            }
        }
    }

    // ---------- wysyłka ----------

    /** Token antyspamowy pobrany przy otwarciu ekranu (zamiast suwaka ze strony). Przykład niczego nie pobiera. */
    fun przygotujToken() {
        if (przyklad != null || token != null || pobieranieTokenu?.isActive == true) return
        pobieranieTokenu = zakres.launch { if (token == null) token = SiecAlertu.token() }
    }

    fun wyslij() {
        if (stan != Stan.Edycja) return
        blad = null
        if (!sprawdz()) return
        if (przyklad != null) {
            // przykład do zrzutów: bez serwera, od razu potwierdzenie
            stan = Stan.Wyslane(PotwierdzenieAlertu("A-1234", tekstWyslania()))
            return
        }
        stan = Stan.Wysylanie(0f)
        procent = -1
        zakres.launch {
            pobieranieTokenu?.join()
            var ponowiony = false
            while (true) {
                if (token?.wygasl() == true) token = null
                val t = token ?: SiecAlertu.token()
                if (t == null) {
                    koniecZBledem(BladWysylki(tl("app.alertNoConnection", "Brak połączenia z tvml.pl. Spróbuj ponownie za chwilę."), true))
                    return@launch
                }
                token = null // jednorazowy
                // serwer przyjmuje token dopiero po minAge s od wydania
                val czekaj = t.od + t.minimumMs - SystemClock.elapsedRealtime()
                if (czekaj > 0) delay(czekaj)
                val odp = try {
                    SiecAlertu.wyslij(pola(t.wartosc), zalacznik, ::postep)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    koniecZBledem(BladWysylki(tl("app.alertNoConnection", "Brak połączenia z tvml.pl. Spróbuj ponownie za chwilę."), true))
                    return@launch
                }
                val j = odp.json
                if (odp.kod in 200..299 && (j.b("ok") || j.ma("id"))) {
                    val numer = j.s("number").ifEmpty { j.s("id").takeIf { it.isNotEmpty() }?.let { "A-$it" }.orEmpty() }
                    zapamietajDane()
                    ustawZalacznik(null)
                    stan = Stan.Wyslane(PotwierdzenieAlertu(numer, j.s("message").ifEmpty { tekstWyslania() }))
                    return@launch
                }
                val e = j.o("error")
                // token jednorazowy albo za stary: nowy i jedna cicha powtórka
                if (e.s("code") == "token_invalid" && !ponowiony) {
                    ponowiony = true
                    continue
                }
                val pole = e.s("field").ifEmpty { if (e.s("code") == "consent_required" || odp.kod == 422) "consent" else "" }
                if (pole.isNotEmpty()) {
                    bledyPol = setOf(pole)
                    probaZBledem++
                }
                koniecZBledem(bladZOdpowiedzi(odp.kod, e.s("code"), e.s("message"), odp.ponowZaSekund))
                return@launch
            }
        }
    }

    private fun tekstWyslania() =
        tl("app.alertSentDefault", "Dziękujemy! Twój alert został przesłany i po weryfikacji przez redakcję ukaże się na stronie.")

    /** Pola z kontraktu (API 1.9.0); położenie tylko, gdy czytelnik je dołączył. */
    private fun pola(token: String): List<Pair<String, String>> = buildList {
        add("type" to typ)
        add("title" to tytul.trim())
        add("location" to miejsce.trim())
        add("description" to opis.trim())
        add("reporter_name" to imie.trim())
        add("reporter_contact" to kontakt.trim())
        add("consent" to "1")
        add("consent_version" to ustawienia.wersjaZgody)
        add("token" to token)
        add("platform" to "android")
        add("app_version" to BuildConfig.VERSION_NAME.substringBefore('-'))
        polozenie?.let {
            add("lat" to String.format(Locale.US, "%.5f", it.szerokosc))
            add("lon" to String.format(Locale.US, "%.5f", it.dlugosc))
            add("accuracy" to it.dokladnosc.toString())
        }
    }

    /** Postęp z wątku wysyłki; na ekran tylko zmiana o pełny procent. */
    private fun postep(p: Float) {
        val n = (p * 100).roundToInt()
        if (n == procent) return
        procent = n
        zakres.launch { if (stan is Stan.Wysylanie) stan = Stan.Wysylanie(p) }
    }

    private fun koniecZBledem(b: BladWysylki) {
        blad = b
        stan = Stan.Edycja
        // token zużyty: następna próba nie czeka na nowy
        przygotujToken()
    }

    private fun bladZOdpowiedzi(kod: Int, kodBledu: String, tekst: String, ponowZa: Int?): BladWysylki {
        if (kod == 429 || kodBledu == "rate_limited") {
            val minuty = max(1, ((ponowZa ?: 3600) + 59) / 60)
            return BladWysylki(tl("app.alertRateLimited", "Wysłano zbyt wiele zgłoszeń. Spróbuj ponownie za {n} min.", minuty), false)
        }
        if (kod == 503 || kodBledu == "disabled" || kod == 404 || kod >= 500) {
            return BladWysylki(tekst.ifEmpty { tl("app.alertServerError", "Nie udało się wysłać zgłoszenia z aplikacji. Możesz wysłać je przez stronę.") }, true)
        }
        if (kod == 413 || kodBledu == "too_large") {
            val mb = if (zalacznik?.rodzaj == ZalacznikAlertu.Rodzaj.Film) ustawienia.maxFilmMB else ustawienia.maxZdjecieMB
            return BladWysylki(tekst.ifEmpty { BladZalacznika.ZaDuzy(mb).komunikat }, false)
        }
        if (kod == 415 || kodBledu == "bad_type") return BladWysylki(tekst.ifEmpty { BladZalacznika.Format.komunikat }, false)
        return BladWysylki(tekst.ifEmpty { tl("app.alertCheckFields", "Sprawdź zaznaczone pola.") }, false)
    }

    /** Imię i kontakt zostają w telefonie dopiero po udanej wysyłce (i znikają, gdy przełącznik wyłączony). */
    private fun zapamietajDane() {
        prefs.edit {
            putBoolean(KLUCZ_ZAPAMIETAJ, zapamietaj)
            if (zapamietaj) {
                putString(KLUCZ_IMIE, imie.trim())
                putString(KLUCZ_KONTAKT, kontakt.trim())
            } else {
                remove(KLUCZ_IMIE)
                remove(KLUCZ_KONTAKT)
            }
        }
    }

    /** Zamknięcie („Anuluj”, „Odrzuć”, „Gotowe”): wysyłka i przygotowanie pliku przerwane, pliki tymczasowe precz. */
    fun sprzataj() {
        zakres.cancel()
        zalacznik?.plik?.delete()
        zalacznik = null
        PlikiAlertu.sprzatajWszystko(aplikacja)
    }

    /** Do zrzutów (--es alertPrzyklad YES|wyslane): treść jak FormularzAlertu.przyklad na iPhonie; nic nie idzie do serwera. */
    private fun wypelnijPrzykladem(tryb: String) {
        typ = "accident"
        tytul = "Zderzenie dwóch aut na rondzie"
        miejsce = "Leszno, skrzyżowanie ul. Królowej Jadwigi i S5"
        opis = "Dwa samochody osobowe, zablokowany jeden pas w stronę Poznania. Służby są już na miejscu."
        imie = "Świadek"
        kontakt = "swiadek@example.com"
        zgoda = true
        polozenie = PolozenieAlertu(51.84271, 16.57443, 12)
        zalacznik = PlikiAlertu.przykladowy(aplikacja)
        if (tryb == "wyslane") stan = Stan.Wyslane(PotwierdzenieAlertu("A-1234", tekstWyslania()))
    }

    private companion object {
        const val KLUCZ_IMIE = "alert-imie"
        const val KLUCZ_KONTAKT = "alert-kontakt"
        const val KLUCZ_ZAPAMIETAJ = "alert-zapamietaj"
    }
}

/** Aparat z limitem długości filmu (jak videoMaximumDuration na iPhonie); aparat może go pominąć, wtedy sprawdza aplikacja. */
private class NagrajFilm(private val sekundy: Int) : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).putExtra(MediaStore.EXTRA_DURATION_LIMIT, sekundy)
}

// ---------- ekran ----------

/** Ekran „Zgłoś zdarzenie” w zakładce (Ekran.Alert): pasek z „Anuluj”, formularz albo potwierdzenie po wysłaniu. */
@Composable
fun AlertEkran(m: Magazyn) {
    val b = LocalBarwy.current
    val kontekst = LocalContext.current
    val f = remember {
        m.formularzAlertu ?: run {
            // nowy formularz: pliki po poprzednim (np. aplikacja zamknięta w trakcie) precz
            PlikiAlertu.sprzatajWszystko(kontekst)
            FormularzAlertu(kontekst, m.alerty, m.alertPrzyklad).also { m.formularzAlertu = it }
        }
    }
    var pytanie by remember { mutableStateOf(false) }

    fun zamknijOkno() {
        f.sprzataj()
        if (m.formularzAlertu === f) m.formularzAlertu = null
        m.wroc()
    }

    fun anuluj() {
        when {
            f.stan is FormularzAlertu.Stan.Wysylanie -> Unit
            f.maTresc -> pytanie = true
            else -> zamknijOkno()
        }
    }

    // Wstecz jak „Anuluj”: przy wypełnionym formularzu najpierw pytanie
    BackHandler { anuluj() }
    LaunchedEffect(f) { f.przygotujToken() }

    val potwierdzenie = (f.stan as? FormularzAlertu.Stan.Wyslane)?.potwierdzenie
    Column(Modifier.fillMaxSize().background(b.tlo)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 6.dp)) {
            if (potwierdzenie == null) {
                val aktywny = f.stan == FormularzAlertu.Stan.Edycja
                Text(
                    tl("app.cancel", "Anuluj"),
                    Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = aktywny, role = Role.Button) { anuluj() }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    color = if (aktywny) b.link else b.slaby, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                tl("app.alertTitle", "Zgłoś zdarzenie"),
                Modifier.align(Alignment.Center).padding(horizontal = 84.dp).semantics { heading() },
                color = b.tekst, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
        if (potwierdzenie != null) PotwierdzenieWidok(potwierdzenie) { zamknijOkno() } else Formularz(m, f)
    }

    if (pytanie) {
        AlertDialog(
            onDismissRequest = { pytanie = false },
            title = { Text(tl("app.alertDiscardTitle", "Odrzucić zgłoszenie?")) },
            confirmButton = {
                TextButton(onClick = { pytanie = false; zamknijOkno() }) {
                    Text(tl("app.alertDiscard", "Odrzuć"), color = czerwony, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pytanie = false }) { Text(tl("app.alertKeepWriting", "Pisz dalej"), color = b.link) }
            },
            containerColor = b.karta,
            titleContentColor = b.tekst,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Formularz(m: Magazyn, f: FormularzAlertu) {
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    val kontekst = LocalContext.current
    val fokus = LocalFocusManager.current
    val gestosc = LocalDensity.current
    val przewijanie = rememberScrollState()
    val cele = remember { kolejnoscPol.associateWith { BringIntoViewRequester() } }
    val akcent = m.motyw.akcent.w(ciemny)
    fun cel(pole: String) = Modifier.bringIntoViewRequester(cele.getValue(pole))

    // przewinięcie do pierwszego pola do poprawy (po „Wyślij” albo po error.field z serwera)
    LaunchedEffect(f.probaZBledem) {
        if (f.probaZBledem == 0) return@LaunchedEffect
        kolejnoscPol.firstOrNull { it in f.bledyPol }?.let { cele.getValue(it).bringIntoView() }
    }
    // błąd wysyłki bez pola: dół z komunikatem i przyciskiem strony
    LaunchedEffect(f.blad) {
        if (f.blad != null && f.bledyPol.isEmpty()) przewijanie.animateScrollTo(przewijanie.maxValue)
    }
    // zrzut z wypełnionym przykładem: dół (załącznik, dane, zgoda, Wyślij)
    LaunchedEffect(Unit) {
        if (f.przyklad == "YES") {
            delay(600)
            przewijanie.scrollTo(przewijanie.maxValue)
        }
    }

    // aparat (bez uprawnienia CAMERA: zdjęcie robi aplikacja aparatu do pliku z FileProvider), galeria bez uprawnień
    var plikAparatu by rememberSaveable { mutableStateOf<String?>(null) }
    var menuAparatu by remember { mutableStateOf(false) }
    val zdjecieZAparatu = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        plikAparatu?.let { f.zAparatu(ok, File(it), film = false) }
        plikAparatu = null
    }
    val kontraktFilmu = remember(f.ustawienia.maxFilmSekund) { NagrajFilm(f.ustawienia.maxFilmSekund) }
    val filmZAparatu = rememberLauncherForActivityResult(kontraktFilmu) { ok ->
        plikAparatu?.let { f.zAparatu(ok, File(it), film = true) }
        plikAparatu = null
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) f.wczytaj(uri) }
    val prosbaOPolozenie = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // zgoda tylko na przybliżone też wystarczy
        f.dodajPolozenie(Lokalizator.maZgode(kontekst))
    }

    fun zrob(film: Boolean) {
        val (plik, adres) = PlikiAlertu.dlaAparatu(kontekst, if (film) "mp4" else "jpg")
        plikAparatu = plik.absolutePath
        try {
            if (film) filmZAparatu.launch(adres) else zdjecieZAparatu.launch(adres)
        } catch (_: ActivityNotFoundException) {
            plikAparatu = null
            plik.delete()
        }
    }

    // klawiatura: dół formularza nad nią (pod ekranem są jeszcze zakładki, więc tylko część wysokości klawiatury)
    var dolOkna by remember { mutableIntStateOf(0) }
    val klawiatura = WindowInsets.ime.getBottom(gestosc)
    val zapas = with(gestosc) { (klawiatura - dolOkna).coerceAtLeast(0).toDp() }

    Column(
        Modifier.fillMaxSize()
            .onGloballyPositioned { c -> dolOkna = (c.findRootCoordinates().size.height - c.boundsInRoot().bottom).roundToInt() }
            .padding(bottom = zapas)
            .verticalScroll(przewijanie)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. wstęp i 112
        Karta {
            Text(
                tl("app.alertIntro", "Widzisz wypadek, korek, pożar lub gwałtowne zjawisko pogodowe? Poinformuj innych mieszkańców oraz naszą redakcję."),
                color = b.tekst, fontSize = 14.sp, lineHeight = 19.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Ikony.telefon, null, tint = czerwony, modifier = Modifier.size(16.dp))
                Text(tl("app.alertEmergency", "Zagrożenie życia lub zdrowia? Najpierw dzwoń pod 112."), color = czerwony, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // 2. typ zdarzenia: kafle w kolejności z serwera
        Sekcja(tl("app.alertType", "Typ zdarzenia"), blad = "type" in f.bledyPol, modifier = cel("type")) {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                f.ustawienia.typy.forEach { t -> KafelTypu(t, f.typ == t.id, Modifier.weight(1f)) { f.typ = t.id } }
            }
        }

        // 3. co się dzieje
        Sekcja(tl("app.alertWhat", "Co się dzieje")) {
            PoleTekstu(
                f.tytul, { f.tytul = it }, tl("app.alertFieldTitle", "Tytuł zgłoszenia"), tl("app.alertTitleHint", "np. Zderzenie dwóch aut na rondzie"),
                "title" in f.bledyPol, cel("title"), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            PoleTekstu(
                f.miejsce, { f.miejsce = it }, tl("app.alertFieldLocation", "Miejsce (miejscowość, ulica)"), tl("app.alertLocationHint", "np. Leszno, ul. Królowej Jadwigi"),
                "location" in f.bledyPol, cel("location"), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            WierszPolozenia(f) {
                if (Lokalizator.maZgode(kontekst)) {
                    f.dodajPolozenie(true)
                } else {
                    prosbaOPolozenie.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
            PoleTekstu(
                f.opis, { f.opis = it }, tl("app.alertFieldDescription", "Opis sytuacji"), tl("app.alertDescriptionHint", "Co dokładnie się stało, czy są utrudnienia w ruchu…"),
                "description" in f.bledyPol, cel("description"), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minWiersze = 4, maxWiersze = 10,
            )
        }

        // 4. zdjęcie lub film
        val podpisZalacznika = tl("app.alertAttachmentHint", "Zdjęcie do {img} MB albo film do {s} s i {vid} MB. Aplikacja usuwa z pliku dane o miejscu i telefonie.")
            .replace("{img}", f.ustawienia.maxZdjecieMB.toString())
            .replace("{s}", f.ustawienia.maxFilmSekund.toString())
            .replace("{vid}", f.ustawienia.maxFilmMB.toString())
        Sekcja(tl("app.alertAttachment", "Zdjęcie lub film (opcjonalnie)"), blad = "attachment" in f.bledyPol, podpis = podpisZalacznika, modifier = cel("attachment")) {
            val z = f.zalacznik
            when {
                f.przygotowanie -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = akcent, strokeWidth = 2.dp)
                    Text(tl("app.alertPreparing", "Przygotowuję plik…"), color = b.slaby, fontSize = 14.sp)
                }
                z != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Miniatura(z)
                    Text(z.opis, Modifier.weight(1f), color = b.tekst, fontSize = 14.sp)
                    PrzyciskUsun(tl("app.alertRemoveAttachment", "Usuń załącznik")) { f.usunZalacznik() }
                }
                else -> {
                    if (PlikiAlertu.aparatDostepny(kontekst)) {
                        // wybór zdjęcie / film w małym menu pod przyciskiem
                        Box {
                            WierszAkcji(Ikony.aparat, tl("app.alertTakePhoto", "Zrób zdjęcie lub film")) { menuAparatu = true }
                            DropdownMenu(expanded = menuAparatu, onDismissRequest = { menuAparatu = false }) {
                                DropdownMenuItem(
                                    text = { Text(tl("app.alertPhoto", "Zdjęcie")) },
                                    onClick = { menuAparatu = false; zrob(film = false) },
                                    leadingIcon = { Icon(Ikony.aparat, null, Modifier.size(20.dp)) },
                                )
                                DropdownMenuItem(
                                    text = { Text(tl("app.alertVideo", "Film")) },
                                    onClick = { menuAparatu = false; zrob(film = true) },
                                    leadingIcon = { Icon(Ikony.wideo, null, Modifier.size(20.dp)) },
                                )
                            }
                        }
                    }
                    WierszAkcji(Ikony.galeria, tl("app.alertFromLibrary", "Wybierz z galerii")) {
                        galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                }
            }
            f.bladZalacznika?.let { Text(it, color = czerwony, fontSize = 13.sp, lineHeight = 17.sp) }
        }

        // 5. dane dla redakcji
        Sekcja(tl("app.alertYou", "Twoje dane dla redakcji"), podpis = tl("app.alertYouHint", "Imię i kontakt widzi tylko redakcja. Nie publikujemy ich.")) {
            PoleTekstu(
                f.imie, { f.imie = it }, tl("app.alertFieldName", "Imię lub nick"), tl("app.alertNameHint", "np. Świadek"),
                "reporter_name" in f.bledyPol, cel("reporter_name"), KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            PoleTekstu(
                f.kontakt, { f.kontakt = it }, tl("app.alertFieldContact", "Telefon lub e-mail"), tl("app.alertContactHint", "Do ewentualnej weryfikacji przez redakcję"),
                "reporter_contact" in f.bledyPol, cel("reporter_contact"), KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            )
            Przelacznik(tl("app.alertRemember", "Zapamiętaj na tym telefonie"), f.zapamietaj, akcent, Color.Unspecified) { f.zapamietaj = it }
        }

        // 6. zgoda i polityka prywatności
        Karta(cel("consent")) {
            Przelacznik(f.ustawienia.tekstZgody + " *", f.zgoda, akcent, if ("consent" in f.bledyPol) czerwony else Color.Unspecified, wielkosc = 13) { f.zgoda = it }
            Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
            WierszAkcji(Ikony.tarcza, tl("app.privacyPolicy", "Polityka prywatności")) { otworzStrone(kontekst, f.ustawienia.polityka) }
        }

        // 7. błąd i „Wyślij zgłoszenie” (w trakcie pasek postępu)
        f.blad?.let { BladWysylkiWidok(it) { otworzStrone(kontekst, Siec.ALERT) } }
        when (val s = f.stan) {
            is FormularzAlertu.Stan.Wysylanie -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { s.postep },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = czerwony, trackColor = b.ramka,
                )
                Text(tl("app.alertSending", "Wysyłanie… {n}%", (s.postep * 100).roundToInt()), color = b.slaby, fontSize = 14.sp)
            }
            else -> Row(
                Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).background(czerwony)
                    .clickable(role = Role.Button) {
                        fokus.clearFocus()
                        f.wyslij()
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ikony.wyslij, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tl("app.alertSend", "Wyślij zgłoszenie"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Sekcja jak w formularzu na iPhonie: nagłówek, karta i podpis pod nią. */
@Composable
private fun Sekcja(tytul: String, blad: Boolean = false, podpis: String? = null, modifier: Modifier = Modifier, tresc: @Composable ColumnScope.() -> Unit) {
    val b = LocalBarwy.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            tytul, Modifier.padding(start = 2.dp).semantics { heading() },
            color = if (blad) czerwony else b.tekst, fontSize = 17.sp, fontWeight = FontWeight.Black, fontFamily = Szeryf,
        )
        Karta(tresc = tresc)
        if (podpis != null) Text(podpis, Modifier.padding(horizontal = 4.dp), color = b.slaby, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun Karta(modifier: Modifier = Modifier, tresc: @Composable ColumnScope.() -> Unit) {
    val b = LocalBarwy.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = tresc,
    )
}

/** Kafel rodzaju: wybrany ma ramkę 2 dp w kolorze rodzaju i tło 14% tego koloru. */
@Composable
private fun KafelTypu(t: TypAlertu, wybrany: Boolean, modifier: Modifier, wybierz: () -> Unit) {
    val b = LocalBarwy.current
    val k = kolor(t.kolor, kolor(0x6c757d))
    Column(
        modifier.heightIn(min = 66.dp).clip(RoundedCornerShape(12.dp))
            .background(if (wybrany) k.copy(alpha = 0.14f) else b.tlo)
            .border(if (wybrany) 2.dp else 1.dp, if (wybrany) k else b.ramka, RoundedCornerShape(12.dp))
            .selectable(selected = wybrany, role = Role.RadioButton, onClick = wybierz)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        // ikony mają różną wysokość, podpisy w jednej linii
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
            Icon(Ikony.alertu(t.id), null, tint = k, modifier = Modifier.size(24.dp))
        }
        Text(
            t.nazwa, color = b.tekst, fontSize = 12.sp, fontWeight = if (wybrany) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Etykieta pola z gwiazdką, na czerwono przy błędzie, i pole tekstu z podpowiedzią. */
@Composable
private fun PoleTekstu(
    wartosc: String,
    zmien: (String) -> Unit,
    nazwa: String,
    podpowiedz: String,
    blad: Boolean,
    modifier: Modifier,
    klawiatura: KeyboardOptions,
    minWiersze: Int = 1,
    maxWiersze: Int = 1,
) {
    val b = LocalBarwy.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$nazwa *", color = if (blad) czerwony else b.slaby, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            wartosc, zmien, Modifier.fillMaxWidth(),
            placeholder = { Text(podpowiedz, fontSize = 16.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
            singleLine = maxWiersze == 1,
            minLines = minWiersze,
            maxLines = maxWiersze,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = klawiatura,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = b.tekst, unfocusedTextColor = b.tekst,
                focusedBorderColor = if (blad) czerwony else b.link, unfocusedBorderColor = if (blad) czerwony else b.ramka,
                focusedPlaceholderColor = b.slaby, unfocusedPlaceholderColor = b.slaby, cursorColor = b.link,
            ),
        )
    }
}

/** „Dodaj moje położenie” (zgoda Androida dopiero po stuknięciu) albo dołączone położenie z przyciskiem usuń. */
@Composable
private fun WierszPolozenia(f: FormularzAlertu, dodaj: () -> Unit) {
    val b = LocalBarwy.current
    val p = f.polozenie
    if (p != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ikony.polozenie, null, tint = b.link, modifier = Modifier.size(18.dp))
            Text(tl("app.alertLocationAdded", "Dołączone położenie z GPS (±{n} m)", p.dokladnosc), Modifier.weight(1f), color = b.tekst, fontSize = 14.sp)
            PrzyciskUsun(tl("app.alertLocationRemove", "Usuń położenie")) { f.polozenie = null }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WierszAkcji(Ikony.polozenie, tl("app.alertAddLocation", "Dodaj moje położenie"), aktywny = !f.szukaPolozenia, wTrakcie = f.szukaPolozenia, akcja = dodaj)
            f.bladPolozenia?.let { Text(it, color = b.slaby, fontSize = 12.sp, lineHeight = 16.sp) }
        }
    }
}

/** Wiersz-przycisk z ikoną w kolorze linku (aparat, galeria, położenie, polityka prywatności, strona). */
@Composable
private fun WierszAkcji(ikona: androidx.compose.ui.graphics.vector.ImageVector, tekst: String, aktywny: Boolean = true, wTrakcie: Boolean = false, akcja: () -> Unit) {
    val b = LocalBarwy.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = aktywny, role = Role.Button, onClick = akcja),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(ikona, null, tint = b.link, modifier = Modifier.size(20.dp))
        Text(tekst, Modifier.weight(1f), color = b.link, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (wTrakcie) CircularProgressIndicator(Modifier.size(18.dp), color = b.link, strokeWidth = 2.dp)
    }
}

@Composable
private fun Przelacznik(nazwa: String, tak: Boolean, akcent: Color, kolorTekstu: Color, wielkosc: Int = 15, zmien: (Boolean) -> Unit) {
    val b = LocalBarwy.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).toggleable(value = tak, role = Role.Switch, onValueChange = zmien),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            nazwa, Modifier.weight(1f), color = if (kolorTekstu == Color.Unspecified) b.tekst else kolorTekstu,
            fontSize = wielkosc.sp, lineHeight = (wielkosc + 4).sp,
        )
        Switch(checked = tak, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = akcent, checkedBorderColor = akcent))
    }
}

/** Krzyżyk w kółku (jak xmark.circle.fill): usuń załącznik albo położenie. */
@Composable
private fun PrzyciskUsun(opis: String, akcja: () -> Unit) {
    val b = LocalBarwy.current
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = akcja), contentAlignment = Alignment.Center) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(b.slaby), contentAlignment = Alignment.Center) {
            Icon(Ikony.zamknij, opis, tint = b.karta, modifier = Modifier.size(14.dp))
        }
    }
}

/** Miniatura 64 dp; film z trójkątem odtwarzania w rogu. */
@Composable
private fun Miniatura(z: ZalacznikAlertu) {
    val b = LocalBarwy.current
    val film = z.rodzaj == ZalacznikAlertu.Rodzaj.Film
    Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(b.ramka), contentAlignment = Alignment.Center) {
        val mini = z.miniatura
        if (mini != null) {
            Image(mini, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(if (film) Ikony.wideo else Ikony.galeria, null, tint = b.slaby, modifier = Modifier.size(24.dp))
        }
        if (film) Icon(Ikony.odtworz, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(18.dp))
    }
}

/** Błąd wysyłki; przy braku sieci i błędzie serwera także „Otwórz formularz na tvml.pl” (formularz zostaje wypełniony). */
@Composable
private fun BladWysylkiWidok(blad: BladWysylki, otworzStroneAlertu: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(czerwony.copy(alpha = 0.08f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ikony.uwaga, null, tint = czerwony, modifier = Modifier.size(18.dp))
            Text(blad.tekst, color = czerwony, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (blad.strona) WierszAkcji(Ikony.przegladarka, tl("app.alertOpenWeb", "Otwórz formularz na tvml.pl"), akcja = otworzStroneAlertu)
    }
}

/** Po wysłaniu zamiast formularza: zielony znak, podziękowanie z serwera, numer zgłoszenia i „Gotowe”. */
@Composable
private fun PotwierdzenieWidok(p: PotwierdzenieAlertu, gotowe: () -> Unit) {
    val b = LocalBarwy.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.padding(top = 40.dp).size(64.dp).clip(CircleShape).background(zielony), contentAlignment = Alignment.Center) {
                Icon(Ikony.ptaszek, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Text(
                tl("app.alertSentTitle", "Zgłoszenie wysłane"), Modifier.semantics { heading() },
                color = b.tekst, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = Szeryf, textAlign = TextAlign.Center,
            )
            Text(p.komunikat, color = b.tekst, fontSize = 16.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            if (p.numer.isNotEmpty()) {
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(tl("app.alertNumber", "Numer zgłoszenia"), color = b.slaby, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    SelectionContainer { Text(p.numer, color = b.tekst, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Text(
                tl("app.alertReviewNote", "Redakcja sprawdzi zgłoszenie przed publikacją. Jeśli będzie trzeba, skontaktuje się z Tobą."),
                color = b.slaby, fontSize = 14.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
            )
            Box(
                Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).background(Barwy.granat)
                    .clickable(role = Role.Button, onClick = gotowe),
                contentAlignment = Alignment.Center,
            ) {
                Text(tl("app.done", "Gotowe"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
