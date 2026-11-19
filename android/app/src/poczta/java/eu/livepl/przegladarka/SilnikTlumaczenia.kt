package eu.livepl.przegladarka

/**
 * Poczta LivePL nie tłumaczy stron (poczta jest po polsku), więc nie ma bibliotek ML Kit:
 * aplikacja jest przez to ok. 14 MB mniejsza. Tłumaczenie jest w przeglądarce LivePL.
 */
object SilnikTlumaczenia : Tlumacz {
    override fun obslugiwany(tag: String?): String? = null

    override fun rozpoznaj(tekst: String, gotowe: (String?) -> Unit) = gotowe(null)

    override fun maSlownik(zJezyka: String, gotowe: (Boolean) -> Unit) = gotowe(false)

    override fun pobierzSlownik(zJezyka: String, gotowe: () -> Unit, blad: () -> Unit) = blad()

    override fun przetlumacz(zJezyka: String, teksty: List<String>, gotowe: (List<String>) -> Unit) = gotowe(teksty)
}
