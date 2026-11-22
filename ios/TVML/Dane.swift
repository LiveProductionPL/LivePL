import SwiftUI
import UIKit
import CoreText

/// Dane z tvml.pl. Do czasu nowego API (/api/v1, robi je TVML Connect) aplikacja czyta te same akcje co strona:
/// tvml-db.php (artykuły) i tvml-portal.php (konfiguracja portalu, pogoda, wideo).
typealias Slownik = [String: Any]

extension Dictionary where Key == String, Value == Any {
    func s(_ k: String) -> String {
        if let v = self[k] as? String { return v }
        if let v = self[k] as? NSNumber { return v.stringValue }
        return ""
    }
    func d(_ k: String) -> Double? {
        if let v = self[k] as? NSNumber { return v.doubleValue }
        if let v = self[k] as? String { return Double(v.replacingOccurrences(of: ",", with: ".")) }
        return nil
    }
    func i(_ k: String) -> Int { Int(s(k)) ?? Int(d(k) ?? 0) }
    func b(_ k: String) -> Bool {
        if let v = self[k] as? Bool { return v }
        let t = s(k).lowercased()
        return t == "true" || t == "1"
    }
    func slownik(_ k: String) -> Slownik { self[k] as? Slownik ?? [:] }
    func lista(_ k: String) -> [Slownik] { self[k] as? [Slownik] ?? [] }
}

enum Siec {
    static let baza = URL(string: "https://tvml.pl/")!

    /// Własny agent: tvml.pl liczy odsłony aplikacji osobno i nie odrzuca jej jako robota.
    static let agent: String = {
        let wersja = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "2.0"
        let v = ProcessInfo.processInfo.operatingSystemVersion
        return "TVMLApp/\(wersja) (iOS \(v.majorVersion).\(v.minorVersion))"
    }()

    static func json(_ sciezka: String) async throws -> Any {
        guard let url = URL(string: sciezka, relativeTo: baza) else { throw URLError(.badURL) }
        var zadanie = URLRequest(url: url, timeoutInterval: 20)
        zadanie.setValue(agent, forHTTPHeaderField: "User-Agent")
        zadanie.setValue("https://tvml.pl/", forHTTPHeaderField: "Referer")
        zadanie.setValue("pl-PL,pl;q=0.9", forHTTPHeaderField: "Accept-Language")
        let (dane, odp) = try await URLSession.shared.data(for: zadanie)
        guard let http = odp as? HTTPURLResponse, http.statusCode == 200 else { throw URLError(.badServerResponse) }
        return try JSONSerialization.jsonObject(with: dane)
    }

    /// API dla aplikacji (TVML Connect). Ładny adres /api/v1/ działa po restarcie serwera WWW, do tego czasu
    /// tvml-app.php?route=. Aplikacja próbuje po kolei i zapamiętuje działający.
    static let bazyApi = ["api/v1/", "tvml-app.php?route="]
    static var dzialajacaBaza: String?

    static func v1(_ trasa: String, _ parametry: [String: String] = [:]) async throws -> Any {
        var znaki = CharacterSet.alphanumerics
        znaki.insert(charactersIn: "-._~")
        let zapytanie = parametry.sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: znaki) ?? "")" }
            .joined(separator: "&")
        let kolejnosc = dzialajacaBaza.map { d in [d] + bazyApi.filter { $0 != d } } ?? bazyApi
        var ostatni: Error = URLError(.badURL)
        for baza in kolejnosc {
            let laczenie = baza.hasSuffix("route=") ? "&" : "?"
            let sciezka = baza + trasa + (zapytanie.isEmpty ? "" : laczenie + zapytanie)
            do {
                let j = try await json(sciezka)
                dzialajacaBaza = baza
                return j
            } catch {
                ostatni = error
            }
        }
        throw ostatni
    }

    /// Mniejszy obraz w WebP z tego samego pośrednika co strona (img-webp-v2.php).
    static func obraz(_ adres: String, szerokosc: Int) -> URL? {
        guard !adres.isEmpty else { return nil }
        var znaki = CharacterSet.alphanumerics
        znaki.insert(charactersIn: "-._~")
        guard let src = adres.addingPercentEncoding(withAllowedCharacters: znaki) else { return URL(string: adres) }
        return URL(string: "https://tvml.pl/img-webp-v2.php?src=\(src)&w=\(szerokosc)&q=80")
    }
}

/// Kolor z panelu (#rrggbb).
extension Color {
    init(hex: String, zapas: Color = .gray) {
        var t = hex.trimmingCharacters(in: .whitespaces)
        if t.hasPrefix("#") { t.removeFirst() }
        guard t.count == 6, let v = UInt32(t, radix: 16) else { self = zapas; return }
        self = Color(red: Double((v >> 16) & 0xff) / 255, green: Double((v >> 8) & 0xff) / 255, blue: Double(v & 0xff) / 255)
    }

    /// Kolor, który zmienia się z trybem dzień/noc telefonu.
    static func tryb(_ dzien: UInt32, _ noc: UInt32) -> Color {
        func ui(_ v: UInt32) -> UIColor {
            UIColor(red: CGFloat((v >> 16) & 0xff) / 255, green: CGFloat((v >> 8) & 0xff) / 255, blue: CGFloat(v & 0xff) / 255, alpha: 1)
        }
        return Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? ui(noc) : ui(dzien) })
    }
}

/// Czcionka nagłówków jak na tvml.pl: Open Sans (plik w aplikacji, oś grubości 300–800), skalowana z rozmiarem tekstu w telefonie.
enum Czcionka {
    static func naglowek(_ rozmiar: CGFloat, _ grubosc: CGFloat = 800) -> Font {
        let opis = UIFontDescriptor(fontAttributes: [
            .family: "Open Sans",
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [0x77676874: grubosc]
        ])
        return Font(UIFontMetrics.default.scaledFont(for: UIFont(descriptor: opis, size: rozmiar)))
    }
}

/// Tekst rośnie razem z ustawieniem „Większy tekst” w iPhonie (Dynamic Type) i od razu się odświeża.
/// Rozmiar to wielkość przy domyślnym ustawieniu; styl odniesienia (od podpisu do dużego tytułu) ustala, jak szybko
/// rośnie, tak jak w iOS: duże napisy rosną wolniej niż drobne.
struct SkalowanaCzcionka: ViewModifier {
    @ScaledMetric private var rozmiar: CGFloat
    let waga: Font.Weight

    init(_ rozmiar: CGFloat, _ waga: Font.Weight) {
        _rozmiar = ScaledMetric(wrappedValue: rozmiar, relativeTo: SkalowanaCzcionka.styl(rozmiar))
        self.waga = waga
    }

    static func styl(_ r: CGFloat) -> Font.TextStyle {
        switch r {
        case ..<11.5: return .caption2
        case ..<12.5: return .caption
        case ..<13.5: return .footnote
        case ..<15.5: return .subheadline
        case ..<18.5: return .body
        case ..<21: return .title3
        case ..<25: return .title2
        case ..<31: return .title
        default: return .largeTitle
        }
    }

    func body(content: Content) -> some View {
        content.font(.system(size: rozmiar, weight: waga))
    }
}

extension View {
    /// Zamiast .font(.system(size:weight:)): ten sam rozmiar przy domyślnym tekście, większy przy większym.
    func czcionka(_ rozmiar: CGFloat, _ waga: Font.Weight = .regular) -> some View {
        modifier(SkalowanaCzcionka(rozmiar, waga))
    }
}

enum Barwy {
    static let granat = Color(hex: "#0a1f44")
    static let zloto = Color(hex: "#e29818")
    static let tlo = Color.tryb(0xeef1f6, 0x0b1220)
    static let karta = Color.tryb(0xffffff, 0x131c2e)
    static let tekst = Color.tryb(0x0b1830, 0xe8ecf4)
    static let slaby = Color.tryb(0x4f5a6e, 0xa8b3c8)
    static let ramka = Color.tryb(0xdde3ed, 0x22304a)
    static let link = Color.tryb(0x0a3a8c, 0xf0b03a)
}

struct Artykul: Identifiable, Hashable {
    let id: Int
    let tytul: String
    let lead: String
    let obraz: String
    let kategoria: String
    let kolor: String
    let autor: String
    let data: Date?
    let komentarze: Int
    let odslony: Int
    let tagi: [String]
    let zrodloZdjecia: String
    let tresc: String?
    let pilne: Bool
    let sponsorowany: Bool
    /// Gotowe rozmiary obrazu z API v1 (400, 800, 1200).
    let warianty: [Int: String]

    /// Przyjmuje artykuł z API v1 (image i category jako obiekty) albo ze starych akcji tvml-db.php.
    init(_ j: Slownik) {
        id = j["id"] != nil ? j.i("id") : j.i("articleId")
        tytul = Html.tekst(j.s("title"))
        lead = Html.tekst(j["lead"] != nil ? j.s("lead") : j.s("excerpt"))
        if let o = j["image"] as? Slownik {
            obraz = o.s("url")
            zrodloZdjecia = Html.tekst(o.s("source"))
            var w: [Int: String] = [:]
            for (k, v) in o.slownik("variants") { if let n = Int(k), let s = v as? String { w[n] = s } }
            warianty = w
        } else {
            obraz = j.s("image")
            zrodloZdjecia = Html.tekst(j.s("image_source"))
            warianty = [:]
        }
        if let k = j["category"] as? Slownik {
            kategoria = Html.tekst(k.s("name")); kolor = k.s("color")
        } else {
            kategoria = Html.tekst(j.s("category")); kolor = j.s("categoryColor")
        }
        autor = (j["author"] as? Slownik)?.s("name") ?? j.s("author")
        if j["publishedAt"] != nil {
            data = ISO8601DateFormatter().date(from: j.s("publishedAt"))
        } else {
            data = Artykul.daty.date(from: j.s("date"))
        }
        komentarze = j["commentsCount"] != nil ? j.i("commentsCount") : j.i("comments")
        odslony = j.i("views")
        if let t = j["tags"] as? [Slownik] {
            tagi = t.map { $0.s("name") }.filter { !$0.isEmpty }
        } else {
            tagi = j.s("tags").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        }
        let t = j["contentHtml"] != nil ? j.s("contentHtml") : j.s("content")
        tresc = t.isEmpty ? nil : t
        pilne = j.slownik("flags").b("urgent")
        sponsorowany = j.slownik("flags").b("sponsored")
    }

    var link: URL { URL(string: "https://tvml.pl/artykul/\(id)")! }
    func miniatura(_ szerokosc: Int = 600) -> URL? {
        if let klucz = warianty.keys.sorted().first(where: { $0 >= szerokosc }) ?? warianty.keys.max(), let s = warianty[klucz] {
            return URL(string: s)
        }
        return Siec.obraz(obraz, szerokosc: szerokosc)
    }
    var kolorKategorii: Color { Color(hex: kolor, zapas: Barwy.zloto) }

    /// „1 dzień temu”, jak na stronie.
    var kiedy: String {
        guard let data else { return "" }
        let f = RelativeDateTimeFormatter()
        f.locale = Locale(identifier: "pl_PL")
        f.unitsStyle = .full
        return f.localizedString(for: data, relativeTo: .now)
    }

    static let daty: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd HH:mm:ssX"
        return f
    }()

    static func == (a: Artykul, b: Artykul) -> Bool { a.id == b.id }
    func hash(into h: inout Hasher) { h.combine(id) }
}

struct Film: Identifiable, Hashable {
    let id: String
    let tytul: String
    let obraz: URL?
    let data: String
    init(_ j: Slownik) {
        id = j.s("youtubeId").isEmpty ? j.s("id") : j.s("youtubeId")
        tytul = Html.tekst(j.s("title"))
        obraz = URL(string: j.s("thumbnail").isEmpty ? j.s("image") : j.s("thumbnail"))
        data = j.s("publishedAt").isEmpty ? j.s("published") : j.s("publishedAt")
    }
    var osadzenie: URL? { URL(string: "https://www.youtube-nocookie.com/embed/\(id)?playsinline=1&rel=0") }
}

struct Pogoda {
    struct Dzien: Identifiable { let id = UUID(); let nazwa: String; let ikona: String; let opis: String; let max: Double?; let min: Double?; let opad: Int }
    struct Godzina: Identifiable { let id = UUID(); let czas: String; let ikona: String; let temp: Double?; let opad: Int }

    let temperatura: Double?
    let odczuwalna: Double?
    let wilgotnosc: Double?
    let cisnienie: Double?
    let wiatr: Double?
    let porywy: Double?
    let kierunek: String
    let opis: String
    let ikona: String
    let dzien: Bool
    let wschod: String
    let zachod: String
    let dni: [Dzien]
    let godziny: [Godzina]

    /// API v1 (state, isNight, daily, hourly w dniach) albo stare weather-full.
    init(_ j: Slownik) {
        let c = j.slownik("current")
        temperatura = c.d("temperature"); odczuwalna = c.d("feelsLike"); wilgotnosc = c.d("humidity")
        cisnienie = c.d("pressure"); wiatr = c.d("windKmh"); porywy = c.d("windGust")
        kierunek = c.s("windDirText")
        if j["state"] != nil {
            ikona = j.s("state")
            opis = j.s("description").replacingOccurrences(of: " - Noc", with: "").replacingOccurrences(of: " - Dzień", with: "")
            dzien = !j.b("isNight")
        } else {
            ikona = c.s("icon"); opis = c.s("description")
            dzien = c["is_day"] == nil ? true : c.b("is_day")
        }
        wschod = j.s("sunrise").isEmpty ? c.s("sunrise") : j.s("sunrise")
        zachod = j.s("sunset").isEmpty ? c.s("sunset") : j.s("sunset")
        let prognoza = !j.lista("daily").isEmpty ? j.lista("daily") : (j.lista("forecast").isEmpty ? c.lista("forecast") : j.lista("forecast"))
        dni = prognoza.map { d in
            Dzien(nazwa: d.s("label"), ikona: d.s("state").isEmpty ? d.s("icon") : d.s("state"), opis: d.s("desc"), max: d.d("temp"), min: d.d("tempMin"), opad: d.i("pop"))
        }
        let godzinowa: [Slownik] = (j["hourly"] as? [[Slownik]])?.flatMap { $0 } ?? j.lista("hourly")
        godziny = godzinowa.map { g in
            Godzina(czas: g.s("time"), ikona: g.s("state").isEmpty ? g.s("icon") : g.s("state"), temp: g.d("temp"), opad: g.i("pop"))
        }
    }

    static func stopnie(_ v: Double?) -> String {
        guard let v else { return "–" }
        return String(format: "%.1f°", v).replacingOccurrences(of: ".", with: ",")
    }
}

/// Wszystko, co aplikacja pobiera z tvml.pl. Jedno źródło dla wszystkich ekranów.
@MainActor
final class Magazyn: ObservableObject {
    @Published var najnowsze: [Artykul] = []
    @Published var najczytniejsze: [Artykul] = []
    @Published var pasek: [Artykul] = []
    @Published var filmy: [Film] = []
    @Published var pogoda: Pogoda?
    @Published var motyw = Motyw.zwykly
    @Published var wKategorii: [String: [Artykul]] = [:]
    @Published var blad: String?
    @Published var laduje = false
    /// Zakładki z panelu (Aplikacja mobilna → Zakładki), a bez API domyślne z projektu.
    @Published var zakladki: [ZakladkaApp] = ZakladkaApp.domyslne
    @Published var zakladka = "home"
    /// Otwarte ekrany w każdej zakładce (NavigationStack): artykuły, gminy, zapisane. Wszystko przez wartości,
    /// bo link do widoku (NavigationLink { … }) obok ścieżki z wartościami nie otwierał potem artykułu.
    @Published var sciezki: [String: NavigationPath] = [:]
    /// Bloki ekranu Start w kolejności z panelu (layout.widgetsMobile).
    @Published var bloki: [BlokStartu] = BlokStartu.domyslne
    @Published var gminy: [Gmina] = []
    @Published var kategorie: [KategoriaMenu] = KategoriaMenu.domyslne
    @Published var dzisiaj: Dzisiaj?
    /// Komunikat redakcji z panelu (config.message) i prośba o aktualizację (config.minAppVersion.ios), od API 1.2.
    @Published var komunikat: Komunikat?
    /// Kategoria artykułów subportalu z panelu (config.subportals[].category, pusta = slug), np. muzyka → muzyka-tvml.
    private var kategoriePortali: [String: String] = [:]
    /// Ogłoszenia drobne zatwierdzone w panelu (/api/v1/classifieds, API 1.4).
    @Published var ogloszenia: [Ogloszenie] = []
    @Published var wymaganaAktualizacja = false
    /// Nowsza wersja w App Store: łagodna prośba o aktualizację pod paskiem „NA ŻYWO” (do zamknięcia).
    @Published var nowaWersja: Komunikat?
    @Published var zamknieteKomunikaty: [String] = UserDefaults.standard.stringArray(forKey: "tvml-zamkniete-komunikaty") ?? [] {
        didSet { UserDefaults.standard.set(Array(zamknieteKomunikaty.suffix(50)), forKey: "tvml-zamkniete-komunikaty") }
    }
    @Published var arkuszPogody = false
    @Published var arkuszWideo = false
    @Published var zapisane: [Int] = UserDefaults.standard.array(forKey: "tvml-zapisane") as? [Int] ?? [] {
        didSet { UserDefaults.standard.set(zapisane, forKey: "tvml-zapisane") }
    }
    private var ostatnio: Date?

    struct Dzisiaj: Equatable { let etykieta: String; let imieniny: [String] }

    struct KategoriaMenu: Hashable {
        let nazwa: String
        let slug: String?
        static let domyslne: [KategoriaMenu] = [
            .init(nazwa: "Wszystko", slug: nil), .init(nazwa: "Aktualności", slug: "aktualnosci"), .init(nazwa: "Sport", slug: "sport"),
            .init(nazwa: "Na sygnale", slug: "na-sygnale"), .init(nazwa: "Kultura", slug: "kultura"), .init(nazwa: "Muzyka", slug: "muzyka")
        ]
    }

    func odswiez(wymus: Bool = false) async {
        if !wymus, let ostatnio, Date().timeIntervalSince(ostatnio) < 120 { return }
        laduje = true
        defer { laduje = false }
        if await odswiezV1() {
            blad = nil
            ostatnio = Date()
            await sprawdzNowaWersje()
            return
        }
        await odswiezStare()
        await sprawdzNowaWersje()
    }

    /// Najnowsza wersja TVML w App Store (wyszukiwarka Apple, bez żadnych danych o telefonie).
    func sprawdzNowaWersje() async {
        guard let adres = URL(string: "https://itunes.apple.com/lookup?bundleId=com.LiveProduction.TVML&country=pl"),
              let odp = try? await URLSession.shared.data(from: adres),
              let j = (try? JSONSerialization.jsonObject(with: odp.0)) as? Slownik,
              let w = j.lista("results").first?.s("version"), !w.isEmpty,
              Wersja.starsza(Wersja.biezaca, niz: w) else { nowaWersja = nil; return }
        nowaWersja = Komunikat(["id": "aktualizacja-" + w, "title": "Nowa wersja TVML \(w)",
                                "text": "Zaktualizuj aplikację w App Store, żeby mieć najnowsze funkcje.", "level": "info",
                                "link": "https://apps.apple.com/app/id1468277905", "linkLabel": "Aktualizuj"])
    }

    /// Nowe API (/api/v1): config, artykuły, najczęściej czytane, wideo, pogoda.
    private func odswiezV1() async -> Bool {
        async let k = Siec.v1("config")
        async let a = Siec.v1("articles", ["limit": "20"])
        async let b = Siec.v1("articles/most-read", ["limit": "10"])
        async let d = Siec.v1("videos", ["limit": "12"])
        async let e = Siec.v1("weather")
        guard let lista = (try? await a) as? Slownik, let konf = (try? await k) as? Slownik else { return false }
        najnowsze = lista.lista("items").map(Artykul.init)
        if let m = Motyw.zV1(konf.slownik("theme"), wersjaApi: konf.s("apiVersion")) { motyw = m }
        let z = konf.lista("tabs").compactMap(ZakladkaApp.init)
        if !z.isEmpty {
            var gotowe = Array(z.prefix(5))
            if !gotowe.contains(where: { $0.cel == .wiecej }) {
                if gotowe.count == 5 { gotowe.removeLast() }
                gotowe.append(ZakladkaApp.wiecej)
            }
            zakladki = gotowe
            if !gotowe.contains(where: { $0.id == zakladka }) { zakladka = gotowe[0].id }
        }
        // drugi blok ogłoszeń (np. „popularne”) pokazywałby tę samą listę, więc zostaje pierwszy
        var bylyOgloszenia = false
        let bl = konf.lista("home").compactMap(BlokStartu.init).filter { b in
            guard b.typ == "classifieds" else { return true }
            defer { bylyOgloszenia = true }
            return !bylyOgloszenia
        }
        if !bl.isEmpty { bloki = bl }
        pasek = konf.slownik("ticker").lista("items").map(Artykul.init)
        gminy = konf.lista("counties").map(Gmina.init).sorted { $0.kolejnosc < $1.kolejnosc }
        for s in konf.lista("subportals") where !s.s("slug").isEmpty {
            kategoriePortali[s.s("slug")] = s.s("category").isEmpty ? s.s("slug") : s.s("category")
        }
        let sub = konf.lista("subportals").map { s in
            KategoriaMenu(nazwa: s.s("name").replacingOccurrences(of: "TVML.pl - ", with: ""), slug: s.s("slug"))
        }
        if !sub.isEmpty { kategorie = [.init(nazwa: "Wszystko", slug: nil), .init(nazwa: "Aktualności", slug: "aktualnosci")] + sub }
        let d0 = konf.slownik("today")
        if !d0.s("label").isEmpty { dzisiaj = Dzisiaj(etykieta: d0.s("label"), imieniny: (d0["nameDays"] as? [String]) ?? []) }
        komunikat = Komunikat(konf.slownik("message"))
        let minimalna = konf.slownik("minAppVersion").s("ios")
        wymaganaAktualizacja = !minimalna.isEmpty && Wersja.starsza(Wersja.biezaca, niz: minimalna)
        if let j = (try? await b) as? Slownik { najczytniejsze = j.lista("items").map(Artykul.init) }
        if let j = (try? await d) as? Slownik { filmy = j.lista("items").map(Film.init) }
        if let j = (try? await e) as? Slownik, j["current"] != nil { pogoda = Pogoda(j) }
        // listy bloków Start z kategorią albo flagą i fotorelacje, odświeżane razem z resztą
        for b in bloki where b.typ == "posts" { await wpisy(b) }
        if let g = bloki.first(where: { $0.typ == "gallery" }) { await fotorelacje(limit: g.limit) }
        if let o = bloki.first(where: { $0.typ == "classifieds" }),
           let j = (try? await Siec.v1("classifieds", ["limit": String(o.limit > 0 ? o.limit : 4)])) as? Slownik {
            ogloszenia = j.lista("items").map(Ogloszenie.init)
        }
        return true
    }

    /// Wpisy bloku posts z kategorią albo flagą z panelu.
    func wpisy(_ b: BlokStartu) async {
        guard let klucz = b.klucz else { return }
        var p = ["limit": String(b.limit > 0 ? b.limit : 4)]
        if let k = b.kategoria { p["category"] = k }
        if let f = b.filtr { p[f] = "1" }
        if let j = (try? await Siec.v1("articles", p)) as? Slownik { wKategorii[klucz] = j.lista("items").map(Artykul.init) }
    }

    /// Zapas: te same akcje co strona (tvml-db.php i tvml-portal.php).
    private func odswiezStare() async {
        async let a = Siec.json("tvml-db.php?action=latest&limit=20")
        async let b = Siec.json("tvml-db.php?action=most-read&limit=10")
        async let c = Siec.json("tvml-db.php?action=ticker&category=aktualnosci&limit=10")
        async let d = Siec.json("tvml-portal.php?action=youtube&channel=@TVMLlive&limit=12")
        async let e = Siec.json("tvml-portal.php?action=weather-full")
        async let f = Siec.json("tvml-portal.php?action=portal-config")
        do {
            najnowsze = ((try await a) as? [Slownik] ?? []).map(Artykul.init)
            blad = nil
            ostatnio = Date()
        } catch {
            if najnowsze.isEmpty { blad = "Nie udało się pobrać wiadomości. Sprawdź internet i przeciągnij w dół, żeby spróbować ponownie." }
        }
        if let lista = (try? await b) as? [Slownik] { najczytniejsze = lista.map(Artykul.init) }
        if let lista = (try? await c) as? [Slownik] { pasek = lista.map(Artykul.init) }
        if let j = (try? await d) as? Slownik { filmy = j.lista("items").map(Film.init) }
        if let j = (try? await e) as? Slownik, j["current"] != nil { pogoda = Pogoda(j) }
        if let j = (try? await f) as? Slownik { motyw = Motyw.wylicz(z: j) }
    }

    func kategoria(_ slug: String, limit: Int = 20) async {
        // subportal ma w panelu własną kategorię artykułów (Muzyka: muzyka-tvml); lista zostaje pod slugiem
        let kat = kategoriePortali[slug] ?? slug
        if let j = (try? await Siec.v1("articles", ["category": kat, "limit": String(limit)])) as? Slownik {
            wKategorii[slug] = j.lista("items").map(Artykul.init)
            return
        }
        guard let lista = (try? await Siec.json("tvml-db.php?action=news&category=\(kat)&limit=\(limit)")) as? [Slownik] else { return }
        wKategorii[slug] = lista.map(Artykul.init)
    }

    func gmina(_ g: Gmina) async -> [Artykul] {
        guard let j = (try? await Siec.v1("articles", ["county": g.id, "limit": "20"])) as? Slownik else { return [] }
        return j.lista("items").map(Artykul.init)
    }

    func fotorelacje(limit: Int) async {
        guard let j = (try? await Siec.v1("articles", ["photoReport": "1", "limit": String(max(limit, 4))])) as? Slownik else { return }
        wKategorii["_fotorelacje"] = j.lista("items").map(Artykul.init)
    }

    func artykul(_ id: Int) async -> Artykul? {
        if let j = (try? await Siec.v1("articles/\(id)")) as? Slownik, j["id"] != nil { return Artykul(j) }
        guard let j = (try? await Siec.json("tvml-db.php?action=article&id=\(id)")) as? Slownik, j["id"] != nil else { return nil }
        return Artykul(j)
    }

    func szukaj(_ fraza: String) async -> [Artykul] {
        if let j = (try? await Siec.v1("search", ["q": fraza, "limit": "20"])) as? Slownik { return j.lista("items").map(Artykul.init) }
        let q = fraza.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
        guard !q.isEmpty, let lista = (try? await Siec.json("tvml-db.php?action=search&q=\(q)&limit=20")) as? [Slownik] else { return [] }
        return lista.map(Artykul.init)
    }

    func przelaczZapis(_ a: Artykul) {
        if let i = zapisane.firstIndex(of: a.id) { zapisane.remove(at: i) } else { zapisane.insert(a.id, at: 0) }
    }

    /// Pogoda i wideo: zakładka, jeśli panel ją ustawił, a jeśli nie, arkusz nad bieżącym ekranem.
    func pokaz(_ cel: ZakladkaApp.Cel) {
        if let z = zakladki.first(where: { $0.cel == cel }) { zakladka = z.id; return }
        if cel == .pogoda { arkuszPogody = true }
        if cel == .wideo { arkuszWideo = true }
    }

    func naStart() {
        let start = zakladki.first { $0.cel == .start }?.id ?? zakladki.first?.id ?? "home"
        zakladka = start
        sciezki[start] = NavigationPath()
    }
}

/// Zakładka dolnego paska ustawiana w panelu (mobileApp.tabs).
struct ZakladkaApp: Identifiable, Hashable {
    enum Cel: Hashable { case start, pogoda, wideo, wiecej, kategoria(String) }
    let id: String
    let tytul: String
    let ikona: String
    let cel: Cel

    static let wiecej = ZakladkaApp(id: "more", tytul: "Więcej", ikona: "square.grid.2x2", cel: .wiecej)
    static let domyslne: [ZakladkaApp] = [
        ZakladkaApp(id: "home", tytul: "Start", ikona: "house", cel: .start),
        ZakladkaApp(id: "sport", tytul: "Sport", ikona: "sportscourt", cel: .kategoria("sport")),
        ZakladkaApp(id: "video", tytul: "Wideo", ikona: "play.rectangle", cel: .wideo),
        ZakladkaApp(id: "weather", tytul: "Pogoda", ikona: "cloud.sun", cel: .pogoda),
        wiecej
    ]

    init(id: String, tytul: String, ikona: String, cel: Cel) {
        self.id = id; self.tytul = tytul; self.ikona = ikona; self.cel = cel
    }

    init?(_ j: Slownik) {
        let cel = j.s("target")
        switch cel {
        case "home": self.cel = .start
        case "video": self.cel = .wideo
        case "weather": self.cel = .pogoda
        case "more": self.cel = .wiecej
        default:
            guard let i = cel.firstIndex(of: ":") else { return nil }
            self.cel = .kategoria(String(cel[cel.index(after: i)...]))
        }
        id = j.s("id").isEmpty ? cel : j.s("id")
        tytul = j.s("title")
        let ikony = ["home": "house", "sport": "sportscourt", "video": "play.rectangle", "weather": "cloud.sun", "more": "square.grid.2x2",
                     "news": "newspaper", "culture": "theatermasks", "music": "music.note", "alert": "light.beacon.max", "county": "map"]
        ikona = ikony[j.s("icon")] ?? "circle.grid.2x2"
    }
}

/// Komunikat redakcji (config.message): baner pod paskiem „NA ŻYWO”. Pilnego nie da się zamknąć.
struct Komunikat: Equatable {
    let id: String
    let tytul: String
    let tekst: String
    let poziom: String
    let link: URL?
    let etykieta: String

    init?(_ j: Slownik) {
        tytul = Html.tekst(j.s("title"))
        tekst = Html.tekst(j.s("text"))
        guard !tekst.isEmpty || !tytul.isEmpty else { return nil }
        if let koniec = ISO8601DateFormatter().date(from: j.s("until")), koniec < .now { return nil }
        id = j.s("id").isEmpty ? tytul + "|" + tekst : j.s("id")
        poziom = ["info", "warning", "urgent"].contains(j.s("level")) ? j.s("level") : "info"
        link = j.s("link").isEmpty ? nil : URL(string: j.s("link"))
        etykieta = j.s("linkLabel").isEmpty ? "Więcej" : j.s("linkLabel")
    }

    var pilny: Bool { poziom == "urgent" }
}

/// Ogłoszenie drobne z /api/v1/classifieds: tytuł, kategoria, cena, treść, kontakt i zdjęcia.
struct Ogloszenie: Identifiable, Hashable {
    let id: Int
    let tytul: String
    let kategoria: String
    let cena: String
    let tekst: String
    let kontakt: String
    let zdjecia: [URL]
    let data: Date?

    init(_ j: Slownik) {
        id = j.i("id")
        tytul = Html.tekst(j.s("title"))
        kategoria = Html.tekst((j["category"] as? Slownik)?.s("name") ?? j.s("category"))
        let c = j.s("price")
        cena = c.isEmpty ? "" : (Double(c) != nil ? c.replacingOccurrences(of: ".", with: ",") + " zł" : c)
        tekst = Html.tekst(j.s("text"), zachowajAkapity: true)
        if let k = j["contact"] as? Slownik {
            kontakt = ["name", "phone", "email"].map { k.s($0) }.filter { !$0.isEmpty }.joined(separator: "\n")
        } else {
            kontakt = j.s("contact")
        }
        zdjecia = j.lista("photos").compactMap { o in
            let w = o.slownik("variants")
            return URL(string: (w["800"] as? String) ?? (w["400"] as? String) ?? o.s("url"))
        }
        data = ISO8601DateFormatter().date(from: j.s("publishedAt"))
    }
}

/// Porównanie wersji aplikacji („2.0” < „2.0.1” < „2.1”).
enum Wersja {
    static var biezaca: String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0" }
    static func starsza(_ a: String, niz b: String) -> Bool {
        let x = a.split(separator: ".").map { Int($0) ?? 0 }, y = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(x.count, y.count) {
            let p = i < x.count ? x[i] : 0, q = i < y.count ? y[i] : 0
            if p != q { return p < q }
        }
        return false
    }
}

/// Blok ekranu Start z panelu (Page builder, wersja na telefon).
struct BlokStartu: Identifiable, Hashable {
    let id: String
    let typ: String
    let tytul: String
    let ukryjTytul: Bool
    let limit: Int
    let kategoria: String?
    /// Flaga z panelu dla bloku posts (API 1.3): sponsored, featured, urgent albo photoReport, filtr do /articles.
    let filtr: String?

    static let domyslne: [BlokStartu] = [
        BlokStartu(id: "hero", typ: "hero", tytul: "", ukryjTytul: true, limit: 5, kategoria: nil),
        BlokStartu(id: "weather", typ: "weather", tytul: "Pogoda", ukryjTytul: true, limit: 0, kategoria: nil),
        BlokStartu(id: "posts", typ: "posts", tytul: "Aktualności", ukryjTytul: false, limit: 10, kategoria: nil),
        BlokStartu(id: "mostread", typ: "mostread", tytul: "Najczęściej czytane", ukryjTytul: false, limit: 5, kategoria: nil),
        BlokStartu(id: "video", typ: "video", tytul: "Wideo", ukryjTytul: false, limit: 6, kategoria: nil)
    ]

    init(id: String, typ: String, tytul: String, ukryjTytul: Bool, limit: Int, kategoria: String?, filtr: String? = nil) {
        self.id = id; self.typ = typ; self.tytul = tytul; self.ukryjTytul = ukryjTytul; self.limit = limit; self.kategoria = kategoria
        self.filtr = filtr
    }

    init?(_ j: Slownik) {
        let p = j.slownik("props")
        let typ = j.s("type")
        guard ["hero", "weather", "alert", "video", "posts", "mostread", "gallery", "counties", "social", "sponsored", "classifieds"].contains(typ) else { return nil }
        // „sponsored” z kreatora to lista artykułów sponsorowanych, rysowana jak posts
        self.typ = typ == "sponsored" ? "posts" : typ
        id = j.s("id").isEmpty ? UUID().uuidString : j.s("id")
        tytul = j.s("title")
        ukryjTytul = p.b("hideTitle")
        limit = p.i("limit")
        kategoria = p.s("category").isEmpty ? nil : p.s("category")
        filtr = typ == "sponsored" ? "sponsored" : ["sponsored", "featured", "urgent", "photoReport"].first { p.b($0) }
    }

    /// Klucz własnej listy bloku posts (kategoria albo flaga); nil, gdy blok pokazuje kolejne najnowsze.
    var klucz: String? { kategoria == nil && filtr == nil ? nil : "blok:\(kategoria ?? ""):\(filtr ?? "")" }
}

struct Gmina: Identifiable, Hashable {
    let id: String
    let nazwa: String
    let herb: URL?
    let kolejnosc: Int
    init(_ j: Slownik) {
        id = j.s("tag").isEmpty ? j.s("id") : j.s("tag")
        nazwa = j.s("name")
        herb = URL(string: j.s("crest"))
        kolejnosc = j.i("order")
    }
}

/// Treść artykułu (HTML z edytora) jako bloki do wyświetlenia natywnie: akapity, śródtytuły, obrazy i filmy.
enum Html {
    enum Blok: Identifiable {
        case akapit(String), srodtytul(String), obraz(URL), film(String)
        var id: String {
            switch self {
            case .akapit(let t): return "p" + t.prefix(40)
            case .srodtytul(let t): return "h" + t.prefix(40)
            case .obraz(let u): return "i" + u.absoluteString
            case .film(let f): return "v" + f
            }
        }
    }

    static func bloki(_ html: String) -> [Blok] {
        var wynik: [Blok] = []
        let wzor = try! NSRegularExpression(pattern: #"<img[^>]*src="([^"]+)"[^>]*>|<iframe[^>]*src="[^"]*(?:youtube(?:-nocookie)?\.com/embed/|youtu\.be/)([A-Za-z0-9_-]{6,})[^"]*"[^>]*>(?:</iframe>)?|<h[2-4][^>]*>(.*?)</h[2-4]>"#,
                                         options: [.caseInsensitive, .dotMatchesLineSeparators])
        let ns = html as NSString
        var od = 0
        func tekstDo(_ koniec: Int) {
            guard koniec > od else { return }
            let kawalek = ns.substring(with: NSRange(location: od, length: koniec - od))
            for p in akapity(kawalek) { wynik.append(.akapit(p)) }
        }
        for m in wzor.matches(in: html, range: NSRange(location: 0, length: ns.length)) {
            tekstDo(m.range.location)
            if m.range(at: 1).location != NSNotFound, let u = URL(string: ns.substring(with: m.range(at: 1)), relativeTo: Siec.baza) {
                wynik.append(.obraz(u.absoluteURL))
            } else if m.range(at: 2).location != NSNotFound {
                wynik.append(.film(ns.substring(with: m.range(at: 2))))
            } else if m.range(at: 3).location != NSNotFound {
                let t = tekst(ns.substring(with: m.range(at: 3)))
                if !t.isEmpty { wynik.append(.srodtytul(t)) }
            }
            od = m.range.location + m.range.length
        }
        tekstDo(ns.length)
        return wynik
    }

    static func akapity(_ html: String) -> [String] {
        var t = html.replacingOccurrences(of: #"<br\s*/?>"#, with: "\n", options: .regularExpression)
        t = t.replacingOccurrences(of: #"</(p|div|li|blockquote|ul|ol|figure)>"#, with: "\n\n", options: [.regularExpression, .caseInsensitive])
        t = t.replacingOccurrences(of: #"<li[^>]*>"#, with: "• ", options: [.regularExpression, .caseInsensitive])
        return tekst(t, zachowajAkapity: true)
            .components(separatedBy: "\n\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    /// Tekst bez znaczników, z rozkodowanymi encjami.
    static func tekst(_ html: String, zachowajAkapity: Bool = false) -> String {
        var t = html.replacingOccurrences(of: #"<script.*?</script>|<style.*?</style>"#, with: "", options: [.regularExpression, .caseInsensitive])
        t = t.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        let nazwy = ["&nbsp;": " ", "&amp;": "&", "&quot;": "\"", "&#39;": "'", "&apos;": "'", "&lt;": "<", "&gt;": ">",
                     "&bdquo;": "„", "&rdquo;": "”", "&ldquo;": "“", "&ndash;": "–", "&mdash;": "—", "&hellip;": "…", "&oacute;": "ó"]
        for (k, v) in nazwy { t = t.replacingOccurrences(of: k, with: v) }
        if let wzor = try? NSRegularExpression(pattern: "&#(x?)([0-9a-fA-F]+);") {
            let ns = t as NSString
            var wynik = ""
            var od = 0
            for m in wzor.matches(in: t, range: NSRange(location: 0, length: ns.length)) {
                wynik += ns.substring(with: NSRange(location: od, length: m.range.location - od))
                let hex = ns.substring(with: m.range(at: 1)) == "x"
                if let kod = UInt32(ns.substring(with: m.range(at: 2)), radix: hex ? 16 : 10), let z = Unicode.Scalar(kod) {
                    wynik.append(Character(z))
                }
                od = m.range.location + m.range.length
            }
            wynik += ns.substring(from: od)
            t = wynik
        }
        if !zachowajAkapity {
            t = t.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        }
        return t.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
