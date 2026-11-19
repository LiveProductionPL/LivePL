import Foundation
import Security

/// Zaufanie do urzędu certyfikacji LivePL (LivePL Root CA R1) w aplikacji, jak src/nasze-ca.js w LivePL na Windows.
/// Działa tylko dla naszych domen (domeny.json) i tylko w LivePL: iPhone ani inne aplikacje nic o tym nie wiedzą,
/// więc niczego nie trzeba instalować. Sprawdzany jest cały łańcuch do naszego roota (podpisy, daty, nazwa strony,
/// przeznaczenie certyfikatu) zasadami SSL systemu; publiczne certyfikaty (Let's Encrypt) działają jak zawsze.
enum NaszeCA {
    private static let rooty: [SecCertificate] = {
        guard let plik = Bundle.main.url(forResource: "livepl-root-ca-r1", withExtension: "crt"),
              let pem = try? String(contentsOf: plik, encoding: .utf8)
        else { return [] }
        let base64 = pem
            .components(separatedBy: .newlines)
            .filter { !$0.hasPrefix("-----") }
            .joined()
        guard let der = Data(base64Encoded: base64),
              let cert = SecCertificateCreateWithData(nil, der as CFData)
        else { return [] }
        return [cert]
    }()

    private static let domeny: [String] = {
        struct Plik: Decodable { let domeny: [String] }
        guard let url = Bundle.main.url(forResource: "domeny", withExtension: "json"),
              let dane = try? Data(contentsOf: url),
              let plik = try? JSONDecoder().decode(Plik.self, from: dane)
        else { return [] }
        return plik.domeny.map { $0.lowercased() }
    }()

    static func naszaDomena(_ host: String) -> Bool {
        let h = host.lowercased()
        return domeny.contains { h == $0 || h.hasSuffix("." + $0) }
    }

    /// Odpowiedź na pytanie WebKitu o certyfikat serwera. Dla obcych stron i braku naszego roota decyduje system.
    static func odpowiedz(_ wyzwanie: URLAuthenticationChallenge) async -> (URLSession.AuthChallengeDisposition, URLCredential?) {
        let miejsce = wyzwanie.protectionSpace
        guard miejsce.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let zaufanie = miejsce.serverTrust,
              !rooty.isEmpty,
              naszaDomena(miejsce.host)
        else { return (.performDefaultHandling, nil) }

        SecTrustSetPolicies(zaufanie, SecPolicyCreateSSL(true, miejsce.host as CFString))
        SecTrustSetAnchorCertificates(zaufanie, rooty as CFArray)
        // nasz root dochodzi do zaufanych systemowych, więc Let's Encrypt dalej przechodzi
        SecTrustSetAnchorCertificatesOnly(zaufanie, false)

        let kolejka = DispatchQueue(label: "eu.livepl.zaufanie")
        let wynik: Bool = await withCheckedContinuation { dalej in
            kolejka.async {
                SecTrustEvaluateAsyncWithError(zaufanie, kolejka) { _, ok, _ in
                    dalej.resume(returning: ok)
                }
            }
        }
        return wynik ? (.useCredential, URLCredential(trust: zaufanie)) : (.performDefaultHandling, nil)
    }
}
