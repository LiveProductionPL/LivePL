package eu.livepl.przegladarka.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.livepl.przegladarka.Adres
import eu.livepl.przegladarka.BuildConfig
import eu.livepl.przegladarka.Karta
import eu.livepl.przegladarka.Kolor
import eu.livepl.przegladarka.MojaStrona
import eu.livepl.przegladarka.Program
import eu.livepl.przegladarka.Przegladarka
import eu.livepl.przegladarka.R
import eu.livepl.przegladarka.kartaLivePL
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.max

/** Ekran startowy LivePL: pole adresu, programy, otwarte karty, Moje strony i przełącznik dzień/noc przy zegarze. */
@Composable
fun Rezyserka(p: Przegladarka) {
    var doUsuniecia by remember { mutableStateOf<MojaStrona?>(null) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Naglowek()
                PoleAdresu(p)
                Programy(p)
                if (p.zwykleKarty.isNotEmpty()) OtwarteKarty(p)
                MojeStrony(p) { doUsuniecia = it }
                Certyfikat(p)
                Stopka(p)
            }
        }
        // przewijana treść nie wchodzi pod godzinę i baterię
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Kolor.tlo.copy(alpha = 0.94f)),
        )
    }

    doUsuniecia?.let { strona ->
        AlertDialog(
            onDismissRequest = { doUsuniecia = null },
            title = { Text("Usunąć stronę z Moich stron?") },
            text = { Text("„${strona.wyswietlanaNazwa}” zniknie z Reżyserki.") },
            confirmButton = {
                TextButton(onClick = {
                    p.usunStrone(strona)
                    doUsuniecia = null
                }) { Text("Usuń", color = Kolor.czerwony) }
            },
            dismissButton = { TextButton(onClick = { doUsuniecia = null }) { Text("Anuluj") } },
        )
    }
}

// MARK: - Części ekranu

@Composable
private fun Naglowek() {
    Row(
        Modifier.padding(top = 6.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.wstega), contentDescription = null, modifier = Modifier.size(46.dp, 34.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Kolor.tekst)) { append("Live") }
                    withStyle(SpanStyle(color = Kolor.czerwony)) { append("PL") }
                },
                fontSize = 25.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("Przeglądarka Live Production", fontSize = 13.sp, color = Kolor.slaby)
        }
    }
}

@Composable
private fun PoleAdresu(p: Przegladarka) {
    var tekst by rememberSaveable { mutableStateOf("") }
    val fokus = remember { FocusRequester() }
    val klawiatura = LocalSoftwareKeyboardController.current

    LaunchedEffect(p.prosbaOAdres) {
        if (p.prosbaOAdres > p.obsluzonaProsbaOAdres) {
            p.obsluzonaProsbaOAdres = p.prosbaOAdres
            delay(350)
            fokus.requestFocus()
            klawiatura?.show()
        }
    }

    fun przejdz() {
        val url = Adres.zTekstu(tekst) ?: return
        tekst = ""
        klawiatura?.hide()
        p.otworz(url)
    }

    Row(
        Modifier.fillMaxWidth().height(50.dp).kartaLivePL(14.dp).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ikona(R.drawable.ic_szukaj, null, Kolor.slaby, 20.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (tekst.isEmpty()) Text("Adres albo szukaj", fontSize = 17.sp, color = Kolor.bardzoSlaby)
            BasicTextField(
                value = tekst,
                onValueChange = { tekst = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 17.sp, color = Kolor.tekst),
                cursorBrush = SolidColor(Kolor.czerwony),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { przejdz() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fokus)
                    .semantics { contentDescription = "Adres albo szukaj" },
            )
        }
        if (tekst.isNotEmpty()) {
            IconButton(onClick = { tekst = "" }, modifier = Modifier.size(40.dp)) {
                Ikona(R.drawable.ic_wyczysc, "Wyczyść", Kolor.bardzoSlaby, 18.dp)
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

/** Siatka kafelków co najmniej 150 dp szerokości, jak GridItem(.adaptive(minimum: 150)) na iPhonie. */
@Composable
private fun <T> Siatka(elementy: List<T>, minSzerokosc: Dp = 150.dp, odstep: Dp = 10.dp, tresc: @Composable (T) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val kolumny = max(1, ((maxWidth + odstep) / (minSzerokosc + odstep)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(odstep)) {
            elementy.chunked(kolumny).forEach { wiersz ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(odstep),
                ) {
                    wiersz.forEach { Box(Modifier.weight(1f).fillMaxHeight()) { tresc(it) } }
                    repeat(kolumny - wiersz.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Programy(p: Przegladarka) {
    var wiecej by rememberSaveable { mutableStateOf(false) }
    val obrot by animateFloatAsState(if (wiecej) 180f else 0f, label = "strzalka")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TytulSekcji("Programy")
        Siatka(p.widoczneProgramy) { KafelProgramu(p, it) }
        if (p.pozostaleProgramy.isNotEmpty()) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { wiecej = !wiecej }
                    .padding(start = 2.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (wiecej) "Mniej programów" else "Więcej programów",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Kolor.slaby,
                )
                Spacer(Modifier.width(4.dp))
                Ikona(R.drawable.ic_rozwin, null, Kolor.slaby, 20.dp, Modifier.rotate(obrot))
            }
            AnimatedVisibility(wiecej) {
                Siatka(p.pozostaleProgramy) { KafelProgramu(p, it) }
            }
        }
    }
}

@Composable
private fun KafelProgramu(p: Przegladarka, program: Program) {
    val otwarty = p.karta(program.id) != null
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .kartaLivePL()
            .clickable { p.otworz(program) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IkonaProgramu(program, 30.dp)
            Spacer(Modifier.weight(1f))
            if (otwarty) Text("otwarty", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Kolor.ok)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                program.krotkaNazwa,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Kolor.tekst,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                program.opis ?: Adres.host(program.adresNaTelefonie),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Kolor.slaby,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Lista(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).kartaLivePL()) { content() }
}

@Composable
private fun Rozdzielacz() {
    Box(Modifier.padding(start = 58.dp).fillMaxWidth().height(0.5.dp).background(Kolor.ramka))
}

@Composable
private fun OtwarteKarty(p: Przegladarka) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TytulSekcji("Otwarte karty")
        val karty = p.zwykleKarty
        Lista {
            karty.forEachIndexed { i, karta ->
                WierszKarty(p, karta)
                if (i < karty.lastIndex) Rozdzielacz()
            }
        }
    }
}

@Composable
private fun WierszKarty(p: Przegladarka, karta: Karta) {
    val host = Adres.host(karta.wyswietlanyAdres)
    val tytul = karta.tytul.ifEmpty { host }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clickable { p.aktywuj(karta) }
                .padding(start = 12.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Literka(tytul)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tytul, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Kolor.tekst, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(host, fontSize = 12.sp, color = Kolor.slaby, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = { p.zamknij(karta) }) {
            Ikona(R.drawable.ic_zamknij, "Zamknij kartę $tytul", Kolor.slaby, 18.dp)
        }
    }
}

@Composable
private fun MojeStrony(p: Przegladarka, usun: (MojaStrona) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TytulSekcji("Moje strony")
            Spacer(Modifier.weight(1f))
            MalyPrzycisk("Dodaj", R.drawable.ic_dodaj) { p.dodawanaStrona = MojaStrona("", "") }
        }
        if (p.mojeStrony.isEmpty()) {
            Text("Dodaj dowolną stronę, a pojawi się tutaj.", fontSize = 15.sp, color = Kolor.slaby)
        } else {
            Lista {
                p.mojeStrony.forEachIndexed { i, strona ->
                    WierszStrony(p, strona) { usun(strona) }
                    if (i < p.mojeStrony.lastIndex) Rozdzielacz()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WierszStrony(p: Przegladarka, strona: MojaStrona, usun: () -> Unit) {
    val kontekst = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { p.otworz(strona.adres) }, onLongClick = { menu = true })
                    .padding(start = 12.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Literka(strona.wyswietlanaNazwa)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(strona.wyswietlanaNazwa, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Kolor.tekst, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Adres.host(strona.adres), fontSize = 12.sp, color = Kolor.slaby, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Kopiuj adres") },
                    leadingIcon = { Ikona(R.drawable.ic_kopiuj, null, Kolor.tekst, 20.dp) },
                    onClick = {
                        menu = false
                        kopiuj(kontekst, strona.adres, p)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Usuń", color = Kolor.czerwony) },
                    leadingIcon = { Ikona(R.drawable.ic_usun, null, Kolor.czerwony, 20.dp) },
                    onClick = {
                        menu = false
                        usun()
                    },
                )
            }
        }
        IconButton(onClick = usun) {
            Ikona(R.drawable.ic_usun, "Usuń ${strona.wyswietlanaNazwa}", Kolor.slaby, 18.dp)
        }
    }
}

fun kopiuj(kontekst: android.content.Context, adres: String, p: Przegladarka) {
    kontekst.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Adres", adres))
    // Android 13 i nowszy sam pokazuje, że coś skopiowano
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) p.komunikat("Skopiowano adres")
}

@Composable
private fun Certyfikat(p: Przegladarka) {
    Row(Modifier.fillMaxWidth().kartaLivePL().padding(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
            Ikona(R.drawable.ic_tarcza, null, Kolor.czerwony, 28.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Certyfikat LivePL", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Kolor.tekst)
            Text(
                "Żeby telefon rozpoznawał certyfikaty LivePL, np. podpisaną pocztę, zainstaluj raz certyfikat główny. Instrukcja otworzy się w nowej karcie.",
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = Kolor.slaby,
            )
            MalyPrzycisk("Jak zainstalować", modifier = Modifier.padding(top = 4.dp)) {
                p.otworz("https://cert.livepl.eu/instalacja#android")
            }
        }
    }
}

@Composable
private fun Stopka(p: Przegladarka) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "LivePL ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            fontSize = 13.sp,
            color = Kolor.bardzoSlaby,
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = { p.ustawMotyw(p.motyw.nastepny) },
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Tryb: ${p.motyw.opis}. Stuknij: ${p.motyw.nastepny.opis}" },
        ) {
            Ikona(p.motyw.ikona, null, Kolor.slaby, 20.dp)
        }
        Spacer(Modifier.width(6.dp))
        Zegar()
    }
}

@Composable
private fun Zegar() {
    val kontekst = LocalContext.current
    val format = remember { DateFormat.getTimeFormat(kontekst) }
    var teraz by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            teraz = System.currentTimeMillis()
        }
    }
    Text(format.format(Date(teraz)), fontSize = 13.sp, color = Kolor.slaby)
}
