package eu.livepl.przegladarka.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.livepl.przegladarka.Kolor
import eu.livepl.przegladarka.Przegladarka
import eu.livepl.przegladarka.R

/** Główny ekran: Reżyserka albo otwarta karta, a na dole pasek programów jak pasek po lewej w LivePL na Windows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Glowny(p: Przegladarka) {
    val aktywna = p.aktywna
    // najpierw, żeby obsługa Wstecz w karcie (szukanie, adres) miała pierwszeństwo
    BackHandler(enabled = aktywna != null) { p.wstecz() }

    Box(Modifier.fillMaxSize().background(Kolor.tlo)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .imePadding(),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (aktywna != null) {
                    key(aktywna.id) { WidokKarty(p, aktywna) }
                } else {
                    Rezyserka(p)
                }
            }
            // przy klawiaturze pasek się chowa, a strona dostaje całe miejsce
            if (!WindowInsets.isImeVisible) PasekProgramow(p)
        }

        if (p.pokazListeKart) ListaKart(p)
        p.dodawanaStrona?.let { DodajStrone(p, it) }
        p.okna.firstOrNull()?.let { key(it) { OknoStrony(p, it) } }
        OknaMenuPrzegladarki(p)
        p.pelnyEkran?.let { PelnyEkranWidok(p, it) }
    }
}

/**
 * Dolny pasek z samymi ikonami: Start (Wstęga), programy (z kropką, gdy są otwarte) i licznik kart.
 * Nazwy zna czytnik ekranu, a przytrzymanie ikony pokazuje dymek z nazwą.
 */
@Composable
fun PasekProgramow(p: Przegladarka) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Kolor.karta)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Kolor.ramka))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrzyciskPaska("Start", aktywny = p.aktywnaId == null, otwarty = false, onClick = { p.pokazRezyserke() }) {
                Image(painterResource(R.drawable.wstega), contentDescription = null, modifier = Modifier.size(36.dp, 26.dp))
            }
            Kreska()
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                p.programyNaPasku.forEach { program ->
                    key(program.id) {
                        val karta = p.karta(program.id)
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            PrzyciskPaska(
                                program.krotkaNazwa,
                                aktywny = karta != null && karta.id == p.aktywnaId,
                                otwarty = karta != null,
                                onClick = { p.otworz(program) },
                                // otwarty program: przytrzymanie daje menu z nazwą i „Zamknij”
                                onLongClick = if (karta != null) ({ menu = true }) else null,
                            ) {
                                IkonaProgramu(program, 26.dp, szerokie = 1.9f)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Zamknij ${program.krotkaNazwa}") },
                                    leadingIcon = { Ikona(R.drawable.ic_zamknij, null, Kolor.tekst, 20.dp) },
                                    onClick = {
                                        menu = false
                                        karta?.let { p.zamknij(it) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Kreska()
            PrzyciskPaska("Karty", aktywny = false, otwarty = false, onClick = { p.pokazListeKart = true }) {
                LicznikKart(p.karty.size)
            }
        }
    }
}

@Composable
private fun Kreska() {
    Box(Modifier.width(0.5.dp).height(30.dp).background(Kolor.ramka))
}

/**
 * Przycisk paska: sama ikona, co najmniej 52×48 dp. Aktywny ma tło, otwarty program czerwoną kropkę.
 * Przytrzymanie robi [onLongClick] albo, gdy go nie ma, pokazuje dymek z nazwą.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PrzyciskPaska(
    nazwa: String,
    aktywny: Boolean,
    otwarty: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    tresc: @Composable () -> Unit,
) {
    val czerwony = Kolor.czerwony
    val kropka = otwarty && !aktywny
    val dymek = rememberTooltipState()
    val zakres = rememberCoroutineScope()
    val pokazNazwe: () -> Unit = { zakres.launch { dymek.show() } }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(nazwa) } },
        state = dymek,
        focusable = false,
        enableUserInput = false,
    ) {
        Box(
            Modifier
                .defaultMinSize(minWidth = 52.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (aktywny) Kolor.wcisniety else Color.Transparent)
                .combinedClickable(
                    role = Role.Tab,
                    onClick = onClick,
                    onLongClick = onLongClick ?: pokazNazwe,
                )
                .semantics {
                    contentDescription = if (kropka) "$nazwa, otwarty" else nazwa
                    selected = aktywny
                }
                .drawWithContent {
                    drawContent()
                    if (kropka) drawCircle(czerwony, radius = 3.dp.toPx(), center = Offset(size.width - 9.dp.toPx(), 8.dp.toPx()))
                }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            tresc()
        }
    }
}

@Composable
fun LicznikKart(liczba: Int) {
    Box(
        Modifier.size(24.dp).border(1.7.dp, Kolor.tekst, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (liczba > 99) "∞" else "$liczba",
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Kolor.tekst,
        )
    }
}
