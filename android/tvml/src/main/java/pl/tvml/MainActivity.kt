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
 * --es wyglad dzien|noc, --es ikona zwykla|zima|swieta, --es jezyk pl|uk|en|system, --ez bezPowiadomien true,
 * --es scena autumn-forest|leszno-andrzejki|leszno-zima|… (scena pogody ze strony, ScenyStrony.znane), --es pora dzien|noc,
 * --ez dzienTestowy true (belka karty pogody z 11 listopada: święto i trzy imiona),
 * --es komunikat aktualizacja|redakcja|pilny|wymagana (pasek nowej wersji, komunikat redakcji, ekran aktualizacji),
 * w wersji testowej --es ekran alert --ez alertNatywny true [--es alertPrzyklad YES|wyslane] (formularz Alertu TVML).
 */
class MainActivity : ComponentActivity() {
    private lateinit var m: Magazyn
    /** Do testów ikony: --es ikona zwykla|zima|swieta (zmiana przy zejściu aplikacji z ekranu). */
    private var ikonaTestowa: IkonaAplikacji.Rodzaj? = null

    /**
     * Po config z serwera: kanały powiadomień i rejestracja (albo wyrejestrowanie) z aktualnymi tematami i językiem.
     * O zgodę aplikacja sama nie pyta: dopiero po włączeniu w Więcej → Powiadomienia (PowiadomieniaEkran).
     */
    private fun powiadomienia() {
        if (!Powiadomienia.dostepne(this)) return
        Powiadomienia.przygotujKanaly(this)
        Powiadomienia.zarejestruj(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Siec.przygotuj(this)
        Jezyk.przygotuj(this)
        Powiadomienia.przygotuj(this)
        SingletonImageLoader.setSafe { kontekst ->
            val klient = OkHttpClient.Builder()
                .addInterceptor { lancuch -> lancuch.proceed(lancuch.request().newBuilder().header("User-Agent", Siec.agent).build()) }
                .build()
            ImageLoader.Builder(kontekst)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { klient }))
                    // sceny pogody SVG ze Studia motywów (ScenaStrony)
                    add(coil3.svg.SvgDecoder.Factory())
                }
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
            LaunchedEffect(m.konfiguracjaPush) { if (m.konfiguracjaPush > 0) powiadomienia() }
            // granice okien motywów (np. Andrzejki 28.11 od 16:00): scena zmienia się od razu, a config dociąga etap motywu
            LaunchedEffect(m.nadchodzaceSceny, m.koniecMotywu) {
                while (true) {
                    val nast = m.nastepnaZmianaSceny() ?: break
                    val ile = java.time.Duration.between(java.time.ZonedDateTime.now(Daty.strefa), nast).toMillis()
                    kotlinx.coroutines.delay(ile.coerceIn(1_000, 6 * 3_600_000L) + 1_000)
                    m.zegarScen++
                    if (!java.time.ZonedDateTime.now(Daty.strefa).isBefore(nast)) m.odswiez(wymus = true)
                }
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
        m.wznowienia++
        m.odswiez()
        // zgoda na powiadomienia mogła się zmienić w ustawieniach telefonu
        val zgodaPrzed = Powiadomienia.zgoda
        Powiadomienia.przygotuj(this)
        if (Powiadomienia.zgoda != zgodaPrzed) Powiadomienia.zarejestruj(this)
    }

    override fun onStart() {
        super.onStart()
        m.naEkranie = true
    }

    override fun onStop() {
        super.onStop()
        m.naEkranie = false
        // ikona sezonowa z panelu zmienia się, gdy aplikacja schodzi z ekranu
        val ikona = ikonaTestowa ?: m.motyw.ikona.takeIf { m.motywTestowy == null }
        if (ikona != null) runCatching { IkonaAplikacji.ustaw(this, ikona) }
    }

    private fun argumenty(i: Intent, start: Boolean) {
        Motyw.zArgumentu(i.getStringExtra("motyw"))?.let { m.motywTestowy = it; m.motyw = it }
        i.getStringExtra("wyglad")?.let { m.ustawWyglad(it) }
        i.getStringExtra("jezyk")?.let { m.ustawJezyk(it) }
        if (i.getBooleanExtra("bezPowiadomien", false)) Powiadomienia.wylaczDoZrzutow(this)
        i.getStringExtra("scena")?.let { m.scenaTestowa = it; m.zegarScen++ }
        i.getStringExtra("komunikat")?.let { m.komunikatTestowy = it }
        if (BuildConfig.DEBUG) i.getStringExtra("wylacz")?.let { ScenyStrony.wylaczone = it.split(',').map { s -> s.trim() }.toSet() }
        if (i.getBooleanExtra("dzienTestowy", false)) {
            m.dzienTestowy = true
            m.dzisiaj = Dzisiaj("poniedziałek, 11 listopada 2026", listOf("Bartłomieja", "Marcina", "Prota"), java.time.LocalDate.of(2026, 11, 11), "Narodowe Święto Niepodległości")
            // karta pokazuje dane dnia tylko z bieżącego dnia, więc chwila też 11 listopada (o ile nie podano innej)
            if (i.getStringExtra("chwila") == null) m.chwilaTestowa = java.time.ZonedDateTime.of(2026, 11, 11, 12, 0, 0, 0, Daty.strefa)
        }
        i.getStringExtra("chwila")?.let { m.chwilaTestowa = Daty.iso(it); m.zegarScen++ }
        i.getStringExtra("pora")?.let { m.nocTestowa = when (it) { "noc" -> true; "dzien" -> false; else -> null } }
        i.getStringExtra("ikona")?.let { n -> ikonaTestowa = IkonaAplikacji.Rodzaj.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } }
        // link do artykułu: https://tvml.pl/artykul/123[-tytul]
        i.data?.lastPathSegment?.takeIf { i.data?.pathSegments?.firstOrNull() == "artykul" }
            ?.takeWhile { it.isDigit() }?.toIntOrNull()?.let { m.otworzArtykul(it) }
        // podgląd szkicu z panelu (kod QR): https://tvml.pl/app/podglad?t=… albo tvml://preview?t=…
        i.data?.let { u ->
            val podglad = (u.scheme == "tvml" && u.host == "preview") || (u.host == "tvml.pl" && u.path.orEmpty().startsWith("/app/podglad"))
            if (podglad) u.getQueryParameter("t")?.let { m.wlaczPodglad(it) }
        }
        // stuknięte powiadomienie: dane wiadomości FCM w extras (articleId albo url do artykułu)
        Powiadomienia.artykul(i)?.let { id ->
            listOf("articleId", "url").forEach { i.removeExtra(it) }
            m.otworzArtykul(id)
        }
        // Alert TVML, tylko w wersji testowej (jak -alert-natywny i -alert-przyklad na iPhonie): --ez alertNatywny true
        // (formularz w aplikacji bez config.alerts.native), --es alertPrzyklad YES|wyslane (przykład przewinięty na dół
        // albo potwierdzenie A-1234; nic nie idzie do serwera). Formularz otwiera --es ekran alert.
        if (BuildConfig.DEBUG) {
            if (i.getBooleanExtra("alertNatywny", false)) m.alertNatywnyTestowy = true
            i.getStringExtra("alertPrzyklad")?.let {
                m.alertPrzyklad = it
                m.formularzAlertu?.sprzataj()
                m.formularzAlertu = null
            }
        }
        // do zrzutów: pasek podglądu bez pytania serwera (jak -ekran podglad na iPhonie)
        if (i.getStringExtra("ekran") == "podglad") m.podgladTestowy()
        val ekran = i.getStringExtra("ekran") ?: return
        lifecycleScope.launch {
            m.odswiezTeraz(wymus = start)
            when (ekran) {
                "pogoda" -> m.pokaz(ZakladkaApp.Cel.Pogoda)
                "wideo" -> m.pokaz(ZakladkaApp.Cel.Wideo)
                "wiecej" -> m.pokaz(ZakladkaApp.Cel.Wiecej)
                "szukaj" -> m.arkuszSzukaj = true
                "artykul" -> m.najnowsze.firstOrNull()?.let { m.otworz(it) }
                // jak stuknięcie „Zgłoś zdarzenie”: formularz w aplikacji albo strona (bez config.alerts.native i alertNatywny)
                "alert" -> if (BuildConfig.DEBUG) m.zglosZdarzenie(this@MainActivity)
            }
        }
    }
}
