package eu.livepl.przegladarka

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eu.livepl.przegladarka.ui.Glowny

/**
 * Przeglądarka LivePL: ekran Start, programy i karty. Dzień albo noc według przełącznika przy zegarze,
 * a resztę oddaje widokom (Glowny) i stanowi przeglądarki (Przegladarka).
 */
class MainActivity : AktywnoscLivePL() {
    override fun onCreate(savedInstanceState: Bundle?) {
        dolaczPrzegladarke()
        super.onCreate(savedInstanceState)

        val nocTeraz = noc
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { nocTeraz },
            navigationBarStyle = SystemBarStyle.auto(
                Color.argb(0xe6, 0xff, 0xff, 0xff),
                Color.argb(0x80, 0x1b, 0x1b, 0x1b),
            ) { nocTeraz },
        )
        setContent {
            LivePLMotyw { Glowny(p) }
        }

        p.uruchom()
        val zHistorii = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !zHistorii) p.obsluz(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        p.obsluz(intent)
    }
}
