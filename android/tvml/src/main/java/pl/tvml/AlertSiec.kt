package pl.tvml

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

// „Zgłoś zdarzenie (Alert TVML)” w aplikacji, bez strony www. Kontrakt z TVML Connect (API 1.9.0): config.alerts,
// POST /api/v1/alerts/token i POST /api/v1/alerts (multipart), zapasowo tvml-app.php?route=…, jak AlertWidok.swift
// na iPhonie. Gdy serwer nie zgłasza formularza (brak config.alerts.native), aplikacja dalej otwiera tvml.pl/#alert.

/** Rodzaj zdarzenia z config.alerts.types: kafel w kolejności z serwera, ikona w aplikacji według id (Ikony.alertu). */
data class TypAlertu(val id: String, val nazwa: String, val kolor: String)

/** Ustawienia formularza z config.alerts, zapisane w ustawieniach na czas bez sieci (Magazyn.alerty). */
data class UstawieniaAlertu(
    val wlaczone: Boolean = true,
    /** Ekran w aplikacji tylko przy alerts.native == true; inaczej przycisk otwiera tvml.pl/#alert. */
    val natywny: Boolean = false,
    val typy: List<TypAlertu> = domyslneTypy(),
    val maxZdjecieMB: Int = 10,
    val maxFilmMB: Int = 25,
    val maxFilmSekund: Int = 60,
    /** consentText z serwera (tłumaczony jak reszta); pusty = tekst wbudowany. */
    val zgoda: String = "",
    /** Serwer (API 1.9.0) wymaga niepustej wersji; ta sama co na iPhonie, gdy config jej nie poda. */
    val wersjaZgody: String = "2026-11-24",
    val polityka: String = "https://tvml.pl/strona/polityka-prywatnosci",
) {
    val tekstZgody: String
        get() = zgoda.ifEmpty {
            tl("app.alertConsent", "Wyrażam zgodę na przetwarzanie moich danych osobowych w celu zgłoszenia informacji o zdarzeniu zgodnie z polityką prywatności serwisu.")
        }

    companion object {
        /** Te same rodzaje co na stronie (portal-chrome.js, openAlertSubmissionModal). */
        fun domyslneTypy(): List<TypAlertu> = listOf(
            TypAlertu("traffic", tl("app.alertTraffic", "Korek"), "#f5a623"),
            TypAlertu("accident", tl("app.alertAccident", "Wypadek"), "#ff4d6a"),
            TypAlertu("weather", tl("app.alertWeather", "Pogoda"), "#007bff"),
            TypAlertu("fire", tl("app.alertFire", "Pożar"), "#fd7e14"),
            TypAlertu("other", tl("app.alertOther", "Inne"), "#6c757d"),
        )

        /** config.alerts; null albo pusty obiekt (stara wersja API) = formularz ze strony. */
        fun z(j: JSONObject?): UstawieniaAlertu {
            val s = j ?: JSONObject()
            val wlaczone = !s.ma("enabled") || s.b("enabled")
            val zapas = domyslneTypy()
            val typy = s.lista("types").mapNotNull { t ->
                val id = t.s("id")
                if (id.isEmpty()) return@mapNotNull null
                val d = zapas.firstOrNull { it.id == id }
                TypAlertu(id, t.s("label").ifEmpty { d?.nazwa ?: id }, t.s("color").ifEmpty { d?.kolor ?: "#6c757d" })
            }
            return UstawieniaAlertu(
                wlaczone = wlaczone,
                natywny = wlaczone && s.b("native"),
                typy = typy.ifEmpty { zapas },
                maxZdjecieMB = s.i("maxImageMB").takeIf { it > 0 } ?: 10,
                maxFilmMB = s.i("maxVideoMB").takeIf { it > 0 } ?: 25,
                maxFilmSekund = s.i("maxVideoSeconds").takeIf { it > 0 } ?: 60,
                zgoda = s.s("consentText"),
                wersjaZgody = s.s("consentVersion").ifEmpty { "2026-11-24" },
                polityka = s.s("privacyUrl").ifEmpty { "https://tvml.pl/strona/polityka-prywatnosci" },
            )
        }
    }
}

/** Token antyspamowy zamiast suwaka ze strony: jednorazowy, przyjmowany po minAge s od wydania, ważny expiresIn s. */
class TokenAlertu(val wartosc: String, val od: Long, val minimumMs: Long, val waznyMs: Long) {
    /** Minutę przed końcem ważności lepiej pobrać nowy, niż dostać token_invalid po wysłaniu pliku. */
    fun wygasl(teraz: Long = SystemClock.elapsedRealtime()): Boolean = waznyMs > 0 && teraz - od > waznyMs - 60_000
}

object SiecAlertu {
    /** POST alerts/token (bez identyfikatora urządzenia). null bez sieci albo przy błędzie serwera. */
    suspend fun token(): TokenAlertu? {
        val (kod, j) = try {
            Siec.v1Wyslij("alerts/token", JSONObject())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return null
        }
        if (kod != 200 || j.s("token").isEmpty()) return null
        return TokenAlertu(
            j.s("token"), SystemClock.elapsedRealtime(),
            ((j.d("minAge") ?: 0.0) * 1000).toLong(), ((j.d("expiresIn") ?: 0.0) * 1000).toLong(),
        )
    }

    /** Odpowiedź na wysyłkę: kod HTTP, JSON (także błąd v1) i nagłówek Retry-After w sekundach. */
    class Odpowiedz(val kod: Int, val json: JSONObject, val ponowZaSekund: Int?)

    /**
     * Jedna próba: POST alerts?lang=… (multipart), a przy 404 zapasowy adres tvml-app.php?route=alerts, jak w Siec.v1Wyslij.
     * Ciało ma znaną długość (setFixedLengthStreamingMode), więc plik idzie prosto z dysku, a [postep] (0…1, wątek IO)
     * liczy wysłane bajty. Bez sieci rzuca wyjątek.
     */
    suspend fun wyslij(pola: List<Pair<String, String>>, zalacznik: ZalacznikAlertu?, postep: (Float) -> Unit): Odpowiedz =
        withContext(Dispatchers.IO) {
            val granica = "TVML-" + UUID.randomUUID()
            val poczatek = ByteArrayOutputStream().apply {
                for ((nazwa, wartosc) in pola) {
                    write("--$granica\r\nContent-Disposition: form-data; name=\"$nazwa\"\r\n\r\n".toByteArray(Charsets.UTF_8))
                    write(wartosc.toByteArray(Charsets.UTF_8))
                    write("\r\n".toByteArray(Charsets.UTF_8))
                }
                if (zalacznik != null) {
                    write(
                        ("--$granica\r\nContent-Disposition: form-data; name=\"attachment\"; filename=\"${zalacznik.nazwaPliku}\"\r\n" +
                            "Content-Type: ${zalacznik.mime}\r\n\r\n").toByteArray(Charsets.UTF_8),
                    )
                }
            }.toByteArray()
            val koniec = ((if (zalacznik != null) "\r\n" else "") + "--$granica--\r\n").toByteArray(Charsets.UTF_8)
            val dlugosc = poczatek.size + (zalacznik?.plik?.length() ?: 0L) + koniec.size

            val kolejnosc = Siec.bazyV1()
            var ostatni: Exception = java.io.IOException("brak API")
            for (baza in kolejnosc) {
                val laczenie = if (baza.endsWith("route=")) "&" else "?"
                val polaczenie = URL(Siec.BAZA + baza + "alerts" + laczenie + "lang=" + Jezyk.biezacy).openConnection() as HttpURLConnection
                try {
                    polaczenie.requestMethod = "POST"
                    polaczenie.doOutput = true
                    polaczenie.connectTimeout = 15000
                    // jak na iPhonie: 90 s na odpowiedź (serwer zapisuje plik i wysyła mail do redakcji)
                    polaczenie.readTimeout = 90000
                    polaczenie.setFixedLengthStreamingMode(dlugosc)
                    polaczenie.setRequestProperty("User-Agent", Siec.agent)
                    polaczenie.setRequestProperty("Referer", Siec.BAZA)
                    polaczenie.setRequestProperty("Accept", "application/json")
                    polaczenie.setRequestProperty("Accept-Language", Jezyk.naglowek)
                    polaczenie.setRequestProperty("Content-Type", "multipart/form-data; boundary=$granica")
                    postep(0f)
                    polaczenie.outputStream.use { wyj ->
                        wyj.write(poczatek)
                        var wyslane = poczatek.size.toLong()
                        zalacznik?.plik?.inputStream()?.use { wej ->
                            val bufor = ByteArray(64 * 1024)
                            while (true) {
                                val n = wej.read(bufor)
                                if (n < 0) break
                                wyj.write(bufor, 0, n)
                                wyslane += n
                                postep((wyslane.toFloat() / dlugosc).coerceIn(0f, 1f))
                            }
                        }
                        wyj.write(koniec)
                    }
                    postep(1f)
                    val kod = polaczenie.responseCode
                    if (BuildConfig.DEBUG) android.util.Log.d("TVML-Siec", "$kod ${polaczenie.url}")
                    if (kod == 404 && baza != kolejnosc.last()) continue
                    val strumien = if (kod >= 400) polaczenie.errorStream else polaczenie.inputStream
                    val tekst = strumien?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val ponow = polaczenie.getHeaderField("Retry-After")?.trim()?.toIntOrNull()
                    return@withContext Odpowiedz(kod, runCatching { JSONObject(tekst) }.getOrNull() ?: JSONObject(), ponow)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    ostatni = e
                } finally {
                    polaczenie.disconnect()
                }
            }
            throw ostatni
        }
}
