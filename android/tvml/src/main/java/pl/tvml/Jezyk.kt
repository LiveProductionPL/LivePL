package pl.tvml

import android.content.Context
import android.content.SharedPreferences
import android.icu.text.PluralRules
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONObject
import java.util.Locale

/**
 * Język aplikacji (API 1.8.0): polski, ukraiński albo angielski. Treść tłumaczy redakcja na serwerze (?lang= w każdym
 * zapytaniu), a napisy na ekranach są w słownikach config.i18n, tych samych co na stronie. Aplikacja nie ma własnych
 * tłumaczeń: po polsku i przy brakującym kluczu zostaje wbudowany tekst polski. Tak samo jak TVML na iPhonie.
 */
object Jezyk {
    const val SYSTEM = "system"
    val znane = listOf("pl", "uk", "en")

    /** Nazwy języków w nich samych, jak w wyborze na stronie. */
    val nazwy = mapOf("pl" to "Polski", "uk" to "Українська", "en" to "English")

    private var ustawienia: SharedPreferences? = null

    /** Wybór czytelnika: [SYSTEM] („Jak w systemie”), pl, uk albo en. */
    var wybor by mutableStateOf(SYSTEM)
        private set

    /** Języki z config.languages (redakcja może wyłączyć UK albo EN). Pusta lista = serwer bez języków, wtedy bez wyboru. */
    var dostepne by mutableStateOf<List<String>>(emptyList())
        private set

    /** Język zapytań i napisów w tej chwili. */
    var biezacy by mutableStateOf("pl")
        private set

    private var slowniki by mutableStateOf<Map<String, Map<String, String>>>(emptyMap())

    fun przygotuj(kontekst: Context) {
        val u = kontekst.getSharedPreferences("tvml", Context.MODE_PRIVATE)
        ustawienia = u
        wybor = u.getString("jezyk", SYSTEM)?.takeIf { it == SYSTEM || it in znane } ?: SYSTEM
        dostepne = u.getString("jezyki", "").orEmpty().split(',').filter { it in znane }
        // ostatni słownik z serwera, żeby bez sieci napisy też były w wybranym języku
        slowniki = runCatching { czytajSlowniki(JSONObject(u.getString("i18n", "{}") ?: "{}")) }.getOrDefault(emptyMap())
        biezacy = wylicz()
    }

    /**
     * Język telefonu: ukraiński albo angielski, a każdy inny (rosyjski, niemiecki…) to polski. LocaleList.getDefault
     * uwzględnia też język ustawiony dla samej aplikacji (Android 13+, Ustawienia → Aplikacje → Język).
     */
    fun systemowy(): String {
        val l = runCatching { LocaleList.getDefault()[0].language }.getOrNull() ?: Locale.getDefault().language
        return if (l == "uk" || l == "en") l else "pl"
    }

    private fun wylicz(): String {
        val j = if (wybor == SYSTEM) systemowy() else wybor
        // język wyłączony w panelu: po polsku (tak samo odpowiada serwer)
        return if (j == "pl" || dostepne.isEmpty() || j in dostepne) j else "pl"
    }

    /** Ustawia wybór; true, jeśli zmienił się język treści (wtedy trzeba przeładować ekrany). */
    fun ustaw(w: String): Boolean {
        wybor = if (w == SYSTEM || w in znane) w else SYSTEM
        ustawienia?.edit { putString("jezyk", wybor) }
        return przelicz()
    }

    /** Po zmianie języka telefonu albo listy języków z serwera; true przy zmianie języka treści. */
    fun przelicz(): Boolean {
        val nowy = wylicz()
        if (nowy == biezacy) return false
        biezacy = nowy
        return true
    }

    /** config.languages i config.i18n; true, jeśli przez to zmienił się język treści. */
    fun zKonfiguracji(konf: JSONObject): Boolean {
        val l = konf.teksty("languages").filter { it in znane }
        if (l.isNotEmpty()) {
            dostepne = l
            ustawienia?.edit { putString("jezyki", l.joinToString(",")) }
        }
        konf.optJSONObject("i18n")?.let { j ->
            val s = czytajSlowniki(j)
            if (s.isNotEmpty() && s != slowniki) {
                slowniki = s
                ustawienia?.edit { putString("i18n", j.toString()) }
            }
        }
        return przelicz()
    }

    private fun czytajSlowniki(j: JSONObject): Map<String, Map<String, String>> =
        znane.mapNotNull { l ->
            val o = j.optJSONObject(l) ?: return@mapNotNull null
            l to o.keys().asSequence().mapNotNull { k -> o.optString(k).takeIf { it.isNotEmpty() }?.let { k to it } }.toMap()
        }.toMap()

    /**
     * Napis z config.i18n: po ukraińsku i angielsku tekst z serwera, a bez klucza (i po polsku) tekst wbudowany [pl],
     * taki jak dotąd. Tak samo jak Napisy.t w TVML na iPhonie, więc polskie ekrany się nie zmieniają.
     */
    fun tekst(klucz: String, pl: String): String = if (biezacy == "pl") pl else slowniki[biezacy]?.get(klucz) ?: pl

    /** Locale do dat i liczb: pl-PL, uk-UA albo en-GB. */
    val locale: Locale
        get() = when (biezacy) {
            "uk" -> Locale.forLanguageTag("uk-UA")
            "en" -> Locale.forLanguageTag("en-GB")
            else -> Locale.forLanguageTag("pl-PL")
        }

    /** Nagłówek Accept-Language zgodny z wyborem (serwer patrzy na niego tylko przy lang=auto). */
    val naglowek: String
        get() = when (biezacy) {
            "uk" -> "uk-UA,uk;q=0.9,pl;q=0.5"
            "en" -> "en-GB,en;q=0.9,pl;q=0.5"
            else -> "pl-PL,pl;q=0.9"
        }

    /**
     * Liczba z odmianą, jak na iPhonie: po ukraińsku i angielsku klucz.one / .few / .many / .other według reguł języka
     * (CLDR), a bez nich klucz.other; po polsku i bez kluczy polska odmiana z [pl] (jeden, kilka, wiele). {n} = liczba.
     */
    fun liczba(klucz: String, n: Long, pl: Triple<String, String, String>): String {
        val s = slowniki[biezacy]?.takeIf { biezacy != "pl" }
        if (s != null) {
            val forma = runCatching { PluralRules.forLocale(locale).select(n.toDouble()) }.getOrDefault("other")
            (s["$klucz.$forma"] ?: s["$klucz.other"])?.let { return it.replace("{n}", n.toString()) }
        }
        return Daty.odmiana(n, pl.first, pl.second, pl.third)
    }
}

/** Krótko: napis z config.i18n (klucz app.*) z polskim tekstem na zapas. */
fun tl(klucz: String, pl: String): String = Jezyk.tekst(klucz, pl)

/** Napis z {n}, np. tl("app.readingMinutes", "czytanie ok. {n} min", 3). */
fun tl(klucz: String, pl: String, n: Number): String = Jezyk.tekst(klucz, pl).replace("{n}", n.toString())
