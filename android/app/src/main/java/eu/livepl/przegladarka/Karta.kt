package eu.livepl.przegladarka

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.ClientCertRequest
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Błąd otwierania strony, pokazywany zamiast pustego ekranu. */
data class BladStrony(val opis: String, val adres: String?)

/**
 * Jedna karta: program z paska albo zwykła strona. Każda ma własny WebView (tworzony dopiero,
 * gdy jest potrzebny), a wszystkie dzielą zapamiętane logowania (ciasteczka) w tym telefonie.
 */
class Karta(val programId: String?, private val p: Przegladarka) {
    val id: Long = ++licznik
    var otwierajaca: Karta? = null
    /** Tłumaczenie tej strony na polski (ML Kit, w telefonie). */
    val tlumaczenie = Tlumaczenie(this, p)

    var tytul by mutableStateOf("")
    var adres by mutableStateOf<String?>(null)
        private set
    var postep by mutableIntStateOf(0)
        private set
    var laduje by mutableStateOf(false)
        private set
    var mozeWstecz by mutableStateOf(false)
        private set
    var mozeDalej by mutableStateOf(false)
        private set
    var blad by mutableStateOf<BladStrony?>(null)
        private set
    /** Wynik „Znajdź na stronie”: który z ilu. */
    var znalezione by mutableStateOf(0 to 0)
        private set
    /** Adres karty przywróconej po ponownym uruchomieniu, ładowany dopiero po wejściu na kartę. */
    var odlozona by mutableStateOf<String?>(null)
        private set

    /** Adres, który właśnie się ładuje w głównej ramce (do rozpoznania błędów certyfikatu). */
    private var docelowy: String? = null
    /** Strona przewinięta wewnątrz (np. lista w poczcie): wtedy przeciągnięcie w dół nie odświeża. */
    private var wewnetrznePrzewiniecie = false
    private var skryptNaStarcie = false
    /** Plik z adresu blob:, na który czekamy (nazwa i chwila kliknięcia). */
    private var oczekiwanyPlik: Pair<String, Long>? = null

    private var widok: WebView? = null
    private var _kontener: SwipeRefreshLayout? = null

    val webViewJesli: WebView? get() = widok

    val webView: WebView
        get() = widok ?: utworz().also { w ->
            widok = w
            _kontener?.addView(w, pelnyRozmiar())
        }

    /** WebView w ramce „przeciągnij w dół, żeby odświeżyć”. */
    val kontener: SwipeRefreshLayout
        get() = _kontener ?: SwipeRefreshLayout(p.kontekst).also { k ->
            k.addView(webView, pelnyRozmiar())
            k.setOnRefreshListener { przeladuj() }
            k.setOnChildScrollUpCallback { _, _ ->
                val w = widok
                w == null || w.scrollY > 0 || wewnetrznePrzewiniecie
            }
            _kontener = k
        }

    /** Przy błędzie adres, który się nie otworzył; inaczej bieżąca strona. */
    val wyswietlanyAdres: String? get() = blad?.adres ?: adres ?: odlozona

    /** Kłódka: bez błędu i nie przez zwykłe http (mieszanych treści WebView i tak nie wczytuje). */
    val bezpieczna: Boolean
        get() = blad == null && wyswietlanyAdres?.startsWith("http://", ignoreCase = true) != true

    /**
     * Wstecz przy błędzie: gdy nieudany adres nie wszedł do historii (np. zły certyfikat),
     * wystarczy schować komunikat, bo pod spodem jest poprzednia strona.
     */
    fun schowajBlad(): Boolean {
        val b = blad ?: return false
        if (b.adres == null || widok?.url == b.adres) return false
        blad = null
        return true
    }

    fun laduj(url: String) {
        odlozona = null
        blad = null
        docelowy = url
        webView.loadUrl(url)
    }

    fun odloz(url: String, tytul: String) {
        odlozona = url
        this.tytul = tytul
    }

    fun zaladujOdlozona() {
        odlozona?.let { laduj(it) }
    }

    fun sprobujPonownie() {
        val url = blad?.adres ?: adres
        if (url != null) {
            laduj(url)
        } else {
            blad = null
            webView.reload()
        }
    }

    fun przeladuj() {
        if (blad != null) sprobujPonownie() else webView.reload()
    }

    fun zatrzymaj() {
        widok?.stopLoading()
        laduje = false
        _kontener?.isRefreshing = false
    }

    fun szukaj(tekst: String) {
        if (tekst.isEmpty()) {
            widok?.clearMatches()
            znalezione = 0 to 0
        } else {
            widok?.findAllAsync(tekst)
        }
    }

    fun nastepny(wprzod: Boolean) {
        widok?.findNext(wprzod)
    }

    fun koniecSzukania() {
        widok?.clearMatches()
        znalezione = 0 to 0
    }

    fun wstrzymaj() {
        widok?.onPause()
    }

    fun wznow() {
        widok?.onResume()
    }

    /** Po zmianie dnia i nocy WebView na nowo czyta motyw (prefers-color-scheme), gdy zmieni się jego ustawienie. */
    fun odswiezMotyw() {
        val ustawienia = widok?.settings ?: return
        val zoom = ustawienia.textZoom
        ustawienia.textZoom = zoom + 1
        ustawienia.textZoom = zoom
    }

    fun zakoncz() {
        tlumaczenie.zakoncz()
        _kontener?.let { (it.parent as? ViewGroup)?.removeView(it) }
        widok?.let { w ->
            w.stopLoading()
            (w.parent as? ViewGroup)?.removeView(w)
            w.destroy()
        }
        widok = null
        _kontener = null
    }

    // MARK: - Tworzenie WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun utworz(): WebView {
        val w = WebView(p.kontekst)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            // jak Chrome na tym telefonie, żeby strony nie dawały okrojonej wersji dla aplikacji;
            // „LivePL/…” na końcu: nasze strony wiedzą, że są w aplikacji LivePL (np. poczta nie proponuje dodania do ekranu)
            userAgentString = userAgentString
                .replace("; wv)", ")")
                .replace(Regex("""\sVersion/[\d.]+"""), "") + " LivePL/${BuildConfig.VERSION_NAME}"
        }
        // strony dostają prefers-color-scheme z motywu LivePL; bez sztucznego przyciemniania, jak na Windows
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(w.settings, false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = Klient()
        w.webChromeClient = Chrome()
        w.setDownloadListener { url, agent, opis, typ, _ -> pobierz(url, agent, opis, typ) }
        w.setFindListener { ktory, ile, _ -> znalezione = (if (ile == 0) 0 else ktory + 1) to ile }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(w, MOST, setOf("*")) { _, wiadomosc, _, glowna, _ ->
                val dane = wiadomosc.data ?: return@addWebMessageListener
                when {
                    dane == "1" -> wewnetrznePrzewiniecie = true
                    dane == "0" -> wewnetrznePrzewiniecie = false
                    glowna && dane == "tlumacz" -> tlumaczenie.nowaTresc()
                    glowna && dane.startsWith("{") -> odbierzPlik(dane)
                }
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(w, SKRYPT, setOf("*"))
                skryptNaStarcie = true
            }
        }
        return w
    }

    private fun pelnyRozmiar() =
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    /** Proces stron padł (np. brak pamięci): nowy WebView, ta sama strona. */
    private fun odtworz() {
        val url = adres ?: docelowy
        widok?.let { w ->
            (w.parent as? ViewGroup)?.removeView(w)
            w.destroy()
        }
        widok = null
        laduje = false
        _kontener?.isRefreshing = false
        if (url == null) return
        if (p.aktywna === this) laduj(url) else odloz(url, tytul)
    }

    private fun historia(w: WebView) {
        mozeWstecz = w.canGoBack()
        mozeDalej = w.canGoForward()
    }

    private fun nadawca(url: String?): String = Adres.host(url).ifEmpty { Adres.host(adres) }

    private fun pokazBlad(kod: Int, opis: String?, url: String?) {
        // przerwane celowo: kolejne kliknięcie, pobieranie pliku, link do innej aplikacji
        if (opis?.contains("ERR_ABORTED") == true) return
        val tekst = when {
            !p.jestInternet() -> "Brak połączenia z internetem. Sprawdź Wi-Fi albo dane komórkowe."
            kod == WebViewClient.ERROR_HOST_LOOKUP -> "Nie ma takiej strony. Sprawdź, czy adres jest dobrze wpisany."
            kod == WebViewClient.ERROR_TIMEOUT -> "Strona nie odpowiada. Spróbuj za chwilę."
            kod == WebViewClient.ERROR_CONNECT -> "Serwer strony nie przyjmuje połączeń."
            kod == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ->
                "Połączenie nie jest bezpieczne: certyfikat strony się nie zgadza. LivePL jej nie otworzy."
            kod == WebViewClient.ERROR_REDIRECT_LOOP -> "Strona przekierowuje w kółko i nie da się jej otworzyć."
            kod == WebViewClient.ERROR_BAD_URL || kod == WebViewClient.ERROR_UNSUPPORTED_SCHEME ->
                "Tego adresu nie da się otworzyć."
            kod == WebViewClient.ERROR_TOO_MANY_REQUESTS -> "Strona dostała za dużo zapytań. Spróbuj za chwilę."
            else -> "Coś poszło nie tak przy otwieraniu strony." + (opis?.let { " ($it)" } ?: "")
        }
        blad = BladStrony(tekst, url ?: adres)
        laduje = false
        _kontener?.isRefreshing = false
    }

    // MARK: - Pobieranie

    private fun pobierz(url: String, agent: String?, opis: String?, typ: String?) {
        if (!url.startsWith("blob:")) {
            Pobieranie.pobierz(p, url, agent, opis, typ)
            return
        }
        // blob: istnieje tylko w stronie, więc strona sama oddaje plik przez most LivePL
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            p.komunikat("Tego pliku nie da się pobrać w LivePL.")
            return
        }
        val nazwa = URLUtil.guessFileName(url, opis, typ)
        oczekiwanyPlik = nazwa to SystemClock.elapsedRealtime()
        val skrypt = """
            (function () {
              var adres = ${JSONObject.quote(url)};
              var p = window.__liveplPlik, a = document.querySelector('a[download][href="' + adres + '"]');
              var nazwa = (a && a.getAttribute('download')) || (p && p.adres === adres ? p.nazwa : '');
              fetch(adres).then(function (r) { return r.blob(); }).then(function (b) {
                var f = new FileReader();
                f.onload = function () { $MOST.postMessage(JSON.stringify({ plik: 1, nazwa: nazwa, typ: b.type, dane: f.result })); };
                f.readAsDataURL(b);
              }).catch(function (e) { $MOST.postMessage(JSON.stringify({ blad: String(e) })); });
            })();
        """.trimIndent()
        widok?.evaluateJavascript(skrypt, null)
    }

    private fun odbierzPlik(json: String) {
        // przyjmujemy tylko plik, o który LivePL sam poprosił chwilę wcześniej
        val (nazwa, kiedy) = oczekiwanyPlik ?: return
        oczekiwanyPlik = null
        if (SystemClock.elapsedRealtime() - kiedy > 120_000) return
        val o = try {
            JSONObject(json)
        } catch (e: Exception) {
            return
        }
        val dane = o.tekst("dane")
        if (dane == null || !dane.startsWith("data:")) {
            p.komunikat("Nie udało się pobrać pliku.")
            return
        }
        // nazwa z <a download>, a gdy jej nie ma, data zamiast losowego identyfikatora z adresu blob:
        val zeStrony = o.tekst("nazwa")?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
        val losowa = Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}(\.\w+)?$""").matches(nazwa)
        val nazwaPliku = zeStrony
            ?: if (losowa) "plik-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + nazwa.substringAfter('.', "").let { if (it.isEmpty()) "" else ".$it" } else nazwa
        Pobieranie.zapiszDane(p, dane, nazwaPliku, o.tekst("typ"))
    }

    // MARK: - WebViewClient

    private inner class Klient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val uri = request.url
            if (Przegladarka.obcyAdres(uri)) {
                p.otworzWSystemie(uri, view)
                // okno otwarte przez stronę tylko po to, żeby uruchomić inną aplikację
                if (otwierajaca != null && view.copyBackForwardList().size == 0) p.zamknij(this@Karta)
                return true
            }
            // Poczta LivePL: kliknięty link do innej strony otwiera się w przeglądarce, a nie w aplikacji poczty
            if (request.hasGesture() && !p.dozwolony(uri)) {
                p.otworzWSystemie(uri, view)
                if (otwierajaca != null && view.copyBackForwardList().size == 0) p.zamknij(this@Karta)
                return true
            }
            docelowy = uri.toString()
            return false
        }

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
            // certyfikat „Urządzenie LivePL” tylko dla naszych domen (jak w LivePL na Windows)
            val tozsamosc = if (NaszeCA.naszaDomena(p.kontekst, request.host)) Urzadzenie.dla(p.kontekst).tozsamosc() else null
            if (tozsamosc != null) request.proceed(tozsamosc.first, tozsamosc.second) else request.cancel()
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            tlumaczenie.nowaStrona()
            laduje = true
            if (blad?.adres != url) blad = null
            wewnetrznePrzewiniecie = false
            if (url != null && url != "about:blank") adres = url
            historia(view)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            laduje = false
            _kontener?.isRefreshing = false
            historia(view)
            if (blad == null) {
                view.title?.takeIf { it.isNotBlank() && it != url }?.let { tytul = it }
                tlumaczenie.stronaGotowa()
            }
            if (!skryptNaStarcie && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                view.evaluateJavascript(SKRYPT, null)
            }
            p.zapiszSesje()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (url != null && url != "about:blank") adres = url
            historia(view)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                pokazBlad(error.errorCode, error.description?.toString(), request.url.toString())
            }
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // złego certyfikatu nigdy nie przepuszczamy
            handler.cancel()
            if (error.url == docelowy || error.url == view.url) {
                pokazBlad(ERROR_FAILED_SSL_HANDSHAKE, null, error.url)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            odtworz()
            return true
        }
    }

    // MARK: - WebChromeClient

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            postep = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (blad == null && !title.isNullOrBlank()) tytul = title
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            // Poczta LivePL: link do innej strony z target=_blank też idzie do przeglądarki
            val wynik = view.hitTestResult
            val link = wynik.extra?.takeIf {
                wynik.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || wynik.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            }
            if (link != null && !p.dozwolony(Uri.parse(link))) {
                p.otworzWSystemie(Uri.parse(link), view)
                return false
            }
            val nowa = p.dodajKarte(this@Karta)
            (resultMsg.obj as WebView.WebViewTransport).webView = nowa.webView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            p.zamknij(this@Karta)
        }

        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            p.pokazOkno(Okno.Komunikat(this@Karta, nadawca(url), message.orEmpty(), result))
            return true
        }

        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            p.pokazOkno(Okno.Potwierdzenie(this@Karta, nadawca(url), message.orEmpty(), result))
            return true
        }

        override fun onJsPrompt(
            view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult,
        ): Boolean {
            p.pokazOkno(Okno.Pytanie(this@Karta, nadawca(url), message.orEmpty(), defaultValue, result))
            return true
        }

        override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            p.pokazOkno(
                Okno.Potwierdzenie(
                    this@Karta, nadawca(url),
                    "Opuścić tę stronę? Zmiany, których nie zapisano, mogą przepaść.", result, tak = "Opuść",
                ),
            )
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            p.zapytajOUprawnienia(this@Karta, request)
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            p.anulujPytanie(request)
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            p.zapytajOLokalizacje(this@Karta, origin, callback)
        }

        override fun onGeolocationPermissionsHidePrompt() {
            p.anulujPytanieOLokalizacje(this@Karta)
        }

        override fun onShowFileChooser(
            webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams,
        ): Boolean = p.wybierzPliki(filePathCallback, fileChooserParams)

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            p.pelnyEkran = PelnyEkran(view, callback)
        }

        override fun onHideCustomView() {
            p.pelnyEkran = null
        }
    }

    companion object {
        private var licznik = 0L
        private const val MOST = "LivePLMost"

        /** Mówi LivePL, czy dotknięty fragment strony jest przewinięty w dół (lista w poczcie, czat…). */
        private val SKRYPT = """
            (function () {
              if (window.top !== window || window.__livepl) return;
              window.__livepl = true;
              function przewiniety(el) {
                for (; el && el.nodeType === 1 && el !== document.body && el !== document.documentElement; el = el.parentElement) {
                  if (el.scrollTop > 0) {
                    var o = getComputedStyle(el).overflowY;
                    if (o === 'auto' || o === 'scroll' || o === 'overlay') return true;
                  }
                }
                return false;
              }
              document.addEventListener('touchstart', function (e) {
                try { $MOST.postMessage(przewiniety(e.target) ? '1' : '0'); } catch (_) {}
              }, { capture: true, passive: true });
              // nazwa pliku z <a download="…"> do pobierania z adresów blob:
              document.addEventListener('click', function (e) {
                var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
                if (a) window.__liveplPlik = { adres: a.href, nazwa: a.getAttribute('download') || '' };
              }, true);
            })();
        """.trimIndent()
    }
}
