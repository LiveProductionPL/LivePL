package eu.livepl.przegladarka

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import kotlin.concurrent.thread

/**
 * Rejestracja tego telefonu w urzędzie LivePL (certyfikat „Urządzenie LivePL”), jak src/urzadzenie.js na Windows
 * i Urzadzenie.swift na iPhonie. Klucz EC P-256 powstaje w Android Keystore (StrongBox albo TEE, czyli sprzętowy
 * odpowiednik TPM) i nie da się go skopiować. Wniosek PKCS#10 idzie do cert.livepl.eu, urząd wystawia certyfikat
 * od razu, a LivePL sprawdza, że pasuje do klucza i pochodzi z urzędu LivePL. Co kilka godzin LivePL wysyła krótki
 * raport (wersja), a w odpowiedzi dowiaduje się, czy urząd nie unieważnił tego telefonu.
 */
class Urzadzenie private constructor(kontekst: Context) {
    private val aplikacja = kontekst.applicationContext
    private val ustawienia = aplikacja.getSharedPreferences("livepl-urzadzenie", Context.MODE_PRIVATE)
    private val glowny = Handler(Looper.getMainLooper())

    /** Stan rejestracji: brak, wyslany, gotowe, odrzucony, uniewazniony. */
    data class Zapis(
        val stan: String = "brak",
        val id: String? = null,
        val kod: String? = null,
        val csr: String? = null,
        val sprzetowy: Boolean = false,
        val strongbox: Boolean = false,
        val certyfikat: String? = null,
        val waznyDo: Long? = null,
        val raport: Long? = null,
        val polaczenie: String? = null,
    )

    /** Stan dla logiki (wątki w tle czytają go od razu po zmianie). */
    @Volatile
    private var biezacy = wczytaj()

    /** Ten sam stan dla widoków (Compose), zmieniany w wątku głównym. */
    var zapis by mutableStateOf(biezacy)
        private set
    var trwa by mutableStateOf(false)
        private set
    var blad by mutableStateOf<String?>(null)
        private set

    private var ostatniaProba = 0L

    val zarejestrowany: Boolean get() = biezacy.stan == "gotowe"

    class Problem(tekst: String) : Exception(tekst)

    // MARK: - Kiedy rejestrować

    /**
     * Przy każdym powrocie aplikacji na ekran. Rejestracja idzie sama, bez pytania (jak certyfikaty w LivePL na Windows);
     * po błędzie (np. brak sieci) kolejna próba najwcześniej po 10 minutach. Emulator (zrzuty na GitHubie) nie zakłada
     * „urządzeń” w prawdziwym urzędzie.
     */
    fun przyStarcie() {
        if (emulator() && !BuildConfig.URZAD_TESTOWY) return
        val teraz = System.currentTimeMillis()
        when {
            biezacy.stan == "brak" && !trwa && teraz - ostatniaProba > 600_000 -> {
                ostatniaProba = teraz
                zarejestruj()
            }
            biezacy.stan == "wyslany" -> wTle { sprawdz() }
            else -> wTle { raport(teraz = false) }
        }
    }

    /** Rejestracja (też ponowna, z nowym kluczem, gdy urząd odrzucił albo unieważnił ten telefon). */
    fun zarejestruj() {
        if (trwa) return
        trwa = true
        blad = null
        thread(name = "LivePL-urzad") {
            val wynik = runCatching { rejestracja() }
            glowny.post {
                trwa = false
                wynik.exceptionOrNull()?.let { blad = opis(it) }
            }
        }
    }

    /** „Sprawdź”, gdy wniosek czeka w urzędzie. */
    fun sprawdzTeraz() {
        wTle { sprawdz() }
    }

    /** Klucz i łańcuch certyfikatów do logowania certyfikatem klienta na naszych stronach (TLS). */
    fun tozsamosc(): Pair<PrivateKey, Array<X509Certificate>>? {
        if (!zarejestrowany) return null
        return try {
            val wpis = magazyn().getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
            val lancuch = wpis.certificateChain.mapNotNull { it as? X509Certificate }
            if (lancuch.isEmpty() || lancuch[0].subjectX500Principal == lancuch[0].issuerX500Principal) return null
            wpis.privateKey to lancuch.toTypedArray()
        } catch (e: Exception) {
            null
        }
    }

    // MARK: - Urząd

    private fun rejestracja() {
        var z = biezacy
        if (z.csr == null || z.stan == "odrzucony" || z.stan == "uniewazniony") {
            if (z.stan == "odrzucony" || z.stan == "uniewazniony") usunKlucz()
            val klucz = kluczUrzadzenia()
            val spki = klucz.publiczny.encoded
            val kod = kodZKlucza(spki)
            z = Zapis(
                kod = kod, csr = wniosek(klucz.prywatny, spki, kod),
                sprzetowy = klucz.sprzetowy, strongbox = klucz.strongbox,
            )
        }
        val (status, dane) = zapytaj(
            "/urzadzenia/wniosek", "POST",
            JSONObject()
                .put("csr", z.csr)
                .put("nazwa", nazwa(z.kod.orEmpty()))
                .put("uzytkownik", "")
                .put("system", "Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})")
                .put("wersja", BuildConfig.VERSION_NAME)
                .put("tpm", z.sprzetowy),
        )
        val id = dane.tekst("id")
        if (status != 200 || id == null) throw Problem(dane.tekst("blad") ?: "Urząd nie przyjął wniosku ($status).")
        dane.tekst("kod")?.let { if (it != z.kod) throw Problem("Kod z urzędu nie zgadza się z kluczem tego telefonu.") }
        ustaw(z.copy(id = id, stan = "wyslany"))
        // urząd wystawia certyfikat od razu (rejestracja automatyczna)
        sprawdz()
        if (biezacy.stan == "gotowe") raport(teraz = true)
    }

    /** Odbiór certyfikatu po zatwierdzeniu wniosku. */
    private fun sprawdz() {
        val z = biezacy
        val id = z.id ?: return
        if (z.stan != "wyslany") return
        val (status, dane) = zapytaj("/urzadzenia/stan/$id")
        val stan = dane.tekst("stan").orEmpty()
        when {
            // urząd nie zna wniosku: przy następnej rejestracji wyślemy ten sam wniosek jeszcze raz
            status == 404 || stan == "nieznany" -> ustaw(z.copy(stan = "brak", id = null))
            stan == "odrzucony" || stan == "uniewazniony" -> ustaw(z.copy(stan = stan))
            stan == "wydany" -> {
                val pem = dane.tekst("certyfikat") ?: return
                val (cert, wystawcy) = sprawdzCertyfikat(pem)
                zapiszCertyfikat(cert, wystawcy)
                ustaw(z.copy(stan = "gotowe", certyfikat = pem, waznyDo = cert.notAfter.time))
            }
        }
    }

    /** Raport co kilka godzin; urząd odpowiada stanem (np. unieważniony) i opisem połączenia. */
    private fun raport(teraz: Boolean) {
        val z = biezacy
        val id = z.id ?: return
        if (z.stan != "gotowe") return
        if (!teraz && z.raport != null && System.currentTimeMillis() - z.raport < CO_ILE_RAPORT) return
        try {
            val (status, dane) = zapytaj(
                "/urzadzenia/raport/$id", "POST",
                JSONObject()
                    // urząd zna tylko BitLocker; stan szyfrowania telefonu pokaże dopiero nowszy panel
                    .put("dysk", "nieznany")
                    // logowania trzyma WebView w danych aplikacji, szyfrowanych przez Androida
                    .put("dpapi", true)
                    .put("wersja", BuildConfig.VERSION_NAME),
            )
            var nowy = biezacy.copy(raport = System.currentTimeMillis())
            if (status == 200) dane.tekst("polaczenie")?.let { nowy = nowy.copy(polaczenie = it.take(80)) }
            if (dane.tekst("stan") == "uniewazniony") nowy = nowy.copy(stan = "uniewazniony")
            ustaw(nowy)
        } catch (_: Exception) {
            // bez sieci: następnym razem
        }
    }

    private fun zapytaj(sciezka: String, metoda: String = "GET", cialo: JSONObject? = null): Pair<Int, JSONObject> {
        val polaczenie = URL(BuildConfig.ADRES_URZEDU + sciezka).openConnection() as HttpURLConnection
        try {
            polaczenie.requestMethod = metoda
            polaczenie.connectTimeout = 30_000
            polaczenie.readTimeout = 30_000
            polaczenie.useCaches = false
            polaczenie.setRequestProperty("Content-Type", "application/json")
            polaczenie.setRequestProperty("User-Agent", "LivePL/${BuildConfig.VERSION_NAME} (Android)")
            if (cialo != null) {
                polaczenie.doOutput = true
                polaczenie.outputStream.use { it.write(cialo.toString().toByteArray()) }
            }
            val status = polaczenie.responseCode
            val strumien = if (status >= 400) polaczenie.errorStream else polaczenie.inputStream
            val tekst = strumien?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(tekst)
            } catch (_: Exception) {
                JSONObject()
            }
            if (status >= 400 && status != 404) throw Problem(json.tekst("blad") ?: "Urząd odpowiedział $status.")
            return status to json
        } finally {
            polaczenie.disconnect()
        }
    }

    // MARK: - Klucz w Android Keystore

    private class Klucz(val prywatny: PrivateKey, val publiczny: PublicKey, val sprzetowy: Boolean, val strongbox: Boolean)

    private fun magazyn(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** Istniejący klucz tego telefonu albo nowy (najpierw StrongBox, potem TEE). */
    private fun kluczUrzadzenia(): Klucz {
        (magazyn().getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry)?.let { wpis ->
            return Klucz(wpis.privateKey, wpis.certificate.publicKey, sprzetowy(wpis.privateKey), biezacy.strongbox)
        }
        fun utworz(strongbox: Boolean): Klucz {
            val opis = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .apply {
                    // StrongBox zna tylko SHA-256; TEE podpisuje też dłuższymi skrótami (TLS z certyfikatem klienta)
                    if (strongbox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                        setIsStrongBoxBacked(true)
                    } else {
                        setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    }
                }
                .build()
            val para = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                .apply { initialize(opis) }
                .generateKeyPair()
            return Klucz(para.private, para.public, sprzetowy(para.private), strongbox)
        }
        val maStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            aplikacja.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (maStrongBox) {
            try {
                return utworz(strongbox = true)
            } catch (_: StrongBoxUnavailableException) {
            } catch (_: Exception) {
            }
        }
        return utworz(strongbox = false)
    }

    /** Klucz w sprzęcie (TEE albo StrongBox), czyli „tpm” w urzędzie. */
    private fun sprzetowy(klucz: PrivateKey): Boolean = try {
        val info = KeyFactory.getInstance(klucz.algorithm, "AndroidKeyStore").getKeySpec(klucz, KeyInfo::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } else {
            @Suppress("DEPRECATION")
            info.isInsideSecureHardware
        }
    } catch (_: Exception) {
        false
    }

    private fun usunKlucz() {
        try {
            magazyn().deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }

    /** np. „Android-K7F2QX”, w Poczcie LivePL „Android-K7F2QX Poczta” (każda aplikacja ma własny klucz). */
    private fun nazwa(kod: String): String {
        val dopisek = buildString {
            if (BuildConfig.POCZTA) append(" Poczta")
            if (BuildConfig.DEBUG) append(" test")
        }
        return "Android-${kod.replace("-", "")}$dopisek"
    }

    /** Wniosek PKCS#10 podpisany kluczem z Android Keystore (ecdsa-with-SHA256). */
    private fun wniosek(klucz: PrivateKey, spki: ByteArray, kod: String): String {
        fun atrybut(oid: ByteArray, wartosc: ByteArray) = DER.zbior(DER.sekwencja(DER.oid(oid), wartosc))
        val temat = DER.sekwencja(
            atrybut(bajty(0x55, 0x04, 0x06), DER.drukowalny("PL")),
            atrybut(bajty(0x55, 0x04, 0x0A), DER.utf8("Live Production")),
            atrybut(bajty(0x55, 0x04, 0x03), DER.utf8("LivePL ${nazwa(kod)}")),
        )
        val info = DER.sekwencja(bajty(0x02, 0x01, 0x00), temat, spki, bajty(0xA0, 0x00))
        val podpis = Signature.getInstance("SHA256withECDSA").run {
            initSign(klucz)
            update(info)
            sign()
        }
        val algorytm = DER.sekwencja(DER.oid(bajty(0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x04, 0x03, 0x02)))
        val calosc = DER.sekwencja(info, algorytm, DER.bity(podpis))
        val base64 = Base64.encodeToString(calosc, Base64.NO_WRAP).chunked(64).joinToString("\n")
        return "-----BEGIN CERTIFICATE REQUEST-----\n$base64\n-----END CERTIFICATE REQUEST-----\n"
    }

    // MARK: - Certyfikat z urzędu

    /** Certyfikat musi pasować do klucza tego telefonu, być ważny i prowadzić do roota urzędu LivePL z aplikacji. */
    private fun sprawdzCertyfikat(pem: String): Pair<X509Certificate, List<X509Certificate>> {
        val fabryka = CertificateFactory.getInstance("X.509")
        val cert = try {
            fabryka.generateCertificate(pem.byteInputStream()) as X509Certificate
        } catch (e: Exception) {
            throw Problem("Urząd przysłał uszkodzony certyfikat.")
        }
        val nasz = (magazyn().getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry)?.certificate?.publicKey
        if (nasz == null || !nasz.encoded.contentEquals(cert.publicKey.encoded)) {
            throw Problem("Certyfikat z urzędu nie pasuje do klucza tego telefonu.")
        }
        try {
            cert.checkValidity()
        } catch (e: Exception) {
            throw Problem("Certyfikat z urzędu jest nieważny.")
        }
        val rooty = certyfikaty("ca")
        val posrednie = certyfikaty("ca/posrednie")
        if (rooty.isEmpty()) throw Problem("Brak certyfikatów urzędu w aplikacji.")
        fun wystawil(c: X509Certificate, wystawca: X509Certificate) =
            c.issuerX500Principal == wystawca.subjectX500Principal && runCatching { c.verify(wystawca.publicKey) }.isSuccess
        fun wazny(c: X509Certificate) = runCatching { c.checkValidity() }.isSuccess
        rooty.firstOrNull { wazny(it) && wystawil(cert, it) }?.let { return cert to listOf(it) }
        for (posredni in posrednie) {
            if (!wazny(posredni) || !wystawil(cert, posredni)) continue
            val root = rooty.firstOrNull { wazny(it) && wystawil(posredni, it) } ?: continue
            return cert to listOf(posredni, root)
        }
        throw Problem("Certyfikat nie pochodzi z urzędu LivePL.")
    }

    private fun certyfikaty(folder: String): List<X509Certificate> {
        val fabryka = CertificateFactory.getInstance("X.509")
        return (aplikacja.assets.list(folder) ?: emptyArray())
            .filter { it.endsWith(".crt") || it.endsWith(".pem") }
            .mapNotNull { plik ->
                runCatching { aplikacja.assets.open("$folder/$plik").use { fabryka.generateCertificate(it) as X509Certificate } }.getOrNull()
            }
    }

    /** Certyfikat z urzędu zastępuje tymczasowy certyfikat klucza w Android Keystore; razem tworzą tożsamość telefonu. */
    private fun zapiszCertyfikat(cert: X509Certificate, wystawcy: List<X509Certificate>) {
        val ks = magazyn()
        val klucz = ks.getKey(ALIAS, null) as? PrivateKey ?: return
        ks.setKeyEntry(ALIAS, klucz, null, (listOf(cert) + wystawcy).toTypedArray())
    }

    // MARK: - Zapis stanu

    private fun ustaw(z: Zapis) {
        biezacy = z
        zapisz(z)
        if (Looper.myLooper() == Looper.getMainLooper()) zapis = z else glowny.post { zapis = z }
    }

    private fun wTle(praca: () -> Unit) {
        thread(name = "LivePL-urzad") {
            try {
                praca()
            } catch (e: Exception) {
                glowny.post { blad = opis(e) }
            }
        }
    }

    private fun opis(e: Throwable): String = when (e) {
        is Problem -> e.message.orEmpty()
        is java.io.IOException -> "Brak połączenia z urzędem LivePL. Spróbuj za chwilę."
        else -> e.message ?: e.toString()
    }

    private fun wczytaj(): Zapis {
        val tekst = ustawienia.getString("stan", null) ?: return Zapis()
        return try {
            val o = JSONObject(tekst)
            Zapis(
                stan = o.tekst("stan") ?: "brak",
                id = o.tekst("id"),
                kod = o.tekst("kod"),
                csr = o.tekst("csr"),
                sprzetowy = o.optBoolean("sprzetowy"),
                strongbox = o.optBoolean("strongbox"),
                certyfikat = o.tekst("certyfikat"),
                waznyDo = o.optLong("waznyDo").takeIf { it > 0 },
                raport = o.optLong("raport").takeIf { it > 0 },
                polaczenie = o.tekst("polaczenie"),
            )
        } catch (_: Exception) {
            Zapis()
        }
    }

    private fun zapisz(z: Zapis) {
        val o = JSONObject()
            .put("stan", z.stan)
            .put("id", z.id ?: JSONObject.NULL)
            .put("kod", z.kod ?: JSONObject.NULL)
            .put("csr", z.csr ?: JSONObject.NULL)
            .put("sprzetowy", z.sprzetowy)
            .put("strongbox", z.strongbox)
            .put("certyfikat", z.certyfikat ?: JSONObject.NULL)
            .put("waznyDo", z.waznyDo ?: 0)
            .put("raport", z.raport ?: 0)
            .put("polaczenie", z.polaczenie ?: JSONObject.NULL)
        ustawienia.edit().putString("stan", o.toString()).apply()
    }

    companion object {
        private const val ALIAS = "livepl-urzadzenie"
        private const val CO_ILE_RAPORT = 6 * 3600 * 1000L

        @Volatile
        private var instancja: Urzadzenie? = null

        fun dla(kontekst: Context): Urzadzenie =
            instancja ?: synchronized(this) { instancja ?: Urzadzenie(kontekst).also { instancja = it } }

        /** Ten sam kod co w panelu urzędu i w LivePL na Windows: 30 bitów SHA-256 klucza, np. K7F-2QX. */
        fun kodZKlucza(spki: ByteArray): String {
            val h = MessageDigest.getInstance("SHA-256").digest(spki).map { it.toInt() and 0xff }
            val bity = (h[0] shl 22) or (h[1] shl 14) or (h[2] shl 6) or (h[3] shr 2)
            val alfabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val kod = (5 downTo 0).map { i -> alfabet[(bity shr (i * 5)) and 31] }.joinToString("")
            return kod.take(3) + "-" + kod.takeLast(3)
        }

        private fun emulator(): Boolean =
            Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") ||
                Build.PRODUCT.startsWith("sdk") || Build.FINGERPRINT.startsWith("generic") ||
                Build.MODEL.contains("Android SDK built for")

        private fun bajty(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
    }
}

/** Najprostsze kodowanie DER (wniosek PKCS#10). */
private object DER {
    private fun dlugosc(n: Int): ByteArray {
        if (n < 0x80) return byteArrayOf(n.toByte())
        val b = ByteArrayOutputStream()
        var x = n
        val bajty = mutableListOf<Byte>()
        while (x > 0) {
            bajty.add(0, (x and 0xff).toByte())
            x = x shr 8
        }
        b.write(0x80 or bajty.size)
        bajty.forEach { b.write(it.toInt()) }
        return b.toByteArray()
    }

    fun tlv(tag: Int, tresc: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + dlugosc(tresc.size) + tresc
    fun sekwencja(vararg elementy: ByteArray) = tlv(0x30, elementy.fold(ByteArray(0)) { a, b -> a + b })
    fun zbior(vararg elementy: ByteArray) = tlv(0x31, elementy.fold(ByteArray(0)) { a, b -> a + b })
    fun oid(bajty: ByteArray) = tlv(0x06, bajty)
    fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))
    fun drukowalny(s: String) = tlv(0x13, s.toByteArray(Charsets.US_ASCII))
    fun bity(b: ByteArray) = tlv(0x03, byteArrayOf(0) + b)
}
