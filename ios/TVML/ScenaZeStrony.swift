import SwiftUI
import WebKit

/// Scena pogody prosto ze strony tvml.pl (Karol 25.11: „Tak, róbcie”): ten sam kod co karta na stronie,
/// więc każda zmiana sceny na tvml.pl jest od razu w aplikacji, bez nowej wersji i bez osobnej kopii na iOS.
/// Strona /app/scena (robi ją TVML Connect) rysuje samo płótno weather-effects, bez napisów, i po pierwszej
/// klatce wysyła wiadomość „tvml” z gotowe: true. Do tego czasu, bez internetu albo przy błędzie widać
/// wbudowaną scenę aplikacji (ScenaPogody) jako zapas.
enum AdresSceny {
    static let baza = "https://tvml.pl/app/scena"

    /// tryb: „karta” (Start, prawie kwadrat) albo „zakladka” (nagłówek zakładki Pogoda).
    static func url(tryb: String, bezRuchu: Bool, podglad: String?) -> URL? {
        var c = URLComponents(string: baza)
        var q = [URLQueryItem(name: "tryb", value: tryb)]
        if bezRuchu { q.append(URLQueryItem(name: "ruch", value: "0")) }
        // podgląd szkicu z panelu (kod QR); zrzut ekranu w CI ma umowny token bez szkicu na serwerze
        if let podglad, podglad != Podglad.tokenZrzutu { q.append(URLQueryItem(name: "podglad", value: podglad)) }
        c?.queryItems = q
        return c?.url
    }
}

/// Wbudowana scena pod spodem i scena ze strony na wierzchu, gdy jest gotowa. Wbudowana stoi wtedy w miejscu
/// (jedna klatka), żeby nie zużywać baterii na dwie animacje naraz.
/// Po powrocie do aplikacji strona wczytuje się od nowa, gdy sceny ze strony jeszcze nie było (np. bez internetu
/// albo zanim TVML Connect wgrał stronę) albo minęło ponad 30 minut (nowy etap motywu, nowa pogoda).
struct ScenaZZapasem<Zapas: View>: View {
    let adres: URL?
    @ViewBuilder var zapas: (_ stoi: Bool) -> Zapas
    @Environment(\.scenePhase) private var faza
    @State private var gotowa = false
    @State private var wersja = 0
    @State private var wczytano = Date()

    var body: some View {
        ZStack {
            zapas(gotowa)
            if let adres {
                ScenaZeStrony(adres: adres, wersja: wersja, gotowa: $gotowa)
                    .opacity(gotowa ? 1 : 0)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .onChange(of: faza) { _, nowa in
            guard nowa == .active, adres != nil else { return }
            if !gotowa || Date().timeIntervalSince(wczytano) > 30 * 60 {
                wczytano = Date()
                wersja += 1
            }
        }
    }
}

struct ScenaZeStrony: UIViewRepresentable {
    let adres: URL
    /// zmiana wczytuje stronę od nowa
    var wersja = 0
    @Binding var gotowa: Bool

    func makeCoordinator() -> Koordynator { Koordynator(gotowa: $gotowa) }

    func makeUIView(context: Context) -> WKWebView {
        let k = WKWebViewConfiguration()
        // trwała pamięć przeglądarki: skrypty sceny z ?v= są w telefonie także po ponownym uruchomieniu
        k.websiteDataStore = .default()
        k.mediaTypesRequiringUserActionForPlayback = .all
        k.applicationNameForUserAgent = Siec.agent
        k.userContentController.add(Posrednik(context.coordinator), name: "tvml")
        let w = WKWebView(frame: .zero, configuration: k)
        w.isOpaque = false
        w.backgroundColor = .clear
        w.scrollView.backgroundColor = .clear
        w.scrollView.isScrollEnabled = false
        w.scrollView.contentInsetAdjustmentBehavior = .never
        w.isUserInteractionEnabled = false
        w.navigationDelegate = context.coordinator
        context.coordinator.wersja = wersja
        context.coordinator.wczytaj(w, adres)
        return w
    }

    func updateUIView(_ w: WKWebView, context: Context) {
        context.coordinator.gotowa = $gotowa
        if context.coordinator.adres != adres || context.coordinator.wersja != wersja {
            context.coordinator.wersja = wersja
            context.coordinator.wczytaj(w, adres)
        }
    }

    static func dismantleUIView(_ w: WKWebView, coordinator: Koordynator) {
        w.stopLoading()
        w.configuration.userContentController.removeScriptMessageHandler(forName: "tvml")
    }

    @MainActor final class Koordynator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var gotowa: Binding<Bool>
        var adres: URL?
        var wersja = 0

        init(gotowa: Binding<Bool>) { self.gotowa = gotowa }

        func wczytaj(_ w: WKWebView, _ a: URL) {
            adres = a
            ustaw(false)
            w.load(URLRequest(url: a, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 15))
        }

        private func ustaw(_ wartosc: Bool) {
            DispatchQueue.main.async {
                guard self.gotowa.wrappedValue != wartosc else { return }
                withAnimation(.easeOut(duration: 0.35)) { self.gotowa.wrappedValue = wartosc }
            }
        }

        func userContentController(_ u: WKUserContentController, didReceive m: WKScriptMessage) {
            guard let d = m.body as? [String: Any], (d["gotowe"] as? Bool) == true else { return }
            ustaw(true)
        }

        func webView(_ w: WKWebView, didFail n: WKNavigation!, withError e: Error) { ustaw(false) }
        func webView(_ w: WKWebView, didFailProvisionalNavigation n: WKNavigation!, withError e: Error) { ustaw(false) }

        /// Strona bez sceny (np. 404, zanim TVML Connect ją wgra) nie wyśle „gotowe”, więc zostaje zapas.
        func webView(_ w: WKWebView, decidePolicyFor r: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
            if let http = r.response as? HTTPURLResponse, http.statusCode != 200, r.isForMainFrame {
                ustaw(false)
                decisionHandler(.cancel)
                return
            }
            decisionHandler(.allow)
        }

        /// Tylko strona sceny na tvml.pl; linki i ramki z innych adresów się nie otwierają.
        func webView(_ w: WKWebView, decidePolicyFor a: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            let host = a.request.url?.host ?? ""
            let tvml = host == "tvml.pl" || host.hasSuffix(".tvml.pl")
            decisionHandler(tvml && a.navigationType != .linkActivated ? .allow : .cancel)
        }

        /// iOS zamknął proces strony (np. przy braku pamięci): zapas, a potem ponowne wczytanie.
        func webViewWebContentProcessDidTerminate(_ w: WKWebView) {
            ustaw(false)
            if let adres { w.load(URLRequest(url: adres)) }
        }
    }

    /// Słaby pośrednik: WKUserContentController trzyma obsługę wiadomości mocno, a ona trzyma widok.
    @MainActor final class Posrednik: NSObject, WKScriptMessageHandler {
        weak var cel: WKScriptMessageHandler?
        init(_ cel: WKScriptMessageHandler) { self.cel = cel }
        func userContentController(_ u: WKUserContentController, didReceive m: WKScriptMessage) {
            cel?.userContentController(u, didReceive: m)
        }
    }
}
