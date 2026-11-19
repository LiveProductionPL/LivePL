package pl.tvml

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Artykuł z API v1 (image i category jako obiekty) albo ze starych akcji tvml-db.php. */
class Artykul(j: JSONObject) {
    val id: Int = if (j.ma("id")) j.i("id") else j.i("articleId")
    val tytul: String = Html.tekst(j.s("title"))
    val lead: String = Html.tekst(if (j.ma("lead")) j.s("lead") else j.s("excerpt"))
    val obraz: String
    val zrodloZdjecia: String
    /** Gotowe rozmiary obrazu z API v1 (400, 800, 1200). */
    val warianty: Map<Int, String>
    val kategoria: String
    val kolor: String
    val autor: String
    val data: ZonedDateTime?
    val komentarze: Int = if (j.ma("commentsCount")) j.i("commentsCount") else j.i("comments")
    val odslony: Int = j.i("views")
    val tagi: List<String>
    val tresc: String?
    val pilne: Boolean = j.o("flags").b("urgent")
    val link: String

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
            kategoria = Html.tekst(k.s("name")); kolor = k.s("color")
        } else {
            kategoria = Html.tekst(j.s("category")); kolor = j.s("categoryColor")
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

    override fun equals(other: Any?) = other is Artykul && other.id == id
    override fun hashCode() = id
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
        fun stopnie(v: Double?): String = v?.let { String.format(Locale.US, "%.1f°", it).replace('.', ',') } ?: "–"
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
        val wiecej = ZakladkaApp("more", "Więcej", "more", Cel.Wiecej)
        val domyslne = listOf(
            ZakladkaApp("home", "Start", "home", Cel.Start),
            ZakladkaApp("sport", "Sport", "sport", Cel.Kategoria("sport")),
            ZakladkaApp("video", "Wideo", "video", Cel.Wideo),
            ZakladkaApp("weather", "Pogoda", "weather", Cel.Pogoda),
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

/** Blok ekranu Start z panelu (Page builder, wersja na telefon), w kolejności z config.home. */
data class BlokStartu(val id: String, val typ: String, val tytul: String, val ukryjTytul: Boolean, val limit: Int, val kategoria: String?) {
    companion object {
        val znane = setOf("hero", "weather", "alert", "video", "posts", "mostread", "gallery", "counties", "social")
        val domyslne = listOf(
            BlokStartu("hero", "hero", "", true, 5, null),
            BlokStartu("weather", "weather", "Pogoda", true, 0, null),
            BlokStartu("posts", "posts", "Aktualności", false, 10, null),
            BlokStartu("mostread", "mostread", "Najczęściej czytane", false, 5, null),
            BlokStartu("video", "video", "Wideo", false, 6, null),
        )

        /** Typy nieznane aplikacji (sondy, ogłoszenia, newsletter…) pomija, jak na iPhonie. */
        fun z(j: JSONObject, nr: Int): BlokStartu? {
            val typ = j.s("type")
            if (typ !in znane) return null
            val p = j.o("props")
            return BlokStartu(j.s("id").ifEmpty { "$typ-$nr" }, typ, Html.tekst(j.s("title")), p.b("hideTitle"), p.i("limit"), p.s("category").ifEmpty { null })
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

data class Dzisiaj(val etykieta: String, val imieniny: List<String>)

data class StronaRedakcji(val slug: String, val tytul: String)

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

/** Daty po polsku, niezależnie od języka telefonu (aplikacja jest tylko po polsku). */
object Daty {
    val strefa: ZoneId = ZoneId.of("Europe/Warsaw")
    private val stary = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX")
    private val miesiace = listOf("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")
    private val dniTygodnia = listOf("poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela")

    fun iso(s: String): ZonedDateTime? = runCatching { OffsetDateTime.parse(s).atZoneSameInstant(strefa) }.getOrNull()
    fun stara(s: String): ZonedDateTime? = runCatching { ZonedDateTime.parse(s, stary).withZoneSameInstant(strefa) }.getOrNull()

    /** „18 listopada 2026” */
    fun dzien(d: ZonedDateTime): String = "${d.dayOfMonth} ${miesiace[d.monthValue - 1]} ${d.year}"

    /** „czwartek, 19 listopada 2026” */
    fun dzisiaj(d: LocalDate = LocalDate.now(strefa)): String = "${dniTygodnia[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${miesiace[d.monthValue - 1]} ${d.year}"

    private fun odmiana(n: Long, jeden: String, kilka: String, wiele: String): String {
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
        if (sekundy < 60) return "przed chwilą"
        val minuty = sekundy / 60
        if (minuty < 60) return odmiana(minuty, "minutę", "minuty", "minut") + " temu"
        val godziny = minuty / 60
        if (godziny < 24) return odmiana(godziny, "godzinę", "godziny", "godzin") + " temu"
        val dni = godziny / 24
        if (dni < 7) return odmiana(dni, "dzień", "dni", "dni") + " temu"
        if (dni < 31) return odmiana(dni / 7, "tydzień", "tygodnie", "tygodni") + " temu"
        val miesiace = ChronoUnit.MONTHS.between(d, teraz)
        if (miesiace < 12) return odmiana(maxOf(1, miesiace), "miesiąc", "miesiące", "miesięcy") + " temu"
        return odmiana(maxOf(1, miesiace / 12), "rok", "lata", "lat") + " temu"
    }
}
