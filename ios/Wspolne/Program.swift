import Foundation

/// Program z paska LivePL. Lista pochodzi z tego samego pliku programy.json co w wersji na Windows.
struct Program: Identifiable, Decodable, Hashable {
    let id: String
    let nazwa: String
    let skrot: String?
    let adres: String
    let ikona: String?
    let opis: String?
    let ukryty: Bool?
    let szeroki: Bool?
    let tylkoZYubiKey: Bool?

    var krotkaNazwa: String { skrot ?? nazwa }
    /// Schowane: „ukryty” z programy.json oraz programy tylko z kluczem YubiKey (Urząd), bo na telefonie
    /// otwierają tylko stronę publiczną. Nie ma ich na dolnym pasku, są pod „Więcej programów”.
    var schowany: Bool { (ukryty ?? false) || (tylkoZYubiKey ?? false) }

    /// Panel urzędu loguje się kluczem YubiKey przez Windows, więc na telefonie otwieramy stronę publiczną.
    var adresNaTelefonie: URL {
        let url = URL(string: adres) ?? URL(string: "https://livepl.eu/")!
        if tylkoZYubiKey == true, let host = url.host, let publiczny = URL(string: "https://\(host)/") {
            return publiczny
        }
        return url
    }

    /// Własna grafika programu z Assets.xcassets (jak „obrazek” w programy.json).
    var obrazek: String? {
        switch id {
        case "poczta": return "program-poczta"
        case "liveproduction": return "program-liveproduction"
        case "tvml": return "program-tvml"
        default: return nil
        }
    }

    /// Ikona systemowa odpowiadająca ikonie z ikony.js.
    var symbol: String {
        switch ikona {
        case "poczta": return "envelope"
        case "klient": return "person.crop.circle"
        case "lekcje": return "play.rectangle"
        case "egzamin": return "graduationcap"
        case "serwer": return "server.rack"
        case "tarcza": return "checkmark.shield"
        default: return "globe"
        }
    }

    static func wczytaj() -> [Program] {
        guard let plik = Bundle.main.url(forResource: "programy", withExtension: "json"),
              let dane = try? Data(contentsOf: plik),
              let lista = try? JSONDecoder().decode([Program].self, from: dane)
        else { return [] }
        return lista
    }
}

/// Strona dopisana przez użytkownika; lista zostaje tylko w tym telefonie.
struct MojaStrona: Identifiable, Codable, Hashable {
    var id = UUID()
    var adres: String
    var nazwa: String

    var url: URL? { URL(string: adres) }
    var wyswietlanaNazwa: String { nazwa.isEmpty ? Adres.host(url) : nazwa }
}

enum Adres {
    private static let wzorAdresu = #"^[^/\s]+\.[A-Za-z]{2,}(:\d+)?(/\S*)?$"#

    /// Jak w LivePL: pełny adres, adres bez https:// albo wyszukiwanie w Google.
    static func zTekstu(_ tekst: String) -> URL? {
        let t = tekst.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return nil }
        let maly = t.lowercased()
        for przedrostek in ["http://", "https://", "mailto:", "tel:", "about:"] where maly.hasPrefix(przedrostek) {
            return URL(string: t) ?? URL(string: t.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? t)
        }
        if let pelny = adresStrony(t) { return URL(string: pelny) }
        var szukaj = URLComponents(string: "https://www.google.com/search")
        szukaj?.queryItems = [URLQueryItem(name: "q", value: t)]
        return szukaj?.url
    }

    /// „livepl.eu” → „https://livepl.eu”; nil, gdy to nie wygląda na adres strony.
    static func adresStrony(_ tekst: String) -> String? {
        let t = tekst.trimmingCharacters(in: .whitespacesAndNewlines)
        let maly = t.lowercased()
        if maly.hasPrefix("http://") || maly.hasPrefix("https://") { return URL(string: t) == nil ? nil : t }
        guard !t.contains(" "), t.range(of: wzorAdresu, options: .regularExpression) != nil else { return nil }
        return "https://" + t
    }

    static func host(_ url: URL?) -> String {
        guard let url else { return "" }
        guard var host = url.host else { return url.absoluteString }
        if host.hasPrefix("www.") { host.removeFirst(4) }
        if let port = url.port { host += ":\(port)" }
        return host
    }

    static func taSamaStrona(_ a: String, _ b: String) -> Bool {
        func bezUkosnika(_ s: String) -> String {
            var w = s.lowercased()
            while w.hasSuffix("/") { w.removeLast() }
            return w
        }
        return bezUkosnika(a) == bezUkosnika(b)
    }
}
