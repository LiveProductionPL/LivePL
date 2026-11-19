package eu.livepl.przegladarka

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import eu.livepl.przegladarka.ui.Ikona
import eu.livepl.przegladarka.ui.OknoStrony
import eu.livepl.przegladarka.ui.PasekPostepu
import eu.livepl.przegladarka.ui.PelnyEkranWidok
import eu.livepl.przegladarka.ui.WidokBledu
import androidx.compose.ui.graphics.Color as KolorCompose

/**
 * Aplikacja jednej strony na cały ekran, bez paska adresu: Poczta LivePL (poczta.livepl.eu) i LivePL Connect
 * (connect.livepl.eu). Ten sam silnik co przeglądarka LivePL: pobieranie plików, certyfikaty LivePL, rejestracja
 * urządzenia, logowania zapamiętane w telefonie. Linki do innych stron otwierają się w przeglądarce.
 */
abstract class JednaStronaActivity : AktywnoscLivePL() {
    /** Kolor góry strony (pod paskiem stanu), w dzień i w nocy ten sam. */
    protected abstract val kolorGory: Long

    /** Jedyne hosty, które otwierają się w aplikacji. */
    protected abstract val dozwoloneHosty: Set<String>

    /** Program z programy.json albo adres, od którego aplikacja startuje. */
    protected open val program: String? = null
    protected open val adres: String? = null

    /** Odświeżanie przeciągnięciem, zaznaczanie tekstu przytrzymaniem i most „ekran nie gaśnie”. */
    protected open val odswiezanie: Boolean = true
    protected open val dlugieDotkniecie: Boolean = true
    protected open val mostEkranu: Boolean = false

    /** Liczba nieprzeczytanych z tytułu strony w nazwie okna na liście ostatnich aplikacji. */
    protected open val licznikWTytule: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        dolaczPrzegladarke()
        p.dozwoloneHosty = dozwoloneHosty
        p.odswiezaniePrzeciagnieciem = odswiezanie
        p.dlugieDotkniecie = dlugieDotkniecie
        p.mostEkranu = mostEkranu
        super.onCreate(savedInstanceState)

        val nocTeraz = noc
        // góra strony jest ciemna w dzień i w nocy, więc jasne ikony na pasku stanu
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(
                Color.argb(0xe6, 0xff, 0xff, 0xff),
                Color.argb(0x80, 0x1b, 0x1b, 0x1b),
            ) { nocTeraz },
        )
        val gora = KolorCompose(kolorGory)
        val licznik = licznikWTytule
        setContent {
            LivePLMotyw { JednaStrona(p, this, gora, licznik) }
        }

        p.uruchom()
        if (p.karty.isEmpty()) {
            val zProgramu = program?.let { p.program(it) }
            when {
                zProgramu != null -> p.otworz(zProgramu)
                adres != null -> p.otworz(adres!!)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

/**
 * Strony na telefon same robią miejsce na paski (env(safe-area-inset-*)), ale WebView podaje te odstępy
 * poprawnie dopiero od wersji 140. Na starszym WebView aplikacja sama zostawia miejsce na paski.
 */
private fun webViewZnaMarginesy(aktywnosc: Activity): Boolean = try {
    val wersja = WebViewCompat.getCurrentWebViewPackage(aktywnosc)?.versionName.orEmpty()
    (wersja.substringBefore('.').toIntOrNull() ?: 0) >= 140
} catch (_: Exception) {
    false
}

@Composable
private fun JednaStrona(p: Przegladarka, aktywnosc: Activity, gora: KolorCompose, licznik: Boolean) {
    val glowna = p.karty.firstOrNull { it.otwierajaca == null }
    // okno otwarte przez stronę (podgląd załącznika, drugie konto) jest nad stroną, z „Gotowe”
    val okno = p.aktywna?.takeIf { it !== glowna }
    val naCalyEkran = remember { webViewZnaMarginesy(aktywnosc) }

    BackHandler {
        val w = glowna?.webViewJesli
        when {
            okno != null -> p.zamknij(okno)
            glowna?.schowajBlad() == true -> Unit
            // np. w Connect cofnięcie kończy sesję (strona używa history.pushState)
            w != null && w.canGoBack() -> w.goBack()
            else -> aktywnosc.moveTaskToBack(true)
        }
    }

    if (licznik) {
        // liczba nieprzeczytanych z tytułu (np. „(12) Poczta”) w nazwie okna na liście ostatnich aplikacji
        LaunchedEffect(glowna?.tytul) {
            val liczba = nieprzeczytane(glowna?.tytul.orEmpty())
            val nazwa = aktywnosc.getString(R.string.app_name) + if (liczba > 0) " ($liczba)" else ""
            val opis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityManager.TaskDescription.Builder().setLabel(nazwa).setPrimaryColor(gora.toArgb()).build()
            } else {
                @Suppress("DEPRECATION")
                ActivityManager.TaskDescription(nazwa)
            }
            aktywnosc.setTaskDescription(opis)
        }
    }

    Box(Modifier.fillMaxSize().background(gora)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            if (!naCalyEkran) Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (glowna != null) StronaGlowna(glowna, naCalyEkran)
            }
            if (!naCalyEkran) {
                Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(Kolor.tlo))
            }
        }
        okno?.let { OknoNadStrona(p, it) }
        p.okna.firstOrNull()?.let { key(it) { OknoStrony(p, it) } }
        p.pelnyEkran?.let { PelnyEkranWidok(p, it) }
    }
}

@Composable
private fun StronaGlowna(karta: Karta, naCalyEkran: Boolean) {
    Box(Modifier.fillMaxSize()) {
        StronaKarty(karta)
        karta.blad?.let { WidokBledu(it) { karta.sprobujPonownie() } }
        if (karta.laduje) PasekPostepu(karta.postep, if (naCalyEkran) Modifier.statusBarsPadding() else Modifier)
    }
}

/** Okno otwarte przez stronę, z „Gotowe”, jak arkusz w aplikacjach na iPhonie. */
@Composable
private fun OknoNadStrona(p: Przegladarka, karta: Karta) {
    Column(Modifier.fillMaxSize().background(Kolor.tlo).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(Kolor.karta).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { karta.adres?.let { udostepnij(p, it) } }, enabled = karta.adres != null) {
                Ikona(R.drawable.ic_udostepnij, "Udostępnij", Kolor.tekst, 22.dp)
            }
            Text(
                karta.tytul.ifEmpty { Adres.host(karta.adres) },
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Kolor.tekst,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { p.zamknij(karta) }) { Text("Gotowe", fontWeight = FontWeight.SemiBold) }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Kolor.ramka))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            StronaKarty(karta)
            if (karta.laduje) PasekPostepu(karta.postep)
        }
    }
}

@Composable
private fun StronaKarty(karta: Karta) {
    AndroidView(
        factory = { karta.kontener.also { k -> (k.parent as? ViewGroup)?.removeView(k) } },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun udostepnij(p: Przegladarka, adres: String) {
    val tresc = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, adres)
    p.aktywnosc?.startActivity(Intent.createChooser(tresc, null))
}

/** „(12) Poczta” albo „12 nieprzeczytanych” → 12. */
private fun nieprzeczytane(tytul: String): Int {
    for (wzor in listOf(Regex("""^\s*\((\d+)\)"""), Regex("""(\d+)\s+nieprzeczytan""", RegexOption.IGNORE_CASE))) {
        wzor.find(tytul)?.let { return it.groupValues[1].toIntOrNull() ?: 0 }
    }
    return 0
}
