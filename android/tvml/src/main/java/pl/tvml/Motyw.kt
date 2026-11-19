package pl.tvml

import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.pow

/** Kolor z panelu (#rrggbb). */
fun kolor(hex: String, zapas: Color = Color.Gray): Color {
    val t = hex.trim().removePrefix("#")
    val v = if (t.length == 6) t.toLongOrNull(16) else null
    return if (v == null) zapas else Color(0xFF000000 or v)
}

fun kolor(v: Long): Color = Color(0xFF000000 or v)

/** Kolor, który zmienia się z trybem dzień/noc. */
data class Para(val dzien: Color, val noc: Color) {
    fun w(ciemny: Boolean) = if (ciemny) noc else dzien

    companion object {
        fun z(dzien: Long, noc: Long) = Para(kolor(dzien), kolor(noc))
        fun stala(c: Color) = Para(c, c)
    }
}

/** Kolor napisu na kolorze kategorii: biały albo ciemny, zależnie od jasności tła (czytelność jak w WCAG). */
object Kontrast {
    fun napis(hex: String): Color {
        val t = hex.removePrefix("#")
        val v = (if (t.length == 6) t.toLongOrNull(16) else null) ?: return kolor(0x1d1400)
        fun kanal(c: Long): Double {
            val x = c / 255.0
            return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        val l = 0.2126 * kanal((v shr 16) and 0xff) + 0.7152 * kanal((v shr 8) and 0xff) + 0.0722 * kanal(v and 0xff)
        return if (1.05 / (l + 0.05) >= 4.5) Color.White else kolor(0x1d1400)
    }
}

/**
 * Wersja wyglądu ustawiana w panelu tvml.pl (Ustawienia → Wygląd): żałoba, 11 Listopada, zima, Boże Narodzenie,
 * Sylwester i Nowy Rok. Wylicza ją serwer (config.theme) tą samą regułą co strona; aplikacja zamienia ją na kolory,
 * ozdobę logo, pasek życzeń, śnieg albo iskry i scenę pogody. To samo co Motyw.swift na iPhonie.
 */
data class Motyw(
    val rodzaj: Rodzaj,
    val wariant: String = "",
    val pasek: Pasek? = null,
    val paleta: Paleta? = null,
    /** Ikona z panelu (theme.appIcon: default, winter, xmas). null = wg rodzaju. */
    val ikonaZPanelu: String? = null,
    val szaroscZPanelu: Boolean = false,
) {
    enum class Rodzaj { ZWYKLY, ZALOBA, LISTOPAD, ZIMA, MIKOLAJKI, CZAPECZKA, WIGILIA, SWIETA, SYLWESTER, NOWY_ROK }
    enum class Scena { WIOSNA, LATO, JESIEN, ZIMA, SWIETA, SYLWESTER, LISTOPAD }
    data class Pasek(val etykieta: String, val tekst: String, val stopka: String? = null, val odliczanieDo: ZonedDateTime? = null)

    /** Kolory wersji z panelu (API 1.1: theme.colors.light i .dark). */
    class Paleta private constructor(
        private val jasna: Map<String, String>,
        private val ciemna: Map<String, String>,
        private val gradientJasny: List<String>,
        private val gradientCiemny: List<String>,
        val paski: List<String>,
    ) {
        fun barwa(k: String): Para? {
            val a = jasna[k] ?: return null
            return Para(kolor(a), kolor(ciemna[k] ?: a))
        }
        fun napis(k: String): Para? {
            val a = jasna[k] ?: return null
            return Para(Kontrast.napis(a), Kontrast.napis(ciemna[k] ?: a))
        }
        val naglowek: List<Para>?
            get() = if (gradientJasny.size < 2) null else gradientJasny.mapIndexed { i, h -> Para(kolor(h), kolor(gradientCiemny.getOrNull(i) ?: h)) }

        override fun equals(other: Any?) = other is Paleta && other.jasna == jasna && other.ciemna == ciemna && other.gradientJasny == gradientJasny
        override fun hashCode() = jasna.hashCode()

        companion object {
            fun z(c: JSONObject): Paleta? {
                val j = c.o("light")
                val d = c.o("dark").let { if (it.length() == 0) j else it }
                if (j.length() == 0) return null
                fun pola(s: JSONObject) = listOf("accent", "live", "tabActive", "headerLine", "gold").filter { s.s(it).isNotEmpty() }.associateWith { s.s(it) }
                val gj = j.teksty("headerGradient")
                return Paleta(pola(j), pola(d), gj, d.teksty("headerGradient").ifEmpty { gj }, j.teksty("headerLineStripes"))
            }
        }
    }

    // ---------- wygląd ----------

    val naglowek: List<Para>
        get() = paleta?.naglowek ?: when (rodzaj) {
            Rodzaj.ZIMA -> listOf(0x040d1aL, 0x0a1e38L, 0x0f3156L)
            Rodzaj.WIGILIA, Rodzaj.SWIETA -> listOf(0x14070cL, 0x24101aL)
            Rodzaj.SYLWESTER, Rodzaj.NOWY_ROK -> listOf(0x0d0b24L, 0x1b1640L)
            Rodzaj.ZALOBA -> listOf(0x16181cL, 0x23262cL)
            else -> listOf(0x0a1f44L, 0x0d2856L)
        }.map { Para.stala(kolor(it)) }

    /** Dwukolorowa linia pod banerem (11 Listopada: biało-czerwona). */
    val paskiLinii: List<Color> get() = paleta?.paski?.map { kolor(it) } ?: emptyList()

    val linia: Para?
        get() = paleta?.barwa("headerLine") ?: when (rodzaj) {
            Rodzaj.ZIMA -> 0x38bdf8L
            Rodzaj.LISTOPAD -> 0xdc143cL
            Rodzaj.MIKOLAJKI, Rodzaj.CZAPECZKA -> 0xc8102eL
            Rodzaj.WIGILIA, Rodzaj.SWIETA -> 0xe0b45aL
            Rodzaj.SYLWESTER, Rodzaj.NOWY_ROK -> 0xe8c26aL
            Rodzaj.ZALOBA -> 0x4b5563L
            Rodzaj.ZWYKLY -> null
        }?.let { Para.stala(kolor(it)) }

    val naZywo: Para
        get() = paleta?.barwa("live") ?: Para.stala(kolor(when (rodzaj) {
            Rodzaj.ZIMA -> 0x0369a1L
            Rodzaj.MIKOLAJKI, Rodzaj.CZAPECZKA -> 0xb91c1cL
            Rodzaj.WIGILIA, Rodzaj.SWIETA -> 0x9b1c1cL
            Rodzaj.SYLWESTER, Rodzaj.NOWY_ROK -> 0x5b21b6L
            Rodzaj.ZALOBA -> 0x374151L
            else -> 0xc8102eL
        }))

    /** Kolor zaznaczonej zakładki i kategorii. */
    val akcent: Para
        get() = paleta?.barwa("tabActive") ?: paleta?.barwa("accent") ?: when (rodzaj) {
            Rodzaj.ZWYKLY -> Para.z(0x0a1f44, 0xf0b03a)
            Rodzaj.LISTOPAD -> Para.stala(kolor(0xa50e26))
            Rodzaj.ZALOBA -> Para.z(0x111827, 0xd1d5db)
            else -> naZywo
        }

    /** Napis na kolorze akcentu: w nocy akcent zwykły jest złoty, a żałobny jasnoszary, więc wtedy ciemny napis. */
    val napisNaAkcencie: Para
        get() = paleta?.napis("tabActive") ?: paleta?.napis("accent") ?: when (rodzaj) {
            Rodzaj.ZWYKLY -> Para.z(0xffffff, 0x1d1400)
            Rodzaj.ZALOBA -> Para.z(0xffffff, 0x111827)
            else -> Para.stala(Color.White)
        }

    val snieg: Boolean get() = rodzaj in setOf(Rodzaj.ZIMA, Rodzaj.MIKOLAJKI, Rodzaj.CZAPECZKA, Rodzaj.WIGILIA, Rodzaj.SWIETA)
    val iskry: Boolean get() = rodzaj == Rodzaj.SYLWESTER || rodzaj == Rodzaj.NOWY_ROK
    val kolorPlatkow: Color
        get() = when (rodzaj) {
            Rodzaj.WIGILIA -> kolor(0xf6e3b0)
            Rodzaj.SYLWESTER, Rodzaj.NOWY_ROK -> kolor(0xe8c26a)
            else -> Color.White
        }
    val szarosc: Boolean get() = rodzaj == Rodzaj.ZALOBA || szaroscZPanelu
    val logo: Int get() = if (rodzaj == Rodzaj.ZALOBA) R.drawable.logo_zaloba else R.drawable.logo_noc

    val tloPaska: List<Color>
        get() = when (rodzaj) {
            Rodzaj.SYLWESTER, Rodzaj.NOWY_ROK -> listOf(0x0a0820L, 0x17123aL)
            Rodzaj.LISTOPAD -> listOf(0x7f0d1fL, 0xa50e26L)
            Rodzaj.ZIMA -> listOf(0x040d1aL, 0x0f3156L)
            else -> listOf(0x1c0609L, 0x2b0b12L)
        }.map { kolor(it) }

    val zlotoPaska: Color
        get() = when (rodzaj) {
            Rodzaj.SYLWESTER, Rodzaj.NOWY_ROK -> kolor(0xe8c26a)
            Rodzaj.LISTOPAD -> Color.White
            Rodzaj.ZIMA -> kolor(0x7dd3fc)
            Rodzaj.MIKOLAJKI -> kolor(0xf3d27a)
            else -> kolor(0xe0b45a)
        }

    /** Ikona aplikacji wbudowana w aplikację (activity-alias w AndroidManifest.xml). */
    val ikona: IkonaAplikacji.Rodzaj
        get() = when (ikonaZPanelu) {
            "winter" -> IkonaAplikacji.Rodzaj.ZIMA
            "xmas" -> IkonaAplikacji.Rodzaj.SWIETA
            "default" -> IkonaAplikacji.Rodzaj.ZWYKLA
            else -> when (rodzaj) {
                Rodzaj.ZIMA -> IkonaAplikacji.Rodzaj.ZIMA
                Rodzaj.MIKOLAJKI, Rodzaj.CZAPECZKA, Rodzaj.WIGILIA, Rodzaj.SWIETA -> IkonaAplikacji.Rodzaj.SWIETA
                else -> IkonaAplikacji.Rodzaj.ZWYKLA
            }
        }

    fun scena(miesiac: Int = LocalDate.now(Daty.strefa).monthValue): Scena = when (rodzaj) {
        Rodzaj.ZIMA -> Scena.ZIMA
        Rodzaj.MIKOLAJKI, Rodzaj.CZAPECZKA, Rodzaj.WIGILIA, Rodzaj.SWIETA -> Scena.SWIETA
        Rodzaj.SYLWESTER, Rodzaj.NOWY_ROK -> Scena.SYLWESTER
        Rodzaj.LISTOPAD -> Scena.LISTOPAD
        else -> when (miesiac) {
            12, 1, 2 -> Scena.ZIMA
            3, 4, 5 -> Scena.WIOSNA
            6, 7, 8 -> Scena.LATO
            else -> Scena.JESIEN
        }
    }

    companion object {
        val zwykly = Motyw(Rodzaj.ZWYKLY)

        /** Wersja wyliczona przez serwer (API v1, pole theme). null, gdy odpowiedź jej nie ma. */
        fun zV1(t: JSONObject, wersjaApi: String, teraz: LocalDate = LocalDate.now(Daty.strefa)): Motyw? {
            val id = t.s("id")
            if (id.isEmpty()) return null
            val wariant = t.s("variant")
            val rodzaj = when (id) {
                "mourning", "zaloba" -> Rodzaj.ZALOBA
                "indep", "11listopada" -> Rodzaj.LISTOPAD
                "winter" -> Rodzaj.ZIMA
                "xmas" -> when (wariant) {
                    "mikolajki" -> Rodzaj.MIKOLAJKI
                    "wigilia" -> Rodzaj.WIGILIA
                    "swieta" -> Rodzaj.SWIETA
                    else -> Rodzaj.CZAPECZKA
                }
                "nye" -> if (wariant == "new_year" || wariant == "nowy_rok" || teraz.monthValue == 1) Rodzaj.NOWY_ROK else Rodzaj.SYLWESTER
                else -> Rodzaj.ZWYKLY
            }
            val p = t.o("topbar")
            val pasek = if (p.s("text").isEmpty()) null else {
                val domyslna = when (rodzaj) {
                    Rodzaj.LISTOPAD -> "ŚWIĘTO NIEPODLEGŁOŚCI"
                    Rodzaj.ZIMA -> "ZIMA"
                    Rodzaj.MIKOLAJKI -> "6 GRUDNIA · MIKOŁAJKI"
                    Rodzaj.CZAPECZKA, Rodzaj.WIGILIA, Rodzaj.SWIETA -> "BOŻE NARODZENIE"
                    Rodzaj.SYLWESTER -> "SYLWESTER"
                    Rodzaj.NOWY_ROK -> "NOWY ROK"
                    else -> "TVML"
                }
                Pasek(p.s("label").ifEmpty { domyslna }, p.s("text"), p.s("foot").ifEmpty { null }, Daty.iso(p.s("countdownTo")))
            }
            // API 1.0 podawało tu kolory bazowe portalu, dopiero 1.1 kolory wersji
            val kolory = if (rodzaj != Rodzaj.ZWYKLY && porownajWersje(wersjaApi, "1.1") >= 0) Paleta.z(t.o("colors")) else null
            return Motyw(rodzaj, wariant, pasek, kolory, t.s("appIcon").ifEmpty { null }, t.b("grayscaleImages"))
        }

        fun porownajWersje(a: String, b: String): Int {
            val x = a.split('.').map { it.toIntOrNull() ?: 0 }
            val y = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val r = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
                if (r != 0) return r
            }
            return 0
        }

        /** Do zrzutów i testów: --es motyw zima|czapeczka|wigilia|sylwester|listopad|zaloba … */
        fun zArgumentu(nazwa: String?): Motyw? {
            val r = when (nazwa) {
                "zwykly" -> Rodzaj.ZWYKLY
                "zaloba" -> Rodzaj.ZALOBA
                "listopad" -> Rodzaj.LISTOPAD
                "zima" -> Rodzaj.ZIMA
                "mikolajki" -> Rodzaj.MIKOLAJKI
                "czapeczka" -> Rodzaj.CZAPECZKA
                "wigilia" -> Rodzaj.WIGILIA
                "swieta" -> Rodzaj.SWIETA
                "sylwester" -> Rodzaj.SYLWESTER
                "nowyRok" -> Rodzaj.NOWY_ROK
                else -> return null
            }
            return when (r) {
                Rodzaj.WIGILIA, Rodzaj.SWIETA -> Motyw(r, pasek = Pasek("BOŻE NARODZENIE", "Spokojnych, pełnych ciepła i rodzinnej atmosfery Świąt życzy Zespół Redakcyjny TVML.PL", "WYDANIE ŚWIĄTECZNE • GRUDZIEŃ 2026"))
                Rodzaj.SYLWESTER -> Motyw(r, pasek = Pasek("SYLWESTER 2026/2027", "Szampańskiej nocy sylwestrowej oraz pomyślności i sukcesów w Nowym Roku 2027 życzy Zespół Redakcyjny TVML.PL", odliczanieDo = ZonedDateTime.now(Daty.strefa).plusDays(42)))
                Rodzaj.ZIMA -> Motyw(r, "c")
                Rodzaj.LISTOPAD -> Motyw(r, "b")
                else -> Motyw(r)
            }
        }
    }
}
