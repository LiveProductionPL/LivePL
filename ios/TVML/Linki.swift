import SwiftUI

/// Linki do aplikacji (TVML 2.2, Karol 24.11: „Oba do 2.2”):
/// - https://tvml.pl/artykul/<id>… (Universal Links, applinks:tvml.pl) otwiera artykuł w aplikacji, jak App Links na Androidzie;
/// - https://tvml.pl/app/podglad?t=<token> (kod QR z kreatora „Aplikacja Mobilna” w panelu) i tvml://preview?t=<token>
///   (przycisk na stronie zapasowej) otwierają podgląd szkicu ustawień aplikacji.
/// Podgląd: GET /api/v1/config?preview=<token> zwraca zwykły config ze szkicem mobileApp i polem
/// preview {expiresAt, label, revision}. Token działa 30 minut; zły daje 400, wygasły 410 (Panel-TVML: backend/lib/app_preview.php).
/// Podgląd żyje tylko w pamięci aplikacji i widzi go tylko ten telefon.
struct Podglad: Equatable {
    /// Zrzut ekranu w CI (-ekran podglad): pasek bez pobierania szkicu z serwera.
    static let tokenZrzutu = "zrzut-ekranu"

    let token: String
    var etykieta: String
    var wygasa: Date
    var wersja = 0

    init(token: String) {
        self.token = token
        etykieta = Napisy.t("app.preview", "Podgląd")
        wygasa = Date().addingTimeInterval(30 * 60)
    }

    mutating func ustaw(_ d: Slownik) {
        if !d.s("label").isEmpty { etykieta = d.s("label") }
        if let w = ISO8601DateFormatter().date(from: d.s("expiresAt")) { wygasa = w }
        wersja = d.i("revision")
    }

    /// Token z panelu: 32 bajty w base64url (43 znaki); inne odrzucamy bez pytania serwera.
    static func poprawny(_ t: String) -> Bool {
        (16...128).contains(t.count) && t.allSatisfy { $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-" || $0 == "_") }
    }
}

extension Magazyn {
    /// Link z zewnątrz (Universal Link albo tvml://).
    func otworzLink(_ url: URL) {
        let host = (url.host ?? "").lowercased()
        let nasz = (url.scheme == "https" || url.scheme == "http") && (host == "tvml.pl" || host == "www.tvml.pl")
        let czesci = url.pathComponents.filter { $0 != "/" }
        let t = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "t" }?.value

        if (url.scheme == "tvml" && host == "preview") || (nasz && czesci.first == "app" && czesci.dropFirst().first == "podglad") {
            if let t, Podglad.poprawny(t) { Task { await rozpocznijPodglad(t) } }
            return
        }
        // /artykul/678328 albo /artykul/678328-tytul: numer z początku (tak jak Android)
        if nasz, czesci.first == "artykul", let s = czesci.dropFirst().first, let id = Int(s.prefix(while: { $0.isASCII && $0.isNumber })) {
            Task { if let a = await artykul(id) { otworz(a) } }
        }
    }

    func rozpocznijPodglad(_ token: String) async {
        if podglad?.token != token { podglad = Podglad(token: token) }
        podgladWygasl = false
        await odswiez(wymus: true)
    }

    /// Koniec podglądu: przycisk „Zakończ”, wygaśnięcie (410) albo koniec czasu. Odświeżenie robi wywołujący.
    func zakonczPodglad(wygasl: Bool) {
        guard podglad != nil else { return }
        podglad = nil
        if wygasl { podgladWygasl = true }
    }

    /// Config z API: przy podglądzie szkic z panelu (bez pamięci podręcznej), a gdy podgląd wygasł, zwykły.
    /// Zwraca też token podglądu, z którym config pobrano (nil = zwykły).
    func konfiguracja() async -> (Slownik?, String?) {
        if let p = podglad {
            if p.token == Podglad.tokenZrzutu {
                return ((try? await Siec.v1("config", ["features": "ai-themes"])) as? Slownik, p.token)
            }
            do {
                if let k = try await Siec.v1("config", ["features": "ai-themes", "preview": p.token], swiezy: true) as? Slownik {
                    if podglad?.token == p.token, var nowy = podglad {
                        nowy.ustaw(k.slownik("preview"))
                        podglad = nowy
                    }
                    return (k, p.token)
                }
            } catch let b as Siec.BladHttp where b.kod == 400 || b.kod == 410 {
                if podglad?.token == p.token { zakonczPodglad(wygasl: true) }
            } catch {
                // brak sieci albo błąd serwera: nic nie zmieniamy, podgląd zostaje do następnej próby
                return (nil, p.token)
            }
        }
        return ((try? await Siec.v1("config", ["features": "ai-themes"])) as? Slownik, nil)
    }

    /// Po czasie z expiresAt aplikacja sama wraca do opublikowanych ustawień.
    func zaplanujKoniecPodgladu() {
        zegarPodgladu?.cancel()
        guard let p = podglad, p.token != Podglad.tokenZrzutu else { return }
        let za = max(0, p.wygasa.timeIntervalSinceNow) + 1
        zegarPodgladu = Task { [weak self] in
            try? await Task.sleep(for: .seconds(za))
            guard !Task.isCancelled, let self, self.podglad?.token == p.token else { return }
            self.zakonczPodglad(wygasl: true)
            await self.odswiez(wymus: true)
        }
    }
}

/// Pasek pod banerem, gdy aplikacja pokazuje szkic z panelu: etykieta i godzina końca, „Odśwież” i „Zakończ”.
struct PasekPodgladu: View {
    @EnvironmentObject private var m: Magazyn
    let p: Podglad

    private var godzina: String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f.string(from: p.wygasa)
    }

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "eye.fill").font(.system(size: 13, weight: .bold)).accessibilityHidden(true)
            Text(verbatim: p.etykieta + " · " + Napisy.t("app.previewUntil", "do {t}").replacingOccurrences(of: "{t}", with: godzina))
                .czcionka(13, .bold).lineLimit(2).minimumScaleFactor(0.8)
            Spacer(minLength: 4)
            przycisk(Napisy.t("app.refresh", "Odśwież")) { Task { await m.odswiez(wymus: true) } }
            przycisk(Napisy.t("app.previewEnd", "Zakończ")) {
                m.zakonczPodglad(wygasl: false)
                Task { await m.odswiez(wymus: true) }
            }
        }
        .foregroundStyle(Color(hex: "#1d1400"))
        .padding(.horizontal, 12).padding(.vertical, 6)
        .frame(maxWidth: .infinity)
        .background(Barwy.zloto)
        .accessibilityElement(children: .contain)
    }

    private func przycisk(_ tytul: String, akcja: @escaping () -> Void) -> some View {
        Button(action: akcja) {
            Text(tytul).czcionka(12, .bold).padding(.horizontal, 10).frame(minHeight: 30)
                .background(Color.black.opacity(0.12), in: Capsule())
        }
        .buttonStyle(.plain)
    }
}
