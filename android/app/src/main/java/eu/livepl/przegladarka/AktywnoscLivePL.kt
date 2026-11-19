package eu.livepl.przegladarka

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

/**
 * Wspólna aktywność przeglądarki LivePL i Poczty LivePL: dzień albo noc według przełącznika LivePL,
 * uprawnienia Androida, wybór plików do formularzy i rejestracja urządzenia w urzędzie LivePL.
 */
abstract class AktywnoscLivePL : ComponentActivity() {
    protected lateinit var p: Przegladarka

    private val wyborPlikow = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { wynik ->
        val zwrot = p.plikiZwrot ?: return@registerForActivityResult
        p.plikiZwrot = null
        val zdjecie = p.zdjecie
        p.zdjecie = null
        if (wynik.resultCode != RESULT_OK) {
            zwrot.onReceiveValue(null)
            return@registerForActivityResult
        }
        val lista = mutableListOf<Uri>()
        wynik.data?.clipData?.let { c -> for (i in 0 until c.itemCount) c.getItemAt(i).uri?.let { lista += it } }
        if (lista.isEmpty()) wynik.data?.data?.let { lista += it }
        // zdjęcie z aparatu wraca bez danych, za to w przygotowanym pliku
        if (lista.isEmpty() && zdjecie != null && zdjecie.second.length() > 0) lista += zdjecie.first
        zwrot.onReceiveValue(if (lista.isEmpty()) null else lista.toTypedArray())
    }

    private val prosbaOUprawnienia = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { wynik ->
        val gotowe = p.poUprawnieniach ?: return@registerForActivityResult
        p.poUprawnieniach = null
        gotowe(p.proszoneUprawnienia.associateWith { wynik[it] ?: maUprawnienie(it) })
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // dzień albo noc z przełącznika LivePL; „auto” zostawia to, co ustawił telefon
        Motyw.zapisany(base).trybNocny?.let { tryb ->
            applyOverrideConfiguration(Configuration().apply { uiMode = tryb })
        }
    }

    /** Przygotowanie wspólnego stanu; wywołują je aktywności przed super.onCreate. */
    protected fun dolaczPrzegladarke() {
        p = Przegladarka.dla(this)
        p.dolacz(this)
    }

    protected val noc: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onStart() {
        super.onStart()
        p.wznow()
        // certyfikat „Urządzenie LivePL” zakłada się sam, bez pytania (jak w LivePL na Windows i iPhonie)
        Urzadzenie.dla(this).przyStarcie()
    }

    override fun onStop() {
        super.onStop()
        p.zapiszSesje()
        p.wstrzymaj()
    }

    override fun onDestroy() {
        p.odlacz(this, koniec = isFinishing && !isChangingConfigurations)
        super.onDestroy()
    }

    // MARK: - Uprawnienia Androida (aparat, mikrofon, lokalizacja, zapis)

    private fun maUprawnienie(nazwa: String) = checkSelfPermission(nazwa) == PackageManager.PERMISSION_GRANTED

    fun poprosOUprawnienia(lista: Array<String>, gotowe: (Map<String, Boolean>) -> Unit) {
        if (lista.all { maUprawnienie(it) }) {
            gotowe(lista.associateWith { true })
            return
        }
        // poprzednia prośba bez odpowiedzi przepada
        p.poUprawnieniach?.invoke(emptyMap())
        p.poUprawnieniach = gotowe
        p.proszoneUprawnienia = lista
        prosbaOUprawnienia.launch(lista.filterNot { maUprawnienie(it) }.toTypedArray())
    }

    // MARK: - Pliki w formularzach (<input type=file>)

    fun wybierzPliki(zwrot: ValueCallback<Array<Uri>>, opcje: FileChooserParams) {
        p.plikiZwrot?.onReceiveValue(null)
        p.plikiZwrot = zwrot
        p.zdjecie = null

        val wybor = try {
            opcje.createIntent()
        } catch (e: Exception) {
            Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        }
        if (opcje.mode == FileChooserParams.MODE_OPEN_MULTIPLE) wybor.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        val typy = opcje.acceptTypes.filter { it.isNotBlank() }.map { it.lowercase() }
        val obrazy = typy.isEmpty() || typy.any {
            it.startsWith("image/") || it in setOf(".jpg", ".jpeg", ".png", ".heic", ".webp")
        }
        val maAparat = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        fun uruchom(zAparatem: Boolean) {
            val aparat = if (zAparatem) aparat() else null
            val okno = Intent.createChooser(wybor, "Wybierz plik").apply {
                if (aparat != null) putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(aparat))
            }
            try {
                wyborPlikow.launch(okno)
            } catch (e: Exception) {
                p.plikiZwrot = null
                zwrot.onReceiveValue(null)
            }
        }

        when {
            !obrazy || !maAparat -> uruchom(false)
            maUprawnienie(Manifest.permission.CAMERA) -> uruchom(true)
            // pole z „capture” wprost prosi o zdjęcie, więc pytamy o aparat
            opcje.isCaptureEnabled -> poprosOUprawnienia(arrayOf(Manifest.permission.CAMERA)) { w ->
                uruchom(w[Manifest.permission.CAMERA] == true)
            }
            else -> uruchom(false)
        }
    }

    private fun aparat(): Intent? = try {
        val folder = File(cacheDir, "zdjecia").apply { mkdirs() }
        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val plik = File(folder, "zdjecie-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.pliki", plik)
        p.zdjecie = uri to plik
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } catch (e: Exception) {
        null
    }
}
