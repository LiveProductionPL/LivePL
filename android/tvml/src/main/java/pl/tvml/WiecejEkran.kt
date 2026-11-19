package pl.tvml

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Więcej: zgłoszenie zdarzenia, kafle działów, zapisane artykuły, wygląd, rozmiar tekstu, strony redakcji
 * i media społecznościowe. Sondy, newsletter i powiadomienia wejdą z kolejnym krokiem API (zapis, Play Integrity, FCM).
 */
@Composable
fun WiecejEkran(m: Magazyn) {
    val b = LocalBarwy.current
    val kontekst = LocalContext.current
    var rozwiniete by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(b.tlo).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Naglowek(m.zakladki.firstOrNull { it.cel == ZakladkaApp.Cel.Wiecej }?.tytul?.ifEmpty { null } ?: "Więcej", wielkosc = 24)
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(kolor(0xc8102e))
                .clickable(role = Role.Button) { otworzStrone(kontekst, Siec.BAZA) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ikony.alarm, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text("  Zgłoś zdarzenie (Alert TVML)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Text("Formularz jest na stronie głównej tvml.pl, przycisk „Zgłoś zdarzenie”.", color = b.slaby, fontSize = 12.sp)

        // kafle jak w projekcie: trzy w rzędzie
        val kafle = listOf(
            Kafel("Gminy powiatu", "G", 0xe7eefb, 0x0a3a8c) { m.otworz(Ekran.Gminy) },
            Kafel("Ogłoszenia", "O", 0xfdf1dc, 0x8a5400) { otworzStrone(kontekst, Siec.BAZA + "lista/ogloszenia") },
            Kafel("Fotorelacje", "F", 0xe6f4ee, 0x0d7351) { m.otworz(Ekran.Lista("Fotorelacje", "articles", mapOf("photoReport" to "1", "limit" to "20"))) },
            Kafel("Najczęściej czytane", "10", 0xf3e8ff, 0x6b21a8) { m.otworz(Ekran.Lista("Najczęściej czytane", "articles/most-read", mapOf("limit" to "20"))) },
            Kafel("Zapisane" + if (m.zapisane.isEmpty()) "" else " (${m.zapisane.size})", "★", 0xe0f2fe, 0x0369a1) { m.otworz(Ekran.Zapisane) },
            Kafel("Artykuły sponsorowane", "S", 0xfee2e2, 0x9f0404) { otworzStrone(kontekst, Siec.BAZA + "lista/aktualnosci/artykuly-sponsorowane") },
        )
        kafle.chunked(3).forEach { rzad ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rzad.forEach { k -> KafelWiecej(k, Modifier.weight(1f)) }
            }
        }

        Karta {
            Wiersz("Wygląd", when (m.wyglad) { "dzien" -> "Dzień"; "noc" -> "Noc"; else -> "Jak w telefonie" }, pierwszy = true) {
                rozwiniete = if (rozwiniete == "wyglad") null else "wyglad"
            }
            if (rozwiniete == "wyglad") Wybor(listOf("auto" to "Jak w telefonie", "dzien" to "Dzień", "noc" to "Noc"), m.wyglad) { m.ustawWyglad(it) }
            Wiersz("Rozmiar tekstu", listOf("Mały", "Średni", "Duży")[m.rozmiar]) {
                rozwiniete = if (rozwiniete == "rozmiar") null else "rozmiar"
            }
            if (rozwiniete == "rozmiar") Wybor(listOf("0" to "Mały", "1" to "Średni", "2" to "Duży"), m.rozmiar.toString()) { m.ustawRozmiar(it.toInt()) }
        }

        Naglowek("TVML", wielkosc = 18)
        Karta {
            m.strony.forEachIndexed { i, s ->
                Wiersz(s.tytul, "", pierwszy = i == 0) { otworzStrone(kontekst, Siec.BAZA + "strona/" + s.slug) }
            }
        }

        Naglowek("Obserwuj nas", wielkosc = 18)
        Spolecznosc()

        Text(
            "TVML ${BuildConfig.VERSION_NAME} · Telewizja Młodzieżowa Leszno",
            Modifier.fillMaxWidth().padding(vertical = 12.dp), color = b.slaby, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
    }
}

private class Kafel(val nazwa: String, val znak: String, val tlo: Long, val kolor: Long, val akcja: () -> Unit)

@Composable
private fun KafelWiecej(k: Kafel, modifier: Modifier) {
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    Column(
        modifier.heightIn(min = 78.dp).clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = k.akcja).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (ciemny) kolor(k.kolor).copy(alpha = 0.25f) else kolor(k.tlo)),
            contentAlignment = Alignment.Center,
        ) {
            Text(k.znak, color = if (ciemny) kolor(k.tlo) else kolor(k.kolor), fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Text(k.nazwa, color = b.tekst, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 15.sp)
    }
}

@Composable
private fun Karta(tresc: @Composable () -> Unit) {
    val b = LocalBarwy.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp))) { tresc() }
}

@Composable
private fun Wiersz(nazwa: String, wartosc: String, pierwszy: Boolean = false, akcja: () -> Unit) {
    val b = LocalBarwy.current
    if (!pierwszy) Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = akcja).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(nazwa, Modifier.weight(1f), color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (wartosc.isNotEmpty()) Text(wartosc, color = b.slaby, fontSize = 13.sp)
        Icon(Ikony.dalej, null, tint = b.slaby, modifier = Modifier.padding(start = 6.dp).size(16.dp))
    }
}

@Composable
private fun Wybor(opcje: List<Pair<String, String>>, wybrana: String, wybierz: (String) -> Unit) {
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opcje.forEach { (klucz, nazwa) ->
            val tak = klucz == wybrana
            Box(
                Modifier.weight(1f).height(38.dp).clip(CircleShape)
                    .background(if (tak) m.motyw.akcent.w(ciemny) else b.tlo)
                    .clickable(role = Role.RadioButton) { wybierz(klucz) },
                contentAlignment = Alignment.Center,
            ) {
                Text(nazwa, color = if (tak) m.motyw.napisNaAkcencie.w(ciemny) else b.tekst, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Szukaj na tvml.pl: wyniki otwierają się w bieżącej zakładce. */
@Composable
fun SzukajArkusz(m: Magazyn) {
    val b = LocalBarwy.current
    val zakres = rememberCoroutineScope()
    val klawiatura = LocalSoftwareKeyboardController.current
    val fokus = remember { FocusRequester() }
    var fraza by remember { mutableStateOf("") }
    var wyniki by remember { mutableStateOf<List<Artykul>?>(null) }
    var szuka by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { fokus.requestFocus() } }
    Arkusz("Szukaj", { m.arkuszSzukaj = false }, przycisk = "Zamknij") {
        OutlinedTextField(
            fraza, { fraza = it },
            Modifier.fillMaxWidth().focusRequester(fokus),
            placeholder = { Text("Szukaj na tvml.pl") },
            leadingIcon = { Icon(Ikony.szukaj, null, Modifier.size(20.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = b.tekst, unfocusedTextColor = b.tekst, focusedBorderColor = b.link, unfocusedBorderColor = b.ramka, cursorColor = b.link),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                klawiatura?.hide()
                zakres.launch { szuka = true; wyniki = m.szukaj(fraza.trim()); szuka = false }
            }),
        )
        when {
            szuka -> Postep()
            wyniki?.isEmpty() == true -> Text("Nic nie znaleziono.", color = b.slaby, fontSize = 15.sp, modifier = Modifier.padding(vertical = 16.dp))
            else -> wyniki.orEmpty().forEach { a ->
                Column(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { m.arkuszSzukaj = false; m.otworz(a) }.padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(a.tytul, color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOf(a.kategoria, a.kiedy).filter { it.isNotEmpty() }.joinToString(" · "), color = b.slaby, fontSize = 12.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
            }
        }
    }
}
