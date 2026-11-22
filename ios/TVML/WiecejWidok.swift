import SwiftUI
import SafariServices
import WebKit

/// Więcej: zapisane artykuły, działy tvml.pl, wygląd, rozmiar tekstu, strony redakcji i media społecznościowe.
/// „Zgłoś zdarzenie”, sondy i powiadomienia przejdą tu natywnie razem z nowym API tvml.pl.
struct WiecejWidok: View {
    @EnvironmentObject private var m: Magazyn
    @AppStorage("tvml-wyglad") private var wyglad = "auto"
    @AppStorage("tvml-rozmiar") private var rozmiar = 1
    @State private var strona: StronaWWW?

    /// Podstrony w sekcji „TVML”; pozostałe z panelu idą niżej, do „Informacje”.
    private static let glowne = ["o-nas", "redakcja", "reklama", "kontakt", "regulamin", "polityka-prywatnosci"]
    private static let ikony = ["o-nas": "info.circle", "redakcja": "person.2", "reklama": "megaphone.fill", "kontakt": "envelope",
                                "regulamin": "doc.text", "polityka-prywatnosci": "hand.raised", "cookies": "checkmark.shield",
                                "mapa-strony": "map", "newsletter": "envelope.open", "zasady-redakcyjne": "text.book.closed",
                                "sprzedaj-ogloszenie": "tag", "gazeta-ogloszeniowa": "newspaper"]

    var body: some View {
        List {
            Section {
                Button { strona = StronaWWW(sciezka: "") } label: {
                    Label("Zgłoś zdarzenie (Alert TVML)", systemImage: "exclamationmark.triangle.fill")
                        .czcionka(16, .bold).foregroundStyle(Color(hex: "#c8102e"))
                }
            } footer: {
                Text("Formularz jest na stronie głównej tvml.pl, przycisk „Zgłoś zdarzenie”.")
            }
            Section("Moje") {
                NavigationLink(value: EkranWiecej.zapisane) {
                    Label("Zapisane artykuły", systemImage: "bookmark")
                        .badge(m.zapisane.count)
                }
            }
            Section("Na tvml.pl") {
                NavigationLink(value: EkranWiecej.ogloszenia) { Label("Ogłoszenia drobne", systemImage: "megaphone") }
                NavigationLink(value: EkranWiecej.fotorelacje) { Label("Fotorelacje", systemImage: "photo.on.rectangle") }
            }
            Section("Wygląd") {
                Picker("Tryb", selection: $wyglad) {
                    Text("Jak w telefonie").tag("auto")
                    Text("Dzień").tag("dzien")
                    Text("Noc").tag("noc")
                }
                Picker("Rozmiar tekstu", selection: $rozmiar) {
                    Text("Mały").tag(0)
                    Text("Średni").tag(1)
                    Text("Duży").tag(2)
                }
            }
            Section("TVML") {
                ForEach(m.strony.filter { WiecejWidok.glowne.contains($0.slug) }) { s in pozycja(s) }
            }
            let inne = m.strony.filter { !WiecejWidok.glowne.contains($0.slug) }
            if !inne.isEmpty {
                Section("Informacje") {
                    ForEach(inne) { s in pozycja(s) }
                }
            }
            Section("Obserwuj nas") {
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
        .sheet(item: $strona) { s in Safari(url: URL(string: "https://tvml.pl/" + s.sciezka)!).ignoresSafeArea() }
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
                    HStack(spacing: 2) { Image(systemName: "chevron.left"); Text("Wróć") }
                        .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
                }
                Text("Zapisane").font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst)
                if laduje { ProgressView().padding(40) }
                else if lista.isEmpty {
                    Text("Nie masz zapisanych artykułów. Zapiszesz je ikoną zakładki w artykule.")
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
            var wynik: [Artykul] = []
            for id in m.zapisane.prefix(30) {
                if let a = await m.artykul(id) { wynik.append(a) }
            }
            lista = wynik
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
                else if !fraza.isEmpty && wyniki.isEmpty { Text("Nic nie znaleziono.").foregroundStyle(Barwy.slaby) }
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
            .navigationTitle("Szukaj")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $fraza, placement: .navigationBarDrawer(displayMode: .always), prompt: "Szukaj na tvml.pl")
            .onSubmit(of: .search) {
                Task {
                    szuka = true
                    wyniki = await m.szukaj(fraza)
                    szuka = false
                }
            }
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Zamknij") { zamknij() } } }
        }
    }
}

/// Ekrany z „Więcej” w ścieżce nawigacji zakładki (przez wartość, tak jak artykuły i gminy).
enum EkranWiecej: Hashable {
    case zapisane, ogloszenia, fotorelacje
    case strona(String)
}

struct EkranWiecejWidok: View {
    let ekran: EkranWiecej

    var body: some View {
        switch ekran {
        case .zapisane: ZapisaneWidok()
        case .ogloszenia: OgloszeniaWidok().kolumna()
        case .fotorelacje: FotorelacjeWidok().kolumna()
        case .strona(let slug): PodstronaWidok(slug: slug).kolumna()
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

    private var tytul: String { tresc?.tytul ?? m.strony.first { $0.slug == slug }?.tytul ?? "" }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { wroc() } label: {
                HStack(spacing: 2) { Image(systemName: "chevron.left"); Text("Wróć") }
                    .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
            }
            .padding(.horizontal, 16)
            VStack(alignment: .leading, spacing: 4) {
                Text(tytul).font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst).accessibilityAddTraits(.isHeader)
                if let d = tresc?.aktualizacja {
                    Text("Aktualizacja: \(PodstronaWidok.data.string(from: d))").czcionka(12).foregroundStyle(Barwy.slaby)
                }
            }
            .padding(.horizontal, 16).padding(.bottom, 6)
            if laduje {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let t = tresc {
                if t.html.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text("Treść tej strony jest w przygotowaniu.").czcionka(15).foregroundStyle(Barwy.slaby).padding(16)
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
        var zaladowane = ""
        init(link: @escaping (URL) -> Void) { self.link = link }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping @MainActor @Sendable (WKNavigationActionPolicy) -> Void) {
            guard navigationAction.navigationType == .linkActivated, let u = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }
            decisionHandler(.cancel)
            link(u)
        }
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
                    HStack(spacing: 2) { Image(systemName: "chevron.left"); Text("Wróć") }
                        .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
                }
                Text("Ogłoszenia drobne").font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst).accessibilityAddTraits(.isHeader)
                if laduje { ProgressView().frame(maxWidth: .infinity, minHeight: 200) }
                else if lista.isEmpty {
                    Text("Na razie nie ma ogłoszeń.").czcionka(15).foregroundStyle(Barwy.slaby).padding(.vertical, 12)
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
                    Text("Dodaj ogłoszenie na tvml.pl").czcionka(14, .semibold)
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
                    HStack(spacing: 2) { Image(systemName: "chevron.left"); Text("Wróć") }
                        .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
                }
                Text("Fotorelacje").font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst).accessibilityAddTraits(.isHeader)
                if laduje { ProgressView().frame(maxWidth: .infinity, minHeight: 200) }
                else if lista.isEmpty { Text("Brak fotorelacji.").czcionka(15).foregroundStyle(Barwy.slaby) }
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
