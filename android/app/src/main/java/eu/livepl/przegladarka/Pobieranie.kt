package eu.livepl.przegladarka

import android.Manifest
import android.app.DownloadManager
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import java.io.File
import kotlin.concurrent.thread

/** Pobrane pliki trafiają do folderu Pobrane (Download), jak w LivePL na Windows. */
object Pobieranie {
    fun pobierz(p: Przegladarka, url: String, agent: String?, opis: String?, typ: String?) {
        when {
            url.startsWith("data:") -> zapiszDane(p, url, URLUtil.guessFileName(url, opis, typ), typ)
            url.startsWith("https://") || url.startsWith("http://") -> zZapisem(p) { wyslij(p, url, agent, opis, typ) }
            else -> p.komunikat("Tego pliku nie da się pobrać w LivePL.")
        }
    }

    /** Android 8 i 9 wymagają zgody na zapis w Pobranych; nowsze nie. */
    private fun zZapisem(p: Przegladarka, dalej: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            dalej()
            return
        }
        val a = p.aktywnosc ?: return
        a.poprosOUprawnienia(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { wynik ->
            if (wynik.values.all { it }) dalej() else p.komunikat("Bez zgody na zapis LivePL nie pobierze pliku.")
        }
    }

    private fun wyslij(p: Przegladarka, url: String, agent: String?, opis: String?, typ: String?) {
        val nazwa = URLUtil.guessFileName(url, opis, typ)
        try {
            val prosba = DownloadManager.Request(Uri.parse(url)).apply {
                typ?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
                // pliki za logowaniem (np. załączniki w poczcie) potrzebują ciasteczek strony
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                agent?.takeIf { it.isNotBlank() }?.let { addRequestHeader("User-Agent", it) }
                setTitle(nazwa)
                setDescription(Adres.host(url))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nazwa)
            }
            val menedzer = p.kontekst.applicationContext.getSystemService(DownloadManager::class.java)
            menedzer.enqueue(prosba)
            p.komunikat("Pobieram: $nazwa")
        } catch (e: Exception) {
            p.komunikat("Nie udało się pobrać pliku.")
        }
    }

    /** Plik z adresu data: (albo blob: przekazanego przez stronę) zapisany prosto w Pobranych. */
    fun zapiszDane(p: Przegladarka, daneUrl: String, nazwaPliku: String, typPliku: String?) {
        zZapisem(p) {
            val kontekst = p.kontekst.applicationContext
            val glowny = Handler(Looper.getMainLooper())
            thread(name = "LivePL-zapis") {
                val wynik = try {
                    val przecinek = daneUrl.indexOf(',')
                    require(daneUrl.startsWith("data:") && przecinek > 0)
                    val naglowek = daneUrl.substring(5, przecinek)
                    val tresc = daneUrl.substring(przecinek + 1)
                    val bajty = if (naglowek.endsWith(";base64")) {
                        Base64.decode(tresc, Base64.DEFAULT)
                    } else {
                        Uri.decode(tresc).toByteArray()
                    }
                    val typ = naglowek.substringBefore(';').ifBlank { typPliku?.ifBlank { null } ?: "application/octet-stream" }
                    val nazwa = zRozszerzeniem(nazwaPliku, typ)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val wartosci = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, nazwa)
                            put(MediaStore.Downloads.MIME_TYPE, typ)
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val resolver = kontekst.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, wartosci)
                            ?: error("brak miejsca w Pobranych")
                        resolver.openOutputStream(uri)?.use { it.write(bajty) } ?: error("nie da się zapisać")
                        wartosci.clear()
                        wartosci.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, wartosci, null, null)
                    } else {
                        @Suppress("DEPRECATION")
                        val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        folder.mkdirs()
                        wolnyPlik(folder, nazwa).writeBytes(bajty)
                    }
                    "Zapisano w Pobranych: $nazwa"
                } catch (e: Exception) {
                    "Nie udało się zapisać pliku."
                }
                glowny.post { p.komunikat(wynik) }
            }
        }
    }

    private fun zRozszerzeniem(nazwa: String, typ: String): String {
        val rozszerzenie = MimeTypeMap.getSingleton().getExtensionFromMimeType(typ) ?: return nazwa
        val bez = nazwa.removeSuffix(".bin")
        return if (bez.contains('.')) nazwa else "$bez.$rozszerzenie"
    }

    private fun wolnyPlik(folder: File, nazwa: String): File {
        var plik = File(folder, nazwa)
        var n = 1
        val baza = nazwa.substringBeforeLast('.')
        val rozszerzenie = nazwa.substringAfterLast('.', "")
        while (plik.exists()) {
            plik = File(folder, if (rozszerzenie.isEmpty()) "$baza ($n)" else "$baza ($n).$rozszerzenie")
            n++
        }
        return plik
    }
}
