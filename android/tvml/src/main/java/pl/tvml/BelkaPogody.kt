package pl.tvml

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZonedDateTime

/**
 * Belka z datą pod grafiką karty pogody jako przedłużenie podłoża sceny (Karol, 23.11: „dopasowany pod wydarzenie,
 * jak śnieg to biały wyróżniający się, a jak pole to brązowy jak ziemia”): śnieg, ziemia, trawa, piasek, woda albo
 * bruk Rynku, w nocy przyciemnione; tekst ciemny albo jasny, z kontrastem co najmniej 4,5 : 1. Motywy okolicznościowe
 * mają u góry pasek w swoim kolorze i medal z ikoną jak karta daty na stronie. 1:1 z iPhone'em (PaletaBelki w
 * WyborSceny.swift, budowa 58, zatwierdzona przez Karola).
 */
data class PaletaBelki(
    val tlo: Color,
    /** Pasek akcentu motywu u góry belki (null = belka zlewa się z podłożem). */
    val akcent: Color?,
    val data: Color,
    val drugaLinia: Color,
    val medal: Color,
    val obwodka: Color,
    val ikona: Ikona,
    /** Tekst w medalu przy Nowym Roku („27”). */
    val rok: String = "",
) {
    enum class Ikona { KALENDARZ, ROZETA, SNIEZYNKA, CZAPKA, GWIAZDA, CHOINKA, SWIECA, ROK, BRAK }

    companion object {
        /** Podłoże sceny (dzień, noc) i czy jest jasne (wtedy tekst ciemny). */
        private fun podloze(id: String, noc: Boolean): Pair<Long, Boolean> = when (id) {
            "leszno-zima", "leszno-mikolajki", "leszno-wigilia", "leszno-swieta", "winter-village", "winter-christmas", "alpine-peaks" ->
                if (noc) 0x24324aL to false else 0xeef3f8L to true          // śnieg
            "leszno-sylwester", "leszno-nowy-rok" -> if (noc) 0x121a2cL to false else 0xe4ebf3L to true   // zaśnieżony Rynek
            "autumn-forest" -> if (noc) 0x241812L to false else 0x5e3219L to false                    // ziemia jesiennej łąki
            "easter-spring" -> if (noc) 0x0c1a14L to false else 0x1b4332L to false                    // trawa
            "park-lanterns" -> if (noc) 0x141824L to false else 0x2b2936L to false                    // mokra alejka
            "powstanczy-biwak" -> if (noc) 0x1a120cL to false else 0x3a2a1cL to false                 // ziemia przy ognisku
            "beach" -> if (noc) 0x2c2416L to false else 0xe3c27aL to true                             // piasek
            "ocean" -> if (noc) 0x04101cL to false else 0x00405aL to false                            // głęboka woda
            "autumn-lake" -> if (noc) 0x10162aL to false else 0x24314aL to false                      // jezioro
            "new-year" -> 0x0b0f24L to false                                                          // woda nocą
            "leszno-ratusz-11listopada" -> if (noc) 0x161a24L to false else 0x4a505cL to false        // bruk Rynku
            "leszno-andrzejki" -> if (noc) 0x26103eL to false else 0x5b3a8aL to false                 // fioletowe wzgórza
            else -> if (noc) 0x241812L to false else 0x5e3219L to false
        }

        /** Nazwy ikon medalu z theme.dateBar.medal.icon (API 1.8.3). */
        private fun ikonaZ(nazwa: String): Ikona? = when (nazwa.lowercase()) {
            "calendar", "kalendarz" -> Ikona.KALENDARZ
            "rosette", "cockade", "kotylion", "rozeta", "kokarda", "indep" -> Ikona.ROZETA
            "snowflake", "sniezynka", "snow" -> Ikona.SNIEZYNKA
            "santa-hat", "santahat", "gift", "prezent", "czapka" -> Ikona.CZAPKA
            "star", "gwiazda" -> Ikona.GWIAZDA
            "tree", "choinka", "xmas-tree" -> Ikona.CHOINKA
            "candle", "flame", "swieca" -> Ikona.SWIECA
            "year", "rok" -> Ikona.ROK
            "none", "brak" -> Ikona.BRAK
            else -> null
        }

        /**
         * Paleta belki dla sceny [id] (albo obrazu SVG motywu, gdy [svg]). Z [pasek] (theme.dateBar z panelu, API 1.8.3)
         * idą pasek akcentu, medal z obwódką i ikona; tło i kolory tekstu zostają z podłoża sceny (wzór z iPhone'a).
         */
        fun dla(
            id: String, noc: Boolean, svg: Boolean, teraz: ZonedDateTime = ZonedDateTime.now(Daty.strefa), pasek: PasekDaty? = null,
            poraRoku: Motyw.Scena = Motyw.Scena.JESIEN,
        ): PaletaBelki {
            val wbud = wbudowana(id, noc, svg, teraz)
            // Panorama Leszna: od 25.11 Rynek z 11 Listopada w wersji codziennej (jak na stronie), więc belka w kolorze
            // bruku Rynku, jak przy scenie 11 Listopada (tak samo na iOS)
            val baza = if (id == ScenyStrony.PANORAMA && !svg) {
                wbud.copy(tlo = kolor(if (noc) 0x161a24 else 0x4a505c), data = Color.White, drugaLinia = kolor(0xe2e8f0))
            } else {
                wbud
            }
            if (pasek == null) return baza
            val ikona = ikonaZ(pasek.ikona) ?: baza.ikona
            return baza.copy(
                akcent = pasek.akcent?.let { Color(it) } ?: baza.akcent,
                medal = pasek.medal?.let { Color(it) } ?: baza.medal,
                obwodka = pasek.obwodka?.let { Color(it) } ?: baza.obwodka,
                ikona = ikona,
                rok = pasek.tekstMedalu ?: if (ikona == Ikona.ROK) baza.rok.ifEmpty { (teraz.year % 100).toString() } else baza.rok,
            )
        }

        private fun wbudowana(id: String, noc: Boolean, svg: Boolean, teraz: ZonedDateTime): PaletaBelki {
            val (hex, jasne) = if (svg) 0x111827L to false else podloze(id, noc)
            val data = if (jasne) kolor(0x0f172a) else Color.White
            val druga = if (jasne) kolor(0x334155) else kolor(0xe2e8f0)
            fun p(akcent: Long?, medal: Long, obwodka: Long, ikona: Ikona, rok: String = "") =
                PaletaBelki(kolor(hex), akcent?.let { kolor(it) }, data, druga, kolor(medal), kolor(obwodka), ikona, rok)
            if (svg) return p(0xd4af37, 0x0a1f44, 0xd4af37, Ikona.BRAK)
            return when (id) {
                "leszno-ratusz-11listopada", "powstanczy-biwak" -> p(0xdc143c, 0xdc143c, 0x9e0b22, Ikona.ROZETA)
                "leszno-zima" -> p(0x0284c7, 0x0284c7, 0x7dd3fc, Ikona.SNIEZYNKA)
                "leszno-mikolajki" -> p(0xb91830, 0x991b2e, 0xf5d287, Ikona.CZAPKA)
                "leszno-wigilia" -> p(0xd4af37, 0x7a1220, 0xf5de9e, Ikona.GWIAZDA)
                "leszno-swieta" -> p(0xc8a45d, 0x7a1220, 0xe5c98b, Ikona.CHOINKA)
                "leszno-sylwester" -> p(0xd4af37, 0x0a162c, 0xd4af37, Ikona.GWIAZDA)
                "leszno-nowy-rok" -> p(0xd4af37, 0x0a162c, 0xffecaf, Ikona.ROK, (teraz.year % 100).toString())
                "leszno-andrzejki" -> p(0xf5b942, 0x5b21b6, 0xf5b942, Ikona.SWIECA)
                else -> p(null, 0xf5a623, 0xe8890a, Ikona.KALENDARZ)
            }
        }

        /**
         * Linie pod datą jak na stronie (TvmlSeasonal.dateBar), rozdzielone, żeby nic nie ucinało się w pół słowa:
         * wyróżnienie (święto z kalendarza albo przedrostek motywu) i imieniny.
         */
        fun linie(
            id: String, svg: Boolean, swieto: String?, imieniny: String?, teraz: ZonedDateTime = ZonedDateTime.now(Daty.strefa), pasek: PasekDaty? = null,
            dataImienin: java.time.LocalDate? = null,
        ): Pair<String?, String?> {
            // API 1.8.3: przedrostek, imieniny jutro i ukrycie imienin z danych motywu; dateBar.days[] ma wpis na każdy
            // dzień etapu (data w Warszawie), więc także poza siecią po północy tekst jest na właściwy dzień
            if (pasek != null) {
                val dzien = pasek.dni[teraz.toLocalDate().toString()]
                val przedrostek = if (dzien != null) dzien.przedrostek else pasek.przedrostek
                val jutro = if (dzien != null) dzien.jutro else pasek.jutro
                val bez = dzien?.bezImienin ?: pasek.bezImienin
                val imiona = when {
                    bez -> null
                    jutro != null -> jutro.replaceFirstChar { it.titlecase(Jezyk.locale) }
                    // config.today jest z innego dnia (aplikacja bez sieci po północy): imiona z linii dnia etapu
                    dzien != null && dataImienin != null && dataImienin != teraz.toLocalDate() -> imionaZLinii(dzien.linia) ?: imieniny
                    else -> imieniny
                }
                // jak strona (weather-effects.js, pasek motywu) i iOS 9e341c2: w dniach motywu przedrostek dnia, a święto
                // z kalendarza tylko gdy przedrostek pusty (1.12 „Zima w Lesznie”, nie „Światowy Dzień AIDS”)
                return (przedrostek?.takeIf { it.isNotBlank() } ?: swieto) to imiona
            }
            val m = teraz.monthValue
            val d = teraz.dayOfMonth
            val nastepny = if (m == 1) teraz.year else teraz.year + 1
            var przedrostek: String? = null
            var imiona = imieniny
            if (!svg) when (id) {
                "leszno-ratusz-11listopada" -> przedrostek = if (m == 11 && d == 11) "Święto Niepodległości" else null
                "leszno-zima" -> przedrostek = "Zima w Lesznie"
                "leszno-mikolajki" -> przedrostek = "Mikołajki w Lesznie"
                "leszno-wigilia" -> przedrostek = "Wigilia Bożego Narodzenia"
                "leszno-swieta" -> przedrostek = if (m == 12 && (d == 25 || d == 26)) "Boże Narodzenie" else "Świąteczne Leszno"
                "leszno-sylwester" -> przedrostek = if (m == 12 && d == 31) "Sylwester • Gala $nastepny" else "Odliczanie do $nastepny"
                "leszno-nowy-rok" -> {
                    przedrostek = "Nowy Rok $nastepny • Szczęśliwego Nowego Roku!"
                    imiona = null
                }
                "leszno-andrzejki" -> if (m == 11 && d == 29) {
                    przedrostek = "Wieczór andrzejkowy"
                    imiona = "Jutro imieniny: Andrzeja"
                } else {
                    przedrostek = if (m == 11 && d == 28) "Andrzejkowy weekend" else "Andrzejki"
                }
            }
            // święto z kalendarza ma pierwszeństwo (11 listopada: „Narodowe Święto Niepodległości” zamiast przedrostka)
            return (swieto ?: przedrostek) to imiona
        }
    }
}

/** „… • Imieniny: Andrzeja, Justyny” z dateBar.days[].line → „Imieniny: Andrzeja, Justyny” (etykieta w bieżącym języku). */
private fun imionaZLinii(linia: String): String? {
    val i = linia.indexOf("Imieniny:", ignoreCase = true)
    if (i < 0) return null
    val imiona = linia.substring(i + "Imieniny:".length).trim().ifEmpty { return null }
    return tl("app.nameDays", "Imieniny") + ": " + imiona
}

/** Medal na belce: koło w barwie sceny z ikoną (kalendarz, rozeta 11 Listopada, śnieżynka, czapka Mikołaja…). */
@Composable
fun MedalBelki(p: PaletaBelki) {
    Box(Modifier.size(34.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        when (p.ikona) {
            PaletaBelki.Ikona.KALENDARZ -> {
                Canvas(Modifier.size(34.dp)) {
                    val r = size.minDimension / 2
                    // poświata jak cień na iPhonie (#f5a623, 50 %)
                    drawCircle(Brush.radialGradient(listOf(kolor(0xf5a623).copy(alpha = 0.45f), Color.Transparent), center, r * 1.25f), r * 1.25f)
                    drawCircle(
                        Brush.radialGradient(listOf(kolor(0xffc04d), kolor(0xe8890a)), Offset(size.width * 0.38f, size.height * 0.38f), 22.dp.toPx()),
                        r,
                    )
                }
                Icon(Ikony.kalendarzMedal, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            PaletaBelki.Ikona.ROZETA -> Canvas(Modifier.size(34.dp)) {
                val r = size.minDimension / 2
                drawCircle(kolor(0xdc143c), r)
                // obwódka z przerywanej kreski 5 pt, wewnątrz koła (strokeBorder)
                val k = 5.dp.toPx()
                drawCircle(
                    kolor(0x9e0b22), r - k / 2,
                    style = Stroke(k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.6.dp.toPx(), 2.6.dp.toPx()))),
                )
                drawCircle(kolor(0x780616), r - 9.dp.toPx())
                drawCircle(Color.White, r - 10.5.dp.toPx())
                drawCircle(kolor(0xf8fafc), r - 14.dp.toPx())
                drawCircle(kolor(0xcbd5e1), r - 14.dp.toPx(), style = Stroke(0.8.dp.toPx()))
            }
            else -> {
                Canvas(Modifier.size(34.dp)) {
                    val r = size.minDimension / 2
                    drawCircle(p.medal, r)
                    drawCircle(p.obwodka, r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                }
                when (p.ikona) {
                    PaletaBelki.Ikona.SNIEZYNKA -> Icon(Ikony.sniezynka, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    // jak gift.fill na iPhonie: prezent w kolorze #e11d48 na bordowym medalu
                    PaletaBelki.Ikona.CZAPKA -> Icon(Ikony.prezent, null, tint = kolor(0xe11d48), modifier = Modifier.size(18.dp))
                    PaletaBelki.Ikona.GWIAZDA -> Icon(Ikony.gwiazdaPelna, null, tint = kolor(0xffe88a), modifier = Modifier.size(17.dp))
                    PaletaBelki.Ikona.CHOINKA -> Icon(Ikony.choinka, null, tint = kolor(0x16a34a), modifier = Modifier.size(18.dp))
                    PaletaBelki.Ikona.SWIECA -> Icon(Ikony.plomien, null, tint = kolor(0xf5b942), modifier = Modifier.size(17.dp))
                    PaletaBelki.Ikona.ROK -> Text(p.rok, color = kolor(0xffecaf), fontSize = 14.sp, fontWeight = FontWeight.Black)
                    else -> {}
                }
            }
        }
    }
}
