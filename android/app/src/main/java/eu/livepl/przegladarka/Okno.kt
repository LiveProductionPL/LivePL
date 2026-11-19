package eu.livepl.przegladarka

import android.webkit.JsPromptResult
import android.webkit.JsResult

/** Okienko, o które prosi strona; czeka w kolejce, aż użytkownik odpowie. */
sealed class Okno(val karta: Karta?) {
    /** Odpowiedź, gdy karta znika, zanim użytkownik zdążył odpowiedzieć. */
    abstract fun anuluj()

    class Komunikat(karta: Karta, val tytul: String, val tresc: String, val wynik: JsResult) : Okno(karta) {
        override fun anuluj() = wynik.confirm()
    }

    class Potwierdzenie(
        karta: Karta, val tytul: String, val tresc: String, val wynik: JsResult, val tak: String = "OK",
    ) : Okno(karta) {
        override fun anuluj() = wynik.cancel()
    }

    class Pytanie(
        karta: Karta, val tytul: String, val tresc: String, val domyslnie: String?, val wynik: JsPromptResult,
    ) : Okno(karta) {
        override fun anuluj() = wynik.cancel()
    }

    /** Zgoda na aparat, mikrofon albo lokalizację; [dane] to prośba strony, żeby dało się ją wycofać. */
    class Zgoda(
        karta: Karta, val tytul: String, val tresc: String, val dane: Any?, val gotowe: (Boolean) -> Unit,
    ) : Okno(karta) {
        override fun anuluj() = gotowe(false)
    }
}
