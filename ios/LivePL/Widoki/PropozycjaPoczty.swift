import SwiftUI

/// Aplikacja Poczta LivePL widziana z przeglądarki LivePL: czy jest na telefonie, jak ją zainstalować i otworzyć.
@MainActor
enum AplikacjaPoczty {
    /// Schemat adresu, który rejestruje Poczta LivePL (project.yml, CFBundleURLTypes).
    static let otworz = URL(string: "livepl-poczta://")!
    /// Strona Poczty LivePL w App Store.
    static let instaluj = URL(string: "https://apps.apple.com/app/id6836741669")!
    private static let kluczOdrzucenia = "livepl-poczta-aplikacja-odrzucona"

    static var zainstalowana: Bool { UIApplication.shared.canOpenURL(otworz) }

    /// Czy Poczta LivePL jest już w App Store. Dopóki nie jest, propozycja się nie pokazuje.
    /// Odpowiedź „jest” zostaje zapamiętana, „nie ma” sprawdzamy znowu po godzinie.
    private static var wSklepie = false
    private static var sprawdzonoBrak: Date?

    static func jestWSklepie() async -> Bool {
        if wSklepie { return true }
        if let kiedy = sprawdzonoBrak, Date().timeIntervalSince(kiedy) < 3600 { return false }
        guard let adres = URL(string: "https://itunes.apple.com/lookup?bundleId=eu.livepl.poczta&country=pl"),
              let odp = try? await URLSession.shared.data(from: adres),
              let j = (try? JSONSerialization.jsonObject(with: odp.0)) as? [String: Any],
              let ile = j["resultCount"] as? Int else { return false }
        if ile > 0 { wSklepie = true } else { sprawdzonoBrak = Date() }
        return ile > 0
    }

    /// „×” chowa propozycję na 30 dni.
    static var odrzuconaNiedawno: Bool {
        let kiedy = UserDefaults.standard.double(forKey: kluczOdrzucenia)
        return kiedy > 0 && Date().timeIntervalSince1970 - kiedy < 30 * 86400
    }

    static func odrzuc() {
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: kluczOdrzucenia)
    }
}

/// Pod adresem, gdy otwarta jest Poczta, a aplikacji Poczta LivePL nie ma na telefonie.
struct PropozycjaPoczty: View {
    @ObservedObject var karta: Karta
    @Environment(\.scenePhase) private var faza
    @State private var pokaz = false

    var body: some View {
        Group {
            if pokaz {
                HStack(spacing: 10) {
                    Image("program-poczta")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 26, height: 26)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Poczta ma własną aplikację")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Kolor.tekst)
                        Text("Poczta LivePL: osobna ikona i liczba nieprzeczytanych")
                            .font(.caption)
                            .foregroundStyle(Kolor.slaby)
                            .lineLimit(2)
                    }
                    Spacer(minLength: 4)
                    Button("Zainstaluj") { UIApplication.shared.open(AplikacjaPoczty.instaluj) }
                        .font(.subheadline.weight(.semibold))
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                    Button {
                        AplikacjaPoczty.odrzuc()
                        withAnimation(.snappy) { pokaz = false }
                    } label: {
                        Image(systemName: "xmark")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Kolor.slaby)
                            .frame(width: 30, height: 30)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Nie teraz")
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Kolor.karta)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(Kolor.ramka).frame(height: 0.5)
                }
            }
        }
        .onAppear(perform: sprawdz)
        // po powrocie z App Store: aplikacja może już być zainstalowana
        .onChange(of: faza) { _, nowa in if nowa == .active { sprawdz() } }
    }

    private func sprawdz() {
        guard karta.programID == "poczta", !AplikacjaPoczty.zainstalowana, !AplikacjaPoczty.odrzuconaNiedawno else {
            pokaz = false
            return
        }
        Task {
            let jest = await AplikacjaPoczty.jestWSklepie()
            pokaz = jest && karta.programID == "poczta"
        }
    }
}
