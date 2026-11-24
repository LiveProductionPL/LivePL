package pl.tvml

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * „Odsłuchaj”: czyta artykuł głosem telefonu (silnik mowy Androida) w języku tekstu: oryginał po polsku (pl-PL),
 * tłumaczenie po ukraińsku (uk-UA) albo angielsku (en-GB), także przy wygaszonym ekranie. Jak na iPhonie
 * (Karol 24.11: „wersja en i uk musi być 1:1 polskiej”): przycisk jest przy każdym artykule, a na ekranie blokady,
 * w powiadomieniach i w sterowaniu multimediami jest tytuł z „TVML” oraz pauza, wznowienie i stop (MediaSession).
 * Powiadomienie sesji multimediów nie wymaga zgody na powiadomienia (Android 13+ je z niej zwalnia).
 * Po końcu czytania sesja i powiadomienie znikają.
 */
object Lektor {
    /** czyta albo stoi w pauzie z ekranu blokady (jak `mowi` na iPhonie) */
    var mowi by mutableStateOf(false)
        private set

    /** który artykuł czyta (przycisk w innym artykule zaczyna czytanie od nowa) */
    var artykul by mutableStateOf<Int?>(null)
        private set

    private const val KANAL = "lektor"
    private const val ID_POWIADOMIENIA = 4201
    internal const val PAUZA = "pl.tvml.lektor.PAUZA"
    internal const val WZNOW = "pl.tvml.lektor.WZNOW"
    internal const val STOP = "pl.tvml.lektor.STOP"

    private var app: Context? = null
    private var tts: TextToSpeech? = null
    private var gotowy = false
    private var pauza = false
    private var kawalki: List<String> = emptyList()
    private var biezacy = 0
    private var jezyk = "pl"
    private var tytul = ""
    private var sesja: MediaSession? = null

    /** Przycisk w artykule: zaczyna czytanie albo je kończy. [jezyk] to język pokazanego tekstu (pl, uk, en). */
    fun przelacz(kontekst: Context, akapity: List<String>, jezyk: String, tytul: String, id: Int) {
        if (mowi && artykul == id) { zatrzymaj(); return }
        tts?.stop()
        app = kontekst.applicationContext
        this.jezyk = jezyk
        this.tytul = tytul
        artykul = id
        // silnik mowy przyjmuje ograniczoną długość tekstu, więc akapit po akapicie
        val granica = TextToSpeech.getMaxSpeechInputLength() - 1
        kawalki = akapity.flatMap { it.chunked(granica) }.filter { it.isNotBlank() }
        biezacy = 0
        pauza = false
        mowi = true
        val t = tts
        if (t == null) tts = TextToSpeech(app) { stan -> naGlownym { poStarcie(stan) } }
        else if (gotowy) mow()
    }

    private fun poStarcie(stan: Int) {
        val t = tts
        gotowy = stan == TextToSpeech.SUCCESS && t != null
        if (!gotowy || t == null) {
            tts = null
            koniec()
            komunikat(tl("app.ttsMissing", "Telefon nie ma silnika mowy."))
            return
        }
        t.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
        )
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                val i = id?.removePrefix("a")?.toIntOrNull() ?: return
                naGlownym { if (!pauza) biezacy = i }
            }

            override fun onDone(id: String?) {
                naGlownym { if (mowi && !pauza && id == "a${kawalki.size - 1}") koniec() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                naGlownym { if (!pauza) koniec() }
            }
        })
        if (mowi && !pauza) mow()
    }

    /** Głos w języku tekstu; brak angielskiego brytyjskiego → amerykański (jak na iPhonie). */
    private fun ustawGlos(t: TextToSpeech): Boolean {
        fun ok(wynik: Int) = wynik != TextToSpeech.LANG_MISSING_DATA && wynik != TextToSpeech.LANG_NOT_SUPPORTED
        return when (jezyk) {
            "uk" -> ok(t.setLanguage(Locale.forLanguageTag("uk-UA")))
            "en" -> ok(t.setLanguage(Locale.forLanguageTag("en-GB"))) || ok(t.setLanguage(Locale.forLanguageTag("en-US")))
            else -> ok(t.setLanguage(Locale.forLanguageTag("pl-PL")))
        }
    }

    private fun mow() {
        val t = tts ?: return
        if (!ustawGlos(t)) {
            koniec()
            komunikat(
                if (jezyk == "pl") tl("app.ttsNoPolish", "Brak polskiego głosu. Dodasz go w ustawieniach telefonu: Zamiana tekstu na mowę.")
                else tl("app.ttsNoVoice", "Brak głosu w tym języku. Dodasz go w ustawieniach telefonu: Zamiana tekstu na mowę."),
            )
            return
        }
        if (biezacy >= kawalki.size) { koniec(); return }
        for (i in biezacy until kawalki.size) {
            t.speak(kawalki[i], if (i == biezacy) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "a$i")
        }
        pokaz(gra = true)
    }

    /** Pauza z ekranu blokady, słuchawek albo powiadomienia: czytanie wróci od bieżącego akapitu. */
    fun wstrzymaj() {
        if (!mowi || pauza) return
        pauza = true
        tts?.stop()
        pokaz(gra = false)
    }

    fun wznow() {
        if (!mowi || !pauza) return
        pauza = false
        mow()
    }

    fun przelaczPauze() = if (pauza) wznow() else wstrzymaj()

    fun zatrzymaj() {
        tts?.stop()
        koniec()
    }

    private fun koniec() {
        mowi = false
        pauza = false
        artykul = null
        schowaj()
    }

    /** Tytuł artykułu z „TVML” na ekranie blokady i w sterowaniu multimediami; pauza, wznowienie i stop. */
    private fun pokaz(gra: Boolean) {
        val c = app ?: return
        val s = sesja ?: MediaSession(c, "TVML-lektor").also { nowa ->
            nowa.setCallback(object : MediaSession.Callback() {
                override fun onPlay() = wznow()
                override fun onPause() = wstrzymaj()
                override fun onStop() = zatrzymaj()
            })
            // przyciski słuchawek: dźwięk gra silnik mowy (inna aplikacja), więc system może je wysłać tylko tutaj
            val odbiornik = ComponentName(c, OdbiornikLektora::class.java)
            if (Build.VERSION.SDK_INT >= 31) nowa.setMediaButtonBroadcastReceiver(odbiornik)
            else @Suppress("DEPRECATION") nowa.setMediaButtonReceiver(
                PendingIntent.getBroadcast(c, 1, Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(odbiornik), PendingIntent.FLAG_IMMUTABLE),
            )
            sesja = nowa
        }
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, tytul)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "TVML")
                .build(),
        )
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                .setState(if (gra) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (gra) 1f else 0f)
                .build(),
        )
        s.isActive = true
        powiadomienie(c, s, gra)
    }

    // Powiadomienie sesji multimediów jest zwolnione ze zgody POST_NOTIFICATIONS; gdyby system je odrzucił,
    // czytanie i tak trwa (runCatching).
    @SuppressLint("MissingPermission")
    private fun powiadomienie(c: Context, s: MediaSession, gra: Boolean) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(KANAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(KANAL, tl("app.listen", "Odsłuchaj"), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            )
        }
        val flagi = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun polecenie(akcja: String) =
            PendingIntent.getBroadcast(c, akcja.hashCode(), Intent(c, OdbiornikLektora::class.java).setAction(akcja), flagi)
        fun przycisk(ikona: Int, opis: String, akcja: String) =
            Notification.Action.Builder(Icon.createWithResource(c, ikona), opis, polecenie(akcja)).build()
        val otworz = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), flagi,
        )
        val n = Notification.Builder(c, KANAL)
            .setSmallIcon(R.drawable.ikona_powiadomienia)
            .setColor(c.getColor(R.color.granat))
            .setContentTitle(tytul)
            .setContentText("TVML")
            .setContentIntent(otworz)
            .setDeleteIntent(polecenie(STOP))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(gra)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(
                if (gra) przycisk(android.R.drawable.ic_media_pause, tl("app.pause", "Pauza"), PAUZA)
                else przycisk(android.R.drawable.ic_media_play, tl("app.play", "Odtwórz"), WZNOW),
            )
            .addAction(przycisk(android.R.drawable.ic_menu_close_clear_cancel, tl("app.stopListening", "Zatrzymaj"), STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(s.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
        runCatching { nm.notify(ID_POWIADOMIENIA, n) }
    }

    private fun schowaj() {
        app?.getSystemService(NotificationManager::class.java)?.cancel(ID_POWIADOMIENIA)
        sesja?.let { it.isActive = false; it.release() }
        sesja = null
    }

    /** Przycisk w powiadomieniu po zamknięciu aplikacji przez system: samo powiadomienie trzeba sprzątnąć. */
    internal fun posprzataj(c: Context) {
        if (app == null) app = c.applicationContext
        if (!mowi) app?.getSystemService(NotificationManager::class.java)?.cancel(ID_POWIADOMIENIA)
    }

    private fun naGlownym(akcja: () -> Unit) {
        Handler(Looper.getMainLooper()).post(akcja)
    }

    private fun komunikat(tekst: String) = naGlownym { app?.let { Toast.makeText(it, tekst, Toast.LENGTH_LONG).show() } }
}

/** Pauza, wznowienie i stop z powiadomienia lektora oraz z przycisków słuchawek (ACTION_MEDIA_BUTTON). */
class OdbiornikLektora : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Lektor.PAUZA -> Lektor.wstrzymaj()
            Lektor.WZNOW -> Lektor.wznow()
            Lektor.STOP -> Lektor.zatrzymaj()
            Intent.ACTION_MEDIA_BUTTON -> {
                val klawisz = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                if (klawisz?.action == KeyEvent.ACTION_DOWN) when (klawisz.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> Lektor.wstrzymaj()
                    KeyEvent.KEYCODE_MEDIA_PLAY -> Lektor.wznow()
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> Lektor.przelaczPauze()
                    KeyEvent.KEYCODE_MEDIA_STOP -> Lektor.zatrzymaj()
                }
            }
        }
        Lektor.posprzataj(c)
    }
}
