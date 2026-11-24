package pl.tvml

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Ikony kreskowe z projektu (siatka 24): obrys 2, zaokrąglone końce. Wypełnione części zaczynają się od „!”. */
object Ikony {
    private fun ikona(vararg sciezki: String, grubosc: Float = 2f): ImageVector {
        val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (s in sciezki) {
            if (s.startsWith("!")) {
                b.addPath(pathData = addPathNodes(s.substring(1)), fill = SolidColor(Color.Black))
            } else {
                b.addPath(
                    pathData = addPathNodes(s), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = grubosc,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        return b.build()
    }

    private fun kolo(x: Float, y: Float, r: Float) = "M${x - r} ${y}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"
    private fun kwadrat(x: Float, y: Float) = "M${x + 1.5f} ${y}h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3a1.5 1.5 0 0 1-1.5-1.5v-3a1.5 1.5 0 0 1 1.5-1.5z"

    val start = ikona("M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")
    val sport = ikona(kolo(12f, 12f, 9f), "M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18M3 12h18")
    val wideo = ikona("M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "!M10 9.5v5l4.5-2.5z")
    val pogoda = ikona("M17.5 19H8a5 5 0 1 1 1.1-9.9A6 6 0 0 1 20 11.5 3.75 3.75 0 0 1 17.5 19z")
    val wiecej = ikona(kwadrat(4f, 4f), kwadrat(14f, 4f), kwadrat(4f, 14f), kwadrat(14f, 14f))
    val wiadomosci = ikona("M4 5h13v14H6a2 2 0 0 1-2-2z", "M17 9h3v8a2 2 0 0 1-2 2", "M7 9h7M7 13h7M7 16h4")
    val kultura = ikona("M5 4h14v6a7 7 0 0 1-14 0z", "M9 9h.01M15 9h.01", "M9.5 13.5a3.5 3.5 0 0 0 5 0")
    val muzyka = ikona("M9 18V5l11-2v13", kolo(6f, 18f, 3f), kolo(17f, 16f, 3f))
    val alarm = ikona("M12 3l9 16H3z", "M12 10v4M12 17h.01")
    val gmina = ikona("M9 4L3 6v14l6-2 6 2 6-2V4l-6 2z", "M9 4v14M15 6v14")
    val szukaj = ikona(kolo(11f, 11f, 7f), "M20 20l-3.5-3.5")
    val wstecz = ikona("M15 5l-7 7 7 7", grubosc = 2.2f)
    val dalej = ikona("M9 6l6 6-6 6")
    val wDol = ikona("M6 9l6 6 6-6", grubosc = 3f)

    // medale belki z datą na karcie pogody (BelkaPogody.kt), odpowiedniki symboli z iPhone'a
    val kalendarzMedal = ikona(
        "M5.5 5h13A1.5 1.5 0 0 1 20 6.5v12a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v-12A1.5 1.5 0 0 1 5.5 5z", "M4 9.5h16", "M8 3v3.5", "M16 3v3.5",
        "!" + kolo(8f, 13f, 1.1f), "!" + kolo(12f, 13f, 1.1f), "!" + kolo(16f, 13f, 1.1f),
        "!" + kolo(8f, 16.8f, 1.1f), "!" + kolo(12f, 16.8f, 1.1f), "!" + kolo(16f, 16.8f, 1.1f),
    )
    val sniezynka = ikona(
        "M12 2.5v19", "M3.8 7.25l16.4 9.5", "M3.8 16.75l16.4-9.5",
        "M9.5 4l2.5 2 2.5-2", "M9.5 20l2.5-2 2.5 2",
        "M4.2 10.1l3 -0.6-1-3", "M19.8 13.9l-3 .6 1 3", "M4.2 13.9l3 .6-1 3", "M19.8 10.1l-3-.6 1-3",
        grubosc = 2.2f,
    )
    /** Prezent jak gift.fill na iPhonie: pudełko z pokrywką i kokardą, wstążka to przerwa w kształcie. */
    val prezent = ikona(
        "!M4 11h7v10H5a1 1 0 0 1-1-1zM13 11h7v9a1 1 0 0 1-1 1h-6z", "!M3 7.5h8V10H3zM13 7.5h8V10h-8z",
        "M12 7.5C10.8 4.6 7.4 4.3 7.6 6.4 7.8 7.7 10.6 7.6 12 7.5zM12 7.5c1.2-2.9 4.6-3.2 4.4-1.1-.2 1.3-3 1.2-4.4 1.1z",
        grubosc = 1.6f,
    )
    val gwiazdaPelna = ikona("!M12 2.5l2.9 6 6.6.9-4.8 4.6 1.2 6.5L12 17.4l-5.9 3.1 1.2-6.5L2.5 9.4l6.6-.9z")
    val choinka = ikona("!M12 2l5 7h-2.6l4.1 6h-3l3.5 5H5l3.5-5h-3l4.1-6H7z", "!M11 20h2v3h-2z")
    val plomien = ikona("!M12 2c1 3.6 5.2 5.6 5.2 11.2a5.2 5.2 0 0 1-10.4 0c0-2.5 1.2-4.2 2.6-5.4.2 1.8 1 2.8 2.1 3.3C11 8.6 11.3 5.1 12 2z")
    val zamknij = ikona("M6 6l12 12M18 6L6 18")
    val udostepnij = ikona(kolo(18f, 5f, 2.6f), kolo(6f, 12f, 2.6f), kolo(18f, 19f, 2.6f), "M8.3 13.3l7.4 4.4M15.7 6.3l-7.4 4.4")
    val glosnik = ikona("M11 5L6 9H3v6h3l5 4z", "M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13")
    val stop = ikona("!M7 7h10v10H7z")
    val zakladka = ikona("M6 3h12v18l-6-4-6 4z")
    val zakladkaPelna = ikona("!M6 3h12v18l-6-4-6 4z", "M6 3h12v18l-6-4-6 4z")
    val komentarze = ikona("M21 12a8 8 0 0 1-11.6 7.1L4 21l1.9-5.4A8 8 0 1 1 21 12z")
    val odtworz = ikona("!M8 5v14l11-7z")
    val pauza = ikona("!M7 5h3.5v14H7zM13.5 5H17v14h-3.5z")
    /** Oko (pasek podglądu szkicu z panelu). */
    val oko = ikona("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z")
    /** Kółko z „i” (komunikat informacyjny). */
    val informacja = ikona("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 16.5v-5", "M12 8h.01")
    /** Strzałka w kółku w lewo (cofnij o 10 s; liczbę dopisuje odtwarzacz). */
    val cofnij = ikona("M9 7H5V3", "M5.6 7A8 8 0 1 1 4 12")
    /** Strzałka w kółku w prawo (przewiń o 10 s). */
    val przewin = ikona("M15 7h4V3", "M18.4 7A8 8 0 1 0 20 12")
    val kalendarz = ikona("M5 5h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M4 10h16M8 3v4M16 3v4")
    val miejsce = ikona("M12 21s-7-6.1-7-11.5a7 7 0 0 1 14 0C19 14.9 12 21 12 21z", kolo(12f, 9.5f, 2.5f))
    val osoby = ikona(kolo(9f, 8f, 3.5f), "M2.5 20a6.5 6.5 0 0 1 13 0", "M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.2a6.5 6.5 0 0 1 3.5 5.8")
    val bilet = ikona("M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4z", "M14 7v2M14 11v2M14 15v2")
    val youtube = ikona("M6 5h12a4 4 0 0 1 4 4v6a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V9a4 4 0 0 1 4-4z", "!M10 9.5v5l4.5-2.5z")

    // ---------- Alert TVML (AlertEkran.kt): kafle rodzajów jak ikony Material, reszta kreskowa ----------
    /** Samochód (Material DirectionsCar); ścieżka także do obrazka w przykładzie do zrzutów (PlikiAlertu.przykladowy). */
    const val SAMOCHOD = "M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z"
    val samochod = ikona("!$SAMOCHOD")
    /** Trójkąt z wykrzyknikiem (Material Warning). */
    val ostrzezenie = ikona("!M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z")
    /** Chmura z błyskawicą (jak Thunderstorm). */
    val burza = ikona(
        "!M17.5 14H7.5a4 4 0 1 1 .9-7.9A5 5 0 0 1 18 8.2 2.9 2.9 0 0 1 17.5 14z",
        "!M12.5 15.5l-3 4.5h2.5l-1 3.5 4-5h-2.5l1.2-3z",
    )
    /** Kółko z „i”, wypełnione (Material Info). */
    val informacjaPelna = ikona("!M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z")
    /** Słuchawka (Material Phone) przy „Najpierw dzwoń pod 112”. */
    val telefon = ikona("!M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z")
    /** Strzałka położenia (jak location na iPhonie). */
    val polozenie = ikona("M20 4L4 11l7 2 2 7z")
    val aparat = ikona("M4 8h3l1.5-2.5h7L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z", kolo(12f, 13f, 3.5f))
    val galeria = ikona("M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M4 16l4.5-4.5 4 4 2.5-2.5 5 5", kolo(15.5f, 8.5f, 1.5f))
    /** Samolot papierowy (Material Send) na przycisku „Wyślij zgłoszenie”. */
    val wyslij = ikona("!M2.01 21L23 12 2.01 3 2 10l15 2-15 2z")
    /** Tarcza przy „Polityka prywatności”. */
    val tarcza = ikona("M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6z")
    /** Kółko z wykrzyknikiem: błąd wysyłki. */
    val uwaga = ikona(kolo(12f, 12f, 9f), "M12 7.5v5.5", "M12 16.5h.01")
    /** Kula ziemska: formularz na stronie tvml.pl. */
    val przegladarka = ikona(kolo(12f, 12f, 9f), "M3 12h18", "M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3z")
    val ptaszek = ikona("M5 12.5l4.5 4.5L19 7.5", grubosc = 2.6f)

    /** Ikona kafla rodzaju zdarzenia (config.alerts.types[].id); nieznany rodzaj: znacznik miejsca. */
    fun alertu(id: String): ImageVector = when (id) {
        "traffic" -> samochod
        "accident" -> ostrzezenie
        "weather" -> burza
        "fire" -> plomien
        "other" -> informacjaPelna
        else -> miejsce
    }

    /** Ikona zakładki z panelu (tabs[].icon). */
    fun zakladki(nazwa: String): ImageVector = when (nazwa) {
        "home" -> start
        "sport" -> sport
        "video" -> wideo
        "weather" -> pogoda
        "more" -> wiecej
        "news" -> wiadomosci
        "culture" -> kultura
        "music" -> muzyka
        "alert" -> alarm
        "county" -> gmina
        else -> wiecej
    }
}
