package eu.livepl.przegladarka

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/** Co widać na pasku tłumaczenia pod adresem strony. */
sealed class StanTlumaczenia {
    /** Strona jest w obcym języku: LivePL proponuje tłumaczenie. */
    data class Propozycja(val jezyk: String) : StanTlumaczenia()
    data object Sprawdzanie : StanTlumaczenia()
    data class PobieranieSlownika(val jezyk: String) : StanTlumaczenia()
    data class Trwa(val jezyk: String, val zrobione: Int, val wszystkie: Int) : StanTlumaczenia()
    data class Gotowe(val jezyk: String) : StanTlumaczenia()
    data class Blad(val opis: String) : StanTlumaczenia()
}

/** Tłumacz ML Kit: wszystko dzieje się w telefonie, tekst strony nigdzie nie wychodzi. */
object SilnikTlumaczenia {
    const val CEL = TranslateLanguage.POLISH

    private val rozpoznawanie by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.5f).build(),
        )
    }
    private val tlumacze = mutableMapOf<String, Translator>()

    fun tlumacz(zJezyka: String): Translator = tlumacze.getOrPut(zJezyka) {
        Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(zJezyka).setTargetLanguage(CEL).build(),
        )
    }

    /** Kod języka ML Kit dla „en”, „de-DE”, „pt-BR”… albo null, gdy tego języka nie umie. */
    fun obslugiwany(tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val t = tag.trim().replace('_', '-').lowercase()
        return TranslateLanguage.fromLanguageTag(t) ?: TranslateLanguage.fromLanguageTag(t.substringBefore('-'))
    }

    fun rozpoznaj(tekst: String, gotowe: (String?) -> Unit) {
        if (tekst.length < 20) return gotowe(null)
        rozpoznawanie.identifyLanguage(tekst)
            .addOnSuccessListener { gotowe(if (it == "und") null else it) }
            .addOnFailureListener { gotowe(null) }
    }

    fun maSlownik(zJezyka: String, gotowe: (Boolean) -> Unit) {
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { modele ->
                val kody = modele.map { it.language }
                gotowe(zJezyka in kody && CEL in kody)
            }
            .addOnFailureListener { gotowe(false) }
    }

    /** „angielski”, „niemiecki”… */
    fun nazwa(jezyk: String): String =
        Locale.forLanguageTag(jezyk).getDisplayLanguage(Locale.forLanguageTag("pl")).ifBlank { jezyk }
}

/**
 * Tłumaczenie strony w jednej karcie. Skrypt w stronie zbiera teksty (najpierw te widoczne),
 * LivePL tłumaczy je partiami i wstawia z powrotem. Nowe treści (np. lista w poczcie) tłumaczą się na bieżąco.
 */
class Tlumaczenie(private val karta: Karta, private val p: Przegladarka) {
    var stan by mutableStateOf<StanTlumaczenia?>(null)
        private set

    /** Rośnie przy każdej nowej stronie, żeby spóźnione wyniki nie trafiły na inną stronę. */
    private var pokolenie = 0
    private var zJezyka: String? = null
    private var pracuje = false
    private var zrobione = 0
    /** Język, który użytkownik już tłumaczył w tej karcie: kolejne strony w nim tłumaczą się same. */
    private var zawszeZ: String? = null

    val aktywne: Boolean
        get() = stan.let { it is StanTlumaczenia.Trwa || it is StanTlumaczenia.Gotowe || it is StanTlumaczenia.PobieranieSlownika }

    /** Nowa strona w karcie: poprzednie tłumaczenie przepada. */
    fun nowaStrona() {
        pokolenie++
        pracuje = false
        zrobione = 0
        zJezyka = null
        stan = null
    }

    /** Strona się wczytała: sprawdzamy jej język i proponujemy (albo od razu robimy) tłumaczenie. */
    fun stronaGotowa() {
        // Poczta LivePL nie ma paska tłumaczenia
        if (stan != null || p.dozwoloneHosty != null) return
        val nr = pokolenie
        probka { lang, tekst ->
            if (nr != pokolenie || stan != null) return@probka
            // propozycja tylko wtedy, gdy na stronie jest dość tekstu, żeby pewnie rozpoznać język
            if (tekst.length < 80) return@probka
            SilnikTlumaczenia.rozpoznaj(tekst) { wykryty ->
                if (nr != pokolenie || stan != null) return@rozpoznaj
                val jezyk = SilnikTlumaczenia.obslugiwany(wykryty) ?: return@rozpoznaj
                if (jezyk == SilnikTlumaczenia.CEL) return@rozpoznaj
                if (jezyk == zawszeZ) {
                    zacznij(jezyk)
                } else if (Adres.host(karta.adres) !in p.bezPropozycjiTlumaczenia) {
                    stan = StanTlumaczenia.Propozycja(jezyk)
                }
            }
        }
    }

    /** „Przetłumacz na polski” z menu albo z propozycji. */
    fun przetlumacz() {
        (stan as? StanTlumaczenia.Propozycja)?.let {
            zacznij(it.jezyk)
            return
        }
        val nr = pokolenie
        stan = StanTlumaczenia.Sprawdzanie
        probka { lang, tekst ->
            if (nr != pokolenie) return@probka
            SilnikTlumaczenia.rozpoznaj(tekst) { wykryty ->
                if (nr != pokolenie) return@rozpoznaj
                val jezyk = SilnikTlumaczenia.obslugiwany(wykryty) ?: SilnikTlumaczenia.obslugiwany(lang)
                when {
                    jezyk == null -> stan = StanTlumaczenia.Blad("Nie udało się rozpoznać języka tej strony.")
                    jezyk == SilnikTlumaczenia.CEL -> {
                        stan = null
                        p.komunikat("Ta strona już jest po polsku.")
                    }
                    else -> zacznij(jezyk)
                }
            }
        }
    }

    /** „Pokaż oryginał”: przywraca teksty strony i wyłącza tłumaczenie w tej karcie. */
    fun oryginal() {
        pokolenie++
        pracuje = false
        zawszeZ = null
        stan = null
        karta.webViewJesli?.evaluateJavascript("window.__livepl_t && window.__livepl_t.przywroc()", null)
    }

    /** Zamknięcie propozycji: na tej stronie (i całym serwisie, do końca uruchomienia) już nie pytamy. */
    fun odrzuc() {
        if (stan is StanTlumaczenia.Propozycja) p.bezPropozycjiTlumaczenia += Adres.host(karta.adres)
        if (aktywne) oryginal() else stan = null
    }

    /** Strona dodała nowe teksty (sygnał ze skryptu). */
    fun nowaTresc() {
        if (aktywne && !pracuje && zJezyka != null) {
            pracuje = true
            partia(pokolenie)
        }
    }

    fun zakoncz() {
        pokolenie++
        pracuje = false
    }

    // MARK: - Wnętrze

    private fun probka(gotowe: (String?, String) -> Unit) {
        val w = karta.webViewJesli ?: return
        w.evaluateJavascript(SKRYPT_PROBKI) { wynik ->
            val o = odczytaj(wynik) as? JSONObject ?: return@evaluateJavascript gotowe(null, "")
            gotowe(o.tekst("lang"), o.optString("probka"))
        }
    }

    private fun zacznij(jezyk: String) {
        val nr = pokolenie
        zJezyka = jezyk
        zawszeZ = jezyk
        zrobione = 0
        SilnikTlumaczenia.maSlownik(jezyk) { jest ->
            if (nr != pokolenie) return@maSlownik
            if (!jest) stan = StanTlumaczenia.PobieranieSlownika(jezyk)
            SilnikTlumaczenia.tlumacz(jezyk).downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    if (nr != pokolenie) return@addOnSuccessListener
                    stan = StanTlumaczenia.Trwa(jezyk, 0, 0)
                    karta.webViewJesli?.evaluateJavascript(SKRYPT_TLUMACZA) {
                        if (nr != pokolenie) return@evaluateJavascript
                        pracuje = true
                        partia(nr)
                    }
                }
                .addOnFailureListener {
                    if (nr != pokolenie) return@addOnFailureListener
                    stan = StanTlumaczenia.Blad("Nie udało się pobrać słownika. Sprawdź internet i spróbuj jeszcze raz.")
                }
        }
    }

    /** Bierze ze strony kolejną partię tekstów, tłumaczy i wstawia z powrotem. */
    private fun partia(nr: Int) {
        val w = karta.webViewJesli
        val jezyk = zJezyka
        if (w == null || jezyk == null || nr != pokolenie) return
        w.evaluateJavascript("window.__livepl_t ? window.__livepl_t.nastepne(40) : null") { wynik ->
            if (nr != pokolenie) return@evaluateJavascript
            val o = odczytaj(wynik) as? JSONObject
            val lista = o?.optJSONArray("l")
            if (o == null || lista == null || lista.length() == 0) {
                pracuje = false
                stan = StanTlumaczenia.Gotowe(jezyk)
                return@evaluateJavascript
            }
            val tlumacz = SilnikTlumaczenia.tlumacz(jezyk)
            val zadania = (0 until lista.length()).map { i ->
                val para = lista.getJSONArray(i)
                val tekst = para.getString(1)
                tlumacz.translate(tekst).continueWith { t -> if (t.isSuccessful) t.result else tekst }
            }
            Tasks.whenAllComplete(zadania).addOnCompleteListener {
                if (nr != pokolenie) return@addOnCompleteListener
                val gotowe = JSONArray()
                zadania.forEachIndexed { i, z ->
                    gotowe.put(JSONArray().put(lista.getJSONArray(i).getInt(0)).put(z.result ?: lista.getJSONArray(i).getString(1)))
                }
                karta.webViewJesli?.evaluateJavascript("window.__livepl_t && window.__livepl_t.wstaw($gotowe)", null)
                zrobione += lista.length()
                stan = StanTlumaczenia.Trwa(jezyk, zrobione, zrobione + o.optInt("r"))
                partia(nr)
            }
        }
    }

    /** Wynik evaluateJavascript to JSON; skrypty zwracają napis z JSON-em w środku. */
    private fun odczytaj(wynik: String?): Any? = try {
        val v = JSONTokener(wynik ?: "null").nextValue()
        if (v is String) JSONTokener(v).nextValue() else v
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Język z <html lang> i próbka tekstu do rozpoznania języka. */
        private const val SKRYPT_PROBKI = """
            (function () {
              var b = document.body;
              return JSON.stringify({
                lang: document.documentElement.lang || '',
                probka: b ? (b.innerText || '').replace(/\s+/g, ' ').slice(0, 2000) : ''
              });
            })();
        """

        /**
         * Zbiera teksty strony (bez skryptów, kodu, pól edycji i miejsc z translate="no"), najpierw te na ekranie.
         * Zapamiętuje oryginały, żeby „Pokaż oryginał” przywrócił stronę bez wczytywania jej od nowa.
         */
        private const val SKRYPT_TLUMACZA = """
            (function () {
              if (window.__livepl_t) return JSON.stringify({ jest: 1 });
              var POMIN = /^(SCRIPT|STYLE|NOSCRIPT|CODE|PRE|TEXTAREA|SVG|MATH|KBD|SAMP|VAR|TEMPLATE|IFRAME|CANVAS)$/;
              var ATRYBUTY = ['placeholder', 'title', 'alt', 'aria-label'];
              var LITERA = /\p{L}/u, MAKS = 5000;
              var elementy = [], kolejka = [], indeks = new WeakMap(), atrybuty = new WeakMap(), aktywne = true, sygnal = 0;

              function wlasny(el) {
                return POMIN.test(el.tagName) || el.getAttribute('contenteditable') === 'true' ||
                  el.getAttribute('translate') === 'no' || (el.classList && el.classList.contains('notranslate'));
              }
              function pominiety(el) {
                for (; el; el = el.parentElement) if (wlasny(el)) return true;
                return false;
              }
              function dodaj(w, a, tekst) {
                if (elementy.length >= MAKS || !tekst || tekst.length > 4000 || !LITERA.test(tekst)) return -1;
                elementy.push({ w: w, a: a, o: tekst, t: null });
                kolejka.push(elementy.length - 1);
                return elementy.length - 1;
              }
              function atrybut(el, a, v) {
                var z = atrybuty.get(el) || {};
                if (z[a] !== undefined) return;
                z[a] = dodaj(el, a, v);
                atrybuty.set(el, z);
              }
              function zbierz(korzen) {
                var it = document.createTreeWalker(korzen, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, {
                  acceptNode: function (w) { return w.nodeType === 1 && wlasny(w) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT; }
                });
                var w = korzen;
                do {
                  if (w.nodeType === 3) {
                    if (!indeks.has(w) && w.nodeValue.trim()) indeks.set(w, dodaj(w, null, w.nodeValue));
                  } else if (w.nodeType === 1) {
                    for (var i = 0; i < ATRYBUTY.length; i++) {
                      var v = w.getAttribute(ATRYBUTY[i]);
                      if (v && v.trim()) atrybut(w, ATRYBUTY[i], v);
                    }
                    if (w.tagName === 'INPUT' && /^(submit|button|reset)$/i.test(w.type) && w.value) atrybut(w, 'value', w.value);
                  }
                } while ((w = it.nextNode()));
              }
              function polozenie(i) {
                var e = elementy[i], el = e.a ? e.w : e.w.parentElement;
                if (!el || !el.getBoundingClientRect) return 1e9;
                var r = el.getBoundingClientRect();
                var widoczny = r.bottom > 0 && r.top < innerHeight;
                return (widoczny ? 0 : 1e6) + Math.abs(r.top);
              }
              function uloz() {
                var p = {};
                kolejka.forEach(function (i) { p[i] = polozenie(i); });
                kolejka.sort(function (a, b) { return p[a] - p[b]; });
              }
              function zTlumaczeniem(e) {
                var m = /^(\s*)[\s\S]*?(\s*)$/.exec(e.o);
                return m[1] + e.t + m[2];
              }
              function powiadom() {
                clearTimeout(sygnal);
                sygnal = setTimeout(function () { try { LivePLMost.postMessage('tlumacz'); } catch (_) {} }, 300);
              }

              var obserwator = new MutationObserver(function (zmiany) {
                if (!aktywne) return;
                var bylo = kolejka.length;
                zmiany.forEach(function (z) {
                  if (z.type === 'childList') {
                    z.addedNodes.forEach(function (n) {
                      if ((n.nodeType === 1 || n.nodeType === 3) && !pominiety(n.nodeType === 1 ? n : n.parentElement)) zbierz(n);
                    });
                  } else if (z.type === 'characterData') {
                    var i = indeks.get(z.target);
                    if (i === undefined || i < 0) return;
                    var e = elementy[i];
                    if (e.t !== null && z.target.nodeValue === zTlumaczeniem(e)) return;
                    e.o = z.target.nodeValue;
                    e.t = null;
                    kolejka.push(i);
                  }
                });
                if (kolejka.length > bylo) powiadom();
              });

              window.__livepl_t = {
                nastepne: function (ile) {
                  var l = [], znaki = 0;
                  while (kolejka.length && l.length < ile && znaki < 6000) {
                    var i = kolejka.shift(), e = elementy[i];
                    if (!e.w.isConnected) continue;
                    var t = e.o.trim();
                    znaki += t.length;
                    l.push([i, t]);
                  }
                  return JSON.stringify({ l: l, r: kolejka.length });
                },
                wstaw: function (lista) {
                  lista.forEach(function (p) {
                    var e = elementy[p[0]];
                    if (!e || !e.w.isConnected) return;
                    e.t = p[1];
                    if (!e.a) e.w.nodeValue = zTlumaczeniem(e);
                    else if (e.a === 'value') e.w.value = e.t;
                    else e.w.setAttribute(e.a, e.t);
                  });
                },
                przywroc: function () {
                  aktywne = false;
                  obserwator.disconnect();
                  elementy.forEach(function (e) {
                    if (e.t === null || !e.w.isConnected) return;
                    if (!e.a) e.w.nodeValue = e.o;
                    else if (e.a === 'value') e.w.value = e.o;
                    else e.w.setAttribute(e.a, e.o);
                  });
                  delete window.__livepl_t;
                }
              };

              if (document.body) {
                zbierz(document.body);
                obserwator.observe(document.body, { childList: true, subtree: true, characterData: true });
              }
              var tytul = document.querySelector('title');
              if (tytul) zbierz(tytul);
              uloz();
              return JSON.stringify({ ile: kolejka.length });
            })();
        """
    }
}
