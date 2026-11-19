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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        p.pelnyEkran?.let { PelnyEkranWidok(p, it) }
    }
}

/** Dolny pasek: Reżyserka, programy (z kropką, gdy są otwarte) i licznik kart. */
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
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrzyciskPaska("Reżyserka", aktywny = p.aktywnaId == null, otwarty = false, onClick = { p.pokazRezyserke() }) {
                Image(painterResource(R.drawable.wstega), contentDescription = null, modifier = Modifier.size(32.dp, 24.dp))
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
                                onLongClick = if (karta != null) ({ menu = true }) else null,
                            ) {
                                IkonaProgramu(program, 24.dp)
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
    Box(Modifier.width(0.5.dp).height(34.dp).background(Kolor.ramka))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrzyciskPaska(
    podpis: String,
    aktywny: Boolean,
    otwarty: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    tresc: @Composable () -> Unit,
) {
    val czerwony = Kolor.czerwony
    val kropka = otwarty && !aktywny
    Column(
        Modifier
            .widthIn(min = 58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (aktywny) Kolor.wcisniety else Color.Transparent)
            .combinedClickable(role = Role.Tab, onClick = onClick, onLongClick = onLongClick)
            .semantics {
                contentDescription = if (kropka) "$podpis, otwarty" else podpis
                selected = aktywny
            }
            .drawWithContent {
                drawContent()
                if (kropka) drawCircle(czerwony, radius = 3.dp.toPx(), center = Offset(size.width - 11.dp.toPx(), 7.dp.toPx()))
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) { tresc() }
        Spacer(Modifier.height(3.dp))
        Text(
            podpis,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (aktywny) FontWeight.SemiBold else FontWeight.Normal,
            color = if (aktywny) Kolor.tekst else Kolor.slaby,
            maxLines = 1,
        )
    }
}

@Composable
fun LicznikKart(liczba: Int) {
    Box(
        Modifier.size(22.dp).border(1.6.dp, Kolor.tekst, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (liczba > 99) "∞" else "$liczba",
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Kolor.tekst,
        )
    }
}
