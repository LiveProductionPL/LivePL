package eu.livepl.przegladarka

/**
 * Poczta LivePL: firmowa poczta (poczta.livepl.eu) jako osobna aplikacja, bez paska adresu, jak na iPhonie.
 * Załączniki do Pobranych, zdjęcia z aparatu do wiadomości, logowanie zapamiętane w telefonie (90 dni przedłuża
 * serwer poczty, gdy widzi „LivePL/” w identyfikatorze przeglądarki). Linki do innych stron otwiera przeglądarka.
 */
class PocztaActivity : JednaStronaActivity() {
    /** Granat nagłówka poczty. */
    override val kolorGory = 0xFF071C2DL
    override val dozwoloneHosty = setOf("poczta.livepl.eu")
    override val program = "poczta"
    override val licznikWTytule = true
}
