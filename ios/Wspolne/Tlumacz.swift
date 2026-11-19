import SwiftUI
import WebKit
import NaturalLanguage
import Translation

/// Fragment tekstu strony do przetłumaczenia: id węzła tekstowego w stronie i jego treść.
struct FragmentTekstu: Sendable {
    let id: String
    let tekst: String
}

/// Tłumacz stron: Apple Translation tłumaczy w telefonie, bez wysyłania tekstu do nikogo.
/// Skrypt w stronie działa w osobnym świecie JavaScriptu (strona go nie widzi) i podmienia tylko węzły tekstowe:
/// pola formularzy, hasła, kod i edytory zostają nietknięte. „Pokaż oryginał” przywraca tekst.
@MainActor
enum Tlumacz {
    static let skrypt = #"""
    (() => {
      if (window.__liveplTlumacz) return;
      const POMIN = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'CODE', 'PRE', 'TEXTAREA', 'INPUT', 'SELECT', 'OPTION',
        'SVG', 'MATH', 'KBD', 'SAMP', 'VAR', 'TEMPLATE', 'IFRAME', 'CANVAS']);
      const oryginaly = new Map();
      const wezly = new Map();
      let zebrane = new WeakSet();
      let licznik = 0, aktywny = false, obserwator = null, zegar = null;

      function pomijany(el) {
        for (let e = el; e && e !== document.documentElement; e = e.parentElement) {
          if (POMIN.has(e.tagName) || e.isContentEditable) return true;
          if (e.getAttribute('translate') === 'no' || e.classList.contains('notranslate')) return true;
        }
        return false;
      }

      function zbierz(korzen, limit) {
        const wynik = [];
        if (!korzen) return wynik;
        const chodz = document.createTreeWalker(korzen, NodeFilter.SHOW_TEXT, {
          acceptNode(n) {
            if (zebrane.has(n)) return NodeFilter.FILTER_REJECT;
            const t = n.nodeValue;
            if (!t || t.trim().length < 2 || !/\p{L}/u.test(t)) return NodeFilter.FILTER_REJECT;
            if (!n.parentElement || pomijany(n.parentElement)) return NodeFilter.FILTER_REJECT;
            return NodeFilter.FILTER_ACCEPT;
          }
        });
        for (let n = chodz.nextNode(); n && wynik.length < limit; n = chodz.nextNode()) {
          zebrane.add(n);
          const id = 't' + (++licznik);
          wezly.set(id, n);
          wynik.push({ id, tekst: n.nodeValue.trim() });
        }
        return wynik;
      }

      function obserwuj() {
        if (obserwator || !document.body) return;
        obserwator = new MutationObserver(() => {
          if (!aktywny) return;
          clearTimeout(zegar);
          zegar = setTimeout(() => {
            const nowe = zbierz(document.body, 400);
            if (nowe.length) window.webkit.messageHandlers.liveplTlumacz.postMessage(nowe);
          }, 700);
        });
        obserwator.observe(document.body, { childList: true, subtree: true, characterData: true });
      }

      window.__liveplTlumacz = {
        probka() {
          const tekst = document.body ? document.body.innerText.slice(0, 4000) : '';
          return { lang: (document.documentElement.lang || '').toLowerCase(), tekst };
        },
        zbierz() {
          aktywny = true;
          obserwuj();
          return zbierz(document.body, 4000);
        },
        wstaw(pary) {
          for (const [id, t] of pary) {
            const n = wezly.get(id);
            if (!n) continue;
            wezly.delete(id);
            const o = n.nodeValue;
            if (!oryginaly.has(n)) oryginaly.set(n, o);
            n.nodeValue = o.match(/^\s*/)[0] + t + o.match(/\s*$/)[0];
          }
        },
        przywroc() {
          aktywny = false;
          if (obserwator) { obserwator.disconnect(); obserwator = null; }
          for (const [n, o] of oryginaly) n.nodeValue = o;
          oryginaly.clear();
          wezly.clear();
          zebrane = new WeakSet();
        }
      };
    })();
    """#

    static func fragmenty(_ wynik: Any?) -> [FragmentTekstu] {
        guard let lista = wynik as? [[String: Any]] else { return [] }
        return lista.compactMap { d in
            guard let id = d["id"] as? String, let tekst = d["tekst"] as? String else { return nil }
            return FragmentTekstu(id: id, tekst: tekst)
        }
    }

    /// Kod języka strony (np. „en”) albo nil, gdy strona jest po polsku albo nie da się tego ustalić.
    /// Przy dłuższym tekście decyduje rozpoznanie z treści, przy krótkim atrybut lang strony.
    static func jezyk(lang: String, tekst: String) -> String? {
        var kod: String?
        if tekst.count >= 200 {
            let rozpoznanie = NLLanguageRecognizer()
            rozpoznanie.processString(tekst)
            if let najlepszy = rozpoznanie.languageHypotheses(withMaximum: 1).first, najlepszy.value > 0.6 {
                kod = najlepszy.key.rawValue
            }
        }
        if kod == nil, let zLang = lang.split(separator: "-").first, !zLang.isEmpty {
            kod = String(zLang)
        }
        guard let kod, kod != "pl", kod != "und" else { return nil }
        return kod
    }

    static func nazwaJezyka(_ kod: String) -> String {
        Locale(identifier: "pl").localizedString(forIdentifier: kod) ?? kod
    }

    /// Języki, z których LivePL tłumaczy od razu, bez pytania.
    static var zawszeZ: Set<String> {
        get { Set(UserDefaults.standard.stringArray(forKey: "livepl-tlumacz-zawsze") ?? []) }
        set { UserDefaults.standard.set(Array(newValue).sorted(), forKey: "livepl-tlumacz-zawsze") }
    }

    /// Strony (hosty), na których LivePL nie proponuje tłumaczenia.
    static var nigdyNa: Set<String> {
        get { Set(UserDefaults.standard.stringArray(forKey: "livepl-tlumacz-nigdy") ?? []) }
        set { UserDefaults.standard.set(Array(newValue).sorted(), forKey: "livepl-tlumacz-nigdy") }
    }
}

/// Odbiera ze stron nowe fragmenty tekstu (treść dopisywaną po przetłumaczeniu) i przekazuje je właściwej karcie.
/// Jeden na wszystkie karty, bo okna otwierane przez stronę dzielą WKUserContentController z kartą, która je otworzyła.
@MainActor
final class PosrednikTlumacza: NSObject, WKScriptMessageHandler {
    static let wspolny = PosrednikTlumacza()

    private struct SlabaKarta { weak var karta: Karta? }
    private var karty: [ObjectIdentifier: SlabaKarta] = [:]
    private let kontrolery = NSHashTable<WKUserContentController>.weakObjects()

    func podlacz(_ karta: Karta) {
        karty = karty.filter { $0.value.karta != nil }
        karty[ObjectIdentifier(karta.webView)] = SlabaKarta(karta: karta)
        let kontroler = karta.webView.configuration.userContentController
        guard !kontrolery.contains(kontroler) else { return }
        kontrolery.add(kontroler)
        kontroler.addUserScript(WKUserScript(source: Tlumacz.skrypt, injectionTime: .atDocumentEnd,
                                             forMainFrameOnly: true, in: .defaultClient))
        kontroler.add(self, contentWorld: .defaultClient, name: "liveplTlumacz")
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let webView = message.webView, let karta = karty[ObjectIdentifier(webView)]?.karta else { return }
        karta.noweFragmenty(Tlumacz.fragmenty(message.body))
    }
}

/// Uruchamia sesję Apple Translation dla karty, gdy ta poprosi o tłumaczenie.
private struct TlumaczKarty: ViewModifier {
    @ObservedObject var karta: Karta
    @State private var konfiguracja: TranslationSession.Configuration?

    func body(content: Content) -> some View {
        content
            .translationTask(konfiguracja) { sesja in
                await karta.wykonajTlumaczenie(sesja)
            }
            .onChange(of: karta.prosbaOTlumaczenie) { _, _ in uruchom() }
            .onAppear {
                // po powrocie na kartę tłumacz dalej treść dopisywaną przez stronę
                if karta.tlumaczenieWlaczone { uruchom() }
            }
    }

    private func uruchom() {
        let zrodlo = karta.jezykStrony.map { Locale.Language(identifier: $0) }
        if let obecna = konfiguracja, obecna.source == zrodlo {
            konfiguracja?.invalidate()
        } else {
            konfiguracja = TranslationSession.Configuration(source: zrodlo, target: Locale.Language(identifier: "pl"))
        }
    }
}

extension View {
    func tlumaczenieStron(_ karta: Karta) -> some View {
        modifier(TlumaczKarty(karta: karta))
    }
}

/// Pasek pod adresem: propozycja tłumaczenia, postęp, „Pokaż oryginał” albo błąd.
struct PasekTlumaczenia: View {
    @ObservedObject var karta: Karta

    var body: some View {
        if let blad = karta.bladTlumaczenia {
            pasek(ikona: "exclamationmark.triangle", tekst: blad) {
                przyciskZamknij { karta.bladTlumaczenia = nil }
            }
        } else if karta.tlumaczy {
            pasek(ikona: "translate", tekst: "Tłumaczę na polski…") {
                ProgressView().controlSize(.small)
            }
        } else if karta.przetlumaczona {
            pasek(ikona: "translate", tekst: "Przetłumaczono na polski") {
                Button("Pokaż oryginał") { karta.pokazOryginal() }
                    .font(.subheadline.weight(.semibold))
            }
        } else if karta.pokazPropozycje, let kod = karta.jezykStrony {
            pasek(ikona: "translate", tekst: "Język strony: \(Tlumacz.nazwaJezyka(kod))") {
                Button("Przetłumacz") { karta.tlumacz() }
                    .font(.subheadline.weight(.semibold))
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                Menu {
                    Button { karta.zawszeTlumacz() } label: {
                        Label("Zawsze tłumacz: \(Tlumacz.nazwaJezyka(kod))", systemImage: "checkmark.circle")
                    }
                    Button { karta.nigdyNaTejStronie() } label: {
                        Label("Nigdy na \(Adres.host(karta.adres))", systemImage: "nosign")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .frame(width: 30, height: 30)
                        .contentShape(Rectangle())
                }
                .foregroundStyle(Kolor.slaby)
                .accessibilityLabel("Więcej opcji tłumaczenia")
                przyciskZamknij { karta.pokazPropozycje = false }
            }
        }
    }

    private func pasek<Przyciski: View>(ikona: String, tekst: String, @ViewBuilder przyciski: () -> Przyciski) -> some View {
        HStack(spacing: 8) {
            Image(systemName: ikona)
                .foregroundStyle(Kolor.czerwony)
            Text(tekst)
                .font(.subheadline)
                .foregroundStyle(Kolor.tekst)
                .lineLimit(2)
            Spacer(minLength: 4)
            przyciski()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(Kolor.karta)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Kolor.ramka).frame(height: 0.5)
        }
    }

    private func przyciskZamknij(_ akcja: @escaping () -> Void) -> some View {
        Button(action: akcja) {
            Image(systemName: "xmark")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Kolor.slaby)
                .frame(width: 30, height: 30)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Zamknij")
    }
}
