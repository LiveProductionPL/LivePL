package pl.tvml

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Temat powiadomień z config.push.topics, np. „urgent” (Pilne) albo „county:osieczna” (Gmina Osieczna). */
data class TematPush(val id: String, val nazwa: String, val domyslny: Boolean) {
    val gmina: Boolean get() = id.startsWith("county:")
}

/**
 * Powiadomienia push przez Firebase Cloud Messaging (projekt tvml-3f2b5), bez Firebase Analytics.
 * Kontrakt z serwerem tvml.pl (API 1.8.2, uzgodniony z sesją TVML Connect 23.11.2026):
 *
 * - od 2.2 (Karol, 24.11: „domyślnie powinna być funkcja włączona … na wszystkie tematy”): przy pierwszym uruchomieniu
 *   (także po aktualizacji) raz nasze okno „Włączyć powiadomienia o nowych wpisach?” Tak/Nie; „Tak” → na Androidzie 13+
 *   systemowa prośba o zgodę → rejestracja ze WSZYSTKIMI tematami; „Nie” → nic i bez ponownego pytania. Przełącznik
 *   w Więcej → Powiadomienia przy pierwszym włączeniu też zaznacza wszystkie tematy; każdy można odznaczyć;
 * - POST devices {platform, token, topics, appVersion, lang}: przy starcie, po zmianie tokenu, tematów albo języka;
 *   bez identyfikatora urządzenia, tylko token FCM;
 * - POST devices/delete {token}: po wyłączeniu, odebraniu zgody, odznaczeniu wszystkich tematów albo
 *   wyłączeniu push w panelu (config.push.enabled = false);
 * - wiadomość: notification {title, body} i data {articleId, url, topic, lang}; kanał „pilne” (urgent, wysoka
 *   ważność) albo „nowe_wpisy”. Stuknięcie otwiera artykuł (MainActivity czyta articleId z extras).
 *
 * Wersja zbudowana bez google-services.json nie ma Firebase i nic tu nie robi ([dostepne]).
 */
object Powiadomienia {
    const val KANAL_PILNE = "pilne"
    const val KANAL_NOWE = "nowe_wpisy"
    private val tlo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Jedna rejestracja naraz: start, config i powrót do aplikacji potrafią zawołać ją jednocześnie. */
    private val zamek = Mutex()

    /** Stan dla ekranu Więcej → Powiadomienia (z ustawień, odświeżany po każdej zmianie). */
    var naSerwerze by mutableStateOf(false)
        private set
    var wlaczone by mutableStateOf(false)
        private set
    var zgoda by mutableStateOf(true)
        private set
    var tematy by mutableStateOf<List<TematPush>>(emptyList())
        private set
    var wybrane by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Pokazać okno „Włączyć powiadomienia o nowych wpisach?” (raz, gdy push działa, a czytelnik jeszcze nie decydował). */
    var doZapytania by mutableStateOf(false)
        private set

    private fun ustawienia(ctx: Context) = ctx.applicationContext.getSharedPreferences("powiadomienia", Context.MODE_PRIVATE)

    /** Firebase jest w tej wersji (google-services.json przy budowaniu) i uruchomił się. */
    fun dostepne(ctx: Context): Boolean = BuildConfig.PUSH && FirebaseApp.getApps(ctx.applicationContext).isNotEmpty()

    /** Zgoda w systemie: na Androidzie 13+ uprawnienie POST_NOTIFICATIONS, wcześniej przełącznik w ustawieniach aplikacji. */
    fun zgodaSystemu(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Wczytuje stan; wywoływane przy starcie, po powrocie do aplikacji (zgoda mogła się zmienić) i w usłudze FCM. */
    fun przygotuj(ctx: Context) {
        val u = ustawienia(ctx)
        naSerwerze = u.getBoolean("serwer", false)
        wlaczone = u.getBoolean("uzytkownik", false)
        zgoda = zgodaSystemu(ctx)
        tematy = runCatching {
            JSONArray(u.getString("tematy", "[]")).obiekty()
                .map { TematPush(it.s("id"), it.s("title"), it.b("default")) }
                .filter { it.id.isNotEmpty() }
        }.getOrDefault(emptyList())
        val wlasne = u.getStringSet("wybrane", null)
        wybrane = if (wlasne != null) wlasne.filter { w -> tematy.any { it.id == w } }.toSet()
        else tematy.map { it.id }.toSet()
        doZapytania = dostepne(ctx) && naSerwerze && tematy.isNotEmpty() && !wlaczone &&
            !u.getBoolean("zapytano", false) && !u.getBoolean("doZrzutow", false)
    }

    /** Odpowiedź z okna przy pierwszym uruchomieniu; pytamy tylko raz (także po „Nie”). */
    fun zapytano(ctx: Context) {
        ustawienia(ctx).edit { putBoolean("zapytano", true) }
        przygotuj(ctx)
    }

    /** config.push z serwera; zapamiętany, bo usługa FCM działa też wtedy, gdy aplikacja nie jest otwarta. */
    fun zKonfiguracji(ctx: Context, push: JSONObject) {
        if (!push.ma("enabled") && !push.ma("topics")) return
        ustawienia(ctx).edit {
            putBoolean("serwer", push.b("enabled"))
            putString("tematy", push.optJSONArray("topics")?.toString() ?: "[]")
        }
        przygotuj(ctx)
    }

    /** Główny przełącznik. Przy pierwszym włączeniu zaznaczone są wszystkie tematy (każdy można potem odznaczyć). */
    fun ustawWlaczone(ctx: Context, tak: Boolean) {
        ustawienia(ctx).edit {
            putBoolean("uzytkownik", tak)
            putBoolean("zapytano", true)
            if (tak && !ustawienia(ctx).contains("wybrane")) putStringSet("wybrane", tematy.map { it.id }.toSet())
        }
        przygotuj(ctx)
        zarejestruj(ctx)
    }

    fun ustawTemat(ctx: Context, id: String, tak: Boolean) {
        ustawienia(ctx).edit { putStringSet("wybrane", if (tak) wybrane + id else wybrane - id) }
        przygotuj(ctx)
        zarejestruj(ctx)
    }

    /** Ustawienia powiadomień TVML w systemie (gdy czytelnik wcześniej odmówił zgody). */
    fun otworzUstawieniaSystemu(ctx: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    /**
     * Do zrzutów ekranu (--ez bezPowiadomien true): bez rejestracji emulatora na serwerze, nawet po włączeniu.
     * Zostaje do wyczyszczenia danych aplikacji (skrypty zrzutów i tak robią pm clear).
     */
    fun wylaczDoZrzutow(ctx: Context) = ustawienia(ctx).edit { putBoolean("doZrzutow", true) }

    /**
     * Token na serwer z tematami i językiem albo wyrejestrowanie. Ten sam zestaw idzie ponownie najwyżej raz
     * na tydzień (serwer wie wtedy, że urządzenie dalej działa), zmiana tokenu, tematów lub języka od razu.
     */
    fun zarejestruj(ctx: Context, token: String? = null) {
        val app = ctx.applicationContext
        if (!dostepne(app) || ustawienia(app).getBoolean("doZrzutow", false)) return
        tlo.launch { zamek.withLock {
            val u = ustawienia(app)
            val chce = naSerwerze && wlaczone && zgodaSystemu(app) && wybrane.isNotEmpty()
            if (!chce && u.getString("wyslane", null) == null) return@withLock
            val t = token ?: runCatching { FirebaseMessaging.getInstance().token.wynik() }.getOrNull() ?: return@withLock
            if (!chce) {
                val kod = runCatching { Siec.v1Wyslij("devices/delete", JSONObject().put("token", t)).first }.getOrDefault(0)
                if (BuildConfig.DEBUG) android.util.Log.d("TVML-Push", "devices/delete $kod")
                if (kod in 200..299) u.edit { remove("wyslane"); remove("kiedy") }
                return@withLock
            }
            val cialo = JSONObject()
                .put("platform", "android")
                .put("token", t)
                .put("topics", JSONArray(wybrane.sorted()))
                .put("appVersion", BuildConfig.VERSION_NAME.substringBefore('-'))
                .put("lang", Jezyk.biezacy)
            val podpis = cialo.toString()
            if (podpis == u.getString("wyslane", null) && System.currentTimeMillis() - u.getLong("kiedy", 0) < 7 * 86_400_000L) return@withLock
            val kod = runCatching { Siec.v1Wyslij("devices", cialo).first }.getOrDefault(0)
            if (BuildConfig.DEBUG) android.util.Log.d("TVML-Push", "devices $kod")
            if (kod in 200..299) u.edit { putString("wyslane", podpis); putLong("kiedy", System.currentTimeMillis()) }
        } }
    }

    /**
     * Kanały jak na serwerze: „pilne” (wysoka ważność, dźwięk i baner) i „nowe_wpisy” (domyślna).
     * Nazwy w bieżącym języku (ponowne utworzenie zmienia tylko nazwę, ustawień czytelnika nie rusza).
     */
    fun przygotujKanaly(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(KANAL_PILNE, tl("app.pushUrgentChannel", "Pilne"), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(KANAL_NOWE, tl("app.pushNewChannel", "Nowe wpisy"), NotificationManager.IMPORTANCE_DEFAULT))
        // kanał z pierwszej wersji testowej (przed kontraktem z serwerem)
        nm.deleteNotificationChannel("wiadomosci")
    }

    /** Powiadomienie, gdy aplikacja jest na ekranie (wtedy system sam go nie pokazuje). Stuknięcie otwiera artykuł. */
    @SuppressLint("MissingPermission")
    fun pokaz(ctx: Context, tytul: String, tresc: String, kanal: String, dane: Map<String, String>) {
        if (!zgodaSystemu(ctx)) return
        przygotujKanaly(ctx)
        val numer = (dane["articleId"] ?: dane["url"] ?: tytul).hashCode()
        val otworz = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            dane.forEach { (k, v) -> putExtra(k, v) }
        }
        val pi = PendingIntent.getActivity(ctx, numer, otworz, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, kanal)
            .setSmallIcon(R.drawable.ikona_powiadomienia)
            .setColor(ContextCompat.getColor(ctx, R.color.granat))
            .setContentTitle(tytul)
            .setContentText(tresc)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tresc))
            .setPriority(if (kanal == KANAL_PILNE) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(ctx).notify(numer, n)
    }

    /** Numer artykułu z danych powiadomienia: articleId albo link tvml.pl/artykul/123. */
    fun artykul(i: Intent): Int? {
        val e = i.extras ?: return null
        e.getString("articleId")?.trim()?.toIntOrNull()?.let { return it }
        val adres = e.getString("url") ?: return null
        val u = runCatching { Uri.parse(adres) }.getOrNull() ?: return null
        if (u.host?.removePrefix("www.") != "tvml.pl") return null
        return u.lastPathSegment?.takeIf { u.pathSegments.firstOrNull() == "artykul" }?.takeWhile { it.isDigit() }?.toIntOrNull()
    }
}

/** Wynik zadania Google Play Services w korutynie (bez dodatkowej biblioteki). */
private suspend fun <T> Task<T>.wynik(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}

/** Usługa FCM: nowy token od razu na serwer, wiadomość przy otwartej aplikacji jako zwykłe powiadomienie. */
class UslugaPowiadomien : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Jezyk.przygotujRaz(applicationContext)
        Powiadomienia.przygotuj(applicationContext)
        Powiadomienia.zarejestruj(this, token)
    }

    override fun onMessageReceived(w: RemoteMessage) {
        Jezyk.przygotujRaz(applicationContext)
        val tytul = w.notification?.title ?: w.data["title"] ?: return
        val tresc = w.notification?.body ?: w.data["body"].orEmpty()
        val kanal = w.notification?.channelId?.takeIf { it == Powiadomienia.KANAL_PILNE || it == Powiadomienia.KANAL_NOWE }
            ?: if (w.data["topic"] == "urgent") Powiadomienia.KANAL_PILNE else Powiadomienia.KANAL_NOWE
        Powiadomienia.pokaz(this, tytul, tresc, kanal, w.data)
    }
}
