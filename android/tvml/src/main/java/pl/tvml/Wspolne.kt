package pl.tvml

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage

private val szarosc = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Obrazek z sieci; w trybie żałobnym (albo gdy panel tak ustawi) w odcieniach szarości. */
@Composable
fun Obraz(adres: String?, modifier: Modifier = Modifier) {
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    AsyncImage(
        model = adres,
        contentDescription = null,
        modifier = modifier.background(b.ramka),
        contentScale = ContentScale.Crop,
        colorFilter = if (m.motyw.szarosc) szarosc else null,
    )
}

/** Tytuł sekcji szeryfowy, jak na stronie. */
@Composable
fun Naglowek(tytul: String, modifier: Modifier = Modifier, wielkosc: Int = 21) {
    Text(
        tytul, modifier.padding(top = 4.dp).semantics { heading() },
        color = LocalBarwy.current.tekst, fontSize = wielkosc.sp, fontWeight = FontWeight.Black, fontFamily = KrojNaglowkow,
    )
}

@Composable
fun EtykietaKategorii(a: Artykul) {
    Text(
        a.kategoria.uppercase(),
        Modifier.clip(RoundedCornerShape(8.dp)).background(kolor(a.kolor, Barwy.zloto)).padding(horizontal = 10.dp, vertical = 5.dp),
        color = Kontrast.napis(a.kolor), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
    )
}

/** Duża karta: zdjęcie, kategoria, tytuł, autor. */
@Composable
fun KartaArtykulu(a: Artykul, wysokosc: Dp, modifier: Modifier = Modifier) {
    val m = LocalMagazyn.current
    Box(
        modifier.fillMaxWidth().height(wysokosc).clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) { m.otworz(a) },
        contentAlignment = Alignment.BottomStart,
    ) {
        Obraz(a.miniatura(900), Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.25f to Color.Transparent, 1f to Color(0.02f, 0.05f, 0.11f, 0.92f)),
            ),
        )
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a.kategoria.isNotEmpty()) EtykietaKategorii(a)
            Text(a.tytul, color = Color.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, fontFamily = KrojNaglowkow, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text(listOf(a.autor, a.kiedy).filter { it.isNotEmpty() }.joinToString(" · "), color = kolor(0xd7deea), fontSize = 12.sp)
        }
    }
}

@Composable
fun WierszArtykulu(a: Artykul, modifier: Modifier = Modifier) {
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) { m.otworz(a) }.padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Obraz(a.miniatura(300), Modifier.size(92.dp, 70.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (a.kategoria.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(kolor(a.kolor, Barwy.zloto)))
                Text(a.kategoria.uppercase(), color = b.slaby, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, maxLines = 1)
            }
            Text(a.tytul, color = b.tekst, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(a.kiedy, color = b.slaby, fontSize = 12.sp)
        }
    }
}

/** „‹ Wróć” nad listami otwartymi w zakładce. */
@Composable
fun Wroc() {
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { m.wroc() }.padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(Ikony.wstecz, null, tint = b.tekst, modifier = Modifier.size(20.dp))
        Text(tl("app.back", "Wróć"), color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Postep() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = LocalMagazyn.current.motyw.akcent.w(LocalCiemny.current))
    }
}

@Composable
fun Odstep(h: Dp) = Spacer(Modifier.height(h))

/** Strona tvml.pl (formularz zgłoszenia, ogłoszenia, regulamin) w karcie przeglądarki, jak Safari na iPhonie. */
fun otworzStrone(kontekst: Context, adres: String) {
    val uri = adres.toUri()
    try {
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF0A1F44.toInt()).build())
            .setShowTitle(true)
            .build()
            .launchUrl(kontekst, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            kontekst.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

/** Adres spoza aplikacji (inne strony, poczta, telefon, YouTube) w aplikacji systemowej albo domyślnej przeglądarce. */
fun otworzWSystemie(kontekst: Context, adres: android.net.Uri) {
    try {
        kontekst.startActivity(Intent(Intent.ACTION_VIEW, adres))
    } catch (_: ActivityNotFoundException) {
    }
}

fun udostepnij(kontekst: Context, tytul: String, adres: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, tytul)
        putExtra(Intent.EXTRA_TEXT, "$tytul\n$adres")
    }
    kontekst.startActivity(Intent.createChooser(i, tl("app.share", "Udostępnij")))
}
