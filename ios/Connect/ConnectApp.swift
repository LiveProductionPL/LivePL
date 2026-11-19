import SwiftUI
import WebKit

/// LivePL Connect: zdalny pulpit Live Production (connect.livepl.eu) jako osobna aplikacja na iPhone'a i iPada.
/// Łączy się z komputerem po ID i haśle z programu LivePL Connect. Obraz i sterowanie idą bezpośrednio między
/// urządzeniami (WebRTC). Ten sam silnik co przeglądarka LivePL i Poczta LivePL (folder Wspolne), a do tego
/// mostek MostConnect: ekran nie gaśnie w trakcie sesji, wibracja przy przytrzymaniu i pełny ekran.
@main
struct ConnectApp: App {
    @StateObject private var przegladarka: Przegladarka = {
        let p = Przegladarka()
        p.dozwoloneHosty = [MostConnect.host]
        return p
    }()
    @StateObject private var most = MostConnect()

    var body: some Scene {
        WindowGroup {
            ConnectGlowny()
                .environmentObject(przegladarka)
                .environmentObject(most)
                .tint(Kolor.czerwony)
        }
    }
}

struct ConnectGlowny: View {
    @EnvironmentObject private var p: Przegladarka
    @EnvironmentObject private var most: MostConnect
    @Environment(\.scenePhase) private var faza

    /// Connect nie jest na liście programów przeglądarki (programy.json), więc ma własny wpis.
    static let program = Program(id: "connect", nazwa: "LivePL Connect", skrot: "Connect",
                                 adres: "https://\(MostConnect.host)/", ikona: nil, opis: nil,
                                 ukryty: true, szeroki: nil, tylkoZYubiKey: nil)

    var body: some View {
        ZStack {
            Color("TloStartu").ignoresSafeArea()
            if let connect = p.karta(programu: Self.program.id) {
                ConnectStrona(karta: connect)
            }
        }
        .statusBarHidden(most.pelnyEkran)
        .persistentSystemOverlays(most.pelnyEkran ? .hidden : .automatic)
        // okna otwierane przez stronę (np. pobieranie programu na komputer) jako arkusz nad Connect
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
                guard let a = p.aktywna, a.programID != Self.program.id else { return nil }
                return a
            },
            set: { nowa in
                if nowa == nil, let a = p.aktywna, a.programID != Self.program.id { p.zamknij(a) }
            }
        )
    }

    private func start() {
        p.dostosujKonfiguracje = { [most] konfiguracja in most.podlacz(konfiguracja) }
        p.zastosujMotyw()
        guard p.karta(programu: Self.program.id) == nil else { return }
        p.otworz(Self.program)
    }
}

/// Connect na cały ekran. Strona sama robi miejsce na pasek stanu, wycięcie ekranu i klawiaturę.
struct ConnectStrona: View {
    @ObservedObject var karta: Karta
    @EnvironmentObject private var most: MostConnect

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
        .onAppear { przygotuj(karta.webView) }
        .onChange(of: karta.laduje) { _, laduje in
            if laduje { most.koniecSesji() }
        }
    }

    /// Na zdalnym ekranie palec steruje myszą: przytrzymanie to prawy przycisk, przesunięcie od krawędzi
    /// albo w dół to ruch myszy. Dlatego bez gestu „wstecz”, bez odświeżania przeciągnięciem i bez podglądu linków.
    private func przygotuj(_ w: WKWebView) {
        w.allowsBackForwardNavigationGestures = false
        w.allowsLinkPreview = false
        w.isFindInteractionEnabled = false
        w.scrollView.refreshControl = nil
        w.scrollView.bounces = false
        w.scrollView.contentInsetAdjustmentBehavior = .never
    }
}
