package eu.livepl.przegladarka

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/** Tłumacz Google ML Kit: wszystko dzieje się w telefonie, tekst strony nigdzie nie wychodzi. */
object SilnikTlumaczenia : Tlumacz {
    private val rozpoznawanie by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.5f).build(),
        )
    }
    private val tlumacze = mutableMapOf<String, Translator>()

    private fun tlumacz(zJezyka: String): Translator = tlumacze.getOrPut(zJezyka) {
        Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(zJezyka).setTargetLanguage(TranslateLanguage.POLISH).build(),
        )
    }

    override fun obslugiwany(tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val t = tag.trim().replace('_', '-').lowercase()
        return TranslateLanguage.fromLanguageTag(t) ?: TranslateLanguage.fromLanguageTag(t.substringBefore('-'))
    }

    override fun rozpoznaj(tekst: String, gotowe: (String?) -> Unit) {
        if (tekst.length < 20) return gotowe(null)
        rozpoznawanie.identifyLanguage(tekst)
            .addOnSuccessListener { gotowe(if (it == "und") null else it) }
            .addOnFailureListener { gotowe(null) }
    }

    override fun maSlownik(zJezyka: String, gotowe: (Boolean) -> Unit) {
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { modele ->
                val kody = modele.map { it.language }
                gotowe(zJezyka in kody && TranslateLanguage.POLISH in kody)
            }
            .addOnFailureListener { gotowe(false) }
    }

    override fun pobierzSlownik(zJezyka: String, gotowe: () -> Unit, blad: () -> Unit) {
        tlumacz(zJezyka).downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { gotowe() }
            .addOnFailureListener { blad() }
    }

    override fun przetlumacz(zJezyka: String, teksty: List<String>, gotowe: (List<String>) -> Unit) {
        val t = tlumacz(zJezyka)
        val zadania = teksty.map { tekst -> t.translate(tekst).continueWith { z -> if (z.isSuccessful) z.result else tekst } }
        Tasks.whenAllComplete(zadania).addOnCompleteListener {
            gotowe(zadania.mapIndexed { i, z -> z.result ?: teksty[i] })
        }
    }
}
