import SwiftUI
import PhotosUI
import AVFoundation
import CoreLocation
import UniformTypeIdentifiers
import UIKit

// „Zgłoś zdarzenie (Alert TVML)” w aplikacji, bez strony www.
// Kontrakt z TVML Connect: config.alerts, POST /api/v1/alerts/token i POST /api/v1/alerts (multipart).
// Gdy serwer nie zgłasza formularza (brak config.alerts.native), aplikacja dalej otwiera tvml.pl/#alert.

/// Ustawienia formularza z config.alerts, zapisane na czas bez sieci.
struct UstawieniaAlertu {
    struct Typ: Identifiable, Hashable {
        let id: String
        let nazwa: String
        let kolor: String
    }

    var natywny = false
    var typy: [Typ] = UstawieniaAlertu.domyslneTypy
    var maxZdjecieMB = 10
    var maxFilmMB = 25
    var maxFilmSekund = 60
    var zgoda = ""
    /// Serwer (API 1.9.0) wymaga niepustej wersji; ta sama co TVML_APP_ALERT_CONSENT_VERSION, gdy config jej nie poda.
    var wersjaZgody = "2026-11-24"
    var polityka = "https://tvml.pl/strona/polityka-prywatnosci"

    /// Te same rodzaje co na stronie (portal-chrome.js, openAlertSubmissionModal).
    static var domyslneTypy: [Typ] {
        [Typ(id: "traffic", nazwa: Napisy.t("app.alertTraffic", "Korek"), kolor: "#f5a623"),
         Typ(id: "accident", nazwa: Napisy.t("app.alertAccident", "Wypadek"), kolor: "#ff4d6a"),
         Typ(id: "weather", nazwa: Napisy.t("app.alertWeather", "Pogoda"), kolor: "#007bff"),
         Typ(id: "fire", nazwa: Napisy.t("app.alertFire", "Pożar"), kolor: "#fd7e14"),
         Typ(id: "other", nazwa: Napisy.t("app.alertOther", "Inne"), kolor: "#6c757d")]
    }

    static let ikony = ["traffic": "car.fill", "accident": "exclamationmark.triangle.fill", "weather": "cloud.bolt.rain.fill",
                        "fire": "flame.fill", "other": "info.circle.fill"]

    init() {}

    init(_ s: Slownik) {
        let w = s["enabled"] == nil || s.b("enabled")
        natywny = w && s.b("native")
        let t = s.lista("types").compactMap { t -> Typ? in
            let id = t.s("id")
            guard !id.isEmpty else { return nil }
            let zapas = UstawieniaAlertu.domyslneTypy.first { $0.id == id }
            return Typ(id: id, nazwa: t.s("label").isEmpty ? (zapas?.nazwa ?? id) : t.s("label"),
                       kolor: t.s("color").isEmpty ? (zapas?.kolor ?? "#6c757d") : t.s("color"))
        }
        if !t.isEmpty { typy = t }
        if s.i("maxImageMB") > 0 { maxZdjecieMB = s.i("maxImageMB") }
        if s.i("maxVideoMB") > 0 { maxFilmMB = s.i("maxVideoMB") }
        if s.i("maxVideoSeconds") > 0 { maxFilmSekund = s.i("maxVideoSeconds") }
        zgoda = s.s("consentText")
        if !s.s("consentVersion").isEmpty { wersjaZgody = s.s("consentVersion") }
        if !s.s("privacyUrl").isEmpty { polityka = s.s("privacyUrl") }
    }

    private static let klucz = "tvml-alerts"

    /// Ostatnie ustawienia z serwera (albo zapisane przy poprzednim uruchomieniu).
    static var biezace: UstawieniaAlertu = {
        guard let d = UserDefaults.standard.data(forKey: klucz),
              let s = (try? JSONSerialization.jsonObject(with: d)) as? Slownik else { return UstawieniaAlertu() }
        return UstawieniaAlertu(s)
    }()

    /// Z odswiezV1: brak config.alerts = formularz ze strony.
    static func ustaw(_ s: Slownik?) {
        let s = s ?? [:]
        biezace = UstawieniaAlertu(s)
        if let d = try? JSONSerialization.data(withJSONObject: s) { UserDefaults.standard.set(d, forKey: klucz) }
    }

    /// Ekran w aplikacji: gdy serwer go zgłasza, a w CI także z argumentem -alert-natywny YES.
    static var natywnyDostepny: Bool { biezace.natywny || UserDefaults.standard.bool(forKey: "alert-natywny") }

    /// CI: -ekran alert otwiera formularz raz po starcie (zrzuty), bez zmiany przełącznika ekranów w Glowny.
    private static var otwartyNaStarcie = false
    static func otworzNaStarcie() -> Bool {
        guard !otwartyNaStarcie, UserDefaults.standard.string(forKey: "ekran") == "alert" else { return false }
        otwartyNaStarcie = true
        return true
    }

    var tekstZgody: String {
        zgoda.isEmpty
            ? Napisy.t("app.alertConsent", "Wyrażam zgodę na przetwarzanie moich danych osobowych w celu zgłoszenia informacji o zdarzeniu zgodnie z polityką prywatności serwisu.")
            : zgoda
    }
}

/// Okno ze strony tvml.pl. Formularz Alertu TVML w aplikacji, gdy serwer go obsługuje; decyzja zapada przy otwarciu.
struct OknoStrony: View {
    let strona: StronaWWW
    @State private var natywny = UstawieniaAlertu.natywnyDostepny

    var body: some View {
        if (strona.sciezka == "#alert" || strona.sciezka == "#zglos-zdarzenie") && natywny {
            AlertWidok()
        } else {
            Safari(url: URL(string: "https://tvml.pl/" + strona.sciezka)!).ignoresSafeArea()
        }
    }
}

// MARK: - Załącznik

struct ZalacznikAlertu {
    enum Rodzaj { case zdjecie, film }
    let rodzaj: Rodzaj
    let plik: URL
    let mime: String
    let rozmiar: Int
    let miniatura: UIImage?
    let sekundy: Int?

    var opis: String {
        let r = ByteCountFormatter.string(fromByteCount: Int64(rozmiar), countStyle: .file)
        switch rodzaj {
        case .zdjecie: return Napisy.t("app.alertPhoto", "Zdjęcie") + " · " + r
        case .film:
            let s = sekundy ?? 0
            return Napisy.t("app.alertVideo", "Film") + " · " + String(format: "%d:%02d", s / 60, s % 60) + " · " + r
        }
    }
}

enum BladZalacznika: Error {
    case format, zaDuzy(Int), zaDlugi(Int), eksport

    var komunikat: String {
        switch self {
        case .format: return Napisy.t("app.alertFileFormat", "Tego pliku nie da się dołączyć. Wybierz zdjęcie albo film.")
        case .zaDuzy(let mb): return Napisy.t("app.alertFileTooBig", "Plik jest za duży (maks. {n} MB).", n: mb)
        case .zaDlugi(let s): return Napisy.t("app.alertVideoTooLong", "Film jest za długi. Wybierz krótszy niż {n} s.", n: s)
        case .eksport: return Napisy.t("app.alertVideoFailed", "Nie udało się przygotować filmu. Spróbuj z innym.")
        }
    }
}

/// Zdjęcia i filmy przed wysyłką: mniejsze i bez metadanych (miejsce, urządzenie).
enum PlikiAlertu {
    static func tymczasowy(_ rozszerzenie: String) -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("alert-\(UUID().uuidString).\(rozszerzenie)")
    }

    /// JPEG narysowany od nowa: bez EXIF (w tym GPS ze zdjęcia), dłuższy bok do 2048 px.
    static func jpeg(_ obraz: UIImage, dluzszyBok: CGFloat, jakosc: CGFloat) -> Data? {
        let bok = max(obraz.size.width, obraz.size.height)
        guard bok > 0 else { return nil }
        let skala = min(1, dluzszyBok / bok)
        let rozmiar = CGSize(width: (obraz.size.width * skala).rounded(), height: (obraz.size.height * skala).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: rozmiar, format: format).jpegData(withCompressionQuality: jakosc) { _ in
            obraz.draw(in: CGRect(origin: .zero, size: rozmiar))
        }
    }

    static func zdjecie(_ obraz: UIImage, maksMB: Int) throws -> ZalacznikAlertu {
        let limit = maksMB * 1024 * 1024
        var dane = jpeg(obraz, dluzszyBok: 2048, jakosc: 0.8)
        if let d = dane, d.count > limit { dane = jpeg(obraz, dluzszyBok: 1600, jakosc: 0.65) }
        guard let d = dane else { throw BladZalacznika.format }
        guard d.count <= limit else { throw BladZalacznika.zaDuzy(maksMB) }
        let plik = tymczasowy("jpg")
        try d.write(to: plik)
        let miniatura = jpeg(obraz, dluzszyBok: 240, jakosc: 0.7).flatMap(UIImage.init(data:))
        return ZalacznikAlertu(rodzaj: .zdjecie, plik: plik, mime: "image/jpeg", rozmiar: d.count, miniatura: miniatura, sekundy: nil)
    }

    /// Film do MP4 720p (H.264), bez metadanych miejsca; dłuższy niż limit = komunikat, bez przycinania.
    static func film(_ zrodlo: URL, maksMB: Int, maksSekund: Int) async throws -> ZalacznikAlertu {
        let zasob = AVURLAsset(url: zrodlo)
        let czas = try await zasob.load(.duration).seconds
        guard czas.isFinite, czas > 0 else { throw BladZalacznika.format }
        guard czas <= Double(maksSekund) + 0.5 else { throw BladZalacznika.zaDlugi(maksSekund) }
        let limit = maksMB * 1024 * 1024
        var gotowy: (URL, Int)?
        for preset in [AVAssetExportPreset1280x720, AVAssetExportPresetMediumQuality] {
            guard let eksport = AVAssetExportSession(asset: zasob, presetName: preset) else { continue }
            eksport.shouldOptimizeForNetworkUse = true
            eksport.metadataItemFilter = AVMetadataItemFilter.forSharing()
            let cel = tymczasowy("mp4")
            do {
                try await eksport.export(to: cel, as: .mp4)
            } catch {
                try? FileManager.default.removeItem(at: cel)
                continue
            }
            let rozmiar = (try? cel.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
            if rozmiar > 0 && rozmiar <= limit {
                gotowy = (cel, rozmiar)
                break
            }
            try? FileManager.default.removeItem(at: cel)
            if rozmiar > limit && preset == AVAssetExportPresetMediumQuality { throw BladZalacznika.zaDuzy(maksMB) }
        }
        guard let wynik = gotowy else { throw BladZalacznika.eksport }
        let (plik, rozmiar) = wynik
        let generator = AVAssetImageGenerator(asset: zasob)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 240, height: 240)
        let klatka = try? await generator.image(at: .zero).image
        return ZalacznikAlertu(rodzaj: .film, plik: plik, mime: "video/mp4", rozmiar: rozmiar,
                               miniatura: klatka.map { UIImage(cgImage: $0) }, sekundy: Int(czas.rounded()))
    }

    /// CI (-alert-przyklad): obrazek w miejscu zdjęcia, bez sięgania do galerii symulatora.
    static func przykladowy() -> ZalacznikAlertu? {
        let rozmiar = CGSize(width: 480, height: 360)
        let obraz = UIGraphicsImageRenderer(size: rozmiar).image { k in
            let kolory = [UIColor(red: 0.42, green: 0.52, blue: 0.66, alpha: 1).cgColor,
                          UIColor(red: 0.14, green: 0.19, blue: 0.28, alpha: 1).cgColor] as CFArray
            if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: kolory, locations: nil) {
                k.cgContext.drawLinearGradient(g, start: .zero, end: CGPoint(x: 0, y: rozmiar.height), options: [])
            }
            if let auto = UIImage(systemName: "car.side.fill", withConfiguration: UIImage.SymbolConfiguration(pointSize: 140))?
                .withTintColor(.white, renderingMode: .alwaysOriginal) {
                let r = CGRect(x: (rozmiar.width - auto.size.width) / 2, y: (rozmiar.height - auto.size.height) / 2,
                               width: auto.size.width, height: auto.size.height)
                auto.draw(in: r)
            }
        }
        return try? zdjecie(obraz, maksMB: 10)
    }
}

/// Film z galerii (PhotosPicker) skopiowany do plików tymczasowych aplikacji.
struct FilmZGalerii: Transferable {
    let url: URL
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { SentTransferredFile($0.url) } importing: { odebrany in
            let rozszerzenie = odebrany.file.pathExtension.isEmpty ? "mov" : odebrany.file.pathExtension
            let cel = PlikiAlertu.tymczasowy(rozszerzenie)
            try FileManager.default.copyItem(at: odebrany.file, to: cel)
            return FilmZGalerii(url: cel)
        }
    }
}

/// Aparat (zdjęcie albo film) przez UIImagePickerController; PhotosPicker nie robi zdjęć.
struct Aparat: UIViewControllerRepresentable {
    let maksSekund: Int
    let gotowe: (UIImage?, URL?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController()
        p.sourceType = .camera
        p.mediaTypes = [UTType.image.identifier, UTType.movie.identifier]
        p.videoMaximumDuration = TimeInterval(maksSekund)
        p.videoQuality = .typeHigh
        p.delegate = context.coordinator
        return p
    }

    func updateUIViewController(_ vc: UIImagePickerController, context: Context) {}
    func makeCoordinator() -> Koordynator { Koordynator(gotowe) }

    final class Koordynator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let gotowe: (UIImage?, URL?) -> Void
        init(_ gotowe: @escaping (UIImage?, URL?) -> Void) { self.gotowe = gotowe }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            // film z aparatu leży w tmp systemu tylko chwilę, więc od razu kopia
            var film: URL?
            if let zrodlo = info[.mediaURL] as? URL {
                let cel = PlikiAlertu.tymczasowy(zrodlo.pathExtension.isEmpty ? "mov" : zrodlo.pathExtension)
                if (try? FileManager.default.copyItem(at: zrodlo, to: cel)) != nil { film = cel }
            }
            gotowe(info[.originalImage] as? UIImage, film)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { gotowe(nil, nil) }
    }
}

// MARK: - Położenie

/// Jedno położenie na prośbę (przycisk „Dodaj moje położenie”), bez śledzenia w tle.
@MainActor
final class Lokalizator: NSObject, CLLocationManagerDelegate {
    enum Blad: Error { case brakZgody, brakPolozenia }

    private let menedzer = CLLocationManager()
    private var czekaNaZgode: CheckedContinuation<Void, Never>?
    private var czekaNaPolozenie: CheckedContinuation<CLLocation, Error>?

    override init() {
        super.init()
        menedzer.delegate = self
        menedzer.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
    }

    func pobierz() async throws -> CLLocation {
        if menedzer.authorizationStatus == .notDetermined {
            await withCheckedContinuation { c in
                czekaNaZgode = c
                menedzer.requestWhenInUseAuthorization()
            }
        }
        let status = menedzer.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { throw Blad.brakZgody }
        return try await withCheckedThrowingContinuation { c in
            czekaNaPolozenie?.resume(throwing: Blad.brakPolozenia)
            czekaNaPolozenie = c
            menedzer.requestLocation()
        }
    }

    // CLLocationManager utworzony na wątku głównym wywołuje te metody też na nim
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        MainActor.assumeIsolated {
            guard menedzer.authorizationStatus != .notDetermined else { return }
            czekaNaZgode?.resume()
            czekaNaZgode = nil
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        MainActor.assumeIsolated {
            guard let l = locations.last else { return }
            czekaNaPolozenie?.resume(returning: l)
            czekaNaPolozenie = nil
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        MainActor.assumeIsolated {
            czekaNaPolozenie?.resume(throwing: Blad.brakPolozenia)
            czekaNaPolozenie = nil
        }
    }
}

struct PolozenieAlertu: Equatable {
    let szerokosc: Double
    let dlugosc: Double
    let dokladnosc: Int
}

// MARK: - Wysyłka

/// Postęp wysyłania pliku (URLSession task delegate). Zamknięcie tylko przekazuje liczbę na główny wątek.
final class PostepWysylki: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    let zmiana: (Double) -> Void
    init(_ zmiana: @escaping (Double) -> Void) { self.zmiana = zmiana }

    func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64,
                    totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        guard totalBytesExpectedToSend > 0 else { return }
        zmiana(Double(totalBytesSent) / Double(totalBytesExpectedToSend))
    }
}

struct Multipart {
    let granica = "TVML-" + UUID().uuidString
    private(set) var dane = Data()

    mutating func pole(_ nazwa: String, _ wartosc: String) {
        dane.append(Data("--\(granica)\r\nContent-Disposition: form-data; name=\"\(nazwa)\"\r\n\r\n".utf8))
        dane.append(Data(wartosc.utf8))
        dane.append(Data("\r\n".utf8))
    }

    mutating func plik(_ nazwa: String, nazwaPliku: String, mime: String, zawartosc: Data) {
        dane.append(Data("--\(granica)\r\nContent-Disposition: form-data; name=\"\(nazwa)\"; filename=\"\(nazwaPliku)\"\r\nContent-Type: \(mime)\r\n\r\n".utf8))
        dane.append(zawartosc)
        dane.append(Data("\r\n".utf8))
    }

    mutating func zamknij() -> Data {
        dane.append(Data("--\(granica)--\r\n".utf8))
        return dane
    }
}

struct PotwierdzenieAlertu: Equatable {
    let numer: String
    let komunikat: String
}

struct BladWysylki: Equatable {
    let tekst: String
    /// Przycisk „Otwórz formularz na tvml.pl” (brak sieci, błąd serwera, formularz wyłączony).
    let strona: Bool
}

@MainActor
final class FormularzAlertu: ObservableObject {
    enum Stan: Equatable { case edycja, wysylanie(Double), wyslane(PotwierdzenieAlertu) }

    let ustawienia: UstawieniaAlertu
    @Published var typ: String
    @Published var tytul = ""
    @Published var miejsce = ""
    @Published var opis = ""
    @Published var imie: String
    @Published var kontakt: String
    @Published var zapamietaj: Bool
    @Published var zgoda = false
    @Published var zalacznik: ZalacznikAlertu?
    @Published var przygotowanie = false
    @Published var bladZalacznika: String?
    @Published var polozenie: PolozenieAlertu?
    @Published var szukaPolozenia = false
    @Published var bladPolozenia: String?
    @Published var stan: Stan = .edycja
    @Published var blad: BladWysylki?
    /// Pola do poprawy po „Wyślij” (podświetlone na czerwono).
    @Published var bledyPol: Set<String> = []

    private var token: (wartosc: String, od: Date, minimum: Double)?
    private let lokalizator = Lokalizator()
    private static let kluczImie = "tvml-alert-imie", kluczKontakt = "tvml-alert-kontakt", kluczZapamietaj = "tvml-alert-zapamietaj"

    init(ustawienia: UstawieniaAlertu = UstawieniaAlertu.biezace) {
        self.ustawienia = ustawienia
        typ = ustawienia.typy.first?.id ?? "other"
        let d = UserDefaults.standard
        imie = d.string(forKey: FormularzAlertu.kluczImie) ?? ""
        kontakt = d.string(forKey: FormularzAlertu.kluczKontakt) ?? ""
        zapamietaj = d.object(forKey: FormularzAlertu.kluczZapamietaj) as? Bool ?? true
    }

    /// Czy zamknięcie okna coś straci (wtedy pytanie „Odrzucić zgłoszenie?”).
    var maTresc: Bool {
        if case .wyslane = stan { return false }
        return !tytul.isEmpty || !opis.isEmpty || !miejsce.isEmpty || zalacznik != nil
    }

    // MARK: pola

    private static func dlugosc(_ s: String) -> Int { s.trimmingCharacters(in: .whitespacesAndNewlines).count }

    func sprawdz() -> Bool {
        var b: Set<String> = []
        if Self.dlugosc(tytul) < 3 { b.insert("title") }
        if Self.dlugosc(miejsce) < 3 { b.insert("location") }
        if Self.dlugosc(opis) < 10 { b.insert("description") }
        if Self.dlugosc(imie) < 2 { b.insert("reporter_name") }
        if Self.dlugosc(kontakt) < 5 { b.insert("reporter_contact") }
        if !zgoda { b.insert("consent") }
        bledyPol = b
        return b.isEmpty
    }

    // MARK: załącznik

    func wczytaj(_ element: PhotosPickerItem) async {
        przygotowanie = true
        bladZalacznika = nil
        defer { przygotowanie = false }
        do {
            if element.supportedContentTypes.contains(where: { $0.conforms(to: .movie) }) {
                guard let f = try await element.loadTransferable(type: FilmZGalerii.self) else { throw BladZalacznika.format }
                defer { try? FileManager.default.removeItem(at: f.url) }
                ustawZalacznik(try await PlikiAlertu.film(f.url, maksMB: ustawienia.maxFilmMB, maksSekund: ustawienia.maxFilmSekund))
            } else {
                guard let d = try await element.loadTransferable(type: Data.self), let obraz = UIImage(data: d) else { throw BladZalacznika.format }
                ustawZalacznik(try PlikiAlertu.zdjecie(obraz, maksMB: ustawienia.maxZdjecieMB))
            }
        } catch let b as BladZalacznika {
            bladZalacznika = b.komunikat
        } catch {
            bladZalacznika = BladZalacznika.format.komunikat
        }
    }

    func zAparatu(_ obraz: UIImage?, _ film: URL?) async {
        guard obraz != nil || film != nil else { return }
        przygotowanie = true
        bladZalacznika = nil
        defer { przygotowanie = false }
        do {
            if let film {
                defer { try? FileManager.default.removeItem(at: film) }
                ustawZalacznik(try await PlikiAlertu.film(film, maksMB: ustawienia.maxFilmMB, maksSekund: ustawienia.maxFilmSekund))
            } else if let obraz {
                ustawZalacznik(try PlikiAlertu.zdjecie(obraz, maksMB: ustawienia.maxZdjecieMB))
            }
        } catch let b as BladZalacznika {
            bladZalacznika = b.komunikat
        } catch {
            bladZalacznika = BladZalacznika.format.komunikat
        }
    }

    func ustawZalacznik(_ z: ZalacznikAlertu?) {
        if let stary = zalacznik { try? FileManager.default.removeItem(at: stary.plik) }
        zalacznik = z
    }

    // MARK: położenie

    func dodajPolozenie() async {
        szukaPolozenia = true
        bladPolozenia = nil
        defer { szukaPolozenia = false }
        do {
            let l = try await lokalizator.pobierz()
            polozenie = PolozenieAlertu(szerokosc: l.coordinate.latitude, dlugosc: l.coordinate.longitude,
                                        dokladnosc: max(1, Int(l.horizontalAccuracy.rounded())))
            // adres tylko jako podpowiedź do pustego pola; wpisanego tekstu nie zmieniamy
            if miejsce.trimmingCharacters(in: .whitespaces).isEmpty,
               let p = try? await CLGeocoder().reverseGeocodeLocation(l, preferredLocale: Locale(identifier: "pl_PL")).first {
                let ulica = [p.thoroughfare, p.subThoroughfare].compactMap { $0 }.joined(separator: " ")
                miejsce = [p.locality ?? "", ulica].filter { !$0.isEmpty }.joined(separator: ", ")
            }
        } catch Lokalizator.Blad.brakZgody {
            bladPolozenia = Napisy.t("app.alertLocationDenied", "Brak zgody na położenie. Możesz ją dać w Ustawieniach telefonu albo wpisać miejsce ręcznie.")
        } catch {
            bladPolozenia = Napisy.t("app.alertLocationFailed", "Nie udało się ustalić położenia. Wpisz miejsce ręcznie.")
        }
    }

    // MARK: wysyłka

    /// Token antyspamowy pobrany przy otwarciu okna (zamiast suwaka ze strony).
    func przygotujToken() async {
        guard token == nil else { return }
        token = await Self.nowyToken()
    }

    private static func nowyToken() async -> (wartosc: String, od: Date, minimum: Double)? {
        guard let odp = try? await Siec.v1Wyslij("alerts/token", [:]), odp.0 == 200, !odp.1.s("token").isEmpty else { return nil }
        return (odp.1.s("token"), Date(), odp.1.d("minAge") ?? 0)
    }

    func wyslij() async {
        blad = nil
        guard sprawdz() else { return }
        stan = .wysylanie(0)
        var ponowiony = false
        while true {
            if token == nil { token = await Self.nowyToken() }
            guard let t = token else {
                koniecZBledem(BladWysylki(tekst: Napisy.t("app.alertNoConnection", "Brak połączenia z tvml.pl. Spróbuj ponownie za chwilę."), strona: true))
                return
            }
            // serwer przyjmuje token dopiero po minAge s od wydania
            let czekaj = t.minimum - Date().timeIntervalSince(t.od)
            if czekaj > 0 { try? await Task.sleep(nanoseconds: UInt64(czekaj * 1_000_000_000)) }
            token = nil // jednorazowy
            do {
                let (kod, j, ponow) = try await wyslijRaz(token: t.wartosc)
                if (200...299).contains(kod), j.b("ok") || j["id"] != nil {
                    let numer = j.s("number").isEmpty ? (j.s("id").isEmpty ? "" : "A-" + j.s("id")) : j.s("number")
                    let komunikat = j.s("message").isEmpty
                        ? Napisy.t("app.alertSentDefault", "Dziękujemy! Twój alert został przesłany i po weryfikacji przez redakcję ukaże się na stronie.")
                        : j.s("message")
                    zapamietajDane()
                    ustawZalacznik(nil)
                    stan = .wyslane(PotwierdzenieAlertu(numer: numer, komunikat: komunikat))
                    return
                }
                let e = j.slownik("error")
                if e.s("code") == "token_invalid" && !ponowiony {
                    ponowiony = true
                    continue
                }
                if !e.s("field").isEmpty { bledyPol = [e.s("field")] }
                koniecZBledem(Self.bladZOdpowiedzi(kod: kod, kodBledu: e.s("code"), tekst: e.s("message"), ponowZa: ponow))
                return
            } catch {
                koniecZBledem(BladWysylki(tekst: Napisy.t("app.alertNoConnection", "Brak połączenia z tvml.pl. Spróbuj ponownie za chwilę."), strona: true))
                return
            }
        }
    }

    private func koniecZBledem(_ b: BladWysylki) {
        blad = b
        stan = .edycja
    }

    private static func bladZOdpowiedzi(kod: Int, kodBledu: String, tekst: String, ponowZa: Int?) -> BladWysylki {
        if kod == 429 || kodBledu == "rate_limited" {
            let minuty = max(1, ((ponowZa ?? 3600) + 59) / 60)
            return BladWysylki(tekst: Napisy.t("app.alertRateLimited", "Wysłano zbyt wiele zgłoszeń. Spróbuj ponownie za {n} min.", n: minuty), strona: false)
        }
        if kod == 503 || kodBledu == "disabled" || kod == 404 || kod >= 500 {
            return BladWysylki(tekst: tekst.isEmpty ? Napisy.t("app.alertServerError", "Nie udało się wysłać zgłoszenia z aplikacji. Możesz wysłać je przez stronę.") : tekst, strona: true)
        }
        return BladWysylki(tekst: tekst.isEmpty ? Napisy.t("app.alertCheckFields", "Sprawdź zaznaczone pola.") : tekst, strona: false)
    }

    /// Jedna próba: /api/v1/alerts, a przy 404 zapasowy adres tvml-app.php?route=alerts (jak inne trasy).
    private func wyslijRaz(token: String) async throws -> (Int, Slownik, Int?) {
        var m = Multipart()
        m.pole("type", typ)
        m.pole("title", tytul.trimmingCharacters(in: .whitespacesAndNewlines))
        m.pole("location", miejsce.trimmingCharacters(in: .whitespacesAndNewlines))
        m.pole("description", opis.trimmingCharacters(in: .whitespacesAndNewlines))
        m.pole("reporter_name", imie.trimmingCharacters(in: .whitespacesAndNewlines))
        m.pole("reporter_contact", kontakt.trimmingCharacters(in: .whitespacesAndNewlines))
        m.pole("consent", "1")
        m.pole("consent_version", ustawienia.wersjaZgody)
        m.pole("token", token)
        m.pole("platform", "ios")
        m.pole("app_version", Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")
        if let p = polozenie {
            m.pole("lat", String(format: "%.5f", p.szerokosc))
            m.pole("lon", String(format: "%.5f", p.dlugosc))
            m.pole("accuracy", String(p.dokladnosc))
        }
        if let z = zalacznik {
            let dane = try Data(contentsOf: z.plik)
            m.plik("attachment", nazwaPliku: "zgloszenie." + (z.rodzaj == .film ? "mp4" : "jpg"), mime: z.mime, zawartosc: dane)
        }
        let granica = m.granica
        let cialo = m.zamknij()

        let kolejnosc = Siec.dzialajacaBaza.map { d in [d] + Siec.bazyApi.filter { $0 != d } } ?? Siec.bazyApi
        var ostatni: Error = URLError(.badURL)
        for b in kolejnosc {
            let jezyk = (b.hasSuffix("route=") ? "&" : "?") + "lang=" + Jezyk.biezacy
            guard let url = URL(string: b + "alerts" + jezyk, relativeTo: Siec.baza) else { continue }
            var z = URLRequest(url: url, timeoutInterval: 90)
            z.httpMethod = "POST"
            z.setValue(Siec.agent, forHTTPHeaderField: "User-Agent")
            z.setValue("https://tvml.pl/", forHTTPHeaderField: "Referer")
            z.setValue("multipart/form-data; boundary=\(granica)", forHTTPHeaderField: "Content-Type")
            let postep = PostepWysylki { [weak self] p in
                Task { @MainActor in
                    guard let self, case .wysylanie = self.stan else { return }
                    self.stan = .wysylanie(p)
                }
            }
            do {
                let (dane, odp) = try await URLSession.shared.upload(for: z, from: cialo, delegate: postep)
                guard let http = odp as? HTTPURLResponse else { continue }
                if http.statusCode == 404 && b != kolejnosc.last { continue }
                let j = (try? JSONSerialization.jsonObject(with: dane)) as? Slownik ?? [:]
                let ponow = (http.value(forHTTPHeaderField: "Retry-After")).flatMap { Int($0) }
                return (http.statusCode, j, ponow)
            } catch {
                ostatni = error
            }
        }
        throw ostatni
    }

    private func zapamietajDane() {
        let d = UserDefaults.standard
        d.set(zapamietaj, forKey: Self.kluczZapamietaj)
        if zapamietaj {
            d.set(imie.trimmingCharacters(in: .whitespacesAndNewlines), forKey: Self.kluczImie)
            d.set(kontakt.trimmingCharacters(in: .whitespacesAndNewlines), forKey: Self.kluczKontakt)
        } else {
            d.removeObject(forKey: Self.kluczImie)
            d.removeObject(forKey: Self.kluczKontakt)
        }
    }

    /// Zamknięcie okna: pliki tymczasowe precz.
    func sprzataj() { ustawZalacznik(nil) }

    /// CI: -alert-przyklad YES (wypełniony formularz) albo wyslane (potwierdzenie); nic nie idzie do serwera.
    func przyklad(_ tryb: String) {
        typ = "accident"
        tytul = "Zderzenie dwóch aut na rondzie"
        miejsce = "Leszno, skrzyżowanie ul. Królowej Jadwigi i S5"
        opis = "Dwa samochody osobowe, zablokowany jeden pas w stronę Poznania. Służby są już na miejscu."
        imie = "Świadek"
        kontakt = "swiadek@example.com"
        zgoda = true
        polozenie = PolozenieAlertu(szerokosc: 51.84271, dlugosc: 16.57443, dokladnosc: 12)
        zalacznik = PlikiAlertu.przykladowy()
        if tryb == "wyslane" {
            stan = .wyslane(PotwierdzenieAlertu(numer: "A-1234",
                                                komunikat: Napisy.t("app.alertSentDefault", "Dziękujemy! Twój alert został przesłany i po weryfikacji przez redakcję ukaże się na stronie.")))
        }
    }
}

// MARK: - Ekran

struct AlertWidok: View {
    @Environment(\.dismiss) private var zamknij
    @StateObject private var f = FormularzAlertu()
    @State private var wybor: PhotosPickerItem?
    @State private var aparat = false
    @State private var pytanieOOdrzucenie = false
    @State private var www: StronaWWW?
    @FocusState private var pole: String?

    private static let czerwony = Color(hex: "#c8102e")
    private var aparatDostepny: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }
    private var potwierdzenieWyslania: PotwierdzenieAlertu? {
        if case .wyslane(let p) = f.stan { return p }
        return nil
    }

    var body: some View {
        NavigationStack {
            Group {
                if let p = potwierdzenieWyslania {
                    potwierdzenie(p)
                } else {
                    formularz
                }
            }
            .background(Barwy.tlo)
            .navigationTitle(Napisy.t("app.alertTitle", "Zgłoś zdarzenie"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if potwierdzenieWyslania == nil {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(Napisy.t("app.cancel", "Anuluj")) {
                            if f.maTresc { pytanieOOdrzucenie = true } else { zamknijOkno() }
                        }
                        .disabled(f.stan != .edycja)
                    }
                }
                ToolbarItem(placement: .keyboard) {
                    HStack { Spacer(); Button(Napisy.t("app.done", "Gotowe")) { pole = nil } }
                }
            }
        }
        .tint(Barwy.link)
        .interactiveDismissDisabled(f.maTresc || f.stan != .edycja)
        .confirmationDialog(Napisy.t("app.alertDiscardTitle", "Odrzucić zgłoszenie?"), isPresented: $pytanieOOdrzucenie, titleVisibility: .visible) {
            Button(Napisy.t("app.alertDiscard", "Odrzuć"), role: .destructive) { zamknijOkno() }
            Button(Napisy.t("app.alertKeepWriting", "Pisz dalej"), role: .cancel) {}
        }
        .fullScreenCover(isPresented: $aparat) {
            Aparat(maksSekund: f.ustawienia.maxFilmSekund) { obraz, film in
                aparat = false
                Task { await f.zAparatu(obraz, film) }
            }
            .ignoresSafeArea()
        }
        .sheet(item: $www) { s in Safari(url: URL(string: s.sciezka)!).ignoresSafeArea() }
        .onChange(of: wybor) { _, nowy in
            guard let nowy else { return }
            wybor = nil
            Task { await f.wczytaj(nowy) }
        }
        .task {
            if let tryb = UserDefaults.standard.string(forKey: "alert-przyklad") { f.przyklad(tryb) }
            await f.przygotujToken()
        }
    }

    /// Zamknięcie z „Anuluj” albo „Odrzuć”: pliki tymczasowe załącznika precz (po wysłaniu robi to formularz).
    private func zamknijOkno() {
        f.sprzataj()
        zamknij()
    }

    // MARK: formularz

    private var formularz: some View {
        ScrollViewReader { przewin in
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(Napisy.t("app.alertIntro", "Widzisz wypadek, korek, pożar lub gwałtowne zjawisko pogodowe? Poinformuj innych mieszkańców oraz naszą redakcję."))
                            .czcionka(14).foregroundStyle(Barwy.tekst)
                        Label(Napisy.t("app.alertEmergency", "Zagrożenie życia lub zdrowia? Najpierw dzwoń pod 112."), systemImage: "phone.fill")
                            .czcionka(13, .semibold).foregroundStyle(Self.czerwony)
                    }
                    .padding(.vertical, 4)
                }

                Section(Napisy.t("app.alertType", "Typ zdarzenia")) {
                    rodzaje
                }

                Section(Napisy.t("app.alertWhat", "Co się dzieje")) {
                    poleTekstu("title", Napisy.t("app.alertFieldTitle", "Tytuł zgłoszenia"),
                               Napisy.t("app.alertTitleHint", "np. Zderzenie dwóch aut na rondzie"), tekst: $f.tytul)
                    poleTekstu("location", Napisy.t("app.alertFieldLocation", "Miejsce (miejscowość, ulica)"),
                               Napisy.t("app.alertLocationHint", "np. Leszno, ul. Królowej Jadwigi"), tekst: $f.miejsce)
                    polozenie
                    VStack(alignment: .leading, spacing: 4) {
                        etykieta("description", Napisy.t("app.alertFieldDescription", "Opis sytuacji"))
                        TextField(Napisy.t("app.alertDescriptionHint", "Co dokładnie się stało, czy są utrudnienia w ruchu…"), text: $f.opis, axis: .vertical)
                            .lineLimit(4...10)
                            .focused($pole, equals: "description")
                            .czcionka(16)
                    }
                    .id("description")
                }

                Section {
                    zalacznik
                } header: {
                    Text(Napisy.t("app.alertAttachment", "Zdjęcie lub film (opcjonalnie)"))
                } footer: {
                    Text(Napisy.t("app.alertAttachmentHint", "Zdjęcie do {img} MB albo film do {s} s i {vid} MB. Aplikacja usuwa z pliku dane o miejscu i telefonie.")
                        .replacingOccurrences(of: "{img}", with: String(f.ustawienia.maxZdjecieMB))
                        .replacingOccurrences(of: "{s}", with: String(f.ustawienia.maxFilmSekund))
                        .replacingOccurrences(of: "{vid}", with: String(f.ustawienia.maxFilmMB)))
                }

                Section {
                    poleTekstu("reporter_name", Napisy.t("app.alertFieldName", "Imię lub nick"),
                               Napisy.t("app.alertNameHint", "np. Świadek"), tekst: $f.imie, rodzaj: .nickname)
                    poleTekstu("reporter_contact", Napisy.t("app.alertFieldContact", "Telefon lub e-mail"),
                               Napisy.t("app.alertContactHint", "Do ewentualnej weryfikacji przez redakcję"), tekst: $f.kontakt)
                    Toggle(Napisy.t("app.alertRemember", "Zapamiętaj na tym telefonie"), isOn: $f.zapamietaj)
                        .czcionka(15)
                } header: {
                    Text(Napisy.t("app.alertYou", "Twoje dane dla redakcji"))
                } footer: {
                    Text(Napisy.t("app.alertYouHint", "Imię i kontakt widzi tylko redakcja. Nie publikujemy ich."))
                }

                Section {
                    Toggle(isOn: $f.zgoda) {
                        Text(f.ustawienia.tekstZgody + " *").czcionka(13)
                            .foregroundStyle(f.bledyPol.contains("consent") ? Self.czerwony : Barwy.tekst)
                    }
                    .id("consent")
                    Button {
                        www = StronaWWW(sciezka: f.ustawienia.polityka)
                    } label: {
                        Label(Napisy.t("app.privacyPolicy", "Polityka prywatności"), systemImage: "hand.raised")
                            .czcionka(14)
                    }
                }

                Section {
                    if let b = f.blad { bladWysylki(b) }
                    przyciskWyslij.id("send")
                }
            }
            .scrollContentBackground(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: f.bledyPol) { _, nowe in
                // pierwsze pole do poprawy na ekranie (kolejność formularza)
                let kolejnosc = ["title", "location", "description", "reporter_name", "reporter_contact", "consent"]
                if let pierwsze = kolejnosc.first(where: { nowe.contains($0) }) {
                    withAnimation { przewin.scrollTo(pierwsze, anchor: .center) }
                }
            }
            .onChange(of: f.blad) { _, nowy in
                if nowy != nil { withAnimation { przewin.scrollTo("send", anchor: .bottom) } }
            }
            .task {
                // zrzut CI z wypełnionym formularzem: dół (załącznik, dane, zgoda, Wyślij)
                if UserDefaults.standard.string(forKey: "alert-przyklad") == "YES" {
                    try? await Task.sleep(nanoseconds: 600_000_000)
                    przewin.scrollTo("send", anchor: .bottom)
                }
            }
        }
    }

    private var rodzaje: some View {
        HStack(spacing: 8) {
            ForEach(f.ustawienia.typy) { t in
                let wybrany = f.typ == t.id
                let kolor = Color(hex: t.kolor)
                Button { f.typ = t.id } label: {
                    VStack(spacing: 6) {
                        Image(systemName: UstawieniaAlertu.ikony[t.id] ?? "mappin.circle.fill")
                            .font(.system(size: 22)).foregroundStyle(kolor)
                        Text(t.nazwa).czcionka(12, wybrany ? .bold : .semibold)
                            .foregroundStyle(Barwy.tekst).lineLimit(1).minimumScaleFactor(0.7)
                    }
                    .frame(maxWidth: .infinity, minHeight: 66)
                    .background(wybrany ? kolor.opacity(0.14) : Barwy.tlo, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(wybrany ? kolor : Barwy.ramka, lineWidth: wybrany ? 2 : 1))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(wybrany ? .isSelected : [])
            }
        }
        .padding(.vertical, 4)
    }

    private func etykieta(_ id: String, _ tekst: String) -> some View {
        Text(tekst + " *").czcionka(12, .semibold)
            .foregroundStyle(f.bledyPol.contains(id) ? Self.czerwony : Barwy.slaby)
    }

    private func poleTekstu(_ id: String, _ nazwa: String, _ podpowiedz: String, tekst: Binding<String>,
                            rodzaj: UITextContentType? = nil) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            etykieta(id, nazwa)
            TextField(podpowiedz, text: tekst)
                .textContentType(rodzaj)
                .focused($pole, equals: id)
                .submitLabel(.next)
                .czcionka(16)
        }
        .id(id)
    }

    @ViewBuilder private var polozenie: some View {
        if let p = f.polozenie {
            HStack {
                Label(Napisy.t("app.alertLocationAdded", "Dołączone położenie z GPS (±{n} m)", n: p.dokladnosc), systemImage: "location.fill")
                    .czcionka(14).foregroundStyle(Barwy.tekst)
                Spacer()
                Button { f.polozenie = nil } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(Barwy.slaby) }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Napisy.t("app.alertLocationRemove", "Usuń położenie"))
            }
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Button {
                    Task { await f.dodajPolozenie() }
                } label: {
                    HStack {
                        Label(Napisy.t("app.alertAddLocation", "Dodaj moje położenie"), systemImage: "location")
                        if f.szukaPolozenia { Spacer(); ProgressView() }
                    }
                    .czcionka(15)
                }
                .disabled(f.szukaPolozenia)
                if let b = f.bladPolozenia { Text(b).czcionka(12).foregroundStyle(Barwy.slaby) }
            }
        }
    }

    @ViewBuilder private var zalacznik: some View {
        if f.przygotowanie {
            HStack(spacing: 10) {
                ProgressView()
                Text(Napisy.t("app.alertPreparing", "Przygotowuję plik…")).czcionka(14).foregroundStyle(Barwy.slaby)
            }
        } else if let z = f.zalacznik {
            HStack(spacing: 12) {
                Group {
                    if let mini = z.miniatura {
                        Image(uiImage: mini).resizable().scaledToFill()
                    } else {
                        Image(systemName: z.rodzaj == .film ? "film" : "photo").foregroundStyle(Barwy.slaby)
                    }
                }
                .frame(width: 64, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(alignment: .bottomLeading) {
                    if z.rodzaj == .film {
                        Image(systemName: "play.circle.fill").foregroundStyle(.white).padding(4)
                    }
                }
                Text(z.opis).czcionka(14).foregroundStyle(Barwy.tekst)
                Spacer()
                Button { f.ustawZalacznik(nil) } label: { Image(systemName: "xmark.circle.fill").font(.system(size: 20)).foregroundStyle(Barwy.slaby) }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Napisy.t("app.alertRemoveAttachment", "Usuń załącznik"))
            }
        } else {
            if aparatDostepny {
                Button { aparat = true } label: {
                    Label(Napisy.t("app.alertTakePhoto", "Zrób zdjęcie lub film"), systemImage: "camera").czcionka(15)
                }
            }
            PhotosPicker(selection: $wybor, matching: .any(of: [.images, .videos])) {
                Label(Napisy.t("app.alertFromLibrary", "Wybierz z galerii"), systemImage: "photo.on.rectangle").czcionka(15)
            }
        }
        if let b = f.bladZalacznika {
            Text(b).czcionka(13).foregroundStyle(Self.czerwony)
        }
    }

    private func bladWysylki(_ b: BladWysylki) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(b.tekst, systemImage: "exclamationmark.circle.fill")
                .czcionka(14, .semibold).foregroundStyle(Self.czerwony)
            if b.strona {
                Button {
                    www = StronaWWW(sciezka: "https://tvml.pl/#alert")
                } label: {
                    Label(Napisy.t("app.alertOpenWeb", "Otwórz formularz na tvml.pl"), systemImage: "safari").czcionka(14)
                }
            }
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder private var przyciskWyslij: some View {
        if case .wysylanie(let p) = f.stan {
            VStack(alignment: .leading, spacing: 8) {
                ProgressView(value: p)
                    .tint(Self.czerwony)
                Text(Napisy.t("app.alertSending", "Wysyłanie… {n}%", n: Int((p * 100).rounded())))
                    .czcionka(14).foregroundStyle(Barwy.slaby)
            }
            .padding(.vertical, 8)
        } else {
            Button {
                pole = nil
                Task { await f.wyslij() }
            } label: {
                Label(Napisy.t("app.alertSend", "Wyślij zgłoszenie"), systemImage: "paperplane.fill")
                    .czcionka(16, .bold).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(Self.czerwony, in: RoundedRectangle(cornerRadius: 14))
            }
            .buttonStyle(.plain)
            .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
        }
    }

    // MARK: potwierdzenie

    private func potwierdzenie(_ p: PotwierdzenieAlertu) -> some View {
        ScrollView {
            VStack(spacing: 16) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 64)).foregroundStyle(Color(hex: "#1f9d55"))
                    .padding(.top, 40)
                Text(Napisy.t("app.alertThanks", "Dziękujemy!")).font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst)
                Text(p.komunikat).czcionka(16).foregroundStyle(Barwy.tekst).multilineTextAlignment(.center)
                if !p.numer.isEmpty {
                    VStack(spacing: 4) {
                        Text(Napisy.t("app.alertNumber", "Numer zgłoszenia")).czcionka(12, .semibold).foregroundStyle(Barwy.slaby)
                        Text(p.numer).font(.system(size: 22, weight: .bold, design: .monospaced)).foregroundStyle(Barwy.tekst)
                            .textSelection(.enabled)
                    }
                    .padding(.horizontal, 24).padding(.vertical, 12)
                    .background(Barwy.karta, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Barwy.ramka))
                }
                Text(Napisy.t("app.alertReviewNote", "Redakcja sprawdzi zgłoszenie przed publikacją. Jeśli będzie trzeba, skontaktuje się z Tobą."))
                    .czcionka(14).foregroundStyle(Barwy.slaby).multilineTextAlignment(.center)
                Button { zamknij() } label: {
                    Text(Napisy.t("app.done", "Gotowe"))
                        .czcionka(16, .bold).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(Barwy.granat, in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
                .padding(.top, 8)
            }
            .padding(24)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
        }
    }
}
