package eu.livepl.przegladarka.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.livepl.przegladarka.Adres
import eu.livepl.przegladarka.Karta
import eu.livepl.przegladarka.Kolor
import eu.livepl.przegladarka.Program
import eu.livepl.przegladarka.Przegladarka
import eu.livepl.przegladarka.R
import eu.livepl.przegladarka.kartaLivePL
import kotlinx.coroutines.launch

/** Lista otwartych programów i kart (przycisk „Karty” na dolnym pasku). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaKart(p: Przegladarka) {
    val stan = rememberModalBottomSheetState()
    val zakres = rememberCoroutineScope()
    fun zamknijOkno(potem: () -> Unit = {}) {
        zakres.launch { stan.hide() }.invokeOnCompletion {
            p.pokazListeKart = false
            potem()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { p.pokazListeKart = false },
        sheetState = stan,
        containerColor = Kolor.tlo,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    p.zamknijWszystkie()
                    zamknijOkno()
                },
                enabled = p.karty.isNotEmpty(),
            ) {
                Text(
                    "Zamknij wszystkie",
                    color = if (p.karty.isNotEmpty()) Kolor.czerwony else Kolor.bardzoSlaby,
                )
            }
            Text(
                "Otwarte",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Kolor.tekst,
            )
            TextButton(onClick = { zamknijOkno() }) {
                Text("Gotowe", fontWeight = FontWeight.SemiBold)
            }
        }

        val programowe = p.kartyProgramow
        val zwykle = p.zwykleKarty
        LazyColumn(
            Modifier.weight(1f, fill = false).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (programowe.isNotEmpty()) {
                item { TytulSekcji("Programy", Modifier.padding(top = 8.dp)) }
                item {
                    Grupa {
                        programowe.forEachIndexed { i, karta ->
                            WierszListyKart(p, karta, p.program(karta.programId)) {
                                p.aktywuj(karta)
                                zamknijOkno()
                            }
                            if (i < programowe.lastIndex) Kreska()
                        }
                    }
                }
            }
            item { TytulSekcji("Karty", Modifier.padding(top = 8.dp)) }
            item {
                if (zwykle.isEmpty()) {
                    Text("Brak otwartych kart.", fontSize = 15.sp, color = Kolor.slaby, modifier = Modifier.padding(start = 2.dp))
                } else {
                    Grupa {
                        zwykle.forEachIndexed { i, karta ->
                            WierszListyKart(p, karta, null) {
                                p.aktywuj(karta)
                                zamknijOkno()
                            }
                            if (i < zwykle.lastIndex) Kreska()
                        }
                    }
                }
            }
        }

        Button(
            onClick = { zamknijOkno { p.nowaKarta() } },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
        ) {
            Ikona(R.drawable.ic_dodaj, null, Color.White, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text("Nowa karta", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Grupa(tresc: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).kartaLivePL()) { tresc() }
}

@Composable
private fun Kreska() {
    HorizontalDivider(Modifier.padding(start = 64.dp), thickness = 0.5.dp, color = Kolor.ramka)
}

@Composable
private fun WierszListyKart(p: Przegladarka, karta: Karta, program: Program?, wybierz: () -> Unit) {
    val host = Adres.host(karta.wyswietlanyAdres)
    val tytul = karta.tytul.ifEmpty { host }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClick = wybierz)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                if (program != null) IkonaProgramu(program, if (program.szeroki) 18.dp else 26.dp) else Literka(tytul)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    program?.krotkaNazwa ?: tytul,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Kolor.tekst,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (program == null) host else tytul,
                    fontSize = 12.sp,
                    color = Kolor.slaby,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (karta.id == p.aktywnaId) {
                Ikona(R.drawable.ic_ok, "Otwarta teraz", Kolor.czerwony, 18.dp, Modifier.padding(start = 6.dp))
            }
        }
        IconButton(onClick = { p.zamknij(karta) }) {
            Ikona(R.drawable.ic_zamknij, "Zamknij $tytul", Kolor.slaby, 18.dp)
        }
    }
}
