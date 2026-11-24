package pl.tvml

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.PathParser
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.Metadata
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4OrientationData
import androidx.media3.container.Mp4TimestampData
import androidx.media3.effect.Presentation
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.DefaultMuxer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Alert TVML: zdjęcie, film i położenie do zgłoszenia. Pliki są mniejsze i bez metadanych (miejsce, telefon),
// zanim cokolwiek wyjdzie z telefonu, tak jak PlikiAlertu i Lokalizator w AlertWidok.swift na iPhonie.

/** Załącznik zgłoszenia: plik tymczasowy w cache/alert, już przygotowany do wysyłki. */
class ZalacznikAlertu(
    val rodzaj: Rodzaj,
    val plik: File,
    val mime: String,
    val rozmiar: Long,
    val miniatura: ImageBitmap?,
    val sekundy: Int?,
) {
    enum class Rodzaj { Zdjecie, Film }

    val nazwaPliku: String get() = if (rodzaj == Rodzaj.Film) "zgloszenie.mp4" else "zgloszenie.jpg"

    /** „Zdjęcie · 1,2 MB” albo „Film · 0:24 · 8,4 MB”. */
    val opis: String
        get() {
            val r = rozmiarTekst(rozmiar)
            return when (rodzaj) {
                Rodzaj.Zdjecie -> tl("app.alertPhoto", "Zdjęcie") + " · " + r
                Rodzaj.Film -> {
                    val s = sekundy ?: 0
                    tl("app.alertVideo", "Film") + " · " + "%d:%02d".format(s / 60, s % 60) + " · " + r
                }
            }
        }

    companion object {
        /** Jak ByteCountFormatter na iPhonie: jednostki dziesiętne, liczba w formacie wybranego języka. */
        fun rozmiarTekst(bajty: Long): String =
            if (bajty < 1_000_000) "${max(1, (bajty / 1000.0).roundToInt())} kB"
            else String.format(Jezyk.locale, "%.1f MB", bajty / 1_000_000.0)
    }
}

/** Błąd przygotowania pliku z komunikatem dla czytelnika (te same co na iPhonie). */
sealed class BladZalacznika : Exception() {
    object Format : BladZalacznika()
    class ZaDuzy(val mb: Int) : BladZalacznika()
    class ZaDlugi(val sekundy: Int) : BladZalacznika()
    object Eksport : BladZalacznika()

    val komunikat: String
        get() = when (this) {
            Format -> tl("app.alertFileFormat", "Tego pliku nie da się dołączyć. Wybierz zdjęcie albo film.")
            is ZaDuzy -> tl("app.alertFileTooBig", "Plik jest za duży (maks. {n} MB).", mb)
            is ZaDlugi -> tl("app.alertVideoTooLong", "Film jest za długi. Wybierz krótszy niż {n} s.", sekundy)
            Eksport -> tl("app.alertVideoFailed", "Nie udało się przygotować filmu. Spróbuj z innym.")
        }
}

/** Zdjęcia i filmy przed wysyłką: mniejsze i bez metadanych (miejsce, urządzenie). */
object PlikiAlertu {
    /** FileProvider z manifestu (res/xml/pliki.xml): aparat zapisuje zdjęcie i film w cache/alert. */
    private fun dostawca(k: Context) = "${k.packageName}.pliki"

    fun katalog(k: Context): File = File(k.cacheDir, "alert").apply { mkdirs() }

    fun tymczasowy(k: Context, rozszerzenie: String): File = File(katalog(k), "alert-${UUID.randomUUID()}.$rozszerzenie")

    /**
     * Pusty plik dla aparatu (TakePicture / CaptureVideo) i jego adres content:// z FileProvider. Aplikacja nie ma
     * uprawnienia CAMERA: zdjęcie robi aplikacja aparatu, która dostaje tylko prawo zapisu do tego jednego pliku.
     */
    fun dlaAparatu(k: Context, rozszerzenie: String): Pair<File, Uri> {
        val plik = File(katalog(k), "aparat-${UUID.randomUUID()}.$rozszerzenie")
        return plik to FileProvider.getUriForFile(k, dostawca(k), plik)
    }

    /** Nowy formularz albo zamknięcie: pliki tymczasowe precz (także po aplikacji zamkniętej w trakcie). */
    fun sprzatajWszystko(k: Context) {
        File(k.cacheDir, "alert").listFiles()?.forEach { it.delete() }
    }

    fun aparatDostepny(k: Context): Boolean = k.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    // ---------- zdjęcie ----------

    /** Zdjęcie z galerii albo aparatu: obrót z EXIF, dłuższy bok 2048 px, JPEG 80 bez EXIF; za duże: 1600 px, JPEG 65. */
    suspend fun zdjecie(k: Context, zrodlo: Uri, maksMB: Int): ZalacznikAlertu = withContext(Dispatchers.IO) {
        val obraz = dekoduj(k, zrodlo, 2048) ?: throw BladZalacznika.Format
        try {
            zdjecie(k, obraz, maksMB)
        } finally {
            obraz.recycle()
        }
    }

    fun zdjecie(k: Context, obraz: Bitmap, maksMB: Int): ZalacznikAlertu {
        val limit = maksMB * 1024L * 1024L
        var dane = jpeg(obraz, 2048, 80)
        if (dane.size > limit) dane = jpeg(obraz, 1600, 65)
        if (dane.size > limit) throw BladZalacznika.ZaDuzy(maksMB)
        val plik = tymczasowy(k, "jpg")
        plik.writeBytes(dane)
        val miniatura = przeskaluj(obraz, 240).asImageBitmap()
        return ZalacznikAlertu(ZalacznikAlertu.Rodzaj.Zdjecie, plik, "image/jpeg", dane.size.toLong(), miniatura, null)
    }

    /** JPEG narysowany od nowa (Bitmap.compress nie zapisuje EXIF, więc ani GPS, ani modelu telefonu). */
    private fun jpeg(obraz: Bitmap, dluzszyBok: Int, jakosc: Int): ByteArray {
        val maly = przeskaluj(obraz, dluzszyBok)
        return ByteArrayOutputStream().use { wyj ->
            maly.compress(Bitmap.CompressFormat.JPEG, jakosc, wyj)
            if (maly !== obraz) maly.recycle()
            wyj.toByteArray()
        }
    }

    /** Dłuższy bok najwyżej [bok] px, na białym tle (przezroczysty PNG w JPEG nie wychodzi czarny). */
    private fun przeskaluj(obraz: Bitmap, bok: Int): Bitmap {
        val skala = min(1f, bok.toFloat() / max(obraz.width, obraz.height))
        val w = max(1, (obraz.width * skala).roundToInt())
        val h = max(1, (obraz.height * skala).roundToInt())
        val wynik = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(wynik).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(obraz, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        return wynik
    }

    /** Potęga dwójki, przy której dłuższy bok zostaje co najmniej [maks] px (mniej pamięci niż pełne zdjęcie z aparatu). */
    private fun probka(bok: Int, maks: Int): Int {
        var s = 1
        while (bok / (s * 2) >= maks) s *= 2
        return s
    }

    /** Dekodowanie z obrotem z EXIF: od Androida 9 ImageDecoder (także HEIC), niżej BitmapFactory i ExifInterface. */
    private fun dekoduj(k: Context, zrodlo: Uri, maks: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(k.contentResolver, zrodlo)) { dekoder, info, _ ->
                dekoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                dekoder.setTargetSampleSize(probka(max(info.size.width, info.size.height), maks))
            }
        } else {
            val granice = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            k.contentResolver.openInputStream(zrodlo)?.use { BitmapFactory.decodeStream(it, null, granice) }
            val bok = max(granice.outWidth, granice.outHeight)
            if (bok <= 0) return@runCatching null
            val opcje = BitmapFactory.Options().apply { inSampleSize = probka(bok, maks) }
            val obraz = k.contentResolver.openInputStream(zrodlo)?.use { BitmapFactory.decodeStream(it, null, opcje) }
                ?: return@runCatching null
            val exif = runCatching { k.contentResolver.openInputStream(zrodlo)?.use { ExifInterface(it) } }.getOrNull()
            val obrot = exif?.rotationDegrees ?: 0
            val odbicie = exif?.isFlipped ?: false
            if (obrot == 0 && !odbicie) {
                obraz
            } else {
                // według ExifInterface: najpierw odbicie w poziomie, potem obrót
                val macierz = Matrix().apply {
                    if (odbicie) postScale(-1f, 1f)
                    postRotate(obrot.toFloat())
                }
                Bitmap.createBitmap(obraz, 0, 0, obraz.width, obraz.height, macierz, true).also { if (it !== obraz) obraz.recycle() }
            }
        }
    }.getOrNull()

    // ---------- film ----------

    /**
     * Film do MP4 720p (H.264/AAC) bez metadanych miejsca i telefonu; dłuższy niż limit = komunikat, bez przycinania.
     * Pierwsza próba: 720p z przepływnością dobraną do limitu MB; gdy plik i tak za duży albo koder zawiedzie,
     * druga próba w niższej jakości (480p), a potem komunikat.
     */
    suspend fun film(k: Context, zrodlo: Uri, maksMB: Int, maksSekund: Int): ZalacznikAlertu {
        class Dane(val ms: Long, val krotszyBok: Int)
        val dane = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(k, zrodlo)
                val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                Dane(ms, min(w, h))
            } catch (e: Exception) {
                null
            } finally {
                runCatching { r.release() }
            }
        } ?: throw BladZalacznika.Format
        if (dane.ms <= 0) throw BladZalacznika.Format
        if (dane.ms > maksSekund * 1000L + 500) throw BladZalacznika.ZaDlugi(maksSekund)

        val limit = maksMB * 1024L * 1024L
        val sekundy = max(1.0, dane.ms / 1000.0)
        // obraz + dźwięk AAC 128 kb/s, z zapasem 15% na kontener i wahania kodera
        val bity720 = ((limit * 8 * 0.85 / sekundy) - 128_000).toInt().coerceIn(400_000, 4_000_000)
        val proby = listOf(720 to bity720, 480 to (bity720 * 0.6).toInt().coerceAtLeast(250_000))
        for ((i, proba) in proby.withIndex()) {
            val cel = tymczasowy(k, "mp4")
            val gotowe = try {
                przekoduj(k, zrodlo, cel, proba.first, proba.second, dane.krotszyBok)
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    cel.delete()
                    throw e
                }
                if (BuildConfig.DEBUG) android.util.Log.w("TVML-Alert", "przekodowanie ${proba.first}p", e)
                false
            }
            val rozmiar = if (gotowe) cel.length() else 0L
            if (rozmiar in 1..limit) {
                // sprawdzenie jak forSharing() na iPhonie: w wyniku nie może być położenia
                if (maPolozenie(cel)) {
                    cel.delete()
                    throw BladZalacznika.Eksport
                }
                return ZalacznikAlertu(
                    ZalacznikAlertu.Rodzaj.Film, cel, "video/mp4", rozmiar, klatka(k, cel), sekundy.roundToInt(),
                )
            }
            cel.delete()
            if (rozmiar > limit && i == proby.lastIndex) throw BladZalacznika.ZaDuzy(maksMB)
        }
        throw BladZalacznika.Eksport
    }

    /** Transformer (media3) na wątku głównym, jak wymaga biblioteka; anulowanie przerywa przekodowanie. */
    @OptIn(UnstableApi::class)
    private suspend fun przekoduj(k: Context, zrodlo: Uri, cel: File, krotszyBok: Int, bity: Int, krotszyBokZrodla: Int) =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { c ->
                // 720p = krótszy bok 720 px także w filmie pionowym; mniejszego filmu nie powiększamy
                val efekty = if (krotszyBokZrodla in 1..krotszyBok) emptyList() else listOf(Presentation.createForShortSide(krotszyBok))
                val element = EditedMediaItem.Builder(MediaItem.fromUri(zrodlo))
                    .setEffects(Effects(emptyList(), efekty))
                    .build()
                val koder = DefaultEncoderFactory.Builder(k)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bity).build())
                    .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(128_000).build())
                    .setEnableFallback(true)
                    .build()
                val t = Transformer.Builder(k)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(koder)
                    .setMuxerFactory(FabrykaBezMetadanych(DefaultMuxer.Factory()))
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (c.isActive) c.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (c.isActive) c.resumeWithException(exportException)
                        }
                    })
                    .build()
                val glowny = Handler(Looper.getMainLooper())
                c.invokeOnCancellation { glowny.post { runCatching { t.cancel() } } }
                t.start(element, cel.absolutePath)
            }
        }

    /**
     * Transformer przepisuje do wyniku metadane z filmu (położenie ©xyz, XMP, klucze mdta z modelem telefonu).
     * Ten muxer przepuszcza tylko obrót (film pionowy) i czas nagrania.
     */
    @OptIn(UnstableApi::class)
    private class FabrykaBezMetadanych(private val fabryka: Muxer.Factory) : Muxer.Factory {
        override fun create(path: String): Muxer = MuxerBezMetadanych(fabryka.create(path))
        override fun getSupportedSampleMimeTypes(trackType: Int) = fabryka.getSupportedSampleMimeTypes(trackType)
        override fun supportsWritingNegativeTimestampsInEditList() = fabryka.supportsWritingNegativeTimestampsInEditList()
    }

    @OptIn(UnstableApi::class)
    private class MuxerBezMetadanych(private val muxer: Muxer) : Muxer by muxer {
        override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
            if (metadataEntry is Mp4OrientationData || metadataEntry is Mp4TimestampData) muxer.addMetadataEntry(metadataEntry)
        }
    }

    private fun maPolozenie(plik: File): Boolean {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(plik.absolutePath)
            !r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION).isNullOrBlank()
        } catch (e: Exception) {
            false
        } finally {
            runCatching { r.release() }
        }
    }

    /** Pierwsza klatka gotowego filmu jako miniatura 240 px. */
    private suspend fun klatka(k: Context, plik: File): ImageBitmap? = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(plik.absolutePath)
            val obraz = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@withContext null
            przeskaluj(obraz, 240).also { obraz.recycle() }.asImageBitmap()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    // ---------- przykład do zrzutów ----------

    /** Do zrzutów (--es alertPrzyklad): obrazek z autem w miejscu zdjęcia, bez sięgania do galerii emulatora. */
    fun przykladowy(k: Context): ZalacznikAlertu? = runCatching {
        val w = 480
        val h = 360
        val obraz = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(obraz)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF6B85A8.toInt(), 0xFF243047.toInt(), Shader.TileMode.CLAMP)
        })
        // ikona samochodu z Ikony (siatka 24) powiększona do 168 px, na środku
        val auto = PathParser.createPathFromPathData(Ikony.SAMOCHOD)
        auto.transform(Matrix().apply { setScale(7f, 7f); postTranslate((w - 168) / 2f, (h - 168) / 2f) })
        c.drawPath(auto, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        try {
            zdjecie(k, obraz, 10)
        } finally {
            obraz.recycle()
        }
    }.getOrNull()
}

// ---------- położenie ----------

data class PolozenieAlertu(val szerokosc: Double, val dlugosc: Double, val dokladnosc: Int)

/** Jedno położenie na prośbę (przycisk „Dodaj moje położenie”), bez śledzenia w tle i bez Usług Google Play. */
object Lokalizator {
    fun maZgode(k: Context): Boolean =
        ContextCompat.checkSelfPermission(k, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(k, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Bieżące położenie z najlepszego włączonego dostawcy (fused od Androida 12, GPS tylko przy zgodzie na dokładne,
     * sieć). Zgoda tylko na przybliżone też działa (wtedy większa dokładność ±). Gdy przez 15 s nic nie przyjdzie,
     * ostatnie znane z ostatnich 10 minut; null = nie udało się.
     */
    @SuppressLint("MissingPermission")
    suspend fun pobierz(k: Context): Location? {
        if (!maZgode(k)) return null
        val lm = ContextCompat.getSystemService(k, LocationManager::class.java) ?: return null
        if (!LocationManagerCompat.isLocationEnabled(lm)) return null
        val dokladne = ContextCompat.checkSelfPermission(k, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val dostawcy = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            if (dokladne) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val dostawca = dostawcy.firstOrNull() ?: return null
        val biezace = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { c ->
                val sygnal = CancellationSignal()
                c.invokeOnCancellation { runCatching { sygnal.cancel() } }
                try {
                    LocationManagerCompat.getCurrentLocation(lm, dostawca, sygnal, ContextCompat.getMainExecutor(k), Consumer<Location> { l ->
                        if (c.isActive) c.resume(l)
                    })
                } catch (e: Exception) {
                    if (c.isActive) c.resume(null)
                }
            }
        }
        if (biezace != null) return biezace
        val granica = System.currentTimeMillis() - 10 * 60_000
        return dostawcy.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.time >= granica }
            .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
    }

    /** Adres po polsku („Leszno, ul. Królowej Jadwigi 5”) do pustego pola Miejsce; null, gdy telefon go nie zna. */
    suspend fun adres(k: Context, p: PolozenieAlertu): String? {
        if (!Geocoder.isPresent()) return null
        val g = Geocoder(k, Locale.forLanguageTag("pl-PL"))
        val adresy: List<Address>? = withTimeoutOrNull<List<Address>?>(8_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<List<Address>?> { c ->
                    g.getFromLocation(p.szerokosc, p.dlugosc, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (c.isActive) c.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (c.isActive) c.resume(null)
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching<List<Address>?> { g.getFromLocation(p.szerokosc, p.dlugosc, 1) }.getOrNull()
                }
            }
        }
        val a = adresy?.firstOrNull() ?: return null
        val ulica = listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ")
        return listOf(a.locality.orEmpty(), ulica).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null }
    }
}
