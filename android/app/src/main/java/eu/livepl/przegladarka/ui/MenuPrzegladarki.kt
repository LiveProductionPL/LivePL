package eu.livepl.przegladarka.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.livepl.przegladarka.BuildConfig
import eu.livepl.przegladarka.Kolor
import eu.livepl.przegladarka.MojaStrona
import eu.livepl.przegladarka.Motyw
import eu.livepl.przegladarka.Przegladarka
import eu.livepl.przegladarka.R
import eu.livepl.przegladarka.Urzadzenie
import java.text.DateFormat
import java.util.Date

/** Przycisk ⋮ menu przeglądarki w nagłówku ekranu Start (jak ⋮ w LivePL na Windows). */
@Composable
fun PrzyciskMenuPrzegladarki(p: Przegladarka) {
    var otwarte by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { otwarte = true }) {
            Ikona(R.drawable.ic_menu, "Menu przeglądarki", Kolor.tekst, 24.dp)
        }
        DropdownMenu(expanded = otwarte, onDismissRequest = { otwarte = false }) {
            PozycjeMenuPrzegladarki(p) { otwarte = false }
        }
    }
}

/** Pozycje menu przeglądarki: na ekranie Start i w podmenu „Przeglądarka” otwartej strony. */
@Composable
fun PozycjeMenuPrzegladarki(p: Przegladarka, zamknij: () -> Unit) {
    PozycjaMenu("Nowa karta", R.drawable.ic_nowa_karta) {
        zamknij()
        p.nowaKarta()
    }
    PozycjaMenu("Otwarte karty", R.drawable.ic_karty) {
        zamknij()
        p.pokazListeKart = true
    }
    PozycjaMenu("Dodaj stronę do Moich stron", R.drawable.ic_dodaj) {
        zamknij()
        p.dodawanaStrona = MojaStrona("", "")
    }
    HorizontalDivider(color = Kolor.ramka)
    Text(
        "Wygląd",
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Kolor.slaby,
    )
    Motyw.entries.forEach { motyw ->
        val wybrany = p.motyw == motyw
        DropdownMenuItem(
            text = { Text(motyw.nazwa, fontWeight = if (wybrany) FontWeight.SemiBold else FontWeight.Normal) },
            leadingIcon = { Ikona(motyw.ikona, null, Kolor.tekst, 20.dp) },
            trailingIcon = if (wybrany) ({ Ikona(R.drawable.ic_ok, "wybrany", Kolor.czerwony, 20.dp) }) else null,
            onClick = {
                zamknij()
                if (!wybrany) p.ustawMotyw(motyw)
            },
        )
    }
    HorizontalDivider(color = Kolor.ramka)
    PozycjaMenu("Certyfikat LivePL dla telefonu", R.drawable.ic_tarcza) {
        zamknij()
        p.pokazCertyfikat = true
    }
    PozycjaMenu("Urządzenie LivePL", R.drawable.ic_telefon) {
        zamknij()
        p.pokazUrzadzenie = true
    }
    PozycjaMenu("O programie", R.drawable.ic_informacje) {
        zamknij()
        p.pokazInformacje = true
    }
    DropdownMenuItem(
        text = { Text("Wyczyść dane przeglądania…", color = Kolor.czerwony) },
        leadingIcon = { Ikona(R.drawable.ic_usun, null, Kolor.czerwony, 20.dp) },
        onClick = {
            zamknij()
            p.pokazCzyszczenie = true
        },
    )
}

@Composable
fun PozycjaMenu(tekst: String, @DrawableRes ikona: Int, wlaczona: Boolean = true, akcja: () -> Unit) {
    DropdownMenuItem(
        text = { Text(tekst) },
        leadingIcon = { Ikona(ikona, null, if (wlaczona) Kolor.tekst else Kolor.bardzoSlaby, 20.dp) },
        enabled = wlaczona,
        onClick = akcja,
    )
}

/** Okna otwierane z menu przeglądarki; podpięte raz, w głównym widoku. */
@Composable
fun OknaMenuPrzegladarki(p: Przegladarka) {
    if (p.pokazCertyfikat) {
        // LivePL ufa naszym certyfikatom sam (res/xml/siec.xml). Certyfikat w całym telefonie przyda się tylko innym aplikacjom.
        AlertDialog(
            onDismissRequest = { p.pokazCertyfikat = false },
            title = { Text("Certyfikat dla całego telefonu") },
            text = {
                Text(
                    "LivePL ufa certyfikatom LivePL sam i niczego nie trzeba instalować. Certyfikat w telefonie " +
                        "przyda się tylko innym aplikacjom, np. programowi pocztowemu przy podpisanych mailach.\n\n" +
                        "Android pozwala go dodać w Ustawieniach (zwykle: Bezpieczeństwo → Szyfrowanie i dane logowania → " +
                        "Zainstaluj certyfikat → Certyfikat CA). Instrukcja krok po kroku jest na cert.livepl.eu.",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    p.pokazCertyfikat = false
                    p.otworz("https://cert.livepl.eu/instalacja#android")
                }) { Text("Otwórz instrukcję") }
            },
            dismissButton = { TextButton(onClick = { p.pokazCertyfikat = false }) { Text("Anuluj") } },
        )
    }

    if (p.pokazCzyszczenie) {
        AlertDialog(
            onDismissRequest = { p.pokazCzyszczenie = false },
            title = { Text("Wyczyścić dane przeglądania?") },
            text = {
                Text("Wylogujesz się ze wszystkich stron, także z Poczty, a otwarte karty się zamkną. Moje strony i wygląd zostają.")
            },
            confirmButton = {
                TextButton(onClick = {
                    p.pokazCzyszczenie = false
                    p.wyczyscDane()
                }) { Text("Wyczyść i zamknij karty", color = Kolor.czerwony) }
            },
            dismissButton = { TextButton(onClick = { p.pokazCzyszczenie = false }) { Text("Anuluj") } },
        )
    }

    if (p.pokazInformacje) OProgramie(p)
    if (p.pokazUrzadzenie) OknoUrzadzenia(p)
}

/** „Urządzenie LivePL”: stan rejestracji tego telefonu w urzędzie LivePL. */
@Composable
private fun OknoUrzadzenia(p: Przegladarka) {
    val kontekst = LocalContext.current
    val u = remember { Urzadzenie.dla(kontekst) }
    val z = u.zapis
    val zamknij = { p.pokazUrzadzenie = false }
    val (tytul, opis, ikona, kolor) = when (z.stan) {
        "gotowe" -> Quad("Zarejestrowany w urzędzie LivePL",
            "Ten telefon ma certyfikat „Urządzenie LivePL”, a jego klucz jest w sprzęcie telefonu, jak TPM w komputerze.",
            R.drawable.ic_tarcza, Kolor.ok)
        "wyslany" -> Quad("Wniosek wysłany",
            "Urząd jeszcze nie wystawił certyfikatu. LivePL sprawdzi to sam przy następnym otwarciu.",
            R.drawable.ic_telefon, Kolor.slaby)
        "odrzucony" -> Quad("Urząd odrzucił wniosek",
            "Ten telefon nie jest zaufanym urządzeniem LivePL. Możesz zarejestrować go od nowa, z nowym kluczem.",
            R.drawable.ic_uwaga, Kolor.czerwony)
        "uniewazniony" -> Quad("Rejestracja unieważniona",
            "Ten telefon nie jest już zaufanym urządzeniem LivePL. Możesz zarejestrować go od nowa, z nowym kluczem.",
            R.drawable.ic_uwaga, Kolor.czerwony)
        else -> Quad("Niezarejestrowany",
            "LivePL rejestruje ten telefon sam przy uruchomieniu. Klucz powstaje w Android Keystore i nigdy go nie opuszcza. " +
                "Urząd dostaje nazwę urządzenia, model, wersję Androida i wersję LivePL.",
            R.drawable.ic_telefon, Kolor.slaby)
    }
    AlertDialog(
        onDismissRequest = zamknij,
        icon = { Ikona(ikona, null, kolor, 34.dp) },
        title = { Text(tytul, textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(opis, fontSize = 14.sp, lineHeight = 19.sp, color = Kolor.slaby)
                u.blad?.let { Text(it, fontSize = 14.sp, color = Kolor.czerwony) }
                if (z.kod != null && z.stan != "brak") {
                    Wiersz("Kod", z.kod)
                    Wiersz(
                        "Klucz",
                        when {
                            z.strongbox -> "StrongBox, nie do skopiowania"
                            z.sprzetowy -> "w sprzęcie telefonu (TEE), nie do skopiowania"
                            else -> "programowy (bez sprzętowego magazynu)"
                        },
                    )
                    z.waznyDo?.let { Wiersz("Ważny do", DateFormat.getDateInstance(DateFormat.LONG).format(Date(it))) }
                    z.raport?.let { Wiersz("Ostatni kontakt", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))) }
                    z.polaczenie?.let { Wiersz("Połączenie", it) }
                    Text("Ten sam kod widać w urzędzie LivePL, w „Urządzenia LivePL”.", fontSize = 12.sp, color = Kolor.bardzoSlaby)
                }
                if (u.trwa) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Rejestruję w urzędzie LivePL…", fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            when {
                u.trwa -> Unit
                z.stan == "brak" || z.stan == "odrzucony" || z.stan == "uniewazniony" ->
                    TextButton(onClick = { u.zarejestruj() }) { Text(if (z.stan == "brak") "Zarejestruj teraz" else "Zarejestruj ponownie") }
                z.stan == "wyslany" -> TextButton(onClick = { u.sprawdzTeraz() }) { Text("Sprawdź") }
                else -> TextButton(onClick = zamknij) { Text("Gotowe") }
            }
        },
        dismissButton = if (z.stan == "gotowe") null else ({ TextButton(onClick = zamknij) { Text("Zamknij") } }),
    )
}

private data class Quad(val tytul: String, val opis: String, @DrawableRes val ikona: Int, val kolor: androidx.compose.ui.graphics.Color)

@Composable
private fun Wiersz(nazwa: String, wartosc: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(nazwa, fontSize = 14.sp, color = Kolor.slaby)
        Spacer(Modifier.width(12.dp))
        Text(wartosc, modifier = Modifier.weight(1f), fontSize = 14.sp, color = Kolor.tekst, textAlign = TextAlign.End)
    }
}

@Composable
private fun OProgramie(p: Przegladarka) {
    fun otworz(adres: String) {
        p.pokazInformacje = false
        p.otworz(adres)
    }
    AlertDialog(
        onDismissRequest = { p.pokazInformacje = false },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(R.drawable.wstega), contentDescription = null, modifier = Modifier.size(88.dp, 64.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Kolor.tekst)) { append("Live") }
                            withStyle(SpanStyle(color = Kolor.czerwony)) { append("PL") }
                        },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Przeglądarka · wersja ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        fontSize = 14.sp,
                        color = Kolor.slaby,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    "Twoje dane (Moje strony, logowania, karty) zostają w tym telefonie. Strony tłumaczy Tłumacz Google " +
                        "w telefonie, bez wysyłania ich tekstu. LivePL ufa certyfikatom urzędu LivePL tylko na naszych stronach.",
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = Kolor.slaby,
                )
                Column {
                    Odnosnik("Strona LivePL", R.drawable.ic_strona) { otworz("https://livepl.eu/") }
                    Odnosnik("Kod źródłowy (licencja MIT)", R.drawable.ic_kod) { otworz("https://github.com/LiveProductionPL/LivePL") }
                    Odnosnik("Tłumacz Google (ML Kit)", R.drawable.ic_tlumacz) { otworz("https://cloud.google.com/translate") }
                }
                Text("© 2026 Live Production · Karol Kowalski", fontSize = 12.sp, color = Kolor.bardzoSlaby)
            }
        },
        confirmButton = { TextButton(onClick = { p.pokazInformacje = false }) { Text("Gotowe") } },
    )
}

@Composable
private fun Odnosnik(tekst: String, @DrawableRes ikona: Int, akcja: () -> Unit) {
    TextButton(onClick = akcja, modifier = Modifier.fillMaxWidth()) {
        Ikona(ikona, null, Kolor.czerwony, 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(tekst, modifier = Modifier.weight(1f), color = Kolor.czerwony)
    }
}
