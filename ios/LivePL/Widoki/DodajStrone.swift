import SwiftUI

/// Dodawanie strony do „Moich stron” (z Reżyserki albo z menu otwartej strony).
struct DodajStrone: View {
    @EnvironmentObject private var p: Przegladarka
    @Environment(\.dismiss) private var zamknijOkno
    @State private var adres: String
    @State private var nazwa: String
    @State private var blad: String?
    @FocusState private var fokusAdresu: Bool

    init(strona: MojaStrona) {
        _adres = State(initialValue: strona.adres)
        _nazwa = State(initialValue: strona.nazwa)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("np. livepl.eu", text: $adres)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .focused($fokusAdresu)
                } header: {
                    Text("Adres")
                }
                Section {
                    TextField("np. Strona firmy", text: $nazwa)
                        .onChange(of: nazwa) { _, nowa in
                            if nowa.count > 24 { nazwa = String(nowa.prefix(24)) }
                        }
                } header: {
                    Text("Nazwa")
                } footer: {
                    if let blad {
                        Text(blad).foregroundStyle(Kolor.czerwony)
                    } else {
                        Text("Strona pojawi się w Reżyserce, w „Moich stronach”. Lista zostaje tylko w tym telefonie.")
                    }
                }
            }
            .navigationTitle("Dodaj stronę")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Anuluj") { zamknijOkno() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Dodaj") { dodaj() }
                        .disabled(adres.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear {
                if adres.isEmpty { fokusAdresu = true }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func dodaj() {
        guard let pelny = Adres.adresStrony(adres) else {
            blad = "To nie wygląda na adres strony (np. livepl.eu)."
            return
        }
        let strona = MojaStrona(adres: pelny, nazwa: nazwa.trimmingCharacters(in: .whitespaces))
        if let problem = p.dodajStrone(strona) {
            blad = problem
            return
        }
        zamknijOkno()
    }
}
