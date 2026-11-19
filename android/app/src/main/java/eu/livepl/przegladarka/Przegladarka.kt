package eu.livepl.przegladarka

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URISyntaxException

/** Film albo inny element strony na pełnym ekranie. */
class PelnyEkran(val widok: View, val zwrot: WebChromeClient.CustomViewCallback)

/**
 * Stan całej przeglądarki: programy, otwarte karty, Moje strony i tryb dzień/noc.
 * Jeden na cały proces, więc karty (i ich WebView) przetrwają obrót ekranu i zmianę motywu.
 */
class Przegladarka private constructor(kontekstAplikacji: Context) {
    private val aplikacja: Context = kontekstAplikacji.applicationContext

    /** Kontekst wszystkich WebView. Wskazuje bieżącą aktywność, żeby strony brały z niej motyw i okna. */
    val kontekst = MutableContextWrapper(aplikacja)

    private val ustawienia: SharedPreferences =
        aplikacja.getSharedPreferences(Motyw.USTAWIENIA, Context.MODE_PRIVATE)

    val programy: List<Program> = Program.wczytaj(aplikacja)
    val karty = mutableStateListOf<Karta>()
    /** null = Reżyserka (ekran startowy) */
    var aktywnaId by mutableStateOf<Long?>(null)
    val mojeStrony = mutableStateListOf<MojaStrona>()
    var motyw by mutableStateOf(Motyw.zapisany(ustawienia))
        private set
    var pokazListeKart by mutableStateOf(false)
    var dodawanaStrona by mutableStateOf<MojaStrona?>(null)
    /** „Nowa karta” prosi Reżyserkę o kursor w polu adresu. */
    var prosbaOAdres by mutableIntStateOf(0)
        private set
    var obsluzonaProsbaOAdres = 0
    /** Okienka stron (alert, confirm, prompt, zgody) czekające w kolejce. */
    val okna = mutableStateListOf<Okno>()
    var pelnyEkran by mutableStateOf<PelnyEkran?>(null)

    var aktywnosc: MainActivity? = null
        private set
    private var uruchomiona = false
    /** Zgody na aparat, mikrofon i lokalizację, dane w tym uruchomieniu (host|zasoby). */
    private val zgody = mutableSetOf<String>()
    /** Serwisy, w których użytkownik zamknął propozycję tłumaczenia (do końca uruchomienia). */
    val bezPropozycjiTlumaczenia = mutableSetOf<String>()

    // sprawy, które czekają na wynik innej aplikacji; przetrwają odtworzenie aktywności
    var plikiZwrot: ValueCallback<Array<Uri>>? = null
    var zdjecie: Pair<Uri, File>? = null
    var poUprawnieniach: ((Map<String, Boolean>) -> Unit)? = null
    var proszoneUprawnienia: Array<String> = emptyArray()

    init {
        wczytajStrony()
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
    }

    // MARK: - Programy i karty

    val aktywna: Karta? get() = aktywnaId?.let { id -> karty.firstOrNull { it.id == id } }

    val widoczneProgramy: List<Program> get() = programy.filter { !it.ukryty }
    val pozostaleProgramy: List<Program> get() = programy.filter { it.ukryty }

    /** Na dolnym pasku: widoczne programy zawsze, schowane tylko wtedy, gdy są otwarte. */
    val programyNaPasku: List<Program> get() = programy.filter { !it.ukryty || karta(it.id) != null }

    val kartyProgramow: List<Karta> get() = karty.filter { it.programId != null }
    val zwykleKarty: List<Karta> get() = karty.filter { it.programId == null }

    fun karta(programu: String): Karta? = karty.firstOrNull { it.programId == programu }

    fun program(id: String?): Program? = id?.let { i -> programy.firstOrNull { it.id == i } }

    fun aktywuj(karta: Karta) {
        aktywnaId = karta.id
        karta.zaladujOdlozona()
    }

    /** Każdy program ma jedną własną kartę, jak kanał na pasku LivePL. */
    fun otworz(program: Program) {
        karta(program.id)?.let {
            aktywuj(it)
            return
        }
        val nowa = Karta(program.id, this)
        karty += nowa
        aktywnaId = nowa.id
        nowa.laduj(program.adresNaTelefonie)
        zapiszSesje()
    }

    fun otworz(adres: String) {
        val uri = Uri.parse(adres)
        if (obcyAdres(uri)) {
            otworzWSystemie(uri)
            return
        }
        val nowa = Karta(null, this)
        karty += nowa
        aktywnaId = nowa.id
        nowa.laduj(adres)
        zapiszSesje()
    }

    /** Okno otwarte przez stronę (target=_blank, window.open). */
    fun dodajKarte(otwierajaca: Karta): Karta {
        val nowa = Karta(null, this)
        nowa.otwierajaca = otwierajaca
        val i = karty.indexOf(otwierajaca)
        if (i >= 0) karty.add(i + 1, nowa) else karty += nowa
        aktywnaId = nowa.id
        return nowa
    }

    fun zamknij(karta: Karta) {
        val i = karty.indexOf(karta)
        if (i < 0) return
        val byla = aktywnaId == karta.id
        val otwierajaca = karta.otwierajaca
        anulujOkna(karta)
        karta.zakoncz()
        karty.removeAt(i)
        karty.forEach { if (it.otwierajaca === karta) it.otwierajaca = null }
        if (byla) {
            // okno otwarte przez stronę wraca do strony, która je otworzyła
            aktywnaId = otwierajaca?.takeIf { it in karty }?.id
        }
        zapiszSesje()
    }

    fun zamknijWszystkie() {
        karty.toList().forEach { anulujOkna(it) }
        karty.forEach { it.zakoncz() }
        karty.clear()
        aktywnaId = null
        zapiszSesje()
    }

    fun pokazRezyserke() {
        aktywnaId = null
    }

    fun nowaKarta() {
        aktywnaId = null
        prosbaOAdres++
    }

    /** Systemowy przycisk Wstecz: strona wstecz, okno strony wraca do otwierającej, a potem Reżyserka. */
    fun wstecz() {
        val k = aktywna ?: return
        val w = k.webViewJesli
        when {
            k.schowajBlad() -> Unit
            w != null && w.canGoBack() -> w.goBack()
            k.otwierajaca?.let { it in karty } == true -> zamknij(k)
            else -> pokazRezyserke()
        }
    }

    // MARK: - Inne aplikacje

    /** mailto:, tel:, intent:, sklep… otwiera Android. */
    fun otworzWSystemie(uri: Uri, widok: WebView? = null): Boolean {
        val schemat = uri.scheme?.lowercase()
        val intencja = when (schemat) {
            "intent" -> try {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    // strona nie może wskazać konkretnego składnika ani dać dostępu do plików LivePL
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                    flags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()
                }
            } catch (e: URISyntaxException) {
                return false
            }
            "tel" -> Intent(Intent.ACTION_DIAL, uri)
            "mailto", "sms", "smsto" -> Intent(Intent.ACTION_SENDTO, uri)
            else -> Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        }
        if (intencja.`package` == aplikacja.packageName) return false
        val a = aktywnosc ?: return false
        try {
            a.startActivity(intencja)
            return true
        } catch (e: ActivityNotFoundException) {
            val zapas = intencja.getStringExtra("browser_fallback_url")
            if (zapas != null && (zapas.startsWith("https://") || zapas.startsWith("http://"))) {
                if (widok != null) widok.loadUrl(zapas) else otworz(zapas)
                return true
            }
            val pakiet = intencja.`package`
            if (pakiet != null) {
                try {
                    a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pakiet")))
                    return true
                } catch (_: ActivityNotFoundException) {
                }
            }
            komunikat("Na tym telefonie nie ma aplikacji, która to otworzy.")
            return false
        } catch (e: SecurityException) {
            komunikat("Android nie pozwolił otworzyć tego adresu.")
            return false
        }
    }

    // MARK: - Okienka i zgody stron

    fun pokazOkno(okno: Okno) {
        okna += okno
    }

    fun zamknijOkno(okno: Okno, akcja: () -> Unit) {
        if (okna.remove(okno)) akcja()
    }

    private fun anulujOkna(karta: Karta) {
        okna.filter { it.karta === karta }.forEach {
            okna.remove(it)
            it.anuluj()
        }
    }

    fun zapytajOUprawnienia(karta: Karta, prosba: PermissionRequest) {
        val zasoby = prosba.resources.toList()
        val media = zasoby.filter {
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }
        // treści chronione (np. filmy z DRM) nie wymagają pytania
        val chronione = zasoby.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
        if (media.isEmpty()) {
            if (chronione.isNotEmpty()) prosba.grant(chronione.toTypedArray()) else prosba.deny()
            return
        }
        val host = Adres.host(prosba.origin.toString())
        val klucz = host + "|" + media.sorted().joinToString(",")
        if (klucz in zgody) {
            przyznajZSystemem(prosba, media + chronione)
            return
        }
        val aparat = PermissionRequest.RESOURCE_VIDEO_CAPTURE in media
        val mikrofon = PermissionRequest.RESOURCE_AUDIO_CAPTURE in media
        val co = when {
            aparat && mikrofon -> "aparatu i mikrofonu"
            aparat -> "aparatu"
            else -> "mikrofonu"
        }
        pokazOkno(
            Okno.Zgoda(karta, "Dostęp do $co", "$host prosi o dostęp do $co. Zezwolić?", prosba) { zgoda ->
                if (zgoda) {
                    zgody += klucz
                    przyznajZSystemem(prosba, media + chronione)
                } else {
                    prosba.deny()
                }
            },
        )
    }

    private fun przyznajZSystemem(prosba: PermissionRequest, zasoby: List<String>) {
        val a = aktywnosc ?: return prosba.deny()
        fun uprawnienie(zasob: String) = when (zasob) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
            else -> null
        }
        val potrzebne = zasoby.mapNotNull { uprawnienie(it) }.distinct()
        a.poprosOUprawnienia(potrzebne.toTypedArray()) { wynik ->
            val dozwolone = zasoby.filter { z -> uprawnienie(z)?.let { wynik[it] == true } ?: true }
            if (dozwolone.any { uprawnienie(it) != null }) {
                prosba.grant(dozwolone.toTypedArray())
            } else {
                prosba.deny()
                komunikat("Bez zgody Androida strona nie dostanie aparatu ani mikrofonu.")
            }
        }
    }

    fun anulujPytanie(prosba: PermissionRequest) {
        okna.filter { it is Okno.Zgoda && it.dane === prosba }.forEach { okna.remove(it) }
    }

    fun zapytajOLokalizacje(karta: Karta, zrodlo: String, zwrot: GeolocationPermissions.Callback) {
        val host = Adres.host(zrodlo)
        val klucz = "$host|lokalizacja"
        val przyznaj = {
            val a = aktywnosc
            if (a == null) {
                zwrot.invoke(zrodlo, false, false)
            } else {
                a.poprosOUprawnienia(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                ) { wynik -> zwrot.invoke(zrodlo, wynik.values.any { it }, false) }
            }
        }
        if (klucz in zgody) {
            przyznaj()
            return
        }
        pokazOkno(
            Okno.Zgoda(karta, "Twoja lokalizacja", "$host prosi o Twoją lokalizację. Zezwolić?", zwrot) { zgoda ->
                if (zgoda) {
                    zgody += klucz
                    przyznaj()
                } else {
                    zwrot.invoke(zrodlo, false, false)
                }
            },
        )
    }

    fun anulujPytanieOLokalizacje(karta: Karta) {
        okna.filter { it is Okno.Zgoda && it.karta === karta && it.dane is GeolocationPermissions.Callback }
            .forEach { okna.remove(it) }
    }

    fun wybierzPliki(zwrot: ValueCallback<Array<Uri>>, opcje: WebChromeClient.FileChooserParams): Boolean {
        val a = aktywnosc ?: return false
        a.wybierzPliki(zwrot, opcje)
        return true
    }

    fun wyjdzZPelnegoEkranu() {
        val pe = pelnyEkran ?: return
        pelnyEkran = null
        pe.zwrot.onCustomViewHidden()
    }

    fun komunikat(tekst: String) {
        Toast.makeText(aplikacja, tekst, Toast.LENGTH_SHORT).show()
    }

    fun jestInternet(): Boolean {
        val cm = aplikacja.getSystemService(ConnectivityManager::class.java) ?: return true
        val siec = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(siec)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    // MARK: - Moje strony

    /** Zwraca opis błędu albo null, gdy strona została dodana. */
    fun dodajStrone(strona: MojaStrona): String? {
        val schemat = Uri.parse(strona.adres).scheme?.lowercase()
        if (schemat != "http" && schemat != "https") return "To nie wygląda na adres strony (np. livepl.eu)."
        if (mojeStrony.any { Adres.taSamaStrona(it.adres, strona.adres) }) return "Ta strona już jest na liście."
        mojeStrony += strona
        zapiszStrony()
        return null
    }

    fun usunStrone(strona: MojaStrona) {
        mojeStrony.removeAll { it.id == strona.id }
        zapiszStrony()
    }

    private fun wczytajStrony() {
        val tekst = ustawienia.getString(KLUCZ_STRONY, null) ?: return
        try {
            val lista = JSONArray(tekst)
            for (i in 0 until lista.length()) {
                val o = lista.getJSONObject(i)
                val adres = o.tekst("adres") ?: continue
                mojeStrony += MojaStrona(adres, o.tekst("nazwa") ?: "", o.tekst("id") ?: adres)
            }
        } catch (_: Exception) {
        }
    }

    private fun zapiszStrony() {
        val lista = JSONArray()
        mojeStrony.forEach {
            lista.put(JSONObject().put("id", it.id).put("adres", it.adres).put("nazwa", it.nazwa))
        }
        ustawienia.edit().putString(KLUCZ_STRONY, lista.toString()).apply()
    }

    // MARK: - Tryb dzień i noc

    /** Zapisuje tryb i odtwarza aktywność w nowym motywie; karty i ich strony zostają. */
    fun ustawMotyw(nowy: Motyw) {
        motyw = nowy
        Motyw.zapisz(ustawienia, nowy)
        aktywnosc?.recreate()
    }

    // MARK: - Aktywność

    fun dolacz(a: MainActivity) {
        aktywnosc = a
        kontekst.baseContext = a
        motyw = Motyw.zapisany(ustawienia)
        // strony dostają prefers-color-scheme z motywu nowej aktywności
        karty.forEach { it.odswiezMotyw() }
    }

    fun odlacz(a: MainActivity, koniec: Boolean) {
        if (aktywnosc !== a) return
        aktywnosc = null
        kontekst.baseContext = aplikacja
        if (koniec) {
            zapiszSesje()
            okna.toList().forEach { it.anuluj() }
            okna.clear()
            karty.forEach { it.zakoncz() }
            karty.clear()
            synchronized(Companion) { if (instancja === this) instancja = null }
        }
    }

    fun wstrzymaj() {
        karty.forEach { it.wstrzymaj() }
        CookieManager.getInstance().flush()
    }

    fun wznow() {
        karty.forEach { it.wznow() }
    }

    // MARK: - Start i sesja

    fun uruchom() {
        if (uruchomiona) return
        uruchomiona = true
        przywrocSesje()
    }

    /** Link z innej aplikacji albo (do zrzutów ekranu) --es otworz poczta / --es otworz https://… */
    fun obsluz(intencja: Intent?) {
        intencja ?: return
        intencja.getStringExtra("otworz")?.let { co ->
            val program = programy.firstOrNull { it.id == co }
            if (program != null) {
                otworz(program)
            } else if (co.startsWith("https://") || co.startsWith("http://")) {
                otworz(co)
            }
            return
        }
        if (intencja.action == Intent.ACTION_VIEW) {
            val adres = intencja.dataString ?: return
            if (adres.startsWith("https://", true) || adres.startsWith("http://", true)) otworz(adres)
        }
    }

    /** Otwarte karty wracają po ponownym uruchomieniu; strony ładują się dopiero po wejściu na kartę. */
    private fun przywrocSesje() {
        if (karty.isNotEmpty()) return
        val tekst = ustawienia.getString(KLUCZ_SESJA, null) ?: return
        try {
            val lista = JSONArray(tekst)
            for (i in 0 until minOf(lista.length(), 15)) {
                val o = lista.getJSONObject(i)
                val adres = o.tekst("adres") ?: continue
                val programId = o.tekst("program")
                if (programId != null && (program(programId) == null || karta(programId) != null)) continue
                val karta = Karta(programId, this)
                karta.odloz(adres, o.tekst("tytul") ?: "")
                karty += karta
            }
        } catch (_: Exception) {
        }
    }

    fun zapiszSesje() {
        val lista = JSONArray()
        karty.forEach { k ->
            val adres = k.wyswietlanyAdres ?: return@forEach
            if (!adres.startsWith("http://") && !adres.startsWith("https://")) return@forEach
            lista.put(JSONObject().put("program", k.programId ?: JSONObject.NULL).put("adres", adres).put("tytul", k.tytul))
        }
        ustawienia.edit().putString(KLUCZ_SESJA, lista.toString()).apply()
    }

    companion object {
        private const val KLUCZ_STRONY = "livepl-strony"
        private const val KLUCZ_SESJA = "livepl-sesja"

        @Volatile
        private var instancja: Przegladarka? = null

        fun dla(kontekst: Context): Przegladarka =
            instancja ?: synchronized(this) {
                instancja ?: Przegladarka(kontekst).also { instancja = it }
            }

        fun obcyAdres(uri: Uri): Boolean {
            val schemat = uri.scheme?.lowercase() ?: return false
            return schemat !in setOf("http", "https", "about", "data", "blob", "file", "javascript")
        }
    }
}
