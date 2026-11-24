package pl.tvml

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.time.ZonedDateTime
import kotlin.coroutines.resume

/**
 * Komunikat pod paskiem „NAJNOWSZE”, jak na iPhonie (BanerKomunikatu): redakcji z panelu (config.message, od API 1.2)
 * albo prośba o aktualizację, gdy w Google Play jest nowsza wersja. Poziom info, warning albo urgent (pilnego nie da się
 * zamknąć). Stuknięcie otwiera link.
 */
data class Komunikat(val id: String, val tytul: String, val tekst: String, val poziom: String, val link: String?, val etykieta: String) {
    val pilny: Boolean get() = poziom == "urgent"

    companion object {
        private fun bezHtml(s: String) = HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

        fun z(j: JSONObject?): Komunikat? {
            if (j == null || j.length() == 0) return null
            val tytul = bezHtml(j.s("title"))
            val tekst = bezHtml(j.s("text"))
            if (tytul.isEmpty() && tekst.isEmpty()) return null
            Daty.iso(j.s("until"))?.let { if (it.isBefore(ZonedDateTime.now())) return null }
            val poziom = j.s("level").takeIf { it in setOf("info", "warning", "urgent") } ?: "info"
            return Komunikat(
                j.s("id").ifEmpty { "$tytul|$tekst" }, tytul, tekst, poziom,
                j.s("link").ifEmpty { null }, j.s("linkLabel").ifEmpty { tl("app.readMore", "Więcej") },
            )
        }
    }
}

/** Aktualizacje TVML z Google Play: łagodna prośba (nowsza wersja w sklepie) i wymagana (config.minAppVersion.android). */
object AktualizacjaAplikacji {
    /** Strona TVML w Google Play (także z wersji testowej pl.tvml.debug). */
    private const val PAKIET = "pl.tvml"

    /** Wersja aplikacji bez dopisku „-debug”. */
    val biezaca: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Czy minimalna wersja z panelu jest wyższa niż ta. */
    fun wymagana(minimalna: String): Boolean = minimalna.isNotBlank() && Motyw.porownajWersje(biezaca, minimalna) < 0

    /**
     * Numer nowszej wersji w Google Play albo null (Play Core: In-App Updates, bez żadnych danych o czytelniku).
     * Działa tylko w aplikacji zainstalowanej ze Sklepu; w wersji testowej i bez Sklepu zawsze null.
     */
    suspend fun nowszaWSklepie(kontekst: Context): Int? = suspendCancellableCoroutine { k ->
        fun koniec(v: Int?) { if (k.isActive) k.resume(v) }
        try {
            AppUpdateManagerFactory.create(kontekst).appUpdateInfo
                .addOnSuccessListener { i -> koniec(if (i.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) i.availableVersionCode() else null) }
                .addOnFailureListener { koniec(null) }
        } catch (_: Exception) {
            koniec(null)
        }
    }

    fun komunikat(kod: Int) = Komunikat(
        "aktualizacja-$kod",
        tl("app.newVersion", "Nowa wersja TVML {v}").replace(" {v}", "").replace("{v}", "").trim(),
        tl("app.updateAvailable", "Dostępna jest nowa wersja aplikacji."),
        "info", "sklep", tl("app.update", "Aktualizuj"),
    )

    /** Strona TVML w aplikacji Sklep Google Play, a bez niej w przeglądarce. */
    fun otworzSklep(kontekst: Context) {
        val sklep = Intent(Intent.ACTION_VIEW, "market://details?id=$PAKIET".toUri()).setPackage("com.android.vending")
        try {
            kontekst.startActivity(sklep)
        } catch (_: ActivityNotFoundException) {
            runCatching { kontekst.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$PAKIET".toUri())) }
        }
    }
}

/** Komunikat redakcji i prośba o aktualizację pod paskiem (krzyżyk zapamiętuje zamknięcie, poza pilnym). */
@Composable
fun KomunikatyAplikacji(m: Magazyn) {
    m.komunikat?.let { k -> if (k.pilny || k.id !in m.zamknieteKomunikaty) BanerKomunikatu(k, m) }
    m.nowaWersja?.let { k -> if (!m.wymaganaAktualizacja && k.id !in m.zamknieteKomunikaty) BanerKomunikatu(k, m) }
}

@Composable
private fun BanerKomunikatu(k: Komunikat, m: Magazyn) {
    val kontekst = LocalContext.current
    val ciemny = LocalCiemny.current
    val (tlo, napis) = when {
        k.pilny -> kolor(0xc8102e) to Color.White
        k.poziom == "warning" -> if (ciemny) kolor(0x3a2a0a) to kolor(0xf5c463) else kolor(0xfdf1dc) to kolor(0x7a4a00)
        else -> if (ciemny) kolor(0x0f2140) to kolor(0xdbe4f3) else kolor(0xe8f0fe) to kolor(0x0a1f44)
    }
    val ikona = if (k.pilny || k.poziom == "warning") Ikony.alarm else Ikony.informacja
    Row(
        Modifier.fillMaxWidth().background(tlo)
            .then(
                if (k.link != null) Modifier.clickable(role = Role.Button, onClickLabel = k.etykieta) {
                    if (k.link == "sklep") AktualizacjaAplikacji.otworzSklep(kontekst) else otworzStrone(kontekst, k.link)
                } else Modifier,
            )
            .padding(start = 16.dp, end = if (k.pilny) 16.dp else 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(ikona, null, tint = napis, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (k.tytul.isNotEmpty()) Text(k.tytul, color = napis, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (k.tekst.isNotEmpty()) Text(k.tekst, color = napis, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (k.link != null) Text(k.etykieta, color = napis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
        }
        if (!k.pilny) {
            val opis = tl("app.close", "Zamknij komunikat")
            Icon(
                Ikony.zamknij, null, tint = napis,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { m.zamknijKomunikat(k.id) }
                    .semantics { contentDescription = opis }.padding(9.dp).size(16.dp),
            )
        }
    }
}

/** Wymagana aktualizacja (panel podniósł config.minAppVersion.android): cały ekran z przyciskiem do Google Play. */
@Composable
fun EkranAktualizacji() {
    val kontekst = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(kolor(0x0a1f44)).windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.logo_noc), null, Modifier.height(56.dp))
        Text(tl("app.forceUpdateTitle", "Zaktualizuj aplikację TVML"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            tl("app.forceUpdateTextAndroid", "Ta wersja ({v}) jest już nieaktualna. Pobierz nową z Google Play, żeby dalej czytać wiadomości.")
                .replace("{v}", AktualizacjaAplikacji.biezaca),
            color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, textAlign = TextAlign.Center,
        )
        Text(
            tl("app.openGooglePlay", "Otwórz Google Play"),
            Modifier.widthIn(max = 320.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(kolor(0xe29818))
                .clickable(role = Role.Button) { AktualizacjaAplikacji.otworzSklep(kontekst) }.padding(vertical = 14.dp),
            color = kolor(0x1d1400), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
    }
}
