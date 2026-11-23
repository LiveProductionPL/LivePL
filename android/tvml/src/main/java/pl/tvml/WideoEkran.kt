package pl.tvml

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.core.net.toUri
import coil3.compose.AsyncImage
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
                    Text(tl("app.video", "Wideo"), Modifier.weight(1f), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = Szeryf)
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

/**
 * Film z YouTube bez ciasteczek śledzących (youtube-nocookie), na czarnym tle, z pełnym ekranem.
 * YouTube od 2025 r. wymaga strony osadzającej (inaczej „Błąd 153”), więc odtwarzacz stoi na stronie o adresie
 * https://tvml.pl, a iframe ma parametr origin i referrerpolicy. Zapasowo „Otwórz w YouTube”.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Odtwarzacz(id: String, zamknij: () -> Unit) {
    val kontekst = LocalContext.current
    Dialog(onDismissRequest = zamknij, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tl("app.openYoutube", "Otwórz w YouTube"),
                    Modifier.clip(CircleShape).clickable(role = Role.Button) {
                        runCatching { kontekst.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$id".toUri())) }
                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                    color = szaryWideo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                Box(Modifier.weight(1f))
                Text(
                    tl("app.done", "Gotowe"),
                    Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = zamknij).padding(horizontal = 12.dp, vertical = 10.dp),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
            AndroidView(
                factory = { ctx ->
                    val pojemnik = FrameLayout(ctx)
                    val web = WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = settings.userAgentString + " " + Siec.agent
                        webViewClient = object : WebViewClient() {
                            // linki z odtwarzacza (logo YouTube, „Obejrzyj na YouTube”) w aplikacji YouTube albo przeglądarce
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                if (!r.isForMainFrame) return false
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, r.url)) }
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            private var pelny: View? = null
                            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                pelny = view
                                pojemnik.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            }
                            override fun onHideCustomView() {
                                pelny?.let { pojemnik.removeView(it) }
                                pelny = null
                            }
                        }
                        val strona = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                            |<style>html,body{margin:0;height:100%;background:#000}iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style></head>
                            |<body><iframe src="https://www.youtube-nocookie.com/embed/$id?autoplay=1&playsinline=1&rel=0&origin=https%3A%2F%2Ftvml.pl" referrerpolicy="strict-origin-when-cross-origin"
                            |allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe></body></html>""".trimMargin()
                        loadDataWithBaseURL(Siec.BAZA, strona, "text/html", "utf-8", null)
                    }
                    pojemnik.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    pojemnik.tag = web
                    pojemnik
                },
                onRelease = { (it.tag as? WebView)?.destroy() },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}
