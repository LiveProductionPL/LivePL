import SwiftUI
import UIKit
import CoreText
import CryptoKit

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

/// Język aplikacji (API 1.8.0): polski, ukraiński albo angielski. Domyślnie „Jak w systemie”: telefon po ukraińsku
/// albo angielsku dostaje ten język, każdy inny polski. Wybór w „Więcej” (UserDefaults); język wyłączony w panelu → polski.
enum Jezyk {
    static let kluczWyboru = "tvml-jezyk"
    static let wszystkie = ["pl", "uk", "en"]
    /// Nazwy w ich własnym języku, jak w przełącznikach języka.
    static let nazwy = ["pl": "Polski", "uk": "Українська", "en": "English"]

    /// "system", "pl", "uk" albo "en"
    static var wybor: String { UserDefaults.standard.string(forKey: kluczWyboru) ?? "system" }

    /// Języki włączone w panelu (config.languages), zapamiętane; bez tej informacji wszystkie trzy.
    static var dostepne: [String] {
        let l = (UserDefaults.standard.stringArray(forKey: "tvml-jezyki") ?? []).filter { wszystkie.contains($0) }
        return l.isEmpty ? wszystkie : l
    }

    static func ustawDostepne(_ l: [String]) {
        let znane = wszystkie.filter { l.contains($0) }
        if !znane.isEmpty { UserDefaults.standard.set(znane, forKey: "tvml-jezyki") }
    }

    /// Język telefonu: uk albo en, każdy inny (też rosyjski czy niemiecki) to polski.
    static var systemowy: String {
        let k = Locale.preferredLanguages.first.map { String($0.prefix(2)).lowercased() } ?? "pl"
        return k == "uk" || k == "en" ? k : "pl"
    }

    static var biezacy: String {
        let j = wybor == "system" ? systemowy : wybor
        return dostepne.contains(j) ? j : "pl"
    }

    static var polski: Bool { biezacy == "pl" }
    /// Separator dziesiętny: po angielsku kropka, po polsku i ukraińsku przecinek.
    static var przecinek: String { biezacy == "en" ? "." : "," }

    static func locale(_ j: String? = nil) -> Locale {
        switch j ?? biezacy {
        case "uk": return Locale(identifier: "uk_UA")
        case "en": return Locale(identifier: "en_GB")
        default: return Locale(identifier: "pl_PL")
        }
    }

    private static var formatery: [String: DateFormatter] = [:]

    /// DateFormatter w języku aplikacji i czasie polskim, jeden na wzór i język.
    static func format(_ wzor: String, posix: Bool = false) -> DateFormatter {
        let jezyk = posix ? "posix" : biezacy
        let klucz = jezyk + "|" + wzor
        if let f = formatery[klucz] { return f }
        let f = DateFormatter()
        f.locale = posix ? Locale(identifier: "en_US_POSIX") : locale(jezyk)
        f.timeZone = TimeZone(identifier: "Europe/Warsaw")
        f.dateFormat = wzor
        formatery[klucz] = f
        return f
    }
}

/// Napisy aplikacji z config.i18n (te same słowniki co strona, klucze app.*). Aplikacja nie ma własnych tłumaczeń:
/// po ukraińsku i angielsku tekst z serwera, a bez klucza (i po polsku) tekst wbudowany, taki jak dotąd.
/// Ostatnie słowniki zostają w telefonie, więc bez sieci napisy też są.
enum Napisy {
    private static var slowniki: [String: [String: String]] =
        (UserDefaults.standard.dictionary(forKey: "tvml-i18n") as? [String: [String: String]]) ?? [:]

    static func ustaw(_ i: Slownik) {
        var nowe: [String: [String: String]] = [:]
        for (j, v) in i {
            guard let s = v as? Slownik else { continue }
            var teksty: [String: String] = [:]
            for (k, t) in s { if let t = t as? String, !t.isEmpty { teksty[k] = t } }
            if !teksty.isEmpty { nowe[j] = teksty }
        }
        guard !nowe.isEmpty else { return }
        slowniki = nowe
        UserDefaults.standard.set(nowe, forKey: "tvml-i18n")
    }

    static func t(_ klucz: String, _ polski: String) -> String {
        let j = Jezyk.biezacy
        guard j != "pl", let v = slowniki[j]?[klucz] else { return polski }
        return v
    }

    /// Odmiana liczebnika według CLDR: klucz.one, klucz.few, klucz.many, klucz.other (jak na Androidzie).
    static func kategoria(_ n: Int, _ jezyk: String) -> String {
        let d = n % 10, s = n % 100
        switch jezyk {
        case "en": return n == 1 ? "one" : "other"
        case "uk":
            if d == 1 && s != 11 { return "one" }
            return (2...4).contains(d) && !(12...14).contains(s) ? "few" : "many"
        default:
            if n == 1 { return "one" }
            return (2...4).contains(d) && !(12...14).contains(s) ? "few" : "many"
        }
    }

    /// Liczba z odmianą, np. liczba("app.votes", 5, ["one": "{n} głos", "few": "{n} głosy", "many": "{n} głosów"]).
    static func liczba(_ klucz: String, _ n: Int, _ polski: [String: String]) -> String {
        let j = Jezyk.biezacy
        let k = kategoria(n, j)
        let wzor = j == "pl" ? polski[k] : (slowniki[j]?[klucz + "." + k] ?? slowniki[j]?[klucz + ".other"])
        return (wzor ?? polski[kategoria(n, "pl")] ?? "{n}").replacingOccurrences(of: "{n}", with: String(n))
    }

    /// {n} w tekście zastąpione liczbą, np. app.readingMinutes.
    static func t(_ klucz: String, _ polski: String, n: Int) -> String {
        t(klucz, polski).replacingOccurrences(of: "{n}", with: String(n))
    }
}

enum Siec {
    static let baza = URL(string: "https://tvml.pl/")!

    /// Własny agent: tvml.pl liczy odsłony aplikacji osobno i nie odrzuca jej jako robota.
    static let agent: String = {
        let wersja = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "2.0"
        let v = ProcessInfo.processInfo.operatingSystemVersion
        return "TVMLApp/\(wersja) (iOS \(v.majorVersion).\(v.minorVersion))"
    }()

    /// swiezy: z pominięciem pamięci podręcznej telefonu (serwer daje max-age=300, a artykuł w tłumaczeniu zmienia się po kilku sekundach)
    static func json(_ sciezka: String, swiezy: Bool = false) async throws -> Any {
        guard let url = URL(string: sciezka, relativeTo: baza) else { throw URLError(.badURL) }
        var zadanie = URLRequest(url: url, cachePolicy: swiezy ? .reloadIgnoringLocalCacheData : .useProtocolCachePolicy, timeoutInterval: 20)
        zadanie.setValue(agent, forHTTPHeaderField: "User-Agent")
        zadanie.setValue("https://tvml.pl/", forHTTPHeaderField: "Referer")
        zadanie.setValue("pl-PL,pl;q=0.9", forHTTPHeaderField: "Accept-Language")
        let (dane, odp) = try await URLSession.shared.data(for: zadanie)
        guard let http = odp as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        guard http.statusCode == 200 else { throw BladHttp(kod: http.statusCode) }
        return try JSONSerialization.jsonObject(with: dane)
    }

    /// Odpowiedź serwera inna niż 200 (np. 410 dla wygasłego podglądu z kodu QR).
    struct BladHttp: Error { let kod: Int }

    /// API dla aplikacji (TVML Connect). Ładny adres /api/v1/ działa po restarcie serwera WWW, do tego czasu
    /// tvml-app.php?route=. Aplikacja próbuje po kolei i zapamiętuje działający.
    static let bazyApi = ["api/v1/", "tvml-app.php?route="]
    static var dzialajacaBaza: String?

    static func v1(_ trasa: String, _ parametry: [String: String] = [:], swiezy: Bool = false) async throws -> Any {
        // API 1.8.0: język treści w każdym zapytaniu (bez lang serwer odpowiada po polsku)
        var parametry = parametry
        if parametry["lang"] == nil { parametry["lang"] = Jezyk.biezacy }
        var znaki = CharacterSet.alphanumerics
        znaki.insert(charactersIn: "-._~")
        let zapytanie = parametry.sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: znaki) ?? "")" }
            .joined(separator: "&")
        let kolejnosc = dzialajacaBaza.map { d in [d] + bazyApi.filter { $0 != d } } ?? bazyApi
        // podgląd z kodu QR nie trafia do pamięci: to szkic, nie opublikowane ustawienia
        let klucz = trasa + "?" + zapytanie
        let doPamieci = parametry["preview"] == nil
        var ostatni: Error = URLError(.badURL)
        for baza in kolejnosc {
            let laczenie = baza.hasSuffix("route=") ? "&" : "?"
            let sciezka = baza + trasa + (zapytanie.isEmpty ? "" : laczenie + zapytanie)
            do {
                let j = try await json(sciezka, swiezy: swiezy)
                dzialajacaBaza = baza
                if doPamieci { Pamiec.zapisz(j, klucz: klucz) }
                return j
            } catch {
                // anulowane zadanie (np. przerwane odświeżenie) to nie brak sieci: bez pamięci i bez „Bez internetu”
                // (Karol 28.11: napis „Bez internetu” przy każdym odświeżeniu, na Wi-Fi i w sieci komórkowej)
                if Task.isCancelled || error is CancellationError || (error as? URLError)?.code == .cancelled { throw error }
                ostatni = error
            }
        }
        // bez internetu albo przy awarii serwera: ostatnia odpowiedź z telefonu (błąd 4xx to prawdziwa odmowa, nie brak sieci)
        let odmowa = (ostatni as? BladHttp).map { (400..<500).contains($0.kod) } ?? false
        if doPamieci, !odmowa, let zapis = Pamiec.wczytaj(klucz) {
            zPamieci?.dodaj(zapis.kiedy)
            return zapis.json
        }
        throw ostatni
    }

    /// Odpowiedzi z pamięci telefonu w jednym odświeżeniu Startu (Magazyn.odswiez). Wartość zadania, a nie zmienna
    /// globalna: zapytania z innych ekranów (bloki, artykuł) w tym czasie nie zapalają napisu „Bez internetu”.
    @TaskLocal static var zPamieci: ZPamieci?

    /// Najstarsza odpowiedź wzięta z pamięci telefonu; nil = wszystko prosto z sieci.
    final class ZPamieci: @unchecked Sendable {
        private let zamek = NSLock()
        private var data: Date?
        var najstarsza: Date? { zamek.lock(); defer { zamek.unlock() }; return data }
        func dodaj(_ kiedy: Date) {
            zamek.lock(); defer { zamek.unlock() }
            data = min(data ?? kiedy, kiedy)
        }
    }

    /// Ostatnie odpowiedzi API w pamięci podręcznej telefonu (Caches/tvml-api), żeby bez internetu było widać
    /// ostatnie wiadomości, pogodę i ustawienia z panelu (Karol 25.11: „Aplikacja może pokazywać wczytuje pogodę
    /// itp jak nie ma Internetu prawda ?”). System może je usunąć przy braku miejsca; wtedy jak dotąd komunikat o błędzie.
    enum Pamiec {
        static let katalog: URL = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("tvml-api", isDirectory: true)

        static func plik(_ klucz: String) -> URL {
            let skrot = SHA256.hash(data: Data(klucz.utf8)).map { String(format: "%02x", $0) }.joined()
            return katalog.appendingPathComponent(skrot + ".json")
        }

        static func zapisz(_ j: Any, klucz: String) {
            guard JSONSerialization.isValidJSONObject(j), let dane = try? JSONSerialization.data(withJSONObject: j) else { return }
            try? FileManager.default.createDirectory(at: katalog, withIntermediateDirectories: true)
            try? dane.write(to: plik(klucz), options: .atomic)
        }

        static func wczytaj(_ klucz: String) -> (json: Any, kiedy: Date)? {
            let p = plik(klucz)
            guard let dane = try? Data(contentsOf: p), let j = try? JSONSerialization.jsonObject(with: dane) else { return nil }
            let kiedy = (try? p.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date()
            return (j, kiedy)
        }
    }

    /// POST z JSON-em do API (np. głos w sondzie). Zwraca kod HTTP i odpowiedź; bez identyfikatorów urządzenia.
    static func v1Wyslij(_ trasa: String, _ cialo: [String: Any]) async throws -> (Int, Slownik) {
        let kolejnosc = dzialajacaBaza.map { d in [d] + bazyApi.filter { $0 != d } } ?? bazyApi
        var ostatni: Error = URLError(.badURL)
        for b in kolejnosc {
            let jezyk = (b.hasSuffix("route=") ? "&" : "?") + "lang=" + Jezyk.biezacy
            guard let url = URL(string: b + trasa + jezyk, relativeTo: baza) else { continue }
            var z = URLRequest(url: url, timeoutInterval: 20)
            z.httpMethod = "POST"
            z.setValue(agent, forHTTPHeaderField: "User-Agent")
            z.setValue("https://tvml.pl/", forHTTPHeaderField: "Referer")
            z.setValue("application/json", forHTTPHeaderField: "Content-Type")
            z.httpBody = try JSONSerialization.data(withJSONObject: cialo)
            do {
                let (dane, odp) = try await URLSession.shared.data(for: z)
                guard let http = odp as? HTTPURLResponse else { continue }
                if http.statusCode == 404 && b != kolejnosc.last { continue }
                return (http.statusCode, (try? JSONSerialization.jsonObject(with: dane)) as? Slownik ?? [:])
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
    let kategoriaSlug: String
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
    /// API 1.8.0: tytuł i treść w wybranym języku (translated)
    let przetlumaczony: Bool
    /// API 1.8.0: tłumaczenie jeszcze powstaje, więc tekst jest po polsku (translationPending)
    let tlumaczenieWToku: Bool
    /// translationEta: "morning" = tłumaczenie będzie rano (prośba w nocy), inaczej zwykle w ciągu godziny
    let tlumaczenieRano: Bool
    /// translationEta: "now" = serwer tłumaczy w tej chwili (API 1.8.1), artykuł trzeba pobrać ponownie
    let tlumaczenieZaChwile: Bool

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
            kategoria = Html.tekst(k.s("name")); kolor = k.s("color"); kategoriaSlug = k.s("slug")
        } else {
            kategoria = Html.tekst(j.s("category")); kolor = j.s("categoryColor"); kategoriaSlug = j.s("categorySlug")
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
        przetlumaczony = j.b("translated")
        tlumaczenieWToku = j.b("translationPending") && !j.b("translated")
        tlumaczenieRano = j.s("translationEta") == "morning"
        tlumaczenieZaChwile = tlumaczenieWToku && j.s("translationEta") == "now"
    }

    /// Artykuły sponsorowane są w aplikacji wyłączone (Karol, 22.11.2026), niezależnie od panelu.
    var sponsorowanyAlboZKategorii: Bool { sponsorowany || kategoriaSlug == "artykuly-sponsorowane" }
    static func widoczny(_ j: Slownik) -> Artykul? {
        let a = Artykul(j)
        return a.sponsorowanyAlboZKategorii ? nil : a
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
        f.locale = Jezyk.locale()
        f.unitsStyle = .full
        return f.localizedString(for: data, relativeTo: .now)
    }

    static let daty: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd HH:mm:ssX"
        return f
    }()

    // Równość i hash ze wszystkich pól (domyślne Hashable), a nie z samego id: SwiftUI uznaje wartość równą
    // za brak zmiany, więc przetłumaczony artykuł z tym samym id (tłumaczenie od razu) nie odświeżał ekranu.
    // Tożsamość na listach daje dalej Identifiable (id).
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

/// Efekt tła pogody liczony przez serwer (API 1.7.2, weather_effect.php): ten sam na stronie, w panelu i w aplikacji.
struct EfektPogody: Equatable {
    /// clear-day, clear-night, clouds, fog, drizzle, rain, rain-heavy, storm, snow, sleet, wind
    let rodzaj: String
    /// 0–1: gęstość cząstek, przy opadzie według natężenia ze stacji
    let natezenie: Double
    /// silny wiatr: krople skośnie, płatki znoszone, chmury szybciej
    let wietrznie: Bool
    /// jesienne liście jako dodatek (tylko jesienią i przy suchej pogodzie)
    let liscie: Bool
    /// day, dusk (do 45 min od wschodu albo zachodu), night
    let faza: String

    init?(_ j: Slownik) {
        guard !j.s("effect").isEmpty else { return nil }
        rodzaj = j.s("effect")
        natezenie = min(max(j.d("intensity") ?? 0.5, 0), 1)
        wietrznie = j.b("windy")
        liscie = j.b("leaves")
        faza = j.s("phase").isEmpty ? (j.b("isNight") ? "night" : "day") : j.s("phase")
    }
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
    /// Efekt tła z serwera (API 1.7.2); nil przy starszym API, wtedy scena według ikony.
    let efekt: EfektPogody?

    /// API v1 (state, isNight, daily, hourly w dniach) albo stare weather-full.
    init(_ j: Slownik) {
        efekt = EfektPogody(j)
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
        return String(format: "%.1f°", v).replacingOccurrences(of: ".", with: Jezyk.przecinek)
    }
}

/// Wszystko, co aplikacja pobiera z tvml.pl. Jedno źródło dla wszystkich ekranów.
@MainActor
final class Magazyn: ObservableObject {
    @Published var najnowsze: [Artykul] = []
    @Published var najczytniejsze: [Artykul] = []
    @Published var pasek: [Artykul] = []
    @Published var filmy: [Film] = []
    /// Następna strona filmów (/api/v1/videos, nextCursor); nil = to już wszystkie.
    @Published var filmyKursor: String?
    @Published var ladujeFilmy = false
    @Published var pogoda: Pogoda?
    @Published var motyw = Motyw.zwykly
    @Published var wKategorii: [String: [Artykul]] = [:]
    @Published var blad: String?
    @Published var laduje = false
    /// Język treści i napisów (pl, uk, en); zmiana przebudowuje ekrany.
    @Published var jezyk = Jezyk.biezacy
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
    /// Tematy powiadomień z config.push.topics (pusto = serwer bez push albo jeszcze nie pobrano).
    @Published var tematyPush: [TematPush] = []
    /// Scena karty pogody z serwera (theme.weatherScene i harmonogram upcomingThemes).
    @Published var wyborSceny = WyborSceny()
    /// Ustawienia scen z panelu według stanu pogody (config.weatherAnimation: skyColor, waterColor, fale…).
    @Published var ustawieniaScen: [String: Slownik] = [:]
    /// Zmienia się na granicy motywu (from/until) albo o północy, żeby karta przerysowała się z nową sceną.
    @Published var chwilaSceny = Date()
    private var zegarSceny: Task<Void, Never>?

    /// Tylko TestFlight (Więcej → Podgląd dnia): karta pogody i cała szata graficzna (motyw: kolory, pasek, logo)
    /// liczone na wybraną chwilę, tylko w pamięci (Karol 24.11: „szata graficzna aplikacji nie zmieniła się”).
    @Published var podgladDnia: Date? {
        didSet { motyw = podgladDnia.map { motywNa($0) } ?? motywZConfig }
    }
    /// Motyw z config na dziś (bez podglądu) oraz harmonogram motywów z etapami (theme + upcomingThemes).
    private(set) var motywZConfig: Motyw = .zwykly
    private var harmonogramMotywow: [Slownik] = []
    private var wersjaApiMotywu = "1.0.0"

    /// Motyw na daną chwilę z harmonogramu: okno (from/until), w nim trwający etap (stages: variant, ornament, kolory,
    /// pasek), zbudowany tak jak z config.theme. Poza oknami: dziś = motyw z config, inny dzień = zwykły.
    func motywNa(_ chwila: Date) -> Motyw {
        let iso = ISO8601DateFormatter()
        func trwa(_ t: Slownik) -> Bool {
            guard let od = iso.date(from: t.s("from")), let doKonca = iso.date(from: t.s("until")) else { return false }
            return od <= chwila && chwila <= doKonca
        }
        for t in harmonogramMotywow where trwa(t) {
            var th: Slownik = t
            if let etap = t.lista("stages").first(where: { trwa($0) }) {
                for (k, v) in etap where k != "id" && k != "from" && k != "until" { th[k] = v }
            }
            if let m = Motyw.zV1(th, wersjaApi: wersjaApiMotywu, teraz: chwila) { return m }
        }
        return Calendar.current.isDateInToday(chwila) ? motywZConfig : .zwykly
    }

    /// Scena teraz: z okna harmonogramu, jeśli trwa, inaczej bieżąca z serwera (w TestFlight: na dzień z podglądu).
    var aktywnaScena: WyborSceny.Scena { wyborSceny.scena(na: podgladDnia ?? max(chwilaSceny, Date())) }

    /// Na najbliższej granicy (początek albo koniec motywu, północ w Warszawie) przełącza scenę od razu
    /// i pobiera config, bo etapy motywu (np. Mikołajki → Święta) i tło po motywie liczy serwer.
    func zaplanujZmianeSceny() {
        zegarSceny?.cancel()
        guard let kiedy = wyborSceny.nastepnaZmiana(po: Date()) else { return }
        zegarSceny = Task { [weak self] in
            let sekundy = max(1, kiedy.timeIntervalSinceNow)
            try? await Task.sleep(nanoseconds: UInt64(sekundy * 1_000_000_000))
            guard !Task.isCancelled, let self else { return }
            self.chwilaSceny = Date()
            // odswiezV1 woła zaplanujZmianeSceny, a ta anulowała to zadanie w połowie odświeżania (część danych
            // kończyła się anulowaniem i napisem „Bez internetu”); zadanie już nie jest do anulowania
            self.zegarSceny = nil
            await self.odswiez(wymus: true)
        }
    }
    /// Komunikat redakcji z panelu (config.message) i prośba o aktualizację (config.minAppVersion.ios), od API 1.2.
    @Published var komunikat: Komunikat?
    /// Kategoria artykułów subportalu z panelu (config.subportals[].category, pusta = slug), np. muzyka → muzyka-tvml.
    private var kategoriePortali: [String: String] = [:]
    /// Ogłoszenia drobne zatwierdzone w panelu (/api/v1/classifieds, API 1.4).
    @Published var ogloszenia: [Ogloszenie] = []
    /// Kalendarz wydarzeń (/api/v1/events, API 1.7), gdy panel go włączy.
    @Published var wydarzenia: [Wydarzenie] = []
    /// Podstrony redakcji do „Więcej” (config.pages), w kolejności z serwera.
    @Published var strony: [Podstrona] = Podstrona.domyslne
    /// Podpis w stopce „Więcej” (config.about.footer).
    @Published var stopka = "Telewizja TVML · Leszno"
    /// Etykieta paska pod banerem (config.ticker.label) i czy to transmisja (config.ticker.live).
    @Published var etykietaPaska = "NAJNOWSZE"
    @Published var pasekNaZywo = false
    /// Podstrony z prawdziwą treścią (bez pustych i „Wkrótce.”); nil = jeszcze nie sprawdzone.
    @Published var stronyGotowe: Set<String>?
    /// Czy w „Więcej” pokazać ogłoszenia i fotorelacje (tylko gdy coś w nich jest).
    @Published var maOgloszenia = false
    @Published var maFotorelacje = true
    /// Treść podstron pobrana przy sprawdzaniu, żeby otwierały się od razu.
    private var trescStron: [String: TrescPodstrony] = [:]
    /// Blok poll: trwająca sonda albo kalendarz wydarzeń (klucz: id bloku).
    @Published var spotlight: [String: Spotlight] = [:]
    /// Głosy oddane z tego telefonu (id sondy → odpowiedź), tylko lokalnie.
    @Published var glosy: [String: String] = (UserDefaults.standard.dictionary(forKey: "tvml-glosy") as? [String: String]) ?? [:] {
        didSet { UserDefaults.standard.set(glosy, forKey: "tvml-glosy") }
    }
    @Published var wymaganaAktualizacja = false
    /// Bez internetu: z kiedy są pokazywane dane z pamięci telefonu (nil = świeże z sieci).
    @Published var daneZPamieci: Date?
    /// Nowsza wersja w App Store: łagodna prośba o aktualizację pod paskiem „NA ŻYWO” (do zamknięcia).
    @Published var nowaWersja: Komunikat?
    @Published var zamknieteKomunikaty: [String] = UserDefaults.standard.stringArray(forKey: "tvml-zamkniete-komunikaty") ?? [] {
        didSet { UserDefaults.standard.set(Array(zamknieteKomunikaty.suffix(50)), forKey: "tvml-zamkniete-komunikaty") }
    }
    /// Podgląd szkicu aplikacji z panelu (kod QR, Linki.swift): tylko w pamięci, najwyżej 30 minut.
    @Published var podglad: Podglad? {
        didSet { if podglad != oldValue { zaplanujKoniecPodgladu() } }
    }
    /// Okno „Podgląd zakończony” po wygaśnięciu albo zakończeniu podglądu w panelu.
    @Published var podgladWygasl = false
    var zegarPodgladu: Task<Void, Never>?
    @Published var arkuszPogody = false
    @Published var arkuszWideo = false
    @Published var zapisane: [Int] = UserDefaults.standard.array(forKey: "tvml-zapisane") as? [Int] ?? [] {
        didSet { UserDefaults.standard.set(zapisane, forKey: "tvml-zapisane") }
    }
    private var ostatnio: Date?

    struct Dzisiaj: Equatable { let etykieta: String; let imieniny: [String]; var swieto: String? = nil }

    struct KategoriaMenu: Hashable {
        let nazwa: String
        let slug: String?
        static let domyslne: [KategoriaMenu] = [
            .init(nazwa: "Wszystko", slug: nil), .init(nazwa: "Aktualności", slug: "aktualnosci"), .init(nazwa: "Sport", slug: "sport"),
            .init(nazwa: "Na sygnale", slug: "na-sygnale"), .init(nazwa: "Kultura", slug: "kultura"), .init(nazwa: "Muzyka", slug: "muzyka")
        ]
    }

    func odswiez(wymus: Bool = false) async {
        // przy podglądzie z panelu bez przerwy 2 minut: redakcja zmienia szkic i od razu patrzy na telefon
        if !wymus, podglad == nil, let ostatnio, Date().timeIntervalSince(ostatnio) < 120 { return }
        laduje = true
        defer { laduje = false }
        let zPamieci = Siec.ZPamieci()
        let udane = await Siec.$zPamieci.withValue(zPamieci) { await odswiezV1() }
        // przerwane odświeżenie (np. nowe w jego miejsce) nie zmienia napisu „Bez internetu”
        if Task.isCancelled { return }
        if udane {
            blad = nil
            // dane z pamięci telefonu: napis „Bez internetu” na Starcie i bez przerwy 2 minut przed kolejną próbą
            daneZPamieci = zPamieci.najstarsza
            if daneZPamieci == nil { ostatnio = Date() }
            await sprawdzNowaWersje()
            return
        }
        await odswiezStare()
        await sprawdzNowaWersje()
    }

    /// Najnowsza wersja TVML w App Store (wyszukiwarka Apple, bez żadnych danych o telefonie). Najpierw polski sklep,
    /// a gdy go brak, brytyjski i amerykański: do zatwierdzenia DSA przez Apple TVML nie ma w sklepach UE i polski
    /// zwracał pustą listę, więc baner „Nowa wersja” się nie pokazywał (Karol 25.11: „Tak, popraw”).
    func sprawdzNowaWersje() async {
        var najnowsza: String?
        for kraj in ["pl", "gb", "us"] {
            guard let adres = URL(string: "https://itunes.apple.com/lookup?bundleId=com.LiveProduction.TVML&country=" + kraj),
                  let odp = try? await URLSession.shared.data(from: adres),
                  let j = (try? JSONSerialization.jsonObject(with: odp.0)) as? Slownik,
                  let w = j.lista("results").first?.s("version"), !w.isEmpty else { continue }
            najnowsza = w
            break
        }
        guard let w = najnowsza, Wersja.starsza(Wersja.biezaca, niz: w) else { nowaWersja = nil; return }
        nowaWersja = Komunikat(["id": "aktualizacja-" + w, "title": Napisy.t("app.newVersion", "Nowa wersja TVML {v}").replacingOccurrences(of: "{v}", with: w),
                                "text": Napisy.t("app.updateAvailable", "Zaktualizuj aplikację w App Store, żeby mieć najnowsze funkcje."), "level": "info",
                                "link": "https://apps.apple.com/app/id1468277905", "linkLabel": Napisy.t("app.update", "Aktualizuj")])
    }

    /// Nowe API (/api/v1): config, artykuły, najczęściej czytane, wideo, pogoda.
    private func odswiezV1() async -> Bool {
        // features=ai-themes: także motywy z panelu ze sceną SVG (weatherSceneType "svg" i adres obrazu)
        // przy podglądzie z kodu QR: szkic ustawień aplikacji z panelu (Linki.swift)
        async let k = konfiguracja()
        async let a = Siec.v1("articles", ["limit": "20"])
        async let b = Siec.v1("articles/most-read", ["limit": "10"])
        async let d = Siec.v1("videos", ["limit": "15"])
        async let e = Siec.v1("weather")
        let (konfOdp, tokenPodgladu) = await k
        // podgląd bez odpowiedzi serwera: zostaje to, co już widać
        if konfOdp == nil, tokenPodgladu != nil { return true }
        guard let lista = (try? await a) as? Slownik, let konf = konfOdp else { return false }
        // w trakcie pobierania zaczął się albo skończył podgląd: wynik wpisze tamto odświeżenie
        guard podglad?.token == tokenPodgladu else { return true }
        // API 1.8.0: języki włączone w panelu i napisy aplikacji (config.i18n), zapisane też na czas bez sieci
        if let l = konf["languages"] as? [String] { Jezyk.ustawDostepne(l) }
        Napisy.ustaw(konf.slownik("i18n"))
        // formularz „Zgłoś zdarzenie” w aplikacji (config.alerts.native); bez klucza dalej tvml.pl/#alert
        UstawieniaAlertu.ustaw(konf["alerts"] as? Slownik)
        // push o nowym wpisie: tematy z panelu, gdy serwer ma powiadomienia włączone
        let push = konf.slownik("push")
        tematyPush = push.b("enabled") ? push.lista("topics").compactMap(TematPush.init) : []
        // scena karty pogody jak na stronie: teraz theme.weatherScene, później upcomingThemes (przełączenie o czasie)
        wyborSceny = WyborSceny(konf)
        ustawieniaScen = (konf["weatherAnimation"] as? [String: Slownik]) ?? [:]
        zaplanujZmianeSceny()
        if jezyk != Jezyk.biezacy { jezyk = Jezyk.biezacy }
        najnowsze = lista.lista("items").compactMap(Artykul.widoczny)
        harmonogramMotywow = [konf.slownik("theme")] + konf.lista("upcomingThemes")
        wersjaApiMotywu = konf.s("apiVersion")
        if let m = Motyw.zV1(konf.slownik("theme"), wersjaApi: konf.s("apiVersion")) { motywZConfig = m }
        // TestFlight, podgląd dnia: motyw liczony na wybraną chwilę, odświeżenie go nie nadpisuje
        motyw = podgladDnia.map { motywNa($0) } ?? motywZConfig
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
        pasek = konf.slownik("ticker").lista("items").compactMap(Artykul.widoczny)
        let pasekKonf = konf.slownik("ticker")
        if !pasekKonf.s("label").isEmpty { etykietaPaska = pasekKonf.s("label") }
        pasekNaZywo = pasekKonf.b("live")
        let listaStron = konf.lista("pages").map(Podstrona.init).filter { !$0.slug.isEmpty && $0.doPokazania }
        if !listaStron.isEmpty { strony = listaStron }
        if !konf.slownik("about").s("footer").isEmpty { stopka = konf.slownik("about").s("footer") }
        gminy = konf.lista("counties").map(Gmina.init).sorted { $0.kolejnosc < $1.kolejnosc }
        for s in konf.lista("subportals") where !s.s("slug").isEmpty {
            kategoriePortali[s.s("slug")] = s.s("category").isEmpty ? s.s("slug") : s.s("category")
        }
        let sub = konf.lista("subportals").map { s in
            // „TVML.pl - Sport” albo po tłumaczeniu „TVML.pl – Спорт” (półpauza): na przycisku sama nazwa działu
            KategoriaMenu(nazwa: s.s("name").replacingOccurrences(of: #"^TVML\.pl\s*[-–—]\s*"#, with: "", options: .regularExpression), slug: s.s("slug"))
        }
        let aktualnosci = konf.lista("categories").first { $0.s("slug") == "aktualnosci" }.map { Html.tekst($0.s("name")) } ?? ""
        if !sub.isEmpty {
            kategorie = [.init(nazwa: Napisy.t("app.all", "Wszystko"), slug: nil),
                         .init(nazwa: aktualnosci.isEmpty ? "Aktualności" : aktualnosci, slug: "aktualnosci")] + sub
        }
        let d0 = konf.slownik("today")
        if !d0.s("label").isEmpty {
            // holiday: null albo nazwa święta (na wszelki wypadek także obiekt z polem name)
            let swieto = (d0["holiday"] as? String) ?? (d0["holiday"] as? Slownik)?.s("name")
            dzisiaj = Dzisiaj(etykieta: d0.s("label"), imieniny: (d0["nameDays"] as? [String]) ?? [],
                              swieto: swieto?.trimmingCharacters(in: .whitespaces).isEmpty == false ? swieto : nil)
        }
        komunikat = Komunikat(konf.slownik("message"))
        let minimalna = konf.slownik("minAppVersion").s("ios")
        wymaganaAktualizacja = !minimalna.isEmpty && Wersja.starsza(Wersja.biezaca, niz: minimalna)
        if let j = (try? await b) as? Slownik { najczytniejsze = j.lista("items").compactMap(Artykul.widoczny) }
        if let j = (try? await d) as? Slownik {
            filmy = j.lista("items").map(Film.init)
            filmyKursor = j.s("nextCursor").isEmpty ? nil : j.s("nextCursor")
        }
        if let j = (try? await e) as? Slownik, j["current"] != nil { pogoda = Pogoda(j) }
        // listy bloków Start z kategorią albo flagą i fotorelacje, odświeżane razem z resztą
        for b in bloki where b.typ == "posts" { await wpisy(b) }
        if let g = bloki.first(where: { $0.typ == "gallery" }) { await fotorelacje(limit: g.limit) }
        if let o = bloki.first(where: { $0.typ == "classifieds" }),
           let j = (try? await Siec.v1("classifieds", ["limit": String(o.limit > 0 ? o.limit : 4)])) as? Slownik {
            ogloszenia = j.lista("items").map(Ogloszenie.init)
        }
        if konf.slownik("modules").b("events") || bloki.contains(where: { $0.typ == "events" }) {
            if let j = (try? await Siec.v1("events", ["limit": "20"])) as? Slownik {
                wydarzenia = j.lista("items").map(Wydarzenie.init)
            }
        } else {
            wydarzenia = []
        }
        for b in bloki where b.typ == "poll" { await wczytajSpotlight(b) }
        return true
    }

    /// Kolejne 15 filmów w zakładce Wideo po stuknięciu „Starsze filmy” (cursor=nextCursor). Archiwum na serwerze ma
    /// od 25.11 także filmy z artykułów, ok. 140 (Karol: „miało wczytywac filmy po 15 i dac opcje wczytania kolejnych
    /// a nie od razu wszytskie”).
    func wiecejFilmow() async {
        guard let kursor = filmyKursor, !ladujeFilmy else { return }
        ladujeFilmy = true
        defer { ladujeFilmy = false }
        guard let j = (try? await Siec.v1("videos", ["limit": "15", "cursor": kursor])) as? Slownik,
              filmyKursor == kursor else { return }
        let znane = Set(filmy.map(\.id))
        filmy += j.lista("items").map(Film.init).filter { !znane.contains($0.id) }
        filmyKursor = j.s("nextCursor").isEmpty ? nil : j.s("nextCursor")
    }

    /// Wpisy bloku posts z kategorią albo flagą z panelu.
    func wpisy(_ b: BlokStartu) async {
        guard let klucz = b.klucz else { return }
        var p = ["limit": String(b.limit > 0 ? b.limit : 4)]
        if let k = b.kategoria { p["category"] = k }
        if let f = b.filtr { p[f] = "1" }
        if let j = (try? await Siec.v1("articles", p)) as? Slownik { wKategorii[klucz] = j.lista("items").compactMap(Artykul.widoczny) }
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
            najnowsze = ((try await a) as? [Slownik] ?? []).compactMap(Artykul.widoczny)
            blad = nil
            ostatnio = Date()
        } catch {
            if najnowsze.isEmpty { blad = Napisy.t("app.loadError", "Nie udało się pobrać wiadomości. Sprawdź internet i przeciągnij w dół, żeby spróbować ponownie.") }
        }
        if let lista = (try? await b) as? [Slownik] { najczytniejsze = lista.compactMap(Artykul.widoczny) }
        if let lista = (try? await c) as? [Slownik] { pasek = lista.compactMap(Artykul.widoczny) }
        if let j = (try? await d) as? Slownik { filmy = j.lista("items").map(Film.init) }
        if let j = (try? await e) as? Slownik, j["current"] != nil { pogoda = Pogoda(j) }
        if let j = (try? await f) as? Slownik { motyw = Motyw.wylicz(z: j) }
    }

    func kategoria(_ slug: String, limit: Int = 20) async {
        // subportal ma w panelu własną kategorię artykułów (Muzyka: muzyka-tvml); lista zostaje pod slugiem
        let kat = kategoriePortali[slug] ?? slug
        if let j = (try? await Siec.v1("articles", ["category": kat, "limit": String(limit)])) as? Slownik {
            wKategorii[slug] = j.lista("items").compactMap(Artykul.widoczny)
            return
        }
        guard let lista = (try? await Siec.json("tvml-db.php?action=news&category=\(kat)&limit=\(limit)")) as? [Slownik] else { return }
        wKategorii[slug] = lista.compactMap(Artykul.widoczny)
    }

    func gmina(_ g: Gmina) async -> [Artykul] {
        guard let j = (try? await Siec.v1("articles", ["county": g.id, "limit": "20"])) as? Slownik else { return [] }
        return j.lista("items").compactMap(Artykul.widoczny)
    }

    /// Treść podstrony redakcji (/api/v1/pages/<slug>); nil przy błędzie sieci albo nieznanej stronie.
    func podstrona(_ slug: String) async -> TrescPodstrony? {
        if let t = trescStron[slug] { return t }
        guard let j = (try? await Siec.v1("pages/" + slug)) as? Slownik, j["html"] != nil else { return nil }
        let t = TrescPodstrony(j)
        trescStron[slug] = t
        return t
    }

    /// Raz po starcie: które podstrony mają treść (Karol, 22.11: tego, co w budowie, nie publikujemy)
    /// i czy są ogłoszenia oraz fotorelacje. Bez sieci zostaje to, co było.
    func sprawdzStrony() async {
        var gotowe = Set<String>()
        var odpowiedzi = 0
        for s in strony {
            guard let j = (try? await Siec.v1("pages/" + s.slug)) as? Slownik, j["html"] != nil else { continue }
            odpowiedzi += 1
            let t = TrescPodstrony(j)
            trescStron[s.slug] = t
            if Html.tekst(t.html).count >= 40 { gotowe.insert(s.slug) }
        }
        if odpowiedzi > 0 { stronyGotowe = gotowe }
        if let j = (try? await Siec.v1("classifieds", ["limit": "1"])) as? Slownik { maOgloszenia = !j.lista("items").isEmpty }
        if let j = (try? await Siec.v1("articles", ["photoReport": "1", "limit": "1"])) as? Slownik { maFotorelacje = !j.lista("items").isEmpty }
    }

    /// Blok poll: sonda, gdy trwa, a bez niej kalendarz wydarzeń. Przy błędzie sieci zostaje poprzedni stan.
    func wczytajSpotlight(_ b: BlokStartu) async {
        var p = ["limit": "12"]
        if let id = b.ankieta { p["pollId"] = id }
        guard let j = (try? await Siec.v1("spotlight", p)) as? Slownik else { return }
        if j.s("type") == "poll", j["poll"] is Slownik {
            spotlight[b.id] = .sonda(Sonda(j.slownik("poll")))
        } else {
            spotlight[b.id] = .kalendarz(KalendarzSpot(j.slownik("events")))
        }
    }

    /// Głos w sondzie (POST /api/v1/polls/{id}/vote). Zwraca komunikat dla czytelnika albo nil.
    func glosuj(sonda: Sonda, klucz: String, blok: BlokStartu) async -> String? {
        guard let wynik = try? await Siec.v1Wyslij("polls/" + sonda.id + "/vote", ["option": klucz]) else {
            return Napisy.t("app.voteOffline", "Brak połączenia. Spróbuj jeszcze raz.")
        }
        let (kod, j) = wynik
        if kod == 200 {
            glosy[sonda.id] = klucz
            if j["poll"] is Slownik { spotlight[blok.id] = .sonda(Sonda(j.slownik("poll"))) }
            return j.b("counted") || j.s("message").isEmpty ? nil : j.s("message")
        }
        if kod == 409 {
            await wczytajSpotlight(blok)
            return Napisy.t("app.pollClosed", "Głosowanie w tej sondzie zostało zakończone.")
        }
        let blad = j.slownik("error").s("message")
        return blad.isEmpty ? Napisy.t("app.voteError", "Nie udało się oddać głosu.") : blad
    }

    /// Wydarzenia jednego dnia (/api/v1/events?from=D&to=D, API 1.7); nil, gdy serwer jeszcze tego nie ma.
    func wydarzeniaDnia(_ d: String) async -> [Wydarzenie]? {
        guard let j = (try? await Siec.v1("events", ["from": d, "to": d, "limit": "50"])) as? Slownik else { return nil }
        return j.lista("items").map(Wydarzenie.init)
    }

    /// Szczegóły wydarzenia (/api/v1/events/{id}, API 1.7); nil, gdy serwer jeszcze tego nie ma.
    func wydarzenie(_ id: Int) async -> Wydarzenie? {
        guard let j = (try? await Siec.v1("events/\(id)")) as? Slownik, j["id"] != nil else { return nil }
        return Wydarzenie(j)
    }

    /// Ogłoszenia drobne do ekranu z „Więcej”.
    func wszystkieOgloszenia() async -> [Ogloszenie] {
        guard let j = (try? await Siec.v1("classifieds", ["limit": "50"])) as? Slownik else { return ogloszenia }
        return j.lista("items").map(Ogloszenie.init)
    }

    /// Fotorelacje do ekranu z „Więcej” (osobno od bloku na Start).
    func listaFotorelacji() async -> [Artykul] {
        guard let j = (try? await Siec.v1("articles", ["photoReport": "1", "limit": "30"])) as? Slownik else { return [] }
        return j.lista("items").compactMap(Artykul.widoczny)
    }

    func fotorelacje(limit: Int) async {
        guard let j = (try? await Siec.v1("articles", ["photoReport": "1", "limit": String(max(limit, 4))])) as? Slownik else { return }
        wKategorii["_fotorelacje"] = j.lista("items").compactMap(Artykul.widoczny)
    }

    /// jezyk: "pl" = polski oryginał przetłumaczonego artykułu (app.originalPolish)
    func artykul(_ id: Int, jezyk: String? = nil) async -> Artykul? {
        // zawsze z serwera: stan tłumaczenia (translated, translationEta) zmienia się szybciej niż max-age=300
        if let j = (try? await Siec.v1("articles/\(id)", jezyk.map { ["lang": $0] } ?? [:], swiezy: true)) as? Slownik, j["id"] != nil { return Artykul(j) }
        guard let j = (try? await Siec.json("tvml-db.php?action=article&id=\(id)")) as? Slownik, j["id"] != nil else { return nil }
        return Artykul(j)
    }

    /// Zapisane artykuły na liście. Pobranie szczegółów w języku uk/en zamawia tłumaczenie (PR #94, tłumaczenie na żądanie),
    /// więc lista bierze je z /articles?ids= (API 1.8.1, bez zamawiania), a przy starszym serwerze szczegóły po polsku.
    /// Tłumaczenie zamawia dopiero otwarcie artykułu.
    func zapisaneArtykuly() async -> [Artykul] {
        let ids = Array(zapisane.prefix(30))
        guard !ids.isEmpty else { return [] }
        if let j = (try? await Siec.v1("articles", ["ids": ids.map(String.init).joined(separator: ","), "limit": String(ids.count)])) as? Slownik {
            let lista = j.lista("items").map(Artykul.init)
            if !lista.isEmpty && lista.allSatisfy({ ids.contains($0.id) }) {
                return ids.compactMap { id in lista.first { $0.id == id } }
            }
        }
        var wynik: [Artykul] = []
        for id in ids {
            if let a = await artykul(id, jezyk: Jezyk.polski ? nil : "pl") { wynik.append(a) }
        }
        return wynik
    }

    func szukaj(_ fraza: String) async -> [Artykul] {
        if let j = (try? await Siec.v1("search", ["q": fraza, "limit": "20"])) as? Slownik { return j.lista("items").compactMap(Artykul.widoczny) }
        let q = fraza.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
        guard !q.isEmpty, let lista = (try? await Siec.json("tvml-db.php?action=search&q=\(q)&limit=20")) as? [Slownik] else { return [] }
        return lista.compactMap(Artykul.widoczny)
    }

    /// Zmiana języka w „Więcej”: odpowiedzi API różnią się językiem, więc wszystko pobiera się od nowa, bez restartu.
    func zmienJezyk() async {
        let nowy = Jezyk.biezacy
        guard nowy != jezyk else { return }
        jezyk = nowy
        wKategorii = [:]
        trescStron = [:]
        spotlight = [:]
        await odswiez(wymus: true)
        await sprawdzStrony()
        // powiadomienia w nowym języku (pole lang w POST devices)
        Powiadomienia.shared.zglos()
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

    static var wiecej: ZakladkaApp { ZakladkaApp(id: "more", tytul: Napisy.t("app.tab.more", "Więcej"), ikona: "square.grid.2x2", cel: .wiecej) }
    static var domyslne: [ZakladkaApp] { [
        ZakladkaApp(id: "home", tytul: Napisy.t("app.tab.home", "Start"), ikona: "house", cel: .start),
        ZakladkaApp(id: "sport", tytul: Napisy.t("app.tab.sport", "Sport"), ikona: "sportscourt", cel: .kategoria("sport")),
        ZakladkaApp(id: "video", tytul: Napisy.t("app.tab.video", "Wideo"), ikona: "play.rectangle", cel: .wideo),
        ZakladkaApp(id: "weather", tytul: Napisy.t("app.tab.weather", "Pogoda"), ikona: "cloud.sun", cel: .pogoda),
        wiecej
    ] }

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
        etykieta = j.s("linkLabel").isEmpty ? Napisy.t("app.readMore", "Więcej") : j.s("linkLabel")
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
    /// Sonda bloku poll (props.pollId); nil = pierwsza z układu strony.
    let ankieta: String?
    /// Blok banner (API 1.10.0): obraz, tytuł, tekst i przycisk z panelu, stuknięcie otwiera url.
    var baner: Baner? = nil

    /// Baner Startu ustawiany w panelu (Karol 28.11: „trzeba dac w nowej wersji opcje tez ze mozamy dodac jakis baner
    /// gdzie podzie jakas podstorna”). Serwer wysyła blok tylko w oknie from–until.
    struct Baner: Hashable {
        let obraz: URL?
        /// szerokość / wysokość obrazu (imageRatio), domyślnie 2,5
        let proporcja: Double
        let tytul: String?
        let tekst: String?
        let przycisk: String?
        let adres: URL

        init?(_ p: Slownik) {
            guard let adres = URL(string: p.s("url")), adres.scheme == "https" || adres.scheme == "http" else { return nil }
            self.adres = adres
            obraz = p.s("image").isEmpty ? nil : URL(string: p.s("image"))
            let r = p.d("imageRatio") ?? 2.5
            proporcja = r > 0.3 && r < 10 ? r : 2.5
            func tekstLubNil(_ k: String) -> String? {
                let t = Html.tekst(p.s(k)).trimmingCharacters(in: .whitespacesAndNewlines)
                return t.isEmpty ? nil : t
            }
            tytul = tekstLubNil("title")
            tekst = tekstLubNil("text")
            przycisk = tekstLubNil("button")
        }
    }

    static let domyslne: [BlokStartu] = [
        BlokStartu(id: "hero", typ: "hero", tytul: "", ukryjTytul: true, limit: 5, kategoria: nil),
        BlokStartu(id: "weather", typ: "weather", tytul: "Pogoda", ukryjTytul: true, limit: 0, kategoria: nil),
        BlokStartu(id: "posts", typ: "posts", tytul: "Aktualności", ukryjTytul: false, limit: 10, kategoria: nil),
        BlokStartu(id: "mostread", typ: "mostread", tytul: Napisy.t("app.mostRead", "Najczęściej czytane"), ukryjTytul: false, limit: 5, kategoria: nil),
        BlokStartu(id: "video", typ: "video", tytul: Napisy.t("app.video", "Wideo"), ukryjTytul: false, limit: 6, kategoria: nil)
    ]

    init(id: String, typ: String, tytul: String, ukryjTytul: Bool, limit: Int, kategoria: String?, filtr: String? = nil, ankieta: String? = nil) {
        self.id = id; self.typ = typ; self.tytul = tytul; self.ukryjTytul = ukryjTytul; self.limit = limit; self.kategoria = kategoria
        self.filtr = filtr; self.ankieta = ankieta
    }

    init?(_ j: Slownik) {
        let p = j.slownik("props")
        let typ = j.s("type")
        guard ["hero", "weather", "alert", "video", "posts", "mostread", "gallery", "counties", "social", "classifieds", "events", "poll", "banner"].contains(typ) else { return nil }
        // artykuły sponsorowane są w aplikacji wyłączone (Karol, 22.11.2026): bez bloku sponsored i bez list sponsorowanych
        if p.s("category") == "artykuly-sponsorowane" || p.b("sponsored") { return nil }
        self.typ = typ
        id = j.s("id").isEmpty ? UUID().uuidString : j.s("id")
        tytul = j.s("title")
        ukryjTytul = p.b("hideTitle")
        limit = p.i("limit")
        kategoria = p.s("category").isEmpty ? nil : p.s("category")
        filtr = ["featured", "urgent", "photoReport"].first { p.b($0) }
        ankieta = p.s("pollId").isEmpty ? nil : p.s("pollId")
        if typ == "banner" {
            // baner bez poprawnego adresu nie ma sensu
            guard let b = Baner(p) else { return nil }
            baner = b
        }
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

/// Podstrona redakcji z config.pages (O nas, Kontakt, Regulamin…), w kolejności z panelu.
struct Podstrona: Identifiable, Hashable {
    let slug: String
    let tytul: String
    let adres: URL?
    let adresApp: URL?
    /// Sekcja w „Więcej” z serwera (API 1.7.1): „tvml” albo „dokumenty”; pusta = według listy w aplikacji.
    let sekcja: String
    /// API 1.7.1: ready=false (pusta, „Wkrótce”, w budowie) i app=false (tylko www) aplikacja pomija.
    let doPokazania: Bool
    var id: String { slug }

    init(slug: String, tytul: String) {
        self.slug = slug
        self.tytul = tytul
        adres = URL(string: "https://tvml.pl/strona/" + slug)
        adresApp = URL(string: "https://tvml.pl/strona/" + slug + "?widok=app")
        sekcja = ""
        doPokazania = true
    }

    init(_ j: Slownik) {
        slug = j.s("slug")
        tytul = Html.tekst(j.s("title"))
        adres = URL(string: j.s("url"))
        adresApp = URL(string: j.s("appUrl"))
        sekcja = j.s("section")
        doPokazania = (j["ready"] == nil || j.b("ready")) && (j["app"] == nil || j.b("app"))
    }

    /// Bez API (stary serwer albo brak sieci przy pierwszym starcie).
    static let domyslne: [Podstrona] = [
        Podstrona(slug: "o-nas", tytul: "O nas"), Podstrona(slug: "redakcja", tytul: "Redakcja"),
        Podstrona(slug: "reklama", tytul: "Reklama"), Podstrona(slug: "kontakt", tytul: "Kontakt"),
        Podstrona(slug: "regulamin", tytul: "Regulamin"), Podstrona(slug: "polityka-prywatnosci", tytul: "Polityka prywatności")
    ]
}

/// Treść podstrony z /api/v1/pages/<slug>: bezpieczny HTML z panelu (p, h2–h4, a, listy, obrazy, tabele).
struct TrescPodstrony {
    let slug: String
    let tytul: String
    let html: String
    let aktualizacja: Date?

    init(_ j: Slownik) {
        slug = j.s("slug")
        tytul = Html.tekst(j.s("title"))
        html = j.s("html")
        aktualizacja = ISO8601DateFormatter().date(from: j.s("updatedAt"))
    }
}

/// Wydarzenie z kalendarza tvml.pl (/api/v1/events i spotlight; szczegóły z /api/v1/events/{id}, API 1.7).
/// Tylko zatwierdzone, od najbliższego, także trwające.
struct Wydarzenie: Identifiable, Hashable {
    let id: Int
    let tytul: String
    let poczatek: Date?
    let koniec: Date?
    let calyDzien: Bool
    let miejsce: String
    let adres: URL?
    let artykulId: Int?
    let obraz: URL?
    /// Dzień rozpoczęcia, np. „2026-11-23” (do zawężania listy w kalendarzu).
    let dzienISO: String
    let opis: String
    let organizator: String
    let bilety: String

    init(_ j: Slownik) {
        id = j.i("id")
        tytul = Html.tekst(j.s("title"))
        let iso = ISO8601DateFormatter()
        poczatek = iso.date(from: j.s("startsAt"))
        koniec = j.s("endsAt").isEmpty ? nil : iso.date(from: j.s("endsAt"))
        calyDzien = j.b("allDay")
        miejsce = Html.tekst(j.s("place").isEmpty ? j.s("location") : j.s("place"))
        // kanoniczny adres to /wydarzenia?id=N (wersja .html to tylko przekierowanie)
        adres = URL(string: j.s("url").replacingOccurrences(of: "wydarzenia.html", with: "wydarzenia"))
        artykulId = j.i("articleId") > 0 ? j.i("articleId") : nil
        obraz = Wydarzenie.obrazek(j)
        let dzien = j.s("date")
        dzienISO = dzien.isEmpty ? (poczatek.map { Wydarzenie.fISO.string(from: $0) } ?? "") : dzien
        opis = j.s("description")
        organizator = Html.tekst(j.s("organizer"))
        bilety = Html.tekst(j.s("tickets"))
    }

    /// Miniatura jak na stronie: wariant co najmniej 200 px, a bez wariantów oryginał.
    private static func obrazek(_ j: Slownik) -> URL? {
        if let o = j["image"] as? Slownik {
            var w: [Int: String] = [:]
            for (k, v) in o.slownik("variants") { if let n = Int(k), let s = v as? String { w[n] = s } }
            if let k = w.keys.sorted().first(where: { $0 >= 200 }) ?? w.keys.max(), let s = w[k] { return URL(string: s) }
            return o.s("url").isEmpty ? nil : URL(string: o.s("url"))
        }
        return j.s("image").isEmpty ? nil : URL(string: j.s("image"))
    }

    /// „trwa”, „cały dzień” albo godzina rozpoczęcia, np. „18:00”.
    var godzina: String {
        let teraz = Date()
        if let p = poczatek, p < teraz, (koniec ?? p) > teraz { return Napisy.t("app.eventNow", "trwa") }
        if calyDzien { return Napisy.t("app.eventAllDay", "cały dzień") }
        return poczatek.map { Wydarzenie.fGodzina.string(from: $0) } ?? ""
    }
    var dzien: String { poczatek.map { Wydarzenie.fDzien.string(from: $0) } ?? "" }
    var miesiac: String { poczatek.map { Wydarzenie.fMiesiac.string(from: $0).uppercased() } ?? "" }
    var dzienTygodnia: String { poczatek.map { Wydarzenie.fTydzien.string(from: $0) } ?? "" }

    private static var kalendarz: Calendar {
        var k = Calendar(identifier: .gregorian)
        k.timeZone = TimeZone(identifier: "Europe/Warsaw") ?? .current
        return k
    }

    /// Koniec do pokazania: całodniowe kończą się często o północy następnego dnia, wtedy ostatni dzień to dzień wcześniej.
    var koniecDnia: Date? {
        guard let k = koniec else { return nil }
        let c = Wydarzenie.kalendarz.dateComponents([.hour, .minute, .second], from: k)
        if calyDzien && c.hour == 0 && c.minute == 0 && c.second == 0 { return k.addingTimeInterval(-1) }
        return k
    }

    /// Wydarzenie na kilka dni (np. wystawa): koniec innego dnia niż początek.
    var wielodniowe: Bool {
        guard let p = poczatek, let k = koniecDnia else { return false }
        return !Wydarzenie.kalendarz.isDate(k, inSameDayAs: p)
    }

    /// Wielodniowe, które w danym dniu już trwa: zaczęło się wcześniej i jeszcze się nie skończyło.
    func trwaW(_ dzien: Date) -> Bool {
        guard wielodniowe, let p = poczatek, let k = koniecDnia else { return false }
        let start = Wydarzenie.kalendarz.startOfDay(for: dzien)
        return p < start && k >= start
    }

    /// „20 lis – 5 gru” (wielodniowe).
    var zakres: String {
        guard let p = poczatek, let k = koniecDnia else { return "" }
        return Wydarzenie.fKrotka.string(from: p) + " – " + Wydarzenie.fKrotka.string(from: k)
    }

    /// „sobota, 28 listopada 2026 · 18:00–20:00” albo „… · cały dzień”; wielodniowe:
    /// „piątek, 20 listopada 2026 – sobota, 5 grudnia 2026”.
    var dataPelna: String {
        guard let p = poczatek else { return "" }
        if wielodniowe, let k = koniecDnia {
            let od = Wydarzenie.fPelna.string(from: p) + (calyDzien ? "" : " " + Wydarzenie.fGodzina.string(from: p))
            let doKonca = Wydarzenie.fPelna.string(from: k) + (calyDzien ? "" : " " + Wydarzenie.fGodzina.string(from: k))
            return od + " – " + doKonca
        }
        var t = Wydarzenie.fPelna.string(from: p)
        if calyDzien { return t + " · " + Napisy.t("app.eventAllDay", "cały dzień") }
        t += " · " + Wydarzenie.fGodzina.string(from: p)
        if let k = koniec {
            var kal = Calendar(identifier: .gregorian)
            kal.timeZone = TimeZone(identifier: "Europe/Warsaw") ?? .current
            t += kal.isDate(k, inSameDayAs: p) ? "–" + Wydarzenie.fGodzina.string(from: k) : " – " + Wydarzenie.fPelna.string(from: k)
        }
        return t
    }

    // nazwy dni i miesięcy w języku aplikacji
    static var fGodzina: DateFormatter { Jezyk.format("HH:mm") }
    static var fDzien: DateFormatter { Jezyk.format("d") }
    static var fMiesiac: DateFormatter { Jezyk.format("LLL") }
    static var fTydzien: DateFormatter { Jezyk.format("EEE") }
    static var fPelna: DateFormatter { Jezyk.format("EEEE, d MMMM yyyy") }
    static var fKrotka: DateFormatter { Jezyk.format("d MMM") }
    static var fISO: DateFormatter { Jezyk.format("yyyy-MM-dd", posix: true) }
}

/// Sonda z /api/v1/spotlight albo /api/v1/polls/{id}: pytanie, odpowiedzi i wyniki (pokazywane dopiero po głosie).
struct Sonda {
    struct Opcja: Identifiable {
        let klucz: String
        let etykieta: String
        let glosy: Int
        let procent: Int
        var id: String { klucz }
    }
    let id: String
    let pytanie: String
    let otwarta: Bool
    let doKiedy: Date?
    let razem: Int
    let opcje: [Opcja]

    init(_ j: Slownik) {
        id = j.s("id")
        pytanie = Html.tekst(j.s("question"))
        otwarta = j.b("open")
        doKiedy = ISO8601DateFormatter().date(from: j.s("showTo"))
        razem = j.i("totalVotes")
        opcje = j.lista("options").map { o in
            Opcja(klucz: o.s("key"), etykieta: Html.tekst(o.s("label")), glosy: o.i("votes"), procent: o.i("pct"))
        }
    }

    /// 1 głos, 2–4 głosy (bez 12–14), 5+ głosów.
    static func glosyTekst(_ n: Int) -> String {
        Napisy.liczba("app.votes", n, ["one": "{n} głos", "few": "{n} głosy", "many": "{n} głosów"])
    }

    /// „30 listopada, 20:00”, z rokiem tylko gdy inny niż bieżący.
    static func doKiedyTekst(_ d: Date) -> String {
        let innyRok = Calendar.current.component(.year, from: d) != Calendar.current.component(.year, from: Date())
        return Jezyk.format(innyRok ? "d MMMM yyyy, HH:mm" : "d MMMM, HH:mm").string(from: d)
    }
}

/// Kalendarz wydarzeń w miejscu sondy (spotlight type „events”): miesiąc, dni z wydarzeniami i najbliższe wydarzenia.
struct KalendarzSpot {
    let miesiac: String
    let dzisiaj: String
    let dni: Set<String>
    let wydarzenia: [Wydarzenie]
    let adres: URL?

    init(_ j: Slownik) {
        miesiac = j.s("month")
        dzisiaj = j.s("today")
        dni = Set((j["days"] as? [String]) ?? [])
        wydarzenia = j.lista("items").map(Wydarzenie.init)
        adres = URL(string: j.s("url"))
    }
}

/// Blok „poll”: trwająca sonda albo, gdy jej nie ma, kalendarz wydarzeń.
enum Spotlight {
    case sonda(Sonda)
    case kalendarz(KalendarzSpot)
}
