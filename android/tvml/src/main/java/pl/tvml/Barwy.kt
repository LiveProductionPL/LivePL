package pl.tvml

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Barwy aplikacji na dzień i noc, jak Barwy w Dane.swift na iPhonie. */
@Immutable
data class Barwy(
    val tlo: Color,
    val karta: Color,
    val tekst: Color,
    val slaby: Color,
    val ramka: Color,
    val link: Color,
) {
    companion object {
        val granat = kolor(0x0a1f44)
        val zloto = kolor(0xe29818)
        val dzien = Barwy(kolor(0xeef1f6), kolor(0xffffff), kolor(0x0b1830), kolor(0x4f5a6e), kolor(0xdde3ed), kolor(0x0a3a8c))
        val noc = Barwy(kolor(0x0b1220), kolor(0x131c2e), kolor(0xe8ecf4), kolor(0xa8b3c8), kolor(0x22304a), kolor(0xf0b03a))
    }
}

val LocalBarwy = staticCompositionLocalOf { Barwy.dzien }
val LocalCiemny = staticCompositionLocalOf { false }
val LocalMagazyn = compositionLocalOf<Magazyn> { error("brak magazynu") }

// Nagłówki i tytuły jak na tvml.pl (h1/h2: Open Sans 800) i na iPhonie: Open Sans (zmienna czcionka, SIL OFL,
// licencja w assets/licencje/OpenSans-OFL.txt), wcześniej szeryfowy (Karol 24.11: „Nagłówki też kiepski krój czcionki”).
// Open Sans ma najwyżej 800, więc Black rysuje się jak ExtraBold.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val KrojNaglowkow = FontFamily(
    listOf(400 to FontWeight.Normal, 600 to FontWeight.SemiBold, 700 to FontWeight.Bold, 800 to FontWeight.ExtraBold, 800 to FontWeight.Black)
        .map { (os, waga) ->
            androidx.compose.ui.text.font.Font(
                R.font.open_sans, waga,
                variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(os)),
            )
        },
)
