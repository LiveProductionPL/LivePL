package pl.tvml

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * TVML: serwis informacyjny tvml.pl natywnie, jak TVML na iPhonie. Wszystko ustawia redakcja w panelu
 * tvml.pl/app/panel (zakładki, bloki Startu, wersja wyglądu z kolorami i ikoną, pasek „NA ŻYWO”, imieniny).
 *
 * Do zrzutów ekranu: --es ekran pogoda|artykul|wideo|wiecej|szukaj, --es motyw zima|czapeczka|wigilia|…,
 * --es wyglad dzien|noc, --es ikona zwykla|zima|swieta, --es jezyk pl|uk|en|system.
 */
class MainActivity : ComponentActivity() {
    private lateinit var m: Magazyn
    /** Do testów ikony: --es ikona zwykla|zima|swieta (zmiana przy zejściu aplikacji z ekranu). */
    private var ikonaTestowa: IkonaAplikacji.Rodzaj? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Siec.przygotuj(this)
        Jezyk.przygotuj(this)
        SingletonImageLoader.setSafe { kontekst ->
            val klient = OkHttpClient.Builder()
                .addInterceptor { lancuch -> lancuch.proceed(lancuch.request().newBuilder().header("User-Agent", Siec.agent).build()) }
                .build()
            ImageLoader.Builder(kontekst)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { klient })) }
                .crossfade(true)
                .build()
        }
        m = Magazyn(this, lifecycleScope)
        argumenty(intent, start = true)

        setContent {
            val ciemny = when (m.wyglad) {
                "dzien" -> false
                "noc" -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(ciemny) {
                // baner jest zawsze ciemny, więc jasne ikony paska stanu; pasek nawigacji jak tło zakładek
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (ciemny) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }
            val barwy = if (ciemny) Barwy.noc else Barwy.dzien
            MaterialTheme(colorScheme = if (ciemny) darkColorScheme(surface = barwy.karta, background = barwy.tlo) else lightColorScheme(surface = barwy.karta, background = barwy.tlo)) {
                CompositionLocalProvider(LocalBarwy provides barwy, LocalCiemny provides ciemny, LocalMagazyn provides m) {
                    // zmiana języka buduje ekrany od nowa (bez restartu aplikacji), więc listy i strony pobierają się w nowym języku
                    key(Jezyk.biezacy) { Glowny(m) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        argumenty(intent, start = false)
    }

    override fun onResume() {
        super.onResume()
        // jak na iPhonie: po powrocie do aplikacji odświeżenie (najwyżej co 2 minuty); przy „Jak w systemie” także nowy język telefonu
        m.sprawdzJezyk()
        m.odswiez()
    }

    override fun onStop() {
        super.onStop()
        // ikona sezonowa z panelu zmienia się, gdy aplikacja schodzi z ekranu
        val ikona = ikonaTestowa ?: m.motyw.ikona.takeIf { m.motywTestowy == null }
        if (ikona != null) runCatching { IkonaAplikacji.ustaw(this, ikona) }
    }

    private fun argumenty(i: Intent, start: Boolean) {
        Motyw.zArgumentu(i.getStringExtra("motyw"))?.let { m.motywTestowy = it; m.motyw = it }
        i.getStringExtra("wyglad")?.let { m.ustawWyglad(it) }
        i.getStringExtra("jezyk")?.let { m.ustawJezyk(it) }
        i.getStringExtra("ikona")?.let { n -> ikonaTestowa = IkonaAplikacji.Rodzaj.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } }
        // link do artykułu: https://tvml.pl/artykul/123[-tytul]
        i.data?.lastPathSegment?.takeIf { i.data?.pathSegments?.firstOrNull() == "artykul" }
            ?.takeWhile { it.isDigit() }?.toIntOrNull()?.let { m.otworzArtykul(it) }
        val ekran = i.getStringExtra("ekran") ?: return
        lifecycleScope.launch {
            m.odswiezTeraz(wymus = start)
            when (ekran) {
                "pogoda" -> m.pokaz(ZakladkaApp.Cel.Pogoda)
                "wideo" -> m.pokaz(ZakladkaApp.Cel.Wideo)
                "wiecej" -> m.pokaz(ZakladkaApp.Cel.Wiecej)
                "szukaj" -> m.arkuszSzukaj = true
                "artykul" -> m.najnowsze.firstOrNull()?.let { m.otworz(it) }
            }
        }
    }
}
