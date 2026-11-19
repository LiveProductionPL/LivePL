package eu.livepl.przegladarka

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Program z paska LivePL. Lista pochodzi z tego samego pliku programy.json co w wersji na Windows. */
data class Program(
    val id: String,
    val nazwa: String,
    val skrot: String?,
    val adres: String,
    val ikona: String?,
    val opis: String?,
    val ukryty: Boolean,
    val szeroki: Boolean,
    val tylkoZYubiKey: Boolean,
) {
    val krotkaNazwa: String get() = skrot ?: nazwa

    /** Panel urzędu loguje się kluczem YubiKey przez Windows, więc na telefonie otwieramy stronę publiczną. */
    val adresNaTelefonie: String
        get() {
            if (tylkoZYubiKey) {
                Uri.parse(adres).host?.let { return "https://$it/" }
            }
            return adres
        }

    /** Własna grafika programu (jak „obrazek” w programy.json); dzień i noc wybiera Android. */
    @get:DrawableRes
    val obrazek: Int?
        get() = when (id) {
            "poczta" -> R.drawable.program_poczta
            "liveproduction" -> R.drawable.program_liveproduction
            "tvml" -> R.drawable.program_tvml
            else -> null
        }

    /** Ikona odpowiadająca ikonie z ikony.js w wersji na Windows. */
    @get:DrawableRes
    val symbol: Int
        get() = when (ikona) {
            "poczta" -> R.drawable.ic_poczta
            "klient" -> R.drawable.ic_klient
            "lekcje" -> R.drawable.ic_lekcje
            "egzamin" -> R.drawable.ic_egzamin
            "serwer" -> R.drawable.ic_serwer
            "tarcza" -> R.drawable.ic_tarcza
            else -> R.drawable.ic_strona
        }

    companion object {
        fun wczytaj(kontekst: Context): List<Program> = try {
            val tekst = kontekst.assets.open("programy.json").bufferedReader().use { it.readText() }
            val tablica = JSONArray(tekst)
            (0 until tablica.length()).map { i ->
                val o = tablica.getJSONObject(i)
                Program(
                    id = o.getString("id"),
                    nazwa = o.getString("nazwa"),
                    skrot = o.tekst("skrot"),
                    adres = o.getString("adres"),
                    ikona = o.tekst("ikona"),
                    opis = o.tekst("opis"),
                    ukryty = o.optBoolean("ukryty", false),
                    szeroki = o.optBoolean("szeroki", false),
                    tylkoZYubiKey = o.optBoolean("tylkoZYubiKey", false),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

fun JSONObject.tekst(klucz: String): String? =
    if (has(klucz) && !isNull(klucz)) getString(klucz).takeIf { it.isNotEmpty() } else null

/** Strona dopisana przez użytkownika; lista zostaje tylko w tym telefonie. */
data class MojaStrona(
    val adres: String,
    val nazwa: String,
    val id: String = UUID.randomUUID().toString(),
) {
    val wyswietlanaNazwa: String get() = nazwa.ifEmpty { Adres.host(adres) }
}

object Adres {
    private val wzorAdresu = Regex("""^[^/\s]+\.[A-Za-z]{2,}(:\d+)?(/\S*)?$""")

    /** Jak w LivePL: pełny adres, adres bez https:// albo wyszukiwanie w Google. */
    fun zTekstu(tekst: String): String? {
        val t = tekst.trim()
        if (t.isEmpty()) return null
        val maly = t.lowercase()
        if (listOf("http://", "https://", "mailto:", "tel:", "about:").any { maly.startsWith(it) }) return t
        adresStrony(t)?.let { return it }
        return "https://www.google.com/search?q=" + Uri.encode(t)
    }

    /** „livepl.eu” → „https://livepl.eu”; null, gdy to nie wygląda na adres strony. */
    fun adresStrony(tekst: String): String? {
        val t = tekst.trim()
        val maly = t.lowercase()
        if (maly.startsWith("http://") || maly.startsWith("https://")) {
            return if (Uri.parse(t).host.isNullOrEmpty()) null else t
        }
        if (t.contains(' ') || !wzorAdresu.matches(t)) return null
        return "https://$t"
    }

    fun host(adres: String?): String {
        if (adres.isNullOrEmpty()) return ""
        val uri = Uri.parse(adres)
        var host = uri.host ?: return adres
        if (host.startsWith("www.")) host = host.removePrefix("www.")
        if (uri.port != -1) host += ":${uri.port}"
        return host
    }

    fun taSamaStrona(a: String, b: String): Boolean =
        a.lowercase().trimEnd('/') == b.lowercase().trimEnd('/')
}
