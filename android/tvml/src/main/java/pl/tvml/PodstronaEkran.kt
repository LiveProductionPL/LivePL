package pl.tvml

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Podstrona redakcji (O nas, Kontakt, Regulamin…) w aplikacji, a nie pełna strona portalu z nagłówkiem i banerem cookies.
 * Najpierw wersja strony dla aplikacji (appUrl, ?widok=app: sama treść, bez skryptów i statystyk) w trybie aplikacji
 * (&motyw=jasny|ciemny); bez appUrl albo gdy się nie wczyta, treść z /api/v1/pages/<slug> z własnym CSS w kolorach
 * aplikacji. WebView bez JavaScriptu, tekst rośnie z rozmiarem czcionki w telefonie i rozmiarem tekstu w aplikacji.
 * Linki do podstron i artykułów tvml.pl otwierają się w aplikacji, pozostałe w systemie.
 */
@Composable
fun PodstronaEkran(m: Magazyn, slug: String) {
    val b = LocalBarwy.current
    val ciemny = LocalCiemny.current
    val kontekst = LocalContext.current
    var tresc by remember(slug) { mutableStateOf<TrescPodstrony?>(null) }
    var pobrana by remember(slug) { mutableStateOf(false) }
    var bezAdresuApp by remember(slug) { mutableStateOf(false) }
    LaunchedEffect(slug) {
        tresc = m.podstrona(slug)
        pobrana = true
    }
    val wpis = m.strony.firstOrNull { it.slug == slug }
    val adresApp = (wpis?.adresApp?.ifEmpty { null } ?: tresc?.adresApp?.ifEmpty { null })?.takeIf { !bezAdresuApp }
    val obsluz: (Uri) -> Unit = { u -> obsluzLink(m, kontekst, slug, u) }
    // tło jak w stronie dla aplikacji (w dzień białe), żeby pasek „Wróć” się od niej nie odcinał
    Column(Modifier.fillMaxSize().background(if (ciemny) b.tlo else b.karta)) {
        Box(Modifier.padding(horizontal = 16.dp)) { Wroc() }
        val t = tresc
        when {
            adresApp != null -> HtmlWidok(zMotywem(adresApp, ciemny), null, obsluz, { bezAdresuApp = true }, Modifier.fillMaxWidth().weight(1f))
            !pobrana -> Postep()
            t != null -> {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Naglowek(t.tytul.ifEmpty { wpis?.tytul.orEmpty() }, wielkosc = 26)
                    t.aktualizacja?.let { Text(tl("app.updated", "Aktualizacja") + ": " + Daty.krotka(it), color = b.slaby, fontSize = 12.sp) }
                }
                if (t.html.isBlank()) {
                    Text(tl("app.pageInProgress", "Treść tej strony jest w przygotowaniu."), Modifier.padding(16.dp), color = b.slaby, fontSize = 15.sp)
                } else {
                    HtmlWidok(null, t.html, obsluz, {}, Modifier.fillMaxWidth().weight(1f))
                }
            }
            else -> Text(tl("app.error", "Nie udało się wczytać strony. Sprawdź internet i spróbuj ponownie."), Modifier.padding(16.dp), color = b.slaby, fontSize = 15.sp)
        }
    }
}

private fun zMotywem(adres: String, ciemny: Boolean): String =
    adres + (if ('?' in adres) "&" else "?") + "motyw=" + if (ciemny) "ciemny" else "jasny"

/** Link z treści podstrony: inne podstrony i artykuły w aplikacji, Pogoda i strona główna w zakładkach, reszta w systemie. */
private fun obsluzLink(m: Magazyn, kontekst: Context, slug: String, u: Uri) {
    val strona = Linki.podstrona(u)
    val artykul = Linki.artykul(u)
    when {
        strona != null -> if (strona != slug) m.otworz(Ekran.Strona(strona))
        artykul != null -> m.otworzArtykul(artykul) { otworzWSystemie(kontekst, u) }
        Linki.pogoda(u) -> m.pokaz(ZakladkaApp.Cel.Pogoda)
        Linki.glowna(u) -> m.naStart()
        else -> otworzWSystemie(kontekst, u)
    }
}

/**
 * Treść HTML w WebView: [adres] (strona dla aplikacji z tvml.pl, z JavaScriptem, bo np. „Salon wróżb” na podstronie
 * Andrzejek działa na skrypcie strony) albo [html] z API (bez JavaScriptu). Skoki w obrębie tej samej strony (#wosk)
 * zostają w widoku (Karol 28.11: „klikam na linki od wrozba na aplikacji ale nie nie dziala”); każdy inny link trafia
 * do [link], a nie do tego samego widoku; błąd wczytania strony do [blad].
 */
@Composable
private fun HtmlWidok(adres: String?, html: String?, link: (Uri) -> Unit, blad: () -> Unit, modifier: Modifier) {
    val m = LocalMagazyn.current
    val b = LocalBarwy.current
    val powiekszenie = (LocalDensity.current.fontScale * 100 * listOf(0.9f, 1f, 1.15f)[m.rozmiar.coerceIn(0, 2)]).roundToInt()
    val aktualnyLink by rememberUpdatedState(link)
    val aktualnyBlad by rememberUpdatedState(blad)
    val strona = remember(html, b) { html?.let { stronaHtml(it, b) } }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // skrypty tylko na stronie dla aplikacji z tvml.pl (adres z config.pages), treść z API bez nich
                settings.javaScriptEnabled = adres?.let { Uri.parse(it) }?.let { it.scheme == "https" && it.host?.endsWith("tvml.pl") == true } == true
                settings.domStorageEnabled = settings.javaScriptEnabled
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.userAgentString = settings.userAgentString + " " + Siec.agent
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        if (!r.isForMainFrame || r.isRedirect) return false
                        // skok do miejsca na tej samej stronie (spis treści #wosk, #buty…): przewija widok
                        val obecny = v.url?.let { Uri.parse(it) }
                        if (r.url.fragment != null && obecny != null && r.url.host == obecny.host && r.url.path.orEmpty().trimEnd('/') == obecny.path.orEmpty().trimEnd('/')) return false
                        aktualnyLink(r.url)
                        return true
                    }

                    override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                        if (r.isForMainFrame) aktualnyBlad()
                    }

                    override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, odp: WebResourceResponse) {
                        if (r.isForMainFrame && odp.statusCode >= 400) aktualnyBlad()
                    }
                }
            }
        },
        update = { w ->
            w.settings.textZoom = powiekszenie
            val klucz = adres ?: strona.hashCode().toString()
            if (w.tag != klucz) {
                w.tag = klucz
                if (adres != null) w.loadUrl(adres) else w.loadDataWithBaseURL(Siec.BAZA, strona.orEmpty(), "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier,
    )
}

private fun hex(c: Color): String = String.format(Locale.ROOT, "#%06x", c.toArgb() and 0xffffff)

private val filmWTresci = Regex("""<figure[^>]*data-youtube="([A-Za-z0-9_-]{6,})"[^>]*>.*?</figure>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/** Strona z treścią z panelu: kolory z motywu aplikacji (nie z HTML), obrazy w szerokości ekranu, tabele przewijane w poziomie. */
private fun stronaHtml(tresc: String, b: Barwy): String {
    // film z panelu (figure data-youtube) bez JavaScriptu: miniatura z linkiem do YouTube
    val html = filmWTresci.replace(tresc) { w ->
        val id = w.groupValues[1]
        """<p><a href="https://www.youtube.com/watch?v=$id"><img src="https://i.ytimg.com/vi/$id/hqdefault.jpg" alt="Film w YouTube"></a></p>"""
    }
    return """<!doctype html><html lang="pl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html { -webkit-text-size-adjust: 100%; }
body { margin: 0; padding: 4px 16px 32px; font-family: sans-serif; font-size: 16px; line-height: 1.5; color: ${hex(b.tekst)}; background: transparent; overflow-wrap: anywhere; }
h2, h3, h4 { line-height: 1.25; margin: 1.2em 0 .5em; }
p { margin: 0 0 .8em; }
a { color: ${hex(b.link)}; }
img { max-width: 100%; height: auto; border-radius: 8px; }
figure { margin: 1em 0; }
table { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; }
td, th { border: 1px solid ${hex(b.ramka)}; padding: 6px 8px; text-align: left; vertical-align: top; }
blockquote { margin: 1em 0; padding-left: 12px; border-left: 3px solid ${hex(b.ramka)}; color: ${hex(b.slaby)}; }
hr { border: 0; border-top: 1px solid ${hex(b.ramka)}; margin: 1.2em 0; }
ul, ol { padding-left: 1.3em; }
</style></head><body>$html</body></html>"""
}

/** Linki tvml.pl w treści podstron, także w postaci z panelu (strona.html?slug=…, artykul.html?id=…, index.html). */
object Linki {
    private fun tvml(u: Uri): Boolean {
        val h = u.host?.lowercase() ?: return true
        return h == "tvml.pl" || h == "www.tvml.pl"
    }

    private fun www(u: Uri): Boolean = u.scheme == null || u.scheme == "http" || u.scheme == "https"

    private fun naszWww(u: Uri) = www(u) && tvml(u)

    fun podstrona(u: Uri): String? {
        if (!naszWww(u)) return null
        val sciezka = u.path.orEmpty()
        if (sciezka.startsWith("/strona/")) return sciezka.removePrefix("/strona/").substringBefore('/').ifEmpty { null }
        if (sciezka.endsWith("strona.html")) return u.getQueryParameter("slug")?.ifEmpty { null }
        return null
    }

    fun artykul(u: Uri): Int? {
        if (!naszWww(u)) return null
        val czesci = u.pathSegments
        val i = czesci.indexOf("artykul")
        if (i >= 0 && i + 1 < czesci.size) czesci[i + 1].takeWhile { it.isDigit() }.toIntOrNull()?.let { return it }
        if (u.path.orEmpty().endsWith("artykul.html")) return u.getQueryParameter("id")?.toIntOrNull()
        return null
    }

    fun pogoda(u: Uri): Boolean = naszWww(u) && u.path.orEmpty().let { it == "/pogoda" || it.endsWith("pogoda.html") }

    fun glowna(u: Uri): Boolean = naszWww(u) && u.path.orEmpty() in setOf("", "/", "/index.html")
}

/** Ogłoszenia drobne z panelu (/api/v1/classifieds) w aplikacji. */
@Composable
fun OgloszeniaEkran(m: Magazyn) {
    val b = LocalBarwy.current
    val kontekst = LocalContext.current
    var lista by remember { mutableStateOf<List<Ogloszenie>?>(null) }
    var wybrane by remember { mutableStateOf<Ogloszenie?>(null) }
    LaunchedEffect(Unit) { lista = m.wszystkieOgloszenia().orEmpty() }
    LazyColumn(Modifier.fillMaxSize().background(b.tlo), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "wroc") { Wroc() }
        item(key = "tytul") { Naglowek(tl("app.classifieds", "Ogłoszenia drobne"), wielkosc = 26) }
        val l = lista
        when {
            l == null -> item(key = "laduje") { Postep() }
            l.isEmpty() -> item(key = "pusta") { Text(tl("app.noClassifieds", "Na razie nie ma ogłoszeń."), Modifier.padding(vertical = 12.dp), color = b.slaby, fontSize = 15.sp) }
            else -> items(l, key = { it.id }) { o -> WierszOgloszenia(o) { wybrane = o } }
        }
        item(key = "dodaj") {
            Text(
                tl("app.addClassified", "Dodaj ogłoszenie na tvml.pl"),
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { otworzStrone(kontekst, Siec.BAZA + "lista/ogloszenia") }.padding(4.dp),
                color = b.link, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
    wybrane?.let { OgloszenieArkusz(it) { wybrane = null } }
}

@Composable
private fun WierszOgloszenia(o: Ogloszenie, akcja: () -> Unit) {
    val b = LocalBarwy.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.karta).border(1.dp, b.ramka, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = akcja).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        o.zdjecia.firstOrNull()?.let { Obraz(it, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(o.tytul, color = b.tekst, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            val opis = listOf(o.kategoria, o.cena).filter { it.isNotEmpty() }.joinToString(" · ")
            if (opis.isNotEmpty()) Text(opis, color = b.slaby, fontSize = 13.sp)
        }
        Icon(Ikony.dalej, null, tint = b.slaby, modifier = Modifier.size(16.dp))
    }
}

/** Szczegóły ogłoszenia: zdjęcia, cena, treść i kontakt (do zaznaczenia i skopiowania). */
@Composable
private fun OgloszenieArkusz(o: Ogloszenie, zamknij: () -> Unit) {
    val b = LocalBarwy.current
    Arkusz(tl("app.classified", "Ogłoszenie"), zamknij) {
        if (o.zdjecia.isNotEmpty()) {
            val strony = rememberPagerState { o.zdjecia.size }
            HorizontalPager(strony, pageSpacing = 8.dp, modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(14.dp))) { i ->
                Obraz(o.zdjecia[i], Modifier.fillMaxSize())
            }
            // jak kropki strony pod zdjęciami na iOS (Karol 25.11: przy każdej karuzeli wskaźnik)
            if (o.zdjecia.size > 1) KropkiKaruzeli(o.zdjecia.size, strony.currentPage, Modifier.padding(top = 8.dp))
            Odstep(8.dp)
        }
        if (o.kategoria.isNotEmpty()) Text(o.kategoria.uppercase(), color = b.slaby, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(o.tytul, color = b.tekst, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black, fontFamily = KrojNaglowkow)
        if (o.cena.isNotEmpty()) Text(o.cena, color = b.tekst, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (o.tekst.isNotEmpty()) Text(o.tekst, Modifier.padding(top = 8.dp), color = b.tekst, fontSize = 16.sp, lineHeight = 24.sp)
        if (o.kontakt.isNotEmpty()) {
            Column(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(b.karta).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(tl("app.classifiedContact", "Kontakt"), color = b.slaby, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                SelectionContainer { Text(o.kontakt, color = b.tekst, fontSize = 16.sp) }
            }
        }
    }
}
