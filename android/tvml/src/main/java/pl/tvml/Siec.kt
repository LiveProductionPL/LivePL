package pl.tvml

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Dane z tvml.pl. Aplikacja czyta API dla aplikacji (/api/v1, robi je TVML Connect), a gdy go nie ma,
 * te same akcje co strona: tvml-db.php (artykuły) i tvml-portal.php (pogoda, wideo). Tak samo jak TVML na iPhonie.
 */
object Siec {
    const val BAZA = "https://tvml.pl/"

    /** Własny agent: tvml.pl liczy odsłony aplikacji osobno i nie odrzuca jej jako robota (wyjątek „TVMLApp”). */
    val agent: String = "TVMLApp/${BuildConfig.VERSION_NAME.substringBefore('-')} (Android ${Build.VERSION.RELEASE})"

    /** Ładny adres /api/v1/ albo zapasowy tvml-app.php?route=. Aplikacja próbuje po kolei i zapamiętuje działający. */
    private val bazyApi = listOf("api/v1/", "tvml-app.php?route=")
    @Volatile private var dzialajacaBaza: String? = null

    /** Ostatnie odpowiedzi z ETag: odświeżenie bez zmian to 304, a bez internetu aplikacja pokazuje ostatnie dane. */
    private var schowek: File? = null

    fun przygotuj(kontekst: Context) {
        schowek = File(kontekst.cacheDir, "api").apply { mkdirs() }
    }

    private fun plik(adres: String): File? {
        val katalog = schowek ?: return null
        val skrot = MessageDigest.getInstance("SHA-1").digest(adres.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(katalog, skrot)
    }

    /** JSON (obiekt albo tablica) z adresu względnego wobec tvml.pl. */
    suspend fun json(sciezka: String): Any = withContext(Dispatchers.IO) {
        val adres = if (sciezka.startsWith("http")) sciezka else BAZA + sciezka
        val zapis = plik(adres)
        val etagPlik = zapis?.let { File(it.path + ".etag") }
        val polaczenie = URL(adres).openConnection() as HttpURLConnection
        try {
            polaczenie.connectTimeout = 15000
            polaczenie.readTimeout = 20000
            polaczenie.setRequestProperty("User-Agent", agent)
            polaczenie.setRequestProperty("Referer", BAZA)
            polaczenie.setRequestProperty("Accept-Language", Jezyk.naglowek)
            polaczenie.setRequestProperty("Accept", "application/json")
            if (zapis?.exists() == true && etagPlik?.exists() == true) {
                polaczenie.setRequestProperty("If-None-Match", etagPlik.readText())
            }
            val kod = try {
                polaczenie.responseCode
            } catch (e: Exception) {
                // bez internetu: ostatnia zapisana odpowiedź
                if (zapis?.exists() == true) return@withContext parsuj(zapis.readText())
                throw e
            }
            if (BuildConfig.DEBUG) android.util.Log.d("TVML-Siec", "$kod $adres")
            when (kod) {
                304 -> parsuj(zapis!!.readText())
                200 -> {
                    val tekst = polaczenie.inputStream.bufferedReader().use { it.readText() }
                    val wynik = parsuj(tekst)
                    zapis?.writeText(tekst)
                    val etag = polaczenie.getHeaderField("ETag")
                    if (etag != null) etagPlik?.writeText(etag) else etagPlik?.delete()
                    wynik
                }
                else -> throw java.io.IOException("HTTP $kod: $adres")
            }
        } finally {
            polaczenie.disconnect()
        }
    }

    private fun parsuj(tekst: String): Any = JSONTokener(tekst).nextValue()

    /**
     * Trasa API v1, np. v1("articles", mapOf("limit" to "20")). Każde zapytanie ma lang= (API 1.8.0) z wybranym językiem,
     * chyba że [parametry] podają własny (oryginał po polsku). Pamięć podręczna jest więc osobna dla każdego języka.
     */
    suspend fun v1(trasa: String, parametry: Map<String, String> = emptyMap()): Any {
        val zJezykiem = if ("lang" in parametry) parametry else parametry + ("lang" to Jezyk.biezacy)
        val zapytanie = zJezykiem.toSortedMap().entries.joinToString("&") { "${it.key}=${koduj(it.value)}" }
        val kolejnosc = dzialajacaBaza?.let { d -> listOf(d) + bazyApi.filter { it != d } } ?: bazyApi
        var ostatni: Exception = java.io.IOException("brak API")
        for (baza in kolejnosc) {
            val laczenie = if (baza.endsWith("route=")) "&" else "?"
            val sciezka = baza + trasa + if (zapytanie.isEmpty()) "" else laczenie + zapytanie
            try {
                val j = json(sciezka)
                dzialajacaBaza = baza
                return j
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ostatni = e
            }
        }
        throw ostatni
    }

    suspend fun v1Obiekt(trasa: String, parametry: Map<String, String> = emptyMap()): JSONObject? =
        runCatching { v1(trasa, parametry) as? JSONObject }.getOrNull()

    /**
     * POST z JSON-em do API (głos w sondzie): kod HTTP i odpowiedź, bez pamięci podręcznej.
     * Bez identyfikatorów instalacji i urządzenia: serwer ich nie potrzebuje i nie przyjmuje.
     */
    suspend fun v1Wyslij(trasa: String, cialo: JSONObject): Pair<Int, JSONObject> = withContext(Dispatchers.IO) {
        val kolejnosc = dzialajacaBaza?.let { d -> listOf(d) + bazyApi.filter { it != d } } ?: bazyApi
        var ostatni: Exception = java.io.IOException("brak API")
        for (baza in kolejnosc) {
            val laczenie = if (baza.endsWith("route=")) "&" else "?"
            val polaczenie = URL(BAZA + baza + trasa + laczenie + "lang=" + Jezyk.biezacy).openConnection() as HttpURLConnection
            try {
                polaczenie.requestMethod = "POST"
                polaczenie.doOutput = true
                polaczenie.connectTimeout = 15000
                polaczenie.readTimeout = 20000
                polaczenie.setRequestProperty("User-Agent", agent)
                polaczenie.setRequestProperty("Referer", BAZA)
                polaczenie.setRequestProperty("Accept", "application/json")
                polaczenie.setRequestProperty("Accept-Language", Jezyk.naglowek)
                polaczenie.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                polaczenie.outputStream.use { it.write(cialo.toString().toByteArray(Charsets.UTF_8)) }
                val kod = polaczenie.responseCode
                if (kod == 404 && baza != kolejnosc.last()) continue
                val strumien = if (kod >= 400) polaczenie.errorStream else polaczenie.inputStream
                val tekst = strumien?.bufferedReader()?.use { it.readText() }.orEmpty()
                return@withContext kod to (runCatching { JSONObject(tekst) }.getOrNull() ?: JSONObject())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ostatni = e
            } finally {
                polaczenie.disconnect()
            }
        }
        throw ostatni
    }

    suspend fun staryObiekt(sciezka: String): JSONObject? = runCatching { json(sciezka) as? JSONObject }.getOrNull()
    suspend fun staraLista(sciezka: String): List<JSONObject> = runCatching { (json(sciezka) as? JSONArray)?.obiekty() }.getOrNull() ?: emptyList()

    fun koduj(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Mniejszy obraz w WebP z tego samego pośrednika co strona (img-webp-v2.php). */
    fun obraz(adres: String, szerokosc: Int): String? {
        if (adres.isEmpty()) return null
        return "${BAZA}img-webp-v2.php?src=${koduj(adres)}&w=$szerokosc&q=80"
    }
}

// ---------- JSON jak słownik na iPhonie: brak pola i null to pusty tekst ----------

fun JSONObject.s(k: String): String = when (val v = opt(k)) {
    null, JSONObject.NULL -> ""
    is String -> v
    is Double -> if (v % 1.0 == 0.0 && !v.isInfinite()) v.toLong().toString() else v.toString()
    is Number, is Boolean -> v.toString()
    else -> ""
}

fun JSONObject.d(k: String): Double? = when (val v = opt(k)) {
    is Number -> v.toDouble()
    is String -> v.replace(',', '.').toDoubleOrNull()
    else -> null
}

fun JSONObject.i(k: String): Int = s(k).toIntOrNull() ?: d(k)?.toInt() ?: 0

fun JSONObject.b(k: String): Boolean = when (val v = opt(k)) {
    is Boolean -> v
    else -> s(k).lowercase().let { it == "true" || it == "1" }
}

fun JSONObject.o(k: String): JSONObject = optJSONObject(k) ?: JSONObject()

fun JSONObject.lista(k: String): List<JSONObject> = optJSONArray(k)?.obiekty() ?: emptyList()

fun JSONObject.teksty(k: String): List<String> {
    val t = optJSONArray(k) ?: return emptyList()
    return (0 until t.length()).mapNotNull { t.opt(it) as? String }
}

fun JSONArray.obiekty(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONObject.ma(k: String): Boolean = has(k) && !isNull(k)
