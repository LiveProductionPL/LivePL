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
    val zamknij = ikona("M6 6l12 12M18 6L6 18")
    val udostepnij = ikona(kolo(18f, 5f, 2.6f), kolo(6f, 12f, 2.6f), kolo(18f, 19f, 2.6f), "M8.3 13.3l7.4 4.4M15.7 6.3l-7.4 4.4")
    val glosnik = ikona("M11 5L6 9H3v6h3l5 4z", "M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13")
    val stop = ikona("!M7 7h10v10H7z")
    val zakladka = ikona("M6 3h12v18l-6-4-6 4z")
    val zakladkaPelna = ikona("!M6 3h12v18l-6-4-6 4z", "M6 3h12v18l-6-4-6 4z")
    val komentarze = ikona("M21 12a8 8 0 0 1-11.6 7.1L4 21l1.9-5.4A8 8 0 1 1 21 12z")
    val odtworz = ikona("!M8 5v14l11-7z")
    val kalendarz = ikona("M5 5h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M4 10h16M8 3v4M16 3v4")
    val miejsce = ikona("M12 21s-7-6.1-7-11.5a7 7 0 0 1 14 0C19 14.9 12 21 12 21z", kolo(12f, 9.5f, 2.5f))
    val osoby = ikona(kolo(9f, 8f, 3.5f), "M2.5 20a6.5 6.5 0 0 1 13 0", "M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.2a6.5 6.5 0 0 1 3.5 5.8")
    val bilet = ikona("M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4z", "M14 7v2M14 11v2M14 15v2")
    val youtube = ikona("M6 5h12a4 4 0 0 1 4 4v6a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V9a4 4 0 0 1 4-4z", "!M10 9.5v5l4.5-2.5z")

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
