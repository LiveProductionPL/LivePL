import SwiftUI

/// „Urządzenie LivePL” z menu przeglądarki: stan rejestracji tego iPhone'a w urzędzie.
struct WidokUrzadzenia: View {
    @ObservedObject private var u = Urzadzenie.wspolny
    @Environment(\.dismiss) private var zamknijOkno

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 14) {
                        Image(systemName: ikona)
                            .font(.system(size: 34))
                            .foregroundStyle(kolor)
                            .frame(width: 44)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(tytul).font(.headline).foregroundStyle(Kolor.tekst)
                            Text(opis).font(.subheadline).foregroundStyle(Kolor.slaby)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(.vertical, 6)
                }
                if let blad = u.blad {
                    Section {
                        Label(blad, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(Kolor.czerwony)
                    }
                }
                if let kod = u.zapis.kod, u.zapis.stan != "brak" {
                    Section {
                        wiersz("Kod", kod)
                        wiersz("Klucz", u.zapis.sprzetowy ? "w Secure Enclave, nie do skopiowania" : "programowy (bez Secure Enclave)")
                        if let data = u.zapis.waznyDo {
                            wiersz("Ważny do", data.formatted(date: .long, time: .omitted))
                        }
                        if let raport = u.zapis.raport {
                            wiersz("Ostatni kontakt", raport.formatted(date: .abbreviated, time: .shortened))
                        }
                        if let polaczenie = u.zapis.polaczenie {
                            wiersz("Połączenie", polaczenie)
                        }
                    } footer: {
                        Text("Ten sam kod widać w urzędzie LivePL, w „Urządzenia LivePL”.")
                    }
                }
                Section {
                    if u.trwa {
                        HStack(spacing: 10) {
                            ProgressView()
                            Text("Rejestruję w urzędzie LivePL…")
                        }
                    } else if u.zapis.stan == "brak" || u.zapis.stan == "odrzucony" || u.zapis.stan == "uniewazniony" {
                        Button {
                            Task { await u.zarejestruj() }
                        } label: {
                            Label(u.zapis.stan == "brak" ? "Zarejestruj ten iPhone" : "Zarejestruj ponownie", systemImage: "checkmark.shield")
                                .font(.headline)
                        }
                    } else if u.zapis.stan == "wyslany" {
                        Button {
                            Task { try? await u.sprawdz() }
                        } label: {
                            Label("Sprawdź, czy urząd zatwierdził", systemImage: "arrow.clockwise")
                        }
                    }
                } footer: {
                    if u.zapis.stan == "brak" {
                        Text("Klucz powstaje w Secure Enclave tego iPhone'a i nigdy go nie opuszcza. Urząd dostaje nazwę urządzenia, wersję iOS i wersję LivePL.")
                    }
                }
            }
            .navigationTitle("Urządzenie LivePL")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Gotowe") { zamknijOkno() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func wiersz(_ nazwa: String, _ wartosc: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(nazwa).foregroundStyle(Kolor.slaby)
            Spacer(minLength: 12)
            Text(wartosc).foregroundStyle(Kolor.tekst).multilineTextAlignment(.trailing)
        }
        .font(.subheadline)
    }

    private var tytul: String {
        switch u.zapis.stan {
        case "gotowe": return "Zarejestrowany w urzędzie LivePL"
        case "wyslany": return "Wniosek wysłany"
        case "odrzucony": return "Urząd odrzucił wniosek"
        case "uniewazniony": return "Rejestracja unieważniona"
        default: return "Niezarejestrowany"
        }
    }

    private var opis: String {
        switch u.zapis.stan {
        case "gotowe": return "Ten iPhone ma certyfikat „Urządzenie LivePL”, a jego klucz jest w Secure Enclave, jak TPM w komputerze."
        case "wyslany": return "Urząd jeszcze nie wystawił certyfikatu. LivePL sprawdzi to sam przy następnym otwarciu."
        case "odrzucony", "uniewazniony": return "Ten iPhone nie jest już zaufanym urządzeniem LivePL. Możesz zarejestrować go od nowa, z nowym kluczem."
        default: return "Zarejestruj ten iPhone, żeby urząd LivePL go znał, tak jak przeglądarki LivePL na komputerach."
        }
    }

    private var ikona: String {
        switch u.zapis.stan {
        case "gotowe": return "checkmark.shield.fill"
        case "wyslany": return "clock"
        case "odrzucony", "uniewazniony": return "xmark.shield"
        default: return "shield"
        }
    }

    private var kolor: Color {
        switch u.zapis.stan {
        case "gotowe": return Kolor.ok
        case "odrzucony", "uniewazniony": return Kolor.czerwony
        default: return Kolor.slaby
        }
    }
}

/// Propozycja na ekranie Start, dopóki iPhone nie jest zarejestrowany (jak okienko przy starcie LivePL na Windows).
struct PropozycjaRejestracji: View {
    @ObservedObject private var u = Urzadzenie.wspolny

    var body: some View {
        if u.pokazPropozycje || u.trwa {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "checkmark.shield")
                    .font(.title2)
                    .foregroundStyle(Kolor.czerwony)
                    .frame(width: 34)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Zarejestruj ten iPhone w urzędzie LivePL")
                        .font(.headline)
                        .foregroundStyle(Kolor.tekst)
                    Text("Jedno stuknięcie. Klucz powstaje w Secure Enclave telefonu i nigdy go nie opuszcza.")
                        .font(.subheadline)
                        .foregroundStyle(Kolor.slaby)
                        .fixedSize(horizontal: false, vertical: true)
                    if let blad = u.blad {
                        Text(blad).font(.footnote).foregroundStyle(Kolor.czerwony)
                    }
                    HStack(spacing: 10) {
                        Button {
                            Task { await u.zarejestruj() }
                        } label: {
                            if u.trwa {
                                ProgressView().controlSize(.small)
                            } else {
                                Text("Zarejestruj")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                        .disabled(u.trwa)
                        Button("Nie teraz") { u.propozycjaOdlozona = true }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                            .disabled(u.trwa)
                    }
                    .padding(.top, 2)
                }
                Spacer(minLength: 0)
            }
            .padding(14)
            .kartaLivePL()
        }
    }
}
