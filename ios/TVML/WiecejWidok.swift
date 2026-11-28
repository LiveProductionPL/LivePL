import SwiftUI
import SafariServices
import WebKit

/// Więcej: zapisane artykuły, działy tvml.pl, wygląd, rozmiar tekstu, strony redakcji i media społecznościowe.
/// Powiadomienia o nowych wpisach (push) w „Moje → Powiadomienia”.
struct WiecejWidok: View {
    @EnvironmentObject private var m: Magazyn
    @AppStorage("tvml-wyglad") private var wyglad = "auto"
    @AppStorage("tvml-rozmiar") private var rozmiar = 1
    @AppStorage(Jezyk.kluczWyboru) private var wyborJezyka = "system"
    @State private var strona: StronaWWW?

    /// Podstrony w dwóch sekcjach, w tej kolejności. Nowe strony z panelu (z treścią) dochodzą na koniec „TVML”.
    /// Strony bez treści albo z „Wkrótce.” (Karol, 22.11: tego, co w budowie, nie publikujemy) aplikacja pomija,
    /// a mapy strony www w aplikacji nie ma.
    private static let tvml = ["o-nas", "redakcja", "zasady-redakcyjne", "reklama", "kontakt"]
    private static let dokumenty = ["regulamin", "polityka-prywatnosci", "cookies"]
    private static let pomijane: Set<String> = ["mapa-strony"]
    private static let ikony = ["o-nas": "info.circle", "redakcja": "person.2", "zasady-redakcyjne": "text.book.closed",
                                "reklama": "megaphone", "kontakt": "envelope", "regulamin": "doc.text",
                                "polityka-prywatnosci": "hand.raised", "cookies": "checkmark.shield",
                                "newsletter": "envelope.open", "sprzedaj-ogloszenie": "tag", "gazeta-ogloszeniowa": "newspaper"]

    /// Strony gotowe do pokazania; zanim aplikacja je sprawdzi, tylko te, które na pewno mają treść.
    private var gotowe: [Podstrona] {
        let pewne: Set<String> = ["o-nas", "redakcja", "kontakt", "regulamin", "polityka-prywatnosci"]
        return m.strony.filter { s in
            !WiecejWidok.pomijane.contains(s.slug) && (m.stronyGotowe?.contains(s.slug) ?? pewne.contains(s.slug))
        }
    }
    /// Sekcja strony: z serwera (API 1.7.1, pole section), a bez niego według list wyżej.
    private func wDokumentach(_ s: Podstrona) -> Bool {
        s.sekcja.isEmpty ? WiecejWidok.dokumenty.contains(s.slug) : s.sekcja == "dokumenty"
    }
    /// Znane strony w ustalonej kolejności, potem pozostałe w kolejności z panelu.
    private func uloz(_ lista: [Podstrona], _ kolejnosc: [String]) -> [Podstrona] {
        let znane = kolejnosc.compactMap { slug in lista.first { $0.slug == slug } }
        return znane + lista.filter { !kolejnosc.contains($0.slug) }
    }
    private var sekcjaTVML: [Podstrona] { uloz(gotowe.filter { !wDokumentach($0) }, WiecejWidok.tvml) }
    private var sekcjaDokumenty: [Podstrona] { uloz(gotowe.filter { wDokumentach($0) }, WiecejWidok.dokumenty) }

    var body: some View {
        List {
            Section {
                // formularz Alertu TVML od razu: w aplikacji (AlertWidok) albo tvml.pl/#alert, gdy serwer go nie zgłasza
                Button { strona = StronaWWW(sciezka: "#alert") } label: {
                    Label(Napisy.t("app.reportIncident", "Zgłoś zdarzenie (Alert TVML)"), systemImage: "exclamationmark.triangle")
                        .czcionka(16, .bold).foregroundStyle(Color(hex: "#c8102e"))
                }
            }
            Section(Napisy.t("app.mine", "Moje")) {
                NavigationLink(value: EkranWiecej.zapisane) {
                    Label(Napisy.t("app.savedArticles", "Zapisane artykuły"), systemImage: "bookmark")
                        .badge(m.zapisane.count)
                }
                // push o nowym wpisie (config.push.enabled i tematy z panelu)
                if !m.tematyPush.isEmpty {
                    NavigationLink(value: EkranWiecej.powiadomienia) {
                        Label(Napisy.t("app.notifications", "Powiadomienia"), systemImage: "bell")
                    }
                }
            }
            if m.maOgloszenia || m.maFotorelacje {
                Section(Napisy.t("app.onSite", "Na tvml.pl")) {
                    if m.maOgloszenia {
                        NavigationLink(value: EkranWiecej.ogloszenia) { Label(Napisy.t("app.classifieds", "Ogłoszenia drobne"), systemImage: "list.bullet.rectangle") }
                    }
                    if m.maFotorelacje {
                        NavigationLink(value: EkranWiecej.fotorelacje) { Label(Napisy.t("app.photoReports", "Fotorelacje"), systemImage: "photo.on.rectangle") }
                    }
                }
            }
            Section(Napisy.t("app.theme", "Wygląd")) {
                Picker(Napisy.t("app.mode", "Tryb"), selection: $wyglad) {
                    Text(Napisy.t("app.themeAuto", "Jak w telefonie")).tag("auto")
                    Text(Napisy.t("app.themeLight", "Dzień")).tag("dzien")
                    Text(Napisy.t("app.themeDark", "Noc")).tag("noc")
                }
                Picker(Napisy.t("app.textSize", "Rozmiar tekstu"), selection: $rozmiar) {
                    Text(Napisy.t("app.textSmall", "Mały")).tag(0)
                    Text(Napisy.t("app.textMedium", "Średni")).tag(1)
                    Text(Napisy.t("app.textLarge", "Duży")).tag(2)
                }
            }
            // tylko w TestFlight: karta pogody na wybrany dzień (sceny z harmonogramu panelu)
            if PodgladDnia.dostepny { PodgladDnia() }
            // API 1.8.0: język treści i napisów; tylko języki włączone w panelu (config.languages)
            if Jezyk.dostepne.count > 1 {
                Section {
                    Picker(Napisy.t("app.language", "Język"), selection: $wyborJezyka) {
                        Text(Napisy.t("app.languageSystem", "Jak w systemie")).tag("system")
                        ForEach(Jezyk.dostepne, id: \.self) { j in Text(Jezyk.nazwy[j] ?? j).tag(j) }
                    }
                } footer: {
                    Text(Napisy.t("app.languageHint", "Treść artykułów tłumaczy redakcja. Teksty, których jeszcze nie przetłumaczono, są po polsku."))
                }
            }
            let tvml = sekcjaTVML
            if !tvml.isEmpty {
                Section("TVML") { ForEach(tvml) { s in pozycja(s) } }
            }
            let dokumenty = sekcjaDokumenty
            if !dokumenty.isEmpty {
                Section(Napisy.t("app.documents", "Dokumenty")) { ForEach(dokumenty) { s in pozycja(s) } }
            }
            Section(Napisy.t("app.followUs", "Obserwuj nas")) {
                Link(destination: URL(string: "https://www.facebook.com/TVMLpl")!) { Label("Facebook", systemImage: "f.circle") }
                Link(destination: URL(string: "https://www.youtube.com/@TVMLlive")!) { Label("YouTube", systemImage: "play.rectangle") }
                Link(destination: URL(string: "https://www.instagram.com/tvmlpl/")!) { Label("Instagram", systemImage: "camera") }
                Link(destination: URL(string: "https://www.tiktok.com/@tvml.pl")!) { Label("TikTok", systemImage: "music.note") }
            }
            Section {
                Text("TVML \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "") · \(m.stopka)")
                    .czcionka(12).foregroundStyle(Barwy.slaby)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Barwy.tlo)
        .sheet(item: $strona) { s in OknoStrony(strona: s) }
        .task { if m.stronyGotowe == nil { await m.sprawdzStrony() } }
    }

    /// Podstrona redakcji otwiera się w aplikacji (treść z API), nie w przeglądarce.
    private func pozycja(_ s: Podstrona) -> some View {
        NavigationLink(value: EkranWiecej.strona(s.slug)) {
            Label(s.tytul, systemImage: WiecejWidok.ikony[s.slug] ?? "doc.text").foregroundStyle(Barwy.tekst)
        }
    }
}

struct StronaWWW: Identifiable { let sciezka: String; var id: String { sciezka } }

struct Safari: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }
    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}

struct ZapisaneWidok: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dismiss) private var wroc
    @State private var lista: [Artykul] = []
    @State private var laduje = true

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                Button { wroc() } label: {
                    HStack(spacing: 2) { Image(systemName: "chevron.left"); Text(Napisy.t("app.back", "Wróć")) }
                        .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
                }
                Text(Napisy.t("app.saved", "Zapisane")).font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst)
                if laduje { ProgressView().padding(40) }
                else if lista.isEmpty {
                    Text(Napisy.t("app.savedEmpty", "Nie masz zapisanych artykułów. Zapiszesz je ikoną zakładki w artykule."))
                        .czcionka(15).foregroundStyle(Barwy.slaby).padding(24)
                }
                ForEach(lista) { a in
                    NavigationLink(value: a) { WierszArtykulu(artykul: a) }.buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        .background(Barwy.tlo)
        .toolbar(.hidden, for: .navigationBar)
        .task {
            lista = await m.zapisaneArtykuly()
            laduje = false
        }
    }
}

struct SzukajWidok: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dismiss) private var zamknij
    @State private var fraza = ""
    @State private var wyniki: [Artykul] = []
    @State private var szuka = false

    var body: some View {
        NavigationStack {
            List {
                if szuka { ProgressView() }
                else if !fraza.isEmpty && wyniki.isEmpty { Text(Napisy.t("app.noResults", "Nic nie znaleziono.")).foregroundStyle(Barwy.slaby) }
                ForEach(wyniki) { a in
                    Button {
                        zamknij()
                        m.otworz(a)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(a.tytul).czcionka(15, .semibold).foregroundStyle(Barwy.tekst)
                            Text([a.kategoria, a.kiedy].filter { !$0.isEmpty }.joined(separator: " · ")).czcionka(12).foregroundStyle(Barwy.slaby)
                        }
                    }
                }
            }
            .navigationTitle(Napisy.t("app.search", "Szukaj"))
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $fraza, placement: .navigationBarDrawer(displayMode: .always), prompt: Napisy.t("app.searchPlaceholder", "Szukaj na tvml.pl"))
            .onSubmit(of: .search) {
                Task {
                    szuka = true
                    wyniki = await m.szukaj(fraza)
                    szuka = false
                }
            }
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(Napisy.t("app.close", "Zamknij")) { zamknij() } } }
        }
    }
}

/// Ekrany z „Więcej” w ścieżce nawigacji zakładki (przez wartość, tak jak artykuły i gminy).
enum EkranWiecej: Hashable {
    case zapisane, ogloszenia, fotorelacje, powiadomienia
    case strona(String)
    case wydarzenie(Wydarzenie)
}

struct EkranWiecejWidok: View {
    let ekran: EkranWiecej

    var body: some View {
        switch ekran {
        case .zapisane: ZapisaneWidok()
        case .powiadomienia: PowiadomieniaWidok().kolumna()
        case .ogloszenia: OgloszeniaWidok().kolumna()
        case .fotorelacje: FotorelacjeWidok().kolumna()
        case .strona(let slug): PodstronaWidok(slug: slug).kolumna()
        case .wydarzenie(let w): WydarzenieWidok(poczatkowe: w).kolumna()
        }
    }
}

/// Podstrona redakcji (O nas, Kontakt, Regulamin…) w aplikacji: tytuł, data aktualizacji i treść z /api/v1/pages
/// w WKWebView bez JavaScriptu. Bez API: ta sama strona w wersji dla aplikacji (?widok=app), też w aplikacji.
struct PodstronaWidok: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dismiss) private var wroc
    @Environment(\.colorScheme) private var schemat
    @Environment(\.openURL) private var otworzAdres
    let slug: String
    @State private var tresc: TrescPodstrony?
    @State private var laduje = true
    /// strona dla aplikacji (appUrl) nie wczytała się: zostaje treść z API
    @State private var bladStronyApp = false

    private var tytul: String { tresc?.tytul ?? m.strony.first { $0.slug == slug }?.tytul ?? "" }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { wroc() } label: {
                HStack(spacing: 2) { Image(systemName: "chevron.left"); Text(Napisy.t("app.back", "Wróć")) }
                    .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
            }
            .padding(.horizontal, 16)
            VStack(alignment: .leading, spacing: 4) {
                Text(tytul).font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst).accessibilityAddTraits(.isHeader)
                if let d = tresc?.aktualizacja {
                    Text(Napisy.t("app.updated", "Aktualizacja") + ": " + PodstronaWidok.data.string(from: d)).czcionka(12).foregroundStyle(Barwy.slaby)
                }
            }
            .padding(.horizontal, 16).padding(.bottom, 6)
            if let u = adresDlaAplikacji, !bladStronyApp {
                // najpierw strona dla aplikacji z config.pages (appUrl), jak na Androidzie: ma skrypty (np. salon
                // wróżb Andrzejek) i kotwice tej strony; treść z API zostaje zapasem (Karol 28.11: linki wróżb nie działały)
                HtmlWidok(zrodlo: .adres(u), ciemny: schemat == .dark, link: obsluz, blad: { bladStronyApp = true })
            } else if laduje {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let t = tresc {
                if t.html.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(Napisy.t("app.pageInProgress", "Treść tej strony jest w przygotowaniu.")).czcionka(15).foregroundStyle(Barwy.slaby).padding(16)
                    Spacer()
                } else {
                    HtmlWidok(zrodlo: .html(t.html), ciemny: schemat == .dark, link: obsluz)
                }
            } else if let u = zapasowyAdres {
                HtmlWidok(zrodlo: .adres(u), ciemny: schemat == .dark, link: obsluz)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Barwy.tlo)
        .toolbar(.hidden, for: .navigationBar)
        .task(id: slug) {
            tresc = await m.podstrona(slug)
            laduje = false
        }
    }

    /// appUrl podstrony z config.pages (w motywie aplikacji); nil, gdy strony nie ma w config.pages.
    private var adresDlaAplikacji: URL? {
        guard let baza = m.strony.first(where: { $0.slug == slug })?.adresApp?.absoluteString else { return nil }
        return URL(string: baza + (baza.contains("?") ? "&" : "?") + "motyw=" + (schemat == .dark ? "ciemny" : "jasny"))
    }

    /// Zapas, gdy API nie odpowie: strona w wersji dla aplikacji, w motywie aplikacji.
    private var zapasowyAdres: URL? {
        let baza = m.strony.first { $0.slug == slug }?.adresApp?.absoluteString ?? "https://tvml.pl/strona/\(slug)?widok=app"
        return URL(string: baza + (baza.contains("?") ? "&" : "?") + "motyw=" + (schemat == .dark ? "ciemny" : "jasny"))
    }

    /// Linki w treści: inne podstrony i artykuły tvml.pl w aplikacji, reszta w systemie (Safari, poczta, telefon).
    private func obsluz(_ u: URL) {
        if let s = Linki.podstrona(u) {
            if s != slug { m.otworz(EkranWiecej.strona(s)) }
        } else if let id = Linki.artykul(u) {
            Task { @MainActor in
                if let a = await m.artykul(id) { m.otworz(a) } else { otworzAdres(u) }
            }
        } else {
            otworzAdres(u)
        }
    }

    static let data: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "pl_PL")
        f.timeZone = TimeZone(identifier: "Europe/Warsaw")
        f.dateFormat = "dd.MM.yyyy"
        return f
    }()
}

/// Linki tvml.pl w treści podstron, także w postaci z panelu (strona.html?slug=…, artykul.html?id=…).
enum Linki {
    static func tvml(_ u: URL) -> Bool {
        guard let h = u.host?.lowercased() else { return true }
        return h == "tvml.pl" || h == "www.tvml.pl"
    }

    static func podstrona(_ u: URL) -> String? {
        guard tvml(u), u.scheme == nil || u.scheme == "http" || u.scheme == "https" else { return nil }
        let sciezka = u.path
        if sciezka.hasPrefix("/strona/") {
            let s = sciezka.dropFirst("/strona/".count).split(separator: "/").first.map(String.init) ?? ""
            return s.isEmpty ? nil : s
        }
        if sciezka.hasSuffix("strona.html"),
           let s = URLComponents(url: u, resolvingAgainstBaseURL: true)?.queryItems?.first(where: { $0.name == "slug" })?.value, !s.isEmpty {
            return s
        }
        return nil
    }

    static func artykul(_ u: URL) -> Int? {
        guard tvml(u), u.scheme == nil || u.scheme == "http" || u.scheme == "https" else { return nil }
        let czesci = u.pathComponents
        if let i = czesci.firstIndex(of: "artykul"), i + 1 < czesci.count, let id = Int(czesci[i + 1].prefix { $0.isNumber }) {
            return id
        }
        if u.path.hasSuffix("artykul.html"),
           let v = URLComponents(url: u, resolvingAgainstBaseURL: true)?.queryItems?.first(where: { $0.name == "id" })?.value, let id = Int(v) {
            return id
        }
        return nil
    }
}

/// Treść HTML z panelu w WKWebView bez JavaScriptu, w kolorach i wielkości tekstu aplikacji.
/// Każde stuknięcie w link trafia do aplikacji (link), a nie do tego samego widoku.
struct HtmlWidok: UIViewRepresentable {
    enum Zrodlo: Hashable { case html(String), adres(URL) }
    let zrodlo: Zrodlo
    let ciemny: Bool
    let link: (URL) -> Void
    /// strona z adresu nie wczytała się (brak sieci, błąd HTTP)
    var blad: (() -> Void)? = nil

    func makeCoordinator() -> Koordynator { Koordynator(link: link) }

    func makeUIView(context: Context) -> WKWebView {
        let k = WKWebViewConfiguration()
        k.defaultWebpagePreferences.allowsContentJavaScript = false
        let w = WKWebView(frame: .zero, configuration: k)
        w.navigationDelegate = context.coordinator
        w.isOpaque = false
        w.backgroundColor = .clear
        w.scrollView.backgroundColor = .clear
        return w
    }

    func updateUIView(_ w: WKWebView, context: Context) {
        context.coordinator.link = link
        context.coordinator.blad = blad
        let klucz = "\(zrodlo.hashValue)|\(ciemny)|\(UIFont.preferredFont(forTextStyle: .body).pointSize)"
        guard context.coordinator.zaladowane != klucz else { return }
        context.coordinator.zaladowane = klucz
        switch zrodlo {
        case .html(let h): w.loadHTMLString(HtmlWidok.strona(h, ciemny: ciemny), baseURL: URL(string: "https://tvml.pl/"))
        case .adres(let u): w.load(URLRequest(url: u))
        }
    }

    static func strona(_ tresc: String, ciemny: Bool) -> String {
        let rozmiar = Int(UIFont.preferredFont(forTextStyle: .body).pointSize.rounded())
        let tekst = ciemny ? "#e8ecf4" : "#0b1830"
        let slaby = ciemny ? "#a8b3c8" : "#4f5a6e"
        let link = ciemny ? "#f0b03a" : "#0a3a8c"
        let ramka = ciemny ? "#22304a" : "#dde3ed"
        return """
        <!doctype html><html lang="pl"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
        <style>
        html { -webkit-text-size-adjust: 100%; }
        body { margin: 0; padding: 4px 16px calc(32px + env(safe-area-inset-bottom)); font-family: -apple-system, system-ui, sans-serif;
               font-size: \(rozmiar)px; line-height: 1.5; color: \(tekst); background: transparent; overflow-wrap: anywhere; }
        h2, h3, h4 { line-height: 1.25; margin: 1.2em 0 .5em; }
        p { margin: 0 0 .8em; }
        a { color: \(link); }
        img { max-width: 100%; height: auto; border-radius: 8px; }
        figure { margin: 1em 0; }
        table { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; }
        td, th { border: 1px solid \(ramka); padding: 6px 8px; text-align: left; vertical-align: top; }
        blockquote { margin: 1em 0; padding-left: 12px; border-left: 3px solid \(ramka); color: \(slaby); }
        hr { border: 0; border-top: 1px solid \(ramka); margin: 1.2em 0; }
        ul, ol { padding-left: 1.3em; }
        </style></head><body>\(tresc)</body></html>
        """
    }

    final class Koordynator: NSObject, WKNavigationDelegate {
        var link: (URL) -> Void
        var blad: (() -> Void)?
        var zaladowane = ""
        init(link: @escaping (URL) -> Void) { self.link = link }

        private func zglosBlad() {
            guard let blad else { return }
            DispatchQueue.main.async { blad() }
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            if (error as? URLError)?.code != .cancelled { zglosBlad() }
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse,
                     decisionHandler: @escaping @MainActor @Sendable (WKNavigationResponsePolicy) -> Void) {
            if navigationResponse.isForMainFrame, let http = navigationResponse.response as? HTTPURLResponse, http.statusCode >= 400 {
                decisionHandler(.cancel)
                zglosBlad()
                return
            }
            decisionHandler(.allow)
        }

        /// JavaScript tylko dla strony w wersji dla aplikacji z tvml.pl (?widok=app, np. salon wróżb Andrzejek),
        /// treść z API dalej bez skryptów. Kotwica na tej samej stronie (#wosk) przewija w widoku, a nie otwiera
        /// go od nowa (Karol 28.11: „klikam na linki od wrozba na aplikacji ale nie nie dziala”).
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, preferences: WKWebpagePreferences,
                     decisionHandler: @escaping @MainActor @Sendable (WKNavigationActionPolicy, WKWebpagePreferences) -> Void) {
            let u = navigationAction.request.url
            preferences.allowsContentJavaScript = u.map(HtmlWidok.stronaDlaAplikacji) ?? false
            guard navigationAction.navigationType == .linkActivated, let u else {
                decisionHandler(.allow, preferences)
                return
            }
            if u.fragment != nil, let teraz = webView.url, HtmlWidok.bezKotwicy(u) == HtmlWidok.bezKotwicy(teraz) {
                decisionHandler(.allow, preferences)
                return
            }
            decisionHandler(.cancel, preferences)
            link(u)
        }
    }

    /// https://tvml.pl/…?widok=app: strona przygotowana dla aplikacji (może mieć skrypty, np. salon wróżb).
    static func stronaDlaAplikacji(_ u: URL) -> Bool {
        let host = u.host?.lowercased() ?? ""
        guard u.scheme == "https", host == "tvml.pl" || host == "www.tvml.pl" else { return false }
        return URLComponents(url: u, resolvingAgainstBaseURL: false)?.queryItems?.contains { $0.name == "widok" && $0.value == "app" } ?? false
    }

    static func bezKotwicy(_ u: URL) -> String {
        var c = URLComponents(url: u, resolvingAgainstBaseURL: false)
        c?.fragment = nil
        return c?.string ?? u.absoluteString
    }
}

/// Ogłoszenia drobne z panelu (/api/v1/classifieds) w aplikacji.
struct OgloszeniaWidok: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dismiss) private var wroc
    @State private var lista: [Ogloszenie] = []
    @State private var laduje = true
    @State private var wybrane: Ogloszenie?
    @State private var strona: StronaWWW?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                Button { wroc() } label: {
                    HStack(spacing: 2) { Image(systemName: "chevron.left"); Text(Napisy.t("app.back", "Wróć")) }
                        .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
                }
                Text(Napisy.t("app.classifieds", "Ogłoszenia drobne")).font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst).accessibilityAddTraits(.isHeader)
                if laduje { ProgressView().frame(maxWidth: .infinity, minHeight: 200) }
                else if lista.isEmpty {
                    Text(Napisy.t("app.noClassifieds", "Na razie nie ma ogłoszeń.")).czcionka(15).foregroundStyle(Barwy.slaby).padding(.vertical, 12)
                } else {
                    VStack(spacing: 0) {
                        ForEach(Array(lista.enumerated()), id: \.element.id) { i, o in
                            Button { wybrane = o } label: { WierszOgloszenia(ogloszenie: o) }.buttonStyle(.plain)
                            if i < lista.count - 1 { Barwy.ramka.frame(height: 1) }
                        }
                    }
                    .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
                }
                Button { strona = StronaWWW(sciezka: "lista/ogloszenia") } label: {
                    Text(Napisy.t("app.addClassified", "Dodaj ogłoszenie na tvml.pl")).czcionka(14, .semibold)
                }
            }
            .padding(16)
        }
        .background(Barwy.tlo)
        .toolbar(.hidden, for: .navigationBar)
        .sheet(item: $wybrane) { OgloszenieWidok(ogloszenie: $0) }
        .sheet(item: $strona) { s in Safari(url: URL(string: "https://tvml.pl/" + s.sciezka)!).ignoresSafeArea() }
        .task {
            lista = await m.wszystkieOgloszenia()
            laduje = false
        }
    }
}

/// Fotorelacje (/api/v1/articles?photoReport=1) w aplikacji.
struct FotorelacjeWidok: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dismiss) private var wroc
    @State private var lista: [Artykul] = []
    @State private var laduje = true

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                Button { wroc() } label: {
                    HStack(spacing: 2) { Image(systemName: "chevron.left"); Text(Napisy.t("app.back", "Wróć")) }
                        .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
                }
                Text(Napisy.t("app.photoReports", "Fotorelacje")).font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst).accessibilityAddTraits(.isHeader)
                if laduje { ProgressView().frame(maxWidth: .infinity, minHeight: 200) }
                else if lista.isEmpty { Text(Napisy.t("app.photoReportsEmpty", "Brak fotorelacji.")).czcionka(15).foregroundStyle(Barwy.slaby) }
                ForEach(lista) { a in
                    NavigationLink(value: a) { WierszArtykulu(artykul: a) }.buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        .background(Barwy.tlo)
        .toolbar(.hidden, for: .navigationBar)
        .task {
            lista = await m.listaFotorelacji()
            laduje = false
        }
    }
}

/// Tylko TestFlight (i Xcode): „Podgląd dnia” w Więcej. Karta pogody na Starcie pokazuje scenę i belkę z wybranego dnia
/// i godziny, liczone z harmonogramu motywów z panelu tak jak w dniu wydania (Karol, 24.11: „Przygotuj wersję test
/// już poprawionymi”). Tylko w pamięci; w wersji z App Store tej sekcji nie ma.
struct PodgladDnia: View {
    @EnvironmentObject private var m: Magazyn
    @State private var wybrana = Date()

    struct Skrot { let nazwa: String; let miesiac: Int; let dzien: Int; let godzina: Int }
    static let skroty: [Skrot] = [
        Skrot(nazwa: "28.11", miesiac: 11, dzien: 28, godzina: 20), Skrot(nazwa: "1.12", miesiac: 12, dzien: 1, godzina: 12),
        Skrot(nazwa: "6.12", miesiac: 12, dzien: 6, godzina: 12), Skrot(nazwa: "24.12", miesiac: 12, dzien: 24, godzina: 20),
        Skrot(nazwa: "31.12", miesiac: 12, dzien: 31, godzina: 23)
    ]

    /// Tylko w budowie z Xcode (DEBUG). W TestFlight i App Store go nie ma
    /// (Karol 24.11: „usun podglad dnia bo bylo tylko w testach ios tez i publikujemy”).
    static var dostepny: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    var body: some View {
        Section {
            Toggle("Podgląd dnia", isOn: Binding(
                get: { m.podgladDnia != nil },
                set: { m.podgladDnia = $0 ? wybrana : nil }))
            DatePicker("Dzień i godzina", selection: $wybrana, displayedComponents: [.date, .hourAndMinute])
                .onChange(of: wybrana) { _, nowa in if m.podgladDnia != nil { m.podgladDnia = nowa } }
            HStack {
                ForEach(Self.skroty, id: \.nazwa) { s in
                    Button(s.nazwa) { ustaw(miesiac: s.miesiac, dzien: s.dzien, godzina: s.godzina) }
                        .buttonStyle(.bordered).font(.caption)
                }
            }
        } header: {
            Text("TestFlight")
        } footer: {
            Text("Karta pogody na Starcie pokaże scenę i belkę z tego dnia. Pora dnia na karcie zależy od prawdziwej godziny. Widać to tylko w TestFlight.")
        }
    }

    private func ustaw(miesiac: Int, dzien: Int, godzina: Int) {
        var k = Calendar(identifier: .gregorian)
        k.timeZone = TimeZone(identifier: "Europe/Warsaw") ?? .current
        let rok = k.component(.year, from: Date())
        if let d = k.date(from: DateComponents(year: rok, month: miesiac, day: dzien, hour: godzina)) {
            wybrana = d
            m.podgladDnia = d
        }
    }
}
