package pl.tvml

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cała aplikacja: baner nad każdym ekranem, zakładki z panelu i arkusze (pogoda, wideo, szukaj). */
@Composable
fun Glowny(m: Magazyn) {
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    val stany = rememberSaveableStateHolder()
    val sciezka = m.sciezki[m.zakladka].orEmpty()
    val startId = m.zakladki.firstOrNull { it.cel == ZakladkaApp.Cel.Start }?.id

    // Wstecz: najpierw zamyka otwarty ekran zakładki, potem wraca na Start, a ze Startu wychodzi z aplikacji
    BackHandler(enabled = sciezka.isNotEmpty() || (startId != null && m.zakladka != startId)) {
        if (!m.wroc()) m.naStart()
    }

    Column(Modifier.fillMaxSize().background(b.tlo)) {
        Baner(m)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val z = m.zakladki.firstOrNull { it.id == m.zakladka } ?: m.zakladki.first()
            val gora = sciezka.lastOrNull()
            // każdy ekran ma własny zapamiętany stan (przewinięcie), także po powrocie z artykułu
            stany.SaveableStateProvider("${z.id}/${sciezka.size}/${gora?.let { klucz(it) } ?: "glowny"}") {
                if (gora == null) TrescZakladki(m, z) else EkranZakladki(m, gora)
            }
        }
        NavigationBar(containerColor = b.karta, tonalElevation = 0.dp) {
            m.zakladki.forEach { z ->
                val wybrana = z.id == m.zakladka
                NavigationBarItem(
                    selected = wybrana,
                    onClick = {
                        // stuknięcie w bieżącą zakładkę wraca do jej początku
                        if (wybrana) m.sciezki[z.id] = emptyList() else m.zakladka = z.id
                    },
                    icon = { Icon(Ikony.zakladki(z.ikona), null, Modifier.size(24.dp)) },
                    label = { Text(z.tytul, fontSize = 11.sp, fontWeight = if (wybrana) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = m.motyw.akcent.w(ciemny),
                        selectedTextColor = m.motyw.akcent.w(ciemny),
                        indicatorColor = m.motyw.akcent.w(ciemny).copy(alpha = 0.12f),
                        unselectedIconColor = b.slaby,
                        unselectedTextColor = b.slaby,
                    ),
                )
            }
        }
    }

    if (m.arkuszPogody) Arkusz(tl("app.weather", "Pogoda"), { m.arkuszPogody = false }, przewijany = false) { Box(Modifier.fillMaxHeight(0.92f)) { PogodaEkran(m) } }
    if (m.arkuszWideo) Arkusz(tl("app.video", "Wideo"), { m.arkuszWideo = false }, przewijany = false) { Box(Modifier.fillMaxHeight(0.92f)) { WideoEkran(m) } }
    if (m.arkuszSzukaj) SzukajArkusz(m)
}

private fun klucz(e: Ekran): String = when (e) {
    is Ekran.Tekst -> "a${e.artykul.id}"
    is Ekran.ZGminy -> "g${e.gmina.id}"
    Ekran.Gminy -> "gminy"
    Ekran.Zapisane -> "zapisane"
    is Ekran.Lista -> "l${e.trasa}${e.parametry}"
    is Ekran.Strona -> "s${e.slug}"
    Ekran.Ogloszenia -> "ogloszenia"
    is Ekran.JednoWydarzenie -> "w${e.wydarzenie.id}"
}

@Composable
private fun TrescZakladki(m: Magazyn, z: ZakladkaApp) {
    when (val c = z.cel) {
        ZakladkaApp.Cel.Start -> StartEkran(m)
        ZakladkaApp.Cel.Wideo -> WideoEkran(m)
        ZakladkaApp.Cel.Pogoda -> PogodaEkran(m)
        ZakladkaApp.Cel.Wiecej -> WiecejEkran(m)
        is ZakladkaApp.Cel.Kategoria -> ListaKategorii(m, c.slug, z.tytul)
    }
}

@Composable
private fun EkranZakladki(m: Magazyn, e: Ekran) {
    when (e) {
        is Ekran.Tekst -> ArtykulEkran(m, e.artykul)
        is Ekran.ZGminy -> ListaArtykulow(e.gmina.nazwa, e.gmina.herb, tl("app.noNewsCommune", "Brak wiadomości z tej gminy.")) { m.gmina(e.gmina) }
        Ekran.Gminy -> ListaGmin(m)
        Ekran.Zapisane -> ListaArtykulow(tl("app.saved", "Zapisane"), pusta = tl("app.savedEmpty", "Nie masz zapisanych artykułów. Zapiszesz je ikoną zakładki w artykule.")) {
            m.zapisane.take(30).mapNotNull { m.artykul(it) }
        }
        is Ekran.Lista -> ListaArtykulow(e.tytul) { m.lista(e.trasa, e.parametry) }
        is Ekran.Strona -> PodstronaEkran(m, e.slug)
        Ekran.Ogloszenia -> OgloszeniaEkran(m)
        is Ekran.JednoWydarzenie -> WydarzenieEkran(m, e.wydarzenie)
    }
}

/** Arkusz od dołu z tytułem i przyciskiem „Gotowe”, jak .sheet na iPhonie. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Arkusz(tytul: String, zamknij: () -> Unit, przycisk: String = tl("app.done", "Gotowe"), przewijany: Boolean = true, tresc: @Composable ColumnScope.() -> Unit) {
    val b = LocalBarwy.current
    val stan = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = zamknij, sheetState = stan, containerColor = b.tlo, contentColor = b.tekst, dragHandle = null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tytul, Modifier.weight(1f), color = b.tekst, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    przycisk,
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = zamknij).padding(horizontal = 12.dp, vertical = 12.dp),
                    color = b.link, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Column(
                Modifier.fillMaxWidth().then(if (przewijany) Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(if (przewijany) 4.dp else 0.dp),
                content = tresc,
            )
            if (przewijany) Box(Modifier.height(16.dp))
        }
    }
}
