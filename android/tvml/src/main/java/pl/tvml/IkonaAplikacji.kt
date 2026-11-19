package pl.tvml

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Ikona zimowa i świąteczna są wbudowane w aplikację (activity-alias w AndroidManifest.xml), a panel tvml.pl
 * wybiera je przez wersję wyglądu (config.theme.appIcon). Na iPhonie to setAlternateIconName.
 * Zmiana idzie, gdy aplikacja schodzi z ekranu: launcher podmienia wtedy ikonę bez zamykania aplikacji.
 */
object IkonaAplikacji {
    enum class Rodzaj(val alias: String) { ZWYKLA("IkonaZwykla"), ZIMA("IkonaZima"), SWIETA("IkonaSwieta") }

    private fun skladnik(kontekst: Context, r: Rodzaj) = ComponentName(kontekst.packageName, "pl.tvml.${r.alias}")

    fun obecna(kontekst: Context): Rodzaj {
        val pm = kontekst.packageManager
        return Rodzaj.entries.firstOrNull { r ->
            when (pm.getComponentEnabledSetting(skladnik(kontekst, r))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> r == Rodzaj.ZWYKLA
                else -> false
            }
        } ?: Rodzaj.ZWYKLA
    }

    fun ustaw(kontekst: Context, cel: Rodzaj) {
        if (obecna(kontekst) == cel) return
        val pm = kontekst.packageManager
        // najpierw włącz nową, potem wyłącz pozostałe: zawsze jest jakaś ikona w launcherze
        pm.setComponentEnabledSetting(skladnik(kontekst, cel), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (r in Rodzaj.entries) if (r != cel) {
            pm.setComponentEnabledSetting(skladnik(kontekst, r), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
