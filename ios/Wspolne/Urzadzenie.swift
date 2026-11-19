import Foundation
import Security
import CryptoKit
import UIKit

/// Rejestracja tego iPhone'a w urzędzie LivePL (certyfikat „Urządzenie LivePL”), jak src/urzadzenie.js na Windows.
/// Klucz powstaje w Secure Enclave telefonu (odpowiednik TPM) i nie da się go skopiować ani wyeksportować.
/// Wniosek (PKCS#10, EC P-256) idzie do cert.livepl.eu, urząd wystawia certyfikat od razu, a LivePL sprawdza,
/// że pasuje do klucza i pochodzi z LivePL CA 1. Co kilka godzin LivePL wysyła krótki raport (wersja, połączenie),
/// a w odpowiedzi dowiaduje się, czy urząd nie unieważnił tego iPhone'a.
@MainActor
final class Urzadzenie: ObservableObject {
    static let wspolny = Urzadzenie()

    @Published private(set) var zapis = Zapis()
    @Published private(set) var trwa = false
    @Published var blad: String?

    struct Zapis: Codable {
        var stan = "brak"            // brak, wyslany, gotowe, odrzucony, uniewazniony
        var id: String?
        var kod: String?
        var csr: String?
        var sprzetowy = false        // klucz w Secure Enclave
        var certyfikat: String?
        var waznyDo: Date?
        var raport: Date?
        var polaczenie: String?
    }

    private static let adres = "https://cert.livepl.eu"
    private static let tagKlucza = Data("eu.livepl.przegladarka.urzadzenie".utf8)
    private static let usluga = "eu.livepl.przegladarka.urzadzenie"
    private static let coIleRaport: TimeInterval = 6 * 3600

    private init() {
        zapis = Urzadzenie.wczytaj() ?? Zapis()
    }

    var zarejestrowany: Bool { zapis.stan == "gotowe" }

    // MARK: - Rejestracja

    func zarejestruj() async {
        guard !trwa else { return }
        trwa = true
        blad = nil
        defer { trwa = false }
        do {
            var z = zapis
            if z.csr == nil || ["odrzucony", "uniewazniony"].contains(z.stan) {
                if ["odrzucony", "uniewazniony"].contains(z.stan) { Urzadzenie.usunKlucz() }
                let (klucz, sprzetowy) = try Urzadzenie.kluczUrzadzenia()
                let spki = try Urzadzenie.spki(klucz)
                let kod = Urzadzenie.kodZKlucza(spki)
                let csr = try Urzadzenie.wniosek(klucz: klucz, spki: spki, kod: kod)
                z = Zapis(stan: "brak", kod: kod, csr: csr, sprzetowy: sprzetowy)
            }
            let (status, dane) = try await Urzadzenie.zapytaj("/urzadzenia/wniosek", metoda: "POST", cialo: [
                "csr": z.csr ?? "",
                "nazwa": Urzadzenie.nazwa(kod: z.kod ?? ""),
                "uzytkownik": "",
                "system": "\(UIDevice.current.systemName) \(UIDevice.current.systemVersion)",
                "wersja": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "",
                "tpm": z.sprzetowy
            ])
            guard status == 200, let id = dane["id"] as? String else {
                throw Problem((dane["blad"] as? String) ?? "Urząd nie przyjął wniosku (\(status)).")
            }
            if let kod = dane["kod"] as? String, kod != z.kod {
                throw Problem("Kod z urzędu nie zgadza się z kluczem tego iPhone'a.")
            }
            z.id = id
            z.stan = "wyslany"
            ustaw(z)
            // urząd wystawia certyfikat od razu (rejestracja automatyczna)
            try await sprawdz()
            if zarejestrowany { await raport(teraz: true) }
        } catch {
            blad = opis(error)
        }
    }

    /// Odbiór certyfikatu po zatwierdzeniu wniosku.
    func sprawdz() async throws {
        var z = zapis
        guard let id = z.id, z.stan == "wyslany" else { return }
        let (status, dane) = try await Urzadzenie.zapytaj("/urzadzenia/stan/" + id)
        let stan = dane["stan"] as? String ?? ""
        if status == 404 || stan == "nieznany" {
            // urząd nie zna wniosku: przy następnej rejestracji wyślemy ten sam wniosek jeszcze raz
            z.stan = "brak"
            z.id = nil
            ustaw(z)
            return
        }
        if stan == "odrzucony" || stan == "uniewazniony" {
            z.stan = stan
            ustaw(z)
            return
        }
        guard stan == "wydany", let pem = dane["certyfikat"] as? String else { return }
        let cert = try Urzadzenie.sprawdzCertyfikat(pem)
        Urzadzenie.zapiszCertyfikatWPeku(cert)
        z.certyfikat = pem
        z.waznyDo = Urzadzenie.waznyDo(SecCertificateCopyData(cert) as Data)
        z.stan = "gotowe"
        ustaw(z)
    }

    /// Raport co kilka godzin; urząd odpowiada stanem (np. unieważniony) i opisem połączenia.
    func raport(teraz: Bool = false) async {
        var z = zapis
        guard let id = z.id, z.stan == "gotowe" else { return }
        if !teraz, let ostatni = z.raport, Date().timeIntervalSince(ostatni) < Urzadzenie.coIleRaport { return }
        do {
            let (status, dane) = try await Urzadzenie.zapytaj("/urzadzenia/raport/" + id, metoda: "POST", cialo: [
                // urząd zna tylko BitLocker; stan szyfrowania iPhone'a pokaże dopiero nowszy panel
                "dysk": "nieznany",
                // hasła trzyma pęk kluczy iOS (szyfrowany)
                "dpapi": true,
                "wersja": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
            ])
            z.raport = Date()
            if status == 200, let p = dane["polaczenie"] as? String { z.polaczenie = String(p.prefix(80)) }
            if dane["stan"] as? String == "uniewazniony" { z.stan = "uniewazniony" }
            ustaw(z)
        } catch {
            // bez sieci: następnym razem
        }
    }

    /// Przy każdym powrocie LivePL na ekran.
    /// Przy każdym powrocie aplikacji na ekran. Rejestracja idzie sama, bez pytania (jak certyfikaty w LivePL na Windows);
    /// po błędzie (np. brak sieci) kolejna próba najwcześniej po 10 minutach.
    func przyStarcie() async {
        #if targetEnvironment(simulator)
        // symulator (zrzuty na Macach GitHuba) nie zakłada „urządzeń” w prawdziwym urzędzie
        return
        #else
        if zapis.stan == "brak", !trwa, Date().timeIntervalSince(ostatniaProba) > 600 {
            ostatniaProba = Date()
            await zarejestruj()
            return
        }
        if zapis.stan == "wyslany" { try? await sprawdz() }
        await raport()
        #endif
    }

    private var ostatniaProba = Date.distantPast

    private func ustaw(_ z: Zapis) {
        zapis = z
        Urzadzenie.zapisz(z)
    }

    private func opis(_ e: Error) -> String {
        if let p = e as? Problem { return p.tekst }
        let n = e as NSError
        if n.domain == NSURLErrorDomain { return "Brak połączenia z urzędem LivePL. Spróbuj za chwilę." }
        return e.localizedDescription
    }

    struct Problem: Error { let tekst: String; init(_ t: String) { tekst = t } }

    // MARK: - Urząd (HTTPS, cert.livepl.eu ma certyfikat publiczny)

    private static func zapytaj(_ sciezka: String, metoda: String = "GET", cialo: [String: Any]? = nil) async throws -> (Int, [String: Any]) {
        guard let url = URL(string: adres + sciezka) else { throw Problem("Zły adres urzędu.") }
        var prosba = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        prosba.httpMethod = metoda
        prosba.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let cialo { prosba.httpBody = try JSONSerialization.data(withJSONObject: cialo) }
        let (dane, odpowiedz) = try await URLSession.shared.data(for: prosba)
        let status = (odpowiedz as? HTTPURLResponse)?.statusCode ?? 0
        let json = (try? JSONSerialization.jsonObject(with: dane)) as? [String: Any] ?? [:]
        if status >= 400 && status != 404 {
            throw Problem((json["blad"] as? String) ?? "Urząd odpowiedział \(status).")
        }
        return (status, json)
    }

    // MARK: - Klucz w Secure Enclave

    /// Istniejący klucz tego iPhone'a albo nowy (Secure Enclave; w symulatorze klucz programowy).
    private static func kluczUrzadzenia() throws -> (SecKey, Bool) {
        if let istniejacy = znajdzKlucz() {
            let atrybuty = SecKeyCopyAttributes(istniejacy) as? [String: Any]
            let token = atrybuty?[kSecAttrTokenID as String] as? String
            return (istniejacy, token == (kSecAttrTokenIDSecureEnclave as String))
        }
        var blad: Unmanaged<CFError>?
        let sprzetowy = SecureEnclave.isAvailable
        var prywatne: [String: Any] = [
            kSecAttrIsPermanent as String: true,
            kSecAttrApplicationTag as String: tagKlucza
        ]
        var atrybuty: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits as String: 256
        ]
        if sprzetowy {
            guard let dostep = SecAccessControlCreateWithFlags(kCFAllocatorDefault,
                                                               kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                                                               .privateKeyUsage, &blad)
            else { throw Problem("Nie udało się przygotować klucza w Secure Enclave.") }
            prywatne[kSecAttrAccessControl as String] = dostep
            atrybuty[kSecAttrTokenID as String] = kSecAttrTokenIDSecureEnclave
        } else {
            prywatne[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        }
        atrybuty[kSecPrivateKeyAttrs as String] = prywatne
        guard let klucz = SecKeyCreateRandomKey(atrybuty as CFDictionary, &blad) else {
            throw Problem("Nie udało się utworzyć klucza: \(blad?.takeRetainedValue().localizedDescription ?? "nieznany błąd").")
        }
        return (klucz, sprzetowy)
    }

    private static func znajdzKlucz() -> SecKey? {
        let zapytanie: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tagKlucza,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecReturnRef as String: true
        ]
        var wynik: CFTypeRef?
        guard SecItemCopyMatching(zapytanie as CFDictionary, &wynik) == errSecSuccess, let wynik else { return nil }
        return (wynik as! SecKey)
    }

    private static func usunKlucz() {
        SecItemDelete([kSecClass as String: kSecClassKey, kSecAttrApplicationTag as String: tagKlucza] as CFDictionary)
    }

    /// Klucz publiczny jako SubjectPublicKeyInfo (EC P-256).
    private static func spki(_ klucz: SecKey) throws -> [UInt8] {
        var blad: Unmanaged<CFError>?
        guard let publiczny = SecKeyCopyPublicKey(klucz),
              let punkt = SecKeyCopyExternalRepresentation(publiczny, &blad) as Data?
        else { throw Problem("Nie udało się odczytać klucza publicznego.") }
        let algorytm = DER.sekwencja([DER.oid([0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01]),      // ecPublicKey
                                      DER.oid([0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07])]) // prime256v1
        return DER.sekwencja([algorytm, DER.bity(Array(punkt))])
    }

    /// Ten sam kod co w panelu urzędu i w LivePL na Windows: 30 bitów SHA-256 klucza, np. K7F-2QX.
    static func kodZKlucza(_ spki: [UInt8]) -> String {
        let h = Array(SHA256.hash(data: Data(spki)))
        let bity = (Int(h[0]) << 22) | (Int(h[1]) << 14) | (Int(h[2]) << 6) | (Int(h[3]) >> 2)
        let alfabet = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        var kod = ""
        for i in stride(from: 5, through: 0, by: -1) { kod.append(alfabet[(bity >> (i * 5)) & 31]) }
        return String(kod.prefix(3)) + "-" + String(kod.suffix(3))
    }

    /// np. „iPhone-K7F2QX”, a w Poczcie LivePL „iPhone-K7F2QX Poczta” i w LivePL Connect „iPhone-K7F2QX Connect”
    /// (każda aplikacja ma własny klucz).
    private static func nazwa(kod: String) -> String {
        let aplikacja: String
        switch Bundle.main.bundleIdentifier {
        case "eu.livepl.poczta": aplikacja = " Poczta"
        case "eu.livepl.connect": aplikacja = " Connect"
        default: aplikacja = ""
        }
        return "\(UIDevice.current.model)-\(kod.replacingOccurrences(of: "-", with: ""))\(aplikacja)"
    }

    /// Wniosek PKCS#10 podpisany kluczem z Secure Enclave (ecdsa-with-SHA256).
    private static func wniosek(klucz: SecKey, spki: [UInt8], kod: String) throws -> String {
        func atrybut(_ oid: [UInt8], _ wartosc: [UInt8]) -> [UInt8] {
            DER.zbior([DER.sekwencja([DER.oid(oid), wartosc])])
        }
        let temat = DER.sekwencja([
            atrybut([0x55, 0x04, 0x06], DER.drukowalny("PL")),
            atrybut([0x55, 0x04, 0x0A], DER.utf8("Live Production")),
            atrybut([0x55, 0x04, 0x03], DER.utf8("LivePL \(nazwa(kod: kod))"))
        ])
        let info = DER.sekwencja([[0x02, 0x01, 0x00], temat, spki, [0xA0, 0x00]])
        var blad: Unmanaged<CFError>?
        guard let podpis = SecKeyCreateSignature(klucz, .ecdsaSignatureMessageX962SHA256, Data(info) as CFData, &blad) as Data? else {
            throw Problem("Nie udało się podpisać wniosku: \(blad?.takeRetainedValue().localizedDescription ?? "nieznany błąd").")
        }
        let algorytm = DER.sekwencja([DER.oid([0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x04, 0x03, 0x02])]) // ecdsa-with-SHA256
        let calosc = DER.sekwencja([info, algorytm, DER.bity(Array(podpis))])
        let base64 = Data(calosc).base64EncodedString(options: [.lineLength64Characters, .endLineWithLineFeed])
        return "-----BEGIN CERTIFICATE REQUEST-----\n\(base64)\n-----END CERTIFICATE REQUEST-----\n"
    }

    // MARK: - Certyfikat z urzędu

    /// Certyfikat musi pasować do klucza tego iPhone'a i pochodzić od LivePL CA 1 (łańcuch do LivePL Root CA R1).
    private static func sprawdzCertyfikat(_ pem: String) throws -> SecCertificate {
        guard let cert = certyfikat(pem: pem) else { throw Problem("Urząd przysłał uszkodzony certyfikat.") }
        guard let klucz = znajdzKlucz(), let naszPubliczny = SecKeyCopyPublicKey(klucz),
              let nasz = SecKeyCopyExternalRepresentation(naszPubliczny, nil) as Data?,
              let jegoKlucz = SecCertificateCopyKey(cert),
              let jego = SecKeyCopyExternalRepresentation(jegoKlucz, nil) as Data?,
              nasz == jego
        else { throw Problem("Certyfikat z urzędu nie pasuje do klucza tego iPhone'a.") }
        guard let root = zasob("livepl-root-ca-r1"), let ca1 = zasob("livepl-ca-1") else {
            throw Problem("Brak certyfikatów urzędu w aplikacji.")
        }
        var zaufanie: SecTrust?
        SecTrustCreateWithCertificates([cert, ca1] as CFArray, SecPolicyCreateBasicX509(), &zaufanie)
        guard let zaufanie else { throw Problem("Nie udało się sprawdzić certyfikatu.") }
        SecTrustSetAnchorCertificates(zaufanie, [root] as CFArray)
        SecTrustSetAnchorCertificatesOnly(zaufanie, true)
        guard SecTrustEvaluateWithError(zaufanie, nil) else {
            throw Problem("Certyfikat nie pochodzi z urzędu LivePL.")
        }
        return cert
    }

    /// Certyfikat w pęku kluczy obok klucza z Secure Enclave: razem tworzą tożsamość tego iPhone'a.
    private static func zapiszCertyfikatWPeku(_ cert: SecCertificate) {
        SecItemAdd([kSecClass as String: kSecClassCertificate,
                    kSecValueRef as String: cert,
                    kSecAttrLabel as String: "LivePL - urzadzenie"] as CFDictionary, nil)
    }

    private static func certyfikat(pem: String) -> SecCertificate? {
        let base64 = pem.components(separatedBy: .newlines).filter { !$0.hasPrefix("-----") }.joined()
        guard let der = Data(base64Encoded: base64) else { return nil }
        return SecCertificateCreateWithData(nil, der as CFData)
    }

    private static func zasob(_ nazwa: String) -> SecCertificate? {
        guard let url = Bundle.main.url(forResource: nazwa, withExtension: "crt"),
              let pem = try? String(contentsOf: url, encoding: .utf8) else { return nil }
        return certyfikat(pem: pem)
    }

    /// Data końca ważności z certyfikatu (tbsCertificate → validity → notAfter).
    private static func waznyDo(_ der: Data) -> Date? {
        let b = [UInt8](der)
        guard let cert = DER.czytaj(b, 0), let tbs = DER.czytaj(b, cert.tresc) else { return nil }
        var o = tbs.tresc
        guard var el = DER.czytaj(b, o) else { return nil }
        if el.tag == 0xA0 { o = el.koniec; guard let n = DER.czytaj(b, o) else { return nil }; el = n } // wersja
        for _ in 0..<3 { o = el.koniec; guard let n = DER.czytaj(b, o) else { return nil }; el = n }      // numer, algorytm, wystawca
        guard el.tag == 0x30, let od = DER.czytaj(b, el.tresc), let doKiedy = DER.czytaj(b, od.koniec) else { return nil }
        let tekst = String(decoding: b[doKiedy.tresc..<doKiedy.koniec], as: UTF8.self)
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = doKiedy.tag == 0x17 ? "yyMMddHHmmss'Z'" : "yyyyMMddHHmmss'Z'"
        return f.date(from: tekst)
    }

    // MARK: - Zapis stanu w pęku kluczy (zostaje po przeinstalowaniu, więc w urzędzie nie powstaje drugi wpis)

    private static func wczytaj() -> Zapis? {
        var wynik: CFTypeRef?
        let zapytanie: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                        kSecAttrService as String: usluga,
                                        kSecAttrAccount as String: "stan",
                                        kSecReturnData as String: true]
        guard SecItemCopyMatching(zapytanie as CFDictionary, &wynik) == errSecSuccess, let dane = wynik as? Data else { return nil }
        return try? JSONDecoder().decode(Zapis.self, from: dane)
    }

    private static func zapisz(_ z: Zapis) {
        guard let dane = try? JSONEncoder().encode(z) else { return }
        let klucz: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: usluga,
                                    kSecAttrAccount as String: "stan"]
        let status = SecItemUpdate(klucz as CFDictionary, [kSecValueData as String: dane] as CFDictionary)
        if status == errSecItemNotFound {
            var nowy = klucz
            nowy[kSecValueData as String] = dane
            nowy[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(nowy as CFDictionary, nil)
        }
    }
}

/// Najprostsze kodowanie i odczyt DER (wniosek PKCS#10, data z certyfikatu).
enum DER {
    static func dlugosc(_ n: Int) -> [UInt8] {
        if n < 0x80 { return [UInt8(n)] }
        var bajty: [UInt8] = []
        var x = n
        while x > 0 { bajty.insert(UInt8(x & 0xff), at: 0); x >>= 8 }
        return [0x80 | UInt8(bajty.count)] + bajty
    }
    static func tlv(_ tag: UInt8, _ tresc: [UInt8]) -> [UInt8] { [tag] + dlugosc(tresc.count) + tresc }
    static func sekwencja(_ elementy: [[UInt8]]) -> [UInt8] { tlv(0x30, elementy.flatMap { $0 }) }
    static func zbior(_ elementy: [[UInt8]]) -> [UInt8] { tlv(0x31, elementy.flatMap { $0 }) }
    static func oid(_ bajty: [UInt8]) -> [UInt8] { tlv(0x06, bajty) }
    static func utf8(_ s: String) -> [UInt8] { tlv(0x0C, Array(s.utf8)) }
    static func drukowalny(_ s: String) -> [UInt8] { tlv(0x13, Array(s.utf8)) }
    static func bity(_ b: [UInt8]) -> [UInt8] { tlv(0x03, [0x00] + b) }

    struct Element { let tag: UInt8; let tresc: Int; let koniec: Int }

    static func czytaj(_ b: [UInt8], _ od: Int) -> Element? {
        guard od + 1 < b.count else { return nil }
        var dl = Int(b[od + 1])
        var p = od + 2
        if dl & 0x80 != 0 {
            let n = dl & 0x7f
            guard n <= 4, p + n <= b.count else { return nil }
            dl = 0
            for i in 0..<n { dl = dl << 8 | Int(b[p + i]) }
            p += n
        }
        guard p + dl <= b.count else { return nil }
        return Element(tag: b[od], tresc: p, koniec: p + dl)
    }
}
