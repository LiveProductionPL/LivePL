package eu.livepl.przegladarka

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri

/**
 * Aplikacja Poczta LivePL widziana z przeglądarki LivePL: czy jest na telefonie, jak ją zainstalować i otworzyć
 * (jak PropozycjaPoczty.swift na iPhonie). Android pokazuje ją dzięki wpisowi <queries> w manifeście przeglądarki.
 */
object AplikacjaPoczty {
    const val PAKIET = "eu.livepl.poczta"
    private const val KLUCZ_ODRZUCENIA = "livepl-poczta-aplikacja-odrzucona"
    private const val DNI_SPOKOJU = 30L

    /** Wersja testowa przeglądarki szuka najpierw testowej Poczty, wydanie tylko wydania. */
    private val pakiety: List<String>
        get() = if (BuildConfig.DEBUG) listOf("$PAKIET.debug", PAKIET) else listOf(PAKIET)

    /** Pakiet zainstalowanej Poczty LivePL albo null. */
    fun zainstalowana(kontekst: Context): String? = pakiety.firstOrNull { pakiet ->
        runCatching { kontekst.packageManager.getPackageInfo(pakiet, 0) }.isSuccess
    }

    fun otworz(kontekst: Context): Boolean {
        val pakiet = zainstalowana(kontekst) ?: return false
        val intencja = kontekst.packageManager.getLaunchIntentForPackage(pakiet) ?: return false
        return try {
            kontekst.startActivity(intencja.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Sklep Google Play (aplikacja), a bez niej strona Sklepu w karcie LivePL. */
    fun instaluj(p: Przegladarka) {
        val a = p.aktywnosc ?: return
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PAKIET")))
        } catch (_: ActivityNotFoundException) {
            p.otworz("https://play.google.com/store/apps/details?id=$PAKIET")
        }
    }

    /** „×” chowa propozycję na 30 dni. */
    fun odrzuconaNiedawno(ustawienia: SharedPreferences): Boolean {
        val kiedy = ustawienia.getLong(KLUCZ_ODRZUCENIA, 0)
        return kiedy > 0 && System.currentTimeMillis() - kiedy < DNI_SPOKOJU * 86_400_000
    }

    fun odrzuc(ustawienia: SharedPreferences) {
        ustawienia.edit().putLong(KLUCZ_ODRZUCENIA, System.currentTimeMillis()).apply()
    }
}
