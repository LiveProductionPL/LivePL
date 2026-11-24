package pl.tvml

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Artykuł z API v1 (image i category jako obiekty) albo ze starych akcji tvml-db.php. */
class Artykul(j: JSONObject) {
    /** Odpowiedź serwera, z której powstał artykuł (do skrótu zapisanego w telefonie, „Zapisane”). */
    val dane: JSONObject = j
    val id: Int = if (j.ma("id")) j.i("id") else j.i("articleId")
    val tytul: String = Html.tekst(j.s("title"))
    val lead: String = Html.tekst(if (j.ma("lead")) j.s("lead") else j.s("excerpt"))
    val obraz: String
    val zrodloZdjecia: String
    /** Gotowe rozmiary obrazu z API v1 (400, 800, 1200). */
    val warianty: Map<Int, String>
    val kategoria: String
    val kolor: String
    /** Slug kategorii (category.slug w API v1), np. „artykuly-sponsorowane”. */
    val kategoriaSlug: String
    val sponsorowany: Boolean = j.o("flags").b("sponsored")
    val autor: String
    val data: ZonedDateTime?
    val komentarze: Int = if (j.ma("commentsCount")) j.i("commentsCount") else j.i("comments")
    val odslony: Int = j.i("views")
    val tagi: List<String>
    val tresc: String?
    val pilne: Boolean = j.o("flags").b("urgent")
    val link: String
    /** API 1.8.0: język tekstu (pl, uk, en) i czy to tłumaczenie redakcji. Bez tych pól (lang=pl) artykuł jest po polsku. */
    val jezyk: String = j.s("lang").ifEmpty { "pl" }
    val przetlumaczony: Boolean = j.b("translated")
    /** Tłumaczenie w przygotowaniu: tekst jest po polsku, a nad nim jedno zdanie w wybranym języku. */
    val tlumaczenieWDrodze: Boolean = j.b("translationPending") && !j.b("translated")
    /** Kiedy będzie tłumaczenie na żądanie: „hour” (zwykle w ciągu godziny) albo „morning” (w nocy, będzie rano). */
    val terminTlumaczenia: String = j.s("translationEta")

    init {
        val o = j.optJSONObject("image")
        if (o != null) {
            obraz = o.s("url")
            zrodloZdjecia = Html.tekst(o.s("source"))
            val w = mutableMapOf<Int, String>()
            o.optJSONObject("variants")?.let { v -> v.keys().forEach { k -> k.toIntOrNull()?.let { n -> v.optString(k).takeIf { it.isNotEmpty() }?.let { w[n] = it } } } }
            warianty = w
        } else {
            obraz = j.s("image")
            zrodloZdjecia = Html.tekst(j.s("image_source"))
            warianty = emptyMap()
        }
        val k = j.optJSONObject("category")
        if (k != null) {
            kategoria = Html.tekst(k.s("name")); kolor = k.s("color"); kategoriaSlug = k.s("slug")
        } else {
            kategoria = Html.tekst(j.s("category")); kolor = j.s("categoryColor"); kategoriaSlug = j.s("categorySlug")
        }
        autor = j.optJSONObject("author")?.s("name") ?: j.s("author")
        data = if (j.ma("publishedAt")) Daty.iso(j.s("publishedAt")) else Daty.stara(j.s("date"))
        val t = j.optJSONArray("tags")
        tagi = if (t != null) t.obiekty().map { it.s("name") }.filter { it.isNotEmpty() }
        else j.s("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val tr = if (j.ma("contentHtml")) j.s("contentHtml") else j.s("content")
        tresc = tr.ifEmpty { null }
        link = j.s("shareUrl").ifEmpty { "${Siec.BAZA}artykul/$id" }
    }

    fun miniatura(szerokosc: Int = 600): String? {
        val klucz = warianty.keys.sorted().firstOrNull { it >= szerokosc } ?: warianty.keys.maxOrNull()
        if (klucz != null) return warianty[klucz]
        return Siec.obraz(obraz, szerokosc)
    }

    /** „1 dzień temu”, jak na stronie. */
    val kiedy: String get() = data?.let { Daty.temu(it) } ?: ""

    // bez equals po numerze: stan Compose pomija wartość „równą” poprzedniej, więc tłumaczenie tego samego artykułu
    // (ten sam numer) albo lista po zmianie języka nie odświeżałyby ekranu; listy i ekrany mają własne klucze z numerem

    companion object {
        const val SPONSOROWANE = "artykuly-sponsorowane"

        /** Artykuły sponsorowane są w aplikacji wyłączone (Karol, 22.11.2026), niezależnie od panelu i serwera. */
        fun widoczne(lista: List<JSONObject>): List<Artykul> =
            lista.map(::Artykul).filter { !it.sponsorowany && it.kategoriaSlug != SPONSOROWANE }
    }
}

class Film(j: JSONObject) {
    val id: String = j.s("youtubeId").ifEmpty { j.s("id") }
    val tytul: String = Html.tekst(j.s("title"))
    val obraz: String = j.s("thumbnail").ifEmpty { j.s("image") }
    val data: String = j.s("publishedAt").ifEmpty { j.s("published") }
    val wyroznione: Boolean = j.b("featured")
    val kiedy: String get() = Daty.iso(data)?.let { Daty.temu(it) } ?: ""
}

class Pogoda(j: JSONObject) {
    class Dzien(val nazwa: String, val ikona: String, val opis: String, val max: Double?, val min: Double?, val opad: Int)
    class Godzina(val czas: String, val ikona: String, val temp: Double?, val opad: Int)

    private val c = j.o("current")
    val temperatura = c.d("temperature")
    val odczuwalna = c.d("feelsLike")
    val wilgotnosc = c.d("humidity")
    val cisnienie = c.d("pressure")
    val wiatr = c.d("windKmh")
    val kierunek = c.s("windDirText")
    val ikona: String
    val opis: String
    val dzien: Boolean
    val wschod = j.s("sunrise").ifEmpty { c.s("sunrise") }
    val zachod = j.s("sunset").ifEmpty { c.s("sunset") }
    val dni: List<Dzien>
    val godziny: List<Godzina>
    /** Animacja tła z serwera (API 1.7.2); null na starszym serwerze, wtedy scena według stanu (state). */
    val efekt: EfektPogody? = j.s("effect").takeIf { it in EfektPogody.rodzaje }?.let { e ->
        EfektPogody(
            e, (j.d("intensity") ?: 0.5).toFloat().coerceIn(0f, 1f), j.b("windy"), j.b("leaves"),
            j.s("phase").ifEmpty { if (j.b("isNight")) "night" else "day" },
        )
    }

    init {
        // API v1: state, isNight, daily, hourly (płaska lista 24 godzin); stare weather-full: current.icon
        if (j.ma("state")) {
            ikona = j.s("state")
            opis = j.s("description").replace(" - Noc", "").replace(" - Dzień", "")
            dzien = !j.b("isNight")
        } else {
            ikona = c.s("icon"); opis = c.s("description")
            dzien = if (c.ma("is_day")) c.b("is_day") else true
        }
        val prognoza = j.lista("daily").ifEmpty { j.lista("forecast").ifEmpty { c.lista("forecast") } }
        dni = prognoza.map { d ->
            Dzien(d.s("label"), d.s("state").ifEmpty { d.s("icon") }, d.s("desc"), d.d("temp"), d.d("tempMin"), d.i("pop"))
        }
        val h = j.optJSONArray("hourly")
        val godzinowa = if (h != null && h.length() > 0 && h.opt(0) is JSONArray) {
            (0 until h.length()).flatMap { (h.optJSONArray(it) ?: JSONArray()).obiekty() }
        } else h?.obiekty() ?: emptyList()
        godziny = godzinowa.map { g -> Godzina(g.s("time"), g.s("state").ifEmpty { g.s("icon") }, g.d("temp"), g.i("pop")) }
    }

    companion object {
        /** „7,5°” po polsku i ukraińsku, „7.5°” po angielsku. */
        fun stopnie(v: Double?): String = v?.let { String.format(Jezyk.locale, "%.1f°", it) } ?: "–"
    }
}

/** Zakładka dolnego paska ustawiana w panelu (config.tabs). */
data class ZakladkaApp(val id: String, val tytul: String, val ikona: String, val cel: Cel) {
    sealed interface Cel {
        data object Start : Cel
        data object Pogoda : Cel
        data object Wideo : Cel
        data object Wiecej : Cel
        data class Kategoria(val slug: String) : Cel
    }

    companion object {
        val wiecej get() = ZakladkaApp("more", tl("app.tab.more", "Więcej"), "more", Cel.Wiecej)
        /** Bez API (pierwsze uruchomienie bez sieci); nazwy w wybranym języku ze słownika. */
        val domyslne get() = listOf(
            ZakladkaApp("home", tl("app.tab.home", "Start"), "home", Cel.Start),
            ZakladkaApp("sport", tl("app.tab.sport", "Sport"), "sport", Cel.Kategoria("sport")),
            ZakladkaApp("video", tl("app.tab.video", "Wideo"), "video", Cel.Wideo),
            ZakladkaApp("weather", tl("app.tab.weather", "Pogoda"), "weather", Cel.Pogoda),
            wiecej,
        )

        fun z(j: JSONObject): ZakladkaApp? {
            val cel = j.s("target")
            val c: Cel = when (cel) {
                "home" -> Cel.Start
                "video" -> Cel.Wideo
                "weather" -> Cel.Pogoda
                "more" -> Cel.Wiecej
                else -> {
                    val i = cel.indexOf(':')
                    if (i < 0) return null
                    Cel.Kategoria(cel.substring(i + 1))
                }
            }
            return ZakladkaApp(j.s("id").ifEmpty { cel }, j.s("title"), j.s("icon"), c)
        }
    }
}

/**
 * Blok ekranu Start z panelu (Page builder, wersja na telefon), w kolejności z config.home.
 * [ankieta] to sonda bloku poll (props.pollId); null = pierwsza z układu strony.
 */
data class BlokStartu(
    val id: String, val typ: String, val tytul: String, val ukryjTytul: Boolean, val limit: Int, val kategoria: String?,
    val ankieta: String? = null,
) {
    companion object {
        val znane = setOf("hero", "weather", "alert", "video", "posts", "mostread", "gallery", "counties", "social", "poll", "events")
        val domyslne get() = listOf(
            BlokStartu("hero", "hero", "", true, 5, null),
            BlokStartu("weather", "weather", tl("app.weather", "Pogoda"), true, 0, null),
            BlokStartu("posts", "posts", tl("app.news", "Aktualności"), false, 10, null),
            BlokStartu("mostread", "mostread", tl("app.mostRead", "Najczęściej czytane"), false, 5, null),
            BlokStartu("video", "video", tl("app.video", "Wideo"), false, 6, null),
        )

        /** Typy nieznane aplikacji (ogłoszenia, newsletter…) pomija, jak na iPhonie. */
        fun z(j: JSONObject, nr: Int): BlokStartu? {
            val typ = j.s("type")
            if (typ !in znane) return null
            val p = j.o("props")
            // artykuły sponsorowane są w aplikacji wyłączone (Karol, 22.11.2026): bez list z ich kategorią albo flagą
            if (p.s("category") == Artykul.SPONSOROWANE || p.b("sponsored")) return null
            return BlokStartu(
                j.s("id").ifEmpty { "$typ-$nr" }, typ, Html.tekst(j.s("title")), p.b("hideTitle"), p.i("limit"),
                p.s("category").ifEmpty { null }, p.s("pollId").ifEmpty { null },
            )
        }
    }
}

class Gmina(j: JSONObject) {
    val id: String = j.s("tag").ifEmpty { j.s("id") }
    val nazwa: String = j.s("name")
    val herb: String = j.s("crest")
    val kolejnosc: Int = j.i("order")
}

data class KategoriaMenu(val nazwa: String, val slug: String?) {
    companion object {
        val domyslne = listOf(
            KategoriaMenu("Wszystko", null), KategoriaMenu("Aktualności", "aktualnosci"), KategoriaMenu("Sport", "sport"),
            KategoriaMenu("Na sygnale", "na-sygnale"), KategoriaMenu("Kultura", "kultura"), KategoriaMenu("Muzyka", "muzyka"),
        )
    }
}

/**
 * Dzień z config.today: [etykieta] z rokiem (ekran Pogoda), [data] do krótkiej daty na karcie pogody na Start,
 * imieniny zawsze po polsku i [swieto], gdy serwer je poda (holiday nie jest null).
 */
data class Dzisiaj(val etykieta: String, val imieniny: List<String>, val data: java.time.LocalDate? = null, val swieto: String? = null)

/**
 * Podstrona redakcji z config.pages (O nas, Kontakt, Regulamin…), w kolejności z panelu. [adresApp] to wersja strony
 * dla aplikacji (appUrl, ?widok=app). API 1.7.1: [sekcja] „tvml” albo „dokumenty” (bez pola „tvml”), a [gotowa]
 * z pól ready i app (false: pusta, „Wkrótce”, w budowie albo tylko www). Bez tych pól (starszy serwer) [gotowa] jest
 * null i o pokazaniu decyduje sprawdzenie treści w aplikacji (Magazyn.sprawdzStrony).
 */
data class StronaRedakcji(val slug: String, val tytul: String, val adresApp: String = "", val sekcja: String = "tvml", val gotowa: Boolean? = null) {
    companion object {
        const val DOKUMENTY = "dokumenty"

        fun z(j: JSONObject) = StronaRedakcji(
            j.s("slug"), Html.tekst(j.s("title")), j.s("appUrl"), j.s("section").ifEmpty { "tvml" },
            if (j.ma("ready") || j.ma("app")) (!j.ma("ready") || j.b("ready")) && (!j.ma("app") || j.b("app")) else null,
        )

        private fun domyslna(slug: String, tytul: String, sekcja: String = "tvml") = StronaRedakcji(slug, tytul, "${Siec.BAZA}strona/$slug?widok=app", sekcja)

        /** Bez API (stary serwer albo brak sieci przy pierwszym starcie). Podstrony są na razie tylko po polsku. */
        val domyslne = listOf(
            domyslna("o-nas", "O nas"), domyslna("redakcja", "Redakcja"), domyslna("kontakt", "Kontakt"),
            domyslna("regulamin", "Regulamin", DOKUMENTY), domyslna("polityka-prywatnosci", "Polityka prywatności", DOKUMENTY),
        )
    }
}

/** Treść podstrony z /api/v1/pages/<slug>: bezpieczny HTML z panelu (p, h2–h4, a, listy, obrazy, tabele). */
class TrescPodstrony(j: JSONObject) {
    val slug: String = j.s("slug")
    val tytul: String = Html.tekst(j.s("title"))
    val html: String = j.s("html")
    val aktualizacja: ZonedDateTime? = Daty.iso(j.s("updatedAt"))
    val adresApp: String = j.s("appUrl")
    /**
     * Czy pokazać stronę w „Więcej” (Karol, 22.11: tego, co w budowie, nie publikujemy): API 1.7.1 ready i app,
     * a w starszych odpowiedziach (bez tych pól) co najmniej 40 znaków tekstu, więc bez pustych stron i samego „Wkrótce.”.
     */
    val gotowa: Boolean =
        if (j.ma("ready") || j.ma("app")) (!j.ma("ready") || j.b("ready")) && (!j.ma("app") || j.b("app"))
        else Html.tekst(html).length >= 40
}

/** Ogłoszenie drobne zatwierdzone w panelu (/api/v1/classifieds). */
class Ogloszenie(j: JSONObject) {
    val id: Int = j.i("id")
    val tytul: String = Html.tekst(j.s("title"))
    val kategoria: String = Html.tekst(j.optJSONObject("category")?.s("name") ?: j.s("category"))
    val cena: String = j.s("price").let { c ->
        if (c.isEmpty()) "" else if (c.toDoubleOrNull() != null) c.replace('.', ',') + " zł" else c
    }
    val tekst: String = Html.tekst(j.s("text"), zachowajAkapity = true)
    val kontakt: String = j.optJSONObject("contact")?.let { k ->
        listOf("name", "phone", "email").map { k.s(it) }.filter { it.isNotEmpty() }.joinToString("\n")
    } ?: j.s("contact")
    val zdjecia: List<String> = j.lista("photos").mapNotNull { o ->
        val w = o.o("variants")
        w.s("800").ifEmpty { w.s("400") }.ifEmpty { o.s("url") }.ifEmpty { null }
    }
    val data: ZonedDateTime? = Daty.iso(j.s("publishedAt"))
}

/**
 * Wydarzenie z kalendarza tvml.pl (/api/v1/events i spotlight; szczegóły z /api/v1/events/{id}, API 1.7).
 * Tylko zatwierdzone, od najbliższego, także trwające.
 */
class Wydarzenie(j: JSONObject) {
    val id: Int = j.i("id")
    val tytul: String = Html.tekst(j.s("title"))
    val poczatek: ZonedDateTime? = Daty.iso(j.s("startsAt"))
    val koniec: ZonedDateTime? = Daty.iso(j.s("endsAt"))
    val calyDzien: Boolean = j.b("allDay")
    val miejsce: String = Html.tekst(j.s("place").ifEmpty { j.s("location") })
    /** Kanoniczny adres to /wydarzenia?id=N (wersja .html to tylko przekierowanie). */
    val adres: String = j.s("url").replace("wydarzenia.html", "wydarzenia").ifEmpty { "${Siec.BAZA}wydarzenia?id=$id" }
    val artykulId: Int? = j.i("articleId").takeIf { it > 0 }
    /** Miniatura jak na stronie: wariant co najmniej 200 px, a bez wariantów oryginał przez img-webp-v2.php. */
    val miniatura: String?
    val obraz: String?
    val zrodloZdjecia: String
    /** Dzień rozpoczęcia, np. „2026-11-23” (do listy dnia w kalendarzu bez sieci). */
    val dzienISO: String = j.s("date").ifEmpty { poczatek?.toLocalDate()?.toString().orEmpty() }
    val lead: String = Html.tekst(j.s("lead"))
    /** Pełny opis (czysty tekst z akapitami) tylko z /api/v1/events/{id}. */
    val opis: String = j.s("description").trim()
    val organizator: String = Html.tekst(j.s("organizer"))
    val bilety: String = Html.tekst(j.s("tickets"))
    val zrodlo: String = j.s("sourceUrl")

    init {
        val o = j.optJSONObject("image")
        if (o != null) {
            val w = mutableMapOf<Int, String>()
            o.optJSONObject("variants")?.let { v -> v.keys().forEach { k -> k.toIntOrNull()?.let { n -> v.optString(k).takeIf { it.isNotEmpty() }?.let { w[n] = it } } } }
            val oryginal = o.s("url")
            miniatura = (w.keys.sorted().firstOrNull { it >= 200 } ?: w.keys.maxOrNull())?.let { w[it] } ?: Siec.obraz(oryginal, 300)
            obraz = oryginal.ifEmpty { null }?.let { Siec.obraz(it, 1200) }
            zrodloZdjecia = Html.tekst(o.s("source"))
        } else {
            val oryginal = j.s("image")
            miniatura = Siec.obraz(oryginal, 300)
            obraz = Siec.obraz(oryginal, 1200)
            zrodloZdjecia = Html.tekst(j.s("imageCredit"))
        }
    }

    /** „trwa”, „cały dzień” albo godzina rozpoczęcia, np. „18:00”. */
    val godzina: String
        get() {
            val teraz = ZonedDateTime.now(Daty.strefa)
            val p = poczatek ?: return ""
            if (p.isBefore(teraz) && (koniec ?: p).isAfter(teraz)) return tl("app.eventNow", "trwa")
            if (calyDzien) return tl("app.eventAllDay", "cały dzień")
            return Daty.godzina(p)
        }
    val dzien: String get() = poczatek?.dayOfMonth?.toString().orEmpty()
    val miesiac: String get() = poczatek?.let { Daty.skrotMiesiaca(it.toLocalDate()).uppercase() }.orEmpty()
    val dzienTygodnia: String get() = poczatek?.let { Daty.skrotDnia(it.toLocalDate()) }.orEmpty()

    /** Koniec do pokazania: całodniowe kończą się często o północy następnego dnia, wtedy ostatni dzień to dzień wcześniej. */
    val koniecDnia: ZonedDateTime?
        get() = koniec?.let { k -> if (calyDzien && k.hour == 0 && k.minute == 0 && k.second == 0) k.minusSeconds(1) else k }

    /** Wydarzenie na kilka dni (np. wystawa): koniec innego dnia niż początek. */
    val wielodniowe: Boolean
        get() {
            val p = poczatek ?: return false
            val k = koniecDnia ?: return false
            return k.toLocalDate() != p.toLocalDate()
        }

    /** Wielodniowe, które w dniu [dzien] już trwa: zaczęło się wcześniej i jeszcze się nie skończyło. */
    fun trwaW(dzien: java.time.LocalDate): Boolean {
        if (!wielodniowe) return false
        val p = poczatek ?: return false
        val k = koniecDnia ?: return false
        val start = dzien.atStartOfDay(Daty.strefa)
        return p.isBefore(start) && !k.isBefore(start)
    }

    /** „20 lis – 5 gru” (wielodniowe). */
    val zakres: String
        get() {
            val p = poczatek ?: return ""
            val k = koniecDnia ?: return ""
            fun krotka(d: ZonedDateTime) = "${d.dayOfMonth} ${Daty.skrotMiesiaca(d.toLocalDate())}"
            return krotka(p) + " – " + krotka(k)
        }

    /**
     * „sobota, 28 listopada 2026 · 18:00–20:00” albo „… · cały dzień”; wielodniowe (jak na iPhonie):
     * „piątek, 20 listopada 2026 – sobota, 5 grudnia 2026”, z godzinami, gdy nie są całodniowe.
     */
    val dataPelna: String
        get() {
            val p = poczatek ?: return ""
            if (wielodniowe) {
                val k = koniecDnia ?: return ""
                val od = Daty.dzisiaj(p.toLocalDate()) + if (calyDzien) "" else " " + Daty.godzina(p)
                val doKonca = Daty.dzisiaj(k.toLocalDate()) + if (calyDzien) "" else " " + Daty.godzina(k)
                return "$od – $doKonca"
            }
            var t = Daty.dzisiaj(p.toLocalDate())
            if (calyDzien) {
                val k = koniec
                return if (k != null && k.toLocalDate() != p.toLocalDate()) "$t – ${Daty.dzisiaj(k.toLocalDate())}" else "$t · ${tl("app.eventAllDay", "cały dzień")}"
            }
            t += " · " + Daty.godzina(p)
            koniec?.let { k -> t += if (k.toLocalDate() == p.toLocalDate()) "–" + Daty.godzina(k) else " – " + Daty.dzisiaj(k.toLocalDate()) }
            return t
        }

    // bez equals po numerze, jak w Artykul: nowa wersja wydarzenia (inny język) ma odświeżać ekran
}

/** Sonda z /api/v1/spotlight albo /api/v1/polls/{id}: pytanie, odpowiedzi i wyniki (pokazywane dopiero po głosie). */
class Sonda(j: JSONObject) {
    class Opcja(val klucz: String, val etykieta: String, val glosy: Int, val procent: Int)

    val id: String = j.s("id")
    val pytanie: String = Html.tekst(j.s("question"))
    val otwarta: Boolean = j.b("open")
    val doKiedy: ZonedDateTime? = Daty.iso(j.s("showTo"))
    val razem: Int = j.i("totalVotes")
    val opcje: List<Opcja> = j.lista("options").map { Opcja(it.s("key"), Html.tekst(it.s("label")), it.i("votes"), it.i("pct")) }

    companion object {
        /** 1 głos, 2–4 głosy (bez 12–14), 5+ głosów; po ukraińsku i angielsku app.votes.one/few/many/other. */
        fun glosyTekst(n: Int): String = Jezyk.liczba("app.votes", n.toLong(), Triple("głos", "głosy", "głosów"))

        /** „30 listopada, 20:00”, z rokiem tylko gdy inny niż bieżący. */
        fun doKiedyTekst(d: ZonedDateTime): String {
            val rok = if (d.year != LocalDate.now(Daty.strefa).year) " ${d.year}" else ""
            return "${Daty.dzienMiesiaca(d.toLocalDate())}$rok, ${Daty.godzina(d)}"
        }
    }
}

/** Kalendarz wydarzeń w miejscu sondy (spotlight type „events”): miesiąc, dni z wydarzeniami i najbliższe wydarzenia. */
class KalendarzSpot(j: JSONObject) {
    val miesiac: String = j.s("month")
    val dzisiaj: String = j.s("today").ifEmpty { LocalDate.now(Daty.strefa).toString() }
    val dni: Set<String> = j.teksty("days").toSet()
    val wydarzenia: List<Wydarzenie> = j.lista("items").map(::Wydarzenie)
    val adres: String = j.s("url").replace("wydarzenia.html", "wydarzenia").ifEmpty { "${Siec.BAZA}wydarzenia" }
}

/** Blok „poll” na Start: trwająca sonda albo, gdy jej nie ma, kalendarz wydarzeń (decyduje serwer, pole type). */
sealed interface Spotlight {
    class ZSonda(val sonda: Sonda) : Spotlight
    class ZKalendarzem(val kalendarz: KalendarzSpot) : Spotlight
}

/** Treść artykułu (HTML z edytora) jako bloki do wyświetlenia natywnie: akapity, śródtytuły, obrazy i filmy. */
object Html {
    sealed interface Blok {
        data class Akapit(val tekst: String) : Blok
        data class Srodtytul(val tekst: String) : Blok
        data class Obraz(val adres: String) : Blok
        data class Film(val id: String) : Blok
    }

    private val wzor = Regex(
        """<img[^>]*src="([^"]+)"[^>]*>|<iframe[^>]*src="[^"]*(?:youtube(?:-nocookie)?\.com/embed/|youtu\.be/)([A-Za-z0-9_-]{6,})[^"]*"[^>]*>(?:</iframe>)?|<figure[^>]*data-youtube="([A-Za-z0-9_-]{6,})"[^>]*>|<h[2-4][^>]*>(.*?)</h[2-4]>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun bloki(html: String): List<Blok> {
        val wynik = mutableListOf<Blok>()
        var od = 0
        fun tekstDo(koniec: Int) {
            if (koniec > od) akapity(html.substring(od, koniec)).forEach { wynik += Blok.Akapit(it) }
        }
        for (m in wzor.findAll(html)) {
            tekstDo(m.range.first)
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> wynik += Blok.Obraz(if (g[1].startsWith("http")) g[1] else Siec.BAZA + g[1].trimStart('/'))
                g[2].isNotEmpty() -> wynik += Blok.Film(g[2])
                g[3].isNotEmpty() -> wynik += Blok.Film(g[3])
                g[4].isNotEmpty() -> tekst(g[4]).takeIf { it.isNotEmpty() }?.let { wynik += Blok.Srodtytul(it) }
            }
            od = m.range.last + 1
        }
        tekstDo(html.length)
        return wynik
    }

    fun akapity(html: String): List<String> {
        var t = html.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        t = t.replace(Regex("""</(p|div|li|blockquote|ul|ol|figure)>""", RegexOption.IGNORE_CASE), "\n\n")
        t = t.replace(Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE), "• ")
        return tekst(t, zachowajAkapity = true).split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private val encje = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&lt;" to "<", "&gt;" to ">",
        "&bdquo;" to "„", "&rdquo;" to "”", "&ldquo;" to "“", "&ndash;" to "–", "&mdash;" to "—", "&hellip;" to "…", "&oacute;" to "ó",
    )

    /** Tekst bez znaczników, z rozkodowanymi encjami. */
    fun tekst(html: String, zachowajAkapity: Boolean = false): String {
        if (html.isEmpty()) return ""
        var t = html.replace(Regex("""<script.*?</script>|<style.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        t = t.replace(Regex("<[^>]+>"), "")
        for ((k, v) in encje) t = t.replace(k, v)
        t = t.replace(Regex("&#(x?)([0-9a-fA-F]+);")) { m ->
            val kod = m.groupValues[2].toIntOrNull(if (m.groupValues[1] == "x") 16 else 10)
            kod?.let { String(Character.toChars(it)) } ?: m.value
        }
        if (!zachowajAkapity) t = t.replace(Regex("""\s+"""), " ")
        else t = t.replace(Regex("""[ \t ]+"""), " ").replace(Regex(""" *\n *"""), "\n").replace(Regex("""\n{3,}"""), "\n\n")
        return t.trim()
    }
}

/**
 * Daty w języku aplikacji (Jezyk.biezacy). Po polsku własne nazwy, jak na stronie; po ukraińsku i angielsku
 * systemowe formatery w locale uk-UA i en-GB (odmiany miesięcy i „5 хвилин тому” daje ICU).
 */
object Daty {
    val strefa: ZoneId = ZoneId.of("Europe/Warsaw")
    private val stary = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX")
    private val miesiace = listOf("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")
    private val dniTygodnia = listOf("poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela")

    private val polski: Boolean get() = Jezyk.biezacy == "pl"
    private fun wzor(w: String): DateTimeFormatter = DateTimeFormatter.ofPattern(w, Jezyk.locale)
    private fun wielka(s: String): String = s.replaceFirstChar { it.titlecase(Jezyk.locale) }

    fun iso(s: String): ZonedDateTime? = runCatching { OffsetDateTime.parse(s).atZoneSameInstant(strefa) }.getOrNull()
    fun stara(s: String): ZonedDateTime? = runCatching { ZonedDateTime.parse(s, stary).withZoneSameInstant(strefa) }.getOrNull()

    /** „18 listopada 2026”, „18 листопада 2026 р.”, „18 November 2026” */
    fun dzien(d: ZonedDateTime): String =
        if (polski) "${d.dayOfMonth} ${miesiace[d.monthValue - 1]} ${d.year}" else wzor("d MMMM yyyy").format(d)

    /** „czwartek, 19 listopada 2026” */
    fun dzisiaj(d: LocalDate = LocalDate.now(strefa)): String =
        if (polski) "${dniTygodnia[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${miesiace[d.monthValue - 1]} ${d.year}"
        else wzor("EEEE, d MMMM yyyy").format(d)

    private val miesiaceMianownik = listOf("styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec", "lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień")
    private val skrotyMiesiecy = listOf("sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru")
    private val skrotyDni = listOf("pon.", "wt.", "śr.", "czw.", "pt.", "sob.", "niedz.")
    private val skrotyDniKalendarza = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")

    /** „23.06.2026”, po angielsku „23/06/2026” */
    fun krotka(d: ZonedDateTime): String =
        String.format(Locale.ROOT, if (Jezyk.biezacy == "en") "%02d/%02d/%d" else "%02d.%02d.%d", d.dayOfMonth, d.monthValue, d.year)

    /** „18:05” (24 godziny także po angielsku, jak en-GB) */
    fun godzina(d: ZonedDateTime): String = String.format(Locale.ROOT, "%02d:%02d", d.hour, d.minute)

    /** „30 listopada” */
    fun dzienMiesiaca(d: LocalDate): String =
        if (polski) "${d.dayOfMonth} ${miesiace[d.monthValue - 1]}" else wzor("d MMMM").format(d)

    /** „Niedziela, 22 listopada” */
    fun dzienTygodniaIData(d: LocalDate): String =
        if (polski) "${dniTygodnia[d.dayOfWeek.value - 1]}, ${dzienMiesiaca(d)}".replaceFirstChar { it.uppercase() }
        else wielka(wzor("EEEE, d MMMM").format(d))

    /** „Listopad 2026” */
    fun miesiacIRok(d: LocalDate): String =
        if (polski) "${miesiaceMianownik[d.monthValue - 1].replaceFirstChar { it.uppercase() }} ${d.year}"
        else wielka(wzor("LLLL yyyy").format(d))

    /** „lis” */
    fun skrotMiesiaca(d: LocalDate): String = if (polski) skrotyMiesiecy[d.monthValue - 1] else wzor("MMM").format(d).trimEnd('.')

    /** „pon.” */
    fun skrotDnia(d: LocalDate): String = if (polski) skrotyDni[d.dayOfWeek.value - 1] else wzor("EEE").format(d)

    /** Nagłówek kalendarza od poniedziałku: „Pn Wt Śr…”, „Пн Вт Ср…”, „Mo Tu We…”. */
    fun dniKalendarza(): List<String> =
        if (polski) skrotyDniKalendarza
        else java.time.DayOfWeek.entries.map { d ->
            d.getDisplayName(TextStyle.SHORT_STANDALONE, Jezyk.locale).trimEnd('.').let { if (Jezyk.biezacy == "en") it.take(2) else wielka(it) }
        }

    fun odmiana(n: Long, jeden: String, kilka: String, wiele: String): String {
        val forma = when {
            n == 1L -> jeden
            n % 10 in 2..4 && n % 100 !in 12..14 -> kilka
            else -> wiele
        }
        return "$n $forma"
    }

    /** „5 minut temu”, „1 dzień temu”, „3 tygodnie temu” (jak RelativeDateTimeFormatter na iPhonie). */
    fun temu(d: ZonedDateTime, teraz: ZonedDateTime = ZonedDateTime.now(strefa)): String {
        val sekundy = ChronoUnit.SECONDS.between(d, teraz)
        if (sekundy < 60) return tl("app.justNow", "przed chwilą")
        val minuty = sekundy / 60
        val godziny = minuty / 60
        val dni = godziny / 24
        val miesiace = ChronoUnit.MONTHS.between(d, teraz)
        if (!polski) {
            val f = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(Jezyk.locale))
            val (ile, jednostka) = when {
                minuty < 60 -> minuty to RelativeDateTimeFormatter.RelativeUnit.MINUTES
                godziny < 24 -> godziny to RelativeDateTimeFormatter.RelativeUnit.HOURS
                dni < 7 -> dni to RelativeDateTimeFormatter.RelativeUnit.DAYS
                dni < 31 -> dni / 7 to RelativeDateTimeFormatter.RelativeUnit.WEEKS
                miesiace < 12 -> maxOf(1, miesiace) to RelativeDateTimeFormatter.RelativeUnit.MONTHS
                else -> maxOf(1, miesiace / 12) to RelativeDateTimeFormatter.RelativeUnit.YEARS
            }
            return f.format(ile.toDouble(), RelativeDateTimeFormatter.Direction.LAST, jednostka)
        }
        if (minuty < 60) return odmiana(minuty, "minutę", "minuty", "minut") + " temu"
        if (godziny < 24) return odmiana(godziny, "godzinę", "godziny", "godzin") + " temu"
        if (dni < 7) return odmiana(dni, "dzień", "dni", "dni") + " temu"
        if (dni < 31) return odmiana(dni / 7, "tydzień", "tygodnie", "tygodni") + " temu"
        if (miesiace < 12) return odmiana(maxOf(1, miesiace), "miesiąc", "miesiące", "miesięcy") + " temu"
        return odmiana(maxOf(1, miesiace / 12), "rok", "lata", "lat") + " temu"
    }
}
