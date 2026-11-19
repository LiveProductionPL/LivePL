package eu.livepl.przegladarka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.livepl.przegladarka.Adres
import eu.livepl.przegladarka.Kolor
import eu.livepl.przegladarka.MojaStrona
import eu.livepl.przegladarka.Przegladarka
import kotlinx.coroutines.delay

/** Dodawanie strony do „Moich stron” (z Reżyserki albo z menu otwartej strony). */
@Composable
fun DodajStrone(p: Przegladarka, strona: MojaStrona) {
    var adres by remember { mutableStateOf(strona.adres) }
    var nazwa by remember { mutableStateOf(strona.nazwa) }
    var blad by remember { mutableStateOf<String?>(null) }
    val fokus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (adres.isEmpty()) {
            delay(150)
            fokus.requestFocus()
        }
    }

    fun dodaj() {
        val pelny = Adres.adresStrony(adres)
        if (pelny == null) {
            blad = "To nie wygląda na adres strony (np. livepl.eu)."
            return
        }
        val problem = p.dodajStrone(MojaStrona(pelny, nazwa.trim()))
        if (problem != null) {
            blad = problem
            return
        }
        p.dodawanaStrona = null
    }

    AlertDialog(
        onDismissRequest = { p.dodawanaStrona = null },
        title = { Text("Dodaj stronę") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = adres,
                    onValueChange = {
                        adres = it
                        blad = null
                    },
                    label = { Text("Adres") },
                    placeholder = { Text("np. livepl.eu") },
                    singleLine = true,
                    isError = blad != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(fokus),
                )
                OutlinedTextField(
                    value = nazwa,
                    onValueChange = { nazwa = it.take(24) },
                    label = { Text("Nazwa") },
                    placeholder = { Text("np. Strona firmy") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { dodaj() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    blad ?: "Strona pojawi się w Reżyserce, w „Moich stronach”. Lista zostaje tylko w tym telefonie.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = if (blad != null) Kolor.czerwony else Kolor.slaby,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { dodaj() }, enabled = adres.isNotBlank()) { Text("Dodaj") }
        },
        dismissButton = {
            TextButton(onClick = { p.dodawanaStrona = null }) { Text("Anuluj") }
        },
    )
}
