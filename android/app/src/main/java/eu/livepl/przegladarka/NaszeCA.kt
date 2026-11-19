package eu.livepl.przegladarka

import android.content.Context
import org.json.JSONObject

/**
 * Nasze domeny z assets/ca/domeny.json (ten sam plik co na Windows i iPhonie). Zaufanie do urzędu LivePL na tych
 * domenach ustawia res/xml/siec.xml; tutaj LivePL decyduje, komu pokazać certyfikat „Urządzenie LivePL”.
 */
object NaszeCA {
    @Volatile
    private var domeny: List<String>? = null

    fun naszaDomena(kontekst: Context, host: String?): Boolean {
        val h = host?.lowercase()?.trimEnd('.') ?: return false
        return wczytaj(kontekst).any { h == it || h.endsWith(".$it") }
    }

    private fun wczytaj(kontekst: Context): List<String> = domeny ?: try {
        val tekst = kontekst.assets.open("ca/domeny.json").bufferedReader().use { it.readText() }
        val lista = JSONObject(tekst).getJSONArray("domeny")
        (0 until lista.length()).map { lista.getString(it).trim().lowercase() }.filter { it.isNotEmpty() }
    } catch (_: Exception) {
        emptyList()
    }.also { domeny = it }
}
