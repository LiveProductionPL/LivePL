package eu.livepl.przegladarka

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Kolory „Reżyserki”: te same co w styl.css wersji webowej, powloka.css na Windows i Kolory.swift na iPhonie. */
@Immutable
data class Paleta(
    val tlo: Color,
    val karta: Color,
    val tekst: Color,
    val slaby: Color,
    val bardzoSlaby: Color,
    val ramka: Color,
    val wcisniety: Color,
    val czerwony: Color,
    val czerwonyTlo: Color,
    val ok: Color,
    val noc: Boolean,
)

val PaletaDzien = Paleta(
    tlo = Color(0xFFE9ECEF),
    karta = Color(0xFFFFFFFF),
    tekst = Color(0xFF1D2733),
    slaby = Color(0xFF6B7886),
    bardzoSlaby = Color(0xFF98A3AE),
    ramka = Color(0xFFD3D9DF),
    wcisniety = Color(0xFF142332).copy(alpha = 0.09f),
    czerwony = Color(0xFFB0261F),
    czerwonyTlo = Color(0xFFB0261F).copy(alpha = 0.08f),
    ok = Color(0xFF1F8A4C),
    noc = false,
)

val PaletaNoc = Paleta(
    tlo = Color(0xFF0D0F12),
    karta = Color(0xFF16191E),
    tekst = Color(0xFFE6E9EE),
    slaby = Color(0xFF8B95A1),
    bardzoSlaby = Color(0xFF5E6874),
    ramka = Color(0xFF252A31),
    wcisniety = Color.White.copy(alpha = 0.1f),
    czerwony = Color(0xFFE3463A),
    czerwonyTlo = Color(0xFFE3463A).copy(alpha = 0.12f),
    ok = Color(0xFF45C486),
    noc = true,
)

val LocalPaleta = staticCompositionLocalOf { PaletaDzien }

/** Skrót do kolorów w widokach: Kolor.tekst, Kolor.czerwony… */
object Kolor {
    val tlo: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.tlo
    val karta: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.karta
    val tekst: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.tekst
    val slaby: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.slaby
    val bardzoSlaby: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.bardzoSlaby
    val ramka: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.ramka
    val wcisniety: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.wcisniety
    val czerwony: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.czerwony
    val czerwonyTlo: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.czerwonyTlo
    val ok: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.ok
}

/**
 * Motyw LivePL na Material 3. Dzień i noc wynikają z konfiguracji aktywności, którą
 * MainActivity ustawia według przełącznika przy zegarze (auto, dzień, noc).
 */
@Composable
fun LivePLMotyw(tresc: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) PaletaNoc else PaletaDzien
    val wspolne = { s: androidx.compose.material3.ColorScheme ->
        s.copy(
            primary = p.czerwony,
            onPrimary = Color.White,
            primaryContainer = p.czerwonyTlo,
            onPrimaryContainer = p.czerwony,
            secondary = p.slaby,
            onSecondary = Color.White,
            secondaryContainer = p.wcisniety,
            onSecondaryContainer = p.tekst,
            tertiary = p.ok,
            background = p.tlo,
            onBackground = p.tekst,
            surface = p.karta,
            onSurface = p.tekst,
            surfaceVariant = p.tlo,
            onSurfaceVariant = p.slaby,
            surfaceTint = Color.Transparent,
            surfaceBright = p.karta,
            surfaceDim = p.tlo,
            surfaceContainerLowest = p.karta,
            surfaceContainerLow = p.karta,
            surfaceContainer = p.karta,
            surfaceContainerHigh = p.karta,
            surfaceContainerHighest = p.tlo,
            inverseSurface = p.tekst,
            inverseOnSurface = p.tlo,
            outline = p.ramka,
            outlineVariant = p.ramka,
            error = p.czerwony,
            onError = Color.White,
            errorContainer = p.czerwonyTlo,
            onErrorContainer = p.czerwony,
        )
    }
    val schemat = wspolne(if (p.noc) darkColorScheme() else lightColorScheme())
    MaterialTheme(colorScheme = schemat) {
        CompositionLocalProvider(LocalPaleta provides p) { tresc() }
    }
}

/** Biała (nocą grafitowa) karta z cienką ramką, jak kafelki w Reżyserce. */
@Composable
fun Modifier.kartaLivePL(promien: Dp = 16.dp): Modifier {
    val ksztalt = RoundedCornerShape(promien)
    return this
        .background(Kolor.karta, ksztalt)
        .border(BorderStroke(1.dp, Kolor.ramka), ksztalt)
}
