package eu.livepl.przegladarka

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.annotation.DrawableRes

/** Tryb dzień i noc przy zegarze: auto → dzień → noc → auto, jak w LivePL na Windows. */
enum class Motyw(val klucz: String, @DrawableRes val ikona: Int, val opis: String) {
    AUTO("auto", R.drawable.ic_auto, "automatycznie, jak telefon"),
    DZIEN("dzien", R.drawable.ic_dzien, "dzień"),
    NOC("noc", R.drawable.ic_noc, "noc");

    val nastepny: Motyw
        get() = when (this) {
            AUTO -> DZIEN
            DZIEN -> NOC
            NOC -> AUTO
        }

    /** Tryb nocny w konfiguracji Androida albo null, gdy decyduje telefon. */
    val trybNocny: Int?
        get() = when (this) {
            AUTO -> null
            DZIEN -> Configuration.UI_MODE_NIGHT_NO
            NOC -> Configuration.UI_MODE_NIGHT_YES
        }

    companion object {
        const val USTAWIENIA = "livepl"
        private const val KLUCZ = "motyw"

        fun zapisany(ustawienia: SharedPreferences): Motyw {
            val k = ustawienia.getString(KLUCZ, null)
            return entries.firstOrNull { it.klucz == k } ?: AUTO
        }

        fun zapisany(kontekst: Context): Motyw =
            zapisany(kontekst.getSharedPreferences(USTAWIENIA, Context.MODE_PRIVATE))

        fun zapisz(ustawienia: SharedPreferences, motyw: Motyw) {
            ustawienia.edit().putString(KLUCZ, motyw.klucz).apply()
        }
    }
}
