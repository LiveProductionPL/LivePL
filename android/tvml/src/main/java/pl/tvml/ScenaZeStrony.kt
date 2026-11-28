package pl.tvml

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.json.JSONObject

/**
 * Scena pogody prosto ze strony tvml.pl (Karol 25.11: „Tak, róbcie”): ten sam kod co karta na stronie, więc każda
 * zmiana sceny na tvml.pl jest od razu w aplikacji, bez nowej wersji. Strona app-scena.php (TVML Connect, PR #170
 * i #172) rysuje samo płótno weather-effects, bez napisów, poduszek i pasa, na przezroczystym tle, i po pierwszej
 * klatce woła TVMLAndroid.gotowe({gotowe: true, scena, noc}); przy błędzie {gotowe: false, blad}. Do tego czasu,
 * bez internetu albo przy błędzie widać wbudowaną scenę aplikacji ([ScenaStrony]) jako zapas. Tak samo na iOS
 * (ScenaZeStrony.swift).
 */
object AdresSceny {
    /** PR #170 w Panel-TVML. Ładny adres /app/scena też działa; plik działa zawsze (tak samo iOS). */
    const val BAZA = "https://tvml.pl/app-scena.php"

    /** [tryb]: „karta” (Start, prawie kwadrat) albo „zakladka” (nagłówek zakładki Pogoda). */
    fun adres(tryb: String, bezRuchu: Boolean, podglad: String?): String {
        val u = Uri.parse(BAZA).buildUpon().appendQueryParameter("tryb", tryb)
        if (bezRuchu) u.appendQueryParameter("ruch", "0")
        // podgląd szkicu z panelu (kod QR); podgląd do zrzutów ma umowny token bez szkicu na serwerze
        if (podglad != null) u.appendQueryParameter("podglad", podglad)
        return u.build().toString()
    }

    /**
     * Adres sceny ze strony dla bieżącego stanu albo null (sama wbudowana): w „Podglądzie dnia” i przy scenie, motywie
     * albo porze wymuszonej argumentem do zrzutów zostaje wbudowana, bo strona pokazuje dzisiejszy dzień i pogodę.
     */
    fun dla(m: Magazyn, tryb: String): String? {
        if (m.podgladDniaTrwa || m.scenaTestowa != null || m.motywTestowy != null || m.nocTestowa != null || m.scenaWbudowana) return null
        val p = m.podglad
        return adres(tryb, !ValueAnimator.areAnimatorsEnabled(), p?.takeIf { !it.testowy }?.token)
    }
}

/**
 * Wbudowana scena pod spodem ([zapas], z argumentem „stoi”) i scena ze strony na wierzchu, gdy jest gotowa
 * (łagodnie, 0,35 s). Wbudowana stoi wtedy na jednej klatce, żeby nie zużywać baterii na dwie animacje naraz.
 * Po powrocie do aplikacji strona wczytuje się od nowa, gdy sceny ze strony jeszcze nie było (np. bez internetu)
 * albo minęło ponad 30 minut (nowy etap motywu, nowa pogoda). [adres] null = tylko wbudowana.
 */
@Composable
fun ScenaZZapasem(adres: String?, modifier: Modifier = Modifier, zapas: @Composable (stoi: Boolean) -> Unit) {
    if (adres == null) {
        Box(modifier) { zapas(false) }
        return
    }
    val kontekst = LocalContext.current
    val scena = remember { ScenaWWW() }
    val widocznosc by animateFloatAsState(if (scena.gotowa) 1f else 0f, tween(350), label = "scena ze strony")
    val cykl = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(cykl, scena) {
        var bylaPrzerwa = false
        val obserwator = LifecycleEventObserver { _, zdarzenie ->
            when (zdarzenie) {
                Lifecycle.Event.ON_STOP -> { bylaPrzerwa = true; scena.widok?.onPause() }
                Lifecycle.Event.ON_START -> scena.widok?.onResume()
                Lifecycle.Event.ON_RESUME -> if (bylaPrzerwa) {
                    bylaPrzerwa = false
                    if (!scena.gotowa || System.currentTimeMillis() - scena.wczytano > 30 * 60_000L) scena.wczytajOdNowa()
                }
                else -> {}
            }
        }
        cykl.addObserver(obserwator)
        onDispose {
            cykl.removeObserver(obserwator)
            scena.zniszcz()
        }
    }
    Box(modifier) {
        zapas(scena.gotowa)
        // nowy widok po zamknięciu procesu strony przez system (onRenderProcessGone)
        key(scena.pokolenie) {
            AndroidView(
                factory = { scena.utworz(kontekst, adres) },
                update = { if (scena.adres != adres) scena.wczytaj(adres) },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = widocznosc }.clearAndSetSemantics { },
            )
        }
    }
}

/** Jeden WebView ze sceną: stan „gotowa”, czas wczytania i wymiana widoku po awarii procesu strony. */
class ScenaWWW {
    var gotowa by mutableStateOf(false)
        private set
    var pokolenie by mutableIntStateOf(0)
        private set
    var widok: WebView? = null
        private set
    var adres: String? = null
        private set
    var wczytano = 0L
        private set
    private val glowny = Handler(Looper.getMainLooper())

    private fun ustaw(wartosc: Boolean) = glowny.post { gotowa = wartosc }

    @SuppressLint("SetJavaScriptEnabled")
    fun utworz(kontekst: Context, a: String): WebView {
        widok?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        val w = WidokSceny(kontekst)
        w.setBackgroundColor(Color.TRANSPARENT)
        w.isVerticalScrollBarEnabled = false
        w.isHorizontalScrollBarEnabled = false
        w.overScrollMode = View.OVER_SCROLL_NEVER
        w.isFocusable = false
        w.isFocusableInTouchMode = false
        w.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        w.settings.apply {
            javaScriptEnabled = true
            // trwała pamięć przeglądarki: skrypty sceny z ?v= zostają w telefonie także po ponownym uruchomieniu
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = "$userAgentString ${Siec.agent}"
        }
        w.addJavascriptInterface(Most(), "TVMLAndroid")
        w.webViewClient = Klient()
        widok = w
        wczytaj(a)
        return w
    }

    fun wczytaj(a: String) {
        adres = a
        wczytano = System.currentTimeMillis()
        ustaw(false)
        widok?.loadUrl(a)
    }

    fun wczytajOdNowa() {
        adres?.let { wczytaj(it) }
    }

    fun zniszcz() {
        glowny.removeCallbacksAndMessages(null)
        widok?.let {
            it.stopLoading()
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        widok = null
    }

    /** Sygnał ze strony (app-scena.js): {gotowe: true, scena, noc} po pierwszej klatce, {gotowe: false, blad} przy błędzie. */
    private inner class Most {
        @JavascriptInterface
        fun gotowe(json: String?) {
            val g = runCatching { JSONObject(json ?: "").optBoolean("gotowe", false) }.getOrDefault(false)
            if (BuildConfig.DEBUG) android.util.Log.d("TVML-Scena", "$json ($adres)")
            ustaw(g)
        }
    }

    private inner class Klient : WebViewClient() {
        /** Tylko strona sceny na tvml.pl, bez linków: inne adresy i stuknięcia w odnośniki się nie otwierają. */
        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
            val host = r.url.host.orEmpty()
            val tvml = r.url.scheme == "https" && (host == "tvml.pl" || host.endsWith(".tvml.pl"))
            return !(tvml && !r.hasGesture())
        }

        override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
            if (r.isForMainFrame) ustaw(false)
        }

        /** Strona bez sceny (np. 404, awaria serwera) nie wyśle „gotowe”: zostaje zapas. */
        override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, odp: WebResourceResponse) {
            if (r.isForMainFrame && odp.statusCode != 200) {
                ustaw(false)
                v.stopLoading()
            }
        }

        /** System zamknął proces strony (np. przy braku pamięci): zapas i nowy widok zamiast zamknięcia aplikacji. */
        override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
            if (v === widok) {
                (v.parent as? ViewGroup)?.removeView(v)
                v.destroy()
                widok = null
                gotowa = false
                pokolenie++
            }
            return true
        }
    }
}

/** WebView bez dotyku: stuknięcie w kartę przechodzi do karty (otwiera zakładkę Pogoda), strona go nie dostaje. */
private class WidokSceny(kontekst: Context) : WebView(kontekst) {
    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(e: MotionEvent?): Boolean = false
    override fun onTouchEvent(e: MotionEvent?): Boolean = false
}
