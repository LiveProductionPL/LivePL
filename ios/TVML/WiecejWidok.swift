import SwiftUI
import SafariServices

/// Więcej: zapisane artykuły, działy tvml.pl, wygląd, rozmiar tekstu, strony redakcji i media społecznościowe.
/// „Zgłoś zdarzenie”, sondy i powiadomienia przejdą tu natywnie razem z nowym API tvml.pl.
struct WiecejWidok: View {
    @EnvironmentObject private var m: Magazyn
    @AppStorage("tvml-wyglad") private var wyglad = "auto"
    @AppStorage("tvml-rozmiar") private var rozmiar = 1
    @State private var strona: StronaWWW?

    var body: some View {
        List {
            Section {
                Button { strona = StronaWWW(sciezka: "") } label: {
                    Label("Zgłoś zdarzenie (Alert TVML)", systemImage: "exclamationmark.triangle.fill")
                        .font(.system(size: 16, weight: .bold)).foregroundStyle(Color(hex: "#c8102e"))
                }
            } footer: {
                Text("Formularz jest na stronie głównej tvml.pl, przycisk „Zgłoś zdarzenie”.")
            }
            Section("Moje") {
                NavigationLink { ZapisaneWidok() } label: {
                    Label("Zapisane artykuły", systemImage: "bookmark")
                        .badge(m.zapisane.count)
                }
            }
            Section("Na tvml.pl") {
                pozycja("Ogłoszenia drobne", "megaphone", "lista/ogloszenia")
                pozycja("Fotorelacje", "photo.on.rectangle", "lista/fotorelacje")
                pozycja("Artykuły sponsorowane", "tag", "lista/aktualnosci/artykuly-sponsorowane")
            }
            Section("Wygląd") {
                Picker("Tryb", selection: $wyglad) {
                    Text("Jak w telefonie").tag("auto")
                    Text("Dzień").tag("dzien")
                    Text("Noc").tag("noc")
                }
                Picker("Rozmiar tekstu", selection: $rozmiar) {
                    Text("Mały").tag(0)
                    Text("Średni").tag(1)
                    Text("Duży").tag(2)
                }
            }
            Section("TVML") {
                pozycja("O nas", "info.circle", "strona/o-nas")
                pozycja("Redakcja", "person.2", "strona/redakcja")
                pozycja("Reklama", "megaphone.fill", "strona/reklama")
                pozycja("Kontakt", "envelope", "strona/kontakt")
                pozycja("Regulamin", "doc.text", "strona/regulamin")
                pozycja("Polityka prywatności", "hand.raised", "strona/polityka-prywatnosci")
            }
            Section("Obserwuj nas") {
                Link(destination: URL(string: "https://www.facebook.com/TVMLpl")!) { Label("Facebook", systemImage: "f.circle") }
                Link(destination: URL(string: "https://www.youtube.com/@TVMLlive")!) { Label("YouTube", systemImage: "play.rectangle") }
                Link(destination: URL(string: "https://www.instagram.com/tvmlpl/")!) { Label("Instagram", systemImage: "camera") }
                Link(destination: URL(string: "https://www.tiktok.com/@tvml.pl")!) { Label("TikTok", systemImage: "music.note") }
            }
            Section {
                Text("TVML \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "") · Telewizja Młodzieżowa Leszno")
                    .font(.system(size: 12)).foregroundStyle(Barwy.slaby)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Barwy.tlo)
        .sheet(item: $strona) { s in Safari(url: URL(string: "https://tvml.pl/" + s.sciezka)!).ignoresSafeArea() }
    }

    private func pozycja(_ tytul: String, _ ikona: String, _ sciezka: String) -> some View {
        Button { strona = StronaWWW(sciezka: sciezka) } label: {
            Label(tytul, systemImage: ikona).foregroundStyle(Barwy.tekst)
        }
    }
}

struct StronaWWW: Identifiable { let sciezka: String; var id: String { sciezka } }

struct Safari: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }
    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}

struct ZapisaneWidok: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dismiss) private var wroc
    @State private var lista: [Artykul] = []
    @State private var laduje = true

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                Button { wroc() } label: {
                    HStack(spacing: 2) { Image(systemName: "chevron.left"); Text("Wróć") }
                        .font(.system(size: 15, weight: .bold)).foregroundStyle(Barwy.tekst).frame(height: 44)
                }
                Text("Zapisane").font(Czcionka.naglowek(26)).foregroundStyle(Barwy.tekst)
                if laduje { ProgressView().padding(40) }
                else if lista.isEmpty {
                    Text("Nie masz zapisanych artykułów. Zapiszesz je ikoną zakładki w artykule.")
                        .font(.system(size: 15)).foregroundStyle(Barwy.slaby).padding(24)
                }
                ForEach(lista) { a in
                    NavigationLink(value: a) { WierszArtykulu(artykul: a) }.buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        .background(Barwy.tlo)
        .toolbar(.hidden, for: .navigationBar)
        .task {
            var wynik: [Artykul] = []
            for id in m.zapisane.prefix(30) {
                if let a = await m.artykul(id) { wynik.append(a) }
            }
            lista = wynik
            laduje = false
        }
    }
}

struct SzukajWidok: View {
    @EnvironmentObject private var m: Magazyn
    @Environment(\.dismiss) private var zamknij
    @State private var fraza = ""
    @State private var wyniki: [Artykul] = []
    @State private var szuka = false

    var body: some View {
        NavigationStack {
            List {
                if szuka { ProgressView() }
                else if !fraza.isEmpty && wyniki.isEmpty { Text("Nic nie znaleziono.").foregroundStyle(Barwy.slaby) }
                ForEach(wyniki) { a in
                    Button {
                        zamknij()
                        m.otworz(a)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(a.tytul).font(.system(size: 15, weight: .semibold)).foregroundStyle(Barwy.tekst)
                            Text([a.kategoria, a.kiedy].filter { !$0.isEmpty }.joined(separator: " · ")).font(.system(size: 12)).foregroundStyle(Barwy.slaby)
                        }
                    }
                }
            }
            .navigationTitle("Szukaj")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $fraza, placement: .navigationBarDrawer(displayMode: .always), prompt: "Szukaj na tvml.pl")
            .onSubmit(of: .search) {
                Task {
                    szuka = true
                    wyniki = await m.szukaj(fraza)
                    szuka = false
                }
            }
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Zamknij") { zamknij() } } }
        }
    }
}
