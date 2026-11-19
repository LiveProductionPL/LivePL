package eu.livepl.przegladarka.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import eu.livepl.przegladarka.Kolor
import eu.livepl.przegladarka.Okno
import eu.livepl.przegladarka.PelnyEkran
import eu.livepl.przegladarka.Przegladarka

/** Okienka stron (alert, confirm, prompt) i pytania o zgodę jako okna systemowe Androida. */
@Composable
fun OknoStrony(p: Przegladarka, okno: Okno) {
    when (okno) {
        is Okno.Komunikat -> {
            val ok = { p.zamknijOkno(okno) { okno.wynik.confirm() } }
            AlertDialog(
                onDismissRequest = ok,
                title = { Text(okno.tytul) },
                text = { Tresc(okno.tresc) },
                confirmButton = { TextButton(onClick = ok) { Text("OK") } },
            )
        }

        is Okno.Potwierdzenie -> {
            val nie = { p.zamknijOkno(okno) { okno.wynik.cancel() } }
            AlertDialog(
                onDismissRequest = nie,
                title = { Text(okno.tytul) },
                text = { Tresc(okno.tresc) },
                confirmButton = { TextButton(onClick = { p.zamknijOkno(okno) { okno.wynik.confirm() } }) { Text(okno.tak) } },
                dismissButton = { TextButton(onClick = nie) { Text("Anuluj") } },
            )
        }

        is Okno.Pytanie -> {
            var tekst by remember { mutableStateOf(okno.domyslnie.orEmpty()) }
            val nie = { p.zamknijOkno(okno) { okno.wynik.cancel() } }
            AlertDialog(
                onDismissRequest = nie,
                title = { Text(okno.tytul) },
                text = {
                    Column {
                        if (okno.tresc.isNotBlank()) Tresc(okno.tresc)
                        OutlinedTextField(
                            value = tekst,
                            onValueChange = { tekst = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { p.zamknijOkno(okno) { okno.wynik.confirm(tekst) } }) { Text("OK") } },
                dismissButton = { TextButton(onClick = nie) { Text("Anuluj") } },
            )
        }

        is Okno.Zgoda -> {
            val nie = { p.zamknijOkno(okno) { okno.gotowe(false) } }
            AlertDialog(
                onDismissRequest = nie,
                title = { Text(okno.tytul) },
                text = { Tresc(okno.tresc) },
                confirmButton = { TextButton(onClick = { p.zamknijOkno(okno) { okno.gotowe(true) } }) { Text("Zezwól") } },
                dismissButton = { TextButton(onClick = nie) { Text("Nie zezwalaj", color = Kolor.czerwony) } },
            )
        }
    }
}

@Composable
private fun Tresc(tekst: String) {
    Text(tekst, modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
}

/** Film (albo inny element strony) na pełnym ekranie, bez pasków systemu. */
@Composable
fun PelnyEkranWidok(p: Przegladarka, pe: PelnyEkran) {
    val widok = LocalView.current
    val okno = LocalActivity.current?.window
    DisposableEffect(pe) {
        val kontroler = okno?.let { WindowCompat.getInsetsController(it, widok) }
        kontroler?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        kontroler?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { kontroler?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    BackHandler { p.wyjdzZPelnegoEkranu() }
    AndroidView(
        factory = { kontekst ->
            FrameLayout(kontekst).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                (pe.widok.parent as? ViewGroup)?.removeView(pe.widok)
                addView(pe.widok, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black),
    )
}
