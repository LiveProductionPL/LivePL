package eu.livepl.przegladarka.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import eu.livepl.przegladarka.Adres
import eu.livepl.przegladarka.BladStrony
import eu.livepl.przegladarka.Karta
import eu.livepl.przegladarka.Kolor
import eu.livepl.przegladarka.LocalPaleta
import eu.livepl.przegladarka.MainActivity
import eu.livepl.przegladarka.MojaStrona
import eu.livepl.przegladarka.Przegladarka
import eu.livepl.przegladarka.R
import eu.livepl.przegladarka.SilnikTlumaczenia
import eu.livepl.przegladarka.StanTlumaczenia

/** Otwarta karta: u góry wstecz, adres z kłódką, odśwież i menu; pod spodem strona. */
@Composable
fun WidokKarty(p: Przegladarka, karta: Karta) {
    var edycja by remember { mutableStateOf(false) }
    var szukanie by remember { mutableStateOf(false) }

    BackHandler(enabled = edycja || szukanie) {
        if (edycja) {
            edycja = false
        } else {
            szukanie = false
            karta.koniecSzukania()
        }
    }

    Column(Modifier.fillMaxSize().background(Kolor.tlo)) {
        Column(Modifier.fillMaxWidth().background(Kolor.tlo).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (edycja) {
                    PoleEdycji(p, karta, Modifier.weight(1f).padding(start = 4.dp)) { edycja = false }
                    TextButton(onClick = { edycja = false }) { Text("Anuluj", fontSize = 15.sp) }
                } else {
                    IconButton(onClick = { karta.webViewJesli?.goBack() }, enabled = karta.mozeWstecz) {
                        Ikona(
                            R.drawable.ic_wstecz, "Wstecz",
                            if (karta.mozeWstecz) Kolor.tekst else Kolor.bardzoSlaby, 22.dp,
                        )
                    }
                    PasekAdresu(karta, Modifier.weight(1f)) { edycja = true }
                    MenuKarty(p, karta) {
                        szukanie = true
                    }
                }
            }
            if (szukanie) PasekSzukania(karta) {
                szukanie = false
                karta.koniecSzukania()
            }
            karta.tlumaczenie.stan?.let { PasekTlumaczenia(karta, it) }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Kolor.ramka))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            WebWidok(karta)
            karta.blad?.let { WidokBledu(it) { karta.sprobujPonownie() } }
            if (karta.laduje) PasekPostepu(karta.postep)
        }
    }
}

// MARK: - Górny pasek

@Composable
private fun PasekAdresu(karta: Karta, modifier: Modifier, edytuj: () -> Unit) {
    val host = Adres.host(karta.wyswietlanyAdres)
    Row(
        modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(Kolor.karta)
            .border(1.dp, Kolor.ramka, CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClickLabel = "wpisz inny adres", role = Role.Button, onClick = edytuj)
                .semantics(mergeDescendants = true) { contentDescription = "Adres: $host" }
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ikona(
                if (karta.bezpieczna) R.drawable.ic_klodka else R.drawable.ic_uwaga, null,
                if (karta.bezpieczna) Kolor.slaby else Kolor.czerwony, 14.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                host,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Kolor.tekst,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        IconButton(
            onClick = { if (karta.laduje) karta.zatrzymaj() else karta.przeladuj() },
            modifier = Modifier.size(36.dp),
        ) {
            Ikona(
                if (karta.laduje) R.drawable.ic_zamknij else R.drawable.ic_odswiez,
                if (karta.laduje) "Zatrzymaj" else "Odśwież",
                Kolor.slaby, 18.dp,
            )
        }
    }
}

@Composable
private fun PoleEdycji(p: Przegladarka, karta: Karta, modifier: Modifier, koniec: () -> Unit) {
    val poczatek = karta.wyswietlanyAdres.orEmpty()
    var wartosc by remember { mutableStateOf(TextFieldValue(poczatek, TextRange(0, poczatek.length))) }
    val fokus = remember { FocusRequester() }
    var mialFokus by remember { mutableStateOf(false) }
    val klawiatura = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        fokus.requestFocus()
        klawiatura?.show()
    }

    fun przejdz() {
        val url = Adres.zTekstu(wartosc.text)
        klawiatura?.hide()
        koniec()
        if (url == null) return
        val uri = Uri.parse(url)
        if (Przegladarka.obcyAdres(uri)) p.otworzWSystemie(uri) else karta.laduj(url)
    }

    Row(
        modifier
            .height(38.dp)
            .background(Kolor.karta, CircleShape)
            .border(1.dp, Kolor.czerwony, CircleShape)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ikona(R.drawable.ic_szukaj, null, Kolor.slaby, 16.dp)
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = wartosc,
            onValueChange = { wartosc = it },
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = Kolor.tekst),
            cursorBrush = SolidColor(Kolor.czerwony),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { przejdz() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(fokus)
                .onFocusChanged {
                    if (it.isFocused) mialFokus = true else if (mialFokus) koniec()
                }
                .semantics { contentDescription = "Adres albo szukaj" },
        )
        if (wartosc.text.isNotEmpty()) {
            IconButton(onClick = { wartosc = TextFieldValue("") }, modifier = Modifier.size(32.dp)) {
                Ikona(R.drawable.ic_wyczysc, "Wyczyść", Kolor.bardzoSlaby, 18.dp)
            }
        }
    }
}

@Composable
private fun MenuKarty(p: Przegladarka, karta: Karta, szukaj: () -> Unit) {
    val kontekst = LocalContext.current
    var otwarte by remember { mutableStateOf(false) }
    val adres = karta.adres

    Box {
        IconButton(onClick = { otwarte = true }) {
            Ikona(R.drawable.ic_menu, "Menu", Kolor.tekst, 22.dp)
        }
        DropdownMenu(expanded = otwarte, onDismissRequest = { otwarte = false }) {
            Pozycja("Dalej", R.drawable.ic_dalej, karta.mozeDalej) {
                otwarte = false
                karta.webViewJesli?.goForward()
            }
            Pozycja("Udostępnij", R.drawable.ic_udostepnij, adres != null) {
                otwarte = false
                adres?.let { udostepnij(kontekst, it, karta.tytul) }
            }
            Pozycja("Znajdź na stronie", R.drawable.ic_znajdz, karta.webViewJesli != null) {
                otwarte = false
                szukaj()
            }
            val przetlumaczona = karta.tlumaczenie.aktywne
            Pozycja(
                if (przetlumaczona) "Pokaż oryginał" else "Przetłumacz z Google",
                R.drawable.ic_tlumacz,
                karta.webViewJesli != null && karta.blad == null,
            ) {
                otwarte = false
                if (przetlumaczona) karta.tlumaczenie.oryginal() else karta.tlumaczenie.przetlumacz()
            }
            HorizontalDivider(color = Kolor.ramka)
            Pozycja("Dodaj do Moich stron", R.drawable.ic_dodaj, adres != null) {
                otwarte = false
                adres?.let { p.dodawanaStrona = MojaStrona(it, karta.tytul.take(24)) }
            }
            Pozycja("Kopiuj adres", R.drawable.ic_kopiuj, adres != null) {
                otwarte = false
                adres?.let { kopiuj(kontekst, it, p) }
            }
            Pozycja("Otwórz w przeglądarce", R.drawable.ic_przegladarka, adres != null) {
                otwarte = false
                adres?.let { otworzGdzieIndziej(kontekst, it, p) }
            }
            HorizontalDivider(color = Kolor.ramka)
            DropdownMenuItem(
                text = { Text(if (karta.programId == null) "Zamknij kartę" else "Zamknij program", color = Kolor.czerwony) },
                leadingIcon = { Ikona(R.drawable.ic_zamknij, null, Kolor.czerwony, 20.dp) },
                onClick = {
                    otwarte = false
                    p.zamknij(karta)
                },
            )
        }
    }
}

@Composable
private fun Pozycja(tekst: String, ikona: Int, wlaczona: Boolean, akcja: () -> Unit) {
    DropdownMenuItem(
        text = { Text(tekst) },
        leadingIcon = { Ikona(ikona, null, if (wlaczona) Kolor.tekst else Kolor.bardzoSlaby, 20.dp) },
        enabled = wlaczona,
        onClick = akcja,
    )
}

private fun udostepnij(kontekst: Context, adres: String, tytul: String) {
    val tresc = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, adres)
        if (tytul.isNotBlank()) putExtra(Intent.EXTRA_TITLE, tytul)
    }
    kontekst.startActivity(Intent.createChooser(tresc, null))
}

/** „Otwórz w przeglądarce”: wybór innej przeglądarki, bez samego LivePL. */
private fun otworzGdzieIndziej(kontekst: Context, adres: String, p: Przegladarka) {
    val widok = Intent(Intent.ACTION_VIEW, Uri.parse(adres)).addCategory(Intent.CATEGORY_BROWSABLE)
    val wybor = Intent.createChooser(widok, "Otwórz w przeglądarce").apply {
        putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(kontekst, MainActivity::class.java)))
    }
    try {
        kontekst.startActivity(wybor)
    } catch (e: Exception) {
        p.komunikat("Na tym telefonie nie ma innej przeglądarki.")
    }
}

// MARK: - Znajdź na stronie

@Composable
private fun PasekSzukania(karta: Karta, zamknij: () -> Unit) {
    var tekst by remember { mutableStateOf("") }
    val fokus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fokus.requestFocus() }
    val (ktory, ile) = karta.znalezione

    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(38.dp)
                .background(Kolor.karta, CircleShape)
                .border(1.dp, Kolor.ramka, CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ikona(R.drawable.ic_znajdz, null, Kolor.slaby, 16.dp)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (tekst.isEmpty()) Text("Znajdź na stronie", fontSize = 16.sp, color = Kolor.bardzoSlaby)
                BasicTextField(
                    value = tekst,
                    onValueChange = {
                        tekst = it
                        karta.szukaj(it)
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = Kolor.tekst),
                    cursorBrush = SolidColor(Kolor.czerwony),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { karta.nastepny(true) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fokus),
                )
            }
            if (tekst.isNotEmpty()) {
                Text("$ktory/$ile", fontSize = 13.sp, color = if (ile == 0) Kolor.czerwony else Kolor.slaby)
            }
        }
        IconButton(onClick = { karta.nastepny(false) }, enabled = ile > 0) {
            Ikona(R.drawable.ic_w_gore, "Poprzedni", if (ile > 0) Kolor.tekst else Kolor.bardzoSlaby)
        }
        IconButton(onClick = { karta.nastepny(true) }, enabled = ile > 0) {
            Ikona(R.drawable.ic_w_dol, "Następny", if (ile > 0) Kolor.tekst else Kolor.bardzoSlaby)
        }
        IconButton(onClick = zamknij) {
            Ikona(R.drawable.ic_zamknij, "Zamknij szukanie", Kolor.tekst, 20.dp)
        }
    }
}

// MARK: - Tłumaczenie

/** Pasek pod adresem: propozycja tłumaczenia, postęp albo „Przetłumaczono” z powrotem do oryginału. */
@Composable
private fun PasekTlumaczenia(karta: Karta, stan: StanTlumaczenia) {
    val t = karta.tlumaczenie
    fun nazwa(jezyk: String) = SilnikTlumaczenia.nazwa(jezyk)
    val opis = when (stan) {
        is StanTlumaczenia.Propozycja -> "Język strony: ${nazwa(stan.jezyk)}"
        StanTlumaczenia.Sprawdzanie -> "Sprawdzam język strony…"
        is StanTlumaczenia.PobieranieSlownika -> "Pobieram słownik Google: ${nazwa(stan.jezyk)} → polski (ok. 30 MB, tylko raz)…"
        is StanTlumaczenia.Trwa -> "Tłumacz Google: ${nazwa(stan.jezyk)} → polski"
        is StanTlumaczenia.Gotowe -> "Przetłumaczono przez Google: ${nazwa(stan.jezyk)} → polski"
        is StanTlumaczenia.Blad -> stan.opis
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ikona(R.drawable.ic_tlumacz, null, Kolor.czerwony, 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                opis,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = if (stan is StanTlumaczenia.Blad) Kolor.czerwony else Kolor.tekst,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when (stan) {
                is StanTlumaczenia.Propozycja -> TextButton(onClick = { t.przetlumacz() }) {
                    Text("Przetłumacz z Google", fontWeight = FontWeight.SemiBold)
                }
                is StanTlumaczenia.Blad -> TextButton(onClick = { t.przetlumacz() }) { Text("Ponów") }
                StanTlumaczenia.Sprawdzanie -> Unit
                else -> TextButton(onClick = { t.oryginal() }) { Text("Oryginał") }
            }
            IconButton(onClick = { t.odrzuc() }, modifier = Modifier.size(40.dp)) {
                Ikona(R.drawable.ic_zamknij, "Zamknij pasek tłumaczenia", Kolor.slaby, 18.dp)
            }
        }
        if (stan is StanTlumaczenia.Trwa && stan.wszystkie > 0) {
            val ile by animateFloatAsState(stan.zrobione.toFloat() / stan.wszystkie, tween(250), label = "tlumaczenie")
            Box(Modifier.fillMaxWidth(ile).height(2.dp).background(Kolor.czerwony))
        }
    }
}

// MARK: - Strona, postęp i błąd

/** Osadza WebView karty. Ten sam WebView przechodzi między widokami, więc strona nie ładuje się od nowa. */
@Composable
private fun WebWidok(karta: Karta) {
    val paleta = LocalPaleta.current
    AndroidView(
        factory = {
            karta.kontener.also { k -> (k.parent as? ViewGroup)?.removeView(k) }
        },
        update = { k ->
            k.setColorSchemeColors(paleta.czerwony.toArgb())
            k.setProgressBackgroundColorSchemeColor(paleta.karta.toArgb())
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Cienki czerwony pasek ładowania pod paskiem adresu. */
@Composable
private fun PasekPostepu(postep: Int) {
    val ile by animateFloatAsState((postep / 100f).coerceIn(0.08f, 1f), tween(250), label = "postep")
    Box(Modifier.fillMaxWidth(ile).height(2.dp).background(Kolor.czerwony))
}

@Composable
private fun WidokBledu(blad: BladStrony, ponow: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Kolor.tlo).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Ikona(R.drawable.ic_brak_sieci, null, Kolor.czerwony, 46.dp)
            Text(
                "Nie udało się otworzyć strony",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Kolor.tekst,
                textAlign = TextAlign.Center,
            )
            Text(blad.opis, fontSize = 16.sp, lineHeight = 22.sp, color = Kolor.slaby, textAlign = TextAlign.Center)
            blad.adres?.let { Text(Adres.host(it), fontSize = 13.sp, color = Kolor.bardzoSlaby) }
            Button(onClick = ponow, modifier = Modifier.padding(top = 6.dp)) { Text("Spróbuj ponownie") }
        }
    }
}
