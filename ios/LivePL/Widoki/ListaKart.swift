import SwiftUI

/// Lista otwartych programów i kart (przycisk „Karty” na dolnym pasku).
struct ListaKart: View {
    @EnvironmentObject private var p: Przegladarka
    @Environment(\.dismiss) private var zamknijOkno

    var body: some View {
        NavigationStack {
            List {
                let programowe = p.kartyProgramow
                let zwykle = p.zwykleKarty
                if !programowe.isEmpty {
                    Section("Programy") {
                        ForEach(programowe) { karta in
                            WierszListyKart(karta: karta, program: p.program(karta.programID)) { wybierz(karta) }
                        }
                        .onDelete { indeksy in
                            for i in indeksy { p.zamknij(programowe[i]) }
                        }
                    }
                }
                Section("Karty") {
                    if zwykle.isEmpty {
                        Text("Brak otwartych kart.")
                            .foregroundStyle(Kolor.slaby)
                    }
                    ForEach(zwykle) { karta in
                        WierszListyKart(karta: karta, program: nil) { wybierz(karta) }
                    }
                    .onDelete { indeksy in
                        for i in indeksy { p.zamknij(zwykle[i]) }
                    }
                }
            }
            .navigationTitle("Otwarte")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Zamknij wszystkie", role: .destructive) {
                        p.zamknijWszystkie()
                        zamknijOkno()
                    }
                    .disabled(p.karty.isEmpty)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Gotowe") { zamknijOkno() }
                        .fontWeight(.semibold)
                }
                ToolbarItem(placement: .bottomBar) {
                    Button {
                        p.nowaKarta()
                        zamknijOkno()
                    } label: {
                        Label("Nowa karta", systemImage: "plus")
                            .labelStyle(.titleAndIcon)
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func wybierz(_ karta: Karta) {
        p.aktywuj(karta)
        zamknijOkno()
    }
}

struct WierszListyKart: View {
    @EnvironmentObject private var p: Przegladarka
    @ObservedObject var karta: Karta
    let program: Program?
    let wybierz: () -> Void

    var body: some View {
        let host = Adres.host(karta.wyswietlanyAdres)
        let tytul = karta.tytul.isEmpty ? host : karta.tytul
        Button(action: wybierz) {
            HStack(spacing: 12) {
                if let program {
                    IkonaProgramu(program: program, rozmiar: 26)
                        .frame(width: 52)
                } else {
                    Literka(tekst: tytul)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(program?.krotkaNazwa ?? tytul)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Kolor.tekst)
                        .lineLimit(1)
                    Text(program == nil ? host : tytul)
                        .font(.caption)
                        .foregroundStyle(Kolor.slaby)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                if karta.id == p.aktywnaID {
                    Image(systemName: "checkmark")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Kolor.czerwony)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
