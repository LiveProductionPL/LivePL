package pl.tvml

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

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

/** Tytuły jak na stronie (Playfair Display): szeryfowe. */
val Szeryf = FontFamily.Serif
