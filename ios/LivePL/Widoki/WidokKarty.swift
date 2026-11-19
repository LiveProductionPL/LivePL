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

/// Cienki czerwony pasek ładowania pod paskiem adresu.
struct PasekPostepu: View {
    let postep: Double

    var body: some View {
        GeometryReader { miejsce in
            Rectangle()
                .fill(Kolor.czerwony)
                .frame(width: miejsce.size.width * max(0.08, min(1, postep)), height: 2)
                .animation(.easeOut(duration: 0.25), value: postep)
        }
        .frame(height: 2)
        .allowsHitTesting(false)
    }
}

struct WidokBledu: View {
    let blad: BladStrony
    let ponow: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "wifi.exclamationmark")
                .font(.system(size: 42))
                .foregroundStyle(Kolor.czerwony)
            Text("Nie udało się otworzyć strony")
                .font(.title3.weight(.semibold))
                .foregroundStyle(Kolor.tekst)
            Text(blad.opis)
                .multilineTextAlignment(.center)
                .foregroundStyle(Kolor.slaby)
            if let url = blad.adres {
                Text(Adres.host(url))
                    .font(.footnote)
                    .foregroundStyle(Kolor.bardzoSlaby)
            }
            Button("Spróbuj ponownie", action: ponow)
                .buttonStyle(.borderedProminent)
                .padding(.top, 6)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Kolor.tlo)
    }
}

/// Osadza WKWebView karty. Ten sam WKWebView przechodzi między kontenerami, więc strona nie ładuje się od nowa.
struct WebWidok: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> UIView {
        let kontener = UIView()
        osadz(w: kontener)
        return kontener
    }

    func updateUIView(_ kontener: UIView, context: Context) {
        if webView.superview !== kontener { osadz(w: kontener) }
    }

    private func osadz(w kontener: UIView) {
        webView.removeFromSuperview()
        webView.translatesAutoresizingMaskIntoConstraints = false
        kontener.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: kontener.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: kontener.trailingAnchor),
            webView.topAnchor.constraint(equalTo: kontener.topAnchor),
            webView.bottomAnchor.constraint(equalTo: kontener.bottomAnchor)
        ])
    }
}
