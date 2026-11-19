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
}

/** Wszystko, co aplikacja pobiera z tvml.pl. Jedno źródło dla wszystkich ekranów (Magazyn na iPhonie). */
class Magazyn(kontekst: Context, private val zakres: CoroutineScope) {
    private val ustawienia = kontekst.getSharedPreferences("tvml", Context.MODE_PRIVATE)

    var najnowsze by mutableStateOf<List<Artykul>>(emptyList())
    var najczytniejsze by mutableStateOf<List<Artykul>>(emptyList())
    var pasek by mutableStateOf<List<Artykul>>(emptyList())
    var pasekWlaczony by mutableStateOf(true)
    var etykietaPaska by mutableStateOf("NA ŻYWO")
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
    var strony by mutableStateOf(listOf(
        StronaRedakcji("o-nas", "O nas"), StronaRedakcji("redakcja", "Redakcja"), StronaRedakcji("reklama", "Reklama"),
        StronaRedakcji("kontakt", "Kontakt"), StronaRedakcji("regulamin", "Regulamin"), StronaRedakcji("polityka-prywatnosci", "Polityka prywatności"),
    ))
    var arkuszPogody by mutableStateOf(false)
    var arkuszWideo by mutableStateOf(false)
    var arkuszSzukaj by mutableStateOf(false)
    val zapisane = mutableStateListOf<Int>().apply {
        addAll(ustawienia.getString("zapisane", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() })
    }
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
        val k = zakres.async { Siec.v1Obiekt("config") }
        val a = zakres.async { Siec.v1Obiekt("articles", mapOf("limit" to "20")) }
        val b = zakres.async { Siec.v1Obiekt("articles/most-read", mapOf("limit" to "10")) }
        val d = zakres.async { Siec.v1Obiekt("videos", mapOf("limit" to "12")) }
        val e = zakres.async { Siec.v1Obiekt("weather") }
        val lista = a.await() ?: return false
        val konf = k.await() ?: return false
        najnowsze = lista.lista("items").map(::Artykul)
        zastosuj(konf)
        b.await()?.let { najczytniejsze = it.lista("items").map(::Artykul) }
        d.await()?.let { filmy = it.lista("items").map(::Film) }
        e.await()?.let { if (it.ma("current")) pogoda = Pogoda(it) }
        // listy bloków Startu z kategorii z panelu (np. artykuły sponsorowane)
        bloki.mapNotNull { it.kategoria }.distinct().forEach { slug -> zakres.launch { kategoria(slug) } }
        return true
    }

    private fun zastosuj(konf: JSONObject) {
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
        pasek = t.lista("items").map(::Artykul)
        pasekWlaczony = !t.ma("enabled") || t.b("enabled")
        etykietaPaska = t.s("label").ifEmpty { "NA ŻYWO" }
        gminy = konf.lista("counties").map(::Gmina).sortedBy { it.kolejnosc }
        val sub = konf.lista("subportals")
        if (sub.isNotEmpty()) {
            kategorieSubportali = sub.filter { it.s("slug").isNotEmpty() }.associate { it.s("slug") to it.s("category").ifEmpty { it.s("slug") } }
            kategorie = listOf(KategoriaMenu("Wszystko", null), KategoriaMenu("Aktualności", "aktualnosci")) +
                sub.map { KategoriaMenu(it.s("name").replace("TVML.pl - ", ""), it.s("slug")) }
        }
        val d0 = konf.o("today")
        if (d0.s("label").isNotEmpty()) dzisiaj = Dzisiaj(d0.s("label"), d0.teksty("nameDays"))
        val s = konf.lista("pages").map { StronaRedakcji(it.s("slug"), Html.tekst(it.s("title"))) }.filter { it.slug.isNotEmpty() }
        if (s.isNotEmpty()) strony = s
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
            najnowsze = lista.map(::Artykul)
            blad = null
            ostatnio = System.currentTimeMillis()
        } else if (najnowsze.isEmpty()) {
            blad = "Nie udało się pobrać wiadomości. Sprawdź internet i przeciągnij w dół, żeby spróbować ponownie."
        }
        b.await().takeIf { it.isNotEmpty() }?.let { najczytniejsze = it.map(::Artykul) }
        c.await().takeIf { it.isNotEmpty() }?.let { pasek = it.map(::Artykul) }
        d.await()?.let { filmy = it.lista("items").map(::Film) }
        e.await()?.let { if (it.ma("current")) pogoda = Pogoda(it) }
    }

    /** Lista działu: subportal (Sport, Muzyka, Na sygnale…) przez jego kategorię z panelu albo zwykła kategoria. */
    suspend fun kategoria(slug: String, limit: Int = 20) {
        val kat = kategorieSubportali[slug] ?: slug
        Siec.v1Obiekt("articles", mapOf("category" to kat, "limit" to limit.toString()))?.let {
            wKategorii[slug] = it.lista("items").map(::Artykul)
            return
        }
        val lista = Siec.staraLista("tvml-db.php?action=news&category=${Siec.koduj(kat)}&limit=$limit")
        if (lista.isNotEmpty()) wKategorii[slug] = lista.map(::Artykul)
    }

    suspend fun lista(trasa: String, parametry: Map<String, String>): List<Artykul> =
        Siec.v1Obiekt(trasa, parametry)?.lista("items")?.map(::Artykul) ?: emptyList()

    suspend fun gmina(g: Gmina): List<Artykul> = lista("articles", mapOf("county" to g.id, "limit" to "20"))

    suspend fun fotorelacje(limit: Int) {
        wKategorii["_fotorelacje"] = lista("articles", mapOf("photoReport" to "1", "limit" to maxOf(limit, 4).toString()))
    }

    suspend fun artykul(id: Int): Artykul? {
        Siec.v1Obiekt("articles/$id")?.let { if (it.ma("id")) return Artykul(it) }
        val j = Siec.staryObiekt("tvml-db.php?action=article&id=$id") ?: return null
        return if (j.ma("id")) Artykul(j) else null
    }

    suspend fun szukaj(fraza: String): List<Artykul> {
        Siec.v1Obiekt("search", mapOf("q" to fraza, "limit" to "20"))?.let { return it.lista("items").map(::Artykul) }
        if (fraza.isBlank()) return emptyList()
        return Siec.staraLista("tvml-db.php?action=search&q=${Siec.koduj(fraza)}&limit=20").map(::Artykul)
    }

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
        if (a.id in zapisane) zapisane.remove(a.id) else zapisane.add(0, a.id)
        ustawienia.edit { putString("zapisane", zapisane.joinToString(",")) }
    }

    // ---------- nawigacja ----------

    fun otworz(e: Ekran) {
        sciezki[zakladka] = sciezki[zakladka].orEmpty() + e
    }

    fun otworz(a: Artykul) = otworz(Ekran.Tekst(a))

    /** Otwiera artykuł z linku (tvml.pl/artykul/123): od razu pobiera go z API. */
    fun otworzArtykul(id: Int) {
        zakres.launch { artykul(id)?.let { otworz(it) } }
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
