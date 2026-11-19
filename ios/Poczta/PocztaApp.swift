import SwiftUI
import UserNotifications

/// Poczta LivePL: firmowa poczta (poczta.livepl.eu) jako osobna aplikacja na iPhone'a, bez paska adresu.
/// Ten sam silnik co przeglądarka LivePL (folder Wspolne): karty, pobieranie plików, certyfikaty LivePL,
/// logowanie trwające 7 dni od ostatniego użycia. Linki do innych stron otwierają się w przeglądarce.
@main
struct PocztaApp: App {
    @StateObject private var przegladarka: Przegladarka = {
        let p = Przegladarka()
        p.dozwoloneHosty = ["poczta.livepl.eu"]
        return p
    }()

    var body: some Scene {
        WindowGroup {
            PocztaGlowny()
                .environmentObject(przegladarka)
                .tint(Kolor.czerwony)
        }
    }
}

struct PocztaGlowny: View {
    @EnvironmentObject private var p: Przegladarka
    @Environment(\.scenePhase) private var faza

    var body: some View {
        ZStack {
            Color(uiColor: UIColor(rgb: 0x071c2d)).ignoresSafeArea()
            if let poczta = p.karta(programu: "poczta") {
                PocztaStrona(karta: poczta)
            }
        }
        // okna otwierane przez pocztę (podgląd załącznika, logowanie do innego konta) jako arkusz nad pocztą
        .sheet(item: oknoStrony) { karta in
            OknoStrony(karta: karta)
        }
        .onAppear(perform: start)
        .onChange(of: faza, initial: true) { _, nowa in
            if nowa == .active {
                p.zastosujMotyw()
                // certyfikat „Urządzenie LivePL” zakłada się sam (klucz w Secure Enclave), jak w przeglądarce LivePL
                Task { await Urzadzenie.wspolny.przyStarcie() }
            }
        }
    }

    private var oknoStrony: Binding<Karta?> {
        Binding(
            get: {
                guard let a = p.aktywna, a.programID != "poczta" else { return nil }
                return a
            },
            set: { nowa in
                if nowa == nil, let a = p.aktywna, a.programID != "poczta" { p.zamknij(a) }
            }
        )
    }

    private func start() {
        p.zastosujMotyw()
        guard p.karta(programu: "poczta") == nil, let poczta = p.program("poczta") else { return }
        p.otworz(poczta)
    }
}

/// Poczta na cały ekran: jej wersja na telefon sama robi miejsce na pasek stanu i dolny pasek iPhone'a.
struct PocztaStrona: View {
    @ObservedObject var karta: Karta

    var body: some View {
        ZStack(alignment: .top) {
            WebWidok(webView: karta.webView)
                .ignoresSafeArea()
            if let blad = karta.blad {
                WidokBledu(blad: blad) { karta.sprobujPonownie() }
            }
            if karta.laduje {
                PasekPostepu(postep: karta.postep)
            }
        }
        .onAppear {
            karta.webView.scrollView.contentInsetAdjustmentBehavior = .never
        }
        .onChange(of: karta.tytul, initial: true) { _, tytul in
            Licznik.ustaw(z: tytul)
        }
    }
}

/// Okno otwarte przez pocztę, z przyciskiem „Gotowe”.
struct OknoStrony: View {
    @EnvironmentObject private var p: Przegladarka
    @ObservedObject var karta: Karta

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                WebWidok(webView: karta.webView)
                if karta.laduje { PasekPostepu(postep: karta.postep) }
            }
            .navigationTitle(karta.tytul.isEmpty ? Adres.host(karta.adres) : karta.tytul)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Gotowe") { p.zamknij(karta) }
                }
                ToolbarItem(placement: .topBarLeading) {
                    if let url = karta.adres {
                        ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }
                    }
                }
            }
        }
    }
}

/// Liczba nieprzeczytanych z tytułu poczty (np. „(12) Poczta”) na ikonie aplikacji, jak licznik w LivePL na Windows.
@MainActor
enum Licznik {
    private static var ostatni = -1
    private static var zapytano = false

    static func ustaw(z tytul: String) {
        let liczba = odczytaj(tytul)
        guard liczba != ostatni else { return }
        ostatni = liczba
        let centrum = UNUserNotificationCenter.current()
        if liczba > 0 && !zapytano {
            // iOS pozwala pokazać liczbę na ikonie dopiero po zgodzie (tylko plakietka, bez powiadomień)
            zapytano = true
            centrum.requestAuthorization(options: [.badge]) { _, _ in
                Task { @MainActor in centrum.setBadgeCount(ostatni) }
            }
        } else {
            centrum.setBadgeCount(liczba)
        }
    }

    private static func odczytaj(_ tytul: String) -> Int {
        for wzor in [#"^\s*\((\d+)\)"#, #"(\d+)\s+nieprzeczytan"#] {
            if let zakres = tytul.range(of: wzor, options: [.regularExpression, .caseInsensitive]) {
                let cyfry = tytul[zakres].filter(\.isNumber)
                return Int(cyfry) ?? 0
            }
        }
        return 0
    }
}
