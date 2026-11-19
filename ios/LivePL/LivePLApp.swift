import SwiftUI

/// LivePL na iPhone'a i iPada: programy Live Production, karty, Moje strony i tryb dzień/noc,
/// w tym samym układzie „Reżyserki” co przeglądarka LivePL na Windows.
@main
struct LivePLApp: App {
    @StateObject private var przegladarka = Przegladarka()

    var body: some Scene {
        WindowGroup {
            Glowny()
                .environmentObject(przegladarka)
                .tint(Kolor.czerwony)
        }
    }
}
