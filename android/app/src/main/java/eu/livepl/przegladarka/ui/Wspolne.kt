package eu.livepl.przegladarka.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.livepl.przegladarka.Kolor
import eu.livepl.przegladarka.Program

/** Nagłówek sekcji Reżyserki: małe szare wersaliki. */
@Composable
fun TytulSekcji(tekst: String, modifier: Modifier = Modifier) {
    Text(
        tekst.uppercase(),
        modifier = modifier.padding(start = 2.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        color = Kolor.slaby,
    )
}

/** Kwadracik z pierwszą literą nazwy, jak w wersji webowej. */
@Composable
fun Literka(tekst: String) {
    Box(
        Modifier
            .size(34.dp)
            .background(Kolor.czerwonyTlo, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tekst.trim().take(1).uppercase(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Kolor.czerwony,
        )
    }
}

/** Grafika programu (Poczta, Live Production, TVML) albo ikona; szerokie logotypy mają [szerokie] × wysokość. */
@Composable
fun IkonaProgramu(program: Program, rozmiar: Dp = 24.dp, szerokie: Float = 2f) {
    val obrazek = program.obrazek
    if (obrazek != null) {
        Image(
            painterResource(obrazek),
            contentDescription = null,
            modifier = Modifier.size(width = if (program.szeroki) rozmiar * szerokie else rozmiar, height = rozmiar),
            contentScale = ContentScale.Fit,
        )
    } else {
        Icon(
            painterResource(program.symbol),
            contentDescription = null,
            tint = Kolor.tekst,
            modifier = Modifier.size(rozmiar),
        )
    }
}

/** Mały przycisk w kolorze LivePL (jak .bordered .small na iPhonie). */
@Composable
fun MalyPrzycisk(tekst: String, @DrawableRes ikona: Int? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Kolor.czerwonyTlo,
            contentColor = Kolor.czerwony,
        ),
    ) {
        if (ikona != null) {
            Icon(painterResource(ikona), contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(tekst, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Ikona(@DrawableRes ikona: Int, opis: String?, kolor: Color, rozmiar: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(ikona), contentDescription = opis, tint = kolor, modifier = modifier.size(rozmiar))
}
