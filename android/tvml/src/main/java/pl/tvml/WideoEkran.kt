package pl.tvml

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val tloWideo = kolor(0x070d1a)
private val szaryWideo = kolor(0xa8b3c8)

/** Wideo z kanału YouTube TVML (@TVMLlive): wyróżniony film i lista. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WideoEkran(m: Magazyn) {
    val zakres = rememberCoroutineScope()
    var odtwarzany by rememberSaveable { mutableStateOf<String?>(null) }
    PullToRefreshBox(isRefreshing = m.laduje, onRefresh = { zakres.launch { m.odswiezTeraz(wymus = true) } }, modifier = Modifier.fillMaxSize().background(tloWideo)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "naglowek") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tl("app.video", "Wideo"), Modifier.weight(1f), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = KrojNaglowkow)
                    Icon(Ikony.youtube, null, tint = szaryWideo, modifier = Modifier.size(16.dp))
                    Text(" YouTube @TVMLlive", color = szaryWideo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (m.filmy.isEmpty()) item(key = "laduje") { Postep() }
            m.filmy.firstOrNull()?.let { f ->
                item(key = f.id) {
                    Column(Modifier.clickable(role = Role.Button) { odtwarzany = f.id }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(kolor(0x1c2840))) {
                            AsyncImage(f.obraz, null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
                            Text(
                                tl("app.featured", "Wyróżnione").uppercase(Jezyk.locale), Modifier.padding(12.dp).clip(RoundedCornerShape(6.dp)).background(kolor(0xc8102e)).padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                            )
                            Box(Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Barwy.zloto.copy(alpha = 0.95f)), contentAlignment = Alignment.Center) {
                                Icon(Ikony.odtworz, tl("app.playVideo", "Odtwórz"), tint = kolor(0x1d1400), modifier = Modifier.size(26.dp))
                            }
                        }
                        Text(f.tytul, color = Color.White, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
                        Text(f.kiedy, color = szaryWideo, fontSize = 12.sp)
                    }
                }
            }
            items(m.filmy.drop(1), key = { it.id }) { f ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { odtwarzany = f.id }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    KafelFilmu(f, Modifier.width(150.dp), zPodpisem = false, wlasnyKlik = false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(f.tytul, color = Color.White, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(f.kiedy, color = szaryWideo, fontSize = 12.sp)
                    }
                }
            }
            // na końcu listy przycisk „Starsze filmy” (app.videoMore): kolejne 15 po stuknięciu, nie wszystkie od razu
            // (Karol 25.11: „miało wczytywac filmy po 15 i dac opcje wczytania kolejnych”)
            if (m.filmyKursor != null && m.filmy.isNotEmpty()) item(key = "starsze") {
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    if (m.ladujeFilmy) {
                        Postep()
                    } else {
                        Text(
                            tl("app.videoMore", "Starsze filmy"),
                            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
                                .clickable(role = Role.Button) { zakres.launch { m.wiecejFilmow() } }
                                .padding(horizontal = 22.dp, vertical = 12.dp),
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
    odtwarzany?.let { Odtwarzacz(it) { odtwarzany = null } }
}

/** Miniatura filmu z przyciskiem odtwarzania; stuknięcie otwiera odtwarzacz (chyba że robi to wiersz listy). */
@Composable
fun KafelFilmu(f: Film, modifier: Modifier = Modifier, zPodpisem: Boolean = true, wlasnyKlik: Boolean = true) {
    val b = LocalBarwy.current
    var otwarty by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.then(if (wlasnyKlik) Modifier.clickable(role = Role.Button) { otwarty = true } else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = tl("app.videoItem", "Film") + ": ${f.tytul}" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(kolor(0x1c2840)), contentAlignment = Alignment.Center) {
            AsyncImage(f.obraz, null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Ikony.odtworz, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        if (zPodpisem) Text(f.tytul, color = b.tekst, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (otwarty) Odtwarzacz(f.id) { otwarty = false }
}

/** Stany odtwarzacza YouTube IFrame API (YT.PlayerState). */
private object StanYT {
    const val NIE_RUSZYL = -1
    const val KONIEC = 0
    const val GRA = 1
    const val BUFOROWANIE = 3
}

private val ID_FILMU = Regex("[A-Za-z0-9_-]{6,20}")

private fun Context.aktywnosc(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Most JavaScript → Kotlin: stan, czas i długość filmu co pół sekundy, kod błędu YouTube. Zawsze na wątku głównym. */
private class MostFilmu(val naStan: (Int, Double, Double) -> Unit, val naBlad: (Int) -> Unit) {
    private val glowny = Handler(Looper.getMainLooper())
    @JavascriptInterface fun stan(s: Int, czas: Double, dlugosc: Double) { glowny.post { naStan(s, czas, dlugosc) } }
    @JavascriptInterface fun blad(kod: Int) { glowny.post { naBlad(kod) } }
}

private fun mmss(s: Double): String {
    val c = s.coerceAtLeast(0.0).toInt()
    return if (c >= 3600) "%d:%02d:%02d".format(c / 3600, c / 60 % 60, c % 60) else "%d:%02d".format(c / 60, c % 60)
}

/**
 * Film z YouTube od razu na pełnym ekranie w poziomie (Karol, 24.11, „droga 1”): odtwarzacz IFrame API bez
 * przycisków i podpowiedzi YouTube (controls=0, rel=0, iv_load_policy=3, fs=0, disablekb=1), z własnym paskiem:
 * start/pauza, ±10 s, postęp z przewijaniem, zamknij. Logo i tytułu YouTube nie zasłaniamy (warunki YouTube API):
 * róg z logo zostaje wolny, a stuknięcia przechodzą do odtwarzacza. youtube-nocookie na stronie o adresie
 * https://tvml.pl (bez tego „Błąd 153”). Paski systemowe schowane (wracają po przesunięciu palcem); po zamknięciu
 * poprzednia orientacja. Przy błędzie odtwarzania „Otwórz w YouTube”.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun Odtwarzacz(id: String, zamknij: () -> Unit) {
    val kontekst = LocalContext.current
    val aktywnosc = remember(kontekst) { kontekst.aktywnosc() }
    DisposableEffect(aktywnosc) {
        val poprzednia = aktywnosc?.requestedOrientation
        aktywnosc?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (aktywnosc != null && poprzednia != null) aktywnosc.requestedOrientation = poprzednia }
    }
    var stan by remember { mutableIntStateOf(StanYT.NIE_RUSZYL) }
    var czas by remember { mutableDoubleStateOf(0.0) }
    var dlugosc by remember { mutableDoubleStateOf(0.0) }
    var przeciagany by remember { mutableStateOf<Float?>(null) }
    var blad by remember { mutableStateOf(if (ID_FILMU.matches(id)) null else 2) }
    var widoczne by remember { mutableStateOf(true) }
    var dotyk by remember { mutableLongStateOf(0L) }
    var web by remember { mutableStateOf<WebView?>(null) }
    // z TalkBackiem pasek zostaje cały czas (jak z VoiceOver na iPhonie)
    val czytnik = remember(kontekst) {
        (kontekst.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager)?.isTouchExplorationEnabled == true
    }
    fun pokaz() { widoczne = true; dotyk = System.nanoTime() }
    fun js(kod: String) { web?.evaluateJavascript("try{$kod}catch(e){}", null); pokaz() }
    // pasek chowa się po 3 s grania (z TalkBackiem dłużej, według ustawień dostępności)
    val dostepnosc = LocalAccessibilityManager.current
    LaunchedEffect(widoczne, dotyk, stan) {
        if (widoczne && stan == StanYT.GRA && przeciagany == null) {
            delay(dostepnosc?.calculateRecommendedTimeoutMillis(3000, containsIcons = true, containsControls = true) ?: 3000)
            widoczne = false
        }
    }
    // w tle film staje (i nie gra dźwięk), po powrocie można go wznowić paskiem
    val cykl = LocalLifecycleOwner.current
    DisposableEffect(cykl) {
        val obserwator = LifecycleEventObserver { _, z ->
            when (z) {
                Lifecycle.Event.ON_PAUSE -> { web?.evaluateJavascript("try{gracz.pauseVideo()}catch(e){}", null); web?.onPause() }
                Lifecycle.Event.ON_RESUME -> web?.onResume()
                else -> {}
            }
        }
        cykl.lifecycle.addObserver(obserwator)
        onDispose { cykl.lifecycle.removeObserver(obserwator) }
    }
    Dialog(onDismissRequest = zamknij, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val widok = LocalView.current
        DisposableEffect(widok) {
            val okno = (widok.parent as? DialogWindowProvider)?.window
            val sterownik = okno?.let { WindowCompat.getInsetsController(it, widok) }
            sterownik?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            sterownik?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { sterownik?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (blad == null) AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = settings.userAgentString + " " + Siec.agent
                        webViewClient = object : WebViewClient() {
                            // logo YouTube i tytuł filmu prowadzą do YouTube: w aplikacji YouTube albo w przeglądarce
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                if (!r.isForMainFrame) return false
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, r.url)) }
                                return true
                            }
                        }
                        addJavascriptInterface(MostFilmu({ s, t, d -> stan = s; czas = t; dlugosc = d }, { k -> blad = k }), "TVML")
                        if (BuildConfig.DEBUG) webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                                android.util.Log.d("TVML-Film", "${m.message()} (${m.lineNumber()})")
                                return true
                            }
                        }
                        val strona = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                            |<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;inset:0;width:100%;height:100%}</style></head>
                            |<body><div id="p"></div><script>
                            |var gracz;
                            |function zglos(){try{TVML.stan(gracz.getPlayerState(),gracz.getCurrentTime()||0,gracz.getDuration()||0)}catch(e){}}
                            |function onYouTubeIframeAPIReady(){gracz=new YT.Player('p',{host:'https://www.youtube-nocookie.com',videoId:'$id',width:'100%',height:'100%',
                            |playerVars:{autoplay:1,controls:0,rel:0,iv_load_policy:3,fs:0,playsinline:1,disablekb:1,origin:'https://tvml.pl'},
                            |events:{onReady:function(e){e.target.playVideo();zglos()},onStateChange:zglos,onError:function(e){TVML.blad(e.data|0)}}})}
                            |setInterval(zglos,500);
                            |</script><script src="https://www.youtube.com/iframe_api"></script></body></html>""".trimMargin()
                        loadDataWithBaseURL(Siec.BAZA, strona, "text/html", "utf-8", null)
                        web = this
                    }
                },
                onRelease = { it.destroy(); web = null },
                modifier = Modifier.fillMaxSize(),
            )
            // przezroczysta warstwa nad filmem (bez przyciemniania, logo i tytuł YouTube zostają widoczne): stuknięcie
            // pokazuje albo chowa pasek, więc nic nie otwiera YouTube przypadkiem (jak na iPhonie)
            Box(
                Modifier.fillMaxSize().clearAndSetSemantics { }
                    .pointerInput(Unit) { detectTapGestures { if (widoczne) widoczne = false else pokaz() } },
            )
            if (blad != null) {
                // błąd YouTube (osadzanie wyłączone, „potwierdź, że nie jesteś botem”…): czarne tło zasłania stronę błędu,
                // zostaje komunikat, „Otwórz w YouTube” i samo „Zamknij”
                Box(Modifier.fillMaxSize().background(Color.Black))
                BladFilmu(id)
                PrzyciskFilmu(Ikony.zamknij, tl("app.close", "Zamknij"), Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 12.dp), 44.dp, onClick = zamknij)
            } else AnimatedVisibility(widoczne || stan != StanYT.GRA || czytnik, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
                PasekFilmu(
                    gotowy = dlugosc > 0,
                    gra = stan == StanYT.GRA || stan == StanYT.BUFOROWANIE,
                    czas = przeciagany?.let { it * dlugosc } ?: czas, dlugosc = dlugosc,
                    naPrzycisk = {
                        when (stan) {
                            StanYT.GRA, StanYT.BUFOROWANIE -> js("gracz.pauseVideo()")
                            StanYT.KONIEC -> js("gracz.seekTo(0,true);gracz.playVideo()")
                            else -> js("gracz.playVideo()")
                        }
                    },
                    naPrzesun = { o ->
                        val cel = (czas + o).coerceIn(0.0, (dlugosc - 1).coerceAtLeast(0.0))
                        czas = cel
                        js("gracz.seekTo($cel,true)")
                    },
                    naSuwak = { przeciagany = it; pokaz() },
                    naSuwakKoniec = { przeciagany?.let { u -> js("gracz.seekTo(${u * dlugosc},true)"); czas = u * dlugosc }; przeciagany = null },
                    zamknij = zamknij,
                )
            }
        }
    }
}

/**
 * Własny pasek filmu jak na iPhonie: zamknij w lewym górnym rogu, przyciski na środku (nieaktywne, dopóki film
 * się nie wczyta), postęp na dole z wolnym prawym miejscem na logo YouTube.
 */
@Composable
private fun PasekFilmu(
    gotowy: Boolean, gra: Boolean, czas: Double, dlugosc: Double,
    naPrzycisk: () -> Unit, naPrzesun: (Int) -> Unit, naSuwak: (Float) -> Unit, naSuwakKoniec: () -> Unit, zamknij: () -> Unit,
) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        PrzyciskFilmu(Ikony.zamknij, tl("app.close", "Zamknij"), Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 12.dp), 44.dp, onClick = zamknij)
        Row(
            Modifier.align(Alignment.Center).graphicsLayer { alpha = if (gotowy) 1f else 0.5f },
            horizontalArrangement = Arrangement.spacedBy(44.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            PrzyciskFilmu(Ikony.cofnij, tl("app.videoBack10", "Cofnij o 10 sekund"), rozmiar = 57.dp, napis = "10", wlaczony = gotowy) { naPrzesun(-10) }
            PrzyciskFilmu(
                if (gra) Ikony.pauza else Ikony.odtworz,
                if (gra) tl("app.pause", "Pauza") else tl("app.play", "Odtwórz"),
                rozmiar = 76.dp, wlaczony = gotowy, onClick = naPrzycisk,
            )
            PrzyciskFilmu(Ikony.przewin, tl("app.videoForward10", "Do przodu o 10 sekund"), rozmiar = 57.dp, napis = "10", wlaczony = gotowy) { naPrzesun(10) }
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 24.dp, end = 130.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(mmss(czas), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val opis = tl("app.videoProgress", "Postęp filmu")
            val wartosc = "${mmss(czas)} / ${mmss(dlugosc)}"
            Slider(
                value = if (dlugosc > 0) (czas / dlugosc).toFloat().coerceIn(0f, 1f) else 0f,
                onValueChange = naSuwak, onValueChangeFinished = naSuwakKoniec, enabled = gotowy,
                modifier = Modifier.weight(1f).semantics { contentDescription = opis; stateDescription = wartosc },
                colors = SliderDefaults.colors(thumbColor = Barwy.zloto, activeTrackColor = Barwy.zloto, inactiveTrackColor = Color.White.copy(alpha = 0.35f)),
            )
            Text(mmss(dlugosc), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PrzyciskFilmu(
    ikona: androidx.compose.ui.graphics.vector.ImageVector, opis: String, modifier: Modifier = Modifier, rozmiar: androidx.compose.ui.unit.Dp,
    napis: String? = null, wlaczony: Boolean = true, onClick: () -> Unit,
) {
    Box(
        modifier.size(rozmiar).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            .clickable(enabled = wlaczony, role = Role.Button, onClickLabel = opis, onClick = onClick).semantics { contentDescription = opis },
        contentAlignment = Alignment.Center,
    ) {
        Icon(ikona, null, tint = Color.White, modifier = Modifier.size(rozmiar * 0.5f))
        if (napis != null) Text(napis, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Film, którego YouTube nie pozwala odtworzyć w aplikacji (osadzanie wyłączone, „potwierdź, że nie jesteś botem”):
 * komunikat i przejście do YouTube, jak na iPhonie (komunikatBledu).
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.BladFilmu(id: String) {
    val kontekst = LocalContext.current
    Column(
        Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tl("app.videoUnavailable", "Tego filmu nie da się odtworzyć w aplikacji."), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            tl("app.openYoutube", "Otwórz w YouTube"),
            Modifier.clip(CircleShape).background(Barwy.zloto).clickable(role = Role.Button) {
                runCatching { kontekst.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$id".toUri())) }
            }.padding(horizontal = 18.dp, vertical = 10.dp),
            color = kolor(0x1d1400), fontSize = 15.sp, fontWeight = FontWeight.Bold,
        )
    }
}
