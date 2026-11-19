package eu.livepl.przegladarka

/**
 * LivePL Connect: zdalny pulpit Live Production (connect.livepl.eu) na cały ekran, jak na iPhonie.
 * Cała obsługa jest w stronie (WebRTC z kanałami danych, bez aparatu i mikrofonu). Aplikacja dokłada:
 * ekran nie gaśnie w trakcie sesji (navigator.wakeLock), wibrację przy przytrzymaniu (prawy klik), pełny ekran,
 * a przytrzymanie i przeciąganie zostawia stronie (bez zaznaczania tekstu i bez odświeżania przeciągnięciem).
 * Systemowy Wstecz cofa stronę, więc kończy sesję (strona używa history.pushState).
 */
class ConnectActivity : JednaStronaActivity() {
    /** Góra strony w barwach marki (#0d0f12), w dzień i w nocy. */
    override val kolorGory = 0xFF0D0F12L
    override val dozwoloneHosty = setOf("connect.livepl.eu")
    override val adres = "https://connect.livepl.eu/"
    override val odswiezanie = false
    override val dlugieDotkniecie = false
    override val mostEkranu = true
}
