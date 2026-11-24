package pl.tvml

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

private val napisPodgladu = kolor(0x1d1400)
private val godzina = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Pasek podglądu szkicu z panelu pod banerem, jak na iPhonie: złote tło, ikona oka, „{label} · do HH:MM”
 * i przyciski „Odśwież” (szkic od nowa) oraz „Zakończ” (powrót do opublikowanych ustawień).
 */
@Composable
fun PasekPodgladu(m: Magazyn) {
    val p = m.podglad ?: return
    val tekst = p.etykieta.ifEmpty { tl("app.preview", "Podgląd") } +
        (p.doKiedy?.let { " · " + tl("app.previewUntil", "do {t}").replace("{t}", it.withZoneSameInstant(Daty.strefa).format(godzina)) } ?: "")
    Row(
        Modifier.fillMaxWidth().background(Barwy.zloto).padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Ikony.oko, null, tint = napisPodgladu, modifier = Modifier.size(18.dp))
        Text(tekst, Modifier.weight(1f), color = napisPodgladu, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        Kapsula(tl("app.refresh", "Odśwież")) { m.odswiez(wymus = true) }
        Kapsula(tl("app.previewEnd", "Zakończ")) { m.zakonczPodglad(wygasl = false) }
    }
}

@Composable
private fun Kapsula(napis: String, onClick: () -> Unit) {
    Text(
        napis,
        Modifier.clip(CircleShape).background(napisPodgladu.copy(alpha = 0.12f)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = napisPodgladu, fontSize = 13.sp, fontWeight = FontWeight.Bold,
    )
}

/** Podgląd wygasł albo zakończono go w panelu: wyjaśnienie, a aplikacja wraca do opublikowanych ustawień. */
@Composable
fun OknoKoncaPodgladu(m: Magazyn) {
    if (!m.podgladZakonczony) return
    AlertDialog(
        onDismissRequest = { m.podgladZakonczony = false },
        title = { Text(tl("app.previewEnded", "Podgląd zakończony")) },
        text = { Text(tl("app.previewEndedText", "Podgląd wygasł albo został zakończony w panelu. Aplikacja wraca do opublikowanych ustawień.")) },
        confirmButton = { TextButton(onClick = { m.podgladZakonczony = false }) { Text("OK", fontWeight = FontWeight.Bold) } },
    )
}
