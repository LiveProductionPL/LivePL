import SwiftUI

/// Menu przeglądarki (jak ⋮ w LivePL na Windows): na ekranie Start w nagłówku, na otwartej stronie w menu ⋯.
struct MenuPrzegladarki: View {
    @EnvironmentObject private var p: Przegladarka

    var body: some View {
        Button { p.nowaKarta() } label: {
            Label("Nowa karta", systemImage: "plus.square.on.square")
        }
        Button { p.pokazListeKart = true } label: {
            Label("Otwarte karty", systemImage: "square.on.square")
        }
        Button { p.dodawanaStrona = MojaStrona(adres: "", nazwa: "") } label: {
            Label("Dodaj stronę do Moich stron", systemImage: "plus")
        }
        Divider()
        Picker(selection: $p.motyw) {
            Label("Automatycznie, jak telefon", systemImage: Motyw.auto.symbol).tag(Motyw.auto)
            Label("Dzień", systemImage: Motyw.dzien.symbol).tag(Motyw.dzien)
            Label("Noc", systemImage: Motyw.noc.symbol).tag(Motyw.noc)
        } label: {
            Label("Wygląd", systemImage: "circle.lefthalf.filled")
        }
        .pickerStyle(.menu)
        Divider()
        Button { p.pokazCertyfikat = true } label: {
            Label("Certyfikat LivePL dla iPhone'a", systemImage: "checkmark.shield")
        }
        Button { p.pokazInformacje = true } label: {
            Label("O programie", systemImage: "info.circle")
        }
        Button(role: .destructive) { p.pokazCzyszczenie = true } label: {
            Label("Wyczyść dane przeglądania…", systemImage: "trash")
        }
    }
}

/// Okna otwierane z menu przeglądarki; podpięte raz, w głównym widoku.
struct OknaMenuPrzegladarki: ViewModifier {
    @EnvironmentObject private var p: Przegladarka

    func body(content: Content) -> some View {
        content
            // LivePL ufa naszym certyfikatom sam (NaszeCA.swift). Certyfikat w całym iPhonie przyda się tylko innym
            // aplikacjom, a iOS pozwala go dodać wyłącznie przez Ustawienia z pliku pobranego w Safari.
            .alert("Certyfikat dla całego iPhone'a", isPresented: $p.pokazCertyfikat) {
                Button("Pobierz w Safari") {
                    if let url = URL(string: "https://cert.livepl.eu/ca/livepl-root-ca-r1.cer") {
                        UIApplication.shared.open(url)
                    }
                }
                Button("Anuluj", role: .cancel) {}
            } message: {
                Text("LivePL ufa certyfikatom LivePL sam i niczego nie trzeba instalować. Certyfikat w iPhonie przyda się tylko innym aplikacjom, np. Poczcie Apple przy podpisanych mailach.\n\niOS pozwala go dodać tylko przez Ustawienia: Safari pobierze certyfikat, potem Ustawienia → Pobrany profil → Instaluj, a na koniec Ogólne → To urządzenie → Ustawienia zaufania certyfikatów → włącz „LivePL Root CA R1”.")
            }
            .confirmationDialog("Wyczyścić dane przeglądania?", isPresented: $p.pokazCzyszczenie, titleVisibility: .visible) {
                Button("Wyczyść i zamknij karty", role: .destructive) { p.wyczyscDane() }
                Button("Anuluj", role: .cancel) {}
            } message: {
                Text("Wylogujesz się ze wszystkich stron, także z Poczty, a otwarte karty się zamkną. Moje strony i wygląd zostają.")
            }
            .sheet(isPresented: $p.pokazInformacje) {
                OProgramie()
            }
    }
}

extension View {
    func oknaMenuPrzegladarki() -> some View {
        modifier(OknaMenuPrzegladarki())
    }
}

struct OProgramie: View {
    @Environment(\.dismiss) private var zamknijOkno

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(spacing: 10) {
                        Image("Wstega").resizable().scaledToFit().frame(width: 88, height: 64)
                        HStack(spacing: 0) {
                            Text("Live").foregroundStyle(Kolor.tekst)
                            Text("PL").foregroundStyle(Kolor.czerwony)
                        }
                        .font(.system(size: 28, weight: .bold))
                        Text("Przeglądarka · wersja \(Bundle.main.wersjaLivePL)")
                            .font(.subheadline)
                            .foregroundStyle(Kolor.slaby)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .accessibilityElement(children: .combine)
                }
                Section {
                    Text("Tłumaczenie stron i Twoje dane (Moje strony, logowania, karty) zostają w tym telefonie. LivePL ufa certyfikatom urzędu LivePL tylko na naszych stronach.")
                        .font(.subheadline)
                        .foregroundStyle(Kolor.slaby)
                }
                Section {
                    Link(destination: URL(string: "https://livepl.eu/")!) {
                        Label("Strona LivePL", systemImage: "globe")
                    }
                    Link(destination: URL(string: "https://github.com/LiveProductionPL/LivePL")!) {
                        Label("Kod źródłowy (licencja MIT)", systemImage: "chevron.left.forwardslash.chevron.right")
                    }
                } footer: {
                    Text("© 2026 Live Production · Karol Kowalski")
                }
            }
            .navigationTitle("O programie")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Gotowe") { zamknijOkno() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
