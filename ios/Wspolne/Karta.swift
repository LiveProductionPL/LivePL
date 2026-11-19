import SwiftUI
import WebKit
import Combine
import Translation

/// Błąd otwierania strony, pokazywany zamiast pustego ekranu.
struct BladStrony: Equatable {
    let opis: String
    let adres: URL?
}

/// Jedna karta: program z paska albo zwykła strona. Każda ma własny WKWebView,
/// a wszystkie dzielą zapamiętane logowania (ciasteczka) w tym telefonie.
@MainActor
final class Karta: NSObject, ObservableObject, Identifiable, WKNavigationDelegate, WKUIDelegate, WKDownloadDelegate {
    let id = UUID()
    let programID: String?
    let webView: WKWebView
    weak var przegladarka: Przegladarka?
    weak var otwierajaca: Karta?

    @Published var tytul = ""
    @Published private(set) var adres: URL?
    @Published private(set) var postep: Double = 0
    @Published private(set) var laduje = false
    @Published private(set) var mozeWstecz = false
    @Published private(set) var mozeDalej = false
    @Published private(set) var tylkoBezpieczne = true
    @Published private(set) var blad: BladStrony?

    // tłumaczenie strony (Tlumacz.swift)
    /// Kod języka strony, gdy nie jest po polsku (np. „en”).
    @Published private(set) var jezykStrony: String?
    @Published var pokazPropozycje = false
    @Published private(set) var tlumaczy = false
    @Published private(set) var przetlumaczona = false
    @Published var bladTlumaczenia: String?
    @Published private(set) var prosbaOTlumaczenie = 0
    private(set) var tlumaczenieWlaczone = false
    private var noweTeksty: AsyncStream<[FragmentTekstu]>.Continuation?

    /// Adres karty przywróconej po ponownym uruchomieniu, ładowany dopiero po wejściu na kartę.
    private(set) var odlozona: URL?
    private var pobierane: [WKDownload: URL] = [:]

    init(programID: String?, konfiguracja: WKWebViewConfiguration? = nil, przegladarka: Przegladarka) {
        self.programID = programID
        self.przegladarka = przegladarka
        // okno otwarte przez stronę dostaje konfigurację od WebKit (z tymi samymi skryptami co karta, która je otworzyła)
        let k: WKWebViewConfiguration
        if let konfiguracja {
            k = konfiguracja
        } else {
            k = Karta.nowaKonfiguracja()
            przegladarka.dostosujKonfiguracje?(k)
        }
        webView = WKWebView(frame: .zero, configuration: k)
        super.init()

        webView.navigationDelegate = self
        webView.uiDelegate = self
        PosrednikTlumacza.wspolny.podlacz(self)
        webView.allowsBackForwardNavigationGestures = true
        webView.allowsLinkPreview = true
        webView.isFindInteractionEnabled = true
        #if DEBUG
        webView.isInspectable = true
        #endif

        let odswiezanie = UIRefreshControl()
        odswiezanie.addTarget(self, action: #selector(odswiezPrzeciagnieciem), for: .valueChanged)
        webView.scrollView.refreshControl = odswiezanie

        webView.publisher(for: \.title).map { $0 ?? "" }.assign(to: &$tytul)
        webView.publisher(for: \.url).assign(to: &$adres)
        webView.publisher(for: \.estimatedProgress).assign(to: &$postep)
        webView.publisher(for: \.isLoading).assign(to: &$laduje)
        webView.publisher(for: \.canGoBack).assign(to: &$mozeWstecz)
        webView.publisher(for: \.canGoForward).assign(to: &$mozeDalej)
        webView.publisher(for: \.hasOnlySecureContent).assign(to: &$tylkoBezpieczne)
    }

    static func nowaKonfiguracja() -> WKWebViewConfiguration {
        let k = WKWebViewConfiguration()
        k.websiteDataStore = .default()
        k.allowsInlineMediaPlayback = true
        k.allowsAirPlayForMediaPlayback = true
        k.allowsPictureInPictureMediaPlayback = true
        // jak Safari na tym iPhonie, żeby strony nie dawały okrojonej wersji
        let ios = ProcessInfo.processInfo.operatingSystemVersion.majorVersion
        // „LivePL/…” na końcu: nasze strony wiedzą, że są w aplikacji LivePL (np. poczta nie proponuje dodania do ekranu)
        let wersja = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0"
        k.applicationNameForUserAgent = "Version/\(ios).0 Mobile/15E148 Safari/604.1 LivePL/\(wersja)"
        return k
    }

    var wyswietlanyAdres: URL? { adres ?? odlozona }

    /// Kłódka: https i bez niezabezpieczonych elementów na stronie.
    var bezpieczna: Bool {
        guard let url = wyswietlanyAdres else { return true }
        return url.scheme?.lowercased() == "https" && (odlozona != nil || tylkoBezpieczne)
    }

    func laduj(_ url: URL) {
        odlozona = nil
        blad = nil
        webView.load(URLRequest(url: url))
    }

    func odloz(_ url: URL, tytul: String) {
        odlozona = url
        self.tytul = tytul
    }

    func zaladujOdlozona() {
        if let url = odlozona { laduj(url) }
    }

    func sprobujPonownie() {
        if let url = blad?.adres ?? adres {
            laduj(url)
        } else {
            blad = nil
            webView.reload()
        }
    }

    func zakoncz() {
        webView.stopLoading()
        webView.navigationDelegate = nil
        webView.uiDelegate = nil
        webView.removeFromSuperview()
    }

    @objc private func odswiezPrzeciagnieciem() {
        if blad != nil { sprobujPonownie() } else { webView.reload() }
    }

    private func koniecOdswiezania() {
        webView.scrollView.refreshControl?.endRefreshing()
    }

    // MARK: - Tłumaczenie strony (Apple Translation, w telefonie)

    /// Po załadowaniu strony: jaki to język i czy zaproponować tłumaczenie.
    /// Programy z paska (Poczta, Urząd…) to nasze polskie strony, więc tam LivePL sam nie proponuje.
    private func wykryjJezyk() async {
        let wynik = try? await webView.callAsyncJavaScript(
            "return window.__liveplTlumacz ? window.__liveplTlumacz.probka() : null",
            contentWorld: .defaultClient)
        guard let probka = wynik as? [String: Any] else { return }
        guard let kod = Tlumacz.jezyk(lang: probka["lang"] as? String ?? "", tekst: probka["tekst"] as? String ?? "") else {
            jezykStrony = nil
            return
        }
        let stan = await LanguageAvailability().status(from: Locale.Language(identifier: kod), to: Locale.Language(identifier: "pl"))
        guard stan != .unsupported else {
            jezykStrony = nil
            return
        }
        jezykStrony = kod
        guard programID == nil, !tlumaczenieWlaczone else { return }
        if let host = adres?.host, Tlumacz.nigdyNa.contains(host) { return }
        if Tlumacz.zawszeZ.contains(kod) {
            tlumacz()
        } else {
            pokazPropozycje = true
        }
    }

    func tlumacz() {
        tlumaczenieWlaczone = true
        pokazPropozycje = false
        bladTlumaczenia = nil
        prosbaOTlumaczenie += 1
    }

    func zawszeTlumacz() {
        if let kod = jezykStrony { Tlumacz.zawszeZ.insert(kod) }
        tlumacz()
    }

    func nigdyNaTejStronie() {
        if let host = adres?.host { Tlumacz.nigdyNa.insert(host) }
        pokazPropozycje = false
    }

    func pokazOryginal() {
        zakonczTlumaczenie()
        Task {
            _ = try? await webView.callAsyncJavaScript(
                "if (window.__liveplTlumacz) window.__liveplTlumacz.przywroc(); return true;",
                contentWorld: .defaultClient)
        }
    }

    private func zakonczTlumaczenie() {
        tlumaczenieWlaczone = false
        przetlumaczona = false
        tlumaczy = false
        pokazPropozycje = false
        noweTeksty?.finish()
        noweTeksty = nil
    }

    /// Treść dopisana przez stronę po przetłumaczeniu (z PosrednikTlumacza).
    func noweFragmenty(_ fragmenty: [FragmentTekstu]) {
        guard tlumaczenieWlaczone, !fragmenty.isEmpty else { return }
        noweTeksty?.yield(fragmenty)
    }

    /// Wywołuje TlumaczKarty (translationTask): tłumaczy całą stronę, potem treść dopisywaną, dopóki karta jest widoczna.
    func wykonajTlumaczenie(_ sesja: TranslationSession) async {
        guard tlumaczenieWlaczone else { return }
        let (strumien, kontynuacja) = AsyncStream.makeStream(of: [FragmentTekstu].self)
        noweTeksty?.finish()
        noweTeksty = kontynuacja
        tlumaczy = true
        do {
            try await sesja.prepareTranslation()
            let wynik = try await webView.callAsyncJavaScript(
                "return window.__liveplTlumacz ? window.__liveplTlumacz.zbierz() : []",
                contentWorld: .defaultClient)
            try await przetlumacz(Tlumacz.fragmenty(wynik), sesja)
            tlumaczy = false
            przetlumaczona = tlumaczenieWlaczone
            for await nowe in strumien {
                try await przetlumacz(nowe, sesja)
            }
        } catch is CancellationError {
            tlumaczy = false
        } catch {
            tlumaczy = false
            if tlumaczenieWlaczone {
                zakonczTlumaczenie()
                bladTlumaczenia = "Nie udało się przetłumaczyć strony. \(error.localizedDescription)"
            }
        }
    }

    private func przetlumacz(_ fragmenty: [FragmentTekstu], _ sesja: TranslationSession) async throws {
        var od = 0
        while od < fragmenty.count {
            try Task.checkCancellation()
            guard tlumaczenieWlaczone else { return }
            let czesc = fragmenty[od..<min(od + 80, fragmenty.count)]
            od += 80
            let prosby = czesc.map { TranslationSession.Request(sourceText: $0.tekst, clientIdentifier: $0.id) }
            let odpowiedzi = try await sesja.translations(from: prosby)
            let pary: [[String]] = odpowiedzi.compactMap { odp in
                guard let id = odp.clientIdentifier else { return nil }
                return [id, odp.targetText]
            }
            guard tlumaczenieWlaczone else { return }
            _ = try? await webView.callAsyncJavaScript(
                "if (window.__liveplTlumacz) window.__liveplTlumacz.wstaw(pary); return true;",
                arguments: ["pary": pary],
                contentWorld: .defaultClient)
        }
    }

    // MARK: - WKNavigationDelegate

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
        guard let url = navigationAction.request.url else { return .allow }
        // mailto:, tel:, mapy, App Store… otwiera iOS
        if Przegladarka.obcyAdres(url) {
            _ = await UIApplication.shared.open(url)
            return .cancel
        }
        // Poczta LivePL: kliknięty link do innej strony otwiera się w przeglądarce, a nie w aplikacji poczty
        if let dozwolone = przegladarka?.dozwoloneHosty,
           navigationAction.navigationType == .linkActivated,
           navigationAction.targetFrame?.isMainFrame ?? true,
           let host = url.host?.lowercased(), !dozwolone.contains(host) {
            _ = await UIApplication.shared.open(url)
            return .cancel
        }
        return navigationAction.shouldPerformDownload ? .download : .allow
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse) async -> WKNavigationResponsePolicy {
        if navigationResponse.isForMainFrame && !navigationResponse.canShowMIMEType {
            return .download
        }
        return .allow
    }

    func webView(_ webView: WKWebView, navigationAction: WKNavigationAction, didBecome download: WKDownload) {
        download.delegate = self
    }

    func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) {
        download.delegate = self
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        blad = nil
        zakonczTlumaczenie()
        jezykStrony = nil
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        koniecOdswiezania()
        Task { await wykryjJezyk() }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        koniecOdswiezania()
        pokazBlad(error)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        koniecOdswiezania()
        pokazBlad(error)
    }

    /// Certyfikaty naszego urzędu (LivePL Root CA) są zaufane w LivePL dla naszych domen, bez instalowania w iPhonie.
    func webView(_ webView: WKWebView, respondTo challenge: URLAuthenticationChallenge) async -> (URLSession.AuthChallengeDisposition, URLCredential?) {
        await NaszeCA.odpowiedz(challenge)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        webView.reload()
    }

    private func pokazBlad(_ error: Error) {
        let e = error as NSError
        // przerwane celowo: kolejne kliknięcie, pobieranie pliku, link do innej aplikacji
        if e.domain == NSURLErrorDomain && e.code == NSURLErrorCancelled { return }
        if e.domain == "WebKitErrorDomain" && (e.code == 102 || e.code == 204) { return }

        let opis: String
        switch (e.domain, e.code) {
        case (NSURLErrorDomain, NSURLErrorNotConnectedToInternet), (NSURLErrorDomain, NSURLErrorNetworkConnectionLost):
            opis = "Brak połączenia z internetem. Sprawdź Wi-Fi albo dane komórkowe."
        case (NSURLErrorDomain, NSURLErrorCannotFindHost), (NSURLErrorDomain, NSURLErrorDNSLookupFailed):
            opis = "Nie ma takiej strony. Sprawdź, czy adres jest dobrze wpisany."
        case (NSURLErrorDomain, NSURLErrorTimedOut):
            opis = "Strona nie odpowiada. Spróbuj za chwilę."
        case (NSURLErrorDomain, NSURLErrorCannotConnectToHost):
            opis = "Serwer strony nie przyjmuje połączeń."
        case (NSURLErrorDomain, NSURLErrorServerCertificateUntrusted),
             (NSURLErrorDomain, NSURLErrorServerCertificateHasUnknownRoot),
             (NSURLErrorDomain, NSURLErrorServerCertificateHasBadDate),
             (NSURLErrorDomain, NSURLErrorServerCertificateNotYetValid),
             (NSURLErrorDomain, NSURLErrorSecureConnectionFailed):
            opis = "Połączenie nie jest bezpieczne: certyfikat strony się nie zgadza. LivePL jej nie otworzy."
        default:
            opis = e.localizedDescription
        }
        blad = BladStrony(opis: opis, adres: e.userInfo[NSURLErrorFailingURLErrorKey] as? URL ?? adres)
    }

    // MARK: - WKUIDelegate

    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let url = navigationAction.request.url, Przegladarka.obcyAdres(url) {
            UIApplication.shared.open(url)
            return nil
        }
        guard let przegladarka else { return nil }
        return przegladarka.dodajKarte(konfiguracja: configuration, otwierajaca: self).webView
    }

    func webViewDidClose(_ webView: WKWebView) {
        przegladarka?.zamknij(self)
    }

    private func nadawca(_ ramka: WKFrameInfo) -> String {
        let host = ramka.securityOrigin.host
        return host.isEmpty ? Adres.host(adres) : host
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo) async {
        let tytul = nadawca(frame)
        await withCheckedContinuation { (dalej: CheckedContinuation<Void, Never>) in
            Okna.komunikat(tytul, message) { dalej.resume() }
        }
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo) async -> Bool {
        let tytul = nadawca(frame)
        return await withCheckedContinuation { (dalej: CheckedContinuation<Bool, Never>) in
            Okna.potwierdzenie(tytul, message) { dalej.resume(returning: $0) }
        }
    }

    func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String,
                 defaultText: String?, initiatedByFrame frame: WKFrameInfo) async -> String? {
        let tytul = nadawca(frame)
        return await withCheckedContinuation { (dalej: CheckedContinuation<String?, Never>) in
            Okna.pytanie(tytul, prompt, domyslnie: defaultText) { dalej.resume(returning: $0) }
        }
    }

    // MARK: - WKDownloadDelegate (pobrane pliki trafiają do arkusza „Udostępnij”: Zachowaj w Plikach, Poczta…)

    func download(_ download: WKDownload, decideDestinationUsing response: URLResponse,
                  suggestedFilename: String) async -> URL? {
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("Pobrane", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let nazwa = suggestedFilename.isEmpty ? "plik" : suggestedFilename
        let cel = folder.appendingPathComponent(nazwa)
        pobierane[download] = cel
        return cel
    }

    func downloadDidFinish(_ download: WKDownload) {
        guard let plik = pobierane.removeValue(forKey: download) else { return }
        Okna.udostepnij([plik])
    }

    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        pobierane.removeValue(forKey: download)
        Okna.komunikat("Nie udało się pobrać pliku", error.localizedDescription) {}
    }
}
