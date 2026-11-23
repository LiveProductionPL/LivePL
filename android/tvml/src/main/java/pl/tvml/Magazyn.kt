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
    data class JednoWydarzenie(val wydarzenie: Wydarzenie) : Ekran
}

/** Wszystko, co aplikacja pobiera z tvml.pl. Jedno źródło dla wszystkich ekranów (Magazyn na iPhonie). */
class Magazyn(kontekst: Context, private val zakres: CoroutineScope) {
    private val ustawienia = kontekst.getSharedPreferences("tvml", Context.MODE_PRIVATE)

    var najnowsze by mutableStateOf<List<Artykul>>(emptyList())
    var najczytniejsze by mutableStateOf<List<Artykul>>(emptyList())
    var pasek by mutableStateOf<List<Artykul>>(emptyList())
    var pasekWlaczony by mutableStateOf(true)
    /** Etykieta paska pod banerem (config.ticker.label) i czy to transmisja (config.ticker.live). */
    var etykietaPaska by mutableStateOf(tl("app.latest", "Najnowsze").uppercase())
    var pasekNaZywo by mutableStateOf(false)
    var filmy by mutableStateOf<List<Film>>(emptyList())
    var pogoda by mutableStateOf<Pogoda?>(null)
    var motyw by mutableStateOf(Motyw.zwykly)
    /** Motyw z argumentu (zrzuty ekranu); wtedy panel go nie zmienia. */
    var motywTestowy: Motyw? = null
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

    suspend fun odswiezTeraz(wymus: Boolean = false) {
        if (!wymus && System.currentTimeMillis() - ostatnio < 120_000) return
        laduje = true
        try {
            if (odswiezV1()) {
                blad = null
                ostatnio = System.currentTimeMillis()
            } else {
                odswiezStare()
            }
        } finally {
            laduje = false
        }
    }

    /** Nowe API (/api/v1): config, artykuły, najczęściej czytane, wideo, pogoda. */
    private suspend fun odswiezV1(): Boolean {
        val jezykZapytan = Jezyk.biezacy
        val k = zakres.async { Siec.v1Obiekt("config") }
        val a = zakres.async { Siec.v1Obiekt("articles", mapOf("limit" to "20")) }
        val b = zakres.async { Siec.v1Obiekt("articles/most-read", mapOf("limit" to "10")) }
        val d = zakres.async { Siec.v1Obiekt("videos", mapOf("limit" to "12")) }
        val e = zakres.async { Siec.v1Obiekt("weather") }
        val lista = a.await() ?: return false
        val konf = k.await() ?: return false
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
        d.await()?.let { filmy = it.lista("items").map(::Film) }
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
        val m = Motyw.zV1(konf.o("theme"), konf.s("apiVersion").ifEmpty { "1.0.0" })
        if (m != null && motywTestowy == null) motyw = m
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
        if (bl.isNotEmpty()) bloki = bl
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
        // today.label jest zawsze po polsku, więc w innym języku data z today.date (imieniny zostają polskie)
        val dataDnia = runCatching { java.time.LocalDate.parse(d0.s("date")) }.getOrNull()
        val etykietaDnia = if (Jezyk.biezacy != "pl" && dataDnia != null) Daty.dzisiaj(dataDnia) else d0.s("label")
        if (etykietaDnia.isNotEmpty()) dzisiaj = Dzisiaj(etykietaDnia, d0.teksty("nameDays"))
        val s = konf.lista("pages").map(StronaRedakcji::z).filter { it.slug.isNotEmpty() && it.gotowa != false }
        if (s.isNotEmpty()) strony = s
        konf.o("about").s("footer").takeIf { it.isNotBlank() }?.let { stopka = Html.tekst(it) }
    }

    /** Zapas: te same akcje co strona (tvml-db.php i tvml-portal.php). */
    private suspend fun odswiezStare() {
        val a = zakres.async { runCatching { Siec.json("tvml-db.php?action=latest&limit=20") } }
        val b = zakres.async { Siec.staraLista("tvml-db.php?action=most-read&limit=10") }
        val c = zakres.async { Siec.staraLista("tvml-db.php?action=ticker&category=aktualnosci&limit=10") }
        val d = zakres.async { Siec.staryObiekt("tvml-portal.php?action=youtube&channel=@TVMLlive&limit=12") }
        val e = zakres.async { Siec.staryObiekt("tvml-portal.php?action=weather-full") }
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
        d.await()?.let { filmy = it.lista("items").map(::Film) }
        e.await()?.let { if (it.ma("current")) pogoda = Pogoda(it) }
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
