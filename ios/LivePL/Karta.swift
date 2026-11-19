import SwiftUI
import WebKit
import Combine

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

    /// Adres karty przywróconej po ponownym uruchomieniu, ładowany dopiero po wejściu na kartę.
    private(set) var odlozona: URL?
    private var pobierane: [WKDownload: URL] = [:]

    init(programID: String?, konfiguracja: WKWebViewConfiguration? = nil, przegladarka: Przegladarka) {
        self.programID = programID
        self.przegladarka = przegladarka
        webView = WKWebView(frame: .zero, configuration: konfiguracja ?? Karta.nowaKonfiguracja())
        super.init()

        webView.navigationDelegate = self
        webView.uiDelegate = self
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
        k.applicationNameForUserAgent = "Version/\(ios).0 Mobile/15E148 Safari/604.1"
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

    // MARK: - WKNavigationDelegate

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
        guard let url = navigationAction.request.url else { return .allow }
        // mailto:, tel:, mapy, App Store… otwiera iOS
        if Przegladarka.obcyAdres(url) {
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
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        koniecOdswiezania()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        koniecOdswiezania()
        pokazBlad(error)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        koniecOdswiezania()
        pokazBlad(error)
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
