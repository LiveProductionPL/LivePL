import SwiftUI
import WebKit

/// Otwarta karta: u góry wstecz, adres z kłódką, odśwież i menu; pod spodem strona.
struct WidokKarty: View {
    @EnvironmentObject private var p: Przegladarka
    @ObservedObject var karta: Karta
    @State private var edycja = false
    @State private var tekst = ""
    @FocusState private var fokus: Bool

    var body: some View {
        VStack(spacing: 0) {
            gornyPasek
            PasekTlumaczenia(karta: karta)
            ZStack(alignment: .top) {
                WebWidok(webView: karta.webView)
                if let blad = karta.blad {
                    WidokBledu(blad: blad) { karta.sprobujPonownie() }
                }
                if karta.laduje {
                    PasekPostepu(postep: karta.postep)
                }
            }
        }
        .background(Kolor.tlo)
        .tlumaczenieStron(karta)
        .animation(.snappy, value: karta.pokazPropozycje)
        .animation(.snappy, value: karta.przetlumaczona)
        .onChange(of: fokus) { _, jest in
            if !jest { edycja = false }
        }
    }

    // MARK: - Górny pasek

    private var gornyPasek: some View {
        HStack(spacing: 4) {
            if edycja {
                poleEdycji
                Button("Anuluj") {
                    fokus = false
                    edycja = false
                }
                .font(.subheadline)
                .padding(.horizontal, 6)
            } else {
                Button { karta.webView.goBack() } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 38, height: 38)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(karta.mozeWstecz ? Kolor.tekst : Kolor.bardzoSlaby)
                .disabled(!karta.mozeWstecz)
                .accessibilityLabel("Wstecz")

                adres
                menu
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 6)
        .background(Kolor.tlo)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Kolor.ramka).frame(height: 0.5)
        }
    }

    private var adres: some View {
        HStack(spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: karta.bezpieczna ? "lock.fill" : "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(karta.bezpieczna ? Kolor.slaby : Kolor.czerwony)
                Text(Adres.host(karta.wyswietlanyAdres))
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Kolor.tekst)
                    .lineLimit(1)
                    .truncationMode(.middle)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
            .onTapGesture { zacznijEdycje() }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Adres: \(Adres.host(karta.wyswietlanyAdres))")
            .accessibilityHint("Stuknij, żeby wpisać inny adres")
            .accessibilityAddTraits(.isButton)

            Button {
                if karta.laduje { karta.webView.stopLoading() } else { karta.webView.reload() }
            } label: {
                Image(systemName: karta.laduje ? "xmark" : "arrow.clockwise")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Kolor.slaby)
                    .frame(width: 30, height: 30)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(karta.laduje ? "Zatrzymaj" : "Odśwież")
        }
        .padding(.leading, 12)
        .padding(.trailing, 4)
        .frame(height: 38)
        .background(Kolor.karta, in: Capsule())
        .overlay(Capsule().stroke(Kolor.ramka, lineWidth: 1))
    }

    private var poleEdycji: some View {
        HStack(spacing: 6) {
            Image(systemName: "magnifyingglass")
                .font(.footnote)
                .foregroundStyle(Kolor.slaby)
            TextField("Adres albo szukaj", text: $tekst)
                .keyboardType(.webSearch)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.go)
                .focused($fokus)
                .onSubmit { przejdz() }
            if !tekst.isEmpty {
                Button { tekst = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Kolor.bardzoSlaby)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Wyczyść")
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 38)
        .background(Kolor.karta, in: Capsule())
        .overlay(Capsule().stroke(Kolor.czerwony, lineWidth: 1))
    }

    private var menu: some View {
        Menu {
            Button { karta.webView.goForward() } label: {
                Label("Dalej", systemImage: "chevron.right")
            }
            .disabled(!karta.mozeDalej)
            if let url = karta.adres {
                ShareLink(item: url) {
                    Label("Udostępnij", systemImage: "square.and.arrow.up")
                }
            }
            Button {
                karta.webView.findInteraction?.presentFindNavigator(showingReplace: false)
            } label: {
                Label("Znajdź na stronie", systemImage: "doc.text.magnifyingglass")
            }
            if karta.przetlumaczona || karta.tlumaczy {
                Button { karta.pokazOryginal() } label: {
                    Label("Pokaż oryginał", systemImage: "arrow.uturn.backward")
                }
            } else {
                Button { karta.tlumacz() } label: {
                    Label("Przetłumacz na polski", systemImage: "translate")
                }
                .disabled(karta.adres == nil)
            }
            Divider()
            Button {
                if let url = karta.adres {
                    p.dodawanaStrona = MojaStrona(adres: url.absoluteString, nazwa: String(karta.tytul.prefix(24)))
                }
            } label: {
                Label("Dodaj do Moich stron", systemImage: "plus")
            }
            .disabled(karta.adres == nil)
            Button {
                if let url = karta.adres { UIPasteboard.general.url = url }
            } label: {
                Label("Kopiuj adres", systemImage: "doc.on.doc")
            }
            Button {
                if let url = karta.adres { UIApplication.shared.open(url) }
            } label: {
                Label("Otwórz w Safari", systemImage: "safari")
            }
            Divider()
            Menu {
                MenuPrzegladarki()
            } label: {
                Label("Przeglądarka", systemImage: "gearshape")
            }
            Divider()
            Button(role: .destructive) { p.zamknij(karta) } label: {
                Label(karta.programID == nil ? "Zamknij kartę" : "Zamknij program", systemImage: "xmark")
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .font(.system(size: 20))
                .foregroundStyle(Kolor.tekst)
                .frame(width: 38, height: 38)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Menu")
    }

    private func zacznijEdycje() {
        tekst = karta.wyswietlanyAdres?.absoluteString ?? ""
        edycja = true
        fokus = true
    }

    private func przejdz() {
        defer {
            fokus = false
            edycja = false
        }
        guard let url = Adres.zTekstu(tekst) else { return }
        if Przegladarka.obcyAdres(url) {
            UIApplication.shared.open(url)
        } else {
            karta.laduj(url)
        }
    }
}
