import SwiftUI

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

    init(_ j: Slownik) {
        id = j.i("id")
        tytul = Html.tekst(j.s("title"))
        lead = Html.tekst(j.s("excerpt"))
        obraz = j.s("image")
        kategoria = Html.tekst(j.s("category"))
        kolor = j.s("categoryColor")
        autor = j.s("author")
        data = Artykul.daty.date(from: j.s("date"))
        komentarze = j.i("comments")
        odslony = j.i("views")
        tagi = j.s("tags").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        zrodloZdjecia = Html.tekst(j.s("image_source"))
        let t = j.s("content")
        tresc = t.isEmpty ? nil : t
    }

    var link: URL { URL(string: "https://tvml.pl/artykul/\(id)")! }
    func miniatura(_ szerokosc: Int = 600) -> URL? { Siec.obraz(obraz, szerokosc: szerokosc) }
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
        id = j.s("id")
        tytul = Html.tekst(j.s("title"))
        obraz = URL(string: j.s("image"))
        data = j.s("published")
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

    init(_ j: Slownik) {
        let c = j.slownik("current")
        temperatura = c.d("temperature"); odczuwalna = c.d("feelsLike"); wilgotnosc = c.d("humidity")
        cisnienie = c.d("pressure"); wiatr = c.d("windKmh"); porywy = c.d("windGust")
        kierunek = c.s("windDirText"); opis = c.s("description"); ikona = c.s("icon")
        dzien = c["is_day"] == nil ? true : c.b("is_day")
        wschod = j.s("sunrise").isEmpty ? c.s("sunrise") : j.s("sunrise")
        zachod = j.s("sunset").isEmpty ? c.s("sunset") : j.s("sunset")
        let prognoza = j.lista("forecast").isEmpty ? c.lista("forecast") : j.lista("forecast")
        dni = prognoza.map { Dzien(nazwa: $0.s("label"), ikona: $0.s("icon"), opis: $0.s("desc"), max: $0.d("temp"), min: $0.d("tempMin"), opad: $0.i("pop")) }
        godziny = j.lista("hourly").map { Godzina(czas: $0.s("time"), ikona: $0.s("icon"), temp: $0.d("temp"), opad: $0.i("pop")) }
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
    @Published var zakladka = Zakladka.start
    /// Otwarte artykuły w każdej zakładce (NavigationStack).
    @Published var sciezki: [Zakladka: [Artykul]] = [:]
    @Published var zapisane: [Int] = UserDefaults.standard.array(forKey: "tvml-zapisane") as? [Int] ?? [] {
        didSet { UserDefaults.standard.set(zapisane, forKey: "tvml-zapisane") }
    }
    private var ostatnio: Date?

    /// Kategorie na górze ekranu Start (te same co w menu strony).
    struct KategoriaMenu: Hashable { let nazwa: String; let slug: String? }
    let kategorie: [KategoriaMenu] = [
        .init(nazwa: "Wszystko", slug: nil), .init(nazwa: "Aktualności", slug: "aktualnosci"), .init(nazwa: "Sport", slug: "sport"),
        .init(nazwa: "Na sygnale", slug: "na-sygnale"), .init(nazwa: "Kultura", slug: "kultura"), .init(nazwa: "Muzyka", slug: "muzyka")
    ]

    func odswiez(wymus: Bool = false) async {
        if !wymus, let ostatnio, Date().timeIntervalSince(ostatnio) < 120 { return }
        laduje = true
        defer { laduje = false }
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

    func kategoria(_ slug: String) async {
        guard let lista = (try? await Siec.json("tvml-db.php?action=news&category=\(slug)&limit=20")) as? [Slownik] else { return }
        wKategorii[slug] = lista.map(Artykul.init)
    }

    func artykul(_ id: Int) async -> Artykul? {
        guard let j = (try? await Siec.json("tvml-db.php?action=article&id=\(id)")) as? Slownik, j["id"] != nil else { return nil }
        return Artykul(j)
    }

    func szukaj(_ fraza: String) async -> [Artykul] {
        let q = fraza.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
        guard !q.isEmpty, let lista = (try? await Siec.json("tvml-db.php?action=search&q=\(q)&limit=20")) as? [Slownik] else { return [] }
        return lista.map(Artykul.init)
    }

    func przelaczZapis(_ a: Artykul) {
        if let i = zapisane.firstIndex(of: a.id) { zapisane.remove(at: i) } else { zapisane.insert(a.id, at: 0) }
    }
}

enum Zakladka: Hashable { case start, sport, wideo, pogoda, wiecej }

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
