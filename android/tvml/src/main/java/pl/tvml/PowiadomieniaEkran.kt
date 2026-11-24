package pl.tvml

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

/**
 * Więcej → Powiadomienia: główny przełącznik i tematy z config.push.topics (gminy w osobnej sekcji).
 * Domyślnie wyłączone; o zgodę Androida 13+ aplikacja pyta dopiero po włączeniu głównego przełącznika.
 * Po odmowie zostaje przycisk do ustawień powiadomień w telefonie.
 */
@Composable
fun PowiadomieniaEkran(m: Magazyn) {
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    val kontekst = LocalContext.current
    var odmowa by remember { mutableStateOf(false) }
    val prosba = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tak ->
        Powiadomienia.przygotuj(kontekst)
        if (tak) Powiadomienia.ustawWlaczone(kontekst, true) else odmowa = true
    }
    val wlaczone = Powiadomienia.wlaczone && Powiadomienia.zgoda
    fun przelacz(tak: Boolean) {
        odmowa = false
        when {
            !tak -> Powiadomienia.ustawWlaczone(kontekst, false)
            Powiadomienia.zgodaSystemu(kontekst) -> Powiadomienia.ustawWlaczone(kontekst, true)
            Build.VERSION.SDK_INT >= 33 -> prosba.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> odmowa = true
        }
    }
    val akcent = m.motyw.akcent.w(ciemny)
    Column(
        Modifier.fillMaxSize().background(b.tlo).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Naglowek(tl("app.notifications", "Powiadomienia"), wielkosc = 24)
        KartaUstawien {
            // te same klucze co na iPhonie (Powiadomienia.swift): app.pushEnable, app.pushDenied, app.pushHint, app.pushTopics, app.pushCounties
            WierszPrzelacznika(tl("app.pushEnable", "Powiadomienia o nowych wpisach"), null, wlaczone, akcent, ::przelacz)
        }
        if (odmowa || (Powiadomienia.wlaczone && !Powiadomienia.zgoda)) {
            Text(tl("app.pushDenied", "Powiadomienia dla TVML są wyłączone w ustawieniach telefonu."), color = b.slaby, fontSize = 13.sp)
            Box(
                Modifier.height(40.dp).clip(CircleShape).background(akcent)
                    .clickable(role = Role.Button) { Powiadomienia.otworzUstawieniaSystemu(kontekst) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tl("app.pushOpenSettings", "Otwórz ustawienia telefonu"), color = m.motyw.napisNaAkcencie.w(ciemny), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (wlaczone) {
            val (gminy, tematy) = Powiadomienia.tematy.partition { it.gmina }
            Text(tl("app.pushHint", "Wybierz, o czym chcesz dostawać powiadomienia. Możesz to zmienić w każdej chwili."), color = b.slaby, fontSize = 13.sp, lineHeight = 17.sp)
            for ((tytul, lista) in listOf(tl("app.pushTopics", "Tematy") to tematy, tl("app.pushCounties", "Gminy") to gminy)) {
                if (lista.isEmpty()) continue
                Naglowek(tytul, wielkosc = 18)
                KartaUstawien {
                    lista.forEachIndexed { i, t ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(b.ramka))
                        WierszPrzelacznika(t.nazwa, null, t.id in Powiadomienia.wybrane, akcent) { Powiadomienia.ustawTemat(kontekst, t.id, it) }
                    }
                }
            }
            if (Powiadomienia.wybrane.isEmpty()) {
                Text(tl("app.pushNoTopics", "Bez wybranego tematu powiadomienia nie przychodzą."), color = b.slaby, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun KartaUstawien(tresc: @Composable () -> Unit) {
    val b = LocalBarwy.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp))) { tresc() }
}

@Composable
private fun WierszPrzelacznika(nazwa: String, opis: String?, tak: Boolean, akcent: androidx.compose.ui.graphics.Color, zmien: (Boolean) -> Unit) {
    val b = LocalBarwy.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(value = tak, role = Role.Switch, onValueChange = zmien).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nazwa, color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (!opis.isNullOrEmpty()) Text(opis, color = b.slaby, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(checked = tak, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = akcent, checkedBorderColor = akcent))
    }
}
