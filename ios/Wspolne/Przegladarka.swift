import SwiftUI
import WebKit

/// Tryb dzień i noc przy zegarze: auto → dzień → noc → auto, jak w LivePL na Windows.
enum Motyw: String, CaseIterable {
    case auto, dzien, noc

    var nastepny: Motyw {
        switch self {
        case .auto: return .dzien
        case .dzien: return .noc
        case .noc: return .auto
        }
    }

    var symbol: String {
        switch self {
        case .auto: return "circle.lefthalf.filled"
        case .dzien: return "sun.max"
        case .noc: return "moon"
        }
    }

    var opis: String {
        switch self {
        case .auto: return "automatycznie, jak telefon"
        case .dzien: return "dzień"
        case .noc: return "noc"
        }
    }

    var styl: UIUserInterfaceStyle {
        switch self {
        case .auto: return .unspecified
        case .dzien: return .light
        case .noc: return .dark
        }
    }
}

/// Stan całej przeglądarki: programy, otwarte karty, Moje strony i tryb.
@MainActor
final class Przegladarka: ObservableObject {
    let programy: [Program] = Program.wczytaj()
    /// Poczta LivePL: tylko te strony otwierają się w aplikacji, kliknięte linki do innych idą do przeglądarki.
    /// nil (przeglądarka LivePL): wszystkie strony w aplikacji.
    var dozwoloneHosty: Set<String>?

    @Published private(set) var karty: [Karta] = []
    /// nil = Reżyserka (ekran startowy)
    @Published var aktywnaID: UUID?
    @Published var mojeStrony: [MojaStrona] = [] {
        didSet { zapiszStrony() }
    }
    @Published var motyw: Motyw = .auto {
        didSet {
            UserDefaults.standard.set(motyw.rawValue, forKey: Klucz.motyw)
            zastosujMotyw()
        }
    }
    @Published var pokazListeKart = false
    @Published var dodawanaStrona: MojaStrona?
    // okna z menu przeglądarki (MenuPrzegladarki.swift)
    @Published var pokazCertyfikat = false
    @Published var pokazUrzadzenie = false
    @Published var pokazInformacje = false
    @Published var pokazCzyszczenie = false
    /// „Nowa karta” prosi Reżyserkę o kursor w polu adresu.
    @Published private(set) var prosbaOAdres = 0
    var obsluzonaProsbaOAdres = 0

    private var uruchomiona = false

    private enum Klucz {
        static let motyw = "motyw"
        static let strony = "livepl-strony"
        static let sesja = "livepl-sesja"
    }

    private struct ZapisKarty: Codable {
        var program: String?
        var adres: String
        var tytul: String
    }

    init() {
        let ustawienia = UserDefaults.standard
        if let m = ustawienia.string(forKey: Klucz.motyw), let zapisany = Motyw(rawValue: m) {
            motyw = zapisany
        }
        if let dane = ustawienia.data(forKey: Klucz.strony),
           let lista = try? JSONDecoder().decode([MojaStrona].self, from: dane) {
            mojeStrony = lista
        }
    }

    // MARK: - Programy i karty

    var aktywna: Karta? {
        guard let aktywnaID else { return nil }
        return karty.first { $0.id == aktywnaID }
    }

    var widoczneProgramy: [Program] { programy.filter { !$0.schowany } }
    var pozostaleProgramy: [Program] { programy.filter { $0.schowany } }

    /// Na dolnym pasku tylko główne programy. Schowane (np. Urząd) są pod „Więcej programów”,
    /// a gdy są otwarte, wraca się do nich z listy kart.
    var programyNaPasku: [Program] {
        programy.filter { !$0.schowany }
    }

    var kartyProgramow: [Karta] { karty.filter { $0.programID != nil } }
    var zwykleKarty: [Karta] { karty.filter { $0.programID == nil } }

    func karta(programu id: String) -> Karta? {
        karty.first { $0.programID == id }
    }

    func program(_ id: String?) -> Program? {
        guard let id else { return nil }
        return programy.first { $0.id == id }
    }

    func aktywuj(_ karta: Karta) {
        karta.zaladujOdlozona()
        aktywnaID = karta.id
    }

    /// Każdy program ma jedną własną kartę, jak kanał na pasku LivePL.
    func otworz(_ program: Program) {
        if let istniejaca = karta(programu: program.id) {
            aktywuj(istniejaca)
            return
        }
        let nowa = Karta(programID: program.id, przegladarka: self)
        nowa.laduj(program.adresNaTelefonie)
        karty.append(nowa)
        aktywnaID = nowa.id
    }

    func otworz(_ url: URL) {
        if Przegladarka.obcyAdres(url) {
            UIApplication.shared.open(url)
            return
        }
        let nowa = Karta(programID: nil, przegladarka: self)
        nowa.laduj(url)
        karty.append(nowa)
        aktywnaID = nowa.id
    }

    /// Okno otwarte przez stronę (target=_blank, window.open).
    func dodajKarte(konfiguracja: WKWebViewConfiguration, otwierajaca: Karta) -> Karta {
        let nowa = Karta(programID: nil, konfiguracja: konfiguracja, przegladarka: self)
        nowa.otwierajaca = otwierajaca
        if let i = karty.firstIndex(where: { $0 === otwierajaca }) {
            karty.insert(nowa, at: i + 1)
        } else {
            karty.append(nowa)
        }
        aktywnaID = nowa.id
        return nowa
    }

    func zamknij(_ karta: Karta) {
        guard let i = karty.firstIndex(where: { $0 === karta }) else { return }
        let byla = aktywnaID == karta.id
        let otwierajaca = karta.otwierajaca
        karta.zakoncz()
        karty.remove(at: i)
        if byla {
            // okno otwarte przez stronę wraca do strony, która je otworzyła
            if let otwierajaca, karty.contains(where: { $0 === otwierajaca }) {
                aktywnaID = otwierajaca.id
            } else {
                aktywnaID = nil
            }
        }
        zapiszSesje()
    }

    func zamknijWszystkie() {
        karty.forEach { $0.zakoncz() }
        karty.removeAll()
        aktywnaID = nil
        zapiszSesje()
    }

    func pokazRezyserke() {
        aktywnaID = nil
    }

    /// Zamyka karty i usuwa ciasteczka, pamięć stron i dane witryn (wylogowanie ze wszystkich stron).
    /// Moje strony i wybrany wygląd zostają.
    func wyczyscDane() {
        zamknijWszystkie()
        let magazyn = WKWebsiteDataStore.default()
        magazyn.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince: .distantPast) {}
    }

    func nowaKarta() {
        aktywnaID = nil
        prosbaOAdres += 1
    }

    nonisolated static func obcyAdres(_ url: URL) -> Bool {
        guard let schemat = url.scheme?.lowercased() else { return false }
        return !["http", "https", "about", "data", "blob", "file", "javascript"].contains(schemat)
    }

    // MARK: - Moje strony

    /// Zwraca opis błędu albo nil, gdy strona została dodana.
    func dodajStrone(_ strona: MojaStrona) -> String? {
        guard let url = strona.url, ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
            return "To nie wygląda na adres strony (np. livepl.eu)."
        }
        if mojeStrony.contains(where: { Adres.taSamaStrona($0.adres, strona.adres) }) {
            return "Ta strona już jest na liście."
        }
        mojeStrony.append(strona)
        return nil
    }

    func usunStrone(_ strona: MojaStrona) {
        mojeStrony.removeAll { $0.id == strona.id }
    }

    private func zapiszStrony() {
        if let dane = try? JSONEncoder().encode(mojeStrony) {
            UserDefaults.standard.set(dane, forKey: Klucz.strony)
        }
    }

    // MARK: - Tryb dzień i noc

    /// Ustawia tryb całemu oknu, więc strony też dostają prefers-color-scheme.
    func zastosujMotyw() {
        for scena in UIApplication.shared.connectedScenes {
            guard let okna = (scena as? UIWindowScene)?.windows else { continue }
            for okno in okna { okno.overrideUserInterfaceStyle = motyw.styl }
        }
    }

    // MARK: - Start i sesja

    func uruchom() {
        guard !uruchomiona else { return }
        uruchomiona = true
        zastosujMotyw()
        przywrocSesje()
        // do zrzutów ekranu z symulatora: -otworz poczta albo -otworz https://…
        if let co = UserDefaults.standard.string(forKey: "otworz") {
            if let p = programy.first(where: { $0.id == co }) {
                otworz(p)
            } else if let url = URL(string: co) {
                otworz(url)
            }
        }
    }

    /// Otwarte karty wracają po ponownym uruchomieniu; strony ładują się dopiero po wejściu na kartę.
    private func przywrocSesje() {
        guard karty.isEmpty,
              let dane = UserDefaults.standard.data(forKey: Klucz.sesja),
              let lista = try? JSONDecoder().decode([ZapisKarty].self, from: dane)
        else { return }
        for zapis in lista.prefix(15) {
            guard let url = URL(string: zapis.adres) else { continue }
            if let id = zapis.program, program(id) == nil { continue }
            let karta = Karta(programID: zapis.program, przegladarka: self)
            karta.odloz(url, tytul: zapis.tytul)
            karty.append(karta)
        }
    }

    func zapiszSesje() {
        let lista = karty.compactMap { karta -> ZapisKarty? in
            guard let url = karta.wyswietlanyAdres, ["http", "https"].contains(url.scheme ?? "") else { return nil }
            return ZapisKarty(program: karta.programID, adres: url.absoluteString, tytul: karta.tytul)
        }
        if let dane = try? JSONEncoder().encode(lista) {
            UserDefaults.standard.set(dane, forKey: Klucz.sesja)
        }
    }
}
