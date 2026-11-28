package pl.tvml

import android.content.Context
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Ekran otwarty w zakładce nad jej ekranem głównym (jak NavigationStack na iPhonie). */
sealed interface Ekran {
    data class Tekst(val artykul: Artykul) : Ekran
    data class ZGminy(val gmina: Gmina) : Ekran
    data object Gminy : Ekran
    data object Zapisane : Ekran
    /** Lista artykułów z API, np. fotorelacje albo najczęściej czytane. */
    data class Lista(val tytul: String, val trasa: String, val parametry: Map<String, String>) : Ekran
    /** Podstrona redakcji z „Więcej” (O nas, Kontakt, Regulamin…) w aplikacji. */
    data class Strona(val slug: String) : Ekran
    data object Ogloszenia : Ekran
    data object Powiadomienia : Ekran
    data class JednoWydarzenie(val wydarzenie: Wydarzenie) : Ekran
    /** „Zgłoś zdarzenie (Alert TVML)” w aplikacji (AlertEkran.kt), gdy serwer go zgłasza (config.alerts.native). */
    data object Alert : Ekran
}

/** Wszystko, co aplikacja pobiera z tvml.pl. Jedno źródło dla wszystkich ekranów (Magazyn na iPhonie). */
class Magazyn(kontekst: Context, private val zakres: CoroutineScope) {
    private val ustawienia = kontekst.getSharedPreferences("tvml", Context.MODE_PRIVATE)
    private val aplikacja: Context = kontekst.applicationContext

    /** Rośnie po każdym config z serwera; MainActivity wtedy pyta o zgodę na powiadomienia albo odświeża rejestrację. */
    var konfiguracjaPush by mutableIntStateOf(0)

    var najnowsze by mutableStateOf<List<Artykul>>(emptyList())
    var najczytniejsze by mutableStateOf<List<Artykul>>(emptyList())
    var pasek by mutableStateOf<List<Artykul>>(emptyList())
    var pasekWlaczony by mutableStateOf(true)
    /** Etykieta paska pod banerem (config.ticker.label) i czy to transmisja (config.ticker.live). */
    var etykietaPaska by mutableStateOf(tl("app.latest", "Najnowsze").uppercase())
    var pasekNaZywo by mutableStateOf(false)
    var filmy by mutableStateOf<List<Film>>(emptyList())
    /** Następna strona filmów (/api/v1/videos, nextCursor); null = to już wszystkie. */
    var filmyKursor by mutableStateOf<String?>(null)
        private set
    /** Trwa wczytywanie starszych filmów (przycisk „Starsze filmy” pokazuje wtedy kółko). */
    var ladujeFilmy by mutableStateOf(false)
        private set
    var pogoda by mutableStateOf<Pogoda?>(null)
    var motyw by mutableStateOf(Motyw.zwykly)
    /** Motyw z argumentu (zrzuty ekranu); wtedy panel go nie zmienia. */
    var motywTestowy: Motyw? = null

    // ---------- scena pogody ze strony (ScenyStrony.kt) ----------
    /** config.theme.weatherScene: scena wyliczona przez serwer z harmonogramu motywów i jego etapu (pusta = bez motywu). */
    var scenaMotywu by mutableStateOf<String?>(null)
    /** config.theme.backgroundScene (API 1.8.3): scena na teraz także przy żałobie i pustej scenie w panelu. */
    var scenaTla by mutableStateOf<String?>(null)
    /** config.theme.dateBar (API 1.8.3): pasek daty bieżącego motywu (medal, akcent, przedrostek), wyliczony na teraz. */
    var pasekMotywu by mutableStateOf<PasekDaty?>(null)
    /** Koniec bieżącego motywu (theme.until); potem scena wraca do ogólnej z weatherAnimation. */
    var koniecMotywu by mutableStateOf<java.time.ZonedDateTime?>(null)
    /**
     * Następne etapy bieżącego motywu i etapy nadchodzących (API 1.8.3 stages[]; starszy serwer: całe upcomingThemes),
     * żeby scena, SVG i pasek daty przełączyły się na granicy bez czekania na odświeżenie.
     */
    var nadchodzaceSceny by mutableStateOf<List<OknoSceny>>(emptyList())
    /** config.weatherAnimation[state]: opcje sceny dla każdego stanu pogody (skyColor, woda i piasek plaży…). */
    var animacjePogody by mutableStateOf<Map<String, OpcjeSceny>>(emptyMap())
    /** Rośnie na każdej granicy okna motywu (MainActivity), żeby karta pogody przerysowała scenę. */
    var zegarScen by mutableIntStateOf(0)
    /** Scena z argumentu (--es scena leszno-zima): do zrzutów i sprawdzania scen. */
    var scenaTestowa: String? = null
    /** Chwila z argumentu (--es chwila 2026-12-24T12:00:00+01:00): do sprawdzania etapów motywów; null = teraz. */
    var chwilaTestowa: java.time.ZonedDateTime? = null

    fun chwila(): java.time.ZonedDateTime = chwilaTestowa ?: java.time.ZonedDateTime.now(Daty.strefa)

    /** Motyw z config.theme (na dziś), do powrotu z „Podglądu dnia”. */
    private var motywZKonfiguracji: Motyw? = null
    /** config.theme i config.upcomingThemes (z etapami) do „Podglądu dnia”: szata graficzna aplikacji na wybraną chwilę. */
    private var oknaMotywow: List<JSONObject> = emptyList()
    private var wersjaApi = "1.0.0"
    /** Trwa „Podgląd dnia”: po odświeżeniu konfiguracji szata graficzna dalej na wybraną chwilę. */
    private var podgladDnia = false
    /** Trwa „Podgląd dnia” (tylko wersja debug): scena wbudowana zamiast sceny ze strony (strona pokazuje dziś). */
    val podgladDniaTrwa: Boolean get() = podgladDnia

    /**
     * „Podgląd dnia” (wersja testowa): karta pogody i cała szata graficzna aplikacji (pasek u góry, kolory, logo,
     * ozdoby) na wybraną chwilę z harmonogramu motywów; null wraca do teraz (Karol 24.11: „szata graficzna aplikacji
     * nie zmieniła się”). Motyw podglądu blokuje zmiany z panelu i ikonę aplikacji jak motyw testowy z argumentu.
     */
    fun ustawPodgladDnia(chwila: java.time.ZonedDateTime?, noc: Boolean?) {
        podgladDnia = chwila != null
        chwilaTestowa = chwila
        nocTestowa = noc
        if (chwila == null) {
            motywTestowy = null
            motyw = motywZKonfiguracji ?: Motyw.zwykly
        } else {
            val p = motywNa(chwila)
            motywTestowy = p
            motyw = p
        }
        zegarScen++
    }

    /** Motyw okna (i jego etapu: id, wariant, ozdoba), które trwa w [chwila]; poza oknami zwykły albo dzisiejszy. */
    private fun motywNa(chwila: java.time.ZonedDateTime): Motyw {
        fun trwa(j: JSONObject): Boolean {
            val od = Daty.iso(j.s("from")) ?: return false
            val dO = Daty.iso(j.s("until")) ?: return false
            return !chwila.isBefore(od) && !chwila.isAfter(dO)
        }
        val okno = oknaMotywow.firstOrNull(::trwa)
            ?: return if (chwila.toLocalDate() == java.time.LocalDate.now(Daty.strefa)) motywZKonfiguracji ?: Motyw.zwykly else Motyw.zwykly
        val j = JSONObject(okno.toString())
        // z etapu tylko wariant i ozdoba: id etapu to „main”, „wigilia”…, a motyw rozpoznaje się po id okna („xmas”)
        okno.lista("stages").firstOrNull(::trwa)?.let { etap ->
            listOf("variant", "ornament").forEach { k -> if (etap.has(k) && !etap.isNull(k)) j.put(k, etap.get(k)) }
        }
        return Motyw.zV1(j, wersjaApi, chwila.toLocalDate()) ?: Motyw.zwykly
    }

    /** Dzień z argumentu (--ez dzienTestowy true): 11 listopada ze świętem i trzema imionami, jak na arkuszach iPhone'a. */
    var dzienTestowy = false

    /** Pora sceny z argumentu (--es pora dzien|noc): do zrzutów scen w dzień i w nocy. null = z /api/v1/weather. */
    var nocTestowa by mutableStateOf<Boolean?>(null)

    /** Noc dla sceny pogody: isNight z /api/v1/weather albo pora z argumentu. */
    fun nocSceny(): Boolean = nocTestowa ?: (pogoda?.dzien == false)
    /** Scena SVG motywu ze Studia motywów (theme.weatherSceneType = svg), gdy taki motyw trwa. */
    var scenaSvg by mutableStateOf<ScenaSvg?>(null)

    /** Scena SVG na teraz: tylko w oknie jej motywu (nie w oknie następnego, nie po końcu, nie przy scenie testowej). */
    fun scenaSvgTeraz(teraz: java.time.ZonedDateTime = chwila()): ScenaSvg? {
        @Suppress("UNUSED_VARIABLE") val odswiez = zegarScen
        if (scenaTestowa != null) return null
        nadchodzaceSceny.firstOrNull { it.trwa(teraz) }?.let { return it.svg }
        val koniec = koniecMotywu
        return if (koniec != null && teraz.isAfter(koniec)) null else scenaSvg
    }

    /** Pasek daty na teraz: etap, w którym jesteśmy, albo bieżący motyw (null = zwykła belka z kalendarzem). */
    fun pasekDatyTeraz(teraz: java.time.ZonedDateTime = chwila()): PasekDaty? {
        @Suppress("UNUSED_VARIABLE") val odswiez = zegarScen
        if (scenaTestowa != null) return null
        nadchodzaceSceny.firstOrNull { it.trwa(teraz) }?.let { return it.pasek }
        val koniec = koniecMotywu
        return if (koniec != null && teraz.isAfter(koniec)) null else pasekMotywu
    }

    fun opcjeSceny(stan: String): OpcjeSceny = animacjePogody[stan] ?: OpcjeSceny()

    /**
     * Scena na teraz: okno nadchodzącego motywu, w którym jesteśmy (serwer jeszcze go nie podał jako bieżący),
     * potem scena bieżącego motywu (do jego końca), a na końcu ogólna scena z panelu, jak na stronie.
     */
    fun scenaPogody(stan: String, teraz: java.time.ZonedDateTime = chwila()): String {
        @Suppress("UNUSED_VARIABLE") val odswiez = zegarScen
        scenaTestowa?.let { if (it in ScenyStrony.znane) return it }
        val opcje = opcjeSceny(stan)
        nadchodzaceSceny.firstOrNull { it.trwa(teraz) }?.let { return ScenyStrony.wybierz(it.scena, opcje) }
        val koniec = koniecMotywu
        if (koniec != null && teraz.isAfter(koniec)) return ScenyStrony.wybierz(null, opcje)
        return ScenyStrony.wybierz(scenaMotywu ?: scenaTla, opcje)
    }

    /**
     * Najbliższa granica po [teraz]: początek albo koniec etapu motywu albo północ (nowy dzień: imieniny i przedrostek
     * paska daty z serwera), do przełączenia sceny i odświeżenia config w otwartej aplikacji.
     */
    fun nastepnaZmianaSceny(teraz: java.time.ZonedDateTime = chwila()): java.time.ZonedDateTime? =
        (nadchodzaceSceny.flatMap { listOfNotNull(it.od, it.do_) } + listOfNotNull(koniecMotywu, teraz.toLocalDate().plusDays(1).atStartOfDay(Daty.strefa)))
            .filter { it.isAfter(teraz) }.minOrNull()
    val wKategorii = mutableStateMapOf<String, List<Artykul>>()
    var blad by mutableStateOf<String?>(null)
    var laduje by mutableStateOf(false)
    /** Zakładki z panelu (Aplikacja mobilna → Zakładki), a bez API domyślne z projektu. */
    var zakladki by mutableStateOf(ZakladkaApp.domyslne)
    var zakladka by mutableStateOf("home")
    /** Otwarte ekrany w każdej zakładce. */
    val sciezki = mutableStateMapOf<String, List<Ekran>>()
    /** Bloki ekranu Start w kolejności z panelu (config.home). */
    var bloki by mutableStateOf(BlokStartu.domyslne)
    var gminy by mutableStateOf<List<Gmina>>(emptyList())
    var kategorie by mutableStateOf(KategoriaMenu.domyslne)
    /**
     * Kategoria artykułów każdego subportalu z panelu (config.subportals[].category). Nazwa subportalu nie zawsze
     * jest kategorią: muzyka to „muzyka-tvml” (?category=muzyka jest puste), a „na-sygnale” nie ma w panelu
     * ani kategorii, ani tagu, więc ?subportal=na-sygnale jest puste. Pusta kategoria w panelu = nazwa subportalu.
     */
    var kategorieSubportali by mutableStateOf(mapOf("muzyka" to "muzyka-tvml"))
    var dzisiaj by mutableStateOf<Dzisiaj?>(null)
    /** Podstrony redakcji do „Więcej” (config.pages), w kolejności z serwera. */
    var strony by mutableStateOf(StronaRedakcji.domyslne)
    /** Podstrony z prawdziwą treścią (bez pustych i „Wkrótce.”); null = jeszcze nie sprawdzone. */
    var stronyGotowe by mutableStateOf<Set<String>?>(null)
    private var sprawdzoneStrony: List<String>? = null
    /** Treść podstron pobrana przy sprawdzaniu, żeby otwierały się od razu. */
    private val trescStron = mutableMapOf<String, TrescPodstrony>()
    /** Podpis w stopce „Więcej” (config.about.footer). */
    var stopka by mutableStateOf("Telewizja TVML · Leszno")
    /** Komunikat redakcji z panelu (config.message, od API 1.2), jak na iPhonie. */
    var komunikat by mutableStateOf<Komunikat?>(null)
    /** Nowsza wersja w Google Play: łagodna prośba o aktualizację pod paskiem (do zamknięcia). */
    var nowaWersja by mutableStateOf<Komunikat?>(null)
    /** Panel podniósł minimalną wersję (config.minAppVersion.android): ekran z przyciskiem do Google Play. */
    var wymaganaAktualizacja by mutableStateOf(false)
    private var sprawdzonoSklep = 0L
    /** Zamknięte krzyżykiem komunikaty (ostatnie 50), żeby nie wracały po odświeżeniu. */
    var zamknieteKomunikaty by mutableStateOf(
        ustawienia.getString("zamkniete-komunikaty", "").orEmpty().split('\n').filter { it.isNotEmpty() }.toSet(),
    )
        private set

    fun zamknijKomunikat(id: String) {
        val lista = (zamknieteKomunikaty.toList() - id + id).takeLast(50)
        zamknieteKomunikaty = lista.toSet()
        ustawienia.edit().putString("zamkniete-komunikaty", lista.joinToString("\n")).apply()
    }
    /**
     * Formularz „Zgłoś zdarzenie” w aplikacji (config.alerts, API 1.9.0), zapisany na czas bez sieci jak i18n.
     * Brak klucza, native: false albo stara wersja API = dalej tvml.pl/#alert w karcie przeglądarki.
     */
    var alerty by mutableStateOf(UstawieniaAlertu.z(runCatching { JSONObject(ustawienia.getString("alerts", "{}") ?: "{}") }.getOrNull()))
        private set
    /** Do zrzutów (tylko wersja testowa): --ez alertNatywny true, ekran w aplikacji bez config.alerts.native. */
    var alertNatywnyTestowy = false
    /** Do zrzutów (tylko wersja testowa): --es alertPrzyklad YES|wyslane, przykład bez wysyłania. */
    var alertPrzyklad: String? = null
    /** Otwarty formularz Alertu TVML: zostaje przy przejściu do innej zakładki, znika po „Gotowe” albo „Odrzuć”. */
    var formularzAlertu: FormularzAlertu? = null

    fun alertNatywny(): Boolean = alerty.natywny || alertNatywnyTestowy

    /** „Zgłoś zdarzenie (Alert TVML)”: decyzja w chwili stuknięcia, formularz w aplikacji albo strona jak dotąd. */
    fun zglosZdarzenie(kontekst: Context) {
        if (!alertNatywny()) {
            otworzStrone(kontekst, Siec.ALERT)
            return
        }
        // po wysłanym zgłoszeniu nowe zaczyna się od pustego formularza
        formularzAlertu?.takeIf { it.wyslane }?.let { it.sprzataj(); formularzAlertu = null }
        otworz(Ekran.Alert)
    }

    /** Czy w „Więcej” pokazać ogłoszenia i fotorelacje (tylko gdy coś w nich jest). */
    var maOgloszenia by mutableStateOf(false)
    var maFotorelacje by mutableStateOf(true)
    /** Blok events: kalendarz wydarzeń (/api/v1/events, API 1.7). */
    var wydarzenia by mutableStateOf<List<Wydarzenie>>(emptyList())
    /** Blok poll: trwająca sonda albo kalendarz wydarzeń (klucz: id bloku). */
    val spotlight = mutableStateMapOf<String, Spotlight>()
    /** Głosy oddane z tego telefonu (id sondy → odpowiedź), tylko lokalnie, bez żadnego identyfikatora. */
    val glosy = mutableStateMapOf<String, String>().apply {
        runCatching { JSONObject(ustawienia.getString("glosy", "{}") ?: "{}") }.getOrNull()?.let { j ->
            j.keys().forEach { k -> j.optString(k).takeIf { it.isNotEmpty() }?.let { put(k, it) } }
        }
    }
    var arkuszPogody by mutableStateOf(false)
    var arkuszWideo by mutableStateOf(false)
    var arkuszSzukaj by mutableStateOf(false)
    val zapisane = mutableStateListOf<Int>().apply {
        addAll(ustawienia.getString("zapisane", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() })
    }
    /**
     * Skróty zapisanych artykułów (tytuł, zdjęcie, kategoria, data), żeby „Zapisane” nie pobierały każdego artykułu
     * z osobna: GET articles/{id} po ukraińsku albo angielsku zgłasza prośbę o tłumaczenie (tłumaczenie na żądanie),
     * a lista to jeszcze nie czytanie. Pełny tekst pobiera się dopiero po otwarciu artykułu.
     */
    private val skrotyZapisanych: MutableMap<Int, JSONObject> = mutableMapOf<Int, JSONObject>().apply {
        runCatching { JSONObject(ustawienia.getString("zapisane-skroty", "{}") ?: "{}") }.getOrNull()?.let { j ->
            j.keys().forEach { k -> k.toIntOrNull()?.let { id -> j.optJSONObject(k)?.let { put(id, it) } } }
        }
    }
    /** Ile razy aplikacja wróciła na ekran (artykuł z tłumaczeniem w przygotowaniu odświeża się wtedy sam). */
    var wznowienia by mutableIntStateOf(0)
    /** Czy aplikacja jest na ekranie (onStart–onStop): tylko wtedy artykuł czeka na tłumaczenie od razu. */
    var naEkranie by mutableStateOf(true)
    /** Rozmiar tekstu artykułów: 0 mały, 1 średni, 2 duży. */
    var rozmiar by mutableIntStateOf(ustawienia.getInt("rozmiar", 1))
        private set
    /** Wygląd: auto (jak w telefonie), dzien, noc. */
    var wyglad by mutableStateOf(ustawienia.getString("wyglad", "auto") ?: "auto")
        private set
    private var ostatnio = 0L

    /** Bez internetu: z kiedy są pokazywane dane z pamięci telefonu (ms); null = świeże z sieci. Napis na Starcie. */
    var daneZPamieci by mutableStateOf<Long?>(null)
        private set

    fun ustawRozmiar(r: Int) {
        rozmiar = r.coerceIn(0, 2)
        ustawienia.edit { putInt("rozmiar", rozmiar) }
    }

    fun ustawWyglad(w: String) {
        wyglad = w
        ustawienia.edit { putString("wyglad", w) }
    }

    /**
     * Język z „Więcej” (system, pl, uk, en). Przy zmianie języka treści: zamyka otwarte ekrany, czyści dane w pamięci
     * (odpowiedzi różnią się językiem; zapis na dysku jest osobny dla każdego lang=) i pobiera wszystko od nowa.
     */
    fun ustawJezyk(w: String) {
        if (Jezyk.ustaw(w)) przeladujJezyk()
    }

    /** Po powrocie do aplikacji: język telefonu mógł się zmienić, a wybór to „Jak w systemie”. */
    fun sprawdzJezyk() {
        if (Jezyk.przelicz()) przeladujJezyk()
    }

    private fun przeladujJezyk() {
        sciezki.clear()
        wKategorii.clear()
        trescStron.clear()
        sprawdzoneStrony = null
        spotlight.clear()
        // nazwy zakładek, bloków i kategorii przyjdą z config w nowym języku
        odswiez(wymus = true)
    }

    fun odswiez(wymus: Boolean = false) {
        zakres.launch { odswiezTeraz(wymus) }
    }

    /** Podgląd szkicu aplikacji z panelu (kod QR → tvml.pl/app/podglad?t=… albo tvml://preview?t=…), jak na iPhonie. */
    data class Podglad(val token: String, val etykieta: String = "", val doKiedy: java.time.ZonedDateTime? = null, val rewizja: String = "", val testowy: Boolean = false)

    /** Trwający podgląd: tylko w pamięci, bez zapisu; cała aplikacja (Start, zakładki, wygląd) działa na config ze szkicu. */
    var podglad by mutableStateOf<Podglad?>(null)
        private set
    /** Okno „Podgląd zakończony” (wygasł albo zakończony w panelu). */
    var podgladZakonczony by mutableStateOf(false)
    private var koniecPodgladu: kotlinx.coroutines.Job? = null

    /** Link z kodu QR: token base64url 16–128 znaków, inne ignorujemy bez pytania serwera. */
    fun wlaczPodglad(token: String) {
        if (!Regex("[A-Za-z0-9_-]{16,128}").matches(token)) return
        podglad = Podglad(token)
        podgladZakonczony = false
        odswiez(wymus = true)
    }

    /** Do zrzutów: pasek podglądu z etykietą jak z panelu, bez pytania serwera (--es ekran podglad). */
    fun podgladTestowy() {
        podglad = Podglad("test", "Podgląd: Karol, 14:05", java.time.ZonedDateTime.now(Daty.strefa).plusMinutes(30), "1", testowy = true)
    }

    /** „Zakończ” albo wygaśnięcie: powrót do opublikowanego config; przy wygaśnięciu okno z wyjaśnieniem. */
    fun zakonczPodglad(wygasl: Boolean) {
        if (podglad == null) return
        koniecPodgladu?.cancel()
        podglad = null
        if (wygasl) podgladZakonczony = true
        odswiez(wymus = true)
    }

    private fun zaplanujKoniecPodgladu(p: Podglad) {
        koniecPodgladu?.cancel()
        val koniec = p.doKiedy ?: return
        koniecPodgladu = zakres.launch {
            kotlinx.coroutines.delay(java.time.Duration.between(java.time.ZonedDateTime.now(), koniec).toMillis().coerceAtLeast(0) + 1000)
            if (podglad?.token == p.token) zakonczPodglad(wygasl = true)
        }
    }

    /**
     * config: przy podglądzie GET config?preview=…&features=ai-themes bez pamięci podręcznej; 400/410 kończy podgląd
     * i wraca do zwykłego config, inny błąd albo brak sieci zostawia to, co widać (null).
     */
    private suspend fun pobierzKonfiguracje(): JSONObject? {
        val p = podglad
        if (p != null && !p.testowy) {
            val (kod, j) = Siec.v1BezSchowka("config", mapOf("preview" to p.token, "features" to "ai-themes"))
            when {
                kod in 200..299 && j != null -> {
                    val pv = j.o("preview")
                    val nowy = p.copy(etykieta = pv.s("label"), doKiedy = Daty.iso(pv.s("expiresAt")), rewizja = pv.s("revision"))
                    podglad = nowy
                    zaplanujKoniecPodgladu(nowy)
                    return j
                }
                kod == 400 || kod == 410 -> {
                    koniecPodgladu?.cancel()
                    podglad = null
                    podgladZakonczony = true
                }
                else -> return null
            }
        }
        return Siec.v1Obiekt("config")
    }

    suspend fun odswiezTeraz(wymus: Boolean = false) {
        // przy podglądzie bez przerwy 2 min: każde odświeżenie pobiera szkic od nowa
        if (!wymus && podglad == null && System.currentTimeMillis() - ostatnio < 120_000) return
        laduje = true
        // znacznik tylko tego odświeżenia (Karol 28.11: fałszywe „Bez internetu”); przerwane odświeżenie nie zmienia napisu
        val z = Siec.ZPamieci()
        try {
            if (odswiezV1(z)) {
                blad = null
                // dane z pamięci telefonu: napis „Bez internetu” na Starcie i bez przerwy 2 minut przed kolejną próbą
                daneZPamieci = z.od
                if (daneZPamieci == null) ostatnio = System.currentTimeMillis()
            } else {
                odswiezStare(z)
                daneZPamieci = z.od?.takeIf { najnowsze.isNotEmpty() }
                if (daneZPamieci != null) ostatnio = 0L
            }
        } finally {
            laduje = false
        }
        // nowsza wersja w Google Play: najwyżej co 6 godzin (Play Core, bez danych o czytelniku)
        if (komunikatTestowy == null && System.currentTimeMillis() - sprawdzonoSklep > 6 * 3_600_000L) {
            sprawdzonoSklep = System.currentTimeMillis()
            nowaWersja = AktualizacjaAplikacji.nowszaWSklepie(aplikacja)?.let { AktualizacjaAplikacji.komunikat(it) }
        }
    }

    /** Do zrzutów: --es komunikat aktualizacja|redakcja|pilny|wymagana. */
    /** Do zrzutów w wersji debug (--ez banerTestowy true): przykładowy baner Andrzejek na Starcie, zanim poda go serwer. */
    var banerTestowy = false

    private fun przykladBanera() = BlokStartu(
        "banner-test", "banner", "Andrzejki – noc wróżb", false, 0, null,
        baner = BlokStartu.Baner(
            najnowsze.firstOrNull()?.obraz?.ifEmpty { null }, 2.5f, "Andrzejki – noc wróżb ✦", "Wosk, tarot, szklana kula…", "Wróż z nami",
            "https://tvml.pl/strona/andrzejki", "https://tvml.pl/strona/andrzejki?widok=app",
        ),
    )

    var komunikatTestowy: String? = null
        set(v) {
            field = v
            when (v) {
                "aktualizacja" -> nowaWersja = AktualizacjaAplikacji.komunikat(99)
                "redakcja" -> komunikat = Komunikat("test", "Utrudnienia w ruchu", "Od poniedziałku remont ul. Słowiańskiej, objazd przez al. Jana Pawła II.", "warning", "https://tvml.pl/", tl("app.readMore", "Więcej"))
                "pilny" -> komunikat = Komunikat("test-pilny", "Komunikat pilny", "Przerwa w dostawie wody na Zatorzu do godz. 18.", "urgent", null, "")
                "wymagana" -> wymaganaAktualizacja = true
            }
        }

    /** Nowe API (/api/v1): config, artykuły, najczęściej czytane, wideo, pogoda. */
    private suspend fun odswiezV1(z: Siec.ZPamieci): Boolean {
        val jezykZapytan = Jezyk.biezacy
        // API 1.8.3 podaje scenę SVG motywów ze Studia motywów zawsze (weatherSceneType/weatherSceneSvgUrl), bez features
        val k = zakres.async(z) { pobierzKonfiguracje() }
        val a = zakres.async(z) { Siec.v1Obiekt("articles", mapOf("limit" to "20")) }
        val b = zakres.async(z) { Siec.v1Obiekt("articles/most-read", mapOf("limit" to "10")) }
        val d = zakres.async(z) { Siec.v1Obiekt("videos", mapOf("limit" to "15")) }
        val e = zakres.async(z) { Siec.v1Obiekt("weather") }
        val lista = a.await() ?: return false
        // podgląd bez sieci albo z błędem serwera: zostaje to, co widać (bez przechodzenia na stare API)
        val konf = k.await() ?: return podglad != null
        // język zmieniony w trakcie pobierania: te odpowiedzi są w poprzednim języku, więc tylko nowe pobranie
        if (Jezyk.biezacy != jezykZapytan) return true
        najnowsze = Artykul.widoczne(lista.lista("items"))
        zastosuj(konf)
        // redakcja wyłączyła wybrany język (albo serwer ma inną listę): od razu po polsku, bez mieszania języków
        if (Jezyk.biezacy != jezykZapytan) {
            przeladujJezyk()
            return true
        }
        b.await()?.let { najczytniejsze = Artykul.widoczne(it.lista("items")) }
        d.await()?.let {
            filmy = it.lista("items").map(::Film)
            filmyKursor = it.s("nextCursor").ifEmpty { null }
        }
        e.await()?.let { if (it.ma("current")) pogoda = Pogoda(it) }
        // listy bloków Startu z kategorii z panelu
        bloki.mapNotNull { it.kategoria }.distinct().forEach { slug -> zakres.launch { kategoria(slug) } }
        // blok poll: sonda albo kalendarz; osobny blok events tylko bez bloku poll (bez podwójnego kalendarza)
        bloki.filter { it.typ == "poll" }.forEach { blok -> zakres.launch { wczytajSpotlight(blok) } }
        val blokWydarzen = bloki.firstOrNull { it.typ == "events" }
        if (blokWydarzen != null && bloki.none { it.typ == "poll" }) {
            zakres.launch {
                Siec.v1Obiekt("events", mapOf("limit" to maxOf(blokWydarzen.limit, 5).toString()))?.let { wydarzenia = it.lista("items").map(::Wydarzenie) }
            }
        } else {
            wydarzenia = emptyList()
        }
        return true
    }

    private fun zastosuj(konf: JSONObject) {
        Jezyk.zKonfiguracji(konf)
        if (komunikatTestowy == null) {
            komunikat = Komunikat.z(konf.optJSONObject("message"))
            wymaganaAktualizacja = AktualizacjaAplikacji.wymagana(konf.o("minAppVersion").s("android"))
        }
        Powiadomienia.zKonfiguracji(aplikacja, konf.o("push"))
        konfiguracjaPush++
        // formularz „Zgłoś zdarzenie” w aplikacji (config.alerts.native); bez klucza dalej tvml.pl/#alert
        val al = konf.optJSONObject("alerts") ?: JSONObject()
        alerty = UstawieniaAlertu.z(al)
        ustawienia.edit { putString("alerts", al.toString()) }
        val m = Motyw.zV1(konf.o("theme"), konf.s("apiVersion").ifEmpty { "1.0.0" })
        if (m != null && motywTestowy == null) motyw = m
        motywZKonfiguracji = m
        wersjaApi = konf.s("apiVersion").ifEmpty { "1.0.0" }
        oknaMotywow = listOf(konf.o("theme")) + konf.lista("upcomingThemes")
        if (podgladDnia) chwilaTestowa?.let { val p = motywNa(it); motywTestowy = p; motyw = p }
        // scena pogody z harmonogramu (API 1.8.3: theme.weatherScene/backgroundScene/weatherSceneType/dateBar/stages,
        // upcomingThemes[].stages[], weatherAnimation[state]); starszy serwer bez etapów: całe okna upcomingThemes
        val th = konf.o("theme")
        scenaMotywu = th.s("weatherScene").ifEmpty { null }
        scenaTla = th.s("backgroundScene").ifEmpty { null }
        scenaSvg = OknoSceny.svgZ(th)
        pasekMotywu = PasekDaty.z(th.optJSONObject("dateBar"))
        koniecMotywu = Daty.iso(th.s("until"))
        val teraz = java.time.ZonedDateTime.now(Daty.strefa)
        // bieżący etap opisuje sam theme (wyliczony na teraz), więc z etapów bieżącego motywu tylko przyszłe
        val dalszeEtapy = th.lista("stages").mapNotNull(OknoSceny::z).filter { it.od != null && it.od.isAfter(teraz) }
        nadchodzaceSceny = dalszeEtapy + konf.lista("upcomingThemes").flatMap { u ->
            u.lista("stages").mapNotNull(OknoSceny::z).ifEmpty { listOfNotNull(OknoSceny.z(u)) }
        }
        val wa = konf.o("weatherAnimation")
        animacjePogody = wa.keys().asSequence().associateWith { OpcjeSceny.z(wa.optJSONObject(it)) }
        val z = konf.lista("tabs").mapNotNull(ZakladkaApp::z)
        if (z.isNotEmpty()) {
            val gotowe = z.take(5).toMutableList()
            if (gotowe.none { it.cel == ZakladkaApp.Cel.Wiecej }) {
                if (gotowe.size == 5) gotowe.removeAt(4)
                gotowe += ZakladkaApp.wiecej
            }
            zakladki = gotowe
            if (gotowe.none { it.id == zakladka }) zakladka = gotowe[0].id
        }
        val bl = konf.lista("home").mapIndexedNotNull { i, j -> BlokStartu.z(j, i) }
        if (bl.isNotEmpty()) bloki = if (banerTestowy) bl.take(2) + przykladBanera() + bl.drop(2) else bl
        val t = konf.o("ticker")
        pasek = Artykul.widoczne(t.lista("items"))
        pasekWlaczony = !t.ma("enabled") || t.b("enabled")
        etykietaPaska = t.s("label").ifEmpty { tl("app.latest", "Najnowsze").uppercase() }
        pasekNaZywo = t.b("live")
        gminy = konf.lista("counties").map(::Gmina).sortedBy { it.kolejnosc }
        val sub = konf.lista("subportals")
        if (sub.isNotEmpty()) {
            kategorieSubportali = sub.filter { it.s("slug").isNotEmpty() }.associate { it.s("slug") to it.s("category").ifEmpty { it.s("slug") } }
            // nazwa „Aktualności” z config.categories, bo serwer ją tłumaczy (Новини, News)
            val aktualnosci = konf.lista("categories").firstOrNull { it.s("slug") == "aktualnosci" }?.s("name")?.ifEmpty { null }
            kategorie = listOf(KategoriaMenu(tl("app.all", "Wszystko"), null), KategoriaMenu(aktualnosci ?: tl("app.news", "Aktualności"), "aktualnosci")) +
                // „TVML.pl - Sport” po polsku, „TVML.pl – Спорт” po ukraińsku: przedrostek z myślnikiem albo półpauzą
                sub.map { KategoriaMenu(it.s("name").replace(Regex("""^\s*TVML\.pl\s*[-–—]\s*"""), ""), it.s("slug")) }
        }
        val d0 = konf.o("today")
        // today.label serwer tłumaczy na uk i en (od API 1.8.0); bez niego data z today.date (imieniny zostają polskie)
        val dataDnia = runCatching { java.time.LocalDate.parse(d0.s("date")) }.getOrNull()
        val etykietaDnia = d0.s("label").ifEmpty { dataDnia?.let { Daty.dzisiaj(it) }.orEmpty() }
        // holiday: null albo nazwa święta (tekst, a na wszelki wypadek także obiekt z name/label)
        val swieto = when (val h = d0.opt("holiday")) {
            is String -> h
            is JSONObject -> h.s("name").ifEmpty { h.s("label") }
            else -> ""
        }.trim().ifEmpty { null }
        if (etykietaDnia.isNotEmpty() && !dzienTestowy) dzisiaj = Dzisiaj(etykietaDnia, d0.teksty("nameDays"), dataDnia, swieto)
        val s = konf.lista("pages").map(StronaRedakcji::z).filter { it.slug.isNotEmpty() && it.gotowa != false }
        if (s.isNotEmpty()) strony = s
        konf.o("about").s("footer").takeIf { it.isNotBlank() }?.let { stopka = Html.tekst(it) }
    }

    /** Zapas: te same akcje co strona (tvml-db.php i tvml-portal.php). */
    private suspend fun odswiezStare(z: Siec.ZPamieci) {
        val a = zakres.async(z) { runCatching { Siec.json("tvml-db.php?action=latest&limit=20") } }
        val b = zakres.async(z) { Siec.staraLista("tvml-db.php?action=most-read&limit=10") }
        val c = zakres.async(z) { Siec.staraLista("tvml-db.php?action=ticker&category=aktualnosci&limit=10") }
        val d = zakres.async(z) { Siec.staryObiekt("tvml-portal.php?action=youtube&channel=@TVMLlive&limit=12") }
        val e = zakres.async(z) { Siec.staryObiekt("tvml-portal.php?action=weather-full") }
        val wynik = a.await()
        val lista = (wynik.getOrNull() as? org.json.JSONArray)?.obiekty()
        if (lista != null) {
            najnowsze = Artykul.widoczne(lista)
            blad = null
            ostatnio = System.currentTimeMillis()
        } else if (najnowsze.isEmpty()) {
            blad = tl("app.loadError", "Nie udało się pobrać wiadomości. Sprawdź internet i przeciągnij w dół, żeby spróbować ponownie.")
        }
        b.await().takeIf { it.isNotEmpty() }?.let { najczytniejsze = Artykul.widoczne(it) }
        c.await().takeIf { it.isNotEmpty() }?.let { pasek = Artykul.widoczne(it) }
        d.await()?.let {
            filmy = it.lista("items").map(::Film)
            filmyKursor = null
        }
        e.await()?.let { if (it.ma("current")) pogoda = Pogoda(it) }
    }

    /**
     * Kolejne 15 filmów w zakładce Wideo po stuknięciu „Starsze filmy” (cursor=nextCursor). Archiwum na serwerze ma od
     * 25.11 także filmy z artykułów, ok. 140 (Karol: „w aplikacji nadal jest tylko 15 filmów”, potem „miało wczytywac
     * filmy po 15 i dac opcje wczytania kolejnych a nie od razu wszytskie”). Bez powtórek po id.
     */
    suspend fun wiecejFilmow() {
        val kursor = filmyKursor ?: return
        if (ladujeFilmy) return
        ladujeFilmy = true
        try {
            val j = Siec.v1Obiekt("videos", mapOf("limit" to "15", "cursor" to kursor)) ?: return
            // w międzyczasie odświeżenie listy: ta strona już nieaktualna
            if (filmyKursor != kursor) return
            val znane = filmy.map { it.id }.toSet()
            filmy = filmy + j.lista("items").map(::Film).filter { it.id !in znane }
            filmyKursor = j.s("nextCursor").ifEmpty { null }
        } finally {
            ladujeFilmy = false
        }
    }

    /** Lista działu: subportal (Sport, Muzyka, Na sygnale…) przez jego kategorię z panelu albo zwykła kategoria. */
    suspend fun kategoria(slug: String, limit: Int = 20) {
        val kat = kategorieSubportali[slug] ?: slug
        Siec.v1Obiekt("articles", mapOf("category" to kat, "limit" to limit.toString()))?.let {
            wKategorii[slug] = Artykul.widoczne(it.lista("items"))
            return
        }
        val lista = Siec.staraLista("tvml-db.php?action=news&category=${Siec.koduj(kat)}&limit=$limit")
        if (lista.isNotEmpty()) wKategorii[slug] = Artykul.widoczne(lista)
    }

    suspend fun lista(trasa: String, parametry: Map<String, String>): List<Artykul> =
        Siec.v1Obiekt(trasa, parametry)?.lista("items")?.let { Artykul.widoczne(it) } ?: emptyList()

    suspend fun gmina(g: Gmina): List<Artykul> = lista("articles", mapOf("county" to g.id, "limit" to "20"))

    suspend fun fotorelacje(limit: Int) {
        wKategorii["_fotorelacje"] = lista("articles", mapOf("photoReport" to "1", "limit" to maxOf(limit, 4).toString()))
    }

    /** Pełny artykuł; [jezyk] = "pl" pobiera polski oryginał przetłumaczonego tekstu („Pokaż oryginał po polsku”). */
    suspend fun artykul(id: Int, jezyk: String? = null): Artykul? {
        Siec.v1Obiekt("articles/$id", jezyk?.let { mapOf("lang" to it) } ?: emptyMap())?.let { if (it.ma("id")) return Artykul(it) }
        val j = Siec.staryObiekt("tvml-db.php?action=article&id=$id") ?: return null
        return if (j.ma("id")) Artykul(j) else null
    }

    suspend fun szukaj(fraza: String): List<Artykul> {
        Siec.v1Obiekt("search", mapOf("q" to fraza, "limit" to "20"))?.let { return Artykul.widoczne(it.lista("items")) }
        if (fraza.isBlank()) return emptyList()
        return Artykul.widoczne(Siec.staraLista("tvml-db.php?action=search&q=${Siec.koduj(fraza)}&limit=20"))
    }

    // ---------- Więcej: podstrony, ogłoszenia ----------

    /** Treść podstrony redakcji (/api/v1/pages/<slug>); null przy błędzie sieci albo nieznanej stronie. */
    suspend fun podstrona(slug: String): TrescPodstrony? {
        trescStron[slug]?.let { return it }
        val j = Siec.v1Obiekt("pages/$slug")?.takeIf { it.has("html") } ?: return null
        return TrescPodstrony(j).also { trescStron[slug] = it }
    }

    /**
     * Po wczytaniu listy podstron: które mają treść (Karol, 22.11: tego, co w budowie, nie publikujemy)
     * i czy są ogłoszenia oraz fotorelacje. Od API 1.7.1 o stronach decyduje serwer (ready, app), a sprawdzanie
     * treści zostaje dla starszych odpowiedzi. Bez sieci zostaje to, co było.
     */
    suspend fun sprawdzStrony() {
        val slugi = strony.map { it.slug }
        if (slugi != sprawdzoneStrony) {
            sprawdzoneStrony = slugi
            val doSprawdzenia = strony.filter { it.gotowa == null }.map { it.slug }
            val odpowiedzi = doSprawdzenia.map { s -> zakres.async { s to Siec.v1Obiekt("pages/$s")?.takeIf { it.has("html") } } }
                .mapNotNull { z -> z.await().let { (s, j) -> j?.let { s to TrescPodstrony(it) } } }
            odpowiedzi.forEach { (s, t) -> trescStron[s] = t }
            if (doSprawdzenia.isEmpty() || odpowiedzi.isNotEmpty()) {
                stronyGotowe = odpowiedzi.filter { it.second.gotowa }.map { it.first }.toSet()
            } else {
                sprawdzoneStrony = null
            }
        }
        Siec.v1Obiekt("classifieds", mapOf("limit" to "1"))?.let { maOgloszenia = it.lista("items").isNotEmpty() }
        Siec.v1Obiekt("articles", mapOf("photoReport" to "1", "limit" to "1"))?.let { maFotorelacje = Artykul.widoczne(it.lista("items")).isNotEmpty() }
    }

    /** Ogłoszenia drobne do ekranu z „Więcej”. */
    suspend fun wszystkieOgloszenia(): List<Ogloszenie>? =
        Siec.v1Obiekt("classifieds", mapOf("limit" to "50"))?.lista("items")?.map(::Ogloszenie)

    // ---------- Start: sonda albo kalendarz wydarzeń ----------

    /** Blok poll: sonda, gdy trwa, a bez niej kalendarz wydarzeń (/api/v1/spotlight). Przy błędzie sieci zostaje poprzedni stan. */
    suspend fun wczytajSpotlight(b: BlokStartu) {
        val p = mutableMapOf("limit" to "12")
        b.ankieta?.let { p["pollId"] = it }
        val j = Siec.v1Obiekt("spotlight", p) ?: return
        val sonda = j.optJSONObject("poll")
        spotlight[b.id] = if (j.s("type") == "poll" && sonda != null) Spotlight.ZSonda(Sonda(sonda)) else Spotlight.ZKalendarzem(KalendarzSpot(j.o("events")))
    }

    /** Głos w sondzie (POST /api/v1/polls/{id}/vote, bez identyfikatorów). Zwraca komunikat dla czytelnika albo null. */
    suspend fun glosuj(sonda: Sonda, klucz: String, blok: BlokStartu): String? {
        val (kod, j) = try {
            Siec.v1Wyslij("polls/${sonda.id}/vote", JSONObject().put("option", klucz))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return tl("app.voteOffline", "Brak połączenia. Spróbuj jeszcze raz.")
        }
        return when (kod) {
            200 -> {
                glosy[sonda.id] = klucz
                ustawienia.edit { putString("glosy", JSONObject(glosy.toMap()).toString()) }
                j.optJSONObject("poll")?.let { spotlight[blok.id] = Spotlight.ZSonda(Sonda(it)) }
                if (j.b("counted") || j.s("message").isEmpty()) null else j.s("message")
            }
            409 -> {
                wczytajSpotlight(blok)
                tl("app.pollClosed", "Głosowanie w tej sondzie zostało zakończone.")
            }
            else -> j.o("error").s("message").ifEmpty { tl("app.voteError", "Nie udało się oddać głosu.") }
        }
    }

    /** Wydarzenia jednego dnia, także trwające wtedy (/api/v1/events?from=D&to=D); null bez sieci. */
    suspend fun wydarzeniaDnia(d: String): List<Wydarzenie>? =
        Siec.v1Obiekt("events", mapOf("from" to d, "to" to d, "limit" to "50"))?.lista("items")?.map(::Wydarzenie)

    /** Szczegóły wydarzenia (/api/v1/events/{id}): opis, organizator, bilety. */
    suspend fun wydarzenie(id: Int): Wydarzenie? = Siec.v1Obiekt("events/$id")?.takeIf { it.ma("id") }?.let(::Wydarzenie)

    /** Komentarze czytelników zatwierdzone przez redakcję (akcja strony; w API v1 jeszcze ich nie ma). */
    suspend fun komentarze(a: Artykul): List<JSONObject> {
        val j = runCatching { Siec.json("tvml-db.php?action=get-comments&post_id=${a.id}") }.getOrNull()
        return when (j) {
            is org.json.JSONArray -> j.obiekty()
            is JSONObject -> j.lista("comments").ifEmpty { j.lista("items") }
            else -> emptyList()
        }
    }

    fun przelaczZapis(a: Artykul) {
        if (a.id in zapisane) {
            zapisane.remove(a.id)
            skrotyZapisanych.remove(a.id)
        } else {
            zapisane.add(0, a.id)
            skrotyZapisanych[a.id] = skrot(a)
        }
        ustawienia.edit { putString("zapisane", zapisane.joinToString(",")) }
        zapiszSkroty()
    }

    /**
     * „Zapisane”: jednym zapytaniem articles?ids= (API 1.8.1, kolejność z parametru, tytuły w wybranym języku, bez próśb
     * o tłumaczenie) i od razu nowe skróty. Bez sieci albo na starszym serwerze (ignoruje ids i podaje najnowsze) skróty
     * z telefonu, a starsze zapisy bez skrótu raz po polsku (lang=pl nie zgłasza tłumaczenia).
     */
    suspend fun zapisaneArtykuly(): List<Artykul> {
        val numery = zapisane.take(30).toList()
        if (numery.isEmpty()) return emptyList()
        val j = Siec.v1Obiekt("articles", mapOf("ids" to numery.joinToString(",")))
        val pozycje = j?.lista("items")
        if (pozycje != null && pozycje.isNotEmpty() && pozycje.all { o -> Artykul(o).id in numery }) {
            val lista = Artykul.widoczne(pozycje)
            lista.forEach { skrotyZapisanych[it.id] = skrot(it) }
            zapiszSkroty()
            return lista
        }
        val lista = numery.mapNotNull { id ->
            skrotyZapisanych[id]?.let { runCatching { Artykul(it) }.getOrNull() }
                ?: artykul(id, jezyk = "pl")?.also { skrotyZapisanych[id] = skrot(it) }
        }
        zapiszSkroty()
        return lista
    }

    private fun skrot(a: Artykul): JSONObject =
        JSONObject(a.dane.toString()).apply { listOf("contentHtml", "content", "related", "ttsUrl").forEach { remove(it) } }

    private fun zapiszSkroty() {
        val j = JSONObject()
        skrotyZapisanych.filterKeys { it in zapisane }.forEach { (id, o) -> j.put(id.toString(), o) }
        ustawienia.edit { putString("zapisane-skroty", j.toString()) }
    }

    // ---------- nawigacja ----------

    fun otworz(e: Ekran) {
        sciezki[zakladka] = sciezki[zakladka].orEmpty() + e
    }

    fun otworz(a: Artykul) = otworz(Ekran.Tekst(a))

    /** Otwiera artykuł z linku (tvml.pl/artykul/123): od razu pobiera go z API. Bez artykułu wykonuje [inaczej]. */
    fun otworzArtykul(id: Int, inaczej: () -> Unit = {}) {
        zakres.launch { artykul(id)?.let { otworz(it) } ?: inaczej() }
    }

    /** Wydarzenie z artykułem na tvml.pl otwiera artykuł, a bez niego ekran wydarzenia w aplikacji. */
    fun otworz(w: Wydarzenie) {
        val id = w.artykulId
        if (id != null) otworzArtykul(id) { otworz(Ekran.JednoWydarzenie(w)) } else otworz(Ekran.JednoWydarzenie(w))
    }

    fun wroc(): Boolean {
        val s = sciezki[zakladka].orEmpty()
        if (s.isEmpty()) return false
        sciezki[zakladka] = s.dropLast(1)
        return true
    }

    /** Pogoda i wideo: zakładka, jeśli panel ją ustawił, a jeśli nie, arkusz nad bieżącym ekranem. */
    fun pokaz(cel: ZakladkaApp.Cel) {
        zakladki.firstOrNull { it.cel == cel }?.let { zakladka = it.id; return }
        if (cel == ZakladkaApp.Cel.Pogoda) arkuszPogody = true
        if (cel == ZakladkaApp.Cel.Wideo) arkuszWideo = true
    }

    fun naStart() {
        val start = zakladki.firstOrNull { it.cel == ZakladkaApp.Cel.Start }?.id ?: zakladki.firstOrNull()?.id ?: "home"
        zakladka = start
        sciezki[start] = emptyList()
    }
}
