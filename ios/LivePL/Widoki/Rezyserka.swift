import SwiftUI

/// Ekran startowy LivePL: pole adresu, programy, otwarte karty, Moje strony i przełącznik dzień/noc przy zegarze.
struct Rezyserka: View {
    @EnvironmentObject private var p: Przegladarka
    @State private var tekst = ""
    @State private var wiecejProgramow = false
    @State private var doUsuniecia: MojaStrona?
    @FocusState private var fokus: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) {
                naglowek
                poleAdresu
                programy
                if !p.zwykleKarty.isEmpty { otwarteKarty }
                mojeStrony
                certyfikat
                stopka
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)
            .padding(.bottom, 20)
            .frame(maxWidth: 720)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Kolor.tlo)
        .task(id: p.prosbaOAdres) {
            guard p.prosbaOAdres > p.obsluzonaProsbaOAdres else { return }
            p.obsluzonaProsbaOAdres = p.prosbaOAdres
            try? await Task.sleep(for: .milliseconds(350))
            fokus = true
        }
        .confirmationDialog("Usunąć stronę z Moich stron?",
                            isPresented: Binding(get: { doUsuniecia != nil }, set: { if !$0 { doUsuniecia = nil } }),
                            titleVisibility: .visible,
                            presenting: doUsuniecia) { strona in
            Button("Usuń „\(strona.wyswietlanaNazwa)”", role: .destructive) { p.usunStrone(strona) }
        }
    }

    // MARK: - Części ekranu

    private var naglowek: some View {
        HStack(spacing: 12) {
            Image("Wstega").resizable().scaledToFit().frame(width: 46, height: 34)
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 0) {
                    Text("Live").foregroundStyle(Kolor.tekst)
                    Text("PL").foregroundStyle(Kolor.czerwony)
                }
                .font(.system(size: 25, weight: .bold))
                Text("Przeglądarka Live Production")
                    .font(.footnote)
                    .foregroundStyle(Kolor.slaby)
            }
            Spacer()
        }
        .padding(.top, 6)
        .accessibilityElement(children: .combine)
    }

    private var poleAdresu: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(Kolor.slaby)
            TextField("Adres albo szukaj", text: $tekst)
                .keyboardType(.webSearch)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.go)
                .focused($fokus)
                .onSubmit {
                    guard let url = Adres.zTekstu(tekst) else { return }
                    tekst = ""
                    p.otworz(url)
                }
            if !tekst.isEmpty {
                Button { tekst = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Kolor.bardzoSlaby)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Wyczyść")
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 50)
        .kartaLivePL(promien: 14)
    }

    private var siatka: [GridItem] { [GridItem(.adaptive(minimum: 150), spacing: 10)] }

    private var programy: some View {
        VStack(alignment: .leading, spacing: 10) {
            TytulSekcji(tekst: "Programy")
            LazyVGrid(columns: siatka, spacing: 10) {
                ForEach(p.widoczneProgramy) { KafelProgramu(program: $0) }
            }
            if !p.pozostaleProgramy.isEmpty {
                Button {
                    withAnimation(.snappy) { wiecejProgramow.toggle() }
                } label: {
                    HStack(spacing: 6) {
                        Text(wiecejProgramow ? "Mniej programów" : "Więcej programów")
                        Image(systemName: "chevron.down")
                            .rotationEffect(.degrees(wiecejProgramow ? 180 : 0))
                    }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Kolor.slaby)
                    .padding(.top, 2)
                }
                .buttonStyle(.plain)
                if wiecejProgramow {
                    LazyVGrid(columns: siatka, spacing: 10) {
                        ForEach(p.pozostaleProgramy) { KafelProgramu(program: $0) }
                    }
                }
            }
        }
    }

    private var otwarteKarty: some View {
        VStack(alignment: .leading, spacing: 10) {
            TytulSekcji(tekst: "Otwarte karty")
            VStack(spacing: 0) {
                ForEach(p.zwykleKarty) { karta in
                    WierszKarty(karta: karta)
                    if karta.id != p.zwykleKarty.last?.id {
                        Rectangle().fill(Kolor.ramka).frame(height: 0.5).padding(.leading, 58)
                    }
                }
            }
            .kartaLivePL()
        }
    }

    private var mojeStrony: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                TytulSekcji(tekst: "Moje strony")
                Spacer()
                Button {
                    p.dodawanaStrona = MojaStrona(adres: "", nazwa: "")
                } label: {
                    Label("Dodaj", systemImage: "plus")
                        .font(.subheadline.weight(.semibold))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
            if p.mojeStrony.isEmpty {
                Text("Dodaj dowolną stronę, a pojawi się tutaj.")
                    .font(.subheadline)
                    .foregroundStyle(Kolor.slaby)
            } else {
                VStack(spacing: 0) {
                    ForEach(p.mojeStrony) { strona in
                        WierszStrony(strona: strona) { doUsuniecia = strona }
                        if strona.id != p.mojeStrony.last?.id {
                            Rectangle().fill(Kolor.ramka).frame(height: 0.5).padding(.leading, 58)
                        }
                    }
                }
                .kartaLivePL()
            }
        }
    }

    private var certyfikat: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "checkmark.shield")
                .font(.title2)
                .foregroundStyle(Kolor.czerwony)
                .frame(width: 34)
            VStack(alignment: .leading, spacing: 6) {
                Text("Certyfikat LivePL")
                    .font(.headline)
                    .foregroundStyle(Kolor.tekst)
                Text("Żeby iPhone rozpoznawał certyfikaty LivePL, np. podpisaną pocztę, zainstaluj raz certyfikat główny. Instrukcja otworzy się w Safari.")
                    .font(.subheadline)
                    .foregroundStyle(Kolor.slaby)
                    .fixedSize(horizontal: false, vertical: true)
                Button("Jak zainstalować") {
                    // profil konfiguracyjny iOS instaluje tylko z Safari
                    if let url = URL(string: "https://cert.livepl.eu/instalacja#iphone") {
                        UIApplication.shared.open(url)
                    }
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .padding(.top, 4)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .kartaLivePL()
    }

    private var stopka: some View {
        HStack(spacing: 10) {
            Text("LivePL \(Bundle.main.wersjaLivePL)")
                .font(.footnote)
                .foregroundStyle(Kolor.bardzoSlaby)
            Spacer()
            Button {
                p.motyw = p.motyw.nastepny
            } label: {
                Image(systemName: p.motyw.symbol)
                    .font(.body)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(Kolor.slaby)
            .accessibilityLabel("Tryb: \(p.motyw.opis). Stuknij: \(p.motyw.nastepny.opis)")
            TimelineView(.everyMinute) { chwila in
                Text(chwila.date, style: .time)
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(Kolor.slaby)
            }
        }
    }
}

struct TytulSekcji: View {
    let tekst: String

    var body: some View {
        Text(tekst)
            .font(.footnote.weight(.semibold))
            .textCase(.uppercase)
            .foregroundStyle(Kolor.slaby)
            .padding(.leading, 2)
    }
}

struct KafelProgramu: View {
    @EnvironmentObject private var p: Przegladarka
    let program: Program

    var body: some View {
        let otwarty = p.karta(programu: program.id) != nil
        Button { p.otworz(program) } label: {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top) {
                    IkonaProgramu(program: program, rozmiar: 30)
                    Spacer(minLength: 4)
                    if otwarty {
                        Text("otwarty")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(Kolor.ok)
                    }
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(program.krotkaNazwa)
                        .font(.headline)
                        .foregroundStyle(Kolor.tekst)
                        .lineLimit(1)
                    Text(program.opis ?? Adres.host(program.adresNaTelefonie))
                        .font(.caption)
                        .foregroundStyle(Kolor.slaby)
                        .lineLimit(2, reservesSpace: true)
                        .multilineTextAlignment(.leading)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .kartaLivePL()
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Kwadracik z pierwszą literą nazwy, jak w wersji webowej.
struct Literka: View {
    let tekst: String

    var body: some View {
        Text(String(tekst.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased())
            .font(.system(size: 16, weight: .bold))
            .foregroundStyle(Kolor.czerwony)
            .frame(width: 34, height: 34)
            .background(Kolor.czerwonyTlo, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
    }
}

struct WierszKarty: View {
    @EnvironmentObject private var p: Przegladarka
    @ObservedObject var karta: Karta

    var body: some View {
        let host = Adres.host(karta.wyswietlanyAdres)
        let tytul = karta.tytul.isEmpty ? host : karta.tytul
        HStack(spacing: 10) {
            Button { p.aktywuj(karta) } label: {
                HStack(spacing: 12) {
                    Literka(tekst: tytul)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(tytul).font(.subheadline.weight(.semibold)).foregroundStyle(Kolor.tekst).lineLimit(1)
                        Text(host).font(.caption).foregroundStyle(Kolor.slaby).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Button { p.zamknij(karta) } label: {
                Image(systemName: "xmark")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Kolor.slaby)
                    .frame(width: 34, height: 34)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Zamknij kartę \(tytul)")
        }
        .padding(.leading, 12)
        .padding(.trailing, 6)
        .padding(.vertical, 9)
    }
}

struct WierszStrony: View {
    @EnvironmentObject private var p: Przegladarka
    let strona: MojaStrona
    let usun: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Button {
                if let url = strona.url { p.otworz(url) }
            } label: {
                HStack(spacing: 12) {
                    Literka(tekst: strona.wyswietlanaNazwa)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(strona.wyswietlanaNazwa).font(.subheadline.weight(.semibold)).foregroundStyle(Kolor.tekst).lineLimit(1)
                        Text(Adres.host(strona.url)).font(.caption).foregroundStyle(Kolor.slaby).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .contextMenu {
                Button {
                    if let url = strona.url { UIPasteboard.general.url = url }
                } label: { Label("Kopiuj adres", systemImage: "doc.on.doc") }
                Button(role: .destructive, action: usun) { Label("Usuń", systemImage: "trash") }
            }
            Button(action: usun) {
                Image(systemName: "trash")
                    .font(.footnote)
                    .foregroundStyle(Kolor.slaby)
                    .frame(width: 34, height: 34)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Usuń \(strona.wyswietlanaNazwa)")
        }
        .padding(.leading, 12)
        .padding(.trailing, 6)
        .padding(.vertical, 9)
    }
}
